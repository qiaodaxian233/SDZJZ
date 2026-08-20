package com.sdzjz.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * m151 卫星节点自定义模型：bbmodel 是 free 格式（任意欧拉角+抛物面网格 3 件），原版 JSON
 * 装不下——几何已由 python 离线全烘成平面 quad 表（models/block/satellite_node_geo.json，
 * 含元素旋转/网格三角补角/锅面双面/UV 归一），Java 侧只做瘦壳：读表→打包顶点→BakedQuad。
 * 全部 quad 进 null-face 桶（斜面几何不做邻面剔除）。geo 读取/解析失败=返回原模型不炸游戏。
 *
 * 【编译修正备忘（本沙箱无 MC 依赖盲写，若报错按此对表）】
 * ① BakedQuad 构造：1.21.1 Yarn 应为 (int[] vertexData, int tintIndex, Direction face,
 *    TextureAtlasSprite sprite, boolean shade)；若多要 lightEmission(int) 补 0。
 * ② TextureAtlasSprite#getFrameU/getFrameV：入参 0..1（u16/16f 已除好）；若签名是 0..16 制则去掉 /16f。
 * ③ UnbakedModel 三方法名以 Yarn 1.21.1 为准：getModelDependencies/setParents/bake。
 * ④ [m151-3 已命中修正] OnLoad.Context 无 id()——1.21.1 拆为 resourceId()/topLevelId()，文件模型走前者。
 */
public final class SatelliteNodeModel implements UnbakedModel {
    private static final ResourceLocation MODEL_ID = ResourceLocation.of("sdzjz", "block/satellite_node");
    private static final ResourceLocation GEO_ID = ResourceLocation.of("sdzjz", "models/block/satellite_node_geo.json");
    private static final Material ATLAS = new Material(
            TextureAtlas.BLOCK_ATLAS_TEXTURE, ResourceLocation.of("sdzjz", "block/satellite_node_atlas"));
    private static final Material JOINT = new Material(
            TextureAtlas.BLOCK_ATLAS_TEXTURE, ResourceLocation.of("sdzjz", "block/satellite_dish_joint"));

    /** m156：BER 接管渲染时静态模型烘空壳（只留粒子 sprite）——否则双重渲染。
     *  BER 若编译/运行出问题，把这里改 false 即回 m151 静态渲染兜底。 */
    public static final boolean BER_TAKEOVER = true;

    private final JsonArray quads;

    private SatelliteNodeModel(JsonArray quads) { this.quads = quads; }

    /** 客户端入口调用：拦截 sdzjz:block/satellite_node 的模型加载。 */
    public static void register() {
        ModelLoadingPlugin.register(ctx -> ctx.modifyModelOnLoad().register((original, context) -> {
            // m151-3 编译修正=类注释备忘④：1.21.1 Fabric 把 id 拆成 resourceId()（文件模型）/
            // topLevelId()（blockstate/物品顶层，ModelIdentifier），二者恰一非空。我们拦
            // blockstate 引用的文件模型 sdzjz:block/satellite_node → 走 resourceId()。
            ResourceLocation id = context.resourceId();
            if (id != null && "sdzjz".equals(id.getNamespace()) && id.getPath().endsWith("block/satellite_node")) {
                JsonArray geo = loadGeo();
                if (geo != null) return new SatelliteNodeModel(BER_TAKEOVER ? new JsonArray() : geo);
            }
            return original;
        }));
    }

    private static JsonArray loadGeo() {
        try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(GEO_ID).getInputStream()) {
            var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (root.isJsonObject()) return root.getAsJsonObject().getAsJsonArray("quads"); // m156 v2 格式
            return root.getAsJsonArray();
        } catch (Exception e) {
            com.sdzjz.Sdzjz.LOGGER.warn("卫星节点 geo 读取失败，维持原模型: {}", e.toString());
            return null;
        }
    }

    @Override public Collection<ResourceLocation> getModelDependencies() { return Collections.emptyList(); }
    @Override public void setParents(Function<ResourceLocation, UnbakedModel> modelLoader) {}

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter, ModelState settings) {
        TextureAtlasSprite atlas = textureGetter.apply(ATLAS);
        TextureAtlasSprite joint = textureGetter.apply(JOINT);
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (var el : quads) {
            JsonArray q = el.getAsJsonArray();
            TextureAtlasSprite spr = q.get(1).getAsString().endsWith("dish_joint") ? joint : atlas; // m156 v2: 0=分组 1=贴图
            float nx = q.get(2).getAsFloat(), ny = q.get(3).getAsFloat(), nz = q.get(4).getAsFloat();
            int packedN = ((int) (nx * 127) & 0xFF) | (((int) (ny * 127) & 0xFF) << 8) | (((int) (nz * 127) & 0xFF) << 16);
            int[] data = new int[32];
            for (int v = 0; v < 4; v++) {
                int base = 5 + v * 5; // m156 v2 索引+1
                float x = q.get(base).getAsFloat() / 16f, y = q.get(base + 1).getAsFloat() / 16f, z = q.get(base + 2).getAsFloat() / 16f;
                float u = q.get(base + 3).getAsFloat(), vv = q.get(base + 4).getAsFloat();
                int o = v * 8;
                data[o] = Float.floatToRawIntBits(x);
                data[o + 1] = Float.floatToRawIntBits(y);
                data[o + 2] = Float.floatToRawIntBits(z);
                data[o + 3] = 0xFFFFFFFF; // 白，光照着色交给渲染管线
                data[o + 4] = Float.floatToRawIntBits(spr.getFrameU(u / 16f));
                data[o + 5] = Float.floatToRawIntBits(spr.getFrameV(vv / 16f));
                data[o + 6] = 0;          // lightmap 由方块光决定
                data[o + 7] = packedN;
            }
            out.add(new BakedQuad(data, -1, faceOf(nx, ny, nz), spr, true));
        }
        return new Baked(out, atlas);
    }

    private static Direction faceOf(float nx, float ny, float nz) {
        return Direction.getFacing(nx, ny, nz);
    }

    private record Baked(List<BakedQuad> all, TextureAtlasSprite particle) implements BakedModel {
        @Override public List<BakedQuad> getQuads(BlockState state, Direction face, RandomSource random) {
            return face == null ? all : Collections.emptyList(); // 不做邻面剔除：斜面几何塞方向桶会被邻方块错误剔掉
        }
        @Override public boolean useAmbientOcclusion() { return false; } // 薄件斜面吃 AO 会出黑斑
        @Override public boolean hasDepth() { return true; }
        @Override public boolean isSideLit() { return true; }
        @Override public boolean isBuiltin() { return false; }
        @Override public TextureAtlasSprite getParticleSprite() { return particle; }
        @Override public ItemTransforms getTransformation() { return ItemTransforms.NONE; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
