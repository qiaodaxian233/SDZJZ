package com.sdzjz.retro;

import com.sdzjz.block.CableEnd;
import com.sdzjz.block.CableEndCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * m444：数据线方块 1.20.1 版——语义蓝本=xplat {@code DataCableBlock}（m67 三态连接：缆对缆=纯细管、
 * 对设备=带插头臂、无连接=不伸臂；路由 BFS 只认方块类型，视觉不影响逻辑）。
 *
 * <p>m502（真移植 B4b）：属性表/碰撞箱几何/三态判定核心迁 {@link CableEndCore} 两代共用
 * （本世代自己的 {@code CableEnd120} 枚举同刀删除，改用共用的 {@link CableEnd}——两者原本逐字同值，
 * 序列化名 none/cable/plug 与根仓 blockstate 多部件资产键同源，资产文件本身即同一份拷贝，
 * 分两份纯属仿写路线的历史遗留）。
 *
 * <p>1.20.1 签名差按 m440 清单口径行内指认：无 MapCodec（1.20.3 起才有）；updateShape/getShape/
 * getStateForPlacement 本版可见性为 public（1.20.5 起 Mojang 才收成 protected，蓝本的 protected
 * 在这版编不过）；交互走单一 use()（1.20.5 起才拆 useItemOn/useWithoutItem）。
 *
 * <p>本世代裁剪（P-C 到期补，非漏抄）：m233 按面断开（链接器随 P-C）——endFor 传给共用判定核心的
 * 断开钩子恒 false；refreshEnd 无消费者不带。抽取口无配置屏（m226 属 P-C），空手右键在服务端
 * 循环 关→送出→回收 三态（actionbar 反馈），让 m231 双向拍在本世代可用可验。
 */
public final class DataCableBlock120 extends Block implements EntityBlock {

    public DataCableBlock120(Properties settings) {
        super(settings);
        registerDefaultState(CableEndCore.allNone(getStateDefinition().any()));
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
            s = s.setValue(CableEndCore.END_PROPS.get(d), endFor(ctx.getLevel(), np, ctx.getLevel().getBlockState(np)));
        }
        return s;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // m469 撤回旧注（"本世代无掩码，双端重算同果"是把坏尺子）：endFor 的 FTA 分支自带
        // !isClientSide 闸，客户端本地重算对 FTA-only 邻居必得 NONE，而服务端是 PLUG——
        // 双端不同果，客户端会把服务端同步来的插头臂算没（鬼影反向版）。形状一律听服务端。
        if (world.isClientSide()) return state;
        return state.setValue(CableEndCore.END_PROPS.get(direction), endFor(world, neighborPos, neighborState));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CableEndCore.shapeFor(state);
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

    /** m469 旧档自愈：已放好的线在名单修好之前存的是 NONE，不碰邻居就一直不刷新——
     *  BE 首拍按当前邻居重算六面，变了才写（flags=3 同步客户端，蓝本 refreshEnd 同款口径）。
     *  邻块未加载直接返回 false 让调用方下拍再来（别把 getBlockState 变成强制加载票，m142 前车）。 */
    static boolean healEnds(Level world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof DataCableBlock120)) return true;
        BlockState s = state;
        for (Direction d : Direction.values()) {
            BlockPos np = pos.relative(d);
            if (!world.getChunkSource().hasChunk(np.getX() >> 4, np.getZ() >> 4)) return false; // 在树先例=StorageCore120.loadedCoreAt
            s = s.setValue(CableEndCore.END_PROPS.get(d), endFor(world, np, world.getBlockState(np)));
        }
        if (s != state) world.setBlock(pos, s, 3);
        return true;
    }

    /** 三态判定（蓝本 endFor 的本世代裁剪版）：数据线→缆管；**自家三块**（存储核心/结构核心/
     *  数据面板）·容器 BE·任意暴露 FTA 的存储→插头；其余→无。m502：核心算法迁
     *  {@link CableEndCore#classify}（两代共用，m233/m229 断开钩子本世代恒 false）；本方法只剩
     *  "把名单判成布尔"——**名单字面必须留在本方法体里**（第 16 闸抓这段文本防漏抄，m469 血案）。
     *  <p>m469：自家名单原只抄了 STORAGE_CORE 一条——结构核心与数据面板都不实现 Container，
     *  于是恒落到 NONE，线怼上去不伸插头（作者实机截图）。名单=蓝本六块与本世代已有三块的交集，
     *  **由 tools_retro_parity_check 对表闸看住，别再手抄**。 */
    private static CableEnd endFor(LevelAccessor world, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        boolean isCable = b instanceof DataCableBlock120;
        boolean isPlugBlock = b == RetroBlocks.STORAGE_CORE || b == RetroBlocks.STRUCTURE_CORE || b == RetroBlocks.DATA_PANEL;
        // m469：蓝本另三块（无线节点/卫星节点/交易所）本世代未建，到位随各自里程碑进名单
        return CableEndCore.classify(world, pos, isCable, false, false, isPlugBlock, false);
    }
}
