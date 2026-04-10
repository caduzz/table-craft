package net.caduzz.tablecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.annotation.Nullable;

import net.caduzz.tablecraft.client.online.ClientPlayerRegistrationStore;
import net.caduzz.tablecraft.client.online.SessionManager;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.online.api.GameApiClient;
import net.caduzz.tablecraft.online.api.GameApiException;
import net.caduzz.tablecraft.online.api.MatchHistoryDTO;
import net.caduzz.tablecraft.online.api.PlayerProfileDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Perfil do jogador na API TableCraft: estatísticas e histórico (sessão via {@link SessionManager}).
 */
public class PlayerProfileScreen extends Screen {
    /** Y inicial do bloco de estatísticas (alinhado com {@link #renderHeader}). */
    private static final int HEADER_TOP_Y = 48;
    /** Espaço para Refresh/Done (y ≈ height−32, altura 20) + margem. */
    private static final int BOTTOM_RESERVE = 56;

    private final Screen parent;
    private LoadState loadState = LoadState.LOADING;
    @Nullable
    private Component errorMessage;
    @Nullable
    private PlayerProfileDTO profile;
    private List<MatchHistoryDTO> matches = List.of();
    private double scrollAmount;
    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    /** Ignora respostas HTTP antigas (ex.: duplo Refresh). */
    private int loadGeneration;

    private enum LoadState {
        LOADING,
        READY,
        ERROR
    }

    private record Loaded(PlayerProfileDTO profile, List<MatchHistoryDTO> history) {
    }

    public PlayerProfileScreen(Screen parent) {
        super(Component.translatable("gui.tablecraft.profile.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int btnW = 100;
        int gap = 10;
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.tablecraft.profile.refresh"), b -> loadData())
                .bounds(cx - btnW - gap / 2, this.height - 32, btnW, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.tablecraft.profile.done"), b -> onClose())
                .bounds(cx + gap / 2, this.height - 32, btnW, 20)
                .build());
        layoutListRegion();
        loadData();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        layoutListRegion();
    }

    /** Altura de uma linha do histórico (4 linhas de texto + margens compactas). */
    private int matchRowHeight() {
        int lh = this.font.lineHeight;
        return 6 + 4 * lh + 4;
    }

    private void layoutListRegion() {
        listLeft = 28;
        listWidth = this.width - 56;
        int lh = this.font.lineHeight;
        // Cabeçalho: linhas em y+0, +14, +28, +42 — última baseline em HEADER_TOP_Y+42+lh
        int headerBottom = HEADER_TOP_Y + 42 + lh + 6;
        // Título "Recent matches" em listTop-18; não sobrepor o cabeçalho
        listTop = headerBottom + 18 + lh + 2;
        listHeight = this.height - listTop - BOTTOM_RESERVE;
        listHeight = Math.max(48, listHeight);
    }

    private void loadData() {
        Minecraft mc = Minecraft.getInstance();
        if (!SessionManager.hasSession()) {
            this.loadState = LoadState.ERROR;
            this.errorMessage = Component.translatable("gui.tablecraft.profile.no_session");
            this.profile = null;
            this.matches = List.of();
            return;
        }
        this.loadState = LoadState.LOADING;
        this.errorMessage = null;
        String base = TableCraftConfig.apiBaseUrl();
        String sid = SessionManager.getSessionId();
        final int gen = ++loadGeneration;
        CompletableFuture<PlayerProfileDTO> fProfile = GameApiClient.getMyProfile(base, sid);
        CompletableFuture<List<MatchHistoryDTO>> fHistory = GameApiClient.getMyMatchHistory(base, sid, 20);
        fProfile.thenCombine(fHistory, Loaded::new).whenComplete((loaded, err) -> mc.execute(() -> {
            if (gen != loadGeneration || mc.screen != this) {
                return;
            }
            applyLoadResult(mc, loaded, err);
        }));
    }

    private void applyLoadResult(Minecraft mc, @Nullable Loaded loaded, @Nullable Throwable err) {
        if (err != null) {
            if (isUnauthorized(err)) {
                ClientPlayerRegistrationStore.invalidateSession();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("gui.tablecraft.profile.session_invalid"), false);
                }
                // Nunca saltar para TitleScreen com mundo carregado — crash / estado inválido no cliente.
                if (mc.level == null) {
                    mc.setScreen(new TitleScreen());
                } else {
                    this.loadState = LoadState.ERROR;
                    this.errorMessage = Component.translatable("gui.tablecraft.profile.session_invalid");
                    this.profile = null;
                    this.matches = List.of();
                    this.scrollAmount = 0;
                }
                return;
            }
            this.loadState = LoadState.ERROR;
            this.errorMessage = Component.literal(rootMsg(err));
            this.profile = null;
            this.matches = List.of();
            this.scrollAmount = 0;
            return;
        }
        if (loaded == null) {
            this.loadState = LoadState.ERROR;
            this.errorMessage = Component.translatable("gui.tablecraft.profile.unknown_error");
            return;
        }
        this.loadState = LoadState.READY;
        this.errorMessage = null;
        this.profile = loaded.profile();
        this.matches = new ArrayList<>(loaded.history());
        this.scrollAmount = 0;
    }

