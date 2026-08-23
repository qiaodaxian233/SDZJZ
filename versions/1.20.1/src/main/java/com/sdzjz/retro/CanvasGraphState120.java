package com.sdzjz.retro;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * m454（C2-③）：画布渲染子集状态——语义蓝本=xplat {@code CanvasGraphState}（m430/m431），字段
 * 与 NBT 键**逐字同名同布局**（machineNodes/connections/groups/nodeStat/nodeWhy/storEnds/
 * storEdges/storNodePos/busTop/prodPM——DFU 升档红利口径同 m443：1.20.5 原版升档把节点栈 tag
 * 收进组件，存档升 1.21.1 后本布局与蓝本逐键对上）。蓝本不可挂（唯一触点=HolderLookup 两签名，
 * m450 普查表），世代新写对位：save(lookup)→save(new CompoundTag())、parse(lookup,·).ifPresent→
 * of(·)+isEmpty 判（失败即 EMPTY 静默跳过，不炸档）。
 *
 * <p>读侧两注入点保形（m431 原样）：mergedIds=m143 旧子机器 id→合并机映射——1.20.1 无史前存档，
 * 常态传空表（Map.of()），但参数保留：将来若在 1.20.1 世代内再发生机器合并，接线点现成；
 * onTopoChange=拓扑翻代回调（消费方 C2-④ 接 bumpTopo）。纯状态容器零业务逻辑，蓝本同性。
 */
final class CanvasGraphState120 {

    final java.util.List<ItemStack> machineNodes = new java.util.ArrayList<>();
    final java.util.List<int[]> connections = new java.util.ArrayList<>(); // {from, to} 节点下标
    final java.util.LinkedHashMap<Integer, String> groupNames = new java.util.LinkedHashMap<>(); // m191 分组 id→名（成员归属存各节点栈 NBT "gp"，随栈走免下标重映射）

    /** 已扫描到的接口端点：{posLong, kind}，kind 口径同蓝本（0..5）。 */
    final java.util.List<long[]> storageEndpoints = new java.util.ArrayList<>();
    final java.util.List<String> storageEndpointDims = new java.util.ArrayList<>();
    final java.util.Map<Long, int[]> storageNodePos = new java.util.HashMap<>();
    /** 机器↔存储 定向连线：{machineIndex, posLong, dir}，dir 0=产出 1=供料。 */
    final java.util.List<long[]> storageEdges = new java.util.ArrayList<>();
    final java.util.List<String> storageEdgeDims = new java.util.ArrayList<>();

    final java.util.List<Integer> nodeStatus = new java.util.ArrayList<>(); // 0待机 1绿 2黄 3红
    final java.util.List<String> nodeReason = new java.util.ArrayList<>();  // m178 与上表平行

    final java.util.List<String> busTopIds = new java.util.ArrayList<>();   // m85 总线库存
    final java.util.List<Long> busTopCounts = new java.util.ArrayList<>();

    long prodPerMin = 0; // m86

    /** 键序与蓝本 writeRenderNbt 逐段对齐（存档与画布快照共用，蓝本同注）。 */
    void writeRenderNbt(CompoundTag nbt) {
        ListTag mn = new ListTag();
        for (ItemStack s : machineNodes) if (!s.isEmpty()) mn.add(s.save(new CompoundTag())); // 1.20.1 对位 save(lookup)
        nbt.put("machineNodes", mn);
        int[] flat = new int[connections.size() * 2];
        for (int i = 0; i < connections.size(); i++) { flat[i * 2] = connections.get(i)[0]; flat[i * 2 + 1] = connections.get(i)[1]; }
        nbt.putIntArray("connections", flat);
        CompoundTag grp = new CompoundTag(); // m191 分组元数据
        for (var ge : groupNames.entrySet()) grp.putString(Integer.toString(ge.getKey()), ge.getValue());
        nbt.put("groups", grp);
        int[] nst = new int[machineNodes.size()];
        for (int i = 0; i < nst.length; i++) nst[i] = i < nodeStatus.size() ? nodeStatus.get(i) : 0;
        nbt.putIntArray("nodeStat", nst);
        ListTag nwl = new ListTag(); // m178 阻塞原因（与 nodeStat 同序）
        for (int i = 0; i < nst.length; i++) nwl.add(StringTag.valueOf(i < nodeReason.size() ? nodeReason.get(i) : ""));
        nbt.put("nodeWhy", nwl);
        ListTag eps = new ListTag();
        for (int i = 0; i < storageEndpoints.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", storageEndpoints.get(i)[0]);
            c.putInt("k", (int) storageEndpoints.get(i)[1]);
            c.putString("d", storageEndpointDims.get(i));
            eps.add(c);
        }
        nbt.put("storEnds", eps);
        ListTag seg = new ListTag();
        for (int i = 0; i < storageEdges.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putInt("m", (int) storageEdges.get(i)[0]);
            c.putLong("p", storageEdges.get(i)[1]);
            c.putInt("r", (int) storageEdges.get(i)[2]);
            c.putString("d", storageEdgeDims.get(i));
            seg.add(c);
        }
        nbt.put("storEdges", seg);
        CompoundTag spn = new CompoundTag();
        for (var en : storageNodePos.entrySet()) spn.putIntArray(Long.toString(en.getKey()), en.getValue());
        nbt.put("storNodePos", spn);
        ListTag bt = new ListTag(); // m85
        for (int i = 0; i < busTopIds.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putString("i", busTopIds.get(i));
            c.putLong("n", busTopCounts.get(i));
            bt.add(c);
        }
        nbt.put("busTop", bt);
        nbt.putLong("prodPM", prodPerMin); // m86
    }

