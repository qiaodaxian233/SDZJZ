package com.sdzjz.machine;

import java.util.List;

/**
 * 机器定义：一台机器产什么、是否消耗输入。
 *
 * 核心原则（作者定）：**消耗与否对齐原版对应的生电机器**——
 *  · 靠自然刷出/生长收获的（刷怪掉落 / 刷石 / 作物 / 刷铁刷金…）→ consumesInputs=false，不喂也出货；
 *  · 有明确配方输入的加工/合成（熔炼要矿+燃料 / 切石 / 合成）→ consumesInputs=true，照原版消耗输入。
 * 玩家省掉的是「搭不对」的工程难度，不是原版的资源经济。
 */
public record MachineDef(
        String id,                 // 机器 id，如 "wire_brusher"
        String product,            // 产物物品 id，如 "minecraft:string"
        int baseOutputPerCycle,    // 每周期产出个数（基础，未算数量升级）
        int baseIntervalTicks,     // 每周期 tick（基础，未算速度升级）
        boolean consumesInputs,    // 是否消耗输入（= 原版对应机器是否需要）
        List<Input> inputs         // 消耗清单（免费农场类为空）
) {
    /** 单项消耗：物品 id + 数量。 */
    public record Input(String item, int count) {}
}
