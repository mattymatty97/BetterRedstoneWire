package mattymatty.redstone.utility;

import mattymatty.redstone.RedstoneWireBlockEntity;
import mattymatty.redstone.RedstoneWireBlockEntity.RedstoneEntry;
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

    public World world;
    public BlockPos pos;
    public BlockPos from;
    public RedstoneEntry entry;
    public int depth;
    public boolean removing;

    RedstoneUpdate(World world, BlockPos from, BlockPos pos, RedstoneEntry entry, int depth, boolean removing) {
        this.world = world;
        this.pos = pos;
        this.from = from;
        this.entry = entry;
        this.depth = depth;
        this.removing = removing;
    }

    @Override
    public void run() {
        boolean changed = false;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity){
            if (this.removing){
                if (redstoneWireBlockEntity.getPower() > 0) {
                    if (redstoneWireBlockEntity.current_power_source.sameSource(entry)) {
                        redstoneWireBlockEntity.current_power_source = null;
                        changed = true;
                    }else{
                        if(redstoneWireBlockEntity.current_power_source.isValid(world)) {
                            RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                            next.distance++;
                            propagateUpdate(world, pos, next, next.getPower(), false);
                        }
                    }
                }
            }else{
                if (entry.getPower() > redstoneWireBlockEntity.getPower()){
                    redstoneWireBlockEntity.current_power_source = entry;
                    redstoneWireBlockEntity.parentPos = from;
                    changed = true;
                }
            }

            if (redstoneWireBlockEntity.local_power_sources.size() > 0) {
                RedstoneEntry entry = redstoneWireBlockEntity.local_power_sources.first();
                if (entry.getPower() > redstoneWireBlockEntity.getPower() && entry.isValid(world)){
                    redstoneWireBlockEntity.current_power_source = entry;
                    redstoneWireBlockEntity.parentPos = pos;
                    changed = true;
                    this.removing = false;
                    this.depth = entry.getPower();
                }
            }

            if (changed){
                if (this.removing){
                    propagateUpdate(world, pos,entry,this.depth-1, true);
                }else{
                    assert redstoneWireBlockEntity.current_power_source != null;
                    RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                    next.distance++;
                    propagateUpdate(world, pos,next,this.depth-1,false);
                }
            }
        }
    }


    static final Queue<RedstoneUpdate> updates = new LinkedList<>();

    public static void propagateUpdate(World world, BlockPos pos, RedstoneEntry entry, int depth, boolean removing){
        if (depth < 0)
            return;
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.REDSTONE_WIRE)){
            _propagate(world, pos, entry, depth, removing, state.get(Properties.NORTH_WIRE_CONNECTION), Direction.NORTH);
            _propagate(world, pos, entry, depth, removing, state.get(Properties.WEST_WIRE_CONNECTION), Direction.WEST);
            _propagate(world, pos, entry, depth, removing, state.get(Properties.SOUTH_WIRE_CONNECTION), Direction.SOUTH);
            _propagate(world, pos, entry, depth, removing, state.get(Properties.EAST_WIRE_CONNECTION), Direction.EAST);
        }
    }

    private static void _propagate(World world, BlockPos pos, RedstoneEntry entry, int depth, boolean removing, WireConnection connection, Direction direction) {
        if (connection == WireConnection.SIDE){
            BlockPos next = pos.offset(direction);
            BlockPos down = pos.offset(Direction.DOWN);
            BlockState state = world.getBlockState(next);
            if ( !world.getBlockState(next).isSolidBlock(world,next) ){
                if (state.isOf(Blocks.REDSTONE_WIRE))
                    updates.add(new RedstoneUpdate(world, pos, next, entry, depth, removing));
                else {
                    state = world.getBlockState(next.offset(Direction.DOWN));
                    if ( ( removing || world.getBlockState(down).isSolidBlock(world,down)) && state.isOf(Blocks.REDSTONE_WIRE))
                        updates.add(new RedstoneUpdate(world, pos, next.offset(Direction.DOWN), entry, depth, removing));
                }
            }
        }
        if ( connection == WireConnection.UP ){
            BlockPos next = pos.offset(direction);
            BlockPos up = pos.offset(Direction.UP);
            BlockState state = world.getBlockState(next.offset(Direction.UP));
            if (!world.getBlockState(up).isSolidBlock(world,up) && state.isOf(Blocks.REDSTONE_WIRE))
                updates.add(new RedstoneUpdate(world, pos, next.offset(Direction.UP), entry, depth, removing));
        }

    }

    public static RedstoneUpdate getNextUpdate(){
        return updates.poll();
    }

}
