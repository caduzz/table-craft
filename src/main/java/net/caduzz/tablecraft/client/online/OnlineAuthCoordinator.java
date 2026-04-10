package net.caduzz.tablecraft.client.online;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.caduzz.tablecraft.online.api.PlayerProfileDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Autenticação assíncrona ao entrar no mundo: registo se necessário e validação {@code GET /me} para cache de rating.
 */
public final class OnlineAuthCoordinator {
    private static final long MIN_INTERVAL_MS = 8000L;
    private static volatile long lastWorldAuthEpochMs;
    private static final AtomicBoolean authRunning = new AtomicBoolean(false);

    private OnlineAuthCoordinator() {
    }

    /** Chamado no thread do cliente (payload S2C). */
    public static void onWorldLoginReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWorldAuthEpochMs < MIN_INTERVAL_MS) {
            return;
        }
        if (!authRunning.compareAndSet(false, true)) {
            return;
        }
        lastWorldAuthEpochMs = now;
        String base = TableCraftConfig.apiBaseUrl();
        Player p = mc.player;
        runAuthChain(mc, base, p).whenComplete((v, err) -> {
            authRunning.set(false);
            if (err != null) {
                TableCraft.LOGGER.debug("TableCraft API auth: {}", rootMsg(err));
            }
        });
    }

    private static CompletableFuture<Void> runAuthChain(Minecraft mc, String base, Player player) {
        if (SessionManager.hasSession()) {
            String sessionToken = SessionManager.getSessionId();
            return GameApiClient.getMyProfile(base, sessionToken).thenAccept(prof -> mc.execute(() -> OnlineProfileCache.updateFromProfile(prof)))
                    .exceptionallyCompose(ex -> {
                        if (isUnauthorized(ex)) {
                            ClientPlayerRegistrationStore.invalidateSession();
                            return registerThenMe(mc, base, player, true);
                        }
                        return CompletableFuture.failedFuture(unpack(ex));
                    });
        }
        return registerThenMe(mc, base, player, false);
    }

    private static CompletableFuture<Void> registerThenMe(Minecraft mc, String base, Player player, boolean afterInvalidSession) {
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        return GameApiClient.registerPlayer(base, uuid, name).thenCompose(sessionToken -> {
            ClientPlayerRegistrationStore.saveRegistered(sessionToken, player.getUUID());
            return GameApiClient.getMyProfile(base, sessionToken);
        }).thenAccept(prof -> mc.execute(() -> {
            OnlineProfileCache.updateFromProfile(prof);
            if (afterInvalidSession && mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("gui.tablecraft.auth.relogged"), false);
            }
        }));
    }

    private static boolean isUnauthorized(Throwable t) {
        for (Throwable c = unpack(t); c != null; c = c.getCause()) {
            if (c instanceof GameApiException ge) {
                int code = ge.httpStatus();
                if (code == 401 || code == 403) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Throwable unpack(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static String rootMsg(Throwable err) {
        Throwable c = unpack(err);
        if (c instanceof GameApiException ge) {
            return ge.getMessage();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }

    public static CompletableFuture<PlayerProfileDTO> refreshProfileAsync(Minecraft mc) {
        if (mc.player == null || !SessionManager.hasSession()) {
            return CompletableFuture.completedFuture(null);
        }
        String base = TableCraftConfig.apiBaseUrl();
        String sessionToken = SessionManager.getSessionId();
        return GameApiClient.getMyProfile(base, sessionToken).whenComplete((prof, err) -> {
            if (prof != null) {
                mc.execute(() -> OnlineProfileCache.updateFromProfile(prof));
            }
        });
    }
}
