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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 存储终端：搜索 + 滚动 + 大数量显示（仿 Tom's Simple Storage）。 */
public class DataPanelScreen extends HandledScreen<DataPanelScreenHandler> {

    private static final int BACKDROP = 0xFF080B12;
    private static final int TXT      = 0xFFBFD2EC;
    private static final int SUB      = 0xFF7C90B0;
    private static final int CYAN     = 0xFF2EC4FF;
    private static final int CELL     = 0xFF0A1626;
    private static final int CELLFRM  = 0xFF163049;

    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/data_panel_gui.png");

    private TextFieldWidget search;
    private int scroll = 0;

    public DataPanelScreen(DataPanelScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 360;
        this.backgroundHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        this.search = new TextFieldWidget(this.textRenderer, this.x + 180, this.y + 8, 176, 14, Text.literal("搜索"));
        this.search.setPlaceholder(Text.literal("搜索物品(支持中文)…"));
        this.search.setChangedListener(s -> { scroll = 0; sendView(); });
        this.addDrawableChild(this.search);
    }

    private void sendView() {
        BlockPos p = this.handler.blockPos();
        if (p == null) return;
        String q = search == null ? "" : search.getText();
        ClientPlayNetworking.send(new DataPanelViewPayload(p, q, scroll, matchByLocalName(q)));
    }

