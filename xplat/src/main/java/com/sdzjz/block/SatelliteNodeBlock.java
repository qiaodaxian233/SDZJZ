package com.sdzjz.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;

/**
 * 卫星节点（后期）：接在核心相邻或其数据线网络上，使核心可跨维度、无限距离
 * 把产出送达/取料到任意已加载的数据面板（登记表全维度检索）。
 * 见 StructureCoreBlockEntity.hasSatelliteNode / findSatellitePanel。
 * m156：挂空壳 BE 供扫描动画渲染器；outline 贴合底座+塔体+桅杆（模型比一格大且几何
 * 探出方块框，默认整格 outline 会让"框不套模型"——观感即用户 m152 反馈的"位置不对"）。
 */
public class SatelliteNodeBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(-1.5, 0, 1, 17.5, 10, 15),   // 阶梯底座
            Block.box(4, 10, 5, 12, 16, 11));      // 塔体+桅杆段
    public SatelliteNodeBlock(Properties settings) {
        super(settings);
    }
    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SatelliteNodeBlock::new);
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SatelliteNodeBlockEntity(pos, state);
    }
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // BER 之外仍走模型管线（模型已被插件替换为空壳，只留粒子）
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
}
