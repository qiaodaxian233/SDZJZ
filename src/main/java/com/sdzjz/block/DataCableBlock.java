package com.sdzjz.block;

import net.minecraft.block.Block;

/**
 * 数据线：连接方块。结构核心推送产出时会顺着相连的数据线做 BFS，
 * 把产物送到线路末端连接的数据面板/箱子（见 StructureCoreBlockEntity.findTarget）。
 * 第一版是完整方块，之后可改成细导线+连接模型。
 */
public class DataCableBlock extends Block {
    public DataCableBlock(Settings settings) {
        super(settings);
    }
}
