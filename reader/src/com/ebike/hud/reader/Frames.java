package com.ebike.hud.reader;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 帧协议（和手机端 Sender 对应）：
 *   '{' 开头      -> 旧版按行 JSON（兼容测试脚本）
 *   0x01 + len4 + JPEG 数据 -> 屏幕镜像帧
 *   0x02 + len4 + JSON      -> 数据帧
 */
public class Frames {

    public interface Sink {
        void onNav(JSONObject o);

        void onImage(byte[] jpeg);
    }

    public static void loop(InputStream raw, Sink sink) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw, 128 * 1024);
        while (true) {
            int b = in.read();
            if (b < 0) return;

            if (b == '{') {
                ByteArrayOutputStream bo = new ByteArrayOutputStream(512);
                bo.write(b);
                int c;
                while ((c = in.read()) >= 0 && c != '\n') bo.write(c);
                try {
                    sink.onNav(new JSONObject(new String(bo.toByteArray(), "UTF-8")));
                } catch (Exception ignored) { }
                continue;
            }

            if (b != 1 && b != 2) continue;

            int len = 0;
            for (int i = 0; i < 4; i++) {
                int c = in.read();
                if (c < 0) return;
                len = (len << 8) | (c & 0xFF);
            }
            if (len <= 0 || len > 6 * 1024 * 1024) continue;

            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) {
                int n = in.read(buf, off, len - off);
                if (n < 0) return;
                off += n;
            }
            if (b == 1) {
                sink.onImage(buf);
            } else {
                try {
                    sink.onNav(new JSONObject(new String(buf, "UTF-8")));
                } catch (Exception ignored) { }
            }
        }
    }

    public static byte[] frame(int type, byte[] payload) {
        byte[] out = new byte[payload.length + 5];
        out[0] = (byte) type;
        out[1] = (byte) ((payload.length >>> 24) & 0xFF);
        out[2] = (byte) ((payload.length >>> 16) & 0xFF);
        out[3] = (byte) ((payload.length >>> 8) & 0xFF);
        out[4] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, out, 5, payload.length);
        return out;
    }
}
