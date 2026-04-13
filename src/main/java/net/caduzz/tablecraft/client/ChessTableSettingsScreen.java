package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.game.BoardGameClockConfig;
import net.caduzz.tablecraft.network.ChessTableSettingsActionPayload;
import net.caduzz.tablecraft.network.OnlineTableClearPayload;
import net.caduzz.tablecraft.network.OnlineTableBindPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Menu da mesa: painel centrado; slots de opções (lã) fora do painel, acima, alinhados ao início da coluna de conteúdo.
 * Matchmaking em {@link ChessMatchmakingScreen}.
 */
public class ChessTableSettingsScreen extends Screen {
    private static final int CONTENT_W = 248;
    private static final int OUTER_PAD_H = 12;
    /** Contorno do painel (mesmo inseto que o fill). */
    private static final int FRAME_OUTSET = 0;
    private static final int NAV_SLOT_GAP = 6;
    /** Espaço entre o contorno do painel e a faixa de nav (fora do menu). */
    private static final int NAV_OUTSIDE_GAP = 0;
    private static final int ROW_H = 22;
    private static final int GAP_Y = 2;
    private static final int SECTION_GAP = 10;
    private static final int BOTTOM_PAD = 12;
    private static final int ARROW_BTN_W = 36;
    private static final int ARROW_GAP = 2;
    private static final int TITLE_BELOW_TOP = 26;

    private enum PanelSection {
        OFFLINE,
        ONLINE
    }

    private final BlockPos boardPos;
    private PanelSection section = PanelSection.OFFLINE;

    /** Retângulo do painel principal; slots de nav acima do painel, alinhados a {@link #contentLeft}. */
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int contentLeft;
    private int navOfflineX;
    private int navOnlineX;
    private int navSlotY;
    private int timerHintY = -1;
    private int timerLabelY = -1;
    @Nullable
    private TableSettingsPanelButton previousMoveButton;
    @Nullable
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
        super.init();
        clearWidgets();
        layoutPanelGeometry();
        addSectionContent();
        addNavTabs();
        refreshLabelsFromBoard();
    }

    private void selectSection(PanelSection next) {
        if (section == next) {
            return;
        }
        section = next;
        clearWidgets();
        layoutPanelGeometry();
        addSectionContent();
        addNavTabs();
        refreshLabelsFromBoard();
    }

    private void layoutPanelGeometry() {
        int offlineBody = (ROW_H + GAP_Y) * 2 + SECTION_GAP + 11 + 11 + 6 + ROW_H + GAP_Y + SECTION_GAP + ROW_H + GAP_Y + 10 + ROW_H + BOTTOM_PAD;
        int onlineBody = (ROW_H + GAP_Y) * 2 + 10 + ROW_H + BOTTOM_PAD;
        int bodyH = Math.max(offlineBody, onlineBody);
        panelWidth = OUTER_PAD_H + CONTENT_W + OUTER_PAD_H;
        panelHeight = TITLE_BELOW_TOP + bodyH;
        panelLeft = this.width / 2 - panelWidth / 2;
        panelTop = this.height / 2 - panelHeight / 2;
        contentLeft = panelLeft + OUTER_PAD_H;
        int slot = ChessSettingsNavSlotButton.SLOT_SIZE;
        navOfflineX = contentLeft - 12;
        navOnlineX = navOfflineX + slot + NAV_SLOT_GAP;
        int frameTop = panelTop - FRAME_OUTSET;
        navSlotY = frameTop - NAV_OUTSIDE_GAP - slot;
    }

    private void addNavTabs() {
        addRenderableWidget(new ChessSettingsNavSlotButton(navOfflineX, navSlotY, new ItemStack(Items.RED_WOOL),
                Component.literal("Offline — opções locais"), () -> selectSection(PanelSection.OFFLINE), section == PanelSection.OFFLINE));
        addRenderableWidget(new ChessSettingsNavSlotButton(navOnlineX, navSlotY, new ItemStack(Items.GREEN_WOOL),
                Component.literal("Online — fila API"), () -> selectSection(PanelSection.ONLINE), section == PanelSection.ONLINE));
    }

    private void addSectionContent() {
        int btnW = CONTENT_W;
        int btnX = contentLeft;
        int y = panelTop + TITLE_BELOW_TOP;
        timerHintY = -1;
        timerLabelY = -1;
        previousMoveButton = null;
        legalHintsButton = null;

        if (section == PanelSection.ONLINE) {
            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Partida online (fila API)"),
                    () -> Minecraft.getInstance().setScreen(new ChessMatchmakingScreen(this, boardPos)), TableSettingsPanelButton.Style.ROW));
            y += ROW_H + GAP_Y;
            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Desligar online nesta mesa"),
                    () -> TableCraftNetworking.sendOnlineClear(new OnlineTableClearPayload(boardPos, OnlineTableBindPayload.GAME_CHESS)),
                    TableSettingsPanelButton.Style.DANGER));
            y += ROW_H + GAP_Y + 10;
            addRenderableWidget(new TableSettingsPanelButton(btnX, y, btnW, ROW_H, Component.literal("Concluído"), this::onClose,
                    TableSettingsPanelButton.Style.PRIMARY));
            return;
        }

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
        int slot = ChessSettingsNavSlotButton.SLOT_SIZE;
        graphics.fill(navOfflineX + 2, navSlotY + 3, navOnlineX + slot + 2, navSlotY + slot + 3, 0x48080810);

        int x0 = panelLeft;
        int y0 = panelTop;
        int x1 = panelLeft + panelWidth;
        int y1 = panelTop + panelHeight;
        int o = FRAME_OUTSET;
        graphics.fill(x0 + 2, y0 + 3, x1 + 3, y1 + 4, 0x48080810);
        graphics.fill(x0 - o, y0 - o, x1 + o, y1 + o, 0xC0101018);
        graphics.renderOutline(x0 - o, y0 - o, panelWidth + 2 * o, panelHeight + 2 * o, 0xFF7a8898);
        int titleX = panelLeft + (panelWidth - this.font.width(this.title)) / 2;
        graphics.drawString(this.font, this.title, titleX, panelTop + 8, 0xFFE8EEF5, false);

        if (section == PanelSection.OFFLINE && timerHintY >= 0 && timerLabelY >= 0) {
            graphics.drawString(this.font, Component.literal("Relógio — ajuste antes da partida"), contentLeft, timerHintY, 0xFF8a9aaa, false);
            graphics.drawString(this.font, timerLabel, contentLeft, timerLabelY, 0xFFD0D8E0, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
