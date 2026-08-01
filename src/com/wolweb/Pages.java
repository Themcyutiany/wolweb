package com.wolweb;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

/** 生成网页 HTML */
public final class Pages {
    private Pages() {}

    private static volatile int currentPort = 9999;
    private static volatile String cachedIps = null;
    private static volatile long cacheTime = 0;

    /** 由 Main 设置实际端口, 供地址显示使用 */
    public static void setPort(int port) {
        currentPort = port;
        cachedIps = null;
    }

    private static final String CSS = """
        *{box-sizing:border-box;margin:0;padding:0}
        body{font-family:"Segoe UI","Microsoft YaHei",system-ui,sans-serif;background:#f3f5f9;color:#222;line-height:1.6}
        .wrap{max-width:760px;margin:0 auto;padding:24px 16px 40px}
        header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:20px}
        h1{font-size:22px;color:#1f3a5f}
        .who{font-size:13px;color:#666}
        .who a{color:#2f6fd0;text-decoration:none}
        .card{background:#fff;border-radius:10px;box-shadow:0 1px 4px rgba(0,0,0,.08);padding:20px;margin-bottom:18px}
        h2{font-size:16px;color:#1f3a5f;margin-bottom:12px}
        table{width:100%;border-collapse:collapse;font-size:14px}
        th,td{padding:9px 8px;text-align:left;border-bottom:1px solid #eceff4}
        th{color:#667;font-weight:600}
        input{width:100%;padding:9px 10px;border:1px solid #ccd3de;border-radius:6px;font-size:14px;margin-bottom:10px}
        .row{display:flex;gap:10px}
        .row input{margin-bottom:0}
        button{padding:9px 16px;border:0;border-radius:6px;background:#2f6fd0;color:#fff;font-size:14px;cursor:pointer}
        button:hover{background:#265caf}
        button.danger{background:#d9534f}
        button.danger:hover{background:#c0433f}
        button.mini{padding:5px 10px;font-size:13px}
        form.inline{display:inline}
        .msg{padding:10px 14px;border-radius:6px;margin-bottom:14px;font-size:14px}
        .msg.ok{background:#e5f6ec;color:#1d7a43;border:1px solid #b7e5c8}
        .msg.err{background:#fdeaea;color:#b02a2a;border:1px solid #f3c4c4}
        .hint{font-size:13px;color:#999;margin-top:8px}
        .empty{color:#999;text-align:center;padding:18px 0}
        footer{font-size:12px;color:#999;text-align:center;margin-top:26px}
        .brand{text-align:center;margin:26px 0 18px}
        .brand h1{font-size:24px;margin-bottom:4px}
        .brand p{color:#888;font-size:14px}
        .login{max-width:340px;margin:0 auto}
        .login button{width:100%;margin-top:4px}
        .login input{margin-bottom:12px}
        label{display:block;font-size:13px;color:#555;margin-bottom:4px}
        """;

