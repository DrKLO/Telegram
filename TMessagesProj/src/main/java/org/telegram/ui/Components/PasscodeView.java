package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
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
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.QrShare.QrGlass;
import org.telegram.ui.QrShare.QrRender;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import me.vkryl.core.reference.ReferenceList;

public class PasscodeView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final static float BACKGROUND_SPRING_STIFFNESS = 300f;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didGenerateFingerprintKeyPair) {
            checkFingerprintButton();
            if ((boolean) args[0] && SharedConfig.appLocked) {
                checkFingerprint();
            }
        } else if (id == NotificationCenter.passcodeDismissed && isAppLock()) {
            if (args[0] != this) {
                setVisibility(GONE);
            }
        }
    }

    public interface PasscodeViewDelegate {
        void didAcceptedPassword(PasscodeView view);
    }

    private final int BUTTON_X_MARGIN = 28;
    private final int BUTTON_Y_MARGIN = 16;
    private final int BUTTON_SIZE = 60;

    private void checkTitle() {
        final boolean isEmpty = passwordEditText2 == null || passwordEditText2.length() > 0;
        if (numbersTitleContainer != null) {
            numbersTitleContainer.animate().cancel();
            numbersTitleContainer.animate().alpha(isEmpty ? 0f : 1f).scaleX(isEmpty ? .8f : 1f).scaleY(isEmpty ? .8f : 1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
        }
    }

    private class AnimatingTextView extends FrameLayout {

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
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
                textView.setGravity(Gravity.CENTER);
                textView.setAlpha(0);
                textView.setPivotX(AndroidUtilities.dp(25));
                textView.setPivotY(AndroidUtilities.dp(25));
                addView(textView, LayoutHelper.createFrame(50, 50, Gravity.TOP | Gravity.LEFT));
                characterTextViews.add(textView);

                textView = new TextView(context);
                textView.setTextColor(0xffffffff);
                textView.setTypeface(AndroidUtilities.bold());
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
            return (getMeasuredWidth() - stringBuilder.length() * dp(30)) / 2 + pos * dp(30) - dp(10);
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
            animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, dp(20), 0));
            textView = dotTextViews.get(newPos);
            textView.setTranslationX(getXForTextView(newPos));
            textView.setAlpha(0);
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_X, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.SCALE_Y, 0, 1));
            animators.add(ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, dp(20), 0));

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

            checkTitle();
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

            checkTitle();

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

            checkTitle();
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
    private Drawable backgroundDarkDrawable;
    private FrameLayout numbersTitleContainer;
    private TextView subtitleView;
    private FrameLayout numbersContainer;
    public FrameLayout numbersFrameLayout;
    private ArrayList<TextView> numberTextViews;
    private ArrayList<TextView> lettersTextViews;
    private ArrayList<FrameLayout> numberFrameLayouts;
    private FrameLayout passwordFrameLayout;
    private ImageView eraseView;
    private PasscodeButton fingerprintView;
    private EditTextBoldCursor passwordEditText;
    private AnimatingTextView passwordEditText2;
    private FrameLayout backgroundFrameLayout;
    private int backgroundFrameLayoutColor;
    private TextView passcodeTextView;
    private TextView retryTextView;
    private ImageView checkImage;
    private ImageView fingerprintImage;
    private View border;
    private int keyboardHeight = 0;

    // glass of the number buttons, as on the QR screen: the background keeps a low-res copy of itself
    // that is used directly as the blur source
    private static final int GLASS_WIDTH = 54;
    private final BlurredBackgroundSourceBitmap glassSource = new BlurredBackgroundSourceBitmap();
    private final BlurredBackgroundDrawableViewFactory glassFactory = new BlurredBackgroundDrawableViewFactory(glassSource);
    private final ReferenceList<View> glassViews = new ReferenceList<>();
    private @Nullable Bitmap glassBitmap;
    private @Nullable Canvas glassCanvas;

    private boolean selfCancelled;

    private int imageY;

    private RLottieImageView imageView;

    private Rect rect = new Rect();

    private PasscodeViewDelegate delegate;
    private final String title;
    private final String subtitle;
    private boolean dismissing;
    private AnimatorSet showAnimator;
    private AnimatorSet imageAnimator;
    private ValueAnimator hideAnimator;

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
        this(context, LocaleController.getString(R.string.UnlockToUse), null);
    }

    public PasscodeView(final Context context, String title, String subtitle) {
        super(context);
        this.title = title;
        this.subtitle = subtitle;

        setWillNotDraw(false);
        setVisibility(GONE);

        backgroundFrameLayout = new FrameLayout(context) {

            private Paint paint = new Paint();

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (w > 0 && h > 0) {
                    glassBitmap = Bitmap.createBitmap(GLASS_WIDTH, Math.max(1, Math.round(GLASS_WIDTH * h / (float) w)), Bitmap.Config.ARGB_8888);
                    glassCanvas = new Canvas(glassBitmap);
                    glassSource.setBitmap(glassBitmap);
                    glassSource.setParentSize(w, h, 0);
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                drawBackground(canvas);
                // the copy follows every frame of the background, e.g. the gradient animation
                if (glassBitmap != null && glassCanvas != null && getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
                    glassBitmap.eraseColor(0);
                    glassCanvas.save();
                    glassCanvas.scale(glassBitmap.getWidth() / (float) getMeasuredWidth(), glassBitmap.getHeight() / (float) getMeasuredHeight());
                    drawBackground(glassCanvas);
                    glassCanvas.restore();
                    glassFactory.invalidateAllLinkedViews();
                }
            }

            private void drawBackground(Canvas canvas) {
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
        glassFactory.setSourceRootView(new ViewPositionWatcher(backgroundFrameLayout), backgroundFrameLayout);
        glassFactory.setLinkedViewsRef(glassViews);

        imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.passcode_lock, 58, 58);
        imageView.setAutoRepeat(false);
        addView(imageView, LayoutHelper.createFrame(58, 58, Gravity.LEFT | Gravity.TOP));

        passwordFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.addView(passwordFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        passcodeTextView = new TextView(context);
        passcodeTextView.setTextColor(0xffffffff);
        passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18.33f);
        passcodeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        passcodeTextView.setTypeface(AndroidUtilities.bold());
        passcodeTextView.setAlpha(0f);
        passwordFrameLayout.addView(passcodeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 128));

        retryTextView = new TextView(context);
        retryTextView.setTextColor(0xffffffff);
        retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        retryTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        retryTextView.setVisibility(INVISIBLE);
        backgroundFrameLayout.addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        passwordEditText2 = new AnimatingTextView(context);
        passwordFrameLayout.addView(passwordEditText2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 46));

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
        passwordEditText.setCursorSize(dp(32));
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
        passwordFrameLayout.addView(checkImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 4));
        checkImage.setContentDescription(LocaleController.getString(R.string.Done));
        checkImage.setOnClickListener(v -> processDone(false));

        fingerprintImage = new ImageView(context);
        fingerprintImage.setImageResource(R.drawable.fingerprint);
        fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
        fingerprintImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(fingerprintImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT, 10, 0, 0, 4));
        fingerprintImage.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
        fingerprintImage.setOnClickListener(v -> checkFingerprint());

        border = new View(context);
        border.setBackgroundColor(0x30FFFFFF);
        passwordFrameLayout.addView(border, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        numbersContainer = new FrameLayout(context);
        numbersContainer.setClipChildren(false); // the outer keys' rims reach past the keypad's own bounds
        backgroundFrameLayout.addView(numbersContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        numbersFrameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if ((getParent() instanceof View)) {
                    int parentHeight = ((View) getParent()).getHeight();
                    int height = getHeight();
                    float scale = Math.min((float) parentHeight / height, 1f);

                    setPivotX(getWidth() / 2f);
                    setPivotY(((LayoutParams) getLayoutParams()).gravity == Gravity.CENTER ? getHeight() / 2f : 0);
                    setScaleX(scale);
                    setScaleY(scale);
                }
            }
        };
        // the rim of the glass goes beyond the buttons
        numbersFrameLayout.setClipChildren(false);
        numbersContainer.addView(numbersFrameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        numbersTitleContainer = new FrameLayout(context);
        numbersFrameLayout.addView(numbersTitleContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setText(title);
        numbersTitleContainer.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(0xFFFFFFFF);
        subtitleView.setText(subtitle);
        numbersTitleContainer.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 23, 0, 0));

        numberFrameLayouts = new ArrayList<>(10);
        for (int a = 0; a < 12; a++) {
            PasscodeButton frameLayout = new PasscodeButton(context);
            ScaleStateListAnimator.apply(frameLayout, .15f, 1.5f);
            frameLayout.setTag(a);
            if (a == 11) {
                frameLayout.setBackground(createGlass(frameLayout));
                frameLayout.setForeground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0, 0x26ffffff));
                frameLayout.setImage(R.drawable.filled_clear);
                frameLayout.setOnLongClickListener(v -> {
                    passwordEditText.setText("");
                    passwordEditText2.eraseAllCharacters(true);
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        ((MotionBackgroundDrawable) backgroundDrawable).switchToPrevPosition(true);
                    }
                    return true;
                });
                frameLayout.setContentDescription(LocaleController.getString(R.string.AccDescrBackspace));
                setNextFocus(frameLayout, R.id.passcode_btn_0);
            } else if (a == 10) {
                fingerprintView = frameLayout;
                frameLayout.setBackground(createGlass(frameLayout));
                frameLayout.setForeground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0, 0x26ffffff));
                frameLayout.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
                frameLayout.setImage(R.drawable.fingerprint);
                setNextFocus(frameLayout, R.id.passcode_btn_1);
            } else {
                frameLayout.setBackground(createGlass(frameLayout));
                frameLayout.setForeground(Theme.createSimpleSelectorRoundRectDrawable(dp(30), 0, 0x26ffffff));
                frameLayout.setContentDescription(a + "");
                frameLayout.setNum(a);
                if (a == 0) {
                    setNextFocus(frameLayout, R.id.passcode_btn_backspace);
                } else if (a == 9) {
                    if (hasFingerprint()) {
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
                if (!pinShown || dismissing)
                    return;
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
                        checkFingerprint();
                        break;
                    case 11:
                        erased = passwordEditText2.eraseLastCharacter();
                        break;
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
                // after the background step of the last digit, so a wrong code can turn the background back smoothly
                if (passwordEditText2.length() == 4) {
                    processDone(false);
                }
            });
            numberFrameLayouts.add(frameLayout);
        }
        for (int a = 11; a >= 0; a--) {
            FrameLayout frameLayout = numberFrameLayouts.get(a);
            numbersFrameLayout.addView(frameLayout, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.TOP | Gravity.LEFT));
        }
        checkFingerprintButton();
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
        backgroundAnimationSpring.addUpdateListener((animation, value, velocity) -> motionBackgroundDrawable.updateAnimation());
        backgroundAnimationSpring.start();
    }

    /**
     * Turns the background back after a wrong code. A running step is not cut short: jumping to its end made
     * the whole screen jerk. The steps waiting behind it are dropped, they belong to the erased digits.
     */
    private void rotateBackgroundBack(MotionBackgroundDrawable drawable) {
        backgroundSpringQueue.clear();
        backgroundSpringNextQueue.clear();
        if (backgroundAnimationSpring != null && backgroundAnimationSpring.isRunning()) {
            backgroundSpringQueue.offer(() -> drawable.rotatePreview(true));
            backgroundSpringNextQueue.offer(false);
        } else {
            drawable.rotatePreview(true);
        }
    }

    /**
     * The glass of the QR screen controls (Big group): the blurred background, the white fill, the top sheen and the baked
     * rim, no shadow. The buttons are measured with {@link #BUTTON_SIZE}, so it is a circle.
     */
    private Drawable createGlass(View view) {
        final BlurredBackgroundProviderBuilder provider = new BlurredBackgroundProviderBuilder(null)
            .setBackgroundColor((r, isDark) -> QrGlass.controlFill());
        if (!QrRender.hasNodes()) {
            // the rim is baked only from Android 10 (its crescents need Plus): the plain strokes before
            provider.setStrokeColorTop(0x4DFFFFFF, 0x4DFFFFFF)
                .setStrokeColorBottom(0x1AFFFFFF, 0x1AFFFFFF)
                .setStrokeWidth(dpf2(1), dpf2(2 / 3f));
        }
        // the position is taken on screen: the keypad is scaled down on short screens
        return new QrGlassButton(glassFactory.create(view, provider.build(), true).setRadius(dp(BUTTON_SIZE / 2)));
    }

    /** The blurred background with the QR glass's sheen and rim over it (the rim reaches ~2dp past the bounds). */
    private static class QrGlassButton extends Drawable implements Drawable.Callback {
        private final Drawable blur;
        private final RectF bounds = new RectF();
        private int alpha = 255;

        QrGlassButton(Drawable blur) {
            this.blur = blur;
            blur.setCallback(this);
        }

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            blur.setBounds(b);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            blur.draw(canvas);
            bounds.set(getBounds());
            final float radius = Math.min(bounds.width(), bounds.height()) / 2f;
            final float a = alpha / 255f;
            QrGlass.drawSheenRound(canvas, bounds, radius, false, a);
            QrGlass.drawRim(canvas, bounds, radius, QrGlass.RIM_BIG, a);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            blur.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            blur.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }
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

    protected boolean isAppLock() {
        return true;
    }

    protected void processDone(boolean fingerprint) {
        if (dismissing || getVisibility() != VISIBLE) return;
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
                    rotateBackgroundBack((MotionBackgroundDrawable) backgroundDrawable);
                }
                return;
            }
        }
        dismissing = true;
        onPause();
        SharedConfig.badPasscodeTries = 0;
        passwordEditText.clearFocus();
        AndroidUtilities.hideKeyboard(passwordEditText);

        if (isAppLock() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && FingerprintController.isKeyReady() && FingerprintController.checkDeviceFingerprintsChanged()) {
            FingerprintController.deleteInvalidKey();
        }

        if (isAppLock()) {
            SharedConfig.appLocked = false;
        }
        SharedConfig.saveConfig();
        if (isAppLock()) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
        }
        setOnTouchListener(null);
        if (delegate != null) {
            delegate.didAcceptedPassword(this);
        }

        hide(true);
    }

    /** Cancels a confirmation view without accepting the passcode or unlocking the app. */
    public void dismiss() {
        if (isAppLock() || dismissing) return;
        dismissing = true;
        onPause();
        passwordEditText.clearFocus();
        AndroidUtilities.hideKeyboard(passwordEditText);
        hide(false);
    }

    private void hide(boolean accepted) {
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        if (imageAnimator != null) {
            imageAnimator.cancel();
            imageAnimator = null;
        }
        if (accepted) {
            imageView.getAnimatedDrawable().setCustomEndFrame(71);
            imageView.getAnimatedDrawable().setCurrentFrame(37, false);
            imageView.playAnimation();
        } else {
            imageView.stopAnimation();
        }

        AndroidUtilities.runOnUIThread(() -> {
            ValueAnimator va = hideAnimator = ValueAnimator.ofFloat(shownT, 0);
            va.addUpdateListener(anm -> {
                shownT = (float) anm.getAnimatedValue();
                onAnimationUpdate(shownT);
                setAlpha(shownT);
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    hideAnimator = null;
                    passwordEditText.setText("");
                    passwordEditText2.eraseAllCharacters(false);
                    setVisibility(View.GONE);
                    onAnimationUpdate(shownT = 0f);
                    setAlpha(0f);
                    onHidden();
                }
            });
            va.setDuration(420);
            va.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            va.start();
        });
    }

    private float shownT;
    protected void onAnimationUpdate(float open) {

    }

    protected void onHidden() {

    }

    private int shiftDp = -12;
    private void shakeTextView(final float x, final int num) {
        if (num == 6) {
            return;
        }
        AndroidUtilities.shakeViewSpring(numbersTitleContainer, shiftDp = -shiftDp);
    }

    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkRetryTextView();
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
                retryTextView.setText(LocaleController.formatString(R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)));
                lastValue = value;
            }
            if (retryTextView.getVisibility() != VISIBLE) {
                retryTextView.setVisibility(VISIBLE);
                passwordFrameLayout.setVisibility(INVISIBLE);
                showPin(false);
                AndroidUtilities.hideKeyboard(passwordEditText);
            }
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        } else {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            if (retryTextView.getVisibility() == VISIBLE) {
                retryTextView.setVisibility(INVISIBLE);
                passwordFrameLayout.setVisibility(VISIBLE);
                showPin(true);
                if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }
        }
    }

    private void onPasscodeError() {
        BotWebViewVibrationEffect.NOTIFICATION_ERROR.vibrate();
        shakeTextView(2, 0);
    }

    int resumeCount = 0;
    public void onResume() {
        if (dismissing) return;
        checkRetryTextView();
        if (retryTextView.getVisibility() != VISIBLE) {
            if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!dismissing && isAttachedToWindow() && retryTextView.getVisibility() != VISIBLE && passwordEditText != null) {
                        passwordEditText.requestFocus();
                        AndroidUtilities.showKeyboard(passwordEditText);
                    }
                }, 200);
            }
            checkFingerprint();
        }
    }

    public boolean onBackPressed() {
        if (keyboardNotifier != null && keyboardNotifier.keyboardVisible()) {
            AndroidUtilities.hideKeyboard(passwordEditText);
            return false;
        }
        return true;
    }

    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(checkRunnable);
    }

    private KeyboardNotifier keyboardNotifier;
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (isAppLock()) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.passcodeDismissed);
        }

        if (keyboardNotifier == null && getParent() instanceof View) {
            keyboardNotifier = new KeyboardNotifier((View) getParent(), keyboardHeight -> {
                if (getContext() == null) return;
                final boolean landscape = getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                keyboardHeight -= AndroidUtilities.navigationBarHeight;
                if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
                    passwordFrameLayout.animate().translationY(keyboardHeight <= dp(20) ? 0 : (getHeight() - keyboardHeight) / 2f - passwordFrameLayout.getHeight() / (landscape ? 1f : 2f) - passwordFrameLayout.getTop()).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    imageView.animate().alpha(keyboardHeight <= dp(20) ? 1f : 0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                }
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        onPause();
        dismissing = true;
        if (showAnimator != null) showAnimator.cancel();
        if (imageAnimator != null) imageAnimator.cancel();
        if (hideAnimator != null) {
            hideAnimator.removeAllListeners();
            hideAnimator.cancel();
            hideAnimator = null;
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.passcodeDismissed);
    }

    private boolean pinShown = true;
    private ValueAnimator pinAnimator;
    private void showPin(boolean show) {
        if (pinAnimator != null) {
            pinAnimator.cancel();
        }
        pinShown = show;
        pinAnimator = ValueAnimator.ofFloat(numbersFrameLayout.getAlpha(), show ? 1f : 0f);
        pinAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            numbersFrameLayout.setScaleX(AndroidUtilities.lerp(.8f, 1f, t));
            numbersFrameLayout.setScaleY(AndroidUtilities.lerp(.8f, 1f, t));
            numbersFrameLayout.setAlpha(AndroidUtilities.lerp(0f, 1f, t));

            passcodeTextView.setScaleX(AndroidUtilities.lerp(1f, .9f, t));
            passcodeTextView.setScaleY(AndroidUtilities.lerp(1f, .9f, t));
            passcodeTextView.setAlpha(AndroidUtilities.lerp(1f, 0f, t));

            passwordEditText2.setAlpha(AndroidUtilities.lerp(0f, 1f, t));
        });
        pinAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                final float t = show ? 1f : 0f;
                numbersFrameLayout.setScaleX(AndroidUtilities.lerp(.8f, 1f, t));
                numbersFrameLayout.setScaleY(AndroidUtilities.lerp(.8f, 1f, t));
                numbersFrameLayout.setAlpha(AndroidUtilities.lerp(0f, 1f, t));

                passcodeTextView.setScaleX(AndroidUtilities.lerp(1f, .9f, t));
                passcodeTextView.setScaleY(AndroidUtilities.lerp(1f, .9f, t));
                passcodeTextView.setAlpha(AndroidUtilities.lerp(1f, 0f, t));

                passwordEditText2.setAlpha(AndroidUtilities.lerp(0f, 1f, t));
            }
        });
        pinAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        pinAnimator.setDuration(320);
        pinAnimator.start();
    }

    protected void checkFingerprint() {
        if (dismissing) return;
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        Activity parentActivity = AndroidUtilities.findActivity(getContext());
        if (parentActivity != null && fingerprintView.getVisibility() == VISIBLE && !ApplicationLoader.mainInterfacePaused && (!(parentActivity instanceof LaunchActivity) || ((LaunchActivity) parentActivity).allowShowFingerprintDialog(this))) {
            try {
                if (BiometricManager.from(getContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged()) {
                    final Executor executor = ContextCompat.getMainExecutor(getContext());
                    BiometricPrompt prompt = new BiometricPrompt(LaunchActivity.instance, executor, new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errMsgId, @NonNull CharSequence errString) {
                            FileLog.d("PasscodeView onAuthenticationError " + errMsgId + " \"" + errString + "\"");
                            showPin(true);
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            FileLog.d("PasscodeView onAuthenticationSucceeded");
                            processDone(true);
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            FileLog.d("PasscodeView onAuthenticationFailed");
                            showPin(true);
                        }
                    });
                    final BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle(LocaleController.getString(R.string.UnlockToUse))
                            .setNegativeButtonText(LocaleController.getString(R.string.UsePIN))
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            .build();
                    prompt.authenticate(promptInfo);
                    showPin(false);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void onShow(boolean fingerprint, boolean animated) {
        onShow(fingerprint, animated, -1, -1, null, null);
    }

    protected boolean hasFingerprint() {
        Activity parentActivity = AndroidUtilities.findActivity(getContext());
        if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && SharedConfig.useFingerprintLock) {
            try {
                FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                return fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints() && FingerprintController.isKeyReady() && !FingerprintController.checkDeviceFingerprintsChanged();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    private void checkFingerprintButton() {
        boolean hasFingerprint = hasFingerprint();
        fingerprintView.setVisibility(hasFingerprint ? VISIBLE : GONE);
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {
            fingerprintImage.setVisibility(fingerprintView.getVisibility());
        }
        subtitleView.setText(subtitle != null ? subtitle : LocaleController.getString(
                SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD ? R.string.EnterPassword :
                hasFingerprint ? R.string.EnterPINorFingerprint : R.string.EnterPIN));
    }

    public void onShow(boolean fingerprint, boolean animated, int x, int y, Runnable onShow, Runnable onStart) {
        if (getVisibility() != VISIBLE) dismissing = false;
        checkFingerprintButton();
        checkRetryTextView();
        Activity parentActivity = AndroidUtilities.findActivity(getContext());
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
                    AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                }
            }
        }
        if (getVisibility() == View.VISIBLE) {
            return;
        }
        setTranslationY(0);
        boolean saturateColors = false;
        backgroundDrawable = null;
        backgroundFrameLayoutColor = 0;
        if (Theme.getCachedWallpaper() instanceof MotionBackgroundDrawable) {
            saturateColors = !Theme.isCurrentThemeDark();
            backgroundDrawable = Theme.getCachedWallpaper();
            backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xbf000000);
        } else if (Theme.isCustomTheme() && !"CJz3BZ6YGEYBAAAABboWp6SAv04".equals(Theme.getSelectedBackgroundSlug()) && !"qeZWES8rGVIEAAAARfWlK1lnfiI".equals(Theme.getSelectedBackgroundSlug())) {
            backgroundDrawable = Theme.getCurrentGradientWallpaper();
            if (backgroundDrawable == null) {
                backgroundDrawable = Theme.getCachedWallpaper();
            }
            if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x22000000);
            } else {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xbf000000);
            }
        } else {
            String selectedBackgroundSlug = Theme.getSelectedBackgroundSlug();
            if (Theme.DEFAULT_BACKGROUND_SLUG.equals(selectedBackgroundSlug) || Theme.isPatternWallpaper()) {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xff517c9e);
            } else {
                backgroundDrawable = Theme.getCachedWallpaper();
                if (backgroundDrawable instanceof BackgroundGradientDrawable) {
                    backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x22000000);
                } else if (backgroundDrawable != null) {
                    backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xbf000000);
                } else {
                    backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0xff517c9e);
                }
            }
        }
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable drawable = (MotionBackgroundDrawable) backgroundDrawable;
            int[] colors = drawable.getColors();
            if (saturateColors) {
                int[] newColors = new int[colors.length];
                for (int i = 0; i < colors.length; ++i) {
                    newColors[i] = Theme.adaptHSV(colors[i], +.14f, 0.0f);
                }
                colors = newColors;
            }
            backgroundDrawable = new MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], false);
            if (drawable.hasPattern() && drawable.getIntensity() < 0) {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x7f000000);
            } else {
                backgroundFrameLayout.setBackgroundColor(backgroundFrameLayoutColor = 0x22000000);
            }
            ((MotionBackgroundDrawable) backgroundDrawable).setParentView(backgroundFrameLayout);
        }

        passcodeTextView.setText(isAppLock() ? LocaleController.getString(R.string.AppLocked) : title);

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
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (dismissing || !isAttachedToWindow()) return;
                    setAlpha(1.0f);
                    imageView.getAnimatedDrawable().setCurrentFrame(0, false);
                    imageView.getAnimatedDrawable().setCustomEndFrame(37);
                    imageView.playAnimation();
                    showPin(true);
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            imageView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignore) {}
                    }, 350);
                    AnimatorSet animatorSet = showAnimator = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    int w = isAppLock() ? AndroidUtilities.displaySize.x : getMeasuredWidth();
                    int h = isAppLock() ? AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) : getMeasuredHeight();
                    if (Build.VERSION.SDK_INT >= 21) {
                        double d1 = Math.sqrt((w - x) * (w - x) + (h - y) * (h - y));
                        double d2 = Math.sqrt(x * x + (h - y) * (h - y));
                        double d3 = Math.sqrt(x * x + y * y);
                        double d4 = Math.sqrt((w - x) * (w - x) + y * y);
                        double finalRadius = Math.max(Math.max(Math.max(d1, d2), d3), d4);

                        innerAnimators.clear();

                        for (int a = 0, N = numbersFrameLayout.getChildCount(); a < N; a++) {
                            View child = numbersFrameLayout.getChildAt(a);
//                            if (!(child instanceof TextView || child instanceof ImageView)) {
//                                continue;
//                            }
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
                            innerAnimator.animatorSet.playTogether(
                                ObjectAnimator.ofFloat(child, View.SCALE_X, a == -1 ? 0.9f : 0.6f, a == -1 ? 1.0f : 1.04f),
                                ObjectAnimator.ofFloat(child, View.SCALE_Y, a == -1 ? 0.9f : 0.6f, a == -1 ? 1.0f : 1.04f),
                                ObjectAnimator.ofFloat(child, View.ALPHA, 0.0f, 1.0f)
                            );
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

//                        animators.add(ViewAnimationUtils.createCircularReveal(backgroundFrameLayout, x, y, 0, (float) finalRadius));
                        animators.add(ObjectAnimator.ofFloat(backgroundFrameLayout, View.ALPHA, 0.0f, 1.0f));
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
                        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        animatorSet.setDuration(500);
                    } else {
                        animatorSet.setDuration(350);
                    }
                    ValueAnimator va = ValueAnimator.ofFloat(shownT, 1);
                    va.addUpdateListener(anm -> onAnimationUpdate(shownT = (float) anm.getAnimatedValue()));
                    va.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!dismissing) onAnimationUpdate(shownT = 1f);
                        }
                    });
                    va.setDuration(420);
                    va.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    animators.add(va);

                    animatorSet.playTogether(animators);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (dismissing) return;
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

                    AnimatorSet animatorSet2 = imageAnimator = new AnimatorSet();
                    animatorSet2.setDuration(332);

                    float ix;
                    if (!AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        ix = (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? w / 2f : w) / 2 - dp(30);
                    } else {
                        ix = w / 2f - dp(29);
                    }

                    animatorSet2.playTogether(
                        ObjectAnimator.ofFloat(imageView, View.TRANSLATION_X, x - dp(29), ix),
                        ObjectAnimator.ofFloat(imageView, View.TRANSLATION_Y, y - dp(29), imageY),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_X, 0.5f, 1.0f),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 0.5f, 1.0f)
                    );
                    animatorSet2.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    animatorSet2.start();
                }
            });
            requestLayout();
        } else {
            setAlpha(1.0f);
            onAnimationUpdate(shownT = 1f);
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
        BotWebViewVibrationEffect.NOTIFICATION_ERROR.vibrate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.displaySize.y - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
        if (!isAppLock()) height = MeasureSpec.getSize(heightMeasureSpec);

        LayoutParams layoutParams;

        int sizeBetweenNumbersX = dp(BUTTON_X_MARGIN);
        int sizeBetweenNumbersY = dp(BUTTON_Y_MARGIN);
        int buttonSize = dp(BUTTON_SIZE);

        final boolean landscape = !AndroidUtilities.isTablet() && getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (border != null) {
            border.setVisibility(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD ? VISIBLE : GONE);
        }

        if (landscape) {
            imageView.setTranslationX((SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? width / 2f : width) / 2 - dp(29));

            layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.width = SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? width / 2 : width;
            layoutParams.height = dp(180);
            layoutParams.topMargin = (height - dp(140)) / 2 + (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? dp(40) : 0);
            passwordFrameLayout.setLayoutParams(layoutParams);

            layoutParams = (LayoutParams) numbersContainer.getLayoutParams();
            layoutParams.height = height;
            layoutParams.leftMargin = width / 2;
            layoutParams.topMargin = height - layoutParams.height + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            layoutParams.width = width / 2;
            numbersContainer.setLayoutParams(layoutParams);

            int cols = 3;
            int rows = 4;
            layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = dp(82) + buttonSize * rows + sizeBetweenNumbersY * Math.max(0, rows - 1);
            layoutParams.width =  buttonSize * cols + sizeBetweenNumbersX * Math.max(0, cols - 1);
            layoutParams.gravity = Gravity.CENTER;
            numbersFrameLayout.setLayoutParams(layoutParams);
        } else {
            imageView.setTranslationX(width / 2f - dp(29));

            int top = AndroidUtilities.statusBarHeight;
            int left = 0;
            if (AndroidUtilities.isTablet()) {
                if (width > dp(498)) {
                    left = (width - dp(498)) / 2;
                    width = dp(498);
                }
                if (height > dp(528)) {
                    top = (height - dp(528)) / 2;
                    height = dp(528);
                }
            }
            layoutParams = (LayoutParams) passwordFrameLayout.getLayoutParams();
            layoutParams.height = height / 3 + (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? dp(40) : 0);
            layoutParams.width = width;
            layoutParams.topMargin = top;
            layoutParams.leftMargin = left;
            passwordFrameLayout.setTag(top);
            passwordFrameLayout.setLayoutParams(layoutParams);
            int passwordTop = layoutParams.topMargin + layoutParams.height;

            int cols = 3;
            int rows = 4;
            layoutParams = (LayoutParams) numbersFrameLayout.getLayoutParams();
            layoutParams.height = dp(82) + buttonSize * rows + sizeBetweenNumbersY * Math.max(0, rows - 1);
            layoutParams.width =  buttonSize * cols + sizeBetweenNumbersX * Math.max(0, cols - 1);
            if (AndroidUtilities.isTablet()) {
                layoutParams.gravity = Gravity.CENTER;
            } else {
                layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            }
            numbersFrameLayout.setLayoutParams(layoutParams);

            int buttonHeight = height - layoutParams.height;
            layoutParams = (LayoutParams) numbersContainer.getLayoutParams();
            layoutParams.leftMargin = left;
            if (AndroidUtilities.isTablet()) {
                layoutParams.topMargin = (height - buttonHeight) / 2;
            } else {
                layoutParams.topMargin = passwordTop;
            }
            layoutParams.width = width;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            numbersContainer.setLayoutParams(layoutParams);
        }

        int headerMargin = dp(landscape ? 52 : 82);
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
            FrameLayout frameLayout = numberFrameLayouts.get(a);
            layoutParams1 = (LayoutParams) frameLayout.getLayoutParams();
            layoutParams1.topMargin = headerMargin + (buttonSize + sizeBetweenNumbersY) * row;
            layoutParams1.leftMargin = (buttonSize + sizeBetweenNumbersX) * col;
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
            imageView.setTranslationY(imageY = pos[1] - dp(100));
        } else {
            imageView.setTranslationY(imageY = pos[1] - dp(100));
        }
    }

    private static class BlurBackground extends Drawable {

        private final int[] pos = new int[2];
        private final View view;
        private final int rad;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap bitmap;
        private final BitmapShader shader;
        private final Matrix matrix = new Matrix();
        private final float scale;

        public BlurBackground(View view, Drawable drawable, int overlay, int rad) {
            this.view = view;

            if (drawable != null) {
                final float a = (float) AndroidUtilities.displaySize.x / AndroidUtilities.displaySize.y;
                final int w = (int) (50 * a);
                final int h = (int) (50 / a);
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                scale = (float) AndroidUtilities.displaySize.x / w;
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                canvas.scale(1f / scale, 1f / scale);
                drawable.draw(canvas);
                canvas.drawColor(overlay);
            } else {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                new Canvas(bitmap).drawColor(0x60000000);
                scale = 1f;
            }

            ColorMatrix colorMatrix = new ColorMatrix();
            if (Theme.isCurrentThemeDark()) {
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, +.15f);
            } else {
                colorMatrix.setSaturation(1.25f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, -.1f);
            }

            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.reset();
            matrix.postScale(scale, scale);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

            this.rad = rad;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            view.getLocationOnScreen(pos);
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(-pos[0], -pos[1]);
            shader.setLocalMatrix(matrix);
            AndroidUtilities.rectTmp.set(getBounds());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public static class PasscodeButton extends FrameLayout {

        private final ImageView imageView;
        private final TextView textView1, textView2;

        public PasscodeButton(@NonNull Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.fingerprint);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            textView1 = new TextView(context);
            textView1.setTypeface(AndroidUtilities.bold());
            textView1.setTextColor(0xffffffff);
            textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            textView1.setGravity(Gravity.CENTER);
            addView(textView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, -5.33f, 0, 0));

            textView2 = new TextView(context);
            textView2.setTypeface(AndroidUtilities.bold());
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            textView2.setTextColor(0x7fffffff);
            textView2.setGravity(Gravity.CENTER);
            addView(textView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 14, 0, 0));
        }

        public void setImage(int resId) {
            imageView.setVisibility(View.VISIBLE);
            textView1.setVisibility(View.GONE);
            textView2.setVisibility(View.GONE);
            imageView.setImageResource(resId);
        }

        public void setNum(int num) {
            imageView.setVisibility(View.GONE);
            textView1.setVisibility(View.VISIBLE);
            textView2.setVisibility(View.VISIBLE);
            textView1.setText("" + num);
            textView2.setText(letter(num));
        }

        public static String letter(int num) {
            switch (num) {
                case 0: return "+";
                case 2: return "ABC";
                case 3: return "DEF";
                case 4: return "GHI";
                case 5: return "JKL";
                case 6: return "MNO";
                case 7: return "PQRS";
                case 8: return "TUV";
                case 9: return "WXYZ";
            }
            return "";
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
        }
    }
}