    private static boolean isUnauthorized(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof GameApiException ge) {
                int code = ge.httpStatus();
                if (code == 401 || code == 403) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String rootMsg(Throwable err) {
        Throwable c = err;
        if (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof GameApiException ge) {
            return ge.getMessage();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Um único véu escuro (sem blur). O {@link Screen#render} vanilla voltaria a chamar isto + camadas extra — por isso
     * {@link #render} desenha widgets manualmente e não usa {@code super.render}.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD8182430);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= listTop && mouseY < listTop + listHeight && loadState == LoadState.READY) {
            int maxScroll = maxScroll();
            scrollAmount = Mth.clamp(scrollAmount - scrollY * 18, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScroll() {
        int total = matches.size() * matchRowHeight();
        return Math.max(0, total - listHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int pad = 24;
        int frameBottom = this.height - BOTTOM_RESERVE;
        // Só contorno + faixa muito leve no topo — evita segunda camada opaca por cima do texto
        graphics.renderOutline(pad, 16, this.width - pad * 2, frameBottom - 16, 0xFF5a7088);
        graphics.fill(pad + 1, 17, this.width - pad - 1, Math.min(listTop - 6, frameBottom - 2), 0x18081018);

        graphics.drawString(this.font, this.title, pad + 8, 24, 0xFFE8EEF5, false);

        switch (loadState) {
            case LOADING -> graphics.drawCenteredString(this.font, Component.translatable("gui.tablecraft.profile.loading"), this.width / 2,
                    this.height / 2, 0xFFCCCCCC);
            case ERROR -> {
                Component msg = errorMessage != null ? errorMessage : Component.translatable("gui.tablecraft.profile.unknown_error");
                int cy = this.height / 2 - 6;
                for (var line : this.font.split(msg, this.width - pad * 2 - 16)) {
                    graphics.drawString(this.font, line, this.width / 2 - this.font.width(line) / 2, cy, 0xFFFF8888, false);
                    cy += this.font.lineHeight;
                }
            }
            case READY -> {
                if (profile != null) {
                    renderHeader(graphics, pad + 10, 48);
                }
                renderMatchList(graphics, mouseX, mouseY);
            }
        }
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderHeader(GuiGraphics graphics, int x, int y) {
        if (profile == null) {
            return;
        }
        String name = profile.displayName().isEmpty() ? "—" : profile.displayName();
        graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.name", name), x, y, 0xFFE0E8F0, false);
        String ratingStr = String.valueOf(profile.rating());
        Component ratingLabel = Component.translatable("gui.tablecraft.profile.rating_label");
        graphics.drawString(this.font, ratingLabel, x, y + 14, 0xFF9aaaba, false);
        graphics.drawString(this.font, ratingStr, x + this.font.width(ratingLabel) + 6, y + 14, 0xFF66D4AA, false);
        graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.wins_losses", profile.wins(), profile.losses()), x, y + 28,
                0xFFc8d0dc, false);
        String since = formatMemberSince(profile.memberSince());
        graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.member_since", since), x, y + 42, 0xFF8a9aaa, false);
    }

    private static String formatMemberSince(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        String s = raw;
        if (s.length() >= 19 && s.charAt(10) == 'T') {
            s = s.substring(0, 10) + " " + s.substring(11, 19) + (s.length() > 19 && s.endsWith("Z") ? " UTC" : "");
        }
        return s;
    }

    private void renderMatchList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.history_title"), listLeft, listTop - 18, 0xFFccd8e8, false);
        graphics.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight, 0x48080810);
        graphics.renderOutline(listLeft, listTop, listWidth, listHeight, 0xFF4a5a6a);

        int innerX = listLeft + 1;
        int innerY = listTop + 1;
        int innerW = Math.max(0, listWidth - 2);
        int innerH = Math.max(0, listHeight - 2);

        if (matches.isEmpty()) {
            graphics.enableScissor(innerX, innerY, innerW, innerH);
            graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.no_matches"), listLeft + 8, listTop + 10, 0xFFa0a8b0,
                    false);
            graphics.disableScissor();
            return;
        }

        int maxScroll = maxScroll();
        scrollAmount = Mth.clamp(scrollAmount, 0, maxScroll);
        int yBase = listTop - (int) scrollAmount;
        int rowH = matchRowHeight();
        int lh = this.font.lineHeight;
        int gap = 1;

        graphics.enableScissor(innerX, innerY, innerW, innerH);
        try {
            for (int i = 0; i < matches.size(); i++) {
                MatchHistoryDTO m = matches.get(i);
                int y = yBase + i * rowH;
                if (y + rowH <= listTop || y >= listTop + listHeight) {
                    continue;
                }
                int entryLeft = listLeft + 6;
                int entryW = listWidth - 12;
                boolean hover = mouseX >= entryLeft && mouseX < entryLeft + entryW && mouseY >= Math.max(y, listTop)
                        && mouseY < Math.min(y + rowH, listTop + listHeight);
                int bg = hover ? 0x50304050 : 0x280c1018;
                graphics.fill(entryLeft, y, entryLeft + entryW, y + rowH - 1, bg);

                int ty = y + 4;
                String game = formatGameType(m.gameType());
                graphics.drawString(this.font, game, entryLeft + 4, ty, 0xFFd8e4f4, false);
                ty += lh + gap;
                String opp = m.opponentDisplayName().isEmpty() ? "—" : m.opponentDisplayName();
                graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.vs", opp), entryLeft + 4, ty, 0xFFe8eef8, false);
                ty += lh + gap;
                int oc = outcomeColor(m.outcome());
                graphics.drawString(this.font, m.outcome().isEmpty() ? "—" : m.outcome(), entryLeft + 4, ty, oc, false);
                ty += lh + gap;
                String side = formatSide(m.yourSide());
                String when = shortDate(m.createdAt());
                graphics.drawString(this.font, Component.translatable("gui.tablecraft.profile.side_date", side, when), entryLeft + 4, ty,
                        0xFFa8b8c8, false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static String formatGameType(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "CHESS" -> "Chess";
            case "CHECKERS" -> "Checkers";
            default -> raw;
        };
    }

    private static String formatSide(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "WHITE" -> "White";
            case "BLACK" -> "Black";
            default -> raw;
        };
    }

    private static String shortDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        if (raw.length() >= 16 && raw.charAt(10) == 'T') {
            return raw.substring(0, 10) + " " + raw.substring(11, 16);
        }
        return raw;
    }

    private static int outcomeColor(String outcome) {
        String u = outcome == null ? "" : outcome.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "WIN" -> 0xFF55CC66;
            case "LOSS" -> 0xFFFF5555;
            case "DRAW" -> 0xFF999999;
            case "ONGOING" -> 0xFFFFCC66;
            default -> 0xFFCCCCCC;
        };
    }
}
