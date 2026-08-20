package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("画布逻辑节点：手动闸门，一键通断支线").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("开=直通 · 关=上游机器整台暂停（不白产）").formatted(ChatFormatting.GRAY));
        tooltip.add(Component.literal("放入画布后，点节点上的 开/关 按钮切换").formatted(ChatFormatting.DARK_GREEN));
    }
}
