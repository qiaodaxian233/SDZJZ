package com.sdzjz.retro;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * m448：1.20.1 客户端入口——面板屏注册 + Rows 接收器（render 线程直达，路由给在开的面板屏）。
 * 只做客户端接线，零业务逻辑（业务全在服务端半 m447，客户端屏只是显示与转发点击）。
 */
public final class RetroClientBootstrap implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(RetroBlocks.PANEL_MENU, DataPanelScreen120::new);
        MenuScreens.register(RetroBlocks.CANVAS_MENU, CanvasScreen120::new); // m456
        // m465：动画归位——存储核心（能量核旋转+呼吸）与数据线（能量脉冲）BER，蓝本=主线 SdzjzClient
        // 同两行；走原版注册口 BlockEntityRenderers（1.20.1 在位，免 Fabric rendering 面）。
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(RetroBlocks.STORAGE_CORE_BE, StorageCoreRenderer120::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(RetroBlocks.DATA_CABLE_BE, DataCableRenderer120::new);
        ClientNet120.onClient(PanelPayloads120.Rows.TYPE, packet -> {
            if (Minecraft.getInstance().screen instanceof DataPanelScreen120 screen) screen.acceptRows(packet);
        });
        ClientNet120.onClient(CanvasPayloads120.CanvasSnapshot.TYPE, packet -> { // m456
            if (Minecraft.getInstance().screen instanceof CanvasScreen120 screen) screen.acceptSnapshot(packet);
        });
    }
}
