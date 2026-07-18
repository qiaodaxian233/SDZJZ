package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** 数据线方块实体：无数据、无 tick，仅作为能量脉冲动画的渲染载体（BER 挂点）。 */
public class DataCableBlockEntity extends BlockEntity {
    public DataCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_CABLE_BE, pos, state);
    }
}
