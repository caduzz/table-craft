package net.caduzz.tablecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Botão plano usado nas telas de configuração das mesas (xadrez / damas). */
public final class TableSettingsPanelButton extends AbstractButton {
    public enum Style {
        ROW,
        COMPACT,
        PRIMARY,
        DANGER
    }

    private final Runnable action;
    private final Style style;

    public TableSettingsPanelButton(int x, int y, int w, int h, Component message, Runnable action, Style style) {
        super(x, y, w, h, message);
        this.action = action;
        this.style = style;
    }

    @Override
    public void onPress() {
        action.run();
    }

    /** Pinta o mesmo retângulo preenchido + contorno que os botões do menu (sem texto). */
    public static void paintStyleRect(GuiGraphics g, int x, int y, int w, int h, Style style, boolean hover) {
        int fill;
        int border;
        switch (style) {
            case DANGER -> {
                fill = hover ? 0xFF7a2a32 : 0xFF4a1820;
                border = hover ? 0xFFc45a62 : 0xFF8a3038;
            }
            case PRIMARY -> {
                fill = hover ? 0xFF3a6a4a : 0xFF284832;
                border = hover ? 0xFF5cb88a : 0xFF3d8a62;
            }
            case COMPACT -> {
                fill = hover ? 0xFF354555 : 0xFF252d38;
                border = hover ? 0xFF6a7a8e : 0xFF4a5868;
            }
            default -> {
                fill = hover ? 0xFF323c4e : 0xFF222830;
                border = hover ? 0xFF5a6a82 : 0xFF3d4858;
            }
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.renderOutline(x, y, w, h, border);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = isHovered();

        int textColor;
        switch (style) {
            case DANGER -> textColor = 0xFFFFF0F0;
            case PRIMARY -> textColor = 0xFFE8FFF0;
            case COMPACT -> textColor = 0xFFE0E8F0;
            default -> textColor = 0xFFE4ECF5;
        }

        paintStyleRect(g, x, y, w, h, style, hover);
        Font f = Minecraft.getInstance().font;
        int msgW = f.width(getMessage());
        int tx = x + (w - msgW) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(f, getMessage(), tx, ty, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
