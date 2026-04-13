package net.caduzz.tablecraft.online.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.caduzz.tablecraft.TableCraft;
import net.caduzz.tablecraft.block.ChessMoveLogic;
import net.caduzz.tablecraft.block.entity.ChessBlockEntity.Piece;

/**
 * Cliente HTTP assíncrono para a API externa. Contrato esperado (REST + JSON):
 *
 * <h2>POST {base}/tablecraft/v1/register</h2>
 * Body: {@code {"playerUuid":"<uuid>","displayName":"<name>"}}<br>
 * O {@code playerUuid} é enviado em minúsculas; contas offline (UUID v3) são reetiquetadas para o formato RFC v4
 * para APIs que usam {@code @IsUUID('4')} (rejeitam v3).<br>
 * Response: {@code {"ok":true,"sessionId":"<token>"}}
 *
 * <h2>POST {base}/tablecraft/v1/chess/matchmaking</h2>
 * Header: {@code X-TableCraft-Session: <sessionId>}<br>
 * Na fila: {@code {"ok":true,"queued":true}} — voltar a fazer POST (poll) até haver par.<br>
 * Partida: {@code {"ok":true,"matchId":"...","side":"WHITE"|"BLACK"}}
 *
 * <h2>DELETE {base}/tablecraft/v1/chess/matchmaking</h2>
 * Header: {@code X-TableCraft-Session: <sessionId>}<br>
 * Sair da fila: {@code {"ok":true,"wasInQueue":true|false}}
 *
 * <h2>GET {base}/tablecraft/v1/chess/matches/{matchId}/state</h2>
 * Header: {@code X-TableCraft-Session: <sessionId>}<br>
 * Response (preferido): {@code {"board":[64 ints],"whiteTurn":true,"status":"PLAYING"|"FINISHED"|"WHITE_WIN"|"BLACK_WIN",
 * "result":...,"capturedWhite":0,"capturedBlack":0,"lastMove":{...}}}<br>
 * Opcional (HUD): {@code whiteDisplayName} / {@code blackDisplayName}, ou {@code whitePlayer}/{@code blackPlayer} com
 * {@code displayName}, ou {@code players.WHITE.displayName} (e análogo para BLACK).<br>
 * Perspectiva da sessão: {@code playerDisplayName}, {@code playerPlayerUuid} (ou {@code playerUuid}),
 * {@code opponentDisplayName}, {@code opponentPlayerUuid} — relativos ao dono do header; {@code opponent*} pode vir
 * JSON {@code null} se o lugar estiver vazio. O mesmo esquema aplica-se a {@code /tablecraft/v1/checkers/matches/...}.<br>
 * Com xadrez via chess.js: partida pode ficar {@code FINISHED} com {@code result} (vitória brancas/pretas ou empate).<br>
 * Grelha API: {@code row} 0 = rank 1, {@code col} 0 = file a (converter para o índice interno do mod).<br>
 * Alternativa: objeto com {@code moves:[{fromRow,fromCol,toRow,toCol,side?,at?}]} (reconstitui o tabuleiro no cliente).
 * Também aceita aninhamento {@code data}/{@code state}/{@code snapshot}.
 *
 * <h2>POST {base}/tablecraft/v1/chess/matches/{matchId}/move</h2>
 * Body: {@code {"fromRow":0..7,"fromCol":0..7,"toRow":0..7,"toCol":0..7}} na grelha da API (rate limited).<br>
 * Response: mesmo formato que /state (incl. {@code MoveAppliedResponseDto}: {@code status} ACTIVE|FINISHED, {@code finished},
 * {@code result}, {@code winnerSide}, {@code turn}).
 *
 * <h2>DELETE {base}/tablecraft/v1/checkers/matchmaking</h2>
 * Igual ao de xadrez; header {@code X-TableCraft-Session}.
 *
 * <h2>GET {base}/tablecraft/v1/matches/live</h2>
 * Sem sessão: {@code {"ok":true,"matches":[{matchId, gameType, turn, white:{uuid,displayName}, black:…}]}}.
 *
 * <h2>GET {base}/tablecraft/v1/matches/{matchId}</h2>
 * Sem sessão: {@code ok}, {@code matchId}, {@code gameType}, {@code status}, {@code turn}, {@code state}, jogadores.
 *
 * <h2>POST/DELETE …/chess|checkers/matches/{matchId}/draw-offer</h2>
 * <h2>POST …/draw-response</h2> corpo {@code {"accept":boolean}}
 * <h2>POST …/resign</h2> sem corpo — resposta como move (estado + terminado).
 *
 * <h2>GET {base}/tablecraft/v1/me</h2>
 * Header: {@code X-TableCraft-Session}<br>
 * Perfil: {@code displayName}, {@code rating}, {@code wins}, {@code losses}, {@code memberSince}, …<br>
 * Opcional (retomada de partida): {@code activeChessMatchId} + {@code activeChessYourSide}, ou objeto
 * {@code activeChessMatch}{@code {matchId, yourSide}}, ou sinónimos {@code activeMatchId} / {@code activeChessSide}.
 *
 * <h2>GET {base}/tablecraft/v1/me/history?limit=…</h2>
 * Header: {@code X-TableCraft-Session}<br>
 * Lista: {@code matches:[{ gameType, opponentDisplayName, outcome, yourSide, createdAt, … }]}
 */
