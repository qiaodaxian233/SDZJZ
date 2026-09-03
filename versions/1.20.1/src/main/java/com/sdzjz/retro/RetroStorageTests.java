package com.sdzjz.retro;

import com.sdzjz.config.SdzjzConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * m443 刀②行为判官：1.20.1 账本核心 GameTest——语义蓝本=Legacy {@code SdzjzGameTests} 同名用例
 * （无复制无蒸发/事务回滚/嵌套回滚/类型硬顶/索引平移/NBT 往返），外加 tag 世代专属一条：
 * "不混堆不变裸"在 tag 身份模型上的重证（m440 排刀稿点名的主战场）。m444 起追加数据线三用例
 * （送出穿链/回收含 tag 件/余量回账不落地）——Create 传送带互通的 CI 侧上界，实机那一眼归作者。
 *
 * <p>注解形态照 mojmap 1.20.x 官方名（Forge 1.20.x 原文实证 template 属性）+ fabric-api 1.20.1 分支
 * EMPTY_STRUCTURE；注册走 fabric.mod.json 的 fabric-gametest 入口（与 Legacy/Modern 同键）。
 * 1.20.1 无 timeoutTicks 需求——全部用例单 tick 同步跑完。
 */
public final class RetroStorageTests implements FabricGameTest {

    /** 每用例现放一台存储核心（EMPTY_STRUCTURE 里 (0,1,0)）。 */
    private static StorageCore120 core(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (ctx.getBlockEntity(rel) instanceof StorageCore120 c) return c;
        ctx.fail("存储核心方块实体未生成");
        return null; // 不可达（上一行抛）
    }

    /** 带 tag 的"精确件"样品（k 值区分同物品不同 tag）——蓝本 exactSample 的 tag 版。 */
    private static ItemStack exactSample(int k, int count) {
        ItemStack st = new ItemStack(Items.COBBLESTONE, count);
        st.getOrCreateTag().putInt("k", k);
        return st;
    }

