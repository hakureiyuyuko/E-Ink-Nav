package com.ebike.hud.phone;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * 骑行数据：GPS 速度 / 海拔 / 方向（航向）。
 * 搜星阶段也会定时上报 wait 帧，避免电纸书看起来“没反应”。
 */
public class Gps implements LocationListener {

    private static final Gps INST = new Gps();

    public static Gps get() {
        return INST;
    }

    private LocationManager lm;
    private volatile boolean running;
    private volatile long startAt;
    private volatile long lastFixAt;
    private volatile long lastPushAt;
    private volatile Location lastLoc;
    private String provider = LocationManager.GPS_PROVIDER;
    private final Handler h = new Handler(Looper.getMainLooper());

    public boolean isRunning() {
        return running;
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public String status() {
        if (!running) return "GPS 未启动";
        long now = System.currentTimeMillis();
        if (lastLoc != null && now - lastFixAt < 8000) {
            return "GPS 已定位（" + (now - lastFixAt) / 1000 + "s 前）";
        }
        return "GPS 搜索中 " + (now - startAt) / 1000 + "s";
    }

    public String start(Context ctx) {
        stop();
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return "无定位服务";
        if (!Gps.hasPermission(ctx)) return "缺少定位权限";

        provider = LocationManager.GPS_PROVIDER;
        boolean ok = false;
        try { ok = lm.isProviderEnabled(provider); } catch (Throwable ignored) { }
        if (!ok) {
            provider = LocationManager.NETWORK_PROVIDER;
            try { ok = lm.isProviderEnabled(provider); } catch (Throwable ignored) { }
        }
        if (!ok) return "定位服务未开启（请打开手机 GPS）";

        try {
            lm.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper());
        } catch (SecurityException e) {
            return "定位权限被拒绝";
        } catch (Throwable t) {
            return "启动定位失败: " + t.getClass().getSimpleName();
        }

        running = true;
        startAt = System.currentTimeMillis();
        lastFixAt = 0L;
        lastLoc = null;

        // 让电纸书立刻切到竖屏仪表盘，并显示“搜索中”
        Sender.push("{\"cmd\":\"rotate\",\"deg\":0}");
        Sender.push(waitFrame(0));
        h.postDelayed(tick, 1200);

        DebugLog.push("GPS 已启动，provider=" + provider);
        return "GPS 已启动（" + provider + "）";
    }

    public void stop() {
        running = false;
        h.removeCallbacksAndMessages(null);
        if (lm != null) {
            try { lm.removeUpdates(this); } catch (Throwable ignored) { }
        }
        lm = null;
        lastLoc = null;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            if (lastLoc != null && now - lastFixAt < 8000) {
                Sender.push(dataFrame(lastLoc));
            } else {
                Sender.push(waitFrame((now - startAt) / 1000));
            }
            h.postDelayed(this, 1200);
        }
    };

    private String waitFrame(long sec) {
        return "{\"gps\":1,\"wait\":1,\"ws\":" + sec + "}";
    }

    private String dataFrame(Location loc) {
        float spd = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0f;
        float alt = loc.hasAltitude() ? (float) loc.getAltitude() : 0f;
        float hdg = loc.hasBearing() ? loc.getBearing() : 0f;
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 0f;
        return String.format(java.util.Locale.US,
                "{\"gps\":1,\"spd\":%.1f,\"alt\":%.0f,\"hdg\":%.0f,\"acc\":%.0f}",
                spd, alt, hdg, acc);
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (loc == null || !running) return;
        if (Mirror.get().isRunning()) return;   // 截图模式下不发骑行数据
        lastLoc = loc;
        lastFixAt = System.currentTimeMillis();
        lastPushAt = lastFixAt;
        Sender.push(dataFrame(loc));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
        DebugLog.push("定位服务已开启: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        DebugLog.push("定位服务被关闭: " + provider);
    }
}
