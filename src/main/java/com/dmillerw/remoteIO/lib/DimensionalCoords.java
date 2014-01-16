package com.dmillerw.remoteIO.lib;

import dan200.turtle.api.ITurtleAccess;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Created by Dylan Miller on 1/13/14
 */
public class DimensionalCoords {

    public static DimensionalCoords create(TileEntity tile) {
        return new DimensionalCoords(tile.worldObj, tile.xCoord, tile.yCoord, tile.zCoord);
    }

    public static DimensionalCoords create(EntityLivingBase entity) {
        return new DimensionalCoords(entity.worldObj, entity.posX, entity.posY, entity.posZ);
    }

    public static DimensionalCoords fromTurtle(ITurtleAccess turtle) {
        return new DimensionalCoords(turtle.getWorld(), (int)Math.floor(turtle.getPosition().xCoord), (int)Math.floor(turtle.getPosition().yCoord), (int)Math.floor(turtle.getPosition().zCoord));
    }

    public static DimensionalCoords fromNBT(NBTTagCompound nbt) {
        return new DimensionalCoords(nbt.getInteger("dimension"), nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
    }

    public int dimensionID;

    public int x;
    public int y;
    public int z;

    public DimensionalCoords(World world, int x, int y, int z) {
        this(world.provider.dimensionId, x, y, z);
    }

    public DimensionalCoords(World world, double x, double y, double z) {
        this(world.provider.dimensionId, x, y, z);
    }

    public DimensionalCoords(int dimensionID, double x, double y, double z) {
        this(dimensionID, (int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
    }

    public DimensionalCoords(int dimensionID, int x, int y, int z) {
        this.dimensionID = dimensionID;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getRangeTo(DimensionalCoords coords) {
        int xRange = (int)Math.abs(this.x - coords.x);
        int yRange = (int)Math.abs(this.y - coords.y);
        int zRange = (int)Math.abs(this.z - coords.z);

        return Math.max(xRange, Math.max(yRange, zRange));
    }

    /* WORLD WRAPPERS */

    public boolean inWorld(World world) {
        return world.provider.dimensionId == this.dimensionID;
    }

    public World getWorld() {
        return MinecraftServer.getServer().worldServerForDimension(this.dimensionID);
    }

    public TileEntity getTileEntity() {
        return getWorld().getBlockTileEntity(x, y, z);
    }

    /* END */

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("dimension", this.dimensionID);
        nbt.setInteger("x", this.x);
        nbt.setInteger("y", this.y);
        nbt.setInteger("z", this.z);
    }

    public int hashCode() {
        return this.dimensionID & this.x & this.y & this.z;
    }

    public DimensionalCoords copy() {
        return new DimensionalCoords(this.dimensionID, this.x, this.y, this.z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DimensionalCoords)) {
            return false;
        }

        return equals(obj);
    }

    public boolean equals(DimensionalCoords coords) {
        return ((this.dimensionID == coords.dimensionID) &&
                (this.x == coords.x) &&
                (this.y == coords.y) &&
                (this.z == coords.z));
    }

}
