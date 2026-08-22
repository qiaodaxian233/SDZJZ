package com.sdzjz.retro;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
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
    public static Block DATA_PANEL; // m447
    public static BlockEntityType<StorageCore120> STORAGE_CORE_BE;
    public static BlockEntityType<DataCable120> DATA_CABLE_BE; // m444
    public static BlockEntityType<DataPanel120> DATA_PANEL_BE; // m447
    public static net.minecraft.world.inventory.MenuType<DataPanel120.PanelMenu120> PANEL_MENU; // m447 扩展开屏（BlockPos 随包）

    /** 1.20.1 无 codec 的最小 EntityBlock 壳（渲染置回 MODEL，BaseEntityBlock 默认 INVISIBLE）。 */
    private static final class StorageCoreBlock120 extends BaseEntityBlock {
        StorageCoreBlock120(BlockBehaviour.Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StorageCore120(pos, state); }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    }

    /** m447 面板方块：右键开菜单（openMenu 走 Fabric 扩展开屏工厂，BlockPos 随包到客户端）。 */
    private static final class DataPanelBlock120 extends BaseEntityBlock {
        DataPanelBlock120(BlockBehaviour.Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DataPanel120(pos, state); }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level world,
                BlockPos pos, net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (world.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
            if (world.getBlockEntity(pos) instanceof DataPanel120 panel) player.openMenu(panel);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
    }

    public static void register() {
        STORAGE_CORE = reg("storage_core", new StorageCoreBlock120(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
        DATA_CABLE = reg("data_cable", new DataCableBlock120(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion())); // m444 三态连接
        STORAGE_CORE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("storage_core"),
                FabricBlockEntityTypeBuilder.create(StorageCore120::new, STORAGE_CORE).build());
        DATA_CABLE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("data_cable"), // m444（BE id 与 Legacy 同名同源）
                FabricBlockEntityTypeBuilder.create(DataCable120::new, DATA_CABLE).build());
        DATA_PANEL = reg("data_panel", new DataPanelBlock120(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion())); // m447
        DATA_PANEL_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("data_panel"),
                FabricBlockEntityTypeBuilder.create(DataPanel120::new, DATA_PANEL).build());
        PANEL_MENU = Registry.register(BuiltInRegistries.MENU, id("data_panel"), // m447 菜单 id 与方块同名
                new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                        (syncId, inv, buf) -> new DataPanel120.PanelMenu120(syncId, inv, buf.readBlockPos())));
        // m452 专属创造栏页（作者实机反馈：原挂功能方块页得靠搜索才找得到）——tab id "sdzjz:main"
        // 与 Legacy 同名同源（lang 键 itemGroup.sdzjz.main 共用），图标=存储核心。
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> groupKey =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, id("main"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, groupKey,
                net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                        .icon(() -> new net.minecraft.world.item.ItemStack(STORAGE_CORE))
                        .title(net.minecraft.network.chat.Component.translatable("itemGroup.sdzjz.main"))
                        .build());
        ItemGroupEvents.modifyEntriesEvent(groupKey).register(e -> {
            e.accept(STORAGE_CORE);
            e.accept(DATA_CABLE);
            e.accept(DATA_PANEL);
        });
    }

    private static Block reg(String name, Block b) {
        Registry.register(BuiltInRegistries.BLOCK, id(name), b);
        Registry.register(BuiltInRegistries.ITEM, id(name), new BlockItem(b, new Item.Properties()));
        return b;
    }

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }
}
