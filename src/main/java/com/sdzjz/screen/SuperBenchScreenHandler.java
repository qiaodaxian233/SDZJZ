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
            if (s.isEmpty()) continue;
            // m242 认包：压缩包按 内容物×倍率 折算成原版计数入集，包自身 id 不入集（裸包=0 不参与）。
            // 折算溢出防护：单格上限 64×4096=262144 远小于 int，但 144 格合计可到 3775 万仍在 int 内；
            // 工程款 BOM 最大单项 13 万级（litematic 实测 json），口径安全。
            long raw = com.sdzjz.item.CompressedPackItem.rawCount(s);
            if (raw > 0) m.merge(com.sdzjz.item.CompressedPackItem.innerId(s), (int) Math.min(raw, Integer.MAX_VALUE), Integer::sum);
            else if (!(s.getItem() instanceof com.sdzjz.item.CompressedPackItem))
                m.merge(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
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
            // m242 扣料认包：散件先扣，再整只扣压缩包（每只入账 倍率 件）。精确多重集匹配下
            // 总量==需求，理论无找零；防御性找零（need 非倍率整除）拆成散件回网格不白丢。
            for (int i = 0; i < GRID_SLOTS && need > 0; i++) {
                ItemStack s = input.getStack(i);
                if (s.isEmpty() || s.getItem() instanceof com.sdzjz.item.CompressedPackItem) continue;
                if (Registries.ITEM.getId(s.getItem()).toString().equals(e.getKey())) {
                    int take = Math.min(need, s.getCount());
                    s.decrement(take);
                    need -= take;
                }
            }
            for (int i = 0; i < GRID_SLOTS && need > 0; i++) {
                ItemStack s = input.getStack(i);
                if (!(s.getItem() instanceof com.sdzjz.item.CompressedPackItem pk)) continue;
                if (!e.getKey().equals(com.sdzjz.item.CompressedPackItem.innerId(s))) continue;
                while (need > 0 && !s.isEmpty()) {
                    s.decrement(1);
                    if (pk.ratio > need) { // 防御性找零：多出来的拆成散件回网格
                        ItemStack change = new ItemStack(Registries.ITEM.get(Identifier.of(e.getKey())), 1);
                        int left = pk.ratio - need;
                        while (left > 0) {
                            int chunk = Math.min(left, change.getMaxCount());
                            insertToGrid(change.copyWithCount(chunk));
                            left -= chunk;
                        }
                        need = 0;
                    } else need -= pk.ratio;
                }
                if (s.isEmpty()) input.setStack(i, ItemStack.EMPTY);
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

    // ===== m241 压缩区（方案A）：保留钮 id 远离配方下标域，复用既有 clickButton 通道零新协议 =====
    public static final int BTN_COMPRESS = 1_000_000; // 压缩网格材料（64→一级包，64一级→二级包，一次点击级联）
    public static final int BTN_UNPACK   = 1_000_001; // 拆开材料包（二级→一级→原物，只落网格、拆前查容量）

    /** 配方浏览器点击：把 #id 配方的材料从背包填入网格（先清空网格还给玩家）。 */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == BTN_COMPRESS) { if (!player.getWorld().isClient) compressGrid(player); return true; }
        if (id == BTN_UNPACK)   { if (!player.getWorld().isClient) unpackGrid(player);   return true; }
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
        // m244 打包填料分流：layout=null 的打包版 BOM 不铺蓝图——笼子照常搬，材料从背包
        // 按内容物贪心搬压缩包（二级→一级→散件），落网格自动堆叠。
        if (r.layout() == null) {
            for (ItemStack c : cages) {
                ItemStack rem = insertToGrid(c);
                if (!rem.isEmpty() && !player.getInventory().insertStack(rem)) player.dropItem(rem, false);
            }
            for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
                if (SuperBenchRecipes.CAGE_ID.equals(e.getKey())) continue;
                pullPacked(player, e.getKey(), e.getValue());
            }
            input.markDirty();
            sendMissingSummary(player, r);
            return true;
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

    /** m244 打包填料：为材料 id 凑 need 件原版计数——背包里 二级包→一级包→普通散件 三轮贪心，
     *  整只搬包不拆（need 为包整倍时刚好凑齐），落网格自动堆叠；塞不下（理论到不了，槽位账
     *  离线断言过）按倍率回账并还背包不落地。 */
    private void pullPacked(PlayerEntity player, String id, int need) {
        PlayerInventory pinv = player.getInventory();
        Item t2 = com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK, t1 = com.sdzjz.registry.ModItems.COMPRESSED_PACK;
        for (int pass = 0; pass < 3 && need > 0; pass++) {
            int ratio = pass == 0 ? 4096 : pass == 1 ? 64 : 1;
            Item want = pass == 0 ? t2 : pass == 1 ? t1 : null;
            for (int i = 0; i < pinv.size() && need >= ratio; i++) {
                ItemStack s = pinv.getStack(i);
                if (pass < 2) {
                    if (s.getItem() != want || !id.equals(com.sdzjz.item.CompressedPackItem.innerId(s))) continue;
                } else {
                    if (s.isEmpty() || s.getItem() instanceof com.sdzjz.item.CompressedPackItem) continue;
                    if (!s.getComponentChanges().isEmpty()) continue; // 组件件不当散料搬（附魔书等）
                    if (!Registries.ITEM.getId(s.getItem()).toString().equals(id)) continue;
                }
                int take = Math.min(need / ratio, s.getCount());
                if (take <= 0) continue;
                ItemStack moved = s.copyWithCount(take);
                s.decrement(take);
                if (s.isEmpty()) pinv.setStack(i, ItemStack.EMPTY);
                need -= take * ratio;
                ItemStack rem = insertToGrid(moved);
                if (!rem.isEmpty()) {
                    need += rem.getCount() * ratio;
                    if (!pinv.insertStack(rem)) player.dropItem(rem, false);
                }
            }
        }
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

    // ===== m241 压缩引擎（方案A，服务端权威）。口径：包=原版物品的数量压缩（作者拍板），
    // 只压"无组件差异的普通物品"（附魔书/药水等跳过，防压包抹组件——精确条目教训同源）。 =====

    /** 压缩：网格里同种普通物品每满 64 → 1 一级包；同内容一级包每满 64 → 1 二级包（一次点击级联）。 */
    private void compressGrid(PlayerEntity player) {
        long rawPacked = 0; int t1Made = 0, t2Made = 0;
        Map<String, Integer> plain = new HashMap<>();
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (s.isEmpty() || s.getItem() instanceof com.sdzjz.item.CompressedPackItem) continue;
            if (!s.getComponentChanges().isEmpty()) continue;
            plain.merge(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : plain.entrySet()) {
            int k = e.getValue() / 64;
            if (k <= 0) continue;
            takePlainFromGrid(e.getKey(), k * 64);
            giveToGridOrPlayer(player, com.sdzjz.item.CompressedPackItem.of(
                    com.sdzjz.registry.ModItems.COMPRESSED_PACK, e.getKey(), k));
            rawPacked += (long) k * 64; t1Made += k;
        }
        Map<String, Integer> packs = new HashMap<>();
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (s.getItem() == com.sdzjz.registry.ModItems.COMPRESSED_PACK) {
                String in = com.sdzjz.item.CompressedPackItem.innerId(s);
                if (in != null) packs.merge(in, s.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : packs.entrySet()) {
            int k = e.getValue() / 64;
            if (k <= 0) continue;
            takePacksFromGrid(e.getKey(), k * 64);
            giveToGridOrPlayer(player, com.sdzjz.item.CompressedPackItem.of(
                    com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK, e.getKey(), k));
            t2Made += k;
        }
        input.markDirty();
        if (t1Made == 0 && t2Made == 0) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "没有可压缩的（需网格里同种普通物品≥64，或同内容一级包≥64）")
                    .formatted(net.minecraft.util.Formatting.GRAY), false);
        } else {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "压缩完成: " + (t1Made > 0 ? "一级包+" + t1Made + "（收纳原物 " + rawPacked + " 件）" : "")
                    + (t1Made > 0 && t2Made > 0 ? "、" : "") + (t2Made > 0 ? "二级包+" + t2Made : ""))
                    .formatted(net.minecraft.util.Formatting.GREEN), false);
        }
    }

    /** 拆包（m246 重做，作者实测"压得了拆不了"）：二级→一级→原物逐包拆，产物**先落网格、
     *  溢出进背包**（旧版只落网格，网格一满整体罢工=看着像拆不了），两边都装不下才停；
     *  每次点击必报账（拆了多少/剩多少/为什么停），网格没包但背包有包时明说"把包放进网格"。 */
    private void unpackGrid(PlayerEntity player) {
        long t2Opened = 0, t1Opened = 0;
        boolean spaceOut = false;
        Item t2 = com.sdzjz.registry.ModItems.SUPER_COMPRESSED_PACK, t1 = com.sdzjz.registry.ModItems.COMPRESSED_PACK;
        for (int pass = 0; pass < 2; pass++) {
            Item packItem = pass == 0 ? t2 : t1;
            boolean passOut = false; // 空间账按层各算：超级包层堵住不连坐一级层（产物不同容量不同）
            boolean any = true;
            while (any && !passOut) {
                any = false;
                for (int i = 0; i < GRID_SLOTS && !passOut; i++) {
                    ItemStack s = input.getStack(i);
                    if (s.getItem() != packItem) continue;
                    String in = com.sdzjz.item.CompressedPackItem.innerId(s);
                    if (in == null) continue;
                    ItemStack proto = pass == 0
                            ? com.sdzjz.item.CompressedPackItem.of(t1, in, 1)
                            : new ItemStack(Registries.ITEM.get(Identifier.of(in)), 1);
                    if (capacityFor(player, proto) < 64) { passOut = true; spaceOut = true; break; } // 网格+背包合计
                    s.decrement(1);
                    if (s.isEmpty()) input.setStack(i, ItemStack.EMPTY);
                    int left = 64;
                    while (left > 0) {
                        int chunk = Math.min(left, proto.getMaxCount());
                        ItemStack rem = insertToGrid(proto.copyWithCount(chunk));
                        if (!rem.isEmpty() && !player.getInventory().insertStack(rem)) player.dropItem(rem, false); // 容量已核，兜底
                        left -= chunk;
                    }
                    if (pass == 0) t2Opened++; else t1Opened++;
                    any = true;
                }
            }
        }
        input.markDirty();
        // 每击必报账（旧版拆成功一声不吭，出问题只剩猜）
        long t2Left = 0, t1Left = 0;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (s.getItem() == t2) t2Left += s.getCount();
            else if (s.getItem() == t1) t1Left += s.getCount();
        }
        if (t2Opened + t1Opened > 0) {
            String msg = "已拆开: " + (t2Opened > 0 ? "超级包×" + t2Opened + "→一级包×" + (t2Opened * 64) : "")
                    + (t2Opened > 0 && t1Opened > 0 ? "、" : "")
                    + (t1Opened > 0 ? "一级包×" + t1Opened + "→原物×" + (t1Opened * 64) : "")
                    + (spaceOut ? "；网格+背包已满，剩 超级包×" + t2Left + "/一级包×" + t1Left + " 未拆" : "");
            player.sendMessage(net.minecraft.text.Text.literal(msg)
                    .formatted(spaceOut ? net.minecraft.util.Formatting.YELLOW : net.minecraft.util.Formatting.GREEN), false);
        } else if (spaceOut) {
            player.sendMessage(net.minecraft.text.Text.literal("网格和背包都装不下拆出来的东西——先腾地方再点")
                    .formatted(net.minecraft.util.Formatting.RED), false);
        } else {
            // 网格没包——背包里有包的话明说（作者实测最容易踩的坑：包放在背包区点拆开）
            boolean invHasPack = false;
            PlayerInventory pinv = player.getInventory();
            for (int i = 0; i < pinv.size() && !invHasPack; i++) {
                Item it = pinv.getStack(i).getItem();
                if (it == t1 || it == t2) invHasPack = true;
            }
            player.sendMessage(net.minecraft.text.Text.literal(invHasPack
                    ? "拆开只认网格里的包——把背包里的包放进左边网格再点"
                    : "网格里没有材料包").formatted(net.minecraft.util.Formatting.GRAY), false);
        }
    }

    /** 网格+玩家背包合计还能容纳多少件与 proto 同物同组件的东西（m246：拆包产物两级承接）。 */
    private int capacityFor(PlayerEntity player, ItemStack proto) {
        int cap = gridCapacityFor(proto);
        if (cap >= 64) return cap;
        PlayerInventory pinv = player.getInventory();
        for (int i = 0; i < pinv.main.size(); i++) { // 只数主背包 36 格（盔甲/副手不收料）
            ItemStack s = pinv.main.get(i);
            if (s.isEmpty()) cap += proto.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(s, proto)) cap += Math.max(0, s.getMaxCount() - s.getCount());
            if (cap >= 64) return cap;
        }
        return cap;
    }

    /** 从网格取走 n 件指定 id 的"普通物品"（无组件差异；调用方已核总量足够）。 */
    private void takePlainFromGrid(String id, int n) {
        for (int i = 0; i < GRID_SLOTS && n > 0; i++) {
            ItemStack s = input.getStack(i);
            if (s.isEmpty() || s.getItem() instanceof com.sdzjz.item.CompressedPackItem) continue;
            if (!s.getComponentChanges().isEmpty()) continue;
            if (!Registries.ITEM.getId(s.getItem()).toString().equals(id)) continue;
            int take = Math.min(n, s.getCount());
            s.decrement(take); n -= take;
            if (s.isEmpty()) input.setStack(i, ItemStack.EMPTY);
        }
    }

    /** 从网格取走 n 个装着 innerId 的一级包。 */
    private void takePacksFromGrid(String innerId, int n) {
        for (int i = 0; i < GRID_SLOTS && n > 0; i++) {
            ItemStack s = input.getStack(i);
            if (s.getItem() != com.sdzjz.registry.ModItems.COMPRESSED_PACK) continue;
            if (!innerId.equals(com.sdzjz.item.CompressedPackItem.innerId(s))) continue;
            int take = Math.min(n, s.getCount());
            s.decrement(take); n -= take;
            if (s.isEmpty()) input.setStack(i, ItemStack.EMPTY);
        }
    }

    /** 网格还能容纳多少件与 proto 同物同组件的东西（空格按 maxCount 计，同栈按余量计）。 */
    private int gridCapacityFor(ItemStack proto) {
        int cap = 0;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getStack(i);
            if (s.isEmpty()) cap += proto.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(s, proto)) cap += Math.max(0, s.getMaxCount() - s.getCount());
            if (cap >= 64) return cap; // 够本次拆包就不必扫完
        }
        return cap;
    }

    /** 插入网格：先并同物同组件栈，再落空格；返回没塞下的余量（可能为空栈）。 */
    private ItemStack insertToGrid(ItemStack st) {
        for (int i = 0; i < GRID_SLOTS && !st.isEmpty(); i++) {
            ItemStack s = input.getStack(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, st)) {
                int room = s.getMaxCount() - s.getCount();
                if (room > 0) { int mv = Math.min(room, st.getCount()); s.increment(mv); st.decrement(mv); }
            }
        }
        for (int i = 0; i < GRID_SLOTS && !st.isEmpty(); i++) {
            if (input.getStack(i).isEmpty()) {
                int mv = Math.min(st.getMaxCount(), st.getCount());
                input.setStack(i, st.copyWithCount(mv));
                st.decrement(mv);
            }
        }
        return st;
    }

    /** 压缩产物给回：优先网格，塞不下进背包，再不行落地（与关屏散落同口径）。 */
    private void giveToGridOrPlayer(PlayerEntity player, ItemStack st) {
        ItemStack rem = insertToGrid(st);
        if (!rem.isEmpty() && !player.getInventory().insertStack(rem)) player.dropItem(rem, false);
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
