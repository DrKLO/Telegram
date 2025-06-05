package org.telegram.ui.Stories;

import static org.telegram.ui.Stories.StoryMediaAreasView.rgbaToArgb;

import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.Paint.Views.LocationMarker;
import org.telegram.ui.Components.Reactions.ReactionImageHolder;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stories.recorder.Weather;

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
            } else if (storyItem.media_areas.get(i) instanceof TL_stories.TL_mediaAreaWeather) {
                if (drawingObjects == null) {
                    drawingObjects = new ArrayList<>();
                }
                drawingObjects.add(new WeatherWidget((TL_stories.TL_mediaAreaWeather) storyItem.media_areas.get(i)));
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

        private final StoryReactionWidgetBackground storyReactionWidgetBackground = new StoryReactionWidgetBackground(null);

        private final TL_stories.TL_mediaAreaSuggestedReaction mediaArea;
        private final ReactionImageHolder imageHolder = new ReactionImageHolder(null);

        public ReactionWidget(TL_stories.TL_mediaAreaSuggestedReaction mediaArea) {
            this.mediaArea = mediaArea;
            if (mediaArea.flipped) {
                storyReactionWidgetBackground.setMirror(true, false);
            }
            if (mediaArea.dark) {
                storyReactionWidgetBackground.nextStyle();
            }
            imageHolder.setStatic();
            imageHolder.setVisibleReaction(ReactionsLayoutInBubble.VisibleReaction.fromTL(mediaArea.reaction));
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

    public class WeatherWidget extends DrawingObject {

        private final LocationMarker marker;
        private final TL_stories.TL_mediaAreaWeather mediaArea;
        private View parentView;

        public WeatherWidget(TL_stories.TL_mediaAreaWeather mediaArea) {
            this.mediaArea = mediaArea;

            final Weather.State state = new Weather.State();
            state.emoji = mediaArea.emoji;
            state.temperature = (float) mediaArea.temperature_c;

            this.marker = new LocationMarker(ApplicationLoader.applicationContext, LocationMarker.VARIANT_WEATHER, AndroidUtilities.density, 0) {
                @Override
                public void invalidate() {
                    if (parentView != null) {
                        parentView.invalidate();
                    }
                }
            };
            marker.setMaxWidth(AndroidUtilities.displaySize.x);
            marker.setIsVideo(false);
            marker.setCodeEmoji(UserConfig.selectedAccount, state.getEmoji());
            marker.setText(state.getTemperature());
            marker.setType(3, mediaArea.color);

            marker.setupLayout();
        }

        @Override
        public void draw(Canvas canvas, ImageReceiver imageReceiver, float alpha) {
            float x = (float) (imageX + imageW * mediaArea.coordinates.x / 100);
            float y = (float) (imageY + imageH * mediaArea.coordinates.y / 100);
            float w = (float) (imageW * mediaArea.coordinates.w / 100);
            float h = (float) (imageH * mediaArea.coordinates.h / 100);

            canvas.save();
            canvas.translate(x, y);
            final int markerWidth = marker.getWidthInternal() - marker.getPaddingLeft() - marker.getPaddingRight();
            final int markerHeight = marker.getHeightInternal() - marker.getPaddingTop() - marker.getPaddingBottom();
            float scale = Math.min(w / markerWidth, h / markerHeight);
            canvas.scale(scale, scale);
            if (mediaArea.coordinates.rotation != 0) {
                canvas.rotate((float) mediaArea.coordinates.rotation);
            }
            canvas.translate(-markerWidth / 2f - marker.getPaddingLeft(), -markerHeight / 2f - marker.getPaddingTop());
            marker.drawInternal(canvas);
            canvas.restore();
        }

        @Override
        public void onAttachedToWindow(boolean attached) {
            if (attached) {
                marker.attachInternal();
            } else {
                marker.detachInternal();
            }
        }

        @Override
        public void setParent(View parentView) {
            this.parentView = parentView;
        }
    }
}
