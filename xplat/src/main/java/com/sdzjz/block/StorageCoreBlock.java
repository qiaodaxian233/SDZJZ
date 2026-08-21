package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 存储核心方块：手持存储升级右键=升级(提升类型上限)；空手右键=显示用量。 */
public class StorageCoreBlock extends BaseEntityBlock {

    public static final MapCodec<StorageCoreBlock> CODEC = simpleCodec(StorageCoreBlock::new);

    public StorageCoreBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCoreBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.STORAGE_CORE_BE, StorageCoreBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.is(ModItems.STORAGE_UPGRADE)) {
            if (core.maxTypes() == Integer.MAX_VALUE) { // m98：无限类型下不白扣升级
                player.displayClientMessage(Component.literal("存储核心为无限类型，无需升级（config storageTypesPerTier 可启用上限机制）"), true);
                return InteractionResult.SUCCESS;
            }
            core.upgrade();
            held.shrink(1);
            player.displayClientMessage(Component.literal("存储核心已升级：类型上限 " + core.maxTypes()), true);
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty()) {
            String cap = core.maxTypes() == Integer.MAX_VALUE ? "已存 " + core.usedTypes() + " 种 · 类型无限"
                    : "类型 " + core.usedTypes() + "/" + core.maxTypes();
            player.displayClientMessage(Component.literal("存储核心：" + cap + "（用数据面板/终端访问内容）"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
