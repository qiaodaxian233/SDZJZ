package com.sdzjz.item;

import com.sdzjz.block.DataPanelBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 自动喂食器（m80d）：放在背包里，饿了自动吃「你选定的食物」。
 * 选食物：副手拿食物 + 主手拿喂食器右键 → 设定；潜行右键 → 清除。
 * 食物来源：优先吃背包里的，没有则从绑定的数据面板网络取（右键面板绑定）。
 * 触发：饱食度掉到「吃一份不浪费」时进食（或 ≤6 强制进食）。
 * 限制：只补饥饿与饱和；金苹果等的药水效果不生效（那是原版进食动作附带的）。
 */
public class AutoFeederItem extends Item {

    private static final String K_POS = "sdzjz_pos", K_DIM = "sdzjz_dim";
    static final String K_FOOD = "sdzjz_food"; // 终端镶嵌时要读

    public AutoFeederItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        if (world.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        if (world.getBlockEntity(pos) instanceof DataPanelBlockEntity) {
            CustomData oc = ctx.getItemInHand().get(DataComponents.CUSTOM_DATA);
            CompoundTag nbt = oc != null ? oc.copyTag() : new CompoundTag(); // 保留已选食物
            nbt.putLong(K_POS, pos.asLong());
            nbt.putString(K_DIM, world.dimension().location().toString());
            ctx.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            msg(ctx.getPlayer(), "喂食器已绑定面板 " + pos.toShortString());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) return InteractionResultHolder.success(stack);
        CustomData oc = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = oc != null ? oc.copyTag() : new CompoundTag();
        if (player.isShiftKeyDown()) {
            nbt.remove(K_FOOD);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            msg(player, "已清除选定食物");
            return InteractionResultHolder.success(stack);
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.get(DataComponents.FOOD) != null) {
            nbt.putString(K_FOOD, BuiltInRegistries.ITEM.getKey(off.getItem()).toString());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            msg(player, "已设定食物: " + off.getHoverName().getString());
            return InteractionResultHolder.success(stack);
        }
        String cur = nbt.getString(K_FOOD);
        msg(player, cur.isEmpty() ? "副手拿食物再右键=设定；潜行右键=清除"
                : "当前食物: " + BuiltInRegistries.ITEM.get(ResourceLocation.parse(cur)).getDescription().getString());
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (world.isClientSide || world.getGameTime() % 40 != 0) return;
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return;
        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        if (c == null) return;
        CompoundTag nbt = c.copyTag();
        feedTick(world, player, nbt.getString(K_FOOD), nbt);
    }

    /** 共享进食核心（m82）：喂食器独立使用或镶嵌进终端都走这里；bindNbt 提供面板绑定（sdzjz_pos/dim）。 */
    static void feedTick(Level world, net.minecraft.server.level.ServerPlayer player, String foodId, CompoundTag bindNbt) {
        if (foodId == null || foodId.isEmpty()) return;
        Item food = BuiltInRegistries.ITEM.get(ResourceLocation.parse(foodId));
        FoodProperties fc = new ItemStack(food).get(DataComponents.FOOD);
        if (fc == null) return;
        int lvl = player.getFoodData().getFoodLevel();
        boolean fit = lvl <= 20 - fc.nutrition(); // 吃一份不浪费
        if (!fit && lvl > 6) return;              // ≤6 强制吃防饿死
        boolean got = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(food) && s.getComponentsPatch().isEmpty()) { s.shrink(1); got = true; break; }
        }
        if (!got && bindNbt.contains("sdzjz_pos")) {
            DataPanelBlockEntity panel = TerminalItem.resolvePanel(world, bindNbt);
            if (panel != null && panel.withdraw(foodId, 1) > 0) got = true;
        }
        if (!got) return;
        player.getFoodData().eat(fc); // 原版进食的饥饿+饱和路径（不含使用型效果）
        world.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_BURP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip,
                              net.minecraft.world.item.TooltipFlag type) {
        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        String cur = c != null ? c.copyTag().getString(K_FOOD) : "";
        if (cur.isEmpty()) {
            tooltip.add(Component.literal("副手拿食物+右键=选定要吃的").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("自动吃: " + BuiltInRegistries.ITEM.get(ResourceLocation.parse(cur)).getDescription().getString())
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        tooltip.add(Component.literal("饿了自动进食：背包优先，再从绑定面板取").withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    private static void msg(Player player, String s) {
        if (player != null) player.displayClientMessage(Component.literal(s), true);
    }
}
