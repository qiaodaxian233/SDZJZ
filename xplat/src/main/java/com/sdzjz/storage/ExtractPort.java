package com.sdzjz.storage;

import com.sdzjz.machine.StorageLedgerProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * m502（真移植 B4a）：**数据线抽取口业务两代共用一份**——m225 逐模板抽取 / 全部模式游标轮转 /
 * m231 回收拍 / m228 六面视图邻接收集 / m218b 相连核心 40t 缓存，全部整段取自主线
 * {@code DataCableBlockEntity}（注释刀号原样带）。1.20.1 那份 m444 仿写件（约 140 行业务）同刀删除。
 *
 * <p><b>为什么现在能合一（m440 旧结论过期，m483 同型）</b>：m444 排刀时定过「本世代不设 Xfer 门面，
 * transfer API 直接内联」——那是仿写路线的取舍；而 m434 把 Xfer 接口化时注释里本来就写着
 * <i>"1.20.1 格照此同款（该版 fabric-api 同有此 API）"</i>。真移植路线下，**那五口门面本身就是世代口**：
 * 本刀给 1.20.1 装上 {@code RetroXfer}（FabricXfer 原文照搬），抽取口业务立刻整段可共用。
 * 逐句对下来两代真差异全住在**主线专属功能**（m229 转化桌 EMC/m230 升级/m233 按面断开）而不在
 * 抽取业务本身——全部收进带默认值的宿主口，本世代零实现即得蓝本行为。
 *
 * <p><b>transfer 触点一律走 {@link Xfer}（Object 句柄）</b>：本类零 fabric import（layer_gate ①），
 * 五口内脏由各加载器 bootstrap 安装（1.21=FabricXfer / 1.20.1=RetroXfer）。
 */
public final class ExtractPort {

    /** 邻接视图收集所需的世代面（{@link #scanAdjacent} 只吃这半张脸）。两个钩子都有默认值：
     *  1.20.1 恒默认（m233 按面断开与 m229 转化桌都是主线专属功能，P-C 到期各自补）。 */
    public interface Views {
        /** m233 按面断开：该面不对接（送出/回收/卖桌/计数四口径同断）。 */
        default boolean faceDisabled(Direction d) { return false; }
        /** m229 转化桌：无 BE 无 FTA 的特殊邻居，走 EMC 出售通道，计一个对接。 */
        default boolean isSellNeighbor(Level world, BlockPos np) { return false; }
    }

    /** 单拍全量宿主口：核心网络三口（世代 BE 类型差）+ m229 两钩子（默认关）。 */
    public interface Host extends Views {
        /** 相连存储核心的**位置**清单（数据线 BFS；调用方缓存 40t，见 {@link #cores}）。 */
        List<BlockPos> scanCores(Level world, BlockPos pos);
        /** 位置→已加载核心（跨拍绝不缓存 BE 引用，蓝本 loadedCoreAt 口径）；未加载/已拆返 null。 */
        StorageLedgerProbe coreAt(Level world, BlockPos p);
        /** m161c：核心的 FTA 视图（回收拍目的地；各世代自转自己的核心类型）。 */
        Object coreStorage(StorageLedgerProbe core);
        /** m229 本拍是否有在线卖手（有则"目标满"只是箱子满，EMC 无限，不置整拍收工标志）。 */
        default boolean sellerActive() { return false; }
        /** m229 余量卖给转化桌（FTA 目标优先=箱子先装，溢出才卖）；返回实际卖出件数。 */
        default long sellOverflow(ItemStack tpl, boolean exact, long remaining) { return 0; }
    }

    /** 邻接扫描结果：插入视图序列 + 可对接邻块数 + 是否贴着 ProjectEF 转化桌（m229 EMC 出售通道）。 */
    public record Adjacency(List<Object> targets, int blockCount, boolean sellTable) { }

