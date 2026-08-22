package com.sdzjz.retro;

import com.sdzjz.platform.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * m439 旧世代（1.20.1）bootstrap 入口——m436 方案 P-B。m443 起带真业务：存储核心账本
 * （StorageCore120 双账本+FTA 对外视图）。错误归 Retro 不污染 Legacy/Modern（m370 口径同款）。
 */
public final class RetroBootstrap implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("sdzjz");

    @Override
    public void onInitialize() {
        // 代际引导（m371 对位 ModernBootstrap）：configDir 第一行（m365 规矩，早于任何 SdzjzConfig.get()）
        // ——m443 起账本类型闸消费 common 配置，缺这行=首次 typeGate 即硬失败。
        Platform.initConfigDir(FabricLoader.getInstance().getConfigDir());
        RetroBlocks.register(); // m441 刀①：注册骨架（存储核心+数据线+BE 类型+创造栏）

        // m443（蓝本=Legacy m161c，Sdzjz.onInitialize 同位）：存储核心双账本挂 Fabric Transfer API——
        // Create（Fabric 移植）等一切走 fabric-transfer-api 的管道怼在核心任意面即可存取；
        // 传送带↔数据线互通随刀③（m444）。提供侧天生属加载器层，不抽口（m404 定性同款）。
        ItemStorage.SIDED.registerForBlockEntity((be, direction) -> be.fabricStorage(), RetroBlocks.STORAGE_CORE_BE);

        LOGGER.info("[sdzjz] 1.20.1 旧世代 bootstrap 在岗：Common 层已挂载（{}/{}/{} 可达）；存储核心账本+FTA 直连已上线（m443），数据线双向拍与 Create 验收随 m444",
                com.sdzjz.machine.CraftPlanner.class.getSimpleName(),
                com.sdzjz.machine.CoreScheduler.class.getSimpleName(),
                com.sdzjz.machine.MobDrops.class.getSimpleName());
    }
}
