package net.caduzz.tablecraft.client;

public final class BoardGameHudFormat {

    private BoardGameHudFormat() {}

    public static String formatClockTicks(int ticks) {
        int t = Math.max(0, ticks);
        int seconds = t / 20;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
