package com.sdzjz.retro;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** m441 刀①：1.20.1 世代注册骨架（存储核心+数据线，能摆能看）。签名差按 m440 清单：
 *  ResourceLocation 走构造器（1.21 是静态工厂）、Properties.copy（1.21 是 ofFullCopy）、
 *  BaseEntityBlock 无 codec（1.20.3 起才有）。id/贴图/lang 键与 Legacy 同名同源。
 *  数据线刀①先平方块占位，连接形态与 BE 随刀③（m443）。 */
public final class RetroBlocks {

    private RetroBlocks() { }

    public static Block STORAGE_CORE;
    public static Block DATA_CABLE;
    public static BlockEntityType<StorageCore120> STORAGE_CORE_BE;

    /** 1.20.1 无 codec 的最小 EntityBlock 壳（渲染置回 MODEL，BaseEntityBlock 默认 INVISIBLE）。 */
    private static final class StorageCoreBlock120 extends BaseEntityBlock {
        StorageCoreBlock120(BlockBehaviour.Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StorageCore120(pos, state); }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    }

    public static void register() {
        STORAGE_CORE = reg("storage_core", new StorageCoreBlock120(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
        DATA_CABLE = reg("data_cable", new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
        STORAGE_CORE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("storage_core"),
                FabricBlockEntityTypeBuilder.create(StorageCore120::new, STORAGE_CORE).build());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> {
            e.accept(STORAGE_CORE);
            e.accept(DATA_CABLE);
        });
    }

    private static Block reg(String name, Block b) {
        Registry.register(BuiltInRegistries.BLOCK, id(name), b);
        Registry.register(BuiltInRegistries.ITEM, id(name), new BlockItem(b, new Item.Properties()));
        return b;
    }

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }
}
