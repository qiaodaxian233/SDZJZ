package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 附魔工厂（m132）：放入画布后点节点徽章选目标附魔+等级（注册表全谱含模组附魔）；
 *  每本消耗 书+青金石+经验（从本画布核心经验池扣——刷怪塔/熔炉攒的经验直接喂），
 *  产出指定附魔书走精确存储入库。 */
public class EnchantFactoryItem extends MachineItem {

    public EnchantFactoryItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("周期 2 秒（吃加速/数量/并列升级）").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("每本消耗 书×1+青金石×3级+经验（扣本画布核心经验池）").formatted(Formatting.RED));
        tooltip.add(Text.literal("放入画布后，点节点右上角徽章选择附魔与等级").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("全附魔可选（含模组附魔）；产物直接入库（精确存储）").formatted(Formatting.DARK_GREEN));
    }
}
