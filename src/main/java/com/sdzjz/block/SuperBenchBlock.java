package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** 超大工作台：12×12 合成站（无形状匹配机器配方）。网格仍随开关暂存在 Handler；
 *  m249 升级 BlockWithEntity——BE 零数据，只给客户端动画渲染器当挂点（悬浮全息合成网格）。
 *  注意 BlockWithEntity 默认渲染型 INVISIBLE，必须覆写回 MODEL（结构核心同款雷）。 */
public class SuperBenchBlock extends BlockWithEntity {

    private static final Text TITLE = Text.literal("超大工作台");
    public static final MapCodec<SuperBenchBlock> CODEC = createCodec(SuperBenchBlock::new); // yarn method_54094 已核

    public SuperBenchBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(net.minecraft.util.math.BlockPos pos, BlockState state) {
        return new SuperBenchBlockEntity(pos, state);
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
