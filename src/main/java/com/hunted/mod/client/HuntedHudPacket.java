package com.hunted.mod.client;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HuntedHudPacket(
    boolean eventActive,
    boolean isTarget,
    String  targetName,
    int     targetX,
    int     targetY,
    int     targetZ,
    int     eventSecsLeft,
    int     distToTarget,
    String  dirToTarget
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("hunted", "hud_sync");
    public static final Type<HuntedHudPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, HuntedHudPacket> CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.eventActive());
                buf.writeBoolean(pkt.isTarget());
                buf.writeUtf(pkt.targetName());
                buf.writeInt(pkt.targetX());
                buf.writeInt(pkt.targetY());
                buf.writeInt(pkt.targetZ());
                buf.writeInt(pkt.eventSecsLeft());
                buf.writeInt(pkt.distToTarget());
                buf.writeUtf(pkt.dirToTarget());
            },
            buf -> new HuntedHudPacket(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
