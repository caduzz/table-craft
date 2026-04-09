package net.caduzz.tablecraft.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Geometria + UVs por face extraídos do JSON Blockbench (formato modelo de bloco). O renderer usa as UVs
 * normalizadas; a textura bound no {@link net.minecraft.client.renderer.RenderType} deve ser a PNG da peça
 * (branca ou preta) com o mesmo layout de atlas que o export do Blockbench.
 */
public final class ChessPieceModelData {

    public record FaceUv(float u0, float v0, float u1, float v1) {
        static FaceUv unit() {
            return new FaceUv(0f, 0f, 1f, 1f);
        }
    }

    public static final class TexturedElement {
        public final float fromX;
        public final float fromY;
        public final float fromZ;
        public final float toX;
        public final float toY;
        public final float toZ;
        @Nullable public final FaceUv north;
        @Nullable public final FaceUv east;
        @Nullable public final FaceUv south;
        @Nullable public final FaceUv west;
        @Nullable public final FaceUv up;
        @Nullable public final FaceUv down;

        public TexturedElement(float fromX, float fromY, float fromZ, float toX, float toY, float toZ, @Nullable FaceUv north,
                @Nullable FaceUv east, @Nullable FaceUv south, @Nullable FaceUv west, @Nullable FaceUv up, @Nullable FaceUv down) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toY = toY;
            this.toZ = toZ;
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
            this.up = up;
            this.down = down;
        }
    }

    private final TexturedElement[] elements;

    public ChessPieceModelData(TexturedElement[] elements) {
        this.elements = elements;
    }

    public TexturedElement[] elements() {
        return elements;
    }

    public static ChessPieceModelData fromGeometryOnly(float[][] boxes) {
        FaceUv u = FaceUv.unit();
        TexturedElement[] els = new TexturedElement[boxes.length];
        for (int i = 0; i < boxes.length; i++) {
            float[] b = boxes[i];
            els[i] = new TexturedElement(b[0], b[1], b[2], b[3], b[4], b[5], u, u, u, u, u, u);
        }
        return new ChessPieceModelData(els);
    }

    public static ChessPieceModelData parse(JsonObject root, float[][] fallbackBoxes) {
        if (!root.has("elements") || !root.get("elements").isJsonArray()) {
            return fromGeometryOnly(fallbackBoxes);
        }
        int tw = 16;
        int th = 16;
        if (root.has("texture_size") && root.get("texture_size").isJsonArray()) {
            JsonArray ts = root.getAsJsonArray("texture_size");
            if (ts.size() >= 2) {
                tw = ts.get(0).getAsInt();
                th = ts.get(1).getAsInt();
            }
        }
        JsonArray arr = root.getAsJsonArray("elements");
        List<TexturedElement> list = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            if (!o.has("from") || !o.has("to")) {
                continue;
            }
            JsonArray from = o.getAsJsonArray("from");
            JsonArray to = o.getAsJsonArray("to");
            if (from.size() < 3 || to.size() < 3) {
                continue;
            }
            float fx = from.get(0).getAsFloat();
            float fy = from.get(1).getAsFloat();
            float fz = from.get(2).getAsFloat();
            float tx = to.get(0).getAsFloat();
            float ty = to.get(1).getAsFloat();
            float tz = to.get(2).getAsFloat();
            FaceUv north = null;
            FaceUv east = null;
            FaceUv south = null;
            FaceUv west = null;
            FaceUv up = null;
            FaceUv down = null;
            if (o.has("faces") && o.get("faces").isJsonObject()) {
                JsonObject faces = o.getAsJsonObject("faces");
                north = parseFaceUv(faces, "north", tw, th);
                east = parseFaceUv(faces, "east", tw, th);
                south = parseFaceUv(faces, "south", tw, th);
                west = parseFaceUv(faces, "west", tw, th);
                up = parseFaceUv(faces, "up", tw, th);
                down = parseFaceUv(faces, "down", tw, th);
            }
            list.add(new TexturedElement(fx, fy, fz, tx, ty, tz, north, east, south, west, up, down));
        }
        if (list.isEmpty()) {
            return fromGeometryOnly(fallbackBoxes);
        }
        return new ChessPieceModelData(list.toArray(new TexturedElement[0]));
    }

    @Nullable
    private static FaceUv parseFaceUv(JsonObject faces, String key, int tw, int th) {
        if (!faces.has(key) || !faces.get(key).isJsonObject()) {
            return null;
        }
        JsonObject f = faces.getAsJsonObject(key);
        if (!f.has("uv") || !f.get("uv").isJsonArray()) {
            return FaceUv.unit();
        }
        JsonArray uv = f.getAsJsonArray("uv");
        if (uv.size() < 4) {
            return FaceUv.unit();
        }
        float u0 = uv.get(0).getAsFloat() / tw;
        float v0 = uv.get(1).getAsFloat() / th;
        float u1 = uv.get(2).getAsFloat() / tw;
        float v1 = uv.get(3).getAsFloat() / th;
        return new FaceUv(u0, v0, u1, v1);
    }
}
