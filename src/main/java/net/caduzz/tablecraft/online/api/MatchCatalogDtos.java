package net.caduzz.tablecraft.online.api;

import java.util.List;

import javax.annotation.Nullable;

/**
 * DTOs para catálogo de partidas ({@code GET /matches/live}, {@code GET /matches/:matchId}).
 */
public final class MatchCatalogDtos {
    private MatchCatalogDtos() {
    }

    public record CatalogPlayer(@Nullable String uuid, String displayName) {
    }

    public record LiveMatchSummary(String matchId, String gameType, String turn, @Nullable CatalogPlayer white, @Nullable CatalogPlayer black) {
    }

    public record MatchDetailInfo(boolean ok, String matchId, String gameType, String status, String turn,
            @Nullable CatalogPlayer whitePlayer, @Nullable CatalogPlayer blackPlayer) {
    }

    public record LiveMatchesResponse(boolean ok, List<LiveMatchSummary> matches) {
    }
}
