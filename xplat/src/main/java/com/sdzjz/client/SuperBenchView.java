package com.sdzjz.client;

import com.sdzjz.machine.SuperBenchRecipes;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** m525（SB4）超大工作台屏**绘制体+交互体两代共用件**：主线 {@code SuperBenchScreen}（m201 12×12 合成界面 + 右侧配方浏览器 m237 搜索 /
 *  m240 底图三段带绘 / m241 压缩区两钮 / m287 名称排序与 BOM 重设计 / m338 材料总览卡）整段搬入，两代屏只留输入/生命周期壳。
 *  机械替换六类：宿主字体、宿主 leftPos/topPos、宿主屏幕尺寸、面板尺寸常量化、槽位列表、发包/加控件走宿主口；
 *  真世代差只三处，收进已有的口：贴图 id 构造→{@link SciSkin#gfxTex}（m509）、物品/实体 id 解析×2→{@link SciSkin#gfxItem}（m484）、
 *  自家物品类 CompressedPackItem/CaptureCageItem（1.20.1 白名单不可见，m522b 族）→{@link SuperBenchScreenHandler#host()} 十口（m523，本世代空宿主=认包恒否/无笼）。
 *
 *  <h3>宿主前提（D5 制度化第一份模板——评审④「共用件读到的环境两代一样吗」，前科四例 m511/m516/m517）</h3>
 *  <ul>
 *  <li><b>坐标层</b>：本类全部在<b>屏幕坐标</b>下画、不 push 矩阵；renderBg 以 left/top 为原点并自铺全屏底（不依赖宿主 renderBackground 调没调），
 *      renderLabels 沿 AbstractContainerScreen 惯例已在 translate(leftPos, topPos) 下——宿主须在原生 renderLabels 里调，别挪到 render 里。</li>
 *  <li><b>字体</b>：{@link #init(Font)} 时给（Screen.font 构造期还是 null，只能 init 后取）；宿主 resize 会再调 init，本类保留搜索词与列表/选中状态。</li>
 *  <li><b>控件</b>：搜索框由本类新建，宿主 {@link Host#addBox} 挂进 renderables；<b>1.20.1 宿主要在 containerTick 手动 search().tick()</b>（光标闪烁，1.20.2 起移除，m448 原注）。</li>
 *  <li><b>取色</b>：全走 {@link SciSkin} 常量（m117 换肤只改它），不看画布作用域 scopeCanvas。</li>
 *  <li><b>事件</b>：mouseScrolled 只收纵向量 v（1.21 四参/1.20.1 三参的差留壳）；各事件回 false=没吃，宿主再走 super。charTyped 不进本类（主线原句直接回 EditBox 结果，壳里两句）。</li>
 *  </ul> */
public final class SuperBenchView {

    /** 宿主口：屏壳两代各实现——全是 AbstractContainerScreen 现成字段/方法的转发，逐帧现读（resize 后 leftPos/topPos 会变）。 */
    public interface Host {
        int left();
        int top();
        int screenW();
        int screenH();
        List<net.minecraft.world.inventory.Slot> slots();
        /** 原句 minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)，含 minecraft/gameMode 非空判；发了回 true。 */
        boolean clickButton(int id);
        /** 原句 addRenderableWidget(box)。 */
        void addBox(net.minecraft.client.gui.components.EditBox box);
    }

    public static final int WIDTH = 470;
    private static final int PANEL = SciSkin.withAlpha8(SciSkin.CELL, 0xF0); // m207 孤儿归队
    private static final int CELLF = SciSkin.FRAME;
    private static final int CELLB = SciSkin.BTN_FACE;
    private static final int CYAN  = SciSkin.ACCENT;
    private static final int TXT   = SciSkin.TXT;
    private static final int SUB   = SciSkin.SUB;
    private static final int SEL   = SciSkin.withAlpha8(SciSkin.ACCENT, 0x55); // m207 孤儿归队
    private static ResourceLocation bg; // m483 律：走世代口的常量惰性求值——static final 会在类加载时调 gfx()，早于 installGfx 就炸
    /** 原 {@code BG = ResourceLocation.fromNamespaceAndPath("sdzjz", "textures/gui/super_bench_gui.png")}，世代口 m509（1.21 fromNamespaceAndPath / 1.20.1 构造器）。 */
    private static ResourceLocation bg() { if (bg == null) bg = SciSkin.gfxTex("textures/gui/super_bench_gui.png"); return bg; }

    // 浏览器布局（GUI 相对坐标）；m237 搜索框占一行：LIST_Y 30→34、行数 11→10（清单区反而更宽裕）
    private static final int PX = 270, PW = 192, LIST_Y = 34, ENTRY_H = 18, LIST_ROWS = 10;
    private static final int SEARCH_Y = 18; // m237 搜索框行（渲染/点击/占位提示三处同源）
    // m240 底图三段带绘：热栏格(304..321)原先压过艺术底边(贴图308..315行)并伸出面板5px——
    // 面板加高到 332，贴图按 [0..TEX_SPLIT) 原样 + [TEX_TILE..TEX_SPLIT) 干净行带平铺 + [TEX_SPLIT..316) 底边带挪到新底，
    // 逐行扫描证实 242..306 行只有竖向边框线（0/5/175/264-267/466-469 列），平铺像素级无缝、艺术零拉伸。
    private static final int BH = 332, TEX_H = 316, TEX_SPLIT = 304, TEX_TILE = 288;
    public static final int HEIGHT = BH; // 主线 imageHeight = BH（m240 底部越界修复：316→332，热栏整体包进框内）
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

    private Font font; // init(Font) 时给（Screen.font 构造期为 null）
    private final Host h;

    public SuperBenchView(Host h) { this.h = h; }

    /** 主线 init() 去掉 super.init() 那句的原文；宿主每次 init（含 resize）调一次。 */
    public void init(Font font) {
        this.font = font;
        String keep = this.search != null ? this.search.getValue() : ""; // resize 保留已输入（pickerField 惯例）
        this.search = new net.minecraft.client.gui.components.EditBox(
                font, h.left() + PX, h.top() + SEARCH_Y + 1, PW - 6, 12, Component.literal("搜索"));
        this.search.setBordered(false); // m161b 去黑壳，底格自绘
        this.search.setTextColor(TXT);
        this.search.setResponder(t -> refilter());
        this.search.setValue(keep);
        h.addBox(this.search); // 主线 addRenderableWidget / 1.20.1 同名（宿主口）
        refilter();
    }

    /** 1.20.1 宿主 containerTick 里 search().tick() 用（EditBox.tick 1.20.2 起移除，共用层不能写）；也给壳的 charTyped 用。 */
    public net.minecraft.client.gui.components.EditBox search() { return search; }

    public boolean searchFocused() { return search != null && search.isFocused(); }

    /** m237 过滤视图重建：按结果物品显示名/注册 id 匹配（大小写不敏感），空词=全表。 */
    private void refilter() {
        view.clear();
        String q = search != null ? search.getValue().trim().toLowerCase() : "";
        List<SuperBenchRecipes.Recipe> all = SuperBenchRecipes.ALL;
        for (int i = 0; i < all.size(); i++) {
            if (!q.isEmpty()) {
                SuperBenchRecipes.Recipe r = all.get(i);
                String nm = SuperBenchRecipes.resultStack(r).getHoverName().getString().toLowerCase();
                if (!nm.contains(q) && !r.result().toLowerCase().contains(q)) continue;
            }
            view.add(i);
        }
        // m287 按名称排序（作者点名）：中文按码点排没意义，借 m282 的 PinyinInitials 做拼音字母序
        // （抽屉c<刷石机s<自动熔炉z…），同首字母再按显示名稳定序。view 只动显示顺序，
        // selected/clickButton 全走 ALL 原下标，填料协议零改动。
        view.sort(java.util.Comparator.comparing(i -> sortKey(SuperBenchRecipes.resultStack(all.get(i)).getHoverName().getString())));
        scroll = 0;
    }

    /** m287 名称排序键：拼音首字母串+原名（PinyinInitials 语言无关，英文名走词首字母同样成序）。 */
    private static String sortKey(String nm) {
        return PinyinInitials.of(nm) + "|" + nm;
    }

    public void renderBg(GuiGraphics ctx, int mouseX, int mouseY) { // 主线 renderBg(ctx, delta, mouseX, mouseY) 去 delta（未用）
        int x = h.left(), y = h.top();
        ctx.fill(0, 0, h.screenW(), h.screenH(), SciSkin.BACKDROP); // m117：与其余三屏统一的全屏底色（此前唯独本屏漏铺）
        ctx.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        // m240 三段带绘：0..304 原样 → 288..304 干净行带平铺补 16px → 304..316 底边带落到新底（艺术零拉伸）
        ctx.blit(bg(), x, y, 0.0F, 0.0F, WIDTH, TEX_SPLIT, WIDTH, TEX_H);
        ctx.blit(bg(), x, y + TEX_SPLIT, 0.0F, (float) TEX_TILE, WIDTH, TEX_SPLIT - TEX_TILE, WIDTH, TEX_H);
        ctx.blit(bg(), x, y + TEX_SPLIT + (TEX_SPLIT - TEX_TILE), 0.0F, (float) TEX_SPLIT, WIDTH, TEX_H - TEX_SPLIT, WIDTH, TEX_H);
        ctx.fill(x, y, x + WIDTH, y + 16, SciSkin.withAlpha8(SciSkin.CELL, 0xB8));       // 标题条可读性底（m207 归队）
        ctx.fill(x + PX - 6, y + 16, x + WIDTH, y + HEIGHT, SciSkin.withAlpha8(SciSkin.CELL, 0xA0)); // 浏览器区可读性底（m207 归队）
        ctx.fill(x, y, x + WIDTH, y + 1, CYAN);
        ctx.fill(x, y + 15, x + WIDTH, y + 16, CYAN);
        ctx.fill(x + PX - 6, y + 18, x + PX - 5, y + HEIGHT, CYAN); // 分隔线
        // m237 搜索框底格（m216 工艺：CELL 底+细边，聚焦=强调色边；提示自绘在 drawForeground）
        ctx.fill(x + PX - 2, y + SEARCH_Y - 1, x + PX + PW - 2, y + SEARCH_Y + 13, SciSkin.CELL);
        ctx.renderOutline(x + PX - 2, y + SEARCH_Y - 1, PW, 14,
                search != null && search.isFocused() ? CYAN : SciSkin.CELL_FRM);

        // m241 压缩区两钮底格（label 在 drawForeground；悬停=强调色边）
        for (int b = 0; b < 2; b++) {
            int bx = x + PX + b * (BTN_W + BTN_GAP), by = y + BTN_Y;
            boolean hov = mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H;
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, SciSkin.BTN_FACE);
            ctx.renderOutline(bx, by, BTN_W, BTN_H, hov ? CYAN : SciSkin.CELL_FRM);
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

    public void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
        ctx.drawString(font, "超大工作台 · 12×12", 8, 4, TXT, false);
        ctx.drawString(font, "→", 232 - 14, 118 + 4, CYAN, false);
        ctx.drawString(font, "材料位置随意", 8, 18 + 12 * 18 + 2, SUB, false);

        // ===== 右侧配方浏览器 =====
        ctx.drawString(font, "机器配方（点击填料）", PX, 4, CYAN, false);
        if (search != null && search.getValue().isEmpty()) // m216 自绘提示：空文本两态都可见
            ctx.drawString(font, "搜索机器…", PX + 1, SEARCH_Y + 2, SUB, false);
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
            ctx.renderItem(res, PX + 1, ey);
            // m165 档位角标：Ⅰ铜石(主世界早期)/Ⅱ铁(下界期)/Ⅲ金钻(终局)，颜色即材质盘暗示；小件 tier=0 不画
            int tier = r.tier();
            int nameW = PW - 22;
            if (tier > 0) {
                String chip = tier == 1 ? "Ⅰ" : tier == 3 ? "Ⅲ" : "Ⅱ";
                int cc = tier == 1 ? 0xFFE8A05A : tier == 3 ? 0xFF66E6FF : 0xFFB8C4D0;
                ctx.drawString(font, chip, PX + PW - 12, ey + 4, cc, false);
                nameW = PW - 36;
            }
            String nm = font.plainSubstrByWidth(res.getHoverName().getString(), nameW);
            ctx.drawString(font, nm, PX + 20, ey + 4, TXT, false);
        }
        // m241 压缩区两钮文字（居中；底格在 drawBackground）
        String[] btnLabels = {"材料→压缩包", "拆开材料包"};
        for (int b = 0; b < 2; b++) {
            int bx = PX + b * (BTN_W + BTN_GAP);
            int tw = font.width(btnLabels[b]);
            ctx.drawString(font, btnLabels[b], bx + (BTN_W - tw) / 2, BTN_Y + 4, TXT, false);
        }

        // 滚动提示
        ctx.drawString(font, view.isEmpty() ? "没有匹配的机器"
                        : (scroll + LIST_ROWS < view.size() ? "▼ 滚轮翻页 " : "") + (scroll > 0 ? "▲" : ""),
                PX, LIST_Y + LIST_ROWS * ENTRY_H + 2, SUB, false);

        // 选中配方的材料（活体对照：绿=已够(网格+背包)，红=还缺，计数显示 现有/需求）
        if (selected >= 0 && selected < all.size()) {
            int dy = LIST_Y + LIST_ROWS * ENTRY_H + 14;
            ctx.drawString(font, "需要材料：", PX, dy, SUB, false);
            java.util.List<String> mobs = all.get(selected).mobs();
            if (!mobs.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                boolean allOk = true;
                for (String mob : mobs) { // m166 多生物逐只显示 ✔/✘（如刷铁机=村民+僵尸）
                    String mn;
                    try { mn = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(SciSkin.gfxItem(mob)).getDescription().getString(); }
                    catch (Exception ex) { mn = mob; }
                    if (sb.length() > 0) sb.append(' ');
                    boolean caged = hasCagedMob(mob);
                    allOk &= caged;
                    sb.append(mn).append(caged ? "✔" : "✘");
                }
                String line = (allOk ? "已捕获: " : "需捕获(笼子装它): ") + sb;
                ctx.drawString(font, font.plainSubstrByWidth(line, PW - 58),
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
                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(SciSkin.gfxItem(e.getKey())));
                bs.put(e.getKey(), st);
                int got = Math.min(have.getOrDefault(e.getKey(), 0), e.getValue());
                bl.put(e.getKey(), got >= e.getValue() ? "×" + cnt(e.getValue()) : cnt(got) + "/" + cnt(e.getValue()));
            }
            bom.sort(java.util.Comparator.comparing(e -> sortKey(bs.get(e.getKey()).getHoverName().getString())));
            final float S = 0.62F;
            int labelW = 12;
            for (String l : bl.values()) labelW = Math.max(labelW, font.width(l));
            int colW = 16 + labelW + 4, rowH = 18; // 行高 18：0.62 缩放下 (298-240)/0.62/18=5 行×6 列=30 格，守卫者农场级 BOM 全显
            int cols = Math.max(1, (int) (PW / S) / colW);
            int rows = Math.max(1, (int) ((BTN_Y - 4 - (dy + 12)) / S) / rowH); // 上界=两钮顶再让 4px
            int cap = cols * rows;
            boolean over = bom.size() > cap;
            int show = over ? cap - 1 : bom.size();
            ctx.pose().pushPose();
            ctx.pose().translate(PX, dy + 12, 0);
            ctx.pose().scale(S, S, 1.0F);
            for (int k = 0; k < show; k++) {
                Map.Entry<String, Integer> e = bom.get(k);
                int sx = (k % cols) * colW, sy = (k / cols) * rowH;
                ctx.renderItem(bs.get(e.getKey()), sx, sy);
                boolean ok = bl.get(e.getKey()).startsWith("×");
                ctx.drawString(font, bl.get(e.getKey()), sx + 17, sy + 5,
                        ok ? 0xFF50E850 : SciSkin.RED, false);
            }
            bomOver = over; // m338 热区缓存（渲染点击同源）
            bomMoreX = PX + (int) (S * ((show % cols) * colW));
            bomMoreY = dy + 12 + (int) (S * ((show / cols) * rowH));
            bomMoreW = (int) (S * colW);
            bomMoreH = (int) (S * rowH);
            if (over) {
                boolean hovMore = mouseX - h.left() >= bomMoreX && mouseX - h.left() < bomMoreX + bomMoreW
                        && mouseY - h.top() >= bomMoreY && mouseY - h.top() < bomMoreY + bomMoreH;
                ctx.drawString(font, "+" + (bom.size() - show) + "▼",
                        (show % cols) * colW, (show / cols) * rowH + 5, hovMore ? SciSkin.ACCENT : SUB, false);
            }
            ctx.pose().popPose();

            if (bomExpanded) { // m338 材料总览卡：盖右栏（不压任何槽位，槽提示零穿透），原尺寸网格+滚轮翻行
                int ox = PX - 4, oy = 20, ow = PW + 8, oh = BTN_Y + BTN_H - oy + 2;
                ctx.pose().pushPose();
                ctx.pose().translate(0, 0, 400); // m283 置顶刀
                ctx.fill(ox - 1, oy - 1, ox + ow + 1, oy + oh + 1, SciSkin.FRAME);
                ctx.fill(ox, oy, ox + ow, oy + oh, SciSkin.CELL);
                ctx.drawString(font, "全部材料（" + bom.size() + " 种）", ox + 6, oy + 5, TXT, false);
                int gcolW = 16 + labelW + 6, gcols = Math.max(1, (ow - 14) / gcolW);
                int grows = Math.max(1, (oh - 34) / 18);
                int totalRows = (bom.size() + gcols - 1) / gcols;
                bomScroll = Math.max(0, Math.min(bomScroll, Math.max(0, totalRows - grows)));
                String foot = null;
                for (int k = bomScroll * gcols; k < bom.size() && k < (bomScroll + grows) * gcols; k++) {
                    int kk = k - bomScroll * gcols;
                    int sx = ox + 6 + (kk % gcols) * gcolW, sy = oy + 16 + (kk / gcols) * 18;
                    Map.Entry<String, Integer> e = bom.get(k);
                    ctx.renderItem(bs.get(e.getKey()), sx, sy);
                    boolean okE = bl.get(e.getKey()).startsWith("×");
                    ctx.drawString(font, bl.get(e.getKey()), sx + 17, sy + 5,
                            okE ? 0xFF50E850 : SciSkin.RED, false);
                    if (mouseX - h.left() >= sx && mouseX - h.left() < sx + gcolW
                            && mouseY - h.top() >= sy && mouseY - h.top() < sy + 18)
                        foot = bs.get(e.getKey()).getHoverName().getString() + "  "
                                + have.getOrDefault(e.getKey(), 0) + "/" + e.getValue(); // 悬停=精确数（cnt 缩写的全量口）
                }
                if (totalRows > grows) { // 迷你滚动条
                    int trackY = oy + 16, trackH = grows * 18 - 2;
                    ctx.fill(ox + ow - 5, trackY, ox + ow - 3, trackY + trackH, SciSkin.CELL_FRM);
                    int thumbH = Math.max(8, trackH * grows / totalRows);
                    int thumbY = trackY + (trackH - thumbH) * bomScroll / Math.max(1, totalRows - grows);
                    ctx.fill(ox + ow - 5, thumbY, ox + ow - 3, thumbY + thumbH, SciSkin.ACCENT);
                }
                ctx.drawString(font,
                        foot != null ? font.plainSubstrByWidth(foot, ow - 12) : "滚轮翻行 · 再点/Esc 收起",
                        ox + 6, oy + oh - 11, foot != null ? TXT : SUB, false);
                ctx.pose().popPose();
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
        for (int i = 0; i < h.slots().size(); i++) {
            ItemStack s = h.slots().get(i).getItem();
            if (s.isEmpty()) continue;
            // m242 认包：绿/红对照与服务端 gridMultiset 同口径——包按 内容物×倍率 计原版件数
            long raw = SuperBenchScreenHandler.host().packRawCount(s); // m523 宿主口（原 CompressedPackItem.rawCount）
            if (raw > 0) m.merge(SuperBenchScreenHandler.host().packInnerId(s), (int) Math.min(raw, Integer.MAX_VALUE), Integer::sum);
            else if (!SuperBenchScreenHandler.host().isPack(s))
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return m;
    }

    /** 网格或背包里是否有「装着指定生物」的抓物笼子。 */
    private boolean hasCagedMob(String mob) {
        for (int i = 0; i < h.slots().size(); i++) {
            ItemStack s = h.slots().get(i).getItem();
            if (mob.equals(SuperBenchScreenHandler.host().cagedType(s))) return true; // m523 宿主口：原 `instanceof CaptureCageItem && mob.equals(CaptureCageItem.cagedType(s))`（非笼/空笼回 null）
        }
        return false;
    }

    /** 纵向滚动量 v（1.21 四参的第四参 / 1.20.1 三参的第三参）；回 false=没吃，宿主走 super。 */
    public boolean mouseScrolled(double mouseX, double mouseY, double v) {
        if (bomExpanded) { // m338 总览卡翻行（下限此处夹，上限渲染按行数夹）
            if (v < 0) bomScroll++; else if (v > 0) bomScroll = Math.max(0, bomScroll - 1);
            return true;
        }
        double rx = mouseX - h.left();
        if (rx >= PX - 6) {
            scroll = Math.max(0, scroll - (int) Math.signum(v));
            return true;
        }
        return false;
    }

    /** 回 false=没吃，宿主走 super。 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double rx = mouseX - h.left(), ry = mouseY - h.top();
        if (bomExpanded) { bomExpanded = false; return true; } // m338 任意点=收起（可预期，不藏关闭钮）
        if (button == 0 && bomOver && rx >= bomMoreX && rx < bomMoreX + bomMoreW
                && ry >= bomMoreY && ry < bomMoreY + bomMoreH) {
            bomExpanded = true; bomScroll = 0; return true; // m338 点"+N▼"=展开全部
        }
        if (button == 0 && ry >= BTN_Y && ry < BTN_Y + BTN_H && rx >= PX && rx < PX + PW) { // m241 压缩区两钮
            int b = rx < PX + BTN_W ? 0 : (rx >= PX + BTN_W + BTN_GAP ? 1 : -1);
            if (b >= 0 && h.clickButton(b == 0 ? SuperBenchScreenHandler.BTN_COMPRESS : SuperBenchScreenHandler.BTN_UNPACK)) // 原句 minecraft.gameMode.handleInventoryButtonClick（宿主口含非空判）
                return true;
        }
        if (button == 0 && rx >= PX && rx <= PX + PW && ry >= LIST_Y && ry < LIST_Y + LIST_ROWS * ENTRY_H) {
            int row = (int) ((ry - LIST_Y) / ENTRY_H);
            int vi = scroll + row;
            if (vi >= 0 && vi < view.size()) {
                int idx = view.get(vi); // m237 过滤视图→ALL 下标，填料协议 clickButton(原下标) 口径不变
                selected = idx;
                bomExpanded = false; bomScroll = 0; // m338 换台收卡清滚
                h.clickButton(idx); // 填料（原句 minecraft.gameMode.handleInventoryButtonClick，宿主口含非空判）
                return true;
            }
        }
        return false;
    }

    /** 回 false=没吃，宿主走 super。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { // m237 聚焦时按键进搜索框（Esc 除外），防 E 关屏
        if (bomExpanded && keyCode == 256) { bomExpanded = false; return true; } // m338 Esc 收总览卡（先吃，防连带关屏）
        if (search != null && search.isFocused() && keyCode != 256) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return false;
    }
}
