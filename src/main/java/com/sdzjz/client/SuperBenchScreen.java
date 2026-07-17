package com.sdzjz.client;

import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** 超大工作台 12×12 合成界面。 */
public class SuperBenchScreen extends HandledScreen<SuperBenchScreenHandler> {

    private static final int PANEL = 0xF00A1626;
    private static final int CELLF = 0xFF1C5A80;
    private static final int CELLB = 0xFF0C1E30;
    private static final int CYAN  = 0xFF2EC4FF;
    private static final int TXT   = 0xFFBFD2EC;

    public SuperBenchScreen(SuperBenchScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 284;
        this.backgroundHeight = 316;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        ctx.fill(x, y, x + backgroundWidth, y + 1, CYAN);
        ctx.fill(x, y + 15, x + backgroundWidth, y + 16, CYAN);

        // 12×12 输入网格
        for (int r = 0; r < 12; r++)
            for (int c = 0; c < 12; c++)
                cell(ctx, x + 8 + c * 18, y + 18 + r * 18);
        // 结果槽
        int rx = x + 8 + 12 * 18 + 24, ry = y + 18 + (12 * 18) / 2 - 8;
        cell(ctx, rx, ry);
        // 玩家背包
        int py = y + 18 + 12 * 18 + 12;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                cell(ctx, x + 8 + c * 18, py + r * 18);
        for (int c = 0; c < 9; c++)
            cell(ctx, x + 8 + c * 18, py + 58);
    }

    private void cell(DrawContext ctx, int x, int y) {
        ctx.fill(x - 1, y - 1, x + 17, y + 17, CELLF);
        ctx.fill(x, y, x + 16, y + 16, CELLB);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, "超大工作台 · 12×12 机器合成", 8, 4, TXT, false);
        ctx.drawText(this.textRenderer, "→", 8 + 12 * 18 + 12, 18 + (12 * 18) / 2 - 4, CYAN, false);
        ctx.drawText(this.textRenderer, "把配方材料放进网格(位置随意)", 8, 18 + 12 * 18 + 2, 0xFF7C90B0, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
