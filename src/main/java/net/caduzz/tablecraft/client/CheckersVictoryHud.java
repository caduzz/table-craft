package net.caduzz.tablecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Vitória: nome grande do vencedor (participante). Visível ao mirar no tabuleiro ou perto dele se fores participante.
 */
@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CheckersVictoryHud {

    private static final double NEAR_VICTORY_BLOCKS = 22.0;

    private CheckersVictoryHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "checkers_victory_hud"),
                new LayeredDraw.Layer() {
                    @Override
                    public void render(GuiGraphics gui, DeltaTracker deltaTracker) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null || mc.level == null || mc.options.hideGui) {
                            return;
                        }
                        CheckersBlockEntity board = pickVictoryBoard(mc);
                        if (board == null) {
                            return;
                        }
                        String name = board.getLastWinnerDisplayName();
                        if (name.isEmpty()) {
                            return;
                        }
                        CheckersBlockEntity.CheckersGameStatus st = board.getGameStatus();
                        String subtitle = switch (st) {
                            case WHITE_WIN -> "Brancas venceram";
                            case BLACK_WIN -> "Pretas venceram";
                            case DRAW -> "Empate";
                            default -> "";
                        };
                        if (subtitle.isEmpty()) {
                            return;
                        }
                        boolean whiteWon = st == CheckersBlockEntity.CheckersGameStatus.WHITE_WIN;
                        boolean draw = st == CheckersBlockEntity.CheckersGameStatus.DRAW;

                        int sw = mc.getWindow().getGuiScaledWidth();
                        int sh = mc.getWindow().getGuiScaledHeight();
                        int nameColor = draw ? 0xFFE8E8F0 : whiteWon ? 0xFFF8F8FF : 0xFFB8B8C8;
                        int subColor = draw ? 0xFFAAB8CC : whiteWon ? 0xFFCCDDEE : 0xFF888899;

                        RenderSystem.enableBlend();
                        gui.fill(0, 0, sw, sh, 0x18000000);

                        var pose = gui.pose();
                        pose.pushPose();
                        pose.translate(sw / 2f, sh * 0.22f, 0f);
                        float nameScale = 3.2f;
                        pose.scale(nameScale, nameScale, 1f);
                        int nw = mc.font.width(name);
                        gui.drawString(mc.font, name, (int) (-nw / 2f), 0, nameColor, true);
                        pose.popPose();

                        pose.pushPose();
                        pose.translate(sw / 2f, sh * 0.22f + 42f, 0f);
                        float subScale = 1.6f;
                        pose.scale(subScale, subScale, 1f);
                        int sw2 = mc.font.width(subtitle);
                        gui.drawString(mc.font, subtitle, (int) (-sw2 / 2f), 0, subColor, true);
                        pose.popPose();

                        RenderSystem.disableBlend();
                    }
                });
    }

    private static CheckersBlockEntity pickVictoryBoard(Minecraft mc) {
        CheckersBlockEntity fromHit = fromCrosshair(mc);
        if (fromHit != null) {
            return fromHit;
        }
        return fromNearbyParticipant(mc);
    }

    private static CheckersBlockEntity fromCrosshair(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (!mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHECKERS_BLOCK.get())) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof CheckersBlockEntity c)) {
            return null;
        }
        if (c.isGameInProgress() || c.getLastWinnerDisplayName().isEmpty()) {
            return null;
        }
        return c;
    }

    /** Participante a menos de ~22 blocos do tabuleiro ainda vê o banner sem precisar mirar. */
    private static CheckersBlockEntity fromNearbyParticipant(Minecraft mc) {
        if (!mc.player.isAlive()) {
            return null;
        }
        BlockPos origin = mc.player.blockPosition();
        int r = 12;
        double maxSq = NEAR_VICTORY_BLOCKS * NEAR_VICTORY_BLOCKS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(p).is(ModBlocks.CHECKERS_BLOCK.get())) {
                        continue;
                    }
                    BlockEntity be = mc.level.getBlockEntity(p);
                    if (!(be instanceof CheckersBlockEntity c)) {
                        continue;
                    }
                    if (c.isGameInProgress() || c.getLastWinnerDisplayName().isEmpty()) {
                        continue;
                    }
                    if (!c.isRegisteredParticipant(mc.player.getUUID())) {
                        continue;
                    }
                    if (mc.player.distanceToSqr(Vec3.atCenterOf(p)) > maxSq) {
                        continue;
                    }
                    return c;
                }
            }
        }
        return null;
    }
}
