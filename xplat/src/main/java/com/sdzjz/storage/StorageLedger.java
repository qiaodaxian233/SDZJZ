package com.sdzjz.storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * m485（真移植·作者点名「是移植不是重做，把以前写的 1.20.1 直接移除」）：**存储账本核心两代共用一份**。
 *
 * <p>本文件的全部方法体，逐句取自主线 {@code StorageCoreBlockEntity} 的 139~275 行段落
 * （m130 精确账本 / m293 类型硬顶 / m295 哈希索引 / m273 饱和加法 / m218·m322 修订号 / m80c 经验库），
 * **只做了一处机械替换**：{@code setChanged()} → {@code onChange.run()}——账本本身不该知道
 * 自己长在哪个方块实体上。除此之外一个字未改，包括注释里的刀号与论证。
 *
 * <p>两代 BE 各持一个实例并转发；1.20.1 的仿写件 {@code StorageCore120} 里那份**同功能的重写代码
 * 就此删除**。行为由 m480 立下的十类跨代契约（{@code StorageDomainAssertions}）压着——
 * 契约先立、手术后做，正是为这一刀准备的。
 */
public final class StorageLedger {

    private final Runnable onChange;

    /** @param onChange 账本变更时回调（两代传各自的 {@code setChanged}）。 */
    public StorageLedger(Runnable onChange) {
        this.onChange = onChange;
    }

    private final LinkedHashMap<String, Long> store = new LinkedHashMap<>();
    private final List<ItemStack> exactTpl = new ArrayList<>();
    private final List<Long> exactN = new ArrayList<>();
    private int tier = 1;

    /** 类型上限走配置（m98）：0=无限（默认），>0=每级该数。 */
    private static int typesPerTier() {
        return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier;
    }

    public void setTier(int t) { tier = Math.max(1, t); }

    public int tier() { return tier; }
    public int maxTypes() { int p = typesPerTier(); return p <= 0 ? Integer.MAX_VALUE : p * tier; }
    // ===== m295 精确账本内存索引（外部审计 P2：账本自身仍是 List 线性找）=====
    // 列表仍是唯一权威与落盘格式（undo 语义/存档零改动），旁挂 transient 键→下标索引：
    // 查找 逐条组件深比 O(n) → 哈希 O(1)（m404 起键=加载器中立的 StackKey，equals 直调
    // areItemsAndComponentsEqual 与旧 ItemVariant 键同口径）；追加 O(1)；删除 O(n) 但只平移整数下标（无组件比较，常数便宜
    // 一两个量级）；**事务回滚重放/NBT 读回直接置脏懒重建**（abort/读档罕见，正确性优先）。
    private transient java.util.HashMap<com.sdzjz.storage.StackKey, Integer> exactIdx; // null=脏

    public int exactIndexOf(ItemStack probe) {
        var m = exactIdx;
        if (m == null) {
            m = new java.util.HashMap<>();
            for (int i = 0; i < exactTpl.size(); i++) m.put(com.sdzjz.storage.StackKey.of(exactTpl.get(i)), i);
            exactIdx = m;
        }
        Integer i = m.get(com.sdzjz.storage.StackKey.of(probe));
        return i == null ? -1 : i;
    }

    private void exactIdxAppended() { // append 之后调（新条目下标=size-1）
        if (exactIdx != null) exactIdx.put(com.sdzjz.storage.StackKey.of(exactTpl.get(exactTpl.size() - 1)), exactTpl.size() - 1);
    }

    private void exactIdxRemoved(int i, ItemStack tpl) { // remove(i) 之后调：删键+平移
        var m = exactIdx;
        if (m == null) return;
        m.remove(com.sdzjz.storage.StackKey.of(tpl));
        for (var e : m.entrySet()) if (e.getValue() > i) e.setValue(e.getValue() - 1);
    }

    public int usedTypes() { return store.size() + exactTpl.size(); } // m130：精确条目同占类型额度

    /** m293 插入闸口径（外部审计 P2：默认无限类型=最大的存档/NBT/GUI 排序压力源没有技术保险）：
     *  玩法额度与绝对安全上限取小。安全上限独立于玩法——typesPerTier=0 的"无限仓库"照常显示无限、
     *  照常用，只是单核心新类型到 8192 种后拒收（已有超限存档不裁账，只是加不了新类型）。≤0=关闸。 */
    public int typeGate() {
        int hard = com.sdzjz.config.SdzjzConfig.get().absoluteStorageTypeSafetyLimit;
        int play = maxTypes();
        return hard > 0 ? (int) Math.min((long) play, (long) hard) : play;
    }
    public void upgrade() { tier++; onChange.run(); }

