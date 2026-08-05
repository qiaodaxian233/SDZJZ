package com.sdzjz.item;

import com.sdzjz.block.DataCableBlock;
import com.sdzjz.block.DataCableBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * m224 网络扳手：数据线抽取口的配置工具。
 * 右键数据线 = 探测旁边可对接的存储（任意暴露 Fabric Transfer API 的容器：原版箱子内建适配，
 * Tom's Simple Storage / AE2 / Storage Drawers / Create 等一切 Fabric 物流模组即插即用——
 * 走标准 API 不做逐模组集成，这就是多版本兼容口径）。
 * 后续里程碑：潜行右键=快速开/关抽取口（m225），右键=抽取口配置界面（m226）。
 */
public class WrenchItem extends Item {

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        if (!(world.getBlockState(pos).getBlock() instanceof DataCableBlock)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        PlayerEntity player = ctx.getPlayer();
        int n = DataCableBlockEntity.adjacentStorages(world, pos).size();
        if (player != null && player.isSneaking() // m225 潜行右键=快速开/关抽取口（右键=配置界面留 m226）
                && world.getBlockEntity(pos) instanceof DataCableBlockEntity cable) {
            cable.setExtractOn(!cable.extractOn());
            player.sendMessage(cable.extractOn()
                    ? Text.translatable("sdzjz.wrench.on", n)
                    : Text.translatable("sdzjz.wrench.off"), true);
            return ActionResult.SUCCESS;
        }
        if (player != null) player.sendMessage(n > 0
                ? Text.translatable("sdzjz.wrench.found", n)
                : Text.translatable("sdzjz.wrench.none"), true);
        return ActionResult.SUCCESS;
    }
}
