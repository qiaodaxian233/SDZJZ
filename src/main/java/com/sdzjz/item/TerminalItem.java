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
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(player, th == 0 ? "自动补货：关" : "自动补货：手持物 < " + th + " 时从面板补齐");
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

    /** m80d 自动补货：终端在背包里即生效——主手可堆叠物少于阈值时，从绑定面板网络自动补到手里。 */
    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient || world.getTime() % 20 != 0) return;
        if (!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)) return;
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return;
        NbtCompound nbt = c.copyNbt();
        int th = nbt.getInt(K_RESTOCK);
        if (th <= 0 || !nbt.contains(K_POS)) return;
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty() || hand.getMaxCount() <= 1) return;               // 只补可堆叠物
        if (!hand.getComponentChanges().isEmpty()) return;                    // 带组件的不按 id 补
        if ("sdzjz".equals(net.minecraft.registry.Registries.ITEM.getId(hand.getItem()).getNamespace())
                && (hand.getItem() instanceof TerminalItem)) return;
        int want = Math.min(th, hand.getMaxCount()) - hand.getCount();
        if (want <= 0) return;
        DataPanelBlockEntity panel = resolvePanel(world, nbt);
        if (panel == null) return;
        int got = panel.withdraw(net.minecraft.registry.Registries.ITEM.getId(hand.getItem()).toString(), want);
        if (got > 0) hand.increment(got);
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
        tooltip.add(Text.literal(th > 0 ? "自动补货: 手持 < " + th + " 补齐（潜行右键调整）"
                : "自动补货: 关（潜行右键开启）").formatted(net.minecraft.util.Formatting.AQUA));
    }

    private static void msg(PlayerEntity player, String s) {
        if (player != null) player.sendMessage(Text.literal(s), true);
    }
}
