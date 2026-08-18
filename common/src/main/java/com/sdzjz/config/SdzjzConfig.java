package com.sdzjz.config;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 全数值可调配置。约定（照 yongye）：
 * - 改结构就升 configVersion；
 * - 老存档缺键由 GSON 取字段默认值，load() 后 save() 一次把缺键补齐回写。
 */
public class SdzjzConfig {
    public int configVersion = 53; // m384 区块机器特效总开关（chunkFxEnabled：选区框+锁定爆发+前沿粒子）；m382 区块移除器区域半径上限（chunkRemoverMaxRadius）；m381 区块储存器三键（chunkVaultEnabled 开关/chunkVaultBlocksPerCycle 每周期扫描位点/chunkTemplateMaxCount 模板库全局封顶）；m380 区块扫描器两键（chunkScannerEnabled 开关/chunkScannerBlocksPerCycle 每周期扫描位点数）；m378 虚空处理器两键（voidProcessorEnabled 开关/voidXpPerItemsEaten 汇率）；m377 区块过滤器开关（chunkFilterEnabled，关=移除器忽略过滤器按全量挖）；m376 区块移除器三键（chunkRemoverEnabled 开关/chunkRemoverBlocksPerCycle 每周期基础块数/chunkRemoverSkipBlockEntities 跳过方块实体）；m352 节点双侧进出口开关（nodeDualSidePorts，作者点名）；m348 停机核心端点扫描降频开关（coreIdleScanRelief，外部审计）；m347 孤儿强加载声明核销开关（chunkClaimReconcile，外部审计）；m343 合成机槽位替代材料开关（craftIngredientAlternatives，外部审计P0）；m341 节点进出口互换开关（nodePortsSwapped）；m340 显式供料线补足开关（supplyTopUp）；m339 经验池公平层开关（xpFairShare）；m335 选择器查询语法开关（pickerQuerySyntax）；m334 无限复制机两键（duplicatorEnabled 开关/duplicatorXpPerItem 每件经验价）；m333 交易所等级系统两键（tradeLeveling 总开关/tradeXpMultiplier 经验倍率）；m332 随身仓库专属仓位开关（portableVaultSlot，需双端一致）；m322 终端主快照缓存开关（panelMasterSnapshotCache）；m320 Sodium 图标动画保活开关（sodiumIconAnimFix）；m311 随身仓库两键（吸附半径/类型上限）；m310 原生大堆叠两键（bigStacks 开关 + bigStackMax 上限，替代 ItemStackProMax）；m293 类型安全硬顶；m289 配方书计仓储开关；m285 扁平扫光开关；m282 终端搜索首字母开关；m281 终端配方书开关；m280 压缩包内容物自转速度键；m270 服务器硬上限四键+核心tick预算真接线；m269 每玩家每tick C2S写包预算（防伪造包洪泛触发同步风暴）；m265 总线端点卡可拖下画布开关（关=全部按停靠渲染，落位数据保留）；m261 画布背景默认纯黑（旧默认空串迁移成 000000，用户自定义值不动）；m225 数据线抽取口两键（周期/每拍件数）；m221 整理布局间距三键（收紧默认并可调）；m220 画布装饰底图开关（设背景色自动隐图）；m219 画布状态区收纳开关；m218 多核心性能双开关；m217 画布背景四项（底色/网格色/网格浓度/暗角强度）；m215 画布上下栏紧凑化开关+总线卡尺寸落盘；m214 画布/终端主题分家（canvas* 7键默认暗夜，共用预设者终端回紫晶）；m207 画布新配色默认迁移；m200 终端主题7色；m198 连线分色；m197 线宽随缩放

