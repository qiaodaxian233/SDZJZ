package com.sdzjz.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * m381 区块模板库——全局一份 SavedData 挂主世界（m296 声明表/m311 仓位账同刀法，
 * 方案 m379 决策1：模板本体服务端权威，"区块数据核心"物品只揣 UUID+摘要——全量塞物品组件
 * 被一票否决：随背包全量同步客户端，包体/存档双爆炸）。
 * 模板 NBT 形制（储存器 m381 写 / 复制器 m382 读，唯一权威在此注释）：
 *   ox/oz(int 原区块) dim(string) total(long 可重建方块数)
 *   pal: NbtList<CompoundTag>（NbtHelper.fromBlockState 全状态含属性）
 *   secs: CompoundTag{ "<sectionY>": int[4096] }（段内序=(y&15)*256+lx*16+lz，值=调色板下标+1，0=不建；全空段缺席）
 *   bom: CompoundTag{ 物品id: long }（asItem 口径=重建收料唯一权威；模板不变量=每个入模板的位都可付可建）
 * 封顶 chunkTemplateMaxCount 拒存出声；模板=玩家资产，本类只增删查不擅动（m379 待拍板缺省：不自动核销，清点命令归 m383）。
 */
public class ChunkTemplateStore extends SavedData {

    private final Map<UUID, CompoundTag> templates = new HashMap<>();

    public static final SavedData.Factory<ChunkTemplateStore> TYPE = new SavedData.Factory<>(
            ChunkTemplateStore::new, ChunkTemplateStore::read, null);

    public static ChunkTemplateStore read(CompoundTag nbt, HolderLookup.Provider lookup) {
        ChunkTemplateStore s = new ChunkTemplateStore();
        CompoundTag m = nbt.getCompound("tpls");
        for (String k : m.getKeys()) {
            try {
                s.templates.put(UUID.fromString(k), m.getCompound(k));
            } catch (IllegalArgumentException ignored) { // 坏 UUID 键丢弃（m273 读入校验同律）
            }
        }
        return s;
    }

    @Override
    public CompoundTag writeNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag m = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> e : templates.entrySet()) m.put(e.getKey().toString(), e.getValue());
        nbt.put("tpls", m);
        return nbt;
    }

    public static ChunkTemplateStore of(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "sdzjz_chunk_templates");
    }

    /** 入库：封顶即拒（返 null，调用方红灯出声）；成功返新 UUID 并落盘置脏。 */
    public UUID put(CompoundTag tpl, int cap) {
        if (templates.size() >= Math.max(1, cap)) return null;
        UUID u = UUID.randomUUID();
        templates.put(u, tpl);
        markDirty();
        return u;
    }

    /** 取模板（复制器 m382 读口）；不存在=null（核心成孤儿票，调用方红灯出声）。 */
    public CompoundTag get(UUID u) { return templates.get(u); }

    public boolean remove(UUID u) {
        boolean hit = templates.remove(u) != null;
        if (hit) markDirty();
        return hit;
    }

    public int count() { return templates.size(); }
}
