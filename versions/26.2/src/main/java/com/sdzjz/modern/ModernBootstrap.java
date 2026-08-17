package com.sdzjz.modern;

import com.sdzjz.machine.CoreScheduler;
import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.platform.Platform;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * m370 新世代（26.2）bootstrap 入口——本阶段只验证三件事：
 * ① 新世代工具链（fabric-loom 1.17 / Gradle 9.5.1 / JDK 25 / 无 mappings）能编译打包本仓；
 * ② Common 层（common/src/main/java，硬闸保证零 MC 依赖）在 26.2 侧原样可编译、可装载；
 * ③ 产物能被 26.2 的 Fabric Loader 加载进游戏（看下面这行日志）。
 *
 * 刻意不做的事：不注册任何 Platform 服务、不注册方块/物品/网络包——ModernRecipeAccess
 * 等适配器属 Phase 2 后续刀（分域各写 ModernXxxAccess 再组合，见 m368 四域接口）。
 * Platform 的"未注册即用=硬失败"语义在此安全：bootstrap 只取类名不取服务。
 */
public final class ModernBootstrap implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("sdzjz");

    @Override
    public void onInitialize() {
        // 编译期强引用三个 Common 核心类=Common 挂载的最硬证据（类静态体仅缓存表，零副作用）。
        LOGGER.info("[sdzjz] 26.2 新世代 bootstrap 在岗：Common 层已挂载（{}/{}/{} 可达），适配器待 Phase 2 接续",
                CraftPlanner.class.getSimpleName(),
                CoreScheduler.class.getSimpleName(),
                Platform.class.getSimpleName());
    }
}
