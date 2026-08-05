package com.sdzjz.compat.jei;

import com.sdzjz.net.JeiFillPayload;
import com.sdzjz.registry.ModScreenHandlers;
import com.sdzjz.screen.DataPanelScreenHandler;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.ScreenHandlerType;

import java.util.Optional;

/**
 * 自定义转移器（m212，替代 m205 的基本七参注册）：transferRecipe 在客户端被 JEI 调用，
 * 我们只发自家 C2S 包（JeiFillPayload：配方 id + 是否整组），材料由服务端权威结算——
 * **仓储网络优先取料、背包兜底**（作者点名"合成终端要能读取终端里的物品"）。
 * 附带收益：不走 JEI 自带的服务端槽位搬运 → 专用服务器无需装 JEI。
 * 注意：本类引用了客户端专属 ClientPlayNetworking——JEI 的插件注册只发生在客户端运行时，
 * 专用服务器上本类不会被加载（与 entrypoint 惰性加载同理），不会炸服。
 */
public final class SdzjzJeiTransfer
        implements IRecipeTransferHandler<DataPanelScreenHandler, RecipeEntry<CraftingRecipe>> {

    @Override
    public Class<? extends DataPanelScreenHandler> getContainerClass() {
        return DataPanelScreenHandler.class;
    }

    @Override
    public Optional<ScreenHandlerType<DataPanelScreenHandler>> getMenuType() {
        return Optional.of(ModScreenHandlers.DATA_PANEL);
    }

    @Override
    public RecipeType<RecipeEntry<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(DataPanelScreenHandler handler, RecipeEntry<CraftingRecipe> recipe,
                                               IRecipeSlotsView recipeSlots, PlayerEntity player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            ClientPlayNetworking.send(new JeiFillPayload(handler.blockPos(), recipe.id(), maxTransfer));
        }
        return null; // null=放行："+"常亮，缺料由服务端结算后 actionbar 报数（客户端不预测，m95 口径）
    }
}
