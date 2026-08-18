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
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class SdzjzClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.sdzjz.client.SatelliteNodeModel.register(); // m151 卫星节点bbmodel自定义烘焙
        HandledScreens.register(ModScreenHandlers.STRUCTURE_CORE, StructureCoreScreen::new);
        HandledScreens.register(ModScreenHandlers.DATA_PANEL, DataPanelScreen::new);
        HandledScreens.register(ModScreenHandlers.TRADE_CENTER, com.sdzjz.client.TradeCenterScreen::new);
        HandledScreens.register(ModScreenHandlers.SUPER_BENCH, SuperBenchScreen::new);
        HandledScreens.register(ModScreenHandlers.EXTRACT_PORT, com.sdzjz.client.ExtractPortScreen::new); // m226 抽取口配置
        HandledScreens.register(ModScreenHandlers.PORTABLE_VAULT, com.sdzjz.client.PortableVaultScreen::new); // m312 随身仓库
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
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.sdzjz.net.CanvasEndsPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        com.sdzjz.client.StructureCoreScreen.applyEndsPayload(payload)));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver( // m265 端点画布落位
                com.sdzjz.net.StorageNodeHomePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        com.sdzjz.client.StructureCoreScreen.applyHomesPayload(payload)));
        // m275：观众定向渲染快照 → 写回客户端 BE 渲染字段（画布屏 be() 读法零改动）
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.sdzjz.net.CanvasSnapshotPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    var w = context.client().world;
                    if (w != null && w.getBlockEntity(payload.pos()) instanceof com.sdzjz.block.StructureCoreBlockEntity be)
                        be.applyRenderSnapshot(payload.nbt(), w.getRegistryManager());
                }));
        // m289：终端库存摘要 → 灌进正开着的终端 handler 并催书重算"可合成"
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.sdzjz.net.TerminalStockPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    var pl = context.client().player;
                    if (pl != null && pl.currentScreenHandler instanceof com.sdzjz.screen.DataPanelScreenHandler h
                            && h.syncId == payload.syncId()) {
                        h.applyStock(payload.ids(), payload.counts(), payload.truncated()); // m298
                        if (context.client().currentScreen instanceof com.sdzjz.client.DataPanelScreen ds)
                            ds.onStockSync();
                    }
                }));
        // m80：全模组物品 tooltip 水印
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if ("sdzjz".equals(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getNamespace()))
                lines.add(net.minecraft.text.Text.literal("DY：乔大仙").formatted(net.minecraft.util.Formatting.GOLD));
        });
        // m320：Sodium"仅动画可见纹理"优化会冻结纯 GUI 物品动画精灵（方块精灵靠世界渲染保活不受累）
        // ——每客户端 tick 给四件动画物品精灵标活跃；未装 Sodium 垫片自动熔断零开销。
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(com.sdzjz.client.SodiumSpriteKicker::tick);
        // m384 选区高亮：手持已绑定移除器=世界内紫色能量框罩住选区（"技能选中"圈）
        com.sdzjz.client.ChunkRegionHighlighter.register();
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心画布 + 超大工作台 GUI 已注册。");
    }
}