    // ===== 生产限制（照设计文档 §7.4：不用传统电力，用结构完整度/吞吐/散热 + 每tick操作预算）=====
    public long maxRecipesPerCoreTick = 65_536L;        // 单生产核心每tick最大逻辑配方次数（m270 真接线：cyclesThisTick 全核共享预算，0或负=无限；默认值高于 节点cap20×512节点=10240 的理论峰值，默认不束缚纯作管理员旋钮）
    public long maxRecipesPerChunkTick = 262_144L;      // m324 真接线：每区块每tick生产周期上限（跨核共享,CoreScheduler区块账每拍复位,0或负=无限;四层=节点cap→核内→区块→全服,耗尽只欠不丢;区块层无公平名单=同区块内按BE tick序竞争,是"核心挤一个强加载区块"的管理员钝闸）；默认极高不束缚
    // ===== m311 随身仓库 =====
    public int portableVaultMagnetRadius = 5;   // 吸附半径（格），0=全局禁吸附；每 0.5s 一扫
    public int portableVaultTypeCap = 256;
    public boolean portableVaultSlot = true;    // m332 背包屏追加专属仓位（下标46，副手上方）。需双端一致（bigStacks 同律）：不一致=槽数错位同步炸
    public boolean tradeLeveling = true;        // m333 交易所合同等级系统（新手→大师，交易攒经验解锁高档交易）。关=全表解锁旧行为。服务端权威；客户端值只影响锁行显示
    public int tradeXpMultiplier = 1;           // m333 每笔交易合同经验倍率（≥1，想快毕业调大）
    public boolean duplicatorEnabled = true;    // m334 无限复制机总开关（服主熔断阀：关=画布节点黄灯待命，不删档）
    public int duplicatorXpPerItem = 20;        // m334 每复制 1 件烧核心经验池的经验（≥1）——复制的唯一运行成本，服主按经济调
    public boolean chunkRemoverEnabled = true;  // m376 区块移除器总开关（服主熔断阀：关=画布节点黄灯待命，不删档不丢绑定）
    public int chunkRemoverBlocksPerCycle = 64; // m376 每周期基础移除方块数（速度/数量/并发升级另乘）；384 高整区块≈9.8 万块，默认约 51 分钟/台清完
    public boolean chunkFilterEnabled = true;   // m377 区块过滤器总开关（关=区块移除器忽略过滤器按全量挖——注意语义是"撤规则"不是"停移除"）
    public boolean chunkScannerEnabled = true;  // m380 区块扫描器总开关（关=节点黄灯待命，绑定与已出报告不丢）
    public int chunkScannerBlocksPerCycle = 4096; // m380 每周期扫描位点数（只读扫描便宜给足；384 高整区块≈24 周期≈48 秒/台，升级另乘，tick 硬顶 16384）
    public boolean chunkVaultEnabled = true;    // m381 区块储存器总开关（关=节点黄灯待命，绑定与已产核心不丢）
    public int chunkVaultBlocksPerCycle = 4096; // m381 每周期扫描位点数（只读扫描，扫描器同额；升级另乘，tick 硬顶 16384）
    public int chunkTemplateMaxCount = 256;     // m381 模板库全局封顶（PersistentState 防膨胀；满=红灯拒存出声不重扫，清理后自动续存）
    public boolean voidProcessorEnabled = true; // m378 虚空处理器总开关（关=拒收+持料待命：上游走默认路由回仓，缓存残料不吞不退）
    public int voidXpPerItemsEaten = 64;        // m378 汇率：每吞多少件折 1 经验（≥1）——整区块≈9.8万块≈1500 经验≈复制 75 件，服主按经济调
    public boolean chunkFxEnabled = true;      // m384 区块机器特效总开关：手持选区框（客户端本地读）+锁定爆发粒子环/信标音+前沿传送门粒子（服务端，每台每拍封顶 6 点）
    public int chunkRemoverMaxRadius = 2;      // m382 移除区域半径上限（0=只许 1×1，2=最大 5×5=25 分块；预算不变区域越大越慢，服主按服情收顶）
    public boolean chunkRemoverSkipBlockEntities = true; // m376 跳过带方块实体的方块（箱子/刷怪笼/本模组机器等，防误吞基地）；关=照拆，容器内容物按原版散落原地不进机器
    public boolean pickerQuerySyntax = true;    // m335 画布选择器查询语法（@模组/-排除/|并联，学JEI语法习惯自写实现）。关=回纯包含匹配
    public boolean chunkClaimReconcile = true; // m347 孤儿强加载声明核销（外部审计）：声明表区块连续三轮（≥30s）运行时零核心登记即撤票删声明。关=旧行为（孤儿声明永久钉住区块）
    public boolean coreIdleScanRelief = true; // m348 停机+无观众的核心端点扫描 40t→200t 慢拍保底（200%40=0 日历拍切换无缝；开机/开画布两转变沿哨兵强刷零陈旧窗）。关=恒 40t 旧行为
    public boolean nodeDualSidePorts = true; // m352 画布节点卡左右两缘各带一对进/出口（出上进下，m184 连线几何两侧智能选缘终于有柱可贴；判定双侧可抓，进口起手随鼠标选缘）。关=回 m341/m342 单侧行为（nodePortsSwapped 仅在关时生效）
    public boolean craftIngredientAlternatives = true; // m343 合成机槽位替代材料（外部审计P0）：任意木板类配方认全部候选材料，组内按序贪心取用。关=旧"只认首选"（消耗与路由同口径回退）
    public boolean nodePortsSwapped = true;     // m341 画布节点接线柱互换：出口在左、进口在右（作者点名）。关=旧左入右出。线端走就近缘（m184）不受影响
    public boolean supplyTopUp = true;          // m340 连线喂料的机器：显式存储供料线自动补足缓存缺口（熔炉族除外防误烧）。关=旧"接线就只吃线"
    public boolean xpFairShare = true;          // m339 经验池公平层：有吃经验机器挨饿时全池先喂它（复制机/附魔工厂多台并存不再"第一台吃光第二台饿死"）。关=旧先到先得
    public boolean sodiumIconAnimFix = true;    // m320：装 Sodium 时每客户端tick给本模组动画物品精灵标"活跃"（其"仅动画可见纹理"优化只保世界内方块精灵，纯GUI物品精灵会冻帧）；未装Sodium自动停用零开销      // 账本类型上限（只闸新类型，已有类型照常并账）。账本存物品组件，类数越大背包同步越重，慎调大

