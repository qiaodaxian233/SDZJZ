package com.sdzjz.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sdzjz.item.CaptureCageItem;
import com.sdzjz.item.MachineItem;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ItemScatterer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** 结构核心 / 超大工作台。tier=1 基础，tier=2 超大（更高并发与产量）。 */
public class StructureCoreBlock extends BlockWithEntity {
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
        return new StructureCoreBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof StructureCoreBlockEntity core)) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        if (!held.isEmpty()) {
            if (held.getItem() instanceof MachineItem || held.getItem() instanceof CaptureCageItem) {
                core.insertMachine(held);
                return ActionResult.SUCCESS;
            }
            if (held.isOf(ModItems.SPEED_UPGRADE) || held.isOf(ModItems.COUNT_UPGRADE) || held.isOf(ModItems.PARALLEL_UPGRADE)) {
                core.insertUpgrade(held);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS; // 其它物品放行（可正常放置方块等）
        }
        if (player.isSneaking()) {
            core.ejectOne(player);
            return ActionResult.SUCCESS;
        }
        player.openHandledScreen(core);
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof StructureCoreBlockEntity core) {
                core.dropAll(world, pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, ModBlockEntities.STRUCTURE_CORE_BE, StructureCoreBlockEntity::tick);
    }
}
