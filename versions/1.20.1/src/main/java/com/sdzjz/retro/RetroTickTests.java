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
}
