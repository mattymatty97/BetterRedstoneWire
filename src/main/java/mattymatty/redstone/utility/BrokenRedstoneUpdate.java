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

public class BrokenRedstoneUpdate extends RedstoneUpdate {

    BrokenRedstoneUpdate(World world,BlockPos from, BlockPos to, RedstoneEntry entry, int depth) {
        super(world, from, to, entry, depth, true);
    }

    @Override
    public void run() {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity){

            if (redstoneWireBlockEntity.getPower() > 0) {
                if (redstoneWireBlockEntity.current_power_source.sameSource(entry)) {
                    if(redstoneWireBlockEntity.getPower() < entry.getPower()) {
                        redstoneWireBlockEntity.current_power_source = null;
                        redstoneWireBlockEntity.parentPos = null;

                        RedstoneEntry entry = this.entry.clone();
                        entry.distance++;
                        BrokenRedstoneUpdate.propagateUpdate(world, pos,entry,this.depth-1);
                    }else{
                        if(redstoneWireBlockEntity.current_power_source.isValid(world)) {
                            RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                            next.distance++;
                            RedstoneUpdate.propagateUpdate(world, pos, next, next.getPower(), false);
                        }
                    }
                }else{
                    if(redstoneWireBlockEntity.current_power_source.isValid(world)) {
                        RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                        next.distance++;
                        RedstoneUpdate.propagateUpdate(world, pos, next, next.getPower(), false);
                    }
                }
            }

            if (redstoneWireBlockEntity.local_power_sources.size() > 0) {
                RedstoneEntry entry = redstoneWireBlockEntity.local_power_sources.first();
                if (entry.getPower() > redstoneWireBlockEntity.getPower() && entry.isValid(world)){
                    redstoneWireBlockEntity.current_power_source = entry;
                    redstoneWireBlockEntity.parentPos = pos;
                    this.removing = false;
                    this.depth = entry.getPower();

                    RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
                    next.distance++;
                    RedstoneUpdate.propagateUpdate(world, pos, next,this.depth-1,false);
                }
            }
        }
    }

    public static void propagateUpdate(World world, BlockPos pos, RedstoneEntry entry, int depth){
        if (depth < 0)
            return;
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.REDSTONE_WIRE)){
            _propagate(world, pos, entry, depth, state.get(Properties.NORTH_WIRE_CONNECTION), Direction.NORTH);
            _propagate(world, pos, entry, depth, state.get(Properties.WEST_WIRE_CONNECTION), Direction.WEST);
            _propagate(world, pos, entry, depth, state.get(Properties.SOUTH_WIRE_CONNECTION), Direction.SOUTH);
            _propagate(world, pos, entry, depth, state.get(Properties.EAST_WIRE_CONNECTION), Direction.EAST);
        }
    }

    private static void _propagate(World world, BlockPos pos, RedstoneEntry entry, int depth, WireConnection connection, Direction direction) {
        if (connection == WireConnection.SIDE){
            BlockPos next = pos.offset(direction);
            BlockState state = world.getBlockState(next);
            if ( !world.getBlockState(next).isSolidBlock(world,next) ){
                if (state.isOf(Blocks.REDSTONE_WIRE))
                    updates.add(new BrokenRedstoneUpdate(world, pos, next, entry, depth));
                else {
                    state = world.getBlockState(next.offset(Direction.DOWN));
                    if (state.isOf(Blocks.REDSTONE_WIRE))
                        updates.add(new BrokenRedstoneUpdate(world, pos, next.offset(Direction.DOWN), entry, depth));
                }
            }
        }
        if ( connection == WireConnection.UP ){
            BlockPos next = pos.offset(direction);
            BlockPos up = pos.offset(Direction.UP);
            BlockState state = world.getBlockState(next.offset(Direction.UP));
            if (!world.getBlockState(up).isSolidBlock(world,up) && state.isOf(Blocks.REDSTONE_WIRE))
                updates.add(new BrokenRedstoneUpdate(world, pos, next.offset(Direction.UP), entry, depth));
        }

    }

}
