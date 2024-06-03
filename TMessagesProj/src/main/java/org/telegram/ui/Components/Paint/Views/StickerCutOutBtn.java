package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

@SuppressLint("ViewConstructor")
public class StickerCutOutBtn extends ButtonWithCounterView {

    private static final int STATE_CUT_OUT = 0;
    private static final int STATE_UNDO_CAT = 1;
    private static final int STATE_CANCEL = 2;
    private static final int STATE_ERASE = 3;
    private static final int STATE_RESTORE = 4;
    private static final int STATE_UNDO = 5;
    private static final int STATE_OUTLINE = 6;

    protected final BlurringShader.StoryBlurDrawer blurDrawer;
    protected final RectF bounds = new RectF();
    private int state;
    private final StickerMakerView stickerMakerView;

    private final Theme.ResourcesProvider resourcesProvider;

    public StickerCutOutBtn(StickerMakerView stickerMakerView, Context context, Theme.ResourcesProvider resourcesProvider, BlurringShader.BlurManager blurManager) {
        super(context, false, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.stickerMakerView = stickerMakerView;
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_BACKGROUND, true);
        setWillNotDraw(false);
        setTextColor(Color.WHITE);
        setFlickeringLoading(true);
        text.setTypeface(AndroidUtilities.bold());
        disableRippleView();
        setForeground(Theme.createRadSelectorDrawable(Theme.multAlpha(Color.WHITE, .08f), 8, 8));
        setPadding(dp(24), 0, dp(24), 0);
    }

    public int rad = 8;
    public void setRad(int rad) {
        this.rad = rad;
        setForeground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), rad, rad));
    }

    @Override
    public void setAlpha(float alpha) {
        if (!stickerMakerView.hasSegmentedBitmap()) {
            alpha = 0f;
        }
        super.setAlpha(alpha);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (wrapContentDynamic) {
            float w = text.getCurrentWidth() + getPaddingLeft() + getPaddingRight();
            bounds.set((getMeasuredWidth() - w) / 2f, 0, (getMeasuredWidth() + w) / 2f, getMeasuredHeight());
        } else {
            bounds.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
        super.onDraw(canvas);
    }

    @Override
    public void setVisibility(int visibility) {
        if (Build.VERSION.SDK_INT < 24) {
            super.setVisibility(View.GONE);
        } else {
            super.setVisibility(visibility);
        }
    }

    public void setCutOutState(boolean animated) {
        state = STATE_CUT_OUT;
        SpannableStringBuilder cutOutBtnText = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.media_magic_cut);
        coloredImageSpan.setSize(dp(22));
        coloredImageSpan.setTranslateX(dp(1));
        coloredImageSpan.setTranslateY(dp(2));
        coloredImageSpan.spaceScaleX = 1.2f;
        cutOutBtnText.setSpan(coloredImageSpan, 0, 1, 0);
        cutOutBtnText.append(" ").append(LocaleController.getString(R.string.SegmentationCutObject));
        setText(cutOutBtnText, animated);
    }

    public void setUndoCutState(boolean animated) {
        state = STATE_UNDO_CAT;
    }

    public void setUndoState(boolean animated) {
        state = STATE_UNDO;
        SpannableStringBuilder cutOutBtnText = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.photo_undo2);
        coloredImageSpan.setSize(dp(20));
        coloredImageSpan.setTranslateX(dp(-3));
        cutOutBtnText.setSpan(coloredImageSpan, 0, 1, 0);
        cutOutBtnText.append(" ").append(LocaleController.getString(R.string.SegmentationUndo));
        setText(cutOutBtnText, animated);
    }

    public void setOutlineState(boolean animated) {
        state = STATE_OUTLINE;
        SpannableStringBuilder cutOutBtnText = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.media_sticker_stroke);
        coloredImageSpan.setSize(dp(20));
        coloredImageSpan.setTranslateX(dp(-3));
        cutOutBtnText.setSpan(coloredImageSpan, 0, 1, 0);
        cutOutBtnText.append(" ").append(LocaleController.getString(R.string.SegmentationOutline));
        setText(cutOutBtnText, animated);
    }

    public void setRestoreState(boolean animated) {
        state = STATE_RESTORE;
        SpannableStringBuilder cutOutBtnText = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.media_button_restore);
        coloredImageSpan.setSize(dp(20));
        coloredImageSpan.setTranslateX(dp(-3));
        cutOutBtnText.setSpan(coloredImageSpan, 0, 1, 0);
        cutOutBtnText.append(" ").append(LocaleController.getString(R.string.SegmentationRestore));
        setText(cutOutBtnText, animated);
    }

    public void setEraseState(boolean animated) {
        state = STATE_ERASE;
        SpannableStringBuilder cutOutBtnText = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.media_button_erase);
        coloredImageSpan.setSize(dp(20));
        coloredImageSpan.setTranslateX(dp(-3));
        cutOutBtnText.setSpan(coloredImageSpan, 0, 1, 0);
        cutOutBtnText.append(" ").append(LocaleController.getString(R.string.SegmentationErase));
        setText(cutOutBtnText, animated);
    }

    public void setCancelState(boolean animated) {
        state = STATE_CANCEL;
        setText(LocaleController.getString(R.string.Cancel), animated);
    }

    public boolean isCutOutState() {
        return state == STATE_CUT_OUT;
    }

    public boolean isCancelState() {
        return state == STATE_CANCEL;
    }

    public boolean isUndoCutState() {
        return state == STATE_UNDO_CAT;
    }

    public void clean() {
        setCutOutState(false);
    }

    public void invalidateBlur() {
        invalidate();
    }

    private boolean wrapContent;
    public void wrapContent() {
        wrapContent = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (wrapContent) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(getPaddingLeft() + (int) text.getCurrentWidth() + getPaddingRight(), MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
