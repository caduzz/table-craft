package net.caduzz.tablecraft.client.online;

import net.caduzz.tablecraft.online.api.PlayerProfileDTO;

/**
 * Cache do perfil API no cliente (preenchido após {@code GET /me}).
 */
public final class OnlineProfileCache {
    private static final Object LOCK = new Object();
    private static volatile String cachedDisplayName = "";
    private static volatile int playerRating;
    private static volatile boolean profileLoaded;
    private static volatile String activeChessMatchId = "";
    private static volatile String activeChessYourSide = "";

    private OnlineProfileCache() {
    }

    public static void updateFromProfile(PlayerProfileDTO profile) {
        synchronized (LOCK) {
            cachedDisplayName = profile.displayName() != null && !profile.displayName().isEmpty() ? profile.displayName() : "—";
            playerRating = profile.rating();
            profileLoaded = true;
            activeChessMatchId = profile.activeChessMatchId() != null ? profile.activeChessMatchId() : "";
            activeChessYourSide = profile.activeChessYourSide() != null ? profile.activeChessYourSide() : "";
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            cachedDisplayName = "";
            playerRating = 0;
            profileLoaded = false;
            activeChessMatchId = "";
            activeChessYourSide = "";
        }
    }

    /** Limpa só a partida ativa de xadrez (ex.: após fim na API), mantendo nome/rating em cache. */
    public static void clearActiveChessBinding() {
        synchronized (LOCK) {
            activeChessMatchId = "";
            activeChessYourSide = "";
        }
    }

    public static String getCachedDisplayName() {
        return cachedDisplayName;
    }

    public static int getPlayerRating() {
        return playerRating;
    }

    public static boolean isProfileLoaded() {
        return profileLoaded;
    }

    public static String getActiveChessMatchId() {
        return activeChessMatchId;
    }

    public static String getActiveChessYourSide() {
        return activeChessYourSide;
    }
}
