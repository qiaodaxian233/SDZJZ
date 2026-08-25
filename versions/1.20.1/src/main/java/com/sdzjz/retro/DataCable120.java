package com.sdzjz.retro;

import com.sdzjz.config.SdzjzConfig;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * m444 刀③：数据线 BE 1.20.1 版——语义蓝本=Legacy {@code DataCableBlockEntity}（m225 抽取口主拍/
 * m228 六面视图/m231 双向收发，逐段对照新写）。m440 排刀稿口径：<b>本世代不设 Xfer 门面</b>，
 * transfer API 直接内联，五口语义照抄 FabricXfer 内脏（find=ItemStorage.SIDED.find、
 * canInsert/canExtract=supportsInsertion/Extraction、insert=单笔事务提交、move=StorageUtil.move
 * 谓词从 ItemStack 适配到 ItemVariant）——句柄直接用 Storage&lt;ItemVariant&gt; 类型，不走 Object。
 *
 * <p>Create（Fabric 移植走同一套 transfer API）的传送带/漏斗/置物台怼在数据线任意面：
 * 送出拍把网络账本里的物品塞给它（能给），回收拍把它里面的物品收进网络（能收）——你点名的
 * 机械动力互通就走这两拍，实机验收线见 DEVLOG m444。
 *
 * <p>本世代裁剪（P-C 到期补，非漏抄）：m226 配置屏与开屏工厂、m229 所有者/EMC 出售通道
 * （ProjectEF 兼容属 Legacy 专属）、m230 升级槽（effPeriod/effBudget 退基础配置值，键同源 common）、
 * m233 按面断开（链接器随 P-C）。filter 字段与 NBT 键（extractOn/pullMode/filter）与蓝本同名
 * 同布局保留——m449 起持物右键线即可增删白名单（配置屏 P-C 到位后共存）。
 */
public final class DataCable120 extends BlockEntity {

    // ===== m225 抽取口状态（NBT 持久化，键同蓝本）=====
    private boolean extractOn = false;
    private final List<ItemStack> filter = new ArrayList<>(); // 模板 count 恒 1；本世代恒空（见类注）
    private int rrCursor = 0;                                  // 全部模式的类型轮转游标（跨拍公平，不持久化）
    private boolean opTargetsFull = false;                     // 本拍"目标已塞满"标志（extractSpec 置位统一收工）
    private long coresScanTick = Long.MIN_VALUE;               // 相连核心 40t 缓存（m108c 同款语义）
    private List<BlockPos> coresCache = List.of();
    private boolean pullMode = false; // m231 方向：false=送出(仓→机器) true=回收(机器→仓)；单向无环
    private boolean endsHealed = false; // m469 端点自愈一次性闸（transient：每次加载重跑一遍，不落盘）

    public DataCable120(BlockPos pos, BlockState state) {
        super(RetroBlocks.DATA_CABLE_BE, pos, state);
    }

    public boolean extractOn() { return extractOn; }
    public void setExtractOn(boolean on) { extractOn = on; setChanged(); }
    public boolean pullMode() { return pullMode; }
    public void setPullMode(boolean pull) { pullMode = pull; setChanged(); } // m231

    /** 过滤模板视图（P-C 配置界面到位后读写；写入方自行 setChanged）。 */
    public List<ItemStack> filterView() { return filter; }

    // ===== m449 过滤器交互（配置屏 P-C 到位前的最小可用面；语义与 m225 一致：模板 count 恒 1，
    // 无 tag 模板=只对裸物品、带 tag=连 tag 精确匹配，空表=全抽/全收）=====
    public static final int FILTER_ADDED = 0, FILTER_REMOVED = 1, FILTER_FULL = 2;

    /** 加入/移出白名单（同款判定=isSameItemSameTags，与 pullWants/extractSpec 同口径）；
     *  上限 9 条（m226 蓝本容量同源）。改动即拓扑无关，无需作废核心缓存。 */
    public int filterToggle(ItemStack held) {
        if (held.isEmpty()) return FILTER_FULL; // 不可达（调用方已判），防御
        for (int i = 0; i < filter.size(); i++) {
            if (ItemStack.isSameItemSameTags(filter.get(i), held)) {
                filter.remove(i);
                setChanged();
                return FILTER_REMOVED;
            }
        }
        if (filter.size() >= 9) return FILTER_FULL;
        filter.add(held.copyWithCount(1));
        setChanged();
        return FILTER_ADDED;
    }

    /** 清空白名单（回全抽/全收态）。 */
    public void filterClear() {
        if (filter.isEmpty()) return;
        filter.clear();
        setChanged();
    }

