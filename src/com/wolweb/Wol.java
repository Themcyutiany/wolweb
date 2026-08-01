package com.wolweb;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

/** Wake-on-LAN: 发送魔术包到本机所有网段的广播地址 (UDP 9 端口) */
public class Wol {

    /** 解析 MAC, 支持 AA:BB:CC:DD:EE:FF / AA-BB-CC-DD-EE-FF / AABBCCDDEEFF */
    public static byte[] parseMac(String mac) throws IllegalArgumentException {
        String cleaned = mac.replaceAll("[^0-9a-fA-F]", "");
        if (cleaned.length() != 12) {
            throw new IllegalArgumentException("MAC 地址格式不正确: " + mac);
        }
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /** 发送魔术包, 返回实际发送到的广播地址列表 */
    public List<String> wake(String mac) throws IOException, IllegalArgumentException {
        byte[] target = parseMac(mac);
        byte[] packet = new byte[102];
        for (int i = 0; i < 6; i++) packet[i] = (byte) 0xFF;
        for (int i = 0; i < 16; i++) {
            System.arraycopy(target, 0, packet, 6 + i * 6, 6);
        }
        List<String> sent = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (InetAddress addr : broadcastAddresses()) {
                DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, 9);
                socket.send(dp);
                sent.add(addr.getHostAddress());
            }
        }
        return sent;
    }

    /** 收集 255.255.255.255 + 本机各网段的广播地址 */
    private static List<InetAddress> broadcastAddresses() {
        List<InetAddress> list = new ArrayList<>();
        list.add(ip("255.255.255.255"));
        try {
            var nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (var a : ni.getInterfaceAddresses()) {
                    InetAddress addr = a.getAddress();
                    if (addr instanceof Inet4Address && a.getNetworkPrefixLength() <= 30) {
                        byte[] raw = addr.getAddress();
                        int prefix = a.getNetworkPrefixLength();
                        for (int i = 0; i < 4; i++) {
                            int bits = Math.min(8, Math.max(0, prefix - i * 8));
                            int mask = bits == 0 ? 0 : (0xFF << (8 - bits)) & 0xFF;
                            raw[i] |= (byte) (~mask & 0xFF);
                        }
                        list.add(InetAddress.getByAddress(raw));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        List<InetAddress> unique = new ArrayList<>();
        for (InetAddress a : list) {
            if (!unique.contains(a)) unique.add(a);
        }
        return unique;
    }

    private static InetAddress ip(String s) {
        try {
            return InetAddress.getByName(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
