package net.caduzz.tablecraft.client.online;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;

/**
 * Garante {@code DELETE .../chess/matchmaking} quando o cliente termina sem passar por ecrãs (ex.: Alt+F4),
 * para a API não deixar a sessão na fila.
 */
public final class ChessMatchmakingApiCleanup {
    private static final AtomicBoolean SHUTDOWN_HOOK_INSTALLED = new AtomicBoolean(false);

    private ChessMatchmakingApiCleanup() {
    }

    public static void installClientShutdownHook() {
        if (!SHUTDOWN_HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(ChessMatchmakingApiCleanup::cancelQueueBlockingOnShutdown, "tablecraft-chess-mm-cancel"));
    }

    /**
     * Chamado ao desligar do servidor: assíncrono para não atrasar o fluxo vanilla.
     */
    public static void cancelQueueOnClientDisconnect() {
        if (!SessionManager.hasSession()) {
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String sid = SessionManager.getSessionId();
        GameApiClient.cancelChessMatchmaking(base, sid).whenComplete((res, ex) -> {
            if (ex != null) {
                TableCraft.LOGGER.debug("Cancelar fila (xadrez) ao sair (API): {}", rootMsg(ex));
            }
        });
        GameApiClient.cancelCheckersMatchmaking(base, sid).whenComplete((res, ex) -> {
            if (ex != null) {
                TableCraft.LOGGER.debug("Cancelar fila (damas) ao sair (API): {}", rootMsg(ex));
            }
        });
    }

    private static void cancelQueueBlockingOnShutdown() {
        if (!SessionManager.hasSession()) {
            return;
        }
        String base = TableCraftConfig.apiBaseUrl();
        String sid = SessionManager.getSessionId();
        try {
            GameApiClient.cancelChessMatchmaking(base, sid).get(4, TimeUnit.SECONDS);
        } catch (@SuppressWarnings("unused") InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            TableCraft.LOGGER.debug("Cancelar fila (xadrez) no shutdown: {}", rootMsg(e));
        }
        try {
            GameApiClient.cancelCheckersMatchmaking(base, sid).get(4, TimeUnit.SECONDS);
        } catch (@SuppressWarnings("unused") InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            TableCraft.LOGGER.debug("Cancelar fila (damas) no shutdown: {}", rootMsg(e));
        }
    }

    private static String rootMsg(Throwable err) {
        Throwable c = err;
        if (c instanceof ExecutionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof GameApiException ge) {
            return ge.getMessage();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }
}
