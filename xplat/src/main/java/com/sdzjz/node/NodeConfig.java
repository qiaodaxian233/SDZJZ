package com.sdzjz.node;

import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.machine.MachineDef;
import com.sdzjz.machine.Machines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * m541（真移植·1.20.1 结构核心补全第一刀）：画布节点**配置操作层**两代共用——主线
 * {@code StructureCoreBlockEntity} 的 {@code togglePause / toggleSwitch / toggleFilterEntry /
 * setSensorConfig / setNodeTarget} 五方法整段搬入（m506 分组六方法同一手法）。这五个方法是
 * 客户端五包（NodePause / NodeSwitch / NodeFilter / NodeSensor / NodeTarget）的服务端落点，
 * 1.20.1 此前一个都没有：逻辑节点（开关/抽取/传感/过滤/垃圾桶）的服务端逻辑 m473~m499 早就在
 * 共用件里跑，玩家却没有任何入口去设置它们——作者「结构核心里很多功能没做全」点的正是这层。
 *
 * <p>只做三类机械替换（其余逐句同原文，含注释刀号）：
 * ①身份判定 {@code instanceof AutoCrafterItem/ChunkFilterItem/VoidProcessorItem/CropFarmItem/…}
 *   → {@link NodeTags#defOf} 与 {@link Machines} 常量引用同一性（m472 已证两代通用；
 *   CropFarmItem 在主线同时挂 CROP_FARM 与 MEGA_CROP_FARM 两个 def，两者都认）；
 * ②{@code setChanged(); syncToClient();} 收尾 → {@link Host#changed()}（主线=原两句；1.20.1=setChanged，
 *   快照由接收器直推——执行面差非数据差，m506 同律）；
 * ③{@code level} → {@link Host#level()} 不透明代际句柄（CraftPlanner/EnchantPlanner 本就吃 Object）。
 *
 * <p><b>世代口=机器专属哨兵与目标校验</b>：toggleFilterEntry 里 #zy/#zrd/#bfx/#bfl/#zsbd/#zm/#zw/#zs
 * 七段与 setNodeTarget 的 trade/dup/seal 三项校验依赖 {@code ChunkFilterItem/ChunkRemoverItem/
 * InfiniteBeaconItem/DuplicatorItem/TradePlanner} 静态助手——这些类含 1.21 专属 tooltip 签名不能上
 * 1.20.1 白名单，原文整段挪进主线宿主（{@code LegacyNodeConfigHost}）兑现；1.20.1 默认关
 * （ExtractPort.Host「主线专属功能默认关」同一副面孔），对应机型 C5 到序时接上。
 * <b>默认关是静默的（m523 教训②）：主线判官钉「宿主装了没」。</b>
 */
public final class NodeConfig {

    private NodeConfig() { }

    /** 宿主面：图状态 + 世界句柄 + 变更回调 + 机器专属哨兵/校验（带默认值）。 */
    public interface Host {
        CanvasGraphState graph();
        /** 不透明代际句柄（Level），只透传给 common 规划器；不许在本类内转型。 */
        Object level();
        /** 主线=setChanged+syncToClient；1.20.1=setChanged（快照由接收器直推）。 */
        void changed();
        /** toggleFilterEntry 机器专属哨兵段（#zy/#zrd/#bfx/#bfl/#zsbd/#zm/#zw/#zs，主线原文住宿主）：
         *  处理了（含 changed）返回 true；返回 false 走通用名单段。默认不处理。 */
        default boolean specialFilterEntry(ItemStack s, String id) { return false; }
        /** setNodeTarget 的三项主线专属校验（m146 交易 / m334 复制 / m396 封边），默认全拒。 */
        default boolean tradeTargetOk(ItemStack s, String id) { return false; }
        default boolean dupTargetOk(ItemStack s, String id) { return false; }
        default boolean sealTargetOk(ItemStack s, String id) { return false; }
    }

    /** m472 身份判定：defOf 引用同一性，两代通用（RouteBrain 同款）。 */
    private static boolean isDef(ItemStack st, MachineDef def) {
        return def != null && NodeTags.defOf(st) == def;
    }

    /** 主线 {@code instanceof CropFarmItem}：该物品类在主线同时挂基础款与农业塔两个 def。 */
    private static boolean isCropFarm(ItemStack st) {
        return isDef(st, Machines.CROP_FARM) || isDef(st, Machines.MEGA_CROP_FARM);
    }

    /** 切换节点 暂停/运行（m110b）。 */
    public static void togglePause(Host h, int index) {
        CanvasGraphState g = h.graph();
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if (s.isEmpty()) return;
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        n.putBoolean("np", !com.sdzjz.node.NodeTags.nodePaused(s));
        com.sdzjz.item.ItemData.write(s, n);
        h.changed();
    }

    /** 切换开关节点 开/关。 */
    public static void toggleSwitch(Host h, int index) {
        CanvasGraphState g = h.graph();
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        if (com.sdzjz.node.NodeTags.isSwitch(s)) n.putBoolean("so", !com.sdzjz.node.NodeTags.switchOn(s));
        else if (com.sdzjz.node.NodeTags.isExtractor(s)) n.putBoolean("xo", !com.sdzjz.node.NodeTags.extractorOn(s)); // m154 抽取启停走同一收包口
        else return;
        com.sdzjz.item.ItemData.write(s, n);
        h.changed();
    }

    /** 加/移一条过滤名单项（已在名单=移除）；id 为空串=切换 白名单↔黑名单。 */
    public static void toggleFilterEntry(Host h, int index, String id) {
        CanvasGraphState g = h.graph();
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if ("#cr".equals(id) && isDef(s, Machines.AUTO_CRAFTER)) { // m235 配方换挡复用此收包口（#xr 同款哨兵工艺）：
            // 自动(-1)→候选0→候选1→…→回自动；候选序=CraftPlanner.plans 原版排前+id字典序，双端同源循环稳定
            String tgt = com.sdzjz.node.NodeTags.craftTarget(s);
            java.util.List<CraftPlanner.Plan> ps = tgt.isEmpty() ? java.util.List.of() : CraftPlanner.plans(h.level(), tgt);
            CompoundTag nc = com.sdzjz.node.NodeTags.nbtOf(s);
            String cur = nc.contains("cr") ? nc.getString("cr") : "";
            int at = -1;
            for (int k = 0; k < ps.size(); k++) if (ps.get(k).recipeId().equals(cur)) { at = k; break; }
            int nxt = at + 1;
            if (nxt >= ps.size()) nc.remove("cr"); else nc.putString("cr", ps.get(nxt).recipeId());
            com.sdzjz.item.ItemData.write(s, nc);
            h.changed();
            return;
        }
        if ("#xr".equals(id) && com.sdzjz.node.NodeTags.isExtractor(s)) { // m159 抽取量换挡复用此收包口；m163a 扩至五挡（用户点名"还是太少"）
            CompoundTag nx = com.sdzjz.node.NodeTags.nbtOf(s);
            long cur = com.sdzjz.node.NodeTags.extractorRate(s);
            nx.putLong("xr", cur == 64 ? 512 : cur == 512 ? 4096 : cur == 4096 ? 32768 : cur == 32768 ? 262144 : 64);
            com.sdzjz.item.ItemData.write(s, nx);
            h.changed();
            return;
        }
        // 世代口：区块族/信标七段哨兵（#zy/#zrd/#bfx/#bfl/#zsbd/#zm/#zw/#zs）住主线宿主原文；1.20.1 默认不处理
        if (h.specialFilterEntry(s, id)) return;
        boolean chunkF = isDef(s, Machines.CHUNK_FILTER); // m377 区块过滤器：名单+黑白切换全套复用过滤节点收包口
        boolean voidP = isDef(s, Machines.VOID_PROCESSOR); // m378 虚空处理器：白名单复用（永远白名单无黑白，垃圾桶同律）
        if (!com.sdzjz.node.NodeTags.isFilter(s) && !com.sdzjz.node.NodeTags.machineFilterable(s) && !com.sdzjz.node.NodeTags.isExtractor(s) && !com.sdzjz.node.NodeTags.isTrash(s) && !chunkF && !voidP) return;
        // m149 机器加工过滤 / m160 抽取白名单+垃圾桶白名单（安全桶）同走此口
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        if (id == null || id.isEmpty()) {
            if (!com.sdzjz.node.NodeTags.isFilter(s) && !chunkF) return; // 机器侧永远白名单，无黑白切换（m377 区块过滤器有黑白）
            n.putBoolean("fb", !n.getBoolean("fb"));
        } else {
            ListTag l = n.getList("fl", Tag.TAG_STRING);
            boolean removed = false;
            for (int k = 0; k < l.size(); k++)
                if (l.getString(k).equals(id)) { l.remove(k); removed = true; break; }
            if (!removed) {
                if (l.size() >= 64) return; // 名单封顶，防 NBT 膨胀
                l.add(net.minecraft.nbt.StringTag.valueOf(id));
            }
            n.put("fl", l);
        }
        com.sdzjz.item.ItemData.write(s, n);
        h.changed();
    }

    /** 设置传感器：监测物品 + 阈值 + 方向（低于/高于放行）。 */
    public static void setSensorConfig(Host h, int index, String id, long threshold, boolean less) {
        CanvasGraphState g = h.graph();
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        if (!com.sdzjz.node.NodeTags.isSensor(s) && !com.sdzjz.node.NodeTags.isExtractor(s)) return; // m160 抽取节点内置自动启停同走此口
        CompoundTag n = com.sdzjz.node.NodeTags.nbtOf(s);
        if ("§clear".equals(id)) n.remove("si"); // m160 清除感应（传感/抽取通用）
        else if (id != null && !id.isEmpty()) n.putString("si", id);
        n.putLong("sv", Math.max(0, Math.min(1_000_000_000_000L, threshold)));
        n.putBoolean("sl", less);
        com.sdzjz.item.ItemData.write(s, n);
        h.changed();
    }

    /** 设置自动合成机节点的目标产物（画布徽章点选，走 NodeTargetPayload）。 */
    public static void setNodeTarget(Host h, int index, String id) {
        CanvasGraphState g = h.graph();
        if (index < 0 || index >= g.machineNodes.size()) return;
        ItemStack s = g.machineNodes.get(index);
        boolean cropOk = isCropFarm(s) && com.sdzjz.machine.CropFarms.has(id);
        boolean brewOk = isDef(s, Machines.BREWING_TOWER)
                && com.sdzjz.machine.BrewPlanner.targetStack(id) != null; // m131b 目标串服务端校验
        boolean enchOk = isDef(s, Machines.ENCHANT_FACTORY)
                && com.sdzjz.machine.EnchantPlanner.targetStack(h.level(), id) != null; // m132 目标串服务端校验
        boolean tradeOk = h.tradeTargetOk(s, id); // m146 目标串服务端校验（世代口：主线 VillagerTraderItem+TradePlanner.valid）
        boolean dupOk = h.dupTargetOk(s, id); // m334 目标=物品id 服务端校验（世代口：主线 DuplicatorItem.validTarget）
        boolean sealOk = h.sealTargetOk(s, id); // m396 封边材料（世代口：主线 ChunkRemoverItem.validSealBlock；移除器的 setNodeTarget 槽 m376 起本就空着=复用零新协议）
        if (!isDef(s, Machines.AUTO_CRAFTER) && !cropOk && !brewOk && !enchOk && !tradeOk && !dupOk && !sealOk) return;
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        if (sealOk) { // m396 封边材料：单选写 zsb（清回默认走菜单 #zsbd 哨兵）
            n.putString("zsb", id);
        } else if (cropOk) { // m93 多选 toggle：在列表则移除，否则加入（≤8）；旧单选 ct 自动并入
            java.util.List<String> cur = com.sdzjz.node.NodeTags.cropList(s);
            if (cur.contains(id)) cur.remove(id);
            else if (cur.size() < 8) cur.add(id);
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (String c : cur) l.add(net.minecraft.nbt.StringTag.valueOf(c));
            n.put("crops", l);
            n.remove("ct");
        } else {
            n.putString("ct", id);
            n.remove("cr"); // m235 换目标即回"自动"（旧手选配方不属于新目标）
        }
        com.sdzjz.item.ItemData.write(s, n);
        h.changed();
    }
}
