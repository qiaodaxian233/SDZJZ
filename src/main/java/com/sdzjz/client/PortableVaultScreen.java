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
    /** m315 搜索框几何（自绘底格与控件同源）。 */
    private static final int SRCH_Y = 18, SRCH_W = 122, SRCH_H = 14;
    private int scroll = 0;
    private net.minecraft.client.gui.widget.TextFieldWidget search; // m315：m216 去黑壳刀法
    /** 名称/首字母检索键缓存（客户端只增不清，物品名不会变；≤类型上限 256 条）。 */
    private final java.util.HashMap<String, String> nameLc = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> initKey = new java.util.HashMap<>();

    public PortableVaultScreen(PortableVaultScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 200;
        this.backgroundHeight = 236; // m315：随 PINV_Y 138→150 同步 224→236（底边留 10px，m240 教训）
    }

    @Override
    protected void init() {
        super.init();
        // m161b 去黑壳（setDrawsBackground(false)，自绘底格接管观感）；resize 保留已输入文字（pickerField 惯例）。
        String keep = this.search != null ? this.search.getText() : "";
        this.search = new net.minecraft.client.gui.widget.TextFieldWidget(
                this.textRenderer, this.x + LIST_X + 4, this.y + SRCH_Y + 3, SRCH_W - 8, 10, Text.literal("搜索"));
        this.search.setDrawsBackground(false);
        this.search.setEditableColor(SciSkin.TXT_MAX);
        this.search.setChangedListener(s -> scroll = 0); // 改词回顶，纯客户端过滤零新协议
        this.search.setText(keep);
        this.addDrawableChild(this.search);
    }

    /** 账本行快照（每帧重建，≤256 类开销可忽略；计数降序稳定）。
     *  m315：叠搜索过滤——名称子串 / id 子串 / 拼音首字母（m282 PinyinInitials，纯字母查询才开通道）。 */
    private java.util.List<String> rows(NbtCompound v) {
        java.util.List<String> ids = new java.util.ArrayList<>(v.getKeys());
        String q = search == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) {
            boolean ini = PinyinInitials.applicable(q);
            ids.removeIf(id -> {
                String name = nameLc.computeIfAbsent(id, k ->
                        new ItemStack(Registries.ITEM.get(Identifier.of(k))).getName().getString()
                                .toLowerCase(java.util.Locale.ROOT));
                if (name.contains(q) || id.contains(q)) return false;
                if (ini) {
                    String key = initKey.computeIfAbsent(id, k ->
                            PinyinInitials.of(new ItemStack(Registries.ITEM.get(Identifier.of(k))).getName().getString()));
                    if (!key.isEmpty() && key.contains(q)) return false;
                }
                return true;
            });
        }
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

        // m315 搜索底格（m216 自绘刀法：CELL 底+细边，聚焦换 ACCENT 描边；占位提示两态都可见）
        ctx.fill(x + LIST_X, y + SRCH_Y, x + LIST_X + SRCH_W, y + SRCH_Y + SRCH_H, SciSkin.CELL);
        ctx.drawBorder(x + LIST_X, y + SRCH_Y, SRCH_W, SRCH_H,
                search != null && search.isFocused() ? SciSkin.ACCENT : SciSkin.CELL_FRM);
        if (search != null && search.getText().isEmpty())
            ctx.drawText(this.textRenderer, "搜索：名称/首字母/id", x + LIST_X + 4, y + SRCH_Y + 3, SciSkin.SUB, false);

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
            ctx.drawText(this.textRenderer,
                    (search != null && !search.getText().isEmpty()) ? "没有匹配的物品"
                            : "空仓库——开吸附或 Shift 点背包物品入账",
                    x + LIST_X + 4, y + PortableVaultScreenHandler.LIST_Y + 4, SciSkin.SUB, false);
        if (maxScroll > 0) {
            String pg = (scroll + 1) + ".." + Math.min(scroll + PortableVaultScreenHandler.ROWS, ids.size()) + "/" + ids.size();
            ctx.drawText(this.textRenderer, pg, x + backgroundWidth - LIST_X - this.textRenderer.getWidth(pg), y + 20, SciSkin.SUB, false);
        }
        // m315：旧版两行同落 y+126 逐字重叠（列表底+4 与 PINV_Y-12 撞车）；PINV_Y 下移后 126/138 各占一行
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

    /** m315：搜索框聚焦时截前吃键盘（否则打字撞背包键 E 直接关屏）；Esc(256) 放行照常关屏。 */
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
        if (search != null && search.isFocused()) return search.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
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
