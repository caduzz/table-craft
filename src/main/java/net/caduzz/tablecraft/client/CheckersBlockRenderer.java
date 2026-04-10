package net.caduzz.tablecraft.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.caduzz.tablecraft.block.CheckersBlock;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
import net.caduzz.tablecraft.block.entity.CheckersBlockEntity.Piece;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Tabuleiro: overlays verdes, peças, destaque da seleção, animação. */
public final class CheckersBlockRenderer implements BlockEntityRenderer<CheckersBlockEntity> {

    private static final ResourceLocation WHITE_TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float CELL = 1f / 8f;
    private static final float TEXEL_WORLD = 1f / 16f;
    /** Escala base das peças de damas no tabuleiro. */
    private static final float PIECE_BASE_SCALE = 1.25f;
    /** Raio no plano XZ (peça “larga”). */
    private static final float HALF_W = PIECE_BASE_SCALE * TEXEL_WORLD * 0.5f;
    /** Meia-altura menor → perfil retangular / ficha baixa. */
    private static final float HALF_H = HALF_W * 0.32f;
    /** Topo da base de madeira (face do tabuleiro) — igual a {@link CheckersBlock#BOARD_SURFACE_Y}. */
    private static final float BOARD_TOP_Y = CheckersBlock.BOARD_SURFACE_Y;
    private static final float OVERLAY_Y = BOARD_TOP_Y + 0.004f;
    private static final float CELL_INSET = CELL * 0.12f;
    private static final int RGB_CHECKERS_WHITE = (238 << 16) | (238 << 8) | 245;
    private static final int RGB_CHECKERS_BLACK = (38 << 16) | (38 << 8) | 48;
    private static final int RGB_CHECKERS_NEUTRAL = (200 << 16) | (200 << 8) | 200;

    public CheckersBlockRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(CheckersBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
            int packedLight, int packedOverlay) {
        poseStack.pushPose();
        CheckersBlock.applyBoardRenderRotation(poseStack, be.getBlockState().getValue(CheckersBlock.FACING));

        VertexConsumer translucent = buffer.getBuffer(RenderType.entityTranslucent(WHITE_TEX));
        if (be.showsLegalMoveHints()) {
            for (int i = 0; i < be.getValidMoveCount(); i++) {
                int packed = be.getValidMovePacked(i);
                int tr = packed / 8;
                int tc = packed % 8;
                drawValidCellOverlay(poseStack.last(), translucent, tr, tc, packedLight);
            }
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEX));

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = be.getPiece(r, c);
                if (p == Piece.EMPTY) {
                    continue;
                }
                boolean selected = be.hasSelection() && r == be.getSelectedRow() && c == be.getSelectedCol();
                float scale = selected ? 1.35f : 1f;
                float yLift = selected ? 4f * HALF_H : 0f;
                float cx = (c + 0.5f) * CELL;
                float cz = (r + 0.5f) * CELL;
                float cy = BOARD_TOP_Y + HALF_H * scale + 0.002f + yLift;
                int rgb = checkersRgbPacked(p);
                drawBox(poseStack.last(), consumer, cx, cy, cz, HALF_W * scale, HALF_H * scale, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                        255, packedLight);
            }
        }

        if (be.hasActiveAnimation()) {
            float t = be.getMoveProgress(partialTick);
            float fcx = (be.getAnimFromCol() + 0.5f) * CELL;
            float fcz = (be.getAnimFromRow() + 0.5f) * CELL;
            float tcx = (be.getAnimToCol() + 0.5f) * CELL;
            float tcz = (be.getAnimToRow() + 0.5f) * CELL;
            float cx = Mth.lerp(t, fcx, tcx);
            float cz = Mth.lerp(t, fcz, tcz);
            float lift = 2.5f * HALF_H * Mth.sin((float) Math.PI * t);
            float cy = BOARD_TOP_Y + HALF_H + 0.002f + lift;
            Piece ap = be.getAnimPiece();
            int rgb = checkersRgbPacked(ap);
            drawBox(poseStack.last(), consumer, cx, cy, cz, HALF_W, HALF_H, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255, packedLight);
        }

        poseStack.popPose();
    }

    private static void drawValidCellOverlay(PoseStack.Pose pose, VertexConsumer consumer, int row, int col, int light) {
        float minX = col * CELL + CELL_INSET;
        float maxX = (col + 1) * CELL - CELL_INSET;
        float minZ = row * CELL + CELL_INSET;
        float maxZ = (row + 1) * CELL - CELL_INSET;
        if (minX >= maxX || minZ >= maxZ) {
            return;
        }
        int cr = 50;
        int cg = 220;
        int cb = 80;
        int ca = 100;
        Matrix4f mat = pose.pose();
        float y = OVERLAY_Y;
        float nx = 0;
        float ny = 1;
        float nz = 0;
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, minX, y, minZ, nx, ny, nz);
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, maxX, y, minZ, nx, ny, nz);
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, maxX, y, maxZ, nx, ny, nz);
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, minX, y, maxZ, nx, ny, nz);
    }

    private static int checkersRgbPacked(Piece p) {
        return switch (p) {
            case WHITE_MAN, WHITE_KING -> RGB_CHECKERS_WHITE;
            case BLACK_MAN, BLACK_KING -> RGB_CHECKERS_BLACK;
            default -> RGB_CHECKERS_NEUTRAL;
        };
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer consumer, float cx, float cy, float cz,
            float halfW, float halfH, int cr, int cg, int cb, int ca, int light) {
        float minX = cx - halfW;
        float maxX = cx + halfW;
        float minY = cy - halfH;
        float maxY = cy + halfH;
        float minZ = cz - halfW;
        float maxZ = cz + halfW;
        Matrix4f mat = pose.pose();

        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                0, -1, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
                0, 1, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ,
                0, 0, -1);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ,
                0, 0, 1);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ,
                -1, 0, 0);
        quad(consumer, mat, pose, light, cr, cg, cb, ca, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ,
                1, 0, 0);
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f mat,
            PoseStack.Pose pose,
            int light,
            int cr,
            int cg,
            int cb,
            int ca,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float nx,
            float ny,
            float nz) {
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, x0, y0, z0, nx, ny, nz);
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, x1, y1, z1, nx, ny, nz);
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, x2, y2, z2, nx, ny, nz);
        vertexT(consumer, mat, pose, light, cr, cg, cb, ca, x3, y3, z3, nx, ny, nz);
    }

    private static void vertexT(
            VertexConsumer consumer,
            Matrix4f mat,
            PoseStack.Pose pose,
            int light,
            int cr,
            int cg,
            int cb,
            int ca,
            float px,
            float py,
            float pz,
            float nx,
            float ny,
            float nz) {
        consumer.addVertex(mat, px, py, pz)
                .setColor(cr, cg, cb, ca)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
