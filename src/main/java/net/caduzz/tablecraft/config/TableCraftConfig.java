package net.caduzz.tablecraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Servidor / lógica comum: URL da API e intervalo de polling para partidas online.
 */
public final class TableCraftConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static final ModConfigSpec.IntValue ONLINE_POLL_TICKS;

    static {
        BUILDER.push("online_api");
        API_BASE_URL = BUILDER.comment("Base URL of the external game API (no trailing slash). Example: http://127.0.0.1:8080")
                .define("api_base_url", "http://localhost:3000");
        ONLINE_POLL_TICKS = BUILDER.comment("Server ticks between GET state polls for bound online tables (20 = 1s).")
                .defineInRange("poll_interval_ticks", 40, 10, 400);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private TableCraftConfig() {
    }

    public static String apiBaseUrl() {
        return API_BASE_URL.get().trim().replaceAll("/+$", "");
    }

    public static int onlinePollTicks() {
        return ONLINE_POLL_TICKS.get();
    }
}
