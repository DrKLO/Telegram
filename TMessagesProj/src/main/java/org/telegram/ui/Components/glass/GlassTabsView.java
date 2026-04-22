package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

public class GlassTabsView extends FrameLayout {
    //private final @Nullable BlurredBackgroundSourceRenderNode lensBackgroundSourceNode;
    //private final @NonNull BlurredBackgroundSourceColor lensBackgroundSourceColor;
    //public final BlurredBackgroundDrawable lensDrawable;

    public final LinearLayout linearLayout;

    public GlassTabsView(@NonNull Context context) {
        super(context);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));


        /*lensBackgroundSourceColor = new BlurredBackgroundSourceColor();

        BlurredBackgroundDrawableViewFactory lensBackgroundFactory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lensBackgroundSourceNode = new BlurredBackgroundSourceRenderNode(lensBackgroundSourceColor);
            lensBackgroundSourceNode.setBlur(dp(4f));
            lensBackgroundFactory = new BlurredBackgroundDrawableViewFactory(lensBackgroundSourceNode);
            lensBackgroundFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        } else {
            lensBackgroundSourceNode = null;
            lensBackgroundFactory = new BlurredBackgroundDrawableViewFactory(lensBackgroundSourceColor);
        }

        lensDrawable = lensBackgroundFactory.create();
        lensDrawable.setColorProvider(new BlurredBackgroundColorProviderThemed(null, Theme.key_windowBackgroundWhite) {
            @Override
            public int getBackgroundColor() {
                return 0;
            }

            @Override
            public int getShadowColor() {
                return 0;
            }
        });*/
    }

    private int lensColorBackground;
    private int lensColorForeground;
    private float lensVisibility;

    private final Rect lensBounds = new Rect();
    private final Rect lensBoundsForeground = new Rect();
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected void setLensColor(int lensBackgroundColor, int lensForegroundColor) {
        this.lensColorBackground = lensBackgroundColor;
        this.lensColorForeground = lensForegroundColor;
        lensPaint.setColor(lensBackgroundColor);
    }

    protected void setLensBounds(int l, int t, int r, int b) {
        lensBounds.set(l, t, r, b);
        checkBounds();
    }


    protected void setLensVisibility(float visibility) {
        lensVisibility = visibility;
        //lensDrawable.setAlpha(MathUtils.clamp((int) (255 * visibility), 0, 255));
        checkBounds();
    }

    private void checkBounds() {
        int i = dp(7f * lensVisibility);
        lensBoundsForeground.set(lensBounds);
        lensBoundsForeground.inset(-i, -i);

        //lensDrawable.setBounds(lensBoundsForeground);
        //lensDrawable.setRadius(Math.min(lensBoundsForeground.width(), lensBoundsForeground.height()) / 2f);
    }

/*
    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (lensBackgroundSourceNode != null && !lensBackgroundSourceNode.inRecording()) {
                final RecordingCanvas c = lensBackgroundSourceNode.beginRecording(getMeasuredWidth(), getMeasuredHeight());
                final Drawable background = getBackground();
                if (background != null) {
                    background.draw(c);
                }

                c.drawColor(lensColorForeground);

                super.dispatchDraw(c);
                lensBackgroundSourceNode.endRecording();
            }
        }

        drawLens(canvas);
        super.dispatchDraw(canvas);
    }

    protected void drawLens(Canvas canvas) {
        if (lensDrawable.getAlpha() != 255) {
            final float r = Math.min(lensBounds.width() / 2f, lensBounds.height() / 2f);
            canvas.drawRoundRect(lensBounds.left, lensBounds.top,
                    lensBounds.right, lensBounds.bottom, r, r, lensPaint);
        }

        if (lensDrawable.getAlpha() != 0) {
            lensDrawable.draw(canvas);
        }
    }
*/
}
