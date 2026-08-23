package com.sdzjz.retro;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * m458（C2-④c）：机器↔存储连线两包——对位蓝本 StorageLink/StorageNodeMove（m455 稿归类第三组；
 * StorageNodeHome 归停靠美化到序再排）。定长字段零串零表；服务端前验+存在性校验后执行。
 * StorageLink 为**循环手势**：同一(机器,端点)反复触发=无→产出(0)→供料(1)→断（蓝本拆两方向
 * 显式选择的世代精简，记档；C2-⑤ 消费口径：产出=机器出货入该仓，供料=该仓给机器上料）。
 */
final class StoragePayloads120 {

    private StoragePayloads120() { }

    record StorageLink(BlockPos pos, int machine, long endpoint) implements FabricPacket {
        static final PacketType<StorageLink> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "storage_link"), StorageLink::new);

        StorageLink(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readVarInt(), buf.readLong()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(machine);
            buf.writeLong(endpoint);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    record StorageNodeMove(BlockPos pos, long endpoint, int x, int y) implements FabricPacket {
        static final PacketType<StorageNodeMove> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "storage_node_move"), StorageNodeMove::new);

        StorageNodeMove(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readLong(), buf.readVarInt(), buf.readVarInt()); }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeLong(endpoint);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }
}
