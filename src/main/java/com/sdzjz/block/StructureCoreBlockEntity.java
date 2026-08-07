package com.sdzjz.block;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import com.sdzjz.screen.StructureCoreScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import com.sdzjz.item.MachineItem;
import com.sdzjz.item.AutoCrafterItem;
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.machine.MachineDef;
import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.machine.MachineXp;
import com.sdzjz.machine.MobDrops;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.ItemScatterer;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
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
public class StructureCoreBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory {

    public static final int MACHINE_START = 0, MACHINE_SLOTS = 8;
    public static final int UPGRADE_START = 8, UPGRADE_SLOTS = 3;
    public static final int OUTPUT_START = 11, OUTPUT_SLOTS = 8;
    public static final int SIZE = MACHINE_SLOTS + UPGRADE_SLOTS + OUTPUT_SLOTS; // 19

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private final java.util.List<ItemStack> machineNodes = new java.util.ArrayList<>();
    private final java.util.List<int[]> connections = new java.util.ArrayList<>(); // {from, to} 节点下标
    private final java.util.LinkedHashMap<Integer, String> groupNames = new java.util.LinkedHashMap<>(); // m191 画布分组 id→名（成员归属存各节点栈 NBT "gp"，随栈走免下标重映射）
    public transient com.sdzjz.debug.CoreProfiler.Stats prof; // m177 性能尺子(纯内存,不入NBT)
    // m179 编译执行计划：拓扑派生结构(hasOut/hasIn/outT)只在图变更时重建，普通 tick 直接复用缓存
    // ——修订号失配才编译；长度兜底防漏 bump；派生列表运行期只读(已核)所以可跨 tick 共享。
    private transient int topoRev = 1;
    private transient int planRev = 0;
    private transient boolean[] planHasOut, planHasIn;
    private transient java.util.Map<Integer, java.util.List<Integer>> planOutT;
    private void bumpTopo() { topoRev++; }
    private final java.util.Map<String, Long> internalBuffer = new java.util.HashMap<>(); // 遗留共享池（老档迁移+节点删除回收），消耗时兜底
    private final java.util.List<java.util.Map<String, Long>> nodeBufs = new java.util.ArrayList<>(); // 每节点输入缓存：连线按边精确路由
    private static final long BUF_CAP = 200000L;

    // ===== 存储/终端接口节点（画布右侧显示，连了几个显示几个） =====
    /** 已扫描到的接口端点：{posLong, kind}，kind 0=绑定 1=有线 2=无线 3=卫星 4=离线(仅被连线引用) 5=数据终端。 */
    private final java.util.List<long[]> storageEndpoints = new java.util.ArrayList<>();
    private final java.util.List<String> storageEndpointDims = new java.util.ArrayList<>(); // 与上表同序的维度 id
    private final java.util.Map<Long, int[]> storageNodePos = new java.util.HashMap<>();    // posLong → 画布坐标
    /** 机器↔存储 定向连线：{machineIndex, posLong, dir}，dir 0=机器→存储(产出) 1=存储→机器(供料)。 */
    private final java.util.List<long[]> storageEdges = new java.util.ArrayList<>();
    private final java.util.List<String> storageEdgeDims = new java.util.ArrayList<>();
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

