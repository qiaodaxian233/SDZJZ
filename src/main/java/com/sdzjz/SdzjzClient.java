package com.sdzjz;

import com.sdzjz.client.DataPanelScreen;
import com.sdzjz.client.DataCableRenderer;
import com.sdzjz.client.StorageCoreRenderer;
import com.sdzjz.client.StructureCoreScreen;
import com.sdzjz.client.SuperBenchScreen;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public class SdzjzClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.sdzjz.client.ClientNet.install(new com.sdzjz.client.FabricClientNet()); // m433 平台口安装：必须早于下方一切客户端接收器挂接
        com.sdzjz.client.ClientHooks.install(new com.sdzjz.client.FabricClientHooks()); // m435 平台口安装
        com.sdzjz.client.SatelliteNodeModel.register(); // m151 卫星节点bbmodel自定义烘焙
        MenuScreens.register(ModScreenHandlers.STRUCTURE_CORE, StructureCoreScreen::new);
        MenuScreens.register(ModScreenHandlers.DATA_PANEL, DataPanelScreen::new);
        MenuScreens.register(ModScreenHandlers.TRADE_CENTER, com.sdzjz.client.TradeCenterScreen::new);
        MenuScreens.register(ModScreenHandlers.SUPER_BENCH, SuperBenchScreen::new);
        MenuScreens.register(ModScreenHandlers.EXTRACT_PORT, com.sdzjz.client.ExtractPortScreen::new); // m226 抽取口配置
        MenuScreens.register(ModScreenHandlers.PORTABLE_VAULT, com.sdzjz.client.PortableVaultScreen::new); // m312 随身仓库
        BlockEntityRendererRegistry.register(ModBlockEntities.STORAGE_CORE_BE, StorageCoreRenderer::new); // 存储核心动画
        BlockEntityRendererRegistry.register(ModBlockEntities.DATA_CABLE_BE, DataCableRenderer::new); // 数据线能量脉冲
        BlockEntityRendererRegistry.register(ModBlockEntities.WIRELESS_NODE_BE, com.sdzjz.client.WirelessNodeRenderer::new); // 无线节点信号波
        BlockEntityRendererRegistry.register(ModBlockEntities.SATELLITE_NODE_BE, com.sdzjz.client.SatelliteNodeRenderer::new); // m156 卫星扫描动画
        // m277 三块动画改原生贴图帧动画（.png.mcmeta，docs/tools_block_anim.py 生成）——m249/m250 全息 BER 三件套退役
        // m243 压缩包动态图标：内容物模型缩0.8 + 档位边框叠层（模型 parent=builtin/entity 触发本渲染器）
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.sdzjz.registry.ModItems.COMPRESSED_PACK,
                new com.sdzjz.client.CompressedPackRenderer(com.sdzjz.registry.ModItems.COMPRESSED_PACK_FRAME));
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK,
                new com.sdzjz.client.CompressedPackRenderer(com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK_FRAME));
        // m89：画布端点直发包 → 静态缓存（画布优先读缓存，BE 数据后备）
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.CanvasEndsPayload.ID,
                (payload, client) -> com.sdzjz.client.StructureCoreScreen.applyEndsPayload(payload));
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.StorageNodeHomePayload.ID, // m265 端点画布落位
                (payload, client) -> com.sdzjz.client.StructureCoreScreen.applyHomesPayload(payload));
        // m275：观众定向渲染快照 → 写回客户端 BE 渲染字段（画布屏 be() 读法零改动）
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.CanvasSnapshotPayload.ID,
                (payload, client) -> {
                    var w = client.level;
                    if (w != null && w.getBlockEntity(payload.pos()) instanceof com.sdzjz.block.StructureCoreBlockEntity be)
                        be.applyRenderSnapshot(payload.nbt(), w.registryAccess());
                });
        // m289：终端库存摘要 → 灌进正开着的终端 handler 并催书重算"可合成"
        com.sdzjz.client.ClientNet.onClient(com.sdzjz.net.TerminalStockPayload.ID,
                (payload, client) -> {
                    var pl = client.player;
                    if (pl != null && pl.containerMenu instanceof com.sdzjz.screen.DataPanelScreenHandler h
                            && h.containerId == payload.syncId()) {
                        h.applyStock(payload.ids(), payload.counts(), payload.truncated()); // m298
                        if (client.screen instanceof com.sdzjz.client.DataPanelScreen ds)
                            ds.onStockSync();
                    }
                });
        // m80：全模组物品 tooltip 水印
        com.sdzjz.client.ClientHooks.onItemTooltip((stack, lines) -> { // m405 平台口
            if ("sdzjz".equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()))
                lines.add(net.minecraft.network.chat.Component.literal("DY：乔大仙").withStyle(net.minecraft.ChatFormatting.GOLD));
        });
        // m320：Sodium"仅动画可见纹理"优化会冻结纯 GUI 物品动画精灵（方块精灵靠世界渲染保活不受累）
        // ——每客户端 tick 给四件动画物品精灵标活跃；未装 Sodium 垫片自动熔断零开销。
        com.sdzjz.client.ClientHooks.onClientTickEnd(com.sdzjz.client.SodiumSpriteKicker::tick); // m405 平台口
        // m384 选区高亮：手持已绑定移除器=世界内紫色能量框罩住选区（"技能选中"圈）
        com.sdzjz.client.ChunkRegionHighlighter.register();
        // m386 手持设置面板快捷键（默认 R 可改键位）：手持移除器时开屏，否则无事
        var chunkCfgKey = com.sdzjz.client.ClientHooks.registerKey("key.sdzjz.chunk_config",
                org.lwjgl.glfw.GLFW.GLFW_KEY_R, "category.sdzjz"); // m405 平台口
        com.sdzjz.client.ClientHooks.onClientTickEnd(mc -> {
            while (chunkCfgKey.consumeClick()) {
                if (mc.player == null || mc.screen != null) continue;
                int handK = mc.player.getMainHandItem().getItem() instanceof com.sdzjz.item.ChunkRemoverItem ? 0
                        : mc.player.getOffhandItem().getItem() instanceof com.sdzjz.item.ChunkRemoverItem ? 1 : -1;
                if (handK >= 0) mc.setScreen(new com.sdzjz.client.ChunkRemoverConfigScreen(handK));
            }
        });
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心画布 + 超大工作台 GUI 已注册。");
    }
}
