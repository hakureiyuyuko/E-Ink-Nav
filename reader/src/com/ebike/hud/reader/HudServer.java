package com.ebike.hud.reader;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 电纸书端接收服务：
 *  - TCP 8848：手机推送的导航数据（每行一个 JSON）
 *  - UDP 8849：局域网自动发现（手机广播 HUDPROBE，本机回 HUDPONG）
 */
public class HudServer {

    public interface Callback extends Frames.Sink {
        void onStatus(String status);
    }

    public static final int TCP_PORT = 8848;
    public static final int DISCOVER_PORT = 8849;

    private final Callback cb;
    private ServerSocket tcp;
    private DatagramSocket udp;
    private volatile boolean running = true;

    public HudServer(Callback cb) {
        this.cb = cb;
    }

    public void start() {
        Thread t1 = new Thread(this::tcpLoop, "hud-tcp");
        t1.setDaemon(true);
        t1.start();
        Thread t2 = new Thread(this::udpLoop, "hud-udp");
        t2.setDaemon(true);
        t2.start();
    }

    public void stop() {
        running = false;
        try { if (tcp != null) tcp.close(); } catch (Exception ignored) { }
        try { if (udp != null) udp.close(); } catch (Exception ignored) { }
    }

    private void tcpLoop() {
        try {
            tcp = new ServerSocket(TCP_PORT);
            tcp.setReuseAddress(true);
        } catch (IOException e) {
            cb.onStatus("端口 " + TCP_PORT + " 被占用");
            return;
        }
        cb.onStatus("等待手机连接");
        while (running) {
            try {
                final Socket s = tcp.accept();
                s.setTcpNoDelay(true);
                cb.onStatus("已连接");
                Thread reader = new Thread(() -> {
                    try {
                        Frames.loop(s.getInputStream(), cb);
                    } catch (IOException ignored) {
                    } finally {
                        try { s.close(); } catch (Exception ignored) { }
                        cb.onStatus("连接已断开");
                    }
                }, "hud-conn");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                if (running) cb.onStatus("等待手机连接");
            }
        }
    }

    private void udpLoop() {
        try {
            udp = new DatagramSocket(DISCOVER_PORT);
            udp.setBroadcast(true);
        } catch (IOException e) {
            return;
        }
        byte[] buf = new byte[64];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                udp.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                if ("HUDPROBE".equals(msg)) {
                    byte[] out = "HUDPONG".getBytes("UTF-8");
                    udp.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                }
            } catch (IOException ignored) { }
        }
    }
}
