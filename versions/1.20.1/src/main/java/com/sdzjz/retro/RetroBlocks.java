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
 *  数据线刀①先平方块占位，连接形态与 BE 随刀③（m443）。m524（SB3）加超大工作台方块/BE/MenuType（S 线）。 */
public final class RetroBlocks {

    private RetroBlocks() { }

    public static Block STORAGE_CORE;
    public static Block DATA_CABLE;
    public static Block DATA_PANEL; // m447
    public static Block STRUCTURE_CORE; // m454
    public static BlockEntityType<StorageCore120> STORAGE_CORE_BE;
    public static BlockEntityType<DataCable120> DATA_CABLE_BE; // m444
    public static BlockEntityType<DataPanel120> DATA_PANEL_BE; // m447
    public static BlockEntityType<StructureCore120> STRUCTURE_CORE_BE; // m454
    public static net.minecraft.world.inventory.MenuType<DataPanel120.PanelMenu120> PANEL_MENU; // m447 扩展开屏（BlockPos 随包）
    public static net.minecraft.world.inventory.MenuType<StructureCoreMenu120> CANVAS_MENU; // m456
    public static Block SUPER_BENCH; // m524（SB3）
    public static BlockEntityType<SuperBench120> SUPER_BENCH_BE; // m524（SB3）零数据挂点，id 与主线同名同源
    public static net.minecraft.world.inventory.MenuType<com.sdzjz.screen.SuperBenchScreenHandler> SUPER_BENCH_MENU; // m524（SB3）普通 MenuType（无随包数据，主线同形）

    /** 1.20.1 无 codec 的最小 EntityBlock 壳（渲染置回 MODEL，BaseEntityBlock 默认 INVISIBLE）。 */
    private static final class StorageCoreBlock120 extends BaseEntityBlock {
        StorageCoreBlock120(BlockBehaviour.Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StorageCore120(pos, state); }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    }

    /** m454 结构核心（画布）方块；m456 起右键开画布屏（零槽位菜单，交互全走 payload）。 */
    private static final class StructureCoreBlock120 extends BaseEntityBlock {
        StructureCoreBlock120(BlockBehaviour.Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StructureCore120(pos, state); }

