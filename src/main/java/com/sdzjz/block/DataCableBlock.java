package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

/**
 * 数据线：连接式方块（AE 风格自动连接）。放下/邻居变化时自动检测六面：
 * 相邻是 数据线 / 网络方块（结构核心/存储核心/数据面板/无线/卫星/交易所）/ 任意容器 → 伸臂连接。
 * 模型 = 中心接头 + 每个连接方向一条臂（臂由用户线缆模型对称切半生成）。
 * 路由 BFS 只认方块类型，视觉连接不影响逻辑。
 */
public class DataCableBlock extends ConnectingBlock {

    public static final MapCodec<DataCableBlock> CODEC = createCodec(DataCableBlock::new);

    public DataCableBlock(Settings settings) {
        super(0.1875F, settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, false).with(EAST, false).with(SOUTH, false)
                .with(WEST, false).with(UP, false).with(DOWN, false));
    }

    @Override
    protected MapCodec<? extends ConnectingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState s = getDefaultState();
        for (Direction d : Direction.values()) {
            BlockPos np = ctx.getBlockPos().offset(d);
            s = s.with(FACING_PROPERTIES.get(d), connectsTo(ctx.getWorld(), np, ctx.getWorld().getBlockState(np)));
        }
        return s;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return state.with(FACING_PROPERTIES.get(direction), connectsTo(world, neighborPos, neighborState));
    }

    /** 连接判定：数据线、网络方块、任意容器（箱子/漏斗/熔炉…）。 */
    private static boolean connectsTo(WorldAccess world, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (b instanceof DataCableBlock) return true;
        if (b == ModBlocks.STRUCTURE_CORE || b == ModBlocks.STORAGE_CORE || b == ModBlocks.DATA_PANEL
                || b == ModBlocks.WIRELESS_NODE || b == ModBlocks.SATELLITE_NODE || b == ModBlocks.TRADE_CENTER) return true;
        return world.getBlockEntity(pos) instanceof Inventory;
    }
}
