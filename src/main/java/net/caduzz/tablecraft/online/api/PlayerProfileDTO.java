package net.caduzz.tablecraft.online.api;

import javax.annotation.Nullable;

/**
 * Resposta de {@code GET /tablecraft/v1/me} (campos opcionais tolerantes a omissões da API).
 */
public record PlayerProfileDTO(
        @Nullable String playerUuid,
        String displayName,
        int rating,
        int wins,
        int losses,
        String memberSince,
        /** Partida de xadrez em curso no servidor, se a API expuser em {@code GET /me}. */
        @Nullable String activeChessMatchId,
        /** {@code WHITE} ou {@code BLACK} para a partida ativa, quando conhecido. */
        @Nullable String activeChessYourSide) {

    public PlayerProfileDTO {
        displayName = displayName != null ? displayName : "";
        memberSince = memberSince != null ? memberSince : "";
    }
}
