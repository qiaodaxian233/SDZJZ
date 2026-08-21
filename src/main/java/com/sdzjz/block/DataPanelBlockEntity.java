package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.screen.DataPanelScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据面板：存储终端（不自带存储）。经网络访问相连的存储核心，聚合显示/存取。 */
public class DataPanelBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, com.sdzjz.machine.StorageAccess {

    public static final int PAGE = 54;
    // m292：searchFilter/scrollRow/matchedIds/filteredCount/lastViewTick/display 全部迁 handler（每玩家独立）
    /** m126a：合成网格常驻方块（学 AE2 CraftingTerminalPart，代码自写）——关界面模板不清空，
     *  重开即接着合；多人共开同一面板共用同一网格（AE2 同款语义）。随 NBT 持久化，拆方块散落。 */
    public final com.sdzjz.screen.CraftGridInventory craftGrid = new com.sdzjz.screen.CraftGridInventory(); // m201 换挂 RecipeInputInventory 的子类（持久化路径零变化）

    public DataPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_PANEL_BE, pos, state);
        craftGrid.addListener(inv -> this.setChanged()); // 网格改动落盘（markDirty 自带 world==null 守卫）
    }

    public static void tick(Level world, BlockPos pos, BlockState state, DataPanelBlockEntity be) {
        // m292：BE 级共享展示页退休（外部审计 P1：多人共用面板搜索/滚动互相覆盖）。
        // 视图状态与分页全部迁到各玩家自己的 DataPanelScreenHandler（sendContentUpdates 里
        // viewDirty 即时 + 10t 节拍兜机器侧变化），BE 只供 masterEntries() 快照。tick 无事可做。
    }

    // m107a：打开界面的玩家计数。handler 服务端构造 +1（并立即刷一次，打开不空白），onClosed -1。
    private int viewers = 0;
    public void addViewer() { viewers++; coresCacheTime = -1000; } // m108c 开界面强刷网络缓存；m292 首刷由 handler 构造时自 repage
    public void removeViewer() { if (viewers > 0) viewers--; if (viewers == 0) masterCache = null; } // m322 末观众离席释放快照内存

    // m108c：cores() 此前每次调用全新 BFS——机器供料/落库/熔炉扫描/经验/计数全走它，
    // 高产线（用户实测 104.8M/分）下一 tick 能打出几十趟 BFS。改 40t 缓存（与画布端点扫描同节奏）；
    // 缓存里出现已拆除核心立即重建；开界面时强制刷新（addViewer 置 -1000，m90 教训：哨兵不用 MIN_VALUE 防溢出）。
    private List<StorageCoreBlockEntity> coresCache;
    private long coresCacheTime = -1000;

    private List<StorageCoreBlockEntity> cores() {
        long now = (this.level != null) ? this.level.getTime() : 0;
        if (coresCache != null && now - coresCacheTime < 40) {
            boolean ok = true;
            for (StorageCoreBlockEntity c : coresCache) if (c.isRemoved()) { ok = false; break; }
            if (ok) return coresCache;
        }
        coresCache = StorageCoreBlockEntity.connectedCores(this.level, this.worldPosition);
        coresCacheTime = now;
        return coresCache;
    }

    /** m290 升 public：m289 库存摘要蹭这条缓存链路（cores() 40t 缓存），别再裸 BFS——
     *  m108c 治过的病不许复发。副作用=顺手刷新 typesUsed/typesCap/xp 三缓存为真值，多调无害。 */
    public LinkedHashMap<String, Long> aggregate() {
        List<StorageCoreBlockEntity> cs = cores();
        refreshMeta(cs); // m322 拆出（口径逐行同旧版；副作用语义不变，多调无害）
        LinkedHashMap<String, Long> agg = new LinkedHashMap<>();
        for (StorageCoreBlockEntity core : cs)
            for (Map.Entry<String, Long> e : core.storeView().entrySet())
                agg.merge(e.getKey(), e.getValue(), Long::sum);
        return agg;
    }

    /** m322 从 aggregate() 拆出：类型用量/上限/经验三缓存刷新（O(核心数) 纯读 getter）。
     *  单列的原因=主快照缓存命中时不再跑 aggregate()，但 xpBank 变动**不进账本修订号**——
     *  属性通道每 tick 读这三缓存，必须与快照缓存脱钩保活，否则命中期间经验读数冻住。 */
    private void refreshMeta(List<StorageCoreBlockEntity> cs) {
        int used = 0, coreCount = 0; long cap = 0; boolean unlimited = false; // m97/m98
        long xp = 0; // m107a：经验总量顺手统计（复用同一次 BFS）
        for (StorageCoreBlockEntity core : cs) {
            coreCount++;
            used += core.usedTypes(); // m130：精确条目同占类型额度
            xp += core.xpBank();
            int mt = core.maxTypes();
            if (mt == Integer.MAX_VALUE) unlimited = true; else cap += mt; // 防 MAX_VALUE 求和溢出
        }
        typesUsedCache = Math.min(used, 65534);
        typesCapCache = coreCount == 0 ? 0 : (unlimited ? 0xFFFF : (int) Math.min(cap, 65534L));
        xpCache = xp;
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
        if (this.level != null && this.level.isClientSide) return; // m112 保险丝：账本只在服务端
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
        long now = (this.level != null) ? this.level.getTime() : 0;
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
        if (this.level != null && this.level.isClientSide) return 0; // m112 保险丝：账本只在服务端
        int got = 0;
        for (StorageCoreBlockEntity core : cores()) {
            if (got >= amount) break;
            got += core.withdraw(id, amount - got);
        }
        return got;
    }

    /** m130：精确取出（按物品+组件跨核心累计），返回实际取出。 */
    public int withdrawExact(ItemStack template, int amount) {
        if (this.level != null && this.level.isClientSide) return 0; // m112 保险丝同款
        int got = 0;
        for (StorageCoreBlockEntity core : cores()) {
            if (got >= amount) break;
            got += core.withdrawExact(template, amount - got);
        }
        return got;
    }

    // m267 视图包 DoS 护栏常量（m292：钳制/节流逻辑随视图状态迁 handler，常量留此供两端对齐——
    //  m291 协议层 Bounded 上限也照它）。
    public static final int VIEW_SEARCH_MAX = 128;   // 搜索词最长
    public static final int VIEW_MATCHED_MAX = 256;  // 匹配 id 列表最长
    public static final int VIEW_ID_MAX = 128;       // 单个 id 最长

    /** m130：展示条目——tpl==null 为普通(id账本)，否则为精确件(模板账本，count=1)。 */
    public static final class DispEnt { // m292 升 public：各 handler 自行过滤/排序/分页
        public final String id; public final ItemStack tpl; public long n;
        public DispEnt(String id, ItemStack tpl, long n) { this.id = id; this.tpl = tpl; this.n = n; }
    }

    /** m292：全量条目快照（普通聚合 + 精确条目合并），**不含**过滤/排序/分页——那些是每玩家
     *  handler 自己的事（外部审计 P1：共享视图状态=多人搜索互相覆盖）。
     *  m112 保险丝原样保留：客户端 BE 账本恒空，禁跑聚合。 */
    // m322 主快照缓存：同一面板多观众各自 repage（≤10t/人）时，此前 masterEntries 每人各建
    // 一遍完整聚合+排序——同一网络 5 人同看 8192 类型=同样的快照建 5 次（评审第三优先点名）。
    // 指纹=普通修订号和+精确修订号和+核心数（m218 viewCache 同工艺：rev 单调只增，和相等⇔全没动；
    // 精确账本此前零修订号，正是本笔给 StorageCore 补 exactRev 的原因）。缓存值全 handler 共享，
    // **调用方只读**：不许改 DispEnt.n、不许增删元素、不持有跨 tick。
    private java.util.List<DispEnt> masterCache;
    private long masterNormalRev = -1, masterExactRev = -1;
    private int masterCoreN = -1;

    public java.util.List<DispEnt> masterEntries() {
        if (this.level == null || this.level.isClientSide) return java.util.List.of();
        List<StorageCoreBlockEntity> cs = cores();
        boolean useCache = com.sdzjz.config.SdzjzConfig.get().panelMasterSnapshotCache;
        long nr = 0, er = 0;
        if (useCache) {
            for (StorageCoreBlockEntity core : cs) { nr += core.storeRev(); er += core.exactRev(); }
            if (masterCache != null && nr == masterNormalRev && er == masterExactRev && cs.size() == masterCoreN) {
                refreshMeta(cs); // 命中也刷元数据：xpBank 变动不进修订号（见 refreshMeta 注释）
                return masterCache;
            }
        }
        java.util.List<DispEnt> all = new java.util.ArrayList<>();
        LinkedHashMap<String, Long> agg = aggregate();
        for (Map.Entry<String, Long> e : agg.entrySet()) all.add(new DispEnt(e.getKey(), null, e.getValue()));
        // m130：精确条目跨核心按「物品+组件」合并（m267 哈希键 O(n)；m404 换加载器中立的 StackKey）
        java.util.LinkedHashMap<com.sdzjz.storage.StackKey, DispEnt> exactMap = new java.util.LinkedHashMap<>();
        for (StorageCoreBlockEntity core : cs) {
            java.util.List<ItemStack> tpls = core.exactTemplates();
            for (int k = 0; k < tpls.size(); k++) {
                ItemStack t = tpls.get(k); long n = core.exactCount(k);
                var key = com.sdzjz.storage.StackKey.of(t);
                DispEnt d = exactMap.get(key);
                if (d != null) d.n += n;
                else exactMap.put(key, new DispEnt(BuiltInRegistries.ITEM.getKey(t.getItem()).toString(), t.copyWithCount(1), n));
            }
        }
        all.addAll(exactMap.values());
        // m322 排序上移 BE：一次快照一次排序，handler 只过滤/分页。与旧"先筛后排"逐元素同序——
        // List.sort 稳定（TimSort）+ 过滤是子序列筛选，序关系保持；useCache=false 也走此处（口径唯一）。
        all.sort(MASTER_ORDER);
        if (useCache) { masterCache = all; masterNormalRev = nr; masterExactRev = er; masterCoreN = cs.size(); }
        return all;
    }

    /** m322：m83 存量降序比较器（原 DataPanelScreenHandler.repage 内联体逐行搬来，口径零改）。 */
    public static final java.util.Comparator<DispEnt> MASTER_ORDER = (x, y) -> {
        int c = Long.compare(y.n, x.n); // m83：ME 式排序，存量多的排前面；同量按 id 稳定，防止刷新抖动
        if (c != 0) return c;
        c = x.id.compareTo(y.id);
        if (c != 0) return c;
        c = Boolean.compare(x.tpl != null, y.tpl != null); // 同量同 id：普通在前
        if (c != 0) return c;
        String ca = x.tpl == null ? "" : String.valueOf(x.tpl.getComponentsPatch()); // 精确同款全平：按组件串稳定
        String cb = y.tpl == null ? "" : String.valueOf(y.tpl.getComponentsPatch());
        return ca.compareTo(cb);
    };

    @Override
    protected void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        // m126a：合成网格持久化（稀疏槽位表，照 StorageCoreBlockEntity/TradeCenter 既有 NBT 写法）
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 9; i++) {
            ItemStack st = craftGrid.getItem(i);
            if (st.isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("slot", i);
            c.put("item", st.save(lookup));
            list.add(c);
        }
        nbt.put("craftGrid", list);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        for (int i = 0; i < 9; i++) craftGrid.setItem(i, ItemStack.EMPTY);
        net.minecraft.nbt.ListTag list = nbt.getList("craftGrid", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int s = c.getInt("slot");
            if (s >= 0 && s < 9)
                craftGrid.setItem(s, ItemStack.parse(lookup, c.getCompound("item")).orElse(ItemStack.EMPTY));
        }
    }

    /** m126a：拆方块散落合成网格内容（AE2 addAdditionalDrops 同义，绝不吞）。 */
    public void dropCraftGrid(Level world, BlockPos pos) {
        net.minecraft.world.Containers.dropContents(world, pos, craftGrid);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sdzjz.data_panel");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DataPanelScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
        return this.worldPosition;
    }
}
