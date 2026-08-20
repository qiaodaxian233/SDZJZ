package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * 过滤器节点（画布逻辑节点）：一入一出。
 * 白名单=只放行名单内物品；黑名单=拦下名单内物品。
 * 放行的沿出线流向下游；拦下的自动走 定向存储/默认路由（绝不堵死）。
 */
public class FilterNodeItem extends MachineItem {

    public FilterNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("画布逻辑节点：接在两台机器之间分流物品").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("白名单=只放行名单内 · 黑名单=拦下名单内").formatted(ChatFormatting.GRAY));
        tooltip.add(Component.literal("放行→沿出线下游；拦下→自动进存储").formatted(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("放入画布后右键节点配置").formatted(ChatFormatting.GRAY));
    }
}
