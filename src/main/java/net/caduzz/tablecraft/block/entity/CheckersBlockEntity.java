package net.caduzz.tablecraft.block.entity;

import java.util.Arrays;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector2d;

import com.mojang.authlib.GameProfile;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.CheckersBlock;
import net.caduzz.tablecraft.block.CheckersMoveLogic;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CheckersBlockEntity extends BlockEntity {

    public static final float MOVE_DURATION_TICKS = 12.0f;
    /** ~5 s a 20 TPS antes de reiniciar o tabuleiro após vitória. */
    public static final long GAME_END_RESET_DELAY_TICKS = 100L;

    public enum CheckersGameStatus {
        PLAYING,
        WHITE_WIN,
        BLACK_WIN
    }

    public enum Piece {
        EMPTY,
        WHITE_MAN,
        WHITE_KING,
        BLACK_MAN,
        BLACK_KING
    }

    private final Piece[][] board = new Piece[8][8];

    private boolean whiteTurn = true;
    private int selRow = -1;
    private int selCol = -1;

    private int validMoveCount;
    private final int[] validMovesBuf = new int[16];

    /** Um movimento animado por vez; peça removida da origem até o fim da animação. */
    private int animFromRow;
    private int animFromCol;
    private int animToRow;
    private int animToCol;
    private Piece animPiece = Piece.EMPTY;
    private long animStartGameTime = -1L;
    private boolean animIsCapture;

    private CheckersGameStatus gameStatus = CheckersGameStatus.PLAYING;
    private long resetAtGameTime = -1L;

    /** Dois primeiros jogadores distintos que agiram no tabuleiro (brancas / pretas na HUD). */
    private UUID gameSeatWhiteUuid;
    private String gameSeatWhiteName = "";
    private UUID gameSeatBlackUuid;
    private String gameSeatBlackName = "";

    /** Nome exibido na vitória (participante do lado vencedor). */
    private String lastWinnerDisplayName = "";
    private int whiteClockTicks = BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS;
    private int blackClockTicks = BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS;
    private int playerTimePresetIndex = BoardGameClockConfig.closestPresetIndexFromTicks(BoardGameClockConfig.DEFAULT_PLAYER_TIME_TICKS);

    public CheckersBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHECKERS.get(), pos, state);
        resetToStartingPosition();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CheckersBlockEntity be) {
        if (!level.isClientSide()) {
            be.serverTick();
        }
    }

    private void serverTick() {
        if (level == null) {
            return;
        }
        if (gameStatus != CheckersGameStatus.PLAYING && resetAtGameTime >= 0L
                && level.getGameTime() >= resetAtGameTime) {
            performAutoResetAfterGameEnd();
            return;
        }
        if (gameStatus == CheckersGameStatus.PLAYING) {
            tickGameClock();
        }
        if (gameStatus != CheckersGameStatus.PLAYING) {
            return;
        }
        if (animStartGameTime < 0L) {
            return;
        }
        if (level.getGameTime() >= animStartGameTime + (long) MOVE_DURATION_TICKS) {
            finishAnimatedMove();
        }
    }

    private void finishAnimatedMove() {
        int endR = animToRow;
        int endC = animToCol;
        Piece moving = animPiece;
        boolean wasCapture = animIsCapture;

        Piece placed = promoteIfNeeded(moving, endR);
        board[endR][endC] = placed;
        animPiece = Piece.EMPTY;
        animStartGameTime = -1L;
        animIsCapture = false;

        int wc = CheckersMoveLogic.countWhitePieces(board);
        int bc = CheckersMoveLogic.countBlackPieces(board);
        if (wc == 0) {
            endGame(CheckersGameStatus.BLACK_WIN);
            return;
        }
        if (bc == 0) {
            endGame(CheckersGameStatus.WHITE_WIN);
            return;
        }

        boolean chainCapture = wasCapture
                && CheckersMoveLogic.pieceHasAnyCapture(board, endR, endC, whiteTurn);
        if (chainCapture) {
            selRow = endR;
            selCol = endC;
            recomputeValidMoves();
        } else {
            whiteTurn = !whiteTurn;
            selRow = -1;
            selCol = -1;
            clearValidMoves();
            if (!CheckersMoveLogic.currentPlayerHasAnyMove(board, whiteTurn)) {
                endGame(whiteTurn ? CheckersGameStatus.BLACK_WIN : CheckersGameStatus.WHITE_WIN);
                return;
            }
        }
        setChanged();
        syncToClients();
    }

    private void endGame(CheckersGameStatus outcome) {
        endGame(outcome, null);
    }

    private void endGame(CheckersGameStatus outcome, @Nullable Component announceOverride) {
        gameStatus = outcome;
        resetAtGameTime = level.getGameTime() + GAME_END_RESET_DELAY_TICKS;
        selRow = -1;
        selCol = -1;
        clearValidMoves();
        if (outcome == CheckersGameStatus.WHITE_WIN) {
            lastWinnerDisplayName = gameSeatWhiteName == null || gameSeatWhiteName.isEmpty() ? "Brancas" : gameSeatWhiteName;
        } else {
            lastWinnerDisplayName = gameSeatBlackName == null || gameSeatBlackName.isEmpty() ? "Pretas" : gameSeatBlackName;
        }
        Component msg = announceOverride != null ? announceOverride
                : (outcome == CheckersGameStatus.WHITE_WIN
                        ? Component.literal("Branco venceu!")
                        : Component.literal("Preto venceu!"));
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double reach = 24.0;
        double reachSq = reach * reach;
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp && p.distanceToSqr(center) <= reachSq) {
                sp.sendSystemMessage(msg);
            }
        }
        setChanged();
        syncToClients();
    }

    private void performAutoResetAfterGameEnd() {
        resetToStartingPosition();
        setChanged();
        syncToClients();
    }

    private static Piece promoteIfNeeded(Piece p, int destRow) {
        if (p == Piece.WHITE_MAN && destRow == 0) {
            return Piece.WHITE_KING;
        }
        if (p == Piece.BLACK_MAN && destRow == 7) {
            return Piece.BLACK_KING;
        }
        return p;
    }

    public void resetToStartingPosition() {
        for (Piece[] row : board) {
            Arrays.fill(row, Piece.EMPTY);
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 8; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = Piece.BLACK_MAN;
                }
            }
        }
        for (int r = 5; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = Piece.WHITE_MAN;
                }
            }
        }
        whiteTurn = true;
        selRow = -1;
        selCol = -1;
        gameStatus = CheckersGameStatus.PLAYING;
        resetAtGameTime = -1L;
        clearValidMoves();
        clearAnimation();
        clearGameSeats();
        lastWinnerDisplayName = "";
        resetGameClock();
    }

    private void clearGameSeats() {
        gameSeatWhiteUuid = null;
        gameSeatWhiteName = "";
        gameSeatBlackUuid = null;
        gameSeatBlackName = "";
    }

    public String getLastWinnerDisplayName() {
        return lastWinnerDisplayName == null ? "" : lastWinnerDisplayName;
    }

    public boolean isRegisteredParticipant(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return playerId.equals(gameSeatWhiteUuid) || playerId.equals(gameSeatBlackUuid);
    }

    public boolean hasGameSeatWhite() {
        return gameSeatWhiteUuid != null;
    }

    public String getGameSeatWhiteName() {
        return gameSeatWhiteName == null ? "" : gameSeatWhiteName;
    }

    public boolean hasGameSeatBlack() {
        return gameSeatBlackUuid != null;
    }

    public String getGameSeatBlackName() {
        return gameSeatBlackName == null ? "" : gameSeatBlackName;
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

    /**
     * Com lugares já atribuídos, só o dono da cor da vez pode interagir.
     * Lugar ainda vazio = qualquer um (ex.: solo até registar o 2.º).
     */
    private boolean canPlayerControlCurrentTurn(Player player) {
        UUID id = player.getUUID();
        if (whiteTurn) {
            if (gameSeatWhiteUuid == null) {
                return true;
            }
            return gameSeatWhiteUuid.equals(id);
        }
        if (gameSeatBlackUuid == null) {
            return true;
        }
        return gameSeatBlackUuid.equals(id);
    }

    private void tryRegisterGameSeat(Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        if (name == null) {
            name = "";
        }
        boolean changed = false;
        if (gameSeatWhiteUuid == null) {
            gameSeatWhiteUuid = id;
            gameSeatWhiteName = name;
            changed = true;
        } else if (!gameSeatWhiteUuid.equals(id) && gameSeatBlackUuid == null) {
            gameSeatBlackUuid = id;
            gameSeatBlackName = name;
            resetGameClock();
            changed = true;
        }
        if (changed) {
            setChanged();
            syncToClients();
        }
    }

    private void resetGameClock() {
        int playerTicks = BoardGameClockConfig.ticksFromMinutes(
                BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS[playerTimePresetIndex]);
        whiteClockTicks = playerTicks;
        blackClockTicks = playerTicks;
    }

    public void cyclePlayerTimePreset(Player player) {
        if (gameStatus != CheckersGameStatus.PLAYING || hasGameSeatWhite() || hasGameSeatBlack()) {
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

    private boolean bothGameSeatsOccupied() {
        return gameSeatWhiteUuid != null && gameSeatBlackUuid != null;
    }

    private void tickGameClock() {
        if (gameStatus != CheckersGameStatus.PLAYING || !bothGameSeatsOccupied()) {
            return;
        }
        if (hasActiveAnimation()) {
            return;
        }
        if (whiteTurn) {
            whiteClockTicks = Math.max(0, whiteClockTicks - 1);
            if (whiteClockTicks <= 0) {
                endGame(CheckersGameStatus.BLACK_WIN, Component.literal("Tempo das brancas esgotado! Pretas venceram."));
                return;
            }
        } else {
            blackClockTicks = Math.max(0, blackClockTicks - 1);
            if (blackClockTicks <= 0) {
                endGame(CheckersGameStatus.WHITE_WIN, Component.literal("Tempo das pretas esgotado! Brancas venceram."));
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

    public CheckersGameStatus getGameStatus() {
        return gameStatus;
    }

    public boolean isGameInProgress() {
        return gameStatus == CheckersGameStatus.PLAYING;
    }

    private void clearAnimation() {
        animPiece = Piece.EMPTY;
        animStartGameTime = -1L;
        animIsCapture = false;
    }

    public Piece getPiece(int row, int col) {
        return board[Mth.clamp(row, 0, 7)][Mth.clamp(col, 0, 7)];
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
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

    public long getAnimStartGameTime() {
        return animStartGameTime;
    }

    /**
     * Progresso linear 0–1 da animação atual (cliente usa com partialTick).
     */
    public float getMoveProgress(float partialTick) {
        if (level == null || animStartGameTime < 0L || animPiece == Piece.EMPTY) {
            return 0f;
        }
        float age = level.getGameTime() - animStartGameTime + partialTick;
        return Mth.clamp(age / MOVE_DURATION_TICKS, 0f, 1f);
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

    private void clearValidMoves() {
        validMoveCount = 0;
    }

    private void recomputeValidMoves() {
        clearValidMoves();
        if (gameStatus != CheckersGameStatus.PLAYING) {
            return;
        }
        if (selRow < 0 || selCol < 0) {
            return;
        }
        CheckersMoveLogic.collectLegalMovesForPiece(board, selRow, selCol, whiteTurn, packed -> {
            if (validMoveCount < validMovesBuf.length) {
                validMovesBuf[validMoveCount++] = packed;
            }
        });
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && hasSelection() && gameStatus == CheckersGameStatus.PLAYING) {
            recomputeValidMoves();
        }
    }

    public void handlePlayerClick(Player player, BlockHitResult hit) {
        if (gameStatus != CheckersGameStatus.PLAYING) {
            player.displayClientMessage(Component.literal("Partida encerrada. Aguarde o reinício do tabuleiro."), true);
            return;
        }
        int[] cell = hitToCell(hit);
        if (cell == null) {
            player.displayClientMessage(Component.literal("Clique no topo do tabuleiro para escolher uma casa."), true);
            TableCraft.LOGGER.debug("Checkers: hit not on top face at {}", worldPosition);
            return;
        }
        if (hasActiveAnimation()) {
            player.displayClientMessage(Component.literal("Aguarde o movimento terminar."), true);
            return;
        }
        if (!canPlayerControlCurrentTurn(player)) {
            player.displayClientMessage(
                    Component.literal(whiteTurn ? "Só o jogador das brancas pode jogar agora." : "Só o jogador das pretas pode jogar agora."),
                    true);
            return;
        }

        int row = cell[0];
        int col = cell[1];
        Piece at = board[row][col];

        if (selRow < 0) {
            if (at == Piece.EMPTY) {
                player.displayClientMessage(Component.literal("Escolha uma peça sua."), true);
                return;
            }
            if (CheckersMoveLogic.isWhite(at) != whiteTurn) {
                player.displayClientMessage(Component.literal("Não é a vez dessa cor."), true);
                return;
            }
            if (CheckersMoveLogic.playerHasAnyCapture(board, whiteTurn)
                    && !CheckersMoveLogic.pieceHasAnyCapture(board, row, col, whiteTurn)) {
                player.displayClientMessage(Component.literal("Captura obrigatória: escolha uma peça que possa capturar."), true);
                return;
            }
            selRow = row;
            selCol = col;
            recomputeValidMoves();
            tryRegisterGameSeat(player);
            player.displayClientMessage(Component.literal("Peça selecionada. Clique na casa de destino."), true);
            setChanged();
            syncToClients();
            return;
        }

        if (row == selRow && col == selCol) {
            selRow = -1;
            selCol = -1;
            clearValidMoves();
            player.displayClientMessage(Component.literal("Seleção cancelada."), true);
            setChanged();
            syncToClients();
            return;
        }

        if (at != Piece.EMPTY && CheckersMoveLogic.isWhite(at) == whiteTurn) {
            if (CheckersMoveLogic.playerHasAnyCapture(board, whiteTurn)
                    && !CheckersMoveLogic.pieceHasAnyCapture(board, row, col, whiteTurn)) {
                player.displayClientMessage(Component.literal("Captura obrigatória: essa peça não pode capturar."), true);
                return;
            }
            selRow = row;
            selCol = col;
            recomputeValidMoves();
            tryRegisterGameSeat(player);
            player.displayClientMessage(Component.literal("Outra peça selecionada."), true);
            setChanged();
            syncToClients();
            return;
        }

        if (!tryStartMove(selRow, selCol, row, col)) {
            if (CheckersMoveLogic.playerHasAnyCapture(board, whiteTurn)) {
                player.displayClientMessage(Component.literal("Movimento inválido: use uma captura em diagonal (2 casas)."), true);
            } else {
                player.displayClientMessage(Component.literal("Movimento inválido (diagonal 1 casa, casa escura vazia)."), true);
            }
            return;
        }

        selRow = -1;
        selCol = -1;
        clearValidMoves();
        tryRegisterGameSeat(player);
        player.displayClientMessage(Component.literal("Movendo…"), true);
        setChanged();
        syncToClients();
    }

    private boolean tryStartMove(int fromR, int fromC, int toR, int toC) {
        Piece p = board[fromR][fromC];
        if (p == Piece.EMPTY || CheckersMoveLogic.isWhite(p) != whiteTurn) {
            return false;
        }
        if (!CheckersMoveLogic.isLegalMoveForPiece(board, fromR, fromC, toR, toC, whiteTurn)) {
            return false;
        }

        boolean capture = CheckersMoveLogic.canCaptureJump(board, fromR, fromC, toR, toC, whiteTurn);
        board[fromR][fromC] = Piece.EMPTY;
        if (capture) {
            int mr = (fromR + toR) / 2;
            int mc = (fromC + toC) / 2;
            board[mr][mc] = Piece.EMPTY;
        }
        animIsCapture = capture;
        animFromRow = fromR;
        animFromCol = fromC;
        animToRow = toR;
        animToCol = toC;
        animPiece = p;
        animStartGameTime = level != null ? level.getGameTime() : 0L;
        return true;
    }

    private int[] hitToCell(BlockHitResult hit) {
        if (hit.getDirection() != Direction.UP) {
            return null;
        }
        Vec3 o = Vec3.atLowerCornerOf(worldPosition);
        Vec3 v = hit.getLocation().subtract(o);
        double ax = v.x;
        double az = v.z;
        if (ax < 0 || ax > 1 || az < 0 || az > 1) {
            return null;
        }
        Vector2d logical = new Vector2d();
        CheckersBlock.axisAlignedHitToLogicalBoardFrac(ax, az, getBlockState().getValue(CheckersBlock.FACING), logical);
        int col = Mth.clamp((int) (logical.x * 8), 0, 7);
        int row = Mth.clamp((int) (logical.y * 8), 0, 7);
        return new int[] { row, col };
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeCommonTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readCommonTag(tag);
    }

    private void writeCommonTag(CompoundTag tag) {
        int[] flat = new int[64];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                flat[r * 8 + c] = board[r][c].ordinal();
            }
        }
        tag.putIntArray("CheckersBoard", flat);
        tag.putBoolean("WhiteTurn", whiteTurn);
        tag.putInt("SelRow", selRow);
        tag.putInt("SelCol", selCol);
        tag.putBoolean("AnimActive", animStartGameTime >= 0L && animPiece != Piece.EMPTY);
        if (animStartGameTime >= 0L && animPiece != Piece.EMPTY) {
            tag.putInt("AnimFromR", animFromRow);
            tag.putInt("AnimFromC", animFromCol);
            tag.putInt("AnimToR", animToRow);
            tag.putInt("AnimToC", animToCol);
            tag.putInt("AnimPiece", animPiece.ordinal());
            tag.putLong("AnimStart", animStartGameTime);
        }
        tag.putInt("ValidMoveCount", validMoveCount);
        if (validMoveCount > 0) {
            tag.putIntArray("ValidMoves", Arrays.copyOfRange(validMovesBuf, 0, validMoveCount));
        } else {
            tag.putIntArray("ValidMoves", new int[0]);
        }
        tag.putInt("GameStatus", gameStatus.ordinal());
        tag.putLong("ResetAtGameTime", resetAtGameTime);
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
    }

    private void readCommonTag(CompoundTag tag) {
        int[] flat = tag.getIntArray("CheckersBoard");
        if (flat.length == 64) {
            Piece[] values = Piece.values();
            for (int i = 0; i < 64; i++) {
                int ord = flat[i];
                Piece p = (ord >= 0 && ord < values.length) ? values[ord] : Piece.EMPTY;
                board[i / 8][i % 8] = p;
            }
        }
        whiteTurn = tag.contains("WhiteTurn") ? tag.getBoolean("WhiteTurn") : true;
        selRow = tag.contains("SelRow") ? tag.getInt("SelRow") : -1;
        selCol = tag.contains("SelCol") ? tag.getInt("SelCol") : -1;
        if (tag.contains("GameStatus")) {
            int ord = tag.getInt("GameStatus");
            CheckersGameStatus[] vals = CheckersGameStatus.values();
            gameStatus = (ord >= 0 && ord < vals.length) ? vals[ord] : CheckersGameStatus.PLAYING;
        } else {
            gameStatus = CheckersGameStatus.PLAYING;
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
        int[] vm = tag.getIntArray("ValidMoves");
        if (tag.contains("ValidMoveCount")) {
            validMoveCount = tag.getInt("ValidMoveCount");
        } else {
            validMoveCount = vm.length;
        }
        if (validMoveCount > vm.length) {
            validMoveCount = vm.length;
        }
        if (validMoveCount > validMovesBuf.length) {
            validMoveCount = validMovesBuf.length;
        }
        for (int i = 0; i < validMoveCount; i++) {
            validMovesBuf[i] = vm[i];
        }
        if (!hasSelection()) {
            validMoveCount = 0;
        } else if (level != null && level.isClientSide() && validMoveCount == 0 && gameStatus == CheckersGameStatus.PLAYING) {
            recomputeValidMoves();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeCommonTag(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readCommonTag(tag);
    }
}
