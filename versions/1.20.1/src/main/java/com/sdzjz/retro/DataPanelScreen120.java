package com.sdzjz.retro;

import com.sdzjz.client.SciSkinPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * m448 落地 / m452 换肤重绘（作者实机三连反馈之二三：界面透+丑）：①全部填充改**全不透明**并
 * 加双层边框——原 0xF0 半透明底叠在世界上=作者"游戏变透明了"观感主嫌疑；②配色全量切
 * {@link SciSkin}（白名单挂载后 m117"换肤只改它"铁律在本世代归位，屏内色区废除，本屏禁写色值）；
 * ③槽框/标题栏/搜索区/滚动条按主线四屏形制重绘（靛紫系 m207）。
 *
 * <p>1.20.1 客户端签名差行内指认（m448 原注保留）：EditBox 手动 tick()（1.20.2 起移除）；
 * mouseScrolled 三参（1.20.2 起四参）；renderBackground 单参（1.20.2 起带坐标）。
 */
public final class DataPanelScreen120 extends AbstractContainerScreen<DataPanel120.PanelMenu120> {

    private static final int COLS = 9, ROWS = 6, CELL_PX = 18;
    private static final int GRID_X = 8, GRID_Y = 22; // 相对 leftPos/topPos（标题栏 16 + 间距）

    private EditBox search;
    private PanelPayloads120.Rows data = new PanelPayloads120.Rows(0, 0, List.of());
    private int scrollRow = 0;
    private int refreshTicker = 0;

