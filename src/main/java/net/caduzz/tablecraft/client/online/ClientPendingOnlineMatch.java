package net.caduzz.tablecraft.client.online;

import net.caduzz.tablecraft.network.OnlineTableBindPayload;

/**
 * Resultado do matchmaking no cliente, usado ao “ligar” a mesa ao bloco visado.
 */
public final class ClientPendingOnlineMatch {
    private static final Object LOCK = new Object();
    private static int gameKind = -1;
    private static String sessionId = "";
    private static String matchId = "";
    private static int sideOrdinal;

    private ClientPendingOnlineMatch() {
    }

    public static void setPending(int kind, String sid, String mid, String sideLabel) {
        synchronized (LOCK) {
            gameKind = kind;
            sessionId = sid != null ? sid : "";
            matchId = mid != null ? mid : "";
            sideOrdinal = "BLACK".equalsIgnoreCase(sideLabel) ? 1 : 0;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            gameKind = -1;
            sessionId = "";
            matchId = "";
            sideOrdinal = 0;
        }
    }

    public static boolean hasPending() {
        synchronized (LOCK) {
            return gameKind == OnlineTableBindPayload.GAME_CHESS && matchId != null && !matchId.isEmpty();
        }
    }

    public static int getGameKind() {
        return gameKind;
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static String getMatchId() {
        return matchId;
    }

    public static int getSideOrdinal() {
        return sideOrdinal;
    }
}
