package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 存储核心：逻辑仓储(id→long)，类型数受等级上限；可升级。数据面板/机器经网络(数据线/相邻)访问。 */
public class StorageCoreBlockEntity extends BlockEntity implements com.sdzjz.machine.StorageAccess {

    /** m98：类型上限走配置——storageTypesPerTier 0=无限(默认)，>0=每级该数(旧机制27)。tier 保留兼容旧档与配置回切。 */
    private static int typesPerTier() { return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier; }
    private final LinkedHashMap<String, Long> store = new LinkedHashMap<>();
    private int tier = 1;

    private static final Map<RegistryKey<World>, Set<BlockPos>> CORES = new HashMap<>();

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_CORE_BE, pos, state);
    }

    public static void register(World world, BlockPos pos) {
        if (world.isClient) return;
        CORES.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>()).add(pos.toImmutable());
    }
    public static void unregister(World world, BlockPos pos) {
        Set<BlockPos> s = CORES.get(world.getRegistryKey());
        if (s != null) s.remove(pos);
    }
    public static Set<BlockPos> coresIn(World world) {
        return CORES.getOrDefault(world.getRegistryKey(), Set.of());
    }
    public static Set<RegistryKey<World>> dimensionsWithCores() {
        return CORES.keySet();
    }

    /** 服务器停止时清空登记表（防跨存档幽灵坐标）。 */
    public static void clearAll() {
        CORES.clear();
    }

    /**
     * 安全查找：只查已加载区块（getBlockEntity 在服务端会强制加载区块，遍历登记表时绝不能用）。
     * 区块已加载但不存在存储核心 → 判定为幽灵坐标，顺手从登记表剔除。
     */
    public static StorageCoreBlockEntity loadedCoreAt(World world, BlockPos p) {
        if (!world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return null;
        if (world.getBlockEntity(p) instanceof StorageCoreBlockEntity core) return core;
        unregister(world, p);
        return null;
    }

    public static void tick(World world, BlockPos pos, BlockState state, StorageCoreBlockEntity be) {
        if (world.isClient) return;
        register(world, pos);
    }

    @Override
    public void markRemoved() {
        if (this.world != null) unregister(this.world, this.pos);
        super.markRemoved();
    }

    public int tier() { return tier; }
    public int maxTypes() { int p = typesPerTier(); return p <= 0 ? Integer.MAX_VALUE : p * tier; }
    public int usedTypes() { return store.size(); }
    public void upgrade() { tier++; markDirty(); }

    // ===== m80c 经验库：网络级经验银行（数据面板界面存/取）=====
    private long xpBank = 0;
    public long xpBank() { return xpBank; }
    public void xpAdd(long points) { if (points > 0) { xpBank += points; markDirty(); } }
    /** 取出至多 max 点，返回实际取出。 */
    public long xpTake(long max) {
        long t = Math.min(xpBank, Math.max(0, max));
        xpBank -= t;
        if (t > 0) markDirty();
        return t;
    }

    public long count(String id) {
        Long v = store.get(id);
        return v == null ? 0L : v;
    }

    /** 存入。默认无限类型（m98）；config 启用上限时，类型未满或已有该类型才收（拒收时栈原样保留）。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (!store.containsKey(id) && store.size() >= maxTypes()) return;
        store.merge(id, (long) stack.getCount(), Long::sum);
        stack.setCount(0);
        markDirty();
    }

    public int withdraw(String id, int amount) {
        Long have = store.get(id);
        if (have == null || amount <= 0) return 0;
        int take = (int) Math.min((long) amount, have);
        long left = have - take;
        if (left <= 0) store.remove(id); else store.put(id, left);
        markDirty();
        return take;
    }

    public Map<String, Long> storeView() { return store; }

    /** BFS：从某位置经数据线/相邻找到所有存储核心。 */
    public static List<StorageCoreBlockEntity> connectedCores(World world, BlockPos from) {
        List<StorageCoreBlockEntity> out = new ArrayList<>();
        if (world == null) return out;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        q.add(from); seen.add(from);
        int limit = 4096;
        while (!q.isEmpty() && limit-- > 0) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos np = p.offset(d);
                if (!seen.add(np)) continue;
                BlockEntity be = world.getBlockEntity(np);
                if (be instanceof StorageCoreBlockEntity core) { out.add(core); q.add(np); }
                else if (world.getBlockState(np).getBlock() instanceof DataCableBlock) q.add(np);
            }
        }
        return out;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putInt("tier", tier);
        NbtList list = new NbtList();
        for (Map.Entry<String, Long> e : store.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("id", e.getKey());
            c.putLong("n", e.getValue());
            list.add(c);
        }
        nbt.put("store", list);
        nbt.putLong("xpBank", xpBank);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        tier = Math.max(1, nbt.getInt("tier"));
        xpBank = Math.max(0, nbt.getLong("xpBank"));
        store.clear();
        NbtList list = nbt.getList("store", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            store.put(c.getString("id"), c.getLong("n"));
        }
    }
}