    /** m228 六面视图邻接探测（蓝本 scanAdjacent 裁剪 EMC/断开面）：贴线面只是查询视角之一——
     *  侧向机器往往只在某一面暴露输入槽，按 贴线面→其余五面 逐视角收集，单实例注册的按身份去重
     *  只收一次，六面全无才试无侧注册；语义=六面各贴一只漏斗，线插哪面都能喂到收料面。
     *  自家网络方块排除口径同蓝本（存储核心=左手倒右手 m161c）。 */
    static List<Storage<ItemVariant>> scanAdjacent(Level world, BlockPos pos) {
        List<Storage<ItemVariant>> targets = new ArrayList<>();
        if (world == null || world.isClientSide) return targets;
        for (Direction d : Direction.values()) {
            BlockPos np = pos.relative(d);
            BlockEntity be = world.getBlockEntity(np);
            if (be != null && be.getClass().getName().startsWith("com.sdzjz")) continue; // 自家网络方块不作对接目标
            collectView(targets, world, np, d.getOpposite()); // 贴线面优先（多目标分发顺位保持蓝本口径）
            for (Direction q : Direction.values())
                if (q != d.getOpposite()) collectView(targets, world, np, q);
            if (targets.isEmpty()) collectView(targets, world, np, null); // 部分模组只登记无侧访问
        }
        return targets;
    }

    private static void collectView(List<Storage<ItemVariant>> out, Level world, BlockPos np, Direction side) {
        Storage<ItemVariant> st = ItemStorage.SIDED.find(world, np, side); // 五口内脏①find
        if (st == null || (!st.supportsInsertion() && !st.supportsExtraction())) return; // ②③m231 双向收：送出用可插、回收用可取
        for (Storage<ItemVariant> s : out) if (s == st) return; // 身份去重：全面同实例注册的只收一次
        out.add(st);
    }

    /** m225 抽取口主拍：pos 哈希移相（m218c 口径，多口不挤同一全局 tick），周期与批量走 common
     *  配置同源键（extractPortPeriodTicks/extractPortBatch，m230 升级槽随 P-C——本世代基础值）。 */
    public static void tick(Level world, BlockPos pos, BlockState state, DataCable120 be) {
        if (world.isClientSide) return;
        if (!be.endsHealed) be.endsHealed = DataCableBlock120.healEnds(world, pos, state); // m469 旧档端点自愈（一次性，不落盘）
        if (!be.extractOn) return;
        int period = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        if (Math.floorMod(world.getGameTime() + pos.hashCode(), period) != 0) return;
        be.pulse(world, pos);
    }

    /** 单拍主体（tick 相位闸之后的全部工作；拆出来让 GameTest 免等相位确定性直调）。 */
    void pulse(Level world, BlockPos pos) {
        List<Storage<ItemVariant>> targets = scanAdjacent(world, pos);
        if (targets.isEmpty()) return;
        List<StorageCore120> cores = cores(world, pos);
        if (cores.isEmpty()) return;
        long budget = Math.max(1, SdzjzConfig.get().extractPortBatch);
        if (pullMode) { doPull(cores, targets, budget); return; } // m231 回收：机器→仓
        opTargetsFull = false;
        List<ItemStack> want = new ArrayList<>();
        for (ItemStack f : filter) if (!f.isEmpty()) want.add(f);
        if (want.isEmpty()) extractAll(cores, targets, budget);      // 空过滤=全部抽取（游标轮转；本世代常态）
        else for (ItemStack tpl : want) {                             // 白名单：逐模板抽（P-C 界面到位后生效）
            budget -= extractSpec(cores, targets, tpl, budget);
            if (budget <= 0 || opTargetsFull) break;                  // 目标满：本拍收工（余下模板下拍再来）
        }
    }

