package net.caduzz.tablecraft.client.online;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.client.TableSettingsPanelButton;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.network.OnlineTableBindPayload;
import net.caduzz.tablecraft.network.OnlineTableClearPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.phys.HitResult;

/**
 * Legado: o fluxo online está no menu de cada mesa ({@code Jogar online (fila API)}).
 */
@Deprecated
public class OnlinePlayMenuScreen extends Screen {
    /** Intervalo entre POSTs ao mesmo endpoint de matchmaking (poll). */
    private static final int MATCHMAKING_POLL_MS = 2000;
    /** Limite do cliente; alinhar com {@code MATCHMAKING_QUEUE_TTL_MINUTES} na API (+ margem). */
    private static final long MATCHMAKING_MAX_WAIT_MS = 12L * 60L * 1000L;

    private static final int PANEL_W = 260;
    /** Altura reservada no topo: título + várias linhas de mensagem da API antes do 1.º botão. */
    private static final int HEADER_H = 66;
    private static final int PANEL_H = 248;
    private Component statusLine = Component.literal("");
    private volatile boolean busy;
    /** Incrementado a cada novo pedido de matchmaking para ignorar polls antigos. */
    private int matchmakingToken;

    public OnlinePlayMenuScreen() {
        super(Component.literal("TableCraft — Online"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PANEL_W / 2;
        int panelTop = this.height / 2 - PANEL_H / 2;
        int y = panelTop + HEADER_H;
        int gap = 24;
        int bw = PANEL_W - 20;
        int bx = left + 10;

        addRenderableWidget(new TableSettingsPanelButton(bx, y, bw, 20, Component.literal("Registrar jogador na API"), this::onRegister,
                TableSettingsPanelButton.Style.ROW));
        y += gap;
        addRenderableWidget(new TableSettingsPanelButton(bx, y, bw, 20, Component.literal("Buscar partida (xadrez)"), this::onFindChess,
                TableSettingsPanelButton.Style.ROW));
        y += gap;
        addRenderableWidget(new TableSettingsPanelButton(bx, y, bw, 20, Component.literal("Ligar mesa ao bloco visado"), this::onBindLooked,
                TableSettingsPanelButton.Style.PRIMARY));
        y += gap;
        addRenderableWidget(new TableSettingsPanelButton(bx, y, bw, 20, Component.literal("Desligar online na mesa visada"), this::onClearLooked,
                TableSettingsPanelButton.Style.DANGER));
        y += gap + 8;
        addRenderableWidget(new TableSettingsPanelButton(this.width / 2 - 60, y, 120, 20, Component.literal("Fechar"), this::onClose,
                TableSettingsPanelButton.Style.COMPACT));
    }

    private void onRegister() {
        if (busy) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        busy = true;
        statusLine = Component.literal("A registar…");
        String base = TableCraftConfig.apiBaseUrl();
        String uuid = mc.player.getUUID().toString();
        String name = mc.player.getGameProfile().getName();
        GameApiClient.registerPlayer(base, uuid, name).whenComplete((sid, err) -> mc.execute(() -> {
            busy = false;
            if (err != null) {
                statusLine = Component.literal("Erro: " + rootMsg(err));
                return;
            }
            ClientPlayerRegistrationStore.saveRegistered(sid, mc.player.getUUID());
            statusLine = Component.literal("Registado. sessionId guardado localmente.");
        }));
    }

    private void onFindChess() {
        findMatch();
    }

    private void findMatch() {
        if (busy) {
            return;
        }
        if (!ClientPlayerRegistrationStore.isRegistered()) {
            statusLine = Component.literal("Registe-se primeiro na API.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        busy = true;
        matchmakingToken++;
        final int token = matchmakingToken;
        long deadlineMs = System.currentTimeMillis() + MATCHMAKING_MAX_WAIT_MS;
        statusLine = Component.literal("À procura de partida (xadrez)…");
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
            statusLine = Component.literal("Fila: tempo máximo esgotado. Tente de novo.");
            ClientPendingOnlineMatch.clear();
            return;
        }
        GameApiClient.chessMatchmaking(base, sid).whenComplete((res, err) -> mc.execute(() -> {
            if (token != matchmakingToken) {
                return;
            }
            if (err != null) {
                busy = false;
                statusLine = Component.literal("Matchmaking: " + rootMsg(err));
                ClientPendingOnlineMatch.clear();
                return;
            }
            if (res.queued()) {
                statusLine = Component.literal("Na fila (xadrez)… à procura de oponente com rating semelhante.");
                scheduleNextMatchmakingPoll(mc,
                        () -> runMatchmakingPoll(mc, base, sid, token, deadlineMs));
                return;
            }
            String mid = res.matchId();
            String side = res.side();
            if (mid == null || mid.isEmpty()) {
                busy = false;
                statusLine = Component.literal("Matchmaking: resposta sem matchId.");
                ClientPendingOnlineMatch.clear();
                return;
            }
            busy = false;
            ClientPendingOnlineMatch.setPending(OnlineTableBindPayload.GAME_CHESS, sid, mid, side != null ? side : "WHITE");
            statusLine = Component.literal("Partida: " + mid + " | Lado: " + side + " — use «Ligar mesa».");
        }));
    }

    private static void scheduleNextMatchmakingPoll(Minecraft mc, Runnable onClientThread) {
        CompletableFuture.delayedExecutor(MATCHMAKING_POLL_MS, TimeUnit.MILLISECONDS, ForkJoinPool.commonPool())
                .execute(() -> mc.execute(onClientThread));
    }

    private void onBindLooked() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientPendingOnlineMatch.hasPending()) {
            statusLine = Component.literal("Nenhuma partida pendente. Faça matchmaking primeiro.");
            return;
        }
        BlockPos pos = rayBlock(mc);
        if (pos == null) {
            statusLine = Component.literal("Aponte para um tabuleiro de xadrez.");
            return;
        }
        if (!mc.level.getBlockState(pos).is(ModBlocks.CHESS_BLOCK.get())) {
            statusLine = Component.literal("O bloco visado não é xadrez.");
            return;
        }
        TableCraftNetworking.sendOnlineBind(new OnlineTableBindPayload(pos, OnlineTableBindPayload.GAME_CHESS,
                ClientPendingOnlineMatch.getSessionId(), ClientPendingOnlineMatch.getMatchId(), ClientPendingOnlineMatch.getSideOrdinal()));
        statusLine = Component.literal("Pedido de ligação enviado ao servidor.");
    }

    private void onClearLooked() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = rayBlock(mc);
        if (pos == null) {
            statusLine = Component.literal("Aponte para uma mesa.");
            return;
        }
        if (!mc.level.getBlockState(pos).is(ModBlocks.CHESS_BLOCK.get())) {
            statusLine = Component.literal("Bloco não é mesa de xadrez TableCraft.");
            return;
        }
        TableCraftNetworking.sendOnlineClear(new OnlineTableClearPayload(pos, OnlineTableBindPayload.GAME_CHESS));
        ClientPendingOnlineMatch.clear();
        statusLine = Component.literal("Pedido de desligar enviado.");
    }

    @Nullable
    private static BlockPos rayBlock(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK || mc.level == null) {
            return null;
        }
        return bhr.getBlockPos();
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

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int left = this.width / 2 - PANEL_W / 2 - 8;
        int top = this.height / 2 - PANEL_H / 2;
        g.fill(left, top, left + PANEL_W + 16, top + PANEL_H, 0xD0101018);
        g.renderOutline(left, top, PANEL_W + 16, PANEL_H, 0xFF5c6b7a);
        g.drawString(this.font, this.title, left + 10, top + 8, 0xFFFFFF, false);
        if (busy) {
            g.drawString(this.font, Component.literal("…"), left + PANEL_W - 4, top + 8, 0xFFFFAA, false);
        }
        int textMaxW = PANEL_W - 4;
        int sy = top + 26;
        for (FormattedCharSequence line : this.font.split(statusLine, textMaxW)) {
            g.drawString(this.font, line, left + 10, sy, 0xCCCCCC, false);
            sy += this.font.lineHeight;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
