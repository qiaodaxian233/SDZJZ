package com.sdzjz.screen;

import com.sdzjz.machine.SuperBenchRecipes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** 超大工作台：12×12 输入网格 + 1 结果槽 + 玩家背包；无形状(多重集精确)匹配机器配方。 */
public class SuperBenchScreenHandler extends AbstractContainerMenu {

    public static final int GRID = 12;
    public static final int GRID_SLOTS = GRID * GRID; // 144
    public static final int RESULT_INDEX = GRID_SLOTS;

    // ===== m523（SB2b）世代口两件：菜单类型安装口 + 物品宿主口 =====
    // m522b 血案：本类挂 1.20.1 白名单后作者 1.20.1 构建当场红——`super(ModScreenHandlers.SUPER_BENCH, id)` 引 src 的注册表类
    // （"程序包 com.sdzjz.registry 不存在"），`ModItems.COMPRESSED_PACK/SUPER_COMPRESSED_PACK`×6 与 `CompressedPackItem`/`CaptureCageItem`
    // 引非白名单 xplat 物品类。根构建 src+xplat 一起编从来不报，1.20.1 只编白名单子集才炸（第 20 闸 m522b 起查自家类可见性）。
    // 收法：注册表类→静态安装口（ItemData.install 样板：重复装抛、未装用抛）；功能区物品→带默认值的宿主口（ExtractPort.Host 样板：
    // 默认值=本世代没有这件东西）。主线 Sdzjz.onInitialize 装 LegacySuperBenchHost（原表达式原句照搬），1.20.1 RetroBootstrap 装空宿主=全关。

    /** 菜单类型：注册住在各世代注册表类（主线 ModScreenHandlers 静态段 / 1.20.1 RetroBootstrap 随 SB3），注册完即装；本类不引注册表类。 */
    private static MenuType<SuperBenchScreenHandler> type;

    public static void installType(MenuType<SuperBenchScreenHandler> t) {
        if (type != null) throw new IllegalStateException("SuperBenchScreenHandler 菜单类型重复安装");
        type = t;
    }

    private static MenuType<SuperBenchScreenHandler> reqType() {
        if (type == null) throw new IllegalStateException("SuperBenchScreenHandler 菜单类型未安装：注册 MenuType 后须调 installType(...)（主线=ModScreenHandlers 静态段）");
        return type;
    }

    /** 物品宿主口：压缩材料包（m241 两件通用包 + of/innerId/rawCount/ratio）与抓物笼（cagedType）——两族都是主线专属物品类。
     *  默认值=本世代没有这件东西：认包一律"不是包"（rawCount 0 / innerId null / tier 为 null——`s.getItem()` 永不为 null，`== null` 自然假，
     *  pullPacked 前两轮扫空只搬散件）；压缩区两钮报"此版本没有压缩材料包"零动作（不许静默吞件）；刷怪类配方 mobOk 恒假（没有笼子合不出刷怪机）。 */
    public interface Host {
        /** 本世代有没有压缩材料包（压缩区两钮的总闸）。 */
        default boolean packsAvailable() { return false; }
        /** 是不是包物品（含裸包）——原句 `s.getItem() instanceof CompressedPackItem`。 */
        default boolean isPack(ItemStack s) { return false; }
        /** 一级包（64:1）物品；无=null。 */
        default Item tier1() { return null; }
        /** 二级包（4096:1）物品；无=null。 */
        default Item tier2() { return null; }
        /** 包的内容物物品 id（非包/裸包=null）——原 CompressedPackItem.innerId。 */
        default String packInnerId(ItemStack s) { return null; }
        /** 该栈折算成原版计数的量（非包/裸包=0）——原 CompressedPackItem.rawCount。 */
        default long packRawCount(ItemStack s) { return 0L; }
        /** 包物品的倍率（调用方已判 isPack）——原 `((CompressedPackItem) s.getItem()).ratio`。 */
        default int packRatio(ItemStack s) { return 0; }
        /** 造一个装着 innerId 的包（调用方已判 packsAvailable）——原 CompressedPackItem.of。 */
        default ItemStack packOf(Item packItem, String innerId, int count) {
            throw new UnsupportedOperationException("本世代没有压缩材料包，packOf 不该被调到（调用方先问 packsAvailable）");
        }
        /** 抓物笼里装的生物类型 id（不是笼子/空笼=null）——原句 `s.getItem() instanceof CaptureCageItem && mob.equals(CaptureCageItem.cagedType(s))` 的后半。 */
        default String cagedType(ItemStack s) { return null; }
    }

