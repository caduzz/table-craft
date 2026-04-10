package net.caduzz.tablecraft.online;

public enum OnlineSide {
    WHITE,
    BLACK;

    public static OnlineSide fromApi(String s) {
        if (s == null) {
            return WHITE;
        }
        return "BLACK".equalsIgnoreCase(s) ? BLACK : WHITE;
    }
}
