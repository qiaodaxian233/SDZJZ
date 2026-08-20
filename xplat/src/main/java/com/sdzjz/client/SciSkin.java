package com.sdzjz.client;

/**
 * m117 全 MOD 界面皮肤中心：所有屏幕的颜色/样式唯一出口。
 * 换肤 = 只改这个文件。语义命名，别按色相命名（"CYAN"是历史遗留，走 ACCENT）。
 * 用户若提供 slot.png / button.png（见 GUI素材.md），贴图接入点也放这里。
 */
public final class SciSkin {
    private SciSkin() {}

    // ===== 基底 =====
    public static final int BACKDROP  = 0xFF0B0D18; // 全屏底色（m207 靛紫系）（四屏统一，先铺再贴背景图）
    public static final int CELL      = 0xFF14182A; // 面板/格子底（m207）
    public static final int CELL_FRM  = 0xFF2A2F4E; // 格子细边（m207）
    public static final int FRAME     = 0xFF474C74; // 面板/节点主边框（m207 灰紫）
    public static final int HOVER     = 0xFF232849; // 行/格悬停底（m207；原蓝系变体并入史见m117）

    // ===== 强调 =====
    public static final int ACCENT    = 0xFFA8A0F0; // 主强调薰衣草紫（m207 照新截图；=出线默认色，青系退役）
    public static final int ON        = 0xFF33D07A; // 运行绿
    public static final int ON_DARK   = 0xFF10321E; // 运行绿的暗底
    public static final int RED       = 0xFFE85050; // 报警红
    public static final int RED_SOFT  = 0xFFE07070; // 柔和红（文字）
    public static final int GOLD      = 0xFFE8C43C; // 金（经验/货币）
    public static final int OFF_GRAY  = 0xFF5A6470; // 离线灰

    // ===== 文字 =====
    public static final int TXT       = 0xFFC9CCE2; // 正文（m207）
    public static final int TXT_HI    = 0xFFC7C0FB; // 高亮读数（m207）
    public static final int TXT_SOFT  = 0xFFB9BDD8; // 次级读数（m207）
    public static final int TXT_MAX   = 0xFFF1F1FC; // 最亮（悬停按钮字，m207）
    public static final int SUB       = 0xFF8A8FAE; // 辅助说明（m207）

    // ===== 按钮（四屏统一为画布 SciButton 配色；终端旧的 1E4258/3FA9D0/0D1B2C 一族并入） =====
    public static final int BTN_FRM      = 0xFF474C74; // 常态边（m207）
    public static final int BTN_FRM_HOV  = 0xFFA8A0F0; // 悬停边（m207）
    public static final int BTN_FACE     = 0xFF1B1F35; // 常态面（m207）
    public static final int BTN_FACE_HOV = 0xFF272C4D; // 悬停面（m207）

    // ===== m187 质感层（渐变端点/网格/暗角——各屏一律经由下方方法用，不许屏内散抄字面量）=====
    public static final int CARD_TOP   = 0xE0282D45; // 卡面渐变·上（m207 靛蓝卡面照截图24293E）（受光；保留 0xE0 网格微透传统）
    public static final int CARD_BOT   = 0xE00F1222; // 卡面渐变·下（m207）（沉底）
    public static final int SHEEN      = 0x22B3ABFA; // 卡顶冷光泽（m207 薰衣草）（向下渐隐到透明）
    public static final int EDGE_LIGHT = 0x2EFFFFFF; // 内顶受光棱线（全局光照自上而下）
    public static final int EDGE_DARK  = 0x8C000000; // 外圈分离暗环 + 内底压边
    public static final int BAND_TOP   = 0xF21B1F33; // 顶/底栏渐变·亮端（m207）
    public static final int BAND_BOT   = 0xF20C0F1D; // 顶/底栏渐变·暗端（m207）
    public static final int GRID_MINOR = 0x1A26456A; // 画布细网格线
    public static final int GRID_MAJOR = 0x2E3A6E96; // 画布主网格线（每4格一根）
    public static final int VIGNETTE   = 0x55000000; // 画布四缘暗角强度
    public static final int GROUP_FRM  = 0xC88C85DC; // m207 分组框边转薰衣草（原半透青蓝退役）

