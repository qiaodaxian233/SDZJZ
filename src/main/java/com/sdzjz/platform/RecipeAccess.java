package com.sdzjz.platform;

/**
 * 配方查询 SPI（多版本代际架构 Phase 1 第②刀，顾问方案 §9）。
 *
 * 约法三章：
 * - Common 侧（planner 族）只认本口，不认 RecipeManager/Registries/Identifier；
 * - 返回值全为 Common 纯值对象（CraftPlanner.Plan/Group 本就是 record+Map/String/int）；
 * * - level=不透明代际句柄（Legacy 下即原版 World 对象，服务端/客户端两侧都有各自的
 *   配方视图故不能持服务端引用）——Common 只透传**绝不触碰**；后续阶段若引入 LevelHandle
 *   值对象再收紧，Phase 1 先按顾问"不追求完美，只做到不直接 import 版本特有 API"。
 *
 * 实现：Legacy(≤1.21.11)=com.sdzjz.legacy.LegacyRecipeAccess；Modern(26.x)=Phase 3 后补。
 * 酿造/附魔/熔炼三口随 m363 各 planner 收口时增补，不预开空方法（防"巨型 Platform 接口"）。
 */
public interface RecipeAccess {

    /** 目标物品 id 的全部合成候选（顺位=minecraft 命名空间排前+配方 id 字典序，与 m234 逐位一致）。
     *  无配方/id 非法=空表。结果由调用方缓存（CraftPlanner.CACHE），实现侧不必缓存。 */
    java.util.List<com.sdzjz.machine.CraftPlanner.Plan> craftingPlans(Object level, String targetId);

    /** 物品的合成残留容器 id（奶桶→桶）；无残留=null。纯注册表查询与 level 无关。 */
    String craftRemainderOf(String itemId);

    /** m363 熔炼候选全表：输入 id → 候选列表（每候选={recipeId, outputId, outCount}，全 String/Integer
     *  纯值）。稳定选序（m346 pickStable）留在 Common——实现侧只管收集不管裁决。 */
    java.util.Map<String, java.util.List<Object[]>> smeltingCandidates(Object level);
}
