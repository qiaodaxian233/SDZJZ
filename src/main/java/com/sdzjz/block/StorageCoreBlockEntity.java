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
public class StorageCoreBlockEntity extends BlockEntity {

    private static final int TYPES_PER_TIER = 27;
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
    public int maxTypes() { return TYPES_PER_TIER * tier; }
    public int usedTypes() { return store.size(); }
    public void upgrade() { tier++; markDirty(); }

    public long count(String id) {
        Long v = store.get(id);
        return v == null ? 0L : v;
    }

    /** 存入（类型未满或已有该类型才收）。 */
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
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        tier = Math.max(1, nbt.getInt("tier"));
        store.clear();
        NbtList list = nbt.getList("store", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            store.put(c.getString("id"), c.getLong("n"));
        }
    }
}
