package com.sdzjz.machine;

import java.util.List;

/**
 * 机器定义：产什么（可多种，带数量区间与概率）、周期、是否消耗输入。
 * 消耗原则：农场类 consumesInputs=false 免费出；加工/合成类 =true 照原版消耗。
 */
public record MachineDef(
        String id,
        List<Drop> outputs,        // 每周期尝试产出的掉落表
        int baseIntervalTicks,
        boolean consumesInputs,
        List<Input> inputs
) {
    /** 一条掉落：物品 id，单次数量区间 [min,max]，触发概率 chance(0..1)。 */
    public record Drop(String item, int min, int max, float chance) {}

    /** 一项消耗：物品 id + 数量。 */
    public record Input(String item, int count) {}
}
