package com.sdzjz.compat.jei;

import com.sdzjz.Sdzjz;
import com.sdzjz.registry.ModScreenHandlers;
import com.sdzjz.screen.DataPanelScreenHandler;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.util.Identifier;

/**
 * JEI 兼容插件（m205，看板 #15）：让 JEI 配方界面的 "+" 按钮能把工作台配方
 * 一键填进数据面板（存储终端）的合成区。
 *
 * 机制核对（JEI@1.21.1 源码实锤，不臆测）：
 * - 发现机制：Fabric 侧 JEI 走 fabric.mod.json 的 "jei_mod_plugin" entrypoint
 *   （FabricPluginFinder.getModPlugins → getEntrypointContainers("jei_mod_plugin", IModPlugin.class)），
 *   不是注解扫描；@JeiPlugin 注解按 API javadoc 要求保留（NeoForge 侧靠它发现）。
 *   entrypoint 惰性实例化：没装 JEI 就没人调它，本类永不加载 → compileOnly 不增加玩家侧前置。
 * - 注册签名：IRecipeTransferRegistration.addRecipeTransferHandler 七参基本注册
 *   （containerClass / 可空 menuType / recipeType / 配方槽起+数 / 库存槽起+数，@since 11.0.0）。
 * - 服务端搬运（BasicRecipeTransferHandlerServer）只做 canInsert/canTakeItems 校验 + setStack
 *   直写 + sendContentUpdates：咱合成格是裸 Slot、库存区给的是原版玩家背包槽，全兼容；
 *   结果格（RESULT=9）与展示区（DISP0..INV0，网络投影槽，禁止外人直写）都不在搬运范围。
 *
 * 口径：槽位下标全部引用 handler 头部常量（m201 唯一口径），不许手写数字。
 * 库存区=玩家背包+快捷栏 36 格（INV0..INV0+35）；"+" 取料只看背包——展示区是仓储网络
 * 的只读投影，JEI 直写会撕账本，绝不能圈进库存区；持续合成时网格模板化网络补料（m106）照旧。
 * 注意：JEI 的转移是 C2S 包、服务端执行——单人/自建局域网天然可用；专用服务器需服务端也装 JEI。
 */
@JeiPlugin
public final class SdzjzJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Sdzjz.id("jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                DataPanelScreenHandler.class,
                ModScreenHandlers.DATA_PANEL,           // 锁定菜单类型：别的屏不误触
                RecipeTypes.CRAFTING,                   // 工作台配方
                DataPanelScreenHandler.CRAFT0, 9,       // 配方槽=合成 3×3（0..8）
                DataPanelScreenHandler.INV0, 36         // 库存槽=玩家背包+快捷栏（64..99）
        );
    }
}
