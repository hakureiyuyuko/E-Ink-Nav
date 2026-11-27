package com.ebike.hud.reader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONObject;

public class ReaderActivity extends Activity implements BtServer.Callback {

    private HudView view;
    private BtServer bt;
    private PowerManager.WakeLock lock;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String btState = "启动中…";
    private String btMac = "";
    private byte[] lastFrame;
    private int landscapeDeg = 90;   // 横向时使用的旋转角度（可由指令覆盖）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        view = new HudView(this);
        setContentView(view);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "einknav:reader");
            lock.setReferenceCounted(false);
            lock.acquire();
        }

        bt = new BtServer(this, this);
        bt.start();

        ui.postDelayed(ticker, 2000);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 2000);
        }
    };

    private void refresh() {
        boolean connected = "蓝牙已连接".equals(btState);
        view.status = connected ? "蓝牙已连接" : "等待手机连接";
        view.tip1 = "蓝牙名 " + BtServer.ADAPTER_NAME;
        view.tip2 = btMac.isEmpty()
                ? "在手机端填入本机蓝牙 MAC"
                : "MAC " + btMac;
        view.invalidate();
    }

    // ---- 旋转指令 / 文字数据 ----
    @Override
    public void onNav(final JSONObject o) {
        // 控制帧：{"cmd":"rotate","deg":0|90|270}
        if ("rotate".equals(o.optString("cmd", ""))) {
            final int deg = o.optInt("deg", 0);
            runOnUiThread(() -> {
                if (deg != 0) landscapeDeg = deg;
                view.canvasRotate = deg;
                view.invalidate();
            });
            return;
        }

        // 骑行数据：{"gps":1,"spd":18.3,"alt":120,"hdg":87,"acc":3}
        if (o.optInt("gps", 0) == 1) {
            final float spd = (float) o.optDouble("spd", 0);
            final float alt = (float) o.optDouble("alt", 0);
            final float hdg = (float) o.optDouble("hdg", 0);
            final float acc = (float) o.optDouble("acc", 0);
            final boolean wait = o.optInt("wait", 0) == 1;
            final int waitSec = o.optInt("ws", 0);
            runOnUiThread(() -> {
                view.gpsMode = true;
                view.image = null;
                view.canvasRotate = 0;
                view.gpsWait = wait;
                view.gpsWaitSec = waitSec;
                view.gpsSpeed = spd;
                view.gpsAlt = alt;
                view.gpsHdg = hdg;
                view.gpsAcc = acc;
                view.lastRx = System.currentTimeMillis();
                view.invalidate();
            });
            return;
        }

        // 文字导航帧
        runOnUiThread(() -> {
            view.image = null;   // 切回文字模式
            view.gpsMode = false;
            String a = o.optString("arrow", "");
            if (a.length() > 0) view.arrow = a;
            view.dist = o.optString("dist", view.dist);
            view.unit = o.optString("unit", view.unit);
            String r = o.optString("road", "");
            if (r.length() > 0) view.road = r;
            String rem = o.optString("remain", "");
            if (rem.length() > 0) view.remain = rem;
            String eta = o.optString("eta", "");
            if (eta.length() > 0) view.eta = eta;
            view.lastRx = System.currentTimeMillis();
            view.invalidate();
        });
    }

    // ---- 屏幕镜像 ----
    @Override
    public void onImage(final byte[] jpeg) {
        // 画面没变就不重绘：省电、也避免墨水屏无意义全刷闪屏
        byte[] prev = lastFrame;
        if (prev != null && prev.length == jpeg.length
                && java.util.Arrays.equals(prev, jpeg)) {
            runOnUiThread(() -> { if (view != null) view.lastRx = System.currentTimeMillis(); });
            return;
        }
        lastFrame = jpeg;
        final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        if (bmp == null) return;
        runOnUiThread(() -> {
            // 自愈兜底：根据图片本身宽高比判断横竖，指令丢了也能摆正
            boolean imgLandscape = bmp.getWidth() > bmp.getHeight();
            if (imgLandscape && view.canvasRotate == 0) view.canvasRotate = landscapeDeg;
            else if (!imgLandscape && view.canvasRotate != 0) view.canvasRotate = 0;

            android.graphics.Bitmap old = view.image;
            view.image = bmp;
            view.gpsMode = false;
            view.lastRx = System.currentTimeMillis();
            view.invalidate();
            if (old != null && old != bmp && !old.isRecycled()) old.recycle();
        });
    }

    // ---- 蓝牙状态 ----
    @Override
    public void onBtStatus(String status) {
        btState = status;
        runOnUiThread(this::refresh);
    }

    @Override
    public void onMac(String mac) {
        btMac = mac == null ? "" : mac;
        runOnUiThread(this::refresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bt != null) bt.stop();
        if (lock != null && lock.isHeld()) lock.release();
    }
}
