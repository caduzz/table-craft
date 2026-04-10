package net.caduzz.tablecraft;

import net.caduzz.tablecraft.network.TableCraftLoginReadyPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TableCraft.MOD_ID)
public final class TableCraftPlayerEvents {
    private TableCraftPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, TableCraftLoginReadyPayload.INSTANCE);
        }
    }
}
