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
        HandledScreens.register(ModScreenHandlers.STRUCTURE_CORE, StructureCoreScreen::new);
        HandledScreens.register(ModScreenHandlers.DATA_PANEL, DataPanelScreen::new);
        HandledScreens.register(ModScreenHandlers.TRADE_CENTER, com.sdzjz.client.TradeCenterScreen::new);
        HandledScreens.register(ModScreenHandlers.SUPER_BENCH, SuperBenchScreen::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.STORAGE_CORE_BE, StorageCoreRenderer::new); // 存储核心动画
        BlockEntityRendererRegistry.register(ModBlockEntities.DATA_CABLE_BE, DataCableRenderer::new); // 数据线能量脉冲
        BlockEntityRendererRegistry.register(ModBlockEntities.WIRELESS_NODE_BE, com.sdzjz.client.WirelessNodeRenderer::new); // 无线节点信号波
        // m80：全模组物品 tooltip 水印
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if ("sdzjz".equals(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getNamespace()))
                lines.add(net.minecraft.text.Text.literal("抖音：乔大仙").formatted(net.minecraft.util.Formatting.GOLD));
        });
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心画布 + 超大工作台 GUI 已注册。");
    }
}
