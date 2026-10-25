package org.telegram.ui.QrShare;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;

/**
 * The "Dark out" glass rim (Glass Lab) for views outside QrShareView (emoji islands): white crescents inside with the
 * white blend (Plus), a dark ring outside drawn as black at alpha a(θ) (== Multiply by grey 1 − a). Same RimTune values,
 * so the glass looks the same everywhere. Round rects only.
 */
final class RimPainter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SweepGradient[] sweeps = new SweepGradient[RimTune.NG];
    private int builtVersion = -1;
    private final Matrix m = new Matrix();
    private final Path b = new Path();
    private final RectF r = new RectF(), r2 = new RectF();

    /** The light axis rotation (°) and top/bottom balance, shared with the QR screen's tilt. */
    static float tiltRot, tiltBal;

    RimPainter() {
        stroke.setStyle(Paint.Style.STROKE);
    }

    private void ensure() {
        if (builtVersion == RimTune.version) return;
        builtVersion = RimTune.version;
        final int n = 73;
        for (int k = 0; k < RimTune.NG; k++) {
            final float[] gk = RimTune.g[k];
            final int[] cols = new int[n];
            final float[] pos = new float[n];
            for (int i = 0; i < n; i++) {
                final double th = Math.toRadians(i * 5);
                final float side = (float) Math.pow(Math.abs(Math.cos(th)), gk[RimTune.D_EXP]);
                final float sn = (float) Math.sin(th);
                final float tb = (sn < 0 ? gk[RimTune.D_TOP] : gk[RimTune.D_BOT]) / 100f;
                final float al = Math.max(0f, Math.min(1f, tb + (gk[RimTune.D_A] / 100f - tb) * side));
                cols[i] = ((int) (255 * al + 0.5f)) << 24; // black at alpha a
                pos[i] = i / (float) (n - 1);
            }
            sweeps[k] = new SweepGradient(0, 0, cols, pos);
        }
    }

    /** Draws the rim of the round rect (rect, radius) in group grp, faded by alpha. */
    void draw(Canvas c, RectF rect, float radius, int grp, float alpha) {
        draw(c, rect, radius, grp, alpha, false);
    }

    /** layered = drawn into an offscreen layer without the real pixels beneath: no Plus (it would turn grey). */
    void draw(Canvas c, RectF rect, float radius, int grp, float alpha, boolean layered) {
        if (alpha <= 0.004f) return;
        if (QrGlass.drawRim(c, rect, radius, grp, alpha)) return; // baked: the same outline as the QR screen's
        ensure();
        final float[] gk = RimTune.g[grp];
        final float rot = tiltRot, bal = tiltBal;
        final float inset = dpf2(gk[RimTune.INSET]);
        r.set(rect);
        r.inset(inset, inset);
        radius = Math.max(0f, radius - inset);
        m.setRotate(rot);
        m.postScale(r.width() / Math.max(1f, r.height()), 1f);
        m.postTranslate(r.centerX(), r.centerY());
        final float dw = dpf2(gk[RimTune.D_W]);
        if (dw > 0.05f) {
            sweeps[grp].setLocalMatrix(m);
            stroke.setShader(sweeps[grp]);
            stroke.setColor(Color.WHITE);
            stroke.setAlpha((int) (255 * alpha));
            stroke.setStrokeWidth(dw);
            r2.set(r);
            r2.inset(-dw / 2f, -dw / 2f);
            c.drawRoundRect(r2, radius + dw / 2f, radius + dw / 2f, stroke);
            stroke.setShader(null);
        }
        final double phi = Math.toRadians(rot);
        final float sx = (float) -Math.sin(phi), sy = (float) Math.cos(phi);
        final float tTop = dpf2(gk[RimTune.T_TOP]), tBot = dpf2(gk[RimTune.T_BOT]);
        final float aTop = Math.min(1f, gk[RimTune.A_TOP] / 100f * (1f + bal)), aBot = Math.min(1f, gk[RimTune.A_BOT] / 100f * (1f - bal));
        final boolean plus = !layered && QrRender.hasNodes();
        final float rr = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        for (int pass = 0; pass < 2; pass++) {
            final float s = pass == 0 ? aTop : aBot;
            final float off = pass == 0 ? tTop : -tBot;
            if (s <= 0.004f || Math.abs(off) < 0.1f) continue;
            // the crescent: the shape with its shifted copy clipped out (Path.op gave wrong shapes on some devices)
            r2.set(r);
            r2.offset(sx * off, sy * off);
            b.rewind();
            b.addRoundRect(r2, rr, rr, Path.Direction.CW);
            c.save();
            QrRender.clipOut(c, b);
            if (plus) {
                final int v = Math.round(255 * Math.min(1f, s));
                fill.setColor(0xFF000000 | v << 16 | v << 8 | v);
                fill.setAlpha((int) (255 * alpha));
                QrRender.setBlendMode(fill, QrRender.BLEND_PLUS);
            } else {
                fill.setColor(Color.WHITE);
                fill.setAlpha((int) (255 * s * alpha));
            }
            c.drawRoundRect(r, rr, rr, fill);
            if (plus) QrRender.setBlendMode(fill, QrRender.BLEND_NONE);
            c.restore();
        }
    }
}
