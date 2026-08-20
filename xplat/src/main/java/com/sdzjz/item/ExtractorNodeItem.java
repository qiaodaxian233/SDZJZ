package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** m154 抽取节点（画布主动泵）：点击启停，开=无条件抽上游仓沿线推走。 */
public class ExtractorNodeItem extends MachineItem {
    public ExtractorNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("主动泵：开=把上游仓的货抽出来沿线推走").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("不问下游要不要——想搬什么自己接过滤器管").formatted(ChatFormatting.RED));
        tooltip.add(Component.literal("右键菜单启停；默认关，点了才抽").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("推不出去自动背压停抽，绝不抽出来又存回去空转").formatted(ChatFormatting.DARK_GRAY));
    }
}
