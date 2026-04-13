package net.caduzz.tablecraft.client.online;

import net.caduzz.tablecraft.online.api.ChessApiSnapshot;

/**
 * Quando uma partida de xadrez online termina, remove o {@code matchId} do perfil local e do cache de {@code /me}.
 */
public final class ClientOnlineChessAfterMatch {
    private ClientOnlineChessAfterMatch() {
    }

    public static void clearCachedBindingsIfSameMatch(String finishedMatchId) {
        if (finishedMatchId == null || finishedMatchId.isEmpty()) {
            return;
        }
        String cachedMid = ClientPlayerRegistrationStore.getLastChessMatchId();
        if (!cachedMid.isEmpty() && cachedMid.equals(finishedMatchId)) {
            ClientPlayerRegistrationStore.setLastChessOnlineBinding("", "");
        }
        String activeMid = OnlineProfileCache.getActiveChessMatchId();
        if (!activeMid.isEmpty() && activeMid.equals(finishedMatchId)) {
            OnlineProfileCache.clearActiveChessBinding();
        }
    }

    /** Se o snapshot não estiver {@code PLAYING}, limpa caches que referem este {@code matchId}. */
    public static void clearCachedBindingsIfSnapshotEnded(String matchId, ChessApiSnapshot snap) {
        if (snap == null || matchId == null || matchId.isEmpty()) {
            return;
        }
        if ("PLAYING".equals(snap.statusKey())) {
            return;
        }
        clearCachedBindingsIfSameMatch(matchId);
    }
}
