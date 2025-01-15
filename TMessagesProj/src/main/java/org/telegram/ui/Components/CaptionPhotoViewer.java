package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ActionBar.Theme.RIPPLE_MASK_CIRCLE_20DP;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.CaptionContainerView;
import org.telegram.ui.Stories.recorder.HintView2;

public class CaptionPhotoViewer extends CaptionContainerView {

    private boolean addPhotoVisible;
    private final ImageView addPhotoButton;

    private boolean timerVisible;
    private final ImageView timerButton;
    private final PeriodDrawable timerDrawable;
    private ItemOptions timerPopup;

    private int timer = 0;
    private final int SHOW_ONCE = 0x7FFFFFFF;
    private final int[] values = new int[] { SHOW_ONCE, 3, 10, 30, 0 };

//    private final BlurringShader.StoryBlurDrawer hintBlur;
    private final HintView2 hint;
    private final Runnable applyCaption;

    private final RectF moveButtonBounds = new RectF();
    private Drawable moveButtonIcon;
    private final AnimatedTextView.AnimatedTextDrawable moveButtonText = new AnimatedTextView.AnimatedTextDrawable();
    private final ButtonBounce moveButtonBounce = new ButtonBounce(this);

    @Override
    protected int getEditTextStyle() {
        return EditTextEmoji.STYLE_PHOTOVIEWER;
    }

    public CaptionPhotoViewer(
        Context context,
        FrameLayout rootView,
        SizeNotifierFrameLayout sizeNotifierFrameLayout,
        FrameLayout containerView,
        Theme.ResourcesProvider resourcesProvider,
        BlurringShader.BlurManager blurManager,
        Runnable applyCaption
    ) {
        super(context, rootView, sizeNotifierFrameLayout, containerView, resourcesProvider, blurManager);
        this.applyCaption = applyCaption;

        moveButtonText.setTextSize(dp(14));
        moveButtonText.setOverrideFullWidth(AndroidUtilities.displaySize.x);
        moveButtonText.setTextColor(0xFFFFFFFF);
        if (isAtTop()) {
            moveButtonText.setText(getString(R.string.MoveCaptionDown));
            moveButtonIcon = context.getResources().getDrawable(R.drawable.menu_link_below);
        } else {
            moveButtonText.setText(getString(R.string.MoveCaptionUp));
            moveButtonIcon = context.getResources().getDrawable(R.drawable.menu_link_above);
        }

        addPhotoButton = new ImageView(context);
        addPhotoButton.setImageResource(R.drawable.filled_add_photo);
        addPhotoButton.setScaleType(ImageView.ScaleType.CENTER);
        addPhotoButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        addPhotoButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, RIPPLE_MASK_CIRCLE_20DP, dp(18)));
        setAddPhotoVisible(false, false);
        addView(addPhotoButton, LayoutHelper.createFrame(44, 44, Gravity.LEFT | (isAtTop() ? Gravity.TOP : Gravity.BOTTOM), 14, isAtTop() ? 10 : 0, 0, isAtTop() ? 0 : 10));

        timerButton = new ImageView(context);
        timerButton.setImageDrawable(timerDrawable = new PeriodDrawable());
        timerButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, RIPPLE_MASK_CIRCLE_20DP, dp(18)));
        timerButton.setScaleType(ImageView.ScaleType.CENTER);
        setTimerVisible(false, false);
        addView(timerButton, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | (isAtTop() ? Gravity.TOP : Gravity.BOTTOM), 0, isAtTop() ? 10 : 0, 11, isAtTop() ? 0 : 10));

        hint = new HintView2(context, isAtTop() ? HintView2.DIRECTION_TOP : HintView2.DIRECTION_BOTTOM);
        hint.setRounding(12);
        hint.setPadding(dp(12), dp(isAtTop() ? 8 : 0), dp(12), dp(isAtTop() ? 0 : 8));
        hint.setJoint(1, -21);
        hint.setMultilineText(true);
        addView(hint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.RIGHT | (isAtTop() ? Gravity.TOP : Gravity.BOTTOM)));

        timerButton.setOnClickListener(e -> {
            if (timerPopup != null && timerPopup.isShown()) {
                timerPopup.dismiss();
                timerPopup = null;
                return;
            }
            hint.hide();

            timerPopup = ItemOptions.makeOptions(rootView, new DarkThemeResourceProvider(), timerButton);
            timerPopup.setDimAlpha(0);
            timerPopup.addText(getString(R.string.TimerPeriodHint), 13, dp(200));
            timerPopup.addGap();
            for (int value : values) {
                String text;
                if (value == 0) {
                    text = getString(R.string.TimerPeriodDoNotDelete);
                } else if (value == SHOW_ONCE) {
                    text = getString(R.string.TimerPeriodOnce);
                } else {
                    text = LocaleController.formatPluralString("Seconds", value);
                }
                timerPopup.add(0, text, () -> changeTimer(value));
                if (this.timer == value) {
                    timerPopup.putCheck();
                }
            }
            timerPopup.show();
        });
    }

