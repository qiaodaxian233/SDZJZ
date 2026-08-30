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
 * 组件哈希退 tag 哈希），身份键={@link com.sdzjz.storage.StackKey}（m478 起两代共用一份，世代差走 Kind 口）；②NBT 键与 Legacy 同名同布局
 * （tier/store/exact/xpBank，store 条目 id/n、exact 条目 item/n）——1.20.5 原版 DFU 升档时把物品 tag
 * 自动收进 custom_data（m436 红利），存档升 1.21.1 零迁移代码；③CORES 登记表/桶索引/BFS 均属机器与
 * 线缆消费面，随刀③（m444）与 P-C 移植，本类不带（蓝本对应段落=范围外，非漏抄）。
 *
 * <p>xplat 未挂本世代 → 不实现 StorageAccess 薄口、不消费 ItemData 门面（那两件建在 1.20.5+ 类型上），
 * 读写直用原版 hasTag/getTag——m353"读→view 写→copy"铁律的 tag 版：模板 tag 只读绝不改。
 */
public final class StorageCore120 extends BlockEntity implements com.sdzjz.machine.StorageAccess,
        com.sdzjz.machine.StorageLedgerProbe { // m464 挂主线契约（deposit/withdraw/count/storeView 四口现成）；m480 加挂跨代行为契约探针（十三口，零方法体改动）

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("sdzjz");

    /** m98：类型上限走配置（common 同源，不抄数字）——storageTypesPerTier 0=无限(默认)，>0=每级该数。 */
    private static int typesPerTier() { return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier; }
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

    /** m444 普通类型可收判定（供路由侧问"这仓还收不收这种"）。 */
    boolean acceptsPlainType(String id) { return ledger.storeView().containsKey(id) || usedTypes() < ledger.typeGate(); }

    // ===== m485（真移植·作者点名「是移植不是重做，把以前写的 1.20.1 直接移除」）=====
    // 原来这里是 181 行**按主线重写的账本**（普通账/精确账/哈希索引/类型闸/饱和加法/修订号/经验库）。
    // 整段删除，改用与主线**同一份** com.sdzjz.storage.StorageLedger——那份代码逐句来自主线
    // StorageCoreBlockEntity，本世代不再有自己的账本实现。行为由 m480 的十类跨代契约压着。
    private final com.sdzjz.storage.StorageLedger ledger = new com.sdzjz.storage.StorageLedger(this::setChanged);

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
    public long exactRev() { return ledger.exactRev(); }

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
            ledger.bumpStoreRev(); // m218（回滚也是变更）
            ledger.bumpExactRev(); // m322：undo 可能碰过精确账本——宁可多记
        }

        @Override protected void onFinalCommit() { undoJournal.clear(); setChanged(); }

        @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (!one.hasTag()) { // 与 deposit 同一分流：无 tag 走普通账本（1.20.1 口径，类注①）
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
            int hit = ledger.exactIndexOf(one); // m295 索引直查（带 tag 走精确账本，m130 同款口径）
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
            ledger.exactTemplates().add(one); // toStack(1) 即模板规格（count=1，tag 原样）
            ledger.exactCounts().add(maxAmount);
            ledger.markIndexDirty(); // m295
            ledger.bumpExactRev(); // m322
            return maxAmount;
        }

        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            ItemStack one = resource.toStack(1);
            if (!one.hasTag()) {
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
            int i = ledger.exactIndexOf(one); // m295 索引直查
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
                    ledger.markIndexDirty(); //i, ptpl); // m295
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
            for (String id : ledger.storeView().keySet()) { // m350 撤键拷贝口径同蓝本：建表期 store 零突变
                ResourceLocation rl = ResourceLocation.tryParse(id); // 1.20.1 无静态 parse——tryParse+判空对位（id 出自 getKey 恒合法，防御读档脏串）
                if (rl == null) continue;
                Item it = BuiltInRegistries.ITEM.get(rl);
                ItemVariant v = ItemVariant.of(it);
                if (v.isBlank()) continue; // 已卸载物品条目跳过（缺失 id 落回 air 即 blank）
                views.add(new View(v, id));
            }
            for (ItemStack tpl : new ArrayList<>(ledger.exactTemplates())) views.add(new View(ItemVariant.of(tpl), null));
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
                if (plainId != null) return ledger.storeView().getOrDefault(plainId, 0L);
                ItemStack one = variant.toStack(1);
                int i = ledger.exactIndexOf(one); // m295 索引直查
                return i >= 0 ? ledger.exactCounts().get(i) : 0;
            }

            @Override public long getCapacity() { return Long.MAX_VALUE; }
        }
    }

    // ===== NBT：1.20.1 签名（m440 清单②）——saveAdditional(CompoundTag)/load(CompoundTag) 无 Lookup 参；
    // ItemStack 走 save(new CompoundTag())/ItemStack.of(tag)。键布局与蓝本逐字同源（类注②）。=====
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("tier", ledger.tierRaw());
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : ledger.storeView().entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey());
            c.putLong("n", e.getValue());
            list.add(c);
        }
        nbt.put("store", list);
        ListTag ex = new ListTag(); // m130：精确账本持久化（模板 save + long 计数）
        for (int i = 0; i < ledger.exactTemplates().size(); i++) {
            CompoundTag c = new CompoundTag();
            c.put("item", ledger.exactTemplates().get(i).save(new CompoundTag()));
            c.putLong("n", ledger.exactCounts().get(i));
            ex.add(c);
        }
        nbt.put("exact", ex);
        nbt.putLong("xpBank", ledger.xpBank());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        ledger.setTier(nbt.getInt("tier"));
        ledger.setXpBank(Math.max(0, nbt.getLong("xpBank")));
        ledger.storeView().clear();
        ListTag list = nbt.getList("store", Tag.TAG_COMPOUND);
        int dropped = 0; // m273：账本读入校验——空id/非正计数=非法条目（写路径 left<=0 即 remove，零值从不合法落盘）
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String id = c.getString("id");
            long n = c.getLong("n");
            if (!id.isEmpty() && n > 0) ledger.storeView().put(id, n); else dropped++;
        }
        if (dropped > 0) LOGGER.warn("存储核心 {} 账本读入丢弃 {} 条非法条目（空id或非正计数）", worldPosition, dropped);
        ledger.bumpStoreRev(); // m218（NBT 读回=整本换血，记一次）
        ledger.bumpExactRev(); // m322：下方精确账本同被整本换血
        ledger.markIndexDirty(); // m295 读档置脏（懒重建）
        ledger.exactTemplates().clear(); // m130：读回精确账本（解析失败/物品已卸载的条目静默跳过，不炸档）
        ledger.exactCounts().clear();
        ListTag ex = nbt.getList("exact", Tag.TAG_COMPOUND);
        for (int i = 0; i < ex.size(); i++) {
            CompoundTag c = ex.getCompound(i);
            ItemStack t = ItemStack.of(c.getCompound("item")); // 1.20.1 对位 parse(lookup,…).orElse(EMPTY)——of 失败即返回 EMPTY
            long n = c.getLong("n");
            if (!t.isEmpty() && n > 0) { ledger.exactTemplates().add(t.copyWithCount(1)); ledger.exactCounts().add(n); }
        }
    }
}
