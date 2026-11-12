package org.telegram.utils.twidget;

import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A TwidgetCore display list in the bridge's binary form (twidget-bridge.cpp, encodeDisplayList, which documents the
 * layout), read in paint order by a {@link Cursor}. It comes from the widget engine, possibly compromised: every read
 * is bounds-checked, every count is checked against what is left before anything is allocated, and numbers that are
 * not finite read as 0. A malformed list throws IllegalArgumentException, which drops the frame.
 */
final class WidgetDisplayList {
    static final int MAGIC = 0x4C445754; // "TWDL"
    static final int VERSION = 1;
    static final int HAS_RECT = 1, HAS_RADII = 2, HAS_CLIP = 4;
    // Bounds for one command's parts, far above what the engine's own limits let through.
    static final int MAX_STOPS = 4096, MAX_FILTERS = 64, MAX_PATH_OPS = 1 << 20, MAX_DASHES = 1024;
    private static final int HEADER = 20;

    private final ByteBuffer data;
    final int count;
    final boolean hasBackdrop;
    private final int end;
    private final int[] stringOffsets, stringLengths;
    private final String[] strings;

    private WidgetDisplayList(ByteBuffer data, int count, boolean hasBackdrop, int end, int[] offsets, int[] lengths) {
        this.data = data;
        this.count = count;
        this.hasBackdrop = hasBackdrop;
        this.end = end;
        this.stringOffsets = offsets;
        this.stringLengths = lengths;
        this.strings = new String[offsets.length];
    }

    /** The header and the string table checked; the commands are read as they are painted. */
    static WidgetDisplayList parse(byte[] bytes, int maxCommands) {
        if (bytes == null || bytes.length < HEADER + 4 || (bytes.length & 3) != 0) throw invalid();
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (data.getInt(0) != MAGIC || data.getInt(4) != VERSION) throw invalid();
        int count = data.getInt(8);
        int flags = data.getInt(12);
        long table = data.getInt(16) * 4L;
        if (count < 0 || count > maxCommands || table < HEADER || table + 4 > bytes.length) throw invalid();
        int at = (int) table;
        int strings = data.getInt(at);
        at += 4;
        if (strings < 0 || strings > count || strings > (bytes.length - at) / 4) throw invalid();
        int[] offsets = new int[strings], lengths = new int[strings];
        for (int i = 0; i < strings; i++) {
            if (at + 4 > bytes.length) throw invalid();
            int length = data.getInt(at);
            if (length < 0 || length > bytes.length - at - 4) throw invalid();
            offsets[i] = at + 4;
            lengths[i] = length;
            at += 4 + ((length + 3) & ~3);
        }
        if (at > bytes.length) throw invalid();
        return new WidgetDisplayList(data, count, (flags & 1) != 0, (int) table, offsets, lengths);
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Display list");
    }

    String string(int index) {
        if (index < 0 || index >= strings.length) throw invalid();
        String text = strings[index];
        if (text == null) {
            text = new String(data.array(), stringOffsets[index], stringLengths[index], StandardCharsets.UTF_8);
            strings[index] = text;
        }
        return text;
    }

    Cursor cursor() {
        return new Cursor();
    }

    /** The commands in order. Absolute reads: the list itself is never changed, so cursors may run side by side. */
    final class Cursor {
        private int at = HEADER;

        int i() {
            if (at + 4 > end) throw invalid();
            int value = data.getInt(at);
            at += 4;
            return value;
        }

        float f() {
            float value = Float.intBitsToFloat(i());
            return Float.isNaN(value) || Float.isInfinite(value) ? 0 : value;
        }

        /** A count of items of `words` words each, checked against `max` and against what is left. */
        int count(int max, int words) {
            int n = i();
            if (n < 0 || n > max || (long) n * words * 4 > end - at) throw invalid();
            return n;
        }

        /** x, y, w, h as a RectF. */
        RectF rect(RectF out) {
            float x = f(), y = f(), w = f(), h = f();
            out.set(x, y, x + w, y + h);
            return out;
        }

        void floats(float[] out, int n) {
            for (int k = 0; k < n; k++) out[k] = f();
        }

        /** `n` op bytes, four to a word: where they start in the list, read with {@link #op}. */
        int ops(int n) {
            int start = at;
            long words = (n + 3L) / 4;
            if (words * 4 > end - at) throw invalid();
            at += (int) words * 4;
            return start;
        }

        int op(int start, int k) {
            return data.get(start + k) & 0xff;
        }

        void skip(int words) {
            if (words < 0 || (long) words * 4 > end - at) throw invalid();
            at += words * 4;
        }
    }

