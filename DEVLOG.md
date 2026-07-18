# DEVLOG · 生电终结者

倒序或正序均可，这里正序。每条：改了什么 / 关键文件 / 待验证点 / configVersion。

## m1 — Phase 0 工程骨架（待编译验证）

搭起 Fabric 1.21.1 Loom 工程与注册框架挂载点：

- build 系统：`build.gradle`（Loom 1.7-SNAPSHOT）/ `settings.gradle` / `gradle.properties`（四个版本号：mc 1.21.1 / yarn 1.21.1+build.3 / loader 0.16.5 / fabric-api 0.105.0+1.21.1，build 失败照 fabricmc.net/develop 调）。
- `fabric.mod.json`（entrypoints main+client）+ `sdzjz.mixins.json`（空占位，package `com.sdzjz.mixin`）。
- `Sdzjz`（主类：`onInitialize` 加载 config + `ModItems.init()`；`id()` 用 `Identifier.of`）。
- `SdzjzClient`（客户端占位）。
- `SdzjzConfig`（GSON + configVersion=1，缺键取默认回写；电力 / 防卡顿 / 基调字段）。
- `ModItems`（注册创造物品组「生电终结者」，图标暂用工作台）。
- 中英 lang；文档 README / DESIGN / HANDOVER / SKILL / DEVLOG。

**待编译验证**：整个 Phase 0 未在沙箱 build 过。重点核 `Identifier.of`、`FabricItemGroup` + `Registries.ITEM_GROUP` 注册链、Loom 版本、四个版本号匹配。
**gradle wrapper 未内置**：本地 `gradle wrapper --gradle-version 8.10` 生成，或 IDEA 导入自动补。
**configVersion**：1（首版）。

## m0 — 设计蓝图

`DESIGN.md`：锁定 控制器方块 + 可连线节点画布 + 电力系统，面板=虚拟节点，基调偏硬核可调，Phase 0→6+ 路线，防卡顿与数据组件持久化原则。

## m2 — 纳入权威设计文档 + 撤电力系统

- 作者上传《完整设计文档.md》（1358 行，数据面板/无限逻辑仓储/生产核心矩阵/喷射输出/原版生电交互/性能与防复制/版本路线），纳入仓库作为**权威设计**；DESIGN.md 顶部加指针。
- 按文档 §7.4「不用传统电力」，从 SdzjzConfig 撤掉上一轮的电力字段（enablePower/energyCapacity/accelEnergyExponent），换成每tick操作预算（core/chunk/network）+ 加速下限周期 + 散热开关 + 喷射实体上限 + 休眠开关。**configVersion 1→2**。
- 未决：ComfyUI 节点画布的落位（编排层 A / 全画布 B），待作者拍板后进 Phase 1 排期。

## m3 — 核心愿景校正 + 节点图数据模型

- 作者澄清核心愿景：**给不会搭生电的人用的 ComfyUI 式节点工厂**（一头输入 → 拖几个模块 → 另一头出材料）。不把「不免费复制/平衡」当硬约束（推翻文档 §12 硬性平衡），默认好上手、可配。**A/B 定为 B**（整个界面就是画布）。文档里矩阵/喷射/无限仓储降级为可选扩展层。DESIGN.md 顶部加「核心愿景」段。
- 落地 Phase 1 地基：`graph` 包 —— `ProductionGraph`（Node + Edge 纯数据模型 + Codec，从头按「节点+边」存，画布与运行时共用）+ `NodeKinds`（MVP 节点类型 input/craft/smelt/output）。
- **待编译验证**：`RecordCodecBuilder` / `Codec.optionalFieldOf` / `listOf` 形状（DFU 标准 API，把握较高）。configVersion 不变（仍 2）。

## m4 — 机器系统草案（结构方块 + 刷线机 + 升级 + 数据化转换器）

- 作者口述具体机制：合成刷线机→塞进结构方块(可多台)→插升级(速度/个数/并发)→开机出料→管道/数据线(+数据化转换器)→箱子/面板。ComfyUI 连线落到**世界里**（方块=节点，管道/数据线=连线，不写红石）。
- 新增 `机器系统.md`（草案）：刷线机/结构方块/数据化转换器 配方提案 + 结构方块槽位 + 升级三类映射(速度=加速/个数=并行份数/并发=同时驱动台数) + 管道vs数据线两条传输。
- 待作者确认三点（刷线机是否通用/配方材料/结构方块面板是否要内嵌节点画布）后进 Phase 1 建方块。纯文档，无代码/配置变更 configVersion 仍 2。

## m5 — 确认刷线机=刷线 + 结构核心 GUI 蓝图（去电力）

- 作者上传《刷线机结构核心》GUI 蓝图并要求「去掉电力」。据此确认：**刷线机 = 固定刷「线」**（击杀蜘蛛刷丝，非空白通用机）；合成配方改用蓝图版（铁锭/观察者/核心模块/红石粉/铜锭/线）。
- 去电力：删生产状态「能量消耗」+ 底栏「能量 FE」，底栏换「结构完整度」。（config 早在 m2 已无电力，一致。）
- 新增 GUI 施工参照 `docs/ui/结构核心界面_无电力.html`（深色科幻，还原蓝图布局：合成/插槽/核心控制/升级(速度·数量·并发)/生产状态/输出与传输/输入·输出缓存/输出模式/状态栏）。
- 重写 `机器系统.md` 落定上述 + 输出模式(自动/喷射/管道优先/数据线优先) + 两条传输。待确认：输入缓存是否必耗 / 核心模块配方 / 插槽数。纯文档+资源，configVersion 仍 2。

## m6 — 消耗原则：对齐原版生电机器

- 作者定核心原则：机器不靠喂料出货，**消耗与否对齐原版对应生电机器**（需要就需要/不需要就不需要）。农场类（刷怪/刷石/作物…）免费出；加工合成类照原版消耗。刷线机=蜘蛛农场→不消耗出线。
- 代码：新增 `machine/MachineDef`（consumesInputs + inputs）+ `machine/Machines`（WIRE_BRUSHER def）。机器系统.md 加「消耗原则」段并结清待确认（核心模块默认配方 铜+红石×4+石英；插槽默认8随规模增）。
- 待编译验证：纯 record/List，无新 API。configVersion 仍 2。

## m7 — 机器/农场完整清单（1.21.1）+ 抓物笼子

- 按作者要求查了 1.21.1 各类生电机器需要什么，整理成 `机器清单.md`：五类（生物掉落/动物产出/采集生长/特殊村民AFK/加工合成），每台列 产物·需求·是否消耗·是否需抓物笼子。
- 抓物笼子（Capture Cage）= 原版刷怪笼/试炼刷怪器的对应物：抓一只怪供应对应「刷X机」，抓到即前置成本、掉落免费出（符合不喂料）。多生物机器如刷铁机=村民+僵尸。
- 已核 1.21 事实：旋风人（试炼刷怪器刷出→旋风棒）、沼泽骷髅（骨/箭/毒箭/剪蘑菇）、铁傀儡农场（3村民+床+工作方块+僵尸恐吓→铁+罂粟）。
- 下一步：把清单逐条落进 machine/Machines.java + 抓物笼子物品与捕获逻辑。纯文档，configVersion 仍 2。

