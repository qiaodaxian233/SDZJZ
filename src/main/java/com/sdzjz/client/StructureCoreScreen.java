package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.net.NodeLinkPayload;
import com.sdzjz.net.NodeMovePayload;
import com.sdzjz.screen.StructureCoreScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 结构核心画布界面（ComfyUI 式 · Phase3）：
 * 机器为无上限「节点」，可平移、拖动、连线。机器/升级经右键方块放入，潜行右键弹出。
 * 连线运行效果（A输出喂B）为后续阶段；缩放为后续阶段。
 */
public class StructureCoreScreen extends HandledScreen<StructureCoreScreenHandler> {

    private static final int BACKDROP = 0xFF080B12;
    private static final int GRID     = 0x22284A6B;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int ON       = 0xFF33D07A;
    private static final int CYAN     = 0xFF2EC4FF;
    private static final int NODEBG   = 0xE00A1626;
    private static final int NODEFRM  = 0xFF1C5A80;
    private static final int NW = 100, NH = 52;

    private double panX = 0, panY = 0;
    private int dragIndex = -1, dragOffX, dragOffY;
    private boolean linking = false;
    private int linkFrom = -1;

    public StructureCoreScreen(StructureCoreScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new SciButton(this.width - 220, 8, 100, 18, Text.literal("▶ 开机"), b -> click(0)));
        this.addDrawableChild(new SciButton(this.width - 112, 8, 100, 18, Text.literal("■ 停止"), b -> click(1)));
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

    // 节点在屏幕上的左上角坐标（含平移）
    private int nsx(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) {
        return be.nodeX(nodes.get(i), 20 + (i % 6) * 112) + (int) panX;
    }
    private int nsy(StructureCoreBlockEntity be, List<ItemStack> nodes, int i) {
        return be.nodeY(nodes.get(i), 60 + (i / 6) * 66) + (int) panY;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        // 画布网格（随平移）
        int step = 32;
        int ox = ((int) panX) % step, oy = ((int) panY) % step;
        for (int x = ox; x < this.width; x += step) ctx.fill(x, 34, x + 1, this.height, GRID);
        for (int y = 34 + oy; y < this.height; y += step) ctx.fill(0, y, this.width, y + 1, GRID);

        StructureCoreBlockEntity be = be();
        if (be != null) {
            List<ItemStack> nodes = be.nodes();
            // 连线（画在节点下层）
            for (int[] c : be.connections()) {
                if (c[0] < nodes.size() && c[1] < nodes.size()) {
                    int ax = nsx(be, nodes, c[0]) + NW, ay = nsy(be, nodes, c[0]) + NH / 2;
                    int bx = nsx(be, nodes, c[1]),      by = nsy(be, nodes, c[1]) + NH / 2;
                    drawWire(ctx, ax, ay, bx, by, CYAN);
                }
            }
            // 拖拽中的临时连线
            if (linking && linkFrom >= 0 && linkFrom < nodes.size()) {
                int ax = nsx(be, nodes, linkFrom) + NW, ay = nsy(be, nodes, linkFrom) + NH / 2;
                drawWire(ctx, ax, ay, mouseX, mouseY, 0xFF88E0FF);
            }
            // 节点
            for (int i = 0; i < nodes.size(); i++) {
                drawNode(ctx, nsx(be, nodes, i), nsy(be, nodes, i), nodes.get(i));
            }
        }
    }

    private void drawNode(DrawContext ctx, int x, int y, ItemStack st) {
        ctx.fill(x - 1, y - 1, x + NW + 1, y + NH + 1, NODEFRM);
        ctx.fill(x, y, x + NW, y + NH, NODEBG);
        ctx.fill(x, y, x + NW, y + 3, CYAN); // 顶条
        // 端口：左输入(青) 右输出(绿)
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
        String st = "机器 " + this.handler.machineCount()
                + "  速度Lv" + this.handler.speedLv()
                + " 数量Lv" + this.handler.countLv()
                + " 并发Lv" + this.handler.parallelLv();
        ctx.drawText(this.textRenderer, st, 130, 12, SUB, false);
        ctx.drawText(this.textRenderer, "右键核心=放入 · 拖节点=移动 · 拖绿口→另一节点=连/断 · 拖空白=平移", 10, this.height - 12, SUB, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY > 34) {
            StructureCoreBlockEntity be = be();
            if (be != null) {
                List<ItemStack> nodes = be.nodes();
                // 先判输出口(绿) → 开始连线
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    int ox = nsx(be, nodes, i) + NW, oy = nsy(be, nodes, i) + NH / 2;
                    if (Math.abs(mouseX - ox) <= 6 && Math.abs(mouseY - oy) <= 6) {
                        linking = true; linkFrom = i; return true;
                    }
                }
                // 再判节点体 → 拖动
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    int nx = nsx(be, nodes, i), ny = nsy(be, nodes, i);
                    if (mouseX >= nx && mouseX <= nx + NW && mouseY >= ny && mouseY <= ny + NH) {
                        dragIndex = i; dragOffX = (int) mouseX - nx; dragOffY = (int) mouseY - ny; return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (linking) return true; // 临时连线跟随鼠标，在 drawBackground 里画
        if (button == 0 && dragIndex >= 0) {
            StructureCoreBlockEntity be = be();
            if (be != null && dragIndex < be.nodes().size()) {
                int nx = (int) mouseX - dragOffX - (int) panX;
                int ny = (int) mouseY - dragOffY - (int) panY;
                be.setNodePos(dragIndex, nx, ny); // 本地即时视觉
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
                int target = -1;
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    int nx = nsx(be, nodes, i), ny = nsy(be, nodes, i);
                    if (mouseX >= nx && mouseX <= nx + NW && mouseY >= ny && mouseY <= ny + NH) { target = i; break; }
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
