package com.sdzjz.retro;

import com.sdzjz.item.CompressedPackItem;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** m529（N2a）：{@link SuperBenchScreenHandler.Host} 的 **1.20.1 世代实现**——主线 {@code LegacySuperBenchHost}（m523）逐句对位，
 *  注册表换 {@link RetroItems}。压缩包八口 m529 真实现；抓物笼口 m530 接主线原句——十口全真，与主线同形。
 *  装配点：RetroBootstrap（ItemData.install 同位）。同值判官：RetroBenchTests.super_bench_compress_unpack_roundtrip。 */
public final class RetroSuperBenchHost implements SuperBenchScreenHandler.Host {
    @Override public boolean packsAvailable() { return true; }
    @Override public boolean isPack(ItemStack s) { return s.getItem() instanceof CompressedPackItem; }
    @Override public Item tier1() { return RetroItems.COMPRESSED_PACK; }
    @Override public Item tier2() { return RetroItems.SUPER_COMPRESSED_PACK; }
    @Override public String packInnerId(ItemStack s) { return CompressedPackItem.innerId(s); }
    @Override public long packRawCount(ItemStack s) { return CompressedPackItem.rawCount(s); }
    @Override public int packRatio(ItemStack s) { return ((CompressedPackItem) s.getItem()).ratio; }
    @Override public ItemStack packOf(Item packItem, String innerId, int count) { return CompressedPackItem.of(packItem, innerId, count); }
    @Override public String cagedType(ItemStack s) { // m530（N2b）：主线 LegacySuperBenchHost 原句
        return s.getItem() instanceof com.sdzjz.item.CaptureCageItem ? com.sdzjz.item.CaptureCageItem.cagedType(s) : null;
    }
}
