package net.caduzz.tablecraft.client.online;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.caduzz.tablecraft.TableCraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OnlineMenuKeys {
    public static final String CATEGORY = "key.categories.tablecraft";
    static KeyMapping OPEN_ONLINE_MENU;

    private OnlineMenuKeys() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        OPEN_ONLINE_MENU = new KeyMapping("key.tablecraft.online_menu", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P, CATEGORY);
        event.register(OPEN_ONLINE_MENU);
    }
}

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
final class OnlineMenuKeyHandler {
    private OnlineMenuKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OnlineMenuKeys.OPEN_ONLINE_MENU.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("TableCraft: abra o menu da mesa (interaja com o tabuleiro) e use «Jogar online (fila API)»."), false);
            }
        }
    }
}
