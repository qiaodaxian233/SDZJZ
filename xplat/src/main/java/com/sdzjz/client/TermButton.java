package com.sdzjz.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** m203 终端主题按钮：画布横幅换浅色主题后的配套控件（chrome 走 SciSkin.termBtn 唯一出口，
 *  primary=强调紫面/否则墨面）。悬停缓动照 SciButton 同族指数趋近。 */
public class TermButton extends SciButton {
    private final boolean primary;
    private float hoverP = 0f;
    private long hoverNs = 0;

    public TermButton(int x, int y, int w, int h, Component t, PressAction a) { this(x, y, w, h, t, a, false); }

    public TermButton(int x, int y, int w, int h, Component t, PressAction a, boolean primary) {
        super(x, y, w, h, t, a);
        this.primary = primary;
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = hoverNs == 0 ? 0.016f : Math.min(0.1f, (now - hoverNs) / 1.0e9f);
        hoverNs = now;
        boolean hover = this.isHovered();
        hoverP += ((hover ? 1f : 0f) - hoverP) * (1f - (float) Math.exp(-18f * dt));
        SciSkin.termBtn(ctx, net.minecraft.client.Minecraft.getInstance().textRenderer,
                getX(), getY(), width, height, getMessage().getString(), hover, primary);
        if (hoverP > 0.02f) // 底沿强调线随悬停渐显（termBtn 之上，质感语言与 SciButton 同族）
            ctx.fill(getX() + 2, getY() + height - 2, getX() + width - 2, getY() + height - 1,
                    SciSkin.withAlpha(SciSkin.termAccent(), 0.9f * hoverP));
    }
}
