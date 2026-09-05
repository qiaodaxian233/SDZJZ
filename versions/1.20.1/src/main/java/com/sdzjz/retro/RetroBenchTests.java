package com.sdzjz.retro;

import com.sdzjz.machine.SuperBenchRecipes;
import com.sdzjz.screen.SuperBenchScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * m524（SB3）S 线判官：超大工作台在 1.20.1 的注册三件（方块/BE/MenuType，id 与主线同名同源）+
 * **菜单类型安装口装了没**（m523 教训②：带默认值的口默认是静默的——{@code installType} 漏调只在玩家开屏那一刻 {@code reqType()} 抛，
 * 判官用两参构造把这一刻提前到测试里）。
 * m526（SB5）S 线收官五判官：①配方表多重集全表唯一 + 蓝图与 BOM 逐位一致（主线离线校验在本世代照跑）
 * ②配方表 id 在 1.20.1 注册表可解析——**已知缺口白名单双向对表**（多出新缺口红、缺口补上白名单没滚也红）
 * ③端到端：按蓝图铺料→结果槽出机器→左键取走（PICKUP 走 doClick→tryRemove→onTake，服务端权威扣料）→网格全空/结果槽空/手上是机器
 * ④点浏览器填料 `clickMenuButton(idx)`：背包→网格按蓝图铺、结果出；空宿主两钮回 true 且一件不少（m523 明说不吞件）；换台回收；越界 id 回 false；全程件数守恒
 * ⑤quickMove：网格→背包、背包→网格、结果槽 shift 无动作（"结果槽用鼠标取"）。
 */
public final class RetroBenchTests implements FabricGameTest {

    private static ResourceLocation id(String p) { return new ResourceLocation("sdzjz", p); }

    /** 放下方块→BE 是本世代壳；三注册表 id 各指回 RetroBlocks 那份；handler 能建（安装口已装）且槽位数=144 网格+1 结果+36 背包。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_block_be_and_menu_installed(GameTestHelper ctx) {
        BlockPos rel = new BlockPos(0, 1, 0);
        ctx.setBlock(rel, RetroBlocks.SUPER_BENCH.defaultBlockState());
        ctx.assertTrue(ctx.getBlockEntity(rel) instanceof SuperBench120, "超大工作台方块实体未生成（newBlockEntity/BE 类型没接）");
        ctx.assertTrue(BuiltInRegistries.BLOCK.get(id("super_bench")) == RetroBlocks.SUPER_BENCH, "方块 id sdzjz:super_bench 该指回 RetroBlocks.SUPER_BENCH");
        ctx.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id("super_bench")) == RetroBlocks.SUPER_BENCH_BE, "BE 类型 id 该指回 RetroBlocks.SUPER_BENCH_BE");
        ctx.assertTrue(BuiltInRegistries.MENU.get(id("super_bench")) == RetroBlocks.SUPER_BENCH_MENU, "菜单 id 该指回 RetroBlocks.SUPER_BENCH_MENU");
        var p = ctx.makeMockPlayer(); // 1.20.1 无参版（1.21 起才带 GameType 参，RetroPanelTests 同注）
        var h = new SuperBenchScreenHandler(1, p.getInventory()); // 两参构造=ContainerLevelAccess.NULL；reqType() 未装会在此抛
        ctx.assertTrue(h.getType() == RetroBlocks.SUPER_BENCH_MENU, "handler 菜单类型该是本世代 installType 装进去的那份");
        ctx.assertTrue(h.slots.size() == SuperBenchScreenHandler.GRID_SLOTS + 1 + 36,
                "槽位数该=144 网格+1 结果+36 背包，实得 " + h.slots.size());
        ctx.succeed();
    }

    // ===== m526（SB5）=====

    /** 1.20.1 注册表里能不能解析出非空气物品（world 层查表，配方表两代同一份 xplat）。 */
    private static boolean resolvable(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        return BuiltInRegistries.ITEM.containsKey(rl) && BuiltInRegistries.ITEM.get(rl) != Items.AIR;
    }

