package com.sdzjz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * m515（真移植·A7c）：**画布设置面板（m199/m211/m217/m223/m239/m262）两代共用一份**——主线 {@code StructureCoreScreen} 的设置面板
 * 整段搬：状态（开关 + 四个颜色 EditBox + 调节目标/拖动通道）、几何常量、init 里四框的"重排保留输入/首建取配置现值"、开/关、
 * 渲染（十行开关/步进/颜色框+样片 → RGB 三滑杆 → 主题预设五片 → 恢复默认）、点击派发、拖动/收拖、键盘/字符路由。
 *
 * <p><b>世代差为零</b>：只用 {@code EditBox}（两代同名同签名：构造/setValue/getValue/setResponder/setMaxLength/setFocused/setX/setY/render/
 * mouseClicked/keyPressed/charTyped——1.20.1 m192 重命名框在树先例）、{@code GuiGraphics.fill/drawString}、{@link SciSkin} 与 common 配置。
 * 宿主只给三样：字体、屏幕尺寸（面板居中，原文 {@code this.width/this.height}）、以及 open 之前自己清场（菜单/拾取器/重命名是宿主的 modal）。
 * 机械替换只两类：{@code this.font}→字段、{@code this.width/height}→字段；方法改名 renderSettings→render / settingsClick→click /
 * openSettings→open / closeSettings→close，宿主保留同名壳。
 *
 * <p>注：面板十行里「背景色/网格色/网格浓度/暗角强度」四行写的是 common 配置，1.20.1 的底/网格层在 A11 搬过去之前不读它们（写进去两代同一份配置，
 * 主线立刻生效，1.20.1 等 A11）。
 */
public final class CanvasSettingsPanel {
    // ===== m199 画布设置面板（游戏内可调画布客户端项；modal 照 renameField 在树写法）=====
    private boolean settingsOpen = false;
    private EditBox wireOutField, wireInField;   // 出/进线颜色 RRGGBB：live 写配置实例即时预览，关窗落盘
    private EditBox bgField, gridColField;       // m217 背景色/网格色 RRGGBB：空=跟随主题；live 写实例即时预览
    private static final int SETT_W = 236, SETT_H = 300, SETT_ROW = 18; // m262 再紧凑：行距 20→18/行区起点 24→20/滑杆内距 14→13/预设与按钮间距各收 2~3/底注释挪标题行，334→300（作者点名还是太大；m239 首轮 394→334）
    private static final int SETT_ROW0 = 20;                       // 行区起点 y 偏移（m262 收口：渲染/点击原各硬编码 24）
    // m223 颜色行 RGB 滑杆调节区（作者点名照终端主题编辑器"图六那样，不能让我自己输入"；渲染/点击几何共用以下常量，改必同改）
    private static final int SETT_SL_Y = SETT_ROW0 + 10 * SETT_ROW + 2; // 调节区头行 y 偏移（=202）
    private static final int SETT_SL_SP = 13;                     // 滑杆内距（m239 收口常量 17→14，m262 再收 13：轨高 12+1px 间隙）
    private static final int SETT_PRESET_Y = SETT_SL_Y + 14 + 3 * SETT_SL_SP + 7; // 主题预设行 y 偏移（=262）
    private static final int SETT_RESET_Y = SETT_PRESET_Y + 19;   // 恢复默认钮 y 偏移（=281；钮 16 高+3 底距=300 收口）
    private static final int SETT_SL_X = 32, SETT_SL_W = 146;     // 滑轨 x 偏移/宽（照 m202 终端滑杆工艺）
    private static final String[] SETT_COLOR_NAMES = {"出线颜色", "进线颜色", "背景色", "网格色"}; // 调节目标名（序=setFs 序）
    private int settColSel = 2;   // m223 调节目标：0出线 1进线 2背景 3网格（默认背景色，作者截图点名的行）
    private int settSlDrag = -1;  // m223 拖动中的通道 0R/1G/2B；-1=没拖
    private Font font;            // m515：宿主屏字体（init 时取，重排随之更新）
    private int width, height;    // m515：宿主屏尺寸（原文 this.width/this.height；面板居中用）

