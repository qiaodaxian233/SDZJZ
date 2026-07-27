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
                // m95：扣料只在服务端执行。原版 container_click 包上报"本次点击改动的槽位"，
                // 协议硬上限 128 个；客户端本地预测若同时扣 144 格材料，144网格+1结果=145 个改动槽
                // 直接超限 → EncoderException 断线（m61 配方铺满 140~144 格后取成品必炸）。
                // 客户端这次点击只动结果槽（1~2 槽）；服务端扣料后经槽位同步把网格纠正回来。
                if (!player.getWorld().isClient) consumeIngredients();
                super.onTakeItem(player, stack);
            }
            // m127b：整取或不取——右键取半/Q键取1 也会 consumeIngredients 扣整份料，
            // onContentChanged 再把剩余覆盖成满结果=白丢差额（与终端结果格同族漏洞，同款焊法）。
            @Override public java.util.Optional<ItemStack> tryTakeStackRange(int min, int max, PlayerEntity player) {
                ItemStack st = this.getStack();
                if (!st.isEmpty() && Math.min(min, max) < st.getCount()) return java.util.Optional.empty();
                return super.tryTakeStackRange(min, max, player);
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

    /** 刷怪类配方：每种指定生物都要有一个「装着它」的抓物笼子（空笼/装错生物都不行；m166 支持多生物，如刷铁机=村民+僵尸）。 */
    private boolean mobOk(SuperBenchRecipes.Recipe r) {
        if (r == null) return false;
        for (String mob : r.mobs()) {
            boolean found = false;
            for (int i = 0; i < GRID_SLOTS && !found; i++) {
                ItemStack s = input.getStack(i);
                if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                        && mob.equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) found = true;
            }
            if (!found) return false;
        }
        return true;
    }

    private void consumeIngredients() {
        SuperBenchRecipes.Recipe r = SuperBenchRecipes.match(gridMultiset());
        if (r == null || !mobOk(r)) return;
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            int need = e.getValue();
            if (SuperBenchRecipes.CAGE_ID.equals(e.getKey()) && !r.mobs().isEmpty()) {
                // 笼子不消耗：生物「装进」机器，清 NBT 留空笼在网格里（m166 多生物=每种清一个对应的笼）
                for (String mob : r.mobs()) {
                    for (int i = 0; i < GRID_SLOTS; i++) {
                        ItemStack s = input.getStack(i);
                        if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                                && mob.equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) {
                            s.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                            s.remove(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
                            break;
                        }
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

    // m127b：双击收集(PICKUP_ALL)对结果格绝缘——该路径 takeStack 直取可部分吸走结果，
    // 每吸一口都扣整份 144 格配方料（原版 CraftingScreenHandler 排除 result 的同款语义）。
    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != result;
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
        // 刷怪类：每种生物从背包找「装着它」的那个笼子（整个带 NBT 搬过来，不能造新的；m166 多生物逐只找）
        java.util.List<ItemStack> cages = new java.util.ArrayList<>();
        for (String mob : r.mobs()) {
            PlayerInventory pinv = player.getInventory();
            for (int i = 0; i < pinv.size(); i++) {
                ItemStack s = pinv.getStack(i);
                if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                        && mob.equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) {
                    cages.add(s.copyWithCount(1)); // 只搬 1 只（多重集精确匹配要求 每生物×1）
                    s.decrement(1);
                    if (s.isEmpty()) pinv.setStack(i, ItemStack.EMPTY);
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
                if (!cages.isEmpty()) input.setStack(i, cages.remove(0));
                continue;
            }
            int have = pool.getOrDefault(want, 0);
            if (have > 0) {
                input.setStack(i, new ItemStack(Registries.ITEM.get(Identifier.of(want)), 1));
                pool.put(want, have - 1);
            }
        }
        for (ItemStack c : cages) { if (!player.getInventory().insertStack(c)) player.dropItem(c, false); }
        input.markDirty();
        sendMissingSummary(player, r); // 填完统计缺什么，聊天栏直说，不再"点了没反应"
        return true;
    }

    /** 填料后核对网格 vs 配方：缺什么、缺几个，发聊天消息；齐了发"就绪"。m166 多生物逐只报缺。 */
    private void sendMissingSummary(PlayerEntity player, SuperBenchRecipes.Recipe r) {
        Map<String, Integer> grid = gridMultiset();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            int lack = e.getValue() - grid.getOrDefault(e.getKey(), 0);
            if (lack <= 0) continue;
            if (SuperBenchRecipes.CAGE_ID.equals(e.getKey())) continue; // 笼子按生物逐只报，见下
            Item it = Registries.ITEM.get(Identifier.of(e.getKey()));
            missing.add(it.getName().getString() + "×" + lack);
        }
        java.util.List<String> cageMiss = new java.util.ArrayList<>(); // 缺笼或装错生物的，报生物名
        for (String mob : r.mobs()) {
            boolean found = false;
            for (int i = 0; i < GRID_SLOTS && !found; i++) {
                ItemStack s = input.getStack(i);
                if (s.getItem() instanceof com.sdzjz.item.CaptureCageItem
                        && mob.equals(com.sdzjz.item.CaptureCageItem.cagedType(s))) found = true;
            }
            if (!found) {
                String mn;
                try { mn = net.minecraft.registry.Registries.ENTITY_TYPE
                        .get(Identifier.of(mob)).getName().getString(); }
                catch (Exception ex) { mn = mob; }
                cageMiss.add(mn);
            }
        }
        if (missing.isEmpty() && cageMiss.isEmpty()) {
            player.sendMessage(net.minecraft.text.Text.literal("材料齐全，取走结果即可")
                    .formatted(net.minecraft.util.Formatting.GREEN), false);
            return;
        }
        net.minecraft.text.MutableText msg = net.minecraft.text.Text.literal("还缺: ")
                .formatted(net.minecraft.util.Formatting.RED);
        if (!cageMiss.isEmpty()) {
            msg.append(net.minecraft.text.Text.literal("装着[" + String.join("/", cageMiss)
                    + "]的抓物笼子（每种一只，去抓）" + (missing.isEmpty() ? "" : "、")));
        }
        int shown = Math.min(missing.size(), 6);
        msg.append(net.minecraft.text.Text.literal(String.join("、", missing.subList(0, shown))
                + (missing.size() > shown ? " 等" + missing.size() + "项" : "")));
        player.sendMessage(msg, false);
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
