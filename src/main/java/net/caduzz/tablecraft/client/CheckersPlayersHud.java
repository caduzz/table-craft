package net.caduzz.tablecraft.client;

import com.mojang.authlib.GameProfile;
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

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CheckersPlayersHud {

    private CheckersPlayersHud() {}

    private static void drawPlayerHead(GuiGraphics gui, Minecraft mc, int x, int y, int size, GameProfile profile) {
        if (profile == null) return;

        ResourceLocation skin = mc.getSkinManager().getInsecureSkin(profile).texture();

        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);

        float scale = size / 8.0f;
        gui.pose().scale(scale, scale, 1.0f);

        // cabeça
        gui.blit(skin, 0, 0, 8, 8, 8, 8, 64, 64);
        // camada extra
        gui.blit(skin, 0, 0, 40, 8, 8, 8, 64, 64);

        gui.pose().popPose();

        // frame da cabeça
        gui.fill(x - 1, y - 1, x + size + 1, y, 0xFF000000);
        gui.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xFF000000);
        gui.fill(x - 1, y, x, y + size, 0xFF000000);
        gui.fill(x + size, y, x + size + 1, y + size, 0xFF000000);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "checkers_players_hud"),
            new LayeredDraw.Layer() {

                @Override
                public void render(GuiGraphics gui, DeltaTracker deltaTracker) {

                    Minecraft mc = Minecraft.getInstance();

                    if (mc.player == null || mc.level == null || mc.options.hideGui) return;

                    HitResult hit = mc.hitResult;
                    if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;

                    if (!mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHECKERS_BLOCK.get())) return;

                    BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
                    if (!(be instanceof CheckersBlockEntity checkers) || !checkers.isRegisteredParticipant(mc.player.getUUID())) return;

                    int screenWidth = mc.getWindow().getGuiScaledWidth();

                    int panelW = BoardPlayersHudMetrics.PANEL_W;
                    int panelH = BoardPlayersHudMetrics.panelHeight(mc);

                    BoardPlayersHudLayout.Result lay = BoardPlayersHudLayout.layout(mc, panelW, panelH);
                    int leftX = lay.leftX();
                    int rightX = lay.rightX();
                    int y = lay.panelTopY();
                    int pad = BoardPlayersHudMetrics.PAD;
                    int headY = BoardPlayersHudMetrics.headY(y, mc);

                    boolean whiteTurn = checkers.isWhiteTurn();
                    boolean blackTurn = !whiteTurn;

                    int whiteColor = whiteTurn ? 0xFFFFFFAA : 0xFFCCCCCC;
                    int blackColor = blackTurn ? 0xFFFFFFAA : 0xFFCCCCCC;

                    // ===== BRANCAS =====
                    gui.fill(leftX, y, leftX + panelW, y + panelH, 0xC0101010);

                    // bordas
                    gui.fill(leftX, y, leftX + panelW, y + 1, 0xFF555555);
                    gui.fill(leftX, y + panelH - 1, leftX + panelW, y + panelH, 0xFF222222);

                    // highlight turno
                    if (whiteTurn) {
                        gui.fill(leftX, y, leftX + panelW, y + panelH, 0x20FFFFFF);
                    }

                    gui.drawString(mc.font, "Brancas", leftX + pad, BoardPlayersHudMetrics.titleY(y), whiteColor, true);

                    if (checkers.hasGameSeatWhite()) {
                        drawPlayerHead(gui, mc, leftX + pad, headY, BoardPlayersHudMetrics.HEAD_SIZE, checkers.getWhiteSeatGameProfile());
                    }

                    String whiteName = checkers.hasGameSeatWhite() ? checkers.getGameSeatWhiteName() : "—";
                    gui.drawString(mc.font, whiteName, leftX + pad + BoardPlayersHudMetrics.HEAD_SIZE + BoardPlayersHudMetrics.NAME_GAP,
                            BoardPlayersHudMetrics.nameBaselineY(headY, mc), 0xE0E0E0, true);

                    // ===== PRETAS =====
                    gui.fill(rightX, y, rightX + panelW, y + panelH, 0xC0101010);

                    gui.fill(rightX, y, rightX + panelW, y + 1, 0xFF555555);
                    gui.fill(rightX, y + panelH - 1, rightX + panelW, y + panelH, 0xFF222222);

                    if (blackTurn) {
                        gui.fill(rightX, y, rightX + panelW, y + panelH, 0x20FFFFFF);
                    }

                    gui.drawString(mc.font, "Pretas", rightX + pad, BoardPlayersHudMetrics.titleY(y), blackColor, true);

                    if (checkers.hasGameSeatBlack()) {
                        drawPlayerHead(gui, mc, rightX + pad, headY, BoardPlayersHudMetrics.HEAD_SIZE, checkers.getBlackSeatGameProfile());
                    }

                    String blackName = checkers.hasGameSeatBlack() ? checkers.getGameSeatBlackName() : "—";
                    gui.drawString(mc.font, blackName, rightX + pad + BoardPlayersHudMetrics.HEAD_SIZE + BoardPlayersHudMetrics.NAME_GAP,
                            BoardPlayersHudMetrics.nameBaselineY(headY, mc), 0xE0E0E0, true);

                    // ===== TEXTO CENTRAL =====
                    String turnText = checkers.isWhiteTurn() ? "Brancas jogam" : "Pretas jogam";

                    gui.drawString(
                        mc.font,
                        turnText,
                        (screenWidth / 2) - (mc.font.width(turnText) / 2),
                        lay.turnTextY(),
                        0xFFFFFF,
                        true
                    );
                }
            }
        );
    }
}