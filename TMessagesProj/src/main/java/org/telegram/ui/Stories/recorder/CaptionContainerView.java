package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.ActionBar.Theme.RIPPLE_MASK_CIRCLE_20DP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MentionsContainerView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.PeerStoriesView;
import org.telegram.ui.WrappedResourceProvider;

public class CaptionContainerView extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout containerView;
    private int currentAccount;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final EditTextEmoji editText;
    public ImageView applyButton;
    public AnimatedTextView limitTextView;

    public ImageView periodButton;
    private ItemOptions periodPopup;
    private boolean periodVisible = true;

    public static final int[] periods = new int[] { 6 * 3600, 12 * 3600, 86400, 2 * 86400/*, Integer.MAX_VALUE*/ };
    public static final int[] periodDrawables = new int[] { R.drawable.msg_story_6h, R.drawable.msg_story_12h, R.drawable.msg_story_24h, R.drawable.msg_story_48h/*, R.drawable.msg_story_infinite*/ };
    private int periodIndex = 0;

    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient fadeGradient = new LinearGradient(0, 0, 0, AndroidUtilities.dp(10), new int[] { 0xffff0000, 0x00000000 }, new float[] { 0.05f, 1 }, Shader.TileMode.CLAMP);
    private final Matrix matrix = new Matrix();

    private final StoryRecorder.WindowView rootView;

    public final KeyboardNotifier keyboardNotifier;
    public MentionsContainerView mentionContainer;

    public CaptionContainerView(Context context, int currentAccount, StoryRecorder.WindowView rootView, FrameLayout containerView, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;
        this.rootView = rootView;
        this.containerView = containerView;

        backgroundPaint.setColor(0x80000000);

        keyboardNotifier = new KeyboardNotifier(rootView, this::updateKeyboard);

        editText = new EditTextEmoji(context, rootView, null, EditTextEmoji.STYLE_STORY, true, resourcesProvider) {
            @Override
            protected void onEmojiKeyboardUpdate() {
                keyboardNotifier.fire();
            }

            @Override
            protected void onWaitingForKeyboard() {
                keyboardNotifier.awaitKeyboard();
            }

            @Override
            protected void createEmojiView() {
                super.createEmojiView();
                EmojiView emojiView = getEmojiView();
                if (emojiView != null) {
                    emojiView.shouldLightenBackground = false;
                    emojiView.fixBottomTabContainerTranslation = false;
                    emojiView.setShouldDrawBackground(false);
                }
            }

            @Override
            protected void drawEmojiBackground(Canvas canvas, View view) {
                AndroidUtilities.rectTmp.set(0, 0, view.getWidth(), view.getHeight());
                drawBackground(canvas, AndroidUtilities.rectTmp, 0, .95f, view);
            }
        };
        editText.setHint(LocaleController.getString("StoryAddCaption", R.string.StoryAddCaption));
        editText.getEditText().setTranslationX(AndroidUtilities.dp(-40 + 18));
        editText.getEmojiButton().setAlpha(0f);
        editText.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (editText.getEditText().suppressOnTextChanged) {
                    return;
                }
                if (mentionContainer == null) {
                    createMentionsContainer();
                }
                if (mentionContainer.getAdapter() != null) {
                    mentionContainer.getAdapter().setUserOrChar(UserConfig.getInstance(currentAccount).getCurrentUser(), null);
                    mentionContainer.getAdapter().searchUsernameOrHashtag(text, editText.getEditText().getSelectionStart(), null, false, false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                EditTextCaption editText2 = editText.getEditText();
                if (editText2 != null && editText2.getLayout() != null) {
                    editText2.ignoreClipTop = (
                        editText2.getLayout().getHeight() > (dp(180) - editText2.getPaddingTop() - editText2.getPaddingBottom())
                    );
                }
                int length = 0;
                try {
                    length = editText.getEditText().getText().length();
                } catch (Exception ignore) {
                }
                String limitText = null;
                final int limit = MessagesController.getInstance(currentAccount).storyCaptionLengthLimit;
                if (length + 25 > limit) {
                    limitText = "" + (limit - length);
                }
                limitTextView.cancelAnimation();
                limitTextView.setText(limitText);
                limitTextView.setTextColor(length >= limit ? 0xffEC7777 : 0xffffffff);
            }
        });
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 12, 12, 12, 12));

        applyButton = new BounceableImageView(context);
        CombinedDrawable drawable = new CombinedDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(16), 0xff66bffa), context.getResources().getDrawable(R.drawable.input_done).mutate(), 0, AndroidUtilities.dp(1));
        drawable.setCustomSize(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        applyButton.setImageDrawable(drawable);
        applyButton.setScaleType(ImageView.ScaleType.CENTER);
        applyButton.setAlpha(0f);
        applyButton.setVisibility(View.GONE);
        applyButton.setOnClickListener(e -> {
            closeKeyboard();
        });
        applyButton.setTranslationY(-AndroidUtilities.dp(1));
        addView(applyButton, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.BOTTOM));

        limitTextView = new AnimatedTextView(context, false, true, true);
        limitTextView.setGravity(Gravity.CENTER);
        limitTextView.setTextSize(dp(15));
        limitTextView.setTextColor(0xffffffff);
        limitTextView.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        limitTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        limitTextView.setTranslationX(dp(2));
        addView(limitTextView, LayoutHelper.createFrame(52, 16, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 44));

        fadePaint.setShader(fadeGradient);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        periodButton = new ImageView(context);
        periodButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18)));
        periodButton.setScaleType(ImageView.ScaleType.CENTER);
        periodButton.setOnClickListener(e -> {
            if (periodPopup != null && periodPopup.isShown()) {
                return;
            }

            Utilities.Callback<Integer> onPeriodSelected = period -> {
                setPeriod(period);
                if (onPeriodUpdate != null) {
                    onPeriodUpdate.run(period);
                }
            };

            final boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();

            Utilities.Callback<Integer> showPremiumHint = isPremium ? null : period -> {
                if (onPremiumHintShow != null) {
                    onPremiumHintShow.run(period);
                }
            };

            periodPopup = ItemOptions.makeOptions(rootView, resourcesProvider, periodButton);
            for (int i = 0; i < periods.length; ++i) {
                final int period = periods[i];
                periodPopup.add(
                    0,
                    period == Integer.MAX_VALUE ?
                        LocaleController.getString("StoryPeriodKeep") :
                        LocaleController.formatPluralString("Hours", period / 3600),
                    periodIndex == i ? Theme.key_dialogTextBlue2 : Theme.key_actionBarDefaultSubmenuItem,
                    () -> onPeriodSelected.run(period)
                ).putPremiumLock(
                    isPremium || period == 86400 || period == Integer.MAX_VALUE ? null : () -> showPremiumHint.run(period)
                );
            }
//            periodPopup.addGap();
            // <string name="StoryPeriodKeepHint">Select ’Keep Always’ to show the story on your page.</string>
//            periodPopup.addText(LocaleController.getString("StoryPeriodKeepHint"), 13);
            periodPopup.addGap();
            periodPopup.addText(LocaleController.getString("StoryPeriodHint"), 13);
            periodPopup.setDimAlpha(0).show();
        });
        setPeriod(86400, false);
        addView(periodButton, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 11, 11));

