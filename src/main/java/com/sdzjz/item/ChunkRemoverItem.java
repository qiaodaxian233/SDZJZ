package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.node.NodeTags;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * m376 区块移除器——区块机器线第一台（作者拍板路线：扫描器/过滤器/虚空/复制器排后续）。
 * 玩法：手持本机对目标区块内任意方块右键=绑定该区块（LinkerItem 同款 useOnBlock 存 NBT 打法，
 * 可交互方块请潜行右键；重绑=清进度重扫）；放上画布后自顶向下逐层移除整区块，
 * 原本会掉落的产物（如同正确工具无附魔挖掘）进机器输出→出线/存储网络。
 * 边界三条：基岩类（硬度<0）永不动；带方块实体的方块默认跳过（chunkRemoverSkipBlockEntities，
 * 防误吞基地箱子/本模组机器）；只认与核心同维度的绑定。免费型（挖矿在原版也免费，成本=时间），
 * 速度/数量/并发升级照常放大每周期预算。
 */
public class ChunkRemoverItem extends MachineItem {

    public ChunkRemoverItem(Settings settings, MachineDef def) {
        super(settings, def);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos();
        ItemStack stack = ctx.getStack();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        NbtCompound n = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putInt("zx", cx);
        n.putInt("zz", cz);
        n.putString("zd", world.getRegistryKey().getValue().toString());
        n.putInt("zy", world.getTopY() - 1); // 游标自世界顶往下（getTopY 为排他上界）
        n.putInt("zi", 0);
        n.putInt("zc", 0); // m382 分块序号归零（区域挡位 zr 保留——范围是机器设定随机走）
        n.remove("zf"); // 重绑=清完成位+清累计（重新开扫）
        n.remove("zn");
        n.remove("zq"); // m390 湿账随新工程归零
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        if (ctx.getPlayer() != null)
            ctx.getPlayer().sendMessage(Text.literal("已绑定区块 (" + cx + ", " + cz + ")，放入同维度核心画布即开挖"), true);
        return ActionResult.SUCCESS;
    }

    /** m384 手上换挡：潜行右键【空处】循环区域挡（对方块潜行右键仍是绑定，useOnBlock 先响）——
     *  瑞剑体验闭环：手持预览框（m384 高亮器）随挡即时变大变小，先圈定再上画布。
     *  服务端权威（客户端只回 SUCCESS 摆手）；换挡=新工程同 #zr 口径（清游标回顶，zn 保留）。 */
    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand) {
        ItemStack s = player.getStackInHand(hand);
        if (!player.isSneaking()) return net.minecraft.util.TypedActionResult.pass(s);
        if (!world.isClient) { // m386 区域自由调后循环换挡退役（cap=64 时循环 65 挡=灾难 UX），潜行右键空处改=快切掉落模式（野外随手切"这块不要掉落快拆"）
            NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            int nm = n.getInt("zm") == 1 ? 0 : 1;
            n.putInt("zm", nm);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
            player.sendMessage(Text.literal(nm == 1 ? "模式：无掉落·极速（方块直接蒸发）" : "模式：有掉落·出货"), true);
        }
        return net.minecraft.util.TypedActionResult.success(s, world.isClient);
    }

    /** m382 区域挡名（1×1/3×3/…，菜单与副行同源）。 */
    public static String regionLabel(ItemStack s) {
        int w = 2 * Math.max(0, NodeTags.chunkRadius(s)) + 1;
        return w + "×" + w;
    }

    /** 画布节点副行文案（客户端徽章行唯一口径；showWhy 阻塞时让位 m178 原因行）。 */
    public static String canvasLine(ItemStack s) {
        if (!NodeTags.chunkBound(s)) return "未绑定：手持对目标区块内方块右键";
        String at = regionLabel(s) + "@(" + NodeTags.chunkX(s) + "," + NodeTags.chunkZ(s) + ")";
        if (NodeTags.chunkMode(s) == 1) at += "·无掉"; // m386 模式标
        if (!NodeTags.chunkSealOn(s)) at += "·不堵水"; // m394 封边挡水默认开，只标关掉的（原为标开的，人人都开=白噪音）
        if (NodeTags.chunkDone(s)) return at + " 已清空·共" + NodeTags.chunkRemoved(s);
        return at + " Y=" + NodeTags.chunkY(s) + " 已挖" + NodeTags.chunkRemoved(s);
    }

    /** tick 侧游标推进落盘（每 tick 一次，含空气快进段——游标动了就得存，别只在有产出时存）。
     *  m390 追加湿账两参：wetDelta=本拍"清过的流体+补过的封"数（zq 累加，残水复检环的判据）；
     *  wetReset=真则清 zq（复检环开新一遍 / 真完成收尾），reset 优先于 delta（旧遍的账不带进新遍）。 */
    public static void advance(ItemStack s, int y, int ord, int idx, long removedDelta, boolean done,
                               long wetDelta, boolean wetReset) {
        NbtCompound n = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        n.putInt("zy", y);
        n.putInt("zc", ord); // m382 层主序分块序号
        n.putInt("zi", idx);
        if (removedDelta > 0) n.putLong("zn", n.getLong("zn") + removedDelta);
        if (wetReset) n.remove("zq");
        else if (wetDelta > 0) n.putLong("zq", n.getLong("zq") + wetDelta); // m390
        if (done) n.putBoolean("zf", true);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("手持对目标区块内任意方块右键=绑定（箱子等可交互方块请潜行右键）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("放入画布后自顶向下移除整个区块，掉落物进出线/存储网络").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("每周期基础 " + Math.max(1, com.sdzjz.config.SdzjzConfig.get().chunkRemoverBlocksPerCycle)
                + " 块（config 可调），速度/数量/并发升级照常放大").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("手持按设置键（默认 R）开面板：区域自由调 " + regionLabel(stack) + " / 掉落模式 / 封边挡水").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal((NodeTags.chunkMode(stack) == 1 ? "当前：无掉落·极速蒸发（不产任何物品）" : "当前：有掉落·出货") + "；潜行右键空处快切").formatted(NodeTags.chunkMode(stack) == 1 ? Formatting.GOLD : Formatting.GREEN));
        tooltip.add(Text.literal("手持已绑定本机=世界内浮现紫色选区框（技能选中圈，chunkFxEnabled 可关）").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("基岩不动；箱子等带方块实体的默认跳过（config 可开）；仅同维度").formatted(Formatting.RED));
        if (NodeTags.chunkBound(stack))
            tooltip.add(Text.literal("当前绑定：" + canvasLine(stack) + "（重绑=重扫）").formatted(Formatting.GREEN));
    }
}
