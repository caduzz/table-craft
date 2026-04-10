package net.caduzz.tablecraft.client;

import java.lang.reflect.Method;

import net.caduzz.tablecraft.TableCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = TableCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerProfileMenuEvents {

    /** Lazy: o bytecode de {@link Screen} pode declarar o parâmetro como outro tipo (apagamento de genéricos). */
    private static volatile Method addRenderableWidgetMethod;
    private static volatile boolean addRenderableWidgetLookupFailed;

    private PlayerProfileMenuEvents() {
    }

    @javax.annotation.Nullable
    private static Method resolveAddRenderableWidget() {
        if (addRenderableWidgetLookupFailed) {
            return null;
        }
        Method cached = addRenderableWidgetMethod;
        if (cached != null) {
            return cached;
        }
        synchronized (PlayerProfileMenuEvents.class) {
            if (addRenderableWidgetMethod != null) {
                return addRenderableWidgetMethod;
            }
            if (addRenderableWidgetLookupFailed) {
                return null;
            }
            for (Method candidate : Screen.class.getDeclaredMethods()) {
                if (!"addRenderableWidget".equals(candidate.getName()) || candidate.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = candidate.getParameterTypes()[0];
                if (param.isAssignableFrom(AbstractWidget.class)) {
                    candidate.setAccessible(true);
                    addRenderableWidgetMethod = candidate;
                    return candidate;
                }
            }
            addRenderableWidgetLookupFailed = true;
            TableCraft.LOGGER.error("TableCraft: could not resolve Screen.addRenderableWidget; Player Profile menu buttons are disabled.");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends AbstractWidget> T addRenderableWidget(Screen screen, T widget) {
        Method m = resolveAddRenderableWidget();
        if (m == null) {
            return widget;
        }
        try {
            return (T) m.invoke(screen, widget);
        } catch (ReflectiveOperationException e) {
            TableCraft.LOGGER.error("TableCraft: failed to add profile menu widget", e);
            return widget;
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof TitleScreen) {
            int w = 116;
            int h = 20;
            addRenderableWidget(screen, Button.builder(Component.translatable("gui.tablecraft.profile.button"), b -> mc.setScreen(new PlayerProfileScreen(screen)))
                    .bounds(screen.width - w - 8, screen.height - h - 8, w, h)
                    .build());
        } else if (screen instanceof PauseScreen) {
            int w = 160;
            int h = 20;
            addRenderableWidget(screen, Button.builder(Component.translatable("gui.tablecraft.profile.button"), b -> mc.setScreen(new PlayerProfileScreen(screen)))
                    .bounds(screen.width / 2 - w / 2, 52, w, h)
                    .build());
        }
    }
}
