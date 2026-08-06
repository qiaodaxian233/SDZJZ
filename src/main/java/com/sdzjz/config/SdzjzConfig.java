package com.sdzjz.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

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
    public int configVersion = 24; // m269 每玩家每tick C2S写包预算（防伪造包洪泛触发同步风暴）；m265 总线端点卡可拖下画布开关（关=全部按停靠渲染，落位数据保留）；m261 画布背景默认纯黑（旧默认空串迁移成 000000，用户自定义值不动）；m225 数据线抽取口两键（周期/每拍件数）；m221 整理布局间距三键（收紧默认并可调）；m220 画布装饰底图开关（设背景色自动隐图）；m219 画布状态区收纳开关；m218 多核心性能双开关；m217 画布背景四项（底色/网格色/网格浓度/暗角强度）；m215 画布上下栏紧凑化开关+总线卡尺寸落盘；m214 画布/终端主题分家（canvas* 7键默认暗夜，共用预设者终端回紫晶）；m207 画布新配色默认迁移；m200 终端主题7色；m198 连线分色；m197 线宽随缩放

    // ===== 生产限制（照设计文档 §7.4：不用传统电力，用结构完整度/吞吐/散热 + 每tick操作预算）=====
    public long maxRecipesPerCoreTick = 65_536L;        // 单生产核心每tick最大逻辑配方次数
    public long maxRecipesPerChunkTick = 262_144L;      // 每区块每tick上限
    public long maxRecipesPerNetworkTick = 1_048_576L;  // 每玩家网络每tick上限
    public int accelMinPeriodTicks = 1;                 // 【遗留,m99后不再参与计算】旧线性加速的最小周期下限
    public double upgradeSpeedGainPerLevel = 0.5;       // m99 速度升级每级增益(乘算,0.5=+50%,速率=1.5^级)，速率溢出折成同tick多周期，永不触底
    public int upgradeMaxCyclesPerTick = 20;            // m99 单节点每tick最多结算周期数(防极高速度级单tick天量运算卡服)
    public int wirelessRange = 48;                      // 无线(WiFi)连接范围(格,同维度)
    public boolean enableThermalThrottle = false;       // 高速产热/需散热框架（默认关，可选平衡）

    // ===== 防卡顿 / 输出 =====
    public int maxSprayEntitiesPerTick = 32;  // 每tick最大喷射实体数（§8.3 防实体爆炸）
    public int coreBufferSlots = 27;          // 生产核心输出缓存槽数（满则按面板设置停机/喷射）
    public int storageTypesPerTier = 0;       // 存储核心每级类型数：0=无限类型(默认,m98)；>0=旧成长机制(原27,存储升级+1级)
    public boolean sleepWhenIdle = true;      // 无红石/缺料/堵塞/无人加载时休眠停tick（§15.3）
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
    // m219 底带再瘦身（作者圈图点名：状态/提示两坨字收进顶栏按钮，底带只剩按钮排）：
    public boolean canvasStatusOpen = false;   // 状态区展开：true=底带显示两行运行统计（顶栏"状态"钮即点即存切换）；false=收起只剩按钮排

    // ---- 单例 + 读写 ----
    private static SdzjzConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "sdzjz.json";

    public static SdzjzConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        SdzjzConfig cfg = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                cfg = GSON.fromJson(r, SdzjzConfig.class);
            } catch (IOException e) {
                cfg = null;
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

        INSTANCE = cfg;
        save(); // 回写补齐缺键 / 生成默认文件
    }

    public static void save() {
        if (INSTANCE == null) return;
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException ignored) {
        }
    }
}
