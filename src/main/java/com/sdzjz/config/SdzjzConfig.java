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
    public int configVersion = 1;

    // ===== 电力系统（Phase 2 起用，先占位）=====
    public boolean enablePower = true;
    public long controllerEnergyCapacity = 1_000_000L; // 单控制器电网缓冲上限
    public double accelEnergyExponent = 1.35;           // 加速模块耗电超线性指数（>1 = 越加速越费电）

    // ===== 防卡顿 =====
    public int globalTickInterval = 20;   // 面板产出统一节拍（tick）
    public int perPanelBufferSlots = 27;  // 单面板内部缓冲槽数（满则暂停，绝不掉落物）
    public int maxPanelsPerController = 64;

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
