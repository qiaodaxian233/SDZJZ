package com.sdzjz.retro;

import com.sdzjz.client.SciSkin;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** m524（SB3）超大工作台屏 **1.20.1 最小壳**：只画面板底 + 主线槽位贴图，让"放下方块右键能开屏"可验（作业表 SB3 验收④）。
 *  绘制体（右侧配方浏览器/搜索框/压缩区两钮/合成台工艺 m207/m240）随 **SB4** 下沉共用件后整体换掉——
 *  **本类不许长功能**（m508 最小菜单件→m509 换共用件同律，长了就是仿写件）。
 *  1.20.1 客户端签名差（m448 原注）：{@code renderBackground} 单参；render/renderBg 同名同签名。
 *  尺寸口径照主线 {@code SuperBenchScreen}：高 332（m240 底部越界修复口径），宽只覆盖网格+结果槽（主线 470 含右侧浏览器 PX=270，SB4 到序）。 */
public final class SuperBenchScreen120 extends AbstractContainerScreen<SuperBenchScreenHandler> {

    /** handler 坐标（m201）：网格 gx=8/gy=18，结果槽 x=gx+GRID*18+24，背包 py=gy+GRID*18+12。 */
    private static final int GX = 8, GY = 18;
    private static final int W = GX + SuperBenchScreenHandler.GRID * 18 + 24 + 18 + GX; // 网格+结果槽+右边距=274
    private static final int PY = GY + SuperBenchScreenHandler.GRID * 18 + 12;          // 背包首行 y=246

    public SuperBenchScreen120(SuperBenchScreenHandler menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = 332;
        this.inventoryLabelY = PY - 10; // 标签压背包槽上一行（DataPanelScreen120 同律）
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); // 1.20.1 单参（1.20.2 起带坐标）
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        SciSkin.termPanel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) SciSkin.termSlot(g, leftPos + slot.x, topPos + slot.y); // 主线槽（18×18 贴图，传物品区左上角）
    }
}