    public static String page(String title, String body, String adminName) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + esc(title) + " - WOL Web</title><style>" + CSS + "</style></head><body><div class=\"wrap\">"
            + "<header><h1>\uD83D\uDDA5\uFE0F 局域网唤醒 (WOL)</h1>"
            + (adminName == null || adminName.isEmpty() ? ""
                : "<span class=\"who\">管理员: " + esc(adminName) + " · <a href=\"/logout\">退出登录</a></span>")
            + "</header><main>" + body + "</main>"
            + "<footer>本机访问地址: " + localIps() + "</footer>"
            + "</div></body></html>";
    }

    public static String brandPage(String title, String inner) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + esc(title) + " - WOL Web</title><style>" + CSS + "</style></head><body><div class=\"wrap\">"
            + "<div class=\"brand\"><h1>\uD83D\uDDA5\uFE0F 局域网唤醒 (WOL)</h1><p>通过网页远程唤醒局域网电脑</p></div>"
            + inner
            + "<footer>本机访问地址: " + localIps() + "</footer>"
            + "</div></body></html>";
    }

    public static String msg(String text, boolean ok) {
        return "<div class=\"msg " + (ok ? "ok" : "err") + "\">" + esc(text) + "</div>";
    }

    public static String setupForm(String error) {
        String body = "<div class=\"login card\"><h2>首次使用 · 设置管理员账号</h2>"
            + (error == null ? "" : msg(error, false))
            + "<form method=\"post\" action=\"/setup\">"
            + "<label>管理员用户名</label><input name=\"user\" value=\"admin\" required>"
            + "<label>密码 (至少 6 位)</label><input type=\"password\" name=\"password\" required>"
            + "<label>确认密码</label><input type=\"password\" name=\"confirm\" required>"
            + "<button type=\"submit\">创建账号并进入</button></form></div>";
        return brandPage("设置管理员", body);
    }

    public static String loginForm(String error) {
        String body = "<div class=\"login card\"><h2>管理员登录</h2>"
            + (error == null ? "" : msg(error, false))
            + "<form method=\"post\" action=\"/login\">"
            + "<label>用户名</label><input name=\"user\" required>"
            + "<label>密码</label><input type=\"password\" name=\"password\" required>"
            + "<button type=\"submit\">登录</button></form></div>";
        return brandPage("登录", body);
    }

    public static String mainPage(Store store, String message, boolean msgOk) {
        StringBuilder body = new StringBuilder();
        if (message != null && !message.isEmpty()) body.append(msg(message, msgOk));

        body.append("<div class=\"card\"><h2>电脑列表</h2><table><tr><th>名称</th><th>MAC 地址</th><th style=\"width:150px\">操作</th></tr>");
        List<Store.MacEntry> list = store.listMacs();
        if (list.isEmpty()) {
            body.append("<tr><td colspan=\"3\" class=\"empty\">还没有配置电脑, 请在下方添加</td></tr>");
        }
        for (Store.MacEntry m : list) {
            body.append("<tr><td>").append(esc(m.name())).append("</td>")
                .append("<td><code>").append(esc(m.addr())).append("</code></td><td>")
                .append("<form class=\"inline\" method=\"post\" action=\"/wake?id=").append(esc(m.id())).append("\">")
                .append("<button class=\"mini\" type=\"submit\">唤醒</button></form> ")
                .append("<form class=\"inline\" method=\"post\" action=\"/delete?id=").append(esc(m.id())).append("\" ")
                .append("onsubmit=\"return confirm('确定删除该电脑?')\">")
                .append("<button class=\"mini danger\" type=\"submit\">删除</button></form>")
                .append("</td></tr>");
        }
        body.append("</table></div>");

        body.append("<div class=\"card\"><h2>添加电脑</h2><form method=\"post\" action=\"/add\">")
            .append("<div class=\"row\"><input name=\"name\" placeholder=\"名称, 如: 客厅电脑\" required>")
            .append("<input name=\"addr\" placeholder=\"MAC 地址, 如: AA:BB:CC:DD:EE:FF\" required></div>")
            .append("<p class=\"hint\">请填写目标电脑网卡的 MAC 地址, 支持冒号/横线/无分隔符格式</p>")
            .append("<button type=\"submit\" style=\"margin-top:10px\">添加</button></form></div>");

        return page("控制台", body.toString(), store.adminUser());
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** 只显示默认网卡的地址: IPv4 推荐 + 全局 IPv6 + 本机测试用的链路本地地址 (缓存 10 秒) */
    private static String localIps() {
        long now = System.currentTimeMillis();
        if (cachedIps != null && now - cacheTime < 10_000) return cachedIps;
        cachedIps = computeIps();
        cacheTime = now;
        return cachedIps;
    }

    /** 默认网卡的可访问地址: [IPv4, 全局IPv6, 链路本地fe80] (均为主机名, 无端口/方括号) */
    public static String[] accessAddresses() {
        // 默认路由出口 IP (UDP connect 只在本地完成, 不会真的发包)
        String v4 = defaultSourceIp("8.8.8.8");
        String v6 = defaultGlobalIp6(v4);
        String fe80 = linkLocalOf(v4);
        return new String[]{v4, v6, fe80};
    }

    private static String computeIps() {
        String[] a = accessAddresses();
        String v4 = a[0];
        String v6 = a[1];
        String fe80 = a[2];

        StringBuilder sb = new StringBuilder();
        if (!v4.isEmpty()) {
            addAddr(sb, v4, " (推荐, 局域网设备访问)");
        } else {
            for (String ip : allV4()) addAddr(sb, ip, " (推荐)");
        }
        if (!v6.isEmpty()) {
            addAddr(sb, "[" + v6 + "]", " (IPv6)");
        }
        if (!fe80.isEmpty()) {
            addAddr(sb, "[" + fe80 + "]", " (本机测试)");
        }
        if (sb.length() == 0) return "本机";
        return sb.toString();
    }

    /** 让系统选择默认路由的源 IP (UDP connect 只在本地完成, 不发包) */
    private static String defaultSourceIp(String host) {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName(host), 9);
            InetAddress local = s.getLocalAddress();
            if (local != null && !local.isLoopbackAddress()) return stripZone(local.getHostAddress());
        } catch (Exception ignored) {
        }
        return "";
    }

    /** 主网卡的全局 IPv6: 优先返回稳定地址 (临时地址会定期变化, 不适合复制使用) */
    private static String defaultGlobalIp6(String v4) {
        String source = defaultSourceIp6("2001:4860:4860::8888");
        if (v4.isEmpty()) return source;
        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getByName(v4));
            if (ni != null) {
                List<String> globals = new ArrayList<>();
                for (var a : ni.getInterfaceAddresses()) {
                    InetAddress ip = a.getAddress();
                    if (ip instanceof Inet6Address && !ip.isLinkLocalAddress() && !ip.isLoopbackAddress()) {
                        globals.add(stripZone(ip.getHostAddress()));
                    }
                }
                for (String g : globals) {
                    if (!g.equals(source)) return g;
                }
                if (!globals.isEmpty()) return globals.get(0);
            }
        } catch (Exception ignored) {
        }
        return source;
    }

    private static String defaultSourceIp6(String host) {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName(host), 9);
            InetAddress local = s.getLocalAddress();
            if (local instanceof Inet6Address && !local.isLoopbackAddress() && !local.isLinkLocalAddress()) {
                return stripZone(local.getHostAddress());
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** 主 IPv4 所在网卡的链路本地 IPv6 (本机浏览器测试用) */
    private static String linkLocalOf(String v4) {
        if (v4.isEmpty()) return "";
        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getByName(v4));
            if (ni != null) {
                for (var a : ni.getInterfaceAddresses()) {
                    InetAddress ip = a.getAddress();
                    if (ip instanceof Inet6Address && ip.isLinkLocalAddress()) {
                        return stripZone(ip.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static List<String> allV4() {
        List<String> list = new ArrayList<>();
        try {
            var nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (var a : ni.getInterfaceAddresses()) {
                    InetAddress ip = a.getAddress();
                    if (ip.isLoopbackAddress()) continue;
                    String host = stripZone(ip.getHostAddress());
                    if (!host.contains(":") && !list.contains(host)) list.add(host);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static String stripZone(String host) {
        int pct = host.indexOf("%");
        return pct >= 0 ? host.substring(0, pct) : host;
    }

    private static void addAddr(StringBuilder sb, String ip, String tag) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append("<code>http://").append(ip).append(":").append(currentPort).append("</code>").append(tag);
    }
}



