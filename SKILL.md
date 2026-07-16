---
name: sdzjz-mod
description: 生电终结者（Fabric 1.21.1）项目的踩坑、必查 API、沙箱边界、交活前自查。动手前先读。
---

# SKILL · 生电终结者开工手册

目的：别让同一个坑踩第二遍。动手写代码 / 改配置 / 提交前先过一遍。

## 一、三条铁律

1. **不装懂、不臆想**：拿不准的接口先查 fabricmc.net/develop 或照仓库现有用法抄；实在核不了的标「待编译验证」，绝不假装确定。
2. **话少、先干活**：用户反馈直接照做，别拿"原则/安全"包装拖延；有担忧末尾一句话提醒即可。
3. **沙箱会清、先落盘**：每个里程碑及时 push（多文件走 git push，不甩 attachment）。

## 二、沙箱能做 / 不能做

- **不能** `./gradlew build`：沙箱未放行 Fabric/Mojang Maven。所以新 API 一律标「待编译验证」，作者本地 JDK21 编译。
- **能**：写代码、静态自检（括号配平、import 对照、JSON 合法）、git push。
- push 靠作者一次性 PAT：只在推送命令内联用，**不写进 .git/config、不写进任何提交文件**，用完提醒作者 revoke（一次）。

## 三、1.21.1 必查/易错 API

- `Identifier` 构造器已私有 → 一律 `Identifier.of(namespace, path)`。
- **物品数据用数据组件（Data Components, 1.20.5+）+ Codec/PacketCodec，不要用旧 NBT tag**。面板配置（配方 id、升级数、缓冲、连线）都走组件。
- 物品组：`FabricItemGroup.builder().icon(...).displayName(...).build()` + `Registry.register(Registries.ITEM_GROUP, key, group)`，key 用 `RegistryKey.of(RegistryKeys.ITEM_GROUP, id)`。
- `EntityType.Builder.build(...)` 在 1.21.1 取 **String**（不是 RegistryKey）——真到自定义实体那步注意（yongye 踩过）。
- 方块实体 ticking、`ScreenHandler`/`HandledScreen`、网络包同步：**服务端算、客户端只显示**，进度/电量走自定义 payload 同步。
- 新文件的 import 路径逐条和仓库已编过的文件对照，别凭记忆写包名。

## 四、架构红线（别破坏）

- 数据模型从头按「节点 + 边(edge)」存，渲染层（机架 / 画布）可换，逻辑层不动。
- 产物**只进内部缓冲，缓冲满即暂停，绝不生成掉落物实体**。
- 面板按 `globalTickInterval` 节拍、错峰调度，别每 tick 全量遍历。
- 新机制的数值一律进 `SdzjzConfig`，改结构升 `configVersion`。

## 五、交活前自查清单

- [ ] 花括号 / 圆括号配平，无未用 import
- [ ] 新 API 已标「待编译验证」，或已对照仓库既有用法
- [ ] 新增可调数值已进 config，必要时升 configVersion
- [ ] JSON（fabric.mod.json / lang / 配方）合法
- [ ] 已 push；PAT 未落进任何文件
- [ ] 给作者的收尾：改了哪些文件 + 待验证点 + 下一步，简洁四样，不啰嗦
