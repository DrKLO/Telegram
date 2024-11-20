package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.IUpdateButton;

import java.io.File;

public class UpdateButton extends IUpdateButton {

    private AnimatorSet animator;
    private RadialProgress2 icon;
    private TextView textView;

    @Keep
    public UpdateButton(Context context) {
        super(context);

        setWillNotDraw(false);
        setVisibility(View.INVISIBLE);
        setTranslationY(dp(48));
        if (Build.VERSION.SDK_INT >= 21) {
            setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        }
        setOnClickListener(v -> {
            if (!SharedConfig.isAppUpdateAvailable()) return;
            Activity activity = AndroidUtilities.findActivity(getContext());
            if (activity == null) return;
            AndroidUtilities.openForView(SharedConfig.pendingAppUpdate.document, true, activity);
        });

        icon = new RadialProgress2(this);
        icon.setColors(0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff);
        icon.setCircleRadius(dp(11));
        icon.setAsMini();
        icon.setIcon(MediaActionDrawable.ICON_UPDATE, true, false);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setText(LocaleController.getString(org.telegram.messenger.R.string.AppUpdateNow).toUpperCase());
        textView.setTextColor(0xffffffff);
        textView.setPadding(dp(30), 0, 0, 0);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));
    }


    private Paint paint = new Paint();
    private Matrix matrix = new Matrix();
    private LinearGradient updateGradient;
    private int lastGradientWidth;

    @Override
    public void draw(Canvas canvas) {
        if (updateGradient != null) {
            paint.setColor(0xffffffff);
            paint.setShader(updateGradient);
            updateGradient.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
            icon.setBackgroundGradientDrawable(updateGradient);
            icon.draw(canvas);
        }
        super.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastGradientWidth != width) {
            updateGradient = new LinearGradient(0, 0, width, 0, new int[]{0xff69BF72, 0xff53B3AD}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
            lastGradientWidth = width;
        }
        int x = (getMeasuredWidth() - textView.getMeasuredWidth()) / 2;
        icon.setProgressRect(x, dp(13), x + dp(22), dp(13 + 22));
    }


    private Utilities.Callback<Float> onTranslationUpdate;
    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (onTranslationUpdate != null) {
            onTranslationUpdate.run(translationY);
        }
    }

    @Keep
    public void onTranslationUpdate(Utilities.Callback<Float> onTranslationUpdate) {
        this.onTranslationUpdate = onTranslationUpdate;
    }

    @Keep
    public void update(boolean animated) {
        final boolean show;
        if (SharedConfig.isAppUpdateAvailable()) {
            final String fileName = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
            final File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            show = path.exists();
        } else {
            show = false;
        }
        if (show) {
            if (getTag() != null) {
                return;
            }
            if (animator != null) {
                animator.cancel();
            }
            setVisibility(View.VISIBLE);
            setTag(1);
            if (animated) {
                animator = new AnimatorSet();
                animator.setDuration(180);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animator.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 0));
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animator = null;
                    }
                });
                animator.start();
            } else {
                setTranslationY(0);
            }
        } else {
            if (getTag() == null) {
                return;
            }
            setTag(null);
            if (animated) {
                animator = new AnimatorSet();
                animator.setDuration(180);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animator.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, dp(48)));
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (getTag() == null) {
                            setVisibility(View.INVISIBLE);
                        }
                        animator = null;
                    }
                });
                animator.start();
            } else {
                setTranslationY(dp(48));
                setVisibility(View.INVISIBLE);
            }
        }
    }

}
