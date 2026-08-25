package com.sdzjz.retro;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * m443 刀②：1.20.1 存储核心账本——语义蓝本=Legacy {@code block/StorageCoreBlockEntity}（逐段对照新写，
 * 不复制改；每处与蓝本的语义偏差在行内注释指认）。双账本：普通(id→long) + 精确(物品+tag 模板→long)。
 *
 * <p><b>本世代口径三条</b>：①精确分流从"组件增量非空"对位为 {@link ItemStack#hasTag()}（m436 方案：
 * 组件哈希退 tag 哈希），身份键={@link TagStackKey}；②NBT 键与 Legacy 同名同布局
 * （tier/store/exact/xpBank，store 条目 id/n、exact 条目 item/n）——1.20.5 原版 DFU 升档时把物品 tag
 * 自动收进 custom_data（m436 红利），存档升 1.21.1 零迁移代码；③CORES 登记表/桶索引/BFS 均属机器与
 * 线缆消费面，随刀③（m444）与 P-C 移植，本类不带（蓝本对应段落=范围外，非漏抄）。
 *
 * <p>xplat 未挂本世代 → 不实现 StorageAccess 薄口、不消费 ItemData 门面（那两件建在 1.20.5+ 类型上），
 * 读写直用原版 hasTag/getTag——m353"读→view 写→copy"铁律的 tag 版：模板 tag 只读绝不改。
 */
public final class StorageCore120 extends BlockEntity implements com.sdzjz.machine.StorageAccess { // m464 挂主线契约：deposit/withdraw/count/storeView 四口现成，生产 tick 与蓝本辅件同型

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("sdzjz");

    /** m98：类型上限走配置（common 同源，不抄数字）——storageTypesPerTier 0=无限(默认)，>0=每级该数。 */
    private static int typesPerTier() { return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier; }
    private final LinkedHashMap<String, Long> store = new LinkedHashMap<>();
    // m130 精确存储：带 tag 物品的模板账本（模板 count=1 + 独立 long 计数，两表下标对齐）。
    // 普通物品仍走 store（将来机器热路径零改动）；机器/过滤器只见普通账本的设计留痕同蓝本。
    private final List<ItemStack> exactTpl = new ArrayList<>();
    private final List<Long> exactN = new ArrayList<>();
    private int tier = 1;

    public StorageCore120(BlockPos pos, BlockState state) {
        super(RetroBlocks.STORAGE_CORE_BE, pos, state);
    }

    // ===== m444 网络连通（蓝本 connectedCores/loadedCoreAt 的本世代裁剪版：CORES 登记表/桶索引
    // 属机器消费面随 P-C，loadedCoreAt 去掉幽灵剔除支路——没有登记表就没有幽灵；m233 linkBlocked
    // 按面断开随 P-C 链接器，BFS 暂无断边闸，到期在 seen 之前插同位）=====

    /** BFS：从某位置经数据线/相邻找到所有存储核心（4096 上限同蓝本）。 */
    public static List<StorageCore120> connectedCores(net.minecraft.world.level.Level world, BlockPos from) {
        List<StorageCore120> out = new ArrayList<>();
        if (world == null) return out;
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(from); seen.add(from);
        int limit = 4096;
        while (!queue.isEmpty() && limit-- > 0) {
            BlockPos p = queue.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos np = p.relative(d);
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCore120 core) { out.add(core); queue.add(np); }
                else if (world.getBlockState(np).getBlock() instanceof DataCableBlock120) queue.add(np);
            }
        }
        return out;
    }

    /** 安全查找：只查已加载区块（getBlockEntity 在服务端会强制加载区块，缓存位置解引用时绝不能裸用）。 */
    public static StorageCore120 loadedCoreAt(net.minecraft.world.level.Level world, BlockPos p) {
        if (!world.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
        return world.getBlockEntity(p) instanceof StorageCore120 core ? core : null;
    }

    public int tier() { return tier; }
    public int maxTypes() { int p = typesPerTier(); return p <= 0 ? Integer.MAX_VALUE : p * tier; }

    // ===== m295 精确账本内存索引（列表仍是唯一权威与落盘格式，旁挂 transient 键→下标索引）=====
    // 查找 O(1)；追加 O(1)；删除 O(n) 但只平移整数下标；事务回滚重放/NBT 读回直接置脏懒重建。
    private transient HashMap<TagStackKey, Integer> exactIdx; // null=脏

    private int exactIndexOf(ItemStack probe) {
        var m = exactIdx;
        if (m == null) {
            m = new HashMap<>();
            for (int i = 0; i < exactTpl.size(); i++) m.put(TagStackKey.of(exactTpl.get(i)), i);
            exactIdx = m;
        }
        Integer i = m.get(TagStackKey.of(probe));
        return i == null ? -1 : i;
    }

    private void exactIdxAppended() { // append 之后调（新条目下标=size-1）
        if (exactIdx != null) exactIdx.put(TagStackKey.of(exactTpl.get(exactTpl.size() - 1)), exactTpl.size() - 1);
    }

    private void exactIdxRemoved(int i, ItemStack tpl) { // remove(i) 之后调：删键+平移
        var m = exactIdx;
        if (m == null) return;
        m.remove(TagStackKey.of(tpl));
        for (var e : m.entrySet()) if (e.getValue() > i) e.setValue(e.getValue() - 1);
    }

    public int usedTypes() { return store.size() + exactTpl.size(); } // m130：精确条目同占类型额度

    /** m293 插入闸口径：玩法额度与绝对安全上限取小（common 配置同源）。≤0=关闸。 */
    private int typeGate() {
        int hard = com.sdzjz.config.SdzjzConfig.get().absoluteStorageTypeSafetyLimit;
        int play = maxTypes();
        return hard > 0 ? (int) Math.min((long) play, (long) hard) : play;
    }

    public void upgrade() { tier++; setChanged(); }

    /** m466（C2-⑤b）：普通物品类型余量先验——耗料机"先扣料后入仓"，入仓才发现类型满=白耗料，
     *  故扣料前用本口先验（与 deposit 的 m293 闸同一判式；带 tag 走精确账本不在本口）。 */
    boolean acceptsPlainType(String id) { return store.containsKey(id) || usedTypes() < typeGate(); }

    /** m273：非负计数饱和加法——溢出封顶 Long.MAX_VALUE（与蓝本同式同注：符号溢出检测）。 */
    public static long satAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    // ===== m80c 经验库（NBT 布局同源随迁；面板消费随 P-C，先到先有防键漂移）=====
    private long xpBank = 0;
    public long xpBank() { return xpBank; }
    public void xpAdd(long points) { if (points > 0) { xpBank = satAdd(xpBank, points); setChanged(); } }
    public long xpTake(long max) {
        long t = Math.min(xpBank, Math.max(0, max));
        xpBank -= t;
        if (t > 0) setChanged();
        return t;
    }

    public long count(String id) {
        Long v = store.get(id);
        return v == null ? 0L : v;
    }

    /** 存入。带 tag 的物品自动分流进精确账本（1.20.1 分流口径=hasTag，见类注①）；拒收时栈原样保留。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (stack.hasTag()) { depositExact(stack); return; }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!store.containsKey(id) && usedTypes() >= typeGate()) return; // m293 安全硬顶同闸
        store.merge(id, (long) stack.getCount(), StorageCore120::satAdd); // m273 饱和加法
        storeRev++; // m218
        stack.setCount(0);
        setChanged();
    }

    /** m130：精确存入——按「物品+tag」找同款条目并账；新类型受同一类型上限（拒收时栈原样保留）。 */
    public void depositExact(ItemStack stack) {
        if (stack.isEmpty()) return;
        int hit = exactIndexOf(stack); // m295 索引直查（等价逐条 isSameItemSameTags 扫描）
        if (hit >= 0) {
            exactN.set(hit, satAdd(exactN.get(hit), stack.getCount())); // m273 饱和加法
            exactRev++; // m322
            stack.setCount(0);
            setChanged();
            return;
        }
        if (usedTypes() >= typeGate()) return; // m293
        exactTpl.add(stack.copyWithCount(1));
        exactN.add((long) stack.getCount());
        exactIdxAppended(); // m295
        exactRev++; // m322
        stack.setCount(0);
        setChanged();
    }

    /** m130：精确取出——按「物品+tag」匹配模板，返回实际取出数量。 */
    public int withdrawExact(ItemStack template, int amount) {
        if (template == null || template.isEmpty() || amount <= 0) return 0;
        int i = exactIndexOf(template); // m295 索引直查
        if (i >= 0) {
            long have = exactN.get(i);
            int take = (int) Math.min((long) amount, have);
            long left = have - take;
            if (left <= 0) { ItemStack t = exactTpl.get(i); exactTpl.remove(i); exactN.remove(i); exactIdxRemoved(i, t); }
            else exactN.set(i, left);
            if (take > 0) { exactRev++; setChanged(); } // m322
            return take;
        }
        return 0;
    }

    /** m130：精确账本视图（模板 count 恒为 1，计数走 exactCount）。 */
    public List<ItemStack> exactTemplates() { return exactTpl; }
    public long exactCount(int i) { return (i >= 0 && i < exactN.size()) ? exactN.get(i) : 0L; }

    public int withdraw(String id, int amount) {
        Long have = store.get(id);
        if (have == null || amount <= 0) return 0;
        int take = (int) Math.min((long) amount, have);
        long left = have - take;
        if (left <= 0) store.remove(id); else store.put(id, left);
        storeRev++; // m218
        setChanged();
        return take;
    }

    public Map<String, Long> storeView() { return store; }

    // m218 账本修订号：store 每次变更 +1（单调只增，跨核心求和作指纹）。面板消费随 P-C，
    // 先随账本迁——变更点挂钩与蓝本逐位对齐，缺它将来面板缓存要回头补挂钩（易漏）。
    private long storeRev;
    public long storeRev() { return storeRev; }

    // m322 精确账本修订号：口径同 storeRev；回滚/读档宁可多记（多失效=白重建一次，无害）。
    private long exactRev;
    public long exactRev() { return exactRev; }

    // ===== m161c 跨模组直连：Fabric Transfer API（0.92 同包同形，m440 清单④）=====
    // Create（Fabric 移植）/管道模组怼在存储核心任意面即可存取——你要的机械动力对接的物理基础，
    // 传送带实机验收随刀③（m444）。事务安全=m278 增量 undo 日志；markDirty 推迟 onFinalCommit；
    // 管道惯用 Long.MAX_VALUE 试探性 insert，累加前先钳余量——三条语义与蓝本逐位对齐。
    private FabricLedger120 fabricLedger;

    public Storage<ItemVariant> fabricStorage() {
        if (fabricLedger == null) fabricLedger = new FabricLedger120();
        return fabricLedger;
    }

    public class FabricLedger120 extends SnapshotParticipant<Integer> implements Storage<ItemVariant> {

        // m278 增量事务日志：快照=日志位点(int)，回滚=从尾到位点逆序重放 undo（逆序保证精确账本
        // add/remove 的按下标前像恢复正确）。嵌套事务天然支持：每层快照各记自己的位点。
        // 日志不变量：事务外恒为空（最外层 commit 走 onFinalCommit 清空 / 最外层 abort 截断回位点 0）。
        private final ArrayList<Runnable> undoJournal = new ArrayList<>();

        @Override protected Integer createSnapshot() { return undoJournal.size(); }

        @Override protected void readSnapshot(Integer pos) {
            for (int i = undoJournal.size() - 1; i >= pos; i--) undoJournal.get(i).run();
            undoJournal.subList(pos, undoJournal.size()).clear();
            storeRev++; // m218（回滚也是变更）
            exactRev++; // m322：undo 可能碰过精确账本——宁可多记
        }

        @Override protected void onFinalCommit() { undoJournal.clear(); setChanged(); }

        @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (!one.hasTag()) { // 与 deposit 同一分流：无 tag 走普通账本（1.20.1 口径，类注①）
                String id = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
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
            int hit = exactIndexOf(one); // m295 索引直查（带 tag 走精确账本，m130 同款口径）
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
            exactTpl.add(one); // toStack(1) 即模板规格（count=1，tag 原样）
            exactN.add(maxAmount);
            exactIdxAppended(); // m295
            exactRev++; // m322
            return maxAmount;
        }

        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (!one.hasTag()) {
                String id = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
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
            for (String id : store.keySet()) { // m350 撤键拷贝口径同蓝本：建表期 store 零突变
                ResourceLocation rl = ResourceLocation.tryParse(id); // 1.20.1 无静态 parse——tryParse+判空对位（id 出自 getKey 恒合法，防御读档脏串）
                if (rl == null) continue;
                Item it = BuiltInRegistries.ITEM.get(rl);
                ItemVariant v = ItemVariant.of(it);
                if (v.isBlank()) continue; // 已卸载物品条目跳过（缺失 id 落回 air 即 blank）
                views.add(new View(v, id));
            }
            for (ItemStack tpl : new ArrayList<>(exactTpl)) views.add(new View(ItemVariant.of(tpl), null));
            return views.iterator();
        }

        /** 游标视图：金额按键实时查（迭代途中被别的事务抽走也读不到脏值）；抽取转正门统一走外层 extract。 */
        private class View implements StorageView<ItemVariant> {
            private final ItemVariant variant;
            private final String plainId; // 非 null=普通账本键；null=精确账本按模板匹配

            View(ItemVariant variant, String plainId) { this.variant = variant; this.plainId = plainId; }

            @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
                if (!variant.equals(resource)) return 0;
                return FabricLedger120.this.extract(resource, maxAmount, tx);
            }

            @Override public boolean isResourceBlank() { return variant.isBlank(); }
            @Override public ItemVariant getResource() { return variant; }

            @Override public long getAmount() {
                if (plainId != null) return store.getOrDefault(plainId, 0L);
                ItemStack one = variant.toStack(1);
                int i = exactIndexOf(one); // m295 索引直查
                return i >= 0 ? exactN.get(i) : 0;
            }

            @Override public long getCapacity() { return Long.MAX_VALUE; }
        }
    }

    // ===== NBT：1.20.1 签名（m440 清单②）——saveAdditional(CompoundTag)/load(CompoundTag) 无 Lookup 参；
    // ItemStack 走 save(new CompoundTag())/ItemStack.of(tag)。键布局与蓝本逐字同源（类注②）。=====
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("tier", tier);
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : store.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey());
            c.putLong("n", e.getValue());
            list.add(c);
        }
        nbt.put("store", list);
        ListTag ex = new ListTag(); // m130：精确账本持久化（模板 save + long 计数）
        for (int i = 0; i < exactTpl.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.put("item", exactTpl.get(i).save(new CompoundTag()));
            c.putLong("n", exactN.get(i));
            ex.add(c);
        }
        nbt.put("exact", ex);
        nbt.putLong("xpBank", xpBank);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        tier = Math.max(1, nbt.getInt("tier"));
        xpBank = Math.max(0, nbt.getLong("xpBank"));
        store.clear();
        ListTag list = nbt.getList("store", Tag.TAG_COMPOUND);
        int dropped = 0; // m273：账本读入校验——空id/非正计数=非法条目（写路径 left<=0 即 remove，零值从不合法落盘）
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String id = c.getString("id");
            long n = c.getLong("n");
            if (!id.isEmpty() && n > 0) store.put(id, n); else dropped++;
        }
        if (dropped > 0) LOGGER.warn("存储核心 {} 账本读入丢弃 {} 条非法条目（空id或非正计数）", worldPosition, dropped);
        storeRev++; // m218（NBT 读回=整本换血，记一次）
        exactRev++; // m322：下方精确账本同被整本换血
        exactIdx = null; // m295 读档置脏（懒重建）
        exactTpl.clear(); // m130：读回精确账本（解析失败/物品已卸载的条目静默跳过，不炸档）
        exactN.clear();
        ListTag ex = nbt.getList("exact", Tag.TAG_COMPOUND);
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag c = ex.getCompound(i);
            ItemStack t = ItemStack.of(c.getCompound("item")); // 1.20.1 对位 parse(lookup,…).orElse(EMPTY)——of 失败即返回 EMPTY
            long n = c.getLong("n");
            if (!t.isEmpty() && n > 0) { exactTpl.add(t.copyWithCount(1)); exactN.add(n); }
        }
    }
}
