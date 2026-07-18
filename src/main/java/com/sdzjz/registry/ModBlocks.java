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
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModBlocks {

    public static final StructureCoreBlock STRUCTURE_CORE =
            reg("structure_core", new StructureCoreBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 1));

    public static final SuperBenchBlock SUPER_BENCH =
            reg("super_bench", new SuperBenchBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE)));

    public static final DataPanelBlock DATA_PANEL =
            reg("data_panel", new DataPanelBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final StorageCoreBlock STORAGE_CORE =
            reg("storage_core", new StorageCoreBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final DataCableBlock DATA_CABLE =
            reg("data_cable", new DataCableBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final WirelessNodeBlock WIRELESS_NODE =
            reg("wireless_node", new WirelessNodeBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final SatelliteNodeBlock SATELLITE_NODE =
            reg("satellite_node", new SatelliteNodeBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final TradeCenterBlock TRADE_CENTER =
            reg("trade_center", new TradeCenterBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    private static <T extends Block> T reg(String name, T block) {
        T b = Registry.register(Registries.BLOCK, Sdzjz.id(name), block);
        Registry.register(Registries.ITEM, Sdzjz.id(name), new BlockItem(b, new Item.Settings()));
        return b;
    }

    public static void init() {}
}
