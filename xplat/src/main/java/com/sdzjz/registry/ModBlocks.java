package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.DataCableBlock;
import com.sdzjz.block.SatelliteNodeBlock;
import com.sdzjz.block.WirelessNodeBlock;
import com.sdzjz.block.DataPanelBlock;
import com.sdzjz.block.StructureCoreBlock;
import com.sdzjz.block.StorageCoreBlock;
import com.sdzjz.block.SuperBenchBlock;
import com.sdzjz.block.TradeCenterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;

public class ModBlocks {

    public static final StructureCoreBlock STRUCTURE_CORE =
            reg("structure_core", new StructureCoreBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(), 1));

    public static final SuperBenchBlock SUPER_BENCH =
            reg("super_bench", new SuperBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)));

    public static final DataPanelBlock DATA_PANEL =
            reg("data_panel", new DataPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final StorageCoreBlock STORAGE_CORE =
            reg("storage_core", new StorageCoreBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final DataCableBlock DATA_CABLE =
            reg("data_cable", new DataCableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final WirelessNodeBlock WIRELESS_NODE =
            reg("wireless_node", new WirelessNodeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final SatelliteNodeBlock SATELLITE_NODE =
            reg("satellite_node", new SatelliteNodeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final TradeCenterBlock TRADE_CENTER =
            reg("trade_center", new TradeCenterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    private static <T extends Block> T reg(String name, T block) {
        T b = Registry.register(BuiltInRegistries.BLOCK, Sdzjz.id(name), block);
        Registry.register(BuiltInRegistries.ITEM, Sdzjz.id(name), new BlockItem(b, new Item.Properties()));
        return b;
    }

    public static void init() {}
}
