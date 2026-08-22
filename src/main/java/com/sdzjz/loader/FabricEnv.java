package com.sdzjz.loader;

/** m435：{@link Env.Impl} 的 Fabric 实现——m405 门面里的两段 Fabric 内脏原样搬来。 */
public final class FabricEnv implements Env.Impl {

    @Override
    public boolean isModLoaded(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public java.nio.file.Path configDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
    }
}
