package com.sdzjz.retro;

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
        s.getOrCreateTag().putInt("xc", xc);
        s.getOrCreateTag().putInt("yc", yc);
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
        ctx.assertTrue(!removed.isEmpty() && removed.getTag().getInt("xc") == 20, "摘回的应是中间节点（xc=20）");
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
}
