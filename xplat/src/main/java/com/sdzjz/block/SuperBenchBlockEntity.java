package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** 超大工作台方块实体（m249）：零数据，只为客户端动画渲染器提供挂点（网格仍随开关暂存在 Handler）。 */
public class SuperBenchBlockEntity extends BlockEntity {
    public SuperBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPER_BENCH_BE, pos, state);
    }
}
