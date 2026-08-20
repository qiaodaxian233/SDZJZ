package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** 数据面板：数字化仓储终端。存物品为数据（近乎无限），可浏览/取出。 */
public class DataPanelBlock extends BlockWithEntity {

    public static final MapCodec<DataPanelBlock> CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(createSettingsCodec()).apply(i, DataPanelBlock::new));

    public DataPanelBlock(Settings settings) {
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

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DataPanelBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // 手持终端/链接器时放行，让物品的 useOnBlock 去绑定，而不是直接开界面
        net.minecraft.item.Item held = player.getMainHandStack().getItem();
        if (held instanceof com.sdzjz.item.TerminalItem || held instanceof com.sdzjz.item.LinkerItem) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof DataPanelBlockEntity panel) {
                player.openHandledScreen(panel);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, ModBlockEntities.DATA_PANEL_BE, DataPanelBlockEntity::tick);
    }
}
