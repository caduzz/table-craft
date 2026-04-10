package net.caduzz.tablecraft.client.online;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.network.OnlineTableBindPayload;
import net.caduzz.tablecraft.network.OnlineTableClearPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Registo na API, fila de matchmaking e ligação automática à mesa de xadrez associada ao menu.
 */
public final class BoardOnlineIntegration {
    private static final int MATCHMAKING_POLL_MS = 2000;
    private static final long MATCHMAKING_MAX_WAIT_MS = 12L * 60L * 1000L;

    private final BlockPos boardPos;
    private final AtomicReference<Component> statusLine = new AtomicReference<>(Component.empty());
    private volatile boolean busy;
    private int matchmakingToken;

    public BoardOnlineIntegration(BlockPos boardPos) {
        this.boardPos = boardPos;
    }

    public Component getStatusLine() {
        return statusLine.get();
    }

    public boolean isBusy() {
        return busy;
    }

    public void register(Minecraft mc) {
        if (busy || mc.player == null) {
            return;
        }
        busy = true;
        statusLine.set(Component.literal("A registar…"));
        String base = TableCraftConfig.apiBaseUrl();
        String uuid = mc.player.getUUID().toString();
        String name = mc.player.getGameProfile().getName();
        GameApiClient.registerPlayer(base, uuid, name).whenComplete((sid, err) -> mc.execute(() -> {
            busy = false;
            if (err != null) {
                statusLine.set(Component.literal("Erro: " + rootMsg(err)));
                return;
            }
            ClientPlayerRegistrationStore.saveRegistered(sid, mc.player.getUUID());
            statusLine.set(Component.literal("Registado na API."));
        }));
    }

    public void startMatchmaking(Minecraft mc) {
        if (busy) {
            return;
        }
        if (!ClientPlayerRegistrationStore.isRegistered()) {
            statusLine.set(Component.literal("Registe-se primeiro na API."));
            return;
        }
        busy = true;
        matchmakingToken++;
        final int token = matchmakingToken;
        long deadlineMs = System.currentTimeMillis() + MATCHMAKING_MAX_WAIT_MS;
        statusLine.set(Component.literal("À procura de partida (xadrez)…"));
        String base = TableCraftConfig.apiBaseUrl();
        String sid = ClientPlayerRegistrationStore.getSessionId();
        runMatchmakingPoll(mc, base, sid, token, deadlineMs);
    }

    private void runMatchmakingPoll(Minecraft mc, String base, String sid, int token, long deadlineMs) {
        if (token != matchmakingToken) {
            return;
        }
        if (System.currentTimeMillis() > deadlineMs) {
            busy = false;
            statusLine.set(Component.literal("Fila: tempo máximo esgotado. Tente de novo."));
            ClientPendingOnlineMatch.clear();
            return;
        }
        GameApiClient.chessMatchmaking(base, sid).whenComplete((res, err) -> mc.execute(() -> {
            if (token != matchmakingToken) {
                return;
            }
            if (err != null) {
                busy = false;
                statusLine.set(Component.literal("Matchmaking: " + rootMsg(err)));
                ClientPendingOnlineMatch.clear();
                return;
            }
            if (res.queued()) {
                statusLine.set(Component.literal("Na fila (xadrez)…"));
                scheduleNextMatchmakingPoll(mc, () -> runMatchmakingPoll(mc, base, sid, token, deadlineMs));
                return;
            }
            String mid = res.matchId();
            String side = res.side();
            if (mid == null || mid.isEmpty()) {
                busy = false;
                statusLine.set(Component.literal("Matchmaking: resposta sem matchId."));
                ClientPendingOnlineMatch.clear();
                return;
            }
            busy = false;
            int sideOrd = "BLACK".equalsIgnoreCase(side) ? 1 : 0;
            TableCraftNetworking.sendOnlineBind(
                    new OnlineTableBindPayload(boardPos, OnlineTableBindPayload.GAME_CHESS, sid, mid, sideOrd));
            ClientPendingOnlineMatch.clear();
            statusLine.set(Component.literal("Partida ligada a esta mesa. " + mid + " | Lado: " + (side != null ? side : "WHITE")));
        }));
    }

    private static void scheduleNextMatchmakingPoll(Minecraft mc, Runnable onClientThread) {
        CompletableFuture.delayedExecutor(MATCHMAKING_POLL_MS, TimeUnit.MILLISECONDS, ForkJoinPool.commonPool()).execute(() -> mc.execute(onClientThread));
    }

    public void clearOnlineOnThisBoard(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        TableCraftNetworking.sendOnlineClear(new OnlineTableClearPayload(boardPos, OnlineTableBindPayload.GAME_CHESS));
        ClientPendingOnlineMatch.clear();
        matchmakingToken++;
        busy = false;
        statusLine.set(Component.literal("Pedido para desligar online desta mesa enviado."));
    }

    /** Chamar ao fechar o menu para cancelar polls pendentes. */
    public void cancelMatchmakingIfLeaving() {
        matchmakingToken++;
        busy = false;
    }

    private static String rootMsg(Throwable err) {
        Throwable c = err;
        if (c instanceof java.util.concurrent.CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof GameApiException ge) {
            return ge.getMessage();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }
}
