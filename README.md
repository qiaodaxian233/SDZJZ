# 生电终结者（SDZJZ）

ComfyUI 式模块化量产 · Minecraft **Fabric 1.21.1** · 纯 Java · 前置仅 Fabric API

> 用「可连线的功能面板」替代生电玩法里的巨型红石农场 / 刷怪塔 / 自动机器：面板即节点，挂加速模块，量产一切；面板需材料合成，**合成完即用，不再搭红石**。中枢是控制器方块 + 节点画布，成本走电力系统。

完整设计见 [`DESIGN.md`](DESIGN.md)。开工前必读 [`SKILL.md`](SKILL.md)（踩坑 / API 核查 / 自查清单），接手看 [`HANDOVER.md`](HANDOVER.md)。

## 已定方向

- 架构：控制器方块 + 可连线节点画布（面板 = 画布里的虚拟节点，合成出的「面板物品」作为添加节点的成本凭证消耗）
- 成本：电力系统（发电面板供电，加速超线性耗电）
- 基调：偏硬核、要搭发电链，但产量等全 config 可调

## Phase 进度

| Phase | 范围 | 状态 |
|---|---|---|
| 0 | 工程骨架：Loom + 注册框架挂载点 + config(configVersion) + mixin 占位 + 文档四件套 | ✅ 骨架落地（待编译验证） |
| 1 | 控制器方块 + 第一个生产面板 + 内部缓冲 + 机架式 GUI，跑通「合成→装入→出货」 | ⬜ |
| 2 | 电力：发电面板 / 耗电 / 电网缓冲 / 电不足暂停 / 电量显示 | ⬜ |
| 3 | 加速模块 + 升级槽 + 多档面板 | ⬜ |
| 4 | 面板品类扩充（矿/农/刷怪/加工）+ 数据组件持久化 + 相邻容器导出 | ⬜ |
| 5 | 面板互联（目标选择 → 节点-边正式连线） | ⬜ |
| 6+ | 节点画布自绘渲染 + 控制/逻辑面板 | ⬜ |

## 构建

需 **JDK 21**。

```
./gradlew build
```

产物在 `build/libs/sdzjz-<版本>.jar`，连同 Fabric API、Fabric Loader 丢进 `mods/`。

> ⚠️ 本工程在沙箱内编写，沙箱**未放行 Fabric/Mojang 的 Maven 源**，所以代码**未在沙箱实际 build 过**。首次本地构建若遇版本/映射不匹配，按 `gradle.properties` 顶部四行（`minecraft_version` / `yarn_mappings` / `loader_version` / `fabric_version`）对照 <https://fabricmc.net/develop> 微调。

## 许可

All Rights Reserved · © qiaodaxian233
