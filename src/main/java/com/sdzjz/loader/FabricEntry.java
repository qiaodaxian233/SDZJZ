package com.sdzjz.loader;

import net.fabricmc.api.ModInitializer;

/** m531（F1a）**Fabric 1.21.1 加载器入口**：原 {@code Sdzjz.onInitialize()} 头四句（加载器口安装）+ 尾部提供侧传输注册原文挪到这儿，
 *  中间业务体留 {@code Sdzjz.init()}。NeoForge 对位 {@code NeoForgeEntry}（F1d）：装 NeoForge 版四口后同样调 {@code Sdzjz.init()}——
 *  一份业务、多份入口，业务侧一行不动（m404 原注兑现）。 */
public final class FabricEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        com.sdzjz.net.Net.install(new com.sdzjz.loader.FabricNet()); // m433 平台口安装：必须早于下方一切 payload 注册/接收器挂接
        com.sdzjz.storage.Xfer.install(new com.sdzjz.loader.FabricXfer()); // m434 平台口安装
        com.sdzjz.loader.Env.install(new com.sdzjz.loader.FabricEnv()); // m435 平台口安装：必须早于 initConfigDir
        com.sdzjz.loader.Hooks.install(new com.sdzjz.loader.FabricHooks()); // m435 平台口安装
        com.sdzjz.loader.Menus.install(new com.sdzjz.loader.FabricMenus()); // m532（F1b）菜单数据口：必须早于 Sdzjz.init（ModScreenHandlers 类初始化在里面）

        com.sdzjz.Sdzjz.init(); // 世代口安装 + 业务初始化（内含 ModBlockEntities.init，下行提供侧注册依赖它）

        // m161c 跨模组直连：存储核心双账本挂上 Fabric Transfer API——Create/Modern Industrialization/
        // Tech Reborn/AE2 等一切走 fabric-transfer-api 的管道怼在存储核心任意面即可存取。
        // 注意原版漏斗不走此 API（漏斗只认 Inventory 接口），漏斗对接另开里程碑（见 DEVLOG m161）。
        // m404 提供侧（我们把自家账本暴露给别的模组）：天生属加载器层，不抽口——换 NeoForge 时这里换成能力注册，业务侧一行不动。
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> be.fabricStorage(), com.sdzjz.registry.ModBlockEntities.STORAGE_CORE_BE);
    }
}
