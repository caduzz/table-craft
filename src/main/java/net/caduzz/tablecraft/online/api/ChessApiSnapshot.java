package net.caduzz.tablecraft.online.api;

import javax.annotation.Nullable;

/**
 * Estado de xadrez devolvido pela API (64 ordinais {@link net.caduzz.tablecraft.block.entity.ChessBlockEntity.Piece}).
 */
public record ChessApiSnapshot(int[] boardOrdinals, boolean whiteTurn, String statusKey, int capturedWhite, int capturedBlack,
        Integer lastFromRow, Integer lastFromCol, Integer lastToRow, Integer lastToCol,
        @Nullable String whiteDisplayName,
        @Nullable String blackDisplayName,
        /** Perspectiva da sessão {@code X-TableCraft-Session} usada no GET/POST (pode ser null na API). */
        @Nullable String playerDisplayName,
        @Nullable String playerPlayerUuid,
        @Nullable String opponentDisplayName,
        @Nullable String opponentPlayerUuid) {

    public ChessApiSnapshot {
        if (boardOrdinals == null || boardOrdinals.length != 64) {
            throw new IllegalArgumentException("board must have 64 cells");
        }
    }
}
