package net.caduzz.tablecraft.item;

import java.util.function.Supplier;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModeTabs {
  public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TableCraft.MOD_ID);

  
  public static final Supplier<CreativeModeTab> GAMES_ITEMS_TABS = CREATIVE_MODE_TAB.register("games_tab",
    () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.CHECKERS_BLOCK.get()))
      .title(Component.translatable("creativetab.tablecraft.games"))
      .displayItems(
        (itemDisplayParamenters, output) -> {
          output.accept(ModBlocks.CHECKERS_BLOCK);
          output.accept(ModBlocks.CHESS_BLOCK);
        }
      )
      .build()
  );
  

  public static void register(IEventBus eventBus) {
    CREATIVE_MODE_TAB.register(eventBus);
  }
}
