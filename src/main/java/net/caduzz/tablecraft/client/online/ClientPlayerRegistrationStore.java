package net.caduzz.tablecraft.client.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.caduzz.tablecraft.TableCraft;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Persistência local (cliente): registo na API e {@code sessionId} devolvido pelo servidor.
 */
public final class ClientPlayerRegistrationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static volatile Profile cached = Profile.empty();

    private ClientPlayerRegistrationStore() {
    }

    public static void loadFromDisk() {
        synchronized (LOCK) {
            Path p = profilePath();
            if (!Files.isRegularFile(p)) {
                cached = Profile.empty();
                return;
            }
            try {
                String json = Files.readString(p, StandardCharsets.UTF_8);
                Profile pr = GSON.fromJson(json, Profile.class);
                if (pr != null && pr.lastChessMatchId == null) {
                    pr.lastChessMatchId = "";
                }
                if (pr != null && pr.lastChessSide == null) {
                    pr.lastChessSide = "";
                }
                cached = pr != null ? pr : Profile.empty();
            } catch (Exception e) {
                TableCraft.LOGGER.warn("Could not read online profile: {}", e.toString());
                cached = Profile.empty();
            }
        }
    }

    public static boolean isRegistered() {
        return cached.registered && cached.sessionId != null && !cached.sessionId.isEmpty();
    }

    public static String getSessionId() {
        return cached.sessionId == null ? "" : cached.sessionId;
    }

    /**
     * {@code false} se existir sessão gravada para outro {@link UUID} (ex.: mudou de conta no launcher).
     */
    public static boolean storedSessionMatchesCurrentPlayer(UUID playerUuid) {
        synchronized (LOCK) {
            if (!cached.registered || cached.sessionId == null || cached.sessionId.isEmpty()) {
                return true;
            }
            if (cached.playerUuid == null || cached.playerUuid.isBlank()) {
                return true;
            }
            return cached.playerUuid.equalsIgnoreCase(playerUuid.toString());
        }
    }

    public static void saveRegistered(String sessionId, UUID playerUuid) {
        synchronized (LOCK) {
            String keepMatch = cached.lastChessMatchId != null && !cached.lastChessMatchId.isEmpty() ? cached.lastChessMatchId : "";
            String keepSide = cached.lastChessSide != null && !cached.lastChessSide.isEmpty() ? cached.lastChessSide : "";
            cached = new Profile(true, sessionId, playerUuid.toString(), keepMatch, keepSide);
            try {
                Path p = profilePath();
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(cached), StandardCharsets.UTF_8);
            } catch (IOException e) {
                TableCraft.LOGGER.error("Failed to save online profile", e);
            }
        }
    }

    public static String getLastChessMatchId() {
        return cached.lastChessMatchId == null ? "" : cached.lastChessMatchId;
    }

    /**
     * Atualiza só o id da última partida; mantém {@link #getLastChessSide()} se já estiver definido.
     */
    public static void setLastChessMatchId(String matchId) {
        synchronized (LOCK) {
            String mid = matchId == null ? "" : matchId.trim();
            String keepSide = cached.lastChessSide != null ? cached.lastChessSide : "";
            cached = new Profile(cached.registered, cached.sessionId, cached.playerUuid, mid, keepSide);
            try {
                Path p = profilePath();
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(cached), StandardCharsets.UTF_8);
            } catch (IOException e) {
                TableCraft.LOGGER.error("Failed to save online profile", e);
            }
        }
    }

    /** Persiste última partida online e o lado do jogador (para retomar após queda de rede / sair do jogo). */
    public static void setLastChessOnlineBinding(String matchId, String sideLabel) {
        synchronized (LOCK) {
            String mid = matchId == null ? "" : matchId.trim();
            String side = normalizeStoredSide(sideLabel);
            cached = new Profile(cached.registered, cached.sessionId, cached.playerUuid, mid, side);
            try {
                Path p = profilePath();
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(cached), StandardCharsets.UTF_8);
            } catch (IOException e) {
                TableCraft.LOGGER.error("Failed to save online profile", e);
            }
        }
    }

    public static String getLastChessSide() {
        return cached.lastChessSide == null ? "" : cached.lastChessSide;
    }

    private static String normalizeStoredSide(String sideLabel) {
        if (sideLabel == null) {
            return "";
        }
        String t = sideLabel.trim();
        if (t.equalsIgnoreCase("BLACK")) {
            return "BLACK";
        }
        if (t.equalsIgnoreCase("WHITE")) {
            return "WHITE";
        }
        return "";
    }

    /** Limpa sessão inválida (ex.: 401 na API) e persiste perfil vazio. */
    public static void invalidateSession() {
        synchronized (LOCK) {
            OnlineProfileCache.clear();
            cached = Profile.empty();
            try {
                Path p = profilePath();
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(cached), StandardCharsets.UTF_8);
            } catch (IOException e) {
                TableCraft.LOGGER.error("Failed to persist cleared session", e);
            }
        }
    }

    private static Path profilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(TableCraft.MOD_ID).resolve("online_profile.json");
    }

    public static final class Profile {
        public boolean registered;
        public String sessionId;
        public String playerUuid;
        /** Último match de xadrez ligado a uma mesa (cliente); usado para «verificar partida» sem a mesa estar aberta. */
        public String lastChessMatchId;
        /** {@code WHITE} ou {@code BLACK} para o último {@link #lastChessMatchId}. */
        public String lastChessSide;

        Profile() {
        }

        Profile(boolean registered, String sessionId, String playerUuid, String lastChessMatchId, String lastChessSide) {
            this.registered = registered;
            this.sessionId = sessionId;
            this.playerUuid = playerUuid;
            this.lastChessMatchId = lastChessMatchId != null ? lastChessMatchId : "";
            this.lastChessSide = lastChessSide != null ? lastChessSide : "";
        }

        static Profile empty() {
            return new Profile(false, "", "", "", "");
        }
    }
}
