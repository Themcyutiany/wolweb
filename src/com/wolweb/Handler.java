package com.wolweb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** HTTP 路由: 设置管理员 / 登录 / 添加删除 MAC / 唤醒 */
public class Handler implements HttpHandler {
    private static final long SESSION_TIMEOUT_MS = 12L * 60 * 60 * 1000;

    private final Store store;
    private final Wol wol = new Wol();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public Handler(Store store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery() == null ? "" : ex.getRequestURI().getQuery();
            String method = ex.getRequestMethod();

            if (path.equals("/favicon.ico")) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            if (path.equals("/") || path.isEmpty()) {
                if (!store.isConfigured()) redirect(ex, "/setup");
                else if (!isLoggedIn(ex)) redirect(ex, "/login");
                else handleMain(ex, query);
                return;
            }
            if (path.equals("/api") || path.startsWith("/api/")) {
                handleApi(ex, path, method);
                return;
            }
            switch (path) {
                case "/setup" -> handleSetup(ex, method);
                case "/login" -> handleLogin(ex, method);
                case "/logout" -> handleLogout(ex);
                case "/wake" -> handleWake(ex, query);
                case "/add" -> handleAdd(ex);
                case "/delete" -> handleDelete(ex, query);
                default -> respond(ex, 404, "404 Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                respond(ex, 500, "服务器错误: " + e.getMessage());
            } catch (IOException ignored) {
            }
        }
    }

    private void handleMain(HttpExchange ex, String query) throws IOException {
        respond(ex, 200, Pages.mainPage(store, param(query, "msg"), "1".equals(param(query, "ok"))));
    }

    private void handleSetup(HttpExchange ex, String method) throws IOException {
        if (store.isConfigured()) {
            redirect(ex, isLoggedIn(ex) ? "/" : "/login");
            return;
        }
        if (method.equals("POST")) {
            Map<String, String> form = parseForm(readBody(ex));
            String user = form.get("user");
            String password = form.get("password");
            String confirm = form.get("confirm");
            if (password == null || !password.equals(confirm)) {
                respond(ex, 200, Pages.setupForm("两次输入的密码不一致"));
                return;
            }
            if (store.setupAdmin(user, password)) {
                login(ex);
                redirect(ex, "/?msg=" + enc("管理员设置成功, 请添加要唤醒的电脑") + "&ok=1");
            } else {
                respond(ex, 200, Pages.setupForm("用户名不能为空, 密码至少 6 位"));
            }
            return;
        }
        respond(ex, 200, Pages.setupForm(null));
    }

    private void handleLogin(HttpExchange ex, String method) throws IOException {
        if (!store.isConfigured()) {
            redirect(ex, "/setup");
            return;
        }
        if (isLoggedIn(ex)) {
            redirect(ex, "/");
            return;
        }
        if (method.equals("POST")) {
            Map<String, String> form = parseForm(readBody(ex));
            if (store.verifyLogin(form.get("user"), form.get("password"))) {
                login(ex);
                redirect(ex, "/");
            } else {
                respond(ex, 200, Pages.loginForm("用户名或密码错误"));
            }
            return;
        }
        respond(ex, 200, Pages.loginForm(null));
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String token = cookie(ex, "WOL_SESSION");
        if (token != null) sessions.remove(token);
        ex.getResponseHeaders().add("Set-Cookie", "WOL_SESSION=; Path=/; Max-Age=0; HttpOnly");
        redirect(ex, "/login");
    }

    private void handleWake(HttpExchange ex, String query) throws IOException {
        if (!requireLogin(ex)) return;
        String id = param(query, "id");
        Store.MacEntry mac = store.getMac(id == null ? "" : id);
        if (mac == null) {
            redirect(ex, "/?msg=" + enc("未找到该电脑") + "&ok=0");
            return;
        }
        try {
            List<String> sent = wol.wake(mac.addr());
            redirect(ex, "/?msg=" + enc("已向 " + mac.name() + " 发送唤醒包 (广播: " + String.join(", ", sent) + ")") + "&ok=1");
        } catch (Exception e) {
            redirect(ex, "/?msg=" + enc("唤醒失败: " + e.getMessage()) + "&ok=0");
        }
    }

    private void handleAdd(HttpExchange ex) throws IOException {
        if (!requireLogin(ex)) return;
        Map<String, String> form = parseForm(readBody(ex));
        String name = form.get("name");
        String addr = form.get("addr");
        if (name == null || name.trim().isEmpty()) {
            redirect(ex, "/?msg=" + enc("名称不能为空") + "&ok=0");
            return;
        }
        try {
            Wol.parseMac(addr == null ? "" : addr);
        } catch (IllegalArgumentException e) {
            redirect(ex, "/?msg=" + enc(e.getMessage()) + "&ok=0");
            return;
        }
        store.addMac(name, addr);
        redirect(ex, "/?msg=" + enc("已添加: " + name) + "&ok=1");
    }

    private void handleDelete(HttpExchange ex, String query) throws IOException {
        if (!requireLogin(ex)) return;
        store.removeMac(param(query, "id"));
        redirect(ex, "/?msg=" + enc("已删除") + "&ok=1");
    }

    private boolean requireLogin(HttpExchange ex) throws IOException {
        if (isLoggedIn(ex)) return true;
        redirect(ex, "/login");
        return false;
    }

    // ---------- API ----------