    // ── Debug: the list as JSON in to_json's keys, for the test screen's dumps ──

    /** `bytes` as to_json writes a display list, or an error object. */
    static String toJson(byte[] bytes) {
        try {
            WidgetDisplayList list = parse(bytes, Integer.MAX_VALUE);
            Cursor c = list.cursor();
            JSONArray out = new JSONArray();
            RectF r = new RectF();
            for (int n = 0; n < list.count; n++) {
                int head = c.i(), kind = head & 0xff, present = head >>> 8;
                JSONObject o = new JSONObject().put("k", kind);
                if ((present & HAS_RECT) != 0) o.put("r", rect(c.rect(r)));
                if ((present & HAS_RADII) != 0) o.put("q", floats(c, 8));
                if ((present & HAS_CLIP) != 0) o.put("l", rect(c.rect(r)));
                switch (kind) {
                    case 0: case 2: o.put("c", c.i() & 0xffffffffL); break;
                    case 1: case 3: o.put("c", c.i() & 0xffffffffL).put("w", c.f()); break;
                    case 4: o.put("c", c.i() & 0xffffffffL).put("f", c.i()).put("x", c.f()).put("y", c.f()).put("t", list.string(c.i())); break;
                    case 5: { int canvas = c.i(); o.put(canvas != 0 ? "cv" : "i", c.i()); break; }
                    case 6: case 7: case 8: o.put("a", floats(c, 4)).put("s", stops(c)); break;
                    case 9: if (c.i() != 0) o.put("p", path(c)); break;
                    case 11: if (c.i() != 0) { JSONObject p = path(c); svg(c, p); o.put("p", p); } break;
                    case 12: o.put("m", floats(c, 6)).put("o", c.f()); if (c.i() != 0) o.put("h", 1); break;
                    case 14: { o.put("c", c.i() & 0xffffffffL).put("a", floats(c, 4)); if (c.i() != 0) o.put("n", 1); break; }
                    case 15: case 17: {
                        int k = c.count(MAX_FILTERS, 5);
                        JSONArray fl = new JSONArray();
                        for (int j = 0; j < k; j++)
                            fl.put(new JSONArray().put(c.i()).put(c.f()).put(c.f()).put(c.f()).put(c.i() & 0xffffffffL));
                        o.put("fl", fl);
                        break;
                    }
                    default: break;
                }
                out.put(o);
            }
            return out.toString();
        } catch (Exception e) {
            return "{\"error\":" + JSONObject.quote(String.valueOf(e)) + "}";
        }
    }

    private static JSONArray rect(RectF r) throws Exception {
        return new JSONArray().put(r.left).put(r.top).put(r.width()).put(r.height());
    }

    private static JSONArray floats(Cursor c, int n) throws Exception {
        JSONArray a = new JSONArray();
        for (int k = 0; k < n; k++) a.put(c.f());
        return a;
    }

    private static JSONArray stops(Cursor c) throws Exception {
        int n = c.count(MAX_STOPS, 2);
        JSONArray a = new JSONArray();
        for (int k = 0; k < n; k++) a.put(c.f()).put(c.i() & 0xffffffffL);
        return a;
    }

    private static JSONObject path(Cursor c) throws Exception {
        int ops = c.count(MAX_PATH_OPS, 0), points = c.count(MAX_PATH_OPS * 6, 1);
        int start = c.ops(ops);
        JSONArray o = new JSONArray(), v = new JSONArray();
        for (int k = 0; k < ops; k++) o.put(c.op(start, k));
        for (int k = 0; k < points; k++) v.put(c.f());
        JSONObject p = new JSONObject().put("o", o).put("v", v);
        if (c.i() != 0) p.put("e", 1);
        return p;
    }

    private static void svg(Cursor c, JSONObject p) throws Exception {
        int flags = c.i();
        if ((flags & 1) != 0) {
            int g = c.i();
            if (g == 0) p.put("f", c.i() & 0xffffffffL);
            else p.put("g", g).put("ga", floats(c, 7)).put("s", stops(c));
        }
        if ((flags & 2) != 0) {
            p.put("sc", c.i() & 0xffffffffL);
            int g = c.i();
            if (g != 0) p.put("sg", g).put("sga", floats(c, 7)).put("ss", stops(c));
            p.put("sw", c.f()).put("lc", c.i()).put("lj", c.i()).put("ml", c.f());
            int n = c.count(MAX_DASHES, 1);
            if (n > 0) p.put("d", floats(c, n));
            float offset = c.f();
            if (n > 0) p.put("do", offset);
        }
    }
}
