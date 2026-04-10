package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.client.online.ChessMatchmakingManager;
import net.caduzz.tablecraft.client.online.OnlineAuthCoordinator;
import net.caduzz.tablecraft.client.online.OnlineProfileCache;
import net.caduzz.tablecraft.client.online.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;

/**
 * Perfil + matchmaking de xadrez online (separado das opções visuais da mesa).
 */
public class ChessMatchmakingScreen extends Screen {
    private static final int PANEL_W = 280;

    private final Screen parent;
    private final BlockPos boardPos;
    @Nullable
    private ChessMatchmakingManager matchmakingManager;
    private Button mainActionButton;
    private Button doneButton;

    public ChessMatchmakingScreen(Screen parent, BlockPos boardPos) {
        super(Component.literal("Xadrez — partida online"));
        this.parent = parent;
        this.boardPos = boardPos;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        matchmakingManager = new ChessMatchmakingManager(mc, this::updateSearchButtonLabel, this::onMatchmakingSuccess);
        int left = this.width / 2 - PANEL_W / 2;
        int top = this.height / 2 - 100;
        int btnW = PANEL_W - 20;
        int bx = left + 10;
        int y = top + 120;

        mainActionButton = Button.builder(Component.literal("PROCURAR PARTIDA"), b -> toggleSearch())
                .bounds(bx, y, btnW, 22)
                .build();
        addRenderableWidget(mainActionButton);
        y += 30;
        doneButton = Button.builder(Component.literal("Concluído"), b -> onClose())
                .bounds(bx, y, btnW, 20)
                .build();
        addRenderableWidget(doneButton);

        OnlineAuthCoordinator.refreshProfileAsync(mc);
        updateSearchButtonLabel();
    }

    private void toggleSearch() {
        if (matchmakingManager == null) {
            return;
        }
        if (matchmakingManager.isSearching()) {
            matchmakingManager.cancelSearch();
        } else {
            matchmakingManager.startSearch();
        }
        updateSearchButtonLabel();
    }

    private void updateSearchButtonLabel() {
        if (mainActionButton == null || matchmakingManager == null) {
            return;
        }
        boolean busy = matchmakingManager.isSearching();
        mainActionButton.setMessage(Component.literal(busy ? "CANCELAR" : "PROCURAR PARTIDA"));
        if (doneButton != null) {
            doneButton.active = !busy;
        }
    }

    private void onMatchmakingSuccess(String currentMatchId, String side) {
        Minecraft mc = Minecraft.getInstance();
        String sessionToken = SessionManager.getSessionId();
        ChessOnlineBoardSetup.initBoard(mc, boardPos, sessionToken, currentMatchId, side);
        mc.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (matchmakingManager != null && matchmakingManager.isSearching()) {
            matchmakingManager.cancelSearch();
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD8182430);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - PANEL_W / 2;
        int top = this.height / 2 - 100;
        graphics.fill(left - 4, top - 4, left + PANEL_W + 4, top + 200, 0xE0101418);
        graphics.renderOutline(left - 4, top - 4, PANEL_W + 8, 204, 0xFF5a7088);
        graphics.drawString(this.font, this.title, left + 8, top + 8, 0xFFE8EEF5, false);

        String name = OnlineProfileCache.getCachedDisplayName();
        int rating = OnlineProfileCache.getPlayerRating();
        if (!OnlineProfileCache.isProfileLoaded()) {
            graphics.drawString(this.font, Component.literal("Perfil: a carregar…"), left + 10, top + 32, 0xFFaab6c4, false);
        } else {
            graphics.drawString(this.font, Component.literal("Jogador: " + name), left + 10, top + 32, 0xFFE0E8F0, false);
            graphics.drawString(this.font, Component.literal("Rating: " + rating), left + 10, top + 48, 0xFF66D4AA, false);
        }

        if (matchmakingManager != null) {
            int sy = top + 72;
            int maxW = PANEL_W - 8;
            for (FormattedCharSequence line : this.font.split(matchmakingManager.getStatusLine(), maxW)) {
                graphics.drawString(this.font, line, left + 10, sy, 0xFFCCCCCC, false);
                sy += this.font.lineHeight;
            }
        }

        if (matchmakingManager != null && matchmakingManager.isSearching()) {
            graphics.drawString(this.font, Component.literal("Buscando…"), left + PANEL_W - 72, top + 8, 0xFFFFCC66, false);
        }

        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
