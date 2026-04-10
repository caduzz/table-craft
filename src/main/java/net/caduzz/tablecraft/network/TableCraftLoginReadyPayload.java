package net.caduzz.tablecraft.network;

import io.netty.buffer.ByteBuf;
import net.caduzz.tablecraft.TableCraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: o servidor avisa o cliente para executar autenticação / validação de sessão ao entrar no mundo.
 */
public record TableCraftLoginReadyPayload(int _pad) implements CustomPacketPayload {
    public static final TableCraftLoginReadyPayload INSTANCE = new TableCraftLoginReadyPayload(0);

    public static final CustomPacketPayload.Type<TableCraftLoginReadyPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "login_ready"));

    public static final StreamCodec<ByteBuf, TableCraftLoginReadyPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(v -> INSTANCE, p -> 0);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
