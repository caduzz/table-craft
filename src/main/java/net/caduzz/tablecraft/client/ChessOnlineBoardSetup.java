package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.client.online.ClientPlayerRegistrationStore;
import net.caduzz.tablecraft.network.OnlineTableBindPayload;
import net.caduzz.tablecraft.network.TableCraftNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Liga a mesa do mundo à partida API após matchmaking.
 */
public final class ChessOnlineBoardSetup {
    private ChessOnlineBoardSetup() {
    }

    /**
     * @param sessionToken {@code X-TableCraft-Session} atual
     * @param currentMatchId id devolvido pela API
     * @param sideLabel      {@code WHITE} ou {@code BLACK}
     */
    public static void initBoard(Minecraft minecraft, BlockPos boardPos, String sessionToken, String currentMatchId, String sideLabel) {
        if (minecraft.player == null || boardPos == null || sessionToken == null || sessionToken.isEmpty() || currentMatchId == null
                || currentMatchId.isEmpty()) {
            return;
        }
        int sideOrdinal = "BLACK".equalsIgnoreCase(sideLabel) ? 1 : 0;
        ClientPlayerRegistrationStore.setLastChessOnlineBinding(currentMatchId, sideLabel);
        TableCraftNetworking.sendOnlineBind(
                new OnlineTableBindPayload(boardPos, OnlineTableBindPayload.GAME_CHESS, sessionToken, currentMatchId, sideOrdinal));
        minecraft.player.displayClientMessage(
                Component.literal("Mesa ligada: " + currentMatchId + " | Lado: " + (sideOrdinal == 1 ? "PRETAS" : "BRANCAS")), false);
    }
}