    // ===== m310 原生大堆叠（替代 ItemStackProMax，模组自带）=====
    public boolean bigStacks = true;              // 全局提升可堆叠物品的堆叠上限（原版不可堆叠的 maxCount=1 物品不动，防工具/耐久合并出鬼）
    public int bigStackMax = 1_073_741_823;       // 每格上限，默认 2^30。为何不是 2147483647：①int 是物理天花板任何模组都超不过；②原版合并算术 a+b 两栈相加在 >2^30 时会 int 溢出成负数吃物品——2^30 保证两栈相加 ≤2^31-2 永不溢出。更大量级走压缩包(×4096≈每格4.4万亿等效)或仓储 long 账本。加载时钳到 [64, 2^30]
        public long maxRecipesPerNetworkTick = 1_048_576L;  // m302 真接线：**全服**每tick生产周期总预算（跨核心共享,0或负=无限;耗尽只欠不丢,饥饿名单保底=没吃到的核心下tick先食1周期,见 machine/CoreScheduler）；默认极高不束缚纯作管理员旋钮
    public int accelMinPeriodTicks = 1;                 // 【遗留,m99后不再参与计算】旧线性加速的最小周期下限
    public double upgradeSpeedGainPerLevel = 0.5;       // m99 速度升级每级增益(乘算,0.5=+50%,速率=1.5^级)，速率溢出折成同tick多周期，永不触底
    public int upgradeMaxCyclesPerTick = 20;            // m99 单节点每tick最多结算周期数(防极高速度级单tick天量运算卡服)
    public int wirelessRange = 48;                      // 无线(WiFi)连接范围(格,同维度)
    public boolean enableThermalThrottle = false;       // 高速产热/需散热框架（默认关，可选平衡）

    // ===== 防卡顿 / 输出 =====
    public int maxSprayEntitiesPerTick = 32;  // 每tick最大喷射实体数（§8.3 防实体爆炸）
    public int coreBufferSlots = 27;          // 生产核心输出缓存槽数（满则按面板设置停机/喷射）
    public int storageTypesPerTier = 0;       // 存储核心每级类型数：0=无限类型(默认,m98,但仍受下方 8192 技术硬顶闸新)；>0=旧成长机制(原27,存储升级+1级)
    public int absoluteStorageTypeSafetyLimit = 8192; // m293 单核心类型数技术保险(独立于玩法额度,无限仓照常显示无限)；≤0=关
    public boolean sleepWhenIdle = true;      // 无红石/缺料/堵塞/无人加载时休眠停tick（§15.3）
    // ===== m270 服务器硬上限（外部审计"'无限节点'需要服务器硬上限"；全部 0=无限即旧行为；只闸新增长，超限旧档原样跑不截断）=====
    public int maxNodesPerCore = 512;        // 每核心画布节点数上限（拓扑重编译/tick遍历/NBT同步成本全随它涨）
    public int maxEdgesPerCore = 2048;       // 每核心机器连线总数上限（存储连线单独同额封顶）
    public int maxEdgesPerNode = 64;         // 单节点连线度数上限（进+出合计；分配器扇出场景 64 已很奢侈）
    public int maxBufferTypesPerNode = 256;  // 单节点输入缓存物品类型数上限（拒收新类型时残量走默认路由/干脆不抽，零物品损失）

