package com.sdzjz.retro;

import com.mojang.math.Axis;
import com.sdzjz.client.SciSkinPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * m456（C2-④a）：画布屏第一片——**只读视口**：平移（左键拖空处）/缩放（滚轮，指针为锚）/暗底
 * 网格/节点卡（图标+SciSkinPalette 框+状态灯环 0..3）/真对角连线（pose 旋转+fill，Axis 1.20.1
 * 在位）。节点坐标读栈 NBT xc/yc（NodeTags 谱系键同源）。数据=轮询快照（开屏首查+20t+
 * 应用层 1024 节点硬顶整包拒绝，m455 稿红线）。
 *
 * <p>蓝本五大区裁剪记档（④b/④c 逐片接，非漏抄）：节点操作/小地图/浮层四件（改名/设置/帮助/
 * 右键菜单）/存储端点列。1.20.1 客户端签名差同 m448 三处口径（mouseScrolled 三参等）。
 */
public final class CanvasScreen120 extends AbstractContainerScreen<StructureCoreMenu120> {

    private static final int GRID_STEP = 24; // 画布世界格距（缩放前）
    private static final int SIDEBAR_W = 30; // m457 机器库侧栏宽
    private CanvasGraphState120 g = new CanvasGraphState120(); // 最近快照的本地像（只读）
    private double viewX = 0, viewY = 0; // 视口左上对应的画布坐标
    private float zoom = 1.0f;
    private int refreshTicker = 0;
    private boolean panning = false;
    // ===== m457 交互态 =====
    private int placingSlot = -1;      // 侧栏点选的背包槽（≥0=放置模式，跟指针画幽灵图标）
    private ItemStack placingIcon = ItemStack.EMPTY;
    private int dragIndex = -1;        // 左键按住的节点（本地幽灵位，松手才发 NodeMove）
    private double dragCx, dragCy;
    private boolean dragMoved = false;
    private boolean linkMode = false;  // 顶栏按钮切换
    private int linkFrom = -1;         // 连线模式第一端（机器下标）
    private long dragStorage = Long.MIN_VALUE; // m458 拖动中的存储节点（posLong）
    private double dragStX, dragStY;
    private int sidebarScroll = 0; // m459 修③：侧栏首可见行（滚轮区域化 m103）

    public CanvasScreen120(StructureCoreMenu120 menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = width;   // 画布屏满窗（蓝本同性：不是槽位屏）
        this.imageHeight = height;
        this.leftPos = 0;
        this.topPos = 0;
        sendQuery();
    }

    private void sendQuery() {
        ClientNet120.toServer(new CanvasPayloads120.CanvasQuery(menu.corePos));
    }

