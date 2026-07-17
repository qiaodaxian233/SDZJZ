package com.sdzjz.block;

import net.minecraft.block.Block;

/**
 * 卫星节点（后期）：接在核心相邻或其数据线网络上，使核心可跨维度、无限距离
 * 把产出送达/取料到任意已加载的数据面板（登记表全维度检索）。
 * 见 StructureCoreBlockEntity.hasSatelliteNode / findSatellitePanel。
 */
public class SatelliteNodeBlock extends Block {
    public SatelliteNodeBlock(Settings settings) {
        super(settings);
    }
}
