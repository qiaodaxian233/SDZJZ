package com.sdzjz.screen;

import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.item.CompressedPackItem;
import com.sdzjz.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * m523（SB2b）：{@link SuperBenchScreenHandler.Host} 的 1.21 世代实现——十口全是合一前 handler 里的**原表达式原句照搬**
 * （m180 家法：方法体一字不改，回归风险按构造为零）。版本差（{@code ModItems} 注册表 / {@code CompressedPackItem} /
 * {@code CaptureCageItem} 三个主线专属类，m522b 作者 1.20.1 构建红的根因）自此钉死在本文件，handler 整文件可挂 1.20.1；
 * 1.20.1 本世代无压缩材料包无抓物笼（m521 缺口表 21 件非机器物品之列）→ RetroBootstrap 装空宿主 {@code new Host() {}} = 全关。
 * 装配点：Sdzjz.onInitialize 首段（ItemData.install 同位）。同值判官：SdzjzGameTests.super_bench_host_pack_roundtrip。
 */
public final class LegacySuperBenchHost implements SuperBenchScreenHandler.Host {

    @Override public boolean packsAvailable() { return true; }

    @Override public boolean isPack(ItemStack s) { return s.getItem() instanceof CompressedPackItem; } // 原句照搬

    @Override public Item tier1() { return ModItems.COMPRESSED_PACK; }

    @Override public Item tier2() { return ModItems.SUPER_COMPRESSED_PACK; }

    @Override public String packInnerId(ItemStack s) { return CompressedPackItem.innerId(s); }

    @Override public long packRawCount(ItemStack s) { return CompressedPackItem.rawCount(s); }

    @Override public int packRatio(ItemStack s) { return ((CompressedPackItem) s.getItem()).ratio; } // 原 `instanceof CompressedPackItem pk` → pk.ratio

    @Override public ItemStack packOf(Item packItem, String innerId, int count) { return CompressedPackItem.of(packItem, innerId, count); }

    @Override public String cagedType(ItemStack s) {
        return s.getItem() instanceof CaptureCageItem ? CaptureCageItem.cagedType(s) : null; // 原句 `instanceof CaptureCageItem && mob.equals(cagedType(s))` 的判定拆成两段
    }
}
