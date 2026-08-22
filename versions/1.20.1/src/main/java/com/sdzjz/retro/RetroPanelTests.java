package com.sdzjz.retro;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * m447 刀②行为判官：数据面板服务端半——快照聚合/过滤/排序/开窗（纯函数直判）、取物进背包与
 * 背包满余量回账（makeMockPlayer 免真客户端，mojmap 1.20.1 无参版）、quickMove 背包入仓
 * （双端红线的服务端支路）。payload 编解码红线已由 RetroNetTests 罩（同 Net120 两锚点口）。
 */
public final class RetroPanelTests implements FabricGameTest {

    private static StorageCore120 core(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.STORAGE_CORE.defaultBlockState());
        if (ctx.getBlockEntity(rel) instanceof StorageCore120 c) return c;
        ctx.fail("存储核心方块实体未生成");
        return null;
    }

    private static ItemStack tagged(int k, int count) {
        ItemStack st = new ItemStack(Items.COBBLESTONE, count);
        st.getOrCreateTag().putInt("k", k);
        return st;
    }

    /** 快照：普通+精确合流、id 串过滤大小写不敏、账面数降序、滚动行服务端钳位。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void panel_snapshot_filters_sorts_and_windows(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.STONE, 5));
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        c.deposit(new ItemStack(Items.DIRT, 9));
        c.deposit(tagged(1, 3)); // 精确件同 id 也该被 "cobble" 命中
        var all = DataPanel120.snapshot(List.of(c), "", 0);
        ctx.assertTrue(all.rows().size() == 4, "空查询应见 4 条，实得 " + all.rows().size());
        ctx.assertTrue(all.rows().get(0).n() == 64 && !all.rows().get(0).exact(),
                "首条应为账面最大 64 的裸圆石，实得 n=" + all.rows().get(0).n());
        var hit = DataPanel120.snapshot(List.of(c), "COBBLE", 0); // 大小写不敏
        ctx.assertTrue(hit.rows().size() == 2, "cobble 应命中裸+精确两条，实得 " + hit.rows().size());
        var clamped = DataPanel120.snapshot(List.of(c), "", 9999); // 越界滚动
        ctx.assertTrue(clamped.scrollRow() == 0, "滚动行应被服务端钳回 0，实得 " + clamped.scrollRow());
        ctx.succeed();
    }

    /** 取物：普通按 id、精确按模板各进背包，账本对应扣减；申报天量被钳位。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void panel_take_moves_into_player_inventory(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 100));
        c.deposit(tagged(2, 10));
        Player p = ctx.makeMockPlayer(); // 1.20.1 无参版（1.21 起才带 GameType 参）
        int got = DataPanel120.serverTake(p, List.of(c), false, "minecraft:cobblestone", ItemStack.EMPTY, 64);
        ctx.assertTrue(got == 64, "普通取 64 应到手 64，实得 " + got);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 36, "账本应余 36，实得 " + c.count("minecraft:cobblestone"));
        int gotExact = DataPanel120.serverTake(p, List.of(c), true, "", tagged(2, 1), 999999); // 天量申报
        ctx.assertTrue(gotExact == 10, "精确件应整取 10（申报被钳仍取到实存量），实得 " + gotExact);
        ctx.assertTrue(c.exactTemplates().isEmpty(), "精确条目应提净");
        int tagCount = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty() && st.hasTag() && st.getTag().getInt("k") == 2) tagCount += st.getCount();
        }
        ctx.assertTrue(tagCount == 10, "背包里 tag 件应=10（不变裸），实得 " + tagCount);
        ctx.succeed();
    }

    /** 背包满：余量回账本绝不落地（先塞满 36 格再取）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void panel_take_returns_overflow_when_inventory_full(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        c.deposit(new ItemStack(Items.COBBLESTONE, 64));
        Player p = ctx.makeMockPlayer();
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64)); // 主背包+快捷栏全满
        int got = DataPanel120.serverTake(p, List.of(c), false, "minecraft:cobblestone", ItemStack.EMPTY, 64);
        ctx.assertTrue(got == 0, "背包全满应到手 0，实得 " + got);
        ctx.assertTrue(c.count("minecraft:cobblestone") == 64, "余量应全数回账=64，实得 " + c.count("minecraft:cobblestone"));
        ctx.succeed();
    }

    /** quickMove：shift 点背包槽整栈入仓（tag 件进精确账本），槽清空返 EMPTY 终止续移。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void panel_quickmove_deposits_into_network(GameTestHelper ctx) {
        StorageCore120 c = core(ctx);
        BlockPos panelRel = new BlockPos(1, 1, 0);
        ctx.setBlock(panelRel, RetroBlocks.DATA_PANEL.defaultBlockState());
        Player p = ctx.makeMockPlayer();
        p.getInventory().setItem(9, tagged(3, 8)); // 背包第一格（菜单槽 0=inv 下标 9）
        var menu = new DataPanel120.PanelMenu120(1, p.getInventory(), ctx.absolutePos(panelRel));
        ItemStack ret = menu.quickMove(p, 0);
        ctx.assertTrue(ret.isEmpty(), "quickMove 应返 EMPTY 终止续移");
        ctx.assertTrue(p.getInventory().getItem(9).isEmpty(), "槽应清空");
        ctx.assertTrue(c.exactTemplates().size() == 1 && c.exactCount(0) == 8,
                "tag 件应入精确账本 1 条 ×8，实得 " + c.exactTemplates().size() + " 条 ×" + c.exactCount(0));
        ctx.succeed();
    }
}
