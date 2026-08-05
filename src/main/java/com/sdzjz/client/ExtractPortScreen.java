package com.sdzjz.client;

import com.sdzjz.screen.ExtractPortScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * m226 数据线抽取口配置界面（科技风自绘，零贴图）：
 * 标题 + 启停钮 + 邻接存储计数 + 9 幽灵过滤槽 + 背包。
 * 幽灵槽交互全在 Handler（服务端权威），本屏只画底格与状态。
 */
public class ExtractPortScreen extends HandledScreen<ExtractPortScreenHandler> {

    private static final int TXT     = SciSkin.TXT;
    private static final int SUB     = SciSkin.SUB;
    private static final int ACCENT  = SciSkin.ACCENT;
    private static final int CELL    = SciSkin.CELL;
    private static final int CELLFRM = SciSkin.CELL_FRM;

    // 启停钮几何（渲染与点击同源常量，m215/m223 教训：先收口再改数）
    private static final int BTN_X = 8, BTN_Y = 20, BTN_W = 160, BTN_H = 18;

    public ExtractPortScreen(ExtractPortScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 178;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, SciSkin.BACKDROP);
        int x = this.x, y = this.y;
        ctx.drawBorder(x - 1, y - 1, backgroundWidth + 2, backgroundHeight + 2, SciSkin.FRAME);

        ctx.drawText(this.textRenderer, this.title, x + 8, y + 8, ACCENT, false);

        // 启停钮（属性 [0] 同步；点击发 onButtonClick id=0）
        boolean on = this.handler.extractOn();
        int bx = x + BTN_X, by = y + BTN_Y;
        boolean hov = mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H;
        ctx.fill(bx, by, bx + BTN_W, by + BTN_H, hov ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawBorder(bx, by, BTN_W, BTN_H, hov ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(bx + 5, by + 6, bx + 11, by + 12, on ? SciSkin.ON : SciSkin.OFF_GRAY); // 状态灯
        String label = Text.translatable(on ? "sdzjz.extract_port.running" : "sdzjz.extract_port.stopped").getString();
        ctx.drawText(this.textRenderer, label, bx + 16, by + 5, on ? SciSkin.TXT_MAX : TXT, false);

        // 邻接存储计数（属性 [1] 同步；0 台=柔和红提醒）
        int n = this.handler.adjacentCount();
        String adj = n > 0
                ? Text.translatable("sdzjz.extract_port.adjacent", n).getString()
                : Text.translatable("sdzjz.extract_port.none").getString();
        ctx.drawText(this.textRenderer, adj, x + 8, y + 44, n > 0 ? SUB : SciSkin.RED_SOFT, false);

        // 幽灵过滤槽底（槽坐标与 Handler 同：8+i*18, 58）
        for (int i = 0; i < ExtractPortScreenHandler.FILTER; i++) cell(ctx, x + 8 + i * 18, y + 58);
        ctx.drawText(this.textRenderer, Text.translatable("sdzjz.extract_port.hint").getString(), x + 8, y + 80, SUB, false);

        // 背包槽底（与 Handler px=8/py=96 同源）
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) cell(ctx, x + 8 + c * 18, y + 96 + r * 18);
        for (int c = 0; c < 9; c++) cell(ctx, x + 8 + c * 18, y + 154);
    }

    private void cell(DrawContext ctx, int cx, int cy) { // 四屏同款角标格（TradeCenter 工艺）
        ctx.fill(cx, cy, cx + 16, cy + 16, CELL);
        ctx.drawBorder(cx - 1, cy - 1, 18, 18, CELLFRM);
        ctx.fill(cx - 1, cy - 1, cx + 2, cy, ACCENT);
        ctx.fill(cx - 1, cy - 1, cx, cy + 2, ACCENT);
        ctx.fill(cx + 14, cy + 15, cx + 17, cy + 16, ACCENT);
        ctx.fill(cx + 16, cy + 13, cx + 17, cy + 16, ACCENT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bx = this.x + BTN_X, by = this.y + BTN_Y;
        if (mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H) {
            this.client.interactionManager.clickButton(this.handler.syncId, 0);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // 标题自绘，禁用默认标题（四屏同口径）
    }
}
