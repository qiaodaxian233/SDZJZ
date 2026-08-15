package com.sdzjz.machine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 村民交易表（v1：7 职业代表性交易，取材原版；m333 起逐条带最低等级）。
 * 折扣：每级 -10% 输入（向下取整，至少 1），由交易所"治愈"提升，最高 5 级。
 * 等级（m333）：合同 1..5 级（新手/学徒/老手/专家/大师），交易攒经验按原版门槛升级，
 * 高档交易到级解锁。**序号锚定铁律**：交易机目标串是"职业|序号"，本表只许在各职业
 * 表尾追加、绝不插队/删行/换序——插队=全服已选目标静默漂移（廿二号用例当判官）。
 */
public final class VillagerTrades {

    /** in2Item 可为 null（单输入交易）；enchant 非 null 时产物为该附魔的附魔书（m101）；
     *  minLevel=解锁所需合同等级 1..5（m333）。 */
    public record Trade(String inItem, int inCount, String in2Item, int in2Count,
                        String outItem, int outCount, String enchant, int enchantLv, int minLevel) {}

    /** 职业 id → (工作方块, 交易列表)。工作方块用于"就业"消耗。 */
    public record Profession(String workstation, String nameKey, List<Trade> trades) {}

    public static final Map<String, Profession> ALL = new LinkedHashMap<>();

    static {
        ALL.put("farmer", new Profession("minecraft:composter", "sdzjz.prof.farmer", List.of(
                t(1, "minecraft:wheat", 20, "minecraft:emerald", 1),
                t(1, "minecraft:potato", 26, "minecraft:emerald", 1),
                t(1, "minecraft:carrot", 22, "minecraft:emerald", 1),
                t(1, "minecraft:emerald", 1, "minecraft:bread", 6),
                // m333 表尾追加（原版学徒/老手/大师档）
                t(2, "minecraft:pumpkin", 6, "minecraft:emerald", 1),
                t(3, "minecraft:melon", 4, "minecraft:emerald", 1),
                t(5, "minecraft:emerald", 3, "minecraft:golden_carrot", 3))));
        ALL.put("librarian", new Profession("minecraft:lectern", "sdzjz.prof.librarian", List.of(
                t(1, "minecraft:paper", 24, "minecraft:emerald", 1),
                t(2, "minecraft:book", 4, "minecraft:emerald", 1),
                t(1, "minecraft:emerald", 9, "minecraft:bookshelf", 1),
                t(2, "minecraft:emerald", 5, "minecraft:lantern", 1),
                // m101 好附魔书（绿宝石+书购买，价格取材原版大师级区间；折扣对绿宝石生效）
                // m333 分档：顶级书（经验修补/无限）压大师，主力书专家，入门书学徒——序号 4..13 锚定不动
                book(5, "minecraft:mending", 1, 30),
                book(3, "minecraft:silk_touch", 1, 20),
                book(4, "minecraft:fortune", 3, 25),
                book(4, "minecraft:efficiency", 5, 25),
                book(4, "minecraft:sharpness", 5, 25),
                book(3, "minecraft:looting", 3, 20),
                book(2, "minecraft:protection", 4, 15),
                book(5, "minecraft:infinity", 1, 25),
                book(2, "minecraft:unbreaking", 3, 15),
                book(3, "minecraft:channeling", 1, 25))));
        ALL.put("cartographer", new Profession("minecraft:cartography_table", "sdzjz.prof.cartographer", List.of(
                t(1, "minecraft:paper", 24, "minecraft:emerald", 1),
                t(2, "minecraft:glass_pane", 11, "minecraft:emerald", 1),
                t(1, "minecraft:emerald", 7, "minecraft:map", 1),
                t(3, "minecraft:emerald", 3, "minecraft:item_frame", 1))));
        ALL.put("toolsmith", new Profession("minecraft:smithing_table", "sdzjz.prof.toolsmith", List.of(
                t(1, "minecraft:coal", 15, "minecraft:emerald", 1),
                t(2, "minecraft:iron_ingot", 4, "minecraft:emerald", 1),
                t(3, "minecraft:flint", 30, "minecraft:emerald", 1),
                t(2, "minecraft:emerald", 1, "minecraft:iron_pickaxe", 1),
                // m333 表尾追加（原版专家收钻石）
                t(4, "minecraft:diamond", 1, "minecraft:emerald", 1))));
        ALL.put("cleric", new Profession("minecraft:brewing_stand", "sdzjz.prof.cleric", List.of(
                t(2, "minecraft:emerald", 1, "minecraft:lapis_lazuli", 1), // m153 青金石量产口（原版牧师学徒价）——附魔工厂的青金石此前全表无产路
                t(1, "minecraft:rotten_flesh", 32, "minecraft:emerald", 1),
                t(3, "minecraft:gold_ingot", 3, "minecraft:emerald", 1),
                t(1, "minecraft:emerald", 1, "minecraft:redstone", 2),
                t(4, "minecraft:emerald", 5, "minecraft:ender_pearl", 1))));
        ALL.put("butcher", new Profession("minecraft:smoker", "sdzjz.prof.butcher", List.of(
                t(1, "minecraft:porkchop", 14, "minecraft:emerald", 1),
                t(1, "minecraft:chicken", 14, "minecraft:emerald", 1),
                t(2, "minecraft:beef", 10, "minecraft:emerald", 1),
                t(2, "minecraft:emerald", 1, "minecraft:cooked_porkchop", 5))));
        ALL.put("fisherman", new Profession("minecraft:barrel", "sdzjz.prof.fisherman", List.of(
                t(1, "minecraft:string", 14, "minecraft:emerald", 1), // m146 收线（价取原版制箭师14线/宝石；渔夫做鱼竿用线顺理成章）——刷线机→交易机直连场景的落地条目
                t(1, "minecraft:cod", 6, "minecraft:emerald", 1),
                t(2, "minecraft:salmon", 6, "minecraft:emerald", 1),
                t(1, "minecraft:coal", 10, "minecraft:emerald", 1),
                t(1, "minecraft:emerald", 1, "minecraft:cooked_cod", 6))));
    }