    private final Host host;
    private final Runnable onChange; // setChanged 注入（m485 StorageLedger 同法）

    // ===== m225 抽取口状态（NBT 键在各世代壳里写读；键名 extractOn/pullMode/filter 两代同名同布局）=====
    private boolean extractOn = false;
    private final List<ItemStack> filter = new ArrayList<>(); // 模板 count 恒 1，≤9 条；空=全部抽取
    private int rrCursor = 0;                                  // 全部模式的类型轮转游标（跨拍公平，不持久化）
    private boolean opTargetsFull = false;                     // 本拍"目标已塞满"标志（extractSpec 置位，两模式统一收工）
    private long coresScanTick = Long.MIN_VALUE;               // 相连核心 40t 缓存（m108c 同款语义）
    private List<BlockPos> coresCache = List.of();
    private boolean pullMode = false; // m231 方向：false=送出(仓→机器/卖桌) true=回收(机器→仓)；单向无环

    public ExtractPort(Host host, Runnable onChange) {
        this.host = host;
        this.onChange = onChange;
    }

    public boolean extractOn() { return extractOn; }
    public void setExtractOn(boolean on) { extractOn = on; onChange.run(); }
    public boolean pullMode() { return pullMode; }
    public void setPullMode(boolean pull) { pullMode = pull; onChange.run(); } // m231

    /** 过滤模板视图（m226 界面 / m449 持物右键读写；写入方自行 setChanged）。 */
    public List<ItemStack> filterView() { return filter; }

    /** 读档专用（不触发 onChange；filter 由调用方经 {@link #filterView} 清+加，口径同旧）。 */
    public void loadState(boolean on, boolean pull) { this.extractOn = on; this.pullMode = pull; }

    /** m233：拓扑变了（按面断开切换），相连核心缓存立即作废（40t 缓存别撑到下一窗）。 */
    public void invalidateCores() { coresScanTick = Long.MIN_VALUE; }

    /** m224 邻接可抽取存储探测（走 Fabric Transfer API 标准口不做逐模组集成）→ m228 升级「六面视图」：
     *  贴线面只是查询视角之一——侧向机器（AvaritiaNeo 中子态素压缩机等）往往只在顶面暴露输入槽、
     *  其余面全是输出槽，线插侧面时旧逻辑拿到的唯一视图 insert 恒 0（实机反馈"插头接上了却导不进"）。
     *  现按 贴线面→其余五面 逐视角收集：单实例注册的模组按身份去重只收一次；侧向包装每面一实例、
     *  各按其面规则放行/拒绝——语义=六面各贴一只漏斗，线插哪面都能喂到收料面。六面全无才试无侧注册。
     *  自家网络方块排除口径不变（存储核心=左手倒右手 m161c；结构核心 Inventory 会被 Fabric 兜底捞到）。
     *  blockCount=有可对接视图的邻块数（界面"旁接存储"口径）。 */
    public static Adjacency scanAdjacent(Views v, Level world, BlockPos pos) {
        List<Object> targets = new ArrayList<>();
        int blocks = 0;
        boolean sellTable = false;
        if (world == null || world.isClientSide) return new Adjacency(targets, 0, false);
        for (Direction d : Direction.values()) {
            if (v.faceDisabled(d)) continue; // m233 断开面不对接（送出/回收/卖桌/计数四口径同断）
            BlockPos np = pos.relative(d);
            BlockEntity be = world.getBlockEntity(np);
            if (be != null && be.getClass().getName().startsWith("com.sdzjz")) continue; // 自家网络方块不作抽取目标
            if (v.isSellNeighbor(world, np)) {
                sellTable = true; blocks++; continue; // m229 转化桌：无 BE 无 FTA，走 EMC 出售通道，计一个对接
            }
            int before = targets.size();
            collectView(targets, world, np, d.getOpposite()); // 贴线面优先（多目标分发顺位保持旧口径）
            for (Direction q : Direction.values())
                if (q != d.getOpposite()) collectView(targets, world, np, q);
            if (targets.size() == before) collectView(targets, world, np, null); // 部分模组只登记无侧访问
            if (targets.size() > before) blocks++;
        }
        return new Adjacency(targets, blocks, sellTable);
    }

