package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.DataCableBlockEntity;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.block.TradeCenterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;

/** m531（F1a）：Fabric `FabricBlockEntityTypeBuilder.create(..).build()` → 原版 `BlockEntityType.Builder.of(..).build(null)`（Fabric 那个就是它的封装，DataFixer 类型同样传 null）——去加载器符号，两加载器同句。 */
public class ModBlockEntities {

    public static final BlockEntityType<StructureCoreBlockEntity> STRUCTURE_CORE_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("structure_core"),
                    BlockEntityType.Builder.of(StructureCoreBlockEntity::new,
                            ModBlocks.STRUCTURE_CORE).build(null));

    public static final BlockEntityType<DataPanelBlockEntity> DATA_PANEL_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("data_panel"),
                    BlockEntityType.Builder.of(DataPanelBlockEntity::new,
                            ModBlocks.DATA_PANEL).build(null));

    public static final BlockEntityType<com.sdzjz.block.SuperBenchBlockEntity> SUPER_BENCH_BE = // m249 动画挂点
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("super_bench"),
                    BlockEntityType.Builder.of(com.sdzjz.block.SuperBenchBlockEntity::new,
                            ModBlocks.SUPER_BENCH).build(null));

    public static final BlockEntityType<StorageCoreBlockEntity> STORAGE_CORE_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("storage_core"),
                    BlockEntityType.Builder.of(StorageCoreBlockEntity::new,
                            ModBlocks.STORAGE_CORE).build(null));

    public static final BlockEntityType<TradeCenterBlockEntity> TRADE_CENTER_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("trade_center"),
                    BlockEntityType.Builder.of(TradeCenterBlockEntity::new,
                            ModBlocks.TRADE_CENTER).build(null));

    public static final BlockEntityType<com.sdzjz.block.SatelliteNodeBlockEntity> SATELLITE_NODE_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("satellite_node"),
                    BlockEntityType.Builder.of(com.sdzjz.block.SatelliteNodeBlockEntity::new,
                            ModBlocks.SATELLITE_NODE).build(null)); // m156

    public static final BlockEntityType<com.sdzjz.block.WirelessNodeBlockEntity> WIRELESS_NODE_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("wireless_node"),
                    BlockEntityType.Builder.of(com.sdzjz.block.WirelessNodeBlockEntity::new,
                            ModBlocks.WIRELESS_NODE).build(null));

    public static final BlockEntityType<DataCableBlockEntity> DATA_CABLE_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Sdzjz.id("data_cable"),
                    BlockEntityType.Builder.of(DataCableBlockEntity::new,
                            ModBlocks.DATA_CABLE).build(null));

    public static void init() {}
}
