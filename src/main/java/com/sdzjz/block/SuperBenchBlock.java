package com.sdzjz.block;

import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** 超大工作台：12×12 合成站（无形状匹配机器配方）。仿原版工作台，无方块实体，网格随开关暂存。 */
public class SuperBenchBlock extends Block {

    private static final Text TITLE = Text.literal("超大工作台");

    public SuperBenchBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inv, p) -> new SuperBenchScreenHandler(syncId, inv, ScreenHandlerContext.create(world, pos)),
                    TITLE));
        }
        return ActionResult.SUCCESS;
    }
}
