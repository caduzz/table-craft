package net.caduzz.tablecraft.network;

import io.netty.buffer.ByteBuf;
import net.caduzz.tablecraft.TableCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Ações do menu da mesa de xadrez (cliente → servidor).
 */
public record ChessTableSettingsActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChessTableSettingsActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "chess_table_settings_action"));

    public static final StreamCodec<ByteBuf, ChessTableSettingsActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ChessTableSettingsActionPayload::pos,
            ByteBufCodecs.VAR_INT,
            ChessTableSettingsActionPayload::action,
            ChessTableSettingsActionPayload::new);

    /** Alterna realce do último movimento. */
    public static final int ACTION_TOGGLE_PREVIOUS_MOVE = 0;
    /** Alterna indicadores de jogadas possíveis no tabuleiro. */
    public static final int ACTION_TOGGLE_LEGAL_HINTS = 1;
    /** Próximo preset de tempo (só antes da partida com dois assentos). */
    public static final int ACTION_TIMER_NEXT = 2;
    /** Preset de tempo anterior. */
    public static final int ACTION_TIMER_PREV = 3;
    /** Reinicia tabuleiro, assentos e relógio. */
    public static final int ACTION_RESET_BOARD = 4;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
