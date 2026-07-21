package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.net.NodeMovePayload;
import com.sdzjz.net.NodeFilterPayload;
import com.sdzjz.net.NodeSensorPayload;
import com.sdzjz.net.NodeSwitchPayload;
import com.sdzjz.net.NodeRemovePayload;
import com.sdzjz.net.NodeTargetPayload;
import com.sdzjz.net.NodeUpgradePayload;
import com.sdzjz.net.StorageLinkPayload;
import com.sdzjz.net.StorageNodeMovePayload;
import com.sdzjz.registry.ModItems;
import com.sdzjz.screen.StructureCoreScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 结构核心画布界面（ComfyUI 式）：
 * 机器节点 + 存储/终端接口节点（连了几个显示几个），平移/缩放/拖动/双向连线/右键菜单。
 */
public class StructureCoreScreen extends HandledScreen<StructureCoreScreenHandler> {

    private static final int BACKDROP = 0xFF080B12;
    private static final Identifier FRAME = Identifier.of("sdzjz", "textures/gui/structure_core_canvas.png");
    private static final int GRID     = 0x22284A6B;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int ON       = 0xFF33D07A;
    private static final int CYAN     = 0xFF2EC4FF;
    private static final int NODEBG   = 0xE00A1626;
    private static final int NODEFRM  = 0xFF1C5A80;
    private static final int STORFRM  = 0xFF1E8A5A;   // 存储节点边框（绿）
    private static final int TERMFRM  = 0xFF7A5AC8;   // 数据终端边框（紫）
    private static final int OFFFRM   = 0xFF5A6470;   // 离线边框（灰）
    private static final int NW = 100, NH = 52;
    private static final int SW = 88, SH = 30;        // 存储节点尺寸（m92 紧凑化，用户点名"还是太大"）
    private static final String[] KIND = {"绑定", "有线", "无线", "卫星", "离线", "终端", "接口"};
    private static final Item[] UPG = { ModItems.SPEED_UPGRADE, ModItems.COUNT_UPGRADE, ModItems.PARALLEL_UPGRADE };

    private static final java.util.Map<BlockPos, double[]> VIEW = new java.util.HashMap<>();

    private double panX = 0, panY = 0, zoom = 1.0;
    private boolean libOpen = false; // m88 机器库侧栏
    private int libScroll = 0;
    private boolean busCollapsed = false; // m91：总线收起（拉线时自动展开）
    private boolean busVisible() { return !busCollapsed || linking; }
    private static float busScale = 1f;   // m93：总线大小滑块（0.8~1.25，跨开屏保留）
    private boolean busScaleDrag = false;
    private int bw() { return Math.round(SW * busScale); }
    private int bh() { return Math.round(SH * busScale); }
    private int busTrackX() { return workRight() - 152; }
    private static final int BUS_TRACK_W = 104;
    private void busScaleFromMouse(double mx) {
        busScale = (float) Math.max(0.8, Math.min(1.25, 0.8 + (mx - busTrackX()) / BUS_TRACK_W * 0.45));
    }

    // ===== m89：端点直发包缓存（BE 同步链实机不生效的最终修复）=====
    private static long endsCachePos = Long.MIN_VALUE;
    private static java.util.List<long[]> endsCache = java.util.List.of();
    private static java.util.List<String> endsDimsCache = java.util.List.of();
    private static java.util.List<String> busIdsCache = java.util.List.of();
    private static java.util.List<Long> busCountsCache = java.util.List.of();

    public static void applyEndsPayload(com.sdzjz.net.CanvasEndsPayload p) {
        java.util.List<long[]> e = new java.util.ArrayList<>();
        for (int i = 0; i < p.endPos().size() && i < p.endKind().size(); i++)
            e.add(new long[]{p.endPos().get(i), p.endKind().get(i)});
        endsCache = e;
        endsDimsCache = new java.util.ArrayList<>(p.endDim());
        busIdsCache = new java.util.ArrayList<>(p.busIds());
        busCountsCache = new java.util.ArrayList<>(p.busCounts());
        endsCachePos = p.pos().asLong();
    }

    private boolean cacheHit() {
        BlockPos p = this.handler.blockPos();
        return p != null && p.asLong() == endsCachePos;
    }

