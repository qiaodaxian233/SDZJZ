package com.sdzjz.mixin;

import com.sdzjz.config.SdzjzConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * m310 原生大堆叠③（行为核心）：getMaxCount 按配置抬到 bigStackMax。
 * 只抬"原版可堆叠"（vanilla maxCount>1）——maxCount=1 的工具/盔甲/药水不动，
 * 防耐久/组件件合并出鬼（精确账本教训同源）。全库自家代码 m163c 起已全动态读
 * getMaxCount，本注入生效即全 MOD 界面白捡兼容；配置关=原样直返，零行为变化。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMaxCountMixin {

    @Inject(method = "getMaxCount", at = @At("RETURN"), cancellable = true)
    private void sdzjz$bigMaxCount(CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue();
        if (vanilla <= 1) return; // 不可堆叠物品永不抬
        SdzjzConfig cfg = SdzjzConfig.get();
        if (cfg.bigStacks && cfg.bigStackMax > vanilla) cir.setReturnValue(cfg.bigStackMax);
    }
}
