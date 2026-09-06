package com.sdzjz.neoforge;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.storage.StorageLedger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** m539（F1d-3a）存储核心的 NeoForge **提供侧**适配器（对位 Fabric 的 {@code loader/FabricStorageAdapter}）——把双账本暴露成 {@link IItemHandler}
 *  给漏斗管道类模组。业务判断（分流/类型闸/undo 前像/索引）一律转调两代共用的 {@link StorageLedger#ftaInsert}/{@link StorageLedger#ftaExtract}/
 *  {@link StorageLedger#ftaAmount}（m503 刻意不含任何加载器类型的三口），本类只做**槽位视图 + 模拟/回滚 + 类型转换**。
 *  <p><b>有界槽位视图</b>（评估报告 §14.1：别把内部总量塞进一个栈、别每次查询遍历全库）：普通账每个 id 一槽 + 精确账每个模板一槽 + **末尾一个空槽**（收新件）；
 *  槽表按账本修订号（storeRev/exactRev）懒刷新；{@code getStackInSlot} 显示量钳到堆叠上限（真实总量在账本里，抽取按槽多次取即可）。
 *  <p><b>simulate</b>：IItemHandler 没有事务——模拟=真做一遍再把 undo 日志**逆序**回放（与 Fabric 壳 readSnapshot 同一律：逆序才能让精确账本按下标前像恢复正确），
 *  回滚后两修订号各 +1（"回滚也是变更"，m218/m322 口径）。真做后 {@code be.setChanged()}。
 *  <p>实例由 BE 的不透明槽 {@code transferAdapter(Supplier)} 缓存（m534：同一 BE 恒返同一实例）；注册见 NeoForgeEntry 的 RegisterCapabilitiesEvent。 */
public final class NeoForgeStorageAdapter implements IItemHandler {
    private final StorageCoreBlockEntity be;
    private final StorageLedger ledger;
    private long seenStoreRev = Long.MIN_VALUE, seenExactRev = Long.MIN_VALUE;
    private final List<ItemStack> ones = new ArrayList<>(); // 每槽一件模板栈（普通账=裸 Item 栈；精确账=模板拷贝）
    private final List<String> ids = new ArrayList<>();     // 与 ones 同序：普通账=注册 id；精确账=null（ftaAmount 按模板匹配）

    public NeoForgeStorageAdapter(StorageCoreBlockEntity be) { this.be = be; this.ledger = be.ledger(); }

    private void refresh() {
        if (ledger.storeRev() == seenStoreRev && ledger.exactRev() == seenExactRev) return;
        ones.clear(); ids.clear();
        for (String id : ledger.storeView().keySet()) { // 坏档脏 id 静默跳过（FabricStorageAdapter.iterator 同款防御）
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;
            Item it = BuiltInRegistries.ITEM.get(rl);
            if (it == Items.AIR) continue;
            ones.add(new ItemStack(it)); ids.add(id);
        }
        for (ItemStack tpl : ledger.exactTemplates()) { ones.add(tpl.copyWithCount(1)); ids.add(null); }
        seenStoreRev = ledger.storeRev(); seenExactRev = ledger.exactRev();
    }

    private static void rollback(List<Runnable> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
    }

    @Override public int getSlots() { refresh(); return ones.size() + 1; }

    @Override
    public ItemStack getStackInSlot(int slot) {
        refresh();
        if (slot < 0 || slot >= ones.size()) return ItemStack.EMPTY;
        ItemStack one = ones.get(slot);
        long n = ledger.ftaAmount(ids.get(slot), one);
        return n <= 0 ? ItemStack.EMPTY : one.copyWithCount((int) Math.min(n, one.getMaxStackSize()));
    }

    /** 任意槽都收（内部按分流判据入普通/精确账，槽号只是视图坐标）；返回余量。 */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        List<Runnable> undo = new ArrayList<>();
        long ins = ledger.ftaInsert(stack.copyWithCount(1), stack.getCount(), undo, () -> { });
        if (simulate) { rollback(undo); ledger.bumpStoreRev(); ledger.bumpExactRev(); }
        else if (ins > 0) be.setChanged();
        return ins >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount((int) (stack.getCount() - ins));
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        refresh();
        if (amount <= 0 || slot < 0 || slot >= ones.size()) return ItemStack.EMPTY;
        ItemStack one = ones.get(slot);
        List<Runnable> undo = new ArrayList<>();
        long got = ledger.ftaExtract(one.copyWithCount(1), Math.min(amount, one.getMaxStackSize()), undo, () -> { });
        if (simulate) { rollback(undo); ledger.bumpStoreRev(); ledger.bumpExactRev(); }
        else if (got > 0) be.setChanged();
        return got <= 0 ? ItemStack.EMPTY : one.copyWithCount((int) got);
    }

    @Override public int getSlotLimit(int slot) { return 64; } // 视图口径一栈；大额由调用方分批（NeoForgeXfer.insert 就是这么做的）

    @Override public boolean isItemValid(int slot, ItemStack stack) { return true; } // 收不收由 ftaInsert 的类型闸决定，不在这儿预判
}
