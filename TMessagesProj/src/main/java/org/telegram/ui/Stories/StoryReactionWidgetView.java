package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Reactions.ReactionImageHolder;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.EmojiAnimationsOverlay;

public class StoryReactionWidgetView extends StoryMediaAreasView.AreaView {

    private final ReactionsLayoutInBubble.VisibleReaction visibleReaction;
    StoryReactionWidgetBackground storyReactionWidgetBackground = new StoryReactionWidgetBackground(this);
    ReactionImageHolder holder = new ReactionImageHolder(this);
    ImageReceiver preloadSmallReaction = new ImageReceiver(this);
    AnimatedFloat progressToCount = new AnimatedFloat(this);
    AnimatedTextView.AnimatedTextDrawable animatedTextDrawable = new AnimatedTextView.AnimatedTextDrawable();
    boolean hasCounter;

    public StoryReactionWidgetView(Context context, View parent, TL_stories.TL_mediaAreaSuggestedReaction mediaArea, EmojiAnimationsOverlay overlay) {
        super(context, parent, mediaArea);
        visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(mediaArea.reaction);
        if (mediaArea.flipped) {
            storyReactionWidgetBackground.setMirror(true, false);
        }

        storyReactionWidgetBackground.updateShadowLayer(getScaleX());
        holder.setVisibleReaction(visibleReaction);
        overlay.preload(visibleReaction);
        if (visibleReaction.emojicon != null) {
            TLRPC.TL_availableReaction r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(visibleReaction.emojicon);
            if (r != null) {
                preloadSmallReaction.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", null, "webp", r, 1);
            }
        }
        animatedTextDrawable.setGravity(Gravity.CENTER);
        animatedTextDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
        animatedTextDrawable.setTextSize(AndroidUtilities.dp(18));
        animatedTextDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x);

        if (mediaArea.dark) {
            storyReactionWidgetBackground.nextStyle();
            animatedTextDrawable.setTextColor(Color.WHITE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        animatedTextDrawable.setTextSize(Math.min(AndroidUtilities.dp(18), .8f * ((1f - 0.61f) / 2f) * getMeasuredHeight()));
    }

    public void setViews(TL_stories.StoryViews storyViews, boolean animated) {
        if (storyViews != null) {
            for (int i = 0; i < storyViews.reactions.size(); i++) {
                if (ReactionsUtils.compare(storyViews.reactions.get(i).reaction, visibleReaction)) {
                    boolean animateText = animated && hasCounter;
                    hasCounter = storyViews.reactions.get(i).count > 0;
                    animatedTextDrawable.setText(AndroidUtilities.formatWholeNumber(storyViews.reactions.get(i).count, 0), animateText);
                    if (!animated) {
                        progressToCount.set(hasCounter ? 1f : 0, true);
                    }
                    return;
                }
            }
        }
        hasCounter = false;
        invalidate();
        if (!animated) {
            progressToCount.set(hasCounter ? 1f : 0, true);
        }
    }

    @Override
    public void setScaleX(float scaleX) {
        if (getScaleX() != scaleX) {
            storyReactionWidgetBackground.updateShadowLayer(scaleX);
            super.setScaleX(scaleX);
        }
    }

    @Override
    public void customDraw(Canvas canvas) {
        storyReactionWidgetBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        storyReactionWidgetBackground.draw(canvas);
        int imageSize = (int) (getMeasuredWidth() * 0.61f);
        float x1 = storyReactionWidgetBackground.getBounds().centerX() - imageSize / 2f;
        float y1 = storyReactionWidgetBackground.getBounds().centerY() - imageSize / 2f;
        float x2 = storyReactionWidgetBackground.getBounds().centerX() + imageSize / 2f;
        float y2 = storyReactionWidgetBackground.getBounds().centerY() + imageSize / 2f;

        float cy = storyReactionWidgetBackground.getBounds().top + storyReactionWidgetBackground.getBounds().height() * 0.427f;

        float countedY1 = cy - imageSize / 2f;
        float countedY2 = cy + imageSize / 2f;

        float progress = progressToCount.set(hasCounter ? 1f : 0);
        AndroidUtilities.rectTmp2.set(
                (int) x1,
                (int) AndroidUtilities.lerp(y1, countedY1, progress),
                (int) x2,
                (int) AndroidUtilities.lerp(y2, countedY2, progress)
        );
        holder.setColor(storyReactionWidgetBackground.isDarkStyle() ? Color.WHITE : Color.BLACK);
        holder.setBounds(AndroidUtilities.rectTmp2);
        holder.draw(canvas);

        float textCy = storyReactionWidgetBackground.getBounds().top + storyReactionWidgetBackground.getBounds().height() * 0.839f;
        animatedTextDrawable.setBounds(
                storyReactionWidgetBackground.getBounds().left,
                (int) (textCy - AndroidUtilities.dp(10)),
                storyReactionWidgetBackground.getBounds().right,
                (int) (textCy + AndroidUtilities.dp(10))
        );
        canvas.save();
        canvas.scale(progress, progress, storyReactionWidgetBackground.getBounds().centerX(), textCy);
        animatedTextDrawable.draw(canvas);
        canvas.restore();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        holder.onAttachedToWindow(true);
        preloadSmallReaction.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        holder.onAttachedToWindow(false);
        preloadSmallReaction.onDetachedFromWindow();

    }

    public void playAnimation() {
        holder.play();
    }

    public AnimatedEmojiDrawable getAnimatedEmojiDrawable() {
        return holder.animatedEmojiDrawable;
    }
}
