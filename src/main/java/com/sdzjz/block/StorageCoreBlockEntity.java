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
public class StorageCoreBlockEntity extends BlockEntity implements com.sdzjz.machine.StorageAccess {

    /** m98：类型上限走配置——storageTypesPerTier 0=无限(默认)，>0=每级该数(旧机制27)。tier 保留兼容旧档与配置回切。
     *  m293 注：展示口径归本方法；插入闸另受 absoluteStorageTypeSafetyLimit(默认8192) 技术硬顶，见 typeGate()。 */
    private static int typesPerTier() { return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier; }
    private final LinkedHashMap<String, Long> store = new LinkedHashMap<>();
    // m130 精确存储：带组件物品的模板账本（模板 count=1 + 独立 long 计数，两表下标对齐）。
    // 普通物品仍走 store（机器热路径零改动）；过滤器/熔炉/传感器只见普通账本——机器不吃附魔书（设计留痕）。
    private final List<ItemStack> exactTpl = new ArrayList<>();
    private final List<Long> exactN = new ArrayList<>();
    private int tier = 1;

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
        if (world.isClient) return;
        BlockPos ip = pos.toImmutable();
        if (!CORES.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>()).add(ip)) return; // 已登记=桶里必已有
        CORE_BUCKETS.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>())
                .computeIfAbsent(bucketKey(ip.getX(), ip.getZ()), k -> new HashSet<>()).add(ip);
    }
    public static void unregister(Level world, BlockPos pos) {
        Set<BlockPos> s = CORES.get(world.getRegistryKey());
        if (s != null) s.remove(pos);
        Map<Long, Set<BlockPos>> bm = CORE_BUCKETS.get(world.getRegistryKey()); // m279 桶同步剔除
        if (bm != null) {
            long k = bucketKey(pos.getX(), pos.getZ());
            Set<BlockPos> b = bm.get(k);
            if (b != null && b.remove(pos) && b.isEmpty()) bm.remove(k); // 空桶回收防泄漏
        }
    }
    public static Set<BlockPos> coresIn(Level world) {
        return CORES.getOrDefault(world.getRegistryKey(), Set.of());
    }

    /** m279 范围查询：只访问 AABB 覆盖的桶，返回快照列表（调用方 loadedCoreAt 触发 unregister
     *  也不炸游标）。候选按桶粒度粗筛，精确球面距离仍由调用方判（口径与旧全表扫一致）。
     *  桶格数超阈值（超大 range 配置）退回全表快照——桶遍历不许比旧路径还贵。 */
    public static List<BlockPos> coresNear(Level world, BlockPos center, long range) {
        Map<Long, Set<BlockPos>> bm = CORE_BUCKETS.get(world.getRegistryKey());
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
        if (!world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return null;
        if (world.getBlockEntity(p) instanceof StorageCoreBlockEntity core) return core;
        unregister(world, p);
        return null;
    }

    public static void tick(Level world, BlockPos pos, BlockState state, StorageCoreBlockEntity be) {
        if (world.isClient) return;
        register(world, pos);
    }

    @Override
    public void markRemoved() {
        if (this.world != null) unregister(this.world, this.pos);
        super.markRemoved();
    }

    public int tier() { return tier; }
    public int maxTypes() { int p = typesPerTier(); return p <= 0 ? Integer.MAX_VALUE : p * tier; }
    // ===== m295 精确账本内存索引（外部审计 P2：账本自身仍是 List 线性找）=====
    // 列表仍是唯一权威与落盘格式（undo 语义/存档零改动），旁挂 transient 键→下标索引：
    // 查找 逐条组件深比 O(n) → 哈希 O(1)（m404 起键=加载器中立的 StackKey，equals 直调
    // areItemsAndComponentsEqual 与旧 ItemVariant 键同口径）；追加 O(1)；删除 O(n) 但只平移整数下标（无组件比较，常数便宜
    // 一两个量级）；**事务回滚重放/NBT 读回直接置脏懒重建**（abort/读档罕见，正确性优先）。
    private transient java.util.HashMap<com.sdzjz.storage.StackKey, Integer> exactIdx; // null=脏

    private int exactIndexOf(ItemStack probe) {
        var m = exactIdx;
        if (m == null) {
            m = new java.util.HashMap<>();
            for (int i = 0; i < exactTpl.size(); i++) m.put(com.sdzjz.storage.StackKey.of(exactTpl.get(i)), i);
            exactIdx = m;
        }
        Integer i = m.get(com.sdzjz.storage.StackKey.of(probe));
        return i == null ? -1 : i;
    }

    private void exactIdxAppended() { // append 之后调（新条目下标=size-1）
        if (exactIdx != null) exactIdx.put(com.sdzjz.storage.StackKey.of(exactTpl.get(exactTpl.size() - 1)), exactTpl.size() - 1);
    }

    private void exactIdxRemoved(int i, ItemStack tpl) { // remove(i) 之后调：删键+平移
        var m = exactIdx;
        if (m == null) return;
        m.remove(com.sdzjz.storage.StackKey.of(tpl));
        for (var e : m.entrySet()) if (e.getValue() > i) e.setValue(e.getValue() - 1);
    }

    public int usedTypes() { return store.size() + exactTpl.size(); } // m130：精确条目同占类型额度

    /** m293 插入闸口径（外部审计 P2：默认无限类型=最大的存档/NBT/GUI 排序压力源没有技术保险）：
     *  玩法额度与绝对安全上限取小。安全上限独立于玩法——typesPerTier=0 的"无限仓库"照常显示无限、
     *  照常用，只是单核心新类型到 8192 种后拒收（已有超限存档不裁账，只是加不了新类型）。≤0=关闸。 */
    private int typeGate() {
        int hard = com.sdzjz.config.SdzjzConfig.get().absoluteStorageTypeSafetyLimit;
        int play = maxTypes();
        return hard > 0 ? (int) Math.min((long) play, (long) hard) : play;
    }
    public void upgrade() { tier++; markDirty(); }

    /** m273：非负计数饱和加法——溢出封顶 Long.MAX_VALUE（把 FTA insert 路径既有的
     *  Long.MAX_VALUE-cur 口径收成公共辅助；账本/缓存/经验库全部裸加法统一走这里）。 */
    public static long satAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r; // 符号溢出检测：两非负操作数溢出必为负
    }

    // ===== m80c 经验库：网络级经验银行（数据面板界面存/取）=====
    private long xpBank = 0;
    public long xpBank() { return xpBank; }
    public void xpAdd(long points) { if (points > 0) { xpBank = satAdd(xpBank, points); markDirty(); } } // m273 饱和加法
    /** 取出至多 max 点，返回实际取出。 */
    public long xpTake(long max) {
        long t = Math.min(xpBank, Math.max(0, max));
        xpBank -= t;
        if (t > 0) markDirty();
        return t;
    }

    public long count(String id) {
        Long v = store.get(id);
        return v == null ? 0L : v;
    }

    /** 存入。默认无限类型（m98）；config 启用上限时，类型未满或已有该类型才收（拒收时栈原样保留）。
     *  m130：带组件的物品自动分流进精确账本，组件原样保存——附魔书/药水/损耗工具/带阶位机器全部可入仓。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!stack.getComponentChanges().isEmpty()) { depositExact(stack); return; }
        String id = BuiltInRegistries.ITEM.getId(stack.getItem()).toString();
        if (!store.containsKey(id) && usedTypes() >= typeGate()) return; // m293 安全硬顶同闸
        store.merge(id, (long) stack.getCount(), StorageCoreBlockEntity::satAdd); // m273 饱和加法
        storeRev++; // m218
        stack.setCount(0);
        markDirty();
    }

    /** m130：精确存入——按「物品+组件」找同款条目并账；新类型受同一类型上限（拒收时栈原样保留）。 */
    public void depositExact(ItemStack stack) {
        if (stack.isEmpty()) return;
        int hit = exactIndexOf(stack); // m295 索引直查（等价旧 areItemsAndComponentsEqual 扫描）
        if (hit >= 0) {
            exactN.set(hit, satAdd(exactN.get(hit), stack.getCount())); // m273 饱和加法
            exactRev++; // m322
            stack.setCount(0);
            markDirty();
            return;
        }
        if (usedTypes() >= typeGate()) return; // m293
        exactTpl.add(stack.copyWithCount(1));
        exactN.add((long) stack.getCount());
        exactIdxAppended(); // m295
        exactRev++; // m322
        stack.setCount(0);
        markDirty();
    }

    /** m130：精确取出——按「物品+组件」匹配模板，返回实际取出数量。 */
    public int withdrawExact(ItemStack template, int amount) {
        if (template == null || template.isEmpty() || amount <= 0) return 0;
        int i = exactIndexOf(template); // m295 索引直查
        if (i >= 0) {
            long have = exactN.get(i);
            int take = (int) Math.min((long) amount, have);
            long left = have - take;
            if (left <= 0) { ItemStack t = exactTpl.get(i); exactTpl.remove(i); exactN.remove(i); exactIdxRemoved(i, t); }
            else exactN.set(i, left);
            if (take > 0) { exactRev++; markDirty(); } // m322
            return take;
        }
        return 0;
    }

    /** m130：精确账本视图（面板聚合用；模板 count 恒为 1，计数走 exactCount）。 */
    public List<ItemStack> exactTemplates() { return exactTpl; }
    public long exactCount(int i) { return (i >= 0 && i < exactN.size()) ? exactN.get(i) : 0L; }

    public int withdraw(String id, int amount) {
        Long have = store.get(id);
        if (have == null || amount <= 0) return 0;
        int take = (int) Math.min((long) amount, have);
        long left = have - take;
        if (left <= 0) store.remove(id); else store.put(id, left);
        storeRev++; // m218
        markDirty();
        return take;
    }

    public Map<String, Long> storeView() { return store; }

    // m218 账本修订号：store 每次变更 +1（六处变更点逐一挂钩+NBT读回一处）。数据面板聚合视图靠它做
    // "无变更不重建"缓存——只增不减，跨核心求和作指纹（和相等⇔各核心都没动过，因为单调）。
    private long storeRev;
    public long storeRev() { return storeRev; }

    // m322 精确账本修订号：storeRev 只罩普通账本（m218 面板 storeView 缓存只消费普通支路，当年够用），
    // 但数据面板主快照（masterEntries）同时聚合精确条目——组件件变动若无修订号，快照缓存会陈旧。
    // 口径同 storeRev：单调只增、跨核心求和作指纹；回滚/读档宁可多记（多失效=白重建一次，无害）。
    private long exactRev;
    public long exactRev() { return exactRev; }

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
            storeRev++; // m218（回滚也是变更；口径同旧版=每次回滚记一次）
            exactRev++; // m322：undo 可能碰过精确账本——宁可多记，白重建一次无害
        }

        @Override protected void onFinalCommit() { undoJournal.clear(); markDirty(); }

        @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (one.getComponentChanges().isEmpty()) { // 与 deposit 同一分流：无组件走普通账本
                String id = BuiltInRegistries.ITEM.getId(one.getItem()).toString();
                if (!store.containsKey(id) && usedTypes() >= typeGate()) return 0; // m293
                long cur = store.getOrDefault(id, 0L);
                long accept = Math.min(maxAmount, Long.MAX_VALUE - cur);
                if (accept <= 0) return 0;
                updateSnapshots(tx);
                final boolean had = store.containsKey(id); // m278 前像：键此前是否存在
                undoJournal.add(() -> { if (had) store.put(id, cur); else store.remove(id); });
                store.put(id, cur + accept);
                storeRev++; // m218
                return accept;
            }
            int hit = exactIndexOf(one); // m295 索引直查（带组件走精确账本，m130 同款口径）
            if (hit >= 0) {
                long cur = exactN.get(hit);
                long accept = Math.min(maxAmount, Long.MAX_VALUE - cur);
                if (accept <= 0) return 0;
                updateSnapshots(tx);
                final int idx = hit; // m278 前像
                undoJournal.add(() -> exactN.set(idx, cur));
                exactN.set(hit, cur + accept);
                exactRev++; // m322
                return accept;
            }
            if (usedTypes() >= typeGate()) return 0; // m293
            updateSnapshots(tx);
            undoJournal.add(() -> { // m278 undo=撤尾（逆序重放保证撤到的必是本条 add）；m295 动列表即置脏索引
                int last = exactTpl.size() - 1; exactTpl.remove(last); exactN.remove(last); exactIdx = null; });
            exactTpl.add(one); // toStack(1) 即模板规格（count=1，组件原样）
            exactN.add(maxAmount);
            exactIdxAppended(); // m295
            exactRev++; // m322
            return maxAmount;
        }

        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (one.getComponentChanges().isEmpty()) {
                String id = BuiltInRegistries.ITEM.getId(one.getItem()).toString();
                long have = store.getOrDefault(id, 0L);
                long take = Math.min(have, maxAmount);
                if (take <= 0) return 0;
                updateSnapshots(tx);
                undoJournal.add(() -> store.put(id, have)); // m278 前像（take>0 ⇒ 键此前必存在）
                if (have - take <= 0) store.remove(id); else store.put(id, have - take);
                storeRev++; // m218
                return take;
            }
            int i = exactIndexOf(one); // m295 索引直查
            if (i >= 0) {
                long have = exactN.get(i);
                long take = Math.min(have, maxAmount);
                if (take <= 0) return 0;
                updateSnapshots(tx);
                final int idx = i;
                if (have - take <= 0) {
                    final ItemStack ptpl = exactTpl.get(i); // m278 结构前像：原下标插回（模板从不被原地改，存引用即安全）
                    undoJournal.add(() -> { exactTpl.add(idx, ptpl); exactN.add(idx, have); exactIdx = null; }); // m295 动列表即置脏
                    exactTpl.remove(i); exactN.remove(i);
                    exactIdxRemoved(i, ptpl); // m295
                } else {
                    undoJournal.add(() -> exactN.set(idx, have));
                    exactN.set(i, have - take);
                }
                exactRev++; // m322
                return take;
            }
            return 0;
        }

        @Override public Iterator<StorageView<ItemVariant>> iterator() {
            List<StorageView<ItemVariant>> views = new ArrayList<>(store.size() + exactTpl.size());
            for (String id : store.keySet()) { // m350 撤键拷贝：views 表在此建完才外泄，外部 extract 只动 views 走 View 懒读，建表期 store 零突变（原"迭代中抽空"担忧指向返回后的消费期，与建表游标无关）
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.of(id));
                ItemVariant v = ItemVariant.of(it);
                if (v.isBlank()) continue; // 已卸载物品条目跳过（缺失 id 落回 air 即 blank）
                views.add(new View(v, id));
            }
            for (ItemStack tpl : new ArrayList<>(exactTpl)) views.add(new View(ItemVariant.of(tpl), null));
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
                if (id != null) return store.getOrDefault(id, 0L);
                ItemStack one = v.toStack(1);
                int i = exactIndexOf(one); // m295 索引直查（管道每 tick 模拟就打它，收益最大的一处）
                return i >= 0 ? exactN.get(i) : 0;
            }

            @Override public long getCapacity() { return Long.MAX_VALUE; }
        }
    }

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
                BlockPos np = p.offset(d);
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
    protected void writeNbt(CompoundTag nbt, HolderLookup.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putInt("tier", tier);
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : store.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey());
            c.putLong("n", e.getValue());
            list.add(c);
        }
        nbt.put("store", list);
        ListTag ex = new ListTag(); // m130：精确账本持久化（模板 encode + long 计数）
        for (int i = 0; i < exactTpl.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.put("item", exactTpl.get(i).encode(lookup));
            c.putLong("n", exactN.get(i));
            ex.add(c);
        }
        nbt.put("exact", ex);
        nbt.putLong("xpBank", xpBank);
    }

    @Override
    protected void readNbt(CompoundTag nbt, HolderLookup.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        tier = Math.max(1, nbt.getInt("tier"));
        xpBank = Math.max(0, nbt.getLong("xpBank"));
        store.clear();
        ListTag list = nbt.getList("store", Tag.COMPOUND_TYPE);
        int dropped = 0; // m273：账本读入校验——空id/非正计数=非法条目（写路径 left<=0 即 remove，零值从不合法落盘；负数毒化全部计数算术）
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String id = c.getString("id");
            long n = c.getLong("n");
            if (!id.isEmpty() && n > 0) store.put(id, n); else dropped++;
        }
        if (dropped > 0) com.sdzjz.Sdzjz.LOGGER.warn("存储核心 {} 账本读入丢弃 {} 条非法条目（空id或非正计数）", pos, dropped);
        storeRev++; // m218（NBT 读回=整本换血，记一次）
        exactRev++; // m322：下方精确账本同被整本换血
        exactIdx = null; // m295 读档置脏（懒重建）
        exactTpl.clear(); // m130：读回精确账本（解析失败/物品已卸载的条目静默跳过，不炸档）
        exactN.clear();
        ListTag ex = nbt.getList("exact", Tag.COMPOUND_TYPE);
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag c = ex.getCompound(i);
            ItemStack t = ItemStack.fromNbt(lookup, c.getCompound("item")).orElse(ItemStack.EMPTY);
            long n = c.getLong("n");
            if (!t.isEmpty() && n > 0) { exactTpl.add(t.copyWithCount(1)); exactN.add(n); }
        }
    }
}
