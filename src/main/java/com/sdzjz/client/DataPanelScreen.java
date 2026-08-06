package com.sdzjz.client;

import com.sdzjz.net.DataPanelViewPayload;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.screen.DataPanelScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** 存储终端：搜索 + 滚动 + 大数量显示（仿 Tom's Simple Storage）。
 *  m200 照作者设计稿整体重铺：浅灰+紫主题（7 色全走 SciSkin.termXxx 配置出口）、分区卡片（标题栏/搜索/
 *  存储网格/背包/经验库/合成终端/回收）、圆角细边+受光渐变质感；标题栏"主题"钮开游戏内调色面板，
 *  打字实时换肤（m199 同款 live 写配置 + SciSkin 串比缓存）。全屏 BG 贴图退役，改程序化卡片。 */
public class DataPanelScreen extends HandledScreen<DataPanelScreenHandler>
        implements net.minecraft.client.gui.screen.recipebook.RecipeBookProvider {

    private TextFieldWidget search;
    private int scroll = 0;

    // ===== m281 原版配方书（学 Tom's Simple Storage 的机制自写）：handler 侧 m201 已继承
    // AbstractRecipeScreenHandler=协议地基早备好，本次补上屏端三件：RecipeBookWidget 实体、
    // 绿书开关钮（原版 BUTTON_TEXTURES 同款贴图）、RecipeBookProvider 接口（ghost 回包找得到书）。
    // 点配方的服务端落点在 handler.fillInputSlots（已改仓储优先），客户端零新协议。
    private final net.minecraft.client.gui.screen.recipebook.RecipeBookWidget recipeBook =
            new net.minecraft.client.gui.screen.recipebook.RecipeBookWidget();
    private boolean narrow;
    private boolean bookOn; // 配置开关快照（init 时读一次，terminalRecipeBook=false 整套隐身）
    // m286 主题化书钮（作者：原版绿书贴图在卡上突兀+没对齐）：SciSkin.termBtn 同语言自绘"配方"钮，
    // 右缘与卡内容右缘 x+346（清空回仓右端）对齐、纵向落在标题行（83..95 < 网格顶 96），渲染/命中同一套常量。
    private static final int BOOK_BTN_DX = 314, BOOK_BTN_DY = 83, BOOK_BTN_W = 32, BOOK_BTN_H = 12;

    // ===== m200 主题调色面板（modal，照 m199 画布设置面板刀法）=====
    private boolean themeOpen = false;
    private TextFieldWidget[] themeF;
    private int thSel = 0;      // m202 当前所选色（滑块作用对象）
    private int thDragCh = -1;  // m202 拖拽中的滑块通道（0/1/2=R/G/B，-1=无）
    private static final String[] THEME_LABELS = {"主色", "主色深", "强调紫", "强调深", "墨色", "边框", "高亮"};
    private static final int TH_W = 308, TH_H = 190, TH_ROW = 18;
    private static final int TH_RX = 180;                 // 右列（滑块/预设/恢复默认）相对 x
    private static final int SL_X = 192, SL_W = 86;       // 滑块轨道相对 x / 宽

    public DataPanelScreen(DataPanelScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        // ===== m281 配方书先行：findLeftEdge 会在书打开时把窗体右移，必须先定 this.x 再摆自家控件 =====
        this.bookOn = com.sdzjz.config.SdzjzConfig.get().terminalRecipeBook;
        if (bookOn) {
            this.narrow = this.width < 563; // 原版 379=176窗+203书区，同比例换算 360+203
            this.recipeBook.initialize(this.width, this.height, this.client, this.narrow, this.handler);
            this.x = this.recipeBook.findLeftEdge(this.width, this.backgroundWidth);
            this.addSelectableChild(this.recipeBook); // 书的搜索框走原版焦点派发链吃键盘（m286 书钮改自绘见 render/mouseClicked）
        }
        // m161b 搜索框去黑壳（setDrawsBackground(false)，卡片接管观感）；resize 保留已输入文字（pickerField 惯例）。
        String keep = this.search != null ? this.search.getText() : "";
        this.search = new TextFieldWidget(this.textRenderer, this.x + 16, this.y + 30, 176, 12, Text.literal("搜索"));
        this.search.setDrawsBackground(false);
        this.search.setEditableColor(SciSkin.termInk()); // m200 浅面上写墨字
        // m216 撤原版 setPlaceholder：1.21.1 的 placeholder 聚焦即隐（作者实机截图=聚焦紫描边+空框无字），
        // 改 drawBackground 自绘提示——空文本时两态（聚焦/失焦）都可见，光标压在提示首字上属正常观感。
        this.search.setChangedListener(s -> { scroll = 0; sendView(); });
        this.search.setText(keep);
        this.addDrawableChild(this.search);
        // m200 主题面板 7 色输入框（占位 Text.empty 保 literal 棘轮；live 写配置=打字实时换肤，落盘在关窗）
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        TextFieldWidget[] old = themeF;
        themeF = new TextFieldWidget[7];
        for (int i = 0; i < 7; i++) {
            final int fi = i;
            String kv = old != null && old[i] != null ? old[i].getText() : themeGet(c, i);
            themeF[i] = new TextFieldWidget(this.textRenderer, 0, 0, 54, 12, Text.empty());
            themeF[i].setDrawsBackground(false);
            themeF[i].setMaxLength(7);
            themeF[i].setText(kv == null ? "" : kv);
            themeF[i].setChangedListener(t -> themeSet(com.sdzjz.config.SdzjzConfig.get(), fi, t.trim()));
        }
    }

    private static String themeGet(com.sdzjz.config.SdzjzConfig c, int i) {
        if (i == 0) return c.termBase;
        if (i == 1) return c.termBaseDeep;
        if (i == 2) return c.termAccent;
        if (i == 3) return c.termAccentDeep;
        if (i == 4) return c.termInk;
        if (i == 5) return c.termFrame;
        return c.termHi;
    }

    private static void themeSet(com.sdzjz.config.SdzjzConfig c, int i, String v) {
        if (i == 0) c.termBase = v;
        else if (i == 1) c.termBaseDeep = v;
        else if (i == 2) c.termAccent = v;
        else if (i == 3) c.termAccentDeep = v;
        else if (i == 4) c.termInk = v;
        else if (i == 5) c.termFrame = v;
        else c.termHi = v;
    }

    /** 第 i 色的当前生效值（经 SciSkin 出口=非法回退默认，样片直显真实渲染色）。 */
    private static int themeColor(int i) {
        if (i == 0) return SciSkin.termBase();
        if (i == 1) return SciSkin.termBaseDeep();
        if (i == 2) return SciSkin.termAccent();
        if (i == 3) return SciSkin.termAccentDeep();
        if (i == 4) return SciSkin.termInk();
        if (i == 5) return SciSkin.termFrame();
        return SciSkin.termHi();
    }

    private static boolean hexOk(String s) { // 照 m199（非法只做红线提示，渲染层自会回退）
        if (s == null) return false;
        String t = s.trim().replace("#", "");
        if (t.isEmpty() || t.length() > 6) return false;
        for (int i = 0; i < t.length(); i++) if (Character.digit(t.charAt(i), 16) < 0) return false;
        return true;
    }

    private void sendView() {
        BlockPos p = this.handler.blockPos();
        if (p == null) return;
        String q = search == null ? "" : search.getText();
        ClientPlayNetworking.send(new DataPanelViewPayload(p, q, scroll, matchByLocalName(q)));
    }

    /** 用客户端本地化显示名匹配物品 id（支持中文搜索），上限 200 条防包过大。 */
    // m107a：此前每敲一个字全注册表逐项 new ItemStack+本地化(1400+项)——改静态索引一次构建。
    // 语言切换探针：石头的本地化名变了=换了语言，重建索引（不引入 LanguageManager 新接口，复用已有 getName 路径）。
    private static java.util.LinkedHashMap<String, String> nameIndex;
    private static java.util.LinkedHashMap<String, String> initIndex; // m282 id→拼音/词首字母键（与 nameIndex 同建同弃）
    private static String nameProbe;

    private static java.util.List<String> matchByLocalName(String q) {
        if (q == null || q.isBlank()) return java.util.List.of();
        String probe = new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE).getName().getString();
        if (nameIndex == null || !probe.equals(nameProbe)) {
            java.util.LinkedHashMap<String, String> idx = new java.util.LinkedHashMap<>();
            java.util.LinkedHashMap<String, String> ini = new java.util.LinkedHashMap<>();
            for (net.minecraft.item.Item item : net.minecraft.registry.Registries.ITEM) {
                String id = net.minecraft.registry.Registries.ITEM.getId(item).toString();
                String nm = new net.minecraft.item.ItemStack(item).getName().getString();
                idx.put(id, nm.toLowerCase());
                ini.put(id, PinyinInitials.of(nm)); // m282：一次同建，语言切换随探针同弃重建
            }
            nameIndex = idx;
            initIndex = ini;
            nameProbe = probe;
        }
        String lower = q.toLowerCase();
        // m282 首字母通道：纯 ASCII 字母查询才开（"zs"→钻石/"xjhjd"→下界合金锭），与子串通道取并集不抢不挡
        boolean byInit = com.sdzjz.config.SdzjzConfig.get().terminalSearchInitials && PinyinInitials.applicable(lower);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : nameIndex.entrySet()) {
            if (e.getValue().contains(lower)
                    || (byInit && initIndex.get(e.getKey()).contains(lower))) {
                out.add(e.getKey());
                if (out.size() >= 200) break;
            }
        }
        return out;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.fill(0, 0, this.width, this.height, SciSkin.termInk()); // 全屏暗底（设计稿背景色）
        SciSkin.termPanel(ctx, x + 4, y, 352, 256); // 窗体大卡
        // ===== 标题栏：图标 + 名称 + 类型用量 + 主题钮 + 关闭 =====
        ctx.fill(x + 6, y + 20, x + 354, y + 21, SciSkin.withAlpha(SciSkin.termFrame(), 0.6f)); // 分隔细线
        ctx.fill(x + 11, y + 5, x + 23, y + 17, SciSkin.termAccentDeep()); // 图标：紫方块+受光角
        ctx.fill(x + 12, y + 6, x + 22, y + 16, SciSkin.termAccent());
        ctx.fill(x + 12, y + 6, x + 16, y + 10, SciSkin.withAlpha(SciSkin.termHi(), 0.55f));
        ctx.drawText(this.textRenderer, "存储终端", x + 28, y + 7, SciSkin.termInk(), false);
        // m97/m98 全网类型用量（满=红：核心类型上限到顶新种类被拒收的原因亮出来）
        int tu = this.handler.typesUsedView(), tc = this.handler.typesCapView();
        String usage; int ucol;
        if (tc <= 0)           { usage = "无存储核心"; ucol = SciSkin.RED; }
        else if (tc == 0xFFFF) { usage = "类型 " + tu; ucol = SciSkin.termSub(); }
        else                   { usage = "类型 " + tu + "/" + tc + (tu >= tc ? " 满" : ""); ucol = tu >= tc ? SciSkin.RED : SciSkin.termSub(); }
        ctx.drawText(this.textRenderer, usage, x + 294 - this.textRenderer.getWidth(usage), y + 7, ucol, false);
        SciSkin.termBtn(ctx, this.textRenderer, x + 298, y + 4, 34, 16, "主题",
                mouseX >= x + 298 && mouseX <= x + 332 && mouseY >= y + 4 && mouseY <= y + 20, false);
        SciSkin.termBtn(ctx, this.textRenderer, x + 336, y + 4, 16, 16, "×",
                mouseX >= x + 336 && mouseX <= x + 352 && mouseY >= y + 4 && mouseY <= y + 20, false);
        // ===== 左列：搜索卡 =====
        SciSkin.termPanel(ctx, x + 8, y + 24, 191, 20);
        if (search != null && search.getText().isEmpty()) // m216 自绘默认提示：坐标=控件文字原位(x+16,y+30)，subdued 色不与输入争眼
            ctx.drawText(this.textRenderer, "搜索物品，中文/首字母…", x + 16, y + 30, SciSkin.termSub(), false);
        if (search != null && search.isFocused()) { // 聚焦=紫描边（四边 1px）
            int a = SciSkin.termAccent();
            ctx.fill(x + 9, y + 25, x + 198, y + 26, a);
            ctx.fill(x + 9, y + 42, x + 198, y + 43, a);
            ctx.fill(x + 9, y + 25, x + 10, y + 43, a);
            ctx.fill(x + 197, y + 25, x + 198, y + 43, a);
        }
        // ===== 左列：存储网格卡 + 真实比例滚动条（m107b）=====
        SciSkin.termPanel(ctx, x + 8, y + 46, 191, 118);
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 9; c++) SciSkin.termSlot(ctx, x + 16 + c * 18, y + 52 + r * 18);
        int sbx = x + 181;
        ctx.fill(sbx, y + 52, sbx + 6, y + 160, SciSkin.termBaseDeep());
        ctx.fill(sbx, y + 52, sbx + 6, y + 53, SciSkin.withAlpha(SciSkin.termInk(), 0.28f)); // 轨内顶阴影，与槽同质感
        int rowsAll = Math.max(6, this.handler.rowsView());
        int th = Math.max(12, 108 * 6 / rowsAll);
        int mr = Math.max(0, this.handler.rowsView() - 6);
        int ty2 = y + 52 + (mr == 0 ? 0 : (108 - th) * Math.min(scroll, mr) / mr);
        ctx.fill(sbx, ty2, sbx + 6, ty2 + (mr == 0 ? 108 : th),
                mr == 0 ? SciSkin.mix(SciSkin.termBaseDeep(), SciSkin.termFrame(), 0.5f) : SciSkin.termAccent());
        // ===== 左列：背包卡 =====
        SciSkin.termPanel(ctx, x + 8, y + 168, 191, 87);
        ctx.drawText(this.textRenderer, "背包", x + 16, y + 170, SciSkin.termSub(), false);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) SciSkin.termSlot(ctx, x + 16 + c * 18, y + 181 + r * 18);
        for (int c = 0; c < 9; c++) SciSkin.termSlot(ctx, x + 16 + c * 18, y + 237);
        // ===== 右列：经验库（m80c）=====
        SciSkin.termPanel(ctx, x + 205, y + 24, 147, 52);
        ctx.fill(x + 211, y + 29, x + 213, y + 37, SciSkin.termAccent()); // 标题紫刻
        ctx.drawText(this.textRenderer, "经验库", x + 217, y + 29, SciSkin.termSub(), false);
        ctx.drawText(this.textRenderer, fmt(this.handler.xpBankView()) + " 点", x + 217, y + 41, SciSkin.termAccentDeep(), false);
        SciSkin.termBtn(ctx, this.textRenderer, x + 211, y + 56, 66, 16, "存入经验",
                mouseX >= x + 211 && mouseX <= x + 277 && mouseY >= y + 56 && mouseY <= y + 72, false);
        SciSkin.termBtn(ctx, this.textRenderer, x + 281, y + 56, 66, 16, "取出经验",
                mouseX >= x + 281 && mouseX <= x + 347 && mouseY >= y + 56 && mouseY <= y + 72, false);
        // ===== 右列：合成终端（m84b）=====
        SciSkin.termPanel(ctx, x + 205, y + 80, 147, 98);
        ctx.fill(x + 211, y + 85, x + 213, y + 93, SciSkin.termAccent());
        ctx.drawText(this.textRenderer, "合成终端", x + 217, y + 85, SciSkin.termSub(), false);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) SciSkin.termSlot(ctx, x + 213 + c * 18, y + 96 + r * 18);
        ctx.drawText(this.textRenderer, "▶", x + 283, y + 118, SciSkin.termAccent(), false); // 网格→结果
        ctx.fill(x + 306, y + 111, x + 328, y + 133, SciSkin.termAccentDeep()); // 结果格紫外环
        SciSkin.termSlot(ctx, x + 309, y + 114);
        SciSkin.termBtn(ctx, this.textRenderer, x + 211, y + 156, 135, 16, "清空回仓",
                mouseX >= x + 211 && mouseX <= x + 346 && mouseY >= y + 156 && mouseY <= y + 172, true); // 设计稿主紫钮
        // ===== 右列：回收（放入即销毁）=====
        SciSkin.termPanel(ctx, x + 205, y + 182, 147, 73);
        ctx.fill(x + 211, y + 187, x + 213, y + 195, SciSkin.RED);
        ctx.drawText(this.textRenderer, "回收", x + 217, y + 187, SciSkin.termSub(), false);
        ctx.fill(x + 266, y + 199, x + 288, y + 221, SciSkin.RED); // 危险红外环
        SciSkin.termSlot(ctx, x + 269, y + 202);
        String rc = "放入即销毁";
        ctx.drawText(this.textRenderer, rc, x + 278 - this.textRenderer.getWidth(rc) / 2, y + 228, SciSkin.termSub(), false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // m200 全部文字随卡片画在 drawBackground（撤原版标题/物品栏默认字）；本覆盖保留为空即达意。
    }

    @Override
    protected void drawSlot(DrawContext ctx, net.minecraft.screen.slot.Slot slot) {
        if (!(slot.inventory instanceof PlayerInventory) && slot.hasStack()) {
            net.minecraft.item.ItemStack st = slot.getStack();
            ctx.drawItem(st, slot.x, slot.y);
            String s = fmt(amtOf(st));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(slot.x + 17, slot.y + 12.5f, 200); // 右下角锚定
            ctx.getMatrices().scale(0.5f, 0.5f, 1f);                       // 半尺寸：最长 "606.4K" 也压不出格
            ctx.drawText(this.textRenderer, s, -this.textRenderer.getWidth(s), 0, SciSkin.termHi(), true);
            ctx.getMatrices().pop();
        } else {
            super.drawSlot(ctx, slot);
        }
    }

    private int qtySlot = -1, qtyX, qtyY; // m82 数量选择浮层
    private static final int[] QTY = {1, 8, 16, 32, 64};
    private static final String[] QTY2 = {"2组", "4组", "8组", "填满"}; // m100 批量行：k=5..8(组=堆叠上限,填满=装满背包余量回仓)

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (themeOpen) return themeClick(mx, my, button); // m200 主题面板 modal 最先判（m103 区域化）
        if (qtySlot >= 0) { // 浮层打开中：命中按钮或关闭
            for (int k = 0; k < QTY.length; k++) { // 第一行：定量
                int bx = qtyX + k * 26, by = qtyY;
                if (mx >= bx && mx <= bx + 24 && my >= by && my <= by + 16) {
                    clickXp(1000 + qtySlot * 10 + k);
                    qtySlot = -1;
                    return true;
                }
            }
            for (int j = 0; j < QTY2.length; j++) { // m100 第二行：批量
                int bx = qtyX + j * 32, by = qtyY + 20;
                if (mx >= bx && mx <= bx + 30 && my >= by && my <= by + 16) {
                    clickXp(1000 + qtySlot * 10 + (5 + j));
                    qtySlot = -1;
                    return true;
                }
            }
            qtySlot = -1;
            return true;
        }
        // m281 配方书点击（原版 CraftingScreen 同款）：命中即夺焦；窄屏开书=书外点击全吞防误操作底下窗
        if (bookOn) {
            if (button == 0 && mx >= this.x + BOOK_BTN_DX && mx < this.x + BOOK_BTN_DX + BOOK_BTN_W
                    && my >= this.y + BOOK_BTN_DY && my < this.y + BOOK_BTN_DY + BOOK_BTN_H) {
                toggleBook(); // m286 自绘钮命中在书之前：窄屏开书时钮浮在书上仍可一键关
                return true;
            }
            if (this.recipeBook.mouseClicked(mx, my, button)) { this.setFocused(this.recipeBook); return true; }
            if (this.narrow && this.recipeBook.isOpen()) return true;
        }
        // m107b：滚动条命中——点轨道跳页并开始拖拽（浮层打开时不抢，见上）
        int sbx = this.x + 181;
        if (button == 0 && mx >= sbx - 1 && mx <= sbx + 7 && my >= this.y + 52 && my <= this.y + 160) {
            sbDrag = true;
            sbUpdate(my);
            return true;
        }
        // m111 AE 手感：光标拿着东西点存储区 = 存入（左键全放/右键放1）——原版此处是"无操作"，必须拦截
        boolean overGridClick = mx >= this.x + 16 && mx < this.x + 178 && my >= this.y + 52 && my < this.y + 160;
        if (overGridClick && !this.handler.getCursorStack().isEmpty() && (button == 0 || button == 1)) {
            clickXp(button == 0 ? 4 : 5);
            return true;
        }
        // m126b AE CRAFT_STACK：右键结果格=连续合成一整组到光标（部分取出路径在 handler 焊死，见 m127b）
        if (button == 1) {
            var resSlot = this.handler.slots.get(DataPanelScreenHandler.RESULT); // m201 槽序重排：结果=9
            int rsx = this.x + resSlot.x, rsy = this.y + resSlot.y;
            if (mx >= rsx && mx < rsx + 16 && my >= rsy && my < rsy + 16) {
                if (resSlot.hasStack()) clickXp(6);
                return true;
            }
        }
        if (button == 1) { // m113 空手右键存储格=数量浮层（定量/拿满是百万量级下的主力，肌肉记忆优先）
            for (int i = DataPanelScreenHandler.DISP0; i < DataPanelScreenHandler.INV0 && i < this.handler.slots.size(); i++) { // m201 展示区=10..63
                var sl = this.handler.slots.get(i);
                if (!sl.hasStack()) continue;
                int sx = this.x + sl.x, sy = this.y + sl.y;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    qtySlot = i;
                    qtyX = (int) Math.min(mx, this.width - QTY.length * 26 - 6);
                    qtyY = (int) Math.min(my + 8, this.height - 42); // m100 两行高度
                    return true;
                }
            }
        }
        if (button == 0) { // 固定钮：经验库(1存/2取)、清空回仓(3)、主题、关闭（区域与 drawBackground 同一套）
            double rx = mx - this.x, ry = my - this.y;
            if (rx >= 211 && rx <= 277 && ry >= 56 && ry <= 72) { clickXp(1); return true; }
            if (rx >= 281 && rx <= 347 && ry >= 56 && ry <= 72) { clickXp(2); return true; }
            if (rx >= 211 && rx <= 346 && ry >= 156 && ry <= 172) { clickXp(3); return true; }
            if (rx >= 298 && rx <= 332 && ry >= 4 && ry <= 20) { openTheme(); return true; }
            if (rx >= 336 && rx <= 352 && ry >= 4 && ry <= 20) { this.close(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void clickXp(int id) {
        if (this.client != null && this.client.interactionManager != null)
            this.client.interactionManager.clickButton(this.handler.syncId, id);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (themeOpen) return true; // m200 主题面板吞滚轮
        // m107b：只在悬停存储格/滚动条区域时翻页（m103 交易列表同款教训），指着背包/合成区滚不再劫持
        boolean overGrid = mx >= this.x + 16 && mx <= this.x + 187
                && my >= this.y + 52 && my <= this.y + 160;
        if (!overGrid) return super.mouseScrolled(mx, my, h, v);
        int mr = Math.max(0, this.handler.rowsView() - 6); // 真实 clamp，撤 bottomFull 启发式
        int ns = Math.max(0, Math.min(mr, scroll + (v < 0 ? 1 : -1)));
        if (ns != scroll) { scroll = ns; sendView(); }
        return true;
    }

    // ===== m107b 滚动条拖拽 =====
    private boolean sbDrag = false;

    private void sbUpdate(double my) {
        int mr = Math.max(0, this.handler.rowsView() - 6);
        if (mr <= 0) return;
        int rowsAll = Math.max(6, this.handler.rowsView());
        int th = Math.max(12, 108 * 6 / rowsAll);
        double rel = (my - (this.y + 52) - th / 2.0) / (double) (108 - th);
        int ns = (int) Math.round(Math.max(0, Math.min(1, rel)) * mr);
        if (ns != scroll) { scroll = ns; sendView(); }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (themeOpen) { // m202 滑块拖拽实时换肤
            if (thDragCh >= 0) thApplySlider(mx);
            return true;
        }
        if (sbDrag) { sbUpdate(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (themeOpen) { thDragCh = -1; return true; } // m202 松手停拖
        if (sbDrag) { sbDrag = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (themeOpen) { // m200 主题窗：Esc=关，其余喂 7 色框（未聚焦的自不吃）
            if (keyCode == 256) { closeTheme(); return true; }
            for (TextFieldWidget f : themeF) f.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (search != null && search.isFocused() && keyCode != 256) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (themeOpen) { for (TextFieldWidget f : themeF) f.charTyped(chr, modifiers); return true; } // m200
        if (search != null && search.isFocused()) {
            return search.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void removed() {
        if (themeOpen) com.sdzjz.config.SdzjzConfig.save(); // m200 主题窗开着直接关屏也落盘（m199 同款兜底）
        super.removed();
    }

    // ===== m281 配方书四件收尾（全按原版 CraftingScreen 刀法）=====
    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (bookOn) this.recipeBook.update(); // 背包变动→"可合成"筛选/ghost 跟着刷
    }

    @Override
    protected void onMouseClick(net.minecraft.screen.slot.Slot slot, int slotId, int button,
                                net.minecraft.screen.slot.SlotActionType actionType) {
        super.onMouseClick(slot, slotId, button, actionType);
        if (bookOn) this.recipeBook.slotClicked(slot); // 手动动格=清 ghost（原版语义）
    }

    @Override
    protected boolean isClickOutsideBounds(double mx, double my, int left, int top, int button) {
        boolean out = mx < left || my < top || mx >= left + this.backgroundWidth || my >= top + this.backgroundHeight;
        if (!bookOn) return out;
        // 书区不算"窗外"——否则拿着东西点书=误丢地上（原版同款合取）
        return this.recipeBook.isClickOutsideBounds(mx, my, this.x, this.y,
                this.backgroundWidth, this.backgroundHeight, button) && out;
    }

    /** m286 书开合（原版 CraftingScreen 同款挪窗）：翻书→重算左缘→自家绝对坐标控件跟着窗体走。 */
    private void toggleBook() {
        recipeBook.toggleOpen();
        this.x = recipeBook.findLeftEdge(this.width, this.backgroundWidth);
        if (search != null) search.setX(this.x + 16);
    }

    @Override
    public void refreshRecipeBook() { this.recipeBook.refresh(); } // 数据包重载(/reload)后原版回调

    /** m289 库存摘要到货：催书全量重算（refresh=method_2592 重建输入与结果；书没开着字段也安全）。
     *  书的 update() 只认玩家背包 changeCount，仓储侧机器进出料不动背包——不催就一直陈灰。 */
    public void onStockSync() {
        if (bookOn) this.recipeBook.refresh();
    }

    @Override
    public net.minecraft.client.gui.screen.recipebook.RecipeBookWidget getRecipeBookWidget() {
        return this.recipeBook; // CraftFailedResponse 回包经它把 ghost 配方摆进网格
    }

    // ===== m200 主题调色面板 =====
    private void openTheme() {
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        for (int i = 0; i < 7; i++) themeF[i].setText(themeGet(c, i)); // 开窗对齐配置现值
        themeOpen = true;
    }

    private void closeTheme() {
        themeOpen = false;
        for (TextFieldWidget f : themeF) f.setFocused(false);
        com.sdzjz.config.SdzjzConfig.save();
    }

    private int[] thPos() { return new int[]{(this.width - TH_W) / 2, (this.height - TH_H) / 2}; }

    /** 主题面板渲染：面板自身就用 termPanel/termBtn 画——调色时面板同步换肤，所见即所得。
     *  m202：整体抬 z=400（槽内物品/数量角标画在 z100~200 带深度测试，z0 后画的填充会被剔除=物品穿透，
     *  作者截图实锤）；布局改双列——左 7 色行（点行选中），右 所选色 RGB 滑块 + 预设 + 恢复默认。 */
    private void renderTheme(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int px = thPos()[0], py = thPos()[1];
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400); // 与原版 tooltip 同层且后画=盖顶
        SciSkin.termPanel(ctx, px, py, TH_W, TH_H);
        ctx.drawText(this.textRenderer, "终端主题（RRGGBB）", px + 8, py + 7, SciSkin.termInk(), false);
        for (int i = 0; i < 7; i++) {
            int ry = py + 22 + i * TH_ROW;
            ctx.drawText(this.textRenderer, THEME_LABELS[i], px + 8, ry + 3,
                    i == thSel ? SciSkin.termAccentDeep() : SciSkin.termSub(), false);
            ctx.fill(px + 62, ry - 1, px + 120, ry + 14, SciSkin.termBaseDeep()); // 输入井
            ctx.fill(px + 62, ry - 1, px + 120, ry, SciSkin.withAlpha(SciSkin.termInk(), 0.28f));
            themeF[i].setEditableColor(SciSkin.termInk()); // 浅井写墨字，随主题联动
            themeF[i].setX(px + 65);
            themeF[i].setY(ry + 2);
            themeF[i].render(ctx, mouseX, mouseY, delta);
            if (i == thSel) ctx.fill(px + 124, ry - 3, px + 144, ry + 14, SciSkin.termAccent()); // 选中环
            ctx.fill(px + 125, ry - 2, px + 143, ry + 13, SciSkin.termFrame());
            ctx.fill(px + 126, ry - 1, px + 142, ry + 12, themeColor(i)); // 样片=实际生效色（非法自动显回退）
            if (!hexOk(themeF[i].getText())) ctx.fill(px + 62, ry + 14, px + 120, ry + 15, SciSkin.RED);
        }
        // ===== 右列：所选色 RGB 滑块 =====
        ctx.drawText(this.textRenderer, "调节: " + THEME_LABELS[thSel], px + TH_RX, py + 22, SciSkin.termInk(), false);
        int c = themeColor(thSel);
        String[] chn = {"R", "G", "B"};
        for (int ch = 0; ch < 3; ch++) {
            int v = (c >> (16 - ch * 8)) & 0xFF;
            int sy = py + 36 + ch * 18;
            ctx.drawText(this.textRenderer, chn[ch], px + TH_RX, sy + 1, SciSkin.termSub(), false);
            ctx.fill(px + SL_X - 1, sy - 1, px + SL_X + SL_W + 1, sy + 11, SciSkin.termFrame()); // 轨
            ctx.fill(px + SL_X, sy, px + SL_X + SL_W, sy + 10, SciSkin.termBaseDeep());
            int kx = px + SL_X + (int) Math.round(v / 255.0 * (SL_W - 4));
            ctx.fill(px + SL_X, sy, kx + 2, sy + 10, SciSkin.withAlpha(SciSkin.termAccent(), 0.55f)); // 已填段
            ctx.fill(kx, sy - 1, kx + 4, sy + 11, SciSkin.termAccentDeep()); // 滑钮
            ctx.fill(kx + 1, sy, kx + 3, sy + 10, SciSkin.termHi());
            String vs = String.valueOf(v);
            ctx.drawText(this.textRenderer, vs, px + SL_X + SL_W + 6, sy + 1, SciSkin.termInk(), false);
        }
        // ===== 右列：预设 =====
        int hovP = hoveredPreset(mouseX, mouseY);
        ctx.drawText(this.textRenderer, "预设" + (hovP >= 0 ? ": " + SciSkin.TERM_PRESET_NAMES[hovP] : ""),
                px + TH_RX, py + 94, SciSkin.termSub(), false);
        for (int k = 0; k < SciSkin.TERM_PRESETS.length; k++) {
            int cx = px + TH_RX + k * 24, cy = py + 106;
            ctx.fill(cx - 1, cy - 1, cx + 21, cy + 15, hovP == k ? SciSkin.termAccentDeep() : SciSkin.termFrame());
            ctx.fill(cx, cy, cx + 20, cy + 14, SciSkin.hex(SciSkin.TERM_PRESETS[k][0], SciSkin.termBase())); // 底=预设主色
            ctx.fill(cx + 3, cy + 3, cx + 17, cy + 11, SciSkin.hex(SciSkin.TERM_PRESETS[k][2], SciSkin.termAccent())); // 心=预设强调
        }
        SciSkin.termBtn(ctx, this.textRenderer, px + TH_RX, py + 130, 120, 16, "恢复默认",
                mouseX >= px + TH_RX && mouseX <= px + TH_RX + 120 && mouseY >= py + 130 && mouseY <= py + 146, true);
        ctx.drawText(this.textRenderer, "打字/拖滑块实时换肤 · Esc/点窗外=关", px + 8, py + TH_H - 13, SciSkin.termSub(), false);
        ctx.getMatrices().pop();
    }

    /** m202 预设片悬停命中（渲染/点击同一套几何）。 */
    private int hoveredPreset(double mx, double my) {
        int px = thPos()[0], py = thPos()[1];
        if (my < py + 106 || my > py + 120) return -1;
        for (int k = 0; k < SciSkin.TERM_PRESETS.length; k++) {
            int cx = px + TH_RX + k * 24;
            if (mx >= cx && mx <= cx + 20) return k;
        }
        return -1;
    }

    /** m202 滑块通道命中（-1=未命中）。 */
    private int hitSlider(double mx, double my) {
        int px = thPos()[0], py = thPos()[1];
        if (mx < px + SL_X - 2 || mx > px + SL_X + SL_W + 2) return -1;
        for (int ch = 0; ch < 3; ch++) {
            int sy = py + 36 + ch * 18;
            if (my >= sy - 2 && my <= sy + 12) return ch;
        }
        return -1;
    }

    /** m202 按鼠标位写所选色的某通道：改十六进制串→setText 触发监听→配置→SciSkin 缓存重解析=实时换肤。 */
    private void thApplySlider(double mx) {
        int px = thPos()[0];
        int v = (int) Math.round(Math.max(0, Math.min(1, (mx - (px + SL_X)) / (double) (SL_W - 4))) * 255);
        int c = themeColor(thSel);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if (thDragCh == 0) r = v; else if (thDragCh == 1) g = v; else b = v;
        themeF[thSel].setText(String.format("%02X%02X%02X", r, g, b));
    }

    /** 主题面板点击派发（几何与 renderTheme 同一套）。恒返回 true=modal 吞穿透。
     *  m202 聚焦修复：非 children 的 TextFieldWidget 点击不会自聚焦（原版聚焦由 ParentElement 派发，
     *  手搓 modal 没这条链）——命中后必须显式 setFocused(true)，"颜色改不了"根因即此。 */
    private boolean themeClick(double mx, double my, int button) {
        int px = thPos()[0], py = thPos()[1];
        if (mx < px || mx > px + TH_W || my < py || my > py + TH_H) { closeTheme(); return true; } // 窗外点=关
        for (int i = 0; i < 7; i++) {
            if (themeF[i].mouseClicked(mx, my, button)) {
                for (int j = 0; j < 7; j++) themeF[j].setFocused(j == i); // 显式聚焦（先 mouseClicked 定光标位）
                thSel = i; // 编辑哪行，滑块就跟哪行
                return true;
            }
        }
        for (TextFieldWidget f : themeF) f.setFocused(false);
        if (button != 0) return true;
        for (int i = 0; i < 7; i++) { // 点行（标签/样片区）=选中该色
            int ry = py + 22 + i * TH_ROW;
            if (my >= ry - 3 && my <= ry + 15 && ((mx >= px + 6 && mx <= px + 60) || (mx >= px + 122 && mx <= px + 146))) {
                thSel = i;
                return true;
            }
        }
        int ch = hitSlider(mx, my); // 点滑块轨=起拖并立即写值
        if (ch >= 0) {
            thDragCh = ch;
            thApplySlider(mx);
            return true;
        }
        int pk = hoveredPreset(mx, my); // 预设片：整套七色一键应用
        if (pk >= 0) {
            for (int i = 0; i < 7; i++) themeF[i].setText(SciSkin.TERM_PRESETS[pk][i]);
            com.sdzjz.config.SdzjzConfig.save();
            return true;
        }
        if (mx >= px + TH_RX && mx <= px + TH_RX + 120 && my >= py + 130 && my <= py + 146) { // 恢复默认
            com.sdzjz.config.SdzjzConfig d = new com.sdzjz.config.SdzjzConfig();
            for (int i = 0; i < 7; i++) themeF[i].setText(themeGet(d, i)); // setText 触发 listener 回写配置
            com.sdzjz.config.SdzjzConfig.save();
        }
        return true;
    }

    private static long amtOf(net.minecraft.item.ItemStack st) {
        var c = st.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (c != null) {
            var t = c.copyNbt();
            if (t.contains("amt")) return t.getLong("amt");
        }
        return st.getCount();
    }

    private static String fmt(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000L) return trim(n / 1_000.0) + "K";
        if (n < 1_000_000_000L) return trim(n / 1_000_000.0) + "M";
        if (n < 1_000_000_000_000L) return trim(n / 1_000_000_000.0) + "B";
        return trim(n / 1_000_000_000_000.0) + "T";
    }

    private static String trim(double v) {
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // m281 渲染次序照原版 CraftingScreen：窄屏开书=书盖窗只画底；常规=窗→书→网格 ghost；tooltip 殿后
        if (bookOn && this.recipeBook.isOpen() && this.narrow) {
            this.renderBackground(ctx, mouseX, mouseY, delta);
            this.recipeBook.render(ctx, mouseX, mouseY, delta);
        } else {
            super.render(ctx, mouseX, mouseY, delta);
            if (bookOn) {
                this.recipeBook.render(ctx, mouseX, mouseY, delta);
                this.recipeBook.drawGhostSlots(ctx, this.x, this.y, true, delta); // 缺料半透明配方影
            }
        }
        if (bookOn) { // m286 主题书钮（书之后画=窄屏开书浮在书上可关；开=主紫态）
            int bbx = this.x + BOOK_BTN_DX, bby = this.y + BOOK_BTN_DY;
            SciSkin.termBtn(ctx, this.textRenderer, bbx, bby, BOOK_BTN_W, BOOK_BTN_H, "配方",
                    mouseX >= bbx && mouseX < bbx + BOOK_BTN_W && mouseY >= bby && mouseY < bby + BOOK_BTN_H,
                    this.recipeBook.isOpen());
            // m298 摘要截断的诚实提示（审计"缺席≠0"）：仓储原料种数超摘要额度时，可合成灰名单只是参考——
            // 点红配方照样发服务端填料，真缺不缺以实际填料为准。0.62 缩放小字贴书钮下沿，不碰点击区。
            if (this.recipeBook.isOpen() && this.handler.stockTruncated()) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(bbx + BOOK_BTN_W, bby + BOOK_BTN_H + 2, 0);
                ctx.getMatrices().scale(0.62F, 0.62F, 1F);
                String warn = "库存摘要超额，灰名单仅供参考";
                ctx.drawText(this.textRenderer, warn, -this.textRenderer.getWidth(warn), 0, SciSkin.termAccentDeep(), false);
                ctx.getMatrices().pop();
            }
        }
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
        if (bookOn) this.recipeBook.drawTooltip(ctx, this.x, this.y, mouseX, mouseY);
        if (qtySlot >= 0) { // m82 数量选择浮层 + m100 批量行（m200 换终端主题皮；m202 抬 z=400 防槽物品穿透）
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            int w = QTY.length * 26 + 6;
            ctx.fill(qtyX - 5, qtyY - 17, qtyX + w + 1, qtyY + 41, SciSkin.termFrame());
            ctx.fill(qtyX - 4, qtyY - 16, qtyX + w, qtyY + 40, SciSkin.withAlpha(SciSkin.termInk(), 0.96f));
            ctx.drawText(this.textRenderer, "取出数量:", qtyX, qtyY - 12, SciSkin.termHi(), false);
            for (int k = 0; k < QTY.length; k++) { // 第一行：定量
                int bx = qtyX + k * 26, by = qtyY;
                boolean hov = mouseX >= bx && mouseX <= bx + 24 && mouseY >= by && mouseY <= by + 16;
                SciSkin.termBtn(ctx, this.textRenderer, bx, by, 24, 16, String.valueOf(QTY[k]), hov, hov);
            }
            for (int j = 0; j < QTY2.length; j++) { // m100 第二行：批量(2组/4组/8组/填满背包)
                int bx = qtyX + j * 32, by = qtyY + 20;
                boolean hov = mouseX >= bx && mouseX <= bx + 30 && mouseY >= by && mouseY <= by + 16;
                SciSkin.termBtn(ctx, this.textRenderer, bx, by, 30, 16, QTY2[j], hov, hov);
            }
            ctx.getMatrices().pop();
        }
        if (themeOpen) renderTheme(ctx, mouseX, mouseY, delta); // m200 主题面板压最上层（tooltip 之后）
    }
}
