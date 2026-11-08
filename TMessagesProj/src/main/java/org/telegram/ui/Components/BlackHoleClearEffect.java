package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

/**
 * سیاه‌چاله از بالای صفحه پایین می‌آید و همه پیام‌های چت را به سمت بالا می‌کشد و می‌بلعد.
 * فقط Canvas ساده، بدون OpenGL.
 */
public class BlackHoleClearEffect extends View {

    private static final float EMERGE = 0.6f;          // پایین آمدن سیاه‌چاله
    private static final float SWALLOW_START = 0.25f;  // شروع بلعیدن
    private static final float SWALLOW = 1.1f;         // مدت بلعیدن
    private static final float COLLAPSE = 0.35f;       // برگشت به بالا
    private static final float TOTAL = SWALLOW_START + SWALLOW + COLLAPSE;
    private static final float STAGGER = 0.45f;        // فاصله زمانی بین پیام‌های بالا و پایین

    private static final int COLS = 4;
    private static final int ROWS = 16;

    private static class Item {
        Bitmap bmp;
        float x, y, w, h;
        float delay;
        float[] src = new float[(COLS + 1) * (ROWS + 1) * 2];
        float[] dst = new float[(COLS + 1) * (ROWS + 1) * 2];
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RadialGradient glowShader;
    private final SweepGradient ringShader;

    private final float maxRadius = AndroidUtilities.dp(46);
    private long startTime;
    private boolean finished;
    private View listView;
    private Runnable onFinished;

    private BlackHoleClearEffect(Context context) {
        super(context);
        glowShader = new RadialGradient(0, 0, 1f,
                new int[]{0xFF000000, 0xFF000000, 0xFFFFE2B0, 0x66FF9A40, 0x00FF9A40},
                new float[]{0f, 0.45f, 0.5f, 0.62f, 1f}, Shader.TileMode.CLAMP);
        ringShader = new SweepGradient(0, 0,
                new int[]{0xFFFFFFFF, 0xFFFFC27A, 0x11FFFFFF, 0xFFFFE0B0, 0xFFFFFFFF},
                new float[]{0f, 0.25f, 0.55f, 0.8f, 1f});
        holePaint.setColor(0xFF000000);
        holePaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
    }

    /**
     * @param host        ویوی والد (مثلاً fragmentView در ChatActivity)
     * @param list        لیست پیام‌ها (chatListView)
     * @param onCaptured  بعد از گرفتن عکس از پیام‌ها صدا زده می‌شود؛ پاک کردن واقعی تاریخچه را اینجا انجام بدهید
     * @param onFinished  بعد از پایان انیمیشن صدا زده می‌شود؛ مثلاً نمایش پیام «Chat cleared»
     * @return false اگر چیزی برای انیمیشن نبود (آن موقع خودتان کار را معمولی انجام بدهید)
     */
    public static boolean start(ViewGroup host, ViewGroup list, Runnable onCaptured, Runnable onFinished) {
        if (host == null || list == null || list.getChildCount() == 0 || host.getHeight() == 0) {
            return false;
        }
        BlackHoleClearEffect effect = new BlackHoleClearEffect(host.getContext());
        int[] hostLoc = new int[2];
        int[] childLoc = new int[2];
        host.getLocationInWindow(hostLoc);
        float hostH = host.getHeight();

        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getWidth() <= 0 || child.getHeight() <= 0 || child.getVisibility() != VISIBLE) {
                continue;
            }
            child.getLocationInWindow(childLoc);
            Item item = new Item();
            item.x = childLoc[0] - hostLoc[0];
            item.y = childLoc[1] - hostLoc[1];
            item.w = child.getWidth();
            item.h = child.getHeight();
            if (item.y + item.h < 0 || item.y > hostH) {
                continue;
            }
            try {
                item.bmp = Bitmap.createBitmap(child.getWidth(), child.getHeight(), Bitmap.Config.ARGB_8888);
                child.draw(new Canvas(item.bmp));
            } catch (Throwable e) {
                continue;
            }
            float cy = Math.max(0f, Math.min(1f, (item.y + item.h / 2f) / hostH));
            item.delay = STAGGER * cy;
            int k = 0;
            for (int r = 0; r <= ROWS; r++) {
                for (int c = 0; c <= COLS; c++) {
                    item.src[k++] = item.x + item.w * c / COLS;
                    item.src[k++] = item.y + item.h * r / ROWS;
                }
            }
            effect.items.add(item);
        }
        if (effect.items.isEmpty()) {
            return false;
        }

