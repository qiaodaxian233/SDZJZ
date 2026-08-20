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

/**
 * m334 无限复制机（作者点名：本体配方超难 + 能复制一切物品）。
 * 目标物品画布徽章选（全物品注册表网格+搜索）；母本制：网络里 ≥1 件目标压阵（不消耗）；
 * 每复制 1 件烧核心经验池 duplicatorXpPerItem——经验是全 MOD 终局货币（附魔工厂/幽匿线同源），
 * 复制没有免费午餐。组件不复制：产物是干净 id 计数件（机器组合.md 第 1 条物理不为复制机开洞）。
 */
public class DuplicatorItem extends MachineItem {

    public DuplicatorItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    /** 目标合法性唯一口径（setNodeTarget 服务端闸 / tick 闸 / 廿三号用例直测）：
     *  可解析、已注册、非空气。 */
    public static boolean validTarget(String id) {
        if (id == null || id.isEmpty()) return false;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return false;
        return Registries.ITEM.get(ident) != net.minecraft.item.Items.AIR;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("放入画布后，点徽章选择复制目标（全物品·可搜索）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("母本制：网络里须有 ≥1 件目标物品压阵，母本不消耗").formatted(Formatting.RED));
        tooltip.add(Text.literal("每复制 1 件消耗核心经验池 "
                + Math.max(1, com.sdzjz.config.SdzjzConfig.get().duplicatorXpPerItem)
                + " 经验（config 可调）").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("组件不复制：附魔书/药水复制出来是素体（物流只认 id 记账）").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("产出走出线或存回存储").formatted(Formatting.AQUA));
    }
}
