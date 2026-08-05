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
    public int configVersion = 9; // m185 画布缩放上下限；m186 缩放平滑动效；m191 画布分组开关；m193 分组连线归并开关

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
    public boolean coreChunkLoading = true;   // m133 结构核心开机=强制加载自身区块(重启自恢复)+存储端点区块(有期票)；关=离开即停产(旧行为)
    public int structureBlocksPerTick = 1024;         // 一键建造每tick摆放方块数(分批防卡顿)
    public boolean structureConsumeMaterials = false; // 一键建造是否消耗背包材料(默认关)

    // ===== 基调（偏硬核，全可调；越大越休闲）=====
    public double productionRateMultiplier = 1.0;

    // ===== 画布（客户端视图数值，读本机配置文件，不参与服务端逻辑）=====
    public double canvasZoomMin = 0.05;   // m185 画布缩放下限（0.05=5%；旧硬编码 0.4 放开，想更小自己改）
    public double canvasZoomMax = 8.0;    // m185 画布缩放上限（8=800%；旧硬编码 2.5 放开）
    public boolean canvasSmoothZoom = true; // m186 画布缩放平滑动效（指数缓动+指哪缩哪；false=瞬时跳变旧行为）
    public boolean canvasGroupsEnabled = true; // m191 画布机器打组（框选成组/组框拖动/连线归并）；关=服务端拒收组操作+客户端不画组框
    public boolean canvasGroupBundleWires = true; // m193 跨组界连线归并成一条(×N徽章)；false=每条线照旧各画各的（纯客户端渲染，不碰数据）

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
