package com.sdzjz.retro;

import com.sdzjz.item.CaptureCageItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** m530（N2b）抓物笼 **1.20.1 世代壳**：{@link CaptureCageItem} 两代共用，本类只覆写 1.20.1 签名的
 *  {@code appendHoverText(ItemStack, Level, List, TooltipFlag)} 一句转调 {@link #hoverLines}（主线对位 {@code LegacyCaptureCageItem}）。 */
public final class CaptureCage120 extends CaptureCageItem {
    public CaptureCage120(Properties settings) { super(settings); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
        hoverLines(stack, tooltip);
    }
}
