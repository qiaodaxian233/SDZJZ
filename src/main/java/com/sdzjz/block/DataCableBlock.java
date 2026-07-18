package com.sdzjz.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * 数据线：连接方块（轴向，像原木——放置时沿点击面方向，横竖任意走线）。
 * 模型为用户 Blockbench 细缆（竖向基准 0..16 无缝拼接，blockstate 旋转出 x/z 轴向）。
 * 路由逻辑只认方块类型不看朝向（见 StructureCoreBlockEntity 的 BFS）。
 */
public class DataCableBlock extends PillarBlock {
    private static final VoxelShape SHAPE_Y = Block.createCuboidShape(5, 0, 5, 11, 16, 11);
    private static final VoxelShape SHAPE_Z = Block.createCuboidShape(5, 5, 0, 11, 11, 16);
    private static final VoxelShape SHAPE_X = Block.createCuboidShape(0, 5, 5, 16, 11, 11);

    public DataCableBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
    }
}
