package com.sdzjz.retro;

import com.sdzjz.node.CanvasGraphState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * m454（C2-③）：结构核心（画布）BE 骨架——语义蓝本=Legacy SCBE（3965 行）的**图状态与拓扑操作
 * 子集**：持唯一图实例 g（蓝本 m430 同构）+ 节点挂/摘/连/断四操作（摘除簿记逐字对照蓝本
 * detachNode 的剪线/移位段）。生产 tick 五分支、在途缓存 nodeBufs、供料/分发/链需求全数随
 * C2-④+ 分片来（蓝本对应段=范围外非漏抄）；无屏无 payload，摆得下存得住是本刀验收线。
 *
 * <p>m471（C2-⑤c1）起：在途缓存 nodeBufs + 直连路由（机器↔机器连线）落地——蓝本
 * {@code nodeBuf/BUF_CAP/bufTypeOk/mergeLegacy/distribute/accepts} 的世代对位，见下方
 * 「在途缓存 + 直连路由」段。m473（C2-⑤c2）起：逻辑节点七分支在 accepts/chainWants **成对**落地 +
 * 六族 tick 清运分支 + 垃圾桶两轮垫底真判定 + 分配器均分（见 tickLogicNode 段）；链式拉料的
 * **供料侧接线**（拉料回路消费 chainWants）随 ⑤c3。
 */
final class StructureCore120 extends BlockEntity implements com.sdzjz.node.RouteBrainProbe { // m481 路由域跨代契约探针（四口转发，纯加法）

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("sdzjz");

    /** m143 合并机映射：1.20.1 无史前存档常态空表（CanvasGraphState 类注），接线点保形。 */
    private static final Map<String, String> MERGED_IDS = Map.of();

    final CanvasGraphState g = new CanvasGraphState(); // 蓝本同名：唯一图实例
    long endpointScanTick = Long.MIN_VALUE; // m458：端点扫描 40t 缓存戳（m218b 谱系防逐观众裸扫）

    /** m471（⑤c1）在途缓存三件：每节点输入缓存（与 machineNodes 同序，懒补齐）+ 遗留共享池
     *  （删节点回收的在途物品，消耗时兜底）+ 单 id 硬顶。名、型、常量逐位照抄蓝本
     *  StructureCoreBlockEntity:72-74，**不发明新数值**（m468 分片稿口径）。 */
    private final java.util.List<java.util.Map<String, Long>> nodeBufs = new java.util.ArrayList<>();
    private final java.util.Map<String, Long> internalBuffer = new java.util.HashMap<>();
    private static final long BUF_CAP = 200000L;

    StructureCore120(BlockPos pos, BlockState state) {
        super(RetroBlocks.STRUCTURE_CORE_BE, pos, state);
    }

    /** 挂节点：栈上画布坐标等随栈 NBT 走（xc/yc/gp 口径，NodeTags 谱系）；状态灯/原因同步补位。 */
    void addNode(ItemStack nodeStack) {
        if (nodeStack.isEmpty()) return;
        g.machineNodes.add(nodeStack.copyWithCount(1));
        g.nodeStatus.add(0);
        g.nodeReason.add("");
        setChanged();
    }

    /** 摘节点（蓝本 detachNode 剪线/移位段逐字对照）：机器线触删即断、下标大于被删位整体左移；
     *  存储线同剪同移；**在途缓存同位摘除并入遗留池**（m471 补齐，蓝本 2434-2435 同序：先补齐对齐
     *  再摘——不补齐时 nodeBufs 比 machineNodes 短，remove(index) 会把别人的在途物品摘走）。
     *  返回被摘栈（归还调用方自理）。 */
    ItemStack detachNode(int index) {
        if (index < 0 || index >= g.machineNodes.size()) return ItemStack.EMPTY;
        nodeBuf(g.machineNodes.size() - 1); // 蓝本同位：先补齐对齐
        ItemStack s = g.machineNodes.remove(index);
        if (index < nodeBufs.size()) mergeLegacy(nodeBufs.remove(index)); // 在途物品回遗留池，不丢
        if (index < g.nodeStatus.size()) g.nodeStatus.remove(index);
        if (index < g.nodeReason.size()) g.nodeReason.remove(index); // m178
        List<int[]> kept = new ArrayList<>();
        for (int[] c : g.connections) {
            if (c[0] == index || c[1] == index) continue; // 触及被删节点→断
            int a = c[0] > index ? c[0] - 1 : c[0];
            int b = c[1] > index ? c[1] - 1 : c[1];
            kept.add(new int[]{a, b});
        }
        g.connections.clear();
        g.connections.addAll(kept);
        for (int i = g.storageEdges.size() - 1; i >= 0; i--) { // 存储连线同剪/移位（蓝本同段）
            long[] e = g.storageEdges.get(i);
            if (e[0] == index) { g.storageEdges.remove(i); g.storageEdgeDims.remove(i); }
            else if (e[0] > index) e[0]--;
        }
        setChanged();
        return s;
    }

    /** 连线（定向 from→to）：越界/自连/同向重复拒绝，返回是否成立。 */
    boolean connect(int from, int to) {
        int n = g.machineNodes.size();
        if (from < 0 || to < 0 || from >= n || to >= n || from == to) return false;
        for (int[] c : g.connections) if (c[0] == from && c[1] == to) return false; // 同向重复
        g.connections.add(new int[]{from, to});
        setChanged();
        return true;
    }

    /** 断线（精确匹配定向对）。 */
    boolean disconnect(int from, int to) {
        for (int i = 0; i < g.connections.size(); i++) {
            int[] c = g.connections.get(i);
            if (c[0] == from && c[1] == to) {
                g.connections.remove(i);
                setChanged();
                return true;
            }
        }
        return false;
    }

    // ===== m471（C2-⑤c1）：在途缓存 + 直连路由（机器↔机器连线）=====
    // 蓝本对位：nodeBuf/bufTypeOk/bufCountFor/bufWithdrawFor/bufAdd/mergeLegacy（缓存六件）+
    // distribute 两轮垫底 + accepts 收料判定（路由两脑的下发那半）。世代取舍（m468 分片稿显式记档）：
    // ①accepts 本刀只落 MachineItem·耗料机一支——零 NodeTags 零规划器依赖，正好盖住 ⑤b 那 11 台
    //   defConsume 的上游喂料（「产线接产线」本刀能玩）；逻辑节点七分支随 ⑤c2 与 chainWants **成对**
    //   落地（m131b→m132-6 血案：只写 accepts 那面，拉料恒不通拖了一整刀才实锤）；
    // ②垃圾桶两轮垫底的**壳**先立好、判定恒 false（NodeTags 未上挂、虚空处理器本世代未建）；
    // ③链式拉料 chainWants 随 ⑤c3；④蓝本 distribute 尾巴是 depositOrBuffer→addOutput（输出缓存/
    //   断网喷射），本世代无输出缓存（⑤a 取舍②），故余量原样回吐给调用方走既定「折损黄灯」——
    //   账面上看得见，绝不静默吞件（m99 家法：静默无效比数值弱更伤）。

    /** 取第 i 个节点的输入缓存（懒补齐对齐 machineNodes；蓝本同名同构）。 */
    private java.util.Map<String, Long> nodeBuf(int i) {
        while (nodeBufs.size() < g.machineNodes.size()) nodeBufs.add(new java.util.HashMap<>());
        return nodeBufs.get(i);
    }

