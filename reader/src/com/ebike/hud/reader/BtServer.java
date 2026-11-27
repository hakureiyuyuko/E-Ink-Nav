package com.ebike.hud.reader;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.provider.Settings;

import java.io.IOException;
import java.util.UUID;

/**
 * 蓝牙接收端：经典蓝牙 RFCOMM（标准 SPP UUID），
 * 用 insecure 方式免配对直连，手机按 MAC 直接连上来。
 */
public class BtServer {

    /** 蓝牙适配器对外显示的名字 */
    public static final String ADAPTER_NAME = "E-Ink Nav";

    /** 标准 SPP UUID，电脑/其他串口工具也能连 */
    public static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /** 回调：数据帧、镜像帧、连接状态与 MAC */
    public interface Callback extends Frames.Sink {
        void onBtStatus(String status);

        void onMac(String mac);
    }

    private final Callback cb;
    private final Context ctx;
    private BluetoothServerSocket server;
    private volatile boolean running = true;

    public BtServer(Context ctx, Callback cb) {
        this.ctx = ctx;
        this.cb = cb;
    }

    public void start() {
        Thread t = new Thread(this::loop, "hud-bt");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
    }

    private void loop() {
        BluetoothAdapter a;
        try {
            a = BluetoothAdapter.getDefaultAdapter();
        } catch (Exception e) {
            cb.onBtStatus("蓝牙不可用");
            return;
        }
        if (a == null) {
            cb.onBtStatus("蓝牙不可用");
            return;
        }

        if (!a.isEnabled()) {
            try { a.enable(); } catch (Exception ignored) { }
            for (int i = 0; i < 40 && !a.isEnabled(); i++) sleep(200);
        }
        if (!a.isEnabled()) {
            cb.onBtStatus("蓝牙未开启");
            return;
        }

        try { a.setName(ADAPTER_NAME); } catch (Exception ignored) { }
        try {
            // 让设备可被手机扫描到（隐藏 API，失败也不影响按 MAC 直连）
            java.lang.reflect.Method m = BluetoothAdapter.class.getMethod("setScanMode", int.class);
            m.invoke(a, 23); // SCAN_MODE_CONNECTABLE_DISCOVERABLE
        } catch (Exception ignored) { }
        cb.onMac(realMac(a));

        BluetoothServerSocket ss = null;
        for (int attempt = 0; attempt < 3 && ss == null && running; attempt++) {
            try {
                ss = a.listenUsingInsecureRfcommWithServiceRecord("NavHUD", SPP_UUID);
            } catch (IOException e) {
                sleep(600);
            }
        }
        if (ss == null) {
            cb.onBtStatus("蓝牙监听失败");
            return;
        }
        server = ss;
        cb.onBtStatus("蓝牙待连");

        while (running) {
            try {
                final BluetoothSocket s = ss.accept();
                if (s == null) continue;
                cb.onBtStatus("蓝牙已连接");
                Thread r = new Thread(() -> readLoop(s), "hud-bt-conn");
                r.setDaemon(true);
                r.start();
            } catch (IOException e) {
                if (!running) break;
                sleep(300);
            }
        }
    }

    /** Android 6+ 把 getAddress() 伪装成 02:00:00:00:00:00且不允许应用读真 MAC，读不到就返回 null */
    private String realMac(BluetoothAdapter a) {
        try {
            String s = Settings.Secure.getString(ctx.getContentResolver(), "bluetooth_address");
            if (s != null) {
                s = s.trim().toUpperCase();
                if (s.length() == 17 && !s.startsWith("02:00:00")) return s;
            }
        } catch (Exception ignored) { }
        try {
            String s = a.getAddress();
            if (s != null && s.length() == 17 && !s.startsWith("02:00:00")) return s.toUpperCase();
        } catch (Exception ignored) { }
        return null;
    }

    private void readLoop(BluetoothSocket s) {
        try {
            Frames.loop(s.getInputStream(), cb);
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (Exception ignored) { }
            cb.onBtStatus("蓝牙待连");
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) { }
    }
}
