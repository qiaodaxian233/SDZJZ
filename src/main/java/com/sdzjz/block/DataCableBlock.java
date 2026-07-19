package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据线（m67 重做）：三态连接。
 * 缆对缆 = 纯细管（直线摆放视觉连续，无接头盒）；对设备 = 带连接器插头的臂；无连接 = 不伸臂。
 * 中心件与细管同粗，直通时看不出断点。路由 BFS 只认方块类型，视觉不影响逻辑。
 */
public class DataCableBlock extends Block implements BlockEntityProvider {

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
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState s = getDefaultState();
        for (Direction d : Direction.values()) {
            BlockPos np = ctx.getBlockPos().offset(d);
            s = s.with(END_PROPS.get(d), endFor(ctx.getWorld(), np, ctx.getWorld().getBlockState(np)));
        }
        return s;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return state.with(END_PROPS.get(direction), endFor(world, neighborPos, neighborState));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.computeIfAbsent(state, st -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if (st.get(END_PROPS.get(d)) != CableEnd.NONE) shape = VoxelShapes.union(shape, ARMS.get(d));
            }
            return shape;
        });
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DataCableBlockEntity(pos, state);
    }

    /** 三态判定：数据线→缆管；网络方块/容器→插头；其余→无。 */
    private static CableEnd endFor(WorldAccess world, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (b instanceof DataCableBlock) return CableEnd.CABLE;
        if (b == ModBlocks.STRUCTURE_CORE || b == ModBlocks.STORAGE_CORE || b == ModBlocks.DATA_PANEL
                || b == ModBlocks.WIRELESS_NODE || b == ModBlocks.SATELLITE_NODE || b == ModBlocks.TRADE_CENTER) return CableEnd.PLUG;
        if (world.getBlockEntity(pos) instanceof Inventory) return CableEnd.PLUG;
        return CableEnd.NONE;
    }
}
