package net.caduzz.tablecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private ChessSelectionHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "chess_selection_hud"), new LayeredDraw.Layer() {
            @Override
            public void render(GuiGraphics gui, DeltaTracker deltaTracker) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null || mc.options.hideGui) {
                    return;
                }
                HitResult hit = mc.hitResult;
                if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
                    return;
                }
                if (!mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHESS_BLOCK.get())) {
                    return;
                }
                BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
                if (!(be instanceof ChessBlockEntity chess) || !chess.hasSelection()) {
                    return;
                }
                Piece p = chess.getPiece(chess.getSelectedRow(), chess.getSelectedCol());
                if (p == Piece.EMPTY) {
                    return;
                }
                boolean white = ChessMoveLogic.isWhite(p);
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                int x = sw - 136;
                int y = sh - 44;
                RenderSystem.enableBlend();
                gui.fill(x, y, x + 128, y + 36, 0xC0101018);
                gui.drawString(mc.font, white ? "Branca" : "Preta", x + 8, y + 8, 0xFFFFFF, true);
                gui.drawString(mc.font, pieceName(p), x + 8, y + 20, 0xD0D0D0, false);
                RenderSystem.disableBlend();
            }
        });
    }

    private static String pieceName(Piece p) {
        return switch (p) {
            case WHITE_PAWN, BLACK_PAWN -> "Peao";
            case WHITE_ROOK, BLACK_ROOK -> "Torre";
            case WHITE_KNIGHT, BLACK_KNIGHT -> "Cavalo";
            case WHITE_BISHOP, BLACK_BISHOP -> "Bispo";
            case WHITE_QUEEN, BLACK_QUEEN -> "Rainha";
            case WHITE_KING, BLACK_KING -> "Rei";
            default -> "Peca";
        };
    }
}