    // ===== m198 画布连线进/出分色（配置可调，本类为唯一出口——屏内不许硬编码） =====
    private static String wireOutSrc, wireInSrc;           // 解析缓存：配置串没变就不重解析
    private static int wireOutVal = ACCENT, wireInVal = ON;
    /** 出线色：机器产出→存储 / 机器→机器下游。配置 canvasWireOutColor(RRGGBB)，非法回退 ACCENT。 */
    public static int wireOut() {
        String c = com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor;
        if (!java.util.Objects.equals(c, wireOutSrc)) { wireOutSrc = c; wireOutVal = parseHex(c, ACCENT); }
        return wireOutVal;
    }
    /** 进线色：存储供料→机器。配置 canvasWireInColor(RRGGBB)，非法回退 ON。 */
    public static int wireIn() {
        String c = com.sdzjz.config.SdzjzConfig.get().canvasWireInColor;
        if (!java.util.Objects.equals(c, wireInSrc)) { wireInSrc = c; wireInVal = parseHex(c, ON); }
        return wireInVal;
    }
    private static int parseHex(String s, int fallback) {
        if (s == null) return fallback;
        try { return 0xFF000000 | (int) Long.parseLong(s.trim().replace("#", ""), 16); }
        catch (NumberFormatException e) { return fallback; }
    } // m192 画布分组框边/标题带基色（半透青蓝，垫在连线卡片之下）

    // ===== m200 存储终端浅色主题（配置 7 色可调，本类唯一出口；默认=作者设计稿配色方案）=====
    public static final int TERM_BASE_DEF        = 0xFFE6E8EF;
    public static final int TERM_BASE_DEEP_DEF   = 0xFFAEB4C7;
    public static final int TERM_ACCENT_DEF      = 0xFF8B7CF6;
    public static final int TERM_ACCENT_DEEP_DEF = 0xFF6D5CE0;
    public static final int TERM_INK_DEF         = 0xFF181C2B; // m207 墨色转藏蓝（照新截图工作区底）
    public static final int TERM_FRAME_DEF       = 0xFF3A3F4B;
    public static final int TERM_HI_DEF          = 0xFFFFFFFF;

    /** 配置色缓存件（m198 wireOut 同款串比：配置串不变不重解析，逐帧调用零开销）。 */
    private static final class CfgColor {
        private String src; private int val; private final int fb;
        CfgColor(int fb) { this.fb = fb; this.val = fb; }
        int get(String c) { if (!java.util.Objects.equals(c, src)) { src = c; val = parseHex(c, fb); } return val; }
    }
    private static final CfgColor T_BASE = new CfgColor(TERM_BASE_DEF), T_DEEP = new CfgColor(TERM_BASE_DEEP_DEF),
            T_ACC = new CfgColor(TERM_ACCENT_DEF), T_ACCD = new CfgColor(TERM_ACCENT_DEEP_DEF),
            T_INK = new CfgColor(TERM_INK_DEF), T_FRM = new CfgColor(TERM_FRAME_DEF), T_HI = new CfgColor(TERM_HI_DEF);

    // ===== m214 主题分家：画布(结构核心)与终端(数据面板)各一套 7 色 =====
    // scopeCanvas 由 StructureCoreScreen.render 帧首开/帧尾关（try/finally），期间 term*() 全族改读 canvas* 配置——
    // termBand/termBtn/termSlot/termSub/termGrid* 与 TermButton 全部经由这七个口取色，画布族渲染代码零改动自动跟对家。
    // 画布默认=暗夜预设（作者点名）；渲染单线程，boolean 静态位即可，不上 ThreadLocal。
    private static boolean canvasScope = false;
    public static void scopeCanvas(boolean on) { canvasScope = on; }
    public static boolean scopedCanvas() { return canvasScope; } // m239 事件路径取色前保存/恢复用
    private static final CfgColor C_BASE = new CfgColor(0xFF262C38), C_DEEP = new CfgColor(0xFF161B24), // 暗夜整行
            C_ACC = new CfgColor(0xFF8B7CF6), C_ACCD = new CfgColor(0xFFB0A6FF),
            C_INK = new CfgColor(0xFFE7EAF3), C_FRM = new CfgColor(0xFF444B5A), C_HI = new CfgColor(0xFF0E1118);

