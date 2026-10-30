package app;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Обращения к REST API гостиницы (проект 1_Api). При ошибке бросает Exception с текстом от сервера.
 */
public class Api {

    /** Адрес API. Если сервер запущен на другом компьютере, заменить localhost на его адрес. */
    static final String BASE = "http://localhost:8080/api/";

    /** Токен, выданный при входе; уходит в заголовке Authorization. */
    static String token;

    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    static JsonElement get(String path) throws Exception {
        return send("GET", path, null);
    }

    static JsonElement post(String path, JsonObject body) throws Exception {
        return send("POST", path, body);
    }

    static JsonElement put(String path, JsonObject body) throws Exception {
        return send("PUT", path, body);
    }

    static JsonElement send(String method, String path, JsonObject body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()));
        if (token != null) {
            b.header("Authorization", token);
        }
        HttpResponse<String> r;
        try {
            r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new Exception("Сервер API недоступен (" + BASE + "). Запустите проект 1_Api");
        }
        JsonElement json = r.body().isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(r.body());
        if (r.statusCode() >= 400) {
            JsonElement m = json.isJsonObject() ? json.getAsJsonObject().get("message") : null;
            throw new Exception(m != null && !m.isJsonNull() && !m.getAsString().isBlank()
                    ? m.getAsString() : "Ошибка сервера: " + r.statusCode());
        }
        return json;
    }

    /** body("login", "admin", "password", "1") — тело запроса из пар ключ/значение. */
    static JsonObject body(Object... pairs) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            if (v instanceof Number n) {
                o.addProperty((String) pairs[i], n);
            } else {
                o.addProperty((String) pairs[i], v == null ? null : v.toString());
            }
        }
        return o;
    }

    static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
