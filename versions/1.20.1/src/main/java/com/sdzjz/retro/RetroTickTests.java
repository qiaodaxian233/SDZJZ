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
        s.getOrCreateTag().putInt("nx", xc); // m474 键位归位：画布坐标 nx/ny（原 xc 撞 NodeTags 抽取累计）
        s.getOrCreateTag().putInt("ny", yc);
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

    // ===== m471（C2-⑤c1）在途缓存 + 直连路由判官五条 =====
    // 样本对：sand_maker（15 拍 1 沙，chance=1f、min=max）→ glass_kiln（15 拍吃 1 沙出 1 玻璃）——
    // 全库唯一「确定性生成机产物恰好是确定性耗料机输入」的一对，判官可以对数不对趋势。

    /** 建存储核心（判官通用）。 */
    private static StorageCore120 storage(GameTestHelper ctx, BlockPos rel) {
        ctx.setBlock(rel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (ctx.getBlockEntity(rel) instanceof StorageCore120 sc) return sc;
        ctx.fail("存储核心方块实体未生成");
        return null;
    }

    /** 手拍 n 次（配置改动/单元断言场景用：同步跑完无跨拍暴露窗，不脏并行判官）。 */
    private static void handTick(GameTestHelper ctx, StructureCore120 c, int n) {
        BlockPos abs = ctx.absolutePos(new BlockPos(0, 1, 0));
        for (int t = 0; t < n; t++)
            StructureCore120.tick(ctx.getLevel(), abs, ctx.getLevel().getBlockState(abs), c);
    }

    /** ①直连产线跑通：sand_maker →（出线）→ glass_kiln，**下游一条供料仓边都不接**也能吃上料。
     *  这条是 ⑤c1 的验收线——「产线接产线」在本世代第一次成立。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void tick_direct_link_feeds_downstream_without_supply_storage(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));  // 0：免费产沙，**不接任何仓**，只有一条出线
        c.addNode(node("glass_kiln", 20, 10));  // 1：吃沙出玻璃，**不接供料仓**，只接产出仓
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{1, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 0}); // 只给下游一条 kind0
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ctx.succeedWhen(() -> {
            long glass = dep.count("minecraft:glass");
            ctx.assertTrue(glass >= 3, "直连产线应产出玻璃入账（下游无供料仓，料全走线），现账 " + glass);
            ctx.assertTrue(c.g.nodeStatus.get(0) == 1 && c.g.nodeStatus.get(1) == 1,
                    "上下游都该绿灯，实得 " + c.g.nodeStatus.get(0) + "/" + c.g.nodeStatus.get(1));
            ctx.assertTrue(dep.count("minecraft:sand") == 0, "沙子该全程走线不落仓，现仓里有 " + dep.count("minecraft:sand"));
            com.sdzjz.machine.CoreScheduler.clearAll();
        });
    }

    /** ②下游不收 → 余量必落 kind0 仓，**进出账恒等式**：产 N = 仓 N + 在途 0 + 遗留池 0，一件不许凭空消失。
     *  下游取 cobble_maker（免费产出机，accepts 尾兜=false），手拍 60 拍=sand_maker 恰 4 周期。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void tick_undeliverable_output_falls_back_to_storage(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));    // 0：有出线，也有产出仓
        c.addNode(node("cobble_maker", 20, 10));  // 1：免费产出机=不吃料，出线上的沙它不收
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 0});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        handTick(ctx, c, 60); // 15 拍/周期 → 恰 4 周期 → 恰 4 沙
        long inStore = dep.count("minecraft:sand"), inBuf = c.bufAmount(1, "minecraft:sand"), inPool = c.legacyAmount("minecraft:sand");
        ctx.assertTrue(inStore == 4, "下游拒收的 4 件应全额落仓，现仓 " + inStore);
        ctx.assertTrue(inBuf == 0, "拒收就不该有东西留在下游缓存里，现缓存 " + inBuf);
        ctx.assertTrue(inStore + inBuf + inPool == 4, "进出账恒等式破了：产 4 ≠ 仓 " + inStore + "+缓存 " + inBuf + "+遗留 " + inPool);
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ③类型上限拒收 → 转默认路由零丢件；最后一站也没有 → **原样回吐**给调用方点折损黄灯，绝不静默吞。
     *  下游取 wither_killer（唯一双输入耗料机：soul_sand+wither_skeleton_skull），硬顶设 1 让第二种料必被拒。
     *  单元直驱 routeOut（同步设改+复位，无跨拍暴露窗）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void route_type_cap_rejection_loses_nothing(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));
        c.addNode(node("wither_killer", 20, 10)); // 吃 灵魂沙×4 + 凋骷头×3
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        com.sdzjz.config.SdzjzConfig cfg = com.sdzjz.config.SdzjzConfig.get();
        int old = cfg.maxBufferTypesPerNode;
        cfg.maxBufferTypesPerNode = 1; // 单节点缓存只许一种类型
        try {
            long l1 = c.routeOut(ctx.getLevel(), 0, dep, true, "minecraft:soul_sand", 10);
            ctx.assertTrue(l1 == 0 && c.bufAmount(1, "minecraft:soul_sand") == 10,
                    "第一种类型该正常入缓存，回吐 " + l1 + "、缓存 " + c.bufAmount(1, "minecraft:soul_sand"));
            long l2 = c.routeOut(ctx.getLevel(), 0, dep, true, "minecraft:wither_skeleton_skull", 7);
            ctx.assertTrue(c.bufAmount(1, "minecraft:wither_skeleton_skull") == 0, "超类型上限就不该进缓存");
            ctx.assertTrue(l2 == 0 && dep.count("minecraft:wither_skeleton_skull") == 7,
                    "被拒的 7 件该整额转默认路由落仓，回吐 " + l2 + "、仓 " + dep.count("minecraft:wither_skeleton_skull"));
            long l3 = c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:wither_skeleton_skull", 3);
            ctx.assertTrue(l3 == 3, "缓存拒收且无产出仓时应原样回吐 3（调用方点折损黄灯），实得 " + l3);
        } finally {
            cfg.maxBufferTypesPerNode = old;
        }
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ④摘节点：被摘节点的在途物品进遗留池不丢 + **剩余节点的缓存随下标左移不错位**
     *  （detachNode 少了「先补齐对齐」那行就会摘走别人的货，这条专盯它）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void detach_recycles_inflight_and_keeps_alignment(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        c.addNode(node("sand_maker", 10, 10));  // 0
        c.addNode(node("glass_kiln", 20, 10));  // 1：待摘，缓存 20
        c.addNode(node("glass_kiln", 30, 10));  // 2：留守，缓存 5，摘后应整体挪到下标 1
        ctx.assertTrue(c.connect(0, 1), "出线 0→1 应连得上");
        c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 20);
        ctx.assertTrue(c.disconnect(0, 1) && c.connect(0, 2), "改线 0→2 应成立");
        c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 5);
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 20 && c.bufAmount(2, "minecraft:sand") == 5, "两节点缓存预置该到位");
        c.detachNode(1);
        ctx.assertTrue(c.legacyAmount("minecraft:sand") == 20,
                "被摘节点的 20 件在途该进遗留池，实得 " + c.legacyAmount("minecraft:sand"));
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 5,
                "留守节点的缓存该随下标左移跟过来（原 2 → 现 1），实得 " + c.bufAmount(1, "minecraft:sand"));
        ctx.assertTrue(c.legacyAmount("minecraft:sand") + c.bufAmount(1, "minecraft:sand") == 25, "摘节点账不许缺斤少两");
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    // ===== m473（C2-⑤c2）：逻辑节点七分支成对判官（accepts 面经 routeOut 观测，chainWants 面直呼）=====

    private static void putFilterList(ItemStack s, String... ids) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (String id : ids) l.add(net.minecraft.nbt.StringTag.valueOf(id));
        s.getOrCreateTag().put("fl", l);
    }

    private static String rs(StructureCore120 c, int i) { // 判官报错带灯色+原因
        return c.g.nodeStatus.get(i) + "/" + c.g.nodeReason.get(i);
    }

    /** ①过滤器成对+端到端：白名单放行的走线到下游耗料机（sand_maker→过滤器→glass_kiln 全线绿），
     *  名单外 accepts 拒收（routeOut 原样回吐）、chainWants 同步说不要——同一张表两面各拍一下。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void pair_filter_passes_line_and_blocks_offlist(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));   // 0
        ItemStack filter = node("filter_node", 20, 10);
        putFilterList(filter, "minecraft:sand"); // 白名单只放沙
        c.addNode(filter);                        // 1
        c.addNode(node("glass_kiln", 30, 10));   // 2：只接产出仓
        ctx.assertTrue(c.connect(0, 1) && c.connect(1, 2), "两段出线应连得上");
        c.g.storageEdges.add(new long[]{2, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 0});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        // chainWants 面：过滤器"要"沙（下游窑吃沙）、不"要"泥土（名单拦）
        ctx.assertTrue(c.chainWants(ctx.getLevel(), 1, "minecraft:sand", 0, c.wantsScratchCleared()),
                "链式需求应认沙（过滤器放行+下游窑吃沙）");
        ctx.assertTrue(!c.chainWants(ctx.getLevel(), 1, "minecraft:dirt", 0, c.wantsScratchCleared()),
                "链式需求不该认泥土（白名单拦下）");
        // accepts 面：名单外投递整额回吐（无仓无去处，零吞件）
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:dirt", 5) == 5,
                "名单外物品应被过滤器拒收并原样回吐 5");
        ctx.succeedWhen(() -> {
            long glass = dep.count("minecraft:glass");
            ctx.assertTrue(glass >= 2, "白名单沙应穿过过滤器喂进窑产玻璃，现账 " + glass);
            ctx.assertTrue(c.g.nodeStatus.get(0) == 1 && c.g.nodeStatus.get(1) == 1 && c.g.nodeStatus.get(2) == 1,
                    "全线该绿灯，实得 " + rs(c, 0) + " | " + rs(c, 1) + " | " + rs(c, 2));
            com.sdzjz.machine.CoreScheduler.clearAll();
        });
    }

    /** ②开关成对+持料+闸门连锁：关=accepts 拒收/chainWants 不要/缓存持料黄灯/上游"下游闸门全关"不白产；
     *  开=直通转发一件不少。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void pair_switch_holds_when_off_forwards_when_on(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        c.addNode(node("sand_maker", 10, 10));   // 0
        ItemStack sw = node("switch_node", 20, 10);
        c.addNode(sw);                            // 1
        c.addNode(node("glass_kiln", 30, 10));   // 2
        ctx.assertTrue(c.connect(0, 1) && c.connect(1, 2), "两段出线应连得上");
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 6) == 0
                        && c.bufAmount(1, "minecraft:sand") == 6, "开着的开关应收下 6 件预置");
        sw.getOrCreateTag().putBoolean("so", false); // 关闸
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 4) == 4,
                "关着的开关应拒收并原样回吐 4");
        ctx.assertTrue(!c.chainWants(ctx.getLevel(), 1, "minecraft:sand", 0, c.wantsScratchCleared()),
                "关着的开关链式需求应说不要");
        handTick(ctx, c, 10);
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 6, "关闸持料：缓存该一件不动，实得 " + c.bufAmount(1, "minecraft:sand"));
        ctx.assertTrue(c.g.nodeStatus.get(1) == 2 && c.g.nodeReason.get(1).contains("开关已关"),
                "开关该黄灯说人话，实得 " + rs(c, 1));
        ctx.assertTrue(c.g.nodeStatus.get(0) == 2 && c.g.nodeReason.get(0).contains("闸门"),
                "上游该整台暂停不白产（下游闸门全关），实得 " + rs(c, 0));
        ctx.assertTrue(c.bufAmount(2, "minecraft:sand") == 0, "关闸期间一件都不许漏到下游");
        sw.getOrCreateTag().putBoolean("so", true); // 开闸
        ctx.assertTrue(c.chainWants(ctx.getLevel(), 1, "minecraft:sand", 0, c.wantsScratchCleared()),
                "开闸后链式需求应认沙");
        handTick(ctx, c, 10);
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 0 && c.bufAmount(2, "minecraft:sand") >= 1,
                "开闸后 6 件该直通下游（窑已开吃），开关余 " + c.bufAmount(1, "minecraft:sand")
                        + "、窑缓存 " + c.bufAmount(2, "minecraft:sand"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ③传感器成对（判定表两面**刻意不同**的那一格）：关闸时 accepts 拒收、tick 持料，
     *  但 chainWants **照样放行**（蓝本原注"闸门在下发阶段生效，需求判定直接放行"）；监测目标=
     *  自己的 kind1 供料线所指仓，阈值方向按 sl。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void pair_sensor_gates_accepts_but_not_wants(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 mon = storage(ctx, new BlockPos(1, 1, 0));
        if (mon == null) return;
        c.addNode(node("sand_maker", 10, 10));   // 0
        ItemStack se = node("sensor_node", 20, 10);
        se.getOrCreateTag().putString("si", "minecraft:iron_ingot");
        se.getOrCreateTag().putLong("sv", 10); // 默认 sl=低于阈值放行（补货型）
        c.addNode(se);                            // 1
        c.addNode(node("glass_kiln", 30, 10));   // 2
        ctx.assertTrue(c.connect(0, 1) && c.connect(1, 2), "两段出线应连得上");
        c.g.storageEdges.add(new long[]{1, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1}); // 传感器监测线（kind1）
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ctx.assertTrue(c.sensorOpen(ctx.getLevel(), 1), "监测仓 0<10 应开闸（补货型）");
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 6) == 0
                        && c.bufAmount(1, "minecraft:sand") == 6, "开闸传感器应收下 6 件预置");
        mon.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:iron_ingot")), 15));
        ctx.assertTrue(!c.sensorOpen(ctx.getLevel(), 1), "监测仓 15>=10 应关闸");
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 4) == 4,
                "关闸传感器 accepts 应拒收并原样回吐 4");
        ctx.assertTrue(c.chainWants(ctx.getLevel(), 1, "minecraft:sand", 0, c.wantsScratchCleared()),
                "关闸传感器 chainWants 应照样放行（蓝本：闸门在下发阶段生效）——两面刻意不同的那一格");
        handTick(ctx, c, 10);
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 6 && c.g.nodeStatus.get(1) == 2
                        && c.g.nodeReason.get(1).contains("传感器关闸"),
                "关闸持料黄灯，实得缓存 " + c.bufAmount(1, "minecraft:sand") + "、灯 " + rs(c, 1));
        mon.withdraw("minecraft:iron_ingot", 15); // 库存跌回阈下 → 自动复通
        handTick(ctx, c, 10);
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 0 && c.bufAmount(2, "minecraft:sand") >= 1,
                "复通后该直通转发，传感余 " + c.bufAmount(1, "minecraft:sand") + "、窑缓存 " + c.bufAmount(2, "minecraft:sand"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ④分配器均分（蓝本 distributeEven 三口径）：余数轮转（11→6/5）、非收方拿零、
     *  类型上限拒收的份额转默认路由零丢件、最后一站也没有→原样回吐。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void distributor_even_split_remainder_cap_reroute(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("distributor_node", 10, 10)); // 0
        c.addNode(node("glass_kiln", 20, 10));       // 1
        c.addNode(node("glass_kiln", 30, 10));       // 2
        c.addNode(node("cobble_maker", 40, 10));     // 3：免费产出机=不收，均分该拿零
        ctx.assertTrue(c.connect(0, 1) && c.connect(0, 2) && c.connect(0, 3), "三条出线应连得上");
        ctx.assertTrue(c.distributeEvenOut(ctx.getLevel(), 0, dep, true, "minecraft:sand", 11) == 0,
                "11 件在两个收方间该全额分完");
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 6 && c.bufAmount(2, "minecraft:sand") == 5
                        && c.bufAmount(3, "minecraft:sand") == 0,
                "均分该 6/5/0（余数轮转给前排），实得 " + c.bufAmount(1, "minecraft:sand") + "/"
                        + c.bufAmount(2, "minecraft:sand") + "/" + c.bufAmount(3, "minecraft:sand"));
        // 类型上限：wither_killer 双输入——先给 4 号占一位灵魂沙，硬顶 1 后凋骷头份额该转默认路由
        c.addNode(node("wither_killer", 50, 10)); // 4
        c.addNode(node("wither_killer", 60, 10)); // 5
        c.addNode(node("distributor_node", 70, 10)); // 6
        ctx.assertTrue(c.connect(6, 4) && c.connect(6, 5), "均分两目标应连得上");
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 6, null, true, "minecraft:soul_sand", 3) == 0
                && c.bufAmount(4, "minecraft:soul_sand") == 3, "4 号先占一个类型位");
        com.sdzjz.config.SdzjzConfig cfg = com.sdzjz.config.SdzjzConfig.get();
        int old = cfg.maxBufferTypesPerNode;
        cfg.maxBufferTypesPerNode = 1;
        try {
            ctx.assertTrue(c.distributeEvenOut(ctx.getLevel(), 6, dep, true, "minecraft:wither_skeleton_skull", 8) == 0,
                    "拒收份额有仓兜底该零回吐");
            ctx.assertTrue(c.bufAmount(4, "minecraft:wither_skeleton_skull") == 0
                            && c.bufAmount(5, "minecraft:wither_skeleton_skull") == 4
                            && dep.count("minecraft:wither_skeleton_skull") == 4,
                    "满型目标份额该整额转仓零丢件，实得 4号 " + c.bufAmount(4, "minecraft:wither_skeleton_skull")
                            + "/5号 " + c.bufAmount(5, "minecraft:wither_skeleton_skull")
                            + "/仓 " + dep.count("minecraft:wither_skeleton_skull"));
            ctx.assertTrue(c.distributeEvenOut(ctx.getLevel(), 6, null, true, "minecraft:wither_skeleton_skull", 3) == 3 - Math.min(3, 0)
                            ? c.distributeEvenOut(ctx.getLevel(), 6, null, true, "minecraft:blaze_rod", 3) == 3 : false,
                    "没人收且无仓时应原样回吐 3");
        } finally {
            cfg.maxBufferTypesPerNode = old;
        }
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ⑤垃圾桶成对+两轮垫底：有人收就轮不到桶（第一轮喂窑、桶拿零）；没人要的第二轮进桶，
     *  tick 吞光并把"已吞"真写进节点栈（m353 三段式）；m160 白名单桶名单外拒收回仓；
     *  chainWants 照蓝本无条件"想要"（授权语义，与 accepts 白名单面刻意不同）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void pair_trash_two_pass_eats_and_counts(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));   // 0
        c.addNode(node("glass_kiln", 20, 10));   // 1
        ItemStack trash = node("trash_node", 30, 10);
        c.addNode(trash);                         // 2
        ctx.assertTrue(c.connect(0, 1) && c.connect(0, 2), "两条出线应连得上");
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, dep, true, "minecraft:sand", 7) == 0,
                "沙该有去处");
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 7 && c.bufAmount(2, "minecraft:sand") == 0,
                "两轮垫底：有人收就轮不到桶，实得 窑 " + c.bufAmount(1, "minecraft:sand") + "/桶 " + c.bufAmount(2, "minecraft:sand"));
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, dep, true, "minecraft:dirt", 5) == 0
                        && c.bufAmount(2, "minecraft:dirt") == 5 && dep.count("minecraft:dirt") == 0,
                "没人要的第二轮该进桶（不落仓）");
        handTick(ctx, c, 5);
        ctx.assertTrue(c.bufAmount(2, "minecraft:dirt") == 0
                        && com.sdzjz.node.NodeTags.trashCount(c.g.machineNodes.get(2)) == 5,
                "桶该吞光并计账 tc=5，实得缓存 " + c.bufAmount(2, "minecraft:dirt")
                        + "/tc " + com.sdzjz.node.NodeTags.trashCount(c.g.machineNodes.get(2)));
        putFilterList(c.g.machineNodes.get(2), "minecraft:sand"); // m160 安全桶：只吞沙
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, dep, true, "minecraft:dirt", 3) == 0
                        && dep.count("minecraft:dirt") == 3 && c.bufAmount(2, "minecraft:dirt") == 0,
                "白名单外该拒收回仓零丢件");
        ctx.assertTrue(c.chainWants(ctx.getLevel(), 2, "minecraft:dirt", 0, c.wantsScratchCleared()),
                "桶的链式需求照蓝本无条件放行（授权语义，与 accepts 白名单面刻意不同的那一格）");
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ⑥暂停成对+最先判：暂停的耗料机 accepts 拒收、chainWants 不要；暂停的生成机整拍白灯黄话
     *  一件不产（early-continue 在预算/扣料之前，m99 教训）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void pair_paused_node_rejects_and_produces_nothing(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));   // 0
        c.addNode(node("glass_kiln", 20, 10));   // 1
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 0});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        c.g.machineNodes.get(1).getOrCreateTag().putBoolean("np", true); // 暂停下游窑
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 4) == 4,
                "暂停节点该拒收（上游走默认路由），实回吐应 4");
        ctx.assertTrue(!c.chainWants(ctx.getLevel(), 1, "minecraft:sand", 0, c.wantsScratchCleared()),
                "暂停节点链式需求应说不要");
        c.g.machineNodes.get(0).getOrCreateTag().putBoolean("np", true); // 暂停上游沙机
        handTick(ctx, c, 40);
        ctx.assertTrue(dep.count("minecraft:sand") == 0, "暂停生成机整拍一件不产（early-continue 在累积之前）");
        ctx.assertTrue(c.g.nodeStatus.get(0) == 2 && c.g.nodeReason.get(0).contains("暂停"),
                "暂停该黄灯说人话，实得 " + rs(c, 0));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ⑦持料守恒（世代取舍④判官）：过滤器拦下的货**无仓可去**时原样回灌自己缓存亮黄，一件不丢；
     *  补上名单+下游后自动续走清空。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void logic_leftovers_hold_in_place_zero_loss(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        c.addNode(node("sand_maker", 10, 10));   // 0
        ItemStack filter = node("filter_node", 20, 10); // 白名单空=全拦
        c.addNode(filter);                        // 1
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 9) == 9,
                "白名单空的过滤器该拒收（蓝本口径：空名单=全拦），回吐应 9");
        putFilterList(filter, "minecraft:sand");  // 放行沙后预置 9 件进过滤器
        ctx.assertTrue(c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 9) == 0
                        && c.bufAmount(1, "minecraft:sand") == 9, "预置该到位");
        handTick(ctx, c, 10); // 过滤器放行但没有下游也没有仓 → 原样回灌持料
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 9,
                "无处可去该整锅回灌零丢件，实得 " + c.bufAmount(1, "minecraft:sand"));
        ctx.assertTrue(c.g.nodeStatus.get(1) == 2 && c.g.nodeReason.get(1).contains("持料"),
                "该黄灯说持料，实得 " + rs(c, 1));
        c.addNode(node("glass_kiln", 30, 10));    // 2：补下游
        ctx.assertTrue(c.connect(1, 2), "补线应连得上");
        handTick(ctx, c, 10);
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 0 && c.bufAmount(2, "minecraft:sand") >= 1,
                "补下游后该自动续走，过滤余 " + c.bufAmount(1, "minecraft:sand") + "、窑缓存 " + c.bufAmount(2, "minecraft:sand"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ⑧闸门全关连锁（判别性口径：**连着产出仓也不许绕道白产**）——上游沙机同时接了绿线产出仓
     *  与一条通往关着的开关的出线，蓝本语义=整台暂停。只要有一个下游闸没关（补一台窑），立刻复工。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void gates_all_closed_stops_upstream_even_with_storage(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 dep = storage(ctx, new BlockPos(1, 1, 0));
        if (dep == null) return;
        c.addNode(node("sand_maker", 10, 10));   // 0：既有产出仓，又有一条出线通向关着的开关
        ItemStack sw = node("switch_node", 20, 10);
        sw.getOrCreateTag().putBoolean("so", false); // 关闸
        c.addNode(sw);                            // 1
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 0});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        handTick(ctx, c, 45); // 沙机 15 拍/周期：不设闸门连锁的话这里该有 3 件落仓
        ctx.assertTrue(dep.count("minecraft:sand") == 0,
                "下游闸门全关时上游整台停——有产出仓也不许绕道白产，实得落仓 " + dep.count("minecraft:sand"));
        ctx.assertTrue(c.g.nodeStatus.get(0) == 2 && c.g.nodeReason.get(0).contains("闸门"),
                "上游该黄灯说人话，实得 " + rs(c, 0));
        c.addNode(node("glass_kiln", 30, 10));    // 2：补一个没关闸的下游 → 不再是「全关」
        ctx.assertTrue(c.connect(0, 2), "补线应连得上");
        handTick(ctx, c, 45);
        ctx.assertTrue(c.bufAmount(2, "minecraft:sand") >= 1 || dep.count("minecraft:sand") >= 1,
                "有一个闸没关就该复工，窑缓存 " + c.bufAmount(2, "minecraft:sand") + "、仓 " + dep.count("minecraft:sand"));
        ctx.assertTrue(c.bufAmount(1, "minecraft:sand") == 0, "关着的开关一件都不该收到");
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    // ===== m475（C2-⑤c3）：链式拉料供料侧接线判官 =====

    /** ①**m132-6 血案的复现形**：仓 →(kind1 供料线)→ 过滤器 →(出线)→ 耗料机，中间那台过滤器
     *  必须**主动把料拉过来**再喂下去。m131b 当年只写了 accepts 那面，这条链恒不通拖了整整一刀；
     *  m473 把 chainWants 补齐、本刀（⑤c3）接上供料侧，这条链第一次在本世代跑通。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void chain_pull_storage_through_filter_into_consumer(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 src = storage(ctx, new BlockPos(1, 1, 0));   // 料仓（供给过滤器）
        StorageCore120 dep = storage(ctx, new BlockPos(2, 1, 0));   // 窑的产出仓
        if (src == null || dep == null) return;
        ItemStack filter = node("filter_node", 10, 10);
        putFilterList(filter, "minecraft:sand"); // 白名单只放沙
        c.addNode(filter);                        // 0
        c.addNode(node("glass_kiln", 20, 10));   // 1：吃沙出玻璃
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1}); // 仓→过滤器（kind1 供料）
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        c.g.storageEdges.add(new long[]{1, ctx.absolutePos(new BlockPos(2, 1, 0)).asLong(), 0}); // 窑→产出仓
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        src.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:sand")), 40));
        src.deposit(new ItemStack(net.minecraft.world.item.Items.DIRT, 30)); // 名单外：一件都不许被拉走
        handTick(ctx, c, 60);
        ctx.assertTrue(src.count("minecraft:sand") < 40, "过滤器该主动把沙拉过来（m132-6 复现形），仓里还剩 " + src.count("minecraft:sand"));
        ctx.assertTrue(src.count("minecraft:dirt") == 30, "白名单外的泥土一件都不该被拉走，实剩 " + src.count("minecraft:dirt"));
        ctx.assertTrue(dep.count("minecraft:glass") >= 1, "拉来的沙该喂进窑产出玻璃，现账 " + dep.count("minecraft:glass"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ②抽取节点=主动泵（m154）+ m157「没去处的不抽」+ m160 白名单：
     *  关着=一件不抽；开了但下游没人要且没接产出仓=还是不抽（不许把整仓吸进缓存囤着失踪）；
     *  接上产出仓=搬仓模式全抽；白名单外的永远碰都不碰。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void extractor_pump_respects_sink_and_whitelist(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 src = storage(ctx, new BlockPos(1, 1, 0));
        if (src == null) return;
        ItemStack x = node("extractor_node", 10, 10);
        putFilterList(x, "minecraft:sand"); // m160 抽取白名单：只碰沙
        c.addNode(x);                        // 0
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1}); // 仓→抽取（供料）
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        src.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:sand")), 100));
        src.deposit(new ItemStack(net.minecraft.world.item.Items.DIRT, 50));
        handTick(ctx, c, 10);
        ctx.assertTrue(src.count("minecraft:sand") == 100 && c.bufAmount(0, "minecraft:sand") == 0,
                "抽取节点默认关着（m154 点击才开始），一件都不该抽，仓剩 " + src.count("minecraft:sand"));
        x.getOrCreateTag().putBoolean("xo", true); // 开抽取
        handTick(ctx, c, 10);
        ctx.assertTrue(src.count("minecraft:sand") == 100 && c.bufAmount(0, "minecraft:sand") == 0,
                "开了但没去处（无出线无产出仓）也不该抽——m157 修的就是这个失踪案，实抽 " + c.bufAmount(0, "minecraft:sand"));
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 0}); // 补 kind0=搬仓模式
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        handTick(ctx, c, 10);
        ctx.assertTrue(src.count("minecraft:dirt") == 50, "白名单外的泥土永远碰都不碰，实剩 " + src.count("minecraft:dirt"));
        ctx.assertTrue(src.count("minecraft:sand") < 100, "搬仓模式该开抽，仓里还剩 " + src.count("minecraft:sand"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ③精确账本支路授权闸（m155→m158）：带 tag 的精确件**只在出线链通向垃圾桶时**才允许抽
     *  （抹组件=销毁语义无损）；链尾不是垃圾桶（换成玻璃窑）时一件都不许动。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void exact_pull_requires_trash_terminated_chain(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 src = storage(ctx, new BlockPos(1, 1, 0));
        if (src == null) return;
        c.addNode(node("filter_node", 10, 10));  // 0：白名单空=普通支路全拦，只留精确支路受检
        c.addNode(node("glass_kiln", 20, 10));   // 1：链尾**不是**垃圾桶
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        ItemStack exact = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD, 8);
        exact.getOrCreateTag().putInt("k", 1); // 带 tag → 走精确账本
        src.deposit(exact);
        ctx.assertTrue(src.exactTemplates().size() == 1, "精确件该进精确账本");
        handTick(ctx, c, 20);
        ctx.assertTrue(c.bufAmount(0, "minecraft:diamond_sword") == 0,
                "链尾不是垃圾桶时精确件一件都不许抽（抹组件必须先有销毁授权），实抽 "
                        + c.bufAmount(0, "minecraft:diamond_sword"));
        c.addNode(node("trash_node", 30, 10));   // 2：把链尾改成垃圾桶
        ctx.assertTrue(c.disconnect(0, 1) && c.connect(0, 2), "改线应成立");
        handTick(ctx, c, 20);
        ctx.assertTrue(src.exactTemplates().isEmpty() || src.exactCount(0) < 8,
                "链尾是垃圾桶时该获授权开抽（销毁语义），精确账剩 "
                        + (src.exactTemplates().isEmpty() ? 0 : src.exactCount(0)));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ④拉料侧守恒对账：仓里少的 = 节点缓存里多的 + 下游吃掉的，一件不许凭空生灭；
     *  且每种类型的在途量不超过 4096 封顶（m116）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void pull_conserves_items_and_respects_cap(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 src = storage(ctx, new BlockPos(1, 1, 0));
        if (src == null) return;
        ItemStack sw = node("switch_node", 10, 10); // 开关（开）：自身放行，无下游=链式需求不成立…
        c.addNode(sw);                               // 0
        c.addNode(node("glass_kiln", 20, 10));      // 1：给它一个真需求（吃沙）
        ctx.assertTrue(c.connect(0, 1), "出线应连得上");
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        // 一次塞 9000（deposit 只读 getCount，不受堆叠上限约束）——用来验每种 4096 封顶
        src.deposit(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("minecraft:sand")), 9000));
        long total = src.count("minecraft:sand");
        handTick(ctx, c, 30);
        long inStore = src.count("minecraft:sand");
        long inSw = c.bufAmount(0, "minecraft:sand"), inKiln = c.bufAmount(1, "minecraft:sand");
        long ate = total - inStore - inSw - inKiln; // 被窑吃掉的（已变成玻璃或在产）
        ctx.assertTrue(ate >= 0, "账不该穿：仓 " + inStore + "+开关 " + inSw + "+窑 " + inKiln + " > 总 " + total);
        ctx.assertTrue(inSw <= 4096, "每种在途该有 4096 封顶（m116），实得 " + inSw);
        ctx.assertTrue(inSw > 0 || inKiln > 0, "链式需求成立时该拉得到料");
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    // ===== m494（C2-⑤d2）：熔炉族——万能熔炉，接什么烧什么 =====

    /** ①供料线路径：仓里放铁矿 →(金线供料)→ 万能熔炉 →(绿线)→ 产出仓，应烧出铁锭；
     *  烧不了的东西（泥土）一件不动——**不做全局网络兜底**，也不乱烧仓里别的料。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void smelter_burns_from_supply_line(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 src = storage(ctx, new BlockPos(1, 1, 0));
        StorageCore120 dep = storage(ctx, new BlockPos(2, 1, 0));
        if (src == null || dep == null) return;
        c.addNode(node("super_smelter", 10, 10)); // 0
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1}); // 供料
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(2, 1, 0)).asLong(), 0}); // 产出
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        src.deposit(new ItemStack(Items.RAW_IRON, 30));
        src.deposit(new ItemStack(Items.DIRT, 20)); // 烧不了的：一件都不该动
        handTick(ctx, c, 60);
        ctx.assertTrue(dep.count("minecraft:iron_ingot") > 0,
                "万能熔炉该把粗铁烧成铁锭落产出仓，实得 " + dep.count("minecraft:iron_ingot"));
        ctx.assertTrue(src.count("minecraft:dirt") == 20, "烧不了的泥土一件都不该动，实剩 " + src.count("minecraft:dirt"));
        ctx.assertTrue(src.count("minecraft:raw_iron") < 30, "粗铁该被取走，实剩 " + src.count("minecraft:raw_iron"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** ②不接线不取料（防「把玩家存着的原木/圆石悄悄全烧了」）+ m149 白名单只烧选中的。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void smelter_needs_link_and_respects_whitelist(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        StorageCore120 src = storage(ctx, new BlockPos(1, 1, 0));
        if (src == null) return;
        ItemStack sm = node("super_smelter", 10, 10);
        c.addNode(sm); // 0：先什么线都不接
        src.deposit(new ItemStack(Items.RAW_IRON, 20));
        handTick(ctx, c, 30);
        ctx.assertTrue(src.count("minecraft:raw_iron") == 20,
                "没接供料线时一件都不该取（不做全局网络兜底），实剩 " + src.count("minecraft:raw_iron"));
        ctx.assertTrue(c.g.nodeStatus.get(0) == 3, "该红灯说「未接存储/供料线」，实得灯 " + c.g.nodeStatus.get(0));
        putFilterList(sm, "minecraft:raw_gold"); // m149 只烧金
        c.g.storageEdges.add(new long[]{0, ctx.absolutePos(new BlockPos(1, 1, 0)).asLong(), 1});
        c.g.storageEdgeDims.add(ctx.getLevel().dimension().location().toString());
        handTick(ctx, c, 30);
        ctx.assertTrue(src.count("minecraft:raw_iron") == 20,
                "白名单只勾了金，粗铁一件都不该烧，实剩 " + src.count("minecraft:raw_iron"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    // ===== m472（绞杀者第五刀）：NodeTags 上挂判官 =====

    /** m472 NodeTags 上挂：六族身份（def 引用同一性）各归各位 + defOf 对位 + 默认值口径不漂
     *  （开关默认开/暂停默认否/白名单空=全拦=蓝本同口径）+ 标签写读走 TagItemData 全链路真落栈。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void nodetags_mounts_on_retro_generation(GameTestHelper ctx) {
        ItemStack filter = node("filter_node", 0, 0);
        ItemStack trash = node("trash_node", 0, 0);
        ItemStack machine = node("cobble_maker", 0, 0);
        ctx.assertTrue(com.sdzjz.node.NodeTags.isFilter(filter)
                        && !com.sdzjz.node.NodeTags.isFilter(trash) && !com.sdzjz.node.NodeTags.isFilter(machine),
                "过滤器身份判定错位");
        ctx.assertTrue(com.sdzjz.node.NodeTags.isTrash(trash)
                        && com.sdzjz.node.NodeTags.isExtractor(node("extractor_node", 0, 0))
                        && com.sdzjz.node.NodeTags.isSensor(node("sensor_node", 0, 0))
                        && com.sdzjz.node.NodeTags.isSwitch(node("switch_node", 0, 0))
                        && com.sdzjz.node.NodeTags.isDistributor(node("distributor_node", 0, 0)),
                "六族身份判定应各归各位");
        ctx.assertTrue(com.sdzjz.node.NodeTags.defOf(machine) == com.sdzjz.machine.Machines.COBBLE_MAKER,
                "defOf 应回同一 def 常量对象（引用同一性）");
        ctx.assertTrue(com.sdzjz.node.NodeTags.defOf(new ItemStack(net.minecraft.world.item.Items.DIRT)) == null,
                "非机器族 defOf 应为 null");
        ctx.assertTrue(com.sdzjz.node.NodeTags.machineFilterable(node("super_smelter", 0, 0))
                        && !com.sdzjz.node.NodeTags.machineFilterable(machine),
                "machineFilterable 应熔炉族真/单产物机假");
        ctx.assertTrue(com.sdzjz.node.NodeTags.switchOn(node("switch_node", 0, 0))
                        && !com.sdzjz.node.NodeTags.nodePaused(filter),
                "默认值口径漂了（开关默认开/暂停默认否）");
        ctx.assertTrue(!com.sdzjz.node.NodeTags.filterPasses(filter, "minecraft:sand"),
                "过滤器白名单空应全拦（蓝本同口径）");
        com.sdzjz.node.NodeTags.addTrashCount(trash, 42); // 三段式写（copyOf→改→write）走 TagItemData
        ctx.assertTrue(com.sdzjz.node.NodeTags.trashCount(trash) == 42,
                "tc 写读应走通 ItemData 五口，实得 " + com.sdzjz.node.NodeTags.trashCount(trash));
        ctx.succeed();
    }

    /** ⑤在途缓存存档写读对拍（m468 风险③：nodeBufs 入 NBT=存档结构变更，写读必须同一刀做完）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void inflight_buffers_survive_save_load_roundtrip(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        c.addNode(node("sand_maker", 10, 10));
        c.addNode(node("glass_kiln", 20, 10));
        c.addNode(node("glass_kiln", 30, 10));
        ctx.assertTrue(c.connect(0, 2), "出线 0→2 应连得上");
        c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 7);
        c.detachNode(2);                                   // 遗留池 = 7
        ctx.assertTrue(c.connect(0, 1), "出线 0→1 应连得上");
        c.routeOut(ctx.getLevel(), 0, null, true, "minecraft:sand", 20); // 节点 1 缓存 = 20
        net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
        c.saveAdditional(nbt);
        BlockPos rel2 = new BlockPos(0, 1, 2);
        ctx.setBlock(rel2, RetroBlocks.STRUCTURE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel2) instanceof StructureCore120 c2)) {
            ctx.fail("第二个结构核心方块实体未生成");
            return;
        }
        c2.load(nbt);
        ctx.assertTrue(c2.g.machineNodes.size() == 2, "读档节点数该是 2，实得 " + c2.g.machineNodes.size());
        ctx.assertTrue(c2.bufAmount(1, "minecraft:sand") == 20,
                "节点在途缓存该逐位读回，实得 " + c2.bufAmount(1, "minecraft:sand"));
        ctx.assertTrue(c2.legacyAmount("minecraft:sand") == 7,
                "遗留池该逐位读回，实得 " + c2.legacyAmount("minecraft:sand"));
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

    /** m481（真移植 D 阶段先行·第二域）：路由域**跨代行为契约**——判官只此一份在 xplat
     *  （com.sdzjz.node.RouteDomainAssertions，六条成对判定），本用例喂 StructureCore120；
     *  主线 SdzjzGameTests 喂 StructureCoreBlockEntity 跑**同一套断言**。
     *  <p>最值钱的是「传感器关闸时两面刻意不同」那条：它长得像 bug，所以最容易在 C1 下沉时
     *  被顺手「改对」，一改就是 m132-6 血案重演——契约钉死它。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void route_domain_contract_retro(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        StructureCore120 c = canvas(ctx);
        var lvl = ctx.getLevel();
        try {
            // ① 过滤器（白名单=沙）
            ItemStack f = node("filter_node", 10, 10);
            putFilterList(f, "minecraft:sand");
            c.addNode(f);                                   // 0
            c.addNode(node("glass_kiln", 20, 10));          // 1：给过滤器一个真下游（吃沙）
            ctx.assertTrue(c.connect(0, 1), "出线应连得上");
            com.sdzjz.node.RouteDomainAssertions.过滤器(c, lvl, 0, "minecraft:sand", "minecraft:dirt");

            // ⑥ 通用耗料机
            com.sdzjz.node.RouteDomainAssertions.耗料机(c, lvl, 1, "minecraft:sand", "minecraft:dirt");

            // ② 开关（关→开）
            ItemStack sw = node("switch_node", 30, 10);
            sw.getOrCreateTag().putBoolean("so", false);
            c.addNode(sw);                                  // 2
            ctx.assertTrue(c.connect(2, 1), "开关出线应连得上");
            com.sdzjz.node.RouteDomainAssertions.开关关着(c, lvl, 2, "minecraft:sand");
            sw.getOrCreateTag().putBoolean("so", true);
            com.sdzjz.node.RouteDomainAssertions.开关开着(c, lvl, 2, "minecraft:sand");

            // ③ 传感器：未连监测仓=按 0 计（世代取舍），溢出型 → 关闸
            ItemStack se = node("sensor_node", 40, 10);
            se.getOrCreateTag().putString("si", "minecraft:iron_ingot");
            se.getOrCreateTag().putLong("sv", 10);
            se.getOrCreateTag().putBoolean("sl", false); // 溢出型：库存 > 阈值才开；未连线按 0 计 → 关闸
            c.addNode(se);                                  // 3
            ctx.assertTrue(c.connect(3, 1), "传感器出线应连得上");
            com.sdzjz.node.RouteDomainAssertions.传感器关闸时两面刻意不同(c, lvl, 3, "minecraft:sand");
            se.getOrCreateTag().putBoolean("sl", true);  // 补货型：库存 < 阈值就开 → 开闸
            com.sdzjz.node.RouteDomainAssertions.传感器开闸时两面放行(c, lvl, 3, "minecraft:sand");

            // ④ 暂停
            ItemStack pz = node("filter_node", 50, 10);
            putFilterList(pz, "minecraft:sand");
            pz.getOrCreateTag().putBoolean("np", true);
            c.addNode(pz);                                  // 4
            ctx.assertTrue(c.connect(4, 1), "暂停节点出线应连得上");
            com.sdzjz.node.RouteDomainAssertions.暂停(c, lvl, 4, "minecraft:sand");

            // ⑤ 垃圾桶（安全白名单=沙）
            ItemStack tr = node("trash_node", 60, 10);
            putFilterList(tr, "minecraft:sand");
            c.addNode(tr);                                  // 5
            com.sdzjz.node.RouteDomainAssertions.垃圾桶授权语义(c, lvl, 5, "minecraft:sand", "minecraft:dirt");

            // 抽取启停
            ItemStack xOff = node("extractor_node", 70, 10);
            ItemStack xOn = node("extractor_node", 71, 10);
            xOn.getOrCreateTag().putBoolean("xo", true);
            c.addNode(xOff);                                // 6
            com.sdzjz.node.RouteDomainAssertions.抽取启停(c, lvl, 6, xOff, xOn);
        } catch (AssertionError e) {
            com.sdzjz.machine.CoreScheduler.clearAll();
            ctx.fail("路由域契约失败: " + e.getMessage());
            return;
        }
        com.sdzjz.machine.CoreScheduler.clearAll();
        ctx.succeed();
    }

}
