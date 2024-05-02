package org.telegram.ui.Stories;

import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.Reactions.ReactionImageHolder;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;

public class StoryWidgetsImageDecorator extends ImageReceiver.Decorator {

    ArrayList<DrawingObject> drawingObjects;
    public StoryWidgetsImageDecorator(TL_stories.StoryItem storyItem) {
        for (int i = 0; i < storyItem.media_areas.size(); i++) {
            if (storyItem.media_areas.get(i) instanceof TL_stories.TL_mediaAreaSuggestedReaction) {
                if (drawingObjects == null) {
                    drawingObjects = new ArrayList<>();
                }
                drawingObjects.add(new ReactionWidget((TL_stories.TL_mediaAreaSuggestedReaction) storyItem.media_areas.get(i)));
            }
        }
    }

    float imageX;
    float imageY;
    float imageW;
    float imageH;


    @Override
    protected void onDraw(Canvas canvas, ImageReceiver imageReceiver) {
        if (drawingObjects == null) {
            return;
        }
        float alpha = imageReceiver.getAlpha();
        float cx = imageReceiver.getCenterX();
        float cy = imageReceiver.getCenterY();
        imageW = imageReceiver.getImageWidth();
        imageH = imageW * 16 / 9f;
        imageX = cx - imageW / 2f;
        imageY = cy - imageH / 2f;

        canvas.save();
        canvas.clipRect(imageReceiver.getImageX(), imageReceiver.getImageY(), imageReceiver.getImageX2(), imageReceiver.getImageY2());
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).draw(canvas, imageReceiver, alpha);
        }
        canvas.restore();
    }

    @Override
    public void onAttachedToWindow(ImageReceiver imageReceiver) {
        if (drawingObjects == null) {
            return;
        }
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).setParent(imageReceiver.getParentView());
            drawingObjects.get(i).onAttachedToWindow(true);
        }
    }

    @Override
    public void onDetachedFromWidnow() {
        if (drawingObjects == null) {
            return;
        }
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).onAttachedToWindow(false);
        }
    }

    public static abstract class DrawingObject {
        public abstract void draw(Canvas canvas, ImageReceiver imageReceiver, float alpha);
        public abstract void onAttachedToWindow(boolean attached);
        public abstract void setParent(View parentView);
    }

    public class ReactionWidget extends DrawingObject {

        StoryReactionWidgetBackground storyReactionWidgetBackground = new StoryReactionWidgetBackground(null);

        TL_stories.TL_mediaAreaSuggestedReaction mediaArea;
        ReactionImageHolder imageHolder = new ReactionImageHolder(null);

        public ReactionWidget(TL_stories.TL_mediaAreaSuggestedReaction mediaArea) {
            this.mediaArea = mediaArea;
            if (mediaArea.flipped) {
                storyReactionWidgetBackground.setMirror(true, false);
            }
            if (mediaArea.dark) {
                storyReactionWidgetBackground.nextStyle();
            }
            imageHolder.setStatic();
            imageHolder.setVisibleReaction(ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(mediaArea.reaction));
        }

        public void draw(Canvas canvas, ImageReceiver imageReceiver, float alpha) {
            if (!imageHolder.isLoaded()) {
                return;
            }

            float x = (float) (imageX + imageW * mediaArea.coordinates.x / 100);
            float y = (float) (imageY + imageH * mediaArea.coordinates.y / 100);
            float w = (float) (imageW * mediaArea.coordinates.w / 100);
            float h = (float) (imageH * mediaArea.coordinates.h / 100);
            storyReactionWidgetBackground.setBounds(
                    (int) (x - w / 2f),
                    (int) (y - h / 2f),
                    (int) (x + w / 2f),
                    (int) (y + h / 2f)
            );
            storyReactionWidgetBackground.setAlpha((int) (255 * alpha));

            canvas.save();
            if (mediaArea.coordinates.rotation != 0) {
               canvas.rotate((float) mediaArea.coordinates.rotation, x, y);
            }
            float imageSize = storyReactionWidgetBackground.getBounds().height() * 0.61f;
            AndroidUtilities.rectTmp2.set(
                    (int) (storyReactionWidgetBackground.getBounds().centerX() - imageSize / 2f),
                    (int) (storyReactionWidgetBackground.getBounds().centerY() - imageSize / 2f),
                    (int) (storyReactionWidgetBackground.getBounds().centerX() + imageSize / 2f),
                    (int) (storyReactionWidgetBackground.getBounds().centerY() + imageSize / 2f)
            );

            storyReactionWidgetBackground.updateShadowLayer(1f);
            storyReactionWidgetBackground.draw(canvas);
            imageHolder.setBounds(AndroidUtilities.rectTmp2);
            imageHolder.setAlpha(alpha);
            imageHolder.setColor(storyReactionWidgetBackground.isDarkStyle() ? Color.WHITE : Color.BLACK);
            imageHolder.draw(canvas);
            canvas.restore();

        }

        public void onAttachedToWindow(boolean attached) {
            imageHolder.onAttachedToWindow(attached);
        }

        @Override
        public void setParent(View parentView) {
            imageHolder.setParent(parentView);
        }
    }
}
