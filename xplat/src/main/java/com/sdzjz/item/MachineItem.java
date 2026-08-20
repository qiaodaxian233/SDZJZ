package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

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
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        super.appendTooltip(stack, context, tooltip, type);
        float sec = def.baseIntervalTicks() / 20f;
        tooltip.add(Component.literal("周期 " + (sec == (int) sec ? String.valueOf((int) sec) : String.format("%.1f", sec)) + " 秒")
                .formatted(ChatFormatting.GRAY));
        if (com.sdzjz.machine.Machines.smelterFamily(def.id())) { // m173 熔炉族共用提示
            tooltip.add(Component.literal("万能熔炼：接什么烧什么（原版熔炼配方全支持）").formatted(ChatFormatting.GOLD));
            tooltip.add(Component.literal("须画布接线供料（机器入线/存储供料线），防误烧库存").formatted(ChatFormatting.RED));
            if ("mega_super_smelter".equals(def.id()))
                tooltip.add(Component.literal("1728熔炉阵：每周期108组×并行×(1+数量升级)").formatted(ChatFormatting.AQUA));
            else
                tooltip.add(Component.literal("每周期一组×并行×(1+数量升级)，产物入存储/连线").formatted(ChatFormatting.AQUA));
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
            tooltip.add(Component.literal(in.toString()).formatted(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal("免费产出（对齐原版，不吃料）").formatted(ChatFormatting.DARK_GREEN));
        }
        for (MachineDef.Drop d : def.outputs()) {
            StringBuilder out = new StringBuilder("产出: ");
            out.append(d.min() == d.max() ? String.valueOf(d.min()) : d.min() + "-" + d.max());
            out.append("× ").append(itemName(d.item()));
            if (d.chance() < 1f) out.append("（").append((int) (d.chance() * 100)).append("%）");
            tooltip.add(Component.literal(out.toString()).formatted(ChatFormatting.AQUA));
        }
    }

    private static String itemName(String id) {
        return Component.translatable(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).getTranslationKey()).getString();
    }
}
