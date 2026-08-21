package com.sdzjz.client;

import com.sdzjz.item.PortableVaultItem;
import com.sdzjz.net.VaultTakePayload;
import com.sdzjz.screen.PortableVaultScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * m312 随身仓库取物屏（科技风自绘零贴图，照抽取口/交易所刀法）。
 * 列表数据零同步：直接读客户端手上包的组件账本（随背包槽位天然同步）。
 * 行交互：左键=取一组64 · 右键=拿满一格 · Shift+左键=取尽装满背包；滚轮翻列表。
 * 背包槽 shift 点物品=整叠入账（handler.quickMove）。
 */
public class PortableVaultScreen extends AbstractContainerScreen<PortableVaultScreenHandler> {

    private static final int LIST_X = 8, LIST_W = 184;
    /** m315 搜索框几何（自绘底格与控件同源）。 */
    private static final int SRCH_Y = 18, SRCH_W = 122, SRCH_H = 14;
    private int scroll = 0;
    private net.minecraft.client.gui.components.EditBox search; // m315：m216 去黑壳刀法
    /** 名称/首字母检索键缓存（客户端只增不清，物品名不会变；≤类型上限 256 条）。 */
    private final java.util.HashMap<String, String> nameLc = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> initKey = new java.util.HashMap<>();

    public PortableVaultScreen(PortableVaultScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 236; // m315：随 PINV_Y 138→150 同步 224→236（底边留 10px，m240 教训）
    }

    @Override
    protected void init() {
        super.init();
        // m161b 去黑壳（setDrawsBackground(false)，自绘底格接管观感）；resize 保留已输入文字（pickerField 惯例）。
        String keep = this.search != null ? this.search.getValue() : "";
        this.search = new net.minecraft.client.gui.components.EditBox(
                this.font, this.leftPos + LIST_X + 4, this.topPos + SRCH_Y + 3, SRCH_W - 8, 10, Component.literal("搜索"));
        this.search.setBordered(false);
        this.search.setTextColor(SciSkin.TXT_MAX);
        this.search.setResponder(s -> scroll = 0); // 改词回顶，纯客户端过滤零新协议
        this.search.setValue(keep);
        this.addRenderableWidget(this.search);
    }

