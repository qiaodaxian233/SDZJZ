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
/**
 * 配方查询组合入口（m368 拆四域接口，顾问⑥轮④：RecipeAccess 已达八口触警戒线——拆
 * Craft/Smelt/Brew/Ench 四域，26.x 可分域迁移各自 ModernXxxAccess 再组合；本口只做聚合，
 * **此后不许再往这里塞新方法**——新域=新接口）。Legacy 单实现类实现本口=四域全承，调用面零改动。
 */
public interface RecipeAccess extends CraftAccess, SmeltAccess, BrewAccess, EnchAccess {
}
