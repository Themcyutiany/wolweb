package com.wolweb;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = 9999;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            }
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        Store store = new Store();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new Handler(store));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("============================================");
        System.out.println(" WOL Web 已启动, 监听端口: " + port);
        System.out.println(" 访问地址: http://<本机IP>:" + port);
        System.out.println(" 配置文件: " + store.configPath());
        if (store.isConfigured()) {
            System.out.println(" 管理员已设置, 打开网页登录即可使用");
        } else {
            System.out.println(" 首次运行: 打开网页会自动进入管理员设置");
        }
        System.out.println(" 提示: 若忘记密码, 删除配置文件后重新设置");
        System.out.println("============================================");
    }
}
