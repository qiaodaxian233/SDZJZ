package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;

/**
 * 数据线（m67 重做）：三态连接。
 * 缆对缆 = 纯细管（直线摆放视觉连续，无接头盒）；对设备 = 带连接器插头的臂；无连接 = 不伸臂。
 * 中心件与细管同粗，直通时看不出断点。路由 BFS 只认方块类型，视觉不影响逻辑。
 *
 * <p>m502（真移植 B4b）：属性表/碰撞箱几何/三态判定核心迁 {@link CableEndCore} 两代共用
 * （本类保留 NORTH/SOUTH/... 等作为**只读转发**，外部引用点零改动）；本类只剩 CODEC/方法可见性
 * 这类版本 API 差、与 m233 按面断开/m229 转化桌这类主线专属功能（{@code endFor} 里判成布尔传共用件）。
 */
public class DataCableBlock extends Block implements EntityBlock {

    public static final MapCodec<DataCableBlock> CODEC = simpleCodec(DataCableBlock::new);

    // ===== m502：转发 CableEndCore（外部引用点如 DataCableRenderer 零改动）=====
    public static final EnumProperty<CableEnd> NORTH = CableEndCore.NORTH;
    public static final EnumProperty<CableEnd> SOUTH = CableEndCore.SOUTH;
    public static final EnumProperty<CableEnd> EAST  = CableEndCore.EAST;
    public static final EnumProperty<CableEnd> WEST  = CableEndCore.WEST;
    public static final EnumProperty<CableEnd> UP    = CableEndCore.UP;
    public static final EnumProperty<CableEnd> DOWN  = CableEndCore.DOWN;
    public static final java.util.Map<Direction, EnumProperty<CableEnd>> END_PROPS = CableEndCore.END_PROPS;

    public DataCableBlock(Properties settings) {
        super(settings);
        registerDefaultState(CableEndCore.allNone(getStateDefinition().any()));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CableEndCore.addStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.values()) {
            BlockPos np = ctx.getClickedPos().relative(d);
            s = s.setValue(END_PROPS.get(d), endFor(ctx.getLevel(), ctx.getClickedPos(), d, np, ctx.getLevel().getBlockState(np)));
        }
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                                   LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // m233 端点判定含 BE 断开掩码，而掩码只在服务端（BE 数据不向客户端同步）——客户端本地重算会
        // 拿 0 掩码把断开面的手臂算回来造成鬼影，形状一律听服务端方块状态同步
        if (world.isClientSide()) return state;
        return state.setValue(END_PROPS.get(direction), endFor(world, pos, direction, neighborPos, neighborState));
    }

    /** m233 单面端点重算（链接器切断开掩码后调用；flags=3 触发邻居形状更新，对面缆管同步收放）。 */
    public static void refreshEnd(net.minecraft.world.level.Level world, BlockPos pos, Direction d) {
        BlockState st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof DataCableBlock)) return;
        BlockPos np = pos.relative(d);
        world.setBlock(pos, st.setValue(END_PROPS.get(d), endFor(world, pos, d, np, world.getBlockState(np))), 3);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CableEndCore.shapeFor(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DataCableBlockEntity(pos, state);
    }

    /** m225 服务端 ticker：抽取口主拍（未启用的线首判即返，成本≈空转；BlockWithEntity.validateTicker
     *  是 protected 进不来，照其内部语义手写 type 校验+未检查转换）。 */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level world, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (world.isClientSide || type != com.sdzjz.registry.ModBlockEntities.DATA_CABLE_BE) return null;
        return (net.minecraft.world.level.block.entity.BlockEntityTicker<T>)
                (net.minecraft.world.level.block.entity.BlockEntityTicker<DataCableBlockEntity>) DataCableBlockEntity::tick;
    }

    /** 三态判定：数据线→缆管；网络方块/容器→插头；其余→无。m233：任一端按面断开→无。
     *  m502：核心算法迁 {@link CableEndCore#classify}（两代共用）；本方法只剩"把名单/断开状态
     *  判成布尔"这道手续——**名单字面必须留在本方法体里**（第 16 闸靠抓这段文本防漏抄，m469 血案）。 */
    private static CableEnd endFor(LevelAccessor world, BlockPos cablePos, Direction d, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        boolean selfDisabled = world.getBlockEntity(cablePos) instanceof DataCableBlockEntity self && self.faceDisabled(d);
        boolean isCable = b instanceof DataCableBlock;
        boolean neighborDisabled = isCable
                && world.getBlockEntity(pos) instanceof DataCableBlockEntity oc && oc.faceDisabled(d.getOpposite());
        boolean isPlugBlock = b == ModBlocks.STRUCTURE_CORE || b == ModBlocks.STORAGE_CORE || b == ModBlocks.DATA_PANEL
                || b == ModBlocks.WIRELESS_NODE || b == ModBlocks.SATELLITE_NODE || b == ModBlocks.TRADE_CENTER;
        boolean isSellNeighbor = com.sdzjz.compat.ProjectEFCompat.isTransmutationTable(state); // m229 转化桌
        return CableEndCore.classify(world, pos, isCable, selfDisabled, neighborDisabled, isPlugBlock, isSellNeighbor);
    }
}
