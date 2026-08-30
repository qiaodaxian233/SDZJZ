package com.sdzjz.retro;

import com.sdzjz.storage.Xfer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** m502：{@link Xfer.Impl} 的 1.20.1 实现——**FabricXfer 原文照搬**（m434 注释早写明
 *  "1.20.1 格照此同款，该版 fabric-api 同有此 API"，本刀兑现）。五口语义与 m444 内联版逐位一致：
 *  find=ItemStorage.SIDED.find / canInsert=supportsInsertion / canExtract=supportsExtraction /
 *  insert=单笔事务提交（变体身份=物品+tag，本世代口径）/ move=StorageUtil.move 谓词从
 *  ItemStack 适配到 ItemVariant（null=全收）。m440「本世代不设 Xfer 门面」是仿写路线的旧结论，
 *  真移植路线下这五口门面本身就是世代口（m483「不可挂记档要定期重量」同型），就此过期。 */
public final class RetroXfer implements Xfer.Impl {

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
