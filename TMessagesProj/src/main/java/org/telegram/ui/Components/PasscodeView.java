/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.os.CancellationSignal;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Locale;

public class PasscodeView extends FrameLayout {

    public interface PasscodeViewDelegate {
        void didAcceptedPassword();
    }

    private class AnimatingTextView extends FrameLayout {

        private ArrayList<TextView> characterTextViews;
        private ArrayList<TextView> dotTextViews;
        private StringBuilder stringBuilder;
        private String DOT = "\u2022";
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
                addView(textView);
                LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
                layoutParams.width = AndroidUtilities.dp(50);
                layoutParams.height = AndroidUtilities.dp(50);
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                textView.setLayoutParams(layoutParams);
                characterTextViews.add(textView);

                textView = new TextView(context);
                textView.setTextColor(0xffffffff);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setAlpha(0);
                textView.setText(DOT);
                textView.setPivotX(AndroidUtilities.dp(25));
                textView.setPivotY(AndroidUtilities.dp(25));
                addView(textView);
                layoutParams = (LayoutParams) textView.getLayoutParams();
                layoutParams.width = AndroidUtilities.dp(50);
                layoutParams.height = AndroidUtilities.dp(50);
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                textView.setLayoutParams(layoutParams);
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
            animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, "translationY", AndroidUtilities.dp(20), 0));
            textView = dotTextViews.get(newPos);
            textView.setTranslationX(getXForTextView(newPos));
            textView.setAlpha(0);
            animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, "translationY", AndroidUtilities.dp(20), 0));

            for (int a = newPos + 1; a < 4; a++) {
                textView = characterTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
                }

                textView = dotTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
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
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
                    textView = dotTextViews.get(newPos);
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 1));
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 1));
                    animators.add(ObjectAnimator.ofFloat(textView, "alpha", 1));

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
                animators.add(ObjectAnimator.ofFloat(textView, "translationX", getXForTextView(a)));
                animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
                animators.add(ObjectAnimator.ofFloat(textView, "translationY", 0));
                textView = dotTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, "translationX", getXForTextView(a)));
                animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 1));
                animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 1));
                animators.add(ObjectAnimator.ofFloat(textView, "alpha", 1));
                animators.add(ObjectAnimator.ofFloat(textView, "translationY", 0));
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

            for (int a = deletingPos; a < 4; a++) {
                TextView textView = characterTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "translationY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "translationX", getXForTextView(a)));
                }

                textView = dotTextViews.get(a);
                if (textView.getAlpha() != 0) {
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "translationY", 0));
                    animators.add(ObjectAnimator.ofFloat(textView, "translationX", getXForTextView(a)));
                }
            }

            if (deletingPos == 0) {
                stringBuilder.deleteCharAt(deletingPos);
            }

            for (int a = 0; a < deletingPos; a++) {
                TextView textView = characterTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, "translationX", getXForTextView(a)));
                textView = dotTextViews.get(a);
                animators.add(ObjectAnimator.ofFloat(textView, "translationX", getXForTextView(a)));
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

                for (int a = 0; a < 4; a++) {
                    TextView textView = characterTextViews.get(a);
                    if (textView.getAlpha() != 0) {
                        animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                        animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                        animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
                    }

                    textView = dotTextViews.get(a);
                    if (textView.getAlpha() != 0) {
                        animators.add(ObjectAnimator.ofFloat(textView, "scaleX", 0));
                        animators.add(ObjectAnimator.ofFloat(textView, "scaleY", 0));
                        animators.add(ObjectAnimator.ofFloat(textView, "alpha", 0));
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

    private Drawable backgroundDrawable;
    private FrameLayout numbersFrameLayout;
    private ArrayList<TextView> numberTextViews;
    private ArrayList<TextView> lettersTextViews;
    private ArrayList<FrameLayout> numberFrameLayouts;
    private FrameLayout passwordFrameLayout;
    private ImageView eraseView;
    private EditTextBoldCursor passwordEditText;
    private AnimatingTextView passwordEditText2;
    private FrameLayout backgroundFrameLayout;
    private TextView passcodeTextView;
    private TextView retryTextView;
    private ImageView checkImage;
    private int keyboardHeight = 0;

    private CancellationSignal cancellationSignal;
    private ImageView fingerprintImageView;
    private TextView fingerprintStatusTextView;
    private boolean selfCancelled;
    private AlertDialog fingerprintDialog;

    private Rect rect = new Rect();

    private PasscodeViewDelegate delegate;

    private final static int id_fingerprint_textview = 1000;
    private final static int id_fingerprint_imageview = 1001;

    public PasscodeView(final Context context) {
        super(context);

        setWillNotDraw(false);
        setVisibility(GONE);

        backgroundFrameLayout = new FrameLayout(context);
        addView(backgroundFrameLayout);
        LayoutParams layoutParams = (LayoutParams) backgroundFrameLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        backgroundFrameLayout.setLayoutParams(layoutParams);

        passwordFrameLayout = new FrameLayout(context);
        addView(passwordFrameLayout);
        layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        passwordFrameLayout.setLayoutParams(layoutParams);

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setImageResource(R.drawable.passcode_logo);
        passwordFrameLayout.addView(imageView);
        layoutParams = (LayoutParams) imageView.getLayoutParams();
        if (AndroidUtilities.density < 1) {
            layoutParams.width = AndroidUtilities.dp(30);
            layoutParams.height = AndroidUtilities.dp(30);
        } else {
            layoutParams.width = AndroidUtilities.dp(40);
            layoutParams.height = AndroidUtilities.dp(40);
        }
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        layoutParams.bottomMargin = AndroidUtilities.dp(100);
        imageView.setLayoutParams(layoutParams);

        passcodeTextView = new TextView(context);
        passcodeTextView.setTextColor(0xffffffff);
        passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        passcodeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordFrameLayout.addView(passcodeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 62));

        retryTextView = new TextView(context);
        retryTextView.setTextColor(0xffffffff);
        retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        retryTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        retryTextView.setVisibility(INVISIBLE);
        addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        passwordEditText2 = new AnimatingTextView(context);
        passwordFrameLayout.addView(passwordEditText2);
        layoutParams = (FrameLayout.LayoutParams) passwordEditText2.getLayoutParams();
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.leftMargin = AndroidUtilities.dp(70);
        layoutParams.rightMargin = AndroidUtilities.dp(70);
        layoutParams.bottomMargin = AndroidUtilities.dp(6);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        passwordEditText2.setLayoutParams(layoutParams);

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
        passwordFrameLayout.addView(passwordEditText);
        layoutParams = (FrameLayout.LayoutParams) passwordEditText.getLayoutParams();
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.leftMargin = AndroidUtilities.dp(70);
        layoutParams.rightMargin = AndroidUtilities.dp(70);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        passwordEditText.setLayoutParams(layoutParams);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    processDone(false);
                    return true;
                }
                return false;
            }
        });
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (passwordEditText.length() == 4 && SharedConfig.passcodeType == 0) {
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
        passwordFrameLayout.addView(checkImage);
        layoutParams = (LayoutParams) checkImage.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(60);
        layoutParams.height = AndroidUtilities.dp(60);
        layoutParams.bottomMargin = AndroidUtilities.dp(4);
        layoutParams.rightMargin = AndroidUtilities.dp(10);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        checkImage.setLayoutParams(layoutParams);
        checkImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                processDone(false);
            }
        });

        FrameLayout lineFrameLayout = new FrameLayout(context);
        lineFrameLayout.setBackgroundColor(0x26ffffff);
        passwordFrameLayout.addView(lineFrameLayout);
        layoutParams = (LayoutParams) lineFrameLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(1);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        layoutParams.leftMargin = AndroidUtilities.dp(20);
        layoutParams.rightMargin = AndroidUtilities.dp(20);
        lineFrameLayout.setLayoutParams(layoutParams);

        numbersFrameLayout = new FrameLayout(context);
        addView(numbersFrameLayout);
        layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        numbersFrameLayout.setLayoutParams(layoutParams);

        lettersTextViews = new ArrayList<>(10);
        numberTextViews = new ArrayList<>(10);
        numberFrameLayouts = new ArrayList<>(10);
        for (int a = 0; a < 10; a++) {
            TextView textView = new TextView(context);
            textView.setTextColor(0xffffffff);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
            textView.setGravity(Gravity.CENTER);
            textView.setText(String.format(Locale.US, "%d", a));
            numbersFrameLayout.addView(textView);
            layoutParams = (LayoutParams) textView.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(50);
            layoutParams.height = AndroidUtilities.dp(50);
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            textView.setLayoutParams(layoutParams);
            numberTextViews.add(textView);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setTextColor(0x7fffffff);
            textView.setGravity(Gravity.CENTER);
            numbersFrameLayout.addView(textView);
            layoutParams = (LayoutParams) textView.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(50);
            layoutParams.height = AndroidUtilities.dp(20);
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            textView.setLayoutParams(layoutParams);
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
        numbersFrameLayout.addView(eraseView);
        layoutParams = (LayoutParams) eraseView.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(50);
        layoutParams.height = AndroidUtilities.dp(50);
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        eraseView.setLayoutParams(layoutParams);
        for (int a = 0; a < 11; a++) {
            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundResource(R.drawable.bar_selector_lock);
            frameLayout.setTag(a);
            if (a == 10) {
                frameLayout.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        passwordEditText.setText("");
                        passwordEditText2.eraseAllCharacters(true);
                        return true;
                    }
                });
            }
            frameLayout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    int tag = (Integer) v.getTag();
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
                            passwordEditText2.eraseLastCharacter();
                            break;
                    }
                    if (passwordEditText2.length() == 4) {
                        processDone(false);
                    }
                }
            });
            numberFrameLayouts.add(frameLayout);
        }
        for (int a = 10; a >= 0; a--) {
            FrameLayout frameLayout = numberFrameLayouts.get(a);
            numbersFrameLayout.addView(frameLayout);
            layoutParams = (LayoutParams) frameLayout.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(100);
            layoutParams.height = AndroidUtilities.dp(100);
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            frameLayout.setLayoutParams(layoutParams);
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
            if (SharedConfig.passcodeType == 0) {
                password = passwordEditText2.getString();
            } else if (SharedConfig.passcodeType == 1) {
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
                return;
            }
        }
        SharedConfig.badPasscodeTries = 0;
        passwordEditText.clearFocus();
        AndroidUtilities.hideKeyboard(passwordEditText);

        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.setDuration(200);
        AnimatorSet.playTogether(
                ObjectAnimator.ofFloat(this, "translationY", AndroidUtilities.dp(20)),
                ObjectAnimator.ofFloat(this, "alpha", AndroidUtilities.dp(0.0f)));
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.GONE);
            }
        });
        AnimatorSet.start();

        SharedConfig.appLocked = false;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
        setOnTouchListener(null);
        if (delegate != null) {
            delegate.didAcceptedPassword();
        }
    }

    private void shakeTextView(final float x, final int num) {
        if (num == 6) {
            return;
        }
        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(ObjectAnimator.ofFloat(passcodeTextView, "translationX", AndroidUtilities.dp(x)));
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
                AndroidUtilities.cancelRunOnUIThread(checkRunnable);
                AndroidUtilities.runOnUIThread(checkRunnable, 100);
            }
        } else {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            if (passwordFrameLayout.getVisibility() != VISIBLE) {
                retryTextView.setVisibility(INVISIBLE);
                passwordFrameLayout.setVisibility(VISIBLE);
                if (SharedConfig.passcodeType == 0) {
                    numbersFrameLayout.setVisibility(VISIBLE);
                } else if (SharedConfig.passcodeType == 1) {
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
            if (SharedConfig.passcodeType == 1) {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (retryTextView.getVisibility() != VISIBLE && passwordEditText != null) {
                            passwordEditText.requestFocus();
                            AndroidUtilities.showKeyboard(passwordEditText);
                        }
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

    private void checkFingerprint() {
        Activity parentActivity = (Activity) getContext();
        if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && SharedConfig.useFingerprint && !ApplicationLoader.mainInterfacePaused) {
            try {
                if (fingerprintDialog != null && fingerprintDialog.isShowing()) {
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                if (fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints()) {
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
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (cancellationSignal != null) {
                                selfCancelled = true;
                                cancellationSignal.cancel();
                                cancellationSignal = null;
                            }
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
                            if (!selfCancelled) {
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

    public void onShow() {
        checkRetryTextView();
        Activity parentActivity = (Activity) getContext();
        if (SharedConfig.passcodeType == 1) {
            if (retryTextView.getVisibility() != VISIBLE && passwordEditText != null) {
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
        if (retryTextView.getVisibility() != VISIBLE) {
            checkFingerprint();
        }
        if (getVisibility() == View.VISIBLE) {
            return;
        }
        setAlpha(1.0f);
        setTranslationY(0);
        if (Theme.isCustomTheme()) {
            backgroundDrawable = Theme.getCachedWallpaper();
            backgroundFrameLayout.setBackgroundColor(0xbf000000);
        } else {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            int selectedBackground = preferences.getInt("selectedBackground", 1000001);
            if (selectedBackground == 1000001) {
                backgroundFrameLayout.setBackgroundColor(0xff517c9e);
            } else {
                backgroundDrawable = Theme.getCachedWallpaper();
                if (backgroundDrawable != null) {
                    backgroundFrameLayout.setBackgroundColor(0xbf000000);
                } else {
                    backgroundFrameLayout.setBackgroundColor(0xff517c9e);
                }
            }
        }

        passcodeTextView.setText(LocaleController.getString("EnterYourPasscode", R.string.EnterYourPasscode));


        if (SharedConfig.passcodeType == 0) {
            if (retryTextView.getVisibility() != VISIBLE) {
                numbersFrameLayout.setVisibility(VISIBLE);
            }
            passwordEditText.setVisibility(GONE);
            passwordEditText2.setVisibility(VISIBLE);
            checkImage.setVisibility(GONE);
        } else if (SharedConfig.passcodeType == 1) {
            passwordEditText.setFilters(new InputFilter[0]);
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            numbersFrameLayout.setVisibility(GONE);
            passwordEditText.setFocusable(true);
            passwordEditText.setFocusableInTouchMode(true);
            passwordEditText.setVisibility(VISIBLE);
            passwordEditText2.setVisibility(GONE);
            checkImage.setVisibility(VISIBLE);
        }
        setVisibility(VISIBLE);
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setText("");
        passwordEditText2.eraseAllCharacters(false);

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
    }

    private void showFingerprintError(CharSequence error) {
        fingerprintImageView.setImageResource(R.drawable.ic_fingerprint_error);
        fingerprintStatusTextView.setText(error);
        fingerprintStatusTextView.setTextColor(0xfff4511e);
        Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        AndroidUtilities.shakeView(fingerprintStatusTextView, 2, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.displaySize.y - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);

        LayoutParams layoutParams;

        if (!AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.width = SharedConfig.passcodeType == 0 ? width / 2 : width;
            layoutParams.height = AndroidUtilities.dp(140);
            layoutParams.topMargin = (height - AndroidUtilities.dp(140)) / 2;
            passwordFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = height;
            layoutParams.leftMargin = width / 2;
            layoutParams.topMargin = height - layoutParams.height;
            layoutParams.width = width / 2;
            numbersFrameLayout.setLayoutParams(layoutParams);
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
            }
            layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.height = height / 3;
            layoutParams.width = width;
            layoutParams.topMargin = top;
            layoutParams.leftMargin = left;
            passwordFrameLayout.setTag(top);
            passwordFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = height / 3 * 2;
            layoutParams.leftMargin = left;
            layoutParams.topMargin = height - layoutParams.height + top;
            layoutParams.width = width;
            numbersFrameLayout.setLayoutParams(layoutParams);
        }

        int sizeBetweenNumbersX = (layoutParams.width - AndroidUtilities.dp(50) * 3) / 4;
        int sizeBetweenNumbersY = (layoutParams.height - AndroidUtilities.dp(50) * 4) / 5;

        for (int a = 0; a < 11; a++) {
            LayoutParams layoutParams1;
            int num;
            if (a == 0) {
                num = 10;
            } else if (a == 10) {
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
                textView.setLayoutParams(layoutParams);
                textView1.setLayoutParams(layoutParams1);
            } else {
                layoutParams = (LayoutParams) eraseView.getLayoutParams();
                top = layoutParams.topMargin = sizeBetweenNumbersY + (sizeBetweenNumbersY + AndroidUtilities.dp(50)) * row + AndroidUtilities.dp(8);
                layoutParams.leftMargin = sizeBetweenNumbersX + (sizeBetweenNumbersX + AndroidUtilities.dp(50)) * col;
                top -= AndroidUtilities.dp(8);
                eraseView.setLayoutParams(layoutParams);
            }

            FrameLayout frameLayout = numberFrameLayouts.get(a);
            layoutParams1 = (LayoutParams) frameLayout.getLayoutParams();
            layoutParams1.topMargin = top - AndroidUtilities.dp(17);
            layoutParams1.leftMargin = layoutParams.leftMargin - AndroidUtilities.dp(25);
            frameLayout.setLayoutParams(layoutParams1);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View rootView = getRootView();
        int usableViewHeight = rootView.getHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.getViewInset(rootView);
        getWindowVisibleDisplayFrame(rect);
        keyboardHeight = usableViewHeight - (rect.bottom - rect.top);

        if (SharedConfig.passcodeType == 1 && (AndroidUtilities.isTablet() || getContext().getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)) {
            int t = 0;
            if (passwordFrameLayout.getTag() != null) {
                t = (Integer) passwordFrameLayout.getTag();
            }
            LayoutParams layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.topMargin = t + layoutParams.height - keyboardHeight / 2 - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            passwordFrameLayout.setLayoutParams(layoutParams);
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (backgroundDrawable != null) {
            if (backgroundDrawable instanceof ColorDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
            } else {
                float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                float scaleY = (float) (getMeasuredHeight() + keyboardHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                float scale = scaleX < scaleY ? scaleY : scaleX;
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
    }
}