    // ===== m280 压缩包图标 =====
    public int compressedPackSpinDegPerSec = 45; // 压缩包内容物自转速度(度/秒,45=8秒一圈)；0或负=不转；只转3D方块模型，扁平物品与边框恒静止
    public boolean compressedPackFlatSheen = true; // m285 扁平内容物(剑/锭/粉等不转的)原版流光扫光；false=关

    // ===== m281 存储终端配方书 =====
    public boolean terminalRecipeBook = true; // 终端合成区接原版配方书(绿书钮/点配方仓储优先填料/缺料ghost)；false=整套隐身
    public boolean terminalBookStock = true; // m289 配方书"可合成"计入仓储库存(摘要前2048种/计数封顶9999/指纹节流每秒至多1包)；false=只认背包+网格
    public boolean terminalSearchInitials = true; // m282 终端搜索拼音/词首字母通道("zs"→钻石,"ii"→Iron Ingot)；与子串搜索取并集
    public int packetWriteBudgetPerTick = 40; // m269 每玩家每tick画布/面板C2S写包预算（防洪泛：每个写包都触发全量同步，无闸=同步风暴；正常UI交互每tick至多几包，40远够）；0=关闭护栏
    public boolean coreChunkLoading = true;   // m133 结构核心开机=强制加载自身区块(重启自恢复)+存储端点区块(有期票)；关=离开即停产(旧行为)
    public boolean canvasEndsPlaceable = true; // m265 总线端点卡可拖下画布钉在图上；关=拖拽禁用+一律按停靠栏渲染（已落位数据保留不丢，重开即恢复）
    public int structureBlocksPerTick = 1024;         // 一键建造每tick摆放方块数(分批防卡顿)
    public boolean structureConsumeMaterials = false; // 一键建造是否消耗背包材料(默认关)

    // ===== 基调（偏硬核，全可调；越大越休闲）=====
    public double productionRateMultiplier = 1.0;

    // ===== 画布（客户端视图数值，读本机配置文件，不参与服务端逻辑）=====
    public double canvasZoomMin = 0.05;   // m185 画布缩放下限（0.05=5%；旧硬编码 0.4 放开，想更小自己改）
    public double canvasZoomMax = 8.0;    // m185 画布缩放上限（8=800%；旧硬编码 2.5 放开）
    public boolean canvasSmoothZoom = true; // m186 画布缩放平滑动效（指数缓动+指哪缩哪；false=瞬时跳变旧行为）
    public boolean canvasGroupsEnabled = true; // m191 画布机器打组（框选成组/组框拖动/连线归并）；关=服务端拒收组操作+客户端不画组框
    public boolean canvasWireScaleWithZoom = true; // m197 连线宽度随画布缩放（缩小时线跟着变细，与卡片视觉一致）；false=旧行为屏幕恒宽
    public double canvasWireMaxScale = 1.0;        // m197 线宽屏幕封顶倍率：屏幕线宽=基准×min(缩放,此值)。1.0=放大不加粗（现有粗细即上限）；调大则放大时线可增粗到该倍；下限0.2
    public String canvasWireOutColor = "A8A0F0"; // m198 出线颜色RRGGBB（机器产出→存储/机器→机器），默认=薰衣草紫（m207 照新截图），非法值回退默认
    public String canvasWireInColor  = "6FB57A"; // m198 进线颜色RRGGBB（存储供料→机器），默认=柔绿（m207 照新截图）
    public boolean canvasGroupBundleWires = true; // m193 跨组界连线归并成一条(×N徽章)；false=每条线照旧各画各的（纯客户端渲染，不碰数据）

