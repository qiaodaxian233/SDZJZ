package com.sdzjz.retro;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * m456（C2-④a）：画布快照两包——轮询请求-响应制（m455 稿定性，同 m447 面板口径；Legacy 的
 * 观众推送+200t 自愈随规模再上）。快照体=CanvasGraphState120.writeRenderNbt 整包过 writeNbt；
 * **有界红线落应用层**（m455 稿：解码 readNbt 有原版 NbtAccounter 顶着，节点数硬顶在
 * 应用函数里再验——CanvasScreen120.applyGuard 1024 常量，超界整包拒绝）。
 */
final class CanvasPayloads120 {

    private CanvasPayloads120() { }

    /** 应用层节点数硬顶（m455 稿 1024）。 */
    static final int MAX_NODES = 1024;

    /** C2S：客户端请求一帧画布快照（开屏首查/每 20t/每操作后）。 */
    record CanvasQuery(BlockPos pos) implements FabricPacket {
        static final PacketType<CanvasQuery> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "canvas_query"), CanvasQuery::new);

        CanvasQuery(FriendlyByteBuf buf) { this(buf.readBlockPos()); }

        @Override public void write(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

        @Override public PacketType<?> getType() { return TYPE; }
    }

    /** S2C：一帧渲染快照（键布局=CanvasGraphState120，蓝本同名）。 */
    record CanvasSnapshot(BlockPos pos, CompoundTag render) implements FabricPacket {
        static final PacketType<CanvasSnapshot> TYPE =
                PacketType.create(new ResourceLocation("sdzjz", "canvas_snapshot"), CanvasSnapshot::new);

        CanvasSnapshot(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readNbt()); } // 1.20.1 readNbt 自带 NbtAccounter 顶

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeNbt(render);
        }

        @Override public PacketType<?> getType() { return TYPE; }
    }
}
