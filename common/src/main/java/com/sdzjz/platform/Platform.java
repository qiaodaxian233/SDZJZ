package com.sdzjz.platform;

/**
 * 平台服务定位器（多版本代际架构 Phase 1）：代际引导端（Legacy=Sdzjz.onInitialize）在最早时机注册，
 * Common 侧只读。未注册即用=硬失败带指路信息（比 NPE 好查十倍）。
 * 不做成可热替换：注册一次定终身，杜绝运行期换实现的幽灵态。
 */
public final class Platform {
    private static RecipeAccess recipes;
    private static java.nio.file.Path configDir; // m365 配置目录（JDK 类型，Common 安全）

    private Platform() { }

    public static void initRecipes(RecipeAccess r) {
        if (recipes != null) throw new IllegalStateException("RecipeAccess 已注册，禁止二次注册");
        recipes = r;
    }

    public static void initConfigDir(java.nio.file.Path p) {
        if (configDir != null) throw new IllegalStateException("configDir 已注册，禁止二次注册");
        configDir = p;
    }

    public static java.nio.file.Path configDir() {
        if (configDir == null) throw new IllegalStateException("configDir 未注册：代际引导端必须在 mod init 第一行注册（早于任何 SdzjzConfig.get()）");
        return configDir;
    }

    public static RecipeAccess recipes() {
        if (recipes == null) throw new IllegalStateException("RecipeAccess 未注册：代际引导端必须在 mod init 注册（Legacy=Sdzjz.onInitialize）");
        return recipes;
    }
}
