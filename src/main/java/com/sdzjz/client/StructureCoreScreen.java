package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.net.NodeMovePayload;
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
    private static final String[] KIND = {"绑定", "有线", "无线", "卫星", "离线", "终端"};
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
    private TextFieldWidget pickerField;
    private List<Item> craftables;
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
    private int snx(StructureCoreBlockEntity be, long pl, int j) { return be.storageNodeX(pl, 760); }
    private int sny(StructureCoreBlockEntity be, long pl, int j) { return be.storageNodeY(pl, 20 + j * 72); }

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
        // 机器↔存储 定向连线
        for (long[] e : be.storageEdgesView()) {
            int mi = (int) e[0];
            if (mi >= nodes.size()) continue;
            int j = endpointIndex(ends, e[1]);
            int sx = snx(be, e[1], j < 0 ? 0 : j), sy = sny(be, e[1], j < 0 ? 0 : j);
            if (e[2] == 0) { // 机器→存储（产出）
                drawWire(ctx, wnx(be, nodes, mi) + NW, wny(be, nodes, mi) + NH / 2, sx, sy + SH / 2, CYAN);
            } else {         // 存储→机器（供料）
                drawWire(ctx, sx + SW, sy + SH / 2, wnx(be, nodes, mi), wny(be, nodes, mi) + NH / 2, ON);
            }
        }
        if (linking && linkFrom >= 0 && linkFrom < nodes.size()) {
            int ax = wnx(be, nodes, linkFrom) + NW, ay = wny(be, nodes, linkFrom) + NH / 2;
            drawWire(ctx, ax, ay, (int) wmx(mouseX), (int) wmy(mouseY), 0xFF88E0FF);
        }
        if (linking && linkStor != Long.MIN_VALUE) {
            int j = endpointIndex(ends, linkStor);
            int sx = snx(be, linkStor, Math.max(j, 0)), sy = sny(be, linkStor, Math.max(j, 0));
            drawWire(ctx, sx + SW, sy + SH / 2, (int) wmx(mouseX), (int) wmy(mouseY), 0xFF9BF0C0);
        }
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            drawNode(ctx, nx, ny, nodes.get(i));
            drawUpgradeSlots(ctx, be, nx, ny, nodes.get(i));
        }
        for (int j = 0; j < ends.size(); j++) drawStorageNode(ctx, be, ends.get(j), j);
        m.pop();
    }

    private static int endpointIndex(List<long[]> ends, long pl) {
        for (int j = 0; j < ends.size(); j++) if (ends.get(j)[0] == pl) return j;
        return -1;
    }

    /** 存储/终端接口节点：连了几个显示几个。 */
    private void drawStorageNode(DrawContext ctx, StructureCoreBlockEntity be, long[] ep, int j) {
        long pl = ep[0];
        int kind = (int) ep[1];
        int x = snx(be, pl, j), y = sny(be, pl, j);
        int frm = kind == 5 ? TERMFRM : kind == 4 ? OFFFRM : STORFRM;
        ctx.fill(x - 1, y - 1, x + SW + 1, y + SH + 1, frm);
        ctx.fill(x, y, x + SW, y + SH, NODEBG);
        ctx.fill(x, y, x + SW, y + 3, frm);
        if (kind != 5) { // 终端只展示，不可连线
            ctx.fill(x - 4, y + SH / 2 - 3, x + 2, y + SH / 2 + 3, CYAN);       // 收料口（机器→存储）
            ctx.fill(x + SW - 2, y + SH / 2 - 3, x + SW + 4, y + SH / 2 + 3, ON); // 供料口（存储→机器）
        }
        ItemStack icon = new ItemStack(kind == 5 ? com.sdzjz.registry.ModBlocks.DATA_PANEL.asItem()
                : com.sdzjz.registry.ModBlocks.STORAGE_CORE.asItem());
        ctx.drawItem(icon, x + 4, y + 12);
        BlockPos bp = BlockPos.fromLong(pl);
        String title = kind == 5 ? "数据终端" : "存储核心";
        ctx.drawText(this.textRenderer, title, x + 24, y + 7, TXT, false);
        ctx.drawText(this.textRenderer, "[" + KIND[Math.min(kind, 5)] + "]", x + 24 + this.textRenderer.getWidth(title) + 4, y + 7,
                kind == 4 ? SUB : kind == 5 ? 0xFFB9A0F0 : ON, false);
        String sub = bp.getX() + "," + bp.getY() + "," + bp.getZ();
        if (this.client != null && this.client.world != null
                && this.client.world.getBlockEntity(bp) instanceof StorageCoreBlockEntity sc) {
            sub += "  类型 " + sc.usedTypes() + "/" + sc.maxTypes();
        }
        ctx.drawText(this.textRenderer, sub, x + 24, y + 22, SUB, false);
    }

    private void drawNode(DrawContext ctx, int x, int y, ItemStack st) {
        ctx.fill(x - 1, y - 1, x + NW + 1, y + NH + 1, NODEFRM);
        ctx.fill(x, y, x + NW, y + NH, NODEBG);
        ctx.fill(x, y, x + NW, y + 3, CYAN);
        ctx.fill(x - 4, y + NH / 2 - 3, x + 2, y + NH / 2 + 3, CYAN);
        ctx.fill(x + NW - 2, y + NH / 2 - 3, x + NW + 4, y + NH / 2 + 3, ON);
        ctx.drawItem(st, x + 8, y + 16);
        String name = st.getName().getString();
        if (this.textRenderer.getWidth(name) > NW - 12) {
            while (name.length() > 1 && this.textRenderer.getWidth(name + "…") > NW - 12) name = name.substring(0, name.length() - 1);
            name = name + "…";
        }
        ctx.drawText(this.textRenderer, name, x + 6, y + 6, TXT, false);
        ctx.drawText(this.textRenderer, "×" + st.getCount(), x + 30, y + 20, CYAN, false);
        if (st.getItem() instanceof AutoCrafterItem) {
            int bx = x + NW - 30, by = y + 14;
            ctx.fill(bx - 1, by - 1, bx + 21, by + 21, NODEFRM);
            ctx.fill(bx, by, bx + 20, by + 20, 0xFF0C1E30);
            String t = StructureCoreBlockEntity.craftTarget(st);
            if (!t.isEmpty()) {
                ItemStack ts = new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(t)));
                ctx.drawItem(ts, bx + 2, by + 2);
                String tn = ts.getName().getString();
                while (tn.length() > 1 && this.textRenderer.getWidth("→" + tn) > NW - 12) tn = tn.substring(0, tn.length() - 1);
                ctx.drawText(this.textRenderer, "→" + tn, x + 6, y + 40, ON, false);
            } else {
                ctx.drawText(this.textRenderer, "?", bx + 7, by + 6, SUB, false);
                ctx.drawText(this.textRenderer, "点徽章设目标", x + 6, y + 40, SUB, false);
            }
        }
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
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps, u = 1 - t;
            float bx = u * u * u * x1 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x2;
            float by = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            int px = (int) bx, py = (int) by;
            ctx.fill(px - 1, py - 1, px + 1, py + 1, color);
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
        if (be != null) for (long[] e : be.storageEndpointsView()) { if (e[1] == 5) term++; else stor++; }
        String st = "机器 " + this.handler.machineCount()
                + "  存储 " + stor + " · 终端 " + term
                + "  升级∑ 加速" + this.handler.speedLv()
                + " 数量" + this.handler.countLv()
                + " 并列" + this.handler.parallelLv()
                + "  缩放" + String.format("%.1f", zoom) + "x";
        ctx.drawText(this.textRenderer, st, 130, 12, SUB, false);
        ctx.drawText(this.textRenderer, "右键节点/空白=菜单 · 拖节点=移动 · 拖绿口=连线(拖到存储=定向产出,存储绿口拖到机器=定向供料) · 格左键加/右键取升级 · 滚轮缩放", 8, this.height - 12, SUB, false);
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
                    if (bp != null) ClientPlayNetworking.send(new NodeTargetPayload(bp, pickerNode,
                            Registries.ITEM.getId(pickerFiltered.get(k)).toString()));
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
                            addMenu("取消", () -> {});
                            openMenu((int) mouseX, (int) mouseY);
                            return true;
                        }
                    }
                    // 右键存储节点 → 菜单
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (wx >= sx && wx <= sx + SW && wy >= sy && wy <= sy + SH) {
                            clearMenu();
                            addMenu("断开全部连线", () -> clearLinksOfStorage(pl));
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
                    // 自动合成机目标徽章
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        if (!(nodes.get(i).getItem() instanceof AutoCrafterItem)) continue;
                        int bx = wnx(be, nodes, i) + NW - 30, by = wny(be, nodes, i) + 14;
                        if (wx >= bx && wx <= bx + 20 && wy >= by && wy <= by + 20) {
                            openPicker(i);
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
                    // 存储供料口(绿) → 连线（存储→机器）
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        if (ends.get(j)[1] == 5) continue;
                        long pl = ends.get(j)[0];
                        int oxp = snx(be, pl, j) + SW, oyp = sny(be, pl, j) + SH / 2;
                        if (Math.abs(wx - oxp) <= 7 && Math.abs(wy - oyp) <= 7) {
                            linking = true; linkStor = pl; linkFrom = -1; return true;
                        }
                    }
                    // 机器节点体 → 拖动
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            dragIndex = i; dragStor = Long.MIN_VALUE; dragOffX = wx - nx; dragOffY = wy - ny; return true;
                        }
                    }
                    // 存储节点体 → 拖动
                    for (int j = ends.size() - 1; j >= 0; j--) {
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (wx >= sx && wx <= sx + SW && wy >= sy && wy <= sy + SH) {
                            dragStor = pl; dragIndex = -1; dragOffX = wx - sx; dragOffY = wy - sy; return true;
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
        if (button == 0 && dragStor != Long.MIN_VALUE) {
            StructureCoreBlockEntity be = be();
            if (be != null) {
                int nx = (int) (wmx(mouseX) - dragOffX);
                int ny = (int) (wmy(mouseY) - dragOffY);
                be.setStorageNodePos(dragStor, nx, ny); // 客户端本地视觉；松手发包保存
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
                        if (ends.get(j)[1] == 5) continue;
                        long pl = ends.get(j)[0];
                        int sx = snx(be, pl, j), sy = sny(be, pl, j);
                        if (wx >= sx && wx <= sx + SW && wy >= sy && wy <= sy + SH) {
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
        if (button == 0 && dragStor != Long.MIN_VALUE) {
            StructureCoreBlockEntity be = be();
            BlockPos p = this.handler.blockPos();
            if (be != null && p != null) {
                ClientPlayNetworking.send(new StorageNodeMovePayload(p, dragStor,
                        be.storageNodeX(dragStor, 0), be.storageNodeY(dragStor, 0)));
            }
            dragStor = Long.MIN_VALUE;
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
        pickerNode = node;
        if (craftables == null) buildCraftables();
        pickerField.setText("");
        refilterPicker();
        this.setFocused(pickerField);
        pickerField.setFocused(true);
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
        if (craftables == null) return;
        String q = pickerField.getText().trim().toLowerCase();
        for (Item it : craftables) {
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
        ctx.drawText(this.textRenderer, "选择目标产物（中文/英文搜索）", px + 8, py + 8, TXT, false);
        pickerField.setX(px + 8);
        pickerField.setY(py + 22);
        pickerField.render(ctx, mouseX, mouseY, delta);
        int gx = px + 8, gy = py + 44;
        Item hovered = null;
        for (int k = 0; k < pickerFiltered.size(); k++) {
            int cx = gx + (k % PICK_COLS) * 21, cy = gy + (k / PICK_COLS) * 21;
            boolean hov = mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20;
            ctx.fill(cx, cy, cx + 20, cy + 20, hov ? 0xFF14304A : 0xFF0C1E30);
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
