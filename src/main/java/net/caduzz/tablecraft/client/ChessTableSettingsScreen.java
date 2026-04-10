package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.caduzz.tablecraft.network.ChessTableSettingsActionPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Menu compacto da mesa: sem blur de fundo e botões com visual próprio.
 */
public class ChessTableSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int ROW_H = 22;
    private static final int GAP_Y = 6;
    private static final int SECTION_GAP = 10;
    private static final int BOTTOM_PAD = 12;
    private static final int ARROW_BTN_W = 36;
    private static final int ARROW_GAP = 2;

    private final BlockPos boardPos;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int timerHintY;
    private int timerLabelY;
    private TableSettingsPanelButton previousMoveButton;
    private TableSettingsPanelButton legalHintsButton;
    private Component timerLabel = Component.empty();

    public ChessTableSettingsScreen(BlockPos boardPos) {
        super(Component.literal("Mesa de xadrez"));
        this.boardPos = boardPos;
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
        panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        int btnW = PANEL_WIDTH - 20;
        int btnX = panelLeft + 10;

        final int yRel = 26;
        final int relAfterToggles = yRel + (ROW_H + GAP_Y) * 2;
        final int relTimerHint = relAfterToggles + SECTION_GAP;
        final int relTimerLabel = relTimerHint + 11;
        final int relArrows = relTimerLabel + 11 + 6;
        final int relAfterArrows = relArrows + ROW_H;
        final int relReset = relAfterArrows + GAP_Y + SECTION_GAP;
        final int relAfterReset = relReset + ROW_H;
        final int relDone = relAfterReset + GAP_Y + 10;
        panelHeight = relDone + ROW_H + BOTTOM_PAD;
        panelTop = this.height / 2 - panelHeight / 2;

        int y = panelTop + yRel;

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

        refreshLabelsFromBoard();
    }

    private void refreshLabelsFromBoard() {
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
        graphics.drawString(this.font, Component.literal("Relógio — ajuste antes da partida"), panelLeft + 10, timerHintY, 0xFF8a9aaa, false);
        graphics.drawString(this.font, timerLabel, panelLeft + 10, timerLabelY, 0xFFD0D8E0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
