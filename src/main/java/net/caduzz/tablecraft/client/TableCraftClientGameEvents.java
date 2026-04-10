package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.client.online.OnlineAuthCoordinator;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * GAME bus: fallback para autenticação API no primeiro tick com jogador no mundo (além do S2C no login).
 */
@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class TableCraftClientGameEvents {
    private static boolean loginAuthFallbackPending = true;

    private TableCraftClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            loginAuthFallbackPending = true;
            return;
        }
        if (loginAuthFallbackPending) {
            loginAuthFallbackPending = false;
            OnlineAuthCoordinator.onWorldLoginReady();
        }
    }
}
