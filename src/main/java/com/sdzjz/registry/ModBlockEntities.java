package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.DataCableBlockEntity;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.block.StorageCoreBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
import com.sdzjz.block.TradeCenterBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModBlockEntities {

    public static final BlockEntityType<StructureCoreBlockEntity> STRUCTURE_CORE_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Sdzjz.id("structure_core"),
                    FabricBlockEntityTypeBuilder.create(StructureCoreBlockEntity::new,
                            ModBlocks.STRUCTURE_CORE).build());

    public static final BlockEntityType<DataPanelBlockEntity> DATA_PANEL_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Sdzjz.id("data_panel"),
                    FabricBlockEntityTypeBuilder.create(DataPanelBlockEntity::new,
                            ModBlocks.DATA_PANEL).build());

    public static final BlockEntityType<StorageCoreBlockEntity> STORAGE_CORE_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Sdzjz.id("storage_core"),
                    FabricBlockEntityTypeBuilder.create(StorageCoreBlockEntity::new,
                            ModBlocks.STORAGE_CORE).build());

    public static final BlockEntityType<TradeCenterBlockEntity> TRADE_CENTER_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Sdzjz.id("trade_center"),
                    FabricBlockEntityTypeBuilder.create(TradeCenterBlockEntity::new,
                            ModBlocks.TRADE_CENTER).build());

    public static final BlockEntityType<com.sdzjz.block.WirelessNodeBlockEntity> WIRELESS_NODE_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Sdzjz.id("wireless_node"),
                    FabricBlockEntityTypeBuilder.create(com.sdzjz.block.WirelessNodeBlockEntity::new,
                            ModBlocks.WIRELESS_NODE).build());

    public static final BlockEntityType<DataCableBlockEntity> DATA_CABLE_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Sdzjz.id("data_cable"),
                    FabricBlockEntityTypeBuilder.create(DataCableBlockEntity::new,
                            ModBlocks.DATA_CABLE).build());

    public static void init() {}
}
