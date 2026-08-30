package com.sdzjz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * m490（真移植·画布视觉第五件）：**画布小地图两代共用一份**——主线
 * {@code StructureCoreScreen.renderMinimap/mapGeom/mapJump} 整段搬。
 *
 * <p>工艺照原文：m120 投影 + 边框 + 深底 + 顶部青条 + 空画布提示 + 节点按
 * {@link NodeCardRenderer#nodeAccent 分类配色}画小矩形（尺寸随缩放，最小 3px）+
 * **当前视口白框**（四边各 1px，钳在图内）。点击/拖拽跳转沿用 `jumpTarget` 算式。
 *
 * <p><b>世代差为零</b>：全是 `ctx.fill` 与算术。主线的 `panX/panY` 与 1.20.1 的
 * `viewX/viewY` 只是同一件事的两种记法（主线存"平移像素"、本世代存"视口左上的画布坐标"），
 * 换算在 {@link View} 里做一次，共用件只认 {@link View}。
 */
public final class MinimapRenderer {

    private MinimapRenderer() { }

    public static final int MAP_W = 148, MAP_H = 100;

    /** 视图口：把两代各自的平移/缩放记法归一成「视口左上的画布坐标 + 缩放 + 工作区边界」。 */
    public interface View {
        double viewLeft();      // 视口左上对应的画布 x
        double viewTop();       // 视口左上对应的画布 y
        double zoom();
        int workLeft();         // 工作区（画布可视区）屏幕边界
        int workTop();
        int workRight();
        int workBottom();
        int nodeCount();
        int nodeX(int i);       // 画布坐标
        int nodeY(int i);
        ItemStack nodeStack(int i);
    }

    /** 几何：{画布最小 x, 画布最小 y, 缩放比}——原文 mapGeom 逐句。 */
    public static double[] geom(View v) {
        double minX = v.viewLeft(), minY = v.viewTop();
        double maxX = v.viewLeft() + (v.workRight() - v.workLeft()) / v.zoom();
        double maxY = v.viewTop() + (v.workBottom() - v.workTop()) / v.zoom();
        for (int i = 0; i < v.nodeCount(); i++) {
            int nx = v.nodeX(i), ny = v.nodeY(i);
            minX = Math.min(minX, nx); minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx + NodeCardRenderer.NW);
            maxY = Math.max(maxY, ny + NodeCardRenderer.NH + 28);
        }
        double sc = Math.min((MAP_W - 10) / Math.max(1, maxX - minX), (MAP_H - 10) / Math.max(1, maxY - minY));
        return new double[]{minX, minY, sc};
    }

    /** 画小地图（mx,my=图左上角）——原文 renderMinimap 逐句。 */
    public static void render(GuiGraphics ctx, Font font, View v, int mx, int my) {
        ctx.fill(mx + 2, my + 3, mx + MAP_W + 3, my + MAP_H + 3, 0x59000000); // m120 投影
        ctx.fill(mx - 1, my - 1, mx + MAP_W + 1, my + MAP_H + 1, SciSkin.FRAME);
        ctx.fill(mx, my, mx + MAP_W, my + MAP_H, 0xE0101820);
        ctx.fill(mx, my, mx + MAP_W, my + 2, SciSkin.ACCENT);
        if (v.nodeCount() == 0) {
            String s = "画布为空";
            ctx.drawString(font, s, mx + (MAP_W - font.width(s)) / 2, my + MAP_H / 2 - 4, SciSkin.SUB, false);
            return;
        }
        double[] g = geom(v);
        for (int i = 0; i < v.nodeCount(); i++) {
            int x1 = mx + 5 + (int) ((v.nodeX(i) - g[0]) * g[2]);
            int y1 = my + 5 + (int) ((v.nodeY(i) - g[1]) * g[2]);
            int w = Math.max(3, (int) (NodeCardRenderer.NW * g[2]));
            int h = Math.max(3, (int) (NodeCardRenderer.NH * g[2]));
            ctx.fill(x1, y1, x1 + w, y1 + h, NodeCardRenderer.nodeAccent(v.nodeStack(i)));
        }
        int vx1 = mx + 5 + (int) ((v.viewLeft() - g[0]) * g[2]);
        int vy1 = my + 5 + (int) ((v.viewTop() - g[1]) * g[2]);
        int vx2 = mx + 5 + (int) ((v.viewLeft() + (v.workRight() - v.workLeft()) / v.zoom() - g[0]) * g[2]);
        int vy2 = my + 5 + (int) ((v.viewTop() + (v.workBottom() - v.workTop()) / v.zoom() - g[1]) * g[2]);
        vx1 = Math.max(mx + 1, vx1); vy1 = Math.max(my + 1, vy1);
        vx2 = Math.min(mx + MAP_W - 1, vx2); vy2 = Math.min(my + MAP_H - 1, vy2);
        int vc = 0xCCFFFFFF; // 当前视口白框
        ctx.fill(vx1, vy1, vx2, vy1 + 1, vc); ctx.fill(vx1, vy2 - 1, vx2, vy2, vc);
        ctx.fill(vx1, vy1, vx1 + 1, vy2, vc); ctx.fill(vx2 - 1, vy1, vx2, vy2, vc);
    }

    /** 点中的画布点（调用方据此把它移到工作区中心）——原文 mapJump 的几何部分。 */
    public static double[] jumpTarget(View v, int mx, int my, double mouseX, double mouseY) {
        double[] g = geom(v);
        return new double[]{g[0] + (mouseX - mx - 5) / g[2], g[1] + (mouseY - my - 5) / g[2]};
    }
}
