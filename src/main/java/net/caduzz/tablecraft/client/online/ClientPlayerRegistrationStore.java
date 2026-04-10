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

    public static void saveRegistered(String sessionId, UUID playerUuid) {
        synchronized (LOCK) {
            cached = new Profile(true, sessionId, playerUuid.toString());
            try {
                Path p = profilePath();
                Files.createDirectories(p.getParent());
                Files.writeString(p, GSON.toJson(cached), StandardCharsets.UTF_8);
            } catch (IOException e) {
                TableCraft.LOGGER.error("Failed to save online profile", e);
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

        Profile() {
        }

        Profile(boolean registered, String sessionId, String playerUuid) {
            this.registered = registered;
            this.sessionId = sessionId;
            this.playerUuid = playerUuid;
        }

        static Profile empty() {
            return new Profile(false, "", "");
        }
    }
}
