package com.sdzjz.block;

import com.mojang.serialization.MapCodec;
import com.sdzjz.registry.ModBlockEntities;
import com.sdzjz.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** 存储核心方块：手持存储升级右键=升级(提升类型上限)；空手右键=显示用量。 */
public class StorageCoreBlock extends BlockWithEntity {

    public static final MapCodec<StorageCoreBlock> CODEC = createCodec(StorageCoreBlock::new);

    public StorageCoreBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCoreBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, ModBlockEntities.STORAGE_CORE_BE, StorageCoreBlockEntity::tick);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        if (held.isOf(ModItems.STORAGE_UPGRADE)) {
            core.upgrade();
            held.decrement(1);
            player.sendMessage(Text.literal("存储核心已升级：类型上限 " + core.maxTypes()), true);
            return ActionResult.SUCCESS;
        }
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("存储核心：类型 " + core.usedTypes() + "/" + core.maxTypes()
                    + "（用数据面板/终端访问内容）"), true);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}
