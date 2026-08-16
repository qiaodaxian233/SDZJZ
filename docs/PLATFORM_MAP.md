# SDZJZ 平台耦合地雷图（m361 Phase 0，工具自动生成：docs/tools_platform_scan.py）

双端锚点=1.21.1(Legacy) + 26.2(Modern)；本图指导 Phase 1 Common 剥离顺位。

## 总量

- **A Common-safe 可直迁**: 12 文件 / 1212 行
- **B Legacy-coupled 需 SPI 剥离**: 93 文件 / 15210 行
- **D Client-only**: 20 文件 / 6704 行
- **E Mixin(代际隔离)**: 6 文件 / 198 行
- C Modern-only: 0（26.x 适配落地后产生）

## B 类 API 族分布（=Platform SPI 接口清单的量化依据）

| API 族 | 文件数 | 用点数 | 对应 SPI |
|---|---|---|---|
| world/block | 61 | 1194 | WorldAdapter |
| item | 52 | 922 | ItemPlatform/ItemView |
| nbt/component | 21 | 411 | NbtAdapter/DataComponentAdapter |
| registry | 56 | 278 | IdResolver |
| screen | 23 | 239 | VaultScreenPlatform/ScreenAdapter |
| text/i18n | 34 | 173 | MsgPlatform |
| network | 26 | 159 | NetPlatform |
| gametest | 4 | 90 | 版本测试层 |
| recipe | 7 | 57 | RecipeAccess |
| fabric-api | 18 | 44 | loader 层 |

## 耦合最重 TOP15（B 类按用点数）——Phase 1 最后动，先易后难

- block/StructureCoreBlockEntity.java（3517 行 / 732 用点：world/block×268, item×196, nbt/component×150, registry×86）
- gametest/SdzjzGameTests.java（918 行 / 272 用点：world/block×96, gametest×84, item×52, screen×22）
- screen/DataPanelScreenHandler.java（836 行 / 196 用点：item×90, world/block×33, recipe×24, screen×22）
- item/TerminalItem.java（222 行 / 132 用点：nbt/component×46, world/block×36, item×22, registry×19）
- screen/SuperBenchScreenHandler.java（593 行 / 125 用点：item×78, screen×19, registry×17, text/i18n×9）
- block/StorageCoreBlockEntity.java（509 行 / 118 用点：world/block×55, item×24, nbt/component×19, registry×15）
- Sdzjz.java（332 行 / 113 用点：network×42, world/block×42, screen×14, fabric-api×8）
- block/TradeCenterBlockEntity.java（305 行 / 101 用点：world/block×34, item×25, nbt/component×21, text/i18n×8）
- block/DataCableBlockEntity.java（332 行 / 100 用点：world/block×50, item×28, nbt/component×9, fabric-api×6）
- block/DataPanelBlockEntity.java（322 行 / 80 用点：world/block×43, item×14, nbt/component×10, screen×8）
- item/PortableVaultItem.java（257 行 / 77 用点：item×29, nbt/component×22, world/block×13, screen×5）
- item/AutoFeederItem.java（132 行 / 66 用点：nbt/component×25, item×16, world/block×13, registry×8）
- debug/BenchRunner.java（506 行 / 64 用点：world/block×41, item×18, registry×3, text/i18n×1）
- node/NodeTags.java（149 行 / 63 用点：item×33, nbt/component×29, world/block×1）
- screen/ExtractPortScreenHandler.java（193 行 / 59 用点：item×26, screen×19, world/block×14）

## A 类清单（Phase 1 第一批直迁 common/）

- debug/GcAccount.java（52 行）
- graph/NodeKinds.java（22 行）
- machine/CraftPlanner.java（321 行）
- machine/CropFarms.java（43 行）
- machine/MachineDef.java（22 行）
- machine/MachineXp.java（57 行）
- machine/Machines.java（408 行）
- machine/MobDrops.java（74 行）
- machine/PickerQuery.java（46 行）
- machine/VillagerTrades.java（119 行）
- platform/Platform.java（23 行）
- platform/RecipeAccess.java（25 行）

## E 类 Mixin 清单（§6 代际隔离对象）

- mixin/ItemStackCodecMixin.java（item, screen）
- mixin/ItemStackMaxCountMixin.java（item）
- mixin/PlayerScreenHandlerVaultMixin.java（screen）
- mixin/SlotMaxCountMixin.java（item, screen）
- mixin/client/DrawContextCountMixin.java（item, client-render）
- mixin/client/InventoryScreenVaultSlotMixin.java（screen, client-render）

## D 类 Client 清单

- client/StructureCoreScreen.java（3281 行 / 466 用点）
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
