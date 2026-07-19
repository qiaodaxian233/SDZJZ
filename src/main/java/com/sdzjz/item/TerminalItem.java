package com.sdzjz.item;

import com.sdzjz.block.DataPanelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 手持终端：右键数据面板 → 绑定该面板；右键(空手/地面) → 远程打开该面板的存储界面。
 * 依赖：目标面板所在区块须已加载（否则取不到方块实体）。跨维度也可，靠服务端持有真实面板、
 * 槽位内容下发同步显示（客户端无需该方块实体）。
 */
public class TerminalItem extends Item {

    private static final String K_POS = "sdzjz_pos", K_DIM = "sdzjz_dim", K_RESTOCK = "sdzjz_rst";
    private static final String K_LAST = "sdzjz_last", K_FEED = "sdzjz_feed", K_FFOOD = "sdzjz_ffood";

    public TerminalItem(Settings settings) {
        super(settings);
    }

    /** 右键数据面板：绑定；右键其它方块：转交 use() 打开。 */
    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos();
        if (world.getBlockEntity(pos) instanceof DataPanelBlockEntity) {
            NbtComponent oc = ctx.getStack().get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound nbt = oc != null ? oc.copyNbt() : new NbtCompound(); // 保留补货阈值等既有设置
            nbt.putLong(K_POS, pos.asLong());
            nbt.putString(K_DIM, world.getRegistryKey().getValue().toString());
            ctx.getStack().set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(ctx.getPlayer(), "终端已绑定面板 " + pos.toShortString());
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** 右键空手/地面：远程打开绑定的面板。 */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        if (player.isSneaking()) { // m80d：潜行右键循环自动补货阈值 关→16→32→64→关
            NbtComponent oc = stack.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound nbt = oc != null ? oc.copyNbt() : new NbtCompound();
            int th = nbt.getInt(K_RESTOCK);
            th = th == 0 ? 16 : th == 16 ? 32 : th == 32 ? 64 : 0;
            nbt.putInt(K_RESTOCK, th);
            if (th == 0) nbt.remove(K_LAST); // 关闭即清手持记忆
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(player, th == 0 ? "自动补货：关" : "自动补货：手持 < " + th + " 补齐；打空自动补一组");
            return TypedActionResult.success(stack);
        }

        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null || !c.copyNbt().contains(K_POS)) {
            msg(player, "先右键一个数据面板绑定终端");
            return TypedActionResult.fail(stack);
        }
        NbtCompound nbt = c.copyNbt();
        BlockPos target = BlockPos.fromLong(nbt.getLong(K_POS));
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString(K_DIM)));

        World tw = world;
        if (!world.getRegistryKey().equals(dimKey) && world instanceof net.minecraft.server.world.ServerWorld sw) {
            tw = sw.getServer().getWorld(dimKey);
        }
        if (tw == null || !(tw.getBlockEntity(target) instanceof DataPanelBlockEntity panel)) {
            msg(player, "面板不可达（区块未加载或已移除）");
            return TypedActionResult.fail(stack);
        }
        player.openHandledScreen(panel);
        return TypedActionResult.success(stack);
    }

    /** m82：补货(含"打空自动补一组") + 镶嵌喂食，终端在背包里即生效。 */
    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return;
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return;
        NbtCompound nbt = c.copyNbt();
        long t = world.getTime();
        if (t % 20 == 0) restockTick(stack, world, player, nbt);
        if (t % 40 == 0 && nbt.getBoolean(K_FEED))
            AutoFeederItem.feedTick(world, player, nbt.getString(K_FFOOD), nbt); // 镶嵌喂食：用终端自己的绑定取食物
    }

    private void restockTick(ItemStack stack, World world, net.minecraft.server.network.ServerPlayerEntity player, NbtCompound nbt) {
        int th = nbt.getInt(K_RESTOCK);
        if (th <= 0 || !nbt.contains(K_POS)) return;
        ItemStack hand = player.getMainHandStack();
        String last = nbt.getString(K_LAST);
        if (!hand.isEmpty()) {
            if (hand.getMaxCount() <= 1) return;                              // 只补可堆叠物
            if (!hand.getComponentChanges().isEmpty()) return;                 // 带组件的不按 id 补
            if (hand.getItem() instanceof TerminalItem || hand.getItem() instanceof AutoFeederItem) return;
            String id = net.minecraft.registry.Registries.ITEM.getId(hand.getItem()).toString();
            if (!id.equals(last)) { nbt.putString(K_LAST, id); stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt)); }
            int want = Math.min(th, hand.getMaxCount()) - hand.getCount();
            if (want <= 0) return;
            DataPanelBlockEntity panel = resolvePanel(world, nbt);
            if (panel == null) return;
            int got = panel.withdraw(id, want);
            if (got > 0) hand.increment(got);
        } else if (!last.isEmpty()) {
            // 手里打空了 → 按上次手持记忆直接补一组到手（用户点名的行为）
            DataPanelBlockEntity panel = resolvePanel(world, nbt);
            if (panel == null) return;
            net.minecraft.item.Item it = net.minecraft.registry.Registries.ITEM.get(Identifier.of(last));
            int got = panel.withdraw(last, Math.min(th, new ItemStack(it).getMaxCount()));
            if (got > 0) player.getInventory().setStack(player.getInventory().selectedSlot, new ItemStack(it, got));
        }
    }

    /** m82：喂食器像镶嵌一样装进终端——背包里把喂食器「右键点到」终端上=安装；右键空手点终端=取出。 */
    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, net.minecraft.screen.slot.Slot slot,
                             net.minecraft.util.ClickType clickType, PlayerEntity player,
                             net.minecraft.inventory.StackReference cursorStackReference) {
        if (clickType != net.minecraft.util.ClickType.RIGHT) return false;
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = c != null ? c.copyNbt() : new NbtCompound();
        if (otherStack.getItem() instanceof AutoFeederItem && !nbt.getBoolean(K_FEED)) { // 安装
            NbtComponent fc = otherStack.get(DataComponentTypes.CUSTOM_DATA);
            nbt.putBoolean(K_FEED, true);
            nbt.putString(K_FFOOD, fc != null ? fc.copyNbt().getString(AutoFeederItem.K_FOOD) : "");
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            otherStack.decrement(1);
            return true;
        }
        if (otherStack.isEmpty() && nbt.getBoolean(K_FEED)) { // 取出（左键仍可正常拿终端）
            ItemStack feeder = new ItemStack(com.sdzjz.registry.ModItems.AUTO_FEEDER, 1);
            String food = nbt.getString(K_FFOOD);
            if (!food.isEmpty()) {
                NbtCompound fn = new NbtCompound();
                fn.putString(AutoFeederItem.K_FOOD, food);
                feeder.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(fn));
            }
            nbt.remove(K_FEED);
            nbt.remove(K_FFOOD);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            cursorStackReference.set(feeder);
            return true;
        }
        return false;
    }

    /** 解析绑定的面板（可跨维度，区块须已加载）。 */
    static DataPanelBlockEntity resolvePanel(World world, NbtCompound nbt) {
        BlockPos target = BlockPos.fromLong(nbt.getLong(K_POS));
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString(K_DIM)));
        World tw = world;
        if (!world.getRegistryKey().equals(dimKey) && world instanceof net.minecraft.server.world.ServerWorld sw)
            tw = sw.getServer().getWorld(dimKey);
        if (tw == null) return null;
        BlockPos p = target;
        if (!tw.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return null;
        return tw.getBlockEntity(p) instanceof DataPanelBlockEntity dp ? dp : null;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, java.util.List<Text> tooltip,
                              net.minecraft.item.tooltip.TooltipType type) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        int th = c != null ? c.copyNbt().getInt(K_RESTOCK) : 0;
        tooltip.add(Text.literal(th > 0 ? "自动补货: 手持 < " + th + " 补齐，打空补一组（潜行右键调整）"
                : "自动补货: 关（潜行右键开启）").formatted(net.minecraft.util.Formatting.AQUA));
        NbtCompound tn = c != null ? c.copyNbt() : new NbtCompound();
        if (tn.getBoolean(K_FEED)) {
            String fo = tn.getString(K_FFOOD);
            String fn2 = fo.isEmpty() ? "未选食物" : net.minecraft.registry.Registries.ITEM
                    .get(Identifier.of(fo)).getName().getString();
            tooltip.add(Text.literal("已镶嵌: 自动喂食器（" + fn2 + "）· 右键空手取出")
                    .formatted(net.minecraft.util.Formatting.GREEN));
        } else {
            tooltip.add(Text.literal("背包里把喂食器右键点到终端上=镶嵌").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
    }

    private static void msg(PlayerEntity player, String s) {
        if (player != null) player.sendMessage(Text.literal(s), true);
    }
}
