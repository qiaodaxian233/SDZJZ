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
import java.util.Iterator;
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

    // ===== m161c 跨模组直连：Fabric Transfer API =====
    // m503（真移植 B5）：业务判断整段迁 xplat/storage/StorageLedger 两代共用（原
    // FabricLedger120.insert/extract 内脏搬去 StorageLedger.ftaInsert/ftaExtract）。**本类只剩薄壳**：
    // FTA 生命周期钩子 + ItemVariant↔ItemStack 类型转换 + iterator 物化——这层皮不能再往下沉
    // （m406 分层硬闸：xplat 见 MC、不见加载器，FTA 类型本身就是加载器符号，详见 StorageLedger
    // 里三个 fta 前缀方法的类注）。分流判据统一走 ItemData.has 世代口（本世代实现即原
    // !one.hasTag()，逐位不变）；索引维护恢复走增量的 exactIdxAppended/exactIdxRemoved
    // （原此处退化成 markIndexDirty() 全量置脏重建——语义结果等价，现在 StorageLedger.ftaInsert/
    // ftaExtract 内部天然能访问这两个 private 方法，恢复了应有的 O(1)）。
    private FabricLedger120 fabricLedger;

    public Storage<ItemVariant> fabricStorage() {
        if (fabricLedger == null) fabricLedger = new FabricLedger120();
        return fabricLedger;
    }

    public class FabricLedger120 extends SnapshotParticipant<Integer> implements Storage<ItemVariant> {

        // m278 增量事务日志：本类只管这份日志的生命周期（快照=位点/回滚=逆序重放/commit 清空），
        // 日志*内容*（撤销动作）由 StorageLedger.ftaInsert/ftaExtract 追加，蓝本同款注释见主线。
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
            return ledger.ftaInsert(resource.toStack(1), maxAmount, undoJournal, () -> updateSnapshots(tx));
        }

        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (resource.isBlank() || maxAmount <= 0) return 0;
            return ledger.ftaExtract(resource.toStack(1), maxAmount, undoJournal, () -> updateSnapshots(tx));
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
            @Override public long getAmount() { return ledger.ftaAmount(plainId, variant.toStack(1)); }
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
