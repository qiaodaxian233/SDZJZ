package com.sdzjz.screen;

import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** 数据面板 GUI：54 展示格（只可取出，取出即从逻辑仓储扣数）+ 玩家背包（可 shift 存入面板）。 */
public class DataPanelScreenHandler extends ScreenHandler {

    private final DataPanelBlockEntity panel;
    private final BlockPos blockPos;
    private final SimpleInventory craft = new SimpleInventory(9);       // m84b 合成终端
    private final SimpleInventory craftResult = new SimpleInventory(1);
    private final SimpleInventory trash = new SimpleInventory(1);       // 回收格：放入即销毁

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, DataPanelBlockEntity be) {
        super(ModScreenHandlers.DATA_PANEL, syncId);
        this.panel = be;
        this.blockPos = (be != null) ? be.getPos() : null;
        Inventory display = (be != null) ? be.display : new SimpleInventory(DataPanelBlockEntity.PAGE);

        // 展示区 6×9（只取不放）
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = c + r * 9;
                this.addSlot(new Slot(display, idx, 99 + c * 18, 30 + r * 18) {
                    @Override public boolean canInsert(ItemStack s) { return false; }
                    @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                        if (panel != null && !stack.isEmpty()) {
                            panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
                        }
                        // 剥掉展示用的数量 NBT，否则取出的物品与普通同类物品无法堆叠
                        stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                        super.onTakeItem(player, stack);
                    }
                });
            }
        }
        // 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 99 + c * 18, 158 + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 99 + c * 18, 216));
        // m84b 合成终端：3×3(90..98) + 结果(99) + 回收(100)
        craft.addListener(inv -> updateCraftResult());
        trash.addListener(inv -> { if (!trash.getStack(0).isEmpty()) trash.setStack(0, ItemStack.EMPTY); }); // 放入即销毁
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                this.addSlot(new Slot(craft, c + r * 3, 272 + c * 18, 40 + r * 18));
        this.addSlot(new Slot(craftResult, 0, 290, 102) {
            @Override public boolean canInsert(ItemStack s) { return false; }
            @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                consumeCraft(player);
                super.onTakeItem(player, stack);
            }
        });
        this.addSlot(new Slot(trash, 0, 334, 216));
        this.addProperties(xpProps); // m80c 经验库同步（双属性防 short 截断：id0=低16位 id1=高15位）
    }

    // ===== m84b 合成终端（ME 风格：终端里直接手动合成）=====
    private net.minecraft.recipe.input.CraftingRecipeInput craftInput() {
        java.util.List<ItemStack> l = new java.util.ArrayList<>(9);
        for (int i = 0; i < 9; i++) l.add(craft.getStack(i));
        return net.minecraft.recipe.input.CraftingRecipeInput.create(3, 3, l);
    }

    private void updateCraftResult() {
        if (panel == null || panel.getWorld() == null || panel.getWorld().isClient) return;
        var w = panel.getWorld();
        var input = craftInput();
        craftResult.setStack(0, w.getRecipeManager()
                .getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, w)
                .map(e -> e.value().craft(input, w.getRegistryManager())).orElse(ItemStack.EMPTY));
    }

    /** 取走结果：每格扣 1，容器残留(桶等)留在原格或还给玩家，然后重算结果。 */
    private void consumeCraft(PlayerEntity player) {
        if (panel == null || panel.getWorld() == null) return;
        var w = panel.getWorld();
        var input = craftInput();
        var match = w.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, w);
        net.minecraft.util.collection.DefaultedList<ItemStack> rem =
                match.map(e -> e.value().getRemainder(input)).orElse(null);
        for (int i = 0; i < 9; i++) {
            ItemStack st = craft.getStack(i);
            if (!st.isEmpty()) st.decrement(1);
            if (rem != null && i < rem.size() && !rem.get(i).isEmpty()) {
                ItemStack r2 = rem.get(i).copy();
                if (craft.getStack(i).isEmpty()) craft.setStack(i, r2);
                else if (!player.getInventory().insertStack(r2)) player.dropItem(r2, false);
            }
        }
        updateCraftResult();
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player.getWorld().isClient) return;
        for (int i = 0; i < 9; i++) { // 关界面归还合成格材料，绝不吞
            ItemStack st = craft.getStack(i);
            if (!st.isEmpty()) {
                craft.setStack(i, ItemStack.EMPTY);
                if (!player.getInventory().insertStack(st)) player.dropItem(st, false);
            }
        }
    }

    // ===== m80c 经验库 =====
    private final net.minecraft.screen.PropertyDelegate xpProps = new net.minecraft.screen.PropertyDelegate() {
        @Override public int get(int i) {
            if (i == 2) return panel != null ? panel.typesUsed() : 0; // m97 全网类型用量
            if (i == 3) return panel != null ? panel.typesCap()  : 0;
            long v = panel != null ? Math.min(panel.xpTotal(), Integer.MAX_VALUE) : 0;
            return i == 0 ? (int) (v & 0xFFFF) : (int) ((v >> 16) & 0x7FFF);
        }
        @Override public void set(int i, int v) {}
        @Override public int size() { return 4; }
    };

    /** 客户端读经验库总量。 */
    public long xpBankView() { return (xpLo & 0xFFFFL) | ((long) xpHi << 16); }
    private int xpLo, xpHi, typesUsed, typesCap;
    /** m97：客户端读全网类型用量（"类型 X/Y"，Y=0 表示网络里没有存储核心）。 */
    public int typesUsedView() { return typesUsed; }
    public int typesCapView()  { return typesCap; }
    @Override
    public void setProperty(int id, int value) {
        super.setProperty(id, value);
        if (id == 0) xpLo = value & 0xFFFF;
        if (id == 1) xpHi = value & 0x7FFF;
        if (id == 2) typesUsed = value;
        if (id == 3) typesCap = value;
    }

    /** 按钮：1=存入全部玩家经验 2=取出全部。服务端执行。 */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (panel == null) return false;
        if (id >= 1000) { // m82 取指定数量 / m100 批量取出：id = 1000 + 展示格下标*10 + 档位(0..8)
            int slotIdx = (id - 1000) / 10, k = (id - 1000) % 10;
            // 档位 0-4：定量 1/8/16/32/64；5-7：2组/4组/8组(组=该物品堆叠上限)；8：填满背包
            if (slotIdx < 0 || slotIdx >= DataPanelBlockEntity.PAGE || k > 8) return false;
            ItemStack disp = this.slots.get(slotIdx).getStack();
            if (disp.isEmpty()) return true;
            String idStr = Registries.ITEM.getId(disp.getItem()).toString();
            int maxStack = Math.max(1, disp.getItem().getMaxCount());
            int[] fixed = {1, 8, 16, 32, 64};
            long want = k < 5 ? fixed[k] : (k < 8 ? (long) maxStack * (2L << (k - 5)) : Long.MAX_VALUE); // 5→2组 6→4组 7→8组 8→填满
            long given = 0;
            while (want > 0) {
                int chunk = (int) Math.min(want, maxStack);
                int got = panel.withdraw(idStr, chunk);
                if (got <= 0) break; // 仓储见底
                ItemStack give = new ItemStack(disp.getItem(), got);
                player.getInventory().insertStack(give);
                given += got - give.getCount();
                if (!give.isEmpty()) { // 背包满：余量原路回仓，绝不落地/销毁
                    panel.deposit(give);
                    if (!give.isEmpty()) player.dropItem(give, false); // 双保险(刚取出的同类物品，理论回得去)
                    break;
                }
                want -= chunk;
            }
            if (k >= 5) msg(player, given > 0 ? "已装入 " + given + " 个" : "背包没有空位");
            return true;
        }
        if (id == 1) {
            if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return true;
            long pts = totalXp(player);
            if (pts <= 0) { msg(player, "你没有可存入的经验"); return true; }
            if (!panel.xpDeposit(pts)) { msg(player, "网络里没有存储核心，无法存入经验"); return true; }
            sp.setExperienceLevel(0);  // 这两个 setter 在 ServerPlayerEntity 上（Yarn 1.21 查证），
            sp.setExperiencePoints(0); // PlayerEntity 没有——onButtonClick 本就服务端执行，安全转型
            msg(player, "已存入经验 " + pts + " 点");
            return true;
        }
        if (id == 2) {
            long got = panel.xpWithdraw(Integer.MAX_VALUE);
            if (got <= 0) { msg(player, "经验库是空的"); return true; }
            player.addExperience((int) Math.min(got, Integer.MAX_VALUE));
            msg(player, "已取出经验 " + got + " 点");
            return true;
        }
        return false;
    }

    private static void msg(PlayerEntity p, String s) { p.sendMessage(net.minecraft.text.Text.literal(s), true); }

    /** 玩家当前总经验点（原版等级公式）。 */
    private static long totalXp(PlayerEntity p) {
        int lv = p.experienceLevel;
        long base;
        if (lv <= 16) base = (long) lv * lv + 6L * lv;
        else if (lv <= 31) base = Math.round(2.5 * lv * lv - 40.5 * lv + 360);
        else base = Math.round(4.5 * lv * lv - 162.5 * lv + 2220);
        return base + Math.round((double) p.experienceProgress * p.getNextLevelExperience());
    }

    private static DataPanelBlockEntity resolve(PlayerInventory playerInv, BlockPos pos) {
        BlockEntity be = playerInv.player.getWorld().getBlockEntity(pos);
        return be instanceof DataPanelBlockEntity p ? p : null;
    }

    public BlockPos blockPos() { return blockPos; }

    @Override
    public boolean canUse(PlayerEntity player) {
        return panel != null;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();

        if (index < DataPanelBlockEntity.PAGE) {
            // 展示格 → 玩家背包：先试塞（干净副本，无展示 NBT），按实际塞入量扣 store。
            // 顺序绝不能反：先扣后塞时背包满/塞一半 = 物品凭空消失。
            ItemStack clean = new ItemStack(stack.getItem(), stack.getCount());
            int before = clean.getCount();
            this.insertItem(clean, DataPanelBlockEntity.PAGE, DataPanelBlockEntity.PAGE + 36, true);
            int inserted = before - clean.getCount();
            if (inserted > 0 && panel != null) {
                panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), inserted);
            }
            slot.setStack(ItemStack.EMPTY); // 展示格下个刷新周期重建
            return ItemStack.EMPTY;
        } else if (index >= DataPanelBlockEntity.PAGE + 36 && index < DataPanelBlockEntity.PAGE + 45) {
            // 合成格 → 背包
            this.insertItem(stack, DataPanelBlockEntity.PAGE, DataPanelBlockEntity.PAGE + 36, true);
            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            return ItemStack.EMPTY;
        } else if (index >= DataPanelBlockEntity.PAGE + 45) {
            return ItemStack.EMPTY; // 结果格用鼠标取；回收格不 shift
        } else {
            // 玩家背包 → 存入面板
            if (panel != null) {
                // 带组件的物品（附魔/损耗/药水/成书等）拒存：仓储按 id 记账，存入会抹掉组件——宁可不动，绝不销毁数据
                if (!stack.getComponentChanges().isEmpty()) return ItemStack.EMPTY;
                ItemStack copy = stack.copy();
                panel.deposit(copy);
                // 只按实际存入量扣：无存储核心/类型满时余量留在原槽，绝不凭空消失
                if (copy.getCount() != stack.getCount()) {
                    slot.setStack(copy.isEmpty() ? ItemStack.EMPTY : copy);
                    slot.markDirty();
                }
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
