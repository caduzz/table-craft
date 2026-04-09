package net.caduzz.tablecraft.client;

import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.ResourceLocation;

/**
 * Lê modelos Blockbench em {@code models/block/} (pawn, tower, horse, bishop, queen, king) com geometria e UVs
 * por face. F3+T ou reinício aplica o reload.
 */
public final class ChessBlockbenchModelLoader {

    public static final ResourceLocation PAWN_MODEL = ResourceLocation.fromNamespaceAndPath("tablecraft", "models/block/pawn.json");
    public static final ResourceLocation TOWER_MODEL = ResourceLocation.fromNamespaceAndPath("tablecraft", "models/block/tower.json");
    public static final ResourceLocation HORSE_MODEL = ResourceLocation.fromNamespaceAndPath("tablecraft", "models/block/horse.json");
    public static final ResourceLocation BISHOP_MODEL = ResourceLocation.fromNamespaceAndPath("tablecraft", "models/block/bishop.json");
    public static final ResourceLocation QUEEN_MODEL = ResourceLocation.fromNamespaceAndPath("tablecraft", "models/block/queen.json");
    public static final ResourceLocation KING_MODEL = ResourceLocation.fromNamespaceAndPath("tablecraft", "models/block/king.json");

    private static final float[][] DEFAULT_PAWN = {
            { 5, 7, 6, 11, 9, 10 }, { 6, 6, 6, 10, 10, 10 }, { 7, 10, 7, 9, 11, 9 }, { 6, 7, 5, 10, 9, 11 },
            { 7, 2, 7, 9, 6, 9 }, { 6, 3, 7, 10, 4, 9 }, { 5, 6, 7, 11, 7, 9 }, { 5, 1, 7, 11, 2, 9 },
            { 7, 3, 6, 9, 4, 10 }, { 7, 6, 5, 9, 7, 11 }, { 7, 1, 5, 9, 2, 11 }, { 5, 0, 5, 11, 1, 11 },
            { 6, 1, 6, 10, 3, 10 }
    };

    private static final float[][] DEFAULT_TOWER = {
            { 5, 8, 5, 11, 10, 6 }, { 6, 8, 6, 10, 9, 10 }, { 5, 8, 10, 11, 10, 11 }, { 5, 8, 6, 6, 10, 10 },
            { 10, 8, 6, 11, 10, 10 }, { 10, 10, 7, 11, 11, 9 }, { 5, 10, 7, 6, 11, 9 }, { 7, 10, 10, 9, 11, 11 },
            { 7, 10, 5, 9, 11, 6 }, { 10, 10, 5, 11, 11, 6 }, { 5, 10, 5, 6, 11, 6 }, { 5, 10, 10, 6, 11, 11 },
            { 10, 10, 10, 11, 11, 11 }, { 5, 0, 5, 11, 1, 11 }, { 6, 7, 6, 10, 8, 10 }, { 7, 4, 7, 9, 7, 9 },
            { 6, 1, 6, 10, 4, 10 }
    };

    private static final float[][] DEFAULT_HORSE = {
            { 6, 9, 6, 10, 10, 10 }, { 7, 10, 7, 9, 11, 9 }, { 6, 7, 5, 10, 9, 11 }, { 7, 6, 11, 9, 8, 13 },
            { 7, 3, 5, 9, 6, 8 }, { 6, 3, 7, 10, 4, 9 }, { 5, 1, 7, 11, 2, 9 }, { 7, 3, 6, 9, 4, 10 },
            { 6, 6, 5, 10, 7, 11 }, { 7, 1, 5, 9, 2, 11 }, { 5, 0, 5, 11, 1, 11 }, { 6, 1, 6, 10, 3, 10 }
    };

    private static final float[][] DEFAULT_BISHOP = {
            { 5, 0, 5, 11, 1, 11 }, { 6, 1, 6, 10, 9, 10 }, { 7, 15, 7, 9, 16, 9 }, { 5, 1, 6, 11, 2, 10 },
            { 6, 1, 5, 10, 2, 11 }, { 5, 9, 5, 11, 11, 8 }, { 5, 11, 5, 11, 12, 7 }, { 5, 13, 7, 11, 14, 8 },
            { 6, 12, 5, 10, 14, 6 }, { 6, 14, 6, 10, 15, 10 }, { 5, 9, 8, 11, 14, 11 }, { 5, 9, 8, 11, 14, 11 },
            { 5, 8, 6, 11, 9, 10 }, { 6, 8, 5, 10, 9, 11 }
    };

    private static final float[][] DEFAULT_QUEEN = {
            { 5, 0, 5, 11, 1, 11 }, { 6, 1, 6, 10, 4, 10 }, { 7, 7, 7, 9, 9, 9 }, { 6, 9, 6, 10, 13, 10 }, { 7, 13, 7, 9, 15, 9 },
            { 5, 12, 6, 6, 14, 7 }, { 6, 12, 10, 7, 14, 11 }, { 9, 12, 10, 10, 14, 11 }, { 10, 12, 9, 11, 14, 10 }, { 5, 12, 9, 6, 14, 10 },
            { 10, 12, 6, 11, 14, 7 }, { 6, 12, 5, 7, 14, 6 }, { 9, 12, 5, 10, 14, 6 }, { 6, 6, 6, 10, 7, 10 }, { 7, 4, 7, 9, 6, 9 },
            { 5, 1, 6, 11, 2, 10 }, { 6, 1, 5, 10, 2, 11 }
    };

