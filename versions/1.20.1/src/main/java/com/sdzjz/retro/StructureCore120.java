package com.sdzjz.retro;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * m454（C2-③）：结构核心（画布）BE 骨架——语义蓝本=Legacy SCBE（3965 行）的**图状态与拓扑操作
 * 子集**：持唯一图实例 g（蓝本 m430 同构）+ 节点挂/摘/连/断四操作（摘除簿记逐字对照蓝本
 * detachNode 的剪线/移位段）。生产 tick 五分支、在途缓存 nodeBufs、供料/分发/链需求全数随
 * C2-④+ 分片来（蓝本对应段=范围外非漏抄）；无屏无 payload，摆得下存得住是本刀验收线。
 */
final class StructureCore120 extends BlockEntity {

    /** m143 合并机映射：1.20.1 无史前存档常态空表（CanvasGraphState120 类注），接线点保形。 */
    private static final Map<String, String> MERGED_IDS = Map.of();

    final CanvasGraphState120 g = new CanvasGraphState120(); // 蓝本同名：唯一图实例
    long endpointScanTick = Long.MIN_VALUE; // m458：端点扫描 40t 缓存戳（m218b 谱系防逐观众裸扫）

    StructureCore120(BlockPos pos, BlockState state) {
        super(RetroBlocks.STRUCTURE_CORE_BE, pos, state);
    }

    /** 挂节点：栈上画布坐标等随栈 NBT 走（xc/yc/gp 口径，NodeTags 谱系）；状态灯/原因同步补位。 */
    void addNode(ItemStack nodeStack) {
        if (nodeStack.isEmpty()) return;
        g.machineNodes.add(nodeStack.copyWithCount(1));
        g.nodeStatus.add(0);
        g.nodeReason.add("");
        setChanged();
    }

    /** 摘节点（蓝本 detachNode 剪线/移位段逐字对照；在途缓存回收支路随 C2-④ 的 nodeBufs 来）：
     *  机器线触删即断、下标大于被删位整体左移；存储线同剪同移。返回被摘栈（归还调用方自理）。 */
    ItemStack detachNode(int index) {
        if (index < 0 || index >= g.machineNodes.size()) return ItemStack.EMPTY;
        ItemStack s = g.machineNodes.remove(index);
        if (index < g.nodeStatus.size()) g.nodeStatus.remove(index);
        if (index < g.nodeReason.size()) g.nodeReason.remove(index); // m178
        List<int[]> kept = new ArrayList<>();
        for (int[] c : g.connections) {
            if (c[0] == index || c[1] == index) continue; // 触及被删节点→断
            int a = c[0] > index ? c[0] - 1 : c[0];
            int b = c[1] > index ? c[1] - 1 : c[1];
            kept.add(new int[]{a, b});
        }
        g.connections.clear();
        g.connections.addAll(kept);
        for (int i = g.storageEdges.size() - 1; i >= 0; i--) { // 存储连线同剪/移位（蓝本同段）
            long[] e = g.storageEdges.get(i);
            if (e[0] == index) { g.storageEdges.remove(i); g.storageEdgeDims.remove(i); }
            else if (e[0] > index) e[0]--;
        }
        setChanged();
        return s;
    }

    /** 连线（定向 from→to）：越界/自连/同向重复拒绝，返回是否成立。 */
    boolean connect(int from, int to) {
        int n = g.machineNodes.size();
        if (from < 0 || to < 0 || from >= n || to >= n || from == to) return false;
        for (int[] c : g.connections) if (c[0] == from && c[1] == to) return false; // 同向重复
        g.connections.add(new int[]{from, to});
        setChanged();
        return true;
    }

    /** 断线（精确匹配定向对）。 */
    boolean disconnect(int from, int to) {
        for (int i = 0; i < g.connections.size(); i++) {
            int[] c = g.connections.get(i);
            if (c[0] == from && c[1] == to) {
                g.connections.remove(i);
                setChanged();
                return true;
            }
        }
        return false;
    }

