package com.sdzjz;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModBlocks;
import com.sdzjz.registry.ModItems;
import com.sdzjz.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sdzjz implements ModInitializer {
    public static final String MOD_ID = "sdzjz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SdzjzConfig.load();
        ModBlocks.init();
        ModBlockEntities.init();
        ModScreenHandlers.init();
        ModItems.init();
        LOGGER.info("[生电终结者] Phase 1 已加载：结构核心 + 刷线机 + 升级 + 抓物笼子。");
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