    /** 账本行快照（每帧重建，≤256 类开销可忽略；计数降序稳定）。
     *  m315：叠搜索过滤——名称子串 / id 子串 / 拼音首字母（m282 PinyinInitials，纯字母查询才开通道）。 */
    private java.util.List<String> rows(CompoundTag v) {
        java.util.List<String> ids = new java.util.ArrayList<>(v.getAllKeys());
        String q = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) {
            boolean ini = PinyinInitials.applicable(q);
            ids.removeIf(id -> {
                String name = nameLc.computeIfAbsent(id, k ->
                        new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(k))).getHoverName().getString()
                                .toLowerCase(java.util.Locale.ROOT));
                if (name.contains(q) || id.contains(q)) return false;
                if (ini) {
                    String key = initKey.computeIfAbsent(id, k ->
                            PinyinInitials.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(k))).getHoverName().getString()));
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
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, SciSkin.BACKDROP);
        int x = this.leftPos, y = this.topPos;
        ctx.renderOutline(x - 1, y - 1, imageWidth + 2, imageHeight + 2, SciSkin.FRAME);
        ItemStack vault = this.menu.vault();
        CompoundTag v = PortableVaultItem.ledger(vault);

        ctx.drawString(this.font, this.title, x + LIST_X, y + 8, SciSkin.ACCENT, false);
        long total = PortableVaultItem.vaultTotal(vault);
        String head = "类型 " + v.getAllKeys().size() + " · 总件 " + fmt(total);
        ctx.drawString(this.font, head, x + imageWidth - LIST_X - this.font.width(head), y + 8, SciSkin.SUB, false);

        // m315 搜索底格（m216 自绘刀法：CELL 底+细边，聚焦换 ACCENT 描边；占位提示两态都可见）
        ctx.fill(x + LIST_X, y + SRCH_Y, x + LIST_X + SRCH_W, y + SRCH_Y + SRCH_H, SciSkin.CELL);
        ctx.renderOutline(x + LIST_X, y + SRCH_Y, SRCH_W, SRCH_H,
                search != null && search.isFocused() ? SciSkin.ACCENT : SciSkin.CELL_FRM);
        if (search != null && search.getValue().isEmpty())
            ctx.drawString(this.font, "搜索：名称/首字母/id", x + LIST_X + 4, y + SRCH_Y + 3, SciSkin.SUB, false);

        java.util.List<String> ids = rows(v);
        int maxScroll = Math.max(0, ids.size() - PortableVaultScreenHandler.ROWS);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int i = 0; i < PortableVaultScreenHandler.ROWS; i++) {
            int idx = scroll + i;
            int ry = y + PortableVaultScreenHandler.LIST_Y + i * PortableVaultScreenHandler.ROW_H;
            ctx.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + PortableVaultScreenHandler.ROW_H - 2, SciSkin.CELL);
            ctx.renderOutline(x + LIST_X, ry, LIST_W, PortableVaultScreenHandler.ROW_H - 2, SciSkin.CELL_FRM);
            if (idx >= ids.size()) continue;
            boolean hov = mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                    && mouseY >= ry && mouseY < ry + PortableVaultScreenHandler.ROW_H - 2;
            if (hov) ctx.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + PortableVaultScreenHandler.ROW_H - 2, SciSkin.HOVER);
            String id = ids.get(idx);
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            ctx.renderItem(icon, x + LIST_X + 1, ry);
            ctx.drawString(this.font, icon.getHoverName().getString(), x + LIST_X + 20, ry + 4,
                    hov ? SciSkin.TXT_MAX : SciSkin.TXT, false);
            String cnt = "×" + fmt(v.getLong(id));
            ctx.drawString(this.font, cnt,
                    x + LIST_X + LIST_W - 4 - this.font.width(cnt), ry + 4, SciSkin.TXT_HI, false);
        }
        if (ids.isEmpty())
            ctx.drawString(this.font,
                    (search != null && !search.getValue().isEmpty()) ? "没有匹配的物品"
                            : "空仓库——开吸附或 Shift 点背包物品入账",
                    x + LIST_X + 4, y + PortableVaultScreenHandler.LIST_Y + 4, SciSkin.SUB, false);
        if (maxScroll > 0) {
            String pg = (scroll + 1) + ".." + Math.min(scroll + PortableVaultScreenHandler.ROWS, ids.size()) + "/" + ids.size();
            ctx.drawString(this.font, pg, x + imageWidth - LIST_X - this.font.width(pg), y + 20, SciSkin.SUB, false);
        }
        // m315：旧版两行同落 y+126 逐字重叠（列表底+4 与 PINV_Y-12 撞车）；PINV_Y 下移后 126/138 各占一行
        ctx.drawString(this.font, "左键=取一组 · 右键=拿满一格 · Shift+左=取尽",
                x + LIST_X, y + PortableVaultScreenHandler.LIST_Y + PortableVaultScreenHandler.ROWS * PortableVaultScreenHandler.ROW_H + 4,
                SciSkin.SUB, false);
        ctx.drawString(this.font, "Shift 点背包物品=整叠入账",
                x + LIST_X, y + PortableVaultScreenHandler.PINV_Y - 12, SciSkin.SUB, false);

        // 背包槽底（与 handler 常量同源）
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                cell(ctx, x + PortableVaultScreenHandler.PINV_X + c * 18 - 1, y + PortableVaultScreenHandler.PINV_Y + r * 18 - 1);
        for (int c = 0; c < 9; c++)
            cell(ctx, x + PortableVaultScreenHandler.PINV_X + c * 18 - 1, y + PortableVaultScreenHandler.PINV_Y + 58 - 1);
    }

    private static void cell(GuiGraphics ctx, int x, int y) {
        ctx.fill(x, y, x + 18, y + 18, SciSkin.CELL);
        ctx.renderOutline(x, y, 18, 18, SciSkin.CELL_FRM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos, y = this.topPos;
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                && mouseY >= y + PortableVaultScreenHandler.LIST_Y
                && mouseY < y + PortableVaultScreenHandler.LIST_Y + PortableVaultScreenHandler.ROWS * PortableVaultScreenHandler.ROW_H) {
            int row = (int) ((mouseY - y - PortableVaultScreenHandler.LIST_Y) / PortableVaultScreenHandler.ROW_H);
            CompoundTag v = PortableVaultItem.ledger(this.menu.vault());
            java.util.List<String> ids = rows(v);
            int idx = scroll + row;
            if (idx >= 0 && idx < ids.size()) {
                int mode = button == 1 ? 1 : (hasShiftDown() ? 2 : 0);
                com.sdzjz.client.ClientNet.toServer(new VaultTakePayload(ids.get(idx), mode));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = this.leftPos, y = this.topPos;
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
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
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
