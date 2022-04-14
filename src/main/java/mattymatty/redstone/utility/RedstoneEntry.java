package mattymatty.redstone.utility;

import mattymatty.redstone.mixins.RedstoneWireAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class RedstoneEntry implements Comparable<RedstoneEntry>, Cloneable {
    public int power_level;
    public int distance;
    public BlockPos blockPos;
    public Direction direction;

    public RedstoneEntry(int power_level, int distance, BlockPos blockPos, Direction direction) {
        this.power_level = power_level;
        this.distance = distance;
        this.blockPos = blockPos;
        this.direction = direction;
    }

    public boolean isValid(World world) {
        Block block = world.getBlockState(blockPos.offset(direction.getOpposite())).getBlock();
        if (block instanceof RedstoneWireBlock wire) {
            boolean mem = ((RedstoneWireAccessor) wire).getWiresGivePower();
            ((RedstoneWireAccessor) wire).setWiresGivePower(false);
            int level = world.getEmittedRedstonePower(blockPos, direction);
            ((RedstoneWireAccessor) wire).setWiresGivePower(mem);
            return level == power_level;
        } else
            return false;
    }

    public boolean sameSource(RedstoneEntry entry) {
        if (entry == null)
            return false;
        return (this.blockPos.equals(entry.blockPos) && this.direction.equals(entry.direction) && this.power_level == entry.power_level);
    }

    public int getPower() {
        return Math.max(power_level - distance, 0);
    }

    public static RedstoneEntry fromNbt(NbtCompound nbt) {
        return new RedstoneEntry(
                nbt.getInt("power_level"),
                nbt.getInt("distance"),
                NbtHelper.toBlockPos(nbt.getCompound("pos")),
                Direction.byId(nbt.getByte("direction"))
        );
    }

    public NbtElement toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("power_level", this.power_level);
        nbt.putInt("distance", this.distance);
        nbt.put("pos", NbtHelper.fromBlockPos(this.blockPos));
        nbt.putByte("direction", (byte) this.direction.getId());
        return nbt;
    }

    @Override
    public RedstoneEntry clone() {
        try {
            return (RedstoneEntry) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RedstoneEntry e))
            return false;
        if (this.power_level != e.power_level)
            return false;
        if (this.distance != e.distance)
            return false;
        if (!this.blockPos.equals(e.blockPos))
            return false;
        return this.direction.equals(e.direction);
    }

    @Override
    public int compareTo(RedstoneEntry o) {
        int i = Integer.compare(this.getPower(), o.getPower());
        return (i != 0) ? i : -Integer.compare(this.distance, o.distance);
    }
}
