# F1d NeoForge 1.21.1 排刀稿（m535）

> 目标：`versions/1.21.1/neoforge/` 从「只挂 common/ 的骨架」变成能跑玩法的第二个加载器。业务层零改动是硬约束
> （xplat/common 零加载器符号，第 13 闸），本稿只讨论**胶水**与**时机**。API 名一律对官方 1.21.1 文档核过（链接见表），
> 沙箱没有 NeoForge jar，**CI「NeoForge 1.21.1：编译出包」job 是唯一编译判官**（m535 起红时错误清单回推 `ci-neoforge-errors` 分支）。

## 一、API-01：五口 + 注册 + 客户端，逐口对照（Fabric 原句 → NeoForge 对位）

| 口 | Fabric（在树） | NeoForge 21.1 | 出处 |
|---|---|---|---|
| 注册（Registry.register 直调） | 随时可调 | **只在该注册表自己的 `RegisterEvent` 期解冻**；事件内 `Registry.register` 与 `RegisterHelper.register` 等价 | [registries](https://docs.neoforged.net/docs/1.21.1/concepts/registries) |
| Net.c2s / s2c | `PayloadTypeRegistry.playC2S().register(type, codec)` | `RegisterPayloadHandlersEvent`（模组总线）→ `event.registrar("1").playToServer(type, codec, IPayloadHandler)` / `playToClient(...)`；**registrar 出事件作用域即失效** | [payload](https://docs.neoforged.net/docs/1.21.1/networking/payload) |
| Net.onServer | `ServerPlayNetworking.registerGlobalReceiver` | 处理器随 `playToServer` 一起给（晚绑定分派，见 NeoForgeNet 类注） | 同上 |
| Net.toPlayer | `ServerPlayNetworking.send` | `PacketDistributor.sendToPlayer(sp, payload)` | 同上 |
| ClientNet.toServer / onClient | `ClientPlayNetworking.send` / `registerGlobalReceiver` | `PacketDistributor.sendToServer` / 处理器随 `playToClient` 给（1.21.1 无独立客户端注册事件） | 同上 |
| Menus.type | `new ExtendedScreenHandlerType<>(factory::create, codec)` | `IMenuTypeExtension.create(IContainerFactory)`：`(id, inv, buf) -> factory.create(id, inv, codec.decode(buf))` | [menus](https://docs.neoforged.net/docs/1.21.1/gui/menus) |
| Menus.open | `player.openMenu(ExtendedScreenHandlerFactory)`（Fabric 自己从菜单类型找 codec） | `player.openMenu(provider, buf -> codec.encode(buf, data))`——**写数据时拿不到菜单类型 → `MenuData` 加 `menuCodec()`**（m535） | 同上 |
| Hooks 六口 | ServerTickEvents.END_SERVER_TICK / ServerWorldEvents.LOAD / ServerPlayConnectionEvents.DISCONNECT / SERVER_STOPPED / UseEntityCallback / CommandRegistrationCallback | 游戏总线 `NeoForge.EVENT_BUS.addListener`：`ServerTickEvent.Post` / `LevelEvent.Load`（过滤 ServerLevel）/ `PlayerEvent.PlayerLoggedOutEvent` / `ServerStoppedEvent` / `PlayerInteractEvent.EntityInteract`（非 PASS → setCancellationResult + setCanceled）/ `RegisterCommandsEvent` | [events](https://docs.neoforged.net/docs/1.21.1/concepts/events) |
| Env 两口 | `FabricLoader.getInstance().isModLoaded/getConfigDir` | `ModList.get().isLoaded` / `FMLPaths.CONFIGDIR.get()` | — |
| Xfer 消费侧五口 | `ItemStorage.SIDED.find` + `Storage<ItemVariant>`（事务） | `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)` + `IItemHandler`（simulate 布尔）；`ItemHandlerHelper.insertItem` | [capabilities](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/) |
| 提供侧（存储核心暴露账本） | `ItemStorage.SIDED.registerForBlockEntity(..., FabricStorageAdapter.of)` | `RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STORAGE_CORE_BE, (be, side) -> be.transferAdapter(() -> new NeoForgeStorageAdapter(be)))`——需要账本的**有界槽位视图** | 同上；F1d-3 |
| 客户端 | `MenuScreens.register` / `BlockEntityRenderers.register` 直调；`ModelLoadingPlugin`；`BuiltinItemRendererRegistry` | `RegisterMenuScreensEvent` / `EntityRenderersEvent.RegisterRenderers`；`ModelEvent.ModifyBakingResult`/`RegisterAdditional`；`IClientItemExtensions`（`RegisterClientExtensionsEvent`）——**F1d-2 开工前逐个对文档核名** | F1d-2 |
| 判官 | `FabricGameTest` | `@GameTestHolder` + `@PrefixGameTestTemplate(false)`（`RegisterGameTestsEvent`） | F1d-3 |

## 二、注册时机（报告 P0 的生命周期检查点，m535 落地）

- 事实：四注册类靠**类初始化**触发直接 `Registry.register`；`ModBlocks.reg` 一句里方块+方块物品同注册。NeoForge 在 BLOCK 期碰 ITEM 表 → `Registry is already frozen`。
- 处置（Fabric 零行为变化）：`ModBlocks.reg` 只注册方块，八个 `BlockItem` 挪 `ModBlocks.registerBlockItems()`，由 `ModItems` 静态段**首句**调用 → ITEM 注册表内顺序逐位不变（八方块物品在前）；`ModItems.initItems()` 新增无体触发口；`ModItems.init()` 仍=创造栏注册（CREATIVE_MODE_TAB 期）。
- `Sdzjz.init()` 拆三段 `initPorts / initRegistries / initHooksAndNet`（语句与顺序不动，Fabric 仍调 `init()`）；NeoForge：构造器调 ①③，`RegisterEvent` 五期各触发一句。
- 静态初始化连带核过：`LegacySuperBenchHost` 只在方法体摸 `ModItems`（构造安全）；`initHooksAndNet` 顶层无触碰注册表的语句（全在 lambda 里）。
- 剩余风险：某个方块/物品**构造器**若静态摸别的注册类（如 `Machines` 外的 `ModX.Y`），会在错误的期触发注册 → CI 不报、启动才炸。F1d-3 用 NeoForge GameTest/服务器启动兜。

## 三、分刀

| 刀 | 内容 | 判官 |
|---|---|---|
| **F1d-1（m535）** | build.gradle 整挂三源集（排除胶水 13 文件 + mixin）；`NeoForgeEntry` + Env/Hooks/Menus/Net/Xfer 消费侧；注册分期；`MenuData.menuCodec`；第 13 闸 +2 判据；CI 错误回推分支 | NeoForge job **编译绿** |
| F1d-2 | `NeoForgeClientEntry`（dist=CLIENT）：`ClientNet/ClientHooks` 两口实现、`RegisterMenuScreensEvent`、`EntityRenderersEvent`、`SatelliteNodeModel` 的 ModelEvent 世代口、压缩包 `IClientItemExtensions`；`SdzjzClient.init()` 按事件拆段（同 m535 手法） | 编译绿 + 作者 NeoForge 客户端进世界开屏 |
| F1d-3 | 提供侧 `NeoForgeStorageAdapter implements IItemHandler`（账本有界槽位视图：普通账 by id + 精确账模板；`insertItem/extractItem` 走 `StorageLedger` 现有口）+ `RegisterCapabilitiesEvent`；主线判官 `@GameTestHolder` 壳 + 传输断言两代化；CI job 升 GameTest | 判官绿；箱子↔核心双向存取 |
| F1d-4 | 收官验收：服务器起 + 客户端进 + 拿到并放下核心/工作台 + 最小生产链（箱子供料→生产→入仓→取物）；能力表第 3 节填七态 | 作者实机 |

## 四、本刀刻意不做

- mixin 族（大堆叠/随身仓库）：NeoForge mixin 配置/refmap 工艺另议 → F1e。
- 客户端入口：F1d-2（先让服务端侧编过、注册分期被 CI 验过）。
- 提供侧能力：F1d-3（要写账本槽位视图这块真逻辑，单刀）。
