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

/** 存储核心：双账本逻辑仓储——普通(id→long) + 精确(物品+组件模板→long, m130)；类型数受等级上限；可升级。
 *  数据面板/机器经网络(数据线/相邻)访问；机器/过滤器/熔炉只见普通账本。 */
public class StorageCoreBlockEntity extends BlockEntity implements com.sdzjz.machine.StorageAccess {

    /** m98：类型上限走配置——storageTypesPerTier 0=无限(默认)，>0=每级该数(旧机制27)。tier 保留兼容旧档与配置回切。 */
    private static int typesPerTier() { return com.sdzjz.config.SdzjzConfig.get().storageTypesPerTier; }
    private final LinkedHashMap<String, Long> store = new LinkedHashMap<>();
    // m130 精确存储：带组件物品的模板账本（模板 count=1 + 独立 long 计数，两表下标对齐）。
    // 普通物品仍走 store（机器热路径零改动）；过滤器/熔炉/传感器只见普通账本——机器不吃附魔书（设计留痕）。
    private final List<ItemStack> exactTpl = new ArrayList<>();
    private final List<Long> exactN = new ArrayList<>();
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
    public int usedTypes() { return store.size() + exactTpl.size(); } // m130：精确条目同占类型额度
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

    /** 存入。默认无限类型（m98）；config 启用上限时，类型未满或已有该类型才收（拒收时栈原样保留）。
     *  m130：带组件的物品自动分流进精确账本，组件原样保存——附魔书/药水/损耗工具/带阶位机器全部可入仓。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!stack.getComponentChanges().isEmpty()) { depositExact(stack); return; }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (!store.containsKey(id) && usedTypes() >= maxTypes()) return;
        store.merge(id, (long) stack.getCount(), Long::sum);
        stack.setCount(0);
        markDirty();
    }

    /** m130：精确存入——按「物品+组件」找同款条目并账；新类型受同一类型上限（拒收时栈原样保留）。 */
    public void depositExact(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int i = 0; i < exactTpl.size(); i++) {
            if (ItemStack.areItemsAndComponentsEqual(exactTpl.get(i), stack)) {
                exactN.set(i, exactN.get(i) + stack.getCount());
                stack.setCount(0);
                markDirty();
                return;
            }
        }
        if (usedTypes() >= maxTypes()) return;
        exactTpl.add(stack.copyWithCount(1));
        exactN.add((long) stack.getCount());
        stack.setCount(0);
        markDirty();
    }

    /** m130：精确取出——按「物品+组件」匹配模板，返回实际取出数量。 */
    public int withdrawExact(ItemStack template, int amount) {
        if (template == null || template.isEmpty() || amount <= 0) return 0;
        for (int i = 0; i < exactTpl.size(); i++) {
            if (ItemStack.areItemsAndComponentsEqual(exactTpl.get(i), template)) {
                long have = exactN.get(i);
                int take = (int) Math.min((long) amount, have);
                long left = have - take;
                if (left <= 0) { exactTpl.remove(i); exactN.remove(i); } else exactN.set(i, left);
                if (take > 0) markDirty();
                return take;
            }
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
        NbtList ex = new NbtList(); // m130：精确账本持久化（模板 encode + long 计数）
        for (int i = 0; i < exactTpl.size(); i++) {
            NbtCompound c = new NbtCompound();
            c.put("item", exactTpl.get(i).encode(lookup));
            c.putLong("n", exactN.get(i));
            ex.add(c);
        }
        nbt.put("exact", ex);
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
        exactTpl.clear(); // m130：读回精确账本（解析失败/物品已卸载的条目静默跳过，不炸档）
        exactN.clear();
        NbtList ex = nbt.getList("exact", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ex.size(); i++) {
            NbtCompound c = ex.getCompound(i);
            ItemStack t = ItemStack.fromNbt(lookup, c.getCompound("item")).orElse(ItemStack.EMPTY);
            long n = c.getLong("n");
            if (!t.isEmpty() && n > 0) { exactTpl.add(t.copyWithCount(1)); exactN.add(n); }
        }
    }
}
