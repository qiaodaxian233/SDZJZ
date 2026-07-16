package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.StructureCoreBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 方块注册。结构核心 = 基础宿主；超大工作台 = 高一阶（更高并发/产量）。 */
public class ModBlocks {

    public static final StructureCoreBlock STRUCTURE_CORE =
            reg("structure_core", new StructureCoreBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 1));

    public static final StructureCoreBlock SUPER_BENCH =
            reg("super_bench", new StructureCoreBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 2));

    private static StructureCoreBlock reg(String name, StructureCoreBlock block) {
        StructureCoreBlock b = Registry.register(Registries.BLOCK, Sdzjz.id(name), block);
        Registry.register(Registries.ITEM, Sdzjz.id(name), new BlockItem(b, new Item.Settings()));
        return b;
    }

    public static void init() {
        // 触发静态初始化
    }
}
