package com.sdzjz.node;

import com.sdzjz.machine.CraftPlanner;
import com.sdzjz.machine.MachineDef;
import net.minecraft.world.item.ItemStack;

/**
 * m486（真移植·C 阶段主刀）：**路由脑判定层两代共用一份**。
 *
 * <p>全部方法体逐句取自主线 {@code StructureCoreBlockEntity}（accepts0 3241~3282、
 * chainWants0 2818~2887、chainEndsInTrash 2752~2768、sensorOpen 2198~2207、
 * allGatesClosed 2210~2221、extractorLive 1886~1888），**只做三类机械替换**：
 * <ul>
 *   <li>身份判定 {@code instanceof VoidProcessorItem/AutoCrafterItem/BrewingTowerItem/
 *       EnchantFactoryItem/MachineItem} → {@code NodeTags.defOf(st)} 与 {@code Machines}
 *       常量的引用同一性（m472 已证两代通用）；</li>
 *   <li>传感器的库存来源（主线 supplyFor→resolveInputSource 默认主存储回落）→
 *       {@link Host#sensorObserved}；</li>
 *   <li>画布状态/出边/profiler 计数 → {@link Host}。</li>
 * </ul>
 * 除此之外一个字未改，包括注释里的刀号与血案记录（尤其 m153 垃圾桶授权语义、
 * 传感器「闸门在下发阶段生效」那格——它们看起来像 bug，改一下就是 m132-6 重演）。
 *
 * <p>1.20.1 原来那份**按蓝本重写的同功能代码就此删除**。行为由 m481 立下的路由域跨代契约
 * （{@code RouteDomainAssertions} 六条成对判定）压着，两代同绿。
 */
public final class RouteBrain {

    private RouteBrain() { }

    /** 宿主口：路由脑要用的、来自本世代结构核心的四项。 */
    public interface Host {
        int nodeCount();
        ItemStack nodeStack(int i);
        /** 传感器监测到的库存（主线=供料线→默认主存储回落；1.20.1=供料线，未连线按 0 计）。 */
        long sensorObserved(int i, String id);
        /** m177 链式检查计数（无 profiler 的世代空实现即可）。 */
        default void countChainCheck() { }
    }

    /** m472 身份判定：defOf 引用同一性，两代通用。 */
    private static boolean isDef(ItemStack st, MachineDef def) {
        return def != null && com.sdzjz.node.NodeTags.defOf(st) == def;
    }

    public static boolean sensorOpen(Host host, Object world, int i) {
        ItemStack st = host.nodeStack(i);
        String id = com.sdzjz.node.NodeTags.sensorItem(st);
        if (id.isEmpty()) return true;
        long have = host.sensorObserved(i, id); // 世代口：主线=供料线→默认主存储回落，1.20.1=供料线（无默认主存储，未连线按 0 计）
        long th = com.sdzjz.node.NodeTags.sensorThreshold(st);
        return com.sdzjz.node.NodeTags.sensorLess(st) ? have < th : have > th;
    }

    public static boolean extractorLive(Host host, Object world, int i, ItemStack st) {
        return com.sdzjz.node.NodeTags.extractorOn(st) && (com.sdzjz.node.NodeTags.sensorItem(st).isEmpty() || sensorOpen(host, world, i));
    }

    public static boolean allGatesClosed(Host host, Object world, int[] targets) { // m355 数组化
        if (targets == null || targets.length == 0) return false;
        for (int t : targets) {
            if (t < 0 || t >= host.nodeCount()) return false;
            ItemStack ts = host.nodeStack(t);
            boolean closedGate = (com.sdzjz.node.NodeTags.isSensor(ts) && !sensorOpen(host, world, t)) || (com.sdzjz.node.NodeTags.isSwitch(ts) && !com.sdzjz.node.NodeTags.switchOn(ts))
                    || (com.sdzjz.node.NodeTags.isExtractor(ts) && !extractorLive(host, world, t, ts)) // m154+m160 感应暂停同视关闸
                    || com.sdzjz.node.NodeTags.nodePaused(ts); // m110b 暂停视同关闸——下游全暂停时上游整台停，不白产
            if (!closedGate) return false;
        }
        return true;
    }

