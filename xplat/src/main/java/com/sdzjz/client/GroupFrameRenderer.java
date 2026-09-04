package com.sdzjz.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * m507（真移植·A5b）：**画布分组组框两代共用一份**——主线 {@code StructureCoreScreen} 的
 * m192 组框渲染段（原 601~622：世界坐标独立一次变换 → 组面 + 标题带渐变 + 四边框线 + 带底分隔线 +
 * 「组名 ×N」）与几何辅助 {@code groupMembers}/{@code groupRect}（原 1340~1363）整段搬。
 *
 * <p><b>世代差为零</b>：全是 {@code ctx.fill}/{@link SciSkin#hGrad}/{@code drawString} 与算术。
 * 两代屏各自的状态（拖动覆盖坐标、拖动中的组、平移/缩放记法、卡包围盒高）收进 {@link View}：
 * 主线 {@code wnx/wny} 含 m196 拖动覆盖、1.20.1 {@code nodeCx/nodeCy} 含拖动幽灵位——共用件只认口。
 * 平移记法照 {@link MinimapRenderer.View} 同一约法（视口左上的画布坐标 + 缩放），换算在 View 里做一次。
 *
 * <p><b>唯一参数化的真世代差</b>：{@link View#cardHeight()}——成员卡包围盒的高。主线是「卡体+升级格行」
 * {@code NH + 26}（与悬停判定同口径，原注），1.20.1 无升级系统（m464 记档）卡下没有升级格行=
 * {@code NH}。不参数化就会给 1.20.1 的组框底边多空出 26 格。
 *
 * <p>分组共享表（gid→成员 / gid→矩形 / 节点→组）由调用方按自己的需要装配——主线还要拿它喂 m193
 * 连线归并（A4），1.20.1 A5b 只画框；共用件不替调用方决定表的形状（m480「不为接口好看搬东西」同律）。
 */
public final class GroupFrameRenderer {

    private GroupFrameRenderer() { }

    /** 组框内边距 / 标题带高（世界单位）——主线 m192 原常量原值。 */
    public static final int GPAD = 10, GBAND = 16;

    /** 视图口：两代屏各自的画布状态归一（坐标记法照 {@link MinimapRenderer.View}）。 */
    public interface View {
        double viewLeft();                      // 视口左上对应的画布 x（主线 = -panX/zoom；1.20.1 = viewX）
        double viewTop();
        double zoom();
        int nodeCount();
        ItemStack nodeStack(int i);
        int nodeX(int i);                       // 画布坐标（含各代自己的拖动覆盖）
        int nodeY(int i);
        int cardHeight();                       // 成员卡包围盒高：主线 NH+26（卡体+升级格行）；1.20.1 NH
        java.util.Map<Integer, String> groupNames(); // 组元数据 id→名（主线 be.groupsView()；1.20.1 快照像 g.groupNames）
        int dragGid();                          // 拖动中的组（框提亮；无=-1，1.20.1 在 A5c 前恒 -1）
    }

    /** gid → 成员下标（只收元数据在册且 ≥2 台的组；归属读 NodeTags.nodeGroup，m180 新代码直用不走垫片）。 */
    public static java.util.LinkedHashMap<Integer, java.util.List<Integer>> groupMembers(View v) {
        java.util.LinkedHashMap<Integer, java.util.List<Integer>> gm = new java.util.LinkedHashMap<>();
        int n = v.nodeCount();
        for (int i = 0; i < n; i++) {
            int g = com.sdzjz.node.NodeTags.nodeGroup(v.nodeStack(i));
            if (g >= 0 && v.groupNames().containsKey(g)) gm.computeIfAbsent(g, k -> new ArrayList<>()).add(i);
        }
        gm.values().removeIf(l -> l.size() < 2);
        return gm;
    }

    /** 组框矩形（世界坐标）：成员卡包围盒（卡体+升级格行，与悬停判定同口径——高走 {@link View#cardHeight()}）
     *  外扩 GPAD，顶上再抬一条 GBAND 标题带。返回 {x1,y1,x2,y2}，标题带=y1..y1+GBAND。 */
    public static int[] groupRect(View v, List<Integer> members) {
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        int n = v.nodeCount(), ch = v.cardHeight();
        for (int i : members) {
            if (i >= n) continue;
            int nx = v.nodeX(i), ny = v.nodeY(i);
            x1 = Math.min(x1, nx); y1 = Math.min(y1, ny);
            x2 = Math.max(x2, nx + NodeCardRenderer.NW); y2 = Math.max(y2, ny + ch);
        }
        return new int[]{x1 - GPAD, y1 - GPAD - GBAND, x2 + GPAD, y2 + GPAD};
    }

    /** m192 组框（最底层：存储线/机器线/卡片全画其上）——世界坐标独立一次变换。
     *  @param gm    gid→成员（{@link #groupMembers}）
     *  @param gRect gid→矩形（{@link #groupRect}），键集须覆盖 gm 的键 */
    public static void drawFrames(GuiGraphics ctx, Font font, View v,
                                  java.util.Map<Integer, java.util.List<Integer>> gm, java.util.Map<Integer, int[]> gRect) {
        if (gm.isEmpty()) return;
        pushWorld(ctx, v); // = 主线 translate(panX, panY, 0) + scale(zoom)
        for (var ge : gm.entrySet()) {
            int[] r = gRect.get(ge.getKey());
            int fc = v.dragGid() == ge.getKey() ? SciSkin.ACCENT : SciSkin.GROUP_FRM; // 拖动中框提亮（主线 CYAN=SciSkin.ACCENT）
            ctx.fill(r[0], r[1] + GBAND, r[2], r[3], SciSkin.GROUP_FILL);
            SciSkin.hGrad(ctx, r[0], r[1], r[2], r[1] + GBAND,
                    SciSkin.withAlpha(SciSkin.GROUP_FRM, 0.50f), SciSkin.withAlpha(SciSkin.GROUP_FRM, 0.08f));
            ctx.fill(r[0], r[1], r[2], r[1] + 1, fc);
            ctx.fill(r[0], r[3] - 1, r[2], r[3], fc);
            ctx.fill(r[0], r[1], r[0] + 1, r[3], fc);
            ctx.fill(r[2] - 1, r[1], r[2], r[3], fc);
            ctx.fill(r[0], r[1] + GBAND - 1, r[2], r[1] + GBAND, SciSkin.withAlpha(fc, 0.45f));
            ctx.drawString(font, v.groupNames().getOrDefault(ge.getKey(), "组" + ge.getKey())
                    + " ×" + ge.getValue().size(), r[0] + 5, r[1] + 4, SciSkin.TXT_HI, false);
        }
        ctx.pose().popPose();
    }

    /** 世界坐标一次变换（= 主线机器层 translate(panX, panY, 0) + scale(zoom)）。
     *  m511 放开 public：1.20.1 机器线层（非归并线 + {@link WireBundler#drawMachineBundles}）也包进同一形状的世界矩阵——
     *  主线机器线本就在世界矩阵下传 pxScale=zoom（m197 线宽封顶按世界单位算），1.20.1 m488 起在屏幕坐标层传 zoom 是半搬；
     *  调用方用完自己 {@code ctx.pose().popPose()}。 */
    public static void pushWorld(GuiGraphics ctx, View v) {
        double zoom = v.zoom();
        PoseStack mg = ctx.pose();
        mg.pushPose();
        mg.translate(-v.viewLeft() * zoom, -v.viewTop() * zoom, 0);
        mg.scale((float) zoom, (float) zoom, 1);
    }

    /** m192 选中高亮（青描边；服务端删点后失效下标顺手清）——m508 自主线原 765~775 搬入，
     *  调用方在机器层之后调（原文位置同）；卡高走 NodeCardRenderer.NH（两代同值，描边只框卡体不含升级格行，原文同）。 */
    public static void drawSelection(GuiGraphics ctx, View v, java.util.Set<Integer> selected) {
        if (selected.isEmpty()) return;
        int n = v.nodeCount();
        selected.removeIf(i -> i >= n);
        pushWorld(ctx, v);
        for (int i : selected) {
            int nx = v.nodeX(i), ny = v.nodeY(i);
            int NW = NodeCardRenderer.NW, NH = NodeCardRenderer.NH;
            int selC = SciSkin.termAccent(); // m203 选中描边随主题
            ctx.fill(nx - 3, ny - 3, nx + NW + 3, ny - 2, selC);
            ctx.fill(nx - 3, ny + NH + 2, nx + NW + 3, ny + NH + 3, selC);
            ctx.fill(nx - 3, ny - 3, nx - 2, ny + NH + 3, selC);
            ctx.fill(nx + NW + 2, ny - 3, nx + NW + 3, ny + NH + 3, selC);
        }
        ctx.pose().popPose();
    }

    /** m192 框选矩形（淡面+亮边）——m508 自主线原 776~786 搬入；入参=框选两角的世界坐标。 */
    public static void drawSelectBox(GuiGraphics ctx, View v, double boxX0, double boxY0, double boxX1, double boxY1) {
        pushWorld(ctx, v);
        int bx1 = (int) Math.min(boxX0, boxX1), by1 = (int) Math.min(boxY0, boxY1);
        int bx2 = (int) Math.max(boxX0, boxX1), by2 = (int) Math.max(boxY0, boxY1);
        int boxC = SciSkin.termAccent(); // m203 框选随主题
        ctx.fill(bx1, by1, bx2, by2, SciSkin.withAlpha(boxC, 0.10f));
        ctx.fill(bx1, by1, bx2, by1 + 1, boxC);
        ctx.fill(bx1, by2 - 1, bx2, by2, boxC);
        ctx.fill(bx1, by1, bx1 + 1, by2, boxC);
        ctx.fill(bx2 - 1, by1, bx2, by2, boxC);
        ctx.pose().popPose();
    }
}
