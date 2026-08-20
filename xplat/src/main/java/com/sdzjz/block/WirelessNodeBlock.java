package com.sdzjz.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 无线节点（中期 WiFi）：接在结构核心旁或其数据线网络上，
 * 使核心可无视连线、把产出无线送到范围内(config.wirelessRange, 同维度)最近的存储；
 * 消耗类机器亦可无线取料。带渲染 BE 播放用户模型的信号波动画。
 */
public class WirelessNodeBlock extends Block implements BlockEntityProvider {
    public WirelessNodeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessNodeBlockEntity(pos, state);
    }
}
