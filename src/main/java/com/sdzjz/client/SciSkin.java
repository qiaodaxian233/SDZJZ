package com.sdzjz.client;

/**
 * m117 全 MOD 界面皮肤中心：所有屏幕的颜色/样式唯一出口。
 * 换肤 = 只改这个文件。语义命名，别按色相命名（"CYAN"是历史遗留，走 ACCENT）。
 * 用户若提供 slot.png / button.png（见 GUI素材.md），贴图接入点也放这里。
 */
public final class SciSkin {
    private SciSkin() {}

    // ===== 基底 =====
    public static final int BACKDROP  = 0xFF080B12; // 全屏底色（四屏统一，先铺再贴背景图）
    public static final int CELL      = 0xFF0A1626; // 面板/格子底
    public static final int CELL_FRM  = 0xFF163049; // 格子细边
    public static final int FRAME     = 0xFF1C5A80; // 面板/节点主边框
    public static final int HOVER     = 0xFF14304A; // 行/格悬停底（原 0xFF102A40 变体并入）

    // ===== 强调 =====
    public static final int ACCENT    = 0xFF2EC4FF; // 主强调青
    public static final int ON        = 0xFF33D07A; // 运行绿
    public static final int ON_DARK   = 0xFF10321E; // 运行绿的暗底
    public static final int RED       = 0xFFE85050; // 报警红
    public static final int RED_SOFT  = 0xFFE07070; // 柔和红（文字）
    public static final int GOLD      = 0xFFE8C43C; // 金（经验/货币）
    public static final int OFF_GRAY  = 0xFF5A6470; // 离线灰

    // ===== 文字 =====
    public static final int TXT       = 0xFFBFD2EC; // 正文
    public static final int TXT_HI    = 0xFF9BE8FF; // 高亮读数
    public static final int TXT_SOFT  = 0xFFB9D8E8; // 次级读数
    public static final int TXT_MAX   = 0xFFE8FBFF; // 最亮（悬停按钮字）
    public static final int SUB       = 0xFF7C90B0; // 辅助说明

    // ===== 按钮（四屏统一为画布 SciButton 配色；终端旧的 1E4258/3FA9D0/0D1B2C 一族并入） =====
    public static final int BTN_FRM      = 0xFF1C5A80; // 常态边
    public static final int BTN_FRM_HOV  = 0xFF2EC4FF; // 悬停边
    public static final int BTN_FACE     = 0xFF0C1E30; // 常态面
    public static final int BTN_FACE_HOV = 0xFF123249; // 悬停面

    // ===== 贴图接入点（m118）：换皮=同名覆盖 textures/gui/ 下的 png，代码零改动 =====
    public static final net.minecraft.util.Identifier SLOT_TEX =
            net.minecraft.util.Identifier.of("sdzjz", "textures/gui/slot.png");
    public static final net.minecraft.util.Identifier BUTTON_TEX =
            net.minecraft.util.Identifier.of("sdzjz", "textures/gui/button.png");

    /** 18×18 槽位贴图；x,y 传 16×16 物品区左上角（贴图向外扩 1px，与旧程序槽同占位）。 */
    public static void drawSlot(net.minecraft.client.gui.DrawContext ctx, int x, int y) {
        ctx.drawTexture(SLOT_TEX, x - 1, y - 1, 0.0F, 0.0F, 18, 18, 18, 18);
    }

    /** m120 画布卡片：投影+纵向渐变半透面（与旧 NODEBG 同 0xE0 透明度，网格微透）+边框+四角括号刻。
     *  顶部有强调色条的卡片，上方两刻会被条覆盖——刻意如此，下沿两刻保持呼应即可。 */
    public static void drawCard(net.minecraft.client.gui.DrawContext ctx, int x, int y, int w, int h, int frame) {
        ctx.fill(x + 2, y + 3, x + w + 3, y + h + 3, 0x59000000);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
        int band = Math.max(3, h / 5);
        ctx.fill(x, y, x + w, y + band, 0xE00E1E32);
        ctx.fill(x, y + band, x + w, y + h - band, 0xE00A1626);
        ctx.fill(x, y + h - band, x + w, y + h, 0xE0081220);
        int t = lighten(frame);
        ctx.fill(x, y, x + 4, y + 1, t);             ctx.fill(x, y, x + 1, y + 4, t);
        ctx.fill(x + w - 4, y, x + w, y + 1, t);     ctx.fill(x + w - 1, y, x + w, y + 4, t);
        ctx.fill(x, y + h - 1, x + 4, y + h, t);     ctx.fill(x, y + h - 4, x + 1, y + h, t);
        ctx.fill(x + w - 4, y + h - 1, x + w, y + h, t); ctx.fill(x + w - 1, y + h - 4, x + w, y + h, t);
    }

    /** 颜色提亮（角刻/悬停微光用）。 */
    public static int lighten(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | (Math.min(255, r + 70) << 16) | (Math.min(255, g + 70) << 8) | Math.min(255, b + 70);
    }

    /** 按钮三切片（button.png 200×32：上=常态 下=悬停）。左右 8px 帽区原样、中段横向拉伸、整体纵向缩放到 h。 */
    public static void drawButton(net.minecraft.client.gui.DrawContext ctx, int x, int y, int w, int h, boolean hover) {
        int v = hover ? 16 : 0, cap = 8;
        ctx.drawTexture(BUTTON_TEX, x, y, cap, h, 0.0F, v, cap, 16, 200, 32);
        ctx.drawTexture(BUTTON_TEX, x + w - cap, y, cap, h, 200 - cap, v, cap, 16, 200, 32);
        ctx.drawTexture(BUTTON_TEX, x + cap, y, w - 2 * cap, h, cap, v, 200 - 2 * cap, 16, 200, 32);
    }
}
