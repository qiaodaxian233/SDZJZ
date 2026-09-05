package com.sdzjz.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** m530（N2b）抓物笼 **1.21 世代壳**：{@link CaptureCageItem} 两代共用后只剩 tooltip 覆写签名这一处世代差，一句转调 {@link #hoverLines}。
 *  1.20.1 对位 {@code CaptureCage120}（N2a 压缩包同律）。 */
public final class LegacyCaptureCageItem extends CaptureCageItem {
    public LegacyCaptureCageItem(Properties settings) { super(settings); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag type) {
        hoverLines(stack, tooltip);
    }
}
