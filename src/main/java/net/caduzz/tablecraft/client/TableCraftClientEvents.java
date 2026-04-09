package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.entity.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TableCraftClientEvents {
    private TableCraftClientEvents() {
    }

    @SubscribeEvent
    public static void registerChessModelReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ChessBlockbenchModelLoader.reloader());
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CHECKERS.get(), CheckersBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CHESS.get(), ChessBlockRenderer::new);
    }
}