    /** m273：非负计数饱和加法——溢出封顶 Long.MAX_VALUE（把 FTA insert 路径既有的
     *  Long.MAX_VALUE-cur 口径收成公共辅助；账本/缓存/经验库全部裸加法统一走这里）。 */
    public static long satAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r; // 符号溢出检测：两非负操作数溢出必为负
    }

    // ===== m80c 经验库：网络级经验银行（数据面板界面存/取）=====
    private long xpBank = 0;
    public long xpBank() { return xpBank; }
    public void xpAdd(long points) { if (points > 0) { xpBank = satAdd(xpBank, points); onChange.run(); } } // m273 饱和加法
    /** 取出至多 max 点，返回实际取出。 */
    public long xpTake(long max) {
        long t = Math.min(xpBank, Math.max(0, max));
        xpBank -= t;
        if (t > 0) onChange.run();
        return t;
    }

    public long count(String id) {
        Long v = store.get(id);
        return v == null ? 0L : v;
    }

    /** 存入。默认无限类型（m98）；config 启用上限时，类型未满或已有该类型才收（拒收时栈原样保留）。
     *  m130：带组件的物品自动分流进精确账本，组件原样保存——附魔书/药水/损耗工具/带阶位机器全部可入仓。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        // m487 世代口：原文是 1.21 的 stack.getComponentsPatch().isEmpty()（组件世代专属）。
        // 「有没有附加数据」两代口径不同（1.21=组件补丁非空，1.20.1=有 tag），走 ItemData 五口的 has()
        // ——它本来就是为这件事建的门面（m437/m451），两代实现各自判各自的。
        if (com.sdzjz.item.ItemData.has(stack)) { depositExact(stack); return; }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!store.containsKey(id) && usedTypes() >= typeGate()) return; // m293 安全硬顶同闸
        store.merge(id, (long) stack.getCount(), StorageLedger::satAdd); // m273 饱和加法
        storeRev++; // m218
        stack.setCount(0);
        onChange.run();
    }

    /** m130：精确存入——按「物品+组件」找同款条目并账；新类型受同一类型上限（拒收时栈原样保留）。 */
    public void depositExact(ItemStack stack) {
        if (stack.isEmpty()) return;
        int hit = exactIndexOf(stack); // m295 索引直查（等价旧 areItemsAndComponentsEqual 扫描）
        if (hit >= 0) {
            exactN.set(hit, satAdd(exactN.get(hit), stack.getCount())); // m273 饱和加法
            exactRev++; // m322
            stack.setCount(0);
            onChange.run();
            return;
        }
        if (usedTypes() >= typeGate()) return; // m293
        exactTpl.add(stack.copyWithCount(1));
        exactN.add((long) stack.getCount());
        exactIdxAppended(); // m295
        exactRev++; // m322
        stack.setCount(0);
        onChange.run();
    }

    /** m130：精确取出——按「物品+组件」匹配模板，返回实际取出数量。 */
    public int withdrawExact(ItemStack template, int amount) {
        if (template == null || template.isEmpty() || amount <= 0) return 0;
        int i = exactIndexOf(template); // m295 索引直查
        if (i >= 0) {
            long have = exactN.get(i);
            int take = (int) Math.min((long) amount, have);
            long left = have - take;
            if (left <= 0) { ItemStack t = exactTpl.get(i); exactTpl.remove(i); exactN.remove(i); exactIdxRemoved(i, t); }
            else exactN.set(i, left);
            if (take > 0) { exactRev++; onChange.run(); } // m322
            return take;
        }
        return 0;
    }

    /** m130：精确账本视图（面板聚合用；模板 count 恒为 1，计数走 exactCount）。 */
    public List<ItemStack> exactTemplates() { return exactTpl; }
    public long exactCount(int i) { return (i >= 0 && i < exactN.size()) ? exactN.get(i) : 0L; }

    public int withdraw(String id, int amount) {
        Long have = store.get(id);
        if (have == null || amount <= 0) return 0;
        int take = (int) Math.min((long) amount, have);
        long left = have - take;
        if (left <= 0) store.remove(id); else store.put(id, left);
        storeRev++; // m218
        onChange.run();
        return take;
    }

    public Map<String, Long> storeView() { return store; }

    // m218 账本修订号：store 每次变更 +1（六处变更点逐一挂钩+NBT读回一处）。数据面板聚合视图靠它做
    // "无变更不重建"缓存——只增不减，跨核心求和作指纹（和相等⇔各核心都没动过，因为单调）。
    private long storeRev;
    public long storeRev() { return storeRev; }
    /** 存档/事务路径要用的内部直取（两代 BE 的 save/load 与 FTA 前像回滚共用）。 */
    public int tierRaw() { return tier; }

    /** m322 精确账修订号（与 storeRev 同用途，面板聚合视图缓存指纹）。 */
    private long exactRev;
    public long exactRev() { return exactRev; }

    /** 存档读回/事务回滚后置脏：索引懒重建（正确性优先，罕见路径）。 */
    public void markIndexDirty() { exactIdx = null; }

    /** 存档读写用的直通视图（两代 BE 各自按自己的 NBT 签名落盘）。 */
    public List<Long> exactCounts() { return exactN; }
    public void setXpBank(long v) { xpBank = Math.max(0, v); }
    public void bumpStoreRev() { storeRev++; }
    public void bumpExactRev() { exactRev++; }

    // ===== m503（真移植 B5）：FTA 事务包装的**业务核对面**两代共用一份 =====
    // 本节业务判定整段取自主线 StorageCoreBlockEntity 的 FabricLedger.insert/extract（1.20.1 的
    // FabricLedger120 同功能重写同刀删除）。**这三个方法刻意不出现任何 Fabric Transfer API 类型**
    // （Storage/ItemVariant/TransactionContext/SnapshotParticipant 等）——m406 分层硬闸（layer_gate，
    // 第 13 道）钉死"xplat 见 MC、不见加载器"，FTA 接口本身就是加载器类型，实现它这层"薄皮"
    // （extends SnapshotParticipant/implements Storage/快照回调时机 updateSnapshots）没法下沉，
    // 必须留在两代各自源集里的 FabricLedger 壳（本文件旁的 block/StorageCoreBlockEntity.java 与
    // retro/StorageCore120.java）。壳只剩：FTA 生命周期钩子、ItemVariant↔ItemStack 类型转换、
    // 一行转发调用本节方法——业务判断（分流判据/类型闸/undo 前像/索引维护）全在这一份代码里。
    //
    // undo 日志与快照时机作为参数跨边界传入：{@code undo} 是调用方（各世代壳）持有的
    // {@code ArrayList<Runnable>}（java.util 标准类型，非加载器类型，可以安全跨这条边界）；
    // {@code beforeMutate} 对应原 {@code updateSnapshots(tx)}——必须在"确定真的要修改状态"那一刻
    // 精确调用一次（早了会创建不必要的快照，晚了 undo 记录会失真），这个决策点天然长在业务判断
    // 内部，所以设计成回调而不是让调用方自己猜时机。
    //
    // 【分流判据世代口】：与 deposit() 同款替换，两代原文（组件补丁非空 / hasTag）统一走
    // com.sdzjz.item.ItemData.has()（m437/m451 两代早已装好实现）。
    //
    // 【顺手修的真编译错】：主线原 FabricLedger 里 exactIndexOf/exactIdxAppended/exactIdxRemoved
    // 六处调用点全部漏了 ledger. 前缀——这几个方法从 m485 账本下沉起就整段搬来了本类，但调用点
    // 忘了跟着改（当年只顾着改 store/exactTemplates()/exactCounts() 这类字段访问，唯独漏了这三个
    // "索引维护"方法）。javac 缺 MC jar 冒烟时这类"cannot find symbol"被筛选器（m491）故意当噪音
    // 滤掉——它把这类"自家符号找不到"整体让位给第 19 闸盯，但当年第 19 闸的登记表里没有这个文件/
    // 这几个符号，两层网都没接住，一路潜伏到现在（基线核对：git log 显示这个文件从很早的提交起
    // 就没人再碰过，非本刀引入）。现在这三个方法体就在本类里，调用天然合法，本刀顺手修正。
    // 本世代此前用 markIndexDirty() 全量置脏重建侥幸绕开了这个坑（逻辑结果等价，性能是退化）。

    /** m503：FTA insert 业务核对面（不含 FTA 类型）。返回实际插入量；
     *  {@code beforeMutate} 恰在"确定要修改"的那一刻调用一次（对应各世代壳的 updateSnapshots）。 */
    public long ftaInsert(ItemStack one, long maxAmount, List<Runnable> undo, Runnable beforeMutate) {
        if (!com.sdzjz.item.ItemData.has(one)) { // 与 deposit 同一分流：无附加数据走普通账本（世代口）
            String id = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
            if (!store.containsKey(id) && usedTypes() >= typeGate()) return 0; // m293
            long cur = store.getOrDefault(id, 0L);
            long accept = Math.min(maxAmount, Long.MAX_VALUE - cur);
            if (accept <= 0) return 0;
            beforeMutate.run();
            final boolean had = store.containsKey(id); // m278 前像：键此前是否存在
            undo.add(() -> { if (had) store.put(id, cur); else store.remove(id); });
            store.put(id, cur + accept);
            bumpStoreRev(); // m218
            return accept;
        }
        int hit = exactIndexOf(one); // m295 索引直查（带附加数据走精确账本，m130 同款口径）
        if (hit >= 0) {
            long cur = exactN.get(hit);
            long accept = Math.min(maxAmount, Long.MAX_VALUE - cur);
            if (accept <= 0) return 0;
            beforeMutate.run();
            final int idx = hit; // m278 前像
            undo.add(() -> exactN.set(idx, cur));
            exactN.set(hit, cur + accept);
            bumpExactRev(); // m322
            return accept;
        }
        if (usedTypes() >= typeGate()) return 0; // m293
        beforeMutate.run();
        undo.add(() -> { // m278 undo=撤尾（逆序重放保证撤到的必是本条 add）；m295 动列表即置脏索引
            int last = exactTpl.size() - 1; exactTpl.remove(last); exactN.remove(last); markIndexDirty(); });
        exactTpl.add(one); // toStack(1) 即模板规格（count=1，附加数据原样，转换在壳层做）
        exactN.add(maxAmount);
        exactIdxAppended(); // m295
        bumpExactRev(); // m322
        return maxAmount;
    }

    /** m503：FTA extract 业务核对面（不含 FTA 类型）。返回实际取出量。 */
    public long ftaExtract(ItemStack one, long maxAmount, List<Runnable> undo, Runnable beforeMutate) {
        if (!com.sdzjz.item.ItemData.has(one)) {
            String id = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
            long have = store.getOrDefault(id, 0L);
            long take = Math.min(have, maxAmount);
            if (take <= 0) return 0;
            beforeMutate.run();
            undo.add(() -> store.put(id, have)); // m278 前像（take>0 ⇒ 键此前必存在）
            if (have - take <= 0) store.remove(id); else store.put(id, have - take);
            bumpStoreRev(); // m218
            return take;
        }
        int i = exactIndexOf(one); // m295 索引直查
        if (i >= 0) {
            long have = exactN.get(i);
            long take = Math.min(have, maxAmount);
            if (take <= 0) return 0;
            beforeMutate.run();
            final int idx = i;
            if (have - take <= 0) {
                final ItemStack ptpl = exactTpl.get(i); // m278 结构前像：原下标插回（模板从不被原地改，存引用即安全）
                undo.add(() -> { exactTpl.add(idx, ptpl); exactN.add(idx, have); markIndexDirty(); }); // m295 动列表即置脏
                exactTpl.remove(i); exactN.remove(i);
                exactIdxRemoved(i, ptpl); // m295
            } else {
                undo.add(() -> exactN.set(idx, have));
                exactN.set(i, have - take);
            }
            bumpExactRev(); // m322
            return take;
        }
        return 0;
    }

    /** m503：FTA 游标视图的金额直查（不含 FTA 类型）——{@code plainId} 非空=普通账本键，
     *  为空=精确账本按 {@code exactProbe} 模板匹配（壳层 View.getAmount 转发到此）。 */
    public long ftaAmount(String plainId, ItemStack exactProbe) {
        if (plainId != null) return store.getOrDefault(plainId, 0L);
        int i = exactIndexOf(exactProbe); // m295 索引直查（管道每 tick 模拟就打它，收益最大的一处）
        return i >= 0 ? exactN.get(i) : 0;
    }
}
