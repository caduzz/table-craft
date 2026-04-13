package net.caduzz.tablecraft.block.entity;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector2d;
import net.caduzz.tablecraft.block.ChessBlock;
import net.caduzz.tablecraft.block.ChessMoveLogic;
import net.caduzz.tablecraft.client.online.ClientOnlineChessAfterMatch;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.caduzz.tablecraft.online.OnlineSide;
import net.caduzz.tablecraft.online.TablePlayMode;
import net.caduzz.tablecraft.online.api.ChessApiSnapshot;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ChessBlockEntity extends BlockEntity {
    public static final long GAME_END_RESET_DELAY_TICKS = 100L;
    /** Duração da animação de movimento no cliente (mesma ideia das damas). */
    public static final float MOVE_DURATION_TICKS = 14.0f;


    public enum ChessGameStatus {
        PLAYING,
        WHITE_WIN,
        BLACK_WIN,
        DRAW
    }

    public enum Piece {
        EMPTY,
        WHITE_PAWN,
        WHITE_ROOK,
        WHITE_KNIGHT,
        WHITE_BISHOP,
        WHITE_QUEEN,
        WHITE_KING,
        BLACK_PAWN,
        BLACK_ROOK,
        BLACK_KNIGHT,
        BLACK_BISHOP,
        BLACK_QUEEN,
        BLACK_KING
    }

    private final Piece[][] board = new Piece[8][8];
    private boolean whiteTurn = true;
    private int selRow = -1;
    private int selCol = -1;
    private int validMoveCount;
    private final int[] validMovesBuf = new int[64];
    private int captureMoveCount;
    private final int[] captureMovesBuf = new int[64];
    private int blockedByCheckMoveCount;
    private final int[] blockedByCheckMovesBuf = new int[64];
    private ChessGameStatus gameStatus = ChessGameStatus.PLAYING;
    private long resetAtGameTime = -1L;
    private UUID gameSeatWhiteUuid;
    private String gameSeatWhiteName = "";
    private UUID gameSeatBlackUuid;
    private String gameSeatBlackName = "";
    private String lastWinnerDisplayName = "";
    private int whiteClockTicks = BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS;
    private int blackClockTicks = BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS;
    private int playerTimePresetIndex = BoardGameClockConfig.closestPresetIndexFromTicks(BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS);
    /** Realce das casas do último movimento (sincronizado; todos veem o mesmo). */
    private boolean showPreviousMove = true;
    /** Indicadores de jogadas legais / captura / bloqueio por xeque no tabuleiro. */
    private boolean showLegalMoveHints = true;

    private TablePlayMode tablePlayMode = TablePlayMode.LOCAL;
    /** Fallback / NBT antigo (uma única sessão). */
    private String onlineSessionId = "";
    private String onlineSessionWhite = "";
    private String onlineSessionBlack = "";
    private String onlineMatchId = "";
    private OnlineSide onlineSide = OnlineSide.WHITE;
    @Nullable
    private UUID onlineBoundPlayerWhite;
    @Nullable
    private UUID onlineBoundPlayerBlack;
    /** Mundos antigos: um jogador + um lado. */
    @Nullable
    private UUID onlineOwnerUuid;
    private boolean onlineMoveInFlight;
    private long onlineLastPollTick;
    private int onlineCapturedWhite;
    private int onlineCapturedBlack;
    /** Nomes na HUD (modo online): jogador(es) no Minecraft + opcionalmente campos da API no estado. */
    private String onlineHudWhiteName = "";
    private String onlineHudBlackName = "";
    /** Snapshot da API a aplicar quando a animação online terminar. */
    @Nullable
    private ChessApiSnapshot onlinePendingSnapshot;
    /** Sessão usada no GET/POST que produziu {@link #onlinePendingSnapshot} (para mapear player/opponent → brancas/pretas). */
    @Nullable
    private String onlinePendingSnapshotSessionId;

    /** Origem/destino vazios no tabuleiro até o fim da animação; a peça é desenhada só no cliente interpolando. */
    private int animFromRow;
    private int animFromCol;
    private int animToRow;
    private int animToCol;
    private Piece animPiece = Piece.EMPTY;
    private long animStartGameTime = -1L;
    private int lastMoveFromRow = -1;
    private int lastMoveFromCol = -1;
    private int lastMoveToRow = -1;
    private int lastMoveToCol = -1;

    public ChessBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHESS.get(), pos, state);
        resetToStartingPosition();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChessBlockEntity be) {
        if (!level.isClientSide()) {
            be.serverTick();
        }
    }

    private void serverTick() {
        if (level == null) {
            return;
        }
        if (tablePlayMode == TablePlayMode.ONLINE) {
            serverTickOnline();
            return;
        }
        tickGameClock();
        if (hasActiveAnimation()) {
            if (level.getGameTime() >= animStartGameTime + (long) MOVE_DURATION_TICKS) {
                finishAnimatedMove();
            }
            return;
        }
        if (gameStatus != ChessGameStatus.PLAYING && resetAtGameTime >= 0L && level.getGameTime() >= resetAtGameTime) {
            resetToStartingPosition();
            setChanged();
            syncToClients();
        }
    }

    private void serverTickOnline() {
        if (hasActiveAnimation() && onlinePendingSnapshot != null && level != null) {
            if (level.getGameTime() >= animStartGameTime + (long) MOVE_DURATION_TICKS) {
                finishOnlineAnimatedMove();
            }
        } else if (hasActiveAnimation()) {
            // Animação órfã (ex.: mudança local antes de ligar online): descartar.
            clearAnimation();
            setChanged();
            syncToClients();
        }
        tickOnlinePoll();
        if (gameStatus != ChessGameStatus.PLAYING && resetAtGameTime >= 0L && level.getGameTime() >= resetAtGameTime) {
            clearOnlineSession(null);
            resetToStartingPosition();
            setChanged();
            syncToClients();
        }
    }

    private void tickOnlinePoll() {
        if (level == null || onlineMatchId.isEmpty()) {
            return;
        }
        // Evita polls que reatribuem resetAtGameTime e adiam o auto-reset indefinidamente.
        if (gameStatus != ChessGameStatus.PLAYING && resetAtGameTime >= 0L) {
            return;
        }
        String pollSid = pollSessionId();
        if (pollSid.isEmpty()) {
            return;
        }
        if (onlineMoveInFlight) {
            return;
        }
        if (hasActiveAnimation() && onlinePendingSnapshot != null) {
            return;
        }
        long tick = level.getGameTime();
        if (tick - onlineLastPollTick < TableCraftConfig.onlinePollTicks()) {
            return;
        }
        onlineLastPollTick = tick;
        String base = TableCraftConfig.apiBaseUrl();
        GameApiClient.getChessState(base, pollSid, onlineMatchId).whenComplete((snap, err) -> runOnServer(() -> {
            if (err != null) {
                Throwable c = rootCause(err);
                if (c instanceof GameApiException ge && (ge.httpStatus() == 401 || ge.httpStatus() == 403)) {
                    clearOnlineSession(null);
                    setChanged();
                    syncToClients();
                }
                return;
            }
            applyChessApiSnapshot(snap, pollSid);
            setChanged();
            syncToClients();
        }));
    }

    private void runOnServer(Runnable r) {
        if (level != null && level.getServer() != null) {
            level.getServer().execute(r);
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        if (c instanceof java.util.concurrent.CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }

    public void bindOnlineMatch(ServerPlayer player, String sessionId, String matchId, OnlineSide side) {
        if (level == null || level.isClientSide()) {
            return;
        }
        String sid = sessionId != null ? sessionId : "";
        if (tablePlayMode == TablePlayMode.ONLINE && !onlineMatchId.isEmpty() && !onlineMatchId.equals(matchId)) {
            onlineSessionWhite = "";
            onlineSessionBlack = "";
            onlineBoundPlayerWhite = null;
            onlineBoundPlayerBlack = null;
            onlineSessionId = "";
            onlineOwnerUuid = null;
            onlineHudWhiteName = "";
            onlineHudBlackName = "";
        }
        this.onlineMatchId = matchId != null ? matchId : "";
        this.onlineSide = side;
        this.onlineOwnerUuid = null;
        String profileName = player.getGameProfile().getName();
        if (side == OnlineSide.WHITE) {
            this.onlineSessionWhite = sid;
            this.onlineBoundPlayerWhite = player.getUUID();
            this.onlineHudWhiteName = profileName != null ? profileName : "";
        } else {
            this.onlineSessionBlack = sid;
            this.onlineBoundPlayerBlack = player.getUUID();
            this.onlineHudBlackName = profileName != null ? profileName : "";
        }
        this.onlineSessionId = coalesceSessions(onlineSessionWhite, onlineSessionBlack, onlineSessionId);
        this.tablePlayMode = TablePlayMode.ONLINE;
        this.onlineMoveInFlight = false;
        this.onlineLastPollTick = level.getGameTime() - TableCraftConfig.onlinePollTicks();
        clearSelection();
        clearAnimation();
        onlinePendingSnapshot = null;
        onlinePendingSnapshotSessionId = null;
        String base = TableCraftConfig.apiBaseUrl();
        String pollSid = pollSessionId();
        GameApiClient.getChessState(base, pollSid, onlineMatchId).whenComplete((snap, err) -> runOnServer(() -> {
            if (err == null) {
                applyChessApiSnapshot(snap, pollSid);
            }
            setChanged();
            syncToClients();
        }));
        player.displayClientMessage(Component.literal("Mesa ligada ao modo online."), false);
        setChanged();
        syncToClients();
    }

    public void clearOnlineSession(@Nullable Player notify) {
        tablePlayMode = TablePlayMode.LOCAL;
        onlineSessionId = "";
        onlineSessionWhite = "";
        onlineSessionBlack = "";
        onlineMatchId = "";
        onlineBoundPlayerWhite = null;
        onlineBoundPlayerBlack = null;
        onlineOwnerUuid = null;
        onlineMoveInFlight = false;
        onlinePendingSnapshot = null;
        onlinePendingSnapshotSessionId = null;
        onlineCapturedWhite = 0;
        onlineCapturedBlack = 0;
        onlineHudWhiteName = "";
        onlineHudBlackName = "";
        if (notify != null) {
            notify.displayClientMessage(Component.literal("Sessão online encerrada na mesa."), true);
        }
        setChanged();
        syncToClients();
    }

    private void applyChessApiSnapshot(ChessApiSnapshot s, @Nullable String sessionUsedForRequest) {
        if (tablePlayMode == TablePlayMode.ONLINE && tryBeginOnlineMoveAnimation(s, sessionUsedForRequest)) {
            return;
        }
        applyChessApiSnapshotDirect(s, sessionUsedForRequest);
    }

    /**
     * Inicia a mesma animação que {@link #tryMove} para um lance vindo da API (outro jogador ou confirmação do POST).
     */
    private boolean tryBeginOnlineMoveAnimation(ChessApiSnapshot s, @Nullable String sessionUsedForRequest) {
        if (level == null || gameStatus != ChessGameStatus.PLAYING) {
            return false;
        }
        if (s.lastFromRow() == null || s.lastFromCol() == null || s.lastToRow() == null || s.lastToCol() == null) {
            return false;
        }
        int fr = s.lastFromRow();
        int fc = s.lastFromCol();
        int tr = s.lastToRow();
        int tc = s.lastToCol();
        if (hasLastMoveMarker() && fr == lastMoveFromRow && fc == lastMoveFromCol && tr == lastMoveToRow && tc == lastMoveToCol) {
            return false;
        }
        if (hasActiveAnimation()) {
            return false;
        }
        Piece moving = board[fr][fc];
        if (moving == Piece.EMPTY) {
            return false;
        }
        board[fr][fc] = Piece.EMPTY;
        board[tr][tc] = Piece.EMPTY;
        animFromRow = fr;
        animFromCol = fc;
        animToRow = tr;
        animToCol = tc;
        animPiece = moving;
        animStartGameTime = level.getGameTime();
        onlinePendingSnapshot = s;
        onlinePendingSnapshotSessionId = sessionUsedForRequest;
        return true;
    }

    private void finishOnlineAnimatedMove() {
        ChessApiSnapshot s = onlinePendingSnapshot;
        String sess = onlinePendingSnapshotSessionId;
        onlinePendingSnapshot = null;
        onlinePendingSnapshotSessionId = null;
        clearAnimation();
        if (s != null) {
            applyChessApiSnapshotDirect(s, sess);
        }
        setChanged();
        syncToClients();
    }

    private void applyChessApiSnapshotDirect(ChessApiSnapshot s, @Nullable String sessionUsedForRequest) {
        Piece[] values = Piece.values();
        for (int i = 0; i < 64; i++) {
            int ord = s.boardOrdinals()[i];
            board[i / 8][i % 8] = ord >= 0 && ord < values.length ? values[ord] : Piece.EMPTY;
        }
        whiteTurn = s.whiteTurn();
        onlineCapturedWhite = s.capturedWhite();
        onlineCapturedBlack = s.capturedBlack();
        if (tablePlayMode == TablePlayMode.ONLINE) {
            if (s.whiteDisplayName() != null && !s.whiteDisplayName().isBlank()) {
                onlineHudWhiteName = s.whiteDisplayName().trim();
            }
            if (s.blackDisplayName() != null && !s.blackDisplayName().isBlank()) {
                onlineHudBlackName = s.blackDisplayName().trim();
            }
            // Sobrepõe quando a API envia player/opponent na perspectiva da sessão do pedido.
            applySessionRelativeHudFromSnapshot(s, sessionUsedForRequest);
        }
        String key = s.statusKey();
        if ("WHITE_WIN".equalsIgnoreCase(key)) {
            if (gameStatus != ChessGameStatus.WHITE_WIN) {
                gameStatus = ChessGameStatus.WHITE_WIN;
                resetAtGameTime = level != null ? level.getGameTime() + GAME_END_RESET_DELAY_TICKS : -1L;
            }
        } else if ("BLACK_WIN".equalsIgnoreCase(key)) {
            if (gameStatus != ChessGameStatus.BLACK_WIN) {
                gameStatus = ChessGameStatus.BLACK_WIN;
                resetAtGameTime = level != null ? level.getGameTime() + GAME_END_RESET_DELAY_TICKS : -1L;
            }
        } else if ("DRAW".equalsIgnoreCase(key)) {
            if (gameStatus != ChessGameStatus.DRAW) {
                gameStatus = ChessGameStatus.DRAW;
                resetAtGameTime = level != null ? level.getGameTime() + GAME_END_RESET_DELAY_TICKS : -1L;
                lastWinnerDisplayName = "Empate";
            }
        } else {
            gameStatus = ChessGameStatus.PLAYING;
            resetAtGameTime = -1L;
        }
        if (s.lastFromRow() != null && s.lastFromCol() != null && s.lastToRow() != null && s.lastToCol() != null) {
            setLastMoveMarker(s.lastFromRow(), s.lastFromCol(), s.lastToRow(), s.lastToCol());
        } else {
            clearLastMoveMarker();
        }
        clearAnimation();
        // API pode atrasar ou omitir status; alinhar com as regras do mod (xeque-mate / afogamento).
        maybeFinishChessIfNoLegalMoves();
    }

    /**
     * Preenche {@link #onlineHudWhiteName} / {@link #onlineHudBlackName} a partir de
     * {@code playerDisplayName} / {@code opponentDisplayName} da API, relativos à sessão do pedido HTTP.
     */
    private void applySessionRelativeHudFromSnapshot(ChessApiSnapshot s, @Nullable String sessionUsedForRequest) {
        if (tablePlayMode != TablePlayMode.ONLINE || sessionUsedForRequest == null || sessionUsedForRequest.isEmpty()) {
            return;
        }
        String p = s.playerDisplayName() != null ? s.playerDisplayName().trim() : "";
        String o = s.opponentDisplayName() != null ? s.opponentDisplayName().trim() : "";
        if (p.isEmpty() && o.isEmpty()) {
            return;
        }
        if (!onlineSessionWhite.isEmpty() && sessionUsedForRequest.equals(onlineSessionWhite)) {
            if (!p.isEmpty()) {
                onlineHudWhiteName = p;
            }
            if (!o.isEmpty()) {
                onlineHudBlackName = o;
            }
            return;
        }
        if (!onlineSessionBlack.isEmpty() && sessionUsedForRequest.equals(onlineSessionBlack)) {
            if (!p.isEmpty()) {
                onlineHudBlackName = p;
            }
            if (!o.isEmpty()) {
                onlineHudWhiteName = o;
            }
            return;
        }
        if (!onlineSessionId.isEmpty() && sessionUsedForRequest.equals(onlineSessionId)) {
            if (onlineSide == OnlineSide.WHITE) {
                if (!p.isEmpty()) {
                    onlineHudWhiteName = p;
                }
                if (!o.isEmpty()) {
                    onlineHudBlackName = o;
                }
            } else {
                if (!p.isEmpty()) {
                    onlineHudBlackName = p;
                }
                if (!o.isEmpty()) {
                    onlineHudWhiteName = o;
                }
            }
        }
    }

    /**
     * Termina a partida se o lado que joga não tem lances legais (xeque-mate ou afogamento), ou se falta um rei.
     */
    private void maybeFinishChessIfNoLegalMoves() {
        if (gameStatus != ChessGameStatus.PLAYING || level == null) {
            return;
        }
        if (!ChessMoveLogic.hasKing(board, true)) {
            endGame(ChessGameStatus.BLACK_WIN, Component.literal("Xeque-mate! Pretas venceram no xadrez."));
            return;
        }
        if (!ChessMoveLogic.hasKing(board, false)) {
            endGame(ChessGameStatus.WHITE_WIN, Component.literal("Xeque-mate! Brancas venceram no xadrez."));
            return;
        }
        if (!ChessMoveLogic.currentPlayerHasAnyMove(board, whiteTurn)) {
            boolean inCheck = ChessMoveLogic.isKingInCheck(board, whiteTurn);
            if (inCheck) {
                endGame(whiteTurn ? ChessGameStatus.BLACK_WIN : ChessGameStatus.WHITE_WIN,
                        Component.literal(whiteTurn ? "Xeque-mate! Pretas venceram no xadrez." : "Xeque-mate! Brancas venceram no xadrez."));
            } else {
                endGame(whiteTurn ? ChessGameStatus.BLACK_WIN : ChessGameStatus.WHITE_WIN,
                        Component.literal("Sem jogadas legais (afogamento)."));
            }
        }
    }

    private void submitOnlineChessMove(Player player, int fr, int fc, int tr, int tc) {
        if (level == null || level.isClientSide() || onlineMatchId.isEmpty()) {
            return;
        }
        String moveSid = sessionIdForMove(whiteTurn);
        if (moveSid.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("Online: falta ligar esta mesa com a sessão API do lado que joga (brancas ou pretas)."), true);
            return;
        }
        if (onlineMoveInFlight) {
            return;
        }
        if (!ChessMoveLogic.isLegalMove(board, fr, fc, tr, tc, whiteTurn)) {
            player.displayClientMessage(Component.literal("Movimento inválido para essa peça."), true);
            return;
        }
        onlineMoveInFlight = true;
        setChanged();
        syncToClients();
        String base = TableCraftConfig.apiBaseUrl();
        GameApiClient.postChessMove(base, moveSid, onlineMatchId, fr, fc, tr, tc).whenComplete((snap, err) -> runOnServer(() -> {
            onlineMoveInFlight = false;
            if (err != null) {
                Throwable c = rootCause(err);
                String msg = c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
                player.displayClientMessage(Component.literal("Online: " + msg), true);
                if (c instanceof GameApiException ge && (ge.httpStatus() == 401 || ge.httpStatus() == 403)) {
                    clearOnlineSession(player);
                }
                setChanged();
                syncToClients();
                return;
            }
            applyChessApiSnapshot(snap, moveSid);
            clearSelection();
            player.displayClientMessage(Component.literal("Lance confirmado pela API."), true);
            setChanged();
            syncToClients();
        }));
    }

    public TablePlayMode getTablePlayMode() {
        return tablePlayMode;
    }

    /** ID da partida na API (cliente sincronizado); vazio se não estiver em modo online. */
    public String getOnlineMatchId() {
        return onlineMatchId == null ? "" : onlineMatchId;
    }

    public OnlineSide getOnlineSide() {
        return onlineSide;
    }

    /** Lado API deste jogador na mesa, se tiver ligado esta mesa (brancas ou pretas). */
    @Nullable
    public OnlineSide getOnlineSideFor(UUID playerId) {
        if (onlineBoundPlayerWhite != null && onlineBoundPlayerWhite.equals(playerId)) {
            return OnlineSide.WHITE;
        }
        if (onlineBoundPlayerBlack != null && onlineBoundPlayerBlack.equals(playerId)) {
            return OnlineSide.BLACK;
        }
        if (onlineOwnerUuid != null && onlineOwnerUuid.equals(playerId)) {
            return onlineSide;
        }
        return null;
    }

    private static String coalesceSessions(String a, String b, String legacy) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        return legacy != null ? legacy : "";
    }

    private String pollSessionId() {
        return coalesceSessions(onlineSessionWhite, onlineSessionBlack, onlineSessionId);
    }

    private String sessionIdForMove(boolean whiteToMove) {
        if (whiteToMove) {
            if (!onlineSessionWhite.isEmpty()) {
                return onlineSessionWhite;
            }
        } else if (!onlineSessionBlack.isEmpty()) {
            return onlineSessionBlack;
        }
        return onlineSessionId;
    }

    public boolean isOnlineMovePending() {
        return onlineMoveInFlight;
    }

    public int getOnlineCapturedWhiteCount() {
        return onlineCapturedWhite;
    }

    public int getOnlineCapturedBlackCount() {
        return onlineCapturedBlack;
    }

    public void resetToStartingPosition() {
        for (Piece[] row : board) {
            Arrays.fill(row, Piece.EMPTY);
        }
        // Colunas a–h = 0–7. D e E iguais para pretas e brancas; só o rank (linha) muda.
        final int fileD = 3;
        final int fileE = 4;
        final int rankBlack = 0; // rank 8
        final int rankWhite = 7; // rank 1 (fundo das brancas neste mod)
        board[rankBlack][0] = Piece.BLACK_ROOK;
        board[rankBlack][1] = Piece.BLACK_KNIGHT;
        board[rankBlack][2] = Piece.BLACK_BISHOP;
        board[rankBlack][fileD] = Piece.BLACK_QUEEN;
        board[rankBlack][fileE] = Piece.BLACK_KING;
        board[rankBlack][5] = Piece.BLACK_BISHOP;
        board[rankBlack][6] = Piece.BLACK_KNIGHT;
        board[rankBlack][7] = Piece.BLACK_ROOK;
        Arrays.fill(board[1], Piece.BLACK_PAWN);
        Arrays.fill(board[6], Piece.WHITE_PAWN);
        board[rankWhite][0] = Piece.WHITE_ROOK;
        board[rankWhite][1] = Piece.WHITE_KNIGHT;
        board[rankWhite][2] = Piece.WHITE_BISHOP;
        board[rankWhite][fileD] = Piece.WHITE_QUEEN;
        board[rankWhite][fileE] = Piece.WHITE_KING;
        board[rankWhite][5] = Piece.WHITE_BISHOP;
        board[rankWhite][6] = Piece.WHITE_KNIGHT;
        board[rankWhite][7] = Piece.WHITE_ROOK;
        whiteTurn = true;
        selRow = -1;
        selCol = -1;
        validMoveCount = 0;
        gameStatus = ChessGameStatus.PLAYING;
        resetAtGameTime = -1L;
        clearLastMoveMarker();
        gameSeatWhiteUuid = null;
        gameSeatWhiteName = "";
        gameSeatBlackUuid = null;
        gameSeatBlackName = "";
        lastWinnerDisplayName = "";
        resetGameClock();
        clearAnimation();
    }

    private void clearAnimation() {
        animPiece = Piece.EMPTY;
        animStartGameTime = -1L;
    }

    public boolean hasActiveAnimation() {
        return animStartGameTime >= 0L && animPiece != Piece.EMPTY;
    }

    public int getAnimFromRow() {
        return animFromRow;
    }

    public int getAnimFromCol() {
        return animFromCol;
    }

    public int getAnimToRow() {
        return animToRow;
    }

    public int getAnimToCol() {
        return animToCol;
    }

    public Piece getAnimPiece() {
        return animPiece;
    }

    /**
     * Progresso 0–1 do movimento animado (usa {@code partialTick} no cliente).
     */
    public float getMoveProgress(float partialTick) {
        if (level == null || animStartGameTime < 0L || animPiece == Piece.EMPTY) {
            return 0f;
        }
        float age = level.getGameTime() - animStartGameTime + partialTick;
        return Mth.clamp(age / MOVE_DURATION_TICKS, 0f, 1f);
    }

    public void handlePlayerClick(Player player, BlockHitResult hit) {
        if (gameStatus != ChessGameStatus.PLAYING) {
            player.displayClientMessage(Component.literal("Partida encerrada. Aguarde o reinício do tabuleiro."), true);
            return;
        }
        if (hasActiveAnimation()) {
            player.displayClientMessage(Component.literal("Aguarde o movimento terminar."), true);
            return;
        }
        if (tablePlayMode == TablePlayMode.ONLINE && onlineMoveInFlight) {
            player.displayClientMessage(Component.literal("Aguarde a API confirmar o lance…"), true);
            return;
        }
        if (!canPlayerControlCurrentTurn(player)) {
            if (tablePlayMode == TablePlayMode.ONLINE && getOnlineSideFor(player.getUUID()) == null) {
                player.displayClientMessage(Component.literal(
                        "Esta mesa está ligada ao online por outros jogadores — «Ligar mesa ao bloco visado» com a sua sessão e lado (API)."),
                        true);
            } else {
                player.displayClientMessage(Component.literal(whiteTurn ? "Só as brancas jogam agora." : "Só as pretas jogam agora."), true);
            }
            return;
        }
        int[] cell = hitToCell(hit, player);
        if (cell == null) {
            return;
        }
        int row = cell[0];
        int col = cell[1];
        Piece at = board[row][col];

        if (!hasSelection()) {
            if (at == Piece.EMPTY || ChessMoveLogic.isWhite(at) != whiteTurn) {
                player.displayClientMessage(Component.literal("Escolha uma peça da sua cor."), true);
                return;
            }
            selRow = row;
            selCol = col;
            recomputeValidMoves();
            tryRegisterGameSeat(player);
            setChanged();
            syncToClients();
            return;
        }

        if (row == selRow && col == selCol) {
            clearSelection();
            setChanged();
            syncToClients();
            return;
        }

        if (at != Piece.EMPTY && ChessMoveLogic.isWhite(at) == whiteTurn) {
            selRow = row;
            selCol = col;
            recomputeValidMoves();
            tryRegisterGameSeat(player);
            setChanged();
            syncToClients();
            return;
        }

        if (tablePlayMode == TablePlayMode.ONLINE) {
            submitOnlineChessMove(player, selRow, selCol, row, col);
            return;
        }

        if (!tryMove(selRow, selCol, row, col)) {
            player.displayClientMessage(Component.literal("Movimento inválido para essa peça."), true);
            return;
        }
        clearSelection();
        tryRegisterGameSeat(player);
        setChanged();
        syncToClients();
    }

    private boolean tryMove(int fr, int fc, int tr, int tc) {
        if (!ChessMoveLogic.isLegalMove(board, fr, fc, tr, tc, whiteTurn)) {
            return false;
        }
        Piece moving = board[fr][fc];
        board[fr][fc] = Piece.EMPTY;
        board[tr][tc] = Piece.EMPTY;

        animFromRow = fr;
        animFromCol = fc;
        animToRow = tr;
        animToCol = tc;
        animPiece = moving;
        animStartGameTime = level != null ? level.getGameTime() : -1L;
        return true;
    }

    private void finishAnimatedMove() {
        int tr = animToRow;
        int tc = animToCol;
        int fr = animFromRow;
        int fc = animFromCol;
        Piece moving = animPiece;
        Piece placed = promoteIfNeeded(moving, tr);
        board[tr][tc] = placed;
        clearAnimation();
        setLastMoveMarker(fr, fc, tr, tc);

        whiteTurn = !whiteTurn;
        if (!ChessMoveLogic.currentPlayerHasAnyMove(board, whiteTurn)) {
            boolean sideInCheck = ChessMoveLogic.isKingInCheck(board, whiteTurn);
            if (sideInCheck) {
                endGame(whiteTurn ? ChessGameStatus.BLACK_WIN : ChessGameStatus.WHITE_WIN,
                        Component.literal(whiteTurn ? "Xeque-mate! Pretas venceram no xadrez." : "Xeque-mate! Brancas venceram no xadrez."));
            } else {
                // Sem jogadas legais e sem xeque: empate por afogamento.
                endGame(whiteTurn ? ChessGameStatus.BLACK_WIN : ChessGameStatus.WHITE_WIN,
                        Component.literal("Sem jogadas legais (afogamento)."));
            }
        }
        setChanged();
        syncToClients();
    }

    private void endGame(ChessGameStatus status) {
        endGame(status, null);
    }

    private void endGame(ChessGameStatus status, @Nullable Component announceOverride) {
        gameStatus = status;
        resetAtGameTime = level == null ? -1L : level.getGameTime() + GAME_END_RESET_DELAY_TICKS;
        if (status == ChessGameStatus.WHITE_WIN) {
            lastWinnerDisplayName = gameSeatWhiteName == null || gameSeatWhiteName.isEmpty() ? "Brancas" : gameSeatWhiteName;
        } else if (status == ChessGameStatus.BLACK_WIN) {
            lastWinnerDisplayName = gameSeatBlackName == null || gameSeatBlackName.isEmpty() ? "Pretas" : gameSeatBlackName;
        } else if (status == ChessGameStatus.DRAW) {
            lastWinnerDisplayName = "Empate";
        } else {
            lastWinnerDisplayName = "";
        }
        if (level != null) {
            Component msg = announceOverride != null ? announceOverride
                    : switch (status) {
                        case WHITE_WIN -> Component.literal("Brancas venceram no xadrez!");
                        case BLACK_WIN -> Component.literal("Pretas venceram no xadrez!");
                        case DRAW -> Component.literal("Partida empatada.");
                        default -> Component.empty();
                    };
            if (!msg.getString().isEmpty()) {
                Vec3 center = Vec3.atCenterOf(worldPosition);
                for (Player p : level.players()) {
                    if (p.distanceToSqr(center) <= 24.0 * 24.0) {
                        p.displayClientMessage(msg, false);
                    }
                }
            }
        }
    }

    private static Piece promoteIfNeeded(Piece p, int destRow) {
        if (p == Piece.WHITE_PAWN && destRow == 0) {
            return Piece.WHITE_QUEEN;
        }
        if (p == Piece.BLACK_PAWN && destRow == 7) {
            return Piece.BLACK_QUEEN;
        }
        return p;
    }

    private boolean canPlayerControlCurrentTurn(Player player) {
        if (tablePlayMode == TablePlayMode.ONLINE) {
            UUID id = player.getUUID();
            if (whiteTurn) {
                if (onlineBoundPlayerWhite != null) {
                    return id.equals(onlineBoundPlayerWhite);
                }
                if (onlineOwnerUuid != null) {
                    return id.equals(onlineOwnerUuid) && onlineSide == OnlineSide.WHITE;
                }
                return false;
            }
            if (onlineBoundPlayerBlack != null) {
                return id.equals(onlineBoundPlayerBlack);
            }
            if (onlineOwnerUuid != null) {
                return id.equals(onlineOwnerUuid) && onlineSide == OnlineSide.BLACK;
            }
            return false;
        }
        UUID id = player.getUUID();
        if (whiteTurn) {
            return gameSeatWhiteUuid == null || gameSeatWhiteUuid.equals(id);
        }
        return gameSeatBlackUuid == null || gameSeatBlackUuid.equals(id);
    }

    private void tryRegisterGameSeat(Player player) {
        if (tablePlayMode == TablePlayMode.ONLINE) {
            return;
        }
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        if (name == null) {
            name = "";
        }
        if (gameSeatWhiteUuid == null) {
            gameSeatWhiteUuid = id;
            gameSeatWhiteName = name;
            return;
        }
        if (!gameSeatWhiteUuid.equals(id) && gameSeatBlackUuid == null) {
            gameSeatBlackUuid = id;
            gameSeatBlackName = name;
            resetGameClock();
        }
    }

    private void resetGameClock() {
        int playerTicks = BoardGameClockConfig.ticksFromMinutes(
                BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS[playerTimePresetIndex]);
        whiteClockTicks = playerTicks;
        blackClockTicks = playerTicks;
    }

    public void cyclePlayerTimePreset(Player player) {
        if (gameStatus != ChessGameStatus.PLAYING || hasGameSeatWhite() || hasGameSeatBlack()) {
            player.displayClientMessage(Component.literal("Altere o tempo antes de iniciar a partida."), true);
            return;
        }
        playerTimePresetIndex = (playerTimePresetIndex + 1) % BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS.length;
        resetGameClock();
        int minutes = BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS[playerTimePresetIndex];
        player.displayClientMessage(Component.literal("Tempo da mesa: " + minutes + " min por jogador"), true);
        setChanged();
        syncToClients();
    }

    public boolean showsPreviousMove() {
        return showPreviousMove;
    }

    public boolean showsLegalMoveHints() {
        return showLegalMoveHints;
    }

    public int getPlayerTimePresetIndex() {
        return playerTimePresetIndex;
    }

    public void toggleShowPreviousMove() {
        showPreviousMove = !showPreviousMove;
        setChanged();
        syncToClients();
    }

    public void toggleShowLegalMoveHints() {
        showLegalMoveHints = !showLegalMoveHints;
        setChanged();
        syncToClients();
    }

    /**
     * Ajusta o preset de tempo (menu). Mesmas regras que {@link #cyclePlayerTimePreset}: só antes de ambos assentos ocupados.
     */
    public void adjustTimePresetFromMenu(Player player, int delta) {
        if (gameStatus != ChessGameStatus.PLAYING || hasGameSeatWhite() || hasGameSeatBlack()) {
            player.displayClientMessage(Component.literal("Altere o tempo antes de iniciar a partida."), true);
            return;
        }
        int n = BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS.length;
        playerTimePresetIndex = ((playerTimePresetIndex + delta) % n + n) % n;
        resetGameClock();
        setChanged();
        syncToClients();
    }

    public void resetFromMenu(Player player) {
        if (hasActiveAnimation()) {
            player.displayClientMessage(Component.literal("Aguarde o movimento terminar para reiniciar."), true);
            return;
        }
        if (tablePlayMode == TablePlayMode.ONLINE) {
            clearOnlineSession(player);
        }
        resetToStartingPosition();
        setChanged();
        syncToClients();
        player.displayClientMessage(Component.literal("Mesa de xadrez reiniciada."), true);
    }

    private boolean bothGameSeatsOccupied() {
        return gameSeatWhiteUuid != null && gameSeatBlackUuid != null;
    }

    private void tickGameClock() {
        if (gameStatus != ChessGameStatus.PLAYING || !bothGameSeatsOccupied()) {
            return;
        }
        if (hasActiveAnimation()) {
            return;
        }
        if (whiteTurn) {
            whiteClockTicks = Math.max(0, whiteClockTicks - 1);
            if (whiteClockTicks <= 0) {
                endGame(ChessGameStatus.BLACK_WIN, Component.literal("Tempo das brancas esgotado! Pretas venceram."));
                setChanged();
                syncToClients();
                return;
            }
        } else {
            blackClockTicks = Math.max(0, blackClockTicks - 1);
            if (blackClockTicks <= 0) {
                endGame(ChessGameStatus.WHITE_WIN, Component.literal("Tempo das pretas esgotado! Brancas venceram."));
                setChanged();
                syncToClients();
                return;
            }
        }
        if (level.getGameTime() % 20 == 0) {
            setChanged();
            syncToClients();
        }
    }

    public int getWhiteClockTicks() {
        return whiteClockTicks;
    }

    public int getBlackClockTicks() {
        return blackClockTicks;
    }

    private int[] hitToCell(BlockHitResult hit, Player player) {
        if (hit.getDirection() != Direction.UP) {
            return null;
        }
        Vec3 o = Vec3.atLowerCornerOf(worldPosition);
        Vec3 v = hit.getLocation().subtract(o);
        if (v.x < 0 || v.x > 1 || v.z < 0 || v.z > 1) {
            return null;
        }
        Vector2d logical = new Vector2d();
        ChessBlock.axisAlignedHitToLogicalBoardFrac(v.x, v.z, getBlockState().getValue(ChessBlock.FACING), logical);
        if (tablePlayMode == TablePlayMode.ONLINE && player != null && getOnlineSideFor(player.getUUID()) == OnlineSide.BLACK) {
            logical.x = 1.0 - logical.x;
            logical.y = 1.0 - logical.y;
        }
        int col = Mth.clamp((int) (logical.x * 8), 0, 7);
        int row = Mth.clamp((int) (logical.y * 8), 0, 7);
        return new int[] { row, col };
    }

    private void recomputeValidMoves() {
        validMoveCount = 0;
        captureMoveCount = 0;
        blockedByCheckMoveCount = 0;
        if (!hasSelection() || gameStatus != ChessGameStatus.PLAYING) {
            return;
        }
        ChessMoveLogic.collectLegalMovesForPiece(board, selRow, selCol, whiteTurn, packed -> {
            if (validMoveCount < validMovesBuf.length) {
                validMovesBuf[validMoveCount++] = packed;
                int tr = packed / 8;
                int tc = packed % 8;
                if (board[tr][tc] != Piece.EMPTY && captureMoveCount < captureMovesBuf.length) {
                    captureMovesBuf[captureMoveCount++] = packed;
                }
            }
        });
        ChessMoveLogic.collectBlockedByCheckMovesForPiece(board, selRow, selCol, whiteTurn, packed -> {
            if (blockedByCheckMoveCount < blockedByCheckMovesBuf.length) {
                blockedByCheckMovesBuf[blockedByCheckMoveCount++] = packed;
            }
        });
    }

    private void clearSelection() {
        selRow = -1;
        selCol = -1;
        validMoveCount = 0;
        captureMoveCount = 0;
        blockedByCheckMoveCount = 0;
    }

    private void setLastMoveMarker(int fromRow, int fromCol, int toRow, int toCol) {
        lastMoveFromRow = fromRow;
        lastMoveFromCol = fromCol;
        lastMoveToRow = toRow;
        lastMoveToCol = toCol;
    }

    private void clearLastMoveMarker() {
        lastMoveFromRow = -1;
        lastMoveFromCol = -1;
        lastMoveToRow = -1;
        lastMoveToCol = -1;
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public Piece getPiece(int row, int col) {
        return board[Mth.clamp(row, 0, 7)][Mth.clamp(col, 0, 7)];
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public boolean hasSelection() {
        return selRow >= 0 && selCol >= 0;
    }

    public int getSelectedRow() {
        return selRow;
    }

    public int getSelectedCol() {
        return selCol;
    }

    public int getValidMoveCount() {
        return validMoveCount;
    }

    public int getValidMovePacked(int index) {
        if (index < 0 || index >= validMoveCount) {
            return 0;
        }
        return validMovesBuf[index];
    }

    public int getCaptureMoveCount() {
        return captureMoveCount;
    }

    public int getCaptureMovePacked(int index) {
        if (index < 0 || index >= captureMoveCount) {
            return 0;
        }
        return captureMovesBuf[index];
    }

    public int getBlockedByCheckMoveCount() {
        return blockedByCheckMoveCount;
    }

    public int getBlockedByCheckMovePacked(int index) {
        if (index < 0 || index >= blockedByCheckMoveCount) {
            return 0;
        }
        return blockedByCheckMovesBuf[index];
    }

    public boolean hasLastMoveMarker() {
        return lastMoveFromRow >= 0 && lastMoveFromCol >= 0 && lastMoveToRow >= 0 && lastMoveToCol >= 0;
    }

    public int getLastMoveFromRow() {
        return lastMoveFromRow;
    }

    public int getLastMoveFromCol() {
        return lastMoveFromCol;
    }

    public int getLastMoveToRow() {
        return lastMoveToRow;
    }

    public int getLastMoveToCol() {
        return lastMoveToCol;
    }

    public boolean isGameInProgress() {
        return gameStatus == ChessGameStatus.PLAYING;
    }

    public ChessGameStatus getGameStatus() {
        return gameStatus;
    }

    public boolean hasGameSeatWhite() {
        return gameSeatWhiteUuid != null;
    }

    public boolean hasGameSeatBlack() {
        return gameSeatBlackUuid != null;
    }

    public String getGameSeatWhiteName() {
        return gameSeatWhiteName == null ? "" : gameSeatWhiteName;
    }

    public String getGameSeatBlackName() {
        return gameSeatBlackName == null ? "" : gameSeatBlackName;
    }

    /** Mesa em modo online com partida API ativa (para layout da HUD). */
    public boolean isChessOnlineTable() {
        return tablePlayMode == TablePlayMode.ONLINE && onlineMatchId != null && !onlineMatchId.isEmpty();
    }

    /** Texto do jogador das brancas na HUD (local: assento; online: nome guardado / API). */
    public String getChessHudWhiteDisplayName() {
        if (!isChessOnlineTable()) {
            return hasGameSeatWhite() ? getGameSeatWhiteName() : "—";
        }
        if (onlineHudWhiteName != null && !onlineHudWhiteName.isBlank()) {
            return onlineHudWhiteName.trim();
        }
        return "—";
    }

    /** Texto do jogador das pretas na HUD (local: assento; online: nome guardado / API). */
    public String getChessHudBlackDisplayName() {
        if (!isChessOnlineTable()) {
            return hasGameSeatBlack() ? getGameSeatBlackName() : "—";
        }
        if (onlineHudBlackName != null && !onlineHudBlackName.isBlank()) {
            return onlineHudBlackName.trim();
        }
        return "—";
    }

    public boolean isRegisteredParticipant(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (tablePlayMode == TablePlayMode.ONLINE) {
            return getOnlineSideFor(playerId) != null;
        }
        return playerId.equals(gameSeatWhiteUuid) || playerId.equals(gameSeatBlackUuid);
    }

    public String getLastWinnerDisplayName() {
        return lastWinnerDisplayName == null ? "" : lastWinnerDisplayName;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readTag(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeTag(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readTag(tag);
    }

    public Player getWhitePlayer() {
        if (level == null || gameSeatWhiteUuid == null) {
            return null;
        }
        return level.getPlayerByUUID(gameSeatWhiteUuid);
    }

    public Player getBlackPlayer() {
        if (level == null || gameSeatBlackUuid == null) {
            return null;
        }
        return level.getPlayerByUUID(gameSeatBlackUuid);
    }

    /**
     * Perfil para textura de skin na HUD (cliente): jogador carregado no nível ou perfil mínimo pelo assento.
     */
    @Nullable
    public GameProfile getWhiteSeatGameProfile() {
        if (gameSeatWhiteUuid == null) {
            return null;
        }
        Player p = getWhitePlayer();
        if (p != null) {
            return p.getGameProfile();
        }
        String name = gameSeatWhiteName != null && !gameSeatWhiteName.isBlank() ? gameSeatWhiteName : "Player";
        return new GameProfile(gameSeatWhiteUuid, name);
    }

    @Nullable
    public GameProfile getBlackSeatGameProfile() {
        if (gameSeatBlackUuid == null) {
            return null;
        }
        Player p = getBlackPlayer();
        if (p != null) {
            return p.getGameProfile();
        }
        String name = gameSeatBlackName != null && !gameSeatBlackName.isBlank() ? gameSeatBlackName : "Player";
        return new GameProfile(gameSeatBlackUuid, name);
    }

    private void writeTag(CompoundTag tag) {
        int[] flat = new int[64];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                flat[r * 8 + c] = board[r][c].ordinal();
            }
        }
        tag.putIntArray("ChessBoard", flat);
        tag.putBoolean("WhiteTurn", whiteTurn);
        tag.putInt("SelRow", selRow);
        tag.putInt("SelCol", selCol);
        tag.putInt("ValidMoveCount", validMoveCount);
        tag.putIntArray("ValidMoves", Arrays.copyOf(validMovesBuf, validMoveCount));
        tag.putInt("CaptureMoveCount", captureMoveCount);
        tag.putIntArray("CaptureMoves", Arrays.copyOf(captureMovesBuf, captureMoveCount));
        tag.putInt("BlockedByCheckMoveCount", blockedByCheckMoveCount);
        tag.putIntArray("BlockedByCheckMoves", Arrays.copyOf(blockedByCheckMovesBuf, blockedByCheckMoveCount));
        tag.putInt("GameStatus", gameStatus.ordinal());
        tag.putLong("ResetAtGameTime", resetAtGameTime);
        tag.putBoolean("OnlineEnabled", tablePlayMode == TablePlayMode.ONLINE);
        tag.putString("OnlineSessionW", onlineSessionWhite == null ? "" : onlineSessionWhite);
        tag.putString("OnlineSessionB", onlineSessionBlack == null ? "" : onlineSessionBlack);
        tag.putString("OnlineSession", coalesceSessions(onlineSessionWhite, onlineSessionBlack, onlineSessionId));
        tag.putString("OnlineMatch", onlineMatchId == null ? "" : onlineMatchId);
        tag.putInt("OnlineSide", onlineSide.ordinal());
        if (onlineBoundPlayerWhite != null) {
            tag.putUUID("OnlineBindW", onlineBoundPlayerWhite);
        }
        if (onlineBoundPlayerBlack != null) {
            tag.putUUID("OnlineBindB", onlineBoundPlayerBlack);
        }
        if (onlineOwnerUuid != null) {
            tag.putUUID("OnlineOwner", onlineOwnerUuid);
        }
        tag.putInt("OnlineCapW", onlineCapturedWhite);
        tag.putInt("OnlineCapB", onlineCapturedBlack);
        tag.putString("OnlineHudW", onlineHudWhiteName == null ? "" : onlineHudWhiteName);
        tag.putString("OnlineHudB", onlineHudBlackName == null ? "" : onlineHudBlackName);
        if (gameSeatWhiteUuid != null) {
            tag.putUUID("GameSeatWhite", gameSeatWhiteUuid);
        }
        tag.putString("GameSeatWhiteName", gameSeatWhiteName == null ? "" : gameSeatWhiteName);
        if (gameSeatBlackUuid != null) {
            tag.putUUID("GameSeatBlack", gameSeatBlackUuid);
        }
        tag.putString("GameSeatBlackName", gameSeatBlackName == null ? "" : gameSeatBlackName);
        tag.putString("LastWinnerName", lastWinnerDisplayName == null ? "" : lastWinnerDisplayName);
        tag.putInt("ClockWhiteTicks", whiteClockTicks);
        tag.putInt("ClockBlackTicks", blackClockTicks);
        tag.putInt("ClockPresetIndex", playerTimePresetIndex);
        tag.putBoolean("ShowPreviousMove", showPreviousMove);
        tag.putBoolean("ShowLegalHints", showLegalMoveHints);
        tag.putBoolean("AnimActive", hasActiveAnimation());
        if (hasActiveAnimation()) {
            tag.putInt("AnimFromR", animFromRow);
            tag.putInt("AnimFromC", animFromCol);
            tag.putInt("AnimToR", animToRow);
            tag.putInt("AnimToC", animToCol);
            tag.putInt("AnimPiece", animPiece.ordinal());
            tag.putLong("AnimStart", animStartGameTime);
        }
        tag.putBoolean("LastMoveActive", hasLastMoveMarker());
        if (hasLastMoveMarker()) {
            tag.putInt("LastMoveFromR", lastMoveFromRow);
            tag.putInt("LastMoveFromC", lastMoveFromCol);
            tag.putInt("LastMoveToR", lastMoveToRow);
            tag.putInt("LastMoveToC", lastMoveToCol);
        }
    }

    private void readTag(CompoundTag tag) {
        int[] flat = tag.getIntArray("ChessBoard");
        if (flat.length == 64) {
            Piece[] values = Piece.values();
            for (int i = 0; i < 64; i++) {
                int ord = flat[i];
                board[i / 8][i % 8] = (ord >= 0 && ord < values.length) ? values[ord] : Piece.EMPTY;
            }
        }
        whiteTurn = tag.contains("WhiteTurn") && tag.getBoolean("WhiteTurn");
        selRow = tag.contains("SelRow") ? tag.getInt("SelRow") : -1;
        selCol = tag.contains("SelCol") ? tag.getInt("SelCol") : -1;
        int[] vm = tag.getIntArray("ValidMoves");
        validMoveCount = Math.min(tag.contains("ValidMoveCount") ? tag.getInt("ValidMoveCount") : vm.length, vm.length);
        validMoveCount = Math.min(validMoveCount, validMovesBuf.length);
        for (int i = 0; i < validMoveCount; i++) {
            validMovesBuf[i] = vm[i];
        }
        int[] cm = tag.getIntArray("CaptureMoves");
        captureMoveCount = Math.min(tag.contains("CaptureMoveCount") ? tag.getInt("CaptureMoveCount") : cm.length, cm.length);
        captureMoveCount = Math.min(captureMoveCount, captureMovesBuf.length);
        for (int i = 0; i < captureMoveCount; i++) {
            captureMovesBuf[i] = cm[i];
        }
        int[] bm = tag.getIntArray("BlockedByCheckMoves");
        blockedByCheckMoveCount = Math.min(tag.contains("BlockedByCheckMoveCount") ? tag.getInt("BlockedByCheckMoveCount") : bm.length, bm.length);
        blockedByCheckMoveCount = Math.min(blockedByCheckMoveCount, blockedByCheckMovesBuf.length);
        for (int i = 0; i < blockedByCheckMoveCount; i++) {
            blockedByCheckMovesBuf[i] = bm[i];
        }
        if (tag.contains("GameStatus")) {
            int ord = tag.getInt("GameStatus");
            ChessGameStatus[] values = ChessGameStatus.values();
            gameStatus = ord >= 0 && ord < values.length ? values[ord] : ChessGameStatus.PLAYING;
        } else {
            gameStatus = ChessGameStatus.PLAYING;
        }
        resetAtGameTime = tag.contains("ResetAtGameTime") ? tag.getLong("ResetAtGameTime") : -1L;
        gameSeatWhiteUuid = tag.hasUUID("GameSeatWhite") ? tag.getUUID("GameSeatWhite") : null;
        gameSeatWhiteName = tag.contains("GameSeatWhiteName") ? tag.getString("GameSeatWhiteName") : "";
        gameSeatBlackUuid = tag.hasUUID("GameSeatBlack") ? tag.getUUID("GameSeatBlack") : null;
        gameSeatBlackName = tag.contains("GameSeatBlackName") ? tag.getString("GameSeatBlackName") : "";
        lastWinnerDisplayName = tag.contains("LastWinnerName") ? tag.getString("LastWinnerName") : "";
        whiteClockTicks = tag.contains("ClockWhiteTicks") ? tag.getInt("ClockWhiteTicks") : BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS;
        blackClockTicks = tag.contains("ClockBlackTicks") ? tag.getInt("ClockBlackTicks") : BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS;
        int presetCount = BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS.length;
        int presetIndex = tag.contains("ClockPresetIndex") ? tag.getInt("ClockPresetIndex")
                : BoardGameClockConfig.closestPresetIndexFromTicks(whiteClockTicks);
        if (presetIndex < 0 || presetIndex >= presetCount) {
            presetIndex = BoardGameClockConfig.closestPresetIndexFromTicks(whiteClockTicks);
        }
        playerTimePresetIndex = presetIndex;
        showPreviousMove = !tag.contains("ShowPreviousMove") || tag.getBoolean("ShowPreviousMove");
        showLegalMoveHints = !tag.contains("ShowLegalHints") || tag.getBoolean("ShowLegalHints");
        if (tag.getBoolean("AnimActive")) {
            animFromRow = tag.getInt("AnimFromR");
            animFromCol = tag.getInt("AnimFromC");
            animToRow = tag.getInt("AnimToR");
            animToCol = tag.getInt("AnimToC");
            int po = tag.getInt("AnimPiece");
            Piece[] values = Piece.values();
            animPiece = (po >= 0 && po < values.length) ? values[po] : Piece.EMPTY;
            animStartGameTime = tag.getLong("AnimStart");
        } else {
            clearAnimation();
        }
        if (tag.getBoolean("LastMoveActive")) {
            lastMoveFromRow = tag.getInt("LastMoveFromR");
            lastMoveFromCol = tag.getInt("LastMoveFromC");
            lastMoveToRow = tag.getInt("LastMoveToR");
            lastMoveToCol = tag.getInt("LastMoveToC");
        } else {
            clearLastMoveMarker();
        }
        if (tag.contains("OnlineEnabled") && tag.getBoolean("OnlineEnabled") && tag.contains("OnlineMatch")
                && !tag.getString("OnlineMatch").isEmpty()) {
            String match = tag.getString("OnlineMatch");
            boolean hasLegacySession = tag.contains("OnlineSession") && !tag.getString("OnlineSession").isEmpty();
            boolean newFmt = tag.contains("OnlineSessionW") || tag.contains("OnlineSessionB");
            tablePlayMode = TablePlayMode.ONLINE;
            onlineMatchId = match;
            int sd = tag.getInt("OnlineSide");
            onlineSide = sd == 1 ? OnlineSide.BLACK : OnlineSide.WHITE;
            onlineCapturedWhite = tag.contains("OnlineCapW") ? tag.getInt("OnlineCapW") : 0;
            onlineCapturedBlack = tag.contains("OnlineCapB") ? tag.getInt("OnlineCapB") : 0;
            onlineHudWhiteName = tag.contains("OnlineHudW") ? tag.getString("OnlineHudW") : "";
            onlineHudBlackName = tag.contains("OnlineHudB") ? tag.getString("OnlineHudB") : "";
            onlineSessionWhite = tag.contains("OnlineSessionW") ? tag.getString("OnlineSessionW") : "";
            onlineSessionBlack = tag.contains("OnlineSessionB") ? tag.getString("OnlineSessionB") : "";
            onlineBoundPlayerWhite = tag.hasUUID("OnlineBindW") ? tag.getUUID("OnlineBindW") : null;
            onlineBoundPlayerBlack = tag.hasUUID("OnlineBindB") ? tag.getUUID("OnlineBindB") : null;
            onlineOwnerUuid = tag.hasUUID("OnlineOwner") ? tag.getUUID("OnlineOwner") : null;
            onlineSessionId = tag.contains("OnlineSession") ? tag.getString("OnlineSession") : "";
            if (!newFmt && hasLegacySession) {
                String os = onlineSessionId;
                onlineSessionWhite = "";
                onlineSessionBlack = "";
                onlineBoundPlayerWhite = null;
                onlineBoundPlayerBlack = null;
                if (onlineOwnerUuid != null) {
                    if (onlineSide == OnlineSide.BLACK) {
                        onlineSessionBlack = os;
                        onlineBoundPlayerBlack = onlineOwnerUuid;
                    } else {
                        onlineSessionWhite = os;
                        onlineBoundPlayerWhite = onlineOwnerUuid;
                    }
                }
            } else {
                onlineSessionId = coalesceSessions(onlineSessionWhite, onlineSessionBlack, onlineSessionId);
            }
        } else {
            tablePlayMode = TablePlayMode.LOCAL;
            onlineSessionId = "";
            onlineSessionWhite = "";
            onlineSessionBlack = "";
            onlineMatchId = "";
            onlineBoundPlayerWhite = null;
            onlineBoundPlayerBlack = null;
            onlineOwnerUuid = null;
            onlineCapturedWhite = 0;
            onlineCapturedBlack = 0;
            onlineHudWhiteName = "";
            onlineHudBlackName = "";
        }
        if (level != null && level.isClientSide() && tablePlayMode == TablePlayMode.ONLINE && gameStatus != ChessGameStatus.PLAYING
                && onlineMatchId != null && !onlineMatchId.isEmpty()) {
            ClientOnlineChessAfterMatch.clearCachedBindingsIfSameMatch(onlineMatchId);
        }
    }
}
