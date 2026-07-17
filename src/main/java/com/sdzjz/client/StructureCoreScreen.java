package com.sdzjz.client;

import com.sdzjz.screen.StructureCoreScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 结构核心界面（全屏仪表盘 + 科技风槽位/按钮）。 */
public class StructureCoreScreen extends HandledScreen<StructureCoreScreenHandler> {

    private static final int BACKDROP = 0xFF080B12;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int ON       = 0xFF33D07A;
    private static final int CYAN     = 0xFF2EC4FF;
    private static final int CELL     = 0xFF0A1626;
    private static final int CELLFRM  = 0xFF163049;

    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/structure_core_gui.png");

    public StructureCoreScreen(StructureCoreScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        int bx = this.x, by = this.y;
        this.addDrawableChild(new SciButton(bx + 24, by + 140, 150, 16, Text.literal("▶ 开机"), b -> click(0)));
        this.addDrawableChild(new SciButton(bx + 186, by + 140, 150, 16, Text.literal("■ 停止"), b -> click(1)));
    }

    private void click(int id) {
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, id);
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        int x = this.x, y = this.y;
        ctx.drawTexture(BG, x, y, 0.0F, 0.0F, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        for (int i = 0; i < 8; i++) cell(ctx, x + 24 + (i % 4) * 20, y + 42 + (i / 4) * 20);
        for (int i = 0; i < 3; i++) cell(ctx, x + 24 + i * 20, y + 98);
        for (int i = 0; i < 8; i++) cell(ctx, x + 190 + (i % 4) * 20, y + 42 + (i / 4) * 20);
    }

    /** 科技风槽格：深色内凹 + 青色四角。 */
    private void cell(DrawContext ctx, int x, int y) {
        ctx.fill(x - 1, y - 1, x + 17, y + 17, CELLFRM);
        ctx.fill(x, y, x + 16, y + 16, CELL);
        ctx.fill(x, y, x + 4, y + 1, CYAN);           ctx.fill(x, y, x + 1, y + 4, CYAN);
        ctx.fill(x + 12, y, x + 16, y + 1, CYAN);     ctx.fill(x + 15, y, x + 16, y + 4, CYAN);
        ctx.fill(x, y + 15, x + 4, y + 16, CYAN);     ctx.fill(x, y + 12, x + 1, y + 16, CYAN);
        ctx.fill(x + 12, y + 15, x + 16, y + 16, CYAN); ctx.fill(x + 15, y + 12, x + 16, y + 16, CYAN);
    }

    private void header(DrawContext ctx, String s, int x, int y) {
        ctx.fill(x, y + 1, x + 2, y + 9, CYAN);                 // 前置青色竖条
        ctx.drawText(this.textRenderer, s, x + 6, y, SUB, false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        String tierName = this.handler.tier() >= 2 ? "超大工作台" : "结构核心";
        ctx.drawText(this.textRenderer, tierName, 24, 12, TXT, false);
        boolean run = this.handler.isRunning();
        String st = run ? "● 运行中" : "○ 已停止";
        ctx.drawText(this.textRenderer, st, backgroundWidth - 24 - this.textRenderer.getWidth(st), 12, run ? ON : SUB, false);

        header(ctx, "机器", 16, 28);
        header(ctx, "升级", 16, 84);
        header(ctx, "产出", 184, 28);
        header(ctx, "状态", 184, 84);

        ctx.drawText(this.textRenderer, "机器数 " + this.handler.machineCount(), 190, 98, TXT, false);
        ctx.drawText(this.textRenderer, "速度 Lv" + this.handler.speedLv(), 190, 110, TXT, false);
        ctx.drawText(this.textRenderer, "数量 Lv" + this.handler.countLv(), 190, 122, TXT, false);
        ctx.drawText(this.textRenderer, "并发 Lv" + this.handler.parallelLv(), 268, 98, CYAN, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
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
            int fill   = hover ? 0xFF123249 : 0xFF0C1E30;
            int tc     = hover ? 0xFFE8FBFF : 0xFFBFD2EC;
            ctx.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, border);
            ctx.fill(getX(), getY(), getX() + width, getY() + height, fill);
            ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, tc);
        }
    }
}