    /** m270 单节点缓存类型上限：拒收**新类型**，已有类型照常合并，0=无限。
     *  调用规矩同蓝本：**投递/取料前判**——拒收=不投递，余量转产出仓，零物品损失。 */
    private static boolean bufTypeOk(java.util.Map<String, Long> m, String id) {
        int cap = com.sdzjz.config.SdzjzConfig.get().maxBufferTypesPerNode;
        return cap <= 0 || m.containsKey(id) || m.size() < cap;
    }

    /** 节点可用量 = 自己的输入缓存 + 遗留共享池（蓝本 bufCountFor 同口径）。 */
    private long bufCountFor(int i, String id) {
        return StorageCore120.satAdd(nodeBuf(i).getOrDefault(id, 0L), internalBuffer.getOrDefault(id, 0L)); // m273 饱和加法
    }

    /** 先扣自己的输入缓存，不足部分再扣遗留池（蓝本同序）。 */
    private void bufWithdrawFor(int i, String id, long amt) {
        if (amt <= 0) return;
        java.util.Map<String, Long> m = nodeBuf(i);
        long own = m.getOrDefault(id, 0L);
        long fromOwn = Math.min(own, amt);
        if (fromOwn > 0) {
            long leftOwn = own - fromOwn;
            if (leftOwn <= 0) m.remove(id); else m.put(id, leftOwn); // 写路径零/负值即 remove（m273 口径，读侧据此校验）
        }
        long rest = amt - fromOwn;
        if (rest > 0) {
            long left = internalBuffer.getOrDefault(id, 0L) - rest;
            if (left <= 0) internalBuffer.remove(id); else internalBuffer.put(id, left);
        }
        setChanged();
    }

    /** 遗留池入账，BUF_CAP 硬顶（m273 饱和加法：中间加法溢出翻负会绕过封顶）。
     *  世代差记档：蓝本超顶部分溢到输出缓存，本世代无输出缓存 → 硬顶截留并 warn 记账，
     *  只在「单 id 遗留池已达 20 万还再摘一个满缓存节点」的极端形出现。 */
    private void bufAdd(String id, long amt) {
        if (id == null || id.isEmpty() || amt <= 0) return;
        long sum = StorageCore120.satAdd(internalBuffer.getOrDefault(id, 0L), amt);
        if (sum > BUF_CAP) {
            LOGGER.warn("结构核心 {} 遗留池 {} 触顶 {}，截留 {} 件（本世代无输出缓存，m471 记档）",
                    worldPosition, id, BUF_CAP, sum - BUF_CAP);
            internalBuffer.put(id, BUF_CAP);
        } else {
            internalBuffer.put(id, sum);
        }
        setChanged();
    }

    /** 节点缓存回收进遗留池（bufAdd 自带封顶），摘节点不丢在途物品（蓝本 mergeLegacy）。 */
    private void mergeLegacy(java.util.Map<String, Long> m) {
        if (m == null) return;
        for (java.util.Map.Entry<String, Long> e : m.entrySet()) bufAdd(e.getKey(), e.getValue());
    }

    /** 垃圾桶族判定（蓝本 {@code NodeTags.isTrash(st) || st.getItem() instanceof VoidProcessorItem}）。
     *  m473 真判定上线——两轮垫底自此生效。虚空处理器随 <b>⑤e</b> 入伙：xpPool 未建，它连收料行都还
     *  没开（见 accepts ⑤e 行），现在进本名单=死码假装；届时与 accepts/chainWants 行**成对**补。 */
    private static boolean isTrashLike(ItemStack st) {
        return com.sdzjz.node.NodeTags.isTrash(st);
    }





    // m218d 谱系：chainWants 顶层调用的 visited 集合复用（服务端 tick 单线程、递归自身传同一集合，
    // 清场复用安全）。⑤c3 拉料循环届时消费，判官现在就用。
    private final transient java.util.HashSet<Integer> wantsScratch = new java.util.HashSet<>();

    java.util.Set<Integer> wantsScratchCleared() { wantsScratch.clear(); return wantsScratch; }



    private final transient java.util.HashSet<Integer> trashScratch = new java.util.HashSet<>();

    private java.util.Set<Integer> trashScratchCleared() { trashScratch.clear(); return trashScratch; }

    /** m471 产物出线：先按边喂下游节点在途缓存（蓝本 distribute 两轮垫底同构），余量落 kind0 产出仓；
     *  返回**仍无处可去**的件数（>0=折损，调用方点黄灯）。
     *  <p><b>不变量</b>：绝不堵死在下游缓存里（装不下就往下游一级级让位到产出仓），也绝不静默吞件
     *  （最后一站拒收就把数字原样回吐，账面可见）。 */
    long routeOut(net.minecraft.world.level.Level world, int fromIndex, StorageCore120 dep,
            boolean hasOut, String id, long amt) {
        if (amt <= 0) return 0L;
        if (hasOut) {
            for (int pass = 0; pass < 2; pass++) {   // 蓝本 m150 两轮：第一轮跳过垃圾桶族，第二轮只喂它们——
                for (int[] c : g.connections) {      // 垃圾桶永远是最后去处，与连线先后无关（判定随 ⑤c2，壳先立）
                    if (amt <= 0) return 0L;
                    if (c[0] != fromIndex) continue; // 蓝本 outT[i] 的等价遍历（同为 connections 原序，逐位一致）
                    int t = c[1];
                    if (t < 0 || t >= g.machineNodes.size()) continue;
                    if ((pass == 0) == isTrashLike(g.machineNodes.get(t))) continue;
                    if (!accepts(world, t, id)) continue;
                    java.util.Map<String, Long> m = nodeBuf(t);
                    if (!bufTypeOk(m, id)) continue; // m270 类型上限：跳过满型目标，余量走下面产出仓
                    long cur = m.getOrDefault(id, 0L);
                    long put = Math.min(Math.max(0L, BUF_CAP - cur), amt);
                    if (put > 0) { m.put(id, cur + put); amt -= put; setChanged(); }
                }
            }
        }
        return depositTail(dep, id, amt); // m473 抽成共用尾巴（distributeEvenOut 同用），行为逐位不变
    }

