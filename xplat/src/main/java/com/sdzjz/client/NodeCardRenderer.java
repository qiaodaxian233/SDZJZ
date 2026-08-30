package com.sdzjz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * m484（真移植·画布视觉按件搬·第二件）：**节点卡渲染两代共用一份**——
 * 主线 {@code StructureCoreScreen.drawNode}（216 行）整段下沉，主线调用点改为转发，
 * 1.20.1 画布屏直接调用。作者点名的「结构核心完全不一样」，根子就在这 216 行没搬。
 *
 * <p><b>为什么是整段搬而不是照着重画</b>：m483 我在 1.20.1 侧自己编了个状态灯点的坐标，
 * 而主线本来就有 {@code drawStatusDot}（带呼吸动效的）。**手上有原版还去重新设计，
 * 既慢又必然不一样**——这正是「仿品」的生产方式。本刀起：有原版的，一律整段搬。
 *
 * <p><b>三处世代差的处理</b>：
 * <ul>
 *   <li><b>宿主数据</b>（节点状态灯/阻塞原因/出线条数/是否在运行）→ {@link Host} 小接口，
 *       两代各自的屏/BE 实现四行；</li>
 *   <li><b>机器身份判定</b>：主线原文用 {@code instanceof CropFarmItem} 等 1.21 世代物品类，
 *       改走 {@code NodeTags.defOf(st)} 与 {@code Machines} 常量的**引用同一性**
 *       （m472 绞杀者第五刀已证两代通用）；本世代没建的机器自然不命中，走通用分支；</li>
 *   <li><b>物品 id 解析</b>（画白名单/传感器目标的小图标）→ {@link SciSkin.Gfx#item(String)}
 *       世代口（1.21 的 {@code ResourceLocation.parse} vs 1.20.1 的构造器）。</li>
 * </ul>
 *
 * <p>常量 NW/NH 与配色一律取自共用出口，两代同源。
 */
public final class NodeCardRenderer {

    private NodeCardRenderer() { }

    public static final int NW = 100, NH = 52;

    private static final int TXT = SciSkin.TXT;
    private static final int SUB = SciSkin.SUB;
    private static final int ON = SciSkin.ON;
    private static final int CYAN = SciSkin.ACCENT;
    private static final int NODEFRM = SciSkin.FRAME;

    /** 宿主数据口：节点卡要显示的、来自本世代结构核心的四项。两代各实现四行。 */
    public interface Host {
        /** 状态灯：0 待机 1 绿 2 黄 3 红。 */
        int nodeStatus(int i);
        /** 阻塞原因（黄/红灯时显示）；无则空串。 */
        String nodeReason(int i);
        /** 该节点的出线条数（分配器卡面「均分 → N 路」用）。 */
        int outCount(int i);
        /** 核心是否在运行（停机时状态灯一律灰）。 */
        boolean running();
    }

    /** m86 分类配色：逻辑节点紫 / 作物绿 / 耗料机橙 / 其余青。 */
    public static int nodeAccent(ItemStack st) {
        if (com.sdzjz.node.NodeTags.isFilter(st) || com.sdzjz.node.NodeTags.isSensor(st)
                || com.sdzjz.node.NodeTags.isSwitch(st) || com.sdzjz.node.NodeTags.isDistributor(st)) return 0xFFB06AE8;
        if (is(st, com.sdzjz.machine.Machines.CROP_FARM)) return 0xFF63D06A;
        com.sdzjz.machine.MachineDef d = com.sdzjz.node.NodeTags.defOf(st);
        if (d != null && d.consumesInputs()) return 0xFFE8963C;
        return CYAN;
    }

    /** m472 身份判定：走 defOf 的引用同一性，两代通用（本世代没建的机器自然不命中）。 */
    private static boolean is(ItemStack st, com.sdzjz.machine.MachineDef def) {
        return def != null && com.sdzjz.node.NodeTags.defOf(st) == def;
    }

    public static String fmtNum(long n) {
        if (n < 10000L) return String.valueOf(n);
        if (n < 1_000_000L) return String.format("%.1fK", n / 1000.0);
        if (n < 1_000_000_000L) return String.format("%.1fM", n / 1_000_000.0);
        return String.format("%.1fB", n / 1_000_000_000.0);
    }

    public static String fitText(Font font, String t, int maxW) {
        if (font.width(t) <= maxW) return t;
        while (!t.isEmpty() && font.width(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** 状态灯点（绿灯带呼吸动效）；核心停机一律灰。 */
    public static void drawStatusDot(GuiGraphics ctx, int x, int y, int stat, boolean running) {
        int c;
        if (!running) c = 0xFF3A424E;
        else c = switch (stat) {
            case 1 -> ((165 + (int) (88 * Math.sin(System.currentTimeMillis() / 300.0))) << 24) | 0x33D07A;
            case 2 -> SciSkin.GOLD;
            case 3 -> SciSkin.RED;
            default -> 0xFF3A424E;
        };
        ctx.fill(x - 1, y - 1, x + 7, y + 7, 0xFF06101C);
        ctx.fill(x, y, x + 6, y + 6, c);
    }

    private static void icon(GuiGraphics ctx, String id, int x, int y) {
        try {
            ctx.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(SciSkin.gfxItem(id))), x, y);
        } catch (Exception ignored) { }
    }

    /**
     * 画一张节点卡（主线 drawNode 原文整段，m180 家法：方法体逐句照搬，只把三处世代差换成口）。
     *
     * @param i 节点下标 @param x,y 卡左上角 @param st 节点栈
     */
    public static void drawNode(GuiGraphics ctx, Font font, Host host, int i, int x, int y, ItemStack st) {
        drawBase(ctx, font, host, i, x, y, st);
        if (!drawBody(ctx, font, host, i, x, y, st)) drawGeneric(ctx, font, host, i, x, y, st);
    }

    /**
     * 骨架层：卡面+分类顶条+标题底带+进出口柱与字标+阶位图标与前缀+机器名+状态灯点。
     * 与本世代有哪些机器无关，两代逐位相同。
     */
    public static void drawBase(GuiGraphics ctx, Font font, Host host, int i, int x, int y, ItemStack st) {
        SciSkin.drawCard(ctx, x, y, NW, NH, NODEFRM); // m120 投影+渐变面+角刻
        ctx.fill(x, y, x + NW, y + 3, nodeAccent(st)); // m86 分类配色
        ctx.fill(x, y + 3, x + NW, y + 15, SciSkin.withAlpha(SciSkin.BACKDROP, 0.25f)); // m120 标题读数底带
        if (com.sdzjz.config.SdzjzConfig.get().nodeDualSidePorts) { // m352 双侧进出口
            int oyP = y + NH / 2 - 10, iyP = y + NH / 2 + 4;
            ctx.fill(x - 4, oyP, x + 2, oyP + 6, ON);           ctx.fill(x + NW - 2, oyP, x + NW + 4, oyP + 6, ON);
            ctx.fill(x - 4, iyP, x + 2, iyP + 6, CYAN);         ctx.fill(x + NW - 2, iyP, x + NW + 4, iyP + 6, CYAN);
            ctx.drawString(font, "出", x - 16, oyP - 1, ON, false);   ctx.drawString(font, "出", x + NW + 7, oyP - 1, ON, false);
            ctx.drawString(font, "进", x - 16, iyP - 1, CYAN, false); ctx.drawString(font, "进", x + NW + 7, iyP - 1, CYAN, false);
        } else {
            boolean swP = com.sdzjz.config.SdzjzConfig.get().nodePortsSwapped; // m341 进出口互换
            int inX = swP ? x + NW - 2 : x - 4, outX = swP ? x - 4 : x + NW - 2;
            ctx.fill(inX, y + NH / 2 - 3, inX + 6, y + NH / 2 + 3, CYAN);
            ctx.fill(outX, y + NH / 2 - 3, outX + 6, y + NH / 2 + 3, ON);
            boolean inRight = inX > x + NW / 2; // m342 字标画卡外
            ctx.drawString(font, "进", inRight ? inX + 9 : inX - 12, y + NH / 2 - 4, CYAN, false);
            ctx.drawString(font, "出", inRight ? outX - 12 : outX + 9, y + NH / 2 - 4, ON, false);
        }
        int mt = com.sdzjz.node.NodeTags.machineTier(st); // m123 阶位视觉：图标放大+前缀变色
        float isc = 2f + 0.45f * mt;
        var msi = ctx.pose();
        msi.pushPose();
        msi.translate(x + 6, y + 16 - (isc - 2f) * 10, 0);
        msi.scale(isc, isc, 1f);
        ctx.renderItem(st, 0, 0);
        msi.popPose();
        String pre = mt <= 0 ? "" : mt == 1 ? "超级·" : mt == 2 ? "神级·" : "GM·";
        int preW = pre.isEmpty() ? 0 : font.width(pre);
        if (preW > 0) ctx.drawString(font, pre, x + 6, y + 6,
                mt == 1 ? SciSkin.GOLD : mt == 2 ? 0xFFB06AE8 : SciSkin.RED, false);
        String name = st.getHoverName().getString();
        if (font.width(name) > NW - 22 - preW) {
            while (name.length() > 1 && font.width(name + "…") > NW - 22 - preW) name = name.substring(0, name.length() - 1);
            name = name + "…";
        }
        ctx.drawString(font, name, x + 6 + preW, y + 6, TXT, false);
        drawStatusDot(ctx, x + NW - 11, y + 5, host.nodeStatus(i), host.running());
    }

    /**
     * 专有行层：六族逻辑节点各自的卡面读数行。
     * @return true=已画（调用方不要再画别的），false=本类型不归共用件管，调用方走自己的分支
     */
    public static boolean drawBody(GuiGraphics ctx, Font font, Host host, int i, int x, int y, ItemStack st) {
        if (com.sdzjz.node.NodeTags.isDistributor(st)) {
            int outs = host.outCount(i);
            ctx.drawString(font, "均分 → " + outs + " 路", x + 44, y + 26, CYAN, false);
            ctx.drawString(font, outs == 0 ? "拉出线到下游" : "余数轮转", x + 44, y + 38, SUB, false);
            return true;
        }
        if (com.sdzjz.node.NodeTags.isExtractor(st)) { // m154 启停显示 + m159 速率与已抽读数
            boolean onX = com.sdzjz.node.NodeTags.extractorOn(st);
            int bfx = onX ? ON : SciSkin.OFF_GRAY;
            ctx.fill(x + 43, y + 23, x + 91, y + 45, bfx);
            ctx.fill(x + 44, y + 24, x + 90, y + 44, onX ? SciSkin.ON_DARK : 0xFF141A24);
            ctx.drawString(font, onX ? "● 抽取中" : "○ 待命", x + 48, y + 30, onX ? ON : SUB, false);
            String siX = com.sdzjz.node.NodeTags.sensorItem(st);
            if (!siX.isEmpty()) { // m160 自动启停行：图标 <阈值 [−][+]
                icon(ctx, siX, x + 4, y + 44);
                long thX = com.sdzjz.node.NodeTags.sensorThreshold(st);
                ctx.drawString(font, (com.sdzjz.node.NodeTags.sensorLess(st) ? "<" : ">") + fmtNum(thX),
                        x + 22, y + 48, CYAN, false);
                ctx.fill(x + 62, y + 46, x + 76, y + 59, SciSkin.BTN_FACE);
                ctx.fill(x + 79, y + 46, x + 93, y + 59, SciSkin.BTN_FACE);
                ctx.drawString(font, "-", x + 67, y + 49, TXT, false);
                ctx.drawString(font, "+", x + 84, y + 49, TXT, false);
            } else {
                long xr = com.sdzjz.node.NodeTags.extractorRate(st);
                long xc = com.sdzjz.node.NodeTags.extractorCount(st);
                ctx.drawString(font, fitText(font, fmtNum(xr) + "/轮" + (xc > 0 ? " 抽" + fmtNum(xc) : "×升级"), NW - 48),
                        x + 44, y + 48, SUB, false);
            }
            return true;
        }
        if (com.sdzjz.node.NodeTags.isSwitch(st)) {
            boolean on = com.sdzjz.node.NodeTags.switchOn(st);
            int bfr = on ? ON : SciSkin.OFF_GRAY;
            ctx.fill(x + 43, y + 23, x + 91, y + 45, bfr);
            ctx.fill(x + 44, y + 24, x + 90, y + 44, on ? SciSkin.ON_DARK : 0xFF141A24);
            ctx.drawString(font, on ? "● 开" : "○ 关", x + 55, y + 30, on ? ON : SUB, false);
            return true;
        }
        if (com.sdzjz.node.NodeTags.isTrash(st)) { // m150 卡面
            long ate = com.sdzjz.node.NodeTags.trashCount(st);
            int tfN = com.sdzjz.node.NodeTags.filterList(st).size();
            ctx.drawString(font, tfN > 0 ? "[白名单·" + tfN + "]" : "[虚空]", x + 44, y + 26, SciSkin.RED_SOFT, false);
            ctx.drawString(font, ate > 0 ? "已吞 " + fmtNum(ate) : tfN > 0 ? "只吞名单内" : "连啥吞啥", x + 44, y + 38, SUB, false);
            return true;
        }
        if (com.sdzjz.node.NodeTags.isFilter(st)) {
            boolean black = com.sdzjz.node.NodeTags.filterBlacklist(st);
            ctx.drawString(font, black ? "[黑名单]" : "[白名单]", x + 44, y + 26, black ? SciSkin.GOLD : ON, false);
            List<String> fl = com.sdzjz.node.NodeTags.filterList(st);
            if (fl.isEmpty()) {
                ctx.drawString(font, "右键配置", x + 44, y + 38, SUB, false);
            } else {
                int shown = Math.min(3, fl.size());
                for (int k = 0; k < shown; k++) icon(ctx, fl.get(k), x + 42 + k * 18, y + 34);
                if (fl.size() > 3) ctx.drawString(font, "+" + (fl.size() - 3), x + 42 + 54, y + 38, SUB, false);
            }
            return true;
        }
        if (com.sdzjz.node.NodeTags.isSensor(st)) {
            String si = com.sdzjz.node.NodeTags.sensorItem(st);
            if (si.isEmpty()) {
                ctx.drawString(font, "直通(未配置)", x + 44, y + 26, SUB, false);
                ctx.drawString(font, "右键配置", x + 44, y + 38, SUB, false);
            } else {
                icon(ctx, si, x + 40, y + 20);
                long th = com.sdzjz.node.NodeTags.sensorThreshold(st);
                boolean less = com.sdzjz.node.NodeTags.sensorLess(st);
                ctx.drawString(font, (less ? "<" : ">") + fmtNum(th) + " 放行", x + 58, y + 24, CYAN, false);
                ctx.fill(x + 57, y + 36, x + 71, y + 49, SciSkin.BTN_FACE); // [−]
                ctx.fill(x + 74, y + 36, x + 88, y + 49, SciSkin.BTN_FACE); // [+]
                ctx.drawString(font, "-", x + 62, y + 39, TXT, false);
                ctx.drawString(font, "+", x + 79, y + 39, TXT, false);
            }
            return true;
        }
        return false; // 不是六族逻辑节点：交回调用方（主线有作物/酿造/附魔/交易/区块族等独有分支）
    }

    /** 通用兜底行：数量 + 阻塞原因。调用方在 drawBody 回 false 且自己也没有专属画法时调。 */
    public static void drawGeneric(GuiGraphics ctx, Font font, Host host, int i, int x, int y, ItemStack st) {
        float isc = 2f + 0.45f * com.sdzjz.node.NodeTags.machineTier(st);
        // 通用机器：数量 + 阻塞原因行（主线的作物/酿造/附魔/交易/复制器/区块族徽章随各族到序补，
        // 本世代未建的机器 defOf 不命中，自然走到这里——与主线对同一台机器的画法一致）
        ctx.drawString(font, "×" + st.getCount(), x + Math.max(44, 10 + Math.round(16 * isc)), y + 26, CYAN, false);
        int stv = host.nodeStatus(i);
        String why = host.nodeReason(i); // m178 阻塞原因：黄/红灯时显在 y+38 行
        if ((stv == 2 || stv == 3) && !why.isEmpty()) {
            ctx.drawString(font, fitText(font, why, NW - 50), x + 44, y + 38, stv == 3 ? SciSkin.RED_SOFT : SciSkin.GOLD, false);
        }
    }
}