## m8 — Phase 1 核心竖切（结构核心 + 刷线机 + 升级 + 抓物笼子）【待编译验证】

作者「先写、先完善代码、再修 BUG」。落地整条：合成刷线机 → 塞进结构核心 → 开机免费出线 → 推入正下方容器。

新增：
- 物品：core_module / wire_brusher / speed_upgrade / count_upgrade / parallel_upgrade / capture_cage（CaptureCageItem 右键活体捕获，存 CUSTOM_DATA + 改名 + discard）。
- 方块：structure_core(tier1) / super_bench(tier2 更高并发×产量)，StructureCoreBlock(BlockWithEntity, onUse 开 GUI, 服务端 ticker)。
- 方块实体 StructureCoreBlockEntity：8 机器槽+3 升级槽+8 输出槽；tick 免费产线（速度缩周期/数量放大单产/并发提同时台数），产物进缓存并推正下方容器，满则暂停不掉落物；NBT + ExtendedScreenHandlerFactory<BlockPos>。
- GUI：StructureCoreScreenHandler（槽位限制 + quickMove + 开机/停止按钮 onButtonClick）+ 客户端 StructureCoreScreen（纯色面板+按钮）+ 注册。
- 阶梯配方：core_module（铜+红石+石英）→ wire_brusher/三升级/结构核心 → super_bench（结构核心×4+核心模块×4+钻石块）。
- 模型/blockstate/中英 lang 齐（贴图未画=品红占位，art TODO）。

**首次本地编译大概率要盯的点（沙箱编不了，逐个核）**：
1. `StructureCoreBlock.onUse` 覆盖签名——1.21.1 是否为 `onUse(BlockState,World,BlockPos,PlayerEntity,BlockHitResult)`；若报错看是否要用 `onUseWithItem` 或参数不符，按 IDE 提示改。
2. `ExtendedScreenHandlerFactory#getScreenOpeningData(ServerPlayerEntity)` 与 `ExtendedScreenHandlerType(constructor, BlockPos.PACKET_CODEC)` 签名。
3. `new Item(new Item.Settings())` / `AbstractBlock.Settings.copy(...)`（1.21.1 无需 registryKey；1.21.2+ 才要）。
4. 配方 result 用 `{"id":..,"count":..}`、数据包目录 `data/sdzjz/recipe/`（1.21 单数）——已按此写。
5. `NbtComponent`：用 `copyNbt().contains(...)`（已改）。
6. `HandledScreens.register` / `drawBackground(DrawContext,float,int,int)` 客户端签名。

configVersion 仍 2。静态自检：16 Java 括号全平、24 JSON 合法。

## m9 — 数据面板（数字化仓储终端）【待编译验证】

- 新增数据面板：DataPanelBlock + DataPanelBlockEntity（逻辑仓储 id→long，近乎无限；服务端每 tick 从 store 前 54 种刷新 54 格展示；deposit/withdraw；NBT 存 store 列表）。
- GUI：DataPanelScreenHandler（6×9 展示格只取不放、取出即扣 store；玩家背包 shift 存入面板）+ 客户端 DataPanelScreen；已注册 BE/方块/物品/ScreenHandler/Screen，创造组加入。
- 结构核心 pushDown 改：下方是数据面板则直接 deposit（否则走原 Inventory 逻辑）。→ 核心放数据面板上即自动入库。
- 配方：玻璃×4+青金×2+末影珍珠×2+箱子+核心模块 → 数据面板。模型/blockstate/中英 lang 齐（贴图占位）。
- 之前 m8 成功编译（仅 getCodec 一错已修）；ModBlockEntities 的 deprecation 只是警告，暂留。
- Phase 2 简化/待办：展示不翻页（类型>54 暂不显示多的）；取物的 partial/右键细节按需再调。configVersion 仍 2。

## m10 — 结构核心通用化 + 一批机器【待编译验证】

- 结构核心 tick 从"只认刷线机、只出线"改为**按 MachineDef 跑任意机器**：机器做成 MachineItem（携带 def），tick 按类型分组，各自按 def 的产物/周期/单产运行，共享 速度/数量/并发 升级；改用单调计数 ticks + 每 def interval 取模，支持不同机器不同周期。
- 机器槽 canInsert 泛化为接受任意 MachineItem。
- 新增 7 台农场类机器（consumesInputs=false 免费出）：刷石机(cobblestone,10t)/刷骨机(bone)/刷火药机(gunpowder,25t)/刷腐肉机(rotten_flesh)/刷珍珠机(ender_pearl,30t)/刷史莱姆机(slime_ball)/刷铁机(iron_ingot,40t)。配方统一模板（铁+观察者+核心模块+红石+铜 + 1个目标产物引子），物品模型+中英名齐。
- 加工/合成类（consumesInputs=true）仍跳过，下一步做。修了一次自己引入的括号错（误删 countUpgrade 签名）。configVersion 仍 2。

## m11 — 结构核心 GUI 通用状态化 + 再加 6 台机器 + 路线图【待编译验证】

- GUI 通用化以"兼容所有机器"：StructureCoreBlockEntity 加 PropertyDelegate(运行/机器数/tier/速度·数量·并发Lv)；ScreenHandler 走 addProperties + 状态 getter；客户端 Screen drawForeground 显示 运行状态/tier名/机器数/升级等级。机器槽本就接受任意 MachineItem。
- 再加 6 台单产农场：树场/甘蔗/竹子/刷沙/刷冰/黑曜石（共 14 台）。
- 新增 机器路线图.md：把作者 1.21.1 生电大全分 6 桶（A单产已支持 / B多掉落需MachineDef加权 / C消耗类走consumesInputs / D抓物笼子供生物 / E规模多方块 / F红石工程装置超范围）。
- configVersion 仍 2。

## m12 — 多掉落系统 + 3 台多掉落机器【待编译验证】

- MachineDef 升级：product 单产 → outputs=List<Drop>(item,min,max,chance)，保留 Input。Machines 单产走 def() 包成单条 Drop，新增 defMulti()。BE tick 遍历 outputs 按概率+数量区间产出（数量升级仍 +8/条，并发/tier 生效），用 world.getRandom()。
- 新增 3 台多掉落：沼泽刷怪塔(线/火药/骨/箭/腐肉/蜘蛛眼/粘液)、女巫塔(红石/发光粉/糖/瓶/火药/棍/蜘蛛眼)、守卫者农场(海晶碎片/晶体/鳕鱼)。共 17 台机器。
- 新增 TEXTURES.md：25 张贴图清单(22 物品+3 方块，16×16 透明)给作者用 GPT 做，放对目录即可无需改代码。
- configVersion 仍 2。

## m16 — 结构核心界面改全屏仪表盘【待编译验证】

