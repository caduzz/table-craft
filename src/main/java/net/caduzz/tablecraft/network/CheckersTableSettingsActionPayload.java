package net.caduzz.tablecraft.network;

import io.netty.buffer.ByteBuf;
import net.caduzz.tablecraft.TableCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Ações do menu da mesa de damas (cliente → servidor). Sem opção de “último movimento”. */
public record CheckersTableSettingsActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CheckersTableSettingsActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "checkers_table_settings_action"));

    public static final StreamCodec<ByteBuf, CheckersTableSettingsActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            CheckersTableSettingsActionPayload::pos,
            ByteBufCodecs.VAR_INT,
            CheckersTableSettingsActionPayload::action,
            CheckersTableSettingsActionPayload::new);

    public static final int ACTION_TOGGLE_LEGAL_HINTS = 0;
    public static final int ACTION_TIMER_NEXT = 1;
    public static final int ACTION_TIMER_PREV = 2;
    public static final int ACTION_RESET_BOARD = 3;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
