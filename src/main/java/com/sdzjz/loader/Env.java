package com.sdzjz.loader;

/**
 * m405 环境查询平台口——"有没有装某个模组""配置目录在哪"这类问题，各加载器答法不同（Fabric 走
 * FabricLoader、Neo/Forge 走 ModList/FMLPaths），业务侧不该知道。
 */
public final class Env {

    private Env() { }

    /** 某模组是否已装（软兼容判定的唯一出口，m229 ProjectE 兼容层用它）。 */
    public static boolean isModLoaded(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
    }

    /** 配置目录（m365 起在 mod init 第一行注册进 Platform，早于任何 SdzjzConfig.get()）。 */
    public static java.nio.file.Path configDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
    }
}
