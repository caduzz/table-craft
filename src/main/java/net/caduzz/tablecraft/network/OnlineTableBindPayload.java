package net.caduzz.tablecraft.network;

import io.netty.buffer.ByteBuf;
import net.caduzz.tablecraft.TableCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Liga uma mesa do mundo à partida atual (dados vindos do cliente após matchmaking na API). */
public record OnlineTableBindPayload(BlockPos pos, int gameKind, String sessionId, String matchId, int sideOrdinal) implements CustomPacketPayload {
    public static final int GAME_CHESS = 0;

    public static final CustomPacketPayload.Type<OnlineTableBindPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "online_table_bind"));

    public static final StreamCodec<ByteBuf, OnlineTableBindPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OnlineTableBindPayload::pos,
            ByteBufCodecs.VAR_INT,
            OnlineTableBindPayload::gameKind,
            ByteBufCodecs.STRING_UTF8,
            OnlineTableBindPayload::sessionId,
            ByteBufCodecs.STRING_UTF8,
            OnlineTableBindPayload::matchId,
            ByteBufCodecs.VAR_INT,
            OnlineTableBindPayload::sideOrdinal,
            OnlineTableBindPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
