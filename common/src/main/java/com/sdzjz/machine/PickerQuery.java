package com.sdzjz.machine;

/**
 * m335 选择器查询语法（**学** JEI 的用户语法习惯——@模组前缀/-排除/|并联，**实现全自写**，
 * 作者明令"要学习不要搬代码"）。纯函数零 MC 依赖，放 machine 包供双端与廿四号用例直测。
 *
 * 语法：`|` 分组=或；组内空格分词=与；`-词`=排除；`@词`=按命名空间（模组id）匹配；
 * 普通词=匹配显示名或注册路径；全程不区分大小写；空查询恒真。比 JEI 更宽松的一点：
 * 允许纯排除查询（如 `-stone`），不要求先有正向词。
 */
public final class PickerQuery {
    private PickerQuery() {}

    /**
     * @param nameLower 物品显示名（调用方已转小写，语言相关）
     * @param id        完整注册 id，如 "minecraft:diamond"（原生已是小写）
     */
    public static boolean matches(String nameLower, String id, String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return true;
        String ns = "", path = id;
        int c = id.indexOf(':');
        if (c >= 0) { ns = id.substring(0, c); path = id.substring(c + 1); }
        for (String group : q.split("\\|"))
            if (groupMatches(nameLower, ns, path, group.trim())) return true;
        return false;
    }

    private static boolean groupMatches(String name, String ns, String path, String group) {
        if (group.isEmpty()) return false; // 尾随 "|" 的空组不放行（空组=没写条件）
        boolean anyTerm = false;
        for (String term : group.split("\\s+")) {
            boolean neg = term.startsWith("-");
            if (neg) term = term.substring(1);
            boolean byMod = term.startsWith("@");
            if (byMod) term = term.substring(1);
            if (term.isEmpty()) continue; // 裸 "-" / "@" 忽略
            anyTerm = true;
            boolean hit = byMod ? ns.contains(term) : (name.contains(term) || path.contains(term));
            if (hit == neg) return false; // 该命中没命中 / 该排除却命中
        }
        return anyTerm; // 全组都是废词（如 " - @ "）=没写条件，不放行
    }
}
