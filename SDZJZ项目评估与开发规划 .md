**生电终结者（SDZJZ）项目评估与开发规划**

评估日期：2026-09-05（UTC）  
仓库：[qiaodaxian233/SDZJZ](https://github.com/qiaodaxian233/SDZJZ)  
评估基线：`main@5131f1b1e0c78d47571ea72ae4af866443fa85ab`，里程碑 m530b，版本 `0.1.530`。以下“现状”均指这个提交。

增补日期：2026-09-05（UTC）。第十三至十七章新增可用 API、版本边界、接入建议和执行清单，并链接官方开发资料。第一至十二章保留原评估记录；本次增补未重新运行仓库检查或游戏实测。下文“建议接入”不代表项目已经实现，也不代表第三方模组覆盖全部六格。

**建议把下一阶段目标定为：以 1.21.1 Fabric 为功能参照，交付可验证的多加载器版本，并补齐 1.20.1 的生产玩法。** 项目已具备清晰定位和相当规模的实现。当前投入应集中在功能对齐、适配边界和发布验收；新增机器按完成这一阶段后的收益排序。

这是一份基于代码、构建配置、任务记录与 CI 的规划建议。没有修改或提交项目代码，也没有进行 Minecraft 客户端操作、真实存档升级或 Create 联机实测。

**一、我实际核查了什么**

读取了 README、开发守则、交接文档当前状态、作业表、1.20.1 缺口普查及各版本构建配置；重点检查了机器定义、主线与 1.20.1 核心 tick、升级系统、超大工作台与测试、跨加载器传输接口、方块注册及初始化路径。

| 核查项 | 结果 | 可以说明的范围 |
|---|---|---|
| 仓库现有 22 个离线检查脚本 | 全部退出成功 | 当前配置下的静态门控通过；其中 `gauge_audit` 是只报告、不阻断的工具，本轮仍有待判读条目 |
| 当前提交的 GitHub Actions | 8 个 job 全部成功 | 主线构建、主线 GameTest、1.20.1 构建与 GameTest、NeoForge 骨架构建、26.1/26.2 构建与测试及辅助任务成功 |
| 游戏内完整玩法 | 本轮未实测 | 不能据 CI 成功认定所有机型、客户端交互和旧存档均已验收 |
| 工作区 | 源码未改动 | 本轮交付为评估与规划 |

CI 依据：[运行 33953566152](https://github.com/qiaodaxian233/SDZJZ/actions/runs/33953566152)，对应上述提交，2026-09-05 07:50 UTC 完成。核验通过 GitHub API 的运行及逐 job 状态完成，未下载 JUnit 报告逐条复核。

**二、项目定位与版本现状**

产品定位是“不会搭复杂红石的玩家，也能通过合成机器、拖动节点和连接物流完成自动化”。画布交互、存储网络和规模化生产共同构成核心体验。建议以三个玩家结果衡量产品：能造出第一台机器、能连出持续生产链、能看懂停机原因并恢复生产。

主线定义表对应 **101 台机器，另有 6 个逻辑节点**。部分文档里的 107 是把两者合计，不能直接当作新增 6 台机器，也不能代表每个版本都有 107 个可运行机型。

| 版本与加载器 | 当前实现 | 本阶段安排 |
|---|---|---|
| 1.21.1 Fabric | 功能参照版本；机器、画布、存储、升级、工作台、终端等已有实现 | 维持稳定，作为行为和资源对照基线 |
| 1.20.1 Fabric | 已有生产与存储主链、画布、超大工作台、压缩包、抓物笼；功能移植未完成 | 补齐自动合成、升级、终端与特种机型等 |
| 1.21.1 NeoForge | 独立构建目前只挂 `common/`，入口用于验证骨架，无完整玩法 | 按现有 F1 优先推进，拆成多个可验收任务 |
| 1.21.1 MinecraftForge | 仓库尚无对应实现目录 | F2，复用 F1 提炼出的共享边界 |
| 1.20.1 MinecraftForge | 仓库尚无对应实现目录 | F3，接入已经明确范围的 1.20.1 功能集 |
| 1.20.1 NeoForge | 仓库尚无对应实现目录 | F4，复用 F3 的可复用部分，独立验证接口差异 |
| 26.1 / 26.2 Fabric | 工具链与配方域适配验证；26.1 共享 26.2 的 Java 源，尚无完整玩法 | 保持已有 CI，完整玩法移植单列后续阶段 |

本规划沿用作业表里已记录的 **1.20.1、1.21.1 × Fabric、MinecraftForge、NeoForge** 六格目标，以及 Create 物流对接目标。26.x 继续保留在路线中，当前六格验收与它分别排期。

1.20.1 有三个尤其需要说明的事实：

1. 超大工作台已经落地，m521 正文中“缺少造机器入口”属于历史状态，不能再次当成待开发任务。
2. 抓物笼落地后，工作台测试中的可解析配方基准为 **100 条**，仓库当前记为 **100/122**。这个检查验证结果物品与材料 ID 能否在注册表中解析；它没有证明每条配方的生存获取路径、后续机器 tick 和目标设置都已完成。
3. 四类升级物品已注册，但节点升级行为与画布槽位仍缺；自动合成的配方查询已接入，目标 UI 与运行分支仍缺。`StructureCore120` 对未实现的特种机型仍有显式停机分支。

**三、最值得优先解决的五个问题**

| 优先级 | 发现与依据 | 对开发的影响 | 建议处理 |
|---|---|---|---|
| P0 | F1 工作量描述偏乐观：150 个 xplat Java 文件中，至少 23 个通过显式 import 引用根 `src` 中的类 | “没有加载器 import”还不足以证明能直接挂进新加载器；注册表、方块实体、入口类仍形成依赖 | 先做依赖闭包与初始化顺序清单，再逐域接入 |
| P0 | 可注册、配方可解析、可运行、可存档的状态混在一起 | CI 可通过，但玩家仍可能合成出不能工作的机器 | 建立逐功能、逐版本的能力表，把这些状态分别记录 |
| P1 | A8 曾以“本世代没有升级系统”销项，后续 N1 又把升级物品接入并引用 A8 | 升级系统没有一个明确、可执行的收尾任务 | 重开独立 U1/U2：升级安装与数值生效、融合阶位 |
| P1 | 文档状态分散且存在冲突：1.20.1 README 仍写无玩法；旧设计仍停留在设计期；旧守则的输出规则与现有实现不同 | 新开发者容易重复已完成工作，或按旧规则改变现有玩法 | 用一页现状和生效决策表统一入口，旧记录保留为历史 |
| P1 | 大文件与客户端搬入副本仍使改动容易漏接 | 主线核心约 3865 行、主线画布约 2630 行、1.20.1 画布 992 行；适配与业务修改容易相互牵连 | 按 F1 和 A12 的具体需要分域抽取，每次保持行为对照与编译通过 |

“23 个文件”仅统计去注释后显式 import 的直接引用，是依赖规模的下界，不是全量依赖图，也不表示 23 个文件都必须重写。

F1 还存在两个实际的技术检查点。`ModBlocks` 目前在静态字段初始化中直接注册方块和物品，需要核对新加载器的注册生命周期。`FabricXfer` 使用 Fabric 的事务式传输，迁移时需要同时实现“访问外部容器”和“向外暴露自家存储”两侧，并验证部分接收、失败与组件保真。

NeoForge 1.21.1 官方能力文档以 `IItemHandler` 表达物品容器接口；结合本仓的 `FabricXfer` 实现，我据此判断传输语义需要专门适配与测试，不能仅按替换类名估时。参考：[NeoForge 1.21.1 Capabilities](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/)。这属于实现风险评估，并非已发现物品复制漏洞。

**四、建议的阶段路线**

估时假设：1 名熟悉 Java / Minecraft 模组的开发者持续投入，作者每周提供一次集中实机反馈，CI 和开发环境可正常使用。以下是工作量预算；首个 NeoForge 玩法闭环完成后，应按实际耗时修订。每阶段范围以验收条件控制，不为赶日期降低标准。

| 阶段 | 预计投入 | 主要交付 | 离开阶段的条件 |
|---|---|---|---|
| M0：统一基线 | 2–3 个工作日 | 生效目标、版本能力表、F1 依赖图、存档验证清单 | 每项功能能回答“哪个版本做到哪一步”；下一批任务没有互相矛盾的状态 |
| M1：F1 NeoForge 1.21.1 | 2–4 周，估时不确定性较高 | 核心注册、服务端运行、存储接口、网络、画布与工作台，完成共享业务接入 | 同一产物通过编译、服务器启动、行为测试和客户端核心玩法验收；其余功能逐项登记差异 |
| M2：1.20.1 主要玩法对齐 | 3–5 周 | 自动合成、升级、经验基础、N3 分组任务与关键 UI；有明确范围的可玩版本 | 核心玩法清单全部端到端通过；版本固有差异明确列出，未实现功能有清晰说明 |
| M3：F2 / F3 / F4 | 3–6 周 | 其余三个加载器实现、逐格 CI 与兼容验证 | 六格各自通过约定功能清单；每格产物都与具体提交及验证结果对应 |
| M4：Beta 发布准备 | 1–2 周 | 存档回归、客户端检查、Create/JEI 验证、性能对比、玩家文档与发布材料 | 约定矩阵无阻断问题，剩余问题有影响说明与复现步骤 |

按上述投入假设，六格达到本规划定义的 Beta 验收范围，建议先准备 **约 10–18 周** 的预算。它不包含 26.x 完整移植、新玩法扩张，也不是对“六格全部功能零差异”的工期承诺。区块机器、随身仓库 Mixin 和跨版本专属内容若要求同批完整验收，应在 M0 计入范围后重新估算。

近期顺序建议为 **M0 → F1 → 1.20.1 关键功能收口 → F2 → F3 → F4 → Beta**。这保留仓库“下一刀 F1”的方向，同时避免把未收口的 1.20.1 功能状态直接复制到三个加载器。

**五、F1 应怎样拆，才能持续看到成果**

| 任务 | 内容与触点 | 验收 |
|---|---|---|
| F1-0：依赖闭包 | 对 `src` / `xplat` / `common` 建类清单；标出注册、BE、菜单、网络、客户端和第三方依赖；扩充分层检查的 Forge/NeoForge 符号范围 | 每个 xplat 所需类都能定位到共享实现或平台实现；不能只统计 Fabric 关键词 |
| F1-1：注册与初始化 | 注册时序、平台实现安装、服务端入口、基础物品/方块/BE/菜单/资源 | 在 NeoForge 中能启动服务器、进入客户端、获得并放置核心与工作台；所需安装口都有测试 |
| F1-2：存储与传输 | 对齐 `Xfer` 五口、存储账本及外部能力暴露；保留现有精确栈身份语义 | 普通物品、命名物品、附魔书、药水存取保真；部分接收和失败路径不多扣不丢件 |
| F1-3：生产与网络 | 核心 tick、节点操作、目标配置、同步；保留原服务端资格校验与解码边界 | 新世界里完成合成机器、插入核心、连线、生产、入仓和取物；专用服务器运行通过 |
| F1-4：客户端与体验 | 画布、终端、工作台、渲染、菜单和交互；资源从共同来源接入 | 打开屏幕、拖节点、连线、缩放、分组、填料与取物均可操作；无缺失模型或客户端类误入服务端 |
| F1-5：差异收尾 | 逐项核对主线功能、JEI/可选联动、大堆叠与随身仓库等 Mixin；升级 CI 为玩法测试 | 已声明支持的功能都有证据；不能以“jar 成功生成”关闭整个 F1 |

上述任务有依赖关系，可按功能域继续细分提交。先让一个完整生产场景在 NeoForge 运转，再扩展剩余能力；验证所需共享代码逐步抽出，避免一次移动整个核心类。

**六、1.20.1 的任务排序与验收**

| 顺序 / ID | 工作项 | 验收条件 |
|---|---|---|
| 1 / C4a、C4b | 自动合成：目标 UI、配置包、接收器、tick | 从节点菜单选择目标；供料后持续产出；无目标可识别；无关材料不扣；配方余料正确返回；重进存档目标保留 |
| 2 / U1 | 加速、数量、并发升级：身份映射、安装/拆卸、状态同步、产速计算和画布槽位 | 装入扣正确物品、拆出返还；三类升级分别生效；Shift 批量与拆机返还守恒；存档保留等级 |
| 3 / U2 | 融合阶位与存储升级，分别核对消费方 | 融合数量、阶位、拆解行为符合主线规则；存储升级有独立验收，避免只检查物品是否注册 |
| 4 / C3、C2b 分拆 | 经验池基础；酿造域；附魔域分别排任务 | 经验有入账、使用与存档回归；酿造链能生成正确药水；附魔定价在决定后单独验收 |
| 5 / N3-a | 数据链接器、无线节点、卫星节点 | 可绑定、可断开、距离/维度行为明确；重启后恢复；失联时状态可解释 |
| 6 / N3-b | 村民合同、交易所、交易相关机型 | 合同来源到就业再到交易闭环；物品、折扣与经验状态持久化 |
| 7 / N3-c | 手持终端、自动喂食器、随身仓库与必要 Mixin | 远程开屏、库存同步、补货/喂食逐项验证；仓库物品与专属槽位不因死亡或重进丢失，按现行设计核对 |
| 8 / A12 | 画布连线绘制与拖线交互的共用抽取 | 主线与 1.20.1 两套宿主同场景通过；搬入副本减少，取色/矩阵/缩放/事件前提写清 |
| 9 / C5 及特种机型 | 酿造塔、附魔工厂、复制器、虚空处理器、区块族、信标等逐族收口 | 每族独立记录：目标可设置、tick 可执行、产物正确、持久化正确、故障恢复正确 |
| 10 / 兼容与观测 | 性能命令、JEI 转移、Create 物流 | 可重复测量；同一套物品经过两边物流与终端后数量和数据保持一致 |

N3 原来集中列了多种物品、屏幕和方块，实际上横跨多个玩法域，不适合当作一个普通小任务。`logo` 与 `chunk_data_core` 分别随资源整理、区块机器能力验收归位。

作业表当前把酿造、附魔以及后续经验任务连在同一条阻塞线上。根据 `RecipeAccess` 的独立方法和现有 tick 结构，**建议先检查并解除不必要的依赖**：自动合成继续使用已实现的合成域；经验池基础和酿造域尽可能独立推进。附魔成本的选择仅阻塞真正依赖它的功能。这是排期建议，落地前仍须核对具体调用关系。

**七、保留现有架构，补齐实际边界**

当前源集规模（Java 文件、行数包含注释与空行）：

| 源集 | 文件数 | 行数 | 建议职责 |
|---|---:|---:|---|
| `common/src/main/java` | 23 | 2444 | 零 MC 依赖的数据定义、规划器、配置与算法 |
| `xplat/src/main/java` | 150 | 19386 | 可共享的 Minecraft 业务及界面组件，通过明确的宿主接口取得差异能力 |
| 根 `src/main/java` | 28 | 8238 | 当前混有 Fabric 适配与较大业务类；逐步拆开平台差异与业务 |
| `versions/1.20.1/src/main/java` | 41 | 6936 | 1.20.1 数据模型和 API 适配、入口、测试；减少与主线重复的行为实现 |
| NeoForge 1.21.1 | 1 | 33 | 目前为启动骨架，尚需接入具体功能 |
| Modern 26.2（26.1 共用） | 5 | 518 | 新世代配方等适配实现 |

下一步架构工作应围绕正在实现的功能进行：

- 给注册表对象和菜单类型建立清晰的安装时机，业务层通过共享契约获取对象，减少依赖根 Fabric 入口类。
- 服务端核心按生产执行、目标/升级操作、存储端点、同步、持久化等职责逐段抽取；先服务 F1 的具体接入点。
- 延续 A12，把画布重复的连线绘制和拖线状态逐步共用。衡量结果看重复行为是否减少、两端操作是否一致，行数只作辅助。
- 把 1.20.1 的 xplat 挂载白名单作为构建输入集中管理，补充依赖闭包验证；其余 1.20.1 加载器读取同一份功能范围。
- 延续已有服务端权威、组件/NBT 保真、有界网络解码与调度器行为测试。主线已有的调度优化作为回归基线；1.20.1 存在独立调度差异，需要单独测量。

本轮不需要先更换整个构建框架。现有 shared source + 平台实现的方式已经能承载后续工作，先验证首格的依赖与生命周期，再判断是否有足够收益调整目录或构建结构。

**八、把“完成”写成玩家可以验证的结果**

建议为每个功能、每个版本记录以下状态：定义存在、注册成功、配方 ID 可解析、生存可取得、行为可运行、保存/恢复正确、客户端验收完成。允许标记“不适用”，但必须给出具体原因。已有“配方可达”指标保留，并写明它仅检查注册表可解析性。

一个阶段完成时需要具备以下证据：

1. 目标提交和最终 jar 明确，受影响的构建与测试成功。
2. 生存链完整：核心模块 → 超大工作台 → 获得机器 → 插入画布 → 接供料/出料 → 生产入仓 → 终端取物；刷怪链包含捕获正确生物的笼子。
3. 物品守恒：只对搬运、压缩/拆包、槽位操作、升级返还等应守恒的动作检查数量；生产与合成按各自输入/输出规则检查。带附加数据的物品另验数据保真。
4. 保存/恢复：用测试存档副本验证节点位置、连线、分组、升级、库存、目标与经验；退出再进入或重启服务器后仍正确。
5. 客户端交互：单机与专用服务器连接各验一次，覆盖分辨率、GUI 缩放、画布缩放、拖动、菜单和异常状态提示。
6. 兼容：锁定具体 Minecraft、加载器、Create、JEI 版本组合；分别验证未安装、安装与物流实际连接场景，不能仅以同一接口体系推断兼容完成。

性能验收沿用仓库已有 profile / bench 思路。在相同硬件、配置和测试存档下，从 1 核×64 节点开始，增加到 10 核×256 节点及 100 核×64 节点；记录 MSPT、核心吞吐分布、网络同步量、内存趋势及客户端帧时间。先建立基线，再与改动后对比。新的调度或缓存实现出现后才扩大测试规模。

建议 Beta 标准：约定场景中无崩溃、无搬运丢件或复制、无永久饿死核心；客户端功能清单全部通过；性能差异可解释。具体数值预算在 M0 实测后制定，不能从源码行数或机器数量推断。

**九、文档、任务与玩家体验整理**

建议维护三份简短的当前文档：一页项目现状、一张版本能力表、一张只有活跃任务的作业表。DEVLOG 保存历史；HANDOVER 首页只放当前阶段、有效约束、未决项及证据链接，旧过程转为索引。

每个任务包含：任务 ID、玩家结果、版本/加载器、依赖、预计工作量、触及的数据/协议、验收步骤、提交 SHA 和 CI 链接。保留现有 mNNN 编号作为开发追踪；对玩家提供可辨认的发行标签、安装组合与变更说明。

玩家文案需要同步整理。已有帮助卡继续使用，补充三条短教程：第一条免费生产线、带供料的加工链、抓物笼驱动的刷怪链。状态提示用“未设置目标”“缺少材料”“输出位置已满”“当前版本尚未支持该机器”等玩家语言，替换运行时出现的开发分片编号。

1.20.1 中引用新版原版物品的配方，仓库记录的决定是保留原表。本规划沿用这个决定。材料替代、隐藏条目或新增兼容材料属于另外的玩法/呈现选择，不以版本对齐为由默认实施。

**十、未来两周可直接执行的清单**

| 工作窗口 | 任务 | 可检查的交付物 |
|---|---|---|
| 第 1–2 天 | M0：整理能力表、修正文档状态、重开 U1/U2、给 N3 分组 | 一页状态、一张差异表、按依赖排序的活跃任务 |
| 第 3–5 天 | F1-0 与 F1-1 第一段：依赖闭包、注册时序、NeoForge 服务端基础启动 | 依赖清单、首个通过的构建/启动记录；第 5 天按真实卡点修订估时 |
| 第 2 周 | F1-2、F1-3：储存接口、共享业务接入、最小生产链；接上必要客户端入口 | 可放置的核心/工作台、生产和取物演示、对应行为测试与剩余缺口 |

这两周的目标是取得 NeoForge 首个可用生产场景。完整画布交互与其余主线功能继续按 F1-4/F1-5 收尾，完成后再进入 M2。进度汇报应说明用户能操作什么，以及哪个具体步骤仍未完成。

**十一、仍需在对应功能开工前决定的事项**

当前调研和规划不因这些问题停下；它们只影响相应分支。

| 事项 | 现有状态与建议 | 影响范围 |
|---|---|---|
| 1.20.1 附魔定价 | 仓库 A/B 尚待决定。若优先与主线定价一致，建议评估共同附魔 ID 的显式映射表，并维护完整性检查 | 附魔域与附魔工厂 |
| 首批 Beta 范围 | 建议先承诺生产、存储、画布、工作台、自动合成和升级主链；区块族等重功能逐项明确同批或后续交付 | M2–M4 工期与发布承诺 |
| 真实人力与时间 | 本规划按一名熟练开发者估计；若主要是业余投入，应按可投入工时重新折算 | 所有阶段日期 |

**十二、关键代码与记录依据**

下面均锁定本次评估提交，便于与未来版本比较：

- [项目 README](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/README.md)：玩法定位及主线介绍。
- [当前作业表](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/docs/作业表.md)：F/N/A/C/D 任务、六格目标和已记录决策。
- [机器定义](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/common/src/main/java/com/sdzjz/machine/Machines.java)与[文档同步脚本](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/docs/tools_docs_sync.py)：101 台机器与 6 个逻辑节点的统计口径。
- [RetroBenchTests](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/versions/1.20.1/src/main/java/com/sdzjz/retro/RetroBenchTests.java)：可解析配方基准、已知缺口和工作台端到端测试。
- [RetroItems](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/versions/1.20.1/src/main/java/com/sdzjz/retro/RetroItems.java)、[StructureCore120](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/versions/1.20.1/src/main/java/com/sdzjz/retro/StructureCore120.java)、[RetroRecipeAccess](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/versions/1.20.1/src/main/java/com/sdzjz/retro/RetroRecipeAccess.java)：升级物品注册、实际生产分支及配方域实现。
- [NeoForge 构建配置](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/versions/1.21.1/neoforge/build.gradle)：当前仅挂载 Common。
- [ModBlocks](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/xplat/src/main/java/com/sdzjz/registry/ModBlocks.java)、[FabricXfer](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/src/main/java/com/sdzjz/loader/FabricXfer.java)：注册生命周期与传输语义的具体适配面。
- [CI 配置](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/.github/workflows/ci.yml)、[只报告的检查工具](https://github.com/qiaodaxian233/SDZJZ/blob/5131f1b1e0c78d47571ea72ae4af866443fa85ab/docs/tools_gauge_audit.py)：构建/测试覆盖范围及静态检查边界。

文中当前实现与 CI 状态来自以上基线；任务优先级、工期、Beta 范围、架构分片和验收建议为本次评估提出的方案。

**十三、项目能用到的 API 与选型建议（新增）**

SDZJZ 当前最需要的是 Minecraft、加载器及模组提供的 Java API。它们通过开发依赖和加载器入口接入，通常不需要 HTTP API Key。联网服务接口单列在第十六章，适合版本查询与发布辅助。

本章优先级：**P0＝当前适配闭环必需；P1＝Beta 兼容或体验优先；P2＝有需求后扩展。** 已有的实现应复用并补齐适配，不按新增 API 数量衡量进度。表内类名用于定位官方接口；Yarn 与 Mojang mappings 的名称、泛型和方法签名可能不同，以对应子工程的映射和锁定依赖为准。

**13.1 基础能力：先把注册、搬运、同步和生产接通**

| API / 官方入口 | 能用在 SDZJZ 的哪里 | 接入建议与版本边界 | 优先级 / 关联任务 |
|---|---|---|---|
| 注册 API：Fabric 所用原版 `Registry.register`；Forge / NeoForge 的 `DeferredRegister` | 核心、机器物品、超大工作台、方块实体、菜单、配方类型注册 | 把定义和实际注册时机分开。Forge / NeoForge 各自在模组事件总线接入延迟注册；共享代码通过句柄获取对象。[NeoForge 1.21.1 注册](https://docs.neoforged.net/docs/1.21.1/concepts/registries/)、[Forge 1.20.1 注册](https://docs.minecraftforge.net/en/1.20.1/concepts/registries/) | P0 / F1-1 |
| Fabric Transfer API：`Storage<ItemVariant>`、`ItemStorage.SIDED`、`Transaction` | 外部容器接入、自家仓库存取、自动供料与出料 | 延续 `FabricXfer`。自家账本也要参与事务，必要时用 `SnapshotParticipant` 管理快照；只给外部操作套事务不够。[Storage](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/transfer/v1/storage/Storage.html)、[Transaction](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/transfer/v1/transaction/Transaction.html) | P0 / F1-2，对照主线 |
| NeoForge Capabilities：`IItemHandler`、`Capabilities.ItemHandler.BLOCK`、`RegisterCapabilitiesEvent` | NeoForge 1.21.1 存储端口、与其他物流模组互通 | 同时实现查询外部能力与注册自家能力；高频查询可用 `BlockCapabilityCache`，端口实现变化时正确失效。[1.21.1 Capabilities](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/) | P0 / F1-2 |
| Forge Capabilities：`IItemHandler`、`ForgeCapabilities.ITEM_HANDLER`、`LazyOptional` | Forge 1.20.1 的同类存储接入 | 按 Forge 生命周期提供和失效能力。不要把 NeoForge 1.21.1 的注册方式直接复制到旧版本；Forge 1.21.1、NeoForge 1.20.1 分别锁定目标依赖后核对。[Forge 1.20.1 Capabilities](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/) | P0 / F2、F3、F4 |
| Fabric Networking：`ServerPlayNetworking`、`ClientPlayNetworking`；1.21.1 的 `PayloadTypeRegistry` | 节点操作、升级安装、合成目标、终端查询、画布同步 | 1.21.1 按方向先注册 payload 类型和编解码器，再注册接收器。1.20.1 使用其对应版本的网络适配，不能直接复用新版签名。[Fabric 1.21.1 网络入口](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.html) | P0 / F1-3、C4、U1 |
| NeoForge Networking：`RegisterPayloadHandlersEvent`、`PayloadRegistrar`、`PacketDistributor`；原版 `CustomPacketPayload`、`StreamCodec` | NeoForge 客户端与专用服务器通信 | 复用业务消息定义，平台侧封装编码和发送；处理器线程按配置核对，世界修改在服务器主线程执行。[1.21.1 Payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/) | P0 / F1-3 |
| Forge Networking：`SimpleChannel`、`NetworkRegistry`、`NetworkEvent.Context` | Forge 1.20.1 的配置包和状态同步 | 用独立协议版本与服务端校验；处理网络线程消息时，通过 `enqueueWork` 进入主线程后操作世界。此行是 1.20.1 路线，其他格不直接套用。[Forge 1.20.1 SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/) | P0 / F3 |
| 原版配方 API：`RecipeManager`、`RecipeType`、`RecipeSerializer`、`Ingredient` | 自动合成目标查询、机器加工配方、JEI / EMI 的共同数据来源 | 经现有 `RecipeAccess` 隔离版本差异。生产执行在服务端重新匹配输入并处理余料；1.21.1 与 1.20.1 的输入模型及序列化实现分开。[NeoForge 1.21.1 配方](https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/)、[Forge 1.20.1 自定义配方](https://docs.minecraftforge.net/en/1.20.1/resources/server/recipes/custom/) | P0 / C4、F1-3 |
| 物品数据：1.21.1 Data Components；1.20.1 对应 NBT 读写 | 命名物品、药水、附魔书、升级及带数据机器物品的保真 | 建立跨版本的物品身份和序列化边界；1.21.1 使用对应 `ItemStack` / 组件读写机制，不能只保存注册 ID 与数量。[1.21.1 Data Components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/) | P0 / F1-2、U1、U2 |
| 持久化 API：方块实体保存钩子、世界级 `SavedData` | 节点图、库存账本、无线绑定、升级、经验和版本迁移 | 按数据归属选择保存位置，维持单一权威来源；修改后标记待保存。使用世界级数据前先确认现有账本是否已承担该职责。[Forge 1.20.1 Saved Data](https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/) | P0 / F1、N3、存档验收 |

Fabric Javadoc 链接中的 `0.116.7+1.21.1` 是本次查阅的文档版本，**不是要求项目升级到该版本**。开始编码前，仍按仓库当前依赖和目标 Minecraft 版本选择对应文档。

**13.2 联动 API：围绕玩家实际能做什么接入**

| 候选 API / 官方入口 | 建议实现的玩家功能 | 接入边界与验收 | 优先级 |
|---|---|---|---|
| Create：先接加载器标准存储接口；深层联动再评估其公开 API | Create 物流向 SDZJZ 供料，SDZJZ 产物送回 Create 生产线 | 建议先验证静态物品输入/输出端口。机械动力、移动结构、订单物流分别立项。Fabric 移植与上游有独立维护入口，按实际发布版本锁定组合。[Create 上游](https://github.com/Creators-of-Create/Create)、[Create Fabric](https://github.com/Fabricators-of-Create/Create) | P1 / 既有联动目标 |
| JEI：`IModPlugin`、`IRecipeCategory`、`registerRecipeTransferHandlers` / `IRecipeTransferHandler` | 展示机器配方、点击工作台查看用途、从配方页填料或设置自动合成目标 | 沿用已有 JEI 工作。把“设置目标”和“实际搬运材料”分开；虚拟槽和大仓库走自定义转移，最终由服务端验证。Fabric 1.21.1 的 `jei_mod_plugin` 入口和 Forge / NeoForge 的插件发现方式分别配置。[1.21.1 插件入口](https://github.com/mezz/JustEnoughItems/wiki/Creating-Plugins-%5BMinecraft-1.21-and-1.21.1%5D)、[转移接口说明](https://github.com/mezz/JustEnoughItems/wiki/Recipe-Transfer-Handlers) | P1 / F1-5、C4 |
| Jade：`IWailaPlugin`、`IBlockComponentProvider`、`IServerDataProvider<BlockAccessor>` | 看向核心时显示运行节点数、库存摘要、停机原因 | 先做核心的简短只读摘要；详细虚拟节点仍在画布查看。仅同步展示所需字段，沿用权限范围。[Jade 1.20–1.21.5 开发文档](https://jademc.readthedocs.io/en/latest/plugins20/getting-started/) | P1 候选 / 体验收尾 |
| EMI：`EmiPlugin`、`EmiRegistry`、`EmiRecipe` | 为使用 EMI 的整合包提供机器配方和生产链展示 | 有明确整合包需求再做。与 JEI 共用 SDZJZ 配方描述，分别实现展示适配；配方可见、目标设置、材料转移分别验收。[EMI 开发指南](https://github.com/emilyploszaj/emi/wiki/Getting-Started-Guide) | P2 |
| KubeJS：`ServerEvents.recipes`、`event.custom` | 整合包作者调整加工配方、输入输出和生产耗时 | 前提是对应规则已通过 `RecipeSerializer` 进入数据包配方系统；Java 硬编码分支不会自动支持脚本。先提供明确 JSON 格式与示例，再考虑专用扩展。[KubeJS 配方文档](https://kubejs.com/wiki/tutorials/recipes) | P2 |
| CC:Tweaked：`IPeripheral`、`@LuaFunction` | 电脑读取库存摘要、机器状态和生产统计；后续按需控制节点 | 第一版建议只读。访问世界使用 `mainThread = true` 或主线程任务；开放写操作时复用服务端权限、数量与频率限制。[CC:Tweaked 1.21.1 IPeripheral](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/peripheral/IPeripheral.html) | P2 |

**上述可选联动都要逐格确认目标 jar 是否存在。** 某一格缺少上游发布物时，登记“该组合暂无可验收依赖”，继续完成 SDZJZ 的基础功能；不要由一个 Forge / Fabric 构建推断其他加载器也受支持。JEI 的转移 Wiki 含旧版注册示例，具体注册代码以对应 Minecraft 版本的插件指南和 API jar 为准。

如果后续确实增加液体端口，可使用 Fabric 的 `Storage<FluidVariant>` / `FluidStorage.SIDED`，以及 Forge / NeoForge 的 `IFluidHandler`。建议先定义统一内部单位、端口方向与容量上限，再做平台换算；Fabric 的标准一桶为 81,000 droplets，不能直接把数量字段当作其他平台的液体量。[Fabric 液体单位](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/transfer/v1/fluid/FluidConstants.html)、[NeoForge 流体能力](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/)

Forge / NeoForge 还提供 `IEnergyStorage`，但是否引入能量消耗属于玩法决策。当前先完成原有生产机制；若以后增加能量系统，再为 Fabric 单独选择对应能量 API 并重新估算平衡与兼容工作。[Forge 提供的能力](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/)

**13.3 开发与验收工具**

| API / 工具 | 建议用途 | 使用方式 |
|---|---|---|
| Data Generation：`DataGenerator`、`DataProvider` 及平台的数据生成入口 | 从共同定义生成配方、标签、模型等，减少各版本资源漂移 | 先检查现有脚本覆盖，缺什么再接什么；保留版本输出差异与原配方决策。[Forge 数据生成](https://docs.minecraftforge.net/en/1.20.1/datagen/) |
| GameTest：`@GameTest`、`GameTestHelper` 及平台注册入口 | 自动检查搬运、生产、升级返还、重载后的行为 | 扩充已有行为测试，让同一场景分别在目标加载器执行；画布手感与完整存档升级继续单独验收。[NeoForge 1.21.1 Game Tests](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) |
| spark 性能分析工具 | 对照现有 profile / bench，定位 tick、配方查询、同步及分配热点 | 建议作为测试环境工具使用，记录同场景改动前后的 profile；当前无需嵌入其 API 或列为玩家必装依赖。[spark 官方文档](https://spark.lucko.me/docs) |

**十四、API 接入时必须写清的四个边界（新增）**

**14.1 物品传输：两套接口需要不同的提交策略**

Fabric 事务参与者能够在未提交时回滚；嵌套事务提交后仍可能随外层事务回滚。因此，建议把账本修改纳入同一事务，把通知等副作用放在适当的最终提交阶段。[Fabric Transaction 契约](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/transfer/v1/transaction/Transaction.html)

Forge / NeoForge 的 `IItemHandler` 接入采用模拟与实际执行的方式，不能据此给两个任意外部容器承诺共同回滚。结合 SDZJZ 的仓库与 `Xfer`，建议这样实现：

1. 模拟结果只用于规划，账本结算以实际插入的余量、实际提取出的栈为准。
2. 向外输出前，在自家仓库预留待出物品，避免同一批物品被再次取走；未被接收的部分恢复到自家可用库存。
3. 从外部提取前，预留自家接收容量；若一次搬运跨越多个外部端点，使用有上限且可保存的中转缓冲承接未完成部分。
4. 检查端口方向、重复回调和邻接变化；不得通过重复执行同一扣除动作修复失败。

这是一项实现建议，仍须按目标 API 契约与具体容器行为验证。验收至少覆盖：目标全满、部分接收、源不足、命名物品、药水、端口切换、区块卸载/重载和双向物流同时运行。大仓库向槽位接口暴露容量时，采用有界槽位视图和分批搬运；避免把内部总量直接塞进单个物品栈，或每次查询遍历全部库存。

**14.2 网络：共享玩家操作，分别适配传输格式**

建议共享“设置节点目标、安装升级、请求库存分页”等业务消息含义，加载器侧承担 payload / channel 的注册和编解码。每条写操作都由服务端检查玩家是否可访问核心、目标节点是否存在、材料与数量是否合法；客户端提交的产物、库存余额或升级等级不能直接作为结算结果。

画布打开时按需要发送快照，之后同步变化；大库存提供分页或搜索结果。为请求加入可验证的菜单会话或状态版本，在过期时让客户端刷新，避免多人操作覆盖新状态。协议版本、消息长度、列表数量与错误响应应写在同一份接口说明里。这里描述的是建议的应用层约束；传输入口分别参照[Fabric 网络文档](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.html)与[NeoForge 网络文档](https://docs.neoforged.net/docs/1.21.1/networking/payload/)。

**14.3 配方与物品数据：共享规则，不混用版本对象**

| 边界 | 1.20.1 路线 | 1.21.1 路线 | 建议保留的共同约定 |
|---|---|---|---|
| 物品附加数据 | 对应版本的 NBT 与 `ItemStack` 保存/恢复 | 对应版本的 Data Components 与栈序列化 | 精确物品身份、数量和完整附加数据；同版本跨加载器保真分别实测 |
| 自定义配方序列化 | `fromJson`、`fromNetwork`、`toNetwork` 等目标版本接口 | `MapCodec`、`StreamCodec` 等目标版本接口 | 配方 ID、输入、输出、耗时、余料与升级影响规则 |
| 配方运行 | 由 1.20.1 的 `RecipeAccess` 实现查询与匹配 | 由主线对应实现查询与匹配 | 服务端执行；先确认完整输入与输出空间，再提交生产结果 |
| 重载与存档 | 各自处理资源重载及旧格式读取 | 各自处理资源重载及旧格式读取 | 持久化稳定 ID 和数据格式版本；重载后失效配方缓存、重新检查已设目标 |

序列化接口依据：[Forge 1.20.1 自定义配方](https://docs.minecraftforge.net/en/1.20.1/resources/server/recipes/custom/)、[NeoForge 1.21.1 配方](https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/)、[1.21.1 组件](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)。共同约定为针对本项目的设计建议。它不构成 1.21.1 存档可降级到 1.20.1 的承诺；26.x 也需要独立适配与回归。

**14.4 可选依赖：安装与未安装都要可启动**

建议把 JEI、Create、Jade、EMI、KubeJS、CC:Tweaked 的直接类型引用限制在各自兼容模块，通用入口只加载实际存在的集成。编译时使用官方 API 依赖，开发运行环境再加完整模组；按加载器声明可选依赖及支持范围。JEI 官方已经给出这类依赖分离方式，可作为当前接入范例。[JEI 1.21.1 开发依赖](https://github.com/mezz/JustEnoughItems/wiki/Getting-Started-%5BMinecraft-1.21-and-1.21.1%5D)

每个联动至少验三种环境：未安装、安装目标版本、专用服务器运行。额外维护兼容清单，记录 Minecraft 版本、加载器版本、第三方版本、SDZJZ 提交、实际验证功能与结果。依赖版本先固定，在单独任务中升级；读取“latest”文档只用于发现变化，不直接替换生产构建。

**十五、结合 API 的开发建议（新增）**

**15.1 用现有边界承接 API，先服务 F1**

沿用 `common`、`xplat` 和加载器实现的方向。`common` 继续保留零 MC 依赖；MC 对象相关适配进入 `xplat` 的明确契约或版本层。优先复用已有 `Xfer`、`RecipeAccess` 与安装口，先补齐行为契约和目标实现，再决定是否有必要新增接口。

| 建议整理的边界 | 需要统一的内容 | 完成后的直接收益 |
|---|---|---|
| 注册与初始化 | 哪个阶段安装平台实现、何时可以访问注册对象 | F1-1 更容易定位未初始化或注册过早的问题 |
| `Xfer` 与存储端口 | 方向、数量上限、实际完成量、余量、模拟与提交语义 | Fabric / Forge / NeoForge 的搬运可使用同一套行为验收 |
| `RecipeAccess` | 稳定 ID、查询、匹配、结果、余料和重载失效 | 自动合成、JEI、EMI 共用规则，避免展示与实际产出不一致 |
| 网络与节点操作 | 权限、操作结果、错误原因、状态版本 | 画布、终端、JEI 转移以及未来外设共用服务端校验 |
| 状态摘要 | 运行状态、缺失材料、输出阻塞、最后成功生产时间 | 画布、Jade、诊断命令复用一份可解释的状态 |

这些是拟整理的职责，并非宣称仓库已经存在同名新类。建议先完成“箱子供料 → SDZJZ 生产 → 入仓 → 取物”的 NeoForge 场景，再用同一端口测试 Create 对接。仅为新增联动换掉整个跨加载器框架，当前收益不足。

**15.2 把停机原因作为一项正式产品能力**

建议由服务端产生稳定原因码，再由客户端翻译成玩家语言。首批覆盖未设目标、缺料、输出已满、外部端口不可用、配方已失效、当前版本未支持。画布显示受影响节点和恢复动作；核心与 Jade 只显示摘要。若多处故障同时发生，展示主要原因并允许查看详情，避免“停了但不知道为什么”。

**15.3 自动合成先完成单目标，再扩展多步骤规划**

C4 首批先交付单个目标的材料匹配、余料、缺料提示和持久化。JEI 的“填料”与“设目标”分别接入；复杂递归合成、循环配方处理和跨核心订单另排任务。这样能把当前已经接入的配方查询转换成可玩的功能，也能得到可复用的 API 行为基线。

**15.4 可扩展性先开放数据，再考虑公开 Java API**

若要支持整合包，建议先为适合数据化的加工规则提供 JSON 配方、明确字段和重载行为；默认规则维持原有决定，1.20.1 缺失原版材料的处理仍按原规划单独决策。KubeJS 能否修改某项规则，逐项标注“数据包支持 / 专用扩展支持 / 尚未开放”。

如果后续出现附属模组需求，再设计 `sdzjz-api`。首批可以考虑机器状态只读查询、配方扩展和经过校验的节点命令；不要直接暴露可变库存 Map 或核心内部实现。公开 API 应有版本、弃用说明和最小示例。以上都是后续候选，当前不计入 F1 的必交范围。

**15.5 性能优化以真实瓶颈为依据**

优先观察配方重复查找、库存扫描、能力查询与画布全量同步。只为实测热点加缓存，并同时实现配方重载、物品数据变化、端口替换等失效路径。绘制与网络同步按玩家当前查看范围裁剪；纯计算若要异步，先生成不可变快照，结果回服务器线程复核后应用，世界和库存修改保留在服务器线程。

**十六、可以用到的联网 API：发布与版本查询（新增）**

这些接口适合开发脚本、发布页面或用户主动触发的更新检查。建议核心生产与存档不依赖外网响应；HTTP 请求设置超时、缓存和失败退避，避免在游戏 tick 中等待请求。

| API / 官方文档 | 可用端点 | SDZJZ 的用途与建议 |
|---|---|---|
| [GitHub Releases REST API](https://docs.github.com/en/rest/releases/releases) | 主机 `api.github.com`；`GET /repos/qiaodaxian233/SDZJZ/releases`；正式版可查询 `GET /repos/qiaodaxian233/SDZJZ/releases/latest` | 读取发布版本、变更说明与 jar 附件。Beta 检查使用 release 列表并明确筛选规则；`latest` 只返回符合条件的正式发布，不能拿它判断最新预发布版本。是否已有可用 Release，本次未核验。 |
| [Modrinth 项目版本 API](https://docs.modrinth.com/api/operations/getprojectversions/) | 主机 `api.modrinth.com`；`GET /v2/project/{project_id_or_slug}/version`；查询参数 `loaders`、`game_versions` 使用 JSON 数组编码 | 为指定 Minecraft 与加载器查询版本、依赖和下载文件信息，也可辅助核对联动依赖是否有目标发布物。结果再按 `version_type` 区分 release / beta / alpha。项目 ID 必须取自真实发布项目；本次未确认 SDZJZ 的 Modrinth 项目 ID。 |

接入时区分公开查询、私有资源读取与发布写入所需权限。若以后添加发布自动化，凭据放在 CI 的秘密配置中，按目标仓库或项目授权；发布结果记录提交与产物校验值。玩家只需要选择正式版或测试版，以及匹配的 Minecraft / 加载器组合。

**十七、把 API 接入加入现有作业表（新增）**

| 新增任务建议 | 并入原任务 | 可交付内容 | 关闭条件 |
|---|---|---|---|
| API-01：锁定接口与依赖 | M0、F1-0 | 逐格 API 对照、依赖坐标、映射、必需/可选关系与对应文档 | F1 所需基础依赖可解析；联动缺少目标发布物时有明确记录 |
| API-02：注册与能力接入 | F1-1、F1-2 | NeoForge 注册入口、自家存储暴露、外部容器访问与能力失效 | 核心/工作台可放置；相邻箱子双向存取、部分接收及数据保真通过 |
| API-03：统一业务消息 | F1-3、C4、U1 | 节点目标与升级操作、错误反馈、状态同步的应用层约定 | 单机和专用服务器均能生产；过期/无效操作被正确处理 |
| API-04：Create 静态物流 | F1-5、兼容与观测 | 锁定组合中的输入/输出端口对接 | 选定 Create 物流部件实际供料/出料、堵塞恢复、重进存档均通过 |
| API-05：JEI 展示与操作 | F1-5、C4 | 机器配方页、工作台转移、自动合成目标选择 | 展示与实际产出一致；缺料反馈正确；材料搬运不重复扣除 |
| API-06：停机摘要与 Jade | M4，按容量选入 | 统一原因码、画布说明、核心摘要与 Jade 适配 | 玩家可判断主要停机原因；未安装 Jade 时正常启动 |
| API-07：后续扩展候选 | Beta 后 | EMI、KubeJS、CC:Tweaked、公开 Java API 或发布查询 | 每项先有明确使用场景和目标版本，再单独估时与验收 |

**建议执行顺序：API-01 → API-02 → API-03 → API-04 / API-05 → 按范围选入 API-06。** API-02/03 随 F1 推进，API-04/05 并入原有兼容范围；EMI、脚本控制、公开 API 和联网更新不默认加入原 10–18 周预算。第 5 天与首个 NeoForge 生产闭环完成时，根据实际依赖和接口卡点修订排期。

本次增补完成了官方接口入口与主要版本边界的资料核对；没有编写 API 适配代码、测试第三方 jar 组合或重新认证原报告的 CI 结果。后续实现应把接口建议转化为绑定具体依赖版本的任务、代码和验收证据。