        effect.listView = list;
        effect.onFinished = onFinished;
        host.addView(effect, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setAlpha(0f);
        effect.startTime = System.nanoTime();
        if (onCaptured != null) {
            onCaptured.run();
        }
        effect.invalidate();
        return true;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (finished) {
            return;
        }
        float t = (System.nanoTime() - startTime) / 1_000_000_000f;

        // --- شکل سیاه‌چاله ---
        float h;
        if (t < EMERGE) {
            float p = clamp(t / EMERGE);
            h = 1f - (1f - p) * (1f - p);
        } else if (t > SWALLOW_START + SWALLOW) {
            float p = clamp((t - SWALLOW_START - SWALLOW) / COLLAPSE);
            h = 1f - p * p;
        } else {
            h = 1f;
        }
        float radius = maxRadius * (0.3f + 0.7f * h);
        float topY = AndroidUtilities.statusBarHeight + maxRadius * 0.9f;
        float cx = getWidth() / 2f;
        float cy = -maxRadius + (topY + maxRadius) * h;

        // --- پیام‌ها ---
        float T = clamp((t - SWALLOW_START) / SWALLOW);
        for (int n = 0; n < items.size(); n++) {
            Item item = items.get(n);
            float p = clamp((T - item.delay) / (1f - STAGGER));
            if (p >= 1f) {
                continue;
            }
            float alpha = 1f - clamp((p - 0.85f) / 0.15f);
            int len = item.src.length;
            for (int k = 0; k < len; k += 2) {
                float vx = item.src[k];
                float vy = item.src[k + 1];
                float frac = clamp((vy - item.y) / item.h);
                // بالای هر پیام زودتر کشیده می‌شود تا پیام کشیده شود
                float pv = clamp(p * 1.25f - 0.25f * frac);
                float e = pv * pv;
                float px = vx + (cx - vx) * e;
                float py = vy + (cy - vy) * e;
                // کمی چرخش
                float ang = 1.5f * e * (1f - e);
                float dx = px - cx;
                float dy = py - cy;
                float cos = (float) Math.cos(ang);
                float sin = (float) Math.sin(ang);
                item.dst[k] = cx + dx * cos - dy * sin;
                item.dst[k + 1] = cy + dx * sin + dy * cos;
            }
            bmpPaint.setAlpha((int) (alpha * 255));
            canvas.save();
            // مش با مختصات مطلق رسم می‌شود، پس bitmap را در مختصات خودش بکشیم
            canvas.translate(0, 0);
            drawItemMesh(canvas, item);
            canvas.restore();
        }

        // --- سیاه‌چاله ---
        if (h > 0.01f) {
            float glowR = radius * 2.2f;
            matrix.setScale(glowR, glowR);
            matrix.postTranslate(cx, cy);
            glowShader.setLocalMatrix(matrix);
            glowPaint.setShader(glowShader);
            canvas.drawCircle(cx, cy, glowR, glowPaint);

            canvas.drawCircle(cx, cy, radius, holePaint);

            matrix.setRotate(t * 250f);
            matrix.postTranslate(cx, cy);
            ringShader.setLocalMatrix(matrix);
            ringPaint.setShader(ringShader);
            canvas.drawCircle(cx, cy, radius * 1.02f, ringPaint);
        }

        if (t >= TOTAL) {
            finish();
        } else {
            invalidate();
        }
    }

    private void drawItemMesh(Canvas canvas, Item item) {
        // drawBitmapMesh: مختصات مبدا (src) از خود bitmap حساب می‌شود، مقصد همان dst است.
        canvas.drawBitmapMesh(item.bmp, COLS, ROWS, item.dst, 0, null, 0, bmpPaint);
    }

    private void finish() {
        finished = true;
        for (Item item : items) {
            if (item.bmp != null) {
                item.bmp.recycle();
            }
        }
        items.clear();
        if (listView != null) {
            listView.setAlpha(1f);
        }
        final Runnable done = onFinished;
        post(() -> {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
            if (done != null) {
                done.run();
            }
        });
    }
}
