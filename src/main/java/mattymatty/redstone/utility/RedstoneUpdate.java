package mattymatty.redstone.utility;

import mattymatty.redstone.RedstoneWireBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.LinkedList;
import java.util.Queue;

public class RedstoneUpdate implements Runnable {

    static final Queue<RedstoneUpdate> updates = new LinkedList<>();
    public World world;
    public BlockPos pos;
    public BlockPos from;
    public RedstoneEntry entry;
    public int depth;
    public Type type;

    RedstoneUpdate(World world, BlockPos from, BlockPos pos, RedstoneEntry entry, int depth, Type removing) {
        this.world = world;
        this.pos = pos;
        this.from = from;
        this.entry = entry;
        this.depth = depth;
        this.type = removing;
    }

    RedstoneUpdate(World world, BlockPos from, BlockPos pos, RedstoneEntry entry, int depth) {
        this.world = world;
        this.pos = pos;
        this.from = from;
        this.entry = entry;
        this.depth = depth;
        this.type = Type.Propagate;
    }

    @Override
    public void run() {
        boolean changed = false;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity) {
            switch (this.type) {
                case Invalidate:
                    if (redstoneWireBlockEntity.getPower() > 0) {
                        if (redstoneWireBlockEntity.current_power_source.sameSource(entry) || !redstoneWireBlockEntity.current_power_source.isValid(world)) {
                            redstoneWireBlockEntity.current_power_source = null;
                            redstoneWireBlockEntity.parentPos = null;
                            propagateUpdateEverywhere(world, pos, entry, this.depth - 1, Type.Invalidate);
                        } else {
                            RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                            next.distance++;
                            propagateUpdateTo(world, pos, from, next, next.getPower(), Type.Propagate);
                        }
                    }
                    break;
                case Broken:
                    if (redstoneWireBlockEntity.getPower() > 0) {
                        if ((redstoneWireBlockEntity.current_power_source.sameSource(entry) && entry.getPower() >= redstoneWireBlockEntity.getPower())
                                || !redstoneWireBlockEntity.current_power_source.isValid(world)) {
                            redstoneWireBlockEntity.current_power_source = null;
                            redstoneWireBlockEntity.parentPos = null;
                            RedstoneEntry entry1 = entry.clone();
                            entry1.distance++;
                            propagateUpdateEverywhere(world, pos, entry1, this.depth - 1, Type.Broken);
                        } else {
                            RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                            next.distance++;
                            propagateUpdateTo(world, pos, from, next, next.getPower(), Type.Propagate);
                        }
                    }
                    break;
                case Propagate:
                    if (redstoneWireBlockEntity.getPower() == 0 || redstoneWireBlockEntity.current_power_source.compareTo(entry) < 0) {
                        if (redstoneWireBlockEntity.isValidParent(world, entry, from)) {
                            redstoneWireBlockEntity.current_power_source = entry;
                            redstoneWireBlockEntity.parentPos = from;
                            changed = true;
                        }
                    }
            }

            if (redstoneWireBlockEntity.local_power_sources.size() > 0) {
                RedstoneEntry entry = redstoneWireBlockEntity.local_power_sources.first();
                if (entry.getPower() > redstoneWireBlockEntity.getPower() && entry.isValid(world)) {
                    redstoneWireBlockEntity.current_power_source = entry;
                    redstoneWireBlockEntity.parentPos = pos;
                    this.depth = entry.getPower();
                    changed = true;
                }
            }

            if (changed) {
                RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                next.distance++;
                propagateUpdateEverywhere(world, pos, next, this.depth - 1, Type.Propagate);
            }
        }
    }

    public enum Type {
        Propagate,
        Invalidate,
        Broken
    }

    public static void propagateUpdateTo(World world, BlockPos pos, BlockPos dest, RedstoneEntry entry, int depth, Type type) {
        if (depth < 0)
            return;
        updates.offer(new RedstoneUpdate(world, pos, dest, entry, depth, type));
    }

    public static void propagateUpdateEverywhere(World world, BlockPos pos, RedstoneEntry entry, int depth, Type type) {
        if (depth < 0)
            return;
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.REDSTONE_WIRE)) {
            _propagate(world, pos, entry, depth, type, state.get(Properties.NORTH_WIRE_CONNECTION), Direction.NORTH);
            _propagate(world, pos, entry, depth, type, state.get(Properties.WEST_WIRE_CONNECTION), Direction.WEST);
            _propagate(world, pos, entry, depth, type, state.get(Properties.SOUTH_WIRE_CONNECTION), Direction.SOUTH);
            _propagate(world, pos, entry, depth, type, state.get(Properties.EAST_WIRE_CONNECTION), Direction.EAST);
        }
    }

    private static void _propagate(World world, BlockPos pos, RedstoneEntry entry, int depth, Type type, WireConnection connection, Direction direction) {
        if (connection == WireConnection.SIDE) {
            BlockPos next = pos.offset(direction);
            BlockPos down = pos.down();
            if (!world.getBlockState(next).isSolidBlock(world, next)) {
                propagateUpdateTo(world, pos, next, entry, depth, type);
                if (type != Type.Propagate || world.getBlockState(down).isSolidBlock(world, down))
                    propagateUpdateTo(world, pos, next.down(), entry, depth, type);
            }
        }
        if (connection == WireConnection.UP) {
            BlockPos next = pos.offset(direction);
            BlockPos up = pos.up();
            if (!world.getBlockState(up).isSolidBlock(world, up))
                propagateUpdateTo(world, pos, next.up(), entry, depth, type);
        }

    }

    public static RedstoneUpdate getNextUpdate() {
        return updates.poll();
    }

}
