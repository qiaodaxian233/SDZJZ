package com.sdzjz.client;

import net.fabricmc.api.ClientModInitializer;

/** m531（F1a）**Fabric 1.21.1 客户端入口**：原 {@code SdzjzClient.onInitializeClient()} 里的 Fabric 专属句原文——ClientHooks 加载器口安装、
 *  卫星节点模型插件（ModelLoadingPlugin）、压缩包内建物品渲染器两句（BuiltinItemRendererRegistry）；其余原版 API 留 {@code SdzjzClient.init()}。
 *  NeoForge 对位 {@code NeoForgeClientEntry}（F1d）：ModelEvent / IClientItemExtensions。 */
public final class FabricClientEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.sdzjz.client.ClientHooks.install(new com.sdzjz.client.FabricClientHooks()); // m435 平台口安装
        com.sdzjz.client.SatelliteNodeModel.register(); // m151 卫星节点bbmodel自定义烘焙（Fabric ModelLoadingPlugin）
        com.sdzjz.SdzjzClient.init();
        // m243 压缩包动态图标：内容物模型缩0.8 + 档位边框叠层（模型 parent=builtin/entity 触发本渲染器）——Fabric 专属注册口
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.sdzjz.registry.ModItems.COMPRESSED_PACK,
                new com.sdzjz.client.CompressedPackRenderer(com.sdzjz.registry.ModItems.COMPRESSED_PACK_FRAME));
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK,
                new com.sdzjz.client.CompressedPackRenderer(com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK_FRAME));
    }
}
