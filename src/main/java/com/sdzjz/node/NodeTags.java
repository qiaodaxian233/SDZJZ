package com.sdzjz.node;

import com.sdzjz.item.MachineItem;
import com.sdzjz.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * m180 绞杀者拆分第一刀：节点 ItemStack 标签纯函数（自 StructureCoreBlockEntity 原样迁入，
 * 方法体一字未改）。判定（is 六族）/读数（阶位·暂停·开关·过滤·传感·抽取·垃圾桶·作物·目标）/
 * 并发战力 runningCount。全部只碰 ItemStack NBT，不碰任何 BE 状态。
 * SCBE 原位留同签名垫片＝全库零调用点改动；垫片待后续里程碑切换调用点后拆除。新代码直用本类。
 */
public final class NodeTags {
    private NodeTags() {}

    public static java.util.List<String> cropList(ItemStack s) {
        NbtCompound n = viewOf(s); // m353 只读免拷贝
        java.util.List<String> out = new java.util.ArrayList<>();
        if (n.contains("crops")) {
            net.minecraft.nbt.NbtList l = n.getList("crops", 8);
            for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        } else {
            String ct = n.getString("ct");
            if (!ct.isEmpty() && com.sdzjz.machine.CropFarms.has(ct)) out.add(ct);
        }
        return out;
    }

    public static String craftTarget(ItemStack s) {
        NbtCompound n = viewOf(s); // m353 只读免拷贝
        return n.contains("ct") ? n.getString("ct") : "";
    }

    /** m235 合成机手选配方 id（空=自动按库存挑，m234）。 */
    public static String craftRecipe(ItemStack s) {
        NbtCompound n = viewOf(s); // m353 只读免拷贝
        return n.contains("cr") ? n.getString("cr") : "";
    }

    /** 写路起手口：返回**拷贝**——改完必须 s.set(CUSTOM_DATA, NbtComponent.of(n)) 回写，否则丢写
     *  （m353 垃圾桶 tc 就栽在这：改了拷贝没回写，"已吞"自组件化起是死数）。只读请走 viewOf 零拷贝。 */
    public static NbtCompound nbtOf(ItemStack s) { // m159 客户端卡面读xc改包内可见
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    /** m353 免拷贝只读视图（yarn getNbt=组件内部实包，mojmap getUnsafe 同口，1.21.1 核名 method_57463）。
     *  铁律：**绝对只读**——改它=篡改组件内部状态，且 DEFAULT 空件全局共享一份，写它=全服中毒。
     *  要写走 nbtOf 拷贝→改→set 三段。读路全面换本口是压测 447MB/s 分配火源的主刀（m353）。 */
    public static NbtCompound viewOf(ItemStack s) {
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).getNbt();
    }

    /** m353 垃圾桶已吞计数累加（修丢写 bug：拷贝→加→set 回三段全）。 */
    public static void addTrashCount(ItemStack s, long ate) {
        NbtCompound n = nbtOf(s);
        n.putLong("tc", n.getLong("tc") + ate);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
    }

    public static boolean isFilter(ItemStack s) { return s.isOf(ModItems.FILTER_NODE); }

    public static boolean isTrash(ItemStack s) { return s.isOf(ModItems.TRASH_NODE); }

    public static boolean isExtractor(ItemStack s) { return s.isOf(ModItems.EXTRACTOR_NODE); }

    public static boolean extractorOn(ItemStack s) { return viewOf(s).getBoolean("xo"); }

    public static long extractorCount(ItemStack s) { return viewOf(s).getLong("xc"); }

    public static long extractorRate(ItemStack s) {
        long r = viewOf(s).getLong("xr");
        return r > 0 ? r : 512;
    }

    public static long trashCount(ItemStack s) { return viewOf(s).getLong("tc"); }

    public static boolean isSensor(ItemStack s) { return s.isOf(ModItems.SENSOR_NODE); }

    public static boolean isSwitch(ItemStack s) { return s.isOf(ModItems.SWITCH_NODE); }

    public static boolean isDistributor(ItemStack s) { return s.isOf(ModItems.DISTRIBUTOR_NODE); }

    public static boolean switchOn(ItemStack s) {
        NbtCompound n = viewOf(s);
        return !n.contains("so") || n.getBoolean("so");
    }

    public static int machineTier(ItemStack s) { return viewOf(s).getInt("mt"); }

    public static boolean nodePaused(ItemStack s) { return viewOf(s).getBoolean("np"); }

