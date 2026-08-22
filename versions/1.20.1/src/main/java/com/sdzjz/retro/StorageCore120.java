package com.sdzjz.retro;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** m441 刀①最小 BE：只占坑证注册链通；账本/精确条目/FabricLedger120 随刀②（m442）来，
 *  语义蓝本=Legacy StorageCoreBlockEntity（逐段对照新写，不复制改）。 */
public final class StorageCore120 extends BlockEntity {

    public StorageCore120(BlockPos pos, BlockState state) {
        super(RetroBlocks.STORAGE_CORE_BE, pos, state);
    }
}
