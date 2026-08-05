package com.sdzjz.screen;

import com.sdzjz.block.DataCableBlockEntity;
import com.sdzjz.registry.ModScreenHandlers;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * m226 数据线抽取口配置界面（扳手右键开）：9 个幽灵过滤槽 + 启停钮。
 * 幽灵槽=只登记模板不动真物品：光标有物品点槽=登记（count 恒 1、不消耗），空光标点槽=清除；
 * 背包物品 shift 点=登记进第一个空槽（物品原地不动，AE2 同款手感），过滤槽 shift/Q=清除。
 * 属性同步：[0]=抽取开关 [1]=邻接可对接存储数。按钮协议 id 0=启停切换。
 */
public class ExtractPortScreenHandler extends ScreenHandler {

    public static final int FILTER = 9; // 过滤槽数 = DataCableBlockEntity 过滤模板上限（m225 ≤9 条口径）
    public static final int UPG = 3;    // m230 升级槽数（速度/数量/并发）
    // 几何收口常量（渲染与槽位同源，m215/m223 教训）：过滤行/升级行/背包首行 y 与升级行 x
    public static final int FILTER_Y = 80, UPG_X = 44, UPG_Y = 118, PINV_Y = 142; // m231 模式钮行整体下移 22

    private final DataCableBlockEntity be;
    private final SimpleInventory ghost = new SimpleInventory(FILTER); // 幽灵模板显示层（真数据在 BE.filter）
    private final PropertyDelegate props;

