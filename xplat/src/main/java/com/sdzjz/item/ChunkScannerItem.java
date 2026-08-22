package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.node.NodeTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * m380 区块扫描器——区块机器线读取端（三件套方案 m379 第一刀，零经济争议先行）。
 * 玩法：手持对目标区块内方块右键=绑定（移除器 m376 同款 useOnBlock，绑定/游标复用 z 族键
 * zx/zz/zd/zy/zi——NodeTags 读器全套白捡）→放同维度核心画布→自顶向下只读扫描→出统计报告：
 * 方块总数/类型数/矿物合计（c:ores 标签 ∪ _ore 后缀 ∪ 远古残骸，标签缺席也不瞎）/容器数
 * （方块实体 instanceof Inventory）/生物数（完成拍一次性 getEntitiesByClass 清点）+ 方块 Top 榜。
 * 报告面：卡面两行摘要 + 节点菜单 Top8 明细行；"重新扫描"走 #zs 哨兵（#cr/#xr 同款工艺）。
 * 只读机器：不动世界不收不产（accepts/chainWants 恒假）。类型榜封顶 64 种，溢出归"#其他"桶。
 */
public class ChunkScannerItem extends MachineItem {

    /** 矿物判定标签（fabric 惯例标签，模组矿自动入榜；vanilla 兜底走 _ore 后缀）。 */
    public static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    /** 类型榜封顶（防节点 NBT/同步包膨胀，m291 有界精神）。 */
    public static final int TYPE_CAP = 64;

    public ChunkScannerItem(Properties settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level world = ctx.getLevel();
        if (world.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(stack);
        n.putInt("zx", cx);
        n.putInt("zz", cz);
        n.putString("zd", world.dimension().location().toString());
        n.putInt("zy", world.getMaxBuildHeight() - 1);
        n.putInt("zi", 0);
        clearReport(n); // 重绑=清报告重扫
        com.sdzjz.item.ItemData.write(stack, n);
        if (ctx.getPlayer() != null)
            ctx.getPlayer().displayClientMessage(Component.literal("已绑定区块 (" + cx + ", " + cz + ")，放入同维度核心画布即开扫"), true);
        return InteractionResult.SUCCESS;
    }

    private static void clearReport(CompoundTag n) {
        n.remove("sa"); n.remove("so"); n.remove("sc"); n.remove("se"); n.remove("sf"); n.remove("sm");
    }

    /** #zs 重新扫描（服务端收包口调用）：报告清空、游标回顶；未绑定=无事发生。 */
    public static void resetScan(ItemStack s, int topY) {
        if (!NodeTags.chunkBound(s)) return;
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putInt("zy", topY);
        n.putInt("zi", 0);
        clearReport(n);
        com.sdzjz.item.ItemData.write(s, n);
    }

    /** tick 侧每拍累计落盘（游标+四计数+类型榜合并，榜满归"#其他"桶）。 */
    public static void accumulate(ItemStack s, int y, int idx, long total, long ore, long containers,
                                  Map<String, Long> typeDelta) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putInt("zy", y);
        n.putInt("zi", idx);
        if (total > 0) n.putLong("sa", n.getLong("sa") + total);
        if (ore > 0) n.putLong("so", n.getLong("so") + ore);
        if (containers > 0) n.putLong("sc", n.getLong("sc") + containers);
        if (!typeDelta.isEmpty()) {
            CompoundTag m = n.getCompound("sm");
            for (Map.Entry<String, Long> e : typeDelta.entrySet()) {
                String k = (m.contains(e.getKey()) || m.getAllKeys().size() < TYPE_CAP) ? e.getKey() : "#其他";
                m.putLong(k, m.getLong(k) + e.getValue());
            }
            n.put("sm", m);
        }
        com.sdzjz.item.ItemData.write(s, n);
    }

    /** 扫描收官：生物数一次性入账+就绪位。 */
    public static void finish(ItemStack s, long entities) {
        CompoundTag n = com.sdzjz.item.ItemData.copyOf(s);
        n.putLong("se", entities);
        n.putBoolean("sf", true);
        com.sdzjz.item.ItemData.write(s, n);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("区块侦察：手持对目标区块内方块右键绑定（可交互方块请潜行右键）").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("放入画布后只读扫描，出统计报告：方块/类型/矿物/容器/生物 + Top榜").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("卡面看摘要，右键节点菜单看 Top8 明细与重新扫描").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("只读不动世界；挖之前先侦察，配移除器/过滤器食用").withStyle(ChatFormatting.DARK_GRAY));
        if (NodeTags.chunkBound(stack))
            tooltip.add(Component.literal("当前绑定：区块(" + NodeTags.chunkX(stack) + "," + NodeTags.chunkZ(stack)
                    + (NodeTags.scanDone(stack) ? ")·报告就绪" : ")·待扫/扫描中")).withStyle(ChatFormatting.GREEN));
    }
}
