package net.caduzz.tablecraft.online.api;

/**
 * Estado de xadrez devolvido pela API (64 ordinais {@link net.caduzz.tablecraft.block.entity.ChessBlockEntity.Piece}).
 */
public record ChessApiSnapshot(int[] boardOrdinals, boolean whiteTurn, String statusKey, int capturedWhite, int capturedBlack,
        Integer lastFromRow, Integer lastFromCol, Integer lastToRow, Integer lastToCol) {

    public ChessApiSnapshot {
        if (boardOrdinals == null || boardOrdinals.length != 64) {
            throw new IllegalArgumentException("board must have 64 cells");
        }
    }
}
