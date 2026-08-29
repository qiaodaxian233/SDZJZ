package com.sdzjz.machine;

import net.minecraft.world.item.ItemStack;

/**
 * m480（真移植 D 阶段先行）：存储账本**行为契约探针**——把两代存储核心已有的十三个同名账本方法
 * 声明成一个接口，好让 {@link StorageDomainAssertions} 那套断言两代各喂自己的实现跑同一份。
 *
 * <p><b>为什么是探针不是生产契约</b>：生产侧的窄契约是 {@link StorageAccess}（四口，m464 就位，
 * 生产 tick 用）；本口是**为跨代行为不变量准备的宽面**，把精确账本、类型额度、经验池也纳进来。
 * 两者刻意分开——生产代码不该因为要测试而被迫吃一张大接口（m368「不许再往聚合口塞新方法」同律）。
 *
 * <p><b>零方法体改动</b>：两代 BE（StorageCoreBlockEntity / StorageCore120）本来就有这十三个
 * 同名同签名的 public 方法，各加一行 {@code implements} 即可——m180 家法的极致形态，
 * 连方法都不用搬，只是把「它们本来就长一样」这件事写进类型系统让编译器盯着。
 *
 * <p><b>它同时是 B 阶段的安全网</b>：账本下沉（StorageLedger）之后，实现从 BE 自己变成转发给
 * 共用账本类，而这套断言必须继续全绿——**契约先立、手术后做**，行为不变量就有了机器保证。
 */
public interface StorageLedgerProbe {

    // ===== 普通账本 =====
    void deposit(ItemStack stack);
    int withdraw(String id, int amount);
    long count(String id);
    java.util.Map<String, Long> storeView();

    // ===== 精确账本（带附加数据的物品，m130 谱系）=====
    void depositExact(ItemStack stack);
    int withdrawExact(ItemStack template, int amount);
    java.util.List<ItemStack> exactTemplates();
    long exactCount(int i);

    // ===== 类型额度（m130 精确条目同占额度 / m293 安全硬顶）=====
    int usedTypes();
    int maxTypes();

    // ===== 经验池 =====
    long xpBank();
    void xpAdd(long points);
    long xpTake(long max);
}