//        setOnClickListener(e -> {
//            if (!editText.isKeyboardVisible() && !editText.isPopupVisible()) {
//                editText.openKeyboard();
//            }
//        });
    }

    public void closeKeyboard() {
        editText.closeKeyboard();
        editText.hidePopup(true);
    }

    public boolean ignoreTouches;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ignoreTouches) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void createMentionsContainer() {
        mentionContainer = new MentionsContainerView(getContext(), UserConfig.getInstance(currentAccount).getClientUserId(), 0, LaunchActivity.getLastFragment(), null, resourcesProvider) {
            @Override
            public void drawRoundRect(Canvas canvas, Rect rectTmp, float radius) {
                AndroidUtilities.rectTmp.set(rectTmp);
                drawBackground(canvas, AndroidUtilities.rectTmp, radius, .9f, mentionContainer);
            }
        };
        mentionContainer.getAdapter().setAllowStickers(false);
        mentionContainer.getAdapter().setAllowBots(false);
        mentionContainer.getAdapter().setAllowChats(false);
        mentionContainer.getAdapter().setSearchInDailogs(true);
        mentionContainer.withDelegate(new MentionsContainerView.Delegate() {

            @Override
            public void replaceText(int start, int len, CharSequence replacingString, boolean allowShort) {
                replaceWithText(start, len, replacingString, allowShort);
            }

            @Override
            public Paint.FontMetricsInt getFontMetrics() {
                return editText.getEditText().getPaint().getFontMetricsInt();
            }
        });
        containerView.addView(mentionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM));
    }

    private void replaceWithText(int start, int len, CharSequence text, boolean parseEmoji) {
        if (editText == null) {
            return;
        }
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(editText.getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, editText.getEditText().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            editText.setText(builder);
            editText.setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setPeriod(int period) {
        setPeriod(period, true);
    }

    public void setPeriodVisible(boolean visible) {
        periodVisible = visible;
        periodButton.setVisibility(periodVisible && !keyboardShown ? View.VISIBLE : View.GONE);
    }

    public void setPeriod(int period, boolean animated) {
        int index = 2;
        for (int i = 0; i < periods.length; ++i) {
            if (periods[i] == period) {
                index = i;
                break;
            }
        }
        if (periodIndex == index) {
            return;
        }
        Drawable drawable = getResources().getDrawable(periodDrawables[periodIndex = index]).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
        if (animated) {
            AndroidUtilities.updateImageViewImageAnimated(periodButton, drawable);
        } else {
            periodButton.setImageDrawable(drawable);
        }
    }

    public void hidePeriodPopup() {
        if (periodPopup != null) {
            periodPopup.dismiss();
            periodPopup = null;
        }
    }

    public void onResume() {
        editText.onResume();
    }
    public void onPause() {
        editText.onPause();
    }

    private Utilities.Callback<Integer> onHeightUpdate;
    public void setOnHeightUpdate(Utilities.Callback<Integer> onHeightUpdate) {
        this.onHeightUpdate = onHeightUpdate;
    }

    private Utilities.Callback<Integer> onPeriodUpdate;
    public void setOnPeriodUpdate(Utilities.Callback<Integer> listener) {
        this.onPeriodUpdate = listener;
    }

    private Utilities.Callback<Integer> onPremiumHintShow;
    public void setOnPremiumHint(Utilities.Callback<Integer> listener) {
        this.onPremiumHintShow = listener;
    }

    public void heightUpdate() {
        if (onHeightUpdate != null) {
            int height = editText.getHeight();
            if (keyboardShown) {
                height = Math.max(dp(46), height);
            } else {
                height = Math.min(dp(150), height);
            }
            onHeightUpdate.run(height);
        }
    }

    public int getEditTextHeight() {
        int height = editText.getHeight();
        if (keyboardShown) {
            height = Math.max(dp(46), height);
        } else {
            height = Math.min(dp(150), height);
        }
        return height;
    }

    private Utilities.Callback<Boolean> onKeyboardOpen;
    public void setOnKeyboardOpen(Utilities.Callback<Boolean> onKeyboardOpen) {
        this.onKeyboardOpen = onKeyboardOpen;
    }

    ObjectAnimator parentKeyboardAnimator;

    private void updateKeyboard(int keyboardHeight) {
        rootView.notifyHeightChanged();
        if (editText.isPopupShowing() || editText.isWaitingForKeyboardOpen()) {
            keyboardHeight = Math.max(0, AndroidUtilities.navigationBarHeight + editText.getKeyboardHeight());
        }
        keyboardHeight = Math.max(0, keyboardHeight - rootView.getBottomPadding(true));
        View parent = (View) getParent();
        parent.clearAnimation();

        if (parentKeyboardAnimator != null) {
            parentKeyboardAnimator.removeAllListeners();
            parentKeyboardAnimator.cancel();
            parentKeyboardAnimator = null;
        }

        parentKeyboardAnimator = ObjectAnimator.ofFloat(parent, TRANSLATION_Y, parent.getTranslationY(), -keyboardHeight);
        if (keyboardHeight > AndroidUtilities.dp(20)) {
            parentKeyboardAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
            parentKeyboardAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
        } else {
            parentKeyboardAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            parentKeyboardAnimator.setDuration(640);
        }
        parentKeyboardAnimator.start();

        toKeyboardShow = keyboardHeight > AndroidUtilities.dp(20);
        AndroidUtilities.cancelRunOnUIThread(updateShowKeyboard);
        AndroidUtilities.runOnUIThread(updateShowKeyboard);
        if (keyboardHeight < AndroidUtilities.dp(20)) {
            editText.getEditText().clearFocus();
            editText.hidePopup(true);
        }
    }

    private boolean toKeyboardShow;
    private Runnable updateShowKeyboard = () -> {
        updateShowKeyboard(toKeyboardShow, true);
    };

    public float keyboardT;
    public boolean keyboardShown;
    private ValueAnimator keyboardAnimator;
    private void updateShowKeyboard(boolean show, boolean animated) {
        if (keyboardShown == show) {
            return;
        }
        keyboardShown = show;
        if (keyboardAnimator != null) {
            keyboardAnimator.cancel();
            keyboardAnimator = null;
        }
        if (onKeyboardOpen != null) {
            onKeyboardOpen.run(show);
        }
        if (animated) {
            if (show) {
                if (mentionContainer != null) {
                    mentionContainer.setVisibility(View.VISIBLE);
                }
                applyButton.setVisibility(View.VISIBLE);
            } else {
                editText.getEditText().scrollBy(0, -editText.getEditText().getScrollY());
                periodButton.setVisibility(periodVisible ? View.VISIBLE : View.GONE);
            }
            keyboardAnimator = ValueAnimator.ofFloat(keyboardT, show ? 1 : 0);
            keyboardAnimator.addUpdateListener(anm -> {
                keyboardT = (float) anm.getAnimatedValue();
                editText.getEditText().setTranslationX(AndroidUtilities.lerp(AndroidUtilities.dp(-40 + 18), AndroidUtilities.dp(2), keyboardT));
                editText.setTranslationX(AndroidUtilities.lerp(0, AndroidUtilities.dp(-8), keyboardT));
                editText.setTranslationY(AndroidUtilities.lerp(0, AndroidUtilities.dp(12 - 2), keyboardT));
                limitTextView.setAlpha(AndroidUtilities.lerp(0, 1, keyboardT));
                editText.getEmojiButton().setAlpha(keyboardT);
                applyButton.setAlpha((float) Math.pow(keyboardT, 16));
                periodButton.setAlpha(1f - keyboardT);
                if (mentionContainer != null) {
                    mentionContainer.setAlpha((float) Math.pow(keyboardT, 4));
                }
                invalidate();
            });
            keyboardAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        applyButton.setVisibility(View.GONE);
                        if (mentionContainer != null) {
                            mentionContainer.setVisibility(View.GONE);
                        }
                    } else {
                        periodButton.setVisibility(View.GONE);
                    }
                }
            });
            if (show) {
                keyboardAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                keyboardAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
            } else {
                keyboardAnimator.setInterpolator(new FastOutSlowInInterpolator());
                keyboardAnimator.setDuration(420);
            }
            keyboardAnimator.start();
        } else {
            keyboardT = show ? 1 : 0;
            editText.getEditText().setTranslationX(AndroidUtilities.lerp(AndroidUtilities.dp(-40 + 18), AndroidUtilities.dp(2), keyboardT));
            editText.setTranslationX(AndroidUtilities.lerp(0, AndroidUtilities.dp(-8), keyboardT));
            editText.setTranslationY(AndroidUtilities.lerp(0, AndroidUtilities.dp(12 - 2), keyboardT));
            limitTextView.setAlpha(AndroidUtilities.lerp(0, 1, keyboardT));
            editText.getEmojiButton().setAlpha(keyboardT);
            applyButton.setVisibility(show ? View.VISIBLE : View.GONE);
            applyButton.setAlpha(show ? 1f : 0f);
            periodButton.setVisibility(!show && periodVisible ? View.VISIBLE : View.GONE);
            periodButton.setAlpha(!show ? 1f : 0f);
            invalidate();
        }
        editText.setSuggestionsEnabled(show);
        if (!show) {
            editText.getEditText().setSpoilersRevealed(false, true);
        }

        if (show && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE && !LiteMode.isPowerSaverApplied()) {
            if (blurBitmap == null) {
                blurBitmap = Bitmap.createBitmap((int) (rootView.getWidth() / 12f), (int) (rootView.getHeight() / 12f), Bitmap.Config.ARGB_8888);
            }
            ignoreDraw = true;
            drawBlurBitmap(blurBitmap, 12);
            ignoreDraw = false;
            blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            if (blurBitmapMatrix == null) {
                blurBitmapMatrix = new Matrix();
            } else {
                blurBitmapMatrix.reset();
            }
            blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
            if (blurPaint == null) {
                blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                blurPaint.setColor(0xffffffff);
            }
            blurPaint.setShader(blurBitmapShader);
        }
    }

    protected void drawBlurBitmap(Bitmap bitmap, float amount) {
        // do draw
        Utilities.stackBlurBitmap(bitmap, (int) amount);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (blurBitmap != null) {
            blurBitmap.recycle();
        }
        blurBitmapShader = null;
        blurPaint = null;
    }

    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Matrix blurBitmapMatrix;
    private Paint blurPaint;

    public boolean onBackPressed() {
        if (editText.isPopupShowing()) {
            editText.hidePopup(true);
            return true;
        }

        if (editText.isKeyboardVisible() && !keyboardNotifier.ignoring) {
            closeKeyboard();
            return true;
        }

        return false;
    }

    private final AnimatedFloat heightAnimated = new AnimatedFloat(this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
    private int lastHeight;
    private float lastHeightTranslation;

    private boolean ignoreDraw = false;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (ignoreDraw) {
            return;
        }
        int height = editText.getHeight();
        if (keyboardShown) {
            height = Math.max(dp(46), height);
        } else {
            height = Math.min(dp(150), height);
        }
        if (height != lastHeight) {
            if (onHeightUpdate != null) {
                onHeightUpdate.run(height);
            }
            lastHeight = height;
        }
        final int heightAnimated = (int) this.heightAnimated.set(height);
        updateMentionsLayoutPosition();
        final float heightTranslation = height - heightAnimated;
        if (Math.abs(lastHeightTranslation - heightTranslation) >= 1) {
            editText.getEditText().setTranslationY(heightTranslation);
        }
        lastHeightTranslation = heightTranslation;

        final float pad = AndroidUtilities.lerp(AndroidUtilities.dp(12), 0, keyboardT);
        AndroidUtilities.rectTmp.set(
            pad,
            getHeight() - pad - heightAnimated,
            getWidth() - pad,
            getHeight() - pad
        );

        final float r = AndroidUtilities.lerp(AndroidUtilities.dp(21), 0, keyboardT);
        drawBackground(canvas, AndroidUtilities.rectTmp, r, 1f, this);

        canvas.save();
        canvas.clipRect(AndroidUtilities.rectTmp);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas canvas, RectF rectF, float r, float alpha, View view) {
        if (keyboardT > 0 && blurPaint != null && blurBitmapShader != null && blurBitmap != null && !blurBitmap.isRecycled()) {
            blurBitmapMatrix.reset();
            blurBitmapMatrix.postScale((float) rootView.getWidth() / blurBitmap.getWidth(), (float) rootView.getHeight() / blurBitmap.getHeight());
            float x = 0, y = 0;
            for (int i = 0; i < 8 && view != null; ++i) {
                x += view.getX();
                y += view.getY();
                ViewParent parent = view.getParent();
                view = parent instanceof View ? (View) parent : null;
            }
            blurBitmapMatrix.postTranslate(-x, -y);
            blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
            blurPaint.setAlpha((int) (0xFF * keyboardT * alpha));
            canvas.drawRoundRect(rectF, r, r, blurPaint);
        }
        backgroundPaint.setAlpha((int) (blurPaint == null ? 0x80 : AndroidUtilities.lerp(0x80, 0x99, keyboardT) * alpha));
        canvas.drawRoundRect(rectF, r, r, backgroundPaint);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == editText) {
            final float pad = AndroidUtilities.lerp(dp(12), 0, keyboardT);
            AndroidUtilities.rectTmp.set(pad, getHeight() - pad - heightAnimated.get(), getWidth() - pad, getHeight() - pad);

            float ty = Math.max(0, editText.getHeight() - dp(150 - 7)) * (1f - keyboardT);
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            canvas.save();
            canvas.translate(0, ty);
            final boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restore();

            canvas.save();
            matrix.reset();
            matrix.postTranslate(0, AndroidUtilities.rectTmp.top - 1);
            fadeGradient.setLocalMatrix(matrix);
            canvas.drawRect(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top, AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.top + AndroidUtilities.dp(10), fadePaint);

            matrix.reset();
            matrix.postRotate(180);
            matrix.postTranslate(0, AndroidUtilities.rectTmp.bottom);
            fadeGradient.setLocalMatrix(matrix);
            canvas.drawRect(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.bottom - AndroidUtilities.dp(10), AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.bottom, fadePaint);
            canvas.restore();
            canvas.restore();

            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void clearFocus() {
        editText.clearFocus();
    }

    public void clear() {
        editText.setText("");
    }

    public void setText(CharSequence text) {
        editText.setText(text);
    }

    public CharSequence getText() {
        return editText.getText();
    }

    public void updateMentionsLayoutPosition() {
        if (mentionContainer != null) {
            float y = ((View) getParent()).getTranslationY() - heightAnimated.get();
            if (mentionContainer.getY() != y) {
                mentionContainer.setTranslationY(y);
                mentionContainer.invalidate();
            }
        }
    }

    public static class BounceableImageView extends ImageView {
        private final float scale;
        public BounceableImageView(Context context) {
            this(context, .2f);
        }
        public BounceableImageView(Context context, float scale) {
            super(context);
            this.scale = scale;
        }

        private final ButtonBounce bounce = new ButtonBounce(this);

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            bounce.setPressed(pressed);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.save();
            final float scale = bounce.getScale(this.scale);
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            super.draw(canvas);
            canvas.restore();
        }
    }


}