    /** 存储尾巴（routeOut/distributeEvenOut 共用，m473 自 routeOut 原样抽出）：余量落 kind0 仓，
     *  deposit 全有或全无（类型满=整栈拒收），拒收即**原样回吐**零丢件（m471 判官③口径不动）。 */
    private long depositTail(StorageCore120 dep, String id, long amt) {
        if (amt <= 0) return 0L;
        if (dep == null) return amt;
        while (amt > 0) {
            int give = (int) Math.min(amt, Integer.MAX_VALUE);
            ItemStack rest = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(new net.minecraft.resources.ResourceLocation(id)), give);
            dep.deposit(rest);
            if (!rest.isEmpty()) return amt;
            amt -= give;
        }
        return 0L;
    }

    /** m473 均分分发（分配器，蓝本 distributeEven0 世代版）：在所有"吃得下"的目标间平分、余数轮转
     *  （前 extra 个各多一件）；类型上限拒收的份额与装不下的部分并入余量走存储尾巴；仍无处去的
     *  **原样回吐**（蓝本 addOutput 输出缓存的世代对位=回调用方持料，m471 取舍④同路）。
     *  蓝本刻意无两轮垫底：垃圾桶在均分里是平等目标（accepts=白名单），逐位照抄不发明。 */
    long distributeEvenOut(net.minecraft.world.level.Level world, int fromIndex, StorageCore120 dep,
            boolean hasOut, String id, long amt) {
        if (amt <= 0) return 0L;
        if (hasOut) {
            int okN = 0; // m357 scratch 两遍法：收可吃目标进复用数组（不跨调用不可重入）
            for (int[] c : g.connections) {
                if (c[0] != fromIndex) continue;
                int t = c[1];
                if (t >= 0 && t < g.machineNodes.size() && accepts(world, t, id)) {
                    if (okN >= evenOk.length) evenOk = java.util.Arrays.copyOf(evenOk, evenOk.length * 2);
                    evenOk[okN++] = t;
                }
            }
            if (okN > 0) {
                long share = amt / okN, extra = amt % okN, undelivered = 0;
                for (int k = 0; k < okN; k++) {
                    long want = share + (k < extra ? 1 : 0);
                    if (want <= 0) continue;
                    java.util.Map<String, Long> m = nodeBuf(evenOk[k]);
                    if (!bufTypeOk(m, id)) { undelivered += want; continue; } // m270 类型上限：拒收份额转默认路由
                    long cur = m.getOrDefault(id, 0L);
                    long put = Math.min(Math.max(0L, BUF_CAP - cur), want);
                    if (put > 0) { m.put(id, cur + put); setChanged(); }
                    undelivered += want - put;
                }
                amt = undelivered;
            }
        }
        return depositTail(dep, id, amt);
    }

    // ===== m473 逻辑节点清运 scratch（蓝本 m350/m357 同构：转存进复用双数组再处理，
    // 不跨节点不跨 tick 不可重入；服务端 tick 单线程） =====
    private transient String[] drainIds = new String[16];
    private transient long[] drainAmts = new long[16];
    private transient int[] evenOk = new int[8]; // m357 均分目标 scratch

    private int fillDrain(java.util.Map<String, Long> m) {
        int n = m.size();
        if (drainIds.length < n) {
            int cap = Math.max(n, drainIds.length * 2);
            drainIds = new String[cap];
            drainAmts = new long[cap];
        }
        int k = 0;
        for (java.util.Map.Entry<String, Long> en : m.entrySet()) {
            drainIds[k] = en.getKey();
            Long v = en.getValue();
            drainAmts[k] = v == null ? 0L : v;
            k++;
        }
        return k;
    }

    /** m471 白耗料护栏（m466 那条的超集）：产物**有没有去处**——任一下游节点收得下（收料判定过 +
     *  类型位没满 + 缓存没到顶）｜ kind0 产出仓类型有余量。无出线时逐位退化成 m466 原判据
     *  {@code dep.acceptsPlainType}，m466 判官行为不变（那条本就无出线）。宁待机不白耗料。 */
    private boolean hasSinkFor(net.minecraft.world.level.Level world, int fromIndex, StorageCore120 dep, String id) {
        for (int[] c : g.connections) {
            if (c[0] != fromIndex) continue;
            int t = c[1];
            if (t < 0 || t >= g.machineNodes.size()) continue;
            if (!accepts(world, t, id)) continue;
            java.util.Map<String, Long> m = nodeBuf(t);
            if (bufTypeOk(m, id) && m.getOrDefault(id, 0L) < BUF_CAP) return true;
        }
        return dep != null && dep.acceptsPlainType(id);
    }

    /** 在途缓存读数（包内可见：判官对账用，屏侧顶栏「在途件数」到序时同口共用；蓝本 bufferedTotal 谱系）。
     *  只读不建表——越界返 0，不触发懒补齐，判官问一句不改状态。 */
    long bufAmount(int i, String id) {
        if (i < 0 || i >= nodeBufs.size()) return 0L;
        return nodeBufs.get(i).getOrDefault(id, 0L);
    }

    /** 遗留共享池读数（同上）。 */
    long legacyAmount(String id) {
        return internalBuffer.getOrDefault(id, 0L);
    }

    /** m340 连线喂料的「显式供料线补足」：连线喂料优先、显式 kind1 供料线补缺口（配置 supplyTopUp）。 */
    private StorageCore120 topUpSource(net.minecraft.world.level.Level world, int i) {
        return com.sdzjz.config.SdzjzConfig.get().supplyTopUp ? supplyTarget(world, i) : null;
    }

    private long dualCount(int i, StorageCore120 exp, String id) {
        return bufCountFor(i, id) + (exp != null ? exp.count(id) : 0L);
    }

    /** 先吃缓存后吃供料线；调用方的量已被 dualCount 夹过，缺口非零时 exp 必非空（蓝本同注）。 */
    private void dualWithdraw(int i, StorageCore120 exp, String id, long want) {
        long fromBuf = Math.min(want, bufCountFor(i, id));
        bufWithdrawFor(i, id, fromBuf);
        if (want > fromBuf && exp != null) exp.withdraw(id, (int) Math.min(Integer.MAX_VALUE, want - fromBuf));
    }

    /** 缺料说人话（蓝本 whyMissingBufIn 世代版：running 恒 1；有供料线补足时口径写「缓存+供料」）。 */
    private String whyMissingBufIn(int node, StorageCore120 exp, java.util.List<com.sdzjz.machine.MachineDef.Input> ins) {
        String where = exp != null ? "缓存+供料" : "缓存";
        for (com.sdzjz.machine.MachineDef.Input in : ins) {
            long have = dualCount(node, exp, in.item());
            if (have < in.count()) return "缺料：" + itemName120(in.item()) + "（" + where + " " + have + "/需 " + in.count() + "）";
        }
        return "缺料（" + where + "不足）";
    }

    /** m471 邻接派生位（每拍重建到复用数组）。世代取舍：蓝本 m179 按 topoRev 修订号缓存 hasOut/hasIn/outT，
     *  本世代改「每拍两趟填复用数组 + 路由直接遍历 g.connections」——省掉修订号与 outT 逐节点分配，
     *  代价 O(V+E)/拍（画布规模下可忽略），换掉「漏 bump 即静默错路由」那整条回归面
     *  （蓝本靠 profile core 的 planCompiles 计数器兜的就是它）。规模上来再上修订号，改法现成。 */
    private transient boolean[] planHasOut = new boolean[0];
    private transient boolean[] planHasIn = new boolean[0];

    private void buildPlan(int n) {
        if (planHasOut.length < n) { planHasOut = new boolean[n]; planHasIn = new boolean[n]; }
        java.util.Arrays.fill(planHasOut, 0, n, false);
        java.util.Arrays.fill(planHasIn, 0, n, false);
        for (int[] c : g.connections)
            if (c[0] >= 0 && c[0] < n && c[1] >= 0 && c[1] < n) { planHasOut[c[0]] = true; planHasIn[c[1]] = true; }
    }

    // ===== m464（C2-⑤a）+ m466（C2-⑤b）：生产 tick 脊柱 + 数据驱动族（生成类+耗料类）=====
    // 蓝本=SCBE tickInner 的最小可产集（m463 普查分片）：预算折算（cyclesThisTick 四层闸逐位对照，
    // 世代差=无速度升级 rate 恒 1.0、无并发升级恒 1 台）→ 通用 MachineItem 分支两半：
    // consumesInputs=false 半（rollDrops → kind0 产出仓 deposit）；=true 半（m466：kind1 供料仓
    // 蓝本式按料折算 doCycles → withdraw → 产出同前）→ 灯表说人话。
    // m471（⑤c1）接线：产出侧走 routeOut（有出线先喂下游节点在途缓存，余量落 kind0 仓），
    // 供料侧补 hasIn 半（吃自己的在途缓存 + m340 显式供料线补足）——「产线接产线」自此能玩。
    // 世代取舍（m463 记档，m471 修订三条）：①本世代核心**自动运转**（有节点即 tick，开停闸随屏侧
    // 到序）——产出仓连线（m458 手势）本身就是玩家显式授权；②【m471 改】产出要有去处：**出线或
    // 产出仓二者有其一**即可开工，两者皆无=红灯待机（本世代仍无输出缓存/断网喷射，零吞件零实体
    // 洪水）；③特种/配方机型（def 产表为空或熔炉族）黄灯待后续分片；④组件产物（山羊角）随 ⑤d
    // 精确账本对接；⑤【m471 改】去处满：免费产物=折损黄灯（料本免费不产不损）；耗料机=扣料前先验
    // hasSinkFor（下游收得下 ｜ 仓类型有余量）待机，白耗料不可接受——m466 护栏的超集，无出线时
    // 逐位退化成原判据；⑥MachineXp 经验产出（蓝本 xpPool += mxp）随 ⑤e 经验经济族到序——本世代
    // 耗料机只产物品不攒经验（显式记档非漏抄）；⑦【m471 销】供料侧 hasIn 分支与 nodeBufs 已到位，
    // 余下的链式拉料 chainWants 随 ⑤c3。
    private transient double[] workAcc = new double[0]; // 蓝本 m356 数组直取（不落盘：重启丢半周期无妨）
    private transient long recipesThisTick;             // 蓝本 m270 全核 tick 周期预算游标
    private transient long ticks; // m473 逻辑节点 5t 节拍游标（蓝本 be.ticks；世代取舍记档：暂无 m218c 多核心移相，画布规模下可忽略，规模上来再补相位）

    static void tick(net.minecraft.world.level.Level world, BlockPos pos, BlockState state, StructureCore120 be) {
        if (world.isClientSide) return;
        be.recipesThisTick = 0;
        be.ticks++;
        com.sdzjz.config.SdzjzConfig cfg = com.sdzjz.config.SdzjzConfig.get();
        int nSize = be.g.machineNodes.size();
        be.buildPlan(nSize); // m471 邻接派生位：hasOut/hasIn（每拍重建，见 buildPlan 注的世代取舍）
        if (be.ticks % 5 == 0) be.pullSupply(world, nSize); // m475（⑤c3）供料拉料段：与逻辑节点清运同拍，先拉后跑（蓝本同位）
        for (int i = 0; i < nSize; i++) {
            ItemStack st = be.g.machineNodes.get(i);
            // m473（⑤c2）暂停最先判（m110b；m99 教训：early-continue 必须在任何累积/扣料之前）——任意节点类型通用
            if (com.sdzjz.node.NodeTags.nodePaused(st)) { be.stat(i, 2, "已手动暂停"); continue; }
            boolean hasOut = be.planHasOut[i], hasIn = be.planHasIn[i]; // m471
            // m473 传感器闸门连锁：该节点全部出线目标都是关着的闸 → 整台暂停（不白产、不绕道塞存储，蓝本同位）
            if (hasOut && be.allGatesClosed(world, i)) { be.stat(i, 2, "下游闸门全关"); continue; }
            if (be.tickLogicNode(world, i, st, hasOut)) continue; // m473 逻辑节点六族清运分支（命中即本拍归它管）
            if (!(st.getItem() instanceof RetroMachineItems.RetroMachineItem rmi)) { be.stat(i, 0, ""); continue; }
            com.sdzjz.machine.MachineDef def = rmi.def;
            if (com.sdzjz.machine.Machines.smelterFamily(def.id()) || def.outputs().isEmpty()) {
                be.stat(i, 2, "配方/特种机型随 C2-⑤ 后续分片到序（本世代暂只跑数据驱动生成类）");
                continue;
            }
            StorageCore120 dep = be.depositTarget(world, i);
            if (!hasOut && dep == null) { // m471 取舍②：出线与产出仓二者有其一即可开工
                be.stat(i, 3, "未连产出仓、也没有出线（点机器再点仓=绿线产出仓，或从本机拉一条出线喂下游机器）");
                continue;
            }
            StorageCore120 sup = null; // kind1 供料仓（无入线时的唯一料源）
            StorageCore120 exp = null; // m340 有入线时的「显式供料线补足」源
            if (def.consumesInputs()) { // ===== m466（C2-⑤b）耗料类 + m471（⑤c1）连线喂料 =====
                if (hasIn) {
                    exp = be.topUpSource(world, i); // 连线喂料优先、显式供料线补缺口；无供料线=纯吃缓存（判官①）
                } else {
                    sup = be.supplyTarget(world, i); // 蓝本 supplyFor 的世代精简（kind1，m458 循环手势：绿线再点=金线供料）
                    if (sup == null) {
                        be.stat(i, 3, "未连供料仓、也没有入线（点机器再点仓、再点循环到金线=供料仓，或从上游机器拉一条入线）");
                        continue;
                    }
                }
                boolean sink = true; // 白耗料护栏：先验产出**有去处**再扣料（m466 护栏的 m471 超集，见 hasSinkFor）
                for (com.sdzjz.machine.MachineDef.Drop d : def.outputs()) { // 概率产物（如龙蛋 0.005）也计入先验=宁待机不烧料
                    if ("minecraft:goat_horn".equals(d.item())) continue;
                    if (!be.hasSinkFor(world, i, dep, d.item())) { sink = false; break; }
                }
                if (!sink) { be.stat(i, 2, "产出仓类型已满且下游吃不下，耗料机不白耗料先待机（清账本类型/换仓/加下游即恢复）"); continue; }
            }
            int cycles = be.cyclesThisTick(world, i, def.baseIntervalTicks(), cfg);
            if (cycles <= 0) continue; // 预算剪零已亮黄说话；没攒够周期=保持上拍灯
            int doCycles = cycles;
            if (def.consumesInputs()) { // 蓝本 m99：料不够整批时按料量折算周期数（running 恒 1 世代差）
                if (hasIn) { // m471：吃自己的在途缓存（+ 遗留池），缺口由显式供料线补足（蓝本 dualCount/dualWithdraw）
                    for (com.sdzjz.machine.MachineDef.Input in : def.inputs())
                        doCycles = (int) Math.min(doCycles, be.dualCount(i, exp, in.item()) / (long) in.count());
                    if (doCycles <= 0) { be.stat(i, 3, be.whyMissingBufIn(i, exp, def.inputs())); continue; }
                    for (com.sdzjz.machine.MachineDef.Input in : def.inputs())
                        be.dualWithdraw(i, exp, in.item(), (long) in.count() * doCycles);
                } else {
                    for (com.sdzjz.machine.MachineDef.Input in : def.inputs())
                        doCycles = (int) Math.min(doCycles, sup.count(in.item()) / (long) in.count());
                    if (doCycles <= 0) { be.stat(i, 3, whyMissingSup(sup, def.inputs())); continue; }
                    for (com.sdzjz.machine.MachineDef.Input in : def.inputs())
                        sup.withdraw(in.item(), in.count() * doCycles); // 蓝本同式（int 乘，cap 钳过不溢）
                }
                be.stat(i, 1, ""); // 蓝本位次：扣料即点绿——全概率产表（如猪灵交易）本拍全没掷中也不滞留旧灯
            }
            boolean skippedComponent = false, produced = false;
            for (com.sdzjz.machine.MachineDef.Drop d : def.outputs()) {
                if ("minecraft:goat_horn".equals(d.item())) { skippedComponent = true; continue; } // 组件产物 ⑤d
                long sum = com.sdzjz.machine.DropRolls.rollDrops(world.getRandom(), d, doCycles, 0);
                if (sum <= 0) continue;
                long left = be.routeOut(world, i, dep, hasOut, d.item(), sum); // m471：先喂下游缓存，余量落仓
                if (left > 0) { // 折损（见类注取舍⑤）：下游吃不下且产出仓也拒收——回吐的数字在这儿变黄灯，不静默
                    be.stat(i, 2, "产出没处放，折损中（下游缓存满/产出仓类型已满，清账本类型或加下游即恢复）");
                    produced = false;
                    break;
                }
                produced = true;
            }
            if (produced) be.stat(i, 1, "");
            else if (skippedComponent) be.stat(i, 2, "组件产物机型随 C2-⑤d（精确账本对接）到序");
        }
    }

    /** m473（⑤c2）逻辑节点六族清运分支（蓝本 tick 分配器/过滤器/垃圾桶/抽取/开关/传感器六段的世代版，
     *  5t 节拍同蓝本）。命中六族返回 true——本拍无论有没有活都不再走机器路径；非逻辑节点返回 false。
     *  <p><b>世代取舍显式记档（送不出去的货）</b>：蓝本装不下走 depositOrBuffer→addOutput（输出缓存/
     *  断网喷射），本世代没有（⑤a 取舍②）——统一**原样回灌自己缓存**持料待命并亮黄说话（m471 取舍④
     *  同路：账面可见绝不吞件；缓存到顶=对上游天然背压，蓝本抽取节点残量语义的推广）。 */
    private boolean tickLogicNode(net.minecraft.world.level.Level world, int i, ItemStack st, boolean hasOut) {
        boolean isF = com.sdzjz.node.NodeTags.isFilter(st), isT = com.sdzjz.node.NodeTags.isTrash(st),
                isX = com.sdzjz.node.NodeTags.isExtractor(st), isSw = com.sdzjz.node.NodeTags.isSwitch(st),
                isSe = com.sdzjz.node.NodeTags.isSensor(st), isD = com.sdzjz.node.NodeTags.isDistributor(st);
        if (!(isF || isT || isX || isSw || isSe || isD)) return false;
        if (ticks % 5 != 0) return true; // 5t 节拍（蓝本同：非节拍不动缓存不改灯）
        java.util.Map<String, Long> own = nodeBuf(i);
        if (isD) { // 分配器：来料在出线目标间均分（余数轮转），没人要的走产出仓
            if (own.isEmpty()) return true;
            StorageCore120 dep = depositTarget(world, i);
            boolean moved = false; long held = 0;
            final int dn = fillDrain(own); own.clear(); // m350 整锅转存再清（回灌进空表语义同蓝本）
            for (int dk = 0; dk < dn; dk++) {
                String id = drainIds[dk]; long amt = drainAmts[dk];
                if (amt <= 0) continue;
                long left = distributeEvenOut(world, i, dep, hasOut, id, amt);
                if (left < amt) moved = true;
                if (left > 0) { own.merge(id, left, StorageCore120::satAdd); held += left; }
            }
            lampAfterDrain(i, moved, held, false); // 蓝本：动了才点绿，没动保持上拍灯
            return true;
        }
        if (isF) { // 过滤器：清运输入缓存——放行的沿出线下游，拦下的直落产出仓（蓝本 targets=null 同义）
            if (own.isEmpty()) return true;
            StorageCore120 dep = depositTarget(world, i);
            boolean moved = false; long held = 0;
            final int dn = fillDrain(own); own.clear();
            for (int dk = 0; dk < dn; dk++) {
                String id = drainIds[dk]; long amt = drainAmts[dk];
                if (amt <= 0) continue;
                long left = routeOut(world, i, dep, hasOut && com.sdzjz.node.NodeTags.filterPasses(st, id), id, amt);
                if (left < amt) moved = true;
                if (left > 0) { own.merge(id, left, StorageCore120::satAdd); held += left; }
            }
            lampAfterDrain(i, moved, held, false);
            return true;
        }
        if (isT) { // m150 垃圾桶：吞光输入缓存并累计 tc（白名单在收料侧 accepts 把关，吞侧不设卡=蓝本同构）
            if (own.isEmpty()) return true;
            long ate = 0;
            final int dn = fillDrain(own); own.clear();
            for (int dk = 0; dk < dn; dk++) if (drainAmts[dk] > 0) ate += drainAmts[dk];
            if (ate > 0) {
                com.sdzjz.node.NodeTags.addTrashCount(st, ate); // m353 三段式写读（丢写="已吞"死数血案）
                setChanged();
                stat(i, 1, "");
            }
            return true;
        }
        if (isX) { // m154/m157/m160 抽取节点：开=推缓存给收的目标+搬仓，残量持料背压；感应暂停持料；关=退料
            boolean on = com.sdzjz.node.NodeTags.extractorOn(st);
            if (on && !extractorLive(world, i, st)) { stat(i, 2, "感应暂停：持料待命（监测条件一到自动续跑）"); return true; }
            StorageCore120 dep = depositTarget(world, i);
            if (!on) { // m157 歇工退料：缓存退回产出仓（本世代无默认路由；没接仓=持料不丢）
                boolean movedO = false; long heldO = 0;
                if (!own.isEmpty()) {
                    final int dnO = fillDrain(own); own.clear();
                    for (int dk = 0; dk < dnO; dk++) {
                        String id = drainIds[dk]; long amt = drainAmts[dk];
                        if (amt <= 0) continue;
                        long left = routeOut(world, i, dep, false, id, amt);
                        if (left < amt) movedO = true;
                        if (left > 0) { own.merge(id, left, StorageCore120::satAdd); heldO += left; }
                    }
                    if (movedO) setChanged();
                }
                stat(i, 2, heldO > 0 ? "抽取已停止；退料没处放，持料待命（接一条产出仓绿线即退清）" : "抽取已停止");
                return true;
            }
            if (own.isEmpty()) { stat(i, 0, ""); return true; }
            boolean moved = false;
            final int dn = fillDrain(own); own.clear(); // 蓝本"转存不清+残量 put/remove"的等价式：清后回灌残量
            for (int dk = 0; dk < dn; dk++) {
                String id = drainIds[dk]; long amt = drainAmts[dk];
                if (amt <= 0) continue;
                long left = routeOut(world, i, dep, hasOut, id, amt); // 两轮垫底+m157 搬仓（定向存储=明确目的地）
                if (left < amt) moved = true;
                if (left > 0) own.merge(id, left, StorageCore120::satAdd); // 残量留缓存=背压（蓝本原样，不亮黄）
            }
            stat(i, moved ? 1 : 0, "");
            return true;
        }
        if (isSw && !com.sdzjz.node.NodeTags.switchOn(st)) { stat(i, 2, "开关已关：持料待命"); return true; }
        if (isSe && !sensorOpen(world, i)) { stat(i, 2, "传感器关闸：持料待命（监测条件未满足）"); return true; }
        // 开关（开）/传感器（开闸）：直通转发自己的缓存（蓝本两段同构）
        if (own.isEmpty()) { stat(i, 0, ""); return true; }
        StorageCore120 dep = depositTarget(world, i);
        boolean moved = false; long held = 0;
        final int dn = fillDrain(own); own.clear();
        for (int dk = 0; dk < dn; dk++) {
            String id = drainIds[dk]; long amt = drainAmts[dk];
            if (amt <= 0) continue;
            long left = routeOut(world, i, dep, hasOut, id, amt);
            if (left < amt) moved = true;
            if (left > 0) { own.merge(id, left, StorageCore120::satAdd); held += left; }
        }
        lampAfterDrain(i, moved, held, true); // 蓝本开关/传感器：stat(moved?1:0)
        return true;
    }

    /** m473 清运后灯表收口：持料>0 亮黄说人话（世代取舍④：本世代无输出缓存，货在缓存没丢）；
     *  否则按蓝本口径——动了点绿，没动的分配器/过滤器保持上拍灯、开关/传感器/抽取归零待机。 */
    private void lampAfterDrain(int i, boolean moved, long held, boolean zeroWhenIdle) {
        if (moved) setChanged();
        if (held > 0) {
            stat(i, 2, "去处满，持料待命（下游缓存/产出仓都装不下——货在本节点缓存里没丢，清出空位自动续走）");
            return;
        }
        if (moved) stat(i, 1, "");
        else if (zeroWhenIdle) stat(i, 0, "");
    }

    /** m475（C2-⑤c3）逻辑节点供料拉取·链式需求传播（蓝本 tick 供料段 m92 世代版，5t 与清运同拍）：
     *  任何逻辑节点接了「存储→自己」的 kind1 供料边，都按 <b>自身放行规则 ∩ 下游机器真实需求</b>
     *  （{@link #chainWants}，m473 已就位）从那个仓里拉料进自己的在途缓存。遍历的是仓库类型清单
     *  （有限），逐条按 id 问一次链式需求。
     *  <p><b>抽取节点=主动泵</b>（m154）：不问下游要不要，按挡位速率无条件抽——但 m157 修订过
     *  「没去处的不抽」（否则把全网络吸进缓存囤着失踪），故非搬仓模式下要求至少一个出线目标当下肯收；
     *  m160 白名单名单外碰都不碰；有 kind0 定向产出仓=搬仓模式（pumpAll）全抽。
     *  <p><b>精确账本支路</b>（m155→m158）：带组件的精确件只在「该 id 的出线链通向垃圾桶」时才抽
     *  （{@link #chainEndsInTrash} 授权闸）——反正是去销毁，抹组件无损语义。
     *  <p><b>三处世代取舍显式记档</b>：①无数量升级，蓝本 {@code extractorRate × (1+nodeCount)} 的
     *  后半在本世代恒 1（m464 取舍同源），故泵速=挡位原值；②供料源只认自家存储核心（本世代无
     *  数据面板聚合视图），蓝本的 banks 多源合并退化成单源；③<b>蓝本泵支路不查 nodePaused</b>
     *  （暂停的抽取节点照抽，只是抽进来的货在清运侧被 m110b 拦住不动）——本刀<b>逐位照抄不擅自改</b>，
     *  两代同形优先；这是蓝本疑似疏漏，留待作者拍板，改要两代一起改。 */
    private void pullSupply(net.minecraft.world.level.Level world, int nSize) {
        for (int i = 0; i < nSize; i++) {
            ItemStack stL = g.machineNodes.get(i);
            // 五族逻辑节点（蓝本同款清单：**不含垃圾桶**——防"仓→垃圾桶"手滑清空整仓，垃圾桶只吞推送来的）
            if (!(com.sdzjz.node.NodeTags.isFilter(stL) || com.sdzjz.node.NodeTags.isSwitch(stL)
                    || com.sdzjz.node.NodeTags.isSensor(stL) || com.sdzjz.node.NodeTags.isDistributor(stL)
                    || com.sdzjz.node.NodeTags.isExtractor(stL))) continue;
            boolean pump = com.sdzjz.node.NodeTags.isExtractor(stL); // m154 抽取节点=主动泵
            if (pump && !extractorLive(world, i, stL)) continue;     // m160 手动关或感应未放行=不抽
            boolean pumpAll = pump && depositTarget(world, i) != null; // m157 有定向产出仓=搬仓，全抽
            StorageCore120 sup = supplyTarget(world, i);
            if (sup == null) continue;
            java.util.Map<String, Long> ownL = nodeBuf(i);
            long pumpRate = 0, pumped = 0;
            long bufCapL = 4096;
            if (pump) {
                pumpRate = com.sdzjz.node.NodeTags.extractorRate(stL); // 世代取舍①：无数量升级，蓝本 ×(1+nodeCount) 恒 ×1
                // m163a：泵缓存只是 id→long 计数不占实存，上限随速率放宽到双周期余量（否则高挡位速率>缓存=卡喉）
                bufCapL = Math.max(4096, pumpRate * 2);
            }
            boolean got1 = false;
            final int dnP = fillDrain(sup.storeView()); // m350 转存 scratch：withdraw 当场实扣，边遍历边改表会炸迭代器
            for (int dk = 0; dk < dnP; dk++) {
                String id = drainIds[dk];
                long have = ownL.getOrDefault(id, 0L);
                if (have >= bufCapL) continue; // m116 每种封顶（泵按速率放宽）
                if (!pump && !chainWants(world, i, id, 0, wantsScratchCleared())) continue; // m218d scratch 复用
                if (pump && !com.sdzjz.node.NodeTags.machineFilterAllows(stL, id)) continue; // m160 白名单：名单外碰都不碰
                if (pump && !pumpAll) { // m157 没去处的不抽（出线目标当下肯收才抽；过滤白名单在 accepts 里生效）
                    boolean anyTake = false;
                    for (int[] c : g.connections) {
                        if (c[0] != i) continue;
                        int t = c[1];
                        if (t >= 0 && t < nSize && accepts(world, t, id)) { anyTake = true; break; }
                    }
                    if (!anyTake) continue;
                }
                long roomL = bufCapL - have;
                if (pump) roomL = Math.min(roomL, pumpRate - pumped); // 泵按挡位限速
                if (roomL <= 0) { if (pump) break; else continue; }
                if (!bufTypeOk(ownL, id)) continue; // m270 类型上限：withdraw **前**判——拒收=不抽，物品留仓零损失
                int got = sup.withdraw(id, (int) Math.min(roomL, Integer.MAX_VALUE));
                if (got > 0) { ownL.merge(id, (long) got, StorageCore120::satAdd); pumped += got; got1 = true; } // m273 饱和加法
            }
            // m155→m158 精确账本支路：只在「出线链通向垃圾桶」时抽（抹组件=销毁语义无损）。
            // 世代取舍②：供料源只认自家存储核心（无数据面板聚合视图），蓝本 banks 多源退化成单源。
            for (ItemStack tpl : new java.util.ArrayList<>(sup.exactTemplates())) {
                String idE = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tpl.getItem()).toString();
                long haveE = ownL.getOrDefault(idE, 0L);
                if (haveE >= bufCapL) continue; // m163a 与普通支路同一上限（原硬编码 4096 会先卡死精确支路）
                if (pump && !com.sdzjz.node.NodeTags.machineFilterAllows(stL, idE)) continue; // m160
                if (!bufTypeOk(ownL, idE)) continue; // m270 withdrawExact 前判，拒收=不抽零损失
                if (!chainEndsInTrash(world, i, idE, 0, trashScratchCleared())) continue; // 授权闸
                long roomE = bufCapL - haveE;
                if (pump) roomE = Math.min(roomE, pumpRate - pumped);
                if (roomE <= 0) break;
                ItemStack one = tpl.copyWithCount(1); // withdrawExact 可能移除模板，先复制
                int gotE = sup.withdrawExact(one, (int) Math.min(roomE, Integer.MAX_VALUE));
                if (gotE > 0) { ownL.merge(idE, (long) gotE, StorageCore120::satAdd); pumped += gotE; got1 = true; }
            }
            if (got1) setChanged();
        }
    }

    /** 蓝本 cyclesThisTick 四层闸逐位对照（节点 cap→核内→区块→全服，耗尽只欠不丢工作量累积续）；
     *  世代差：无速度升级 rate 恒 1.0（蓝本 rateOf 查表跳过）。 */
    private int cyclesThisTick(net.minecraft.world.level.Level world, int nodeIndex, int baseInterval,
            com.sdzjz.config.SdzjzConfig cfg) {
        int base = Math.max(1, baseInterval);
        int cap = Math.max(1, cfg.upgradeMaxCyclesPerTick);
        double acc = (nodeIndex < workAcc.length ? workAcc[nodeIndex] : 0.0) + 1.0;
        int cycles = (int) (acc / base);
        if (cycles > cap) cycles = cap;
        boolean hadWork = cycles > 0; // 蓝本 m339：区分"没攒够周期"与"被预算剪没"
        if (cfg.maxRecipesPerCoreTick > 0) { // 蓝本 m270
            long remain = cfg.maxRecipesPerCoreTick - recipesThisTick;
            if (remain <= 0) cycles = 0;
            else if (cycles > remain) cycles = (int) remain;
        }
        boolean chunkGate = cfg.maxRecipesPerChunkTick > 0
                && world instanceof net.minecraft.server.level.ServerLevel;
        if (cycles > 0 && chunkGate) { // 蓝本 m324：全服申请前按区块余量钳
            long head = com.sdzjz.machine.CoreScheduler.chunkHeadroom(
                    world.dimension().location().toString(), net.minecraft.world.level.ChunkPos.asLong(worldPosition),
                    cfg.maxRecipesPerChunkTick, ((net.minecraft.server.level.ServerLevel) world).getServer().getTickCount());
            if (cycles > head) cycles = (int) Math.max(0L, head);
        }
        if (cycles > 0 && cfg.maxRecipesPerNetworkTick > 0
                && world instanceof net.minecraft.server.level.ServerLevel sw) { // 蓝本 m302 全服闸+饥饿保底
            cycles = com.sdzjz.machine.CoreScheduler.request(sw.dimension().location().toString(),
                    worldPosition.asLong(), cycles, cfg.maxRecipesPerNetworkTick, sw.getServer().getTickCount());
        }
        if (cycles > 0 && chunkGate) { // 蓝本 m324 终账（先记后裁会虚耗区块账）
            com.sdzjz.machine.CoreScheduler.chunkCharge(world.dimension().location().toString(),
                    net.minecraft.world.level.ChunkPos.asLong(worldPosition), cycles,
                    ((net.minecraft.server.level.ServerLevel) world).getServer().getTickCount());
        }
        acc -= (double) cycles * base;
        if (acc > (double) base * cap) acc = (double) base * cap; // 被 cap/预算截断时不无限囤积
        if (nodeIndex >= workAcc.length)
            workAcc = java.util.Arrays.copyOf(workAcc, Math.max(nodeIndex + 1, Math.max(8, workAcc.length * 2)));
        workAcc[nodeIndex] = acc;
        recipesThisTick += cycles;
        if (hadWork && cycles == 0)
            stat(nodeIndex, 2, "生产预算本拍已满（核内/区块/全服），工作量已排队下拍续");
        return cycles;
    }

    /** kind0（产出）连线目标（蓝本 depositFor 的世代精简）。 */
    StorageCore120 depositTarget(net.minecraft.world.level.Level world, int machineIndex) {
        return edgeTarget(world, machineIndex, 0);
    }

    /** kind1（供料）连线目标（蓝本 supplyFor→edgeStorage 的世代精简，m466）。 */
    StorageCore120 supplyTarget(net.minecraft.world.level.Level world, int machineIndex) {
        return edgeTarget(world, machineIndex, 1);
    }

    /** 边表定向解析：同维度已加载存储核心，首中即用（蓝本 edgeStorage 同构）。 */
    private StorageCore120 edgeTarget(net.minecraft.world.level.Level world, int machineIndex, int kind) {
        String dim = world.dimension().location().toString();
        for (int e = 0; e < g.storageEdges.size(); e++) {
            long[] edge = g.storageEdges.get(e);
            if (edge[0] != machineIndex || edge[2] != kind) continue;
            if (e < g.storageEdgeDims.size() && !dim.equals(g.storageEdgeDims.get(e))) continue;
            StorageCore120 sc = StorageCore120.loadedCoreAt(world, BlockPos.of(edge[1]));
            if (sc != null) return sc;
        }
        return null;
    }

    /** 缺料说人话（蓝本 whyMissingSupIn 世代版：running 恒 1；名称走 getHoverName 同蓝本 itemName）。 */
    private static String whyMissingSup(StorageCore120 sup, java.util.List<com.sdzjz.machine.MachineDef.Input> ins) {
        for (com.sdzjz.machine.MachineDef.Input in : ins) {
            long have = sup.count(in.item());
            if (have < in.count()) return "缺料：" + itemName120(in.item()) + "（仓 " + have + "/需 " + in.count() + "）";
        }
        return "缺料（仓不足）";
    }

    private static String itemName120(String id) {
        try {
            return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(new net.minecraft.resources.ResourceLocation(id))).getHoverName().getString();
        } catch (Exception e) { return id; }
    }

    /** 灯表写入（蓝本 stat/statR 合口）：值不变不置脏（tick 每拍跑，setChanged 去重防落盘风暴）。 */
    void stat(int i, int code, String why) {
        while (g.nodeStatus.size() <= i) g.nodeStatus.add(0);
        while (g.nodeReason.size() <= i) g.nodeReason.add("");
        if (g.nodeStatus.get(i) == code && g.nodeReason.get(i).equals(why)) return;
        g.nodeStatus.set(i, code);
        g.nodeReason.set(i, why);
        setChanged();
    }

    // ===== NBT：渲染子集即存档画布段（蓝本"存档 writeNbt 与画布快照共用"同构），键在 BE 根层
    // 与蓝本同位同名（m443 DFU 红利口径）。=====
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        g.writeRenderNbt(nbt, null); // m477 共用图状态（句柄本世代恒 null）
        // m471 在途缓存两键：键名同蓝本（internalBuffer/nodeBufs，DFU 红利口径 m443——存档升 1.21.1 后逐键对上）
        CompoundTag buf = new CompoundTag();
        for (java.util.Map.Entry<String, Long> e : internalBuffer.entrySet()) buf.putLong(e.getKey(), e.getValue());
        nbt.put("internalBuffer", buf);
        ListTag nbl = new ListTag(); // 每节点输入缓存（与 machineNodes 同序）
        for (int i = 0; i < g.machineNodes.size(); i++) {
            CompoundTag c = new CompoundTag();
            for (java.util.Map.Entry<String, Long> e : nodeBuf(i).entrySet()) c.putLong(e.getKey(), e.getValue());
            nbl.add(c);
        }
        nbt.put("nodeBufs", nbl);
    }

    /** m474 旧档自愈（m469 healEnds 谱系）：本世代 m457 起把画布坐标误写在节点栈的 {@code xc/yc}
     *  键上，而 {@code xc} 在 NodeTags 谱系里是 m159 <b>抽取节点累计抽取量</b>（long）——同键异义。
     *  两个后果：①抽取节点卡面"已抽取"读的是自己的画布 X 坐标；②⑤c3 抽取泵一写累计就把节点弹飞。
     *  另外蓝本坐标键是 {@code nx/ny}，键不同名还破 m443 DFU 红利（存档升 1.21.1 坐标全丢回默认位）。
     *  <p>自愈规矩：只在<b>有旧键且无新键</b>时搬（新键在场=已是新档，一个字不动），搬完删旧键；
     *  本世代此前从未写过抽取累计，故旧 xc 一定是坐标，搬迁无歧义。读档一次性，不落 tick 热路径。 */
    private void healNodeCoordKeys() {
        int healed = 0;
        for (ItemStack s : g.machineNodes) {
            CompoundTag t = s.getTag();
            if (t == null) continue;
            boolean hadOld = false;
            if (t.contains("xc")) {
                if (!t.contains("nx")) t.putInt("nx", t.getInt("xc"));
                t.remove("xc");
                hadOld = true;
            }
            if (t.contains("yc")) {
                if (!t.contains("ny")) t.putInt("ny", t.getInt("yc"));
                t.remove("yc");
                hadOld = true;
            }
            if (hadOld) healed++;
        }
        if (healed > 0) {
            LOGGER.info("结构核心 {} 旧档自愈：{} 个节点的画布坐标键 xc/yc 已迁为 nx/ny（m474 键位归位）", worldPosition, healed);
            setChanged();
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        g.readRenderNbt(nbt, null, MERGED_IDS, () -> { }); // 拓扑翻代消费方随 C2-④ 接 bumpTopo
        healNodeCoordKeys(); // m474 旧档自愈：画布坐标键 xc/yc → nx/ny（须在 readRenderNbt 之后、任何消费方之前）
        // m471：渲染子集必须先读——下面按 machineNodes.size() 对齐补位（蓝本 m275 同序注）
        internalBuffer.clear();
        int dropped = 0; // m273 口径：写路径 left<=0 即 remove，零/负值从不合法落盘；负数毒化算术且能绕封顶
        CompoundTag buf = nbt.getCompound("internalBuffer");
        for (String k : buf.getAllKeys()) {
            long v = buf.getLong(k);
            if (!k.isEmpty() && v > 0) internalBuffer.put(k, v); else dropped++;
        }
        nodeBufs.clear();
        ListTag nbl = nbt.getList("nodeBufs", Tag.TAG_COMPOUND);
        for (int i = 0; i < g.machineNodes.size(); i++) {
            java.util.Map<String, Long> m = new java.util.HashMap<>();
            if (i < nbl.size()) {
                CompoundTag c = nbl.getCompound(i);
                for (String k : c.getAllKeys()) {
                    long v = c.getLong(k);
                    if (!k.isEmpty() && v > 0) m.put(k, v); else dropped++;
                }
            }
            nodeBufs.add(m); // 老档无此键=全空缓存（无损；本世代自 m454 起就没有史前在途物品）
        }
        if (dropped > 0) LOGGER.warn("结构核心 {} 在途缓存读入丢弃 {} 条非法条目（空键或非正计数）", worldPosition, dropped);
    }

    // ===== m481 路由域跨代行为契约探针（纯加法：四行转发，不改任何现有方法体）=====
    // 与主线跑同一套断言（xplat RouteDomainAssertions），判官只此一份。

    @Override
    public boolean probeAccepts(Object level, int target, String id) {
        return accepts((net.minecraft.world.level.Level) level, target, id);
    }

    @Override
    public boolean probeChainWants(Object level, int from, String id) {
        return chainWants((net.minecraft.world.level.Level) level, from, id, 0, new java.util.HashSet<>());
    }

    @Override
    public boolean probeSensorOpen(Object level, int i) {
        return sensorOpen((net.minecraft.world.level.Level) level, i);
    }

    @Override
    public boolean probeExtractorLive(Object level, int i, ItemStack st) {
        return extractorLive((net.minecraft.world.level.Level) level, i, st);
    }

    // ===== m486（真移植·C 阶段主刀）：路由脑判定层已下沉共用（xplat node/RouteBrain）=====
    // 原来这里是六个**按主线重写的判定函数**（accepts/chainWants/chainEndsInTrash/sensorOpen/
    // extractorLive/allGatesClosed，共 157 行）。整段删除，改为转发到与主线**同一份**代码。
    // 行为由 m481 的路由域跨代契约压着（RouteDomainAssertions 六条成对判定）。
    private final com.sdzjz.node.RouteBrain.Host brainHost = new com.sdzjz.node.RouteBrain.Host() {
        @Override public int nodeCount() { return g.machineNodes.size(); }
        @Override public ItemStack nodeStack(int i) { return g.machineNodes.get(i); }
        @Override public long sensorObserved(int i, String id) {
            StorageCore120 sc = supplyTarget(level, i); // 世代取舍：本世代无「默认主存储」，未连供料线按 0 计
            return sc == null ? 0L : sc.count(id);
        }
    };

    /** 本节点的出线目标数组（主线 m355 已数组化，本世代按需现算）。 */
    private int[] outTargetsOf(int i) {
        int n = 0;
        for (int[] c : g.connections) if (c[0] == i) n++;
        int[] out = new int[n];
        int k = 0;
        for (int[] c : g.connections) if (c[0] == i) out[k++] = c[1];
        return out;
    }

    private int[][] outTAll() {
        int n = g.machineNodes.size();
        int[][] t = new int[n][];
        for (int i = 0; i < n; i++) t[i] = outTargetsOf(i);
        return t;
    }

    private boolean accepts(net.minecraft.world.level.Level world, int target, String id) {
        return com.sdzjz.node.RouteBrain.accepts(brainHost, world, target, id);
    }

    boolean chainWants(net.minecraft.world.level.Level world, int i, String id, int depth, java.util.Set<Integer> visited) {
        return com.sdzjz.node.RouteBrain.chainWants(brainHost, world, i, id, depth, visited, outTAll(), new java.util.HashMap<>());
    }

    private boolean chainEndsInTrash(net.minecraft.world.level.Level world, int i, String id, int depth,
            java.util.Set<Integer> visited) {
        return com.sdzjz.node.RouteBrain.chainEndsInTrash(brainHost, world, i, id, depth, visited, outTAll());
    }

    boolean sensorOpen(net.minecraft.world.level.Level world, int i) {
        return com.sdzjz.node.RouteBrain.sensorOpen(brainHost, world, i);
    }

    boolean extractorLive(net.minecraft.world.level.Level world, int i, ItemStack st) {
        return com.sdzjz.node.RouteBrain.extractorLive(brainHost, world, i, st);
    }

    private boolean allGatesClosed(net.minecraft.world.level.Level world, int from) {
        return com.sdzjz.node.RouteBrain.allGatesClosed(brainHost, world, outTargetsOf(from));
    }

}
