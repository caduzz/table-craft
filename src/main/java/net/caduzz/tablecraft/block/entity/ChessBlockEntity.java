package net.caduzz.tablecraft.block.entity;

import java.util.Arrays;
import java.util.UUID;
import org.joml.Vector2d;
import net.caduzz.tablecraft.block.ChessBlock;
import net.caduzz.tablecraft.block.ChessMoveLogic;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
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
        BLACK_WIN
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

    /** Origem/destino vazios no tabuleiro até o fim da animação; a peça é desenhada só no cliente interpolando. */
    private int animFromRow;
    private int animFromCol;
    private int animToRow;
    private int animToCol;
    private Piece animPiece = Piece.EMPTY;
    private long animStartGameTime = -1L;

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

    public void resetToStartingPosition() {
        for (Piece[] row : board) {
            Arrays.fill(row, Piece.EMPTY);
        }
        board[0] = new Piece[] { Piece.BLACK_ROOK, Piece.BLACK_KNIGHT, Piece.BLACK_BISHOP, Piece.BLACK_QUEEN, Piece.BLACK_KING,
                Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT, Piece.BLACK_ROOK };
        Arrays.fill(board[1], Piece.BLACK_PAWN);
        Arrays.fill(board[6], Piece.WHITE_PAWN);
        board[7] = new Piece[] { Piece.WHITE_ROOK, Piece.WHITE_KNIGHT, Piece.WHITE_BISHOP, Piece.WHITE_QUEEN, Piece.WHITE_KING,
                Piece.WHITE_BISHOP, Piece.WHITE_KNIGHT, Piece.WHITE_ROOK };
        whiteTurn = true;
        selRow = -1;
        selCol = -1;
        validMoveCount = 0;
        gameStatus = ChessGameStatus.PLAYING;
        resetAtGameTime = -1L;
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
        if (!canPlayerControlCurrentTurn(player)) {
            player.displayClientMessage(Component.literal(whiteTurn ? "Só as brancas jogam agora." : "Só as pretas jogam agora."), true);
            return;
        }
        int[] cell = hitToCell(hit);
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
        Piece moving = animPiece;
        Piece placed = promoteIfNeeded(moving, tr);
        board[tr][tc] = placed;
        clearAnimation();

        boolean whiteKingAlive = ChessMoveLogic.hasKing(board, true);
        boolean blackKingAlive = ChessMoveLogic.hasKing(board, false);
        if (!whiteKingAlive) {
            endGame(ChessGameStatus.BLACK_WIN);
        } else if (!blackKingAlive) {
            endGame(ChessGameStatus.WHITE_WIN);
        } else {
            whiteTurn = !whiteTurn;
            if (!ChessMoveLogic.currentPlayerHasAnyMove(board, whiteTurn)) {
                endGame(whiteTurn ? ChessGameStatus.BLACK_WIN : ChessGameStatus.WHITE_WIN);
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
        } else {
            lastWinnerDisplayName = gameSeatBlackName == null || gameSeatBlackName.isEmpty() ? "Pretas" : gameSeatBlackName;
        }
        if (level != null) {
            Component msg = announceOverride != null ? announceOverride
                    : Component.literal(status == ChessGameStatus.WHITE_WIN ? "Brancas venceram no xadrez!" : "Pretas venceram no xadrez!");
            Vec3 center = Vec3.atCenterOf(worldPosition);
            for (Player p : level.players()) {
                if (p.distanceToSqr(center) <= 24.0 * 24.0) {
                    p.displayClientMessage(msg, false);
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
        UUID id = player.getUUID();
        if (whiteTurn) {
            return gameSeatWhiteUuid == null || gameSeatWhiteUuid.equals(id);
        }
        return gameSeatBlackUuid == null || gameSeatBlackUuid.equals(id);
    }

    private void tryRegisterGameSeat(Player player) {
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

    private int[] hitToCell(BlockHitResult hit) {
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
        int col = Mth.clamp((int) (logical.x * 8), 0, 7);
        int row = Mth.clamp((int) (logical.y * 8), 0, 7);
        return new int[] { row, col };
    }

    private void recomputeValidMoves() {
        validMoveCount = 0;
        if (!hasSelection() || gameStatus != ChessGameStatus.PLAYING) {
            return;
        }
        ChessMoveLogic.collectLegalMovesForPiece(board, selRow, selCol, whiteTurn, packed -> {
            if (validMoveCount < validMovesBuf.length) {
                validMovesBuf[validMoveCount++] = packed;
            }
        });
    }

    private void clearSelection() {
        selRow = -1;
        selCol = -1;
        validMoveCount = 0;
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

    public boolean isRegisteredParticipant(UUID playerId) {
        if (playerId == null) {
            return false;
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
        tag.putBoolean("AnimActive", hasActiveAnimation());
        if (hasActiveAnimation()) {
            tag.putInt("AnimFromR", animFromRow);
            tag.putInt("AnimFromC", animFromCol);
            tag.putInt("AnimToR", animToRow);
            tag.putInt("AnimToC", animToCol);
            tag.putInt("AnimPiece", animPiece.ordinal());
            tag.putLong("AnimStart", animStartGameTime);
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
    }
}
