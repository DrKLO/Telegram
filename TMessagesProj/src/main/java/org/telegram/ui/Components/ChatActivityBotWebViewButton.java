package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ChatActivityBotWebViewButton extends FrameLayout {
    public final static SimpleFloatPropertyCompat<ChatActivityBotWebViewButton> PROGRESS_PROPERTY = new SimpleFloatPropertyCompat<>("progress", obj -> obj.progress, ChatActivityBotWebViewButton::setProgress)
            .setMultiplier(100f);

    private Path path = new Path();
    private float progress;
    private int buttonColor = Theme.getColor(Theme.key_featuredStickers_addButton);
    private int backgroundColor;
    private int menuButtonWidth;

    private TextView textView;
    private RadialProgressView progressView;
    private View rippleView;

    private boolean progressWasVisible;
    private BotCommandsMenuView menuButton;

    public ChatActivityBotWebViewButton(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine();
        textView.setAlpha(0f);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 0, 0, 0, 0));

        progressView = new RadialProgressView(context);
        progressView.setSize(AndroidUtilities.dp(18));
        progressView.setAlpha(0f);
        progressView.setScaleX(0);
        progressView.setScaleY(0);
        addView(progressView, LayoutHelper.createFrame(28, 28, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        rippleView = new View(context);
        rippleView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_featuredStickers_addButtonPressed), 2));
        addView(rippleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 0, 0, 0, 0));

        setWillNotDraw(false);
    }

    public void setBotMenuButton(BotCommandsMenuView menuButton) {
        this.menuButton = menuButton;
        invalidate();
    }

    public void setupButtonParams(boolean isActive, String text, int color, int textColor, boolean isProgressVisible) {
        setClickable(isActive);
        rippleView.setVisibility(isActive ? VISIBLE : GONE);
        textView.setText(text);
        textView.setTextColor(textColor);
        buttonColor = color;

        rippleView.setBackground(Theme.createSelectorDrawable(BotWebViewContainer.getMainButtonRippleColor(buttonColor), 2));
        invalidate();

        progressView.setProgressColor(textColor);
        if (progressWasVisible != isProgressVisible) {
            progressWasVisible = isProgressVisible;
            progressView.animate().cancel();
            if (isProgressVisible) {
                progressView.setAlpha(0f);
                progressView.setVisibility(VISIBLE);
            }
            progressView.animate().alpha(isProgressVisible ? 1f : 0f)
                    .scaleX(isProgressVisible ? 1f : 0.1f)
                    .scaleY(isProgressVisible ? 1f : 0.1f)
                    .setDuration(250)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!isProgressVisible) {
                                progressView.setVisibility(GONE);
                            }
                        }
                    }).start();
        }
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        backgroundColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground), buttonColor, progress);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setAlpha(progress);
        }
        invalidate();
    }

    public void setMeasuredButtonWidth(int width) {
        menuButtonWidth = width;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        float menuY = (getHeight() - AndroidUtilities.dp(32)) / 2f;
        float offset = Math.max(getWidth() - menuButtonWidth - AndroidUtilities.dp(4), getHeight()) * progress;
        float rad = AndroidUtilities.dp(16) + offset;
        AndroidUtilities.rectTmp.set(AndroidUtilities.dp(14) - offset, menuY + AndroidUtilities.dp(4) - offset, AndroidUtilities.dp(6) + menuButtonWidth + offset, getHeight() - AndroidUtilities.dp(12) + offset);

        path.rewind();
        path.addRoundRect(AndroidUtilities.rectTmp, rad, rad, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawColor(backgroundColor);

        canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) ((1f - Math.min(0.5f, progress) / 0.5f) * 0xFF), Canvas.ALL_SAVE_FLAG);
        canvas.translate(AndroidUtilities.dp(10), menuY);
        if (menuButton != null) {
            menuButton.setDrawBackgroundDrawable(false);
            menuButton.draw(canvas);
            menuButton.setDrawBackgroundDrawable(true);
        }
        canvas.restore();

        canvas.translate(-AndroidUtilities.dp(8) * (1f - progress), 0);
        super.draw(canvas);
        canvas.restore();
    }
}
