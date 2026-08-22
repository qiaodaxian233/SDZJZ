package com.sdzjz.loader;

/**
 * m405 环境查询平台口——"有没有装某个模组""配置目录在哪"这类问题，各加载器答法不同（Fabric 走
 * FabricLoader、Neo/Forge 走 ModList/FMLPaths），业务侧不该知道。
 *
 * <p><b>m435 接口化（漏斗销账）</b>：门面迁 xplat，Fabric 内脏进 loader/FabricEnv。
 * 注意安装序：Sdzjz.onInitialize 里 {@code Platform.initConfigDir(Env.configDir())} 依赖本口，
 * 故 Env.install 必须排在它之前（现与 Net/Xfer 同在首段安装块）。
 */
public final class Env {

    private Env() { }

    /** 加载器要给的两个口（m435）。 */
    public interface Impl {
        boolean isModLoaded(String modId);
        java.nio.file.Path configDir();
    }

    private static Impl impl;

    /** 加载器入口首段调（重复安装直接炸出来）。 */
    public static void install(Impl i) {
        if (impl != null) throw new IllegalStateException("Env 平台实现重复安装");
        impl = i;
    }

    private static Impl req() {
        if (impl == null) throw new IllegalStateException("Env 平台实现未安装：加载器入口须先调 Env.install(...)（Fabric=Sdzjz.onInitialize 首段，必须早于 Platform.initConfigDir）");
        return impl;
    }

    /** 某模组是否已装（软兼容判定的唯一出口，m229 ProjectE 兼容层用它）。 */
    public static boolean isModLoaded(String modId) {
        return req().isModLoaded(modId);
    }

    /** 配置目录（m365 起在 mod init 首段注册进 Platform，早于任何 SdzjzConfig.get()）。 */
    public static java.nio.file.Path configDir() {
        return req().configDir();
    }
}
