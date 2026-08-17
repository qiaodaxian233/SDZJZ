package com.sdzjz.platform;

/**
 * 附魔配方口（m368 拆分）。
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
public interface EnchAccess {

    /** 附魔计划：目标串（"附魔id|等级"）→ Plan；非法=null。 */
    com.sdzjz.machine.EnchantPlanner.Plan enchantingPlan(Object level, String target);

    /** 附魔目标样板书句柄（Legacy=ItemStack）；非法=null。 */
    Object enchantTargetStack(Object level, String target);

    /** 附魔展示名句柄（Legacy=原版文本组件，罗马数字/诅咒红字）；非法=null。 */
    Object enchantTargetName(Object level, String target);
}