    /** 用客户端本地化显示名匹配物品 id（支持中文搜索），上限 200 条防包过大。 */
    private static java.util.List<String> matchByLocalName(String q) {
        if (q == null || q.isBlank()) return java.util.List.of();
        String lower = q.toLowerCase();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (net.minecraft.item.Item item : net.minecraft.registry.Registries.ITEM) {
            String name = new net.minecraft.item.ItemStack(item).getName().getString().toLowerCase();
            if (name.contains(lower)) {
                out.add(net.minecraft.registry.Registries.ITEM.getId(item).toString());
                if (out.size() >= 200) break;
            }
        }
        return out;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        int x = this.x, y = this.y;
        ctx.drawTexture(BG, x, y, 0.0F, 0.0F, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        // 搜索框底
        ctx.fill(x + 178, y + 6, x + 358, y + 24, 0xFF0A1626);
        // 存储 6×9
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 9; c++) cell(ctx, x + 99 + c * 18, y + 30 + r * 18);
        // 滚动条轨
        int sbx = x + 99 + 9 * 18 + 3;
        ctx.fill(sbx, y + 30, sbx + 6, y + 30 + 6 * 18, 0xFF0A1626);
        ctx.fill(sbx, y + 30 + Math.min(5, scroll) * 6, sbx + 6, y + 30 + Math.min(5, scroll) * 6 + 24, CYAN);
        // 背包 3×9 + 快捷栏
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) cell(ctx, x + 99 + c * 18, y + 158 + r * 18);
        for (int c = 0; c < 9; c++) cell(ctx, x + 99 + c * 18, y + 216);
        // ===== m80c 左侧：乔大仙立牌 + 经验库 =====
        ctx.drawTexture(QDX, x + 10, y + 22, 0.0F, 0.0F, 78, 138, 78, 138);
        ctx.fill(x + 8, y + 166, x + 92, y + 240, 0xC0081120);
        ctx.drawText(this.textRenderer, "经验库", x + 14, y + 170, SUB, false);
        String xv = fmt(((com.sdzjz.screen.DataPanelScreenHandler) this.handler).xpBankView());
        ctx.drawText(this.textRenderer, xv + " 点", x + 14, y + 182, 0xFF7CFC9A, false);
        xpBtn(ctx, x + 12, y + 196, "存入经验", mouseX, mouseY);
        xpBtn(ctx, x + 12, y + 218, "取出经验", mouseX, mouseY);
        // ===== m84b 合成终端 3×3 + 结果 =====
        for (int r = 0; r < 3; r++)
            for (int c2 = 0; c2 < 3; c2++) cell(ctx, x + 272 + c2 * 18, y + 40 + r * 18);
        cell(ctx, x + 290, y + 102);
        ctx.fill(x + 294, y + 94, x + 302, y + 100, CYAN); // 网格→结果 指示
        // 回收格（红框，放入即销毁）
        int tx = x + 334, ty = y + 216;
        ctx.fill(tx - 1, ty - 1, tx + 17, ty + 17, 0xFF8E2E2E);
        ctx.fill(tx, ty, tx + 16, ty + 16, 0xFF1A0D0D);
    }

    private static final net.minecraft.util.Identifier QDX =
            net.minecraft.util.Identifier.of("sdzjz", "textures/gui/qdx_card.png");

    private void xpBtn(DrawContext ctx, int bx, int by, String label, int mouseX, int mouseY) {
        boolean hov = mouseX >= bx && mouseX <= bx + 76 && mouseY >= by && mouseY <= by + 18;
        ctx.fill(bx - 1, by - 1, bx + 77, by + 19, hov ? 0xFF3FA9D0 : 0xFF1E4258);
        ctx.fill(bx, by, bx + 76, by + 18, 0xFF0D1B2C);
        ctx.drawText(this.textRenderer, label, bx + (76 - this.textRenderer.getWidth(label)) / 2, by + 5,
                hov ? 0xFF9BE8FF : 0xFFB9D8E8, false);
    }

    private void cell(DrawContext ctx, int x, int y) {
        ctx.fill(x - 1, y - 1, x + 17, y + 17, CELLFRM);
        ctx.fill(x, y, x + 16, y + 16, CELL);
        ctx.fill(x, y, x + 4, y + 1, CYAN);           ctx.fill(x, y, x + 1, y + 4, CYAN);
        ctx.fill(x + 12, y, x + 16, y + 1, CYAN);     ctx.fill(x + 15, y, x + 16, y + 4, CYAN);
        ctx.fill(x, y + 15, x + 4, y + 16, CYAN);     ctx.fill(x, y + 12, x + 1, y + 16, CYAN);
        ctx.fill(x + 12, y + 15, x + 16, y + 16, CYAN); ctx.fill(x + 15, y + 12, x + 16, y + 16, CYAN);
    }

    private void header(DrawContext ctx, String s, int x, int y) {
        ctx.fill(x, y + 1, x + 2, y + 9, CYAN);
        ctx.drawText(this.textRenderer, s, x + 6, y, SUB, false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, "存储终端", 24, 12, TXT, false);
        header(ctx, "存储 · 滚轮翻页", 99, 20);
        // m97：全网类型用量。满了变红——存储核心类型上限(27×等级)到顶时新种类被拒收，
        // 表现就是"格子只填了几排再也进不去新东西"，这里把原因亮出来。
        int tu = this.handler.typesUsedView(), tc = this.handler.typesCapView();
        String usage; int ucol; // m98 哨兵：0=无存储核心, 0xFFFF=无限
        if (tc <= 0)            { usage = "无存储核心"; ucol = 0xFFE07070; }
        else if (tc == 0xFFFF)  { usage = "类型 " + tu; ucol = SUB; }
        else                    { usage = "类型 " + tu + "/" + tc + (tu >= tc ? " 满" : ""); ucol = tu >= tc ? 0xFFE07070 : SUB; }
        int uw = this.textRenderer.getWidth(usage);
        ctx.drawText(this.textRenderer, usage, 99 + 162 - uw, 20, ucol, false);
        header(ctx, "物品栏", 99, 148);
        header(ctx, "合成", 272, 28);
        ctx.drawText(this.textRenderer, "回收", 306, 220, 0xFFE07070, false);
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
            ctx.drawText(this.textRenderer, s, -this.textRenderer.getWidth(s), 0, 0xFFFFFFFF, true);
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
        if (button == 1) { // 右键展示格 → 打开数量选择
            for (int i = 0; i < DataPanelBlockEntity.PAGE && i < this.handler.slots.size(); i++) {
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
        if (button == 0) { // m80c：经验库按钮（1=存入 2=取出）
            double rx = mx - this.x, ry = my - this.y;
            if (rx >= 12 && rx <= 88 && ry >= 196 && ry <= 214) { clickXp(1); return true; }
            if (rx >= 12 && rx <= 88 && ry >= 218 && ry <= 236) { clickXp(2); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void clickXp(int id) {
        if (this.client != null && this.client.interactionManager != null)
            this.client.interactionManager.clickButton(this.handler.syncId, id);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v < 0) {
            boolean bottomFull = false;
            for (int i = 45; i < 54 && i < this.handler.slots.size(); i++)
                if (this.handler.slots.get(i).hasStack()) { bottomFull = true; break; }
            if (bottomFull) { scroll++; sendView(); }
        } else if (v > 0 && scroll > 0) {
            scroll--; sendView();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused() && keyCode != 256) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (search != null && search.isFocused()) {
            return search.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
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
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
        if (qtySlot >= 0) { // m82 数量选择浮层 + m100 批量行
            int w = QTY.length * 26 + 6;
            ctx.fill(qtyX - 4, qtyY - 16, qtyX + w, qtyY + 40, 0xF0081120);
            ctx.drawText(this.textRenderer, "取出数量:", qtyX, qtyY - 12, 0xFF8FB8CC, false);
            for (int k = 0; k < QTY.length; k++) { // 第一行：定量
                int bx = qtyX + k * 26, by = qtyY;
                boolean hov = mouseX >= bx && mouseX <= bx + 24 && mouseY >= by && mouseY <= by + 16;
                ctx.fill(bx - 1, by - 1, bx + 25, by + 17, hov ? 0xFF3FA9D0 : 0xFF1E4258);
                ctx.fill(bx, by, bx + 24, by + 16, 0xFF0D1B2C);
                String t = String.valueOf(QTY[k]);
                ctx.drawText(this.textRenderer, t, bx + (24 - this.textRenderer.getWidth(t)) / 2, by + 4,
                        hov ? 0xFF9BE8FF : 0xFFB9D8E8, false);
            }
            for (int j = 0; j < QTY2.length; j++) { // m100 第二行：批量(2组/4组/8组/填满背包)
                int bx = qtyX + j * 32, by = qtyY + 20;
                boolean hov = mouseX >= bx && mouseX <= bx + 30 && mouseY >= by && mouseY <= by + 16;
                ctx.fill(bx - 1, by - 1, bx + 31, by + 17, hov ? 0xFF3FA9D0 : 0xFF1E4258);
                ctx.fill(bx, by, bx + 30, by + 16, 0xFF0D1B2C);
                String t = QTY2[j];
                ctx.drawText(this.textRenderer, t, bx + (30 - this.textRenderer.getWidth(t)) / 2, by + 4,
                        hov ? 0xFF9BE8FF : 0xFFB9D8E8, false);
            }
        }
    }
}
