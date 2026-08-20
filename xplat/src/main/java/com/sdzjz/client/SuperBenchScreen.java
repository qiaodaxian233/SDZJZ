package com.sdzjz.client;

import com.sdzjz.machine.SuperBenchRecipes;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** 超大工作台 12×12 合成界面 + 右侧配方浏览器（点机器=自动从背包填料）。 */
public class SuperBenchScreen extends AbstractContainerScreen<SuperBenchScreenHandler> {

    private static final int PANEL = SciSkin.withAlpha8(SciSkin.CELL, 0xF0); // m207 孤儿归队
    private static final int CELLF = SciSkin.FRAME;
    private static final int CELLB = SciSkin.BTN_FACE;
    private static final int CYAN  = SciSkin.ACCENT;
    private static final int TXT   = SciSkin.TXT;
    private static final int SUB   = SciSkin.SUB;
    private static final int SEL   = SciSkin.withAlpha8(SciSkin.ACCENT, 0x55); // m207 孤儿归队
    private static final ResourceLocation BG = ResourceLocation.of("sdzjz", "textures/gui/super_bench_gui.png");

    // 浏览器布局（GUI 相对坐标）；m237 搜索框占一行：LIST_Y 30→34、行数 11→10（清单区反而更宽裕）
    private static final int PX = 270, PW = 192, LIST_Y = 34, ENTRY_H = 18, LIST_ROWS = 10;
    private static final int SEARCH_Y = 18; // m237 搜索框行（渲染/点击/占位提示三处同源）
    // m240 底图三段带绘：热栏格(304..321)原先压过艺术底边(贴图308..315行)并伸出面板5px——
    // 面板加高到 332，贴图按 [0..TEX_SPLIT) 原样 + [TEX_TILE..TEX_SPLIT) 干净行带平铺 + [TEX_SPLIT..316) 底边带挪到新底，
    // 逐行扫描证实 242..306 行只有竖向边框线（0/5/175/264-267/466-469 列），平铺像素级无缝、艺术零拉伸。
    private static final int BH = 332, TEX_H = 316, TEX_SPLIT = 304, TEX_TILE = 288;
    // m241 压缩区两钮（右栏底部，BOM 清单最深到 ~296、底边艺术带从 324 起，302..317 两不相扰）
    private static final int BTN_Y = 302, BTN_H = 15, BTN_W = 93, BTN_GAP = 6;
    // m338 材料总览卡（作者点名"+N…显示不全，加个展开全部"）：点"+N▼"展开、任意点/Esc 收起、滚轮翻行
    private boolean bomExpanded;
    private int bomScroll;
    private boolean bomOver;
    private int bomMoreX, bomMoreY, bomMoreW, bomMoreH; // "+N▼"点击热区（渲染时缓存=渲染点击同源，m215）

    private int scroll = 0;
    private int selected = -1;                 // 选中配方（ALL 下标——填料协议 clickButton(idx) 口径不变）
    private net.minecraft.client.gui.components.EditBox search; // m237 配方搜索（m216 数据面板同工艺）
    private final java.util.List<Integer> view = new java.util.ArrayList<>(); // 过滤视图：存 ALL 下标

