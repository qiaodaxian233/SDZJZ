package com.sdzjz.item;

import com.sdzjz.machine.MachineDef;
import com.sdzjz.node.NodeTags;
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
    public InteractionResult useOnBlock(UseOnContext ctx) {
        Level world = ctx.getWorld();
        if (world.isClient) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos();
        ItemStack stack = ctx.getStack();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        CompoundTag n = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.DEFAULT).copyNbt();
        n.putInt("zx", cx);
        n.putInt("zz", cz);
        n.putString("zd", world.getRegistryKey().getValue().toString());
        n.putInt("zy", world.getTopY() - 1); // 游标自世界顶往下（getTopY 为排他上界）
        n.putInt("zi", 0);
        n.putInt("zc", 0); // m382 分块序号归零（区域挡位 zr 保留——范围是机器设定随机走）
        n.remove("zf"); // 重绑=清完成位+清累计（重新开扫）
        n.remove("zn");
        n.remove("zq"); // m390 湿账随新工程归零
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
        if (ctx.getPlayer() != null)
            ctx.getPlayer().sendMessage(Component.literal("已绑定区块 (" + cx + ", " + cz + ")，放入同维度核心画布即开挖"), true);
        return InteractionResult.SUCCESS;
    }

    /** m384 手上换挡：潜行右键【空处】循环区域挡（对方块潜行右键仍是绑定，useOnBlock 先响）——
     *  瑞剑体验闭环：手持预览框（m384 高亮器）随挡即时变大变小，先圈定再上画布。
     *  服务端权威（客户端只回 SUCCESS 摆手）；换挡=新工程同 #zr 口径（清游标回顶，zn 保留）。 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level world, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack s = player.getStackInHand(hand);
        if (!player.isSneaking()) return net.minecraft.world.InteractionResultHolder.pass(s);
        if (!world.isClient) { // m386 区域自由调后循环换挡退役（cap=64 时循环 65 挡=灾难 UX），潜行右键空处改=快切掉落模式（野外随手切"这块不要掉落快拆"）
            CompoundTag n = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.DEFAULT).copyNbt();
            int nm = nextMode(n.getInt("zm"), com.sdzjz.config.SdzjzConfig.get().chunkRemoverVoidMode); // m397 三挡循环
            n.putInt("zm", nm);
            s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
            player.sendMessage(Component.literal("模式：" + modeLabel(nm) + (nm == 2 ? "（基岩也拆，坑底会通虚空）" : "")), true);
        }
        return net.minecraft.world.InteractionResultHolder.success(s, world.isClient);
    }

    /** m382 区域挡名（1×1/3×3/…，菜单与副行同源）。 */
    public static String regionLabel(ItemStack s) {
        int w = 2 * Math.max(0, NodeTags.chunkRadius(s)) + 1;
        return w + "×" + w;
    }

    /** m397 模式表（三挡，双端同源唯一权威；序号存节点 NBT 键 zm）：
     *  0=有掉落·出货 / 1=无掉落·极速蒸发 / **2=空置域·破基岩**（作者点名新增：连基岩这类
     *  硬度<0 的方块一起拆，把整片区域清成真空；无掉落，走极速快车道）。 */
    public static final String[] MODE_NAME = {"有掉落 · 出货", "无掉落 · 极速蒸发", "空置域 · 破基岩"};

    /** 越界读收 0（旧档/伪造包脏值）；config 关掉空置域时 2 按 1 用。 */
    public static int mode(ItemStack s, boolean voidAllowed) {
        int m = NodeTags.chunkMode(s);
        if (m < 0 || m > 2) return 0;
        return (m == 2 && !voidAllowed) ? 1 : m;
    }

    /** 循环换挡：0→1→2→0；config 关空置域时 0→1→0（不给玩家选一个不生效的挡）。 */
    public static int nextMode(int cur, boolean voidAllowed) {
        int max = voidAllowed ? 2 : 1;
        int n = cur + 1;
        return (n > max || n < 0) ? 0 : n;
    }

    public static String modeLabel(int m) { return MODE_NAME[(m < 0 || m > 2) ? 0 : m]; }

    /** m396 封边材料解析（作者点名"默认铺可以改方块、可以自定义"）：空串/非法/无方块形态=null=按默认
     *  **免费石头**（m389 口径：置石不扣料，防顶层没圆石封不上=水照灌的哑死）。自定义料则**从存储扣**，
     *  扣不到=黄灯提醒并当拍回落免费石墙——绝不静默停封让水灌进来（m99 精神）。 */
    public static net.minecraft.world.level.block.Block sealBlockOf(String id) {
        if (id == null || id.isEmpty()) return null;
        net.minecraft.resources.ResourceLocation ident = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (ident == null) return null;
        net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ident);
        if (!(it instanceof net.minecraft.world.item.BlockItem bi)) return null; // 没有方块形态的物品当不了墙
        net.minecraft.world.level.block.Block b = bi.getBlock();
        return b == net.minecraft.world.level.block.Blocks.AIR ? null : b;
    }

    public static boolean validSealBlock(String id) { return sealBlockOf(id) != null; }

    /** 材料显示名（菜单/面板/副行唯一口径）。 */
    public static String sealLabel(ItemStack s) {
        net.minecraft.world.level.block.Block b = sealBlockOf(NodeTags.chunkSealBlock(s));
        return b == null ? "石头（免费）" : new ItemStack(b.asItem()).getName().getString();
    }

    /** 画布节点副行文案（客户端徽章行唯一口径；showWhy 阻塞时让位 m178 原因行）。 */
    public static String canvasLine(ItemStack s) {
        if (!NodeTags.chunkBound(s)) return "未绑定：手持对目标区块内方块右键";
        String at = regionLabel(s) + "@(" + NodeTags.chunkX(s) + "," + NodeTags.chunkZ(s) + ")";
        int mL = NodeTags.chunkMode(s); // m397 模式标（三挡）
        if (mL == 2) at += "·空置域";
        else if (mL == 1) at += "·无掉";
        if (!NodeTags.chunkSealOn(s)) at += "·不堵水"; // m394 封边挡水默认开，只标关掉的（原为标开的，人人都开=白噪音）
        else if (!NodeTags.chunkSealBlock(s).isEmpty()) at += "·砌" + sealLabel(s); // m396 自定义封边材料
        if (NodeTags.chunkDone(s)) return at + " 已清空·共" + NodeTags.chunkRemoved(s);
        int lim = NodeTags.chunkLimited(s); // m398 满载标（作者报"升级拉满还是慢"=把天花板摆到脸上，别让人以为升级还有用）
        if (lim == 1) at += "·满载(每拍方块硬顶)";
        else if (lim == 2) at += "·满载(时间片)";
        return at + " Y=" + NodeTags.chunkY(s) + " 已挖" + NodeTags.chunkRemoved(s);
    }

    /** tick 侧游标推进落盘（每 tick 一次，含空气快进段——游标动了就得存，别只在有产出时存）。
     *  m390 追加湿账两参：wetDelta=本拍"清过的流体+补过的封"数（zq 累加，残水复检环的判据）；
     *  wetReset=真则清 zq（复检环开新一遍 / 真完成收尾），reset 优先于 delta（旧遍的账不带进新遍）。 */
    public static void advance(ItemStack s, int y, int ord, int idx, long removedDelta, boolean done,
                               long wetDelta, boolean wetReset, int limited) {
        CompoundTag n = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.DEFAULT).copyNbt();
        n.putInt("zy", y);
        n.putInt("zc", ord); // m382 层主序分块序号
        n.putInt("zi", idx);
        if (removedDelta > 0) n.putLong("zn", n.getLong("zn") + removedDelta);
        if (wetReset) n.remove("zq");
        else if (wetDelta > 0) n.putLong("zq", n.getLong("zq") + wetDelta); // m390
        if (done) n.putBoolean("zf", true);
        if (limited > 0) n.putInt("zl", limited); else n.remove("zl"); // m398 上限位（0=不写键，老档缺键=没撞）
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(n));
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.literal("手持对目标区块内任意方块右键=绑定（箱子等可交互方块请潜行右键）").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("放入画布后自顶向下移除整个区块，掉落物进出线/存储网络").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("每周期基础 " + Math.max(1, com.sdzjz.config.SdzjzConfig.get().chunkRemoverBlocksPerCycle)
                + " 块（config 可调），速度/数量/并发升级照常放大").formatted(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("到顶提醒：每拍最多 " + Math.max(256, com.sdzjz.config.SdzjzConfig.get().chunkRemoverMaxBlocksPerTick)
                + " 块且每台每拍限 " + Math.max(0, com.sdzjz.config.SdzjzConfig.get().chunkRemoverTimeSliceMs)
                + "ms——副行显示\"满载\"时再加升级没用，改 config 三键或多放几台各绑一片").formatted(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("手持按设置键（默认 R）开面板：区域自由调 " + regionLabel(stack) + " / 掉落模式 / 封边挡水").formatted(ChatFormatting.LIGHT_PURPLE));
        int mT = NodeTags.chunkMode(stack);
        tooltip.add(Component.literal("当前：" + modeLabel(mT) + (mT == 2 ? "（连基岩一起拆，坑底通虚空）" : mT == 1 ? "（不产任何物品）" : "（进出线/存储）") + "；潜行右键空处快切")
                .formatted(mT == 2 ? ChatFormatting.RED : mT == 1 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        tooltip.add(Component.literal("手持已绑定本机=世界内浮现紫色选区框（技能选中圈，chunkFxEnabled 可关）").formatted(ChatFormatting.AQUA));
        tooltip.add(Component.literal("基岩不动；箱子等带方块实体的默认跳过（config 可开）；仅同维度").formatted(ChatFormatting.RED));
        if (NodeTags.chunkBound(stack))
            tooltip.add(Component.literal("当前绑定：" + canvasLine(stack) + "（重绑=重扫）").formatted(ChatFormatting.GREEN));
    }
}
