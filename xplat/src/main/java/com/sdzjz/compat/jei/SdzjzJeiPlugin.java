package com.sdzjz.compat.jei;

import com.sdzjz.Sdzjz;

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
 * 口径（m212 起）：转移=自家 C2S 包（JeiFillPayload），服务端权威从**仓储网络优先**取料、背包兜底
 * （见 SdzjzJeiTransfer / DataPanelScreenHandler.jeiFill）；展示区仍是只读投影，任何人不得直写。
 * 专用服务器无需装 JEI（我们不走它的服务端搬运）。
 */
@JeiPlugin
public final class SdzjzJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Sdzjz.id("jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // m212：弃基本七参注册（那条只会搬玩家背包），换自定义转移器——发自家 C2S 包，
        // 服务端从仓储网络优先取料、背包兜底；专用服务器无需装 JEI。
        registration.addRecipeTransferHandler(new SdzjzJeiTransfer(), RecipeTypes.CRAFTING);
    }
}
