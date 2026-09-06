package com.sdzjz.neoforge;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * m535（F1d-1）NeoForge 1.21.1 加载器入口——m408 骨架 {@code SdzjzNeoForge}（只挂 common/ 报一行日志）退役，
 * 本类对位 Fabric 的 {@code loader/FabricEntry}：**装五个加载器口 → 业务初始化三段**（m535 把 {@code Sdzjz.init()} 拆成
 * initPorts / initRegistries / initHooksAndNet，Fabric 仍一口气调、语句顺序逐位不变）。
 *
 * <p><b>注册分期（评估报告 P0 点名的生命周期检查点）</b>：NeoForge 每个注册表只在自己的 {@link RegisterEvent} 期解冻，
 * 构造器期全冻着——直接 {@code Registry.register} 会抛 "Registry is already frozen"。四个注册类靠**类初始化**触发直接注册
 * （在树写法不动），所以本类要做的只是：BLOCK 期触发 ModBlocks、ITEM 期触发 ModItems（含 m535 从 ModBlocks 拆出的
 * 八个方块物品）、BLOCK_ENTITY_TYPE 期触发 ModBlockEntities、MENU 期触发 ModScreenHandlers、CREATIVE_MODE_TAB 期
 * 注册创造栏。注册表事件期内直接 {@code Registry.register} 与 {@code RegisterHelper.register} 等价（后者就是前者一句转调）。
 *
 * <p><b>网络</b>：{@code Sdzjz.initHooksAndNet()} 在构造器里就把全部 payload 类型/接收器交给 {@link NeoForgeNet}，那边缓冲到
 * {@code RegisterPayloadHandlersEvent} 再落（NeoForge 的 registrar 出了事件作用域即失效——m535 排刀稿）。
 *
 * <p>客户端入口另开 {@code NeoForgeClientEntry}（F1d-2：{@code RegisterMenuScreensEvent}/{@code EntityRenderersEvent}/模型插件）。
 * 提供侧能力（存储核心 {@code IItemHandler}）：m539 {@link #onRegisterCapabilities}。
 */
@Mod(NeoForgeEntry.MODID)
public final class NeoForgeEntry {

    public static final String MODID = "sdzjz";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("sdzjz-neoforge");

    public NeoForgeEntry(IEventBus modBus, ModContainer modContainer) {
        com.sdzjz.net.Net.install(new NeoForgeNet()); // m433 平台口：必须早于 Sdzjz.initHooksAndNet 的一切 payload 注册
        com.sdzjz.storage.Xfer.install(new NeoForgeXfer()); // m434 平台口
        com.sdzjz.loader.Env.install(new NeoForgeEnv()); // m435 平台口：必须早于 initPorts 里的 Platform.initConfigDir
        com.sdzjz.loader.Hooks.install(new NeoForgeHooks()); // m435 平台口
        com.sdzjz.loader.Menus.install(new NeoForgeMenus()); // m532 菜单数据口：必须早于 MENU 期（ModScreenHandlers 类初始化在那儿）

        com.sdzjz.Sdzjz.initPorts(); // 世代口/配置/命令/压测/服务端 tick 钩——不碰注册表

        modBus.addListener(NeoForgeEntry::onRegister); // 注册分期
        modBus.addListener(NeoForgeNet::onRegisterPayloads); // 缓冲的 payload 在这儿落
        modBus.addListener(NeoForgeEntry::onRegisterCapabilities); // m539（F1d-3a）提供侧能力：存储核心 IItemHandler（对位 FabricEntry 的 ItemStorage.SIDED 注册句）

        com.sdzjz.Sdzjz.initHooksAndNet(); // 平台事件钩（游戏总线随时可挂）+ payload 类型/接收器（进缓冲）

        LOGGER.info("[生电终结者] NeoForge 入口在岗：业务层与 Fabric 同一份代码（熔炉族判定 {}）", com.sdzjz.machine.Machines.smelterFamily("super_smelter"));
    }

    /** m539：把存储核心双账本暴露成 IItemHandler（Create/漏斗管道类模组怼任意面即存取）；适配器实例走 BE 不透明槽（m534），同一 BE 恒返同一实例。 */
    private static void onRegisterCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.sdzjz.registry.ModBlockEntities.STORAGE_CORE_BE,
                (be, side) -> (net.neoforged.neoforge.items.IItemHandler) be.transferAdapter(() -> new NeoForgeStorageAdapter(be)));
    }

    /** 五期各触发一句——与 Fabric {@code Sdzjz.initRegistries()} 四句同源，只是按注册表拆开叫。 */
    private static void onRegister(RegisterEvent event) {
        ResourceKey<? extends Registry<?>> key = event.getRegistryKey();
        if (key.equals(Registries.BLOCK)) com.sdzjz.registry.ModBlocks.init();
        else if (key.equals(Registries.ITEM)) com.sdzjz.registry.ModItems.initItems();
        else if (key.equals(Registries.BLOCK_ENTITY_TYPE)) com.sdzjz.registry.ModBlockEntities.init();
        else if (key.equals(Registries.MENU)) com.sdzjz.registry.ModScreenHandlers.init();
        else if (key.equals(Registries.CREATIVE_MODE_TAB)) com.sdzjz.registry.ModItems.init();
    }
}
