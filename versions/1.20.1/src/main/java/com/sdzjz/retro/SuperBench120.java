package com.sdzjz.retro;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** m524（SB3）超大工作台方块实体 **1.20.1 世代壳**——主线 {@code block/SuperBenchBlockEntity}（m249）原文：
 *  零数据，只为客户端动画渲染器提供挂点（网格仍随开关暂存在 Handler）。
 *  唯一世代差=BE 类型来源：主线引 {@code registry.ModBlockEntities.SUPER_BENCH_BE}（在 src，1.20.1 白名单子集不可见，
 *  m522b 血案同族）→ 本世代 {@link RetroBlocks#SUPER_BENCH_BE}；BE id 与主线同名同源 {@code sdzjz:super_bench}。 */
public final class SuperBench120 extends BlockEntity {
    public SuperBench120(BlockPos pos, BlockState state) {
        super(RetroBlocks.SUPER_BENCH_BE, pos, state);
    }
}
