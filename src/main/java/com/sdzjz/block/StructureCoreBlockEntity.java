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
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.machine.MachineDef;
import com.sdzjz.machine.MobDrops;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
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
    private BlockPos boundPanelPos;
    private String boundPanelDim;
    public boolean running = false;
    private long ticks = 0;

    /** GUI 状态同步：0=运行 1=机器数 2=tier 3=速度Lv 4=数量Lv 5=并发Lv。 */
    public final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> running ? 1 : 0;
                case 1 -> machineCount();
                case 2 -> tierOf();
                case 3 -> countUpgrade(ModItems.SPEED_UPGRADE);
                case 4 -> countUpgrade(ModItems.COUNT_UPGRADE);
                case 5 -> countUpgrade(ModItems.PARALLEL_UPGRADE);
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { if (i == 0) running = (v != 0); }
        @Override public int size() { return 6; }
    };

    private int machineCount() {
        int n = 0;
        for (int i = MACHINE_START; i < MACHINE_START + MACHINE_SLOTS; i++) {
            if (items.get(i).getItem() instanceof MachineItem
                    || items.get(i).getItem() instanceof CaptureCageItem) n += items.get(i).getCount();
        }
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
        int speedLv = be.countUpgrade(ModItems.SPEED_UPGRADE);
        int countLv = be.countUpgrade(ModItems.COUNT_UPGRADE);
        int parallelLv = be.countUpgrade(ModItems.PARALLEL_UPGRADE);
        SdzjzConfig cfg = SdzjzConfig.get();

        be.ticks++;

        // 按机器类型分组，统计每种装了几台
        Map<MachineDef, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> cages = new LinkedHashMap<>();
        for (int i = MACHINE_START; i < MACHINE_START + MACHINE_SLOTS; i++) {
            ItemStack st = be.items.get(i);
            if (st.getItem() instanceof MachineItem mi) {
                counts.merge(mi.def(), st.getCount(), Integer::sum);
            } else if (st.getItem() instanceof CaptureCageItem && CaptureCageItem.isCaged(st)) {
                String mob = CaptureCageItem.cagedType(st);
                if (mob != null) cages.merge(mob, st.getCount(), Integer::sum);
            }
        }
        if (counts.isEmpty() && cages.isEmpty()) return;

        boolean produced = false;
        for (Map.Entry<MachineDef, Integer> e : counts.entrySet()) {
            MachineDef def = e.getKey();
            int interval = Math.max(cfg.accelMinPeriodTicks, def.baseIntervalTicks() - speedLv * 4);
            if (be.ticks % interval != 0) continue;
            int parallelCap = (4 + parallelLv * 4) * tier;
            int running = Math.min(e.getValue(), parallelCap);

            if (def.consumesInputs()) {
                // 消耗类：从连接的数据面板取料，产物入缓存
                DataPanelBlockEntity src = be.boundPanel(world, pos);
                if (src == null) src = be.findPanel(world, pos);
                if (src == null && be.hasWirelessNode(world, pos)) src = be.nearestWirelessPanel(world, pos);
                if (src == null && be.hasSatelliteNode(world, pos)) src = be.findSatellitePanel(world, pos);
                if (src == null) continue;
                boolean ok = true;
                for (MachineDef.Input in : def.inputs())
                    if (src.count(in.item()) < (long) in.count() * running) { ok = false; break; }
                if (!ok) continue;
                for (MachineDef.Input in : def.inputs()) src.withdraw(in.item(), in.count() * running);
            }

            for (MachineDef.Drop d : def.outputs()) {
                if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                if (amt <= 0) continue;
                int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                Item product = Registries.ITEM.get(Identifier.of(d.item()));
                be.addOutput(new ItemStack(product, total));
                produced = true;
            }
        }
        // 抓物笼子：按笼中生物掉落表产出（自由产出，30t 基础周期，同样吃升级）
        for (Map.Entry<String, Integer> e : cages.entrySet()) {
            java.util.List<MachineDef.Drop> drops = MobDrops.get(e.getKey());
            if (drops == null) continue;
            int interval = Math.max(cfg.accelMinPeriodTicks, 30 - speedLv * 4);
            if (be.ticks % interval != 0) continue;
            int running = Math.min(e.getValue(), (4 + parallelLv * 4) * tier);
            for (MachineDef.Drop d : drops) {
                if (d.chance() < 1f && world.getRandom().nextFloat() >= d.chance()) continue;
                int amt = d.min() + (d.max() > d.min() ? world.getRandom().nextInt(d.max() - d.min() + 1) : 0);
                if (amt <= 0) continue;
                int total = Math.min(running * (amt + countLv * 8) * tier, 64 * OUTPUT_SLOTS);
                be.addOutput(new ItemStack(Registries.ITEM.get(Identifier.of(d.item())), total));
                produced = true;
            }
        }
        if (produced) {
            be.pushOutput(world, pos);
            be.markDirty();
        }
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

    /** 把输出缓存推入正下方容器。 */
    /** 把输出缓存送到：相邻的数据面板/箱子，或顺着数据线 BFS 连到的存储。 */
    private void pushOutput(World world, BlockPos corePos) {
        Object target = boundPanel(world, corePos);
        if (target == null) target = findTarget(world, corePos);
        if (target == null && hasWirelessNode(world, corePos)) target = nearestWirelessPanel(world, corePos);
        if (target == null && hasSatelliteNode(world, corePos)) target = findSatellitePanel(world, corePos);
        if (target == null) return;
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) continue;
            if (target instanceof DataPanelBlockEntity panel) panel.deposit(slot);
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
                if (be instanceof DataPanelBlockEntity panel) return panel;
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
    private DataPanelBlockEntity nearestWirelessPanel(World world, BlockPos corePos) {
        long range = SdzjzConfig.get().wirelessRange;
        long r2 = range * range, best = Long.MAX_VALUE;
        DataPanelBlockEntity found = null;
        for (BlockPos p : DataPanelBlockEntity.panelsIn(world)) {
            long dx = p.getX() - corePos.getX(), dy = p.getY() - corePos.getY(), dz = p.getZ() - corePos.getZ();
            long d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > r2 || d2 >= best) continue;
            if (world.getBlockEntity(p) instanceof DataPanelBlockEntity panel) {
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
    private DataPanelBlockEntity boundPanel(World world, BlockPos corePos) {
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
            return world.getBlockEntity(boundPanelPos) instanceof DataPanelBlockEntity p ? p : null;
        }
        if (!hasSatelliteNode(world, corePos)) return null;
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.server.world.ServerWorld ow = sw.getServer().getWorld(dimKey);
            if (ow != null && ow.getBlockEntity(boundPanelPos) instanceof DataPanelBlockEntity p) return p;
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
    private DataPanelBlockEntity findSatellitePanel(World world, BlockPos corePos) {
        long best = Long.MAX_VALUE;
        DataPanelBlockEntity found = null;
        for (BlockPos p : DataPanelBlockEntity.panelsIn(world)) {
            long dx = p.getX() - corePos.getX(), dy = p.getY() - corePos.getY(), dz = p.getZ() - corePos.getZ();
            long d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < best && world.getBlockEntity(p) instanceof DataPanelBlockEntity panel) {
                best = d2;
                found = panel;
            }
        }
        if (found != null) return found;
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            var server = sw.getServer();
            for (var key : DataPanelBlockEntity.dimensionsWithPanels()) {
                if (key.equals(world.getRegistryKey())) continue;
                net.minecraft.server.world.ServerWorld ow = server.getWorld(key);
                if (ow == null) continue;
                for (BlockPos p : DataPanelBlockEntity.panelsIn(ow)) {
                    if (ow.getBlockEntity(p) instanceof DataPanelBlockEntity panel) return panel;
                }
            }
        }
        return found;
    }
    private DataPanelBlockEntity findPanel(World world, BlockPos corePos) {
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
                if (be instanceof DataPanelBlockEntity panel) return panel;
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
        if (boundPanelPos != null && boundPanelDim != null) {
            nbt.putLong("boundPos", boundPanelPos.asLong());
            nbt.putString("boundDim", boundPanelDim);
        }
        nbt.putBoolean("running", running);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        if (nbt.contains("boundPos")) {
            boundPanelPos = BlockPos.fromLong(nbt.getLong("boundPos"));
            boundPanelDim = nbt.getString("boundDim");
        } else {
            boundPanelPos = null; boundPanelDim = null;
        }
        running = nbt.getBoolean("running");
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
