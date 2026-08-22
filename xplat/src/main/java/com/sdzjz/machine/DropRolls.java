package com.sdzjz.machine;

import net.minecraft.util.RandomSource;

/**
 * m428 绞杀者拆分第四刀：掉落结算（自 StructureCoreBlockEntity 原样迁入，方法体一字未改）。
 * m180 路线图第二位候选：instance 形态但零 BE 状态触点——入参齐全（随机源/掉落表项/周期数/
 * 数量升级等级），纯函数改 public static。因入参含 MC 的 RandomSource，家安 xplat
 * （见 MC 不见加载器）而非 common 的 MachineDef/MobDrops 旁（Common 硬闸①零 MC 字面）。
 * 五条生产分支（农场/抓物笼/万能熔炉/通用机/自动合成中走掉落表者）共用此一处结算，新代码直用本类。
 */
public final class DropRolls {
    private DropRolls() {}

    /** m99 随机掉落表按周期数结算：每周期独立掷概率/数量，命中加数量升级奖励(+8/级)。 */
    public static long rollDrops(RandomSource rand, MachineDef.Drop d, int cycles, int countLv) {
        long sum = 0;
        for (int c = 0; c < cycles; c++) {
            if (d.chance() < 1f && rand.nextFloat() >= d.chance()) continue;
            int amt = d.min() + (d.max() > d.min() ? rand.nextInt(d.max() - d.min() + 1) : 0);
            if (amt <= 0) continue;
            sum += amt + (long) countLv * 8;
        }
        return sum;
    }
}
