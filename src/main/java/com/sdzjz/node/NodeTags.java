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
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
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
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        return n.contains("ct") ? n.getString("ct") : "";
    }

    public static NbtCompound nbtOf(ItemStack s) { // m159 客户端卡面读xc改包内可见
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    public static boolean isFilter(ItemStack s) { return s.isOf(ModItems.FILTER_NODE); }

    public static boolean isTrash(ItemStack s) { return s.isOf(ModItems.TRASH_NODE); }

    public static boolean isExtractor(ItemStack s) { return s.isOf(ModItems.EXTRACTOR_NODE); }

    public static boolean extractorOn(ItemStack s) { return nbtOf(s).getBoolean("xo"); }

    public static long extractorCount(ItemStack s) { return nbtOf(s).getLong("xc"); }

    public static long extractorRate(ItemStack s) {
        long r = nbtOf(s).getLong("xr");
        return r > 0 ? r : 512;
    }

    public static long trashCount(ItemStack s) { return nbtOf(s).getLong("tc"); }

    public static boolean isSensor(ItemStack s) { return s.isOf(ModItems.SENSOR_NODE); }

    public static boolean isSwitch(ItemStack s) { return s.isOf(ModItems.SWITCH_NODE); }

    public static boolean isDistributor(ItemStack s) { return s.isOf(ModItems.DISTRIBUTOR_NODE); }

    public static boolean switchOn(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return !n.contains("so") || n.getBoolean("so");
    }

    public static int machineTier(ItemStack s) { return nbtOf(s).getInt("mt"); }

    public static boolean nodePaused(ItemStack s) { return nbtOf(s).getBoolean("np"); }

    /** m191 画布分组：节点所属组 id（存节点栈 NBT "gp"，随栈走天然免下标重映射；无组=-1）。 */
    public static int nodeGroup(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return n.contains("gp") ? n.getInt("gp") : -1;
    }

    public static boolean filterBlacklist(ItemStack s) { return nbtOf(s).getBoolean("fb"); }

    public static java.util.List<String> filterList(ItemStack s) {
        NbtList l = nbtOf(s).getList("fl", NbtElement.STRING_TYPE);
        java.util.List<String> out = new java.util.ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static boolean filterPasses(ItemStack s, String id) {
        boolean in = false;
        NbtList l = nbtOf(s).getList("fl", NbtElement.STRING_TYPE);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) { in = true; break; }
        return filterBlacklist(s) ? !in : in;
    }

    public static boolean machineFilterable(ItemStack s) {
        if (!(s.getItem() instanceof com.sdzjz.item.MachineItem mi)) return false;
        return com.sdzjz.machine.Machines.smelterFamily(mi.def().id()) || mi.def().outputs().size() > 1; // m173 熔炉族
    }

    public static boolean machineFilterAllows(ItemStack s, String id) {
        NbtList l = nbtOf(s).getList("fl", NbtElement.STRING_TYPE);
        if (l.isEmpty()) return true;
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) return true;
        return false;
    }

    public static String sensorItem(ItemStack s) { return nbtOf(s).getString("si"); }

    public static long sensorThreshold(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return n.contains("sv") ? Math.max(0, n.getLong("sv")) : 10000L;
    }

    public static boolean sensorLess(ItemStack s) {
        NbtCompound n = nbtOf(s);
        return !n.contains("sl") || n.getBoolean("sl");
    }

    public static int runningCount(ItemStack st, int parallelLv, int tier) {
        long r = (long) Math.max(1, st.getCount()) * (1L + Math.max(0, parallelLv)) * Math.max(1, tier);
        r <<= 3 * Math.min(3, Math.max(0, machineTier(st))); // m123 阶位战力 8^mt（4台份×2速/阶）
        return (int) Math.min(r, 1_000_000L);
    }
}
