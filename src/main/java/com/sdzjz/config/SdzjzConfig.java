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
    public int configVersion = 3;

    // ===== 生产限制（照设计文档 §7.4：不用传统电力，用结构完整度/吞吐/散热 + 每tick操作预算）=====
    public long maxRecipesPerCoreTick = 65_536L;        // 单生产核心每tick最大逻辑配方次数
    public long maxRecipesPerChunkTick = 262_144L;      // 每区块每tick上限
    public long maxRecipesPerNetworkTick = 1_048_576L;  // 每玩家网络每tick上限
    public int accelMinPeriodTicks = 1;                 // 加速叠加后的最小周期下限
    public int wirelessRange = 48;                      // 无线(WiFi)连接范围(格,同维度)
    public boolean enableThermalThrottle = false;       // 高速产热/需散热框架（默认关，可选平衡）

    // ===== 防卡顿 / 输出 =====
    public int maxSprayEntitiesPerTick = 32;  // 每tick最大喷射实体数（§8.3 防实体爆炸）
    public int coreBufferSlots = 27;          // 生产核心输出缓存槽数（满则按面板设置停机/喷射）
    public int storageTypesPerTier = 0;       // 存储核心每级类型数：0=无限类型(默认,m98)；>0=旧成长机制(原27,存储升级+1级)
    public boolean sleepWhenIdle = true;      // 无红石/缺料/堵塞/无人加载时休眠停tick（§15.3）
    public int structureBlocksPerTick = 1024;         // 一键建造每tick摆放方块数(分批防卡顿)
    public boolean structureConsumeMaterials = false; // 一键建造是否消耗背包材料(默认关)

    // ===== 基调（偏硬核，全可调；越大越休闲）=====
    public double productionRateMultiplier = 1.0;

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
