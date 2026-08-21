package com.sdzjz.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

/**
 * 无线节点（中期 WiFi）：接在结构核心旁或其数据线网络上，
 * 使核心可无视连线、把产出无线送到范围内(config.wirelessRange, 同维度)最近的存储；
 * 消耗类机器亦可无线取料。带渲染 BE 播放用户模型的信号波动画。
 */
public class WirelessNodeBlock extends Block implements EntityBlock {
    public WirelessNodeBlock(Properties settings) {
        super(settings);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessNodeBlockEntity(pos, state);
    }
}
