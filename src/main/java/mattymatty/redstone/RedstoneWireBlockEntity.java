package mattymatty.redstone;

import mattymatty.redstone.utility.RedstoneEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.SortedSet;
import java.util.TreeSet;

public class RedstoneWireBlockEntity extends BlockEntity {
    public static final BlockEntityType<RedstoneWireBlockEntity> TYPE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            "mattymatty:redstone_wire",
            BlockEntityType.Builder.create(RedstoneWireBlockEntity::new, Blocks.REDSTONE_WIRE).build(null)
    );

    public static void init() {
    }

    public final SortedSet<RedstoneEntry> local_power_sources = new TreeSet<>();
    public RedstoneEntry current_power_source = null;
    public BlockPos parentPos = null;
    public RedstoneWireBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public int getPower() {
        if (current_power_source == null)
            return 0;
        return current_power_source.getPower();
    }

    public boolean hasValidParent(World world) {
        if (this.parentPos == null || this.current_power_source == null)
            return true;
        return isValidParent(world, this.current_power_source, this.parentPos);
    }

    public boolean isValidParent(World world, RedstoneEntry entry, BlockPos parentPos) {
        if (parentPos == pos)
            return true;
        if (!world.getBlockState(parentPos).isOf(Blocks.REDSTONE_WIRE))
            return false;
        int dy = pos.getY() - parentPos.getY();
        if (dy > 0 && world.getBlockState(parentPos.up()).isSolidBlock(world, parentPos.up())) {
            return false;
        }
        if (dy < 0 && (!world.getBlockState(parentPos.down()).isSolidBlock(world, parentPos.down())
                || world.getBlockState(pos.up()).isSolidBlock(world, pos.up()))) {
            return false;
        }
        BlockEntity blockEntity = world.getBlockEntity(parentPos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity) {
            return entry.sameSource(redstoneWireBlockEntity.current_power_source);
        } else
            return false;
    }


    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        NbtList list = new NbtList();
        list.addAll(local_power_sources.stream().map(RedstoneEntry::toNbt).toList());
        tag.put("local_power_sources", list);
        if (this.current_power_source != null)
            tag.put("current_power_source", this.current_power_source.toNbt());
        if (this.parentPos != null)
            tag.put("parentPos", NbtHelper.fromBlockPos(this.parentPos));
        return tag;
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        NbtList list = tag.getList("local_power_sources", 0);
        local_power_sources.clear();
        list.forEach(e -> local_power_sources.add(RedstoneEntry.fromNbt((NbtCompound) e)));
        if (tag.contains("current_power_source"))
            this.current_power_source = RedstoneEntry.fromNbt(tag.getCompound("current_power_source"));
        if (tag.contains("parentPos"))
            this.parentPos = NbtHelper.toBlockPos(tag.getCompound("parentPos"));
    }




}
