package org.telegram.ui.Components.Reactions;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.Objects;

public class ReactionImageHolder {

    final ImageReceiver imageReceiver;
    public AnimatedEmojiDrawable animatedEmojiDrawable;

    private final Rect bounds = new Rect();
    ReactionsLayoutInBubble.VisibleReaction reaction;
    private final int currentAccount = UserConfig.selectedAccount;
    ReactionsLayoutInBubble.VisibleReaction currentReaction;
    private final View parent;
    private boolean attached;
    float alpha = 1f;

    public ReactionImageHolder(View parent) {
        this.parent = parent;
        imageReceiver = new ImageReceiver(parent);
        imageReceiver.setAllowLoadingOnAttachedOnly(true);
    }

    public void setVisibleReaction(ReactionsLayoutInBubble.VisibleReaction currentReaction) {
        if (Objects.equals(this.currentReaction, currentReaction)) {
            return;
        }
        imageReceiver.clearImage();
        if (animatedEmojiDrawable != null) {
            animatedEmojiDrawable.removeView(parent);
            animatedEmojiDrawable = null;
        }

        this.currentReaction = currentReaction;
        if (currentReaction.emojicon != null) {
            TLRPC.TL_availableReaction defaultReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(currentReaction.emojicon);
            if (defaultReaction != null) {
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(defaultReaction.select_animation, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                imageReceiver.setImage(ImageLocation.getForDocument(defaultReaction.select_animation), "60_60", null, null, svgThumb, 0, "tgs", currentReaction, 0);
//                imageReceiver.setAllowStartAnimation(false);
//                imageReceiver.setAutoRepeatCount(1);
            }
        } else {
            animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE, UserConfig.selectedAccount, currentReaction.documentId);
            if (attached) {
                animatedEmojiDrawable.addView(parent);
            }
            animatedEmojiDrawable.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP));
        }
    }

    public void draw(Canvas canvas) {
        if (animatedEmojiDrawable != null) {
            if (animatedEmojiDrawable.getImageReceiver() != null) {
                animatedEmojiDrawable.getImageReceiver().setRoundRadius((int) (bounds.width() * 0.1f));
            }
            animatedEmojiDrawable.setBounds(bounds);
            animatedEmojiDrawable.setAlpha((int) (255 * alpha));
            animatedEmojiDrawable.draw(canvas);
        } else {
            imageReceiver.setImageCoords(bounds.left, bounds.top, bounds.width(), bounds.height());
            imageReceiver.setAlpha(alpha);
            imageReceiver.draw(canvas);
        }
    }

    public void setBounds(Rect bounds) {
        this.bounds.set(bounds);
    }

    public void onAttachedToWindow(boolean attached) {
        this.attached = attached;
        if (attached) {
            imageReceiver.onAttachedToWindow();
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(parent);
            }
        } else {
            imageReceiver.onDetachedFromWindow();
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(parent);
            }
        }
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void play() {
        imageReceiver.startAnimation();
    }
}
