package com.sdzjz.neoforge;

/** m535（F1d-1）{@link com.sdzjz.loader.Env.Impl} 的 NeoForge 实现——对位 {@code loader/FabricEnv} 两口：
 *  {@code FabricLoader.getInstance().isModLoaded} → {@code ModList.get().isLoaded}；{@code getConfigDir} → {@code FMLPaths.CONFIGDIR.get()}。 */
public final class NeoForgeEnv implements com.sdzjz.loader.Env.Impl {
    @Override
    public boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    @Override
    public java.nio.file.Path configDir() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
    }
}
