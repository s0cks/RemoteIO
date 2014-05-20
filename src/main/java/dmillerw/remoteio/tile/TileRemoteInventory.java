package dmillerw.remoteio.tile;

import dmillerw.remoteio.core.TransferType;
import dmillerw.remoteio.core.UpgradeType;
import dmillerw.remoteio.core.helper.transfer.FluidTransferHelper;
import dmillerw.remoteio.core.helper.transfer.IC2TransferHelper;
import dmillerw.remoteio.inventory.wrapper.InventoryArmor;
import dmillerw.remoteio.inventory.wrapper.InventoryArray;
import dmillerw.remoteio.item.ItemWirelessTransmitter;
import dmillerw.remoteio.lib.VisualState;
import dmillerw.remoteio.tile.core.TileIOCore;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * @author dmillerw
 */
public class TileRemoteInventory extends TileIOCore implements IInventory, IFluidHandler, IEnergySink {

	public static final byte ACCESS_INVENTORY = 0;
	public static final byte ACCESS_ARMOR = 1;

	@Override
	public void callback(IInventory inventory) {
		if (!hasWorldObj() || getWorldObj().isRemote) {
			return;
		}

		// I think IC2 caches tile state...
		if (registeredWithIC2) {
			MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
			registeredWithIC2 = false;
		}

		if (!registeredWithIC2 && hasTransferChip(TransferType.ENERGY_IC2) && getPlayer() != null) {
			MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
			registeredWithIC2 = true;
		}

		// Clear missing upgrade flag
		missingUpgrade = false;

		updateVisualState();
		updateNeighbors();
	}

	public String target;

	public byte accessType = ACCESS_INVENTORY;

	private boolean registeredWithIC2 = false;
	private boolean missingUpgrade = false;

	@Override
	public void writeCustomNBT(NBTTagCompound nbt) {
		if (target != null && !target.isEmpty()) {
			nbt.setString("target", target);
		}

		nbt.setByte("access", accessType);
	}

	@Override
	public void onClientUpdate(NBTTagCompound nbt) {
		super.onClientUpdate(nbt);

		if (nbt.hasKey("access")) {
			accessType = nbt.getByte("access");
			System.out.println("Received " + accessType);
		}
	}

	@Override
	public void readCustomNBT(NBTTagCompound nbt) {
		if (nbt.hasKey("target")) {
			target = nbt.getString("target");
		} else {
			target = "";
		}

		accessType = nbt.getByte("access");
	}

	@Override
	public void onChunkUnload() {
		if (registeredWithIC2) {
			MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
			registeredWithIC2 = false;
		}
	}

	@Override
	public void invalidate() {
		if (registeredWithIC2) {
			MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
			registeredWithIC2 = false;
		}
	}

	/* CHIP METHODS */

	public EntityPlayer getPlayer() {
		if (target == null || target.isEmpty()) {
			return null;
		}

		ServerConfigurationManager configurationManager = MinecraftServer.getServer().getConfigurationManager();
		EntityPlayer player = configurationManager.getPlayerForUsername(target);

		if (player != null) {
			if (!ItemWirelessTransmitter.hasValidRemote(player)) {
				return null;
			}
		}

		return player;
	}
	public IInventory getPlayerInventory(int transferType) {
		EntityPlayer player = getPlayer();
		if (player != null && hasTransferChip(transferType)) {
			if (accessType == ACCESS_INVENTORY) {
				return new InventoryArray(player.inventory.mainInventory);
			} else if (accessType == ACCESS_ARMOR) {
				return new InventoryArmor(player);
			}
		}
		return null;
	}

	/* END CHIP METHODS */

