package com.sdzjz.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * m493（真移植·画布视觉第六件·A 线收尾）：**节点悬停详情两代共用一份**——主线
 * {@code StructureCoreScreen} 的 m85 悬停详情段整段搬（状态/周期/基础产量/产出表/消耗提示）。
 *
 * <p>照 `docs/作业表.md` 的搬运流程办：先查 `世代API对照表`，命中两处 1.21 专属——
 * `instanceof MachineItem` → {@code NodeTags.defOf}（m472 口）、`ResourceLocation.parse` →
 * {@link SciSkin.Gfx#id}（m484 口）。其余逐句原文，含格式串与「消耗输入（对齐原版）」那行。
 *
 * <p>状态文案与 {@link NodeCardRenderer.Host} 同源（核心停机优先于分支状态），
 * 两代同一份，不会出现"卡面说运行中、浮层说待机"的错位。
 */
public final class NodeTooltip {

    private NodeTooltip() { }

    /** 组装某个节点的悬停详情行（调用方拿去 renderComponentTooltip）。 */
    public static List<Component> lines(ItemStack st, int status, boolean running) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(st.getHoverName().getString() + " ×" + st.getCount()));
        tip.add(Component.literal("状态: " + (!running ? "核心停机"
                : switch (status) {
                    case 1 -> "运行中";
                    case 2 -> "阻塞/关闸";
                    case 3 -> "缺料";
                    default -> "待机";
                })));
        com.sdzjz.machine.MachineDef def = com.sdzjz.node.NodeTags.defOf(st); // m472 世代口（原文是 instanceof MachineItem）
        if (def != null) {
            double avg = 0;
            for (var d : def.outputs()) avg += d.chance() * (d.min() + d.max()) / 2.0;
            double perMin = avg * (1200.0 / Math.max(1, def.baseIntervalTicks())) * st.getCount();
            tip.add(Component.literal(String.format("周期 %.1f 秒 · 基础产出 ~%.0f/分",
                    def.baseIntervalTicks() / 20.0, perMin)));
            StringBuilder sb = new StringBuilder("产出: ");
            for (int k = 0; k < def.outputs().size() && k < 3; k++) {
                if (k > 0) sb.append("、");
                sb.append(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(SciSkin.gfxItem(def.outputs().get(k).item()))).getHoverName().getString()); // m484 世代口
            }
            if (def.outputs().size() > 3) sb.append("…");
            tip.add(Component.literal(sb.toString()));
            if (def.consumesInputs()) tip.add(Component.literal("消耗输入（对齐原版）"));
        }
        return tip;
    }
}
