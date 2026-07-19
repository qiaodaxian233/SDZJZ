package com.sdzjz.screen;

import com.sdzjz.machine.SuperBenchRecipes;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** 超大工作台：12×12 输入网格 + 1 结果槽 + 玩家背包；无形状(多重集精确)匹配机器配方。 */
public class SuperBenchScreenHandler extends ScreenHandler {

    public static final int GRID = 12;
    public static final int GRID_SLOTS = GRID * GRID; // 144
    public static final int RESULT_INDEX = GRID_SLOTS;

    private final Inventory input = new SimpleInventory(GRID_SLOTS) {
        @Override public void markDirty() { super.markDirty(); onContentChanged(this); }
    };
    private final CraftingResultInventory result = new CraftingResultInventory();
    private final ScreenHandlerContext context;

    // 客户端
    public SuperBenchScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, ScreenHandlerContext.EMPTY);
    }

    public SuperBenchScreenHandler(int syncId, PlayerInventory playerInv, ScreenHandlerContext context) {
        super(ModScreenHandlers.SUPER_BENCH, syncId);
        this.context = context;

        int gx = 8, gy = 18;
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++)
                this.addSlot(new Slot(input, r * GRID + c, gx + c * 18, gy + r * 18));

        // 结果槽（网格右侧居中）
        this.addSlot(new Slot(result, 0, gx + GRID * 18 + 24, gy + (GRID * 18) / 2 - 8) {
            @Override public boolean canInsert(ItemStack s) { return false; }
            @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                consumeIngredients();
                super.onTakeItem(player, stack);
            }
        });

        // 玩家背包（网格下方）
        int py = gy + GRID * 18 + 12;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, gx + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, gx + c * 18, py + 58));
    }

    private Map<String, Integer> gridMultiset() {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (!s.isEmpty()) m.merge(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return m;
    }

    @Override
    public void onContentChanged(Inventory inv) {
        if (inv == input) {
            SuperBenchRecipes.Recipe r = SuperBenchRecipes.match(gridMultiset());
            result.setStack(0, mobOk(r) ? SuperBenchRecipes.resultStack(r) : ItemStack.EMPTY);
        }
    }

    /** 刷怪类配方：网格里必须有一个「装着指定生物」的抓物笼子（空笼/装错生物都不行）。 */
    private boolean mobOk(SuperBenchRecipes.Recipe r) {
        if (r == null) return false;
        if (r.mob().isEmpty()) return true;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                    && r.mob().equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) return true;
        }
        return false;
    }

    private void consumeIngredients() {
        SuperBenchRecipes.Recipe r = SuperBenchRecipes.match(gridMultiset());
        if (r == null || !mobOk(r)) return;
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            int need = e.getValue();
            if (SuperBenchRecipes.CAGE_ID.equals(e.getKey()) && !r.mob().isEmpty()) {
                // 笼子不消耗：生物「装进」机器，清 NBT 留一个空笼在网格里
                for (int i = 0; i < GRID_SLOTS && need > 0; i++) {
                    ItemStack s = input.getStack(i);
                    if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                            && r.mob().equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) {
                        s.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                        s.remove(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
                        need--;
                    }
                }
                continue;
            }
            for (int i = 0; i < GRID_SLOTS && need > 0; i++) {
                ItemStack s = input.getStack(i);
                if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).toString().equals(e.getKey())) {
                    int take = Math.min(need, s.getCount());
                    s.decrement(take);
                    need -= take;
                }
            }
        }
        onContentChanged(input);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.context.run((world, pos) -> this.dropInventory(player, input));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /** 配方浏览器点击：把 #id 配方的材料从背包填入网格（先清空网格还给玩家）。 */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id < 0 || id >= SuperBenchRecipes.ALL.size()) return false;
        SuperBenchRecipes.Recipe r = SuperBenchRecipes.ALL.get(id);
        // 清空网格→还给玩家
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (!s.isEmpty()) {
                if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
                input.setStack(i, ItemStack.EMPTY);
            }
        }
        // 刷怪类：先从背包找「装着指定生物」的那个笼子（整个带 NBT 搬过来，不能造新的）
        ItemStack cage = ItemStack.EMPTY;
        if (!r.mob().isEmpty()) {
            PlayerInventory pinv = player.getInventory();
            for (int i = 0; i < pinv.size(); i++) {
                ItemStack s = pinv.getStack(i);
                if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                        && r.mob().equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) {
                    cage = s.copy();
                    pinv.setStack(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        // 从背包按需批量取料，建立可用池
        Map<String, Integer> pool = new HashMap<>();
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            if (SuperBenchRecipes.CAGE_ID.equals(e.getKey())) continue; // 笼子单独处理
            Item item = Registries.ITEM.get(Identifier.of(e.getKey()));
            pool.put(e.getKey(), takeFromInv(player, item, e.getValue()));
        }
        // 按蓝图布局逐格摆放（1 格 1 件；缺料的格留空）
        String[] layout = r.layout();
        for (int i = 0; i < GRID_SLOTS; i++) {
            String want = layout[i];
            if (want == null) continue;
            if (SuperBenchRecipes.CAGE_ID.equals(want)) {
                if (!cage.isEmpty()) { input.setStack(i, cage); cage = ItemStack.EMPTY; }
                continue;
            }
            int have = pool.getOrDefault(want, 0);
            if (have > 0) {
                input.setStack(i, new ItemStack(Registries.ITEM.get(Identifier.of(want)), 1));
                pool.put(want, have - 1);
            }
        }
        if (!cage.isEmpty()) { if (!player.getInventory().insertStack(cage)) player.dropItem(cage, false); }
        input.markDirty();
        return true;
    }

    private int takeFromInv(PlayerEntity player, Item item, int need) {
        int got = 0;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size() && got < need; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) { int take = Math.min(need - got, s.getCount()); s.decrement(take); got += take; }
        }
        return got;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index == RESULT_INDEX) return ItemStack.EMPTY; // 结果槽用鼠标取
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack st = slot.getStack();
            ret = st.copy();
            int invStart = RESULT_INDEX + 1;
            int invEnd = invStart + 36;
            if (index < GRID_SLOTS) {
                if (!this.insertItem(st, invStart, invEnd, false)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(st, 0, GRID_SLOTS, false)) return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        }
        return ret;
    }
}