	public void sendAccessType() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setByte("access", accessType);
		sendClientUpdate(nbt);
	}

	public void setPlayer(EntityPlayer player) {
		if (registeredWithIC2) {
			MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
			registeredWithIC2 = false;
		}

		target = player.getCommandSenderName();

		if (!registeredWithIC2 && hasTransferChip(TransferType.ENERGY_IC2)) {
			MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
			registeredWithIC2 = true;
		}

		updateVisualState();
		updateNeighbors();
		markForUpdate();
	}

	public VisualState calculateVisualState() {
		if (target == null || target.isEmpty()) {
			return VisualState.INACTIVE;
		} else {
			EntityPlayer player = getPlayer();

			if (player == null) {
				return VisualState.INACTIVE_BLINK;
			}

			boolean simple = hasUpgradeChip(UpgradeType.SIMPLE_CAMO);

			if (simple) {
				return VisualState.CAMOUFLAGE_SIMPLE;
			}

			return missingUpgrade ? VisualState.ACTIVE_BLINK : VisualState.ACTIVE;
		}
	}

	/* IINVENTORY */
	@Override
	public int getSizeInventory() {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		return inventoryPlayer != null ? inventoryPlayer.getSizeInventory() : 0;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		return inventoryPlayer != null ? inventoryPlayer.getStackInSlot(slot) : null;
	}

	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		return inventoryPlayer != null ? inventoryPlayer.decrStackSize(slot, amount) : null;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		return inventoryPlayer != null ? inventoryPlayer.getStackInSlotOnClosing(slot) : null;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		if (inventoryPlayer != null) inventoryPlayer.setInventorySlotContents(slot, stack);
	}

	@Override
	public String getInventoryName() {
		return null;
	}

	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}

	@Override
	public int getInventoryStackLimit() {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		return inventoryPlayer != null ? inventoryPlayer.getInventoryStackLimit() : 0;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return true;
	}

	@Override
	public void openInventory() {

	}

	@Override
	public void closeInventory() {

	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		IInventory inventoryPlayer = getPlayerInventory(TransferType.MATTER_ITEM);
		System.out.println(stack.getDisplayName() + " : " + slot + " : " + inventoryPlayer.isItemValidForSlot(slot, stack));
		return inventoryPlayer != null ? inventoryPlayer.isItemValidForSlot(slot, stack) : false;
	}

	/* IFLUIDHANDLER */
	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
		IInventory IInventory = getPlayerInventory(TransferType.MATTER_FLUID);
		return IInventory != null ? FluidTransferHelper.fill(IInventory, resource, doFill) : 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
		IInventory IInventory = getPlayerInventory(TransferType.MATTER_FLUID);
		return IInventory != null ? FluidTransferHelper.drain(IInventory, resource, doDrain) : null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
		IInventory IInventory = getPlayerInventory(TransferType.MATTER_FLUID);
		return IInventory != null ? FluidTransferHelper.drain(IInventory, maxDrain, doDrain) : null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid) {
		IInventory IInventory = getPlayerInventory(TransferType.MATTER_FLUID);
		return IInventory != null ? FluidTransferHelper.canFill(IInventory, fluid) : false;
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid) {
		IInventory IInventory = getPlayerInventory(TransferType.MATTER_FLUID);
		return IInventory != null ? FluidTransferHelper.canDrain(IInventory, fluid) : false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) {
		IInventory IInventory = getPlayerInventory(TransferType.MATTER_FLUID);
		return IInventory != null ? FluidTransferHelper.getTankInfo(IInventory) : new FluidTankInfo[0];
	}

	/* IENERGYSINK */
	@Override
	public double demandedEnergyUnits() {
		IInventory IInventory = getPlayerInventory(TransferType.ENERGY_IC2);
		return IInventory != null ? IC2TransferHelper.requiresCharge(IInventory) ? 32D : 0D : 0D;
	}

	@Override
	public double injectEnergyUnits(ForgeDirection directionFrom, double amount) {
		IInventory IInventory = getPlayerInventory(TransferType.ENERGY_IC2);
		return IInventory != null ? IC2TransferHelper.fill(IInventory, amount) : 0D;
	}

	@Override
	public int getMaxSafeInput() {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection direction) {
		return getPlayerInventory(TransferType.ENERGY_IC2) != null;
	}

}