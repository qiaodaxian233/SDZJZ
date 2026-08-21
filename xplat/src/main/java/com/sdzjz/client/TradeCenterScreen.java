package com.sdzjz.client;

import com.sdzjz.block.TradeCenterBlockEntity;
import com.sdzjz.machine.VillagerTrades;
import com.sdzjz.screen.TradeCenterScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 村民交易所界面（全屏科技风）。
 * 无职业合同：显示 7 个职业就业按钮；有职业：显示交易列表（点击执行）+ 治愈按钮。
 */
public class TradeCenterScreen extends AbstractContainerScreen<TradeCenterScreenHandler> {

    private static final int BACKDROP = SciSkin.BACKDROP;
    private static final int TXT      = SciSkin.TXT;
    private static final int SUB      = SciSkin.SUB;
    private static final int CYAN     = SciSkin.ACCENT;
    private static final int CELL     = SciSkin.CELL;
    private static final int CELLFRM  = SciSkin.CELL_FRM;
    private static final int ROW_H    = 22;
    private static final int VISIBLE_ROWS = 4; // m101 交易列表滚动窗口
    private int tradeScroll;

    private static String roman(int lv) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return lv >= 0 && lv < r.length ? r[lv] : String.valueOf(lv);
    }

    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("sdzjz", "textures/gui/trade_center_gui.png");

    public TradeCenterScreen(TradeCenterScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title);
        this.imageWidth = 360;
        this.imageHeight = 256;
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        int x = this.leftPos, y = this.topPos;
        ctx.blit(BG, x, y, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);

        ctx.drawString(this.font, "村民交易所", x + 10, y + 8, CYAN, false);

        // 合同槽底
        cell(ctx, x + 30, y + 40);
        ctx.drawString(this.font, "合同", x + 26, y + 26, SUB, false);

        ItemStack c = this.menu.contract();
        String prof = TradeCenterBlockEntity.contractProf(c);

        if (c.isEmpty()) {
            ctx.drawString(this.font, "放入村民合同开始交易（村民繁殖机可产出）", x + 80, y + 44, SUB, false);
        } else if (prof == null) {
            // 就业选择
            ctx.drawString(this.font, "选择职业就业（消耗存储网络里 1 个对应工作方块）：", x + 80, y + 30, TXT, false);
            List<String> ids = VillagerTrades.professionIds();
            for (int i = 0; i < ids.size(); i++) {
                int bx = x + 80 + (i % 4) * 68, by = y + 44 + (i / 4) * 24;
                boolean hov = mouseX >= bx && mouseX < bx + 64 && mouseY >= by && mouseY < by + 20;
                ctx.fill(bx, by, bx + 64, by + 20, hov ? SciSkin.HOVER : CELL);
                ctx.renderOutline(bx, by, 64, 20, hov ? CYAN : CELLFRM);
                String name = profName(ids.get(i));
                ctx.drawString(this.font, name, bx + (64 - this.font.getWidth(name)) / 2, by + 6, TXT, false);
            }
        } else {
            int disc = TradeCenterBlockEntity.contractDiscount(c);
            int lvl = TradeCenterBlockEntity.contractLevel(c);
            boolean lvOn = com.sdzjz.config.SdzjzConfig.get().tradeLeveling; // m333 关=全表解锁旧观感
            String lvTxt = !lvOn ? "" : " · " + VillagerTrades.levelName(lvl)
                    + (lvl >= 5 ? "(满级)" : "(" + TradeCenterBlockEntity.contractXp(c) + "/" + VillagerTrades.LEVEL_XP[lvl] + ")");
            ctx.drawString(this.font, "职业：" + profName(prof) + lvTxt + " · 折扣Lv" + disc + "(-" + disc * 10 + "%)", x + 80, y + 30, TXT, false);
            if (lvOn) { // m335 经验进度条（学原版村民屏的等级条，实现自写、配色走本屏皮肤）
                int bx0 = x + 80, by0 = y + 41, bw = 200;
                ctx.fill(bx0, by0, bx0 + bw, by0 + 3, CELL);
                int prev = VillagerTrades.LEVEL_XP[lvl - 1];
                int fill = lvl >= 5 ? bw : (int) Math.min(bw, (long) bw
                        * Math.max(0, TradeCenterBlockEntity.contractXp(c) - prev)
                        / Math.max(1, VillagerTrades.LEVEL_XP[lvl] - prev));
                ctx.fill(bx0, by0, bx0 + fill, by0 + 3, CYAN);
            }

            // 治愈按钮
            int hx = x + 288, hy = y + 26;
            boolean hovH = mouseX >= hx && mouseX < hx + 62 && mouseY >= hy && mouseY < hy + 18;
            ctx.fill(hx, hy, hx + 62, hy + 18, hovH ? SciSkin.HOVER : CELL);
            ctx.renderOutline(hx, hy, 62, 18, hovH ? CYAN : CELLFRM);
            ctx.drawString(this.font, disc >= 5 ? "折扣已满" : "治愈+折扣", hx + 5, hy + 5, disc >= 5 ? SUB : TXT, false);

            // 交易列表（m101 滚动窗口：一次 4 条，滚轮翻页——图书管理员 14 条不再压穿界面）
            List<VillagerTrades.Trade> trades = VillagerTrades.ALL.get(prof).trades();
            int maxScroll = Math.max(0, trades.size() - VISIBLE_ROWS);
            if (tradeScroll > maxScroll) tradeScroll = maxScroll;
            for (int v = 0; v < VISIBLE_ROWS && tradeScroll + v < trades.size(); v++) {
                int i = tradeScroll + v;
                VillagerTrades.Trade t = trades.get(i);
                int rx = x + 80, ry = y + 48 + v * (ROW_H + 4);
                boolean locked = lvOn && lvl < t.minLevel(); // m333 未到级：不亮不点，右侧标解锁等级
                boolean hov = !locked && mouseX >= rx && mouseX < rx + 270 && mouseY >= ry && mouseY < ry + ROW_H;
                ctx.fill(rx, ry, rx + 270, ry + ROW_H, hov ? SciSkin.HOVER : CELL);
                ctx.renderOutline(rx, ry, 270, ROW_H, hov ? CYAN : CELLFRM);
                int need = VillagerTrades.discounted(t.inCount(), disc);
                ItemStack in = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.inItem())));
                ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.outItem())));
                ctx.renderItem(in, rx + 4, ry + 3);
                ctx.drawString(this.font, "×" + need, rx + 24, ry + 8, TXT, false);
                if (t.in2Item() != null) { // m101 第二输入（附魔书要的那本书）
                    ctx.drawString(this.font, "+", rx + 52, ry + 8, SUB, false);
                    ItemStack in2 = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.in2Item())));
                    ctx.renderItem(in2, rx + 62, ry + 3);
                    ctx.drawString(this.font, "×" + t.in2Count(), rx + 82, ry + 8, TXT, false);
                }
                ctx.drawString(this.font, locked ? "×" : "→", rx + 130, ry + 8, locked ? SciSkin.RED : CYAN, false); // m335 学原版锁定叉
                ctx.renderItem(out, rx + 150, ry + 3);
                if (t.enchant() != null) { // m101 附魔书：显示附魔名+等级（走原版翻译键）
                    String en = Component.translatable("enchantment." + t.enchant().replace(':', '.')).getString()
                            + (t.enchantLv() > 1 ? " " + roman(t.enchantLv()) : "");
                    ctx.drawString(this.font, en, rx + 170, ry + 8, SciSkin.TXT_HI, false);
                } else {
                    ctx.drawString(this.font, "×" + t.outCount(), rx + 170, ry + 8, TXT, false);
                }
                if (locked) ctx.drawString(this.font, VillagerTrades.levelName(t.minLevel()) + "解锁", rx + 214, ry + 8, SUB, false);
                else if (hov) ctx.drawString(this.font, "点击交易", rx + 214, ry + 8, CYAN, false);
            }
            if (maxScroll > 0) { // m101 滚动条
                int trackX = x + 352, trackY = y + 48, trackH = VISIBLE_ROWS * (ROW_H + 4) - 4;
                ctx.fill(trackX, trackY, trackX + 4, trackY + trackH, CELL);
                int thumbH = Math.max(10, trackH * VISIBLE_ROWS / trades.size());
                int thumbY = trackY + (trackH - thumbH) * tradeScroll / maxScroll;
                ctx.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, CYAN);
            }
            ctx.drawString(this.font, "输入从存储扣·产出存回·附魔书进背包·滚轮翻页", x + 80, y + 156, SUB, false);
        }

        // 背包标题 + 槽底
        ctx.fill(x + 96, y + 166, x + 99, y + 176, CYAN);
        ctx.drawString(this.font, "背包", x + 103, y + 167, SUB, false);
        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 9; col++) cell(ctx, x + 99 + col * 18, y + 170 + r * 18);
        for (int col = 0; col < 9; col++) cell(ctx, x + 99 + col * 18, y + 228);
    }

    private void cell(GuiGraphics ctx, int cx, int cy) {
        ctx.fill(cx, cy, cx + 16, cy + 16, CELL);
        ctx.renderOutline(cx - 1, cy - 1, 18, 18, CELLFRM);
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
        int x = this.leftPos, y = this.topPos;
        ItemStack c = this.menu.contract();
        String prof = TradeCenterBlockEntity.contractProf(c);

        if (!c.isEmpty() && prof == null) {
            List<String> ids = VillagerTrades.professionIds();
            for (int i = 0; i < ids.size(); i++) {
                int bx = x + 80 + (i % 4) * 68, by = y + 44 + (i / 4) * 24;
                if (mx >= bx && mx < bx + 64 && my >= by && my < by + 20) {
                    this.minecraft.gameMode.clickButton(this.menu.containerId, i);
                    return true;
                }
            }
        } else if (prof != null) {
            int hx = x + 288, hy = y + 26;
            if (mx >= hx && mx < hx + 62 && my >= hy && my < hy + 18) {
                this.minecraft.gameMode.clickButton(this.menu.containerId, TradeCenterScreenHandler.BTN_HEAL);
                return true;
            }
            List<VillagerTrades.Trade> trades = VillagerTrades.ALL.get(prof).trades();
            int lvl = TradeCenterBlockEntity.contractLevel(c);
            boolean lvOn = com.sdzjz.config.SdzjzConfig.get().tradeLeveling;
            for (int v = 0; v < VISIBLE_ROWS && tradeScroll + v < trades.size(); v++) { // m101 按滚动偏移换算
                int rx = x + 80, ry = y + 48 + v * (ROW_H + 4);
                if (mx >= rx && mx < rx + 270 && my >= ry && my < ry + ROW_H) {
                    if (lvOn && lvl < trades.get(tradeScroll + v).minLevel()) return true; // m333 锁定行吃点击
                    this.minecraft.gameMode.clickButton(this.menu.containerId,
                            TradeCenterScreenHandler.BTN_TRADE_BASE + tradeScroll + v);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) { // m101 滚轮翻交易列表; m103 只在悬停列表区时生效,不劫持背包滚轮
        String prof = TradeCenterBlockEntity.contractProf(this.menu.contract());
        if (prof != null
                && mx >= this.leftPos + 80 && mx < this.leftPos + 356
                && my >= this.topPos + 44 && my < this.topPos + 152) {
            int maxScroll = Math.max(0, VillagerTrades.ALL.get(prof).trades().size() - VISIBLE_ROWS);
            if (maxScroll > 0) {
                if (v < 0 && tradeScroll < maxScroll) tradeScroll++;
                else if (v > 0 && tradeScroll > 0) tradeScroll--;
                return true;
            }
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
        // 标题自绘，禁用默认标题
    }
}
