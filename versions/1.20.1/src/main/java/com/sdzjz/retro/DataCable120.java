package com.sdzjz.retro;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.machine.StorageLedgerProbe;
import com.sdzjz.storage.ExtractPort;
import com.sdzjz.storage.StackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * m444 刀③立 → m502（真移植 B4a）**降为世代壳**：抽取口业务（m225 逐模板抽/全部模式游标轮转/
 * m231 回收拍/m228 六面视图/m218b 核心 40t 缓存）整段迁 {@link ExtractPort} 两代共用，
 * 五口传输内脏迁 {@link RetroXfer}（FabricXfer 原文照搬，m434 门面本世代兑现，m440「不设门面」
 * 旧结论就此过期）。本文件只剩：BE 注册与存档签名（1.20.1 无 lookup）、tick 相位闸与
 * m469 端点自愈、m449 持物右键过滤器交互——都是世代/交互侧该各写的。
 *
 * <p>本世代裁剪不变（P-C 到期补，非漏抄）：m226 配置屏与开屏工厂、m229 所有者/EMC 出售通道
 * （宿主口默认关）、m230 升级槽（effPeriod/effBudget 退基础配置值，键同源 common）、
 * m233 按面断开（宿主口默认全通）。NBT 键 extractOn/pullMode/filter 与蓝本同名同布局。
 */
final class DataCable120 extends BlockEntity {

    private static final PortHost HOST = new PortHost(); // 无实例状态，全线共用一份
    private final ExtractPort port = new ExtractPort(HOST, this::setChanged); // m502 抽取口业务共用件
    private boolean endsHealed = false; // m469 端点自愈一次性闸（transient：每次加载重跑一遍，不落盘）

    DataCable120(BlockPos pos, BlockState state) {
        super(RetroBlocks.DATA_CABLE_BE, pos, state);
    }

    public boolean extractOn() { return port.extractOn(); }
    public void setExtractOn(boolean on) { port.setExtractOn(on); }
    public boolean pullMode() { return port.pullMode(); }
    public void setPullMode(boolean pull) { port.setPullMode(pull); } // m231

    /** 过滤模板视图（P-C 配置界面到位后读写；写入方自行 setChanged）。 */
    public List<ItemStack> filterView() { return port.filterView(); }

    // ===== m449 过滤器交互（配置屏 P-C 到位前的最小可用面；语义与 m225 一致：模板 count 恒 1，
    // 无 tag 模板=只对裸物品、带 tag=连 tag 精确匹配，空表=全抽/全收）=====
    public static final int FILTER_ADDED = 0, FILTER_REMOVED = 1, FILTER_FULL = 2;

    /** 加入/移出白名单（同款判定=StackKey.same，与共用件 pullWants/extractSpec 同口径——
     *  m502 起三处同走 m478 世代口，本世代实现即原来的 isSameItemSameTags，逐位不变）；
     *  上限 9 条（m226 蓝本容量同源）。改动即拓扑无关，无需作废核心缓存。 */
    public int filterToggle(ItemStack held) {
        if (held.isEmpty()) return FILTER_FULL; // 不可达（调用方已判），防御
        List<ItemStack> filter = port.filterView();
        for (int i = 0; i < filter.size(); i++) {
            if (StackKey.same(filter.get(i), held)) {
                filter.remove(i);
                setChanged();
                return FILTER_REMOVED;
            }
        }
        if (filter.size() >= 9) return FILTER_FULL;
        filter.add(held.copyWithCount(1));
        setChanged();
        return FILTER_ADDED;
    }

    /** 清空白名单（回全抽/全收态）。 */
    public void filterClear() {
        if (port.filterView().isEmpty()) return;
        port.filterView().clear();
        setChanged();
    }

    /** m225 抽取口主拍：pos 哈希移相（m218c 口径，多口不挤同一全局 tick），周期与批量走 common
     *  配置同源键（extractPortPeriodTicks/extractPortBatch，m230 升级槽随 P-C——本世代基础值）。 */
    public static void tick(Level world, BlockPos pos, BlockState state, DataCable120 be) {
        if (world.isClientSide) return;
        if (!be.endsHealed) be.endsHealed = DataCableBlock120.healEnds(world, pos, state); // m469 旧档端点自愈（一次性，不落盘）
        if (!be.port.extractOn()) return;
        int period = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        if (Math.floorMod(world.getGameTime() + pos.hashCode(), period) != 0) return;
        be.pulse(world, pos);
    }

    /** 单拍主体（tick 相位闸之后的全部工作；拆出来让 GameTest 免等相位确定性直调）。
     *  m502：分派/抽取/回收全走共用件，本方法只剩「算 targets/cores/budget」三件世代前菜。 */
    void pulse(Level world, BlockPos pos) {
        List<Object> targets = ExtractPort.scanAdjacent(HOST, world, pos).targets();
        if (targets.isEmpty()) return;
        List<StorageLedgerProbe> cores = port.cores(world, pos);
        if (cores.isEmpty()) return;
        long budget = Math.max(1, SdzjzConfig.get().extractPortBatch);
        port.runPulse(targets, cores, budget);
    }

    /** m502 宿主口：核心网络三口（本世代类型）；m229 卖桌/m233 按面断开钩子走默认关（主线专属功能）。 */
    private static final class PortHost implements ExtractPort.Host {
        @Override
        public List<BlockPos> scanCores(Level world, BlockPos pos) {
            List<BlockPos> ps = new ArrayList<>();
            for (StorageCore120 c : StorageCore120.connectedCores(world, pos)) ps.add(c.getBlockPos().immutable());
            return ps;
        }

        @Override
        public StorageLedgerProbe coreAt(Level world, BlockPos p) {
            return StorageCore120.loadedCoreAt(world, p);
        }

        @Override
        public Object coreStorage(StorageLedgerProbe core) {
            return ((StorageCore120) core).fabricStorage(); // m161c FTA 出口（回收拍目的地）
        }
    }

    // ===== NBT：1.20.1 签名（m443 同款），键 extractOn/pullMode/filter 与蓝本同名同布局
    // （owner/offFaces/upN 三组键随 P-C 的 m229/m233/m230 到期再写，缺键读=各字段默认，向前兼容）=====
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putBoolean("extractOn", port.extractOn());
        nbt.putBoolean("pullMode", port.pullMode()); // m231 方向
        ListTag fl = new ListTag(); // 过滤模板持久化（精确账本 m130 同款 encode）
        for (ItemStack f : port.filterView()) if (!f.isEmpty()) fl.add(f.save(new CompoundTag()));
        nbt.put("filter", fl);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        port.loadState(nbt.getBoolean("extractOn"), nbt.getBoolean("pullMode")); // m231；读档不触发 setChanged（口径同旧）
        port.filterView().clear();
        ListTag fl = nbt.getList("filter", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            ItemStack t = ItemStack.of(fl.getCompound(i)); // 1.20.1 对位 parse().orElse（失败即 EMPTY）
            if (!t.isEmpty()) port.filterView().add(t.copyWithCount(1)); // 解析失败/物品已卸载静默跳过，不炸档
        }
    }
}
