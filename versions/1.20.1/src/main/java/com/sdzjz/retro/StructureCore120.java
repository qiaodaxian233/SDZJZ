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
