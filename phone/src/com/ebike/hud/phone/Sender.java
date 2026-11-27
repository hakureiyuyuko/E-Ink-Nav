package com.ebike.hud.phone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 发送端：蓝牙 RFCOMM（SPP）。
 * 只发最新一帧，断线自动重连；文字帧（如旋转指令）优先于图片帧写出。
 */
public class Sender {

    public static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static volatile String btMac = "";
    private static volatile String latest = null;
    private static volatile byte[] latestImage = null;
    private static volatile String status = "未启动";
    private static volatile boolean running = false;
    private static volatile Closeable link;
    private static Thread worker;

    public static void configure(String mac) {
        btMac = mac == null ? "" : mac.trim();
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(Sender::loop, "hud-sender");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stop() {
        running = false;
        status = "已停止";
        closeQuietly();
        if (worker != null) worker.interrupt();
        worker = null;
    }

    public static void push(String json) {
        latest = json;
        start();
    }

    /** 推一帧屏幕镜像（JPEG），帧格式：[0x01][len4][jpeg] */
    public static void pushImage(byte[] jpeg) {
        latestImage = jpeg;
        start();
    }

    public static String status() {
        return status;
    }

    private static void loop() {
        OutputStream os = null;
        long lastBeat = 0L;
        while (running) {
            try {
                if (os == null) {
                    os = connect();
                    status = "蓝牙已连接 " + btMac;
                    DebugLog.push("连接成功: " + status);
                    lastBeat = 0L;
                }
                String msg;
                byte[] img;
                synchronized (Sender.class) {
                    msg = latest;
                    latest = null;
                    img = latestImage;
                    latestImage = null;
                }
                long now = System.currentTimeMillis();
                // 文字帧（例如旋转指令）必须先发，不能被图片顶掉
                if (msg != null) {
                    os.write((msg + "\n").getBytes("UTF-8"));
                    os.flush();
                    lastBeat = now;
                }
                if (img != null) {
                    byte[] frame = new byte[img.length + 5];
                    frame[0] = 1;
                    frame[1] = (byte) ((img.length >>> 24) & 0xFF);
                    frame[2] = (byte) ((img.length >>> 16) & 0xFF);
                    frame[3] = (byte) ((img.length >>> 8) & 0xFF);
                    frame[4] = (byte) (img.length & 0xFF);
                    System.arraycopy(img, 0, frame, 5, img.length);
                    os.write(frame);
                    os.flush();
                    lastBeat = now;
                }
                if (img == null && msg == null && now - lastBeat > 5000) {
                    os.write("{\"ping\":1}\n".getBytes("UTF-8"));
                    os.flush();
                    lastBeat = now;
                }
                Thread.sleep(img != null ? 30 : 120);
            } catch (Exception e) {
                String m = e.getMessage();
                status = "连接失败: " + e.getClass().getSimpleName() + (m == null ? "" : (" " + m));
                closeQuietly();
                os = null;
                try { Thread.sleep(2000); } catch (Exception ignored) { }
            }
        }
        closeQuietly();
    }

    private static OutputStream connect() throws Exception {
        if (btMac.length() < 17) throw new Exception("未填写电纸书蓝牙MAC");
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) throw new Exception("本机无蓝牙");
        if (!a.isEnabled()) {
            a.enable();
            try { Thread.sleep(1200); } catch (Exception ignored) { }
        }
        if (!a.isEnabled()) throw new Exception("蓝牙未开启");
        try {
            if (a.isDiscovering()) a.cancelDiscovery();
        } catch (SecurityException ignored) {
            // 新系统要求 BLUETOOTH_SCAN 运行时权限，没有也继续尝试连接
        }
        BluetoothDevice d;
        try {
            d = a.getRemoteDevice(btMac.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Exception("MAC 格式不对");
        }
        BluetoothSocket bs = d.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
        link = bs;
        bs.connect();
        return bs.getOutputStream();
    }

    private static void closeQuietly() {
        try {
            if (link != null) link.close();
        } catch (Exception ignored) { }
        link = null;
    }
}
