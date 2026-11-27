package com.ebike.hud.phone;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * 截图模式：MediaProjection 抓屏 → 缩放到 480x800 → 灰度 JPEG → 蓝牙发送。
 * 电纸书端收到 JPEG 直接铺屏显示，不做任何解析。
 */
public class Mirror {

    private static final Mirror INST = new Mirror();

    public static Mirror get() {
        return INST;
    }

    private MediaProjection projection;
    private VirtualDisplay vd;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running;
    private volatile long lastAt;
    private volatile int intervalMs = 1500;
    private volatile float cropTop = 0f;   // 0=全屏适应，>0=只取顶部该比例并铺满
    private volatile boolean nativeRes = false;  // true=让应用按 480x800 原生布局渲染
    private volatile int nativeDpi = 200;
    private volatile boolean cropBars = true;   // 自动裁掉应用留下的白/黑边并铺满
    private volatile boolean autoRotate = true;  // 横屏内容让电纸书切横版显示
    private volatile int landscapeDeg = 90;      // 电纸书画布旋转方向：90 或 270
    private volatile int lastSentRot = -1;
    private volatile int frameCount = 0;
    private volatile float scale = 1.5f;        // 虚拟屏超采样倍数：1.0 / 1.5 / 2.0

    public boolean isRunning() {
        return running;
    }

    public void setInterval(int ms) {
        intervalMs = Math.max(400, ms);
    }

    public void setCropTop(float f) {
        cropTop = f;
    }

    /**
     * 原生分辨率模式：虚拟显示器直接开成 480x800，
     * 配合系统「单个应用」投屏，应用会重布局到这个尺寸原生渲染，无需缩图。
     */
    public void setNativeRes(boolean b) {
        nativeRes = b;
    }

    public boolean nativeRes() {
        return nativeRes;
    }

    public void setNativeDpi(int dpi) {
        nativeDpi = dpi;
    }

    public void setCropBars(boolean b) {
        cropBars = b;
    }

    public void setAutoRotate(boolean b) {
        autoRotate = b;
    }

    public void setLandscapeDeg(int deg) {
        landscapeDeg = deg;
    }

    public int landscapeDeg() {
        return landscapeDeg;
    }

    /** 90 ↔ 270 */
    public int nextLandscapeDeg() {
        return landscapeDeg == 90 ? 270 : 90;
    }

    public boolean autoRotate() {
        return autoRotate;
    }

    public void setScale(float s) {
        scale = s;
    }

    public float scale() {
        return scale;
    }

    /** 1.0 → 1.5 → 2.0 循环 */
    public float nextScale() {
        if (scale < 1.4f) return 1.5f;
        if (scale < 1.9f) return 2.0f;
        return 1.0f;
    }

    public boolean cropBars() {
        return cropBars;
    }

    public int nativeDpi() {
        return nativeDpi;
    }

    /** 160 → 200 → 240 → 280 循环，dpi 越高字越大 */
    public int nextDpi() {
        if (nativeDpi < 200) return 200;
        if (nativeDpi < 240) return 240;
        if (nativeDpi < 280) return 280;
        return 160;
    }

    public float cropTop() {
        return cropTop;
    }

    public int interval() {
        return intervalMs;
    }

    public String start(Context ctx, int resultCode, Intent data) {
        stop();
        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, data);
            if (projection == null) return "投屏授权失败";

            thread = new HandlerThread("hud-mirror");
            thread.start();
            handler = new Handler(thread.getLooper());

            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int w = dm.widthPixels, h = dm.heightPixels;
            int cw, ch, dpi;
            if (nativeRes) {
                float sc = scale;
                cw = Math.round(480 * sc);
                ch = Math.round(800 * sc);
                dpi = Math.round(nativeDpi * sc);
            } else {
                cw = Math.max(360, w / 3);
                ch = Math.max(600, h / 3);
                dpi = Math.max(1, dm.densityDpi / 3);
            }

