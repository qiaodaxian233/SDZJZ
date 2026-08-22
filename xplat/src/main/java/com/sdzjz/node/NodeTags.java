package com.sdzjz.node;

import com.sdzjz.item.MachineItem;
import com.sdzjz.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;

/**
 * m180 绞杀者拆分第一刀：节点 ItemStack 标签纯函数（自 StructureCoreBlockEntity 原样迁入，
 * 方法体一字未改）。判定（is 六族）/读数（阶位·暂停·开关·过滤·传感·抽取·垃圾桶·作物·目标）/
 * 并发战力 runningCount。全部只碰 ItemStack NBT，不碰任何 BE 状态。
 * SCBE 原位留同签名垫片＝全库零调用点改动；垫片待后续里程碑切换调用点后拆除。新代码直用本类。
 */
public final class NodeTags {
    private NodeTags() {}

    /** m93：全自动农场多选作物（最多8种）。无 crops 列表时回退旧单选 ct（自动迁移）。 */
    public static java.util.List<String> cropList(ItemStack s) {
        CompoundTag n = viewOf(s); // m353 只读免拷贝
        java.util.List<String> out = new java.util.ArrayList<>();
        if (n.contains("crops")) {
            net.minecraft.nbt.ListTag l = n.getList("crops", 8);
            for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        } else {
            String ct = n.getString("ct");
            if (!ct.isEmpty() && com.sdzjz.machine.CropFarms.has(ct)) out.add(ct);
        }
        return out;
    }

    public static String craftTarget(ItemStack s) {
        CompoundTag n = viewOf(s); // m353 只读免拷贝
        return n.contains("ct") ? n.getString("ct") : "";
    }

    /** m235 合成机手选配方 id（空=自动按库存挑，m234）。 */
    public static String craftRecipe(ItemStack s) {
        CompoundTag n = viewOf(s); // m353 只读免拷贝
        return n.contains("cr") ? n.getString("cr") : "";
    }

