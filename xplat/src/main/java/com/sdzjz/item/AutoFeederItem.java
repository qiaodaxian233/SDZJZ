package com.sdzjz.item;

import com.sdzjz.block.DataPanelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

    public AutoFeederItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos();
        if (world.getBlockEntity(pos) instanceof DataPanelBlockEntity) {
            NbtComponent oc = ctx.getStack().get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound nbt = oc != null ? oc.copyNbt() : new NbtCompound(); // 保留已选食物
            nbt.putLong(K_POS, pos.asLong());
            nbt.putString(K_DIM, world.getRegistryKey().getValue().toString());
            ctx.getStack().set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(ctx.getPlayer(), "喂食器已绑定面板 " + pos.toShortString());
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        NbtComponent oc = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = oc != null ? oc.copyNbt() : new NbtCompound();
        if (player.isSneaking()) {
            nbt.remove(K_FOOD);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(player, "已清除选定食物");
            return TypedActionResult.success(stack);
        }
        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty() && off.get(DataComponentTypes.FOOD) != null) {
            nbt.putString(K_FOOD, Registries.ITEM.getId(off.getItem()).toString());
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(player, "已设定食物: " + off.getName().getString());
            return TypedActionResult.success(stack);
        }
        String cur = nbt.getString(K_FOOD);
        msg(player, cur.isEmpty() ? "副手拿食物再右键=设定；潜行右键=清除"
                : "当前食物: " + Registries.ITEM.get(Identifier.of(cur)).getName().getString());
        return TypedActionResult.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient || world.getTime() % 40 != 0) return;
        if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return;
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return;
        NbtCompound nbt = c.copyNbt();
        feedTick(world, player, nbt.getString(K_FOOD), nbt);
    }

    /** 共享进食核心（m82）：喂食器独立使用或镶嵌进终端都走这里；bindNbt 提供面板绑定（sdzjz_pos/dim）。 */
    static void feedTick(World world, net.minecraft.server.network.ServerPlayerEntity player, String foodId, NbtCompound bindNbt) {
        if (foodId == null || foodId.isEmpty()) return;
        Item food = Registries.ITEM.get(Identifier.of(foodId));
        FoodComponent fc = new ItemStack(food).get(DataComponentTypes.FOOD);
        if (fc == null) return;
        int lvl = player.getHungerManager().getFoodLevel();
        boolean fit = lvl <= 20 - fc.nutrition(); // 吃一份不浪费
        if (!fit && lvl > 6) return;              // ≤6 强制吃防饿死
        boolean got = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(food) && s.getComponentChanges().isEmpty()) { s.decrement(1); got = true; break; }
        }
        if (!got && bindNbt.contains("sdzjz_pos")) {
            DataPanelBlockEntity panel = TerminalItem.resolvePanel(world, bindNbt);
            if (panel != null && panel.withdraw(foodId, 1) > 0) got = true;
        }
        if (!got) return;
        player.getHungerManager().eat(fc); // 原版进食的饥饿+饱和路径（不含使用型效果）
        world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_BURP,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, java.util.List<Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType type) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        String cur = c != null ? c.copyNbt().getString(K_FOOD) : "";
        if (cur.isEmpty()) {
            tooltip.add(Text.literal("副手拿食物+右键=选定要吃的").formatted(net.minecraft.util.Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("自动吃: " + Registries.ITEM.get(Identifier.of(cur)).getName().getString())
                    .formatted(net.minecraft.util.Formatting.GREEN));
        }
        tooltip.add(Text.literal("饿了自动进食：背包优先，再从绑定面板取").formatted(net.minecraft.util.Formatting.AQUA));
    }

    private static void msg(PlayerEntity player, String s) {
        if (player != null) player.sendMessage(Text.literal(s), true);
    }
}
