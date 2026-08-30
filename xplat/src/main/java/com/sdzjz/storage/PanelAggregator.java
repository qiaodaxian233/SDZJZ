package com.sdzjz.storage;

import com.sdzjz.machine.StorageLedgerProbe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * m500（真移植 B3）：**数据面板聚合两代共用一份**——把「一组存储核心 → 展示条目」这条链
 * 从主线整段搬过来：普通账本跨核心聚合 + 精确条目按「物品+附加数据」跨核心合并 + m83 存量降序排序
 * （{@link #entriesOf}，原文取自 {@code DataPanelBlockEntity.masterEntries} 主体），
 * 以及过滤/钳滚动/开窗（{@link #page}，原文取自 {@code DataPanelScreenHandler.repage} 前半）。
 *
 * <p><b>为什么这两段能合一</b>：逐句对完两代（主线 320 行 BE + handler 分页段 vs 本世代
 * {@code DataPanel120.snapshot} 60 行）——本世代那份是把主线的「BE 出全量快照、handler 出分页」
 * 两步压扁成一个方法的**仿写件**，循环体、排序意图、钳位口径本就同构，真差异只有三处，
 * 且三处都是**本世代少了主线的加固**（见下），不是世代取舍。合一后三处加固自动推广（m477 红利同款）。
 *
 * <p><b>核心列表吃 {@link StorageLedgerProbe}</b>：m480 建它时是为跨代行为契约，这里发现
 * 两代存储核心都已实现（{@code storeView/exactTemplates/exactCount/deposit/withdraw/withdrawExact/count}
 * 现成），于是共用件不必再开新接口、两代不必再加一行 implements——**为 A 目的建的抽象在 B 处第二次白捡**
 * （第一次是 m498 的拉料 bank）。
 *
 * <p><b>不在本件里的</b>：展示栈的**物化**（普通条目 id→ItemStack、amt 数量标签）留在各世代自己那侧——
 * 主线走 {@code ResourceLocation.parse}（1.21 专属）+ 真槽位，本世代走 {@code tryParse} + 虚拟列表包，
 * 那是协议形状差不是业务差（m485「留世代壳各写」同律）。缓存层（cores 40t / viewCache / masterCache
 * 三套指纹）同样留主线 BE——本世代眼下没有观众态也没有多观众场景，**不为接口好看把只有一边用的东西搬上来**（m480 同律）。
 */
public final class PanelAggregator {

    private PanelAggregator() {}

    /** m130：展示条目——tpl==null 为普通(id账本)，否则为精确件(模板账本，count=1)。 */
    public static final class DispEnt { // m292 升 public：各 handler 自行过滤/排序/分页
        public final String id; public final ItemStack tpl; public long n;
        public DispEnt(String id, ItemStack tpl, long n) { this.id = id; this.tpl = tpl; this.n = n; }
    }

    /** m322：m83 存量降序比较器（原 DataPanelScreenHandler.repage 内联体逐行搬来，口径零改）。 */
    public static final java.util.Comparator<DispEnt> MASTER_ORDER = (x, y) -> {
        int c = Long.compare(y.n, x.n); // m83：ME 式排序，存量多的排前面；同量按 id 稳定，防止刷新抖动
        if (c != 0) return c;
        c = x.id.compareTo(y.id);
        if (c != 0) return c;
        c = Boolean.compare(x.tpl != null, y.tpl != null); // 同量同 id：普通在前
        if (c != 0) return c;
        String ca = x.tpl == null ? "" : StackKey.dataOrder(x.tpl); // 精确同款全平：按附加数据串稳定（m500 走世代口，原文是 1.21 专属的 getComponentsPatch）
        String cb = y.tpl == null ? "" : StackKey.dataOrder(y.tpl);
        return ca.compareTo(cb);
    };

    /** 普通账本跨核心聚合（id→数量）。原文取自主线 {@code DataPanelBlockEntity.aggregate} 的合并循环。 */
    public static LinkedHashMap<String, Long> merged(List<? extends StorageLedgerProbe> cores) {
        LinkedHashMap<String, Long> agg = new LinkedHashMap<>();
        for (StorageLedgerProbe core : cores)
            for (Map.Entry<String, Long> e : core.storeView().entrySet())
                agg.merge(e.getKey(), e.getValue(), StorageLedger::satAdd); // m273 饱和加法（m500 统一：主线原为 Long::sum，取本世代的加固口径）
        return agg;
    }

    /**
     * 全量条目快照（普通聚合 + 精确条目合并），**不含**过滤/分页——那些是每玩家 handler 自己的事
     * （外部审计 P1：共享视图状态=多人搜索互相覆盖）。
     * 排序在此一次做完：{@link #MASTER_ORDER}，调用方只过滤/分页（m322：一次快照一次排序）。
     */
    public static List<DispEnt> entriesOf(List<? extends StorageLedgerProbe> cores) {
        List<DispEnt> all = new ArrayList<>();
        LinkedHashMap<String, Long> agg = merged(cores);
        for (Map.Entry<String, Long> e : agg.entrySet()) all.add(new DispEnt(e.getKey(), null, e.getValue()));
        // m130：精确条目跨核心按「物品+组件」合并（m267 哈希键 O(n)；m404 换加载器中立的 StackKey）
        LinkedHashMap<StackKey, DispEnt> exactMap = new LinkedHashMap<>();
        for (StorageLedgerProbe core : cores) {
            List<ItemStack> tpls = core.exactTemplates();
            for (int k = 0; k < tpls.size(); k++) {
                ItemStack t = tpls.get(k); long n = core.exactCount(k);
                var key = StackKey.of(t);
                DispEnt d = exactMap.get(key);
                if (d != null) d.n = StorageLedger.satAdd(d.n, n);
                else exactMap.put(key, new DispEnt(BuiltInRegistries.ITEM.getKey(t.getItem()).toString(), t.copyWithCount(1), n));
            }
        }
        all.addAll(exactMap.values());
        // m322 排序上移：一次快照一次排序，handler 只过滤/分页。与旧"先筛后排"逐元素同序——
        // List.sort 稳定（TimSort）+ 过滤是子序列筛选，序关系保持。
        all.sort(MASTER_ORDER);
        return all;
    }

    /** 一页：匹配条目总数 / 匹配总行数 / **服务端钳位后**的滚动行 / 该窗条目。 */
    public record Page(int filteredCount, int totalRows, int scrollRow, List<DispEnt> rows) { }

    /**
     * 过滤 → 钳滚动 → 开窗。原文取自主线 {@code DataPanelScreenHandler.repage} 前半。
     *
     * @param query      搜索词（null 视作空；大小写不敏）
     * @param matchedIds 客户端本地化名索引命中的 id 集（m107 搜中文名；本世代眼下无此通道，传空集即退化为纯 id 子串匹配）
     * @param scrollRow  客户端申报的滚动行——**恒不可信，此处钳位**
     * @param window     一屏条目数（两代同为 54=9 列×6 行）
     */
    public static Page page(List<DispEnt> all, String query, Set<String> matchedIds, int scrollRow, int window) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT); // m500 统一：主线原为默认 locale，取本世代的 ROOT（土耳其 locale 下 "I" 会被小写成 "ı" 打断 id 匹配）
        Set<String> matched = matchedIds == null ? Set.of() : matchedIds;
        List<DispEnt> filtered = new ArrayList<>();
        for (DispEnt d : all)
            if (q.isEmpty() || d.id.toLowerCase(Locale.ROOT).contains(q) || matched.contains(d.id)) filtered.add(d);
        int rows = (filtered.size() + 8) / 9;
        int maxRow = Math.max(0, rows - window / 9);
        int row = Math.max(0, Math.min(scrollRow, maxRow)); // 服务端钳位（客户端数字不可信）
        int from = Math.min(row * 9, filtered.size());
        int to = Math.min(filtered.size(), from + window);
        return new Page(filtered.size(), rows, row, new ArrayList<>(filtered.subList(from, to)));
    }

    // ===== 聚合读写门面（跨核心，原文取自主线 DataPanelBlockEntity；客户端保险丝留各世代 BE 侧，它才有 level）=====

    /** 存入：塞进第一个收得下的存储核心。 */
    public static void deposit(List<? extends StorageLedgerProbe> cores, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (StorageLedgerProbe core : cores) {
            core.deposit(stack);
            if (stack.isEmpty()) return;
        }
    }

    /** 取出：跨核心累计取，返回实际取出数量。 */
    public static int withdraw(List<? extends StorageLedgerProbe> cores, String id, int amount) {
        int got = 0;
        for (StorageLedgerProbe core : cores) {
            if (got >= amount) break;
            got += core.withdraw(id, amount - got);
        }
        return got;
    }

    /** m130：精确取出（按物品+组件跨核心累计），返回实际取出。 */
    public static int withdrawExact(List<? extends StorageLedgerProbe> cores, ItemStack template, int amount) {
        int got = 0;
        for (StorageLedgerProbe core : cores) {
            if (got >= amount) break;
            got += core.withdrawExact(template, amount - got);
        }
        return got;
    }

    /** 库存量（跨核心求和）。 */
    public static long count(List<? extends StorageLedgerProbe> cores, String id) {
        long n = 0;
        for (StorageLedgerProbe core : cores) n = StorageLedger.satAdd(n, core.count(id));
        return n;
    }
}