    public SuperBenchScreen(SuperBenchScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title);
        this.backgroundWidth = 470;
        this.backgroundHeight = BH; // m240 底部越界修复：316→332，热栏整体包进框内
    }

    @Override
    protected void init() {
        super.init();
        String keep = this.search != null ? this.search.getText() : ""; // resize 保留已输入（pickerField 惯例）
        this.search = new net.minecraft.client.gui.components.EditBox(
                this.textRenderer, this.x + PX, this.y + SEARCH_Y + 1, PW - 6, 12, Component.literal("搜索"));
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
        // m287 按名称排序（作者点名）：中文按码点排没意义，借 m282 的 PinyinInitials 做拼音字母序
        // （抽屉c<刷石机s<自动熔炉z…），同首字母再按显示名稳定序。view 只动显示顺序，
        // selected/clickButton 全走 ALL 原下标，填料协议零改动。
        view.sort(java.util.Comparator.comparing(i -> sortKey(SuperBenchRecipes.resultStack(all.get(i)).getName().getString())));
        scroll = 0;
    }

    /** m287 名称排序键：拼音首字母串+原名（PinyinInitials 语言无关，英文名走词首字母同样成序）。 */
    private static String sortKey(String nm) {
        return PinyinInitials.of(nm) + "|" + nm;
    }

    @Override
    protected void drawBackground(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
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

    private void cell(GuiGraphics ctx, int x, int y) {
        ctx.fill(x - 1, y - 1, x + 17, y + 17, CELLF);
        ctx.fill(x, y, x + 16, y + 16, CELLB);
    }

    @Override
    protected void drawForeground(GuiGraphics ctx, int mouseX, int mouseY) {
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
                    try { mn = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.of(mob)).getName().getString(); }
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
            // m287 BOM 重设计（作者三点名：太多缩小/可能没显示全/乱摆）。此前 6 列×32px 三行封顶 18 种、
            // 文本预算 17px 装不下"0/256"糊到邻列，守卫者农场 30+ 种直接淹过压缩两钮（截图实锤）。
            // 现在：①条目按名称排序（拼音字母序同机器列表）；②整块 0.62 缩放（drawItem/drawText 都吃
            // pose 缩放）；③列宽=16+最长计数文本+4 自适应不再互糊；④行数按 BTN_Y 上界硬夹永不淹钮，
            // 溢出末格画"+N"绝不静默截断。
            java.util.List<Map.Entry<String, Integer>> bom =
                    new java.util.ArrayList<>(all.get(selected).ingredients().entrySet());
            java.util.Map<String, ItemStack> bs = new java.util.HashMap<>();
            java.util.Map<String, String> bl = new java.util.HashMap<>();
            for (Map.Entry<String, Integer> e : bom) {
                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.of(e.getKey())));
                bs.put(e.getKey(), st);
                int got = Math.min(have.getOrDefault(e.getKey(), 0), e.getValue());
                bl.put(e.getKey(), got >= e.getValue() ? "×" + cnt(e.getValue()) : cnt(got) + "/" + cnt(e.getValue()));
            }
            bom.sort(java.util.Comparator.comparing(e -> sortKey(bs.get(e.getKey()).getName().getString())));
            final float S = 0.62F;
            int labelW = 12;
            for (String l : bl.values()) labelW = Math.max(labelW, this.textRenderer.getWidth(l));
            int colW = 16 + labelW + 4, rowH = 18; // 行高 18：0.62 缩放下 (298-240)/0.62/18=5 行×6 列=30 格，守卫者农场级 BOM 全显
            int cols = Math.max(1, (int) (PW / S) / colW);
            int rows = Math.max(1, (int) ((BTN_Y - 4 - (dy + 12)) / S) / rowH); // 上界=两钮顶再让 4px
            int cap = cols * rows;
            boolean over = bom.size() > cap;
            int show = over ? cap - 1 : bom.size();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(PX, dy + 12, 0);
            ctx.getMatrices().scale(S, S, 1.0F);
            for (int k = 0; k < show; k++) {
                Map.Entry<String, Integer> e = bom.get(k);
                int sx = (k % cols) * colW, sy = (k / cols) * rowH;
                ctx.drawItem(bs.get(e.getKey()), sx, sy);
                boolean ok = bl.get(e.getKey()).startsWith("×");
                ctx.drawText(this.textRenderer, bl.get(e.getKey()), sx + 17, sy + 5,
                        ok ? 0xFF50E850 : SciSkin.RED, false);
            }
            bomOver = over; // m338 热区缓存（渲染点击同源）
            bomMoreX = PX + (int) (S * ((show % cols) * colW));
            bomMoreY = dy + 12 + (int) (S * ((show / cols) * rowH));
            bomMoreW = (int) (S * colW);
            bomMoreH = (int) (S * rowH);
            if (over) {
                boolean hovMore = mouseX - this.x >= bomMoreX && mouseX - this.x < bomMoreX + bomMoreW
                        && mouseY - this.y >= bomMoreY && mouseY - this.y < bomMoreY + bomMoreH;
                ctx.drawText(this.textRenderer, "+" + (bom.size() - show) + "▼",
                        (show % cols) * colW, (show / cols) * rowH + 5, hovMore ? SciSkin.ACCENT : SUB, false);
            }
            ctx.getMatrices().pop();

            if (bomExpanded) { // m338 材料总览卡：盖右栏（不压任何槽位，槽提示零穿透），原尺寸网格+滚轮翻行
                int ox = PX - 4, oy = 20, ow = PW + 8, oh = BTN_Y + BTN_H - oy + 2;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0, 0, 400); // m283 置顶刀
                ctx.fill(ox - 1, oy - 1, ox + ow + 1, oy + oh + 1, SciSkin.FRAME);
                ctx.fill(ox, oy, ox + ow, oy + oh, SciSkin.CELL);
                ctx.drawText(this.textRenderer, "全部材料（" + bom.size() + " 种）", ox + 6, oy + 5, TXT, false);
                int gcolW = 16 + labelW + 6, gcols = Math.max(1, (ow - 14) / gcolW);
                int grows = Math.max(1, (oh - 34) / 18);
                int totalRows = (bom.size() + gcols - 1) / gcols;
                bomScroll = Math.max(0, Math.min(bomScroll, Math.max(0, totalRows - grows)));
                String foot = null;
                for (int k = bomScroll * gcols; k < bom.size() && k < (bomScroll + grows) * gcols; k++) {
                    int kk = k - bomScroll * gcols;
                    int sx = ox + 6 + (kk % gcols) * gcolW, sy = oy + 16 + (kk / gcols) * 18;
                    Map.Entry<String, Integer> e = bom.get(k);
                    ctx.drawItem(bs.get(e.getKey()), sx, sy);
                    boolean okE = bl.get(e.getKey()).startsWith("×");
                    ctx.drawText(this.textRenderer, bl.get(e.getKey()), sx + 17, sy + 5,
                            okE ? 0xFF50E850 : SciSkin.RED, false);
                    if (mouseX - this.x >= sx && mouseX - this.x < sx + gcolW
                            && mouseY - this.y >= sy && mouseY - this.y < sy + 18)
                        foot = bs.get(e.getKey()).getName().getString() + "  "
                                + have.getOrDefault(e.getKey(), 0) + "/" + e.getValue(); // 悬停=精确数（cnt 缩写的全量口）
                }
                if (totalRows > grows) { // 迷你滚动条
                    int trackY = oy + 16, trackH = grows * 18 - 2;
                    ctx.fill(ox + ow - 5, trackY, ox + ow - 3, trackY + trackH, SciSkin.CELL_FRM);
                    int thumbH = Math.max(8, trackH * grows / totalRows);
                    int thumbY = trackY + (trackH - thumbH) * bomScroll / Math.max(1, totalRows - grows);
                    ctx.fill(ox + ow - 5, thumbY, ox + ow - 3, thumbY + thumbH, SciSkin.ACCENT);
                }
                ctx.drawText(this.textRenderer,
                        foot != null ? this.textRenderer.trimToWidth(foot, ow - 12) : "滚轮翻行 · 再点/Esc 收起",
                        ox + 6, oy + oh - 11, foot != null ? TXT : SUB, false);
                ctx.getMatrices().pop();
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
                m.merge(BuiltInRegistries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
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
        if (bomExpanded) { // m338 总览卡翻行（下限此处夹，上限渲染按行数夹）
            if (v < 0) bomScroll++; else if (v > 0) bomScroll = Math.max(0, bomScroll - 1);
            return true;
        }
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
        if (bomExpanded) { bomExpanded = false; return true; } // m338 任意点=收起（可预期，不藏关闭钮）
        if (button == 0 && bomOver && rx >= bomMoreX && rx < bomMoreX + bomMoreW
                && ry >= bomMoreY && ry < bomMoreY + bomMoreH) {
            bomExpanded = true; bomScroll = 0; return true; // m338 点"+N▼"=展开全部
        }
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
                bomExpanded = false; bomScroll = 0; // m338 换台收卡清滚
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
        if (bomExpanded && keyCode == 256) { bomExpanded = false; return true; } // m338 Esc 收总览卡（先吃，防连带关屏）
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
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
