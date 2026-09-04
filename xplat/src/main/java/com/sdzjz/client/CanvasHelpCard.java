package com.sdzjz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * m515（真移植·A7c）：m219 帮助卡两代共用——主线 {@code renderHelp} 原文；纯静态无交互（点哪都关的 helpOpen 开关留宿主）。
 * 世代差为零；宿主给字体与锚定右缘（主线 {@code workRight()}，1.20.1 侧栏左缘）。
 */
public final class CanvasHelpCard {
    private CanvasHelpCard() {}

    /** m219 帮助卡：操作提示行迁入（原底带一行塞不下的内容摊开写；纯静态无交互，点哪都关）。 */
    public static void render(GuiGraphics ctx, Font font, int anchorRight) {
        int hw = 236, hh = 124; // m313 快捷键两行加高
        int px = Math.max(8, Math.min(336, anchorRight - hw - 8)), py = 22; // 锚"帮助"钮下方，窄屏向左让位
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 400); // m202 抬z口径
        SciSkin.drawCard(ctx, px, py, hw, hh, SciSkin.FRAME);
        ctx.drawString(font, "操作帮助", px + 8, py + 7, SciSkin.TXT_HI, false);
        String[] lines = {
                "右键节点=菜单 · 拖节点=移动",
                "绿口拖线=连线 · 滚轮=缩放",
                "状态灯：绿=运行 黄=阻塞 红=缺料",
                "节点色：青=生产 橙=加工 紫=逻辑 绿=农场",
                "快捷键(悬停节点)：P暂停 X断线 Del取出 V选择", // m313
                "G组合所选 · Shift+G解散组 · F2改组名",
        };
        for (int i = 0; i < lines.length; i++)
            ctx.drawString(font, lines[i], px + 10, py + 24 + i * 14, i < 2 ? SciSkin.TXT : SciSkin.SUB, false);
        ctx.drawString(font, "点任意处关闭", px + 8, py + hh - 13, SciSkin.SUB, false);
        ctx.pose().popPose();
    }
}
