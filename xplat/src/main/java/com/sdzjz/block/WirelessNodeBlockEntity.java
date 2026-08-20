package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

/** 无线节点方块实体：无数据、无 tick，仅作为信号波动画的渲染载体（BER 挂点）。 */
public class WirelessNodeBlockEntity extends BlockEntity {
    public WirelessNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_NODE_BE, pos, state);
    }
}
