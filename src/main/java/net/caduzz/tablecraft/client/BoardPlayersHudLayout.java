package net.caduzz.tablecraft.client;

import net.minecraft.client.Minecraft;

/**
 * Posiciona os dois painéis junto à hotbar (à esquerda e à direita), na mesma faixa vertical que a barra de itens.
 */
public final class BoardPlayersHudLayout {

    /** Metade da largura da hotbar em pixels GUI escalados (barra total = 182). */
    private static final int HOTBAR_HALF_WIDTH = 91;
    /** Altura da faixa da hotbar. */
    private static final int HOTBAR_HEIGHT = 22;
    /** Espaço horizontal entre painel e hotbar. */
    private static final int GAP_SIDE = 4;
    /** Distância aproximada entre o topo da hotbar e a linha de turno (texto por cima). */
    private static final int TURN_TEXT_GAP_ABOVE_HOTBAR = 8;
    private static final int SCREEN_MARGIN = 4;

    private BoardPlayersHudLayout() {}

    public record Result(int leftX, int rightX, int panelTopY, int turnTextY) {}

    /**
     * @param panelW largura de cada painel
     * @param panelH altura de cada painel
     */
    public static Result layout(Minecraft mc, int panelW, int panelH) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2;
        int hotbarTop = sh - HOTBAR_HEIGHT;
        // Mesma altura que a hotbar: centrar o painel na faixa de 22px; se for mais alto, alinhar a base à hotbar (fundo do ecrã).
        int panelTopY = hotbarTop + (HOTBAR_HEIGHT - panelH) / 2;
        if (panelTopY + panelH > sh) {
            panelTopY = sh - panelH;
        }
        if (panelTopY < SCREEN_MARGIN) {
            panelTopY = SCREEN_MARGIN;
        }

        int hotbarLeft = cx - HOTBAR_HALF_WIDTH;
        int hotbarRight = cx + HOTBAR_HALF_WIDTH;

        int leftX = hotbarLeft - GAP_SIDE - panelW;
        if (leftX < SCREEN_MARGIN) {
            leftX = SCREEN_MARGIN;
        }

        int rightX = hotbarRight + GAP_SIDE;
        if (rightX + panelW > sw - SCREEN_MARGIN) {
            rightX = sw - panelW - SCREEN_MARGIN;
        }

        int line = mc.font.lineHeight;
        // drawString usa y como baseline; hotbarTop - gap - line deixa ~gap px entre o topo da hotbar e o texto.
        int turnTextY = Math.max(SCREEN_MARGIN, hotbarTop - TURN_TEXT_GAP_ABOVE_HOTBAR - line);

        return new Result(leftX, rightX, panelTopY, turnTextY);
    }
}
