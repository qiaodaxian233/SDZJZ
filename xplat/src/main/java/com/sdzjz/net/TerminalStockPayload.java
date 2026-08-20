package com.sdzjz.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端（m289）：存储终端全网库存摘要，喂给配方书"可合成"判定（m281 挂账：
 * 灰名单原只认背包+网格，仓储里有料的配方被错灰）。
 * 并行列表编码（CanvasEndsPayload 同律）：ids/counts 同序；syncId 路由到正开着的 handler。
 * 摘要按存量取前 2048 种、计数封顶 9999（判定/整组填料足够，精确数在服务端），
 * 服务端指纹节流：内容没变不发（见 DataPanelScreenHandler.sendContentUpdates）。
 */
public record TerminalStockPayload(int syncId, List<String> ids, List<Integer> counts, boolean truncated) implements CustomPayload { // m298 truncated=摘要超额被截，客户端"缺席≠0"

    public static final CustomPayload.Id<TerminalStockPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sdzjz", "terminal_stock"));

    public static final PacketCodec<RegistryByteBuf, TerminalStockPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TerminalStockPayload::syncId,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), TerminalStockPayload::ids,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.VAR_INT), TerminalStockPayload::counts,
            PacketCodecs.BOOL, TerminalStockPayload::truncated, // m298
            TerminalStockPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
