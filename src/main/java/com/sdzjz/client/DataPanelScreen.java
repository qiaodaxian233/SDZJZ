package com.sdzjz.client;

import com.sdzjz.screen.DataPanelScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 数据面板 GUI（全屏科技风：深色铺底 + 科技边框贴图 + 青角槽格 + 分区标题）。 */
public class DataPanelScreen extends HandledScreen<DataPanelScreenHandler> {

    private static final int BACKDROP = 0xFF080B12;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int CYAN     = 0xFF2EC4FF;
    private static final int CELL     = 0xFF0A1626;
    private static final int CELLFRM  = 0xFF163049;

    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/structure_core_gui.png");

    public DataPanelScreen(DataPanelScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        int x = this.x, y = this.y;
        ctx.drawTexture(BG, x, y, 0.0F, 0.0F, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        // 存储 6×9
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 9; c++) cell(ctx, x + 99 + c * 18, y + 30 + r * 18);
        // 背包 3×9 + 快捷栏
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) cell(ctx, x + 99 + c * 18, y + 158 + r * 18);
        for (int c = 0; c < 9; c++) cell(ctx, x + 99 + c * 18, y + 216);
    }

    private void cell(DrawContext ctx, int x, int y) {
        ctx.fill(x - 1, y - 1, x + 17, y + 17, CELLFRM);
        ctx.fill(x, y, x + 16, y + 16, CELL);
        ctx.fill(x, y, x + 4, y + 1, CYAN);           ctx.fill(x, y, x + 1, y + 4, CYAN);
        ctx.fill(x + 12, y, x + 16, y + 1, CYAN);     ctx.fill(x + 15, y, x + 16, y + 4, CYAN);
        ctx.fill(x, y + 15, x + 4, y + 16, CYAN);     ctx.fill(x, y + 12, x + 1, y + 16, CYAN);
        ctx.fill(x + 12, y + 15, x + 16, y + 16, CYAN); ctx.fill(x + 15, y + 12, x + 16, y + 16, CYAN);
    }

    private void header(DrawContext ctx, String s, int x, int y) {
        ctx.fill(x, y + 1, x + 2, y + 9, CYAN);
        ctx.drawText(this.textRenderer, s, x + 6, y, SUB, false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, "数据面板", 24, 12, TXT, false);
        int kinds = 0;
        for (int i = 0; i < 54 && i < this.handler.slots.size(); i++)
            if (this.handler.slots.get(i).hasStack()) kinds++;
        String st = "种类 " + kinds + "/54";
        ctx.drawText(this.textRenderer, st, backgroundWidth - 24 - this.textRenderer.getWidth(st), 12, CYAN, false);
        header(ctx, "存储", 99, 20);
        header(ctx, "背包", 99, 148);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
