package mattymatty.redstone.mixins;

import net.minecraft.block.RedstoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RedstoneWireBlock.class)
public interface RedstoneWireAccessor {
    @Accessor("wiresGivePower")
    public abstract void setWiresGivePower(boolean wiresGivePower);
    @Accessor("wiresGivePower")
    public abstract boolean getWiresGivePower();
}
