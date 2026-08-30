package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

    // ===== m161c 跨模组直连：Fabric Transfer API =====
    // 管道/机器怼在存储核心方块任意面即可存取（Create/Modern Industrialization/Tech Reborn/AE2
    // 等一切走 fabric-transfer-api 的模组；注册在 Sdzjz.onInitialize）。双账本全量暴露：普通账本按
    // id、精确账本连组件（附魔书可被管道按模板抽走）。类型上限与 deposit 同一道闸——满员拒收新类型。
    // 事务安全：m278 起增量 undo 日志（旧版整本浅拷 O(全账本)/带写事务，见 FabricLedger 内注释）；
    // markDirty 推迟到 onFinalCommit——事务中动世界状态是 FTA 明令禁区，回滚回不掉
    // dirty 标记。防长整溢出：管道惯用 Long.MAX_VALUE 试探性 insert，累加前先钳余量。
    // 【盲写 API 对表备忘（沙箱无 MC 依赖，编译报错按此四点改）】：
    //  ① Storage#iterator 为无参（1.19.3 起去掉 TransactionContext 参数）——报错就补参；
    //  ② SnapshotParticipant 包名 ...transfer.v1.transaction.base——报错查 fabric-transfer-api-v1 源；
    //  ③ ItemStorage.SIDED.registerForBlockEntity(单数) 在 Sdzjz.java——无此重载改 registerForBlockEntities；
    //  ④ ItemVariant.of(ItemStack)/of(Item)/toStack(int) 签名不符查 ItemVariant 源。
    private FabricLedger fabricLedger;

    public Storage<ItemVariant> fabricStorage() {
        if (fabricLedger == null) fabricLedger = new FabricLedger();
        return fabricLedger;
    }

    public class FabricLedger extends SnapshotParticipant<Integer> implements Storage<ItemVariant> {

        // m278 增量事务日志：快照=日志位点(int)，回滚=从尾到位点逆序重放 undo——逆序保证精确账本
        // add/remove 的按下标前像恢复正确（每步 undo 都把状态退回到该步之前，下标必然对得上）。
        // 旧版整本浅拷 O(全账本) 每带写事务一次；管道模组每口每 tick 开事务，大仓库=纯拷贝+GC 风暴。
        // 现在 O(实际触碰条目)，典型管道操作=1 条。嵌套事务天然支持：每层快照各记自己的位点。
        // 附带更稳：事务窗口内若有非事务路径（withdraw/deposit 手账）动过账本，旧整本回滚会把
        // 那些改动一起冲掉，增量日志只撤自己记过的条目。日志不变量：事务外恒为空
        // （最外层 commit 走 onFinalCommit 清空 / 最外层 abort 截断回位点 0）。
        private final ArrayList<Runnable> undoJournal = new ArrayList<>();

        @Override protected Integer createSnapshot() { return undoJournal.size(); }

        @Override protected void readSnapshot(Integer pos) {
            for (int i = undoJournal.size() - 1; i >= pos; i--) undoJournal.get(i).run();
            undoJournal.subList(pos, undoJournal.size()).clear();
            ledger.bumpStoreRev(); // m218（回滚也是变更；口径同旧版=每次回滚记一次）
            ledger.bumpExactRev(); // m322：undo 可能碰过精确账本——宁可多记，白重建一次无害
        }

        @Override protected void onFinalCommit() { undoJournal.clear(); setChanged(); }

        @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (one.getComponentsPatch().isEmpty()) { // 与 deposit 同一分流：无组件走普通账本
                String id = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
                if (!ledger.storeView().containsKey(id) && usedTypes() >= ledger.typeGate()) return 0; // m293
                long cur = ledger.storeView().getOrDefault(id, 0L);
                long accept = Math.min(maxAmount, Long.MAX_VALUE - cur);
                if (accept <= 0) return 0;
                updateSnapshots(tx);
                final boolean had = ledger.storeView().containsKey(id); // m278 前像：键此前是否存在
                undoJournal.add(() -> { if (had) ledger.storeView().put(id, cur); else ledger.storeView().remove(id); });
                ledger.storeView().put(id, cur + accept);
                ledger.bumpStoreRev(); // m218
                return accept;
            }
            int hit = exactIndexOf(one); // m295 索引直查（带组件走精确账本，m130 同款口径）
            if (hit >= 0) {
                long cur = ledger.exactCounts().get(hit);
                long accept = Math.min(maxAmount, Long.MAX_VALUE - cur);
                if (accept <= 0) return 0;
                updateSnapshots(tx);
                final int idx = hit; // m278 前像
                undoJournal.add(() -> ledger.exactCounts().set(idx, cur));
                ledger.exactCounts().set(hit, cur + accept);
                ledger.bumpExactRev(); // m322
                return accept;
            }
            if (usedTypes() >= ledger.typeGate()) return 0; // m293
            updateSnapshots(tx);
            undoJournal.add(() -> { // m278 undo=撤尾（逆序重放保证撤到的必是本条 add）；m295 动列表即置脏索引
                int last = ledger.exactTemplates().size() - 1; ledger.exactTemplates().remove(last); ledger.exactCounts().remove(last); ledger.markIndexDirty(); });
            ledger.exactTemplates().add(one); // toStack(1) 即模板规格（count=1，组件原样）
            ledger.exactCounts().add(maxAmount);
            exactIdxAppended(); // m295
            ledger.bumpExactRev(); // m322
            return maxAmount;
        }

        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (one.getComponentsPatch().isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
                long have = ledger.storeView().getOrDefault(id, 0L);
                long take = Math.min(have, maxAmount);
                if (take <= 0) return 0;
                updateSnapshots(tx);
                undoJournal.add(() -> ledger.storeView().put(id, have)); // m278 前像（take>0 ⇒ 键此前必存在）
                if (have - take <= 0) ledger.storeView().remove(id); else ledger.storeView().put(id, have - take);
                ledger.bumpStoreRev(); // m218
                return take;
            }
            int i = exactIndexOf(one); // m295 索引直查
            if (i >= 0) {
                long have = ledger.exactCounts().get(i);
                long take = Math.min(have, maxAmount);
                if (take <= 0) return 0;
                updateSnapshots(tx);
                final int idx = i;
                if (have - take <= 0) {
                    final ItemStack ptpl = ledger.exactTemplates().get(i); // m278 结构前像：原下标插回（模板从不被原地改，存引用即安全）
                    undoJournal.add(() -> { ledger.exactTemplates().add(idx, ptpl); ledger.exactCounts().add(idx, have); ledger.markIndexDirty(); }); // m295 动列表即置脏
                    ledger.exactTemplates().remove(i); ledger.exactCounts().remove(i);
                    exactIdxRemoved(i, ptpl); // m295
                } else {
                    undoJournal.add(() -> ledger.exactCounts().set(idx, have));
                    ledger.exactCounts().set(i, have - take);
                }
                ledger.bumpExactRev(); // m322
                return take;
            }
            return 0;
        }

        @Override public Iterator<StorageView<ItemVariant>> iterator() {
            List<StorageView<ItemVariant>> views = new ArrayList<>(ledger.storeView().size() + ledger.exactTemplates().size());
            for (String id : ledger.storeView().keySet()) { // m350 撤键拷贝：views 表在此建完才外泄，外部 extract 只动 views 走 View 懒读，建表期 store 零突变（原"迭代中抽空"担忧指向返回后的消费期，与建表游标无关）
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                ItemVariant v = ItemVariant.of(it);
                if (v.isBlank()) continue; // 已卸载物品条目跳过（缺失 id 落回 air 即 blank）
                views.add(new View(v, id));
            }
            for (ItemStack tpl : new ArrayList<>(ledger.exactTemplates())) views.add(new View(ItemVariant.of(tpl), null));
            return views.iterator();
        }

        /** 游标视图：金额按键实时查（迭代途中被别的事务抽走也读不到脏值）；抽取转正门统一走外层 extract。 */
        private class View implements StorageView<ItemVariant> {
            private final ItemVariant v;
            private final String id; // 非 null=普通账本键；null=精确账本按模板匹配

            View(ItemVariant v, String id) { this.v = v; this.id = id; }

            @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
                if (!v.equals(resource)) return 0;
                return FabricLedger.this.extract(resource, maxAmount, tx);
            }

            @Override public boolean isResourceBlank() { return v.isBlank(); }
            @Override public ItemVariant getResource() { return v; }

            @Override public long getAmount() {
                if (id != null) return ledger.storeView().getOrDefault(id, 0L);
                ItemStack one = v.toStack(1);
                int i = exactIndexOf(one); // m295 索引直查（管道每 tick 模拟就打它，收益最大的一处）
                return i >= 0 ? ledger.exactCounts().get(i) : 0;
            }

            @Override public long getCapacity() { return Long.MAX_VALUE; }
        }
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
    //   ④FTA 侧：ItemStorage.SIDED 已有显式注册（fabricStorage），显式注册优先于 Fabric 的
    //   Inventory 兜底包装——FTA 管道看到的仍是 FabricLedger 双账本，不会退化成幻影槽。
    // 【第三方越闸兜底】：规矩管不了野管道——有的 Container 管道不问 canPlaceItem 直接 setItem。
    //   类型闸拒收/配置关闸时**绝不吞件**：残料散落核心上方（掉落物比凭空蒸发轻一万倍；
    //   存储域"绝不落地"说的是自家路由，不适用于第三方硬塞）。
    private static final int[] PHANTOM_SLOT = {0};
    private static final int[] NO_SLOTS = {};

    /** 漏斗对接收货闸：与 deposit/depositExact 的类型闸完全同口径（先验后收，防 setItem 半路拒收）。 */
    private boolean hopperDockAccepts(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !com.sdzjz.config.SdzjzConfig.get().hopperDockingEnabled) return false;
        if (!stack.getComponentsPatch().isEmpty())
            return exactIndexOf(stack) >= 0 || usedTypes() < ledger.typeGate(); // 精确件：已有同款或类型有余
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
