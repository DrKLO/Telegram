package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Region;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class OutlineTextContainerView extends FrameLayout {
    private final static int PADDING_LEFT = 14, PADDING_TEXT = 4;

    private final static float SPRING_MULTIPLIER = 100;
    private final static SimpleFloatPropertyCompat<OutlineTextContainerView> SELECTION_PROGRESS_PROPERTY =
            new SimpleFloatPropertyCompat<OutlineTextContainerView>("selectionProgress", obj -> obj.selectionProgress, (obj, value) -> {
                obj.selectionProgress = value;
                if (!obj.forceUseCenter) {
                    obj.outlinePaint.setStrokeWidth(obj.strokeWidthRegular + (obj.strokeWidthSelected - obj.strokeWidthRegular) * obj.selectionProgress);
                    obj.updateColor();
                }
                obj.invalidate();
            }).setMultiplier(SPRING_MULTIPLIER);

    private final static SimpleFloatPropertyCompat<OutlineTextContainerView> ERROR_PROGRESS_PROPERTY =
            new SimpleFloatPropertyCompat<OutlineTextContainerView>("errorProgress", obj -> obj.errorProgress, (obj, value) -> {
                obj.errorProgress = value;
                obj.updateColor();
            }).setMultiplier(SPRING_MULTIPLIER);

    private RectF rect = new RectF();
    private String mText = "";
    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private SpringAnimation selectionSpring = new SpringAnimation(this, SELECTION_PROGRESS_PROPERTY);
    private float selectionProgress;

    private SpringAnimation errorSpring = new SpringAnimation(this, ERROR_PROGRESS_PROPERTY);
    private float errorProgress;

    private float strokeWidthRegular = Math.max(2, AndroidUtilities.dp(0.5f));
    private float strokeWidthSelected = AndroidUtilities.dp(1.5f);

    private EditText attachedEditText;
    private boolean forceUseCenter;

    private final Theme.ResourcesProvider resourcesProvider;

    public OutlineTextContainerView(Context context) {
        this(context, null);
    }

    public OutlineTextContainerView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);
        textPaint.setTextSize(AndroidUtilities.dp(16));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(strokeWidthRegular);
        updateColor();

        setPadding(0, AndroidUtilities.dp(6), 0, 0);
    }

    public void setForceUseCenter(boolean forceUseCenter) {
        this.forceUseCenter = forceUseCenter;
        invalidate();
    }

    public EditText getAttachedEditText() {
        return attachedEditText;
    }

    public void attachEditText(EditText attachedEditText) {
        this.attachedEditText = attachedEditText;
        invalidate();
    }

    public void setText(@NonNull String text) {
        this.mText = text;
        invalidate();
    }

    private void setColor(int color) {
        outlinePaint.setColor(color);
        invalidate();
    }

    public void updateColor() {
        int textSelectionColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider), forceUseCenter ? 0f : selectionProgress);
        textPaint.setColor(ColorUtils.blendARGB(textSelectionColor, Theme.getColor(Theme.key_text_RedBold, resourcesProvider), errorProgress));
        int selectionColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), forceUseCenter ? 0f : selectionProgress);
        setColor(ColorUtils.blendARGB(selectionColor, Theme.getColor(Theme.key_text_RedBold, resourcesProvider), errorProgress));
    }

    public void animateSelection(float newValue) {
        animateSelection(newValue, true);
    }

    public void animateSelection(float newValue, boolean animate) {
        if (!animate) {
            selectionProgress = newValue;
            if (!forceUseCenter) {
                outlinePaint.setStrokeWidth(strokeWidthRegular + (strokeWidthSelected - strokeWidthRegular) * selectionProgress);
            }
            updateColor();
            return;
        }
        animateSpring(selectionSpring, newValue);
    }

    public void animateError(float newValue) {
        animateSpring(errorSpring, newValue);
    }

    private void animateSpring(SpringAnimation spring, float newValue) {
        newValue *= SPRING_MULTIPLIER;
        if (spring.getSpring() != null && newValue == spring.getSpring()
                .getFinalPosition()) return;

        spring.cancel();
        spring.setSpring(new SpringForce(newValue)
                .setStiffness(500f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setFinalPosition(newValue))
                .start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float textOffset = textPaint.getTextSize() / 2f - AndroidUtilities.dp(1.75f);
        float topY = getPaddingTop() + textOffset;
        float centerY = getHeight() / 2f + textPaint.getTextSize() / 2f;
        boolean useCenter = attachedEditText != null && attachedEditText.length() == 0 && TextUtils.isEmpty(attachedEditText.getHint()) || forceUseCenter;
        float textY = useCenter ? topY + (centerY - topY) * (1f - selectionProgress) : topY;
        float stroke = outlinePaint.getStrokeWidth();

        float scaleX = useCenter ? 0.75f + 0.25f * (1f - selectionProgress) : 0.75f;
        float textWidth = textPaint.measureText(mText) * scaleX;

        canvas.save();
        rect.set(getPaddingLeft() + AndroidUtilities.dp(PADDING_LEFT - PADDING_TEXT), getPaddingTop(), getWidth() - AndroidUtilities.dp(PADDING_LEFT + PADDING_TEXT) - getPaddingRight(), getPaddingTop() + stroke * 2);
        canvas.clipRect(rect, Region.Op.DIFFERENCE);
        rect.set(getPaddingLeft() + stroke, getPaddingTop() + stroke, getWidth() - stroke - getPaddingRight(), getHeight() - stroke - getPaddingBottom());
        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), outlinePaint);
        canvas.restore();

        float left = getPaddingLeft() + AndroidUtilities.dp(PADDING_LEFT - PADDING_TEXT), lineY = getPaddingTop() + stroke,
                right = getWidth() - stroke - getPaddingRight() - AndroidUtilities.dp(6);

        float activeLeft = left + textWidth + AndroidUtilities.dp(PADDING_LEFT - PADDING_TEXT);
        float fromLeft = left + textWidth / 2f;
        canvas.drawLine(fromLeft + (activeLeft - fromLeft) * (useCenter ? selectionProgress : 1f), lineY, right, lineY, outlinePaint);

        float fromRight = left + textWidth / 2f + AndroidUtilities.dp(PADDING_TEXT);
        canvas.drawLine(left, lineY, fromRight + (left - fromRight) * (useCenter ? selectionProgress : 1f), lineY, outlinePaint);

        canvas.save();
        canvas.scale(scaleX, scaleX, getPaddingLeft() + AndroidUtilities.dp(PADDING_LEFT + PADDING_TEXT), textY);
        canvas.drawText(mText, getPaddingLeft() + AndroidUtilities.dp(PADDING_LEFT), textY, textPaint);
        canvas.restore();
    }
}
