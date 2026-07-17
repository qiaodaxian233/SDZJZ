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

    private static final String K_POS = "sdzjz_pos", K_DIM = "sdzjz_dim";

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
            NbtCompound nbt = new NbtCompound();
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

    private static void msg(PlayerEntity player, String s) {
        if (player != null) player.sendMessage(Text.literal(s), true);
    }
}
