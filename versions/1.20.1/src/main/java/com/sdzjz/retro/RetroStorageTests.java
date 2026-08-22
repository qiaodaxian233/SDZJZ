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

/**
 * m443 刀②行为判官：1.20.1 账本核心 GameTest——语义蓝本=Legacy {@code SdzjzGameTests} 同名用例
 * （无复制无蒸发/事务回滚/嵌套回滚/类型硬顶/索引平移/NBT 往返），外加 tag 世代专属一条：
 * "不混堆不变裸"在 tag 身份模型上的重证（m440 排刀稿点名的主战场）。
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

    /** 蓝本 two_withdraw_last_stack_no_dupe：两路抢最后一组，取和恒等于库存（无复制无蒸发）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void two_withdraw_last_stack_no_dupe(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        int a = c.withdraw("minecraft:cobblestone", 64);
        int b = c.withdraw("minecraft:cobblestone", 64);
        ctx.assertTrue(a + b == 64, "两路取和必须=64（无复制无凭空蒸发），实得 " + a + "+" + b);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "取尽后余量必须=0");
        ctx.succeed();
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
        cfg.absoluteStorageTypeSafetyLimit = 2;
        try {
            StorageCore120 c = core(ctx);
            c.deposit(new ItemStack(Items.STONE, 8));
            c.deposit(new ItemStack(Items.DIRT, 8));
            ItemStack third = new ItemStack(Items.SAND, 8);
            c.deposit(third);
            ctx.assertTrue(c.usedTypes() == 2, "硬顶=2 时第三种应被拒，usedTypes 实得 " + c.usedTypes());
            ctx.assertTrue(third.getCount() == 8, "被拒的栈必须原样保留，实得 " + third.getCount());
            c.deposit(new ItemStack(Items.STONE, 8)); // 已有类型不受闸
            ctx.assertTrue(c.count("minecraft:stone") == 16, "已有类型应照常并账=16，实得 " + c.count("minecraft:stone"));
        } finally {
            cfg.absoluteStorageTypeSafetyLimit = old; // 测试自还原，不污染同批次其它用例
        }
        ctx.succeed();
    }

    /** 蓝本 exact_index_survives_middle_removal：删中间条目（下标平移）后按模板直查仍逐一命中。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void exact_index_survives_middle_removal(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(exactSample(1, 5));
        c.deposit(exactSample(2, 5));
        c.deposit(exactSample(3, 5));
        int gone = c.withdrawExact(exactSample(2, 1), 5); // 提净中间条目 → 索引删键+平移
        ctx.assertTrue(gone == 5, "中间条目提净应得 5，实得 " + gone);
        ctx.assertTrue(c.exactTemplates().size() == 2, "剩余条目应=2，实得 " + c.exactTemplates().size());
        c.deposit(exactSample(3, 5)); // 平移后并账必须仍命中原条目而非新开一条
        ctx.assertTrue(c.exactTemplates().size() == 2, "平移后并账不得新开条目，实得 " + c.exactTemplates().size());
        int k3 = c.withdrawExact(exactSample(3, 1), 99);
        ctx.assertTrue(k3 == 10, "k=3 应累计 10（5+5），实得 " + k3);
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
}