- 之前是原版小窗(176×186)。按作者要求改**全屏**：StructureCoreScreen 用 360×256 大面板，drawBackground 先用 BACKDROP 铺满整个窗口(盖住世界=全屏感)，再画中央面板 + 五个分区(机器/升级/产出/状态/背包)底板与槽底。
- 槽位在 handler 里重排成仪表盘：机器 2×4、升级 1×3、产出 2×4、背包底部居中；开机/停止改成两个大按钮。
- drawForeground 显示 tier名/运行状态/各区标题/状态读数(机器数·速度·数量·并发 Lv)。
- 纯客户端 + 槽坐标改动，无服务端逻辑变化。数据面板 GUI 暂仍为小窗，需要的话下次也可全屏化。configVersion 仍 2。

## m17 — 结构核心 GUI 用上背景贴图【待编译验证】

- 作者提供了 structure_core_gui.png（科幻边框+电路，空心中间）。缩到 360×256 存 assets/sdzjz/textures/gui/。
- StructureCoreScreen.drawBackground：全窗 BACKDROP 铺底后，用 drawTexture 画该贴图当面板（1:1），去掉原纯色面板与分区底板；槽底/文字/按钮仍代码画在上层保证对齐。
- 盯点：drawTexture 签名（1.21.1 用 `drawTexture(Identifier,int x,int y,float u,float v,int w,int h,int texW,int texH)`）——若报错按 IDE 提示换重载。

## m18 — GUI 科技化【待编译验证】

- 槽格从扁平深色改成科技风：深色内凹 + 青色四角。
- 开机/停止按钮从原版灰按钮换成自绘 SciButton（深底青边，悬停发光变亮），nested ButtonWidget 覆盖 renderWidget。
- 分区标题前加青色竖条。并发数值用青色高亮。
- 盯点：ButtonWidget 7 参构造(narrationSupplier 传 lambda s->s.get())、renderWidget/isHovered/drawCenteredTextWithShadow 1.21.1 签名。
- 说明：升级等级 = 升级槽内该升级物品数量(叠加)；速度-4t/级、数量+8/次/级、并发+4/级。

## m19 — 树场多掉落 + 数据线连接第一版【待编译验证】

- 树场改多掉落：原木1-2、木棍0-2、苹果15%、树苗40%。
- 连接系统第一版：新增 DataCableBlock（数据线）。StructureCoreBlockEntity 的 pushDown 改为 pushOutput+findTarget：从核心 BFS，相邻数据面板/箱子直接存；遇数据线则顺着路由到末端存储（上限256格，无电力）。→ 可"核心拉线到远处面板"。
- 数据线配方(玻璃8+红石+核心模块→8)、方块模型/blockstate/物品模型/lang齐；贴图 data_cable.png 待画(已加进绘图名单)。
- 新增 连接系统.md：现状 + 参考 AE 的后续路线(多目标分发/共享网络存储/远程终端/细导线模型/输入路由)。
- 消耗类机器(熔炉组/合成机/交易)仍待做。configVersion 仍 2。

## m20 — 消耗类机器第一版 + 存储分期设计【待编译验证】

- MachineDef 消耗路径落地：结构核心 tick 对 consumesInputs=true 的机器，findPanel 找到连接的数据面板→校验并扣除 inputs(×running)→按 outputs 产出。农场类逻辑不变(合并进同一循环)。
- 新增 defConsume/in 辅助 + 首台消耗机 猪灵交易塔(piglin_barter，吃金锭1→末影珍珠/线/石英/发光粉/黑曜石/灵魂沙/岩浆膏/皮革 各带概率)。物品/配方/模型/中英名齐，装入 猪灵交易塔.png(素材已清空，仅剩说明)。
- DataPanelBlockEntity 加 count(id)。BE 加 findPanel(BFS)。
- 连接系统.md 补作者存储分期：前期连线→中期无线WiFi→后期卫星；数据面板绑定多核心；手持终端远程开面板；全程无电力。
- configVersion 仍 2。

## m21 — 一键建造结构（建造蓝图）第一版【待编译验证】

- 作者用 ObjToSchematic 导出 .mcfunction（38021 行 setblock，相对~坐标、无方块状态、纯原版方块）。放入 src/main/resources/structures/block_mesh.mcfunction。
- 新增 StructureBlueprintItem：右键地面 → 解析(缓存) mcfunction → 在点击点上方(pos.up())按相对坐标一次性 setBlockState(NOTIFY_LISTENERS) 摆出整个结构 → 非创造消耗 1 蓝图。
- 注册 structure_blueprint 物品(配方 纸7+钻石块+核心模块)、模型、中英名、创造组。贴图待画(已加绘图名单)。
- 盯点/已知：(1) 作者用 1.21.7 图集，个别 1.21.7 新方块在 1.21.1 不存在→get 返回 AIR 会留空洞(多数常见方块没问题)；(2) 3.8万方块一次性放置可能瞬时卡顿，后续可分tick;(3) 材料清单校验暂无(先消耗蓝图)，为后续。
- configVersion 仍 2。

## m22 — 扩充 10 台机器【待编译验证】

- 新农场(免费,consumesInputs=false)：仙人掌场、下界疣场、海带场、烈焰人塔、凋灵骷髅塔(骨头/煤/2.5%凋灵骷髅头)、蜂蜜场(蜂巢/蜂蜜瓶)。
- 新消耗机(从数据面板取料)：铁熔炉(粗铁→铁锭)、金熔炉(粗金→金锭)、木炭窑(橡木原木→木炭)、玻璃窑(沙子→玻璃)。
- 脚本生成：Machines 常量、ModItems 注册+创造组、配方(统一 IOI/RMR/CSC 模板, S=对应引子)、物品模型、中英名、128×128 品红占位贴图；绘图名单追加 10 项。
- 机器物品总数 22→32。configVersion 仍 2。
- 贴图待画(占位品红)：cactus_farm/nether_wart_farm/kelp_farm/blaze_farm/wither_skeleton_farm/honey_farm/iron_smelter/gold_smelter/charcoal_kiln/glass_kiln。

## m23 — 中期无线(WiFi)连接【待编译验证】

- 新增 WirelessNodeBlock「无线节点」。接在核心相邻或其数据线网络上即启用无线。
- 数据面板加静态"位置登记表"(Map<RegistryKey<World>,Set<BlockPos>>)：tick 登记、markRemoved 注销。
- StructureCoreBlockEntity 加 hasWirelessNode(BFS 找网络上的无线节点) + nearestWirelessPanel(登记表内同维度、range内最近面板)。产出 pushOutput 与消耗机取料均"有线优先，无线兜底"。
- 配置加 wirelessRange=48，configVersion 2→3。
- 无线节点：配方(铜4+末影珍珠4+核心模块)、方块/物品模型、blockstate、中英名、创造组、64×64占位贴图；绘图名单+wireless_node。
- 连接系统.md 记录无线已实现；后续卫星/多核心绑定/手持终端。

## m24 — 后期卫星连接（跨维度/全局）【待编译验证】

