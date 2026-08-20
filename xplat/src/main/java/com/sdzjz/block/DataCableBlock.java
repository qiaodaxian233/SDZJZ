package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据线（m67 重做）：三态连接。
 * 缆对缆 = 纯细管（直线摆放视觉连续，无接头盒）；对设备 = 带连接器插头的臂；无连接 = 不伸臂。
 * 中心件与细管同粗，直通时看不出断点。路由 BFS 只认方块类型，视觉不影响逻辑。
 */
public class DataCableBlock extends Block implements EntityBlock {

    public static final MapCodec<DataCableBlock> CODEC = createCodec(DataCableBlock::new);

    public static final EnumProperty<CableEnd> NORTH = EnumProperty.of("north", CableEnd.class);
    public static final EnumProperty<CableEnd> SOUTH = EnumProperty.of("south", CableEnd.class);
    public static final EnumProperty<CableEnd> EAST  = EnumProperty.of("east", CableEnd.class);
    public static final EnumProperty<CableEnd> WEST  = EnumProperty.of("west", CableEnd.class);
    public static final EnumProperty<CableEnd> UP    = EnumProperty.of("up", CableEnd.class);
    public static final EnumProperty<CableEnd> DOWN  = EnumProperty.of("down", CableEnd.class);

    public static final Map<Direction, EnumProperty<CableEnd>> END_PROPS = new EnumMap<>(Direction.class);
    static {
        END_PROPS.put(Direction.NORTH, NORTH);
        END_PROPS.put(Direction.SOUTH, SOUTH);
        END_PROPS.put(Direction.EAST, EAST);
        END_PROPS.put(Direction.WEST, WEST);
        END_PROPS.put(Direction.UP, UP);
        END_PROPS.put(Direction.DOWN, DOWN);
    }

    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final VoxelShape CORE = Block.createCuboidShape(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    static {
        ARMS.put(Direction.NORTH, Block.createCuboidShape(6, 6, 0, 10, 10, 6));
        ARMS.put(Direction.SOUTH, Block.createCuboidShape(6, 6, 10, 10, 10, 16));
        ARMS.put(Direction.EAST,  Block.createCuboidShape(10, 6, 6, 16, 10, 10));
        ARMS.put(Direction.WEST,  Block.createCuboidShape(0, 6, 6, 6, 10, 10));
        ARMS.put(Direction.UP,    Block.createCuboidShape(6, 10, 6, 10, 16, 10));
        ARMS.put(Direction.DOWN,  Block.createCuboidShape(6, 0, 6, 10, 6, 10));
    }

    public DataCableBlock(Settings settings) {
        super(settings);
        BlockState s = getStateManager().getDefaultState();
        for (EnumProperty<CableEnd> p : END_PROPS.values()) s = s.with(p, CableEnd.NONE);
        setDefaultState(s);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getPlacementState(BlockPlaceContext ctx) {
        BlockState s = getDefaultState();
        for (Direction d : Direction.values()) {
            BlockPos np = ctx.getBlockPos().offset(d);
            s = s.with(END_PROPS.get(d), endFor(ctx.getWorld(), ctx.getBlockPos(), d, np, ctx.getWorld().getBlockState(np)));
        }
        return s;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // m233 端点判定含 BE 断开掩码，而掩码只在服务端（BE 数据不向客户端同步）——客户端本地重算会
        // 拿 0 掩码把断开面的手臂算回来造成鬼影，形状一律听服务端方块状态同步
        if (world.isClient()) return state;
        return state.with(END_PROPS.get(direction), endFor(world, pos, direction, neighborPos, neighborState));
    }

    /** m233 单面端点重算（链接器切断开掩码后调用；flags=3 触发邻居形状更新，对面缆管同步收放）。 */
    public static void refreshEnd(net.minecraft.world.level.Level world, BlockPos pos, Direction d) {
        BlockState st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof DataCableBlock)) return;
        BlockPos np = pos.offset(d);
        world.setBlockState(pos, st.with(END_PROPS.get(d), endFor(world, pos, d, np, world.getBlockState(np))), 3);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, st -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if (st.get(END_PROPS.get(d)) != CableEnd.NONE) shape = Shapes.union(shape, ARMS.get(d));
            }
            return shape;
        });
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DataCableBlockEntity(pos, state);
    }

    /** m225 服务端 ticker：抽取口主拍（未启用的线首判即返，成本≈空转；BlockWithEntity.validateTicker
     *  是 protected 进不来，照其内部语义手写 type 校验+未检查转换）。 */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level world, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (world.isClient || type != com.sdzjz.registry.ModBlockEntities.DATA_CABLE_BE) return null;
        return (net.minecraft.world.level.block.entity.BlockEntityTicker<T>)
                (net.minecraft.world.level.block.entity.BlockEntityTicker<DataCableBlockEntity>) DataCableBlockEntity::tick;
    }

    /** 三态判定：数据线→缆管；网络方块/容器→插头；其余→无。m233：任一端按面断开→无。 */
    private static CableEnd endFor(LevelAccessor world, BlockPos cablePos, Direction d, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(cablePos) instanceof DataCableBlockEntity self && self.faceDisabled(d))
            return CableEnd.NONE; // m233 本端断开（放置期 BE 未建=掩码 0，天然直通）
        Block b = state.getBlock();
        if (b instanceof DataCableBlock)
            return (world.getBlockEntity(pos) instanceof DataCableBlockEntity oc && oc.faceDisabled(d.getOpposite()))
                    ? CableEnd.NONE : CableEnd.CABLE; // m233 对端断开=这边也不伸（视觉与 BFS 口径一致）
        if (b == ModBlocks.STRUCTURE_CORE || b == ModBlocks.STORAGE_CORE || b == ModBlocks.DATA_PANEL
                || b == ModBlocks.WIRELESS_NODE || b == ModBlocks.SATELLITE_NODE || b == ModBlocks.TRADE_CENTER) return CableEnd.PLUG;
        if (world.getBlockEntity(pos) instanceof Container) return CableEnd.PLUG;
        if (com.sdzjz.compat.ProjectEFCompat.isTransmutationTable(state)) return CableEnd.PLUG; // m229 转化桌（纯 id 判无反射，双端安全）
        // m224 任意暴露 Fabric Transfer API 的存储也伸插头（Create 置物台/AE2 接口这类不实现 Container 的
        // 全吃）；只在服务端权威世界查（客户端注册表可能缺第三方登记，方块状态由服务端同步），
        // 世界生成期的 ChunkRegion 不是 Level 直接跳过。
        if (world instanceof net.minecraft.world.level.Level w && !w.isClient
                && com.sdzjz.storage.Xfer.find(w, pos, null) != null) return CableEnd.PLUG; // m404 平台口
        return CableEnd.NONE;
    }
}
