package net.caduzz.tablecraft.client;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caduzz.tablecraft.block.ChessBlock;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity.Piece;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ChessBlockRenderer implements BlockEntityRenderer<ChessBlockEntity> {
    private static final ResourceLocation WHITE_TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation PAWN_WHITE_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_white.png");
    private static final ResourceLocation PAWN_BLACK_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_black.png");
    private static final ResourceLocation TOWER_WHITE_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_white.png");
    private static final ResourceLocation TOWER_BLACK_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_black.png");
    private static final ResourceLocation HORSE_WHITE_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_white.png");
    private static final ResourceLocation HORSE_BLACK_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_black.png");
    private static final ResourceLocation BISHOP_WHITE_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_white.png");
    private static final ResourceLocation BISHOP_BLACK_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_black.png");
    private static final ResourceLocation QUEEN_WHITE_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_white.png");
    private static final ResourceLocation QUEEN_BLACK_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_black.png");
    private static final ResourceLocation KING_WHITE_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_white.png");
    private static final ResourceLocation KING_BLACK_TEX = ResourceLocation.fromNamespaceAndPath("tablecraft", "textures/block/chess_black.png");
    /** Escala base global das peças de xadrez no tabuleiro. */
    private static final float CHESS_PIECE_BASE_SCALE = 1.25f;
    /**
     * Modelo do pawn tem base de ~6 pixels no JSON; reduzimos para ~0.9 pixel no mundo.
     */
    private static final float PAWN_MODEL_SCALE = 0.15f;
    private static final float TOWER_MODEL_SCALE = 0.15f;
    private static final float HORSE_MODEL_SCALE = 0.15f;
    private static final float BISHOP_MODEL_SCALE = 0.15f;
    private static final float QUEEN_MODEL_SCALE = 0.15f;
    private static final float KING_MODEL_SCALE = 0.15f;
    private static final float CELL = 1f / 8f;
    private static final float OVERLAY_Y = ChessBlock.BOARD_SURFACE_Y + 0.004f;
    private static final float CELL_INSET = CELL * 0.12f;
    /** RGB em um int: (r<<16)|(g<<8)|b — fallback sólido sem alocação. */
    private static final int RGB_FALLBACK_WHITE = (236 << 16) | (236 << 8) | 242;
    private static final int RGB_FALLBACK_BLACK = (36 << 16) | (36 << 8) | 44;

    public ChessBlockRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /**
     * Abaixo do padrão vanilla (64) para tabuleiros: reduz custo com vários tabuleiros na mesma base.
     * Aumente se precisar ver peças 3D de muito longe.
     */
    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(ChessBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        ChessBlock.applyBoardRenderRotation(poseStack, be.getBlockState().getValue(ChessBlock.FACING));

        VertexConsumer translucent = buffer.getBuffer(RenderType.entityTranslucent(WHITE_TEX));
        if (be.showsPreviousMove() && be.hasLastMoveMarker()) {
            drawPreviousMoveOverlay(poseStack.last(), translucent, be.getLastMoveFromRow(), be.getLastMoveFromCol(), packedLight);
            drawPreviousMoveOverlay(poseStack.last(), translucent, be.getLastMoveToRow(), be.getLastMoveToCol(), packedLight);
        }
        if (be.showsLegalMoveHints()) {
            for (int i = 0; i < be.getValidMoveCount(); i++) {
                int packed = be.getValidMovePacked(i);
                if (!isPackedInCaptureMoves(be, packed)) {
                    drawValidCellOverlay(poseStack.last(), translucent, packed / 8, packed % 8, packedLight);
                }
            }
            for (int i = 0; i < be.getCaptureMoveCount(); i++) {
                int packed = be.getCaptureMovePacked(i);
                drawCaptureCellOverlay(poseStack.last(), translucent, packed / 8, packed % 8, packedLight);
            }
            for (int i = 0; i < be.getBlockedByCheckMoveCount(); i++) {
                int packed = be.getBlockedByCheckMovePacked(i);
                drawBlockedByCheckOverlay(poseStack.last(), translucent, packed / 8, packed % 8, packedLight);
            }
        }

        /*
         * MultiBufferSource.BufferSource encerra o batch do RenderType anterior ao pedir outro que usa o buffer
         * compartilhado. Por isso não podemos guardar vários VertexConsumer de uma vez: só o último estaria válido.
         * Uma textura por passo: getBuffer → todas as peças daquele tipo → próximo tipo.
         */
        PoseStack.Pose pose = poseStack.last();
        renderPiecePass(pose, be, buffer, packedLight, Piece.WHITE_PAWN, PAWN_WHITE_TEX, ChessBlockRenderer::renderPawnModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.BLACK_PAWN, PAWN_BLACK_TEX, ChessBlockRenderer::renderPawnModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.WHITE_ROOK, TOWER_WHITE_TEX, ChessBlockRenderer::renderTowerModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.BLACK_ROOK, TOWER_BLACK_TEX, ChessBlockRenderer::renderTowerModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.WHITE_KNIGHT, HORSE_WHITE_TEX, ChessBlockRenderer::renderHorseWhiteModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.BLACK_KNIGHT, HORSE_BLACK_TEX, ChessBlockRenderer::renderHorseBlackModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.WHITE_BISHOP, BISHOP_WHITE_TEX, ChessBlockRenderer::renderBishopModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.BLACK_BISHOP, BISHOP_BLACK_TEX, ChessBlockRenderer::renderBishopModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.WHITE_QUEEN, QUEEN_WHITE_TEX, ChessBlockRenderer::renderQueenModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.BLACK_QUEEN, QUEEN_BLACK_TEX, ChessBlockRenderer::renderQueenModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.WHITE_KING, KING_WHITE_TEX, ChessBlockRenderer::renderKingModel);
        renderPiecePass(pose, be, buffer, packedLight, Piece.BLACK_KING, KING_BLACK_TEX, ChessBlockRenderer::renderKingModel);
        renderFallbackSolidPieces(pose, be, buffer, packedLight);
        if (be.hasActiveAnimation()) {
            float t = be.getMoveProgress(partialTick);
            float fcx = (be.getAnimFromCol() + 0.5f) * CELL;
            float fcz = (be.getAnimFromRow() + 0.5f) * CELL;
            float tcx = (be.getAnimToCol() + 0.5f) * CELL;
            float tcz = (be.getAnimToRow() + 0.5f) * CELL;
            float cx = Mth.lerp(t, fcx, tcx);
            float cz = Mth.lerp(t, fcz, tcz);
            float lift = CELL * 0.55f * Mth.sin((float) Math.PI * t);
            poseStack.pushPose();
            poseStack.translate(0f, lift, 0f);
            renderSingleAnimatedPiece(poseStack.last(), buffer, packedLight, be.getAnimPiece(), cx, cz, 1f);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** Uma peça em voo: um {@code getBuffer} por tipo (regra do BufferSource). */
    private static void renderSingleAnimatedPiece(PoseStack.Pose pose, MultiBufferSource buffer, int packedLight, Piece p, float cx, float cz,
            float scale) {
        if (p == Piece.WHITE_PAWN) {
            renderPawnModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(PAWN_WHITE_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.BLACK_PAWN) {
            renderPawnModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(PAWN_BLACK_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.WHITE_ROOK) {
            renderTowerModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TOWER_WHITE_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.BLACK_ROOK) {
            renderTowerModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TOWER_BLACK_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.WHITE_KNIGHT) {
            renderHorseWhiteModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(HORSE_WHITE_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.BLACK_KNIGHT) {
            renderHorseBlackModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(HORSE_BLACK_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.WHITE_BISHOP) {
            renderBishopModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(BISHOP_WHITE_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.BLACK_BISHOP) {
            renderBishopModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(BISHOP_BLACK_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.WHITE_QUEEN) {
            renderQueenModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(QUEEN_WHITE_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.BLACK_QUEEN) {
            renderQueenModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(QUEEN_BLACK_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.WHITE_KING) {
            renderKingModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(KING_WHITE_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p == Piece.BLACK_KING) {
            renderKingModel(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(KING_BLACK_TEX)), cx, cz, scale, packedLight);
            return;
        }
        if (p != Piece.EMPTY) {
            float h = pieceHeight(p);
            float cy = ChessBlock.BOARD_SURFACE_Y + h + 0.002f;
            float halfW = CELL * 0.28f * CHESS_PIECE_BASE_SCALE * scale;
            int rgbPacked = chessFallbackRgbPacked(p);
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEX));
            drawBox(pose, consumer, cx, cy, cz, halfW, h * scale, (rgbPacked >> 16) & 0xFF, (rgbPacked >> 8) & 0xFF, rgbPacked & 0xFF, 255,
                    packedLight);
        }
    }

    @FunctionalInterface
    private interface TexturedPieceDrawer {
        void draw(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light);
    }

    private static void renderPiecePass(PoseStack.Pose pose, ChessBlockEntity be, MultiBufferSource buffer, int packedLight, Piece pieceKind,
            ResourceLocation texture, TexturedPieceDrawer drawer) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (be.getPiece(r, c) != pieceKind) {
                    continue;
                }
                float cx = (c + 0.5f) * CELL;
                float cz = (r + 0.5f) * CELL;
                float scale = selectionScale(be, r, c);
                drawer.draw(pose, consumer, cx, cz, scale, packedLight);
            }
        }
    }

    private static float selectionScale(ChessBlockEntity be, int r, int c) {
        return be.hasSelection() && r == be.getSelectedRow() && c == be.getSelectedCol() ? 1.15f : 1f;
    }

    private static boolean isStandardTexturedPiece(Piece p) {
        return switch (p) {
            case WHITE_PAWN, BLACK_PAWN, WHITE_ROOK, BLACK_ROOK, WHITE_KNIGHT, BLACK_KNIGHT, WHITE_BISHOP, BLACK_BISHOP, WHITE_QUEEN, BLACK_QUEEN,
                    WHITE_KING, BLACK_KING -> true;
            default -> false;
        };
    }

    private static void renderFallbackSolidPieces(PoseStack.Pose pose, ChessBlockEntity be, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEX));
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = be.getPiece(r, c);
                if (p == Piece.EMPTY || isStandardTexturedPiece(p)) {
                    continue;
                }
                float cx = (c + 0.5f) * CELL;
                float cz = (r + 0.5f) * CELL;
                float scale = selectionScale(be, r, c);
                float h = pieceHeight(p);
                float cy = ChessBlock.BOARD_SURFACE_Y + h + 0.002f;
                float halfW = CELL * 0.28f * CHESS_PIECE_BASE_SCALE * scale;
                int rgbPacked = chessFallbackRgbPacked(p);
                drawBox(pose, consumer, cx, cy, cz, halfW, h * scale, (rgbPacked >> 16) & 0xFF, (rgbPacked >> 8) & 0xFF, rgbPacked & 0xFF, 255,
                        packedLight);
            }
        }
    }

    private static float pieceHeight(Piece p) {
        return switch (p) {
            case WHITE_PAWN, BLACK_PAWN -> CELL * 0.13f * CHESS_PIECE_BASE_SCALE;
            case WHITE_ROOK, BLACK_ROOK, WHITE_KNIGHT, BLACK_KNIGHT, WHITE_BISHOP, BLACK_BISHOP -> CELL * 0.17f * CHESS_PIECE_BASE_SCALE;
            case WHITE_QUEEN, BLACK_QUEEN -> CELL * 0.20f * CHESS_PIECE_BASE_SCALE;
            case WHITE_KING, BLACK_KING -> CELL * 0.22f * CHESS_PIECE_BASE_SCALE;
            default -> CELL * 0.14f * CHESS_PIECE_BASE_SCALE;
        };
    }

    private static int chessFallbackRgbPacked(Piece p) {
        return switch (p) {
            case WHITE_PAWN, WHITE_ROOK, WHITE_KNIGHT, WHITE_BISHOP, WHITE_QUEEN, WHITE_KING -> RGB_FALLBACK_WHITE;
            case BLACK_PAWN, BLACK_ROOK, BLACK_KNIGHT, BLACK_BISHOP, BLACK_QUEEN, BLACK_KING -> RGB_FALLBACK_BLACK;
            default -> RGB_FALLBACK_WHITE;
        };
    }

    private static void renderPawnModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * PAWN_MODEL_SCALE, ChessBlockbenchModelLoader.pawnModel(),
                light, false);
    }

    private static void renderTowerModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * TOWER_MODEL_SCALE, ChessBlockbenchModelLoader.towerModel(),
                light, false);
    }

    private static void renderHorseWhiteModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * HORSE_MODEL_SCALE, ChessBlockbenchModelLoader.horseModel(),
                light, true);
    }

    private static void renderHorseBlackModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * HORSE_MODEL_SCALE, ChessBlockbenchModelLoader.horseModel(),
                light, false);
    }

    private static void renderBishopModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * BISHOP_MODEL_SCALE,
                ChessBlockbenchModelLoader.bishopModel(), light, false);
    }

    private static void renderQueenModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * QUEEN_MODEL_SCALE, ChessBlockbenchModelLoader.queenModel(),
                light, false);
    }

    private static void renderKingModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scale, int light) {
        renderTexturedPieceModel(pose, consumer, cx, cz, CHESS_PIECE_BASE_SCALE * scale * KING_MODEL_SCALE, ChessBlockbenchModelLoader.kingModel(),
                light, false);
    }

    private static void renderTexturedPieceModel(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cz, float scaled,
            ChessPieceModelData model, int light, boolean rotateY180) {
        float baseY = ChessBlock.BOARD_SURFACE_Y + 0.002f;
        for (ChessPieceModelData.TexturedElement e : model.elements()) {
            drawTexturedElement(pose, consumer, cx, baseY, cz, scaled, e, light, rotateY180);
        }
    }

    private static void drawTexturedElement(PoseStack.Pose pose, VertexConsumer consumer, float cx, float baseY, float cz, float scaled,
            ChessPieceModelData.TexturedElement e, int light, boolean rotateY180) {
        float fx = ((e.fromX - 8f) / 16f) * scaled;
        float tx = ((e.toX - 8f) / 16f) * scaled;
        float fz = ((e.fromZ - 8f) / 16f) * scaled;
        float tz = ((e.toZ - 8f) / 16f) * scaled;
        if (rotateY180) {
            fx = -fx;
            tx = -tx;
            fz = -fz;
            tz = -tz;
        }
        float minX = cx + Math.min(fx, tx);
        float maxX = cx + Math.max(fx, tx);
        float minY = baseY + (e.fromY / 16f) * scaled;
        float maxY = baseY + (e.toY / 16f) * scaled;
        float minZ = cz + Math.min(fz, tz);
        float maxZ = cz + Math.max(fz, tz);
        Matrix4f mat = pose.pose();
        ChessPieceModelData.FaceUv f;
        if ((f = e.down) != null) {
            quadTex(consumer, mat, pose, light, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0, -1, 0, f.u0(), f.v1(),
                    f.u1(), f.v1(), f.u1(), f.v0(), f.u0(), f.v0());
        }
        if ((f = e.up) != null) {
            quadTex(consumer, mat, pose, light, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 0, 1, 0, f.u0(), f.v1(),
                    f.u0(), f.v0(), f.u1(), f.v0(), f.u1(), f.v1());
        }
        if ((f = e.north) != null) {
            quadTex(consumer, mat, pose, light, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0, 0, -1, f.u0(), f.v1(),
                    f.u1(), f.v1(), f.u1(), f.v0(), f.u0(), f.v0());
        }
        if ((f = e.south) != null) {
            quadTex(consumer, mat, pose, light, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0, 0, 1, f.u0(), f.v1(),
                    f.u0(), f.v0(), f.u1(), f.v0(), f.u1(), f.v1());
        }
        if ((f = e.west) != null) {
            quadTex(consumer, mat, pose, light, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, -1, 0, 0, f.u0(), f.v1(),
                    f.u0(), f.v0(), f.u1(), f.v0(), f.u1(), f.v1());
        }
        if ((f = e.east) != null) {
            quadTex(consumer, mat, pose, light, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, 1, 0, 0, f.u0(), f.v1(),
                    f.u0(), f.v0(), f.u1(), f.v0(), f.u1(), f.v1());
        }
    }

    private static void quadTex(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose, int light, float x0, float y0, float z0, float x1,
            float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, float u0, float v0,
            float u1, float v1, float u2, float v2, float u3, float v3) {
        int cr = 255;
        int cg = 255;
        int cb = 255;
        int ca = 255;
        vertexTex(consumer, mat, pose, light, cr, cg, cb, ca, x0, y0, z0, nx, ny, nz, u0, v0);
        vertexTex(consumer, mat, pose, light, cr, cg, cb, ca, x1, y1, z1, nx, ny, nz, u1, v1);
        vertexTex(consumer, mat, pose, light, cr, cg, cb, ca, x2, y2, z2, nx, ny, nz, u2, v2);
        vertexTex(consumer, mat, pose, light, cr, cg, cb, ca, x3, y3, z3, nx, ny, nz, u3, v3);
    }

    private static void drawValidCellOverlay(PoseStack.Pose pose, VertexConsumer consumer, int row, int col, int light) {
        float minX = col * CELL + CELL_INSET;
        float maxX = (col + 1) * CELL - CELL_INSET;
        float minZ = row * CELL + CELL_INSET;
        float maxZ = (row + 1) * CELL - CELL_INSET;
        Matrix4f mat = pose.pose();
        float y = OVERLAY_Y;
        vertexT(consumer, mat, pose, light, 90, 170, 255, 90, minX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 90, 170, 255, 90, maxX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 90, 170, 255, 90, maxX, y, maxZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 90, 170, 255, 90, minX, y, maxZ, 0, 1, 0);
    }

    private static void drawBlockedByCheckOverlay(PoseStack.Pose pose, VertexConsumer consumer, int row, int col, int light) {
        float minX = col * CELL + CELL_INSET;
        float maxX = (col + 1) * CELL - CELL_INSET;
        float minZ = row * CELL + CELL_INSET;
        float maxZ = (row + 1) * CELL - CELL_INSET;
        Matrix4f mat = pose.pose();
        float y = OVERLAY_Y + 0.0003f;
        vertexT(consumer, mat, pose, light, 255, 220, 60, 120, minX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 255, 220, 60, 120, maxX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 255, 220, 60, 120, maxX, y, maxZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 255, 220, 60, 120, minX, y, maxZ, 0, 1, 0);
    }

    private static void drawCaptureCellOverlay(PoseStack.Pose pose, VertexConsumer consumer, int row, int col, int light) {
        float minX = col * CELL + CELL_INSET;
        float maxX = (col + 1) * CELL - CELL_INSET;
        float minZ = row * CELL + CELL_INSET;
        float maxZ = (row + 1) * CELL - CELL_INSET;
        Matrix4f mat = pose.pose();
        float y = OVERLAY_Y + 0.0002f;
        vertexT(consumer, mat, pose, light, 255, 60, 60, 130, minX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 255, 60, 60, 130, maxX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 255, 60, 60, 130, maxX, y, maxZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 255, 60, 60, 130, minX, y, maxZ, 0, 1, 0);
    }

    private static void drawPreviousMoveOverlay(PoseStack.Pose pose, VertexConsumer consumer, int row, int col, int light) {
        float minX = col * CELL + CELL_INSET;
        float maxX = (col + 1) * CELL - CELL_INSET;
        float minZ = row * CELL + CELL_INSET;
        float maxZ = (row + 1) * CELL - CELL_INSET;
        Matrix4f mat = pose.pose();
        float y = OVERLAY_Y + 0.0001f;
        vertexT(consumer, mat, pose, light, 160, 80, 255, 105, minX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 160, 80, 255, 105, maxX, y, minZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 160, 80, 255, 105, maxX, y, maxZ, 0, 1, 0);
        vertexT(consumer, mat, pose, light, 160, 80, 255, 105, minX, y, maxZ, 0, 1, 0);
    }

    private static boolean isPackedInCaptureMoves(ChessBlockEntity be, int packed) {
        for (int i = 0; i < be.getCaptureMoveCount(); i++) {
            if (be.getCaptureMovePacked(i) == packed) {
                return true;
            }
        }
        return false;
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cy, float cz, float halfW, float halfH, int cr,
            int cg, int cb, int ca, int light) {
        float minX = cx - halfW;
        float maxX = cx + halfW;
        float minY = cy - halfH;
        float maxY = cy + halfH;
        float minZ = cz - halfW;
        float maxZ = cz + halfW;
        drawBoxFull(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ, cr, cg, cb, ca, light);
    }

    private static void drawBoxFull(PoseStack.Pose pose, VertexConsumer consumer, float minX, float minY, float minZ, float maxX, float maxY,
            float maxZ, int cr, int cg, int cb, int ca, int light) {
        Matrix4f mat = pose.pose();
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0, -1, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 0, 1, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, 0, 0, -1);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, 1, 0, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, -1, 0, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, 0, 0, 1);
    }

    private static void quad(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose, int light, int cr, int cg, int cb, int ca, float x0,
            float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny,
            float nz) {
        vertexSolid(consumer, mat, pose, light, cr, cg, cb, ca, x0, y0, z0, nx, ny, nz);
        vertexSolid(consumer, mat, pose, light, cr, cg, cb, ca, x1, y1, z1, nx, ny, nz);
        vertexSolid(consumer, mat, pose, light, cr, cg, cb, ca, x2, y2, z2, nx, ny, nz);
        vertexSolid(consumer, mat, pose, light, cr, cg, cb, ca, x3, y3, z3, nx, ny, nz);
    }

    private static void vertexSolid(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose, int light, int cr, int cg, int cb, int ca, float px,
            float py, float pz, float nx, float ny, float nz) {
        consumer.addVertex(mat, px, py, pz).setColor(cr, cg, cb, ca).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static void vertexTex(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose, int light, int cr, int cg, int cb, int ca, float px,
            float py, float pz, float nx, float ny, float nz, float u, float v) {
        consumer.addVertex(mat, px, py, pz).setColor(cr, cg, cb, ca).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static void vertexT(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose, int light, int cr, int cg, int cb, int ca, float px,
            float py, float pz, float nx, float ny, float nz) {
        vertexSolid(consumer, mat, pose, light, cr, cg, cb, ca, px, py, pz, nx, ny, nz);
    }
}
