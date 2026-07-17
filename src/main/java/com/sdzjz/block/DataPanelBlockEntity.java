package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.screen.DataPanelScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据面板方块实体：逻辑仓储。物品以 (id -> 数量) 存储，近乎无限。
 * 展示：SimpleInventory display(54 格)，服务端每 tick 从 store 前 54 种刷新；
 * GUI 取物 = 从对应类型的 store 里扣除（见 DataPanelScreenHandler）。
 * Phase 2：先不做翻页；类型超 54 的以后加。
 */
public class DataPanelBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public static final int PAGE = 54;
    private final LinkedHashMap<String, Long> store = new LinkedHashMap<>();

    // ---- 面板位置登记表（无线连接查找用，仅服务端）----
    private static final Map<RegistryKey<World>, Set<BlockPos>> PANELS = new HashMap<>();

    public static void register(World world, BlockPos pos) {
        if (world.isClient) return;
        PANELS.computeIfAbsent(world.getRegistryKey(), k -> new HashSet<>()).add(pos.toImmutable());
    }

    public static void unregister(World world, BlockPos pos) {
        Set<BlockPos> s = PANELS.get(world.getRegistryKey());
        if (s != null) s.remove(pos);
    }

    public static Set<BlockPos> panelsIn(World world) {
        return PANELS.getOrDefault(world.getRegistryKey(), Set.of());
    }
    public final SimpleInventory display = new SimpleInventory(PAGE);

    public DataPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_PANEL_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, DataPanelBlockEntity be) {
        if (world.isClient) return;
        register(world, pos);
        be.refreshDisplay();
    }

    @Override
    public void markRemoved() {
        if (this.world != null) unregister(this.world, this.pos);
        super.markRemoved();
    }

    /** 存入（消耗传入 stack）。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        store.merge(id, (long) stack.getCount(), Long::sum);
        stack.setCount(0);
        markDirty();
    }

    /** 查询某类型现有数量。 */
    public long count(String id) {
        Long v = store.get(id);
        return v == null ? 0L : v;
    }

    /** 取出某类型至多 amount 个，返回实际取出数量。 */
    public int withdraw(String id, int amount) {
        Long have = store.get(id);
        if (have == null || amount <= 0) return 0;
        int take = (int) Math.min((long) amount, have);
        long left = have - take;
        if (left <= 0) store.remove(id);
        else store.put(id, left);
        markDirty();
        return take;
    }

    public String idAt(int displaySlot) {
        int i = 0;
        for (String key : store.keySet()) {
            if (i++ == displaySlot) return key;
        }
        return null;
    }

    private void refreshDisplay() {
        int i = 0;
        for (Map.Entry<String, Long> e : store.entrySet()) {
            if (i >= PAGE) break;
            Item item = Registries.ITEM.get(Identifier.of(e.getKey()));
            int max = new ItemStack(item).getMaxCount();
            int show = (int) Math.min(e.getValue(), (long) max);
            display.setStack(i, new ItemStack(item, Math.max(1, show)));
            i++;
        }
        for (; i < PAGE; i++) display.setStack(i, ItemStack.EMPTY);
    }

    // ===== NBT =====
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
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
        store.clear();
        NbtList list = nbt.getList("store", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            store.put(c.getString("id"), c.getLong("n"));
        }
        refreshDisplay();
    }

    // ===== GUI 工厂 =====
    @Override
    public Text getDisplayName() {
        return Text.translatable("container.sdzjz.data_panel");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new DataPanelScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
        return this.pos;
    }
}
