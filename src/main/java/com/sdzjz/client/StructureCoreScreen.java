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
    private static final int SW = 104, SH = 40;       // 存储节点尺寸
    private static final String[] KIND = {"绑定", "有线", "无线", "卫星", "离线", "终端", "接口"};
    private static final Item[] UPG = { ModItems.SPEED_UPGRADE, ModItems.COUNT_UPGRADE, ModItems.PARALLEL_UPGRADE };

    private static final java.util.Map<BlockPos, double[]> VIEW = new java.util.HashMap<>();

    private double panX = 0, panY = 0, zoom = 1.0;
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
        this.addDrawableChild(new SciButton(8, this.height - 56, 90, 20, Text.literal("▶ 开机"), b -> click(0)));
        this.addDrawableChild(new SciButton(104, this.height - 56, 90, 20, Text.literal("■ 停止"), b -> click(1)));
        this.addDrawableChild(new SciButton(200, this.height - 56, 96, 20, Text.literal("★ 领取经验"), b -> click(2)));
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
    // m78：端点节点钉在屏幕右侧「端点停靠栏」（屏幕坐标，不随画布平移缩放跑）。
    // 之前画在画布坐标里，视角一拖就丢到屏幕外——看起来像"没有输出接口/面板节点"。列满自动向左换列。
    private int dockRows() { return Math.max(1, (this.height - 62) / (SH + 14)); }
    private int snx(StructureCoreBlockEntity be, long pl, int j) { return this.width - SW - 12 - (j / dockRows()) * (SW + 12); }
    private int sny(StructureCoreBlockEntity be, long pl, int j) { return 50 + (j % dockRows()) * (SH + 14); }

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
        List<long[]> ends = be.storageEndpointsView();

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
        }
        m.pop();

        // ===== 端点停靠栏：屏幕坐标绘制，永远可见 =====
        if (!ends.isEmpty()) {
            int cols = (ends.size() + dockRows() - 1) / dockRows();
            int left = this.width - cols * (SW + 12);
            ctx.fill(left - 6, 38, this.width, 50 + Math.min(ends.size(), dockRows()) * (SH + 14) + 4, 0x66060B14);
            ctx.drawText(this.textRenderer, "端点接口", left, 40, SUB, false);
        }
        // 机器↔存储 定向连线：机器端做 画布→屏幕 换算，存储端已是屏幕坐标
        for (long[] e : be.storageEdgesView()) {
            int mi = (int) e[0];
            if (mi >= nodes.size()) continue;
            int j = endpointIndex(ends, e[1]);
            if (j < 0) continue; // 端点不在列表=不画，杜绝悬空线
            int sx = snx(be, e[1], j), sy = sny(be, e[1], j);
            int mys = (int) (panY + (wny(be, nodes, mi) + NH / 2.0) * zoom);
            if (e[2] == 0) { // 机器→存储（产出）
                int mxs = (int) (panX + (wnx(be, nodes, mi) + NW) * zoom);
                drawWire(ctx, mxs, mys, sx, sy + SH / 2, CYAN);
            } else {         // 存储→机器（供料）
                int mxi = (int) (panX + wnx(be, nodes, mi) * zoom);
                drawWire(ctx, sx + SW, sy + SH / 2, mxi, mys, ON);
            }
        }
        if (linking && linkStor != Long.MIN_VALUE) {
            int j = endpointIndex(ends, linkStor);
            int sx = snx(be, linkStor, Math.max(j, 0)), sy = sny(be, linkStor, Math.max(j, 0));
            drawWire(ctx, sx + SW, sy + SH / 2, mouseX, mouseY, 0xFF9BF0C0);
        }
        for (int j = 0; j < ends.size(); j++)
            drawStorageNode(ctx, be, ends.get(j), j, j < be.storageEndpointDimsView().size() ? be.storageEndpointDimsView().get(j) : "");
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
        ctx.fill(x - 1, y - 1, x + SW + 1, y + SH + 1, frm);
        ctx.fill(x, y, x + SW, y + SH, NODEBG);
        ctx.fill(x, y, x + SW, y + 3, frm);
        ctx.fill(x - 4, y + SH / 2 - 3, x + 2, y + SH / 2 + 3, CYAN);            // 收料口（连到面板=存进它聚合的整个网络）
        if (!iface) ctx.fill(x + SW - 2, y + SH / 2 - 3, x + SW + 4, y + SH / 2 + 3, ON); // 供料口（输出接口无）
        ItemStack icon = new ItemStack(iface ? com.sdzjz.registry.ModBlocks.SATELLITE_NODE.asItem()
                : kind == 5 ? com.sdzjz.registry.ModBlocks.DATA_PANEL.asItem()
                : com.sdzjz.registry.ModBlocks.STORAGE_CORE.asItem());
        var msn = ctx.getMatrices();
        msn.push();
        msn.translate(x + 3, y + 8, 0);
        msn.scale(1.5f, 1.5f, 1f);
        ctx.drawItem(icon, 0, 0);
        msn.pop();
        String title = iface ? "输出接口" : kind == 5 ? "数据面板" : "存储核心";
        ctx.drawText(this.textRenderer, title, x + 32, y + 7, TXT, false);
        ctx.drawText(this.textRenderer, "[" + KIND[Math.min(kind, 6)] + "]", x + 32 + this.textRenderer.getWidth(title) + 4, y + 7,
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
                sub += "  类型 " + sc.usedTypes() + "/" + sc.maxTypes(); // 仅同维度读数
            }
        }
        ctx.drawText(this.textRenderer, sub, x + 32, y + 22, SUB, false);
    }

    private void drawNode(DrawContext ctx, StructureCoreBlockEntity be, int i, int x, int y, ItemStack st) {
        ctx.fill(x - 1, y - 1, x + NW + 1, y + NH + 1, NODEFRM);
        ctx.fill(x, y, x + NW, y + NH, NODEBG);
        ctx.fill(x, y, x + NW, y + 3, CYAN);
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
            String t = StructureCoreBlockEntity.craftTarget(st);
            if (!t.isEmpty()) {
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
        ctx.fill(0, 0, this.width, 34, 0xEE0A121F);
        ctx.fill(0, 33, this.width, 34, CYAN);
        String tierName = this.handler.tier() >= 2 ? "超大工作台 · 画布" : "结构核心 · 画布";
        ctx.drawText(this.textRenderer, tierName, 10, 6, TXT, false);
        boolean run = this.handler.isRunning();
        ctx.drawText(this.textRenderer, run ? "● 运行中" : "○ 已停止", 10, 18, run ? ON : SUB, false);
        ctx.drawText(this.textRenderer, "经验 " + fmtNum(this.handler.xp()), 72, 18, ON, false);
        int stor = 0, term = 0;
        StructureCoreBlockEntity be = be();
        if (be != null) for (long[] e : be.storageEndpointsView()) { if (e[1] == 5) term++; else if (e[1] != 6) stor++; }
        String st = "机器 " + this.handler.machineCount()
                + "  存储 " + stor + " · 终端 " + term
                + "  缓存 " + fmtNum(this.handler.buffered())
                + "  升级∑ 加速" + this.handler.speedLv()
                + " 数量" + this.handler.countLv()
                + " 并列" + this.handler.parallelLv()
                + "  缩放" + String.format("%.1f", zoom) + "x";
        ctx.drawText(this.textRenderer, st, 130, 12, SUB, false);
        ctx.drawText(this.textRenderer, "右键=菜单 · 拖节点=移动 · 绿口拖线 · 滚轮缩放 · 状态灯 绿=运行 黄=阻塞/关闸 红=缺料 · 过滤/传感器右键配置", 8, this.height - 12, SUB, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (pickerNode >= 0 || menuOpen) return true;
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
        menuX = Math.min(x, this.width - MENU_W - 4);
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
        List<String> dims = be.storageEndpointDimsView();
        List<long[]> ends = be.storageEndpointsView();
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
        List<String> dims = be.storageEndpointDimsView();
        List<long[]> ends = be.storageEndpointsView();
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
        List<long[]> ends = be.storageEndpointsView();
        for (int j = 0; j < ends.size(); j++)
            ClientPlayNetworking.send(new StorageNodeMovePayload(p, ends.get(j)[0], 760, 20 + j * 72));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                    }
                    closePicker();
                    return true;
                }
            }
            if (mouseX < px || mouseX > px + PICK_W || mouseY < py || mouseY > py + PICK_H) closePicker();
            return true;
        }
        if (mouseY > 34 && (button == 0 || button == 1)) {
            StructureCoreBlockEntity be = be();
            if (be != null) {
                List<ItemStack> nodes = be.nodes();
                List<long[]> ends = be.storageEndpointsView();
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
                            if (p != null) ClientPlayNetworking.send(new NodeUpgradePayload(p, i, k, button == 0));
                            return true;
                        }
                    }
                }
                if (button == 1) {
                    // 停靠栏优先：右键端点节点 → 菜单（屏幕坐标）
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx && mouseX <= sx + SW && mouseY >= sy && mouseY <= sy + SH) {
                            clearMenu();
                            addMenu("断开全部连线", () -> clearLinksOfStorage(pl));
                            addMenu("取消", () -> {});
                            openMenu((int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // 右键机器节点 → 菜单
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            final int idx = i;
                            BlockPos p = this.handler.blockPos();
                            clearMenu();
                            addMenu("取出机器", () -> { if (p != null) ClientPlayNetworking.send(new NodeRemovePayload(p, idx)); });
                            addMenu("断开全部连线", () -> clearLinksOfMachine(idx));
                            if (nodes.get(i).getItem() instanceof AutoCrafterItem)
                                addMenu("选择合成目标", () -> openPicker(idx));
                            if (nodes.get(i).getItem() instanceof com.sdzjz.item.CropFarmItem)
                                addMenu("选择种植作物", () -> openCropPicker(idx));
                            if (StructureCoreBlockEntity.isFilter(nodes.get(i))) {
                                addMenu("配置过滤物品…", () -> openFilterPicker(idx));
                                addMenu(StructureCoreBlockEntity.filterBlacklist(nodes.get(i)) ? "切为白名单" : "切为黑名单",
                                        () -> { if (p != null) ClientPlayNetworking.send(new NodeFilterPayload(p, idx, "")); });
                            }
                            if (StructureCoreBlockEntity.isSwitch(nodes.get(i))) {
                                addMenu(StructureCoreBlockEntity.switchOn(nodes.get(i)) ? "切为:关闭" : "切为:开启",
                                        () -> { if (p != null) ClientPlayNetworking.send(new NodeSwitchPayload(p, idx)); });
                            }
                            if (StructureCoreBlockEntity.isSensor(nodes.get(i))) {
                                final ItemStack ns = nodes.get(i);
                                addMenu("监测物品…", () -> openSensorPicker(idx));
                                addMenu(StructureCoreBlockEntity.sensorLess(ns) ? "改为:高于阈值放行" : "改为:低于阈值放行",
                                        () -> { if (p != null) ClientPlayNetworking.send(new NodeSensorPayload(p, idx, "",
                                                StructureCoreBlockEntity.sensorThreshold(ns), !StructureCoreBlockEntity.sensorLess(ns))); });
                            }
                            addMenu("取消", () -> {});
                            openMenu((int) mouseX, (int) mouseY);
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
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        if (ends.get(j)[1] == 6) continue; // 输出接口无供料口
                        long pl = ends.get(j)[0];
                        int oxp = snx(be, pl, j) + SW, oyp = sny(be, pl, j) + SH / 2;
                        if (Math.abs(mouseX - oxp) <= 7 && Math.abs(mouseY - oyp) <= 7) {
                            linking = true; linkStor = pl; linkFrom = -1; return true;
                        }
                    }
                    // 停靠栏节点体：吞掉点击，防误触其下的机器/画布拖动
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx - 4 && mouseX <= sx + SW + 6 && mouseY >= sy && mouseY <= sy + SH) return true;
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
        if (button == 0 && linking) {
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null) {
                List<ItemStack> nodes = be.nodes();
                List<long[]> ends = be.storageEndpointsView();
                List<String> dims = be.storageEndpointDimsView();
                double wx = wmx(mouseX), wy = wmy(mouseY);
                if (linkFrom >= 0) {
                    // 优先看是否落在存储节点上 → 机器→存储 定向产出
                    boolean done = false;
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0]; // 数据面板也可连（存进它聚合的整个网络）
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (mouseX >= sx && mouseX <= sx + SW && mouseY >= sy && mouseY <= sy + SH) {
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
        for (Item it : src) {
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
                : pickerMode == 3 ? "选择种植作物"
                : "选择目标产物（中文/英文搜索）";
        ctx.drawText(this.textRenderer, ptitle, px + 8, py + 8, TXT, false);
        List<String> selIds = java.util.Collections.emptyList();
        if (pickerMode == 1) {
            StructureCoreBlockEntity be3 = be();
            if (be3 != null && pickerNode >= 0 && pickerNode < be3.nodes().size())
                selIds = StructureCoreBlockEntity.filterList(be3.nodes().get(pickerNode));
        }
        pickerField.setX(px + 8);
        pickerField.setY(py + 22);
        pickerField.render(ctx, mouseX, mouseY, delta);
        int gx = px + 8, gy = py + 44;
        Item hovered = null;
        for (int k = 0; k < pickerFiltered.size(); k++) {
            int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
            boolean hov = mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20;
            boolean sel = pickerMode == 1 && selIds.contains(Registries.ITEM.getId(pickerFiltered.get(k)).toString());
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
