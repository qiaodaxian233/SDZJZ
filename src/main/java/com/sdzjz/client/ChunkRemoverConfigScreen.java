package com.sdzjz.client;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.net.ChunkRemoverConfigPayload;
import com.sdzjz.node.NodeTags;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * m386 区块移除器手持设置面板（作者点名"进核心里调麻烦，主题类似存储终端"）——
 * 手持移除器按快捷键（默认 R，可改键位）打开；纯客户端 Screen（非 HandledScreen，零服务端
 * 库存态），所有改动走 ChunkRemoverConfigPayload 服务端权威落 NBT，本屏每帧直读手上物品
 * 组件（服务端回同步后自动纠显，乐观值不落地）。配色全走 SciSkin（m239 铁律：屏内禁硬编码）。
 * 三件事：①区域大小自由调（±1/±10，上限=服主 chunkRemoverMaxRadius）；②掉落模式切换
 * （有掉落·出货 / 无掉落·极速蒸发）；③绑定状态一目了然。改区域=重扫（服务端口径）。
 */
public class ChunkRemoverConfigScreen extends Screen {

    private static final int W = 236, H = 148;
    private final int hand; // 0=主手 1=副手

    public ChunkRemoverConfigScreen(int hand) {
        super(Text.literal("区块移除器设置"));
        this.hand = hand;
    }

    private ItemStack stack() {
        if (client == null || client.player == null) return ItemStack.EMPTY;
        return client.player.getStackInHand(hand == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND);
    }

    private int px() { return (width - W) / 2; }

    private int py() { return (height - H) / 2; }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ItemStack s = stack();
        if (!(s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem)) { close(); return; }
        renderBackground(ctx, mouseX, mouseY, delta);
        int x = px(), y = py();
        // 面板体（终端同族：CELL 底+FRAME 边+顶栏条）
        ctx.fill(x - 1, y - 1, x + W + 1, y + H + 1, SciSkin.FRAME);
        ctx.fill(x, y, x + W, y + H, SciSkin.CELL);
        ctx.fill(x, y, x + W, y + 18, SciSkin.BAND_TOP);
        ctx.drawText(textRenderer, "区块移除器 · 设置", x + 8, y + 5, SciSkin.TXT_MAX, false);
        // 绑定行
        boolean bound = NodeTags.chunkBound(s);
        String bind = bound
                ? "绑定：区块(" + NodeTags.chunkX(s) + "," + NodeTags.chunkZ(s) + ") · " + NodeTags.chunkDim(s)
                : "未绑定：手持对目标区块内方块右键";
        ctx.drawText(textRenderer, bind, x + 10, y + 26, bound ? SciSkin.TXT : SciSkin.SUB, false);
        // 区域行：[-10][-1]  N×N  [+1][+10]
        int r = Math.max(0, NodeTags.chunkRadius(s));
        int w2 = 2 * r + 1;
        ctx.drawText(textRenderer, "移除区域", x + 10, y + 48, SciSkin.TXT_SOFT, false);
        drawBtn(ctx, x + 68, y + 44, 26, "-10", mouseX, mouseY);
        drawBtn(ctx, x + 97, y + 44, 20, "-1", mouseX, mouseY);
        String lab = w2 + "×" + w2;
        ctx.drawText(textRenderer, lab, x + 132 - textRenderer.getWidth(lab) / 2 + 14, y + 48, SciSkin.TXT_HI, false);
        drawBtn(ctx, x + 172, y + 44, 20, "+1", mouseX, mouseY);
        drawBtn(ctx, x + 195, y + 44, 26, "+10", mouseX, mouseY);
        ctx.drawText(textRenderer, "上限 " + (2 * Math.max(0, SdzjzConfig.get().chunkRemoverMaxRadius) + 1)
                + "×同值（config 可改）· 改区域=重扫", x + 10, y + 64, SciSkin.SUB, false);
        // 模式行：宽切换钮
        int m = NodeTags.chunkMode(s);
        ctx.drawText(textRenderer, "掉落模式", x + 10, y + 86, SciSkin.TXT_SOFT, false);
        boolean hovM = in(mouseX, mouseY, x + 68, y + 82, 153, 16);
        ctx.fill(x + 68, y + 82, x + 221, y + 98, hovM ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(x + 69, y + 83, x + 220, y + 97, m == 1 ? SciSkin.BTN_FACE_HOV : SciSkin.ON_DARK);
        ctx.drawText(textRenderer, m == 1 ? "无掉落 · 极速（方块直接蒸发）" : "有掉落 · 出货（进出线/存储）",
                x + 76, y + 87, m == 1 ? SciSkin.GOLD : SciSkin.ON, false);
        // 提示两行
        ctx.drawText(textRenderer, m == 1 ? "无掉落不产任何物品，清场专用请三思" : "无掉落模式速度 ×"
                + Math.max(1, SdzjzConfig.get().chunkRemoverNoDropSpeedMult) + "（点上方切换）",
                x + 10, y + 108, m == 1 ? SciSkin.RED_SOFT : SciSkin.SUB, false);
        ctx.drawText(textRenderer, "Esc 关闭 · 画布节点菜单亦可调 · 潜行右键空处快切模式", x + 10, y + 124, SciSkin.SUB, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBtn(DrawContext ctx, int bx, int by, int bw, String t, int mx, int my) {
        boolean hov = in(mx, my, bx, by, bw, 16);
        ctx.fill(bx, by, bx + bw, by + 16, hov ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM);
        ctx.fill(bx + 1, by + 1, bx + bw - 1, by + 15, hov ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE);
        ctx.drawText(textRenderer, t, bx + (bw - textRenderer.getWidth(t)) / 2, by + 4,
                hov ? SciSkin.TXT_MAX : SciSkin.TXT, false);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ItemStack s = stack();
        if (s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem) {
            int x = px(), y = py();
            int mx = (int) mouseX, my = (int) mouseY;
            int r = Math.max(0, NodeTags.chunkRadius(s));
            int m = NodeTags.chunkMode(s);
            int cap = Math.max(0, SdzjzConfig.get().chunkRemoverMaxRadius);
            Integer nr = null;
            if (in(mx, my, x + 68, y + 44, 26, 16)) nr = r - 10;
            else if (in(mx, my, x + 97, y + 44, 20, 16)) nr = r - 1;
            else if (in(mx, my, x + 172, y + 44, 20, 16)) nr = r + 1;
            else if (in(mx, my, x + 195, y + 44, 26, 16)) nr = r + 10;
            if (nr != null) {
                send(Math.max(0, Math.min(nr, cap)), m);
                return true;
            }
            if (in(mx, my, x + 68, y + 82, 153, 16)) {
                send(r, m == 1 ? 0 : 1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int radius, int mode) {
        ClientPlayNetworking.send(new ChunkRemoverConfigPayload(hand, radius, mode));
    }
}
