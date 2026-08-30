package com.sdzjz.screen;

import com.sdzjz.block.DataCableBlockEntity;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * m226 数据线抽取口配置界面（扳手右键开）：9 个幽灵过滤槽 + 启停钮。
 * 幽灵槽=只登记模板不动真物品：光标有物品点槽=登记（count 恒 1、不消耗），空光标点槽=清除；
 * 背包物品 shift 点=登记进第一个空槽（物品原地不动，AE2 同款手感），过滤槽 shift/Q=清除。
 * 属性同步：[0]=抽取开关 [1]=邻接可对接存储数。按钮协议 id 0=启停切换。
 */
public class ExtractPortScreenHandler extends AbstractContainerMenu {

    public static final int FILTER = 9; // 过滤槽数 = DataCableBlockEntity 过滤模板上限（m225 ≤9 条口径）
    public static final int UPG = 3;    // m230 升级槽数（速度/数量/并发）
    // 几何收口常量（渲染与槽位同源，m215/m223 教训）：过滤行/升级行/背包首行 y 与升级行 x
    public static final int FILTER_Y = 80, UPG_X = 44, UPG_Y = 118, PINV_Y = 142; // m231 模式钮行整体下移 22

    private final DataCableBlockEntity be;
    private final SimpleContainer ghost = new SimpleContainer(FILTER); // 幽灵模板显示层（真数据在 BE.filter）
    private final ContainerData props;