    /** 写路起手口：返回**拷贝**——改完必须 s.set(CUSTOM_DATA, CustomData.of(n)) 回写，否则丢写
     *  （m353 垃圾桶 tc 就栽在这：改了拷贝没回写，"已吞"自组件化起是死数）。只读请走 viewOf 零拷贝。 */
    public static CompoundTag nbtOf(ItemStack s) { // m159 客户端卡面读xc改包内可见
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** m353 免拷贝只读视图（yarn getNbt=组件内部实包，mojmap getUnsafe 同口，1.21.1 核名 method_57463）。
     *  铁律：**绝对只读**——改它=篡改组件内部状态，且 DEFAULT 空件全局共享一份，写它=全服中毒。
     *  要写走 nbtOf 拷贝→改→set 三段。读路全面换本口是压测 447MB/s 分配火源的主刀（m353）。 */
    public static CompoundTag viewOf(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
    }

    /** m353 垃圾桶已吞计数累加（修丢写 bug：拷贝→加→set 回三段全）。 */
    public static void addTrashCount(ItemStack s, long ate) {
        CompoundTag n = nbtOf(s);
        n.putLong("tc", n.getLong("tc") + ate);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
    }

    public static boolean isFilter(ItemStack s) { return s.is(ModItems.FILTER_NODE); }

    public static boolean isTrash(ItemStack s) { return s.is(ModItems.TRASH_NODE); }

    public static boolean isExtractor(ItemStack s) { return s.is(ModItems.EXTRACTOR_NODE); }

    /** m154 抽取节点开合（默认关——用户点名"点击抽取才开始"）。 */
    public static boolean extractorOn(ItemStack s) { return viewOf(s).getBoolean("xo"); }

    /** m159 抽取累计读数（卡面用）。 */
    public static long extractorCount(ItemStack s) { return viewOf(s).getLong("xc"); }

    /** m159 抽取量/轮（每 5t 每种上限，未设=512）；m163a 五挡循环 64→512→4096→32768→262144。实际抽量再乘 (1+数量升级)。 */
    public static long extractorRate(ItemStack s) {
        long r = viewOf(s).getLong("xr");
        return r > 0 ? r : 512;
    }

    /** m150 垃圾桶累计吞噬量（卡面读数）。 */
    public static long trashCount(ItemStack s) { return viewOf(s).getLong("tc"); }

    public static boolean isSensor(ItemStack s) { return s.is(ModItems.SENSOR_NODE); }

    public static boolean isSwitch(ItemStack s) { return s.is(ModItems.SWITCH_NODE); }

    public static boolean isDistributor(ItemStack s) { return s.is(ModItems.DISTRIBUTOR_NODE); }

    /** 开关节点状态：默认=开；NBT "so"=false 时为关。 */
    public static boolean switchOn(ItemStack s) {
        CompoundTag n = viewOf(s);
        return !n.contains("so") || n.getBoolean("so");
    }

    /** m123 机器阶位：0普通 1超级 2神级 3GM。每阶=4台合1、战力8×/阶（4台份×2速）。 */
    public static int machineTier(ItemStack s) { return viewOf(s).getInt("mt"); }

    /** m110b 单节点启停：默认=运行；NBT "np"=true 为暂停（任意节点类型通用）。 */
    public static boolean nodePaused(ItemStack s) { return viewOf(s).getBoolean("np"); }

    /** m191 画布分组：节点所属组 id（存节点栈 NBT "gp"，随栈走天然免下标重映射；无组=-1）。 */
    public static int nodeGroup(ItemStack s) {
        CompoundTag n = viewOf(s);
        return n.contains("gp") ? n.getInt("gp") : -1;
    }

    /** 过滤模式：false=白名单（默认，只放行名单内），true=黑名单（拦下名单内）。 */
    public static boolean filterBlacklist(ItemStack s) { return viewOf(s).getBoolean("fb"); }

    public static java.util.List<String> filterList(ItemStack s) {
        ListTag l = viewOf(s).getList("fl", Tag.TAG_STRING);
        java.util.List<String> out = new java.util.ArrayList<>(l.size());
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static boolean filterPasses(ItemStack s, String id) {
        boolean in = false;
        ListTag l = viewOf(s).getList("fl", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) { in = true; break; }
        return filterBlacklist(s) ? !in : in;
    }

    /** m149 机器加工二级界面：哪些机器有"选加工范围"资格——万能熔炉(选烧什么)或多产物机(选出什么)。 */
    public static boolean machineFilterable(ItemStack s) {
        if (!(s.getItem() instanceof com.sdzjz.item.MachineItem mi)) return false;
        return com.sdzjz.machine.Machines.smelterFamily(mi.def().id()) || mi.def().outputs().size() > 1; // m173 熔炉族
    }

    /** m149 机器加工过滤（复用 fl 名单，白名单语义）：空=全放行；非空=只加工选中项。
     *  与过滤节点的 fl+fb 双语义区分：机器侧永远白名单、不碰 fb。 */
    public static boolean machineFilterAllows(ItemStack s, String id) {
        ListTag l = viewOf(s).getList("fl", Tag.TAG_STRING);
        if (l.isEmpty()) return true;
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(id)) return true;
        return false;
    }

    public static String sensorItem(ItemStack s) { return viewOf(s).getString("si"); }

    public static long sensorThreshold(ItemStack s) {
        CompoundTag n = viewOf(s);
        return n.contains("sv") ? Math.max(0, n.getLong("sv")) : 10000L;
    }

    /** 传感器方向：true=「低于阈值放行」(默认，补货型)；false=「高于阈值放行」(溢出型)。 */
    public static boolean sensorLess(ItemStack s) {
        CompoundTag n = viewOf(s);
        return !n.contains("sl") || n.getBoolean("sl");
    }

    // ===== m376 区块移除器（区块机器线第一台）：绑定与扫描游标全在节点 NBT，键 z 族 =====
    /** 已绑定目标区块（zx/zz 成对存在才算，绑定动作见 ChunkRemoverItem#useOnBlock）。 */
    public static boolean chunkBound(ItemStack s) {
        CompoundTag n = viewOf(s);
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

    /** m382 区块移除器区域半径（0=1×1，1=3×3…；配置收顶在 tick 侧）。 */
    public static int chunkRadius(ItemStack s) { return viewOf(s).getInt("zr"); }

    /** m382 层主序游标的分块序号 0..w²-1。 */
    public static int chunkOrd(ItemStack s) { return viewOf(s).getInt("zc"); }

    /** m386 掉落模式：0=有掉落·出货（默认），1=无掉落·极速蒸发。 */
    public static int chunkMode(ItemStack s) { return viewOf(s).getInt("zm"); }

    /** m388 封边挡水：1=区域边界外侧贴水/岩浆处砌石墙代替空气（勾选在手持面板/节点菜单，
     *  config chunkRemoverSealFluids 总闸另有；开=重扫补封，关不动游标；m389 材料玻璃→石头）。 */
    /** m394 封边挡水（作者拍板"默认就该铺，不是说了才铺"）：zw 三态——**0=缺省即开** / 1=显式开 /
     *  2=显式关。老档 zw 缺键或 0 的机器升级后自动变成"堵水开"（要的就是这个），老档显式勾过的
     *  zw=1 语义不变；关掉写 2。刻意不换键：换键=老档丢设定，三态是零迁移的写法。 */
    public static boolean chunkSealOn(ItemStack s) { return viewOf(s).getInt("zw") != 2; }

    /** m399 无限距离信标：效果序号（bfx，缺键=0=急迫）与等级（bfl，0=I 级 / 1=II 级）。 */
    public static int beaconEffect(ItemStack s) { return viewOf(s).getInt("bfx"); }

    public static int beaconLevel(ItemStack s) { return viewOf(s).getInt("bfl"); }

    /** m398 本拍撞到的上限（0=没撞 1=每拍方块硬顶 2=墙钟时间片）——副行说人话用，m99 反静默。 */
    public static int chunkLimited(ItemStack s) { return viewOf(s).getInt("zl"); }

    /** m396 封边材料 id（空=默认免费石头）。自定义料从存储扣，扣不到=黄灯提醒并回落石墙。 */
    public static String chunkSealBlock(ItemStack s) { return viewOf(s).getString("zsb"); }

    /** m390 本轮湿账：本遍游标累计"清过的流体+补过的封"数——>0 表示这一遍碰过水，到底后
     *  不置完成位、从顶再复检一遍（残水复检环），直到全程零流体才算清完。 */
    public static long chunkWetPass(ItemStack s) { return viewOf(s).getLong("zq"); }

    /** m377 区块过滤器 Y 挡位序号（挡位表唯一权威=ChunkFilterItem，此处只管取数）。 */
    public static int chunkFilterPreset(ItemStack s) { return viewOf(s).getInt("zp"); }

    // ===== m380 区块扫描器报告键（绑定/游标复用上面 z 族）：sa 方块总数 / so 矿物 / sc 容器 / se 生物 / sf 就绪位 / sm 类型榜 =====
    public static long scanTotal(ItemStack s) { return viewOf(s).getLong("sa"); }

    public static long scanOre(ItemStack s) { return viewOf(s).getLong("so"); }

    public static long scanContainers(ItemStack s) { return viewOf(s).getLong("sc"); }

    public static long scanEntities(ItemStack s) { return viewOf(s).getLong("se"); }

    public static boolean scanDone(ItemStack s) { return viewOf(s).getBoolean("sf"); }

    /** 类型榜只读视图（viewOf 铁律：绝对只读，消费端只许遍历取数）。 */
    public static CompoundTag scanTypes(ItemStack s) { return viewOf(s).getCompound("sm"); }

    // ===== m381 区块储存器三键（绑定/游标复用 z 族）：tf 就绪位 / tu 模板UUID / tt 可重建总数 =====
    public static boolean vaultDone(ItemStack s) { return viewOf(s).getBoolean("tf"); }

    public static String vaultUuid(ItemStack s) { return viewOf(s).getString("tu"); }

    public static long vaultTotal(ItemStack s) { return viewOf(s).getLong("tt"); }

    // ===== m378 虚空处理器（垃圾炼经验）三键：va 累计吞 / vc 汇率余数 / vn 累计炼得经验 =====
    public static long voidEaten(ItemStack s) { return viewOf(s).getLong("va"); }

    public static long voidCarry(ItemStack s) { return viewOf(s).getLong("vc"); }

    public static long voidXp(ItemStack s) { return viewOf(s).getLong("vn"); }

    /** m99 并发升级直接乘台数：运行台数 = 节点内机器数 ×(1+并发级)×核心层级。 */
    public static int runningCount(ItemStack st, int parallelLv, int tier) {
        long r = (long) Math.max(1, st.getCount()) * (1L + Math.max(0, parallelLv)) * Math.max(1, tier);
        r <<= 3 * Math.min(3, Math.max(0, machineTier(st))); // m123 阶位战力 8^mt（4台份×2速/阶）
        return (int) Math.min(r, 1_000_000L);
    }
}
