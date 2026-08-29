package com.sdzjz.retro;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.machine.Machines;
import com.sdzjz.node.NodeTags;
import net.minecraft.world.item.ItemStack;

/**
 * m472：NodeTags 身份口的 1.20.1 世代实现——本世代全部机器/节点物品都是 RetroMachineItem（m453
 * 反射 Machines 唯一数据源批量注册，六个逻辑节点 def 也在其中），身份判定=def **引用同一性**对比
 * （与 Machines 静态常量是同一个对象，零字符串手抄，m470 漏点按构造消除一半；另一半——六族名单
 * 与 Legacy 侧逐条对齐——由对表闸"NodeTags 身份口六族"条目看着）。defOf 对位 Legacy 的
 * {@code instanceof MachineItem}。
 */
final class RetroNodeIdent implements NodeTags.Ident {

    private static MachineDef d(ItemStack s) {
        return s.getItem() instanceof RetroMachineItems.RetroMachineItem r ? r.def : null;
    }

    @Override public boolean isFilter(ItemStack s) { return d(s) == Machines.FILTER_NODE; }

    @Override public boolean isTrash(ItemStack s) { return d(s) == Machines.TRASH_NODE; }

    @Override public boolean isExtractor(ItemStack s) { return d(s) == Machines.EXTRACTOR_NODE; }

    @Override public boolean isSensor(ItemStack s) { return d(s) == Machines.SENSOR_NODE; }

    @Override public boolean isSwitch(ItemStack s) { return d(s) == Machines.SWITCH_NODE; }

    @Override public boolean isDistributor(ItemStack s) { return d(s) == Machines.DISTRIBUTOR_NODE; }

    @Override public MachineDef defOf(ItemStack s) { return d(s); }
}
