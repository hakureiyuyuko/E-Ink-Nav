package com.ebike.hud.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CAPTURE = 1001;

    private TextView devTV;
    private TextView statusTV;
    private TextView logTV;
    private SharedPreferences sp;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // 扫描结果
    private final LinkedHashMap<String, String> found = new LinkedHashMap<>(); // mac -> name
    private final List<String> rows = new ArrayList<>();
    private final List<String> rowMacs = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;
    private AlertDialog scanDialog;
    private boolean scanning;

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null) addFound(d);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                scanning = false;
                if (rows.isEmpty()) {
                    toast("没找到设备，请确认墨水屏端已打开 App");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("hud", Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("E-Ink Nav 发送端");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("把导航画面或骑行数据推到车把墨水屏。连接方式固定为蓝牙。");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#666666"));
        root.addView(hint);

        // ---- 设备选择 ----
        devTV = new TextView(this);
        devTV.setTextSize(14);
        devTV.setPadding(0, dp(10), 0, dp(2));
        updateDeviceLabel();
        root.addView(devTV);

        Button pick = btn("搜索并选择电纸书");
        pick.setTextColor(Color.parseColor("#1A237E"));
        pick.setOnClickListener(v -> startScan());
        root.addView(pick);

        Button connect = btn("连接 / 重新连接");
        connect.setOnClickListener(v -> {
            Sender.start();
            toast("蓝牙连接中…");
        });
        root.addView(connect);

        // ---- 截图模式 ----
        Button mirror = btn("★ 开启截图模式（推送导航画面）");
        mirror.setTextColor(Color.parseColor("#1A237E"));
        mirror.setOnClickListener(v -> {
            startService(new Intent(this, HudService.class));
            Sender.start();
            try {
                android.media.projection.MediaProjectionManager mpm =
                        (android.media.projection.MediaProjectionManager)
                                getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
                toast("授权时请选「单个应用」→ 目标应用");
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
            } else if (!Gps.hasPermission(this)) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 44);
            } else {
                startGps();
            }
            updateGpsBtn(gpsBtn);
        });
        root.addView(gpsBtn);

        // ---- 截图参数 ----
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

        // ---- 调试 ----
        Button test = btn("发送测试数据（验证墨水屏）");
        test.setOnClickListener(v -> {
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
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        ScrollView page = new ScrollView(this);
        page.addView(root);
        setContentView(page);

        Sender.configure(sp.getString("mac", ""));
        startService(new Intent(this, HudService.class));
        if (!sp.getString("mac", "").isEmpty()) Sender.start();

        ui.postDelayed(ticker, 700);
        ensureBtPermissions();
        // 首次使用（尚未选择设备）自动扫描一次
        if (sp.getString("mac", "").isEmpty()) {
            ui.postDelayed(this::startScan, 1500);
        }
    }

    // ---------- 设备选择 ----------
    private void updateDeviceLabel() {
        String mac = sp.getString("mac", "");
        String name = sp.getString("name", "");
        if (mac.isEmpty()) {
            devTV.setText("尚未选择电纸书");
            devTV.setTextColor(Color.parseColor("#B00020"));
        } else {
            devTV.setText("电纸书：" + (name.isEmpty() ? "已选择" : name) + "  (" + mac + ")");
            devTV.setTextColor(Color.parseColor("#1B5E20"));
        }
    }

    private void startScan() {
        if (!hasBtPermissions()) {
            ensureBtPermissions();
            toast("请先允许蓝牙权限");
            return;
        }
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) { toast("本机无蓝牙"); return; }
        if (!a.isEnabled()) {
            try { a.enable(); } catch (Throwable ignored) { }
            toast("正在打开蓝牙，请稍后重试");
            return;
        }

        found.clear();
        rows.clear();
        rowMacs.clear();

        // 已配对设备先列出来（本方案免配对，通常为空）
        try {
            for (BluetoothDevice d : a.getBondedDevices()) {
                if (d != null) addFound(d);
            }
        } catch (Throwable ignored) { }

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        scanDialog = new AlertDialog.Builder(this)
                .setTitle("选择电纸书")
                .setAdapter(listAdapter, (dialog, which) -> {
                    if (which >= 0 && which < rowMacs.size()) {
                        String mac = rowMacs.get(which);
                        String name = found.get(mac);
                        selectDevice(mac, name);
                    }
                })
                .setNeutralButton("手动输入", (d, w) -> showManualInput())
                .setNegativeButton("停止搜索", (d, w) -> stopScan())
                .create();
        scanDialog.setOnDismissListener(d -> stopScan());
        scanDialog.show();

        try {
            a.cancelDiscovery();
            a.startDiscovery();
            scanning = true;
            DebugLog.push("开始扫描蓝牙设备");
        } catch (SecurityException e) {
            DebugLog.push("扫描被拒绝: 缺少 BLUETOOTH_SCAN");
            toast("缺少蓝牙扫描权限");
        } catch (Throwable t) {
            DebugLog.push("扫描失败: " + t.getClass().getSimpleName());
        }
    }

    private void addFound(BluetoothDevice d) {
        String mac;
        try { mac = d.getAddress(); } catch (SecurityException e) { return; }
        if (mac == null || found.containsKey(mac)) return;
        String name;
        try { name = d.getName(); } catch (SecurityException e) { name = null; }
        if (name == null || name.trim().isEmpty()) name = "未知设备";
        found.put(mac, name);
        rowMacs.add(mac);
        rows.add(name + "\n" + mac);
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && a.isDiscovering()) a.cancelDiscovery();
        } catch (Throwable ignored) { }
    }

    private void selectDevice(String mac, String name) {
        sp.edit().putString("mac", mac).putString("name", name).apply();
        Sender.configure(mac);
        updateDeviceLabel();
        stopScan();
        if (scanDialog != null) scanDialog.dismiss();
        Sender.start();
        toast("已选择：" + name);
    }

    private void showManualInput() {
        EditText et = new EditText(this);
        et.setHint("AA:BB:CC:DD:EE:FF");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(sp.getString("mac", ""));
        new AlertDialog.Builder(this)
                .setTitle("手动输入 MAC")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String mac = et.getText().toString().trim().toUpperCase();
                    if (mac.length() < 17) { toast("MAC 格式不对"); return; }
                    selectDevice(mac, "手动添加");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 权限 ----------
    private static final String PERM_BT_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String PERM_BT_CONNECT = "android.permission.BLUETOOTH_CONNECT";

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT < 31) return true;
        return checkSelfPermission(PERM_BT_CONNECT) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(PERM_BT_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureBtPermissions() {
        if (Build.VERSION.SDK_INT < 31) return;
        List<String> need = new ArrayList<>();
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
                if (!sp.getString("mac", "").isEmpty()) Sender.start();
            } else {
                toast("需要蓝牙权限才能连接");
            }
        }
        if (code == 44) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            DebugLog.push("定位权限: " + (ok ? "已授予" : "被拒绝"));
            if (ok) startGps();
            else toast("需要定位权限才能发送骑行数据");
        }
    }

    private void startGps() {
        startService(new Intent(this, HudService.class));
        Sender.start();
        Mirror.get().stop();   // 骑行数据和截图两者只留一个
        String r = Gps.get().start(this);
        DebugLog.push(r);
        toast(r);
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

    // ---------- 状态刷新 ----------
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

    private void updateGpsBtn(Button b) {
        b.setText(Gps.get().isRunning()
                ? "停止发送骑行数据（速度/海拔/方向）"
                : "★ 发送骑行数据（GPS 速度/海拔/方向）");
        b.setAllCaps(false);
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

    private void updateBarBtn(Button b) {
        b.setText(Mirror.get().cropBars()
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
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try { registerReceiver(btReceiver, f); } catch (Throwable ignored) { }
        updateDeviceLabel();
    }

    @Override
    protected void onPause() {
        try { unregisterReceiver(btReceiver); } catch (Throwable ignored) { }
        stopScan();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
