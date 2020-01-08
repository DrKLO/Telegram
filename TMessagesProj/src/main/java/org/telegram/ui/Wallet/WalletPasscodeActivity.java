/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TonController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;
import java.util.Locale;

import javax.crypto.Cipher;

import androidx.annotation.IdRes;
import drinkless.org.ton.TonApi;

public class WalletPasscodeActivity extends BaseFragment {

    private class AnimatingTextView extends FrameLayout {

        private ArrayList<TextView> characterTextViews;
        private ArrayList<TextView> dotTextViews;
        private StringBuilder stringBuilder;
        private AnimatorSet currentAnimation;
        private Runnable dotRunnable;
        private RectF rect = new RectF();
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int maxCharactrers;
        private boolean addedNew;

        public AnimatingTextView(Context context, int max) {
            super(context);
            setWillNotDraw(false);
            maxCharactrers = max;

            characterTextViews = new ArrayList<>(4);
            dotTextViews = new ArrayList<>(4);
            stringBuilder = new StringBuilder(4);

            for (int a = 0; a < 4; a++) {
                addTextView();
            }
        }

        private void addTextView() {
            TextView textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            textView.setGravity(Gravity.CENTER);
            textView.setAlpha(0);
            addView(textView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT));
            characterTextViews.add(textView);

            textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            textView.setGravity(Gravity.CENTER);
            textView.setAlpha(0);
            textView.setText("\u2022");
            textView.setPivotX(AndroidUtilities.dp(25));
            textView.setPivotY(AndroidUtilities.dp(25));
            addView(textView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT));
            dotTextViews.add(textView);
        }

        private int getXForTextView(int pos) {
            return (getMeasuredWidth() - stringBuilder.length() * AndroidUtilities.dp(20)) / 2 + pos * AndroidUtilities.dp(20) - AndroidUtilities.dp(10);
        }

        public void appendCharacter(String c) {
            if (stringBuilder.length() == maxCharactrers) {
                return;
            }
            if (stringBuilder.length() == characterTextViews.size()) {
                addTextView();
                addedNew = true;
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

            for (int a = newPos + 1, N = characterTextViews.size(); a < N; a++) {
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

        public void eraseLastCharacter() {
            if (stringBuilder.length() == 0) {
                return;
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

            for (int a = deletingPos, N = characterTextViews.size(); a < N; a++) {
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

                for (int a = 0, N = characterTextViews.size(); a < N; a++) {
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
                for (int a = 0, N = characterTextViews.size(); a < N; a++) {
                    characterTextViews.get(a).setAlpha(0);
                    dotTextViews.get(a).setAlpha(0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (!addedNew) {
                if (dotRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(dotRunnable);
                    dotRunnable = null;
                }
                if (currentAnimation != null) {
                    currentAnimation.cancel();
                    currentAnimation = null;
                }
                for (int a = 0, N = characterTextViews.size(); a < N; a++) {
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
            } else {
                addedNew = false;
            }
            super.onLayout(changed, left, top, right, bottom);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            paint.setColor(Theme.getColor(Theme.key_wallet_grayBackground));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), paint);
        }
    }

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
            R.id.passcode_btn_done
    };

    @SuppressWarnings("FieldCanBeLocal")
    private class PasscodeView extends FrameLayout {

        private FrameLayout numbersFrameLayout;
        private FrameLayout passwordFrameLayout;
        private ImageView eraseView;
        private AnimatingTextView passwordEditText;
        private TextView passcodeTextView;
        private TextView passcodeInfoTextView;
        private TextView retryTextView;
        private RLottieImageView lottieImageView;
        private ImageView checkImage;

        private ArrayList<TextView> numberTextViews;
        private ArrayList<TextView> lettersTextViews;
        private ArrayList<FrameLayout> numberFrameLayouts;

        private Rect rect = new Rect();

        public PasscodeView(final Context context, int maxCharacters) {
            super(context);

            setBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackground));
            setOnTouchListener((v, event) -> true);

            passwordFrameLayout = new FrameLayout(context);
            addView(passwordFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 250, Gravity.TOP | Gravity.LEFT));

            lottieImageView = new RLottieImageView(context);
            lottieImageView.setAutoRepeat(true);
            lottieImageView.setScaleType(ImageView.ScaleType.CENTER);
            lottieImageView.setAnimation(R.raw.wallet_lock, 120, 120);
            passwordFrameLayout.addView(lottieImageView, LayoutHelper.createFrame(120, 120, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
            lottieImageView.playAnimation();

            passcodeTextView = new TextView(context);
            passcodeTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            passcodeTextView.setText(LocaleController.getString("WalletEnterPasscode", R.string.WalletEnterPasscode));
            passcodeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            passwordFrameLayout.addView(passcodeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 120, 0, 0));

            passcodeInfoTextView = new TextView(context);
            passcodeInfoTextView.setTextColor(Theme.getColor(Theme.key_wallet_grayText2));
            passcodeInfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            passcodeInfoTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            if (userConfig.tonPasscodeType == 0) {
                passcodeInfoTextView.setText(LocaleController.formatString("WalletPasscodeLength", R.string.WalletPasscodeLength, LocaleController.formatPluralString("Digits", 4)));
            } else if (userConfig.tonPasscodeType == 1) {
                passcodeInfoTextView.setText(LocaleController.formatString("WalletPasscodeLength", R.string.WalletPasscodeLength, LocaleController.formatPluralString("Digits", 6)));
            } else {
                passcodeInfoTextView.setVisibility(GONE);
            }
            passwordFrameLayout.addView(passcodeInfoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 150, 0, 0));

            retryTextView = new TextView(context);
            retryTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            retryTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            retryTextView.setVisibility(INVISIBLE);
            addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            passwordEditText = new AnimatingTextView(context, maxCharacters);
            passwordFrameLayout.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 200, 17, 0));

            numbersFrameLayout = new FrameLayout(context);
            addView(numbersFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

            lettersTextViews = new ArrayList<>(10);
            numberTextViews = new ArrayList<>(10);
            numberFrameLayouts = new ArrayList<>(10);
            for (int a = 0; a < 10; a++) {
                TextView textView = new TextView(context);
                textView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setText(String.format(Locale.US, "%d", a));
                textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                numbersFrameLayout.addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
                numberTextViews.add(textView);

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                textView.setTextColor(Theme.getColor(Theme.key_wallet_grayText2));
                textView.setGravity(Gravity.CENTER);
                numbersFrameLayout.addView(textView, LayoutHelper.createFrame(50, 20, Gravity.TOP | Gravity.LEFT));
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
            eraseView.setImageResource(R.drawable.wallet_clear);
            eraseView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_wallet_whiteText), PorterDuff.Mode.MULTIPLY));
            numbersFrameLayout.addView(eraseView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));

            checkImage = new ImageView(context);
            checkImage.setScaleType(ImageView.ScaleType.CENTER);
            checkImage.setImageResource(R.drawable.passcode_check);
            checkImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_wallet_whiteText), PorterDuff.Mode.MULTIPLY));
            checkImage.setVisibility(userConfig.tonPasscodeType == 2 ? VISIBLE : GONE);
            numbersFrameLayout.addView(checkImage, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT, 0, 0, 10, 4));

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
                    frameLayout.setContentDescription(LocaleController.getString("Done", R.string.Done));
                    setNextFocus(frameLayout, R.id.passcode_btn_1);
                } else if (a == 10) {
                    frameLayout.setOnLongClickListener(v -> {
                        passwordEditText.eraseAllCharacters(true);
                        return true;
                    });
                    frameLayout.setContentDescription(LocaleController.getString("AccDescrBackspace", R.string.AccDescrBackspace));
                    setNextFocus(frameLayout, R.id.passcode_btn_0);
                } else {
                    frameLayout.setContentDescription(a + "");
                    if (a == 0) {
                        setNextFocus(frameLayout, R.id.passcode_btn_done);
                    } else if (a == 9) {
                        setNextFocus(frameLayout, R.id.passcode_btn_backspace);
                    } else {
                        setNextFocus(frameLayout, ids[a + 1]);
                    }
                }
                frameLayout.setId(ids[a]);
                frameLayout.setOnClickListener(v -> {
                    if (!allowEditing) {
                        return;
                    }
                    int tag = (Integer) v.getTag();
                    switch (tag) {
                        case 0:
                            passwordEditText.appendCharacter("0");
                            break;
                        case 1:
                            passwordEditText.appendCharacter("1");
                            break;
                        case 2:
                            passwordEditText.appendCharacter("2");
                            break;
                        case 3:
                            passwordEditText.appendCharacter("3");
                            break;
                        case 4:
                            passwordEditText.appendCharacter("4");
                            break;
                        case 5:
                            passwordEditText.appendCharacter("5");
                            break;
                        case 6:
                            passwordEditText.appendCharacter("6");
                            break;
                        case 7:
                            passwordEditText.appendCharacter("7");
                            break;
                        case 8:
                            passwordEditText.appendCharacter("8");
                            break;
                        case 9:
                            passwordEditText.appendCharacter("9");
                            break;
                        case 10:
                            passwordEditText.eraseLastCharacter();
                            break;
                        case 11:
                            processDone();
                            break;
                    }
                    if (userConfig.tonPasscodeType != 2) {
                        if (passwordEditText.length() == (userConfig.tonPasscodeType == 0 ? 4 : 6)) {
                            processDone();
                        }
                    }
                });
                numberFrameLayouts.add(frameLayout);
            }
            for (int a = 11; a >= 0; a--) {
                FrameLayout frameLayout = numberFrameLayouts.get(a);
                numbersFrameLayout.addView(frameLayout, LayoutHelper.createFrame(64, 64, Gravity.TOP | Gravity.LEFT));
            }
        }

        private void setNextFocus(View view, @IdRes int nextId) {
            view.setNextFocusForwardId(nextId);
            if (Build.VERSION.SDK_INT >= 22) {
                view.setAccessibilityTraversalBefore(nextId);
            }
        }

        public void onWrongPasscode() {
            increaseBadPasscodeTries();
            if (userConfig.tonPasscodeRetryInMs > 0) {
                checkRetryTextView();
            }
            passwordEditText.eraseAllCharacters(true);
            onPasscodeError();
        }

        public void onGoodPasscode(boolean hide) {
            userConfig.tonBadPasscodeTries = 0;
            userConfig.saveConfig(false);
            if (hide) {
                swipeBackEnabled = false;
                actionBar.setBackButtonDrawable(null);
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
                setOnTouchListener(null);
            }
        }

        private void processDone() {
            if (userConfig.tonPasscodeRetryInMs > 0) {
                return;
            }
            String password = passwordEditText.getString();
            if (password.length() == 0) {
                onPasscodeError();
                return;
            }
            checkPasscode(password);
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
            if (currentTime > userConfig.tonLastUptimeMillis) {
                userConfig.tonPasscodeRetryInMs -= (currentTime - userConfig.tonLastUptimeMillis);
                if (userConfig.tonPasscodeRetryInMs < 0) {
                    userConfig.tonPasscodeRetryInMs = 0;
                }
            }
            userConfig.tonLastUptimeMillis = currentTime;
            userConfig.saveConfig(false);
            if (userConfig.tonPasscodeRetryInMs > 0) {
                int value = Math.max(1, (int) Math.ceil(userConfig.tonPasscodeRetryInMs / 1000.0));
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
                    AndroidUtilities.cancelRunOnUIThread(checkRunnable);
                    AndroidUtilities.runOnUIThread(checkRunnable, 100);
                }
            } else {
                AndroidUtilities.cancelRunOnUIThread(checkRunnable);
                if (passwordFrameLayout.getVisibility() != VISIBLE) {
                    retryTextView.setVisibility(INVISIBLE);
                    passwordFrameLayout.setVisibility(VISIBLE);
                    numbersFrameLayout.setVisibility(VISIBLE);
                }
            }
        }

        public void onResume() {
            checkRetryTextView();
        }

        public void onPause() {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
        }

        public void onShow() {
            checkRetryTextView();
            if (retryTextView.getVisibility() != VISIBLE) {
                numbersFrameLayout.setVisibility(VISIBLE);
            }
            passwordEditText.eraseAllCharacters(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int statusBarHeight = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            int height = AndroidUtilities.displaySize.y - statusBarHeight;

            LayoutParams layoutParams;

            if (!AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
                layoutParams.width = width / 2;
                layoutParams.topMargin = (height - layoutParams.height) / 2 + statusBarHeight;

                layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
                layoutParams.height = height;
                layoutParams.leftMargin = width / 2;
                layoutParams.topMargin = statusBarHeight;
                layoutParams.width = width / 2;
            } else {
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
                } else {
                    top = ActionBar.getCurrentActionBarHeight() + statusBarHeight;
                }
                layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
                layoutParams.width = width;
                layoutParams.topMargin = top;
                layoutParams.leftMargin = left;
                int h = layoutParams.height + layoutParams.topMargin;

                layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
                layoutParams.height = height - h;
                layoutParams.leftMargin = left;
                layoutParams.topMargin = height - layoutParams.height + statusBarHeight;
                layoutParams.width = width;
            }

            int sizeBetweenNumbersX = (layoutParams.width - AndroidUtilities.dp(50) * 3) / 4;
            int sizeBetweenNumbersY = (layoutParams.height - AndroidUtilities.dp(50) * 4) / 5;

            for (int a = 0; a < 12; a++) {
                LayoutParams layoutParams1;
                int num;
                if (a == 0) {
                    num = 10;
                } else if (a == 10) {
                    num = 9;
                } else if (a == 11) {
                    num = 11;
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
                } else {
                    View view = a == 10 ? eraseView : checkImage;
                    layoutParams = (LayoutParams) view.getLayoutParams();
                    top = layoutParams.topMargin = sizeBetweenNumbersY + (sizeBetweenNumbersY + AndroidUtilities.dp(50)) * row;
                    layoutParams.leftMargin = sizeBetweenNumbersX + (sizeBetweenNumbersX + AndroidUtilities.dp(50)) * col;
                }

                FrameLayout frameLayout = numberFrameLayouts.get(a);
                layoutParams1 = (LayoutParams) frameLayout.getLayoutParams();
                layoutParams1.topMargin = top + AndroidUtilities.dp(1);
                layoutParams1.leftMargin = layoutParams.leftMargin - AndroidUtilities.dp(7);
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private void onPasscodeError() {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            shakeTextView(2, 0);
        }

        private void increaseBadPasscodeTries() {
            userConfig.tonBadPasscodeTries++;
            if (userConfig.tonBadPasscodeTries >= 3) {
                switch (userConfig.tonBadPasscodeTries) {
                    case 3:
                        userConfig.tonPasscodeRetryInMs = 5000;
                        break;
                    case 4:
                        userConfig.tonPasscodeRetryInMs = 10000;
                        break;
                    case 5:
                        userConfig.tonPasscodeRetryInMs = 15000;
                        break;
                    case 6:
                        userConfig.tonPasscodeRetryInMs = 20000;
                        break;
                    case 7:
                        userConfig.tonPasscodeRetryInMs = 25000;
                        break;
                    default:
                        userConfig.tonPasscodeRetryInMs = 30000;
                        break;
                }
                userConfig.tonLastUptimeMillis = SystemClock.elapsedRealtime();
            }
        }
    }

    public static final int TYPE_PASSCODE_SEND = 0;
    public static final int TYPE_PASSCODE_CHANGE = 1;
    public static final int TYPE_PASSCODE_EXPORT = 2;
    public static final int TYPE_NO_PASSCODE_SEND = 3;

    private PasscodeView passcodeView;
    private TextView continueButton;
    private String fromWallet;
    private String toWallet;
    private long sendingAmount;
    private String sendingMessage;
    private UserConfig userConfig = getUserConfig();
    private int currentType;
    private boolean allowEditing = true;
    private boolean hasWalletInBack;
    private TextView titleTextView;
    private TextView descriptionText;
    private Cipher sendingCipher;
    private boolean sendingFinished;
    private boolean failedToOpenFinished;

    public WalletPasscodeActivity(int type) {
        super();
        currentType = type;
    }

    public WalletPasscodeActivity(boolean passcode, Cipher cipher, String fromAddress, String toAddress, long amount, String message, boolean hasWallet) {
        this(passcode ? TYPE_PASSCODE_SEND : TYPE_NO_PASSCODE_SEND);
        fromWallet = fromAddress;
        toWallet = toAddress;
        sendingAmount = amount;
        sendingMessage = message;
        hasWalletInBack = hasWallet;
        sendingCipher = cipher;
        if (!passcode) {
            checkPasscode(null);
        }
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return false;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (continueButton != null && Build.VERSION.SDK_INT >= 21) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) continueButton.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.statusBarHeight;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundDrawable(null);
        actionBar.setTitleColor(Theme.getColor(Theme.key_wallet_whiteText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_wallet_whiteText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackgroundSelector), false);
        actionBar.setAddToContainer(false);
        actionBar.setOnTouchListener(null);
        actionBar.setOnClickListener(null);
        actionBar.setClickable(false);
        actionBar.setFocusable(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && swipeBackEnabled) {
                    finishFragment();
                }
            }
        });
        return actionBar;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (Build.VERSION.SDK_INT >= 23 && AndroidUtilities.allowScreenCapture()) {
            AndroidUtilities.setFlagSecure(this, false);
        }
    }

    @Override
    public View createView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frameLayout;

        continueButton = new TextView(context);
        continueButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
        continueButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        continueButton.setText(LocaleController.getString("WalletClose", R.string.WalletClose));
        continueButton.setGravity(Gravity.CENTER_VERTICAL);
        continueButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        continueButton.setAlpha(0.0f);
        continueButton.setVisibility(View.VISIBLE);
        actionBar.addView(continueButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT, 0, 0, 22, 0));
        continueButton.setOnClickListener(v -> {
            getTonController().cancelShortPoll();
            if (hasWalletInBack) {
                finishFragment();
            } else {
                presentFragment(new WalletActivity(), true);
            }
        });

        FrameLayout container = new FrameLayout(context);
        frameLayout.addView(container, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(R.raw.wallet_money, 130, 130);
        imageView.playAnimation();
        container.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        titleTextView.setText(LocaleController.getString("WalletSendingGrams", R.string.WalletSendingGrams));
        container.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 130, 0, 0));

        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        descriptionText.setText(LocaleController.getString("WalletSendingGramsInfo", R.string.WalletSendingGramsInfo));
        container.addView(descriptionText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 200, 0, 0));

        if (currentType != TYPE_NO_PASSCODE_SEND) {
            int maxCharacters;
            if (userConfig.tonPasscodeType == 0) {
                maxCharacters = 4;
            } else if (userConfig.tonPasscodeType == 1) {
                maxCharacters = 6;
            } else {
                maxCharacters = 32;
            }
            passcodeView = new PasscodeView(context, maxCharacters);
            frameLayout.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            passcodeView.onShow();
        }

        frameLayout.addView(actionBar);

        if (Build.VERSION.SDK_INT >= 23 && AndroidUtilities.allowScreenCapture()) {
            AndroidUtilities.setFlagSecure(this, true);
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (passcodeView != null) {
            passcodeView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (passcodeView != null) {
            passcodeView.onPause();
        }
    }

    public void checkPasscode(String passcode) {
        allowEditing = false;
        if (currentType == TYPE_PASSCODE_CHANGE) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.show();
            getTonController().prepareForPasscodeChange(passcode, () -> {
                progressDialog.dismiss();
                passcodeView.onGoodPasscode(false);
                WalletCreateActivity fragment = new WalletCreateActivity(WalletCreateActivity.TYPE_SET_PASSCODE);
                fragment.setChangingPasscode();
                presentFragment(fragment, true);
            }, (text, error) -> {
                allowEditing = true;
                progressDialog.dismiss();
                if ("PASSCODE_INVALID".equals(text)) {
                    passcodeView.onWrongPasscode();
                } else {
                    AlertsCreator.showSimpleAlert(this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + (error != null ? error.message : text));
                }
            });
        } else if (currentType == TYPE_PASSCODE_EXPORT) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.show();
            getTonController().getSecretWords(passcode, null, (words) -> {
                passcodeView.onGoodPasscode(false);
                progressDialog.dismiss();
                WalletCreateActivity fragment = new WalletCreateActivity(WalletCreateActivity.TYPE_24_WORDS);
                fragment.setSecretWords(words);
                presentFragment(fragment, true);
            }, (text, error) -> {
                allowEditing = true;
                progressDialog.dismiss();
                if ("PASSCODE_INVALID".equals(text)) {
                    passcodeView.onWrongPasscode();
                } else {
                    AlertsCreator.showSimpleAlert(this, LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + (error != null ? error.message : text));
                }
            });
        } else if (currentType == TYPE_PASSCODE_SEND || currentType == TYPE_NO_PASSCODE_SEND) {
            trySendGrams(passcode, null);
        }
    }

    private void trySendGrams(String passcode, TonApi.InputKey key) {
        AlertDialog progressDialog;
        if (currentType == TYPE_PASSCODE_SEND && key == null) {
            progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.show();
        } else {
            progressDialog = null;
        }
        getTonController().sendGrams(passcode, sendingCipher, key, fromWallet, toWallet, sendingAmount, sendingMessage, () -> {
            passcodeView.onGoodPasscode(true);
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        }, () -> {
            continueButton.setVisibility(View.VISIBLE);
            continueButton.animate().alpha(1.0f).setDuration(180).start();
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            getTonController().scheduleShortPoll();
        }, () -> {
            sendingFinished = true;
            openFinishedFragment();
        }, (inputKey) -> {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("WalletSendWarningTitle", R.string.WalletSendWarningTitle));
            builder.setMessage(LocaleController.getString("WalletSendWarningText", R.string.WalletSendWarningText));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> finishFragment());
            builder.setPositiveButton(LocaleController.getString("WalletSendWarningSendAnyway", R.string.WalletSendWarningSendAnyway), (dialog, which) -> trySendGrams(passcode, inputKey));
            showDialog(builder.create());
        }, (text, error) -> {
            allowEditing = true;
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            if ("PASSCODE_INVALID".equals(text)) {
                passcodeView.onWrongPasscode();
            } else if (error != null && error.message.startsWith("NOT_ENOUGH_FUNDS")) {
                AlertDialog.Builder builder = AlertsCreator.createSimpleAlert(getParentActivity(), LocaleController.getString("WalletInsufficientGramsTitle", R.string.WalletInsufficientGramsTitle), LocaleController.getString("WalletInsufficientGramsText", R.string.WalletInsufficientGramsText));
                showDialog(builder.create(), dialog -> finishFragment());
            } else {
                AlertDialog.Builder builder = AlertsCreator.createSimpleAlert(getParentActivity(), LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + (error != null ? error.message : text));
                showDialog(builder.create(), dialog -> finishFragment());
            }
        });
    }

    private void openFinishedFragment() {
        WalletCreateActivity fragment = new WalletCreateActivity(WalletCreateActivity.TYPE_SEND_DONE);
        String str = LocaleController.formatString("WalletSendDoneText", R.string.WalletSendDoneText, TonController.formatCurrency(sendingAmount));
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(str);
        stringBuilder.append("\n\n");
        int start = str.length();
        stringBuilder.append(toWallet.substring(0, toWallet.length() / 2)).append('\n').append(toWallet.substring(toWallet.length() / 2));
        stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmono.ttf")), start, stringBuilder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        fragment.setSendText(stringBuilder, hasWalletInBack);
        if (!presentFragment(fragment, true)) {
            failedToOpenFinished = true;
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (failedToOpenFinished && isOpen && !backward && (currentType == TYPE_PASSCODE_SEND || currentType == TYPE_NO_PASSCODE_SEND) && sendingFinished) {
            AndroidUtilities.runOnUIThread(this::openFinishedFragment);
        }
    }

    @Override
    public boolean onBackPressed() {
        return swipeBackEnabled;
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarWhiteSelector),

                new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(descriptionText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6)
        };
    }
}
