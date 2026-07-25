package com.sdzjz.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * 卫星节点（后期）：接在核心相邻或其数据线网络上，使核心可跨维度、无限距离
 * 把产出送达/取料到任意已加载的数据面板（登记表全维度检索）。
 * 见 StructureCoreBlockEntity.hasSatelliteNode / findSatellitePanel。
 * m156：挂空壳 BE 供扫描动画渲染器；outline 贴合底座+塔体+桅杆（模型比一格大且几何
 * 探出方块框，默认整格 outline 会让"框不套模型"——观感即用户 m152 反馈的"位置不对"）。
 */
public class SatelliteNodeBlock extends BlockWithEntity {
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(-1.5, 0, 1, 17.5, 10, 15),   // 阶梯底座
            Block.createCuboidShape(4, 10, 5, 12, 16, 11));      // 塔体+桅杆段
    public SatelliteNodeBlock(Settings settings) {
        super(settings);
    }
    @Override
    protected com.mojang.serialization.MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(SatelliteNodeBlock::new);
    }
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SatelliteNodeBlockEntity(pos, state);
    }
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL; // BER 之外仍走模型管线（模型已被插件替换为空壳，只留粒子）
    }
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return SHAPE;
    }
}