- 新增 SatelliteNodeBlock「卫星节点」。接在核心相邻或数据线网络上启用。
- StructureCoreBlockEntity 加 hasSatelliteNode(BFS) + findSatellitePanel(本维度最近无上限→否则遍历 dimensionsWithPanels 其它已加载维度取任一面板, 跨维度 getWorld)。
- 路由链完整：有线 → 无线(48格) → 卫星(全局)，产出与消耗机取料一致。
- DataPanel 暴露 dimensionsWithPanels()。
- 卫星节点：配方(无线节点×3+末影之眼×4+下界之星+核心模块)、模型/blockstate/物品模型、中英名、创造组、占位贴图；绘图名单+satellite_node。
- 连接三期完成：连线✅ 无线✅ 卫星✅。configVersion 仍 3。

## m25 — 数据面板绑定多核心 / 指定目标面板【待编译验证】

- 新增 LinkerItem「数据链接器」：右键面板记录目标(pos+dim→CUSTOM_DATA)，右键核心 setBound 绑定；潜行右键核心解绑。
- StructureCoreBlockEntity：boundPanelPos/boundPanelDim 字段 + NBT 读写；boundPanel() 解析可达绑定目标(同维度=无线&范围内/卫星/wiredReaches；跨维度=卫星)；wiredReaches() 目标定向BFS。
- 路由优先级：绑定 → 有线 → 无线 → 卫星（产出与消耗机取料一致）。多核心绑同一面板即聚合。
- 链接器：配方(红石4+末影珍珠4+核心模块)、模型、中英名、占位贴图、创造组；绘图名单+linker。
- configVersion 仍 3。

## m26 — 手持终端（远程开面板）【待编译验证】

- 新增 TerminalItem「手持终端」：useOnBlock 右键面板→绑定(pos+dim→CUSTOM_DATA)；use() 右键空手/地面→远程 openHandledScreen(真实面板BE)。
- 利用现有 DataPanelScreenHandler：服务端持真实 BE，槽位内容同步下发，客户端无需该方块实体→跨维度/远处可显示；取出经服务端面板扣数。
- 限制：目标面板区块须已加载。盯点：Item.use 在 1.21.1 返回 TypedActionResult<ItemStack>（若签名不符按报错改）。
- 终端：配方(玻璃板3+末影珍珠2+核心模块+铁锭3)、模型、中英名、占位贴图、创造组；绘图名单+terminal。
- configVersion 仍 3。

## m27 — 抓物笼子驱动机器【待编译验证】

- 新增 MobDrops：笼中生物 id→掉落表（僵尸/骷髅/苦力怕/蜘蛛/牛猪鸡羊兔/末影人/烈焰人/史莱姆/女巫/守卫者/鱿鱼/凋灵骷髅/猪灵/恶魂/潜影贝/铁傀儡/蜜蜂/monster 等 30+ 种）。
- 结构核心 tick：机器槽里的抓物笼子(isCaged)按 cagedType 分组，用 MobDrops 表产出，自由产出、30t 基础周期，同吃速度/数量/并发/tier；未收录生物不产。
- 机器槽 canInsert 放开：允许 CaptureCageItem；机器数(machineCount)计入笼子。
- 抓物笼子↔机器(路线图 D)打通。configVersion 仍 3。

## m28 — 结构建造：分tick摆放 + 材料清单【待编译验证】

- 新增 com.sdzjz.structure.StructureBuilder：plan()解析缓存、tally()材料统计、enqueue()入队、tick()每服务端tick按 config.structureBlocksPerTick(默认1024)分批 setBlockState。
- StructureBlueprintItem 重写：改为入队分批建(不再一次性摆)，消掉卡顿；config.structureConsumeMaterials=true 时先 tryConsume 扣背包(够料才建)。
- Sdzjz.onInitialize 注册 ServerTickEvents.END_SERVER_TICK -> StructureBuilder.tick。
- 配置加 structureBlocksPerTick=1024 / structureConsumeMaterials=false（加键不升版本，GSON 取默认）。
- 盯点：ServerTickEvents(fabric lifecycle v1) 依赖存在即可。

## m29 — 撤销蓝图 + 数据面板 GUI 全屏科技化【待编译验证】

- 撤销结构建造(暂停，作者拟 Blockbench 重做)：删 StructureBlueprintItem/StructureBuilder/block_mesh.mcfunction(1.8M) 及其配方/模型/lang/创造组/Sdzjz tick 挂载。无残留引用。代码保留在 git 历史(m21~m28)可恢复。
- 数据面板 GUI 改全屏科技风(对齐结构核心)：DataPanelScreen backgroundWidth/Height=360×256，深色 BACKDROP 铺满，复用 structure_core_gui.png 作边框贴图，青角槽格 cell()，分区标题"存储/背包"带青竖条，标题"数据面板"+右上"种类 N/54"。
- DataPanelScreenHandler 槽位改居中布局：存储6×9 @ (99+c*18,30+r*18)，背包3×9 @ (…,158+…)，快捷栏 @ (…,216)。screen 绘制坐标同步。
- 后续作者可出 data_panel_gui.png 专属背景再替换 BG 常量。configVersion 仍 3。

## m30 — 修复：终端绑定/面板大数显示/猪人塔掉落【待编译验证】

- 终端绑定失败：DataPanelBlock.onUse 在手持 TerminalItem/LinkerItem 时返回 PASS，让物品 useOnBlock 去绑定(不再被方块开界面拦截)。右键面板=绑定，空手右键面板=开界面。
- 面板计数只到 ~9999/被64截断：存储本为 long 无封顶；refreshDisplay 给展示物品附带真实总量组件("amt":long)；DataPanelScreen.drawSlot 自绘该总量(K/M/B/T)，隐藏原版≤64计数。→ 显示与存储突破 9999 与 int(2147483647)，上限为 long(~9.2e18)。
- 猪人塔掉落调准僵尸猪人：金粒1-3、腐肉0-1、金锭5%、金剑5%(原先金锭15%偏高)。
- 说明：图2面板里的绿宝石/箭/不死图腾来自袭击塔，金粒/腐肉/金锭来自猪人塔，两台共用面板，非bug。
- 超大工作台界面：当前=二档结构核心(同仪表盘,更高并发/tier)，见回复中的说明与提问。

## m31 — 关键修复合成 + 新增7机器【待编译验证】

- 【关键】补 src/main/resources/pack.mcmeta（pack_format 48, 1.21.1 数据包）。此前缺失导致数据包(配方)整包不加载→所有配方无效(玩家只能创造栏取)。补上后全部可合成。
- 新增机器：铁轨机、地毯机、普通刷怪塔(骨/火药/腐肉/线/箭)、下界树场(绯红/诡异茎+疣块+菌光)、紫颂果农场、溺尸塔(腐肉/铜/鹦鹉螺壳/三叉戟)、超级熔炉组(消耗,interval2秒烧粗铁→铁锭)。仙人掌场m22已有。
- 机器总数 32→39。配方统一模板；占位品红贴图；绘图名单+7项。
- 待办(单独排)：超大工作台=专用合成台(自定义3×3合成机器/塔)；村民繁殖机+交易所+打折机(独立UI)。
- 结构核心背包：handler 本含玩家背包槽(x99 y176/232)，screen 已画格；需作者确认"不显示背包"具体所指。

