package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.utils.glass.source.GlassSourceColor;
import java.util.HashSet;

public class ChatActivityFadeView extends View implements Theme.Colorable {
    private BlurredBackgroundWithFadeDrawable fadeDrawableTop;
    private BlurredBackgroundWithFadeDrawable fadeDrawableBottom;
    private int fadeZoneTop, fadeZoneBottom;

    public ChatActivityFadeView(Context context) {
        super(context);
    }


    private GlassSourceColor sourceColor;
    private BlurredBackgroundDrawableViewFactory factory;
    private int colorKey;

    public void setupColorKey(int colorKey) {
        this.colorKey = colorKey;
        if (sourceColor == null) {
            sourceColor = new GlassSourceColor();
            sourceColor.setColor(Theme.getColor(colorKey));
            factory = new BlurredBackgroundDrawableViewFactory(sourceColor);
            setup(factory);
        }
    }


    public void setup(BlurredBackgroundDrawableViewFactory factory) {
        setup(factory, null);
    }

    public void setup(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        fadeDrawableTop = new BlurredBackgroundWithFadeDrawable(factory.create(this).setColorProvider(colorProvider));
        fadeDrawableTop.setFadeHeight(-dp(30), true);

        fadeDrawableBottom = new BlurredBackgroundWithFadeDrawable(factory.create(this).setColorProvider(colorProvider));
        fadeDrawableBottom.setFadeHeight(dp(30), true);
    }

    public void setFadeHeightTop(int height, boolean opacity) {
        fadeDrawableTop.setFadeHeight(-height, opacity);
    }

    public void setFadeHeightTop(int height) {
        fadeDrawableTop.setFadeHeight(-height, true);
    }

