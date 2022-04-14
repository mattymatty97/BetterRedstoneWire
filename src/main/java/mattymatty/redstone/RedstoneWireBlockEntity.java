package mattymatty.redstone;

import mattymatty.redstone.mixins.RedstoneWireAccessor;
import mattymatty.redstone.mixins.RedstoneWireMixin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.state.property.Properties;

import java.util.*;

public class RedstoneWireBlockEntity extends BlockEntity {
    public static final BlockEntityType<RedstoneWireBlockEntity> TYPE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            "mattymatty:redstone_wire",
            BlockEntityType.Builder.create(RedstoneWireBlockEntity::new, Blocks.REDSTONE_WIRE).build(null)
    );

    public static void init() { }

    public static class RedstoneEntry implements Comparable<RedstoneEntry>, Cloneable{
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

        public boolean isValid(World world){
            Block block = world.getBlockState(blockPos.offset(direction.getOpposite())).getBlock();
            if ( block instanceof RedstoneWireBlock wire) {
                boolean mem = ((RedstoneWireAccessor) wire).getWiresGivePower();
                ((RedstoneWireAccessor) wire).setWiresGivePower(false);
                int level = world.getEmittedRedstonePower(blockPos, direction);
                ((RedstoneWireAccessor) wire).setWiresGivePower(mem);
                return level == power_level;
            }else
                return false;
        }

        public boolean sameSource(RedstoneEntry entry){
            return (this.blockPos.equals(entry.blockPos) && this.direction.equals(entry.direction) && this.power_level == entry.power_level);
        }

        public int getPower(){
            return Math.max(power_level - distance, 0);
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
        public int compareTo(RedstoneWireBlockEntity.RedstoneEntry o) {
            int i = Integer.compare(this.getPower(),o.getPower());
            return (i!=0)?i:-Integer.compare(this.distance,o.distance);
        }

        public NbtElement toNbt(){
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("power_level", this.power_level);
            nbt.putInt("distance", this.distance);
            nbt.put("pos", NbtHelper.fromBlockPos(this.blockPos));
            nbt.putByte("direction", (byte)this.direction.getId());
            return nbt;
        }

        public static RedstoneEntry fromNbt(NbtCompound nbt){
            return new RedstoneEntry(
                    nbt.getInt("power_level"),
                    nbt.getInt("distance"),
                    NbtHelper.toBlockPos(nbt.getCompound("pos")),
                    Direction.byId((int)nbt.getByte("direction"))
            );
        }

        @Override
        public RedstoneEntry clone(){
            try {
                return (RedstoneEntry) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    public final SortedSet<RedstoneEntry> local_power_sources = new TreeSet<>();
    public RedstoneEntry current_power_source = null;
    public BlockPos parentPos = null;

    public RedstoneWireBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public boolean hasValidParent(World world){
        if (this.parentPos == null || this.current_power_source == null)
            return true;
        if (!world.getBlockState(parentPos).isOf(Blocks.REDSTONE_WIRE))
            return false;
        int dy = pos.getY() - parentPos.getY();
        if (dy > 0 && world.getBlockState(parentPos.up()).isSolidBlock(world,parentPos.up())){
            return false;
        }
        if (dy < 0 && ( !world.getBlockState(parentPos.down()).isSolidBlock(world,parentPos.down())
        || world.getBlockState(pos.up()).isSolidBlock(world,pos.up()))){
            return false;
        }
        BlockEntity blockEntity = world.getBlockEntity(parentPos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity){
            if (redstoneWireBlockEntity.current_power_source == null)
                return false;
            return redstoneWireBlockEntity.current_power_source.sameSource(this.current_power_source);
        }else
            return false;
    }

    public int getPower(){
        if (current_power_source == null)
            return 0;
        return current_power_source.getPower();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        NbtList list = new NbtList();
        list.addAll(local_power_sources.stream().map(RedstoneEntry::toNbt).toList());
        tag.put("local_power_sources",list);
        if (this.current_power_source != null)
            tag.put("current_power_source",this.current_power_source.toNbt());
        if (this.parentPos != null)
            tag.put("parentPos", NbtHelper.fromBlockPos(this.parentPos));
        return tag;
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        NbtList list = tag.getList("local_power_sources",0);
        local_power_sources.clear();
        list.forEach(e -> local_power_sources.add(RedstoneEntry.fromNbt((NbtCompound)e)));
        if (tag.contains("current_power_source"))
            this.current_power_source = RedstoneEntry.fromNbt(tag.getCompound("current_power_source"));
        if (tag.contains("parentPos"))
            this.parentPos = NbtHelper.toBlockPos(tag.getCompound("parentPos"));
    }
}
