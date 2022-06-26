package org.telegram.ui.Components.Premium;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;

public class ReactionDrawingObject extends CarouselView.DrawingObject {

    private View parentView;
    ImageReceiver imageReceiver = new ImageReceiver();
    ImageReceiver actionReceiver = new ImageReceiver();
    ImageReceiver effectImageReceiver = new ImageReceiver();
    TLRPC.TL_availableReaction reaction;
    private int position;
    long lastSelectedTime;
    private boolean selected;
    android.graphics.Rect rect = new Rect();

    private float selectedProgress;

    public ReactionDrawingObject(int i) {
        position = i;
    }

    @Override
    public void onAttachToWindow(View parentView, int i) {
        this.parentView = parentView;
        if (i == 0) {
            imageReceiver.setParentView(parentView);
            imageReceiver.onAttachedToWindow();
            imageReceiver.setLayerNum(Integer.MAX_VALUE);

            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(reaction.activate_animation, Theme.key_windowBackgroundGray, 0.5f);
//            imageReceiver.setImage(ImageLocation.getForDocument(reaction.appear_animation), "60_60_nolimit", null, null, svgThumb, 0, "tgs", reaction, 0);
//            imageReceiver.setAutoRepeat(0);
//            if (imageReceiver.getLottieAnimation() != null) {
//                imageReceiver.getLottieAnimation().setCurrentFrame(0, false);
//            }
//            imageReceiver.startAnimation();
//

            actionReceiver.setParentView(parentView);
            actionReceiver.onAttachedToWindow();
            actionReceiver.setLayerNum(Integer.MAX_VALUE);

            actionReceiver.setAllowStartLottieAnimation(false);
            actionReceiver.setImage(ImageLocation.getForDocument(reaction.activate_animation), "50_50_nolimit", null, null, svgThumb, 0, "tgs", reaction, 0);
            actionReceiver.setAutoRepeat(0);
            if (actionReceiver.getLottieAnimation() != null) {
                actionReceiver.getLottieAnimation().setCurrentFrame(0, false);
                actionReceiver.getLottieAnimation().stop();
            }
        } else {
            effectImageReceiver.setParentView(parentView);
            effectImageReceiver.onAttachedToWindow();
            effectImageReceiver.setLayerNum(Integer.MAX_VALUE);

            effectImageReceiver.setAllowStartLottieAnimation(false);
            int size = ReactionsEffectOverlay.sizeForBigReaction();
            effectImageReceiver.setImage(ImageLocation.getForDocument(reaction.around_animation), size + "_" + size, null, null, null, 0, "tgs", reaction, 0);
            effectImageReceiver.setAutoRepeat(0);
            if (effectImageReceiver.getLottieAnimation() != null) {
                effectImageReceiver.getLottieAnimation().setCurrentFrame(0, false);
                effectImageReceiver.getLottieAnimation().stop();
            }
        }
    }

    @Override
    public void onDetachFromWindow() {
        imageReceiver.onDetachedFromWindow();
        imageReceiver.setParentView(null);
        effectImageReceiver.onDetachedFromWindow();
        effectImageReceiver.setParentView(null);
        actionReceiver.onDetachedFromWindow();
        actionReceiver.setParentView(null);
    }

    @Override
    public void draw(Canvas canvas, float cX, float cY, float globalScale) {
        int imageSize = (int) (AndroidUtilities.dp(120) * globalScale);
        int effectSize = (int) (AndroidUtilities.dp(350) * globalScale);
        rect.set((int) (cX - imageSize / 2f), (int) (cY - imageSize / 2f),
                (int) (cX + imageSize / 2f), (int) (cY + imageSize / 2f));


        imageReceiver.setImageCoords(cX - imageSize / 2f, cY - imageSize / 2f, imageSize, imageSize);
        actionReceiver.setImageCoords(cX - imageSize / 2f, cY - imageSize / 2f, imageSize, imageSize);

        if (actionReceiver.getLottieAnimation() != null && actionReceiver.getLottieAnimation().hasBitmap()) {
            actionReceiver.draw(canvas);
            if (actionReceiver.getLottieAnimation() != null && actionReceiver.getLottieAnimation().isLastFrame()) {
               // selected = false;
            } else if (selected && actionReceiver.getLottieAnimation() != null && !actionReceiver.getLottieAnimation().isRunning()) {
                actionReceiver.getLottieAnimation().start();
            }
        }
        if (selected || selectedProgress != 0) {
            effectImageReceiver.setImageCoords(cX - effectSize / 2f, cY - effectSize / 2f, effectSize, effectSize);
            effectImageReceiver.setAlpha(selectedProgress);
            if (selectedProgress != 1f) {
                float s = 0.7f + selectedProgress * 0.3f;
                canvas.save();
                canvas.scale(s, s, cX, cY);
                effectImageReceiver.draw(canvas);
                canvas.restore();
            } else {
                effectImageReceiver.draw(canvas);
            }

            if (selected && effectImageReceiver.getLottieAnimation() != null && effectImageReceiver.getLottieAnimation().isLastFrame()) {
                carouselView.autoplayToNext();
            }

            if (selected && effectImageReceiver.getLottieAnimation() != null && !effectImageReceiver.getLottieAnimation().isRunning() && !effectImageReceiver.getLottieAnimation().isLastFrame()) {
                effectImageReceiver.getLottieAnimation().start();
            }
            if (selected && effectImageReceiver.getLottieAnimation() != null && !effectImageReceiver.getLottieAnimation().isRunning() && effectImageReceiver.getLottieAnimation().isLastFrame()) {
                selected = false;
            }
            if (selected && selectedProgress != 1f) {
                selectedProgress += 16f / 200f;
                if (selectedProgress > 1f) {
                    selectedProgress = 1f;
                }
            } else if (!selected) {
                selectedProgress -= 16f / 200f;
                if (selectedProgress < 0) {
                    selectedProgress = 0;
                }
            }
        }
    }

    @Override
    public boolean checkTap(float x, float y) {
        if (rect.contains((int) x, (int) y)) {
            select();
            return true;
        }
        return false;
    }

    @Override
    public void select() {
        if (selected) {
            return;
        }
        selected = true;
        if (selectedProgress == 0) {
            selectedProgress = 1f;
        }
        lastSelectedTime = System.currentTimeMillis();
        if (effectImageReceiver.getLottieAnimation() != null) {
            effectImageReceiver.getLottieAnimation().setCurrentFrame(0, false);
            effectImageReceiver.getLottieAnimation().start();
        }

        if (actionReceiver.getLottieAnimation() != null) {
            actionReceiver.getLottieAnimation().setCurrentFrame(0, false);
            actionReceiver.getLottieAnimation().start();
        }

        parentView.invalidate();
    }

    @Override
    public void hideAnimation() {
        super.hideAnimation();
        selected = false;
    }

    public void set(TLRPC.TL_availableReaction reaction) {
        this.reaction = reaction;
    }
}