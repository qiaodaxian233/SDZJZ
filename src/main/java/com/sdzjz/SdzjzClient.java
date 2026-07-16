package com.sdzjz;

import com.sdzjz.client.StructureCoreScreen;
import com.sdzjz.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class SdzjzClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.STRUCTURE_CORE, StructureCoreScreen::new);
        Sdzjz.LOGGER.info("[生电终结者] 客户端已加载：结构核心 GUI 已注册。");
    }
}
