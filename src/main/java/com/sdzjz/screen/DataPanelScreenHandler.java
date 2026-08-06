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
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** 数据面板 GUI：54 展示格（只可取出，取出即从逻辑仓储扣数）+ 玩家背包（可 shift 存入面板）。
 *  m201 接原版工作台接口：继承 AbstractRecipeScreenHandler（原版配方书 CraftRequest 协议直通），
 *  槽序重排为 合成0..8/结果9/展示10..63/背包64..90/快捷91..99/回收100——原版填料器 PlaceRecipe
 *  硬性假设合成格占句柄前排下标（跳过结果位），不排前排=填进展示格。索引常量为唯一口径。 */
public class DataPanelScreenHandler extends net.minecraft.screen.AbstractRecipeScreenHandler<
        net.minecraft.recipe.input.CraftingRecipeInput, net.minecraft.recipe.CraftingRecipe> {

    // m201 槽序唯一口径（screen 同用，别再写裸数字）
    public static final int CRAFT0 = 0, RESULT = 9, DISP0 = 10;
    public static final int INV0 = DISP0 + DataPanelBlockEntity.PAGE; // 64
    public static final int TRASH = INV0 + 36;                        // 100

    private final DataPanelBlockEntity panel;
    private final BlockPos blockPos;
    private final PlayerEntity player;                                  // m201 matches() 要世界；onButtonClick 的 player 形参不与此混用
    private final Inventory display;                                    // m201 身份判定用（canInsertIntoSlot 撤下标依赖）
    private final CraftGridInventory craft;                             // m126a：BE 常驻网格（AE2 式模板）；m201 换挂 RecipeInputInventory
    private final SimpleInventory craftResult = new SimpleInventory(1);
    private final SimpleInventory trash = new SimpleInventory(1);       // 回收格：放入即销毁
    // m126a：共享 BE 网格必须可注销监听——匿名 lambda 无法 remove，每开一次界面就泄漏一个引旧 handler 的监听器
    private final net.minecraft.inventory.InventoryChangedListener craftListener = inv -> updateCraftResult();

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    public DataPanelScreenHandler(int syncId, PlayerInventory playerInv, DataPanelBlockEntity be) {
        super(ModScreenHandlers.DATA_PANEL, syncId);
        this.panel = be;
        this.blockPos = (be != null) ? be.getPos() : null;
        this.player = playerInv.player;
        this.craft = (be != null) ? be.craftGrid : new CraftGridInventory(); // 客户端 BE 同样有实例，槽位同步写它
        this.display = (be != null) ? be.display : new SimpleInventory(DataPanelBlockEntity.PAGE);
        // ===== m201 合成区排前排（0..8 + 结果9，原版填料器口径）=====
        craft.addListener(craftListener);
        trash.addListener(inv -> { if (!trash.getStack(0).isEmpty()) trash.setStack(0, ItemStack.EMPTY); }); // 放入即销毁
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                this.addSlot(new Slot(craft, c + r * 3, 213 + c * 18, 96 + r * 18));
        // 结果格=原版 CraftingResultSlot 子类：EMI CoercedRecipeHandler 按 instanceof 认它（零插件点亮）；
        // onTakeItem 全量覆写走自家 consumeCraft（扣料+网络补料），绝不调 super——原版体内是本地扣格，会二次扣料。
        this.addSlot(new net.minecraft.screen.slot.CraftingResultSlot(playerInv.player, craft, craftResult, 0, 309, 114) {
            @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                consumeCraft(player);
                this.markDirty(); // 原版 Slot.onTakeItem 的收尾语义
            }
            // m127b：结果格整取或不取——取一半也触发 consumeCraft 扣整份料，随后重算把格内剩余覆盖成
            // 满结果=玩家白丢差额。原版右键取半/近满同类光标/Q键 三条部分取出路径在此焊死；双端同跑零闪烁。
            @Override public java.util.Optional<ItemStack> tryTakeStackRange(int min, int max, PlayerEntity player) {
                ItemStack st = this.getStack();
                if (!st.isEmpty() && Math.min(min, max) < st.getCount()) return java.util.Optional.empty();
                return super.tryTakeStackRange(min, max, player);
            }
        });

        // 展示区 6×9（只取不放）
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = c + r * 9;
                this.addSlot(new Slot(display, idx, 16 + c * 18, 52 + r * 18) {
                    @Override public boolean canInsert(ItemStack s) { return false; }
                    @Override public void onTakeItem(PlayerEntity player, ItemStack stack) {
                        // m112：账本只在服务端动（m95 铁律）。此钩子客户端预测也会跑——客户端 BE 账本是空的，
                        // 在这里 withdraw/钳数会按空账把光标预测成 0，还会污染客户端核心读数。
                        if (!player.getWorld().isClient && panel != null && !stack.isEmpty()) {
                            ItemStack tpl = stack.copy(); // m130：剥掉 amt 即真身模板——普通/精确统一判别
                            stripAmt(tpl);
                            int got = tpl.getComponentChanges().isEmpty()
                                    ? panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount())
                                    : panel.withdrawExact(tpl, stack.getCount());
                            // m111：网络实收多少给多少——展示栈在 10t 刷新窗口内可能过期，绝不超发凭空造物
                            if (got < stack.getCount()) stack.setCount(Math.max(0, got));
                            panel.refreshDisplay(); // 取走后余量立刻回显，格子不再空 0.5s（AE 手感）
                        }
                        // m130：剥掉展示用的 amt 数量标签（精确件保留自身组件——附魔/损耗/阶位原样带走）
                        stripAmt(stack);
                        super.onTakeItem(player, stack);
                    }
                });
            }
        }
        // 玩家背包
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 16 + c * 18, 181 + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 16 + c * 18, 237));
        // m84b 回收格（m201 合成区已前移至下标 0..9，此处只剩回收殿后=下标 100）
        this.addSlot(new Slot(trash, 0, 269, 202));
        this.addProperties(xpProps); // m80c 经验库同步（双属性防 short 截断：id0=低16位 id1=高15位）
        // m107a：服务端登记查看者（打开即刷一次，闲置面板不再空转 BFS）；客户端构造 resolve 出的是客户端 BE，不计数
        if (be != null && be.getWorld() != null && !be.getWorld().isClient) be.addViewer();
        updateCraftResult(); // m126a：网格常驻后开界面可能已带配方——立即出结果，不等首次改动（内部自带客户端守卫）
    }

    // ===== m84b 合成终端（ME 风格：终端里直接手动合成）=====
    private net.minecraft.recipe.input.CraftingRecipeInput craftInput() {
        java.util.List<ItemStack> l = new java.util.ArrayList<>(9);
        for (int i = 0; i < 9; i++) l.add(craft.getStack(i));
        return net.minecraft.recipe.input.CraftingRecipeInput.create(3, 3, l);
    }

    // m126b：配方查找缓存（学 AE2 currentRecipe：上次命中的配方仍 matches 就直接用，不再全表扫）。
    // 此前 shift 合一整组=每轮 updateCraftResult+consumeCraft 各一次 getFirstMatch，64 连 130+ 趟全表扫描。
    private net.minecraft.recipe.RecipeEntry<net.minecraft.recipe.CraftingRecipe> cachedRecipe;

    private java.util.Optional<net.minecraft.recipe.RecipeEntry<net.minecraft.recipe.CraftingRecipe>> findRecipe(
            net.minecraft.recipe.input.CraftingRecipeInput input, net.minecraft.world.World w) {
        if (cachedRecipe != null && cachedRecipe.value().matches(input, w)) return java.util.Optional.of(cachedRecipe);
        var m = w.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, w);
        cachedRecipe = m.orElse(null);
        return m;
    }

    private void updateCraftResult() {
        if (panel == null || panel.getWorld() == null || panel.getWorld().isClient) return;
        var w = panel.getWorld();
        var input = craftInput();
        craftResult.setStack(0, findRecipe(input, w)
                .map(e -> e.value().craft(input, w.getRegistryManager())).orElse(ItemStack.EMPTY));
    }

    /** 取走结果：每格扣 1，容器残留(桶等)留在原格或还给玩家，然后 AE 式从网络补料并重算结果。 */
    private void consumeCraft(PlayerEntity player) {
        if (panel == null || panel.getWorld() == null || panel.getWorld().isClient) return; // m95 同款：只在服务端扣料，客户端等同步纠正
        var w = panel.getWorld();
        var input = craftInput();
        var match = findRecipe(input, w); // m126b：走缓存，shift/整组连打不再逐轮全表扫
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

    /** m212 JEI"+"填料（服务端权威）：仓储优先取料、背包兜底；网格里不匹配的先退回背包（原版清格语义）。
     *  自家 C2S 包驱动（JeiFillPayload），不依赖 JEI 的服务端搬运——专用服务器无需装 JEI。 */
    public void jeiFill(net.minecraft.server.network.ServerPlayerEntity player, net.minecraft.util.Identifier rid, boolean max) {
        if (panel == null || panel.getWorld() == null || panel.getWorld().isClient) return; // 服务端权威（m95 口径）
        var w = panel.getWorld();
        var entry = w.getRecipeManager().get(rid).orElse(null);
        if (entry == null || !(entry.value() instanceof net.minecraft.recipe.CraftingRecipe cr)) return;
        var ings = cr.getIngredients();
        net.minecraft.recipe.Ingredient[] want = new net.minecraft.recipe.Ingredient[9]; // 展平 3×3：有形按宽高左上对齐，无形顺排
        if (cr instanceof net.minecraft.recipe.ShapedRecipe sr) {
            int rw = sr.getWidth(), rh = sr.getHeight();
            for (int r = 0; r < rh && r < 3; r++)
                for (int c2 = 0; c2 < rw && c2 < 3; c2++)
                    if (r * rw + c2 < ings.size()) want[r * 3 + c2] = ings.get(r * rw + c2);
        } else {
            for (int i = 0; i < Math.min(9, ings.size()); i++) want[i] = ings.get(i);
        }
        var cores = com.sdzjz.block.StorageCoreBlockEntity.connectedCores(w, blockPos);
        // ① 清场：不匹配的退回背包（原版清格语义，不入仓防组件抹除）
        for (int i = 0; i < 9; i++) {
            net.minecraft.recipe.Ingredient ing = want[i];
            ItemStack cur = craft.getStack(i);
            boolean blank = ing == null || ing.getMatchingStacks().length == 0;
            if (!cur.isEmpty() && (blank || !ing.test(cur))) {
                if (!player.getInventory().insertStack(cur)) player.dropItem(cur, false);
                craft.setStack(i, ItemStack.EMPTY);
            }
        }
        // ② 按原料分组（键=候选材质首选项的物品 id；同 3×3 里同一原料共池均分——m213 修"第一格吞光"）
        java.util.LinkedHashMap<String, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            net.minecraft.recipe.Ingredient ing = want[i];
            if (ing == null || ing.getMatchingStacks().length == 0) continue;
            ItemStack kept = craft.getStack(i);
            String key = !kept.isEmpty() ? Registries.ITEM.getId(kept.getItem()).toString()
                    : Registries.ITEM.getId(ing.getMatchingStacks()[0].getItem()).toString();
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(i);
        }
        int missing = 0;
        for (var ge : groups.entrySet()) {
            java.util.List<Integer> slots = ge.getValue();
            net.minecraft.recipe.Ingredient ing = want[slots.get(0)];
            // 组内材质：已留同款则钉死；否则逐候选试取（木板类多材质配方靠这层兜住）
            ItemStack chosen = null;
            for (int si : slots) if (!craft.getStack(si).isEmpty()) { chosen = craft.getStack(si); break; }
            int n = slots.size();
            long pool = 0; ItemStack cand = null;
            java.util.List<ItemStack> tryList = chosen != null ? java.util.List.of(chosen)
                    : java.util.List.of(ing.getMatchingStacks());
            for (ItemStack c2 : tryList) {
                int cap0 = max ? c2.getMaxCount() : 1;
                long req = 0;
                for (int si : slots) req += Math.max(0, cap0 - craft.getStack(si).getCount());
                if (req <= 0) { cand = c2; break; }
                long got = pullFor(player, cores, c2, (int) Math.min(Integer.MAX_VALUE, req));
                if (got > 0 || chosen != null) { cand = c2; pool = got; break; }
            }
            if (cand == null) { missing += n; continue; }
            int cap = max ? cand.getMaxCount() : 1;
            // ③ 均分：二分最大公平水位 T（Σmax(0,T-have)≤pool 且 T≤cap），已高于 T 的格不动
            long[] have = new long[n];
            for (int k = 0; k < n; k++) have[k] = craft.getStack(slots.get(k)).getCount();
            long lo = 0, hi = cap;
            while (lo < hi) {
                long mid = (lo + hi + 1) >>> 1, needSum = 0;
                for (long h : have) needSum += Math.max(0, mid - h);
                if (needSum <= pool) lo = mid; else hi = mid - 1;
            }
            long left = pool;
            for (int k = 0; k < n; k++) {
                long give = Math.max(0, lo - have[k]);
                if (give <= 0) continue;
                left -= give;
                ItemStack cur = craft.getStack(slots.get(k));
                if (cur.isEmpty()) craft.setStack(slots.get(k), new ItemStack(cand.getItem(), (int) give));
                else cur.increment((int) give);
            }
            // ④ 余量回流：背包 → 仓储 → 落地（候选皆无组件，入仓安全；类型已存在不会撞类型闸）
            while (left > 0) {
                int chunk = (int) Math.min(left, cand.getMaxCount());
                ItemStack back = new ItemStack(cand.getItem(), chunk);
                player.getInventory().insertStack(back);
                if (!back.isEmpty() && !cores.isEmpty()) cores.get(0).deposit(back);
                if (!back.isEmpty()) player.dropItem(back, false);
                left -= chunk;
            }
            for (int si : slots) if (craft.getStack(si).isEmpty()) missing++;
        }
        updateCraftResult();
        sendContentUpdates();
        if (missing > 0) player.sendMessage(net.minecraft.text.Text.literal("JEI 填料：缺 " + missing + " 格材料（仓储+背包都没有）"), true);
    }

    /** m212 取料原语：仓储按 id 取（只对无组件候选，与 m106 补料同口径）→ 背包同款无组件兜底。 */
    private int pullFor(net.minecraft.server.network.ServerPlayerEntity p,
                        java.util.List<com.sdzjz.block.StorageCoreBlockEntity> cores, ItemStack cand, int n) {
        int got = 0;
        if (cand.getComponentChanges().isEmpty()) {
            String id = Registries.ITEM.getId(cand.getItem()).toString();
            for (var core : cores) { if (got >= n) break; got += core.withdraw(id, n - got); }
        }
        var inv = p.getInventory();
        for (int s2 = 0; s2 < inv.size() && got < n; s2++) {
            ItemStack st = inv.getStack(s2);
            if (!st.isEmpty() && st.isOf(cand.getItem()) && st.getComponentChanges().isEmpty()) {
                int take = Math.min(n - got, st.getCount());
                st.decrement(take); got += take;
            }
        }
        return got;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        craft.removeListener(craftListener); // m126a：双端都注销——共享 BE 网格，不注销=重开累积泄漏监听器
        if (player.getWorld().isClient) return;
        if (panel != null) panel.removeViewer(); // m107a：注销查看者（断线也走 onClosed，不泄漏）
        // m126a：网格常驻 BE（AE2 式模板），关界面不再回背包——配方摆一次永远在，重开接着合
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
            if (slotIdx < DISP0 || slotIdx >= INV0 || k > 8) return false; // m201 展示区=10..63（句柄下标口径）
            ItemStack disp = this.slots.get(slotIdx).getStack();
            if (disp.isEmpty()) return true;
            ItemStack tpl = disp.copy(); // m130：剥 amt 即真身——精确件按模板取，普通件按 id 取
            stripAmt(tpl);
            boolean exact = !tpl.getComponentChanges().isEmpty();
            String idStr = Registries.ITEM.getId(disp.getItem()).toString();
            int maxStack = Math.max(1, disp.getItem().getMaxCount());
            int[] fixed = {1, 8, 16, 32, 64};
            long want = k < 5 ? fixed[k] : (k < 8 ? (long) maxStack * (2L << (k - 5)) : Long.MAX_VALUE); // 5→2组 6→4组 7→8组 8→填满
            long given = 0;
            while (want > 0) {
                int chunk = (int) Math.min(want, maxStack);
                int got = exact ? panel.withdrawExact(tpl, chunk) : panel.withdraw(idStr, chunk);
                if (got <= 0) break; // 仓储见底
                ItemStack give = exact ? tpl.copyWithCount(got) : new ItemStack(disp.getItem(), got);
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
            // m130：带组件物品进精确账本（deposit 自动分流），拒收闸门拆除
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
        if (id == 6) { // m126b AE CRAFT_STACK：右键结果格=连续合成一整组到光标（服务端权威零预测，m95 同款）
            ItemStack want = craftResult.getStack(0).copy();
            if (want.isEmpty()) return true;
            ItemStack cursor = this.getCursorStack();
            if (!cursor.isEmpty() && !ItemStack.areItemsAndComponentsEqual(cursor, want)) return true; // 光标异类：不动
            int per = Math.max(1, want.getCount());
            int rounds = 0; // m163c 大堆叠护栏：光标"整组"随 ItemStackProMax 类模组暴涨到百万时，该循环=冻死；
                            // 4096 轮封顶（原版 64 上限下最多 64 轮永不触发），没装满再点一次续装即可
            while (rounds++ < 4096) {
                updateCraftResult(); // 补料后配方可能断，每轮重算（m106b 同款）
                ItemStack cur = craftResult.getStack(0);
                if (cur.isEmpty() || !ItemStack.areItemsAndComponentsEqual(want, cur)) break; // 结果变了即停
                int space = cursor.isEmpty() ? want.getMaxCount() : cursor.getMaxCount() - cursor.getCount();
                if (space < per) break; // 光标装不下下一轮产出即停，绝不超装（单产>堆叠上限的怪配方走左键单取）
                if (cursor.isEmpty()) cursor = cur.copy(); else cursor.increment(cur.getCount());
                consumeCraft(player); // 扣料+网络补料+重算
            }
            this.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor); // 光标同步走原版 cursor 跟踪（m111 同通道）
            return true;
        }
        if (id == 3) { // m107c 清空合成网格：回网络（m130 起带组件也可入仓），无核心的余量回背包，绝不落地销毁
            for (int i = 0; i < 9; i++) {
                ItemStack st = craft.getStack(i);
                if (st.isEmpty()) continue;
                craft.setStack(i, ItemStack.EMPTY);
                panel.deposit(st); // deposit 按实际存入量扣减 st；带组件自动分流精确账本
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

    /** m130：从展示栈剥掉数量标签 "amt"——展示栈=真身+amt 注入，剥后即还原真身：
     *  普通物品剥后归零组件可正常堆叠（与旧行为一致）；精确件保留自身其余组件（附魔/损耗/阶位不丢）。 */
    private static void stripAmt(ItemStack stack) {
        var nc = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (nc == null) return;
        net.minecraft.nbt.NbtCompound n = nc.copyNbt();
        n.remove("amt");
        if (n.isEmpty()) stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        else stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(n));
    }

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

    // m127b：双击收集(PICKUP_ALL)绝缘名单——原版该路径走 takeStack 直取，绕过 tryTakeStackRange 的整取防线：
    // ①结果格：部分吸取=扣整份料丢差额（原版 CraftingScreenHandler 排除 result 的同款语义）；
    // ②展示格：常规光标因 amt 组件不相等吸不走，但创造中键 CLONE 出的光标带同款组件可绕开
    // onTakeItem 正门外的账本钳数。本方法另参与拖拽落格判定，两类格 canInsert 本就 false，零行为变化。
    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != craftResult && slot.inventory != display; // m201 撤下标依赖，按库存身份判
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();

        if (index >= DISP0 && index < INV0) { // m201 展示区=10..63
            // m266 P0 复制窗修复（外部审计报告）：**先取账本、再按实取量塞背包**。
            // 旧序=先塞背包→按塞入量补扣账本，忽略 withdraw 实际返回值——展示格是 10t 节拍的
            // 缓存快照，账本已被别人取空时（两个面板/两个玩家同 tick 抢最后一组，各有各的缓存）
            // 玩家照样先拿到 64 个，账本只扣得到 0~10=凭空复制。普通单击路径当年查了实取量，
            // shift 路径没查。现在改成账本权威：取多少给多少，塞不下的原路退回账本绝不落地。
            // 顺序安全性：先扣后塞的老风险（背包满=物品消失）由末尾 deposit 回退兜住。
            ItemStack clean = stack.copy(); // m130：剥 amt 即真身——精确件保留组件，普通件归零组件（双端同算）
            stripAmt(clean);
            if (player.getWorld().isClient || panel == null) { // 客户端不预测（m95 教训）：账本只在服务端
                slot.setStack(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
            int want = Math.min(clean.getCount(), clean.getMaxCount()); // 展示数已是一格量，再钳一道
            int got = clean.getComponentChanges().isEmpty()
                    ? panel.withdraw(Registries.ITEM.getId(stack.getItem()).toString(), want)
                    : panel.withdrawExact(clean.copy(), want);
            if (got <= 0) { // 账本已空：什么都不给，立刻刷新让展示缓存跟上真相
                panel.refreshDisplay();
                slot.setStack(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
            ItemStack giving = clean.copyWithCount(got);
            this.insertItem(giving, INV0, TRASH, true);
            if (!giving.isEmpty()) panel.deposit(giving); // 背包塞不下的余量原路退回账本（绝不落地）
            panel.refreshDisplay(); // 余量立刻回显
            slot.setStack(ItemStack.EMPTY); // 展示格下个刷新周期重建
            return ItemStack.EMPTY;
        } else if (index >= CRAFT0 && index < RESULT) {
            // 合成格 → 背包
            this.insertItem(stack, INV0, TRASH, true);
            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            return ItemStack.EMPTY;
        } else if (index == RESULT) {
            // m106b：shift 点结果格 = 连续合成一整组（学 AE2 CRAFT_SHIFT）。只在服务端跑，
            // 客户端不预测（m95 教训）；结果变化/背包塞不下即停；配合网络补料可一口气合到底。
            if (player.getWorld().isClient || panel == null || panel.getWorld() == null) return ItemStack.EMPTY;
            ItemStack first = craftResult.getStack(0);
            if (first.isEmpty()) return ItemStack.EMPTY;
            ItemStack want = first.copy();
            int per = Math.max(1, want.getCount());
            // m163c 大堆叠护栏：堆叠上限模组（用户装着 ItemStackProMax）把 maxCount 提到十万/百万级后，
            // "合到一整组"=单次 shift 点击百万轮 updateCraftResult+consumeCraft，服务器当场冻死。
            // 4096 轮封顶——原版 64 上限下 times≤64 永不触发，行为零变化；大堆叠下一次点击最多
            // 4096 轮（还要更多再点/交给自动合成机，量产本就是它的活）。
            int times = (int) Math.min(Math.max(1, (long) want.getMaxCount() / per), 4096);
            for (int n = 0; n < times; n++) {
                updateCraftResult(); // 补料后配方可能断，每轮重算
                ItemStack cur = craftResult.getStack(0);
                if (cur.isEmpty() || !ItemStack.areItemsAndComponentsEqual(want, cur)) break; // 结果变了即停
                ItemStack out = cur.copy();
                boolean any = this.insertItem(out, INV0, TRASH, true);
                if (!any) break;          // 一格都塞不进：不扣料直接停，结果留在格里
                consumeCraft(player);     // 塞进去了才扣料+网络补料+重算
                if (!out.isEmpty()) { player.dropItem(out, false); break; } // 只塞进一半：余量落脚下(AE2 同款)后停
            }
            return ItemStack.EMPTY;
        } else if (index == TRASH) {
            return ItemStack.EMPTY; // 回收格不 shift
        } else {
            // 玩家背包 → 存入面板
            if (player.getWorld().isClient) return ItemStack.EMPTY; // m112 存入零预测：客户端跑到这会用空账本刷屏（视频实锤的整页清空）
            if (panel != null) {
                // m130：带组件的物品（附魔/损耗/药水/带阶位机器）自动进精确账本，组件原样保存——
                // m107c 的"不入仓"拒收闸门就此拆除。
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

    // ===== m201 原版工作台接口实现（AbstractRecipeScreenHandler；名称全按 Yarn 1.21.1 官方映射核过）=====
    @Override
    public void populateRecipeFinder(net.minecraft.recipe.RecipeMatcher finder) {
        craft.provideRecipeInputs(finder); // CraftGridInventory 照原版 CraftingInventory 逐格 addUnenchantedInput
    }

    @Override
    public void clearCraftingSlots() { // 原版填料器清格前已把物品移回背包（clearGrid），此处只清格
        craft.clear(); // 触发监听→updateCraftResult
        craftResult.clear();
    }

    @Override
    public boolean matches(net.minecraft.recipe.RecipeEntry<net.minecraft.recipe.CraftingRecipe> recipe) {
        return recipe.value().matches(craftInput(), player.getWorld());
    }

    @Override
    public int getCraftingResultSlotIndex() { return RESULT; }

    @Override
    public int getCraftingWidth() { return 3; }

    @Override
    public int getCraftingHeight() { return 3; }

    @Override
    public int getCraftingSlotCount() { return 10; } // 网格9+结果1（原版 CraftingScreenHandler 同口径）

    @Override
    public net.minecraft.recipe.book.RecipeBookCategory getCategory() {
        return net.minecraft.recipe.book.RecipeBookCategory.CRAFTING;
    }

    @Override
    public boolean canInsertIntoSlot(int index) { // 原版语义：清格时哪些前排格该移回背包
        return index != RESULT;
    }

    @Override
    public void onInputSlotFillStart() {}

    @Override
    public void onInputSlotFillFinish(net.minecraft.recipe.RecipeEntry<net.minecraft.recipe.CraftingRecipe> recipe) {
        updateCraftResult(); // 填完立即出结果（监听逐格也会触发，这里兜底一次）
    }
}
