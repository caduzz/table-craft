package net.caduzz.tablecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;

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

/**
 * HUD simples ao mirar no bloco de damas com peça selecionada: cor + tipo (peão/dama).
 */
@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CheckersSelectionHud {

    private CheckersSelectionHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "checkers_selection_hud"),
                new LayeredDraw.Layer() {
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
                        if (!mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHECKERS_BLOCK.get())) {
                            return;
                        }
                        BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
                        if (!(be instanceof CheckersBlockEntity checkers)
                                || !checkers.isGameInProgress()
                                || !checkers.hasSelection()) {
                            return;
                        }
                        Piece p = checkers.getPiece(checkers.getSelectedRow(), checkers.getSelectedCol());
                        if (p == Piece.EMPTY) {
                            return;
                        }

                        int sw = mc.getWindow().getGuiScaledWidth();
                        int sh = mc.getWindow().getGuiScaledHeight();
                        int panelW = 120;
                        int panelH = 36;
                        int x = sw - panelW - 8;
                        int y = sh - panelH - 8;

                        boolean white = CheckersMoveLogic.isWhite(p);
                        boolean king = p == Piece.WHITE_KING || p == Piece.BLACK_KING;
                        int swatch = white ? 0xFFF0F0F8 : 0xFF262630;
                        int accent = king ? 0xFFFFCC66 : 0xFF88AAFF;

                        RenderSystem.enableBlend();
                        gui.fill(x, y, x + panelW, y + panelH, 0xC0101018);
                        gui.fill(x + 6, y + 8, x + 22, y + 24, swatch);
                        gui.fill(x + 6, y + 8, x + 22, y + 9, accent);
                        gui.fill(x + 6, y + 23, x + 22, y + 24, accent);
                        gui.fill(x + 6, y + 8, x + 7, y + 24, accent);
                        gui.fill(x + 21, y + 8, x + 22, y + 24, accent);

                        String line1 = white ? "Branca" : "Preta";
                        String line2 = king ? "Dama" : "Peão";
                        gui.drawString(mc.font, line1, x + 28, y + 10, 0xFFFFFF, true);
                        gui.drawString(mc.font, line2, x + 28, y + 22, 0xCCCCCC, false);
                        RenderSystem.disableBlend();
                    }
                });
    }
}