## m32 — 结构核心去玩家背包 + 右键放入/弹出【待编译验证】

- StructureCoreScreenHandler 移除玩家背包槽(3×9+快捷栏)；quickMove 停用(无处可去,返回EMPTY)。
- StructureCoreScreen 去掉背包格与"背包"标题。
- StructureCoreBlock.onUse 重写：手持机器/笼子→insertMachine；手持升级→insertUpgrade；其它物品→PASS(可正常放置);空手→开面板;潜行空手→ejectOne 弹出一台。
- StructureCoreBlockEntity 加 insertMachine/insertUpgrade/insertInto/ejectOne/pop。
- StructureCoreBlock 加 onStateReplaced：破坏时 ItemScatterer 掉落全部内容(防丢机器)。
- 未做(待定):超大工作台=专用合成台需 128×128 含义澄清后建;届时把机器配方从原版工作台迁到合成台(在此之前保留原版配方,避免再次无法合成)。

## m33 — 结构核心 ComfyUI 画布 Phase1【待编译验证】

- 机器从"8固定槽"改为无上限「节点列表」(machineNodes: List<ItemStack>)。tick/机器数/放入/弹出/破坏掉落均改走节点。升级仍走 items 槽8-10(右键放入)，产出仍 items 槽11-18(自动推送)。
- BE：insertMachine 加节点、ejectOne 先弹节点、nodes() 供画布读、dropAll 破坏掉落(含节点)、syncToClient + toUpdatePacket + toInitialChunkDataNbt(节点存 NBT 并同步客户端)。
- StructureCoreScreenHandler：去掉全部 GUI 槽位(画布无槽)，加 blockPos() 供画布定位客户端 BE；保留状态 props + 开机/停止按钮。
- StructureCoreScreen 重写为画布：全屏深底+网格、节点框(图标+名+×数)、拖空白平移、顶栏状态、开机/停止、操作提示。
- 盯点(1.21.1 API)：toUpdatePacket/BlockEntityUpdateS2CPacket、toInitialChunkDataNbt、ItemStack.encode/fromNbt、mouseDragged 签名。
- 后续：Phase2 节点拖动+位置保存(C2S)；Phase3 连线；Phase4 缩放。超大工作台 12×12 合成台单独做。

## m34 — 修编译：ItemScatterer 包名【已查API修正】
- 报错：net.minecraft.block.ItemScatterer 找不到。Yarn 1.21 API 确认其位于 net.minecraft.util.ItemScatterer。
- StructureCoreBlockEntity import 改 net.minecraft.util.ItemScatterer；StructureCoreBlock 不再直接用它，删除该 import。
- dropAll 两 spawn 重载确认存在：spawn(World,BlockPos,Inventory) / spawn(World,double,double,double,ItemStack)。

## m35 — 画布 Phase2：节点拖动 + 位置保存【待编译验证】
- 新增 com/sdzjz/net/NodeMovePayload：C2S 自定义包(BlockPos+index+nx+ny)，CustomPayload + PacketCodec.tuple(BlockPos.PACKET_CODEC/PacketCodecs.INTEGER)。查证 fabric-api 0.115.6+1.21.1 网络 API。
- Sdzjz.onInitialize：PayloadTypeRegistry.playC2S().register(通用init=双端注册) + ServerPlayNetworking.registerGlobalReceiver → 服务端 core.setNodePos。
- BE：节点位置存进各自 ItemStack 的 CUSTOM_DATA(nx,ny)；insertMachine 首次自动网格布局；nodeX/nodeY 读取、setNodePos 写入(服务端同步/客户端本地视觉)。
- StructureCoreScreen：按节点保存坐标渲染；mouseClicked 命中节点开始拖动、mouseDragged 拖节点(本地即时)或平移、mouseReleased 发 NodeMovePayload 持久化。
- 盯点：PacketCodec.tuple 四字段重载、playC2S 双端注册。
- 后续：Phase3 连线；Phase4 缩放。超大工作台 12×12 合成台。

## m36 — 画布 Phase3：节点连线（数据+渲染+拖拽连接）【待编译验证】
- BE：connections List<int[]>{from,to} + connections()/toggleConnection(存在则断) + ejectOne 移除末节点时剪枝相关连线 + NBT(putIntArray/getIntArray 扁平存储, 老存档空数组安全)。
- 新增 NodeLinkPayload(pos,from,to) C2S；Sdzjz 注册 + 接收器 → core.toggleConnection。
- 画布：节点左青(输入)/右绿(输出)端口；三次贝塞尔连线(56采样点, 水平切线 ComfyUI 风格)画在节点下层；按住绿输出口拖到另一节点=连/断(临时连线跟随鼠标)。
- 连线运行效果(A输出→B输入)未接，为后续。tuple 3/4 字段重载已由 Phase2 全绿佐证。
- 后续：Phase4 缩放；连线运行语义；超大工作台 12×12。

## m37 — 每节点独立升级 + 节点升级格【待编译验证】
- 升级从"全局3槽"改为"每台机器节点各自持有"，存进节点 ItemStack CUSTOM_DATA(spd/cnt/par)。
- tick 重写：不再按类型分组用全局升级；改为逐节点独立运行，各读各的 speed/count/parallel(周期用 be.ticks % 各自interval, 无需存每节点计时器)。
- BE：nodeSpeed/nodeCount/nodePar 读取；addNodeUpgrade(从玩家背包扣一个对应升级并+1)/removeNodeUpgrade(‑1并还给玩家)；consumeFromInv；totalNodeUpgrade(状态栏总数)。propertyDelegate 3/4/5 改报总数。
- 新增 NodeUpgradePayload(pos,index,type,add) C2S(PacketCodecs.BOOLEAN)；Sdzjz 注册+接收器→add/removeNodeUpgrade。
- 画布：每节点下方 3 个升级格(加速/数量/并列, 图标+等级)；左键加(扣背包)/右键取(还背包)；自动布局纵距 66→88 给格子留位。
- 方块 onUse 去掉全局升级放入分支(升级只走画布节点格)；countUpgrade/insertUpgrade 变未用(留着无害)。
- 后续：Phase4 缩放；连线运行语义；超大工作台 12×12。

## m38 — 修编译：PacketCodecs.BOOL【已查API】
- 报错 PacketCodecs.BOOLEAN 找不到。Yarn 1.21 API 确认布尔字段名为 PacketCodecs.BOOL(PacketCodec<ByteBuf,Boolean>)。NodeUpgradePayload 改用 BOOL。

