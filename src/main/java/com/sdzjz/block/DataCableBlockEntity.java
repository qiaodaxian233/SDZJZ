package com.sdzjz.block;

import com.sdzjz.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** 数据线方块实体：能量脉冲动画的渲染载体（BER 挂点）；m224 起兼作抽取口探测入口（引擎 m225 接线）。 */
public class DataCableBlockEntity extends BlockEntity {
    public DataCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_CABLE_BE, pos, state);
    }

    /** m224 邻接可抽取存储探测：任意暴露 Fabric Transfer API 的容器都算（原版箱子有 Fabric 内建适配；
     *  Tom's Simple Storage / AE2 / Storage Drawers / Create 等一切 Fabric 物流模组即插即用——走标准
     *  API 不做逐模组集成，即多版本兼容口径）。自家网络方块一律排除：存储核心自身就暴露 FTA（m161c），
     *  抽给它=左手倒右手；结构核心实现 Inventory 会被 Fabric 兜底适配捞到，同样排除。 */
    public static List<Storage<ItemVariant>> adjacentStorages(World world, BlockPos pos) {
        List<Storage<ItemVariant>> out = new ArrayList<>();
        if (world == null || world.isClient) return out;
        for (Direction d : Direction.values()) {
            BlockPos np = pos.offset(d);
            BlockEntity be = world.getBlockEntity(np);
            if (be != null && be.getClass().getName().startsWith("com.sdzjz")) continue; // 自家网络方块不作抽取目标
            Storage<ItemVariant> st = ItemStorage.SIDED.find(world, np, d.getOpposite()); // 先按贴线面查
            if (st == null) st = ItemStorage.SIDED.find(world, np, null);                 // 部分模组只登记无侧访问
            if (st != null && st.supportsInsertion()) out.add(st);
        }
        return out;
    }
}