    // 客户端
    public ExtractPortScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, resolve(playerInv, pos));
    }

    // 服务端
    public ExtractPortScreenHandler(int syncId, PlayerInventory playerInv, DataCableBlockEntity be) {
        super(ModScreenHandlers.EXTRACT_PORT, syncId);
        this.be = be;
        boolean server = !playerInv.player.getWorld().isClient;
        if (be != null && server) { // 开屏把 BE 过滤模板灌进幽灵层，槽位内容走原版槽同步到客户端（零新协议）
            List<ItemStack> f = be.filterView();
            for (int i = 0; i < FILTER && i < f.size(); i++) ghost.setStack(i, f.get(i).copyWithCount(1));
        }
        this.props = (be != null && server) ? new PropertyDelegate() { // 服务端实读 BE；邻接探测仅在开屏期间（m107a 面板同量级）
            @Override public int get(int index) {
                if (index == 0) return be.extractOn() ? 1 : 0;
                DataCableBlockEntity.Adjacency adj = DataCableBlockEntity.scanAdjacent(be.getWorld(), be.getPos());
                if (index == 1) return adj.blockCount(); // m228 计邻块数
                if (index == 2) {
                    if (be.pullMode() || !adj.sellTable()) return 0; // m229 出售状态：0=无桌（m231 回收模式桌不参与）
                    boolean ready = com.sdzjz.compat.ProjectEFCompat.available() && be.owner() != null
                            && be.getWorld().getServer() != null
                            && be.getWorld().getServer().getPlayerManager().getPlayer(be.owner()) != null;
                    return ready ? 1 : 2;   // 1=出售中 2=桌在但未就绪（未认领/所有者离线/API 不可用）
                }
                if (index == 3) return be.effPeriod(); // m230 生效周期（升级实时反映）
                if (index == 4) return (int) Math.min(Integer.MAX_VALUE, be.effBudget()); // m230 生效批量
                return be.pullMode() ? 1 : 0; // m231 方向
            }
            @Override public void set(int index, int value) {}
            @Override public int size() { return 6; }
        } : new ArrayPropertyDelegate(6);
        addProperties(props);

        // 幽灵过滤槽（0..8）：真栈进不来也拿不走——原版 SWAP/QUICK_CRAFT/PICKUP_ALL 路径全被这两钩子挡死
        for (int i = 0; i < FILTER; i++) {
            this.addSlot(new Slot(ghost, i, 8 + i * 18, FILTER_Y) {
                @Override public boolean canInsert(ItemStack s) { return false; }
                @Override public boolean canTakeItems(PlayerEntity p) { return false; }
            });
        }
        // m230 升级槽（9..11，真槽）：0=速度 1=数量 2=并发，各只收对应升级件；级数=件数
        net.minecraft.inventory.SimpleInventory upgInv =
                (be != null) ? be.upgrades : new SimpleInventory(UPG); // 客户端 BE 自带同尺寸库存，槽同步灌显示
        for (int i = 0; i < UPG; i++) {
            final net.minecraft.item.Item want = i == 0 ? com.sdzjz.registry.ModItems.SPEED_UPGRADE
                    : i == 1 ? com.sdzjz.registry.ModItems.COUNT_UPGRADE : com.sdzjz.registry.ModItems.PARALLEL_UPGRADE;
            this.addSlot(new Slot(upgInv, i, UPG_X + i * 18, UPG_Y) {
                @Override public boolean canInsert(ItemStack s) { return s.isOf(want); }
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

    private static DataCableBlockEntity resolve(PlayerInventory playerInv, BlockPos pos) {
        BlockEntity b = playerInv.player.getWorld().getBlockEntity(pos);
        return b instanceof DataCableBlockEntity c ? c : null;
    }

    // 供客户端 Screen 读的状态
    public boolean extractOn()   { return props.get(0) != 0; }
    public int adjacentCount()   { return props.get(1); }
    /** m229 转化桌出售状态：0=无桌 1=出售中 2=桌在但未就绪。 */
    public int sellState()       { return props.get(2); }
    public int effPeriod()       { return props.get(3); } // m230
    public int effBudget()       { return props.get(4); } // m230
    public boolean pullMode()    { return props.get(5) != 0; } // m231

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < FILTER) { // 幽灵槽：只登记模板，真物品一件不进不出
            if (type == SlotActionType.PICKUP) {
                ItemStack cursor = getCursorStack(); // 有货=登记（不消耗光标），空=清除
                setFilter(slotIndex, cursor.isEmpty() ? ItemStack.EMPTY : cursor.copyWithCount(1), player);
            } else if (type == SlotActionType.QUICK_MOVE || type == SlotActionType.THROW) {
                setFilter(slotIndex, ItemStack.EMPTY, player); // shift 点/Q 键 = 清除模板
            } // CLONE/SWAP/QUICK_CRAFT/PICKUP_ALL：幽灵槽一律无操作（防创造中键把模板凭空复制成真栈）
            return;
        }
        super.onSlotClick(slotIndex, button, type, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // 升级槽（真槽）走真实移动；背包物品：升级件优先装升级槽，其余=登记过滤模板（原地不动不重复）
        if (index >= FILTER && index < FILTER + UPG) { // m230 升级槽 shift → 背包
            Slot slot = this.slots.get(index);
            if (slot != null && slot.hasStack()) {
                ItemStack st = slot.getStack();
                ItemStack ret = st.copy();
                if (!this.insertItem(st, FILTER + UPG, this.slots.size(), true)) return ItemStack.EMPTY;
                if (st.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
                return ret;
            }
            return ItemStack.EMPTY;
        }
        if (index >= FILTER + UPG && index < this.slots.size()) {
            ItemStack st = this.slots.get(index).getStack();
            if (st.isEmpty()) return ItemStack.EMPTY;
            int upg = st.isOf(com.sdzjz.registry.ModItems.SPEED_UPGRADE) ? 0
                    : st.isOf(com.sdzjz.registry.ModItems.COUNT_UPGRADE) ? 1
                    : st.isOf(com.sdzjz.registry.ModItems.PARALLEL_UPGRADE) ? 2 : -1;
            if (upg >= 0) { // m230 升级件 shift = 装进对应升级槽（真实移动，装满余量留手）
                Slot slot = this.slots.get(index);
                ItemStack ret = st.copy();
                if (!this.insertItem(st, FILTER + upg, FILTER + upg + 1, false)) return ItemStack.EMPTY;
                if (st.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
                return ret;
            }
            for (int i = 0; i < FILTER; i++)
                if (ItemStack.areItemsAndComponentsEqual(ghost.getStack(i), st)) return ItemStack.EMPTY;
            for (int i = 0; i < FILTER; i++)
                if (ghost.getStack(i).isEmpty()) { setFilter(i, st.copyWithCount(1), player); break; }
        }
        return ItemStack.EMPTY;
    }

    /** 幽灵层写入 + 服务端权威落盘（onSlotClick 双端执行——m106/守则口径：客户端只改本地显示，
     *  BE 过滤与 markDirty 只在服务端做；落盘时压缩掉空位，重开界面模板左对齐属预期）。 */
    private void setFilter(int i, ItemStack tpl, PlayerEntity player) {
        ghost.setStack(i, tpl);
        if (be != null && !player.getWorld().isClient) {
            List<ItemStack> f = be.filterView();
            f.clear();
            for (int k = 0; k < FILTER; k++) {
                ItemStack s = ghost.getStack(k);
                if (!s.isEmpty()) f.add(s.copyWithCount(1));
            }
            be.markDirty();
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (be == null || player.getWorld().isClient) return false;
        if (id == 0) { be.setExtractOn(!be.extractOn()); return true; } // 启停切换（m225 潜行右键同一开关）
        if (id == 1) { be.setPullMode(!be.pullMode()); return true; }   // m231 方向切换
        return false;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return be != null;
    }
}