    private static Host hostImpl;

    public static void installHost(Host h) {
        if (hostImpl != null) throw new IllegalStateException("SuperBenchScreenHandler 物品宿主口重复安装");
        hostImpl = h;
    }

    /** m525（SB4）放开 public：屏共用件 SuperBenchView 的 BOM 认包/抓物笼判定走同一份宿主口（m486 sweepGroups / m511 pushWorld 放开同律）。 */
    public static Host host() {
        if (hostImpl == null) throw new IllegalStateException("SuperBenchScreenHandler 物品宿主口未安装：加载器入口须先调 installHost(...)（主线=Sdzjz.onInitialize 首段 LegacySuperBenchHost；1.20.1=RetroBootstrap 空宿主）");
        return hostImpl;
    }

    private final Container input = new SimpleContainer(GRID_SLOTS) {
        @Override public void setChanged() { super.setChanged(); slotsChanged(this); }
    };
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess context;

    // 客户端
    public SuperBenchScreenHandler(int syncId, Inventory playerInv) {
        this(syncId, playerInv, ContainerLevelAccess.NULL);
    }

    public SuperBenchScreenHandler(int syncId, Inventory playerInv, ContainerLevelAccess context) {
        super(reqType(), syncId); // m523：菜单类型走安装口（原 ModScreenHandlers.SUPER_BENCH）
        this.context = context;

        int gx = 8, gy = 18;
        for (int r = 0; r < GRID; r++)
            for (int c = 0; c < GRID; c++)
                this.addSlot(new Slot(input, r * GRID + c, gx + c * 18, gy + r * 18));

        // 结果槽（网格右侧居中）
        this.addSlot(new Slot(result, 0, gx + GRID * 18 + 24, gy + (GRID * 18) / 2 - 8) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player player, ItemStack stack) {
                // m95：扣料只在服务端执行。原版 container_click 包上报"本次点击改动的槽位"，
                // 协议硬上限 128 个；客户端本地预测若同时扣 144 格材料，144网格+1结果=145 个改动槽
                // 直接超限 → EncoderException 断线（m61 配方铺满 140~144 格后取成品必炸）。
                // 客户端这次点击只动结果槽（1~2 槽）；服务端扣料后经槽位同步把网格纠正回来。
                if (!player.level().isClientSide) consumeIngredients();
                super.onTake(player, stack);
            }
            // m127b：整取或不取——右键取半/Q键取1 也会 consumeIngredients 扣整份料，
            // onContentChanged 再把剩余覆盖成满结果=白丢差额（与终端结果格同族漏洞，同款焊法）。
            @Override public java.util.Optional<ItemStack> tryRemove(int min, int max, Player player) {
                ItemStack st = this.getItem();
                if (!st.isEmpty() && Math.min(min, max) < st.getCount()) return java.util.Optional.empty();
                return super.tryRemove(min, max, player);
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
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            // m242 认包：压缩包按 内容物×倍率 折算成原版计数入集，包自身 id 不入集（裸包=0 不参与）。
            // 折算溢出防护：单格上限 64×4096=262144 远小于 int，但 144 格合计可到 3775 万仍在 int 内；
            // 工程款 BOM 最大单项 13 万级（litematic 实测 json），口径安全。
            long raw = host().packRawCount(s); // m523：认包三处走宿主口（原 CompressedPackItem.rawCount/innerId + instanceof）
            if (raw > 0) m.merge(host().packInnerId(s), (int) Math.min(raw, Integer.MAX_VALUE), Integer::sum);
            else if (!host().isPack(s))
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return m;
    }

    @Override
    public void slotsChanged(Container inv) {
        if (inv == input) {
            SuperBenchRecipes.Recipe r = SuperBenchRecipes.match(gridMultiset());
            result.setItem(0, mobOk(r) ? SuperBenchRecipes.resultStack(r) : ItemStack.EMPTY);
        }
    }

    /** 刷怪类配方：每种指定生物都要有一个「装着它」的抓物笼子（空笼/装错生物都不行；m166 支持多生物，如刷铁机=村民+僵尸）。 */
    private boolean mobOk(SuperBenchRecipes.Recipe r) {
        if (r == null) return false;
        for (String mob : r.mobs()) {
            boolean found = false;
            for (int i = 0; i < GRID_SLOTS && !found; i++) {
                ItemStack s = input.getItem(i);
                if (mob.equals(host().cagedType(s))) found = true; // m523：笼子判定走宿主口（原 instanceof CaptureCageItem && cagedType）
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
                        ItemStack s = input.getItem(i);
                        if (mob.equals(host().cagedType(s))) { // m523：走宿主口
                            com.sdzjz.item.ItemData.clear(s);
                            com.sdzjz.item.ItemData.clearCustomName(s);
                            break;
                        }
                    }
                }
                continue;
            }
            // m242 扣料认包：散件先扣，再整只扣压缩包（每只入账 倍率 件）。精确多重集匹配下
            // 总量==需求，理论无找零；防御性找零（need 非倍率整除）拆成散件回网格不白丢。
            for (int i = 0; i < GRID_SLOTS && need > 0; i++) {
                ItemStack s = input.getItem(i);
                if (s.isEmpty() || host().isPack(s)) continue; // m523：走宿主口
                if (BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(e.getKey())) {
                    int take = Math.min(need, s.getCount());
                    s.shrink(take);
                    need -= take;
                }
            }
            for (int i = 0; i < GRID_SLOTS && need > 0; i++) {
                ItemStack s = input.getItem(i);
                if (!host().isPack(s)) continue; // m523：走宿主口（原 `instanceof CompressedPackItem pk`）
                if (!e.getKey().equals(host().packInnerId(s))) continue;
                int ratio = host().packRatio(s); // m523：原 pk.ratio——pattern 变量在循环前绑定一次，这里同样只取一次（shrink 到空后 getItem 变 AIR）
                while (need > 0 && !s.isEmpty()) {
                    s.shrink(1);
                    if (ratio > need) { // 防御性找零：多出来的拆成散件回网格
                        ItemStack change = new ItemStack(com.sdzjz.item.ItemData.itemById(e.getKey()), 1);
                        int left = ratio - need;
                        while (left > 0) {
                            int chunk = Math.min(left, change.getMaxStackSize());
                            insertToGrid(change.copyWithCount(chunk));
                            left -= chunk;
                        }
                        need = 0;
                    } else need -= ratio;
                }
                if (s.isEmpty()) input.setItem(i, ItemStack.EMPTY);
            }
        }
        slotsChanged(input);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.context.execute((world, pos) -> this.clearContainer(player, input));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // m127b：双击收集(PICKUP_ALL)对结果格绝缘——该路径 takeStack 直取可部分吸走结果，
    // 每吸一口都扣整份 144 格配方料（原版 CraftingScreenHandler 排除 result 的同款语义）。
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result;
    }

    // ===== m241 压缩区（方案A）：保留钮 id 远离配方下标域，复用既有 clickButton 通道零新协议 =====
    public static final int BTN_COMPRESS = 1_000_000; // 压缩网格材料（64→一级包，64一级→二级包，一次点击级联）
    public static final int BTN_UNPACK   = 1_000_001; // 拆开材料包（二级→一级→原物，只落网格、拆前查容量）

    /** 配方浏览器点击：把 #id 配方的材料从背包填入网格（先清空网格还给玩家）。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_COMPRESS) { if (!player.level().isClientSide) compressGrid(player); return true; }
        if (id == BTN_UNPACK)   { if (!player.level().isClientSide) unpackGrid(player);   return true; }
        if (id < 0 || id >= SuperBenchRecipes.ALL.size()) return false;
        SuperBenchRecipes.Recipe r = SuperBenchRecipes.ALL.get(id);
        // 清空网格→还给玩家
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getItem(i);
            if (!s.isEmpty()) {
                if (!player.getInventory().add(s)) player.drop(s, false);
                input.setItem(i, ItemStack.EMPTY);
            }
        }
        // 刷怪类：每种生物从背包找「装着它」的那个笼子（整个带 NBT 搬过来，不能造新的；m166 多生物逐只找）
        java.util.List<ItemStack> cages = new java.util.ArrayList<>();
        for (String mob : r.mobs()) {
            Inventory pinv = player.getInventory();
            for (int i = 0; i < pinv.getContainerSize(); i++) {
                ItemStack s = pinv.getItem(i);
                if (mob.equals(host().cagedType(s))) { // m523：走宿主口
                    cages.add(s.copyWithCount(1)); // 只搬 1 只（多重集精确匹配要求 每生物×1）
                    s.shrink(1);
                    if (s.isEmpty()) pinv.setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        // m244 打包填料分流：layout=null 的打包版 BOM 不铺蓝图——笼子照常搬，材料从背包
        // 按内容物贪心搬压缩包（二级→一级→散件），落网格自动堆叠。
        if (r.layout() == null) {
            for (ItemStack c : cages) {
                ItemStack rem = insertToGrid(c);
                if (!rem.isEmpty() && !player.getInventory().add(rem)) player.drop(rem, false);
            }
            for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
                if (SuperBenchRecipes.CAGE_ID.equals(e.getKey())) continue;
                pullPacked(player, e.getKey(), e.getValue());
            }
            input.setChanged();
            sendMissingSummary(player, r);
            return true;
        }
        // 从背包按需批量取料，建立可用池
        Map<String, Integer> pool = new HashMap<>();
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            if (SuperBenchRecipes.CAGE_ID.equals(e.getKey())) continue; // 笼子单独处理
            Item item = com.sdzjz.item.ItemData.itemById(e.getKey());
            pool.put(e.getKey(), takeFromInv(player, item, e.getValue()));
        }
        // 按蓝图布局逐格摆放（1 格 1 件；缺料的格留空）
        String[] layout = r.layout();
        for (int i = 0; i < GRID_SLOTS; i++) {
            String want = layout[i];
            if (want == null) continue;
            if (SuperBenchRecipes.CAGE_ID.equals(want)) {
                if (!cages.isEmpty()) input.setItem(i, cages.remove(0));
                continue;
            }
            int have = pool.getOrDefault(want, 0);
            if (have > 0) {
                input.setItem(i, new ItemStack(com.sdzjz.item.ItemData.itemById(want), 1));
                pool.put(want, have - 1);
            }
        }
        for (ItemStack c : cages) { if (!player.getInventory().add(c)) player.drop(c, false); }
        input.setChanged();
        sendMissingSummary(player, r); // 填完统计缺什么，聊天栏直说，不再"点了没反应"
        return true;
    }

    /** m244 打包填料：为材料 id 凑 need 件原版计数——背包里 二级包→一级包→普通散件 三轮贪心，
     *  整只搬包不拆（need 为包整倍时刚好凑齐），落网格自动堆叠；塞不下（理论到不了，槽位账
     *  离线断言过）按倍率回账并还背包不落地。 */
    private void pullPacked(Player player, String id, int need) {
        Inventory pinv = player.getInventory();
        Item t2 = host().tier2(), t1 = host().tier1(); // m523：走宿主口（原 ModItems.SUPER_COMPRESSED_PACK / COMPRESSED_PACK）；默认关=null，前两轮 `getItem() != null` 恒真只搬散件
        for (int pass = 0; pass < 3 && need > 0; pass++) {
            int ratio = pass == 0 ? 4096 : pass == 1 ? 64 : 1;
            Item want = pass == 0 ? t2 : pass == 1 ? t1 : null;
            for (int i = 0; i < pinv.getContainerSize() && need >= ratio; i++) {
                ItemStack s = pinv.getItem(i);
                if (pass < 2) {
                    if (s.getItem() != want || !id.equals(host().packInnerId(s))) continue;
                } else {
                    if (s.isEmpty() || host().isPack(s)) continue;
                    if (com.sdzjz.item.ItemData.has(s)) continue; // 组件件不当散料搬（附魔书等）
                    if (!BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
                }
                int take = Math.min(need / ratio, s.getCount());
                if (take <= 0) continue;
                ItemStack moved = s.copyWithCount(take);
                s.shrink(take);
                if (s.isEmpty()) pinv.setItem(i, ItemStack.EMPTY);
                need -= take * ratio;
                ItemStack rem = insertToGrid(moved);
                if (!rem.isEmpty()) {
                    need += rem.getCount() * ratio;
                    if (!pinv.add(rem)) player.drop(rem, false);
                }
            }
        }
    }

    /** 填料后核对网格 vs 配方：缺什么、缺几个，发聊天消息；齐了发"就绪"。m166 多生物逐只报缺。 */
    private void sendMissingSummary(Player player, SuperBenchRecipes.Recipe r) {
        Map<String, Integer> grid = gridMultiset();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) {
            int lack = e.getValue() - grid.getOrDefault(e.getKey(), 0);
            if (lack <= 0) continue;
            if (SuperBenchRecipes.CAGE_ID.equals(e.getKey())) continue; // 笼子按生物逐只报，见下
            Item it = com.sdzjz.item.ItemData.itemById(e.getKey());
            missing.add(it.getDescription().getString() + "×" + lack);
        }
        java.util.List<String> cageMiss = new java.util.ArrayList<>(); // 缺笼或装错生物的，报生物名
        for (String mob : r.mobs()) {
            boolean found = false;
            for (int i = 0; i < GRID_SLOTS && !found; i++) {
                ItemStack s = input.getItem(i);
                if (mob.equals(host().cagedType(s))) found = true; // m523：走宿主口
            }
            if (!found) {
                String mn;
                try { mn = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .get(com.sdzjz.item.ItemData.id(mob)).getDescription().getString(); } // m522：id 走 ItemData 世代口
                catch (Exception ex) { mn = mob; }
                cageMiss.add(mn);
            }
        }
        if (missing.isEmpty() && cageMiss.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("材料齐全，取走结果即可")
                    .withStyle(net.minecraft.ChatFormatting.GREEN), false);
            return;
        }
        net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal("还缺: ")
                .withStyle(net.minecraft.ChatFormatting.RED);
        if (!cageMiss.isEmpty()) {
            msg.append(net.minecraft.network.chat.Component.literal("装着[" + String.join("/", cageMiss)
                    + "]的抓物笼子（每种一只，去抓）" + (missing.isEmpty() ? "" : "、")));
        }
        int shown = Math.min(missing.size(), 6);
        msg.append(net.minecraft.network.chat.Component.literal(String.join("、", missing.subList(0, shown))
                + (missing.size() > shown ? " 等" + missing.size() + "项" : "")));
        player.displayClientMessage(msg, false);
    }

    private int takeFromInv(Player player, Item item, int need) {
        int got = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && got < need; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) { int take = Math.min(need - got, s.getCount()); s.shrink(take); got += take; }
        }
        return got;
    }

    // ===== m241 压缩引擎（方案A，服务端权威）。口径：包=原版物品的数量压缩（作者拍板），
    // 只压"无组件差异的普通物品"（附魔书/药水等跳过，防压包抹组件——精确条目教训同源）。 =====

    /** m523：宿主口默认关（本世代无压缩材料包）时两钮零动作并明说——压缩会把 64 件散件换成一只不存在的包，
     *  静默吞件比"钮没反应"伤得多（m99 同律）。服务端权威：钮 id 来自客户端，屏上不画钮也得在这儿拦。返回真=已拦。 */
    private boolean packsUnavailable(Player player) {
        if (host().packsAvailable()) return false;
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("此版本没有压缩材料包，压缩区不可用")
                .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        return true;
    }

    /** 压缩：网格里同种普通物品每满 64 → 1 一级包；同内容一级包每满 64 → 1 二级包（一次点击级联）。 */
    private void compressGrid(Player player) {
        if (packsUnavailable(player)) return; // m523
        long rawPacked = 0; int t1Made = 0, t2Made = 0;
        Map<String, Integer> plain = new HashMap<>();
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty() || host().isPack(s)) continue; // m523：走宿主口
            if (com.sdzjz.item.ItemData.has(s)) continue;
            plain.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : plain.entrySet()) {
            int k = e.getValue() / 64;
            if (k <= 0) continue;
            takePlainFromGrid(e.getKey(), k * 64);
            giveToGridOrPlayer(player, host().packOf(host().tier1(), e.getKey(), k)); // m523：走宿主口（原 CompressedPackItem.of(ModItems.COMPRESSED_PACK,…)）
            rawPacked += (long) k * 64; t1Made += k;
        }
        Map<String, Integer> packs = new HashMap<>();
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getItem(i);
            if (s.getItem() == host().tier1()) { // m523：走宿主口
                String in = host().packInnerId(s);
                if (in != null) packs.merge(in, s.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : packs.entrySet()) {
            int k = e.getValue() / 64;
            if (k <= 0) continue;
            takePacksFromGrid(e.getKey(), k * 64);
            giveToGridOrPlayer(player, host().packOf(host().tier2(), e.getKey(), k)); // m523：走宿主口（原 CompressedPackItem.of(ModItems.SUPER_COMPRESSED_PACK,…)）
            t2Made += k;
        }
        input.setChanged();
        if (t1Made == 0 && t2Made == 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "没有可压缩的（需网格里同种普通物品≥64，或同内容一级包≥64）")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "压缩完成: " + (t1Made > 0 ? "一级包+" + t1Made + "（收纳原物 " + rawPacked + " 件）" : "")
                    + (t1Made > 0 && t2Made > 0 ? "、" : "") + (t2Made > 0 ? "二级包+" + t2Made : ""))
                    .withStyle(net.minecraft.ChatFormatting.GREEN), false);
        }
    }

    /** 拆包（m246 重做，作者实测"压得了拆不了"）：二级→一级→原物逐包拆，产物**先落网格、
     *  溢出进背包**（旧版只落网格，网格一满整体罢工=看着像拆不了），两边都装不下才停；
     *  每次点击必报账（拆了多少/剩多少/为什么停），网格没包但背包有包时明说"把包放进网格"。 */
    private void unpackGrid(Player player) {
        if (packsUnavailable(player)) return; // m523
        long t2Opened = 0, t1Opened = 0;
        boolean spaceOut = false;
        Item t2 = host().tier2(), t1 = host().tier1(); // m523：走宿主口（原 ModItems.SUPER_COMPRESSED_PACK / COMPRESSED_PACK）
        // m260 卡顿主治：旧版逐包循环——每拆 1 包全扫一遍网格144+背包36做容量账（含 NBT 组件比较）再全扫插入，
        // 一次点击拆几百包=十几万次 ItemStack/NBT 比较，单人内置服一卡客户端就顿。改槽级批量：
        // 容量一次结算整批开包 + insertBulk 单趟灌装，每击总开销 O(槽位数×轮数)，轮数受物理容量约束（腾槽才续轮）。
        for (int pass = 0; pass < 2; pass++) {
            Item packItem = pass == 0 ? t2 : t1;
            boolean any = true; // 空间账仍按层各算：超级包层堵住不连坐一级层（产物不同容量不同）
            while (any) {
                any = false;
                for (int i = 0; i < GRID_SLOTS; i++) {
                    ItemStack s = input.getItem(i);
                    if (s.getItem() != packItem) continue;
                    String in = host().packInnerId(s); // m523：走宿主口
                    if (in == null) continue;
                    ItemStack proto = pass == 0
                            ? host().packOf(t1, in, 1) // m523：走宿主口
                            : new ItemStack(com.sdzjz.item.ItemData.itemById(in), 1);
                    int can = (int) Math.min(s.getCount(), capacityAll(player, proto) / 64); // 一包出64件
                    if (can <= 0) { spaceOut = true; continue; }
                    if (can < s.getCount()) spaceOut = true; // 本槽没拆完=空间见底（若腾出槽位下一轮还会进来）
                    s.shrink(can);
                    if (s.isEmpty()) input.setItem(i, ItemStack.EMPTY);
                    insertBulk(player, proto, (long) can * 64);
                    if (pass == 0) t2Opened += can; else t1Opened += can;
                    any = true;
                }
            }
        }
        input.setChanged();
        // 每击必报账（旧版拆成功一声不吭，出问题只剩猜）
        long t2Left = 0, t1Left = 0;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getItem(i);
            if (host().packInnerId(s) == null) continue; // 裸包拆不了，不进"剩×N"账（m260）；m523 走宿主口
            if (s.getItem() == t2) t2Left += s.getCount();
            else if (s.getItem() == t1) t1Left += s.getCount();
        }
        spaceOut = spaceOut && (t2Left + t1Left) > 0; // 批拆后全清空就别报"已满"
        if (t2Opened + t1Opened > 0) {
            String msg = "已拆开: " + (t2Opened > 0 ? "超级包×" + t2Opened + "→一级包×" + (t2Opened * 64) : "")
                    + (t2Opened > 0 && t1Opened > 0 ? "、" : "")
                    + (t1Opened > 0 ? "一级包×" + t1Opened + "→原物×" + (t1Opened * 64) : "")
                    + (spaceOut ? "；网格+背包已满，剩 超级包×" + t2Left + "/一级包×" + t1Left + " 未拆" : "");
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg)
                    .withStyle(spaceOut ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.GREEN), false);
        } else if (spaceOut) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("网格和背包都装不下拆出来的东西——先腾地方再点")
                    .withStyle(net.minecraft.ChatFormatting.RED), false);
        } else {
            // 网格没包——背包里有包的话明说（作者实测最容易踩的坑：包放在背包区点拆开）
            boolean invHasPack = false;
            Inventory pinv = player.getInventory();
            for (int i = 0; i < pinv.getContainerSize() && !invHasPack; i++) {
                Item it = pinv.getItem(i).getItem();
                if (it == t1 || it == t2) invHasPack = true;
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(invHasPack
                    ? "拆开只认网格里的包——把背包里的包放进左边网格再点"
                    : "网格里没有材料包").withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }
    }

    /** 网格+玩家主背包合计还能容纳多少件与 proto 同物同组件的东西——全量口径不早退（m260 批量结算用；盔甲/副手不收料）。 */
    private long capacityAll(Player player, ItemStack proto) {
        long cap = 0;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) cap += proto.getMaxStackSize();
            else if (com.sdzjz.storage.StackKey.same(s, proto)) cap += Math.max(0, s.getMaxStackSize() - s.getCount());
        }
        Inventory pinv = player.getInventory();
        for (int i = 0; i < pinv.items.size(); i++) {
            ItemStack s = pinv.items.get(i);
            if (s.isEmpty()) cap += proto.getMaxStackSize();
            else if (com.sdzjz.storage.StackKey.same(s, proto)) cap += Math.max(0, s.getMaxStackSize() - s.getCount());
        }
        return cap;
    }

    /** 批量灌装 total 件 proto：网格先并同栈再填空格→主背包同法，各一趟 O(槽)（m260；调用方已按 capacityAll 核量，兜底落地不丢件）。 */
    private void insertBulk(Player player, ItemStack proto, long total) {
        for (int i = 0; i < GRID_SLOTS && total > 0; i++) {
            ItemStack s = input.getItem(i);
            if (!s.isEmpty() && com.sdzjz.storage.StackKey.same(s, proto)) {
                int mv = (int) Math.min(s.getMaxStackSize() - s.getCount(), total);
                if (mv > 0) { s.grow(mv); total -= mv; }
            }
        }
        for (int i = 0; i < GRID_SLOTS && total > 0; i++) {
            if (input.getItem(i).isEmpty()) {
                int mv = (int) Math.min(proto.getMaxStackSize(), total);
                input.setItem(i, proto.copyWithCount(mv)); total -= mv;
            }
        }
        Inventory pinv = player.getInventory();
        for (int i = 0; i < pinv.items.size() && total > 0; i++) {
            ItemStack s = pinv.items.get(i);
            if (!s.isEmpty() && com.sdzjz.storage.StackKey.same(s, proto)) {
                int mv = (int) Math.min(s.getMaxStackSize() - s.getCount(), total);
                if (mv > 0) { s.grow(mv); total -= mv; }
            }
        }
        for (int i = 0; i < pinv.items.size() && total > 0; i++) {
            if (pinv.items.get(i).isEmpty()) {
                int mv = (int) Math.min(proto.getMaxStackSize(), total);
                pinv.items.set(i, proto.copyWithCount(mv)); total -= mv;
            }
        }
        while (total > 0) { // 容量已核不该走到；兜底不丢件（与关屏散落同口径）
            int mv = (int) Math.min(proto.getMaxStackSize(), total);
            player.drop(proto.copyWithCount(mv), false); total -= mv;
        }
        pinv.setChanged();
    }

    /** 从网格取走 n 件指定 id 的"普通物品"（无组件差异；调用方已核总量足够）。 */
    private void takePlainFromGrid(String id, int n) {
        for (int i = 0; i < GRID_SLOTS && n > 0; i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty() || host().isPack(s)) continue; // m523：走宿主口
            if (com.sdzjz.item.ItemData.has(s)) continue;
            if (!BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) continue;
            int take = Math.min(n, s.getCount());
            s.shrink(take); n -= take;
            if (s.isEmpty()) input.setItem(i, ItemStack.EMPTY);
        }
    }

    /** 从网格取走 n 个装着 innerId 的一级包。 */
    private void takePacksFromGrid(String innerId, int n) {
        for (int i = 0; i < GRID_SLOTS && n > 0; i++) {
            ItemStack s = input.getItem(i);
            if (s.getItem() != host().tier1()) continue; // m523：走宿主口（原 ModItems.COMPRESSED_PACK）
            if (!innerId.equals(host().packInnerId(s))) continue;
            int take = Math.min(n, s.getCount());
            s.shrink(take); n -= take;
            if (s.isEmpty()) input.setItem(i, ItemStack.EMPTY);
        }
    }

    /** 插入网格：先并同物同组件栈，再落空格；返回没塞下的余量（可能为空栈）。 */
    private ItemStack insertToGrid(ItemStack st) {
        for (int i = 0; i < GRID_SLOTS && !st.isEmpty(); i++) {
            ItemStack s = input.getItem(i);
            if (!s.isEmpty() && com.sdzjz.storage.StackKey.same(s, st)) {
                int room = s.getMaxStackSize() - s.getCount();
                if (room > 0) { int mv = Math.min(room, st.getCount()); s.grow(mv); st.shrink(mv); }
            }
        }
        for (int i = 0; i < GRID_SLOTS && !st.isEmpty(); i++) {
            if (input.getItem(i).isEmpty()) {
                int mv = Math.min(st.getMaxStackSize(), st.getCount());
                input.setItem(i, st.copyWithCount(mv));
                st.shrink(mv);
            }
        }
        return st;
    }

    /** 压缩产物给回：优先网格，塞不下进背包，再不行落地（与关屏散落同口径）。 */
    private void giveToGridOrPlayer(Player player, ItemStack st) {
        ItemStack rem = insertToGrid(st);
        if (!rem.isEmpty() && !player.getInventory().add(rem)) player.drop(rem, false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == RESULT_INDEX) return ItemStack.EMPTY; // 结果槽用鼠标取
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            ret = st.copy();
            int invStart = RESULT_INDEX + 1;
            int invEnd = invStart + 36;
            if (index < GRID_SLOTS) {
                if (!this.moveItemStackTo(st, invStart, invEnd, false)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(st, 0, GRID_SLOTS, false)) return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        }
        return ret;
    }
}
