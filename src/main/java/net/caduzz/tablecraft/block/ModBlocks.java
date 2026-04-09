package net.caduzz.tablecraft.block;

import java.util.function.Supplier;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TableCraft.MOD_ID);
  /** Tabuleiro de damas 8x8 com BlockEntity (estado persistido). */
  public static final DeferredBlock<Block> CHECKERS_BLOCK = registerBlock("checkers_block",
      () -> new CheckersBlock(
          BlockBehaviour.Properties.of()
              .mapColor(MapColor.COLOR_BROWN)
              .strength(2.5F, 3.0F)
              .sound(SoundType.WOOD)));

  /** Tabuleiro de xadrez 8x8 com BlockEntity próprio. */
  public static final DeferredBlock<Block> CHESS_BLOCK = registerBlock("chess_block",
      () -> new ChessBlock(
          BlockBehaviour.Properties.of()
              .mapColor(MapColor.COLOR_BLACK)
              .strength(2.5F, 3.0F)
              .sound(SoundType.WOOD)));

  private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
    DeferredBlock<T> toReturn = BLOCKS.register(name, block);
    registerBlockItem(name, toReturn);
    return toReturn;
  }

  private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
    ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
  }

  public static void register(IEventBus eventBus) {
    BLOCKS.register(eventBus);
  }
}
