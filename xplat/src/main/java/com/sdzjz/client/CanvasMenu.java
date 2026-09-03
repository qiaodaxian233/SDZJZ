package com.sdzjz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * m509（真移植·A5c 补刀）：**画布右键菜单机制两代共用一份**——主线 {@code StructureCoreScreen} 的
 * 右键菜单机制（状态十一件 / openMenu / clearMenu / addMenu 四重载 / mt 贴图路径 / m148 3A 化 renderMenu /
 * 点选分派块）整段搬。1.20.1 A5c（m508）为组菜单/「打组所选」先写了一份"最小菜单件"（列表形状照主线、工艺随 A7），
 * 本刀把机制整段搬成共用件后那份即退役——照 m484/m485 家法：有原版的一律整段搬、仿写件直接删，不留第二份要维护的菜单。
 *
 * <p><b>世代差</b>：只有 {@code mt()} 里的 {@code ResourceLocation.fromNamespaceAndPath}（1.20.5+ 静态工厂），
 * 走 {@link SciSkin#gfxTex}（Gfx 世代口）。其余 {@code ctx.fill}/{@code blit(十参)}/{@code renderItem}/
 * {@code Util.getMillis}/{@code SimpleSoundInstance.forUI(Holder,float)} 两代同名同签名（后者 yarn 1.20.1
 * {@code PositionedSoundInstance.master(RegistryEntry,float)} 核过在位）。
 *
 * <p>机械替换只四类：①{@code workRight()}/{@code this.height} → openMenu 形参（屏尺寸是宿主的事）
 * ②{@code this.font} → render 形参 ③屏内别名 {@code NODEFRM/CYAN/TXT} → 它们的定义 {@code SciSkin.FRAME/ACCENT/TXT}
 * ④{@code mt} 走 Gfx 口。其余一个字未改（含 m148/m207/m313/m316 注释刀号）。
 * 菜单条目由调用方按自己的需要装配（主线节点菜单/画布菜单/组菜单/存储连线菜单，1.20.1 现有组菜单与画布菜单，节点菜单条目随 A7）。
 */
public final class CanvasMenu {

    // 右键菜单
    private boolean menuOpen = false;
    private int menuX, menuY;
    private final List<String> menuLabels = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();
    private final List<ItemStack> menuIcons = new ArrayList<>();   // m148 行图标（可空）
    private final List<net.minecraft.resources.ResourceLocation> menuTexs = new ArrayList<>(); // m313 用户设计贴图图标（可空，优先于物品图标）
    private final List<Integer> menuStyles = new ArrayList<>();    // m148 0普通 1危险(红) 2组首(上加分隔线)
    private String menuTitle = null;                               // m148 标题带（节点菜单=机器名）
    private long menuOpenMs = 0;                                   // m148 开合动画时钟
    private float[] menuHoverP = new float[0];                     // m148 逐行悬停缓动进度
    public static final int MENU_W = 144, MENU_H = 18, MENU_TITLE_H = 14; // m148 加宽容图标+标题带；m316 136→144 容图标间距加宽后的最长行（抽取量挡位）

    /** 菜单开着？（主线原 {@code menuOpen} 字段的读口：滚轮/拖动/键盘各 modal 判定要看）。 */
    public boolean isOpen() { return menuOpen; }

    /** 一条都没装？（主线原 {@code menuLabels.isEmpty()}：悬停详情只在无菜单时画）。 */
    public boolean isEmpty() { return menuLabels.isEmpty(); }

    /** 标题带（主线原 {@code menuTitle = ...} 直写；节点菜单=机器名，组菜单=组名，画布菜单="画布"）。 */
    public void title(String t) { menuTitle = t; }

    // ================= 右键菜单 =================
    /** @param rightLimit 菜单右缘不越过的屏幕 x（主线 workRight()）；@param height 屏高（主线 this.height）。 */
    public void openMenu(int x, int y, int rightLimit, int height) {
        menuOpen = true;
        menuOpenMs = net.minecraft.Util.getMillis(); // m148 开合动画从零起
        menuHoverP = new float[menuLabels.size()];
        int th = menuTitle != null ? MENU_TITLE_H : 0;
        menuX = Math.min(x, rightLimit - MENU_W - 4);
        menuY = Math.min(y, height - (menuLabels.size() * MENU_H + th) - 4);
    }

    public void clearMenu() {
        menuLabels.clear();
        menuActions.clear();
        menuIcons.clear();
        menuTexs.clear(); // m313
        menuStyles.clear();
        menuTitle = null;
        menuOpen = false;
    }

    public void addMenu(String label, Runnable action) { addMenu(label, (ItemStack) null, 0, action); }

    public void addMenu(String label, ItemStack icon, Runnable action) { addMenu(label, icon, 0, action); }

    /** m148：style 0=普通 1=危险(红字红条) 2=组首(上方分隔线)。 */
    public void addMenu(String label, ItemStack icon, int style, Runnable action) {
        menuLabels.add(label);
        menuActions.add(action);
        menuIcons.add(icon);
        menuTexs.add(null);
        menuStyles.add(style);
    }

    /** m313：贴图图标行（作者设计的 8 张按钮图，32² 源画 16×16）。 */
    public void addMenu(String label, net.minecraft.resources.ResourceLocation tex, int style, Runnable action) {
        menuLabels.add(label);
        menuActions.add(action);
        menuIcons.add(null);
        menuTexs.add(tex);
        menuStyles.add(style);
    }

    public static net.minecraft.resources.ResourceLocation mt(String name) { // m313 菜单贴图路径唯一口径
        return SciSkin.gfxTex("textures/gui/menu/" + name + ".png");
    }

    /** m148 菜单 3A 化：110ms 弹入（缩放+淡入 easeOutCubic）、逐行悬停指数缓动（底色渐显+
     *  强调条滑出+文字右移 2px+颜色插值）、标题带（机器名+青下划线）、组分隔线、行图标（0.8×物品）、
     *  危险项红系。全程 GuiGraphics 原语，零贴图零 shader。 */
    public void renderMenu(GuiGraphics ctx, Font font, int mouseX, int mouseY) {
        int th = menuTitle != null ? MENU_TITLE_H : 0;
        int h = menuLabels.size() * MENU_H + th;
        float ease = SciSkin.easeOut((net.minecraft.Util.getMillis() - menuOpenMs) / 110f);
        if (menuHoverP.length != menuLabels.size()) menuHoverP = new float[menuLabels.size()];
        ctx.pose().pushPose(); // 弹入：以菜单左上角为锚 0.92→1.0
        // m316 整体抬 z=400（m202/m283 同病同刀）：画布节点的物品图标/升级角标由 drawItem 画在
        // z100~200 带深度测试，菜单 z0 平面填充画得再晚也被穿透（作者截图实锤：节点端口图标叠在
        // 菜单标题带上）。重命名/设置/帮助/选择器四浮层早已各自抬 400，唯菜单漏网，本笔补齐。
        ctx.pose().translate(0, 0, 400);
        ctx.pose().translate(menuX, menuY, 0);
        float sc = 0.92f + 0.08f * ease;
        ctx.pose().scale(sc, sc, 1f);
        ctx.pose().translate(-menuX, -menuY, 0);
        ctx.fill(menuX + 3, menuY + 4, menuX + MENU_W + 3, menuY + h + 4, SciSkin.withAlpha(0x66000000, ease));
        ctx.fill(menuX - 1, menuY - 1, menuX + MENU_W + 1, menuY + h + 1, SciSkin.withAlpha(SciSkin.FRAME, ease));
        ctx.fill(menuX, menuY, menuX + MENU_W, menuY + h, SciSkin.withAlpha(SciSkin.withAlpha8(SciSkin.CELL, 0xF0), ease)); // m207 归队
        if (th > 0) {
            ctx.fill(menuX, menuY, menuX + MENU_W, menuY + th, SciSkin.withAlpha(0xF00E2438, ease));
            ctx.fill(menuX, menuY + th - 1, menuX + MENU_W, menuY + th, SciSkin.withAlpha(SciSkin.ACCENT, ease * 0.9f));
            String tt = menuTitle;
            while (tt.length() > 1 && font.width(tt) > MENU_W - 12) tt = tt.substring(0, tt.length() - 1);
            ctx.drawString(font, tt, menuX + 6, menuY + 3,
                    SciSkin.withAlpha(SciSkin.TXT_HI, Math.max(0.3f, ease)), false);
        }
        int tick = SciSkin.lighten(SciSkin.FRAME); // 四角刻与卡片语言呼应
        ctx.fill(menuX, menuY, menuX + 4, menuY + 1, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX, menuY, menuX + 1, menuY + 4, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 4, menuY, menuX + MENU_W, menuY + 1, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 1, menuY, menuX + MENU_W, menuY + 4, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX, menuY + h - 1, menuX + 4, menuY + h, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX, menuY + h - 4, menuX + 1, menuY + h, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 4, menuY + h - 1, menuX + MENU_W, menuY + h, SciSkin.withAlpha(tick, ease));
        ctx.fill(menuX + MENU_W - 1, menuY + h - 4, menuX + MENU_W, menuY + h, SciSkin.withAlpha(tick, ease));
        for (int i = 0; i < menuLabels.size(); i++) {
            int y0 = menuY + th + i * MENU_H;
            boolean hov = mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= y0 && mouseY < y0 + MENU_H;
            menuHoverP[i] += ((hov ? 1f : 0f) - menuHoverP[i]) * 0.35f; // 指数趋近（~60fps 手感）
            float pv = menuHoverP[i];
            boolean danger = menuStyles.get(i) == 1;
            if (menuStyles.get(i) == 2)
                ctx.fill(menuX + 6, y0, menuX + MENU_W - 6, y0 + 1, SciSkin.withAlpha(SciSkin.withAlpha8(SciSkin.ACCENT, 0x55), ease)); // m207 归队
            if (pv > 0.02f)
                ctx.fill(menuX, y0 + 1, menuX + MENU_W, y0 + MENU_H - 1,
                        SciSkin.withAlpha(danger ? 0xFF3A1420 : SciSkin.HOVER, ease * pv));
            int barW = Math.round(3 * pv);
            if (barW > 0)
                ctx.fill(menuX, y0 + 1, menuX + barW, y0 + MENU_H - 1,
                        SciSkin.withAlpha(danger ? SciSkin.RED : SciSkin.ACCENT, ease));
            int tx = menuX + 6 + Math.round(2 * pv);
            net.minecraft.resources.ResourceLocation tex = menuTexs.get(i); // m313 贴图图标优先
            if (tex != null) {
                ctx.blit(tex, tx, y0 + 2, 16, 16, 0f, 0f, 32, 32, 32, 32);
                tx += 20; // m316：贴图满幅 16px，原 +16 零间隙文字贴脸（作者截图点名），补 4px 呼吸位
            }
            ItemStack ic = menuIcons.get(i);
            if (ic != null && !ic.isEmpty()) {
                ctx.pose().pushPose();
                ctx.pose().translate(tx, y0 + 2, 0);
                ctx.pose().scale(0.8f, 0.8f, 1f);
                ctx.renderItem(ic, 0, 0);
                ctx.pose().popPose();
                tx += 18; // m316：0.8×实占 12.8px，原 +16 只剩 3px，与贴图行对齐补到约 5px
            }
            int col = danger ? SciSkin.mix(SciSkin.RED_SOFT, SciSkin.RED, pv) : SciSkin.mix(SciSkin.TXT, SciSkin.TXT_MAX, pv);
            ctx.drawString(font, menuLabels.get(i), tx, y0 + 5,
                    SciSkin.withAlpha(col, Math.max(0.3f, ease)), false);
        }
        ctx.pose().popPose();
    }

    /** 菜单开着时的点选分派（主线 mouseClicked 里 {@code if (menuOpen) {...}} 块原文）：左键点行=清菜单后执行该行
     *  （点选音原版按钮同款）；点在菜单外（或非左键）=只关菜单。调用方只在 {@link #isOpen()} 时调，调完直接 return true。 */
    public void click(double mouseX, double mouseY, int button) {
        int thM = menuTitle != null ? MENU_TITLE_H : 0; // m148 标题带占位
        int h = menuLabels.size() * MENU_H + thM;
        if (button == 0 && mouseX >= menuX && mouseX < menuX + MENU_W && mouseY >= menuY + thM && mouseY < menuY + h) {
            int idx = (int) ((mouseY - menuY - thM) / MENU_H);
            Runnable act = idx >= 0 && idx < menuActions.size() ? menuActions.get(idx) : null;
            clearMenu();
            if (act != null) {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play( // m148 点选音（原版按钮同款）
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                act.run();
            }
            return;
        }
        clearMenu();
    }
}