    public static int termBase()       { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_BASE.get(c.canvasBase) : T_BASE.get(c.termBase); }
    public static int termBaseDeep()   { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_DEEP.get(c.canvasBaseDeep) : T_DEEP.get(c.termBaseDeep); }
    public static int termAccent()     { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_ACC.get(c.canvasAccent) : T_ACC.get(c.termAccent); }
    public static int termAccentDeep() { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_ACCD.get(c.canvasAccentDeep) : T_ACCD.get(c.termAccentDeep); }
    public static int termInk()        { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_INK.get(c.canvasInk) : T_INK.get(c.termInk); }
    public static int termFrame()      { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_FRM.get(c.canvasFrame) : T_FRM.get(c.termFrame); }
    public static int termHi()         { com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get(); return canvasScope ? C_HI.get(c.canvasHi) : T_HI.get(c.termHi); }
    /** 终端次级文字（墨→主色 35% 提灰，随主题联动）。 */
    public static int termSub()        { return mix(termInk(), termBase(), 0.35f); }

    /** m202 十六进制解析公共出口（parseHex 公开壳：屏内画预设片/滑块换算用，保持屏内零色字面量）。 */
    public static int hex(String s, int fallback) { return parseHex(s, fallback); }

    // m202 终端主题预设（配色数据唯一家；列序=配置字段序：base/baseDeep/accent/accentDeep/ink/frame/hi）
    public static final String[] TERM_PRESET_NAMES = {"紫晶", "暗夜", "海雾", "樱粉", "松绿"};
    public static final String[][] TERM_PRESETS = {
            {"E6E8EF", "AEB4C7", "8B7CF6", "6D5CE0", "181C2B", "3A3F4B", "FFFFFF"}, // 紫晶=设计稿默认（m207 墨转藏蓝）
            {"262C38", "161B24", "8B7CF6", "B0A6FF", "E7EAF3", "444B5A", "0E1118"}, // 暗夜（墨色兼文字→亮，高亮→暗压光）
            {"E2EEF0", "A9C3C9", "2FA8C2", "1E7E93", "13282D", "39555C", "FFFFFF"}, // 海雾
            {"F4E7EC", "D3AFBE", "E06C9F", "B84D7F", "32161F", "5C3A47", "FFFFFF"}, // 樱粉
            {"E6EFE6", "AECBB0", "4CAF6E", "337E4E", "15271A", "3C5943", "FFFFFF"}, // 松绿
    };

