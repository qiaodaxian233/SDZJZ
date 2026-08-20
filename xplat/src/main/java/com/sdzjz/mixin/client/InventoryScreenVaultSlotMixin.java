package com.sdzjz.mixin.client;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.item.PortableVaultSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * m332 随身仓库专属仓位②：生存背包屏给追加格手绘槽框（原版底图上没这格）。
 * 三色=暗描边/亮描边/灰底，刻意抄原版槽框配色**不进 SciSkin**——这格要跟原版底图走、
 * 不跟 MOD 主题走（换肤不该把原版背包屏染色）。配方书开合 this.x 自位移，框零补偿跟走。
 * 创造模式"背包"页是另一套 CreativeSlot 重排，追加格在那页的落位属原版重排逻辑——
 * 观感异常先关 portableVaultSlot 复核（边界立档见 DEVLOG m332）。
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenVaultSlotMixin extends AbstractContainerScreen<InventoryMenu> {

    public InventoryScreenVaultSlotMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void sdzjz$drawVaultSlotFrame(GuiGraphics ctx, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (!SdzjzConfig.get().portableVaultSlot) return;
        int px = this.leftPos + PortableVaultSlot.SLOT_X, py = this.topPos + PortableVaultSlot.SLOT_Y;
        ctx.fill(px - 1, py - 1, px + 17, py, 0xFF373737);       // 上暗
        ctx.fill(px - 1, py, px, py + 16, 0xFF373737);           // 左暗
        ctx.fill(px - 1, py + 16, px + 17, py + 17, 0xFFFFFFFF); // 下亮
        ctx.fill(px + 16, py - 1, px + 17, py + 16, 0xFFFFFFFF); // 右亮
        ctx.fill(px, py, px + 16, py + 16, 0xFF8B8B8B);          // 灰底
    }
}
