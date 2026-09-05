package com.sdzjz.block;

import com.sdzjz.config.SdzjzConfig;
import com.sdzjz.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 数据线方块实体：能量脉冲动画的渲染载体（BER 挂点）；m225 起兼作「抽取口」——扳手启用后，
 *  周期性把相连存储核心账本里的物品（按过滤，空=全部）塞进邻接的任意 Fabric Transfer API 存储。
 *  m226：兼作抽取口配置屏的开屏工厂（扳手右键 openHandledScreen，数据面板同款三方法）。 */
public class DataCableBlockEntity extends BlockEntity
        implements com.sdzjz.loader.MenuData<BlockPos> {

    // ===== m225 抽取口状态：m502（真移植 B4a）整段迁 com.sdzjz.storage.ExtractPort 两代共用
    // （extractOn/filter/rrCursor/opTargetsFull/coresScanTick/coresCache/pullMode 七件与
    // 抽取/回收/邻接视图/核心缓存全部业务；本 BE 只剩宿主口与主线专属功能 m229/m230/m233）=====
    private final PortHost portHost = new PortHost();
    private final com.sdzjz.storage.ExtractPort port = new com.sdzjz.storage.ExtractPort(portHost, this::setChanged);
    private java.util.UUID owner = null;                       // m229 端口所有者（EMC 记谁账上；配置即认领）
    /** m230 升级槽（0=速度 1=数量 2=并发；级数=槽内件数，配置界面装取）。 */
    public final net.minecraft.world.SimpleContainer upgrades = new net.minecraft.world.SimpleContainer(3);
    private int offFaces = 0;         // m233 按面断开位掩码（bit=Direction.get3DDataValue()；链接器潜行右键手臂切）
    private net.minecraft.server.level.ServerPlayer opSeller = null; // 本拍在线卖手（瞬态不持久化）

    public DataCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_CABLE_BE, pos, state);
        upgrades.addListener(inv -> this.setChanged()); // m230 升级槽改动落盘（markDirty 自带 world==null 守卫）
    }

    /** m230 生效周期：基础周期 ÷ (1+速度级)，触底 1t（触底后的富余速度折进单拍批量，见 effBudget——
     *  m99 教训"到顶之后玩家再投入会怎样"：每一件升级永远有增益，绝不静默无效）。 */
    public int effPeriod() {
        int base = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        return Math.max(1, base / (1 + upgrades.getItem(0).getCount()));
    }

    /** m230 单拍批量：基础批量 × (1+数量级) × (1+并发级) × 速度触底折算倍率。 */
    public long effBudget() {
        int base = Math.max(1, SdzjzConfig.get().extractPortPeriodTicks);
        long speedFactor = 1 + upgrades.getItem(0).getCount();
        long fold = speedFactor <= base ? 1 : (speedFactor + base - 1) / base; // 超出 1t 触底的速度折进批量（向上取整保单调）
        return Math.max(1, SdzjzConfig.get().extractPortBatch)
                * (1 + upgrades.getItem(1).getCount())
                * (1 + upgrades.getItem(2).getCount()) * fold;
    }

    public boolean extractOn() { return port.extractOn(); }
    public void setExtractOn(boolean on) { port.setExtractOn(on); }
    /** m229 认领所有者：谁配置这个口（开界面/潜行开关），转化桌出售的 EMC 就记谁账上。 */
    public void claimOwner(net.minecraft.world.entity.player.Player p) {
        if (p != null && !p.getUUID().equals(owner)) { owner = p.getUUID(); setChanged(); }
    }
    public java.util.UUID owner() { return owner; }
    public boolean pullMode() { return port.pullMode(); }
    public void setPullMode(boolean pull) { port.setPullMode(pull); } // m231

    // ===== m233 按面断开 =====
    public boolean faceDisabled(Direction d) { return (offFaces & (1 << d.get3DDataValue())) != 0; }
    public void toggleFace(Direction d) {
        offFaces ^= (1 << d.get3DDataValue());
        port.invalidateCores(); // 拓扑变了，相连核心缓存立即作废（40t 缓存别撑到下一窗）
        setChanged();
    }

    /** m233 网络通行判定：cur→np 这条边是否被数据线按面断开（任一端是数据线且该面被禁=不通）。
     *  全部走线（BFS/贴邻）统一插闸用；**必须在 seen 标记之前调用**——先标 seen 再判断会把
     *  经其他路径可达的节点一并堵死。 */
    public static boolean linkBlocked(net.minecraft.world.level.BlockGetter world, BlockPos cur, Direction d, BlockPos np) {
        if (world.getBlockEntity(cur) instanceof DataCableBlockEntity c && c.faceDisabled(d)) return true;
        if (world.getBlockEntity(np) instanceof DataCableBlockEntity c2 && c2.faceDisabled(d.getOpposite())) return true;
        return false;
    }
    /** 过滤模板视图（m226 界面读写；写入方自行 markDirty）。 */
    public List<ItemStack> filterView() { return port.filterView(); }

    // ===== m226 抽取口配置屏开屏工厂（数据面板同款三方法）=====
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("sdzjz.extract_port.title");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv,
                                                         net.minecraft.world.entity.player.Player player) {
        return new com.sdzjz.screen.ExtractPortScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos menuData( // m532（F1b）：原 Fabric getScreenOpeningData，改实现加载器无关的 MenuData（Fabric 发数据由 FabricMenus 包一层）
            net.minecraft.server.level.ServerPlayer player) {
        return this.worldPosition;
    }

    /** m224 邻接可抽取存储探测（走 Fabric Transfer API 标准口不做逐模组集成）→ m228 升级「六面视图」：
     *  贴线面只是查询视角之一——侧向机器（AvaritiaNeo 中子态素压缩机等）往往只在顶面暴露输入槽、
     *  其余面全是输出槽，线插侧面时旧逻辑拿到的唯一视图 insert 恒 0（实机反馈"插头接上了却导不进"）。
     *  现按 贴线面→其余五面 逐视角收集：单实例注册的模组按身份去重只收一次；侧向包装每面一实例、
     *  各按其面规则放行/拒绝——语义=六面各贴一只漏斗，线插哪面都能喂到收料面。六面全无才试无侧注册。
     *  自家网络方块排除口径不变（存储核心=左手倒右手 m161c；结构核心 Inventory 会被 Fabric 兜底捞到）。
     *  blockCount=有可对接视图的邻块数（界面"旁接存储"口径）。 */
    public static com.sdzjz.storage.ExtractPort.Adjacency scanAdjacent(Level world, BlockPos pos) {
        // m502：扫描主体迁 ExtractPort（两代共用）；本处只剩「有缆芯用它的宿主口（m233 面禁生效），
        // 没缆芯退化为无面禁扫描」——原 self==null 分支逐位同义。ProjectEF 判定与面禁走 Views 钩子。
        com.sdzjz.storage.ExtractPort.Views v =
                (world != null && world.getBlockEntity(pos) instanceof DataCableBlockEntity c) ? c.portHost : LEGACY_VIEWS;
        return com.sdzjz.storage.ExtractPort.scanAdjacent(v, world, pos);
    }

    /** m502：无缆芯时的扫描面（原 self==null 分支）：无面禁 + ProjectEF 转化桌判定照旧。 */
    private static final com.sdzjz.storage.ExtractPort.Views LEGACY_VIEWS = new com.sdzjz.storage.ExtractPort.Views() {
        @Override public boolean isSellNeighbor(Level w, BlockPos np) {
            return com.sdzjz.compat.ProjectEFCompat.isTransmutationTable(w.getBlockState(np));
        }
    };

    /** m225 抽取口主拍：pos 哈希移相（m218c 口径，多口不挤同一全局 tick），每拍最多搬 extractPortBatch 件。 */
    public static void tick(Level world, BlockPos pos, BlockState state, DataCableBlockEntity be) {
        if (world.isClientSide || !be.port.extractOn()) return;
        int period = be.effPeriod(); // m230 升级生效
        if (Math.floorMod(world.getGameTime() + pos.hashCode(), period) != 0) return;
        com.sdzjz.storage.ExtractPort.Adjacency adj = scanAdjacent(world, pos);
        List<Object> targets = adj.targets();
        be.opSeller = null; // m229 本拍卖手：贴桌+已认领+所有者在线（离线提供者不可变，写=白写，不卖留货）
        if (!be.port.pullMode() && adj.sellTable() && be.owner != null && world.getServer() != null)
            be.opSeller = world.getServer().getPlayerList().getPlayer(be.owner);
        if (targets.isEmpty() && be.opSeller == null) return;
        java.util.List<com.sdzjz.machine.StorageLedgerProbe> cores = be.port.cores(world, pos);
        if (cores.isEmpty()) return;
        // m502：分派/抽取/回收（m225 逐模板+全部模式游标轮转+m231 回收拍）整段迁 ExtractPort.runPulse。
        be.port.runPulse(targets, cores, be.effBudget()); // m230 升级生效
    }

    /** m502 宿主口：核心网络三口（本世代类型）+ m229 卖桌两钩子 + m233 面禁。宿主专属状态
     *  （opSeller/offFaces/owner）留在 BE；抽取/回收/邻接视图/核心 40t 缓存业务全在共用件
     *  {@link com.sdzjz.storage.ExtractPort}——原 cores/extractSpec/extractAll/doPull/pullWants/insertInto
     *  六个私有方法整段退役（原文迁共用件，注释刀号随迁）。 */
    private final class PortHost implements com.sdzjz.storage.ExtractPort.Host {
        @Override public boolean faceDisabled(Direction d) { return DataCableBlockEntity.this.faceDisabled(d); } // m233
        @Override public boolean isSellNeighbor(Level w, BlockPos np) {
            return com.sdzjz.compat.ProjectEFCompat.isTransmutationTable(w.getBlockState(np)); // m229 转化桌
        }
        @Override public boolean sellerActive() { return opSeller != null; } // m229 在线卖手（有卖手时"满"只是箱子满）

        /** m229 余量卖给转化桌（原 insertInto 尾段原文）：FTA 目标优先=箱子先装，溢出才卖。 */
        @Override public long sellOverflow(ItemStack tpl, boolean exact, long remaining) {
            if (opSeller == null) return 0;
            long unit = com.sdzjz.compat.ProjectEFCompat.unitValue(
                    exact ? tpl : new ItemStack(tpl.getItem()));
            if (unit <= 0) return 0;
            long n = Math.min(remaining, Long.MAX_VALUE / 2 / unit); // 天价物×大批量防溢出
            if (n > 0 && com.sdzjz.compat.ProjectEFCompat.credit(opSeller, unit * n))
                return n; // 物品湮灭为 EMC（卖出=转化桌语义，非丢弃）
            return 0;
        }

        @Override public List<BlockPos> scanCores(Level world, BlockPos pos) { // m218b：40t 缓存在共用件，BFS 在此
            List<BlockPos> ps = new ArrayList<>();
            for (StorageCoreBlockEntity c : StorageCoreBlockEntity.connectedCores(world, pos)) ps.add(c.getBlockPos().immutable());
            return ps;
        }
        @Override public com.sdzjz.machine.StorageLedgerProbe coreAt(Level world, BlockPos p) {
            return StorageCoreBlockEntity.loadedCoreAt(world, p);
        }
        @Override public Object coreStorage(com.sdzjz.machine.StorageLedgerProbe core) {
            // m161c FTA 出口（回收拍目的地）。m533（F1c）：原 `core.fabricStorage()` 直取 FTA 视图=业务 BE 上的加载器符号；
            // 改走既有 Xfer.find（m434 口）按核心自己的坐标找——Fabric 侧走 ItemStorage.SIDED 提供侧（FabricEntry 注册的
            // FabricStorageAdapter.of，同一 BE 恒返同一实例，与原直取逐位同一个对象），NeoForge 侧（F1d）走能力查询；零新口。
            StorageCoreBlockEntity be = (StorageCoreBlockEntity) core;
            return com.sdzjz.storage.Xfer.find(be.getLevel(), be.getBlockPos(), null);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        nbt.putBoolean("extractOn", port.extractOn());
        if (owner != null) nbt.putUUID("owner", owner); // m229 所有者
        nbt.putBoolean("pullMode", port.pullMode()); // m231 方向
        if (offFaces != 0) nbt.putInt("offFaces", offFaces); // m233
        for (int i = 0; i < 3; i++) // m230 升级槽（定槽键，空不写）
            if (!upgrades.getItem(i).isEmpty()) nbt.put("up" + i, upgrades.getItem(i).save(lookup));
        ListTag fl = new ListTag(); // 过滤模板持久化（精确账本 m130 同款 encode）
        for (ItemStack f : port.filterView()) if (!f.isEmpty()) fl.add(f.save(lookup));
        nbt.put("filter", fl);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        port.loadState(nbt.getBoolean("extractOn"), nbt.getBoolean("pullMode")); // m231；读档不触发 setChanged（口径同旧）
        owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null; // m229
        offFaces = nbt.getInt("offFaces"); // m233
        for (int i = 0; i < 3; i++) { // m230
            upgrades.setItem(i, nbt.contains("up" + i)
                    ? ItemStack.parse(lookup, nbt.getCompound("up" + i)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
        }
        port.filterView().clear();
        ListTag fl = nbt.getList("filter", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            ItemStack t = ItemStack.parse(lookup, fl.getCompound(i)).orElse(ItemStack.EMPTY);
            if (!t.isEmpty()) port.filterView().add(t.copyWithCount(1)); // 解析失败/物品已卸载静默跳过，不炸档
        }
    }
}
