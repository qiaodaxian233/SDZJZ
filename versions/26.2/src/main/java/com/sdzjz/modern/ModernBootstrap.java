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
 * （craft/smelt/remainder 三域已落地，brew/ench 显式硬失败立档待 Phase 2 后续刀）。
 * 仍不注册方块/物品/网络包——那些属 Phase 3。
 */
public final class ModernBootstrap implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("sdzjz");

    @Override
    public void onInitialize() {
        // 代际引导（m371）：configDir 第一行（m365 规矩），RecipeAccess 随后——注册一次定终身。
        Platform.initConfigDir(FabricLoader.getInstance().getConfigDir());
        Platform.initRecipes(new ModernRecipeAccess());
        // 编译期强引用 Common 核心类=Common 挂载的最硬证据（类静态体仅缓存表，零副作用）。
        LOGGER.info("[sdzjz] 26.2 新世代 bootstrap 在岗：Common 层已挂载（{}/{}/{} 可达），配方域适配器已注册（craft/smelt/remainder；brew/ench 待 Phase 2）",
                CraftPlanner.class.getSimpleName(),
                CoreScheduler.class.getSimpleName(),
                Platform.class.getSimpleName());
    }
}
