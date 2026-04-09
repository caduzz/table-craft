package net.caduzz.tablecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
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

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ChessVictoryHud {
    private ChessVictoryHud() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TableCraft.MOD_ID, "chess_victory_hud"), new LayeredDraw.Layer() {
            @Override
            public void render(GuiGraphics gui, DeltaTracker deltaTracker) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null || mc.options.hideGui) {
                    return;
                }
                ChessBlockEntity board = pickBoard(mc);
                if (board == null || board.isGameInProgress() || board.getLastWinnerDisplayName().isEmpty()) {
                    return;
                }
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                String name = board.getLastWinnerDisplayName();
                String sub = board.getGameStatus() == ChessBlockEntity.ChessGameStatus.WHITE_WIN ? "Brancas venceram" : "Pretas venceram";
                RenderSystem.enableBlend();
                gui.fill(0, 0, sw, sh, 0x14000000);
                gui.drawCenteredString(mc.font, name, sw / 2, (int) (sh * 0.18f), 0xFFFFFF);
                gui.drawCenteredString(mc.font, sub, sw / 2, (int) (sh * 0.18f) + 14, 0xC9D8F6);
                RenderSystem.disableBlend();
            }
        });
    }

    private static ChessBlockEntity pickBoard(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(bhr.getBlockPos()).is(ModBlocks.CHESS_BLOCK.get())) {
            BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
            if (be instanceof ChessBlockEntity chess) {
                return chess;
            }
        }
        BlockPos origin = mc.player.blockPosition();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(p).is(ModBlocks.CHESS_BLOCK.get())) {
                        continue;
                    }
                    BlockEntity be = mc.level.getBlockEntity(p);
                    if (!(be instanceof ChessBlockEntity chess)) {
                        continue;
                    }
                    if (!chess.isRegisteredParticipant(mc.player.getUUID())) {
                        continue;
                    }
                    if (mc.player.distanceToSqr(Vec3.atCenterOf(p)) <= 22.0 * 22.0) {
                        return chess;
                    }
                }
            }
        }
        return null;
    }
}
