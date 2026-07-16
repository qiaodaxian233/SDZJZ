package com.sdzjz;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生电终结者 主初始化器。
 * Phase 0：只加载配置 + 注册创造物品组，作为整个注册框架的挂载点。
 * Phase 1 起在 onInitialize 里依次挂：控制器方块 / 方块实体 / 面板物品 / ScreenHandler / 配方序列化器。
 */
public class Sdzjz implements ModInitializer {
    public static final String MOD_ID = "sdzjz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SdzjzConfig.load();
        ModItems.init();
        // Phase 1+：ModBlocks.init(); ModBlockEntities.init(); ModScreenHandlers.init(); ModRecipes.init();
        LOGGER.info("[生电终结者] Phase 0 骨架已加载。");
    }

    /** 1.21.1：Identifier 构造器已私有，统一走 Identifier.of(namespace, path)。 */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
