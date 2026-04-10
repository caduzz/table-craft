package net.caduzz.tablecraft.online.api;

import javax.annotation.Nullable;

/**
 * Estado de xadrez da API com metadados opcionais (ex.: lado do jogador autenticado).
 */
public record ChessStateWithMeta(ChessApiSnapshot snapshot, @Nullable String yourSide) {
}