    public final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> running ? 1 : 0;
                case 1 -> machineCount();
                case 2 -> tierOf();
                case 3 -> totalNodeUpgrade("spd");
                case 4 -> totalNodeUpgrade("cnt");
                case 5 -> totalNodeUpgrade("par");
                case 6 -> (int) (((long) xpPool) & 0x7FFF);              // 经验低15位（属性按short网络同步）
                case 7 -> (int) Math.min(((long) xpPool) >> 15, 32767);  // 经验高位
                case 8 -> (int) (bufferedTotal() & 0x7FFF);              // 在途缓存低15位
                case 9 -> (int) Math.min(bufferedTotal() >> 15, 32767);  // 在途缓存高位
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { if (i == 0) running = (v != 0); }
        @Override public int size() { return 10; }
    };

    private int machineCount() {
        int n = 0;
        for (ItemStack st : machineNodes) n += st.getCount();
        return n;
    }

    private int tierOf() {
        if (world == null) return 1;
        return world.getBlockState(pos).getBlock() instanceof StructureCoreBlock scb ? scb.tier : 1;
    }

    public StructureCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE_CORE_BE, pos, state);
    }

    // ================= 运行时（通用：按 MachineDef 跑任意机器）=================
    public static void tick(World world, BlockPos pos, BlockState state, StructureCoreBlockEntity be) {
        if (world.isClient) return;
        if (be.prof == null) be.prof = com.sdzjz.debug.CoreProfiler.register(world, pos); // m177
        long t0 = System.nanoTime();
        try {
            tickInner(world, pos, state, be);
        } finally {
            com.sdzjz.debug.CoreProfiler.record(be.prof, be.machineNodes.size(), be.connections.size(), be.running, System.nanoTime() - t0);
        }
    }

    private static void tickInner(World world, BlockPos pos, BlockState state, StructureCoreBlockEntity be) {
        be.recipesThisTick = 0; // m270 全核tick周期预算复位（cyclesThisTick 共享额度）
        be.flushCanvasSnapshot(world); // m275：上一拍标脏的渲染快照在此聚合、定向发观众（每 tick 至多 1 份/核心）
        // m218c 多核心错峰：周期性大活（ends包/区块票/拉料拍/端点扫描）按 pos 哈希移相——频率逐核不变，
        // 只是不同核心不再挤同一全局 tick（m181 给 m88 兜底同步用过的同款药方，推广到其余四拍）。
        int ph = SdzjzConfig.get().coreTickStagger ? pos.hashCode() : 0;
        long wt = world.getTime() + ph;
        // m218c 端点扫描错峰（回查自纠：初版"每次回拨相位"是复利——last=now-p 让下次在 40-p 就触发，
        // 周期被永久压成 40-p 反而提频。改日历拍：稳态严格 40t、相位由 wt 哈希天然错开；-1000 哨兵=
        // 刚加载首扫即时，画布随时能看到接口的原语义不变，首扫后至多 39t 内并入日历拍）。
        if (be.lastEndpointScan <= -1000 || Math.floorMod(wt, 40) == 0) {
            be.lastEndpointScan = world.getTime();
            be.scanStorageEndpoints(world, pos);
        }
        // m88 兜底：每10秒强制同步（治"changed判否漏同步"的一切边角）。m181 瘦身两刀：
        // ① pos 哈希错峰——多核心不再同一全局 tick 齐发；② 无人看画布不发——事件同步照旧，
        // 开画布首帧鲜度由 createMenu 即时强刷保证，观众在场后 ≤10s 内兜底节奏恢复，最坏陈旧窗与旧行为等长。
        // m276 起本行=标脏→flushCanvasSnapshot 升版本重发渲染快照给观众（自愈兜底防标脏遗漏类 bug 永久失同步）。
        if (Math.floorMod(world.getTime() + pos.hashCode(), 200) == 0 && be.hasCanvasViewer(world)) be.syncToClient();
        // m115 过载保护：平均 tick >45ms 全线自动暂停（<40ms 恢复，滞回防抖）；>60ms 清理本核心喷出的掉落物
        if (be.ticks % 20 == 0 && world instanceof net.minecraft.server.world.ServerWorld sw115) {
            float ms = sw115.getServer().getAverageTickTime();
            boolean was = be.lagPause;
            if (ms > 45f) be.lagPause = true; else if (ms < 40f) be.lagPause = false;
            if (be.lagPause && !was) be.warnNearby(world, "『生电终结者』服务器过载(平均 " + String.format("%.0f", ms)
                    + "ms/tick)，本核心机器已自动暂停，恢复流畅后自动续跑");
            if (ms > 60f) be.cleanupEjected(sw115);
        }
        // m89：端点+总线库存 直发正在看画布的玩家（BE同步链实机不生效的最终修复——走已被证明可靠的包通道）
        if (Math.floorMod(wt, 40) == 0 && world instanceof net.minecraft.server.world.ServerWorld sw) { // m218c 错峰
            com.sdzjz.net.CanvasEndsPayload pk = null;
            for (net.minecraft.server.network.ServerPlayerEntity sp : sw.getServer().getPlayerManager().getPlayerList()) {
                if (!(sp.currentScreenHandler instanceof com.sdzjz.screen.StructureCoreScreenHandler h)
                        || !pos.equals(h.blockPos())) continue;
                if (pk == null) pk = be.buildEndsPayload(pos);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, pk);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, be.buildHomesPayload(pos)); // m265 放置落位姊妹包（同拍同通道，只含已放置项通常极小）
                if (be.prof != null) { be.prof.endsPackets++; be.prof.endsEntries += pk.endPos().size(); } // m177
            }
        }
        // m133 强制加载：开机+配置开 → 钉住自身区块(FORCED,持久化,重启自恢复) + 每100t给端点区块续有期票；
        // 停机/配置关 → 解除；孤儿 forced（重启遗留：停机核心落盘前没来得及解除）每100t回收一次。
        if (Math.floorMod(wt, 20) == 0 && world instanceof net.minecraft.server.world.ServerWorld swf) { // m218c 错峰（内层%100同偏移，嵌套节奏不变）
            boolean want = be.running && SdzjzConfig.get().coreChunkLoading;
            if (want != be.chunkForceOn) {
                if (want) { be.chunkOwned = CoreChunkLoading.force(swf, pos, be.chunkOwned); be.markDirty(); } // m268 传入既有所有权,重启后保持不误判
                else CoreChunkLoading.release(swf, pos, be.chunkOwned);
                be.chunkForceOn = want;
            }
            if (Math.floorMod(wt, 100) == 0) {
                if (want) {
                    be.refreshForceChunks(world);
                    be.renewEndpointTickets(swf);
                } else if (swf.getForcedChunks().contains(new net.minecraft.util.math.ChunkPos(pos).toLong())) {
                    CoreChunkLoading.reclaimOrphan(swf, pos, be.chunkOwned); // m268 孤儿回收凭核心持久化的所有权判定，管理员 /forceload 永不误伤
                }
            }
        }
        if (!be.running) return;

        int tier = (state.getBlock() instanceof StructureCoreBlock scb) ? scb.tier : 1;
        SdzjzConfig cfg = SdzjzConfig.get();
        be.ticks++;
        if (be.ticks - be.prodWinStart >= 1200) { // m86 实测产量：每分钟滚动
            be.prodPerMin = be.prodWin;
            be.prodWin = 0;
            be.prodWinStart = be.ticks;
            be.markDirty();
            be.syncToClient();
        }
        int nSize = be.machineNodes.size();
        if (nSize == 0) return;

        // 连线拓扑（按边精确路由）：产物只流向出线指向的目标节点缓存；有入线的消耗机吃自己的缓存
        // m179 编译执行计划：只在 topoRev 变更(或长度兜底失配)时重建，其余 tick 零分配直用缓存
        if (be.planRev != be.topoRev || be.planHasOut == null || be.planHasOut.length != nSize) {
            boolean[] cHasOut = new boolean[nSize];
            boolean[] cHasIn = new boolean[nSize];
            java.util.Map<Integer, java.util.List<Integer>> cOutT = new java.util.HashMap<>();
            for (int[] c : be.connections()) {
                if (c[0] >= 0 && c[0] < nSize && c[1] >= 0 && c[1] < nSize) {
                    cHasOut[c[0]] = true;
                    cHasIn[c[1]] = true;
                    cOutT.computeIfAbsent(c[0], k -> new java.util.ArrayList<>()).add(c[1]);
                }
            }
            be.planHasOut = cHasOut; be.planHasIn = cHasIn; be.planOutT = cOutT;
            be.planRev = be.topoRev;
            if (be.prof != null) be.prof.planCompiles++; // m179 尺子：编译次数（稳态应≈0增长）
        }
        boolean[] hasOut = be.planHasOut;
        boolean[] hasIn = be.planHasIn;
        java.util.Map<Integer, java.util.List<Integer>> outT = be.planOutT;

        // m92：逻辑节点供料拉取·链式需求传播（连接系统补完）——任何逻辑节点(过滤/开关/传感/分配)接了
        // "存储→自己"的供料边，都按「自身放行规则 ∩ 下游机器真实需求」拉料。遍历的是仓库类型清单（有限），
        // 熔炉需求=可熔炼表、合成机需求=目标配方材料、消耗机需求=定义 inputs；支持逻辑节点串联（深度8+防环）。
        if (Math.floorMod(be.ticks + ph, 5) == 0) { // m116：20t→5t 与逻辑节点转发同拍；m218c 多核心移相（此前 64/20t=64/秒天花板，用户熔炉组升到 50/50/54 也只吃到 100/秒）
            java.util.Map<Integer, java.util.Set<String>> crafterNeeds = new java.util.HashMap<>();
            for (int i = 0; i < nSize; i++) {
                ItemStack stL = be.machineNodes.get(i);
                if (!(isFilter(stL) || isSwitch(stL) || isSensor(stL) || isDistributor(stL) || isExtractor(stL))) continue;
                boolean pump = isExtractor(stL); // m154 抽取节点=主动泵
                if (pump && !be.extractorLive(world, i, stL)) continue; // m160 手动关或感应未放行=不抽
                boolean pumpAll = pump && be.depositFor(world, i) != null; // m157 有定向存储出线=搬仓，全抽
                com.sdzjz.machine.StorageAccess sup = be.supplyFor(world, i);
                if (sup == null) continue;
                java.util.Map<String, Long> ownL = be.nodeBuf(i);
                long pumpRate = 0, pumped = 0; // m159 泵速率=抽取量挡位×(1+数量升级)，缓存上限随速率放宽
                long bufCapL = 4096;
                if (pump) {
                    pumpRate = extractorRate(stL) * (1 + be.nodeCount(stL));
                    // m163a：撤 BUF_CAP 钳位——新高挡(32768/262144)×升级后 速率×2 远超 20 万，钳住就回到
                    // m159 修过的"速率>缓存卡喉"。泵缓存只是 id→long 计数不占实存，直接放到双周期余量。
                    bufCapL = Math.max(4096, pumpRate * 2);
                }
                for (var en : new java.util.ArrayList<>(sup.storeView().entrySet())) {
                    String id = en.getKey();
                    long have = ownL.getOrDefault(id, 0L);
                    if (have >= bufCapL) continue; // m116 每种封顶（泵按速率放宽）：链式需求门控仍在，在途量经 8/9 号属性可见
                    if (!pump && !be.chainWants(world, i, id, 0, be.wantsScratchCleared(), outT, crafterNeeds)) continue; // m218d scratch复用
                    if (pump && !machineFilterAllows(stL, id)) continue; // m160 抽取白名单：名单外碰都不碰
                    if (pump && !pumpAll) {
                        // m157（用户实测：猪人塔/幽匿线产物"消失"）：m154 的无条件抽把全网络吸进
                        // 缓存囤着失踪（每种4096）——改为"没有去处的不抽"：出线机器目标当下肯收
                        // （过滤白名单在 accepts 里生效→只抽名单内）才抽；搬仓走上面 pumpAll。
                        boolean anyTake = false;
                        java.util.List<Integer> tgP = outT.get(i);
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
                            String idE = Registries.ITEM.getId(t.getItem()).toString();
                            long haveE = ownL.getOrDefault(idE, 0L);
                            if (haveE >= bufCapL) continue; // m163a：原硬编码 4096 没跟 bufCapL 统一——泵开高挡后精确支路先被卡死
                            if (isExtractor(stL) && !machineFilterAllows(stL, idE)) continue; // m160
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

        boolean produced = false;
        StorageCoreBlockEntity src = null;
        boolean srcResolved = false;

        for (int i = 0; i < nSize; i++) {
            ItemStack st = be.machineNodes.get(i);
            int speedLv = be.nodeSpeed(st);
            int countLv = be.nodeCount(st);
            int parallelLv = be.nodePar(st);

            // m115 过载保护：全线暂停（黄灯），流畅后自动恢复
            if (be.lagPause) { be.statR(i, 2, "服务器过载，自动暂停"); continue; }
            // m110b 单节点暂停：最先判——不产不耗不攒进度（m99 教训：early-continue 必须在累积之前）
            if (StructureCoreBlockEntity.nodePaused(st)) { be.statR(i, 2, "已手动暂停"); continue; }

            // 传感器闸门：该节点全部出线目标都关闸 → 整台暂停（不白产、不绕道塞存储）
            if (hasOut[i] && be.allGatesClosed(world, outT.get(i))) {
                be.statR(i, 2, "下游闸门全关");
                continue;
            }

            if (StructureCoreBlockEntity.isDistributor(st)) {
                // 分配器节点：来料在出线目标间均分（余数轮转），没人要的走默认路由
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> ownD = be.nodeBuf(i);
                if (ownD.isEmpty()) continue;
                boolean movedD = false;
                for (String id : new java.util.ArrayList<>(ownD.keySet())) {
                    long amt = ownD.getOrDefault(id, 0L);
                    ownD.remove(id);
                    if (amt <= 0) continue;
                    be.distributeEven(world, i, outT.get(i), id, amt);
                    movedD = true;
                }
                if (movedD) { be.stat(i, 1); produced = true; }
            } else if (StructureCoreBlockEntity.isFilter(st)) {
                // 过滤器节点：清运自己的输入缓存——放行的沿出线下游，拦下的走定向存储/默认路由
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> own = be.nodeBuf(i);
                if (own.isEmpty()) continue;
                boolean moved = false;
                for (String id : new java.util.ArrayList<>(own.keySet())) {
                    long amt = own.getOrDefault(id, 0L);
                    own.remove(id);
                    if (amt <= 0) continue;
                    if (StructureCoreBlockEntity.filterPasses(st, id)) be.distribute(world, i, outT.get(i), id, amt);
                    else be.distribute(world, i, null, id, amt);
                    moved = true;
                }
                if (moved) { be.stat(i, 1); produced = true; }
            } else if (StructureCoreBlockEntity.isTrash(st)) {
                // m150 垃圾桶（用户点名：过滤器收想要的，其余连进来销毁）：吞光输入缓存并累计 "tc"。
                // 终点节点无出线语义；不进中继拉料回路（防 仓→垃圾桶 手滑清空仓库——只吞推送来的）。
                if (be.ticks % 5 != 0) continue;
                java.util.Map<String, Long> ownTr = be.nodeBuf(i);
                if (ownTr.isEmpty()) continue;
                long ate = 0;
                for (String id : new java.util.ArrayList<>(ownTr.keySet())) {
                    Long amt = ownTr.remove(id);
                    if (amt != null && amt > 0) ate += amt;
                }
                if (ate > 0) {
                    NbtCompound nTr = nbtOf(st);
                    nTr.putLong("tc", nTr.getLong("tc") + ate);
                    be.markDirty();
                    be.stat(i, 1);
                    produced = true;
                }
            } else if (StructureCoreBlockEntity.isExtractor(st)) {
                // m154 抽取节点（用户点名：点击抽取才开始，抽走物品流动）：开=主动泵——拉料回路
                // pump 分支已无条件抽上游仓入缓存，这里把缓存沿出线推给"收的"目标；推不出去的
                // 留在缓存（背压：缓存到顶 4096 拉料自然停），绝不走默认路由——否则抽出来又
                // 存回同一个仓，每 5t 空转刷账。垃圾桶目标照 distribute 规矩两轮垫底。
                if (be.ticks % 5 != 0) continue;
                if (StructureCoreBlockEntity.extractorOn(st) && !be.extractorLive(world, i, st)) {
                    be.stat(i, 2); // m160 感应暂停：持料待命不退料（条件一到自动续跑）
                    continue;
                }
                if (!StructureCoreBlockEntity.extractorOn(st)) {
                    // m157 歇工退料：关机把缓存沿默认路由退回存储——用户被 m154 无条件抽吸走的
                    // 货，点一下"停止抽取"就全数找回，不留缓存黑洞。
                    java.util.Map<String, Long> ownOff = be.nodeBuf(i);
                    if (!ownOff.isEmpty()) {
                        for (String id : new java.util.ArrayList<>(ownOff.keySet())) {
                            long amt = ownOff.remove(id);
                            if (amt > 0) be.distribute(world, i, null, id, amt);
                        }
                        produced = true;
                    }
                    be.stat(i, 2);
                    continue;
                }
                java.util.Map<String, Long> ownX = be.nodeBuf(i);
                boolean movedX = false;
                java.util.List<Integer> tgX = outT.get(i);
                for (String id : new java.util.ArrayList<>(ownX.keySet())) {
                    long amt = ownX.getOrDefault(id, 0L);
                    if (amt <= 0) { ownX.remove(id); continue; }
                    long left = amt;
                    if (tgX != null) {
                        for (int pass = 0; pass < 2 && left > 0; pass++) {
                            for (int t : tgX) {
                                if (left <= 0) break;
                                if (t < 0 || t >= be.machineNodes.size()) continue;
                                if ((pass == 0) == StructureCoreBlockEntity.isTrash(be.machineNodes.get(t))) continue;
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
                            be.depositOrBuffer(depX, new ItemStack(Registries.ITEM.get(Identifier.of(id)),
                                    (int) Math.min(left, Integer.MAX_VALUE)));
                            left = 0;
                        }
                    }
                    if (left != amt) movedX = true;
                    if (left > 0) ownX.put(id, left); else ownX.remove(id);
                }
                be.stat(i, movedX ? 1 : 0);
                if (movedX) produced = true;
            } else if (StructureCoreBlockEntity.isSwitch(st)) {
                // 开关节点：开=直通转发，关=持料不动
                if (be.ticks % 5 != 0) continue;
                if (!StructureCoreBlockEntity.switchOn(st)) { be.stat(i, 2); continue; }
                java.util.Map<String, Long> ownSw = be.nodeBuf(i);
                boolean movedSw = false;
                for (String id : new java.util.ArrayList<>(ownSw.keySet())) {
                    long amt = ownSw.getOrDefault(id, 0L);
                    ownSw.remove(id);
                    if (amt <= 0) continue;
                    be.distribute(world, i, outT.get(i), id, amt);
                    movedSw = true;
                }
                be.stat(i, movedSw ? 1 : 0);
                if (movedSw) produced = true;
            } else if (StructureCoreBlockEntity.isSensor(st)) {
                // 传感器节点：开闸=直通转发自己的缓存；关闸=持料不动（缓存封顶后上游自然停）
                if (be.ticks % 5 != 0) continue;
                if (!be.sensorOpen(world, i)) { be.stat(i, 2); continue; }
                java.util.Map<String, Long> own = be.nodeBuf(i);
                boolean moved = false;
                for (String id : new java.util.ArrayList<>(own.keySet())) {
                    long amt = own.getOrDefault(id, 0L);
                    own.remove(id);
                    if (amt <= 0) continue;
                    be.distribute(world, i, outT.get(i), id, amt);
                    moved = true;
                }
                be.stat(i, moved ? 1 : 0);
                if (moved) produced = true;
            } else if (st.getItem() instanceof AutoCrafterItem) {
                // 自动合成机：按原版配方吃料出货。目标在画布上设置（节点徽章）。
                String target = craftTarget(st);
                if (target.isEmpty()) continue;
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg); // m99 工作量累积，速度永不触底
                if (cycles <= 0) continue;
                java.util.List<CraftPlanner.Plan> planC = CraftPlanner.plans(world, target); // m234 全候选（原版排前）
                if (planC.isEmpty()) continue; // 无合成配方
                CraftPlanner.Plan chosen = null; // m235 手选配方（空/失效=自动；缺料按手选那条报）
                String chosenR = craftRecipe(st);
                if (!chosenR.isEmpty()) for (CraftPlanner.Plan pp : planC) if (pp.recipeId().equals(chosenR)) { chosen = pp; break; }
                int running = runningCount(st, parallelLv, tier);   // m99 并发直接乘台数
                long crafts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess depositAc = hasOut[i] ? null : be.depositFor(world, i); // 提前解析：封顶只对"进内部缓存"生效
                int maxStack = Registries.ITEM.get(Identifier.of(target)).getMaxCount();
                CraftPlanner.Plan plan; // m234 拿到库存视图后再定案（谁的料齐用谁；都不齐回退首候选按原版材料报缺料）
                if (hasIn[i]) {
                    final int fi = i;
                    plan = chosen != null ? chosen : CraftPlanner.pick(planC, k -> be.bufCountFor(fi, k)); // m235
                    if (!hasOut[i] && depositAc == null)
                        crafts = Math.min(crafts, ((long) maxStack * OUTPUT_SLOTS) / Math.max(1, plan.resultCount())); // m99 只在无存储时封顶（剑/图腾等max=1时防白扣）
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.bufCountFor(i, en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.statR(i, 3, be.whyMissingBuf(i, plan.needs())); continue; }
                    for (var en : plan.needs().entrySet())
                        be.bufWithdrawFor(i, en.getKey(), (long) en.getValue() * crafts);
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
                    plan = chosen != null ? chosen : CraftPlanner.pick(planC, k -> sup.count(k)); // m235
                    if (!hasOut[i] && depositAc == null)
                        crafts = Math.min(crafts, ((long) maxStack * OUTPUT_SLOTS) / Math.max(1, plan.resultCount())); // m99 同上
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, supply.count(en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.statR(i, 3, be.whyMissingSup(supply, plan.needs())); continue; }
                    for (var en : plan.needs().entrySet())
                        supply.withdraw(en.getKey(), (int) ((long) en.getValue() * crafts));
                }
                be.stat(i, 1);
                int total = (int) Math.min(Integer.MAX_VALUE, crafts * plan.resultCount());
                be.prodTally(total); // m86 实测产量
                if (hasOut[i]) be.distribute(world, i, outT.get(i), target, total);
                else if (depositAc != null) be.depositOrBuffer(depositAc, new ItemStack(Registries.ITEM.get(Identifier.of(target)), total));
                else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(target)), total));
                for (var en : plan.remainders().entrySet()) { // 容器残留（桶等）返还
                    int rc = (int) Math.min(64L * OUTPUT_SLOTS, (long) en.getValue() * crafts);
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), en.getKey(), rc);
                    else if (depositAc != null) be.depositOrBuffer(depositAc, new ItemStack(Registries.ITEM.get(Identifier.of(en.getKey())), rc));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(en.getKey())), rc));
                }
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem) {
                // 酿造塔（m131b）：按原版酿造链吃料出药水，目标在画布节点徽章选择。
                // 产物带 POTION_CONTENTS——不走 distribute/内部缓存（id 账本会抹组件），
                // 出线一律无视，只走 存储入库（m130 精确账本自动分流）或输出缓存（addOutput 已保组件）。
                String target = craftTarget(st);
                if (target.isEmpty()) continue;
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                com.sdzjz.machine.BrewPlanner.Plan plan = com.sdzjz.machine.BrewPlanner.plan(world, target);
                if (plan == null) continue; // 目标串非法/酿造不可达
                int running = runningCount(st, parallelLv, tier);
                long crafts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess depositBt = be.depositFor(world, i);
                if (depositBt == null)
                    crafts = Math.min(crafts, (long) OUTPUT_SLOTS / com.sdzjz.machine.BrewPlanner.BOTTLES_PER_BATCH); // 药水 max=1，无存储时封顶防白扣
                int steps = plan.steps();
                int fuelNd = plan.needs().getOrDefault(com.sdzjz.machine.BrewPlanner.FUEL_ID, 0); // 力量药水的材料烈焰粉，与燃料两账并存
                int ops = com.sdzjz.machine.BrewPlanner.OPS_PER_FUEL;
                if (hasIn[i]) {
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.bufCountFor(i, en.getKey()) / en.getValue());
                    long fuelAvail = be.bufCountFor(i, com.sdzjz.machine.BrewPlanner.FUEL_ID);
                    crafts = Math.min(crafts, fuelAvail * ops / ((long) fuelNd * ops + steps));
                    while (crafts > 0 && (long) fuelNd * crafts + (crafts * steps + ops - 1) / ops > fuelAvail) crafts--; // ceil 兜底
                    if (crafts <= 0) { String w178 = be.whyMissingBuf(i, plan.needs()); if (w178.startsWith("缺料（")) w178 = "缺料：烈焰粉（燃料）"; be.statR(i, 3, w178); continue; }
                    for (var en : plan.needs().entrySet())
                        be.bufWithdrawFor(i, en.getKey(), (long) en.getValue() * crafts);
                    be.bufWithdrawFor(i, com.sdzjz.machine.BrewPlanner.FUEL_ID, (crafts * steps + ops - 1) / ops);
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
                ItemStack brewOut = plan.result().copyWithCount(total);
                if (depositBt != null) be.depositOrBuffer(depositBt, brewOut);
                else be.addOutput(brewOut);
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem) {
                // 附魔工厂（m132）：按目标附魔+等级吃 书+青金石+经验（核心经验池）出附魔书。
                // 产物带 ENCHANTMENTS 组件——与酿造塔同款出路：不走 distribute/id 账本，
                // 出线一律无视，只走 存储入库（m130 精确账本）或输出缓存（addOutput 保组件）。
                String target = craftTarget(st);
                if (target.isEmpty()) continue;
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                com.sdzjz.machine.EnchantPlanner.Plan plan = com.sdzjz.machine.EnchantPlanner.plan(world, target);
                if (plan == null) continue; // 目标串非法/附魔不存在（数据包变更等）
                int running = runningCount(st, parallelLv, tier);
                long crafts = (long) running * (1 + countLv) * cycles;
                com.sdzjz.machine.StorageAccess depositEf = be.depositFor(world, i);
                if (depositEf == null)
                    crafts = Math.min(crafts, OUTPUT_SLOTS); // 附魔书 max=1，无存储时封顶防白扣
                crafts = Math.min(crafts, (long) (be.xpPool / plan.xpCost())); // 经验闸：池里够几本合几本
                if (crafts <= 0) { be.statR(i, 3, "经验池不足（本单需 " + plan.xpCost() + "，现 " + (long) be.xpPool + "）"); continue; }
                if (hasIn[i]) {
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.bufCountFor(i, en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.statR(i, 3, be.whyMissingBuf(i, plan.needs())); continue; }
                    for (var en : plan.needs().entrySet())
                        be.bufWithdrawFor(i, en.getKey(), (long) en.getValue() * crafts);
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
                ItemStack enchOut = plan.result().copyWithCount(totalEf);
                if (depositEf != null) be.depositOrBuffer(depositEf, enchOut);
                else be.addOutput(enchOut);
                produced = true;
            } else if (st.getItem() instanceof com.sdzjz.item.CropFarmItem) {
                // 全自动农场：按所选作物产出（免费，对齐原版农场）。m93：多选≤8种，逐种产出
                java.util.List<String> cropsSel = cropList(st);
                if (cropsSel.isEmpty()) { be.stat(i, 0); continue; } // 未选作物=待机
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = runningCount(st, parallelLv, tier);    // m99 并发直接乘台数
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
                    long sum = be.rollDrops(world.getRandom(), d, cycles, countLv);
                    if (sum <= 0) continue;
                    long total = (long) running * sum * cropUnit;
                    if (cappedCf) total = Math.min(total, 64L * OUTPUT_SLOTS);
                    be.prodTally(total); // m86 实测产量
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), total);
                    else if (depositCf != null) be.depositOrBuffer(depositCf, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) Math.min(total, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) total));
                    produced = true;
                }
            } else if (st.getItem() instanceof MachineItem miu && com.sdzjz.machine.Machines.smelterFamily(miu.def().id())) { // m173 熔炉族
                // 万能熔炉：接什么烧什么（原版熔炼配方表）。有入线吃内部缓存，否则吃定向供料/存储网络。
                int cycles = be.cyclesThisTick(i, miu.def().baseIntervalTicks(), speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = runningCount(st, parallelLv, tier); // m99 并发直接乘台数
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
                        if (!machineFilterAllows(st, id)) continue; // m149 选了烧什么就只烧什么
                        long take = Math.min(be.bufCountFor(i, id), capacity - done);
                        if (take <= 0) continue;
                        be.bufWithdrawFor(i, id, take);
                        long give = take * (int) out[1];
                        if (hasOut[i]) be.distribute(world, i, outT.get(i), (String) out[0], give);
                        else if (depositSm != null) be.depositOrBuffer(depositSm, new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, Integer.MAX_VALUE)));
                        else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, 64L * OUTPUT_SLOTS)));
                        done += take;
                        be.prodTally(give); // m124 补漏：入线喂料路径此前不计产量——用户熔炉狂产14.2M碎片而实测只显农场的1311/分
                    }
                } else {
                    // 万能熔炉必须显式接线（机器入线 或 存储→机器 定向供料线）才取料：
                    // 不做全局网络兜底，防止把玩家存着的原木/圆石/粗矿悄悄全烧了。
                    com.sdzjz.machine.StorageAccess supply = be.supplyFor(world, i);
                    if (supply == null) { be.statR(i, 3, "未接存储/供料线"); continue; }
                    for (var en : new java.util.ArrayList<>(supply.storeView().entrySet())) {
                        if (done >= capacity) break;
                        Object[] out = com.sdzjz.machine.SmeltPlanner.resultOf(world, en.getKey());
                        if (out == null) continue;
                        if (!machineFilterAllows(st, en.getKey())) continue; // m149
                        long take = Math.min(en.getValue(), capacity - done);
                        if (take <= 0) continue;
                        int got = supply.withdraw(en.getKey(), (int) Math.min(take, Integer.MAX_VALUE));
                        if (got <= 0) continue;
                        long give = (long) got * (int) out[1];
                        if (hasOut[i]) be.distribute(world, i, outT.get(i), (String) out[0], give);
                        else if (depositSm != null) be.depositOrBuffer(depositSm, new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, Integer.MAX_VALUE)));
                        else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, 64L * OUTPUT_SLOTS)));
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
                int running = runningCount(st, parallelLv, tier);
                com.sdzjz.machine.StorageAccess accG = be.supplyFor(world, i);
                if (accG == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accG = src;
                }
                if (accG == null) { be.statR(i, 3, "未接存储网络"); continue; } // 无网络=红灯（这台离了仓没意义）
                java.util.List<StorageCoreBlockEntity> banks = new java.util.ArrayList<>();
                if (accG instanceof StorageCoreBlockEntity c1) banks.add(c1);
                else if (accG instanceof DataPanelBlockEntity pn)
                    banks.addAll(StorageCoreBlockEntity.connectedCores(world, pn.getPos()));
                long budget = (long) running * (1 + countLv) * cycles; // 本tick磨几本
                boolean ground = false;
                long groundN = 0;
                for (StorageCoreBlockEntity bank : banks) {
                    if (budget <= 0) break;
                    java.util.List<ItemStack> tpls = bank.exactTemplates();
                    for (int k = tpls.size() - 1; k >= 0 && budget > 0; k--) { // 倒序：取空会删条目
                        ItemStack t = tpls.get(k);
                        if (!t.isOf(net.minecraft.item.Items.ENCHANTED_BOOK)) continue;
                        double per = be.grindValue(t);
                        if (per <= 0) continue; // 纯诅咒/空组件不收
                        ItemStack tpl = t.copyWithCount(1); // withdrawExact 可能移除模板，先复制
                        int take = bank.withdrawExact(tpl, (int) Math.min(budget, bank.exactCount(k)));
                        if (take <= 0) continue;
                        be.xpPool += per * take;
                        int lapPer = be.grindLapis(tpl); // m140 青金石退款：1/级（工厂成本3/级的33%，有损回收无泵）
                        if (lapPer > 0) {
                            ItemStack lap = new ItemStack(net.minecraft.item.Items.LAPIS_LAZULI,
                                    (int) Math.min((long) lapPer * take, Integer.MAX_VALUE));
                            accG.deposit(lap);
                            if (!lap.isEmpty()) be.addOutput(lap);
                        }
                        ItemStack books = new ItemStack(net.minecraft.item.Items.BOOK, take);
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
                String tgtT = craftTarget(st);
                com.sdzjz.machine.VillagerTrades.Trade t = com.sdzjz.machine.TradePlanner.trade(tgtT);
                if (t == null) { be.stat(i, 0); continue; } // 未选交易=待机（徽章"选交易"）
                int cycles = be.cyclesThisTick(i, 40, speedLv, cfg);
                if (cycles <= 0) continue;
                int running = runningCount(st, parallelLv, tier);
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
                    banksT.addAll(StorageCoreBlockEntity.connectedCores(world, pbt.getPos()));
                int discT = 0;
                String profT = com.sdzjz.machine.TradePlanner.prof(tgtT);
                if (!banksT.isEmpty())
                    for (TradeCenterBlockEntity tc : TradeCenterBlockEntity.loadedIn(world)) {
                        ItemStack cc = tc.contractSlot.getStack(0);
                        if (profT.equals(TradeCenterBlockEntity.contractProf(cc)) && tc.sharesNetwork(banksT))
                            discT = Math.max(discT, TradeCenterBlockEntity.contractDiscount(cc));
                    }
                int need1 = com.sdzjz.machine.VillagerTrades.discounted(t.inCount(), discT);
                // 产出口先解析好算封顶（附魔书出线无视=山羊角同规；书 maxCount=1 封顶按格数）
                com.sdzjz.machine.StorageAccess depositT = (t.enchant() != null || !hasOut[i]) ? be.depositFor(world, i) : null;
                boolean cappedT = depositT == null && (t.enchant() != null || !hasOut[i]);
                if (cappedT) attempts = Math.min(attempts, t.enchant() != null ? OUTPUT_SLOTS
                        : Math.max(1, 64L * OUTPUT_SLOTS / Math.max(1, t.outCount())));
                if (hasIn[i]) { // 连线喂料（刷线机直供）
                    attempts = Math.min(attempts, be.bufCountFor(i, t.inItem()) / need1);
                    if (t.in2Item() != null) attempts = Math.min(attempts, be.bufCountFor(i, t.in2Item()) / t.in2Count());
                    if (attempts <= 0) { be.statR(i, 3, "缺料（本周期成本料不足，对照徽章/工具提示）"); continue; }
                    be.bufWithdrawFor(i, t.inItem(), (long) need1 * attempts);
                    if (t.in2Item() != null) be.bufWithdrawFor(i, t.in2Item(), (long) t.in2Count() * attempts);
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
                    ItemStack bookT = new ItemStack(net.minecraft.item.Items.ENCHANTED_BOOK,
                            (int) Math.min(attempts, Integer.MAX_VALUE));
                    var regT = world.getRegistryManager()
                            .getWrapperOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
                    var entryT = regT.getOrThrow(net.minecraft.registry.RegistryKey.of(
                            net.minecraft.registry.RegistryKeys.ENCHANTMENT, Identifier.of(t.enchant())));
                    bookT.addEnchantment(entryT, t.enchantLv());
                    be.prodTally(attempts);
                    if (depositT != null) be.depositOrBuffer(depositT, bookT);
                    else be.addOutput(bookT); // maxCount=1 自动一格一本（山羊角同规）
                } else {
                    long totalT = attempts * t.outCount();
                    be.prodTally(totalT);
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), t.outItem(), totalT);
                    else if (depositT != null) be.depositOrBuffer(depositT, new ItemStack(
                            Registries.ITEM.get(Identifier.of(t.outItem())), (int) Math.min(totalT, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(t.outItem())), (int) totalT));
                }
                be.xpPool += 4.5 * attempts;
                produced = true;
            } else if (st.getItem() instanceof MachineItem vd && "villager_discount_machine".equals(vd.def().id())) {
                // m145 村民打折机（用户拍板：独立画布机自动治愈）：吃网络金苹果给共网交易所里的合同
                // 升折扣。1 苹果=1 级与交易所手动治愈同价（自动化不改经济账）；低折扣合同优先补短板；
                // 预算=台数×(1+数量级)×周期。发现面走 TradeCenterBlockEntity.loadedIn 注册表 +
                // sharesNetwork 共网过滤（connectedCores 坐标交集）。状态灯：无网络/有合同没苹果=红灯，
                // 无可升合同（没交易所或全满级）=待机，升了=绿灯。
                int cycles = be.cyclesThisTick(i, vd.def().baseIntervalTicks(), speedLv, cfg);
                if (cycles <= 0) continue;
                int running = runningCount(st, parallelLv, tier);
                com.sdzjz.machine.StorageAccess accV = be.supplyFor(world, i);
                if (accV == null) {
                    if (!srcResolved) { src = be.resolveInputSource(world, pos); srcResolved = true; }
                    accV = src;
                }
                if (accV == null) { be.statR(i, 3, "未接存储网络（取不到金苹果）"); continue; } // 无网络=红灯（苹果没处取）
                java.util.List<StorageCoreBlockEntity> banksV = new java.util.ArrayList<>();
                if (accV instanceof StorageCoreBlockEntity cv) banksV.add(cv);
                else if (accV instanceof DataPanelBlockEntity pv)
                    banksV.addAll(StorageCoreBlockEntity.connectedCores(world, pv.getPos()));
                java.util.List<TradeCenterBlockEntity> tcs = new java.util.ArrayList<>();
                for (TradeCenterBlockEntity tc : TradeCenterBlockEntity.loadedIn(world))
                    if (tc.canCure() && tc.sharesNetwork(banksV)) tcs.add(tc);
                if (tcs.isEmpty()) { be.stat(i, 0); continue; } // 无可升合同=待机不是故障
                tcs.sort(java.util.Comparator.comparingInt(t ->
                        TradeCenterBlockEntity.contractDiscount(t.contractSlot.getStack(0))));
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
                else be.statR(i, 3, "缺料：附魔金苹果"); // 有合同可升却取不到苹果=缺料红灯
            } else if (st.getItem() instanceof MachineItem sk && sk.def().id().startsWith("sculk_")) {
                // m138 幽匿三机：吃核心经验池产幽匿件（原版幽匿=经验具象化——催化体吸收死亡经验长
                // 蔓延，蔓延概率长出传感器/尖啸体）。经验闸镜像附魔工厂（m132 同池竞争先例）：
                // 池里够几轮跑几轮，池空=缺料红灯（画布经验池数字可见）。产物无组件，出路走通用三条
                //（distribute/精确入库/输出缓存）不特判。经验成本对齐原版蔓延电荷量级：
                // m143 三塔合并为幽匿线一台：单价=催化2+传感9+尖啸9 合计 20/轮（三表齐滚，总账不变）。
                MachineDef def = sk.def();
                int cycles = be.cyclesThisTick(i, def.baseIntervalTicks(), speedLv, cfg);
                if (cycles <= 0) continue;
                int running = runningCount(st, parallelLv, tier);
                double xpPer = 20.0;
                long attempts = (long) running * cycles;
                attempts = Math.min(attempts, (long) (be.xpPool / xpPer)); // 经验闸
                if (attempts <= 0) { be.statR(i, 3, "缺料（本周期成本料不足，对照徽章/工具提示）"); continue; }
                be.xpPool -= xpPer * attempts;
                be.stat(i, 1);
                com.sdzjz.machine.StorageAccess depositSk = hasOut[i] ? null : be.depositFor(world, i);
                boolean cappedSk = !hasOut[i] && depositSk == null;
                for (MachineDef.Drop d : def.outputs()) {
                    if (!machineFilterAllows(st, d.item())) continue; // m149 选了产物就只出选中
                    long sum = be.rollDrops(world.getRandom(), d, (int) Math.min(attempts, Integer.MAX_VALUE), countLv);
                    if (sum <= 0) continue;
                    if (cappedSk) sum = Math.min(sum, 64L * OUTPUT_SLOTS);
                    be.prodTally(sum);
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), sum);
                    else if (depositSk != null) be.depositOrBuffer(depositSk, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) Math.min(sum, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) sum));
                    produced = true;
                }
            } else if (st.getItem() instanceof MachineItem mi) {
                MachineDef def = mi.def();
                int cycles = be.cyclesThisTick(i, def.baseIntervalTicks(), speedLv, cfg); // m99 工作量累积
                if (cycles <= 0) continue;
                int running = runningCount(st, parallelLv, tier); // m99 并发直接乘台数
                int doCycles = cycles;

                if (def.consumesInputs()) {
                    if (hasIn[i]) {
                        // 从内部缓存取料（连线喂料）。m99：料不够整批时按料量折算周期数，能跑几轮跑几轮
                        for (MachineDef.Input in : def.inputs())
                            doCycles = (int) Math.min(doCycles, be.bufCountFor(i, in.item()) / ((long) in.count() * running));
                        if (doCycles <= 0) { be.statR(i, 3, be.whyMissingBufIn(i, def.inputs(), running)); continue; }
                        for (MachineDef.Input in : def.inputs()) be.bufWithdrawFor(i, in.item(), (long) in.count() * running * doCycles);
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
                    if (!machineFilterAllows(st, d.item())) continue; // m149 选了产物就只出选中
                    long sum = be.rollDrops(world.getRandom(), d, doCycles, countLv);
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
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), total);
                    else if (depositMi != null) be.depositOrBuffer(depositMi, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) Math.min(total, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) total));
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
                int running = runningCount(st, parallelLv, tier);    // m99 并发直接乘台数
                be.stat(i, 1);
                com.sdzjz.machine.StorageAccess depositCg = hasOut[i] ? null : be.depositFor(world, i);
                boolean cappedCg = !hasOut[i] && depositCg == null;  // m99 封顶只对"进内部缓存"生效
                for (MachineDef.Drop d : drops) {
                    if (!machineFilterAllows(st, d.item())) continue; // m149
                    long sum = be.rollDrops(world.getRandom(), d, cycles, countLv);
                    if (sum <= 0) continue;
                    long total = (long) running * sum;
                    if (cappedCg) total = Math.min(total, 64L * OUTPUT_SLOTS);
                    be.prodTally(total); // m86 实测产量
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), total);
                    else if (depositCg != null) be.depositOrBuffer(depositCg, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) Math.min(total, Integer.MAX_VALUE)));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), (int) total));
                    produced = true;
                }
                double cxp = MachineXp.mob(mob);
                if (cxp > 0) { be.xpPool += cxp * running * cycles; produced = true; }
            }
        }
        if (produced) {
            be.pushOutput(world, pos);
            be.markDirty();
        }
        if (!be.lagPause && be.ticks % 2 == 0) be.ejectOverflow(world, pos); // m114/m115 断网喷射：2t一组≈10组/秒；过载时停喷
        if (be.statusDirty && be.ticks % 20 == 0) { // 状态灯：有变化才同步，最多 1 次/秒
            be.statusDirty = false;
            be.markDirty();
            be.syncToClient();
        }
    }

    /** 右键把机器/笼子作为一个节点加入画布（无数量上限）。 */
    /** 右键把机器/笼子作为一个节点加入画布（无上限）；首次自动布局位置。 */
    public boolean insertMachine(net.minecraft.entity.player.PlayerEntity byPlayer, ItemStack held) {
        if (held.isEmpty()) return false;
        int capN = SdzjzConfig.get().maxNodesPerCore; // m270 硬上限（审计"无限节点"条）：拓扑重编译/tick遍历/NBT同步成本全随节点数涨
        if (capN > 0 && machineNodes.size() >= capN) {
            capMsg(byPlayer, "画布节点已达上限 " + capN + "（config maxNodesPerCore 可调，0=无限）");
            return false;
        }
        ItemStack node = held.copyWithCount(1); // m78：一次只放 1 台（原来整叠塞进一个节点，"一右键就是一组"）
        NbtCompound n = node.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (!n.contains("nx")) {
            int i = machineNodes.size(), cols = 6;
            n.putInt("nx", 20 + (i % cols) * 112);
            n.putInt("ny", 20 + (i / cols) * 88);
            node.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        }
        machineNodes.add(node);
        bumpTopo(); // m179
        nodeBuf(machineNodes.size() - 1); // 懒补齐：新节点空输入缓存
        held.decrement(1);
        markDirty();
        syncToClient();
        return true;
    }

    /** m306 压测收场专用：清空画布节点**不散落**（BenchRunner 自建自清；与 dropAll 同清单
     *  去掉 ItemScatterer——一键压测 5 万节点若走 dropAll = 物品雨。绝不用于任何玩家路径）。 */
    public void benchClearNodes() {
        machineNodes.clear();
        bumpTopo(); // m179
        nodeStatus.clear();
        nodeReason.clear(); // m178
        markDirty();
    }

    /** 读节点各类升级等级。 */
    public int nodeSpeed(ItemStack s) { return nodeInt(s, "spd"); }
    public int nodeCount(ItemStack s) { return nodeInt(s, "cnt"); }
    public int nodePar(ItemStack s)   { return nodeInt(s, "par"); }

    // ===== m99 升级数学重写：工作量累积模型 =====
    // 旧模型三处死区：①速度线性减周期(base-4×级)→触底1tick后再插全部无效；②并发只抬"同时运行台数上限"
    // →节点里只有1台机器时(m78后的常态)从头到尾没生效过；③数量产出被64×输出格硬顶→堆到顶再插白插。
    // 新模型：速率=(1+gain)^速度级×productionRateMultiplier，每tick累积、攒够基础周期结算1次，
    // 速率溢出折成同tick多周期——永不触底；并发直接乘台数(1台也翻倍)；数量顶只在"产出只能进内部缓存"时保留。
    private final java.util.Map<Integer, Double> workAcc = new java.util.HashMap<>(); // 节点索引→累积工作量(不落盘,重载至多丢半个周期)
    private long recipesThisTick = 0; // m270 全核每tick已结算周期数（tickInner 每tick复位；配合 maxRecipesPerCoreTick 共享预算）

    /** 该节点本tick应结算的生产周期数（0=继续攒）。 */
    private int cyclesThisTick(int nodeIndex, int baseInterval, int speedLv, SdzjzConfig cfg) {
        double gain = Math.max(0.0, cfg.upgradeSpeedGainPerLevel);
        double rate = Math.pow(1.0 + gain, Math.max(0, speedLv)) * Math.max(0.01, cfg.productionRateMultiplier);
        int base = Math.max(1, baseInterval);
        int cap = Math.max(1, cfg.upgradeMaxCyclesPerTick);
        double acc = workAcc.getOrDefault(nodeIndex, 0.0) + rate;
        int cycles = (int) (acc / base);
        if (cycles > cap) cycles = cap;
        if (cfg.maxRecipesPerCoreTick > 0) { // m270 真接线（审计核实三键此前只声明零使用）：全核共享每tick周期预算
            long remain = cfg.maxRecipesPerCoreTick - recipesThisTick;
            if (remain <= 0) cycles = 0;                    // 预算耗尽：本tick不结算，工作量照常累积下tick续（不静默蒸发,m99教训）
            else if (cycles > remain) cycles = (int) remain;
        }
        // m302 全服共享预算真接线（maxRecipesPerNetworkTick）+ 饥饿名单公平层：先核内后全服双层封顶；
        // 耗尽同 m270 口径只欠不丢（工作量累积下tick续），没吃到的核心下tick持保底1周期先食权。
        if (cycles > 0 && cfg.maxRecipesPerNetworkTick > 0
                && this.world instanceof net.minecraft.server.world.ServerWorld sw302) {
            cycles = com.sdzjz.machine.CoreScheduler.request(sw302, this.pos, cycles, cfg.maxRecipesPerNetworkTick);
        }
        acc -= (double) cycles * base;
        if (acc > (double) base * cap) acc = (double) base * cap; // 被cap/预算截断时不无限囤积
        workAcc.put(nodeIndex, acc);
        recipesThisTick += cycles;
        return cycles;
    }

    /** m99 并发升级直接乘台数：运行台数 = 节点内机器数 ×(1+并发级)×核心层级。 */
    private static int runningCount(ItemStack st, int parallelLv, int tier) { return com.sdzjz.node.NodeTags.runningCount(st, parallelLv, tier); }

    /** m99 随机掉落表按周期数结算：每周期独立掷概率/数量，命中加数量升级奖励(+8/级)。 */
    private long rollDrops(net.minecraft.util.math.random.Random rand, MachineDef.Drop d, int cycles, int countLv) {
        long sum = 0;
        for (int c = 0; c < cycles; c++) {
            if (d.chance() < 1f && rand.nextFloat() >= d.chance()) continue;
            int amt = d.min() + (d.max() > d.min() ? rand.nextInt(d.max() - d.min() + 1) : 0);
            if (amt <= 0) continue;
            sum += amt + (long) countLv * 8;
        }
        return sum;
    }

    /** m137 山羊角入库：8 变体 instrument 组件（原版 GOAT_HORNS 标签枚举，模组扩展自动跟随），
     *  total 均摊到各变体（余数随机加成），逐变体建栈挂组件走 精确账本（m130）或 输出缓存（addOutput 保组件、
     *  maxCount=1 自动一格一支）。注册表异常兜底裸角不吞产量。 */
    private void depositGoatHorns(net.minecraft.world.World world, com.sdzjz.machine.StorageAccess deposit, long total) {
        java.util.List<net.minecraft.registry.entry.RegistryEntry<net.minecraft.item.Instrument>> vars = new java.util.ArrayList<>();
        for (var e : net.minecraft.registry.Registries.INSTRUMENT.iterateEntries(
                net.minecraft.registry.tag.InstrumentTags.GOAT_HORNS)) vars.add(e);
        if (vars.isEmpty()) { // 数据包清空标签的兜底：裸角照常入库（普通条目），产量不蒸发
            ItemStack bare = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft:goat_horn")),
                    (int) Math.min(total, Integer.MAX_VALUE));
            if (deposit != null) depositOrBuffer(deposit, bare); else addOutput(bare);
            return;
        }
        long base = total / vars.size(), rem = total % vars.size();
        net.minecraft.util.math.random.Random rand = world.getRandom();
        long[] share = new long[vars.size()];
        for (int k = 0; k < vars.size(); k++) share[k] = base;
        for (long r = 0; r < rem; r++) share[rand.nextInt(vars.size())]++;
        for (int k = 0; k < vars.size(); k++) {
            if (share[k] <= 0) continue;
            ItemStack horn = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft:goat_horn")),
                    (int) Math.min(share[k], Integer.MAX_VALUE));
            horn.set(DataComponentTypes.INSTRUMENT, vars.get(k));
            if (deposit != null) depositOrBuffer(deposit, horn);
            else addOutput(horn);
        }
    }

    /** m139 砂轮回收值：Σ各附魔 getMinPower(等级)（原版砂轮经验同源公式），诅咒跳过；
     *  逐附魔封顶 0.8×工厂成本（B×级×25，B=max(1,anvilCost/2)）——第三方附魔 minPower 再高
     *  也造不成「附魔工厂→砂轮」经验永动泵。空组件/纯诅咒返 0（调用方不收）。 */
    private double grindValue(ItemStack book) {
        net.minecraft.component.type.ItemEnchantmentsComponent comp =
                book.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (comp == null || comp.isEmpty()) return 0;
        double v = 0;
        for (var en : comp.getEnchantmentEntries()) {
            var entry = en.getKey();
            int lvl = en.getIntValue();
            if (entry.isIn(net.minecraft.registry.tag.EnchantmentTags.CURSE)) continue;
            net.minecraft.enchantment.Enchantment e = entry.value();
            double cap = 0.8 * Math.max(1, e.getAnvilCost() / 2) * lvl * 25;
            v += Math.min(e.getMinPower(lvl), cap);
        }
        return v;
    }

    /** m140 砂轮青金石退款：Σ非诅咒附魔等级 × 1（附魔工厂成本 3/级 的 33%——有损回收，
     *  「工厂造书→砂轮回收」每圈净亏 2青金石/级+50%以上经验，无泵）。 */
    private int grindLapis(ItemStack book) {
        net.minecraft.component.type.ItemEnchantmentsComponent comp =
                book.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (comp == null || comp.isEmpty()) return 0;
        int lv = 0;
        for (var en : comp.getEnchantmentEntries()) {
            if (en.getKey().isIn(net.minecraft.registry.tag.EnchantmentTags.CURSE)) continue;
            lv += en.getIntValue();
        }
        return lv;
    }

    private int nodeInt(ItemStack s, String key) {
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt(key);
    }

    private int totalNodeUpgrade(String key) {
        int n = 0;
        for (ItemStack s : machineNodes) n += nodeInt(s, key);
        return n;
    }

    /** 从玩家背包扣一个对应升级，加到该节点。 type 0=加速 1=数量 2=并列 */
    /** 领取经验池：直接给玩家经验（画布「领取经验」按钮）。 */
    public void collectXp(PlayerEntity player) {
        int give = (int) Math.min(xpPool, Integer.MAX_VALUE);
        if (give <= 0) return;
        player.addExperience(give);
        xpPool -= give;
        if (world != null) world.playSound(null, pos,
                net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                net.minecraft.sound.SoundCategory.BLOCKS, 0.6f, 1.0f);
        markDirty();
    }

    /** 读取自动合成机节点的目标产物 id（无则空串）。 */
    /** m93：全自动农场多选作物（最多8种）。无 crops 列表时回退旧单选 ct（自动迁移）。 */
    public static java.util.List<String> cropList(ItemStack s) { return com.sdzjz.node.NodeTags.cropList(s); }

    public static String craftTarget(ItemStack s) { return com.sdzjz.node.NodeTags.craftTarget(s); }
    public static String craftRecipe(ItemStack s) { return com.sdzjz.node.NodeTags.craftRecipe(s); } // m235

    // ===== 画布逻辑节点：过滤器 / 数量传感器 =====
    static NbtCompound nbtOf(ItemStack s) { return com.sdzjz.node.NodeTags.nbtOf(s); }

    public static boolean isFilter(ItemStack s) { return com.sdzjz.node.NodeTags.isFilter(s); }

    public static boolean isTrash(ItemStack s) { return com.sdzjz.node.NodeTags.isTrash(s); } // m150

    public static boolean isExtractor(ItemStack s) { return com.sdzjz.node.NodeTags.isExtractor(s); } // m154

    /** m154 抽取节点开合（默认关——用户点名"点击抽取才开始"）。 */
    public static boolean extractorOn(ItemStack s) { return com.sdzjz.node.NodeTags.extractorOn(s); }

    /** m160 抽取节点有效运转态：手动开 且（无感应规则 或 感应放行）。感应键位与传感器
     *  同套（si/sv/sl，sensorOpen 通用判定）——配了监测物品的抽取节点=自带阈值自动启停。 */
    boolean extractorLive(World world, int i, ItemStack st) {
        return extractorOn(st) && (sensorItem(st).isEmpty() || sensorOpen(world, i));
    }

    /** m159 抽取累计读数（卡面用）。 */
    public static long extractorCount(ItemStack s) { return com.sdzjz.node.NodeTags.extractorCount(s); }

    /** m159 抽取量/轮（每 5t 每种上限，未设=512）；m163a 五挡循环 64→512→4096→32768→262144。实际抽量再乘 (1+数量升级)。 */
    public static long extractorRate(ItemStack s) { return com.sdzjz.node.NodeTags.extractorRate(s); }

    /** m150 垃圾桶累计吞噬量（卡面读数）。 */
    public static long trashCount(ItemStack s) { return com.sdzjz.node.NodeTags.trashCount(s); }
    public static boolean isSensor(ItemStack s) { return com.sdzjz.node.NodeTags.isSensor(s); }
    public static boolean isSwitch(ItemStack s) { return com.sdzjz.node.NodeTags.isSwitch(s); }
    public static boolean isDistributor(ItemStack s) { return com.sdzjz.node.NodeTags.isDistributor(s); }

    /** 开关节点状态：默认=开；NBT "so"=false 时为关。 */
    public static boolean switchOn(ItemStack s) { return com.sdzjz.node.NodeTags.switchOn(s); }

    /** m123 机器阶位：0普通 1超级 2神级 3GM。每阶=4台合1、战力8×/阶（4台份×2速）。 */
    public static int machineTier(ItemStack s) { return com.sdzjz.node.NodeTags.machineTier(s); }

    /** m123 融合升阶（up=true：4台同阶→1台高阶，余数还玩家）/ 拆解降阶（1台→4台低阶，超堆叠上限的还玩家）。 */
    public void fuseNode(PlayerEntity player, int index, boolean up) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if (s.isEmpty() || !(s.getItem() instanceof MachineItem)) return;
        int mt = machineTier(s);
        if (up) {
            if (mt >= 3) return;
            if (s.getCount() < 4) { // m128(F1)：m78 后 insertMachine 恒为 ×1 节点——不聚敛则单节点永远
                // 凑不满 4，融合出厂即死（m125 审计实锤，本条为丢失代码重建）。跨节点抽调同物品同阶机器。
                index = gatherSame(player, index, 4);
                s = machineNodes.get(index);
            }
            if (s.getCount() < 4) { // 全画布凑不齐：聚敛可能已部分发生（同类并栈），照样落盘同步
                player.sendMessage(net.minecraft.text.Text.literal(
                        "画布上同类同阶机器不足 4 台，无法融合"), true);
                markDirty();
                syncToClient();
                return;
            }
            int rem = s.getCount() % 4, keep = s.getCount() / 4;
            if (rem > 0) { // 余数保持原阶还给玩家（copy 带原 NBT），绝不凭空消失
                ItemStack back = s.copyWithCount(rem);
                if (!player.getInventory().insertStack(back)) player.dropItem(back, false);
            }
            NbtCompound n = nbtOf(s);
            n.putInt("mt", mt + 1);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
            s.setCount(keep);
        } else {
            if (mt <= 0) return;
            NbtCompound n = nbtOf(s);
            n.putInt("mt", mt - 1);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
            long c = (long) s.getCount() * 4;
            int nc = (int) Math.min(c, s.getMaxCount());
            s.setCount(nc);
            long over = c - nc;
            if (over > 0) { // 超堆叠上限的低阶机还给玩家（copy 已带新阶 NBT）
                ItemStack back = s.copyWithCount((int) over);
                if (!player.getInventory().insertStack(back)) player.dropItem(back, false);
            }
        }
        markDirty();
        syncToClient();
    }

    /** m128(F1)：跨节点聚敛——从画布其它「同物品同阶」节点抽调机器进 index 节点，凑 need 台。
     *  被抽空的节点：**先读后抽**（栈随 detach 清空后 NBT 即失）——先退还其内嵌升级，再走 detachNode
     *  全套簿记；聚敛无视暂停/升级差异（机器可互换，升级留原节点或退还，m125 设计留痕）。
     *  返回聚敛后 index 的新下标（摘除低位节点会使下标前移）。倒序遍历：detach 只影响更高下标，安全。 */
    private int gatherSame(PlayerEntity player, int index, int need) {
        ItemStack target = machineNodes.get(index);
        int mt = machineTier(target);
        int idx = index;
        for (int j = machineNodes.size() - 1; j >= 0 && target.getCount() < need; j--) {
            if (j == idx) continue;
            ItemStack o = machineNodes.get(j);
            if (o.isEmpty() || o.getItem() != target.getItem() || machineTier(o) != mt) continue;
            int take = Math.min(need - target.getCount(), o.getCount());
            if (take >= o.getCount()) { // 将被抽空：先读后抽——升级退还，再摘节点
                refundUpgrades(player, nbtOf(o));
                target.increment(take);
                detachNode(j);
                if (j < idx) idx--; // 摘除低位节点，目标下标前移
            } else {
                o.decrement(take);
                target.increment(take);
            }
        }
        return idx;
    }

    /** m110b 单节点启停：默认=运行；NBT "np"=true 为暂停（任意节点类型通用）。 */
    public static boolean nodePaused(ItemStack s) { return com.sdzjz.node.NodeTags.nodePaused(s); }

    /** 切换节点 暂停/运行（m110b）。 */
    public void togglePause(int index) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if (s.isEmpty()) return;
        NbtCompound n = nbtOf(s);
        n.putBoolean("np", !nodePaused(s));
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    /** 切换开关节点 开/关。 */
    public void toggleSwitch(int index) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        NbtCompound n = nbtOf(s);
        if (isSwitch(s)) n.putBoolean("so", !switchOn(s));
        else if (isExtractor(s)) n.putBoolean("xo", !extractorOn(s)); // m154 抽取启停走同一收包口
        else return;
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    /** 过滤模式：false=白名单（默认，只放行名单内），true=黑名单（拦下名单内）。 */
    public static boolean filterBlacklist(ItemStack s) { return com.sdzjz.node.NodeTags.filterBlacklist(s); }

    public static java.util.List<String> filterList(ItemStack s) { return com.sdzjz.node.NodeTags.filterList(s); }

    public static boolean filterPasses(ItemStack s, String id) { return com.sdzjz.node.NodeTags.filterPasses(s, id); }

    /** m149 机器加工二级界面：哪些机器有"选加工范围"资格——万能熔炉(选烧什么)或多产物机(选出什么)。 */
    public static boolean machineFilterable(ItemStack s) { return com.sdzjz.node.NodeTags.machineFilterable(s); }

    /** m149 机器加工过滤（复用 fl 名单，白名单语义）：空=全放行；非空=只加工选中项。
     *  与过滤节点的 fl+fb 双语义区分：机器侧永远白名单、不碰 fb。 */
    public static boolean machineFilterAllows(ItemStack s, String id) { return com.sdzjz.node.NodeTags.machineFilterAllows(s, id); }

    public static String sensorItem(ItemStack s) { return com.sdzjz.node.NodeTags.sensorItem(s); }

    public static long sensorThreshold(ItemStack s) { return com.sdzjz.node.NodeTags.sensorThreshold(s); }

    /** 传感器方向：true=「低于阈值放行」(默认，补货型)；false=「高于阈值放行」(溢出型)。 */
    public static boolean sensorLess(ItemStack s) { return com.sdzjz.node.NodeTags.sensorLess(s); }

    /** 加/移一条过滤名单项（已在名单=移除）；id 为空串=切换 白名单↔黑名单。 */
    public void toggleFilterEntry(int index, String id) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if ("#cr".equals(id) && s.getItem() instanceof AutoCrafterItem) { // m235 配方换挡复用此收包口（#xr 同款哨兵工艺）：
            // 自动(-1)→候选0→候选1→…→回自动；候选序=CraftPlanner.plans 原版排前+id字典序，双端同源循环稳定
            String tgt = craftTarget(s);
            java.util.List<CraftPlanner.Plan> ps = tgt.isEmpty() ? java.util.List.of() : CraftPlanner.plans(world, tgt);
            NbtCompound nc = nbtOf(s);
            String cur = nc.contains("cr") ? nc.getString("cr") : "";
            int at = -1;
            for (int k = 0; k < ps.size(); k++) if (ps.get(k).recipeId().equals(cur)) { at = k; break; }
            int nxt = at + 1;
            if (nxt >= ps.size()) nc.remove("cr"); else nc.putString("cr", ps.get(nxt).recipeId());
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nc));
            markDirty();
            syncToClient();
            return;
        }
        if ("#xr".equals(id) && isExtractor(s)) { // m159 抽取量换挡复用此收包口；m163a 扩至五挡（用户点名"还是太少"）
            NbtCompound nx = nbtOf(s);
            long cur = extractorRate(s);
            nx.putLong("xr", cur == 64 ? 512 : cur == 512 ? 4096 : cur == 4096 ? 32768 : cur == 32768 ? 262144 : 64);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nx));
            markDirty();
            syncToClient();
            return;
        }
        if (!isFilter(s) && !machineFilterable(s) && !isExtractor(s) && !isTrash(s)) return;
        // m149 机器加工过滤 / m160 抽取白名单+垃圾桶白名单（安全桶）同走此口
        NbtCompound n = nbtOf(s);
        if (id == null || id.isEmpty()) {
            if (!isFilter(s)) return; // 机器侧永远白名单，无黑白切换
            n.putBoolean("fb", !n.getBoolean("fb"));
        } else {
            NbtList l = n.getList("fl", NbtElement.STRING_TYPE);
            boolean removed = false;
            for (int k = 0; k < l.size(); k++)
                if (l.getString(k).equals(id)) { l.remove(k); removed = true; break; }
            if (!removed) {
                if (l.size() >= 64) return; // 名单封顶，防 NBT 膨胀
                l.add(net.minecraft.nbt.NbtString.of(id));
            }
            n.put("fl", l);
        }
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    /** 设置传感器：监测物品 + 阈值 + 方向（低于/高于放行）。 */
    public void setSensorConfig(int index, String id, long threshold, boolean less) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if (!isSensor(s) && !isExtractor(s)) return; // m160 抽取节点内置自动启停同走此口
        NbtCompound n = nbtOf(s);
        if ("§clear".equals(id)) n.remove("si"); // m160 清除感应（传感/抽取通用）
        else if (id != null && !id.isEmpty()) n.putString("si", id);
        n.putLong("sv", Math.max(0, Math.min(1_000_000_000_000L, threshold)));
        n.putBoolean("sl", less);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    /** 传感器闸门是否放行：未配置=直通；否则按监测库存量与阈值比较。
     *  监测目标：连了 存储→传感器 供料线=监测那个库；否则=默认主存储（绑定>有线>无线>卫星）。 */
    boolean sensorOpen(World world, int i) {
        ItemStack st = machineNodes.get(i);
        String id = sensorItem(st);
        if (id.isEmpty()) return true;
        com.sdzjz.machine.StorageAccess sc = supplyFor(world, i);
        if (sc == null) sc = resolveInputSource(world, pos);
        long have = sc == null ? 0 : sc.count(id);
        long th = sensorThreshold(st);
        return sensorLess(st) ? have < th : have > th;
    }

    /** 该节点的全部出线目标是否都是「关闸的传感器」——是则上游整台暂停（不白产不塞存储）。 */
    private boolean allGatesClosed(World world, java.util.List<Integer> targets) {
        if (targets == null || targets.isEmpty()) return false;
        for (int t : targets) {
            if (t < 0 || t >= machineNodes.size()) return false;
            ItemStack ts = machineNodes.get(t);
            boolean closedGate = (isSensor(ts) && !sensorOpen(world, t)) || (isSwitch(ts) && !switchOn(ts))
                    || (isExtractor(ts) && !extractorLive(world, t, ts)) // m154+m160 感应暂停同视关闸
                    || nodePaused(ts); // m110b 暂停视同关闸——下游全暂停时上游整台停，不白产
            if (!closedGate) return false;
        }
        return true;
    }

    // ===== 节点状态灯：0=待机 1=正常(绿) 2=阻塞/关闸(黄) 3=缺料(红)。每 20t 有变化才同步 =====
    private final java.util.List<Integer> nodeStatus = new java.util.ArrayList<>();
    private boolean statusDirty = false;

    void stat(int i, int v) {
        while (nodeStatus.size() < machineNodes.size()) nodeStatus.add(0);
        while (nodeReason.size() < machineNodes.size()) nodeReason.add(""); // m178
        if (v == 1 && i >= 0 && i < nodeReason.size() && !nodeReason.get(i).isEmpty()) { nodeReason.set(i, ""); statusDirty = true; } // m178 转绿清因
        if (i < 0 || i >= nodeStatus.size() || nodeStatus.get(i) == v) return;
        nodeStatus.set(i, v);
        statusDirty = true;
    }

    // ===== m178 阻塞原因（错误解释）：与 nodeStatus 平行同步，卡面黄/红灯常显人话原因 =====
    private final java.util.List<String> nodeReason = new java.util.ArrayList<>();
    /** 设状态并附原因（原因变化也触发同步）。 */
    void statR(int i, int v, String why) {
        while (nodeReason.size() < machineNodes.size()) nodeReason.add("");
        if (i >= 0 && i < nodeReason.size() && !nodeReason.get(i).equals(why)) { nodeReason.set(i, why); statusDirty = true; }
        stat(i, v);
    }
    /** 画布读取阻塞原因（客户端）。 */
    public String nodeReason(int i) { return i >= 0 && i < nodeReason.size() ? nodeReason.get(i) : ""; }
    private static String itemName(String id) {
        try { return new ItemStack(Registries.ITEM.get(Identifier.of(id))).getName().getString(); } catch (Exception e) { return id; }
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
        return i >= 0 && i < nodeStatus.size() ? nodeStatus.get(i) : 0;
    }

    /** 设置自动合成机节点的目标产物（画布徽章点选，走 NodeTargetPayload）。 */
    public void setNodeTarget(int index, String id) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        boolean cropOk = s.getItem() instanceof com.sdzjz.item.CropFarmItem && com.sdzjz.machine.CropFarms.has(id);
        boolean brewOk = s.getItem() instanceof com.sdzjz.item.BrewingTowerItem
                && com.sdzjz.machine.BrewPlanner.targetStack(id) != null; // m131b 目标串服务端校验
        boolean enchOk = s.getItem() instanceof com.sdzjz.item.EnchantFactoryItem
                && com.sdzjz.machine.EnchantPlanner.targetStack(this.world, id) != null; // m132 目标串服务端校验
        boolean tradeOk = s.getItem() instanceof com.sdzjz.item.VillagerTraderItem
                && com.sdzjz.machine.TradePlanner.valid(id); // m146 目标串服务端校验
        if (!(s.getItem() instanceof AutoCrafterItem) && !cropOk && !brewOk && !enchOk && !tradeOk) return;
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (cropOk) { // m93 多选 toggle：在列表则移除，否则加入（≤8）；旧单选 ct 自动并入
            java.util.List<String> cur = cropList(s);
            if (cur.contains(id)) cur.remove(id);
            else if (cur.size() < 8) cur.add(id);
            net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
            for (String c : cur) l.add(net.minecraft.nbt.NbtString.of(c));
            n.put("crops", l);
            n.remove("ct");
        } else {
            n.putString("ct", id);
            n.remove("cr"); // m235 换目标即回"自动"（旧手选配方不属于新目标）
        }
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    public boolean addNodeUpgrade(PlayerEntity player, int index, int type) {
        if (!addNodeUpgradeRaw(player, index, type)) return false;
        syncNow();
        return true;
    }

    /** m128(F3)：无同步内核——批量接收器循环用（此前 Shift 批量 64 连发=一 tick 64 次全量 BE 同步瞬卡），
     *  循环结束由调用方 syncNow() 一次。 */
    public boolean addNodeUpgradeRaw(PlayerEntity player, int index, int type) {
        if (index < 0 || index >= machineNodes.size()) return false;
        Item item = upgradeItem(type);
        String key = upgradeKey(type);
        if (item == null || !consumeFromInv(player, item)) return false;
        ItemStack s = machineNodes.get(index);
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putInt(key, n.getInt(key) + 1);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        return true;
    }

    /** 从该节点取回一个升级还给玩家。 */
    public boolean removeNodeUpgrade(PlayerEntity player, int index, int type) {
        if (!removeNodeUpgradeRaw(player, index, type)) return false;
        syncNow();
        return true;
    }

    /** m128(F3)：无同步内核（同 addNodeUpgradeRaw）。 */
    public boolean removeNodeUpgradeRaw(PlayerEntity player, int index, int type) {
        if (index < 0 || index >= machineNodes.size()) return false;
        Item item = upgradeItem(type);
        String key = upgradeKey(type);
        if (item == null) return false;
        ItemStack s = machineNodes.get(index);
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        int lv = n.getInt(key);
        if (lv <= 0) return false;
        n.putInt(key, lv - 1);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        if (!player.getInventory().insertStack(new ItemStack(item))) player.dropItem(new ItemStack(item), false);
        return true;
    }

    /** m128(F3)：落盘+全量同步一次（批量收包器循环后调用；单发包装方法内部也走它）。 */
    public void syncNow() {
        markDirty();
        syncToClient();
    }

    private static Item upgradeItem(int type) {
        return switch (type) {
            case 0 -> ModItems.SPEED_UPGRADE;
            case 1 -> ModItems.COUNT_UPGRADE;
            case 2 -> ModItems.PARALLEL_UPGRADE;
            default -> null;
        };
    }

    private static String upgradeKey(int type) {
        return switch (type) {
            case 0 -> "spd";
            case 1 -> "cnt";
            case 2 -> "par";
            default -> "";
        };
    }

    private boolean consumeFromInv(PlayerEntity player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) { s.decrement(1); return true; }
        }
        return false;
    }

    /** 读节点画布坐标（无则给默认）。 */
    public int nodeX(ItemStack s, int def) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return n.contains("nx") ? n.getInt("nx") : def;
    }

    public int nodeY(ItemStack s, int def) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return n.contains("ny") ? n.getInt("ny") : def;
    }

    /** 设置某节点画布坐标（服务端会同步客户端；客户端调用仅本地视觉）。 */
    public void setNodePos(int index, int nx, int ny) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putInt("nx", clampCanvas(nx)); // m269 与存储节点(m265)同幅钳制——审计点名"单节点移动直接接受任意32位整数写入NBT"
        n.putInt("ny", clampCanvas(ny));
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    /** 右键把升级放入升级槽。 */
    public boolean insertUpgrade(ItemStack held) { return insertInto(held, UPGRADE_START, UPGRADE_START + UPGRADE_SLOTS); }

    private boolean insertInto(ItemStack held, int start, int end) {
        boolean changed = false;
        for (int i = start; i < end && !held.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, held)) {
                int move = Math.min(s.getMaxCount() - s.getCount(), held.getCount());
                if (move > 0) { s.increment(move); held.decrement(move); changed = true; }
            }
        }
        for (int i = start; i < end && !held.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                int move = Math.min(held.getMaxCount(), held.getCount());
                items.set(i, held.copyWithCount(move)); held.decrement(move); changed = true;
            }
        }
        if (changed) markDirty();
        return changed;
    }

    /** 右键取出指定节点：内嵌升级折成物品归还，机器本体清掉画布 NBT（可正常堆叠）。 */
    public void removeNodeAt(PlayerEntity player, int index) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = detachNode(index);
        returnNodeClean(player, s);
        markDirty();
        syncToClient();
    }

    /** m128(F1)：摘除节点的全套簿记（谨慎重构：逐字搬运自 removeNodeAt 原逻辑）——机器线重映射+
     *  存储线剪/移位+在途缓存并遗留池+状态位；返回被摘的节点栈。不含归还与同步，调用方自理。
     *  removeNodeAt / ejectOne / 融合聚敛三处共用，双写漂移归零。 */
    private ItemStack detachNode(int index) {
        nodeBuf(machineNodes.size() - 1); // 先补齐对齐
        ItemStack s = machineNodes.remove(index);
        bumpTopo(); // m179（本方法随后还重写 connections，一次 bump 覆盖）
        if (index < nodeBufs.size()) mergeLegacy(nodeBufs.remove(index)); // 在途物品回遗留池，不丢
        if (index < nodeStatus.size()) nodeStatus.remove(index);
        if (index < nodeReason.size()) nodeReason.remove(index); // m178
        java.util.List<int[]> kept = new java.util.ArrayList<>();
        for (int[] c : connections) {
            if (c[0] == index || c[1] == index) continue; // 触及被删节点→断
            int a = c[0] > index ? c[0] - 1 : c[0];
            int b = c[1] > index ? c[1] - 1 : c[1];
            kept.add(new int[]{a, b});
        }
        connections.clear();
        connections.addAll(kept);
        for (int i = storageEdges.size() - 1; i >= 0; i--) { // 存储连线同样剪/移位
            long[] e = storageEdges.get(i);
            if (e[0] == index) { storageEdges.remove(i); storageEdgeDims.remove(i); }
            else if (e[0] > index) e[0]--;
        }
        sweepGroups(); // m191 组标记随被摘的栈自然离场（returnNodeClean 会剥画布 NBT）；这里只清剩<2台的组
        return s;
    }

    /** 归还节点：先把嵌在 NBT 里的升级折成升级物品还给玩家，再清掉画布数据返还机器本体。 */
    private void returnNodeClean(PlayerEntity player, ItemStack s) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        int mt = n.getInt("mt"); // m128(F2)：先读后抹——阶位是机器本体属性不是画布数据
        refundUpgrades(player, n);
        s.remove(DataComponentTypes.CUSTOM_DATA);
        if (mt > 0) { // m128(F2)：重挂纯 {"mt"}——取出 GM 仍是 GM，再放回经 insertMachine copy 自然携带；
            // 同阶同物品可堆叠、异阶 CUSTOM_DATA 不同天然不混栈（原版机制白拿）。此前一刀抹全=511 台凭空蒸发。
            NbtCompound keep = new NbtCompound();
            keep.putInt("mt", mt);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(keep));
        }
        if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
    }

    /** m128：把节点 NBT 里的内嵌升级折成物品退还玩家（returnNodeClean 与融合聚敛共用，双写归一）。 */
    private void refundUpgrades(PlayerEntity player, NbtCompound n) {
        for (int type = 0; type < 3; type++) {
            int lv = n.getInt(upgradeKey(type));
            Item item = upgradeItem(type);
            while (lv-- > 0 && item != null) {
                ItemStack up = new ItemStack(item);
                if (!player.getInventory().insertStack(up)) player.dropItem(up, false);
            }
        }
    }

    /** 潜行空手右键：先弹出最后一个机器节点，其次弹升级。 */
    public void ejectOne(PlayerEntity player) {
        if (!machineNodes.isEmpty()) {
            // m128：改共用 detachNode（末位节点无更高下标需要移位，与原 removeIf 写法等价，三写归一）
            ItemStack s = detachNode(machineNodes.size() - 1);
            returnNodeClean(player, s);
            markDirty();
            syncToClient();
            return;
        }
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) if (pop(player, i)) return;
    }

    /** 画布连线读取（客户端渲染）。 */
    public java.util.List<int[]> connections() { return connections; }

    // ===== m191 画布分组：成员归属在节点栈 NBT "gp"（随栈走，detachNode 的下标移位天然无关），
    // 这里只管 id→名元数据 + 组操作；配置 canvasGroupsEnabled 总开关在接收器侧把门。 =====
    /** 分组元数据读取（客户端渲染组框标题用）。 */
    public java.util.Map<Integer, String> groupsView() { return groupNames; }

    /** 建组：≥2 个合法下标才成组；成员先脱旧组再入新组（一台机器只能在一个组）。name 空=自动"组N"。 */
    public void createGroup(java.util.List<Integer> members, String name) {
        java.util.LinkedHashSet<Integer> ms = new java.util.LinkedHashSet<>();
        for (int i : members) if (i >= 0 && i < machineNodes.size()) ms.add(i);
        if (ms.size() < 2) return;
        int gid = 1;
        for (int k : groupNames.keySet()) gid = Math.max(gid, k + 1);
        String nm = name == null ? "" : name.trim();
        if (nm.length() > 24) nm = nm.substring(0, 24);
        groupNames.put(gid, nm.isEmpty() ? "组" + gid : nm);
        for (int i : ms) setNodeGroupTag(machineNodes.get(i), gid);
        sweepGroups(); // 成员被挖走的旧组可能只剩0/1台，顺手清
        markDirty();
        syncToClient();
    }

    /** 解散组：成员脱组标记 + 元数据删除。机器/连线原样不动（分组纯视觉，不碰拓扑）。 */
    public void dissolveGroup(int gid) {
        if (groupNames.remove(gid) == null) return;
        for (ItemStack s : machineNodes) if (com.sdzjz.node.NodeTags.nodeGroup(s) == gid) setNodeGroupTag(s, -1);
        markDirty();
        syncToClient();
    }

    /** 重命名组（长度钳 24，空名不接受）。 */
    public void renameGroup(int gid, String name) {
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty() || !groupNames.containsKey(gid)) return;
        if (nm.length() > 24) nm = nm.substring(0, 24);
        groupNames.put(gid, nm);
        markDirty();
        syncToClient();
    }

    /** 组整体位移：全成员坐标加同一增量，改完只同步一次（防 m128F3 式 N 连发全量同步）。 */
    public void moveGroup(int gid, int dx, int dy) {
        if (!groupNames.containsKey(gid)) return;
        dx = Math.max(-100000, Math.min(100000, dx)); // 防伪造包把整组甩进天文坐标
        dy = Math.max(-100000, Math.min(100000, dy));
        boolean any = false;
        for (ItemStack s : machineNodes) {
            if (com.sdzjz.node.NodeTags.nodeGroup(s) != gid) continue;
            NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            // m269 long 加法+终值钳幅：单次 dx 虽已钳 ±1e5，但反复发包每次+1e5 累加 int 会溢出（审计点名）
            n.putInt("nx", clampCanvas((n.contains("nx") ? (long) n.getInt("nx") : 0L) + dx));
            n.putInt("ny", clampCanvas((n.contains("ny") ? (long) n.getInt("ny") : 0L) + dy));
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
            any = true;
        }
        if (!any) return;
        markDirty();
        syncToClient();
    }

    /** 写/清节点栈上的组标记（gid<0=清除）。 */
    private void setNodeGroupTag(ItemStack s, int gid) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (gid < 0) n.remove("gp"); else n.putInt("gp", gid);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
    }

    /** 组一致性清扫：成员<2 的组解散（1 台不成组）+ 无元数据的孤儿 gp 标记剥除。detachNode 与组操作后调用。 */
    private void sweepGroups() {
        java.util.HashMap<Integer, Integer> cnt = new java.util.HashMap<>();
        for (ItemStack s : machineNodes) {
            int g = com.sdzjz.node.NodeTags.nodeGroup(s);
            if (g >= 0) cnt.merge(g, 1, Integer::sum);
        }
        groupNames.keySet().removeIf(g -> cnt.getOrDefault(g, 0) < 2);
        for (ItemStack s : machineNodes) {
            int g = com.sdzjz.node.NodeTags.nodeGroup(s);
            if (g >= 0 && !groupNames.containsKey(g)) setNodeGroupTag(s, -1);
        }
    }

    // ===== 存储/终端接口节点：扫描 + 定向连线 =====
    /** 扫描本核心可达的存储核心/数据终端端点（绑定>有线>无线>卫星，封顶8个），变化才同步。 */
    private void scanStorageEndpoints(World world, BlockPos corePos) {
        java.util.LinkedHashMap<Long, long[]> found = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<Long, String> dims = new java.util.LinkedHashMap<>();
        String selfDim = world.getRegistryKey().getValue().toString();
        found.put(OUTPUT_IFACE, new long[]{OUTPUT_IFACE, 6}); // 常驻输出接口，永不被封顶挤掉
        dims.put(OUTPUT_IFACE, selfDim);
        // 绑定目标（优先级最高，可跨维度）
        StorageCoreBlockEntity bound = boundPanel(world, corePos);
        if (bound != null && bound.getWorld() != null) {
            long pl = bound.getPos().asLong();
            found.put(pl, new long[]{pl, 0});
            dims.put(pl, bound.getWorld().getRegistryKey().getValue().toString());
        }
        // 有线：BFS 收集全部存储核心 + 数据终端
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos); seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.offset(d);
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
            if (world instanceof net.minecraft.server.world.ServerWorld sw) {
                for (var key : java.util.List.copyOf(StorageCoreBlockEntity.dimensionsWithCores())) {
                    if (found.size() >= ENDPOINT_CAP || key.equals(world.getRegistryKey())) continue;
                    net.minecraft.server.world.ServerWorld ow = sw.getServer().getWorld(key);
                    if (ow == null) continue;
                    for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(ow))) {
                        if (found.size() >= ENDPOINT_CAP) break;
                        if (StorageCoreBlockEntity.loadedCoreAt(ow, p) == null) continue;
                        long pl = p.asLong();
                        found.putIfAbsent(pl, new long[]{pl, 3});
                        dims.putIfAbsent(pl, key.getValue().toString());
                    }
                }
            }
        }
        // 被连线引用但没扫到的端点：显示为离线（不丢用户的接线）；本维度已加载但方块没了→剪掉连线
        java.util.Iterator<long[]> it = storageEdges.iterator();
        java.util.Iterator<String> itd = storageEdgeDims.iterator();
        boolean edgesPruned = false;
        while (it.hasNext()) {
            long[] e = it.next();
            String edim = itd.next();
            if (found.containsKey(e[1])) continue;
            if (edim.equals(selfDim)) {
                BlockPos ep = BlockPos.fromLong(e[1]);
                if (world.getChunkManager().isChunkLoaded(ep.getX() >> 4, ep.getZ() >> 4)
                        && !(world.getBlockEntity(ep) instanceof StorageCoreBlockEntity)) {
                    it.remove(); itd.remove(); edgesPruned = true; // 方块确实没了
                    continue;
                }
            }
            found.putIfAbsent(e[1], new long[]{e[1], 4}); // 离线占位
            dims.putIfAbsent(e[1], edim);
        }
        // 变化才写回+同步（省网络）
        boolean changed = edgesPruned || found.size() != storageEndpoints.size();
        if (!changed) {
            for (int i = 0; i < storageEndpoints.size(); i++) {
                long[] old = storageEndpoints.get(i);
                long[] neu = found.get(old[0]);
                if (neu == null || neu[1] != old[1]) { changed = true; break; }
            }
        }
        if (changed) {
            storageEndpoints.clear();
            storageEndpointDims.clear();
            java.util.List<long[]> ordered = new java.util.ArrayList<>(found.values());
            // m80：分组排序（输出接口→存储核心→数据面板），客户端按组编号"存储1/2…、数据面板1/2…"
            ordered.sort(java.util.Comparator.comparingInt(v -> v[1] == 6 ? 0 : v[1] == 5 ? 2 : 1));
            for (long[] v : ordered) {
                storageEndpoints.add(v);
                storageEndpointDims.add(dims.get(v[0]));
            }
            storageNodePos.keySet().retainAll(found.keySet()); // 修剪已消失端点的画布坐标
        }
        // ===== m85：总线库存聚合（只数存储核心；面板聚合的是同一批核心，数它会重复计）=====
        java.util.LinkedHashMap<String, Long> agg = new java.util.LinkedHashMap<>();
        for (long[] v : found.values()) {
            if (v[1] == 4 || v[1] == 5 || v[1] == 6) continue;
            World tw = resolveDimWorld(world, dims.get(v[0]));
            if (tw == null) continue;
            BlockPos bp = BlockPos.fromLong(v[0]);
            if (!tw.getChunkManager().isChunkLoaded(bp.getX() >> 4, bp.getZ() >> 4)) continue;
            if (tw.getBlockEntity(bp) instanceof StorageCoreBlockEntity sc) {
                for (var en : sc.storeView().entrySet()) agg.merge(en.getKey(), en.getValue(), StorageCoreBlockEntity::satAdd); // m273
                // m163b：精确账本条目按 id 并入——山羊角(乐器组件)/附魔书全在精确账本，不并的话
                // 总线库存看不见它们、抽取白名单选择器（候选=网络现有）也列不出，m155 的招牌用例直接失明。
                java.util.List<ItemStack> tplB = sc.exactTemplates();
                for (int k = 0; k < tplB.size(); k++)
                    agg.merge(Registries.ITEM.getId(tplB.get(k).getItem()).toString(), sc.exactCount(k), StorageCoreBlockEntity::satAdd); // m273
            }
        }
        java.util.List<java.util.Map.Entry<String, Long>> top = new java.util.ArrayList<>(agg.entrySet());
        top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        // m163b：前10→前400。总线条按可用宽度自截断（超宽画"…"），多带的部分喂给抽取白名单
        // 选择器当候选源（客户端 busIdsCache 复用，零新协议）；400 封顶防极端档 NBT/包体失控。
        if (top.size() > 400) top = new java.util.ArrayList<>(top.subList(0, 400));
        java.util.List<String> nIds = new java.util.ArrayList<>();
        java.util.List<Long> nCts = new java.util.ArrayList<>();
        for (var en : top) { nIds.add(en.getKey()); nCts.add(en.getValue()); }
        boolean busChanged = !nIds.equals(busTopIds) || !nCts.equals(busTopCounts);
        if (busChanged) {
            busTopIds.clear(); busTopIds.addAll(nIds);
            busTopCounts.clear(); busTopCounts.addAll(nCts);
        }
        if (changed || busChanged) {
            markDirty();
            syncToClient();
        }
    }

    /** 该机器的定向产出目标（机器→存储 连线；不可用则 null 走默认路由）。 */
    private com.sdzjz.machine.StorageAccess depositFor(World world, int machineIndex) {
        if (prof != null) prof.storageResolves++; // m177
        return edgeStorage(world, machineIndex, 0);
    }

    /** 该机器的定向供料源（存储→机器 连线）。 */
    /** m92：链式需求判定——物品 id 能否被节点 i（含其下游）真实消费。放行规则沿途生效，深度/环双保护。 */
    /** m155 该 id 沿此节点的出线链能否到达垃圾桶（尊重过滤/开关/抽取闸门，深度8防环）。
     *  精确账本物品抽取的授权判定：只有"终点是销毁"才允许抹组件抽走。 */
    private boolean chainEndsInTrash(World world, int i, String id, int depth, java.util.Set<Integer> visited,
                                     java.util.Map<Integer, java.util.List<Integer>> outT) {
        if (depth > 8 || i < 0 || i >= machineNodes.size() || !visited.add(i)) return false;
        ItemStack st = machineNodes.get(i);
        if (nodePaused(st)) return false;
        if (isTrash(st)) return machineFilterAllows(st, id); // m160 安全桶名单外不算销毁终点
        if (isFilter(st) && !filterPasses(st, id)) return false;
        if (isSwitch(st) && !switchOn(st)) return false;
        if (isExtractor(st) && (!extractorLive(world, i, st) || !machineFilterAllows(st, id))) return false; // m160
        java.util.List<Integer> targets = outT.get(i);
        if (targets == null) return false;
        for (int t : targets)
            if (chainEndsInTrash(world, t, id, depth + 1, visited, outT)) return true;
        return false;
    }

    // m218d：chainWants/chainEndsInTrash 顶层调用的 visited 集合复用——此前每类型每节点 new HashSet，
    // 大仓库(数千类型)×多逻辑节点×每5t 可达每秒十万级分配纯喂GC。服务端tick单线程、顶层调用点只有
    // 拉料循环两处且不互相嵌套（递归自身传同一集合），per-BE scratch 清场复用安全。
    private final java.util.HashSet<Integer> wantsScratch = new java.util.HashSet<>();
    private final java.util.HashSet<Integer> trashScratch = new java.util.HashSet<>();
    private java.util.Set<Integer> wantsScratchCleared() { wantsScratch.clear(); return wantsScratch; }
    private java.util.Set<Integer> trashScratchCleared() { trashScratch.clear(); return trashScratch; }

    private boolean chainWants(World world, int i, String id, int depth,
                               java.util.Set<Integer> visited,
                               java.util.Map<Integer, java.util.List<Integer>> outT,
                               java.util.Map<Integer, java.util.Set<String>> crafterNeeds) {
        if (prof != null) prof.chainChecks++; // m177
        if (depth > 8 || i < 0 || i >= machineNodes.size() || !visited.add(i)) return false;
        ItemStack st = machineNodes.get(i);
        if (nodePaused(st)) return false; // m110b 暂停节点不参与链式需求
        if (isFilter(st)) {
            if (!filterPasses(st, id)) return false;
        } else if (isSwitch(st)) {
            if (!switchOn(st)) return false;
        } else if (isExtractor(st)) {
            if (!extractorLive(world, i, st) || !machineFilterAllows(st, id)) return false; // m160 感应+白名单闸
        } else if (isTrash(st)) {
            // m153 垃圾桶链式需求（用户实测：仓→过滤器(白名单山羊角)→垃圾桶抽不动）——
            // 经逻辑节点转接 = 玩家明确布线授权，垃圾桶作为终端什么都"想要"；
            // 直连仓依然不抽（拉料回路的节点类型清单本就不含垃圾桶，m150 防手滑边界不动）。
            return true;
        } else if (isSensor(st) || isDistributor(st)) {
            // 传感器闸门/分配器均分在下发阶段生效，需求判定直接放行
        } else if (st.getItem() instanceof AutoCrafterItem) {
            java.util.Set<String> needs = crafterNeeds.computeIfAbsent(i, k -> {
                String tgt = craftTarget(st);
                if (tgt.isEmpty()) return java.util.Set.of();
                String cr = craftRecipe(st); // m235 手选=只要那条的料；自动=全候选并集（m234）
                if (!cr.isEmpty()) for (var pp : CraftPlanner.plans(world, tgt))
                    if (pp.recipeId().equals(cr)) return pp.needs().keySet();
                return CraftPlanner.wants(world, tgt);
            });
            return needs.contains(id);
        } else if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem) {
            // m132 顺修：m131b 漏接链式需求——存储→过滤器→酿造塔 的拉料此前恒 false
            // （落进下方通用 MachineItem 分支，免费型 def 不吃供料）。语义照 accepts：材料+燃料。
            String tgtB = craftTarget(st);
            if (tgtB.isEmpty()) return false;
            var planB = com.sdzjz.machine.BrewPlanner.plan(world, tgtB);
            return planB != null && (planB.needs().containsKey(id) || com.sdzjz.machine.BrewPlanner.FUEL_ID.equals(id));
        } else if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem) {
            // m132：附魔工厂链式需求=书+青金石（经验非物品不走线）。
            String tgtE = craftTarget(st);
            if (tgtE.isEmpty()) return false;
            var planE = com.sdzjz.machine.EnchantPlanner.plan(world, tgtE);
            return planE != null && planE.needs().containsKey(id);
        } else if (st.getItem() instanceof MachineItem mi) {
            var def = mi.def();
            if (com.sdzjz.machine.Machines.smelterFamily(def.id())) // m173 熔炉族
                return com.sdzjz.machine.SmeltPlanner.resultOf(world, id) != null
                        && machineFilterAllows(st, id); // m149 滤掉的不收，留上游走默认路由
            if (def.consumesInputs()) {
                for (var in : def.inputs()) if (in.item().equals(id)) return true;
                return false;
            }
            return false; // 免费产出机不吃供料
        } else {
            return false; // 农场/笼子等
        }
        for (Integer t : outT.getOrDefault(i, java.util.List.of()))
            if (chainWants(world, t, id, depth + 1, visited, outT, crafterNeeds)) return true;
        return false;
    }

    /** m133：从当前端点+定向连线重算待加载区块清单（并入+miss衰减：连续24拍(≈2分钟)未见才剔除，
     *  重启自举期登记表为空不误删；上限64区块；自身区块走 FORCED 不占票）。 */
    private void refreshForceChunks(World world) {
        String selfDim = world.getRegistryKey().getValue().toString();
        long ownChunk = new net.minecraft.util.math.ChunkPos(pos).toLong();
        java.util.Set<String> cur = new java.util.HashSet<>();
        for (int i = 0; i < storageEndpoints.size(); i++) {
            long pl = storageEndpoints.get(i)[0];
            if (pl == OUTPUT_IFACE) continue; // m142：哨兵不是真实方块——解成天边区块发票=崩服根因（m140/本轮两连崩）
            BlockPos ep = BlockPos.fromLong(pl);
            String d = storageEndpointDims.get(i);
            long c = net.minecraft.util.math.ChunkPos.toLong(ep.getX() >> 4, ep.getZ() >> 4);
            if (!plausibleChunkLong(c)) continue; // m142：任何坏数据解出的边界外区块一律拒收
            if (d.equals(selfDim) && c == ownChunk) continue;
            cur.add(d + "|" + c);
        }
        for (int i = 0; i < storageEdges.size(); i++) {
            long pl = storageEdges.get(i)[1];
            if (pl == OUTPUT_IFACE) continue; // m142：连到输出接口节点的边同病，一并跳过
            BlockPos ep = BlockPos.fromLong(pl);
            String d = i < storageEdgeDims.size() ? storageEdgeDims.get(i) : selfDim;
            long c = net.minecraft.util.math.ChunkPos.toLong(ep.getX() >> 4, ep.getZ() >> 4);
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
        if (changed) markDirty();
    }

    /** m142：区块 long 合法性——世界边界 ±3000万方块 = 区块 ±187.5万。哨兵/坏数据解出的
     *  天边区块（如 OUTPUT_IFACE=Long.MIN+7 → 区块 -2097152，radius=1 邻块 -2097153 在
     *  22 位区段打包里回卷成 +2097151）会打崩实体管理器的 subSet 数学，一律拒收。 */
    private static boolean plausibleChunkLong(long chunkLong) {
        int cx = (int) chunkLong, cz = (int) (chunkLong >>> 32);
        return Math.abs(cx) <= 1_875_000 && Math.abs(cz) <= 1_875_000;
    }

    /** m133：对清单逐项续有期票（跨维度经 resolveDimWorld；票 300t 自动过期零清理）。 */
    private void renewEndpointTickets(net.minecraft.server.world.ServerWorld sw) {
        for (int i = 0; i < forceChunks.size(); i++) {
            World tw = resolveDimWorld(sw, forceDims.get(i));
            if (tw instanceof net.minecraft.server.world.ServerWorld tsw)
                CoreChunkLoading.ticket(tsw, forceChunks.get(i)[0]);
        }
    }

    /** m133：本核心当前是否钉住了自身区块（拆方块时由 Block 调 release）。 */
    public boolean chunkForceActive() { return chunkForceOn; }
    public boolean chunkOwnedFlag() { return chunkOwned; } // m268 供拆核心时把所有权传给 release

    private com.sdzjz.machine.StorageAccess supplyFor(World world, int machineIndex) {
        if (prof != null) prof.storageResolves++; // m177
        return edgeStorage(world, machineIndex, 1);
    }

    private com.sdzjz.machine.StorageAccess edgeStorage(World world, int machineIndex, int dir) {
        for (int i = 0; i < storageEdges.size(); i++) {
            long[] e = storageEdges.get(i);
            if (e[0] != machineIndex || e[2] != dir) continue;
            com.sdzjz.machine.StorageAccess sc = resolveStorageAt(world, storageEdgeDims.get(i), e[1]);
            if (sc != null) return sc;
        }
        return null;
    }

    private com.sdzjz.machine.StorageAccess resolveStorageAt(World world, String dim, long posLong) {
        if (posLong == OUTPUT_IFACE) return null; // 输出接口=默认自动路由，无实体存储
        BlockPos p = BlockPos.fromLong(posLong);
        String self = world.getRegistryKey().getValue().toString();
        if (dim == null || dim.isEmpty() || self.equals(dim)) { // 空维度串按本维度处理（老数据兜底）
            if (!world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return null;
            return asAccess(world.getBlockEntity(p));
        }
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            RegistryKey<World> key;
            try {
                key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dim));
            } catch (Exception e) {
                return null; // 畸形维度串：不炸 tick
            }
            net.minecraft.server.world.ServerWorld ow = sw.getServer().getWorld(key);
            if (ow == null || !ow.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return null;
            return asAccess(ow.getBlockEntity(p));
        }
        return null;
    }

    /** 端点方块 → 存储访问：存储核心=单库；数据面板=聚合它网络里的全部存储核心（连到面板即连到整个网络）。 */
    private static com.sdzjz.machine.StorageAccess asAccess(net.minecraft.block.entity.BlockEntity be) {
        if (be instanceof StorageCoreBlockEntity sc) return sc;
        if (be instanceof DataPanelBlockEntity dp) return dp;
        return null;
    }

    /** 连/断一条 机器↔存储 定向连线（已存在则断开）。dir 0=产出到该存储 1=从该存储供料。 */
    public void toggleStorageEdge(net.minecraft.entity.player.PlayerEntity byPlayer, int machineIndex, long storagePos, int dir, String dim) {
        if (machineIndex < 0 || machineIndex >= machineNodes.size() || dir < 0 || dir > 1) return;
        boolean known = false; // 只允许连到画布上确实显示的端点，防伪造包连任意坐标
        String epDim = null;
        for (int k = 0; k < storageEndpoints.size(); k++) {
            long[] ep = storageEndpoints.get(k);
            if (ep[0] == storagePos) { known = true; epDim = storageEndpointDims.get(k); break; }
        }
        if (!known) return;
        for (int i = 0; i < storageEdges.size(); i++) {
            long[] e = storageEdges.get(i);
            if (e[0] == machineIndex && e[1] == storagePos && e[2] == dir) {
                storageEdges.remove(i);
                storageEdgeDims.remove(i);
                markDirty();
                syncToClient();
                return;
            }
        }
        int capSE = SdzjzConfig.get().maxEdgesPerCore; // m270 存储连线单独同额封顶（断开在上面永远放行，只闸新增）
        if (capSE > 0 && storageEdges.size() >= capSE) {
            capMsg(byPlayer, "存储连线总数已达上限 " + capSE + "（config maxEdgesPerCore 可调，0=无限）");
            return;
        }
        // 维度以服务端端点表为准（客户端传空/伪造都不作数），兜底当前维度
        String useDim = (epDim != null && !epDim.isEmpty()) ? epDim
                : (dim != null && !dim.isEmpty()) ? dim
                : (world != null ? world.getRegistryKey().getValue().toString() : "minecraft:overworld");
        storageEdges.add(new long[]{machineIndex, storagePos, dir});
        storageEdgeDims.add(useDim);
        markDirty();
        syncToClient();
    }

    /** 设置存储节点画布坐标。 */
    /** m265 画布落位坐标钳制（±1,000,000，防伪造包写极端值进 NBT/参与几何运算溢出）。
     *  m269 升 long 入参：moveGroup 用 long 加法防 int 溢出后直接喂进来；原 int 调用点自动拓宽零改动。 */
    private static int clampCanvas(long v) { return (int) Math.max(-1_000_000L, Math.min(1_000_000L, v)); }

    /** m265 语义升级：写入即"放置到画布"——值升三元 {x, y, 1}，第三位=放置标记。
     *  历史二元值（m80 前遗留 + 旧整理布局写入的死数据）没有标记位=仍视为停靠，老档不惊动。 */
    public void setStorageNodePos(long storagePos, int nx, int ny) {
        storageNodePos.put(storagePos, new int[]{clampCanvas(nx), clampCanvas(ny), 1});
        markDirty();
        syncToClient();
    }

    /** m265 收回总线：删除画布落位（回停靠栏）。 */
    public void dockStorageNode(long storagePos) {
        if (storageNodePos.remove(storagePos) == null) return;
        markDirty();
        syncToClient();
    }

    /** m265 该端点是否已放置到画布（三元且标记位=1；二元遗留=否）。 */
    public boolean storageNodePlaced(long pl) {
        int[] v = storageNodePos.get(pl);
        return v != null && v.length >= 3 && v[2] == 1;
    }

    /** m265 打包已放置端点落位（并行列表，只发已放置项；与 buildEndsPayload 同拍走 m89 通道）。 */
    public com.sdzjz.net.StorageNodeHomePayload buildHomesPayload(BlockPos pos) {
        java.util.List<Long> ep = new java.util.ArrayList<>();
        java.util.List<Integer> hx = new java.util.ArrayList<>();
        java.util.List<Integer> hy = new java.util.ArrayList<>();
        for (long[] e : storageEndpoints) {
            int[] v = storageNodePos.get(e[0]);
            if (v != null && v.length >= 3 && v[2] == 1) { ep.add(e[0]); hx.add(v[0]); hy.add(v[1]); }
        }
        return new com.sdzjz.net.StorageNodeHomePayload(pos, ep, hx, hy);
    }

    public java.util.List<long[]> storageEndpointsView() { return storageEndpoints; }

    /** m89：打包端点+总线库存（并行列表）。 */
    public com.sdzjz.net.CanvasEndsPayload buildEndsPayload(BlockPos pos) {
        java.util.List<Long> ep = new java.util.ArrayList<>();
        java.util.List<Integer> ek = new java.util.ArrayList<>();
        java.util.List<String> ed = new java.util.ArrayList<>();
        for (int i = 0; i < storageEndpoints.size(); i++) {
            ep.add(storageEndpoints.get(i)[0]);
            ek.add((int) storageEndpoints.get(i)[1]);
            ed.add(i < storageEndpointDims.size() ? storageEndpointDims.get(i) : "");
        }
        return new com.sdzjz.net.CanvasEndsPayload(pos, ep, ek, ed,
                new java.util.ArrayList<>(busTopIds), new java.util.ArrayList<>(busTopCounts));
    }

    // m85：总线库存（网络前10物品，画布顶栏「存储总线（网络库存）」展示）
    private final java.util.List<String> busTopIds = new java.util.ArrayList<>();
    private final java.util.List<Long> busTopCounts = new java.util.ArrayList<>();
    public java.util.List<String> busTopIdsView() { return busTopIds; }
    public java.util.List<Long> busTopCountsView() { return busTopCounts; }

    // m86：实测产量（分钟滚动窗口；生成点计数，不在 deposit 链上数防重复）
    private long prodWin = 0, prodPerMin = 0, prodWinStart = 0;
    void prodTally(long n) { if (n > 0) prodWin += n; }
    public long prodPerMinView() { return prodPerMin; }

    private World resolveDimWorld(World base, String dim) {
        if (dim == null || dim.isEmpty() || base.getRegistryKey().getValue().toString().equals(dim)) return base;
        if (base instanceof net.minecraft.server.world.ServerWorld sw)
            return sw.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dim)));
        return null;
    }
    public java.util.List<String> storageEndpointDimsView() { return storageEndpointDims; }
    public java.util.List<long[]> storageEdgesView() { return storageEdges; }

    public int storageNodeX(long pl, int def) {
        int[] v = storageNodePos.get(pl);
        return v != null ? v[0] : def;
    }

    public int storageNodeY(long pl, int def) {
        int[] v = storageNodePos.get(pl);
        return v != null ? v[1] : def;
    }

    // ===== 连线内部物流缓存 =====
    private long bufCount(String id) { return internalBuffer.getOrDefault(id, 0L); }

    // ===== 按边精确路由：每节点输入缓存 =====
    /** 取第 i 个节点的输入缓存（懒补齐对齐 machineNodes）。 */
    private java.util.Map<String, Long> nodeBuf(int i) {
        while (nodeBufs.size() < machineNodes.size()) nodeBufs.add(new java.util.HashMap<>());
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
    private void distributeEven(World world, int fromIndex, java.util.List<Integer> targets, String id, long amt) {
        if (prof != null) prof.routes++; // m177
        if (targets != null && !targets.isEmpty()) {
            java.util.List<Integer> ok = new java.util.ArrayList<>();
            for (int t : targets)
                if (t >= 0 && t < machineNodes.size() && accepts(world, t, id)) ok.add(t);
            if (!ok.isEmpty()) {
                long share = amt / ok.size(), extra = amt % ok.size(), undelivered = 0;
                for (int k = 0; k < ok.size(); k++) {
                    long want = share + (k < extra ? 1 : 0);
                    if (want <= 0) continue;
                    java.util.Map<String, Long> m = nodeBuf(ok.get(k));
                    if (!bufTypeOk(m, id)) { undelivered += want; continue; } // m270 类型上限：拒收份额转默认路由
                    long cur = m.getOrDefault(id, 0L);
                    long put = Math.min(Math.max(0, BUF_CAP - cur), want);
                    if (put > 0) m.put(id, cur + put);
                    undelivered += want - put;
                }
                amt = undelivered;
            }
        }
        if (amt <= 0) return;
        com.sdzjz.machine.StorageAccess dep = depositFor(world, fromIndex);
        ItemStack rest = new ItemStack(Registries.ITEM.get(Identifier.of(id)), (int) Math.min(amt, 64L * OUTPUT_SLOTS));
        if (dep != null) depositOrBuffer(dep, rest);
        else addOutput(rest);
    }

    /** 按需分发：只把目标机器"吃得下"的物品送下线；没人要的部分走 定向存储/默认路由——绝不堵死在下游缓存里。 */
    private void distribute(World world, int fromIndex, java.util.List<Integer> targets, String id, long amt) {
        if (prof != null) prof.routes++; // m177
        if (targets != null) {
            for (int pass = 0; pass < 2; pass++) { // m150 两轮：第一轮跳过垃圾桶，第二轮只喂垃圾桶——
                for (int t : targets) {            // 垃圾桶永远是最后去处，与连线先后无关（不然想要的会被吞）
                    if (amt <= 0) return;
                    if (t < 0 || t >= machineNodes.size()) continue;
                    if ((pass == 0) == isTrash(machineNodes.get(t))) continue;
                    if (!accepts(world, t, id)) continue;
                    java.util.Map<String, Long> m = nodeBuf(t);
                    if (!bufTypeOk(m, id)) continue; // m270 类型上限：跳过满型目标，余量走下面定向存储/默认路由
                    long cur = m.getOrDefault(id, 0L);
                    long put = Math.min(Math.max(0, BUF_CAP - cur), amt);
                    if (put > 0) { m.put(id, cur + put); amt -= put; }
                }
            }
        }
        if (amt <= 0) return;
        com.sdzjz.machine.StorageAccess dep = depositFor(world, fromIndex); // 剩余按定向存储→默认路由
        ItemStack rest = new ItemStack(Registries.ITEM.get(Identifier.of(id)), (int) Math.min(amt, 64L * OUTPUT_SLOTS));
        if (dep != null) depositOrBuffer(dep, rest);
        else addOutput(rest);
    }

    /** 目标机器是否"吃"该物品：万能熔炉=可熔炼物；消耗机=配方输入；自动合成机=当前目标用料；农场=不吃。 */
    private boolean accepts(World world, int target, String id) {
        ItemStack st = machineNodes.get(target);
        if (nodePaused(st)) return false;                // m110b 暂停不收（上游改走默认路由）
        if (isFilter(st)) return filterPasses(st, id);   // 拦下的留在上游走默认路由→存储
        if (isSensor(st)) return sensorOpen(world, target); // 关闸不收（上游全关闸时整台暂停）
        if (isSwitch(st)) return switchOn(st);              // 关闸不收，同上
        if (isDistributor(st)) return true;                 // 分配器什么都收（分不出去的走默认路由）
        if (isTrash(st)) return machineFilterAllows(st, id); // m150 垃圾桶 / m160 安全桶：白名单空=连啥吞啥，非空=只吞名单内
        if (isExtractor(st))                                  // m154 开=收 / m160 感应联动+白名单一起闸
            return extractorLive(world, target, st) && machineFilterAllows(st, id);
        if (st.getItem() instanceof AutoCrafterItem) {
            String tgt = craftTarget(st);
            if (tgt.isEmpty()) return false;
            String cr = craftRecipe(st); // m235 手选=只收那条的料
            if (!cr.isEmpty()) for (var pp : CraftPlanner.plans(world, tgt))
                if (pp.recipeId().equals(cr)) return pp.needs().containsKey(id);
            return CraftPlanner.wants(world, tgt).contains(id); // m234 全候选并集
        }
        if (st.getItem() instanceof com.sdzjz.item.BrewingTowerItem) { // m131b：吃酿造链材料+燃料
            String tgt = craftTarget(st);
            if (tgt.isEmpty()) return false;
            var plan = com.sdzjz.machine.BrewPlanner.plan(world, tgt);
            return plan != null && (plan.needs().containsKey(id) || com.sdzjz.machine.BrewPlanner.FUEL_ID.equals(id));
        }
        if (st.getItem() instanceof com.sdzjz.item.EnchantFactoryItem) { // m132：吃书+青金石（经验非物品不走线）
            String tgt = craftTarget(st);
            if (tgt.isEmpty()) return false;
            var plan = com.sdzjz.machine.EnchantPlanner.plan(world, tgt);
            return plan != null && plan.needs().containsKey(id);
        }
        if (st.getItem() instanceof MachineItem mi) {
            if (com.sdzjz.machine.Machines.smelterFamily(mi.def().id())) // m173 熔炉族
                return com.sdzjz.machine.SmeltPlanner.resultOf(world, id) != null
                        && machineFilterAllows(st, id); // m149
            if (mi.def().consumesInputs()) {
                for (MachineDef.Input in : mi.def().inputs()) if (in.item().equals(id)) return true;
            }
        }
        return false;
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
            addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(id)), (int) Math.min(spill, 64L * OUTPUT_SLOTS)));
        } else {
            internalBuffer.put(id, sum);
        }
    }

    /** m270 硬上限拒绝提示（actionbar，空玩家安全跳过）——m99 教训：静默无效比数值弱更伤，界面上必须看得出来。 */
    private static void capMsg(net.minecraft.entity.player.PlayerEntity p, String s) {
        if (p != null) p.sendMessage(net.minecraft.text.Text.literal(s), true);
    }

    /** m270 节点连线度数（进+出合计），配合 maxEdgesPerNode。 */
    private int nodeDegree(int n) {
        int d = 0;
        for (int[] c : connections) if (c[0] == n || c[1] == n) d++;
        return d;
    }

    /** 连/断一条 from→to 连线（已存在则断开）。m270 签名升带玩家：上限拒绝要提示（m99 静默无效教训）。 */
    public void toggleConnection(net.minecraft.entity.player.PlayerEntity byPlayer, int from, int to) {        if (from == to || from < 0 || to < 0 || from >= machineNodes.size() || to >= machineNodes.size()) return;
        for (int i = 0; i < connections.size(); i++) {
            int[] c = connections.get(i);
            if (c[0] == from && c[1] == to) { connections.remove(i); bumpTopo(); markDirty(); syncToClient(); return; } // m179
        }
        SdzjzConfig cfgE = SdzjzConfig.get(); // m270 硬上限：断开永远放行，只闸新增
        if (cfgE.maxEdgesPerCore > 0 && connections.size() >= cfgE.maxEdgesPerCore) {
            capMsg(byPlayer, "连线总数已达上限 " + cfgE.maxEdgesPerCore + "（config maxEdgesPerCore 可调，0=无限）");
            return;
        }
        if (cfgE.maxEdgesPerNode > 0 && (nodeDegree(from) >= cfgE.maxEdgesPerNode || nodeDegree(to) >= cfgE.maxEdgesPerNode)) {
            capMsg(byPlayer, "单节点连线已达上限 " + cfgE.maxEdgesPerNode + "（config maxEdgesPerNode 可调，0=无限）");
            return;
        }
        connections.add(new int[]{from, to});
        bumpTopo(); // m179
        markDirty();
        syncToClient();
    }

    /** 画布渲染读取（客户端）。 */
    public java.util.List<ItemStack> nodes() { return machineNodes; }

    /** 破坏时掉落全部（升级/产出 + 机器节点）。 */
    public void dropAll(World world, BlockPos pos) {
        ItemScatterer.spawn(world, pos, this);
        for (ItemStack s : machineNodes) {
            NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            for (int type = 0; type < 3; type++) {
                int lv = n.getInt(upgradeKey(type));
                Item item = upgradeItem(type);
                if (item != null && lv > 0)
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(item, lv));
            }
            s.remove(DataComponentTypes.CUSTOM_DATA);
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), s);
        }
        machineNodes.clear();
        bumpTopo(); // m179
        nodeStatus.clear();
        nodeReason.clear(); // m178
    }

    /** m181 是否有玩家正开着本核心的画布（判定复用 m89 端点直发同款：currentScreenHandler 指向本 pos）。 */
    private boolean hasCanvasViewer(World world) {
        if (!(world instanceof net.minecraft.server.world.ServerWorld sw)) return false;
        for (net.minecraft.server.network.ServerPlayerEntity sp : sw.getServer().getPlayerManager().getPlayerList()) {
            if (sp.currentScreenHandler instanceof com.sdzjz.screen.StructureCoreScreenHandler h
                    && pos.equals(h.blockPos())) return true;
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
        if (world != null && !world.isClient) canvasDirty = true;
    }

    /** tickInner 顶部每 tick 调用：脏则升版本；对正在看画布的玩家（m89 管线同款判定）按版本差补发渲染快照。
     *  快照每 tick 至多编码一次；版本对齐的观众不重发；无观众清表=重开屏必得首包（createMenu 强刷照旧标脏兜底）。 */
    private void flushCanvasSnapshot(World world) {
        if (!(world instanceof net.minecraft.server.world.ServerWorld sw)) return;
        if (canvasDirty) { snapshotRev++; canvasDirty = false; }
        com.sdzjz.net.CanvasSnapshotPayload pk = null;
        boolean anyViewer = false;
        for (net.minecraft.server.network.ServerPlayerEntity sp : sw.getServer().getPlayerManager().getPlayerList()) {
            if (!(sp.currentScreenHandler instanceof com.sdzjz.screen.StructureCoreScreenHandler h)
                    || !pos.equals(h.blockPos())) continue;
            anyViewer = true;
            Long sent = snapshotSent.get(sp.getUuid());
            if (sent != null && sent == snapshotRev) continue;
            if (pk == null) {
                NbtCompound snap = new NbtCompound();
                writeRenderNbt(snap, sw.getRegistryManager());
                pk = new com.sdzjz.net.CanvasSnapshotPayload(pos, snap);
                if (prof != null) { try { prof.syncBytes += com.sdzjz.debug.CoreProfiler.nbtSize(snap); } catch (Exception ignored) {} } // m177 对表尺随刀迁移
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, pk);
            if (prof != null) prof.syncPackets++; // m275 起口径=真实发出的快照包数（原=updateListeners 调用数）
            snapshotSent.put(sp.getUuid(), snapshotRev);
        }
        if (!anyViewer && !snapshotSent.isEmpty()) snapshotSent.clear();
    }

    /** m177 /sdzjz dumpgraph：整图转储（节点+连线+运行态），进服务器日志用。 */
    public String debugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("running=").append(running).append(" nodes=").append(machineNodes.size())
          .append(" edges=").append(connections.size()).append(" prodPerMin=").append(prodPerMin).append('\n');
        for (int i = 0; i < machineNodes.size(); i++) {
            ItemStack st = machineNodes.get(i);
            sb.append(String.format("  [%d] %s x%d%s%n", i,
                    net.minecraft.registry.Registries.ITEM.getId(st.getItem()), st.getCount(),
                    nodePaused(st) ? " (暂停)" : ""));
        }
        for (int[] c : connections) sb.append("  edge ").append(c[0]).append(" -> ").append(c[1]).append('\n');
        return sb.toString();
    }

    private boolean pop(PlayerEntity player, int i) {
        ItemStack s = items.get(i);
        if (s.isEmpty()) return false;
        if (!player.getInventory().insertStack(s.copy())) player.dropItem(s.copy(), false);
        items.set(i, ItemStack.EMPTY);
        markDirty();
        return true;
    }

    private int countUpgrade(net.minecraft.item.Item up) {
        int n = 0;
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) {
            if (items.get(i).isOf(up)) n += items.get(i).getCount();
        }
        return n;
    }

    /** 定向入库兜底：存储核心类型满被拒时回落输出缓存，绝不静默丢物品。 */
    private void depositOrBuffer(com.sdzjz.machine.StorageAccess sc, ItemStack stack) {
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
                int put = Math.min(remain, out.getMaxCount());
                items.set(i, out.copyWithCount(put)); // m131b：保组件（此前重建栈抹组件——药水/附魔件进输出缓存会变裸件）
                remain -= put;
            } else if (ItemStack.areItemsAndComponentsEqual(slot, out) && slot.getCount() < slot.getMaxCount()) { // m131b：异组件不并栈
                int put = Math.min(remain, slot.getMaxCount() - slot.getCount());
                slot.increment(put);
                remain -= put;
            }
        }
    }

    // ===== 输出路由缓存：目标坐标缓存 40 tick，避免每个生产周期反复 BFS =====
    private BlockPos cachedOutPos;
    private long cachedOutUntil;
    private BlockPos cachedInPos;
    private long cachedInUntil;

    /** 解析消耗机取料源（存储核心），带缓存。 */
    StorageCoreBlockEntity resolveInputSource(World world, BlockPos corePos) {
        long now = world.getTime();
        if (cachedInPos != null && now < cachedInUntil
                && world.getChunkManager().isChunkLoaded(cachedInPos.getX() >> 4, cachedInPos.getZ() >> 4)
                && world.getBlockEntity(cachedInPos) instanceof StorageCoreBlockEntity sc) {
            return sc;
        }
        cachedInPos = null;
        StorageCoreBlockEntity src = boundPanel(world, corePos);
        if (src == null) src = findPanel(world, corePos);
        if (src == null && hasWirelessNode(world, corePos)) src = nearestWirelessPanel(world, corePos);
        if (src == null && hasSatelliteNode(world, corePos)) src = findSatellitePanel(world, corePos);
        if (src != null && src.getWorld() == world) {
            cachedInPos = src.getPos().toImmutable();
            cachedInUntil = now + 40;
        }
        return src;
    }

    private long cachedOutMissUntil = -1000; // m114 断网负缓存时间戳

    /** 解析输出目标（存储核心或普通容器），带缓存。仅缓存同维度目标；查无目标也缓存 40t（m114）。 */
    private Object resolveOutTarget(World world, BlockPos corePos) {
        long now = world.getTime();
        if (cachedOutPos != null && now < cachedOutUntil
                && world.getChunkManager().isChunkLoaded(cachedOutPos.getX() >> 4, cachedOutPos.getZ() >> 4)) {
            BlockEntity be = world.getBlockEntity(cachedOutPos);
            if (be instanceof StorageCoreBlockEntity sc) return sc;
            if (be instanceof Inventory inv && !(be instanceof StructureCoreBlockEntity)) return inv;
        }
        if (now < cachedOutMissUntil) return null; // m114 负缓存：断网核心不必每次全套 BFS+无线/卫星扫描
        cachedOutPos = null;
        Object target = boundPanel(world, corePos);
        if (target == null) target = findTarget(world, corePos);
        if (target == null && hasWirelessNode(world, corePos)) target = nearestWirelessPanel(world, corePos);
        if (target == null && hasSatelliteNode(world, corePos)) target = findSatellitePanel(world, corePos);
        if (target instanceof BlockEntity tbe && tbe.getWorld() == world) {
            cachedOutPos = tbe.getPos().toImmutable();
            cachedOutUntil = now + 40;
        } else if (target == null) {
            cachedOutMissUntil = now + 40; // 新接存储最迟 2s 被感知——与全 MOD 既有 40t 缓存同语义
        }
        return target;
    }

    /** 把输出缓存推入正下方容器。 */
    /** 把输出缓存送到：相邻的数据面板/箱子，或顺着数据线 BFS 连到的存储。 */
    private void pushOutput(World world, BlockPos corePos) {
        Object target = resolveOutTarget(world, corePos);
        if (target == null) return;
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) continue;
            if (target instanceof StorageCoreBlockEntity panel) panel.deposit(slot);
            else if (target instanceof Inventory inv) insertInto(inv, slot);
            if (slot.isEmpty()) items.set(i, ItemStack.EMPTY);
        }
    }

    /** m114 断网喷射：核心连不到面板/存储/箱子时，输出缓存不再憋死——每 10t 从顶面发射器式
     *  喷出一组。有存储时不喷（pushOutput 正常落库）；节流 1 组/10t 防实体洪水（同类掉落物原版
     *  自动合堆+5 分钟消失双兜底）。m99 封顶仍在：缓存满停产、喷射腾格后自动续产——
     *  离网吞吐≈喷射速率（约 2 组/秒），天然自限。 */
    private boolean ejectWarned = false; // m115：断网喷射只警告一次，接回存储后复位
    private boolean lagPause = false;    // m115：过载全线暂停标志（滞回控制）

    private void ejectOverflow(World world, BlockPos corePos) {
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
            net.minecraft.entity.ItemEntity e = new net.minecraft.entity.ItemEntity(world,
                    corePos.getX() + 0.5, corePos.getY() + 1.1, corePos.getZ() + 0.5, out,
                    (r.nextDouble() - 0.5) * 0.16, 0.30 + r.nextDouble() * 0.08, (r.nextDouble() - 0.5) * 0.16);
            e.setToDefaultPickupDelay();
            e.addCommandTag("sdzjz_ejected"); // m115：打标——极端卡顿只清自家喷出的，绝不动玩家掉落
            world.spawnEntity(e);
            markDirty();
            return; // 每次最多一组
        }
    }

    /** m115：给核心 24 格内的玩家发一条聊天提示。 */
    private void warnNearby(World world, String text) {
        for (net.minecraft.entity.player.PlayerEntity pl : world.getPlayers())
            if (pl.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 24 * 24)
                pl.sendMessage(net.minecraft.text.Text.literal(text), false);
    }

    /** m115 极端卡顿(>60ms/tick)：清理本核心周边 64 格内带 sdzjz_ejected 标签的掉落物。 */
    private void cleanupEjected(net.minecraft.server.world.ServerWorld sw) {
        var box = net.minecraft.util.math.Box.of(pos.toCenterPos(), 64, 32, 64);
        for (net.minecraft.entity.ItemEntity e : sw.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, box,
                en -> en.getCommandTags().contains("sdzjz_ejected"))) e.discard();
    }

    /** 从核心出发，直连相邻存储；遇数据线则继续路由，返回最近的数据面板/箱子。 */
    private Object findTarget(World world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.offset(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCoreBlockEntity panel) return panel;
                if (be instanceof Inventory inv && !(be instanceof StructureCoreBlockEntity)) return inv;
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return null;
    }

    /** 核心相邻或其数据线网络上是否接了无线节点。 */
    private boolean hasWirelessNode(World world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 128;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.offset(d);
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
    private StorageCoreBlockEntity nearestWirelessPanel(World world, BlockPos corePos) {
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
    private boolean hasSatelliteNode(World world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 128;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.offset(d);
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
        this.boundPanelPos = pos == null ? null : pos.toImmutable();
        this.boundPanelDim = dim;
        markDirty();
    }

    /** 绑定目标可达则返回（同维度需无线/卫星/有线可达；跨维度需卫星）。优先级最高。 */
    private StorageCoreBlockEntity boundPanel(World world, BlockPos corePos) {
        if (boundPanelPos == null || boundPanelDim == null) return null;
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(boundPanelDim));
        boolean sameDim = world.getRegistryKey().equals(dimKey);
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
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.server.world.ServerWorld ow = sw.getServer().getWorld(dimKey);
            if (ow != null) return StorageCoreBlockEntity.loadedCoreAt(ow, boundPanelPos);
        }
        return null;
    }

    /** 目标面板是否经相邻/数据线有线可达。 */
    private boolean wiredReaches(World world, BlockPos corePos, BlockPos target) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.offset(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                if (np.equals(target)) return true;
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return false;
    }

    /** 卫星：本维度最近(无距离上限)优先，否则其它已加载维度里任意一个数据面板。 */
    private StorageCoreBlockEntity findSatellitePanel(World world, BlockPos corePos) {
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
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            var server = sw.getServer();
            for (var key : java.util.List.copyOf(StorageCoreBlockEntity.dimensionsWithCores())) {
                if (key.equals(world.getRegistryKey())) continue;
                net.minecraft.server.world.ServerWorld ow = server.getWorld(key);
                if (ow == null) continue;
                for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(ow))) {
                    StorageCoreBlockEntity panel = StorageCoreBlockEntity.loadedCoreAt(ow, p);
                    if (panel != null) return panel;
                }
            }
        }
        return found;
    }
    private StorageCoreBlockEntity findPanel(World world, BlockPos corePos) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(corePos);
        seen.add(corePos);
        int budget = 256;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos cur = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = cur.offset(d);
                if (DataCableBlockEntity.linkBlocked(world, cur, d, np)) continue; // m233 按面断开：此边不通（先于 seen）
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCoreBlockEntity panel) return panel;
                if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return null;
    }

    private static void insertInto(Inventory target, ItemStack stack) {
        for (int i = 0; i < target.size() && !stack.isEmpty(); i++) {
            ItemStack t = target.getStack(i);
            if (t.isEmpty()) {
                target.setStack(i, stack.copyAndEmpty());
                return;
            } else if (ItemStack.areItemsAndComponentsEqual(t, stack) && t.getCount() < t.getMaxCount()) {
                int move = Math.min(stack.getCount(), t.getMaxCount() - t.getCount());
                t.increment(move);
                stack.decrement(move);
            }
        }
    }

    // ================= NBT =================
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        // m275：以下为存档专属字段（画布客户端从不消费：items=画布handler无槽位、双缓存/绑定/运行池/强载/所有权纯服务端）
        Inventories.writeNbt(nbt, items, lookup);
        NbtCompound buf = new NbtCompound();
        for (java.util.Map.Entry<String, Long> e : internalBuffer.entrySet()) buf.putLong(e.getKey(), e.getValue());
        nbt.put("internalBuffer", buf);
        NbtList nbl = new NbtList(); // 每节点输入缓存（与 machineNodes 同序）
        for (int i = 0; i < machineNodes.size(); i++) {
            NbtCompound c = new NbtCompound();
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
        NbtList flc = new NbtList(); // m133 待续票端点区块（重启自举清单）
        for (int i = 0; i < forceChunks.size(); i++) {
            NbtCompound fc = new NbtCompound();
            fc.putLong("c", forceChunks.get(i)[0]);
            fc.putInt("m", (int) forceChunks.get(i)[1]);
            fc.putString("d", forceDims.get(i));
            flc.add(fc);
        }
        nbt.put("forceChunks", flc);
        nbt.putBoolean("chunkOwned", chunkOwned); // m268 强加载所有权（管理员 /forceload 撞同区块=false，重启后据此判断该不该解除）
        // m275：渲染子集与观众定向同步快照共用同一编码函数——加渲染字段只改 writeRenderNbt，存档/同步自动同拍不漂移
        writeRenderNbt(nbt, lookup);
    }

    /** m275：渲染快照子集=画布客户端消费面全集（清单依据 docs/同步拆分方案_m274.md §2 grep 实测：
     *  节点栈/连线/分组/状态灯/阻塞原因/存储端点三件套/总线库存/实测产量）。
     *  存档 writeNbt 与 flushCanvasSnapshot 共用。 */
    private void writeRenderNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList mn = new NbtList();
        for (ItemStack s : machineNodes) if (!s.isEmpty()) mn.add(s.encode(lookup));
        nbt.put("machineNodes", mn);
        int[] flat = new int[connections.size() * 2];
        for (int i = 0; i < connections.size(); i++) { flat[i * 2] = connections.get(i)[0]; flat[i * 2 + 1] = connections.get(i)[1]; }
        nbt.putIntArray("connections", flat);
        NbtCompound grp = new NbtCompound(); // m191 分组元数据 id→名（成员归属在各节点栈里随 machineNodes 落盘）
        for (var ge : groupNames.entrySet()) grp.putString(Integer.toString(ge.getKey()), ge.getValue());
        nbt.put("groups", grp);
        int[] nst = new int[machineNodes.size()];
        for (int i = 0; i < nst.length; i++) nst[i] = i < nodeStatus.size() ? nodeStatus.get(i) : 0;
        nbt.putIntArray("nodeStat", nst);
        NbtList nwl = new NbtList(); // m178 阻塞原因（与 nodeStat 同序；转绿清空）
        for (int i = 0; i < nst.length; i++) nwl.add(net.minecraft.nbt.NbtString.of(i < nodeReason.size() ? nodeReason.get(i) : ""));
        nbt.put("nodeWhy", nwl);
        NbtList eps = new NbtList(); // 存储端点（同步给画布：连了几个显示几个）
        for (int i = 0; i < storageEndpoints.size(); i++) {
            NbtCompound c = new NbtCompound();
            c.putLong("p", storageEndpoints.get(i)[0]);
            c.putInt("k", (int) storageEndpoints.get(i)[1]);
            c.putString("d", storageEndpointDims.get(i));
            eps.add(c);
        }
        nbt.put("storEnds", eps);
        NbtList seg = new NbtList(); // 机器↔存储 定向连线
        for (int i = 0; i < storageEdges.size(); i++) {
            NbtCompound c = new NbtCompound();
            c.putInt("m", (int) storageEdges.get(i)[0]);
            c.putLong("p", storageEdges.get(i)[1]);
            c.putInt("r", (int) storageEdges.get(i)[2]);
            c.putString("d", storageEdgeDims.get(i));
            seg.add(c);
        }
        nbt.put("storEdges", seg);
        NbtCompound spn = new NbtCompound(); // 存储节点画布坐标
        for (var en : storageNodePos.entrySet()) spn.putIntArray(Long.toString(en.getKey()), en.getValue());
        nbt.put("storNodePos", spn);
        NbtList bt = new NbtList(); // m85 总线库存
        for (int i = 0; i < busTopIds.size(); i++) {
            NbtCompound c = new NbtCompound();
            c.putString("i", busTopIds.get(i));
            c.putLong("n", busTopCounts.get(i));
            bt.add(c);
        }
        nbt.put("busTop", bt);
        nbt.putLong("prodPM", prodPerMin); // m86 实测产量
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        readRenderNbt(nbt, lookup); // m275：渲染子集先读——下方 nodeBufs 循环依赖 machineNodes.size()
        internalBuffer.clear();
        NbtCompound buf = nbt.getCompound("internalBuffer");
        int droppedBuf = 0; // m273：缓存读入校验——写路径 left<=0 即 remove，零/负值从不合法落盘；负数毒化计数算术且可绕封顶
        for (String k : buf.getKeys()) {
            long v = buf.getLong(k);
            if (!k.isEmpty() && v > 0) internalBuffer.put(k, v); else droppedBuf++;
        }
        nodeBufs.clear();
        NbtList nbl = nbt.getList("nodeBufs", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < machineNodes.size(); i++) {
            java.util.Map<String, Long> m = new java.util.HashMap<>();
            if (i < nbl.size()) {
                NbtCompound c = nbl.getCompound(i);
                for (String k : c.getKeys()) {
                    long v = c.getLong(k);
                    if (!k.isEmpty() && v > 0) m.put(k, v); else droppedBuf++; // m273 同口径
                }
            }
            nodeBufs.add(m); // 老档无此键=全空缓存；共享池留在 internalBuffer 里继续被消耗（无损迁移）
        }
        if (droppedBuf > 0) com.sdzjz.Sdzjz.LOGGER.warn("结构核心 {} 缓存读入丢弃 {} 条非法条目（空键或非正计数）", pos, droppedBuf);
        if (nbt.contains("boundPos")) {
            boundPanelPos = BlockPos.fromLong(nbt.getLong("boundPos"));
            boundPanelDim = nbt.getString("boundDim");
        } else {
            boundPanelPos = null; boundPanelDim = null;
        }
        running = nbt.getBoolean("running");
        xpPool = nbt.getDouble("xpPool");
        forceChunks.clear();
        forceDims.clear();
        NbtList flc = nbt.getList("forceChunks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < flc.size(); i++) {
            NbtCompound fc = flc.getCompound(i);
            long c = fc.getLong("c");
            if (!plausibleChunkLong(c)) continue; // m142：清洗 m133~m141 存档里落盘的毒区块（读入即自愈）
            forceChunks.add(new long[]{c, fc.getInt("m")});
            forceDims.add(fc.getString("d"));
        }
        chunkOwned = nbt.getBoolean("chunkOwned"); // m268 缺键=false（老档/新核心默认无所有权，force 首拍会重新判定并落盘）
    }

    /** m275：渲染子集读入（与 writeRenderNbt 严格对偶）——存档 readNbt 与客户端 applyRenderSnapshot 共用。 */
    private void readRenderNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        machineNodes.clear();
        bumpTopo(); // m179
        NbtList mn = nbt.getList("machineNodes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < mn.size(); i++) {
            NbtCompound mc = mn.getCompound(i);
            String mid = MERGED_IDS.get(mc.getString("id")); // m143：旧子机器id→合并机（不映射会整节点丢失，
            if (mid != null) mc.putString("id", mid);        // 且 inputBuf/nodeStatus 同序列表随之错位）
            ItemStack.fromNbt(lookup, mc).ifPresent(machineNodes::add);
        }
        connections.clear();
        int[] flat = nbt.getIntArray("connections");
        for (int i = 0; i + 1 < flat.length; i += 2) connections.add(new int[]{flat[i], flat[i + 1]});
        groupNames.clear(); // m191 分组元数据；键是 gid 的十进制串，坏键跳过不炸读档
        NbtCompound grp = nbt.getCompound("groups");
        for (String k : grp.getKeys()) {
            try { groupNames.put(Integer.parseInt(k), grp.getString(k)); } catch (NumberFormatException ignored) {}
        }
        nodeStatus.clear();
        for (int v : nbt.getIntArray("nodeStat")) nodeStatus.add(v);
        nodeReason.clear(); // m178
        NbtList nwl178 = nbt.getList("nodeWhy", NbtElement.STRING_TYPE);
        for (int i = 0; i < nwl178.size(); i++) nodeReason.add(nwl178.getString(i));
        storageEndpoints.clear();
        storageEndpointDims.clear();
        NbtList eps = nbt.getList("storEnds", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < eps.size(); i++) {
            NbtCompound c = eps.getCompound(i);
            storageEndpoints.add(new long[]{c.getLong("p"), c.getInt("k")});
            storageEndpointDims.add(c.getString("d"));
        }
        storageEdges.clear();
        storageEdgeDims.clear();
        NbtList seg = nbt.getList("storEdges", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < seg.size(); i++) {
            NbtCompound c = seg.getCompound(i);
            storageEdges.add(new long[]{c.getInt("m"), c.getLong("p"), c.getInt("r")});
            storageEdgeDims.add(c.getString("d"));
        }
        storageNodePos.clear();
        NbtCompound spn = nbt.getCompound("storNodePos");
        for (String k : spn.getKeys()) {
            int[] v = spn.getIntArray(k);
            if (v.length == 2 || v.length == 3) try { storageNodePos.put(Long.parseLong(k), v); } catch (NumberFormatException ignored) {} // m265 三元=画布放置(带标记位)，二元=遗留停靠
        }
        busTopIds.clear();
        busTopCounts.clear();
        NbtList btr = nbt.getList("busTop", NbtElement.COMPOUND_TYPE); // m85
        for (int i = 0; i < btr.size(); i++) {
            busTopIds.add(btr.getCompound(i).getString("i"));
            busTopCounts.add(btr.getCompound(i).getLong("n"));
        }
        prodPerMin = nbt.getLong("prodPM"); // m86
    }

    /** m275：客户端收到观众定向渲染快照时调用（SdzjzClient 收端）——只写渲染字段，
     *  存档专属字段（items/双缓存/强载等）客户端本就不消费不触碰。 */
    public void applyRenderSnapshot(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        readRenderNbt(nbt, lookup);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        // m276：区块追踪初始同步瘦身——路过玩家（含 vanilla BlockEntityUpdateS2CPacket.create 默认取数）
        // 只收渲染子集，不再收存档级全量（items/双缓存/强载/所有权客户端从不消费）；
        // 客户端 readNbt 对缺键全容忍：Inventories 缺键=空、双缓存空表、running/xpPool/chunkOwned 走默认。
        NbtCompound nbt = new NbtCompound();
        writeRenderNbt(nbt, lookup);
        return nbt;
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    // ================= GUI 工厂 =================
    @Override
    public Text getDisplayName() {
        return Text.translatable("container.sdzjz.structure_core");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        syncToClient(); // m181 开画布即时强刷：兜底改为"有人看才发"后，开屏首帧鲜度由这里兜住（服务端调用，客户端侧 syncToClient 自判跳过）
        return new StructureCoreScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
        return this.pos;
    }

    // ================= Inventory =================
    @Override public int size() { return SIZE; }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty();
        return r;
    }
    @Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }
    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
        markDirty();
    }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
    @Override public void clear() { items.clear(); }

    public void toggleRunning(boolean run) { this.running = run; markDirty(); }
    public boolean isRunning() { return running; }
}
