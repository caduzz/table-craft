package net.caduzz.tablecraft.block.entity;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TableCraft.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CheckersBlockEntity>> CHECKERS =
            BLOCK_ENTITY_TYPES.register("checkers_block", () ->
                    BlockEntityType.Builder.of(CheckersBlockEntity::new, ModBlocks.CHECKERS_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChessBlockEntity>> CHESS =
            BLOCK_ENTITY_TYPES.register("chess_block", () ->
                    BlockEntityType.Builder.of(ChessBlockEntity::new, ModBlocks.CHESS_BLOCK.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
