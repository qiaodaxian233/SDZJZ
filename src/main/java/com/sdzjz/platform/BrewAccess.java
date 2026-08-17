package com.sdzjz.platform;

/**
 * 酿造配方口（m368 拆分；产物/样板=不透明句柄，消费者全在版本侧）。
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
public interface BrewAccess {

    // ===== m364 酿造/附魔（解析层整体下沉：Brew=对酿造注册表的活栈 BFS 模拟、Ench=注册表校验+样板书，
    // 本就属于"配方访问"；产物/样板=不透明句柄 Object（Legacy=ItemStack）——Common 只透传，
    // 全部消费者（SCBE 产出/客户端徽章）都在版本侧，故 ItemView 值对象推迟到存储层需要时再设计）。 =====

    /** 酿造计划：目标串（"药水id|p/s/l"）→ Plan（needs/steps/result 句柄）；非法/不可达=null。 */
    com.sdzjz.machine.BrewPlanner.Plan brewingPlan(Object level, String target);

    /** 酿造目标样板栈句柄（Legacy=ItemStack）；非法=null。客户端徽章/服务端校验共用，与 level 无关。 */
    Object brewTargetStack(String target);

}
