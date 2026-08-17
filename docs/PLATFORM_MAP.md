# SDZJZ 平台耦合地雷图（m361 Phase 0，工具自动生成：docs/tools_platform_scan.py）

双端锚点=1.21.1(Legacy) + 26.2(Modern)；本图指导 Phase 1 Common 剥离顺位。

## 总量

- **A Common-safe 可直迁**: 22 文件 / 2174 行
- **B Legacy-coupled 需 SPI 剥离**: 88 文件 / 14413 行
- **D Client-only**: 20 文件 / 6704 行
- **E Mixin(代际隔离)**: 6 文件 / 198 行
- C Modern-only: 0（26.x 适配落地后产生）

## B 类 API 族分布（=Platform SPI 接口清单的量化依据）

| API 族 | 文件数 | 用点数 | 对应 SPI |
|---|---|---|---|
| world/block | 56 | 1162 | WorldAdapter |
| item | 49 | 913 | ItemPlatform/ItemView |
| nbt/component | 21 | 411 | NbtAdapter/DataComponentAdapter |
| registry | 52 | 270 | IdResolver |
| screen | 23 | 239 | VaultScreenPlatform/ScreenAdapter |
| text/i18n | 34 | 173 | MsgPlatform |
| network | 26 | 159 | NetPlatform |
| gametest | 3 | 91 | 版本测试层 |
| recipe | 6 | 53 | RecipeAccess |
| fabric-api | 17 | 42 | loader 层 |

## 耦合最重 TOP15（m368 耦合分排序：分=API 族数²×log(用点)——迁移难度看"同时依赖几个 SPI 面"而非用点绝对值，顾问⑥轮⑤）

- 耦合分 535｜block/StructureCoreBlockEntity.java（3519 行 / 738 用点 / 9 个 SPI 面：world/block×268, item×198, nbt/component×150, registry×90, screen×19）
- 耦合分 428｜screen/DataPanelScreenHandler.java（836 行 / 196 用点 / 9 个 SPI 面：item×90, world/block×33, recipe×24, screen×22, registry×14）
- 耦合分 360｜gametest/SdzjzGameTests.java（924 行 / 278 用点 / 8 个 SPI 面：world/block×98, gametest×86, item×52, screen×22, nbt/component×14）
- 耦合分 239｜item/TerminalItem.java（222 行 / 132 用点 / 7 个 SPI 面：nbt/component×46, world/block×36, item×22, registry×19, screen×4）
- 耦合分 226｜block/TradeCenterBlockEntity.java（305 行 / 101 用点 / 7 个 SPI 面：world/block×34, item×25, nbt/component×21, text/i18n×8, registry×6）
- 耦合分 226｜block/DataCableBlockEntity.java（332 行 / 100 用点 / 7 个 SPI 面：world/block×50, item×28, nbt/component×9, fabric-api×6, registry×3）
- 耦合分 215｜block/DataPanelBlockEntity.java（322 行 / 80 用点 / 7 个 SPI 面：world/block×43, item×14, nbt/component×10, screen×8, fabric-api×3）
- 耦合分 171｜Sdzjz.java（334 行 / 114 用点 / 6 个 SPI 面：network×42, world/block×42, screen×14, fabric-api×9, item×5）
- 耦合分 156｜item/PortableVaultItem.java（257 行 / 77 用点 / 6 个 SPI 面：item×29, nbt/component×22, world/block×13, screen×5, registry×4）
- 耦合分 121｜screen/SuperBenchScreenHandler.java（593 行 / 125 用点 / 5 个 SPI 面：item×78, screen×19, registry×17, text/i18n×9, nbt/component×2）
- 耦合分 119｜block/StorageCoreBlockEntity.java（509 行 / 118 用点 / 5 个 SPI 面：world/block×55, item×24, nbt/component×19, registry×15, fabric-api×5）
- 耦合分 112｜legacy/LegacyRecipeAccess.java（261 行 / 88 用点 / 5 个 SPI 面：item×40, registry×19, world/block×16, recipe×11, nbt/component×2）
- 耦合分 105｜item/AutoFeederItem.java（132 行 / 66 用点 / 5 个 SPI 面：nbt/component×25, item×16, world/block×13, registry×8, text/i18n×4）
- 耦合分 104｜debug/BenchRunner.java（506 行 / 64 用点 / 5 个 SPI 面：world/block×41, item×18, registry×3, text/i18n×1, fabric-api×1）
- 耦合分 90｜item/LinkerItem.java（106 行 / 37 用点 / 5 个 SPI 面：world/block×16, nbt/component×10, item×5, text/i18n×5, registry×1）

## A 类清单（Phase 1 第一批直迁 common/）

- common:config/SdzjzConfig.java（248 行）
- common:debug/CoreProfiler.java（181 行）
- common:debug/GcAccount.java（52 行）
- common:graph/NodeKinds.java（22 行）
- common:machine/BrewPlanner.java（74 行）
- common:machine/CoreScheduler.java（200 行）
- common:machine/CraftPlanner.java（321 行）
- common:machine/CropFarms.java（43 行）
- common:machine/EnchantPlanner.java（77 行）
- common:machine/MachineDef.java（22 行）
- common:machine/MachineXp.java（57 行）
- common:machine/Machines.java（408 行）
- common:machine/MobDrops.java（74 行）
- common:machine/PickerQuery.java（46 行）
- common:machine/SmeltPlanner.java（68 行）
- common:machine/VillagerTrades.java（119 行）
- common:platform/BrewAccess.java（29 行）
- common:platform/CraftAccess.java（26 行）
- common:platform/EnchAccess.java（27 行）
- common:platform/Platform.java（34 行）
- common:platform/RecipeAccess.java（23 行）
- common:platform/SmeltAccess.java（23 行）

## E 类 Mixin 清单（§6 代际隔离对象）

- mixin/ItemStackCodecMixin.java（item, screen）
- mixin/ItemStackMaxCountMixin.java（item）
- mixin/PlayerScreenHandlerVaultMixin.java（screen）
- mixin/SlotMaxCountMixin.java（item, screen）
- mixin/client/DrawContextCountMixin.java（item, client-render）
- mixin/client/InventoryScreenVaultSlotMixin.java（screen, client-render）

## D 类 Client 清单

- client/StructureCoreScreen.java（3281 行 / 470 用点）
- client/SodiumSpriteKicker.java（147 行 / 78 用点）
- client/PortableVaultScreen.java（216 行 / 63 用点）
- client/DataPanelScreen.java（699 行 / 45 用点）
- client/TradeCenterScreen.java（237 行 / 42 用点）
- client/SciSkin.java（327 行 / 35 用点）
- SdzjzClient.java（77 行 / 33 用点）
- client/SuperBenchScreen.java（393 行 / 31 用点）
- client/ExtractPortScreen.java（142 行 / 29 用点）
- client/CompressedPackRenderer.java（120 行 / 26 用点）
- client/SatelliteNodeModel.java（135 行 / 20 用点）
- client/SatelliteNodeRenderer.java（133 行 / 19 用点）
- client/DataCableRenderer.java（84 行 / 16 用点）
- client/StorageCoreRenderer.java（71 行 / 14 用点）
- client/WirelessNodeRenderer.java（227 行 / 14 用点）
- client/SciButton.java（33 行 / 7 用点）
- client/TermButton.java（34 行 / 5 用点）
- client/DataCableAnimGeo.java（34 行 / 0 用点）
- client/PinyinInitials.java（142 行 / 0 用点）
- client/StorageCoreAnimGeo.java（172 行 / 0 用点）