    public DataPanelScreen120(DataPanel120.PanelMenu120 menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 195;
        this.imageHeight = 222;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 129; // 背包槽 y=140（m447 菜单坐标），标签压其上一行
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 98, topPos + 4, 87, 10, Component.translatable("sdzjz.panel.search_hint"));
        search.setMaxLength(PanelPayloads120.Query.MAX_QUERY);
        search.setBordered(false); // 框自绘贴皮肤（renderBg 搜索区）
        search.setTextColor(SciSkinPalette.TXT);
        search.setResponder(text -> { scrollRow = 0; sendQuery(); });
        addRenderableWidget(search);
        sendQuery(); // 开屏首查
    }

    /** Rows 到货（render 线程直达，ClientNet120 类注）：滚动行以服务端钳位值为准。 */
    void acceptRows(PanelPayloads120.Rows rows) {
        this.data = rows;
        this.scrollRow = rows.scrollRow();
    }

    private void sendQuery() {
        ClientNet120.toServer(new PanelPayloads120.Query(menu.panelPos,
                search == null ? "" : search.getValue(), scrollRow));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (search != null) search.tick(); // 1.20.1 光标闪烁需手动 tick（1.20.2 起移除）
        if (++refreshTicker >= 20) { refreshTicker = 0; sendQuery(); } // 线缆在搬货，账面每秒自刷
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused() && keyCode != 256) { // 256=ESC 仍走关屏
            if (search.keyPressed(keyCode, scanCode, modifiers)) return true;
            return search.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers); // 焦点期吞背包键防误关
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { // 1.20.1 三参（1.20.2 起四参）
        if (overGrid(mouseX, mouseY)) { // 滚轮区域化（m103/m107 教训：悬停哪响应哪，防穿透）
            int maxStart = Math.max(0, data.totalRows() - ROWS);
            int next = Math.max(0, Math.min(scrollRow - (int) Math.signum(delta), maxStart));
            if (next != scrollRow) { scrollRow = next; sendQuery(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PanelPayloads120.Row row = rowAt(mouseX, mouseY);
        if (row != null && (button == 0 || button == 1)) {
            boolean exact = row.exact();
            int amount = button == 1 ? 1 : row.display().getMaxStackSize(); // 左键一组/右键一件
            String id = exact ? "" : BuiltInRegistries.ITEM.getKey(row.display().getItem()).toString();
            ItemStack template = exact ? row.display() : ItemStack.EMPTY;
            ClientNet120.toServer(new PanelPayloads120.Take(menu.panelPos, exact, id, template, amount));
            sendQuery(); // Take 回发的是无查询首页帧，紧随的本查询帧按序后到=终显一致（m447 口径）
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); // 1.20.1 单参（1.20.2 起带坐标）
        super.render(g, mouseX, mouseY, partialTick);
        if (search != null && search.getValue().isEmpty() && !search.isFocused())
            g.drawString(font, Component.translatable("sdzjz.panel.search_hint"), search.getX(), search.getY() + 1, SciSkinPalette.SUB, false);
        renderTooltip(g, mouseX, mouseY); // 背包槽悬停
        PanelPayloads120.Row row = rowAt(mouseX, mouseY); // 网格悬停详情
        if (row != null) {
            Component line2 = Component.literal("× " + String.format("%,d", row.n()))
                    .append(row.exact() ? Component.literal(" · ").append(Component.translatable("sdzjz.panel.exact")) : Component.empty());
            g.renderTooltip(font, List.of(row.display().getHoverName(), line2), Optional.empty(), mouseX, mouseY);
        }
    }

    /** m489（真移植）：面板工艺换成主线同一份（SciSkin.termPanel/termSlot/termBtn，
     *  照 xplat DataPanelScreen.renderBg 的形制搬——全屏暗底+窗体大卡+标题紫刻+分隔细线+
     *  分区卡片+聚焦紫描边+真实比例滚动条）。本世代菜单只有背包 36 槽（无经验库/合成终端/回收），
     *  那三块不搬——**搬工艺不搬本世代没有的功能区**。 */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        com.sdzjz.client.SciSkin.scopeCanvas(false); // 终端配色域（与主线 termXxx 同源）
        g.fill(0, 0, this.width, this.height, com.sdzjz.client.SciSkin.termInk()); // 全屏暗底（设计稿背景色）
        com.sdzjz.client.SciSkin.termPanel(g, x, y, imageWidth, imageHeight);      // 窗体大卡
        g.fill(x + 2, y + 20, x + imageWidth - 2, y + 21,
                com.sdzjz.client.SciSkin.withAlpha(com.sdzjz.client.SciSkin.termFrame(), 0.6f)); // 分隔细线
        g.fill(x + 7, y + 5, x + 19, y + 17, com.sdzjz.client.SciSkin.termAccentDeep());        // 图标：紫方块+受光角
        g.fill(x + 8, y + 6, x + 18, y + 16, com.sdzjz.client.SciSkin.termAccent());
        g.fill(x + 8, y + 6, x + 12, y + 10, com.sdzjz.client.SciSkin.withAlpha(com.sdzjz.client.SciSkin.termHi(), 0.55f));

        // ===== 搜索卡：m489 挪到网格上方独占一行（原来钉在标题栏右上角，与 IPN/REI 这类
        // 整理模组默认放按钮的位置抢地盘，作者实机截到过按钮压在搜索框上）=====
        com.sdzjz.client.SciSkin.termPanel(g, x + 4, y + 24, imageWidth - 8, 20);
        if (search != null && search.getValue().isEmpty())
            g.drawString(font, net.minecraft.network.chat.Component.translatable("sdzjz.panel.search_hint"),
                    x + 12, y + 30, com.sdzjz.client.SciSkin.termSub(), false);
        if (search != null && search.isFocused()) { // 聚焦=紫描边（四边 1px，主线同款）
            int a = com.sdzjz.client.SciSkin.termAccent();
            g.fill(x + 5, y + 25, x + imageWidth - 5, y + 26, a);
            g.fill(x + 5, y + 42, x + imageWidth - 5, y + 43, a);
            g.fill(x + 5, y + 25, x + 6, y + 43, a);
            g.fill(x + imageWidth - 6, y + 25, x + imageWidth - 5, y + 43, a);
        }

        // ===== 存储网格卡 + 真实比例滚动条（主线 m107b 同款）=====
        com.sdzjz.client.SciSkin.termPanel(g, x + 4, y + GRID_Y - 6, imageWidth - 8, ROWS * CELL_PX + 12);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int sx = x + GRID_X + c * CELL_PX, sy = y + GRID_Y + r * CELL_PX;
                com.sdzjz.client.SciSkin.termSlot(g, sx + 1, sy + 1); // 主线槽（18×18 贴图，传物品区左上角）
                int idx = r * COLS + c;
                if (idx < data.rows().size()) {
                    PanelPayloads120.Row rw = data.rows().get(idx);
                    g.renderItem(rw.display(), sx + 1, sy + 1);
                    g.renderItemDecorations(font, rw.display(), sx + 1, sy + 1, shortCount(rw.n()));
                }
            }
        if (data.totalRows() > ROWS) {
            int sbx = x + imageWidth - 11, track = ROWS * CELL_PX;
            g.fill(sbx, y + GRID_Y, sbx + 6, y + GRID_Y + track, com.sdzjz.client.SciSkin.termBaseDeep());
            g.fill(sbx, y + GRID_Y, sbx + 6, y + GRID_Y + 1,
                    com.sdzjz.client.SciSkin.withAlpha(com.sdzjz.client.SciSkin.termInk(), 0.28f)); // 轨内顶阴影
            int thumb = Math.max(12, track * ROWS / data.totalRows());
            int off = (track - thumb) * scrollRow / Math.max(1, data.totalRows() - ROWS);
            g.fill(sbx, y + GRID_Y + off, sbx + 6, y + GRID_Y + off + thumb, com.sdzjz.client.SciSkin.termAccent());
        }

        // ===== 背包卡（本世代只有这一块，经验库/合成终端/回收随各族到序）=====
        int invTop = y + inventoryLabelY - 6;
        com.sdzjz.client.SciSkin.termPanel(g, x + 4, invTop, imageWidth - 8, imageHeight - (invTop - y) - 4);
        for (net.minecraft.world.inventory.Slot slot : menu.slots)
            com.sdzjz.client.SciSkin.termSlot(g, x + slot.x, y + slot.y);
    }

    /** 18px 槽框：CELL_FRM 边 + CELL 底，悬停=ACCENT 边 + HOVER 底（主线格子形制）。 */
    private static void cell(GuiGraphics g, int x, int y, boolean hover) {
        g.fill(x, y, x + 18, y + 18, hover ? SciSkinPalette.ACCENT : SciSkinPalette.CELL_FRM);
        g.fill(x + 1, y + 1, x + 17, y + 17, hover ? SciSkinPalette.HOVER : SciSkinPalette.CELL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 24, 7, com.sdzjz.client.SciSkin.termInk(), false); // m489 主线同位（图标右侧）
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, com.sdzjz.client.SciSkin.termSub(), false);
    }

    private boolean overGrid(double mx, double my) {
        return mx >= leftPos + GRID_X && mx < leftPos + GRID_X + COLS * CELL_PX
                && my >= topPos + GRID_Y && my < topPos + GRID_Y + ROWS * CELL_PX;
    }

    private PanelPayloads120.Row rowAt(double mx, double my) {
        if (!overGrid(mx, my)) return null;
        int c = (int) ((mx - leftPos - GRID_X) / CELL_PX), r = (int) ((my - topPos - GRID_Y) / CELL_PX);
        int idx = r * COLS + c;
        return idx >= 0 && idx < data.rows().size() ? data.rows().get(idx) : null;
    }

    /** 账面数短格式（格内 16px 装不下长数字；完整数在悬停详情）。 */
    private static String shortCount(long n) {
        if (n < 1_000L) return Long.toString(n);
        if (n < 1_000_000L) return (n / 100L) / 10.0 + "K";
        if (n < 1_000_000_000L) return (n / 100_000L) / 10.0 + "M";
        return (n / 100_000_000L) / 10.0 + "G";
    }
}
