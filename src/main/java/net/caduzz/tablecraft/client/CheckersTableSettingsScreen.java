package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.caduzz.tablecraft.network.CheckersTableSettingsActionPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/** Menu da mesa de damas (jogo local). */
public class CheckersTableSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int ROW_H = 22;
    private static final int GAP_Y = 6;
    private static final int SECTION_GAP = 10;
    private static final int BOTTOM_PAD = 12;
    private static final int ARROW_BTN_W = 36;
    private static final int ARROW_GAP = 2;
    private static final int TITLE_BELOW_TOP = 26;

    private final BlockPos boardPos;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int timerHintY;
    private int timerLabelY;
    private TableSettingsPanelButton legalHintsButton;
    private Component timerLabel = Component.empty();

    public CheckersTableSettingsScreen(BlockPos boardPos) {
        super(Component.literal("Mesa de damas"));
        this.boardPos = boardPos;
    }

    @Nullable
    private CheckersBlockEntity checkersAtPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        if (!mc.level.getBlockState(boardPos).is(ModBlocks.CHECKERS_BLOCK.get())) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(boardPos);
        return be instanceof CheckersBlockEntity c ? c : null;
    }

    @Override
    protected void init() {
        panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        int btnW = PANEL_WIDTH - 20;
        int btnX = panelLeft + 10;

        panelHeight = TITLE_BELOW_TOP + (ROW_H + GAP_Y) * 2 + SECTION_GAP + 11 + 11 + 6 + ROW_H + GAP_Y + SECTION_GAP + ROW_H + GAP_Y + 10 + ROW_H
                + BOTTOM_PAD;
        panelTop = this.height / 2 - panelHeight / 2;
        int y = panelTop + TITLE_BELOW_TOP;

        legalHintsButton = new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.empty(),
                () -> TableCraftNetworking.sendCheckersTableAction(
                        new CheckersTableSettingsActionPayload(boardPos, CheckersTableSettingsActionPayload.ACTION_TOGGLE_LEGAL_HINTS)),
                TableSettingsPanelButton.Style.ROW);
        addRenderableWidget(legalHintsButton);
        y += ROW_H + GAP_Y + SECTION_GAP;

        timerHintY = y;
        y += 11;
        timerLabelY = y;
        y += 11 + 6;

        addRenderableWidget(new TableSettingsPanelButton(btnX, y, ARROW_BTN_W, ROW_H, Component.literal("<"),
                () -> TableCraftNetworking.sendCheckersTableAction(
                        new CheckersTableSettingsActionPayload(boardPos, CheckersTableSettingsActionPayload.ACTION_TIMER_PREV)),
                TableSettingsPanelButton.Style.COMPACT));
        addRenderableWidget(new TableSettingsPanelButton(btnX + ARROW_BTN_W + ARROW_GAP, y, ARROW_BTN_W, ROW_H, Component.literal(">"),
                () -> TableCraftNetworking.sendCheckersTableAction(
                        new CheckersTableSettingsActionPayload(boardPos, CheckersTableSettingsActionPayload.ACTION_TIMER_NEXT)),
                TableSettingsPanelButton.Style.COMPACT));
        y += ROW_H + GAP_Y + SECTION_GAP;

        addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Reiniciar mesa"),
                () -> TableCraftNetworking.sendCheckersTableAction(
                        new CheckersTableSettingsActionPayload(boardPos, CheckersTableSettingsActionPayload.ACTION_RESET_BOARD)),
                TableSettingsPanelButton.Style.DANGER));
        y += ROW_H + GAP_Y + 10;

        addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Concluído"), this::onClose,
                TableSettingsPanelButton.Style.PRIMARY));

        refreshLabelsFromBoard();
    }

    private void refreshLabelsFromBoard() {
        CheckersBlockEntity checkers = checkersAtPos();
        if (checkers == null) {
            legalHintsButton.setMessage(Component.literal("Jogadas possíveis: —"));
            timerLabel = Component.literal("Tempo: —");
            return;
        }
        legalHintsButton.setMessage(Component.literal("Jogadas possíveis: " + (checkers.showsLegalMoveHints() ? "Sim" : "Não")));
        int idx = checkers.getPlayerTimePresetIndex();
        int[] opts = BoardGameClockConfig.PLAYER_TIME_MINUTES_OPTIONS;
        int minutes = idx >= 0 && idx < opts.length ? opts[idx] : opts[0];
        timerLabel = Component.literal("Tempo por jogador: " + minutes + " min");
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
