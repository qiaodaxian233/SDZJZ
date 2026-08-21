package com.sdzjz.screen;

import com.sdzjz.item.PortableVaultItem;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;

/**
 * m312 随身仓库取物屏。零 S2C 同步取巧：账本存手上包的 CUSTOM_DATA 组件，随背包槽位
 * 天然同步到客户端——屏端直接读客户端手上包画列表，服务端只收一个 VaultTakePayload。
 * 列表区无槽位（照交易所 m101 列表刀法）；下方背包槽 shift 点=整叠入账（离屏收纳同规矩）。
 */
public class PortableVaultScreenHandler extends AbstractContainerMenu {

    /** 列表几何唯一口径（屏同用，m215 教训：渲染与点击同源常量）。
     *  m315：PINV_Y 138→150——旧值下两行提示同落 y+126 逐字重叠（列表底 126 与 PINV_Y-12 撞车），
     *  背包区下移 12 给两行提示各留一行位，屏高同步 224→236。 */
    public static final int LIST_Y = 32, ROW_H = 18, ROWS = 5;
    public static final int PINV_X = 20, PINV_Y = 150;

    private final Player player;
    private final InteractionHand hand; // 开屏时定格：主手优先

    public PortableVaultScreenHandler(int syncId, Inventory playerInv) {
        super(ModScreenHandlers.PORTABLE_VAULT, syncId);
        this.player = playerInv.player;
        this.hand = playerInv.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PortableVaultItem
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, PINV_X + c * 18, PINV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, PINV_X + c * 18, PINV_Y + 58));
    }

    /** 客户端渲染/双端校验都从这拿包（手上实时读，被换手/丢弃即空）。 */
    public ItemStack vault() {
        ItemStack s = player.getItemInHand(hand);
        if (s.getItem() instanceof PortableVaultItem) return s;
        // m332 专属仓位兜底：从仓位开的屏照常校验/渲染；仓位栈走原版槽位同步（playerScreenHandler
        // 每 playerTick 恒广播，本屏开着也刷）——客户端列表读数与服务端账本同源不冻。
        return com.sdzjz.item.PortableVaultSlot.stackOf(player);
    }

    @Override
    public boolean stillValid(Player p) {
        return !vault().isEmpty(); // 包离手即关屏
    }

    /** 背包 shift 点=整叠入账（账本只在服务端动——m95 铁律；客户端只清格做预测，服务端同步兜正）。 */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        if (st.isEmpty() || st.getMaxStackSize() <= 1 || !st.getComponentsPatch().isEmpty()) return ItemStack.EMPTY; // 只收普通可堆叠物
        if (!p.level().isClientSide) {
            ItemStack v = vault();
            if (v.isEmpty()) return ItemStack.EMPTY;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            if (!PortableVaultItem.vaultAdd(v, id, st.getCount())) {
                p.displayClientMessage(Component.literal("仓库类型已满（config portableVaultTypeCap）"), true);
                return ItemStack.EMPTY;
            }
        }
        slot.setByPlayer(ItemStack.EMPTY);
        return ItemStack.EMPTY; // 已入账本，终结循环
    }

    /** m312 服务端取物（接收器调）。mode 0=一组64 / 1=拿满一格 / 2=取尽填背包；余量永留账本绝不落地。 */
    public void take(net.minecraft.server.level.ServerPlayer p, String rawId, int mode) {
        ItemStack v = vault();
        if (v.isEmpty()) return;
        ResourceLocation idf = ResourceLocation.tryParse(rawId);
        if (idf == null) return;
        net.minecraft.world.item.Item it = BuiltInRegistries.ITEM.get(idf);
        if (it == net.minecraft.world.item.Items.AIR) return;
        var acct = PortableVaultItem.ledger(v);
        long left = acct.getLong(rawId);
        if (left <= 0) return;
        int slotMax = Math.max(1, new ItemStack(it).getMaxStackSize());
        long budget = switch (mode) {
            case 1 -> Math.min(left, slotMax);
            case 2 -> left;
            default -> Math.min(left, 64L);
        };
        long taken = 0;
        while (budget > 0) {
            int chunk = (int) Math.min(budget, slotMax);
            ItemStack give = new ItemStack(it, chunk);
            p.getInventory().add(give);
            int in = chunk - give.getCount();
            taken += in;
            budget -= in;
            if (in < chunk) break; // 背包满：余量天然留账本（只扣实收）
        }
        if (taken <= 0) { p.displayClientMessage(Component.literal("背包没有空位"), true); return; }
        long remain = left - taken;
        if (remain > 0) acct.putLong(rawId, remain); else acct.remove(rawId);
        PortableVaultItem.writeLedger(v, acct);
        p.sendMessage(Component.literal("叮！取出 " + new ItemStack(it).getHoverName().getString() + " ×" + taken
                + (budget > 0 ? "（背包已满，余量在库）" : "")), true);
    }
}