//    private final AnimatedFloat aboveAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
//
//    @Override
//    protected float forceRound() {
//        return aboveAnimated.set(isAtTop());
//    }

    private final AnimatedFloat moveButtonAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat moveButtonExpandedAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean moveButtonVisible;
    private boolean moveButtonExpanded;

    public void expandMoveButton() {
        AndroidUtilities.cancelRunOnUIThread(collapseMoveButton);
        moveButtonExpanded = MessagesController.getInstance(currentAccount).shouldShowMoveCaptionHint();
        if (moveButtonExpanded) {
            MessagesController.getInstance(currentAccount).incrementMoveCaptionHint();
            invalidate();
            AndroidUtilities.runOnUIThread(collapseMoveButton, 5000);
        }
    }

    private final Runnable collapseMoveButton = () -> {
        if (moveButtonExpanded) {
            moveButtonExpanded = false;
            invalidate();
        }
    };

    protected void openedKeyboard() {
        expandMoveButton();
    }

    @Override
    public void updateKeyboard(int keyboardHeight) {
        final boolean wasOpen = super.toKeyboardShow;
        super.updateKeyboard(keyboardHeight);
        if (!wasOpen && keyboardNotifier.keyboardVisible()) {
            openedKeyboard();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        final float moveButtonAlpha = moveButtonAnimated.set(moveButtonVisible, !showMoveButton());
        final float moveButtonExpanded = moveButtonExpandedAnimated.set(this.moveButtonExpanded);
        if (moveButtonAlpha > 0.0f) {
            float s = moveButtonBounce.getScale(.03f);
            if (isAtTop()) {
                moveButtonBounds.set(dp(10), bounds.bottom + dp(10), dp(10 + 34) + (moveButtonText.getCurrentWidth() + dp(11)) * moveButtonExpanded, bounds.bottom + dp(10 + 32));
            } else {
                moveButtonBounds.set(dp(10), bounds.top - dp(32 + 10), dp(10 + 34) + (moveButtonText.getCurrentWidth() + dp(11)) * moveButtonExpanded, bounds.top - dp(10));
            }
            if (moveButtonAlpha < 1) {
                canvas.saveLayerAlpha(moveButtonBounds, (int) (0xFF * moveButtonAlpha), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.scale(s, s, moveButtonBounds.centerX(), moveButtonBounds.centerY());
            canvas.clipRect(moveButtonBounds);
            float r = dpf2(8.33f);
            if (customBlur()) {
                drawBlur(backgroundBlur, canvas, moveButtonBounds, r, false, 0, 0, true, 1.0f);
                backgroundPaint.setAlpha((int) (lerp(0, 0x40, moveButtonAlpha)));
                canvas.drawRoundRect(moveButtonBounds, r, r, backgroundPaint);
            } else {
                Paint[] blurPaints = backgroundBlur.getPaints(moveButtonAlpha, 0, 0);
                if (blurPaints == null || blurPaints[1] == null) {
                    backgroundPaint.setAlpha(lerp(0, 0x80, moveButtonAlpha));
                    canvas.drawRoundRect(moveButtonBounds, r, r, backgroundPaint);
                } else {
                    if (blurPaints[0] != null) {
                        canvas.drawRoundRect(moveButtonBounds, r, r, blurPaints[0]);
                    }
                    if (blurPaints[1] != null) {
                        canvas.drawRoundRect(moveButtonBounds, r, r, blurPaints[1]);
                    }
                    backgroundPaint.setAlpha(lerp(0, 0x33, moveButtonAlpha));
                    canvas.drawRoundRect(moveButtonBounds, r, r, backgroundPaint);
                }
            }
            moveButtonIcon.setBounds((int) (moveButtonBounds.left + dp(9)), (int) (moveButtonBounds.centerY() - dp(9)), (int) (moveButtonBounds.left + dp(9 + 18)), (int) (moveButtonBounds.centerY() + dp(9)));
            moveButtonIcon.draw(canvas);
            moveButtonText.setBounds(moveButtonBounds.left + dp(34), moveButtonBounds.top, moveButtonBounds.right, moveButtonBounds.bottom);
            moveButtonText.setAlpha((int) (0xFF * moveButtonExpanded));
            moveButtonText.draw(canvas);
            canvas.restore();
        }
    }

    public void setOnAddPhotoClick(View.OnClickListener listener) {
        addPhotoButton.setOnClickListener(listener);
    }

    public void setAddPhotoVisible(boolean visible, boolean animated) {
        addPhotoVisible = visible;
        addPhotoButton.animate().cancel();
        if (animated) {
            addPhotoButton.setVisibility(View.VISIBLE);
            addPhotoButton.animate().alpha(visible ? 1f : 0f).translationX(visible ? 0 : dp(-8)).withEndAction(() -> {
                if (!visible) {
                    timerButton.setVisibility(View.GONE);
                }
            }).start();
        } else {
            addPhotoButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            addPhotoButton.setAlpha(visible ? 1f : 0f);
            addPhotoButton.setTranslationX(visible ? 0 : dp(-8));
        }
        updateEditTextLeft();

        MarginLayoutParams lp = (MarginLayoutParams) editText.getLayoutParams();
        lp.rightMargin = dp(12 + (addPhotoVisible && timerVisible ? 33 : 0));
        editText.setLayoutParams(lp);
    }

    @Override
    protected int getEditTextLeft() {
        return addPhotoVisible ? dp(31) : 0;
    }

    private boolean isVideo;
    public void setIsVideo(boolean isVideo) {
        this.isVideo = isVideo;
    }

    @Override
    protected void onTextChange() {
        if (applyCaption != null) {
            applyCaption.run();
        }
    }

    public void setTimerVisible(boolean visible, boolean animated) {
        timerVisible = visible;
        timerButton.animate().cancel();
        if (animated) {
            timerButton.setVisibility(View.VISIBLE);
            timerButton.animate().alpha(visible ? 1f : 0f).translationX(visible ? 0 : dp(8)).withEndAction(() -> {
                if (!visible) {
                    timerButton.setVisibility(View.GONE);
                }
            }).start();
        } else {
            timerButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            timerButton.setAlpha(visible ? 1f : 0f);
            timerButton.setTranslationX(visible ? 0 : dp(8));
        }

        MarginLayoutParams lp = (MarginLayoutParams) editText.getLayoutParams();
        lp.rightMargin = dp(12 + (addPhotoVisible && timerVisible ? 33 : 0));
        editText.setLayoutParams(lp);
    }

    public boolean hasTimer() {
        return timerVisible && timer > 0;
    }

    public void setTimer(int value) {
        this.timer = value;
        timerDrawable.setValue(timer == SHOW_ONCE ? 1 : Math.max(1, timer), timer > 0, true);
        if (hint != null) {
            hint.hide();
        }
    }

    private void changeTimer(int value) {
        if (this.timer == value) {
            return;
        }
        setTimer(value);
        if (onTTLChange != null) {
            onTTLChange.run(value);
        }
        CharSequence text;
        if (value == 0) {
            text = getString(isVideo ? R.string.TimerPeriodVideoKeep : R.string.TimerPeriodPhotoKeep);
            hint.setMaxWidthPx(getMeasuredWidth());
            hint.setMultilineText(false);
            hint.setInnerPadding(13, 4, 10, 4);
            hint.setIconMargin(0);
            hint.setIconTranslate(0, -dp(1));
        } else if (value == SHOW_ONCE) {
            text = getString(isVideo ? R.string.TimerPeriodVideoSetOnce : R.string.TimerPeriodPhotoSetOnce);
            hint.setMaxWidthPx(getMeasuredWidth());
            hint.setMultilineText(false);
            hint.setInnerPadding(13, 4, 10, 4);
            hint.setIconMargin(0);
            hint.setIconTranslate(0, -dp(1));
        } else if (value > 0) {
            text = AndroidUtilities.replaceTags(LocaleController.formatPluralString(isVideo ? "TimerPeriodVideoSetSeconds" : "TimerPeriodPhotoSetSeconds", value));
            hint.setMultilineText(true);
            hint.setMaxWidthPx(HintView2.cutInFancyHalf(text, hint.getTextPaint()));
            hint.setInnerPadding(12, 7, 11, 7);
            hint.setIconMargin(2);
            hint.setIconTranslate(0, 0);
        } else {
            return;
        }
        hint.setTranslationY((-Math.min(dp(34), getEditTextHeight()) - dp(14)) * (isAtTop() ? -1.0f : 1.0f));
        hint.setText(text);
        final int iconResId = value > 0 ? R.raw.fire_on : R.raw.fire_off;
        RLottieDrawable icon = new RLottieDrawable(iconResId, "" + iconResId, dp(34), dp(34));
        icon.start();
        hint.setIcon(icon);
        hint.show();

        moveButtonExpanded = false;
        AndroidUtilities.cancelRunOnUIThread(collapseMoveButton);
        invalidate();
    }

    @Override
    protected void onEditHeightChange(int height) {
        hint.setTranslationY((-Math.min(dp(34), height) - dp(10)) * (isAtTop() ? -1.0f : 1.0f));
    }

    @Override
    protected boolean clipChild(View child) {
        return child != hint;
    }

    private Utilities.Callback<Integer> onTTLChange;
    public void setOnTimerChange(Utilities.Callback<Integer> onTTLChange) {
        this.onTTLChange = onTTLChange;
    }

    @Override
    protected int getCaptionLimit() {
        return UserConfig.getInstance(currentAccount).isPremium() ? getCaptionPremiumLimit() : getCaptionDefaultLimit();
    }

    @Override
    protected int getCaptionDefaultLimit() {
        return MessagesController.getInstance(currentAccount).captionLengthLimitDefault;
    }

    @Override
    protected int getCaptionPremiumLimit() {
        return MessagesController.getInstance(currentAccount).captionLengthLimitPremium;
    }

    @Override
    protected void beforeUpdateShownKeyboard(boolean show) {
        if (!show) {
            timerButton.setVisibility(timerVisible ? View.VISIBLE : View.GONE);
            addPhotoButton.setVisibility(addPhotoVisible ? View.VISIBLE : View.GONE);
        }
        if (hint != null) {
            hint.hide();
        }
    }

    @Override
    protected void onUpdateShowKeyboard(float keyboardT) {
        timerButton.setAlpha(1f - keyboardT);
        addPhotoButton.setAlpha(1f - keyboardT);
    }

    @Override
    protected void afterUpdateShownKeyboard(boolean show) {
        timerButton.setVisibility(!show && timerVisible ? View.VISIBLE : View.GONE);
        addPhotoButton.setVisibility(!show && addPhotoVisible ? View.VISIBLE : View.GONE);
        if (show) {
            timerButton.setVisibility(View.GONE);
            addPhotoButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected int additionalKeyboardHeight() {
        return 0;
    }

    @Override
    public void updateColors(Theme.ResourcesProvider resourcesProvider) {
        super.updateColors(resourcesProvider);
        timerDrawable.updateColors(0xffffffff, Theme.getColor(Theme.key_chat_editMediaButton, resourcesProvider), 0xffffffff);
    }

    @Override
    protected void setupMentionContainer() {

    }

    protected boolean showMoveButton() {
        return false;
    }

    public void setShowMoveButtonVisible(boolean visible, boolean animated) {
        if (moveButtonVisible == visible && animated) return;
        moveButtonVisible = visible;
        if (!animated) {
            moveButtonAnimated.set(visible, true);
        }
        invalidate();
    }

    protected void onMoveButtonClick() {

    }

    @Override
    public int getEditTextHeight() {
        return super.getEditTextHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            moveButtonBounce.setPressed(moveButtonBounds.contains(event.getX(), event.getY()));
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (moveButtonBounce.isPressed() && !moveButtonBounds.contains(event.getX(), event.getY())) {
                moveButtonBounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (moveButtonBounce.isPressed()) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onMoveButtonClick();
                    moveButtonText.setText(getString(isAtTop() ? R.string.MoveCaptionDown : R.string.MoveCaptionUp), true);
                }
                moveButtonBounce.setPressed(false);
                return true;
            }
        }
        return moveButtonBounce.isPressed() || super.dispatchTouchEvent(event);
    }
}
