package com.sdzjz.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * m310 原生大堆叠⑤（简写显示）：格内计数 >9999 时改画 K/M/B 缩写（DataPanelScreen.fmt
 * 同口径，m232 先例），不然 7 位数糊满整格。只在调用方没给 countOverride 时代打；
 * 其余渲染路径零改动。
 */
@Mixin(GuiGraphics.class)
public abstract class DrawContextCountMixin {

    @ModifyVariable(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String sdzjz$abbrevCount(String countOverride, Font tr, ItemStack stack) {
        if (countOverride != null || stack.isEmpty() || stack.getCount() <= 9999) return countOverride;
        return sdzjz$fmt(stack.getCount());
    }

    @Unique
    private static String sdzjz$fmt(long n) { // m232 口径：1K/1M/1B，一位小数，≤4 字符进格
        if (n >= 1_000_000_000L) return sdzjz$one(n / 1.0e9) + "B";
        if (n >= 1_000_000L)     return sdzjz$one(n / 1.0e6) + "M";
        return sdzjz$one(n / 1.0e3) + "K";
    }

    @Unique
    private static String sdzjz$one(double v) {
        return v >= 100 ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 10) / 10.0).replaceAll("\\.0$", "");
    }
}
