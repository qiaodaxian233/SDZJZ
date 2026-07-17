package com.sdzjz.client;

import com.sdzjz.screen.DataPanelScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** 数据面板 GUI（Phase 2：纯色面板 + 6×9 展示格 + 玩家背包）。 */
public class DataPanelScreen extends HandledScreen<DataPanelScreenHandler> {

    private static final int PANEL = 0xFF0C1422;
    private static final int SLOTBG = 0xFF0B1526;
    private static final int BORDER = 0xFF1B3050;

    public DataPanelScreen(DataPanelScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        this.titleY = 6;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        ctx.fill(x - 1, y - 1, x + this.backgroundWidth + 1, y + this.backgroundHeight + 1, BORDER);
        ctx.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL);
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 9; c++) {
                int sx = x + 8 + c * 18, sy = y + 18 + r * 18;
                ctx.fill(sx, sy, sx + 16, sy + 16, SLOTBG);
            }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
