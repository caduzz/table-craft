package net.caduzz.tablecraft.client;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class TableSettingsClientEvents {
    private TableSettingsClientEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        var state = event.getLevel().getBlockState(event.getPos());
        Screen screen = null;
        if (state.is(ModBlocks.CHESS_BLOCK.get())) {
            screen = new ChessTableSettingsScreen(event.getPos());
        } else if (state.is(ModBlocks.CHECKERS_BLOCK.get())) {
            screen = new CheckersTableSettingsScreen(event.getPos());
        }
        if (screen != null) {
            Minecraft.getInstance().setScreen(screen);
            event.setCanceled(true);
        }
    }
}
