package com.sdzjz.block;

import com.sdzjz.machine.VillagerTrades;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import com.sdzjz.screen.TradeCenterScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 村民交易所：放入村民合同 → 就业（消耗工作方块）→ 执行交易（输入从相连存储核心取、输出存回）→ 治愈提折扣。
 * 合同数据存物品 CUSTOM_DATA：prof=职业id, disc=折扣等级(0..5)。
 */
public class TradeCenterBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public final SimpleInventory contractSlot = new SimpleInventory(1) {
        @Override public void markDirty() { super.markDirty(); TradeCenterBlockEntity.this.markDirty(); }
    };

    public TradeCenterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADE_CENTER_BE, pos, state);
    }

    private List<StorageCoreBlockEntity> cores() {
        return StorageCoreBlockEntity.connectedCores(this.world, this.pos);
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
        if (s.isEmpty() || !s.isOf(ModItems.VILLAGER_CONTRACT)) return null;
        String p = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getString("prof");
        return p.isEmpty() ? null : p;
    }

    public static int contractDiscount(ItemStack s) {
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt("disc");
    }

    private static void setContract(ItemStack s, String prof, int disc) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putString("prof", prof);
        n.putInt("disc", disc);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
    }

    // ---- 三个服务端动作（由 Handler.onButtonClick 调） ----

    /** 就业：无职业合同 + 从网络消耗 1 个对应工作方块。 */
    public void employ(PlayerEntity player, int profIndex) {
        ItemStack c = contractSlot.getStack(0);
        if (c.isEmpty() || !c.isOf(ModItems.VILLAGER_CONTRACT) || contractProf(c) != null) return;
        List<String> ids = VillagerTrades.professionIds();
        if (profIndex < 0 || profIndex >= ids.size()) return;
        String prof = ids.get(profIndex);
        String ws = VillagerTrades.ALL.get(prof).workstation();
        if (netWithdraw(ws, 1) < 1) {
            player.sendMessage(Text.literal("就业失败：存储网络里没有对应工作方块 " + ws), true);
            return;
        }
        setContract(c, prof, 0);
        contractSlot.markDirty();
    }

    /** 执行第 index 条交易：输入按折扣从网络取，输出存回网络。 */
    public void trade(PlayerEntity player, int index) {
        ItemStack c = contractSlot.getStack(0);
        String prof = contractProf(c);
        if (prof == null) return;
        List<VillagerTrades.Trade> trades = VillagerTrades.ALL.get(prof).trades();
        if (index < 0 || index >= trades.size()) return;
        VillagerTrades.Trade t = trades.get(index);
        int disc = contractDiscount(c);
        int need = VillagerTrades.discounted(t.inCount(), disc);
        if (netCount(t.inItem()) < need) {
            player.sendMessage(Text.literal("材料不足：需要 " + need + "× " + t.inItem()), true);
            return;
        }
        netWithdraw(t.inItem(), need);
        ItemStack out = new ItemStack(Registries.ITEM.get(Identifier.of(t.outItem())), t.outCount());
        if (!netDeposit(out)) {
            // 存储满类型收不下：还给玩家，别凭空消失
            if (!player.getInventory().insertStack(out)) player.dropItem(out, false);
        }
        player.addExperience(3 + player.getRandom().nextInt(4)); // 原版交易经验 3-6
    }

    /** 治愈：消耗网络里 1 个金苹果，折扣 +1（最高 5）。 */
    public void heal(PlayerEntity player) {
        ItemStack c = contractSlot.getStack(0);
        String prof = contractProf(c);
        if (prof == null) return;
        int disc = contractDiscount(c);
        if (disc >= 5) return;
        if (netWithdraw("minecraft:golden_apple", 1) < 1) {
            player.sendMessage(Text.literal("治愈失败：存储网络里没有金苹果"), true);
            return;
        }
        setContract(c, prof, disc + 1);
        contractSlot.markDirty();
    }

    public void dropAll(World world, BlockPos pos) {
        net.minecraft.util.ItemScatterer.spawn(world, pos, contractSlot);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        ItemStack c = contractSlot.getStack(0);
        if (!c.isEmpty()) nbt.put("contract", c.encode(lookup));
    }

    @Override
    protected void readNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        contractSlot.setStack(0, nbt.contains("contract")
                ? ItemStack.fromNbt(lookup, nbt.getCompound("contract")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.sdzjz.trade_center");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new TradeCenterScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
        return this.pos;
    }
}
