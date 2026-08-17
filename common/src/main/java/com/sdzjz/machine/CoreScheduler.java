package com.sdzjz.machine;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * m302 全服生产预算 + 饥饿名单公平层（外部评审文档③ 唯一方向性建议，方案①）。
 *
 * 背景：各核心随区块 BE tick 序消费预算，序稳定=靠前核心恒占便宜；若只做全服硬顶，
 * 预算耗尽时靠后核心会**恒饿**。本类把 maxRecipesPerNetworkTick 真接线成全服共享预算，
 * 并叠饥饿名单：本 tick 一个周期都没吃到的核心记名，下一 tick 预算先切出保留额
 * （每饿核保底 1 周期）只许名单内核心消费；核心到场即出名单（吃到与否都出——
 * 幽灵核心/已卸载核心至多占一拍保留额，下拍名单重建自然过期，绝不永久堵门）。
 * 无中央调度循环，仅两处挂钩：扣预算处（cyclesThisTick）+ 拍轮换处（rollTick，
 * 由首个请求按 server.getTicks() 触发）。
 *
 * 语义要点：
 * - 预算单位=逻辑配方周期数，与 m270 单核心预算（recipesThisTick）双层并存，先核内后全服。
 * - 耗尽只欠不丢：调用方（cyclesThisTick）的工作量累积照旧，下 tick 续（m99/m270 口径）。
 * - globalCap<=0 = 闸关无限：直接放行，名单机制整体旁路（默认 1_048_576 极高，
 *   默认不束缚纯作管理员旋钮，与 maxRecipesPerCoreTick 同哲学）。
 * - 仅服务端主线程调用（BE tick），零同步；SERVER_STOPPED 走 clearAll() 清态。
 * - 保底的物理边界：饿核数 > 预算时保底也发不齐，没轮到的留名单下拍继续持先食权。
 */
public final class CoreScheduler {
    private CoreScheduler() {}

    /** 当前记账拍（MinecraftServer.getTicks()，全维度统一时钟——world.getTime() 会被 /time set 拨动，不用）。 */
    private static long tickStamp = Long.MIN_VALUE;
    /** 本拍全服已批准周期数。 */
    private static long spent = 0;
    /** 本拍剩余保留额 = 名单内尚未到场的饿核数 ×1（到场即释放，自用或并入公池）。 */
    private static int reserveLeft = 0;
    /** 本拍持保留额名单：核心→**拍龄**（连续被完全拒绝的拍数）。m309：k>cap 时按拍龄资历轮转保底——
     *  预算须先够所有比我更饿的核心各吃 1，否则本拍让贤、拍龄+1 继续攒资历，最饿者必先食，
     *  任何核心的连续挨饿拍数有界 ≤O(k/cap)。键式照 StorageCoreBlockEntity.CORES 在树先例。 */
    private static final Map<String, Map<Long, Integer>> STARVED = new HashMap<>();
    /** 本拍新增/续期饿核（下拍生效；值=拍龄，merge 取大保资历不回退）。 */
    private static final Map<String, Map<Long, Integer>> STARVED_NEXT = new HashMap<>();
    /** m309 本拍已进食核心（吃到≥1 周期）——其余节点再吃零**不记名**："吃到部分=有进展不记"的
     *  真实现。此前逐请求记名把全员每拍打回名单，"先食权"人人持有等于没有，k>cap 时 tick 序
     *  末位核心恒饿（作者 100×64+1 产线实测：第 101 核 2400 拍颗粒无收）。 */
    private static final Map<String, Set<Long>> FED = new HashMap<>();
    /** 本拍各拍龄尚未服务的饿核数（rollTick 重建，olderUnserved 资历闸查询用）。 */
    private static final java.util.TreeMap<Integer, Integer> UNSERVED_BY_AGE = new java.util.TreeMap<>();

    // ===== m304 压测观测（评审③复评"下一步是测不是重构"：granted 分布/饿核数得有处读）=====
    /** 每核累计账 long[0]=累计批准周期 long[1]=零批准记名次数。仅 cap>0 路径更新；
     *  reset 只清计数**绝不动名单**（名单是行为态，动了会扰动调度本身）。 */
    private static final Map<String, Map<Long, long[]>> STATS = new HashMap<>();
    /** 上一完整拍的全服消费快照（rollTick 时定格，供 /sdzjz profile sched 读"上拍消费"）。 */
    private static long prevTickSpent = 0;

