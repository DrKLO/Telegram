/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.core.os.CancellationSignal;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class PasscodeView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final static float BACKGROUND_SPRING_STIFFNESS = 300f;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didGenerateFingerprintKeyPair) {
            checkFingerprintButton();
            if ((boolean) args[0] && SharedConfig.appLocked) {
                checkFingerprint();
            }
        } else if (id == NotificationCenter.passcodeDismissed) {
            if (args[0] != this) {
                setVisibility(GONE);

                if (fingerprintDialog != null) {
                    fingerprintDialog.dismiss();
                }
            }
        }
    }

    public interface PasscodeViewDelegate {
        void didAcceptedPassword(PasscodeView view);
    }

    private static class AnimatingTextView extends FrameLayout {

        private ArrayList<TextView> characterTextViews;
        private ArrayList<TextView> dotTextViews;
        private StringBuilder stringBuilder;
        private final static String DOT = "\u2022";
        private AnimatorSet currentAnimation;
        private Runnable dotRunnable;

        public AnimatingTextView(Context context) {
            super(context);
            characterTextViews = new ArrayList<>(4);
            dotTextViews = new ArrayList<>(4);
            stringBuilder = new StringBuilder(4);

            for (int a = 0; a < 4; a++) {
                TextView textView = new TextView(context);
                textView.setTextColor(0xffffffff);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setAlpha(0);
                textView.setPivotX(AndroidUtilities.dp(25));
                textView.setPivotY(AndroidUtilities.dp(25));
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
                characterTextViews.add(textView);

                textView = new TextView(context);
                textView.setTextColor(0xffffffff);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setAlpha(0);
                textView.setText(DOT);
                textView.setPivotX(AndroidUtilities.dp(25));
                textView.setPivotY(AndroidUtilities.dp(25));
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
                dotTextViews.add(textView);
            }
        }

        private int getXForTextView(int pos) {
            return (getMeasuredWidth() - stringBuilder.length() * AndroidUtilities.dp(30)) / 2 + pos * AndroidUtilities.dp(30) - AndroidUtilities.dp(10);
        }

        public void appendCharacter(String c) {
            if (stringBuilder.length() == 4) {
                return;
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception e) {
                FileLog.e(e);
            }


            ArrayList<Animator> animators = new ArrayList<>();
            final int newPos = stringBuilder.length();
            stringBuilder.append(c);

            TextView textView = characterTextViews.get(newPos);
            textView.setText(c);
            textView.setTranslationX(getXForTextView(newPos));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, AndroidUtilities.dp(20), 0));
            textView = dotTextViews.get(newPos);
            textView.setTranslationX(getXForTextView(newPos));
            textView.setAlpha(0);
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, AndroidUtilities.dp(20), 0));

            for (int a = newPos + 1; a < 4; a++) {
                textView = characterTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                }

                textView = dotTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                }
            }

            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
            }
            dotRunnable = new Runnable() {
                @Override
                public void run() {
                    if (dotRunnable != this) {
                        return;
                    }
                    ArrayList<Animator> animators = new ArrayList<>();

                    TextView textView = characterTextViews.get(newPos);
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    textView = dotTextViews.get(newPos);
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 1));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 1));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 1));

                    currentAnimation = new AnimatorSet();
                    currentAnimation.setDuration(150);
                    currentAnimation.playTogether(animators);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (currentAnimation != null && currentAnimation.equals(animation)) {
                                currentAnimation = null;
                            }
                        }
                    });
                    currentAnimation.start();
                }
            };
            AndroidUtilities.runOnUIThread(dotRunnable, 1500);

            for (int a = 0; a < newPos; a++) {
                textView = characterTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
                textView = dotTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 1));
                animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 1));
                animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 1));
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
            }

            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            currentAnimation = new AnimatorSet();
            currentAnimation.setDuration(150);
            currentAnimation.playTogether(animators);
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentAnimation != null && currentAnimation.equals(animation)) {
                        currentAnimation = null;
                    }
                }
            });
            currentAnimation.start();
        }

        public String getString() {
            return stringBuilder.toString();
        }

        public int length() {
            return stringBuilder.length();
        }

        public boolean eraseLastCharacter() {
            if (stringBuilder.length() == 0) {
                return false;
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception e) {
                FileLog.e(e);
            }

            ArrayList<Animator> animators = new ArrayList<>();
            int deletingPos = stringBuilder.length() - 1;
            if (deletingPos != 0) {
                stringBuilder.deleteCharAt(deletingPos);
            }

            for (int a = deletingPos; a < 4; a++) {
                TextView textView = characterTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                }

                textView = dotTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, 0));
                    animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                }
            }

            if (deletingPos == 0) {
                stringBuilder.deleteCharAt(deletingPos);
            }

            for (int a = 0; a < deletingPos; a++) {
                TextView textView = characterTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
                textView = dotTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, getXForTextView(a)));
            }

            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                dotRunnable = null;
            }

            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            currentAnimation = new AnimatorSet();
            currentAnimation.setDuration(150);
            currentAnimation.playTogether(animators);
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentAnimation != null && currentAnimation.equals(animation)) {
                        currentAnimation = null;
                    }
                }
            });
            currentAnimation.start();
            return true;
        }

        private void eraseAllCharacters(final boolean animated) {
            if (stringBuilder.length() == 0) {
                return;
            }
            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                dotRunnable = null;
            }
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            stringBuilder.delete(0, stringBuilder.length());
            if (animated) {
                ArrayList<Animator> animators = new ArrayList<>();

                for (int a = 0; a < 4; a++) {
                    TextView textView = characterTextViews.get(a);
                    if (textView.getAlpha() != 0) {
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    }

                    textView = dotTextViews.get(a);
                    if (textView.getAlpha() != 0) {
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0));
                        animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, 0));
                    }
                }

                currentAnimation = new AnimatorSet();
                currentAnimation.setDuration(150);
                currentAnimation.playTogether(animators);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentAnimation != null && currentAnimation.equals(animation)) {
                            currentAnimation = null;
                        }
                    }
                });
                currentAnimation.start();
            } else {
                for (int a = 0; a < 4; a++) {
                    characterTextViews.get(a).setAlpha(0);
                    dotTextViews.get(a).setAlpha(0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (dotRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                dotRunnable = null;
            }
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }

            for (int a = 0; a < 4; a++) {
                if (a < stringBuilder.length()) {
                    TextView textView = characterTextViews.get(a);
                    textView.setAlpha(0);
                    textView.setScaleX(1);
                    textView.setScaleY(1);
                    textView.setTranslationY(0);
                    textView.setTranslationX(getXForTextView(a));

                    textView = dotTextViews.get(a);
                    textView.setAlpha(1);
                    textView.setScaleX(1);
                    textView.setScaleY(1);
                    textView.setTranslationY(0);
                    textView.setTranslationX(getXForTextView(a));
                } else {
                    characterTextViews.get(a).setAlpha(0);
                    dotTextViews.get(a).setAlpha(0);
                }
            }
            super.onLayout(changed, left, top, right, bottom);
        }
    }

    private FrameLayout container;
    private Drawable backgroundDrawable;
    private FrameLayout numbersFrameLayout;
    private ArrayList<TextView> numberTextViews;
    private ArrayList<TextView> lettersTextViews;
    private ArrayList<FrameLayout> numberFrameLayouts;
    private FrameLayout passwordFrameLayout;
    private ImageView eraseView;
    private ImageView fingerprintView;
    private EditTextBoldCursor passwordEditText;
    private AnimatingTextView passwordEditText2;
    private FrameLayout backgroundFrameLayout;
    private TextView passcodeTextView;
    private TextView retryTextView;
    private ImageView checkImage;
    private ImageView fingerprintImage;
    private int keyboardHeight = 0;

    private CancellationSignal cancellationSignal;
    private ImageView fingerprintImageView;
    private TextView fingerprintStatusTextView;
    private boolean selfCancelled;
    private AlertDialog fingerprintDialog;

    private int imageY;

    private RLottieImageView imageView;

    private Rect rect = new Rect();

    private PasscodeViewDelegate delegate;

    private final static int id_fingerprint_textview = 1000;
    private final static int id_fingerprint_imageview = 1001;

    private SpringAnimation backgroundAnimationSpring;
    private LinkedList<Runnable> backgroundSpringQueue = new LinkedList<>();
    private LinkedList<Boolean> backgroundSpringNextQueue = new LinkedList<>();

    private static class InnerAnimator {
        private AnimatorSet animatorSet;
        private float startRadius;
    }

    private ArrayList<InnerAnimator> innerAnimators = new ArrayList<>();

    private static final @IdRes
    int[] ids = {
            R.id.passcode_btn_0,
            R.id.passcode_btn_1,
            R.id.passcode_btn_2,
            R.id.passcode_btn_3,
            R.id.passcode_btn_4,
            R.id.passcode_btn_5,
            R.id.passcode_btn_6,
            R.id.passcode_btn_7,
            R.id.passcode_btn_8,
            R.id.passcode_btn_9,
            R.id.passcode_btn_backspace,
            R.id.passcode_btn_fingerprint
    };

    public PasscodeView(final Context context) {
        super(context);

        setWillNotDraw(false);
        setVisibility(GONE);

        backgroundFrameLayout = new FrameLayout(context) {

            private Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                if (backgroundDrawable != null) {
                    if (backgroundDrawable instanceof MotionBackgroundDrawable || backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        backgroundDrawable.draw(canvas);
                    } else {
                        float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                        float scaleY = (float) (getMeasuredHeight() + keyboardHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                        float scale = Math.max(scaleX, scaleY);
                        int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                        int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                        int x = (getMeasuredWidth() - width) / 2;
                        int y = (getMeasuredHeight() - height + keyboardHeight) / 2;
                        backgroundDrawable.setBounds(x, y, x + width, y + height);
                        backgroundDrawable.draw(canvas);
                    }
                } else {
                    super.onDraw(canvas);
                }
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
            }

            @Override
            public void setBackgroundColor(int color) {
                paint.setColor(color);
            }
        };
        backgroundFrameLayout.setWillNotDraw(false);
        addView(backgroundFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.passcode_lock_close, 58, 58);
        imageView.setAutoRepeat(false);
        addView(imageView, LayoutHelper.createFrame(58, 58, Gravity.LEFT | Gravity.TOP));

        passwordFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.addView(passwordFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        passcodeTextView = new TextView(context);
        passcodeTextView.setTextColor(0xffffffff);
        passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        passcodeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordFrameLayout.addView(passcodeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 74));

        retryTextView = new TextView(context);
        retryTextView.setTextColor(0xffffffff);
        retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        retryTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        retryTextView.setVisibility(INVISIBLE);
        backgroundFrameLayout.addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        passwordEditText2 = new AnimatingTextView(context);
        passwordFrameLayout.addView(passwordEditText2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 6));

        passwordEditText = new EditTextBoldCursor(context);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
        passwordEditText.setTextColor(0xffffffff);
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setSingleLine(true);
        passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordEditText.setTypeface(Typeface.DEFAULT);
        passwordEditText.setBackgroundDrawable(null);
        passwordEditText.setCursorColor(0xffffffff);
        passwordEditText.setCursorSize(AndroidUtilities.dp(32));
        passwordFrameLayout.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 0));
        passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                processDone(false);
                return true;
            }
            return false;
        });
        passwordEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                    boolean needAnimation = false;
                    MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                    motionBackgroundDrawable.setAnimationProgressProvider(null);
                    float progress = motionBackgroundDrawable.getPosAnimationProgress();
                    boolean next;
                    if (count == 0 && after == 1) {
                        motionBackgroundDrawable.switchToNextPosition(true);
                        needAnimation = true;
                        next = true;
                    } else if (count == 1 && after == 0) {
                        motionBackgroundDrawable.switchToPrevPosition(true);
                        needAnimation = true;
                        next = false;
                    } else {
                        next = false;
                    }

                    if (needAnimation) {
                        if (progress >= 1f) {
                            animateBackground(motionBackgroundDrawable);
                        } else {
                            backgroundSpringQueue.offer(()-> {
                                if (next) {
                                    motionBackgroundDrawable.switchToNextPosition(true);
                                } else {
                                    motionBackgroundDrawable.switchToPrevPosition(true);
                                }
                                animateBackground(motionBackgroundDrawable);
                            });
                            backgroundSpringNextQueue.offer(next);

                            List<Runnable> remove = new ArrayList<>();
                            List<Integer> removeIndex = new ArrayList<>();
                            for (int i = 0; i < backgroundSpringQueue.size(); i++) {
                                Runnable callback = backgroundSpringQueue.get(i);
                                boolean qNext = backgroundSpringNextQueue.get(i);

                                if (qNext != next) {
                                    remove.add(callback);
                                    removeIndex.add(i);
                                }
                            }
                            for (Runnable callback : remove) {
                                backgroundSpringQueue.remove(callback);
                            }
                            for (int i : removeIndex) {
                                if (i < backgroundSpringNextQueue.size()) {
                                    backgroundSpringNextQueue.remove(i);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (passwordEditText.length() == 4 && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
                    processDone(false);
                }
            }
        });
        passwordEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });

        checkImage = new ImageView(context);
        checkImage.setImageResource(R.drawable.passcode_check);
        checkImage.setScaleType(ImageView.ScaleType.CENTER);
        checkImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(checkImage, LayoutHelper.createFrame(60, 60, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 4));
        checkImage.setContentDescription(LocaleController.getString("Done", R.string.Done));
        checkImage.setOnClickListener(v -> processDone(false));

        fingerprintImage = new ImageView(context);
        fingerprintImage.setImageResource(R.drawable.fingerprint);
        fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
        fingerprintImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(fingerprintImage, LayoutHelper.createFrame(60, 60, Gravity.BOTTOM | Gravity.LEFT, 10, 0, 0, 4));
        fingerprintImage.setContentDescription(LocaleController.getString("AccDescrFingerprint", R.string.AccDescrFingerprint));
        fingerprintImage.setOnClickListener(v -> checkFingerprint());

        FrameLayout lineFrameLayout = new FrameLayout(context);
        lineFrameLayout.setBackgroundColor(0x26ffffff);
        passwordFrameLayout.addView(lineFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 20, 0, 20, 0));

        numbersFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.addView(numbersFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        lettersTextViews = new ArrayList<>(10);
        numberTextViews = new ArrayList<>(10);
        numberFrameLayouts = new ArrayList<>(10);
        for (int a = 0; a < 10; a++) {
            TextView textView = new TextView(context);
            textView.setTextColor(0xffffffff);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
            textView.setGravity(Gravity.CENTER);
            textView.setText(String.format(Locale.US, "%d", a));
            numbersFrameLayout.addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            numberTextViews.add(textView);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setTextColor(0x7fffffff);
            textView.setGravity(Gravity.CENTER);
            numbersFrameLayout.addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            switch (a) {
                case 0:
                    textView.setText("+");
                    break;
                case 2:
                    textView.setText("ABC");
                    break;
                case 3:
                    textView.setText("DEF");
                    break;
                case 4:
                    textView.setText("GHI");
                    break;
                case 5:
                    textView.setText("JKL");
                    break;
                case 6:
                    textView.setText("MNO");
                    break;
                case 7:
                    textView.setText("PQRS");
                    break;
                case 8:
                    textView.setText("TUV");
                    break;
                case 9:
                    textView.setText("WXYZ");
                    break;
                default:
                    break;
            }
            lettersTextViews.add(textView);
        }
        eraseView = new ImageView(context);
        eraseView.setScaleType(ImageView.ScaleType.CENTER);
        eraseView.setImageResource(R.drawable.passcode_delete);
        numbersFrameLayout.addView(eraseView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));

        fingerprintView = new ImageView(context);
        fingerprintView.setScaleType(ImageView.ScaleType.CENTER);
        fingerprintView.setImageResource(R.drawable.fingerprint);
        fingerprintView.setVisibility(GONE);
        numbersFrameLayout.addView(fingerprintView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
        checkFingerprintButton();

        for (int a = 0; a < 12; a++) {
            FrameLayout frameLayout = new FrameLayout(context) {
                @Override
                public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setClassName("android.widget.Button");
                }
            };
            frameLayout.setBackgroundResource(R.drawable.bar_selector_lock);
            frameLayout.setTag(a);
            if (a == 11) {
                frameLayout.setContentDescription(LocaleController.getString("AccDescrFingerprint", R.string.AccDescrFingerprint));
                setNextFocus(frameLayout, R.id.passcode_btn_0);
            } else if (a == 10) {
                frameLayout.setOnLongClickListener(v -> {
                    passwordEditText.setText("");
                    passwordEditText2.eraseAllCharacters(true);
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        ((MotionBackgroundDrawable) backgroundDrawable).switchToPrevPosition(true);
                    }
                    return true;
                });
                frameLayout.setContentDescription(LocaleController.getString("AccDescrBackspace", R.string.AccDescrBackspace));
                setNextFocus(frameLayout, R.id.passcode_btn_1);
            } else {
                frameLayout.setContentDescription(a + "");
                if (a == 0) {
                    setNextFocus(frameLayout, R.id.passcode_btn_backspace);
                } else if (a == 9) {
                    if (fingerprintView.getVisibility() == View.VISIBLE) {
                        setNextFocus(frameLayout, R.id.passcode_btn_fingerprint);
                    } else {
                        setNextFocus(frameLayout, R.id.passcode_btn_0);
                    }
                } else {
                    setNextFocus(frameLayout, ids[a + 1]);
                }
            }
            frameLayout.setId(ids[a]);
            frameLayout.setOnClickListener(v -> {
                int tag = (Integer) v.getTag();
                boolean erased = false;
                switch (tag) {
                    case 0:
                        passwordEditText2.appendCharacter("0");
                        break;
                    case 1:
                        passwordEditText2.appendCharacter("1");
                        break;
                    case 2:
                        passwordEditText2.appendCharacter("2");
                        break;
                    case 3:
                        passwordEditText2.appendCharacter("3");
                        break;
                    case 4:
                        passwordEditText2.appendCharacter("4");
                        break;
                    case 5:
                        passwordEditText2.appendCharacter("5");
                        break;
                    case 6:
                        passwordEditText2.appendCharacter("6");
                        break;
                    case 7:
                        passwordEditText2.appendCharacter("7");
                        break;
                    case 8:
                        passwordEditText2.appendCharacter("8");
                        break;
                    case 9:
                        passwordEditText2.appendCharacter("9");
                        break;
                    case 10:
                        erased = passwordEditText2.eraseLastCharacter();
                        break;
                    case 11:
                        checkFingerprint();
                        break;
                }
                if (passwordEditText2.length() == 4) {
                    processDone(false);
                }
                if (tag == 11) {

                } else {
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                        motionBackgroundDrawable.setAnimationProgressProvider(null);
                        boolean needAnimation = false;
                        float progress = motionBackgroundDrawable.getPosAnimationProgress();
                        boolean next;
                        if (tag == 10) {
                            if (erased) {
                                motionBackgroundDrawable.switchToPrevPosition(true);
                                needAnimation = true;
                            }
                            next = false;
                        } else {
                            motionBackgroundDrawable.switchToNextPosition(true);
                            needAnimation = true;
                            next = true;
                        }

                        if (needAnimation) {
                            if (progress >= 1f) {
                                animateBackground(motionBackgroundDrawable);
                            } else {
                                backgroundSpringQueue.offer(()-> {
                                    if (next) {
                                        motionBackgroundDrawable.switchToNextPosition(true);
                                    } else {
                                        motionBackgroundDrawable.switchToPrevPosition(true);
                                    }
                                    animateBackground(motionBackgroundDrawable);
                                });
                                backgroundSpringNextQueue.offer(next);

                                List<Runnable> remove = new ArrayList<>();
                                List<Integer> removeIndex = new ArrayList<>();
                                for (int i = 0; i < backgroundSpringQueue.size(); i++) {
                                    Runnable callback = backgroundSpringQueue.get(i);
                                    Boolean qNext = backgroundSpringNextQueue.get(i);

                                    if (qNext != null && qNext != next) {
                                        remove.add(callback);
                                        removeIndex.add(i);
                                    }
                                }
                                for (Runnable callback : remove) {
                                    backgroundSpringQueue.remove(callback);
                                }
                                Collections.sort(removeIndex, (o1, o2) -> o2 - o1);
                                for (int i : removeIndex) {
                                    backgroundSpringNextQueue.remove(i);
                                }
                            }
                        }
                    }
                }
            });
            numberFrameLayouts.add(frameLayout);
        }
        for (int a = 11; a >= 0; a--) {
            FrameLayout frameLayout = numberFrameLayouts.get(a);
            numbersFrameLayout.addView(frameLayout, LayoutHelper.createFrame(100, 100, Gravity.TOP | Gravity.LEFT));
        }
    }

    private void animateBackground(MotionBackgroundDrawable motionBackgroundDrawable) {
        if (backgroundAnimationSpring != null && backgroundAnimationSpring.isRunning()) {
            backgroundAnimationSpring.cancel();
        }

        FloatValueHolder animationValue = new FloatValueHolder(0);
        motionBackgroundDrawable.setAnimationProgressProvider(obj -> animationValue.getValue() / 100f);
        backgroundAnimationSpring = new SpringAnimation(animationValue)
                .setSpring(new SpringForce(100)
                        .setStiffness(BACKGROUND_SPRING_STIFFNESS)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        backgroundAnimationSpring.addEndListener((animation, canceled, value, velocity) -> {
            backgroundAnimationSpring = null;
            motionBackgroundDrawable.setAnimationProgressProvider(null);

            if (!canceled) {
                motionBackgroundDrawable.setPosAnimationProgress(1f);
                if (!backgroundSpringQueue.isEmpty()) {
                    backgroundSpringQueue.poll().run();
                    backgroundSpringNextQueue.poll();
                }
            }
        });
        backgroundAnimationSpring.addUpdateListener((animation, value, velocity) -> motionBackgroundDrawable.updateAnimation(true));
        backgroundAnimationSpring.start();
    }

    private void setNextFocus(View view, @IdRes int nextId) {
        view.setNextFocusForwardId(nextId);
        if (Build.VERSION.SDK_INT >= 22) {
            view.setAccessibilityTraversalBefore(nextId);
        }
    }

    public void setDelegate(PasscodeViewDelegate delegate) {
        this.delegate = delegate;
    }

    private void processDone(boolean fingerprint) {
        if (!fingerprint) {
            if (SharedConfig.passcodeRetryInMs > 0) {
                return;
            }
            String password = "";
            if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
                password = passwordEditText2.getString();
            } else if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                password = passwordEditText.getText().toString();
            }
            if (password.length() == 0) {
                onPasscodeError();
                return;
            }
            if (!SharedConfig.checkPasscode(password)) {
                SharedConfig.increaseBadPasscodeTries();
                if (SharedConfig.passcodeRetryInMs > 0) {
                    checkRetryTextView();
                }
                passwordEditText.setText("");
                passwordEditText2.eraseAllCharacters(true);
                onPasscodeError();
                if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                    MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                    if (backgroundAnimationSpring != null) {
                        backgroundAnimationSpring.cancel();
                        motionBackgroundDrawable.setPosAnimationProgress(1f);
                    }
                    if (motionBackgroundDrawable.getPosAnimationProgress() >= 1f) {
                        motionBackgroundDrawable.rotatePreview(true);
                    }
                }
                return;
            }
        }
        SharedConfig.badPasscodeTries = 0;
        passwordEditText.clearFocus();
        AndroidUtilities.hideKeyboard(passwordEditText);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && FingerprintController.isKeyReady() && FingerprintController.checkDeviceFingerprintsChanged()) {
            FingerprintController.deleteInvalidKey();
        }

        SharedConfig.appLocked = false;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
        setOnTouchListener(null);
        if (delegate != null) {
            delegate.didAcceptedPassword(this);
        }

        AndroidUtilities.runOnUIThread(() -> {
            AnimatorSet AnimatorSet = new AnimatorSet();
            AnimatorSet.setDuration(200);
            AnimatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, AndroidUtilities.dp(20)),
                    ObjectAnimator.ofFloat(this, View.ALPHA, AndroidUtilities.dp(0.0f)));
            AnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                }
            });
            AnimatorSet.start();
        });
    }

    private void shakeTextView(final float x, final int num) {
        if (num == 6) {
            return;
        }
        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(ObjectAnimator.ofFloat(passcodeTextView, View.TRANSLATION_X, AndroidUtilities.dp(x)));
        AnimatorSet.setDuration(50);
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                shakeTextView(num == 5 ? 0 : -x, num + 1);
            }
        });
        AnimatorSet.start();
    }

    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkRetryTextView();
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        }
    };
    private int lastValue;

    private void checkRetryTextView() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime > SharedConfig.lastUptimeMillis) {
            SharedConfig.passcodeRetryInMs -= (currentTime - SharedConfig.lastUptimeMillis);
            if (SharedConfig.passcodeRetryInMs < 0) {
                SharedConfig.passcodeRetryInMs = 0;
            }
        }
        SharedConfig.lastUptimeMillis = currentTime;
        SharedConfig.saveConfig();
        if (SharedConfig.passcodeRetryInMs > 0) {
            int value = Math.max(1, (int) Math.ceil(SharedConfig.passcodeRetryInMs / 1000.0));
            if (value != lastValue) {
                retryTextView.setText(LocaleController.formatString("TooManyTries", R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)));
                lastValue = value;
            }
            if (retryTextView.getVisibility() != VISIBLE) {
                retryTextView.setVisibility(VISIBLE);
                passwordFrameLayout.setVisibility(INVISIBLE);
                if (numbersFrameLayout.getVisibility() == VISIBLE) {
                    numbersFrameLayout.setVisibility(INVISIBLE);
                }
                AndroidUtilities.hideKeyboard(passwordEditText);
            }
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        } else {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            if (passwordFrameLayout.getVisibility() != VISIBLE) {
                retryTextView.setVisibility(INVISIBLE);
                passwordFrameLayout.setVisibility(VISIBLE);
                if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
                    numbersFrameLayout.setVisibility(VISIBLE);
                } else if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }
        }
    }

    private void onPasscodeError() {
        Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        shakeTextView(2, 0);
    }

    public void onResume() {
        checkRetryTextView();
        if (retryTextView.getVisibility() != VISIBLE) {
            if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (retryTextView.getVisibility() != VISIBLE && passwordEditText != null) {
                        passwordEditText.requestFocus();
                        AndroidUtilities.showKeyboard(passwordEditText);
                    }
                }, 200);
            }
            checkFingerprint();
        }
    }

    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(checkRunnable);
        if (fingerprintDialog != null) {
            try {
                if (fingerprintDialog.isShowing()) {
                    fingerprintDialog.dismiss();
                }
                fingerprintDialog = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && cancellationSignal != null) {
                cancellationSignal.cancel();
                cancellationSignal = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.passcodeDismissed);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.passcodeDismissed);
    }

    private void checkFingerprint() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        Activity parentActivity = (Activity) getContext();
        if (parentActivity != null && fingerprintView.getVisibility() == VISIBLE && !ApplicationLoader.mainInterfacePaused && (!(parentActivity instanceof LaunchActivity) || ((LaunchActivity) parentActivity).allowShowFingerprintDialog(this))) {
            try {
                if (fingerprintDialog != null && fingerprintDialog.isShowing()) {
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                if (fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints() && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()) {
                    RelativeLayout relativeLayout = new RelativeLayout(getContext());
                    relativeLayout.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);

                    TextView fingerprintTextView = new TextView(getContext());
                    fingerprintTextView.setId(id_fingerprint_textview);
                    fingerprintTextView.setTextAppearance(android.R.style.TextAppearance_Material_Subhead);
                    fingerprintTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    fingerprintTextView.setText(LocaleController.getString("FingerprintInfo", R.string.FingerprintInfo));
                    relativeLayout.addView(fingerprintTextView);
                    RelativeLayout.LayoutParams layoutParams = LayoutHelper.createRelative(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START);
                    fingerprintTextView.setLayoutParams(layoutParams);

                    fingerprintImageView = new ImageView(getContext());
                    fingerprintImageView.setImageResource(R.drawable.ic_fp_40px);
                    fingerprintImageView.setId(id_fingerprint_imageview);
                    relativeLayout.addView(fingerprintImageView, LayoutHelper.createRelative(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0, RelativeLayout.ALIGN_PARENT_START, RelativeLayout.BELOW, id_fingerprint_textview));

                    fingerprintStatusTextView = new TextView(getContext());
                    fingerprintStatusTextView.setGravity(Gravity.CENTER_VERTICAL);
                    fingerprintStatusTextView.setText(LocaleController.getString("FingerprintHelp", R.string.FingerprintHelp));
                    fingerprintStatusTextView.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
                    fingerprintStatusTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack) & 0x42ffffff);
                    relativeLayout.addView(fingerprintStatusTextView);
                    layoutParams = LayoutHelper.createRelative(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                    layoutParams.setMarginStart(AndroidUtilities.dp(16));
                    layoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, id_fingerprint_imageview);
                    layoutParams.addRule(RelativeLayout.ALIGN_TOP, id_fingerprint_imageview);
                    layoutParams.addRule(RelativeLayout.END_OF, id_fingerprint_imageview);
                    fingerprintStatusTextView.setLayoutParams(layoutParams);

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setView(relativeLayout);
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setOnDismissListener(dialog -> {
                        if (cancellationSignal != null) {
                            selfCancelled = true;
                            try {
                                cancellationSignal.cancel();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            cancellationSignal = null;
                        }
                    });
                    if (fingerprintDialog != null) {
                        try {
                            if (fingerprintDialog.isShowing()) {
                                fingerprintDialog.dismiss();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    fingerprintDialog = builder.show();

                    cancellationSignal = new CancellationSignal();
                    selfCancelled = false;
                    fingerprintManager.authenticate(null, 0, cancellationSignal, new FingerprintManagerCompat.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errMsgId, CharSequence errString) {
                            if (errMsgId == 10) {
                                try {
                                    if (fingerprintDialog.isShowing()) {
                                        fingerprintDialog.dismiss();
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                fingerprintDialog = null;
                            } else if (!selfCancelled && errMsgId != 5) {
                                showFingerprintError(errString);
                            }
                        }

                        @Override
                        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                            showFingerprintError(helpString);
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            showFingerprintError(LocaleController.getString("FingerprintNotRecognized", R.string.FingerprintNotRecognized));
                        }

                        @Override
                        public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
                            try {
                                if (fingerprintDialog.isShowing()) {
                                    fingerprintDialog.dismiss();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            fingerprintDialog = null;
                            processDone(true);
                        }
                    }, null);
                }
            } catch (Throwable e) {
                //ignore
            }
        }
    }

    public void onShow(boolean fingerprint, boolean animated) {
        onShow(fingerprint, animated, -1, -1, null, null);
    }

    private void checkFingerprintButton() {
        Activity parentActivity = (Activity) getContext();
        if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && SharedConfig.useFingerprint) {
            try {
                if (fingerprintDialog != null && fingerprintDialog.isShowing()) {
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                if (fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints() && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()) {
                    fingerprintView.setVisibility(VISIBLE);
                } else {
                    fingerprintView.setVisibility(GONE);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                fingerprintView.setVisibility(GONE);
            }
        } else {
            fingerprintView.setVisibility(GONE);
        }
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            fingerprintImage.setVisibility(fingerprintView.getVisibility());
        }
        if (numberFrameLayouts.size() >= 11) {
            numberFrameLayouts.get(11).setVisibility(fingerprintView.getVisibility());
        }
    }

    public void onShow(boolean fingerprint, boolean animated, int x, int y, Runnable onShow, Runnable onStart) {
        checkFingerprintButton();
        checkRetryTextView();
        Activity parentActivity = (Activity) getContext();
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            if (!animated && retryTextView.getVisibility() != VISIBLE && passwordEditText != null) {
                passwordEditText.requestFocus();
                AndroidUtilities.showKeyboard(passwordEditText);
            }
        } else {
            if (parentActivity != null) {
                View currentFocus = parentActivity.getCurrentFocus();
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                    AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
                }
            }
        }
        if (fingerprint && retryTextView.getVisibility() != VISIBLE) {
            checkFingerprint();
        }
        if (getVisibility() == View.VISIBLE) {
            return;
        }
        setTranslationY(0);
        backgroundDrawable = null;
        if (Theme.getCachedWallpaper() instanceof MotionBackgroundDrawable) {
            backgroundDrawable = Theme.getCachedWallpaper();
            backgroundFrameLayout.setBackgroundColor(0xbf000000);
        } else if (Theme.isCustomTheme() && !"CJz3BZ6YGEYBAAAABboWp6SAv04".equals(Theme.getSelectedBackgroundSlug()) && !"qeZWES8rGVIEAAAARfWlK1lnfiI".equals(Theme.getSelectedBackgroundSlug())) {
            backgroundDrawable = Theme.getCurrentGradientWallpaper();
            if (backgroundDrawable == null) {
                backgroundDrawable = Theme.getCachedWallpaper();
            }
            if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                backgroundFrameLayout.setBackgroundColor(0x22000000);
            } else {
                backgroundFrameLayout.setBackgroundColor(0xbf000000);
            }
        } else {
            String selectedBackgroundSlug = Theme.getSelectedBackgroundSlug();
            if (Theme.DEFAULT_BACKGROUND_SLUG.equals(selectedBackgroundSlug) || Theme.isPatternWallpaper()) {
                backgroundFrameLayout.setBackgroundColor(0xff517c9e);
            } else {
                backgroundDrawable = Theme.getCachedWallpaper();
                if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                    backgroundFrameLayout.setBackgroundColor(0x22000000);
                } else if (backgroundDrawable != null) {
                    backgroundFrameLayout.setBackgroundColor(0xbf000000);
                } else {
                    backgroundFrameLayout.setBackgroundColor(0xff517c9e);
                }
            }
        }
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable drawable = (MotionBackgroundDrawable) backgroundDrawable;
            int[] colors = drawable.getColors();
            backgroundDrawable = new MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], false);
            if (drawable.hasPattern() && drawable.getIntensity() < 0) {
                backgroundFrameLayout.setBackgroundColor(0x7f000000);
            } else {
                backgroundFrameLayout.setBackgroundColor(0x22000000);
            }
            ((MotionBackgroundDrawable) backgroundDrawable).setParentView(backgroundFrameLayout);
        }

        passcodeTextView.setText(LocaleController.getString("EnterYourTelegramPasscode", R.string.EnterYourTelegramPasscode));

        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
            if (retryTextView.getVisibility() != VISIBLE) {
                numbersFrameLayout.setVisibility(VISIBLE);
            }
            passwordEditText.setVisibility(GONE);
            passwordEditText2.setVisibility(VISIBLE);
            checkImage.setVisibility(GONE);
            fingerprintImage.setVisibility(GONE);
        } else if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            passwordEditText.setFilters(new InputFilter[0]);
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            numbersFrameLayout.setVisibility(GONE);
            passwordEditText.setFocusable(true);
            passwordEditText.setFocusableInTouchMode(true);
            passwordEditText.setVisibility(VISIBLE);
            passwordEditText2.setVisibility(GONE);
            checkImage.setVisibility(VISIBLE);
            fingerprintImage.setVisibility(fingerprintView.getVisibility());
        }
        setVisibility(VISIBLE);
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setText("");
        passwordEditText2.eraseAllCharacters(false);
        if (animated) {
            setAlpha(0.0f);
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    setAlpha(1.0f);
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    imageView.setProgress(0);
                    imageView.playAnimation();
                    AndroidUtilities.runOnUIThread(() -> imageView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING), 350);
                    AnimatorSet animatorSet = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    int w = AndroidUtilities.displaySize.x;
                    int h = AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                    if (Build.VERSION.SDK_INT >= 21) {
                        double d1 = Math.sqrt((w - x) * (w - x) + (h - y) * (h - y));
                        double d2 = Math.sqrt(x * x + (h - y) * (h - y));
                        double d3 = Math.sqrt(x * x + y * y);
                        double d4 = Math.sqrt((w - x) * (w - x) + y * y);
                        double finalRadius = Math.max(Math.max(Math.max(d1, d2), d3), d4);

                        innerAnimators.clear();

                        for (int a = -1, N = numbersFrameLayout.getChildCount(); a < N; a++) {
                            View child;
                            if (a == -1) {
                                child = passcodeTextView;
                            } else {
                                child = numbersFrameLayout.getChildAt(a);
                            }
                            if (!(child instanceof TextView || child instanceof ImageView)) {
                                continue;
                            }
                            child.setScaleX(0.7f);
                            child.setScaleY(0.7f);
                            child.setAlpha(0.0f);
                            InnerAnimator innerAnimator = new InnerAnimator();
                            child.getLocationInWindow(pos);
                            int buttonX = pos[0] + child.getMeasuredWidth() / 2;
                            int buttonY = pos[1] + child.getMeasuredHeight() / 2;
                            innerAnimator.startRadius = (float) Math.sqrt((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY)) - AndroidUtilities.dp(40);

                            AnimatorSet animatorSetInner;
                            if (a != -1) {
                                animatorSetInner = new AnimatorSet();
                                animatorSetInner.playTogether(
                                        ObjectAnimator.ofFloat(child, View.SCALE_X, 1.0f),
                                        ObjectAnimator.ofFloat(child, View.SCALE_Y, 1.0f));
                                animatorSetInner.setDuration(140);
                                animatorSetInner.setInterpolator(new DecelerateInterpolator());
                            } else {
                                animatorSetInner = null;
                            }

                            innerAnimator.animatorSet = new AnimatorSet();
                            innerAnimator.animatorSet.playTogether(ObjectAnimator.ofFloat(child, View.SCALE_X, a == -1 ? 0.9f : 0.6f, a == -1 ? 1.0f : 1.04f),
                                    ObjectAnimator.ofFloat(child, View.SCALE_Y, a == -1 ? 0.9f : 0.6f, a == -1 ? 1.0f : 1.04f),
                                    ObjectAnimator.ofFloat(child, View.ALPHA, 0.0f, 1.0f));
                            innerAnimator.animatorSet.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (animatorSetInner != null) {
                                        animatorSetInner.start();
                                    }
                                }
                            });
                            innerAnimator.animatorSet.setDuration(a == -1 ? 232 : 200);
                            innerAnimator.animatorSet.setInterpolator(new DecelerateInterpolator());
                            innerAnimators.add(innerAnimator);
                        }

                        animators.add(ViewAnimationUtils.createCircularReveal(backgroundFrameLayout, x, y, 0, (float) finalRadius));
                        ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
                        animators.add(animator);
                        animator.addUpdateListener(animation -> {
                            float fraction = animation.getAnimatedFraction();
                            double rad = finalRadius * fraction;

                            for (int a = 0; a < innerAnimators.size(); a++) {
                                InnerAnimator innerAnimator = innerAnimators.get(a);
                                if (innerAnimator.startRadius > rad) {
                                    continue;
                                }
                                innerAnimator.animatorSet.start();
                                innerAnimators.remove(a);
                                a--;
                            }
                        });
                        animatorSet.setInterpolator(Easings.easeInOutQuad);
                        animatorSet.setDuration(498);
                    } else {
                        animators.add(ObjectAnimator.ofFloat(backgroundFrameLayout, View.ALPHA, 0.0f, 1.0f));
                        animatorSet.setDuration(350);
                    }
                    animatorSet.playTogether(animators);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (onShow != null) {
                                onShow.run();
                            }
                            if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD && retryTextView.getVisibility() != VISIBLE && passwordEditText != null) {
                                passwordEditText.requestFocus();
                                AndroidUtilities.showKeyboard(passwordEditText);
                            }
                        }
                    });
                    animatorSet.start();

                    AnimatorSet animatorSet2 = new AnimatorSet();
                    animatorSet2.setDuration(332);

                    float ix;
                    if (!AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        ix = (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? w / 2f : w) / 2 - AndroidUtilities.dp(30);
                    } else {
                        ix = w / 2f - AndroidUtilities.dp(29);
                    }

                    animatorSet2.playTogether(ObjectAnimator.ofFloat(imageView, View.TRANSLATION_X, x - AndroidUtilities.dp(29), ix),
                            ObjectAnimator.ofFloat(imageView, View.TRANSLATION_Y, y - AndroidUtilities.dp(29), imageY),
                            ObjectAnimator.ofFloat(imageView, View.SCALE_X, 0.5f, 1.0f),
                            ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 0.5f, 1.0f));
                    animatorSet2.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    animatorSet2.start();
                }
            });
            requestLayout();
        } else {
            setAlpha(1.0f);
            imageView.setScaleX(1.0f);
            imageView.setScaleY(1.0f);
            imageView.stopAnimation();
            imageView.getAnimatedDrawable().setCurrentFrame(38, false);
            if (onShow != null) {
                onShow.run();
            }
        }

        setOnTouchListener((v, event) -> true);
    }

    private void showFingerprintError(CharSequence error) {
        fingerprintImageView.setImageResource(R.drawable.ic_fingerprint_error);
        fingerprintStatusTextView.setText(error);
        fingerprintStatusTextView.setTextColor(0xfff4511e);
        Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        AndroidUtilities.shakeView(fingerprintStatusTextView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.displaySize.y - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);

        LayoutParams layoutParams;

        if (!AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            imageView.setTranslationX((SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? width / 2f : width) / 2 - AndroidUtilities.dp(29));

            layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.width = SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? width / 2 : width;
            layoutParams.height = AndroidUtilities.dp(140);
            layoutParams.topMargin = (height - AndroidUtilities.dp(140)) / 2 + (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? AndroidUtilities.dp(40) : 0);
            passwordFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = height;
            layoutParams.leftMargin = width / 2;
            layoutParams.topMargin = height - layoutParams.height + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            layoutParams.width = width / 2;
            numbersFrameLayout.setLayoutParams(layoutParams);
        } else {
            imageView.setTranslationX(width / 2f - AndroidUtilities.dp(29));

            int top = 0;
            int left = 0;
            if (AndroidUtilities.isTablet()) {
                if (width > AndroidUtilities.dp(498)) {
                    left = (width - AndroidUtilities.dp(498)) / 2;
                    width = AndroidUtilities.dp(498);
                }
                if (height > AndroidUtilities.dp(528)) {
                    top = (height - AndroidUtilities.dp(528)) / 2;
                    height = AndroidUtilities.dp(528);
                }
            }
            layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.height = height / 3 + (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? AndroidUtilities.dp(40) : 0);
            layoutParams.width = width;
            layoutParams.topMargin = top;
            layoutParams.leftMargin = left;
            passwordFrameLayout.setTag(top);
            passwordFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = height / 3 * 2;
            layoutParams.leftMargin = left;
            if (AndroidUtilities.isTablet()) {
                layoutParams.topMargin = height - layoutParams.height + top + AndroidUtilities.dp(20);
            } else {
                layoutParams.topMargin = height - layoutParams.height + top + (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? AndroidUtilities.dp(40) : 0);
            }
            layoutParams.width = width;
            numbersFrameLayout.setLayoutParams(layoutParams);
        }

        int sizeBetweenNumbersX = (layoutParams.width - AndroidUtilities.dp(50) * 3) / 4;
        int sizeBetweenNumbersY = (layoutParams.height - AndroidUtilities.dp(50) * 4) / 5;

        for (int a = 0; a < 12; a++) {
            LayoutParams layoutParams1;
            int num;
            if (a == 0) {
                num = 10;
            } else if (a == 10) {
                num = 11;
            } else if (a == 11) {
                num = 9;
            } else {
                num = a - 1;
            }
            int row = num / 3;
            int col = num % 3;
            int top;
            if (a < 10) {
                TextView textView = numberTextViews.get(a);
                TextView textView1 = lettersTextViews.get(a);
                layoutParams = (LayoutParams) textView.getLayoutParams();
                layoutParams1 = (LayoutParams) textView1.getLayoutParams();
                top = layoutParams1.topMargin = layoutParams.topMargin = sizeBetweenNumbersY + (sizeBetweenNumbersY + AndroidUtilities.dp(50)) * row;
                layoutParams1.leftMargin = layoutParams.leftMargin = sizeBetweenNumbersX + (sizeBetweenNumbersX + AndroidUtilities.dp(50)) * col;
                layoutParams1.topMargin += AndroidUtilities.dp(40);
                textView.setLayoutParams(layoutParams);
                textView1.setLayoutParams(layoutParams1);
            } else if (a == 10) {
                layoutParams = (LayoutParams) eraseView.getLayoutParams();
                top = layoutParams.topMargin = sizeBetweenNumbersY + (sizeBetweenNumbersY + AndroidUtilities.dp(50)) * row + AndroidUtilities.dp(8);
                layoutParams.leftMargin = sizeBetweenNumbersX + (sizeBetweenNumbersX + AndroidUtilities.dp(50)) * col;
                top -= AndroidUtilities.dp(8);
                eraseView.setLayoutParams(layoutParams);
            } else {
                layoutParams = (LayoutParams) fingerprintView.getLayoutParams();
                top = layoutParams.topMargin = sizeBetweenNumbersY + (sizeBetweenNumbersY + AndroidUtilities.dp(50)) * row + AndroidUtilities.dp(8);
                layoutParams.leftMargin = sizeBetweenNumbersX + (sizeBetweenNumbersX + AndroidUtilities.dp(50)) * col;
                top -= AndroidUtilities.dp(8);
                fingerprintView.setLayoutParams(layoutParams);
            }

            FrameLayout frameLayout = numberFrameLayouts.get(a);
            layoutParams1 = (LayoutParams) frameLayout.getLayoutParams();
            layoutParams1.topMargin = top - AndroidUtilities.dp(17);
            layoutParams1.leftMargin = layoutParams.leftMargin - AndroidUtilities.dp(25);
            frameLayout.setLayoutParams(layoutParams1);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int[] pos = new int[2];

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View rootView = getRootView();
        int usableViewHeight = rootView.getHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.getViewInset(rootView);
        getWindowVisibleDisplayFrame(rect);
        keyboardHeight = usableViewHeight - (rect.bottom - rect.top);

        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD && (AndroidUtilities.isTablet() || getContext().getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)) {
            int t = 0;
            if (passwordFrameLayout.getTag() != null) {
                t = (Integer) passwordFrameLayout.getTag();
            }
            LayoutParams layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.topMargin = t + layoutParams.height - keyboardHeight / 2 - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            passwordFrameLayout.setLayoutParams(layoutParams);
        }

        super.onLayout(changed, left, top, right, bottom);

        passcodeTextView.getLocationInWindow(pos);
        if (!AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            imageView.setTranslationY(imageY = pos[1] - AndroidUtilities.dp(100));
        } else {
            imageView.setTranslationY(imageY = pos[1] - AndroidUtilities.dp(100));
        }
    }
}
