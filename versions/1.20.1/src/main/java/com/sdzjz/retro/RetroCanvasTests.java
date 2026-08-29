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
}