    public static boolean chainEndsInTrash(Host host, Object world, int i, String id, int depth, java.util.Set<Integer> visited,
                                     int[][] outT) { // m355 数组化
        if (depth > 8 || i < 0 || i >= host.nodeCount() || !visited.add(i)) return false;
        ItemStack st = host.nodeStack(i);
        if (com.sdzjz.node.NodeTags.nodePaused(st)) return false;
        if (com.sdzjz.node.NodeTags.isTrash(st)) return com.sdzjz.node.NodeTags.machineFilterAllows(st, id); // m160 安全桶名单外不算销毁终点
        if (isDef(st, com.sdzjz.machine.Machines.VOID_PROCESSOR)) // m378 炼掉=销毁终点同格（磨石回收附魔经验的工业版）
            return com.sdzjz.config.SdzjzConfig.get().voidProcessorEnabled && com.sdzjz.node.NodeTags.machineFilterAllows(st, id);
        if (com.sdzjz.node.NodeTags.isFilter(st) && !com.sdzjz.node.NodeTags.filterPasses(st, id)) return false;
        if (com.sdzjz.node.NodeTags.isSwitch(st) && !com.sdzjz.node.NodeTags.switchOn(st)) return false;
        if (com.sdzjz.node.NodeTags.isExtractor(st) && (!extractorLive(host, world, i, st) || !com.sdzjz.node.NodeTags.machineFilterAllows(st, id))) return false; // m160
        int[] targets = i < outT.length ? outT[i] : null; // m355 计划与节点表同拍重编译，越界仅防御
        if (targets == null) return false;
        for (int t : targets)
            if (chainEndsInTrash(host, world, t, id, depth + 1, visited, outT)) return true;
        return false;
    }

