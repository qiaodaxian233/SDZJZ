package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.screen.DataPanelScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据面板：存储终端（不自带存储）。经网络访问相连的存储核心，聚合显示/存取。 */
public class DataPanelBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, com.sdzjz.machine.StorageAccess {

    public static final int PAGE = 54;
    private String searchFilter = "";
    private int scrollRow = 0;
    private int filteredCount = 0;
    private int refreshTicker = 0;
    public final SimpleInventory display = new SimpleInventory(PAGE);
    /** m126a：合成网格常驻方块（学 AE2 CraftingTerminalPart，代码自写）——关界面模板不清空，
     *  重开即接着合；多人共开同一面板共用同一网格（AE2 同款语义）。随 NBT 持久化，拆方块散落。 */
    public final com.sdzjz.screen.CraftGridInventory craftGrid = new com.sdzjz.screen.CraftGridInventory(); // m201 换挂 RecipeInputInventory 的子类（持久化路径零变化）

    public DataPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_PANEL_BE, pos, state);
        craftGrid.addListener(inv -> this.markDirty()); // 网格改动落盘（markDirty 自带 world==null 守卫）
    }

    public static void tick(World world, BlockPos pos, BlockState state, DataPanelBlockEntity be) {
        if (world.isClient) return;
        if (be.viewers <= 0) return; // m107a：无人查看不聚合——闲置面板零 BFS 空转（存取走 live 路径不受影响）
        // 节流：refreshDisplay 内部要 BFS 聚合存储核心，每 tick 跑是卡顿机器；改每 10 tick。
        if (++be.refreshTicker % 10 != 0) return;
        be.refreshDisplay();
    }

    // m107a：打开界面的玩家计数。handler 服务端构造 +1（并立即刷一次，打开不空白），onClosed -1。
    private int viewers = 0;
    public void addViewer() { viewers++; coresCacheTime = -1000; refreshDisplay(); } // m108c：开界面强刷网络缓存
    public void removeViewer() { if (viewers > 0) viewers--; }

    // m108c：cores() 此前每次调用全新 BFS——机器供料/落库/熔炉扫描/经验/计数全走它，
    // 高产线（用户实测 104.8M/分）下一 tick 能打出几十趟 BFS。改 40t 缓存（与画布端点扫描同节奏）；
    // 缓存里出现已拆除核心立即重建；开界面时强制刷新（addViewer 置 -1000，m90 教训：哨兵不用 MIN_VALUE 防溢出）。
    private List<StorageCoreBlockEntity> coresCache;
    private long coresCacheTime = -1000;

    private List<StorageCoreBlockEntity> cores() {
        long now = (this.world != null) ? this.world.getTime() : 0;
        if (coresCache != null && now - coresCacheTime < 40) {
            boolean ok = true;
            for (StorageCoreBlockEntity c : coresCache) if (c.isRemoved()) { ok = false; break; }
            if (ok) return coresCache;
        }
        coresCache = StorageCoreBlockEntity.connectedCores(this.world, this.pos);
        coresCacheTime = now;
        return coresCache;
    }

    /** m290 升 public：m289 库存摘要蹭这条缓存链路（cores() 40t 缓存），别再裸 BFS——
     *  m108c 治过的病不许复发。副作用=顺手刷新 typesUsed/typesCap/xp 三缓存为真值，多调无害。 */
    public LinkedHashMap<String, Long> aggregate() {
        LinkedHashMap<String, Long> agg = new LinkedHashMap<>();
        int used = 0, coreCount = 0; long cap = 0; boolean unlimited = false; // m97/m98
        long xp = 0; // m107a：经验总量顺手统计（复用同一次 BFS）
        for (StorageCoreBlockEntity core : cores()) {
            coreCount++;
            used += core.usedTypes(); // m130：精确条目同占类型额度
            xp += core.xpBank();
            int mt = core.maxTypes();
            if (mt == Integer.MAX_VALUE) unlimited = true; else cap += mt; // 防 MAX_VALUE 求和溢出
            for (Map.Entry<String, Long> e : core.storeView().entrySet())
                agg.merge(e.getKey(), e.getValue(), Long::sum);
        }
        typesUsedCache = Math.min(used, 65534);
        typesCapCache = coreCount == 0 ? 0 : (unlimited ? 0xFFFF : (int) Math.min(cap, 65534L));
        xpCache = xp;
        return agg;
    }

    /** m97/m98：全网类型用量缓存（属性走 16 位通道，故哨兵：cap 0=无存储核心，0xFFFF=无限，其余=上限和）。 */
    private int typesUsedCache, typesCapCache;
    public int typesUsed() { return typesUsedCache; }
    public int typesCap()  { return typesCapCache; }

    public long count(String id) {
        long n = 0;
        for (StorageCoreBlockEntity core : cores()) n += core.count(id);
        return n;
    }

    /** 存入：塞进第一个收得下的存储核心。 */
    public void deposit(ItemStack stack) {
        if (this.world != null && this.world.isClient) return; // m112 保险丝：账本只在服务端
        if (stack.isEmpty()) return;
        for (StorageCoreBlockEntity core : cores()) {
            core.deposit(stack);
            if (stack.isEmpty()) return;
        }
    }

    /** 取出：跨核心累计取，返回实际取出数量。 */
    // ===== m80c 经验库（聚合网络全部核心）=====
    private long xpCache = 0; // m107a：此前 xpTotal 每次 BFS，且属性通道每 tick 读 2 次=每秒 40 次 BFS——改读缓存
    public long xpTotal() { return xpCache; }
    /** 存入：进网络第一个核心；无核心返回 false（不吞玩家经验）。 */
    public boolean xpDeposit(long points) {
        for (StorageCoreBlockEntity core : cores()) { core.xpAdd(points); xpCache = StorageCoreBlockEntity.satAdd(xpCache, points); return true; } // m273 饱和加法
        return false;
    }
    /** 取出至多 max 点（跨核心），返回实际取出。 */
    public long xpWithdraw(long max) {
        long got = 0;
        for (StorageCoreBlockEntity core : cores()) {
            got += core.xpTake(max - got);
            if (got >= max) break;
        }
        xpCache = Math.max(0, xpCache - got);
        return got;
    }

    /** m218 精确账本支路专用出口：暴露 40t 缓存的核心清单（m108c 同款语义：拆核即重建、开界面强刷）。
     *  此前 SCBE 精确拉料每逻辑节点每 5t 直呼 connectedCores 裸 BFS（4096 上限逐格 getBlockEntity），
     *  绕开了这层缓存——多核心大网络下是 tick 大户。 */
    public List<StorageCoreBlockEntity> coresView() { return cores(); }

    // m218 聚合视图缓存：revSum=各核心 storeRev 求和（rev 单调只增，和相等⇔全都没动过）+ 核心数双指纹。
    // 同 tick 恒复用（调用侧本就循环首快照、value 只作试探上限、真实量以 withdraw 返回为准——语义等价）；
    // 跨 tick 指纹不变也复用（真没变，精确）。旧行为=每调全量重建，panelViewCache=false 一键回退。
    private java.util.LinkedHashMap<String, Long> viewCache;
    private long viewCacheTime = -1000, viewCacheRevSum = -1;
    private int viewCacheCoreN = -1;

    @Override
    public java.util.Map<String, Long> storeView() { // 聚合快照：万能熔炉"接什么烧什么"扫描/逻辑节点拉料用
        List<StorageCoreBlockEntity> cs = cores();
        if (!com.sdzjz.config.SdzjzConfig.get().panelViewCache) {
            java.util.LinkedHashMap<String, Long> merged = new java.util.LinkedHashMap<>();
            for (StorageCoreBlockEntity core : cs)
                for (var e : core.storeView().entrySet())
                    merged.merge(e.getKey(), e.getValue(), Long::sum);
            return merged;
        }
        long now = (this.world != null) ? this.world.getTime() : 0;
        long rs = 0;
        for (StorageCoreBlockEntity core : cs) rs += core.storeRev();
        if (viewCache != null && (now == viewCacheTime || (rs == viewCacheRevSum && cs.size() == viewCacheCoreN)))
            return viewCache;
        java.util.LinkedHashMap<String, Long> merged = new java.util.LinkedHashMap<>();
        for (StorageCoreBlockEntity core : cs)
            for (var e : core.storeView().entrySet())
                merged.merge(e.getKey(), e.getValue(), Long::sum);
        viewCache = merged;
        viewCacheTime = now;
        viewCacheRevSum = rs;
        viewCacheCoreN = cs.size();
        return merged;
    }

    public int withdraw(String id, int amount) {
        if (this.world != null && this.world.isClient) return 0; // m112 保险丝：账本只在服务端
        int got = 0;
        for (StorageCoreBlockEntity core : cores()) {
            if (got >= amount) break;
            got += core.withdraw(id, amount - got);
        }
        return got;
    }

    /** m130：精确取出（按物品+组件跨核心累计），返回实际取出。 */
    public int withdrawExact(ItemStack template, int amount) {
        if (this.world != null && this.world.isClient) return 0; // m112 保险丝同款
        int got = 0;
        for (StorageCoreBlockEntity core : cores()) {
            if (got >= amount) break;
            got += core.withdrawExact(template, amount - got);
        }
        return got;
    }

    private java.util.Set<String> matchedIds = java.util.Set.of();

    // m267 视图包 DoS 护栏（外部审计第二条）：入包边界钳制 + 变化检测 + 每玩家节流。
    public static final int VIEW_SEARCH_MAX = 128;   // 搜索词最长
    public static final int VIEW_MATCHED_MAX = 256;  // 匹配 id 列表最长
    public static final int VIEW_ID_MAX = 128;       // 单个 id 最长
    private long lastViewTick = Long.MIN_VALUE;      // 上次真刷新的世界刻（≥2t 才接下一次）

    /** m267：越界即钳（不是拒收——正常长搜索词照样能用，只是不给无限长），返回清洗后的集合。
     *  合法性交给 Identifier.tryParse：非法 id 直接丢，不进 Set 也不参与后续比对。 */
    private static java.util.Set<String> sanitizeMatched(java.util.List<String> matched) {
        if (matched == null || matched.isEmpty()) return java.util.Set.of();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String id : matched) {
            if (out.size() >= VIEW_MATCHED_MAX) break;
            if (id == null || id.isEmpty() || id.length() > VIEW_ID_MAX) continue;
            if (net.minecraft.util.Identifier.tryParse(id) == null) continue; // 非法 id 丢弃
            out.add(id);
        }
        return out.isEmpty() ? java.util.Set.of() : java.util.Set.copyOf(out);
    }

    public void setView(String search, int scroll, java.util.List<String> matched) {
        String sf = search == null ? "" : (search.length() > VIEW_SEARCH_MAX ? search.substring(0, VIEW_SEARCH_MAX) : search);
        int sr = Math.max(0, Math.min(scroll, 1_000_000)); // 包处理阶段就钳（审计点名）
        java.util.Set<String> ms = sanitizeMatched(matched);
        boolean same = sf.equals(this.searchFilter) && sr == this.scrollRow && ms.equals(this.matchedIds);
        if (same) return; // 值没变=一次全量 refreshDisplay 都不欠（刷屏包最大的一刀）
        this.searchFilter = sf;
        this.scrollRow = sr;
        this.matchedIds = ms;
        long now = this.world != null ? this.world.getTime() : 0L;
        if (this.world != null && now - lastViewTick < 2L) return; // 每玩家 ≥2t 一次真刷新；
        // 值已落字段，下一拍的 10t 节拍刷新会带上（不丢更新，只是最坏晚半秒）
        lastViewTick = now;
        refreshDisplay();
    }

    public int filteredRows() { return (filteredCount + 8) / 9; }

    /** m130：展示条目——tpl==null 为普通(id账本)，否则为精确件(模板账本，count=1)。 */
    private static final class DispEnt {
        final String id; final ItemStack tpl; long n;
        DispEnt(String id, ItemStack tpl, long n) { this.id = id; this.tpl = tpl; this.n = n; }
    }

    public void refreshDisplay() { // m111 升 public：光标存取后 handler 即时刷新，不等 10t 节拍
        // m112 保险丝：客户端 BE 账本恒空，跑聚合=把展示页 54 格全写 EMPTY 且服务端不知情无从纠正（视频 bug）。
        // 客户端展示页只允许原版槽位同步来写。
        if (this.world == null || this.world.isClient) return;
        java.util.List<DispEnt> all = new java.util.ArrayList<>();
        LinkedHashMap<String, Long> agg = aggregate();
        for (Map.Entry<String, Long> e : agg.entrySet()) all.add(new DispEnt(e.getKey(), null, e.getValue()));
        // m130：精确条目跨核心按「物品+组件」合并后并入同一张列表
        // m267 性能（外部审计第 4 条）：旧法对每个模板线性扫已合并列表=O(n²)，带组件物品一多就塌。
        // 改 ItemVariant 作哈希键（Fabric Transfer 的物品+组件不可变键，equals/hashCode 现成、
        // 在树先例 DataCableBlockEntity）——平均 O(n)。合并顺序仍按首见先后（LinkedHashMap），
        // 与旧版一致，排序键不变故展示顺序零漂移。
        java.util.LinkedHashMap<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant, DispEnt> exactMap =
                new java.util.LinkedHashMap<>();
        for (StorageCoreBlockEntity core : cores()) {
            java.util.List<ItemStack> tpls = core.exactTemplates();
            for (int k = 0; k < tpls.size(); k++) {
                ItemStack t = tpls.get(k); long n = core.exactCount(k);
                var key = net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(t);
                DispEnt d = exactMap.get(key);
                if (d != null) d.n += n;
                else exactMap.put(key, new DispEnt(Registries.ITEM.getId(t.getItem()).toString(), t.copyWithCount(1), n));
            }
        }
        all.addAll(exactMap.values());
        java.util.List<DispEnt> filtered = new java.util.ArrayList<>();
        String q = searchFilter == null ? "" : searchFilter.toLowerCase();
        for (DispEnt d : all)
            if (q.isEmpty() || d.id.toLowerCase().contains(q) || matchedIds.contains(d.id)) filtered.add(d);
        filtered.sort((a, b) -> { // m83：ME 式排序，存量多的排前面；同量按 id 稳定，防止刷新抖动
            int c = Long.compare(b.n, a.n);
            if (c != 0) return c;
            c = a.id.compareTo(b.id);
            if (c != 0) return c;
            c = Boolean.compare(a.tpl != null, b.tpl != null); // 同量同 id：普通在前
            if (c != 0) return c;
            String ca = a.tpl == null ? "" : String.valueOf(a.tpl.getComponentChanges()); // 精确同款全平：按组件串稳定
            String cb = b.tpl == null ? "" : String.valueOf(b.tpl.getComponentChanges());
            return ca.compareTo(cb);
        });
        filteredCount = filtered.size();
        int rows = (filteredCount + 8) / 9;
        int maxRow = Math.max(0, rows - 6);
        if (scrollRow > maxRow) scrollRow = maxRow;
        if (scrollRow < 0) scrollRow = 0;

        int i = 0;
        for (int idx = scrollRow * 9; idx < filtered.size() && i < PAGE; idx++, i++) {
            DispEnt d = filtered.get(idx);
            ItemStack st;
            if (d.tpl == null) {
                Item item = Registries.ITEM.get(Identifier.of(d.id));
                int max = new ItemStack(item).getMaxCount();
                st = new ItemStack(item, Math.max(1, (int) Math.min(d.n, (long) max)));
            } else {
                st = d.tpl.copyWithCount(Math.max(1, (int) Math.min(d.n, (long) d.tpl.getMaxCount())));
            }
            // m130：展示栈=真身+amt 数量标签——精确件保留自身 CUSTOM_DATA，仅并入 amt 键；
            // 取出方剥掉 amt 即还原真身（handler stripAmt）。自家 NBT 键无 "amt" 冲突（全键清单核对）。
            NbtCompound tag = st.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                    net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
            tag.putLong("amt", d.n);
            st.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                    net.minecraft.component.type.NbtComponent.of(tag));
            display.setStack(i, st);
        }
        for (; i < PAGE; i++) display.setStack(i, ItemStack.EMPTY);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        // m126a：合成网格持久化（稀疏槽位表，照 StorageCoreBlockEntity/TradeCenter 既有 NBT 写法）
        net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
        for (int i = 0; i < 9; i++) {
            ItemStack st = craftGrid.getStack(i);
            if (st.isEmpty()) continue;
            NbtCompound c = new NbtCompound();
            c.putInt("slot", i);
            c.put("item", st.encode(lookup));
            list.add(c);
        }
        nbt.put("craftGrid", list);
    }

    @Override
    protected void readNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        for (int i = 0; i < 9; i++) craftGrid.setStack(i, ItemStack.EMPTY);
        net.minecraft.nbt.NbtList list = nbt.getList("craftGrid", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            int s = c.getInt("slot");
            if (s >= 0 && s < 9)
                craftGrid.setStack(s, ItemStack.fromNbt(lookup, c.getCompound("item")).orElse(ItemStack.EMPTY));
        }
    }

    /** m126a：拆方块散落合成网格内容（AE2 addAdditionalDrops 同义，绝不吞）。 */
    public void dropCraftGrid(World world, BlockPos pos) {
        net.minecraft.util.ItemScatterer.spawn(world, pos, craftGrid);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.sdzjz.data_panel");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new DataPanelScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
        return this.pos;
    }
}