    private List<long[]> endsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? endsCache : be.storageEndpointsView();
    }

    private java.util.List<String> endDimsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? endsDimsCache : be.storageEndpointDimsView();
    }

    private java.util.List<String> busIdsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? busIdsCache : be.busTopIdsView();
    }

    private java.util.List<Long> busCountsOf(StructureCoreBlockEntity be) {
        return cacheHit() ? busCountsCache : be.busTopCountsView();
    }
    private int dragIndex = -1;
    private long dragStor = Long.MIN_VALUE;
    private double dragOffX, dragOffY;
    private boolean linking = false;
    private int linkFrom = -1;                        // 机器输出口起点
    private long linkStor = Long.MIN_VALUE;           // 存储供料口起点

    // 右键菜单
    private boolean menuOpen = false;
    private int menuX, menuY;
    private final List<String> menuLabels = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();
    private static final int MENU_W = 108, MENU_H = 16;

    // m110a 小地图（纯客户端零协议）
    private boolean mapOpen = false;
    private boolean mapDragging = false;
    private double[] mapGeomDrag;                     // 拖拽期间用抓取时的几何快照，防视口移动导致反馈抖动
    private static final int MAP_W = 148, MAP_H = 100;

    // 自动合成机目标选择器
    private int pickerNode = -1;
    private int pickerMode = 0; // 0=合成目标 1=过滤名单(多选) 2=传感器监测物品
    private TextFieldWidget pickerField;
    private List<Item> craftables;
    private List<Item> allItems;
    private final List<Item> pickerFiltered = new ArrayList<>();
    private static final int PICK_W = 226, PICK_H = 210, PICK_COLS = 10, PICK_ROWS = 7;

    public StructureCoreScreen(StructureCoreScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        BlockPos p = this.handler.blockPos();
        if (p != null && VIEW.containsKey(p)) {
            double[] v = VIEW.get(p);
            panX = v[0]; panY = v[1]; zoom = v[2];
        }
        this.addDrawableChild(new SciButton(8, this.height - 74, 90, 20, Text.literal("▶ 开机"), b -> click(0)));
        this.addDrawableChild(new SciButton(104, this.height - 74, 90, 20, Text.literal("■ 停止"), b -> click(1)));
        this.addDrawableChild(new SciButton(200, this.height - 74, 96, 20, Text.literal("★ 领取经验"), b -> click(2)));
        this.addDrawableChild(new SciButton(300, this.height - 74, 92, 20, Text.literal("整理布局"), b -> autoLayout())); // m85 概念图底栏
        this.addDrawableChild(new SciButton(396, this.height - 74, 92, 20, Text.literal("重置视角"), b -> { panX = 0; panY = 0; zoom = 1.0; }));
        this.addDrawableChild(new SciButton(132, 2, 60, 16, Text.literal("机器库"), b -> libOpen = !libOpen)); // m88
        this.addDrawableChild(new SciButton(196, 2, 44, 16, Text.literal("地图"), b -> mapOpen = !mapOpen)); // m110a
        int wr2 = this.width - Math.min(Math.max(120, this.width / 5), 220); // m86 顶条视图控制（概念图）
        this.addDrawableChild(new SciButton(wr2 - 170, 2, 16, 16, Text.literal("−"), b -> zoomBy(1 / 1.2)));
        this.addDrawableChild(new SciButton(wr2 - 106, 2, 16, 16, Text.literal("+"), b -> zoomBy(1.2)));
        this.addDrawableChild(new SciButton(wr2 - 86, 2, 78, 16, Text.literal("适应视图"), b -> fitView()));
        String keep = pickerField != null ? pickerField.getText() : "";
        this.pickerField = new TextFieldWidget(this.textRenderer, 0, 0, PICK_W - 16, 14, Text.literal("搜索"));
        this.pickerField.setChangedListener(t -> refilterPicker());
        this.pickerField.setText(keep);
    }

    @Override
    public void removed() {
        BlockPos p = this.handler.blockPos();
        if (p != null) VIEW.put(p, new double[]{panX, panY, zoom});
        super.removed();
    }

    private void click(int id) {
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, id);
        }
    }

    private StructureCoreBlockEntity be() {
        BlockPos p = this.handler.blockPos();
        if (p != null && this.client != null && this.client.world != null
                && this.client.world.getBlockEntity(p) instanceof StructureCoreBlockEntity c) return c;
        return null;
    }

    private double wmx(double mx) { return (mx - panX) / zoom; }
    private double wmy(double my) { return (my - panY) / zoom; }
    private int wnx(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) { return be.nodeX(nodes.get(i), 20 + (i % 6) * 112); }
    private int wny(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) { return be.nodeY(nodes.get(i), 20 + (i / 6) * 88); }
    /** m85：画布 UI 的右边界——右侧留空给 JEI/REI 物品栏（用户点名），所有屏幕锚定元素不越界。 */
    private int workRight() { return this.width - Math.min(Math.max(120, this.width / 5), 220); }

    /** m86 节点分类配色（概念图）：紫=逻辑 橙=加工(消耗输入) 绿=农场 青=生产(免费)。 */
    private int nodeAccent(ItemStack st) {
        if (StructureCoreBlockEntity.isFilter(st) || StructureCoreBlockEntity.isSensor(st)
                || StructureCoreBlockEntity.isSwitch(st) || StructureCoreBlockEntity.isDistributor(st)) return 0xFFB06AE8;
        if (st.getItem() instanceof com.sdzjz.item.CropFarmItem) return 0xFF63D06A;
        if (st.getItem() instanceof com.sdzjz.item.MachineItem mi && mi.def().consumesInputs()) return 0xFFE8963C;
        return CYAN;
    }

    /** m88 机器库：背包里可入画布的物品去重合并计数。 */
    private List<ItemStack> libItems() {
        List<ItemStack> out = new java.util.ArrayList<>();
        if (this.client == null || this.client.player == null) return out;
        java.util.LinkedHashMap<net.minecraft.item.Item, Integer> m2 = new java.util.LinkedHashMap<>();
        var inv = this.client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !nodeInsertable(st)) continue;
            m2.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        for (var e : m2.entrySet()) out.add(new ItemStack(e.getKey(), e.getValue()));
        return out;
    }

    private boolean nodeInsertable(ItemStack st) {
        return st.getItem() instanceof com.sdzjz.item.MachineItem
                || st.getItem() instanceof com.sdzjz.item.CropFarmItem
                || (st.getItem() instanceof com.sdzjz.item.CaptureCageItem && com.sdzjz.item.CaptureCageItem.isCaged(st))
                || StructureCoreBlockEntity.isFilter(st) || StructureCoreBlockEntity.isSensor(st)
                || StructureCoreBlockEntity.isSwitch(st) || StructureCoreBlockEntity.isDistributor(st);
    }

    /** m87：文本按宽度截断，尾加省略号——底栏任何文字不越 JEI 界。 */
    private String fitText(String t, int maxW) {
        if (this.textRenderer.getWidth(t) <= maxW) return t;
        while (!t.isEmpty() && this.textRenderer.getWidth(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** m86 视图控制：围绕工作区中心缩放。 */
    private void zoomBy(double f) {
        double nz = Math.max(0.4, Math.min(2.5, zoom * f));
        double cx = workRight() / 2.0, cy = this.height / 2.0;
        panX = cx - (cx - panX) * (nz / zoom);
        panY = cy - (cy - panY) * (nz / zoom);
        zoom = nz;
    }

    /** m86 适应视图：所有节点装进 总线下缘~底栏 之间的可视区。 */
    private void fitView() {
        StructureCoreBlockEntity be = be();
        if (be == null || be.nodes().isEmpty()) { panX = 0; panY = 0; zoom = 1.0; return; }
        List<ItemStack> nodes = be.nodes();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            minX = Math.min(minX, nx); minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx + NW); maxY = Math.max(maxY, ny + NH + 28); // 含升级格
        }
        int top = 118, bottom = this.height - 86, left = 12, right = workRight() - 12;
        double zw = (right - left) / (double) Math.max(1, maxX - minX);
        double zh = (bottom - top) / (double) Math.max(1, maxY - minY);
        zoom = Math.max(0.4, Math.min(2.5, Math.min(zw, zh)));
        panX = left + ((right - left) - (maxX - minX) * zoom) / 2 - minX * zoom;
        panY = top + ((bottom - top) - (maxY - minY) * zoom) / 2 - minY * zoom;
    }

    // m80：端点按用户点名改为顶部「存储总线」横排（屏幕坐标，永远可见），行满向下换行。
    private int busCols() { return Math.max(1, (workRight() - 24) / (bw() + 14)); }
    private int snx(StructureCoreBlockEntity be, long pl, int j) { return 14 + (j % busCols()) * (bw() + 14); }
    private int sny(StructureCoreBlockEntity be, long pl, int j) { return 44 + (j / busCols()) * (bh() + 12); }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        ctx.drawTexture(FRAME, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height);
        int step = 32;
        int ox = ((int) panX) % step, oy = ((int) panY) % step;
        for (int x = ox; x < this.width; x += step) ctx.fill(x, 34, x + 1, this.height, GRID);
        for (int y = 34 + oy; y < this.height; y += step) ctx.fill(0, y, this.width, y + 1, GRID);

        StructureCoreBlockEntity be = be();
        if (be == null) return;
        List<ItemStack> nodes = be.nodes();
        List<long[]> ends = endsOf(be);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(panX, panY, 0);
        m.scale((float) zoom, (float) zoom, 1);

        // 机器↔机器 连线
        for (int[] c : be.connections()) {
            if (c[0] < nodes.size() && c[1] < nodes.size()) {
                int ax = wnx(be, nodes, c[0]) + NW, ay = wny(be, nodes, c[0]) + NH / 2;
                int bx = wnx(be, nodes, c[1]),      by = wny(be, nodes, c[1]) + NH / 2;
                drawWire(ctx, ax, ay, bx, by, CYAN);
            }
        }
        if (linking && linkFrom >= 0 && linkFrom < nodes.size()) {
            int ax = wnx(be, nodes, linkFrom) + NW, ay = wny(be, nodes, linkFrom) + NH / 2;
            drawWire(ctx, ax, ay, (int) wmx(mouseX), (int) wmy(mouseY), 0xFF88E0FF);
        }
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            drawNode(ctx, be, i, nx, ny, nodes.get(i));
            if (!StructureCoreBlockEntity.isFilter(nodes.get(i)) && !StructureCoreBlockEntity.isSensor(nodes.get(i))
                    && !StructureCoreBlockEntity.isSwitch(nodes.get(i)) && !StructureCoreBlockEntity.isDistributor(nodes.get(i)))
                drawUpgradeSlots(ctx, be, nx, ny, nodes.get(i)); // 逻辑节点无升级格
            drawGear(ctx, nx + NW - 24, ny + 4); // m110b 齿轮=节点设置入口
            if (StructureCoreBlockEntity.nodePaused(nodes.get(i))) { // m110b 暂停视觉：压暗+角标
                ctx.fill(nx, ny, nx + NW, ny + NH, 0x66000000);
                ctx.drawText(this.textRenderer, "已暂停", nx + NW - 40, ny + NH - 12, 0xFFFFC84A, false);
            }
        }
        m.pop();

        // ===== 存储总线：顶部横排，屏幕坐标绘制（m91：可收起——收起只留一行库存条，拉线时自动展开）=====
        {
            int rows = Math.max(1, (ends.size() + busCols() - 1) / busCols());
            int bot = busVisible() ? 44 + rows * (bh() + 12) + 2 : 44;
            ctx.fill(8, 24, workRight() - 8, bot, 0x66060B14);
            ctx.fill(8, bot - 2, workRight() - 8, bot, 0xFF2E6E8E); // 总线底轨
            ctx.drawText(this.textRenderer, "存储总线（网络库存）", 14, 29, SUB, false);
            // 收起/展开开关（右上角小块）
            int tx = workRight() - 34;
            boolean th = mouseX >= tx && mouseX <= tx + 22 && mouseY >= 26 && mouseY <= 40;
            ctx.fill(tx - 1, 25, tx + 23, 41, th ? 0xFF3FA9D0 : 0xFF1E4258);
            ctx.fill(tx, 26, tx + 22, 40, 0xFF0D1B2C);
            ctx.drawText(this.textRenderer, busCollapsed ? "▼" : "▲", tx + 7, 29, th ? 0xFF9BE8FF : 0xFFB9D8E8, false);
            // m93：总线大小滑块（0.8x~1.25x）
            int trx = busTrackX();
            ctx.drawText(this.textRenderer, "尺寸", trx - 26, 29, SUB, false);
            ctx.fill(trx, 31, trx + BUS_TRACK_W, 35, 0xFF1E4258);
            int knx = trx + Math.round((busScale - 0.8f) / 0.45f * (BUS_TRACK_W - 6));
            ctx.fill(knx, 27, knx + 6, 39, busScaleDrag ? 0xFF9BE8FF : 0xFF3FA9D0);
            if (busVisible() && ends.isEmpty())
                ctx.drawText(this.textRenderer, "端点同步中…（2秒内应出现输出接口）", 14, 48, SUB, false);
            // m85：网络库存条（前10物品，服务端聚合同步）——概念图顶栏样式
            int cx = 132;
            java.util.List<String> bi = busIdsOf(be);
            java.util.List<Long> bc = busCountsOf(be);
            for (int k2 = 0; k2 < bi.size(); k2++) {
                ItemStack ist = new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(bi.get(k2))));
                if (ist.isEmpty()) continue;
                String cnt = fmtNum(bc.get(k2));
                int cw = 20 + this.textRenderer.getWidth(cnt) + 10;
                if (cx + cw > workRight() - 186) { ctx.drawText(this.textRenderer, "…", cx, 29, SUB, false); break; }
                ctx.drawItem(ist, cx, 22);
                ctx.drawText(this.textRenderer, cnt, cx + 18, 29, TXT, false);
                cx += cw;
            }
        }
        // 机器↔存储 定向连线：机器端做 画布→屏幕 换算，存储端已是屏幕坐标（m91：总线收起时不画）
        if (busVisible()) for (long[] e : be.storageEdgesView()) {
            int mi = (int) e[0];
            if (mi >= nodes.size()) continue;
            int j = endpointIndex(ends, e[1]);
            if (j < 0) continue; // 端点不在列表=不画，杜绝悬空线
            int sx = snx(be, e[1], j), sy = sny(be, e[1], j);
            int mys = (int) (panY + (wny(be, nodes, mi) + NH / 2.0) * zoom);
            if (e[2] == 0) { // 机器→存储（产出）：接到节点下缘左收料口
                int mxs = (int) (panX + (wnx(be, nodes, mi) + NW) * zoom);
                drawWire(ctx, mxs, mys, sx + 14, sy + bh() + 2, CYAN);
            } else {         // 存储→机器（供料）：从节点下缘右供料口出
                int mxi = (int) (panX + wnx(be, nodes, mi) * zoom);
                drawWire(ctx, sx + bw() - 14, sy + bh() + 2, mxi, mys, ON);
            }
        }
        if (linking && linkStor != Long.MIN_VALUE) {
            int j = endpointIndex(ends, linkStor);
            int sx = snx(be, linkStor, Math.max(j, 0)), sy = sny(be, linkStor, Math.max(j, 0));
            drawWire(ctx, sx + bw() - 14, sy + bh() + 2, mouseX, mouseY, 0xFF9BF0C0);
        }
        if (busVisible()) for (int j = 0; j < ends.size(); j++)
            drawStorageNode(ctx, be, ends.get(j), j, j < endDimsOf(be).size() ? endDimsOf(be).get(j) : "");
        if (mapOpen) renderMinimap(ctx); // m110a
    }

    // ================= m110a 小地图 =================
    private int mapX() { return workRight() - MAP_W - 8; }
    private int mapY() { return this.height - 84 - MAP_H; }
    private boolean inMap(double mx, double my) {
        return mapOpen && mx >= mapX() && mx <= mapX() + MAP_W && my >= mapY() && my <= mapY() + MAP_H;
    }

    /** 几何：{世界minX, 世界minY, 缩放}。范围 = 全部机器节点 ∪ 当前视口（视口白框永不丢出图外）。 */
    private double[] mapGeom(StructureCoreBlockEntity be) {
        List<ItemStack> nodes = be.nodes();
        double minX = (0 - panX) / zoom, minY = (34 - panY) / zoom;
        double maxX = (workRight() - panX) / zoom, maxY = ((this.height - 78) - panY) / zoom;
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            minX = Math.min(minX, nx); minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx + NW); maxY = Math.max(maxY, ny + NH + 28);
        }
        double sc = Math.min((MAP_W - 10) / Math.max(1, maxX - minX), (MAP_H - 10) / Math.max(1, maxY - minY));
        return new double[]{minX, minY, sc};
    }

    /** 概览：节点按分类配色画小矩形 + 当前视口白框；点击/拖拽跳转（见 mapJump）。 */
    private void renderMinimap(DrawContext ctx) {
        StructureCoreBlockEntity be = be();
        if (be == null) return;
        int mx = mapX(), my = mapY();
        ctx.fill(mx - 1, my - 1, mx + MAP_W + 1, my + MAP_H + 1, NODEFRM);
        ctx.fill(mx, my, mx + MAP_W, my + MAP_H, 0xE0101820);
        List<ItemStack> nodes = be.nodes();
        if (nodes.isEmpty()) {
            ctx.drawText(this.textRenderer, "画布为空", mx + (MAP_W - this.textRenderer.getWidth("画布为空")) / 2,
                    my + MAP_H / 2 - 4, SUB, false);
            return;
        }
        double[] g = mapGeom(be);
        for (int i = 0; i < nodes.size(); i++) {
            int x1 = mx + 5 + (int) ((wnx(be, nodes, i) - g[0]) * g[2]);
            int y1 = my + 5 + (int) ((wny(be, nodes, i) - g[1]) * g[2]);
            int w = Math.max(3, (int) (NW * g[2])), h = Math.max(3, (int) (NH * g[2]));
            ctx.fill(x1, y1, x1 + w, y1 + h, nodeAccent(nodes.get(i)));
        }
        int vx1 = mx + 5 + (int) (((0 - panX) / zoom - g[0]) * g[2]);
        int vy1 = my + 5 + (int) (((34 - panY) / zoom - g[1]) * g[2]);
        int vx2 = mx + 5 + (int) (((workRight() - panX) / zoom - g[0]) * g[2]);
        int vy2 = my + 5 + (int) ((((this.height - 78) - panY) / zoom - g[1]) * g[2]);
        vx1 = Math.max(mx + 1, vx1); vy1 = Math.max(my + 1, vy1);
        vx2 = Math.min(mx + MAP_W - 1, vx2); vy2 = Math.min(my + MAP_H - 1, vy2);
        int vc = 0xCCFFFFFF;
        ctx.fill(vx1, vy1, vx2, vy1 + 1, vc); ctx.fill(vx1, vy2 - 1, vx2, vy2, vc);
        ctx.fill(vx1, vy1, vx1 + 1, vy2, vc); ctx.fill(vx2 - 1, vy1, vx2, vy2, vc);
    }

    /** 点中的世界点移到工作区中心；拖拽期间用快照几何。 */
    private void mapJump(double mouseX, double mouseY) {
        if (mapGeomDrag == null) return;
        double wx = mapGeomDrag[0] + (mouseX - mapX() - 5) / mapGeomDrag[2];
        double wy = mapGeomDrag[1] + (mouseY - mapY() - 5) / mapGeomDrag[2];
        panX = workRight() / 2.0 - wx * zoom;
        panY = (34 + this.height - 78) / 2.0 - wy * zoom;
    }

    private static int endpointIndex(List<long[]> ends, long pl) {
        for (int j = 0; j < ends.size(); j++) if (ends.get(j)[0] == pl) return j;
        return -1;
    }

    /** 存储/终端接口节点：连了几个显示几个。 */
    private void drawStorageNode(DrawContext ctx, StructureCoreBlockEntity be, long[] ep, int j, String dim) {
        long pl = ep[0];
        int kind = (int) ep[1];
        int x = snx(be, pl, j), y = sny(be, pl, j);
        boolean iface = kind == 6;
        int frm = iface ? CYAN : kind == 5 ? TERMFRM : kind == 4 ? OFFFRM : STORFRM;
        ctx.fill(x - 1, y - 1, x + bw() + 1, y + bh() + 1, frm);
        ctx.fill(x, y, x + bw(), y + bh(), NODEBG);
        ctx.fill(x, y, x + bw(), y + 3, frm);
        ctx.fill(x + 10, y + bh() - 2, x + 18, y + bh() + 4, CYAN);                  // 收料口·下缘左（连到面板=存进它聚合的整个网络）
        if (!iface) ctx.fill(x + bw() - 18, y + bh() - 2, x + bw() - 10, y + bh() + 4, ON); // 供料口·下缘右（输出接口无）
        ItemStack icon = new ItemStack(iface ? com.sdzjz.registry.ModBlocks.SATELLITE_NODE.asItem()
                : kind == 5 ? com.sdzjz.registry.ModBlocks.DATA_PANEL.asItem()
                : com.sdzjz.registry.ModBlocks.STORAGE_CORE.asItem());
        ctx.drawItem(icon, x + 4, y + 7); // m92 紧凑化：1x 图标
        String title;
        if (iface) title = "输出接口";
        else { // 分组编号：存储1/2…、数据面板1/2…（服务端已按 接口→存储→面板 排序）
            int no = 0;
            java.util.List<long[]> allEp = endsOf(be);
            for (int k = 0; k <= j && k < allEp.size(); k++)
                if (allEp.get(k)[1] != 6 && (allEp.get(k)[1] == 5) == (kind == 5)) no++;
            title = (kind == 5 ? "数据面板" : "存储") + no;
        }
        ctx.drawText(this.textRenderer, title, x + 24, y + 5, TXT, false);
        ctx.drawText(this.textRenderer, "[" + KIND[Math.min(kind, 6)] + "]", x + 24 + this.textRenderer.getWidth(title) + 3, y + 5,
                iface ? CYAN : kind == 4 ? SUB : kind == 5 ? 0xFFB9A0F0 : ON, false);
        String sub;
        if (iface) {
            sub = "自动寻路: 绑定>有线>无线>卫星";
        } else {
            BlockPos bp = BlockPos.fromLong(pl);
            sub = bp.getX() + "," + bp.getY() + "," + bp.getZ();
            boolean sameDim = this.client != null && this.client.world != null
                    && (dim == null || dim.isEmpty()
                        || dim.equals(this.client.world.getRegistryKey().getValue().toString()));
            if (sameDim && this.client.world.getBlockEntity(bp) instanceof StorageCoreBlockEntity sc) {
                sub += sc.maxTypes() == Integer.MAX_VALUE ? ("  类型 " + sc.usedTypes()) : ("  类型 " + sc.usedTypes() + "/" + sc.maxTypes()); // 仅同维度读数; m98 无限不显上限
            }
        }
        ctx.drawText(this.textRenderer, sub, x + 24, y + 17, SUB, false);
    }

    private void drawNode(DrawContext ctx, StructureCoreBlockEntity be, int i, int x, int y, ItemStack st) {
        ctx.fill(x - 1, y - 1, x + NW + 1, y + NH + 1, NODEFRM);
        ctx.fill(x, y, x + NW, y + NH, NODEBG);
        ctx.fill(x, y, x + NW, y + 3, nodeAccent(st)); // m86 分类配色
        ctx.fill(x - 4, y + NH / 2 - 3, x + 2, y + NH / 2 + 3, CYAN);
        ctx.fill(x + NW - 2, y + NH / 2 - 3, x + NW + 4, y + NH / 2 + 3, ON);
        var msi = ctx.getMatrices();
        msi.push();
        msi.translate(x + 6, y + 16, 0);
        msi.scale(2f, 2f, 1f);
        ctx.drawItem(st, 0, 0);
        msi.pop();
        String name = st.getName().getString();
        if (this.textRenderer.getWidth(name) > NW - 22) {
            while (name.length() > 1 && this.textRenderer.getWidth(name + "…") > NW - 22) name = name.substring(0, name.length() - 1);
            name = name + "…";
        }
        ctx.drawText(this.textRenderer, name, x + 6, y + 6, TXT, false);
        drawStatusDot(ctx, x + NW - 11, y + 5, be.nodeStatus(i)); // 状态灯：绿=运行 黄=阻塞/关闸 红=缺料
        if (StructureCoreBlockEntity.isDistributor(st)) {
            int outs = 0;
            for (int[] c : be.connections()) if (c[0] == i) outs++;
            ctx.drawText(this.textRenderer, "均分 → " + outs + " 路", x + 44, y + 26, CYAN, false);
            ctx.drawText(this.textRenderer, outs == 0 ? "拉出线到下游" : "余数轮转", x + 44, y + 38, SUB, false);
            return;
        }
        if (StructureCoreBlockEntity.isSwitch(st)) {
            boolean on = StructureCoreBlockEntity.switchOn(st);
            int bfr = on ? ON : 0xFF5A6470;
            ctx.fill(x + 43, y + 23, x + 91, y + 45, bfr);
            ctx.fill(x + 44, y + 24, x + 90, y + 44, on ? 0xFF10321E : 0xFF141A24);
            ctx.drawText(this.textRenderer, on ? "● 开" : "○ 关", x + 55, y + 30, on ? ON : SUB, false);
            return;
        }
        if (StructureCoreBlockEntity.isFilter(st)) {
            boolean black = StructureCoreBlockEntity.filterBlacklist(st);
            ctx.drawText(this.textRenderer, black ? "[黑名单]" : "[白名单]", x + 44, y + 26, black ? 0xFFE8C43C : ON, false);
            List<String> fl = StructureCoreBlockEntity.filterList(st);
            if (fl.isEmpty()) {
                ctx.drawText(this.textRenderer, "右键配置", x + 44, y + 38, SUB, false);
            } else {
                int shown = Math.min(3, fl.size());
                for (int k = 0; k < shown; k++) {
                    try { ctx.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(fl.get(k)))), x + 42 + k * 18, y + 34); } catch (Exception ignored) {}
                }
                if (fl.size() > 3) ctx.drawText(this.textRenderer, "+" + (fl.size() - 3), x + 42 + 54, y + 38, SUB, false);
            }
            return;
        }
        if (StructureCoreBlockEntity.isSensor(st)) {
            String si = StructureCoreBlockEntity.sensorItem(st);
            if (si.isEmpty()) {
                ctx.drawText(this.textRenderer, "直通(未配置)", x + 44, y + 26, SUB, false);
                ctx.drawText(this.textRenderer, "右键配置", x + 44, y + 38, SUB, false);
            } else {
                try { ctx.drawItem(new ItemStack(Registries.ITEM.get(Identifier.of(si))), x + 40, y + 20); } catch (Exception ignored) {}
                long th = StructureCoreBlockEntity.sensorThreshold(st);
                boolean less = StructureCoreBlockEntity.sensorLess(st);
                ctx.drawText(this.textRenderer, (less ? "<" : ">") + fmtNum(th) + " 放行", x + 58, y + 24, CYAN, false);
                ctx.fill(x + 57, y + 36, x + 71, y + 49, 0xFF0C1E30); // [−]
                ctx.fill(x + 74, y + 36, x + 88, y + 49, 0xFF0C1E30); // [+]
                ctx.drawText(this.textRenderer, "-", x + 62, y + 39, TXT, false);
                ctx.drawText(this.textRenderer, "+", x + 79, y + 39, TXT, false);
            }
            return;
        }
        ctx.drawText(this.textRenderer, "×" + st.getCount(), x + 44, y + 26, CYAN, false);
        boolean isCrop = st.getItem() instanceof com.sdzjz.item.CropFarmItem;
        if (st.getItem() instanceof AutoCrafterItem || isCrop) {
            int bx = x + NW - 30, by = y + 14;
            ctx.fill(bx - 1, by - 1, bx + 21, by + 21, NODEFRM);
            ctx.fill(bx, by, bx + 20, by + 20, 0xFF0C1E30);
            java.util.List<String> cropsSel = isCrop ? StructureCoreBlockEntity.cropList(st) : java.util.List.of();
            String t = StructureCoreBlockEntity.craftTarget(st);
            if (isCrop && !cropsSel.isEmpty()) { // m93 多选作物：徽章=第一种，下行前3种mini图标+计数
                ctx.drawItem(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(cropsSel.get(0)))), bx + 2, by + 2);
                int nm = Math.min(3, cropsSel.size());
                for (int k = 0; k < nm; k++)
                    ctx.drawItem(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(cropsSel.get(k)))), x + 42 + k * 13, y + 34);
                ctx.drawText(this.textRenderer, "×" + cropsSel.size() + "种", x + 42 + nm * 13 + 4, y + 38, ON, false);
            } else if (!isCrop && !t.isEmpty()) {
                ItemStack ts = new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(t)));
                ctx.drawItem(ts, bx + 2, by + 2);
                String tn = ts.getName().getString();
                while (tn.length() > 1 && this.textRenderer.getWidth("→" + tn) > NW - 50) tn = tn.substring(0, tn.length() - 1);
                ctx.drawText(this.textRenderer, "→" + tn, x + 44, y + 38, ON, false); // 放大图标后挪右，避免压字
            } else {
                ctx.drawText(this.textRenderer, "?", bx + 7, by + 6, SUB, false);
                ctx.drawText(this.textRenderer, isCrop ? "选作物" : "设目标", x + 44, y + 38, SUB, false);
            }
        }
    }

    /** 状态灯：核心停机=灰；1 绿呼吸=运行 2 黄=阻塞/关闸 3 红=缺料 其余=待机灰。 */
    private void drawStatusDot(DrawContext ctx, int x, int y, int stat) {
        int c;
        if (!this.handler.isRunning()) c = 0xFF3A424E;
        else c = switch (stat) {
            case 1 -> ((165 + (int) (88 * Math.sin(System.currentTimeMillis() / 300.0))) << 24) | 0x33D07A;
            case 2 -> 0xFFE8C43C;
            case 3 -> 0xFFE85050;
            default -> 0xFF3A424E;
        };
        ctx.fill(x - 1, y - 1, x + 7, y + 7, 0xFF06101C);
        ctx.fill(x, y, x + 6, y + 6, c);
    }

    private static String fmtNum(long n) {
        if (n < 10000L) return String.valueOf(n);
        if (n < 1_000_000L) return String.format("%.1fK", n / 1000.0);
        if (n < 1_000_000_000L) return String.format("%.1fM", n / 1_000_000.0);
        return String.format("%.1fB", n / 1_000_000_000.0);
    }

    private void drawUpgradeSlots(DrawContext ctx, StructureCoreBlockEntity be, int x, int y, ItemStack node) {
        int[] lv = { be.nodeSpeed(node), be.nodeCount(node), be.nodePar(node) };
        for (int k = 0; k < 3; k++) {
            int sx = x + 4 + k * 32, sy = y + NH + 4;
            ctx.fill(sx - 1, sy - 1, sx + 25, sy + 19, NODEFRM);
            ctx.fill(sx, sy, sx + 24, sy + 18, 0xFF0C1E30);
            ctx.drawItem(new ItemStack(UPG[k]), sx + 1, sy + 1);
            ctx.drawText(this.textRenderer, "" + lv[k], sx + 18, sy + 6, lv[k] > 0 ? ON : SUB, false);
        }
    }

    private void drawWire(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.max(40, Math.abs(x2 - x1) / 2);
        float c1x = x1 + dx, c2x = x2 - dx;
        int steps = 56;
        // 流动效果：暗色底线常亮 + 亮色能量段沿线行进（方向 = 出口→入口 = 物流方向）
        float phase = (System.currentTimeMillis() % 1000L) / 1000f * 10f; // 1s 走完一个虚线周期
        int dim = (color & 0x00FFFFFF) | 0x66000000;
        int glow = 0x66FFFFFF & color | 0x33FFFFFF;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps, u = 1 - t;
            float bx = u * u * u * x1 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x2;
            float by = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            int px = (int) bx, py = (int) by;
            float m = (s - phase) % 10f;
            if (m < 0) m += 10f;
            if (m < 3.5f) { // 行进亮段（带 1px 光晕）
                ctx.fill(px - 2, py - 2, px + 2, py + 2, glow);
                ctx.fill(px - 1, py - 1, px + 1, py + 1, color);
            } else {        // 常亮暗底
                ctx.fill(px - 1, py - 1, px + 1, py + 1, dim);
            }
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // m85：HandledScreen 会把前景层平移 (x,y)——之前标题/状态因此漂到屏幕中间。translate 回去，用真屏幕坐标。
        ctx.getMatrices().push();
        ctx.getMatrices().translate(-this.x, -this.y, 0);
        // m83：状态栏下沉到底部（用户点名，参考 ME 终端把信息压在操作区）——顶部只留窄标题条，给存储总线腾地方
        ctx.fill(0, 0, workRight(), 20, 0xEE0A121F);
        ctx.fill(0, 19, workRight(), 20, CYAN);
        String tierName = this.handler.tier() >= 2 ? "超大工作台 · 画布" : "结构核心 · 画布";
        ctx.drawText(this.textRenderer, tierName, 10, 6, TXT, false);
        String zp = Math.round(zoom * 100) + "%"; // m86 顶条缩放读数（−/＋按钮之间）
        ctx.drawText(this.textRenderer, zp, workRight() - 128 - this.textRenderer.getWidth(zp) / 2, 6, TXT, false);

        // 底部背板：按钮 + 状态 + 提示 一体
        // m87：底栏加高到 78，状态改画在按钮下方整行——之前固定 x=498 起画，GUI 缩放大时直接怼进 JEI（用户截图实锤）
        ctx.fill(0, this.height - 78, workRight(), this.height, 0xEE0A121F);
        ctx.fill(0, this.height - 78, workRight(), this.height - 77, CYAN);
        boolean run = this.handler.isRunning();
        int stor = 0, term = 0;
        StructureCoreBlockEntity be = be();
        if (be != null) for (long[] e : endsOf(be)) { if (e[1] == 5) term++; else if (e[1] != 6) stor++; }
        int nRun = 0, nBlk = 0, nLack = 0;
        if (be != null) for (int i = 0; i < be.nodes().size(); i++) {
            int st2 = be.nodeStatus(i);
            if (st2 == 1) nRun++; else if (st2 == 2) nBlk++; else if (st2 == 3) nLack++;
        }
        int maxW = workRight() - 16;
        ctx.drawText(this.textRenderer, run ? "● 运行中" : "○ 已停止", 8, this.height - 48, run ? ON : SUB, false);
        ctx.drawText(this.textRenderer, fitText("经验 " + fmtNum(this.handler.xp())
                + "  机器 " + this.handler.machineCount()
                + "  存储 " + stor + " · 面板 " + term
                + "  缓存 " + fmtNum(this.handler.buffered())
                + "  产出 " + (be == null ? "0" : fmtNum(be.prodPerMinView())) + "/分(实测)", maxW - 62), 70, this.height - 48, SUB, false);
        ctx.drawText(this.textRenderer, fitText("运行 " + nRun + " · 阻塞 " + nBlk + " · 缺料 " + nLack
                + "  升级∑ 加速" + this.handler.speedLv()
                + " 数量" + this.handler.countLv()
                + " 并列" + this.handler.parallelLv()
                + "  缩放" + Math.round(zoom * 100) + "%", maxW), 8, this.height - 36, SUB, false);
        ctx.drawText(this.textRenderer, fitText("右键=菜单 · 拖节点=移动 · 绿口拖线 · 滚轮缩放 · 状态灯 绿=运行 黄=阻塞 红=缺料 · 节点色 青=生产 橙=加工 紫=逻辑 绿=农场", maxW), 8, this.height - 12, SUB, false);


        // ===== m88：机器库侧栏（概念图左栏——列背包里的机器，点击放入画布）=====
        if (libOpen) {
            int lx = 8, ly = 24, lw = 160, lb = this.height - 84;
            ctx.fill(lx, ly, lx + lw, lb, 0xE0081120);
            ctx.fill(lx, ly, lx + lw, ly + 14, 0xFF10253A);
            ctx.drawText(this.textRenderer, "机器库（背包）", lx + 6, ly + 3, TXT, false);
            List<ItemStack> lib = libItems();
            int rowH = 20, visible = Math.max(1, (lb - ly - 30) / rowH);
            libScroll = Math.max(0, Math.min(libScroll, Math.max(0, lib.size() - visible)));
            for (int r = 0; r < visible && r + libScroll < lib.size(); r++) {
                ItemStack it = lib.get(r + libScroll);
                int ry = ly + 16 + r * rowH;
                boolean hov = mouseX >= lx && mouseX <= lx + lw && mouseY >= ry && mouseY < ry + rowH;
                if (hov) ctx.fill(lx, ry, lx + lw, ry + rowH, 0x552E6E8E);
                ctx.drawItem(it, lx + 4, ry + 1);
                ctx.drawText(this.textRenderer, fitText(it.getName().getString(), lw - 56), lx + 24, ry + 5, TXT, false);
                String c = "×" + it.getCount();
                ctx.drawText(this.textRenderer, c, lx + lw - 6 - this.textRenderer.getWidth(c), ry + 5, SUB, false);
            }
            if (lib.isEmpty()) ctx.drawText(this.textRenderer, "背包里没有机器", lx + 8, ly + 22, SUB, false);
            ctx.drawText(this.textRenderer, "点击=放 1 台进画布 · 滚轮翻", lx + 6, lb - 12, SUB, false);
        }

        // ===== m85：节点悬停详情（状态/周期/基础产量/产出表）=====
        if (menuLabels.isEmpty() && be != null && mouseY > 20 && mouseY < this.height - 80 && mouseX < workRight()) {
            List<ItemStack> nodes = be.nodes();
            for (int i = 0; i < nodes.size(); i++) {
                int nx = (int) (panX + wnx(be, nodes, i) * zoom), ny = (int) (panY + wny(be, nodes, i) * zoom);
                if (mouseX < nx || mouseX > nx + (int) (NW * zoom) || mouseY < ny || mouseY > ny + (int) (NH * zoom)) continue;
                ItemStack st = nodes.get(i);
                java.util.List<net.minecraft.text.Text> tip = new java.util.ArrayList<>();
                tip.add(Text.literal(st.getName().getString() + " ×" + st.getCount()));
                int stt = be.nodeStatus(i);
                tip.add(Text.literal("状态: " + (!this.handler.isRunning() ? "核心停机"
                        : switch (stt) { case 1 -> "运行中"; case 2 -> "阻塞/关闸"; case 3 -> "缺料"; default -> "待机"; })));
                if (st.getItem() instanceof com.sdzjz.item.MachineItem mi2) {
                    var def = mi2.def();
                    double avg = 0;
                    for (var d : def.outputs()) avg += d.chance() * (d.min() + d.max()) / 2.0;
                    double perMin = avg * (1200.0 / Math.max(1, def.baseIntervalTicks())) * st.getCount();
                    tip.add(Text.literal(String.format("周期 %.1f 秒 · 基础产出 ~%.0f/分", def.baseIntervalTicks() / 20.0, perMin)));
                    StringBuilder sb = new StringBuilder("产出: ");
                    for (int k2 = 0; k2 < def.outputs().size() && k2 < 3; k2++) {
                        if (k2 > 0) sb.append("、");
                        sb.append(new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(def.outputs().get(k2).item()))).getName().getString());
                    }
                    if (def.outputs().size() > 3) sb.append("…");
                    tip.add(Text.literal(sb.toString()));
                    if (def.consumesInputs()) tip.add(Text.literal("消耗输入（对齐原版）"));
                }
                ctx.drawTooltip(this.textRenderer, tip, mouseX, mouseY);
                break;
            }
        }
        ctx.getMatrices().pop();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (libOpen && mouseX >= 8 && mouseX <= 168 && mouseY >= 24 && mouseY <= this.height - 84) { // m88 机器库滚动
            libScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        if (pickerNode >= 0 || menuOpen) return true;
        if (inMap(mouseX, mouseY)) return true; // m110a 地图区不缩放画布
        if (mouseY > 34) {
            double old = zoom;
            zoom = Math.max(0.4, Math.min(2.5, zoom * (verticalAmount > 0 ? 1.1 : 0.9)));
            double wx = (mouseX - panX) / old, wy = (mouseY - panY) / old;
            panX = mouseX - wx * zoom;
            panY = mouseY - wy * zoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ================= 右键菜单 =================
    private void openMenu(int x, int y) {
        menuOpen = true;
        menuX = Math.min(x, workRight() - MENU_W - 4);
        menuY = Math.min(y, this.height - menuLabels.size() * MENU_H - 4);
    }

    private void clearMenu() {
        menuLabels.clear();
        menuActions.clear();
        menuOpen = false;
    }

    private void addMenu(String label, Runnable action) {
        menuLabels.add(label);
        menuActions.add(action);
    }

    /** m110b 节点设置菜单：右键节点与标题栏齿轮共用同一构建（含单节点启停）。 */
    private void openNodeMenu(int idx, int atX, int atY) {
        StructureCoreBlockEntity be = be();
        if (be == null || idx < 0 || idx >= be.nodes().size()) return;
        final ItemStack st = be.nodes().get(idx);
        BlockPos p = this.handler.blockPos();
        clearMenu();
        addMenu("取出机器", () -> { if (p != null) ClientPlayNetworking.send(new NodeRemovePayload(p, idx)); });
        addMenu(StructureCoreBlockEntity.nodePaused(st) ? "恢复运行" : "暂停节点",
                () -> { if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodePausePayload(p, idx)); });
        addMenu("断开全部连线", () -> clearLinksOfMachine(idx));
        if (st.getItem() instanceof AutoCrafterItem)
            addMenu("选择合成目标", () -> openPicker(idx));
        if (st.getItem() instanceof com.sdzjz.item.CropFarmItem)
            addMenu("选择种植作物", () -> openCropPicker(idx));
        if (StructureCoreBlockEntity.isFilter(st)) {
            addMenu("配置过滤物品…", () -> openFilterPicker(idx));
            addMenu(StructureCoreBlockEntity.filterBlacklist(st) ? "切为白名单" : "切为黑名单",
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeFilterPayload(p, idx, "")); });
        }
        if (StructureCoreBlockEntity.isSwitch(st)) {
            addMenu(StructureCoreBlockEntity.switchOn(st) ? "切为:关闭" : "切为:开启",
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeSwitchPayload(p, idx)); });
        }
        if (StructureCoreBlockEntity.isSensor(st)) {
            addMenu("监测物品…", () -> openSensorPicker(idx));
            addMenu(StructureCoreBlockEntity.sensorLess(st) ? "改为:高于阈值放行" : "改为:低于阈值放行",
                    () -> { if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, idx, "",
                            StructureCoreBlockEntity.sensorThreshold(st), !StructureCoreBlockEntity.sensorLess(st))); });
        }
        addMenu("取消", () -> {});
        openMenu(atX, atY);
    }

    /** m110b 标题栏齿轮（滑杆式设置图标，纯 fill 不依赖字体字形）。 */
    private void drawGear(DrawContext ctx, int x, int y) {
        ctx.fill(x, y + 1, x + 9, y + 2, SUB);
        ctx.fill(x, y + 4, x + 9, y + 5, SUB);
        ctx.fill(x, y + 7, x + 9, y + 8, SUB);
        ctx.fill(x + 2, y, x + 4, y + 3, TXT);
        ctx.fill(x + 5, y + 3, x + 7, y + 6, TXT);
        ctx.fill(x + 1, y + 6, x + 3, y + 9, TXT);
    }

    private void renderMenu(DrawContext ctx, int mouseX, int mouseY) {
        int h = menuLabels.size() * MENU_H;
        ctx.fill(menuX - 1, menuY - 1, menuX + MENU_W + 1, menuY + h + 1, NODEFRM);
        for (int i = 0; i < menuLabels.size(); i++) {
            int y0 = menuY + i * MENU_H;
            boolean hov = mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= y0 && mouseY < y0 + MENU_H;
            ctx.fill(menuX, y0, menuX + MENU_W, y0 + MENU_H, hov ? 0xFF14304A : 0xF00A1626);
            ctx.drawText(this.textRenderer, menuLabels.get(i), menuX + 6, y0 + 4, hov ? 0xFFE8FBFF : TXT, false);
        }
    }

    /** 断开某机器节点的全部连线（机器边 + 存储边，逐条 toggle）。 */
    private void clearLinksOfMachine(int idx) {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        for (int[] c : new ArrayList<>(be.connections()))
            if (c[0] == idx || c[1] == idx) ClientPlayNetworking.send(new NodeLinkPayload(p, c[0], c[1]));
        List<String> dims = endDimsOf(be);
        List<long[]> ends = endsOf(be);
        for (long[] e : new ArrayList<>(be.storageEdgesView()))
            if (e[0] == idx) {
                int j = endpointIndex(ends, e[1]);
                String dim = j >= 0 && j < dims.size() ? dims.get(j) : "";
                ClientPlayNetworking.send(new StorageLinkPayload(p, (int) e[0], e[1], (int) e[2], dim));
            }
    }

    private void clearLinksOfStorage(long pl) {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        List<String> dims = endDimsOf(be);
        List<long[]> ends = endsOf(be);
        int j = endpointIndex(ends, pl);
        String dim = j >= 0 && j < dims.size() ? dims.get(j) : "";
        for (long[] e : new ArrayList<>(be.storageEdgesView()))
            if (e[1] == pl) ClientPlayNetworking.send(new StorageLinkPayload(p, (int) e[0], e[1], (int) e[2], dim));
    }

    /** 整理布局：机器排网格，存储排右列。 */
    private void autoLayout() {
        StructureCoreBlockEntity be = be();
        BlockPos p = this.handler.blockPos();
        if (be == null || p == null) return;
        for (int i = 0; i < be.nodes().size(); i++)
            ClientPlayNetworking.send(new NodeMovePayload(p, i, 20 + (i % 6) * 112, 20 + (i / 6) * 88));
        List<long[]> ends = endsOf(be);
        for (int j = 0; j < ends.size(); j++)
            ClientPlayNetworking.send(new StorageNodeMovePayload(p, ends.get(j)[0], 760, 20 + j * 72));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // m93 总线大小滑块抓取
        int trxC = busTrackX();
        if (button == 0 && mouseX >= trxC - 3 && mouseX <= trxC + BUS_TRACK_W + 3 && mouseY >= 26 && mouseY <= 40) {
            busScaleDrag = true;
            busScaleFromMouse(mouseX);
            return true;
        }
        // m91 总线收起/展开开关
        int tbx = workRight() - 34;
        if (button == 0 && mouseX >= tbx && mouseX <= tbx + 22 && mouseY >= 26 && mouseY <= 40) {
            busCollapsed = !busCollapsed;
            return true;
        }
        // m88 机器库侧栏：点击行=放 1 台进画布；面板区吞掉其余点击
        if (libOpen && mouseX >= 8 && mouseX <= 168 && mouseY >= 24 && mouseY <= this.height - 84) {
            if (button == 0) {
                List<ItemStack> lib = libItems();
                int ly = 24, lb = this.height - 84, rowH = 20;
                int visible = Math.max(1, (lb - ly - 30) / rowH);
                int r = (int) ((mouseY - (ly + 16)) / rowH);
                if (mouseY >= ly + 16 && r >= 0 && r < visible && r + libScroll < lib.size()) {
                    BlockPos p = this.handler.blockPos();
                    if (p != null) ClientPlayNetworking.send(new com.sdzjz.net.NodeAddPayload(p,
                            Registries.ITEM.getId(lib.get(r + libScroll).getItem()).toString()));
                }
            }
            return true;
        }
        if (menuOpen) {
            int h = menuLabels.size() * MENU_H;
            if (button == 0 && mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= menuY && mouseY < menuY + h) {
                int idx = (int) ((mouseY - menuY) / MENU_H);
                Runnable act = idx >= 0 && idx < menuActions.size() ? menuActions.get(idx) : null;
                clearMenu();
                if (act != null) act.run();
                return true;
            }
            clearMenu();
            return true;
        }
        if (pickerNode >= 0) {
            int px = (this.width - PICK_W) / 2, py = (this.height - PICK_H) / 2;
            if (pickerField.mouseClicked(mouseX, mouseY, button)) return true;
            int gx = px + 8, gy = py + 44;
            for (int k = 0; k < pickerFiltered.size(); k++) {
                int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
                if (mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20) {
                    BlockPos bp = this.handler.blockPos();
                    String iid = Registries.ITEM.getId(pickerFiltered.get(k)).toString();
                    if (bp != null) {
                        if (pickerMode == 1) { // 过滤多选：切名单项，不关窗
                            ClientPlayNetworking.send(new NodeFilterPayload(bp, pickerNode, iid));
                            return true;
                        }
                        if (pickerMode == 2) { // 传感器：换监测物品，保留阈值/方向
                            StructureCoreBlockEntity be2 = be();
                            ItemStack ns = be2 != null && pickerNode < be2.nodes().size() ? be2.nodes().get(pickerNode) : ItemStack.EMPTY;
                            ClientPlayNetworking.send(new NodeSensorPayload(bp, pickerNode, iid,
                                    StructureCoreBlockEntity.sensorThreshold(ns), StructureCoreBlockEntity.sensorLess(ns)));
                            closePicker();
                            return true;
                        }
                        ClientPlayNetworking.send(new NodeTargetPayload(bp, pickerNode, iid));
                        if (pickerMode == 3) return true; // m93 多选作物：toggle 后不关面板，继续点选
                    }
                    closePicker();
                    return true;
                }
            }
            if (mouseX < px || mouseX > px + PICK_W || mouseY < py || mouseY > py + PICK_H) closePicker();
            return true;
        }
        // m110a 小地图：左键=跳转视角并开始拖拽；面板区吞掉其余点击
        if (inMap(mouseX, mouseY)) {
            if (button == 0) {
                StructureCoreBlockEntity beM = be();
                if (beM != null) { mapGeomDrag = mapGeom(beM); mapJump(mouseX, mouseY); mapDragging = true; }
            }
            return true;
        }
        if (mouseY > 34 && (button == 0 || button == 1)) {
            StructureCoreBlockEntity be = be();
            if (be != null) {
                List<ItemStack> nodes = be.nodes();
                List<long[]> ends = endsOf(be);
                double wx = wmx(mouseX), wy = wmy(mouseY);
                // 升级格：左键加、右键取
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    if (StructureCoreBlockEntity.isFilter(nodes.get(i)) || StructureCoreBlockEntity.isSensor(nodes.get(i))
                            || StructureCoreBlockEntity.isSwitch(nodes.get(i))
                            || StructureCoreBlockEntity.isDistributor(nodes.get(i))) continue;
                    int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                    for (int k = 0; k < 3; k++) {
                        int sx = nx + 4 + k * 32, sy = ny + NH + 4;
                        if (wx >= sx && wx <= sx + 24 && wy >= sy && wy <= sy + 18) {
                            BlockPos p = this.handler.blockPos();
                            // m115a：Shift+点击=批量（一次至多64个，服务端按背包/格内实况截断）
                            if (p != null) ClientPlayNetworking.send(new NodeUpgradePayload(p, i, k, button == 0, hasShiftDown() ? 64 : 1));
                            return true;
                        }
                    }
                }
                // m110b 标题栏齿轮：左键打开节点设置菜单（世界坐标，随缩放）
                if (button == 0) {
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx + NW - 26 && wx <= nx + NW - 13 && wy >= ny + 2 && wy <= ny + 15) {
                            openNodeMenu(i, (int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                }
                if (button == 1) {
                    // 停靠栏优先：右键端点节点 → 菜单（屏幕坐标；m91 总线收起时跳过）
                    if (busVisible()) for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx && mouseX <= sx + bw() && mouseY >= sy && mouseY <= sy + bh()) {
                            clearMenu();
                            addMenu("断开全部连线", () -> clearLinksOfStorage(pl));
                            addMenu("取消", () -> {});
                            openMenu((int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // 右键机器节点 → 菜单（m110b 与标题栏齿轮共用 openNodeMenu）
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            openNodeMenu(i, (int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // 右键空白 → 画布菜单
                    clearMenu();
                    addMenu("整理布局", this::autoLayout);
                    addMenu("重置视角", () -> { panX = 0; panY = 0; zoom = 1.0; });
                    addMenu("取消", () -> {});
                    openMenu((int) mouseX, (int) mouseY);
                    return true;
                }
                if (button == 0) {
                    // 停靠栏优先（屏幕坐标）：供料口(绿) → 存储/面板→机器 供料连线；面板供料=取自它聚合的网络
                    if (busVisible()) for (int j = ends.size() - 1; j >= 0; j--) {
                        if (ends.get(j)[1] == 6) continue; // 输出接口无供料口
                        long pl = ends.get(j)[0];
                        int oxp = snx(be, pl, j) + bw() - 14, oyp = sny(be, pl, j) + bh();
                        if (Math.abs(mouseX - oxp) <= 8 && Math.abs(mouseY - oyp) <= 8) {
                            linking = true; linkStor = pl; linkFrom = -1; return true;
                        }
                    }
                    // 停靠栏节点体：吞掉点击，防误触其下的机器/画布拖动（m91 收起时跳过）
                    if (busVisible()) for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx - 4 && mouseX <= sx + bw() + 6 && mouseY >= sy && mouseY <= sy + bh()) return true;
                    }
                    // 开关节点：点按钮切换 开/关
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        if (!StructureCoreBlockEntity.isSwitch(nodes.get(i))) continue;
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx + 43 && wx <= nx + 91 && wy >= ny + 23 && wy <= ny + 45) {
                            BlockPos p = this.handler.blockPos();
                            if (p != null) ClientPlayNetworking.send(new NodeSwitchPayload(p, i));
                            return true;
                        }
                    }
                    // 传感器阈值 [−][+]：步进100，Shift=1000
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        if (!StructureCoreBlockEntity.isSensor(nodes.get(i))) continue;
                        if (StructureCoreBlockEntity.sensorItem(nodes.get(i)).isEmpty()) continue;
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        int hit = 0;
                        if (wx >= nx + 57 && wx <= nx + 71 && wy >= ny + 36 && wy <= ny + 49) hit = -1;
                        else if (wx >= nx + 74 && wx <= nx + 88 && wy >= ny + 36 && wy <= ny + 49) hit = 1;
                        if (hit != 0) {
                            long step = hasShiftDown() ? 1000 : 100;
                            long th = Math.max(0, StructureCoreBlockEntity.sensorThreshold(nodes.get(i)) + hit * step);
                            BlockPos p = this.handler.blockPos();
                            if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, i, "", th,
                                    StructureCoreBlockEntity.sensorLess(nodes.get(i))));
                            return true;
                        }
                    }
                    // 目标徽章：自动合成机=全物品选择器；全自动农场=作物选择器
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        boolean auto = nodes.get(i).getItem() instanceof AutoCrafterItem;
                        boolean crop = nodes.get(i).getItem() instanceof com.sdzjz.item.CropFarmItem;
                        if (!auto && !crop) continue;
                        int bx = wnx(be, nodes, i) + NW - 30, by = wny(be, nodes, i) + 14;
                        if (wx >= bx && wx <= bx + 20 && wy >= by && wy <= by + 20) {
                            if (crop) openCropPicker(i); else openPicker(i);
                            return true;
                        }
                    }
                    // 机器输出口(绿) → 连线
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int oxp = wnx(be, nodes, i) + NW, oyp = wny(be, nodes, i) + NH / 2;
                        if (Math.abs(wx - oxp) <= 7 && Math.abs(wy - oyp) <= 7) {
                            linking = true; linkFrom = i; linkStor = Long.MIN_VALUE; return true;
                        }
                    }
                    // 机器节点体 → 拖动
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            dragIndex = i; dragStor = Long.MIN_VALUE; dragOffX = wx - nx; dragOffY = wy - ny; return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (busScaleDrag) { busScaleFromMouse(mouseX); return true; } // m93 总线大小滑块
        if (mapDragging) { mapJump(mouseX, mouseY); return true; }    // m110a 小地图拖拽跳转
        if (pickerNode >= 0 || menuOpen) return true;
        if (linking) return true;
        if (button == 0 && dragIndex >= 0) {
            StructureCoreBlockEntity be = be();
            if (be != null && dragIndex < be.nodes().size()) {
                int nx = (int) (wmx(mouseX) - dragOffX);
                int ny = (int) (wmy(mouseY) - dragOffY);
                be.setNodePos(dragIndex, nx, ny);
            }
            return true;
        }
        if (button == 0 && mouseY > 34) {
            panX += deltaX;
            panY += deltaY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        busScaleDrag = false; // m93
        if (mapDragging) { mapDragging = false; mapGeomDrag = null; } // m110a
        if (button == 0 && linking) {
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null) {
                List<ItemStack> nodes = be.nodes();
                List<long[]> ends = endsOf(be);
                List<String> dims = endDimsOf(be);
                double wx = wmx(mouseX), wy = wmy(mouseY);
                if (linkFrom >= 0) {
                    // 优先看是否落在存储节点上 → 机器→存储 定向产出
                    boolean done = false;
                    if (busVisible()) for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0]; // 数据面板也可连（存进它聚合的整个网络）
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx && mouseX <= sx + bw() && mouseY >= sy && mouseY <= sy + bh()) {
                            ClientPlayNetworking.send(new StorageLinkPayload(p, linkFrom, pl, 0, dims.get(j)));
                            done = true;
                            break;
                        }
                    }
                    if (!done) {
                        for (int i = nodes.size() - 1; i >= 0; i--) {
                            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                            if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                                if (i != linkFrom) ClientPlayNetworking.send(new NodeLinkPayload(p, linkFrom, i));
                                break;
                            }
                        }
                    }
                } else if (linkStor != Long.MIN_VALUE) {
                    // 存储→机器 定向供料
                    int j = endpointIndex(ends, linkStor);
                    String dim = j >= 0 && j < dims.size() ? dims.get(j) : "";
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            ClientPlayNetworking.send(new StorageLinkPayload(p, i, linkStor, 1, dim));
                            break;
                        }
                    }
                }
            }
            linking = false; linkFrom = -1; linkStor = Long.MIN_VALUE;
            return true;
        }
        if (button == 0 && dragIndex >= 0) {
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null && dragIndex < be.nodes().size()) {
                ItemStack st = be.nodes().get(dragIndex);
                ClientPlayNetworking.send(new NodeMovePayload(p, dragIndex, be.nodeX(st, 0), be.nodeY(st, 0)));
            }
            dragIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (pickerNode >= 0) renderPicker(ctx, mouseX, mouseY, delta);
        if (menuOpen) renderMenu(ctx, mouseX, mouseY);
    }

    // ================= 自动合成机目标选择器 =================
    private void openPicker(int node) {
        pickerMode = 0;
        pickerNode = node;
        if (craftables == null) buildCraftables();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 过滤名单多选：点选=加/移，不关窗。 */
    private void openFilterPicker(int node) {
        pickerMode = 1;
        pickerNode = node;
        if (allItems == null) buildAllItems();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 传感器监测物品单选。 */
    private void openSensorPicker(int node) {
        pickerMode = 2;
        pickerNode = node;
        if (allItems == null) buildAllItems();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 作物选择（全自动农场，固定 9 种）。 */
    private List<Item> cropItems;

    private void openCropPicker(int node) {
        pickerMode = 3;
        pickerNode = node;
        if (cropItems == null) {
            cropItems = new ArrayList<>();
            for (String id : com.sdzjz.machine.CropFarms.KEYS)
                cropItems.add(Registries.ITEM.get(Identifier.of(id)));
        }
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
    }

    /** 全物品表（过滤/传感器可选任意物品，不限可合成）。 */
    private void buildAllItems() {
        allItems = new ArrayList<>();
        for (Item it : Registries.ITEM) {
            if (it != net.minecraft.item.Items.AIR) allItems.add(it);
        }
    }

    private void closePicker() {
        pickerNode = -1;
        pickerField.setFocused(false);
        this.setFocused(null);
    }

    private void buildCraftables() {
        craftables = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        LinkedHashSet<Item> set = new LinkedHashSet<>();
        for (RecipeEntry<CraftingRecipe> e : mc.world.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            try {
                ItemStack out = e.value().getResult(mc.world.getRegistryManager());
                if (out != null && !out.isEmpty()) set.add(out.getItem());
            } catch (Exception ignored) {}
        }
        craftables.addAll(set);
    }

    private void refilterPicker() {
        pickerFiltered.clear();
        List<Item> src = pickerMode == 0 ? craftables : pickerMode == 3 ? cropItems : allItems;
        if (src == null) return;
        String q = pickerField.getText().trim().toLowerCase();
        // m116：已选项置顶——窗口只显示一页 70 格，1400+ 物品里已选的经常根本翻不到（用户点名）。
        // 按 id 直接解析已选（数量个位数级），不进大扫描，每键成本不回退 m107a。
        if (pickerMode == 1 || pickerMode == 3) {
            StructureCoreBlockEntity beS = be();
            if (beS != null && pickerNode >= 0 && pickerNode < beS.nodes().size()) {
                List<String> sel = pickerMode == 1
                        ? StructureCoreBlockEntity.filterList(beS.nodes().get(pickerNode))
                        : StructureCoreBlockEntity.cropList(beS.nodes().get(pickerNode));
                for (String sid : sel) {
                    Item it = Registries.ITEM.get(net.minecraft.util.Identifier.of(sid));
                    if (q.isEmpty()
                            || new ItemStack(it).getName().getString().toLowerCase().contains(q)
                            || Registries.ITEM.getId(it).getPath().contains(q)) {
                        if (!pickerFiltered.contains(it)) pickerFiltered.add(it);
                    }
                }
            }
        }
        for (Item it : src) {
            if (pickerFiltered.contains(it)) continue; // 已置顶的不重复
            if (q.isEmpty()
                    || new ItemStack(it).getName().getString().toLowerCase().contains(q)
                    || Registries.ITEM.getId(it).getPath().contains(q)) {
                pickerFiltered.add(it);
                if (pickerFiltered.size() >= PICK_COLS * PICK_ROWS) break;
            }
        }
    }

    private void renderPicker(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int px = (this.width - PICK_W) / 2, py = (this.height - PICK_H) / 2;
        ctx.fill(0, 0, this.width, this.height, 0xA0000000);
        ctx.fill(px - 1, py - 1, px + PICK_W + 1, py + PICK_H + 1, NODEFRM);
        ctx.fill(px, py, px + PICK_W, py + PICK_H, 0xF00A1626);
        ctx.fill(px, py, px + PICK_W, py + 3, CYAN);
        String ptitle = pickerMode == 1 ? "配置过滤名单（点选=加/移·可多选·Esc完成）"
                : pickerMode == 2 ? "选择监测物品（中文/英文搜索）"
                : pickerMode == 3 ? "选择种植作物（可多选≤8，再点=取消）"
                : "选择目标产物（中文/英文搜索）";
        ctx.drawText(this.textRenderer, ptitle, px + 8, py + 8, TXT, false);
        List<String> selIds = java.util.Collections.emptyList();
        if (pickerMode == 1 || pickerMode == 3) { // m93：作物多选沿用白名单的已选高亮
            StructureCoreBlockEntity be3 = be();
            if (be3 != null && pickerNode >= 0 && pickerNode < be3.nodes().size())
                selIds = pickerMode == 1
                        ? StructureCoreBlockEntity.filterList(be3.nodes().get(pickerNode))
                        : StructureCoreBlockEntity.cropList(be3.nodes().get(pickerNode));
        }
        pickerField.setX(px + 8);
        pickerField.setY(py + 22);
        pickerField.render(ctx, mouseX, mouseY, delta);
        int gx = px + 8, gy = py + 44;
        Item hovered = null;
        for (int k = 0; k < pickerFiltered.size(); k++) {
            int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
            boolean hov = mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20;
            boolean sel = (pickerMode == 1 || pickerMode == 3) && selIds.contains(Registries.ITEM.getId(pickerFiltered.get(k)).toString());
            if (sel) ctx.fill(cx - 1, cy - 1, cx + 21, cy + 21, ON); // 多选已选=绿框
            ctx.fill(cx, cy, cx + 20, cy + 20, hov ? 0xFF14304A : sel ? 0xFF10321E : 0xFF0C1E30);
            ctx.drawItem(new ItemStack(pickerFiltered.get(k)), cx + 2, cy + 2);
            if (hov) hovered = pickerFiltered.get(k);
        }
        String tip = hovered != null ? new ItemStack(hovered).getName().getString() : "点击图标设为目标 · Esc 关闭";
        ctx.drawText(this.textRenderer, tip, px + 8, py + PICK_H - 14, hovered != null ? ON : SUB, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menuOpen && keyCode == 256) { clearMenu(); return true; }
        if (pickerNode >= 0) {
            if (keyCode == 256) { closePicker(); return true; }
            pickerField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pickerNode >= 0) return pickerField.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    private static class SciButton extends ButtonWidget {
        SciButton(int x, int y, int w, int h, Text t, PressAction a) {
            super(x, y, w, h, t, a, s -> s.get());
        }
        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean hover = this.isHovered();
            int border = hover ? 0xFF2EC4FF : 0xFF1C5A80;
            int fill = hover ? 0xFF123249 : 0xFF0C1E30;
            int tc = hover ? 0xFFE8FBFF : 0xFFBFD2EC;
            ctx.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, border);
            ctx.fill(getX(), getY(), getX() + width, getY() + height, fill);
            ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, tc);
        }
    }
}