    public boolean isOpen() { return settingsOpen; }

    /** 宿主屏 init()/resize 时调：四个颜色框重排保留输入、首建取配置现值（主线 init 原文段）。 */
    public void init(Font font, int width, int height) {
        this.font = font; this.width = width; this.height = height;
        String keepO = wireOutField != null ? wireOutField.getValue() : com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor; // m199 颜色框：重排保留输入，首建取配置现值
        this.wireOutField = new EditBox(font, 0, 0, 58, 14, Component.empty()); // 占位仅narration，empty保literal棘轮（m192 教训）
        this.wireOutField.setMaxLength(7);
        this.wireOutField.setValue(keepO == null ? "" : keepO);
        this.wireOutField.setResponder(t -> com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor = t.trim()); // live 写实例=连线即时预览（SciSkin 串比缓存自动重解析）；落盘在关窗
        String keepI = wireInField != null ? wireInField.getValue() : com.sdzjz.config.SdzjzConfig.get().canvasWireInColor;
        this.wireInField = new EditBox(font, 0, 0, 58, 14, Component.empty());
        this.wireInField.setMaxLength(7);
        this.wireInField.setValue(keepI == null ? "" : keepI);
        this.wireInField.setResponder(t -> com.sdzjz.config.SdzjzConfig.get().canvasWireInColor = t.trim());
        String keepB = bgField != null ? bgField.getValue() : com.sdzjz.config.SdzjzConfig.get().canvasBgColor; // m217 背景色/网格色框（wire 同款：live 写实例即时预览，落盘在关窗）
        this.bgField = new EditBox(font, 0, 0, 58, 14, Component.empty());
        this.bgField.setMaxLength(7);
        this.bgField.setValue(keepB == null ? "" : keepB);
        this.bgField.setResponder(t -> com.sdzjz.config.SdzjzConfig.get().canvasBgColor = t.trim());
        String keepGc = gridColField != null ? gridColField.getValue() : com.sdzjz.config.SdzjzConfig.get().canvasGridColor;
        this.gridColField = new EditBox(font, 0, 0, 58, 14, Component.empty());
        this.gridColField.setMaxLength(7);
        this.gridColField.setValue(keepGc == null ? "" : keepGc);
        this.gridColField.setResponder(t -> com.sdzjz.config.SdzjzConfig.get().canvasGridColor = t.trim());
    }

    /** 开窗：对齐配置现值（主线 openSettings 原文；清场菜单/拾取器/重命名是宿主的事，留在宿主 openSettings 里）。 */
    public void open() {
        wireOutField.setValue(com.sdzjz.config.SdzjzConfig.get().canvasWireOutColor); // 开窗对齐配置现值（可能被恢复默认/改文件动过）
        wireInField.setValue(com.sdzjz.config.SdzjzConfig.get().canvasWireInColor);
        bgField.setValue(com.sdzjz.config.SdzjzConfig.get().canvasBgColor);       // m217
        gridColField.setValue(com.sdzjz.config.SdzjzConfig.get().canvasGridColor);
        settingsOpen = true;
    }

    public void close() {
        settingsOpen = false;
        settSlDrag = -1; // m223 兜底：拖着滑杆按 Esc 关窗不留残拖
        wireOutField.setFocused(false);
        wireInField.setFocused(false);
        bgField.setFocused(false);   // m217
        gridColField.setFocused(false);
        com.sdzjz.config.SdzjzConfig.save(); // 关窗落盘（开关/步进即点即存，颜色打字只写实例，这里兜底）
    }

    /** 宿主屏 removed() 时调：设置窗开着直接关屏也把颜色改动落盘（m199）。 */
    public void onScreenRemoved() { if (settingsOpen) com.sdzjz.config.SdzjzConfig.save(); }

