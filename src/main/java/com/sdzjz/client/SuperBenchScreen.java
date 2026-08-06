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

    private static final int PANEL = SciSkin.withAlpha8(SciSkin.CELL, 0xF0); // m207 孤儿归队
    private static final int CELLF = SciSkin.FRAME;
    private static final int CELLB = SciSkin.BTN_FACE;
    private static final int CYAN  = SciSkin.ACCENT;
    private static final int TXT   = SciSkin.TXT;
    private static final int SUB   = SciSkin.SUB;
    private static final int SEL   = SciSkin.withAlpha8(SciSkin.ACCENT, 0x55); // m207 孤儿归队
    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/super_bench_gui.png");

    // 浏览器布局（GUI 相对坐标）；m237 搜索框占一行：LIST_Y 30→34、行数 11→10（清单区反而更宽裕）
    private static final int PX = 270, PW = 192, LIST_Y = 34, ENTRY_H = 18, LIST_ROWS = 10;
    private static final int SEARCH_Y = 18; // m237 搜索框行（渲染/点击/占位提示三处同源）
    // m240 底图三段带绘：热栏格(304..321)原先压过艺术底边(贴图308..315行)并伸出面板5px——
    // 面板加高到 332，贴图按 [0..TEX_SPLIT) 原样 + [TEX_TILE..TEX_SPLIT) 干净行带平铺 + [TEX_SPLIT..316) 底边带挪到新底，
    // 逐行扫描证实 242..306 行只有竖向边框线（0/5/175/264-267/466-469 列），平铺像素级无缝、艺术零拉伸。
    private static final int BH = 332, TEX_H = 316, TEX_SPLIT = 304, TEX_TILE = 288;
    // m241 压缩区两钮（右栏底部，BOM 清单最深到 ~296、底边艺术带从 324 起，302..317 两不相扰）
    private static final int BTN_Y = 302, BTN_H = 15, BTN_W = 93, BTN_GAP = 6;

    private int scroll = 0;
    private int selected = -1;                 // 选中配方（ALL 下标——填料协议 clickButton(idx) 口径不变）
    private net.minecraft.client.gui.widget.TextFieldWidget search; // m237 配方搜索（m216 数据面板同工艺）
    private final java.util.List<Integer> view = new java.util.ArrayList<>(); // 过滤视图：存 ALL 下标

    public SuperBenchScreen(SuperBenchScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 470;
        this.backgroundHeight = BH; // m240 底部越界修复：316→332，热栏整体包进框内
    }

    @Override
    protected void init() {
        super.init();
        String keep = this.search != null ? this.search.getText() : ""; // resize 保留已输入（pickerField 惯例）
        this.search = new net.minecraft.client.gui.widget.TextFieldWidget(
                this.textRenderer, this.x + PX, this.y + SEARCH_Y + 1, PW - 6, 12, Text.literal("搜索"));
        this.search.setDrawsBackground(false); // m161b 去黑壳，底格自绘
        this.search.setEditableColor(TXT);
        this.search.setChangedListener(t -> refilter());
        this.search.setText(keep);
        this.addDrawableChild(this.search);
        refilter();
    }

    /** m237 过滤视图重建：按结果物品显示名/注册 id 匹配（大小写不敏感），空词=全表。 */
    private void refilter() {
        view.clear();
        String q = search != null ? search.getText().trim().toLowerCase() : "";
        List<SuperBenchRecipes.Recipe> all = SuperBenchRecipes.ALL;
        for (int i = 0; i < all.size(); i++) {
            if (!q.isEmpty()) {
                SuperBenchRecipes.Recipe r = all.get(i);
                String nm = SuperBenchRecipes.resultStack(r).getName().getString().toLowerCase();
                if (!nm.contains(q) && !r.result().toLowerCase().contains(q)) continue;
            }
            view.add(i);
        }
        scroll = 0;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.fill(0, 0, this.width, this.height, SciSkin.BACKDROP); // m117：与其余三屏统一的全屏底色（此前唯独本屏漏铺）
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        // m240 三段带绘：0..304 原样 → 288..304 干净行带平铺补 16px → 304..316 底边带落到新底（艺术零拉伸）
        ctx.drawTexture(BG, x, y, 0.0F, 0.0F, backgroundWidth, TEX_SPLIT, backgroundWidth, TEX_H);
        ctx.drawTexture(BG, x, y + TEX_SPLIT, 0.0F, (float) TEX_TILE, backgroundWidth, TEX_SPLIT - TEX_TILE, backgroundWidth, TEX_H);
        ctx.drawTexture(BG, x, y + TEX_SPLIT + (TEX_SPLIT - TEX_TILE), 0.0F, (float) TEX_SPLIT, backgroundWidth, TEX_H - TEX_SPLIT, backgroundWidth, TEX_H);
        ctx.fill(x, y, x + backgroundWidth, y + 16, SciSkin.withAlpha8(SciSkin.CELL, 0xB8));       // 标题条可读性底（m207 归队）
        ctx.fill(x + PX - 6, y + 16, x + backgroundWidth, y + backgroundHeight, SciSkin.withAlpha8(SciSkin.CELL, 0xA0)); // 浏览器区可读性底（m207 归队）
        ctx.fill(x, y, x + backgroundWidth, y + 1, CYAN);
        ctx.fill(x, y + 15, x + backgroundWidth, y + 16, CYAN);
        ctx.fill(x + PX - 6, y + 18, x + PX - 5, y + backgroundHeight, CYAN); // 分隔线
        // m237 搜索框底格（m216 工艺：CELL 底+细边，聚焦=强调色边；提示自绘在 drawForeground）
        ctx.fill(x + PX - 2, y + SEARCH_Y - 1, x + PX + PW - 2, y + SEARCH_Y + 13, SciSkin.CELL);
        ctx.drawBorder(x + PX - 2, y + SEARCH_Y - 1, PW, 14,
                search != null && search.isFocused() ? CYAN : SciSkin.CELL_FRM);

        // m241 压缩区两钮底格（label 在 drawForeground；悬停=强调色边）
        for (int b = 0; b < 2; b++) {
            int bx = x + PX + b * (BTN_W + BTN_GAP), by = y + BTN_Y;
            boolean hov = mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H;
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, SciSkin.BTN_FACE);
            ctx.drawBorder(bx, by, BTN_W, BTN_H, hov ? CYAN : SciSkin.CELL_FRM);
        }

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
        if (search != null && search.getText().isEmpty()) // m216 自绘提示：空文本两态都可见
            ctx.drawText(this.textRenderer, "搜索机器…", PX + 1, SEARCH_Y + 2, SUB, false);
        List<SuperBenchRecipes.Recipe> all = SuperBenchRecipes.ALL;
        int maxScroll = Math.max(0, view.size() - LIST_ROWS); // m237 滚动/翻页全走过滤视图
        if (scroll > maxScroll) scroll = maxScroll;
        for (int row = 0; row < LIST_ROWS; row++) {
            int vi = scroll + row;
            if (vi >= view.size()) break;
            int idx = view.get(vi);
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
        // m241 压缩区两钮文字（居中；底格在 drawBackground）
        String[] btnLabels = {"材料→压缩包", "拆开材料包"};
        for (int b = 0; b < 2; b++) {
            int bx = PX + b * (BTN_W + BTN_GAP);
            int tw = this.textRenderer.getWidth(btnLabels[b]);
            ctx.drawText(this.textRenderer, btnLabels[b], bx + (BTN_W - tw) / 2, BTN_Y + 4, TXT, false);
        }

        // 滚动提示
        ctx.drawText(this.textRenderer, view.isEmpty() ? "没有匹配的机器"
                        : (scroll + LIST_ROWS < view.size() ? "▼ 滚轮翻页 " : "") + (scroll > 0 ? "▲" : ""),
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
                ctx.drawText(this.textRenderer, ok ? "×" + cnt(e.getValue()) : cnt(got) + "/" + cnt(e.getValue()),
                        sx + 15, sy + 5, ok ? 0xFF50E850 : SciSkin.RED, false);
                col++;
            }
        }
    }

    /** m244 工程款过万计数缩写（32px 列挤不下 5 位整数；精确数在聊天缺料摘要里）。 */
    private static String cnt(int n) {
        return n >= 10000 ? String.format("%.1fK", n / 1000.0) : Integer.toString(n);
    }

    /** 网格 + 玩家背包里每种物品的可用量（客户端本地算，零网络）。 */
    private Map<String, Integer> countAvailable() {
        Map<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < this.handler.slots.size(); i++) {
            ItemStack s = this.handler.slots.get(i).getStack();
            if (s.isEmpty()) continue;
            // m242 认包：绿/红对照与服务端 gridMultiset 同口径——包按 内容物×倍率 计原版件数
            long raw = com.sdzjz.item.CompressedPackItem.rawCount(s);
            if (raw > 0) m.merge(com.sdzjz.item.CompressedPackItem.innerId(s), (int) Math.min(raw, Integer.MAX_VALUE), Integer::sum);
            else if (!(s.getItem() instanceof com.sdzjz.item.CompressedPackItem))
                m.merge(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
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
        if (button == 0 && ry >= BTN_Y && ry < BTN_Y + BTN_H && rx >= PX && rx < PX + PW) { // m241 压缩区两钮
            int b = rx < PX + BTN_W ? 0 : (rx >= PX + BTN_W + BTN_GAP ? 1 : -1);
            if (b >= 0 && this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId,
                        b == 0 ? SuperBenchScreenHandler.BTN_COMPRESS : SuperBenchScreenHandler.BTN_UNPACK);
                return true;
            }
        }
        if (button == 0 && rx >= PX && rx <= PX + PW && ry >= LIST_Y && ry < LIST_Y + LIST_ROWS * ENTRY_H) {
            int row = (int) ((ry - LIST_Y) / ENTRY_H);
            int vi = scroll + row;
            if (vi >= 0 && vi < view.size()) {
                int idx = view.get(vi); // m237 过滤视图→ALL 下标，填料协议 clickButton(原下标) 口径不变
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { // m237 聚焦时按键进搜索框（Esc 除外），防 E 关屏
        if (search != null && search.isFocused() && keyCode != 256) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (search != null && search.isFocused()) return search.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