    /** 观测行（命令层展示用，dim 取 Identifier 串）。 */
    public static final class Row {
        public final String dim; public final long pos; public final long granted; public final long zeroEvents; // m366 pos=打包长整型
        Row(String d, long p, long g, long z) { dim = d; pos = p; granted = g; zeroEvents = z; }
    }

    /** 自上次清零以来所有申请过预算的核心账目快照。 */
    public static java.util.List<Row> statRows() {
        java.util.List<Row> out = new java.util.ArrayList<>();
        for (Map.Entry<String, Map<Long, long[]>> e : STATS.entrySet())
            for (Map.Entry<Long, long[]> r : e.getValue().entrySet())
                out.add(new Row(e.getKey(), r.getKey(), r.getValue()[0], r.getValue()[1]));
        return out;
    }

    public static long prevTickSpent() { return prevTickSpent; }

    /** 当前持保底名单核数（本拍待优先喂）+ 本拍新记名核数。 */
    public static int starvedPending() { int n = 0; for (Map<Long, Integer> s : STARVED.values()) n += s.size(); return n; }
    public static int starvedNew()     { int n = 0; for (Map<Long, Integer> s : STARVED_NEXT.values()) n += s.size(); return n; }

    /** 只清观测计数（/sdzjz profile reset 挂钩），名单与预算态原样。 */
    public static void resetStats() { STATS.clear(); prevTickSpent = 0; }

    /**
     * 核心申请 want 个生产周期，返回实际批准数（0=本拍别结算，工作量自行留存）。
     * 同一核心多节点会多次进来：首次到场消耗其名单身份，后续按普通请求走公池。
     */
    public static int request(String dimK, long posL, int want, long globalCap, long now) { // m366 升 Common：键=维度串+posLong，时钟入参化（版本侧折算）
        if (want <= 0) return 0;
        if (globalCap <= 0) return want; // 闸关=无限，公平层无意义整体旁路
        if (now != tickStamp) rollTick(now);

        long remain = globalCap - spent;
        Long pos = posL;
        long[] st = STATS.computeIfAbsent(dimK, k -> new HashMap<>())
                         .computeIfAbsent(pos, k -> new long[2]); // m304 观测账（每节点结算才进来,频率≈节点数/周期,开销可忽略）
        Map<Long, Integer> dim = STARVED.get(dimK);
        Integer age = dim != null ? dim.get(pos) : null;
        int granted;
        if (age != null) { // 饿核到场即出名单（防幽灵堵门）；拍龄经 NEXT 延续不丢资历
            dim.remove(pos);
            bucketDec(age);
            reserveLeft--;
            if (remain - olderUnserved(age) < 1) { // m309 资历闸：先保比我更饿的，本拍让贤、拍龄+1
                STARVED_NEXT.computeIfAbsent(dimK, k -> new HashMap<>()).merge(pos, age + 1, Math::max);
                st[1]++;
                return 0;
            }
            long open = remain - Math.max(0, reserveLeft); // 公池=余额扣掉他核保留额
            long allow = Math.max(open, Math.min(1L, remain)); // 保底1（预算真见零除外）
            granted = (int) Math.min(want, Math.min(remain, Math.max(0L, allow)));
        } else {
            long open = remain - Math.max(0, reserveLeft); // 普通核心只许动公池
            granted = (int) Math.min(want, Math.max(0L, open));
        }
        if (granted <= 0) {
            st[1]++;
            if (!FED.computeIfAbsent(dimK, k -> new HashSet<>()).contains(pos)) // m309 有进展不记名（真实现——
                STARVED_NEXT.computeIfAbsent(dimK, k -> new HashMap<>()).merge(pos, 1, Math::max);
                // 此前逐请求记名把已进食核心的其余节点也打回名单,全员每拍重列,资历永远清零
            return 0;
        }
        FED.computeIfAbsent(dimK, k -> new HashSet<>()).add(pos);
        st[0] += granted;
        spent += granted;
        return granted;
    }

