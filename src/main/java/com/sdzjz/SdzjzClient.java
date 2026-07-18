package com.sdzjz;

import com.sdzjz.client.DataPanelScreen;
import com.sdzjz.client.StructureCoreScreen;
import com.sdzjz.client.SuperBenchScreen;
import com.sdzjz.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class SdzjzClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.STRUCTURE_CORE, StructureCoreScreen::new);
        HandledScreens.register(ModScreenHandlers.DATA_PANEL, DataPanelScreen::new);
        HandledScreens.register(ModScreenHandlers.TRADE_CENTER, com.sdzjz.client.TradeCenterScreen::new);
        HandledScreens.register(ModScreenHandlers.SUPER_BENCH, SuperBenchScreen::new);
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心画布 + 超大工作台 GUI 已注册。");
    }
}
