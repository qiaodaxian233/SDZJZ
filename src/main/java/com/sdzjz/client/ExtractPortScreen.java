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

    // 启停/模式钮几何（渲染与点击同源常量，m215/m223 教训：先收口再改数）
    private static final int BTN_X = 8, BTN_Y = 20, BTN_W = 160, BTN_H = 18;
    private static final int MODE_Y = 42; // m231 方向钮行

    public ExtractPortScreen(ExtractPortScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 224; // m230 升级行 + m231 模式钮行加高
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

        // m231 方向钮：送出(仓→机器/卖桌) / 回收(机器→仓)
        boolean pull = this.handler.pullMode();
        int mx2 = x + BTN_X, my2 = y + MODE_Y;
        boolean hov2 = mouseX >= mx2 && mouseX < mx2 + BTN_W && mouseY >= my2 && mouseY < my2 + BTN_H;
        ctx.fill(mx2, my2, mx2 + BTN_W, my2 + BTN_H, hov2 ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawBorder(mx2, my2, BTN_W, BTN_H, hov2 ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.drawText(this.textRenderer,
                Text.translatable(pull ? "sdzjz.extract_port.mode_in" : "sdzjz.extract_port.mode_out").getString(),
                mx2 + 6, my2 + 5, pull ? SciSkin.TXT_HI : TXT, false);

        // 邻接存储计数（属性 [1] 同步；0 台=柔和红提醒）
        int n = this.handler.adjacentCount();
        String adj = n > 0
                ? Text.translatable("sdzjz.extract_port.adjacent", n).getString()
                : Text.translatable("sdzjz.extract_port.none").getString();
        int sell = this.handler.sellState(); // m229 转化桌出售状态后缀
        if (sell == 1) adj += Text.translatable("sdzjz.extract_port.selling").getString();
        else if (sell == 2) adj += Text.translatable("sdzjz.extract_port.sell_blocked").getString();
        ctx.drawText(this.textRenderer, adj, x + 8, y + 66,
                n > 0 ? (sell == 1 ? SciSkin.GOLD : SUB) : SciSkin.RED_SOFT, false); // m231 下移

        // 幽灵过滤槽底（槽坐标走 Handler 收口常量同源）
        for (int i = 0; i < ExtractPortScreenHandler.FILTER; i++)
            cell(ctx, x + 8 + i * 18, y + ExtractPortScreenHandler.FILTER_Y);
        ctx.drawText(this.textRenderer, Text.translatable("sdzjz.extract_port.hint").getString(), x + 8, y + 102, SUB, false); // m231 下移

        // m230 升级行：标签 + 三槽（速度/数量/并发）+ 生效读数
        ctx.drawText(this.textRenderer, Text.translatable("sdzjz.extract_port.upgrades").getString(),
                x + 8, y + ExtractPortScreenHandler.UPG_Y + 4, SUB, false);
        for (int i = 0; i < ExtractPortScreenHandler.UPG; i++)
            cell(ctx, x + ExtractPortScreenHandler.UPG_X + i * 18, y + ExtractPortScreenHandler.UPG_Y);
        ctx.drawText(this.textRenderer, Text.translatable("sdzjz.extract_port.stats",
                        this.handler.effPeriod(), this.handler.effBudget()).getString(),
                x + ExtractPortScreenHandler.UPG_X + ExtractPortScreenHandler.UPG * 18 + 6,
                y + ExtractPortScreenHandler.UPG_Y + 4, SciSkin.TXT_HI, false);

        // 背包槽底（与 Handler PINV_Y 同源）
        int py = ExtractPortScreenHandler.PINV_Y;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) cell(ctx, x + 8 + c * 18, y + py + r * 18);
        for (int c = 0; c < 9; c++) cell(ctx, x + 8 + c * 18, y + py + 58);
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
        int bx = this.x + BTN_X;
        if (mx >= bx && mx < bx + BTN_W) {
            if (my >= this.y + BTN_Y && my < this.y + BTN_Y + BTN_H) {
                this.client.interactionManager.clickButton(this.handler.syncId, 0);
                return true;
            }
            if (my >= this.y + MODE_Y && my < this.y + MODE_Y + BTN_H) { // m231 方向钮
                this.client.interactionManager.clickButton(this.handler.syncId, 1);
                return true;
            }
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
