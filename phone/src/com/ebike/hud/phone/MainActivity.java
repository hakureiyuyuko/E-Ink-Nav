package com.ebike.hud.phone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_CAPTURE = 1001;

    private EditText ipET;
    private EditText portET;
    private EditText macET;
    private TextView statusTV;
    private TextView logTV;
    private Button modeBtn;
    private SharedPreferences sp;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String mode = Sender.MODE_BT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("hud", Context.MODE_PRIVATE);
        mode = sp.getString("mode", Sender.MODE_BT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("导航 HUD 发送端");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("把高德的导航画面投到车把墨水屏。只需系统投屏授权，不需要无障碍。");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#666666"));
        root.addView(hint);

        // ---- 截图模式 ----
        Button mirror = btn("★ 开启截图模式（推送高德画面）");
        mirror.setTextColor(Color.parseColor("#1A237E"));
        mirror.setOnClickListener(v -> {
            save();
            Sender.configure(mode, ipET.getText().toString(),
                    intOf(portET.getText().toString()), macET.getText().toString());
            startService(new Intent(this, HudService.class));
            Sender.start();
            try {
                android.media.projection.MediaProjectionManager mpm =
                        (android.media.projection.MediaProjectionManager)
                                getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
                toast("授权时请选「单个应用 → 高德地图」");
            } catch (Throwable t) {
                toast("无法请求投屏: " + t.getClass().getSimpleName());
            }
        });
        root.addView(mirror);

        Button mirrorStop = btn("停止截图模式");
        mirrorStop.setOnClickListener(v -> {
            Mirror.get().stop();
            DebugLog.push("已停止截图模式");
            toast("已停止");
        });
        root.addView(mirrorStop);

        // ---- 骑行数据（GPS）----
        final Button gpsBtn = btn("");
        updateGpsBtn(gpsBtn);
        gpsBtn.setOnClickListener(v -> {
            if (Gps.get().isRunning()) {
                Gps.get().stop();
                DebugLog.push("GPS 已停止");
            } else {
                if (!Gps.hasPermission(this)) {
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 44);
                } else {
                    save();
                    Sender.configure(mode, ipET.getText().toString(),
                            intOf(portET.getText().toString()), macET.getText().toString());
                    startService(new Intent(this, HudService.class));
                    Sender.start();
                    Mirror.get().stop();   // 骑行数据和截图两者只留一个
                    String r = Gps.get().start(this);
                    DebugLog.push(r);
                    toast(r);
                }
            }
            updateGpsBtn(gpsBtn);
        });
        root.addView(gpsBtn);

        final Button nativeBtn = btn("");
        Mirror.get().setNativeRes(sp.getBoolean("nativeRes", true));
        updateNativeBtn(nativeBtn);
        nativeBtn.setOnClickListener(v -> {
            boolean next = !Mirror.get().nativeRes();
            Mirror.get().setNativeRes(next);
            sp.edit().putBoolean("nativeRes", next).apply();
            updateNativeBtn(nativeBtn);
            if (Mirror.get().isRunning()) toast("需先停止再开启截图模式才生效");
        });
        root.addView(nativeBtn);

        final Button scaleBtn = btn("");
        Mirror.get().setScale(sp.getFloat("scale", 1.5f));
        updateScaleBtn(scaleBtn);
        scaleBtn.setOnClickListener(v -> {
            float next = Mirror.get().nextScale();
            Mirror.get().setScale(next);
            sp.edit().putFloat("scale", next).apply();
            updateScaleBtn(scaleBtn);
            if (Mirror.get().isRunning()) toast("需先停止再开启截图模式才生效");
        });
        root.addView(scaleBtn);

        final Button dpiBtn = btn("");
        Mirror.get().setNativeDpi(sp.getInt("nativeDpi", 200));
        updateDpiBtn(dpiBtn);
        dpiBtn.setOnClickListener(v -> {
            int next = Mirror.get().nextDpi();
            Mirror.get().setNativeDpi(next);
            sp.edit().putInt("nativeDpi", next).apply();
            updateDpiBtn(dpiBtn);
            if (Mirror.get().isRunning()) toast("需先停止再开启截图模式才生效");
        });
        root.addView(dpiBtn);

        final Button barBtn = btn("");
        Mirror.get().setCropBars(sp.getBoolean("cropBars", true));
        updateBarBtn(barBtn);
        barBtn.setOnClickListener(v -> {
            boolean next = !Mirror.get().cropBars();
            Mirror.get().setCropBars(next);
            sp.edit().putBoolean("cropBars", next).apply();
            updateBarBtn(barBtn);
        });
        root.addView(barBtn);

        final Button rotBtn = btn("");
        Mirror.get().setAutoRotate(sp.getBoolean("autoRotate", true));
        updateRotBtn(rotBtn);
        rotBtn.setOnClickListener(v -> {
            boolean next = !Mirror.get().autoRotate();
            Mirror.get().setAutoRotate(next);
            sp.edit().putBoolean("autoRotate", next).apply();
            updateRotBtn(rotBtn);
        });
        root.addView(rotBtn);

        final Button dirBtn = btn("");
        Mirror.get().setLandscapeDeg(sp.getInt("landscapeDeg", 90));
        updateDirBtn(dirBtn);
        dirBtn.setOnClickListener(v -> {
            int next = Mirror.get().nextLandscapeDeg();
            Mirror.get().setLandscapeDeg(next);
            sp.edit().putInt("landscapeDeg", next).apply();
            updateDirBtn(dirBtn);
            if (Mirror.get().isRunning()) toast("重启截图模式后生效");
        });
        root.addView(dirBtn);

        // ---- 连接 ----
        modeBtn = btn("");
        updateModeBtn();
        modeBtn.setOnClickListener(v -> {
            mode = Sender.MODE_BT.equals(mode) ? Sender.MODE_WIFI : Sender.MODE_BT;
            sp.edit().putString("mode", mode).apply();
            updateModeBtn();
        });
        root.addView(modeBtn);

        macET = new EditText(this);
        macET.setHint("电纸书蓝牙 MAC，例 AA:BB:CC:DD:EE:FF");
        macET.setInputType(InputType.TYPE_CLASS_TEXT);
        macET.setSingleLine();
        macET.setText(sp.getString("mac", ""));
        root.addView(macET);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        ipET = new EditText(this);
        ipET.setHint("墨水屏IP（Wi-Fi模式）");
        ipET.setInputType(InputType.TYPE_CLASS_TEXT);
        ipET.setSingleLine();
        ipET.setText(sp.getString("ip", ""));
        row.addView(ipET, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        portET = new EditText(this);
        portET.setHint("端口");
        portET.setInputType(InputType.TYPE_CLASS_NUMBER);
        portET.setSingleLine();
        portET.setText(String.valueOf(sp.getInt("port", Sender.DEFAULT_PORT)));
        row.addView(portET, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(row);

        Button discover = btn("自动发现（Wi-Fi）");
        discover.setOnClickListener(v -> doDiscover());
        root.addView(discover);

        Button connect = btn("连接电纸书");
        connect.setOnClickListener(v -> {
            save();
            Sender.configure(mode, ipET.getText().toString(),
                    intOf(portET.getText().toString()), macET.getText().toString());
            startService(new Intent(this, HudService.class));
            Sender.start();
            toast(Sender.MODE_BT.equals(mode) ? "蓝牙连接中…" : "TCP 连接中…");
        });
        root.addView(connect);

        Button test = btn("发送测试数据（验证墨水屏）");
        test.setOnClickListener(v -> {
            save();
            Sender.configure(mode, ipET.getText().toString(),
                    intOf(portET.getText().toString()), macET.getText().toString());
            String[] seq = {
                    "{\"arrow\":\"right\",\"dist\":\"300\",\"unit\":\"米\",\"road\":\"建国路\",\"remain\":\"12.3 公里\",\"eta\":\"15:20\"}",
                    "{\"arrow\":\"left\",\"dist\":\"80\",\"unit\":\"米\",\"road\":\"人民大道\",\"remain\":\"12.0 公里\",\"eta\":\"15:20\"}",
                    "{\"arrow\":\"straight\",\"dist\":\"1.2\",\"unit\":\"公里\",\"road\":\"环城东路\",\"remain\":\"10.8 公里\",\"eta\":\"15:22\"}"
            };
            for (int i = 0; i < seq.length; i++) {
                final String s = seq[i];
                ui.postDelayed(() -> Sender.push(s), i * 1800L);
            }
            toast("已发送 3 帧测试");
        });
        root.addView(test);

        statusTV = new TextView(this);
        statusTV.setTextSize(12);
        statusTV.setTextColor(Color.parseColor("#1B5E20"));
        statusTV.setPadding(0, dp(6), 0, dp(4));
        root.addView(statusTV);

        Button clear = btn("清空日志");
        clear.setOnClickListener(v -> {
            DebugLog.clear();
            refresh();
        });
        root.addView(clear);

        ScrollView sv = new ScrollView(this);
        logTV = new TextView(this);
        logTV.setTextSize(9);
        logTV.setTypeface(Typeface.MONOSPACE);
        logTV.setGravity(Gravity.START);
        sv.addView(logTV);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        ScrollView page = new ScrollView(this);
        page.addView(root);
        setContentView(page);

        Sender.configure(mode, ipET.getText().toString(),
                intOf(portET.getText().toString()), macET.getText().toString());
        startService(new Intent(this, HudService.class));

        ui.postDelayed(ticker, 700);
        ensureBtPermissions();
    }

    /** Android 12+ 需运行时授予 BLUETOOTH_CONNECT/SCAN */
    private static final String PERM_BT_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String PERM_BT_CONNECT = "android.permission.BLUETOOTH_CONNECT";

    private void ensureBtPermissions() {
        if (Build.VERSION.SDK_INT < 31) return;
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        if (checkSelfPermission(PERM_BT_CONNECT) != PackageManager.PERMISSION_GRANTED) need.add(PERM_BT_CONNECT);
        if (checkSelfPermission(PERM_BT_SCAN) != PackageManager.PERMISSION_GRANTED) need.add(PERM_BT_SCAN);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 43);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 43) {
            boolean ok = true;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            DebugLog.push("蓝牙权限: " + (ok ? "已授予" : "被拒绝"));
            if (ok) {
                Sender.configure(mode, ipET.getText().toString(),
                        intOf(portET.getText().toString()), macET.getText().toString());
                Sender.start();
            } else {
                toast("需要蓝牙权限才能连接");
            }
        }
        if (code == 44) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            DebugLog.push("定位权限: " + (ok ? "已授予" : "被拒绝"));
            if (ok) {
                save();
                Sender.configure(mode, ipET.getText().toString(),
                        intOf(portET.getText().toString()), macET.getText().toString());
                startService(new Intent(this, HudService.class));
                Sender.start();
                Mirror.get().stop();
                String r = Gps.get().start(this);
                DebugLog.push(r);
                toast(r);
            } else {
                toast("需要定位权限才能发送骑行数据");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, HudService.class);
                svc.putExtra(HudService.EXTRA_CODE, resultCode);
                svc.putExtra(HudService.EXTRA_DATA, data);
                startService(svc);
                toast("正在启动截图模式…");
            } else {
                toast("未授权截屏");
                DebugLog.push("投屏: 用户取消");
            }
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 700);
        }
    };

    private void refresh() {
        statusTV.setText("发送状态: " + Sender.status()
                + (Mirror.get().isRunning() ? "  ｜ 截图模式运行中" : "")
                + (Gps.get().isRunning() ? "  ｜ " + Gps.get().status() : ""));
        String log = DebugLog.dump();
        if (!log.contentEquals(logTV.getText())) logTV.setText(log);
    }

    private void updateNativeBtn(Button b) {
        b.setText(Mirror.get().nativeRes()
                ? "分辨率：原生（点切换 自适应缩放）"
                : "分辨率：自适应缩放（点切换 原生）");
        b.setAllCaps(false);
    }

    private void updateScaleBtn(Button b) {
        b.setText("虚拟屏倍数：" + Mirror.get().scale() + "×（点切换 1.0/1.5/2.0）");
        b.setAllCaps(false);
    }

    private void updateDpiBtn(Button b) {
        b.setText("原生DPI：" + Mirror.get().nativeDpi() + "（点切换，越大字越大）");
        b.setAllCaps(false);
    }

    private void updateBarBtn(Button b) {        b.setText(Mirror.get().cropBars()
                ? "白边：自动裁掉并铺满（点切换 保留白边）"
                : "白边：保留（点切换 自动裁掉铺满）");
        b.setAllCaps(false);
    }

    private void updateRotBtn(Button b) {
        b.setText(Mirror.get().autoRotate()
                ? "横屏：电纸书切横版显示（点切换 保持竖版）"
                : "横屏：保持竖版（点切换 切横版）");
        b.setAllCaps(false);
        b.setTextColor(Mirror.get().autoRotate()
                ? Color.parseColor("#1A237E") : Color.parseColor("#424242"));
    }

    private void updateDirBtn(Button b) {
        b.setText("横屏方向：" + Mirror.get().landscapeDeg() + "°（点切换，画面倒过来就换）");
        b.setAllCaps(false);
    }

    private void updateGpsBtn(Button b) {
        b.setText(Gps.get().isRunning()
                ? "停止发送骑行数据（速度/海拔/方向）"
                : "★ 发送骑行数据（GPS 速度/海拔/方向）");
        b.setAllCaps(false);
    }

    private void updateModeBtn() {
        boolean bt = Sender.MODE_BT.equals(mode);
        modeBtn.setText(bt ? "连接方式：蓝牙（点切换 Wi-Fi）" : "连接方式：Wi-Fi（点切换 蓝牙）");
        modeBtn.setAllCaps(false);
        modeBtn.setTextColor(bt ? Color.parseColor("#1A237E") : Color.parseColor("#1B5E20"));
    }

    private void doDiscover() {
        toast("正在搜索墨水屏…");
        new Thread(() -> {
            final String ip = Sender.discover(2500);
            ui.post(() -> {
                if (ip != null) {
                    ipET.setText(ip);
                    save();
                    toast("找到墨水屏: " + ip);
                } else {
                    toast("没找到，请确认同一 Wi-Fi");
                }
            });
        }).start();
    }

    private void save() {
        sp.edit()
                .putString("mode", mode)
                .putString("ip", ipET.getText().toString().trim())
                .putString("mac", macET.getText().toString().trim())
                .putInt("port", intOf(portET.getText().toString()))
                .apply();
    }

    private int intOf(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return Sender.DEFAULT_PORT; }
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
