package net.caduzz.tablecraft.client.online;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.online.api.ChessApiSnapshot;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.caduzz.tablecraft.online.api.PlayerProfileDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Rede e polling de matchmaking (3 s) isolados da GUI.
 */
public final class ChessMatchmakingManager {
    public static final int POLL_INTERVAL_MS = 3000;
    private static final long MAX_QUEUE_MS = 12L * 60L * 1000L;

    private final Minecraft minecraft;
    private final Runnable onUiRefresh;
    private final BiConsumer<String, String> onMatchFound;
    private final AtomicBoolean searching = new AtomicBoolean(false);
    private volatile int pollToken;
    private volatile long queueDeadlineMs;
    private volatile Component statusLine = Component.empty();
    private volatile boolean awaitingReauthRetry;

    public ChessMatchmakingManager(Minecraft minecraft, Runnable onUiRefresh, BiConsumer<String, String> onMatchFound) {
        this.minecraft = minecraft;
        this.onUiRefresh = onUiRefresh;
        this.onMatchFound = onMatchFound;
    }

    public boolean isSearching() {
        return searching.get();
    }

    public Component getStatusLine() {
        return statusLine;
    }

    public void startSearch() {
        if (!SessionManager.hasSession()) {
            statusLine = Component.literal("Sem sessão API — aguarde o login no mundo ou registe na mesa.");
            onUiRefresh.run();
            return;
        }
        if (!searching.compareAndSet(false, true)) {
            return;
        }
        pollToken++;
        queueDeadlineMs = System.currentTimeMillis() + MAX_QUEUE_MS;
        awaitingReauthRetry = false;
        statusLine = Component.literal("Verificando partida em curso…");
        onUiRefresh.run();
        checkResumeThenQueue(pollToken, true);
    }

    public void cancelSearch() {
        pollToken++;
        searching.set(false);
        awaitingReauthRetry = false;
        statusLine = Component.literal("Cancelado.");
        onUiRefresh.run();
    }