    public static boolean accepts(Host host, Object world, int target, String id) {
        ItemStack st = host.nodeStack(target);
        if (com.sdzjz.node.NodeTags.nodePaused(st)) return false;                // m110b 暂停不收（上游改走默认路由）
        if (com.sdzjz.node.NodeTags.isFilter(st)) return com.sdzjz.node.NodeTags.filterPasses(st, id);   // 拦下的留在上游走默认路由→存储
        if (com.sdzjz.node.NodeTags.isSensor(st)) return sensorOpen(host, world, target); // 关闸不收（上游全关闸时整台暂停）
        if (com.sdzjz.node.NodeTags.isSwitch(st)) return com.sdzjz.node.NodeTags.switchOn(st);              // 关闸不收，同上
        if (com.sdzjz.node.NodeTags.isDistributor(st)) return true;                 // 分配器什么都收（分不出去的走默认路由）
        if (com.sdzjz.node.NodeTags.isTrash(st)) return com.sdzjz.node.NodeTags.machineFilterAllows(st, id); // m150 垃圾桶 / m160 安全桶：白名单空=连啥吞啥，非空=只吞名单内
        if (isDef(st, com.sdzjz.machine.Machines.VOID_PROCESSOR)) // m378 垃圾桶同律；停用=拒收让上游走默认路由回仓
            return com.sdzjz.config.SdzjzConfig.get().voidProcessorEnabled && com.sdzjz.node.NodeTags.machineFilterAllows(st, id);
        if (com.sdzjz.node.NodeTags.isExtractor(st))                                  // m154 开=收 / m160 感应联动+白名单一起闸
            return extractorLive(host, world, target, st) && com.sdzjz.node.NodeTags.machineFilterAllows(st, id);
        if (isDef(st, com.sdzjz.machine.Machines.AUTO_CRAFTER)) {
            String tgt = com.sdzjz.node.NodeTags.craftTarget(st);
            if (tgt.isEmpty()) return false;
            String cr = com.sdzjz.node.NodeTags.craftRecipe(st); // m235 手选=只收那条的料
            if (!cr.isEmpty()) for (var pp : CraftPlanner.plans(world, tgt))
                if (pp.recipeId().equals(cr)) return CraftPlanner.wantsItem(pp, id); // m343 手选也认槽位替代材料
            return CraftPlanner.wants(world, tgt).contains(id); // m234 全候选并集（m343 起含替代材料）
        }
        if (isDef(st, com.sdzjz.machine.Machines.BREWING_TOWER)) { // m131b：吃酿造链材料+燃料
            String tgt = com.sdzjz.node.NodeTags.craftTarget(st);
            if (tgt.isEmpty()) return false;
            var plan = com.sdzjz.machine.BrewPlanner.plan(world, tgt);
            return plan != null && (plan.needs().containsKey(id) || com.sdzjz.machine.BrewPlanner.FUEL_ID.equals(id));
        }
        if (isDef(st, com.sdzjz.machine.Machines.ENCHANT_FACTORY)) { // m132：吃书+青金石（经验非物品不走线）
            String tgt = com.sdzjz.node.NodeTags.craftTarget(st);
            if (tgt.isEmpty()) return false;
            var plan = com.sdzjz.machine.EnchantPlanner.plan(world, tgt);
            return plan != null && plan.needs().containsKey(id);
        }
        MachineDef mi = com.sdzjz.node.NodeTags.defOf(st);
        if (mi != null) {
            if (com.sdzjz.machine.Machines.smelterFamily(mi.id())) // m173 熔炉族
                return com.sdzjz.machine.SmeltPlanner.resultOf(world, id) != null
                        && com.sdzjz.node.NodeTags.machineFilterAllows(st, id); // m149
            if (mi.consumesInputs()) {
                for (MachineDef.Input in : mi.inputs()) if (in.item().equals(id)) return true;
            }
        }
        return false;
    }

