package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.net.NodeMovePayload;
import com.sdzjz.net.NodeRemovePayload;
import com.sdzjz.net.NodeTargetPayload;
import com.sdzjz.net.NodeUpgradePayload;
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
 * 结构核心画布界面（ComfyUI 式 · Phase4）：
 * 无上限机器节点，可平移/拖动/连线/缩放。机器经右键方块放入，潜行右键弹出，升级走每节点格子。
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
    private static final int NW = 100, NH = 52;
    private static final Item[] UPG = { ModItems.SPEED_UPGRADE, ModItems.COUNT_UPGRADE, ModItems.PARALLEL_UPGRADE };

    private static final java.util.Map<BlockPos, double[]> VIEW = new java.util.HashMap<>();

    private double panX = 0, panY = 0, zoom = 1.0;
    private int dragIndex = -1;
    private double dragOffX, dragOffY;
    private boolean linking = false;
    private int linkFrom = -1;

    // 自动合成机目标选择器
    private int pickerNode = -1;
    private TextFieldWidget pickerField;
    private List<Item> craftables;                       // 客户端配方表里所有可合成产物
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
        // 视角记忆：本次游戏内重开画布保持平移/缩放
        BlockPos p = this.handler.blockPos();
        if (p != null && VIEW.containsKey(p)) {
            double[] v = VIEW.get(p);
            panX = v[0]; panY = v[1]; zoom = v[2];
        }
        // 开机/停止放左下角，避开右上创造栏
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

    // 屏幕鼠标 → 世界坐标
    private double wmx(double mx) { return (mx - panX) / zoom; }
    private double wmy(double my) { return (my - panY) / zoom; }
    // 节点世界坐标（无平移/缩放）
    private int wnx(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) { return be.nodeX(nodes.get(i), 20 + (i % 6) * 112); }
    private int wny(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) { return be.nodeY(nodes.get(i), 20 + (i / 6) * 88); }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        ctx.drawTexture(FRAME, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height); // 科幻边框全屏拉伸
        // 网格（屏幕空间，随平移）
        int step = 32;
        int ox = ((int) panX) % step, oy = ((int) panY) % step;
        for (int x = ox; x < this.width; x += step) ctx.fill(x, 34, x + 1, this.height, GRID);
        for (int y = 34 + oy; y < this.height; y += step) ctx.fill(0, y, this.width, y + 1, GRID);

        StructureCoreBlockEntity be = be();
        if (be == null) return;
        List<ItemStack> nodes = be.nodes();

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(panX, panY, 0);
        m.scale((float) zoom, (float) zoom, 1);

        // 连线（节点下层）
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
        // 节点 + 升级格
        for (int i = 0; i < nodes.size(); i++) {
            int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
            drawNode(ctx, nx, ny, nodes.get(i));
            drawUpgradeSlots(ctx, be, nx, ny, nodes.get(i));
        }
        m.pop();
    }

    private void drawNode(DrawContext ctx, int x, int y, ItemStack st) {
        ctx.fill(x - 1, y - 1, x + NW + 1, y + NH + 1, NODEFRM);
        ctx.fill(x, y, x + NW, y + NH, NODEBG);
        ctx.fill(x, y, x + NW, y + 3, CYAN);
        ctx.fill(x - 4, y + NH / 2 - 3, x + 2, y + NH / 2 + 3, CYAN);      // 输入口
        ctx.fill(x + NW - 2, y + NH / 2 - 3, x + NW + 4, y + NH / 2 + 3, ON); // 输出口
        ctx.drawItem(st, x + 8, y + 16);
        String name = st.getName().getString();
        if (this.textRenderer.getWidth(name) > NW - 12) {
            while (name.length() > 1 && this.textRenderer.getWidth(name + "…") > NW - 12) name = name.substring(0, name.length() - 1);
            name = name + "…";
        }
        ctx.drawText(this.textRenderer, name, x + 6, y + 6, TXT, false);
        ctx.drawText(this.textRenderer, "×" + st.getCount(), x + 30, y + 20, CYAN, false);
        if (st.getItem() instanceof AutoCrafterItem) { // 目标徽章：点击选产物
            int bx = x + NW - 30, by = y + 14;
            ctx.fill(bx - 1, by - 1, bx + 21, by + 21, NODEFRM);
            ctx.fill(bx, by, bx + 20, by + 20, 0xFF0C1E30);
            String t = StructureCoreBlockEntity.craftTarget(st);
            if (!t.isEmpty()) {
                ItemStack ts = new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(t)));
                ctx.drawItem(ts, bx + 2, by + 2);
                String tn = ts.getName().getString();
                while (tn.length() > 1 && this.textRenderer.getWidth("→" + tn) > NW - 12) tn = tn.substring(0, tn.length() - 1);
                ctx.drawText(this.textRenderer, "→" + tn, x + 6, y + 40, ON, false);   // 目标名
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

    /** 每节点下方 3 个升级格：加速/数量/并列（图标+等级）。 */
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

    /** 采样三次贝塞尔画连线（ComfyUI 水平切线风格）。 */
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
        String st = "机器 " + this.handler.machineCount()
                + "  升级∑ 加速" + this.handler.speedLv()
                + " 数量" + this.handler.countLv()
                + " 并列" + this.handler.parallelLv()
                + "  缩放" + String.format("%.1f", zoom) + "x";
        ctx.drawText(this.textRenderer, st, 130, 12, SUB, false);
        ctx.drawText(this.textRenderer, "右键核心=放入 · 拖节点=移动 · 右键节点=取出 · 拖绿口=连线 · 点[?]徽章=设合成目标 · 格左键加/右键取升级 · 滚轮缩放", 8, this.height - 12, SUB, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (pickerNode >= 0) return true;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pickerNode >= 0) { // 选择器打开时吞掉所有点击
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
                    // 右键节点体 → 取出该机器
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            BlockPos p = this.handler.blockPos();
                            if (p != null) ClientPlayNetworking.send(new NodeRemovePayload(p, i));
                            return true;
                        }
                    }
                }
                if (button == 0) {
                    // 自动合成机目标徽章 → 打开选择器
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        if (!(nodes.get(i).getItem() instanceof AutoCrafterItem)) continue;
                        int bx = wnx(be, nodes, i) + NW - 30, by = wny(be, nodes, i) + 14;
                        if (wx >= bx && wx <= bx + 20 && wy >= by && wy <= by + 20) {
                            openPicker(i);
                            return true;
                        }
                    }
                    // 输出口(绿) → 连线
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int oxp = wnx(be, nodes, i) + NW, oyp = wny(be, nodes, i) + NH / 2;
                        if (Math.abs(wx - oxp) <= 7 && Math.abs(wy - oyp) <= 7) {
                            linking = true; linkFrom = i; return true;
                        }
                    }
                    // 节点体 → 拖动
                    for (int i = nodes.size() - 1; i >= 0; i--) {
                        int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                        if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) {
                            dragIndex = i; dragOffX = wx - nx; dragOffY = wy - ny; return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (pickerNode >= 0) return true;
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
                double wx = wmx(mouseX), wy = wmy(mouseY);
                int target = -1;
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    int nx = wnx(be, nodes, i), ny = wny(be, nodes, i);
                    if (wx >= nx && wx <= nx + NW && wy >= ny && wy <= ny + NH) { target = i; break; }
                }
                if (target >= 0 && target != linkFrom) {
                    ClientPlayNetworking.send(new NodeLinkPayload(p, linkFrom, target));
                }
            }
            linking = false; linkFrom = -1;
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

    /** 从客户端配方表收集所有可合成的产物（配方已同步到客户端）。 */
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
        if (pickerNode >= 0) {
            if (keyCode == 256) { closePicker(); return true; } // Esc 关选择器而非界面
            pickerField.keyPressed(keyCode, scanCode, modifiers);
            return true; // 吞掉（含背包键），防误关界面
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pickerNode >= 0) return pickerField.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    /** 深色青边发光按钮。 */
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
