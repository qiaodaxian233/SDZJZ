package com.sdzjz.retro;

import com.sdzjz.client.SciSkin;
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
        search.setTextColor(SciSkin.TXT);
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
            g.drawString(font, Component.translatable("sdzjz.panel.search_hint"), search.getX(), search.getY() + 1, SciSkin.SUB, false);
        renderTooltip(g, mouseX, mouseY); // 背包槽悬停
        PanelPayloads120.Row row = rowAt(mouseX, mouseY); // 网格悬停详情
        if (row != null) {
            Component line2 = Component.literal("× " + String.format("%,d", row.n()))
                    .append(row.exact() ? Component.literal(" · ").append(Component.translatable("sdzjz.panel.exact")) : Component.empty());
            g.renderTooltip(font, List.of(row.display().getHoverName(), line2), Optional.empty(), mouseX, mouseY);
        }
    }

    /** 主线四屏形制（SciSkin=唯一色源）：BACKDROP 全不透明底 + FRAME 外框 + 标题栏条 + 槽框。 */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, SciSkin.FRAME);    // 外框 1px
        g.fill(x0, y0, x1, y1, SciSkin.BACKDROP);                  // 全不透明底（m452 透明观感主修）
        g.fill(x0, y0, x1, y0 + 16, SciSkin.BTN_FACE);             // 标题栏条
        g.fill(x0, y0 + 16, x1, y0 + 17, SciSkin.FRAME);           // 标题栏底缘线
        g.fill(x0 + 96, y0 + 2, x0 + 187, y0 + 14, SciSkin.CELL);  // 搜索区底
        g.fill(x0 + 96, y0 + 2, x0 + 187, y0 + 3, SciSkin.CELL_FRM); // 搜索区细边（上/下两线足够，主线搜索框 m161b 去黑壳同风）
        g.fill(x0 + 96, y0 + 13, x0 + 187, y0 + 14, search != null && search.isFocused() ? SciSkin.ACCENT : SciSkin.CELL_FRM);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int x = x0 + GRID_X + c * CELL_PX, y = y0 + GRID_Y + r * CELL_PX;
                boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                cell(g, x, y, hover);
                int idx = r * COLS + c;
                if (idx < data.rows().size()) {
                    PanelPayloads120.Row rw = data.rows().get(idx);
                    g.renderItem(rw.display(), x + 1, y + 1);
                    g.renderItemDecorations(font, rw.display(), x + 1, y + 1, shortCount(rw.n()));
                }
            }
        for (net.minecraft.world.inventory.Slot slot : menu.slots) // 背包槽同款槽框（槽内物品 super 画）
            cell(g, x0 + slot.x - 1, y0 + slot.y - 1, false);
        if (data.totalRows() > ROWS) { // 滚动条：轨道 CELL、滑块 FRAME
            int trackX = x1 - 6, trackY0 = y0 + GRID_Y, track = ROWS * CELL_PX;
            g.fill(trackX, trackY0, trackX + 3, trackY0 + track, SciSkin.CELL);
            int thumb = Math.max(8, track * ROWS / data.totalRows());
            int off = (track - thumb) * scrollRow / Math.max(1, data.totalRows() - ROWS);
            g.fill(trackX, trackY0 + off, trackX + 3, trackY0 + off + thumb, SciSkin.FRAME);
        }
    }

    /** 18px 槽框：CELL_FRM 边 + CELL 底，悬停=ACCENT 边 + HOVER 底（主线格子形制）。 */
    private static void cell(GuiGraphics g, int x, int y, boolean hover) {
        g.fill(x, y, x + 18, y + 18, hover ? SciSkin.ACCENT : SciSkin.CELL_FRM);
        g.fill(x + 1, y + 1, x + 17, y + 17, hover ? SciSkin.HOVER : SciSkin.CELL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 5, SciSkin.TXT_HI, false); // 标题进标题栏条
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SciSkin.SUB, false);
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
