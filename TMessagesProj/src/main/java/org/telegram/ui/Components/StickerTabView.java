package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;

public class StickerTabView extends FrameLayout {

    public final static int STICKER_TYPE = 0;
    public final static int ICON_TYPE = 1;
    public final static int EMOJI_TYPE = 2;

    public int type;
    public float dragOffset;
    public boolean inited;
    public boolean isChatSticker;
    BackupImageView imageView;
    ImageView iconView;
    TextView textView;
    View visibleView;

    boolean expanded;
    public final int index;
    private static int indexPointer;
    public SvgHelper.SvgDrawable svgThumb;
    boolean roundImage;

    ValueAnimator dragOffsetAnimator;
    float lastLeft;
    boolean hasSavedLeft;

    public StickerTabView(Context context, int type) {
        super(context);
        this.type = type;
        index = indexPointer++;
        if (type == EMOJI_TYPE) {
            imageView = new BackupImageView(getContext());
            imageView.setLayerNum(1);
            imageView.setAspectFit(false);
            addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
            visibleView = imageView;
        } else if (type == ICON_TYPE) {
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
            visibleView = iconView;
        } else {
            imageView = new BackupImageView(getContext());
            imageView.setLayerNum(1);
            imageView.setAspectFit(true);
            addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
            visibleView = imageView;
        }

        textView = new TextView(context);
        textView.setLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 8, 0, 8, 10));

        textView.setVisibility(View.GONE);
    }

    public void setExpanded(boolean expanded) {
        if (type == EMOJI_TYPE) {
            return;
        }
        this.expanded = expanded;
        float size = type == ICON_TYPE ? 24 : 30;
        float sizeExpanded = type == ICON_TYPE ? 38 : 56;

        visibleView.getLayoutParams().width = AndroidUtilities.dp(expanded ? sizeExpanded : size);
        visibleView.getLayoutParams().height = AndroidUtilities.dp(expanded ? sizeExpanded : size);

        textView.setVisibility(expanded ? View.VISIBLE : View.GONE);

        if (type != ICON_TYPE && roundImage) {
            imageView.setRoundRadius(AndroidUtilities.dp(visibleView.getLayoutParams().width / 2f));
        }
    }

    public void updateExpandProgress(float expandProgress) {
        if (type == EMOJI_TYPE) {
            return;
        }
        if (expanded) {
            float size = type == ICON_TYPE ? 24 : 30;
            float sizeExpanded = type == ICON_TYPE ? 38 : 56;
            float fromX = AndroidUtilities.dp(52 - size) / 2f;
            float fromY = AndroidUtilities.dp(48 - size) / 2f;
            float toX = AndroidUtilities.dp(86 - sizeExpanded) / 2f;
            float toY = AndroidUtilities.dp(48 + 50 - sizeExpanded) / 2f;

            visibleView.setTranslationY((fromY - toY) * (1 - expandProgress) - AndroidUtilities.dp(8) * expandProgress);
            visibleView.setTranslationX((fromX - toX) * (1 - expandProgress));
            textView.setAlpha(Math.max(0, (expandProgress - 0.5f) / 0.5f));
            textView.setTranslationY(-AndroidUtilities.dp(40) * (1 - expandProgress));
            textView.setTranslationX(-AndroidUtilities.dp(12) * (1 - expandProgress));

            float s;
            visibleView.setPivotX(0);
            visibleView.setPivotY(0);
            s = size / sizeExpanded * (1 - expandProgress) + expandProgress;

            visibleView.setScaleX(s);
            visibleView.setScaleY(s);

        } else {
            visibleView.setTranslationX(0);
            visibleView.setTranslationY(0);
            visibleView.setScaleX(1f);
            visibleView.setScaleY(1f);
        }

    }

    public void saveXPosition() {
        lastLeft = getLeft();
        hasSavedLeft = true;
        invalidate();
    }

    public void animateIfPositionChanged(ViewGroup parent) {
        if (getLeft() != lastLeft && hasSavedLeft) {
            dragOffset = lastLeft - getLeft();
            if (dragOffsetAnimator != null) {
                dragOffsetAnimator.removeAllListeners();
                dragOffsetAnimator.cancel();
            }
            dragOffsetAnimator = ValueAnimator.ofFloat(dragOffset, 0);
            dragOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    dragOffset = (float) valueAnimator.getAnimatedValue();
                    invalidate();
                    parent.invalidate();
                }
            });
            dragOffsetAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dragOffset = 0;
                    invalidate();
                    parent.invalidate();
                }
            });
            dragOffsetAnimator.start();
        }
        hasSavedLeft = false;
    }

    public void setRoundImage() {
        roundImage = true;
    }
}