    // 客户端
    public ExtractPortScreenHandler(int syncId, Inventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    // 服务端
    public ExtractPortScreenHandler(int syncId, Inventory playerInv, DataCableBlockEntity be) {
        super(ModScreenHandlers.EXTRACT_PORT, syncId);
        this.be = be;
        boolean server = !playerInv.player.level().isClientSide;
        if (be != null && server) { // 开屏把 BE 过滤模板灌进幽灵层，槽位内容走原版槽同步到客户端（零新协议）
            List<ItemStack> f = be.filterView();
            for (int i = 0; i < FILTER && i < f.size(); i++) ghost.setItem(i, f.get(i).copyWithCount(1));
        }
        this.props = (be != null && server) ? new ContainerData() { // 服务端实读 BE；邻接探测仅在开屏期间（m107a 面板同量级）
            @Override public int get(int index) {
                if (index == 0) return be.extractOn() ? 1 : 0;
                com.sdzjz.storage.ExtractPort.Adjacency adj = DataCableBlockEntity.scanAdjacent(be.getLevel(), be.getBlockPos()); // m502：Adjacency 迁共用件
                if (index == 1) return adj.blockCount(); // m228 计邻块数
                if (index == 2) {
                    if (be.pullMode() || !adj.sellTable()) return 0; // m229 出售状态：0=无桌（m231 回收模式桌不参与）
                    boolean ready = com.sdzjz.compat.ProjectEFCompat.available() && be.owner() != null
                            && be.getLevel().getServer() != null
                            && be.getLevel().getServer().getPlayerList().getPlayer(be.owner()) != null;
                    return ready ? 1 : 2;   // 1=出售中 2=桌在但未就绪（未认领/所有者离线/API 不可用）
                }
                if (index == 3) return Math.min(0x7FFF, be.effPeriod()); // m230 生效周期（钳 15 位护通道）
                // m232 生效批量拆低15+高位过 16 位属性通道（m106 教训：直发大数符号扩展成负数）；
                // 上限 2^30-1（高位自身也要过短通道），超出显示饱和为 1B+，搬运用的真值不受影响
                long b = Math.min((1L << 30) - 1, be.effBudget());
                if (index == 4) return (int) (b & 0x7FFF);
                if (index == 5) return (int) (b >> 15);
                return be.pullMode() ? 1 : 0; // m231 方向
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 7; }
        } : new SimpleContainerData(7);
        addDataSlots(props);

        // 幽灵过滤槽（0..8）：真栈进不来也拿不走——原版 SWAP/QUICK_CRAFT/PICKUP_ALL 路径全被这两钩子挡死
        for (int i = 0; i < FILTER; i++) {
            this.addSlot(new Slot(ghost, i, 8 + i * 18, FILTER_Y) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p) { return false; }
            });
        }
        // m230 升级槽（9..11，真槽）：0=速度 1=数量 2=并发，各只收对应升级件；级数=件数
        net.minecraft.world.SimpleContainer upgInv =
                (be != null) ? be.upgrades : new SimpleContainer(UPG); // 客户端 BE 自带同尺寸库存，槽同步灌显示
        for (int i = 0; i < UPG; i++) {
            final net.minecraft.world.item.Item want = i == 0 ? com.sdzjz.registry.ModItems.SPEED_UPGRADE
                    : i == 1 ? com.sdzjz.registry.ModItems.COUNT_UPGRADE : com.sdzjz.registry.ModItems.PARALLEL_UPGRADE;
            this.addSlot(new Slot(upgInv, i, UPG_X + i * 18, UPG_Y) {
                @Override public boolean mayPlace(ItemStack s) { return s.is(want); }
            });
        }
        // 玩家背包（12..38）+ 快捷栏（39..47）
        int px = 8, py = PINV_Y;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, px + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, px + c * 18, py + 58));
    }

    private static DataCableBlockEntity resolve(Inventory playerInv, BlockPos pos) {
        BlockEntity b = playerInv.player.level().getBlockEntity(pos);
        return b instanceof DataCableBlockEntity c ? c : null;
    }

    // 供客户端 Screen 读的状态
    public boolean extractOn()   { return props.get(0) != 0; }
    public int adjacentCount()   { return props.get(1); }
    /** m229 转化桌出售状态：0=无桌 1=出售中 2=桌在但未就绪。 */
    public int sellState()       { return props.get(2); }
    public int effPeriod()       { return props.get(3); } // m230
    /** m232 低15+高位拼回（SCBE 经验同款口径，规避属性 short 截断）。 */
    public long effBudget()      { return ((long) props.get(5) << 15) | (props.get(4) & 0x7FFFL); }
    public boolean pullMode()    { return props.get(6) != 0; } // m231

    @Override
    public void clicked(int slotIndex, int button, ClickType type, Player player) {
        if (slotIndex >= 0 && slotIndex < FILTER) { // 幽灵槽：只登记模板，真物品一件不进不出
            if (type == ClickType.PICKUP) {
                ItemStack cursor = getCarried(); // 有货=登记（不消耗光标），空=清除
                setFilter(slotIndex, cursor.isEmpty() ? ItemStack.EMPTY : cursor.copyWithCount(1), player);
            } else if (type == ClickType.QUICK_MOVE || type == ClickType.THROW) {
                setFilter(slotIndex, ItemStack.EMPTY, player); // shift 点/Q 键 = 清除模板
            } // CLONE/SWAP/QUICK_CRAFT/PICKUP_ALL：幽灵槽一律无操作（防创造中键把模板凭空复制成真栈）
            return;
        }
        super.clicked(slotIndex, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 升级槽（真槽）走真实移动；背包物品：升级件优先装升级槽，其余=登记过滤模板（原地不动不重复）
        if (index >= FILTER && index < FILTER + UPG) { // m230 升级槽 shift → 背包
            Slot slot = this.slots.get(index);
            if (slot != null && slot.hasItem()) {
                ItemStack st = slot.getItem();
                ItemStack ret = st.copy();
                if (!this.moveItemStackTo(st, FILTER + UPG, this.slots.size(), true)) return ItemStack.EMPTY;
                if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
                return ret;
            }
            return ItemStack.EMPTY;
        }
        if (index >= FILTER + UPG && index < this.slots.size()) {
            ItemStack st = this.slots.get(index).getItem();
            if (st.isEmpty()) return ItemStack.EMPTY;
            int upg = st.is(com.sdzjz.registry.ModItems.SPEED_UPGRADE) ? 0
                    : st.is(com.sdzjz.registry.ModItems.COUNT_UPGRADE) ? 1
                    : st.is(com.sdzjz.registry.ModItems.PARALLEL_UPGRADE) ? 2 : -1;
            if (upg >= 0) { // m230 升级件 shift = 装进对应升级槽（真实移动，装满余量留手）
                Slot slot = this.slots.get(index);
                ItemStack ret = st.copy();
                if (!this.moveItemStackTo(st, FILTER + upg, FILTER + upg + 1, false)) return ItemStack.EMPTY;
                if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
                return ret;
            }
            for (int i = 0; i < FILTER; i++)
                if (ItemStack.isSameItemSameComponents(ghost.getItem(i), st)) return ItemStack.EMPTY;
            for (int i = 0; i < FILTER; i++)
                if (ghost.getItem(i).isEmpty()) { setFilter(i, st.copyWithCount(1), player); break; }
        }
        return ItemStack.EMPTY;
    }

    /** 幽灵层写入 + 服务端权威落盘（onSlotClick 双端执行——m106/守则口径：客户端只改本地显示，
     *  BE 过滤与 markDirty 只在服务端做；落盘时压缩掉空位，重开界面模板左对齐属预期）。 */
    private void setFilter(int i, ItemStack tpl, Player player) {
        ghost.setItem(i, tpl);
        if (be != null && !player.level().isClientSide) {
            List<ItemStack> f = be.filterView();
            f.clear();
            for (int k = 0; k < FILTER; k++) {
                ItemStack s = ghost.getItem(k);
                if (!s.isEmpty()) f.add(s.copyWithCount(1));
            }
            be.setChanged();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null || player.level().isClientSide) return false;
        if (id == 0) { be.setExtractOn(!be.extractOn()); return true; } // 启停切换（m225 潜行右键同一开关）
        if (id == 1) { be.setPullMode(!be.pullMode()); return true; }   // m231 方向切换
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return be != null;
    }
}
