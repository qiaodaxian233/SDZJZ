package com.sdzjz.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** m146 村民无限交易机的目标解析。目标串 "职业|交易序号"（如 "librarian|4"），
 *  存节点 CUSTOM_DATA "ct"（与合成/酿造/附魔目标同键同管线 NodeTargetPayload/setNodeTarget）。
 *  valid/trade 服务端校验用；displayName/iconStack 客户端选择器与徽章用。 */
public final class TradePlanner {
    private TradePlanner() {}

    public static String prof(String target) {
        if (target == null) return null;
        int k = target.indexOf('|');
        return k > 0 ? target.substring(0, k) : null;
    }

    public static VillagerTrades.Trade trade(String target) {
        String p = prof(target);
        if (p == null) return null;
        VillagerTrades.Profession pr = VillagerTrades.ALL.get(p);
        if (pr == null) return null;
        int idx;
        try { idx = Integer.parseInt(target.substring(target.indexOf('|') + 1)); }
        catch (Exception e) { return null; }
        return idx >= 0 && idx < pr.trades().size() ? pr.trades().get(idx) : null;
    }

    public static boolean valid(String target) { return trade(target) != null; }

    /** 全部目标串（职业表序 × 交易表序，选择器行源）。 */
    public static List<String> allTargets() {
        List<String> out = new ArrayList<>();
        for (var e : VillagerTrades.ALL.entrySet())
            for (int i = 0; i < e.getValue().trades().size(); i++)
                out.add(e.getKey() + "|" + i);
        return out;
    }

    /** 行/徽章展示名：「职业：付出 → 获得」（翻译键拼接，客户端解析；附魔书带附魔名+等级）。 */
    public static Text displayName(String target) {
        VillagerTrades.Trade t = trade(target);
        if (t == null) return Text.literal("?");
        MutableText s = Text.translatable(VillagerTrades.ALL.get(prof(target)).nameKey()).copy()
                .append(Text.literal("："))
                .append(itemName(t.inItem())).append(Text.literal("×" + t.inCount()));
        if (t.in2Item() != null)
            s.append(Text.literal("+")).append(itemName(t.in2Item())).append(Text.literal("×" + t.in2Count()));
        s.append(Text.literal(" → "));
        if (t.enchant() != null)
            s.append(Text.translatable("enchantment." + t.enchant().replace(':', '.')))
                    .append(Text.literal(" ")).append(Text.translatable("enchantment.level." + t.enchantLv()))
                    .append(Text.literal(" 书"));
        else s.append(itemName(t.outItem())).append(Text.literal("×" + t.outCount()));
        return s;
    }

    /** 徽章/行图标 = 产出物品（附魔书交易恒为附魔书图标）。 */
    public static ItemStack iconStack(String target) {
        VillagerTrades.Trade t = trade(target);
        if (t == null) return null;
        if (t.enchant() != null) return new ItemStack(Items.ENCHANTED_BOOK);
        return new ItemStack(Registries.ITEM.get(Identifier.of(t.outItem())));
    }

    private static Text itemName(String id) {
        return Text.translatable(Registries.ITEM.get(Identifier.of(id)).getTranslationKey());
    }
}
