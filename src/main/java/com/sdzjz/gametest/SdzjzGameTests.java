package com.sdzjz.gametest;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.net.DataPanelViewPayload;
import com.sdzjz.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;

/**
 * m297 GameTest（外部审计四建议之④"给关键问题增加真正的 GameTest"）。
 * 账本级用例 + E2E 用例（m323 起上 mock 玩家：createMockPlayer(GameMode)，m328 从
 * 弃用的 createMockCreativeServerPlayerInWorld 迁来——作者构建报告 8 处 [removal] 警告清账；取物竞争
 * 直接测账本不变量——handler 的取物顺序 m 修复正是靠这条不变量成立）。
 * 跑法：`gradlew runGametest`（build.gradle 已配 run），出 build/junit.xml；
 * 或任何加 -Dfabric-api.gametest 的开发端服务器。生产环境该入口不激活，零开销。
 */
public class SdzjzGameTests implements FabricGameTest {

    /** 每用例现放一台存储核心（EMPTY_STRUCTURE 里 (0,1,0)）。 */
    private static StorageCoreBlockEntity core(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, ModBlocks.STORAGE_CORE.defaultBlockState());
        if (ctx.getBlockEntity(rel) instanceof StorageCoreBlockEntity c) return c;
        ctx.fail("存储核心方块实体未生成");
        return null; // 不可达（上一行抛）
    }

    /** 带自定义组件的"精确件"样品（k 值区分同物品不同组件）。 */
    private static ItemStack exactSample(int k, int count) {
        ItemStack st = new ItemStack(Items.COBBLESTONE, count);
        CompoundTag t = new CompoundTag();
        t.putInt("k", k);
        com.sdzjz.item.ItemData.write(st, t);
        return st;
    }

    /** 审计 twoPlayersShiftTakeLastStack 的账本不变量版：两路抢最后一组，取和恒等于库存——
     *  handler 侧"先扣账、按实收给"的复制漏洞修复正建立在这条不变量上。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void two_withdraw_last_stack_no_dupe(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        int a = c.withdraw("minecraft:cobblestone", 64);
        int b = c.withdraw("minecraft:cobblestone", 64);
        ctx.assertTrue(a + b == 64, "两路取和必须=64（无复制无凭空蒸发），实得 " + a + "+" + b);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "取尽后余量必须=0");
        ctx.succeed();
    }

    /** 审计 fabricTransactionAbortRestoresNormalEntry：外层事务回滚，普通账目还原。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void fabric_abort_restores_normal_entry(GameTestHelper ctx) {
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
        ctx.succeed();
    }

    /** 审计 fabricNestedTransactionAbortRestoresExactEntry：内层提交、外层回滚，精确账目
     *  （含 m295 索引置脏路径）整体还原。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void fabric_nested_abort_restores_exact_entry(GameTestHelper ctx) {
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
        ctx.succeed();
    }

    /** 审计 oversizedPanelViewPayloadRejected：m291 有界 Codec 必须在**解码期**拒掉超长表。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void oversized_panel_view_payload_rejected(GameTestHelper ctx) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                ctx.getLevel().registryAccess());
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeUtf("q", 128);
        buf.writeInt(0);                 // scrollRow：tuple 用 PacketCodecs.INTEGER=4 字节
        buf.writeVarInt(1_000_000);      // 恶意声明：一百万条匹配 id
        boolean rejected = false;
        try {
            DataPanelViewPayload.CODEC.decode(buf);
        } catch (io.netty.handler.codec.DecoderException e) {
            rejected = true; // 期望路径：分配前拒收
        }
        ctx.assertTrue(rejected, "超长匹配表必须在解码期抛 DecoderException 拒收");
        ctx.succeed();
    }

    /** 审计建议③的落地验证：类型绝对安全上限只闸新类型，已有类型照常进出。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void type_safety_limit_rejects_new_types(GameTestHelper ctx) {
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
        ctx.succeed();
    }

    /** m295 精确索引与列表同步：删中间条目（下标平移）后按模板直查仍逐一命中。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void exact_index_survives_middle_removal(GameTestHelper ctx) {
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
        ctx.succeed();
    }

    /** m305 调度器防饥饿 soak（评审③复评"下一步是测"）：100 个合成核心按**固定序**（有序不公平
     *  最坏情形）每拍抢 cap=100 的全服预算、每核要 1000，压 120 拍。断言只对**设计保证**：
     *  ①预算硬顶恒成立；②无长期饥饿——最坏交替节奏下饿核每 2 拍必得 1 周期，断 min≥拍数/4 留裕量。
     *  比例公平（最高/最低几倍）是 anti-starvation **没承诺**的性质，属作者实机真负载矩阵口径
     *  （/sdzjz profile sched 判据行），soak 不断。cap 走 request 形参不动配置；测试服无产线，
     *  静态池零干扰；首尾 clearAll 不留残态。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sdzjzSchedA") // m309:两条调度器用例共享静态池,分batch串行
    public void scheduler_no_core_starves_under_pressure(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        final int CORES = 100, CAP = 100, TICKS = 120;
        final long[] fed = new long[CORES];
        final int[] ran = {0};
        ctx.failIfEver(() -> {
            if (ran[0] >= TICKS) return;
            ran[0]++;
            for (int i = 0; i < CORES; i++) // 固定序=BE tick 序稳定的最坏形
                fed[i] += com.sdzjz.machine.CoreScheduler.request(dimOf(ctx), new BlockPos(i, 300, -300).asLong(), 1000, CAP, ticksOf(ctx));
            if (ran[0] == TICKS) {
                long min = Long.MAX_VALUE, max = 0, sum = 0;
                for (long f : fed) { min = Math.min(min, f); max = Math.max(max, f); sum += f; }
                com.sdzjz.machine.CoreScheduler.clearAll();
                ctx.assertTrue(sum <= (long) CAP * TICKS, "总批准 " + sum + " 超预算硬顶 " + ((long) CAP * TICKS));
                ctx.assertTrue(min >= TICKS / 4, "存在长期饥饿核心：min=" + min + " < " + (TICKS / 4) + "（防饥饿保底失效）");
                ctx.assertTrue(max > 0, "全体零批准（预算通道疑似全堵）");
                ctx.succeed();
            }
        });
    }

    /** m309 回归：k>cap 恒饿修复（作者 100 核+1 产线 cap=100 实测：第 101 核 2400 拍颗粒无收——
     *  旧语义逐请求记名把已进食核心也每拍打回名单，"先食权"人人持有等于没有，tick 序末核
     *  在饿核数>预算时永远轮不到）。105 合成核心×4 请求/拍 抢 cap=100（固定序最坏形），
     *  热身 20 拍达稳态后计 100 拍窗口：拍龄资历轮转下人人有进展。cap 走形参不碰配置，
     *  首尾 clearAll，与七号用例分 batch 串行（共享静态池）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sdzjzSchedB")
    public void scheduler_rotates_when_starved_exceed_budget(GameTestHelper ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        final int CORES = 105, CAP = 100, WARM = 20, RUN = 100;
        final long[] fed = new long[CORES];
        final int[] ran = {0};
        ctx.failIfEver(() -> {
            if (ran[0] >= WARM + RUN) return;
            ran[0]++;
            boolean count = ran[0] > WARM;
            for (int i = 0; i < CORES; i++) {
                long got = 0;
                for (int r = 0; r < 4; r++)
                    got += com.sdzjz.machine.CoreScheduler.request(dimOf(ctx), new BlockPos(i, 300, -600).asLong(), 20, CAP, ticksOf(ctx));
                if (count) fed[i] += got;
            }
            if (ran[0] == WARM + RUN) {
                long min = Long.MAX_VALUE;
                for (long f : fed) min = Math.min(min, f);
                com.sdzjz.machine.CoreScheduler.clearAll();
                ctx.assertTrue(min > 0, "k>cap 稳态仍有恒饿核心（资历轮转失效）");
                ctx.assertTrue(min >= RUN / 8, "最低核窗口吞吐 " + min + " < " + (RUN / 8) + "（有界饥饿超界）");
                ctx.succeed();
            }
        });
    }

    /** m310 原生大堆叠：①getMaxCount 抬到配置值（可堆叠物）且不可堆叠物纹丝不动；
     *  ②百万计数过 ItemStack.CODEC 存档编解码往返不被 1..99 旧钳位吃掉（mixin ①生效验证）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void big_stacks_native(GameTestHelper ctx) {
        ItemStack big = new ItemStack(Items.COBBLESTONE, 1_000_000);
        ctx.assertTrue(big.getMaxStackSize() >= 1_000_000, "大堆叠未生效：cobble getMaxCount=" + big.getMaxStackSize());
        ctx.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).getMaxStackSize() == 1, "不可堆叠物被误抬（耐久合并风险）");
        var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, ctx.getLevel().registryAccess());
        var enc = ItemStack.CODEC.encodeStart(ops, big).getOrThrow();
        ItemStack back = ItemStack.CODEC.parse(ops, enc).getOrThrow();
        ctx.assertTrue(back.getCount() == 1_000_000, "计数存档往返丢失：读回 " + back.getCount() + "（Codec 钳位未放宽）");
        ctx.succeed();
    }

    /** m311 随身仓库账本：跨 int 边界入账（30 亿）→整包倾倒进核心→逐 id 对账+包倒空。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void portable_vault_ledger_survives_int_boundary(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        ItemStack vault = new ItemStack(com.sdzjz.registry.ModItems.PORTABLE_VAULT);
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.vaultAdd(vault, "minecraft:cobblestone", 3_000_000_000L), "入账被拒");
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.vaultAdd(vault, "minecraft:dirt", 5L), "入账被拒");
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.vaultTotal(vault) == 3_000_000_005L,
                "账本总数错：" + com.sdzjz.item.PortableVaultItem.vaultTotal(vault));
        com.sdzjz.item.PortableVaultItem.vaultDumpInto(vault, c);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 3_000_000_000L,
                "倾倒后核心账不符：" + c.count("minecraft:cobblestone") + "（int 边界切块有误）");
        ctx.assertTrue(c.count("minecraft:dirt") == 5L, "小额账目丢失");
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.vaultTypes(vault) == 0, "倾倒后包未清空");
        ctx.succeed();
    }

    /** m322 终端主快照缓存：账本没动=命中同一引用；**只动精确账本也必须失效**（本笔给
     *  StorageCore 补 exactRev 的存在理由——storeRev 只罩普通账本，罩不住组件件变动）；
     *  快照按 MASTER_ORDER 预排序（存量降序），handler 免排的前提在此验真。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void panel_master_snapshot_tracks_exact_ledger(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0); // 与核心贴邻，connectedCores BFS 直连
        ctx.setBlock(prel, ModBlocks.DATA_PANEL.defaultBlockState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.fail("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        c.depositExact(exactSample(1, 5));
        var a1 = panel.masterEntries();
        ctx.assertTrue(a1.size() == 2, "首帧快照应 2 条（普通+精确），实得 " + a1.size());
        ctx.assertTrue(a1.get(0).tpl == null && a1.get(0).n == 64, "预排序：64 普通件应排第一（存量降序）");
        var a2 = panel.masterEntries();
        ctx.assertTrue(a2 == a1, "账本未动应命中缓存（同一引用），实为重建");
        c.depositExact(exactSample(1, 3)); // 只动精确账本
        var a3 = panel.masterEntries();
        ctx.assertTrue(a3 != a1, "精确账本变动必须打掉缓存（exactRev 未挂钩即在此现形）");
        long ex = 0; for (var d : a3) if (d.tpl != null) ex = d.n;
        ctx.assertTrue(ex == 8, "精确条目应并账 5+3=8，实得 " + ex);
        int got = c.withdraw("minecraft:cobblestone", 60); // 只动普通账本（storeRev 支路回归）
        ctx.assertTrue(got == 60, "普通取出应得 60，实得 " + got);
        var a4 = panel.masterEntries();
        ctx.assertTrue(a4 != a3, "普通账本变动必须打掉缓存");
        ctx.assertTrue(a4.get(0).tpl != null && a4.get(0).n == 8, "重排序：精确件 8 > 普通件 4 应升第一");
        ctx.assertTrue(a4.get(1).tpl == null && a4.get(1).n == 4, "普通件余量应为 4，实得 " + a4.get(1).n);
        ctx.succeed();
    }

    // ===== m323 端到端第一批（评审第四优先：网络包→ScreenHandler→玩家库存→BE→存档 完整链）=====

    /** m323 评审清单#1：**真 mock 玩家两人同时 Shift 取最后一组**（m328 起 createMockPlayer(SURVIVAL)，PlayerEntity 形参链全程够用）——m266 复制窗修复的
     *  handler 级判官（此前只有账本级 two_withdraw）。两 handler 各持 10t 陈旧展示页同抢 64 圆石，
     *  账本权威=两人实收和恒等 64、账本清零，谁都不凭空得料。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void two_players_shift_take_last_stack_via_handlers(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlock(prel, ModBlocks.DATA_PANEL.defaultBlockState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.fail("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.COBBLESTONE, 64)); // 最后一组
        var p1 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var p2 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var h1 = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel);
        var h2 = new com.sdzjz.screen.DataPanelScreenHandler(2, p2.getInventory(), panel); // 双 handler 构造即各刷首页——都看见 64
        h1.quickMoveStack(p1, com.sdzjz.screen.DataPanelScreenHandler.DISP0);
        h2.quickMoveStack(p2, com.sdzjz.screen.DataPanelScreenHandler.DISP0); // h2 展示页此刻仍是陈旧 64（10t 窗口）——复制窗正形
        int g1 = p1.getInventory().countItem(Items.COBBLESTONE);
        int g2 = p2.getInventory().countItem(Items.COBBLESTONE);
        ctx.assertTrue(g1 + g2 == 64, "两人实收和必须=64（无复制无蒸发），实得 " + g1 + "+" + g2);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "账本应取尽=0，实余 " + c.count("minecraft:cobblestone"));
        h1.removed(p1); h2.removed(p2); // 注销监听/观众计数（m126a/m107a 口径）
        ctx.succeed();
    }

    /** m323 评审清单#2：两玩家同开同一面板各搜不同词——m292 视图迁 handler 的 E2E 回归
     *  （m322 快照共享后尤须验：共用 master 不等于共用过滤）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void two_players_search_independently(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlock(prel, ModBlocks.DATA_PANEL.defaultBlockState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.fail("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.IRON_INGOT, 32));
        c.deposit(new ItemStack(Items.DIAMOND, 16));
        var p1 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var p2 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var h1 = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel);
        var h2 = new com.sdzjz.screen.DataPanelScreenHandler(2, p2.getInventory(), panel);
        int d0 = com.sdzjz.screen.DataPanelScreenHandler.DISP0;
        ctx.runAfterDelay(3, () -> { // 构造首刷占了本 tick 名额（≥2t 节流），隔 3 拍再设视图=立即真刷
            h1.setView("iron", 0, java.util.List.of());
            h2.setView("diamond", 0, java.util.List.of());
            ctx.assertTrue(h1.getSlot(d0).getItem().is(Items.IRON_INGOT), "玩家A搜iron首格应为铁锭");
            ctx.assertTrue(h1.getSlot(d0 + 1).getItem().isEmpty(), "玩家A过滤后应只剩 1 条");
            ctx.assertTrue(h2.getSlot(d0).getItem().is(Items.DIAMOND), "玩家B搜diamond首格应为钻石（若被A覆盖=m292回归）");
            ctx.assertTrue(h2.getSlot(d0 + 1).getItem().isEmpty(), "玩家B过滤后应只剩 1 条");
            ctx.assertTrue(h1.getSlot(d0).getItem().is(Items.IRON_INGOT), "B设视图后A的页面不许被动");
            h1.removed(p1); h2.removed(p2);
            ctx.succeed();
        });
    }

    /** m323 评审清单#7+#8（缩尺合刀）：大账本存档往返全量对账——普通账本 4096 类型（合成 id 直灌
     *  storeView，readNbt 只验空id/非正数不验物品存在=m273 口径，正好测字符串保真）+ 真实存取 +
     *  精确账本三件（CustomData 组件 / 30 亿 long 计数走 FTA 长插 / 组件相等逐index核）。
     *  "重启"在 GameTest 框架内=createNbt→全新 BE.read（writeNbt/readNbt 同一条存档链路）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void ledger_nbt_roundtrip_reconciles_at_scale(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        for (int i = 0; i < 4096; i++) c.storeView().put("sdzjz_test:type_" + i, (long) (i + 1)); // 合成 id 直灌（readNbt 不裁不验物品表）
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        c.depositExact(exactSample(1, 5));
        try (Transaction tx = Transaction.openOuter()) { // 30 亿精确计数：FTA 长插一笔到位（deposit 的 int 形参到不了）
            long ins = c.fabricStorage().insert(ItemVariant.of(exactSample(7, 1)), 3_000_000_000L, tx);
            ctx.assertTrue(ins == 3_000_000_000L, "FTA 长插应收 30 亿，实收 " + ins);
            tx.commit();
        }
        var lookup = ctx.getLevel().registryAccess();
        CompoundTag saved = c.saveWithoutMetadata(lookup);
        BlockPos rel2 = new BlockPos(2, 1, 0);
        ctx.setBlock(rel2, ModBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel2) instanceof StorageCoreBlockEntity c2)) {
            ctx.fail("对账用第二核心未生成"); return;
        }
        c2.loadWithComponents(saved, lookup);
        ctx.assertTrue(c2.storeView().equals(c.storeView()),
                "普通账本往返必须逐条相等：写 " + c.storeView().size() + " 读 " + c2.storeView().size());
        ctx.assertTrue(c2.exactTemplates().size() == c.exactTemplates().size(),
                "精确条目数不符：写 " + c.exactTemplates().size() + " 读 " + c2.exactTemplates().size());
        for (int i = 0; i < c.exactTemplates().size(); i++) {
            ctx.assertTrue(ItemStack.isSameItemSameComponents(c.exactTemplates().get(i), c2.exactTemplates().get(i)),
                    "精确模板第 " + i + " 条组件往返漂移");
            ctx.assertTrue(c.exactCount(i) == c2.exactCount(i),
                    "精确计数第 " + i + " 条不符：写 " + c.exactCount(i) + " 读 " + c2.exactCount(i));
        }
        ctx.succeed();
    }

    /** m323 评审清单#9：Fabric 事务与同 tick 手账混部——m278 增量 undo 的核心性质 E2E：
     *  事务只回滚**自己碰过的键**，事务窗内的手账改动（异键）不被冲掉；提交后与手账串行算术精确。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void transaction_mix_preserves_manual_changes(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 100));
        c.deposit(new ItemStack(Items.DIRT, 40));
        try (Transaction tx = Transaction.openOuter()) {
            long ext = c.fabricStorage().extract(ItemVariant.of(Items.COBBLESTONE), 50, tx);
            ctx.assertTrue(ext == 50, "事务内提取应报 50，实得 " + ext);
            int manual = c.withdraw("minecraft:dirt", 10); // 事务窗内的手账改动（异键）
            ctx.assertTrue(manual == 10, "手账取土应得 10，实得 " + manual);
            // 不 commit → abort
        }
        ctx.assertTrue(c.count("minecraft:cobblestone") == 100,
                "回滚后圆石应还原 100，实余 " + c.count("minecraft:cobblestone"));
        ctx.assertTrue(c.count("minecraft:dirt") == 30,
                "手账改动不许被回滚冲掉（m278 整本深拷时代的病），实余 " + c.count("minecraft:dirt"));
        try (Transaction tx = Transaction.openOuter()) { // 提交路 + 手账串行：算术精确
            c.fabricStorage().extract(ItemVariant.of(Items.COBBLESTONE), 25, tx);
            tx.commit();
        }
        int direct = c.withdraw("minecraft:cobblestone", 25);
        ctx.assertTrue(direct == 25 && c.count("minecraft:cobblestone") == 50,
                "事务提交+手账串行后应余 50，实余 " + c.count("minecraft:cobblestone"));
        ctx.succeed();
    }

    /** m324 区块级预算（maxRecipesPerChunkTick 真接线）：同区块同账/异区块异账/换拍复位。
     *  合成远坐标直驱账层（m305 调度器用例同法：预算单元不依赖真核心生产链），与其他用例
     *  的区块键天然不撞（真核心的 chunkCharge 记它们自己的区块）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void chunk_budget_shares_and_resets(GameTestHelper ctx) {
        var w = ctx.getLevel();
        long cap = 10;
        BlockPos p1 = new BlockPos(1_234_567, 64, 1_234_567);
        BlockPos p2 = p1.offset(3, 0, 0);   // 同区块
        BlockPos p3 = p1.offset(160, 0, 0); // 异区块（隔 10 个区块）
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p1), cap, ticksW(w)) == 10, "初始余量应=cap");
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p1), 0, ticksW(w)) == Long.MAX_VALUE, "cap<=0 应=闸关无限");
        com.sdzjz.machine.CoreScheduler.chunkCharge(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p1), 7, ticksW(w));
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p2), cap, ticksW(w)) == 3,
                "同区块同账：邻坐标余量应=3，实得 " + com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p2), cap, ticksW(w)));
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p3), cap, ticksW(w)) == 10, "异区块异账：远坐标余量应=10");
        com.sdzjz.machine.CoreScheduler.chunkCharge(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p2), 3, ticksW(w));
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p1), cap, ticksW(w)) == 0, "记满后余量应=0");
        ctx.runAfterDelay(1, () -> { // 下一 server tick：区块账换拍复位（独立时钟，不依赖全服闸开）
            ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p1), cap, ticksW(w)) == 10,
                    "换拍后余量应复位=cap，实得 " + com.sdzjz.machine.CoreScheduler.chunkHeadroom(dimW(w), net.minecraft.world.level.ChunkPos.asLong(p1), cap, ticksW(w)));
            ctx.succeed();
        });
    }

    // ===== m326 端到端第二批（评审清单 #3/#4/#5）=====

    /** m326 评审清单#3：共享 3×3 CraftGrid（m300 公共工作台语义判官）——A 摆料 B 实时可见、
     *  两 handler 结果格各算各的同出 4 木棍、A shift 取走后网格/双方结果格同步清空且只产一轮
     *  （核心无板材=网络补料断即停，m106b 停机条件）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void shared_craft_grid_two_players(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlock(prel, ModBlocks.DATA_PANEL.defaultBlockState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.fail("数据面板方块实体未生成"); return;
        }
        var p1 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var p2 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var h1 = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel);
        var h2 = new com.sdzjz.screen.DataPanelScreenHandler(2, p2.getInventory(), panel);
        int r = com.sdzjz.screen.DataPanelScreenHandler.RESULT;
        h1.getSlot(0).setByPlayer(new ItemStack(Items.OAK_PLANKS)); // 竖排两板=木棍配方
        h1.getSlot(3).setByPlayer(new ItemStack(Items.OAK_PLANKS));
        ctx.assertTrue(h2.getSlot(0).getItem().is(Items.OAK_PLANKS), "共享网格：A 摆料 B 必须实时可见");
        ctx.assertTrue(h1.getSlot(r).getItem().is(Items.STICK) && h1.getSlot(r).getItem().getCount() == 4,
                "A 结果格应出 4 木棍");
        ctx.assertTrue(h2.getSlot(r).getItem().is(Items.STICK) && h2.getSlot(r).getItem().getCount() == 4,
                "B 结果格各算各的，同网格应同出 4 木棍");
        h1.quickMoveStack(p1, r); // 连续合成：仓里无板材→补料断→恰好一轮
        ctx.assertTrue(p1.getInventory().countItem(Items.STICK) == 4,
                "无补料应恰产一轮 4 木棍，实得 " + p1.getInventory().countItem(Items.STICK));
        ctx.assertTrue(h1.getSlot(0).getItem().isEmpty() && h1.getSlot(3).getItem().isEmpty(), "扣料后网格应清空");
        ctx.assertTrue(h2.getSlot(r).getItem().isEmpty(), "网格空了 B 的结果格必须跟着清（监听器同步）");
        ctx.assertTrue(c.count("minecraft:oak_planks") == 0, "核心从头到尾没板材（对照锚）");
        h1.removed(p1); h2.removed(p2);
        ctx.succeed();
    }

    /** m326 评审清单#4：面板被拆后旧 handler 继续发包——canUse 立刻假（m299 存活三判触发
     *  服务端关屏），关屏落地前迟到的视图包走完整 repage 也不许抛。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void stale_handler_after_panel_broken(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlock(prel, ModBlocks.DATA_PANEL.defaultBlockState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.fail("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        var p1 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack term = new ItemStack(com.sdzjz.registry.ModItems.TERMINAL);
        p1.getInventory().setItem(0, term); // 主手（selectedSlot=0）
        var hitPos = panel.getBlockPos();
        var uctx = new net.minecraft.world.item.context.UseOnContext(p1, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(hitPos), net.minecraft.core.Direction.UP, hitPos, false));
        ctx.assertTrue(((com.sdzjz.item.TerminalItem) com.sdzjz.registry.ModItems.TERMINAL)
                        .useOn(uctx) == net.minecraft.world.InteractionResult.SUCCESS, "真绑定路径应 SUCCESS");
        var h = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel, true); // 远程屏（免距离判，专测存活）
        ctx.assertTrue(h.stillValid(p1), "面板在、钥匙在：canUse 应为真");
        ctx.runAfterDelay(3, () -> { // 隔开 ctor 首刷的 ≥2t 节流名额，让迟到包走完整 repage 路
            ctx.getLevel().setBlockAndUpdate(hitPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            ctx.assertTrue(panel.isRemoved(), "拆除后 BE 应已 removed");
            ctx.assertTrue(!h.stillValid(p1), "面板被拆：canUse 必须立刻为假（m299 存活三判）");
            h.setView("stone", 0, java.util.List.of()); // 迟到视图包：完整 repage 在 removed BE 上不许抛
            h.removed(p1);
            ctx.succeed();
        });
    }

    /** m326 评审清单#5：手持终端开远程屏后丢掉/换手——钥匙语义（m303）：背包在=可用、
     *  离身=关屏、**光标栈也算身上**（界面内挪动终端不误关）、彻底丢弃=关屏。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void remote_terminal_key_lifecycle(GameTestHelper ctx) {
        core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlock(prel, ModBlocks.DATA_PANEL.defaultBlockState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.fail("数据面板方块实体未生成"); return;
        }
        var p1 = ctx.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        p1.getInventory().setItem(0, new ItemStack(com.sdzjz.registry.ModItems.TERMINAL));
        var hitPos = panel.getBlockPos();
        var uctx = new net.minecraft.world.item.context.UseOnContext(p1, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(hitPos), net.minecraft.core.Direction.UP, hitPos, false));
        ((com.sdzjz.item.TerminalItem) com.sdzjz.registry.ModItems.TERMINAL).useOn(uctx);
        ctx.assertTrue(com.sdzjz.item.TerminalItem.isBoundTo(p1.getInventory().getItem(0), hitPos, ctx.getLevel()),
                "绑定后 isBoundTo 应为真");
        var h = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel, true);
        ctx.assertTrue(h.stillValid(p1), "持钥匙：canUse 应为真");
        ItemStack key = p1.getInventory().getItem(0);
        p1.getInventory().setItem(0, ItemStack.EMPTY);
        ctx.assertTrue(!h.stillValid(p1), "钥匙离身：canUse 必须为假");
        h.setCarried(key);
        ctx.assertTrue(h.stillValid(p1), "光标栈也算身上：界面内挪动终端不许误关（m303 明文）");
        h.setCarried(ItemStack.EMPTY);
        ctx.assertTrue(!h.stillValid(p1), "彻底丢弃：canUse 必须为假");
        h.removed(p1);
        ctx.succeed();
    }

    /** m332 廿一号：随身仓库专属仓位——账面 PersistentState 存档往返（long 账本跨 int 边界）+ 仓位准入规则。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void portable_vault_slot_state_roundtrip(GameTestHelper ctx) {
        var lookup = ctx.getLevel().registryAccess();
        java.util.UUID u = java.util.UUID.randomUUID();
        ItemStack vault = new ItemStack(com.sdzjz.registry.ModItems.PORTABLE_VAULT);
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.vaultAdd(vault, "minecraft:stone", 3_000_000_000L),
                "入账 30 亿石头应成功");
        var a = new com.sdzjz.item.PortableVaultSlot.State();
        a.set(u, vault);
        var nbt = a.save(new net.minecraft.nbt.CompoundTag(), lookup);
        var b = com.sdzjz.item.PortableVaultSlot.State.read(nbt, lookup);
        ItemStack back = b.get(u);
        ctx.assertTrue(back.getItem() instanceof com.sdzjz.item.PortableVaultItem, "往返后仓位物品身份不变");
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.ledger(back).getLong("minecraft:stone") == 3_000_000_000L,
                "long 账本跨 int 边界往返不变（m311 十号用例同口径）");
        ctx.assertTrue(b.get(java.util.UUID.randomUUID()).isEmpty(), "陌生 UUID 读空");
        // 仓位准入：只收随身仓库、格上限恒 1（m310 SlotMaxCountMixin 打在超类无参口，本覆写直返不受累）
        var slot = new com.sdzjz.item.PortableVaultSlot(new net.minecraft.world.SimpleContainer(1));
        ctx.assertTrue(slot.mayPlace(vault), "仓位应收随身仓库");
        ctx.assertTrue(!slot.mayPlace(new ItemStack(net.minecraft.world.item.Items.STONE)), "仓位拒收普通物品");
        ctx.assertTrue(slot.getMaxStackSize() == 1, "仓位格上限恒 1");
        ctx.succeed();
    }

    /** m333 廿二号：交易所等级系统——门槛升级/满级封顶/旧合同按大师接管/交易表序号锚定不漂移。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void trade_center_leveling(GameTestHelper ctx) {
        ItemStack c = new ItemStack(com.sdzjz.registry.ModItems.VILLAGER_CONTRACT);
        // 旧合同：有职业没有 lv 键 → 按大师接管（m333 前它本就全表解锁，不没收），且不再记账
        var n = new net.minecraft.nbt.CompoundTag();
        n.putString("prof", "librarian");
        // m438：故意原生 poke 组件层（测的是组件身份/混堆本体，不走 ItemData 门面；m404 同理归加载器侧）
        c.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(n));
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.contractLevel(c) == 5, "旧合同（无lv键）应按大师接管");
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.grantTradeXp(c, 999) == 0, "满级/旧合同不再升级");
        // 新合同：新手起步，10/70/150/250 累计门槛逐级升，一笔灌满连升封顶
        n.putInt("lv", 1);
        n.putInt("xp", 0);
        c.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(n));
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.grantTradeXp(c, 10) == 1
                        && com.sdzjz.block.TradeCenterBlockEntity.contractLevel(c) == 2, "累计 10 经验→学徒");
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.grantTradeXp(c, 1000) == 3
                        && com.sdzjz.block.TradeCenterBlockEntity.contractLevel(c) == 5, "一笔灌满→连升三级到大师封顶");
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.grantTradeXp(c, 50) == 0, "大师后再投入=零升级（界面明示满级，不静默）");
        // 表内不变量：每职业至少一条 1 级起步交易（新合同不许开局无事可做）
        for (var e : com.sdzjz.machine.VillagerTrades.ALL.entrySet())
            ctx.assertTrue(e.getValue().trades().stream().anyMatch(t -> t.minLevel() == 1),
                    "职业 " + e.getKey() + " 缺 1 级起步交易");
        // 序号锚定（交易机目标串"职业|序号"防漂移）：m333 只在表尾追加，头部序号必须原位
        ctx.assertTrue("minecraft:wheat".equals(com.sdzjz.machine.VillagerTrades.ALL.get("farmer").trades().get(0).inItem()),
                "farmer|0 仍是小麦收购");
        ctx.assertTrue("minecraft:mending".equals(com.sdzjz.machine.VillagerTrades.ALL.get("librarian").trades().get(4).enchant()),
                "librarian|4 仍是经验修补（m101 序）");
        ctx.assertTrue("minecraft:lapis_lazuli".equals(com.sdzjz.machine.VillagerTrades.ALL.get("cleric").trades().get(0).outItem()),
                "cleric|0 仍是青金石量产口（m153）");
        ctx.succeed();
    }

    /** m334 廿三号：无限复制机——目标校验唯一口径 + 配方"超级难"回归闸 + 六件套注册闭环。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void duplicator_target_and_recipe(GameTestHelper ctx) {
        // 目标校验（setNodeTarget 服务端闸与 tick 闸同口）
        ctx.assertTrue(com.sdzjz.item.DuplicatorItem.validTarget("minecraft:diamond"), "钻石应为合法目标");
        ctx.assertTrue(!com.sdzjz.item.DuplicatorItem.validTarget("minecraft:air"), "空气不许当目标");
        ctx.assertTrue(!com.sdzjz.item.DuplicatorItem.validTarget(""), "空串不许当目标");
        ctx.assertTrue(!com.sdzjz.item.DuplicatorItem.validTarget("坏 id!!"), "非法字符 id 不许当目标（tryParse 挡）");
        ctx.assertTrue(!com.sdzjz.item.DuplicatorItem.validTarget("minecraft:no_such_item_xyz"), "未注册 id 不许当目标");
        // 配方超难回归闸（作者点名"配方要超级难"——防后人手滑降价）：8星+重核在列，总件数≥100，Ⅲ档
        com.sdzjz.machine.SuperBenchRecipes.Recipe r = null;
        for (var rec : com.sdzjz.machine.SuperBenchRecipes.ALL)
            if ("sdzjz:duplicator".equals(rec.result())) { r = rec; break; }
        ctx.assertTrue(r != null, "复制机配方必须在表");
        ctx.assertTrue(r.ingredients().getOrDefault("minecraft:nether_star", 0) >= 8, "下界之星 ≥8（超难底线）");
        ctx.assertTrue(r.ingredients().getOrDefault("minecraft:heavy_core", 0) >= 1, "重型核心在列");
        int total = 0;
        for (int v : r.ingredients().values()) total += v;
        ctx.assertTrue(total >= 100, "配方总件数 ≥100（现 " + total + "）——超难回归闸");
        ctx.assertTrue(r.tier() == 3, "复制机必须Ⅲ档");
        // 注册闭环：物品/def 同 id
        ctx.assertTrue(com.sdzjz.registry.ModItems.DUPLICATOR instanceof com.sdzjz.item.DuplicatorItem, "物品注册为 DuplicatorItem");
        ctx.assertTrue("duplicator".equals(com.sdzjz.machine.Machines.DUPLICATOR.id()), "def id=duplicator");
        ctx.succeed();
    }

    /** m335 廿四号：选择器查询语法真值表（学 JEI 语法习惯、实现自写——@模组/-排除/|并联/大小写/空查询）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void picker_query_syntax(GameTestHelper ctx) {
        var Q = (java.util.function.BiFunction<String[], String, Boolean>) (it, q) ->
                com.sdzjz.machine.PickerQuery.matches(it[0], it[1], q);
        String[] diamond = {"钻石", "minecraft:diamond"};
        String[] dup = {"无限复制机", "sdzjz:duplicator"};
        String[] cobbledDeep = {"深板岩圆石", "minecraft:cobbled_deepslate"};
        String[] deep = {"深板岩", "minecraft:deepslate"};
        ctx.assertTrue(Q.apply(diamond, "diamond"), "注册路径命中");
        ctx.assertTrue(Q.apply(diamond, "钻"), "中文显示名命中");
        ctx.assertTrue(Q.apply(diamond, "DIAmond"), "不区分大小写");
        ctx.assertTrue(Q.apply(diamond, ""), "空查询恒真");
        ctx.assertTrue(Q.apply(dup, "@sdzjz"), "@命名空间命中本模组");
        ctx.assertTrue(!Q.apply(diamond, "@sdzjz"), "@命名空间拒他模组");
        ctx.assertTrue(Q.apply(dup, "@sdzjz 复制"), "组内与：@模组+名词同时成立");
        ctx.assertTrue(!Q.apply(cobbledDeep, "deepslate -cobbled"), "-排除：圆石版出局");
        ctx.assertTrue(Q.apply(deep, "deepslate -cobbled"), "-排除：素版保留（纯排除比 JEI 更宽松，允许独立使用）");
        ctx.assertTrue(Q.apply(diamond, "redstone|diamond"), "|并联任一组命中即真");
        ctx.assertTrue(!Q.apply(diamond, "redstone|lapis"), "|并联全不中为假");
        ctx.assertTrue(!Q.apply(diamond, " - @ "), "全废词组不放行");
        ctx.succeed();
    }

    /** m339 廿五号：经验池公平裁决真值表——礼让期非名单节点吃 0，名单节点照吃，无人挨饿先到先得，开关关=旧行为。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void xp_fair_share_decide(GameTestHelper ctx) {
        ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xpFairDecide(10, 5, true, true, false) == 0,
                "礼让期：非名单节点吃 0（这就是“第二台饿死”的解药——反过来喂）");
        ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xpFairDecide(10, 5, true, true, true) == 5,
                "礼让期：名单节点按池量吃");
        ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xpFairDecide(10, 5, true, false, false) == 5,
                "无人挨饿：先到先得照旧");
        ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xpFairDecide(10, 5, false, true, false) == 5,
                "开关关：回旧先到先得");
        ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xpFairDecide(10, 0, true, true, true) == 0,
                "池空：名单节点也吃不到（继续挂名等池涨）");
        ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xpFairDecide(3, 99, true, false, false) == 3,
                "池量富余按需取小");
        ctx.succeed();
    }

    /** m343 廿六号：合成机槽位替代材料（外部审计P0）——"任意木板"配方（工作台）用云杉木板
     *  也能算次数/能实扣，想要集合含替代材料（路由/链需求认云杉）；显式关口径=旧首选行为。
     *  真配方表口径：用 ctx 世界的 RecipeManager 解析，不造假配方。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void craft_ingredient_alternatives(GameTestHelper ctx) {
        var plans = com.sdzjz.machine.CraftPlanner.plans(ctx.getLevel(), "minecraft:crafting_table");
        ctx.assertTrue(!plans.isEmpty(), "工作台应有合成配方");
        var plan = plans.get(0);
        ctx.assertTrue(!plan.groups().isEmpty() && plan.groups().get(0).candidates().size() > 1,
                "工作台配方的木板槽应解析出多个候选（tag #planks）");
        java.util.Map<String, Long> stock = new java.util.HashMap<>();
        stock.put("minecraft:spruce_planks", 64L);
        java.util.function.ToLongFunction<String> stk = k -> stock.getOrDefault(k, 0L);
        ctx.assertTrue(com.sdzjz.machine.CraftPlanner.maxCrafts(plan, 999, stk, true) == 16,
                "64 云杉木板=16 次工作台（任意木板口径，4 板/次）");
        ctx.assertTrue(com.sdzjz.machine.CraftPlanner.maxCrafts(plan, 999, stk, false) == 0,
                "关口径=旧首选行为（只认首选材料，云杉不算数）");
        java.util.Map<String, Long> taken = com.sdzjz.machine.CraftPlanner.takeFor(
                plan, 2, stk, (id, amt) -> stock.merge(id, -amt, Long::sum), true);
        ctx.assertTrue(taken.getOrDefault("minecraft:spruce_planks", 0L) == 8L
                        && stock.get("minecraft:spruce_planks") == 56L,
                "实扣 2 次=8 块云杉，账对得上（消耗多重集与扣账同源）");
        ctx.assertTrue(com.sdzjz.machine.CraftPlanner.remaindersOf(taken).isEmpty(),
                "木板无容器残留");
        java.util.Map<String, Long> mix = new java.util.HashMap<>(); // 混料：贪心按候选序取，绝不虚扣
        mix.put("minecraft:oak_planks", 3L);
        mix.put("minecraft:spruce_planks", 3L);
        ctx.assertTrue(com.sdzjz.machine.CraftPlanner.maxCrafts(plan, 999, k -> mix.getOrDefault(k, 0L), true) == 1,
                "3 橡木+3 云杉=1 次（跨类型合计 6/4，缺 2 不虚算）");
        ctx.succeed();
    }

    /** m344 廿七号：画布观众登记表——开屏挂号/关屏销号（handler 双钩）+ 漏钩自愈
     *  （直接改写 currentScreenHandler 模拟未经 onClosed 的换屏，核心 tick 的谓词校验应把它销号）。
     *  m344b：createMockPlayer 假人**不是** ServerPlayerEntity（m328 注记早说了"其余全按 PlayerEntity
     *  形参传"），挂号钩 instanceof 天然不进——首跑 CI 抓获。改手工 new ServerPlayerEntity
     *  （四参构造+SyncedClientOptions.createDefault 均 yarn 1.21.1 核到；fake player 通用刀法）。
     *  零发包保障：方法体单 tick 原子执行，体末两人都已 销号/谓词失配，flush 永远轮不到给
     *  无 networkHandler 的假人发快照包。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_viewer_registry(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, ModBlocks.STRUCTURE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel) instanceof com.sdzjz.block.StructureCoreBlockEntity be)) {
            ctx.fail("结构核心方块实体未生成"); return;
        }
        var sw = ctx.getLevel();
        var p1 = new net.minecraft.server.level.ServerPlayer(sw.getServer(), sw,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sdzjz_v1"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var p2 = new net.minecraft.server.level.ServerPlayer(sw.getServer(), sw,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sdzjz_v2"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var h1 = new com.sdzjz.screen.StructureCoreScreenHandler(1, p1.getInventory(), be);
        p1.containerMenu = h1;
        ctx.assertTrue(be.canvasViewerCount() == 1, "开屏挂号：观众数应=1");
        var h2 = new com.sdzjz.screen.StructureCoreScreenHandler(2, p2.getInventory(), be);
        p2.containerMenu = h2;
        ctx.assertTrue(be.canvasViewerCount() == 2, "双观众应=2");
        h2.removed(p2);
        p2.containerMenu = p2.inventoryMenu; // 归位防悬垂（登记已销，双保险）
        ctx.assertTrue(be.canvasViewerCount() == 1, "关屏销号：应回 1");
        p1.containerMenu = p1.inventoryMenu; // 模拟漏钩换屏（未经 onClosed）
        ctx.runAfterDelay(3, () -> { // 核心 tick 的 flushCanvasSnapshot 谓词校验应自愈销号
            ctx.assertTrue(be.canvasViewerCount() == 0, "漏钩自愈：谓词失配观众应被 tick 销号，实余 " + be.canvasViewerCount());
            ctx.succeed();
        });
    }

    /** m346 廿八号：SmeltPlanner 稳定选序——pickStable 纯函数直测（m339 xpFairDecide 同法）
     *  + 真配方表锚点（原版无同输入重复熔炼配方，锚点=行为逐字节不变的对照）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void smelt_planner_stable_pick(GameTestHelper ctx) {
        java.util.List<Object[]> cands = new java.util.ArrayList<>(java.util.List.of(
                new Object[]{"mymod:zzz", "out_z", 1},
                new Object[]{"minecraft:bbb", "out_vanilla", 1},
                new Object[]{"mymod:aaa", "out_a", 1}));
        ctx.assertTrue("out_vanilla".equals(com.sdzjz.machine.SmeltPlanner.pickStable(cands)[1]),
                "minecraft 命名空间候选应排前胜出");
        java.util.Collections.reverse(cands);
        ctx.assertTrue("out_vanilla".equals(com.sdzjz.machine.SmeltPlanner.pickStable(cands)[1]),
                "洗牌不变性：倒序喂入胜者应不变");
        java.util.List<Object[]> mods = java.util.List.of(
                new Object[]{"mymod:z_recipe", "out_z", 1},
                new Object[]{"mymod:a_recipe", "out_a", 1});
        ctx.assertTrue("out_a".equals(com.sdzjz.machine.SmeltPlanner.pickStable(mods)[1]),
                "同空间应按配方 id 字典序取最小");
        ctx.assertTrue(com.sdzjz.machine.SmeltPlanner.pickStable(java.util.List.of()) == null,
                "空候选应返回 null");
        Object[] stone = com.sdzjz.machine.SmeltPlanner.resultOf(ctx.getLevel(), "minecraft:cobblestone");
        ctx.assertTrue(stone != null && "minecraft:stone".equals(stone[0]) && (Integer) stone[1] == 1,
                "真配方表锚点：圆石应烧成石头×1");
        ctx.assertTrue(com.sdzjz.machine.SmeltPlanner.resultOf(ctx.getLevel(), "minecraft:stick") == null,
                "不可熔炼物应返回 null");
        ctx.succeed();
    }

    /** m347 廿九号：孤儿强加载声明渐进核销——force 走真入口（声明+运行时+票三件套），
     *  debugForgetRuntime 精确注入孤儿态（=核心消失于区块未加载态），sweepNow 直驱绕过宽限节拍：
     *  前两击迟滞不动、第三击核销；同批活声明（运行时有主）全程豁免且击数被销。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void chunk_claim_reconcile(GameTestHelper ctx) {
        var w = ctx.getLevel();
        int base = com.sdzjz.block.CoreChunkLoading.claimCount(w);
        BlockPos orphanPos = ctx.absolutePos(new BlockPos(0, 1, 0)).offset(16384, 0, 16384); // 远离在用区块
        BlockPos alivePos = orphanPos.offset(512, 0, 0);
        com.sdzjz.block.CoreChunkLoading.force(w, orphanPos, false);
        com.sdzjz.block.CoreChunkLoading.force(w, alivePos, false);
        ctx.assertTrue(com.sdzjz.block.CoreChunkLoading.claimCount(w) == base + 2, "两声明应入表");
        com.sdzjz.block.CoreChunkLoading.debugForgetRuntime(w, new net.minecraft.world.level.ChunkPos(orphanPos).toLong());
        com.sdzjz.block.CoreChunkLoading.sweepNow(w);
        com.sdzjz.block.CoreChunkLoading.sweepNow(w);
        ctx.assertTrue(com.sdzjz.block.CoreChunkLoading.claimCount(w) == base + 2,
                "两击迟滞：声明应还在（防误杀）");
        com.sdzjz.block.CoreChunkLoading.sweepNow(w);
        ctx.assertTrue(com.sdzjz.block.CoreChunkLoading.claimCount(w) == base + 1,
                "三击核销：孤儿声明应被撤，实存 " + com.sdzjz.block.CoreChunkLoading.claimCount(w));
        com.sdzjz.block.CoreChunkLoading.sweepNow(w);
        com.sdzjz.block.CoreChunkLoading.sweepNow(w);
        com.sdzjz.block.CoreChunkLoading.sweepNow(w);
        ctx.assertTrue(com.sdzjz.block.CoreChunkLoading.claimCount(w) == base + 1,
                "活声明豁免：运行时有主的声明任扫不掉");
        com.sdzjz.block.CoreChunkLoading.release(w, alivePos, false); // 清场不留票
        ctx.assertTrue(com.sdzjz.block.CoreChunkLoading.claimCount(w) == base, "release 清场对账");
        ctx.succeed();
    }

    /** m348 三十号：停机核心端点扫描降频——首扫（加载哨兵）/停→开哨兵/开画布哨兵三个新鲜度契约。
     *  慢拍 200t 的"不扫"负断言故意不测：日历拍相位随 world.getGameTime() 漂，窗口内撞上 %200==0 就假红。
     *  观众哨兵走同 tick 原子挂号→断言→销号（m344b 零发包口径：fake 玩家无 networkHandler，
     *  绝不能活过本 tick 让 flush 给它发包）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void core_idle_scan_relief(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0), relS = new BlockPos(1, 1, 0), relS2 = new BlockPos(0, 1, 1);
        ctx.setBlock(rel, ModBlocks.STRUCTURE_CORE.defaultBlockState());
        ctx.setBlock(relS, ModBlocks.STORAGE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(rel) instanceof com.sdzjz.block.StructureCoreBlockEntity be)) {
            ctx.fail("结构核心方块实体未生成"); return;
        }
        long sPos = ctx.absolutePos(relS).asLong(), s2Pos = ctx.absolutePos(relS2).asLong();
        ctx.runAfterDelay(3, () -> { // ① 首扫：加载哨兵（lastEndpointScan 初值 -1000）停机也要即时扫一次
            ctx.assertTrue(hasEndpoint(be, sPos), "首扫哨兵：停机核心加载后邻接存储核心应已入端点表");
            ctx.setBlock(relS2, ModBlocks.STORAGE_CORE.defaultBlockState()); // 停机期新增第二台
            be.toggleRunning(true); // ② 停→开转变沿哨兵
            ctx.assertTrue(be.endpointScanPending(), "开机哨兵：toggleRunning(true) 应置扫描待刷");
            ctx.runAfterDelay(3, () -> {
                ctx.assertTrue(hasEndpoint(be, s2Pos), "开机新鲜度：≤3t 内新端点应入表，慢拍陈旧窗清零");
                be.toggleRunning(false);
                ctx.assertTrue(!be.endpointScanPending(), "开→停不置哨兵（上轮已扫清位）");
                var sw = ctx.getLevel();
                var pv = new net.minecraft.server.level.ServerPlayer(sw.getServer(), sw,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sdzjz_v3"),
                        net.minecraft.server.level.ClientInformation.createDefault());
                be.addCanvasViewer(pv); // ③ 开画布转变沿哨兵（同 tick 挂号→断言→销号，零发包）
                boolean pend = be.endpointScanPending();
                be.removeCanvasViewer(pv);
                ctx.assertTrue(pend, "开画布哨兵：addCanvasViewer 应置扫描待刷");
                ctx.succeed();
            });
        });
    }

    // m366 调度器升 Common 后键/钟折算助手（测试侧=版本侧）
    private static String dimOf(GameTestHelper ctx) { return ctx.getLevel().dimension().location().toString(); }
    private static long ticksOf(GameTestHelper ctx) { return ctx.getLevel().getServer().getTickCount(); }
    private static String dimW(net.minecraft.server.level.ServerLevel w) { return w.dimension().location().toString(); }
    private static long ticksW(net.minecraft.server.level.ServerLevel w) { return w.getServer().getTickCount(); }

    private static boolean hasEndpoint(com.sdzjz.block.StructureCoreBlockEntity be, long posLong) {
        for (long[] e : be.storageEndpointsView()) if (e[0] == posLong) return true;
        return false;
    }

    /** m349 卅一号：一次成型执行计划——①与旧三口（pick→maxCrafts→takeFor→remaindersOf）逐点等价；
     *  ②快照物化契约=存储每个去重 id 恰查一次（计数器直测，这才是本刀的性能承诺）；
     *  ③手选只看手选；④全缺料回退首候选零扣料；⑤容器残留（蛋糕收 3 空桶）随 Exec 单趟出。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void craft_exec_single_pass(GameTestHelper ctx) {
        var w = ctx.getLevel();
        var plans = com.sdzjz.machine.CraftPlanner.plans(w, "minecraft:crafting_table");
        ctx.assertTrue(!plans.isEmpty(), "工作台应有合成配方");
        java.util.Map<String, Long> stock = new java.util.HashMap<>();
        stock.put("minecraft:spruce_planks", 64L);
        // ① 等价：旧三口组合当参照
        var oldPlan = com.sdzjz.machine.CraftPlanner.pick(plans, k -> stock.getOrDefault(k, 0L));
        long oldCrafts = com.sdzjz.machine.CraftPlanner.maxCrafts(oldPlan, 999, k -> stock.getOrDefault(k, 0L), true);
        java.util.Map<String, Long> shadow = new java.util.HashMap<>(stock);
        var oldTaken = com.sdzjz.machine.CraftPlanner.takeFor(oldPlan, oldCrafts,
                k -> shadow.getOrDefault(k, 0L), (id, amt) -> shadow.merge(id, -amt, Long::sum), true);
        // ② 计数器包着的 StockView：每 id 查询次数记账
        java.util.Map<String, Integer> hits = new java.util.HashMap<>();
        com.sdzjz.machine.CraftPlanner.StockView sv = id -> { hits.merge(id, 1, Integer::sum); return stock.getOrDefault(id, 0L); };
        var ex = com.sdzjz.machine.CraftPlanner.exec(plans, null, p2 -> 999L, sv, true);
        ctx.assertTrue(ex.plan().recipeId().equals(oldPlan.recipeId()) && ex.crafts() == oldCrafts
                        && ex.taken().equals(oldTaken),
                "Exec 与旧三口逐点等价（选配方/次数/实扣多重集），实得 crafts=" + ex.crafts());
        int over = 0;
        for (int v : hits.values()) if (v != 1) over++;
        ctx.assertTrue(over == 0 && !hits.isEmpty(), "快照契约：全候选去重 id 各查恰一次，违约 " + over + " 项");
        // ③ 手选只看手选：手动钉住首候选，只有它的 id 被查
        java.util.Set<String> touched = new java.util.HashSet<>();
        var manual = plans.get(0);
        var exM = com.sdzjz.machine.CraftPlanner.exec(plans, manual,
                p2 -> 999L, id -> { touched.add(id); return stock.getOrDefault(id, 0L); }, true);
        java.util.Set<String> manualIds = new java.util.HashSet<>();
        for (var g : manual.groups()) manualIds.addAll(g.candidates());
        ctx.assertTrue(exM.plan().recipeId().equals(manual.recipeId()) && manualIds.containsAll(touched),
                "手选口径：只评估手选配方且只查它的候选 id");
        // ④ 全缺料：回退首候选、零次零扣零残留（缺料报告口径与旧 pick 一致）
        var exE = com.sdzjz.machine.CraftPlanner.exec(plans, null, p2 -> 999L, id -> 0L, true);
        ctx.assertTrue(exE.plan().recipeId().equals(plans.get(0).recipeId())
                        && exE.crafts() == 0 && exE.taken().isEmpty() && exE.remainders().isEmpty(),
                "全缺料：回退首候选零扣料");
        // ⑤ 容器残留：蛋糕 3 桶奶→3 空桶随 Exec 出（真配方表口径）
        var cake = com.sdzjz.machine.CraftPlanner.plans(w, "minecraft:cake");
        ctx.assertTrue(!cake.isEmpty(), "蛋糕应有合成配方");
        java.util.Map<String, Long> ck = new java.util.HashMap<>();
        ck.put("minecraft:milk_bucket", 3L); ck.put("minecraft:sugar", 2L);
        ck.put("minecraft:egg", 1L); ck.put("minecraft:wheat", 3L);
        var exC = com.sdzjz.machine.CraftPlanner.exec(cake, null, p2 -> 999L, id -> ck.getOrDefault(id, 0L), true);
        ctx.assertTrue(exC.crafts() == 1 && exC.remainders().getOrDefault("minecraft:bucket", 0L) == 3L,
                "蛋糕 1 次：3 桶奶消耗→3 空桶残留，实得 " + exC.remainders());
        ctx.succeed();
    }

    /** m351 卅二号：GC/分配账——两快照单调不减；HotSpot 下服务器线程分配账应覆盖测间真分配
     *  （GameTest 体=冷代码解释执行，逃逸分析消除不了分配；阈值放到 8MB 留余量不赌 JIT）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void gc_account_snapshot(GameTestHelper ctx) {
        var a = com.sdzjz.debug.GcAccount.snap();
        long junk = 0; long[] keep = null;
        for (int k = 0; k < 4096; k++) { // ~32MB 真分配（4096×long[1024]）
            long[] t = new long[1024];
            t[0] = k; junk += t[0];
            if ((k & 1023) == 0) keep = t;
        }
        var b = com.sdzjz.debug.GcAccount.snap();
        ctx.assertTrue(junk == 4096L * 4095 / 2 && keep != null, "防呆：分配体未被优化掉");
        ctx.assertTrue(b.gcCount >= a.gcCount && b.gcMs >= a.gcMs, "GC 计数/停顿应单调不减");
        ctx.assertTrue(a.allocOk == b.allocOk, "分配账可用性同窗内应稳定");
        if (a.allocOk)
            ctx.assertTrue(b.allocBytes - a.allocBytes >= 8L * 1024 * 1024,
                    "线程分配账应覆盖 ~32MB 真分配(阈值8MB)，实得 " + (b.allocBytes - a.allocBytes));
        ctx.succeed();
    }

    /** m353 卅三号：NBT 双口语义——①垃圾桶已吞累计持久（丢写 bug 修复判官：旧代码改 copyNbt
     *  副本从不 set 回）；②nbtOf=拷贝，改副本不落栈（写路必须回写的契约）；③viewOf 与组件同源
     *  零拷贝可见；④无组件栈空视图不炸。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void nbt_view_and_trash_count(GameTestHelper ctx) {
        ItemStack ts = new ItemStack(com.sdzjz.registry.ModItems.TRASH_NODE);
        ctx.assertTrue(com.sdzjz.node.NodeTags.trashCount(ts) == 0, "新垃圾桶计数应为 0");
        com.sdzjz.node.NodeTags.addTrashCount(ts, 5);
        com.sdzjz.node.NodeTags.addTrashCount(ts, 7);
        ctx.assertTrue(com.sdzjz.node.NodeTags.trashCount(ts) == 12,
                "已吞累计应持久(丢写修复判官)，实得 " + com.sdzjz.node.NodeTags.trashCount(ts));
        var cp = com.sdzjz.node.NodeTags.nbtOf(ts);
        cp.putLong("tc", 999L);
        ctx.assertTrue(com.sdzjz.node.NodeTags.trashCount(ts) == 12, "nbtOf=拷贝：改副本不落栈");
        ctx.assertTrue(com.sdzjz.node.NodeTags.viewOf(ts).getLong("tc") == 12L, "viewOf 与组件同源零拷贝可见");
        ctx.assertTrue(com.sdzjz.node.NodeTags.viewOf(new ItemStack(net.minecraft.world.item.Items.STONE)).isEmpty(),
                "无组件栈=空视图不炸");
        ctx.succeed();
    }

    /** m460 卅五号：漏斗对接幻影槽——直调容器口：收货闸放行、setItem 双账本入账（普通+精确
     *  组件保真）、三闸禁抽取（canTake 恒假/getItem 恒空/removeItem 恒空）、幻影槽恒空。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void hopper_dock_phantom_slot_deposits(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        ItemStack plain = new ItemStack(Items.COBBLESTONE, 32);
        ctx.assertTrue(c.canPlaceItemThroughFace(0, plain, net.minecraft.core.Direction.DOWN), "普通件收货闸应放行");
        c.setItem(0, plain);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 32, "setItem 应入普通账本=32，实得 " + c.count("minecraft:cobblestone"));
        ctx.assertTrue(plain.isEmpty(), "收下的栈应被清空（deposit 口径）");
        ItemStack exact = exactSample(7, 3);
        c.setItem(0, exact);
        ctx.assertTrue(c.exactTemplates().size() == 1 && c.exactCount(0) == 3, "精确件应入精确账本且组件保真=3 件");
        ctx.assertTrue(!c.canTakeItemThroughFace(0, new ItemStack(Items.COBBLESTONE), net.minecraft.core.Direction.DOWN), "禁抽取：canTake 必须恒假");
        ctx.assertTrue(c.getItem(0).isEmpty() && c.removeItem(0, 64).isEmpty() && c.isEmpty(), "幻影槽必须恒空（漏斗抽取面三闸）");
        ctx.assertTrue(c.getSlotsForFace(net.minecraft.core.Direction.UP).length == 1, "开闸时应暴露 1 个幻影槽");
        ctx.succeed();
    }

    /** m460 卅六号：漏斗收货闸与类型闸同口径——硬顶=2 时第三种新类型前验即拒（漏斗根本不会掏货），
     *  已有类型照常放行；越闸硬塞（直接 setItem）绝不吞件：账本不涨、残料散落为掉落物。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void hopper_dock_respects_type_gate(GameTestHelper ctx) {
        SdzjzConfig cfg = SdzjzConfig.get();
        int old = cfg.absoluteStorageTypeSafetyLimit;
        cfg.absoluteStorageTypeSafetyLimit = 2;
        try {
            StorageCoreBlockEntity c = core(ctx);
            c.deposit(new ItemStack(Items.STONE, 8));
            c.deposit(new ItemStack(Items.DIRT, 8));
            ItemStack third = new ItemStack(Items.SAND, 8);
            ctx.assertTrue(!c.canPlaceItemThroughFace(0, third, net.minecraft.core.Direction.DOWN), "类型满时新类型应前验即拒");
            ctx.assertTrue(c.canPlaceItemThroughFace(0, new ItemStack(Items.STONE), net.minecraft.core.Direction.DOWN), "已有类型不受闸");
            c.setItem(0, third); // 模拟越闸野管道硬塞
            ctx.assertTrue(c.usedTypes() == 2 && c.count("minecraft:sand") == 0, "越闸硬塞不得入账");
            ctx.assertTrue(third.isEmpty(), "硬塞残料应转掉落物（绝不吞件），栈须被清空");
        } finally {
            cfg.absoluteStorageTypeSafetyLimit = old;
        }
        ctx.succeed();
    }

    /** m460 卅七号（端到端）：真·原版漏斗贴核心顶面，3 件圆石按漏斗节奏（8t/件）自动入账。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hopper_dock_vanilla_hopper_e2e(GameTestHelper ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos hp = new BlockPos(0, 2, 0); // 核心正上方，漏斗默认朝下
        ctx.setBlock(hp, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        if (!(ctx.getBlockEntity(hp) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hop)) {
            ctx.fail("漏斗方块实体未生成");
            return;
        }
        hop.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ctx.succeedWhen(() -> ctx.assertTrue(c.count("minecraft:cobblestone") == 3,
                "漏斗应把 3 件圆石全部塞进核心，现账 " + c.count("minecraft:cobblestone")));
    }

    // ===== m461 判官专用测试桩：玻璃方块挂"只进不出记录仓"（本入口类只在 gametest 运行加载，生产零污染）=====
    private static final java.util.List<ItemStack> XFER_SINK_LOG = new java.util.ArrayList<>();
    private static final net.fabricmc.fabric.api.transfer.v1.storage.Storage<ItemVariant> XFER_SINK =
            new net.fabricmc.fabric.api.transfer.v1.storage.Storage<>() {
                @Override public boolean supportsExtraction() { return false; }
                @Override public long insert(ItemVariant res, long max, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) {
                    XFER_SINK_LOG.add(res.toStack((int) Math.min(max, Integer.MAX_VALUE))); // 测试桩即记（生产路径 FabricXfer.insert 单笔即提交，无回滚窗）
                    return max;
                }
                @Override public long extract(ItemVariant res, long max, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) { return 0; }
                @Override public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<ItemVariant>> iterator() { return java.util.Collections.emptyIterator(); }
            };
    private static boolean xferSinkRegistered;
    private static synchronized void ensureXferSink() {
        if (xferSinkRegistered) return;
        xferSinkRegistered = true;
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlocks(
                (world, pos, state, be, dir) -> XFER_SINK, net.minecraft.world.level.block.Blocks.GLASS);
    }

    /** m461 卅八号：反向直连探针——FTA-only 方块（挂桩玻璃）命中；自家生产核心必须排除
     *  （它实现 Container 会被 Fabric 的 Inventory 兜底包装出 FTA 视图，不排除=相邻两核互喂）；
     *  配置闸关=恒 null。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void xfer_push_probe_finds_fta_and_excludes_self(GameTestHelper ctx) {
        ensureXferSink();
        BlockPos grel = new BlockPos(1, 1, 0);
        ctx.setBlock(grel, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        BlockPos gabs = ctx.absolutePos(grel);
        var lvl = ctx.getLevel();
        Object hit = com.sdzjz.block.StructureCoreBlockEntity.xferPushProbe(lvl, gabs, net.minecraft.core.Direction.WEST, lvl.getBlockEntity(gabs));
        ctx.assertTrue(hit != null, "挂桩玻璃应被探针命中（FTA-only 目标）");
        BlockPos crel = new BlockPos(2, 1, 0);
        ctx.setBlock(crel, ModBlocks.STRUCTURE_CORE.defaultBlockState());
        BlockPos cabs = ctx.absolutePos(crel);
        Object self = com.sdzjz.block.StructureCoreBlockEntity.xferPushProbe(lvl, cabs, net.minecraft.core.Direction.WEST, lvl.getBlockEntity(cabs));
        ctx.assertTrue(self == null, "自家生产核心必须被探针排除（防两核互喂自冲突）");
        SdzjzConfig cfg = SdzjzConfig.get();
        boolean old = cfg.xferPushEnabled;
        cfg.xferPushEnabled = false;
        try {
            ctx.assertTrue(com.sdzjz.block.StructureCoreBlockEntity.xferPushProbe(lvl, gabs, net.minecraft.core.Direction.WEST, lvl.getBlockEntity(gabs)) == null,
                    "配置闸关时探针必须恒 null");
        } finally {
            cfg.xferPushEnabled = old;
        }
        ctx.succeed();
    }

    /** m461 卅九号：反向直连出货栈——裸件走裸变体全收扣栈；带组件件走精确变体**组件保真不变裸**
     *  （精确件三律"不混堆不变裸"延伸到出货口）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void xfer_push_stack_keeps_components(GameTestHelper ctx) {
        ensureXferSink();
        XFER_SINK_LOG.clear();
        BlockPos grel = new BlockPos(1, 1, 0);
        ctx.setBlock(grel, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        Object h = com.sdzjz.storage.Xfer.find(ctx.getLevel(), ctx.absolutePos(grel), null);
        ctx.assertTrue(h != null && com.sdzjz.storage.Xfer.canInsert(h), "测试桩句柄应可插入");
        ItemStack plain = new ItemStack(Items.COBBLESTONE, 32);
        com.sdzjz.block.StructureCoreBlockEntity.xferPushStack(h, plain);
        ctx.assertTrue(plain.isEmpty(), "裸件应全收扣空，残留 " + plain.getCount());
        ItemStack exact = exactSample(9, 5);
        com.sdzjz.block.StructureCoreBlockEntity.xferPushStack(h, exact);
        ctx.assertTrue(exact.isEmpty(), "精确件应全收扣空");
        ctx.assertTrue(XFER_SINK_LOG.size() == 2, "记录仓应收到两笔，实得 " + XFER_SINK_LOG.size());
        ctx.assertTrue(XFER_SINK_LOG.get(0).getCount() == 32 && XFER_SINK_LOG.get(0).getComponentsPatch().isEmpty(),
                "第一笔应为 32 件裸圆石");
        ItemStack got = XFER_SINK_LOG.get(1);
        ctx.assertTrue(got.getCount() == 5 && com.sdzjz.item.ItemData.view(got).getInt("k") == 9,
                "第二笔应为 5 件精确圆石且自定义组件 k=9 保真（不变裸）");
        ctx.succeed();
    }

    /** m461 四十号（端到端）：resolveOutTarget 接线判官——运行中的裸核心相邻挂桩玻璃（FTA 去处），
     *  断网喷射（m114）必须停喷：60 拍后核心上方零 sdzjz_ejected 掉落物、输出缓存原样在位
     *  （无生产拍不推送=既有箱子通道同律）。 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void xfer_push_target_suppresses_eject(GameTestHelper ctx) {
        ensureXferSink();
        BlockPos crel = new BlockPos(0, 1, 0);
        ctx.setBlock(crel, ModBlocks.STRUCTURE_CORE.defaultBlockState());
        if (!(ctx.getBlockEntity(crel) instanceof com.sdzjz.block.StructureCoreBlockEntity sc)) {
            ctx.fail("生产核心方块实体未生成");
            return;
        }
        ctx.setBlock(new BlockPos(1, 1, 0), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        sc.setItem(com.sdzjz.block.StructureCoreBlockEntity.OUTPUT_START, new ItemStack(Items.COBBLESTONE, 8));
        sc.running = true; // 喷射段在 running 闸内
        ctx.runAfterDelay(60, () -> {
            var center = ctx.absolutePos(crel).getCenter();
            int ejected = ctx.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    net.minecraft.world.phys.AABB.ofSize(center, 8, 8, 8),
                    e -> e.getTags().contains("sdzjz_ejected")).size();
            ctx.assertTrue(ejected == 0, "有 FTA 去处时不得喷射，实测喷出 " + ejected + " 个掉落物");
            ctx.assertTrue(sc.getItem(com.sdzjz.block.StructureCoreBlockEntity.OUTPUT_START).getCount() == 8,
                    "无生产拍不推送：输出缓存应原样在位（箱子通道同律）");
            ctx.succeed();
        });
    }

    /** m372 卅四号：配方域 SPI 行为契约（作者拍板 A 线）——判官只此一份在 Common
     *  （platform.RecipeDomainAssertions，七类判定：任意木板/候选组同口径/熔炼稳定选序/
     *  Ingredient 枚举口径/合成残留双口/酿造全路径/附魔成本语义——⑥⑦随 m373 B 线扩入），
     *  本用例喂 LegacyRecipeAccess 真配方表；
     *  26.2 侧 ModernRecipeDomainTests 喂 ModernRecipeAccess 跑**同一套断言**——
     *  跨版本行为不变量，26.3/27.x 加版本零新增测试代码。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void recipe_domain_contract(GameTestHelper ctx) {
        try {
            com.sdzjz.platform.RecipeDomainAssertions.runAll(ctx.getLevel(), com.sdzjz.platform.Platform.recipes());
        } catch (AssertionError e) {
            ctx.fail("配方域契约失败: " + e.getMessage());
            return;
        }
        ctx.succeed();
    }

    /** m472（绞杀者第五刀）卌二号：NodeTags 身份口**同值判官**——Ident 走法与原
     *  {@code s.is(ModItems.X)}/{@code instanceof MachineItem} 走法逐族逐样本同值
     *  （m468 风险②"新增走法+旧走法原位保留同值"的可执行断言；样本盖满六族+机器+原版件）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void nodetags_ident_matches_item_identity(GameTestHelper ctx) {
        ItemStack[] samples = {
                new ItemStack(com.sdzjz.registry.ModItems.FILTER_NODE),
                new ItemStack(com.sdzjz.registry.ModItems.TRASH_NODE),
                new ItemStack(com.sdzjz.registry.ModItems.EXTRACTOR_NODE),
                new ItemStack(com.sdzjz.registry.ModItems.SENSOR_NODE),
                new ItemStack(com.sdzjz.registry.ModItems.SWITCH_NODE),
                new ItemStack(com.sdzjz.registry.ModItems.DISTRIBUTOR_NODE),
                new ItemStack(Items.DIRT),
        };
        for (ItemStack s : samples) {
            ctx.assertTrue(com.sdzjz.node.NodeTags.isFilter(s) == s.is(com.sdzjz.registry.ModItems.FILTER_NODE), "isFilter 同值破位");
            ctx.assertTrue(com.sdzjz.node.NodeTags.isTrash(s) == s.is(com.sdzjz.registry.ModItems.TRASH_NODE), "isTrash 同值破位");
            ctx.assertTrue(com.sdzjz.node.NodeTags.isExtractor(s) == s.is(com.sdzjz.registry.ModItems.EXTRACTOR_NODE), "isExtractor 同值破位");
            ctx.assertTrue(com.sdzjz.node.NodeTags.isSensor(s) == s.is(com.sdzjz.registry.ModItems.SENSOR_NODE), "isSensor 同值破位");
            ctx.assertTrue(com.sdzjz.node.NodeTags.isSwitch(s) == s.is(com.sdzjz.registry.ModItems.SWITCH_NODE), "isSwitch 同值破位");
            ctx.assertTrue(com.sdzjz.node.NodeTags.isDistributor(s) == s.is(com.sdzjz.registry.ModItems.DISTRIBUTOR_NODE), "isDistributor 同值破位");
            boolean mi = s.getItem() instanceof com.sdzjz.item.MachineItem;
            ctx.assertTrue((com.sdzjz.node.NodeTags.defOf(s) != null) == mi, "defOf 有无判定应与 instanceof MachineItem 同值");
            if (mi) ctx.assertTrue(com.sdzjz.node.NodeTags.defOf(s) == ((com.sdzjz.item.MachineItem) s.getItem()).def(),
                    "defOf 应回同一 def 对象");
        }
        ctx.succeed();
    }

    // ===== m477（真移植 A 阶段第一刀）：图状态两代共用一份 =====

    /** m477①共用图状态往返对拍：write→read 十三个字段逐项还原（含带组件的精确栈）。
     *  本条与 1.20.1 侧 RetroCanvasTests 的同名往返判官跑的是**同一份代码**——
     *  真移植的第一份红利：一处修复两代同时受益，不再需要对表闸追平。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_graph_state_roundtrip_shared(GameTestHelper ctx) {
        var g = new com.sdzjz.node.CanvasGraphState();
        g.machineNodes.add(new ItemStack(com.sdzjz.registry.ModItems.FILTER_NODE));
        g.machineNodes.add(exactSample(7, 1)); // 带组件的精确件：编解码走世代口，组件不许丢
        g.connections.add(new int[]{0, 1});
        g.groupNames.put(3, "测试组");
        g.storageEndpoints.add(new long[]{123L, 1});
        g.storageEndpointDims.add("minecraft:overworld");
        g.storageEdges.add(new long[]{1, 456L, 1});
        g.storageEdgeDims.add("minecraft:overworld");
        g.storageNodePos.put(123L, new int[]{10, 20, 1});
        g.nodeStatus.add(1); g.nodeStatus.add(2);
        g.nodeReason.add(""); g.nodeReason.add("测试原因");
        g.busTopIds.add("minecraft:stone"); g.busTopCounts.add(999L);
        g.prodPerMin = 4242L;
        CompoundTag nbt = new CompoundTag();
        var lookup = ctx.getLevel().registryAccess();
        g.writeRenderNbt(nbt, lookup);
        var g2 = new com.sdzjz.node.CanvasGraphState();
        g2.readRenderNbt(nbt, lookup, java.util.Map.of(), () -> { });
        ctx.assertTrue(g2.machineNodes.size() == 2, "节点数该还原，实得 " + g2.machineNodes.size());
        ctx.assertTrue(ItemStack.isSameItemSameComponents(g2.machineNodes.get(1), g.machineNodes.get(1)),
                "带组件的精确栈该组件保真（栈编解码走世代口）");
        ctx.assertTrue(g2.connections.size() == 1 && g2.connections.get(0)[1] == 1, "连线该还原");
        ctx.assertTrue("测试组".equals(g2.groupNames.get(3)), "分组名该还原");
        ctx.assertTrue(g2.storageEdges.size() == 1 && g2.storageEdges.get(0)[2] == 1, "存储边该还原");
        ctx.assertTrue(java.util.Arrays.equals(g2.storageNodePos.get(123L), new int[]{10, 20, 1}), "存储节点坐标该还原");
        ctx.assertTrue(g2.nodeReason.size() == 2 && "测试原因".equals(g2.nodeReason.get(1)), "阻塞原因该还原");
        ctx.assertTrue(g2.busTopCounts.get(0) == 999L && g2.prodPerMin == 4242L, "总线库存与产量该还原");
        ctx.succeed();
    }

    /** m477②**加固推广判官**（行为变更，显式记档）：m459 修④ 原只在 1.20.1 侧，合一后两代共享——
     *  坏档/恶意快照里的越界连线、自连、坏存储边在**读侧即剪**，好数据一条不动。
     *  主线此前只在屏侧护，而路由与摘节点簿记都要吃这张表，读侧剪一次处处安全。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void canvas_graph_state_prunes_bad_edges_shared(GameTestHelper ctx) {
        var g = new com.sdzjz.node.CanvasGraphState();
        g.machineNodes.add(new ItemStack(com.sdzjz.registry.ModItems.FILTER_NODE));
        g.machineNodes.add(new ItemStack(com.sdzjz.registry.ModItems.TRASH_NODE));
        CompoundTag nbt = new CompoundTag();
        var lookup = ctx.getLevel().registryAccess();
        g.writeRenderNbt(nbt, lookup);
        nbt.putIntArray("connections", new int[]{0, 1, 7, 9, 1, 1, -1, 0}); // 好1条 + 越界 + 自连 + 负下标
        var seg = nbt.getList("storEdges", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag bad = new CompoundTag(); bad.putInt("m", 99); bad.putLong("p", 1L); bad.putInt("r", 0); bad.putString("d", "d");
        CompoundTag badDir = new CompoundTag(); badDir.putInt("m", 0); badDir.putLong("p", 2L); badDir.putInt("r", 7); badDir.putString("d", "d");
        CompoundTag good = new CompoundTag(); good.putInt("m", 1); good.putLong("p", 3L); good.putInt("r", 1); good.putString("d", "d");
        seg.add(bad); seg.add(badDir); seg.add(good);
        nbt.put("storEdges", seg);
        var g2 = new com.sdzjz.node.CanvasGraphState();
        g2.readRenderNbt(nbt, lookup, java.util.Map.of(), () -> { });
        ctx.assertTrue(g2.connections.size() == 1 && g2.connections.get(0)[0] == 0 && g2.connections.get(0)[1] == 1,
                "连线该只剩好的 0→1，实得 " + g2.connections.size());
        ctx.assertTrue(g2.storageEdges.size() == 1 && g2.storageEdges.get(0)[0] == 1 && g2.storageEdges.get(0)[2] == 1,
                "存储边该只剩好的 (1,p3,供料)，实得 " + g2.storageEdges.size());
        ctx.assertTrue(g2.storageEdgeDims.size() == 1, "维度表该同长（同剪不错位）");
        ctx.succeed();
    }

    /** m478（真移植 B 阶段第一刀）：共用 StackKey 的**哈希契约判官**（精确账本红线）——
     *  equals 相等必然 hashCode 相等、不同附加数据必不相等、身份键与账本行为一致。
     *  本条与 1.20.1 侧跑的是**同一份 StackKey**，世代差只在 Kind 实现里。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void stack_key_hash_contract_shared(GameTestHelper ctx) {
        ItemStack a1 = exactSample(5, 1), a2 = exactSample(5, 64); // 同附加数据、不同堆叠数
        ItemStack b = exactSample(6, 1);                            // 不同附加数据
        ItemStack plain = new ItemStack(Items.DIAMOND);             // 无附加数据
        var ka1 = com.sdzjz.storage.StackKey.of(a1);
        var ka2 = com.sdzjz.storage.StackKey.of(a2);
        var kb = com.sdzjz.storage.StackKey.of(b);
        var kp = com.sdzjz.storage.StackKey.of(plain);
        ctx.assertTrue(ka1.equals(ka2), "同物品同附加数据该相等（堆叠数不参与身份）");
        ctx.assertTrue(ka1.hashCode() == ka2.hashCode(), "equals 相等则 hashCode 必相等（哈希契约）");
        ctx.assertTrue(!ka1.equals(kb), "不同附加数据不该相等");
        ctx.assertTrue(!ka1.equals(kp) && !kp.equals(ka1), "带附加数据与不带的不该混为一谈");
        ctx.assertTrue(ka1.equals(ka1) && !ka1.equals(null) && !ka1.equals("x"), "自反且对异类型安全");
        ctx.assertTrue(ka1.template() == a1, "模板只读直取，不拷贝不改");
        java.util.HashMap<com.sdzjz.storage.StackKey, Integer> m = new java.util.HashMap<>();
        m.put(ka1, 1); m.put(kb, 2);
        ctx.assertTrue(m.get(ka2) == 1 && m.get(kb) == 2 && m.size() == 2,
                "作 HashMap 键时同款命中同一桶（精确账本 exactIdx 的正确性就靠这条）");
        ctx.succeed();
    }

}
