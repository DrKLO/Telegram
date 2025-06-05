package org.telegram.ui.Components.Forum;


import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ForumBubbleDrawable extends Drawable {


    SvgHelper.SvgDrawable svgDrawable;
    LinearGradient gradient;
    Matrix gradientMatrix = new Matrix();
    ArrayList<View> parents = new ArrayList<>();
    int colorIndex;

    private final Paint strokePaint;
    private final Paint topPaint;
    private static SvgHelper.SvgDrawable mainDrawable;

    private int currentColors[];

    public static final int[] serverSupportedColor = new int[]{
            0x6FB9F0,
            0xFFD67E,
            0xCB86DB,
            0x8EEE98,
            0xFF93B2,
            0xFB6F5F,
    };

    static final SparseArray<int[]> colorsMap = new SparseArray<>();

    static {
        colorsMap.put(0x6FB9F0, new int[]{0xff015EC1, 0xff4BB7FF}); //blue
        colorsMap.put(0xFFD67E, new int[]{0xffEA5800, 0xffFFDB5C}); //yellow
        colorsMap.put(0xCB86DB, new int[]{0xffA438BB, 0xffE57AFF}); //violet
        colorsMap.put(0x8EEE98, new int[]{0xff11B411, 0xff97E334}); //green
        colorsMap.put(0xFF93B2, new int[]{0xffE4215A, 0xffFF7999}); //rose
        colorsMap.put(0xFB6F5F, new int[]{0xffC61505, 0xffFF714C}); //orange
    }


    public ForumBubbleDrawable(int color) {
        if (mainDrawable == null) {
            mainDrawable = SvgHelper.getDrawable(R.raw.topic_bubble, Color.WHITE);
        }
        svgDrawable = mainDrawable.clone();
        svgDrawable.copyCommandFromPosition(0);
        topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        strokePaint.setStyle(Paint.Style.STROKE);
        svgDrawable.setPaint(topPaint, 1);
        svgDrawable.setPaint(strokePaint, 2);

        setColor(color);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        gradientMatrix.reset();
        gradientMatrix.setScale(1f, getBounds().height() / 100f);
        gradient.setLocalMatrix(gradientMatrix);
        svgDrawable.setBounds(getBounds());
        svgDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        svgDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    public int colorDistance(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b));
    }

    public int moveNexColor() {
        colorIndex++;
        if (colorIndex > serverSupportedColor.length - 1) {
            colorIndex = 0;
        }
        int[] fromColors = currentColors;
        color = serverSupportedColor[colorIndex];
        currentColors = colorsMap.get(serverSupportedColor[colorIndex]);
        if (Theme.isCurrentThemeDark()) {
            currentColors = new int[]{
                    ColorUtils.blendARGB(currentColors[0], Color.WHITE, 0.2f), ColorUtils.blendARGB(currentColors[1], Color.WHITE, 0.2f)
            };
        }


        invalidateSelf();
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
        valueAnimator.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            gradient = new LinearGradient(0, 100, 0, 0, new int[]{ColorUtils.blendARGB(fromColors[0], currentColors[0], v), ColorUtils.blendARGB(fromColors[1], currentColors[1], v)}, null, Shader.TileMode.CLAMP);
            gradient.setLocalMatrix(gradientMatrix);
            paint.setShader(gradient);
            svgDrawable.setPaint(paint, 0);

            topPaint.setColor(ColorUtils.blendARGB(ColorUtils.blendARGB(fromColors[1], currentColors[1], v), Color.WHITE, 0.1f));
            strokePaint.setColor(ColorUtils.blendARGB(ColorUtils.blendARGB(fromColors[0], currentColors[0], v), Color.BLACK, 0.1f));
            invalidateSelf();
        });
        valueAnimator.setDuration(200);
        valueAnimator.start();

        return serverSupportedColor[colorIndex];
    }

    public void addParent(View view) {
        parents.add(view);
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
        for (int i = 0; i < parents.size(); i++) {
            parents.get(i).invalidate();
        }
    }

    int color = -1;

    public void setColor(int color) {
        if (this.color == color && this.color == -1) {
            return;
        }
        this.color = color;
        int colorDistance = colorDistance(serverSupportedColor[0], color);
        colorIndex = 0;
        for (int i = 0; i < serverSupportedColor.length; i++) {
            int distanceLocal = colorDistance(serverSupportedColor[i], color);
            if (distanceLocal < colorDistance) {
                colorDistance = distanceLocal;
                colorIndex = i;
            }
        }
        int[] colors = colorsMap.get(serverSupportedColor[colorIndex]);
        if (Theme.isCurrentThemeDark()) {
            colors = new int[]{
                    ColorUtils.blendARGB(colors[0], Color.WHITE, 0.2f), ColorUtils.blendARGB(colors[1], Color.WHITE, 0.2f)
            };
        }
        currentColors = colors;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradient = new LinearGradient(0, 100, 0, 0, colors, null, Shader.TileMode.CLAMP);
        gradient.setLocalMatrix(gradientMatrix);
        paint.setShader(gradient);
        svgDrawable.setPaint(paint, 0);


        topPaint.setColor(ColorUtils.blendARGB(colors[1], Color.WHITE, 0.1f));
        strokePaint.setColor(ColorUtils.blendARGB(colors[0], Color.BLACK, 0.1f));
    }
}
