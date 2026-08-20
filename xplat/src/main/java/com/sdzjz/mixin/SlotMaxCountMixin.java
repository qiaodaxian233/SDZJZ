package com.sdzjz.mixin;

import com.sdzjz.config.SdzjzConfig;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * m310 原生大堆叠④：抬 Slot 通用格上限（无参 getMaxItemCount 默认回
 * inventory.getMaxCountPerStack()=99，是背包/箱子界面收纳的第二道钳）。
 * 带 ItemStack 参的重载与各结果格/燃料格子类的覆写一律不动——它们的收窄是业务语义；
 * 实际每格仍被 min(本值, stack.getMaxCount()) 组合钳住，不可堆叠物品照旧 1。
 */
@Mixin(Slot.class)
public abstract class SlotMaxCountMixin {

    @Inject(method = "getMaxItemCount()I", at = @At("RETURN"), cancellable = true)
    private void sdzjz$bigSlotCap(CallbackInfoReturnable<Integer> cir) {
        SdzjzConfig cfg = SdzjzConfig.get();
        if (cfg.bigStacks && cfg.bigStackMax > cir.getReturnValue()) cir.setReturnValue(cfg.bigStackMax);
    }
}
