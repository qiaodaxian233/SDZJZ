package com.sdzjz.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * m502（真移植 B4b）：数据线**方块层**两代共用一份——m67 三态连接的属性表/碰撞箱几何/形状缓存
 * 与三态判定核心算法，整段取自主线 {@code DataCableBlock}（注释刀号原样带）。1.20.1 的
 * {@code CableEnd120} 同刀删除，改用本类的 {@link CableEnd}（纯 {@code StringRepresentable}
 * 枚举，零版本相关 API，两代逐字同值——序列化名 none/cable/plug 与根仓 blockstate 多部件资产键
 * 同源，资产文件本身即同一份拷贝，枚举没有理由分两份）。
 *
 * <p><b>真差异只有两类，都不在这份共用件里</b>：①{@code CODEC}/方法可见性——1.20.3 才有
 * {@code MapCodec}，1.20.5 起 Mojang 才把 getShape/updateShape/getStateForPlacement 的可见性
 * 收紧成 protected——纯版本 API 差，留各世代 Block 子类自己声明（override 本身无法下沉）；
 * ②主线专属功能（m233 按面断开/m229 转化桌）——{@link #classify} 用带默认值语义的布尔钩子接住，
 * 本世代调用点恒传 false（P-C 到期升级调用点即得蓝本行为，不改本方法）。
 *
 * <p><b>"自家网络方块名单"这份数据，刻意没有下沉进本类</b>：两代的名单字面来自不同的注册类
 * （{@code ModBlocks} 六块 / {@code RetroBlocks} 三块），本来就该各世代在自己的 {@code endFor}
 * 方法体里手写——{@code tools_retro_parity_check.py}（第 16 闸）靠**抓 endFor 方法体里的
 * 前缀.常量 token** 防漏抄（m469 血案），名单字面必须留在各自 endFor 里，闸的判据才有对象可量。
 */
public final class CableEndCore {

    private CableEndCore() { }

    public static final EnumProperty<CableEnd> NORTH = EnumProperty.create("north", CableEnd.class);
    public static final EnumProperty<CableEnd> SOUTH = EnumProperty.create("south", CableEnd.class);
    public static final EnumProperty<CableEnd> EAST  = EnumProperty.create("east", CableEnd.class);
    public static final EnumProperty<CableEnd> WEST  = EnumProperty.create("west", CableEnd.class);
    public static final EnumProperty<CableEnd> UP    = EnumProperty.create("up", CableEnd.class);
    public static final EnumProperty<CableEnd> DOWN  = EnumProperty.create("down", CableEnd.class);

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

    /** 六面端点值拼碰撞箱（蓝本原注：缓存命中率极高——同拓扑的线到处都是）。 */
    public static VoxelShape shapeFor(BlockState state) {
        return SHAPES.computeIfAbsent(state, st -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if (st.getValue(END_PROPS.get(d)) != CableEnd.NONE) shape = Shapes.or(shape, ARMS.get(d));
            }
            return shape;
        });
    }

    /** {@link net.minecraft.world.level.block.state.StateDefinition.Builder#add} 六件套（两代 createBlockStateDefinition 同调）。 */
    public static void addStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    /** 放置态/默认态：六面先填 NONE（两代构造器与 getStateForPlacement 起手式同款）。 */
    public static BlockState allNone(BlockState any) {
        BlockState s = any;
        for (EnumProperty<CableEnd> p : END_PROPS.values()) s = s.setValue(p, CableEnd.NONE);
        return s;
    }

    /**
     * 三态判定核心（蓝本 endFor 主体，两代逐句对完全同序）：本端断开→NONE；
     * 邻居是数据线→按对端断开与否出 NONE/CABLE；邻居是己方网络方块→PLUG；
     * 邻居有 Container→PLUG；邻居是特殊出售目标（m229）→PLUG；邻居暴露 m404 传输平台口→PLUG；
     * 其余→NONE。<b>调用方在 {@code endFor} 里把"名单/断开状态"判成布尔后传入本方法</b>——
     * 名单字面留在调用方（第 16 闸看的就是那份 token），断开类钩子本世代恒传 false。
     *
     * @param isCableNeighbor    邻居是否为"本世代的数据线方块"（调用方 {@code instanceof} 自判）
     * @param selfFaceDisabled   m233：本端（这条数据线自己）是否已按面断开（本世代恒 false）
     * @param neighborFaceDisabled m233：邻居若为数据线，其对端是否也按面断开（本世代恒 false）
     * @param isNetworkPlug      邻居是否为己方网络方块之一（名单字面留在调用方 endFor 里）
     * @param isSellNeighbor     m229：邻居是否为特殊出售目标如转化桌（本世代恒 false）
     */
    public static CableEnd classify(LevelAccessor world, BlockPos pos,
                                     boolean isCableNeighbor, boolean selfFaceDisabled, boolean neighborFaceDisabled,
                                     boolean isNetworkPlug, boolean isSellNeighbor) {
        if (selfFaceDisabled) return CableEnd.NONE; // m233 本端断开（放置期 BE 未建=掩码 0，天然直通）
        if (isCableNeighbor) return neighborFaceDisabled ? CableEnd.NONE : CableEnd.CABLE; // m233 对端断开=这边也不伸
        if (isNetworkPlug) return CableEnd.PLUG;
        if (world.getBlockEntity(pos) instanceof Container) return CableEnd.PLUG;
        if (isSellNeighbor) return CableEnd.PLUG; // m229 转化桌（纯 id 判无反射，双端安全）
        // m224 任意暴露 Fabric Transfer API 的存储也伸插头（Create 置物台/AE2 接口这类不实现 Container 的
        // 全吃）；只在服务端权威世界查（客户端注册表可能缺第三方登记，方块状态由服务端同步），
        // 世界生成期的 ChunkRegion 不是 World 直接跳过。
        if (world instanceof Level w && !w.isClientSide
                && com.sdzjz.storage.Xfer.find(w, pos, null) != null) return CableEnd.PLUG; // m404 平台口
        return CableEnd.NONE;
    }
}
