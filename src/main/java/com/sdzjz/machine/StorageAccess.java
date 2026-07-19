package com.sdzjz.machine;

import net.minecraft.item.ItemStack;

/**
 * 存储访问抽象：机器↔存储 定向连线的目标契约。
 * 实现者：StorageCoreBlockEntity（单库）、DataPanelBlockEntity（聚合它网络里全部存储核心）。
 * m78 起画布连线允许连到数据面板——"最后一格连到面板"语义 = 存进/取自该面板聚合的整个存储网络。
 */
public interface StorageAccess {
    /** 入库：收下会把栈清空；收不下（类型满等）原样留着，调用方自行兜底。 */
    void deposit(ItemStack stack);

    /** 取料：返回实际取到的数量。 */
    int withdraw(String id, int amount);

    /** 库存量。 */
    long count(String id);
}
