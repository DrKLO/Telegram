package org.telegram.ui.Components;

import android.graphics.*;
import android.os.*;
import androidx.annotation.*;
import org.intellij.lang.annotations.Language;
import org.telegram.ui.ProfileActivity;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MetaMask {
    private static final float SOFT_EDGE_PX = 2f;
    private static final float LARGE_MASK_PX = 500f;

    public static float ISO = 1.15f;

    public static boolean FORCE_SW_MASK = false;

    public static void draw(Canvas cv, RectF bubble, RectF button, Paint paint, boolean pillActsAsSingle) {
        if (Build.VERSION.SDK_INT >= 33 && !ProfileActivity.FORCE_MY_BLUR && !FORCE_SW_MASK) {
            drawShader(cv, bubble, button, paint, pillActsAsSingle, ISO);
        } else {
            drawBitmapAsync(cv, bubble, button, paint, pillActsAsSingle, ISO);
        }
    }

    public static void shutdown() {
        try {
            if (worker.bitmap != null) {
                worker.bitmap.recycle();
                worker.bitmap = null;
            }
            if (worker.lastMask != null) {
                worker.lastMask.recycle();
                worker.lastMask = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            worker.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static RuntimeShader shader;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static void drawShader(Canvas cv, RectF bubble, RectF button, Paint paint, boolean pillSingle, float iso) {
        if (shader == null) shader = new RuntimeShader(SRC);
        shader.setFloatUniform("uBubble", bubble.centerX(), bubble.centerY(), bubble.width() * .5f, bubble.height() * .5f);
        shader.setFloatUniform("uPillSingle", pillSingle ? 1f : 0f);
        shader.setFloatUniform("uBtn", button.centerX(), button.centerY(), button.width() * .5f);
        shader.setFloatUniform("uIso", iso);
        shader.setFloatUniform("uBlur", SOFT_EDGE_PX);
        paint.setShader(shader);
        cv.drawRect(0, 0, cv.getWidth(), cv.getHeight(), paint);
        paint.setShader(null);
    }

    @Language("AGSL")
    private static final String SRC =
            "uniform float4 uBubble;      // cx,cy,rx,ry                 \n" +
                    "uniform float3 uBtn;         // cx,cy,r                     \n" +
                    "uniform float  uPillSingle;  // 1 = ellipse, 0 = twin caps  \n" +
                    "uniform float  uIso;         // iso threshold               \n" +
                    "uniform float  uBlur;        // soft edge width (px)        \n" +
                    "                                                                  \n" +
                    "float field(float2 p, float2 c, float r) {                  \n" +
                    "    return r*r / (dot(p-c,p-c) + 1.0);                      \n" +
                    "}                                                           \n" +
                    "                                                                  \n" +
                    "half4 main(float2 p) {                                      \n" +
                    "    float v = 0.0;                                          \n" +
                    "    if (uPillSingle > 0.5) {                                \n" +
                    "        float2 d = (p - uBubble.xy) / float2(uBubble.z,uBubble.w);\n" +
                    "        v += 1.0 / (dot(d,d) + 0.0001);                     \n" +
                    "    } else {                                                \n" +
                    "        float r = uBubble.w;                                \n" +
                    "        float dx = uBubble.z - r;                           \n" +
                    "        float2 L = uBubble.xy - float2(dx,0);               \n" +
                    "        float2 R = uBubble.xy + float2(dx,0);               \n" +
                    "        v += field(p,L,r) + field(p,R,r);                   \n" +
                    "    }                                                       \n" +
                    "    v += field(p,uBtn.xy,uBtn.z);                           \n" +
                    "    float a = smoothstep(uIso - uBlur*0.01,                 \n" +
                    "                         uIso + uBlur*0.01, v);             \n" +
                    "    return half4(0.0,0.0,0.0,a);                      \n" +
                    "}";

    private static final Worker worker = new Worker();

    private static void drawBitmapAsync(Canvas cv, RectF bubble, RectF button, Paint paint, boolean pillSingle, float iso) {
        Bitmap mask = worker.lastMask;
        RectF dst = worker.lastDst;
        if (mask != null && !mask.isRecycled() && dst != null) {
            paint.setFilterBitmap(true);
            try {
                cv.drawBitmap(mask, null, dst, paint);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        worker.request(bubble, button, pillSingle, iso);
    }

    private static final class Worker {

        volatile Bitmap lastMask;
        volatile RectF  lastDst;
        private final AtomicReference<Job> pending = new AtomicReference<>();

        private ExecutorService exec =
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "MetaMaskWorker");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                });
        private Bitmap bitmap;
        private int[]  alpha;
        private int mw, mh;

        void request(RectF bubble, RectF button, boolean pillSingle, float iso) {
            Job j = new Job(new RectF(bubble), new RectF(button), pillSingle, iso);
            if (pending.getAndSet(j) == null) {
                ensureExecutor();
                exec.execute(this::run);
            }
        }
        void shutdown() {
            if (exec != null && !exec.isShutdown()) exec.shutdownNow();
        }

        private void ensureExecutor() {
            if (exec == null || exec.isShutdown()) {
                exec = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "MetaMaskWorker");
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                });
            }
        }

        private void run() {
            Job j;
            while ((j = pending.getAndSet(null)) != null) {
                Bitmap bmp = render(j);
                lastMask = bmp;
                lastDst = j.dst;
            }
        }

        private Bitmap render(Job j) {
            int STEP = (j.dst.width() > LARGE_MASK_PX || j.dst.height()> LARGE_MASK_PX) ? 3 : 2;
            int w = Math.max(1, (int) (j.dst.width()  / STEP));
            int h = Math.max(1, (int) (j.dst.height() / STEP));

            ensureBitmap(w,h);
            boolean pill = j.bubble.width() > j.bubble.height() + 0.5f;
            float rb = j.bubble.height() * 0.5f;
            float cbx = j.bubble.centerX(), cby = j.bubble.centerY();
            float cbLx = j.bubble.left + rb;
            float cbRx = j.bubble.right - rb;
            float rBtn = j.button.width() * 0.5f;
            float cBx = j.button.centerX(), cBy = j.button.centerY();

            int idx = 0;
            for(int y = 0; y < h; y++){
                float wy = j.dst.top + (y + 0.5f) * STEP;
                for(int x=0; x < w; x++, idx++){
                    float wx=j.dst.left+(x+.5f) * STEP;
                    float v;
                    if (!pill) {
                        v = r2(wx, wy, cbx, cby, rb);
                    } else if (j.pillSingle) {
                        float s = j.bubble.width() / j.bubble.height();
                        float dx = (wx-cbx) / s, dy = wy - cby;
                        v = (rb * rb) / (dx * dx + dy * dy + 1f);
                    } else {
                        v = r2(wx, wy, cbLx, cby, rb) + r2(wx, wy, cbRx, cby, rb);
                    }
                    v += r2(wx, wy, cBx, cBy, rBtn);
                    alpha[idx] = (v >= j.iso) ? 0xFF000000 : 0x00000000;
                }
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.setPixels(alpha, 0, w, 0, 0, w, h);
            }
            return bitmap;
        }

        private void ensureBitmap(int w, int h){
            if (bitmap != null && w == mw && h == mh) return;
            if (bitmap != null) bitmap.recycle();
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
            alpha = new int[w * h]; mw = w; mh = h;
        }
        private static float r2(float x,float y,float cx,float cy,float r){
            float dx = cx - x, dy = cy - y;
            return (r * r) / (dx * dx + dy * dy + 1f);
        }

        private static final class Job {

            final RectF bubble,button,dst;
            final boolean pillSingle;
            final float iso;

            Job(RectF b, RectF bt, boolean s, float iso) {
                bubble = b; button = bt; pillSingle = s; this.iso = iso;
                float margin = Math.max(b.height(), bt.height()) * 0.55f;
                dst = new RectF(b); dst.union(bt); dst.inset(-margin, -margin);
            }
        }
    }

    public static float isoForGapPx(float r0, float r1, float gap) {
        float d = r0 + r1 + gap;
        if (d <= 0) return 1f;
        float s = (float) Math.pow(r0 / r1, 2.0 / 3.0);
        float t = 1f + s;
        float term = (float) (Math.pow(r0, 2.0 / 3.0) * Math.pow(r1, 4.0 / 3.0) + r1 * r1);
        return (t * t / (d * d)) * term;
    }
}
