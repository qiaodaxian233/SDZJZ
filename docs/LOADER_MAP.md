# 加载器耦合面地图（m401 自动生成，勿手改）

> 口径：只数 `net.fabricmc.*` 与 Fabric 专属入口/注解的用点。换 Forge/NeoForge 时，
> 这些点要么抽进 `platform/` SPI 由各加载器实现，要么在各加载器源集里各写一份。

扫描文件数（含 fabric 字样）：28；有耦合用点的文件：25

## 按 API 族（决定 SPI 补齐顺序）

| 族 | 用点数 | 涉及文件数 |
|---|---:|---:|
| networking 网络包 | 124 | 9 |
| registry 注册 | 5 | 2 |
| events 生命周期/事件 | 12 | 3 |
| rendering 渲染 | 12 | 3 |
| keybinding 键位 | 2 | 1 |
| transfer 传输API | 69 | 6 |
| screenhandler 屏 | 11 | 6 |
| gametest 测试 | 4 | 2 |
| loader 环境/入口 | 15 | 4 |
| resource 资源/标签 | 0 | 0 |

## 按文件排行（前 25，决定改写顺序）

| 文件 | 用点数 |
|---|---:|
| `src/main/java/com/sdzjz/Sdzjz.java` | 60 |
| `src/main/java/com/sdzjz/client/StructureCoreScreen.java` | 57 |
| `src/main/java/com/sdzjz/block/DataCableBlockEntity.java` | 27 |
| `src/main/java/com/sdzjz/block/StorageCoreBlockEntity.java` | 25 |
| `src/main/java/com/sdzjz/SdzjzClient.java` | 17 |
| `src/main/java/com/sdzjz/gametest/SdzjzGameTests.java` | 13 |
| `src/main/java/com/sdzjz/block/StructureCoreBlockEntity.java` | 7 |
| `src/main/java/com/sdzjz/registry/ModScreenHandlers.java` | 6 |
| `src/main/java/com/sdzjz/block/DataPanelBlockEntity.java` | 5 |
| `versions/26.2/src/main/java/com/sdzjz/modern/ModernBootstrap.java` | 5 |
| `src/main/java/com/sdzjz/registry/ModItems.java` | 4 |
| `src/main/java/com/sdzjz/client/ChunkRegionHighlighter.java` | 4 |
| `src/main/java/com/sdzjz/debug/BenchRunner.java` | 3 |
| `src/main/java/com/sdzjz/client/CompressedPackRenderer.java` | 3 |
| `src/main/java/com/sdzjz/screen/DataPanelScreenHandler.java` | 2 |
| `src/main/java/com/sdzjz/block/DataCableBlock.java` | 2 |
| `src/main/java/com/sdzjz/compat/ProjectEFCompat.java` | 2 |
| `src/main/java/com/sdzjz/compat/jei/SdzjzJeiTransfer.java` | 2 |
| `src/main/java/com/sdzjz/client/ChunkRemoverConfigScreen.java` | 2 |
| `src/main/java/com/sdzjz/client/DataPanelScreen.java` | 2 |
| `src/main/java/com/sdzjz/client/PortableVaultScreen.java` | 2 |
| `src/main/java/com/sdzjz/block/TradeCenterBlockEntity.java` | 1 |
| `src/main/java/com/sdzjz/item/TerminalItem.java` | 1 |
| `src/main/java/com/sdzjz/registry/ModBlockEntities.java` | 1 |
| `versions/26.2/src/main/java/com/sdzjz/modern/ModernRecipeDomainTests.java` | 1 |
