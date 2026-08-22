package com.sdzjz.retro;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * m444：数据线方块 1.20.1 版——语义蓝本=xplat {@code DataCableBlock}（m67 三态连接：缆对缆=纯细管、
 * 对设备=带插头臂、无连接=不伸臂；路由 BFS 只认方块类型，视觉不影响逻辑）。
 *
 * <p>1.20.1 签名差按 m440 清单口径行内指认：无 MapCodec（1.20.3 起才有）；updateShape/getShape/
 * getStateForPlacement 本版可见性为 public（1.20.5 起 Mojang 才收成 protected，蓝本的 protected
 * 在这版编不过）；交互走单一 use()（1.20.5 起才拆 useItemOn/useWithoutItem）。
 *
 * <p>本世代裁剪（P-C 到期补，非漏抄）：m233 按面断开（链接器随 P-C）——endFor 无掩码分支，
 * updateShape 无需服务端限定（掩码不存在，双端重算结果一致）；refreshEnd 无消费者不带。
 * 抽取口无配置屏（m226 属 P-C），空手右键在服务端循环 关→送出→回收 三态（actionbar 反馈），
 * 让 m231 双向拍在本世代可用可验。
 */
public final class DataCableBlock120 extends Block implements EntityBlock {

    static final EnumProperty<CableEnd120> NORTH = EnumProperty.create("north", CableEnd120.class);
    static final EnumProperty<CableEnd120> SOUTH = EnumProperty.create("south", CableEnd120.class);
    static final EnumProperty<CableEnd120> EAST  = EnumProperty.create("east", CableEnd120.class);
    static final EnumProperty<CableEnd120> WEST  = EnumProperty.create("west", CableEnd120.class);
    static final EnumProperty<CableEnd120> UP    = EnumProperty.create("up", CableEnd120.class);
    static final EnumProperty<CableEnd120> DOWN  = EnumProperty.create("down", CableEnd120.class);

    static final Map<Direction, EnumProperty<CableEnd120>> END_PROPS = new EnumMap<>(Direction.class);
    static {
        END_PROPS.put(Direction.NORTH, NORTH);
        END_PROPS.put(Direction.SOUTH, SOUTH);
        END_PROPS.put(Direction.EAST, EAST);
        END_PROPS.put(Direction.WEST, WEST);
        END_PROPS.put(Direction.UP, UP);
        END_PROPS.put(Direction.DOWN, DOWN);
    }

    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    static {
        ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
        ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
        ARMS.put(Direction.EAST,  Block.box(10, 6, 6, 16, 10, 10));
        ARMS.put(Direction.WEST,  Block.box(0, 6, 6, 6, 10, 10));
        ARMS.put(Direction.UP,    Block.box(6, 10, 6, 10, 16, 10));
        ARMS.put(Direction.DOWN,  Block.box(6, 0, 6, 10, 6, 10));
    }

    public DataCableBlock120(Properties settings) {
        super(settings);
        BlockState s = getStateDefinition().any();
        for (EnumProperty<CableEnd120> p : END_PROPS.values()) s = s.setValue(p, CableEnd120.NONE);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.values()) {
            BlockPos np = ctx.getClickedPos().relative(d);
            s = s.setValue(END_PROPS.get(d), endFor(ctx.getLevel(), np, ctx.getLevel().getBlockState(np)));
        }
        return s;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // 蓝本此处限服务端重算是为 m233 掩码（BE 数据不向客户端同步）；本世代无掩码，双端重算同果。
        return state.setValue(END_PROPS.get(direction), endFor(world, neighborPos, neighborState));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, st -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if (st.getValue(END_PROPS.get(d)) != CableEnd120.NONE) shape = Shapes.or(shape, ARMS.get(d));
            }
            return shape;
        });
    }

    /** m444/m449 本世代交互（服务端权威改状态，actionbar 反馈；m226 配置屏随 P-C 到位后保留为
     *  快捷开关不冲突）：空手右键循环 关→送出→回收；潜行空手=清空过滤器；**持物非潜行右键=
     *  把手中物加入/移出过滤白名单**（原版口径：潜行+持物根本到不了 use——isSecondaryUseActive
     *  且手非空时方块交互被跳过直接走物品使用，所以贴线放方块=潜行放，行内记死防回头误改）。 */
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof DataCable120 cable)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) { // m449 持物（必非潜行，见方法注）：白名单开关
            int result = cable.filterToggle(held);
            String key = result == DataCable120.FILTER_ADDED ? "sdzjz.cable.filter.added"
                    : result == DataCable120.FILTER_REMOVED ? "sdzjz.cable.filter.removed"
                    : "sdzjz.cable.filter.full";
            player.displayClientMessage(Component.translatable(key, held.getHoverName(),
                    cable.filterView().size()), true);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) { // m449 潜行空手：清空过滤器（回"全抽/全收"态）
            cable.filterClear();
            player.displayClientMessage(Component.translatable("sdzjz.cable.filter.cleared"), true);
            return InteractionResult.CONSUME;
        }
        String modeKey;
        if (!cable.extractOn()) { // 关 → 送出
            cable.setExtractOn(true); cable.setPullMode(false); modeKey = "sdzjz.cable.mode.push";
        } else if (!cable.pullMode()) { // 送出 → 回收
            cable.setPullMode(true); modeKey = "sdzjz.cable.mode.pull";
        } else { // 回收 → 关
            cable.setExtractOn(false); cable.setPullMode(false); modeKey = "sdzjz.cable.mode.off";
        }
        player.displayClientMessage(Component.translatable(modeKey), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DataCable120(pos, state);
    }

    /** m225 服务端 ticker（蓝本同注：未启用的线首判即返，成本≈空转；照 validateTicker 内部语义
     *  手写 type 校验+未检查转换）。 */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide || type != RetroBlocks.DATA_CABLE_BE) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<DataCable120>) DataCable120::tick;
    }

    /** 三态判定（蓝本 endFor 的本世代裁剪版）：数据线→缆管；存储核心/容器 BE/任意暴露 FTA 的
     *  存储→插头；其余→无。FTA 探测只在服务端权威世界查（蓝本同注：客户端注册表可能缺第三方
     *  登记，方块状态由服务端同步；世界生成期 ChunkRegion 不是 Level 直接跳过）。 */
    private static CableEnd120 endFor(LevelAccessor world, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (b instanceof DataCableBlock120) return CableEnd120.CABLE;
        if (b == RetroBlocks.STORAGE_CORE) return CableEnd120.PLUG;
        if (world.getBlockEntity(pos) instanceof Container) return CableEnd120.PLUG;
        if (world instanceof Level w && !w.isClientSide
                && ItemStorage.SIDED.find(w, pos, null) != null) return CableEnd120.PLUG;
        return CableEnd120.NONE;
    }
}
