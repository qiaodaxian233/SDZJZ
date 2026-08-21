package com.sdzjz.item;

import com.sdzjz.block.DataPanelBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 手持终端：右键数据面板 → 绑定该面板；右键(空手/地面) → 远程打开该面板的存储界面。
 * 依赖：目标面板所在区块须已加载（否则取不到方块实体）。跨维度也可，靠服务端持有真实面板、
 * 槽位内容下发同步显示（客户端无需该方块实体）。
 */
public class TerminalItem extends Item {

    private static final String K_POS = "sdzjz_pos", K_DIM = "sdzjz_dim", K_RESTOCK = "sdzjz_rst";
    private static final String K_LAST = "sdzjz_last", K_FEED = "sdzjz_feed", K_FFOOD = "sdzjz_ffood";

    public TerminalItem(Properties settings) {
        super(settings);
    }

    /** 右键数据面板：绑定；右键其它方块：转交 use() 打开。 */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        if (world.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        if (world.getBlockEntity(pos) instanceof DataPanelBlockEntity) {
            CustomData oc = ctx.getItemInHand().get(DataComponents.CUSTOM_DATA);
            CompoundTag nbt = oc != null ? oc.copyTag() : new CompoundTag(); // 保留补货阈值等既有设置
            nbt.putLong(K_POS, pos.asLong());
            nbt.putString(K_DIM, world.dimension().location().toString());
            ctx.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            msg(ctx.getPlayer(), "终端已绑定面板 " + pos.toShortString());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 右键空手/地面：远程打开绑定的面板。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) return InteractionResultHolder.success(stack);

        if (player.isShiftKeyDown()) { // m80d：潜行右键循环自动补货阈值 关→16→32→64→关
            CustomData oc = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag nbt = oc != null ? oc.copyTag() : new CompoundTag();
            int th = nbt.getInt(K_RESTOCK);
            th = th == 0 ? 16 : th == 16 ? 32 : th == 32 ? 64 : 0;
            nbt.putInt(K_RESTOCK, th);
            if (th == 0) nbt.remove(K_LAST); // 关闭即清手持记忆
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            msg(player, th == 0 ? "自动补货：关" : "自动补货：手持 < " + th + " 补齐；打空自动补一组");
            return InteractionResultHolder.success(stack);
        }

        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        if (c == null || !c.copyTag().contains(K_POS)) {
            msg(player, "先右键一个数据面板绑定终端");
            return InteractionResultHolder.fail(stack);
        }
        CompoundTag nbt = c.copyTag();
        BlockPos target = BlockPos.of(nbt.getLong(K_POS));
        ResourceKey<Level> dimKey = ResourceKey.of(Registries.DIMENSION, ResourceLocation.parse(nbt.getString(K_DIM)));

        Level tw = world;
        if (!world.dimension().equals(dimKey) && world instanceof net.minecraft.server.level.ServerLevel sw) {
            tw = sw.getServer().getLevel(dimKey);
        }
        if (tw == null || !(tw.getBlockEntity(target) instanceof DataPanelBlockEntity panel)) {
            msg(player, "面板不可达（区块未加载或已移除）");
            return InteractionResultHolder.fail(stack);
        }
        // m303 AccessMode：远程开屏不再直走 openHandledScreen(panel)（那会进 BE.createMenu=方块模式），
        // 自带工厂把 remote=true 塞进 handler 构造链；开屏数据仍发 BlockPos，客户端工厂零改动。
        player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BlockPos>() {
            @Override public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer sp) { return panel.getBlockPos(); }
            @Override public Component getDisplayName() { return panel.getDisplayName(); }
            @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new com.sdzjz.screen.DataPanelScreenHandler(syncId, inv, panel, true);
            }
        });
        return InteractionResultHolder.success(stack);
    }

    /** m82：补货(含"打空自动补一组") + 镶嵌喂食，终端在背包里即生效。 */
    @Override
    public void inventoryTick(ItemStack stack, Level world, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (world.isClientSide) return;
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return;
        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        if (c == null) return;
        CompoundTag nbt = c.copyTag();
        long t = world.getGameTime();
        if (t % 20 == 0) restockTick(stack, world, player, nbt);
        if (t % 40 == 0 && nbt.getBoolean(K_FEED))
            AutoFeederItem.feedTick(world, player, nbt.getString(K_FFOOD), nbt); // 镶嵌喂食：用终端自己的绑定取食物
    }

    private void restockTick(ItemStack stack, Level world, net.minecraft.server.level.ServerPlayer player, CompoundTag nbt) {
        int th = nbt.getInt(K_RESTOCK);
        if (th <= 0 || !nbt.contains(K_POS)) return;
        ItemStack hand = player.getMainHandItem();
        String last = nbt.getString(K_LAST);
        if (!hand.isEmpty()) {
            if (hand.getMaxStackSize() <= 1) return;                              // 只补可堆叠物
            if (!hand.getComponentsPatch().isEmpty()) return;                 // 带组件的不按 id 补
            if (hand.getItem() instanceof TerminalItem || hand.getItem() instanceof AutoFeederItem) return;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
            if (!id.equals(last)) { nbt.putString(K_LAST, id); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt)); }
            int want = Math.min(th, hand.getMaxStackSize()) - hand.getCount();
            if (want <= 0) return;
            DataPanelBlockEntity panel = resolvePanel(world, nbt);
            if (panel == null) return;
            int got = panel.withdraw(id, want);
            if (got > 0) hand.grow(got);
        } else if (!last.isEmpty()) {
            // 手里打空了 → 按上次手持记忆直接补一组到手（用户点名的行为）
            DataPanelBlockEntity panel = resolvePanel(world, nbt);
            if (panel == null) return;
            net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(last));
            int got = panel.withdraw(last, Math.min(th, new ItemStack(it).getMaxStackSize()));
            if (got > 0) player.getInventory().setItem(player.getInventory().selected, new ItemStack(it, got));
        }
    }

    /** m82：喂食器像镶嵌一样装进终端——背包里把喂食器「右键点到」终端上=安装；右键空手点终端=取出。 */
    @Override
    public boolean onPress(ItemStack stack, ItemStack otherStack, net.minecraft.world.inventory.Slot slot,
                             net.minecraft.world.inventory.ClickAction clickType, Player player,
                             net.minecraft.world.entity.SlotAccess cursorStackReference) {
        if (clickType != net.minecraft.world.inventory.ClickAction.SECONDARY) return false;
        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = c != null ? c.copyTag() : new CompoundTag();
        if (otherStack.getItem() instanceof AutoFeederItem && !nbt.getBoolean(K_FEED)) { // 安装
            CustomData fc = otherStack.get(DataComponents.CUSTOM_DATA);
            nbt.putBoolean(K_FEED, true);
            nbt.putString(K_FFOOD, fc != null ? fc.copyTag().getString(AutoFeederItem.K_FOOD) : "");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            otherStack.shrink(1);
            return true;
        }
        if (otherStack.isEmpty() && nbt.getBoolean(K_FEED)) { // 取出（左键仍可正常拿终端）
            ItemStack feeder = new ItemStack(com.sdzjz.registry.ModItems.AUTO_FEEDER, 1);
            String food = nbt.getString(K_FFOOD);
            if (!food.isEmpty()) {
                CompoundTag fn = new CompoundTag();
                fn.putString(AutoFeederItem.K_FOOD, food);
                feeder.set(DataComponents.CUSTOM_DATA, CustomData.of(fn));
            }
            nbt.remove(K_FEED);
            nbt.remove(K_FFOOD);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            cursorStackReference.set(feeder);
            return true;
        }
        return false;
    }

    /** m303：该物品是否为绑定到指定面板的终端（远程屏 canUse 生命周期核对用；
     *  K_POS/K_DIM 键保持私有，本方法是外部判定的唯一出口）。 */
    public static boolean isBoundTo(ItemStack stack, BlockPos pos, Level world) {
        if (world == null || pos == null || !(stack.getItem() instanceof TerminalItem)) return false;
        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        if (c == null) return false;
        CompoundTag nbt = c.copyTag();
        return nbt.contains(K_POS)
                && BlockPos.of(nbt.getLong(K_POS)).equals(pos)
                && world.dimension().location().toString().equals(nbt.getString(K_DIM));
    }

    /** 解析绑定的面板（可跨维度，区块须已加载）。 */
    static DataPanelBlockEntity resolvePanel(Level world, CompoundTag nbt) {
        BlockPos target = BlockPos.of(nbt.getLong(K_POS));
        ResourceKey<Level> dimKey = ResourceKey.of(Registries.DIMENSION, ResourceLocation.parse(nbt.getString(K_DIM)));
        Level tw = world;
        if (!world.dimension().equals(dimKey) && world instanceof net.minecraft.server.level.ServerLevel sw)
            tw = sw.getServer().getLevel(dimKey);
        if (tw == null) return null;
        BlockPos p = target;
        if (!tw.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
        return tw.getBlockEntity(p) instanceof DataPanelBlockEntity dp ? dp : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip,
                              net.minecraft.world.item.TooltipFlag type) {
        CustomData c = stack.get(DataComponents.CUSTOM_DATA);
        int th = c != null ? c.copyTag().getInt(K_RESTOCK) : 0;
        tooltip.add(Component.literal(th > 0 ? "自动补货: 手持 < " + th + " 补齐，打空补一组（潜行右键调整）"
                : "自动补货: 关（潜行右键开启）").withStyle(net.minecraft.ChatFormatting.AQUA));
        CompoundTag tn = c != null ? c.copyTag() : new CompoundTag();
        if (tn.getBoolean(K_FEED)) {
            String fo = tn.getString(K_FFOOD);
            String fn2 = fo.isEmpty() ? "未选食物" : net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(ResourceLocation.parse(fo)).getDescription().getString();
            tooltip.add(Component.literal("已镶嵌: 自动喂食器（" + fn2 + "）· 右键空手取出")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("背包里把喂食器右键点到终端上=镶嵌").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    private static void msg(Player player, String s) {
        if (player != null) player.displayClientMessage(Component.literal(s), true);
    }
}