    // ===== m464（C2-⑤a）+ m466（C2-⑤b）：生产 tick 脊柱 + 数据驱动族（生成类+耗料类）=====
    // 蓝本=SCBE tickInner 的最小可产集（m463 普查分片）：预算折算（cyclesThisTick 四层闸逐位对照，
    // 世代差=无速度升级 rate 恒 1.0、无并发升级恒 1 台）→ 通用 MachineItem 分支两半：
    // consumesInputs=false 半（rollDrops → kind0 产出仓 deposit）；=true 半（m466：kind1 供料仓
    // 蓝本式按料折算 doCycles → withdraw → 产出同前）→ 灯表说人话。
    // 世代取舍（m463 记档）：①本世代核心**自动运转**（有节点即 tick，开停闸随屏侧到序）——
    // 产出仓连线（m458 手势）本身就是玩家显式授权；②产出必须接仓，无仓=红灯待机（无输出缓存/
    // 断网喷射，零吞件零实体洪水）；③特种/配方机型（def 产表为空或熔炉族）黄灯待后续分片；
    // ④组件产物（山羊角）随 ⑤d 精确账本对接；⑤产出仓类型满：免费产物=折损黄灯（料本免费不产
    // 不损）；耗料机=扣料前先验类型余量待机（m466 护栏，白耗料不可接受；两机同拍抢最后一个
    // 类型位的残余竞态仍走折损黄灯，⑤c 输出缓存到序即根治）；⑥MachineXp 经验产出（蓝本
    // xpPool += mxp）随 ⑤e 经验经济族到序——本世代耗料机只产物品不攒经验（显式记档非漏抄）；
    // ⑦供料侧无 m340 topUpSource/机器连线缓存（hasIn 分支）——nodeBufs 在途缓存随 ⑤c。
    private transient double[] workAcc = new double[0]; // 蓝本 m356 数组直取（不落盘：重启丢半周期无妨）
    private transient long recipesThisTick;             // 蓝本 m270 全核 tick 周期预算游标

