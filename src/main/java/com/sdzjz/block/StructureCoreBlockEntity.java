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
 * 产物进输出缓存，尝试推入正下方容器；缓存满则暂停（不掉落物实体）。
 */
public class StructureCoreBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory {

    public static final int MACHINE_START = 0, MACHINE_SLOTS = 8;
    public static final int UPGRADE_START = 8, UPGRADE_SLOTS = 3;
    public static final int OUTPUT_START = 11, OUTPUT_SLOTS = 8;
    public static final int SIZE = MACHINE_SLOTS + UPGRADE_SLOTS + OUTPUT_SLOTS; // 19

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private final java.util.List<ItemStack> machineNodes = new java.util.ArrayList<>();
    private final java.util.List<int[]> connections = new java.util.ArrayList<>(); // {from, to} 节点下标
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
    private long lastEndpointScan = Long.MIN_VALUE;
    private static final int ENDPOINT_CAP = 9; // 含常驻输出接口
    /** 常驻「输出接口」哨兵端点：连它=显式走默认自动路由（绑定>有线>无线>卫星>输出缓存）。 */
    public static final long OUTPUT_IFACE = Long.MIN_VALUE + 7;
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
        if (world.getTime() - be.lastEndpointScan >= 40) { // 端点扫描独立于开机状态：画布随时能看到接口
            be.lastEndpointScan = world.getTime();
            be.scanStorageEndpoints(world, pos);
        }
        if (!be.running) return;

        int tier = (state.getBlock() instanceof StructureCoreBlock scb) ? scb.tier : 1;
        SdzjzConfig cfg = SdzjzConfig.get();
        be.ticks++;
        int nSize = be.machineNodes.size();
        if (nSize == 0) return;

