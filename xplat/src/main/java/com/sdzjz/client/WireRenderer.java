package com.sdzjz.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * m488（真移植·画布视觉第三件）：**连线缎带两代共用一份**——主线
 * {@code StructureCoreScreen} 的 drawWire/drawWireFree/wirePath/pulseAt/ribbon/portDot 等
 * 129 行整段搬（m136 谱系）。1.20.1 原来那根**等宽直线段**（我当初自己写的 3 行 line()）就此退役。
 *
 * <p>搬过来的工艺：三次贝塞尔走线（控制柄按距离自适应，近不打结远不拉直）+ 采样法线与弧长累计 +
 * <b>三层缎带</b>（第一层右下偏移暗投影把线从网格上抬起来、第二层宽羽化软光晕、第三层亮度沿线坡升
 * 的亮核）+ <b>彗星脉冲</b>（等距脉冲沿弧长顺流，速度 110px/s 间距 88px，头缘陡尾缘缓，
 * 并排线按端点坐标错相）+ <b>端口发光圆点</b>（12 段三角扇 + 渐隐外环，线的起讫落在接点上
 * 而不是凭空断在卡片边缘）+ m197 线宽随缩放的封顶策略。
 *
 * <p><b>唯一的世代差</b>是发顶点那四行（1.21 {@code addVertex().setColor()} vs 1.20.1
 * {@code vertex().color().endVertex()}），走 m483 已建的 {@link SciSkin.Gfx} 世代口。
 * 其余一个字未改——包括绕序注释（"负向=与 fill 同绕序"、"绕线方向发四边形"），
 * 绕序错了整条线会被背面剔除掉，这类注释尤其不能动。
 */
public final class WireRenderer {

    private WireRenderer() { }

    public static void drawWire(GuiGraphics ctx, float x1, float y1, float tx1, float ty1,
                          float x2, float y2, float tx2, float ty2, int color, float pxScale) {
        wirePath(ctx, x1, y1, tx1, ty1, x2, y2, tx2, ty2, color, pxScale);
    }

    /** 拖线预览：终点跟随鼠标，末端切线取行进方向，曲线自然收尾。 */
    public static void drawWireFree(GuiGraphics ctx, float x1, float y1, float tx1, float ty1,
                              float x2, float y2, int color, float pxScale) {
        float dx = x2 - x1, dy = y2 - y1;
        float l = (float) Math.hypot(dx, dy);
        if (l < 1f) { dx = 1; dy = 0; l = 1; }
        wirePath(ctx, x1, y1, tx1, ty1, x2, y2, dx / l, dy / l, color, pxScale);
    }

