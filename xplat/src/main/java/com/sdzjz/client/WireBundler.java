package com.sdzjz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * m511（真移植·A4）：**画布连线归并（m193）两代共用一份**——主线 {@code StructureCoreScreen} renderBg 里
 * {@code seBundles}（组↔存储归并线，原 599~654）与 {@code mmBundles}（组↔机器归并线，原 663~698）两段 +
 * {@code // ===== m193 连线归并 =====} 助手区（原 1668~1693：GROUP_ENC / mmAnchorGeom / drawBundleBadge）整段搬。
 *
 * <p><b>世代差为零</b>：归并的输入是 m507 已共用的分组共享表（gm/gRect/nGid）与两代同一份的
 * {@link WireRenderer}，输出是 drawWire 调用 + ×N 徽章；全是 LinkedHashMap 计数、算术与
 * {@code ctx.fill}/{@code drawString}。两代屏各自的状态收进两个口：
 * <ul>
 *   <li>{@link GroupFrameRenderer.View}——节点坐标（含各代拖动覆盖）、pan/zoom 记法（换算口径同 m507 pushWorld）；</li>
 *   <li>{@link StoragePorts}——<b>唯一参数化的真世代差</b>：存储端卡片的接口位置。主线是总线带上的放置卡
 *       （{@code snx/sny} + {@code bw()/bh()}，收料口 x+14 / 供料口 x+bw-14，卡底 +2），1.20.1 是画布上
 *       24×24（乘 zoom）的存储节点卡（收料口 x+0.25w / 供料口 x+0.75w，卡底 +2）。口只回四个屏幕坐标，
 *       选缘（m184 看几何）与走线在共用件里，两代同一份。</li>
 * </ul>
 *
 * <p>分流（{@link #takeStorageEdge}/{@link #takeConnection}）留在两代各自的绘制循环里调——归并与非归并线
 * 本就在同一循环里分流（m505 普查稿记着这两段夹在正常绘制循环里，不能把普通连线绘制切走），
 * 所以共用件只收"分流判定+计数"与"归并线绘制"两半，循环体归调用方。
 *
 * <p><b>矩阵约法</b>：存储归并线走屏幕坐标（主线原文在世界矩阵 push 之前画，pxScale=1f）；机器归并线
 * 走世界坐标——<b>调用方须已处于世界矩阵下</b>（主线原文就在自己 push 的 translate(panX,panY)+scale(zoom)
 * 里画，紧跟非归并机器线之后、拖线预览之前；本件不再自己 push，否则主线会双重变换）。1.20.1 用
 * {@link GroupFrameRenderer#pushWorld} 把机器线层包成同一形状。
 */
public final class WireBundler {

    /** 归并锚编码高位：置位=组(低位gid)，否则=节点下标 */
    private static final int GROUP_ENC = 0x40000000;

    /** 存储端卡片接口（屏幕坐标）：{收料口x, 收料口y, 供料口x, 供料口y}；端点不在列表=null（不画，杜绝悬空线）。 */
    public interface StoragePorts {
        float[] ports(long endpoint);
    }

    private final boolean on;   // m193 归并开关（有组 && canvasGroupBundleWires）
    private final int[] nGid;   // 节点→组查表（-1=不属组）
    private final java.util.LinkedHashMap<String, int[]> seBundles = new java.util.LinkedHashMap<>(); // m193 (组|端点|向)→{数,亮}
    private final java.util.LinkedHashMap<Long, int[]> mmBundles = new java.util.LinkedHashMap<>();   // 锚对→{数,亮}

    public WireBundler(boolean on, int[] nGid) {
        this.on = on;
        this.nGid = nGid;
    }

    /** m193 组成员的存储线→归并，后面按组框画一条。返回 true=已计入归并（调用方 continue，不画单线）。 */
    public boolean takeStorageEdge(int mi, long endpoint, long dir, boolean lit) {
        if (on && mi < nGid.length && nGid[mi] >= 0) {
            int[] acc = seBundles.computeIfAbsent(nGid[mi] + "|" + endpoint + "|" + dir, k -> new int[]{0, 0});
            acc[0]++;
            if (lit) acc[1] = 1;
            return true;
        }
        return false;
    }

    /** m193 归并：端点属组→锚到组框缘，同锚对折并成一条+×N徽章；同组内部线照旧各画各的。
     *  返回 true=跨组界已计入归并（调用方 continue，不画单线）。 */
    public boolean takeConnection(int a, int b, boolean lit) {
        int ga = on ? nGid[a] : -1, gb = on ? nGid[b] : -1;
        if ((ga >= 0 || gb >= 0) && ga != gb) { // 跨组界→归并（组内部线不归并）
            int se = ga >= 0 ? (GROUP_ENC | ga) : a, de = gb >= 0 ? (GROUP_ENC | gb) : b;
            int[] acc = mmBundles.computeIfAbsent(((long) se << 32) | (de & 0xFFFFFFFFL), k -> new int[]{0, 0});
            acc[0]++;
            if (lit) acc[1] = 1;
            return true;
        }
        return false;
    }

    /** m193 组↔存储归并线：组框缘（世界→屏幕）到端点口，一对一条。屏幕坐标层（pxScale=1f），在世界矩阵 push 之前调。 */
    public void drawStorageBundles(GuiGraphics ctx, Font font, GroupFrameRenderer.View v,
                                   java.util.Map<Integer, int[]> gRect, StoragePorts ports) {
        for (var en : seBundles.entrySet()) { // m193 组↔存储归并线：组框缘（世界→屏幕）到端点口，一对一条
            String[] kp = en.getKey().split("\\|");
            int[] r = gRect.get(Integer.parseInt(kp[0]));
            long pl = Long.parseLong(kp[1]);
            float[] p = ports.ports(pl);
            if (r == null || p == null) continue;
            float gys = screenY(v, (r[1] + GroupFrameRenderer.GBAND + r[3]) / 2.0);
            float gcx = screenX(v, (r[0] + r[2]) / 2.0);
            boolean lit = en.getValue()[1] != 0;
            float bx2, by2;
            if (kp[2].equals("0")) { // 组→存储（产出）
                boolean er = p[0] >= gcx;
                float gxs = screenX(v, er ? r[2] : r[0]);
                bx2 = p[0]; by2 = p[1];
                WireRenderer.drawWire(ctx, gxs, gys, er ? 1 : -1, 0, bx2, by2, 0, -1,
                        lit ? SciSkin.wireOut() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireOut(), 0.30f), 1f); // m198 出线配置色
                drawBundleBadge(ctx, font, (gxs + bx2) / 2, (gys + by2) / 2, en.getValue()[0], lit);
            } else {                 // 存储→组（供料）
                boolean fr = p[2] >= gcx;
                float gxi = screenX(v, fr ? r[2] : r[0]);
                bx2 = p[2]; by2 = p[3];
                WireRenderer.drawWire(ctx, bx2, by2, 0, 1, gxi, gys, fr ? -1 : 1, 0,
                        lit ? SciSkin.wireIn() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireIn(), 0.30f), 1f); // m198 进线配置色
                drawBundleBadge(ctx, font, (gxi + bx2) / 2, (gys + by2) / 2, en.getValue()[0], lit);
            }
        }
    }

    /** m193 归并线：组框缘/卡缘 → 组框缘/卡缘，一锚对一条。世界坐标层——调用方须已处于世界矩阵下（pxScale=zoom）。 */
    public void drawMachineBundles(GuiGraphics ctx, Font font, GroupFrameRenderer.View v, java.util.Map<Integer, int[]> gRect) {
        for (var en : mmBundles.entrySet()) { // m193 归并线：组框缘/卡缘 → 组框缘/卡缘，一锚对一条
            long k3 = en.getKey();
            double[] A = mmAnchorGeom(v, gRect, (int) (k3 >>> 32));
            double[] B = mmAnchorGeom(v, gRect, (int) k3);
            if (A == null || B == null) continue;
            boolean fwd = B[0] >= A[0];
            float ax = (float) (fwd ? A[3] : A[2]), bx = (float) (fwd ? B[2] : B[3]);
            int dir = fwd ? 1 : -1;
            boolean lit3 = en.getValue()[1] != 0;
            WireRenderer.drawWire(ctx, ax, (float) A[1], dir, 0, bx, (float) B[1], dir, 0,
                    lit3 ? SciSkin.wireOut() : SciSkin.mix(SciSkin.termInk(), SciSkin.wireOut(), 0.30f), (float) v.zoom()); // m198
            drawBundleBadge(ctx, font, (ax + bx) / 2, (float) ((A[1] + B[1]) / 2), en.getValue()[0], lit3);
        }
    }

    /** 归并锚点几何（世界坐标）：组=组框，节点=卡。返回 {中心x, 出线y, 左缘x, 右缘x}；查不到=null 跳过。 */
    private static double[] mmAnchorGeom(GroupFrameRenderer.View v, java.util.Map<Integer, int[]> gRect, int enc) {
        if ((enc & GROUP_ENC) != 0) {
            int[] r = gRect.get(enc & ~GROUP_ENC);
            if (r == null) return null;
            return new double[]{(r[0] + r[2]) / 2.0, (r[1] + GroupFrameRenderer.GBAND + r[3]) / 2.0, r[0], r[2]};
        }
        if (enc < 0 || enc >= v.nodeCount()) return null;
        int nx = v.nodeX(enc), ny = v.nodeY(enc);
        return new double[]{nx + NodeCardRenderer.NW / 2.0, ny + NodeCardRenderer.NH / 2.0, nx, nx + NodeCardRenderer.NW};
    }

    /** 归并线 ×N 徽章：线中点小字（N<2 不画；底衬走 BACKDROP 半透，零新色字面量）。 */
    private static void drawBundleBadge(GuiGraphics ctx, Font font, float midX, float midY, int n, boolean lit) {
        if (n < 2) return;
        String bt = "×" + n;
        int tw = font.width(bt);
        int bx = (int) midX - tw / 2, by = (int) midY - 5;
        ctx.fill(bx - 2, by - 2, bx + tw + 2, by + 9, SciSkin.withAlpha(SciSkin.termInk(), 0.78f));
        ctx.drawString(font, bt, bx, by, lit ? SciSkin.TXT_HI : SciSkin.SUB, false);
    }

    /** 世界→屏幕（= 主线 panX + wx*zoom；换算口径同 {@link GroupFrameRenderer} pushWorld：panX = -viewLeft*zoom）。 */
    private static float screenX(GroupFrameRenderer.View v, double wx) { return (float) ((wx - v.viewLeft()) * v.zoom()); }
    private static float screenY(GroupFrameRenderer.View v, double wy) { return (float) ((wy - v.viewTop()) * v.zoom()); }
}
