package com.sdzjz.client;

import com.sdzjz.block.TradeCenterBlockEntity;
import com.sdzjz.machine.VillagerTrades;
import com.sdzjz.screen.TradeCenterScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 村民交易所界面（全屏科技风）。
 * 无职业合同：显示 7 个职业就业按钮；有职业：显示交易列表（点击执行）+ 治愈按钮。
 */
public class TradeCenterScreen extends HandledScreen<TradeCenterScreenHandler> {

    private static final int BACKDROP = 0xFF080B12;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int CYAN     = 0xFF2EC4FF;
    private static final int CELL     = 0xFF0A1626;
    private static final int CELLFRM  = 0xFF163049;
    private static final int ROW_H    = 22;

    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/structure_core_gui.png");

    public TradeCenterScreen(TradeCenterScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        int x = this.x, y = this.y;
        ctx.drawTexture(BG, x, y, 0.0F, 0.0F, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);

        ctx.drawText(this.textRenderer, "村民交易所", x + 10, y + 8, CYAN, false);

        // 合同槽底
        cell(ctx, x + 30, y + 40);
        ctx.drawText(this.textRenderer, "合同", x + 26, y + 26, SUB, false);

        ItemStack c = this.handler.contract();
        String prof = TradeCenterBlockEntity.contractProf(c);

        if (c.isEmpty()) {
            ctx.drawText(this.textRenderer, "放入村民合同开始交易（村民繁殖机可产出）", x + 80, y + 44, SUB, false);
        } else if (prof == null) {
            // 就业选择
            ctx.drawText(this.textRenderer, "选择职业就业（消耗存储网络里 1 个对应工作方块）：", x + 80, y + 30, TXT, false);
            List<String> ids = VillagerTrades.professionIds();
            for (int i = 0; i < ids.size(); i++) {
                int bx = x + 80 + (i % 4) * 68, by = y + 44 + (i / 4) * 24;
                boolean hov = mouseX >= bx && mouseX < bx + 64 && mouseY >= by && mouseY < by + 20;
                ctx.fill(bx, by, bx + 64, by + 20, hov ? 0xFF14304A : CELL);
                ctx.drawBorder(bx, by, 64, 20, hov ? CYAN : CELLFRM);
                String name = profName(ids.get(i));
                ctx.drawText(this.textRenderer, name, bx + (64 - this.textRenderer.getWidth(name)) / 2, by + 6, TXT, false);
            }
        } else {
            int disc = TradeCenterBlockEntity.contractDiscount(c);
            ctx.drawText(this.textRenderer, "职业：" + profName(prof) + "   折扣：Lv" + disc + "（每级输入-10%）", x + 80, y + 30, TXT, false);

            // 治愈按钮
            int hx = x + 288, hy = y + 26;
            boolean hovH = mouseX >= hx && mouseX < hx + 62 && mouseY >= hy && mouseY < hy + 18;
            ctx.fill(hx, hy, hx + 62, hy + 18, hovH ? 0xFF14304A : CELL);
            ctx.drawBorder(hx, hy, 62, 18, hovH ? CYAN : CELLFRM);
            ctx.drawText(this.textRenderer, disc >= 5 ? "折扣已满" : "治愈+折扣", hx + 5, hy + 5, disc >= 5 ? SUB : TXT, false);

            // 交易列表
            List<VillagerTrades.Trade> trades = VillagerTrades.ALL.get(prof).trades();
            for (int i = 0; i < trades.size(); i++) {
                VillagerTrades.Trade t = trades.get(i);
                int rx = x + 80, ry = y + 48 + i * (ROW_H + 4);
                boolean hov = mouseX >= rx && mouseX < rx + 270 && mouseY >= ry && mouseY < ry + ROW_H;
                ctx.fill(rx, ry, rx + 270, ry + ROW_H, hov ? 0xFF102A40 : CELL);
                ctx.drawBorder(rx, ry, 270, ROW_H, hov ? CYAN : CELLFRM);
                int need = VillagerTrades.discounted(t.inCount(), disc);
                ItemStack in = new ItemStack(Registries.ITEM.get(Identifier.of(t.inItem())));
                ItemStack out = new ItemStack(Registries.ITEM.get(Identifier.of(t.outItem())));
                ctx.drawItem(in, rx + 4, ry + 3);
                ctx.drawText(this.textRenderer, "×" + need, rx + 24, ry + 8, TXT, false);
                ctx.drawText(this.textRenderer, "→", rx + 130, ry + 8, CYAN, false);
                ctx.drawItem(out, rx + 150, ry + 3);
                ctx.drawText(this.textRenderer, "×" + t.outCount(), rx + 170, ry + 8, TXT, false);
                if (hov) ctx.drawText(this.textRenderer, "点击交易", rx + 214, ry + 8, CYAN, false);
            }
            ctx.drawText(this.textRenderer, "输入从相连存储核心扣、产出存回；治愈消耗 1 金苹果", x + 80, y + 156, SUB, false);
        }

        // 背包标题 + 槽底
        ctx.fill(x + 96, y + 166, x + 99, y + 176, CYAN);
        ctx.drawText(this.textRenderer, "背包", x + 103, y + 167, SUB, false);
        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 9; col++) cell(ctx, x + 99 + col * 18, y + 170 + r * 18);
        for (int col = 0; col < 9; col++) cell(ctx, x + 99 + col * 18, y + 228);
    }

    private void cell(DrawContext ctx, int cx, int cy) {
        ctx.fill(cx, cy, cx + 16, cy + 16, CELL);
        ctx.drawBorder(cx - 1, cy - 1, 18, 18, CELLFRM);
        ctx.fill(cx - 1, cy - 1, cx + 2, cy, CYAN);
        ctx.fill(cx - 1, cy - 1, cx, cy + 2, CYAN);
        ctx.fill(cx + 14, cy + 15, cx + 17, cy + 16, CYAN);
        ctx.fill(cx + 16, cy + 13, cx + 17, cy + 16, CYAN);
    }

    private static String profName(String id) {
        return switch (id) {
            case "farmer" -> "农民";
            case "librarian" -> "图书管理员";
            case "cartographer" -> "制图师";
            case "toolsmith" -> "工具匠";
            case "cleric" -> "牧师";
            case "butcher" -> "屠夫";
            case "fisherman" -> "渔夫";
            default -> id;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = this.x, y = this.y;
        ItemStack c = this.handler.contract();
        String prof = TradeCenterBlockEntity.contractProf(c);

        if (!c.isEmpty() && prof == null) {
            List<String> ids = VillagerTrades.professionIds();
            for (int i = 0; i < ids.size(); i++) {
                int bx = x + 80 + (i % 4) * 68, by = y + 44 + (i / 4) * 24;
                if (mx >= bx && mx < bx + 64 && my >= by && my < by + 20) {
                    this.client.interactionManager.clickButton(this.handler.syncId, i);
                    return true;
                }
            }
        } else if (prof != null) {
            int hx = x + 288, hy = y + 26;
            if (mx >= hx && mx < hx + 62 && my >= hy && my < hy + 18) {
                this.client.interactionManager.clickButton(this.handler.syncId, TradeCenterScreenHandler.BTN_HEAL);
                return true;
            }
            List<VillagerTrades.Trade> trades = VillagerTrades.ALL.get(prof).trades();
            for (int i = 0; i < trades.size(); i++) {
                int rx = x + 80, ry = y + 48 + i * (ROW_H + 4);
                if (mx >= rx && mx < rx + 270 && my >= ry && my < ry + ROW_H) {
                    this.client.interactionManager.clickButton(this.handler.syncId, TradeCenterScreenHandler.BTN_TRADE_BASE + i);
                    return true;
                }
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
        // 标题自绘，禁用默认标题
    }
}
