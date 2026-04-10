package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ChessMoveLogic;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity.Piece;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ChessSelectionHud {

    private ChessSelectionHud() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "chess_selection_hud"),
            new LayeredDraw.Layer() {

                @Override
                public void render(GuiGraphics gui, DeltaTracker deltaTracker) {

                    Minecraft mc = Minecraft.getInstance();

                    if (mc.player == null || mc.level == null || mc.options.hideGui) return;

                    HitResult hit = mc.hitResult;
                    if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;

                    if (!mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHESS_BLOCK.get())) return;

                    BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
                    if (!(be instanceof ChessBlockEntity chess) || !chess.hasSelection()) return;

                    Piece p = chess.getPiece(chess.getSelectedRow(), chess.getSelectedCol());
                    if (p == Piece.EMPTY) return;

                    boolean white = ChessMoveLogic.isWhite(p);

                    int sw = mc.getWindow().getGuiScaledWidth();
                    int panelW = 124;
                    int panelH = 36;
                    int x = sw - panelW - 8;
                    int y = 8;

                    int bg = 0xD0121212;
                    int borderTop = 0xFF555555;
                    int borderBottom = 0xFF222222;

                    int pieceColor = white ? 0xFFF5F5F5 : 0xFF2A2A2A;
                    int accent = getAccentColor(p);

                    // ===== FUNDO =====
                    gui.fill(x, y, x + panelW, y + panelH, bg);

                    gui.fill(x, y, x + panelW, y + 1, borderTop);
                    gui.fill(x, y + panelH - 1, x + panelW, y + panelH, borderBottom);

                    gui.fill(x + 1, y + 1, x + panelW - 1, y + panelH - 1, 0x10000000);

                    // ===== ÍCONE (baseado no tipo real da entity) =====
                    int px = x + 6;
                    int py = y + 8;
                    int size = 18;

                    gui.fill(px, py, px + size, py + size, 0xCC0F0F0F);

                    gui.fill(px, py, px + size, py + 1, accent);
                    gui.fill(px, py + size - 1, px + size, py + size, accent);
                    gui.fill(px, py, px + 1, py + size, accent);
                    gui.fill(px + size - 1, py, px + size, py + size, accent);

                    drawPieceGlyph(gui, mc, px, py, size, p, pieceColor);

                    // ===== TEXTO =====
                    String line1 = white ? "Brancas" : "Pretas";
                    String line2 = pieceName(p) + " selecionado";

                    gui.drawString(mc.font, line1, x + 28, y + 7, 0xFFFFFF, true);
                    gui.drawString(mc.font, line2, x + 28, y + 20, 0xBFBFBF, false);
                }
            }
        );
    }

    private static int getAccentColor(Piece p) {
        return switch (p) {
            case WHITE_KING, BLACK_KING -> 0xFFFFD700;
            case WHITE_QUEEN, BLACK_QUEEN -> 0xFFE066FF;
            case WHITE_ROOK, BLACK_ROOK -> 0xFF66CCFF;
            case WHITE_BISHOP, BLACK_BISHOP -> 0xFF66FFAA;
            case WHITE_KNIGHT, BLACK_KNIGHT -> 0xFFFFAA66;
            case WHITE_PAWN, BLACK_PAWN -> 0xFFAAAAAA;
            default -> 0xFFFFFFFF;
        };
    }

    private static void drawPieceGlyph(GuiGraphics gui, Minecraft mc, int x, int y, int size, Piece p, int color) {
        String glyph = pieceGlyph(p);
        int gx = x + ((size - mc.font.width(glyph)) / 2);
        int gy = y + ((size - mc.font.lineHeight) / 2);
        gui.drawString(mc.font, glyph, gx, gy, color, false);
    }

    private static String pieceGlyph(Piece p) {
        return switch (p) {
            case WHITE_KING -> "♔";
            case WHITE_QUEEN -> "♕";
            case WHITE_ROOK -> "♖";
            case WHITE_BISHOP -> "♗";
            case WHITE_KNIGHT -> "♘";
            case WHITE_PAWN -> "♙";
            case BLACK_KING -> "♚";
            case BLACK_QUEEN -> "♛";
            case BLACK_ROOK -> "♜";
            case BLACK_BISHOP -> "♝";
            case BLACK_KNIGHT -> "♞";
            case BLACK_PAWN -> "♟";
            default -> "?";
        };
    }

    private static String pieceName(Piece p) {
        return switch (p) {
            case WHITE_PAWN, BLACK_PAWN -> "Peão";
            case WHITE_ROOK, BLACK_ROOK -> "Torre";
            case WHITE_KNIGHT, BLACK_KNIGHT -> "Cavalo";
            case WHITE_BISHOP, BLACK_BISHOP -> "Bispo";
            case WHITE_QUEEN, BLACK_QUEEN -> "Rainha";
            case WHITE_KING, BLACK_KING -> "Rei";
            default -> "Peça";
        };
    }
}