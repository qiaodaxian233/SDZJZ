# SDZJZ 平台耦合地雷图（m361 Phase 0，工具自动生成：docs/tools_platform_scan.py）

双端锚点=1.21.1(Legacy) + 26.2(Modern)；本图指导 Phase 1 Common 剥离顺位。

## 总量

- **A Common-safe 可直迁**: 23 文件 / 2450 行
- **B Legacy-coupled 需 SPI 剥离**: 107 文件 / 16765 行
- **D Client-only**: 24 文件 / 7299 行
- **E Mixin(代际隔离)**: 6 文件 / 198 行
- C Modern-only: 0（26.x 适配落地后产生）

## B 类 API 族分布（=Platform SPI 接口清单的量化依据）

| API 族 | 文件数 | 用点数 | 对应 SPI |
|---|---|---|---|
| world/block | 65 | 1259 | WorldAdapter |
| item | 62 | 1173 | ItemPlatform/ItemView |
| nbt/component | 29 | 581 | NbtAdapter/DataComponentAdapter |
| network | 26 | 265 | NetPlatform |
| text/i18n | 42 | 218 | MsgPlatform |
| screen | 23 | 213 | VaultScreenPlatform/ScreenAdapter |
| registry | 32 | 178 | IdResolver |
| gametest | 6 | 92 | 版本测试层 |
| recipe | 8 | 76 | RecipeAccess |
| fabric-api | 19 | 50 | loader 层 |
| client-render | 3 | 5 | (D 类专属) |

## 耦合最重 TOP15（m368 耦合分排序：分=API 族数²×log(用点)——迁移难度看"同时依赖几个 SPI 面"而非用点绝对值，顾问⑥轮⑤）

- 耦合分 427｜block/StructureCoreBlockEntity.java（4187 行 / 794 用点 / 8 个 SPI 面：world/block×320, item×217, nbt/component×187, registry×47, screen×16）
- 耦合分 359｜gametest/SdzjzGameTests.java（941 行 / 275 用点 / 8 个 SPI 面：world/block×100, gametest×78, item×55, nbt/component×19, screen×16）
- 耦合分 264｜xplat/screen/DataPanelScreenHandler.java（836 行 / 219 用点 / 7 个 SPI 面：item×116, world/block×35, recipe×24, screen×21, registry×11）
- 耦合分 237｜item/TerminalItem.java（222 行 / 125 用点 / 7 个 SPI 面：nbt/component×47, world/block×36, item×23, registry×10, screen×4）
- 耦合分 227｜block/TradeCenterBlockEntity.java（305 行 / 103 用点 / 7 个 SPI 面：world/block×36, item×26, nbt/component×22, text/i18n×8, screen×6）
- 耦合分 226｜block/DataCableBlockEntity.java（324 行 / 100 用点 / 7 个 SPI 面：world/block×51, item×32, nbt/component×10, screen×3, registry×2）
- 耦合分 215｜block/DataPanelBlockEntity.java（321 行 / 81 用点 / 7 个 SPI 面：world/block×42, nbt/component×14, item×14, screen×8, registry×1）
- 耦合分 155｜xplat/item/PortableVaultItem.java（257 行 / 74 用点 / 6 个 SPI 面：item×30, nbt/component×23, world/block×11, text/i18n×4, registry×3）
- 耦合分 150｜Sdzjz.java（342 行 / 64 用点 / 6 个 SPI 面：world/block×42, screen×9, item×7, nbt/component×4, registry×1）
- 耦合分 137｜versions/26.2/modern/ModernBrewAccess.java（146 行 / 45 用点 / 6 个 SPI 面：item×29, world/block×6, registry×5, nbt/component×3, gametest×1）
- 耦合分 119｜xplat/screen/SuperBenchScreenHandler.java（593 行 / 115 用点 / 5 个 SPI 面：item×78, screen×15, registry×11, text/i18n×9, nbt/component×2）
- 耦合分 118｜block/StorageCoreBlockEntity.java（509 行 / 111 用点 / 5 个 SPI 面：world/block×55, item×24, nbt/component×19, registry×8, fabric-api×5）
- 耦合分 113｜xplat/legacy/LegacyRecipeAccess.java（261 行 / 93 用点 / 5 个 SPI 面：item×48, registry×16, world/block×16, recipe×11, nbt/component×2）
- 耦合分 104｜versions/26.2/modern/ModernRecipeAccess.java（178 行 / 65 用点 / 5 个 SPI 面：item×23, recipe×22, world/block×10, registry×9, fabric-api×1）
- 耦合分 103｜xplat/item/AutoFeederItem.java（132 行 / 62 用点 / 5 个 SPI 面：nbt/component×26, item×17, world/block×11, registry×4, text/i18n×4）

