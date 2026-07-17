package com.sdzjz.client;

import com.sdzjz.screen.StructureCoreScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** 结构核心 GUI：纯色面板 + 通用状态显示（对任意机器生效）+ 开机/停止。 */
public class StructureCoreScreen extends HandledScreen<StructureCoreScreenHandler> {

    private static final int PANEL = 0xFF0C1422;
    private static final int SLOTBG = 0xFF0B1526;
    private static final int BORDER = 0xFF1B3050;

    public StructureCoreScreen(StructureCoreScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        this.titleY = 6;
        this.playerInventoryTitleY = this.backgroundHeight - 94;

        int bx = (this.width - this.backgroundWidth) / 2;
        int by = (this.height - this.backgroundHeight) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ 开机"), b -> click(0))
                .dimensions(bx + 8, by + 90, 78, 12).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("■ 停止"), b -> click(1))
                .dimensions(bx + 90, by + 90, 78, 12).build());
    }

    private void click(int id) {
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, id);
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        ctx.fill(x - 1, y - 1, x + this.backgroundWidth + 1, y + this.backgroundHeight + 1, BORDER);
        ctx.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL);
        drawRow(ctx, x + 8, y + 20, 8);
        drawRow(ctx, x + 8, y + 46, 3);
        drawRow(ctx, x + 8, y + 72, 8);
    }

    private void drawRow(DrawContext ctx, int x, int y, int n) {
        for (int i = 0; i < n; i++) ctx.fill(x + i * 18, y, x + i * 18 + 16, y + 16, SLOTBG);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        super.drawForeground(ctx, mouseX, mouseY);
        boolean run = this.handler.isRunning();
        String s1 = run ? "▶ 运行中" : "■ 已停止";
        int c1 = run ? 0x33D07A : 0x8299B8;
        ctx.drawText(this.textRenderer, s1, this.backgroundWidth - 8 - this.textRenderer.getWidth(s1), 6, c1, false);
        String tierName = this.handler.tier() >= 2 ? "超大工作台" : "结构核心";
        String s2 = tierName + " 机" + this.handler.machineCount()
                + "  速" + this.handler.speedLv() + " 量" + this.handler.countLv() + " 并" + this.handler.parallelLv();
        ctx.drawText(this.textRenderer, s2, this.backgroundWidth - 8 - this.textRenderer.getWidth(s2), 16, 0x8299B8, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
