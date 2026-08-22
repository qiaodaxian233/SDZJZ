package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.node.NodeTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * m378 虚空处理器——垃圾炼经验（区块机器线配套，生产闭环第三块：挖→筛→垃圾炼经验→经验驱动复制/附魔）。
 * 垃圾桶的升级款：吞掉路由进来的物品，按配置汇率（voidXpPerItemsEaten 件=1 经验，默认 64）
 * 炼成经验入**本核心经验池**（复制机/附魔工厂消费的那口池子；熔炼 0.1/件 是产出侧同族先例）。
 * 收料语义与垃圾桶同律：内置白名单，**空名单=连啥炼啥**，非空=只收名单内（名单外走默认路由回仓）；
 * 只吞推送来的——直连仓不抽（m150 防手滑边界），经逻辑节点转接=玩家明确布线授权照拉（m153 同律）。
 * 精确账本件（附魔书等）经授权链抵达=抹组件炼掉（原版磨石回收附魔经验的工业版）。
 * 余数不丢：不足一经验的件数记账进位（vc），凑齐即结。
 */
public class VoidProcessorItem extends MachineItem {

    public VoidProcessorItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    /** tick 侧结算写器（三键一笔：va 累计吞 / vc 汇率余数 / vn 累计炼得经验）。 */
    public static void settle(ItemStack s, long eatenDelta, long carryLeft, long xpDelta) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putLong("va", n.getLong("va") + eatenDelta);
        n.putLong("vc", carryLeft);
        if (xpDelta > 0) n.putLong("vn", n.getLong("vn") + xpDelta);
        com.sdzjz.item.ItemData.write(s, n);
    }

    /** 画布卡面第二行唯一口径。 */
    public static String canvasLine(ItemStack s) {
        long va = NodeTags.voidEaten(s);
        if (va <= 0) return "连线喂它垃圾";
        return "已吞 " + va + " → 经验 " + NodeTags.voidXp(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("垃圾炼经验：吞掉连线送来的物品，炼成经验进本核心经验池").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("汇率 " + Math.max(1, com.sdzjz.config.SdzjzConfig.get().voidXpPerItemsEaten)
                + " 件=1 经验（config 可调），余数记账进位不丢").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("白名单空=连啥炼啥 · 非空=只收名单内（名单外回仓）").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("只吞推送来的，直连仓不抽；经过滤器转接=授权照拉").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("吃下的东西没了换不回，白名单请慎配").withStyle(ChatFormatting.RED));
    }
}
