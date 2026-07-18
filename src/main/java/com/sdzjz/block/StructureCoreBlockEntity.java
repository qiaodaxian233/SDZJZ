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
    private final java.util.Map<String, Long> internalBuffer = new java.util.HashMap<>(); // 连线内部物流缓存 id→量
    private static final long BUF_CAP = 200000L;
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
                case 6 -> (int) Math.min(xpPool, Integer.MAX_VALUE);
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { if (i == 0) running = (v != 0); }
        @Override public int size() { return 7; }
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
        if (world.isClient || !be.running) return;

        int tier = (state.getBlock() instanceof StructureCoreBlock scb) ? scb.tier : 1;
        SdzjzConfig cfg = SdzjzConfig.get();
        be.ticks++;
        int nSize = be.machineNodes.size();
        if (nSize == 0) return;

        // 连线拓扑（粗粒度）：有出线的节点产物入内部缓存；有入线的消耗机从内部缓存取料
        boolean[] hasOut = new boolean[nSize];
        boolean[] hasIn = new boolean[nSize];
        for (int[] c : be.connections()) {
            if (c[0] >= 0 && c[0] < nSize) hasOut[c[0]] = true;
            if (c[1] >= 0 && c[1] < nSize) hasIn[c[1]] = true;
        }

        boolean produced = false;
        StorageCoreBlockEntity src = null;
        boolean srcResolved = false;

        for (int i = 0; i < nSize; i++) {
            ItemStack st = be.machineNodes.get(i);
            int speedLv = be.nodeSpeed(st);
            int countLv = be.nodeCount(st);
            int parallelLv = be.nodePar(st);

            if (st.getItem() instanceof AutoCrafterItem) {
                // 自动合成机：按原版配方吃料出货。目标在画布上设置（节点徽章）。
                String target = craftTarget(st);
                if (target.isEmpty()) continue;
                int interval = Math.max(cfg.accelMinPeriodTicks, 40 - speedLv * 4);
                if (be.ticks % interval != 0) continue;
                CraftPlanner.Plan plan = CraftPlanner.plan(world, target);
                if (plan == null) continue; // 无合成配方
                int running = Math.min(st.getCount(), (4 + parallelLv * 4) * tier);
                long crafts = (long) running * (1 + countLv);
                crafts = Math.min(crafts, (64L * OUTPUT_SLOTS) / Math.max(1, plan.resultCount())); // 先封顶再扣料，防白扣
                if (hasIn[i]) {
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, be.bufCount(en.getKey()) / en.getValue());
                    if (crafts <= 0) continue;
                    for (var en : plan.needs().entrySet())
                        be.bufWithdraw(en.getKey(), (long) en.getValue() * crafts);
                } else {
                    if (!srcResolved) {
                        src = be.resolveInputSource(world, pos);
                        srcResolved = true;
                    }
                    if (src == null) continue;
                    for (var en : plan.needs().entrySet())
                        crafts = Math.min(crafts, src.count(en.getKey()) / en.getValue());
                    if (crafts <= 0) continue;
                    for (var en : plan.needs().entrySet())
                        src.withdraw(en.getKey(), (int) ((long) en.getValue() * crafts));
                }
                int total = (int) (crafts * plan.resultCount());
                if (hasOut[i]) be.bufAdd(target, total);
                else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(target)), total));
                for (var en : plan.remainders().entrySet()) { // 容器残留（桶等）返还
                    int rc = (int) Math.min(64L * OUTPUT_SLOTS, (long) en.getValue() * crafts);
                    if (hasOut[i]) be.bufAdd(en.getKey(), rc);
                    else be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(en.getKey())), rc));
                }
                produced = true;
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
                            if (be.bufCount(in.item()) < (long) in.count() * running) { ok = false; break; }
                        if (!ok) continue;
                        for (MachineDef.Input in : def.inputs()) be.bufWithdraw(in.item(), (long) in.count() * running);
                    } else {
                        if (!srcResolved) {
                            src = be.resolveInputSource(world, pos);
                            srcResolved = true;
                        }
                        if (src == null) continue;
                        boolean ok = true;
                        for (MachineDef.Input in : def.inputs())
                            if (src.count(in.item()) < (long) in.count() * running) { ok = false; break; }
                        if (!ok) continue;
                        for (MachineDef.Input in : def.inputs()) src.withdraw(in.item(), in.count() * running);
                    }
                }

                for (MachineDef.Drop d : def.outputs()) {
                    if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                    int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                    if (amt <= 0) continue;
                    int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                    if (hasOut[i]) be.bufAdd(d.item(), total);
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
                for (MachineDef.Drop d : drops) {
                    if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                    int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                    if (amt <= 0) continue;
                    int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                    if (hasOut[i]) be.bufAdd(d.item(), total);
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
        markDirty();
    }

    /** 读取自动合成机节点的目标产物 id（无则空串）。 */
    public static String craftTarget(ItemStack s) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return n.contains("ct") ? n.getString("ct") : "";
    }

    /** 设置自动合成机节点的目标产物（画布徽章点选，走 NodeTargetPayload）。 */
    public void setNodeTarget(int index, String id) {
        if (index < 0 || index >= machineNodes.size()) return;
        ItemStack s = machineNodes.get(index);
        if (!(s.getItem() instanceof AutoCrafterItem)) return;
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
        ItemStack s = machineNodes.remove(index);
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
            ItemStack s = machineNodes.remove(removed);
            connections.removeIf(c -> c[0] == removed || c[1] == removed);
            returnNodeClean(player, s);
            markDirty();
            syncToClient();
            return;
        }
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) if (pop(player, i)) return;
    }

    /** 画布连线读取（客户端渲染）。 */
    public java.util.List<int[]> connections() { return connections; }

    // ===== 连线内部物流缓存 =====
    private long bufCount(String id) { return internalBuffer.getOrDefault(id, 0L); }

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
        if (boundPanelPos != null && boundPanelDim != null) {
            nbt.putLong("boundPos", boundPanelPos.asLong());
            nbt.putString("boundDim", boundPanelDim);
        }
        nbt.putBoolean("running", running);
        nbt.putDouble("xpPool", xpPool);
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
        if (nbt.contains("boundPos")) {
            boundPanelPos = BlockPos.fromLong(nbt.getLong("boundPos"));
            boundPanelDim = nbt.getString("boundDim");
        } else {
            boundPanelPos = null; boundPanelDim = null;
        }
        running = nbt.getBoolean("running");
        xpPool = nbt.getDouble("xpPool");
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