    /** 快照到货（render 线程直达）：应用层有界——节点数超硬顶整包拒绝（m455 稿红线落点）。 */
    void acceptSnapshot(CanvasPayloads120.CanvasSnapshot snapshot) {
        if (snapshot.render() == null) return;
        CanvasGraphState120 next = new CanvasGraphState120();
        next.readRenderNbt(snapshot.render(), java.util.Map.of(), () -> { });
        if (next.machineNodes.size() > CanvasPayloads120.MAX_NODES) return; // 超界整包拒绝
        this.g = next;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++refreshTicker >= 20) { refreshTicker = 0; sendQuery(); }
    }

    // ===== 视口换算：画布坐标 ↔ 屏幕像素 =====
    private double sx(double cx) { return (cx - viewX) * zoom; }
    private double sy(double cy) { return (cy - viewY) * zoom; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { // 1.20.1 三参
        float next = Math.max(0.35f, Math.min(2.5f, zoom * (delta > 0 ? 1.15f : 1 / 1.15f)));
        if (next != zoom) { // 指针为锚：缩放前后指针下画布点不动（m103 谱系体验口径）
            double anchorCx = viewX + mouseX / zoom, anchorCy = viewY + mouseY / zoom;
            zoom = next;
            viewX = anchorCx - mouseX / zoom;
            viewY = anchorCy - mouseY / zoom;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY < 16 && mouseX >= width - SIDEBAR_W - 64 && mouseX < width - SIDEBAR_W - 8) {
            linkMode = !linkMode; // 顶栏连线按钮（区域化，m103 口径）
            linkFrom = -1;
            return true;
        }
        if (mouseX >= width - SIDEBAR_W) { // m457 机器库侧栏：点选进放置模式
            if (button == 0 && mouseY >= 20) {
                int row = (int) ((mouseY - 20) / 26);
                var lib = library();
                int entry = sidebarScroll + row; // m459 修③：命中按窗内行+滚动偏移
                if (row >= 0 && row < visCap() && entry < lib.size()) {
                    placingSlot = lib.get(entry)[0];
                    placingIcon = minecraft.player.getInventory().getItem(placingSlot).copyWithCount(1);
                }
            }
            return true;
        }
        if (placingSlot >= 0) { // 放置模式：左键落位/右键取消
            if (button == 0) {
                int cx = (int) Math.round(viewX + mouseX / zoom) - 12;
                int cy = (int) Math.round(viewY + mouseY / zoom) - 12;
                ClientNet120.toServer(new NodePayloads120.NodeAdd(menu.corePos, placingSlot, cx, cy));
                sendQuery();
            }
            placingSlot = -1;
            placingIcon = ItemStack.EMPTY;
            return true;
        }
        Long stHit = storageAt(mouseX, mouseY); // m458 存储节点：连线第二端 / 普通拖动
        if (stHit != null) {
            if (linkMode && linkFrom >= 0 && button == 0) {
                ClientNet120.toServer(new StoragePayloads120.StorageLink(menu.corePos, linkFrom, stHit));
                sendQuery();
                linkFrom = -1;
                return true;
            }
            if (button == 0 && !linkMode) {
                dragStorage = stHit;
                int[] sp = g.storageNodePos.get(stHit);
                dragStX = sp[0];
                dragStY = sp[1];
                dragMoved = false;
                return true;
            }
            return true;
        }
        Integer hit = nodeAt(mouseX, mouseY);
        if (hit != null) {
            if (button == 1) { // 右键摘回
                ClientNet120.toServer(new NodePayloads120.NodeRemove(menu.corePos, hit));
                sendQuery();
                if (linkFrom == hit) linkFrom = -1;
                return true;
            }
            if (linkMode) { // 连线模式：A→B（已有同向对=断，toggle 语义）
                if (linkFrom < 0) { linkFrom = hit; return true; }
                if (linkFrom != hit) {
                    boolean cut = false;
                    for (int[] c : g.connections) if (c[0] == linkFrom && c[1] == hit) { cut = true; break; }
                    ClientNet120.toServer(new NodePayloads120.NodeLink(menu.corePos, linkFrom, hit, cut));
                    sendQuery();
                }
                linkFrom = -1;
                return true;
            }
            dragIndex = hit; // 普通模式：按住拖动（本地幽灵，松手结算）
            dragCx = nodeCx(hit);
            dragCy = nodeCy(hit);
            dragMoved = false;
            return true;
        }
        if (button == 0) { panning = true; if (linkMode) linkFrom = -1; return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            panning = false;
            if (dragIndex >= 0) {
                if (dragMoved) {
                    ClientNet120.toServer(new NodePayloads120.NodeMove(menu.corePos, dragIndex,
                            (int) Math.round(dragCx), (int) Math.round(dragCy)));
                    sendQuery();
                }
                dragIndex = -1;
            }
            if (dragStorage != Long.MIN_VALUE) { // m458
                if (dragMoved) {
                    ClientNet120.toServer(new StoragePayloads120.StorageNodeMove(menu.corePos, dragStorage,
                            (int) Math.round(dragStX), (int) Math.round(dragStY)));
                    sendQuery();
                }
                dragStorage = Long.MIN_VALUE;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragIndex >= 0 && button == 0) {
            dragCx += dragX / zoom;
            dragCy += dragY / zoom;
            dragMoved = true;
            return true;
        }
        if (dragStorage != Long.MIN_VALUE && button == 0) { // m458
            dragStX += dragX / zoom;
            dragStY += dragY / zoom;
            dragMoved = true;
            return true;
        }
        if (panning && button == 0) {
            viewX -= dragX / zoom;
            viewY -= dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 背包机器库：{槽位, 无重复}——同物品只列首个槽（占位收藏品同类无差别）。 */
    private java.util.List<int[]> library() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getItem() instanceof RetroMachineItems.RetroMachineItem && seen.add(st.getItem()))
                out.add(new int[]{i});
        }
        return out; // m459 修③：返回全表，开窗归绘制侧（原截断=超一屏机器永远够不着）
    }

    /** 侧栏可见行数。 */
    private int visCap() { return Math.max(1, (height - 24) / 26); }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx); // 1.20.1 单参
        super.render(ctx, mouseX, mouseY, delta);
        Long stHover = storageAt(mouseX, mouseY); // m458 存储节点悬停：名称+方位
        if (stHover != null) {
            var bp = net.minecraft.core.BlockPos.of(stHover);
            ctx.renderTooltip(font, List.of(Component.translatable("block.sdzjz.storage_core"),
                    Component.literal(bp.getX() + ", " + bp.getY() + ", " + bp.getZ())), Optional.empty(), mouseX, mouseY);
        }
        Integer hover = nodeAt(mouseX, mouseY); // 悬停详情（机器名+状态原因）
        if (hover != null) {
            ItemStack st = g.machineNodes.get(hover);
            String why = hover < g.nodeReason.size() ? g.nodeReason.get(hover) : "";
            Component line2 = why.isEmpty() ? Component.translatable("sdzjz.canvas.hint")
                    : Component.literal(why);
            ctx.renderTooltip(font, List.of(st.getHoverName(), line2), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, width, height, SciSkinPalette.BACKDROP); // 满窗不透明（m452 透明教训口径）
        int step = Math.max(6, Math.round(GRID_STEP * zoom));   // 网格
        int offX = (int) (Math.floorMod(Math.round(-viewX * zoom), step));
        int offY = (int) (Math.floorMod(Math.round(-viewY * zoom), step));
        for (int x = offX; x < width; x += step) ctx.fill(x, 0, x + 1, height, SciSkinPalette.CELL);
        for (int y = offY; y < height; y += step) ctx.fill(0, y, width, y + 1, SciSkinPalette.CELL);
        for (int i = 0; i < g.storageEdges.size(); i++) { // m458 机器↔存储边（产出 ON / 供料 GOLD）
            long[] e = g.storageEdges.get(i);
            int m = (int) e[0];
            int[] sp = g.storageNodePos.get(e[1]);
            if (m >= g.machineNodes.size() || sp == null) continue;
            double x1 = sx((m == dragIndex ? dragCx : nodeCx(m)) + 12), y1 = sy((m == dragIndex ? dragCy : nodeCy(m)) + 12);
            double x2 = sx(stX(e[1], sp) + 12), y2 = sy(stY(e[1], sp) + 12);
            line(ctx, x1, y1, x2, y2, e[2] == 0 ? SciSkinPalette.ON : SciSkinPalette.GOLD);
        }
        for (int[] c : g.connections) { // 连线（节点中心→节点中心，真对角）
            if (c[0] >= g.machineNodes.size() || c[1] >= g.machineNodes.size()) continue; // 快照途中拓扑变化防御
            double x1 = sx(nodeCx(c[0]) + 12), y1 = sy(nodeCy(c[0]) + 12);
            double x2 = sx(nodeCx(c[1]) + 12), y2 = sy(nodeCy(c[1]) + 12);
            line(ctx, x1, y1, x2, y2, SciSkinPalette.ACCENT);
        }
        for (int i = 0; i < g.machineNodes.size(); i++) { // 节点卡：24×24 框+图标+状态灯环
            int x = (int) sx(i == dragIndex ? dragCx : nodeCx(i)), y = (int) sy(i == dragIndex ? dragCy : nodeCy(i)); // 拖动幽灵位
            if (x < -32 || y < -32 || x > width + 8 || y > height + 8) continue; // 视口裁剪
            int status = i < g.nodeStatus.size() ? g.nodeStatus.get(i) : 0;
            int ring = switch (status) { // 灯环取色（口径同蓝本状态灯：0待机 1绿 2黄 3红）
                case 1 -> SciSkinPalette.ON;
                case 2 -> SciSkinPalette.GOLD;
                case 3 -> SciSkinPalette.RED;
                default -> SciSkinPalette.OFF_GRAY;
            };
            ctx.fill(x - 1, y - 1, x + 25, y + 25, (linkMode && i == linkFrom) ? SciSkinPalette.ACCENT : ring); // 连线首端高亮
            ctx.fill(x, y, x + 24, y + 24, SciSkinPalette.BTN_FACE);
            ctx.renderItem(g.machineNodes.get(i), x + 4, y + 4);
        }
        for (int i = 0; i < g.storageEndpoints.size(); i++) { // m458 存储节点卡（图标=存储核心）
            long pl = g.storageEndpoints.get(i)[0];
            int[] sp = g.storageNodePos.get(pl);
            if (sp == null) continue;
            int x = (int) sx(stX(pl, sp)), y = (int) sy(stY(pl, sp));
            if (x < -32 || y < -32 || x > width + 8 || y > height + 8) continue;
            ctx.fill(x - 1, y - 1, x + 25, y + 25, SciSkinPalette.CELL_FRM);
            ctx.fill(x, y, x + 24, y + 24, SciSkinPalette.CELL);
            ctx.renderItem(STORAGE_ICON, x + 4, y + 4);
        }
        int sbX = width - SIDEBAR_W; // m457 机器库侧栏
        ctx.fill(sbX, 16, width, height, SciSkinPalette.BTN_FACE);
        ctx.fill(sbX, 16, sbX + 1, height, SciSkinPalette.FRAME);
        var lib = library();
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, lib.size() - visCap()))); // 背包变动后钳回
        for (int r = 0; r < visCap() && sidebarScroll + r < lib.size(); r++) { // m459 修③：开窗绘制
            int y = 20 + r * 26;
            boolean hov = mouseX >= sbX && mouseY >= y && mouseY < y + 24;
            ctx.fill(sbX + 3, y, sbX + 27, y + 24, hov ? SciSkinPalette.HOVER : SciSkinPalette.CELL);
            ctx.renderItem(minecraft.player.getInventory().getItem(lib.get(sidebarScroll + r)[0]), sbX + 7, y + 4);
        }
        if (lib.size() > visCap()) { // 超窗提示条（比例滑块，面板同形制）
            int track = visCap() * 26, thumb = Math.max(8, track * visCap() / lib.size());
            int off = (track - thumb) * sidebarScroll / Math.max(1, lib.size() - visCap());
            ctx.fill(width - 2, 20 + off, width, 20 + off + thumb, SciSkinPalette.FRAME);
        }
        ctx.fill(0, 0, width, 15, SciSkinPalette.BTN_FACE); // 顶栏：标题+连线按钮+节点计数
        ctx.fill(0, 15, width, 16, SciSkinPalette.FRAME);
        ctx.drawString(font, title, 6, 4, SciSkinPalette.TXT_HI, false);
        int btnX = width - SIDEBAR_W - 64;
        ctx.fill(btnX, 2, btnX + 56, 13, linkMode ? SciSkinPalette.ACCENT : SciSkinPalette.BTN_FRM);
        ctx.fill(btnX + 1, 3, btnX + 55, 12, SciSkinPalette.BTN_FACE);
        ctx.drawString(font, Component.translatable("sdzjz.canvas.link"), btnX + 5, 3,
                linkMode ? SciSkinPalette.ACCENT : SciSkinPalette.TXT, false);
        ctx.drawString(font, Component.translatable("sdzjz.canvas.nodes", g.machineNodes.size()),
                width - SIDEBAR_W - 160, 4, SciSkinPalette.SUB, false);
        if (placingSlot >= 0 && !placingIcon.isEmpty()) ctx.renderItem(placingIcon, mouseX - 8, mouseY - 8); // 放置幽灵
    }

    @Override
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) { } // 满窗自绘，压掉默认双标签

    private static final ItemStack STORAGE_ICON = new ItemStack(RetroBlocks.STORAGE_CORE); // m458

    /** 存储节点画布坐标（拖动中取本地幽灵位）。 */
    private double stX(long pl, int[] sp) { return pl == dragStorage ? dragStX : sp[0]; }
    private double stY(long pl, int[] sp) { return pl == dragStorage ? dragStY : sp[1]; }

    /** 命中存储节点（倒序无关，端点无遮叠序）。 */
    private Long storageAt(double mx, double my) {
        for (long[] e : g.storageEndpoints) {
            int[] sp = g.storageNodePos.get(e[0]);
            if (sp == null) continue;
            double x = sx(stX(e[0], sp)), y = sy(stY(e[0], sp));
            if (mx >= x && mx < x + 24 && my >= y && my < y + 24) return e[0];
        }
        return null;
    }

    /** 节点画布坐标：读栈 NBT xc/yc（键同源 NodeTags 谱系；缺键=0,0 落画布原点可见可拖走）。 */
    private int nodeCx(int i) { return g.machineNodes.get(i).hasTag() ? g.machineNodes.get(i).getTag().getInt("nx") : 0; } // m474 键位归位（原 xc 撞 NodeTags 抽取累计）
    private int nodeCy(int i) { return g.machineNodes.get(i).hasTag() ? g.machineNodes.get(i).getTag().getInt("ny") : 0; }

    private Integer nodeAt(double mx, double my) {
        for (int i = g.machineNodes.size() - 1; i >= 0; i--) { // 后画在上，倒序命中
            double x = sx(nodeCx(i)), y = sy(nodeCy(i));
            if (mx >= x && mx < x + 24 && my >= y && my < y + 24) return i;
        }
        return null;
    }

    /** 真对角线：pose 平移+Z 轴旋转后 fill 一条 len×2 横带（Axis 1.20.1 在位，蓝本 addVertex
     *  助手是 1.21 API 不可用——m452b 普查结论的落地对位）。 */
    private static void line(GuiGraphics ctx, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        ctx.pose().pushPose();
        ctx.pose().translate((float) x1, (float) y1, 0);
        ctx.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        ctx.fill(0, -1, Math.round(len), 1, color);
        ctx.pose().popPose();
    }
}