    /** 已知缺口·结果件：m521 缺口表「非机器物品 21」里在配方表出现的 14 件（本世代未注册）。补上一件就该从这儿删一行（判官双向对表会红提醒）。 */
    private static final Set<String> KNOWN_GAP_RESULTS = Set.of(
            "sdzjz:auto_feeder", "sdzjz:capture_cage", "sdzjz:count_upgrade", "sdzjz:linker", // m527：core_module 已注册（RetroItems），滚出
            "sdzjz:parallel_upgrade", "sdzjz:portable_vault", "sdzjz:satellite_node", "sdzjz:speed_upgrade",
            "sdzjz:storage_upgrade", "sdzjz:terminal", "sdzjz:trade_center", "sdzjz:villager_contract", "sdzjz:wireless_node");

    /** 已知缺口·材料（1.20.1 配方可达账：m526b 0/122 → m527 注册 core_module 后账面 56/122，CI 判官②实机对表为准）：
     *  ①~~`sdzjz:core_module`~~ **m527 已注册**（RetroItems 第一件，作者拍板「按 1.21.1 的来，做成一样」）——此前 bom/bomPacked/addSmall 三建表口
     *    都自动并入它、addSmall9 也用，一件卡死全部 122 条（m526b 实机对表得 0，离线正则漏了字母常量 MM）；
     *    它自己的配方铜/红石/石英全原版，注册后立刻可达；
     *  ②m526 新发现——**10 种 1.20.3+/1.21 才有的原版物品**被 12 条 BOM 引用（crafter×9 条、微风/试炼农场专料、heavy_core），
     *    1.20.1 注册表查不到，浏览器 BOM 里显示为空气（替料/隐藏/照旧待拍板）；
     *  ③抓物笼（44 条刷怪类自动带）+ trade_center/villager_contract/wireless_node 三件非机器 sdzjz 料（m521 缺口表已列）。 */
    private static final Set<String> KNOWN_GAP_INGREDIENTS = Set.of(
            "minecraft:breeze_rod", "minecraft:copper_bulb", "minecraft:copper_grate", "minecraft:crafter", "minecraft:heavy_core",
            "minecraft:ominous_bottle", "minecraft:trial_key", "minecraft:trial_spawner", "minecraft:tuff_bricks", "minecraft:vault",
            "sdzjz:capture_cage", "sdzjz:wireless_node"); // m526b 滚掉 trade_center/villager_contract（只是结果件）；m527 滚掉 core_module（已注册）

    /** 本世代可达配方数账面值（判官②等值断言，多了少了都红）。m526b=0（core_module 未注册一件卡死全部）→ **m527=56**（注册 core_module 后：
     *  42 bom + 3 bomPacked + 4 addSmall9 + 7 addSmall；不可达 66 = 44 刷怪类要抓物笼 + 12 条含 1.21 原版新料 + 结果件为未注册非机器件的小配方；
     *  别名解析版离线脚本口径，以 CI 判官②实机对表为准）。本世代每注册一件非机器物品 / 主线每改配方表，此值同刀改。 */
    private static final int EXPECTED_REACHABLE = 56;

    /** 本世代可达（结果件+全部材料都能解析、无生物、有蓝图）的配方里 BOM 总件数最少的那条——端到端/填料两判官用，确定性选取。 */
    private static SuperBenchRecipes.Recipe smallestReachable() {
        SuperBenchRecipes.Recipe best = null; int bestN = Integer.MAX_VALUE;
        for (SuperBenchRecipes.Recipe r : SuperBenchRecipes.ALL) {
            if (r.layout() == null || !r.mobs().isEmpty() || !resolvable(r.result())) continue;
            boolean ok = true; int n = 0;
            for (Map.Entry<String, Integer> e : r.ingredients().entrySet()) { if (!resolvable(e.getKey())) { ok = false; break; } n += e.getValue(); }
            if (ok && n < bestN) { best = r; bestN = n; }
        }
        return best;
    }

