package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;


@SuppressWarnings("FieldCanBeLocal")
public class NoSoundHintView extends FrameLayout {

    private TextView textView;
    private ImageView imageView;
    private ImageView arrowImageView;
    private ChatMessageCell messageCell;
    private AnimatorSet animatorSet;
    private Runnable hideRunnable;

    public NoSoundHintView(Context context) {
        super(context);

        textView = new CorrectlyMeasuringTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setMaxLines(2);
        textView.setMaxWidth(AndroidUtilities.dp(250));
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setText(LocaleController.getString("AutoplayVideoInfo", R.string.AutoplayVideoInfo));
        textView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
        textView.setPadding(AndroidUtilities.dp(54), AndroidUtilities.dp(6), AndroidUtilities.dp(5), AndroidUtilities.dp(7));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 6));

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.tooltip_sound);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_gifSaveHintText), PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(38, 34, Gravity.LEFT | Gravity.TOP, 7, 7, 0, 0));

        arrowImageView = new ImageView(context);
        arrowImageView.setImageResource(R.drawable.tooltip_arrow);
        arrowImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.MULTIPLY));
        addView(arrowImageView, LayoutHelper.createFrame(14, 6, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
    }

    public boolean showForMessageCell(ChatMessageCell cell) {
        if (getTag() != null) {
            return false;
        }
        ImageReceiver imageReceiver = cell.getPhotoImage();
        int top = cell.getTop() + imageReceiver.getImageY();
        int height = imageReceiver.getImageHeight();
        int bottom = top + height;
        View parentView = (View) cell.getParent();
        int parentHeight = parentView.getMeasuredHeight();
        if (top <= getMeasuredHeight() + AndroidUtilities.dp(10) || bottom > parentHeight + height / 4) {
            return false;
        }
        int parentWidth = parentView.getMeasuredWidth();
        setTranslationY(top - getMeasuredHeight());
        int iconX = cell.getLeft() + cell.getNoSoundIconCenterX();
        int left = getLeft();
        if (iconX > parentView.getMeasuredWidth() / 2) {
            int offset = parentWidth - getMeasuredWidth() - AndroidUtilities.dp(38);
            setTranslationX(offset);
            left += offset;
        } else {
            setTranslationX(0);
        }
        arrowImageView.setTranslationX(cell.getLeft() + cell.getNoSoundIconCenterX() - left - arrowImageView.getMeasuredWidth() / 2);
        messageCell = cell;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, "alpha", 0.0f, 1.0f)
        );
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatorSet = null;
                AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), 10000);
            }
        });
        animatorSet.setDuration(300);
        animatorSet.start();

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
                animatorSet = null;
            }
        });
        animatorSet.setDuration(300);
        animatorSet.start();
    }

    public ChatMessageCell getMessageCell() {
        return messageCell;
    }
}
