package net.caduzz.tablecraft.online.api;

/** Erro HTTP ou corpo inválido vindo da API (unchecked para encaixar em {@link java.util.concurrent.CompletableFuture}). */
public class GameApiException extends RuntimeException {
    private final int httpStatus;

    public GameApiException(String message) {
        this(message, -1);
    }

    public GameApiException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public GameApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
