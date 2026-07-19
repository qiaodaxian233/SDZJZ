package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("画布逻辑节点：来料在多条出线间均分").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("只分给吃得下的目标，余数轮转补齐").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("没人要的自动进存储（不堵死）").formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.literal("用法：机器→分配器→多台下游").formatted(Formatting.GRAY));
    }
}