    /** 面板左上角 {px, py}（居中；行区 py+24 起每行 SETT_ROW，几何被 renderSettings/settingsClick 共用，改必同改）。 */
    private int[] settPos() { return new int[]{(width - SETT_W) / 2, Math.max(2, (height - SETT_H) / 2)}; } // m217 变高后钳顶：极小视口宁可底部出屏也保头部可达

    // ===== m223 颜色行↔调节目标公共表（渲染与点击同源，改必同改）=====
    /** 面板行号→调节下标（3出线/4进线/6背景/7网格；其余 -1）。 */
    private static int settSelOfRow(int r) { return r == 3 ? 0 : r == 4 ? 1 : r == 6 ? 2 : r == 7 ? 3 : -1; }
    /** 调节下标→输入框（序=setFs 序：出线/进线/背景/网格）。 */
    private EditBox settColorField(int sel) { return sel == 0 ? wireOutField : sel == 1 ? wireInField : sel == 2 ? bgField : gridColField; }
    /** 调节下标→当前生效色（非法/空自动回退主题/默认，滑杆起点即所见色）。 */
    /** m239 根因修复：scopeCanvas 只在 render 帧内开（m214 try/finally），而滑杆写值走 mouse 事件路径——
     *  作用域是关的，canvasBg() 空值回退落到**终端主题**浅墨（紫晶≈E7EAF3）：点一下滑杆起点就是白、
     *  当场写进背景色="白色阴魂不散/清了又回来"。取色收口函数自己保证画布作用域（保存/恢复，
     *  渲染期已 true 不破坏）。 */
    private static int settColorVal(int sel) {
        boolean prev = SciSkin.scopedCanvas();
        SciSkin.scopeCanvas(true);
        try { return sel == 0 ? SciSkin.wireOut() : sel == 1 ? SciSkin.wireIn() : sel == 2 ? SciSkin.canvasBg() : SciSkin.canvasGridBase(); }
        finally { SciSkin.scopeCanvas(prev); }
    }

    /** m223 按鼠标位写所选色某通道（照 m202 thApplySlider：改串→setText 触发 listener→配置→SciSkin 缓存重解析=即时预览）。 */
    private void settApplySlider(double mx) {
        int px = settPos()[0];
        int v = (int) Math.round(Math.max(0, Math.min(1, (mx - (px + SETT_SL_X)) / (double) (SETT_SL_W - 4))) * 255);
        int cv = settColorVal(settColSel);
        int r = (cv >> 16) & 0xFF, g = (cv >> 8) & 0xFF, b = cv & 0xFF;
        if (settSlDrag == 0) r = v; else if (settSlDrag == 1) g = v; else b = v;
        settColorField(settColSel).setValue(String.format("%02X%02X%02X", r, g, b));
    }

    /** RRGGBB 合法性（允许带#，1~6 位十六进制）；只作红线提示，非法值渲染层自会回退默认（m198 parseHex）。 */
    private static boolean hexOk(String s) {
        if (s == null) return false;
        String t = s.trim().replace("#", "");
        if (t.isEmpty() || t.length() > 6) return false;
        for (int i = 0; i < t.length(); i++) if (Character.digit(t.charAt(i), 16) < 0) return false;
        return true;
    }