    /**
     * Antes de entrar na fila: {@code GET /me} (partida ativa na API) e, se necessário, último {@code matchId}
     * persistido + {@code GET .../state} para retomar após queda de rede.
     */
    private void checkResumeThenQueue(int token, boolean allowSilentReRegister) {
        if (token != pollToken || !searching.get()) {
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String sessionToken = SessionManager.getSessionId();
        GameApiClient.getMyProfile(base, sessionToken).whenComplete((prof, err) -> minecraft.execute(() -> {
            if (token != pollToken || !searching.get()) {
                return;
            }
            if (prof != null) {
                OnlineProfileCache.updateFromProfile(prof);
            }
            if (err != null) {
                if (allowSilentReRegister && !awaitingReauthRetry && isUnauthorized(err) && minecraft.player != null) {
                    awaitingReauthRetry = true;
                    silentReRegisterAndRetryResume(token);
                    return;
                }
                tryLocalStoredMatchResume(token, allowSilentReRegister);
                return;
            }
            String currentMatchId = resolveActiveMatchIdFromProfile(prof);
            String sideHint = resolveActiveSideFromProfile(prof);
            if (currentMatchId != null && !currentMatchId.isEmpty()) {
                verifyPlayingAndResume(currentMatchId, sideHint, token, allowSilentReRegister);
                return;
            }
            tryLocalStoredMatchResume(token, allowSilentReRegister);
        }));
    }

    private static String resolveActiveMatchIdFromProfile(PlayerProfileDTO prof) {
        if (prof != null && prof.activeChessMatchId() != null && !prof.activeChessMatchId().isBlank()) {
            return prof.activeChessMatchId().trim();
        }
        String cached = OnlineProfileCache.getActiveChessMatchId();
        return cached != null && !cached.isBlank() ? cached.trim() : "";
    }

    private static String resolveActiveSideFromProfile(PlayerProfileDTO prof) {
        if (prof != null && prof.activeChessYourSide() != null && !prof.activeChessYourSide().isBlank()) {
            return normalizeSideForBind(prof.activeChessYourSide());
        }
        String cached = OnlineProfileCache.getActiveChessYourSide();
        return cached != null && !cached.isBlank() ? normalizeSideForBind(cached) : "";
    }

    private void tryLocalStoredMatchResume(int token, boolean allowSilentReRegister) {
        if (token != pollToken || !searching.get()) {
            return;
        }
        String currentMatchId = ClientPlayerRegistrationStore.getLastChessMatchId();
        if (currentMatchId == null || currentMatchId.isEmpty()) {
            pollOnce(token, allowSilentReRegister);
            return;
        }
        String storedSide = ClientPlayerRegistrationStore.getLastChessSide();
        verifyPlayingAndResume(currentMatchId, storedSide.isEmpty() ? null : storedSide, token, allowSilentReRegister);
    }

    private void verifyPlayingAndResume(String currentMatchId, String knownSideOrNull, int token, boolean allowSilentReRegister) {
        if (token != pollToken || !searching.get()) {
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String sessionToken = SessionManager.getSessionId();
        GameApiClient.getChessStateWithMeta(base, sessionToken, currentMatchId).whenComplete((meta, err) -> minecraft.execute(() -> {
            if (token != pollToken || !searching.get()) {
                return;
            }
            if (err != null) {
                if (allowSilentReRegister && !awaitingReauthRetry && isUnauthorized(err) && minecraft.player != null) {
                    awaitingReauthRetry = true;
                    silentReRegisterAndRetryResume(token);
                    return;
                }
                pollOnce(token, allowSilentReRegister);
                return;
            }
            if (!isChessMatchInProgress(meta.snapshot())) {
                pollOnce(token, allowSilentReRegister);
                return;
            }
            String side = coalesceSideHint(knownSideOrNull, meta.yourSide(), ClientPlayerRegistrationStore.getLastChessSide());
            if (side == null) {
                pollOnce(token, allowSilentReRegister);
                return;
            }
            finishMatchFound(currentMatchId, side, token, Component.literal("Retomando partida em curso…"));
        }));
    }

    private static boolean isChessMatchInProgress(ChessApiSnapshot snap) {
        return snap != null && "PLAYING".equals(snap.statusKey());
    }

    /**
     * @return {@code WHITE}, {@code BLACK} ou {@code null} se não for possível inferir
     */
    private static String coalesceSideHint(String... candidates) {
        for (String c : candidates) {
            if (c == null || c.isBlank()) {
                continue;
            }
            String n = normalizeSideForBind(c);
            if (!n.isEmpty()) {
                return n;
            }
        }
        return null;
    }

    private static String normalizeSideForBind(String raw) {
        if (raw == null) {
            return "";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.contains("BLACK") || u.equals("B")) {
            return "BLACK";
        }
        if (u.contains("WHITE") || u.equals("W")) {
            return "WHITE";
        }
        return "";
    }

    private void finishMatchFound(String currentMatchId, String side, int token, Component status) {
        searching.set(false);
        pollToken++;
        statusLine = status;
        onUiRefresh.run();
        onMatchFound.accept(currentMatchId, side);
    }

    private void silentReRegisterAndRetryResume(int token) {
        if (minecraft.player == null) {
            searching.set(false);
            statusLine = Component.literal("Sem jogador local.");
            onUiRefresh.run();
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String uuid = minecraft.player.getUUID().toString();
        String name = minecraft.player.getGameProfile().getName();
        ClientPlayerRegistrationStore.invalidateSession();
        GameApiClient.registerPlayer(base, uuid, name).whenComplete((sessionToken, regErr) -> minecraft.execute(() -> {
            if (token != pollToken || !searching.get()) {
                return;
            }
            if (regErr != null) {
                searching.set(false);
                statusLine = Component.literal("Re-login falhou: " + rootMsg(regErr));
                onUiRefresh.run();
                return;
            }
            ClientPlayerRegistrationStore.saveRegistered(sessionToken, minecraft.player.getUUID());
            awaitingReauthRetry = false;
            checkResumeThenQueue(token, false);
        }));
    }

    private void pollOnce(int token, boolean allowSilentReRegister) {
        if (token != pollToken || !searching.get()) {
            return;
        }
        if (System.currentTimeMillis() > queueDeadlineMs) {
            searching.set(false);
            statusLine = Component.literal("Tempo máximo na fila. Tente novamente.");
            onUiRefresh.run();
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String sessionToken = SessionManager.getSessionId();
        GameApiClient.chessMatchmaking(base, sessionToken).whenComplete((res, err) -> minecraft.execute(() -> {
            if (token != pollToken || !searching.get()) {
                return;
            }
            if (err != null) {
                if (allowSilentReRegister && !awaitingReauthRetry && isUnauthorized(err) && minecraft.player != null) {
                    awaitingReauthRetry = true;
                    silentReRegisterAndRetry(token);
                    return;
                }
                searching.set(false);
                statusLine = Component.literal("Erro: " + rootMsg(err));
                onUiRefresh.run();
                return;
            }
            if (res.queued()) {
                statusLine = Component.literal("Buscando… (na fila)");
                onUiRefresh.run();
                scheduleNextPoll(token, allowSilentReRegister);
                return;
            }
            String currentMatchId = res.matchId();
            String side = res.side() != null ? res.side() : "WHITE";
            if (currentMatchId == null || currentMatchId.isEmpty()) {
                searching.set(false);
                statusLine = Component.literal("Resposta sem matchId.");
                onUiRefresh.run();
                return;
            }
            finishMatchFound(currentMatchId, side, token, Component.literal("Partida encontrada."));
        }));
    }

    private void silentReRegisterAndRetry(int token) {
        if (minecraft.player == null) {
            searching.set(false);
            statusLine = Component.literal("Sem jogador local.");
            onUiRefresh.run();
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String uuid = minecraft.player.getUUID().toString();
        String name = minecraft.player.getGameProfile().getName();
        ClientPlayerRegistrationStore.invalidateSession();
        GameApiClient.registerPlayer(base, uuid, name).whenComplete((sessionToken, regErr) -> minecraft.execute(() -> {
            if (token != pollToken || !searching.get()) {
                return;
            }
            if (regErr != null) {
                searching.set(false);
                statusLine = Component.literal("Re-login falhou: " + rootMsg(regErr));
                onUiRefresh.run();
                return;
            }
            ClientPlayerRegistrationStore.saveRegistered(sessionToken, minecraft.player.getUUID());
            pollOnce(token, false);
        }));
    }

    private void scheduleNextPoll(int token, boolean allowReRegister) {
        CompletableFuture.delayedExecutor(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS, ForkJoinPool.commonPool())
                .execute(() -> minecraft.execute(() -> pollOnce(token, allowReRegister)));
    }

    private static boolean isUnauthorized(Throwable t) {
        Throwable c = t;
        if (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof GameApiException ge) {
            int code = ge.httpStatus();
            return code == 401 || code == 403;
        }
        return false;
    }

    private static String rootMsg(Throwable err) {
        Throwable c = err;
        if (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof GameApiException ge) {
            return ge.getMessage();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }
}
