package com.sdzjz.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/** m537（F1d-2b）卫星节点模型的 **Fabric 1.21.1 壳**：原 {@code SatelliteNodeModel.register()}（m151 ModelLoadingPlugin 句）整段搬来，
 *  模型本体留在 {@code SatelliteNodeModel}（纯原版 UnbakedModel，两加载器共编）。NeoForge 对位=NeoForgeClientEntry 的 ModelEvent.ModifyBakingResult。 */
public final class FabricSatelliteModel {
    private FabricSatelliteModel() { }

    /** 客户端入口调用：拦截 sdzjz:block/satellite_node 的模型加载。 */
    public static void register() {
        ModelLoadingPlugin.register(ctx -> ctx.modifyModelOnLoad().register((original, context) -> {
            // m151-3 编译修正=类注释备忘④：1.21.1 Fabric 把 id 拆成 resourceId()（文件模型）/
            // topLevelId()（blockstate/物品顶层，ModelIdentifier），二者恰一非空。我们拦
            // blockstate 引用的文件模型 sdzjz:block/satellite_node → 走 resourceId()。
            ResourceLocation id = context.resourceId();
            if (id != null && "sdzjz".equals(id.getNamespace()) && id.getPath().endsWith("block/satellite_node")) {
                UnbakedModel shell = SatelliteNodeModel.loadShell(); // m537：原 loadGeo()+new SatelliteNodeModel(...) 两句收进本体
                if (shell != null) return shell;
            }
            return original;
        }));
    }
}