    private static void collectView(List<Object> out, Level world, BlockPos np, Direction side) {
        Object st = Xfer.find(world, np, side);
        if (st == null || (!Xfer.canInsert(st) && !Xfer.canExtract(st))) return; // m231 双向收：送出用可插视图、回收用可取视图
        for (Object s : out) if (s == st) return; // 身份去重：全面同实例注册的只收一次
        out.add(st);
    }

    /** 相连存储核心（数据线 BFS，40t 缓存防每拍裸扫——m218b 精确支路同教训；存位置逐拍 coreAt
     *  解引用，绝不缓存 BE 引用跨卸载）。 */
    public List<StorageLedgerProbe> cores(Level world, BlockPos pos) {
        if (coresScanTick == Long.MIN_VALUE || world.getGameTime() - coresScanTick >= 40) {
            coresScanTick = world.getGameTime();
            coresCache = host.scanCores(world, pos);
        }
        List<StorageLedgerProbe> out = new ArrayList<>(coresCache.size());
        for (BlockPos p : coresCache) {
            StorageLedgerProbe c = host.coreAt(world, p);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 单拍分派（相位闸/预算/卖手判定之后的全部工作；世代 tick 只管算 targets/cores/budget）。 */
    public void runPulse(List<Object> targets, List<? extends StorageLedgerProbe> cores, long budget) {
        if (pullMode) { doPull(cores, targets, budget); return; } // m231 回收：机器→仓（卖桌不参与）
        opTargetsFull = false;
        List<ItemStack> want = new ArrayList<>();
        for (ItemStack f : filter) if (!f.isEmpty()) want.add(f);
        if (want.isEmpty()) extractAll(cores, targets, budget);      // 空过滤=全部抽取（游标轮转）
        else for (ItemStack tpl : want) {                            // 白名单：逐模板抽
            budget -= extractSpec(cores, targets, tpl, budget);
            if (budget <= 0 || opTargetsFull) break;                 // 目标满：本拍收工（余下模板下拍再来）
        }
    }

    /** 按单个模板抽取：无附加数据=普通账本按 id；带附加数据=精确账本按模板（附魔书/药水连组件搬，
     *  m130 口径；判定走 ItemData.has 世代口，m487 血案同款替换）。塞不下的一律回账本绝不落地；
     *  返回实际搬动件数。 */
    private long extractSpec(List<? extends StorageLedgerProbe> cores, List<Object> targets, ItemStack tpl, long max) {
        if (tpl.isEmpty() || max <= 0) return 0;
        boolean exact = com.sdzjz.item.ItemData.has(tpl);
        String id = exact ? null : BuiltInRegistries.ITEM.getKey(tpl.getItem()).toString();
        long moved = 0;
        for (StorageLedgerProbe core : cores) {
            while (moved < max) {
                int ask = (int) Math.min(max - moved, Integer.MAX_VALUE);
                int take = exact ? core.withdrawExact(tpl, ask) : core.withdraw(id, ask);
                if (take <= 0) break;
                long ins = insertInto(targets, tpl, exact, take);
                if (ins < take) { // 目标满：余量回账本（绝不落地）；无卖手才置整拍收工标志——
                    // m229 有转化桌时"满"只是箱子满（EMC 无限），该物品无价卖不掉就跳去下一模板
                    if (!host.sellerActive()) opTargetsFull = true;
                    ItemStack back = exact ? tpl.copyWithCount((int) (take - ins)) : new ItemStack(tpl.getItem(), (int) (take - ins));
                    if (exact) core.depositExact(back); else core.deposit(back);
                    return moved + ins;
                }
                moved += ins;
            }
            if (moved >= max) break;
        }
        return moved;
    }

    /** 全部模式：普通账本 id 全表 + 精确账本模板全表拼成候选序列，游标轮转起步（跨拍公平）；
     *  一旦出现"取得出塞不进"即判目标满，整拍收工。id 物化走 tryParse（m443 同款防御：
     *  坏档里的怪 id 静默跳过不炸拍——主线原文是 parse，会抛，合一顺手补上这层）。 */
    private void extractAll(List<? extends StorageLedgerProbe> cores, List<Object> targets, long budget) {
        List<ItemStack> specs = new ArrayList<>();
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (StorageLedgerProbe core : cores) {
            ids.addAll(core.storeView().keySet());
            for (ItemStack t : core.exactTemplates()) specs.add(t.copyWithCount(1));
        }
        List<ItemStack> plain = new ArrayList<>(ids.size());
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id); // m443 同款防御（两代同名静态）
            if (rl == null) continue;
            Item it = BuiltInRegistries.ITEM.get(rl);
            ItemStack st = new ItemStack(it);
            if (!st.isEmpty()) plain.add(st);
        }
        plain.addAll(specs);
        if (plain.isEmpty()) return;
        int n = plain.size(), start = Math.floorMod(rrCursor, n);
        for (int k = 0; k < n && budget > 0; k++) {
            int i = (start + k) % n;
            budget -= extractSpec(cores, targets, plain.get(i), budget);
            rrCursor = i + 1;
            if (opTargetsFull) break; // 目标满：整拍收工，游标停在此处下拍续
        }
    }

