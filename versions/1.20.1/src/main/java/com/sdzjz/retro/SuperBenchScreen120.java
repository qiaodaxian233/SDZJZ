package com.sdzjz.retro;

import com.sdzjz.client.SuperBenchView;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 超大工作台屏 **1.20.1 世代壳**（m524 SB3 最小壳 → m525 SB4 换共用绘制体 {@link SuperBenchView}，两代同一份）。
 *  与主线 {@code SuperBenchScreen} 壳逐句对照，世代差三处（m448 原注）：{@code renderBackground} 单参手动调（1.20.2 起框架带坐标自动调）、
 *  {@code mouseScrolled} 三参（1.20.2 起四参）、EditBox 光标闪烁要 {@code containerTick} 手动 {@code tick()}（1.20.2 起移除）。
 *  Host 七口全是 AbstractContainerScreen 现成字段/方法的转发，两代同名同签名。 */
public final class SuperBenchScreen120 extends AbstractContainerScreen<SuperBenchScreenHandler> implements SuperBenchView.Host {

    private final SuperBenchView view = new SuperBenchView(this);

    public SuperBenchScreen120(SuperBenchScreenHandler menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SuperBenchView.WIDTH;
        this.imageHeight = SuperBenchView.HEIGHT;
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
    protected void containerTick() {
        super.containerTick();
        if (view.search() != null) view.search().tick(); // 1.20.1 光标闪烁需手动 tick（1.20.2 起移除；DataPanelScreen120 同句）
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); // 1.20.1 单参（1.20.2 起带坐标）
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        view.renderBg(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        view.renderLabels(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { // 1.20.1 三参（1.20.2 起四参）
        if (view.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
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
}
