package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * 分配器节点（画布逻辑节点）：把上游来料在所有"吃得下"的出线目标间均分（余数轮转），
 * 没人要的部分走定向存储/默认路由。解决默认分发"先连的先吃饱"。
 */
public class DistributorNodeItem extends MachineItem {

    public DistributorNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("画布逻辑节点：来料在多条出线间均分").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("只分给吃得下的目标，余数轮转补齐").formatted(ChatFormatting.GRAY));
        tooltip.add(Component.literal("没人要的自动进存储（不堵死）").formatted(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("用法：机器→分配器→多台下游").formatted(ChatFormatting.GRAY));
    }
}
