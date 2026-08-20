package com.sdzjz.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * m332 随身仓库专属仓位（作者点名"物品栏新增一个地方放，兼容功能"）。
 *
 * 三件一档：
 *  - 本类 = 只收随身仓库的槽（格上限恒 1；m310 SlotMaxCountMixin 打在超类无参口，本覆写直返不经那条路）；
 *  - {@link State} = 服务端账面，SavedData 按玩家 UUID 落盘（挂主世界一份）。账面**不进 PlayerInventory**：
 *    死亡不掉（贴身口袋语义）、换维度/重进服跟人、keepInventory 无关——零 copyFrom/dropAll 边角；
 *  - {@link Inv} = 1 格视图。服务端逐访问懒解析到 State（PlayerScreenHandler 在玩家实体构造期就建，
 *    彼时 server 引用未必就绪，绝不在构造期碰盘）；客户端本地栈由原版槽位同步喂
 *    （playerScreenHandler 每 playerTick 恒广播，开着别的屏也照样同步——m312 零 S2C 的同款白捡）。
 *
 * 兼容面：吸附走 Sdzjz 服务端 tick 钩（仓位不在 PlayerInventory，原版 inventoryTick 轮不到它）；
 * 开屏走 PortableVaultItem.onClicked 仓位右键分支；取物屏 vault() 兜底到本仓位。
 */
public class PortableVaultSlot extends Slot {

    /** 追加在原版 46 槽（0..45，副手=45）之后的下标。 */
    public static final int SLOT_INDEX = 46;
    /** 屏内坐标：副手格 (77,62) 正上方一格（原版底图此处为素面）。 */
    public static final int SLOT_X = 77, SLOT_Y = 44;

    public PortableVaultSlot(Container inv) {
        super(inv, 0, SLOT_X, SLOT_Y);
    }

    @Override
    public boolean canInsert(ItemStack s) {
        return s.getItem() instanceof PortableVaultItem;
    }

    /** 恒 1（仓库本身 maxCount=1，此覆写是双保险且绕开 bigStacks 抬格）。 */
    @Override
    public int getMaxItemCount() {
        return 1;
    }

    /** 仓位栈统一读口（双端同源：都从 playerScreenHandler 追加格读；开关关/老版联机=空）。 */
    public static ItemStack stackOf(Player p) {
        var h = p.playerScreenHandler;
        if (h == null || h.slots.size() <= SLOT_INDEX) return ItemStack.EMPTY;
        ItemStack s = h.getSlot(SLOT_INDEX).getStack();
        return s.getItem() instanceof PortableVaultItem ? s : ItemStack.EMPTY;
    }

    /** 服务端账面：UUID → 仓位栈，主世界一份 SavedData（m296 声明表同刀法）。 */
    public static final class State extends SavedData {
        private final Map<UUID, ItemStack> byPlayer = new HashMap<>();

        public static final SavedData.Type<State> TYPE = new SavedData.Type<>(
                State::new, State::read, null);

        public static State read(CompoundTag nbt, HolderLookup.WrapperLookup lookup) {
            State s = new State();
            CompoundTag m = nbt.getCompound("slots");
            for (String k : m.getKeys()) {
                try {
                    UUID u = UUID.fromString(k);
                    ItemStack.fromNbt(lookup, m.getCompound(k)).ifPresent(st -> s.byPlayer.put(u, st));
                } catch (IllegalArgumentException ignored) { // 坏 UUID 键丢弃（m273 读入校验同律）
                }
            }
            return s;
        }

        @Override
        public CompoundTag writeNbt(CompoundTag nbt, HolderLookup.WrapperLookup lookup) {
            CompoundTag m = new CompoundTag();
            for (Map.Entry<UUID, ItemStack> e : byPlayer.entrySet())
                if (!e.getValue().isEmpty()) m.put(e.getKey().toString(), e.getValue().encode(lookup));
            nbt.put("slots", m);
            return nbt;
        }

        public static State of(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "sdzjz_vault_slot");
        }

        public ItemStack get(UUID u) {
            return byPlayer.getOrDefault(u, ItemStack.EMPTY);
        }

        public void set(UUID u, ItemStack s) {
            if (s.isEmpty()) byPlayer.remove(u); else byPlayer.put(u, s);
            markDirty();
        }
    }

    /** 1 格账面视图（服务端懒解析、客户端本地栈吃原版同步）。 */
    public static final class Inv implements Container {
        private final Player owner;
        private ItemStack clientStack = ItemStack.EMPTY;

        public Inv(Player owner) {
            this.owner = owner;
        }

        private State state() {
            MinecraftServer sv = owner.getServer();
            return sv == null ? null : State.of(sv);
        }

        private boolean serverSide() {
            return owner instanceof ServerPlayer;
        }

        @Override public int size() { return 1; }

        @Override public boolean isEmpty() { return getStack(0).isEmpty(); }

        @Override
        public ItemStack getStack(int slot) {
            if (!serverSide()) return clientStack;
            State st = state();
            if (st == null) return ItemStack.EMPTY;
            // 账本组件（吸附/收纳/取物）原地写在这枚实例上、不经 setStack；存档期序列化的正是同一实例，
            // dirty 位只决定"要不要落盘"——读口置脏保证任一存档周期都带上组件改动（小表重写代价可忽略）。
            st.markDirty();
            return st.get(owner.getUuid());
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            ItemStack cur = getStack(0);
            if (cur.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = cur.split(amount);
            setStack(0, cur); // split 后残量写回（清空即除账）
            return out;
        }

        @Override
        public ItemStack removeStack(int slot) {
            ItemStack cur = getStack(0);
            setStack(0, ItemStack.EMPTY);
            return cur;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (!serverSide()) { clientStack = stack; return; }
            State st = state();
            if (st != null) st.set(owner.getUuid(), stack);
        }

        @Override
        public void markDirty() {
            if (serverSide()) {
                State st = state();
                if (st != null) st.markDirty();
            }
        }

        @Override public boolean canPlayerUse(Player p) { return p == owner; }

        @Override public void clear() { setStack(0, ItemStack.EMPTY); }
    }
}
