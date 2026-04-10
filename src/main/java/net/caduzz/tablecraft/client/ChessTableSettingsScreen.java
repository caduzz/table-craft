package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.client.online.BoardOnlineIntegration;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.caduzz.tablecraft.network.ChessTableSettingsActionPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Menu da mesa: opções offline e secção online (registo, fila, ligação automática a esta mesa).
 */
public class ChessTableSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int ROW_H = 22;
    private static final int GAP_Y = 6;
    private static final int SECTION_GAP = 10;
    private static final int BOTTOM_PAD = 12;
    private static final int ARROW_BTN_W = 36;
    private static final int ARROW_GAP = 2;
    private static final int TITLE_BELOW_TOP = 26;
    private static final int STATUS_RESERVE = 52;

    private final BlockPos boardPos;
    private final boolean onlineMode;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int timerHintY;
    private int timerLabelY;
    private int onlineStatusTextY;
    private TableSettingsPanelButton previousMoveButton;
    private TableSettingsPanelButton legalHintsButton;
    private Component timerLabel = Component.empty();
    @Nullable
    private BoardOnlineIntegration onlineIntegration;

    public ChessTableSettingsScreen(BlockPos boardPos) {
        this(boardPos, false);
    }

    public ChessTableSettingsScreen(BlockPos boardPos, boolean onlineMode) {
        super(Component.literal(onlineMode ? "Xadrez — online" : "Mesa de xadrez"));
        this.boardPos = boardPos;
        this.onlineMode = onlineMode;
    }

    @Nullable
    private ChessBlockEntity chessAtPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        if (!mc.level.getBlockState(boardPos).is(ModBlocks.CHESS_BLOCK.get())) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(boardPos);
        return be instanceof ChessBlockEntity c ? c : null;
    }

    @Override
    protected void init() {
        onlineIntegration = onlineMode ? new BoardOnlineIntegration(boardPos) : null;

        panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        int btnW = PANEL_WIDTH - 20;
        int btnX = panelLeft + 10;

        if (onlineMode) {
            int contentH = TITLE_BELOW_TOP + (ROW_H + GAP_Y) * 4 + 8 + ROW_H + STATUS_RESERVE + BOTTOM_PAD;
            panelHeight = contentH;
            panelTop = this.height / 2 - panelHeight / 2;
            int y = panelTop + TITLE_BELOW_TOP;

            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Voltar às opções da mesa"),
                    () -> Minecraft.getInstance().setScreen(new ChessTableSettingsScreen(boardPos, false)), TableSettingsPanelButton.Style.ROW));
            y += ROW_H + GAP_Y;

            if (onlineIntegration != null) {
                addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Registrar na API"),
                        () -> onlineIntegration.register(Minecraft.getInstance()), TableSettingsPanelButton.Style.ROW));
                y += ROW_H + GAP_Y;
                addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Buscar partida (fila)"),
                        () -> onlineIntegration.startMatchmaking(Minecraft.getInstance()), TableSettingsPanelButton.Style.ROW));
                y += ROW_H + GAP_Y;
                addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Desligar online nesta mesa"),
                        () -> onlineIntegration.clearOnlineOnThisBoard(Minecraft.getInstance()), TableSettingsPanelButton.Style.DANGER));
                y += ROW_H + GAP_Y + 8;
            }
            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Concluído"), this::onClose,
                    TableSettingsPanelButton.Style.PRIMARY));
            onlineStatusTextY = y + ROW_H + 8;
        } else {
            panelHeight = TITLE_BELOW_TOP + (ROW_H + GAP_Y) * 3 + SECTION_GAP + 11 + 11 + 6 + ROW_H + GAP_Y + SECTION_GAP + ROW_H + GAP_Y + 10 + ROW_H
                    + BOTTOM_PAD;
            panelTop = this.height / 2 - panelHeight / 2;
            int y = panelTop + TITLE_BELOW_TOP;

            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Jogar online (fila API)"),
                    () -> Minecraft.getInstance().setScreen(new ChessTableSettingsScreen(boardPos, true)), TableSettingsPanelButton.Style.ROW));
            y += ROW_H + GAP_Y;

            previousMoveButton = new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.empty(), () -> TableCraftNetworking.sendChessTableAction(
                    new ChessTableSettingsActionPayload(boardPos, ChessTableSettingsActionPayload.ACTION_TOGGLE_PREVIOUS_MOVE)),
                    TableSettingsPanelButton.Style.ROW);
            addRenderableWidget(previousMoveButton);
            y += ROW_H + GAP_Y;

            legalHintsButton = new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.empty(), () -> TableCraftNetworking.sendChessTableAction(
                    new ChessTableSettingsActionPayload(boardPos, ChessTableSettingsActionPayload.ACTION_TOGGLE_LEGAL_HINTS)),
                    TableSettingsPanelButton.Style.ROW);
            addRenderableWidget(legalHintsButton);
            y += ROW_H + GAP_Y + SECTION_GAP;

            timerHintY = y;
            y += 11;
            timerLabelY = y;
            y += 11 + 6;

            addRenderableWidget(new TableSettingsPanelButton(btnX, y, ARROW_BTN_W, ROW_H, Component.literal("<"),
                    () -> TableCraftNetworking.sendChessTableAction(
                            new ChessTableSettingsActionPayload(boardPos, ChessTableSettingsActionPayload.ACTION_TIMER_PREV)),
                    TableSettingsPanelButton.Style.COMPACT));
            addRenderableWidget(new TableSettingsPanelButton(btnX + ARROW_BTN_W + ARROW_GAP, y, ARROW_BTN_W, ROW_H, Component.literal(">"),
                    () -> TableCraftNetworking.sendChessTableAction(
                            new ChessTableSettingsActionPayload(boardPos, ChessTableSettingsActionPayload.ACTION_TIMER_NEXT)),
                    TableSettingsPanelButton.Style.COMPACT));
            y += ROW_H + GAP_Y + SECTION_GAP;

            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Reiniciar mesa"),
                    () -> TableCraftNetworking.sendChessTableAction(
                            new ChessTableSettingsActionPayload(boardPos, ChessTableSettingsActionPayload.ACTION_RESET_BOARD)),
                    TableSettingsPanelButton.Style.DANGER));
            y += ROW_H + GAP_Y + 10;

            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Concluído"), this::onClose,
                    TableSettingsPanelButton.Style.PRIMARY));
            onlineStatusTextY = 0;
        }

        refreshLabelsFromBoard();
    }

    @Override
    public void removed() {
        super.removed();
        if (onlineIntegration != null) {
            onlineIntegration.cancelMatchmakingIfLeaving();
        }
    }

    private void refreshLabelsFromBoard() {
        if (onlineMode) {
            return;
        }
        ChessBlockEntity chess = chessAtPos();
        if (chess == null) {
            if (previousMoveButton != null) {
                previousMoveButton.setMessage(Component.literal("Último movimento: —"));
            }
            if (legalHintsButton != null) {
                legalHintsButton.setMessage(Component.literal("Jogadas possíveis: —"));
            }
            timerLabel = Component.literal("Tempo: —");
            return;
        }
        if (previousMoveButton != null) {
            previousMoveButton.setMessage(Component.literal("Último movimento: " + yesNo(chess.showsPreviousMove())));
        }
        if (legalHintsButton != null) {
            legalHintsButton.setMessage(Component.literal("Jogadas possíveis: " + yesNo(chess.showsLegalMoveHints())));
        }
        int idx = chess.getPlayerTimePresetIndex();
        int[] opts = BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS;
        int minutes = idx >= 0 && idx < opts.length ? opts[idx] : opts[0];
        timerLabel = Component.literal("Tempo por jogador: " + minutes + " min");
    }

    private static String yesNo(boolean v) {
        return v ? "Sim" : "Não";
    }

    @Override
    public void tick() {
        super.tick();
        refreshLabelsFromBoard();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft - 4, panelTop - 4, panelLeft + PANEL_WIDTH + 4, panelTop + panelHeight + 4, 0xE0101018);
        graphics.renderOutline(panelLeft - 4, panelTop - 4, PANEL_WIDTH + 8, panelHeight + 8, 0xFF5c6b7a);
        graphics.drawString(this.font, this.title, panelLeft + 10, panelTop + 8, 0xFFE8EEF5, false);
        if (onlineMode && onlineIntegration != null) {
            if (onlineIntegration.isBusy()) {
                graphics.drawString(this.font, Component.literal("…"), panelLeft + PANEL_WIDTH - 18, panelTop + 8, 0xFFFFAA, false);
            }
            int sy = onlineStatusTextY;
            int textMaxW = PANEL_WIDTH - 8;
            for (FormattedCharSequence line : this.font.split(onlineIntegration.getStatusLine(), textMaxW)) {
                graphics.drawString(this.font, line, panelLeft + 10, sy, 0xFFCCCCCC, false);
                sy += this.font.lineHeight;
            }
        } else {
            graphics.drawString(this.font, Component.literal("Relógio — ajuste antes da partida"), panelLeft + 10, timerHintY, 0xFF8a9aaa, false);
            graphics.drawString(this.font, timerLabel, panelLeft + 10, timerLabelY, 0xFFD0D8E0, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
