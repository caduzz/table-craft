package net.caduzz.tablecraft.network;

import io.netty.buffer.ByteBuf;
import net.caduzz.tablecraft.TableCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Desliga modo online na mesa indicada. */
public record OnlineTableClearPayload(BlockPos pos, int gameKind) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OnlineTableClearPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "online_table_clear"));

    public static final StreamCodec<ByteBuf, OnlineTableClearPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OnlineTableClearPayload::pos,
            ByteBufCodecs.VAR_INT,
            OnlineTableClearPayload::gameKind,
            OnlineTableClearPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
