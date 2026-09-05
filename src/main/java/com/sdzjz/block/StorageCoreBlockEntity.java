package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 存储核心：双账本逻辑仓储——普通(id→long) + 精确(物品+组件模板→long, m130)；类型数受等级上限；可升级。
 *  数据面板/机器经网络(数据线/相邻)访问；机器/过滤器/熔炉只见普通账本。 */
public class StorageCoreBlockEntity extends BlockEntity implements com.sdzjz.machine.StorageAccess,
        com.sdzjz.machine.StorageLedgerProbe, // m480 跨代行为契约探针：十三个账本方法本来就长这样，只是把这件事写进类型系统让编译器盯着（零方法体改动）
        net.minecraft.world.WorldlyContainer { // m460 漏斗对接：幻影槽，见文末「WorldlyContainer」节

    /** m98：类型上限走配置——storageTypesPerTier 0=无限(默认)，>0=每级该数(旧机制27)。tier 保留兼容旧档与配置回切。
     *  m293 注：展示口径归本方法；插入闸另受 absoluteStorageTypeSafetyLimit(默认8192) 技术硬顶，见 ledger.typeGate()。 */
    private static int typesPerTier() { return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier; }
    // m130 精确存储：带组件物品的模板账本（模板 count=1 + 独立 long 计数，两表下标对齐）。
    // 普通物品仍走 store（机器热路径零改动）；过滤器/熔炉/传感器只见普通账本——机器不吃附魔书（设计留痕）。

    private static final Map<ResourceKey<Level>, Set<BlockPos>> CORES = new HashMap<>();
    // m279 空间索引：按 (x>>6, z>>6) 64 格桶分区（y 不分桶，核心分布以水平为主）。与 CORES 平面表
    // 双写同源（register/unregister/clearAll 单一漏斗），范围查询只访问 AABB 覆盖的桶。
    private static final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> CORE_BUCKETS = new HashMap<>();
    private static final int BUCKET_SHIFT = 6; // 桶边长 64 格

    private static long bucketKey(int x, int z) { return packBuckets(x >> BUCKET_SHIFT, z >> BUCKET_SHIFT); }
    private static long packBuckets(int bx, int bz) { // 手工打包零新 API（高32=z桶 低32=x桶）
        return ((long) bz << 32) | (bx & 0xFFFFFFFFL);
    }

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_CORE_BE, pos, state);
    }

    public static void register(Level world, BlockPos pos) {
        if (world.isClientSide) return;
        BlockPos ip = pos.immutable();
        if (!CORES.computeIfAbsent(world.dimension(), k -> new HashSet<>()).add(ip)) return; // 已登记=桶里必已有
        CORE_BUCKETS.computeIfAbsent(world.dimension(), k -> new HashMap<>())
                .computeIfAbsent(bucketKey(ip.getX(), ip.getZ()), k -> new HashSet<>()).add(ip);
    }
    public static void unregister(Level world, BlockPos pos) {
        Set<BlockPos> s = CORES.get(world.dimension());
        if (s != null) s.remove(pos);
        Map<Long, Set<BlockPos>> bm = CORE_BUCKETS.get(world.dimension()); // m279 桶同步剔除
        if (bm != null) {
            long k = bucketKey(pos.getX(), pos.getZ());
            Set<BlockPos> b = bm.get(k);
            if (b != null && b.remove(pos) && b.isEmpty()) bm.remove(k); // 空桶回收防泄漏
        }
    }
    public static Set<BlockPos> coresIn(Level world) {
        return CORES.getOrDefault(world.dimension(), Set.of());
    }

    /** m279 范围查询：只访问 AABB 覆盖的桶，返回快照列表（调用方 loadedCoreAt 触发 unregister
     *  也不炸游标）。候选按桶粒度粗筛，精确球面距离仍由调用方判（口径与旧全表扫一致）。
     *  桶格数超阈值（超大 range 配置）退回全表快照——桶遍历不许比旧路径还贵。 */
    public static List<BlockPos> coresNear(Level world, BlockPos center, long range) {
        Map<Long, Set<BlockPos>> bm = CORE_BUCKETS.get(world.dimension());
        if (bm == null || bm.isEmpty()) return List.of();
        int r = (int) Math.min(range, 30_000_000L);
        int b0x = (center.getX() - r) >> BUCKET_SHIFT, b1x = (center.getX() + r) >> BUCKET_SHIFT;
        int b0z = (center.getZ() - r) >> BUCKET_SHIFT, b1z = (center.getZ() + r) >> BUCKET_SHIFT;
        long cells = (long) (b1x - b0x + 1) * (b1z - b0z + 1);
        if (cells > 1024 || cells > 4L * bm.size()) return List.copyOf(coresIn(world)); // 兜底：稀桶/巨范围走全表
        List<BlockPos> out = new ArrayList<>();
        for (int bx = b0x; bx <= b1x; bx++)
            for (int bz = b0z; bz <= b1z; bz++) {
                Set<BlockPos> b = bm.get(packBuckets(bx, bz));
                if (b != null) out.addAll(b);
            }
        return out;
    }

    public static Set<ResourceKey<Level>> dimensionsWithCores() {
        return CORES.keySet();
    }

    /** 服务器停止时清空登记表（防跨存档幽灵坐标）。 */
    public static void clearAll() {
        CORES.clear();
        CORE_BUCKETS.clear(); // m279
    }

    /**
     * 安全查找：只查已加载区块（getBlockEntity 在服务端会强制加载区块，遍历登记表时绝不能用）。
     * 区块已加载但不存在存储核心 → 判定为幽灵坐标，顺手从登记表剔除。
     */
    public static StorageCoreBlockEntity loadedCoreAt(Level world, BlockPos p) {
        if (!world.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
        if (world.getBlockEntity(p) instanceof StorageCoreBlockEntity core) return core;
        unregister(world, p);
        return null;
    }

    public static void tick(Level world, BlockPos pos, BlockState state, StorageCoreBlockEntity be) {
        if (world.isClientSide) return;
        register(world, pos);
    }

    @Override
    public void setRemoved() {
        if (this.level != null) unregister(this.level, this.worldPosition);
        super.setRemoved();
    }

    // ===== m485（真移植）：账本核心已下沉共用（xplat storage/StorageLedger，两代同一份代码）=====
    // 原 137 行实现整段搬走，本类只留转发；1.20.1 的同功能仿写件同刀删除。
    // 行为由 m480 的十类跨代契约压着（StorageDomainAssertions），两代同绿。
    private final com.sdzjz.storage.StorageLedger ledger = new com.sdzjz.storage.StorageLedger(this::setChanged);

    /** 共用账本直取（存档读写与面板聚合用）。 */
    public com.sdzjz.storage.StorageLedger ledger() { return ledger; }

    public int tier() { return ledger.tier(); }
    public int maxTypes() { return ledger.maxTypes(); }
    public int usedTypes() { return ledger.usedTypes(); }
    public void upgrade() { ledger.upgrade(); }
    public static long satAdd(long a, long b) { return com.sdzjz.storage.StorageLedger.satAdd(a, b); }
    public long xpBank() { return ledger.xpBank(); }
    public void xpAdd(long points) { ledger.xpAdd(points); }
    public long xpTake(long max) { return ledger.xpTake(max); }
    public long count(String id) { return ledger.count(id); }
    public void deposit(ItemStack stack) { ledger.deposit(stack); }
    public void depositExact(ItemStack stack) { ledger.depositExact(stack); }
    public int withdrawExact(ItemStack template, int amount) { return ledger.withdrawExact(template, amount); }
    public List<ItemStack> exactTemplates() { return ledger.exactTemplates(); }
    public long exactCount(int i) { return ledger.exactCount(i); }
    public int withdraw(String id, int amount) { return ledger.withdraw(id, amount); }
    public Map<String, Long> storeView() { return ledger.storeView(); }
    public long storeRev() { return ledger.storeRev(); }

    // m322 精确账本修订号：storeRev 只罩普通账本（m218 面板 storeView 缓存只消费普通支路，当年够用），
    // 但数据面板主快照（masterEntries）同时聚合精确条目——组件件变动若无修订号，快照缓存会陈旧。
    // 口径同 storeRev：单调只增、跨核心求和作指纹；回滚/读档宁可多记（多失效=白重建一次，无害）。
    public long exactRev() { return ledger.exactRev(); }

    // ===== m161c 跨模组直连：Fabric Transfer API（m533/F1c 起：适配器本体在 src/loader/FabricStorageAdapter）=====
    // m503（真移植 B5）：业务判断（分流判据/类型闸/undo 前像/索引维护）整段迁
    // xplat/storage/StorageLedger 两代共用（原 FabricLedger.insert/extract 内脏搬去
    // StorageLedger.ftaInsert/ftaExtract，1.20.1 的同功能重写同刀删除）。**本类只剩薄壳**：
    // FTA 生命周期钩子（快照/回滚/最终提交）+ ItemVariant↔ItemStack 类型转换 + iterator 物化
    // ——这层皮不能再往下沉：xplat 是"见 MC、不见加载器"的地界（m406 分层硬闸），Storage/
    // ItemVariant/TransactionContext/SnapshotParticipant 这些 FTA 类型本身就是加载器符号，
    // 实现这个接口这件事只能留在两代各自源集里做（详见 StorageLedger 里三个 fta 前缀方法的类注，
    // 那份注释也记着这次顺手修的真编译错：exactIndexOf/exactIdxAppended/exactIdxRemoved 六处
    // 调用点漏了 ledger. 前缀，从 m485 账本下沉起编译不过，两层安全网当年都没接住）。
    // m533（F1c）：上面那段「薄壳」已整段搬去 src/loader/FabricStorageAdapter（原文一字未改，机械替换三类）；
    // 本类不再出现任何 FTA 符号——第 13 闸 SRC_GLUE_PENDING 最后一件销账，`src/` 可整挂 NeoForge（F1d）。
    // 留下的只有一个**不透明适配器缓存槽**：加载器提供侧（Fabric=FabricStorageAdapter.of / NeoForge=IItemHandler 适配器）
    // 首次取时建一个并存在这里，之后恒返同一实例——SnapshotParticipant 的 undo 日志住在适配器实例里，
    // 同一 BE 若每次 find 都新建实例，嵌套事务的快照位点就会串账。业务层不解释这个 Object。
    private Object transferAdapter;

    /** 加载器胶水专用：首次调用用 create 建适配器并缓存，之后恒返同一实例。业务代码不要调它。 */
    public Object transferAdapter(java.util.function.Supplier<Object> create) {
        if (transferAdapter == null) transferAdapter = create.get();
        return transferAdapter;
    }

    // ===== m460 漏斗对接：幻影槽 WorldlyContainer =====
    // 原版漏斗/漏斗矿车不走 FTA（只认 Container/WorldlyContainer），m161c 直连管不到它们——本节补上。
    // 幻影槽设计：对外恒 1 格恒空（getItem 恒 EMPTY）→ 漏斗看见"空槽"就整栈 setItem 进来，我们当场
    // deposit 入账（普通/精确双账本同 shift 存入口径，组件保真）；下一拍槽又是空的，吞吐=漏斗自身
    // 节奏（8t/件≈2.5件/秒）。禁抽取三闸：canTakeItemThroughFace 恒假 + getItem 恒空 + removeItem 恒空
    // ——漏斗贴核心底面抽不走任何东西（"插入即入账、禁抽取"，HANDOVER m161 后续①原案）。
    // 【自冲突审计（动手前全树 grep instanceof Container）】：①findTarget/resolveOutTarget 两处
    //   StorageCoreBlockEntity 分支都排在 Container 分支之前——核心变容器后仍走存储核心正路；
    //   ②DataCableBlock.endFor 对 STORAGE_CORE 有显式 PLUG 分支在 Container 判定之前——线缆视觉不变；
    //   ③区块扫描器 conS 统计会把核心计入"容器"——纯报告口径，无玩法影响（记档）；
    //   ④FTA 侧：ItemStorage.SIDED 已有显式注册（FabricEntry→FabricStorageAdapter.of，m533 前是 fabricStorage()），显式注册优先于 Fabric 的
    //   Inventory 兜底包装——FTA 管道看到的仍是双账本适配器，不会退化成幻影槽。
    // 【第三方越闸兜底】：规矩管不了野管道——有的 Container 管道不问 canPlaceItem 直接 setItem。
    //   类型闸拒收/配置关闸时**绝不吞件**：残料散落核心上方（掉落物比凭空蒸发轻一万倍；
    //   存储域"绝不落地"说的是自家路由，不适用于第三方硬塞）。
    private static final int[] PHANTOM_SLOT = {0};
    private static final int[] NO_SLOTS = {};

    /** 漏斗对接收货闸：与 deposit/depositExact 的类型闸完全同口径（先验后收，防 setItem 半路拒收）。 */
    private boolean hopperDockAccepts(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !com.sdzjz.config.SdzjzConfig.get().hopperDockingEnabled) return false;
        if (!stack.getComponentsPatch().isEmpty())
            return ledger.exactIndexOf(stack) >= 0 || usedTypes() < ledger.typeGate(); // 精确件：已有同款或类型有余
        return ledger.storeView().containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) || usedTypes() < ledger.typeGate();
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return true; } // 幻影槽恒空（漏斗抽取侧因此恒跳过）
    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (com.sdzjz.config.SdzjzConfig.get().hopperDockingEnabled) deposit(stack); // 收下即清空栈；类型满原样留着
        if (!stack.isEmpty() && level != null && !level.isClientSide) { // 越闸兜底：绝不吞件，散落核心上方
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, stack.copy());
            stack.setCount(0);
        }
    }
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return false; } // 无 GUI，容器口不对玩家开
    @Override public void clearContent() { } // 幻影槽无内容可清（账本清空只走管理命令，不走容器口）
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return hopperDockAccepts(stack); }
    @Override public int[] getSlotsForFace(Direction side) {
        return com.sdzjz.config.SdzjzConfig.get().hopperDockingEnabled ? PHANTOM_SLOT : NO_SLOTS;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) { return hopperDockAccepts(stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) { return false; } // 禁抽取

    /** BFS：从某位置经数据线/相邻找到所有存储核心。 */
    public static List<StorageCoreBlockEntity> connectedCores(Level world, BlockPos from) {
        List<StorageCoreBlockEntity> out = new ArrayList<>();
        if (world == null) return out;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        q.add(from); seen.add(from);
        int limit = 4096;
        while (!q.isEmpty() && limit-- > 0) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = p.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, p, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCoreBlockEntity core) { out.add(core); q.add(np); }
                else if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        nbt.putInt("tier", ledger.tierRaw());
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : ledger.storeView().entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey());
            c.putLong("n", e.getValue());
            list.add(c);
        }
        nbt.put("store", list);
        ListTag ex = new ListTag(); // m130：精确账本持久化（模板 encode + long 计数）
        for (int i = 0; i < ledger.exactTemplates().size(); i++) {
            CompoundTag c = new CompoundTag();
            c.put("item", ledger.exactTemplates().get(i).save(lookup));
            c.putLong("n", ledger.exactCounts().get(i));
            ex.add(c);
        }
        nbt.put("exact", ex);
        nbt.putLong("xpBank", ledger.xpBank());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        ledger.setTier(nbt.getInt("tier"));
        ledger.setXpBank(Math.max(0, nbt.getLong("xpBank")));
        ledger.storeView().clear();
        ListTag list = nbt.getList("store", Tag.TAG_COMPOUND);
        int dropped = 0; // m273：账本读入校验——空id/非正计数=非法条目（写路径 left<=0 即 remove，零值从不合法落盘；负数毒化全部计数算术）
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String id = c.getString("id");
            long n = c.getLong("n");
            if (!id.isEmpty() && n > 0) ledger.storeView().put(id, n); else dropped++;
        }
        if (dropped > 0) com.sdzjz.Sdzjz.LOGGER.warn("存储核心 {} 账本读入丢弃 {} 条非法条目（空id或非正计数）", worldPosition, dropped);
        ledger.bumpStoreRev(); // m218（NBT 读回=整本换血，记一次）
        ledger.bumpExactRev(); // m322：下方精确账本同被整本换血
        ledger.markIndexDirty(); // m295 读档置脏（懒重建）
        ledger.exactTemplates().clear(); // m130：读回精确账本（解析失败/物品已卸载的条目静默跳过，不炸档）
        ledger.exactCounts().clear();
        ListTag ex = nbt.getList("exact", Tag.TAG_COMPOUND);
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag c = ex.getCompound(i);
            ItemStack t = ItemStack.parse(lookup, c.getCompound("item")).orElse(ItemStack.EMPTY);
            long n = c.getLong("n");
            if (!t.isEmpty() && n > 0) { ledger.exactTemplates().add(t.copyWithCount(1)); ledger.exactCounts().add(n); }
        }
    }
}