    private static Trade t(int lv, String in, int inN, String out, int outN) {
        return new Trade(in, inN, null, 0, out, outN, null, 0, lv);
    }

    /** m101 附魔书交易：绿宝石×cost + 书×1 → 指定附魔书。 */
    private static Trade book(int lv, String enchantId, int elv, int emeraldCost) {
        return new Trade("minecraft:emerald", emeraldCost, "minecraft:book", 1,
                "minecraft:enchanted_book", 1, enchantId, elv, lv);
    }

    public static List<String> professionIds() { return List.copyOf(ALL.keySet()); }

    /** 折扣后的输入数量：每级 -10%，至少 1。 */
    public static int discounted(int base, int discountLevel) {
        int cut = base * Math.min(5, Math.max(0, discountLevel)) / 10;
        return Math.max(1, base - cut);
    }

    // ---- m333 等级口径 ----

    /** 升到下一级所需的**累计**经验（原版村民门槛：→学徒10 →老手70 →专家150 →大师250）。
     *  下标=当前等级：LEVEL_XP[1]=升 2 级门槛 … LEVEL_XP[4]=升 5 级门槛。 */
    public static final int[] LEVEL_XP = {0, 10, 70, 150, 250};

    private static final String[] LEVEL_NAMES = {"", "新手", "学徒", "老手", "专家", "大师"};

    public static String levelName(int lv) {
        return LEVEL_NAMES[Math.max(1, Math.min(5, lv))];
    }

    /** 单笔交易给合同的经验：越高档的交易喂得越多（2+2×交易等级，原版 2~10 区间同量级）。 */
    public static int tradeXp(Trade t) {
        return 2 + 2 * t.minLevel();
    }
}
