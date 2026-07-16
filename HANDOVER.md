# 生电终结者 · 项目交接文档（HANDOVER）

> 给接手的人（或新对话里的 AI）。读完这份 + `DESIGN.md` + `SKILL.md` 就能无缝接上，不用翻聊天记录。
> 仓库：https://github.com/qiaodaxian233/SDZJZ · Fabric 1.21.1 · 纯 Java · 前置仅 Fabric API。

## ⭐ 开发守则（置顶必守）

1. 不猜接口，先查文档；拿不准的照仓库现有用法抄。
2. 不糊里糊涂干活，先把边界问清楚。
3. 不臆想业务，先跟人类对齐并留痕。
4. 不造新接口，先复用已有。
5. 不跳过验证。
6. 不动架构红线。
7. 不装懂，坦白不会。
8. 不盲改，谨慎重构。

**协作风格（作者明确要求，见其 doubaox/对话记忆.md）**：
- 用户反馈是第一信号，不是"待评估输入"。说了就照做，别拿"原则/安全/最佳实践"包装拖延。
- 有担忧先把活干完，末尾一句话提醒即可，**不重复、不展开**。
- 多文件交付走 **git push**，不甩一堆 attachment。
- 别装看过资料；抓不全就说抓不全。

> 沙箱**编不了 Fabric/Mojang 依赖**，新 API 一律标「待编译验证」，作者本地 IDEA + JDK21 编译。沙箱会重置 → 每里程碑及时 push。push 靠作者一次性 PAT，**绝不写进任何提交文件**。

## 0. 一分钟速览

用可连线功能面板替代生电红石工程。控制器方块开节点画布，面板=画布里的虚拟节点（合成出的面板物品当添加成本），面板靠电力运转，加速模块超线性耗电。MVP 先用槽位机架式 GUI 跑通逻辑，节点画布自绘留到后期；数据模型从头按「节点+边」存，后期只换渲染层。

## 1. 当前状态（m1 · Phase 0 骨架）

已落地（**全部待编译验证**，沙箱编不了）：

- Loom 工程：`build.gradle` / `settings.gradle` / `gradle.properties`（四个版本号在 `gradle.properties` 顶部，build 失败照 fabricmc.net/develop 调）
- `fabric.mod.json`（main=`com.sdzjz.Sdzjz`，client=`com.sdzjz.SdzjzClient`）+ `sdzjz.mixins.json`（空占位）
- 主类 `Sdzjz`（`onInitialize`：加载 config + 注册物品组；`id()` 用 `Identifier.of`）
- 客户端类 `SdzjzClient`（占位）
- 配置 `SdzjzConfig`（GSON + configVersion=1，缺键取默认回写；含电力/防卡顿/基调字段）
- 注册 `ModItems`：创造物品组「生电终结者」（后续所有物品的锚点）
- 中英 lang
- 文档：README / DESIGN / SKILL / HANDOVER / DEVLOG

**gradle wrapper 未内置**：本地首次跑 `gradle wrapper --gradle-version 8.10` 生成 `gradlew`，或用 IDEA 导入自动补。

## 2. 下一步（Phase 1）

跑通最小闭环：控制器方块 + 方块实体（存节点图 + 电网缓冲）+ 第一个生产面板 + 内部缓冲 + 机架式 ScreenHandler/Screen + 一条面板合成配方。目标：合成面板 → 装进控制器 → 定时出货进缓冲 → 取出。

## 3. 未决项（待作者拍板，见 DESIGN 第九节）

电单位命名 / 采集面板是否锚定真实区块条件 / 具体数值基调 / 是否跨维度或强制加载区块。
