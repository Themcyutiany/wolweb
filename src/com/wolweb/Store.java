package com.wolweb;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 配置存储: 管理员账号 + MAC 列表, 保存在 jar 同目录的 wolweb.properties */
public class Store {
    public record MacEntry(String id, String name, String addr) {}

    private static final int ITERATIONS = 100_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file = Path.of(System.getProperty("user.dir"), "wolweb.properties");
    private final Properties props = new Properties();
    private final Map<String, MacEntry> macs = new LinkedHashMap<>();
    private String adminUser = "";
    private String adminSalt = "";
    private String adminHash = "";

    public Store() {
        load();
    }

    public synchronized String configPath() {
        return file.toAbsolutePath().toString();
    }

    public synchronized boolean isConfigured() {
        return !adminHash.isEmpty();
    }

    public synchronized String adminUser() {
        return adminUser;
    }

    public synchronized boolean verifyLogin(String user, String password) {
        if (!isConfigured() || user == null || password == null) return false;
        if (!user.equals(adminUser)) return false;
        byte[] salt = HexFormat.of().parseHex(adminSalt);
        String hash = hash(password, salt);
        return MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                adminHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 首次运行设置管理员; 成功后返回 true */
    public synchronized boolean setupAdmin(String user, String password) {
        if (isConfigured()) return false;
        String u = user == null ? "" : user.trim();
        if (u.isEmpty() || password == null || password.length() < 6) return false;
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        adminUser = u;
        adminSalt = HexFormat.of().formatHex(salt);
        adminHash = hash(password, salt);
        save();
        return true;
    }

    private static String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return HexFormat.of().formatHex(factory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    public synchronized List<MacEntry> listMacs() {
        return new ArrayList<>(macs.values());
    }

    public synchronized MacEntry getMac(String id) {
        return macs.get(id);
    }

    public synchronized MacEntry addMac(String name, String addr) {
        String n = name == null ? "" : name.trim();
        String a = addr == null ? "" : addr.trim();
        if (n.isEmpty() || a.isEmpty()) return null;
        int next = 1;
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("mac.") && key.endsWith(".addr")) {
                String id = key.substring(4, key.length() - 5);
                try {
                    int v = Integer.parseInt(id);
                    if (v >= next) next = v + 1;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String id = String.valueOf(next);
        props.setProperty("mac." + id + ".name", n);
        props.setProperty("mac." + id + ".addr", a);
        MacEntry entry = new MacEntry(id, n, a);
        macs.put(id, entry);
        save();
        return entry;
    }

    public synchronized boolean removeMac(String id) {
        MacEntry removed = macs.remove(id);
        if (removed == null) return false;
        props.remove("mac." + id + ".name");
        props.remove("mac." + id + ".addr");
        save();
        return true;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("读取配置文件失败: " + e.getMessage());
            return;
        }
        adminUser = props.getProperty("admin.user", "");
        adminSalt = props.getProperty("admin.salt", "");
        adminHash = props.getProperty("admin.hash", "");
        macs.clear();
        List<String> ids = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("mac.") && key.endsWith(".addr")) {
                ids.add(key.substring(4, key.length() - 5));
            }
        }
        ids.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        for (String id : ids) {
            macs.put(id, new MacEntry(id,
                    props.getProperty("mac." + id + ".name", ""),
                    props.getProperty("mac." + id + ".addr", "")));
        }
    }

    private synchronized void save() {
        props.setProperty("admin.user", adminUser);
        props.setProperty("admin.salt", adminSalt);
        props.setProperty("admin.hash", adminHash);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "WOL Web config (admin.salt/hash 请勿手动修改)");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }
}

