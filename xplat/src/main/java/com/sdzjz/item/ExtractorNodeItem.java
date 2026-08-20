package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** m154 抽取节点（画布主动泵）：点击启停，开=无条件抽上游仓沿线推走。 */
public class ExtractorNodeItem extends MachineItem {
    public ExtractorNodeItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("主动泵：开=把上游仓的货抽出来沿线推走").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("不问下游要不要——想搬什么自己接过滤器管").formatted(Formatting.RED));
        tooltip.add(Text.literal("右键菜单启停；默认关，点了才抽").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("推不出去自动背压停抽，绝不抽出来又存回去空转").formatted(Formatting.DARK_GRAY));
    }
}
