package net.caduzz.tablecraft.client.online;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.online.OnlineSide;
import net.caduzz.tablecraft.online.TablePlayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class TableOnlineStatusHud {
    private TableOnlineStatusHud() {
    }

    @SubscribeEvent
    public static void afterGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        GuiGraphics g = event.getGuiGraphics();
        int y = 6;
        if (be instanceof ChessBlockEntity chess && chess.getTablePlayMode() == TablePlayMode.ONLINE) {
            drawLine(g, 6, y, Component.literal("TableCraft — Xadrez ONLINE"));
            y += 11;
            UUID self = mc.player.getUUID();
            String sideHint = formatOnlineSideHint(chess.getOnlineSideFor(self), chess.getOnlineSide());
            drawLine(g, 6, y, Component.literal(sideHint + " | Vez: " + (chess.isWhiteTurn() ? "Brancas" : "Pretas")));
            y += 11;
            drawLine(g, 6, y, Component.literal(
                    "Capturadas (estim.) B:" + chess.getOnlineCapturedBlackCount() + " W:" + chess.getOnlineCapturedWhiteCount()));
            y += 11;
            if (chess.isOnlineMovePending()) {
                drawLine(g, 6, y, Component.literal("A aguardar confirmação da API…"));
            }
        }
    }

    private static String formatOnlineSideHint(OnlineSide yours, OnlineSide lastBound) {
        if (yours != null) {
            return "O seu lado: " + yours;
        }
        return "Último ligado: " + lastBound + " — ligue a mesa (tecla P) com a sua sessão se for a sua partida";
    }

    private static void drawLine(GuiGraphics g, int x, int y, Component line) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(x - 2, y - 1, x + mc.font.width(line) + 4, y + 9, 0x80000000);
        g.drawString(mc.font, line, x, y, 0xFFFFFF, false);
    }
}
