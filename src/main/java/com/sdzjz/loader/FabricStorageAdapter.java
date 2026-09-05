package com.sdzjz.loader;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.storage.StorageLedger;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** m533（F1c）存储核心的 Fabric Transfer API **提供侧**适配器（我们把自家双账本暴露给 Create/MI/TechReborn/AE2 等管道）。
 *  原 {@code StorageCoreBlockEntity.FabricLedger}（m161c 立、m278 增量事务日志、m503 业务下沉后只剩薄壳）整段搬来，
 *  详见类体首段注释。NeoForge 对位（F1d）：{@code NeoForgeStorageAdapter implements IItemHandler}，
 *  在 {@code RegisterCapabilitiesEvent} 里 {@code registerBlockEntity(Capabilities.ItemHandler.BLOCK, STORAGE_CORE_BE, (be, side) -> …of(be))}。 */
@SuppressWarnings("unchecked")
public final class FabricStorageAdapter extends SnapshotParticipant<Integer> implements Storage<ItemVariant> {

    // ===== m533（F1c）：从 StorageCoreBlockEntity 内部类 FabricLedger 整段搬出（m161c/m278/m503 原文，机械替换三类：
    // 类名 / setChanged()→be.setChanged() / FabricLedger.this→FabricStorageAdapter.this；其余一字未改）=====
    // 为什么搬：它 implements Storage<ItemVariant>、extends SnapshotParticipant——五个 FTA 符号住在业务 BE 里，
    // `src/` 就挂不进 NeoForge（第 13 闸 SRC_GLUE_PENDING 待拆表最后一件）。业务判断早在 m503 沉进
    // xplat/storage/StorageLedger.ftaInsert/ftaExtract/ftaAmount，本类只是 FTA 生命周期钩子 + 类型转换 + iterator 物化，
    // 天生加载器层（Xfer.java 类注「刻意留在原地的两处」之①，今日兑现）。
    // **同一 BE 恒返同一实例**：undo 日志住在本实例里，嵌套事务的快照位点靠它；实例缓存在 BE 的不透明槽
    // StorageCoreBlockEntity.transferAdapter(Supplier)——NeoForge 的 IItemHandler 适配器（F1d）用同一个槽。
    private final StorageCoreBlockEntity be;
    private final StorageLedger ledger;

    private FabricStorageAdapter(StorageCoreBlockEntity be) { this.be = be; this.ledger = be.ledger(); }

    /** 取该核心的 FTA 视图（惰性建、缓存在 BE 槽）。FabricEntry 的 ItemStorage.SIDED 提供侧与 Fabric 判官都走这里。 */
    public static Storage<ItemVariant> of(StorageCoreBlockEntity be) {
        return (Storage<ItemVariant>) be.transferAdapter(() -> new FabricStorageAdapter(be));
    }

    // m278 增量事务日志：快照=日志位点(int)，回滚=从尾到位点逆序重放 undo（逆序保证精确账本
    // add/remove 的按下标前像恢复正确）。嵌套事务天然支持：每层快照各记自己的位点。旧版整本
    // 浅拷 O(全账本) 每带写事务一次；管道模组每口每 tick 开事务，大仓库=纯拷贝+GC 风暴。现在
    // O(实际触碰条目)，典型管道操作=1 条。日志不变量：事务外恒为空（最外层 commit 走
    // onFinalCommit 清空 / 最外层 abort 截断回位点 0）——本类只管这份日志的生命周期，
    // 日志*内容*（撤销动作）由 StorageLedger.ftaInsert/ftaExtract 追加。
    private final ArrayList<Runnable> undoJournal = new ArrayList<>();

    @Override protected Integer createSnapshot() { return undoJournal.size(); }

    @Override protected void readSnapshot(Integer pos) {
        for (int i = undoJournal.size() - 1; i >= pos; i--) undoJournal.get(i).run();
        undoJournal.subList(pos, undoJournal.size()).clear();
        ledger.bumpStoreRev(); // m218（回滚也是变更；口径同旧版=每次回滚记一次）
        ledger.bumpExactRev(); // m322：undo 可能碰过精确账本——宁可多记，白重建一次无害
    }

    @Override protected void onFinalCommit() { undoJournal.clear(); be.setChanged(); }

    @Override public long insert(ItemVariant resource, long maxAmount, TransactionContext tx) {
        if (resource.isBlank() || maxAmount <= 0) return 0;
        // beforeMutate=updateSnapshots(tx)：必须恰在 StorageLedger 确定"真的要改"那一刻触发，
        // 早/晚都会破坏事务快照的正确性，所以设计成回调而不是本方法自己猜时机（防长整溢出：
        // 管道惯用 Long.MAX_VALUE 试探性 insert，累加前先钳余量——这条语义在 ftaInsert 内）。
        return ledger.ftaInsert(resource.toStack(1), maxAmount, undoJournal, () -> updateSnapshots(tx));
    }

    @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
        if (resource.isBlank() || maxAmount <= 0) return 0;
        return ledger.ftaExtract(resource.toStack(1), maxAmount, undoJournal, () -> updateSnapshots(tx));
    }

    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>(ledger.storeView().size() + ledger.exactTemplates().size());
        for (String id : ledger.storeView().keySet()) { // m350 撤键拷贝：views 表在此建完才外泄，外部 extract 只动 views 走 View 懒读，建表期 store 零突变
            ResourceLocation rl = ResourceLocation.tryParse(id); // 防御：坏档脏 id 静默跳过不炸迭代（本世代原写法，两代合一借光，m502 extractAll 同款教训）
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
        private final ItemVariant v;
        private final String id; // 非 null=普通账本键；null=精确账本按模板匹配

        View(ItemVariant v, String id) { this.v = v; this.id = id; }

        @Override public long extract(ItemVariant resource, long maxAmount, TransactionContext tx) {
            if (!v.equals(resource)) return 0;
            return FabricStorageAdapter.this.extract(resource, maxAmount, tx);
        }

        @Override public boolean isResourceBlank() { return v.isBlank(); }
        @Override public ItemVariant getResource() { return v; }
        @Override public long getAmount() { return ledger.ftaAmount(id, v.toStack(1)); }
        @Override public long getCapacity() { return Long.MAX_VALUE; }
    }
}
