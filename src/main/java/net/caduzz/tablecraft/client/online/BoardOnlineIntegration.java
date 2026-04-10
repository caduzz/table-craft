package net.caduzz.tablecraft.client.online;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.network.OnlineTableBindPayload;
import net.caduzz.tablecraft.network.OnlineTableClearPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.caduzz.tablecraft.online.TablePlayMode;
import net.caduzz.tablecraft.online.api.ChessApiSnapshot;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

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
            ClientPlayerRegistrationStore.setLastChessOnlineBinding(mid, side != null ? side : "WHITE");
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

    /**
     * Resume o estado na mesa deste menu e, com sessão API + matchId (mesa ou último guardado), consulta {@code /state}.
     */
    public void checkOnlineGame(Minecraft mc) {
        if (busy || mc.player == null || mc.level == null) {
            return;
        }
        String localPart = buildLocalOnlineStatus(mc);
        if (!ClientPlayerRegistrationStore.isRegistered()) {
            statusLine.set(Component.literal(localPart + "Registe-se na API para consultar o estado remoto."));
            return;
        }
        String matchId = resolveMatchIdForProbe(mc);
        if (matchId.isEmpty()) {
            statusLine.set(Component.literal(localPart + "Nenhum matchId conhecido — faça matchmaking e ligue esta mesa."));
            return;
        }
        busy = true;
        statusLine.set(Component.literal("A verificar na API…"));
        String base = TableCraftConfig.apiBaseUrl();
        String sid = ClientPlayerRegistrationStore.getSessionId();
        final String mid = matchId;
        final String local = localPart;
        GameApiClient.getChessState(base, sid, mid).whenComplete((snap, err) -> mc.execute(() -> {
            busy = false;
            if (err != null) {
                statusLine.set(Component.literal(local + "API: " + rootMsg(err)));
                return;
            }
            statusLine.set(Component.literal(local + describeApiSnapshot(mid, snap)));
        }));
    }

    @Nullable
    private ChessBlockEntity chessAt(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        if (!mc.level.getBlockState(boardPos).is(ModBlocks.CHESS_BLOCK.get())) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(boardPos);
        return be instanceof ChessBlockEntity c ? c : null;
    }

    private String buildLocalOnlineStatus(Minecraft mc) {
        ChessBlockEntity chess = chessAt(mc);
        if (chess == null) {
            return "Mesa indisponível. ";
        }
        if (chess.getTablePlayMode() != TablePlayMode.ONLINE) {
            return "Nesta mesa: modo local. ";
        }
        UUID self = mc.player.getUUID();
        if (chess.getOnlineSideFor(self) == null) {
            return "Nesta mesa: online, mas a sua conta não está ligada a esta partida. ";
        }
        if (chess.isGameInProgress()) {
            return "Nesta mesa: você participa — partida em curso. ";
        }
        return "Nesta mesa: você participa — partida terminada (aguarde reset no servidor). ";
    }

    private String resolveMatchIdForProbe(Minecraft mc) {
        ChessBlockEntity chess = chessAt(mc);
        if (chess != null && chess.getTablePlayMode() == TablePlayMode.ONLINE && mc.player != null) {
            if (chess.getOnlineSideFor(mc.player.getUUID()) != null) {
                String mid = chess.getOnlineMatchId();
                if (!mid.isEmpty()) {
                    return mid;
                }
            }
        }
        return ClientPlayerRegistrationStore.getLastChessMatchId();
    }

    private static String describeApiSnapshot(String matchId, ChessApiSnapshot snap) {
        String st = snap.statusKey() != null ? snap.statusKey() : "PLAYING";
        String human = switch (st) {
            case "PLAYING" -> "em curso";
            case "WHITE_WIN" -> "terminada (vitória das brancas)";
            case "BLACK_WIN" -> "terminada (vitória das pretas)";
            case "DRAW" -> "terminada (empate)";
            default -> "estado: " + st;
        };
        return "API (match " + matchId + "): " + human + ".";
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
