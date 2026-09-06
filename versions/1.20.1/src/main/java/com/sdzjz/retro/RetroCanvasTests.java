package com.sdzjz.retro;

import com.sdzjz.node.CanvasGraphState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * m454 刀③行为判官：画布 BE 骨架——摘除簿记（剪线/移位，蓝本 detachNode 段对拍）与图状态
 * NBT 往返（键同布局：machineNodes/connections/groups/nodeStat/nodeWhy/storEdges/prodPM）。
 */
public final class RetroCanvasTests implements FabricGameTest {

    private static StructureCore120 canvas(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.STRUCTURE_CORE.defaultBlockState());
        if (ctx.getBlockEntity(rel) instanceof StructureCore120 c) return c;
        ctx.fail("结构核心方块实体未生成");
        return null;
    }

    /** 机器节点栈：随栈 NBT 带画布坐标（xc/yc 口径，NodeTags 谱系）。 */
    private static ItemStack node(String machineId, int xc, int yc) {
        ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("sdzjz", machineId)));
        s.getOrCreateTag().putInt("nx", xc); // m474 键位归位（同 RetroTickTests）
        s.getOrCreateTag().putInt("ny", yc);
        return s;
    }

    /** 摘中间节点：触删连线断、大于下标左移；同向重复连线拒绝；断线精确匹配。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_detach_middle_node_shifts_connections(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("wither_farm", 10, 10));
        c.addNode(node("auto_crafter", 20, 10));
        c.addNode(node("super_smelter", 30, 10));
        ctx.assertTrue(c.connect(0, 1) && c.connect(1, 2) && c.connect(0, 2), "三条连线应全成立");
        ctx.assertTrue(!c.connect(0, 1), "同向重复连线应拒绝");
        ctx.assertTrue(!c.connect(0, 0), "自连应拒绝");
        ItemStack removed = c.detachNode(1);
        ctx.assertTrue(!removed.isEmpty() && removed.getTag().getInt("nx") == 20, "摘回的应是中间节点（xc=20）");
        ctx.assertTrue(c.g.machineNodes.size() == 2 && c.g.nodeStatus.size() == 2 && c.g.nodeReason.size() == 2,
                "三表应同步缩为 2");
        ctx.assertTrue(c.g.connections.size() == 1, "触删两条应断，仅存 0→2 重映射，实得 " + c.g.connections.size());
        int[] only = c.g.connections.get(0);
        ctx.assertTrue(only[0] == 0 && only[1] == 1, "0→2 应重映射为 0→1，实得 " + only[0] + "→" + only[1]);
        ctx.assertTrue(c.disconnect(0, 1) && c.g.connections.isEmpty(), "断线精确匹配后应清空");
        ctx.succeed();
    }

    /** 图状态 NBT 往返：节点栈 tag 原样（xc/yc）、连线/分组/状态灯/原因/存储线/产量逐键对账。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_graph_nbt_roundtrip(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("wither_farm", 5, 7));
        c.addNode(node("brewing_tower", 15, 7));
        c.connect(0, 1);
        c.g.groupNames.put(3, "产线甲");
        c.g.nodeStatus.set(0, 2);
        c.g.nodeReason.set(0, "测试黄灯");
        c.g.storageEdges.add(new long[]{1, 12345L, 1});
        c.g.storageEdgeDims.add("minecraft:overworld");
        c.g.busTopIds.add("minecraft:cobblestone");
        c.g.busTopCounts.add(99L);
        c.g.prodPerMin = 42;
        CompoundTag saved = c.saveWithoutMetadata();
        BlockPos rel2 = new BlockPos(2, 1, 0);
        ctx.setBlock(rel2, RetroBlocks.STRUCTURE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel2) instanceof StructureCore120 c2)) { ctx.fail("对账用第二核心未生成"); return; }
        c2.load(saved);
        ctx.assertTrue(c2.g.machineNodes.size() == 2, "节点数往返应=2");
        ctx.assertTrue(ItemStack.isSameItemSameTags(c2.g.machineNodes.get(0), c.g.machineNodes.get(0)),
                "节点栈 tag（画布坐标）往返不得漂移");
        ctx.assertTrue(c2.g.connections.size() == 1 && c2.g.connections.get(0)[0] == 0 && c2.g.connections.get(0)[1] == 1,
                "连线往返应=0→1");
        ctx.assertTrue("产线甲".equals(c2.g.groupNames.get(3)), "分组名往返不得漂移");
        ctx.assertTrue(c2.g.nodeStatus.get(0) == 2 && "测试黄灯".equals(c2.g.nodeReason.get(0)), "状态灯与原因应同回");
        ctx.assertTrue(c2.g.storageEdges.size() == 1 && c2.g.storageEdges.get(0)[1] == 12345L
                && "minecraft:overworld".equals(c2.g.storageEdgeDims.get(0)), "存储连线应同回");
        ctx.assertTrue(c2.g.busTopCounts.get(0) == 99L && c2.g.prodPerMin == 42, "总线库存与产量应同回");
        ctx.succeed();
    }

    /** m456：快照包编解码往返对偶 + 服务端 handleQuery 前验（未开菜单静默丢不回声）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_snapshot_payload_roundtrip_and_guard(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("wither_farm", 8, 9));
        c.addNode(node("super_smelter", 40, 9));
        c.connect(0, 1);
        CompoundTag render = new CompoundTag();
        c.g.writeRenderNbt(render, null);
        var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); // 编→解全走真 buf
        new CanvasPayloads120.CanvasSnapshot(ctx.absolutePos(new BlockPos(0, 1, 0)), render).write(out);
        var back = new CanvasPayloads120.CanvasSnapshot(out);
        CanvasGraphState gg = new CanvasGraphState();
        gg.readRenderNbt(back.render(), null, java.util.Map.of(), () -> { });
        ctx.assertTrue(gg.machineNodes.size() == 2 && gg.connections.size() == 1,
                "快照包往返应保 2 节点 1 连线，实得 " + gg.machineNodes.size() + "/" + gg.connections.size());
        ctx.assertTrue(ItemStack.isSameItemSameTags(gg.machineNodes.get(0), c.g.machineNodes.get(0)),
                "节点栈 tag 过包不得漂移");
        ctx.assertTrue(gg.machineNodes.size() <= CanvasPayloads120.MAX_NODES, "应用层硬顶常量在位");
        ctx.succeed();
    }

    /** m457：放置操作核——槽内非机器拒/生存扣 1 创造不扣/坐标钳位/节点带 xc yc 挂上。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_add_from_slot_consumes_and_validates(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        var p = ctx.makeMockPlayer();
        ItemStack machine = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("sdzjz", "wither_farm")), 2);
        p.getInventory().setItem(0, machine);
        p.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.STONE, 5));
        ctx.assertTrue(!StructureCoreMenu120.addFromSlot(c, p.getInventory(), false, 1, 0, 0), "非机器物品应拒");
        ctx.assertTrue(!StructureCoreMenu120.addFromSlot(c, p.getInventory(), false, 99, 0, 0), "越界槽应拒");
        ctx.assertTrue(StructureCoreMenu120.addFromSlot(c, p.getInventory(), false, 0, 33, 44), "生存放置应成立");
        ctx.assertTrue(p.getInventory().getItem(0).getCount() == 1, "生存应扣 1，余 " + p.getInventory().getItem(0).getCount());
        ctx.assertTrue(StructureCoreMenu120.addFromSlot(c, p.getInventory(), true, 0, 500_000, -500_000), "创造放置应成立");
        ctx.assertTrue(p.getInventory().getItem(0).getCount() == 1, "创造不扣");
        ctx.assertTrue(c.g.machineNodes.size() == 2, "画布应挂 2 节点");
        ctx.assertTrue(c.g.machineNodes.get(0).getTag().getInt("nx") == 33
                && c.g.machineNodes.get(0).getTag().getInt("ny") == 44, "首节点坐标应=33,44");
        ctx.assertTrue(c.g.machineNodes.get(1).getTag().getInt("nx") == 100_000
                && c.g.machineNodes.get(1).getTag().getInt("ny") == -100_000, "天量坐标应被钳位到 ±100000");
        ctx.succeed();
    }

    /** m457：摘回操作核——洗净变裸（剥 xc/yc/gp 空 tag 置 null，与新件混堆）+回背包+画布同缩。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_remove_returns_clean_bare_item(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        ItemStack node = node("brewing_tower", 66, 77);
        node.getTag().putInt("gp", 3); // 分组归属也须剥
        c.addNode(node);
        var p = ctx.makeMockPlayer();
        ctx.assertTrue(StructureCoreMenu120.removeToInventory(c, p.getInventory(), 0), "摘回应成立");
        ctx.assertTrue(c.g.machineNodes.isEmpty(), "画布应清空");
        ItemStack got = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) { got = p.getInventory().getItem(i); break; }
        ctx.assertTrue(!got.isEmpty() && !got.hasTag(), "回手的应为裸件（洗净变裸，与新件混堆）");
        ctx.assertTrue(ItemStack.isSameItemSameTags(got, new ItemStack(got.getItem())), "裸件身份应与新件一致");
        ctx.succeed();
    }

    /** m458：端点扫描——BFS 可达存储核心成端点+自动停靠；拆核心重扫=端点/停靠位/连线三连坐清理。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_endpoint_scan_and_prune(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        ctx.setBlock(new BlockPos(1, 1, 0), RetroBlocks.DATA_CABLE.defaultBlockState());
        BlockPos scRel = new BlockPos(2, 1, 0);
        ctx.setBlock(scRel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        long pl = ctx.absolutePos(scRel).asLong();
        StructureCoreMenu120.refreshEndpoints(c, ctx.getLevel(), true);
        ctx.assertTrue(c.g.storageEndpoints.size() == 1 && c.g.storageEndpoints.get(0)[0] == pl,
                "隔一根线应扫到 1 端点，实得 " + c.g.storageEndpoints.size());
        ctx.assertTrue(c.g.storageNodePos.containsKey(pl), "新端点应自动停靠");
        c.addNode(node("wither_farm", 10, 10));
        ctx.assertTrue(StructureCoreMenu120.storageLinkCycle(c, 0, pl, "minecraft:overworld") == 0, "首触应=产出(0)");
        ctx.setBlock(scRel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); // 拆核心
        StructureCoreMenu120.refreshEndpoints(c, ctx.getLevel(), true);
        ctx.assertTrue(c.g.storageEndpoints.isEmpty() && c.g.storageNodePos.isEmpty() && c.g.storageEdges.isEmpty(),
                "拆核心重扫应三连坐清理，实得 " + c.g.storageEndpoints.size() + "/" + c.g.storageNodePos.size() + "/" + c.g.storageEdges.size());
        ctx.succeed();
    }

    /** m458：连线循环——无→产出(0)→供料(1)→断(-1)；越界机器与不在场端点拒(-2)。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_storage_link_cycles(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        ctx.setBlock(new BlockPos(1, 1, 0), RetroBlocks.STORAGE_CORE.defaultBlockState());
        long pl = ctx.absolutePos(new BlockPos(1, 1, 0)).asLong();
        StructureCoreMenu120.refreshEndpoints(c, ctx.getLevel(), true);
        c.addNode(node("auto_crafter", 5, 5));
        ctx.assertTrue(StructureCoreMenu120.storageLinkCycle(c, 7, pl, "d") == -2, "越界机器应拒");
        ctx.assertTrue(StructureCoreMenu120.storageLinkCycle(c, 0, 12345L, "d") == -2, "不在场端点应拒");
        ctx.assertTrue(StructureCoreMenu120.storageLinkCycle(c, 0, pl, "d") == 0, "第一触=产出");
        ctx.assertTrue(c.g.storageEdges.size() == 1 && c.g.storageEdges.get(0)[2] == 0, "边应=产出");
        ctx.assertTrue(StructureCoreMenu120.storageLinkCycle(c, 0, pl, "d") == 1, "第二触=供料");
        ctx.assertTrue(c.g.storageEdges.get(0)[2] == 1, "边应翻供料");
        ctx.assertTrue(StructureCoreMenu120.storageLinkCycle(c, 0, pl, "d") == -1, "第三触=断");
        ctx.assertTrue(c.g.storageEdges.isEmpty() && c.g.storageEdgeDims.isEmpty(), "边与维度表应同清");
        ctx.succeed();
    }

    /** m459 修④：坏档/恶意快照的越界、自连、坏方向条目读侧即剪——不得进 detach 簿记与生产 tick。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_read_prunes_corrupt_indices(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("wither_farm", 1, 1));
        c.addNode(node("auto_crafter", 2, 2));
        CompoundTag nbt = new CompoundTag();
        c.g.writeRenderNbt(nbt, null);
        nbt.putIntArray("connections", new int[]{0, 1, 7, 9, 1, 1, -1, 0}); // 好1条+越界+自连+负
        var seg = nbt.getList("storEdges", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag bad = new CompoundTag(); bad.putInt("m", 99); bad.putLong("p", 1L); bad.putInt("r", 0); bad.putString("d", "d");
        CompoundTag badDir = new CompoundTag(); badDir.putInt("m", 0); badDir.putLong("p", 2L); badDir.putInt("r", 7); badDir.putString("d", "d");
        CompoundTag good = new CompoundTag(); good.putInt("m", 1); good.putLong("p", 3L); good.putInt("r", 1); good.putString("d", "d");
        seg.add(bad); seg.add(badDir); seg.add(good);
        nbt.put("storEdges", seg);
        CanvasGraphState gg = new CanvasGraphState();
        gg.readRenderNbt(nbt, null, java.util.Map.of(), () -> { });
        ctx.assertTrue(gg.connections.size() == 1 && gg.connections.get(0)[0] == 0 && gg.connections.get(0)[1] == 1,
                "连线应只剩好的 0→1，实得 " + gg.connections.size());
        ctx.assertTrue(gg.storageEdges.size() == 1 && gg.storageEdges.get(0)[0] == 1 && gg.storageEdges.get(0)[2] == 1,
                "存储边应只剩好的 (1,p3,供料)，实得 " + gg.storageEdges.size());
        ctx.assertTrue(gg.storageEdgeDims.size() == 1, "维度表应同长");
        ctx.succeed();
    }

    // ===== m474：画布坐标键位归位（xc/yc → nx/ny）与旧档自愈 =====

    /** m474①旧档自愈：史前存档（坐标写在 xc/yc）读进来后应逐节点搬到 nx/ny、旧键清干净、
     *  坐标值一位不差；已是新档的节点一个字不动（新键在场=不搬）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void legacy_coord_keys_heal_on_load(GameTestHelper ctx) {
        StructureCore120 src = canvas(ctx);
        src.addNode(node("cobble_maker", 11, 22));  // 0：新档写法（nx/ny）
        src.addNode(node("sand_maker", 33, 44));    // 1：下面改造成史前写法
        CompoundTag nbt = new CompoundTag();
        src.saveAdditional(nbt);
        // 把节点 1 的存档栈改回史前键位（模拟 m457~m473 的旧档）
        var list = nbt.getList("machineNodes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag stackTag = list.getCompound(1).getCompound("tag");
        stackTag.putInt("xc", stackTag.getInt("nx"));
        stackTag.putInt("yc", stackTag.getInt("ny"));
        stackTag.remove("nx");
        stackTag.remove("ny");
        BlockPos rel2 = new BlockPos(0, 1, 2);
        ctx.setBlock(rel2, RetroBlocks.STRUCTURE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel2) instanceof StructureCore120 c2)) {
            ctx.fail("第二个结构核心方块实体未生成");
            return;
        }
        c2.load(nbt);
        ctx.assertTrue(c2.g.machineNodes.size() == 2, "读档节点数该是 2，实得 " + c2.g.machineNodes.size());
        CompoundTag t1 = c2.g.machineNodes.get(1).getTag();
        ctx.assertTrue(t1 != null && t1.getInt("nx") == 33 && t1.getInt("ny") == 44,
                "史前节点坐标该自愈到 nx/ny=33,44，实得 " + (t1 == null ? "无 tag" : t1.getInt("nx") + "," + t1.getInt("ny")));
        ctx.assertTrue(!t1.contains("xc") && !t1.contains("yc"),
                "自愈后旧键该清干净（xc 撞 NodeTags 抽取累计，留着就是定时炸弹）");
        CompoundTag t0 = c2.g.machineNodes.get(0).getTag();
        ctx.assertTrue(t0 != null && t0.getInt("nx") == 11 && t0.getInt("ny") == 22, "新档节点该原样不动");
        ctx.succeed();
    }

    /** m474②同键异义收口：节点摆在画布上（nx 有值）时，NodeTags 的抽取累计读数必须是 0——
     *  改键前这里读的是画布 X 坐标（"已抽取 320 件"的鬼数字），改键后两者彻底不相干。
     *  反向也测一次：写抽取累计不该动坐标。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void canvas_coords_do_not_pollute_extractor_count(GameTestHelper ctx) {
        net.minecraft.world.item.ItemStack x = node("extractor_node", 320, 200);
        ctx.assertTrue(com.sdzjz.node.NodeTags.extractorCount(x) == 0,
                "画布坐标不该被当成抽取累计，实得 " + com.sdzjz.node.NodeTags.extractorCount(x));
        x.getOrCreateTag().putLong("xc", 777); // 模拟 ⑤c3 抽取泵写累计
        ctx.assertTrue(x.getTag().getInt("nx") == 320 && x.getTag().getInt("ny") == 200,
                "写抽取累计不该把节点弹飞，实得 " + x.getTag().getInt("nx") + "," + x.getTag().getInt("ny"));
        ctx.assertTrue(com.sdzjz.node.NodeTags.extractorCount(x) == 777, "抽取累计该读回自己的键");
        ctx.succeed();
    }

    // ===== m506（真移植 A5a）：画布分组服务端下沉 =====

    /** m506①分组域跨代行为契约——与主线 SdzjzGameTests.canvas_group_domain_contract 跑的是**同一套断言**
     *  （xplat CanvasGroupAssertions），本侧喂 tag 栈（TagItemData/RetroNodeIdent），两代同绿=分组六方法
     *  在两套栈数据实现上确实是同一个东西。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_group_domain_contract_retro(GameTestHelper ctx) {
        int[] changes = {0};
        CanvasGraphState g = new CanvasGraphState(() -> changes[0]++);
        g.machineNodes.add(node("wither_farm", 10, 10));
        g.machineNodes.add(node("auto_crafter", 20, 10));
        g.machineNodes.add(node("super_smelter", 30, 10));
        g.machineNodes.add(node("brewing_tower", 40, 10));
        try {
            com.sdzjz.node.CanvasGroupAssertions.runAll(g, changes);
        } catch (AssertionError e) {
            ctx.fail("分组域契约失败: " + e.getMessage());
            return;
        }
        ctx.succeed();
    }

    /** m506②接收器操作核分派（一包三义，主线 Sdzjz m191 接收器原文）+ BE 侧 detachNode 顺手清扫
     *  （摘走成员后组剩 1 台该自动解散，与主线 detachNode 同位同句）+ 分组随存档往返。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_group_ops_dispatch_and_detach_sweep(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("wither_farm", 10, 10));
        c.addNode(node("auto_crafter", 20, 10));
        c.addNode(node("super_smelter", 30, 10));
        StructureCoreMenu120.groupOp(c, -1, "", java.util.List.of(0, 1, 2));           // 建组
        ctx.assertTrue(c.g.groupNames.size() == 1 && "组1".equals(c.g.groupNames.get(1)), "gid<0 该走建组，实得 " + c.g.groupNames);
        StructureCoreMenu120.groupOp(c, 1, "刷怪线", java.util.List.of());                // 重命名
        ctx.assertTrue("刷怪线".equals(c.g.groupNames.get(1)), "name 非空该走重命名，实得 " + c.g.groupNames.get(1));
        CompoundTag nbt = c.saveWithoutMetadata();                                       // 分组随存档往返（groups 键 + 栈上 gp，键在 BE 根层）
        CanvasGraphState back = new CanvasGraphState();
        back.readRenderNbt(nbt, null, java.util.Map.of(), () -> { });
        ctx.assertTrue("刷怪线".equals(back.groupNames.get(1))
                && com.sdzjz.node.NodeTags.nodeGroup(back.machineNodes.get(2)) == 1, "组名与成员标记该随存档往返");
        c.detachNode(2);                                                                // 摘 1 台：剩 2 台仍成组
        ctx.assertTrue(c.g.groupNames.containsKey(1) && com.sdzjz.node.NodeTags.nodeGroup(c.g.machineNodes.get(1)) == 1,
                "摘走 1 台还剩 2 台，组该保留");
        c.detachNode(1);                                                                // 再摘：只剩 1 台 → 顺手清
        ctx.assertTrue(c.g.groupNames.isEmpty(), "组只剩 1 台该被 detachNode 顺手解散，实得 " + c.g.groupNames);
        ctx.assertTrue(com.sdzjz.node.NodeTags.nodeGroup(c.g.machineNodes.get(0)) == -1, "被解散组的孤儿该剥 gp");
        c.addNode(node("brewing_tower", 40, 10));
        StructureCoreMenu120.groupOp(c, -1, "", java.util.List.of(0, 1));
        StructureCoreMenu120.groupOp(c, 1, "", java.util.List.of());                     // 解散
        ctx.assertTrue(c.g.groupNames.isEmpty() && com.sdzjz.node.NodeTags.nodeGroup(c.g.machineNodes.get(0)) == -1,
                "gid>=0 且 name 空该走解散，实得 " + c.g.groupNames);
        ctx.succeed();
    }

    /** m506③分组两包编解码往返 + 有界解码红线（m291 对位）：声明超顶的成员表、超长组名在解码期即拒，
     *  不分配后再裁。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_group_payloads_roundtrip_and_bounds(GameTestHelper ctx) {
        BlockPos pos = ctx.absolutePos(new BlockPos(0, 1, 0));
        var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodeGroup(pos, -1, "产线甲", java.util.List.of(0, 1, 2)).write(out);
        var back = new NodePayloads120.NodeGroup(out);
        ctx.assertTrue(back.pos().equals(pos) && back.gid() == -1 && "产线甲".equals(back.name())
                && back.members().equals(java.util.List.of(0, 1, 2)), "NodeGroup 往返四字段该逐位一致");
        var out2 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodeGroupMove(pos, 3, -5, 7).write(out2);
        var back2 = new NodePayloads120.NodeGroupMove(out2);
        ctx.assertTrue(back2.gid() == 3 && back2.dx() == -5 && back2.dy() == 7, "NodeGroupMove 往返该逐位一致");
        // 有界红线①：成员表声明条数超顶 → 分配前拒
        var evil = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        evil.writeBlockPos(pos); evil.writeVarInt(-1); evil.writeUtf("x", 64); evil.writeVarInt(NodePayloads120.GROUP_MEMBERS_MAX + 1);
        boolean rejected = false;
        try { new NodePayloads120.NodeGroup(evil); } catch (io.netty.handler.codec.DecoderException e) { rejected = true; }
        ctx.assertTrue(rejected, "成员表声明 " + (NodePayloads120.GROUP_MEMBERS_MAX + 1) + " 条该在解码期拒收");
        // 有界红线②：组名超长 → 解码期拒（原版 readUtf(max) 自抛）
        var evil2 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        evil2.writeBlockPos(pos); evil2.writeVarInt(1); evil2.writeUtf("a".repeat(200)); evil2.writeVarInt(0);
        boolean rejected2 = false;
        try { new NodePayloads120.NodeGroup(evil2); } catch (io.netty.handler.codec.DecoderException e) { rejected2 = true; }
        ctx.assertTrue(rejected2, "组名 200 字该在解码期拒收（上限 " + NodePayloads120.GROUP_NAME_MAX + "）");
        ctx.succeed();
    }

    /** m541（真移植·1.20.1 结构核心补全第一刀）：节点配置五操作（与主线同一份 xplat NodeConfig）——暂停翻转 /
     *  开关·抽取同口翻转且普通机器不响应 / 过滤名单加移·黑白切换·#xr 五挡换挡·64 封顶 / 传感器写三键·阈值钳 1e12·§clear·
     *  普通机器拒 / 自动合成机目标写 ct 且普通机器拒；外加五包编解码往返与有界解码红线（m291 对位）。
     *  **本世代宿主口默认关**：区块族哨兵 #zy 在本世代必须"落到通用名单段被拒"而不是抛异常。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_node_config_five_ops_and_payload_bounds(GameTestHelper ctx) {
        StructureCore120 c = canvas(ctx);
        c.addNode(node("cobble_maker", 10, 10));   // 0 普通机器
        c.addNode(node("switch_node", 20, 10));    // 1
        c.addNode(node("extractor_node", 30, 10)); // 2
        c.addNode(node("filter_node", 40, 10));    // 3
        c.addNode(node("sensor_node", 50, 10));    // 4
        c.addNode(node("auto_crafter", 60, 10));   // 5
        java.util.function.IntFunction<ItemStack> n = i -> c.g.machineNodes.get(i); // m510 教训：addNode 存副本，读图里那份
        // ① 暂停
        c.togglePause(0);
        ctx.assertTrue(com.sdzjz.node.NodeTags.nodePaused(n.apply(0)), "togglePause 该写 np=true");
        c.togglePause(0);
        ctx.assertTrue(!com.sdzjz.node.NodeTags.nodePaused(n.apply(0)), "再切该回 false");
        // ② 开关 / 抽取启停同口（m154）；普通机器不响应
        boolean so0 = com.sdzjz.node.NodeTags.switchOn(n.apply(1));
        c.toggleSwitch(1);
        ctx.assertTrue(com.sdzjz.node.NodeTags.switchOn(n.apply(1)) != so0, "开关节点 so 该翻转");
        boolean xo0 = com.sdzjz.node.NodeTags.extractorOn(n.apply(2));
        c.toggleSwitch(2);
        ctx.assertTrue(com.sdzjz.node.NodeTags.extractorOn(n.apply(2)) != xo0, "抽取节点 xo 该翻转（开关包同口）");
        c.toggleSwitch(0);
        ctx.assertTrue(!com.sdzjz.node.NodeTags.viewOf(n.apply(0)).contains("so") && !com.sdzjz.node.NodeTags.viewOf(n.apply(0)).contains("xo"),
                "普通机器不该响应开关包");
        // ③ 过滤名单：加→在；再加→移；空串=黑白切换；#xr 换挡（默认 512→4096）；名单封顶 64；普通机器不响应；#zy 哨兵本世代默认关=落到名单段被拒
        c.toggleFilterEntry(3, "minecraft:sand");
        ctx.assertTrue(com.sdzjz.node.NodeTags.filterList(n.apply(3)).contains("minecraft:sand"), "名单该加入 sand");
        c.toggleFilterEntry(3, "minecraft:sand");
        ctx.assertTrue(!com.sdzjz.node.NodeTags.filterList(n.apply(3)).contains("minecraft:sand"), "再点该移除 sand");
        boolean fb0 = com.sdzjz.node.NodeTags.filterBlacklist(n.apply(3));
        c.toggleFilterEntry(3, "");
        ctx.assertTrue(com.sdzjz.node.NodeTags.filterBlacklist(n.apply(3)) != fb0, "空串该切黑白名单");
        long r0 = com.sdzjz.node.NodeTags.extractorRate(n.apply(2));
        c.toggleFilterEntry(2, "#xr");
        long r1 = com.sdzjz.node.NodeTags.extractorRate(n.apply(2));
        ctx.assertTrue(r0 == 512 && r1 == 4096, "#xr 该从默认 512 换到 4096，实得 " + r0 + "→" + r1);
        for (int k = 0; k < 70; k++) c.toggleFilterEntry(3, "minecraft:x" + k);
        ctx.assertTrue(com.sdzjz.node.NodeTags.filterList(n.apply(3)).size() == 64, "名单该封顶 64，实得 " + com.sdzjz.node.NodeTags.filterList(n.apply(3)).size());
        c.toggleFilterEntry(0, "minecraft:sand");
        ctx.assertTrue(com.sdzjz.node.NodeTags.filterList(n.apply(0)).isEmpty(), "普通机器不该响应名单包");
        c.toggleFilterEntry(3, "#zy"); // 区块族哨兵：本世代宿主默认关 → 走通用名单段=当成一条名单项（主线在 ChunkFilterItem 才拦）；只要不抛、不写 zp
        ctx.assertTrue(com.sdzjz.node.NodeTags.viewOf(n.apply(3)).getInt("zp") == 0, "本世代 #zy 不该写 zp");
        // ④ 传感器：写 si/sv/sl，阈值钳 1e12；空 id 不改物品；§clear 清 si；普通机器拒；抽取节点同口（m160）
        c.setSensorConfig(4, "minecraft:cobblestone", 5_000_000_000_000L, true);
        ctx.assertTrue("minecraft:cobblestone".equals(com.sdzjz.node.NodeTags.sensorItem(n.apply(4)))
                && com.sdzjz.node.NodeTags.sensorThreshold(n.apply(4)) == 1_000_000_000_000L
                && com.sdzjz.node.NodeTags.sensorLess(n.apply(4)), "传感器三键该写入且阈值钳到 1e12");
        c.setSensorConfig(4, "", 77, false);
        ctx.assertTrue("minecraft:cobblestone".equals(com.sdzjz.node.NodeTags.sensorItem(n.apply(4)))
                && com.sdzjz.node.NodeTags.sensorThreshold(n.apply(4)) == 77 && !com.sdzjz.node.NodeTags.sensorLess(n.apply(4)),
                "空 id 只改阈值与方向、保留监测物品");
        c.setSensorConfig(4, "§clear", 77, false);
        ctx.assertTrue(com.sdzjz.node.NodeTags.sensorItem(n.apply(4)).isEmpty(), "§clear 该清监测物品");
        c.setSensorConfig(2, "minecraft:dirt", 9, true);
        ctx.assertTrue("minecraft:dirt".equals(com.sdzjz.node.NodeTags.sensorItem(n.apply(2))), "抽取节点自动启停该走同口");
        c.setSensorConfig(0, "minecraft:dirt", 9, true);
        ctx.assertTrue(com.sdzjz.node.NodeTags.sensorItem(n.apply(0)).isEmpty(), "普通机器不该响应传感器包");
        // ⑤ 目标：自动合成机写 ct（m235 换目标清 cr）；普通机器拒
        c.setNodeTarget(5, "minecraft:piston");
        ctx.assertTrue("minecraft:piston".equals(com.sdzjz.node.NodeTags.craftTarget(n.apply(5))), "自动合成机该写 ct");
        c.setNodeTarget(0, "minecraft:piston");
        ctx.assertTrue(com.sdzjz.node.NodeTags.craftTarget(n.apply(0)).isEmpty(), "普通机器不该收目标");
        // ⑥ 五包往返 + 有界红线（串包 128 顶，解码期拒）
        BlockPos pos = ctx.absolutePos(new BlockPos(0, 1, 0));
        var b1 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodeSensor(pos, 4, "minecraft:cobblestone", 123456789012L, true).write(b1);
        var s1 = new NodePayloads120.NodeSensor(b1);
        ctx.assertTrue(s1.pos().equals(pos) && s1.index() == 4 && "minecraft:cobblestone".equals(s1.item()) && s1.threshold() == 123456789012L && s1.less(),
                "NodeSensor 五字段往返该逐位一致");
        var b2 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodeFilter(pos, 3, "#xr").write(b2);
        var f2 = new NodePayloads120.NodeFilter(b2);
        ctx.assertTrue(f2.index() == 3 && "#xr".equals(f2.entry()), "NodeFilter 往返该一致");
        var b3 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodeTarget(pos, 5, "minecraft:piston").write(b3);
        ctx.assertTrue("minecraft:piston".equals(new NodePayloads120.NodeTarget(b3).target()), "NodeTarget 往返该一致");
        var b4 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodePause(pos, 7).write(b4);
        var b5 = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        new NodePayloads120.NodeSwitch(pos, 8).write(b5);
        ctx.assertTrue(new NodePayloads120.NodePause(b4).index() == 7 && new NodePayloads120.NodeSwitch(b5).index() == 8, "Pause/Switch 往返该一致");
        var evil = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        evil.writeBlockPos(pos); evil.writeVarInt(3); evil.writeUtf("a".repeat(200));
        boolean rejected = false;
        try { new NodePayloads120.NodeFilter(evil); } catch (io.netty.handler.codec.DecoderException e) { rejected = true; }
        ctx.assertTrue(rejected, "NodeFilter 200 字 entry 该在解码期拒收（上限 " + NodePayloads120.CFG_STR_MAX + "）");
        ctx.succeed();
    }
}
