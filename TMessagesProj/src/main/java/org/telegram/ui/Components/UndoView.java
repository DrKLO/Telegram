package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

@SuppressWarnings("FieldCanBeLocal")
public class UndoView extends FrameLayout {

    private TextView infoTextView;
    private TextView undoTextView;
    private ImageView undoImageView;

    private int currentAccount = UserConfig.selectedAccount;

    private TextPaint textPaint;
    private Paint progressPaint;
    private RectF rect;

    private long timeLeft;
    private int prevSeconds;
    private String timeLeftString;
    private int textWidth;

    private boolean currentClear;
    private long currentDialogId;
    private Runnable currentActionRunnable;
    private Runnable currentCancelRunnable;

    private long lastUpdateTime;

    public UndoView(Context context) {
        super(context);

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTextView.setTextColor(Theme.getColor(Theme.key_undo_infoColor));
        addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 45, 0, 0, 0));

        LinearLayout undoButton = new LinearLayout(context);
        undoButton.setOrientation(LinearLayout.HORIZONTAL);
        addView(undoButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 19, 0));
        undoButton.setOnClickListener(v -> hide(false, true));

        undoImageView = new ImageView(context);
        undoImageView.setImageResource(R.drawable.chats_undo);
        undoImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_cancelColor), PorterDuff.Mode.MULTIPLY));
        undoButton.addView(undoImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        undoTextView = new TextView(context);
        undoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        undoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        undoTextView.setTextColor(Theme.getColor(Theme.key_undo_cancelColor));
        undoTextView.setText(LocaleController.getString("Undo", R.string.Undo));
        undoTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        undoButton.addView(undoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 6, 0, 0, 0));

        rect = new RectF(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15 + 18), AndroidUtilities.dp(15 + 18));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Theme.getColor(Theme.key_undo_infoColor));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setColor(Theme.getColor(Theme.key_undo_infoColor));

        setBackgroundColor(Theme.getColor(Theme.key_undo_background));

        setOnTouchListener((v, event) -> true);

        setVisibility(INVISIBLE);
    }

    public void hide(boolean apply, boolean animated) {
        if (getVisibility() != VISIBLE || currentActionRunnable == null) {
            return;
        }
        if (currentActionRunnable != null) {
            if (apply) {
                currentActionRunnable.run();
            }
            currentActionRunnable = null;
        }
        if (currentCancelRunnable != null) {
            if (!apply) {
                currentCancelRunnable.run();
            }
            currentCancelRunnable = null;
        }
        MessagesController.getInstance(currentAccount).removeDialogAction(currentDialogId, currentClear, apply);
        if (animated) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, AndroidUtilities.dp(48)));
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(180);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(INVISIBLE);
                }
            });
            animatorSet.start();
        } else {
            setTranslationY(AndroidUtilities.dp(48));
            setVisibility(INVISIBLE);
        }
    }

    public void showWithAction(long did, boolean clear, Runnable actionRunnable) {
        showWithAction(did, clear, actionRunnable, null);
    }

    public void showWithAction(long did, boolean clear, Runnable actionRunnable, Runnable cancelRunnable) {
        if (actionRunnable == null) {
            return;
        }
        if (clear) {
            infoTextView.setText(LocaleController.getString("HistoryClearedUndo", R.string.HistoryClearedUndo));
        } else {
            int lowerId = (int) did;
            if (lowerId < 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    infoTextView.setText(LocaleController.getString("ChannelDeletedUndo", R.string.ChannelDeletedUndo));
                } else {
                    infoTextView.setText(LocaleController.getString("GroupDeletedUndo", R.string.GroupDeletedUndo));
                }
            } else {
                infoTextView.setText(LocaleController.getString("ChatDeletedUndo", R.string.ChatDeletedUndo));
            }
        }
        if (currentActionRunnable != null) {
            currentActionRunnable.run();
        }
        currentActionRunnable = actionRunnable;
        currentCancelRunnable = cancelRunnable;
        currentDialogId = did;
        currentClear = clear;
        timeLeft = 5000;
        lastUpdateTime = SystemClock.uptimeMillis();
        MessagesController.getInstance(currentAccount).addDialogAction(did, clear);
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setTranslationY(AndroidUtilities.dp(48));
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, AndroidUtilities.dp(48), 0));
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(180);
            animatorSet.start();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int newSeconds = timeLeft > 0 ? (int) Math.ceil(timeLeft / 1000.0f) : 0;
        if (prevSeconds != newSeconds) {
            prevSeconds = newSeconds;
            timeLeftString = String.format("%d", Math.max(1, newSeconds));
            textWidth = (int) Math.ceil(textPaint.measureText(timeLeftString));
        }
        canvas.drawText(timeLeftString, rect.centerX() - textWidth / 2, AndroidUtilities.dp(28.2f), textPaint);
        canvas.drawArc(rect, -90, -360 * (timeLeft / 5000.0f), false, progressPaint);

        long newTime = SystemClock.uptimeMillis();
        long dt = newTime - lastUpdateTime;
        timeLeft -= dt;
        lastUpdateTime = newTime;
        if (timeLeft <= 0 && currentActionRunnable != null) {
            hide(true, true);
        }

        invalidate();
    }
}
