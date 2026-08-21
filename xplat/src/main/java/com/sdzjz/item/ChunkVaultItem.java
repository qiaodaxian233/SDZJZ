package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.node.NodeTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * m381 区块储存器——模板端（三件套 m379 第二刀，第 100 台机器）：绑定区块→只读全量扫描→
 * 模板入库（ChunkTemplateStore 服务端权威）→产出"区块数据核心"×1（只揣 UUID+摘要）。
 * 模板不变量（m379 决策收口）：入模板的位=可付可建——空气/基岩类硬度<0/带方块实体（决策2：
 * 空箱防复制基地、猪笼防凭空造不可获取物）/无物品形态（火/传送门/**纯液体**——水岩浆 asItem
 * 即 AIR，一条规则连液体一起出局）一律不入。数据核心是组件物品：**永走真栈通道**
 * （deposit 精确账本/addOutput 保组件），出线 id 计数派发对本机不生效。
 * 暂存器 Acc 刻意 transient 不落盘：半成品模板几百 KB 逐拍写节点 NBT 会把画布快照打爆——
 * 半途重启=回顶重扫（游标在半路而暂存器空即自愈）。重绑=新扫新模板新 UUID（旧模板=玩家资产留库）。
 */
public class ChunkVaultItem extends MachineItem {

    /** 调色板封顶（防病态数据包方块态爆炸；实际区块 <500 态，超限位跳过不入模板）。 */
    public static final int PALETTE_CAP = 4096;

    /** tick 侧暂存器（SCBE transient 持有，键=节点下标；绑定坐标随存防节点下标复用串账）。 */
    public static final class Acc {
        public final int cx, cz;
        public final ArrayList<BlockState> pal = new ArrayList<>();
        public final HashMap<BlockState, Integer> palIdx = new HashMap<>();
        public final HashMap<Integer, int[]> secs = new HashMap<>(); // sectionY -> int[4096]（值=调色板下标+1，0=不建）
        public final LinkedHashMap<String, Long> bom = new LinkedHashMap<>();
        public long total;
        public boolean scanComplete; // 扫完但可能还在等模板库腾位（库满重试不重扫）

        public Acc(int cx, int cz) { this.cx = cx; this.cz = cz; }

        /** 登记方块态入调色板；封顶返 -1（该位跳过）。 */
        public int record(BlockState bs) {
            Integer at = palIdx.get(bs);
            if (at != null) return at;
            if (pal.size() >= PALETTE_CAP) return -1;
            pal.add(bs);
            palIdx.put(bs, pal.size() - 1);
            return pal.size() - 1;
        }
    }

    public ChunkVaultItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        if (world.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        CompoundTag n = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        n.putInt("zx", cx);
        n.putInt("zz", cz);
        n.putString("zd", world.dimension().location().toString());
        n.putInt("zy", world.getMaxBuildHeight() - 1);
        n.putInt("zi", 0);
        n.remove("tf"); n.remove("tu"); n.remove("tt"); // 重绑=新扫新模板（旧模板留库不动）
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
        if (ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.literal("已绑定区块 (" + cx + ", " + cz + ")，放入同维度核心画布即开始存档"), true);
        return InteractionResult.SUCCESS;
    }

    /** 扫描中游标落盘（zy/zi 复用移除器 z 族）。 */
    public static void cursor(ItemStack s, int y, int idx) {
        CompoundTag n = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        n.putInt("zy", y);
        n.putInt("zi", idx);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
    }

    /** 存档收官：就绪位+模板 UUID+可重建总数落节点（卡面读数）。 */
    public static void finish(ItemStack s, String uuid, long total) {
        CompoundTag n = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        n.putBoolean("tf", true);
        n.putString("tu", uuid);
        n.putLong("tt", total);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("区块存档：手持对目标区块内方块右键绑定（可交互方块请潜行右键）").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("放入画布后只读扫描存成模板，产出\"区块数据核心\"×1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("空气/基岩/箱子等方块实体/水岩浆火传送门 不入模板").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("模板存服务端库（config 封顶），核心只揣引用——丢核心≠丢模板").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("重绑=新扫新模板；配区块复制器重建（收料照模板 BOM）").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
