package com.sdzjz.gametest;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.net.DataPanelViewPayload;
import com.sdzjz.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * m297 GameTest（外部审计四建议之④"给关键问题增加真正的 GameTest"）。
 * 全部服务端账本级用例，不依赖假人（TestContext 的 mock 玩家口径各版漂移大，取物竞争
 * 直接测账本不变量——handler 的取物顺序 m 修复正是靠这条不变量成立）。
 * 跑法：`gradlew runGametest`（build.gradle 已配 run），出 build/junit.xml；
 * 或任何加 -Dfabric-api.gametest 的开发端服务器。生产环境该入口不激活，零开销。
 */
public class SdzjzGameTests implements FabricGameTest {

    /** 每用例现放一台存储核心（EMPTY_STRUCTURE 里 (0,1,0)）。 */
    private static StorageCoreBlockEntity core(TestContext ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlockState(rel, ModBlocks.STORAGE_CORE.getDefaultState());
        if (ctx.getBlockEntity(rel) instanceof StorageCoreBlockEntity c) return c;
        ctx.throwGameTestException("存储核心方块实体未生成");
        return null; // 不可达（上一行抛）
    }

    /** 带自定义组件的"精确件"样品（k 值区分同物品不同组件）。 */
    private static ItemStack exactSample(int k, int count) {
        ItemStack st = new ItemStack(Items.COBBLESTONE, count);
        NbtCompound t = new NbtCompound();
        t.putInt("k", k);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(t));
        return st;
    }

    /** 审计 twoPlayersShiftTakeLastStack 的账本不变量版：两路抢最后一组，取和恒等于库存——
     *  handler 侧"先扣账、按实收给"的复制漏洞修复正建立在这条不变量上。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void two_withdraw_last_stack_no_dupe(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        int a = c.withdraw("minecraft:cobblestone", 64);
        int b = c.withdraw("minecraft:cobblestone", 64);
        ctx.assertTrue(a + b == 64, "两路取和必须=64（无复制无凭空蒸发），实得 " + a + "+" + b);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "取尽后余量必须=0");
        ctx.complete();
    }

    /** 审计 fabricTransactionAbortRestoresNormalEntry：外层事务回滚，普通账目还原。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fabric_abort_restores_normal_entry(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
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
        ctx.complete();
    }

    /** 审计 fabricNestedTransactionAbortRestoresExactEntry：内层提交、外层回滚，精确账目
     *  （含 m295 索引置脏路径）整体还原。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fabric_nested_abort_restores_exact_entry(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        c.deposit(exactSample(1, 10)); // 带组件 → 自动走精确账本
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
        ctx.complete();
    }

    /** 审计 oversizedPanelViewPayloadRejected：m291 有界 Codec 必须在**解码期**拒掉超长表。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oversized_panel_view_payload_rejected(TestContext ctx) {
        RegistryByteBuf buf = new RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),
                ctx.getWorld().getRegistryManager());
        buf.writeBlockPos(BlockPos.ORIGIN);
        buf.writeString("q", 128);
        buf.writeInt(0);                 // scrollRow：tuple 用 PacketCodecs.INTEGER=4 字节
        buf.writeVarInt(1_000_000);      // 恶意声明：一百万条匹配 id
        boolean rejected = false;
        try {
            DataPanelViewPayload.CODEC.decode(buf);
        } catch (io.netty.handler.codec.DecoderException e) {
            rejected = true; // 期望路径：分配前拒收
        }
        ctx.assertTrue(rejected, "超长匹配表必须在解码期抛 DecoderException 拒收");
        ctx.complete();
    }

    /** 审计建议③的落地验证：类型绝对安全上限只闸新类型，已有类型照常进出。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void type_safety_limit_rejects_new_types(TestContext ctx) {
        SdzjzConfig cfg = SdzjzConfig.get();
        int old = cfg.absoluteStorageTypeSafetyLimit;
        cfg.absoluteStorageTypeSafetyLimit = 2;
        try {
            StorageCoreBlockEntity c = core(ctx);
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
        ctx.complete();
    }

    /** m295 精确索引与列表同步：删中间条目（下标平移）后按模板直查仍逐一命中。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void exact_index_survives_middle_removal(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
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
        ctx.complete();
    }

    /** m305 调度器防饥饿 soak（评审③复评"下一步是测"）：100 个合成核心按**固定序**（有序不公平
     *  最坏情形）每拍抢 cap=100 的全服预算、每核要 1000，压 120 拍。断言只对**设计保证**：
     *  ①预算硬顶恒成立；②无长期饥饿——最坏交替节奏下饿核每 2 拍必得 1 周期，断 min≥拍数/4 留裕量。
     *  比例公平（最高/最低几倍）是 anti-starvation **没承诺**的性质，属作者实机真负载矩阵口径
     *  （/sdzjz profile sched 判据行），soak 不断。cap 走 request 形参不动配置；测试服无产线，
     *  静态池零干扰；首尾 clearAll 不留残态。 */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void scheduler_no_core_starves_under_pressure(TestContext ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        final int CORES = 100, CAP = 100, TICKS = 120;
        final long[] fed = new long[CORES];
        final int[] ran = {0};
        ctx.runAtEveryTick(() -> {
            if (ran[0] >= TICKS) return;
            ran[0]++;
            for (int i = 0; i < CORES; i++) // 固定序=BE tick 序稳定的最坏形
                fed[i] += com.sdzjz.machine.CoreScheduler.request(ctx.getWorld(), new BlockPos(i, 300, -300), 1000, CAP);
            if (ran[0] == TICKS) {
                long min = Long.MAX_VALUE, max = 0, sum = 0;
                for (long f : fed) { min = Math.min(min, f); max = Math.max(max, f); sum += f; }
                com.sdzjz.machine.CoreScheduler.clearAll();
                ctx.assertTrue(sum <= (long) CAP * TICKS, "总批准 " + sum + " 超预算硬顶 " + ((long) CAP * TICKS));
                ctx.assertTrue(min >= TICKS / 4, "存在长期饥饿核心：min=" + min + " < " + (TICKS / 4) + "（防饥饿保底失效）");
                ctx.assertTrue(max > 0, "全体零批准（预算通道疑似全堵）");
                ctx.complete();
            }
        });
    }
}
