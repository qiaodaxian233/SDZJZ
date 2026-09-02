package com.sdzjz.node;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * m430 绞杀者 mA1（GraphNbt 方案 A 第一刀，docs/GraphNbt拆分方案_m429.md 作者拍板）：
 * 画布渲染子集状态对象——m275 清单认定的「画布客户端消费面全集」12+1 字段自
 * StructureCoreBlockEntity 原样搬家（声明一字未改，仅 private→public；依附注释随迁）。
 * SCBE 持唯一实例 g，SCBE 内全部引用机械前缀 g. / be.g.（逐名计数断言）。
 * m431 mA2：渲染编解码 writeRenderNbt/readRenderNbt 已迁入本类（方法体与 m428 原文逐字对拍一致，读侧仅 bumpTopo/MERGED_IDS 两注入点）。
 * 纯状态容器：零方法零逻辑，字段语义与同步节奏的注释仍以 SCBE 侧使用点为准。
 *
 * <p><b>m477（真移植 A 阶段第一刀）：本类自此两代共用一份</b>——1.20.1 世代的
 * {@code CanvasGraphState120}（132 行仿写件）整个删除，白名单挂本文件。合一前两份的实测差异：
 * 122 行逐字相同，**真世代差只有「栈↔NBT 编解码」一对**（1.21 的 {@code save(lookup)}/
 * {@code parse(lookup,·)} vs 1.20.1 的 {@code save(new CompoundTag())}/{@code of(·)}），
 * 已收进 {@link StackCodec} 世代口。lookup 形参改为**不透明代际句柄** {@code Object}
 * ——与 {@code platform.RecipeAccess} 的 level 句柄同一约法（Common/共用层只透传绝不触碰），
 * 主线调用点传 {@code registryAccess()} 自动向上转型，零改动。
 *
 * <p><b>m506（真移植 A5a）：「纯状态容器」的说法自此过期</b>——m191 画布分组六个业务方法
 * （createGroup/dissolveGroup/renameGroup/moveGroup/setNodeGroupTag/sweepGroups）自主线 SCBE
 * 整段搬入本类两代共用，BE 生命周期触点走构造器注入的 {@link #onChange}（m485 StorageLedger 手法）。
 */
public final class CanvasGraphState {

    /** m477 世代口：栈↔NBT 编解码（两代唯一的真差异）。lookup=不透明代际句柄，实现方自行转型。 */
    public interface StackCodec {
        /** 整栈存 NBT（1.21=save(lookup) 返回 Tag；1.20.1=save(new CompoundTag())）。 */
        Tag save(ItemStack s, Object lookup);
        /** 整栈读回；坏数据返回 {@link ItemStack#EMPTY}（调用方按空跳过，不炸档，两代同律）。 */
        ItemStack load(CompoundTag tag, Object lookup);
    }

    private static StackCodec codec;

    /** 加载器入口首段调（重复安装直接炸出来，ItemData m437 / NodeTags.Ident m472 同律）。 */
    public static void installCodec(StackCodec c) {
        if (codec != null) throw new IllegalStateException("CanvasGraphState 栈编解码重复安装");
        codec = c;
    }

    private static StackCodec codec() {
        if (codec == null) throw new IllegalStateException("CanvasGraphState 栈编解码未安装：加载器入口须先调 CanvasGraphState.installCodec(...)（1.21=Sdzjz.onInitialize 首段，1.20.1=RetroBootstrap 同位）");
        return codec;
    }

    /** m506（真移植 A5a）：状态变更回调——分组六方法（下方 m191 段）原在 SCBE 里以
     *  {@code setChanged(); syncToClient();} 收尾，下沉后只做一处机械替换 → {@code onChange.run()}
     *  （{@code StorageLedger} m485 同一手法）。两代 BE 各传自己的：主线=落盘+推观众快照，
     *  1.20.1=只落盘（客户端拉取式快照，无观众态——执行面差不是数据差，m505 普查记档）。 */
    private final Runnable onChange;

    /** 无参=空回调：客户端快照像（CanvasScreen120 / applyRenderSnapshot）与判官对拍用，零改动。 */
    public CanvasGraphState() { this(() -> { }); }

    /** @param onChange 图状态被分组操作改动后回调（BE 侧的 setChanged/同步）。 */
    public CanvasGraphState(Runnable onChange) { this.onChange = onChange; }

    public final java.util.List<ItemStack> machineNodes = new java.util.ArrayList<>();
    public final java.util.List<int[]> connections = new java.util.ArrayList<>(); // {from, to} 节点下标
    public final java.util.LinkedHashMap<Integer, String> groupNames = new java.util.LinkedHashMap<>(); // m191 画布分组 id→名（成员归属存各节点栈 NBT "gp"，随栈走免下标重映射）

    // ===== 存储/终端接口节点（画布右侧显示，连了几个显示几个） =====
    /** 已扫描到的接口端点：{posLong, kind}，kind 0=绑定 1=有线 2=无线 3=卫星 4=离线(仅被连线引用) 5=数据终端。 */
    public final java.util.List<long[]> storageEndpoints = new java.util.ArrayList<>();
    public final java.util.List<String> storageEndpointDims = new java.util.ArrayList<>(); // 与上表同序的维度 id
    public final java.util.Map<Long, int[]> storageNodePos = new java.util.HashMap<>();    // posLong → 画布坐标
    /** 机器↔存储 定向连线：{machineIndex, posLong, dir}，dir 0=机器→存储(产出) 1=存储→机器(供料)。 */
    public final java.util.List<long[]> storageEdges = new java.util.ArrayList<>();
    public final java.util.List<String> storageEdgeDims = new java.util.ArrayList<>();

    // 节点状态灯：0=待机 1=正常(绿) 2=阻塞/关闸(黄) 3=缺料(红)（同步节奏与 statusDirty 在 SCBE）
    public final java.util.List<Integer> nodeStatus = new java.util.ArrayList<>();
    // m178 阻塞原因（错误解释）：与 nodeStatus 平行同步，卡面黄/红灯常显人话原因
    public final java.util.List<String> nodeReason = new java.util.ArrayList<>();

    // m85：总线库存（网络前10物品，画布顶栏「存储总线（网络库存）」展示）
    public final java.util.List<String> busTopIds = new java.util.ArrayList<>();
    public final java.util.List<Long> busTopCounts = new java.util.ArrayList<>();

    // m86：实测产量快照（分钟滚动窗口的结算值；计量窗口 prodWin/prodWinStart 留 SCBE）
    public long prodPerMin = 0;

    /** m275：渲染快照子集=画布客户端消费面全集（清单依据 docs/同步拆分方案_m274.md §2 grep 实测：
     *  节点栈/连线/分组/状态灯/阻塞原因/存储端点三件套/总线库存/实测产量）。
     *  存档 writeNbt 与 flushCanvasSnapshot 共用。 */
    public void writeRenderNbt(CompoundTag nbt, Object lookup) { // m477 lookup=不透明代际句柄
        ListTag mn = new ListTag();
        for (ItemStack s : machineNodes) if (!s.isEmpty()) mn.add(codec().save(s, lookup)); // m477 世代口
        nbt.put("machineNodes", mn);
        int[] flat = new int[connections.size() * 2];
        for (int i = 0; i < connections.size(); i++) { flat[i * 2] = connections.get(i)[0]; flat[i * 2 + 1] = connections.get(i)[1]; }
        nbt.putIntArray("connections", flat);
        CompoundTag grp = new CompoundTag(); // m191 分组元数据 id→名（成员归属在各节点栈里随 machineNodes 落盘）
        for (var ge : groupNames.entrySet()) grp.putString(Integer.toString(ge.getKey()), ge.getValue());
        nbt.put("groups", grp);
        int[] nst = new int[machineNodes.size()];
        for (int i = 0; i < nst.length; i++) nst[i] = i < nodeStatus.size() ? nodeStatus.get(i) : 0;
        nbt.putIntArray("nodeStat", nst);
        ListTag nwl = new ListTag(); // m178 阻塞原因（与 nodeStat 同序；转绿清空）
        for (int i = 0; i < nst.length; i++) nwl.add(net.minecraft.nbt.StringTag.valueOf(i < nodeReason.size() ? nodeReason.get(i) : ""));
        nbt.put("nodeWhy", nwl);
        ListTag eps = new ListTag(); // 存储端点（同步给画布：连了几个显示几个）
        for (int i = 0; i < storageEndpoints.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", storageEndpoints.get(i)[0]);
            c.putInt("k", (int) storageEndpoints.get(i)[1]);
            c.putString("d", storageEndpointDims.get(i));
            eps.add(c);
        }
        nbt.put("storEnds", eps);
        ListTag seg = new ListTag(); // 机器↔存储 定向连线
        for (int i = 0; i < storageEdges.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putInt("m", (int) storageEdges.get(i)[0]);
            c.putLong("p", storageEdges.get(i)[1]);
            c.putInt("r", (int) storageEdges.get(i)[2]);
            c.putString("d", storageEdgeDims.get(i));
            seg.add(c);
        }
        nbt.put("storEdges", seg);
        CompoundTag spn = new CompoundTag(); // 存储节点画布坐标
        for (var en : storageNodePos.entrySet()) spn.putIntArray(Long.toString(en.getKey()), en.getValue());
        nbt.put("storNodePos", spn);
        ListTag bt = new ListTag(); // m85 总线库存
        for (int i = 0; i < busTopIds.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putString("i", busTopIds.get(i));
            c.putLong("n", busTopCounts.get(i));
            bt.add(c);
        }
        nbt.put("busTop", bt);
        nbt.putLong("prodPM", prodPerMin); // m86 实测产量
    }

    /** m275：渲染子集读入（与 writeRenderNbt 严格对偶）——存档 readNbt 与客户端 applyRenderSnapshot 共用。 */
    public void readRenderNbt(CompoundTag nbt, Object lookup,
                              java.util.Map<String, String> mergedIds, Runnable onTopoChange) { // m431 注入：仅有的两个 SCBE 跨界触点
        machineNodes.clear();
        onTopoChange.run(); // m179 bumpTopo 注入
        ListTag mn = nbt.getList("machineNodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < mn.size(); i++) {
            CompoundTag mc = mn.getCompound(i);
            String mid = mergedIds.get(mc.getString("id")); // m143：旧子机器id→合并机（不映射会整节点丢失，
            if (mid != null) mc.putString("id", mid);        // 且 inputBuf/nodeStatus 同序列表随之错位）
            ItemStack t = codec().load(mc, lookup); // m477 世代口（坏数据=EMPTY，静默跳过不炸档，两代同律）
            if (!t.isEmpty()) machineNodes.add(t);
        }
        connections.clear();
        int[] flat = nbt.getIntArray("connections");
        int nodeCount = machineNodes.size();
        for (int i = 0; i + 1 < flat.length; i += 2) { // m459 修④（m477 自 1.20.1 推广到两代）：坏档/恶意快照的
            int a = flat[i], b = flat[i + 1];          // 越界或自连下标读侧即剪——蓝本原只在屏侧护，而路由/摘节点
            if (a < 0 || b < 0 || a >= nodeCount || b >= nodeCount || a == b) continue; // 簿记都要吃这张表，读侧剪一次处处安全
            connections.add(new int[]{a, b});
        }
        groupNames.clear(); // m191 分组元数据；键是 gid 的十进制串，坏键跳过不炸读档
        CompoundTag grp = nbt.getCompound("groups");
        for (String k : grp.getAllKeys()) {
            try { groupNames.put(Integer.parseInt(k), grp.getString(k)); } catch (NumberFormatException ignored) {}
        }
        nodeStatus.clear();
        for (int v : nbt.getIntArray("nodeStat")) nodeStatus.add(v);
        nodeReason.clear(); // m178
        ListTag nwl178 = nbt.getList("nodeWhy", Tag.TAG_STRING);
        for (int i = 0; i < nwl178.size(); i++) nodeReason.add(nwl178.getString(i));
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
            int m = c.getInt("m"), r = c.getInt("r");
            if (m < 0 || m >= machineNodes.size() || (r != 0 && r != 1)) continue; // m459 修④（m477 推广两代）：机器下标/方向同剪
            storageEdges.add(new long[]{m, c.getLong("p"), r});
            storageEdgeDims.add(c.getString("d"));
        }
        storageNodePos.clear();
        CompoundTag spn = nbt.getCompound("storNodePos");
        for (String k : spn.getAllKeys()) {
            int[] v = spn.getIntArray(k);
            if (v.length == 2 || v.length == 3) try { storageNodePos.put(Long.parseLong(k), v); } catch (NumberFormatException ignored) {} // m265 三元=画布放置(带标记位)，二元=遗留停靠
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

    // ===== m191 画布分组：成员归属在节点栈 NBT "gp"（随栈走，detachNode 的下标移位天然无关），
    // 这里只管 id→名元数据 + 组操作；配置 canvasGroupsEnabled 总开关在接收器侧把门。 =====
    // m506（真移植 A5a）：本段自主线 StructureCoreBlockEntity 2464~2540 整段搬入，两代共用一份；
    // 机械替换只有三类：①`g.machineNodes/g.groupNames` → 本类字段 ②`setChanged(); syncToClient();`
    // → `onChange.run()` ③`sweepGroups` 放开为 public（两代 detachNode 都要调）。其余一个字未改，
    // 含 m269 long 加法、±100000 单包钳幅、24 字组名钳长与 sweepGroups 的 m431b 注。

    /** m265 画布落位坐标钳制（±1,000,000，防伪造包写极端值进 NBT/参与几何运算溢出）。
     *  m269 升 long 入参：moveGroup 用 long 加法防 int 溢出后直接喂进来；原 int 调用点自动拓宽零改动。
     *  m506：自 SCBE 搬入本类（moveGroup 随行），SCBE 原位留同签名垫片零调用点改动。 */
    public static int clampCanvas(long v) { return (int) Math.max(-1_000_000L, Math.min(1_000_000L, v)); }

    /** 建组：≥2 个合法下标才成组；成员先脱旧组再入新组（一台机器只能在一个组）。name 空=自动"组N"。 */
    public void createGroup(java.util.List<Integer> members, String name) {
        java.util.LinkedHashSet<Integer> ms = new java.util.LinkedHashSet<>();
        for (int i : members) if (i >= 0 && i < machineNodes.size()) ms.add(i);
        if (ms.size() < 2) return;
        int gid = 1;
        for (int k : groupNames.keySet()) gid = Math.max(gid, k + 1);
        String nm = name == null ? "" : name.trim();
        if (nm.length() > 24) nm = nm.substring(0, 24);
        groupNames.put(gid, nm.isEmpty() ? "组" + gid : nm);
        for (int i : ms) setNodeGroupTag(machineNodes.get(i), gid);
        sweepGroups(); // 成员被挖走的旧组可能只剩0/1台，顺手清
        onChange.run();
    }

    /** 解散组：成员脱组标记 + 元数据删除。机器/连线原样不动（分组纯视觉，不碰拓扑）。 */
    public void dissolveGroup(int gid) {
        if (groupNames.remove(gid) == null) return;
        for (ItemStack s : machineNodes) if (com.sdzjz.node.NodeTags.nodeGroup(s) == gid) setNodeGroupTag(s, -1);
        onChange.run();
    }

    /** 重命名组（长度钳 24，空名不接受）。 */
    public void renameGroup(int gid, String name) {
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty() || !groupNames.containsKey(gid)) return;
        if (nm.length() > 24) nm = nm.substring(0, 24);
        groupNames.put(gid, nm);
        onChange.run();
    }

    /** 组整体位移：全成员坐标加同一增量，改完只同步一次（防 m128F3 式 N 连发全量同步）。 */
    public void moveGroup(int gid, int dx, int dy) {
        if (!groupNames.containsKey(gid)) return;
        dx = Math.max(-100000, Math.min(100000, dx)); // 防伪造包把整组甩进天文坐标
        dy = Math.max(-100000, Math.min(100000, dy));
        boolean any = false;
        for (ItemStack s : machineNodes) {
            if (com.sdzjz.node.NodeTags.nodeGroup(s) != gid) continue;
            CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
            // m269 long 加法+终值钳幅：单次 dx 虽已钳 ±1e5，但反复发包每次+1e5 累加 int 会溢出（审计点名）
            n.putInt("nx", clampCanvas((n.contains("nx") ? (long) n.getInt("nx") : 0L) + dx));
            n.putInt("ny", clampCanvas((n.contains("ny") ? (long) n.getInt("ny") : 0L) + dy));
            com.sdzjz.item.ItemData.write(s, n);
            any = true;
        }
        if (!any) return;
        onChange.run();
    }

    /** 写/清节点栈上的组标记（gid<0=清除）。 */
    private void setNodeGroupTag(ItemStack s, int gid) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        if (gid < 0) n.remove("gp"); else n.putInt("gp", gid);
        com.sdzjz.item.ItemData.write(s, n);
    }

    /** 组一致性清扫：成员<2 的组解散（1 台不成组）+ 无元数据的孤儿 gp 标记剥除。detachNode 与组操作后调用。
     *  不自带 onChange——调用方（detachNode/组操作）自己收尾，与主线原文同律。 */
    public void sweepGroups() {
        java.util.HashMap<Integer, Integer> cnt = new java.util.HashMap<>();
        for (ItemStack s : machineNodes) {
            int g = com.sdzjz.node.NodeTags.nodeGroup(s);
            if (g >= 0) cnt.merge(g, 1, Integer::sum);
        }
        groupNames.keySet().removeIf(g -> cnt.getOrDefault(g, 0) < 2);
        for (ItemStack s : machineNodes) {
            int g = com.sdzjz.node.NodeTags.nodeGroup(s);
            if (g >= 0 && !groupNames.containsKey(g)) setNodeGroupTag(s, -1); // m431b：主线时局部 int g 遮蔽字段 g 须 this. 限定；m506 下沉后本类无字段 g，限定词随之去掉，局部名原样保留
        }
    }
}