    static void tick(net.minecraft.world.level.Level world, BlockPos pos, BlockState state, StructureCore120 be) {
        if (world.isClientSide) return;
        be.recipesThisTick = 0;
        com.sdzjz.config.SdzjzConfig cfg = com.sdzjz.config.SdzjzConfig.get();
        for (int i = 0; i < be.g.machineNodes.size(); i++) {
            ItemStack st = be.g.machineNodes.get(i);
            if (!(st.getItem() instanceof RetroMachineItems.RetroMachineItem rmi)) { be.stat(i, 0, ""); continue; }
            com.sdzjz.machine.MachineDef def = rmi.def;
            if (com.sdzjz.machine.Machines.smelterFamily(def.id()) || def.outputs().isEmpty()) {
                be.stat(i, 2, "配方/特种机型随 C2-⑤ 后续分片到序（本世代暂只跑数据驱动生成类）");
                continue;
            }
            StorageCore120 dep = be.depositTarget(world, i);
            if (dep == null) { be.stat(i, 3, "未连产出仓（画布连线模式：点机器再点仓=绿线产出）"); continue; }
            StorageCore120 sup = null;
            if (def.consumesInputs()) { // ===== m466（C2-⑤b）：数据驱动耗料类 =====
                sup = be.supplyTarget(world, i); // 蓝本 supplyFor 的世代精简（kind1，m458 循环手势：绿线再点=金线供料）
                if (sup == null) { be.stat(i, 3, "未连供料仓（画布连线模式：点机器再点仓，再点循环到金线=供料）"); continue; }
                boolean typeRoom = true; // 护栏：先验产出仓类型余量再扣料——⑤a 取舍⑤"免费产物折损"到耗料机=白耗料，不可接受；
                for (com.sdzjz.machine.MachineDef.Drop d : def.outputs()) { // 概率产物（如龙蛋 0.005）也计入先验=宁待机不烧料
                    if ("minecraft:goat_horn".equals(d.item())) continue;
                    if (!dep.acceptsPlainType(d.item())) { typeRoom = false; break; }
                }
                if (!typeRoom) { be.stat(i, 2, "产出仓类型已满，耗料机不白耗料先待机（清账本类型/换仓即恢复）"); continue; }
            }
            int cycles = be.cyclesThisTick(world, i, def.baseIntervalTicks(), cfg);
            if (cycles <= 0) continue; // 预算剪零已亮黄说话；没攒够周期=保持上拍灯
            int doCycles = cycles;
            if (def.consumesInputs()) { // 蓝本 m99：料不够整批时按料量折算周期数（running 恒 1 世代差）
                for (com.sdzjz.machine.MachineDef.Input in : def.inputs())
                    doCycles = (int) Math.min(doCycles, sup.count(in.item()) / (long) in.count());
                if (doCycles <= 0) { be.stat(i, 3, whyMissingSup(sup, def.inputs())); continue; }
                for (com.sdzjz.machine.MachineDef.Input in : def.inputs())
                    sup.withdraw(in.item(), in.count() * doCycles); // 蓝本同式（int 乘，cap 钳过不溢）
                be.stat(i, 1, ""); // 蓝本位次：扣料即点绿——全概率产表（如猪灵交易）本拍全没掷中也不滞留旧灯
            }
            boolean skippedComponent = false, produced = false;
            for (com.sdzjz.machine.MachineDef.Drop d : def.outputs()) {
                if ("minecraft:goat_horn".equals(d.item())) { skippedComponent = true; continue; } // 组件产物 ⑤d
                long sum = com.sdzjz.machine.DropRolls.rollDrops(world.getRandom(), d, doCycles, 0);
                while (sum > 0) {
                    int n = (int) Math.min(sum, Integer.MAX_VALUE);
                    ItemStack outSt = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .get(new net.minecraft.resources.ResourceLocation(d.item())), n);
                    dep.deposit(outSt); // 收下清空；类型满原样留着
                    if (!outSt.isEmpty()) { // 免费产物折损（见类注取舍⑤）
                        be.stat(i, 2, "产出仓类型已满，产出折损中（清账本类型/换仓即恢复）");
                        sum = -1; // 哨兵：跳出且不点绿
                        break;
                    }
                    sum -= n;
                    produced = true;
                }
                if (sum < 0) { produced = false; break; }
            }
            if (produced) be.stat(i, 1, "");
            else if (skippedComponent) be.stat(i, 2, "组件产物机型随 C2-⑤d（精确账本对接）到序");
        }
    }

    /** 蓝本 cyclesThisTick 四层闸逐位对照（节点 cap→核内→区块→全服，耗尽只欠不丢工作量累积续）；
     *  世代差：无速度升级 rate 恒 1.0（蓝本 rateOf 查表跳过）。 */
    private int cyclesThisTick(net.minecraft.world.level.Level world, int nodeIndex, int baseInterval,
            com.sdzjz.config.SdzjzConfig cfg) {
        int base = Math.max(1, baseInterval);
        int cap = Math.max(1, cfg.upgradeMaxCyclesPerTick);
        double acc = (nodeIndex < workAcc.length ? workAcc[nodeIndex] : 0.0) + 1.0;
        int cycles = (int) (acc / base);
        if (cycles > cap) cycles = cap;
        boolean hadWork = cycles > 0; // 蓝本 m339：区分"没攒够周期"与"被预算剪没"
        if (cfg.maxRecipesPerCoreTick > 0) { // 蓝本 m270
            long remain = cfg.maxRecipesPerCoreTick - recipesThisTick;
            if (remain <= 0) cycles = 0;
            else if (cycles > remain) cycles = (int) remain;
        }
        boolean chunkGate = cfg.maxRecipesPerChunkTick > 0
                && world instanceof net.minecraft.server.level.ServerLevel;
        if (cycles > 0 && chunkGate) { // 蓝本 m324：全服申请前按区块余量钳
            long head = com.sdzjz.machine.CoreScheduler.chunkHeadroom(
                    world.dimension().location().toString(), net.minecraft.world.level.ChunkPos.asLong(worldPosition),
                    cfg.maxRecipesPerChunkTick, ((net.minecraft.server.level.ServerLevel) world).getServer().getTickCount());
            if (cycles > head) cycles = (int) Math.max(0L, head);
        }
        if (cycles > 0 && cfg.maxRecipesPerNetworkTick > 0
                && world instanceof net.minecraft.server.level.ServerLevel sw) { // 蓝本 m302 全服闸+饥饿保底
            cycles = com.sdzjz.machine.CoreScheduler.request(sw.dimension().location().toString(),
                    worldPosition.asLong(), cycles, cfg.maxRecipesPerNetworkTick, sw.getServer().getTickCount());
        }
        if (cycles > 0 && chunkGate) { // 蓝本 m324 终账（先记后裁会虚耗区块账）
            com.sdzjz.machine.CoreScheduler.chunkCharge(world.dimension().location().toString(),
                    net.minecraft.world.level.ChunkPos.asLong(worldPosition), cycles,
                    ((net.minecraft.server.level.ServerLevel) world).getServer().getTickCount());
        }
        acc -= (double) cycles * base;
        if (acc > (double) base * cap) acc = (double) base * cap; // 被 cap/预算截断时不无限囤积
        if (nodeIndex >= workAcc.length)
            workAcc = java.util.Arrays.copyOf(workAcc, Math.max(nodeIndex + 1, Math.max(8, workAcc.length * 2)));
        workAcc[nodeIndex] = acc;
        recipesThisTick += cycles;
        if (hadWork && cycles == 0)
            stat(nodeIndex, 2, "生产预算本拍已满（核内/区块/全服），工作量已排队下拍续");
        return cycles;
    }

    /** kind0（产出）连线目标（蓝本 depositFor 的世代精简）。 */
    StorageCore120 depositTarget(net.minecraft.world.level.Level world, int machineIndex) {
        return edgeTarget(world, machineIndex, 0);
    }

    /** kind1（供料）连线目标（蓝本 supplyFor→edgeStorage 的世代精简，m466）。 */
    StorageCore120 supplyTarget(net.minecraft.world.level.Level world, int machineIndex) {
        return edgeTarget(world, machineIndex, 1);
    }

    /** 边表定向解析：同维度已加载存储核心，首中即用（蓝本 edgeStorage 同构）。 */
    private StorageCore120 edgeTarget(net.minecraft.world.level.Level world, int machineIndex, int kind) {
        String dim = world.dimension().location().toString();
        for (int e = 0; e < g.storageEdges.size(); e++) {
            long[] edge = g.storageEdges.get(e);
            if (edge[0] != machineIndex || edge[2] != kind) continue;
            if (e < g.storageEdgeDims.size() && !dim.equals(g.storageEdgeDims.get(e))) continue;
            StorageCore120 sc = StorageCore120.loadedCoreAt(world, BlockPos.of(edge[1]));
            if (sc != null) return sc;
        }
        return null;
    }

    /** 缺料说人话（蓝本 whyMissingSupIn 世代版：running 恒 1；名称走 getHoverName 同蓝本 itemName）。 */
    private static String whyMissingSup(StorageCore120 sup, java.util.List<com.sdzjz.machine.MachineDef.Input> ins) {
        for (com.sdzjz.machine.MachineDef.Input in : ins) {
            long have = sup.count(in.item());
            if (have < in.count()) return "缺料：" + itemName120(in.item()) + "（仓 " + have + "/需 " + in.count() + "）";
        }
        return "缺料（仓不足）";
    }

    private static String itemName120(String id) {
        try {
            return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(new net.minecraft.resources.ResourceLocation(id))).getHoverName().getString();
        } catch (Exception e) { return id; }
    }

    /** 灯表写入（蓝本 stat/statR 合口）：值不变不置脏（tick 每拍跑，setChanged 去重防落盘风暴）。 */
    void stat(int i, int code, String why) {
        while (g.nodeStatus.size() <= i) g.nodeStatus.add(0);
        while (g.nodeReason.size() <= i) g.nodeReason.add("");
        if (g.nodeStatus.get(i) == code && g.nodeReason.get(i).equals(why)) return;
        g.nodeStatus.set(i, code);
        g.nodeReason.set(i, why);
        setChanged();
    }

    // ===== NBT：渲染子集即存档画布段（蓝本"存档 writeNbt 与画布快照共用"同构），键在 BE 根层
    // 与蓝本同位同名（m443 DFU 红利口径）。=====
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        g.writeRenderNbt(nbt);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        g.readRenderNbt(nbt, MERGED_IDS, () -> { }); // 拓扑翻代消费方随 C2-④ 接 bumpTopo
    }
}
