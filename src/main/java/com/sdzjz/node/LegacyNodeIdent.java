package com.sdzjz.node;

import com.sdzjz.item.MachineItem;
import com.sdzjz.machine.MachineDef;
import com.sdzjz.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * m472（绞杀者第五刀）：NodeTags 身份口的 1.21 世代实现——六族判定与 defOf 的**原表达式原句照搬**
 * （m180 家法：方法体一字不改才敢说回归风险按构造为零；m468 风险②"新增走法+旧走法原位保留同值"
 * 的落点就是本类）。版本差（ModItems 注册表/MachineItem 物品族）自此钉死在本文件与 RetroNodeIdent
 * 两处，NodeTags 整文件可挂 1.20.1。同值判官见 SdzjzGameTests.nodetags_ident_matches_item_identity。
 *
 * <p><b>m470 家法</b>：本类六个常量与 RetroNodeIdent 的六个 Machines 常量是两代同源手抄名单——
 * 对表闸 tools_retro_parity_check（"NodeTags 身份口六族"条目，豁免关）看着，漏抄即红。
 */
public final class LegacyNodeIdent implements NodeTags.Ident {

    @Override public boolean isFilter(ItemStack s) { return s.is(ModItems.FILTER_NODE); }

    @Override public boolean isTrash(ItemStack s) { return s.is(ModItems.TRASH_NODE); }

    @Override public boolean isExtractor(ItemStack s) { return s.is(ModItems.EXTRACTOR_NODE); }

    @Override public boolean isSensor(ItemStack s) { return s.is(ModItems.SENSOR_NODE); }

    @Override public boolean isSwitch(ItemStack s) { return s.is(ModItems.SWITCH_NODE); }

    @Override public boolean isDistributor(ItemStack s) { return s.is(ModItems.DISTRIBUTOR_NODE); }

    @Override public MachineDef defOf(ItemStack s) {
        return s.getItem() instanceof MachineItem mi ? mi.def() : null; // 原 machineFilterable 首行同式
    }
}