        // 连线拓扑（按边精确路由）：产物只流向出线指向的目标节点缓存；有入线的消耗机吃自己的缓存
        boolean[] hasOut = new boolean[nSize];
        boolean[] hasIn = new boolean[nSize];
        java.util.Map<Integer, java.util.List<Integer>> outT = new java.util.HashMap<>();
        for (int[] c : be.connections()) {
            if (c[0] >= 0 && c[0] < nSize && c[1] >= 0 && c[1] < nSize) {
                hasOut[c[0]] = true;
                hasIn[c[1]] = true;
                outT.computeIfAbsent(c[0], k -> new java.util.ArrayList<>()).add(c[1]);
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

            // 传感器闸门：该节点全部出线目标都关闸 → 整台暂停（不白产、不绕道塞存储）
            if (hasOut[i] && be.allGatesClosed(world, outT.get(i))) {
                be.stat(i, 2);
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
                int interval = Math.max(cfg.accelMinPeriodTicks, 40 - speedLv * 4);
                if (be.ticks % interval != 0) continue;
                CraftPlanner.Plan plan = CraftPlanner.plan(world, target);
                if (plan == null) continue; // 无合成配方
                int running = Math.min(st.getCount(), (4 + parallelLv * 4) * tier);
                long crafts = (long) running * (1 + countLv);
                int maxStack = Registries.ITEM.get(Identifier.of(target)).getMaxCount();
                crafts = Math.min(crafts, ((long) maxStack * OUTPUT_SLOTS) / Math.max(1, plan.resultCount())); // 按产物真实堆叠封顶再扣料（剑/图腾等max=1时防白扣）
                if (hasIn[i]) {
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.bufCountFor(i, en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.stat(i, 3); continue; }
                    for (var en : plan.needs().entrySet())
                        be.bufWithdrawFor(i, en.getKey(), (long) en.getValue() * crafts);
                } else {
                    StorageCoreBlockEntity supply = be.supplyFor(world, i);   // 存储→机器 定向供料连线优先
                    if (supply == null) {
                        if (!srcResolved) {
                            src = be.resolveInputSource(world, pos);
                            srcResolved = true;
                        }
                        supply = src;
                    }
                    if (supply == null) { be.stat(i, 3); continue; }
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, supply.count(en.getKey()) / en.getValue());
                    if (crafts <= 0) { be.stat(i, 3); continue; }
                    for (var en : plan.needs().entrySet())
                        supply.withdraw(en.getKey(), (int) ((long) en.getValue() * crafts));
                }
                be.stat(i, 1);
                int total = (int) (crafts * plan.resultCount());
                StorageCoreBlockEntity depositAc = hasOut[i] ? null : be.depositFor(world, i); // 机器→存储 定向产出连线
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
            } else if (st.getItem() instanceof com.sdzjz.item.CropFarmItem) {
                // 全自动农场：按所选作物产出（免费，对齐原版农场）
                String crop = craftTarget(st);
                java.util.List<MachineDef.Drop> cropDrops = com.sdzjz.machine.CropFarms.get(crop);
                if (cropDrops == null) { be.stat(i, 0); continue; } // 未选作物=待机
                int interval = Math.max(cfg.accelMinPeriodTicks, 40 - speedLv * 4);
                if (be.ticks % interval != 0) continue;
                int running = Math.min(st.getCount(), (4 + parallelLv * 4) * tier);
                be.stat(i, 1);
                StorageCoreBlockEntity depositCf = hasOut[i] ? null : be.depositFor(world, i);
                for (MachineDef.Drop d : cropDrops) {
                    if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                    int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                    if (amt <= 0) continue;
                    int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), total);
                    else if (depositCf != null) be.depositOrBuffer(depositCf, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                    produced = true;
                }
            } else if (st.getItem() instanceof MachineItem miu && "super_smelter".equals(miu.def().id())) {
                // 万能熔炉：接什么烧什么（原版熔炼配方表）。有入线吃内部缓存，否则吃定向供料/存储网络。
                int interval = Math.max(cfg.accelMinPeriodTicks, miu.def().baseIntervalTicks() - speedLv * 4);
                if (be.ticks % interval != 0) continue;
                int running = Math.min(st.getCount(), (4 + parallelLv * 4) * tier);
                StorageCoreBlockEntity depositSm = hasOut[i] ? null : be.depositFor(world, i);
                long capacity = (long) running * 64L * (1 + countLv); // 每周期一组×并行×(1+数量)
                if (!hasOut[i] && depositSm == null) capacity = Math.min(capacity, 64L * OUTPUT_SLOTS); // 无存储时按缓存封顶防白扣
                long done = 0;
                if (hasIn[i]) {
                    java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(be.nodeBuf(i).keySet());
                    keys.addAll(be.internalBuffer.keySet());
                    for (String id : keys) {
                        if (done >= capacity) break;
                        Object[] out = com.sdzjz.machine.SmeltPlanner.resultOf(world, id);
                        if (out == null) continue;
                        long take = Math.min(be.bufCountFor(i, id), capacity - done);
                        if (take <= 0) continue;
                        be.bufWithdrawFor(i, id, take);
                        long give = take * (int) out[1];
                        if (hasOut[i]) be.distribute(world, i, outT.get(i), (String) out[0], give);
                        else if (depositSm != null) be.depositOrBuffer(depositSm, new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, Integer.MAX_VALUE)));
                        else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, 64L * OUTPUT_SLOTS)));
                        done += take;
                    }
                } else {
                    // 万能熔炉必须显式接线（机器入线 或 存储→机器 定向供料线）才取料：
                    // 不做全局网络兜底，防止把玩家存着的原木/圆石/粗矿悄悄全烧了。
                    StorageCoreBlockEntity supply = be.supplyFor(world, i);
                    if (supply == null) { be.stat(i, 3); continue; }
                    for (var en : new java.util.ArrayList<>(supply.storeView().entrySet())) {
                        if (done >= capacity) break;
                        Object[] out = com.sdzjz.machine.SmeltPlanner.resultOf(world, en.getKey());
                        if (out == null) continue;
                        long take = Math.min(en.getValue(), capacity - done);
                        if (take <= 0) continue;
                        int got = supply.withdraw(en.getKey(), (int) Math.min(take, Integer.MAX_VALUE));
                        if (got <= 0) continue;
                        long give = (long) got * (int) out[1];
                        if (hasOut[i]) be.distribute(world, i, outT.get(i), (String) out[0], give);
                        else if (depositSm != null) be.depositOrBuffer(depositSm, new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, Integer.MAX_VALUE)));
                        else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of((String) out[0])), (int) Math.min(give, 64L * OUTPUT_SLOTS)));
                        done += got;
                    }
                }
                if (done > 0) {
                    be.xpPool += 0.1 * done; // 熔炼经验：0.1/件（近似原版均值，DEVLOG 有记）
                    produced = true;
                    be.stat(i, 1);
                } else {
                    be.stat(i, 3); // 本周期没料可烧
                }
            } else if (st.getItem() instanceof MachineItem mi) {
                MachineDef def = mi.def();
                int interval = Math.max(cfg.accelMinPeriodTicks, def.baseIntervalTicks() - speedLv * 4);
                if (be.ticks % interval != 0) continue;
                int running = Math.min(st.getCount(), (4 + parallelLv * 4) * tier);

                if (def.consumesInputs()) {
                    if (hasIn[i]) {
                        // 从内部缓存取料（连线喂料）
                        boolean ok = true;
                        for (MachineDef.Input in : def.inputs())
                            if (be.bufCountFor(i, in.item()) < (long) in.count() * running) { ok = false; break; }
                        if (!ok) { be.stat(i, 3); continue; }
                        for (MachineDef.Input in : def.inputs()) be.bufWithdrawFor(i, in.item(), (long) in.count() * running);
                    } else {
                        StorageCoreBlockEntity supply = be.supplyFor(world, i); // 存储→机器 定向供料连线优先
                        if (supply == null) {
                            if (!srcResolved) {
                                src = be.resolveInputSource(world, pos);
                                srcResolved = true;
                            }
                            supply = src;
                        }
                        if (supply == null) { be.stat(i, 3); continue; }
                        boolean ok = true;
                        for (MachineDef.Input in : def.inputs())
                            if (supply.count(in.item()) < (long) in.count() * running) { ok = false; break; }
                        if (!ok) { be.stat(i, 3); continue; }
                        for (MachineDef.Input in : def.inputs()) supply.withdraw(in.item(), in.count() * running);
                    }
                }
                be.stat(i, 1);

                StorageCoreBlockEntity depositMi = hasOut[i] ? null : be.depositFor(world, i);
                for (MachineDef.Drop d : def.outputs()) {
                    if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                    int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                    if (amt <= 0) continue;
                    int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), total);
                    else if (depositMi != null) be.depositOrBuffer(depositMi, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                    produced = true;
                }
                double mxp = MachineXp.of(def.id());
                if (mxp > 0) { be.xpPool += mxp * running; produced = true; }
            } else if (st.getItem() instanceof CaptureCageItem && CaptureCageItem.isCaged(st)) {
                String mob = CaptureCageItem.cagedType(st);
                java.util.List<MachineDef.Drop> drops = (mob == null) ? null : MobDrops.get(mob);
                if (drops == null) continue;
                int interval = Math.max(cfg.accelMinPeriodTicks, 30 - speedLv * 4);
                if (be.ticks % interval != 0) continue;
                int running = Math.min(st.getCount(), (4 + parallelLv * 4) * tier);
                be.stat(i, 1);
                StorageCoreBlockEntity depositCg = hasOut[i] ? null : be.depositFor(world, i);
                for (MachineDef.Drop d : drops) {
                    if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                    int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                    if (amt <= 0) continue;
                    int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                    if (hasOut[i]) be.distribute(world, i, outT.get(i), d.item(), total);
                    else if (depositCg != null) be.depositOrBuffer(depositCg, new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                    produced = true;
                }
                double cxp = MachineXp.mob(mob);
                if (cxp > 0) { be.xpPool += cxp * running; produced = true; }
            }
        }
        if (produced) {
            be.pushOutput(world, pos);
            be.markDirty();
        }
        if (be.statusDirty && be.ticks % 20 == 0) { // 状态灯：有变化才同步，最多 1 次/秒
            be.statusDirty = false;
            be.markDirty();
            be.syncToClient();
        }
    }

    /** 右键把机器/笼子作为一个节点加入画布（无数量上限）。 */
    /** 右键把机器/笼子作为一个节点加入画布（无上限）；首次自动布局位置。 */
    public boolean insertMachine(ItemStack held) {
        if (held.isEmpty()) return false;
        ItemStack node = held.copy();
        NbtCompound n = node.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (!n.contains("nx")) {
            int i = machineNodes.size(), cols = 6;
            n.putInt("nx", 20 + (i % cols) * 112);
            n.putInt("ny", 20 + (i / cols) * 88);
            node.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        }
        machineNodes.add(node);
        nodeBuf(machineNodes.size() - 1); // 懒补齐：新节点空输入缓存
        held.setCount(0);
        markDirty();
        syncToClient();
        return true;
    }

    /** 读节点各类升级等级。 */
    public int nodeSpeed(ItemStack s) { return nodeInt(s, "spd"); }
    public int nodeCount(ItemStack s) { return nodeInt(s, "cnt"); }
    public int nodePar(ItemStack s)   { return nodeInt(s, "par"); }

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
    public static String craftTarget(ItemStack s) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return n.contains("ct") ? n.getString("ct") : "";
    }

    // ===== 画布逻辑节点：过滤器 / 数量传感器 =====
    private static NbtCompound nbtOf(ItemStack s) {
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    public static boolean isFilter(ItemStack s) { return s.isOf(ModItems.FILTER_NODE); }
    public static boolean isSensor(ItemStack s) { return s.isOf(ModItems.SENSOR_NODE); }
    public static boolean isSwitch(ItemStack s) { return s.isOf(ModItems.SWITCH_NODE); }
    public static boolean isDistributor(ItemStack s) { return s.isOf(ModItems.DISTRIBUTOR_NODE); }

    /** 开关节点状态：默认=开；NBT "so"=false 时为关。 */
    public static boolean switchOn(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return !n.contains("so") || n.getBoolean("so");
    }

    /** 切换开关节点 开/关。 */
    public void toggleSwitch(int index) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if (!isSwitch(s)) return;
        NbtCompound n = nbtOf(s);
        n.putBoolean("so", !switchOn(s));
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    /** 过滤模式：false=白名单（默认，只放行名单内），true=黑名单（拦下名单内）。 */
    public static boolean filterBlacklist(ItemStack s) { return nbtOf(s).getBoolean("fb"); }

    public static java.util.List<String> filterList(ItemStack s) {
        NbtList l = nbtOf(s).getList("fl", NbtElement.STRING_TYPE);
        java.util.List<String> out = new java.util.ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static boolean filterPasses(ItemStack s, String id) {
        boolean in = false;
        NbtList l = nbtOf(s).getList("fl", NbtElement.STRING_TYPE);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) { in = true; break; }
        return filterBlacklist(s) ? !in : in;
    }

    public static String sensorItem(ItemStack s) { return nbtOf(s).getString("si"); }

    public static long sensorThreshold(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return n.contains("sv") ? Math.max(0, n.getLong("sv")) : 10000L;
    }

    /** 传感器方向：true=「低于阈值放行」(默认，补货型)；false=「高于阈值放行」(溢出型)。 */
    public static boolean sensorLess(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return !n.contains("sl") || n.getBoolean("sl");
    }

    /** 加/移一条过滤名单项（已在名单=移除）；id 为空串=切换 白名单↔黑名单。 */
    public void toggleFilterEntry(int index, String id) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if (!isFilter(s)) return;
        NbtCompound n = nbtOf(s);
        if (id == null || id.isEmpty()) {
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
        if (!isSensor(s)) return;
        NbtCompound n = nbtOf(s);
        if (id != null && !id.isEmpty()) n.putString("si", id);
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
        StorageCoreBlockEntity sc = supplyFor(world, i);
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
            boolean closedGate = (isSensor(ts) && !sensorOpen(world, t)) || (isSwitch(ts) && !switchOn(ts));
            if (!closedGate) return false;
        }
        return true;
    }

    // ===== 节点状态灯：0=待机 1=正常(绿) 2=阻塞/关闸(黄) 3=缺料(红)。每 20t 有变化才同步 =====
    private final java.util.List<Integer> nodeStatus = new java.util.ArrayList<>();
    private boolean statusDirty = false;

    void stat(int i, int v) {
        while (nodeStatus.size() < machineNodes.size()) nodeStatus.add(0);
        if (i < 0 || i >= nodeStatus.size() || nodeStatus.get(i) == v) return;
        nodeStatus.set(i, v);
        statusDirty = true;
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
        if (!(s.getItem() instanceof AutoCrafterItem) && !cropOk) return;
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putString("ct", id);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
    }

    public boolean addNodeUpgrade(PlayerEntity player, int index, int type) {
        if (index < 0 || index >= machineNodes.size()) return false;
        Item item = upgradeItem(type);
        String key = upgradeKey(type);
        if (item == null || !consumeFromInv(player, item)) return false;
        ItemStack s = machineNodes.get(index);
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putInt(key, n.getInt(key) + 1);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        markDirty();
        syncToClient();
        return true;
    }

    /** 从该节点取回一个升级还给玩家。 */
    public boolean removeNodeUpgrade(PlayerEntity player, int index, int type) {
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
        markDirty();
        syncToClient();
        return true;
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
        n.putInt("nx", nx);
        n.putInt("ny", ny);
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
        nodeBuf(machineNodes.size() - 1); // 先补齐对齐
        ItemStack s = machineNodes.remove(index);
        if (index < nodeBufs.size()) mergeLegacy(nodeBufs.remove(index)); // 在途物品回遗留池，不丢
        if (index < nodeStatus.size()) nodeStatus.remove(index);
        returnNodeClean(player, s);
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
        markDirty();
        syncToClient();
    }

    /** 归还节点：先把嵌在 NBT 里的升级折成升级物品还给玩家，再清掉画布数据返还机器本体。 */
    private void returnNodeClean(PlayerEntity player, ItemStack s) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        for (int type = 0; type < 3; type++) {
            int lv = n.getInt(upgradeKey(type));
            Item item = upgradeItem(type);
            while (lv-- > 0 && item != null) {
                ItemStack up = new ItemStack(item);
                if (!player.getInventory().insertStack(up)) player.dropItem(up, false);
            }
        }
        s.remove(DataComponentTypes.CUSTOM_DATA);
        if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
    }

    /** 潜行空手右键：先弹出最后一个机器节点，其次弹升级。 */
    public void ejectOne(PlayerEntity player) {
        if (!machineNodes.isEmpty()) {
            int removed = machineNodes.size() - 1;
            nodeBuf(removed); // 补齐对齐
            ItemStack s = machineNodes.remove(removed);
            if (removed < nodeBufs.size()) mergeLegacy(nodeBufs.remove(removed));
            if (removed < nodeStatus.size()) nodeStatus.remove(removed);
            connections.removeIf(c -> c[0] == removed || c[1] == removed);
            for (int i = storageEdges.size() - 1; i >= 0; i--) {
                if (storageEdges.get(i)[0] == removed) { storageEdges.remove(i); storageEdgeDims.remove(i); }
            }
            returnNodeClean(player, s);
            markDirty();
            syncToClient();
            return;
        }
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) if (pop(player, i)) return;
    }

    /** 画布连线读取（客户端渲染）。 */
    public java.util.List<int[]> connections() { return connections; }

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
            for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(world))) {
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
            for (long[] v : found.values()) {
                storageEndpoints.add(v);
                storageEndpointDims.add(dims.get(v[0]));
            }
            storageNodePos.keySet().retainAll(found.keySet()); // 修剪已消失端点的画布坐标
            markDirty();
            syncToClient();
        }
    }

    /** 该机器的定向产出目标（机器→存储 连线；不可用则 null 走默认路由）。 */
    private StorageCoreBlockEntity depositFor(World world, int machineIndex) {
        return edgeStorage(world, machineIndex, 0);
    }

    /** 该机器的定向供料源（存储→机器 连线）。 */
    private StorageCoreBlockEntity supplyFor(World world, int machineIndex) {
        return edgeStorage(world, machineIndex, 1);
    }

    private StorageCoreBlockEntity edgeStorage(World world, int machineIndex, int dir) {
        for (int i = 0; i < storageEdges.size(); i++) {
            long[] e = storageEdges.get(i);
            if (e[0] != machineIndex || e[2] != dir) continue;
            StorageCoreBlockEntity sc = resolveStorageAt(world, storageEdgeDims.get(i), e[1]);
            if (sc != null) return sc;
        }
        return null;
    }

    private StorageCoreBlockEntity resolveStorageAt(World world, String dim, long posLong) {
        if (posLong == OUTPUT_IFACE) return null; // 输出接口=默认自动路由，无实体存储
        BlockPos p = BlockPos.fromLong(posLong);
        String self = world.getRegistryKey().getValue().toString();
        if (dim == null || dim.isEmpty() || self.equals(dim)) { // 空维度串按本维度处理（老数据兜底）
            if (!world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return null;
            return world.getBlockEntity(p) instanceof StorageCoreBlockEntity sc ? sc : null;
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
            return ow.getBlockEntity(p) instanceof StorageCoreBlockEntity sc ? sc : null;
        }
        return null;
    }

    /** 连/断一条 机器↔存储 定向连线（已存在则断开）。dir 0=产出到该存储 1=从该存储供料。 */
    public void toggleStorageEdge(int machineIndex, long storagePos, int dir, String dim) {
        if (machineIndex < 0 || machineIndex >= machineNodes.size() || dir < 0 || dir > 1) return;
        boolean known = false; // 只允许连到画布上确实显示的端点，防伪造包连任意坐标
        String epDim = null;
        for (int k = 0; k < storageEndpoints.size(); k++) {
            long[] ep = storageEndpoints.get(k);
            if (ep[0] == storagePos && ep[1] != 5) { known = true; epDim = storageEndpointDims.get(k); break; }
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
    public void setStorageNodePos(long storagePos, int nx, int ny) {
        storageNodePos.put(storagePos, new int[]{nx, ny});
        markDirty();
        syncToClient();
    }

    public java.util.List<long[]> storageEndpointsView() { return storageEndpoints; }
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

    /** 节点可用量 = 自己的输入缓存 + 遗留共享池（老档迁移兜底）。 */
    private long bufCountFor(int i, String id) {
        return nodeBuf(i).getOrDefault(id, 0L) + internalBuffer.getOrDefault(id, 0L);
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
                    long cur = m.getOrDefault(id, 0L);
                    long put = Math.min(Math.max(0, BUF_CAP - cur), want);
                    if (put > 0) m.put(id, cur + put);
                    undelivered += want - put;
                }
                amt = undelivered;
            }
        }
        if (amt <= 0) return;
        StorageCoreBlockEntity dep = depositFor(world, fromIndex);
        ItemStack rest = new ItemStack(Registries.ITEM.get(Identifier.of(id)), (int) Math.min(amt, 64L * OUTPUT_SLOTS));
        if (dep != null) depositOrBuffer(dep, rest);
        else addOutput(rest);
    }

    /** 按需分发：只把目标机器"吃得下"的物品送下线；没人要的部分走 定向存储/默认路由——绝不堵死在下游缓存里。 */
    private void distribute(World world, int fromIndex, java.util.List<Integer> targets, String id, long amt) {
        if (targets != null) {
            for (int t : targets) {
                if (amt <= 0) return;
                if (t < 0 || t >= machineNodes.size() || !accepts(world, t, id)) continue;
                java.util.Map<String, Long> m = nodeBuf(t);
                long cur = m.getOrDefault(id, 0L);
                long put = Math.min(Math.max(0, BUF_CAP - cur), amt);
                if (put > 0) { m.put(id, cur + put); amt -= put; }
            }
        }
        if (amt <= 0) return;
        StorageCoreBlockEntity dep = depositFor(world, fromIndex); // 剩余按定向存储→默认路由
        ItemStack rest = new ItemStack(Registries.ITEM.get(Identifier.of(id)), (int) Math.min(amt, 64L * OUTPUT_SLOTS));
        if (dep != null) depositOrBuffer(dep, rest);
        else addOutput(rest);
    }

    /** 目标机器是否"吃"该物品：万能熔炉=可熔炼物；消耗机=配方输入；自动合成机=当前目标用料；农场=不吃。 */
    private boolean accepts(World world, int target, String id) {
        ItemStack st = machineNodes.get(target);
        if (isFilter(st)) return filterPasses(st, id);   // 拦下的留在上游走默认路由→存储
        if (isSensor(st)) return sensorOpen(world, target); // 关闸不收（上游全关闸时整台暂停）
        if (isSwitch(st)) return switchOn(st);              // 关闸不收，同上
        if (isDistributor(st)) return true;                 // 分配器什么都收（分不出去的走默认路由）
        if (st.getItem() instanceof AutoCrafterItem) {
            String tgt = craftTarget(st);
            if (tgt.isEmpty()) return false;
            var plan = CraftPlanner.plan(world, tgt);
            return plan != null && plan.needs().containsKey(id);
        }
        if (st.getItem() instanceof MachineItem mi) {
            if ("super_smelter".equals(mi.def().id())) return com.sdzjz.machine.SmeltPlanner.resultOf(world, id) != null;
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
        long sum = internalBuffer.getOrDefault(id, 0L) + amt;
        if (sum > BUF_CAP) {
            long spill = sum - BUF_CAP;
            internalBuffer.put(id, BUF_CAP);
            addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(id)), (int) Math.min(spill, 64L * OUTPUT_SLOTS)));
        } else {
            internalBuffer.put(id, sum);
        }
    }

    /** 连/断一条 from→to 连线（已存在则断开）。 */
    public void toggleConnection(int from, int to) {        if (from == to || from < 0 || to < 0 || from >= machineNodes.size() || to >= machineNodes.size()) return;
        for (int i = 0; i < connections.size(); i++) {
            int[] c = connections.get(i);
            if (c[0] == from && c[1] == to) { connections.remove(i); markDirty(); syncToClient(); return; }
        }
        connections.add(new int[]{from, to});
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
        nodeStatus.clear();
    }

    private void syncToClient() {
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
        }
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
    private void depositOrBuffer(StorageCoreBlockEntity sc, ItemStack stack) {
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
                items.set(i, new ItemStack(out.getItem(), put));
                remain -= put;
            } else if (slot.isOf(out.getItem()) && slot.getCount() < slot.getMaxCount()) {
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

    /** 解析输出目标（存储核心或普通容器），带缓存。仅缓存同维度目标。 */
    private Object resolveOutTarget(World world, BlockPos corePos) {
        long now = world.getTime();
        if (cachedOutPos != null && now < cachedOutUntil
                && world.getChunkManager().isChunkLoaded(cachedOutPos.getX() >> 4, cachedOutPos.getZ() >> 4)) {
            BlockEntity be = world.getBlockEntity(cachedOutPos);
            if (be instanceof StorageCoreBlockEntity sc) return sc;
            if (be instanceof Inventory inv && !(be instanceof StructureCoreBlockEntity)) return inv;
        }
        cachedOutPos = null;
        Object target = boundPanel(world, corePos);
        if (target == null) target = findTarget(world, corePos);
        if (target == null && hasWirelessNode(world, corePos)) target = nearestWirelessPanel(world, corePos);
        if (target == null && hasSatelliteNode(world, corePos)) target = findSatellitePanel(world, corePos);
        if (target instanceof BlockEntity tbe && tbe.getWorld() == world) {
            cachedOutPos = tbe.getPos().toImmutable();
            cachedOutUntil = now + 40;
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
        for (BlockPos p : java.util.List.copyOf(StorageCoreBlockEntity.coresIn(world))) {
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
        Inventories.writeNbt(nbt, items, lookup);
        NbtList mn = new NbtList();
        for (ItemStack s : machineNodes) if (!s.isEmpty()) mn.add(s.encode(lookup));
        nbt.put("machineNodes", mn);
        int[] flat = new int[connections.size() * 2];
        for (int i = 0; i < connections.size(); i++) { flat[i * 2] = connections.get(i)[0]; flat[i * 2 + 1] = connections.get(i)[1]; }
        nbt.putIntArray("connections", flat);
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
        int[] nst = new int[machineNodes.size()];
        for (int i = 0; i < nst.length; i++) nst[i] = i < nodeStatus.size() ? nodeStatus.get(i) : 0;
        nbt.putIntArray("nodeStat", nst);
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
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        machineNodes.clear();
        NbtList mn = nbt.getList("machineNodes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < mn.size(); i++) ItemStack.fromNbt(lookup, mn.getCompound(i)).ifPresent(machineNodes::add);
        connections.clear();
        int[] flat = nbt.getIntArray("connections");
        for (int i = 0; i + 1 < flat.length; i += 2) connections.add(new int[]{flat[i], flat[i + 1]});
        internalBuffer.clear();
        NbtCompound buf = nbt.getCompound("internalBuffer");
        for (String k : buf.getKeys()) internalBuffer.put(k, buf.getLong(k));
        nodeBufs.clear();
        NbtList nbl = nbt.getList("nodeBufs", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < machineNodes.size(); i++) {
            java.util.Map<String, Long> m = new java.util.HashMap<>();
            if (i < nbl.size()) {
                NbtCompound c = nbl.getCompound(i);
                for (String k : c.getKeys()) m.put(k, c.getLong(k));
            }
            nodeBufs.add(m); // 老档无此键=全空缓存；共享池留在 internalBuffer 里继续被消耗（无损迁移）
        }
        if (nbt.contains("boundPos")) {
            boundPanelPos = BlockPos.fromLong(nbt.getLong("boundPos"));
            boundPanelDim = nbt.getString("boundDim");
        } else {
            boundPanelPos = null; boundPanelDim = null;
        }
        running = nbt.getBoolean("running");
        xpPool = nbt.getDouble("xpPool");
        nodeStatus.clear();
        for (int v : nbt.getIntArray("nodeStat")) nodeStatus.add(v);
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
            if (v.length == 2) try { storageNodePos.put(Long.parseLong(k), v); } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, lookup);
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
