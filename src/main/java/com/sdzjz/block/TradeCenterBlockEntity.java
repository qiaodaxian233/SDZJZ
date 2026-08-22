package com.sdzjz.block;

import com.sdzjz.machine.VillagerTrades;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import com.sdzjz.screen.TradeCenterScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 村民交易所：放入村民合同 → 就业（消耗工作方块）→ 执行交易（输入从相连存储核心取、输出存回）→ 治愈提折扣。
 * 合同数据存物品 CUSTOM_DATA：prof=职业id, disc=折扣等级(0..5)。
 */
public class TradeCenterBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public final SimpleContainer contractSlot = new SimpleContainer(1) {
        @Override public void setChanged() { super.setChanged(); TradeCenterBlockEntity.this.setChanged(); }
    };

    // ---- m145 已加载交易所注册表（村民打折机的发现面）----
    /** setWorld 登记 / markRemoved 注销；WeakHashMap 键=世界（世界卸载随 GC 清）。
     *  卸载区块的残留坐标由 loadedIn 的 getBlockEntity 空返回自然过滤（create=false 不强载）。 */
    private static final java.util.Map<Level, java.util.Set<BlockPos>> LOADED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    @Override
    public void setLevel(Level world) {
        super.setLevel(world);
        if (world != null && !world.isClientSide)
            LOADED.computeIfAbsent(world, w -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            java.util.Set<BlockPos> s = LOADED.get(level);
            if (s != null) s.remove(worldPosition);
        }
        super.setRemoved();
    }

    /** 当前世界里已加载的交易所（拷贝遍历，坐标验活）。 */
    public static java.util.List<TradeCenterBlockEntity> loadedIn(Level world) {
        java.util.List<TradeCenterBlockEntity> out = new java.util.ArrayList<>();
        java.util.Set<BlockPos> s = LOADED.get(world);
        if (s == null) return out;
        for (BlockPos p : java.util.List.copyOf(s))
            if (world.getBlockEntity(p) instanceof TradeCenterBlockEntity tc) out.add(tc);
        return out;
    }

    /** m145 打折机接口：与给定仓集共网？（connectedCores 交集，按坐标比不按实例比）。 */
    public boolean sharesNetwork(java.util.Collection<StorageCoreBlockEntity> banks) {
        for (StorageCoreBlockEntity a : cores())
            for (StorageCoreBlockEntity b : banks)
                if (a.getBlockPos().equals(b.getBlockPos())) return true;
        return false;
    }

    /** m145 打折机接口：合同可升折扣？（已就业且未满 5 级）。 */
    public boolean canCure() {
        ItemStack c = contractSlot.getItem(0);
        return contractProf(c) != null && contractDiscount(c) < 5;
    }

    /** m145 打折机接口：升 1 级（调用方已付金苹果——先取料后调用，别反过来）。 */
    public void cureOnce() {
        ItemStack c = contractSlot.getItem(0);
        String prof = contractProf(c);
        if (prof == null) return;
        int d = contractDiscount(c);
        if (d >= 5) return;
        setContract(c, prof, d + 1);
        contractSlot.setChanged();
    }

    public TradeCenterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADE_CENTER_BE, pos, state);
    }

    private List<StorageCoreBlockEntity> cores() {
        return StorageCoreBlockEntity.connectedCores(this.level, this.worldPosition);
    }

    private long netCount(String id) {
        long n = 0;
        for (StorageCoreBlockEntity c : cores()) n += c.count(id);
        return n;
    }

    private int netWithdraw(String id, int amount) {
        int got = 0;
        for (StorageCoreBlockEntity c : cores()) {
            if (got >= amount) break;
            got += c.withdraw(id, amount - got);
        }
        return got;
    }

    private boolean netDeposit(ItemStack stack) {
        for (StorageCoreBlockEntity c : cores()) {
            c.deposit(stack);
            if (stack.isEmpty()) return true;
        }
        return stack.isEmpty();
    }

    // ---- 合同数据 ----
    public static String contractProf(ItemStack s) {
        if (s.isEmpty() || !s.is(ModItems.VILLAGER_CONTRACT)) return null;
        String p = com.sdzjz.node.NodeTags.viewOf(s).getString("prof"); // m353 只读免拷贝（交易机每拍逐合同读）
        return p.isEmpty() ? null : p;
    }

    public static int contractDiscount(ItemStack s) {
        return com.sdzjz.node.NodeTags.viewOf(s).getInt("disc"); // m353 只读免拷贝
    }

    /** m333 合同等级（1..5）。**旧合同（有职业但无 lv 键）按 5 级接管**——m333 上线前它们本就
     *  全表解锁，收回=没收玩家既有权益（m99 之训反面）。新就业合同从 1 级起步。 */
    public static int contractLevel(ItemStack s) {
        if (contractProf(s) == null) return 0;
        CompoundTag n = com.sdzjz.node.NodeTags.viewOf(s); // m353 只读免拷贝
        if (!n.contains("lv")) return 5;
        return Math.max(1, Math.min(5, n.getInt("lv")));
    }

    /** m333 合同累计交易经验（满级后不再累计）。 */
    public static int contractXp(ItemStack s) {
        return com.sdzjz.node.NodeTags.viewOf(s).getInt("xp"); // m353 只读免拷贝
    }

    private static void setLevel(ItemStack s, int lv, int xp) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putInt("lv", lv);
        n.putInt("xp", xp);
        com.sdzjz.item.ItemData.write(s, n);
    }

    /** m333 记交易经验并按门槛升级，返回升了几级（0=没升）。纯函数口（廿二号用例直测）：
     *  旧合同（无 lv 键）视同满级零写入；满级封顶（到顶再投入=界面明示"满级"，不静默——m99 之问）。 */
    public static int grantTradeXp(ItemStack c, int gain) {
        if (contractProf(c) == null) return 0;
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(c);
        int lv = n.contains("lv") ? Math.max(1, Math.min(5, n.getInt("lv"))) : 5;
        if (lv >= 5) return 0;
        int xp = Math.max(0, n.getInt("xp")) + Math.max(0, gain);
        int up = 0;
        while (lv < 5 && xp >= VillagerTrades.LEVEL_XP[lv]) { lv++; up++; }
        n.putInt("lv", lv);
        n.putInt("xp", xp);
        com.sdzjz.item.ItemData.write(c, n);
        return up;
    }

    private static void setContract(ItemStack s, String prof, int disc) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putString("prof", prof);
        n.putInt("disc", disc);
        com.sdzjz.item.ItemData.write(s, n);
    }

    // ---- 三个服务端动作（由 Handler.onButtonClick 调） ----

    /** 就业：无职业合同 + 从网络消耗 1 个对应工作方块。 */
    public void employ(Player player, int profIndex) {
        ItemStack c = contractSlot.getItem(0);
        if (c.isEmpty() || !c.is(ModItems.VILLAGER_CONTRACT) || contractProf(c) != null) return;
        List<String> ids = VillagerTrades.professionIds();
        if (profIndex < 0 || profIndex >= ids.size()) return;
        String prof = ids.get(profIndex);
        String ws = VillagerTrades.ALL.get(prof).workstation();
        if (netWithdraw(ws, 1) < 1) {
            player.displayClientMessage(Component.literal("就业失败：存储网络里没有对应工作方块 " + ws), true);
            return;
        }
        setContract(c, prof, 0);
        setLevel(c, 1, 0); // m333 新就业=新手起步（旧合同无 lv 键=按大师接管，见 contractLevel）
        contractSlot.setChanged();
    }

    /** 执行第 index 条交易：输入按折扣从网络取，输出存回网络；附魔书直发玩家背包（m101）。 */
    public void trade(Player player, int index) {
        ItemStack c = contractSlot.getItem(0);
        String prof = contractProf(c);
        if (prof == null) return;
        List<VillagerTrades.Trade> trades = VillagerTrades.ALL.get(prof).trades();
        if (index < 0 || index >= trades.size()) return;
        VillagerTrades.Trade t = trades.get(index);
        var cfg = com.sdzjz.config.SdzjzConfig.get();
        if (cfg.tradeLeveling && contractLevel(c) < t.minLevel()) { // m333 等级闸（客户端锁行只是礼貌，这里才是门）
            player.displayClientMessage(Component.literal("交易未解锁：需 " + VillagerTrades.levelName(t.minLevel())
                    + "（当前 " + VillagerTrades.levelName(contractLevel(c)) + "）——先做已解锁交易攒经验"), true);
            return;
        }
        int disc = contractDiscount(c);
        int need = VillagerTrades.discounted(t.inCount(), disc);
        if (netCount(t.inItem()) < need) {
            player.displayClientMessage(Component.literal("材料不足：需要 " + need + "× " + t.inItem()), true);
            return;
        }
        // m101 修现成 bug：双输入交易此前完全没查/没扣第二种料（附魔书要的那本书）
        if (t.in2Item() != null && netCount(t.in2Item()) < t.in2Count()) {
            player.displayClientMessage(Component.literal("材料不足：需要 " + t.in2Count() + "× " + t.in2Item()), true);
            return;
        }
        netWithdraw(t.inItem(), need);
        if (t.in2Item() != null) netWithdraw(t.in2Item(), t.in2Count());

        if (t.enchant() != null && this.level != null) {
            // m101 附魔书：按注册表构建带附魔的书。产物**只进玩家背包**——
            // （m329 勘注：原理由"进仓=附魔被抹"自 m130 精确账本起已不成立，m146 交易机的书就走精确账本；
            //  手动交易保留直发背包=既有体验，是否与自动侧统一改走精确账本待作者拍板，行为本笔不动。）
            ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
            var reg = this.level.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var entry = reg.getOrThrow(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT, ResourceLocation.parse(t.enchant())));
            book.enchant(entry, t.enchantLv());
            if (!player.getInventory().add(book)) player.drop(book, false);
            player.displayClientMessage(Component.literal("附魔书已放入背包（带附魔物品不进仓储，防丢附魔）"), true);
        } else {
            ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.outItem())), t.outCount());
            if (!netDeposit(out)) {
                // 存储满类型收不下：还给玩家，别凭空消失
                if (!player.getInventory().add(out)) player.drop(out, false);
            }
        }
        player.giveExperiencePoints(3 + player.getRandom().nextInt(4)); // 原版交易经验 3-6
        if (cfg.tradeLeveling) { // m333 合同经验：越高档交易喂得越多，升级即时播报
            int up = grantTradeXp(c, VillagerTrades.tradeXp(t) * Math.max(1, cfg.tradeXpMultiplier));
            contractSlot.setChanged();
            if (up > 0) player.displayClientMessage(Component.literal("叮！村民升级：" + VillagerTrades.levelName(contractLevel(c))
                    + "——交易列表解锁新行"), true);
        }
    }

    /** 治愈：消耗网络里 1 个金苹果，折扣 +1（最高 5）。 */
    public void heal(Player player) {
        ItemStack c = contractSlot.getItem(0);
        String prof = contractProf(c);
        if (prof == null) return;
        int disc = contractDiscount(c);
        if (disc >= 5) return;
        if (netWithdraw("minecraft:golden_apple", 1) < 1) {
            player.displayClientMessage(Component.literal("治愈失败：存储网络里没有金苹果"), true);
            return;
        }
        setContract(c, prof, disc + 1);
        contractSlot.setChanged();
    }

    public void dropAll(Level world, BlockPos pos) {
        net.minecraft.world.Containers.dropContents(world, pos, contractSlot);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        ItemStack c = contractSlot.getItem(0);
        if (!c.isEmpty()) nbt.put("contract", c.save(lookup));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        contractSlot.setItem(0, nbt.contains("contract")
                ? ItemStack.parse(lookup, nbt.getCompound("contract")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sdzjz.trade_center");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new TradeCenterScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
        return this.worldPosition;
    }
}