## m39 — Phase4 画布缩放 + 连线运行语义【待编译验证】
- 连线语义(BE)：内部缓存 internalBuffer(id→long)。tick 索引循环 + 连线拓扑 hasOut/hasIn(粗粒度)：有出线节点产物入内部缓存(bufAdd)、有入线消耗机从内部缓存取料(bufCount/bufWithdraw)；无连线者维持原存储行为。缓存封顶 BUF_CAP=20万，溢出回存储。NBT 存内部缓存。
- 缩放(画布)：MatrixStack push/translate(pan)/scale(zoom) 包住 连线+节点+升级格；网格仍屏幕空间随平移。滚轮缩放(0.4~2.5)对准光标。所有命中测试(格子/端口/节点体/拖动/连线目标)先把鼠标反变换到世界坐标 wmx/wmy；节点坐标改世界坐标 wnx/wny。顶栏显示缩放倍数。
- 盯点：MatrixStack.translate/scale、mouseScrolled(4参)、drawItem 在缩放矩阵内(若不随缩放则大小固定但位置对)。
- 连线语义为粗粒度(所有出线者共享一个内部池给所有入线消耗机)，精确按边路由为后续。
- 下一轮：超大工作台 12×12 合成台(独立大件)。

## m40 — 绘图名单更新 + 超大工作台 12×12 合成台【待编译验证】
- 绘图名单.md：移除全部已完成条目(几乎全就位)，仅剩 data_cable.png 待画；structure_blueprint 移除(功能搁置)。
- 超大工作台从"tier2 结构核心"改为独立合成站：
  - SuperBenchBlock(仿原版工作台, 无BE, onUse→SimpleNamedScreenHandlerFactory 开界面)。
  - SuperBenchScreenHandler：12×12 输入网格 + 结果槽 + 玩家背包；无形状(多重集精确)匹配；结果取出时消耗对应材料；关界面掉落网格；quickMove 网格↔背包。
  - SuperBenchRecipes：由 39 个机器配方 JSON 自动生成的多重集配方表(位置随意, 需精确材料)。
  - SuperBenchScreen：深色科技风 12×12 界面(284×316)。
  - 注册：ModBlocks 改 SuperBenchBlock；ModBlockEntities 从 STRUCTURE_CORE_BE 移除 super_bench；ModScreenHandlers 加 SUPER_BENCH(ScreenHandlerType+FeatureFlags.VANILLA_FEATURES)；SdzjzClient 注册界面。
  - 删除 39 个机器原版合成配方 → 机器现只能在超大工作台合成；基础物品(核心模块/升级/笼子/线/节点/面板/核心/工作台/linker/terminal)仍原版可合成。
- 盯点：FeatureFlags.VANILLA_FEATURES、ScreenHandlerType(Factory,FeatureSet)、SimpleNamedScreenHandlerFactory、CraftingResultInventory、Block.onUse 签名。
- 注：super_bench 不再是 tier2 核心，画布 tier 恒为1(tier2 分支变死代码, 无害)。

## m41 — 超大工作台配方浏览器（点击自动填料）【待编译验证】
- 问题：删了原版机器配方后，玩家在 12×12 台前不知道放什么料、也无配方书。
- SuperBenchScreen 右侧加配方浏览器：列出全部 39 台机器(图标+名, 滚轮翻页)；点一台=选中并显示所需材料(图标+×数)，同时 clickButton(配方下标) 让服务端自动从背包填料入网格。
- SuperBenchScreenHandler.onButtonClick(id)：先清空网格还给玩家，再按 #id 配方从背包 takeFromInv 取料填入网格→markDirty 重算结果。背包不足则部分填入。
- 界面加宽到 470×316，左网格/结果/背包 + 右浏览器，中间分隔线。
- 盯点：interactionManager.clickButton、ScreenHandler.onButtonClick。

