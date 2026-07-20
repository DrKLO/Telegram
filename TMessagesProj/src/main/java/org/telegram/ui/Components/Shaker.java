package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

public class Shaker {

    private final Runnable invalidate;
    private final long start = System.currentTimeMillis();

    private final float r, sx, sy;

    public Shaker() {
        this((Runnable) null);
    }
    public Shaker(View view) {
        this(view::invalidate);
    }
    public Shaker(Runnable invalidate) {
        this.invalidate = invalidate;
        r =  lerp(5f, 9f, Utilities.clamp01(Utilities.fastRandom.nextFloat()));
        sx = lerp(2.5f, 5f, Utilities.clamp01(Utilities.fastRandom.nextFloat()));
        sy = lerp(2.5f, 5.2f, Utilities.clamp01(Utilities.fastRandom.nextFloat()));
    }

    public void concat(Canvas canvas, float alpha) {
        concat(canvas, alpha, 0, 0);
    }

    public void concat(Canvas canvas, float alpha, float cx, float cy) {
        final float t = (System.currentTimeMillis() - start) / 1000f;

        canvas.translate(cx, cy);
        canvas.rotate(
            (float) Math.sin(t * r * Math.PI) * 1 * alpha
        );
        canvas.translate(
            (float) Math.cos(t * sx * Math.PI) * dp(.5f) * alpha,
            (float) Math.sin(t * sy * Math.PI) * dp(.5f) * alpha
        );
        canvas.translate(-cx, -cy);

        if (alpha > 0 && invalidate != null) {
            invalidate.run();
        }
    }

}
