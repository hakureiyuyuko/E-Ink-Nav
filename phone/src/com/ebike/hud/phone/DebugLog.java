package com.ebike.hud.phone;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class DebugLog {
    private static final LinkedList<String> LINES = new LinkedList<>();
    private static final int MAX = 40;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static synchronized void push(String s) {
        if (s == null) return;
        if (s.length() > 300) s = s.substring(0, 300) + "…";
        String line = FMT.format(new Date()) + "  " + s;
        Log.i("HudLog", line);
        LINES.addFirst(line);
        while (LINES.size() > MAX) LINES.removeLast();
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String l : LINES) sb.append(l).append('\n');
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
