package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.client.online.ChessMatchmakingApiCleanup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Ao sair do mundo/servidor, pede à API para sair da fila de xadrez (Alt+F4 muitas vezes não corre {@code Screen#onClose}).
 */
@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class TableCraftClientOnlineEvents {
    private TableCraftClientOnlineEvents() {
    }

    @SubscribeEvent
    public static void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChessMatchmakingApiCleanup.cancelQueueOnClientDisconnect();
    }
}
