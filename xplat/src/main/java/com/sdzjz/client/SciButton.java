package com.sdzjz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** m117 从 StructureCoreScreen 提为公共控件：全 MOD 按钮统一样式（配色见 SciSkin）。 */
public class SciButton extends Button {
    private float hoverP = 0f; // m187 悬停缓动进度（与 m186 缩放同族指数趋近）
    private long hoverNs = 0;

    public SciButton(int x, int y, int w, int h, Component t, PressAction a) {
        super(x, y, w, h, t, a, s -> s.get());
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = hoverNs == 0 ? 0.016f : Math.min(0.1f, (now - hoverNs) / 1.0e9f);
        hoverNs = now;
        boolean hover = this.isHovered();
        hoverP += ((hover ? 1f : 0f) - hoverP) * (1f - (float) Math.exp(-18f * dt)); // m187 悬停渐入渐出
        int tc = SciSkin.mix(SciSkin.TXT, SciSkin.TXT_MAX, SciSkin.easeOut(hoverP));
        SciSkin.drawButton(ctx, getX(), getY(), width, height, hover); // m118 贴图按钮（简易稿复刻旧观感，换皮=覆盖 png）
        if (hoverP > 0.02f) // m187 底沿强调线随悬停渐显（贴图之上、文字之下）
            ctx.fill(getX() + 2, getY() + height - 2, getX() + width - 2, getY() + height - 1,
                    SciSkin.withAlpha(SciSkin.ACCENT, 0.85f * hoverP));
        ctx.drawCenteredTextWithShadow(Minecraft.getInstance().textRenderer, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2, tc);
    }
}
