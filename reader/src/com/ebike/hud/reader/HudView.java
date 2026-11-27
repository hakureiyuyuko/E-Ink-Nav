package com.ebike.hud.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * 墨水屏专用绘制：纯黑白、零动画、只在内容变化时重绘。
 * 逻辑坐标固定 480x800，按实际尺寸缩放。
 */
public class HudView extends View {

    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);

    public String status = "启动中…";
    public Bitmap image = null;
    public int canvasRotate = 0;   // 0=竖屏，90/270=横屏（画布旋转）
    // 骑行数据
    public boolean gpsMode = false;
    public float gpsSpeed = 0f;
    public float gpsAlt = 0f;
    public float gpsHdg = 0f;
    public float gpsAcc = 0f;
    public boolean gpsWait = false;   // 还在搜星
    public int gpsWaitSec = 0;
    public String tip1 = "";
    public String tip2 = "";
    public String arrow = "";
    public String dist = "";
    public String unit = "";
    public String road = "";
    public String remain = "";
    public String eta = "";
    public long lastRx = 0L;

    public HudView(Context c) {
        super(c);
        setBackgroundColor(Color.WHITE);
        tp.setTypeface(Typeface.DEFAULT);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(26f);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeJoin(Paint.Join.ROUND);
        sp.setColor(Color.BLACK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float sx = getWidth() / 480f;
        float sy = getHeight() / 800f;
        canvas.save();
        canvas.scale(sx, sy);
        canvas.drawColor(Color.WHITE);

        // ---- 镜像模式：直接把手机截图铺满屏幕 ----
        Bitmap img = image;
        if (img != null && !img.isRecycled()) {
            int rot = canvasRotate;
            float LW = 480f, LH = 800f;
            if (rot == 90) {
                canvas.translate(480f, 0f);
                canvas.rotate(90f);
                LW = 800f;
                LH = 480f;
            } else if (rot == 270) {
                canvas.translate(0f, 800f);
                canvas.rotate(-90f);
                LW = 800f;
                LH = 480f;
            }
            float s = Math.min(LW / img.getWidth(), LH / img.getHeight());
            float dw = img.getWidth() * s, dh = img.getHeight() * s;
            RectF dst = new RectF((LW - dw) / 2f, (LH - dh) / 2f,
                    (LW - dw) / 2f + dw, (LH - dh) / 2f + dh);
            Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            canvas.drawBitmap(img, null, dst, bp);
            canvas.restore();
            return;
        }

        // ---- 骑行数据仪表盘 ----
        if (gpsMode) {
            drawGps(canvas);
            canvas.restore();
            return;
        }

        // ---- 顶部状态行 ----
        tp.setTypeface(Typeface.DEFAULT);
        tp.setTextSize(23f);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(Color.parseColor("#555555"));
        canvas.drawText(fit(status, 330f), 20f, 42f, tp);

        long now = System.currentTimeMillis();
        if (lastRx > 0) {
            long age = (now - lastRx) / 1000L;
            tp.setTextAlign(Paint.Align.RIGHT);
            tp.setColor(age > 25 ? Color.parseColor("#990000") : Color.parseColor("#888888"));
            canvas.drawText(age <= 2 ? "实时" : age + "s 前", 460f, 42f, tp);
        }

        // ---- 转向箭头 ----
        if (arrow != null && arrow.length() > 0) {
            drawArrow(canvas, 240f, 292f, 330f);
        } else {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(40f);
            tp.setColor(Color.parseColor("#BBBBBB"));
            canvas.drawText("等待导航数据", 240f, 300f, tp);
            tp.setTextSize(26f);
            tp.setColor(Color.parseColor("#888888"));
            if (tip1 != null && tip1.length() > 0) canvas.drawText(tip1, 240f, 366f, tp);
            if (tip2 != null && tip2.length() > 0) canvas.drawText(tip2, 240f, 406f, tp);
        }

        // ---- 距离 ----
        if (dist != null && dist.length() > 0) {
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setColor(Color.BLACK);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(150f);
            float dw = tp.measureText(dist);
            tp.setTextSize(64f);
            float uw = (unit != null && unit.length() > 0) ? tp.measureText(unit) : 0f;
            float x = 240f - (dw + (uw > 0 ? uw + 10f : 0f)) / 2f;
            tp.setTextSize(150f);
            canvas.drawText(dist, x, 606f, tp);
            if (uw > 0) {
                tp.setTextSize(64f);
                canvas.drawText(unit, x + dw + 10f, 606f, tp);
            }
        }

        // ---- 路名 / 动作 ----
        if (road != null && road.length() > 0) {
            tp.setTypeface(Typeface.DEFAULT);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(42f);
            tp.setColor(Color.BLACK);
            canvas.drawText(ellipsize(road, 11), 240f, 672f, tp);
        }

        // ---- 底部：剩余 / 到达 ----
        StringBuilder sb = new StringBuilder();
        if (remain != null && remain.length() > 0) sb.append("剩余 ").append(remain);
        if (eta != null && eta.length() > 0) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(eta).append(" 到达");
        }
        if (sb.length() > 0) {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(30f);
            tp.setColor(Color.parseColor("#333333"));
            canvas.drawText(sb.toString(), 240f, 748f, tp);
        }

        canvas.restore();
    }

    private void drawGps(Canvas c) {
        // 还在搜星：给出明确反馈，避免看起来像“没反应”
        if (gpsWait) {
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setColor(Color.BLACK);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(64f);
            c.drawText("正在搜索卫星…", 240f, 330f, tp);
            tp.setTypeface(Typeface.DEFAULT);
            tp.setTextSize(40f);
            tp.setColor(Color.parseColor("#555555"));
            c.drawText("已等待 " + gpsWaitSec + " 秒", 240f, 400f, tp);
            tp.setTextSize(26f);
            c.drawText("室内无信号，请到室外", 240f, 460f, tp);
            return;
        }

        // 速度：自适应字号，保证不超出屏幕
        String spd = String.format(java.util.Locale.US, "%.1f", gpsSpeed);
        String unit = "km/h";
        float size = 210f;
        float unitSize = 58f;
        float maxW = 420f;
        while (size > 80f) {
            tp.setTextSize(size);
            float w1 = tp.measureText(spd);
            tp.setTextSize(unitSize);
            float w2 = tp.measureText(unit);
            if (w1 + w2 + 14f <= maxW) break;
            size -= 8f;
            unitSize = Math.max(30f, size * 0.28f);
        }

        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setColor(Color.BLACK);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(size);
        float w1 = tp.measureText(spd);
        tp.setTextSize(unitSize);
        float w2 = tp.measureText(unit);
        float x = 240f - (w1 + w2 + 14f) / 2f;
        tp.setTextSize(size);
        c.drawText(spd, x, 350f, tp);
        tp.setTextSize(unitSize);
        c.drawText(unit, x + w1 + 14f, 350f, tp);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.BLACK);
        line.setStrokeWidth(3f);
        c.drawLine(70f, 400f, 410f, 400f, line);

        // 海拔 / 方向
        tp.setTypeface(Typeface.DEFAULT);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(46f);
        c.drawText(String.format(java.util.Locale.US, "海拔 %.0f m", gpsAlt), 240f, 480f, tp);
        c.drawText(String.format(java.util.Locale.US, "方向 %.0f° %s",
                gpsHdg, dirName(gpsHdg)), 240f, 545f, tp);

        // 罗盘
        drawCompass(c, 240f, 690f, 68f);

        if (gpsAcc > 0.5f) {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(26f);
            tp.setColor(Color.parseColor("#777777"));
            c.drawText(String.format(java.util.Locale.US, "GPS 精度 ±%.0f m", gpsAcc), 240f, 785f, tp);
        }
    }

    private void drawCompass(Canvas c, float cx, float cy, float r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8f);
        p.setColor(Color.BLACK);
        c.drawCircle(cx, cy, r, p);

        c.save();
        c.rotate(gpsHdg, cx, cy);
        Path tri = new Path();
        tri.moveTo(cx, cy - r * 0.86f);
        tri.lineTo(cx - r * 0.30f, cy - r * 0.10f);
        tri.lineTo(cx + r * 0.30f, cy - r * 0.10f);
        tri.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.BLACK);
        c.drawPath(tri, fill);
        c.restore();

        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setTextSize(30f);
        tp.setColor(Color.BLACK);
        c.drawText("N", cx, cy - r - 12f, tp);
    }

    private String dirName(float deg) {
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        int i = Math.round(((deg % 360f) + 360f) % 360f / 45f) % 8;
        return names[i];
    }

    private String fit(String s, float maxWidth) {
        if (s == null) return "";
        if (tp.measureText(s) <= maxWidth) return s;
        int n = tp.breakText(s, true, maxWidth, null);
        if (n <= 0) return "";
        return s.substring(0, Math.max(0, n - 1)) + "…";
    }

    private String ellipsize(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void drawArrow(Canvas c, float cx, float cy, float size) {
        Path stroke = new Path();
        Path fill = new Path();
        float H = size / 2f;
        String t = arrow == null ? "" : arrow;

        c.save();
        c.translate(cx, cy);

        if (t.equals("left") || t.equals("right")) {
            float d = t.equals("right") ? -1f : 1f;
            stroke.moveTo(0.42f * H * d, 0.88f * H);
            stroke.lineTo(0.42f * H * d, -0.02f * H);
            stroke.lineTo(-0.30f * H * d, -0.02f * H);
            stroke.moveTo(-0.00f * H * d, -0.42f * H);
            stroke.lineTo(-0.76f * H * d, -0.02f * H);
            stroke.lineTo(-0.00f * H * d, 0.38f * H);
        } else if (t.equals("slightleft") || t.equals("slightright")
                || t.equals("keepleft") || t.equals("keepright")) {
            boolean l = t.endsWith("left");
            float ang = l ? -32f : 32f;
            if (t.startsWith("keep")) ang *= 0.62f;
            c.rotate(ang);
            straight(stroke, H);
        } else if (t.equals("uturn")) {
            stroke.moveTo(0.30f * H, 0.88f * H);
            stroke.lineTo(0.30f * H, -0.16f * H);
            stroke.quadTo(0.30f * H, -0.64f * H, 0f, -0.64f * H);
            stroke.quadTo(-0.30f * H, -0.64f * H, -0.30f * H, -0.16f * H);
            stroke.lineTo(-0.30f * H, 0.32f * H);
            stroke.moveTo(-0.66f * H, 0.00f * H);
            stroke.lineTo(-0.30f * H, 0.44f * H);
            stroke.lineTo(0.06f * H, 0.00f * H);
        } else if (t.equals("roundabout")) {
            stroke.addCircle(0f, 0f, 0.32f * H, Path.Direction.CW);
            // 出口：右上 45° 甩出并带箭头
            stroke.moveTo(0.226f * H, -0.226f * H);
            stroke.lineTo(0.52f * H, -0.52f * H);
            stroke.moveTo(0.46f * H, -0.18f * H);
            stroke.lineTo(0.52f * H, -0.52f * H);
            stroke.lineTo(0.18f * H, -0.46f * H);
        } else if (t.equals("arrive")) {
            stroke.moveTo(-0.34f * H, 0.86f * H);
            stroke.lineTo(-0.34f * H, -0.74f * H);
            fill.moveTo(-0.34f * H, -0.74f * H);
            fill.lineTo(0.44f * H, -0.46f * H);
            fill.lineTo(-0.34f * H, -0.18f * H);
            fill.close();
        } else {
            straight(stroke, H);
        }

        c.drawPath(stroke, sp);
        if (!fill.isEmpty()) {
            Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
            fp.setStyle(Paint.Style.FILL);
            fp.setColor(Color.BLACK);
            c.drawPath(fill, fp);
        }
        c.restore();
    }

    private void straight(Path p, float H) {
        p.moveTo(0f, 0.88f * H);
        p.lineTo(0f, -0.56f * H);
        p.moveTo(-0.40f * H, -0.14f * H);
        p.lineTo(0f, -0.74f * H);
        p.lineTo(0.40f * H, -0.14f * H);
    }
}
