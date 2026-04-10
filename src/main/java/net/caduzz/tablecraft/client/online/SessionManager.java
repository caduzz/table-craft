package net.caduzz.tablecraft.client.online;

/**
 * Sessão TableCraft na API (cliente). Delega na persistência existente; pedidos {@code /me} usam só o header
 * {@code X-TableCraft-Session}.
 */
public final class SessionManager {
    private SessionManager() {
    }

    public static boolean hasSession() {
        return ClientPlayerRegistrationStore.isRegistered();
    }

    public static String getSessionId() {
        return ClientPlayerRegistrationStore.getSessionId();
    }
}
