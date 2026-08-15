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
 * 账本级用例 + E2E 用例（m323 起上 mock 玩家：createMockPlayer(GameMode)，m328 从
 * 弃用的 createMockCreativeServerPlayerInWorld 迁来——作者构建报告 8 处 [removal] 警告清账；取物竞争
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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "sdzjzSchedA") // m309:两条调度器用例共享静态池,分batch串行
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

    /** m309 回归：k>cap 恒饿修复（作者 100 核+1 产线 cap=100 实测：第 101 核 2400 拍颗粒无收——
     *  旧语义逐请求记名把已进食核心也每拍打回名单，"先食权"人人持有等于没有，tick 序末核
     *  在饿核数>预算时永远轮不到）。105 合成核心×4 请求/拍 抢 cap=100（固定序最坏形），
     *  热身 20 拍达稳态后计 100 拍窗口：拍龄资历轮转下人人有进展。cap 走形参不碰配置，
     *  首尾 clearAll，与七号用例分 batch 串行（共享静态池）。 */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "sdzjzSchedB")
    public void scheduler_rotates_when_starved_exceed_budget(TestContext ctx) {
        com.sdzjz.machine.CoreScheduler.clearAll();
        final int CORES = 105, CAP = 100, WARM = 20, RUN = 100;
        final long[] fed = new long[CORES];
        final int[] ran = {0};
        ctx.runAtEveryTick(() -> {
            if (ran[0] >= WARM + RUN) return;
            ran[0]++;
            boolean count = ran[0] > WARM;
            for (int i = 0; i < CORES; i++) {
                long got = 0;
                for (int r = 0; r < 4; r++)
                    got += com.sdzjz.machine.CoreScheduler.request(ctx.getWorld(), new BlockPos(i, 300, -600), 20, CAP);
                if (count) fed[i] += got;
            }
            if (ran[0] == WARM + RUN) {
                long min = Long.MAX_VALUE;
                for (long f : fed) min = Math.min(min, f);
                com.sdzjz.machine.CoreScheduler.clearAll();
                ctx.assertTrue(min > 0, "k>cap 稳态仍有恒饿核心（资历轮转失效）");
                ctx.assertTrue(min >= RUN / 8, "最低核窗口吞吐 " + min + " < " + (RUN / 8) + "（有界饥饿超界）");
                ctx.complete();
            }
        });
    }

    /** m310 原生大堆叠：①getMaxCount 抬到配置值（可堆叠物）且不可堆叠物纹丝不动；
     *  ②百万计数过 ItemStack.CODEC 存档编解码往返不被 1..99 旧钳位吃掉（mixin ①生效验证）。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void big_stacks_native(TestContext ctx) {
        ItemStack big = new ItemStack(Items.COBBLESTONE, 1_000_000);
        ctx.assertTrue(big.getMaxCount() >= 1_000_000, "大堆叠未生效：cobble getMaxCount=" + big.getMaxCount());
        ctx.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).getMaxCount() == 1, "不可堆叠物被误抬（耐久合并风险）");
        var ops = net.minecraft.registry.RegistryOps.of(net.minecraft.nbt.NbtOps.INSTANCE, ctx.getWorld().getRegistryManager());
        var enc = ItemStack.CODEC.encodeStart(ops, big).getOrThrow();
        ItemStack back = ItemStack.CODEC.parse(ops, enc).getOrThrow();
        ctx.assertTrue(back.getCount() == 1_000_000, "计数存档往返丢失：读回 " + back.getCount() + "（Codec 钳位未放宽）");
        ctx.complete();
    }

    /** m311 随身仓库账本：跨 int 边界入账（30 亿）→整包倾倒进核心→逐 id 对账+包倒空。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void portable_vault_ledger_survives_int_boundary(TestContext ctx) {
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
        ctx.complete();
    }

    /** m322 终端主快照缓存：账本没动=命中同一引用；**只动精确账本也必须失效**（本笔给
     *  StorageCore 补 exactRev 的存在理由——storeRev 只罩普通账本，罩不住组件件变动）；
     *  快照按 MASTER_ORDER 预排序（存量降序），handler 免排的前提在此验真。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void panel_master_snapshot_tracks_exact_ledger(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0); // 与核心贴邻，connectedCores BFS 直连
        ctx.setBlockState(prel, ModBlocks.DATA_PANEL.getDefaultState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.throwGameTestException("数据面板方块实体未生成"); return;
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
        ctx.complete();
    }

    // ===== m323 端到端第一批（评审第四优先：网络包→ScreenHandler→玩家库存→BE→存档 完整链）=====

    /** m323 评审清单#1：**真 mock 玩家两人同时 Shift 取最后一组**（m328 起 createMockPlayer(SURVIVAL)，PlayerEntity 形参链全程够用）——m266 复制窗修复的
     *  handler 级判官（此前只有账本级 two_withdraw）。两 handler 各持 10t 陈旧展示页同抢 64 圆石，
     *  账本权威=两人实收和恒等 64、账本清零，谁都不凭空得料。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void two_players_shift_take_last_stack_via_handlers(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlockState(prel, ModBlocks.DATA_PANEL.getDefaultState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.throwGameTestException("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.COBBLESTONE, 64)); // 最后一组
        var p1 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var p2 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var h1 = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel);
        var h2 = new com.sdzjz.screen.DataPanelScreenHandler(2, p2.getInventory(), panel); // 双 handler 构造即各刷首页——都看见 64
        h1.quickMove(p1, com.sdzjz.screen.DataPanelScreenHandler.DISP0);
        h2.quickMove(p2, com.sdzjz.screen.DataPanelScreenHandler.DISP0); // h2 展示页此刻仍是陈旧 64（10t 窗口）——复制窗正形
        int g1 = p1.getInventory().count(Items.COBBLESTONE);
        int g2 = p2.getInventory().count(Items.COBBLESTONE);
        ctx.assertTrue(g1 + g2 == 64, "两人实收和必须=64（无复制无蒸发），实得 " + g1 + "+" + g2);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 0, "账本应取尽=0，实余 " + c.count("minecraft:cobblestone"));
        h1.onClosed(p1); h2.onClosed(p2); // 注销监听/观众计数（m126a/m107a 口径）
        ctx.complete();
    }

    /** m323 评审清单#2：两玩家同开同一面板各搜不同词——m292 视图迁 handler 的 E2E 回归
     *  （m322 快照共享后尤须验：共用 master 不等于共用过滤）。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void two_players_search_independently(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlockState(prel, ModBlocks.DATA_PANEL.getDefaultState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.throwGameTestException("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.IRON_INGOT, 32));
        c.deposit(new ItemStack(Items.DIAMOND, 16));
        var p1 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var p2 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var h1 = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel);
        var h2 = new com.sdzjz.screen.DataPanelScreenHandler(2, p2.getInventory(), panel);
        int d0 = com.sdzjz.screen.DataPanelScreenHandler.DISP0;
        ctx.waitAndRun(3, () -> { // 构造首刷占了本 tick 名额（≥2t 节流），隔 3 拍再设视图=立即真刷
            h1.setView("iron", 0, java.util.List.of());
            h2.setView("diamond", 0, java.util.List.of());
            ctx.assertTrue(h1.getSlot(d0).getStack().isOf(Items.IRON_INGOT), "玩家A搜iron首格应为铁锭");
            ctx.assertTrue(h1.getSlot(d0 + 1).getStack().isEmpty(), "玩家A过滤后应只剩 1 条");
            ctx.assertTrue(h2.getSlot(d0).getStack().isOf(Items.DIAMOND), "玩家B搜diamond首格应为钻石（若被A覆盖=m292回归）");
            ctx.assertTrue(h2.getSlot(d0 + 1).getStack().isEmpty(), "玩家B过滤后应只剩 1 条");
            ctx.assertTrue(h1.getSlot(d0).getStack().isOf(Items.IRON_INGOT), "B设视图后A的页面不许被动");
            h1.onClosed(p1); h2.onClosed(p2);
            ctx.complete();
        });
    }

    /** m323 评审清单#7+#8（缩尺合刀）：大账本存档往返全量对账——普通账本 4096 类型（合成 id 直灌
     *  storeView，readNbt 只验空id/非正数不验物品存在=m273 口径，正好测字符串保真）+ 真实存取 +
     *  精确账本三件（CustomData 组件 / 30 亿 long 计数走 FTA 长插 / 组件相等逐index核）。
     *  "重启"在 GameTest 框架内=createNbt→全新 BE.read（writeNbt/readNbt 同一条存档链路）。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ledger_nbt_roundtrip_reconciles_at_scale(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        for (int i = 0; i < 4096; i++) c.storeView().put("sdzjz_test:type_" + i, (long) (i + 1)); // 合成 id 直灌（readNbt 不裁不验物品表）
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        c.depositExact(exactSample(1, 5));
        try (Transaction tx = Transaction.openOuter()) { // 30 亿精确计数：FTA 长插一笔到位（deposit 的 int 形参到不了）
            long ins = c.fabricStorage().insert(ItemVariant.of(exactSample(7, 1)), 3_000_000_000L, tx);
            ctx.assertTrue(ins == 3_000_000_000L, "FTA 长插应收 30 亿，实收 " + ins);
            tx.commit();
        }
        var lookup = ctx.getWorld().getRegistryManager();
        NbtCompound saved = c.createNbt(lookup);
        BlockPos rel2 = new BlockPos(2, 1, 0);
        ctx.setBlockState(rel2, ModBlocks.STORAGE_CORE.getDefaultState());
        if (!(ctx.getBlockEntity(rel2) instanceof StorageCoreBlockEntity c2)) {
            ctx.throwGameTestException("对账用第二核心未生成"); return;
        }
        c2.read(saved, lookup);
        ctx.assertTrue(c2.storeView().equals(c.storeView()),
                "普通账本往返必须逐条相等：写 " + c.storeView().size() + " 读 " + c2.storeView().size());
        ctx.assertTrue(c2.exactTemplates().size() == c.exactTemplates().size(),
                "精确条目数不符：写 " + c.exactTemplates().size() + " 读 " + c2.exactTemplates().size());
        for (int i = 0; i < c.exactTemplates().size(); i++) {
            ctx.assertTrue(ItemStack.areItemsAndComponentsEqual(c.exactTemplates().get(i), c2.exactTemplates().get(i)),
                    "精确模板第 " + i + " 条组件往返漂移");
            ctx.assertTrue(c.exactCount(i) == c2.exactCount(i),
                    "精确计数第 " + i + " 条不符：写 " + c.exactCount(i) + " 读 " + c2.exactCount(i));
        }
        ctx.complete();
    }

    /** m323 评审清单#9：Fabric 事务与同 tick 手账混部——m278 增量 undo 的核心性质 E2E：
     *  事务只回滚**自己碰过的键**，事务窗内的手账改动（异键）不被冲掉；提交后与手账串行算术精确。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void transaction_mix_preserves_manual_changes(TestContext ctx) {
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
        ctx.complete();
    }

    /** m324 区块级预算（maxRecipesPerChunkTick 真接线）：同区块同账/异区块异账/换拍复位。
     *  合成远坐标直驱账层（m305 调度器用例同法：预算单元不依赖真核心生产链），与其他用例
     *  的区块键天然不撞（真核心的 chunkCharge 记它们自己的区块）。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chunk_budget_shares_and_resets(TestContext ctx) {
        var w = ctx.getWorld();
        long cap = 10;
        BlockPos p1 = new BlockPos(1_234_567, 64, 1_234_567);
        BlockPos p2 = p1.add(3, 0, 0);   // 同区块
        BlockPos p3 = p1.add(160, 0, 0); // 异区块（隔 10 个区块）
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p1, cap) == 10, "初始余量应=cap");
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p1, 0) == Long.MAX_VALUE, "cap<=0 应=闸关无限");
        com.sdzjz.machine.CoreScheduler.chunkCharge(w, p1, 7);
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p2, cap) == 3,
                "同区块同账：邻坐标余量应=3，实得 " + com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p2, cap));
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p3, cap) == 10, "异区块异账：远坐标余量应=10");
        com.sdzjz.machine.CoreScheduler.chunkCharge(w, p2, 3);
        ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p1, cap) == 0, "记满后余量应=0");
        ctx.waitAndRun(1, () -> { // 下一 server tick：区块账换拍复位（独立时钟，不依赖全服闸开）
            ctx.assertTrue(com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p1, cap) == 10,
                    "换拍后余量应复位=cap，实得 " + com.sdzjz.machine.CoreScheduler.chunkHeadroom(w, p1, cap));
            ctx.complete();
        });
    }

    // ===== m326 端到端第二批（评审清单 #3/#4/#5）=====

    /** m326 评审清单#3：共享 3×3 CraftGrid（m300 公共工作台语义判官）——A 摆料 B 实时可见、
     *  两 handler 结果格各算各的同出 4 木棍、A shift 取走后网格/双方结果格同步清空且只产一轮
     *  （核心无板材=网络补料断即停，m106b 停机条件）。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shared_craft_grid_two_players(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlockState(prel, ModBlocks.DATA_PANEL.getDefaultState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.throwGameTestException("数据面板方块实体未生成"); return;
        }
        var p1 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var p2 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var h1 = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel);
        var h2 = new com.sdzjz.screen.DataPanelScreenHandler(2, p2.getInventory(), panel);
        int r = com.sdzjz.screen.DataPanelScreenHandler.RESULT;
        h1.getSlot(0).setStack(new ItemStack(Items.OAK_PLANKS)); // 竖排两板=木棍配方
        h1.getSlot(3).setStack(new ItemStack(Items.OAK_PLANKS));
        ctx.assertTrue(h2.getSlot(0).getStack().isOf(Items.OAK_PLANKS), "共享网格：A 摆料 B 必须实时可见");
        ctx.assertTrue(h1.getSlot(r).getStack().isOf(Items.STICK) && h1.getSlot(r).getStack().getCount() == 4,
                "A 结果格应出 4 木棍");
        ctx.assertTrue(h2.getSlot(r).getStack().isOf(Items.STICK) && h2.getSlot(r).getStack().getCount() == 4,
                "B 结果格各算各的，同网格应同出 4 木棍");
        h1.quickMove(p1, r); // 连续合成：仓里无板材→补料断→恰好一轮
        ctx.assertTrue(p1.getInventory().count(Items.STICK) == 4,
                "无补料应恰产一轮 4 木棍，实得 " + p1.getInventory().count(Items.STICK));
        ctx.assertTrue(h1.getSlot(0).getStack().isEmpty() && h1.getSlot(3).getStack().isEmpty(), "扣料后网格应清空");
        ctx.assertTrue(h2.getSlot(r).getStack().isEmpty(), "网格空了 B 的结果格必须跟着清（监听器同步）");
        ctx.assertTrue(c.count("minecraft:oak_planks") == 0, "核心从头到尾没板材（对照锚）");
        h1.onClosed(p1); h2.onClosed(p2);
        ctx.complete();
    }

    /** m326 评审清单#4：面板被拆后旧 handler 继续发包——canUse 立刻假（m299 存活三判触发
     *  服务端关屏），关屏落地前迟到的视图包走完整 repage 也不许抛。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stale_handler_after_panel_broken(TestContext ctx) {
        StorageCoreBlockEntity c = core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlockState(prel, ModBlocks.DATA_PANEL.getDefaultState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.throwGameTestException("数据面板方块实体未生成"); return;
        }
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        var p1 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        ItemStack term = new ItemStack(com.sdzjz.registry.ModItems.TERMINAL);
        p1.getInventory().setStack(0, term); // 主手（selectedSlot=0）
        var hitPos = panel.getPos();
        var uctx = new net.minecraft.item.ItemUsageContext(p1, net.minecraft.util.Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                        net.minecraft.util.math.Vec3d.ofCenter(hitPos), net.minecraft.util.math.Direction.UP, hitPos, false));
        ctx.assertTrue(((com.sdzjz.item.TerminalItem) com.sdzjz.registry.ModItems.TERMINAL)
                        .useOnBlock(uctx) == net.minecraft.util.ActionResult.SUCCESS, "真绑定路径应 SUCCESS");
        var h = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel, true); // 远程屏（免距离判，专测存活）
        ctx.assertTrue(h.canUse(p1), "面板在、钥匙在：canUse 应为真");
        ctx.waitAndRun(3, () -> { // 隔开 ctor 首刷的 ≥2t 节流名额，让迟到包走完整 repage 路
            ctx.getWorld().setBlockState(hitPos, net.minecraft.block.Blocks.AIR.getDefaultState());
            ctx.assertTrue(panel.isRemoved(), "拆除后 BE 应已 removed");
            ctx.assertTrue(!h.canUse(p1), "面板被拆：canUse 必须立刻为假（m299 存活三判）");
            h.setView("stone", 0, java.util.List.of()); // 迟到视图包：完整 repage 在 removed BE 上不许抛
            h.onClosed(p1);
            ctx.complete();
        });
    }

    /** m326 评审清单#5：手持终端开远程屏后丢掉/换手——钥匙语义（m303）：背包在=可用、
     *  离身=关屏、**光标栈也算身上**（界面内挪动终端不误关）、彻底丢弃=关屏。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void remote_terminal_key_lifecycle(TestContext ctx) {
        core(ctx);
        BlockPos prel = new BlockPos(1, 1, 0);
        ctx.setBlockState(prel, ModBlocks.DATA_PANEL.getDefaultState());
        if (!(ctx.getBlockEntity(prel) instanceof com.sdzjz.block.DataPanelBlockEntity panel)) {
            ctx.throwGameTestException("数据面板方块实体未生成"); return;
        }
        var p1 = ctx.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        p1.getInventory().setStack(0, new ItemStack(com.sdzjz.registry.ModItems.TERMINAL));
        var hitPos = panel.getPos();
        var uctx = new net.minecraft.item.ItemUsageContext(p1, net.minecraft.util.Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                        net.minecraft.util.math.Vec3d.ofCenter(hitPos), net.minecraft.util.math.Direction.UP, hitPos, false));
        ((com.sdzjz.item.TerminalItem) com.sdzjz.registry.ModItems.TERMINAL).useOnBlock(uctx);
        ctx.assertTrue(com.sdzjz.item.TerminalItem.isBoundTo(p1.getInventory().getStack(0), hitPos, ctx.getWorld()),
                "绑定后 isBoundTo 应为真");
        var h = new com.sdzjz.screen.DataPanelScreenHandler(1, p1.getInventory(), panel, true);
        ctx.assertTrue(h.canUse(p1), "持钥匙：canUse 应为真");
        ItemStack key = p1.getInventory().getStack(0);
        p1.getInventory().setStack(0, ItemStack.EMPTY);
        ctx.assertTrue(!h.canUse(p1), "钥匙离身：canUse 必须为假");
        h.setCursorStack(key);
        ctx.assertTrue(h.canUse(p1), "光标栈也算身上：界面内挪动终端不许误关（m303 明文）");
        h.setCursorStack(ItemStack.EMPTY);
        ctx.assertTrue(!h.canUse(p1), "彻底丢弃：canUse 必须为假");
        h.onClosed(p1);
        ctx.complete();
    }

    /** m332 廿一号：随身仓库专属仓位——账面 PersistentState 存档往返（long 账本跨 int 边界）+ 仓位准入规则。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void portable_vault_slot_state_roundtrip(TestContext ctx) {
        var lookup = ctx.getWorld().getRegistryManager();
        java.util.UUID u = java.util.UUID.randomUUID();
        ItemStack vault = new ItemStack(com.sdzjz.registry.ModItems.PORTABLE_VAULT);
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.vaultAdd(vault, "minecraft:stone", 3_000_000_000L),
                "入账 30 亿石头应成功");
        var a = new com.sdzjz.item.PortableVaultSlot.State();
        a.set(u, vault);
        var nbt = a.writeNbt(new net.minecraft.nbt.NbtCompound(), lookup);
        var b = com.sdzjz.item.PortableVaultSlot.State.read(nbt, lookup);
        ItemStack back = b.get(u);
        ctx.assertTrue(back.getItem() instanceof com.sdzjz.item.PortableVaultItem, "往返后仓位物品身份不变");
        ctx.assertTrue(com.sdzjz.item.PortableVaultItem.ledger(back).getLong("minecraft:stone") == 3_000_000_000L,
                "long 账本跨 int 边界往返不变（m311 十号用例同口径）");
        ctx.assertTrue(b.get(java.util.UUID.randomUUID()).isEmpty(), "陌生 UUID 读空");
        // 仓位准入：只收随身仓库、格上限恒 1（m310 SlotMaxCountMixin 打在超类无参口，本覆写直返不受累）
        var slot = new com.sdzjz.item.PortableVaultSlot(new net.minecraft.inventory.SimpleInventory(1));
        ctx.assertTrue(slot.canInsert(vault), "仓位应收随身仓库");
        ctx.assertTrue(!slot.canInsert(new ItemStack(net.minecraft.item.Items.STONE)), "仓位拒收普通物品");
        ctx.assertTrue(slot.getMaxItemCount() == 1, "仓位格上限恒 1");
        ctx.complete();
    }

    /** m333 廿二号：交易所等级系统——门槛升级/满级封顶/旧合同按大师接管/交易表序号锚定不漂移。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void trade_center_leveling(TestContext ctx) {
        ItemStack c = new ItemStack(com.sdzjz.registry.ModItems.VILLAGER_CONTRACT);
        // 旧合同：有职业没有 lv 键 → 按大师接管（m333 前它本就全表解锁，不没收），且不再记账
        var n = new net.minecraft.nbt.NbtCompound();
        n.putString("prof", "librarian");
        c.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(n));
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.contractLevel(c) == 5, "旧合同（无lv键）应按大师接管");
        ctx.assertTrue(com.sdzjz.block.TradeCenterBlockEntity.grantTradeXp(c, 999) == 0, "满级/旧合同不再升级");
        // 新合同：新手起步，10/70/150/250 累计门槛逐级升，一笔灌满连升封顶
        n.putInt("lv", 1);
        n.putInt("xp", 0);
        c.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(n));
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
        ctx.complete();
    }

    /** m334 廿三号：无限复制机——目标校验唯一口径 + 配方"超级难"回归闸 + 六件套注册闭环。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void duplicator_target_and_recipe(TestContext ctx) {
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
        ctx.complete();
    }

    /** m335 廿四号：选择器查询语法真值表（学 JEI 语法习惯、实现自写——@模组/-排除/|并联/大小写/空查询）。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void picker_query_syntax(TestContext ctx) {
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
        ctx.complete();
    }

    /** m339 廿五号：经验池公平裁决真值表——礼让期非名单节点吃 0，名单节点照吃，无人挨饿先到先得，开关关=旧行为。 */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void xp_fair_share_decide(TestContext ctx) {
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
        ctx.complete();
    }
}
