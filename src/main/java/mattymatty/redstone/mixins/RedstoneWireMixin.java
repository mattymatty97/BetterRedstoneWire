package mattymatty.redstone.mixins;

import mattymatty.redstone.RedstoneWireBlockEntity;
import mattymatty.redstone.RedstoneWireBlockEntity.RedstoneEntry;
import mattymatty.redstone.utility.BrokenRedstoneUpdate;
import mattymatty.redstone.utility.RedstoneUpdate;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedList;
import java.util.List;


@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireMixin extends Block implements BlockEntityProvider {

    private static final int max_depth = 30;


    @Shadow protected abstract void updateNeighbors(World world, BlockPos pos);

    @Shadow private boolean wiresGivePower;

    @Shadow @Final public static IntProperty POWER;

    public RedstoneWireMixin(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneWireBlockEntity(pos, state);
    }

    @Inject(method = "update", at=@At("HEAD"), cancellable = true)
    private void update(World world, BlockPos pos, BlockState state, CallbackInfo ci){
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity){
            // update power sources
            List<RedstoneEntry> powerSources = getLocalPowerSources(world, pos);
            redstoneWireBlockEntity.local_power_sources.clear();
            redstoneWireBlockEntity.local_power_sources.addAll(powerSources);

            //check if current power source is valid
            if (redstoneWireBlockEntity.getPower() > 0){
                if (!redstoneWireBlockEntity.current_power_source.isValid(world)){
                    //set current to null
                    RedstoneEntry to_remove = redstoneWireBlockEntity.current_power_source;
                    redstoneWireBlockEntity.current_power_source = null;
                    redstoneWireBlockEntity.parentPos = null;
                    //propagate the change to the neighbors ( they will also search for the first valid one and update backwards from there )
                    RedstoneUpdate.propagateUpdate(world,pos,to_remove,max_depth,true);
                    RedstoneUpdate update;
                    while ((update = RedstoneUpdate.getNextUpdate()) != null){
                        update.run();
                    }
                }else
                    // check if the parent is still there
                    if (!redstoneWireBlockEntity.hasValidParent(world)){
                        //set current to null
                        RedstoneEntry to_remove = redstoneWireBlockEntity.current_power_source;
                        redstoneWireBlockEntity.current_power_source = null;
                        redstoneWireBlockEntity.parentPos = null;
                        //propagate the change to the neighbors ( they will also search for the first valid one and update backwards from there )
                        BrokenRedstoneUpdate.propagateUpdate(world,pos,to_remove,to_remove.getPower(),true);
                        RedstoneUpdate update;
                        while ((update = RedstoneUpdate.getNextUpdate()) != null){
                            update.run();
                    }
                }
            }

            if (powerSources.size() > 0) {
                RedstoneEntry top = redstoneWireBlockEntity.local_power_sources.first();
                if (top.getPower() >= redstoneWireBlockEntity.getPower()) {
                    redstoneWireBlockEntity.current_power_source = top;
                    redstoneWireBlockEntity.parentPos = pos;
                    RedstoneEntry next = top.clone();
                    next.distance++;
                    RedstoneUpdate.propagateUpdate(world, pos, next, next.getPower(), false);
                    RedstoneUpdate update;
                    while ((update = RedstoneUpdate.getNextUpdate()) != null) {
                        update.run();
                    }
                }
            }

            int i = state.get(POWER);
            int j = redstoneWireBlockEntity.getPower();
            if (i!=j){
                if (world.getBlockState(pos) == state) {
                    world.setBlockState(pos, state.with(POWER, j), Block.NOTIFY_LISTENERS);
                }
                updateNeighbors(world, pos);
            }
            ci.cancel();
        }
    }

    @Inject(method = "neighborUpdate", at=@At(value = "INVOKE", target = "Lnet/minecraft/block/RedstoneWireBlock;update(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", shift = At.Shift.BEFORE))
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify, CallbackInfo ci) {
        if (world.getBlockState(fromPos).isOf(Blocks.REDSTONE_WIRE))
            return;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity){
            BlockPos vect = fromPos.subtract(pos);
            if (vect.getSquaredDistance(BlockPos.ORIGIN) == 1) {
                Direction direction = Direction.fromVector(fromPos.subtract(pos));
                checkNewConnectionsInDirection(world, pos, direction, redstoneWireBlockEntity);
            }
        }
    }

    private List<RedstoneEntry> getLocalPowerSources(World world, BlockPos pos){
        this.wiresGivePower = false;
        List<RedstoneEntry> power_sources = new LinkedList<>();
        for (Direction direction : DIRECTIONS){
            BlockPos next = pos.offset(direction);
            BlockState state = world.getBlockState(next);
            if (!state.isOf(Blocks.REDSTONE_WIRE)){
                int level = world.getEmittedRedstonePower(next,direction);
                if (level > 0){
                    RedstoneEntry entry = new RedstoneEntry(level,0, next, direction);
                    power_sources.add(entry);
                }
            }
        }
        this.wiresGivePower = true;
        return power_sources;
    }

    @Inject(method = "onBlockAdded", at=@At("HEAD"))
    private void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci){
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity) {
            checkNewConnections(world, pos, redstoneWireBlockEntity);
        }
    }

    private void checkNewConnections(World world, BlockPos pos, RedstoneWireBlockEntity redstoneWireBlockEntity) {
        boolean changed = false;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos blockPos = pos.offset(direction);
            BlockState blockState = world.getBlockState(blockPos);
            changed |= checkGreater(world, redstoneWireBlockEntity, blockPos);
            if (blockState.isSolidBlock(world, blockPos)){
                if (!world.getBlockState(pos.up()).isSolidBlock(world, pos.up())) {
                    BlockPos blockPos2 = blockPos.up();
                    changed |= checkGreater(world, redstoneWireBlockEntity, blockPos2);
                }
            }else{
                BlockPos blockPos3 = blockPos.down();
                changed |= checkGreater(world, redstoneWireBlockEntity, blockPos3);
            }
        }

        if (changed){
            RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
            next.distance++;
            RedstoneUpdate.propagateUpdate(world, pos, next, next.getPower(), false);
            RedstoneUpdate update;
            while ((update = RedstoneUpdate.getNextUpdate()) != null) {
                update.run();
            }
        }
    }

    private void checkNewConnectionsInDirection(World world, BlockPos pos, Direction direction, RedstoneWireBlockEntity redstoneWireBlockEntity) {
        boolean changed = false;
        BlockPos blockPos = pos.offset(direction);
        if (direction == Direction.DOWN)
            return;
        if (direction == Direction.UP){
            if (!world.getBlockState(pos.up()).isSolidBlock(world, pos.up())) {
                for (Direction direction2 : Direction.Type.HORIZONTAL) {
                    BlockPos blockPos2 = blockPos.offset(direction2);
                    changed |= checkGreater(world, redstoneWireBlockEntity, blockPos2);
                }
            }
        }else {
            BlockState blockState = world.getBlockState(blockPos);
            changed = checkGreater(world, redstoneWireBlockEntity, blockPos);
            if (blockState.isSolidBlock(world, blockPos)) {
                if (!world.getBlockState(pos.up()).isSolidBlock(world, pos.up())) {
                    BlockPos blockPos2 = blockPos.up();
                    changed |= checkGreater(world, redstoneWireBlockEntity, blockPos2);
                }
            } else {
                BlockPos blockPos3 = blockPos.down();
                changed |= checkGreater(world, redstoneWireBlockEntity, blockPos3);
            }
        }

        if (changed){
            RedstoneEntry next = redstoneWireBlockEntity.current_power_source.clone();
            next.distance++;
            RedstoneUpdate.propagateUpdate(world, pos, next, next.getPower(), false);
            RedstoneUpdate update;
            while ((update = RedstoneUpdate.getNextUpdate()) != null) {
                update.run();
            }
        }
    }

    private boolean checkGreater(World world, RedstoneWireBlockEntity redstoneWireBlockEntity, BlockPos blockPos2) {
        BlockEntity destBlockEntity = world.getBlockEntity(blockPos2);
        if (destBlockEntity instanceof RedstoneWireBlockEntity destRedstoneBlockEntity) {
            if(destRedstoneBlockEntity.getPower() > 1 && destRedstoneBlockEntity.getPower() > redstoneWireBlockEntity.getPower()){
                RedstoneEntry entry = destRedstoneBlockEntity.current_power_source.clone();
                entry.distance++;
                redstoneWireBlockEntity.current_power_source = entry;
                redstoneWireBlockEntity.parentPos = blockPos2;
                return true;
            }
        }
        return false;
    }

}
