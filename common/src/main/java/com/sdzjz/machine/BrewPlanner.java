package com.sdzjz.machine;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 酿造塔的配方解析（m131b）：给定目标药水串「药水id|形态」，从原版酿造注册表
 * （BrewingRecipeRegistry）出发做 BFS——起点=水瓶，边=有效酿造材料的一步 craft()，
 * 终点=目标药水栈。天然覆盖 延长(redstone)/强化(glowstone)/喷溅(gunpowder)/
 * 滞留(dragon_breath) 全链，且第三方模组照原版路子注册的药水配方一并支持。
 * 解析结果按目标串缓存（服务器停止时清空，见 Sdzjz 的 SERVER_STOPPED，与 CraftPlanner 同位）。
 *
 * 形态码：p=普通药水 s=喷溅 l=滞留。延长/强化是独立的药水注册项
 * （minecraft:long_swiftness / minecraft:strong_swiftness），选择器里直接列出即可。
 */
public final class BrewPlanner {
    private BrewPlanner() {}

    /** 燃料：原版 1 烈焰粉 = 20 次酿造操作（一次操作=一批 3 瓶走一步）。 */
    public static final String FUEL_ID = "minecraft:blaze_powder";
    public static final int OPS_PER_FUEL = 20;
    /** 原版酿造台一批 3 瓶。 */
    public static final int BOTTLES_PER_BATCH = 3;

    /**
     * needs: 每批(3 瓶)消耗 物品id→数量（含玻璃瓶×3 与各步材料各×1，不含燃料——
     * 燃料按 steps 在 tick 里聚合结算，材料本身含烈焰粉的力量药水两账并存不混）；
     * steps: 酿造步数；result: 目标药水样板栈（count=1，带 POTION_CONTENTS，
     * 入库走 m130 精确账本）。
     */
    public record Plan(Map<String, Integer> needs, int steps, Object result) {} // m364 不透明产物句柄（Legacy=原版物品栈），消费者全在版本侧

    private static final Map<String, Optional<Plan>> CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear(); // m364 材料缓存随解析层迁 LegacyRecipeAccess，其失效由代际引导端（Sdzjz reload 钩）同拍清
    }

    /**
     * 目标串 → 展示/校验用样板栈；解析失败返回 null。纯注册表实现，客户端画徽章同用。
     * 目标串格式："minecraft:strong_swiftness|s"。
     */

    /** m364 目标样板栈句柄（Legacy=原版物品栈）；非法=null。委托代际适配器，调用面自行强转。 */
    public static Object targetStack(String target) {
        return com.sdzjz.platform.Platform.recipes().brewTargetStack(target);
    }

    /** 返回目标药水的酿造计划；串非法/不可达返回 null。 */
    /** m357 计时壳（PHASES 关=直通零开销，审计⑤轮③：规划器分桶入账）。 */
    public static Plan plan(Object world, String target) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return plan0(world, target);
        long __t = System.nanoTime();
        try { return plan0(world, target); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_P_BREW, System.nanoTime() - __t); }
    }

    private static Plan plan0(Object world, String target) {
        return CACHE.computeIfAbsent(target, t -> Optional.ofNullable(
                com.sdzjz.platform.Platform.recipes().brewingPlan(world, t))).orElse(null); // m364 解析层下沉代际适配器
    }

    /** 全物品过一遍 isValidIngredient 收集酿造材料（一次性，注册表不会中途变）。 */

    /** BFS 状态键=容器物品id+药水id（原版酿造产物必为注册药水，自定义效果不在酿造图里）。 */

}
