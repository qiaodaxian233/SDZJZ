# GraphNbt 拆分方案（m429 普查稿，动刀前须作者拍板）

> 背景：绞杀者路线（m180 立线）前四刀已收官——m180/m426 NodeTags、m427 NodeUpgrades、
> m428 DropRolls，全部是**纯函数**搬家，方法体一字未改、零行为改动、CI GameTest 全绿。
> m180 路线图下一位候选=「NBT 读写段（writeNbt/readNbt 拆 GraphNbt）」。m428 留话：
> **体量与状态触点远大于前四刀，动手前须单独普查立方案，不顺手带**。本文即该普查与方案。

## 一、普查结果（机器化盘点，2026-08-21，基于 0.1.428 / dad30fc）

### 1. 段落构成（SCBE 3831~4040 行区间，约 210 行）

| 方法 | 行数 | 性质 |
|---|---|---|
| `saveAdditional` | ~40 | @Override，写存档专属字段后调 writeRenderNbt |
| `writeRenderNbt` | ~55 | private，渲染子集编码——**m275 核心资产：存档/观众快照/getUpdateTag 三处共用同一编码函数** |
| `loadAdditional` | ~45 | @Override，读存档字段（m273 缓存清洗计数 / m142 毒区块清洗 / 老档迁移语义） |
| `readRenderNbt` | ~55 | private，渲染子集解码（m143 MERGED_IDS 重映射 / m179 bumpTopo / 坏键容忍） |
| `applyRenderSnapshot` / `getUpdateTag` | ~15 | public 门面，委托上两者 |

### 2. 字段触点（20 个实例字段，**全部 private**，外部零直接触点——外部只走访问器方法）

- **渲染子集（12 个，writeRenderNbt/readRenderNbt 消费）**：machineNodes(SCBE 内 79 处引用)、
  connections(24)、groupNames(13)、nodeStatus(19)、nodeReason(22)、storageEndpoints(20)、
  storageEndpointDims(11)、storageEdges(21)、storageEdgeDims(11)、storageNodePos(11)、
  busTopIds(10)、busTopCounts(9)，另 prodPerMin(6，与 prodWin/prodWinStart 三兄弟同行声明)。
- **存档专属（8 个，saveAdditional/loadAdditional 消费）**：items(25)、internalBuffer(16)、
  nodeBufs(12)、boundPanelPos(12)、boundPanelDim(7)、running(57，**public**)、xpPool(12)、
  forceChunks(18)+forceDims(8)、chunkOwned(7)。
- **方法依赖**：nodeBuf(i)、bumpTopo()、plausibleChunkLong()、静态 MERGED_IDS、Sdzjz.LOGGER。

### 3. 机械化改名/前缀的两颗雷（普查抓获，任何方案动手前的断言器必须先吃进去）

1. **字段-方法同名共存**：`nodeStatus` 字段（List\<Integer\>）与 `public int nodeStatus(int)` 方法、
   `nodeReason` 字段与 `public String nodeReason(int)` 方法同名。机械替换必须区分
   `名(`＝方法调用（不动）与其余＝字段（动），且方法定义行自身含字段引用（`nodeReason.size()`）。
2. **局部遮蔽已核清零**：12 个渲染字段名在方法体内无同名局部声明（逐名 grep 声明形态=0）。
   但断言器仍须沿 m427 教训用行锚定+剥注释口径，防子串误报（m109/m427 坏尺子谱系）。

### 4. 为什么前四刀刀法在此失效

前四刀迁的都是**入参齐全的纯函数**——搬家=剪切粘贴，方法体一字未改可断言。NBT 四方法直接
读写 20 个 private 字段：跨类搬家必然改方法体（加实例前缀）或改字段归属（搬字段），
「一字未改」的零风险口径不再可用，必须换成「机械变换+逐名计数断言+CI GameTest 行为判官」口径。

## 二、方案（三选一，推荐 A，作者拍板前不动刀）

### 方案 A（推荐）：渲染子集状态对象 CanvasGraphState——分三刀，直奔 Runtime 家族终局

- **终局形态**：新类 `xplat/node/CanvasGraphState`（见 MC 不见加载器）持有渲染子集 12+1 字段；
  SCBE 持 `final CanvasGraphState g`；渲染编解码成为 CanvasGraphState 成员方法
  writeRender/readRender（m275「单编码函数三处共用」结构原样保持，绝不裂成两份）。
- **第一刀（mA1）**：立 CanvasGraphState、12+1 字段搬家、SCBE 全文机械前缀 `g.`
  （~250 处，m426 干过 244 处的同量级；替换器=行锚定+剥注释+区分同名方法括号形态+逐名计数断言）。
  编解码方法暂留 SCBE 原位（此刀只动字段归属）。零行为改动。
- **第二刀（mA2）**：writeRenderNbt/readRenderNbt 迁入 CanvasGraphState 成员方法——迁入后
  字段前缀 `g.` 反向剥除，方法体回到「裸字段引用」形态，与原文逐字 diff 可对照；
  bumpTopo/MERGED_IDS 依赖以参数或回调注入（普查§2 只这两个跨界触点）。零行为改动。
- **第三刀（mA3，可选缓做）**：存档专属段同构处理（ArchiveState 或留 SCBE，届时再议——
  running 是 public 字段有 57 处引用，搬它的成本收益单独算账）。
- **收益**：SCBE 约 -110 行（mA2 后），且**渲染同步域**（m274/m275/m276 整条线）获得独立
  归属，后续 26.2 代际迁移时该域可整体复用；Runtime 家族拆分从「函数搬家」升级到「状态搬家」。
- **风险与对冲**：存档 NBT 是玩家数据命根子——键名/顺序/语义零改动（纯归属变化）；
  两刀之间 CI GameTest 全量判官；mA1 的 250 处替换用 m426 同款断言组（逐名 5 项）。

### 方案 B：GraphNbt 静态类 + `be.` 前缀 + 字段降包内可见（过渡刀，一笔完）

四方法迁 `xplat/block/GraphNbt`（com.sdzjz.block 包 xplat 侧已有先例），static + SCBE 参数，
20 字段 private→包内，方法体机械加 `be.` 前缀（~120 处）。快（一里程碑）、SCBE 直接 -210 行；
但**封装倒退**（20 个字段包内裸奔）且不推进字段归属终局，将来做方案 A 还得再动一遍。不推荐。

### 方案 C：暂不拆，留驻观察

评审③复评口径「下一步应该测，而不是继续凭感觉重构」。本段有 m275 对偶结构+注释完善+
m273/m142 清洗语义，拆分收益主要是行数。若作者近期重心在实机验证池清账，C 合理——
绞杀者线可先歇，待 26.2 代际 P2/P3 推进时随 PLATFORM_MAP 的 SCBE 压轴项一起动。

## 三、拍板问题（作者一句话即可）

1. 选 A / B / C？
2. 选 A 时：mA3（存档段）是否纳入本轮，还是只做 mA1+mA2？

拍板前绞杀者线暂停；其余待办不受影响。
