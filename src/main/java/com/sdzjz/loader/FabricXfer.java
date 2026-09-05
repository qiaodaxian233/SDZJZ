package com.sdzjz.loader;

import com.sdzjz.storage.Xfer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** m434：{@link Xfer.Impl} 的 Fabric 实现——m404 门面里的 Fabric 内脏原样搬来
 *  （find/canInsert/canExtract/insert 一行未改；move=原 moveToCore 体，唯一改动是
 *  dst 从 {@code core.fabricStorage()} 换成传入句柄按 insert 同款姿势强转；m533 起该视图由 {@link FabricStorageAdapter#of} 提供）。 */
public final class FabricXfer implements Xfer.Impl {

    @Override
    public Object find(Level world, BlockPos np, Direction side) {
        return net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(world, np, side);
    }

    @Override
    public boolean canInsert(Object handle) {
        return handle instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> s && s.supportsInsertion();
    }

    @Override
    public boolean canExtract(Object handle) {
        return handle instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> s && s.supportsExtraction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public long insert(Object handle, ItemStack template, boolean exact, long amount) {
        if (!(handle instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> raw) || amount <= 0) return 0;
        var target = (net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>) raw;
        var v = exact ? net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(template)
                : net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(template.getItem());
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long ins = target.insert(v, amount, tx);
            tx.commit();
            return ins;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public long move(Object from, Object to, java.util.function.Predicate<ItemStack> filter, long max) {
        if (!(from instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> rawFrom)
                || !(to instanceof net.fabricmc.fabric.api.transfer.v1.storage.Storage<?> rawTo) || max <= 0) return 0;
        var src = (net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>) rawFrom;
        var dst = (net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>) rawTo;
        java.util.function.Predicate<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> pred =
                filter == null ? v -> true : v -> filter.test(v.toStack());
        return net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil.move(src, dst, pred, max, null);
    }
}
