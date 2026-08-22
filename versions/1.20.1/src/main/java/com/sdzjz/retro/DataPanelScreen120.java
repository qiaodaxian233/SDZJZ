package com.sdzjz.retro;

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
 * m448（P-C1 收官）：数据面板精简屏——纯填充绘制零贴图（SciSkin 换肤属 P-C2，本世代不引入配色
 * 体系故屏内常量即全部颜色，集中在下方色区一处不散写）。9×6 网格显示服务端窗（m447 三包协议），
 * 搜索按物品 id 串（协议限定，搜索框提示语注明）；左键取一组/右键取一件；每 20t 自动重查刷账。
 *
 * <p>1.20.1 客户端签名差行内指认：EditBox 需手动 tick()（1.20.2 起移除）；mouseScrolled 三参
 * （1.20.2 起四参）；renderBackground 单参（1.20.2 起带坐标）。族名 m447b 教训后经 Forge 1.20.x
 * 补丁原文实证（renderLabels/renderTooltip/leftPos/topPos/imageWidth）。
 */
public final class DataPanelScreen120 extends AbstractContainerScreen<DataPanel120.PanelMenu120> {

    // ===== 色区（唯一配色出口，屏内其余处禁写色值——SciSkin 精神的本地版）=====
    private static final int BG = 0xF0101418;      // 面板底
    private static final int CELL = 0xFF1E2630;    // 格底
    private static final int CELL_HOVER = 0xFF2C3A48; // 悬停格
    private static final int TEXT = 0xFFE0E6F0;    // 主文本
    private static final int HINT = 0xFF707882;    // 提示文本

    private static final int COLS = 9, ROWS = 6, CELL_PX = 18;
    private static final int GRID_X = 8, GRID_Y = 20; // 相对 leftPos/topPos

    private EditBox search;
    private PanelPayloads120.Rows data = new PanelPayloads120.Rows(0, 0, List.of());
    private int scrollRow = 0;
    private int refreshTicker = 0;

    public DataPanelScreen120(DataPanel120.PanelMenu120 menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 195;
        this.imageHeight = 222;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 129; // 背包槽 y=140（m447 菜单坐标），标签压在其上一行
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 96, topPos + 5, 91, 12, Component.translatable("sdzjz.panel.search_hint"));
        search.setMaxLength(PanelPayloads120.Query.MAX_QUERY);
        search.setBordered(true);
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
            return search.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers); // 焦点期吞掉背包键防误关
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
            sendQuery(); // Take 回发的是无查询首页帧，紧随的本查询帧按序后到=终显与本地视图一致（m447 口径）
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); // 1.20.1 单参（1.20.2 起带坐标）
        super.render(g, mouseX, mouseY, partialTick);
        if (search != null && search.getValue().isEmpty() && !search.isFocused())
            g.drawString(font, Component.translatable("sdzjz.panel.search_hint"), search.getX() + 4, search.getY() + 2, HINT, false);
        renderTooltip(g, mouseX, mouseY); // 背包槽悬停
        PanelPayloads120.Row row = rowAt(mouseX, mouseY); // 网格悬停详情
        if (row != null) {
            Component line2 = Component.literal("× " + String.format("%,d", row.n()))
                    .append(row.exact() ? Component.literal(" · ").append(Component.translatable("sdzjz.panel.exact")) : Component.empty());
            g.renderTooltip(font, List.of(row.display().getHoverName(), line2), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int x = leftPos + GRID_X + c * CELL_PX, y = topPos + GRID_Y + r * CELL_PX;
                boolean hover = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
                g.fill(x, y, x + 16, y + 16, hover ? CELL_HOVER : CELL);
                int idx = r * COLS + c;
                if (idx < data.rows().size()) {
                    PanelPayloads120.Row row = data.rows().get(idx);
                    g.renderItem(row.display(), x, y);
                    g.renderItemDecorations(font, row.display(), x, y, shortCount(row.n()));
                }
            }
        for (net.minecraft.world.inventory.Slot slot : menu.slots) // 背包槽底（槽内物品 super 画）
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, CELL);
        if (data.totalRows() > ROWS) { // 极简滚动指示：右缘按比例亮条
            int track = ROWS * CELL_PX, thumb = Math.max(8, track * ROWS / data.totalRows());
            int off = (track - thumb) * scrollRow / Math.max(1, data.totalRows() - ROWS);
            g.fill(leftPos + imageWidth - 5, topPos + GRID_Y + off, leftPos + imageWidth - 3, topPos + GRID_Y + off + thumb, TEXT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false); // 原版默认深灰在暗底不可读，覆写取色区
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
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
