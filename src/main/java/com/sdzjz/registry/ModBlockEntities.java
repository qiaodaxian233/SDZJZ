package com.sdzjz.registry;

import com.sdzjz.Sdzjz;
import com.sdzjz.block.DataPanelBlockEntity;
import com.sdzjz.block.StructureCoreBlockEntity;
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

    public static void init() {}
}
