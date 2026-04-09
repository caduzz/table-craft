package net.caduzz.tablecraft.block;

import java.util.function.IntConsumer;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity.Piece;

public final class ChessMoveLogic {
    private ChessMoveLogic() {
    }

    public static boolean isWhite(Piece p) {
        return switch (p) {
            case WHITE_PAWN, WHITE_ROOK, WHITE_KNIGHT, WHITE_BISHOP, WHITE_QUEEN, WHITE_KING -> true;
            default -> false;
        };
    }

    public static boolean isLegalMove(Piece[][] board, int fr, int fc, int tr, int tc, boolean whiteTurn) {
        if (!isInside(fr, fc) || !isInside(tr, tc) || (fr == tr && fc == tc)) {
            return false;
        }
        Piece from = board[fr][fc];
        if (from == Piece.EMPTY || isWhite(from) != whiteTurn) {
            return false;
        }
        Piece to = board[tr][tc];
        if (to != Piece.EMPTY && isWhite(to) == whiteTurn) {
            return false;
        }

        int dr = tr - fr;
        int dc = tc - fc;
        int adr = Math.abs(dr);
        int adc = Math.abs(dc);

        return switch (from) {
            case WHITE_PAWN -> isLegalPawnMove(board, fr, fc, tr, tc, -1);
            case BLACK_PAWN -> isLegalPawnMove(board, fr, fc, tr, tc, 1);
            case WHITE_ROOK, BLACK_ROOK -> (dr == 0 || dc == 0) && isPathClear(board, fr, fc, tr, tc);
            case WHITE_BISHOP, BLACK_BISHOP -> adr == adc && isPathClear(board, fr, fc, tr, tc);
            case WHITE_QUEEN, BLACK_QUEEN -> ((adr == adc) || (dr == 0 || dc == 0)) && isPathClear(board, fr, fc, tr, tc);
            case WHITE_KNIGHT, BLACK_KNIGHT -> (adr == 2 && adc == 1) || (adr == 1 && adc == 2);
            case WHITE_KING, BLACK_KING -> adr <= 1 && adc <= 1;
            default -> false;
        };
    }

    public static void collectLegalMovesForPiece(Piece[][] board, int fr, int fc, boolean whiteTurn, IntConsumer packedCellConsumer) {
        if (!isInside(fr, fc)) {
            return;
        }
        Piece p = board[fr][fc];
        if (p == Piece.EMPTY || isWhite(p) != whiteTurn) {
            return;
        }
        for (int tr = 0; tr < 8; tr++) {
            for (int tc = 0; tc < 8; tc++) {
                if (isLegalMove(board, fr, fc, tr, tc, whiteTurn)) {
                    packedCellConsumer.accept(tr * 8 + tc);
                }
            }
        }
    }

    public static boolean currentPlayerHasAnyMove(Piece[][] board, boolean whiteTurn) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == Piece.EMPTY || isWhite(p) != whiteTurn) {
                    continue;
                }
                final boolean[] any = { false };
                collectLegalMovesForPiece(board, r, c, whiteTurn, packed -> any[0] = true);
                if (any[0]) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasKing(Piece[][] board, boolean whiteKing) {
        Piece king = whiteKing ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == king) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLegalPawnMove(Piece[][] board, int fr, int fc, int tr, int tc, int dir) {
        int dr = tr - fr;
        int dc = tc - fc;
        Piece dst = board[tr][tc];
        if (dc == 0 && dst == Piece.EMPTY) {
            if (dr == dir) {
                return true;
            }
            boolean startRow = (dir < 0 && fr == 6) || (dir > 0 && fr == 1);
            if (startRow && dr == 2 * dir && board[fr + dir][fc] == Piece.EMPTY) {
                return true;
            }
            return false;
        }
        return Math.abs(dc) == 1 && dr == dir && dst != Piece.EMPTY;
    }

    private static boolean isPathClear(Piece[][] board, int fr, int fc, int tr, int tc) {
        int stepR = Integer.compare(tr, fr);
        int stepC = Integer.compare(tc, fc);
        int r = fr + stepR;
        int c = fc + stepC;
        while (r != tr || c != tc) {
            if (board[r][c] != Piece.EMPTY) {
                return false;
            }
            r += stepR;
            c += stepC;
        }
        return true;
    }

    private static boolean isInside(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }
}
