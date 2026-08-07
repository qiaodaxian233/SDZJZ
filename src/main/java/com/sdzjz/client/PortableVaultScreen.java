package com.sdzjz.client;

import com.sdzjz.item.PortableVaultItem;
import com.sdzjz.net.VaultTakePayload;
import com.sdzjz.screen.PortableVaultScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * m312 随身仓库取物屏（科技风自绘零贴图，照抽取口/交易所刀法）。
 * 列表数据零同步：直接读客户端手上包的组件账本（随背包槽位天然同步）。
 * 行交互：左键=取一组64 · 右键=拿满一格 · Shift+左键=取尽装满背包；滚轮翻列表。
 * 背包槽 shift 点物品=整叠入账（handler.quickMove）。
 */
public class PortableVaultScreen extends HandledScreen<PortableVaultScreenHandler> {

    private static final int LIST_X = 8, LIST_W = 184;
    private int scroll = 0;

    public PortableVaultScreen(PortableVaultScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 200;
        this.backgroundHeight = 224;
    }

    /** 账本行快照（每帧重建，≤256 类开销可忽略；计数降序稳定）。 */
    private java.util.List<String> rows(NbtCompound v) {
        java.util.List<String> ids = new java.util.ArrayList<>(v.getKeys());
        ids.sort((a, b) -> {
            int c = Long.compare(v.getLong(b), v.getLong(a));
            return c != 0 ? c : a.compareTo(b);
        });
        return ids;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, SciSkin.BACKDROP);
        int x = this.x, y = this.y;
        ctx.drawBorder(x - 1, y - 1, backgroundWidth + 2, backgroundHeight + 2, SciSkin.FRAME);
        ItemStack vault = this.handler.vault();
        NbtCompound v = PortableVaultItem.ledger(vault);

        ctx.drawText(this.textRenderer, this.title, x + LIST_X, y + 8, SciSkin.ACCENT, false);
        long total = PortableVaultItem.vaultTotal(vault);
        String head = "类型 " + v.getKeys().size() + " · 总件 " + fmt(total);
        ctx.drawText(this.textRenderer, head, x + backgroundWidth - LIST_X - this.textRenderer.getWidth(head), y + 8, SciSkin.SUB, false);

        java.util.List<String> ids = rows(v);
        int maxScroll = Math.max(0, ids.size() - PortableVaultScreenHandler.ROWS);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int i = 0; i < PortableVaultScreenHandler.ROWS; i++) {
            int idx = scroll + i;
            int ry = y + PortableVaultScreenHandler.LIST_Y + i * PortableVaultScreenHandler.ROW_H;
            ctx.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + PortableVaultScreenHandler.ROW_H - 2, SciSkin.CELL);
            ctx.drawBorder(x + LIST_X, ry, LIST_W, PortableVaultScreenHandler.ROW_H - 2, SciSkin.CELL_FRM);
            if (idx >= ids.size()) continue;
            boolean hov = mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                    && mouseY >= ry && mouseY < ry + PortableVaultScreenHandler.ROW_H - 2;
            if (hov) ctx.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + PortableVaultScreenHandler.ROW_H - 2, SciSkin.HOVER);
            String id = ids.get(idx);
            ItemStack icon = new ItemStack(Registries.ITEM.get(Identifier.of(id)));
            ctx.drawItem(icon, x + LIST_X + 1, ry);
            ctx.drawText(this.textRenderer, icon.getName().getString(), x + LIST_X + 20, ry + 4,
                    hov ? SciSkin.TXT_MAX : SciSkin.TXT, false);
            String cnt = "×" + fmt(v.getLong(id));
            ctx.drawText(this.textRenderer, cnt,
                    x + LIST_X + LIST_W - 4 - this.textRenderer.getWidth(cnt), ry + 4, SciSkin.TXT_HI, false);
        }
        if (ids.isEmpty())
            ctx.drawText(this.textRenderer, "空仓库——开吸附或 Shift 点背包物品入账",
                    x + LIST_X + 4, y + PortableVaultScreenHandler.LIST_Y + 4, SciSkin.SUB, false);
        if (maxScroll > 0) {
            String pg = (scroll + 1) + ".." + Math.min(scroll + PortableVaultScreenHandler.ROWS, ids.size()) + "/" + ids.size();
            ctx.drawText(this.textRenderer, pg, x + backgroundWidth - LIST_X - this.textRenderer.getWidth(pg), y + 20, SciSkin.SUB, false);
        }
        ctx.drawText(this.textRenderer, "左键=取一组 · 右键=拿满一格 · Shift+左=取尽",
                x + LIST_X, y + PortableVaultScreenHandler.LIST_Y + PortableVaultScreenHandler.ROWS * PortableVaultScreenHandler.ROW_H + 4,
                SciSkin.SUB, false);
        ctx.drawText(this.textRenderer, "Shift 点背包物品=整叠入账",
                x + LIST_X, y + PortableVaultScreenHandler.PINV_Y - 12, SciSkin.SUB, false);

        // 背包槽底（与 handler 常量同源）
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                cell(ctx, x + PortableVaultScreenHandler.PINV_X + c * 18 - 1, y + PortableVaultScreenHandler.PINV_Y + r * 18 - 1);
        for (int c = 0; c < 9; c++)
            cell(ctx, x + PortableVaultScreenHandler.PINV_X + c * 18 - 1, y + PortableVaultScreenHandler.PINV_Y + 58 - 1);
    }

    private static void cell(DrawContext ctx, int x, int y) {
        ctx.fill(x, y, x + 18, y + 18, SciSkin.CELL);
        ctx.drawBorder(x, y, 18, 18, SciSkin.CELL_FRM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.x, y = this.y;
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                && mouseY >= y + PortableVaultScreenHandler.LIST_Y
                && mouseY < y + PortableVaultScreenHandler.LIST_Y + PortableVaultScreenHandler.ROWS * PortableVaultScreenHandler.ROW_H) {
            int row = (int) ((mouseY - y - PortableVaultScreenHandler.LIST_Y) / PortableVaultScreenHandler.ROW_H);
            NbtCompound v = PortableVaultItem.ledger(this.handler.vault());
            java.util.List<String> ids = rows(v);
            int idx = scroll + row;
            if (idx >= 0 && idx < ids.size()) {
                int mode = button == 1 ? 1 : (hasShiftDown() ? 2 : 0);
                ClientPlayNetworking.send(new VaultTakePayload(ids.get(idx), mode));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = this.x, y = this.y;
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                && mouseY >= y + PortableVaultScreenHandler.LIST_Y
                && mouseY < y + PortableVaultScreenHandler.LIST_Y + PortableVaultScreenHandler.ROWS * PortableVaultScreenHandler.ROW_H) {
            scroll -= (int) Math.signum(verticalAmount); // m103 口径：悬停列表才响应
            if (scroll < 0) scroll = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // 标题自绘于 drawBackground，撤默认双份标题
    }

    private static String fmt(long n) { // m232 口径
        if (n >= 1_000_000_000_000L) return one(n / 1.0e12) + "T";
        if (n >= 1_000_000_000L) return one(n / 1.0e9) + "B";
        if (n >= 1_000_000L) return one(n / 1.0e6) + "M";
        if (n >= 10_000L) return one(n / 1.0e3) + "K";
        return String.valueOf(n);
    }

    private static String one(double x2) {
        return x2 >= 100 ? String.valueOf((long) x2)
                : String.valueOf(Math.round(x2 * 10) / 10.0).replaceAll("\\.0$", "");
    }
}
