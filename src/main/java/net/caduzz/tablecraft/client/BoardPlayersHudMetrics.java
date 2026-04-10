package net.caduzz.tablecraft.client;

import net.minecraft.client.Minecraft;

/**
 * Tamanhos compactos partilhados pela HUD de jogadores (xadrez e damas).
 */
public final class BoardPlayersHudMetrics {

    public static final int PANEL_W = 100;
    public static final int HEAD_SIZE = 18;
    public static final int PAD = 4;
    public static final int NAME_GAP = 4;
    /** Espaço entre linha do título e topo da cabeça. */
    private static final int GAP_TITLE_TO_HEAD = 3;

    private BoardPlayersHudMetrics() {}

    /** Altura justa: título + cabeça + margens. */
    public static int panelHeight(Minecraft mc) {
        return PAD + mc.font.lineHeight + GAP_TITLE_TO_HEAD + HEAD_SIZE + PAD;
    }

    /** Linha do título (Brancas / Pretas), abaixo do topo do painel. */
    public static int titleY(int panelTop) {
        return panelTop + PAD;
    }

    /** Topo da cabeça, por baixo do título. */
    public static int headY(int panelTop, Minecraft mc) {
        return panelTop + PAD + mc.font.lineHeight + GAP_TITLE_TO_HEAD;
    }

    /** Baseline do nome, alinhado ao centro vertical da cabeça. */
    public static int nameBaselineY(int headTop, Minecraft mc) {
        return headTop + (HEAD_SIZE - mc.font.lineHeight) / 2;
    }
}
