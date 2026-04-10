package net.caduzz.tablecraft.game;

/**
 * Tempo por jogador no relógio (ticks de jogo, 20 TPS).
 */
public final class BoardGameClockConfig {

    /** 5 minutos por jogador. */
    public static final int DEFAULT_PLAYER_TIME_TICKS = 20 * 60 * 5;
    /** Opcoes de tempo por jogador (em minutos). */
    public static final int[] PLAYER_TIME_MINUTES_OPTIONS = new int[] { 3, 5, 10, 15 };

    private BoardGameClockConfig() {}

    public static int ticksFromMinutes(int minutes) {
        return 20 * 60 * minutes;
    }

    public static int closestPresetIndexFromTicks(int ticks) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PLAYER_TIME_MINUTES_OPTIONS.length; i++) {
            int optionTicks = ticksFromMinutes(PLAYER_TIME_MINUTES_OPTIONS[i]);
            int distance = Math.abs(optionTicks - ticks);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
