package com.sdzjz.net;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 服务端→客户端（m275，审计第一批第 3 条"全量 NBT 同步拆分"）：结构核心渲染快照，
 * 定向发给正在看画布的玩家。取代原 vanilla BE 全量 NBT 区块广播（存档级全量×所有追踪玩家）：
 * 内容=画布消费面渲染子集（SCBE.writeRenderNbt），接收方=仅观众（m89 管线同款判定），
 * 频率=标脏聚合每 tick 至多 1 份。收端写回客户端 BE 渲染字段（applyRenderSnapshot），
 * 画布屏 be() 读法零改动。方案与消费面清单见 docs/同步拆分方案_m274.md。
 */
public record CanvasSnapshotPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {

    public static final CustomPayload.Id<CanvasSnapshotPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "canvas_snapshot"));

    public static final PacketCodec<RegistryByteBuf, CanvasSnapshotPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, CanvasSnapshotPayload::pos,
            PacketCodecs.UNLIMITED_NBT_COMPOUND, CanvasSnapshotPayload::nbt,
            CanvasSnapshotPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
