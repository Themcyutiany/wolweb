package com.wolweb;

import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

/** 生成网页 HTML (UI 美化版) */
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
        :root{--blue:#2563eb;--blue2:#4f46e5;--green:#16a34a;--red:#dc2626;--ink:#1e293b}
        *{box-sizing:border-box;margin:0;padding:0}
        body{font-family:"Segoe UI","PingFang SC","Microsoft YaHei",system-ui,sans-serif;
             background:linear-gradient(135deg,#0f2027 0%,#203a43 50%,#2c5364 100%);min-height:100vh;
             color:var(--ink);line-height:1.65}
        .wrap{max-width:820px;margin:0 auto;padding:28px 16px 48px}
        /* 顶部导航 */
        header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;
               color:#fff;margin-bottom:22px}
        .logo{display:flex;align-items:center;gap:10px}
        .logo .ico{width:40px;height:40px;border-radius:12px;display:flex;align-items:center;justify-content:center;
                   font-size:22px;background:linear-gradient(135deg,#38bdf8,#6366f1);
                   box-shadow:0 6px 18px rgba(56,189,248,.35)}
        .logo h1{font-size:20px;font-weight:700;letter-spacing:.5px}
        .who{display:flex;align-items:center;gap:10px;font-size:13px;color:#cbd5e1;background:rgba(255,255,255,.08);
             padding:7px 14px;border-radius:999px}
        .who b{color:#fff}
        .who a{color:#7dd3fc;text-decoration:none;font-weight:600}
        .who a:hover{text-decoration:underline}
        /* 卡片 */
        .card{background:#fff;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.18);padding:22px;margin-bottom:20px}
        h2{font-size:16px;color:#0f2a43;margin-bottom:14px;display:flex;align-items:center;gap:8px}
        .card h2::before{content:"";width:4px;height:16px;border-radius:2px;
                         background:linear-gradient(180deg,#38bdf8,#6366f1)}
        /* 表格 */
        table{width:100%;border-collapse:collapse;font-size:14px}
        th,td{padding:11px 10px;text-align:left;border-bottom:1px solid #eef2f7}
        th{color:#64748b;font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.4px;background:#f8fafc}
        tr:last-child td{border-bottom:0}
        tbody tr:hover{background:#f1f5f9}
        td code{background:#eef2ff;color:#4338ca;padding:3px 8px;border-radius:6px;font-size:12.5px;
                font-family:Consolas,Menlo,monospace;word-break:break-all}
        .empty{color:#94a3b8;text-align:center;padding:26px 0;font-size:14px}
        /* 表单 */
        input{width:100%;padding:11px 14px;border:1.5px solid #dbe3ee;border-radius:12px;font-size:14px;
              color:var(--ink);background:#fbfcfe;outline:none;transition:border-color .2s,box-shadow .2s}
        input::placeholder{color:#b0bccb}
        input:focus{border-color:var(--blue);background:#fff;box-shadow:0 0 0 4px rgba(37,99,235,.15)}
        label{display:block;font-size:13px;color:#5b6b7f;margin:4px 0 6px;font-weight:600}
        .row{display:flex;gap:12px}
        .row input{margin-bottom:0}
        .hint{font-size:12.5px;color:#94a3b8;margin-top:10px}
        /* 按钮 */
        .btn{display:inline-flex;align-items:center;justify-content:center;gap:6px;border:0;border-radius:12px;
             padding:10px 18px;font-size:14px;font-weight:600;color:#fff;cursor:pointer;text-decoration:none;
             background:linear-gradient(135deg,var(--blue),var(--blue2));
             box-shadow:0 4px 14px rgba(37,99,235,.35);transition:transform .15s,box-shadow .15s,filter .15s}
        .btn:hover{transform:translateY(-2px);box-shadow:0 8px 22px rgba(37,99,235,.45);filter:brightness(1.05)}
        .btn:active{transform:translateY(0);box-shadow:0 3px 8px rgba(37,99,235,.35)}
        .btn.wake{background:linear-gradient(135deg,#22c55e,#16a34a);box-shadow:0 4px 14px rgba(34,197,94,.35)}
        .btn.wake:hover{box-shadow:0 8px 22px rgba(34,197,94,.45)}
        .btn.danger{background:linear-gradient(135deg,#ef4444,#dc2626);box-shadow:0 4px 14px rgba(239,68,68,.35)}
        .btn.danger:hover{box-shadow:0 8px 22px rgba(239,68,68,.45)}
        .btn.mini{padding:7px 12px;font-size:13px;border-radius:10px}
        .btn.block{width:100%}
        form.inline{display:inline}
        /* 提示 */
        .msg{padding:12px 16px;border-radius:12px;margin-bottom:16px;font-size:14px;display:flex;align-items:center;gap:8px}
        .msg.ok{background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0}
        .msg.err{background:#fef2f2;color:#991b1b;border:1px solid #fecaca}
        /* 品牌页 */
        .brand{text-align:center;margin:8px 0 26px;color:#fff}
        .brand .ico{width:74px;height:74px;margin:0 auto 16px;border-radius:22px;display:flex;align-items:center;
                    justify-content:center;font-size:38px;background:linear-gradient(135deg,#38bdf8,#6366f1);
                    box-shadow:0 12px 30px rgba(56,189,248,.4)}
        .brand h1{font-size:26px;font-weight:700;margin-bottom:6px}
        .brand p{color:#cbd5e1;font-size:14px}
        .login{max-width:380px;margin:0 auto}
        .login .card{padding:30px}
        .login button{margin-top:6px}
        .login input{margin-bottom:12px}
        /* 页脚地址 */
        footer{font-size:12.5px;color:#cbd5e1;text-align:center;margin-top:30px;line-height:2}
        footer code{background:rgba(255,255,255,.1);color:#e2e8f0;padding:3px 9px;border-radius:8px;
                    font-family:Consolas,Menlo,monospace;font-size:12px}
        /* 小屏适配 */
        @media(max-width:560px){
          .wrap{padding:18px 12px 36px}
          .row{flex-direction:column}
          .row input{margin-bottom:10px}
          .who{max-width:100%}
          table{font-size:13px}
          th,td{padding:9px 8px}
          .brand .ico{width:60px;height:60px;font-size:30px}
        }
        """;

    public static String page(String title, String body, String adminName) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + esc(title) + " - WOL Web</title><style>" + CSS + "</style></head><body><div class=\"wrap\">"
            + "<header><div class=\"logo\"><span class=\"ico\">\uD83D\uDDA5\uFE0F</span><h1>局域网唤醒 WOL</h1></div>"
            + (adminName == null || adminName.isEmpty() ? ""
                : "<span class=\"who\"><span>\uD83D\uDC64 管理员 <b>" + esc(adminName) + "</b></span><a href=\"/logout\">退出登录</a></span>")
            + "</header><main>" + body + "</main>"
            + "<footer>本机访问地址: " + localIps() + "</footer>"
            + "</div></body></html>";
    }

    public static String brandPage(String title, String inner) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + esc(title) + " - WOL Web</title><style>" + CSS + "</style></head><body><div class=\"wrap\">"
            + "<div class=\"brand\"><div class=\"ico\">\uD83D\uDDA5\uFE0F</div><h1>局域网唤醒 WOL</h1><p>通过网页远程唤醒局域网电脑</p></div>"
            + inner
            + "<footer>本机访问地址: " + localIps() + "</footer>"
            + "</div></body></html>";
    }

    public static String msg(String text, boolean ok) {
        String icon = ok ? "\u2705" : "\u26A0\uFE0F";
        return "<div class=\"msg " + (ok ? "ok" : "err") + "\">" + icon + "<span>" + esc(text) + "</span></div>";
    }

    public static String setupForm(String error) {
        String body = "<div class=\"login\"><div class=\"card\"><h2>首次使用 · 设置管理员</h2>"
            + (error == null ? "" : msg(error, false))
            + "<form method=\"post\" action=\"/setup\">"
            + "<label>管理员用户名</label><input name=\"user\" value=\"admin\" required autocomplete=\"username\">"
            + "<label>密码 (至少 6 位)</label><input type=\"password\" name=\"password\" required autocomplete=\"new-password\">"
            + "<label>确认密码</label><input type=\"password\" name=\"confirm\" required autocomplete=\"new-password\">"
            + "<button class=\"btn block\" type=\"submit\">\uD83D\uDD11 创建账号并进入</button></form></div></div>";
        return brandPage("设置管理员", body);
    }

    public static String loginForm(String error) {
        String body = "<div class=\"login\"><div class=\"card\"><h2>管理员登录</h2>"
            + (error == null ? "" : msg(error, false))
            + "<form method=\"post\" action=\"/login\">"
            + "<label>用户名</label><input name=\"user\" required autocomplete=\"username\">"
            + "<label>密码</label><input type=\"password\" name=\"password\" required autocomplete=\"current-password\">"
            + "<button class=\"btn block\" type=\"submit\">\uD83D\uDD12 登录</button></form></div></div>";
        return brandPage("登录", body);
    }

    public static String mainPage(Store store, String message, boolean msgOk) {
        StringBuilder body = new StringBuilder();
        if (message != null && !message.isEmpty()) body.append(msg(message, msgOk));

        body.append("<div class=\"card\"><h2>电脑列表</h2><table><thead><tr><th>名称</th><th>MAC 地址</th><th style=\"width:170px\">操作</th></tr></thead><tbody>");
        List<Store.MacEntry> list = store.listMacs();
        if (list.isEmpty()) {
            body.append("<tr><td colspan=\"3\" class=\"empty\">还没有配置电脑, 请在下方添加</td></tr>");
        }
        for (Store.MacEntry m : list) {
            body.append("<tr><td>\uD83D\uDCBB ").append(esc(m.name())).append("</td>")
                .append("<td><code>").append(esc(m.addr())).append("</code></td><td>")
                .append("<form class=\"inline\" method=\"post\" action=\"/wake?id=").append(esc(m.id())).append("\">")
                .append("<button class=\"btn mini wake\" type=\"submit\">\u26A1 唤醒</button></form> ")
                .append("<form class=\"inline\" method=\"post\" action=\"/delete?id=").append(esc(m.id())).append("\" ")
                .append("onsubmit=\"return confirm('确定删除该电脑?')\">")
                .append("<button class=\"btn mini danger\" type=\"submit\">\uD83D\uDDD1\uFE0F 删除</button></form>")
                .append("</td></tr>");
        }
        body.append("</tbody></table></div>");

        body.append("<div class=\"card\"><h2>添加电脑</h2><form method=\"post\" action=\"/add\">")
            .append("<div class=\"row\"><div style=\"flex:1\"><input name=\"name\" placeholder=\"名称, 如: 客厅电脑\" required></div>")
            .append("<div style=\"flex:1.6\"><input name=\"addr\" placeholder=\"MAC 地址, 如: AA:BB:CC:DD:EE:FF\" required></div></div>")
            .append("<p class=\"hint\">请填写目标电脑网卡的 MAC 地址, 支持冒号 / 横线 / 无分隔符格式</p>")
            .append("<button class=\"btn\" type=\"submit\" style=\"margin-top:14px\">\u2795 添加</button></form></div>");

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