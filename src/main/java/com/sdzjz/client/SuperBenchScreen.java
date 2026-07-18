package com.sdzjz.client;

import com.sdzjz.machine.SuperBenchRecipes;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/** 超大工作台 12×12 合成界面 + 右侧配方浏览器（点机器=自动从背包填料）。 */
public class SuperBenchScreen extends HandledScreen<SuperBenchScreenHandler> {

    private static final int PANEL = 0xF00A1626;
    private static final int CELLF = 0xFF1C5A80;
    private static final int CELLB = 0xFF0C1E30;
    private static final int CYAN  = 0xFF2EC4FF;
    private static final int TXT   = 0xFFBFD2EC;
    private static final int SUB   = 0xFF7C90B0;
    private static final int SEL   = 0x552EC4FF;
    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/super_bench_gui.png");

    // 浏览器布局（GUI 相对坐标）
    private static final int PX = 270, PW = 192, LIST_Y = 30, ENTRY_H = 18, LIST_ROWS = 12;

    private int scroll = 0;
    private int selected = -1;

    public SuperBenchScreen(SuperBenchScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 470;
        this.backgroundHeight = 316;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        ctx.drawTexture(BG, x, y, 0.0F, 0.0F, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        ctx.fill(x, y, x + backgroundWidth, y + 16, 0xB80A1626);                                   // 标题条可读性底
        ctx.fill(x + PX - 6, y + 16, x + backgroundWidth, y + backgroundHeight, 0xA00A1626);       // 浏览器区可读性底
        ctx.fill(x, y, x + backgroundWidth, y + 1, CYAN);
        ctx.fill(x, y + 15, x + backgroundWidth, y + 16, CYAN);
        ctx.fill(x + PX - 6, y + 18, x + PX - 5, y + backgroundHeight, CYAN); // 分隔线

        for (int r = 0; r < 12; r++)
            for (int c = 0; c < 12; c++)
                cell(ctx, x + 8 + c * 18, y + 18 + r * 18);
        cell(ctx, x + 248, y + 118); // 结果槽
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
        ctx.drawText(this.textRenderer, "超大工作台 · 12×12", 8, 4, TXT, false);
        ctx.drawText(this.textRenderer, "→", 232 - 14, 118 + 4, CYAN, false);
        ctx.drawText(this.textRenderer, "材料位置随意", 8, 18 + 12 * 18 + 2, SUB, false);

        // ===== 右侧配方浏览器 =====
        ctx.drawText(this.textRenderer, "机器配方（点击填料）", PX, 4, CYAN, false);
        List<SuperBenchRecipes.Recipe> all = SuperBenchRecipes.ALL;
        int maxScroll = Math.max(0, all.size() - LIST_ROWS);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int row = 0; row < LIST_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= all.size()) break;
            SuperBenchRecipes.Recipe r = all.get(idx);
            int ey = LIST_Y + row * ENTRY_H;
            if (idx == selected) ctx.fill(PX, ey - 1, PX + PW, ey + ENTRY_H - 1, SEL);
            ItemStack res = SuperBenchRecipes.resultStack(r);
            ctx.drawItem(res, PX + 1, ey);
            String nm = res.getName().getString();
            ctx.drawText(this.textRenderer, nm, PX + 20, ey + 4, TXT, false);
        }
        // 滚动提示
        ctx.drawText(this.textRenderer, (scroll + LIST_ROWS < all.size() ? "▼ 滚轮翻页 " : "") + (scroll > 0 ? "▲" : ""),
                PX, LIST_Y + LIST_ROWS * ENTRY_H + 2, SUB, false);

        // 选中配方的材料
        if (selected >= 0 && selected < all.size()) {
            int dy = LIST_Y + LIST_ROWS * ENTRY_H + 14;
            ctx.drawText(this.textRenderer, "需要材料：", PX, dy, SUB, false);
            int iy = dy + 12, col = 0;
            for (Map.Entry<String, Integer> e : all.get(selected).ingredients().entrySet()) {
                ItemStack s = new ItemStack(Registries.ITEM.get(Identifier.of(e.getKey())));
                int sx = PX + (col % 6) * 32, sy = iy + (col / 6) * 20; // 6 列×32px：11 种材料两行放下，不越底
                ctx.drawItem(s, sx, sy);
                ctx.drawText(this.textRenderer, "×" + e.getValue(), sx + 15, sy + 5, TXT, false);
                col++;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        double rx = mouseX - this.x;
        if (rx >= PX - 6) {
            scroll = Math.max(0, scroll - (int) Math.signum(v));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double rx = mouseX - this.x, ry = mouseY - this.y;
        if (button == 0 && rx >= PX && rx <= PX + PW && ry >= LIST_Y && ry < LIST_Y + LIST_ROWS * ENTRY_H) {
            int row = (int) ((ry - LIST_Y) / ENTRY_H);
            int idx = scroll + row;
            if (idx >= 0 && idx < SuperBenchRecipes.ALL.size()) {
                selected = idx;
                if (this.client != null && this.client.interactionManager != null) {
                    this.client.interactionManager.clickButton(this.handler.syncId, idx); // 填料
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
