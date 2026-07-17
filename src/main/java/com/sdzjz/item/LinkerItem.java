package com.sdzjz.item;

import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 数据链接器：右键数据面板记录目标 → 右键结构核心把核心绑定到该面板（绑定优先于自动路由）。
 * 多个核心绑到同一面板 = 面板聚合多核心产出。潜行右键核心 = 解绑。
 */
public class LinkerItem extends Item {

    private static final String K_POS = "sdzjz_pos", K_DIM = "sdzjz_dim";

    public LinkerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos();
        PlayerEntity player = ctx.getPlayer();
        ItemStack stack = ctx.getStack();
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof DataPanelBlockEntity) {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong(K_POS, pos.asLong());
            nbt.putString(K_DIM, world.getRegistryKey().getValue().toString());
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            msg(player, "已记录数据面板 " + pos.toShortString());
            return ActionResult.SUCCESS;
        }

        if (be instanceof StructureCoreBlockEntity core) {
            if (player != null && player.isSneaking()) {
                core.setBound(null, null);
                msg(player, "核心已解绑");
                return ActionResult.SUCCESS;
            }
            NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (c == null || !c.copyNbt().contains(K_POS)) {
                msg(player, "先右键一个数据面板记录目标");
                return ActionResult.FAIL;
            }
            NbtCompound nbt = c.copyNbt();
            BlockPos target = BlockPos.fromLong(nbt.getLong(K_POS));
            core.setBound(target, nbt.getString(K_DIM));
            msg(player, "核心已绑定到面板 " + target.toShortString());
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private static void msg(PlayerEntity player, String s) {
        if (player != null) player.sendMessage(Text.literal(s), true);
    }
}
