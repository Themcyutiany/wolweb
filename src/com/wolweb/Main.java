package com.wolweb;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
        Pages.setPort(port);

        if (hasArg(args, "--help") || hasArg(args, "-h")) {
            System.out.println("用法: java -jar wolweb.jar [选项]");
            System.out.println("  --port <n>   监听端口 (默认 9999, 也可用环境变量 PORT)");
            System.out.println("  --stop       停止已运行的同端口实例 (无需管理员权限)");
            System.out.println("  --help       显示本帮助");
            return;
        }

        if (hasArg(args, "--stop")) {
            stopServer(port);
            return;
        }

        Store store = new Store();
        HttpServer server;
        try {
            // 双栈绑定: IPv6 + IPv4 (系统不支持 IPv6 时自动回退 IPv4)
            server = HttpServer.create(bindAddress(port), 0);
        } catch (java.net.BindException e) {
            System.err.println("启动失败: 端口 " + port + " 已被占用!");
            System.err.println("可能已有 WOL Web 实例在运行, 请先停止它:");
            System.err.println("  在运行中的终端输入 stop 回车, 或另开终端执行: java -jar wolweb.jar --stop");
            System.err.println("或改用其他端口: java -jar wolweb.jar --port 8080");
            System.exit(1);
            return;
        }
        server.createContext("/", new Handler(store));
        server.setExecutor(Executors.newCachedThreadPool());

        Path pidFile = Path.of(System.getProperty("user.dir"), "wolweb-" + port + ".pid");
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(pidFile);
            } catch (IOException ignored) {
            }
        }));

        server.start();
        printBanner(store, port);
        consoleCommands(server);
    }

    private static void printBanner(Store store, int port) {
        String[] a = Pages.accessAddresses();
        String v4 = a[0];
        String v6 = a[1];
        String fe80 = a[2];
        System.out.println("============================================");
        System.out.println(" WOL Web 已启动, 端口 " + port + " (IPv4/IPv6)");
        if (!v4.isEmpty()) {
            System.out.println(" 局域网访问:  http://" + v4 + ":" + port + "   <- 局域网其他设备用这个");
        } else {
            System.out.println(" 本机访问:    http://127.0.0.1:" + port);
        }
        if (!v6.isEmpty()) {
            System.out.println(" IPv6 访问:   http://[" + v6 + "]:" + port);
        }
        if (!fe80.isEmpty()) {
            System.out.println(" 本机测试:    http://[" + fe80 + "]:" + port);
        }
        if (store.isConfigured()) {
            System.out.println(" 状态: 管理员已设置, 打开网页登录即可使用");
        } else {
            System.out.println(" 状态: 首次运行, 打开网页会自动进入管理员设置");
        }
        System.out.println(" 配置文件:    " + store.configPath());
        System.out.println(" 停止服务:    在下方输入 stop 回车 (或另开终端 java -jar wolweb.jar --stop)");
        System.out.println(" 提示: 忘记密码就删除配置文件后重启");
        System.out.println("============================================");
    }

    /** 终端交互: 输入 stop / exit / quit 即可停止服务 */
    private static void consoleCommands(HttpServer server) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String cmd = line.trim().toLowerCase();
                    if (cmd.equals("stop") || cmd.equals("exit") || cmd.equals("quit")) {
                        System.out.println("收到停止指令, 正在退出...");
                        server.stop(0);
                        System.exit(0);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }, "wolweb-console");
        t.setDaemon(true);
        t.start();
    }

    /** 双栈监听地址: IPv6 通配符, 系统不支持时回退 IPv4 */
    private static InetSocketAddress bindAddress(int port) {
        try {
            return new InetSocketAddress(InetAddress.getByName("::"), port);
        } catch (Exception e) {
            try {
                return new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port);
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    /** 停止同端口正在运行的实例 (通过 pid 文件, 无需管理员) */
    private static void stopServer(int port) {
        Path pidFile = Path.of(System.getProperty("user.dir"), "wolweb-" + port + ".pid");
        if (!Files.exists(pidFile)) {
            System.err.println("未找到运行中的实例: " + pidFile);
            System.err.println("如果服务在运行但找不到 pid 文件 (旧版本启动的), 请手动结束 javaw/java 进程");
            System.exit(1);
            return;
        }
        try {
            String pidStr = Files.readString(pidFile).trim();
            long pid = Long.parseLong(pidStr);
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isPresent() && handle.get().isAlive()) {
                handle.get().destroy();
                System.out.println("已发送停止信号给进程 " + pid);
            } else {
                System.out.println("进程 " + pid + " 已不存在, 清理残留 pid 文件");
            }
            Files.deleteIfExists(pidFile);
        } catch (Exception e) {
            System.err.println("停止失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean hasArg(String[] args, String name) {
        for (String a : args) {
            if (name.equals(a)) return true;
        }
        return false;
    }
}
