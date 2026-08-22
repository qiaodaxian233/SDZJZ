package com.sdzjz.client;

/**
 * m452b：全 MOD UI 配色数值唯一出口（自 SciSkin 下沉）——SciSkin 因内联 FQN 摸 1.21 独有 API
 * （fromNamespaceAndPath/addVertex）不可挂旧世代，色值拆到本类（零 MC 触点，纯 int）供各世代
 * 白名单挂载共用。**换肤=只改本文件**（m117 铁律的落点随迁到此）；SciSkin 同名别名转发，
 * 主线全部消费点零改。加色进本类，SciSkin 加一行别名。
 */
public final class SciSkinPalette {
    private SciSkinPalette() {}

    public static final int BACKDROP  = 0xFF0B0D18; // 全屏底色（m207 靛紫系）（四屏统一，先铺再贴背景图）
    public static final int CELL      = 0xFF14182A; // 面板/格子底（m207）
    public static final int CELL_FRM  = 0xFF2A2F4E; // 格子细边（m207）
    public static final int FRAME     = 0xFF474C74; // 面板/节点主边框（m207 灰紫）
    public static final int HOVER     = 0xFF232849; // 行/格悬停底（m207；原蓝系变体并入史见m117）
    public static final int ACCENT    = 0xFFA8A0F0; // 主强调薰衣草紫（m207 照新截图；=出线默认色，青系退役）
    public static final int ON        = 0xFF33D07A; // 运行绿
    public static final int ON_DARK   = 0xFF10321E; // 运行绿的暗底
    public static final int RED       = 0xFFE85050; // 报警红
    public static final int RED_SOFT  = 0xFFE07070; // 柔和红（文字）
    public static final int GOLD      = 0xFFE8C43C; // 金（经验/货币）
    public static final int OFF_GRAY  = 0xFF5A6470; // 离线灰
    public static final int TXT       = 0xFFC9CCE2; // 正文（m207）
    public static final int TXT_HI    = 0xFFC7C0FB; // 高亮读数（m207）
    public static final int TXT_SOFT  = 0xFFB9BDD8; // 次级读数（m207）
    public static final int TXT_MAX   = 0xFFF1F1FC; // 最亮（悬停按钮字，m207）
    public static final int SUB       = 0xFF8A8FAE; // 辅助说明（m207）
    public static final int BTN_FRM      = 0xFF474C74; // 常态边（m207）
    public static final int BTN_FRM_HOV  = 0xFFA8A0F0; // 悬停边（m207）
    public static final int BTN_FACE     = 0xFF1B1F35; // 常态面（m207）
    public static final int BTN_FACE_HOV = 0xFF272C4D; // 悬停面（m207）
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
    public static final int TERM_BASE_DEF        = 0xFFE6E8EF;
    public static final int TERM_BASE_DEEP_DEF   = 0xFFAEB4C7;
    public static final int TERM_ACCENT_DEF      = 0xFF8B7CF6;
    public static final int TERM_ACCENT_DEEP_DEF = 0xFF6D5CE0;
    public static final int TERM_INK_DEF         = 0xFF181C2B; // m207 墨色转藏蓝（照新截图工作区底）
    public static final int TERM_FRAME_DEF       = 0xFF3A3F4B;
    public static final int TERM_HI_DEF          = 0xFFFFFFFF;
    public static final int GROUP_FILL = 0x142B2E56; // 画布分组框面（m192 立、m207 转靛）（极淡，透出网格不压内容）
}
