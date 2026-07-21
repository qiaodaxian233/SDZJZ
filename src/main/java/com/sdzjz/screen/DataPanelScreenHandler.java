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
                        // m112：账本只在服务端动（m95 铁律）。此钩子客户端预测也会跑——客户端 BE 账本是空的，
                        // 在这里 withdraw/钳数会按空账把光标预测成 0，还会污染客户端核心读数。
                        if (!player.getWorld().isClient && panel != null && !stack.isEmpty()) {
                            int got = panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount());
                            // m111：网络实收多少给多少——展示栈在 10t 刷新窗口内可能过期，绝不超发凭空造物
                            if (got < stack.getCount()) stack.setCount(Math.max(0, got));
                            panel.refreshDisplay(); // 取走后余量立刻回显，格子不再空 0.5s（AE 手感）
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
        // m107a：服务端登记查看者（打开即刷一次，闲置面板不再空转 BFS）；客户端构造 resolve 出的是客户端 BE，不计数
        if (be != null && be.getWorld() != null && !be.getWorld().isClient) be.addViewer();
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

    /** 取走结果：每格扣 1，容器残留(桶等)留在原格或还给玩家，然后 AE 式从网络补料并重算结果。 */
    private void consumeCraft(PlayerEntity player) {
        if (panel == null || panel.getWorld() == null || panel.getWorld().isClient) return; // m95 同款：只在服务端扣料，客户端等同步纠正
        var w = panel.getWorld();
        var input = craftInput();
        var match = w.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, w);
        net.minecraft.util.collection.DefaultedList<ItemStack> rem =
                match.map(e -> e.value().getRemainder(input)).orElse(null);
        // m106b AE 式补料：cores 快照一次，9 格共用（不逐格 BFS）
        var cores = com.sdzjz.block.StorageCoreBlockEntity.connectedCores(w, blockPos);
        for (int i = 0; i < 9; i++) {
            ItemStack st = craft.getStack(i);
            boolean plain = !st.isEmpty() && st.getComponentChanges().isEmpty(); // 带组件的(附魔/损耗)不按 id 补
            String idStr = st.isEmpty() ? null : Registries.ITEM.getId(st.getItem()).toString();
            if (!st.isEmpty()) st.decrement(1);
            boolean remPlaced = false;
            if (rem != null && i < rem.size() && !rem.get(i).isEmpty()) {
                ItemStack r2 = rem.get(i).copy();
                if (craft.getStack(i).isEmpty()) { craft.setStack(i, r2); remPlaced = true; }
                else if (!player.getInventory().insertStack(r2)) player.dropItem(r2, false);
            }
            // 网格当模板：消耗掉的 1 个从网络抽同款回填，网格保持满编（学 AE2 合成终端，代码自写）
            if (plain && !remPlaced) {
                ItemStack cur = craft.getStack(i);
                int got = 0;
                if (cur.isEmpty() || cur.getCount() < cur.getMaxCount()) {
                    for (var core : cores) { got = core.withdraw(idStr, 1); if (got > 0) break; }
                }
                if (got > 0) {
                    if (cur.isEmpty()) craft.setStack(i, new ItemStack(
                            Registries.ITEM.get(net.minecraft.util.Identifier.of(idStr)), 1));
                    else cur.increment(1);
                }
            }
        }
        updateCraftResult();
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player.getWorld().isClient) return;
        if (panel != null) panel.removeViewer(); // m107a：注销查看者（断线也走 onClosed，不泄漏）
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
            if (i == 4) return panel != null ? Math.min(panel.filteredRows(), 65534) : 0; // m107b 总行数→真实滚动条
            long v = panel != null ? Math.min(panel.xpTotal(), Integer.MAX_VALUE) : 0;
            return i == 0 ? (int) (v & 0xFFFF) : (int) ((v >> 16) & 0x7FFF);
        }
        @Override public void set(int i, int v) {}
        @Override public int size() { return 5; }
    };

    /** 客户端读经验库总量。 */
    public long xpBankView() { return (xpLo & 0xFFFFL) | ((long) xpHi << 16); }
    private int xpLo, xpHi, typesUsed, typesCap, rowsSynced;
    /** m97：客户端读全网类型用量（"类型 X/Y"，Y=0 表示网络里没有存储核心）。 */
    public int typesUsedView() { return typesUsed; }
    public int typesCapView()  { return typesCap; }
    /** m107b：客户端读筛选后总行数（真实滚动条/滚动 clamp）。 */
    public int rowsView()      { return rowsSynced; }
    @Override
    public void setProperty(int id, int value) {
        super.setProperty(id, value);
        if (id == 0) xpLo = value & 0xFFFF;
        if (id == 1) xpHi = value & 0x7FFF;
        // 原版容器属性包走 16 位 short 通道，0xFFFF(无限哨兵)符号扩展成 -1 → 误判"无存储核心"。
        // 与 xpLo 同款掩码还原无符号（m106a 修：m98 无限成默认后此红字常驻）。
        if (id == 2) typesUsed = value & 0xFFFF;
        if (id == 3) typesCap = value & 0xFFFF;
        if (id == 4) rowsSynced = value & 0xFFFF; // m107b：同通道同款掩码
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
        if (id == 4 || id == 5) { // m111 AE 手感：光标存入网络（4=全放 5=放1）——服务端权威，客户端零预测
            ItemStack cur = this.getCursorStack();
            if (cur.isEmpty()) return true;
            if (!cur.getComponentChanges().isEmpty()) { // 与 quickMove 存入同一条红线：防抹组件
                msg(player, "带附魔/耐久等组件的物品不入仓（防抹数据）");
                return true;
            }
            if (id == 5) {
                ItemStack one = cur.copyWithCount(1);
                panel.deposit(one);                    // deposit 按实际存入量扣减
                if (one.isEmpty()) cur.decrement(1);   // 存进去了才扣，无核心/类型满时原样留在光标
            } else {
                panel.deposit(cur);
            }
            this.setCursorStack(cur.isEmpty() ? ItemStack.EMPTY : cur);
            panel.refreshDisplay();                    // 存完立刻可见
            return true;
        }
        if (id == 3) { // m107c 清空合成网格：无组件回网络，带组件/无核心的余量回背包，绝不落地销毁
            for (int i = 0; i < 9; i++) {
                ItemStack st = craft.getStack(i);
                if (st.isEmpty()) continue;
                craft.setStack(i, ItemStack.EMPTY);
                if (st.getComponentChanges().isEmpty()) panel.deposit(st); // deposit 按实际存入量扣减 st
                if (!st.isEmpty() && !player.getInventory().insertStack(st)) player.dropItem(st, false);
            }
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
            if (inserted > 0 && panel != null && !player.getWorld().isClient) { // m112 账本只在服务端扣
                panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), inserted);
                panel.refreshDisplay(); // 余量立刻回显
            }
            slot.setStack(ItemStack.EMPTY); // 展示格下个刷新周期重建
            return ItemStack.EMPTY;
        } else if (index >= DataPanelBlockEntity.PAGE + 36 && index < DataPanelBlockEntity.PAGE + 45) {
            // 合成格 → 背包
            this.insertItem(stack, DataPanelBlockEntity.PAGE, DataPanelBlockEntity.PAGE + 36, true);
            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            return ItemStack.EMPTY;
        } else if (index == DataPanelBlockEntity.PAGE + 45) {
            // m106b：shift 点结果格 = 连续合成一整组（学 AE2 CRAFT_SHIFT）。只在服务端跑，
            // 客户端不预测（m95 教训）；结果变化/背包塞不下即停；配合网络补料可一口气合到底。
            if (player.getWorld().isClient || panel == null || panel.getWorld() == null) return ItemStack.EMPTY;
            ItemStack first = craftResult.getStack(0);
            if (first.isEmpty()) return ItemStack.EMPTY;
            ItemStack want = first.copy();
            int per = Math.max(1, want.getCount());
            int times = Math.max(1, want.getMaxCount() / per); // 最多合一整组（AE2 同款上限）
            for (int n = 0; n < times; n++) {
                updateCraftResult(); // 补料后配方可能断，每轮重算
                ItemStack cur = craftResult.getStack(0);
                if (cur.isEmpty() || !ItemStack.areItemsAndComponentsEqual(want, cur)) break; // 结果变了即停
                ItemStack out = cur.copy();
                boolean any = this.insertItem(out, DataPanelBlockEntity.PAGE, DataPanelBlockEntity.PAGE + 36, true);
                if (!any) break;          // 一格都塞不进：不扣料直接停，结果留在格里
                consumeCraft(player);     // 塞进去了才扣料+网络补料+重算
                if (!out.isEmpty()) { player.dropItem(out, false); break; } // 只塞进一半：余量落脚下(AE2 同款)后停
            }
            return ItemStack.EMPTY;
        } else if (index >= DataPanelBlockEntity.PAGE + 45) {
            return ItemStack.EMPTY; // 回收格不 shift
        } else {
            // 玩家背包 → 存入面板
            if (player.getWorld().isClient) return ItemStack.EMPTY; // m112 存入零预测：客户端跑到这会用空账本刷屏（视频实锤的整页清空）
            if (panel != null) {
                // 带组件的物品（附魔/损耗/药水/成书等）拒存：仓储按 id 记账，存入会抹掉组件——宁可不动，绝不销毁数据
                if (!stack.getComponentChanges().isEmpty()) {
                    // m107c：此前静默拒收，玩家不知道为什么存不进——服务端 actionbar 说明原因（返回 EMPTY 循环即止，一次点击一条）
                    if (!player.getWorld().isClient) msg(player, "带附魔/耐久等组件的物品不入仓（防抹数据）");
                    return ItemStack.EMPTY;
                }
                ItemStack copy = stack.copy();
                panel.deposit(copy);
                // 只按实际存入量扣：无存储核心/类型满时余量留在原槽，绝不凭空消失
                if (copy.getCount() != stack.getCount()) {
                    slot.setStack(copy.isEmpty() ? ItemStack.EMPTY : copy);
                    slot.markDirty();
                    panel.refreshDisplay(); // m111 存完立刻可见
                }
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
}
