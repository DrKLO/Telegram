package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;


@SuppressWarnings("FieldCanBeLocal")
public class HintView extends FrameLayout {

    private TextView textView;
    private ImageView imageView;
    private ImageView arrowImageView;
    private ChatMessageCell messageCell;
    private View currentView;
    private AnimatorSet animatorSet;
    private Runnable hideRunnable;
    private int currentType;
    private boolean isTopArrow;
    private String overrideText;

    public HintView(Context context, int type) {
        this(context, type, false);
    }

    public HintView(Context context, int type, boolean topArrow) {
        super(context);

        currentType = type;
        isTopArrow = topArrow;

        textView = new CorrectlyMeasuringTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setMaxLines(2);
        textView.setMaxWidth(AndroidUtilities.dp(250));
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
        if (currentType == 2) {
            textView.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(6), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
        } else {
            textView.setPadding(AndroidUtilities.dp(currentType == 0 ? 54 : 5), AndroidUtilities.dp(6), AndroidUtilities.dp(5), AndroidUtilities.dp(7));
        }
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, topArrow ? 6 : 0, 0, topArrow ? 0 : 6));

        if (type == 0) {
            textView.setText(LocaleController.getString("AutoplayVideoInfo", R.string.AutoplayVideoInfo));

            imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.tooltip_sound);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_gifSaveHintText), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(38, 34, Gravity.LEFT | Gravity.TOP, 7, 7, 0, 0));
        }

        arrowImageView = new ImageView(context);
        arrowImageView.setImageResource(topArrow ? R.drawable.tooltip_arrow_up : R.drawable.tooltip_arrow);
        arrowImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.MULTIPLY));
        addView(arrowImageView, LayoutHelper.createFrame(14, 6, Gravity.LEFT | (topArrow ? Gravity.TOP : Gravity.BOTTOM), 0, 0, 0, 0));
    }

    public void setOverrideText(String text) {
        overrideText = text;
        textView.setText(text);
        if (messageCell != null) {
            ChatMessageCell cell = messageCell;
            messageCell = null;
            showForMessageCell(cell, false);
        }
    }

    public boolean showForMessageCell(ChatMessageCell cell, boolean animated) {
        if (currentType == 0 && getTag() != null || messageCell == cell) {
            return false;
        }
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        int top = cell.getTop();
        int centerX;
        View parentView = (View) cell.getParent();
        if (currentType == 0) {
            ImageReceiver imageReceiver = cell.getPhotoImage();
            top += imageReceiver.getImageY();
            int height = imageReceiver.getImageHeight();
            int bottom = top + height;
            int parentHeight = parentView.getMeasuredHeight();
            if (top <= getMeasuredHeight() + AndroidUtilities.dp(10) || bottom > parentHeight + height / 4) {
                return false;
            }
            centerX = cell.getNoSoundIconCenterX();
        } else {
            MessageObject messageObject = cell.getMessageObject();
            if (overrideText == null) {
                textView.setText(LocaleController.getString("HidAccount", R.string.HidAccount));
            } else {
                textView.setText(overrideText);
            }
            measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST));

            top += AndroidUtilities.dp(22);
            if (!messageObject.isOutOwner() && cell.isDrawNameLayout()) {
                top += AndroidUtilities.dp(20);
            }
            if (!isTopArrow && top <= getMeasuredHeight() + AndroidUtilities.dp(10)) {
                return false;
            }
            centerX = cell.getForwardNameCenterX();
        }

        int parentWidth = parentView.getMeasuredWidth();
        if (isTopArrow) {
            setTranslationY(AndroidUtilities.dp(44));
        } else {
            setTranslationY(top - getMeasuredHeight());
        }
        int iconX = cell.getLeft() + centerX;
        int left = AndroidUtilities.dp(19);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            int offset = parentWidth - getMeasuredWidth() - AndroidUtilities.dp(38);
            setTranslationX(offset);
            left += offset;
        } else {
            setTranslationX(0);
        }
        float arrowX = cell.getLeft() + centerX - left - arrowImageView.getMeasuredWidth() / 2;
        arrowImageView.setTranslationX(arrowX);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        } else {
            if (arrowX > getMeasuredWidth() - AndroidUtilities.dp(14 + 10)) {
                float diff = arrowX - getMeasuredWidth() + AndroidUtilities.dp(14 + 10);
                setTranslationX(diff);
                arrowImageView.setTranslationX(arrowX - diff);
            } else if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        }

        messageCell = cell;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, "alpha", 0.0f, 1.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet = null;
                    AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), currentType == 0 ? 10000 : 2000);
                }
            });
            animatorSet.setDuration(300);
            animatorSet.start();
        } else {
            setAlpha(1.0f);
        }

        return true;
    }

    public boolean showForView(View view, boolean animated) {
        if (currentView == view || getTag() != null) {
            return false;
        }
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST));

        int[] position = new int[2];
        view.getLocationInWindow(position);

        int centerX = position[0] + view.getMeasuredWidth() / 2;
        int top = position[1] - AndroidUtilities.dp(4);

        View parentView = (View) getParent();
        parentView.getLocationInWindow(position);
        centerX -= position[0];
        top -= position[1];

        if (Build.VERSION.SDK_INT >= 21) {
            top -= AndroidUtilities.statusBarHeight;
        }


        int parentWidth = parentView.getMeasuredWidth();
        if (isTopArrow) {
            setTranslationY(AndroidUtilities.dp(44));
        } else {
            setTranslationY(top - getMeasuredHeight() - ActionBar.getCurrentActionBarHeight());
        }
        int iconX = centerX;
        int left = AndroidUtilities.dp(19);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            int offset = parentWidth - getMeasuredWidth() - AndroidUtilities.dp(28);
            setTranslationX(offset);
            left += offset;
        } else {
            setTranslationX(0);
        }
        float arrowX = centerX - left - arrowImageView.getMeasuredWidth() / 2;
        arrowImageView.setTranslationX(arrowX);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        } else {
            if (arrowX > getMeasuredWidth() - AndroidUtilities.dp(14 + 10)) {
                float diff = arrowX - getMeasuredWidth() + AndroidUtilities.dp(14 + 10);
                setTranslationX(diff);
                arrowImageView.setTranslationX(arrowX - diff);
            } else if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        }

        currentView = view;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, "alpha", 0.0f, 1.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet = null;
                    AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), 2000);
                }
            });
            animatorSet.setDuration(300);
            animatorSet.start();
        } else {
            setAlpha(1.0f);
        }

        return true;
    }

    public void hide() {
        if (getTag() == null) {
            return;
        }
        setTag(null);
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, "alpha", 0.0f)
        );
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.INVISIBLE);
                currentView = null;
                messageCell = null;
                animatorSet = null;
            }
        });
        animatorSet.setDuration(300);
        animatorSet.start();
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public ChatMessageCell getMessageCell() {
        return messageCell;
    }
}
