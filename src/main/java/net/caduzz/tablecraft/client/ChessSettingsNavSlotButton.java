package net.caduzz.tablecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Quadrado de navegação com as mesmas cores que {@link TableSettingsPanelButton} (ROW vs PRIMARY) e ícone de item.
 */
public final class ChessSettingsNavSlotButton extends AbstractButton {
    /** Igual a {@link ChessTableSettingsScreen} {@code ROW_H} (botões de linha). */
    public static final int SLOT_SIZE = 22;

    private final ItemStack icon;
    private final boolean selected;
    private final Runnable action;

    public ChessSettingsNavSlotButton(int x, int y, ItemStack icon, Component tooltipLabel, Runnable action, boolean selected) {
        super(x, y, SLOT_SIZE, SLOT_SIZE, tooltipLabel);
        this.icon = icon;
        this.action = action;
        this.selected = selected;
        setTooltip(Tooltip.create(tooltipLabel));
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int s = SLOT_SIZE;
        boolean hover = isHovered();
        TableSettingsPanelButton.Style style = selected ? TableSettingsPanelButton.Style.PRIMARY : TableSettingsPanelButton.Style.ROW;
        TableSettingsPanelButton.paintStyleRect(g, x, y, s, s, style, hover);
        int inset = (s - 16) / 2;
        g.renderItem(icon, x + inset, y + inset);
        g.renderItemDecorations(Minecraft.getInstance().font, icon, x + inset, y + inset);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
