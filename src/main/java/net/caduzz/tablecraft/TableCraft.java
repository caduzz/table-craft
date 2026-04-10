package net.caduzz.tablecraft;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.ModBlockEntities;
import net.caduzz.tablecraft.config.TableCraftConfig;
import net.caduzz.tablecraft.item.ModCreativeModeTabs;
import net.caduzz.tablecraft.item.ModItems;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(TableCraft.MOD_ID)
public class TableCraft {
    public static final String MOD_ID = "tablecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TableCraft(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, TableCraftConfig.SPEC, MOD_ID + "-common.toml");

        NeoForge.EVENT_BUS.register(this);

        ModCreativeModeTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().sendSystemMessage(Component.literal("Bem-vindo ao TableCraft!"));
    }
}
