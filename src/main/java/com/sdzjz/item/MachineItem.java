package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** 机器物品：携带自己的 MachineDef。结构核心读取 def 即知产什么/多久/几个。 */
public class MachineItem extends Item {
    private final MachineDef def;

    public MachineItem(Settings settings, MachineDef def) {
        super(settings);
        this.def = def;
    }

    public MachineDef def() {
        return def;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        float sec = def.baseIntervalTicks() / 20f;
        tooltip.add(Text.literal("周期 " + (sec == (int) sec ? String.valueOf((int) sec) : String.format("%.1f", sec)) + " 秒")
                .formatted(Formatting.GRAY));
        if ("super_smelter".equals(def.id())) {
            tooltip.add(Text.literal("万能熔炼：接什么烧什么（原版熔炼配方全支持）").formatted(Formatting.GOLD));
            tooltip.add(Text.literal("每周期一组×并行×(1+数量升级)，产物入存储/连线").formatted(Formatting.AQUA));
            return;
        }
        if (def.consumesInputs()) {
            StringBuilder in = new StringBuilder("消耗: ");
            boolean first = true;
            for (MachineDef.Input i : def.inputs()) {
                if (!first) in.append(", ");
                in.append(i.count()).append("× ").append(itemName(i.item()));
                first = false;
            }
            tooltip.add(Text.literal(in.toString()).formatted(Formatting.RED));
        } else {
            tooltip.add(Text.literal("免费产出（对齐原版，不吃料）").formatted(Formatting.DARK_GREEN));
        }
        for (MachineDef.Drop d : def.outputs()) {
            StringBuilder out = new StringBuilder("产出: ");
            out.append(d.min() == d.max() ? String.valueOf(d.min()) : d.min() + "-" + d.max());
            out.append("× ").append(itemName(d.item()));
            if (d.chance() < 1f) out.append("（").append((int) (d.chance() * 100)).append("%）");
            tooltip.add(Text.literal(out.toString()).formatted(Formatting.AQUA));
        }
    }

    private static String itemName(String id) {
        return Text.translatable(Registries.ITEM.get(Identifier.of(id)).getTranslationKey()).getString();
    }
}
