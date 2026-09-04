package com.sdzjz.retro;

import com.sdzjz.screen.SuperBenchScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * m524（SB3）S 线判官：超大工作台在 1.20.1 的注册三件（方块/BE/MenuType，id 与主线同名同源）+
 * **菜单类型安装口装了没**（m523 教训②：带默认值的口默认是静默的——{@code installType} 漏调只在玩家开屏那一刻 {@code reqType()} 抛，
 * 判官用两参构造把这一刻提前到测试里）。SB4 屏、SB5 合成行为判官接在本类后面。
 */
public final class RetroBenchTests implements FabricGameTest {

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }

    /** 放下方块→BE 是本世代壳；三注册表 id 各指回 RetroBlocks 那份；handler 能建（安装口已装）且槽位数=144 网格+1 结果+36 背包。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_block_be_and_menu_installed(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.SUPER_BENCH.defaultBlockState());
        ctx.assertTrue(ctx.getBlockEntity(rel) instanceof SuperBench120, "超大工作台方块实体未生成（newBlockEntity/BE 类型没接）");
        ctx.assertTrue(BuiltInRegistries.BLOCK.get(id("super_bench")) == RetroBlocks.SUPER_BENCH, "方块 id sdzjz:super_bench 该指回 RetroBlocks.SUPER_BENCH");
        ctx.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("super_bench")) == RetroBlocks.SUPER_BENCH_BE, "BE 类型 id 该指回 RetroBlocks.SUPER_BENCH_BE");
        ctx.assertTrue(BuiltInRegistries.MENU.get(id("super_bench")) == RetroBlocks.SUPER_BENCH_MENU, "菜单 id 该指回 RetroBlocks.SUPER_BENCH_MENU");
        var p = ctx.makeMockPlayer(); // 1.20.1 无参版（1.21 起才带 GameType 参，RetroPanelTests 同注）
        var h = new SuperBenchScreenHandler(1, p.getInventory()); // 两参构造=ContainerLevelAccess.NULL；reqType() 未装会在此抛
        ctx.assertTrue(h.getType() == RetroBlocks.SUPER_BENCH_MENU, "handler 菜单类型该是本世代 installType 装进去的那份");
        ctx.assertTrue(h.slots.size() == SuperBenchScreenHandler.GRID_SLOTS + 1 + 36,
                "槽位数该=144 网格+1 结果+36 背包，实得 " + h.slots.size());
        ctx.succeed();
    }
}
