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
        ResourceLocation ident = ResourceLocation.tryParse(id);
        if (ident == null) return false;
        return BuiltInRegistries.ITEM.get(ident) != net.minecraft.world.item.Items.AIR;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("放入画布后，点徽章选择复制目标（全物品·可搜索）").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("母本制：网络里须有 ≥1 件目标物品压阵，母本不消耗").formatted(ChatFormatting.RED));
        tooltip.add(Component.literal("每复制 1 件消耗核心经验池 "
                + Math.max(1, com.sdzjz.config.SdzjzConfig.get().duplicatorXpPerItem)
                + " 经验（config 可调）").formatted(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("组件不复制：附魔书/药水复制出来是素体（物流只认 id 记账）").formatted(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("产出走出线或存回存储").formatted(ChatFormatting.AQUA));
    }
}
