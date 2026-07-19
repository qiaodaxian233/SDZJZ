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
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据面板：存储终端（不自带存储）。经网络访问相连的存储核心，聚合显示/存取。 */
public class DataPanelBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, com.sdzjz.machine.StorageAccess {

    public static final int PAGE = 54;
    private String searchFilter = "";
    private int scrollRow = 0;
    private int filteredCount = 0;
    private int refreshTicker = 0;
    public final SimpleInventory display = new SimpleInventory(PAGE);

    public DataPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_PANEL_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, DataPanelBlockEntity be) {
        if (world.isClient) return;
        // 节流：refreshDisplay 内部要 BFS 聚合存储核心，每 tick 跑是卡顿机器；改每 10 tick。
        if (++be.refreshTicker % 10 != 0) return;
        be.refreshDisplay();
    }

    private List<StorageCoreBlockEntity> cores() {
        return StorageCoreBlockEntity.connectedCores(this.world, this.pos);
    }

    private LinkedHashMap<String, Long> aggregate() {
        LinkedHashMap<String, Long> agg = new LinkedHashMap<>();
        for (StorageCoreBlockEntity core : cores())
            for (Map.Entry<String, Long> e : core.storeView().entrySet())
                agg.merge(e.getKey(), e.getValue(), Long::sum);
        return agg;
    }

    public long count(String id) {
        long n = 0;
        for (StorageCoreBlockEntity core : cores()) n += core.count(id);
        return n;
    }

    /** 存入：塞进第一个收得下的存储核心。 */
    public void deposit(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (StorageCoreBlockEntity core : cores()) {
            core.deposit(stack);
            if (stack.isEmpty()) return;
        }
    }

    /** 取出：跨核心累计取，返回实际取出数量。 */
    @Override
    public java.util.Map<String, Long> storeView() { // 聚合快照：万能熔炉"接什么烧什么"扫描用
        java.util.LinkedHashMap<String, Long> merged = new java.util.LinkedHashMap<>();
        for (StorageCoreBlockEntity core : cores())
            for (var e : core.storeView().entrySet())
                merged.merge(e.getKey(), e.getValue(), Long::sum);
        return merged;
    }

    public int withdraw(String id, int amount) {
        int got = 0;
        for (StorageCoreBlockEntity core : cores()) {
            if (got >= amount) break;
            got += core.withdraw(id, amount - got);
        }
        return got;
    }

    private java.util.Set<String> matchedIds = java.util.Set.of();

    public void setView(String search, int scroll, java.util.List<String> matched) {
        this.searchFilter = (search == null) ? "" : search;
        this.scrollRow = Math.max(0, scroll);
        this.matchedIds = (matched == null || matched.isEmpty()) ? java.util.Set.of() : java.util.Set.copyOf(matched);
        refreshDisplay();
    }

    public int filteredRows() { return (filteredCount + 8) / 9; }

    private void refreshDisplay() {
        LinkedHashMap<String, Long> agg = aggregate();
        java.util.List<Map.Entry<String, Long>> filtered = new java.util.ArrayList<>();
        String q = searchFilter == null ? "" : searchFilter.toLowerCase();
        for (Map.Entry<String, Long> e : agg.entrySet())
            if (q.isEmpty() || e.getKey().toLowerCase().contains(q) || matchedIds.contains(e.getKey())) filtered.add(e);
        filteredCount = filtered.size();
        int rows = (filteredCount + 8) / 9;
        int maxRow = Math.max(0, rows - 6);
        if (scrollRow > maxRow) scrollRow = maxRow;
        if (scrollRow < 0) scrollRow = 0;

        int i = 0;
        for (int idx = scrollRow * 9; idx < filtered.size() && i < PAGE; idx++, i++) {
            Map.Entry<String, Long> e = filtered.get(idx);
            Item item = Registries.ITEM.get(Identifier.of(e.getKey()));
            int max = new ItemStack(item).getMaxCount();
            int show = (int) Math.min(e.getValue(), (long) max);
            ItemStack st = new ItemStack(item, Math.max(1, show));
            NbtCompound tag = new NbtCompound();
            tag.putLong("amt", e.getValue());
            st.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                    net.minecraft.component.type.NbtComponent.of(tag));
            display.setStack(i, st);
        }
        for (; i < PAGE; i++) display.setStack(i, ItemStack.EMPTY);
    }

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
