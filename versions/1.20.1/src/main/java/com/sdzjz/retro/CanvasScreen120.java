package com.sdzjz.retro;

import com.sdzjz.node.CanvasGraphState;
import com.mojang.math.Axis;
import com.sdzjz.client.SciSkinPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
    private CanvasGraphState g = new CanvasGraphState(); // 最近快照的本地像（只读）
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

    // ===== m192 画布分组：Shift框选 + 组框（数据/协议见 m191；配置 canvasGroupsEnabled 总开关）=====
    // m508（真移植 A5c）：状态字段与事件段照主线 StructureCoreScreen m192 原文逐句对着写（1.20.1 事件签名/EditBox 差异
    // 只在方法签名层，逻辑体一句不改）；组框/选中描边/框选矩形三件绘制走 m507 共用件 GroupFrameRenderer。
    private final java.util.LinkedHashSet<Integer> selected = new java.util.LinkedHashSet<>(); // 选中节点下标（客户端瞬态）
    private boolean boxSelecting = false;
    private double boxX0, boxY0, boxX1, boxY1;        // 框选矩形（世界坐标）
    private int dragGid = -1;                         // 拖动中的组
    private double dragGidWx, dragGidWy;              // 组拖动起点（世界坐标）
    private int dragGidDx = 0, dragGidDy = 0;         // 已应用整数位移（松手随包发出）
    private final java.util.HashMap<Integer, int[]> dragGidSnap = new java.util.HashMap<>(); // 成员坐标快照：快照+增量绝对写，中途被服务端全量同步覆盖也自愈
    private int renameGid = -1;                       // 重命名中的组（>=0 = 小窗开着）
    private EditBox renameField;
    private static final int GBAND = com.sdzjz.client.GroupFrameRenderer.GBAND; // 组框标题带高（世界单位，共用件原值）

    // 右键菜单——m509：机制走 xplat CanvasMenu（主线 m148 3A 菜单整段下沉两代共用一份），m508 那份"最小实现"退役；
    // 本屏只留一行实例与同签名转发壳，条目装配（组菜单/画布菜单）照主线原文 addMenu。
    private final com.sdzjz.client.CanvasMenu cmenu = new com.sdzjz.client.CanvasMenu();

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
        String keepG = renameField != null ? renameField.getValue() : ""; // m192 组重命名输入框（主线原句；1.20.1 EditBox 构造签名同 1.21）
        this.renameField = new EditBox(this.font, 0, 0, 184, 14, Component.empty()); // 占位仅narration不上屏，empty保literal棘轮
        this.renameField.setMaxLength(24);
        this.renameField.setValue(keepG);
        sendQuery();
    }

    // ===== m192 分组：世界坐标换算 + 几何/操作助手（主线 wmx/wmy/wnx/wny/groupsOn/createGroupFromSelection/openGroupMenu/
    // openRename/closeRename/confirmRename/renderRename 逐句对位；be()/menu.blockPos() 在本世代=快照像 g / menu.corePos）=====
    private double wmx(double mx) { return viewX + mx / zoom; } // 屏幕→世界（主线 (mx - panX) / zoom 的本世代记法）
    private double wmy(double my) { return viewY + my / zoom; }

    /** m196 拖动中覆盖：单卡读拖动幽灵、组成员读快照+增量——渲染永不读会被同步打回的快照坐标，根治"闪两个位置来回跳"。 */
    private int wnx(int i) {
        if (i == dragIndex) return (int) Math.round(dragCx);
        if (dragGid >= 0) { int[] sn = dragGidSnap.get(i); if (sn != null) return sn[0] + dragGidDx; }
        return nodeCx(i);
    }
    private int wny(int i) {
        if (i == dragIndex) return (int) Math.round(dragCy);
        if (dragGid >= 0) { int[] sn = dragGidSnap.get(i); if (sn != null) return sn[1] + dragGidDy; }
        return nodeCy(i);
    }

    private boolean groupsOn() { return com.sdzjz.config.SdzjzConfig.get().canvasGroupsEnabled; }

    /** 命中组框标题带（世界坐标）→ gid；无=-1。主线右键/左键两处内联的"遍历 groupMembers→groupRect→带内判定"同一段。 */
    private int groupBandAt(double wx, double wy) {
        if (!groupsOn()) return -1;
        for (var ge : com.sdzjz.client.GroupFrameRenderer.groupMembers(groupView).entrySet()) {
            int[] r = com.sdzjz.client.GroupFrameRenderer.groupRect(groupView, ge.getValue());
            if (wx >= r[0] && wx <= r[2] && wy >= r[1] && wy <= r[1] + GBAND) return ge.getKey();
        }
        return -1;
    }

    /** 建组：当前选中集发服务端（≥2 台才发，服务端还会再验一遍），发完清选。 */
    private void createGroupFromSelection() {
        List<Integer> ms = new java.util.ArrayList<>();
        for (int i : selected) if (i >= 0 && i < g.machineNodes.size()) ms.add(i);
        if (ms.size() < 2) return;
        ClientNet120.toServer(new NodePayloads120.NodeGroup(menu.corePos, -1, "", ms));
        sendQuery();
        selected.clear();
    }

    /** 组菜单（右键标题带）：重命名 / 解散（解散纯视觉，机器与连线不动）。 */
    private void openGroupMenu(int gid, int atX, int atY) {
        clearMenu();
        cmenu.title(g.groupNames.getOrDefault(gid, "组" + gid));
        addMenu("重命名组…", mt("group_rename"), 0, () -> openRename(gid)); // m313 用户图标
        addMenu("解散该组", mt("group_disband"), 1,
                () -> { ClientNet120.toServer(new NodePayloads120.NodeGroup(menu.corePos, gid, "", java.util.List.of())); sendQuery(); });
        addMenu("取消", (ItemStack) null, 2, () -> {});
        openMenu(atX, atY);
    }

    private void openRename(int gid) {
        renameGid = gid;
        renameField.setValue(g.groupNames.getOrDefault(gid, ""));
        this.setFocused(renameField);
        renameField.setFocused(true);
    }

    private void closeRename() {
        renameGid = -1;
        renameField.setFocused(false);
    }

    private void confirmRename() {
        String nm = renameField.getValue().trim();
        if (renameGid >= 0 && !nm.isEmpty()) {
            ClientNet120.toServer(new NodePayloads120.NodeGroup(menu.corePos, renameGid, nm, java.util.List.of()));
            sendQuery();
        }
        closeRename();
    }

    /** 重命名小窗（主线 renderRename 原文：每帧摆位再渲染；SciSkin.drawCard 两代共用 m483）。 */
    private void renderRename(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int w = 200, h = 58, px = (this.width - w) / 2, py = (this.height - h) / 2;
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 400); // m202 同病同修：抬z防卡内物品穿透
        com.sdzjz.client.SciSkin.drawCard(ctx, px, py, w, h, com.sdzjz.client.SciSkin.FRAME);
        ctx.drawString(this.font, "重命名组（回车确认·Esc取消）", px + 8, py + 7, com.sdzjz.client.SciSkin.TXT_HI, false);
        renameField.setX(px + 8);
        renameField.setY(py + 26);
        renameField.render(ctx, mouseX, mouseY, delta);
        ctx.pose().popPose();
    }

    // ================= 右键菜单 =================
    // m509：机制整段在 xplat CanvasMenu（主线 m148 3A 工艺两代同一份：弹入动画/行图标/悬停缓动/危险项红系/点选音），
    // 以下全是与主线 StructureCoreScreen 同名同签名的转发壳——A7 装节点菜单条目时可直接粘主线 addMenu 原文。
    private void openMenu(int x, int y) { cmenu.openMenu(x, y, width - SIDEBAR_W, this.height); } // 右缘钳在侧栏左（主线 workRight()）

    private void clearMenu() { cmenu.clearMenu(); }

    private void addMenu(String label, Runnable action) { cmenu.addMenu(label, action); }

    private void addMenu(String label, ItemStack icon, Runnable action) { cmenu.addMenu(label, icon, action); }

    /** m148：style 0=普通 1=危险(红字红条) 2=组首(上方分隔线)。 */
    private void addMenu(String label, ItemStack icon, int style, Runnable action) { cmenu.addMenu(label, icon, style, action); }

    /** m313：贴图图标行（作者设计的 8 张按钮图，32² 源画 16×16；本世代资源目录里只放已用到的几张，第 19 闸 mt 路径普查看着）。 */
    private void addMenu(String label, net.minecraft.resources.ResourceLocation tex, int style, Runnable action) { cmenu.addMenu(label, tex, style, action); }

    private static ItemStack mi(net.minecraft.world.item.Item it) { return new ItemStack(it); }

    private static net.minecraft.resources.ResourceLocation mt(String name) { return com.sdzjz.client.CanvasMenu.mt(name); } // m313 菜单贴图路径唯一口径

    private void renderMenu(GuiGraphics ctx, int mouseX, int mouseY) { cmenu.renderMenu(ctx, this.font, mouseX, mouseY); }

    private void sendQuery() {
        ClientNet120.toServer(new CanvasPayloads120.CanvasQuery(menu.corePos));
    }

    /** 快照到货（render 线程直达）：应用层有界——节点数超硬顶整包拒绝（m455 稿红线落点）。 */
    void acceptSnapshot(CanvasPayloads120.CanvasSnapshot snapshot) {
        if (snapshot.render() == null) return;
        CanvasGraphState next = new CanvasGraphState();
        next.readRenderNbt(snapshot.render(), null, java.util.Map.of(), () -> { });
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
        if (cmenu.isOpen() || renameGid >= 0) return true; // m509 modal 吞滚轮（主线 mouseScrolled 同一行的本世代两项：菜单/重命名窗）
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
        if (renameGid >= 0) { // m192 重命名小窗 modal：窗内点进字段，窗外点=关
            int rw = 200, rh = 58, rpx = (this.width - rw) / 2, rpy = (this.height - rh) / 2;
            if (renameField.mouseClicked(mouseX, mouseY, button)) return true;
            if (mouseX < rpx || mouseX > rpx + rw || mouseY < rpy || mouseY > rpy + rh) closeRename();
            return true;
        }
        if (cmenu.isOpen()) { cmenu.click(mouseX, mouseY, button); return true; } // 右键菜单 modal：左键点行=执行（点选音），其余=关（m103 吞穿透口径，主线同一份 CanvasMenu.click）
        if (button == 0 && overMap(mouseX, mouseY)) { // m490 小地图跳转：点中的画布点移到工作区中心
            double[] w = com.sdzjz.client.MinimapRenderer.jumpTarget(mapView, mapX(), mapY(), mouseX, mouseY);
            viewX = w[0] - (width - SIDEBAR_W) / 2.0 / zoom;
            viewY = w[1] - (height - 16) / 2.0 / zoom;
            return true;
        }
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
            if (button == 0 && groupsOn() && hasShiftDown()) { // m192 Shift+点卡=切换选中
                if (!selected.remove(Integer.valueOf(hit))) selected.add(hit);
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
        double wx = wmx(mouseX), wy = wmy(mouseY);
        int band = groupBandAt(wx, wy); // m192 组框标题带（卡片先判所以盖在带上的卡不误触）
        if (band >= 0 && button == 1) { openGroupMenu(band, (int) mouseX, (int) mouseY); return true; } // 右键 → 组菜单（重命名/解散）
        if (band >= 0 && button == 0) { // 左键=整组拖动（成员坐标快照，拖动时快照+增量绝对写，松手一包发增量）
            dragGid = band; dragGidWx = wx; dragGidWy = wy; dragGidDx = 0; dragGidDy = 0;
            dragGidSnap.clear();
            var gm = com.sdzjz.client.GroupFrameRenderer.groupMembers(groupView);
            for (int i : gm.getOrDefault(band, java.util.List.of())) if (i < g.machineNodes.size())
                dragGidSnap.put(i, new int[]{nodeCx(i), nodeCy(i)});
            return true;
        }
        if (button == 1 && mouseY >= 16) { // 右键空白 → 画布菜单（主线同款；本世代无整理布局）
            clearMenu();
            cmenu.title("画布"); // m148
            if (groupsOn() && selected.size() >= 2) // m192 框选后从这里成组（另有 G 键快捷）
                addMenu("打组所选(" + selected.size() + "台)", mi(net.minecraft.world.item.Items.LEAD), this::createGroupFromSelection);
            if (groupsOn() && !selected.isEmpty())
                addMenu("清除选择", mi(net.minecraft.world.item.Items.GLASS_PANE), selected::clear);
            addMenu("重置视角", mi(net.minecraft.world.item.Items.SPYGLASS), () -> { viewX = 0; viewY = 0; zoom = 1.0f; }); // 主线 setViewInstant(0,0,1.0) 的本世代记法
            addMenu("取消", (ItemStack) null, 2, () -> {});
            openMenu((int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && groupsOn() && hasShiftDown()) { // m192 Shift+拖空白=框选加选（普通左拖=平移，行为不变）
            boxSelecting = true;
            boxX0 = boxX1 = wx; boxY0 = boxY1 = wy;
            return true;
        }
        if (button == 0) selected.clear(); // m192 左键点空白=清选（不吞事件，随后拖动=平移照旧）
        if (button == 0) { panning = true; if (linkMode) linkFrom = -1; return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragGid >= 0) { // m192 组拖动松手：一包发总增量（服务端批量改+单次同步，防N连发全量同步）
            for (var en : dragGidSnap.entrySet()) // m196 覆盖失效前按快照+增量本地定格——否则松手到快照回来之间闪回旧位置
                if (en.getKey() < g.machineNodes.size()) {
                    var t = g.machineNodes.get(en.getKey()).getOrCreateTag();
                    t.putInt("nx", en.getValue()[0] + dragGidDx);
                    t.putInt("ny", en.getValue()[1] + dragGidDy);
                }
            if (dragGidDx != 0 || dragGidDy != 0) {
                ClientNet120.toServer(new NodePayloads120.NodeGroupMove(menu.corePos, dragGid, dragGidDx, dragGidDy));
                sendQuery();
            }
            dragGid = -1;
            dragGidSnap.clear();
            return true;
        }
        if (button == 0 && boxSelecting) { // m192 框选收口：矩形∩卡体=加选（Shift起手天然是加选语义）
            boxSelecting = false;
            double bx1 = Math.min(boxX0, boxX1), by1 = Math.min(boxY0, boxY1);
            double bx2 = Math.max(boxX0, boxX1), by2 = Math.max(boxY0, boxY1);
            for (int i = 0; i < g.machineNodes.size(); i++) {
                int nx = wnx(i), ny = wny(i);
                if (nx < bx2 && nx + NW > bx1 && ny < by2 && ny + NH > by1) selected.add(i);
            }
            return true;
        }
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
        if (cmenu.isOpen() || renameGid >= 0) return true; // m509 modal 吞拖动（主线 mouseDragged 同一行的本世代两项）
        if (button == 0 && dragGid >= 0) { // m192 组拖动：每帧快照+增量绝对写（渲染读 wnx/wny 的快照+增量，快照回来也自愈）
            dragGidDx = (int) (wmx(mouseX) - dragGidWx);
            dragGidDy = (int) (wmy(mouseY) - dragGidWy);
            return true;
        }
        if (boxSelecting) { boxX1 = wmx(mouseX); boxY1 = wmy(mouseY); return true; } // m192 框选拉框
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
        Long stHover = cmenu.isEmpty() ? storageAt(mouseX, mouseY) : null; // m458 存储节点悬停：名称+方位（m509 菜单开着不画，主线 menuLabels.isEmpty() 口）
        if (stHover != null) {
            var bp = net.minecraft.core.BlockPos.of(stHover);
            ctx.renderTooltip(font, List.of(Component.translatable("block.sdzjz.storage_core"),
                    Component.literal(bp.getX() + ", " + bp.getY() + ", " + bp.getZ())), Optional.empty(), mouseX, mouseY);
        }
        Integer hover = cmenu.isEmpty() ? nodeAt(mouseX, mouseY) : null; // m493 悬停详情走共用件（与主线同一份：状态/周期/基础产量/产出表）；m509 菜单开着不画
        if (hover != null) {
            ItemStack st = g.machineNodes.get(hover);
            java.util.List<Component> tip = new java.util.ArrayList<>(
                    com.sdzjz.client.NodeTooltip.lines(st, cardHost.nodeStatus(hover), cardHost.running()));
            String why = hover < g.nodeReason.size() ? g.nodeReason.get(hover) : "";
            if (!why.isEmpty()) tip.add(Component.literal(why)); // 本世代特有：阻塞原因整句（m464 灯表词条）
            ctx.renderTooltip(font, tip, Optional.empty(), mouseX, mouseY);
        }
        if (cmenu.isOpen()) renderMenu(ctx, mouseX, mouseY);
        if (renameGid >= 0) renderRename(ctx, mouseX, mouseY, delta); // m192 组重命名小窗压最上层
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameGid >= 0) { // m192 重命名窗：回车确认 / Esc取消，其余进输入框（先于 super，否则 E 键会关屏）
            if (keyCode == 257 || keyCode == 335) { confirmRename(); return true; }
            if (keyCode == 256) { closeRename(); return true; }
            renameField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (cmenu.isOpen() && keyCode == 256) { clearMenu(); return true; }
        if (keyCode == 71 && groupsOn() && selected.size() >= 2 && !cmenu.isOpen()) { // m192 G=打组所选（无输入框聚焦时才到这）
            createGroupFromSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renameGid >= 0) return renameField.charTyped(chr, modifiers); // m192
        return super.charTyped(chr, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, width, height, SciSkinPalette.BACKDROP); // 满窗不透明（m452 透明教训口径）
        int step = Math.max(6, Math.round(GRID_STEP * zoom));   // 网格
        int offX = (int) (Math.floorMod(Math.round(-viewX * zoom), step));
        int offY = (int) (Math.floorMod(Math.round(-viewY * zoom), step));
        for (int x = offX; x < width; x += step) ctx.fill(x, 0, x + 1, height, SciSkinPalette.CELL);
        for (int y = offY; y < height; y += step) ctx.fill(0, y, width, y + 1, SciSkinPalette.CELL);
        if (com.sdzjz.config.SdzjzConfig.get().canvasGroupsEnabled) { // m507 m192 组框（最底层：存储线/机器线/卡片全画其上）——与主线同一份代码
            var gm = com.sdzjz.client.GroupFrameRenderer.groupMembers(groupView);
            if (!gm.isEmpty()) {
                java.util.HashMap<Integer, int[]> gRect = new java.util.HashMap<>();
                for (var ge : gm.entrySet()) gRect.put(ge.getKey(), com.sdzjz.client.GroupFrameRenderer.groupRect(groupView, ge.getValue()));
                com.sdzjz.client.GroupFrameRenderer.drawFrames(ctx, this.font, groupView, gm, gRect);
            }
        }
        for (int i = 0; i < g.storageEdges.size(); i++) { // m458 机器↔存储边（产出 ON / 供料 GOLD）
            long[] e = g.storageEdges.get(i);
            int m = (int) e[0];
            int[] sp = g.storageNodePos.get(e[1]);
            if (m >= g.machineNodes.size() || sp == null) continue;
            // m492：锚点算法照主线原文（m184 选缘看几何 + m352 柱心分高 + 存储卡接口在**卡底**）：
            //  产出=机器近侧缘水平出线 → 垂直向上接卡底左收料口；供料=卡底右供料口垂直下发 → 水平接机器近侧缘。
            //  m488 我自己推的「机器右缘 → 存储卡中心」是错的：忽略了选缘、也把存储卡接口当成了中心。
            boolean dualPe = com.sdzjz.config.SdzjzConfig.get().nodeDualSidePorts; // m352 机器端锚分高
            double mnx = wnx(m), mny = wny(m); // m508：拖动覆盖统一走 wnx/wny（单卡幽灵 + 组成员快照+增量）
            float mysO = (float) sy(mny + (dualPe ? NH / 2.0 - 7 : NH / 2.0)); // 产出=出口柱心
            float mysI = (float) sy(mny + (dualPe ? NH / 2.0 + 7 : NH / 2.0)); // 供料=进口柱心
            float mcxS = (float) sx(mnx + NW / 2.0);                            // 机器中心屏幕 x：选缘看几何
            float stx = (float) sx(stX(e[1], sp)), sty = (float) sy(stY(e[1], sp));
            float stW = (float) (24 * zoom), stH = (float) (24 * zoom);         // 存储卡 24×24
            if (e[2] == 0) { // 机器→存储（产出）
                boolean er = stx + stW * 0.25f >= mcxS; // 收料口在机器右侧→右缘出线
                float mxs = (float) sx(mnx + (er ? NW : 0));
                com.sdzjz.client.WireRenderer.drawWire(ctx, mxs, mysO, er ? 1 : -1, 0,
                        stx + stW * 0.25f, sty + stH + 2, 0, -1, SciSkinPalette.ON, 1f); // 主线此处传 1f（屏幕坐标层）
            } else {         // 存储→机器（供料）
                boolean fr = stx + stW * 0.75f >= mcxS; // 供料口在机器右侧→从右缘进
                float mxi = (float) sx(mnx + (fr ? NW : 0));
                com.sdzjz.client.WireRenderer.drawWire(ctx, stx + stW * 0.75f, sty + stH + 2, 0, 1,
                        mxi, mysI, fr ? -1 : 1, 0, SciSkinPalette.GOLD, 1f); // 同上
            }
        }
        for (int[] c : g.connections) { // 连线（节点中心→节点中心，真对角）
            if (c[0] >= g.machineNodes.size() || c[1] >= g.machineNodes.size()) continue; // 快照途中拓扑变化防御
            // m492：照主线原文（m352 柱心分高 + m184 选缘看几何：下游在右=右缘出左缘进，
            //  在左=左缘出右缘进，不再绕背后大圈）。
            boolean dual = com.sdzjz.config.SdzjzConfig.get().nodeDualSidePorts;
            int dyO = dual ? NH / 2 - 7 : NH / 2, dyI = dual ? NH / 2 + 7 : NH / 2;
            double ax0 = wnx(c[0]), ay0 = wny(c[0]) + dyO; // m508：机器线随拖动幽灵/组拖动走（原读快照坐标，拖时线不跟卡）
            double bx0 = wnx(c[1]), by0 = wny(c[1]) + dyI;
            boolean fwd = bx0 >= ax0;
            float ax = (float) sx(ax0 + (fwd ? NW : 0)), bx = (float) sx(bx0 + (fwd ? 0 : NW));
            int dir = fwd ? 1 : -1;
            com.sdzjz.client.WireRenderer.drawWire(ctx, ax, (float) sy(ay0), dir, 0,
                    bx, (float) sy(by0), dir, 0, SciSkinPalette.ACCENT, (float) zoom);
        }
        for (int i = 0; i < g.machineNodes.size(); i++) { // 节点卡：24×24 框+图标+状态灯环
            int x = (int) sx(wnx(i)), y = (int) sy(wny(i)); // 拖动幽灵位（m508 组成员快照+增量同走 wnx/wny）
            if (x < -32 || y < -32 || x > width + 8 || y > height + 8) continue; // 视口裁剪
            int status = i < g.nodeStatus.size() ? g.nodeStatus.get(i) : 0;
            int ring = switch (status) { // 灯环取色（口径同蓝本状态灯：0待机 1绿 2黄 3红）
                case 1 -> SciSkinPalette.ON;
                case 2 -> SciSkinPalette.GOLD;
                case 3 -> SciSkinPalette.RED;
                default -> SciSkinPalette.OFF_GRAY;
            };
            // m484（真移植）：节点卡整张走共用渲染件——与主线**同一份代码**（xplat NodeCardRenderer）：
            // 卡面工艺+分类配色顶条+标题底带+进出口柱与「进」「出」字标+阶位图标放大与前缀变色+
            // 机器名(自动截断)+状态灯点(绿灯呼吸)+六族逻辑节点各自的读数行。m483 我自己编的灯点画法退役。
            com.sdzjz.client.NodeCardRenderer.drawNode(ctx, this.font, cardHost, i, x, y, g.machineNodes.get(i));
            if (linkMode && i == linkFrom) { // 连线首端高亮：卡外描一圈强调色（不覆盖卡面工艺）
                int NW = com.sdzjz.client.NodeCardRenderer.NW, NH = com.sdzjz.client.NodeCardRenderer.NH;
                ctx.fill(x - 2, y - 2, x + NW + 2, y - 1, SciSkinPalette.ACCENT);
                ctx.fill(x - 2, y + NH + 1, x + NW + 2, y + NH + 2, SciSkinPalette.ACCENT);
                ctx.fill(x - 2, y - 1, x - 1, y + NH + 1, SciSkinPalette.ACCENT);
                ctx.fill(x + NW + 1, y - 1, x + NW + 2, y + NH + 1, SciSkinPalette.ACCENT);
            }
        }
        if (groupsOn()) com.sdzjz.client.GroupFrameRenderer.drawSelection(ctx, groupView, selected); // m192 选中高亮（m508 共用件）
        if (boxSelecting) com.sdzjz.client.GroupFrameRenderer.drawSelectBox(ctx, groupView, boxX0, boxY0, boxX1, boxY1); // m192 框选矩形
        for (int i = 0; i < g.storageEndpoints.size(); i++) { // m458 存储节点卡（图标=存储核心）
            long pl = g.storageEndpoints.get(i)[0];
            int[] sp = g.storageNodePos.get(pl);
            if (sp == null) continue;
            int x = (int) sx(stX(pl, sp)), y = (int) sy(stY(pl, sp));
            if (x < -32 || y < -32 || x > width + 8 || y > height + 8) continue;
            com.sdzjz.client.SciSkin.drawCard(ctx, x, y, 24, 24, SciSkinPalette.CELL_FRM); // m483 同上
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
        if (mapOpen) com.sdzjz.client.MinimapRenderer.render(ctx, this.font, mapView, mapX(), mapY()); // m490 小地图
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
    /** m484：把画布状态适配成共用渲染件要的四项数据（与主线 hostOf 同形）。 */
    private final com.sdzjz.client.NodeCardRenderer.Host cardHost = new com.sdzjz.client.NodeCardRenderer.Host() {
        @Override public int nodeStatus(int i) { return i < g.nodeStatus.size() ? g.nodeStatus.get(i) : 0; }
        @Override public String nodeReason(int i) { return i < g.nodeReason.size() ? g.nodeReason.get(i) : ""; }
        @Override public int outCount(int i) {
            int n = 0;
            for (int[] c : g.connections) if (c[0] == i) n++;
            return n;
        }
        @Override public boolean running() { return true; } // 本世代画布无停机开关（世代取舍：核心恒运行）
    };

    // ===== m490（真移植·画布视觉第五件）：小地图两代共用（xplat client/MinimapRenderer）=====
    // 本世代原来完全没有小地图；主线 renderMinimap/mapGeom/mapJump 整段搬，几何走 View 口。
    private boolean mapOpen = true;

    private final com.sdzjz.client.MinimapRenderer.View mapView = new com.sdzjz.client.MinimapRenderer.View() {
        @Override public double viewLeft() { return viewX; }
        @Override public double viewTop() { return viewY; }
        @Override public double zoom() { return zoom; }
        @Override public int workLeft() { return 0; }
        @Override public int workTop() { return 16; } // 本世代顶栏高 16
        @Override public int workRight() { return width - SIDEBAR_W; }
        @Override public int workBottom() { return height; }
        @Override public int nodeCount() { return g.machineNodes.size(); }
        @Override public int nodeX(int i) { return wnx(i); } // m508：小地图也随拖动覆盖
        @Override public int nodeY(int i) { return wny(i); }
        @Override public net.minecraft.world.item.ItemStack nodeStack(int i) { return g.machineNodes.get(i); }
    };

    // ===== m507（真移植·A5b）：画布分组组框两代共用（xplat client/GroupFrameRenderer）=====
    // 本世代原来完全没有组框；主线 m192 组框段 + groupMembers/groupRect 整段搬，几何走 View 口。
    // 只读不写：框选/G 键/拖组/组菜单随 A5c 接 m506 建好的 NodeGroup/NodeGroupMove 两包。
    private final com.sdzjz.client.GroupFrameRenderer.View groupView = new com.sdzjz.client.GroupFrameRenderer.View() {
        @Override public double viewLeft() { return viewX; }
        @Override public double viewTop() { return viewY; }
        @Override public double zoom() { return zoom; }
        @Override public int nodeCount() { return g.machineNodes.size(); }
        @Override public net.minecraft.world.item.ItemStack nodeStack(int i) { return g.machineNodes.get(i); }
        @Override public int nodeX(int i) { return wnx(i); } // 拖动覆盖（单卡幽灵/组成员快照+增量，主线 wnx 的 m196 语义）
        @Override public int nodeY(int i) { return wny(i); }
        @Override public int cardHeight() { return NH; } // 本世代无升级系统（m464）→卡下无升级格行，主线此处 NH+26
        @Override public java.util.Map<Integer, String> groupNames() { return g.groupNames; } // 快照像里的组元数据（m506 服务端已下沉，随 CanvasSnapshot 原样到）
        @Override public int dragGid() { return dragGid; } // m508 拖动中的组框提亮
    };

    private int mapX() { return width - SIDEBAR_W - com.sdzjz.client.MinimapRenderer.MAP_W - 8; }
    private int mapY() { return height - 6 - com.sdzjz.client.MinimapRenderer.MAP_H; }

    private boolean overMap(double mx, double my) {
        return mapOpen && mx >= mapX() && mx <= mapX() + com.sdzjz.client.MinimapRenderer.MAP_W
                && my >= mapY() && my <= mapY() + com.sdzjz.client.MinimapRenderer.MAP_H;
    }

    private static final int NW = com.sdzjz.client.NodeCardRenderer.NW, NH = com.sdzjz.client.NodeCardRenderer.NH;

    private int nodeCx(int i) { return g.machineNodes.get(i).hasTag() ? g.machineNodes.get(i).getTag().getInt("nx") : 0; } // m474 键位归位（原 xc 撞 NodeTags 抽取累计）
    private int nodeCy(int i) { return g.machineNodes.get(i).hasTag() ? g.machineNodes.get(i).getTag().getInt("ny") : 0; }

    private Integer nodeAt(double mx, double my) {
        for (int i = g.machineNodes.size() - 1; i >= 0; i--) { // 后画在上，倒序命中
            double x = sx(nodeCx(i)), y = sy(nodeCy(i));
            // m507 顺手修：命中框还是 m484 之前的 24×24（卡早已长成 NW×NH=100×52），只有卡左上角一小块能悬停/拖动/右键；
            // 改成整卡命中（屏幕坐标乘 zoom），与主线 hoveredNode 的 NW×NH 口径一致（主线另有 +26 升级格行，本世代无）。
            if (mx >= x && mx < x + NW * zoom && my >= y && my < y + NH * zoom) return i;
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
