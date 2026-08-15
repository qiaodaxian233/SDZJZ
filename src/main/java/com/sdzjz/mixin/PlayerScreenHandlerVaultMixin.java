package com.sdzjz.mixin;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.item.PortableVaultSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * m332 随身仓库专属仓位①：给原版背包屏追加第 47 格（下标 46，副手正上方）。
 * 追加在槽表末尾=原版 0..45 下标零漂移；原版 quickMove 的显式区段全不受累，
 * 46 号落进它的兜底 else 分支=shift 点仓位自动回背包（白捡）。
 * 账面不进 PlayerInventory——见 PortableVaultSlot（PersistentState 按 UUID，死亡不掉/换维度跟人）。
 * 开关 portableVaultSlot **需双端一致**（bigStacks 同律：不一致=槽数错位、同步包下标越界）。
 */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerVaultMixin extends ScreenHandler {

    protected PlayerScreenHandlerVaultMixin(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sdzjz$addVaultSlot(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        if (!SdzjzConfig.get().portableVaultSlot) return;
        this.addSlot(new PortableVaultSlot(new PortableVaultSlot.Inv(owner)));
    }
}