## A 类清单（Phase 1 第一批直迁 common/）

- common:config/SdzjzConfig.java（301 行）
- common:debug/CoreProfiler.java（216 行）
- common:debug/GcAccount.java（52 行）
- common:graph/NodeKinds.java（22 行）
- common:machine/BrewPlanner.java（74 行）
- common:machine/CoreScheduler.java（200 行）
- common:machine/CraftPlanner.java（321 行）
- common:machine/CropFarms.java（43 行）
- common:machine/EnchantPlanner.java（77 行）
- common:machine/MachineDef.java（22 行）
- common:machine/MachineXp.java（57 行）
- common:machine/Machines.java（426 行）
- common:machine/MobDrops.java（74 行）
- common:machine/PickerQuery.java（46 行）
- common:machine/SmeltPlanner.java（68 行）
- common:machine/VillagerTrades.java（119 行）
- common:platform/BrewAccess.java（29 行）
- common:platform/CraftAccess.java（26 行）
- common:platform/EnchAccess.java（27 行）
- common:platform/Platform.java（34 行）
- common:platform/RecipeAccess.java（23 行）
- common:platform/RecipeDomainAssertions.java（170 行）
- common:platform/SmeltAccess.java（23 行）

## E 类 Mixin 清单（§6 代际隔离对象）

- xplat/mixin/ItemStackCodecMixin.java（item, screen）
- xplat/mixin/ItemStackMaxCountMixin.java（item）
- xplat/mixin/PlayerScreenHandlerVaultMixin.java（screen）
- xplat/mixin/SlotMaxCountMixin.java（item, screen）
- xplat/mixin/client/DrawContextCountMixin.java（item, client-render）
- xplat/mixin/client/InventoryScreenVaultSlotMixin.java（client-render）

## D 类 Client 清单

- xplat/client/StructureCoreScreen.java（3436 行 / 433 用点）
- xplat/client/PortableVaultScreen.java（215 行 / 58 用点）
- xplat/client/DataPanelScreen.java（698 行 / 42 用点）
- xplat/client/TradeCenterScreen.java（237 行 / 38 用点）
- xplat/client/SciSkin.java（327 行 / 31 用点）
- xplat/client/ExtractPortScreen.java（142 行 / 29 用点）
- xplat/client/SuperBenchScreen.java（393 行 / 28 用点）
- client/CompressedPackRenderer.java（120 行 / 24 用点）
- SdzjzClient.java（83 行 / 22 用点）
- client/SatelliteNodeModel.java（135 行 / 16 用点）
- xplat/client/ChunkRegionHighlighter.java（191 行 / 14 用点）
- xplat/client/DataCableRenderer.java（84 行 / 14 用点）
- xplat/client/SatelliteNodeRenderer.java（133 行 / 14 用点）
- client/ClientNet.java（35 行 / 14 用点）
- xplat/client/ChunkRemoverConfigScreen.java（154 行 / 13 用点）
- client/ClientHooks.java（56 行 / 13 用点）
- xplat/client/StorageCoreRenderer.java（71 行 / 11 用点）
- xplat/client/WirelessNodeRenderer.java（227 行 / 11 用点）
- xplat/client/SciButton.java（33 行 / 7 用点）
- xplat/client/SodiumSpriteKicker.java（147 行 / 5 用点）
- xplat/client/TermButton.java（34 行 / 5 用点）
- xplat/client/DataCableAnimGeo.java（34 行 / 0 用点）
- xplat/client/PinyinInitials.java（142 行 / 0 用点）
- xplat/client/StorageCoreAnimGeo.java（172 行 / 0 用点）
