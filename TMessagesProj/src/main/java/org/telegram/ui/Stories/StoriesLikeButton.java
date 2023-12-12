package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.Objects;

public class StoriesLikeButton extends View {

    PeerStoriesView.SharedResources sharedResources;
    boolean liked;
    AnimatedFloat progressToLiked = new AnimatedFloat(this);
    ImageReceiver reactionImageReceiver = new ImageReceiver(this);
    ImageReceiver animateReactionImageReceiver = new ImageReceiver(this);
    AnimatedEmojiDrawable emojiDrawable;
    private boolean allowDrawReaction = true;
    private boolean isLike;
    private boolean drawAnimateImageReciever;
    private boolean attachedToWindow;

    ReactionsLayoutInBubble.VisibleReaction currentReaction;


    public StoriesLikeButton(Context context, PeerStoriesView.SharedResources sharedResources) {
        super(context);
        this.sharedResources = sharedResources;
        reactionImageReceiver.setAllowLoadingOnAttachedOnly(true);
        reactionImageReceiver.ignoreNotifications = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isLike) {
            float progress = progressToLiked.set(liked ? 1f : 0f);
            if (progress < 1f) {
                sharedResources.likeDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
                sharedResources.likeDrawable.setAlpha((int) (255));
                sharedResources.likeDrawable.draw(canvas);
            }
            if (progress > 0) {
                sharedResources.likeDrawableFilled.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
                sharedResources.likeDrawableFilled.setAlpha((int) (progress * 255));
                sharedResources.likeDrawableFilled.draw(canvas);
            }
        } else {
            if (allowDrawReaction) {
                ImageReceiver receiverToDraw = emojiDrawable != null ? emojiDrawable.getImageReceiver() : reactionImageReceiver;
                if (drawAnimateImageReciever && animateReactionImageReceiver.getBitmap() != null) {
                    receiverToDraw = animateReactionImageReceiver;
                    int size = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                    receiverToDraw.setImageCoords(getPaddingLeft() - size / 2f,
                            getPaddingTop() - size / 2f,
                            size * 2,
                            size * 2
                    );
                    if (animateReactionImageReceiver.getLottieAnimation() != null && animateReactionImageReceiver.getLottieAnimation().isLastFrame()) {
                        drawAnimateImageReciever = false;
                        reactionImageReceiver.setCrossfadeAlpha((byte) 0);
                    }
                } else {
                    if (receiverToDraw != null) {
                        receiverToDraw.setImageCoords(getPaddingLeft(),
                                getPaddingTop(),
                                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                                getMeasuredHeight() - getPaddingTop() - getPaddingBottom()
                        );
                    }
                }
                if (receiverToDraw != null) {
                    receiverToDraw.draw(canvas);
                }
            }
        }
    }

    public void setReaction(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        isLike = visibleReaction == null || (visibleReaction.emojicon != null && visibleReaction.emojicon.equals("\u2764"));
        if (visibleReaction != null && visibleReaction.emojicon != null && visibleReaction.emojicon.equals("\u2764")) {
            this.liked = true;
        } else {
            this.liked = false;
        }
        this.currentReaction = visibleReaction;
        if (emojiDrawable != null) {
            emojiDrawable.removeView(this);
        }
        emojiDrawable = null;
        if (visibleReaction != null) {
            if (visibleReaction.documentId != 0) {
                emojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, UserConfig.selectedAccount, visibleReaction.documentId);
                if (attachedToWindow) {
                    emojiDrawable.addView(this);
                }
            } else {
                TLRPC.TL_availableReaction r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(visibleReaction.emojicon);
                if (r != null) {
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon, Theme.key_windowBackgroundGray, 1.0f);
                    reactionImageReceiver.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", svgThumb, "webp", r, 1);
                }
            }
        }
        invalidate();
    }

    public void setAllowDrawReaction(boolean b) {
        if (allowDrawReaction == b) {
            return;
        }
        allowDrawReaction = b;
        invalidate();
    }

    public void prepareAnimateReaction(ReactionsLayoutInBubble.VisibleReaction reaction) {
        if (reaction.documentId == 0) {
            TLRPC.TL_availableReaction r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(reaction.emojicon);
            if (r != null) {
                animateReactionImageReceiver.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_nolimit", null, "tgs", r, 1);
                animateReactionImageReceiver.setAutoRepeat(0);

            }
        }
    }

    public void animateVisibleReaction() {
        drawAnimateImageReciever = true;
        if (animateReactionImageReceiver.getLottieAnimation() != null) {
            animateReactionImageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reactionImageReceiver.onAttachedToWindow();
        animateReactionImageReceiver.onAttachedToWindow();
        attachedToWindow = true;
        if (emojiDrawable != null) {
            emojiDrawable.addView(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reactionImageReceiver.onDetachedFromWindow();
        animateReactionImageReceiver.onDetachedFromWindow();
        attachedToWindow = false;
        if (emojiDrawable != null) {
            emojiDrawable.removeView(this);
        }
    }
}