            reader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2);
            vd = projection.createVirtualDisplay("hud-mirror", cw, ch, Math.max(1, dpi),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
            reader.setOnImageAvailableListener(this::onFrame, handler);
            running = true;
            return "镜像已启动 " + cw + "x" + ch + (nativeRes ? ("（原生×" + scale + "）") : "");
        } catch (Throwable t) {
            return "启动失败: " + t.getClass().getSimpleName();
        }
    }

    public void stop() {
        running = false;
        try { if (vd != null) vd.release(); } catch (Throwable ignored) { }
        vd = null;
        try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
        reader = null;
        try { if (projection != null) projection.stop(); } catch (Throwable ignored) { }
        projection = null;
        try { if (thread != null) thread.quitSafely(); } catch (Throwable ignored) { }
        thread = null;
    }

    /**
     * 找内容边界：按行/列的像素亮度差异判断“边框”。
     * 纯白边、纯黑边（含游戏自带的上下黑边）都能识别，
     * 比“和角点颜色对比”可靠得多。
     */
    private static int[] contentBox(int[] px, int w, int h) {
        final int TH = 18;   // 亮度差异阈值：低于它视为纯色边框

        int[] rowVar = new int[h];
        for (int y = 0; y < h; y++) {
            int min = 999, max = -1;
            int base = y * w;
            for (int x = 0; x < w; x += 6) {
                int c = px[base + x];
                int lum = ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
                if (lum < min) min = lum;
                if (lum > max) max = lum;
            }
            rowVar[y] = max - min;
        }

        int[] colVar = new int[w];
        for (int x = 0; x < w; x++) {
            int min = 999, max = -1;
            for (int y = 0; y < h; y += 6) {
                int c = px[y * w + x];
                int lum = ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
                if (lum < min) min = lum;
                if (lum > max) max = lum;
            }
            colVar[x] = max - min;
        }

        int top = 0, bottom = h - 1, left = 0, right = w - 1;
        while (top < bottom && rowVar[top] < TH) top++;
        while (bottom > top && rowVar[bottom] < TH) bottom--;
        while (left < right && colVar[left] < TH) left++;
        while (right > left && colVar[right] < TH) right--;

        // 裁得太狠说明判断不可靠，回退整幅
        if (right - left < w / 4 || bottom - top < h / 4) return new int[]{0, 0, w, h};
        return new int[]{left, top, right + 1, bottom + 1};
    }

    private void onFrame(ImageReader r) {
        Image img = null;
        Bitmap raw = null, out = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;

            long now = System.currentTimeMillis();
            if (now - lastAt < intervalMs) return;
            lastAt = now;

            Image.Plane p = img.getPlanes()[0];
            ByteBuffer buf = p.getBuffer();
            int pixelStride = p.getPixelStride();
            int rowStride = p.getRowStride();
            int width = img.getWidth();
            int height = img.getHeight();
            int bufW = rowStride / pixelStride;

            raw = Bitmap.createBitmap(bufW, height, Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(buf);

            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            paint.setColorFilter(new ColorMatrixColorFilter(cm));

            float crop = cropTop;
            Rect src = new Rect(0, 0, width, height);
            int outW = 480, outH = 800;
            int wantRot = 0;

            if (nativeRes) {
                // 虚拟屏尺寸是固定的，但应用可能在内部横屏排版
                if (cropBars) {
                    int[] px = new int[width * height];
                    raw.getPixels(px, 0, width, 0, 0, width, height);
                    int[] box = contentBox(px, width, height);
                    src.set(box[0], box[1], box[2], box[3]);
                }
                boolean landscape = src.width() > src.height();
                if (frameCount % 5 == 0) {
                    DebugLog.push("内容框 " + src.width() + "x" + src.height()
                            + " 横屏=" + landscape);
                }
                if (autoRotate && landscape) {
                    outW = 800;
                    outH = 480;
                    wantRot = landscapeDeg;
                }
            } else if (crop > 0f && crop < 1f) {
                src.set(0, 0, width, Math.max(1, (int) (height * crop)));
            }

            // 先告知电纸书该用竖屏还是横屏画布；定期重发，防止它重启/重连后状态丢失
            frameCount++;
            if (wantRot != lastSentRot || frameCount % 15 == 0) {
                lastSentRot = wantRot;
                Sender.push("{\"cmd\":\"rotate\",\"deg\":" + wantRot + "}");
            }

            if (out == null || out.getWidth() != outW || out.getHeight() != outH) {
                out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            }
            Canvas c = new Canvas(out);
            c.drawColor(Color.WHITE);

            if (nativeRes) {
                // 横屏时输出 800x480、竖屏时 480x800，都是铺满不拉伸
                c.drawBitmap(raw, src, new Rect(0, 0, outW, outH), paint);
            } else if (crop > 0f && crop < 1f) {
                c.drawBitmap(raw, src, new Rect(0, 0, 480, 800), paint);
            } else {
                float s = Math.min(480f / width, 800f / height);
                float dw = width * s, dh = height * s;
                float left = (480f - dw) / 2f, top = (800f - dh) / 2f;
                c.drawBitmap(raw, src, new Rect(Math.round(left), Math.round(top),
                        Math.round(left + dw), Math.round(top + dh)), paint);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream(32 * 1024);
            out.compress(Bitmap.CompressFormat.JPEG, nativeRes ? 68 : 55, bo);
            byte[] jpeg = bo.toByteArray();
            Sender.pushImage(jpeg);
        } catch (Throwable t) {
            DebugLog.push("抓帧失败: " + t.getClass().getSimpleName());
        } finally {
            try { if (img != null) img.close(); } catch (Throwable ignored) { }
            try { if (raw != null) raw.recycle(); } catch (Throwable ignored) { }
            try { if (out != null) out.recycle(); } catch (Throwable ignored) { }
        }
    }
}