public final class GameApiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    private GameApiClient() {
    }

    public static CompletableFuture<String> registerPlayer(String baseUrl, String playerUuid, String displayName) {
        final String apiUuid;
        try {
            apiUuid = normalizePlayerUuidForRegister(playerUuid);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new GameApiException("playerUuid inválido: " + playerUuid, e));
        }
        JsonObject body = new JsonObject();
        body.addProperty("playerUuid", apiUuid);
        body.addProperty("displayName", displayName);
        return sendJson("POST", baseUrl + "/tablecraft/v1/register", null, body.toString()).thenApply(resp -> {
            JsonObject o = parseObject(resp);
            if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
                throw new GameApiException("register rejected: " + resp);
            }
            if (!o.has("sessionId")) {
                throw new GameApiException("register missing sessionId");
            }
            return o.get("sessionId").getAsString();
        });
    }

    public static CompletableFuture<MatchmakingPollResult> chessMatchmaking(String baseUrl, String sessionId) {
        return matchmakingPoll(baseUrl, sessionId, "chess");
    }

    /**
     * Remove a sessão da fila de matchmaking de xadrez na API (não cancela partida já emparelhada).
     *
     * @return {@code wasInQueue} conforme a API; se o campo faltar, assume-se {@code false}.
     */
    public static CompletableFuture<MatchmakingCancelResult> cancelChessMatchmaking(String baseUrl, String sessionId) {
        String url = baseUrl + "/tablecraft/v1/chess/matchmaking";
        return sendJson("DELETE", url, sessionId, null).thenApply(GameApiClient::parseMatchmakingCancel);
    }

    public static CompletableFuture<MatchmakingCancelResult> cancelCheckersMatchmaking(String baseUrl, String sessionId) {
        String url = baseUrl + "/tablecraft/v1/checkers/matchmaking";
        return sendJson("DELETE", url, sessionId, null).thenApply(GameApiClient::parseMatchmakingCancel);
    }

    /** {@code GET /matches/live} — sem cabeçalho de sessão. */
    public static CompletableFuture<MatchCatalogDtos.LiveMatchesResponse> getLiveMatches(String baseUrl) {
        String url = baseUrl + "/tablecraft/v1/matches/live";
        return sendJson("GET", url, null, null).thenApply(GameApiClient::parseLiveMatchesResponse);
    }

    /** {@code GET /matches/:matchId} — sem cabeçalho de sessão. */
    public static CompletableFuture<MatchCatalogDtos.MatchDetailInfo> getMatchDetail(String baseUrl, String matchId) {
        String url = baseUrl + "/tablecraft/v1/matches/" + encode(matchId);
        return sendJson("GET", url, null, null).thenApply(GameApiClient::parseMatchDetail);
    }

    public static CompletableFuture<ChessApiSnapshot> postChessDrawOffer(String baseUrl, String sessionId, String matchId) {
        return sendJson("POST", chessMatchSuffixUrl(baseUrl, matchId, "draw-offer"), sessionId, "{}").thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> deleteChessDrawOffer(String baseUrl, String sessionId, String matchId) {
        return sendJson("DELETE", chessMatchSuffixUrl(baseUrl, matchId, "draw-offer"), sessionId, null).thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> postChessDrawResponse(String baseUrl, String sessionId, String matchId, boolean accept) {
        JsonObject body = new JsonObject();
        body.addProperty("accept", accept);
        return sendJson("POST", chessMatchSuffixUrl(baseUrl, matchId, "draw-response"), sessionId, body.toString()).thenApply(
                json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> postChessResign(String baseUrl, String sessionId, String matchId) {
        return sendJson("POST", chessMatchSuffixUrl(baseUrl, matchId, "resign"), sessionId, null).thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> postCheckersDrawOffer(String baseUrl, String sessionId, String matchId) {
        return sendJson("POST", checkersMatchSuffixUrl(baseUrl, matchId, "draw-offer"), sessionId, "{}").thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> deleteCheckersDrawOffer(String baseUrl, String sessionId, String matchId) {
        return sendJson("DELETE", checkersMatchSuffixUrl(baseUrl, matchId, "draw-offer"), sessionId, null).thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> postCheckersDrawResponse(String baseUrl, String sessionId, String matchId, boolean accept) {
        JsonObject body = new JsonObject();
        body.addProperty("accept", accept);
        return sendJson("POST", checkersMatchSuffixUrl(baseUrl, matchId, "draw-response"), sessionId, body.toString()).thenApply(
                json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<ChessApiSnapshot> postCheckersResign(String baseUrl, String sessionId, String matchId) {
        return sendJson("POST", checkersMatchSuffixUrl(baseUrl, matchId, "resign"), sessionId, null).thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    private static String chessMatchSuffixUrl(String baseUrl, String matchId, String suffix) {
        return baseUrl + "/tablecraft/v1/chess/matches/" + encode(matchId) + "/" + suffix;
    }

    private static String checkersMatchSuffixUrl(String baseUrl, String matchId, String suffix) {
        return baseUrl + "/tablecraft/v1/checkers/matches/" + encode(matchId) + "/" + suffix;
    }

    public record MatchmakingCancelResult(boolean ok, boolean wasInQueue) {
    }

    private static CompletableFuture<MatchmakingPollResult> matchmakingPoll(String baseUrl, String sessionId, String game) {
        return sendJson("POST", baseUrl + "/tablecraft/v1/" + game + "/matchmaking", sessionId, "{}").thenApply(GameApiClient::parseMatchmakingPoll);
    }

    /**
     * Uma chamada ao endpoint de matchmaking: ou ainda na fila ({@link MatchmakingPollResult#queued()}), ou partida encontrada.
     */
    public record MatchmakingPollResult(boolean queued, @javax.annotation.Nullable String matchId, @javax.annotation.Nullable String side) {
        public static MatchmakingPollResult stillQueued() {
            return new MatchmakingPollResult(true, null, null);
        }

        public static MatchmakingPollResult matched(String matchId, String side) {
            return new MatchmakingPollResult(false, matchId, side);
        }
    }

    private static MatchmakingPollResult parseMatchmakingPoll(String resp) {
        JsonObject o = parseObject(resp);
        if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
            throw new GameApiException("matchmaking failed: " + resp);
        }
        boolean queuedFlag = o.has("queued") && o.get("queued").getAsBoolean();
        if (o.has("matchId") && !o.get("matchId").isJsonNull()) {
            String matchId = o.get("matchId").getAsString();
            String side = o.has("side") && !o.get("side").isJsonNull() ? o.get("side").getAsString() : "WHITE";
            return MatchmakingPollResult.matched(matchId, side);
        }
        if (queuedFlag) {
            return MatchmakingPollResult.stillQueued();
        }
        throw new GameApiException("matchmaking unexpected response: " + truncate(resp));
    }

    private static MatchmakingCancelResult parseMatchmakingCancel(String resp) {
        JsonObject o = parseObject(resp);
        if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
            throw new GameApiException("matchmaking cancel rejected: " + truncate(resp));
        }
        boolean wasInQueue = o.has("wasInQueue") && !o.get("wasInQueue").isJsonNull() && o.get("wasInQueue").getAsBoolean();
        return new MatchmakingCancelResult(true, wasInQueue);
    }

    public static CompletableFuture<ChessApiSnapshot> getChessState(String baseUrl, String sessionId, String matchId) {
        String url = baseUrl + "/tablecraft/v1/chess/matches/" + encode(matchId) + "/state";
        return sendJson("GET", url, sessionId, null).thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    /** Como {@link #getChessState} mas inclui {@code yourSide} quando a API o envia no JSON. */
    public static CompletableFuture<ChessStateWithMeta> getChessStateWithMeta(String baseUrl, String sessionId, String matchId) {
        String url = baseUrl + "/tablecraft/v1/chess/matches/" + encode(matchId) + "/state";
        return sendJson("GET", url, sessionId, null).thenApply(GameApiClient::parseChessStateWithMeta);
    }

    public static CompletableFuture<ChessApiSnapshot> postChessMove(String baseUrl, String sessionId, String matchId, int fr, int fc, int tr,
            int tc) {
        JsonObject body = new JsonObject();
        body.addProperty("fromRow", apiRowFromModRow(fr));
        body.addProperty("fromCol", fc);
        body.addProperty("toRow", apiRowFromModRow(tr));
        body.addProperty("toCol", tc);
        String url = baseUrl + "/tablecraft/v1/chess/matches/" + encode(matchId) + "/move";
        return sendJson("POST", url, sessionId, body.toString()).thenApply(json -> parseChessStateWithMeta(json).snapshot());
    }

    public static CompletableFuture<PlayerProfileDTO> getMyProfile(String baseUrl, String sessionId) {
        String url = baseUrl + "/tablecraft/v1/me";
        return sendJson("GET", url, sessionId, null).thenApply(GameApiClient::parsePlayerProfile);
    }

    public static CompletableFuture<List<MatchHistoryDTO>> getMyMatchHistory(String baseUrl, String sessionId, int limit) {
        int lim = Math.clamp(limit, 1, 100);
        String url = baseUrl + "/tablecraft/v1/me/history?limit=" + lim;
        return sendJson("GET", url, sessionId, null).thenApply(GameApiClient::parseMatchHistory);
    }

    private static PlayerProfileDTO parsePlayerProfile(String resp) {
        JsonObject root = parseObject(resp);
        boolean ok = root.has("ok") && root.get("ok").getAsBoolean();
        if (!ok) {
            // Corpo {@code ok:false}: o cliente trata como sessão inválida e volta a {@code POST /register}.
            throw new GameApiException("me rejected: " + truncate(resp), 401);
        }
        JsonObject src = root;
        if (root.has("data") && root.get("data").isJsonObject()) {
            JsonObject d = root.getAsJsonObject("data");
            if (!optString(d, "displayName").isEmpty() || d.has("rating") || d.has("wins")) {
                src = d;
            }
        }
        String playerUuid = coalesceString(src, root, "playerUuid");
        ActiveChessBinding active = parseActiveChessBinding(root, src);
        return new PlayerProfileDTO(
                playerUuid.isEmpty() ? null : playerUuid,
                coalesceString(src, root, "displayName"),
                coalesceInt(src, root, "rating", 0),
                coalesceInt(src, root, "wins", 0),
                coalesceInt(src, root, "losses", 0),
                coalesceString(src, root, "memberSince"),
                active.matchId(),
                active.yourSide());
    }

    private record ActiveChessBinding(@javax.annotation.Nullable String matchId, @javax.annotation.Nullable String yourSide) {
    }

    private static ActiveChessBinding parseActiveChessBinding(JsonObject root, JsonObject src) {
        String mid = firstNonBlank(
                coalesceString(src, root, "activeChessMatchId"),
                coalesceString(src, root, "activeMatchId"),
                coalesceString(src, root, "currentChessMatchId"),
                coalesceString(src, root, "chessMatchId"));
        String side = normalizeChessSideLabel(firstNonBlank(
                coalesceString(src, root, "activeChessYourSide"),
                coalesceString(src, root, "activeChessSide"),
                coalesceString(src, root, "yourSideForActiveMatch")));
        if (mid.isEmpty()) {
            ActiveChessBinding nested = activeChessFromNestedObject(src, "activeChessMatch");
            if (nested.matchId() != null) {
                return new ActiveChessBinding(nested.matchId(), nested.yourSide() != null ? nested.yourSide() : emptyToNull(side));
            }
            nested = activeChessFromNestedObject(root, "activeChessMatch");
            if (nested.matchId() != null) {
                return new ActiveChessBinding(nested.matchId(), nested.yourSide() != null ? nested.yourSide() : emptyToNull(side));
            }
            ActiveChessBinding chess = activeChessFromNestedObject(src, "chess");
            if (chess.matchId() != null) {
                return new ActiveChessBinding(chess.matchId(), chess.yourSide() != null ? chess.yourSide() : emptyToNull(side));
            }
            chess = activeChessFromNestedObject(root, "chess");
            if (chess.matchId() != null) {
                return new ActiveChessBinding(chess.matchId(), chess.yourSide() != null ? chess.yourSide() : emptyToNull(side));
            }
        }
        String midOrNull = mid.isEmpty() ? null : mid;
        return new ActiveChessBinding(midOrNull, emptyToNull(side));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static ActiveChessBinding activeChessFromNestedObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            return new ActiveChessBinding(null, null);
        }
        JsonObject o = parent.getAsJsonObject(key);
        String mid = firstNonBlank(optString(o, "matchId"), optString(o, "id"), optString(o, "match_id"));
        String side = normalizeChessSideLabel(firstNonBlank(optString(o, "yourSide"), optString(o, "side"), optString(o, "color")));
        return new ActiveChessBinding(mid.isEmpty() ? null : mid, side.isEmpty() ? null : side);
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return "";
    }

    /** @return {@code WHITE}, {@code BLACK} ou string vazia se não reconhecido */
    private static String normalizeChessSideLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.equals("BLACK") || u.equals("B") || u.contains("BLACK")) {
            return "BLACK";
        }
        if (u.equals("WHITE") || u.equals("W") || u.contains("WHITE")) {
            return "WHITE";
        }
        return "";
    }

    private static String coalesceString(JsonObject primary, JsonObject fallback, String key) {
        String a = optString(primary, key);
        if (!a.isEmpty()) {
            return a;
        }
        return optString(fallback, key);
    }

    private static int coalesceInt(JsonObject primary, JsonObject fallback, String key, int def) {
        if (primary.has(key) && !primary.get(key).isJsonNull()) {
            return optInt(primary, key, def);
        }
        return optInt(fallback, key, def);
    }

    private static List<MatchHistoryDTO> parseMatchHistory(String resp) {
        JsonObject root = parseObject(resp);
        boolean ok = root.has("ok") && root.get("ok").getAsBoolean();
        if (!ok) {
            throw new GameApiException("history rejected: " + truncate(resp));
        }
        JsonArray arr = null;
        if (root.has("matches") && root.get("matches").isJsonArray()) {
            arr = root.getAsJsonArray("matches");
        } else if (root.has("data") && root.get("data").isJsonObject()) {
            JsonObject d = root.getAsJsonObject("data");
            if (d.has("matches") && d.get("matches").isJsonArray()) {
                arr = d.getAsJsonArray("matches");
            }
        }
        if (arr == null || arr.isEmpty()) {
            return Collections.emptyList();
        }
        List<MatchHistoryDTO> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            if (!arr.get(i).isJsonObject()) {
                continue;
            }
            JsonObject m = arr.get(i).getAsJsonObject();
            String resultStr = null;
            if (m.has("result") && !m.get("result").isJsonNull()) {
                var r = m.get("result");
                if (r.isJsonPrimitive() && r.getAsJsonPrimitive().isString()) {
                    resultStr = r.getAsString();
                } else {
                    resultStr = r.toString();
                }
            }
            out.add(new MatchHistoryDTO(
                    optString(m, "matchId"),
                    optString(m, "gameType"),
                    optString(m, "status"),
                    optString(m, "yourSide"),
                    optString(m, "opponentDisplayName"),
                    optString(m, "outcome"),
                    resultStr,
                    optString(m, "createdAt")));
        }
        return Collections.unmodifiableList(out);
    }

    private static String optString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        var el = o.get(key);
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isString()) {
                return p.getAsString();
            }
            if (p.isNumber()) {
                return Integer.toString(p.getAsInt());
            }
            if (p.isBoolean()) {
                return Boolean.toString(p.getAsBoolean());
            }
        }
        return el.toString();
    }

    private static int optInt(JsonObject o, String key, int defaultValue) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String encode(String matchId) {
        return java.net.URLEncoder.encode(matchId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * API: {@code row} 0 = rank 1 (fundo das brancas); no mod a linha 0 é o lado das pretas.
     */
    private static int apiRowFromModRow(int modRow) {
        return 7 - modRow;
    }

    private static int modRowFromApiRow(int apiRow) {
        return 7 - apiRow;
    }

    private static int[] mapApiBoardFlatToMod(int[] apiFlat) {
        int[] mod = new int[64];
        for (int ar = 0; ar < 8; ar++) {
            for (int ac = 0; ac < 8; ac++) {
                int mr = modRowFromApiRow(ar);
                mod[mr * 8 + ac] = apiFlat[ar * 8 + ac];
            }
        }
        return mod;
    }

    private static String resolveStatusKey(JsonObject primary, JsonObject root) {
        String contract = finishedContractStatusFromRoot(root);
        if (contract != null) {
            return contract;
        }
        String k = deriveGameStatusKey(primary);
        if (!"PLAYING".equals(k)) {
            return k;
        }
        if (primary != root) {
            k = deriveGameStatusKey(root);
            if (!"PLAYING".equals(k)) {
                return k;
            }
        }
        if (root.has("state") && root.get("state").isJsonObject()) {
            k = deriveGameStatusKey(root.getAsJsonObject("state"));
            if (!"PLAYING".equals(k)) {
                return k;
            }
        }
        return "PLAYING";
    }

    /**
     * Contrato {@code MoveAppliedResponseDto} no envelope JSON (ex.: {@code status}, {@code finished}, {@code result}).
     *
     * @return {@code null} se o envelope não especificar estado de partida nesse formato
     */
    @javax.annotation.Nullable
    private static String finishedContractStatusFromRoot(JsonObject root) {
        if (root.has("status") && !root.get("status").isJsonNull()) {
            String st = root.get("status").getAsString().trim();
            if ("ACTIVE".equalsIgnoreCase(st)) {
                return "PLAYING";
            }
            if ("FINISHED".equalsIgnoreCase(st)) {
                return finishedOutcomeFromRoot(root);
            }
        }
        if (root.has("finished") && !root.get("finished").isJsonNull() && root.get("finished").getAsBoolean()) {
            return finishedOutcomeFromRoot(root);
        }
        return null;
    }

    private static String finishedOutcomeFromRoot(JsonObject root) {
        if (root.has("result") && !root.get("result").isJsonNull() && root.get("result").isJsonPrimitive()
                && root.get("result").getAsJsonPrimitive().isString()) {
            String n = normalizeFinishedResult(root.get("result").getAsString());
            if (!"PLAYING".equals(n)) {
                return n;
            }
        }
        if (root.has("winnerSide") && !root.get("winnerSide").isJsonNull() && root.get("winnerSide").isJsonPrimitive()) {
            String w = root.get("winnerSide").getAsString().trim();
            if (w.equalsIgnoreCase("WHITE")) {
                return "WHITE_WIN";
            }
            if (w.equalsIgnoreCase("BLACK")) {
                return "BLACK_WIN";
            }
        }
        return "DRAW";
    }

    private static String deriveGameStatusKey(JsonObject o) {
        String status = "PLAYING";
        if (o.has("status") && !o.get("status").isJsonNull()) {
            status = o.get("status").getAsString().trim();
        }
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return "PLAYING";
        }
        if ("FINISHED".equalsIgnoreCase(status)) {
            String k = finishedResultToKey(o);
            if (!"PLAYING".equals(k)) {
                return k;
            }
            if (o.has("winner") && !o.get("winner").isJsonNull()) {
                var w = o.get("winner");
                if (w.isJsonPrimitive() && w.getAsJsonPrimitive().isString()) {
                    k = normalizeFinishedResult(w.getAsString());
                    if (!"PLAYING".equals(k)) {
                        return k;
                    }
                }
            }
            return "PLAYING";
        }
        if ("WHITE_WIN".equalsIgnoreCase(status)) {
            return "WHITE_WIN";
        }
        if ("BLACK_WIN".equalsIgnoreCase(status)) {
            return "BLACK_WIN";
        }
        if ("DRAW".equalsIgnoreCase(status)) {
            return "DRAW";
        }
        if ("PLAYING".equalsIgnoreCase(status)) {
            return "PLAYING";
        }
        return "PLAYING";
    }

    private static String finishedResultToKey(JsonObject o) {
        if (!o.has("result") || o.get("result").isJsonNull()) {
            return "PLAYING";
        }
        var el = o.get("result");
        if (el.isJsonObject()) {
            return normalizeFinishedResultFromObject(el.getAsJsonObject());
        }
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isString()) {
                String raw = p.getAsString().trim().replace('\u2013', '-');
                return normalizeFinishedResult(raw);
            }
            if (p.isNumber()) {
                int n = p.getAsInt();
                if (n == 1) {
                    return "WHITE_WIN";
                }
                if (n == -1) {
                    return "BLACK_WIN";
                }
                if (n == 0) {
                    return "DRAW";
                }
            }
        }
        return "PLAYING";
    }

    private static String normalizeFinishedResultFromObject(JsonObject obj) {
        for (String k : new String[] { "winner", "outcome", "side", "victor", "winningColor", "winningSide" }) {
            if (obj.has(k) && !obj.get(k).isJsonNull() && obj.get(k).isJsonPrimitive() && obj.get(k).getAsJsonPrimitive().isString()) {
                String v = normalizeFinishedResult(obj.get(k).getAsString());
                if (!"PLAYING".equals(v)) {
                    return v;
                }
            }
        }
        if (obj.has("draw") && !obj.get("draw").isJsonNull() && obj.get("draw").getAsBoolean()) {
            return "DRAW";
        }
        return "PLAYING";
    }

    /** Mapeia {@code result} de partida terminada (chess.js / PGN / API) para chave usada no mod. */
    private static String normalizeFinishedResult(String r) {
        if (r.isEmpty()) {
            return "PLAYING";
        }
        String lower = r.trim().toLowerCase(Locale.ROOT).replace('\u2013', '-').replace('\u2014', '-');
        String compact = lower.replaceAll("\\s+", "");
        if ("white_win".equals(lower) || "white_win".equals(compact) || "whitewin".equals(compact) || "white".equals(lower) || "w".equals(lower)
                || "1-0".equals(compact) || "winner_white".equals(compact) || "win_white".equals(compact)
                || (lower.contains("white") && lower.contains("win"))) {
            return "WHITE_WIN";
        }
        if ("black_win".equals(lower) || "black_win".equals(compact) || "blackwin".equals(compact) || "black".equals(lower) || "b".equals(lower)
                || "0-1".equals(compact) || "winner_black".equals(compact) || "win_black".equals(compact)
                || (lower.contains("black") && lower.contains("win"))) {
            return "BLACK_WIN";
        }
        if ("draw".equals(lower) || "d".equals(lower) || "1/2-1/2".equals(compact) || "stalemate".equals(lower) || "threefold_repetition".equals(lower)
                || "insufficient_material".equals(lower) || "fifty_moves".equals(lower) || "dead_position".equals(lower) || "split".equals(lower)) {
            return "DRAW";
        }
        return "PLAYING";
    }

    /**
     * Minecraft offline usa UUID v3; muitas APIs Nest validam só v4. Mantém os mesmos 122 bits de payload e ajusta
     * versão/variante RFC 4122 para "4" / variante 10, de forma determinística por jogador.
     */
    private static String normalizePlayerUuidForRegister(String playerUuid) {
        UUID u = UUID.fromString(playerUuid.trim());
        if (u.version() == 4) {
            return u.toString().toLowerCase(Locale.ROOT);
        }
        return relabelAsVersion4(u).toString().toLowerCase(Locale.ROOT);
    }

    private static UUID relabelAsVersion4(UUID u) {
        long msb = u.getMostSignificantBits();
        long lsb = u.getLeastSignificantBits();
        msb = (msb & 0xffffffffffff0fffL) | 0x0000000000004000L;
        lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    private static CompletableFuture<String> sendJson(String method, String url, @javax.annotation.Nullable String sessionId, String bodyOrNull) {
        HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(25));
        if (sessionId != null && !sessionId.isEmpty()) {
            rb.header("X-TableCraft-Session", sessionId);
        }
        rb.header("Accept", "application/json");
        if ("GET".equals(method)) {
            rb.header("Cache-Control", "no-cache, no-store");
            rb.header("Pragma", "no-cache");
            rb.GET();
        } else if ("DELETE".equals(method) && (bodyOrNull == null || bodyOrNull.isEmpty())) {
            rb.header("Cache-Control", "no-cache, no-store");
            rb.header("Pragma", "no-cache");
            rb.method("DELETE", HttpRequest.BodyPublishers.noBody());
        } else {
            rb.header("Content-Type", "application/json; charset=UTF-8");
            rb.method(method, bodyOrNull == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(bodyOrNull));
        }
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
            int code = resp.statusCode();
            String b = resp.body() == null ? "" : resp.body();
            if (code < 200 || code >= 300) {
                throw new java.util.concurrent.CompletionException(new GameApiException("HTTP " + code + ": " + truncate(b), code));
            }
            return b;
        });
    }

    private static JsonObject parseObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new GameApiException("Invalid JSON: " + truncate(json), e);
        }
    }

    private static int[] readBoard64(JsonObject o) {
        if (!o.has("board")) {
            throw new GameApiException("missing board");
        }
        JsonArray arr = o.getAsJsonArray("board");
        if (arr.size() != 64) {
            throw new GameApiException("board length " + arr.size() + " != 64");
        }
        int[] b = new int[64];
        for (int i = 0; i < 64; i++) {
            b[i] = arr.get(i).getAsInt();
        }
        return b;
    }

    private static JsonObject unwrapChessState(JsonObject root) {
        if (hasBoardOrMoves(root)) {
            return root;
        }
        String[] keys = { "data", "state", "snapshot", "payload", "game", "match" };
        for (String k : keys) {
            if (root.has(k) && root.get(k).isJsonObject()) {
                JsonObject inner = root.getAsJsonObject(k);
                if (hasBoardOrMoves(inner)) {
                    return inner;
                }
            }
        }
        return root;
    }

    private static boolean hasBoardOrMoves(JsonObject o) {
        if (o.has("board") && o.get("board").isJsonArray() && o.getAsJsonArray("board").size() == 64) {
            return true;
        }
        return o.has("moves") && o.get("moves").isJsonArray();
    }

    private record ChessReplayResult(int[] flatBoard, boolean whiteToMoveNext, Integer lfr, Integer lfc, Integer ltr, Integer ltc) {
    }

    private static Piece[][] newStandardChessBoard() {
        Piece[][] board = new Piece[8][8];
        for (Piece[] row : board) {
            Arrays.fill(row, Piece.EMPTY);
        }
        final int fileD = 3;
        final int fileE = 4;
        final int rankBlack = 0;
        final int rankWhite = 7;
        board[rankBlack][0] = Piece.BLACK_ROOK;
        board[rankBlack][1] = Piece.BLACK_KNIGHT;
        board[rankBlack][2] = Piece.BLACK_BISHOP;
        board[rankBlack][fileD] = Piece.BLACK_QUEEN;
        board[rankBlack][fileE] = Piece.BLACK_KING;
        board[rankBlack][5] = Piece.BLACK_BISHOP;
        board[rankBlack][6] = Piece.BLACK_KNIGHT;
        board[rankBlack][7] = Piece.BLACK_ROOK;
        Arrays.fill(board[1], Piece.BLACK_PAWN);
        Arrays.fill(board[6], Piece.WHITE_PAWN);
        board[rankWhite][0] = Piece.WHITE_ROOK;
        board[rankWhite][1] = Piece.WHITE_KNIGHT;
        board[rankWhite][2] = Piece.WHITE_BISHOP;
        board[rankWhite][fileD] = Piece.WHITE_QUEEN;
        board[rankWhite][fileE] = Piece.WHITE_KING;
        board[rankWhite][5] = Piece.WHITE_BISHOP;
        board[rankWhite][6] = Piece.WHITE_KNIGHT;
        board[rankWhite][7] = Piece.WHITE_ROOK;
        return board;
    }

    private static int[] boardToOrdinals(Piece[][] board) {
        int[] flat = new int[64];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                flat[r * 8 + c] = board[r][c].ordinal();
            }
        }
        return flat;
    }

    private static ChessReplayResult replayChessMoves(JsonArray movesArr) {
        List<JsonObject> moves = new ArrayList<>(movesArr.size());
        for (int i = 0; i < movesArr.size(); i++) {
            if (movesArr.get(i).isJsonObject()) {
                moves.add(movesArr.get(i).getAsJsonObject());
            }
        }
        moves.sort(Comparator.comparing(m -> m.has("at") && !m.get("at").isJsonNull() ? m.get("at").getAsString() : ""));
        Piece[][] board = newStandardChessBoard();
        boolean whiteToMove = true;
        Integer lfr = null, lfc = null, ltr = null, ltc = null;
        for (JsonObject m : moves) {
            int fr = modRowFromApiRow(m.get("fromRow").getAsInt());
            int fc = m.get("fromCol").getAsInt();
            int tr = modRowFromApiRow(m.get("toRow").getAsInt());
            int tc = m.get("toCol").getAsInt();
            boolean movingWhite = m.has("side") ? "WHITE".equalsIgnoreCase(m.get("side").getAsString()) : whiteToMove;
            if (!ChessMoveLogic.applyLegalMove(board, fr, fc, tr, tc, movingWhite)) {
                throw new GameApiException("Replay de moves: lance ilegal ou ordem inválida: " + m);
            }
            whiteToMove = !movingWhite;
            lfr = fr;
            lfc = fc;
            ltr = tr;
            ltc = tc;
        }
        return new ChessReplayResult(boardToOrdinals(board), whiteToMove, lfr, lfc, ltr, ltc);
    }

    private static boolean parseWhiteTurnFromState(JsonObject o, JsonObject root) {
        if (o.has("whiteTurn") && !o.get("whiteTurn").isJsonNull()) {
            return o.get("whiteTurn").getAsBoolean();
        }
        String turn = firstNonBlank(optString(o, "turn"), optString(root, "turn"));
        if (!turn.isEmpty()) {
            return !normalizeChessSideLabel(turn).equals("BLACK");
        }
        return true;
    }

    private static ChessStateWithMeta parseChessStateWithMeta(String json) {
        JsonObject root = parseObject(json);
        JsonObject o = unwrapChessState(root);
        int[] board;
        boolean wt;
        Integer lfr = null, lfc = null, ltr = null, ltc = null;

        if (o.has("board") && o.get("board").isJsonArray() && o.getAsJsonArray("board").size() == 64) {
            board = mapApiBoardFlatToMod(readBoard64(o));
            wt = parseWhiteTurnFromState(o, root);
            if (o.has("lastMove") && o.get("lastMove").isJsonObject()) {
                JsonObject lm = o.getAsJsonObject("lastMove");
                if (lm.has("fromRow")) {
                    lfr = modRowFromApiRow(lm.get("fromRow").getAsInt());
                    lfc = lm.get("fromCol").getAsInt();
                    ltr = modRowFromApiRow(lm.get("toRow").getAsInt());
                    ltc = lm.get("toCol").getAsInt();
                }
            }
        } else if (o.has("moves") && o.get("moves").isJsonArray()) {
            JsonArray ma = o.getAsJsonArray("moves");
            if (ma.isEmpty()) {
                board = boardToOrdinals(newStandardChessBoard());
                wt = parseWhiteTurnFromState(o, root);
            } else {
                TableCraft.LOGGER.debug("API chess state: tabuleiro a partir de {} move(s) (sem board[64])", ma.size());
                ChessReplayResult rep = replayChessMoves(ma);
                board = rep.flatBoard();
                if (o.has("whiteTurn") && !o.get("whiteTurn").isJsonNull()) {
                    wt = o.get("whiteTurn").getAsBoolean();
                } else {
                    String turn = firstNonBlank(optString(o, "turn"), optString(root, "turn"));
                    if (!turn.isEmpty()) {
                        wt = !normalizeChessSideLabel(turn).equals("BLACK");
                    } else {
                        wt = rep.whiteToMoveNext();
                    }
                }
                lfr = rep.lfr();
                lfc = rep.lfc();
                ltr = rep.ltr();
                ltc = rep.ltc();
            }
        } else {
            throw new GameApiException("Chess state sem board[64] nem moves[]: " + truncate(o.toString()));
        }

        String st = resolveStatusKey(o, root);
        int cw = o.has("capturedWhite") ? o.get("capturedWhite").getAsInt() : 0;
        int cb = o.has("capturedBlack") ? o.get("capturedBlack").getAsInt() : 0;
        String wHud = parseChessHudWhiteName(o, root);
        String bHud = parseChessHudBlackName(o, root);
        String pName = firstNonBlank(optString(o, "playerDisplayName"), optString(root, "playerDisplayName"));
        String pUuid = firstNonBlank(
                optString(o, "playerPlayerUuid"),
                optString(o, "playerUuid"),
                optString(root, "playerPlayerUuid"),
                optString(root, "playerUuid"));
        String oName = firstNonBlank(optString(o, "opponentDisplayName"), optString(root, "opponentDisplayName"));
        String oUuid = firstNonBlank(optString(o, "opponentPlayerUuid"), optString(root, "opponentPlayerUuid"));
        ChessApiSnapshot snap = new ChessApiSnapshot(
                board,
                wt,
                st,
                cw,
                cb,
                lfr,
                lfc,
                ltr,
                ltc,
                blankToNull(wHud),
                blankToNull(bHud),
                blankToNull(pName),
                blankToNull(pUuid),
                blankToNull(oName),
                blankToNull(oUuid));
        String yourSide = firstNonBlank(
                normalizeChessSideLabel(optString(o, "yourSide")),
                normalizeChessSideLabel(optString(o, "mySide")),
                normalizeChessSideLabel(optString(o, "playerSide")),
                normalizeChessSideLabel(optString(root, "yourSide")));
        String sideOrNull = yourSide.isEmpty() ? null : yourSide;
        return new ChessStateWithMeta(snap, sideOrNull);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String parseChessHudWhiteName(JsonObject o, JsonObject root) {
        String w = firstNonBlank(
                optString(o, "whiteDisplayName"),
                optString(o, "whiteName"),
                optString(o, "whitePlayerName"),
                optString(root, "whiteDisplayName"),
                catalogPlayerDisplayName(root, "whitePlayer"));
        if (!w.isEmpty()) {
            return w;
        }
        if (o.has("whitePlayer") && o.get("whitePlayer").isJsonObject()) {
            JsonObject wp = o.getAsJsonObject("whitePlayer");
            w = firstNonBlank(optString(wp, "displayName"), optString(wp, "name"), optString(wp, "username"));
            if (!w.isEmpty()) {
                return w;
            }
        }
        if (o.has("players") && o.get("players").isJsonObject()) {
            JsonObject pl = o.getAsJsonObject("players");
            for (String key : new String[] { "WHITE", "white", "White" }) {
                if (pl.has(key) && pl.get(key).isJsonObject()) {
                    JsonObject side = pl.getAsJsonObject(key);
                    w = firstNonBlank(optString(side, "displayName"), optString(side, "name"), optString(side, "username"));
                    if (!w.isEmpty()) {
                        return w;
                    }
                }
            }
        }
        return "";
    }

    private static String parseChessHudBlackName(JsonObject o, JsonObject root) {
        String b = firstNonBlank(
                optString(o, "blackDisplayName"),
                optString(o, "blackName"),
                optString(o, "blackPlayerName"),
                optString(root, "blackDisplayName"),
                catalogPlayerDisplayName(root, "blackPlayer"));
        if (!b.isEmpty()) {
            return b;
        }
        if (o.has("blackPlayer") && o.get("blackPlayer").isJsonObject()) {
            JsonObject bp = o.getAsJsonObject("blackPlayer");
            b = firstNonBlank(optString(bp, "displayName"), optString(bp, "name"), optString(bp, "username"));
            if (!b.isEmpty()) {
                return b;
            }
        }
        if (o.has("players") && o.get("players").isJsonObject()) {
            JsonObject pl = o.getAsJsonObject("players");
            for (String key : new String[] { "BLACK", "black", "Black" }) {
                if (pl.has(key) && pl.get(key).isJsonObject()) {
                    JsonObject side = pl.getAsJsonObject(key);
                    b = firstNonBlank(optString(side, "displayName"), optString(side, "name"), optString(side, "username"));
                    if (!b.isEmpty()) {
                        return b;
                    }
                }
            }
        }
        return "";
    }

    private static String catalogPlayerDisplayName(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return "";
        }
        JsonObject p = root.getAsJsonObject(key);
        return firstNonBlank(optString(p, "displayName"), optString(p, "name"), optString(p, "username"));
    }

    private static MatchCatalogDtos.LiveMatchesResponse parseLiveMatchesResponse(String resp) {
        JsonObject root = parseObject(resp);
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
            throw new GameApiException("live matches rejected: " + truncate(resp));
        }
        if (!root.has("matches") || !root.get("matches").isJsonArray()) {
            return new MatchCatalogDtos.LiveMatchesResponse(true, List.of());
        }
        JsonArray arr = root.getAsJsonArray("matches");
        List<MatchCatalogDtos.LiveMatchSummary> list = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            if (!arr.get(i).isJsonObject()) {
                continue;
            }
            JsonObject m = arr.get(i).getAsJsonObject();
            String mid = optString(m, "matchId");
            if (mid.isEmpty()) {
                continue;
            }
            list.add(new MatchCatalogDtos.LiveMatchSummary(
                    mid,
                    optString(m, "gameType"),
                    optString(m, "turn"),
                    parseCatalogPlayerNullable(m.get("white")),
                    parseCatalogPlayerNullable(m.get("black"))));
        }
        return new MatchCatalogDtos.LiveMatchesResponse(true, Collections.unmodifiableList(list));
    }

    private static MatchCatalogDtos.MatchDetailInfo parseMatchDetail(String resp) {
        JsonObject root = parseObject(resp);
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
            throw new GameApiException("match detail rejected: " + truncate(resp));
        }
        return new MatchCatalogDtos.MatchDetailInfo(
                true,
                optString(root, "matchId"),
                optString(root, "gameType"),
                optString(root, "status"),
                optString(root, "turn"),
                parseCatalogPlayerNullable(root.get("whitePlayer")),
                parseCatalogPlayerNullable(root.get("blackPlayer")));
    }

    @javax.annotation.Nullable
    private static MatchCatalogDtos.CatalogPlayer parseCatalogPlayerNullable(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        String uuid = null;
        if (o.has("uuid") && !o.get("uuid").isJsonNull()) {
            String u = o.get("uuid").getAsString().trim();
            if (!u.isEmpty()) {
                uuid = u;
            }
        }
        String name = firstNonBlank(optString(o, "displayName"), optString(o, "name"));
        if (uuid == null && name.isBlank()) {
            return null;
        }
        return new MatchCatalogDtos.CatalogPlayer(uuid, name.isBlank() ? "?" : name);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

}
