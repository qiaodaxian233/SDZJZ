package com.sdzjz.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** m529（N2a）压缩材料包 **1.21 世代壳**：{@link CompressedPackItem} 两代共用后只剩 tooltip 覆写签名这一处世代差
 *  （1.21 {@code Item.TooltipContext} / 1.20.1 {@code Level}），体一句转调 {@link #hoverLines}。1.20.1 对位 {@code CompressedPack120}。 */
public final class LegacyCompressedPackItem extends CompressedPackItem {
    public LegacyCompressedPackItem(Properties settings, int ratio) { super(settings, ratio); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag type) {
        hoverLines(stack, tooltip);
    }
}
