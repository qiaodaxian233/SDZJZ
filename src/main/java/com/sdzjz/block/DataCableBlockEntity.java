package com.sdzjz.block;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 数据线方块实体：能量脉冲动画的渲染载体（BER 挂点）；m225 起兼作「抽取口」——扳手启用后，
 *  周期性把相连存储核心账本里的物品（按过滤，空=全部）塞进邻接的任意 Fabric Transfer API 存储。
 *  m226：兼作抽取口配置屏的开屏工厂（扳手右键 openHandledScreen，数据面板同款三方法）。 */
public class DataCableBlockEntity extends BlockEntity
        implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BlockPos> {

    // ===== m225 抽取口状态（NBT 持久化；过滤模板由 m226 配置界面编辑）=====
    private boolean extractOn = false;
    private final List<ItemStack> filter = new ArrayList<>(); // 模板 count 恒 1，≤9 条；空=全部抽取
    private int rrCursor = 0;                                  // 全部模式的类型轮转游标（跨拍公平，不持久化）
    private boolean opTargetsFull = false;                     // 本拍"目标已塞满"标志（extractSpec 置位，两模式统一收工）
    private long coresScanTick = Long.MIN_VALUE;               // 相连核心 40t 缓存（m108c 同款语义）
    private List<BlockPos> coresCache = List.of();
    private java.util.UUID owner = null;                       // m229 端口所有者（EMC 记谁账上；配置即认领）
    /** m230 升级槽（0=速度 1=数量 2=并发；级数=槽内件数，配置界面装取）。 */
    public final net.minecraft.world.SimpleContainer upgrades = new net.minecraft.world.SimpleContainer(3);
    private boolean pullMode = false; // m231 方向：false=送出(仓→机器/卖桌) true=回收(机器→仓)；单向无环
    private int offFaces = 0;         // m233 按面断开位掩码（bit=Direction.getId()；链接器潜行右键手臂切）
    private net.minecraft.server.level.ServerPlayer opSeller = null; // 本拍在线卖手（瞬态不持久化）

    public DataCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_CABLE_BE, pos, state);
        upgrades.addListener(inv -> this.markDirty()); // m230 升级槽改动落盘（markDirty 自带 world==null 守卫）
    }

    /** m230 生效周期：基础周期 ÷ (1+速度级)，触底 1t（触底后的富余速度折进单拍批量，见 effBudget——
     *  m99 教训"到顶之后玩家再投入会怎样"：每一件升级永远有增益，绝不静默无效）。 */
    public int effPeriod() {
        int base = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        return Math.max(1, base / (1 + upgrades.getStack(0).getCount()));
    }

    /** m230 单拍批量：基础批量 × (1+数量级) × (1+并发级) × 速度触底折算倍率。 */
    public long effBudget() {
        int base = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        long speedFactor = 1 + upgrades.getStack(0).getCount();
        long fold = speedFactor <= base ? 1 : (speedFactor + base - 1) / base; // 超出 1t 触底的速度折进批量（向上取整保单调）
        return Math.max(1, SdzjzConfig.get().extractPortBatch)
                * (1 + upgrades.getStack(1).getCount())
                * (1 + upgrades.getStack(2).getCount()) * fold;
    }

    public boolean extractOn() { return extractOn; }
    public void setExtractOn(boolean on) { extractOn = on; markDirty(); }
    /** m229 认领所有者：谁配置这个口（开界面/潜行开关），转化桌出售的 EMC 就记谁账上。 */
    public void claimOwner(net.minecraft.world.entity.player.Player p) {
        if (p != null && !p.getUuid().equals(owner)) { owner = p.getUuid(); markDirty(); }
    }
    public java.util.UUID owner() { return owner; }
    public boolean pullMode() { return pullMode; }
    public void setPullMode(boolean pull) { pullMode = pull; markDirty(); } // m231

    // ===== m233 按面断开 =====
    public boolean faceDisabled(Direction d) { return (offFaces & (1 << d.getId())) != 0; }
    public void toggleFace(Direction d) {
        offFaces ^= (1 << d.getId());
        coresScanTick = Long.MIN_VALUE; // 拓扑变了，相连核心缓存立即作废（40t 缓存别撑到下一窗）
        markDirty();
    }

    /** m233 网络通行判定：cur→np 这条边是否被数据线按面断开（任一端是数据线且该面被禁=不通）。
     *  全部走线（BFS/贴邻）统一插闸用；**必须在 seen 标记之前调用**——先标 seen 再判断会把
     *  经其他路径可达的节点一并堵死。 */
    public static boolean linkBlocked(net.minecraft.world.level.BlockGetter world, BlockPos cur, Direction d, BlockPos np) {
        if (world.getBlockEntity(cur) instanceof DataCableBlockEntity c && c.faceDisabled(d)) return true;
        if (world.getBlockEntity(np) instanceof DataCableBlockEntity c2 && c2.faceDisabled(d.getOpposite())) return true;
        return false;
    }
    /** 过滤模板视图（m226 界面读写；写入方自行 markDirty）。 */
    public List<ItemStack> filterView() { return filter; }

    // ===== m226 抽取口配置屏开屏工厂（数据面板同款三方法）=====
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("sdzjz.extract_port.title");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv,
                                                         net.minecraft.world.entity.player.Player player) {
        return new com.sdzjz.screen.ExtractPortScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
        return this.pos;
    }

    /** m224 邻接可抽取存储探测（走 Fabric Transfer API 标准口不做逐模组集成）→ m228 升级「六面视图」：
     *  贴线面只是查询视角之一——侧向机器（AvaritiaNeo 中子态素压缩机等）往往只在顶面暴露输入槽、
     *  其余面全是输出槽，线插侧面时旧逻辑拿到的唯一视图 insert 恒 0（实机反馈"插头接上了却导不进"）。
     *  现按 贴线面→其余五面 逐视角收集：单实例注册的模组按身份去重只收一次；侧向包装每面一实例、
     *  各按其面规则放行/拒绝——语义=六面各贴一只漏斗，线插哪面都能喂到收料面。六面全无才试无侧注册。
     *  自家网络方块排除口径不变（存储核心=左手倒右手 m161c；结构核心 Inventory 会被 Fabric 兜底捞到）。
     *  blockCount=有可对接视图的邻块数（界面"旁接存储"口径）。 */
    public static Adjacency scanAdjacent(Level world, BlockPos pos) {
        List<Object> targets = new ArrayList<>(); // m404 不透明句柄（Fabric=Storage<ItemVariant>，Neo=IItemHandler）
        int blocks = 0;
        boolean sellTable = false;
        if (world == null || world.isClient) return new Adjacency(targets, 0, false);
        DataCableBlockEntity self = world.getBlockEntity(pos) instanceof DataCableBlockEntity c ? c : null;
        for (Direction d : Direction.values()) {
            if (self != null && self.faceDisabled(d)) continue; // m233 断开面不对接（送出/回收/卖桌/计数四口径同断）
            BlockPos np = pos.offset(d);
            BlockEntity be = world.getBlockEntity(np);
            if (be != null && be.getClass().getName().startsWith("com.sdzjz")) continue; // 自家网络方块不作抽取目标
            if (com.sdzjz.compat.ProjectEFCompat.isTransmutationTable(world.getBlockState(np))) {
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
        Object st = com.sdzjz.storage.Xfer.find(world, np, side);
        if (st == null || (!com.sdzjz.storage.Xfer.canInsert(st) && !com.sdzjz.storage.Xfer.canExtract(st))) return; // m231 双向收：送出用可插视图、回收用可取视图
        for (Object s : out) if (s == st) return; // 身份去重：全面同实例注册的只收一次
        out.add(st);
    }

    /** 邻接扫描结果：插入视图序列 + 可对接邻块数 + 是否贴着 ProjectEF 转化桌（m229 EMC 出售通道）。 */
    public record Adjacency(List<Object> targets, int blockCount, boolean sellTable) {}

    /** m225 抽取口主拍：pos 哈希移相（m218c 口径，多口不挤同一全局 tick），每拍最多搬 extractPortBatch 件。 */
    public static void tick(Level world, BlockPos pos, BlockState state, DataCableBlockEntity be) {
        if (world.isClient || !be.extractOn) return;
        int period = be.effPeriod(); // m230 升级生效
        if (Math.floorMod(world.getTime() + pos.hashCode(), period) != 0) return;
        Adjacency adj = scanAdjacent(world, pos);
        List<Object> targets = adj.targets();
        be.opSeller = null; // m229 本拍卖手：贴桌+已认领+所有者在线（离线提供者不可变，写=白写，不卖留货）
        if (!be.pullMode && adj.sellTable() && be.owner != null && world.getServer() != null)
            be.opSeller = world.getServer().getPlayerManager().getPlayer(be.owner);
        if (targets.isEmpty() && be.opSeller == null) return;
        List<StorageCoreBlockEntity> cores = be.cores(world, pos);
        if (cores.isEmpty()) return;
        long budget = be.effBudget(); // m230 升级生效
        if (be.pullMode) { be.doPull(cores, targets, budget); return; } // m231 回收：机器→仓（卖桌不参与）
        be.opTargetsFull = false;
        List<ItemStack> want = new ArrayList<>();
        for (ItemStack f : be.filter) if (!f.isEmpty()) want.add(f);
        if (want.isEmpty()) be.extractAll(cores, targets, budget);      // 空过滤=全部抽取（游标轮转）
        else for (ItemStack tpl : want) {                                // 白名单：逐模板抽
            budget -= be.extractSpec(cores, targets, tpl, budget);
            if (budget <= 0 || be.opTargetsFull) break;                  // 目标满：本拍收工（余下模板下拍再来）
        }
    }

    /** 相连存储核心（数据线 BFS，40t 缓存防每拍裸扫——m218b 精确支路同教训；存位置逐拍 loadedCoreAt
     *  解引用，绝不缓存 BE 引用跨卸载）。 */
    private List<StorageCoreBlockEntity> cores(Level world, BlockPos pos) {
        if (coresScanTick == Long.MIN_VALUE || world.getTime() - coresScanTick >= 40) {
            coresScanTick = world.getTime();
            List<BlockPos> ps = new ArrayList<>();
            for (StorageCoreBlockEntity c : StorageCoreBlockEntity.connectedCores(world, pos)) ps.add(c.getPos().toImmutable());
            coresCache = ps;
        }
        List<StorageCoreBlockEntity> out = new ArrayList<>(coresCache.size());
        for (BlockPos p : coresCache) {
            StorageCoreBlockEntity c = StorageCoreBlockEntity.loadedCoreAt(world, p);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 按单个模板抽取：无组件=普通账本按 id；带组件=精确账本按模板（附魔书/药水连组件搬，m130 口径）。
     *  塞不下的一律回账本绝不落地；返回实际搬动件数。 */
    private long extractSpec(List<StorageCoreBlockEntity> cores, List<Object> targets, ItemStack tpl, long max) {
        if (tpl.isEmpty() || max <= 0) return 0;
        boolean exact = !tpl.getComponentChanges().isEmpty();
        String id = exact ? null : BuiltInRegistries.ITEM.getId(tpl.getItem()).toString();
        long moved = 0;
        for (StorageCoreBlockEntity core : cores) {
            while (moved < max) {
                int ask = (int) Math.min(max - moved, Integer.MAX_VALUE);
                int take = exact ? core.withdrawExact(tpl, ask) : core.withdraw(id, ask);
                if (take <= 0) break;
                long ins = insertInto(targets, tpl, exact, take);
                if (ins < take) { // 目标满：余量回账本（绝不落地）；无卖手才置整拍收工标志——
                    // m229 有转化桌时"满"只是箱子满（EMC 无限），该物品无价卖不掉就跳去下一模板
                    if (opSeller == null) opTargetsFull = true;
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
     *  一旦出现"取得出塞不进"即判目标满，整拍收工。 */
    private void extractAll(List<StorageCoreBlockEntity> cores, List<Object> targets, long budget) {
        List<ItemStack> specs = new ArrayList<>();
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (StorageCoreBlockEntity core : cores) {
            ids.addAll(core.storeView().keySet());
            for (ItemStack t : core.exactTemplates()) specs.add(t.copyWithCount(1));
        }
        List<ItemStack> plain = new ArrayList<>(ids.size());
        for (String id : ids) {
            net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.of(id));
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
     *  连组件正确入精确账本）。StorageUtil.move 只搬"取得出且存得进"的量——仓容不足余量留在机器里
     *  绝不落地；过滤同 m225 语义（无组件模板=只收裸物品，带组件=连组件精确匹配，空=全收）。 */
    private void doPull(List<StorageCoreBlockEntity> cores, List<Object> sources, long budget) {
        boolean anyFilter = false; // m404：空过滤=全收，给 null 省掉每候选一次 toStack（旧 pullWants 的惰性路径同义）
        for (ItemStack tpl : filter) if (!tpl.isEmpty()) { anyFilter = true; break; }
        java.util.function.Predicate<ItemStack> pred = anyFilter ? this::pullWants : null;
        for (Object src : sources) {
            if (budget <= 0) break;
            if (!com.sdzjz.storage.Xfer.canExtract(src)) continue;
            for (StorageCoreBlockEntity core : cores) {
                if (budget <= 0) break;
                budget -= com.sdzjz.storage.Xfer.moveToCore(src, core, pred, budget);
            }
        }
    }

    private boolean pullWants(ItemStack vs) {
        boolean any = false;
        for (ItemStack tpl : filter) {
            if (tpl.isEmpty()) continue;
            any = true;
            if (tpl.getComponentChanges().isEmpty()) { // 无组件模板=只收该 id 的裸物品（m225 口径）
                if (vs.isOf(tpl.getItem()) && vs.getComponentChanges().isEmpty()) return true;
            } else if (ItemStack.areItemsAndComponentsEqual(vs, tpl)) return true; // 精确模板连组件匹配
        }
        return !any; // 空过滤=全收
    }

    private long insertInto(List<Object> targets, ItemStack tpl, boolean exact, long amount) {
        long done = 0;
        for (Object t : targets) {
            if (done >= amount) break;
            done += com.sdzjz.storage.Xfer.insert(t, tpl, exact, amount - done);
        }
        if (done < amount && opSeller != null) { // m229 余量卖给转化桌：FTA 目标优先=箱子先装，溢出才卖
            long unit = com.sdzjz.compat.ProjectEFCompat.unitValue(
                    exact ? tpl : new ItemStack(tpl.getItem()));
            if (unit > 0) {
                long n = Math.min(amount - done, Long.MAX_VALUE / 2 / unit); // 天价物×大批量防溢出
                if (n > 0 && com.sdzjz.compat.ProjectEFCompat.credit(opSeller, unit * n))
                    done += n; // 物品湮灭为 EMC（卖出=转化桌语义，非丢弃）
            }
        }
        return done;
    }

    @Override
    protected void writeNbt(CompoundTag nbt, HolderLookup.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putBoolean("extractOn", extractOn);
        if (owner != null) nbt.putUuid("owner", owner); // m229 所有者
        nbt.putBoolean("pullMode", pullMode); // m231 方向
        if (offFaces != 0) nbt.putInt("offFaces", offFaces); // m233
        for (int i = 0; i < 3; i++) // m230 升级槽（定槽键，空不写）
            if (!upgrades.getStack(i).isEmpty()) nbt.put("up" + i, upgrades.getStack(i).encode(lookup));
        ListTag fl = new ListTag(); // 过滤模板持久化（精确账本 m130 同款 encode）
        for (ItemStack f : filter) if (!f.isEmpty()) fl.add(f.encode(lookup));
        nbt.put("filter", fl);
    }

    @Override
    protected void readNbt(CompoundTag nbt, HolderLookup.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        extractOn = nbt.getBoolean("extractOn");
        owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null; // m229
        pullMode = nbt.getBoolean("pullMode"); // m231
        offFaces = nbt.getInt("offFaces"); // m233
        for (int i = 0; i < 3; i++) { // m230
            upgrades.setStack(i, nbt.contains("up" + i)
                    ? ItemStack.fromNbt(lookup, nbt.getCompound("up" + i)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        }
        filter.clear();
        ListTag fl = nbt.getList("filter", Tag.COMPOUND_TYPE);
        for (int i = 0; i < fl.size(); i++) {
            ItemStack t = ItemStack.fromNbt(lookup, fl.getCompound(i)).orElse(ItemStack.EMPTY);
            if (!t.isEmpty()) filter.add(t.copyWithCount(1)); // 解析失败/物品已卸载静默跳过，不炸档
        }
    }
}