    /** m191 画布分组：节点所属组 id（存节点栈 NBT "gp"，随栈走天然免下标重映射；无组=-1）。 */
    public static int nodeGroup(ItemStack s) {
        NbtCompound n = viewOf(s);
        return n.contains("gp") ? n.getInt("gp") : -1;
    }

    public static boolean filterBlacklist(ItemStack s) { return viewOf(s).getBoolean("fb"); }

    public static java.util.List<String> filterList(ItemStack s) {
        NbtList l = viewOf(s).getList("fl", NbtElement.STRING_TYPE);
        java.util.List<String> out = new java.util.ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static boolean filterPasses(ItemStack s, String id) {
        boolean in = false;
        NbtList l = viewOf(s).getList("fl", NbtElement.STRING_TYPE);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) { in = true; break; }
        return filterBlacklist(s) ? !in : in;
    }

    public static boolean machineFilterable(ItemStack s) {
        if (!(s.getItem() instanceof com.sdzjz.item.MachineItem mi)) return false;
        return com.sdzjz.machine.Machines.smelterFamily(mi.def().id()) || mi.def().outputs().size() > 1; // m173 熔炉族
    }

    public static boolean machineFilterAllows(ItemStack s, String id) {
        NbtList l = viewOf(s).getList("fl", NbtElement.STRING_TYPE);
        if (l.isEmpty()) return true;
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) return true;
        return false;
    }

    public static String sensorItem(ItemStack s) { return viewOf(s).getString("si"); }

    public static long sensorThreshold(ItemStack s) {
        NbtCompound n = viewOf(s);
        return n.contains("sv") ? Math.max(0, n.getLong("sv")) : 10000L;
    }

    public static boolean sensorLess(ItemStack s) {
        NbtCompound n = viewOf(s);
        return !n.contains("sl") || n.getBoolean("sl");
    }

    // ===== m376 区块移除器（区块机器线第一台）：绑定与扫描游标全在节点 NBT，键 z 族 =====
    /** 已绑定目标区块（zx/zz 成对存在才算，绑定动作见 ChunkRemoverItem#useOnBlock）。 */
    public static boolean chunkBound(ItemStack s) {
        NbtCompound n = viewOf(s);
        return n.contains("zx") && n.contains("zz");
    }

    public static int chunkX(ItemStack s) { return viewOf(s).getInt("zx"); }

    public static int chunkZ(ItemStack s) { return viewOf(s).getInt("zz"); }

    /** 绑定时所在维度 id 串（跨维度核心不动别人区块，tick 侧对表）。 */
    public static String chunkDim(ItemStack s) { return viewOf(s).getString("zd"); }

    /** 扫描游标 Y（自顶向下推进）。 */
    public static int chunkY(ItemStack s) { return viewOf(s).getInt("zy"); }

    /** 扫描游标层内序号 0..255（lx=idx>>4, lz=idx&15）。 */
    public static int chunkIdx(ItemStack s) { return viewOf(s).getInt("zi"); }

    /** 整区块已清完（游标穿过世界底后置位；重绑清除）。 */
    public static boolean chunkDone(ItemStack s) { return viewOf(s).getBoolean("zf"); }

    /** 累计移除方块数（画布副行进度显示）。 */
    public static long chunkRemoved(ItemStack s) { return viewOf(s).getLong("zn"); }

    /** m377 区块过滤器 Y 挡位序号（挡位表唯一权威=ChunkFilterItem，此处只管取数）。 */
    public static int chunkFilterPreset(ItemStack s) { return viewOf(s).getInt("zp"); }

    // ===== m378 虚空处理器（垃圾炼经验）三键：va 累计吞 / vc 汇率余数 / vn 累计炼得经验 =====
    public static long voidEaten(ItemStack s) { return viewOf(s).getLong("va"); }

    public static long voidCarry(ItemStack s) { return viewOf(s).getLong("vc"); }

    public static long voidXp(ItemStack s) { return viewOf(s).getLong("vn"); }

    public static int runningCount(ItemStack st, int parallelLv, int tier) {
        long r = (long) Math.max(1, st.getCount()) * (1L + Math.max(0, parallelLv)) * Math.max(1, tier);
        r <<= 3 * Math.min(3, Math.max(0, machineTier(st))); // m123 阶位战力 8^mt（4台份×2速/阶）
        return (int) Math.min(r, 1_000_000L);
    }
}
