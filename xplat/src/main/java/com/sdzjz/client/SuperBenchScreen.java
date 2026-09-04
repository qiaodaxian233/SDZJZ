package com.sdzjz.client;

import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

/** 超大工作台 12×12 合成界面 + 右侧配方浏览器（点机器=自动从背包填料）。
 *  m525（SB4）起绘制体与交互体整段下沉 {@link SuperBenchView}（两代共用），本类只剩 1.21 世代壳：
 *  生命周期（init/render）、Screen 事件签名转发、{@link SuperBenchView.Host} 七口（全是 AbstractContainerScreen 现成字段/方法）。
 *  世代差在壳里：mouseScrolled 四参（1.20.1 三参）、renderBackground 由 1.21 框架自动调（1.20.1 壳手动单参）、EditBox 无需手动 tick。 */
public class SuperBenchScreen extends AbstractContainerScreen<SuperBenchScreenHandler> implements SuperBenchView.Host {

    private final SuperBenchView view = new SuperBenchView(this);

    public SuperBenchScreen(SuperBenchScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title);
        this.imageWidth = SuperBenchView.WIDTH;
        this.imageHeight = SuperBenchView.HEIGHT; // m240 底部越界修复：316→332，热栏整体包进框内
    }

    // ===== SuperBenchView.Host =====
    @Override public int left() { return this.leftPos; }
    @Override public int top() { return this.topPos; }
    @Override public int screenW() { return this.width; }
    @Override public int screenH() { return this.height; }
    @Override public java.util.List<net.minecraft.world.inventory.Slot> slots() { return this.menu.slots; }
    @Override public boolean clickButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
            return true;
        }
        return false;
    }
    @Override public void addBox(net.minecraft.client.gui.components.EditBox box) { this.addRenderableWidget(box); }

    @Override
    protected void init() {
        super.init();
        view.init(this.font);
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        view.renderBg(ctx, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
        view.renderLabels(ctx, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (view.mouseScrolled(mouseX, mouseY, v)) return true;
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (view.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (view.searchFocused()) return view.search().charTyped(chr, modifiers); // 主线原句：聚焦时直接回 EditBox 结果
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);
    }
}
