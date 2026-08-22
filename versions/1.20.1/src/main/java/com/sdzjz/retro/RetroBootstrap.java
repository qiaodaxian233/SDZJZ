package com.sdzjz.retro;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * m439 旧世代（1.20.1）bootstrap 入口——m436 方案 P-B 第一段：工具链骨架 + Common 层真编译验证。
 * 与 26.x 的 ModernBootstrap 同规：装进游戏打一行在岗日志，尚无玩法；业务域按 P-B 第二段起
 * 逐域移植（存储网络+Xfer120/ItemData 的 TagItemData 对位+Create 物流对接=该格首个可玩里程碑，
 * 作者拍板 m437）。错误归 Retro 不污染 Legacy/Modern（m370 口径同款）。
 */
public final class RetroBootstrap implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("sdzjz");

    @Override
    public void onInitialize() {
        LOGGER.info("[sdzjz] 1.20.1 旧世代 bootstrap 在岗：Common 层已挂载（{}/{}/{} 可达）；业务域随 P-B 第二段移植",
                com.sdzjz.machine.CraftPlanner.class.getSimpleName(),
                com.sdzjz.node.CoreScheduler.class.getSimpleName(),
                com.sdzjz.machine.MobDrops.class.getSimpleName());
    }
}
