package org.telegram.ui.Components.blur3.render;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

@RequiresApi(api = 33)
public class SurfaceTouchLighting {

    private final RuntimeShader shader;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public SurfaceTouchLighting() {
        shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.glass_surface_touch_shader));
        basePaint.setBlendMode(BlendMode.PLUS);
        radialPaint.setBlendMode(BlendMode.PLUS);
        radialPaint.setShader(shader);
    }

    public void render(Canvas canvas, RectF bounds, float positionX, float positionY, float progress) {
        if (bounds.isEmpty() || progress <= 0f) {
            return;
        }

        path.rewind();
        path.addRoundRect(bounds, bounds.height() * 0.5f, bounds.height() * 0.5f, Path.Direction.CW);
        path.close();

        basePaint.setColor(Color.WHITE);
        basePaint.setAlpha(Math.round(255f * 0.03f * progress));
        shader.setFloatUniform("radius", bounds.height() * 1.5f);
        shader.setFloatUniform(
            "position",
            Math.max(bounds.left, Math.min(bounds.right, positionX)),
            Math.max(bounds.top, Math.min(bounds.bottom, positionY))
        );
        radialPaint.setAlpha(Math.round(255f * 0.1125f * progress));

        canvas.save();
        canvas.clipPath(path);
        canvas.drawRect(bounds, basePaint);
        canvas.drawRect(bounds, radialPaint);
        canvas.restore();
    }
}
