package org.telegram.ui.Components.blur3.render;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.BlendMode;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

@RequiresApi(api = 33)
public class SurfaceEdgeLighting {

    private final RuntimeShader shader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public SurfaceEdgeLighting() {
        shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.glass_surface_edge_shader));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((float) Math.ceil(dpf2(0.5f)) * 2f);
        paint.setMaskFilter(new BlurMaskFilter(dpf2(0.25f), BlurMaskFilter.Blur.NORMAL));
        paint.setBlendMode(BlendMode.PLUS);
        paint.setShader(shader);
        paint.setAlpha(128);
    }

    public void render(
        Canvas canvas,
        float width,
        float height,
        float radiusLeftTop,
        float radiusRightTop,
        float radiusRightBottom,
        float radiusLeftBottom,
        float alpha
    ) {
        if (width <= 0f || height <= 0f || alpha <= 0f) {
            return;
        }

        final float maxRadius = Math.min(width, height) * 0.5f;
        radiusLeftTop = Math.min(radiusLeftTop, maxRadius);
        radiusRightTop = Math.min(radiusRightTop, maxRadius);
        radiusRightBottom = Math.min(radiusRightBottom, maxRadius);
        radiusLeftBottom = Math.min(radiusLeftBottom, maxRadius);

        shader.setFloatUniform("size", width, height);
        shader.setFloatUniform(
            "cornerRadii",
            radiusLeftTop,
            radiusRightTop,
            radiusRightBottom,
            radiusLeftBottom
        );
        shader.setFloatUniform("angle", (float) (Math.PI * 0.25));
        shader.setFloatUniform("falloff", 1f);
        paint.setAlpha(Math.round(128f * alpha));

        path.rewind();
        path.addRoundRect(
            0f,
            0f,
            width,
            height,
            new float[] {
                radiusLeftTop, radiusLeftTop,
                radiusRightTop, radiusRightTop,
                radiusRightBottom, radiusRightBottom,
                radiusLeftBottom, radiusLeftBottom
            },
            Path.Direction.CW
        );
        path.close();

        canvas.save();
        canvas.clipPath(path);
        canvas.drawPath(path, paint);
        canvas.restore();
    }
}
