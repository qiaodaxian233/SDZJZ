package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 开关节点（画布逻辑节点）：手动闸门，一键通断整条支线。
 * 开=直通转发；关=持料不放，且上游机器的全部出线目标都关闸时整台暂停（不白产）。
 */
public class SwitchNodeItem extends MachineItem {

    public SwitchNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("画布逻辑节点：手动闸门，一键通断支线").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("开=直通 · 关=上游机器整台暂停（不白产）").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("放入画布后，点节点上的 开/关 按钮切换").formatted(Formatting.DARK_GREEN));
    }
}