    /** 设置面板渲染（照 renderRename：每帧摆位再渲染）。 */
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int px = settPos()[0], py = settPos()[1];
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 400); // m202 抬z：卡内物品图标画在带深度的高z，z0后画填充会被穿透（终端实锤同病）
        SciSkin.drawCard(ctx, px, py, SETT_W, SETT_H, SciSkin.FRAME);
        ctx.drawString(font, "画布设置", px + 8, py + 7, SciSkin.TXT_HI, false);
        String[] labels = {"缩放平滑动效", "连线宽度随缩放", "线宽封顶倍率", "出线颜色 RRGGBB", "进线颜色 RRGGBB", "跨组连线归并 ×N",
                "背景色(空=随主题)", "网格色(空=随主题)", "网格浓度", "暗角强度"}; // m217 背景四行
        boolean[] tog = {c.canvasSmoothZoom, c.canvasWireScaleWithZoom, false, false, false, c.canvasGroupBundleWires, false, false, false, false};
        for (int r = 0; r < 10; r++) {
            int ry = py + SETT_ROW0 + r * SETT_ROW; // m262 行区起点收口
            ctx.drawString(font, labels[r], px + 10, ry + 3, SciSkin.TXT, false);
            if (r == 0 || r == 1 || r == 5) { // 开关药丸（开=运行绿族，关=离线灰）
                boolean on = tog[r];
                int bx = px + SETT_W - 56;
                boolean hov = mouseX >= bx && mouseX <= bx + 46 && mouseY >= ry && mouseY <= ry + 14;
                ctx.fill(bx - 1, ry - 1, bx + 47, ry + 15, on ? SciSkin.ON : SciSkin.CELL_FRM);
                ctx.fill(bx, ry, bx + 46, ry + 14, on ? SciSkin.ON_DARK : (hov ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE));
                String tx = on ? "开" : "关";
                ctx.drawString(font, tx, bx + 23 - font.width(tx) / 2, ry + 3,
                        on ? SciSkin.ON : SciSkin.OFF_GRAY, false);
            } else if (r == 2 || r == 8 || r == 9) { // 步进器 [−] 值 [+]（照传感器阈值钮，屏幕坐标版）
                stBox(ctx, px + SETT_W - 80, ry, "−", mouseX, mouseY);
                stBox(ctx, px + SETT_W - 24, ry, "+", mouseX, mouseY);
                String v = r == 2 ? "×" + (Math.round(Math.max(0.2, c.canvasWireMaxScale) * 10) / 10.0) // double 拼串恒小数点，免 Locale 逗号坑
                         : r == 8 ? Math.round(Math.max(0, Math.min(3, c.canvasGridStrength)) * 100) + "%"
                                  : Math.round(Math.max(0, Math.min(2, c.canvasVignetteStrength)) * 100) + "%";
                ctx.drawString(font, v, px + SETT_W - 42 - font.width(v) / 2, ry + 3, SciSkin.TXT_HI, false);
            } else { // 颜色行：输入框 + 生效色样片（样片直读生效色=非法/空自动显回退色，红下划线只提示"非空且非法"——背景/网格行空=跟随主题属合法）
                int sel = settSelOfRow(r); // m223 点行/点样片可选中，下方滑杆调它
                EditBox f = settColorField(sel);
                f.setX(px + SETT_W - 96);
                f.setY(ry);
                f.render(ctx, mouseX, mouseY, delta);
                int sw = settColorVal(sel);
                ctx.fill(px + SETT_W - 25, ry - 1, px + SETT_W - 9, ry + 15,
                        sel == settColSel ? SciSkin.ACCENT : SciSkin.CELL_FRM); // m223 选中环（照 m202 终端选中环工艺）
                ctx.fill(px + SETT_W - 24, ry, px + SETT_W - 10, ry + 14, sw);
                boolean emptyOk = (r == 6 || r == 7) && f.getValue().isBlank();
                if (!emptyOk && !hexOk(f.getValue())) ctx.fill(px + SETT_W - 96, ry + 16, px + SETT_W - 38, ry + 17, SciSkin.RED);
            }
        }
        // ===== m223 RGB 滑杆调节区（照 m202 终端主题编辑器工艺；拖滑杆写十六进制串→listener→配置→即时预览）=====
        int sy0 = py + SETT_SL_Y;
        ctx.drawString(font, "调节: " + SETT_COLOR_NAMES[settColSel] + "（点色行切换）", px + 10, sy0, SciSkin.TXT, false);
        int cvSel = settColorVal(settColSel);
        String[] chn = {"R", "G", "B"};
        for (int ch = 0; ch < 3; ch++) {
            int v = (cvSel >> (16 - ch * 8)) & 0xFF;
            int sy = sy0 + 14 + ch * SETT_SL_SP; // m239 内距收口
            ctx.drawString(font, chn[ch], px + 14, sy + 1, SciSkin.SUB, false);
            ctx.fill(px + SETT_SL_X - 1, sy - 1, px + SETT_SL_X + SETT_SL_W + 1, sy + 11, SciSkin.CELL_FRM); // 轨
            ctx.fill(px + SETT_SL_X, sy, px + SETT_SL_X + SETT_SL_W, sy + 10, SciSkin.CELL);
            int kx = px + SETT_SL_X + (int) Math.round(v / 255.0 * (SETT_SL_W - 4));
            ctx.fill(px + SETT_SL_X, sy, kx + 2, sy + 10, SciSkin.withAlpha(SciSkin.ACCENT, 0.55f)); // 已填段
            ctx.fill(kx, sy - 1, kx + 4, sy + 11, settSlDrag == ch ? SciSkin.TXT_MAX : SciSkin.ACCENT); // 滑钮
            ctx.fill(kx + 1, sy, kx + 3, sy + 10, SciSkin.TXT_MAX);
            ctx.drawString(font, String.valueOf(v), px + SETT_SL_X + SETT_SL_W + 8, sy + 1, SciSkin.TXT_HI, false);
        }
        int ry6 = py + SETT_PRESET_Y; // m211 主题预设行：5 套一键换肤（m214 只写画布7键；m223 下移至常量位）
        ctx.drawString(font, "主题预设", px + 10, ry6 + 3, SciSkin.TXT, false);
        int hovPk = -1;
        for (int k = 0; k < SciSkin.TERM_PRESET_NAMES.length; k++) {
            int bx = px + SETT_W - 126 + k * 24;
            boolean hv = mouseX >= bx && mouseX <= bx + 20 && mouseY >= ry6 - 1 && mouseY <= ry6 + 15;
            if (hv) hovPk = k;
            ctx.fill(bx - 1, ry6 - 1, bx + 21, ry6 + 15, hv ? SciSkin.BTN_FRM_HOV : SciSkin.CELL_FRM);
            ctx.fill(bx, ry6, bx + 20, ry6 + 14, SciSkin.hex(SciSkin.TERM_PRESETS[k][0], SciSkin.termBase()));   // 底=预设主色（照终端样片工艺）
            ctx.fill(bx + 3, ry6 + 3, bx + 17, ry6 + 11, SciSkin.hex(SciSkin.TERM_PRESETS[k][2], SciSkin.termAccent())); // 心=预设强调
        }
        if (hovPk >= 0) ctx.drawString(font, SciSkin.TERM_PRESET_NAMES[hovPk], px + 62, ry6 + 3, SciSkin.TXT_HI, false);
        int rx = px + (SETT_W - 64) / 2, rby = py + SETT_RESET_Y; // 恢复默认（回本面板十项；m223 下移至常量位）
        boolean rh = mouseX >= rx && mouseX <= rx + 64 && mouseY >= rby && mouseY <= rby + 16;
        ctx.fill(rx - 1, rby - 1, rx + 65, rby + 17, rh ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(rx, rby, rx + 64, rby + 16, rh ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawString(font, "恢复默认", rx + 32 - font.width("恢复默认") / 2, rby + 4,
                rh ? SciSkin.TXT_MAX : SciSkin.TXT, false);
        String tip = "即改即存 · Esc/点外=关"; // m262 挪标题行右侧（原底行与恢复默认钮重叠且占高）
        ctx.drawString(font, tip, px + SETT_W - 8 - font.width(tip), py + 7, SciSkin.SUB, false);
        ctx.pose().popPose();
    }

    /** m199 步进小方钮（纯 fill 不依赖字形）。 */
    private void stBox(GuiGraphics ctx, int bx, int by, String s, int mouseX, int mouseY) {
        boolean hov = mouseX >= bx && mouseX <= bx + 14 && mouseY >= by && mouseY <= by + 14;
        ctx.fill(bx - 1, by - 1, bx + 15, by + 15, hov ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(bx, by, bx + 14, by + 14, hov ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawString(font, s, bx + 7 - font.width(s) / 2, by + 3, hov ? SciSkin.TXT_MAX : SciSkin.TXT, false);
    }

    /** m199 设置面板点击派发（几何与 renderSettings 同一套）。恒返回 true=modal 吞穿透（m103 教训）。 */
    public boolean click(double mouseX, double mouseY, int button) {
        int px = settPos()[0], py = settPos()[1];
        if (mouseX < px || mouseX > px + SETT_W || mouseY < py || mouseY > py + SETT_H) { close(); return true; } // 窗外点=关
        EditBox[] setFs = {wireOutField, wireInField, bgField, gridColField}; // m217 四框互斥聚焦（m202 非children输入框必须显式聚焦）
        for (int i = 0; i < setFs.length; i++)
            if (setFs[i].mouseClicked(mouseX, mouseY, button)) {
                for (EditBox o : setFs) o.setFocused(o == setFs[i]);
                settColSel = i; // m223 点进哪个色框，滑杆就调哪个（setFs 序=调节下标序）
                return true;
            }
        for (EditBox f : setFs) f.setFocused(false);
        if (button != 0) return true;
        com.sdzjz.config.SdzjzConfig c = com.sdzjz.config.SdzjzConfig.get();
        for (int r = 0; r < 10; r++) { // m217 6→10 行
            int ry = py + SETT_ROW0 + r * SETT_ROW; // m262 行区起点收口
            if (mouseY < ry || mouseY > ry + 14) continue;
            if ((r == 0 || r == 1 || r == 5) && mouseX >= px + SETT_W - 56 && mouseX <= px + SETT_W - 10) {
                if (r == 0) c.canvasSmoothZoom = !c.canvasSmoothZoom;
                else if (r == 1) c.canvasWireScaleWithZoom = !c.canvasWireScaleWithZoom;
                else c.canvasGroupBundleWires = !c.canvasGroupBundleWires;
                com.sdzjz.config.SdzjzConfig.save();
                return true;
            }
            if (r == 2 || r == 8 || r == 9) {
                int d = 0;
                if (mouseX >= px + SETT_W - 80 && mouseX <= px + SETT_W - 66) d = -1;
                else if (mouseX >= px + SETT_W - 24 && mouseX <= px + SETT_W - 10) d = 1;
                if (d != 0) { // 步进0.2，clamp 各行口径，×5/5 防浮点漂移
                    if (r == 2)      c.canvasWireMaxScale     = Math.round(Math.max(0.2, Math.min(8.0, c.canvasWireMaxScale     + d * 0.2)) * 5) / 5.0;
                    else if (r == 8) c.canvasGridStrength     = Math.round(Math.max(0.0, Math.min(3.0, c.canvasGridStrength     + d * 0.2)) * 5) / 5.0;
                    else             c.canvasVignetteStrength = Math.round(Math.max(0.0, Math.min(2.0, c.canvasVignetteStrength + d * 0.2)) * 5) / 5.0;
                    com.sdzjz.config.SdzjzConfig.save();
                    return true;
                }
            }
            int selR = settSelOfRow(r); // m223 点色行（标签区/样片区，输入框命中已在前面处理）=选中该色为调节目标
            if (selR >= 0 && (mouseX <= px + SETT_W - 98 || mouseX >= px + SETT_W - 26)) { settColSel = selR; return true; }
        }
        int sy0 = py + SETT_SL_Y + 14; // m223 滑杆命中（三轨，几何与 renderSettings 同一套）：点轨=起拖并立即写值
        if (mouseX >= px + SETT_SL_X - 2 && mouseX <= px + SETT_SL_X + SETT_SL_W + 2) {
            for (int ch = 0; ch < 3; ch++) {
                int sy = sy0 + ch * SETT_SL_SP; // m239 内距收口
                if (mouseY >= sy - 2 && mouseY <= sy + 12) { settSlDrag = ch; settApplySlider(mouseX); return true; }
            }
        }
        int ry6 = py + SETT_PRESET_Y; // m211 主题预设点击（几何与 renderSettings 同一套，改必同改；m223 常量位）
        if (mouseY >= ry6 - 1 && mouseY <= ry6 + 15) {
            for (int k = 0; k < SciSkin.TERM_PRESET_NAMES.length; k++) {
                int bx = px + SETT_W - 126 + k * 24;
                if (mouseX >= bx && mouseX <= bx + 20) { // m214 分家：这行只写画布 7 键，终端主题在数据面板里自己选
                    String[] pk = SciSkin.TERM_PRESETS[k];
                    c.canvasBase = pk[0]; c.canvasBaseDeep = pk[1]; c.canvasAccent = pk[2]; c.canvasAccentDeep = pk[3];
                    c.canvasInk = pk[4]; c.canvasFrame = pk[5]; c.canvasHi = pk[6];
                    com.sdzjz.config.SdzjzConfig.save();
                    return true;
                }
            }
        }
        int rx = px + (SETT_W - 64) / 2, rby = py + SETT_RESET_Y; // m223 常量位
        if (mouseX >= rx && mouseX <= rx + 64 && mouseY >= rby && mouseY <= rby + 16) { // 恢复默认：new 实例取字段默认，零硬编码重复
            com.sdzjz.config.SdzjzConfig d = new com.sdzjz.config.SdzjzConfig();
            c.canvasSmoothZoom = d.canvasSmoothZoom;
            c.canvasWireScaleWithZoom = d.canvasWireScaleWithZoom;
            c.canvasWireMaxScale = d.canvasWireMaxScale;
            c.canvasGroupBundleWires = d.canvasGroupBundleWires;
            wireOutField.setValue(d.canvasWireOutColor); // setText 触发 listener 回写配置串
            wireInField.setValue(d.canvasWireInColor);
            bgField.setValue(d.canvasBgColor);       // m217 背景四项一并回默认
            gridColField.setValue(d.canvasGridColor);
            c.canvasGridStrength = d.canvasGridStrength;
            c.canvasVignetteStrength = d.canvasVignetteStrength;
            com.sdzjz.config.SdzjzConfig.save();
        }
        return true;
    }

    /** m223 设置面板 RGB 滑杆拖动（宿主 mouseDragged 首句调：必须先于 modal 吞穿透）。返回 true=已处理。 */
    public boolean dragged(double mouseX) {
        if (settingsOpen && settSlDrag >= 0) { settApplySlider(mouseX); return true; }
        return false;
    }

    /** m223 设置面板 RGB 滑杆收拖（拖动中 setText 只写实例，落盘由 close 兜底——m199 颜色框同口径）。返回 true=已处理。 */
    public boolean released() {
        if (settSlDrag >= 0) { settSlDrag = -1; return true; }
        return false;
    }

    /** m199 设置窗：Esc=关；其余喂四个颜色框（未聚焦的 EditBox 自不吃）。窗关着返回 false 让宿主继续派发。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!settingsOpen) return false;
        if (keyCode == 256) { close(); return true; }
        wireOutField.keyPressed(keyCode, scanCode, modifiers);
        wireInField.keyPressed(keyCode, scanCode, modifiers);
        bgField.keyPressed(keyCode, scanCode, modifiers);      // m217
        gridColField.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    /** m199/m217 字符输入喂四框。窗关着返回 false 让宿主继续派发。 */
    public boolean charTyped(char chr, int modifiers) {
        if (!settingsOpen) return false;
        wireOutField.charTyped(chr, modifiers); wireInField.charTyped(chr, modifiers); bgField.charTyped(chr, modifiers); gridColField.charTyped(chr, modifiers);
        return true;
    }
}