    private static void wirePath(GuiGraphics ctx, float x1, float y1, float tx1, float ty1,
                          float x2, float y2, float tx2, float ty2, int color, float pxScale) {
        float dist = (float) Math.hypot(x2 - x1, y2 - y1);
        if (dist < 2f) return;
        // m197 有效除数：屏幕线宽=基准×min(pxScale,封顶)。开关关=旧行为(除pxScale=屏幕恒宽)；
        // 屏幕坐标层调用传 pxScale=1 且封顶≥1 时 pd=1 完全不受影响。
        com.sdzjz.config.SdzjzConfig wcfg = com.sdzjz.config.SdzjzConfig.get();
        float wcap = (float) Math.max(0.2, wcfg.canvasWireMaxScale);
        float pd = wcfg.canvasWireScaleWithZoom ? pxScale / Math.min(pxScale, wcap) : pxScale;
        float d = Math.max(36f, Math.min(150f, dist * 0.42f)); // 控制柄长度：近不打结、远不拉直
        float c1x = x1 + tx1 * d, c1y = y1 + ty1 * d;
        float c2x = x2 - tx2 * d, c2y = y2 - ty2 * d;
        int n = Math.max(20, Math.min(56, (int) (dist / 8f))) + 1; // 采样数按线长自适应
        float[] px = new float[n], py = new float[n], nx = new float[n], ny = new float[n], cum = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1), u = 1 - t;
            float a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, e = t * t * t;
            px[i] = a * x1 + b * c1x + c * c2x + e * x2;
            py[i] = a * y1 + b * c1y + c * c2y + e * y2;
        }
        for (int i = 0; i < n; i++) { // 单位法线（中央差分）+ 弧长累计（脉冲定位用）
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            float dx = px[i1] - px[i0], dy = py[i1] - py[i0];
            float l = (float) Math.hypot(dx, dy); if (l < 1e-4f) l = 1;
            nx[i] = -dy / l; ny[i] = dx / l;
            if (i > 0) cum[i] = cum[i - 1] + (float) Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
        }
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                ctx.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f mat = ctx.pose().last().pose();

        int rgb = color & 0xFFFFFF;
        float time = (System.currentTimeMillis() % 600000L) / 1000f;
        float seed = ((x1 * 13f + y1 * 7f + x2 * 3f + y2) % 88f + 88f) % 88f; // 并排线错相
        float[] w = new float[n];
        int[] col = new int[n];

        // 第一层：投影（右下偏移的暗缎带，把线从网格上抬起来）
        float shOff = 1.4f / pd;
        java.util.Arrays.fill(w, 1.2f / pd);
        java.util.Arrays.fill(col, 0x3A000000);
        ribbon(vc, mat, px, py, nx, ny, cum, w, 1.8f / pd, col, shOff, shOff * 1.2f);

        // 第二层：软光晕（宽羽化低透明，脉冲过处微微透亮）
        for (int i = 0; i < n; i++) {
            float p = pulseAt(cum[i], time, seed);
            w[i] = 2.1f / pd;
            col[i] = ((int) (0x24 + 0x2C * p) << 24) | rgb;
        }
        ribbon(vc, mat, px, py, nx, ny, cum, w, 3.4f / pd, col, 0, 0);

        // 第三层：亮核（沿线亮度坡升暗示方向；脉冲提亮向白并微胀线身）
        for (int i = 0; i < n; i++) {
            float t = cum[i] / cum[n - 1];
            float p = pulseAt(cum[i], time, seed);
            int cc = towardWhite(mulRgb(rgb, 0.82f + 0.23f * t), p * 0.75f);
            col[i] = (Math.min(0xFF, 0xD8 + (int) (0x27 * p)) << 24) | cc;
            w[i] = (0.9f + 0.5f * p) / pd;
        }
        ribbon(vc, mat, px, py, nx, ny, cum, w, 1.0f / pd, col, 0, 0);

        // 端口圆点：线的起讫落在发光接点上，不再凭空断在卡片边缘
        portDot(vc, mat, x1, y1, rgb, pd);
        portDot(vc, mat, x2, y2, rgb, pd);
    }

    /** 彗星脉冲强度 0..1：等距脉冲沿弧长顺流（速度110px/s 间距88px），头缘陡尾缘缓。 */
    private static float pulseAt(float s, float time, float seed) {
        float m = (s - time * 110f - seed) % 88f;
        if (m < 0) m += 88f;
        float head = m < 10f ? 1f - m / 10f : 0f;          // 头前 10px 陡降
        float tail = m > 88f - 22f ? (m - (88f - 22f)) / 22f : 0f; // 尾后 22px 缓升到头
        float v = Math.max(head, tail * tail * 0.85f);
        return v * v * (3 - 2 * v); // smoothstep 圆润
    }

    /** 单条缎带：中心两侧 halfW 实体 + feather 羽化到全透明（顶点插色=抗锯齿）。绕线方向发四边形，与 fill 同绕序。 */

    private static void ribbon(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat,
                        float[] px, float[] py, float[] nx, float[] ny, float[] cum,
                        float[] halfW, float feather, int[] col, float ox, float oy) {
        int n = px.length;
        for (int i = 0; i + 1 < n; i++) {
            int c0 = col[i], c1 = col[i + 1];
            int f0 = c0 & 0x00FFFFFF, f1 = c1 & 0x00FFFFFF;
            float ax = px[i] + ox, ay = py[i] + oy, bx = px[i + 1] + ox, by = py[i + 1] + oy;
            float anx = nx[i], any = ny[i], bnx = nx[i + 1], bny = ny[i + 1];
            float ah = halfW[i], bh = halfW[i + 1], af = ah + feather, bf = bh + feather;
            quad(vc, mat, ax - anx * af, ay - any * af, f0, ax - anx * ah, ay - any * ah, c0,
                          bx - bnx * bh, by - bny * bh, c1, bx - bnx * bf, by - bny * bf, f1); // 左羽化
            quad(vc, mat, ax - anx * ah, ay - any * ah, c0, ax + anx * ah, ay + any * ah, c0,
                          bx + bnx * bh, by + bny * bh, c1, bx - bnx * bh, by - bny * bh, c1); // 核心
            quad(vc, mat, ax + anx * ah, ay + any * ah, c0, ax + anx * af, ay + any * af, f0,
                          bx + bnx * bf, by + bny * bf, f1, bx + bnx * bh, by + bny * bh, c1); // 右羽化
        }
    }


    private static void portDot(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat,
                         float cx, float cy, int rgb, float pxScale) {
        float r = 2.5f / pxScale, fe = 1.9f / pxScale;
        int cCenter = 0xFF000000 | towardWhite(rgb, 0.55f);
        int cEdge   = 0xE6000000 | rgb;
        int cOut    = rgb; // alpha 0
        int seg = 12;
        for (int k = 0; k < seg; k++) {
            double a0 = -2 * Math.PI * k / seg, a1 = -2 * Math.PI * (k + 1) / seg; // 负向=与 fill 同绕序
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
            float x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
            float X0 = cx + (float) Math.cos(a0) * (r + fe), Y0 = cy + (float) Math.sin(a0) * (r + fe);
            float X1 = cx + (float) Math.cos(a1) * (r + fe), Y1 = cy + (float) Math.sin(a1) * (r + fe);
            quad(vc, mat, cx, cy, cCenter, x0, y0, cEdge, x1, y1, cEdge, cx, cy, cCenter); // 内盘扇片
            quad(vc, mat, x0, y0, cEdge, X0, Y0, cOut, X1, Y1, cOut, x1, y1, cEdge);       // 外环羽化
        }
    }


    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat,
                      float x1, float y1, int c1, float x2, float y2, int c2,
                      float x3, float y3, int c3, float x4, float y4, int c4) {
        SciSkin.gfxQuad(vc, mat, x1, y1, c1, x2, y2, c2, x3, y3, c3, x4, y4, c4); // m488 世代口
    }

    private static int mulRgb(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }


    private static int towardWhite(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }
}