    /** m443 专属："不混堆不变裸"在 tag 身份模型上的重证——同物品带 tag 与不带 tag 必须两条账，
     *  精确模板 tag 原样保存（不变裸），提净互不串账。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void tag_split_keeps_exact_and_plain_apart(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 8));   // 无 tag → 普通账本
        c.deposit(exactSample(1, 8));                      // 带 tag → 精确账本（不混堆）
        ctx.assertTrue(c.count("minecraft:cobblestone") == 8, "普通账目应=8，实得 " + c.count("minecraft:cobblestone"));
        ctx.assertTrue(c.exactTemplates().size() == 1, "精确条目应=1，实得 " + c.exactTemplates().size());
        ctx.assertTrue(c.exactCount(0) == 8, "精确计数应=8，实得 " + c.exactCount(0));
        ItemStack tpl = c.exactTemplates().get(0);
        ctx.assertTrue(tpl.hasTag() && tpl.getTag().getInt("k") == 1, "精确模板 tag 必须原样保存（不变裸）");
        int got = c.withdrawExact(exactSample(1, 1), 99);
        ctx.assertTrue(got == 8, "按模板提净应得 8，实得 " + got);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 8, "提净精确条目不得动普通账目，实得 " + c.count("minecraft:cobblestone"));
        ctx.assertTrue(c.exactTemplates().size() == 0, "提净后精确条目应=0，实得 " + c.exactTemplates().size());
        ctx.succeed();
    }

    /** 蓝本 fabric_abort_restores_normal_entry：外层事务回滚，普通账目还原。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void fabric_abort_restores_normal_entry(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 32));
        try (Transaction tx = Transaction.openOuter()) {
            long ins = c.fabricStorage().insert(ItemVariant.of(Items.COBBLESTONE), 16, tx);
            ctx.assertTrue(ins == 16, "事务内插入应报 16，实得 " + ins);
            long ext = c.fabricStorage().extract(ItemVariant.of(Items.COBBLESTONE), 40, tx);
            ctx.assertTrue(ext == 40, "事务内提取应报 40，实得 " + ext);
            // 不 commit → try 退出即 abort
        }
        ctx.assertTrue(c.count("minecraft:cobblestone") == 32,
                "回滚后普通账目应还原 32，实得 " + c.count("minecraft:cobblestone"));
        ctx.succeed();
    }

    /** 蓝本 fabric_nested_abort_restores_exact_entry：内层提交、外层回滚，精确账目
     *  （含 m295 索引置脏路径）整体还原。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void fabric_nested_abort_restores_exact_entry(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(exactSample(1, 10)); // 带 tag → 自动走精确账本
        ItemVariant v = ItemVariant.of(exactSample(1, 1));
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction inner = outer.openNested()) {
                long ext = c.fabricStorage().extract(v, 10, inner); // 提净=删条目（结构前像+索引置脏都过一遍）
                ctx.assertTrue(ext == 10, "内层提净应报 10，实得 " + ext);
                inner.commit();
            }
            long ins = c.fabricStorage().insert(v, 3, outer); // 又插回=新条目
            ctx.assertTrue(ins == 3, "外层再插应报 3，实得 " + ins);
            // 外层不 commit → 全链回滚
        }
        ctx.assertTrue(c.exactTemplates().size() == 1, "回滚后精确条目数应=1，实得 " + c.exactTemplates().size());
        ctx.assertTrue(c.exactCount(0) == 10, "回滚后精确计数应还原 10，实得 " + c.exactCount(0));
        int again = c.withdrawExact(exactSample(1, 1), 10); // 索引置脏后懒重建的直查还能命中
        ctx.assertTrue(again == 10, "回滚后按模板提取应得 10（索引懒重建正确），实得 " + again);
        ctx.succeed();
    }

    /** 蓝本 type_safety_limit_rejects_new_types：类型绝对安全上限只闸新类型，已有类型照常进出。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void type_safety_limit_rejects_new_types(GameTestHelper ctx) {
        SdzjzConfig cfg = SdzjzConfig.get();
        int old = cfg.absoluteStorageTypeSafetyLimit;
        cfg.absoluteStorageTypeSafetyLimit = 2; // 配置窗归判官管，契约只管判定（m482）
        try {
            com.sdzjz.machine.StorageDomainAssertions.类型硬顶(core(ctx), 2,
                    new ItemStack(Items.STONE, 8), new ItemStack(Items.DIRT, 8), new ItemStack(Items.SAND, 8));
        } catch (AssertionError e) {
            ctx.fail("类型硬顶契约失败: " + e.getMessage());
            return;
        } finally {
            cfg.absoluteStorageTypeSafetyLimit = old; // 测试自还原，不污染同批次其它用例
        }
        ctx.succeed();
    }

    /** 蓝本 ledger_nbt_roundtrip_reconciles_at_scale 的 1.20.1 签名版：4096 合成条目+精确件+
     *  FTA 30 亿长插往返对账——long 计数、tag 模板、非法条目校验一次全过（save/load 无 Lookup 参）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void ledger_nbt_roundtrip_reconciles_at_scale(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        for (int i = 0; i < 4096; i++) c.storeView().put("sdzjz_test:type_" + i, (long) (i + 1)); // 合成 id 直灌（load 不裁不验物品表）
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        c.depositExact(exactSample(1, 5));
        try (Transaction tx = Transaction.openOuter()) { // 30 亿精确计数：FTA 长插一笔到位（deposit 的 int 形参到不了）
            long ins = c.fabricStorage().insert(ItemVariant.of(exactSample(7, 1)), 3_000_000_000L, tx);
            ctx.assertTrue(ins == 3_000_000_000L, "FTA 长插应收 30 亿，实收 " + ins);
            tx.commit();
        }
        CompoundTag saved = c.saveWithoutMetadata(); // 1.20.1 无 Lookup 参（m440 清单②）
        BlockPos rel2 = new BlockPos(2, 1, 0);
        ctx.setBlock(rel2, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel2) instanceof StorageCore120 c2)) {
            ctx.fail("对账用第二核心未生成"); return;
        }
        c2.load(saved); // 1.20.1 的 load 对位 loadWithComponents
        ctx.assertTrue(c2.storeView().equals(c.storeView()),
                "普通账本往返必须逐条相等：写 " + c.storeView().size() + " 读 " + c2.storeView().size());
        ctx.assertTrue(c2.exactTemplates().size() == c.exactTemplates().size(),
                "精确条目数不符：写 " + c.exactTemplates().size() + " 读 " + c2.exactTemplates().size());
        for (int i = 0; i < c.exactTemplates().size(); i++) {
            ctx.assertTrue(ItemStack.isSameItemSameTags(c.exactTemplates().get(i), c2.exactTemplates().get(i)),
                    "精确模板第 " + i + " 条 tag 往返漂移");
            ctx.assertTrue(c.exactCount(i) == c2.exactCount(i),
                    "精确计数第 " + i + " 条不符：写 " + c.exactCount(i) + " 读 " + c2.exactCount(i));
        }
        ctx.succeed();
    }

    /** m444 刀③：送出拍穿越线缆链——核心(0)—线(1)—线(2)—箱(3)，脉冲贴箱那根线：BFS 隔两根线
     *  仍找到核心，账本物品进箱子（能给）。pulse 直调免等相位闸（同包可见，确定性）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void cable_push_reaches_chest_through_chain(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        ctx.setBlock(new BlockPos(1, 1, 0), RetroBlocks.DATA_CABLE.defaultBlockState());
        BlockPos cableRel = new BlockPos(2, 1, 0);
        ctx.setBlock(cableRel, RetroBlocks.DATA_CABLE.defaultBlockState());
        BlockPos chestRel = new BlockPos(3, 1, 0);
        ctx.setBlock(chestRel, Blocks.CHEST.defaultBlockState());
        if (!(ctx.getBlockEntity(cableRel) instanceof DataCable120 cable)) { ctx.fail("数据线方块实体未生成"); return; }
        cable.setExtractOn(true); // 送出模式（pullMode 默认 false）
        cable.pulse(ctx.getLevel(), ctx.absolutePos(cableRel));
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "送出后账本应清零，实得 " + c.count("minecraft:cobblestone"));
        if (!(ctx.getBlockEntity(chestRel) instanceof ChestBlockEntity chest)) { ctx.fail("箱子方块实体未生成"); return; }
        int inChest = 0;
        for (int i = 0; i < chest.getContainerSize(); i++)
            if (chest.getItem(i).is(Items.COBBLESTONE)) inChest += chest.getItem(i).getCount();
        ctx.assertTrue(inChest == 64, "箱内应收 64，实得 " + inChest);
        ctx.succeed();
    }

    /** m444 刀③：回收拍——箱里裸件+带 tag 件一拍收进网络，裸件进普通账本、tag 件进精确账本
     *  （FTA 出口 m161c 双账本分流在 move 路径上重证），箱子被收空（能收）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void cable_pull_collects_chest_including_tagged(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        BlockPos cableRel = new BlockPos(1, 1, 0);
        ctx.setBlock(cableRel, RetroBlocks.DATA_CABLE.defaultBlockState());
        BlockPos chestRel = new BlockPos(2, 1, 0);
        ctx.setBlock(chestRel, Blocks.CHEST.defaultBlockState());
        if (!(ctx.getBlockEntity(chestRel) instanceof ChestBlockEntity chest)) { ctx.fail("箱子方块实体未生成"); return; }
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        chest.setItem(1, exactSample(9, 7));
        if (!(ctx.getBlockEntity(cableRel) instanceof DataCable120 cable)) { ctx.fail("数据线方块实体未生成"); return; }
        cable.setExtractOn(true);
        cable.setPullMode(true); // m231 回收
        cable.pulse(ctx.getLevel(), ctx.absolutePos(cableRel));
        ctx.assertTrue(c.count("minecraft:cobblestone") == 32, "裸件应进普通账本 32，实得 " + c.count("minecraft:cobblestone"));
        ctx.assertTrue(c.exactTemplates().size() == 1 && c.exactCount(0) == 7,
                "tag 件应进精确账本 1 条 ×7，实得 " + c.exactTemplates().size() + " 条 ×" + c.exactCount(0));
        ctx.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).isEmpty(), "箱子应被收空");
        ctx.succeed();
    }

    /** m444 刀③：目标满余量回账本绝不落地——单箱只装得下 27×64=1728，账本 1729 块，送出一拍后
     *  账本恰余 1（extractSpec 回账支路+opTargetsFull 收工口径）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void cable_push_returns_overflow_to_ledger(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.storeView().put("minecraft:cobblestone", 1729L); // 直灌绕开 int 形参（口径同 roundtrip 用例）
        BlockPos cableRel = new BlockPos(1, 1, 0);
        ctx.setBlock(cableRel, RetroBlocks.DATA_CABLE.defaultBlockState());
        ctx.setBlock(new BlockPos(2, 1, 0), Blocks.CHEST.defaultBlockState());
        if (!(ctx.getBlockEntity(cableRel) instanceof DataCable120 cable)) { ctx.fail("数据线方块实体未生成"); return; }
        cable.setExtractOn(true);
        SdzjzConfig cfg = SdzjzConfig.get();
        int oldBatch = cfg.extractPortBatch;
        cfg.extractPortBatch = 4096; // 批量抬过箱容，让"目标满"路径必然触发（测试自还原）
        try {
            cable.pulse(ctx.getLevel(), ctx.absolutePos(cableRel));
        } finally {
            cfg.extractPortBatch = oldBatch;
        }
        ctx.assertTrue(c.count("minecraft:cobblestone") == 1, "箱满后余量应回账本=1，实得 " + c.count("minecraft:cobblestone"));
        ctx.succeed();
    }

    /** m449：过滤器限定双向拍——送出只搬白名单内条目；回收带 tag 模板只收连 tag 匹配件；
     *  toggle 同件再点=移出（与 pullWants/extractSpec 同口径 isSameItemSameTags）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void cable_filter_restricts_push_and_pull(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.STONE, 8));
        c.deposit(new ItemStack(Items.COBBLESTONE, 8));
        BlockPos cableRel = new BlockPos(1, 1, 0);
        ctx.setBlock(cableRel, RetroBlocks.DATA_CABLE.defaultBlockState());
        BlockPos chestRel = new BlockPos(2, 1, 0);
        ctx.setBlock(chestRel, Blocks.CHEST.defaultBlockState());
        if (!(ctx.getBlockEntity(cableRel) instanceof DataCable120 cable)) { ctx.fail("数据线方块实体未生成"); return; }
        if (!(ctx.getBlockEntity(chestRel) instanceof ChestBlockEntity chest)) { ctx.fail("箱子方块实体未生成"); return; }
        cable.setExtractOn(true); // 送出 + 白名单只圆石
        ctx.assertTrue(cable.filterToggle(new ItemStack(Items.COBBLESTONE)) == DataCable120.FILTER_ADDED, "首次加入应=ADDED");
        cable.pulse(ctx.getLevel(), ctx.absolutePos(cableRel));
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "白名单内应送空，实得 " + c.count("minecraft:cobblestone"));
        ctx.assertTrue(c.count("minecraft:stone") == 8, "白名单外不得动，实得 " + c.count("minecraft:stone"));
        int inChest = 0;
        for (int i = 0; i < chest.getContainerSize(); i++)
            if (chest.getItem(i).is(Items.COBBLESTONE)) inChest += chest.getItem(i).getCount();
        ctx.assertTrue(inChest == 8, "箱内应只收 8 圆石，实得 " + inChest);
        // 回收段：白名单换成 tag 模板，箱里混放 tag 件与裸石头，只收 tag 件
        ctx.assertTrue(cable.filterToggle(new ItemStack(Items.COBBLESTONE)) == DataCable120.FILTER_REMOVED, "同件再点应=REMOVED");
        ctx.assertTrue(cable.filterToggle(exactSample(5, 1)) == DataCable120.FILTER_ADDED, "tag 模板加入应=ADDED");
        chest.setItem(10, exactSample(5, 4));
        chest.setItem(11, new ItemStack(Items.STONE, 3));
        cable.setPullMode(true);
        cable.pulse(ctx.getLevel(), ctx.absolutePos(cableRel));
        ctx.assertTrue(c.exactTemplates().size() == 1 && c.exactCount(0) == 4,
                "只应收进 tag 件 1 条 ×4，实得 " + c.exactTemplates().size() + " 条 ×" + c.exactCount(0));
        ctx.assertTrue(!chest.getItem(11).isEmpty() && chest.getItem(11).getCount() == 3, "裸石头应留在箱里=3");
        ctx.assertTrue(c.count("minecraft:stone") == 8, "普通账目不得被回收段污染，实得 " + c.count("minecraft:stone"));
        ctx.succeed();
    }

    /** m453：机器物品批量注册全链路——注册数=数据源枚举数且 ≥107（0.1.453 时点 101 机+6 节点，
     *  主线加机器只增不减）；抽查凋灵农场 id 注册在位；机器物品可入仓可被面板搜索命中。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void machine_items_registered_and_flow_through_network(GameTestHelper ctx) {
        int n = RetroMachineItems.items().size();
        ctx.assertTrue(n == RetroMachineItems.allDefs().size() && n >= 107,
                "注册数应=数据源枚举数且≥107，实得 " + n + "/" + RetroMachineItems.allDefs().size());
        net.minecraft.world.item.Item wf = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("sdzjz", "wither_farm"));
        ctx.assertTrue("sdzjz:wither_farm".equals(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(wf).toString()), "凋灵农场物品应注册在位");
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(wf, 3));
        ctx.assertTrue(c.count("sdzjz:wither_farm") == 3, "机器物品应可入仓=3，实得 " + c.count("sdzjz:wither_farm"));
        var hit = DataPanel120.snapshot(java.util.List.of(c), "wither", 0);
        ctx.assertTrue(hit.rows().size() == 1 && hit.rows().get(0).n() == 3, "面板按 id 搜索应命中机器物品 ×3");
        ctx.succeed();
    }

    /** m469 判官：线缆端点名单——自家三块都要伸插头（原只抄了存储核心，结构核心/数据面板恒 NONE，
     *  作者实机截图"线怼上去不连"）。**方向断言按计数做，避开 GameTest 结构旋转**：先摆线再摆
     *  三个邻居（放置期 updateNeighbourShapes 会回头刷线的端点），数插头数=2、缆管数=0、其余=NONE。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void cable_plugs_into_own_three_blocks(GameTestHelper ctx) {
        BlockPos cableRel = new BlockPos(1, 1, 0);
        ctx.setBlock(cableRel, RetroBlocks.DATA_CABLE.defaultBlockState());
        ctx.setBlock(new BlockPos(2, 1, 0), RetroBlocks.DATA_PANEL.defaultBlockState());
        ctx.setBlock(new BlockPos(0, 1, 0), RetroBlocks.STRUCTURE_CORE.defaultBlockState());
        ctx.setBlock(new BlockPos(1, 1, 1), Blocks.STONE.defaultBlockState()); // 对照：石头不伸
        var st = ctx.getBlockState(cableRel);
        int plug = 0, cable = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            com.sdzjz.block.CableEnd e = st.getValue(com.sdzjz.block.CableEndCore.END_PROPS.get(d)); // m502：属性表迁共用件
            if (e == com.sdzjz.block.CableEnd.PLUG) plug++;
            else if (e == com.sdzjz.block.CableEnd.CABLE) cable++;
        }
        ctx.assertTrue(plug == 2, "数据面板+结构核心应各伸一只插头，实得 " + plug);
        ctx.assertTrue(cable == 0, "无相邻数据线，缆管端应为 0，实得 " + cable);
        ctx.succeed();
    }

    /** m469 判官：旧档自愈——先摆邻居再摆线（原版 updateNeighbourShapes 只刷邻居不刷自己，
     *  线落地即 NONE，等同旧存档里名单修好前存下的状态）；跑一次 healEnds 应把插头补回来。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void cable_heal_ends_fixes_stale_state(GameTestHelper ctx) {
        ctx.setBlock(new BlockPos(2, 1, 0), RetroBlocks.DATA_PANEL.defaultBlockState());
        BlockPos cableRel = new BlockPos(1, 1, 0);
        ctx.setBlock(cableRel, RetroBlocks.DATA_CABLE.defaultBlockState());
        int before = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
            if (ctx.getBlockState(cableRel).getValue(com.sdzjz.block.CableEndCore.END_PROPS.get(d)) != com.sdzjz.block.CableEnd.NONE) before++;
        ctx.assertTrue(before == 0, "后放的线本应是陈旧全 NONE 态，实得非 NONE 端 " + before);
        DataCableBlock120.healEnds(ctx.getLevel(), ctx.absolutePos(cableRel), ctx.getBlockState(cableRel));
        int after = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
            if (ctx.getBlockState(cableRel).getValue(com.sdzjz.block.CableEndCore.END_PROPS.get(d)) == com.sdzjz.block.CableEnd.PLUG) after++;
        ctx.assertTrue(after == 1, "自愈后应对数据面板伸一只插头，实得 " + after);
        ctx.succeed();
    }

    /** m478（真移植 B 阶段第一刀）：共用 StackKey 在 1.20.1 世代的哈希契约——与主线
     *  SdzjzGameTests.stack_key_hash_contract_shared 是**同一份 StackKey**，本条压的是
     *  RetroStackKind（tag 口径）装上去之后契约照样成立，外加本世代特有的
     *  「空 tag 在场 ≠ 无 tag」口径（原版 tagMatches 同口径，存取侧 hasTag 分流与此闭合）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void stack_key_hash_contract_retro(GameTestHelper ctx) {
        ItemStack a1 = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        a1.getOrCreateTag().putInt("k", 5);
        ItemStack a2 = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD, 64);
        a2.getOrCreateTag().putInt("k", 5);
        ItemStack b = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        b.getOrCreateTag().putInt("k", 6);
        // m510：这一对不能用钻石剑——1.20.1 ItemStack 构造器对可损耗物品会 setDamageValue(0) 自带 {Damage:0} tag，
        // "无 tag"的前提根本不成立（两把裸剑 tag 同为 {Damage:0} → same 恒真，判官自 m478 起恒失败）；换不可损耗的石头验。
        ItemStack plain = new ItemStack(net.minecraft.world.item.Items.STONE);
        ItemStack emptyTag = new ItemStack(net.minecraft.world.item.Items.STONE);
        emptyTag.getOrCreateTag(); // 空 tag 在场
        var ka1 = com.sdzjz.storage.StackKey.of(a1);
        var ka2 = com.sdzjz.storage.StackKey.of(a2);
        var kb = com.sdzjz.storage.StackKey.of(b);
        var kp = com.sdzjz.storage.StackKey.of(plain);
        var ke = com.sdzjz.storage.StackKey.of(emptyTag);
        ctx.assertTrue(ka1.equals(ka2) && ka1.hashCode() == ka2.hashCode(), "同 tag 该相等且哈希相等（契约）");
        ctx.assertTrue(!ka1.equals(kb), "不同 tag 不该相等");
        ctx.assertTrue(!kp.equals(ke), "空 tag 在场 ≠ 无 tag（1.20.1 口径，与 hasTag 分流闭合）");
        java.util.HashMap<com.sdzjz.storage.StackKey, Integer> m = new java.util.HashMap<>();
        m.put(ka1, 1); m.put(kb, 2); m.put(kp, 3);
        ctx.assertTrue(m.get(ka2) == 1 && m.size() == 3, "作 HashMap 键时同款命中同桶，异款各占一桶");
        ctx.succeed();
    }

    /** m480（真移植 D 阶段先行）：存储域跨代行为契约——与主线 SdzjzGameTests.storage_domain_contract
     *  跑的是**同一套断言**（xplat StorageDomainAssertions，判官只此一份），本用例喂 StorageCore120。
     *  两代同绿=账本行为在两个世代上确实是同一个东西，而不是「看起来差不多」。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void storage_domain_contract_retro(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel) instanceof StorageCore120 c)) {
            ctx.fail("存储核心方块实体未生成");
            return;
        }
        ItemStack a1 = new ItemStack(Items.DIAMOND_SWORD);
        a1.getOrCreateTag().putInt("k", 11);
        ItemStack a2 = new ItemStack(Items.DIAMOND_SWORD);
        a2.getOrCreateTag().putInt("k", 11); // 与 a1 同款
        ItemStack b = new ItemStack(Items.DIAMOND_SWORD);
        b.getOrCreateTag().putInt("k", 12);  // 异款
        try {
            com.sdzjz.machine.StorageDomainAssertions.runAll(c,
                    new ItemStack(Items.STONE), new ItemStack(Items.DIRT), a1, a2, b);
        } catch (AssertionError e) {
            ctx.fail("存储域契约失败: " + e.getMessage());
            return;
        }
        ctx.succeed();
    }

}