    /** 相连存储核心（数据线 BFS，40t 缓存防每拍裸扫——m218b 同教训；存位置逐拍安全解引用，
     *  绝不缓存 BE 引用跨卸载，蓝本 cores 同款）。 */
    private List<StorageCore120> cores(Level world, BlockPos pos) {
        if (coresScanTick == Long.MIN_VALUE || world.getGameTime() - coresScanTick >= 40) {
            coresScanTick = world.getGameTime();
            List<BlockPos> ps = new ArrayList<>();
            for (StorageCore120 c : StorageCore120.connectedCores(world, pos)) ps.add(c.getBlockPos().immutable());
            coresCache = ps;
        }
        List<StorageCore120> out = new ArrayList<>(coresCache.size());
        for (BlockPos p : coresCache) {
            StorageCore120 c = StorageCore120.loadedCoreAt(world, p);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 按单个模板抽取（蓝本 extractSpec）：无 tag=普通账本按 id；带 tag=精确账本按模板（1.20.1
     *  分流口径同 m443 账本）。塞不下的一律回账本绝不落地；返回实际搬动件数。 */
    private long extractSpec(List<StorageCore120> cores, List<Storage<ItemVariant>> targets, ItemStack tpl, long max) {
        if (tpl.isEmpty() || max <= 0) return 0;
        boolean exact = tpl.hasTag();
        String id = exact ? null : BuiltInRegistries.ITEM.getKey(tpl.getItem()).toString();
        long moved = 0;
        for (StorageCore120 core : cores) {
            while (moved < max) {
                int ask = (int) Math.min(max - moved, Integer.MAX_VALUE);
                int take = exact ? core.withdrawExact(tpl, ask) : core.withdraw(id, ask);
                if (take <= 0) break;
                long ins = insertInto(targets, tpl, exact, take);
                if (ins < take) { // 目标满：余量回账本（绝不落地），整拍收工标志置位
                    opTargetsFull = true;
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

    /** 全部模式（蓝本 extractAll）：普通账本 id 全表 + 精确账本模板全表拼成候选序列，游标轮转起步
     *  （跨拍公平）；一旦出现"取得出塞不进"即判目标满，整拍收工。 */
    private void extractAll(List<StorageCore120> cores, List<Storage<ItemVariant>> targets, long budget) {
        List<ItemStack> specs = new ArrayList<>();
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (StorageCore120 core : cores) {
            ids.addAll(core.storeView().keySet());
            for (ItemStack t : core.exactTemplates()) specs.add(t.copyWithCount(1));
        }
        List<ItemStack> plain = new ArrayList<>(ids.size());
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id); // 1.20.1 对位静态 parse（m443 同款防御）
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

    /** m231 回收拍（蓝本 doPull）：从邻接机器的可取视图抽货进相连存储核心（FTA 出口 m161c 双账本，
     *  带 tag 件正确入精确账本）。StorageUtil.move 只搬"取得出且存得进"的量——仓容不足余量留在
     *  机器里绝不落地；过滤语义同 m225（无 tag 模板=只收裸物品，带 tag=连 tag 精确匹配，空=全收）。 */
    private void doPull(List<StorageCore120> cores, List<Storage<ItemVariant>> sources, long budget) {
        boolean anyFilter = false; // 空过滤=全收，给恒真谓词省掉每候选一次 toStack（蓝本 m404 同义）
        for (ItemStack tpl : filter) if (!tpl.isEmpty()) { anyFilter = true; break; }
        java.util.function.Predicate<ItemVariant> pred = anyFilter ? v -> pullWants(v.toStack()) : v -> true; // 五口内脏⑤谓词适配
        for (Storage<ItemVariant> src : sources) {
            if (budget <= 0) break;
            if (!src.supportsExtraction()) continue;
            for (StorageCore120 core : cores) {
                if (budget <= 0) break;
                budget -= StorageUtil.move(src, core.fabricStorage(), pred, budget, null); // 0.92 签名原文核对（上游实证）
            }
        }
    }

    private boolean pullWants(ItemStack vs) {
        boolean any = false;
        for (ItemStack tpl : filter) {
            if (tpl.isEmpty()) continue;
            any = true;
            if (!tpl.hasTag()) { // 无 tag 模板=只收该 id 的裸物品（m225 口径的 tag 版）
                if (vs.is(tpl.getItem()) && !vs.hasTag()) return true;
            } else if (ItemStack.isSameItemSameTags(vs, tpl)) return true; // 带 tag 模板连 tag 匹配
        }
        return !any; // 空过滤=全收
    }

    /** 五口内脏④insert（照 FabricXfer 原文：模板→变体，单笔事务提交）；逐目标顺序灌到额度用尽。 */
    private static long insertInto(List<Storage<ItemVariant>> targets, ItemStack tpl, boolean exact, long amount) {
        long done = 0;
        ItemVariant v = exact ? ItemVariant.of(tpl) : ItemVariant.of(tpl.getItem()); // 1.20.1 变体身份=物品+tag
        for (Storage<ItemVariant> t : targets) {
            if (done >= amount) break;
            if (!t.supportsInsertion()) continue;
            try (Transaction tx = Transaction.openOuter()) {
                long ins = t.insert(v, amount - done, tx);
                tx.commit();
                done += ins;
            }
        }
        return done;
    }

    // ===== NBT：1.20.1 签名（m443 同款），键 extractOn/pullMode/filter 与蓝本同名同布局
    // （owner/offFaces/upN 三组键随 P-C 的 m229/m233/m230 到期再写，缺键读=各字段默认，向前兼容）=====
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putBoolean("extractOn", extractOn);
        nbt.putBoolean("pullMode", pullMode); // m231 方向
        ListTag fl = new ListTag(); // 过滤模板持久化（精确账本 m130 同款 encode）
        for (ItemStack f : filter) if (!f.isEmpty()) fl.add(f.save(new CompoundTag()));
        nbt.put("filter", fl);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        extractOn = nbt.getBoolean("extractOn");
        pullMode = nbt.getBoolean("pullMode"); // m231
        filter.clear();
        ListTag fl = nbt.getList("filter", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            ItemStack t = ItemStack.of(fl.getCompound(i)); // 1.20.1 对位 parse().orElse（失败即 EMPTY）
            if (!t.isEmpty()) filter.add(t.copyWithCount(1)); // 解析失败/物品已卸载静默跳过，不炸档
        }
    }
}
