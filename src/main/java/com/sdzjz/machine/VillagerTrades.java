package com.sdzjz.machine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 村民交易表（v1：7 职业、每职业 4 条代表性交易，取材原版）。
 * 折扣：每级 -10% 输入（向下取整，至少 1），由交易所"治愈"提升，最高 5 级。
 */
public final class VillagerTrades {

    /** in2Item 可为 null（单输入交易）；enchant 非 null 时产物为该附魔的附魔书（m101）。 */
    public record Trade(String inItem, int inCount, String in2Item, int in2Count,
                        String outItem, int outCount, String enchant, int enchantLv) {}

    /** 职业 id → (工作方块, 交易列表)。工作方块用于"就业"消耗。 */
    public record Profession(String workstation, String nameKey, List<Trade> trades) {}

    public static final Map<String, Profession> ALL = new LinkedHashMap<>();

    static {
        ALL.put("farmer", new Profession("minecraft:composter", "sdzjz.prof.farmer", List.of(
                t("minecraft:wheat", 20, "minecraft:emerald", 1),
                t("minecraft:potato", 26, "minecraft:emerald", 1),
                t("minecraft:carrot", 22, "minecraft:emerald", 1),
                t("minecraft:emerald", 1, "minecraft:bread", 6))));
        ALL.put("librarian", new Profession("minecraft:lectern", "sdzjz.prof.librarian", List.of(
                t("minecraft:paper", 24, "minecraft:emerald", 1),
                t("minecraft:book", 4, "minecraft:emerald", 1),
                t("minecraft:emerald", 9, "minecraft:bookshelf", 1),
                t("minecraft:emerald", 5, "minecraft:lantern", 1),
                // m101 好附魔书（绿宝石+书购买，价格取材原版大师级区间；折扣对绿宝石生效）
                book("minecraft:mending", 1, 30),
                book("minecraft:silk_touch", 1, 20),
                book("minecraft:fortune", 3, 25),
                book("minecraft:efficiency", 5, 25),
                book("minecraft:sharpness", 5, 25),
                book("minecraft:looting", 3, 20),
                book("minecraft:protection", 4, 15),
                book("minecraft:infinity", 1, 25),
                book("minecraft:unbreaking", 3, 15),
                book("minecraft:channeling", 1, 25))));
        ALL.put("cartographer", new Profession("minecraft:cartography_table", "sdzjz.prof.cartographer", List.of(
                t("minecraft:paper", 24, "minecraft:emerald", 1),
                t("minecraft:glass_pane", 11, "minecraft:emerald", 1),
                t("minecraft:emerald", 7, "minecraft:map", 1),
                t("minecraft:emerald", 3, "minecraft:item_frame", 1))));
        ALL.put("toolsmith", new Profession("minecraft:smithing_table", "sdzjz.prof.toolsmith", List.of(
                t("minecraft:coal", 15, "minecraft:emerald", 1),
                t("minecraft:iron_ingot", 4, "minecraft:emerald", 1),
                t("minecraft:flint", 30, "minecraft:emerald", 1),
                t("minecraft:emerald", 1, "minecraft:iron_pickaxe", 1))));
        ALL.put("cleric", new Profession("minecraft:brewing_stand", "sdzjz.prof.cleric", List.of(
                t("minecraft:rotten_flesh", 32, "minecraft:emerald", 1),
                t("minecraft:gold_ingot", 3, "minecraft:emerald", 1),
                t("minecraft:emerald", 1, "minecraft:redstone", 2),
                t("minecraft:emerald", 5, "minecraft:ender_pearl", 1))));
        ALL.put("butcher", new Profession("minecraft:smoker", "sdzjz.prof.butcher", List.of(
                t("minecraft:porkchop", 14, "minecraft:emerald", 1),
                t("minecraft:chicken", 14, "minecraft:emerald", 1),
                t("minecraft:beef", 10, "minecraft:emerald", 1),
                t("minecraft:emerald", 1, "minecraft:cooked_porkchop", 5))));
        ALL.put("fisherman", new Profession("minecraft:barrel", "sdzjz.prof.fisherman", List.of(
                t("minecraft:cod", 6, "minecraft:emerald", 1),
                t("minecraft:salmon", 6, "minecraft:emerald", 1),
                t("minecraft:coal", 10, "minecraft:emerald", 1),
                t("minecraft:emerald", 1, "minecraft:cooked_cod", 6))));
    }

    private static Trade t(String in, int inN, String out, int outN) {
        return new Trade(in, inN, null, 0, out, outN, null, 0);
    }

    /** m101 附魔书交易：绿宝石×cost + 书×1 → 指定附魔书。 */
    private static Trade book(String enchantId, int lv, int emeraldCost) {
        return new Trade("minecraft:emerald", emeraldCost, "minecraft:book", 1,
                "minecraft:enchanted_book", 1, enchantId, lv);
    }

    public static List<String> professionIds() { return List.copyOf(ALL.keySet()); }

    /** 折扣后的输入数量：每级 -10%，至少 1。 */
    public static int discounted(int base, int discountLevel) {
        int cut = base * Math.min(5, Math.max(0, discountLevel)) / 10;
        return Math.max(1, base - cut);
    }
}
