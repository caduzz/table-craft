package net.caduzz.tablecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
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
 * HUD para os dois primeiros jogadores registados: brancas / pretas, só visível para eles ao mirar no tabuleiro.
 */
@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CheckersPlayersHud {

    private CheckersPlayersHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "checkers_players_hud"),
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
                        if (!(be instanceof CheckersBlockEntity checkers)) {
                            return;
                        }
                        if (!checkers.isRegisteredParticipant(mc.player.getUUID())) {
                            return;
                        }
                        if (!checkers.hasGameSeatWhite() && !checkers.hasGameSeatBlack()) {
                            return;
                        }

                        int sw = mc.getWindow().getGuiScaledWidth();
                        int panelW = 220;
                        int panelH = 48;
                        int x = (sw - panelW) / 2;
                        int y = 8;

                        String turnStr = checkers.isWhiteTurn() ? checkers.getGameSeatWhiteName() : checkers.getGameSeatBlackName();
                        String wName = checkers.hasGameSeatWhite() ? checkers.getGameSeatWhiteName() : "—";
                        String bName = checkers.hasGameSeatBlack() ? checkers.getGameSeatBlackName() : "—";

                        if (turnStr.isEmpty()) {
                            turnStr = "—";
                        }

                        RenderSystem.enableBlend();
                        gui.fill(x, y, x + panelW, y + panelH, 0xD0101018);

                        gui.drawString(mc.font, "Damas — mesa", x + 8, y + 6, 0xFFFFFF, true);
                            
                        String rightText = "Turno: " + turnStr;
                        int rightX = x + panelW - mc.font.width(rightText) - 8;
                        gui.drawString(mc.font, rightText, rightX, y + 6, 0xE0E0E0, true);

                        gui.drawString(mc.font, "Brancas: " + wName, x + 8, y + 32, 0xE0E0E0, false);
                        gui.drawString(mc.font, "Pretas: " + bName, x + panelW - mc.font.width("Pretas: " + bName) - 8, y + 32, 0xB0B0B0, false);

                        RenderSystem.disableBlend();
                    }
                });
    }
}
