package com.sdzjz.client;

/**
 * m514（真移植·A7b）：**「整理布局」的落位几何两代共用一份**——主线 {@code StructureCoreScreen.autoLayout}（m149 竖排 / m221 间距进配置）
 * 的算术部分整段搬：机器排网格（单列往下码，满 rows 台换列，步距=卡实占+间隙），存储排右列（x=760，行距 72）。
 *
 * <p><b>世代差为零</b>：只读两代同一份 {@code NodeCardRenderer.NW/NH} 与 common 配置 {@code canvasLayoutRows/GapX/GapY}。
 * 差住在调用点：落位怎么发（主线 {@code NodeMovePayload/StorageNodeMovePayload} + m265 {@code holdHome} 客户端预测 + 停靠卡过滤；
 * 1.20.1 {@code NodeMove/StorageNodeMove}，存储卡皆为放置卡）——两代各自循环发包，几何从这里取。
 */
public final class CanvasLayout {
    private CanvasLayout() {}

    /** 第 i 台机器的画布坐标 {x, y}（m149 竖排：单列往下码，满 rows 台换列；m221 步距=卡实占+间隙，旧 150/130 定值退役）。 */
    public static int[] machinePos(int i) {
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); // m221 间距收紧+进配置（作者截图点名"离得有点远"）
        int rows = Math.max(1, c.canvasLayoutRows);                          // 旧 150/130 定值退役；步距=卡实占+间隙
        int stepX = NodeCardRenderer.NW + Math.max(0, c.canvasLayoutGapX);   // 卡宽 100 + 默认间隙 30 = 130
        int stepY = NodeCardRenderer.NH + 28 + Math.max(0, c.canvasLayoutGapY); // 卡高 52+28(升级格/徽章带，fitView 同口径) + 默认间隙 24 = 104
        return new int[]{20 + (i / rows) * stepX, 20 + (i % rows) * stepY};
    }

    /** 端点列表里第 j 个存储卡的画布坐标 {x, y}（右列竖排，行距 72）。 */
    public static int[] storagePos(int j) {
        return new int[]{760, 20 + j * 72};
    }
}
