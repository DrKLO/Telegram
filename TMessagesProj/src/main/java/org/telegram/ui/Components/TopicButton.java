package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Forum.ForumBubbleDrawable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.Forum.MessageTopicButton;

public class TopicButton {

    private final ChatMessageCell cell;

    public boolean hasImage;
    public int width;
    public boolean waitingForColor;
    public int color, bgColor;
    public Drawable image;
    public int imageColor = 0;
    public AnimatedColor animatedColor;
    public AnimatedColor animatedBgColor;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private float arrowWidth = 0;

    public final ButtonBounce bounce;
    public final RectF clickBounds = new RectF();

    public TopicButton(ChatMessageCell cell) {
        this.cell = cell;
        this.animatedColor = new AnimatedColor(cell::invalidateOutbounds, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.animatedBgColor = new AnimatedColor(cell::invalidateOutbounds, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.bounce = new ButtonBounce(cell);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private boolean active;
    private long topicId;
    public boolean update(MessageObject messageObject) {
        if (cell.isMonoForum) {
            if (topicId == -1) return active;
            topicId = -1;
            if (image instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) image).removeView(cell::invalidateOutbounds);
            }
            image = null;
            waitingForColor = false;
            if (messageObject.isOutOwner()) {
                color = bgColor = Theme.getColor(Theme.key_chat_outReplyLine, cell.getResourcesProvider());
            } else {
                color = bgColor = Theme.getColor(Theme.key_chat_inReplyLine, cell.getResourcesProvider());
            }
            animatedColor.force(color);
            width = dp(24);
            return active = true;
        } else if (cell.isForum) {
            if (image instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) image).removeView(cell::invalidateOutbounds);
            }
            final TLRPC.TL_forumTopic topic = MessagesController.getInstance(cell.currentAccount).getTopicsController().findTopic(-messageObject.getDialogId(), messageObject.getTopicId());
            if (topic != null && topicId == topic.id) return active;
            imageColor = 0;
            image = null;
            waitingForColor = false;
            if (topic == null) {
                image = null;
                topicId = -2;
                return active = false;
            }
            topicId = topic.id;
            if (topic.id == 1) {
                image = cell.getContext().getResources().getDrawable(R.drawable.msg_filled_general).mutate();
            } else if (topic.icon_emoji_id != 0) {
                image = AnimatedEmojiDrawable.make(cell.currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, topic.icon_emoji_id);
            } else {
                image = ForumUtilities.createTopicDrawable(topic, false);
                if (image instanceof CombinedDrawable && ((CombinedDrawable) image).getIcon() instanceof LetterDrawable) {
                    ((LetterDrawable) ((CombinedDrawable) image).getIcon()).scale = 0.65f;
                }
            }
            if (messageObject.isOutOwner()) {
                color = bgColor = Theme.getColor(Theme.key_chat_outReplyLine, cell.getResourcesProvider());
            } else if (topic.id == 1) {
                color = bgColor = Theme.getColor(Theme.key_chat_inReplyLine, cell.getResourcesProvider());
            } else if (topic.icon_emoji_id != 0) {
                color = bgColor = Theme.getColor(Theme.key_chat_inReplyLine, cell.getResourcesProvider());
                waitingForColor = true;
            } else {
                color = getColor(topic.icon_color);
                bgColor = getBackgroundColor(topic.icon_color);
            }
            animatedColor.force(color);
            width = dp(43.66f);
            return active = true;
        }
        return active = false;
    }

    public int getColor(int iconColor) {
        float[] topicHSV = new float[3];
        Color.colorToHSV(iconColor, topicHSV);
        float hue = topicHSV[0];
        float sat = topicHSV[1];
        if (sat <= 0.02f) {
            return Theme.getColor(Theme.key_chat_inReactionButtonText, cell.getResourcesProvider());
        } else {
            Color.colorToHSV(Theme.getColor(Theme.key_chat_inReactionButtonText, cell.getResourcesProvider()), topicHSV);
            topicHSV[0] = hue;
            float[] hueRanges = Theme.isCurrentThemeDark() ? MessageTopicButton.darkHueRanges : MessageTopicButton.lightHueRanges;
            float[] satValues = Theme.isCurrentThemeDark() ? MessageTopicButton.darkSatValues : MessageTopicButton.lightSatValues;
            float[] valValues = Theme.isCurrentThemeDark() ? MessageTopicButton.darkValValues : MessageTopicButton.lightValValues;
            for (int i = 1; i < hueRanges.length; ++i) {
                if (hue <= hueRanges[i]) {
                    float t = (hue - hueRanges[i - 1]) / (hueRanges[i] - hueRanges[i - 1]);
                    topicHSV[1] = AndroidUtilities.lerp(satValues[i - 1], satValues[i], t);
                    topicHSV[2] = AndroidUtilities.lerp(valValues[i - 1], valValues[i], t);
                    break;
                }
            }
            return Color.HSVToColor(Color.alpha(Theme.getColor(Theme.key_chat_inReactionButtonText, cell.getResourcesProvider())), topicHSV);
        }
    }

    public int getBackgroundColor(int iconColor) {
        float[] topicHSV = new float[3];
        Color.colorToHSV(iconColor, topicHSV);
        float hue = topicHSV[0];
        float sat = topicHSV[1];
        if (sat <= 0.02f) {
//            topicNameColor = Theme.getColor(Theme.key_chat_inReactionButtonText, cell.getResourcesProvider());
            return ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_inReactionButtonBackground, cell.getResourcesProvider()), 38);
        } else {
            Color.colorToHSV(Theme.getColor(Theme.key_chat_inReactionButtonText, cell.getResourcesProvider()), topicHSV);
            topicHSV[0] = hue;
            float[] hueRanges = Theme.isCurrentThemeDark() ? MessageTopicButton.darkHueRanges : MessageTopicButton.lightHueRanges;
            float[] satValues = Theme.isCurrentThemeDark() ? MessageTopicButton.darkSatValues : MessageTopicButton.lightSatValues;
            float[] valValues = Theme.isCurrentThemeDark() ? MessageTopicButton.darkValValues : MessageTopicButton.lightValValues;
            for (int i = 1; i < hueRanges.length; ++i) {
                if (hue <= hueRanges[i]) {
                    float t = (hue - hueRanges[i - 1]) / (hueRanges[i] - hueRanges[i - 1]);
                    topicHSV[1] = AndroidUtilities.lerp(satValues[i - 1], satValues[i], t);
                    topicHSV[2] = AndroidUtilities.lerp(valValues[i - 1], valValues[i], t);
                    break;
                }
            }
//            topicNameColor = Color.HSVToColor(Color.alpha(getThemedColor(Theme.key_chat_inReactionButtonText)), topicHSV);
            return Color.HSVToColor(38, topicHSV);
        }
    }

    public void attach() {
        if (image instanceof AnimatedEmojiDrawable) {
            ((AnimatedEmojiDrawable) image).addView(cell::invalidateOutbounds);
        }
    }

    public void detach() {
        if (image instanceof AnimatedEmojiDrawable) {
            ((AnimatedEmojiDrawable) image).removeView(cell::invalidateOutbounds);
        }
    }

    public void draw(Canvas canvas, float x, float y, float alpha) {
        if (!active) return;

        final float scale = lerp(0.35f, 1.0f, alpha) * bounce.getScale(0.025f);
        final float height = dp(24);

        if (waitingForColor) {
            if (image instanceof AnimatedEmojiDrawable) {
                final int iconColor = AnimatedEmojiDrawable.getDominantColor((AnimatedEmojiDrawable) image);
                this.color = getColor(iconColor);
                this.bgColor = getBackgroundColor(iconColor);
            }
            waitingForColor = false;
        }
        final int color = animatedColor.set(this.color);
        final int bgColor = animatedBgColor.set(this.bgColor);

        canvas.save();
        clickBounds.set(x, y, x + width, y + height);
        clickBounds.inset(-dp(4), -dp(4));
        canvas.translate(x, y);
        canvas.scale(scale, scale, width, height / 2f);

        paint.setColor(bgColor);
        paint.setAlpha((int) (0xFF * 0.15f * alpha));
        AndroidUtilities.rectTmp.set(0, 0, width, height);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, height / 2, height / 2, paint);

        final float arrowWidth = dp(3.5f);
        final float arrowHeight = dp(8);
        if (this.arrowWidth != arrowWidth) {
            this.arrowWidth = arrowWidth;
            arrow.rewind();
            arrow.moveTo(-arrowWidth / 2f, -arrowHeight / 2f);
            arrow.lineTo(arrowWidth / 2f, 0);
            arrow.lineTo(-arrowWidth / 2f, arrowHeight / 2f);
        }

        if (image != null) {
            if (imageColor != color) {
                image.setColorFilter(new PorterDuffColorFilter(imageColor = color, PorterDuff.Mode.SRC_IN));
            }
            final int r = dp(16) / 2;
            image.setBounds((int) (dp(2) + height / 2 - r), (int) (height / 2 - r), (int) (dp(2) + height / 2 + r), (int) (height / 2 + r));
            image.setAlpha((int) (0xFF * alpha));
            image.draw(canvas);
        }

        canvas.save();
        strokePaint.setStrokeWidth(dp(1.66f));
        strokePaint.setColor(color);
        strokePaint.setAlpha((int) (0xFF * alpha));
        canvas.translate(width - height / 2f, height / 2f);
        canvas.drawPath(arrow, strokePaint);
        canvas.restore();

        canvas.restore();
    }

    public boolean onTouchEvent(MotionEvent event) {
        final boolean hit = clickBounds.contains(event.getX(), event.getY() - cell.getPaddingTop());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(hit);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (bounce.isPressed() && !hit) {
                bounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed() && cell.getDelegate() != null) {
                cell.getDelegate().didPressSideButton(cell);
            }
            bounce.setPressed(false);
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
        }
        return bounce.isPressed();
    }

}
