package com.sdzjz.screen;

import com.sdzjz.machine.SuperBenchRecipes;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;

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
            result.setStack(0, SuperBenchRecipes.resultStack(r));
        }
    }

    private void consumeIngredients() {
        SuperBenchRecipes.Recipe r = SuperBenchRecipes.match(gridMultiset());
        if (r == null) return;
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            int need = e.getValue();
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
