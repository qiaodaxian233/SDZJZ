package com.sdzjz.retro;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * m447（P-C1 刀②）：数据面板三 payload——1.20.1 精简协议为**从新设计**（三包请求-响应制），
 * 不是 Legacy DataPanelViewPayload 家族（831 行 Handler 协议）的逐位移植：Legacy 的客户端本地化名
 * 静态索引（m107）要求全量快照下发，精简版反过来让服务端按查询串开窗下发（每包 ≤54 条），
 * 写包预算天然有界。红线全数随迁：C2S 解码期有界（Net120 两锚点口）、服务端权威（收包先验
 * "菜单确实开在该面板"再执行）、取不完余量回账本绝不落地。协议差异记档 DEVLOG m447。
 *
 * <p>编解码随包类自带（FabricPacket 体系，m446 口径）：读侧=buf 构造器，写侧=write。
 */
final class PanelPayloads120 {

    private PanelPayloads120() { }

    /** 单行条目：display=展示模板（count 恒 1；exact=连 tag 的精确模板，否则裸物品），n=账面数。 */
    record Row(ItemStack display, long n, boolean exact) { }

    /** C2S 查询：客户端上报视图状态（查询串+滚动行），服务端按窗回 Rows。
     *  查询按物品 id 串匹配（服务端无本地化名——精简版限定，m448 屏内注明，P-C2 再议索引下放）。 */
    record Query(BlockPos pos, String query, int scrollRow) implements FabricPacket {
        static final PacketType<Query> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "panel_query"), Query::new);
        static final int MAX_QUERY = 64;

        Query(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), Net120.readBoundedUtf(buf, MAX_QUERY), buf.readVarInt()); // m291 锚点口
        }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(query, MAX_QUERY);
            buf.writeVarInt(scrollRow);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** S2C 行窗：匹配总行数+当前滚动行+至多 WINDOW 条目（9 列×6 行）。 */
    record Rows(int totalRows, int scrollRow, List<Row> rows) implements FabricPacket {
        static final PacketType<Rows> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "panel_rows"), Rows::new);
        static final int WINDOW = 54;

        Rows(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt(), readRows(buf));
        }

        private static List<Row> readRows(FriendlyByteBuf buf) {
            int n = Net120.readBoundedCount(buf, WINDOW); // 分配前验（S2C 同过红线，双向不豁免）
            List<Row> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new Row(buf.readItem(), buf.readLong(), buf.readBoolean()));
            return out;
        }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(totalRows);
            buf.writeVarInt(scrollRow);
            buf.writeVarInt(Math.min(rows.size(), WINDOW));
            int wrote = 0;
            for (Row r : rows) {
                if (wrote++ >= WINDOW) break; // 写侧同顶：声明数与实写数恒一致（m106 哨兵成对审精神）
                buf.writeItem(r.display());
                buf.writeLong(r.n());
                buf.writeBoolean(r.exact());
            }
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** C2S 取物：exact=按模板走精确账本（template 生效，id 忽略），否则按 id 走普通账本。
     *  amount 客户端只作意愿申报，服务端钳位后执行（服务端权威，客户端数字不可信）。 */
    record Take(BlockPos pos, boolean exact, String id, ItemStack template, int amount) implements FabricPacket {
        static final PacketType<Take> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "panel_take"), Take::new);
        static final int MAX_ID = 256;

        Take(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), buf.readBoolean(), Net120.readBoundedUtf(buf, MAX_ID), // m291 锚点口
                    buf.readItem(), buf.readVarInt());
        }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(exact);
            buf.writeUtf(id, MAX_ID);
            buf.writeItem(template);
            buf.writeVarInt(amount);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }
}
