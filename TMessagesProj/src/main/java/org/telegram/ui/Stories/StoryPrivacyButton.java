package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

public class StoryPrivacyButton extends View {

    private int topColor, bottomColor;
    private final Matrix gradientMatrix = new Matrix();
    private final Paint[] backgroundPaint = new Paint[2];
    private final AnimatedFloat crossfadeT = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);

    public boolean draw;
    private int iconResId;
    private final Drawable[] icon = new Drawable[2];
    private final float[] iconSize = new float[2];

    private boolean drawArrow;
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();

    private final ButtonBounce bounce = new ButtonBounce(this, .6f);

    public StoryPrivacyButton(Context context) {
        super(context);
        backgroundPaint[0] = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint[1] = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setColor(Color.WHITE);
    }

    public boolean set(boolean mine, TLRPC.StoryItem storyItem, boolean animated) {
        drawArrow = mine;
        draw = true;
        if (storyItem == null) {
            draw = false;
        } else if (storyItem.close_friends) {
            setIcon(R.drawable.msg_stories_closefriends, 15);
            setupGradient(0xFF88D93A, 0xFF2DB63B);
            crossfadeT.set(animated, true);
        } else if (storyItem.contacts) {
            setIcon(R.drawable.msg_folders_private, 17.33f);
            setupGradient(0xFFC468F2, 0xFF965CFA);
            crossfadeT.set(animated, true);
        } else if (storyItem.selected_contacts) {
            setIcon(R.drawable.msg_folders_groups, 17.33f);
            setupGradient(0xFFFFB743, 0xFFF68E34);
            crossfadeT.set(animated, true);
        } else if (mine) {
            setIcon(R.drawable.msg_folders_channels, 17.33f);
            setupGradient(0xFF16A5F2, 0xFF1180F7);
            crossfadeT.set(animated, true);
        } else {
            draw = false;
        }
        setVisibility(draw ? View.VISIBLE : View.GONE);
        invalidate();
        return draw;
    }

    public boolean set(boolean mine, StoriesController.UploadingStory uploadingStory, boolean animated) {
        drawArrow = mine;
        draw = true;
        if (uploadingStory == null || uploadingStory.entry.privacy == null) {
            draw = false;
        } else if (uploadingStory.entry.privacy.type == StoryPrivacyBottomSheet.TYPE_CLOSE_FRIENDS) {
            setIcon(R.drawable.msg_stories_closefriends, 15);
            setupGradient(0xFF88D93A, 0xFF2DB63B);
            crossfadeT.set(animated, !animated);
        } else if (uploadingStory.entry.privacy.type == StoryPrivacyBottomSheet.TYPE_CONTACTS) {
            setIcon(R.drawable.msg_folders_private, 17.33f);
            setupGradient(0xFFC468F2, 0xFF965CFA);
            crossfadeT.set(animated, !animated);
        } else if (uploadingStory.entry.privacy.type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS) {
            setIcon(R.drawable.msg_folders_groups, 17.33f);
            setupGradient(0xFFFFB743, 0xFFF68E34);
            crossfadeT.set(animated, !animated);
        } else if (mine) {
            setIcon(R.drawable.msg_folders_channels, 17.33f);
            setupGradient(0xFF16A5F2, 0xFF1180F7);
            crossfadeT.set(animated, !animated);
        } else {
            draw = false;
        }
        setVisibility(draw ? View.VISIBLE : View.GONE);
        invalidate();
        return draw;
    }

    private void setIcon(int resId, float sz) {
        icon[1] = icon[0];
        iconSize[1] = iconSize[0];
        if (icon[0] == null || resId != iconResId) {
            icon[0] = getContext().getResources().getDrawable(iconResId = resId).mutate();
            icon[0].setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            iconSize[0] = dpf2(sz);
            invalidate();
        }
    }

    private void setupGradient(int top, int bottom) {
        backgroundPaint[1].setShader(backgroundPaint[0].getShader());
        if (topColor != top || bottomColor != bottom) {
            LinearGradient gradient = new LinearGradient(0, 0, 0, dp(23), new int[] { topColor = top, bottomColor = bottom }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
            gradientMatrix.reset();
            gradientMatrix.postTranslate(0, dp(8));
            gradient.setLocalMatrix(gradientMatrix);
            backgroundPaint[0].setShader(gradient);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!draw) {
            return;
        }

        final float tx = drawArrow ? 0 : dpf2(7);
        final float w = drawArrow ? dpf2(43) : dpf2(23.66f);
        final float h = dpf2(23.66f);
        AndroidUtilities.rectTmp.set(
            tx + (getWidth() - w) / 2f,
            (getHeight() - h) / 2f,
            tx + (getWidth() + w) / 2f,
            (getHeight() + h) / 2f
        );

        final float scale = bounce.getScale(0.075f);
        canvas.save();
        canvas.scale(scale, scale, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());

        final float crossfade = crossfadeT.set(0);
        if (crossfade > 0) {
            backgroundPaint[1].setAlpha(0xFF);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), backgroundPaint[1]);
        }
        if (crossfade < 1) {
            backgroundPaint[0].setAlpha((int) (0xFF * (1f - crossfade)));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), backgroundPaint[0]);
        }

        final float iconScale = 0.5f + Math.abs(crossfade - 0.5f);
        if (icon[1] != null && crossfade > .5f) {
            float cx = drawArrow ? AndroidUtilities.rectTmp.left + dpf2(14.66f) : AndroidUtilities.rectTmp.centerX();
            icon[1].setBounds(
                (int) (cx - iconSize[1] / 2 * iconScale),
                (int) (AndroidUtilities.rectTmp.centerY() - iconSize[1] / 2f * iconScale),
                (int) (cx + iconSize[1] / 2 * iconScale),
                (int) (AndroidUtilities.rectTmp.centerY() + iconSize[1] / 2f * iconScale)
            );
            icon[1].draw(canvas);
        }
        if (icon[0] != null && crossfade <= .5f) {
            float cx = drawArrow ? AndroidUtilities.rectTmp.left + dpf2(14.66f) : AndroidUtilities.rectTmp.centerX();
            icon[0].setBounds(
                (int) (cx - iconSize[0] / 2 * iconScale),
                (int) (AndroidUtilities.rectTmp.centerY() - iconSize[0] / 2f * iconScale),
                (int) (cx + iconSize[0] / 2 * iconScale),
                (int) (AndroidUtilities.rectTmp.centerY() + iconSize[0] / 2f * iconScale)
            );
            icon[0].draw(canvas);
        }
        if (drawArrow) {
            arrowPath.rewind();
            arrowPath.moveTo(AndroidUtilities.rectTmp.right - dpf2(15.66f), AndroidUtilities.rectTmp.centerY() - dpf2(1.33f));
            arrowPath.lineTo(AndroidUtilities.rectTmp.right - dpf2(12), AndroidUtilities.rectTmp.centerY() + dpf2(2.33f));
            arrowPath.lineTo(AndroidUtilities.rectTmp.right - dpf2(8.16f), AndroidUtilities.rectTmp.centerY() - dpf2(1.33f));
            arrowPaint.setStrokeWidth(dpf2(1.33f));
            canvas.drawPath(arrowPath, arrowPaint);
        }

        canvas.restore();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        bounce.setPressed(pressed);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
    }
    
    public float getCenterX() {
        return getX() + getWidth() / 2f + (drawArrow ? 0 : dp(14));
    }
}
