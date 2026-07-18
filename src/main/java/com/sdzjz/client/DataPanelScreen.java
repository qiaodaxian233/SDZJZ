package com.sdzjz.client;

import com.sdzjz.net.DataPanelViewPayload;
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

    private static final Identifier BG = Identifier.of("sdzjz", "textures/gui/structure_core_gui.png");

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
        header(ctx, "物品栏", 99, 148);
    }

    @Override
    protected void drawSlot(DrawContext ctx, net.minecraft.screen.slot.Slot slot) {
        if (!(slot.inventory instanceof PlayerInventory) && slot.hasStack()) {
            net.minecraft.item.ItemStack st = slot.getStack();
            ctx.drawItem(st, slot.x, slot.y);
            String s = fmt(amtOf(st));
            int tx = slot.x + 17 - this.textRenderer.getWidth(s);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 200);
            ctx.drawText(this.textRenderer, s, tx, slot.y + 9, 0xFFFFFFFF, true);
            ctx.getMatrices().pop();
        } else {
            super.drawSlot(ctx, slot);
        }
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
    }
}
