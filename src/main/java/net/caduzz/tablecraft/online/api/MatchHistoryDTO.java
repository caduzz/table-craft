package net.caduzz.tablecraft.online.api;

import javax.annotation.Nullable;

/**
 * Uma entrada de {@code GET /tablecraft/v1/me/history}.
 */
public record MatchHistoryDTO(
        String matchId,
        String gameType,
        String status,
        String yourSide,
        String opponentDisplayName,
        String outcome,
        @Nullable String result,
        String createdAt) {

    public MatchHistoryDTO {
        matchId = matchId != null ? matchId : "";
        gameType = gameType != null ? gameType : "";
        status = status != null ? status : "";
        yourSide = yourSide != null ? yourSide : "";
        opponentDisplayName = opponentDisplayName != null ? opponentDisplayName : "";
        outcome = outcome != null ? outcome : "";
        createdAt = createdAt != null ? createdAt : "";
    }
}
