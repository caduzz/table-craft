package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.CheckersMoveLogic;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity.Piece;
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
public final class CheckersSelectionHud {

    private CheckersSelectionHud() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "checkers_selection_hud"),
            new LayeredDraw.Layer() {

                @Override
                public void render(GuiGraphics gui, DeltaTracker deltaTracker) {

                    Minecraft mc = Minecraft.getInstance();

                    if (mc.player == null || mc.level == null || mc.options.hideGui) return;

                    HitResult hit = mc.hitResult;
                    if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;

                    if (!mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHECKERS_BLOCK.get())) return;

                    BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
                    if (!(be instanceof CheckersBlockEntity checkers)
                            || !checkers.isGameInProgress()
                            || !checkers.hasSelection()) return;

                    Piece p = checkers.getPiece(checkers.getSelectedRow(), checkers.getSelectedCol());
                    if (p == Piece.EMPTY) return;

                    int sw = mc.getWindow().getGuiScaledWidth();
                    int sh = mc.getWindow().getGuiScaledHeight();

                    int panelW = 112;
                    int panelH = 34;
                    int x = sw - panelW - 8;
                    int y = 8;

                    boolean white = CheckersMoveLogic.isWhite(p);
                    boolean king = p == Piece.WHITE_KING || p == Piece.BLACK_KING;

                    int bg = 0xD0121212;
                    int borderTop = 0xFF555555;
                    int borderBottom = 0xFF222222;

                    int pieceColor = white ? 0xFFF5F5F5 : 0xFF2A2A2A;
                    int accent = king ? 0xFFFFD27A : 0xFF6FA8FF;

                    // ===== FUNDO =====
                    gui.fill(x, y, x + panelW, y + panelH, bg);

                    // bordas sutis
                    gui.fill(x, y, x + panelW, y + 1, borderTop);
                    gui.fill(x, y + panelH - 1, x + panelW, y + panelH, borderBottom);

                    // leve sombra interna
                    gui.fill(x + 1, y + 1, x + panelW - 1, y + panelH - 1, 0x10000000);

                    // ===== ÍCONE DA PEÇA =====
                    int px = x + 6;
                    int py = y + 8;
                    int size = 18;

                    // base da peça
                    gui.fill(px, py, px + size, py + size, pieceColor);

                    // borda arredondada fake
                    gui.fill(px, py, px + size, py + 1, accent);
                    gui.fill(px, py + size - 1, px + size, py + size, accent);
                    gui.fill(px, py, px + 1, py + size, accent);
                    gui.fill(px + size - 1, py, px + size, py + size, accent);

                    // detalhe de dama (coroa)
                    if (king) {
                        gui.fill(px + 5, py + 5, px + 13, py + 8, accent);
                        gui.fill(px + 6, py + 2, px + 12, py + 5, accent);
                    }

                    // ===== TEXTO =====
                    String line1 = white ? "Brancas" : "Pretas";
                    String line2 = king ? "Dama selecionada" : "Peão selecionado";

                    gui.drawString(mc.font, line1, x + 28, y + 7, 0xFFFFFF, true);
                    gui.drawString(mc.font, line2, x + 28, y + 18, 0xBFBFBF, false);
                }
            }
        );
    }
}