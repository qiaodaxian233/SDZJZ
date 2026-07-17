package com.sdzjz.client;

import com.sdzjz.screen.StructureCoreScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** 结构核心界面（全屏仪表盘）：深色背景铺满窗口，中间大面板分区：机器/升级/产出/状态/背包。 */
public class StructureCoreScreen extends HandledScreen<StructureCoreScreenHandler> {

    private static final int BACKDROP = 0xFF080B12; // 铺满窗口，盖住世界=全屏
    private static final int PANEL    = 0xFF0C1422;
    private static final int PANEL2   = 0xFF10192B;
    private static final int BORDER   = 0xFF25406B;
    private static final int SLOTBG   = 0xFF0B1526;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int ON       = 0xFF33D07A;

    public StructureCoreScreen(StructureCoreScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        int bx = this.x, by = this.y;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ 开机"), b -> click(0))
                .dimensions(bx + 24, by + 140, 150, 16).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("■ 停止"), b -> click(1))
                .dimensions(bx + 186, by + 140, 150, 16).build());
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
        ctx.fill(x - 2, y - 2, x + backgroundWidth + 2, y + backgroundHeight + 2, BORDER);
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        // 分区底板
        ctx.fill(x + 16, y + 38, x + 116, y + 84, PANEL2);   // 机器
        ctx.fill(x + 16, y + 94, x + 116, y + 118, PANEL2);  // 升级
        ctx.fill(x + 182, y + 38, x + 282, y + 84, PANEL2);  // 产出
        ctx.fill(x + 182, y + 94, x + 344, y + 134, PANEL2); // 状态
        ctx.fill(x + 95, y + 172, x + 265, y + 250, PANEL2); // 背包
        // 槽底
        for (int i = 0; i < 8; i++) sq(ctx, x + 24 + (i % 4) * 20, y + 42 + (i / 4) * 20);
        for (int i = 0; i < 3; i++) sq(ctx, x + 24 + i * 20, y + 98);
        for (int i = 0; i < 8; i++) sq(ctx, x + 190 + (i % 4) * 20, y + 42 + (i / 4) * 20);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) sq(ctx, x + 99 + c * 18, y + 176 + r * 18);
        for (int c = 0; c < 9; c++) sq(ctx, x + 99 + c * 18, y + 232);
    }

    private void sq(DrawContext ctx, int sx, int sy) {
        ctx.fill(sx, sy, sx + 16, sy + 16, SLOTBG);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // 相对面板左上角坐标
        String tierName = this.handler.tier() >= 2 ? "超大工作台" : "结构核心";
        ctx.drawText(this.textRenderer, tierName, 24, 12, TXT, false);
        boolean run = this.handler.isRunning();
        String st = run ? "● 运行中" : "○ 已停止";
        ctx.drawText(this.textRenderer, st, backgroundWidth - 24 - this.textRenderer.getWidth(st), 12, run ? ON : SUB, false);

        ctx.drawText(this.textRenderer, "机器", 18, 28, SUB, false);
        ctx.drawText(this.textRenderer, "升级", 18, 84, SUB, false);
        ctx.drawText(this.textRenderer, "产出", 184, 28, SUB, false);
        ctx.drawText(this.textRenderer, "状态", 184, 84, SUB, false);
        ctx.drawText(this.textRenderer, "背包", 99, 162, SUB, false);

        ctx.drawText(this.textRenderer, "机器数 " + this.handler.machineCount(), 190, 98, TXT, false);
        ctx.drawText(this.textRenderer, "速度 Lv" + this.handler.speedLv(), 190, 110, TXT, false);
        ctx.drawText(this.textRenderer, "数量 Lv" + this.handler.countLv(), 190, 122, TXT, false);
        ctx.drawText(this.textRenderer, "并发 Lv" + this.handler.parallelLv(), 268, 98, TXT, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
