package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 数据面板：数字化仓储终端。存物品为数据（近乎无限），可浏览/取出。 */
public class DataPanelBlock extends BaseEntityBlock {

    public static final MapCodec<DataPanelBlock> CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(createSettingsCodec()).apply(i, DataPanelBlock::new));

    public DataPanelBlock(Settings settings) {
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

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DataPanelBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        // 手持终端/链接器时放行，让物品的 useOnBlock 去绑定，而不是直接开界面
        net.minecraft.world.item.Item held = player.getMainHandStack().getItem();
        if (held instanceof com.sdzjz.item.TerminalItem || held instanceof com.sdzjz.item.LinkerItem) {
            return InteractionResult.PASS;
        }
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof DataPanelBlockEntity panel) {
                player.openHandledScreen(panel);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        // m126a：网格常驻 BE 后拆方块必须散落内容物（照 TradeCenterBlock 同款样板，绝不吞）
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof DataPanelBlockEntity panel) {
                panel.dropCraftGrid(world, pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, ModBlockEntities.DATA_PANEL_BE, DataPanelBlockEntity::tick);
    }
}
