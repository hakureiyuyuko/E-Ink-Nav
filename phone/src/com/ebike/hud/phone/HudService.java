package com.ebike.hud.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** 前台服务：骑车时熄屏也不让连接被系统掐掉 */
public class HudService extends Service {

    public static final String EXTRA_CODE = "mirror_code";
    public static final String EXTRA_DATA = "mirror_data";

    private PowerManager.WakeLock lock;
    private Notification notification;
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        String ch = "hud";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(ch, "导航HUD", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, ch)
                .setContentTitle("导航HUD 发送端运行中")
                .setContentText("正在把高德导航信息推送到墨水屏")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        startForeground(1, n);
        notification = n;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hud:sender");
            lock.setReferenceCounted(false);
            lock.acquire();
        }
        Sender.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_CODE)) {
            int code = intent.getIntExtra(EXTRA_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            // Android 14+：必须先以 mediaProjection 类型进入前台，才能拿 MediaProjection
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    startForeground(1, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                } catch (Throwable t) {
                    DebugLog.push("前台服务类型设置失败: " + t.getClass().getSimpleName());
                }
            }
            String r = Mirror.get().start(getApplicationContext(), code, data);
            DebugLog.push("投屏: " + r);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (lock != null && lock.isHeld()) lock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
