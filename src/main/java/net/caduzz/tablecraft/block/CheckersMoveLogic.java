package net.caduzz.tablecraft.block;

import java.util.function.IntConsumer;

import net.caduzz.tablecraft.block.entity.CheckersBlockEntity.Piece;

/**
 * Movimento simples (1 diagonal) e captura (2 diagonais, inimigo no meio).
 * Se existir qualquer captura para o jogador, só capturas são legais.
 */
public final class CheckersMoveLogic {

    private CheckersMoveLogic() {
    }

    public static boolean isWhite(Piece p) {
        return p == Piece.WHITE_MAN || p == Piece.WHITE_KING;
    }

    public static boolean isDarkSquare(int row, int col) {
        return ((row + col) & 1) == 1;
    }

    public static boolean canSimpleStep(Piece[][] board, int fr, int fc, int tr, int tc, boolean whiteTurn) {
        Piece p = board[fr][fc];
        if (p == Piece.EMPTY || isWhite(p) != whiteTurn) {
            return false;
        }
        if (board[tr][tc] != Piece.EMPTY) {
            return false;
        }
        if (!isDarkSquare(tr, tc)) {
            return false;
        }
        int dr = Math.abs(tr - fr);
        int dc = Math.abs(tc - fc);
        if (dr != 1 || dc != 1) {
            return false;
        }
        // Peão: só avança (brancas para row menor, pretas para row maior). Dama: qualquer diagonal.
        if (p == Piece.WHITE_MAN && tr >= fr) {
            return false;
        }
        if (p == Piece.BLACK_MAN && tr <= fr) {
            return false;
        }
        return true;
    }

    /**
     * Salto de 2 casas: casa final escura vazia, peça adversária no centro exato.
     */
    public static boolean canCaptureJump(Piece[][] board, int fr, int fc, int tr, int tc, boolean whiteTurn) {
        Piece p = board[fr][fc];
        if (p == Piece.EMPTY || isWhite(p) != whiteTurn) {
            return false;
        }
        int dr = tr - fr;
        int dc = tc - fc;
        if (Math.abs(dr) != 2 || Math.abs(dc) != 2) {
            return false;
        }
        if (!isDarkSquare(tr, tc)) {
            return false;
        }
        if (board[tr][tc] != Piece.EMPTY) {
            return false;
        }
        int mr = (fr + tr) / 2;
        int mc = (fc + tc) / 2;
        Piece mid = board[mr][mc];
        if (mid == Piece.EMPTY || isWhite(mid) == isWhite(p)) {
            return false;
        }
        return true;
    }

    public static boolean pieceHasAnyCapture(Piece[][] board, int fr, int fc, boolean whiteTurn) {
        for (int dr = -2; dr <= 2; dr += 4) {
            for (int dc = -2; dc <= 2; dc += 4) {
                int tr = fr + dr;
                int tc = fc + dc;
                if (tr >= 0 && tr <= 7 && tc >= 0 && tc <= 7) {
                    if (canCaptureJump(board, fr, fc, tr, tc, whiteTurn)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean playerHasAnyCapture(Piece[][] board, boolean whiteTurn) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p != Piece.EMPTY && isWhite(p) == whiteTurn) {
                    if (pieceHasAnyCapture(board, r, c, whiteTurn)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void collectSimpleMoves(Piece[][] board, int fr, int fc, boolean whiteTurn, IntConsumer packedCellConsumer) {
        Piece p = board[fr][fc];
        if (p == Piece.EMPTY || isWhite(p) != whiteTurn) {
            return;
        }
        for (int dr = -1; dr <= 1; dr += 2) {
            for (int dc = -1; dc <= 1; dc += 2) {
                int tr = fr + dr;
                int tc = fc + dc;
                if (tr < 0 || tr > 7 || tc < 0 || tc > 7) {
                    continue;
                }
                if (canSimpleStep(board, fr, fc, tr, tc, whiteTurn)) {
                    packedCellConsumer.accept(tr * 8 + tc);
                }
            }
        }
    }

    public static void collectCaptureMoves(Piece[][] board, int fr, int fc, boolean whiteTurn, IntConsumer packedCellConsumer) {
        Piece p = board[fr][fc];
        if (p == Piece.EMPTY || isWhite(p) != whiteTurn) {
            return;
        }
        for (int dr = -2; dr <= 2; dr += 4) {
            for (int dc = -2; dc <= 2; dc += 4) {
                int tr = fr + dr;
                int tc = fc + dc;
                if (tr < 0 || tr > 7 || tc < 0 || tc > 7) {
                    continue;
                }
                if (canCaptureJump(board, fr, fc, tr, tc, whiteTurn)) {
                    packedCellConsumer.accept(tr * 8 + tc);
                }
            }
        }
    }

    /**
     * Lista legal para a peça selecionada: só capturas se o jogador tiver captura obrigatória; senão só passos simples.
     */
    public static void collectLegalMovesForPiece(Piece[][] board, int fr, int fc, boolean whiteTurn, IntConsumer packedCellConsumer) {
        if (playerHasAnyCapture(board, whiteTurn)) {
            if (pieceHasAnyCapture(board, fr, fc, whiteTurn)) {
                collectCaptureMoves(board, fr, fc, whiteTurn, packedCellConsumer);
            }
        } else {
            collectSimpleMoves(board, fr, fc, whiteTurn, packedCellConsumer);
        }
    }

    public static boolean isLegalMoveForPiece(Piece[][] board, int fr, int fc, int tr, int tc, boolean whiteTurn) {
        if (playerHasAnyCapture(board, whiteTurn)) {
            return canCaptureJump(board, fr, fc, tr, tc, whiteTurn);
        }
        return canSimpleStep(board, fr, fc, tr, tc, whiteTurn);
    }

    public static int countWhitePieces(Piece[][] board) {
        int n = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p != Piece.EMPTY && isWhite(p)) {
                    n++;
                }
            }
        }
        return n;
    }

    public static int countBlackPieces(Piece[][] board) {
        int n = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p != Piece.EMPTY && !isWhite(p)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** O jogador da vez tem pelo menos um lance legal em alguma peça? */
    public static boolean currentPlayerHasAnyMove(Piece[][] board, boolean whiteTurn) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p != Piece.EMPTY && isWhite(p) == whiteTurn) {
                    boolean[] any = { false };
                    collectLegalMovesForPiece(board, r, c, whiteTurn, packed -> any[0] = true);
                    if (any[0]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