    /** m200 终端浅色卡片：软投影 + 1px 圆角外框(角内收) + 近白提亮面 + 顶部受光渐隐 + 底压边——设计稿"圆角细边+质感"。 */
    public static void termPanel(net.minecraft.client.gui.GuiGraphics ctx, int x, int y, int w, int h) {
        softShadow(ctx, x, y, w, h);
        int frm = termFrame(), face = mix(termBase(), termHi(), 0.42f);
        ctx.fill(x, y + 1, x + w, y + h - 1, frm);
        ctx.fill(x + 1, y, x + w - 1, y + h, frm);
        ctx.fill(x + 1, y + 2, x + w - 1, y + h - 2, face);
        ctx.fill(x + 2, y + 1, x + w - 2, y + h - 1, face);
        vGrad(ctx, x + 2, y + 1, x + w - 2, y + Math.max(4, h / 4f), withAlpha(termHi(), 0.55f), withAlpha(termHi(), 0f));
        ctx.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, withAlpha(termBaseDeep(), 0.85f));
    }

    /** m200 终端凹陷槽位（浅色系程序槽；x,y 传 16×16 物品区左上角，与 drawSlot 同占位）：深灰井面+内顶阴影+内底受光。 */
    public static void termSlot(net.minecraft.client.gui.GuiGraphics ctx, int x, int y) {
        ctx.fill(x - 1, y - 1, x + 17, y + 17, termFrame());
        ctx.fill(x, y, x + 16, y + 16, termBaseDeep());
        ctx.fill(x, y, x + 16, y + 1, withAlpha(termInk(), 0.28f));
        ctx.fill(x, y + 15, x + 16, y + 16, withAlpha(termHi(), 0.45f));
    }

    /** m200 终端按钮：primary=强调紫面/深紫边(设计稿"清空回仓")，否则=墨面/边框色边(设计稿"回仓"暗钮)；文字一律高亮色。 */
    public static void termBtn(net.minecraft.client.gui.GuiGraphics ctx, net.minecraft.client.gui.Font tr,
                               int x, int y, int w, int h, String label, boolean hover, boolean primary) {
        int frm = primary ? termAccentDeep() : termFrame();
        int face = primary ? (hover ? mix(termAccent(), termHi(), 0.18f) : termAccent())
                           : (hover ? mix(termInk(), termBase(), 0.22f) : termInk());
        ctx.fill(x, y + 1, x + w, y + h - 1, frm);
        ctx.fill(x + 1, y, x + w - 1, y + h, frm);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        vGrad(ctx, x + 1, y + 1, x + w - 1, y + h / 2f, withAlpha(termHi(), primary ? 0.25f : 0.10f), withAlpha(termHi(), 0f));
        ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, termHi(), false);
    }
    public static final int GROUP_FILL = 0x142B2E56; // 画布分组框面（m192 立、m207 转靛）（极淡，透出网格不压内容）

    // ===== 贴图接入点（m118）：换皮=同名覆盖 textures/gui/ 下的 png，代码零改动 =====
    public static final net.minecraft.resources.ResourceLocation SLOT_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sdzjz", "textures/gui/slot.png");
    public static final net.minecraft.resources.ResourceLocation BUTTON_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sdzjz", "textures/gui/button.png");

    /** 18×18 槽位贴图；x,y 传 16×16 物品区左上角（贴图向外扩 1px，与旧程序槽同占位）。 */
    public static void drawSlot(net.minecraft.client.gui.GuiGraphics ctx, int x, int y) {
        ctx.drawTexture(SLOT_TEX, x - 1, y - 1, 0.0F, 0.0F, 18, 18, 18, 18);
    }

    /** m120 画布卡片 · m187 质感升级：软投影(三层渐淡)+外分离暗环+平滑渐变面(顶点插值,
     *  旧三段带状假渐变退役,0xE0 网格微透保留)+顶部冷光泽+内顶受光棱线/内底压边+四角括号刻。
     *  签名不变全部调用点白捡。顶部有强调色条的卡片，上方两刻会被条覆盖——刻意如此，下沿两刻呼应即可。 */
    public static void drawCard(net.minecraft.client.gui.GuiGraphics ctx, int x, int y, int w, int h, int frame) {
        softShadow(ctx, x, y, w, h);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, EDGE_DARK);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
        vGrad(ctx, x, y, x + w, y + h, CARD_TOP, CARD_BOT);
        vGrad(ctx, x, y, x + w, y + Math.max(6, h / 3f), SHEEN, withAlpha(SHEEN, 0f));
        ctx.fill(x, y, x + w, y + 1, EDGE_LIGHT);
        ctx.fill(x, y + h - 1, x + w, y + h, EDGE_DARK);
        int t = lighten(frame);
        ctx.fill(x, y, x + 4, y + 1, t);             ctx.fill(x, y, x + 1, y + 4, t);
        ctx.fill(x + w - 4, y, x + w, y + 1, t);     ctx.fill(x + w - 1, y, x + w, y + 4, t);
        ctx.fill(x, y + h - 1, x + 4, y + h, t);     ctx.fill(x, y + h - 4, x + 1, y + h, t);
        ctx.fill(x + w - 4, y + h - 1, x + w, y + h, t); ctx.fill(x + w - 1, y + h - 4, x + w, y + h, t);
    }

    /** m148 缓动 easeOutCubic（菜单开合/选择器滑入统一手感）。 */
    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** m148 颜色 alpha 乘法（淡入动画用）。注意 MC 把 alpha<0x04 的文字当不透明渲染，
     *  文字侧请自行钳下限（fills 无此坑）。 */
    public static int withAlpha(int color, float a) {
        int al = (int) (((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    /** m207 精确置 alpha 字节（withAlpha 是"乘现有alpha"的小数口径，转固定 alpha 有截断误差）：
     *  屏内"半透调色板色"一律走此出口，别再写 0xAA?????? 字面量（尺子按 RGB 对表，调色板一变就成孤儿）。 */
    public static int withAlpha8(int color, int a8) {
        return ((a8 & 0xFF) << 24) | (color & 0xFFFFFF);
    }

    /** m148 两色插值（悬停渐变用，含 alpha 通道）。 */
    public static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 颜色提亮（角刻/悬停微光用）。 */
    public static int lighten(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | (Math.min(255, r + 70) << 16) | (Math.min(255, g + 70) << 8) | Math.min(255, b + 70);
    }

    /** m187 平滑纵向渐变矩形：顶点色插值零色带（照画布 wirePath/ribbon 在树先例走 GUI 顶点缓冲）。 */
    public static void vGrad(net.minecraft.client.gui.GuiGraphics ctx,
                             float x1, float y1, float x2, float y2, int top, int bottom) {
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                ctx.getVertexConsumers().getBuffer(net.minecraft.client.renderer.RenderType.getGui());
        org.joml.Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        vc.vertex(mat, x1, y1, 0).color(top);
        vc.vertex(mat, x1, y2, 0).color(bottom);
        vc.vertex(mat, x2, y2, 0).color(bottom);
        vc.vertex(mat, x2, y1, 0).color(top);
    }

    /** m187 平滑横向渐变矩形。 */
    public static void hGrad(net.minecraft.client.gui.GuiGraphics ctx,
                             float x1, float y1, float x2, float y2, int left, int right) {
        com.mojang.blaze3d.vertex.VertexConsumer vc =
                ctx.getVertexConsumers().getBuffer(net.minecraft.client.renderer.RenderType.getGui());
        org.joml.Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        vc.vertex(mat, x1, y1, 0).color(left);
        vc.vertex(mat, x1, y2, 0).color(left);
        vc.vertex(mat, x2, y2, 0).color(right);
        vc.vertex(mat, x2, y1, 0).color(right);
    }

    /** m187 软投影：三层递缩递浓半透黑（中心叠深、边缘渐淡），替代旧单层硬边黑块。 */
    public static void softShadow(net.minecraft.client.gui.GuiGraphics ctx, int x, int y, int w, int h) {
        ctx.fill(x + 1, y + 2, x + w + 5, y + h + 6, 0x12000000);
        ctx.fill(x + 2, y + 3, x + w + 4, y + h + 5, 0x1C000000);
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x26000000);
    }

    /** m187 霓虹横线：1px 亮核 + 上下 3px 渐隐光晕（顶/底栏与总线轨道线统一质感）。 */
    public static void glowLineH(net.minecraft.client.gui.GuiGraphics ctx, int x1, int x2, int y, int color) {
        vGrad(ctx, x1, y - 3, x2, y, withAlpha(color, 0f), withAlpha(color, 0.36f));
        ctx.fill(x1, y, x2, y + 1, color);
        vGrad(ctx, x1, y + 1, x2, y + 4, withAlpha(color, 0.36f), withAlpha(color, 0f));
    }

    /** m187 顶/底横栏渐变底带：全局光照统一自上而下（亮上暗下），配 glowLineH 轨道线用。 */
    public static void panelBand(net.minecraft.client.gui.GuiGraphics ctx, int x1, int y1, int x2, int y2) {
        vGrad(ctx, x1, y1, x2, y2, BAND_TOP, BAND_BOT);
    }

    /** m203 终端主题浅色横幅带（画布顶条/标题条/总线带/底栏/状态区）：近白受光→主色沉底。 */
    public static void termBand(net.minecraft.client.gui.GuiGraphics ctx, int x1, int y1, int x2, int y2) {
        vGrad(ctx, x1, y1, x2, y2, mix(termBase(), termHi(), 0.5f), termBase());
    }

    /** m203 浅色带的轨道线：1px 边框细线 + 强调色霓虹晕（浅底上晕更克制，0.55 衰减）。 */
    public static void termBandLine(net.minecraft.client.gui.GuiGraphics ctx, int x1, int x2, int y) {
        vGrad(ctx, x1, y - 3, x2, y, withAlpha(termAccent(), 0f), withAlpha(termAccent(), 0.20f));
        ctx.fill(x1, y, x2, y + 1, termFrame());
        vGrad(ctx, x1, y + 1, x2, y + 4, withAlpha(termAccent(), 0.20f), withAlpha(termAccent(), 0f));
    }

    // ===== m217 画布背景四项（配置覆盖，空/非法=跟随主题；CfgColor fb=0 作"未设"哨兵——parseHex
    // 成功必带 FF alpha 永不为 0，failed/空串落 0 即回主题色，串比缓存逐帧零开销）=====
    private static final CfgColor C_BG = new CfgColor(0), C_GRID = new CfgColor(0);
    /** 画布工作区底色：配置 canvasBgColor 覆盖，空=主题墨色。 */
    public static int canvasBg() {
        int v = C_BG.get(com.sdzjz.config.SdzjzConfig.get().canvasBgColor);
        return v == 0 ? termInk() : v;
    }
    /** m220 背景色是否被配置覆盖（画布据此隐藏装饰底图：设色=纯色画布，装饰图让位）。 */
    public static boolean canvasBgOverridden() {
        return C_BG.get(com.sdzjz.config.SdzjzConfig.get().canvasBgColor) != 0;
    }
    /** 画布网格基色（不带 alpha 语义）：配置 canvasGridColor 覆盖，空=主题强调色。设置面板样片同源。 */
    public static int canvasGridBase() {
        int v = C_GRID.get(com.sdzjz.config.SdzjzConfig.get().canvasGridColor);
        return v == 0 ? termAccent() : v;
    }
    private static float gridStrength() { // 0~3 钳位（withAlpha 内部再钳 alpha≤1，浓度 3 时细线 30%/主线 57%）
        return (float) Math.max(0.0, Math.min(3.0, com.sdzjz.config.SdzjzConfig.get().canvasGridStrength));
    }

    /** m203 画布网格随主题强调色联动（原 GRID_MINOR/MAJOR 定青退役于画布，别处未用）；m217 接色覆盖+浓度。 */
    public static int termGridMinor() { return withAlpha(canvasGridBase(), 0.10f * gridStrength()); }
    public static int termGridMajor() { return withAlpha(canvasGridBase(), 0.19f * gridStrength()); }

    /** m187 画布暗角：四缘向中心渐隐压景深，角部自然叠深；带宽随区域自适应。
     *  m217 强度可调（0~2 倍率走 withAlpha8 精确置字节——withAlpha 钳 1.0 乘不上去）；0≈关直接省四次渐变。 */
    public static void vignette(net.minecraft.client.gui.GuiGraphics ctx, int x1, int y1, int x2, int y2) {
        double s = Math.max(0.0, Math.min(2.0, com.sdzjz.config.SdzjzConfig.get().canvasVignetteStrength));
        if (s < 0.01) return;
        int v = withAlpha8(VIGNETTE, Math.min(255, (int) Math.round(((VIGNETTE >>> 24) & 0xFF) * s)));
        int bh = Math.max(24, (y2 - y1) / 6), bw = Math.max(28, (x2 - x1) / 8);
        vGrad(ctx, x1, y1, x2, y1 + bh, v, withAlpha(v, 0f));
        vGrad(ctx, x1, y2 - bh, x2, y2, withAlpha(v, 0f), v);
        hGrad(ctx, x1, y1, x1 + bw, y2, v, withAlpha(v, 0f));
        hGrad(ctx, x2 - bw, y1, x2, y2, withAlpha(v, 0f), v);
    }

    /** 按钮三切片（button.png 200×32：上=常态 下=悬停）。左右 8px 帽区原样、中段横向拉伸、整体纵向缩放到 h。 */
    public static void drawButton(net.minecraft.client.gui.GuiGraphics ctx, int x, int y, int w, int h, boolean hover) {
        int v = hover ? 16 : 0, cap = 8;
        ctx.drawTexture(BUTTON_TEX, x, y, cap, h, 0.0F, v, cap, 16, 200, 32);
        ctx.drawTexture(BUTTON_TEX, x + w - cap, y, cap, h, 200 - cap, v, cap, 16, 200, 32);
        ctx.drawTexture(BUTTON_TEX, x + cap, y, w - 2 * cap, h, cap, v, 200 - 2 * cap, 16, 200, 32);
    }
}
