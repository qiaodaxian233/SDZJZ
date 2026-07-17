package com.sdzjz.block;

import net.minecraft.block.Block;

/**
 * 无线节点（中期 WiFi）：接在结构核心旁或其数据线网络上，
 * 使核心可无视连线、把产出无线送到范围内(config.wirelessRange, 同维度)最近的数据面板；
 * 消耗类机器亦可无线取料。见 StructureCoreBlockEntity.hasWirelessNode / nearestWirelessPanel。
 */
public class WirelessNodeBlock extends Block {
    public WirelessNodeBlock(Settings settings) {
        super(settings);
    }
}