    /** m231 回收拍：从邻接机器的可取视图抽货进相连存储核心（FTA 出口 m161c 双账本，附魔书/药水
     *  连组件正确入精确账本）。move 只搬"取得出且存得进"的量——仓容不足余量留在机器里
     *  绝不落地；过滤同 m225 语义（无附加数据模板=只收裸物品，带附加数据=连附加数据精确匹配，空=全收）。 */
    private void doPull(List<? extends StorageLedgerProbe> cores, List<Object> sources, long budget) {
        boolean anyFilter = false; // m404：空过滤=全收，给 null 省掉每候选一次 toStack（旧 pullWants 的惰性路径同义）
        for (ItemStack tpl : filter) if (!tpl.isEmpty()) { anyFilter = true; break; }
        java.util.function.Predicate<ItemStack> pred = anyFilter ? this::pullWants : null;
        for (Object src : sources) {
            if (budget <= 0) break;
            if (!Xfer.canExtract(src)) continue;
            for (StorageLedgerProbe core : cores) {
                if (budget <= 0) break;
                budget -= Xfer.move(src, host.coreStorage(core), pred, budget); // m434 dst 泛化为句柄
            }
        }
    }

    private boolean pullWants(ItemStack vs) {
        boolean any = false;
        for (ItemStack tpl : filter) {
            if (tpl.isEmpty()) continue;
            any = true;
            if (!com.sdzjz.item.ItemData.has(tpl)) { // 无附加数据模板=只收该 id 的裸物品（m225 口径）
                if (vs.is(tpl.getItem()) && !com.sdzjz.item.ItemData.has(vs)) return true;
            } else if (StackKey.same(vs, tpl)) return true; // 精确模板连附加数据匹配（m478 世代口）
        }
        return !any; // 空过滤=全收
    }

    private long insertInto(List<Object> targets, ItemStack tpl, boolean exact, long amount) {
        long done = 0;
        for (Object t : targets) {
            if (done >= amount) break;
            done += Xfer.insert(t, tpl, exact, amount - done);
        }
        if (done < amount) // m229 余量卖给转化桌：FTA 目标优先=箱子先装，溢出才卖（默认口恒 0）
            done += host.sellOverflow(tpl, exact, amount - done);
        return done;
    }
}
