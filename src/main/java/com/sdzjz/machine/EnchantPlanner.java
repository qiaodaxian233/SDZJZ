package com.sdzjz.machine;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔工厂的成本解析（m132）：给定目标串「附魔id|等级」（如 "minecraft:sharpness|5"），
 * 从附魔动态注册表（1.21 起附魔是数据驱动注册表，须经 world 取）解析出一本书的产出计划。
 * 注册表驱动=第三方模组附魔（含诅咒）天然全谱支持，零白名单。
 *
 * 成本（原版锚定，公式集中在此处便于调参）：
 * - 书 ×1、青金石 ×(3×等级)——附魔台单次最多吃 3 青金石，按等级线性放大；
 * - 经验 = 书倍率B × 等级 × 25 点，从画布核心的经验池(xpPool)扣——
 *   B = max(1, anvilCost/2)（原版铁砧的"附魔书减半"倍率：1/2/4/8 → 1/1/2/4），
 *   刷怪塔/熔炉在同一画布攒的经验直接喂附魔，闭环不引入新经济。
 *
 * 缓存按目标串（Plan 持有注册表绑定的样板栈，服务器停止时清空，见 Sdzjz 的
 * SERVER_STOPPED，与 Brew/CraftPlanner 同位）；客户端只走 targetStack/targetName
 * 无缓存（附魔注册表随存档/数据包变，不做跨世界静态缓存）。
 */
public final class EnchantPlanner {
    private EnchantPlanner() {}

    public static final String BOOK_ID = "minecraft:book";
    public static final String LAPIS_ID = "minecraft:lapis_lazuli";
    /** 每级青金石消耗。 */
    public static final int LAPIS_PER_LEVEL = 3;
    /** 经验点/单位权重（权重=书倍率×等级）。 */
    public static final int XP_PER_WEIGHT = 25;

    /**
     * needs: 物品消耗（书×1 + 青金石×3L）；xpCost: 每本书经验点（核心经验池扣）；
     * result: 目标附魔书样板栈（count=1，入库走 m130 精确账本）。
     */
    public record Plan(Map<String, Integer> needs, int xpCost, Object result) {} // m364 不透明产物句柄（Legacy=原版物品栈），消费者全在版本侧

    private static final Map<String, Optional<Plan>> CACHE = new ConcurrentHashMap<>();

    /** m364 目标样板书句柄（Legacy=原版物品栈）；非法=null。 */
    public static Object targetStack(Object world, String target) {
        return com.sdzjz.platform.Platform.recipes().enchantTargetStack(world, target);
    }

    /** m364 展示名句柄（Legacy=原版文本组件）；非法=null。 */
    public static Object targetName(Object world, String target) {
        return com.sdzjz.platform.Platform.recipes().enchantTargetName(world, target);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** 返回目标附魔书的生产计划；串非法/附魔不存在/等级越界返回 null。 */
    /** m357 计时壳（PHASES 关=直通零开销）。 */
    public static Plan plan(Object world, String target) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return plan0(world, target);
        long __t = System.nanoTime();
        try { return plan0(world, target); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_P_ENCH, System.nanoTime() - __t); }
    }

    private static Plan plan0(Object world, String target) {
        return CACHE.computeIfAbsent(target, t -> Optional.ofNullable(
                com.sdzjz.platform.Platform.recipes().enchantingPlan(world, t))).orElse(null); // m364 解析层下沉代际适配器
    }

    /** 目标串 → 附魔书样板栈；解析失败返回 null。客户端画徽章/选择器同用（无缓存）。 */

    /** 目标串 → 展示名（原版 Enchantment.getName：自带罗马数字等级与诅咒红字）。 */



}