    private static final float[][] DEFAULT_KING = {
            { 5, 0, 5, 11, 1, 11 }, { 6, 1, 6, 10, 4, 10 }, { 7, 7, 7, 9, 9, 9 }, { 6, 9, 6, 10, 13, 10 }, { 7, 13, 7, 9, 14, 9 },
            { 7.5f, 13.8f, 7.5f, 8.5f, 16.8f, 8.5f }, { 6.5f, 15.3f, 7.5f, 9.5f, 16.3f, 8.5f },
            { 5, 12, 6, 6, 14, 7 }, { 6, 12, 10, 7, 14, 11 }, { 9, 12, 10, 10, 14, 11 }, { 10, 12, 9, 11, 14, 10 }, { 5, 12, 9, 6, 14, 10 },
            { 10, 12, 6, 11, 14, 7 }, { 6, 12, 5, 7, 14, 6 }, { 9, 12, 5, 10, 14, 6 }, { 6, 6, 6, 10, 7, 10 }, { 7, 4, 7, 9, 6, 9 },
            { 5, 1, 6, 11, 2, 10 }, { 6, 1, 5, 10, 2, 11 }
    };

    private static volatile ChessPieceModelData pawnModelData = ChessPieceModelData.fromGeometryOnly(DEFAULT_PAWN);
    private static volatile ChessPieceModelData towerModelData = ChessPieceModelData.fromGeometryOnly(DEFAULT_TOWER);
    private static volatile ChessPieceModelData horseModelData = ChessPieceModelData.fromGeometryOnly(DEFAULT_HORSE);
    private static volatile ChessPieceModelData bishopModelData = ChessPieceModelData.fromGeometryOnly(DEFAULT_BISHOP);
    private static volatile ChessPieceModelData queenModelData = ChessPieceModelData.fromGeometryOnly(DEFAULT_QUEEN);
    private static volatile ChessPieceModelData kingModelData = ChessPieceModelData.fromGeometryOnly(DEFAULT_KING);

    private ChessBlockbenchModelLoader() {
    }

    public static ChessPieceModelData pawnModel() {
        return pawnModelData;
    }

    public static ChessPieceModelData towerModel() {
        return towerModelData;
    }

    public static ChessPieceModelData horseModel() {
        return horseModelData;
    }

    public static ChessPieceModelData bishopModel() {
        return bishopModelData;
    }

    public static ChessPieceModelData queenModel() {
        return queenModelData;
    }

    public static ChessPieceModelData kingModel() {
        return kingModelData;
    }

    public static PreparableReloadListener reloader() {
        return new Reloader();
    }

    private static final class Reloader implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(
                PreparableReloadListener.PreparationBarrier barrier,
                ResourceManager resourceManager,
                ProfilerFiller prepareProfiler,
                ProfilerFiller applyProfiler,
                Executor backgroundExecutor,
                Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> Parsed.parse(resourceManager), backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(parsed -> parsed.apply(), gameExecutor);
        }

        @Override
        public String getName() {
            return "tablecraft:chess_blockbench_models";
        }
    }

    private record Parsed(ChessPieceModelData pawn, ChessPieceModelData tower, ChessPieceModelData horse, ChessPieceModelData bishop,
            ChessPieceModelData queen, ChessPieceModelData king) {
        static Parsed parse(ResourceManager rm) {
            return new Parsed(
                    loadModel(rm, PAWN_MODEL, DEFAULT_PAWN),
                    loadModel(rm, TOWER_MODEL, DEFAULT_TOWER),
                    loadModel(rm, HORSE_MODEL, DEFAULT_HORSE),
                    loadModel(rm, BISHOP_MODEL, DEFAULT_BISHOP),
                    loadModel(rm, QUEEN_MODEL, DEFAULT_QUEEN),
                    loadModel(rm, KING_MODEL, DEFAULT_KING));
        }

        void apply() {
            pawnModelData = pawn;
            towerModelData = tower;
            horseModelData = horse;
            bishopModelData = bishop;
            queenModelData = queen;
            kingModelData = king;
        }
    }

    private static ChessPieceModelData loadModel(ResourceManager rm, ResourceLocation path, float[][] fallback) {
        try {
            var opt = rm.getResource(path);
            if (opt.isEmpty()) {
                return ChessPieceModelData.fromGeometryOnly(fallback);
            }
            try (Reader reader = opt.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return ChessPieceModelData.parse(root, fallback);
            }
        } catch (Exception ignored) {
            return ChessPieceModelData.fromGeometryOnly(fallback);
        }
    }
}
