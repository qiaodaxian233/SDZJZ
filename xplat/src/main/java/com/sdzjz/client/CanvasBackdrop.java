package com.sdzjz.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * m516（真移植·A11）：**画布底层两代共用一份**——主线 {@code StructureCoreScreen.renderBg} 开头的底色/装饰底图/双色网格/暗角原文整段搬：
 * m203 全屏底随主题（m217 配置可覆盖）→ m220 装饰底图（设了背景色=纯色画布让位；{@code canvasBgDecor=false} 可无条件关）→
 * m188 双色网格（屏幕 32px 定距、相位按世界格序号 floorMod、每 4 格一根主线、m203 随主题强调色、m217 色覆盖+浓度）→ m187/m217 四缘暗角。
 *
 * <p><b>世代差为零</b>：{@code fill/blit}（9 参 blit 两代同签名，SciSkin.drawSlot 在树先例）+ {@link SciSkin}（canvasBg/termGridMinor/Major/vignette）+
 * common 配置；纹理 id 走 {@link SciSkin#gfxTex}（m509 两代门）。差住在调用点的**布局参数**：主线顶栏+总线带占到 34、暗角区 (0,23)~(workRight,botTop)，
 * 且底色与网格之间还夹着主线专有的顶/底终端浅带（m203/m210，本世代顶栏 16 无底栏，不搬）——所以拆成两口：{@link #fillAndDecor} 与
 * {@link #gridAndVignette}，主线在两口之间照旧画浅带，帧序逐位不变。
 */
public final class CanvasBackdrop {
    private CanvasBackdrop() {}

    /** m220 装饰底图（主线 FRAME 常量原文路径；1280×800 拉满全屏）。 */
    private static final String DECOR_TEX = "textures/gui/structure_core_canvas.png";

    /** 底色 + 装饰底图（全屏）。 */
    public static void fillAndDecor(GuiGraphics ctx, int width, int height) {
        ctx.fill(0, 0, width, height, SciSkin.canvasBg()); // m203 全屏底随主题；m217 配置可覆盖（空=跟随主题墨色）
        if (com.sdzjz.config.SdzjzConfig.get().canvasBgDecor && !SciSkin.canvasBgOverridden()) // m220 设了背景色=纯色画布，装饰底图让位（作者截图：改色无感+装饰边碍眼同根）；canvasBgDecor=false 可无条件关
            ctx.blit(SciSkin.gfxTex(DECOR_TEX), 0, 0, 0.0F, 0.0F, width, height, width, height);
    }

    /** 双色网格 + 暗角。panX/panY=世界原点的屏幕位置（主线记法；1.20.1 传 -viewX*zoom）；gridTop=网格起始 y（主线 34，本世代 16）；
     *  (vx1,vy1)~(vx2,vy2)=暗角区（主线 (0,23)~(workRight,botTop)，本世代 (0,16)~(width-SIDEBAR_W,height)）。 */
    public static void gridAndVignette(GuiGraphics ctx, double panX, double panY, int width, int height, int gridTop,
                                       int vx1, int vy1, int vx2, int vy2) {
        // m188 网格双色制：细线打底 + 每4格一根主线；相位按世界格序号（floorMod）定，平移不跳档
        int step = 32;
        int gi0 = (int) Math.floor(-panX / step), gi1 = (int) Math.floor((width - panX) / step);
        for (int gi = gi0; gi <= gi1; gi++) {
            int gsx = (int) Math.floor(panX + gi * (double) step);
            ctx.fill(gsx, gridTop, gsx + 1, height,
                    Math.floorMod(gi, 4) == 0 ? SciSkin.termGridMajor() : SciSkin.termGridMinor()); // m203 网格随主题强调色
        }
        int gj0 = (int) Math.floor((gridTop - panY) / step), gj1 = (int) Math.floor((height - panY) / step);
        for (int gj = gj0; gj <= gj1; gj++) {
            int gsy = (int) Math.floor(panY + gj * (double) step);
            if (gsy >= gridTop) ctx.fill(0, gsy, width, gsy + 1,
                    Math.floorMod(gj, 4) == 0 ? SciSkin.termGridMajor() : SciSkin.termGridMinor());
        }
        SciSkin.vignette(ctx, vx1, vy1, vx2, vy2); // m188 四缘暗角压景深（卡片/连线画在其上不受影响）
    }
}