## m42 — 界面被挡修复 + 画布视角记忆 + 数据面板存储终端 + 绘图名单修正【待编译验证】
- A 界面被挡：结构核心画布 开机/停止 按钮从右上(被创造栏盖住)移到左下角。
- B 画布视角记忆：StructureCoreScreen 静态 Map<BlockPos,double[]> 记 pan/zoom；init 读回、removed 存(本次游戏内重开保持;跨重启后续再做)。
- D 数据面板→存储终端(仿 Tom's Simple Storage)：
  - BE 加 searchFilter/scrollRow；refreshDisplay 改为按 id 子串过滤 + 滚动窗口(每页54)；setView(search,scroll)。
  - DataPanelViewPayload(pos,search,scrollRow) C2S(PacketCodecs.STRING)；Sdzjz 注册+接收器→panel.setView。
  - DataPanelScreenHandler 加 blockPos()。
  - DataPanelScreen 重写：顶部 TextFieldWidget 搜索(改词→scroll归零+发包)、滚轮翻页(底行满才下翻)、滚动条轨、大数量自绘、keyPressed/charTyped 让搜索框优先。
- 绘图名单修正：之前误把洋红占位当完成删了。实测纯洋红=占位=待画，共 22 张(物品19+方块3含 data_cable 无文件)。
- 盯点：TextFieldWidget 构造/setPlaceholder/setChangedListener、PacketCodecs.STRING。
- 排下轮(大件)：C 机器配方铺满12×12指定位置(重做39个shaped大配方)；E 核心连数据面板→画布显示可连线的数据面板节点。

## m43 — 画布右键取出节点 + 存储核心(可升级) + 数据面板改纯终端【待编译验证】
- ① 画布右键取出：NodeRemovePayload(pos,index) C2S；BE.removeNodeAt(返还玩家+连线重索引)；StructureCoreScreen 右键节点体发包。
- ③ 存储核心 StorageCoreBlock/BE：逻辑仓储(id→long)，类型上限=27×tier；右键存储升级=tier++、空手右键=显示用量；CORES 注册表 + connectedCores(数据线/相邻 BFS)。新增 storage_upgrade 物品。注册 ModBlocks/ModBlockEntities/ModItems + 创造组 + 模型/状态/配方/占位贴图。
- ④ 数据面板改纯终端：DataPanelBlockEntity 去掉自带 store，count/deposit/withdraw/refreshDisplay 全部代理到 connectedCores 聚合。
- 机器路由重定向：StructureCoreBlockEntity 全部 DataPanelBlockEntity→StorageCoreBlockEntity，panelsIn→coresIn，dimensionsWithPanels→dimensionsWithCores。LinkerItem 绑定改存储核心。
- 新架构：机器产出→存储核心(经绑定/数据线/无线/卫星)；数据面板终端→聚合显示相连存储核心；升级存储核心提升类型上限。
- 绘图名单+2：storage_core、storage_upgrade(占位)。
- 盯点：StorageCoreBlock createCodec/validateTicker(仿结构核心)、路由重定向。
- 排下轮：② 机器 12×12 大配方铺满好看摆法。

## m46 — 机器 12×12 大配方（蓝图布局）
- SuperBenchRecipes 重做：每台机器一张固定 12×12 蓝图布局，共享模板（边框28铁/内框16铜/角螺栓4侦测器/玻璃窗16/红石线8/核心节点4核心模块）+ 中央 2×2 标志物（每台 4 格），共约 80 件/台，铺满约 80/144 格。
- 匹配仍走多重集（位置无关，手动摆料友好）；自动填充改为按 layout 指定位置逐格铺（批量取料→按格摆放，缺料留空）。
- 标志物设计为每台唯一 → 顺手修掉旧的多重集撞车（bone_farm/mob_tower/wither_skeleton_farm 三撞、super_smelter=iron_smelter、swamp_spawner=flesh_farm）。现在 39 台各自可合成、互不冲突。
- Recipe 记录改为 (result, layout[144], ingredients)；访问器名不变，其它调用点无需改。
- 待编译验证（沙箱编不了 Fabric，静态自检括号/引用已过）。

## m47 — BUG 排查修复（m33~m46 大改动全面审查）
本轮静态审查发现并修复 7 个雷（按严重度）：
1. **物品凭空丢失（严重）**：数据面板 quickMove 先扣仓储再塞背包——背包满/塞一半时已扣的东西直接消失。改为先试塞干净副本、按实际塞入量扣。
2. **强制加载区块（性能地雷）**：无线/卫星/绑定路由遍历登记表时直接 `world.getBlockEntity(p)`，服务端会强制同步加载区块。新增 `StorageCoreBlockEntity.loadedCoreAt()`（先 `getChunkManager().isChunkLoaded()`，未加载直接跳过；已加载但无核心=幽灵坐标顺手剔除），5 处路由全部改用；遍历一律走 `List.copyOf` 防 CME。
3. **跨存档幽灵坐标**：静态 CORES 登记表从不清空，换存档后残留旧坐标。注册 `ServerLifecycleEvents.SERVER_STOPPED` 清空（API 已查证 fabric lifecycle v1）。
4. **伪造包作弊向量**：5 个 C2S 接收器无任何校验，任何玩家可对任意坐标的核心发拖动/连线/升级/移除包。加"当前打开的界面必须对应该坐标"校验（viewingCore/viewingPanel 查 currentScreenHandler），面板走界面校验而非距离——不破坏手持终端远程操作。
5. **取出物不可堆叠**：面板展示格取出的物品带着显示大数用的 CUSTOM_DATA("amt")，与普通同类物品叠不了堆。onTakeItem 剥掉组件；quickMove 用干净副本。
6. **面板每 tick BFS（卡顿机器）**：数据面板每 tick refreshDisplay→BFS(4096) 聚合。节流至每 10 tick；setView 交互仍即时刷新。
7. **弹出节点升级丢失/不可堆叠**：右键取出/潜行弹出/破坏掉落的机器带着 nx/ny/spd/cnt/par NBT——叠不了堆、内嵌升级隐形。改为：内嵌升级折成升级物品归还，机器本体剥 NBT 干净返还（removeNodeAt/ejectOne/dropAll 三路统一）。
- 待编译验证。盯点：`ChunkManager.isChunkLoaded(int,int)`（Yarn 文档确认存在）、`ServerLifecycleEvents.SERVER_STOPPED`、`ItemStack.remove(DataComponentTypes.CUSTOM_DATA)`。

## m48 — 路由缓存优化 + 5 台新机器 + 优化/缺口盘点
- **路由缓存**：pushOutput 与消耗机取料原每个生产周期跑 4 连 BFS（绑定判定内含 BFS + findTarget + 无线判定 + 卫星判定）；改为目标坐标缓存 40 tick（resolveOutTarget/resolveInputSource），命中时仅 1 次已加载区块的 getBlockEntity。缓存失效条件：过期/区块未加载/目标不再是有效类型。仅缓存同维度目标。
- **新机器 ×5**（现共 44 台）：恶魂塔（火药/恶魂泪）、旋风人塔（1.21 旋风棒/风弹）、骨粉机、苔藓机、切石机（消耗：石头→石砖）。注册/创造组/模型/lang/大配方齐；贴图洋红占位（绘图名单 +5）。
- **《优化与缺口.md》**：完整盘点待优化项（同步瘦身/画布裁剪/中文搜索/tooltip/REI/按边路由/老存档迁移提示）与未做生电内容（村民系统/酿造/自动合成机/Warden/经验系统），并给出下一步顺序。
- 待编译验证。

## m49 — 村民系统 v1（点名大件：独立 UI）
- **村民繁殖机**（机器，插结构核心）：消耗 3 面包 → 产 1 村民合同（走存储网络取料/出货，周期 60t）。
- **村民合同**（物品）：CUSTOM_DATA 存 prof（职业）/disc（折扣 0..5）。无职业合同由繁殖机产出。
- **村民交易所**（独立方块 + 独立全屏 UI）：
  - 合同槽放入合同 → 无职业时显示 7 个职业就业按钮（就业消耗存储网络里 1 个对应工作方块：堆肥桶/讲台/制图台/锻造台/酿造台/烟熏炉/木桶）；
  - 有职业显示交易列表（7 职业 × 4 条，取材原版），点击执行：输入按折扣从相连存储核心扣、产出存回；存储收不下时还给玩家不凭空消失；
  - 治愈按钮：消耗网络 1 金苹果，折扣 +1（最高 5 级，每级输入 -10%、至少 1）。
- 按钮协议走 ScreenHandler.onButtonClick（0..6 就业 / 10+ 交易 / 40 治愈），零新增网络包。
- 破坏掉落用项目已验证的 onStateReplaced 写法。交易所原版合成：绿宝石×4+核心模块+铁×4（无序）。繁殖机大配方标志物 bread×3+emerald（45 台零撞车）。
- 占位贴图 3 张待画：villager_contract / villager_breeder（item）、trade_center（block）。
- 待编译验证。盯点：TradeCenterScreen 的 drawBorder/drawItem/clickButton（均为项目内已验证 API 同款用法）。

## m50 — 中文搜索 + 机器 tooltip + GUI 设计稿
- **数据面板中文搜索**：客户端按本地化显示名匹配全物品注册表→id 列表（上限 200）随 DataPanelViewPayload 发服务端（codec：STRING.collect(PacketCodecs.toList())，Yarn 文档确认）；服务端过滤 = id 包含 或 命中匹配列表。搜"铁"能出铁锭了。
- **机器 tooltip**：MachineItem.appendTooltip（签名照 Yarn 1.21 Item 文档）显示周期秒数、消耗（红）/免费（绿）、产出列表（区间+概率，青）。44 台机器悬停即懂。
- **GUI 设计稿**：docs/gui/ 4 张线框（数据面板/交易所/超大工作台/画布边框，标注真实代码坐标）+ GUI素材指南.md（提示词+落位规则），供 GPT 生成专属背景。
- 待编译验证。盯点：collect(toList) 泛型推断、appendTooltip 覆盖签名。

## m51 — 4张GUI背景落位（用户GPT生成）
- `textures/gui/` 新增 4 张：`data_panel_gui` / `trade_center_gui`（比例正好，直接 720×512）、`super_bench_gui`（竖版按 470:316 居中裁切→940×632，圆槽保持正圆，代价是上下菱形饰角裁掉）、`structure_core_canvas`（1280×800，画布全屏拉伸）。全部 256 色量化，共约 1.66MB。
- DataPanelScreen / TradeCenterScreen 切到专属背景（原来借用 structure_core_gui）。
- SuperBenchScreen 接背景贴图 + 标题条/浏览器区两块半透明可读性底（文字不压花纹）。
- StructureCoreScreen（画布）：底色之上全屏拉伸绘制科幻边框，网格/节点/顶栏画在其上。
- 已知取舍：画布贴图静态网格与代码平移网格并存（轻微叠影，可接受）；超大工作台圆饰落点与结果槽不严格重合（GPT 构图 vs 代码坐标，看截图再微调）。

## m52 — 自动合成机（"量产一切"最后一块拼图）
- 新物品「自动合成机」：放入画布成节点，点节点右上角**目标徽章**弹出选择器（中英文搜索、图标网格），选定后按**原版合成配方**周期性吃料出货。
- `CraftPlanner`：目标 id → 扫原版合成配方表 → 材料多重集 + 单次产量 + 容器残留（桶等返还）；结果缓存，SERVER_STOPPED 清空。材料取每格 Ingredient 的第一候选（如任意木板→橡木板）。API 已查证：`listAllOfType`/`getResult(WrapperLookup)`/`getIngredients()`，`world.getRegistryManager()` 即 WrapperLookup。
- 经济诚实：产量=合成次数×配方产量；数量升级=多合成几次（消耗同步放大），不凭空放大产出。先按输出缓存封顶缩量、再扣料，防"白扣材料"。取料走 连线内部缓存 优先，否则存储网络；产出同路返回。
- 新包 `node_target`（走界面校验 + 长度上限 128）。大配方标志物：工作台×2+合成器×2（唯一，45→46 台零撞车）。
- 盯点（编译验证项）：`Ingredient.getMatchingStacks()`（1.21.1 返回 ItemStack[]，1.21.2+ 才改流式）、`Item.getRecipeRemainder()`、`TextFieldWidget.setX/setY/setFocused`。
- 占位贴图 auto_crafter.png（洋红），已进绘图名单。

## m53 — 经验系统 + 用户线缆模型接入
**经验（补齐 m48 盘点缺口）**：
- `MachineXp` 经验表，对齐原版：刷怪类每周期=击杀经验（常规敌 5、守卫者/烈焰人/旋风人 10、史莱姆/岩浆怪 2），熔炼类每件（铁 0.7/金 1.0/木炭 0.15/玻璃 0.1）；铁傀儡/切石/猪灵交易/采集类原版就无经验 → 0。
- 累积=xp×同时运行台数（并列=多杀）；**数量升级不放大经验**（它放大的是掉落，不是击杀数），诚实对齐。
- 结构核心 `xpPool`（double，NBT 持久化），propertyDelegate 第 7 位实时同步；画布顶栏显示「经验 N」，左下新增「★ 领取经验」按钮（onButtonClick id=2 → addExperience）。
- 抓物笼子按生物经验表累积；交易所每笔成交给玩家 3-6 经验（原版交易经验）。
**线缆模型（用户 sci_fi_data_cable.bbmodel）**：
- 自由格式 112 元素、坐标 ±28、64×16 内嵌图集 → 转换器：绕 Z 轴转 90°（横→竖基准）、缩放至正好 16 高（竖直无缝拼接）、居中，像素 UV→MC 0..16 UV，面向重映射（east→up 等），0 个带旋转元素零丢失。
- DataCableBlock：Block → PillarBlock（轴向，原木式放置横竖任意走线），blockstate 三轴变体（对照原版原木），细轮廓 6px（可点选、可跨越）；路由 BFS 只认方块类型不受影响。物品图标保留用户 2D 图。
- 已知取舍：面 UV 旋转统一 +90 近似（细缆侧面几乎不可见，效果不对再按截图微调）；直缆旋转式而非连接式多部件（拐角处两段各自朝向，连接式后续可做）。
- 盯点：`PillarBlock`/`getOutlineShape(protected)` 签名、`PlayerEntity.addExperience`、`Entity.getRandom`。

## m54 — 名字修复 + 贴图清单核准
- 修复「名字不对」根因：`storage_upgrade`（存储升级）/`storage_core`（存储核心）语言条目中英文全漏（m43 遗漏），游戏内显示原始键名。已补。
- 全量校验：模型→贴图、blockstate→模型引用零缺失；151 个原版 id 眼检无拼写错误。
- 绘图名单重写为扫描核准的待画清单：物品 8（auto_crafter/bonemeal/breeze/ghast/moss/stonecutter/villager_breeder/villager_contract）+ 方块 1（trade_center），附提示词。

## m55 — 用户存储核心模型接入（storage_core.bbmodel）
- 自由格式 99 元素、0 旋转、模型天然直立（无需转向）；坐标 X/Z ±12 底 Y=0 高 29.1 → 缩放 16/29.1 高度贴满一格、水平内缩至 13.4px（防邻块 z-fight），X/Z 平移 +8 居中。
- 128×16 金属图集抽出覆盖 textures/block/storage_core.png（原合成图弃用）；第二张 MER 贴图（金属度/自发光，PBR 用）原版 Java 不支持，未接入。
- 面 UV 像素制 → 0..16（÷128×16 / ÷16×16），面向不变、面内 rotation 原样保留。
- 物品模型 item/generated 2D 图标 → parent 方块模型（背包/手持显示 3D 机器，display 变换随模型内嵌）；旧 2D 图标文件保留未删（想换回说一声）。
- 方块侧无需改动：已 nonOpaque、渲染 MODEL、blockstate 指向不变。

## m56 — 存储核心动画（BER 复刻 bbmodel 动画，零新依赖）
- 用户模型自带 animation.core_cycle（4s 循环）：core_energy 绕 Y 匀速转+呼吸缩放 1↔1.08，corner_lights 呼吸 (1.04,1.08,1.04)。原版方块模型不支持动画 → 走方块实体渲染器（箱子开盖同机制）。
- 拆分：73 静态件留方块模型 JSON；26 动画件生成 StorageCoreAnimGeo（156 个四边形顶点数组，UV 旋转 Python 解算）；物品模型为 99 件全量静态版（背包/手持完整显示，BER 不作用于物品）。
- StorageCoreRenderer：世界时间驱动（80t 循环连续、三角波呼吸），绑定独立贴图 EntityCutoutNoCull（不吃背面剔除，绕开手写绕序风险），顶点走已查证的 11 参合并 vertex()（1.21.1 文档确认），矩阵自变换（JOML transformPosition/Matrix3f.transform）。
- 注册走 Fabric BlockEntityRendererRegistry（查证：原版 BlockEntityRendererFactories.register 是 private，不可直调）。
- 盯点：Entry.getNormalMatrix() 返回 Matrix3f（1.21.1）、Fabric BlockEntityRendererRegistry 包名 client.rendering.v1；四边形 UV 朝向映射为标准约定，动画件多为发光小面，若个别面贴图方向不对按截图微调。
