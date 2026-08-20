package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** 超大工作台：12×12 合成站（无形状匹配机器配方）。网格仍随开关暂存在 Handler；
 *  m249 升级 BaseEntityBlock——BE 零数据当渲染挂点；m277 全息 BER 退役改贴图帧动画后
 *  BE 暂留（撤 BaseEntityBlock 会让旧档已放置方块丢 BE 报孤儿告警，退役议题挂待办池）。
 *  注意 BaseEntityBlock 默认渲染型 INVISIBLE，必须覆写回 MODEL（结构核心同款雷）。 */
public class SuperBenchBlock extends BaseEntityBlock {

    private static final Component TITLE = Component.literal("超大工作台");
    public static final MapCodec<SuperBenchBlock> CODEC = createCodec(SuperBenchBlock::new); // yarn method_54094 已核

    public SuperBenchBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderType(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        return new SuperBenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClient) {
            player.openHandledScreen(new SimpleMenuProvider(
                    (syncId, inv, p) -> new SuperBenchScreenHandler(syncId, inv, ContainerLevelAccess.create(world, pos)),
                    TITLE));
        }
        return InteractionResult.SUCCESS;
    }
}
