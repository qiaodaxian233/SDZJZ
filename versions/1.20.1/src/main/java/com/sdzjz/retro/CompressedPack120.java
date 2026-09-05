package com.sdzjz.retro;

import com.sdzjz.item.CompressedPackItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** m529（N2a）压缩材料包 **1.20.1 世代壳**：{@link CompressedPackItem} 两代共用，本类只覆写 1.20.1 签名的
 *  {@code appendHoverText(ItemStack, Level, List, TooltipFlag)} 一句转调 {@link #hoverLines}（主线对位 {@code LegacyCompressedPackItem}）。 */
public final class CompressedPack120 extends CompressedPackItem {
    public CompressedPack120(Properties settings, int ratio) { super(settings, ratio); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
        hoverLines(stack, tooltip);
    }
}