    /** 某物品 id 在 网格+结果槽+玩家背包 里的总件数（守恒断言用）。 */
    private static int countEverywhere(SuperBenchScreenHandler h, String id) {
        int n = 0;
        for (int i = 0; i < h.slots.size(); i++) {
            ItemStack s = h.slots.get(i).getItem();
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) n += s.getCount();
        }
        return n;
    }

    private static int gridCount(SuperBenchScreenHandler h) {
        int n = 0;
        for (int i = 0; i < SuperBenchScreenHandler.GRID_SLOTS; i++) n += h.getSlot(i).getItem().getCount();
        return n;
    }

    /** ①多重集全表唯一（`match` 是精确 equals 查表，撞了就是先到先得的静默错配）+ 蓝图 144 格且逐格多重集==BOM + 总件数≤144。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_recipe_table_unique_and_layout_consistent(GameTestHelper ctx) {
        List<SuperBenchRecipes.Recipe> all = SuperBenchRecipes.ALL;
        ctx.assertTrue(all.size() > 50, "配方表该有几十条，实得 " + all.size());
        for (int i = 0; i < all.size(); i++) {
            SuperBenchRecipes.Recipe a = all.get(i);
            for (int j = i + 1; j < all.size(); j++)
                ctx.assertTrue(!a.ingredients().equals(all.get(j).ingredients()),
                        "多重集撞表：" + a.result() + " 与 " + all.get(j).result());
            int total = 0;
            for (int v : a.ingredients().values()) total += v;
            if (a.layout() != null) {
                ctx.assertTrue(a.layout().length == SuperBenchRecipes.SLOTS, a.result() + " 蓝图不是 144 格：" + a.layout().length);
                ctx.assertTrue(total <= SuperBenchRecipes.SLOTS, a.result() + " 蓝图版 BOM 总件数 " + total + " > 144");
                Map<String, Integer> fromLayout = new HashMap<>();
                for (String cell : a.layout()) if (cell != null) fromLayout.merge(cell, 1, Integer::sum);
                ctx.assertTrue(fromLayout.equals(a.ingredients()), a.result() + " 蓝图逐格多重集 ≠ BOM：" + fromLayout + " vs " + a.ingredients());
            }
        }
        ctx.succeed();
    }

    /** ②配方表全部 id 对本世代注册表：解析不出的必须在已知缺口白名单里（否则=新缺口，红）；白名单里的必须仍解析不出且仍在表里（否则=缺口已补/条目过期，白名单该滚，红）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_recipe_ids_resolvable_or_ledgered(GameTestHelper ctx) {
        List<String> newGap = new ArrayList<>(), stale = new ArrayList<>();
        Set<String> seenRes = new java.util.HashSet<>(), seenIng = new java.util.HashSet<>();
        int reachable = 0;
        for (SuperBenchRecipes.Recipe r : SuperBenchRecipes.ALL) {
            boolean ok = true;
            seenRes.add(r.result());
            if (!resolvable(r.result())) { ok = false; if (!KNOWN_GAP_RESULTS.contains(r.result())) newGap.add("结果件 " + r.result()); }
            for (String ing : r.ingredients().keySet()) {
                seenIng.add(ing);
                if (!resolvable(ing)) { ok = false; if (!KNOWN_GAP_INGREDIENTS.contains(ing)) newGap.add("材料 " + ing + "（配方 " + r.result() + "）"); }
            }
            if (ok) reachable++;
        }
        for (String id : KNOWN_GAP_RESULTS) if (resolvable(id) || !seenRes.contains(id)) stale.add("结果件白名单 " + id + (resolvable(id) ? " 已能解析" : " 不在配方表"));
        for (String id : KNOWN_GAP_INGREDIENTS)   if (resolvable(id) || !seenIng.contains(id)) stale.add("材料白名单 " + id + (resolvable(id) ? " 已能解析" : " 不在配方表"));
        ctx.assertTrue(newGap.isEmpty(), "1.20.1 新缺口（不在白名单）：" + newGap);
        ctx.assertTrue(stale.isEmpty(), "缺口白名单过期该滚：" + stale);
        ctx.assertTrue(reachable == EXPECTED_REACHABLE, "本世代可达配方数账面 " + EXPECTED_REACHABLE + "，实得 " + reachable + "（变了就改 EXPECTED_REACHABLE 并滚白名单/缺口表）");
        ctx.assertTrue((smallestReachable() == null) == (EXPECTED_REACHABLE == 0), "可达账与端到端选取不一致（有可达配方却选不出=全是刷怪类/打包版）");
        ctx.succeed();
    }

    /** ③端到端：按蓝图逐格铺 1 件→结果槽出机器→左键 PICKUP 取走（doClick→tryRemove→onTake→consumeIngredients，服务端权威）→手上是机器、网格全空、结果槽空。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_end_to_end_craft_consumes_bom(GameTestHelper ctx) {
        SuperBenchRecipes.Recipe r = smallestReachable();
        if (r == null) { // 账说 0（core_module 未注册）→ 本判官空转待 SB6；账不是 0 却选不出=红
            ctx.assertTrue(EXPECTED_REACHABLE == 0, "可达账 " + EXPECTED_REACHABLE + " 条却选不出无生物有蓝图的配方");
            ctx.succeed(); return;
        }
        Player p = ctx.makeMockPlayer();
        SuperBenchScreenHandler h = new SuperBenchScreenHandler(1, p.getInventory());
        String[] lay = r.layout();
        for (int i = 0; i < SuperBenchScreenHandler.GRID_SLOTS; i++)
            if (lay[i] != null) h.getSlot(i).set(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(lay[i])), 1)); // Slot.set→setChanged→slotsChanged→match
        ItemStack want = SuperBenchRecipes.resultStack(r);
        ItemStack res = h.getSlot(SuperBenchScreenHandler.RESULT_INDEX).getItem();
        ctx.assertTrue(ItemStack.isSameItemSameTags(res, want) && res.getCount() == want.getCount(),
                r.result() + " 铺满蓝图后结果槽该是 " + want + "，实得 " + res);
        h.clicked(SuperBenchScreenHandler.RESULT_INDEX, 0, ClickType.PICKUP, p);
        ItemStack carried = h.getCarried();
        ctx.assertTrue(ItemStack.isSameItemSameTags(carried, want) && carried.getCount() == want.getCount(), "取走后手上该是 " + want + "，实得 " + carried);
        ctx.assertTrue(gridCount(h) == 0, "取走后网格该全空（扣料守恒），剩 " + gridCount(h));
        ctx.assertTrue(h.getSlot(SuperBenchScreenHandler.RESULT_INDEX).getItem().isEmpty(), "取走后结果槽该空（slotsChanged 重算=无匹配）");
        ctx.succeed();
    }

    /** ④浏览器填料 clickMenuButton(idx)：背包→网格按蓝图铺满、结果出、背包按 BOM 减；空宿主两钮回 true 且网格一件不少；换台回收进背包；越界 id 回 false；全程件数守恒。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_fill_from_inventory_buttons_conserve(GameTestHelper ctx) {
        SuperBenchRecipes.Recipe r = smallestReachable();
        if (r == null) { // 同③：账说 0 → 空转待 SB6
            ctx.assertTrue(EXPECTED_REACHABLE == 0, "可达账 " + EXPECTED_REACHABLE + " 条却选不出无生物有蓝图的配方");
            ctx.succeed(); return;
        }
        int idx = SuperBenchRecipes.ALL.indexOf(r);
        Player p = ctx.makeMockPlayer();
        SuperBenchScreenHandler h = new SuperBenchScreenHandler(1, p.getInventory());
        Map<String, Integer> bom = r.ingredients();
        for (Map.Entry<String, Integer> e : bom.entrySet())
            ctx.assertTrue(p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey())), e.getValue())), "给背包放料失败 " + e.getKey());
        Map<String, Integer> before = new HashMap<>();
        for (String id : bom.keySet()) before.put(id, countEverywhere(h, id));

        ctx.assertTrue(h.clickMenuButton(p, idx), "配方下标该被 handler 认领");
        ItemStack want = SuperBenchRecipes.resultStack(r);
        ItemStack res = h.getSlot(SuperBenchScreenHandler.RESULT_INDEX).getItem();
        ctx.assertTrue(ItemStack.isSameItemSameTags(res, want) && res.getCount() == want.getCount(), "填料后结果槽该是 " + want + "，实得 " + res);
        int total = 0; for (int v : bom.values()) total += v;
        ctx.assertTrue(gridCount(h) == total, "填料后网格件数该=BOM 总件 " + total + "，实得 " + gridCount(h));
        for (String id : bom.keySet()) ctx.assertTrue(countEverywhere(h, id) == before.get(id), "填料件数不守恒 " + id);

        ctx.assertTrue(h.clickMenuButton(p, SuperBenchScreenHandler.BTN_COMPRESS), "压缩钮该回 true（服务端拦截并明说）");
        ctx.assertTrue(h.clickMenuButton(p, SuperBenchScreenHandler.BTN_UNPACK), "拆包钮该回 true");
        ctx.assertTrue(gridCount(h) == total, "空宿主下两钮该零动作，网格件数变了：" + gridCount(h));
        for (String id : bom.keySet()) ctx.assertTrue(countEverywhere(h, id) == before.get(id), "两钮后件数不守恒 " + id);

        int other = (idx + 1) % SuperBenchRecipes.ALL.size();
        ctx.assertTrue(h.clickMenuButton(p, other), "换台该被认领");
        for (String id : bom.keySet()) ctx.assertTrue(countEverywhere(h, id) == before.get(id), "换台回收后件数不守恒 " + id);
        ctx.assertTrue(!h.clickMenuButton(p, -1) && !h.clickMenuButton(p, SuperBenchRecipes.ALL.size()), "越界配方下标该回 false");
        ctx.succeed();
    }

    /** ⑤quickMove：网格→背包、背包→网格；结果槽 shift 无动作（"结果槽用鼠标取"，m95 128 槽协议上限口径）。 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void super_bench_quickmove_grid_inventory_result_noop(GameTestHelper ctx) {
        Player p = ctx.makeMockPlayer();
        SuperBenchScreenHandler h = new SuperBenchScreenHandler(1, p.getInventory());
        h.getSlot(0).set(new ItemStack(Items.COBBLESTONE, 32));
        ItemStack ret = h.quickMoveStack(p, 0);
        ctx.assertTrue(!ret.isEmpty() && h.getSlot(0).getItem().isEmpty(), "网格 shift 该搬进背包");
        ctx.assertTrue(countEverywhere(h, "minecraft:cobblestone") == 32, "搬运件数不守恒");
        int invSlot = -1;
        for (int i = SuperBenchScreenHandler.RESULT_INDEX + 1; i < h.slots.size(); i++) if (h.getSlot(i).getItem().is(Items.COBBLESTONE)) { invSlot = i; break; }
        ctx.assertTrue(invSlot >= 0, "背包槽里该有圆石");
        h.quickMoveStack(p, invSlot);
        ctx.assertTrue(gridCount(h) == 32 && h.getSlot(invSlot).getItem().isEmpty(), "背包 shift 该搬回网格");
        h.getSlot(SuperBenchScreenHandler.RESULT_INDEX).set(new ItemStack(Items.STONE, 1)); // 结果槽直接放一件
        ctx.assertTrue(h.quickMoveStack(p, SuperBenchScreenHandler.RESULT_INDEX).isEmpty()
                && h.getSlot(SuperBenchScreenHandler.RESULT_INDEX).getItem().is(Items.STONE), "结果槽 shift 该无动作");
        ctx.succeed();
    }
}
