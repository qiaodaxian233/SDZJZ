# 生电终结者 · 项目交接文档（HANDOVER）

> 给接手的人（或新对话里的 AI）。**新对话开局标准动作**：作者贴仓库地址+一次性 PAT → clone →
> 读本文件 + DEVLOG.md 末尾几节 → 直接接活。不用翻聊天记录。
> 仓库：https://github.com/qiaodaxian233/SDZJZ · Fabric 1.21.1 · Yarn · 纯 Java · 前置仅 Fabric API。

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

## ⭐⭐ 工作流铁律（血泪换的，m99 之前丢过整轮工作）

1. **做一步推一步**：每完成一个独立改动就 commit + push，绝不在沙箱里攒。沙箱随时重置。
2. PAT 由作者在对话里现贴，**绝不写进任何提交文件**；git 身份沿用仓库既有提交者。
3. 沙箱编不了 Fabric/Mojang 依赖 → 新 API 一律标「待编译验证」，作者本地 IDEA+JDK21 编译，报错贴回逐个修。
   沙箱可 `apt-get update && apt-get install openjdk-21-jdk-headless` 装 javac 做**纯语法冒烟检查**
   （grep "expected|illegal|reached end|not a statement|unclosed"，其余报错都是缺 MC 依赖，正常）。
4. **注册六件套逐项计数断言**（m92b 教训）：新物品 = MachineDef + ModItems(reg+创造栏两处) +
   SuperBenchRecipes/data 配方 + 中英 lang + 模型 json + 贴图 png，改完逐项 grep 验数，JSON 过 json.load。
5. 每里程碑写 DEVLOG（现象→根因→修法→教训），提交信息带 mNN 编号。
6. 升级/封顶类公式改动必问："到顶之后玩家再投入会怎样"——静默无效比数值弱更伤（m99 教训）。

## 当前状态（m104b · 远端 b7598e0 · 2026-07）

**作者已本地编译全绿至 m102；m103/m104 为小修+美术，低风险待验。** 近期里程碑：

- **m99 升级数学重写**：工作量累积模型。速率=(1+speedGain)^速度级×productionRateMultiplier，
  每 tick 累积、溢出折同 tick 多周期永不触底；并发=直接乘台数(台数×(1+级)×tier)；数量封顶只剩
  "产出只能进内部缓存"时。五条生产分支（自动合成/农场/万能熔炉/通用机/抓物笼）统一。
  config 新增 upgradeSpeedGainPerLevel(0.5)/upgradeMaxCyclesPerTick(20)，configVersion=4。
- **m100 批量取出**：数据面板右键浮层第二行 2组/4组/8组/填满背包；服务端分块取+余量回仓绝不落地。
- **m101 交易所**：图书管理员 10 本好附魔书（绿宝石+书，治愈折扣生效）；列表 4 行滚动窗口；
  附魔书直发背包（仓储按 id 记账会抹组件，绝不入仓）；修双输入交易不扣第二种料的旧 bug。
- **m102 深层采掘平台**：钻石(0.15)/远古残骸(0.05)/深层地质/原矿三件套加权掉落；引子配方
  钻石×2+残骸×2；残骸→万能熔炉→下界合金碎片，量产链打通。
- **m103** 滚轮只在悬停交易列表时翻页；**m104** 深层采掘平台真美术归位，**全库 79 张物品图零占位**。

## 架构速查（改哪类问题去哪个文件）

- **生产/升级/tick**：`block/StructureCoreBlockEntity.java`（~1900 行核心；五分支 tick、
  cyclesThisTick/runningCount/rollDrops、供料 supplyFor/入库 depositFor/分发 distribute、链式需求 chainWants）
- **机器定义/注册**：`machine/Machines.java`(掉落表) + `registry/ModItems.java` + `machine/SuperBenchRecipes.java`(引子签名配方)
- **存储网络**：`block/StorageCoreBlockEntity.java`（connectedCores=贴邻/数据线 BFS4096；类型默认无限 m98）
- **存储终端**：`screen/DataPanelScreenHandler.java`(按钮 id=1000+格×10+档位0..8) + `client/DataPanelScreen.java`
- **交易所**：`machine/VillagerTrades.java`(纯Java,Trade record 含 enchant 字段) +
  `block/TradeCenterBlockEntity.java`(employ/trade/heal) + `client/TradeCenterScreen.java`
- **画布 UI**：`client/StructureCoreScreen.java`（节点/总线/机器库侧栏/视图控制）
- **网络包**：`net/*Payload.java`，注册与接收器在 `Sdzjz.java`
- **美术管线**：物品图 128×128 RGBA 透明底进 `textures/item/`；归位=裁边→留4%边距补方→LANCZOS 128→
  断言尺寸/模式/覆盖率→勾 `绘图名单.md`

## 待办池（按优先级）

1. 验证 m103/m104 编译与实机（滚轮区域、采掘平台产出、附魔书购买全流程）。
2. 量产覆盖.md 提案 2 考古工作站 / 提案 3 末地远征平台 / 提案 4 试炼农场（引子模式照 m102 抄）。
3. 概念图剩余大件：小地图、节点卡片齿轮设置/单节点启停（见 DEVLOG m89 对照表）。
4. 可选重绘 5 张程序生成图（绘图名单.md 底部，非必做）。

## 用户使用速查（作者常问）

村民合同：工作台 纸6+面包2+绿宝石1；交易所=绿宝石4+铁锭4+核心模块，**必须贴存储核心或数据线连通**，
就业耗网络里 1 个职业工作方块（图书管理员=讲台），治愈耗金苹果每级-10%最高5级，附魔书进背包不进仓。