    /** 新 server tick：饿名单换代（NEXT→当前，拍龄随迁），预算/保留额/进食集/拍龄桶复位重建。 */
    private static void rollTick(long now) {
        prevTickSpent = spent; // m304：上拍消费定格供观测
        tickStamp = now;
        spent = 0;
        STARVED.clear();
        UNSERVED_BY_AGE.clear();
        FED.clear(); // m309 本拍进食集换代
        int n = 0;
        for (Map.Entry<String, Map<Long, Integer>> e : STARVED_NEXT.entrySet()) {
            STARVED.put(e.getKey(), e.getValue());
            for (int a : e.getValue().values()) UNSERVED_BY_AGE.merge(a, 1, Integer::sum);
            n += e.getValue().size();
        }
        STARVED_NEXT.clear(); // 只清外层映射，内层 Map 已移交 STARVED 持有
        reserveLeft = n;
    }

    /** m309：比拍龄 a 更饿（严格更老）且本拍尚未服务的核心数。O(在场拍龄种数)，仅饿核首请求走到。 */
    private static int olderUnserved(int a) {
        int n = 0;
        for (int v : UNSERVED_BY_AGE.tailMap(a, false).values()) n += v;
        return n;
    }

    private static void bucketDec(int a) {
        UNSERVED_BY_AGE.merge(a, -1, Integer::sum);
        if (UNSERVED_BY_AGE.getOrDefault(a, 0) <= 0) UNSERVED_BY_AGE.remove(a);
    }

    /** 停服清态（单机反复进出存档不留残，挂 Sdzjz SERVER_STOPPED 既有清理块）。 */
    public static void clearAll() {
        tickStamp = Long.MIN_VALUE;
        spent = 0;
        reserveLeft = 0;
        STARVED.clear();
        STARVED_NEXT.clear();
        FED.clear();
        UNSERVED_BY_AGE.clear(); // m309
        resetStats(); // m304
        chunkTickStamp = Long.MIN_VALUE; // m324
        CHUNK_SPENT.clear();
    }

    // ===== m324 区块级预算（评审第六优先：maxRecipesPerChunkTick 真接线，四层=节点→核内→区块→全服）=====
    // 用法（cyclesThisTick 两处挂钩）：全服申请**前**按 chunkHeadroom 钳申请量（区块封死的核心不去
    // 全服排队——它的饿是区块政策造成的，全服保底喂它也吃不下，不该占饥饿名单）；全服终裁**后**按
    // 实批量 chunkCharge 记账（先记后裁会把全服拒掉的量虚耗进区块账，同区块他核平白少吃）。
    // 区块层无公平名单：同区块内多核心按 BE tick 序竞争（m302 公平层只治全服公池），区块 cap 压满时
    // 序偏置存在——该键是"大量核心挤一个强加载区块"的管理员钝闸，默认 262144 极高不束缚。
    // 时钟独立于全服层：全服闸关(≤0)时 rollTick 不跑，区块账得自己换拍。
    private static long chunkTickStamp = Long.MIN_VALUE;
    private static final Map<String, Map<Long, Long>> CHUNK_SPENT = new HashMap<>();

    private static void chunkRollIfNeeded(long now) { // m366 时钟入参化
        if (now != chunkTickStamp) { chunkTickStamp = now; CHUNK_SPENT.clear(); }
    }

    /** 本 tick 该坐标所在区块的剩余预算（chunkCap<=0=闸关无限）。仅服务端主线程。 */
    public static long chunkHeadroom(String dim, long chunkKey, long chunkCap, long now) { // m366 区块键由版本侧折算（ChunkPos.toLong）
        if (chunkCap <= 0) return Long.MAX_VALUE;
        chunkRollIfNeeded(now);
        long used = CHUNK_SPENT.getOrDefault(dim, Map.of()).getOrDefault(chunkKey, 0L);
        return Math.max(0L, chunkCap - used);
    }

    /** 按最终批准量记区块账（在全服终裁之后调）。仅服务端主线程。 */
    public static void chunkCharge(String dim, long chunkKey, int granted, long now) {
        if (granted <= 0) return;
        chunkRollIfNeeded(now);
        CHUNK_SPENT.computeIfAbsent(dim, k -> new HashMap<>())
                .merge(chunkKey, (long) granted, Long::sum);
    }
}