    private void handleApi(HttpExchange ex, String path, String method) throws IOException {
        String body = method.equals("GET") || method.equals("DELETE") ? "" : readBody(ex);
        switch (path) {
            case "/api/health" -> apiJson(ex, 200, Map.of("status", "ok", "version", "1.3"));
            case "/api/login" -> apiLogin(ex, body);
            case "/api/macs" -> {
                if (method.equals("GET")) apiListMacs(ex);
                else if (method.equals("POST")) apiAddMac(ex, body);
                else jsonError(ex, 405, "不支持的请求方法");
            }
            case "/api/wake" -> apiWake(ex, body);
            default -> {
                if (path.startsWith("/api/macs/") && method.equals("DELETE")) {
                    apiDeleteMac(ex, path.substring("/api/macs/".length()));
                } else {
                    jsonError(ex, 404, "接口不存在");
                }
            }
        }
    }

    private void apiLogin(HttpExchange ex, String body) throws IOException {
        Map<String, Object> json;
        try {
            json = Json.object(Json.parse(body));
        } catch (Exception e) {
            jsonError(ex, 400, "JSON 格式错误");
            return;
        }
        String user = Json.string(json.get("user"));
        String password = Json.string(json.get("password"));
        if (!store.isConfigured()) {
            jsonError(ex, 403, "尚未设置管理员, 请先访问网页完成初始化");
            return;
        }
        if (store.verifyLogin(user, password)) {
            String token = UUID.randomUUID().toString().replace("-", "");
            sessions.put(token, System.currentTimeMillis());
            ex.getResponseHeaders().add("Set-Cookie",
                    "WOL_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
            apiJson(ex, 200, Map.of("ok", true, "token", token, "user", store.adminUser()));
        } else {
            jsonError(ex, 401, "用户名或密码错误");
        }
    }

    private void apiListMacs(HttpExchange ex) throws IOException {
        if (!apiAuth(ex)) return;
        List<Object> list = new ArrayList<>();
        for (Store.MacEntry m : store.listMacs()) {
            list.add(Map.of("id", m.id(), "name", m.name(), "addr", m.addr()));
        }
        apiJson(ex, 200, Map.of("macs", list));
    }

    private void apiAddMac(HttpExchange ex, String body) throws IOException {
        if (!apiAuth(ex)) return;
        Map<String, Object> json;
        try {
            json = Json.object(Json.parse(body));
        } catch (Exception e) {
            jsonError(ex, 400, "JSON 格式错误");
            return;
        }
        String name = Json.string(json.get("name"));
        String addr = Json.string(json.get("addr"));
        if (name == null || name.trim().isEmpty()) {
            jsonError(ex, 400, "名称不能为空");
            return;
        }
        try {
            Wol.parseMac(addr == null ? "" : addr);
        } catch (IllegalArgumentException e) {
            jsonError(ex, 400, e.getMessage());
            return;
        }
        Store.MacEntry entry = store.addMac(name, addr);
        apiJson(ex, 201, Map.of("ok", true, "id", entry.id(), "name", entry.name(), "addr", entry.addr()));
    }

    private void apiDeleteMac(HttpExchange ex, String id) throws IOException {
        if (!apiAuth(ex)) return;
        if (store.removeMac(id == null ? "" : id)) {
            apiJson(ex, 200, Map.of("ok", true));
        } else {
            jsonError(ex, 404, "未找到该电脑");
        }
    }

    private void apiWake(HttpExchange ex, String body) throws IOException {
        if (!apiAuth(ex)) return;
        Map<String, Object> json;
        try {
            json = Json.object(Json.parse(body));
        } catch (Exception e) {
            jsonError(ex, 400, "JSON 格式错误");
            return;
        }
        String id = Json.string(json.get("id"));
        String macAddr = Json.string(json.get("mac"));
        Store.MacEntry entry = null;
        if (id != null && !id.isEmpty()) entry = store.getMac(id);
        if (entry == null && macAddr != null && !macAddr.isEmpty()) entry = new Store.MacEntry("", "", macAddr);
        if (entry == null) {
            jsonError(ex, 404, "未找到该电脑 (请传 id 或 mac)");
            return;
        }
        try {
            List<String> sent = wol.wake(entry.addr());
            apiJson(ex, 200, Map.of("ok", true, "name", entry.name(), "mac", entry.addr(), "sent", sent));
        } catch (Exception e) {
            jsonError(ex, 400, "唤醒失败: " + e.getMessage());
        }
    }

    private boolean apiAuth(HttpExchange ex) throws IOException {
        if (validSession(apiToken(ex))) return true;
        jsonError(ex, 401, "未登录或会话已过期, 请先调用 /api/login");
        return false;
    }

    private static void apiJson(HttpExchange ex, int code, Object data) throws IOException {
        byte[] bytes = Json.stringify(data).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void jsonError(HttpExchange ex, int code, String msg) throws IOException {
        apiJson(ex, code, Map.of("error", msg));
    }

    // ---------- 会话 ----------

    private boolean isLoggedIn(HttpExchange ex) {
        return validSession(apiToken(ex));
    }

    /** 从请求头 Authorization: Bearer <token> 或 Cookie 中取登录凭证 */
    private static String apiToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7).trim();
        return cookie(ex, "WOL_SESSION");
    }

    private boolean validSession(String token) {
        if (token == null) return false;
        Long last = sessions.get(token);
        if (last == null) return false;
        if (System.currentTimeMillis() - last > SESSION_TIMEOUT_MS) {
            sessions.remove(token);
            return false;
        }
        sessions.put(token, System.currentTimeMillis());
        return true;
    }

    private void login(HttpExchange ex) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, System.currentTimeMillis());
        ex.getResponseHeaders().add("Set-Cookie",
                "WOL_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
    }

    private static String cookie(HttpExchange ex, String name) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).trim().equals(name)) {
                return part.substring(eq + 1).trim();
            }
        }
        return null;
    }

    // ---------- 工具 ----------

    private static void respond(HttpExchange ex, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            map.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
        return map;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String param(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}