    /** 与 writeRenderNbt 严格对偶（蓝本读侧逐段对齐；两注入点见类注）。 */
    void readRenderNbt(CompoundTag nbt, java.util.Map<String, String> mergedIds, Runnable onTopoChange) {
        machineNodes.clear();
        onTopoChange.run(); // m179 bumpTopo 注入点保形
        ListTag mn = nbt.getList("machineNodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < mn.size(); i++) {
            CompoundTag mc = mn.getCompound(i);
            String mid = mergedIds.get(mc.getString("id")); // m143 合并机映射（1.20.1 常态空表，见类注）
            if (mid != null) mc.putString("id", mid);
            ItemStack t = ItemStack.of(mc); // 1.20.1 对位 parse().ifPresent
            if (!t.isEmpty()) machineNodes.add(t);
        }
        connections.clear();
        int[] flat = nbt.getIntArray("connections");
        int nodeCount = machineNodes.size();
        for (int i = 0; i + 1 < flat.length; i += 2) { // m459 修④：坏档/恶意快照的越界或自连下标读侧即剪
            int a = flat[i], b = flat[i + 1];         // ——蓝本只在屏侧护，本世代 detach 簿记与 C2-⑤ tick 都要吃
            if (a < 0 || b < 0 || a >= nodeCount || b >= nodeCount || a == b) continue; // 这表，读侧剪一次处处安全（加固记档）
            connections.add(new int[]{a, b});
        }
        groupNames.clear(); // 坏键跳过不炸读档（蓝本同注）
        CompoundTag grp = nbt.getCompound("groups");
        for (String k : grp.getAllKeys()) {
            try { groupNames.put(Integer.parseInt(k), grp.getString(k)); } catch (NumberFormatException ignored) { }
        }
        nodeStatus.clear();
        for (int v : nbt.getIntArray("nodeStat")) nodeStatus.add(v);
        nodeReason.clear(); // m178
        ListTag nwl = nbt.getList("nodeWhy", Tag.TAG_STRING);
        for (int i = 0; i < nwl.size(); i++) nodeReason.add(nwl.getString(i));
        storageEndpoints.clear();
        storageEndpointDims.clear();
        ListTag eps = nbt.getList("storEnds", Tag.TAG_COMPOUND);
        for (int i = 0; i < eps.size(); i++) {
            CompoundTag c = eps.getCompound(i);
            storageEndpoints.add(new long[]{c.getLong("p"), c.getInt("k")});
            storageEndpointDims.add(c.getString("d"));
        }
        storageEdges.clear();
        storageEdgeDims.clear();
        ListTag seg = nbt.getList("storEdges", Tag.TAG_COMPOUND);
        for (int i = 0; i < seg.size(); i++) {
            CompoundTag c = seg.getCompound(i);
            int m = c.getInt("m");
            int r = c.getInt("r");
            if (m < 0 || m >= machineNodes.size() || (r != 0 && r != 1)) continue; // m459 修④：机器下标/方向读侧同剪
            storageEdges.add(new long[]{m, c.getLong("p"), r});
            storageEdgeDims.add(c.getString("d"));
        }
        storageNodePos.clear();
        CompoundTag spn = nbt.getCompound("storNodePos");
        for (String k : spn.getAllKeys()) {
            int[] v = spn.getIntArray(k);
            if (v.length == 2 || v.length == 3) try { storageNodePos.put(Long.parseLong(k), v); } catch (NumberFormatException ignored) { } // m265 三元=画布放置，二元=遗留停靠
        }
        busTopIds.clear();
        busTopCounts.clear();
        ListTag btr = nbt.getList("busTop", Tag.TAG_COMPOUND); // m85
        for (int i = 0; i < btr.size(); i++) {
            busTopIds.add(btr.getCompound(i).getString("i"));
            busTopCounts.add(btr.getCompound(i).getLong("n"));
        }
        prodPerMin = nbt.getLong("prodPM"); // m86
    }
}
