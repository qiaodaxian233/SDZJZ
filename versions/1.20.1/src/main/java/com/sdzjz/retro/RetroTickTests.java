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

    /** m466（C2-⑤b）：耗料机连了产出仓但没连供料仓=红灯说人话（先验在预算前，不烧预算不丢工作量）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void tick_consumer_needs_supply_link(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        BlockPos srel = new BlockPos(1, 1, 0);
        ctx.setBlock(srel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        c.addNode(node("iron_smelter", 10, 10));
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(srel).asLong(), 0}); // 只连产出边
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ctx.runAfterDelay(20, () -> {
            ctx.assertTrue(c.g.nodeStatus.get(0) == 3, "缺供料线应红灯，实得 " + c.g.nodeStatus.get(0));
            ctx.assertTrue(c.g.nodeReason.get(0).contains("未连供料仓"), "红灯原因应说人话，实得 " + c.g.nodeReason.get(0));
            com.sdzjz.machine.CoreScheduler.clearAll();
            ctx.succeed();
        });
    }

    /** m466：按料折算+进出账恒等式——iron_smelter（20 拍 1 周期，1 粗铁→1 铁锭 chance=1 确定性）：
     *  供料仓只给 3 粗铁 → 恰产 3 铁锭、供料仓清零、两仓零串账，料尽后红灯"缺料"。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void tick_consumer_folds_by_materials_ledger_identity(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        BlockPos depRel = new BlockPos(1, 1, 0), supRel = new BlockPos(2, 1, 0);
        ctx.setBlock(depRel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        ctx.setBlock(supRel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(depRel) instanceof StorageCore120 dep)
                || !(ctx.getBlockEntity(supRel) instanceof StorageCore120 sup)) {
            ctx.fail("存储核心方块实体未生成");
            return;
        }
        sup.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:raw_iron")), 3));
        c.addNode(node("iron_smelter", 10, 10));
        String dim = ctx.getLevel().dimension().location().toString();
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(depRel).asLong(), 0}); // 产出→dep
        c.g.storageEdgeDims.add(dim);
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(supRel).asLong(), 1}); // 供料←sup
        c.g.storageEdgeDims.add(dim);
        ctx.succeedWhen(() -> {
            ctx.assertTrue(dep.count("minecraft:iron_ingot") == 3, "3 粗铁应恰产 3 铁锭（进出账恒等式），现账 " + dep.count("minecraft:iron_ingot"));
            ctx.assertTrue(sup.count("minecraft:raw_iron") == 0, "供料仓应清零，现余 " + sup.count("minecraft:raw_iron"));
            ctx.assertTrue(dep.count("minecraft:raw_iron") == 0 && sup.count("minecraft:iron_ingot") == 0, "两仓不得串账");
            ctx.assertTrue(c.g.nodeStatus.get(0) == 3 && c.g.nodeReason.get(0).contains("缺料"),
                    "料尽应红灯缺料说人话，实得 " + c.g.nodeStatus.get(0) + "/" + c.g.nodeReason.get(0));
            com.sdzjz.machine.CoreScheduler.clearAll();
        });
    }

    /** m466 护栏：产出仓类型满时耗料机**先验待机不白耗料**——同步手拍 tick（配置改动零暴露窗口，
     *  不脏并行判官）：硬顶=1 且产出仓已被占位类型填满，手拍 25 拍后料一件未动、产出为零、黄灯说话。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void tick_consumer_type_guard_no_waste(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        BlockPos depRel = new BlockPos(1, 1, 0), supRel = new BlockPos(2, 1, 0);
        ctx.setBlock(depRel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        ctx.setBlock(supRel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(depRel) instanceof StorageCore120 dep)
                || !(ctx.getBlockEntity(supRel) instanceof StorageCore120 sup)) {
            ctx.fail("存储核心方块实体未生成");
            return;
        }
        dep.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:dirt")), 1)); // 占掉唯一类型位
        sup.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:raw_iron")), 5));
        c.addNode(node("iron_smelter", 10, 10));
        String dim = ctx.getLevel().dimension().location().toString();
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(depRel).asLong(), 0});
        c.g.storageEdgeDims.add(dim);
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(supRel).asLong(), 1});
        c.g.storageEdgeDims.add(dim);
        com.sdzjz.config.SdzjzConfig cfg = com.sdzjz.config.SdzjzConfig.get();
        int old = cfg.absoluteStorageTypeSafetyLimit;
        cfg.absoluteStorageTypeSafetyLimit = 1; // 同步块内设改+手拍+复位，无跨拍暴露
        try {
            BlockPos abs = ctx.absolutePos(new BlockPos(0, 1, 0));
            for (int t = 0; t < 25; t++)
                StructureCore120.tick(ctx.getLevel(), abs, ctx.getLevel().getBlockState(abs), c);
        } finally {
            cfg.absoluteStorageTypeSafetyLimit = old;
        }
        ctx.assertTrue(sup.count("minecraft:raw_iron") == 5, "护栏下料应一件不耗，现余 " + sup.count("minecraft:raw_iron"));
        ctx.assertTrue(dep.count("minecraft:iron_ingot") == 0, "护栏下不得产出");
        ctx.assertTrue(c.g.nodeStatus.get(0) == 2 && c.g.nodeReason.get(0).contains("类型"),
                "应黄灯说类型满，实得 " + c.g.nodeStatus.get(0) + "/" + c.g.nodeReason.get(0));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }
}
