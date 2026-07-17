package com.sdzjz.client;

import com.sdzjz.block.StructureCoreBlockEntity;
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
 * 结构核心画布界面（ComfyUI 式 · Phase1）：
 * 机器为无上限「节点」，画布可平移；显示状态、开机/停止。
 * 拖动/连线/缩放为后续阶段。机器/升级经右键方块放入，潜行右键弹出。
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

    private double panX = 0, panY = 0;
    private int dragIndex = -1, dragOffX, dragOffY;

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

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        // 画布网格（随平移）
        int step = 32;
        int ox = ((int) panX) % step, oy = ((int) panY) % step;
        for (int x = ox; x < this.width; x += step) ctx.fill(x, 34, x + 1, this.height, GRID);
        for (int y = 34 + oy; y < this.height; y += step) ctx.fill(0, y, this.width, y + 1, GRID);

        // 节点（按各自保存的画布坐标）
        StructureCoreBlockEntity be = be();
        if (be != null) {
            List<ItemStack> nodes = be.nodes();
            for (int i = 0; i < nodes.size(); i++) {
                ItemStack st = nodes.get(i);
                int nx = be.nodeX(st, 20 + (i % 6) * 112) + (int) panX;
                int ny = be.nodeY(st, 60 + (i / 6) * 66) + (int) panY;
                drawNode(ctx, nx, ny, st);
            }
        }
    }

    private void drawNode(DrawContext ctx, int x, int y, ItemStack st) {
        int w = 100, h = 52;
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, NODEFRM);
        ctx.fill(x, y, x + w, y + h, NODEBG);
        // 青色顶条
        ctx.fill(x, y, x + w, y + 3, CYAN);
        ctx.drawItem(st, x + 8, y + 16);
        String name = st.getName().getString();
        if (this.textRenderer.getWidth(name) > w - 12) {
            while (name.length() > 1 && this.textRenderer.getWidth(name + "…") > w - 12) name = name.substring(0, name.length() - 1);
            name = name + "…";
        }
        ctx.drawText(this.textRenderer, name, x + 6, y + 6, TXT, false);
        ctx.drawText(this.textRenderer, "×" + st.getCount(), x + 30, y + 20, CYAN, false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // 顶栏（屏幕坐标，固定）
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
        ctx.drawText(this.textRenderer, "右键核心=放入 · 潜行右键=弹出 · 拖节点=移动 · 拖空白=平移", 10, this.height - 12, SUB, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY > 34) {
            StructureCoreBlockEntity be = be();
            if (be != null) {
                List<ItemStack> nodes = be.nodes();
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    ItemStack st = nodes.get(i);
                    int nx = be.nodeX(st, 20 + (i % 6) * 112) + (int) panX;
                    int ny = be.nodeY(st, 60 + (i / 6) * 66) + (int) panY;
                    if (mouseX >= nx && mouseX <= nx + 100 && mouseY >= ny && mouseY <= ny + 52) {
                        dragIndex = i;
                        dragOffX = (int) mouseX - nx;
                        dragOffY = (int) mouseY - ny;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
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
