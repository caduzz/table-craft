package net.caduzz.tablecraft.game;

/**
 * Tempo por jogador no relógio (ticks de jogo, 20 TPS).
 */
public final class BoardGameClockConfig {

    /** 5 minutos por jogador. */
    public static final int DEFAULT_PLAYER_TIME_TICKS = 20 * 60 * 5;

    private BoardGameClockConfig() {}
}
