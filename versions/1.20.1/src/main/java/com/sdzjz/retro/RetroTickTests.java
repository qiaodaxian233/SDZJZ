package com.sdzjz.retro;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * m464（C2-⑤a）生产 tick 判官：脊柱+数据驱动无输入生成类。
 * 样本机=cobble_maker（10 拍 1 圆石，chance=1f，min=max）——rate 恒 1.0 下产量对拍**确定性**，
 * 判官可以对数而非对趋势。CoreScheduler 全服静态池首尾 clearAll 护栏（蓝本 m309 同款）。
 */
public class RetroTickTests implements FabricGameTest {

    private static StructureCore120 canvas(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.STRUCTURE_CORE.defaultBlockState());
        if (ctx.getBlockEntity(rel) instanceof StructureCore120 c) return c;
        ctx.fail("结构核心方块实体未生成");
        return null;
    }

    private static ItemStack node(String machineId, int xc, int yc) {
        ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("sdzjz", machineId)));
        s.getOrCreateTag().putInt("xc", xc);
        s.getOrCreateTag().putInt("yc", yc);
        return s;
    }

    /** 无产出仓=红灯说人话待机；特种（空产表）/耗料机型=黄灯待后续分片——tick 跑起来但一件不产。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void tick_lamps_without_output_storage(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("cobble_maker", 10, 10));   // 生成类：该红（未连仓）
        c.addNode(node("auto_crafter", 20, 10));   // 特种（空产表）：该黄
        c.addNode(node("super_smelter", 30, 10));  // 熔炉族：该黄
        ctx.runAfterDelay(20, () -> {
            ctx.assertTrue(c.g.nodeStatus.get(0) == 3, "未连仓生成机应红灯，实得 " + c.g.nodeStatus.get(0));
            ctx.assertTrue(c.g.nodeReason.get(0).contains("未连产出仓"), "红灯原因应说人话，实得 " + c.g.nodeReason.get(0));
            ctx.assertTrue(c.g.nodeStatus.get(1) == 2 && c.g.nodeStatus.get(2) == 2,
                    "特种/熔炉族应黄灯待分片，实得 " + c.g.nodeStatus.get(1) + "/" + c.g.nodeStatus.get(2));
            ctx.succeed();
        });
    }

    /** 确定性产量对拍：cobble_maker（10 拍 1 件）连 kind0 产出仓，60 拍窗口入账 ≥5 件且灯绿。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tick_generator_deposits_into_linked_storage(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        BlockPos srel = new BlockPos(1, 1, 0);
        ctx.setBlock(srel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(srel) instanceof StorageCore120 sc)) {
            ctx.fail("存储核心方块实体未生成");
            return;
        }
        c.addNode(node("cobble_maker", 10, 10));
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(srel).asLong(), 0}); // kind0=产出（m458 手势口径）
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ctx.succeedWhen(() -> {
            long got = sc.count("minecraft:cobblestone");
            ctx.assertTrue(got >= 5, "60+ 拍窗口应入账 ≥5 件圆石（10 拍/件确定性），现账 " + got);
            ctx.assertTrue(c.g.nodeStatus.get(0) == 1, "产出中应绿灯，实得 " + c.g.nodeStatus.get(0));
            com.sdzjz.machine.CoreScheduler.clearAll();
        });
    }

    /** 维度闸与首中即用：错维度边不认（红灯）；补一条本维度边即恢复生产。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tick_deposit_target_respects_dimension(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        BlockPos srel = new BlockPos(1, 1, 0);
        ctx.setBlock(srel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(srel) instanceof StorageCore120 sc)) {
            ctx.fail("存储核心方块实体未生成");
            return;
        }
        c.addNode(node("cobble_maker", 10, 10));
        long pl = ctx.absolutePos(srel).asLong();
        c.g.storageEdges.add(new long[]{0, pl, 0});
        c.g.storageEdgeDims.add("minecraft:the_end"); // 错维度：不认
        ctx.assertTrue(c.depositTarget(ctx.getLevel(), 0) == null, "错维度边不得当产出仓");
        c.g.storageEdges.add(new long[]{0, pl, 1}); // 供料边（kind1）：产出侧同样不认
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ctx.assertTrue(c.depositTarget(ctx.getLevel(), 0) == null, "供料边（kind1）不得当产出仓");
        c.g.storageEdges.add(new long[]{0, pl, 0}); // 本维度产出边：认
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ctx.assertTrue(c.depositTarget(ctx.getLevel(), 0) == sc, "本维度 kind0 边应命中存储核心");
        ctx.succeedWhen(() -> {
            ctx.assertTrue(sc.count("minecraft:cobblestone") >= 3, "接对边后应恢复生产入账");
            com.sdzjz.machine.CoreScheduler.clearAll();
        });
    }
}