    public void setFadeHeightBottom(int height) {
        fadeDrawableBottom.setFadeHeight(height, true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkBounds();
    }

    public void setFadeZoneTop(int height) {
        if (fadeZoneTop != height) {
            fadeZoneTop = height;
            checkBounds();
            invalidate();
        }
    }

    public void setFadeZoneBottom(int height) {
        if (fadeZoneBottom != height) {
            fadeZoneBottom = height;
            checkBounds();
            invalidate();
        }
    }

    public void setFadeTopAlpha(int alpha) {
        if (fadeDrawableTop.getAlpha() != alpha) {
            fadeDrawableTop.setAlpha(alpha);
            invalidate();
        }
    }
    
    private void checkBounds() {
        fadeDrawableTop.setBounds(0, 0, getMeasuredWidth(), fadeZoneTop);
        fadeDrawableBottom.setBounds(0, getMeasuredHeight() - fadeZoneBottom, getMeasuredWidth(), getMeasuredHeight());
        overlayShaderTop = overlayShaderBottom = null;
    }

    // extra tint over the fades, e.g. a subtle shadow when the background is a plain color
    private int overlayColor;
    private final Paint overlayPaint = new Paint();
    private Shader overlayShaderTop, overlayShaderBottom;

    private Theme.ResourcesProvider resourcesProvider;

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public void setOverlayColor(int color) {
        if (overlayColor != color) {
            overlayColor = color;
            overlayShaderTop = overlayShaderBottom = null;
            invalidate();
        }
    }

    private Shader createOverlayShader(boolean top) {
        final int edge = overlayColor;
        final int middle = ColorUtils.setAlphaComponent(overlayColor, Color.alpha(overlayColor) * 2 / 3);
        final int none = ColorUtils.setAlphaComponent(overlayColor, 0);
        if (top) {
            return new LinearGradient(0, 0, 0, fadeZoneTop, new int[]{edge, middle, none}, new float[]{0, 0.82f, 1}, Shader.TileMode.CLAMP);
        }
        final int y = getMeasuredHeight() - fadeZoneBottom;
        return new LinearGradient(0, y, 0, getMeasuredHeight(), new int[]{none, middle, edge}, new float[]{0, 0.18f, 1}, Shader.TileMode.CLAMP);
    }

    private void drawOverlay(Canvas canvas) {
        if (Color.alpha(overlayColor) == 0) {
            return;
        }
        if (fadeZoneTop > 0 && fadeDrawableTop.getAlpha() > 0) {
            if (overlayShaderTop == null) {
                overlayShaderTop = createOverlayShader(true);
            }
            overlayPaint.setShader(overlayShaderTop);
            overlayPaint.setAlpha(fadeDrawableTop.getAlpha());
            canvas.drawRect(0, 0, getMeasuredWidth(), fadeZoneTop, overlayPaint);
        }
        if (fadeZoneBottom > 0) {
            if (overlayShaderBottom == null) {
                overlayShaderBottom = createOverlayShader(false);
            }
            overlayPaint.setShader(overlayShaderBottom);
            overlayPaint.setAlpha(0xFF);
            canvas.drawRect(0, getMeasuredHeight() - fadeZoneBottom, getMeasuredWidth(), getMeasuredHeight(), overlayPaint);
        }
    }

    public void setIgnoreFastWay(boolean ignoreFastWay) {
        fadeDrawableTop.setIgnoreFastWay(ignoreFastWay);
        fadeDrawableBottom.setIgnoreFastWay(ignoreFastWay);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (Theme.isCompactMode(resourcesProvider)) {
            drawCompactFades(canvas);
        } else {
            fadeDrawableTop.draw(canvas);
            fadeDrawableBottom.draw(canvas);
        }
        drawOverlay(canvas);
    }

    // compact mode follows the design: the background fades out from the edges, through the middle color at 56% (theme keys)
    private final Paint compactFadePaint = new Paint();
    private Shader compactShaderTop, compactShaderBottom;
    private int compactShaderMiddle;
    private int compactShaderColor, compactShaderTopHeight, compactShaderBottomHeight, compactShaderHeight;

    private void drawCompactFades(Canvas canvas) {
        final int color = Theme.getColor(Theme.key_chat_compact_fadeEdge);
        final int middle = Theme.getColor(Theme.key_chat_compact_fadeMiddle);
        final int h = getMeasuredHeight();
        if (compactShaderTop == null || compactShaderColor != color || compactShaderMiddle != middle || compactShaderTopHeight != fadeZoneTop || compactShaderBottomHeight != fadeZoneBottom || compactShaderHeight != h) {
            compactShaderColor = color;
            compactShaderMiddle = middle;
            compactShaderTopHeight = fadeZoneTop;
            compactShaderBottomHeight = fadeZoneBottom;
            compactShaderHeight = h;
            final int[] colors = new int[] { color, middle, ColorUtils.setAlphaComponent(middle, 0) };
            compactShaderTop = new LinearGradient(0, 0, 0, Math.max(1, fadeZoneTop), colors, new float[] { 0, .557f, 1 }, Shader.TileMode.CLAMP);
            compactShaderBottom = new LinearGradient(0, h, 0, h - Math.max(1, fadeZoneBottom), colors, new float[] { 0, .557f, 1 }, Shader.TileMode.CLAMP);
        }
        if (fadeZoneTop > 0 && fadeDrawableTop.getAlpha() > 0) {
            compactFadePaint.setShader(compactShaderTop);
            compactFadePaint.setAlpha(fadeDrawableTop.getAlpha());
            canvas.drawRect(0, 0, getMeasuredWidth(), fadeZoneTop, compactFadePaint);
        }
        if (fadeZoneBottom > 0) {
            compactFadePaint.setShader(compactShaderBottom);
            compactFadePaint.setAlpha(0xFF);
            canvas.drawRect(0, h - fadeZoneBottom, getMeasuredWidth(), h, compactFadePaint);
        }
    }

    @Override
    public void updateColors() {
        if (sourceColor != null && colorKey != -1) {
            sourceColor.setColor(Theme.getColor(colorKey));
            invalidate();
        }
    }
    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(colorKey);
    }
}