        @Override // m464（C2-⑤a）：生产 tick 挂线（服务端权威，客户端无 ticker）
        public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                net.minecraft.world.level.Level world, BlockState state,
                net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            return world.isClientSide ? null : createTickerHelper(type, STRUCTURE_CORE_BE, StructureCore120::tick);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level world,
                BlockPos pos, net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (world.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
            if (world.getBlockEntity(pos) instanceof StructureCore120)
                player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
                    @Override public net.minecraft.network.chat.Component getDisplayName() {
                        return net.minecraft.network.chat.Component.translatable("block.sdzjz.structure_core");
                    }
                    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId,
                            net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player p) {
                        return new StructureCoreMenu120(syncId, pos);
                    }
                    @Override public void writeScreenOpeningData(net.minecraft.server.level.ServerPlayer p,
                            net.minecraft.network.FriendlyByteBuf buf) {
                        buf.writeBlockPos(pos);
                    }
                });
            return net.minecraft.world.InteractionResult.CONSUME;
        }
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

    /** m524（SB3）超大工作台方块 **1.20.1 世代壳**——主线 {@code block/SuperBenchBlock}（55 行）原文照搬，世代差三处：
     *  ①无 {@code MapCodec codec()}（BaseEntityBlock 1.20.3 起才有，m441 同律）；②{@code useWithoutItem} 五参→本世代 {@code use} 六参
     *  （1.20.1 无"空手/持物"分流）；③BE 类为本世代 {@link SuperBench120}。其余逐句同原文：标题字面 "超大工作台"、
     *  渲染置回 MODEL（BaseEntityBlock 默认 INVISIBLE，结构核心同款雷）、服务端 {@code openMenu(SimpleMenuProvider)} 建
     *  {@code SuperBenchScreenHandler(syncId, inv, ContainerLevelAccess.create(world, pos))}、两侧都回 SUCCESS。 */
    private static final class SuperBenchBlock120 extends BaseEntityBlock {
        private static final net.minecraft.network.chat.Component TITLE = net.minecraft.network.chat.Component.literal("超大工作台");

        SuperBenchBlock120(BlockBehaviour.Properties p) { super(p); }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SuperBench120(pos, state); }

        @Override
        public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level world,
                BlockPos pos, net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (!world.isClientSide) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (syncId, inv, p) -> new com.sdzjz.screen.SuperBenchScreenHandler(syncId, inv,
                                net.minecraft.world.inventory.ContainerLevelAccess.create(world, pos)),
                        TITLE));
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
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
        STRUCTURE_CORE = reg("structure_core", new StructureCoreBlock120(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion())); // m454 画布骨架
        CANVAS_MENU = Registry.register(BuiltInRegistries.MENU, id("structure_core"), // m456 菜单 id 与方块同名
                new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                        (syncId, inv, buf) -> new StructureCoreMenu120(syncId, buf.readBlockPos())));
        STRUCTURE_CORE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("structure_core"), // BE id 与 Legacy 同名同源
                FabricBlockEntityTypeBuilder.create(StructureCore120::new, STRUCTURE_CORE).build());
        // m524（SB3）超大工作台：方块（主线 ofFullCopy(CRAFTING_TABLE)→本世代 copy，m441 签名差清单）+ BE + 普通 MenuType
        // （主线 ModScreenHandlers 同款 new MenuType<>(SuperBenchScreenHandler::new, FeatureFlags.VANILLA_SET)，两代同签名 m523 核过）。
        // **注册 MenuType 后紧跟 installType**——m523 安装口：handler 在白名单里不能引 registry 包，漏装=开屏 reqType() 当场抛（判官 super_bench_block_be_and_menu_installed 钉着）。
        SUPER_BENCH = reg("super_bench", new SuperBenchBlock120(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)));
        SUPER_BENCH_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("super_bench"), // BE id 与 Legacy 同名同源
                FabricBlockEntityTypeBuilder.create(SuperBench120::new, SUPER_BENCH).build());
        SUPER_BENCH_MENU = Registry.register(BuiltInRegistries.MENU, id("super_bench"), // 菜单 id 与主线同名同源
                new net.minecraft.world.inventory.MenuType<>(com.sdzjz.screen.SuperBenchScreenHandler::new,
                        net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
        com.sdzjz.screen.SuperBenchScreenHandler.installType(SUPER_BENCH_MENU);
        // m452 专属创造栏页（作者实机反馈：原挂功能方块页得靠搜索才找得到）——tab id "sdzjz:main"
        // 与 Legacy 同名同源（lang 键 itemGroup.sdzjz.main 共用），图标=存储核心。
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> groupKey =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, id("main"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, groupKey,
                net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                        .icon(() -> new net.minecraft.world.item.ItemStack(STORAGE_CORE))
                        .title(net.minecraft.network.chat.Component.translatable("itemGroup.sdzjz.main"))
                        .build());
        RetroItems.register(); // m527（SB6）：非机器物品骨架，第一件 core_module（作者拍板按 1.21.1 做成一样）
        RetroMachineItems.registerAll(); // m453：101 台机器物品（Machines 唯一数据源反射枚举，id 序）
        ItemGroupEvents.modifyEntriesEvent(groupKey).register(e -> {
            e.accept(STORAGE_CORE);
            e.accept(DATA_CABLE);
            e.accept(DATA_PANEL);
            e.accept(STRUCTURE_CORE); // m454
            e.accept(SUPER_BENCH); // m524（SB3）
            RetroItems.acceptAll(e); // m527：核心模块排机器前（主线 ModItems.init 同序）
            for (net.minecraft.world.item.Item machine : RetroMachineItems.items()) e.accept(machine); // m453
        });
    }

    private static Block reg(String name, Block b) {
        Registry.register(BuiltInRegistries.BLOCK, id(name), b);
        Registry.register(BuiltInRegistries.ITEM, id(name), new BlockItem(b, new Item.Properties()));
        return b;
    }

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }
}
