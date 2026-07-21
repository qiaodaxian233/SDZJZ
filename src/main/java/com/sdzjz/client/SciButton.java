package com.sdzjz.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** m117 从 StructureCoreScreen 提为公共控件：全 MOD 按钮统一样式（配色见 SciSkin）。 */
public class SciButton extends ButtonWidget {
    public SciButton(int x, int y, int w, int h, Text t, PressAction a) {
        super(x, y, w, h, t, a, s -> s.get());
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hover = this.isHovered();
        int border = hover ? SciSkin.BTN_FRM_HOV : SciSkin.BTN_FRM;
        int fill = hover ? SciSkin.BTN_FACE_HOV : SciSkin.BTN_FACE;
        int tc = hover ? SciSkin.TXT_MAX : SciSkin.TXT;
        ctx.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, border);
        ctx.fill(getX(), getY(), getX() + width, getY() + height, fill);
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2, tc);
    }
}
