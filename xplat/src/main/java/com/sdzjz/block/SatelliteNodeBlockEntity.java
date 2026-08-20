package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** m156 卫星节点方块实体：空壳，只为挂扫描动画渲染器（几何在 geo.json，动画在 SatelliteNodeRenderer）。 */
public class SatelliteNodeBlockEntity extends BlockEntity {
    public SatelliteNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SATELLITE_NODE_BE, pos, state);
    }
}
