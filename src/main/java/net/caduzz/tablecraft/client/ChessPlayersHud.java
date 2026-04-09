package net.caduzz.tablecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
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
public final class ChessPlayersHud {
    private ChessPlayersHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "chess_players_hud"), new LayeredDraw.Layer() {
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
                if (!(be instanceof ChessBlockEntity chess) || !chess.isRegisteredParticipant(mc.player.getUUID())) {
                    return;
                }
                int sw = mc.getWindow().getGuiScaledWidth();
                int panelW = 220;
                int panelH = 48;
                int x = (sw - panelW) / 2;
                int y = 8;
                String turn = chess.isWhiteTurn() ? chess.getGameSeatWhiteName() : chess.getGameSeatBlackName();
                if (turn.isEmpty()) {
                    turn = "—";
                }
                RenderSystem.enableBlend();
                gui.fill(x, y, x + panelW, y + panelH, 0xD0101018);
                gui.drawString(mc.font, "Xadrez — mesa", x + 8, y + 6, 0xFFFFFF, true);
                String right = "Turno: " + turn;
                gui.drawString(mc.font, right, x + panelW - mc.font.width(right) - 8, y + 6, 0xE0E0E0, true);
                gui.drawString(mc.font, "Brancas: " + (chess.hasGameSeatWhite() ? chess.getGameSeatWhiteName() : "—"), x + 8, y + 32, 0xE0E0E0,
                        false);
                String b = "Pretas: " + (chess.hasGameSeatBlack() ? chess.getGameSeatBlackName() : "—");
                gui.drawString(mc.font, b, x + panelW - mc.font.width(b) - 8, y + 32, 0xB0B0B0, false);
                RenderSystem.disableBlend();
            }
        });
    }
}
