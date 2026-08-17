package com.sdzjz.modern;

import com.sdzjz.platform.Platform;
import com.sdzjz.platform.RecipeDomainAssertions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * m372 Modern(26.2) 配方域行为判官——与 Legacy 卅四号跑**同一套断言**
 * （com.sdzjz.platform.RecipeDomainAssertions，判官只此一份在 Common），
 * 数据源=26.2 真实 RecipeManager 经 ModernRecipeAccess。绿=编译绿升级行为绿：
 * m371 的两大核名结论（Ingredient#items() 枚举口径、Item#getCraftingRemainder() 残留口）
 * 与 placementInfo/assemble 取数路全部拿到运行时判决；m373 起七类判定含 brew/ench 域
 * （ModernBrewAccess 的 potionBrewing/mix 链与 ModernEnchantAccess 的注册表/成本链同拍受判）。
 *
 * 注解形态照 fabric-api@26.2 官方测试原文（@GameTest 默认 structure=
 * fabric-gametest-api-v1:empty，maxTicks 20——本套断言单 tick 同步跑完，富余）。
 * 注册走 fabric.mod.json 的 fabric-gametest 入口（与 Legacy 同键，官方 26.2 testmod 核到）。
 */
public final class ModernRecipeDomainTests {

    @GameTest
    public void recipeDomainContract(GameTestHelper helper) {
        try {
            RecipeDomainAssertions.runAll(helper.getLevel(), Platform.recipes());
        } catch (AssertionError e) {
            throw new GameTestAssertException(Component.literal("配方域契约失败: " + e.getMessage()), 0);
        }
        helper.succeed();
    }
}
