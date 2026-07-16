package com.sdzjz;

import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端初始化器。
 * Phase 0：占位。Phase 1 起挂：控制器 GUI 的 Screen、方块/物品渲染、网络包客户端接收。
 * 节点画布的自绘渲染（节点卡片 + 贝塞尔连线 + 拖拽/缩放）在 Phase 6+ 才在这里落地。
 */
public class SdzjzClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Sdzjz.LOGGER.info("[生电终结者] 客户端骨架已加载。");
    }
}
