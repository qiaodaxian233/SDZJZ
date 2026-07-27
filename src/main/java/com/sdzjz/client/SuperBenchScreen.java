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
    private static final int CELLF = SciSkin.FRAME;
    private static final int CELLB = SciSkin.BTN_FACE;
    private static final int CYAN  = SciSkin.ACCENT;
    private static final int TXT   = SciSkin.TXT;
    private static final int SUB   = SciSkin.SUB;
    private static final int SEL   = 0x552EC4FF;
    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/super_bench_gui.png");

    // 浏览器布局（GUI 相对坐标）
    private static final int PX = 270, PW = 192, LIST_Y = 30, ENTRY_H = 18, LIST_ROWS = 11; // m166 让位一行：BOM 配方材料可达 14+ 种，清单区要放下三行

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
        ctx.fill(0, 0, this.width, this.height, SciSkin.BACKDROP); // m117：与其余三屏统一的全屏底色（此前唯独本屏漏铺）
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
            // m165 档位角标：Ⅰ铜石(主世界早期)/Ⅱ铁(下界期)/Ⅲ金钻(终局)，颜色即材质盘暗示；小件 tier=0 不画
            int tier = r.tier();
            int nameW = PW - 22;
            if (tier > 0) {
                String chip = tier == 1 ? "Ⅰ" : tier == 3 ? "Ⅲ" : "Ⅱ";
                int cc = tier == 1 ? 0xFFE8A05A : tier == 3 ? 0xFF66E6FF : 0xFFB8C4D0;
                ctx.drawText(this.textRenderer, chip, PX + PW - 12, ey + 4, cc, false);
                nameW = PW - 36;
            }
            String nm = this.textRenderer.trimToWidth(res.getName().getString(), nameW);
            ctx.drawText(this.textRenderer, nm, PX + 20, ey + 4, TXT, false);
        }
        // 滚动提示
        ctx.drawText(this.textRenderer, (scroll + LIST_ROWS < all.size() ? "▼ 滚轮翻页 " : "") + (scroll > 0 ? "▲" : ""),
                PX, LIST_Y + LIST_ROWS * ENTRY_H + 2, SUB, false);

        // 选中配方的材料（活体对照：绿=已够(网格+背包)，红=还缺，计数显示 现有/需求）
        if (selected >= 0 && selected < all.size()) {
            int dy = LIST_Y + LIST_ROWS * ENTRY_H + 14;
            ctx.drawText(this.textRenderer, "需要材料：", PX, dy, SUB, false);
            java.util.List<String> mobs = all.get(selected).mobs();
            if (!mobs.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                boolean allOk = true;
                for (String mob : mobs) { // m166 多生物逐只显示 ✔/✘（如刷铁机=村民+僵尸）
                    String mn;
                    try { mn = net.minecraft.registry.Registries.ENTITY_TYPE.get(Identifier.of(mob)).getName().getString(); }
                    catch (Exception ex) { mn = mob; }
                    if (sb.length() > 0) sb.append(' ');
                    boolean caged = hasCagedMob(mob);
                    allOk &= caged;
                    sb.append(mn).append(caged ? "✔" : "✘");
                }
                String line = (allOk ? "已捕获: " : "需捕获(笼子装它): ") + sb;
                ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(line, PW - 58),
                        PX + 58, dy, allOk ? 0xFF50E850 : SciSkin.RED, false);
            }
            Map<String, Integer> have = countAvailable();
            int iy = dy + 12, col = 0;
            for (Map.Entry<String, Integer> e : all.get(selected).ingredients().entrySet()) {
                ItemStack s = new ItemStack(Registries.ITEM.get(Identifier.of(e.getKey())));
                int sx = PX + (col % 6) * 32, sy = iy + (col / 6) * 20; // 6 列×32px；m166 列表让位后清单区可放三行=18 种，BOM 最多 14 种不越底
                ctx.drawItem(s, sx, sy);
                int got = Math.min(have.getOrDefault(e.getKey(), 0), e.getValue());
                boolean ok = got >= e.getValue();
                ctx.drawText(this.textRenderer, ok ? "×" + e.getValue() : got + "/" + e.getValue(),
                        sx + 15, sy + 5, ok ? 0xFF50E850 : SciSkin.RED, false);
                col++;
            }
        }
    }

    /** 网格 + 玩家背包里每种物品的可用量（客户端本地算，零网络）。 */
    private Map<String, Integer> countAvailable() {
        Map<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < this.handler.slots.size(); i++) {
            ItemStack s = this.handler.slots.get(i).getStack();
            if (!s.isEmpty()) m.merge(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return m;
    }

    /** 网格或背包里是否有「装着指定生物」的抓物笼子。 */
    private boolean hasCagedMob(String mob) {
        for (int i = 0; i < this.handler.slots.size(); i++) {
            ItemStack s = this.handler.slots.get(i).getStack();
            if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                    && mob.equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) return true;
        }
        return false;
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