    // ===== 存储终端主题（m200 照作者设计稿浅灰+紫；RRGGBB 字符串允许带#，非法回退默认；纯客户端渲染）=====
    public String termBase       = "E6E8EF"; // 主色（窗体/卡面浅灰）
    public String termBaseDeep   = "AEB4C7"; // 主色深（槽底/滚条轨/凹陷面）
    public String termAccent     = "8B7CF6"; // 强调紫（主按钮/聚焦边/滚条滑块/数值）
    public String termAccentDeep = "6D5CE0"; // 强调深（主按钮边框/图标）
    public String termInk        = "181C2B"; // 墨色（浅面上的正文字，兼全屏暗底/暗按钮面；m207 转藏蓝）
    public String termFrame      = "3A3F4B"; // 边框色
    public String termHi         = "FFFFFF"; // 高亮（浅面提亮/紫钮与暗钮上的文字）

    // ===== 结构核心画布主题（m214 与终端分家：各一套7色互不影响；默认=暗夜预设，作者点名）=====
    public String canvasBase       = "262C38"; // 画布主色（横幅/卡面深灰蓝）
    public String canvasBaseDeep   = "161B24"; // 画布主色深（槽底/滑轨/凹陷面）
    public String canvasAccent     = "8B7CF6"; // 画布强调紫（主按钮/聚焦边/网格/选中）
    public String canvasAccentDeep = "B0A6FF"; // 画布强调深端（暗系里反向提亮，照暗夜预设口径）
    public String canvasInk        = "E7EAF3"; // 画布墨色（暗面上文字→亮字；兼全屏底反相用法见 SciSkin）
    public String canvasFrame      = "444B5A"; // 画布边框色
    public String canvasHi         = "0E1118"; // 画布高亮（暗夜口径=暗压光：紫钮上的字用暗色）

    // ===== 画布上下栏紧凑化（m215 作者点名"上下的界面太大"）=====
    public boolean canvasCompactChrome = true; // 紧凑模式：底栏78→56(钮20→16/三行字收紧)、总线行距12→8；false=旧版尺寸（改后重开画布生效）
    public double canvasBusScale = 0.75;       // 总线卡尺寸倍率持久化（0.55~1.25，画布"尺寸"滑块同款；m93 原为会话内静态不落盘）
    // m217 画布背景四项（作者点名"背景要根据配色调整、要看得清、全部进设置可调"）：
    public String canvasBgColor        = "000000"; // 工作区底色RRGGBB；空/非法=跟随主题墨色（canvasInk）。m261 默认纯黑（作者点名 RGB 0 0 0；设色即 m220 自动隐装饰底图，清空可回随主题）
    public String canvasGridColor      = "";   // 网格线颜色RRGGBB；空/非法=跟随主题强调色（canvasAccent）
    public double canvasGridStrength   = 1.0;  // 网格浓度倍率 0.0~3.0（细线10%/主线19%基准上乘，0=隐藏网格）
    public double canvasVignetteStrength = 1.0;// 四缘暗角强度倍率 0.0~2.0（0=关暗角，浅色主题看不清可调低）
    public boolean canvasBgDecor       = true; // m220 画布装饰底图开关；且 canvasBgColor 设了值时自动隐藏（设色=纯色画布），false=无条件不贴
    // m221 "整理布局"排布参数（作者点名旧 150/130 步距"离得有点远"）：步距=卡实占(宽100/高80)+间隙
    public int canvasLayoutRows        = 5;    // 每列机器台数（满则换列，m149 竖排口径）
    public int canvasLayoutGapX        = 30;   // 列间隙（像素，画布世界坐标）
    public int canvasLayoutGapY        = 24;   // 行间隙（像素，画布世界坐标）
    // m225 数据线抽取口（扳手启用；从相连存储核心账本抽物品塞进邻接的任意 Fabric Transfer API 存储）：
    public int extractPortPeriodTicks  = 20;   // 抽取拍周期（tick；pos哈希移相多口错峰，m218c 口径）
    public int extractPortBatch        = 256;  // 每拍每口最多搬运件数（跨类型共享预算）
    // m218 多核心性能（多个结构核心叠加时的服务端tick优化，两键独立可关便于线上二分定位）：
    public boolean coreTickStagger = true; // 错峰：ends包/区块票/拉料拍/端点扫描按核心pos哈希移相（逐核频率不变，只是不再挤同一tick）；false=旧同拍
    public boolean panelViewCache = true;  // 数据面板聚合视图revision缓存（账本没动不重建、同tick复用快照）；false=每调全量重建旧行为
    public boolean panelMasterSnapshotCache = true; // m322 终端主快照缓存：多观众共用一次全仓聚合+排序（指纹=普通+精确修订号和+核心数）；false=每 handler 每次全量重建旧行为
    // m219 底带再瘦身（作者圈图点名：状态/提示两坨字收进顶栏按钮，底带只剩按钮排）：
    public boolean canvasStatusOpen = false;   // 状态区展开：true=底带显示两行运行统计（顶栏"状态"钮即点即存切换）；false=收起只剩按钮排

