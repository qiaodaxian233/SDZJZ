package com.sdzjz.block;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import com.sdzjz.screen.StructureCoreScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import com.sdzjz.item.MachineItem;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.machine.MachineDef;
import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.machine.MachineXp;
import com.sdzjz.machine.MobDrops;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Containers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 结构核心方块实体。库存布局：
 *  0..7   机器槽（放刷线机）
 *  8..10  升级槽（速度/数量/并发）
 *  11..18 输出缓存
 * 运行时：开机后按周期让机器免费产出（消耗对齐原版，刷线机=农场类不吃料），
 * 速度升级缩短周期、数量升级放大单次产量、并发升级提高同时运行的机器数；
 * 产物进输出缓存，尝试送入面板/存储/箱子；全都连不到时从顶面喷射掉落物（m114，节流 1 组/10t），缓存满则暂停。
 */
public class StructureCoreBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Container,
        com.sdzjz.node.RouteBrainProbe { // m481 路由域跨代契约探针（四口转发，纯加法）

    public static final int MACHINE_START = 0, MACHINE_SLOTS = 8;
    public static final int UPGRADE_START = 8, UPGRADE_SLOTS = 3;
    public static final int OUTPUT_START = 11, OUTPUT_SLOTS = 8;
    public static final int SIZE = MACHINE_SLOTS + UPGRADE_SLOTS + OUTPUT_SLOTS; // 19

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    /** m430 mA1：渲染子集 12+1 字段搬家进状态对象（清单与语义见 CanvasGraphState），编解码 mA2 迁。 */
    public final com.sdzjz.node.CanvasGraphState g = new com.sdzjz.node.CanvasGraphState(() -> { setChanged(); syncToClient(); }); // m506（A5a）：分组六方法下沉共用，变更回调=落盘+推观众快照（原方法尾两行原文） // m500：原为包私有，而 m481 的 route_domain_contract 判官在 com.sdzjz.gametest 包里直接 be.g.machineNodes（9 处）——主线真编译错，冒烟筛选器抓获；与邻居 prof 同口径放开
    public transient com.sdzjz.debug.CoreProfiler.Stats prof; // m177 性能尺子(纯内存,不入NBT)
    // m179 编译执行计划：拓扑派生结构(hasOut/hasIn/outT)只在图变更时重建，普通 tick 直接复用缓存
    // ——修订号失配才编译；长度兜底防漏 bump；派生列表运行期只读(已核)所以可跨 tick 共享。
    private transient int topoRev = 1;
    private transient int planRev = 0;
    private transient boolean[] planHasOut, planHasIn;
    private transient int[][] planOutT; // m355 数组化（外部审计④轮②）：node index 本就是 0..n-1，Map 盒装下岗；null 槽=无出线（=旧 Map.get 缺席）
    private void bumpTopo() { topoRev++; }
    private final java.util.Map<String, Long> internalBuffer = new java.util.HashMap<>(); // 遗留共享池（老档迁移+节点删除回收），消耗时兜底
    private final java.util.List<java.util.Map<String, Long>> nodeBufs = new java.util.ArrayList<>(); // 每节点输入缓存：连线按边精确路由
    private static final long BUF_CAP = 200000L;

    // m90【根因修复】原为 Long.MIN_VALUE：getTime()-MIN_VALUE 长整型溢出为负 → ">=40" 永假 →
    // 端点扫描自诞生起一次都没执行过——这就是"总线/停靠栏在所有截图里都不出现"的唯一真凶。
    private long lastEndpointScan = -1000;
    // m133 强制加载：待续票端点区块清单 {区块long, miss连续未见次数} + 维度（持久化——重启后先按清单发票自举，
    // 等端点区块加载、登记表重建后清单自然刷新；miss 衰减防"重启后登记表为空时扫描把清单冲掉"）
    private final java.util.List<long[]> forceChunks = new java.util.ArrayList<>();
    private final java.util.List<String> forceDims = new java.util.ArrayList<>();
    private boolean chunkForceOn; // 瞬态：本核心当前是否登记了自身区块 FORCED
    private boolean chunkOwned;   // m268 持久化：本核心是否拥有自身区块 forced 所有权（管理员 /forceload 撞同区块=false，永不由本核心解除）
    private static final int ENDPOINT_CAP = 9; // 含常驻输出接口
    /** 常驻「输出接口」哨兵端点：连它=显式走默认自动路由（绑定>有线>无线>卫星>输出缓存）。 */
    public static final long OUTPUT_IFACE = Long.MIN_VALUE + 7;
    /** m143 机器合并（用户拍板：概念图一图=一机）：旧子机器 id → 合并机 id 的存档重映射表。 */
    private static final java.util.Map<String, String> MERGED_IDS = java.util.Map.ofEntries(
            java.util.Map.entry("sdzjz:froglight_farm", "sdzjz:wither_farm"),
            java.util.Map.entry("sdzjz:goat_horn_farm", "sdzjz:wither_farm"),
            java.util.Map.entry("sdzjz:armadillo_farm", "sdzjz:wither_farm"),
            java.util.Map.entry("sdzjz:sniffer_garden", "sdzjz:wither_farm"),
            java.util.Map.entry("sdzjz:cobweb_machine", "sdzjz:g_misc_machine"),
            java.util.Map.entry("sdzjz:spore_blossom_farm", "sdzjz:g_misc_machine"),
            java.util.Map.entry("sdzjz:budding_amethyst_farm", "sdzjz:g_misc_machine"),
            java.util.Map.entry("sdzjz:sculk_catalyst_farm", "sdzjz:sculk_line"),
            java.util.Map.entry("sdzjz:sculk_sensor_farm", "sdzjz:sculk_line"),
            java.util.Map.entry("sdzjz:sculk_shrieker_farm", "sdzjz:sculk_line"));
    private BlockPos boundPanelPos;
    private String boundPanelDim;
    public boolean running = false;
    private long ticks = 0;

    /** GUI 状态同步：0=运行 1=机器数 2=tier 3=速度Lv 4=数量Lv 5=并发Lv。 */
    private double xpPool; // 经验池（刷怪/熔炼累积，画布领取）

    public final ContainerData propertyDelegate = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> running ? 1 : 0;
                case 1 -> Math.min(32767, machineCount());          // m329：bigStacks 后节点可叠海量机器，裸发16位通道会符号扩展（m106族），显示饱和 32767
                case 2 -> tierOf();
                case 3 -> Math.min(32767, totalNodeUpgrade("spd")); // m329 同上，三升级总量饱和护通道（m230 0x7FFF 同款先例）
                case 4 -> Math.min(32767, totalNodeUpgrade("cnt"));
                case 5 -> Math.min(32767, totalNodeUpgrade("par"));
                case 6 -> (int) (((long) xpPool) & 0x7FFF);              // 经验低15位（属性按short网络同步）
                case 7 -> (int) Math.min(((long) xpPool) >> 15, 32767);  // 经验高位
                case 8 -> (int) (bufferedTotal() & 0x7FFF);              // 在途缓存低15位
                case 9 -> (int) Math.min(bufferedTotal() >> 15, 32767);  // 在途缓存高位
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { if (i == 0) running = (v != 0); }
        @Override public int getCount() { return 10; }
    };

    private int machineCount() {
        int n = 0;
        for (ItemStack st : g.machineNodes) n += st.getCount();
        return n;
    }

    private int tierOf() {
        if (level == null) return 1;
        return level.getBlockState(worldPosition).getBlock() instanceof StructureCoreBlock scb ? scb.tier : 1;
    }

    public StructureCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE_CORE_BE, pos, state);
    }

    // ================= 运行时（通用：按 MachineDef 跑任意机器）=================
    public static void tick(Level world, BlockPos pos, BlockState state, StructureCoreBlockEntity be) {
        if (world.isClientSide) return;
        if (be.prof == null) be.prof = com.sdzjz.debug.CoreProfiler.register(world.dimension().location().toString(), pos.asLong()); // m177（m365 键在版本侧折算）
        long t0 = System.nanoTime();
        try {
            tickInner(world, pos, state, be);
        } finally {
            com.sdzjz.debug.CoreProfiler.record(be.prof, be.g.machineNodes.size(), be.g.connections.size(), be.running, System.nanoTime() - t0);
        }
    }

    private static void tickInner(Level world, BlockPos pos, BlockState state, StructureCoreBlockEntity be) {
        long __ph = System.nanoTime(); // m321 阶段计时游标（四段边界各 1 次 nanoTime，常开可忽略）
        be.recipesThisTick = 0; // m270 全核tick周期预算复位（cyclesThisTick 共享额度）
        // m339 经验池公平层（作者实锤：两台吃经验的机器都拉满，下标靠前的每拍把池里刚攒的经验吃光，
        // 第二台永远等不到 ≥单价 的余额=饿死。m302 饥饿名单管核心之间，这里下沉到**同核节点之间**，
        // 同款方案①：饿着的记名，下一拍全池礼让给名单，直到它吃上为止；先到先得只在无人挨饿时成立）。
        if (SdzjzConfig.get().xpFairShare) {
            be.xpStarved.removeIf(ix -> ix >= be.g.machineNodes.size() || !be.xpConsumerReady(ix)); // 名单保洁：拆机/暂停/丢目标的不许占坑堵池
            be.xpReserveActive = !be.xpStarved.isEmpty();
        } else {
            be.xpStarved.clear();
            be.xpReserveActive = false;
        }
        be.flushCanvasSnapshot(world); // m275：上一拍标脏的渲染快照在此聚合、定向发观众（每 tick 至多 1 份/核心）
        // m218c 多核心错峰：周期性大活（ends包/区块票/拉料拍/端点扫描）按 pos 哈希移相——频率逐核不变，
        // 只是不同核心不再挤同一全局 tick（m181 给 m88 兜底同步用过的同款药方，推广到其余四拍）。
        int ph = SdzjzConfig.get().coreTickStagger ? pos.hashCode() : 0;
        long wt = world.getGameTime() + ph;
        // m218c 端点扫描错峰（回查自纠：初版"每次回拨相位"是复利——last=now-p 让下次在 40-p 就触发，
        // 周期被永久压成 40-p 反而提频。改日历拍：稳态严格 40t、相位由 wt 哈希天然错开；-1000 哨兵=
        // 刚加载首扫即时，画布随时能看到接口的原语义不变，首扫后至多 39t 内并入日历拍）。
        // m348 停机降频（外部审计"idle 核心 tick 头维护段照跑"）：停机+无观众=扫描纯白烧（消费面
        // 三路全闸后：生产路由在 running 闸内/观众链路有登记表/续票 want=running），降 40t→200t
        // 慢拍保底自愈（200%40=0，日历拍切换无缝不丢拍）；开机/开画布两转变沿哨兵强刷零陈旧窗。
        boolean epFull = be.running || !be.canvasViewers.isEmpty(); // flush 在上方已保洁，此表无陈员
        if (be.lastEndpointScan <= -1000
                || Math.floorMod(wt, (epFull || !SdzjzConfig.get().coreIdleScanRelief) ? 40 : 200) == 0) {
            be.lastEndpointScan = world.getGameTime();
            be.scanStorageEndpoints(world, pos);
        }
        // m88 兜底：每10秒强制同步（治"changed判否漏同步"的一切边角）。m181 瘦身两刀：
        // ① pos 哈希错峰——多核心不再同一全局 tick 齐发；② 无人看画布不发——事件同步照旧，
        // 开画布首帧鲜度由 createMenu 即时强刷保证，观众在场后 ≤10s 内兜底节奏恢复，最坏陈旧窗与旧行为等长。
        // m276 起本行=标脏→flushCanvasSnapshot 升版本重发渲染快照给观众（自愈兜底防标脏遗漏类 bug 永久失同步）。
        if (Math.floorMod(world.getGameTime() + pos.hashCode(), 200) == 0 && be.hasCanvasViewer(world)) be.syncToClient();
        // m115 过载保护：平均 tick >45ms 全线自动暂停（<40ms 恢复，滞回防抖）；>60ms 清理本核心喷出的掉落物
        // m348 两修：①基准 be.ticks→日历拍（ticks 在 running 闸后才自增，停机冻结——冻在 %20==0 上
        // 就每 tick 采样、服务器 >60ms 时每 tick 扫实体清扫）；②补 running 闸（看门狗管的是本核心
        // 机器产出，停机=无产出无可保护；重开机 ≤20t 内重采样自校正，lagPause 陈值期间无人消费）。
        if (be.running && Math.floorMod(wt, 20) == 0 && world instanceof net.minecraft.server.level.ServerLevel sw115) {
            float ms = sw115.getServer().getCurrentSmoothedTickTime();
            boolean was = be.lagPause;
            if (ms > 45f) be.lagPause = true; else if (ms < 40f) be.lagPause = false;
            if (be.lagPause && !was) be.warnNearby(world, "『生电终结者』服务器过载(平均 " + String.format("%.0f", ms)
                    + "ms/tick)，本核心机器已自动暂停，恢复流畅后自动续跑");
            if (ms > 60f) be.cleanupEjected(sw115);
        }
        // m89：端点+总线库存 直发正在看画布的玩家（BE同步链实机不生效的最终修复——走已被证明可靠的包通道）
        if (Math.floorMod(wt, 40) == 0 && world instanceof net.minecraft.server.level.ServerLevel sw) { // m218c 错峰
            com.sdzjz.net.CanvasEndsPayload pk = null;
            for (var itv = be.canvasViewers.iterator(); itv.hasNext(); ) { // m344 查登记表不扫全服（失配销号）
                net.minecraft.server.level.ServerPlayer sp = itv.next();
                if (!(sp.containerMenu instanceof com.sdzjz.screen.StructureCoreScreenHandler h)
                        || !pos.equals(h.blockPos())) { itv.remove(); be.snapshotSent.remove(sp.getUUID()); continue; }
                if (pk == null) pk = be.buildEndsPayload(pos);
                com.sdzjz.net.Net.toPlayer(sp, pk);
                com.sdzjz.net.Net.toPlayer(sp, be.buildHomesPayload(pos)); // m265 放置落位姊妹包（同拍同通道，只含已放置项通常极小）
                if (be.prof != null) { be.prof.endsPackets++; be.prof.endsEntries += pk.endPos().size(); } // m177
            }
        }
        { long __n = System.nanoTime(); com.sdzjz.debug.CoreProfiler.phase(com.sdzjz.debug.CoreProfiler.PH_MAINT, __n - __ph); __ph = __n; } // m321
        // m133 强制加载：开机+配置开 → 钉住自身区块(FORCED,持久化,重启自恢复) + 每100t给端点区块续有期票；
        // 停机/配置关 → 解除；孤儿 forced（重启遗留：停机核心落盘前没来得及解除）每100t回收一次。
        if (Math.floorMod(wt, 20) == 0 && world instanceof net.minecraft.server.level.ServerLevel swf) { // m218c 错峰（内层%100同偏移，嵌套节奏不变）
            boolean want = be.running && SdzjzConfig.get().coreChunkLoading;
            if (want != be.chunkForceOn) {
                if (want) { be.chunkOwned = CoreChunkLoading.force(swf, pos, be.chunkOwned); be.setChanged(); } // m268 传入既有所有权,重启后保持不误判
                else CoreChunkLoading.release(swf, pos, be.chunkOwned);
                be.chunkForceOn = want;
            }
            if (Math.floorMod(wt, 100) == 0) {
                if (want) {
                    be.refreshForceChunks(world);
                    be.renewEndpointTickets(swf);
                } else if (swf.getForcedChunks().contains(new net.minecraft.world.level.ChunkPos(pos).toLong())) {
                    CoreChunkLoading.reclaimOrphan(swf, pos, be.chunkOwned); // m268 孤儿回收凭核心持久化的所有权判定，管理员 /forceload 永不误伤
                }
            }
        }
        com.sdzjz.debug.CoreProfiler.phase(com.sdzjz.debug.CoreProfiler.PH_TICKET, System.nanoTime() - __ph); // m321
        if (!be.running) return;

        int tier = (state.getBlock() instanceof StructureCoreBlock scb) ? scb.tier : 1;
        SdzjzConfig cfg = SdzjzConfig.get();
        be.ticks++;
        if (be.ticks - be.prodWinStart >= 1200) { // m86 实测产量：每分钟滚动
            be.g.prodPerMin = be.prodWin;
            be.prodWin = 0;
            be.prodWinStart = be.ticks;
            be.setChanged();
            be.syncToClient();
        }
        int nSize = be.g.machineNodes.size();
        if (nSize == 0) return;

        // 连线拓扑（按边精确路由）：产物只流向出线指向的目标节点缓存；有入线的消耗机吃自己的缓存
        // m179 编译执行计划：只在 topoRev 变更(或长度兜底失配)时重建，其余 tick 零分配直用缓存
        if (be.planRev != be.topoRev || be.planHasOut == null || be.planHasOut.length != nSize) {
            boolean[] cHasOut = new boolean[nSize];
            boolean[] cHasIn = new boolean[nSize];
            int[][] cOutT = new int[nSize][]; // m355 数组化：两趟=计数+按 connections 原序填充（与旧 List.add 序逐位一致）
            int[] cCnt = new int[nSize];
            for (int[] c : be.connections())
                if (c[0] >= 0 && c[0] < nSize && c[1] >= 0 && c[1] < nSize) cCnt[c[0]]++;
            for (int k = 0; k < nSize; k++) if (cCnt[k] > 0) cOutT[k] = new int[cCnt[k]];
            java.util.Arrays.fill(cCnt, 0);
            for (int[] c : be.connections()) {
                if (c[0] >= 0 && c[0] < nSize && c[1] >= 0 && c[1] < nSize) {
                    cHasOut[c[0]] = true;
                    cHasIn[c[1]] = true;
                    cOutT[c[0]][cCnt[c[0]]++] = c[1];
                }
            }
            be.planHasOut = cHasOut; be.planHasIn = cHasIn; be.planOutT = cOutT;
            be.planRev = be.topoRev;
            if (be.prof != null) be.prof.planCompiles++; // m179 尺子：编译次数（稳态应≈0增长）
        }
        boolean[] hasOut = be.planHasOut;
        boolean[] hasIn = be.planHasIn;
        int[][] outT = be.planOutT; // m355 热路径 outT[i] 直取

        // m92：逻辑节点供料拉取·链式需求传播（连接系统补完）——任何逻辑节点(过滤/开关/传感/分配)接了
        // "存储→自己"的供料边，都按「自身放行规则 ∩ 下游机器真实需求」拉料。遍历的是仓库类型清单（有限），
        // 熔炉需求=可熔炼表、合成机需求=目标配方材料、消耗机需求=定义 inputs；支持逻辑节点串联（深度8+防环）。
        long __ps = System.nanoTime(); // m321 供料拍起点
        if (Math.floorMod(be.ticks + ph, 5) == 0) { // m116：20t→5t 与逻辑节点转发同拍；m218c 多核心移相（此前 64/20t=64/秒天花板，用户熔炉组升到 50/50/54 也只吃到 100/秒）
            be.crafterNeedsScratch.clear(); // m350 外层表复用（值集来自 planner 缓存/常量，本就不逐拍配）
            java.util.Map<Integer, java.util.Set<String>> crafterNeeds = be.crafterNeedsScratch;
            for (int i = 0; i < nSize; i++) {
                ItemStack stL = be.g.machineNodes.get(i);
                if (!(com.sdzjz.node.NodeTags.isFilter(stL) || com.sdzjz.node.NodeTags.isSwitch(stL) || com.sdzjz.node.NodeTags.isSensor(stL) || com.sdzjz.node.NodeTags.isDistributor(stL) || com.sdzjz.node.NodeTags.isExtractor(stL))) continue;
                boolean pump = com.sdzjz.node.NodeTags.isExtractor(stL); // m154 抽取节点=主动泵
                if (pump && !be.extractorLive(world, i, stL)) continue; // m160 手动关或感应未放行=不抽
                boolean pumpAll = pump && be.depositFor(world, i) != null; // m157 有定向存储出线=搬仓，全抽
                com.sdzjz.machine.StorageAccess sup = be.supplyFor(world, i);
                if (sup == null) continue;
                java.util.Map<String, Long> ownL = be.nodeBuf(i);
                long pumpRate = 0, pumped = 0; // m159 泵速率=抽取量挡位×(1+数量升级)，缓存上限随速率放宽
                long bufCapL = 4096;
                if (pump) {
                    pumpRate = com.sdzjz.node.NodeTags.extractorRate(stL) * (1 + be.nodeCount(stL));
                    // m163a：撤 BUF_CAP 钳位——新高挡(32768/262144)×升级后 速率×2 远超 20 万，钳住就回到
                    // m159 修过的"速率>缓存卡喉"。泵缓存只是 id→long 计数不占实存，直接放到双周期余量。
                    bufCapL = Math.max(4096, pumpRate * 2);
                }
                final int dnP = be.fillDrain(sup.storeView()); // m350 转存 scratch：撤仓账整表拷贝（旧只用键；withdraw 当场实扣防聚合视图虚账）
                for (int dk = 0; dk < dnP; dk++) {
                    String id = be.drainIds[dk];
                    long have = ownL.getOrDefault(id, 0L);
                    if (have >= bufCapL) continue; // m116 每种封顶（泵按速率放宽）：链式需求门控仍在，在途量经 8/9 号属性可见
                    if (!pump && !be.chainWants(world, i, id, 0, be.wantsScratchCleared(), outT, crafterNeeds)) continue; // m218d scratch复用
                    if (pump && !com.sdzjz.node.NodeTags.machineFilterAllows(stL, id)) continue; // m160 抽取白名单：名单外碰都不碰
                    if (pump && !pumpAll) {
                        // m157（用户实测：猪人塔/幽匿线产物"消失"）：m154 的无条件抽把全网络吸进
                        // 缓存囤着失踪（每种4096）——改为"没有去处的不抽"：出线机器目标当下肯收
                        // （过滤白名单在 accepts 里生效→只抽名单内）才抽；搬仓走上面 pumpAll。
                        boolean anyTake = false;
                        int[] tgP = outT[i];
                        if (tgP != null)
                            for (int t : tgP)
                                if (t >= 0 && t < nSize && be.accepts(world, t, id)) { anyTake = true; break; }
                        if (!anyTake) continue;
                    }
                    long roomL = bufCapL - have;
                    if (pump) roomL = Math.min(roomL, pumpRate - pumped); // 泵按挡位限速
                    if (roomL <= 0) { if (pump) break; else continue; }
                    if (!be.bufTypeOk(ownL, id)) continue; // m270 类型上限：withdraw 前判——拒收=不抽，物品留仓零损失
                    int got = sup.withdraw(id, (int) Math.min(roomL, Integer.MAX_VALUE));
                    if (got > 0) { ownL.merge(id, (long) got, StorageCoreBlockEntity::satAdd); pumped += got; } // m273 饱和加法
                }
                {
                    // m155 精确账本抽取 → m158 推广到任意逻辑节点的供料边（用户新摆法：
                    // 仓→过滤(白名单)→抽取→垃圾桶 过滤打头时原来够不着精确账本，山羊角删不掉）。
                    // 授权闸不变：只在「该 id 的出线链通向垃圾桶」（chainEndsInTrash 尊重过滤
                    // 白名单/开关/抽取启停）时才抽——反正是去销毁，抹组件无损语义；
                    // 顺带收益：链上有关着的抽取节点=闸断不抽，抽取节点成了销毁线的启停阀。
                    java.util.List<StorageCoreBlockEntity> banksP = new java.util.ArrayList<>();
                    if (sup instanceof StorageCoreBlockEntity cp) banksP.add(cp);
                    else if (sup instanceof DataPanelBlockEntity pp)
                        banksP.addAll(pp.coresView()); // m218b：走 m108c 的 40t 缓存——此前每逻辑节点每5t裸BFS(4096上限逐格getBlockEntity)，绕开缓存是tick大户
                    for (StorageCoreBlockEntity bank : banksP) {
                        for (ItemStack t : new java.util.ArrayList<>(bank.exactTemplates())) {
                            String idE = BuiltInRegistries.ITEM.getKey(t.getItem()).toString();
                            long haveE = ownL.getOrDefault(idE, 0L);
                            if (haveE >= bufCapL) continue; // m163a：原硬编码 4096 没跟 bufCapL 统一——泵开高挡后精确支路先被卡死
                            if (com.sdzjz.node.NodeTags.isExtractor(stL) && !com.sdzjz.node.NodeTags.machineFilterAllows(stL, idE)) continue; // m160
                            if (!be.bufTypeOk(ownL, idE)) continue; // m270 类型上限：withdrawExact 前判，拒收=不抽零损失
                            if (!be.chainEndsInTrash(world, i, idE, 0, be.trashScratchCleared(), outT)) continue; // m218d scratch复用
                            long roomE = bufCapL - haveE;
                            if (pump) roomE = Math.min(roomE, pumpRate - pumped);
                            if (roomE <= 0) break;
                            ItemStack tpl = t.copyWithCount(1); // withdrawExact 可能移除模板，先复制
                            int gotE = bank.withdrawExact(tpl, (int) Math.min(roomE, Integer.MAX_VALUE));
                            if (gotE > 0) { ownL.merge(idE, (long) gotE, StorageCoreBlockEntity::satAdd); pumped += gotE; } // m273 饱和加法
                        }
                    }
                }
            }
        }

        long __pp = System.nanoTime(); // m321：供料段收账+生产段起点
        com.sdzjz.debug.CoreProfiler.phase(com.sdzjz.debug.CoreProfiler.PH_SUPPLY, __pp - __ps);
        boolean produced = false;
        StorageCoreBlockEntity src = null;
        boolean srcResolved = false;

        int __tb = -1; long __tn = 0; // m354 机器类型桶：循环体 continue 众多，改在下一节点头部结上一笔账
        for (int i = 0; i < nSize; i++) {
            ItemStack st = be.g.machineNodes.get(i);
            if (com.sdzjz.debug.CoreProfiler.PHASES) {
                long __n2 = System.nanoTime();
                if (__tb >= 0) com.sdzjz.debug.CoreProfiler.sub(__tb, __n2 - __tn);
                __tb = typeBucket(st); __tn = __n2;
            }
            CompoundTag nvL = com.sdzjz.node.NodeTags.viewOf(st); // m356 一次视图三级齐读（原=三次组件查找；矩阵实测空转 654ns/节点·tick 的一份子）
            int speedLv = nvL.getInt(K_SPD);
            int countLv = nvL.getInt(K_CNT);
            int parallelLv = nvL.getInt(K_PAR);

            // m115 过载保护：全线暂停（黄灯），流畅后自动恢复
            if (be.lagPause) { be.statR(i, 2, "服务器过载，自动暂停"); continue; }
            // m110b 单节点暂停：最先判——不产不耗不攒进度（m99 教训：early-continue 必须在累积之前）
            if (com.sdzjz.node.NodeTags.nodePaused(st)) { be.statR(i, 2, "已手动暂停"); continue; }

            // 传感器闸门：该节点全部出线目标都关闸 → 整台暂停（不白产、不绕道塞存储）
            if (hasOut[i] && be.allGatesClosed(world, outT[i])) {
                be.statR(i, 2, "下游闸门全关");
                continue;
            }

            if (com.sdzjz.node.NodeTags.isDistributor(st)) {
                // 分配器节点：来料在出线目标间均分（余数轮转），没人要的走默认路由
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> ownD = be.nodeBuf(i);
                if (ownD.isEmpty()) continue;
                boolean movedD = false;
                final int dnD = be.fillDrain(ownD); ownD.clear(); // m350 整锅转存再清（旧=键拷贝逐个 remove，回灌进空表语义同旧）
                for (int dk = 0; dk < dnD; dk++) {
                    String id = be.drainIds[dk]; long amt = be.drainAmts[dk];
                    if (amt <= 0) continue;
                    be.distributeEven(world, i, outT[i], id, amt);
                    movedD = true;
                }
                if (movedD) { be.stat(i, 1); produced = true; }
            } else if (com.sdzjz.node.NodeTags.isFilter(st)) {
                // 过滤器节点：清运自己的输入缓存——放行的沿出线下游，拦下的走定向存储/默认路由
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> own = be.nodeBuf(i);
                if (own.isEmpty()) continue;
                boolean moved = false;
                final int dnF = be.fillDrain(own); own.clear(); // m350 整锅转存再清
                for (int dk = 0; dk < dnF; dk++) {
                    String id = be.drainIds[dk]; long amt = be.drainAmts[dk];
                    if (amt <= 0) continue;
                    if (com.sdzjz.node.NodeTags.filterPasses(st, id)) be.distribute(world, i, outT[i], id, amt);
                    else be.distribute(world, i, null, id, amt);
                    moved = true;
                }
                if (moved) { be.stat(i, 1); produced = true; }
            } else if (com.sdzjz.node.NodeTags.isTrash(st)) {
                // m150 垃圾桶（用户点名：过滤器收想要的，其余连进来销毁）：吞光输入缓存并累计 "tc"。
                // 终点节点无出线语义；不进中继拉料回路（防 仓→垃圾桶 手滑清空仓库——只吞推送来的）。
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> ownTr = be.nodeBuf(i);
                if (ownTr.isEmpty()) continue;
                long ate = 0;
                final int dnT = be.fillDrain(ownTr); ownTr.clear(); // m350 整锅转存再清
                for (int dk = 0; dk < dnT; dk++) if (be.drainAmts[dk] > 0) ate += be.drainAmts[dk];
                if (ate > 0) {
                    com.sdzjz.node.NodeTags.addTrashCount(st, ate); // m353 修丢写：旧代码改 copyNbt 副本从不 set 回，"已吞"是死数
                    be.setChanged();
                    be.stat(i, 1);
                    produced = true;
                }
            } else if (st.getItem() instanceof com.sdzjz.item.VoidProcessorItem) {
                // m378 虚空处理器（垃圾桶 m150 同刀+汇率结算）：吞光输入缓存，按 voidXpPerItemsEaten
                // 件=1 经验炼进本核心 xpPool（熔炼 0.1/件 产出侧同族先例；复制机/附魔工厂消费同一口池）。
                // 余数记账进位（vc）不丢；停用=持料待命不吞不退（抽取器感应暂停同律），accepts 侧同步
                // 拒收让上游走默认路由回仓。收料语义=垃圾桶同律（白名单空=连啥炼啥；直连仓不抽/
                // 逻辑节点转接授权照拉；精确件经授权链抵达=抹组件炼掉，chainEndsInTrash 已并轨）。
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> ownV = be.nodeBuf(i);
                if (ownV.isEmpty()) continue;
                if (!cfg.voidProcessorEnabled) { be.statR(i, 2, "虚空处理器已在配置停用（voidProcessorEnabled）——持料待命"); continue; }
                long ateV = 0;
                final int dnV = be.fillDrain(ownV); ownV.clear(); // m350 整锅转存再清
                for (int dk = 0; dk < dnV; dk++) if (be.drainAmts[dk] > 0) ateV += be.drainAmts[dk];
                if (ateV > 0) {
                    int rateV = Math.max(1, cfg.voidXpPerItemsEaten);
                    long carryV = com.sdzjz.node.NodeTags.voidCarry(st) + ateV; // 饱和风险：carry<rate≤int 上限，加吞吐不溢 long
                    long xpV = carryV / rateV;
                    com.sdzjz.item.VoidProcessorItem.settle(st, ateV, carryV % rateV, xpV);
                    if (xpV > 0) be.xpPool += xpV;
                    be.setChanged();
                    be.stat(i, 1);
                    produced = true;
                }
            } else if (com.sdzjz.node.NodeTags.isExtractor(st)) {
                // m154 抽取节点（用户点名：点击抽取才开始，抽走物品流动）：开=主动泵——拉料回路
                // pump 分支已无条件抽上游仓入缓存，这里把缓存沿出线推给"收的"目标；推不出去的
                // 留在缓存（背压：缓存到顶 4096 拉料自然停），绝不走默认路由——否则抽出来又
                // 存回同一个仓，每 5t 空转刷账。垃圾桶目标照 distribute 规矩两轮垫底。
                if (be.ticks % 5 != 0) continue;
                if (com.sdzjz.node.NodeTags.extractorOn(st) && !be.extractorLive(world, i, st)) {
                    be.stat(i, 2); // m160 感应暂停：持料待命不退料（条件一到自动续跑）
                    continue;
                }
                if (!com.sdzjz.node.NodeTags.extractorOn(st)) {
                    // m157 歇工退料：关机把缓存沿默认路由退回存储——用户被 m154 无条件抽吸走的
                    // 货，点一下"停止抽取"就全数找回，不留缓存黑洞。
                    java.util.Map<String, Long> ownOff = be.nodeBuf(i);
                    if (!ownOff.isEmpty()) {
                        final int dnO = be.fillDrain(ownOff); ownOff.clear(); // m350 整锅转存再清
                        for (int dk = 0; dk < dnO; dk++)
                            if (be.drainAmts[dk] > 0) be.distribute(world, i, null, be.drainIds[dk], be.drainAmts[dk]);
                        produced = true;
                    }
                    be.stat(i, 2);
                    continue;
                }
                java.util.Map<String, Long> ownX = be.nodeBuf(i);
                boolean movedX = false;
                int[] tgX = outT[i];
                final int dnX = be.fillDrain(ownX); // m350 转存不清：残量口径（尾部 put(left)/remove 原样）
                for (int dk = 0; dk < dnX; dk++) {
                    String id = be.drainIds[dk]; long amt = be.drainAmts[dk];
                    if (amt <= 0) { ownX.remove(id); continue; }
                    long left = amt;
                    if (tgX != null) {
                        for (int pass = 0; pass < 2 && left > 0; pass++) {
                            for (int t : tgX) {
                                if (left <= 0) break;
                                if (t < 0 || t >= be.g.machineNodes.size()) continue;
                                if ((pass == 0) == (com.sdzjz.node.NodeTags.isTrash(be.g.machineNodes.get(t))
                                        || be.g.machineNodes.get(t).getItem() instanceof com.sdzjz.item.VoidProcessorItem)) continue; // m378 虚空同垫底
                                if (!be.accepts(world, t, id)) continue;
                                java.util.Map<String, Long> mX = be.nodeBuf(t);
                                if (!be.bufTypeOk(mX, id)) continue; // m270 类型上限：跳过满型目标，残量留源头/走下面搬仓
                                long cur = mX.getOrDefault(id, 0L);
                                long put = Math.min(Math.max(0, BUF_CAP - cur), left);
                                if (put > 0) { mX.put(id, cur + put); left -= put; }
                            }
                        }
                    }
                    if (left > 0) { // m157 搬仓：机器目标推不完的余量走定向存储出线（明确目的地，非默认路由）
                        com.sdzjz.machine.StorageAccess depX = be.depositFor(world, i);
                        if (depX != null) {
                            be.depositOrBuffer(depX, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)),
                                    (int) Math.min(left, Integer.MAX_VALUE)));
                            left = 0;
                        }
                    }
                    if (left != amt) movedX = true;
                    if (left > 0) ownX.put(id, left); else ownX.remove(id);
                }
                be.stat(i, movedX ? 1 : 0);
                if (movedX) produced = true;
            } else if (com.sdzjz.node.NodeTags.isSwitch(st)) {
                // 开关节点：开=直通转发，关=持料不动
                if (be.ticks % 5 != 0) continue;
                if (!com.sdzjz.node.NodeTags.switchOn(st)) { be.stat(i, 2); continue; }
                java.util.Map<String, Long> ownSw = be.nodeBuf(i);
                boolean movedSw = false;
                final int dnS = be.fillDrain(ownSw); ownSw.clear(); // m350 整锅转存再清
                for (int dk = 0; dk < dnS; dk++) {
                    String id = be.drainIds[dk]; long amt = be.drainAmts[dk];
                    if (amt <= 0) continue;
                    be.distribute(world, i, outT[i], id, amt);
                    movedSw = true;
                }
                be.stat(i, movedSw ? 1 : 0);
                if (movedSw) produced = true;
            } else if (com.sdzjz.node.NodeTags.isSensor(st)) {
                // 传感器节点：开闸=直通转发自己的缓存；关闸=持料不动（缓存封顶后上游自然停）
                if (be.ticks % 5 != 0) continue;
                if (!be.sensorOpen(world, i)) { be.stat(i, 2); continue; }
                java.util.Map<String, Long> own = be.nodeBuf(i);
                boolean moved = false;
                final int dnE = be.fillDrain(own); own.clear(); // m350 整锅转存再清
                for (int dk = 0; dk < dnE; dk++) {
                    String id = be.drainIds[dk]; long amt = be.drainAmts[dk];
                    if (amt <= 0) continue;
                    be.distribute(world, i, outT[i], id, amt);
                    moved = true;
                }
                be.stat(i, moved ? 1 : 0);
                if (moved) produced = true;
            } else if (st.getItem() instanceof AutoCrafterItem) {
                // 自动合成机：按原版配方吃料出货。目标在画布上设置（节点徽章）。
                String target = com.sdzjz.node.NodeTags.craftTarget(st);
                if (target.isEmpty()) continue;
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg); // m99 工作量累积，速度永不触底
                if (cycles <= 0) continue;
                java.util.List<CraftPlanner.Plan> planC = CraftPlanner.plans(world, target); // m234 全候选（原版排前）
                if (planC.isEmpty()) continue; // 无合成配方
                CraftPlanner.Plan chosen = null; // m235 手选配方（空/失效=自动；缺料按手选那条报）
                String chosenR = com.sdzjz.node.NodeTags.craftRecipe(st);
                if (!chosenR.isEmpty()) for (CraftPlanner.Plan pp : planC) if (pp.recipeId().equals(chosenR)) { chosen = pp; break; }
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);   // m99 并发直接乘台数
                long crafts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess depositAc = hasOut[i] ? null : be.depositFor(world, i); // 提前解析：封顶只对"进内部缓存"生效
                int maxStack = BuiltInRegistries.ITEM.get(ResourceLocation.parse(target)).getDefaultMaxStackSize();
                CraftPlanner.Plan plan; // m234 都不齐回退首候选按原版材料报缺料（Exec.plan 同口径）
                CraftPlanner.Exec ex;   // m349 一次成型执行计划：快照→选配方→次数→实扣→残留 单趟（存储每去重 id 只查一次，销"重复算三遍"）
                final long baseCrafts = crafts;
                final boolean capSlots = !hasOut[i] && depositAc == null; // m99 只在无存储时封顶（剑/图腾等max=1时防白扣）
                final int msC = maxStack;
                final java.util.function.ToLongFunction<CraftPlanner.Plan> capOf = // 封顶吃中选配方的 resultCount，故为函数随选随算
                        p2 -> capSlots ? Math.min(baseCrafts, ((long) msC * OUTPUT_SLOTS) / Math.max(1, p2.resultCount())) : baseCrafts;
                if (hasIn[i]) {
                    final int fi = i;
                    final com.sdzjz.machine.StorageAccess expC = be.topUpSource(world, i); // m340 显式供料线补足
                    ex = CraftPlanner.exec(planC, chosen, capOf, k -> be.dualCount(fi, expC, k)); // m235 手选/m340 合计/m343 候选组口径全原样
                    plan = ex.plan();
                    if (ex.crafts() <= 0) { be.statR(i, 3, be.whyMissingPlan(plan, k -> be.dualCount(fi, expC, k), expC != null ? "缓存+供料" : "缓存")); continue; }
                    for (var en : ex.taken().entrySet()) be.dualWithdraw(fi, expC, en.getKey(), en.getValue()); // 实扣序=贪心组序
                } else {
                    com.sdzjz.machine.StorageAccess supply = be.supplyFor(world, i);   // 存储→机器 定向供料连线优先
                    if (supply == null) {
                        if (!srcResolved) {
                            src = be.resolveInputSource(world, pos);
                            srcResolved = true;
                        }
                        supply = src;
                    }
                    if (supply == null) { be.statR(i, 3, "未接存储/供料线"); continue; }
                    final com.sdzjz.machine.StorageAccess sup = supply;
                    ex = CraftPlanner.exec(planC, chosen, capOf, sup::count); // m235
                    plan = ex.plan();
                    if (ex.crafts() <= 0) { be.statR(i, 3, be.whyMissingPlan(plan, sup::count, "仓")); continue; }
                    for (var en : ex.taken().entrySet()) sup.withdraw(en.getKey(), (int) Math.min(Integer.MAX_VALUE, en.getValue()));
                }
                crafts = ex.crafts();
                be.stat(i, 1);
                int total = (int) Math.min(Integer.MAX_VALUE, crafts * plan.resultCount());
                be.prodTally(total); // m86 实测产量
                if (hasOut[i]) be.distribute(world, i, outT[i], target, total);
                else if (depositAc != null) be.depositOrBuffer(depositAc, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(target)), total));
                else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(target)), total));
                for (var en : ex.remainders().entrySet()) { // 容器残留（桶等）返还——m343 按实际消耗物结算（值已是总量，m349 随 Exec 单趟出）
                    int rc = (int) Math.min(64L * OUTPUT_SLOTS, en.getValue());
                    if (hasOut[i]) be.distribute(world, i, outT[i], en.getKey(), rc);
                    else if (depositAc != null) be.depositOrBuffer(depositAc, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(en.getKey())), rc));
                    else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(en.getKey())), rc));
                }
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem) {
                // 酿造塔（m131b）：按原版酿造链吃料出药水，目标在画布节点徽章选择。
                // 产物带 POTION_CONTENTS——不走 distribute/内部缓存（id 账本会抹组件），
                // 出线一律无视，只走 存储入库（m130 精确账本自动分流）或输出缓存（addOutput 已保组件）。
                String target = com.sdzjz.node.NodeTags.craftTarget(st);
                if (target.isEmpty()) continue;
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                com.sdzjz.machine.BrewPlanner.Plan plan = com.sdzjz.machine.BrewPlanner.plan(world, target);
                if (plan == null) continue; // 目标串非法/酿造不可达
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                long crafts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess depositBt = be.depositFor(world, i);
                if (depositBt == null)
                    crafts = Math.min(crafts, (long) OUTPUT_SLOTS / com.sdzjz.machine.BrewPlanner.BOTTLES_PER_BATCH); // 药水 max=1，无存储时封顶防白扣
                int steps = plan.steps();
                int fuelNd = plan.needs().getOrDefault(com.sdzjz.machine.BrewPlanner.FUEL_ID, 0); // 力量药水的材料烈焰粉，与燃料两账并存
                int ops = com.sdzjz.machine.BrewPlanner.OPS_PER_FUEL;
                if (hasIn[i]) {
                    final com.sdzjz.machine.StorageAccess expB = be.topUpSource(world, i); // m340 显式供料线补足
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.dualCount(i, expB, en.getKey()) / en.getValue());
                    long fuelAvail = be.dualCount(i, expB, com.sdzjz.machine.BrewPlanner.FUEL_ID);
                    crafts = Math.min(crafts, fuelAvail * ops / ((long) fuelNd * ops + steps));
                    while (crafts > 0 && (long) fuelNd * crafts + (crafts * steps + ops - 1) / ops > fuelAvail) crafts--; // ceil 兜底
                    if (crafts <= 0) { String w178 = be.whyMissingBuf(i, plan.needs()); if (w178.startsWith("缺料（")) w178 = "缺料：烈焰粉（燃料）"; be.statR(i, 3, w178); continue; }
                    for (var en : plan.needs().entrySet())
                        be.dualWithdraw(i, expB, en.getKey(), (long) en.getValue() * crafts);
                    be.dualWithdraw(i, expB, com.sdzjz.machine.BrewPlanner.FUEL_ID, (crafts * steps + ops - 1) / ops);
                } else {
                    com.sdzjz.machine.StorageAccess supply = be.supplyFor(world, i);
                    if (supply == null) {
                        if (!srcResolved) {
                            src = be.resolveInputSource(world, pos);
                            srcResolved = true;
                        }
                        supply = src;
                    }
                    if (supply == null) { be.statR(i, 3, "未接存储/供料线"); continue; }
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, supply.count(en.getKey()) / en.getValue());
                    long fuelAvail = supply.count(com.sdzjz.machine.BrewPlanner.FUEL_ID);
                    crafts = Math.min(crafts, fuelAvail * ops / ((long) fuelNd * ops + steps));
                    while (crafts > 0 && (long) fuelNd * crafts + (crafts * steps + ops - 1) / ops > fuelAvail) crafts--;
                    if (crafts <= 0) { String w178 = be.whyMissingSup(supply, plan.needs()); if (w178.startsWith("缺料（")) w178 = "缺料：烈焰粉（燃料）"; be.statR(i, 3, w178); continue; }
                    for (var en : plan.needs().entrySet())
                        supply.withdraw(en.getKey(), (int) Math.min(Integer.MAX_VALUE, (long) en.getValue() * crafts));
                    supply.withdraw(com.sdzjz.machine.BrewPlanner.FUEL_ID, (int) Math.min(Integer.MAX_VALUE, (crafts * steps + ops - 1) / ops));
                }
                be.stat(i, 1);
                int total = (int) Math.min(Integer.MAX_VALUE, crafts * com.sdzjz.machine.BrewPlanner.BOTTLES_PER_BATCH);
                be.prodTally(total);
                ItemStack brewOut = ((ItemStack) plan.result()).copyWithCount(total); // m364 句柄拆封（Legacy=原版栈）
                if (depositBt != null) be.depositOrBuffer(depositBt, brewOut);
                else be.addOutput(brewOut);
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem) {
                // 附魔工厂（m132）：按目标附魔+等级吃 书+青金石+经验（核心经验池）出附魔书。
                // 产物带 ENCHANTMENTS 组件——与酿造塔同款出路：不走 distribute/id 账本，
                // 出线一律无视，只走 存储入库（m130 精确账本）或输出缓存（addOutput 保组件）。
                String target = com.sdzjz.node.NodeTags.craftTarget(st);
                if (target.isEmpty()) continue;
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                com.sdzjz.machine.EnchantPlanner.Plan plan = com.sdzjz.machine.EnchantPlanner.plan(world, target);
                if (plan == null) continue; // 目标串非法/附魔不存在（数据包变更等）
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                long crafts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess depositEf = be.depositFor(world, i);
                if (depositEf == null)
                    crafts = Math.min(crafts, OUTPUT_SLOTS); // 附魔书 max=1，无存储时封顶防白扣
                crafts = be.xpGate(i, crafts, plan.xpCost()); // m339 经验闸+公平层：礼让期非名单节点=0
                if (crafts <= 0) { be.statR(i, 3, "经验池不足或礼让保底（本单需 " + plan.xpCost() + "，现 " + (long) be.xpPool + "；有机器挨饿时全池先喂它）"); continue; }
                if (hasIn[i]) {
                    final com.sdzjz.machine.StorageAccess expE = be.topUpSource(world, i); // m340 显式供料线补足
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.dualCount(i, expE, en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.statR(i, 3, be.whyMissingBuf(i, plan.needs())); continue; }
                    for (var en : plan.needs().entrySet())
                        be.dualWithdraw(i, expE, en.getKey(), (long) en.getValue() * crafts);
                } else {
                    com.sdzjz.machine.StorageAccess supply = be.supplyFor(world, i);
                    if (supply == null) {
                        if (!srcResolved) {
                            src = be.resolveInputSource(world, pos);
                            srcResolved = true;
                        }
                        supply = src;
                    }
                    if (supply == null) { be.statR(i, 3, "未接存储/供料线"); continue; }
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, supply.count(en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.statR(i, 3, be.whyMissingSup(supply, plan.needs())); continue; }
                    for (var en : plan.needs().entrySet())
                        supply.withdraw(en.getKey(), (int) Math.min(Integer.MAX_VALUE, (long) en.getValue() * crafts));
                }
                be.xpPool -= (double) plan.xpCost() * crafts;
                be.stat(i, 1);
                int totalEf = (int) Math.min(Integer.MAX_VALUE, crafts);
                be.prodTally(totalEf);
                ItemStack enchOut = ((ItemStack) plan.result()).copyWithCount(totalEf); // m364 句柄拆封
                if (depositEf != null) be.depositOrBuffer(depositEf, enchOut);
                else be.addOutput(enchOut);
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.CropFarmItem) {
                // 全自动农场：按所选作物产出（免费，对齐原版农场）。m93：多选≤8种，逐种产出
                java.util.List<String> cropsSel = com.sdzjz.node.NodeTags.cropList(st);
                if (cropsSel.isEmpty()) { be.stat(i, 0); continue; } // 未选作物=待机
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);    // m99 并发直接乘台数
                be.stat(i, 1);
                com.sdzjz.machine.StorageAccess depositCf = hasOut[i] ? null : be.depositFor(world, i);
                boolean cappedCf = !hasOut[i] && depositCf == null;  // m99 封顶只对"进内部缓存"生效
                java.util.List<MachineDef.Drop> allDrops = new java.util.ArrayList<>();
                for (String crop : cropsSel) {
                    java.util.List<MachineDef.Drop> cd = com.sdzjz.machine.CropFarms.get(crop);
                    if (cd != null) allDrops.addAll(cd);
                }
                long cropUnit = st.getItem() instanceof MachineItem miCf
                        ? com.sdzjz.machine.Machines.cropUnit(miCf.def().id()) : 1L; // m173 农业塔×32
                for (MachineDef.Drop d : allDrops) {
                    long sum = com.sdzjz.machine.DropRolls.rollDrops(world.getRandom(), d, cycles, countLv);
                    if (sum <= 0) continue;
                    long total = (long) running * sum * cropUnit;
                    if (cappedCf) total = Math.min(total, 64L * OUTPUT_SLOTS);
                    be.prodTally(total); // m86 实测产量
                    if (hasOut[i]) be.distribute(world, i, outT[i], d.item(), total);
                    else if (depositCf != null) be.depositOrBuffer(depositCf, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) Math.min(total, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) total));
                    produced = true;
                }
            } else if (st.getItem() instanceof MachineItem miu && com.sdzjz.machine.Machines.smelterFamily(miu.def().id())) { // m173 熔炉族
                // 万能熔炉：接什么烧什么（原版熔炼配方表）。有入线吃内部缓存，否则吃定向供料/存储网络。
                int cycles = be.cyclesThisTick(i, miu.def().baseIntervalTicks(), speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier); // m99 并发直接乘台数
                com.sdzjz.machine.StorageAccess depositSm = hasOut[i] ? null : be.depositFor(world, i);
                long capacity = (long) running * 64L * (1 + countLv) * cycles
                        * com.sdzjz.machine.Machines.smelterUnit(miu.def().id()); // 每周期一组×并行×(1+数量)×周期数×熔炉族倍数(m173 工程款×108)
                if (!hasOut[i] && depositSm == null) capacity = Math.min(capacity, 64L * OUTPUT_SLOTS); // 无存储时按缓存封顶防白扣
                long done = 0;
                if (hasIn[i]) {
                    java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(be.nodeBuf(i).keySet());
                    keys.addAll(be.internalBuffer.keySet());
                    for (String id : keys) {
                        if (done >= capacity) break;
                        Object[] out = com.sdzjz.machine.SmeltPlanner.resultOf(world, id);
                        if (out == null) continue;
                        if (!com.sdzjz.node.NodeTags.machineFilterAllows(st, id)) continue; // m149 选了烧什么就只烧什么
                        long take = Math.min(be.bufCountFor(i, id), capacity - done);
                        if (take <= 0) continue;
                        be.bufWithdrawFor(i, id, take);
                        long give = take * (int) out[1];
                        if (hasOut[i]) be.distribute(world, i, outT[i], (String) out[0], give);
                        else if (depositSm != null) be.depositOrBuffer(depositSm, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) out[0])), (int) Math.min(give, Integer.MAX_VALUE)));
                        else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) out[0])), (int) Math.min(give, 64L * OUTPUT_SLOTS)));
                        done += take;
                        be.prodTally(give); // m124 补漏：入线喂料路径此前不计产量——用户熔炉狂产14.2M碎片而实测只显农场的1311/分
                    }
                } else {
                    // 万能熔炉必须显式接线（机器入线 或 存储→机器 定向供料线）才取料：
                    // 不做全局网络兜底，防止把玩家存着的原木/圆石/粗矿悄悄全烧了。
                    com.sdzjz.machine.StorageAccess supply = be.supplyFor(world, i);
                    if (supply == null) { be.statR(i, 3, "未接存储/供料线"); continue; }
                    final int dnM = be.fillDrain(supply.storeView()); // m350 转存 scratch：撤仓账整表拷贝（withdraw 当场实扣防聚合视图虚账）
                    for (int dk = 0; dk < dnM; dk++) {
                        if (done >= capacity) break;
                        String idM = be.drainIds[dk];
                        Object[] out = com.sdzjz.machine.SmeltPlanner.resultOf(world, idM);
                        if (out == null) continue;
                        if (!com.sdzjz.node.NodeTags.machineFilterAllows(st, idM)) continue; // m149
                        long take = Math.min(be.drainAmts[dk], capacity - done);
                        if (take <= 0) continue;
                        int got = supply.withdraw(idM, (int) Math.min(take, Integer.MAX_VALUE));
                        if (got <= 0) continue;
                        long give = (long) got * (int) out[1];
                        if (hasOut[i]) be.distribute(world, i, outT[i], (String) out[0], give);
                        else if (depositSm != null) be.depositOrBuffer(depositSm, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) out[0])), (int) Math.min(give, Integer.MAX_VALUE)));
                        else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) out[0])), (int) Math.min(give, 64L * OUTPUT_SLOTS)));
                        done += got;
                        be.prodTally(give); // m86 实测产量
                    }
                }
                if (done > 0) {
                    be.xpPool += 0.1 * done; // 熔炼经验：0.1/件（近似原版均值，DEVLOG 有记）
                    produced = true;
                    be.stat(i, 1);
                } else {
                    be.statR(i, 3, "无可烧材料（上游/仓库没有可熔炼项）"); // 本周期没料可烧
                }
            } else if (st.getItem() instanceof MachineItem gr && "grindstone_recycler".equals(gr.def().id())) {
                // m139 砂轮祛魔（缺口#4 另一半收官）：扫源仓精确账本里的附魔书→磨成裸书回原仓+
                // 经验进核心池。V1 只收附魔书不碰装备（防误吞玩家神装）；纯诅咒书不收（原版砂轮
                // 不祛诅咒，磨了还是原书=死循环）；回收值=Σ各附魔 getMinPower(等级)（原版砂轮同源），
                // 逐附魔封顶工厂成本 80%（B×级×25×0.8）——防第三方附魔 minPower 异常高形成
                // 「工厂造书→砂轮回收」经验泵。供料边（存储→机器）选磨哪个仓，没连线走默认源。
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                com.sdzjz.machine.StorageAccess accG = be.supplyFor(world, i);
                if (accG == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accG = src;
                }
                if (accG == null) { be.statR(i, 3, "未接存储网络"); continue; } // 无网络=红灯（这台离了仓没意义）
                java.util.List<StorageCoreBlockEntity> banks = new java.util.ArrayList<>();
                if (accG instanceof StorageCoreBlockEntity c1) banks.add(c1);
                else if (accG instanceof DataPanelBlockEntity pn)
                    banks.addAll(StorageCoreBlockEntity.connectedCores(world, pn.getBlockPos()));
                long budget = (long) running * (1 + countLv) * cycles; // 本tick磨几本
                boolean ground = false;
                long groundN = 0;
                for (StorageCoreBlockEntity bank : banks) {
                    if (budget <= 0) break;
                    java.util.List<ItemStack> tpls = bank.exactTemplates();
                    for (int k = tpls.size() - 1; k >= 0 && budget > 0; k--) { // 倒序：取空会删条目
                        ItemStack t = tpls.get(k);
                        if (!t.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) continue;
                        double per = be.grindValue(t);
                        if (per <= 0) continue; // 纯诅咒/空组件不收
                        ItemStack tpl = t.copyWithCount(1); // withdrawExact 可能移除模板，先复制
                        int take = bank.withdrawExact(tpl, (int) Math.min(budget, bank.exactCount(k)));
                        if (take <= 0) continue;
                        be.xpPool += per * take;
                        int lapPer = be.grindLapis(tpl); // m140 青金石退款：1/级（工厂成本3/级的33%，有损回收无泵）
                        if (lapPer > 0) {
                            ItemStack lap = new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI,
                                    (int) Math.min((long) lapPer * take, Integer.MAX_VALUE));
                            accG.deposit(lap);
                            if (!lap.isEmpty()) be.addOutput(lap);
                        }
                        ItemStack books = new ItemStack(net.minecraft.world.item.Items.BOOK, take);
                        accG.deposit(books);
                        if (!books.isEmpty()) be.addOutput(books); // 仓满兜底进输出缓存不蒸发
                        budget -= take;
                        groundN += take;
                        ground = true;
                    }
                }
                be.stat(i, ground ? 1 : 0); // 没书可磨=待机不是故障
                if (ground) { be.prodTally(groundN); produced = true; }
            } else if (st.getItem() instanceof com.sdzjz.item.VillagerTraderItem) {
                // m146 村民无限交易机（用户点名：直接对接仓库和刷线机）：目标交易画布徽章选（ct 串
                // "职业|序号"，TradePlanner 解析）。供料双路照酿造塔：连线喂料（刷线机→交易机直供，
                // 走节点 inputBuf）优先，否则存储网络自取。折扣自动取共网交易所同职业已就业合同的
                // 最高档（没合同=原价不堵路——繁殖→就业→打折机→交易机全链有用）。附魔书产物照
                // 山羊角组件规矩：出线无视（distribute 按 id 记账带不动组件），精确账本或输出缓存。
                // 交易经验 4.5/次（原版 3-6 均值）进核心经验池——村民交易本就是原版经验源。
                String tgtT = com.sdzjz.node.NodeTags.craftTarget(st);
                com.sdzjz.machine.VillagerTrades.Trade t = com.sdzjz.machine.TradePlanner.trade(tgtT);
                if (t == null) { be.stat(i, 0); continue; } // 未选交易=待机（徽章"选交易"）
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                long attempts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess accT = be.supplyFor(world, i);
                if (accT == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accT = src;
                }
                // 折扣发现：供料仓集 ∩ 交易所 connectedCores
                java.util.List<StorageCoreBlockEntity> banksT = new java.util.ArrayList<>();
                if (accT instanceof StorageCoreBlockEntity cbt) banksT.add(cbt);
                else if (accT instanceof DataPanelBlockEntity pbt)
                    banksT.addAll(StorageCoreBlockEntity.connectedCores(world, pbt.getBlockPos()));
                int discT = 0;
                String profT = com.sdzjz.machine.TradePlanner.prof(tgtT);
                if (!banksT.isEmpty())
                    for (TradeCenterBlockEntity tc : TradeCenterBlockEntity.loadedIn(world)) {
                        ItemStack cc = tc.contractSlot.getItem(0);
                        if (profT.equals(TradeCenterBlockEntity.contractProf(cc)) && tc.sharesNetwork(banksT))
                            discT = Math.max(discT, TradeCenterBlockEntity.contractDiscount(cc));
                    }
                int need1 = com.sdzjz.machine.VillagerTrades.discounted(t.inCount(), discT);
                // 产出口先解析好算封顶（附魔书出线无视=山羊角同规；书 maxCount=1 封顶按格数）
                com.sdzjz.machine.StorageAccess depositT = (t.enchant() != null || !hasOut[i]) ? be.depositFor(world, i) : null;
                boolean cappedT = depositT == null && (t.enchant() != null || !hasOut[i]);
                if (cappedT) attempts = Math.min(attempts, t.enchant() != null ? OUTPUT_SLOTS
                        : Math.max(1, 64L * OUTPUT_SLOTS / Math.max(1, t.outCount())));
                if (hasIn[i]) { // 连线喂料（刷线机直供）+ m340 显式供料线补足
                    final com.sdzjz.machine.StorageAccess expV = be.topUpSource(world, i);
                    attempts = Math.min(attempts, be.dualCount(i, expV, t.inItem()) / need1);
                    if (t.in2Item() != null) attempts = Math.min(attempts, be.dualCount(i, expV, t.in2Item()) / t.in2Count());
                    if (attempts <= 0) { be.statR(i, 3, "缺料（缓存+供料线合计不足，对照徽章/工具提示）"); continue; }
                    be.dualWithdraw(i, expV, t.inItem(), (long) need1 * attempts);
                    if (t.in2Item() != null) be.dualWithdraw(i, expV, t.in2Item(), (long) t.in2Count() * attempts);
                } else {
                    if (accT == null) { be.statR(i, 3, "未接存储/供料线"); continue; } // 没连仓也没喂料
                    attempts = Math.min(attempts, accT.count(t.inItem()) / need1);
                    if (t.in2Item() != null) attempts = Math.min(attempts, accT.count(t.in2Item()) / t.in2Count());
                    if (attempts <= 0) { be.statR(i, 3, "缺料（本周期成本料不足，对照徽章/工具提示）"); continue; }
                    accT.withdraw(t.inItem(), (int) Math.min(Integer.MAX_VALUE, (long) need1 * attempts));
                    if (t.in2Item() != null) accT.withdraw(t.in2Item(), (int) Math.min(Integer.MAX_VALUE, (long) t.in2Count() * attempts));
                }
                be.stat(i, 1);
                if (t.enchant() != null) {
                    ItemStack bookT = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK,
                            (int) Math.min(attempts, Integer.MAX_VALUE));
                    var regT = world.registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                    var entryT = regT.getOrThrow(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.ENCHANTMENT, ResourceLocation.parse(t.enchant())));
                    bookT.enchant(entryT, t.enchantLv());
                    be.prodTally(attempts);
                    if (depositT != null) be.depositOrBuffer(depositT, bookT);
                    else be.addOutput(bookT); // maxCount=1 自动一格一本（山羊角同规）
                } else {
                    long totalT = attempts * t.outCount();
                    be.prodTally(totalT);
                    if (hasOut[i]) be.distribute(world, i, outT[i], t.outItem(), totalT);
                    else if (depositT != null) be.depositOrBuffer(depositT, new ItemStack(
                            BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.outItem())), (int) Math.min(totalT, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.outItem())), (int) totalT));
                }
                be.xpPool += 4.5 * attempts;
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.DuplicatorItem) {
                // m334 无限复制机：目标画布徽章选（全物品注册表+搜索）。母本制：网络里≥1件目标压阵
                // （不消耗），每件复制烧核心经验池 duplicatorXpPerItem——经验是全 MOD 终局货币
                // （附魔工厂/幽匿线同源），复制没有免费午餐。组件不复制：产物是干净 id 计数件
                // （机器组合.md 第 1 条物理不为复制机开洞）。接线五件账：tick=本分支；accepts=恒假
                // （只吃经验不吃料，经验非物品不走线——附魔工厂同注）；setNodeTarget=dupOk；
                // 徽章=通用目标图标路（isDup 并入既有条件）；chainWants=显式零需求（不吃料自然不拉料）。
                if (!cfg.duplicatorEnabled) { be.statR(i, 2, "复制机已在配置停用（duplicatorEnabled）"); continue; }
                String tgtD = com.sdzjz.node.NodeTags.craftTarget(st);
                if (!com.sdzjz.item.DuplicatorItem.validTarget(tgtD)) { be.stat(i, 0); continue; } // 未选=待机（徽章"选复制"）
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                long attempts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess accD = be.supplyFor(world, i);
                if (accD == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accD = src;
                }
                if (accD == null) { be.statR(i, 3, "未接存储网络（母本要仓压阵）"); continue; }
                if (accD.count(tgtD) < 1) { be.statR(i, 3, "网络里没有母本：先放 1 件目标物品进仓（母本不消耗）"); continue; }
                int xpEach = Math.max(1, cfg.duplicatorXpPerItem);
                attempts = be.xpGate(i, attempts, xpEach); // m339 经验闸+公平层：礼让期非名单节点=0
                if (attempts <= 0) { be.statR(i, 3, "经验池不足或礼让保底（每件需 " + xpEach + "，现 " + (long) be.xpPool + "；有机器挨饿时全池先喂它）"); continue; }
                com.sdzjz.machine.StorageAccess depositD = hasOut[i] ? null : be.depositFor(world, i);
                if (!hasOut[i] && depositD == null)
                    attempts = Math.min(attempts, 64L * OUTPUT_SLOTS); // 兜底缓存封顶（交易机同规，不蒸发不洪泛）
                be.stat(i, 1);
                be.prodTally(attempts);
                if (hasOut[i]) be.distribute(world, i, outT[i], tgtD, attempts);
                else if (depositD != null) be.depositOrBuffer(depositD, new ItemStack(
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse(tgtD)), (int) Math.min(attempts, Integer.MAX_VALUE)));
                else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(tgtD)), (int) Math.min(attempts, Integer.MAX_VALUE)));
                be.xpPool -= (double) xpEach * attempts;
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.ChunkScannerItem) {
                // m380 区块扫描器（三件套 m379 第一刀，只读侦察）：绑定/游标复用移除器 z 族键，
                // 自顶向下只读扫描出统计报告（方块总数/矿物 c:ores∪_ore 后缀∪远古残骸/容器
                // instanceof Container/类型榜封顶64溢出归#其他桶），完成拍一次性清点生物
                // （getEntitiesByClass 全高箱——实体会动，边扫边数是假账）。单本预算账
                // （读扫即工作，无移除/扫描拆账需求）+tick 硬顶 16384。接线五件账：tick=本分支；
                // accepts/chainWants=恒假（免费型尾兜，只读机器）；setNodeTarget=不适用；
                // 徽章=卡面自绘三态（未绑定/扫描中/报告摘要），Top8 明细走节点菜单信息行。
                if (!cfg.chunkScannerEnabled) { be.statR(i, 2, "区块扫描器已在配置停用（chunkScannerEnabled）"); continue; }
                if (!com.sdzjz.node.NodeTags.chunkBound(st)) { be.stat(i, 0); continue; }
                if (com.sdzjz.node.NodeTags.scanDone(st)) { be.stat(i, 0); continue; } // 报告就绪=待机（重扫走菜单/重绑）
                String dimS = world.dimension().location().toString();
                if (!dimS.equals(com.sdzjz.node.NodeTags.chunkDim(st))) { be.statR(i, 3, "绑定区块在其它维度（" + com.sdzjz.node.NodeTags.chunkDim(st) + "），换同维度核心或重绑"); continue; }
                int cxS = com.sdzjz.node.NodeTags.chunkX(st), czS = com.sdzjz.node.NodeTags.chunkZ(st);
                if (!world.getChunkSource().hasChunk(cxS, czS)) { be.statR(i, 2, "目标区块未加载·等待中（核心自带 5×5 保载：把核心放进目标区域中心即可挂机；或人在附近）"); continue; }
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                long budgetS = Math.min(16384L, (long) running * (1 + countLv) * cycles * Math.max(1, cfg.chunkScannerBlocksPerCycle));
                int yS = com.sdzjz.node.NodeTags.chunkY(st), idxS = com.sdzjz.node.NodeTags.chunkIdx(st);
                int bottomS = world.getMinBuildHeight();
                long totS = 0, oreS = 0, conS = 0;
                java.util.LinkedHashMap<String, Long> typS = new java.util.LinkedHashMap<>();
                net.minecraft.core.BlockPos.MutableBlockPos mpS = new net.minecraft.core.BlockPos.MutableBlockPos();
                while (budgetS-- > 0 && yS >= bottomS) {
                    mpS.set((cxS << 4) + (idxS >> 4), yS, (czS << 4) + (idxS & 15));
                    net.minecraft.world.level.block.state.BlockState bsS = world.getBlockState(mpS);
                    if (!bsS.isAir()) {
                        totS++;
                        String bidS = BuiltInRegistries.BLOCK.getKey(bsS.getBlock()).toString(); // 报告用方块 id（比物品形态口径准，火/传送门也入榜）
                        typS.merge(bidS, 1L, Long::sum);
                        if (bsS.is(com.sdzjz.item.ChunkScannerItem.C_ORES) || bidS.endsWith("_ore") || bidS.endsWith("ancient_debris")) oreS++;
                        if (bsS.hasBlockEntity() && world.getBlockEntity(mpS) instanceof net.minecraft.world.Container) conS++;
                    }
                    if (++idxS >= 256) { idxS = 0; yS--; be.statusDirty = true; }
                }
                boolean doneS = yS < bottomS;
                com.sdzjz.item.ChunkScannerItem.accumulate(st, Math.max(yS, bottomS), idxS, totS, oreS, conS, typS);
                if (doneS) {
                    int bxS = cxS << 4, bzS = czS << 4;
                    long entsS = world.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(bxS, world.getMinBuildHeight(), bzS, bxS + 16, world.getMaxBuildHeight(), bzS + 16),
                            e2 -> true).size();
                    com.sdzjz.item.ChunkScannerItem.finish(st, entsS);
                    be.statusDirty = true;
                    be.stat(i, 0); // 报告就绪=待机
                } else {
                    be.stat(i, 1);
                }
                be.setChanged();
            } else if (st.getItem() instanceof com.sdzjz.item.ChunkVaultItem) {
                // m381 区块储存器（三件套第二刀·第100台）：只读全量扫描→模板入 ChunkTemplateStore→
                // 产"区块数据核心"×1。模板不变量=入模板的位可付可建：空气/硬度<0/方块实体（决策2）/
                // asItem==AIR（火/传送门/纯液体一条规则全出局）不入。数据核心=组件物品**永走真栈**
                // （deposit 自动精确账本/addOutput m131b 保组件），出线 id 派发对本机不生效（会抹
                // NBT 合并同类=模板引用蒸发）。暂存器 transient：半途重启回顶重扫；库满=红灯保留
                // 暂存逐拍重试入库（不重扫不烧 CPU）。接线五件账：tick=本分支；accepts/chainWants=
                // 恒假（只读机器免费型尾兜）；setNodeTarget=不适用；徽章=卡面自绘三态。
                if (!cfg.chunkVaultEnabled) { be.statR(i, 2, "区块储存器已在配置停用（chunkVaultEnabled）"); continue; }
                if (!com.sdzjz.node.NodeTags.chunkBound(st)) { be.stat(i, 0); continue; }
                if (com.sdzjz.node.NodeTags.vaultDone(st)) { be.stat(i, 0); continue; } // 已存档=待机（重绑=新扫）
                if (!(world instanceof net.minecraft.server.level.ServerLevel swV)) continue;
                String dimV = world.dimension().location().toString();
                if (!dimV.equals(com.sdzjz.node.NodeTags.chunkDim(st))) { be.statR(i, 3, "绑定区块在其它维度（" + com.sdzjz.node.NodeTags.chunkDim(st) + "），换同维度核心或重绑"); continue; }
                int cxV = com.sdzjz.node.NodeTags.chunkX(st), czV = com.sdzjz.node.NodeTags.chunkZ(st);
                if (!world.getChunkSource().hasChunk(cxV, czV)) { be.statR(i, 2, "目标区块未加载·等待中（核心自带 5×5 保载：把核心放进目标区域中心即可挂机；或人在附近）"); continue; }
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                if (be.vaultAcc == null) be.vaultAcc = new java.util.HashMap<>();
                com.sdzjz.item.ChunkVaultItem.Acc accV = be.vaultAcc.get(i);
                if (accV != null && (accV.cx != cxV || accV.cz != czV)) { be.vaultAcc.remove(i); accV = null; } // 节点下标复用/换绑=弃旧账
                int topV = world.getMaxBuildHeight() - 1, bottomV = world.getMinBuildHeight();
                int yV = com.sdzjz.node.NodeTags.chunkY(st), idxV = com.sdzjz.node.NodeTags.chunkIdx(st);
                if (accV == null) {
                    if (yV != topV || idxV != 0) { yV = topV; idxV = 0; } // 半途重启：暂存器空+游标在半路=回顶重扫自愈
                    accV = new com.sdzjz.item.ChunkVaultItem.Acc(cxV, czV);
                    be.vaultAcc.put(i, accV);
                }
                if (!accV.scanComplete) {
                    int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                    long budgetV = Math.min(16384L, (long) running * (1 + countLv) * cycles * Math.max(1, cfg.chunkVaultBlocksPerCycle));
                    net.minecraft.core.BlockPos.MutableBlockPos mpV = new net.minecraft.core.BlockPos.MutableBlockPos();
                    while (budgetV-- > 0 && yV >= bottomV) {
                        mpV.set((cxV << 4) + (idxV >> 4), yV, (czV << 4) + (idxV & 15));
                        net.minecraft.world.level.block.state.BlockState bsV = world.getBlockState(mpV);
                        if (!bsV.isAir() && bsV.getDestroySpeed(world, mpV) >= 0 && !bsV.hasBlockEntity()) {
                            net.minecraft.world.item.Item itV = bsV.getBlock().asItem();
                            if (itV != net.minecraft.world.item.Items.AIR) {
                                int piV = accV.record(bsV);
                                if (piV >= 0) { // 调色板封顶位跳过（病态数据包防线）
                                    int secY = yV >> 4;
                                    int[] arrV = accV.secs.computeIfAbsent(secY, k2 -> new int[4096]);
                                    arrV[((yV & 15) << 8) | idxV] = piV + 1; // 段内序=(y&15)*256+lx*16+lz，idx 恰为 lx*16+lz
                                    accV.bom.merge(BuiltInRegistries.ITEM.getKey(itV).toString(), 1L, Long::sum);
                                    accV.total++;
                                }
                            }
                        }
                        if (++idxV >= 256) { idxV = 0; yV--; be.statusDirty = true; }
                    }
                    com.sdzjz.item.ChunkVaultItem.cursor(st, Math.max(yV, bottomV), idxV);
                    if (yV < bottomV) accV.scanComplete = true;
                }
                if (accV.scanComplete) {
                    net.minecraft.nbt.CompoundTag tplV = new net.minecraft.nbt.CompoundTag();
                    tplV.putInt("ox", cxV);
                    tplV.putInt("oz", czV);
                    tplV.putString("dim", dimV);
                    tplV.putLong("total", accV.total);
                    net.minecraft.nbt.ListTag palV = new net.minecraft.nbt.ListTag();
                    for (net.minecraft.world.level.block.state.BlockState psV : accV.pal) palV.add(net.minecraft.nbt.NbtUtils.writeBlockState(psV));
                    tplV.put("pal", palV);
                    net.minecraft.nbt.CompoundTag secsV = new net.minecraft.nbt.CompoundTag();
                    for (java.util.Map.Entry<Integer, int[]> eV : accV.secs.entrySet()) secsV.putIntArray(String.valueOf(eV.getKey()), eV.getValue());
                    tplV.put("secs", secsV);
                    net.minecraft.nbt.CompoundTag bomV = new net.minecraft.nbt.CompoundTag();
                    for (java.util.Map.Entry<String, Long> eV : accV.bom.entrySet()) bomV.putLong(eV.getKey(), eV.getValue());
                    tplV.put("bom", bomV);
                    java.util.UUID uidV = com.sdzjz.block.ChunkTemplateStore.of(swV.getServer()).put(tplV, cfg.chunkTemplateMaxCount);
                    if (uidV == null) { be.statR(i, 3, "模板库已满（chunkTemplateMaxCount=" + cfg.chunkTemplateMaxCount + "），清理后自动续存"); continue; } // 暂存保留逐拍重试
                    ItemStack coreV = com.sdzjz.item.ChunkDataCoreItem.make(com.sdzjz.registry.ModItems.CHUNK_DATA_CORE,
                            uidV.toString(), cxV, czV, dimV, accV.total, accV.bom.size());
                    com.sdzjz.machine.StorageAccess depV = be.depositFor(world, i);
                    if (depV != null) be.depositOrBuffer(depV, coreV); else be.addOutput(coreV); // 真栈两口，绝不 distribute
                    com.sdzjz.item.ChunkVaultItem.finish(st, uidV.toString(), accV.total);
                    be.vaultAcc.remove(i);
                    be.prodTally(1);
                    be.statusDirty = true;
                    be.stat(i, 0); // 存档收讫=待机
                    produced = true;
                } else {
                    be.stat(i, 1);
                }
                be.setChanged();
            } else if (st.getItem() instanceof com.sdzjz.item.ChunkFilterItem) {
                // m377 区块过滤器：规则牌坊不干活——规则由相连的区块移除器每拍主动来读（见下分支），
                // 本体不收不产不转发（accepts/chainWants 恒假=MachineItem 免费型尾兜）。恒待机灰灯。
                be.stat(i, 0);
            } else if (st.getItem() instanceof com.sdzjz.item.ChunkRemoverItem) {
                // m376 区块移除器（m382 升区域：以绑定区块为中心 w×w 方阵，#zr 哨兵换挡=新工程清进度重扫）：世界内右键绑定目标区块（LinkerItem 同款
                // useOnBlock 存 NBT），画布上自顶向下逐层移除，掉落物（getDroppedStacks=如同正确
                // 工具无附魔挖掘）进出线/存储。免费型（挖矿原版也免费，成本=时间），预算=
                // 台数×(1+数量级)×周期×chunkRemoverBlocksPerCycle。边界：基岩类硬度<0 永不动；
                // 带方块实体的默认跳过（防误吞基地箱子/本模组核心，config 可开，开了容器内容物按
                // 原版 onStateReplaced 散落原地不进机器）；仅同维度；目标区块未加载=红灯不强载
                // （m142 毒区块票教训：绝不替玩家发加载票）。接线五件账：tick=本分支；accepts=
                // 恒假（免费型 def 无输入，落 accepts0 尾兜自然 false）；setNodeTarget=不适用
                // （目标在世界内绑定非画布选）；徽章=副行进度文案（canvasLine，无选择器按钮）；
                // chainWants=零需求（不吃料自然不拉料，MachineItem 尾兜免费型恒 false）。
                if (!cfg.chunkRemoverEnabled) { be.statR(i, 2, "区块移除器已在配置停用（chunkRemoverEnabled）"); continue; }
                if (!com.sdzjz.node.NodeTags.chunkBound(st)) { be.stat(i, 0); continue; } // 未绑定=待机（副行有指引）
                if (com.sdzjz.node.NodeTags.chunkDone(st)) { be.stat(i, 0); continue; } // 已清完=待机（重绑重扫）
                if (!(world instanceof net.minecraft.server.level.ServerLevel swz)) continue;
                String dimZ = world.dimension().location().toString();
                if (!dimZ.equals(com.sdzjz.node.NodeTags.chunkDim(st))) { be.statR(i, 3, "绑定区块在其它维度（" + com.sdzjz.node.NodeTags.chunkDim(st) + "），换同维度核心或重绑"); continue; }
                int cxZ = com.sdzjz.node.NodeTags.chunkX(st), czZ = com.sdzjz.node.NodeTags.chunkZ(st);
                int rZ = Math.min(Math.max(0, com.sdzjz.node.NodeTags.chunkRadius(st)), Math.max(0, cfg.chunkRemoverMaxRadius)); // m382 区域半径（配置收顶/脏值收底）
                int wZ = 2 * rZ + 1, chunksZ = wZ * wZ; // 区域=以绑定区块为中心的 w×w 分块方阵
                // m398 自适应时间预算（评审路线第四笔）：每台每拍先领一个**墙钟时间片**，
                // 全服所有移除器共用一个池。池见底也保底 1ms/台（绝不整拍不干活=m99 防哑死），
                // 于是"到底能挖多快"由真实机器性能决定，不再由一个拍脑袋的方块数决定。
                long sliceNsZ = 0L;
                if (cfg.chunkRemoverTimeSliceMs > 0) {
                    int nowTickZ = swz.getServer().getTickCount();
                    if (nowTickZ != remPoolTick) { remPoolTick = nowTickZ; remPoolUsedNs = 0L; }
                    long sliceWantZ = (long) Math.max(1, cfg.chunkRemoverTimeSliceMs) * 1_000_000L;
                    if (cfg.chunkRemoverTimePoolMs > 0) {
                        long leftZ = (long) cfg.chunkRemoverTimePoolMs * 1_000_000L - remPoolUsedNs;
                        sliceNsZ = Math.max(1_000_000L, Math.min(sliceWantZ, leftZ)); // 池空也给 1ms 保底
                    } else sliceNsZ = sliceWantZ;
                }
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                // m377 收集相连区块过滤器（任一方向连线即生效；多台=规则 AND；m110b 暂停即隔离；
                // config chunkFilterEnabled 关=忽略过滤器按全量挖，文档已写明）。
                java.util.List<com.sdzjz.item.ChunkFilterItem.CompiledRule> rulesZ = null; // m392 评审②：收集期一次编译（Set<Item> 身份查找），热路径零 NBT 读零 String 化
                // m392 评审①"邻接预编译"判不做留证：本收集是每机器周期一次的冷路径（≤512 次 instanceof
                // +小数组扫），量级 µs；真热的是每块×每滤的名单判定（已由②编译解决）。若 m391 仪表
                // SCAN 段实测打脸再回头做拓扑期编译。
                int fTopZ = world.getMaxBuildHeight() - 1, fBotZ = world.getMinBuildHeight();
                if (cfg.chunkFilterEnabled) {
                    for (int j = 0; j < be.g.machineNodes.size(); j++) {
                        ItemStack fz = be.g.machineNodes.get(j);
                        if (!(fz.getItem() instanceof com.sdzjz.item.ChunkFilterItem) || com.sdzjz.node.NodeTags.nodePaused(fz)) continue;
                        boolean linkedZ = false;
                        if (outT[i] != null) for (int t : outT[i]) if (t == j) { linkedZ = true; break; }
                        if (!linkedZ && outT[j] != null) for (int t : outT[j]) if (t == i) { linkedZ = true; break; }
                        if (!linkedZ) continue;
                        if (rulesZ == null) rulesZ = new java.util.ArrayList<>(2);
                        rulesZ.add(com.sdzjz.item.ChunkFilterItem.compile(fz)); // m392 名单→Set<Item> 一次编译
                        fTopZ = Math.min(fTopZ, com.sdzjz.item.ChunkFilterItem.presetMaxY(fz));
                        fBotZ = Math.max(fBotZ, com.sdzjz.item.ChunkFilterItem.presetMinY(fz));
                    }
                }
                int prevStZ = (i < be.g.nodeStatus.size()) ? be.g.nodeStatus.get(i) : 0; // m384 施法爆发沿：非运行态→首铲=锁定爆发
                int fxNZ = 0; // m384 本拍已喷粒子数（前沿流封顶 6 点/拍）
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                boolean voidOkZ = cfg.chunkRemoverVoidMode;
                int modeZ = com.sdzjz.item.ChunkRemoverItem.mode(st, voidOkZ); // m386 0=有掉落 1=无掉落极速；m397 2=空置域（破基岩，config 关掉时按 1 用）
                boolean voidZ = modeZ == 2;   // m397 空置域：硬度<0 的方块（基岩/屏障/末地传送门框）也拆
                boolean voidBlockedZ = com.sdzjz.node.NodeTags.chunkMode(st) == 2 && !voidOkZ; // 选了但服主关了=黄灯说清楚，别静默按别的模式挖
                boolean sealZ = cfg.chunkRemoverSealFluids && com.sdzjz.node.NodeTags.chunkSealOn(st); // m388 封边挡水（config 总闸 AND 节点开关；m394 起节点侧默认开）
                // m396 封边材料自定义（作者点名"默认铺可以改方块，但要检查不够会提醒"）：默认=免费石头
                // （m389 口径不动）；选了自定义料就**从存储扣**，扣不到=本拍起回落免费石墙 + 黄灯提醒，
                // 绝不静默停封让水灌进来（m99：静默无效比慢更伤）。取料口与复制机母本同姿势：
                // 定向存储线优先、退回核心输入源。
                String sealIdZ = cfg.chunkRemoverSealCustom ? com.sdzjz.node.NodeTags.chunkSealBlock(st) : "";
                net.minecraft.world.level.block.Block sealCurZ = sealZ ? com.sdzjz.item.ChunkRemoverItem.sealBlockOf(sealIdZ) : null;
                boolean sealPayZ = sealCurZ != null;                       // 本拍仍在用（付费的）自定义料
                boolean sealShortZ = false;                                // 本拍出现过料不足（末尾出黄灯提醒）
                if (sealCurZ == null) sealCurZ = net.minecraft.world.level.block.Blocks.STONE; // 免费兜底料
                com.sdzjz.machine.StorageAccess sealAccZ = null;
                if (sealPayZ) {
                    sealAccZ = be.supplyFor(world, i);
                    if (sealAccZ == null) {
                        if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                        sealAccZ = src;
                    }
                    if (sealAccZ == null) { sealPayZ = false; sealShortZ = true; sealCurZ = net.minecraft.world.level.block.Blocks.STONE; }
                }
                int minBxZ = (cxZ - rZ) << 4, maxBxZ = ((cxZ + rZ) << 4) + 15; // m388 区域方块外沿（封判只查边界一圈，成本=周长非面积）
                int minBzZ = (czZ - rZ) << 4, maxBzZ = ((czZ + rZ) << 4) + 15;
                // m395 Bulk Engine 第一刀=世界写入标志位瘦身（仪表判决线兑现：MUTATE 占七段 68.5%、
                // 单块 setBlockState 均 2042ns，这 2042ns 的大头就是 flag=3 里的 NOTIFY_NEIGHBORS——
                // 每拆一块都要给六个邻居发方块更新（水/岩浆当场排流体刻、沙砾当场判掉落、红石/观察者
                // 全线响应），外加邻居形状更新。这台机器是"整片区域全拆"，这些更新一律白干：反正邻居
                // 下一拍也要被拆。快写=NOTIFY_LISTENERS|FORCE_STATE（客户端照收更新，跳邻居更新与形状
                // 更新）；关掉即回 NOTIFY_ALL 逐位同旧，供对表与出事回滚。
                final int wflagZ = cfg.chunkRemoverFastWrite
                        ? (net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE)
                        : net.minecraft.world.level.block.Block.UPDATE_ALL;
                long sealFillZ = 0; // m388 空位补封数（重扫给已挖开的边界补石墙；不进 zn 移除账只吃预算）
                long fluidZ = 0; // m390 本拍整层清水数（流体免费不吃移除预算，单独 4096 顶护 tick；计入 zq 湿账供复检环）
                long budgetZ = (long) running * (1 + countLv) * cycles * Math.max(1, cfg.chunkRemoverBlocksPerCycle);
                if (modeZ != 0) budgetZ *= Math.max(1, cfg.chunkRemoverNoDropSpeedMult); // m397 无掉落/空置域同走快车道：免掉落表求值/免路由，预算直接乘倍
                long capBlocksZ = Math.max(256, cfg.chunkRemoverMaxBlocksPerTick); // m398 每拍方块硬顶可配（原写死 4096，默认抬到 16384：m395 快写后单块便宜多了，且下面还有时间片兜着 tick）
                boolean cappedZ = budgetZ > capBlocksZ;                            // 升级算出来的量被硬顶削掉了=副行要说出来（m99：静默无效比慢更伤）
                budgetZ = Math.min(budgetZ, capBlocksZ);
                com.sdzjz.machine.StorageAccess depositZ = (modeZ != 0 || hasOut[i]) ? null : be.depositFor(world, i); // m397 空置域并入无掉落口径
                if (modeZ == 0 && !hasOut[i] && depositZ == null) budgetZ = Math.min(budgetZ, 64L * OUTPUT_SLOTS); // 兜底缓存封顶（交易机同规）
                int yZ = com.sdzjz.node.NodeTags.chunkY(st), idxZ = com.sdzjz.node.NodeTags.chunkIdx(st);
                int ordZ = Math.min(Math.max(0, com.sdzjz.node.NodeTags.chunkOrd(st)), chunksZ - 1); // m382 分块序号（换挡后越界收敛）
                int bottomZ = world.getMinBuildHeight();
                if (yZ > fTopZ) { yZ = fTopZ; idxZ = 0; ordZ = 0; } // m377 Y 挡快进：窗顶以上直接跳过（游标单向不回卷，重扫=重绑）
                long scanCapZ = Math.min(65536L, Math.max(1024L, budgetZ * 4L)); // m398 随硬顶同比抬（真正护 tick 的现在是时间片）；空气/跳过段快进上限
                final boolean profZ = com.sdzjz.debug.CoreProfiler.PHASES; // m391 七段账（评审路线仪表笔：关时全部计时零成本）
                final long scanCap0Z = scanCapZ;
                long pfFilterZ = 0, pfLootZ = 0, pfMutZ = 0, pfSealZ = 0, pfFxZ = 0; // 段耗时（ns）
                long pcFilterZ = 0, pcLootZ = 0, pcMutZ = 0, pcSealZ = 0, pcFxZ = 0; // 段调用数（MUTATE 次数=真实写块数）
                long pT0Z = profZ ? System.nanoTime() : 0;
                long emptyGuardZ = 262144L; // m385 空段快跳硬护栏（isEmpty 位检查几乎零成本，只防病态死循环）
                long removedZ = 0;
                java.util.List<ItemStack> dropsZ = new java.util.ArrayList<>();
                java.util.LinkedHashMap<net.minecraft.world.item.Item, Long> aggZ =
                        (modeZ == 0 && hasOut[i]) ? new java.util.LinkedHashMap<>() : null; // m392 评审③：出线路径边掉落边聚合（Item 身份键），消中间 List 与逐掉落 String 化；存储/兜底缓存两通道保留真栈（组件保真）
                net.minecraft.core.BlockPos.MutableBlockPos mpZ = new net.minecraft.core.BlockPos.MutableBlockPos();
                // m382 层主序扫描：位(256)→分块(w×w)→层(Y)——Y 仍是最外层，m377 过滤下限停靠/
                // 自动续挖语义逐位保真；分块逐个到访时点名查加载，未加载=红灯整单停摆不强载（m142），
                // 绝不静默跳块留窟隆。
                int curCxZ = cxZ + (ordZ % wZ) - rZ, curCzZ = czZ + (ordZ / wZ) - rZ;
                boolean haltZ = false;
                int limitedZ = 0;          // m398 本拍撞了什么上限：0=没撞 1=方块硬顶 2=时间片（副行说人话）
                long opsZ = 0;             // 时间片检查的采样计数（每 128 位查一次墙钟，开销可忽略）
                long tS0Z = System.nanoTime();
                String haltWhyZ = null;
                if (!world.getChunkSource().hasChunk(curCxZ, curCzZ)) { haltZ = true; haltWhyZ = "分块(" + curCxZ + "," + curCzZ + ")未加载·等待中（核心自带 5×5 保载：把核心放进目标区域中心即可挂机；或人在附近）"; }
                while (!haltZ && removedZ + sealFillZ < budgetZ && scanCapZ > 0 && yZ >= fBotZ) { // m388 补封也是 setBlockState 真成本，与移除同吃预算
                    if (sliceNsZ > 0 && (opsZ++ & 127L) == 0L && System.nanoTime() - tS0Z > sliceNsZ) { limitedZ = 2; break; } // m398 时间片到点：游标已落盘，下拍无缝续
                    // m385 空段快跳（实机报修根因：层主序从 Y=顶起步，天上纯空气逐位吃扫描上限——
                    // 1×1 空扫约两分钟才见土，3×3/5×5 是其 9/25 倍观感=死机）：本(分块,层)所在
                    // 16³ 段全空→整 256 位一步跨过，几乎零成本不吃扫描上限（护栏 emptyGuard 防病态）。
                    // getWorldChunk 在 isChunkLoaded 已拦路后调用=绝不触发同步加载（m142 同系警惕）。
                    if (idxZ == 0 && emptyGuardZ-- > 0) {
                        mpZ.set(curCxZ << 4, yZ, curCzZ << 4);
                        if (world.getChunkAt(mpZ).getSections()[world.getSectionIndex(yZ)].hasOnlyAir()) {
                            if (++ordZ >= chunksZ) { ordZ = 0; yZ--; be.statusDirty = true; }
                            if (yZ >= fBotZ) {
                                curCxZ = cxZ + (ordZ % wZ) - rZ;
                                curCzZ = czZ + (ordZ / wZ) - rZ;
                                if (!world.getChunkSource().hasChunk(curCxZ, curCzZ)) { haltZ = true; haltWhyZ = "分块(" + curCxZ + "," + curCzZ + ")未加载·等待中（核心自带 5×5 保载：把核心放进目标区域中心即可挂机；或人在附近）"; }
                            }
                            continue;
                        }
                    }
                    if (sealZ && idxZ == 0) { // m390 水域整层一拍清（作者截图报修"封边没封住+没有支撑的异常水"两案同根）：
                        // 逐位预算把清水拆到多拍，而海洋是自愈的——两个源方块夹空位=当拍再生新源（原版无限水），
                        // 游标身后无限再生=悬浮"异常水"永远清不完。同一游戏刻内流体不更新，进驻(分块,层)先把
                        // 纯流体 256 位一口气原子清完=分块内零再生（分块缝/角的再生交给下方残水复检环兜底）。
                        // 流体无掉落表=免费，不吃移除预算，单独 fluidZ 记账；边界贴水位同拍直接砌石。
                        if (fluidZ > capBlocksZ - 256) break; // m398 每拍清水硬顶随方块硬顶走（原写死 3840=4096-256 同哲学）：满了游标停在本层入口，下拍续清
                        for (int fi = 0; fi < 256; fi++) {
                            mpZ.set((curCxZ << 4) + (fi >> 4), yZ, (curCzZ << 4) + (fi & 15));
                            net.minecraft.world.level.block.state.BlockState fsZ = world.getBlockState(mpZ);
                            if (fsZ.getFluidState().isEmpty()
                                    || fsZ.getBlock().asItem() != net.minecraft.world.item.Items.AIR) continue; // 只清纯流体（海草/含水方块留给常规循环按名单结账，遗留再生由复检环收）
                            boolean fSealZ = false;
                            if (mpZ.getX() == minBxZ || mpZ.getX() == maxBxZ || mpZ.getZ() == minBzZ || mpZ.getZ() == maxBzZ) {
                                long pS0Z = profZ ? System.nanoTime() : 0;
                                fSealZ = chunkSealNeeded(world, mpZ.getX(), yZ, mpZ.getZ(), minBxZ, maxBxZ, minBzZ, maxBzZ);
                                if (profZ) { pfSealZ += System.nanoTime() - pS0Z; pcSealZ++; }
                            }
                            long pM0Z = profZ ? System.nanoTime() : 0;
                            if (fSealZ && sealPayZ && sealAccZ.withdraw(sealIdZ, 1) < 1) { // m396 料不够=本拍起回落免费石墙+末尾提醒
                                sealPayZ = false; sealShortZ = true; sealCurZ = net.minecraft.world.level.block.Blocks.STONE;
                            }
                            world.setBlock(mpZ, (fSealZ ? sealCurZ
                                    : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), wflagZ); // m395 快写标志位；m396 封边材料
                            if (profZ) { pfMutZ += System.nanoTime() - pM0Z; pcMutZ++; }
                            fluidZ++;
                        }
                    }
                    scanCapZ--;
                    mpZ.set((curCxZ << 4) + (idxZ >> 4), yZ, (curCzZ << 4) + (idxZ & 15));
                    net.minecraft.world.level.block.state.BlockState bsZ = world.getBlockState(mpZ);
                    boolean beHereZ = bsZ.hasBlockEntity();
                    boolean skipZ = bsZ.isAir() // m397 空置域=不查硬度（连基岩一起拆），顺带省掉每块一次 getHardness。m392 评审④"Block 分类缓存"判不做留证：1.21.1 反编译源里
                            // getHardness=构造期缓存字段直读、hasBlockEntity=instanceof，每块已是 O(1) 字段级，
                            // 外挂 HashMap 分类缓存反而多一次哈希查找；m391 仪表 SCAN 段若实测打脸再回头。
                            || (!voidZ && bsZ.getDestroySpeed(world, mpZ) < 0) // m397 空置域：硬度<0 的基岩类不再豁免
                            || (cfg.chunkRemoverSkipBlockEntities && beHereZ);
                    boolean pureFluidZ = sealZ && !bsZ.getFluidState().isEmpty()
                            && bsZ.getBlock().asItem() == net.minecraft.world.item.Items.AIR; // m388 纯流体块（水/岩浆本体；含水台阶不算——那是建筑归名单管）
                    if (!skipZ && rulesZ != null && !pureFluidZ) { // m377 方块名单 AND：任一过滤器不放行=留在世上（m388 堵水时纯流体豁免名单——流体不是建筑，留着=水从名单缝里灌）
                        long pF0Z = profZ ? System.nanoTime() : 0; // m391 FILTER 段
                        net.minecraft.world.item.Item bitZ = bsZ.getBlock().asItem(); // m392 评审②落刀：身份查编译集，原"每块新 String+每滤整表重建 NBT 拷贝"退役
                        for (int k = 0; k < rulesZ.size(); k++)
                            if (!rulesZ.get(k).allows(bitZ)) { skipZ = true; break; }
                        if (profZ) { pfFilterZ += System.nanoTime() - pF0Z; pcFilterZ++; }
                    }
                    boolean sealHereZ = false; // m388 本位需封边：区域边界位且外侧贴邻（1~2 个，角双面）有流体
                    if (sealZ && (mpZ.getX() == minBxZ || mpZ.getX() == maxBxZ || mpZ.getZ() == minBzZ || mpZ.getZ() == maxBzZ)
                            && (!skipZ || bsZ.isAir())) { // 被名单/方块实体/基岩留下的块自身就是墙，无需封判
                        long pS0Z = profZ ? System.nanoTime() : 0;
                        sealHereZ = chunkSealNeeded(world, mpZ.getX(), yZ, mpZ.getZ(), minBxZ, maxBxZ, minBzZ, maxBzZ);
                        if (profZ) { pfSealZ += System.nanoTime() - pS0Z; pcSealZ++; }
                        if (sealHereZ && bsZ.getBlock() == sealCurZ) { sealHereZ = false; skipZ = true; } // m396 口径跟着当前材料走：选了自定义料时天然石头**不再**白捡当墙（要的就是整面统一材质），已是本料的照旧不拆不放。m389 石墙已在（自家封边或天然石头都白捡当墙）：不拆不放直接跳过；外侧没水了才当普通石头拆。m388 旧玻璃墙不在保留名单=重扫时被当普通块拆掉同拍置石，自动升级石墙
                    }
                    if (!skipZ) {
                        if (modeZ == 0) { // m386 无掉落模式：直接蒸发不过战利品表（快在这）
                            long pL0Z = profZ ? System.nanoTime() : 0; // m391 LOOT 段
                            for (ItemStack dZ : net.minecraft.world.level.block.Block.getDrops(bsZ, swz, mpZ,
                                    beHereZ ? world.getBlockEntity(mpZ) : null)) {
                                if (dZ.isEmpty()) continue;
                                if (aggZ != null) aggZ.merge(dZ.getItem(), (long) dZ.getCount(), Long::sum); // m392 出线=id 计数通道，边掉边聚合（插入序=首见序与旧口径逐位一致）
                                else dropsZ.add(dZ);
                            }
                            if (profZ) { pfLootZ += System.nanoTime() - pL0Z; pcLootZ++; }
                        }
                        long pM0Z = profZ ? System.nanoTime() : 0; // m391 MUTATE 段（评审判决线主角：均 ns=单块世界写入真成本）
                        if (sealHereZ && sealPayZ && sealAccZ.withdraw(sealIdZ, 1) < 1) { // m396 料不够=回落免费石墙+提醒
                            sealPayZ = false; sealShortZ = true; sealCurZ = net.minecraft.world.level.block.Blocks.STONE;
                        }
                        world.setBlock(mpZ, (sealHereZ ? sealCurZ
                                : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), wflagZ); // m395 快写标志位；m396 封边材料；m389 贴水边界位=石墙代替空气（作者拍板：本来就挖石头；置石免费不从产出扣料——顶层常无圆石，扣料封不上=水照灌）
                        if (profZ) { pfMutZ += System.nanoTime() - pM0Z; pcMutZ++; }
                        if (cfg.chunkFxEnabled) { // m384 施法特效（服务端粒子，周围玩家都看得见零协议）
                            if (removedZ == 0 && prevStZ != 1) // 首铲且上拍非运行=技能锁定：选区顶粒子环+信标激活音
                                chunkFxBurst(swz, cxZ, czZ, rZ, mpZ.getY()); // m385 环在首铲实际层（原 fTop=世界顶天上放烟花）
                            if (fxNZ < 4 && (removedZ & 255L) == 0L) { // m393 激光雕刻：每拍最多四束，按本拍进度铺开（默认预算 64 块/拍=每拍一束跟着游标走；满预算 4096 块/拍时沿削切面撒四束。m394 步长 1024→256：粒子看不见的另一半是太稀）
                                long pX0Z = profZ ? System.nanoTime() : 0; // m391 FX 段（爆发环走首铲一次不单记，前沿流才是每拍常客）
                                chunkFxLaser(swz, mpZ.getX(), mpZ.getY(), mpZ.getZ());
                                if (profZ) { pfFxZ += System.nanoTime() - pX0Z; pcFxZ++; }
                                fxNZ++;
                            }
                        }
                        removedZ++;
                    } else if (sealHereZ && bsZ.isAir()) { // m388 空位补封：开堵水重扫时给已挖开的边界补石墙（此前灌进内圈的水在名单豁免下按普通块清）
                        long pM0Z = profZ ? System.nanoTime() : 0;
                        if (sealPayZ && sealAccZ.withdraw(sealIdZ, 1) < 1) { // m396 料不够=回落免费石墙+提醒
                            sealPayZ = false; sealShortZ = true; sealCurZ = net.minecraft.world.level.block.Blocks.STONE;
                        }
                        world.setBlock(mpZ, sealCurZ.defaultBlockState(), wflagZ); // m395 快写标志位；m396 封边材料
                        if (profZ) { pfMutZ += System.nanoTime() - pM0Z; pcMutZ++; }
                        sealFillZ++;
                    }
                    if (++idxZ >= 256) {
                        idxZ = 0;
                        if (++ordZ >= chunksZ) { ordZ = 0; yZ--; be.statusDirty = true; }
                        if (yZ >= fBotZ) {
                            curCxZ = cxZ + (ordZ % wZ) - rZ;
                            curCzZ = czZ + (ordZ / wZ) - rZ;
                            if (!world.getChunkSource().hasChunk(curCxZ, curCzZ)) { haltZ = true; haltWhyZ = "分块(" + curCxZ + "," + curCzZ + ")未加载·等待中（核心自带 5×5 保载：把核心放进目标区域中心即可挂机；或人在附近）"; }
                        }
                    } // 换层顺带请求同步（进度副行走既有 1/s 节流）
                }
                if (profZ) { // m391 七段账结算（每 tick 一次批量入桶；SCAN=循环墙钟刨去五段=游标/读态/空跳纯开销）
                    long pTotZ = System.nanoTime() - pT0Z;
                    com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_SCAN,
                            pTotZ - pfFilterZ - pfLootZ - pfMutZ - pfSealZ - pfFxZ, scanCap0Z - scanCapZ);
                    com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_FILTER, pfFilterZ, pcFilterZ);
                    com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_LOOT, pfLootZ, pcLootZ);
                    com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_MUTATE, pfMutZ, pcMutZ);
                    com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_SEAL, pfSealZ, pcSealZ);
                    com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_FX, pfFxZ, pcFxZ);
                }
                if (cfg.chunkRemoverTimeSliceMs > 0 && cfg.chunkRemoverTimePoolMs > 0)
                    remPoolUsedNs += System.nanoTime() - tS0Z; // m398 本台实际花掉的墙钟记进全服池
                if (limitedZ == 0 && cappedZ && removedZ + sealFillZ >= budgetZ) limitedZ = 1; // 挖满了被削后的预算=硬顶在拦
                boolean doneZ = yZ < bottomZ; // 完成位只认真·世界底（过滤下限不算完，撤过滤器/换挡自动续挖）
                boolean repassZ = false;
                if (doneZ && sealZ && com.sdzjz.node.NodeTags.chunkWetPass(st) + fluidZ + sealFillZ > 0) {
                    // m390 残水复检环：本遍但凡碰过流体/补过封=不置完成位，从顶再复检一整遍（空段快跳
                    // =复检近乎免费），直到全程零流体才算清完。封边快照的盲区由此自愈：外侧当时干、
                    // 水后来才到的漏点，复检时进来的水被清且此刻外侧有水=当场补石；分块缝再生的源
                    // 方块同样被复检逐遍收干。外侧顶灌（过滤窗顶上方来水）收不干=机器长期当抽水机
                    // 转不置完成位，属正确行为（停勾选即按普通挖收工）。
                    doneZ = false; repassZ = true;
                    yZ = world.getMaxBuildHeight() - 1; ordZ = 0; idxZ = 0;
                }
                boolean parkedZ = !doneZ && !haltZ && !repassZ && yZ < fBotZ; // m377 停在过滤器 Y 下限
                com.sdzjz.item.ChunkRemoverItem.advance(st, Math.max(yZ, bottomZ), ordZ, idxZ, removedZ, doneZ,
                        fluidZ + sealFillZ, repassZ || doneZ, limitedZ); // 空气快进段游标也要落盘；湿账随遍结转（开新遍/真完成即清）；m398 上限位随拍落盘供副行显示
                long pR0Z = profZ ? System.nanoTime() : 0; // m391 ROUTE 段（出线聚合/存储真栈/兜底缓存三通道同账）
                if (removedZ > 0) { // m382 产出与状态分账：停摆拍已挖的掉落照常出货绝不丢
                    be.prodTally(removedZ);
                    if (modeZ == 0 && hasOut[i]) { // 出线走 id 计数派发（掉落物极少带组件，方块实体默认已跳过）
                        for (java.util.Map.Entry<net.minecraft.world.item.Item, Long> eZ : aggZ.entrySet()) // m392 掉落已在 LOOT 段聚合，此处每"类型"一次 String 化（原每"掉落"一次）
                            be.distribute(world, i, outT[i], BuiltInRegistries.ITEM.getKey(eZ.getKey()).toString(), eZ.getValue());
                    } else if (modeZ == 0 && depositZ != null) {
                        for (ItemStack dZ : dropsZ) be.depositOrBuffer(depositZ, dZ); // 存储通道走真栈（组件保真）
                    } else if (modeZ == 0) {
                        for (ItemStack dZ : dropsZ) be.addOutput(dZ);
                    }
                    produced = true;
                    if (profZ) com.sdzjz.debug.CoreProfiler.subAdd(com.sdzjz.debug.CoreProfiler.SUB_R_ROUTE,
                            System.nanoTime() - pR0Z, aggZ != null ? aggZ.size() : dropsZ.size());
                }
                if (voidBlockedZ) { // m397 选了空置域但服主在 config 关了：说清楚按什么模式在挖，别静默降级
                    be.statR(i, 2, "空置域模式已在配置停用（chunkRemoverVoidMode）：本机按无掉落·极速挖，基岩保留");
                } else if (haltZ) {
                    be.statR(i, 2, haltWhyZ); // m387 改黄灯：未加载=等待非错误，红色撞"缺料"色语义作者实机误读（游标已落盘，加载恢复自动续）
                } else if (sealShortZ) { // m396 作者点名"要检查如果不够会提醒"：料不足=黄灯带原因，活照干（回落免费石墙不让水灌）
                    be.statR(i, 2, "封边材料不足：" + com.sdzjz.item.ChunkRemoverItem.sealLabel(st)
                            + " 取不到（"
                            + (sealAccZ == null ? "这台没接存储线/仓——拉一条到有料的存储上" : "库存见底——往连着的存储补货")
                            + "）；本轮已回落免费石墙，不让水灌进来，补上料即自动恢复");
                } else if (removedZ > 0) {
                    be.stat(i, 1);
                } else if (parkedZ) {
                    be.statR(i, 2, "已达过滤器 Y 下限（换挡或撤过滤器自动续挖，全量重扫=重绑）");
                } else {
                    be.stat(i, doneZ ? 0 : 1); // 本拍全是空气/名单外段=游标在走照亮绿灯；真见底=待机
                }
                be.setChanged(); // 游标进度在节点 NBT 里，动了就存
            } else if (st.getItem() instanceof com.sdzjz.item.InfiniteBeaconItem) {
                // m399 无限距离信标（作者点名·第 101 台）：原版信标三条枷锁（金字塔/天空可见/50 格距离）
                // 一次拆干净——每周期从存储扣一份信标料，把选定效果**刷给全服在线玩家**（默认跨维度，
                // config 可收成"只管本核心所在维度"）。接线五件：tick=本分支 / accepts=恒假（掉落表空+
                // consumesInputs=false，accepts0 尾兜自然为假，料从存储扣不吃路由）/ setNodeTarget=不适用
                // （效果与等级走菜单两哨兵 #bfx #bfl）/ 徽章=副行 canvasLine / chainWants=显式零需求
                // （不吃线上料自然不拉料，复制机同律）。效果刷新式=速度/数量升级对本机零收益，tooltip 已明写（m99）。
                if (!cfg.infiniteBeaconEnabled) { be.statR(i, 2, "无限距离信标已在配置停用（infiniteBeaconEnabled）"); continue; }
                if (!(world instanceof net.minecraft.server.level.ServerLevel swb)) continue;
                int cyclesB = be.cyclesThisTick(i, 80, speedLv, cfg);
                if (cyclesB <= 0) continue;
                int fxB = com.sdzjz.item.InfiniteBeaconItem.effectIndex(st);
                int lvB = com.sdzjz.item.InfiniteBeaconItem.level(st);
                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> fxEB =
                        com.sdzjz.item.InfiniteBeaconItem.effectEntry(fxB);
                if (fxEB == null) { be.statR(i, 3, "效果在本版本注册表里查不到（" + com.sdzjz.item.InfiniteBeaconItem.FX_ID[fxB] + "）：菜单换一个效果"); continue; }
                com.sdzjz.machine.StorageAccess accB = be.supplyFor(world, i);
                if (accB == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accB = src;
                }
                if (accB == null) { be.statR(i, 3, "未接存储网络（信标料从仓里扣）"); continue; }
                int needB = Math.max(1, cfg.infiniteBeaconFuelPerCycle)
                        * (lvB == 1 ? Math.max(1, cfg.infiniteBeaconLevel2Cost) : 1);
                String paidB = null;
                for (String fidB : com.sdzjz.item.InfiniteBeaconItem.FUELS) { // 从便宜到贵依次扣（原版收料表同款）
                    if (accB.count(fidB) < needB) continue;
                    if (accB.withdraw(fidB, needB) > 0) { paidB = fidB; break; }
                }
                if (paidB == null) { be.statR(i, 3, "没料：仓里要有 铁锭/金锭/绿宝石/钻石/下界合金锭 任一（本周期需 " + needB + " 个），不赊账"); continue; }
                int durB = Math.max(1, cfg.infiniteBeaconEffectSeconds) * 20;
                boolean crossB = cfg.infiniteBeaconCrossDimension;
                for (net.minecraft.server.level.ServerPlayer spB : swb.getServer().getPlayerList().getPlayers()) {
                    if (!crossB && spB.level() != world) continue; // 收成同维度时只管本核心这层
                    if (spB.isSpectator()) continue;
                    spB.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            fxEB, durB, lvB, true, false, true)); // ambient=true 边框淡；粒子关（全服常驻不刷屏）；图标留
                }
                be.stat(i, 1);
            } else if (st.getItem() instanceof MachineItem vd && "villager_discount_machine".equals(vd.def().id())) {
                // m145 村民打折机（用户拍板：独立画布机自动治愈）：吃网络金苹果给共网交易所里的合同
                // 升折扣。1 苹果=1 级与交易所手动治愈同价（自动化不改经济账）；低折扣合同优先补短板；
                // 预算=台数×(1+数量级)×周期。发现面走 TradeCenterBlockEntity.loadedIn 注册表 +
                // sharesNetwork 共网过滤（connectedCores 坐标交集）。状态灯：无网络/有合同没苹果=红灯，
                // 无可升合同（没交易所或全满级）=待机，升了=绿灯。
                int cycles = be.cyclesThisTick(i, vd.def().baseIntervalTicks(), speedLv, cfg);
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                com.sdzjz.machine.StorageAccess accV = be.supplyFor(world, i);
                if (accV == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accV = src;
                }
                if (accV == null) { be.statR(i, 3, "未接存储网络（取不到金苹果）"); continue; } // 无网络=红灯（苹果没处取）
                java.util.List<StorageCoreBlockEntity> banksV = new java.util.ArrayList<>();
                if (accV instanceof StorageCoreBlockEntity cv) banksV.add(cv);
                else if (accV instanceof DataPanelBlockEntity pv)
                    banksV.addAll(StorageCoreBlockEntity.connectedCores(world, pv.getBlockPos()));
                java.util.List<TradeCenterBlockEntity> tcs = new java.util.ArrayList<>();
                for (TradeCenterBlockEntity tc : TradeCenterBlockEntity.loadedIn(world))
                    if (tc.canCure() && tc.sharesNetwork(banksV)) tcs.add(tc);
                if (tcs.isEmpty()) { be.stat(i, 0); continue; } // 无可升合同=待机不是故障
                tcs.sort(java.util.Comparator.comparingInt(t ->
                        TradeCenterBlockEntity.contractDiscount(t.contractSlot.getItem(0))));
                long budgetV = (long) running * (1 + countLv) * cycles;
                long cured = 0;
                boolean apples = true;
                while (budgetV > 0 && apples) {
                    boolean any = false;
                    for (TradeCenterBlockEntity tc : tcs) {
                        if (budgetV <= 0) break;
                        if (!tc.canCure()) continue;
                        int got = 0;
                        for (StorageCoreBlockEntity bank : banksV) {
                            got = bank.withdraw("minecraft:golden_apple", 1);
                            if (got > 0) break;
                        }
                        if (got <= 0) { apples = false; break; } // 苹果见底，先付后升不欠账
                        tc.cureOnce();
                        budgetV--; cured++; any = true;
                    }
                    if (!any) break; // 本轮一个都没升成（全满级）
                }
                if (cured > 0) { be.prodTally(cured); produced = true; be.stat(i, 1); }
                else be.statR(i, 3, "缺料：金苹果"); // 有合同可升却取不到苹果=缺料红灯（m329 勘误：实取普通金苹果，旧文案误写"附魔"会误导备料）
            } else if (st.getItem() instanceof MachineItem sk && sk.def().id().startsWith("sculk_")) {
                // m138 幽匿三机：吃核心经验池产幽匿件（原版幽匿=经验具象化——催化体吸收死亡经验长
                // 蔓延，蔓延概率长出传感器/尖啸体）。经验闸镜像附魔工厂（m132 同池竞争先例）：
                // 池里够几轮跑几轮，池空=缺料红灯（画布经验池数字可见）。产物无组件，出路走通用三条
                //（distribute/精确入库/输出缓存）不特判。经验成本对齐原版蔓延电荷量级：
                // m143 三塔合并为幽匿线一台：单价=催化2+传感9+尖啸9 合计 20/轮（三表齐滚，总账不变）。
                MachineDef def = sk.def();
                int cycles = be.cyclesThisTick(i, def.baseIntervalTicks(), speedLv, cfg);
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);
                double xpPer = 20.0;
                long attempts = (long) running * cycles;
                attempts = Math.min(attempts, (long) (be.xpPool / xpPer)); // 经验闸
                if (attempts <= 0) { be.statR(i, 3, "缺料（本周期成本料不足，对照徽章/工具提示）"); continue; }
                be.xpPool -= xpPer * attempts;
                be.stat(i, 1);
                com.sdzjz.machine.StorageAccess depositSk = hasOut[i] ? null : be.depositFor(world, i);
                boolean cappedSk = !hasOut[i] && depositSk == null;
                for (MachineDef.Drop d : def.outputs()) {
                    if (!com.sdzjz.node.NodeTags.machineFilterAllows(st, d.item())) continue; // m149 选了产物就只出选中
                    long sum = com.sdzjz.machine.DropRolls.rollDrops(world.getRandom(), d, (int) Math.min(attempts, Integer.MAX_VALUE), countLv);
                    if (sum <= 0) continue;
                    if (cappedSk) sum = Math.min(sum, 64L * OUTPUT_SLOTS);
                    be.prodTally(sum);
                    if (hasOut[i]) be.distribute(world, i, outT[i], d.item(), sum);
                    else if (depositSk != null) be.depositOrBuffer(depositSk, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) Math.min(sum, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) sum));
                    produced = true;
                }
            } else if (st.getItem() instanceof MachineItem mi) {
                MachineDef def = mi.def();
                int cycles = be.cyclesThisTick(i, def.baseIntervalTicks(), speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier); // m99 并发直接乘台数
                int doCycles = cycles;

                if (def.consumesInputs()) {
                    if (hasIn[i]) {
                        // 从内部缓存取料（连线喂料）+ m340 显式供料线补足。m99：料不够整批时按料量折算周期数
                        final com.sdzjz.machine.StorageAccess expG = be.topUpSource(world, i);
                        for (MachineDef.Input in : def.inputs())
                            doCycles = (int) Math.min(doCycles, be.dualCount(i, expG, in.item()) / ((long) in.count() * running));
                        if (doCycles <= 0) { be.statR(i, 3, be.whyMissingBufIn(i, def.inputs(), running)); continue; }
                        for (MachineDef.Input in : def.inputs()) be.dualWithdraw(i, expG, in.item(), (long) in.count() * running * doCycles);
                    } else {
                        com.sdzjz.machine.StorageAccess supply = be.supplyFor(world, i); // 存储→机器 定向供料连线优先
                        if (supply == null) {
                            if (!srcResolved) {
                                src = be.resolveInputSource(world, pos);
                                srcResolved = true;
                            }
                            supply = src;
                        }
                        if (supply == null) { be.statR(i, 3, "未接存储/供料线"); continue; }
                        for (MachineDef.Input in : def.inputs())
                            doCycles = (int) Math.min(doCycles, supply.count(in.item()) / ((long) in.count() * running));
                        if (doCycles <= 0) { be.statR(i, 3, be.whyMissingSupIn(supply, def.inputs(), running)); continue; }
                        for (MachineDef.Input in : def.inputs()) supply.withdraw(in.item(), in.count() * running * doCycles);
                    }
                }
                be.stat(i, 1);

                com.sdzjz.machine.StorageAccess depositMi = hasOut[i] ? null : be.depositFor(world, i);
                boolean cappedMi = !hasOut[i] && depositMi == null; // m99 封顶只对"进内部缓存"生效
                for (MachineDef.Drop d : def.outputs()) {
                    if (!com.sdzjz.node.NodeTags.machineFilterAllows(st, d.item())) continue; // m149 选了产物就只出选中
                    long sum = com.sdzjz.machine.DropRolls.rollDrops(world.getRandom(), d, doCycles, countLv);
                    if (sum <= 0) continue;
                    long total = (long) running * sum;
                    if ("minecraft:goat_horn".equals(d.item())) {
                        // m137 山羊角：8 变体 instrument 组件、maxCount=1——组件产物规矩同酿造/附魔：
                        // 出线一律无视（distribute 走 id 账本带不了组件），只走 精确账本 或 输出缓存；
                        // 有出线时 depositMi 为 null，此处单独解析入库口；无存储按缓存格数封顶防白扣。
                        com.sdzjz.machine.StorageAccess dHorn = depositMi != null ? depositMi : be.depositFor(world, i);
                        if (dHorn == null) total = Math.min(total, OUTPUT_SLOTS);
                        if (total <= 0) continue;
                        be.prodTally(total);
                        be.depositGoatHorns(world, dHorn, total);
                        produced = true;
                        continue;
                    }
                    if (cappedMi) total = Math.min(total, 64L * OUTPUT_SLOTS);
                    be.prodTally(total); // m86 实测产量
                    if (hasOut[i]) be.distribute(world, i, outT[i], d.item(), total);
                    else if (depositMi != null) be.depositOrBuffer(depositMi, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) Math.min(total, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) total));
                    produced = true;
                }
                double mxp = MachineXp.of(def.id());
                if (mxp > 0) { be.xpPool += mxp * running * doCycles; produced = true; }
            } else if (st.getItem() instanceof CaptureCageItem && CaptureCageItem.isCaged(st)) {
                String mob = CaptureCageItem.cagedType(st);
                java.util.List<MachineDef.Drop> drops = (mob == null) ? null : MobDrops.get(mob);
                if (drops == null) continue;
                int cycles = be.cyclesThisTick(i, 30, speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier);    // m99 并发直接乘台数
                be.stat(i, 1);
                com.sdzjz.machine.StorageAccess depositCg = hasOut[i] ? null : be.depositFor(world, i);
                boolean cappedCg = !hasOut[i] && depositCg == null;  // m99 封顶只对"进内部缓存"生效
                for (MachineDef.Drop d : drops) {
                    if (!com.sdzjz.node.NodeTags.machineFilterAllows(st, d.item())) continue; // m149
                    long sum = com.sdzjz.machine.DropRolls.rollDrops(world.getRandom(), d, cycles, countLv);
                    if (sum <= 0) continue;
                    long total = (long) running * sum;
                    if (cappedCg) total = Math.min(total, 64L * OUTPUT_SLOTS);
                    be.prodTally(total); // m86 实测产量
                    if (hasOut[i]) be.distribute(world, i, outT[i], d.item(), total);
                    else if (depositCg != null) be.depositOrBuffer(depositCg, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) Math.min(total, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), (int) total));
                    produced = true;
                }
                double cxp = MachineXp.mob(mob);
                if (cxp > 0) { be.xpPool += cxp * running * cycles; produced = true; }
            }
        }
        if (com.sdzjz.debug.CoreProfiler.PHASES && __tb >= 0)
            com.sdzjz.debug.CoreProfiler.sub(__tb, System.nanoTime() - __tn); // m354 末节点结账（pushOutput 不入类型账）
        if (produced) {
            be.pushOutput(world, pos);
            be.setChanged();
        }
        com.sdzjz.debug.CoreProfiler.phase(com.sdzjz.debug.CoreProfiler.PH_PROD, System.nanoTime() - __pp); // m321（pushOutput 计入生产段）
        if (!be.lagPause && be.ticks % 2 == 0) be.ejectOverflow(world, pos); // m114/m115 断网喷射：2t一组≈10组/秒；过载时停喷
        if (be.statusDirty && be.ticks % 20 == 0) { // 状态灯：有变化才同步，最多 1 次/秒
            be.statusDirty = false;
            be.setChanged();
            be.syncToClient();
        }
    }

    /** 右键把机器/笼子作为一个节点加入画布（无数量上限）。 */
    /** 右键把机器/笼子作为一个节点加入画布（无上限）；首次自动布局位置。 */
    public boolean insertMachine(net.minecraft.world.entity.player.Player byPlayer, ItemStack held) {
        if (held.isEmpty()) return false;
        int capN = SdzjzConfig.get().maxNodesPerCore; // m270 硬上限（审计"无限节点"条）：拓扑重编译/tick遍历/NBT同步成本全随节点数涨
        if (capN > 0 && g.machineNodes.size() >= capN) {
            capMsg(byPlayer, "画布节点已达上限 " + capN + "（config maxNodesPerCore 可调，0=无限）");
            return false;
        }
        ItemStack node = held.copyWithCount(1); // m78：一次只放 1 台（原来整叠塞进一个节点，"一右键就是一组"）
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(node);
        if (!n.contains("nx")) {
            int i = g.machineNodes.size(), cols = 6;
            n.putInt("nx", 20 + (i % cols) * 112);
            n.putInt("ny", 20 + (i / cols) * 88);
            com.sdzjz.item.ItemData.write(node, n);
        }
        g.machineNodes.add(node);
        bumpTopo(); // m179
        nodeBuf(g.machineNodes.size() - 1); // 懒补齐：新节点空输入缓存
        held.shrink(1);
        setChanged();
        syncToClient();
        return true;
    }

    /** m306 压测收场专用：清空画布节点**不散落**（BenchRunner 自建自清；与 dropAll 同清单
     *  去掉 Containers——一键压测 5 万节点若走 dropAll = 物品雨。绝不用于任何玩家路径）。 */
    public void benchClearNodes() {
        g.machineNodes.clear();
        bumpTopo(); // m179
        g.nodeStatus.clear();
        g.nodeReason.clear(); // m178
        setChanged();
    }

    /** 读节点各类升级等级。 */
    static final String K_SPD = "spd", K_CNT = "cnt", K_PAR = "par"; // m356 三级键常量（大循环单视图齐读与读者同源）
    public int nodeSpeed(ItemStack s) { return nodeInt(s, K_SPD); }
    public int nodeCount(ItemStack s) { return nodeInt(s, K_CNT); }
    public int nodePar(ItemStack s)   { return nodeInt(s, K_PAR); }

    // ===== m99 升级数学重写：工作量累积模型 =====
    // 旧模型三处死区：①速度线性减周期(base-4×级)→触底1tick后再插全部无效；②并发只抬"同时运行台数上限"
    // →节点里只有1台机器时(m78后的常态)从头到尾没生效过；③数量产出被64×输出格硬顶→堆到顶再插白插。
    // 新模型：速率=(1+gain)^速度级×productionRateMultiplier，每tick累积、攒够基础周期结算1次，
    // 速率溢出折成同tick多周期——永不触底；并发直接乘台数(1台也翻倍)；数量顶只在"产出只能进内部缓存"时保留。
    private double[] workAcc = new double[0]; // 节点索引→累积工作量(不落盘,重载至多丢半个周期)——m356 Map<Integer,Double> 拆装箱下岗（每节点每 tick 一次 Double put 是分配账常客）

    // m356 速率查表：Math.pow((1+gain)^spd)×mult 每节点每 tick 现算下岗（矩阵实测空转 654ns/节点·tick 的大头）。
    // 失效口径=对 gain/mult 两个来源值做快照比对（配置对象是原地改字段的单例，identity 靠不住）；
    // 表按需成倍扩到最高等级，同参同级与 Math.pow 逐位一致（同一确定性函数同一入参）。
    private transient double[] rateCache = new double[0];
    private transient double rateGainSnap = Double.NaN, rateMultSnap = Double.NaN;
    private double rateOf(int speedLv, SdzjzConfig cfg) {
        double gain = Math.max(0.0, cfg.upgradeSpeedGainPerLevel);
        double mult = Math.max(0.01, cfg.productionRateMultiplier);
        int lv = Math.max(0, speedLv);
        if (gain != rateGainSnap || mult != rateMultSnap) {
            rateCache = new double[0]; rateGainSnap = gain; rateMultSnap = mult;
        }
        if (lv >= rateCache.length) {
            int n = Math.max(lv + 1, Math.max(8, rateCache.length * 2));
            double[] nc = new double[n];
            for (int k = 0; k < n; k++) nc[k] = Math.pow(1.0 + gain, k) * mult;
            rateCache = nc;
        }
        return rateCache[lv];
    }
    private long recipesThisTick = 0; // m270 全核每tick已结算周期数（tickInner 每tick复位；配合 maxRecipesPerCoreTick 共享预算）

    /** 该节点本tick应结算的生产周期数（0=继续攒）。 */
    private int cyclesThisTick(int nodeIndex, int baseInterval, int speedLv, SdzjzConfig cfg) {
        double rate = rateOf(speedLv, cfg); // m356 查表（同参同级与 Math.pow 逐位一致）
        int base = Math.max(1, baseInterval);
        int cap = Math.max(1, cfg.upgradeMaxCyclesPerTick);
        double acc = (nodeIndex < workAcc.length ? workAcc[nodeIndex] : 0.0) + rate; // m356 数组直取
        int cycles = (int) (acc / base);
        if (cycles > cap) cycles = cap;
        boolean hadWork = cycles > 0; // m339 预算剪零可视化用：区分"没攒够周期"与"被预算剪没"
        if (cfg.maxRecipesPerCoreTick > 0) { // m270 真接线（审计核实三键此前只声明零使用）：全核共享每tick周期预算
            long remain = cfg.maxRecipesPerCoreTick - recipesThisTick;
            if (remain <= 0) cycles = 0;                    // 预算耗尽：本tick不结算，工作量照常累积下tick续（不静默蒸发,m99教训）
            else if (cycles > remain) cycles = (int) remain;
        }
        // m324 区块级预算真接线（maxRecipesPerChunkTick，评审第六优先）：四层=节点cap→核内→区块→全服。
        // 全服申请前按区块余量钳申请（区块封死不去全服排队占饥饿名单）；实批量在全服终裁后才记区块账。
        boolean chunkGate = cfg.maxRecipesPerChunkTick > 0
                && this.level instanceof net.minecraft.server.level.ServerLevel;
        if (cycles > 0 && chunkGate) {
            long head = com.sdzjz.machine.CoreScheduler.chunkHeadroom(
                    this.level.dimension().location().toString(), net.minecraft.world.level.ChunkPos.asLong(this.worldPosition),
                    cfg.maxRecipesPerChunkTick, ((net.minecraft.server.level.ServerLevel) this.level).getServer().getTickCount()); // m366 键/钟版本侧折算
            if (cycles > head) cycles = (int) Math.max(0L, head); // 耗尽同 m270 口径：只欠不丢，工作量累积下tick续
        }
        // m302 全服共享预算真接线（maxRecipesPerNetworkTick）+ 饥饿名单公平层：先核内后全服双层封顶；
        // 耗尽同 m270 口径只欠不丢（工作量累积下tick续），没吃到的核心下tick持保底1周期先食权。
        if (cycles > 0 && cfg.maxRecipesPerNetworkTick > 0
                && this.level instanceof net.minecraft.server.level.ServerLevel sw302) {
            cycles = com.sdzjz.machine.CoreScheduler.request(sw302.dimension().location().toString(), this.worldPosition.asLong(), cycles, cfg.maxRecipesPerNetworkTick, sw302.getServer().getTickCount()); // m366
        }
        if (cycles > 0 && chunkGate) { // m324 终账（先记后裁会把全服拒掉的量虚耗进区块账）
            com.sdzjz.machine.CoreScheduler.chunkCharge(
                    this.level.dimension().location().toString(), net.minecraft.world.level.ChunkPos.asLong(this.worldPosition),
                    cycles, ((net.minecraft.server.level.ServerLevel) this.level).getServer().getTickCount()); // m366
        }
        acc -= (double) cycles * base;
        if (acc > (double) base * cap) acc = (double) base * cap; // 被cap/预算截断时不无限囤积
        if (nodeIndex >= workAcc.length) workAcc = java.util.Arrays.copyOf(workAcc, Math.max(nodeIndex + 1, Math.max(8, workAcc.length * 2))); // m356 写时扩容
        workAcc[nodeIndex] = acc;
        recipesThisTick += cycles;
        if (hadWork && cycles == 0) // m339 预算剪零≠没到周期：亮黄说人话，别静默 continue 装死（m99 教训）
            statR(nodeIndex, 2, "生产预算本拍已满（核内/区块/全服四层之一），工作量已排队下拍续");
        return cycles;
    }

    // ---- m339 经验池公平层状态（transient，不落盘）----
    final java.util.LinkedHashSet<Integer> xpStarved = new java.util.LinkedHashSet<>();
    boolean xpReserveActive;

    /** m339 名单保洁口：仍是"就绪的吃经验机器"才配占保底坑（拆机/换机/暂停/丢目标=出名单）。 */
    boolean xpConsumerReady(int ix) {
        ItemStack st = g.machineNodes.get(ix);
        if (com.sdzjz.node.NodeTags.nodePaused(st)) return false;
        String ct = com.sdzjz.node.NodeTags.craftTarget(st);
        if (st.getItem() instanceof com.sdzjz.item.DuplicatorItem)
            return com.sdzjz.item.DuplicatorItem.validTarget(ct);
        if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem)
            return !ct.isEmpty();
        return false;
    }

    /** m339 经验池闸的唯一过账口：礼让保底期非名单节点吃不到；吃到即销名，吃不到即记名。
     *  返回按池量与保底裁剪后的次数（调用方自带其余封顶）。 */
    long xpGate(int i, long want, int costEach) {
        long got = xpFairDecide(want, (long) (xpPool / Math.max(1, costEach)),
                SdzjzConfig.get().xpFairShare, xpReserveActive, xpStarved.contains(i));
        if (got <= 0) xpStarved.add(i);
        else xpStarved.remove(i);
        return got;
    }

    /** m339 公平裁决纯函数（廿五号用例直测）：礼让期非名单节点吃 0；其余按池量与需求取小。 */
    public static long xpFairDecide(long want, long afford, boolean fairOn, boolean reserveActive, boolean amStarved) {
        if (fairOn && reserveActive && !amStarved) return 0;
        return Math.max(0, Math.min(want, afford));
    }

    // ===== m340 连线喂料的"显式供料线补足"（作者实锤：合成机同时接塔线+仓线，只吃塔的涓流，
    // 仓里 62.6M 金粒看得见吃不着=观感"第二台不生效/卡住"——旧语义 hasIn 与存储二选一）。
    // 新语义：连线喂料优先、**显式**存储供料线补足；隐式网络(resolveInputSource)不搅局；
    // 熔炉族刻意除外（"接什么烧什么"补上仓=误烧库存，m173 防线不动）。开关 supplyTopUp。 =====
    private com.sdzjz.machine.StorageAccess topUpSource(Level world, int i) {
        return com.sdzjz.config.SdzjzConfig.get().supplyTopUp ? supplyFor(world, i) : null;
    }

    private long dualCount(int i, com.sdzjz.machine.StorageAccess exp, String id) {
        return bufCountFor(i, id) + (exp != null ? exp.count(id) : 0L);
    }

    /** 先吃缓存后吃供料线；调用方的量已被 dualCount 夹过，缺口非零时 exp 必非空。 */
    private void dualWithdraw(int i, com.sdzjz.machine.StorageAccess exp, String id, long want) {
        long fromBuf = Math.min(want, bufCountFor(i, id));
        bufWithdrawFor(i, id, fromBuf);
        if (want > fromBuf && exp != null)
            exp.withdraw(id, (int) Math.min(Integer.MAX_VALUE, want - fromBuf));
    }

    /** m137 山羊角入库：8 变体 instrument 组件（原版 GOAT_HORNS 标签枚举，模组扩展自动跟随），
     *  total 均摊到各变体（余数随机加成），逐变体建栈挂组件走 精确账本（m130）或 输出缓存（addOutput 保组件、
     *  maxCount=1 自动一格一支）。注册表异常兜底裸角不吞产量。 */
    private void depositGoatHorns(net.minecraft.world.level.Level world, com.sdzjz.machine.StorageAccess deposit, long total) {
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.item.Instrument>> vars = new java.util.ArrayList<>();
        for (var e : net.minecraft.core.registries.BuiltInRegistries.INSTRUMENT.getTagOrEmpty(
                net.minecraft.tags.InstrumentTags.GOAT_HORNS)) vars.add(e);
        if (vars.isEmpty()) { // 数据包清空标签的兜底：裸角照常入库（普通条目），产量不蒸发
            ItemStack bare = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:goat_horn")),
                    (int) Math.min(total, Integer.MAX_VALUE));
            if (deposit != null) depositOrBuffer(deposit, bare); else addOutput(bare);
            return;
        }
        long base = total / vars.size(), rem = total % vars.size();
        net.minecraft.util.RandomSource rand = world.getRandom();
        long[] share = new long[vars.size()];
        for (int k = 0; k < vars.size(); k++) share[k] = base;
        for (long r = 0; r < rem; r++) share[rand.nextInt(vars.size())]++;
        for (int k = 0; k < vars.size(); k++) {
            if (share[k] <= 0) continue;
            ItemStack horn = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:goat_horn")),
                    (int) Math.min(share[k], Integer.MAX_VALUE));
            horn.set(DataComponents.INSTRUMENT, vars.get(k));
            if (deposit != null) depositOrBuffer(deposit, horn);
            else addOutput(horn);
        }
    }

    /** m139 砂轮回收值：Σ各附魔 getMinPower(等级)（原版砂轮经验同源公式），诅咒跳过；
     *  逐附魔封顶 0.8×工厂成本（B×级×25，B=max(1,anvilCost/2)）——第三方附魔 minPower 再高
     *  也造不成「附魔工厂→砂轮」经验永动泵。空组件/纯诅咒返 0（调用方不收）。 */
    private double grindValue(ItemStack book) {
        net.minecraft.world.item.enchantment.ItemEnchantments comp =
                book.get(DataComponents.STORED_ENCHANTMENTS);
        if (comp == null || comp.isEmpty()) return 0;
        double v = 0;
        for (var en : comp.entrySet()) {
            var entry = en.getKey();
            int lvl = en.getIntValue();
            if (entry.is(net.minecraft.tags.EnchantmentTags.CURSE)) continue;
            net.minecraft.world.item.enchantment.Enchantment e = entry.value();
            double cap = 0.8 * Math.max(1, e.getAnvilCost() / 2) * lvl * 25;
            v += Math.min(e.getMinCost(lvl), cap);
        }
        return v;
    }

    /** m140 砂轮青金石退款：Σ非诅咒附魔等级 × 1（附魔工厂成本 3/级 的 33%——有损回收，
     *  「工厂造书→砂轮回收」每圈净亏 2青金石/级+50%以上经验，无泵）。 */
    private int grindLapis(ItemStack book) {
        net.minecraft.world.item.enchantment.ItemEnchantments comp =
                book.get(DataComponents.STORED_ENCHANTMENTS);
        if (comp == null || comp.isEmpty()) return 0;
        int lv = 0;
        for (var en : comp.entrySet()) {
            if (en.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) continue;
            lv += en.getIntValue();
        }
        return lv;
    }

    private int nodeInt(ItemStack s, String key) {
        return com.sdzjz.node.NodeTags.viewOf(s).getInt(key); // m353 只读免拷贝（生产大循环每节点每tick多次，压测火源）
    }

    private int totalNodeUpgrade(String key) {
        int n = 0;
        for (ItemStack s : g.machineNodes) n += nodeInt(s, key);
        return n;
    }

    /** 从玩家背包扣一个对应升级，加到该节点。 type 0=加速 1=数量 2=并列 */
    /** 领取经验池：直接给玩家经验（画布「领取经验」按钮）。 */
    public void collectXp(Player player) {
        int give = (int) Math.min(xpPool, Integer.MAX_VALUE);
        if (give <= 0) return;
        player.giveExperiencePoints(give);
        xpPool -= give;
        if (level != null) level.playSound(null, worldPosition,
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.0f);
        setChanged();
    }

    /** 读取自动合成机节点的目标产物 id（无则空串）。 */

    // ===== 画布逻辑节点：过滤器 / 数量传感器 =====

    /** m354 机器类型桶判定（每节点每 tick 一次，PHASES 闸内；instanceof 链=纳秒级）。 */
    private static int typeBucket(ItemStack st) {
        if (com.sdzjz.node.NodeTags.isFilter(st) || com.sdzjz.node.NodeTags.isSwitch(st) || com.sdzjz.node.NodeTags.isSensor(st) || com.sdzjz.node.NodeTags.isDistributor(st) || com.sdzjz.node.NodeTags.isExtractor(st) || com.sdzjz.node.NodeTags.isTrash(st)
                || st.getItem() instanceof com.sdzjz.item.VoidProcessorItem) // m378 终端件归逻辑桶
            return com.sdzjz.debug.CoreProfiler.SUB_T_LOGIC;
        if (st.getItem() instanceof AutoCrafterItem) return com.sdzjz.debug.CoreProfiler.SUB_T_CRAFT;
        if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem) return com.sdzjz.debug.CoreProfiler.SUB_T_BREW;
        if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem) return com.sdzjz.debug.CoreProfiler.SUB_T_ENCH;
        if (st.getItem() instanceof com.sdzjz.item.VillagerContractItem) return com.sdzjz.debug.CoreProfiler.SUB_T_TRADE;
        if (st.getItem() instanceof com.sdzjz.item.DuplicatorItem) return com.sdzjz.debug.CoreProfiler.SUB_T_DUP;
        if (st.getItem() instanceof MachineItem) return com.sdzjz.debug.CoreProfiler.SUB_T_MACHINE;
        return com.sdzjz.debug.CoreProfiler.SUB_T_MISC;
    }

    /** m160 抽取节点有效运转态：手动开 且（无感应规则 或 感应放行）。感应键位与传感器
     *  同套（si/sv/sl，sensorOpen 通用判定）——配了监测物品的抽取节点=自带阈值自动启停。 */

    /** m123 融合升阶（up=true：4台同阶→1台高阶，余数还玩家）/ 拆解降阶（1台→4台低阶，超堆叠上限的还玩家）。 */
    public void fuseNode(Player player, int index, boolean up) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if (s.isEmpty() || !(s.getItem() instanceof MachineItem)) return;
        int mt = com.sdzjz.node.NodeTags.machineTier(s);
        if (up) {
            if (mt >= 3) return;
            if (s.getCount() < 4) { // m128(F1)：m78 后 insertMachine 恒为 ×1 节点——不聚敛则单节点永远
                // 凑不满 4，融合出厂即死（m125 审计实锤，本条为丢失代码重建）。跨节点抽调同物品同阶机器。
                index = gatherSame(player, index, 4);
                s = g.machineNodes.get(index);
            }
            if (s.getCount() < 4) { // 全画布凑不齐：聚敛可能已部分发生（同类并栈），照样落盘同步
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "画布上同类同阶机器不足 4 台，无法融合"), true);
                setChanged();
                syncToClient();
                return;
            }
            int rem = s.getCount() % 4, keep = s.getCount() / 4;
            if (rem > 0) { // 余数保持原阶还给玩家（copy 带原 NBT），绝不凭空消失
                ItemStack back = s.copyWithCount(rem);
                if (!player.getInventory().add(back)) player.drop(back, false);
            }
            CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
            n.putInt("mt", mt + 1);
            com.sdzjz.item.ItemData.write(s, n);
            s.setCount(keep);
        } else {
            if (mt <= 0) return;
            CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
            n.putInt("mt", mt - 1);
            com.sdzjz.item.ItemData.write(s, n);
            long c = (long) s.getCount() * 4;
            int nc = (int) Math.min(c, s.getMaxStackSize());
            s.setCount(nc);
            long over = c - nc;
            if (over > 0) { // 超堆叠上限的低阶机还给玩家（copy 已带新阶 NBT）
                ItemStack back = s.copyWithCount((int) over);
                if (!player.getInventory().add(back)) player.drop(back, false);
            }
        }
        setChanged();
        syncToClient();
    }

    /** m128(F1)：跨节点聚敛——从画布其它「同物品同阶」节点抽调机器进 index 节点，凑 need 台。
     *  被抽空的节点：**先读后抽**（栈随 detach 清空后 NBT 即失）——先退还其内嵌升级，再走 detachNode
     *  全套簿记；聚敛无视暂停/升级差异（机器可互换，升级留原节点或退还，m125 设计留痕）。
     *  返回聚敛后 index 的新下标（摘除低位节点会使下标前移）。倒序遍历：detach 只影响更高下标，安全。 */
    private int gatherSame(Player player, int index, int need) {
        ItemStack target = g.machineNodes.get(index);
        int mt = com.sdzjz.node.NodeTags.machineTier(target);
        int idx = index;
        for (int j = g.machineNodes.size() - 1; j >= 0 && target.getCount() < need; j--) {
            if (j == idx) continue;
            ItemStack o = g.machineNodes.get(j);
            if (o.isEmpty() || o.getItem() != target.getItem() || com.sdzjz.node.NodeTags.machineTier(o) != mt) continue;
            int take = Math.min(need - target.getCount(), o.getCount());
            if (take >= o.getCount()) { // 将被抽空：先读后抽——升级退还，再摘节点
                com.sdzjz.node.NodeUpgrades.refundUpgrades(player, com.sdzjz.node.NodeTags.nbtOf(o));
                target.grow(take);
                detachNode(j);
                if (j < idx) idx--; // 摘除低位节点，目标下标前移
            } else {
                o.shrink(take);
                target.grow(take);
            }
        }
        return idx;
    }

    /** m398 全服移除器每拍**墙钟时间池**（所有移除器共享，按服务器 tick 归零；作者报"加速拉满还是慢"
     *  的正解=瓶颈从来不是升级级数而是每拍上限，改成按时间收费才用得上真机器）。跨世界共用一份：
     *  服务器 tick 是全局的，池的语义就是"本拍所有移除器一共可以花多少毫秒"。 */
    private static int remPoolTick = Integer.MIN_VALUE;
    private static long remPoolUsedNs = 0L;

    /** m394 远距送达（作者报"我现在看不到激光特效"的真根因）：原版 `ServerWorld.spawnParticles`
     *  只发**32 格内**的玩家——移除器一个 5×5 区域就 80 格宽，削切面还常在脚下几十上百格深，
     *  站边上看整台机器一个粒子都收不到。改成逐玩家走 force 重载（原版 force=true 判距 512 格），
     *  另自设 192 格门槛省包。m384 的锁定爆发环同病同治。 */
    private static void fxAt(net.minecraft.server.level.ServerLevel sw, net.minecraft.core.particles.ParticleOptions pe,
                             double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (net.minecraft.server.level.ServerPlayer sp : sw.getServer().getPlayerList().getPlayers()) {
            if (sp.level() != sw) continue;
            if (sp.distanceToSqr(x, y, z) > 192.0 * 192.0) continue;
            sw.sendParticles(sp, pe, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** m393 激光雕刻观感（作者点名"运行这个效果能不能像激光雕刻机差不多"）/ m394 加密加长+远距送达：
     *  削切点正上方一道 END_ROD 白热光柱（**一次** spawnParticles 用纵向高斯 dy 铺满柱体=一束，横向
     *  散布近零故是细束）+ 落点白热火花 + 龙息烟羽（切割冒烟）。三次调用/束，每拍封顶四束；END_ROD
     *  粒子寿命约三秒，正好把基础速度下"每 40 拍才动一次"的间隙连成一道常亮的光柱（速度升级越高
     *  越连续）。方向感=光从上方打下来，游标走到哪切到哪。 */
    private static void chunkFxLaser(net.minecraft.server.level.ServerLevel sw, int x, int y, int z) {
        double cx = x + 0.5, cz = z + 0.5;
        fxAt(sw, net.minecraft.core.particles.ParticleTypes.END_ROD, cx, y + 5.0, cz, 20, 0.03, 2.6, 0.03, 0.0); // 光柱：纵向铺约 10 格，横向 0.03=细束
        fxAt(sw, net.minecraft.core.particles.ParticleTypes.END_ROD, cx, y + 0.6, cz, 8, 0.15, 0.06, 0.15, 0.08); // 落点白热火花（带速度=溅开）
        fxAt(sw, net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, cx, y + 0.9, cz, 3, 0.08, 0.02, 0.08, 0.01); // 切割烟羽
    }

    /** m384 区块移除器"技能锁定"爆发：选区顶面周长粒子环（采样步长 4 格封顶 96 点）+中心信标激活音。
     *  只在首铲沿触发（非运行→运行），暂停恢复/过滤续挖也算重新施法——审美上正确，机制上免追状态。 */
    private static void chunkFxBurst(net.minecraft.server.level.ServerLevel sw, int cx, int cz, int r, int yTop) {
        int x1 = (cx - r) << 4, z1 = (cz - r) << 4;
        int side = (2 * r + 1) << 4;
        for (int d = 0; d < side; d += 4) { // 四边同步描点：最大 5×5=80格/边→20点×4边=80≤封顶（m394 全部改远距送达 fxAt，原 32 格内才收得到=站边上看不见自己的施法圈）
            fxAt(sw, net.minecraft.core.particles.ParticleTypes.END_ROD, x1 + d + 0.5, yTop + 1.2, z1 + 0.5, 2, 0.1, 0.3, 0.1, 0.01);
            fxAt(sw, net.minecraft.core.particles.ParticleTypes.END_ROD, x1 + d + 0.5, yTop + 1.2, z1 + side - 0.5, 2, 0.1, 0.3, 0.1, 0.01);
            fxAt(sw, net.minecraft.core.particles.ParticleTypes.END_ROD, x1 + 0.5, yTop + 1.2, z1 + d + 0.5, 2, 0.1, 0.3, 0.1, 0.01);
            fxAt(sw, net.minecraft.core.particles.ParticleTypes.END_ROD, x1 + side - 0.5, yTop + 1.2, z1 + d + 0.5, 2, 0.1, 0.3, 0.1, 0.01);
        }
        net.minecraft.core.BlockPos ctr = new net.minecraft.core.BlockPos(x1 + side / 2, yTop, z1 + side / 2);
        sw.playSound(null, ctr, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.25f);
    }

    /** m388 封边挡水判定：区域边界位 (bx,y,bz) 的外侧贴邻（西/东/北/南按所在面取，角=两面）是否有
     *  流体——getFluidState 非空=水/岩浆/含水方块通吃。水不斜流不上流，四横邻足够，无需查上下。 */
    private static boolean chunkSealNeeded(net.minecraft.world.level.Level world, int bx, int y, int bz,
                                           int minBx, int maxBx, int minBz, int maxBz) {
        if (bx == minBx && chunkSealFluidAt(world, bx - 1, y, bz)) return true;
        if (bx == maxBx && chunkSealFluidAt(world, bx + 1, y, bz)) return true;
        if (bz == minBz && chunkSealFluidAt(world, bx, y, bz - 1)) return true;
        return bz == maxBz && chunkSealFluidAt(world, bx, y, bz + 1);
    }

    /** m388 外邻流体探针：外邻分块未加载=按无流体处理，绝不触发同步加载（m142 毒区块票同系警惕：
     *  Level.getFluidState 落在未加载区块会强载）。未加载分块不 tick 也灌不进水，等它加载后水若
     *  贴上来，玩家重扫（重开勾选/重绑）即可补封。 */
    private static boolean chunkSealFluidAt(net.minecraft.world.level.Level world, int x, int y, int z) {
        if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) return false;
        return !world.getFluidState(new BlockPos(x, y, z)).isEmpty();
    }

    /** 切换节点 暂停/运行（m110b）。 */
    public void togglePause(int index) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if (s.isEmpty()) return;
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        n.putBoolean("np", !com.sdzjz.node.NodeTags.nodePaused(s));
        com.sdzjz.item.ItemData.write(s, n);
        setChanged();
        syncToClient();
    }

    /** 切换开关节点 开/关。 */
    public void toggleSwitch(int index) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        if (com.sdzjz.node.NodeTags.isSwitch(s)) n.putBoolean("so", !com.sdzjz.node.NodeTags.switchOn(s));
        else if (com.sdzjz.node.NodeTags.isExtractor(s)) n.putBoolean("xo", !com.sdzjz.node.NodeTags.extractorOn(s)); // m154 抽取启停走同一收包口
        else return;
        com.sdzjz.item.ItemData.write(s, n);
        setChanged();
        syncToClient();
    }

    /** 加/移一条过滤名单项（已在名单=移除）；id 为空串=切换 白名单↔黑名单。 */
    public void toggleFilterEntry(int index, String id) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if ("#cr".equals(id) && s.getItem() instanceof AutoCrafterItem) { // m235 配方换挡复用此收包口（#xr 同款哨兵工艺）：
            // 自动(-1)→候选0→候选1→…→回自动；候选序=CraftPlanner.plans 原版排前+id字典序，双端同源循环稳定
            String tgt = com.sdzjz.node.NodeTags.craftTarget(s);
            java.util.List<CraftPlanner.Plan> ps = tgt.isEmpty() ? java.util.List.of() : CraftPlanner.plans(level, tgt);
            CompoundTag nc = com.sdzjz.node.NodeTags.nbtOf(s);
            String cur = nc.contains("cr") ? nc.getString("cr") : "";
            int at = -1;
            for (int k = 0; k < ps.size(); k++) if (ps.get(k).recipeId().equals(cur)) { at = k; break; }
            int nxt = at + 1;
            if (nxt >= ps.size()) nc.remove("cr"); else nc.putString("cr", ps.get(nxt).recipeId());
            com.sdzjz.item.ItemData.write(s, nc);
            setChanged();
            syncToClient();
            return;
        }
        if ("#xr".equals(id) && com.sdzjz.node.NodeTags.isExtractor(s)) { // m159 抽取量换挡复用此收包口；m163a 扩至五挡（用户点名"还是太少"）
            CompoundTag nx = com.sdzjz.node.NodeTags.nbtOf(s);
            long cur = com.sdzjz.node.NodeTags.extractorRate(s);
            nx.putLong("xr", cur == 64 ? 512 : cur == 512 ? 4096 : cur == 4096 ? 32768 : cur == 32768 ? 262144 : 64);
            com.sdzjz.item.ItemData.write(s, nx);
            setChanged();
            syncToClient();
            return;
        }
        if ("#zy".equals(id) && s.getItem() instanceof com.sdzjz.item.ChunkFilterItem) { // m377 Y 挡循环复用此收包口（#xr 同款哨兵工艺）：全高度→地表下→深层→深板岩→地上→回全高度
            CompoundTag nz = com.sdzjz.node.NodeTags.nbtOf(s);
            nz.putInt("zp", (com.sdzjz.item.ChunkFilterItem.preset(s) + 1) % com.sdzjz.item.ChunkFilterItem.PRESETS);
            com.sdzjz.item.ItemData.write(s, nz);
            setChanged();
            syncToClient();
            return;
        }
        if (id != null && id.startsWith("#zrd:") && s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem) { // m386 区域自由调（替 m382 三挡循环）：带符号增量，服务端钳 0..上限；变更=新工程重扫 zn 保留
            int dR;
            try { dR = Integer.parseInt(id.substring(5)); } catch (NumberFormatException e) { return; }
            if (dR < -1024 || dR > 1024) return; // 伪造包尺寸熔断
            CompoundTag nr = com.sdzjz.node.NodeTags.nbtOf(s);
            int capR = Math.max(0, SdzjzConfig.get().chunkRemoverMaxRadius);
            int nv = Math.max(0, Math.min(Math.max(0, nr.getInt("zr")) + dR, capR));
            if (nv != nr.getInt("zr")) {
                nr.putInt("zr", nv);
                nr.putInt("zy", level != null ? level.getMaxBuildHeight() - 1 : 319);
                nr.putInt("zi", 0);
                nr.putInt("zc", 0);
                nr.remove("zf");
                nr.remove("zq"); // m390 湿账随新工程归零
                com.sdzjz.item.ItemData.write(s, nr);
                setChanged();
                syncToClient();
            }
            return;
        }
        if (("#bfx".equals(id) || "#bfl".equals(id)) && s.getItem() instanceof com.sdzjz.item.InfiniteBeaconItem) { // m399 效果/等级循环
            CompoundTag nbf = com.sdzjz.node.NodeTags.nbtOf(s);
            if ("#bfx".equals(id)) nbf.putInt("bfx", com.sdzjz.item.InfiniteBeaconItem.nextEffect(nbf.getInt("bfx")));
            else nbf.putInt("bfl", nbf.getInt("bfl") >= 1 ? 0 : 1);
            com.sdzjz.item.ItemData.write(s, nbf);
            setChanged();
            syncToClient();
            return;
        }
        if ("#zsbd".equals(id) && s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem) { // m396 封边材料回默认（免费石头）
            CompoundTag nb = com.sdzjz.node.NodeTags.nbtOf(s);
            nb.remove("zsb");
            com.sdzjz.item.ItemData.write(s, nb);
            setChanged();
            syncToClient();
            return;
        }
        if ("#zm".equals(id) && s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem) { // m386 掉落模式切换（不动游标，中途可切）
            CompoundTag nm = com.sdzjz.node.NodeTags.nbtOf(s);
            nm.putInt("zm", com.sdzjz.item.ChunkRemoverItem.nextMode(nm.getInt("zm"),
                    com.sdzjz.config.SdzjzConfig.get().chunkRemoverVoidMode)); // m397 三挡循环
            com.sdzjz.item.ItemData.write(s, nm);
            setChanged();
            syncToClient();
            return;
        }
        if ("#zw".equals(id) && s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem) { // m388 封边挡水切换（开=重扫补封：已挖开的边界回补玻璃墙、灌进的水按普通块清；关不动游标）
            CompoundTag nw = com.sdzjz.node.NodeTags.nbtOf(s);
            boolean sealOnW = nw.getInt("zw") == 2; // m394 三态：切换后的新状态（原为关=2 则开）
            nw.putInt("zw", sealOnW ? 1 : 2);
            if (sealOnW) { // 开堵水=新工程重扫（zn 总账保留，#zrd 同口径）
                nw.putInt("zy", level != null ? level.getMaxBuildHeight() - 1 : 319);
                nw.putInt("zi", 0);
                nw.putInt("zc", 0);
                nw.remove("zf");
                nw.remove("zq"); // m390
            }
            com.sdzjz.item.ItemData.write(s, nw);
            setChanged();
            syncToClient();
            return;
        }
        if ("#zs".equals(id) && s.getItem() instanceof com.sdzjz.item.ChunkScannerItem) { // m380 重新扫描（#zy 同款哨兵工艺）
            com.sdzjz.item.ChunkScannerItem.resetScan(s, level != null ? level.getMaxBuildHeight() - 1 : 319);
            setChanged();
            syncToClient();
            return;
        }
        boolean chunkF = s.getItem() instanceof com.sdzjz.item.ChunkFilterItem; // m377 区块过滤器：名单+黑白切换全套复用过滤节点收包口
        boolean voidP = s.getItem() instanceof com.sdzjz.item.VoidProcessorItem; // m378 虚空处理器：白名单复用（永远白名单无黑白，垃圾桶同律）
        if (!com.sdzjz.node.NodeTags.isFilter(s) && !com.sdzjz.node.NodeTags.machineFilterable(s) && !com.sdzjz.node.NodeTags.isExtractor(s) && !com.sdzjz.node.NodeTags.isTrash(s) && !chunkF && !voidP) return;
        // m149 机器加工过滤 / m160 抽取白名单+垃圾桶白名单（安全桶）同走此口
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        if (id == null || id.isEmpty()) {
            if (!com.sdzjz.node.NodeTags.isFilter(s) && !chunkF) return; // 机器侧永远白名单，无黑白切换（m377 区块过滤器有黑白）
            n.putBoolean("fb", !n.getBoolean("fb"));
        } else {
            ListTag l = n.getList("fl", Tag.TAG_STRING);
            boolean removed = false;
            for (int k = 0; k < l.size(); k++)
                if (l.getString(k).equals(id)) { l.remove(k); removed = true; break; }
            if (!removed) {
                if (l.size() >= 64) return; // 名单封顶，防 NBT 膨胀
                l.add(net.minecraft.nbt.StringTag.valueOf(id));
            }
            n.put("fl", l);
        }
        com.sdzjz.item.ItemData.write(s, n);
        setChanged();
        syncToClient();
    }

    /** 设置传感器：监测物品 + 阈值 + 方向（低于/高于放行）。 */
    public void setSensorConfig(int index, String id, long threshold, boolean less) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if (!com.sdzjz.node.NodeTags.isSensor(s) && !com.sdzjz.node.NodeTags.isExtractor(s)) return; // m160 抽取节点内置自动启停同走此口
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        if ("§clear".equals(id)) n.remove("si"); // m160 清除感应（传感/抽取通用）
        else if (id != null && !id.isEmpty()) n.putString("si", id);
        n.putLong("sv", Math.max(0, Math.min(1_000_000_000_000L, threshold)));
        n.putBoolean("sl", less);
        com.sdzjz.item.ItemData.write(s, n);
        setChanged();
        syncToClient();
    }

    /** 传感器闸门是否放行：未配置=直通；否则按监测库存量与阈值比较。
     *  监测目标：连了 存储→传感器 供料线=监测那个库；否则=默认主存储（绑定>有线>无线>卫星）。 */

    /** 该节点的全部出线目标是否都是「关闸的传感器」——是则上游整台暂停（不白产不塞存储）。 */

    // ===== 节点状态灯：0=待机 1=正常(绿) 2=阻塞/关闸(黄) 3=缺料(红)。每 20t 有变化才同步（字段在 g） =====
    private boolean statusDirty = false;

    void stat(int i, int v) {
        while (g.nodeStatus.size() < g.machineNodes.size()) g.nodeStatus.add(0);
        while (g.nodeReason.size() < g.machineNodes.size()) g.nodeReason.add(""); // m178
        if (v == 1 && i >= 0 && i < g.nodeReason.size() && !g.nodeReason.get(i).isEmpty()) { g.nodeReason.set(i, ""); statusDirty = true; } // m178 转绿清因
        if (i < 0 || i >= g.nodeStatus.size() || g.nodeStatus.get(i) == v) return;
        g.nodeStatus.set(i, v);
        statusDirty = true;
    }

    // ===== m178 阻塞原因（错误解释）：与 nodeStatus 平行同步，卡面黄/红灯常显人话原因（字段在 g） =====
    /** 设状态并附原因（原因变化也触发同步）。 */
    void statR(int i, int v, String why) {
        while (g.nodeReason.size() < g.machineNodes.size()) g.nodeReason.add("");
        if (i >= 0 && i < g.nodeReason.size() && !g.nodeReason.get(i).equals(why)) { g.nodeReason.set(i, why); statusDirty = true; }
        stat(i, v);
    }
    /** 画布读取阻塞原因（客户端）。 */
    public String nodeReason(int i) { return i >= 0 && i < g.nodeReason.size() ? g.nodeReason.get(i) : ""; }
    private static String itemName(String id) {
        try { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))).getHoverName().getString(); } catch (Exception e) { return id; }
    }
    /** m343 候选组口径缺料报告：第一处"组内全部替代材料合计仍不够单次"的槽位（报首选名，量=候选合计）。 */
    private String whyMissingPlan(CraftPlanner.Plan p, java.util.function.ToLongFunction<String> stock, String scope) {
        CraftPlanner.Missing m = CraftPlanner.firstMissing(p, stock);
        return m != null ? "缺料：" + itemName(m.id()) + "（" + scope + " " + m.have() + "/需 " + m.need() + "）"
                         : "缺料（" + scope + "不足）";
    }
    /** 找配方需求里第一项不够的（缓存口径）。 */
    private String whyMissingBuf(int node, java.util.Map<String, Integer> needs) {
        for (var en : needs.entrySet()) { long have = bufCountFor(node, en.getKey());
            if (have < en.getValue()) return "缺料：" + itemName(en.getKey()) + "（缓存 " + have + "/需 " + en.getValue() + "）"; }
        return "缺料（缓存不足）";
    }
    /** 找配方需求里第一项不够的（仓口径）。 */
    private String whyMissingSup(com.sdzjz.machine.StorageAccess sup, java.util.Map<String, Integer> needs) {
        for (var en : needs.entrySet()) { long have = sup.count(en.getKey());
            if (have < en.getValue()) return "缺料：" + itemName(en.getKey()) + "（仓 " + have + "/需 " + en.getValue() + "）"; }
        return "缺料（仓不足）";
    }
    private String whyMissingBufIn(int node, java.util.List<MachineDef.Input> ins, int running) {
        for (MachineDef.Input in : ins) { long have = bufCountFor(node, in.item()); long need = (long) in.count() * running;
            if (have < need) return "缺料：" + itemName(in.item()) + "（缓存 " + have + "/需 " + need + "）"; }
        return "缺料（缓存不足）";
    }
    private String whyMissingSupIn(com.sdzjz.machine.StorageAccess sup, java.util.List<MachineDef.Input> ins, int running) {
        for (MachineDef.Input in : ins) { long have = sup.count(in.item()); long need = (long) in.count() * running;
            if (have < need) return "缺料：" + itemName(in.item()) + "（仓 " + have + "/需 " + need + "）"; }
        return "缺料（仓不足）";
    }

    /** 画布读取节点状态（客户端）。 */
    public int nodeStatus(int i) {
        return i >= 0 && i < g.nodeStatus.size() ? g.nodeStatus.get(i) : 0;
    }

    /** 设置自动合成机节点的目标产物（画布徽章点选，走 NodeTargetPayload）。 */
    public void setNodeTarget(int index, String id) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        boolean cropOk = s.getItem() instanceof com.sdzjz.item.CropFarmItem && com.sdzjz.machine.CropFarms.has(id);
        boolean brewOk = s.getItem() instanceof com.sdzjz.item.BrewingTowerItem
                && com.sdzjz.machine.BrewPlanner.targetStack(id) != null; // m131b 目标串服务端校验
        boolean enchOk = s.getItem() instanceof com.sdzjz.item.EnchantFactoryItem
                && com.sdzjz.machine.EnchantPlanner.targetStack(this.level, id) != null; // m132 目标串服务端校验
        boolean tradeOk = s.getItem() instanceof com.sdzjz.item.VillagerTraderItem
                && com.sdzjz.machine.TradePlanner.valid(id); // m146 目标串服务端校验
        boolean dupOk = s.getItem() instanceof com.sdzjz.item.DuplicatorItem
                && com.sdzjz.item.DuplicatorItem.validTarget(id); // m334 目标=物品id 服务端校验
        boolean sealOk = s.getItem() instanceof com.sdzjz.item.ChunkRemoverItem
                && com.sdzjz.item.ChunkRemoverItem.validSealBlock(id); // m396 封边材料（移除器的 setNodeTarget 槽 m376 起本就空着=复用零新协议；服务端校验"必须有方块形态"）
        if (!(s.getItem() instanceof AutoCrafterItem) && !cropOk && !brewOk && !enchOk && !tradeOk && !dupOk && !sealOk) return;
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        if (sealOk) { // m396 封边材料：单选写 zsb（清回默认走菜单 #zsbd 哨兵）
            n.putString("zsb", id);
        } else if (cropOk) { // m93 多选 toggle：在列表则移除，否则加入（≤8）；旧单选 ct 自动并入
            java.util.List<String> cur = com.sdzjz.node.NodeTags.cropList(s);
            if (cur.contains(id)) cur.remove(id);
            else if (cur.size() < 8) cur.add(id);
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (String c : cur) l.add(net.minecraft.nbt.StringTag.valueOf(c));
            n.put("crops", l);
            n.remove("ct");
        } else {
            n.putString("ct", id);
            n.remove("cr"); // m235 换目标即回"自动"（旧手选配方不属于新目标）
        }
        com.sdzjz.item.ItemData.write(s, n);
        setChanged();
        syncToClient();
    }

    public boolean addNodeUpgrade(Player player, int index, int type) {
        if (!addNodeUpgradeRaw(player, index, type)) return false;
        syncNow();
        return true;
    }

    /** m128(F3)：无同步内核——批量接收器循环用（此前 Shift 批量 64 连发=一 tick 64 次全量 BE 同步瞬卡），
     *  循环结束由调用方 syncNow() 一次。 */
    public boolean addNodeUpgradeRaw(Player player, int index, int type) {
        if (index < 0 || index >= g.machineNodes.size()) return false;
        Item item = com.sdzjz.node.NodeUpgrades.upgradeItem(type);
        String key = com.sdzjz.node.NodeUpgrades.upgradeKey(type);
        if (item == null || !consumeFromInv(player, item)) return false;
        ItemStack s = g.machineNodes.get(index);
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putInt(key, n.getInt(key) + 1);
        com.sdzjz.item.ItemData.write(s, n);
        return true;
    }

    /** 从该节点取回一个升级还给玩家。 */
    public boolean removeNodeUpgrade(Player player, int index, int type) {
        if (!removeNodeUpgradeRaw(player, index, type)) return false;
        syncNow();
        return true;
    }

    /** m128(F3)：无同步内核（同 addNodeUpgradeRaw）。 */
    public boolean removeNodeUpgradeRaw(Player player, int index, int type) {
        if (index < 0 || index >= g.machineNodes.size()) return false;
        Item item = com.sdzjz.node.NodeUpgrades.upgradeItem(type);
        String key = com.sdzjz.node.NodeUpgrades.upgradeKey(type);
        if (item == null) return false;
        ItemStack s = g.machineNodes.get(index);
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        int lv = n.getInt(key);
        if (lv <= 0) return false;
        n.putInt(key, lv - 1);
        com.sdzjz.item.ItemData.write(s, n);
        if (!player.getInventory().add(new ItemStack(item))) player.drop(new ItemStack(item), false);
        return true;
    }

    /** m128(F3)：落盘+全量同步一次（批量收包器循环后调用；单发包装方法内部也走它）。 */
    public void syncNow() {
        setChanged();
        syncToClient();
    }

    private boolean consumeFromInv(Player player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) { s.shrink(1); return true; }
        }
        return false;
    }

    /** 读节点画布坐标（无则给默认）。 */
    public int nodeX(ItemStack s, int def) {
        CompoundTag n = com.sdzjz.node.NodeTags.viewOf(s); // m353 只读免拷贝（客户端每卡每帧+服务端布局）
        return n.contains("nx") ? n.getInt("nx") : def;
    }

    public int nodeY(ItemStack s, int def) {
        CompoundTag n = com.sdzjz.node.NodeTags.viewOf(s); // m353 只读免拷贝
        return n.contains("ny") ? n.getInt("ny") : def;
    }

    /** 设置某节点画布坐标（服务端会同步客户端；客户端调用仅本地视觉）。 */
    public void setNodePos(int index, int nx, int ny) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putInt("nx", clampCanvas(nx)); // m269 与存储节点(m265)同幅钳制——审计点名"单节点移动直接接受任意32位整数写入NBT"
        n.putInt("ny", clampCanvas(ny));
        com.sdzjz.item.ItemData.write(s, n);
        setChanged();
        syncToClient();
    }

    /** 右键把升级放入升级槽。 */
    public boolean insertUpgrade(ItemStack held) { return insertInto(held, UPGRADE_START, UPGRADE_START + UPGRADE_SLOTS); }

    private boolean insertInto(ItemStack held, int start, int end) {
        boolean changed = false;
        for (int i = start; i < end && !held.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, held)) {
                int move = Math.min(s.getMaxStackSize() - s.getCount(), held.getCount());
                if (move > 0) { s.grow(move); held.shrink(move); changed = true; }
            }
        }
        for (int i = start; i < end && !held.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                int move = Math.min(held.getMaxStackSize(), held.getCount());
                items.set(i, held.copyWithCount(move)); held.shrink(move); changed = true;
            }
        }
        if (changed) setChanged();
        return changed;
    }

    /** 右键取出指定节点：内嵌升级折成物品归还，机器本体清掉画布 NBT（可正常堆叠）。 */
    public void removeNodeAt(Player player, int index) {
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = detachNode(index);
        returnNodeClean(player, s);
        setChanged();
        syncToClient();
    }

    /** m128(F1)：摘除节点的全套簿记（谨慎重构：逐字搬运自 removeNodeAt 原逻辑）——机器线重映射+
     *  存储线剪/移位+在途缓存并遗留池+状态位；返回被摘的节点栈。不含归还与同步，调用方自理。
     *  removeNodeAt / ejectOne / 融合聚敛三处共用，双写漂移归零。 */
    private ItemStack detachNode(int index) {
        nodeBuf(g.machineNodes.size() - 1); // 先补齐对齐
        ItemStack s = g.machineNodes.remove(index);
        bumpTopo(); // m179（本方法随后还重写 connections，一次 bump 覆盖）
        if (index < nodeBufs.size()) mergeLegacy(nodeBufs.remove(index)); // 在途物品回遗留池，不丢
        if (index < g.nodeStatus.size()) g.nodeStatus.remove(index);
        if (index < g.nodeReason.size()) g.nodeReason.remove(index); // m178
        java.util.List<int[]> kept = new java.util.ArrayList<>();
        for (int[] c : g.connections) {
            if (c[0] == index || c[1] == index) continue; // 触及被删节点→断
            int a = c[0] > index ? c[0] - 1 : c[0];
            int b = c[1] > index ? c[1] - 1 : c[1];
            kept.add(new int[]{a, b});
        }
        g.connections.clear();
        g.connections.addAll(kept);
        for (int i = g.storageEdges.size() - 1; i >= 0; i--) { // 存储连线同样剪/移位
            long[] e = g.storageEdges.get(i);
            if (e[0] == index) { g.storageEdges.remove(i); g.storageEdgeDims.remove(i); }
            else if (e[0] > index) e[0]--;
        }
        g.sweepGroups(); // m191 组标记随被摘的栈自然离场（returnNodeClean 会剥画布 NBT）；这里只清剩<2台的组（m506 下沉共用，见 CanvasGraphState）
        return s;
    }

    /** 归还节点：先把嵌在 NBT 里的升级折成升级物品还给玩家，再清掉画布数据返还机器本体。 */
    private void returnNodeClean(Player player, ItemStack s) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        int mt = n.getInt("mt"); // m128(F2)：先读后抹——阶位是机器本体属性不是画布数据
        com.sdzjz.node.NodeUpgrades.refundUpgrades(player, n);
        com.sdzjz.item.ItemData.clear(s);
        if (mt > 0) { // m128(F2)：重挂纯 {"mt"}——取出 GM 仍是 GM，再放回经 insertMachine copy 自然携带；
            // 同阶同物品可堆叠、异阶 CUSTOM_DATA 不同天然不混栈（原版机制白拿）。此前一刀抹全=511 台凭空蒸发。
            CompoundTag keep = new CompoundTag();
            keep.putInt("mt", mt);
            com.sdzjz.item.ItemData.write(s, keep);
        }
        if (!player.getInventory().add(s)) player.drop(s, false);
    }

    /** 潜行空手右键：先弹出最后一个机器节点，其次弹升级。 */
    public void ejectOne(Player player) {
        if (!g.machineNodes.isEmpty()) {
            // m128：改共用 detachNode（末位节点无更高下标需要移位，与原 removeIf 写法等价，三写归一）
            ItemStack s = detachNode(g.machineNodes.size() - 1);
            returnNodeClean(player, s);
            setChanged();
            syncToClient();
            return;
        }
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) if (pop(player, i)) return;
    }

    /** 画布连线读取（客户端渲染）。 */
    public java.util.List<int[]> connections() { return g.connections; }

    // ===== m191 画布分组：成员归属在节点栈 NBT "gp"（随栈走，detachNode 的下标移位天然无关），
    // 这里只管 id→名元数据 + 组操作；配置 canvasGroupsEnabled 总开关在接收器侧把门。 =====
    // m506（真移植 A5a）：六个业务方法（createGroup/dissolveGroup/renameGroup/moveGroup/setNodeGroupTag/
    // sweepGroups，原 2467~2540 共 75 行）整段搬进 xplat node/CanvasGraphState 两代共用，本处退役为一行转发；
    // setChanged()+syncToClient() 收尾走 g 的构造器回调（字段声明处）。私有的 setNodeGroupTag/sweepGroups
    // 随行删除（第 19 闸已登记），detachNode 改调 g.sweepGroups()。
    /** 分组元数据读取（客户端渲染组框标题用）。 */
    public java.util.Map<Integer, String> groupsView() { return g.groupNames; }

    /** 建组：≥2 个合法下标才成组；成员先脱旧组再入新组（一台机器只能在一个组）。name 空=自动"组N"。 */
    public void createGroup(java.util.List<Integer> members, String name) { g.createGroup(members, name); }

    /** 解散组：成员脱组标记 + 元数据删除。机器/连线原样不动（分组纯视觉，不碰拓扑）。 */
    public void dissolveGroup(int gid) { g.dissolveGroup(gid); }

    /** 重命名组（长度钳 24，空名不接受）。 */
    public void renameGroup(int gid, String name) { g.renameGroup(gid, name); }

    /** 组整体位移：全成员坐标加同一增量，改完只同步一次（防 m128F3 式 N 连发全量同步）。 */
    public void moveGroup(int gid, int dx, int dy) { g.moveGroup(gid, dx, dy); }

    // ===== 存储/终端接口节点：扫描 + 定向连线 =====
    /** 扫描本核心可达的存储核心/数据终端端点（绑定>有线>无线>卫星，封顶8个），变化才同步。 */
    /** m321 计时壳。 */
    private void scanStorageEndpoints(Level world, BlockPos corePos) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) { scanStorageEndpoints0(world, corePos); return; }
        long __t = System.nanoTime();
        try { scanStorageEndpoints0(world, corePos); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_ENDPOINT, System.nanoTime() - __t); }
    }

    private void scanStorageEndpoints0(Level world, BlockPos corePos) {
        long __sd = com.sdzjz.debug.CoreProfiler.PHASES ? System.nanoTime() : 0; // m357 三段账（审计⑤轮⑤：为 StorageCore revision 决策供数）
        java.util.LinkedHashMap<Long, long[]> found = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<Long, String> dims = new java.util.LinkedHashMap<>();
        String selfDim = world.dimension().location().toString();
        found.put(OUTPUT_IFACE, new long[]{OUTPUT_IFACE, 6}); // 常驻输出接口，永不被封顶挤掉
        dims.put(OUTPUT_IFACE, selfDim);
        // 绑定目标（优先级最高，可跨维度）
        StorageCoreBlockEntity bound = boundPanel(world, corePos);
        if (bound != null && bound.getLevel() != null) {
            long pl = bound.getBlockPos().asLong();
            found.put(pl, new long[]{pl, 0});
            dims.put(pl, bound.getLevel().dimension().location().toString());
        }
        // 有线：BFS 收集全部存储核心 + 数据终端
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos); seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                BlockEntity nbe = world.getBlockEntity(np);
                if (nbe instanceof StorageCoreBlockEntity && found.size() < ENDPOINT_CAP) {
                    long pl = np.asLong();
                    found.putIfAbsent(pl, new long[]{pl, 1});
                    dims.putIfAbsent(pl, selfDim);
                } else if (nbe instanceof DataPanelBlockEntity && found.size() < ENDPOINT_CAP) {
                    long pl = np.asLong();
                    found.putIfAbsent(pl, new long[]{pl, 5});
                    dims.putIfAbsent(pl, selfDim);
                }
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        // 无线：范围内已加载的存储核心
        if (found.size() < ENDPOINT_CAP && hasWirelessNode(world, corePos)) {
            long range = SdzjzConfig.get().wirelessRange, r2 = range * range;
            for (BlockPos p : StorageCoreBlockEntity.coresNear(world, corePos, range)) { // m279 空间索引
                if (found.size() >= ENDPOINT_CAP) break;
                long dx = p.getX() - corePos.getX(), dy = p.getY() - corePos.getY(), dz = p.getZ() - corePos.getZ();
                if (dx * dx + dy * dy + dz * dz > r2) continue;
                if (StorageCoreBlockEntity.loadedCoreAt(world, p) == null) continue;
                long pl = p.asLong();
                found.putIfAbsent(pl, new long[]{pl, 2});
                dims.putIfAbsent(pl, selfDim);
            }
        }
        // 卫星：全维度（先本维度，后其它）
        if (found.size() < ENDPOINT_CAP && hasSatelliteNode(world, corePos)) {
            for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(world))) {
                if (found.size() >= ENDPOINT_CAP) break;
                if (StorageCoreBlockEntity.loadedCoreAt(world, p) == null) continue;
                long pl = p.asLong();
                found.putIfAbsent(pl, new long[]{pl, 3});
                dims.putIfAbsent(pl, selfDim);
            }
            if (world instanceof net.minecraft.server.level.ServerLevel sw) {
                for (var key : java.util.List.copyOf(StorageCoreBlockEntity.dimensionsWithCores())) {
                    if (found.size() >= ENDPOINT_CAP || key.equals(world.dimension())) continue;
                    net.minecraft.server.level.ServerLevel ow = sw.getServer().getLevel(key);
                    if (ow == null) continue;
                    for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(ow))) {
                        if (found.size() >= ENDPOINT_CAP) break;
                        if (StorageCoreBlockEntity.loadedCoreAt(ow, p) == null) continue;
                        long pl = p.asLong();
                        found.putIfAbsent(pl, new long[]{pl, 3});
                        dims.putIfAbsent(pl, key.location().toString());
                    }
                }
            }
        }
        // 被连线引用但没扫到的端点：显示为离线（不丢用户的接线）；本维度已加载但方块没了→剪掉连线
        java.util.Iterator<long[]> it = g.storageEdges.iterator();
        java.util.Iterator<String> itd = g.storageEdgeDims.iterator();
        boolean edgesPruned = false;
        while (it.hasNext()) {
            long[] e = it.next();
            String edim = itd.next();
            if (found.containsKey(e[1])) continue;
            if (edim.equals(selfDim)) {
                BlockPos ep = BlockPos.of(e[1]);
                if (world.getChunkSource().hasChunk(ep.getX() >> 4, ep.getZ() >> 4)
                        && !(world.getBlockEntity(ep) instanceof StorageCoreBlockEntity)) {
                    it.remove(); itd.remove(); edgesPruned = true; // 方块确实没了
                    continue;
                }
            }
            found.putIfAbsent(e[1], new long[]{e[1], 4}); // 离线占位
            dims.putIfAbsent(e[1], edim);
        }
        // 变化才写回+同步（省网络）
        boolean changed = edgesPruned || found.size() != g.storageEndpoints.size();
        if (!changed) {
            for (int i = 0; i < g.storageEndpoints.size(); i++) {
                long[] old = g.storageEndpoints.get(i);
                long[] neu = found.get(old[0]);
                if (neu == null || neu[1] != old[1]) { changed = true; break; }
            }
        }
        if (changed) {
            g.storageEndpoints.clear();
            g.storageEndpointDims.clear();
            java.util.List<long[]> ordered = new java.util.ArrayList<>(found.values());
            // m80：分组排序（输出接口→存储核心→数据面板），客户端按组编号"存储1/2…、数据面板1/2…"
            ordered.sort(java.util.Comparator.comparingInt(v -> v[1] == 6 ? 0 : v[1] == 5 ? 2 : 1));
            for (long[] v : ordered) {
                g.storageEndpoints.add(v);
                g.storageEndpointDims.add(dims.get(v[0]));
            }
            g.storageNodePos.keySet().retainAll(found.keySet()); // 修剪已消失端点的画布坐标
        }
        // ===== m85：总线库存聚合（只数存储核心；面板聚合的是同一批核心，数它会重复计）=====
        long __sa = 0;
        if (com.sdzjz.debug.CoreProfiler.PHASES) { __sa = System.nanoTime(); com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_SCAN_DISC, __sa - __sd); }
        java.util.LinkedHashMap<String, Long> agg = new java.util.LinkedHashMap<>();
        for (long[] v : found.values()) {
            if (v[1] == 4 || v[1] == 5 || v[1] == 6) continue;
            Level tw = resolveDimWorld(world, dims.get(v[0]));
            if (tw == null) continue;
            BlockPos bp = BlockPos.of(v[0]);
            if (!tw.getChunkSource().hasChunk(bp.getX() >> 4, bp.getZ() >> 4)) continue;
            if (tw.getBlockEntity(bp) instanceof StorageCoreBlockEntity sc) {
                for (var en : sc.storeView().entrySet()) agg.merge(en.getKey(), en.getValue(), StorageCoreBlockEntity::satAdd); // m273
                // m163b：精确账本条目按 id 并入——山羊角(乐器组件)/附魔书全在精确账本，不并的话
                // 总线库存看不见它们、抽取白名单选择器（候选=网络现有）也列不出，m155 的招牌用例直接失明。
                java.util.List<ItemStack> tplB = sc.exactTemplates();
                for (int k = 0; k < tplB.size(); k++)
                    agg.merge(BuiltInRegistries.ITEM.getKey(tplB.get(k).getItem()).toString(), sc.exactCount(k), StorageCoreBlockEntity::satAdd); // m273
            }
        }
        long __ss = 0;
        if (com.sdzjz.debug.CoreProfiler.PHASES) { __ss = System.nanoTime(); com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_SCAN_AGG, __ss - __sa); }
        java.util.List<java.util.Map.Entry<String, Long>> top = new java.util.ArrayList<>(agg.entrySet());
        top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        // m163b：前10→前400。总线条按可用宽度自截断（超宽画"…"），多带的部分喂给抽取白名单
        // 选择器当候选源（客户端 busIdsCache 复用，零新协议）；400 封顶防极端档 NBT/包体失控。
        if (top.size() > 400) top = new java.util.ArrayList<>(top.subList(0, 400));
        java.util.List<String> nIds = new java.util.ArrayList<>();
        java.util.List<Long> nCts = new java.util.ArrayList<>();
        for (var en : top) { nIds.add(en.getKey()); nCts.add(en.getValue()); }
        boolean busChanged = !nIds.equals(g.busTopIds) || !nCts.equals(g.busTopCounts);
        if (busChanged) {
            g.busTopIds.clear(); g.busTopIds.addAll(nIds);
            g.busTopCounts.clear(); g.busTopCounts.addAll(nCts);
        }
        if (changed || busChanged) {
            setChanged();
            syncToClient();
        }
        if (com.sdzjz.debug.CoreProfiler.PHASES) com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_SCAN_SORT, System.nanoTime() - __ss); // m357 尾段（排序+top400+同步）
    }

    /** 该机器的定向产出目标（机器→存储 连线；不可用则 null 走默认路由）。 */
    /** m321 计时壳。 */
    private com.sdzjz.machine.StorageAccess depositFor(Level world, int machineIndex) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return depositFor0(world, machineIndex);
        long __t = System.nanoTime();
        try { return depositFor0(world, machineIndex); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_RESOLVE, System.nanoTime() - __t); }
    }

    private com.sdzjz.machine.StorageAccess depositFor0(Level world, int machineIndex) {
        if (prof != null) prof.storageResolves++; // m177
        return edgeStorage(world, machineIndex, 0);
    }

    /** 该机器的定向供料源（存储→机器 连线）。 */
    /** m92：链式需求判定——物品 id 能否被节点 i（含其下游）真实消费。放行规则沿途生效，深度/环双保护。 */
    /** m155 该 id 沿此节点的出线链能否到达垃圾桶（尊重过滤/开关/抽取闸门，深度8防环）。
     *  精确账本物品抽取的授权判定：只有"终点是销毁"才允许抹组件抽走。 */

    // m218d：chainWants/chainEndsInTrash 顶层调用的 visited 集合复用——此前每类型每节点 new HashSet，
    // 大仓库(数千类型)×多逻辑节点×每5t 可达每秒十万级分配纯喂GC。服务端tick单线程、顶层调用点只有
    // 拉料循环两处且不互相嵌套（递归自身传同一集合），per-BE scratch 清场复用安全。
    private final java.util.HashSet<Integer> wantsScratch = new java.util.HashSet<>();
    private final java.util.HashSet<Integer> trashScratch = new java.util.HashSet<>();
    private java.util.Set<Integer> wantsScratchCleared() { wantsScratch.clear(); return wantsScratch; }
    private java.util.Set<Integer> trashScratchCleared() { trashScratch.clear(); return trashScratch; }

    // ===== m350 供料热路径复用 scratch（外部审计③轮②：每 5t 清运/泵料的 ArrayList(entrySet/keySet)
    // 防御性拷贝逐拍重配下岗）——map 转存进 grow-only 双数组后立即处理，不跨节点不跨 tick 不可重入；
    // 需要"实扣看返回值"的路径（泵/熔炉 withdraw）照旧当场扣，绝不按快照值虚记账（聚合视图可能陈旧）。 =====
    /** m381 区块储存器暂存器（节点下标→半成品模板；刻意 transient 不落盘——半成品几百 KB 逐拍写
     *  节点 NBT 会打爆画布快照，半途重启=回顶重扫自愈；Acc 内存绑定坐标防节点下标复用串账）。 */
    private transient java.util.HashMap<Integer, com.sdzjz.item.ChunkVaultItem.Acc> vaultAcc;

    private transient String[] drainIds = new String[16];
    private transient long[] drainAmts = new long[16];
    private int fillDrain(java.util.Map<String, Long> m) {
        int n = m.size();
        if (drainIds.length < n) {
            int cap = Math.max(n, drainIds.length * 2);
            drainIds = new String[cap];
            drainAmts = new long[cap];
        }
        int k = 0;
        for (var en : m.entrySet()) {
            drainIds[k] = en.getKey();
            Long v = en.getValue();
            drainAmts[k] = v == null ? 0L : v;
            k++;
        }
        return k;
    }
    /** m350 链式需求备忘外层表复用（值集来自 CraftPlanner 缓存/常量本就不逐拍配，只有外层 HashMap 在逐拍重配）。 */
    private final transient java.util.Map<Integer, java.util.Set<String>> crafterNeedsScratch = new java.util.HashMap<>();

    /** m321 计时壳：PHASES 关或递归内层=直通（一次 volatile 读+分支）；顶层调用计入 SUB_CHAIN。 */
    private boolean chainWants(Level world, int i, String id, int depth,
                               java.util.Set<Integer> visited,
                               int[][] outT, // m355 数组化
                               java.util.Map<Integer, java.util.Set<String>> crafterNeeds) {
        if (depth != 0 || !com.sdzjz.debug.CoreProfiler.PHASES) return chainWants0(world, i, id, depth, visited, outT, crafterNeeds);
        long __t = System.nanoTime();
        try { return chainWants0(world, i, id, depth, visited, outT, crafterNeeds); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_CHAIN, System.nanoTime() - __t); }
    }

    private void refreshForceChunks(Level world) {
        String selfDim = world.dimension().location().toString();
        long ownChunk = new net.minecraft.world.level.ChunkPos(worldPosition).toLong();
        java.util.Set<String> cur = new java.util.HashSet<>();
        for (int i = 0; i < g.storageEndpoints.size(); i++) {
            long pl = g.storageEndpoints.get(i)[0];
            if (pl == OUTPUT_IFACE) continue; // m142：哨兵不是真实方块——解成天边区块发票=崩服根因（m140/本轮两连崩）
            BlockPos ep = BlockPos.of(pl);
            String d = g.storageEndpointDims.get(i);
            long c = net.minecraft.world.level.ChunkPos.asLong(ep.getX() >> 4, ep.getZ() >> 4);
            if (!plausibleChunkLong(c)) continue; // m142：任何坏数据解出的边界外区块一律拒收
            if (d.equals(selfDim) && c == ownChunk) continue;
            cur.add(d + "|" + c);
        }
        for (int i = 0; i < g.storageEdges.size(); i++) {
            long pl = g.storageEdges.get(i)[1];
            if (pl == OUTPUT_IFACE) continue; // m142：连到输出接口节点的边同病，一并跳过
            BlockPos ep = BlockPos.of(pl);
            String d = i < g.storageEdgeDims.size() ? g.storageEdgeDims.get(i) : selfDim;
            long c = net.minecraft.world.level.ChunkPos.asLong(ep.getX() >> 4, ep.getZ() >> 4);
            if (!plausibleChunkLong(c)) continue;
            if (d.equals(selfDim) && c == ownChunk) continue;
            cur.add(d + "|" + c);
        }
        boolean changed = false;
        for (int i = forceChunks.size() - 1; i >= 0; i--) {
            String key = forceDims.get(i) + "|" + forceChunks.get(i)[0];
            if (cur.remove(key)) {
                if (forceChunks.get(i)[1] != 0) { forceChunks.get(i)[1] = 0; changed = true; }
            } else if (++forceChunks.get(i)[1] > 24) {
                forceChunks.remove(i);
                forceDims.remove(i);
                changed = true;
            }
        }
        for (String key : cur) {
            if (forceChunks.size() >= 64) break;
            int cut = key.lastIndexOf('|');
            forceDims.add(key.substring(0, cut));
            forceChunks.add(new long[]{Long.parseLong(key.substring(cut + 1)), 0});
            changed = true;
        }
        if (changed) setChanged();
    }

    /** m142：区块 long 合法性——世界边界 ±3000万方块 = 区块 ±187.5万。哨兵/坏数据解出的
     *  天边区块（如 OUTPUT_IFACE=Long.MIN+7 → 区块 -2097152，radius=1 邻块 -2097153 在
     *  22 位区段打包里回卷成 +2097151）会打崩实体管理器的 subSet 数学，一律拒收。 */
    private static boolean plausibleChunkLong(long chunkLong) {
        int cx = (int) chunkLong, cz = (int) (chunkLong >>> 32);
        return Math.abs(cx) <= 1_875_000 && Math.abs(cz) <= 1_875_000;
    }

    /** m133：对清单逐项续有期票（跨维度经 resolveDimWorld；票 300t 自动过期零清理）。 */
    private void renewEndpointTickets(net.minecraft.server.level.ServerLevel sw) {
        for (int i = 0; i < forceChunks.size(); i++) {
            Level tw = resolveDimWorld(sw, forceDims.get(i));
            if (tw instanceof net.minecraft.server.level.ServerLevel tsw)
                CoreChunkLoading.ticket(tsw, forceChunks.get(i)[0]);
        }
    }

    /** m133：本核心当前是否钉住了自身区块（拆方块时由 Block 调 release）。 */
    public boolean chunkForceActive() { return chunkForceOn; }
    public boolean chunkOwnedFlag() { return chunkOwned; } // m268 供拆核心时把所有权传给 release

    /** m321 计时壳。 */
    private com.sdzjz.machine.StorageAccess supplyFor(Level world, int machineIndex) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return supplyFor0(world, machineIndex);
        long __t = System.nanoTime();
        try { return supplyFor0(world, machineIndex); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_RESOLVE, System.nanoTime() - __t); }
    }

    private com.sdzjz.machine.StorageAccess supplyFor0(Level world, int machineIndex) {
        if (prof != null) prof.storageResolves++; // m177
        return edgeStorage(world, machineIndex, 1);
    }

    private com.sdzjz.machine.StorageAccess edgeStorage(Level world, int machineIndex, int dir) {
        for (int i = 0; i < g.storageEdges.size(); i++) {
            long[] e = g.storageEdges.get(i);
            if (e[0] != machineIndex || e[2] != dir) continue;
            com.sdzjz.machine.StorageAccess sc = resolveStorageAt(world, g.storageEdgeDims.get(i), e[1]);
            if (sc != null) return sc;
        }
        return null;
    }

    private com.sdzjz.machine.StorageAccess resolveStorageAt(Level world, String dim, long posLong) {
        if (posLong == OUTPUT_IFACE) return null; // 输出接口=默认自动路由，无实体存储
        BlockPos p = BlockPos.of(posLong);
        String self = world.dimension().location().toString();
        if (dim == null || dim.isEmpty() || self.equals(dim)) { // 空维度串按本维度处理（老数据兜底）
            if (!world.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
            return asAccess(world.getBlockEntity(p));
        }
        if (world instanceof net.minecraft.server.level.ServerLevel sw) {
            ResourceKey<Level> key;
            try {
                key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim));
            } catch (Exception e) {
                return null; // 畸形维度串：不炸 tick
            }
            net.minecraft.server.level.ServerLevel ow = sw.getServer().getLevel(key);
            if (ow == null || !ow.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
            return asAccess(ow.getBlockEntity(p));
        }
        return null;
    }

    /** 端点方块 → 存储访问：存储核心=单库；数据面板=聚合它网络里的全部存储核心（连到面板即连到整个网络）。 */
    private static com.sdzjz.machine.StorageAccess asAccess(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof StorageCoreBlockEntity sc) return sc;
        if (be instanceof DataPanelBlockEntity dp) return dp;
        return null;
    }

    /** 连/断一条 机器↔存储 定向连线（已存在则断开）。dir 0=产出到该存储 1=从该存储供料。 */
    public void toggleStorageEdge(net.minecraft.world.entity.player.Player byPlayer, int machineIndex, long storagePos, int dir, String dim) {
        if (machineIndex < 0 || machineIndex >= g.machineNodes.size() || dir < 0 || dir > 1) return;
        boolean known = false; // 只允许连到画布上确实显示的端点，防伪造包连任意坐标
        String epDim = null;
        for (int k = 0; k < g.storageEndpoints.size(); k++) {
            long[] ep = g.storageEndpoints.get(k);
            if (ep[0] == storagePos) { known = true; epDim = g.storageEndpointDims.get(k); break; }
        }
        if (!known) return;
        for (int i = 0; i < g.storageEdges.size(); i++) {
            long[] e = g.storageEdges.get(i);
            if (e[0] == machineIndex && e[1] == storagePos && e[2] == dir) {
                g.storageEdges.remove(i);
                g.storageEdgeDims.remove(i);
                setChanged();
                syncToClient();
                return;
            }
        }
        int capSE = SdzjzConfig.get().maxEdgesPerCore; // m270 存储连线单独同额封顶（断开在上面永远放行，只闸新增）
        if (capSE > 0 && g.storageEdges.size() >= capSE) {
            capMsg(byPlayer, "存储连线总数已达上限 " + capSE + "（config maxEdgesPerCore 可调，0=无限）");
            return;
        }
        // 维度以服务端端点表为准（客户端传空/伪造都不作数），兜底当前维度
        String useDim = (epDim != null && !epDim.isEmpty()) ? epDim
                : (dim != null && !dim.isEmpty()) ? dim
                : (level != null ? level.dimension().location().toString() : "minecraft:overworld");
        g.storageEdges.add(new long[]{machineIndex, storagePos, dir});
        g.storageEdgeDims.add(useDim);
        setChanged();
        syncToClient();
    }

    /** 设置存储节点画布坐标。 */
    /** m265 画布落位坐标钳制（±1,000,000，防伪造包写极端值进 NBT/参与几何运算溢出）。
     *  m269 升 long 入参：moveGroup 用 long 加法防 int 溢出后直接喂进来；原 int 调用点自动拓宽零改动。 */
    private static int clampCanvas(long v) { return com.sdzjz.node.CanvasGraphState.clampCanvas(v); } // m506：本体随 moveGroup 下沉共用，此处留同签名垫片零调用点改动

    /** m265 语义升级：写入即"放置到画布"——值升三元 {x, y, 1}，第三位=放置标记。
     *  历史二元值（m80 前遗留 + 旧整理布局写入的死数据）没有标记位=仍视为停靠，老档不惊动。 */
    public void setStorageNodePos(long storagePos, int nx, int ny) {
        g.storageNodePos.put(storagePos, new int[]{clampCanvas(nx), clampCanvas(ny), 1});
        setChanged();
        syncToClient();
    }

    /** m265 收回总线：删除画布落位（回停靠栏）。 */
    public void dockStorageNode(long storagePos) {
        if (g.storageNodePos.remove(storagePos) == null) return;
        setChanged();
        syncToClient();
    }

    /** m265 该端点是否已放置到画布（三元且标记位=1；二元遗留=否）。 */
    public boolean storageNodePlaced(long pl) {
        int[] v = g.storageNodePos.get(pl);
        return v != null && v.length >= 3 && v[2] == 1;
    }

    /** m265 打包已放置端点落位（并行列表，只发已放置项；与 buildEndsPayload 同拍走 m89 通道）。 */
    public com.sdzjz.net.StorageNodeHomePayload buildHomesPayload(BlockPos pos) {
        java.util.List<Long> ep = new java.util.ArrayList<>();
        java.util.List<Integer> hx = new java.util.ArrayList<>();
        java.util.List<Integer> hy = new java.util.ArrayList<>();
        for (long[] e : g.storageEndpoints) {
            int[] v = g.storageNodePos.get(e[0]);
            if (v != null && v.length >= 3 && v[2] == 1) { ep.add(e[0]); hx.add(v[0]); hy.add(v[1]); }
        }
        return new com.sdzjz.net.StorageNodeHomePayload(pos, ep, hx, hy);
    }

    public java.util.List<long[]> storageEndpointsView() { return g.storageEndpoints; }

    /** m89：打包端点+总线库存（并行列表）。 */
    public com.sdzjz.net.CanvasEndsPayload buildEndsPayload(BlockPos pos) {
        java.util.List<Long> ep = new java.util.ArrayList<>();
        java.util.List<Integer> ek = new java.util.ArrayList<>();
        java.util.List<String> ed = new java.util.ArrayList<>();
        for (int i = 0; i < g.storageEndpoints.size(); i++) {
            ep.add(g.storageEndpoints.get(i)[0]);
            ek.add((int) g.storageEndpoints.get(i)[1]);
            ed.add(i < g.storageEndpointDims.size() ? g.storageEndpointDims.get(i) : "");
        }
        return new com.sdzjz.net.CanvasEndsPayload(pos, ep, ek, ed,
                new java.util.ArrayList<>(g.busTopIds), new java.util.ArrayList<>(g.busTopCounts));
    }

    // m85：总线库存（网络前10物品，画布顶栏「存储总线（网络库存）」展示；字段在 g）
    public java.util.List<String> busTopIdsView() { return g.busTopIds; }
    public java.util.List<Long> busTopCountsView() { return g.busTopCounts; }

    // m86：实测产量（分钟滚动窗口；生成点计数，不在 deposit 链上数防重复）
    private long prodWin = 0, prodWinStart = 0; // prodPerMin 快照在 g
    void prodTally(long n) { if (n > 0) prodWin += n; }
    public long prodPerMinView() { return g.prodPerMin; }

    private Level resolveDimWorld(Level base, String dim) {
        if (dim == null || dim.isEmpty() || base.dimension().location().toString().equals(dim)) return base;
        if (base instanceof net.minecraft.server.level.ServerLevel sw)
            return sw.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
        return null;
    }
    public java.util.List<String> storageEndpointDimsView() { return g.storageEndpointDims; }
    public java.util.List<long[]> storageEdgesView() { return g.storageEdges; }

    public int storageNodeX(long pl, int def) {
        int[] v = g.storageNodePos.get(pl);
        return v != null ? v[0] : def;
    }

    public int storageNodeY(long pl, int def) {
        int[] v = g.storageNodePos.get(pl);
        return v != null ? v[1] : def;
    }

    // ===== 连线内部物流缓存 =====
    private long bufCount(String id) { return internalBuffer.getOrDefault(id, 0L); }

    // ===== 按边精确路由：每节点输入缓存 =====
    /** 取第 i 个节点的输入缓存（懒补齐对齐 machineNodes）。 */
    private java.util.Map<String, Long> nodeBuf(int i) {
        while (nodeBufs.size() < g.machineNodes.size()) nodeBufs.add(new java.util.HashMap<>());
        return nodeBufs.get(i);
    }

    /** m270 单节点缓存类型上限（审计"每个节点缓存的物品类型上限"条）：拒收"新类型"，已有类型照常合并，0=无限。
     *  调用规矩：**取料/入账前判**——泵料在 withdraw 前跳过、分发拒收残量走默认路由/留在源头，零物品损失。 */
    private boolean bufTypeOk(java.util.Map<String, Long> m, String id) {
        int cap = SdzjzConfig.get().maxBufferTypesPerNode;
        return cap <= 0 || m.containsKey(id) || m.size() < cap;
    }

    /** 节点可用量 = 自己的输入缓存 + 遗留共享池（老档迁移兜底）。 */
    private long bufCountFor(int i, String id) {
        return StorageCoreBlockEntity.satAdd(nodeBuf(i).getOrDefault(id, 0L), internalBuffer.getOrDefault(id, 0L)); // m273 饱和加法
    }

    /** 先扣自己的输入缓存，不足部分再扣遗留池。 */
    private void bufWithdrawFor(int i, String id, long amt) {
        java.util.Map<String, Long> m = nodeBuf(i);
        long own = m.getOrDefault(id, 0L);
        long fromOwn = Math.min(own, amt);
        if (fromOwn > 0) {
            long left = own - fromOwn;
            if (left <= 0) m.remove(id); else m.put(id, left);
        }
        long rest = amt - fromOwn;
        if (rest > 0) bufWithdraw(id, rest);
    }

    /** 均分分发（分配器）：在所有"吃得下"的目标间平分，余数轮转；装不下/没人要的走 定向存储/默认路由。 */
    /** m321 计时壳。 */
    private void distributeEven(Level world, int fromIndex, int[] targets, String id, long amt) { // m355 数组化
        if (!com.sdzjz.debug.CoreProfiler.PHASES) { distributeEven0(world, fromIndex, targets, id, amt); return; }
        long __t = System.nanoTime();
        try { distributeEven0(world, fromIndex, targets, id, amt); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_DISTRIBUTE, System.nanoTime() - __t); }
    }

    private void distributeEven0(Level world, int fromIndex, int[] targets, String id, long amt) { // m354b 补
        if (prof != null) prof.routes++; // m177
        // m496：均分主体已下沉共用（ProductRouter.distributeEven，与 1.20.1 同一份）；兜底=本世代输出缓存路径。
        com.sdzjz.node.ProductRouter.distributeEven(routerHost(targets), (lvl, from, id2, amt2) -> {
            com.sdzjz.machine.StorageAccess dep = depositFor((Level) lvl, from);
            ItemStack rest = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id2)),
                    (int) Math.min(amt2, 64L * OUTPUT_SLOTS));
            if (dep != null) depositOrBuffer(dep, rest);
            else addOutput(rest);
            return 0L;
        }, world, fromIndex, targets != null && targets.length > 0, id, amt);
    }

    /** 按需分发：只把目标机器"吃得下"的物品送下线；没人要的部分走 定向存储/默认路由——绝不堵死在下游缓存里。 */
    /** m321 计时壳。 */
    private void distribute(Level world, int fromIndex, int[] targets, String id, long amt) { // m355 数组化
        if (!com.sdzjz.debug.CoreProfiler.PHASES) { distribute0(world, fromIndex, targets, id, amt); return; }
        long __t = System.nanoTime();
        try { distribute0(world, fromIndex, targets, id, amt); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_DISTRIBUTE, System.nanoTime() - __t); }
    }

    private void distribute0(Level world, int fromIndex, int[] targets, String id, long amt) { // m354b 补：同上
        if (prof != null) prof.routes++; // m177
        // m495（B2 前置）：两轮垫底与缓存投喂已下沉共用（xplat node/ProductRouter，与 1.20.1 同一份代码）；
        // 本世代的**兜底口**=定向存储 → depositOrBuffer → addOutput（输出缓存/断网喷射），
        // 那是 1.20.1 没有的东西，故收进 Tail 口各实现各的（两代唯一的真差异）。
        com.sdzjz.node.ProductRouter.distribute(routerHost(targets), (lvl, from, id2, amt2) -> {
            com.sdzjz.machine.StorageAccess dep = depositFor((Level) lvl, from);
            ItemStack rest = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id2)),
                    (int) Math.min(amt2, 64L * OUTPUT_SLOTS));
            if (dep != null) depositOrBuffer(dep, rest);
            else addOutput(rest);
            return 0L; // 主线有输出缓存兜住，恒不回吐
        }, world, fromIndex, targets != null, id, amt);
    }

    /** m495：分发所需的宿主面（targets 由调用方传入——主线 m355 已数组化）。 */
    private com.sdzjz.node.ProductRouter.Host routerHost(int[] targets) {
        return new com.sdzjz.node.ProductRouter.Host() {
            @Override public int nodeCount() { return g.machineNodes.size(); }
            @Override public ItemStack nodeStack(int i) { return g.machineNodes.get(i); }
            @Override public int[] outTargets(int from) { return targets; }
            @Override public java.util.Map<String, Long> nodeBuf(int i) { return StructureCoreBlockEntity.this.nodeBuf(i); }
            @Override public boolean bufTypeOk(java.util.Map<String, Long> buf, String id) {
                return StructureCoreBlockEntity.this.bufTypeOk(buf, id);
            }
            @Override public boolean accepts(Object level, int target, String id) {
                return StructureCoreBlockEntity.this.accepts((Level) level, target, id);
            }
            @Override public void markChanged() { setChanged(); }
        };
    }

    /** 目标机器是否"吃"该物品：万能熔炉=可熔炼物；消耗机=配方输入；自动合成机=当前目标用料；农场=不吃。 */
    /** m359 计时壳（PHASES 关=直通）。 */
    private boolean accepts(Level world, int target, String id) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) return accepts0(world, target, id);
        long __t = System.nanoTime();
        try { return accepts0(world, target, id); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_ACCEPTS, System.nanoTime() - __t); }
    }


    /** 节点缓存回收进遗留池（bufAdd 自带封顶+溢出缓存），删除节点不丢在途物品。 */
    private void mergeLegacy(java.util.Map<String, Long> m) {
        if (m == null) return;
        for (java.util.Map.Entry<String, Long> e : m.entrySet()) bufAdd(e.getKey(), e.getValue());
    }

    /** 全部在途缓存量（画布顶栏显示）。 */
    private long bufferedTotal() {
        long n = 0;
        for (Long v : internalBuffer.values()) n += v;
        for (java.util.Map<String, Long> m : nodeBufs) for (Long v : m.values()) n += v;
        return n;
    }

    private void bufWithdraw(String id, long amt) {
        long left = internalBuffer.getOrDefault(id, 0L) - amt;
        if (left <= 0) internalBuffer.remove(id); else internalBuffer.put(id, left);
    }

    private void bufAdd(String id, long amt) {
        long sum = StorageCoreBlockEntity.satAdd(internalBuffer.getOrDefault(id, 0L), amt); // m273：中间加法溢出翻负会绕过 BUF_CAP 封顶
        if (sum > BUF_CAP) {
            long spill = sum - BUF_CAP;
            internalBuffer.put(id, BUF_CAP);
            addOutput(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), (int) Math.min(spill, 64L * OUTPUT_SLOTS)));
        } else {
            internalBuffer.put(id, sum);
        }
    }

    /** m270 硬上限拒绝提示（actionbar，空玩家安全跳过）——m99 教训：静默无效比数值弱更伤，界面上必须看得出来。 */
    private static void capMsg(net.minecraft.world.entity.player.Player p, String s) {
        if (p != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal(s), true);
    }

    /** m270 节点连线度数（进+出合计），配合 maxEdgesPerNode。 */
    private int nodeDegree(int n) {
        int d = 0;
        for (int[] c : g.connections) if (c[0] == n || c[1] == n) d++;
        return d;
    }

    /** 连/断一条 from→to 连线（已存在则断开）。m270 签名升带玩家：上限拒绝要提示（m99 静默无效教训）。 */
    public void toggleConnection(net.minecraft.world.entity.player.Player byPlayer, int from, int to) {        if (from == to || from < 0 || to < 0 || from >= g.machineNodes.size() || to >= g.machineNodes.size()) return;
        for (int i = 0; i < g.connections.size(); i++) {
            int[] c = g.connections.get(i);
            if (c[0] == from && c[1] == to) { g.connections.remove(i); bumpTopo(); setChanged(); syncToClient(); return; } // m179
        }
        SdzjzConfig cfgE = SdzjzConfig.get(); // m270 硬上限：断开永远放行，只闸新增
        if (cfgE.maxEdgesPerCore > 0 && g.connections.size() >= cfgE.maxEdgesPerCore) {
            capMsg(byPlayer, "连线总数已达上限 " + cfgE.maxEdgesPerCore + "（config maxEdgesPerCore 可调，0=无限）");
            return;
        }
        if (cfgE.maxEdgesPerNode > 0 && (nodeDegree(from) >= cfgE.maxEdgesPerNode || nodeDegree(to) >= cfgE.maxEdgesPerNode)) {
            capMsg(byPlayer, "单节点连线已达上限 " + cfgE.maxEdgesPerNode + "（config maxEdgesPerNode 可调，0=无限）");
            return;
        }
        g.connections.add(new int[]{from, to});
        bumpTopo(); // m179
        setChanged();
        syncToClient();
    }

    /** 画布渲染读取（客户端）。 */
    public java.util.List<ItemStack> nodes() { return g.machineNodes; }

    /** 破坏时掉落全部（升级/产出 + 机器节点）。 */
    public void dropAll(Level world, BlockPos pos) {
        Containers.dropContents(world, pos, this);
        for (ItemStack s : g.machineNodes) {
            CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
            for (int type = 0; type < 3; type++) {
                int lv = n.getInt(com.sdzjz.node.NodeUpgrades.upgradeKey(type));
                Item item = com.sdzjz.node.NodeUpgrades.upgradeItem(type);
                if (item != null && lv > 0)
                    Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(item, lv));
            }
            com.sdzjz.item.ItemData.clear(s);
            Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), s);
        }
        g.machineNodes.clear();
        bumpTopo(); // m179
        g.nodeStatus.clear();
        g.nodeReason.clear(); // m178
    }

    // ===== m344 画布观众登记表（外部审计 P1 销账：此前每核心每 tick 扫一遍全服玩家表——
    // 100 核×50 人=5000 次谓词判定/tick，绝大多数核心根本没人看）。开屏挂号/关屏销号
    // （StructureCoreScreenHandler 构造+onClosed 双钩），三处扫描（快照/兜底判定/m89 端点包）
    // 全改查表；表内逐观众仍过 currentScreenHandler 同款谓词校验，失配即销号=断线/换屏等
    // 一切漏钩路径的兜底，语义与旧全表扫描逐点一致。零新配置键（m279 先例：纯查询加速）。 =====
    private final java.util.Set<net.minecraft.server.level.ServerPlayer> canvasViewers =
            new java.util.LinkedHashSet<>();

    /** m344 开屏挂号（服务端 AbstractContainerMenu 构造调用）。 */
    public void addCanvasViewer(net.minecraft.server.level.ServerPlayer sp) { canvasViewers.add(sp); lastEndpointScan = -1000; } // m348 开画布哨兵强刷端点
    public boolean endpointScanPending() { return lastEndpointScan <= -1000; } // m348 GameTest 观测口：哨兵是否待刷

    /** m344 关屏销号（onClosed 调用）；顺带清快照已发账，重开必得首包（原"无观众清表"的逐人版）。 */
    public void removeCanvasViewer(net.minecraft.server.level.ServerPlayer sp) {
        canvasViewers.remove(sp);
        snapshotSent.remove(sp.getUUID());
    }

    /** m344 观众登记数（GameTest 廿七号/观测用，裸表数不做校验）。 */
    public int canvasViewerCount() { return canvasViewers.size(); }

    /** m181 是否有玩家正开着本核心的画布（判定复用 m89 端点直发同款：currentScreenHandler 指向本 pos）。
     *  m344 起查登记表不扫全服；失配观众就地销号。 */
    private boolean hasCanvasViewer(Level world) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel)) return false;
        for (var it = canvasViewers.iterator(); it.hasNext(); ) {
            net.minecraft.server.level.ServerPlayer sp = it.next();
            if (sp.containerMenu instanceof com.sdzjz.screen.StructureCoreScreenHandler h
                    && worldPosition.equals(h.blockPos())) return true;
            it.remove();
        }
        return false;
    }

    // ===== m275 观众定向渲染快照（审计第一批第3条：全量NBT同步拆分，方案 docs/同步拆分方案_m274.md）=====
    private boolean canvasDirty = false;                 // 事件标脏：同 tick N 次写合并成 1 份快照
    private long snapshotRev = 0;                        // 快照版本：观众 lastSent != rev 即补发（开屏首包与标脏聚合同一机制）
    private final java.util.Map<java.util.UUID, Long> snapshotSent = new java.util.HashMap<>(); // 每观众已发版本

    /** 原 m88/m181 事件同步/周期兜底语义保留，实现 m275 起改标脏——不再走 vanilla 全量 NBT 区块广播
     *  （原路径=完整 writeNbt × 所有追踪区块的玩家；现=渲染子集 × 仅观众 × 每 tick 至多 1 份）。 */
    private void syncToClient() {
        if (level != null && !level.isClientSide) canvasDirty = true;
    }

    /** tickInner 顶部每 tick 调用：脏则升版本；对正在看画布的玩家（m89 管线同款判定）按版本差补发渲染快照。
     *  快照每 tick 至多编码一次；版本对齐的观众不重发；无观众清表=重开屏必得首包（createMenu 强刷照旧标脏兜底）。 */
    private void flushCanvasSnapshot(Level world) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel sw)) return;
        if (canvasDirty) { snapshotRev++; canvasDirty = false; }
        if (canvasViewers.isEmpty()) { // m344 无人看=零成本早退（这才是绝大多数核心的每 tick 路径）
            if (!snapshotSent.isEmpty()) snapshotSent.clear();
            return;
        }
        com.sdzjz.net.CanvasSnapshotPayload pk = null;
        boolean anyViewer = false;
        for (var it = canvasViewers.iterator(); it.hasNext(); ) { // m344 查登记表不扫全服
            net.minecraft.server.level.ServerPlayer sp = it.next();
            if (!(sp.containerMenu instanceof com.sdzjz.screen.StructureCoreScreenHandler h)
                    || !worldPosition.equals(h.blockPos())) { it.remove(); snapshotSent.remove(sp.getUUID()); continue; }
            anyViewer = true;
            Long sent = snapshotSent.get(sp.getUUID());
            if (sent != null && sent == snapshotRev) continue;
            if (pk == null) {
                CompoundTag snap = new CompoundTag();
                g.writeRenderNbt(snap, sw.registryAccess());
                pk = new com.sdzjz.net.CanvasSnapshotPayload(worldPosition, snap);
                if (prof != null) { try { prof.syncBytes += com.sdzjz.legacy.LegacyDebugUtil.nbtSize(snap); } catch (Exception ignored) {} } // m177 对表尺随刀迁移
            }
            com.sdzjz.net.Net.toPlayer(sp, pk);
            if (prof != null) prof.syncPackets++; // m275 起口径=真实发出的快照包数（原=updateListeners 调用数）
            snapshotSent.put(sp.getUUID(), snapshotRev);
        }
        if (!anyViewer && !snapshotSent.isEmpty()) snapshotSent.clear();
    }

    /** m177 /sdzjz dumpgraph：整图转储（节点+连线+运行态），进服务器日志用。 */
    public String debugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("running=").append(running).append(" nodes=").append(g.machineNodes.size())
          .append(" edges=").append(g.connections.size()).append(" prodPerMin=").append(g.prodPerMin).append('\n');
        for (int i = 0; i < g.machineNodes.size(); i++) {
            ItemStack st = g.machineNodes.get(i);
            sb.append(String.format("  [%d] %s x%d%s%n", i,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()), st.getCount(),
                    com.sdzjz.node.NodeTags.nodePaused(st) ? " (暂停)" : ""));
        }
        for (int[] c : g.connections) sb.append("  edge ").append(c[0]).append(" -> ").append(c[1]).append('\n');
        return sb.toString();
    }

    private boolean pop(Player player, int i) {
        ItemStack s = items.get(i);
        if (s.isEmpty()) return false;
        if (!player.getInventory().add(s.copy())) player.drop(s.copy(), false);
        items.set(i, ItemStack.EMPTY);
        setChanged();
        return true;
    }

    private int countUpgrade(net.minecraft.world.item.Item up) {
        int n = 0;
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) {
            if (items.get(i).is(up)) n += items.get(i).getCount();
        }
        return n;
    }

    /** 定向入库兜底：存储核心类型满被拒时回落输出缓存，绝不静默丢物品。 */
    /** m321 计时壳。 */
    private void depositOrBuffer(com.sdzjz.machine.StorageAccess sc, ItemStack stack) {
        if (!com.sdzjz.debug.CoreProfiler.PHASES) { depositOrBuffer0(sc, stack); return; }
        long __t = System.nanoTime();
        try { depositOrBuffer0(sc, stack); }
        finally { com.sdzjz.debug.CoreProfiler.sub(com.sdzjz.debug.CoreProfiler.SUB_DEPOSIT, System.nanoTime() - __t); }
    }

    private void depositOrBuffer0(com.sdzjz.machine.StorageAccess sc, ItemStack stack) {
        if (stack.isEmpty()) return;
        sc.deposit(stack); // 收下会清空栈；类型满则原样留着
        if (!stack.isEmpty()) {
            addOutput(stack);
            stack.setCount(0);
        }
    }

    /** 把产物塞进输出缓存，塞不下的丢弃（缓存满=暂停产出，不生成掉落物）。 */
    private void addOutput(ItemStack out) {
        int remain = out.getCount();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS && remain > 0; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                int put = Math.min(remain, out.getMaxStackSize());
                items.set(i, out.copyWithCount(put)); // m131b：保组件（此前重建栈抹组件——药水/附魔件进输出缓存会变裸件）
                remain -= put;
            } else if (ItemStack.isSameItemSameComponents(slot, out) && slot.getCount() < slot.getMaxStackSize()) { // m131b：异组件不并栈
                int put = Math.min(remain, slot.getMaxStackSize() - slot.getCount());
                slot.grow(put);
                remain -= put;
            }
        }
    }

    // ===== 输出路由缓存：目标坐标缓存 40 tick，避免每个生产周期反复 BFS =====
    private BlockPos cachedOutPos;
    private long cachedOutUntil;
    private Direction cachedOutSide; // m461 非空=缓存目标是 FTA 句柄（记接入面，句柄不缓存每次重取防悬空）
    /** m461 反向直连命中：FTA 目标只记「哪个方块的哪个面」，句柄由 resolveOutTarget 现取现用。 */
    private record XferHit(BlockPos pos, Direction side) { }
    private BlockPos cachedInPos;
    private long cachedInUntil;

    /** 解析消耗机取料源（存储核心），带缓存。 */
    StorageCoreBlockEntity resolveInputSource(Level world, BlockPos corePos) {
        long now = world.getGameTime();
        if (cachedInPos != null && now < cachedInUntil
                && world.getChunkSource().hasChunk(cachedInPos.getX() >> 4, cachedInPos.getZ() >> 4)
                && world.getBlockEntity(cachedInPos) instanceof StorageCoreBlockEntity sc) {
            return sc;
        }
        cachedInPos = null;
        StorageCoreBlockEntity src = boundPanel(world, corePos);
        if (src == null) src = findPanel(world, corePos);
        if (src == null && hasWirelessNode(world, corePos)) src = nearestWirelessPanel(world, corePos);
        if (src == null && hasSatelliteNode(world, corePos)) src = findSatellitePanel(world, corePos);
        if (src != null && src.getLevel() == world) {
            cachedInPos = src.getBlockPos().immutable();
            cachedInUntil = now + 40;
        }
        return src;
    }

    private long cachedOutMissUntil = -1000; // m114 断网负缓存时间戳

    /** 解析输出目标（存储核心或普通容器），带缓存。仅缓存同维度目标；查无目标也缓存 40t（m114）。 */
    private Object resolveOutTarget(Level world, BlockPos corePos) {
        long now = world.getGameTime();
        if (cachedOutPos != null && now < cachedOutUntil
                && world.getChunkSource().hasChunk(cachedOutPos.getX() >> 4, cachedOutPos.getZ() >> 4)) {
            if (cachedOutSide != null) { // m461 缓存的是 FTA 目标：句柄现取现用（不缓存句柄防方块换脸悬空引用）
                Object h = com.sdzjz.storage.Xfer.find(world, cachedOutPos, cachedOutSide);
                if (h != null && com.sdzjz.storage.Xfer.canInsert(h)) return h;
            } else {
                BlockEntity be = world.getBlockEntity(cachedOutPos);
                if (be instanceof StorageCoreBlockEntity sc) return sc;
                if (be instanceof Container inv && !(be instanceof StructureCoreBlockEntity)) return inv;
            }
        }
        if (now < cachedOutMissUntil) return null; // m114 负缓存：断网核心不必每次全套 BFS+无线/卫星扫描
        cachedOutPos = null;
        cachedOutSide = null; // m461 缓存作废两字段同清
        Object target = boundPanel(world, corePos);
        if (target == null) target = findTarget(world, corePos);
        if (target == null && hasWirelessNode(world, corePos)) target = nearestWirelessPanel(world, corePos);
        if (target == null && hasSatelliteNode(world, corePos)) target = findSatellitePanel(world, corePos);
        if (target instanceof XferHit hit) { // m461 反向直连：记「方块+接入面」，句柄现取（同缓存命中路口径）
            cachedOutPos = hit.pos();
            cachedOutSide = hit.side();
            cachedOutUntil = now + 40;
            Object h = com.sdzjz.storage.Xfer.find(world, hit.pos(), hit.side());
            return (h != null && com.sdzjz.storage.Xfer.canInsert(h)) ? h : null; // 刚扫到就被拆=本拍空手，40t 后重扫
        }
        if (target instanceof BlockEntity tbe && tbe.getLevel() == world) {
            cachedOutPos = tbe.getBlockPos().immutable();
            cachedOutUntil = now + 40;
        } else if (target == null) {
            cachedOutMissUntil = now + 40; // 新接存储最迟 2s 被感知——与全 MOD 既有 40t 缓存同语义
        }
        return target;
    }

    /** 把输出缓存推入正下方容器。 */
    /** 把输出缓存送到：相邻的数据面板/箱子，或顺着数据线 BFS 连到的存储。 */
    private void pushOutput(Level world, BlockPos corePos) {
        Object target = resolveOutTarget(world, corePos);
        if (target == null) return;
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) continue;
            if (target instanceof StorageCoreBlockEntity panel) panel.deposit(slot);
            else if (target instanceof Container inv) insertInto(inv, slot);
            else xferPushStack(target, slot); // m461 反向直连：FTA 句柄（resolveOutTarget 已验 canInsert）
            if (slot.isEmpty()) items.set(i, ItemStack.EMPTY);
        }
    }

    /** m114 断网喷射：核心连不到面板/存储/箱子时，输出缓存不再憋死——每 10t 从顶面发射器式
     *  喷出一组。有存储时不喷（pushOutput 正常落库）；节流 1 组/10t 防实体洪水（同类掉落物原版
     *  自动合堆+5 分钟消失双兜底）。m99 封顶仍在：缓存满停产、喷射腾格后自动续产——
     *  离网吞吐≈喷射速率（约 2 组/秒），天然自限。 */
    private boolean ejectWarned = false; // m115：断网喷射只警告一次，接回存储后复位
    private boolean lagPause = false;    // m115：过载全线暂停标志（滞回控制）
    /** m308：压测报告读黄灯占空比用（首轮 100×512 实测 3051× 倍数实为看门狗占空比噪声）。 */
    public boolean lagPausedNow() { return lagPause; }

    private void ejectOverflow(Level world, BlockPos corePos) {
        if (resolveOutTarget(world, corePos) != null) { ejectWarned = false; return; } // 有去处不喷，警告复位
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) continue;
            if (!ejectWarned) { // m115 用户点名：运行前提醒——首次喷射时告知附近玩家
                ejectWarned = true;
                warnNearby(world, "『生电终结者』核心未连接存储：产出将喷射为掉落物，可能造成卡顿（贴上存储核心/箱子即恢复落库）");
            }
            ItemStack out = slot.copy();
            items.set(i, ItemStack.EMPTY);
            var r = world.getRandom();
            net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(world,
                    corePos.getX() + 0.5, corePos.getY() + 1.1, corePos.getZ() + 0.5, out,
                    (r.nextDouble() - 0.5) * 0.16, 0.30 + r.nextDouble() * 0.08, (r.nextDouble() - 0.5) * 0.16);
            e.setDefaultPickUpDelay();
            e.addTag("sdzjz_ejected"); // m115：打标——极端卡顿只清自家喷出的，绝不动玩家掉落
            world.addFreshEntity(e);
            setChanged();
            return; // 每次最多一组
        }
    }

    /** m115：给核心 24 格内的玩家发一条聊天提示。 */
    private void warnNearby(Level world, String text) {
        for (net.minecraft.world.entity.player.Player pl : world.players())
            if (pl.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) < 24 * 24)
                pl.displayClientMessage(net.minecraft.network.chat.Component.literal(text), false);
    }

    /** m115 极端卡顿(>60ms/tick)：清理本核心周边 64 格内带 sdzjz_ejected 标签的掉落物。 */
    private void cleanupEjected(net.minecraft.server.level.ServerLevel sw) {
        var box = net.minecraft.world.phys.AABB.ofSize(worldPosition.getCenter(), 64, 32, 64);
        for (net.minecraft.world.entity.item.ItemEntity e : sw.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                en -> en.getTags().contains("sdzjz_ejected"))) e.discard();
    }

    /** 从核心出发，直连相邻存储；遇数据线则继续路由，返回最近的数据面板/箱子。 */
    private Object findTarget(Level world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCoreBlockEntity panel) return panel;
                if (be instanceof Container inv && !(be instanceof StructureCoreBlockEntity)) return inv;
                Object fh = xferPushProbe(world, np, d.getOpposite(), be); // m461 反向直连：FTA-only 机器（MI/TechReborn/Create 置物台…）
                if (fh != null) return fh;
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return null;
    }

    /** m461 反向直连探针（可测核）：邻位既非自家仓也非原版容器时，问 FTA 有没有能收货的视图。
     *  排除自家 StructureCore——它实现 Container，会被 Fabric 的 Inventory 兜底包装出 FTA 视图，
     *  不排除=相邻两台生产核心互喂产出（自冲突）；自家存储核心/箱子在上游分支已被接走轮不到这里。
     *  配置闸 xferPushEnabled 关=恒 null（出货路由行为逐位回 m460 前）。probe 在 BFS 里对每个邻位
     *  都会问一次 FTA——lookup 是哈希直查+兜底 instanceof，且只在 40t 缓存失效时跑（m224 线缆插头
     *  视觉同款姿势，先例在树）。返回 Object 实为 XferHit（只记方块+接入面，句柄现取现用防悬空）。 */
    public static Object xferPushProbe(Level world, BlockPos np, Direction side, BlockEntity be) {
        if (!com.sdzjz.config.SdzjzConfig.get().xferPushEnabled) return null;
        if (be instanceof StructureCoreBlockEntity) return null; // 自家生产核心绝不当出货目标
        Object h = com.sdzjz.storage.Xfer.find(world, np, side);
        return (h != null && com.sdzjz.storage.Xfer.canInsert(h)) ? new XferHit(np.immutable(), side) : null;
    }

    /** m461 反向直连出货（可测核）：FTA 句柄单栈插入——带组件走精确变体（组件保真，不变裸），
     *  裸件走裸变体（与 deposit 分流同口径）；按实收扣栈，余量留输出缓存绝不落地（insertInto 同律）。 */
    public static void xferPushStack(Object handle, ItemStack slot) {
        if (handle == null || slot == null || slot.isEmpty()) return;
        long acc = com.sdzjz.storage.Xfer.insert(handle, slot, !slot.getComponentsPatch().isEmpty(), slot.getCount());
        if (acc > 0) slot.shrink((int) Math.min(acc, Integer.MAX_VALUE));
    }

    /** 核心相邻或其数据线网络上是否接了无线节点。 */
    private boolean hasWirelessNode(Level world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 128;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                var block = world.getBlockState(np).getBlock();
                if (block instanceof WirelessNodeBlock) return true;
                if (block instanceof DataCableBlock) q.add(np);
            }
        }
        return false;
    }

    /** 登记表里、范围内、同维度最近的数据面板。 */
    private StorageCoreBlockEntity nearestWirelessPanel(Level world, BlockPos corePos) {
        long range = SdzjzConfig.get().wirelessRange;
        long r2 = range * range, best = Long.MAX_VALUE;
        StorageCoreBlockEntity found = null;
        for (BlockPos p : StorageCoreBlockEntity.coresNear(world, corePos, range)) { // m279 空间索引：只访问范围覆盖的桶
            long dx = p.getX() - corePos.getX(), dy = p.getY() - corePos.getY(), dz = p.getZ() - corePos.getZ();
            long d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > r2 || d2 >= best) continue;
            StorageCoreBlockEntity panel = StorageCoreBlockEntity.loadedCoreAt(world, p);
            if (panel != null) {
                best = d2;
                found = panel;
            }
        }
        return found;
    }

    /** 核心网络上是否接了卫星节点。 */
    private boolean hasSatelliteNode(Level world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 128;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                var block = world.getBlockState(np).getBlock();
                if (block instanceof SatelliteNodeBlock) return true;
                if (block instanceof DataCableBlock) q.add(np);
            }
        }
        return false;
    }

    /** 数据链接器设置的绑定目标面板。 */
    public void setBound(BlockPos pos, String dim) {
        this.boundPanelPos = pos == null ? null : pos.immutable();
        this.boundPanelDim = dim;
        setChanged();
    }

    /** 绑定目标可达则返回（同维度需无线/卫星/有线可达；跨维度需卫星）。优先级最高。 */
    private StorageCoreBlockEntity boundPanel(Level world, BlockPos corePos) {
        if (boundPanelPos == null || boundPanelDim == null) return null;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(boundPanelDim));
        boolean sameDim = world.dimension().equals(dimKey);
        if (sameDim) {
            long dx = boundPanelPos.getX() - corePos.getX(), dy = boundPanelPos.getY() - corePos.getY(), dz = boundPanelPos.getZ() - corePos.getZ();
            long d2 = dx * dx + dy * dy + dz * dz, range = SdzjzConfig.get().wirelessRange;
            boolean ok = hasSatelliteNode(world, corePos)
                    || (hasWirelessNode(world, corePos) && d2 <= range * range)
                    || wiredReaches(world, corePos, boundPanelPos);
            if (!ok) return null;
            return StorageCoreBlockEntity.loadedCoreAt(world, boundPanelPos);
        }
        if (!hasSatelliteNode(world, corePos)) return null;
        if (world instanceof net.minecraft.server.level.ServerLevel sw) {
            net.minecraft.server.level.ServerLevel ow = sw.getServer().getLevel(dimKey);
            if (ow != null) return StorageCoreBlockEntity.loadedCoreAt(ow, boundPanelPos);
        }
        return null;
    }

    /** 目标面板是否经相邻/数据线有线可达。 */
    private boolean wiredReaches(Level world, BlockPos corePos, BlockPos target) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                if (np.equals(target)) return true;
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return false;
    }

    /** 卫星：本维度最近(无距离上限)优先，否则其它已加载维度里任意一个数据面板。 */
    private StorageCoreBlockEntity findSatellitePanel(Level world, BlockPos corePos) {
        long best = Long.MAX_VALUE;
        StorageCoreBlockEntity found = null;
        for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(world))) {
            long dx = p.getX() - corePos.getX(), dy = p.getY() - corePos.getY(), dz = p.getZ() - corePos.getZ();
            long d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= best) continue;
            StorageCoreBlockEntity panel = StorageCoreBlockEntity.loadedCoreAt(world, p);
            if (panel != null) {
                best = d2;
                found = panel;
            }
        }
        if (found != null) return found;
        if (world instanceof net.minecraft.server.level.ServerLevel sw) {
            var server = sw.getServer();
            for (var key : java.util.List.copyOf(StorageCoreBlockEntity.dimensionsWithCores())) {
                if (key.equals(world.dimension())) continue;
                net.minecraft.server.level.ServerLevel ow = server.getLevel(key);
                if (ow == null) continue;
                for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(ow))) {
                    StorageCoreBlockEntity panel = StorageCoreBlockEntity.loadedCoreAt(ow, p);
                    if (panel != null) return panel;
                }
            }
        }
        return found;
    }
    private StorageCoreBlockEntity findPanel(Level world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.relative(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCoreBlockEntity panel) return panel;
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return null;
    }

    private static void insertInto(Container target, ItemStack stack) {
        for (int i = 0; i < target.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack t = target.getItem(i);
            if (t.isEmpty()) {
                target.setItem(i, stack.copyAndClear());
                return;
            } else if (ItemStack.isSameItemSameComponents(t, stack) && t.getCount() < t.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), t.getMaxStackSize() - t.getCount());
                t.grow(move);
                stack.shrink(move);
            }
        }
    }

    // ================= NBT =================
    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        // m275：以下为存档专属字段（画布客户端从不消费：items=画布handler无槽位、双缓存/绑定/运行池/强载/所有权纯服务端）
        ContainerHelper.saveAllItems(nbt, items, lookup);
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
        if (boundPanelPos != null && boundPanelDim != null) {
            nbt.putLong("boundPos", boundPanelPos.asLong());
            nbt.putString("boundDim", boundPanelDim);
        }
        nbt.putBoolean("running", running);
        nbt.putDouble("xpPool", xpPool);
        ListTag flc = new ListTag(); // m133 待续票端点区块（重启自举清单）
        for (int i = 0; i < forceChunks.size(); i++) {
            CompoundTag fc = new CompoundTag();
            fc.putLong("c", forceChunks.get(i)[0]);
            fc.putInt("m", (int) forceChunks.get(i)[1]);
            fc.putString("d", forceDims.get(i));
            flc.add(fc);
        }
        nbt.put("forceChunks", flc);
        nbt.putBoolean("chunkOwned", chunkOwned); // m268 强加载所有权（管理员 /forceload 撞同区块=false，重启后据此判断该不该解除）
        // m275：渲染子集与观众定向同步快照共用同一编码函数——加渲染字段只改 writeRenderNbt，存档/同步自动同拍不漂移
        g.writeRenderNbt(nbt, lookup);
    }


    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        ContainerHelper.loadAllItems(nbt, items, lookup);
        g.readRenderNbt(nbt, lookup, MERGED_IDS, this::bumpTopo); // m275：渲染子集先读——下方 nodeBufs 循环依赖 machineNodes.size()
        internalBuffer.clear();
        CompoundTag buf = nbt.getCompound("internalBuffer");
        int droppedBuf = 0; // m273：缓存读入校验——写路径 left<=0 即 remove，零/负值从不合法落盘；负数毒化计数算术且可绕封顶
        for (String k : buf.getAllKeys()) {
            long v = buf.getLong(k);
            if (!k.isEmpty() && v > 0) internalBuffer.put(k, v); else droppedBuf++;
        }
        nodeBufs.clear();
        ListTag nbl = nbt.getList("nodeBufs", Tag.TAG_COMPOUND);
        for (int i = 0; i < g.machineNodes.size(); i++) {
            java.util.Map<String, Long> m = new java.util.HashMap<>();
            if (i < nbl.size()) {
                CompoundTag c = nbl.getCompound(i);
                for (String k : c.getAllKeys()) {
                    long v = c.getLong(k);
                    if (!k.isEmpty() && v > 0) m.put(k, v); else droppedBuf++; // m273 同口径
                }
            }
            nodeBufs.add(m); // 老档无此键=全空缓存；共享池留在 internalBuffer 里继续被消耗（无损迁移）
        }
        if (droppedBuf > 0) com.sdzjz.Sdzjz.LOGGER.warn("结构核心 {} 缓存读入丢弃 {} 条非法条目（空键或非正计数）", worldPosition, droppedBuf);
        if (nbt.contains("boundPos")) {
            boundPanelPos = BlockPos.of(nbt.getLong("boundPos"));
            boundPanelDim = nbt.getString("boundDim");
        } else {
            boundPanelPos = null; boundPanelDim = null;
        }
        running = nbt.getBoolean("running");
        xpPool = nbt.getDouble("xpPool");
        forceChunks.clear();
        forceDims.clear();
        ListTag flc = nbt.getList("forceChunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < flc.size(); i++) {
            CompoundTag fc = flc.getCompound(i);
            long c = fc.getLong("c");
            if (!plausibleChunkLong(c)) continue; // m142：清洗 m133~m141 存档里落盘的毒区块（读入即自愈）
            forceChunks.add(new long[]{c, fc.getInt("m")});
            forceDims.add(fc.getString("d"));
        }
        chunkOwned = nbt.getBoolean("chunkOwned"); // m268 缺键=false（老档/新核心默认无所有权，force 首拍会重新判定并落盘）
    }


    /** m275：客户端收到观众定向渲染快照时调用（SdzjzClient 收端）——只写渲染字段，
     *  存档专属字段（items/双缓存/强载等）客户端本就不消费不触碰。 */
    public void applyRenderSnapshot(CompoundTag nbt, HolderLookup.Provider lookup) {
        g.readRenderNbt(nbt, lookup, MERGED_IDS, this::bumpTopo);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        // m276：区块追踪初始同步瘦身——路过玩家（含 vanilla BlockEntityUpdateS2CPacket.create 默认取数）
        // 只收渲染子集，不再收存档级全量（items/双缓存/强载/所有权客户端从不消费）；
        // 客户端 readNbt 对缺键全容忍：ContainerHelper 缺键=空、双缓存空表、running/xpPool/chunkOwned 走默认。
        CompoundTag nbt = new CompoundTag();
        g.writeRenderNbt(nbt, lookup);
        return nbt;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // ================= GUI 工厂 =================
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sdzjz.structure_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        syncToClient(); // m181 开画布即时强刷：兜底改为"有人看才发"后，开屏首帧鲜度由这里兜住（服务端调用，客户端侧 syncToClient 自判跳过）
        return new StructureCoreScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
        return this.worldPosition;
    }

    // ================= Container =================
    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack r = ContainerHelper.removeItem(items, slot, amount);
        if (!r.isEmpty()) setChanged();
        return r;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxStackSize()) stack.setCount(stack.getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public void clearContent() { items.clear(); }

    public void toggleRunning(boolean run) { if (run && !this.running) lastEndpointScan = -1000; this.running = run; setChanged(); } // m348 停→开哨兵强刷端点，慢拍陈旧窗清零
    public boolean isRunning() { return running; }

    // ===== m481 路由域跨代行为契约探针（纯加法：四行转发，不改任何现有方法体）=====
    // 契约本体在 xplat com.sdzjz.node.RouteDomainAssertions，两代各喂自己的实现跑同一套断言。
    // C1 路由脑下沉之后，转发目标会变成共用实现，而断言必须继续全绿——契约先立、手术后做。

    @Override
    public boolean probeAccepts(Object level, int target, String id) {
        return accepts0((Level) level, target, id);
    }

    @Override
    public boolean probeChainWants(Object level, int from, String id) {
        return chainWants0((Level) level, from, id, 0, new java.util.HashSet<>());
    }

    @Override
    public boolean probeSensorOpen(Object level, int i) {
        return sensorOpen((Level) level, i);
    }

    @Override
    public boolean probeExtractorLive(Object level, int i, ItemStack st) {
        return extractorLive((Level) level, i, st);
    }

    // ===== m486（真移植·C 阶段主刀）：路由脑判定层已下沉共用（xplat node/RouteBrain）=====
    // 原 157 行实现整段搬走，本类只留转发；1.20.1 那份同功能重写同刀删除。
    // 行为由 m481 的路由域跨代契约压着（RouteDomainAssertions 六条成对判定），两代同绿。
    private final com.sdzjz.node.RouteBrain.Host brainHost = new com.sdzjz.node.RouteBrain.Host() {
        @Override public int nodeCount() { return g.machineNodes.size(); }
        @Override public ItemStack nodeStack(int i) { return g.machineNodes.get(i); }
        @Override public long sensorObserved(int i, String id) {
            com.sdzjz.machine.StorageAccess sc = supplyFor(level, i);
            if (sc == null) sc = resolveInputSource(level, worldPosition); // 主线特有：默认主存储回落
            return sc == null ? 0L : sc.count(id);
        }
        @Override public void countChainCheck() { if (prof != null) prof.chainChecks++; } // m177
    };

    private boolean accepts0(Level world, int target, String id) {
        return com.sdzjz.node.RouteBrain.accepts(brainHost, world, target, id);
    }

    private boolean chainWants0(Level world, int i, String id, int depth,
                                java.util.Set<Integer> visited, int[][] outT,
                                java.util.Map<Integer, java.util.Set<String>> crafterNeeds) {
        return com.sdzjz.node.RouteBrain.chainWants(brainHost, world, i, id, depth, visited, outT, crafterNeeds);
    }

    private boolean chainEndsInTrash(Level world, int i, String id, int depth,
                                     java.util.Set<Integer> visited, int[][] outT) {
        return com.sdzjz.node.RouteBrain.chainEndsInTrash(brainHost, world, i, id, depth, visited, outT);
    }

    boolean sensorOpen(Level world, int i) {
        return com.sdzjz.node.RouteBrain.sensorOpen(brainHost, world, i);
    }

    boolean extractorLive(Level world, int i, ItemStack st) {
        return com.sdzjz.node.RouteBrain.extractorLive(brainHost, world, i, st);
    }

    private boolean allGatesClosed(Level world, int[] targets) {
        return com.sdzjz.node.RouteBrain.allGatesClosed(brainHost, world, targets);
    }

}
