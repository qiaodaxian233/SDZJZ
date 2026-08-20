package com.sdzjz.item;

import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 数据链接器：右键存储核心记录目标 → 右键结构核心把核心绑定到该目标（绑定优先于自动路由）。
 * 多个核心绑到同一目标 = 聚合多核心产出。潜行右键核心 = 解绑。
 * m227 起兼任网络扳手（原 m224 扳手退役并入本工具）：右键数据线=抽取口配置界面（9 幽灵过滤槽+启停钮），
 * 潜行右键数据线=快速开/关抽取口。数据线分支与绑定分支目标方块不同，零冲突。
 */
public class LinkerItem extends Item {

    private static final String K_POS = "sdzjz_pos", K_DIM = "sdzjz_dim";

    public LinkerItem(Settings settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext ctx) {
        Level world = ctx.getWorld();
        if (world.isClient) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getStack();
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof com.sdzjz.block.DataCableBlockEntity cable) { // m227：原扳手功能（m224~m226）
            if (player != null) {
                cable.claimOwner(player); // m229 配置即认领：转化桌出售的 EMC 记到最后配置者账上
                if (player.isSneaking()) { // 潜行右键：手臂=断开该面连接；缆芯已断侧=恢复；缆芯=快速开/关
                    double lx = ctx.getHitPos().x - pos.getX() - 0.5,
                           ly = ctx.getHitPos().y - pos.getY() - 0.5,
                           lz = ctx.getHitPos().z - pos.getZ() - 0.5;
                    net.minecraft.core.Direction hitDir = net.minecraft.core.Direction.getFacing(lx, ly, lz);
                    double mag = Math.abs(switch (hitDir.getAxis()) { case X -> lx; case Y -> ly; default -> lz; });
                    if (mag > 0.14) { // m233 缆芯半宽 2/16=0.125，超过即点在手臂/插头上：断开该面
                        cable.toggleFace(hitDir);
                        com.sdzjz.block.DataCableBlock.refreshEnd(world, pos, hitDir);
                        player.sendMessage(Component.translatable(cable.faceDisabled(hitDir)
                                ? "sdzjz.extract_port.face_off" : "sdzjz.extract_port.face_on", hitDir.getName()), true);
                    } else if (cable.faceDisabled(ctx.getSide())) { // m233 点缆芯的已断开侧=恢复该面
                        cable.toggleFace(ctx.getSide());
                        com.sdzjz.block.DataCableBlock.refreshEnd(world, pos, ctx.getSide());
                        player.sendMessage(Component.translatable("sdzjz.extract_port.face_on", ctx.getSide().getName()), true);
                    } else { // 缆芯=快速开/关抽取口（m225 原样）
                        int n = com.sdzjz.block.DataCableBlockEntity.scanAdjacent(world, pos).blockCount(); // m228 计邻块数
                        cable.setExtractOn(!cable.extractOn());
                        player.sendMessage(cable.extractOn()
                                ? Component.translatable("sdzjz.extract_port.on", n)
                                : Component.translatable("sdzjz.extract_port.off"), true);
                    }
                } else { // 右键=抽取口配置界面
                    player.openHandledScreen(cable);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (be instanceof StorageCoreBlockEntity) {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong(K_POS, pos.asLong());
            nbt.putString(K_DIM, world.getRegistryKey().getValue().toString());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            msg(player, "已记录数据面板 " + pos.toShortString());
            return InteractionResult.SUCCESS;
        }

        if (be instanceof StructureCoreBlockEntity core) {
            if (player != null && player.isSneaking()) {
                core.setBound(null, null);
                msg(player, "核心已解绑");
                return InteractionResult.SUCCESS;
            }
            CustomData c = stack.get(DataComponents.CUSTOM_DATA);
            if (c == null || !c.copyNbt().contains(K_POS)) {
                msg(player, "先右键一个数据面板记录目标");
                return InteractionResult.FAIL;
            }
            CompoundTag nbt = c.copyNbt();
            BlockPos target = BlockPos.fromLong(nbt.getLong(K_POS));
            core.setBound(target, nbt.getString(K_DIM));
            msg(player, "核心已绑定到面板 " + target.toShortString());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void msg(Player player, String s) {
        if (player != null) player.sendMessage(Component.literal(s), true);
    }
}
