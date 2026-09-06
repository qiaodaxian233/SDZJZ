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

    // m535（F1d）注册分期：reg 只注册方块；八个 BlockItem 挪到 registerBlockItems()，由 ModItems 静态段**首句**调用
    // （Fabric 侧 ITEM 注册表顺序逐位不变：仍是八个方块物品在前、其余物品在后；BLOCK 顺序不变）。
    // 为什么拆：NeoForge 每个注册表只在自己的 RegisterEvent 期解冻——BLOCK 期里碰 ITEM 注册表直接抛
    // "Registry is already frozen"（评估报告 P0 点名的生命周期检查点），方块与物品同句注册在那边活不了。
    private static <T extends Block> T reg(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, Sdzjz.id(name), block);
    }

    /** 八个方块物品（顺序=上方字段顺序，与 m535 前逐位一致）。ITEM 期调用：Fabric=ModItems 类初始化首句；NeoForge=RegisterEvent(ITEM) 里触发 ModItems 类初始化。 */
    static void registerBlockItems() {
        for (Block b : new Block[]{STRUCTURE_CORE, SUPER_BENCH, DATA_PANEL, STORAGE_CORE, DATA_CABLE, WIRELESS_NODE, SATELLITE_NODE, TRADE_CENTER})
            Registry.register(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK.getKey(b), new BlockItem(b, new Item.Properties()));
    }

    public static void init() {}
}
