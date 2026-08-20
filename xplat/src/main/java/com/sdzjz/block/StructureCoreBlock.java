package com.sdzjz.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.item.MachineItem;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 结构核心 / 超大工作台。tier=1 基础，tier=2 超大（更高并发与产量）。 */
public class StructureCoreBlock extends BaseEntityBlock {
    public final int tier;

    public static final MapCodec<StructureCoreBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    createSettingsCodec(),
                    Codec.INT.fieldOf("tier").forGetter(b -> b.tier)
            ).apply(instance, StructureCoreBlock::new));

    public StructureCoreBlock(Settings settings, int tier) {
        super(settings);
        this.tier = tier;
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
        return new StructureCoreBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClient) return InteractionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof StructureCoreBlockEntity core)) return InteractionResult.SUCCESS;
        ItemStack held = player.getStackInHand(InteractionHand.MAIN_HAND);
        if (!held.isEmpty()) {
            if (held.getItem() instanceof MachineItem || held.getItem() instanceof CaptureCageItem) {
                core.insertMachine(player, held); // m270 带玩家：节点上限拒绝走 actionbar 提示
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS; // 其它物品放行（升级改在画布节点格里加）
        }
        if (player.isSneaking()) {
            core.ejectOne(player);
            return InteractionResult.SUCCESS;
        }
        player.openHandledScreen(core);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof StructureCoreBlockEntity core) {
                core.dropAll(world, pos);
                if (core.chunkForceActive() && world instanceof net.minecraft.server.level.ServerLevel swr)
                    CoreChunkLoading.release(swr, pos, core.chunkOwnedFlag()); // m133 拆核心解除自身区块钉住（端点有期票自动过期）；m268 带所有权=管理员 /forceload 不误伤
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, ModBlockEntities.STRUCTURE_CORE_BE, StructureCoreBlockEntity::tick);
    }
}
