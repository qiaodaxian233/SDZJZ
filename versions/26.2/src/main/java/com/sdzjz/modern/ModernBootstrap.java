package com.sdzjz.modern;

import com.sdzjz.machine.CoreScheduler;
import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.platform.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * m370 新世代（26.2）bootstrap 入口——本阶段只验证三件事：
 * ① 新世代工具链（fabric-loom 1.17 / Gradle 9.5.1 / JDK 25 / 无 mappings）能编译打包本仓；
 * ② Common 层（common/src/main/java，硬闸保证零 MC 依赖）在 26.2 侧原样可编译、可装载；
 * ③ 产物能被 26.2 的 Fabric Loader 加载进游戏（看下面这行日志）。
 *
 * m371 起接管代际引导端职责（与 Legacy 的 Sdzjz.onInitialize 对位）：第一行注册 configDir
 * （早于任何 SdzjzConfig.get() 懒加载链——m365 规矩），随后注册 ModernRecipeAccess
 * （m373 起 craft/smelt/remainder/brew/ench 五口全承，brew/ench 经分域适配器委托）。
 * m375（Phase 3 第一刀）：申报 fabric-recipe-api-v1 配方同步（shaped/shapeless），配套
 * ModernRecipeAccess 枚举口换双侧统一口——26.2 客户端从此可得判官所需配方集（m374 核名兑现）。
 * 仍不注册方块/物品/网络包——那些属 Phase 3 后续刀。
 */
public final class ModernBootstrap implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("sdzjz");

    @Override
    public void onInitialize() {
        // 代际引导（m371）：configDir 第一行（m365 规矩），RecipeAccess 随后——注册一次定终身。
        Platform.initConfigDir(FabricLoader.getInstance().getConfigDir());
        Platform.initRecipes(new ModernRecipeAccess());
        // m375（m374 立档要点①）：申报配方同步 serializer——主初始化器在物理双端各跑一次即
        // "双端注册"齐活（客户端 configuration 阶段自动把本集申报给服务端，canSend 缺通道优雅降级）。
        // 走注册表 getOptional 路（m371 同款编译级），不赌 RecipeSerializer 常量名；缺 id 静默跳过。
        for (String sid : new String[]{"minecraft:crafting_shaped", "minecraft:crafting_shapeless"})
            net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                    .getOptional(net.minecraft.resources.Identifier.parse(sid))
                    .ifPresent(net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization::synchronizeRecipeSerializer);
        // 编译期强引用 Common 核心类=Common 挂载的最硬证据（类静态体仅缓存表，零副作用）。
        LOGGER.info("[sdzjz] 26.2 新世代 bootstrap 在岗：Common 层已挂载（{}/{}/{} 可达），配方域适配器已注册（五域全齐——m373），配方同步已申报（shaped/shapeless——m375）",
                CraftPlanner.class.getSimpleName(),
                CoreScheduler.class.getSimpleName(),
                Platform.class.getSimpleName());
    }
}