    // ---- 单例 + 读写 ----
    private static SdzjzConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // m370：Common 层自持日志口——此前四处错误日志借 Legacy 入口类 Sdzjz 的静态 Logger，
    // Legacy 侧类路径齐全从未爆雷，26.2 侧 Common 单独编译即红（本笔冒烟抓获）。
    // slf4j 双世代类路径都有（MC/loader 自带），硬闸口径放行；硬闸第④检自此禁引名册外自家类。
    private static final Logger LOGGER = LoggerFactory.getLogger("sdzjz");
    private static final String FILE_NAME = "sdzjz.json";

    public static SdzjzConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        Path path = com.sdzjz.platform.Platform.configDir() /* m365 升 Common */.resolve(FILE_NAME);
        SdzjzConfig cfg = null;
        boolean skipWriteBack = false; // m272：读没成功且原文件还在原位时置真——本次不回写，防 save() 用默认值覆盖没读到的用户内容
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                cfg = GSON.fromJson(r, SdzjzConfig.class); // 空文件 Gson 返回 null 不抛异常：无内容可保，走下方默认+回写重生成
            } catch (IOException e) {
                // IO 层读失败（权限/占用等）：文件保留原样、日志出声、回落默认继续启动；本次不回写
                cfg = null;
                skipWriteBack = true;
                LOGGER.error("配置文件读取失败（IO），本次使用默认配置且不回写，原文件保留: {}，原因: {}", path, e.toString());
            } catch (RuntimeException e) {
                // m272：Gson 的 JsonSyntaxException/JsonIOException 等运行时解析异常——旧版完全没兜，
                // 配置里一个多余逗号就中断 MOD 初始化。坏档改名 .broken-时间戳 留证
                // （防 load 尾部 save() 回写默认值覆盖用户手改内容），然后回落默认继续启动。
                cfg = null;
                try {
                    Path broken = path.resolveSibling(FILE_NAME + ".broken-" + System.currentTimeMillis());
                    Files.move(path, broken);
                    LOGGER.error("配置文件解析失败（JSON 损坏），已改名留证 {}，回落默认配置继续启动。原因: {}", broken.getFileName(), e.toString());
                } catch (IOException mv) {
                    skipWriteBack = true; // 改名留证也失败：原文件保留原位，本次不回写防覆盖
                    LOGGER.error("配置文件解析失败且改名留证失败，原文件保留，本次使用默认配置且不回写: {}，解析原因: {}，改名原因: {}", path, e.toString(), mv.toString());
                }
            }
        }
        if (cfg == null) cfg = new SdzjzConfig();
        if (cfg.configVersion < 13) { // m207 v13 迁移：画布新默认色——仅"仍是旧默认"才替换，用户自定义值不动
            if ("2EC4FF".equals(cfg.canvasWireOutColor)) cfg.canvasWireOutColor = "A8A0F0";
            if ("33D07A".equals(cfg.canvasWireInColor))  cfg.canvasWireInColor  = "6FB57A";
            if ("1E2128".equals(cfg.termInk))            cfg.termInk            = "181C2B";
            cfg.configVersion = 13;
        }
        if (cfg.configVersion < 14) { // m214 v14 主题分家：canvas* 7 新键缺省即暗夜（Gson 缺键走字段初值，无需搬迁）；
            // 旧共用时代若终端 7 键恰好整套等于某个非紫晶预设行（=当年为救画布点的全局换肤），回默认紫晶；
            // 逐色手调过的组合（对不上任何整行）一律不动。字面量=SciSkin.TERM_PRESETS 后四行留档（common 侧不能引 client 类）。
            String[][] presets = {
                    {"262C38", "161B24", "8B7CF6", "B0A6FF", "E7EAF3", "444B5A", "0E1118"}, // 暗夜
                    {"E2EEF0", "A9C3C9", "2FA8C2", "1E7E93", "13282D", "39555C", "FFFFFF"}, // 海雾
                    {"F4E7EC", "D3AFBE", "E06C9F", "B84D7F", "32161F", "5C3A47", "FFFFFF"}, // 樱粉
                    {"E6EFE6", "AECBB0", "4CAF6E", "337E4E", "15271A", "3C5943", "FFFFFF"}, // 松绿
            };
            for (String[] p : presets) {
                if (p[0].equals(cfg.termBase) && p[1].equals(cfg.termBaseDeep) && p[2].equals(cfg.termAccent)
                        && p[3].equals(cfg.termAccentDeep) && p[4].equals(cfg.termInk)
                        && p[5].equals(cfg.termFrame) && p[6].equals(cfg.termHi)) {
                    SdzjzConfig def = new SdzjzConfig(); // 取字段默认，零硬编码重复
                    cfg.termBase = def.termBase; cfg.termBaseDeep = def.termBaseDeep;
                    cfg.termAccent = def.termAccent; cfg.termAccentDeep = def.termAccentDeep;
                    cfg.termInk = def.termInk; cfg.termFrame = def.termFrame; cfg.termHi = def.termHi;
                    break;
                }
            }
            cfg.configVersion = 14;
        }
        if (cfg.configVersion < 15) cfg.configVersion = 15; // m215 纯加键（canvasCompactChrome/canvasBusScale），Gson 缺键走字段初值，无值迁移
        if (cfg.configVersion < 16) cfg.configVersion = 16; // m217 纯加键（画布背景四项：canvasBgColor/GridColor/GridStrength/VignetteStrength），缺键走字段初值
        if (cfg.configVersion < 17) cfg.configVersion = 17; // m218 纯加键（coreTickStagger/panelViewCache 多核心性能双开关），缺键走字段初值
        if (cfg.configVersion < 18) cfg.configVersion = 18; // m219 纯加键（canvasStatusOpen 状态区展开开关），缺键走字段初值
        if (cfg.configVersion < 19) cfg.configVersion = 19; // m220 纯加键（canvasBgDecor 装饰底图开关），缺键走字段初值
        if (cfg.configVersion < 20) cfg.configVersion = 20; // m221 纯加键（canvasLayoutRows/GapX/GapY 整理布局排布三键），缺键走字段初值
        if (cfg.configVersion < 21) cfg.configVersion = 21; // m225 纯加键（extractPortPeriodTicks/Batch 抽取口两键），缺键走字段初值
        if (cfg.configVersion < 22) { // m261 背景默认纯黑：仅"仍是旧默认（空=随主题）"才替换，用户自定义值不动（m207 先例）
            if (cfg.canvasBgColor == null || cfg.canvasBgColor.isBlank()) cfg.canvasBgColor = "000000";
            cfg.configVersion = 22;
        }
        if (cfg.configVersion < 23) cfg.configVersion = 23; // m265 纯加键（canvasEndsPlaceable 端点卡可拖下画布开关），缺键走字段初值
        if (cfg.configVersion < 24) cfg.configVersion = 24; // m269 纯加键（packetWriteBudgetPerTick 每玩家每tick写包预算），缺键走字段初值
        if (cfg.configVersion < 25) cfg.configVersion = 25; // m270 纯加键（maxNodesPerCore/maxEdgesPerCore/maxEdgesPerNode/maxBufferTypesPerNode 服务器硬上限），缺键走字段初值
        if (cfg.configVersion < 32) cfg.configVersion = 32; // m310 纯加键（bigStacks/bigStackMax 原生大堆叠），缺键走字段初值
        if (cfg.configVersion < 33) cfg.configVersion = 33; // m311 纯加键（portableVaultMagnetRadius/portableVaultTypeCap 随身仓库），缺键走字段初值
        if (cfg.configVersion < 34) cfg.configVersion = 34; // m320 纯加键（sodiumIconAnimFix），缺键走字段初值
        if (cfg.configVersion < 35) cfg.configVersion = 35; // m322 纯加键（panelMasterSnapshotCache 终端主快照缓存），缺键走字段初值
        if (cfg.configVersion < 36) cfg.configVersion = 36; // m332 纯加键（portableVaultSlot 专属仓位），缺键走字段初值
        if (cfg.configVersion < 37) cfg.configVersion = 37; // m333 纯加键（tradeLeveling/tradeXpMultiplier 交易所等级），缺键走字段初值
        cfg.tradeXpMultiplier = Math.max(1, cfg.tradeXpMultiplier); // m333 钳位
        if (cfg.configVersion < 38) cfg.configVersion = 38; // m334 纯加键（duplicatorEnabled/duplicatorXpPerItem 复制机），缺键走字段初值
        cfg.duplicatorXpPerItem = Math.max(1, cfg.duplicatorXpPerItem); // m334 钳位
        if (cfg.configVersion < 39) cfg.configVersion = 39; // m335 纯加键（pickerQuerySyntax 选择器语法），缺键走字段初值
        if (cfg.configVersion < 40) cfg.configVersion = 40; // m339 纯加键（xpFairShare 经验池公平层），缺键走字段初值
        if (cfg.configVersion < 41) cfg.configVersion = 41; // m340 纯加键（supplyTopUp 显式供料线补足），缺键走字段初值
        if (cfg.configVersion < 42) cfg.configVersion = 42; // m341 纯加键（nodePortsSwapped 进出口互换），缺键走字段初值
        if (cfg.configVersion < 43) cfg.configVersion = 43; // m343 纯加键（craftIngredientAlternatives 合成机槽位替代材料开关），缺键走字段初值
        if (cfg.configVersion < 44) cfg.configVersion = 44; // m347 纯加键（chunkClaimReconcile 孤儿声明核销开关），缺键走字段初值
        if (cfg.configVersion < 45) cfg.configVersion = 45; // m348 纯加键（coreIdleScanRelief 停机扫描降频开关），缺键走字段初值
        if (cfg.configVersion < 46) cfg.configVersion = 46; // m352 纯加键（nodeDualSidePorts 双侧进出口开关），缺键走字段初值
        if (cfg.configVersion < 47) cfg.configVersion = 47; // m376 纯加键（chunkRemover 三键），缺键走字段初值
        if (cfg.configVersion < 48) cfg.configVersion = 48; // m377 纯加键（chunkFilterEnabled），缺键走字段初值
        if (cfg.configVersion < 49) cfg.configVersion = 49; // m378 纯加键（voidProcessor 两键），缺键走字段初值
        if (cfg.configVersion < 50) cfg.configVersion = 50; // m380 纯加键（chunkScanner 两键），缺键走字段初值
        if (cfg.configVersion < 51) cfg.configVersion = 51; // m381 纯加键（chunkVault 三键），缺键走字段初值
        if (cfg.configVersion < 52) cfg.configVersion = 52; // m382 纯加键（chunkRemoverMaxRadius），缺键走字段初值
        if (cfg.configVersion < 53) cfg.configVersion = 53; // m384 纯加键（chunkFxEnabled），缺键走字段初值
        cfg.bigStackMax = Math.max(64, Math.min(1_073_741_823, cfg.bigStackMax)); // m310 钳位：上界 2^30 防原版合并 a+b 溢出吃物品

        INSTANCE = cfg;
        if (!skipWriteBack) save(); // 回写补齐缺键 / 生成默认文件（m272：IO 读失败或改名留证失败时跳过，防覆盖未读到的用户内容）
    }

    public static void save() {
        if (INSTANCE == null) return;
        Path path = com.sdzjz.platform.Platform.configDir() /* m365 升 Common */.resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            LOGGER.error("配置文件保存失败: {}，原因: {}", path, e.toString()); // m272：旧版静默吞掉，磁盘满/只读时用户改的配置默默丢
        }
    }
}
