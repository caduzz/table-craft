package net.caduzz.tablecraft.network;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ModBlocks;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.client.online.OnlineAuthCoordinator;
import net.caduzz.tablecraft.online.OnlineSide;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TableCraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TableCraftNetworking {
    private static final double MAX_ACTION_DISTANCE_SQ = 8.0 * 8.0;

    private TableCraftNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("1");
        reg.playToServer(ChessTableSettingsActionPayload.TYPE, ChessTableSettingsActionPayload.STREAM_CODEC, TableCraftNetworking::handleChessTableAction);
        reg.playToServer(CheckersTableSettingsActionPayload.TYPE, CheckersTableSettingsActionPayload.STREAM_CODEC,
                TableCraftNetworking::handleCheckersTableAction);
        reg.playToServer(OnlineTableBindPayload.TYPE, OnlineTableBindPayload.STREAM_CODEC, TableCraftNetworking::handleOnlineBind);
        reg.playToServer(OnlineTableClearPayload.TYPE, OnlineTableClearPayload.STREAM_CODEC, TableCraftNetworking::handleOnlineClear);
        reg.playToClient(TableCraftLoginReadyPayload.TYPE, TableCraftLoginReadyPayload.STREAM_CODEC, TableCraftNetworking::handleLoginReadyClient);
    }

    private static void handleLoginReadyClient(TableCraftLoginReadyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(OnlineAuthCoordinator::onWorldLoginReady);
    }

    private static void handleChessTableAction(ChessTableSettingsActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(payload.pos())) {
                return;
            }
            if (!player.level().getBlockState(payload.pos()).is(ModBlocks.CHESS_BLOCK.get())) {
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_ACTION_DISTANCE_SQ) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.pos());
            if (!(be instanceof ChessBlockEntity chess)) {
                return;
            }
            switch (payload.action()) {
                case ChessTableSettingsActionPayload.ACTION_TOGGLE_PREVIOUS_MOVE -> chess.toggleShowPreviousMove();
                case ChessTableSettingsActionPayload.ACTION_TOGGLE_LEGAL_HINTS -> chess.toggleShowLegalMoveHints();
                case ChessTableSettingsActionPayload.ACTION_TIMER_NEXT -> chess.adjustTimePresetFromMenu(player, 1);
                case ChessTableSettingsActionPayload.ACTION_TIMER_PREV -> chess.adjustTimePresetFromMenu(player, -1);
                case ChessTableSettingsActionPayload.ACTION_RESET_BOARD -> chess.resetFromMenu(player);
                default -> {
                }
            }
        });
    }

    private static void handleCheckersTableAction(CheckersTableSettingsActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(payload.pos())) {
                return;
            }
            if (!player.level().getBlockState(payload.pos()).is(ModBlocks.CHECKERS_BLOCK.get())) {
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_ACTION_DISTANCE_SQ) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.pos());
            if (!(be instanceof CheckersBlockEntity checkers)) {
                return;
            }
            switch (payload.action()) {
                case CheckersTableSettingsActionPayload.ACTION_TOGGLE_LEGAL_HINTS -> checkers.toggleShowLegalMoveHints();
                case CheckersTableSettingsActionPayload.ACTION_TIMER_NEXT -> checkers.adjustTimePresetFromMenu(player, 1);
                case CheckersTableSettingsActionPayload.ACTION_TIMER_PREV -> checkers.adjustTimePresetFromMenu(player, -1);
                case CheckersTableSettingsActionPayload.ACTION_RESET_BOARD -> checkers.resetFromMenu(player);
                default -> {
                }
            }
        });
    }

    public static void sendChessTableAction(ChessTableSettingsActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendCheckersTableAction(CheckersTableSettingsActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleOnlineBind(OnlineTableBindPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(payload.pos())) {
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_ACTION_DISTANCE_SQ) {
                return;
            }
            if (payload.sessionId() == null || payload.sessionId().isEmpty() || payload.matchId() == null || payload.matchId().isEmpty()) {
                player.displayClientMessage(Component.literal("Sessão ou partida inválida."), true);
                return;
            }
            OnlineSide side = payload.sideOrdinal() == 1 ? OnlineSide.BLACK : OnlineSide.WHITE;
            if (payload.gameKind() == OnlineTableBindPayload.GAME_CHESS) {
                if (!player.level().getBlockState(payload.pos()).is(ModBlocks.CHESS_BLOCK.get())) {
                    return;
                }
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof ChessBlockEntity chess) {
                    chess.bindOnlineMatch(player, payload.sessionId(), payload.matchId(), side);
                }
            }
        });
    }

    private static void handleOnlineClear(OnlineTableClearPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(payload.pos())) {
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_ACTION_DISTANCE_SQ) {
                return;
            }
            if (payload.gameKind() == OnlineTableBindPayload.GAME_CHESS) {
                BlockEntity be = player.level().getBlockEntity(payload.pos());
                if (be instanceof ChessBlockEntity chess) {
                    chess.clearOnlineSession(player);
                }
            }
        });
    }

    public static void sendOnlineBind(OnlineTableBindPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendOnlineClear(OnlineTableClearPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

}
