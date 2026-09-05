package com.sdzjz.retro;

import com.sdzjz.item.CompressedPackItem;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** m529（N2a）：{@link SuperBenchScreenHandler.Host} 的 **1.20.1 世代实现**——主线 {@code LegacySuperBenchHost}（m523）逐句对位，
 *  注册表换 {@link RetroItems}。压缩包八口本刀真实现（m523 空宿主退役）；抓物笼口随 N2b（{@code CaptureCageItem} 上挂）接，本刀仍回 null=无笼。
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
    // cagedType：N2b 抓物笼上挂后改成 `s.getItem() instanceof CaptureCageItem ? CaptureCageItem.cagedType(s) : null`（主线原句）；本刀沿用接口默认 null
}