    public static boolean chainWants(Host host, Object world, int i, String id, int depth,
                               java.util.Set<Integer> visited,
                               int[][] outT, // m355 数组化
                               java.util.Map<Integer, java.util.Set<String>> crafterNeeds) {
        host.countChainCheck(); // m177
        if (depth > 8 || i < 0 || i >= host.nodeCount() || !visited.add(i)) return false;
        ItemStack st = host.nodeStack(i);
        if (com.sdzjz.node.NodeTags.nodePaused(st)) return false; // m110b 暂停节点不参与链式需求
        if (com.sdzjz.node.NodeTags.isFilter(st)) {
            if (!com.sdzjz.node.NodeTags.filterPasses(st, id)) return false;
        } else if (com.sdzjz.node.NodeTags.isSwitch(st)) {
            if (!com.sdzjz.node.NodeTags.switchOn(st)) return false;
        } else if (com.sdzjz.node.NodeTags.isExtractor(st)) {
            if (!extractorLive(host, world, i, st) || !com.sdzjz.node.NodeTags.machineFilterAllows(st, id)) return false; // m160 感应+白名单闸
        } else if (com.sdzjz.node.NodeTags.isTrash(st)) {
            // m153 垃圾桶链式需求（用户实测：仓→过滤器(白名单山羊角)→垃圾桶抽不动）——
            // 经逻辑节点转接 = 玩家明确布线授权，垃圾桶作为终端什么都"想要"；
            // 直连仓依然不抽（拉料回路的节点类型清单本就不含垃圾桶，m150 防手滑边界不动）。
            return true;
        } else if (isDef(st, com.sdzjz.machine.Machines.VOID_PROCESSOR)) {
            // m378 虚空处理器链式需求=垃圾桶 m153 同律：经逻辑节点转接=玩家明确布线授权；
            // 收敛一步：只"想要"白名单放行的（与 accepts 同口径，省得拉来又退回）；停用=不想要。
            return com.sdzjz.config.SdzjzConfig.get().voidProcessorEnabled && com.sdzjz.node.NodeTags.machineFilterAllows(st, id);
        } else if (com.sdzjz.node.NodeTags.isSensor(st) || com.sdzjz.node.NodeTags.isDistributor(st)) {
            // 传感器闸门/分配器均分在下发阶段生效，需求判定直接放行
        } else if (isDef(st, com.sdzjz.machine.Machines.AUTO_CRAFTER)) {
            java.util.Set<String> needs = crafterNeeds.computeIfAbsent(i, k -> {
                String tgt = com.sdzjz.node.NodeTags.craftTarget(st);
                if (tgt.isEmpty()) return java.util.Set.of();
                String cr = com.sdzjz.node.NodeTags.craftRecipe(st); // m235 手选=只要那条的料；自动=全候选并集（m234）
                if (!cr.isEmpty()) for (var pp : CraftPlanner.plans(world, tgt))
                    if (pp.recipeId().equals(cr)) return CraftPlanner.wantsOfCached(pp); // m343 手选也认槽位替代材料；m357 长期缓存版（每拍现建集合下岗，审计⑤轮①唯一真缺口）
                return CraftPlanner.wants(world, tgt);
            });
            return needs.contains(id);
        } else if (isDef(st, com.sdzjz.machine.Machines.BREWING_TOWER)) {
            // m132 顺修：m131b 漏接链式需求——存储→过滤器→酿造塔 的拉料此前恒 false
            // （落进下方通用 MachineItem 分支，免费型 def 不吃供料）。语义照 accepts：材料+燃料。
            String tgtB = com.sdzjz.node.NodeTags.craftTarget(st);
            if (tgtB.isEmpty()) return false;
            var planB = com.sdzjz.machine.BrewPlanner.plan(world, tgtB);
            return planB != null && (planB.needs().containsKey(id) || com.sdzjz.machine.BrewPlanner.FUEL_ID.equals(id));
        } else if (isDef(st, com.sdzjz.machine.Machines.ENCHANT_FACTORY)) {
            // m132：附魔工厂链式需求=书+青金石（经验非物品不走线）。
            String tgtE = com.sdzjz.node.NodeTags.craftTarget(st);
            if (tgtE.isEmpty()) return false;
            var planE = com.sdzjz.machine.EnchantPlanner.plan(world, tgtE);
            return planE != null && planE.needs().containsKey(id);
        } else if (com.sdzjz.node.NodeTags.defOf(st) != null) {
            MachineDef mi = com.sdzjz.node.NodeTags.defOf(st);
            com.sdzjz.machine.MachineDef def = mi;
            if (com.sdzjz.machine.Machines.smelterFamily(def.id())) // m173 熔炉族
                return com.sdzjz.machine.SmeltPlanner.resultOf(world, id) != null
                        && com.sdzjz.node.NodeTags.machineFilterAllows(st, id); // m149 滤掉的不收，留上游走默认路由
            if (def.consumesInputs()) {
                for (var in : def.inputs()) if (in.item().equals(id)) return true;
                return false;
            }
            return false; // 免费产出机不吃供料
        } else {
            return false; // 农场/笼子等
        }
        int[] tsW = i < outT.length ? outT[i] : null; // m355 数组化（旧 getOrDefault 空表=null 同义）
        if (tsW != null)
            for (int t : tsW)
                if (chainWants(host, world, t, id, depth + 1, visited, outT, crafterNeeds)) return true;
        return false;
    }

    /** m133：从当前端点+定向连线重算待加载区块清单（并入+miss衰减：连续24拍(≈2分钟)未见才剔除，
     *  重启自举期登记表为空不误删；上限64区块；自身区块走 FORCED 不占票）。 */
}
