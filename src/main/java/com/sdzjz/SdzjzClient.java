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
        BlockEntityRendererRegistry.register(ModBlockEntities.STORAGE_CORE_BE, StorageCoreRenderer::new); // 存储核心动画
        BlockEntityRendererRegistry.register(ModBlockEntities.DATA_CABLE_BE, DataCableRenderer::new); // 数据线能量脉冲
        BlockEntityRendererRegistry.register(ModBlockEntities.WIRELESS_NODE_BE, com.sdzjz.client.WirelessNodeRenderer::new); // 无线节点信号波
        BlockEntityRendererRegistry.register(ModBlockEntities.SATELLITE_NODE_BE, com.sdzjz.client.SatelliteNodeRenderer::new); // m156 卫星扫描动画
        // m249 三块静态方块动画化（作者点名"存储核心和线都是动态的，它们不动就不好看"）
        BlockEntityRendererRegistry.register(ModBlockEntities.STRUCTURE_CORE_BE, com.sdzjz.client.StructureCoreHoloRenderer::new); // 扫描环
        BlockEntityRendererRegistry.register(ModBlockEntities.DATA_PANEL_BE, com.sdzjz.client.DataPanelHoloRenderer::new);         // 数据流
        BlockEntityRendererRegistry.register(ModBlockEntities.SUPER_BENCH_BE, com.sdzjz.client.SuperBenchHoloRenderer::new);       // 悬浮网格
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
        // m80：全模组物品 tooltip 水印
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if ("sdzjz".equals(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getNamespace()))
                lines.add(net.minecraft.text.Text.literal("DY：乔大仙").formatted(net.minecraft.util.Formatting.GOLD));
        });
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心画布 + 超大工作台 GUI 已注册。");
    }
}
