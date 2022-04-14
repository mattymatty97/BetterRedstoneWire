package mattymatty.redstone.mixins;

import mattymatty.redstone.RedstoneWireBlockEntity;
import mattymatty.redstone.utility.RedstoneEntry;
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
    @Shadow
    @Final
    public static IntProperty POWER;
    @Shadow
    private boolean wiresGivePower;

    public RedstoneWireMixin(Settings settings) {
        super(settings);
    }

    @Shadow
    protected abstract void updateNeighbors(World world, BlockPos pos);

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneWireBlockEntity(pos, state);
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void update(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity redstoneWireBlockEntity) {
            // update power sources
            List<RedstoneEntry> powerSources = getLocalPowerSources(world, pos);
            redstoneWireBlockEntity.local_power_sources.clear();
            redstoneWireBlockEntity.local_power_sources.addAll(powerSources);

            //check if current power source is valid
            if (redstoneWireBlockEntity.getPower() > 0) {
                if (!redstoneWireBlockEntity.current_power_source.isValid(world)) {
                    //set current to null
                    RedstoneEntry to_remove = redstoneWireBlockEntity.current_power_source;
                    redstoneWireBlockEntity.current_power_source = null;
                    redstoneWireBlockEntity.parentPos = null;
                    //propagate the change to the neighbors ( they will also search for the first valid one and update backwards from there )
                    RedstoneUpdate.propagateUpdateEverywhere(world, pos, to_remove, max_depth, RedstoneUpdate.Type.Invalidate);
                    RedstoneUpdate update;
                    while ((update = RedstoneUpdate.getNextUpdate()) != null) {
                        update.run();
                    }
                } else
                    // check if the parent is still there
                    if (!redstoneWireBlockEntity.hasValidParent(world)) {
                        //set current to null
                        RedstoneEntry to_remove = redstoneWireBlockEntity.current_power_source;
                        redstoneWireBlockEntity.current_power_source = null;
                        redstoneWireBlockEntity.parentPos = null;
                        //propagate the change to the neighbors ( they will also search for the first valid one and update backwards from there )
                        RedstoneUpdate.propagateUpdateEverywhere(world, pos, to_remove, to_remove.getPower(), RedstoneUpdate.Type.Broken);
                        RedstoneUpdate update;
                        while ((update = RedstoneUpdate.getNextUpdate()) != null) {
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
                    RedstoneUpdate.propagateUpdateEverywhere(world, pos, next, next.getPower(), RedstoneUpdate.Type.Propagate);
                    RedstoneUpdate update;
                    while ((update = RedstoneUpdate.getNextUpdate()) != null) {
                        update.run();
                    }
                }
            }

            int i = state.get(POWER);
            int j = redstoneWireBlockEntity.getPower();
            if (i != j) {
                if (world.getBlockState(pos) == state) {
                    world.setBlockState(pos, state.with(POWER, j), Block.NOTIFY_LISTENERS);
                }
                updateNeighbors(world, pos);
            }
            ci.cancel();
        }
    }

    @Inject(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/RedstoneWireBlock;update(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", shift = At.Shift.BEFORE))
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify, CallbackInfo ci) {
        if (world.getBlockState(fromPos).isOf(Blocks.REDSTONE_WIRE))
            return;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity) {
            BlockPos vect = fromPos.subtract(pos);
            if (vect.getManhattanDistance(BlockPos.ORIGIN) == 1) {
                Direction direction = Direction.fromVector(vect);
                checkNewConnectionsInDirection(world, pos, direction);
            }
        }
    }

    @Inject(method = "onBlockAdded", at = @At("HEAD"))
    private void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneWireBlockEntity) {
            checkNewConnections(world, pos);
        }
    }

    private List<RedstoneEntry> getLocalPowerSources(World world, BlockPos pos) {
        this.wiresGivePower = false;
        List<RedstoneEntry> power_sources = new LinkedList<>();
        for (Direction direction : DIRECTIONS) {
            BlockPos next = pos.offset(direction);
            BlockState state = world.getBlockState(next);
            if (!state.isOf(Blocks.REDSTONE_WIRE)) {
                int level = world.getEmittedRedstonePower(next, direction);
                if (level > 0) {
                    RedstoneEntry entry = new RedstoneEntry(level, 0, next, direction);
                    power_sources.add(entry);
                }
            }
        }
        this.wiresGivePower = true;
        return power_sources;
    }

    private void checkNewConnections(World world, BlockPos pos) {
        RedstoneEntry next = new RedstoneEntry(0, 0, pos, null);
        RedstoneUpdate.propagateUpdateEverywhere(world, pos, next, 0, RedstoneUpdate.Type.Invalidate);
        RedstoneUpdate update;
        while ((update = RedstoneUpdate.getNextUpdate()) != null) {
            update.run();
        }
    }

    private void checkNewConnectionsInDirection(World world, BlockPos pos, Direction direction) {
        if (direction == Direction.DOWN)
            return;
        RedstoneEntry entry = new RedstoneEntry(0, 0, pos, null);
        BlockPos blockPos = pos.offset(direction);
        if (direction == Direction.UP) {
            if (!world.getBlockState(pos.up()).isSolidBlock(world, pos.up())) {
                for (Direction direction2 : Direction.Type.HORIZONTAL) {
                    BlockPos blockPos2 = blockPos.offset(direction2);
                    RedstoneUpdate.propagateUpdateTo(world, pos, blockPos2, entry, 0, RedstoneUpdate.Type.Invalidate);
                }
            }
        } else {
            RedstoneUpdate.propagateUpdateTo(world, pos, blockPos, entry, 0, RedstoneUpdate.Type.Invalidate);
            RedstoneUpdate.propagateUpdateTo(world, pos, blockPos.down(), entry, 0, RedstoneUpdate.Type.Invalidate);
        }

        RedstoneUpdate update;
        while ((update = RedstoneUpdate.getNextUpdate()) != null) {
            update.run();
        }
    }

}
