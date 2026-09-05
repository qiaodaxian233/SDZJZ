package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 村民交易所：右键打开交易 UI；破坏掉落合同。 */
public class TradeCenterBlock extends BaseEntityBlock {

    public static final MapCodec<TradeCenterBlock> CODEC = simpleCodec(TradeCenterBlock::new);

    public TradeCenterBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradeCenterBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof TradeCenterBlockEntity be) {
            com.sdzjz.loader.Menus.open(player, be); // m532（F1b）：带开屏数据的开屏走加载器口（Fabric=ExtendedScreenHandlerFactory 包一层）
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof TradeCenterBlockEntity be) {
                be.dropAll(world, pos);
            }
            super.onRemove(state, world, pos, newState, moved);
        }
    }
}
