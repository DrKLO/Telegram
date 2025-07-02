package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Components.Forum.ForumUtilities.createGeneralTopicDrawable;
import static org.telegram.ui.Components.Forum.ForumUtilities.createTopicDrawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

public class TopicSeparator {

    private final int currentAccount;
    private final View cell;
    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean withDots;

    public Text text;
    public final AvatarDrawable avatarDrawable = new AvatarDrawable();
    public final ImageReceiver image;
    public AnimatedEmojiDrawable emojiImage;
    private final Path path = new Path();
    private int pathParentWidth, pathWidth;
    private boolean pathWithDots, pathWithCenter;
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();

    private final RectF clickBounds = new RectF();
    private final ButtonBounce bounce;
    private Runnable onClickListener;

    public TopicSeparator(int currentAccount, View cell, Theme.ResourcesProvider resourcesProvider, boolean withDots) {
        this.currentAccount = currentAccount;
        this.cell = cell;
        this.resourcesProvider = resourcesProvider;
        this.withDots = withDots;
        this.bounce = new ButtonBounce(cell);
        image = new ImageReceiver(cell);

        arrowPath.rewind();
        arrowPath.moveTo(-dp(1.75f), -dp(4));
        arrowPath.lineTo(dp(1.75f), 0);
        arrowPath.lineTo(-dp(1.75f), dp(4));
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setOnClickListener(Runnable listener) {
        onClickListener = listener;
    }

    public long topicId;
    public boolean update(MessageObject messageObject) {
        if (emojiImage != null) {
            emojiImage.removeView(cell);
            emojiImage = null;
        }
        pathWidth = 0;
        topicId = 0;
        if (messageObject == null) {
            text = null;
            topicId = 0;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-messageObject.getDialogId());
            if (ChatObject.isMonoForum(chat)) {
                image.setRoundRadius(dp(10));
                final long dialogId = messageObject.getMonoForumTopicId();
                final TLObject userOrChat = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
                topicId = dialogId;
                if (userOrChat == null) {
                    text = null;
                    return false;
                }
                avatarDrawable.setInfo(userOrChat);
                image.setForUserOrChat(userOrChat, avatarDrawable);
                text = new Text(DialogObject.getName(userOrChat), 14, AndroidUtilities.bold());
            } else {
                image.setRoundRadius(0);
                final long topicId = messageObject.getTopicId();
                this.topicId = topicId;
                final TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-messageObject.getDialogId(), topicId);
                if (topic == null) {
                    text = null;
                    return false;
                }
                if (topicId == 1) {
                    image.setImageBitmap(createGeneralTopicDrawable(cell.getContext(), 0.75f, Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false, false));
                } else if (topic != null && topic.icon_emoji_id != 0) {
                    emojiImage = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, currentAccount, topic.icon_emoji_id);
                    image.onDetachedFromWindow();
                    emojiImage.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                } else if (topic != null) {
                    image.setImageBitmap(createTopicDrawable(topic, false));
                } else {
                    image.clearImage();
                }
                if (topic != null) {
                    text = new Text(topic == null ? "" : topic.title, 14, AndroidUtilities.bold());
                } else {
                    text = null;
                }
            }
        }
        return text != null;
    }

    public void attach() {
        image.onAttachedToWindow();
        if (emojiImage != null) {
            emojiImage.addView(cell);
        }
    }

    public void detach() {
        image.onDetachedFromWindow();
        if (emojiImage != null) {
            emojiImage.removeView(cell);
        }
    }

    public void draw(Canvas canvas, int parentWidth, float sideMenuWidth, float top, float bgAlpha, float alpha, boolean withCenter) {
        if (text == null) return;
        text.ellipsize(parentWidth - dp(28.66f + 20 + 64 + 32));
        final float width = dp(28.66f + 20) + text.getWidth();
        final float left = (parentWidth - width) / 2.0f;
        if (pathWidth != (int) width || pathParentWidth != parentWidth || pathWithCenter != withCenter || pathWithDots != withDots) {
            path.rewind();
            AndroidUtilities.rectTmp.set(left, dp(4.5f), left + width, dp(4.5f + 24));
            if (withCenter) {
                path.addRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), Path.Direction.CW);
            }
            if (withDots) {
                float x = parentWidth / 2.0f - dp(1.833f);
                while (x > 0) {
                    AndroidUtilities.rectTmp.set(x - dp(3.66f), dp(15.5f), x, dp(17.5f));
                    path.addRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), Path.Direction.CW);
                    x -= dp(8.33f);
                }
                x = parentWidth / 2.0f + dp(1.833f);
                while (x < parentWidth) {
                    AndroidUtilities.rectTmp.set(x, dp(15.5f), x + dp(3.66f), dp(17.5f));
                    path.addRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), Path.Direction.CW);
                    x += dp(8.33f);
                }
            }
            pathWidth = (int) width;
            pathParentWidth = parentWidth;
            pathWithDots = withDots;
            pathWithCenter = withCenter;
        }
        canvas.save();
        canvas.translate(sideMenuWidth / 2f, top);
        final Paint backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        int oldAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (oldAlpha * alpha * bgAlpha));
        canvas.drawPath(path, backgroundPaint);
        backgroundPaint.setAlpha(oldAlpha);
        if (resourcesProvider == null ? Theme.hasGradientService() : resourcesProvider.hasGradientService()) {
            final Paint darkenPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider);
            oldAlpha = darkenPaint.getAlpha();
            darkenPaint.setAlpha((int) (oldAlpha * alpha * bgAlpha));
            canvas.drawPath(path, darkenPaint);
            darkenPaint.setAlpha(oldAlpha);
        }
        canvas.restore();
        clickBounds.set(sideMenuWidth / 2f + left - dp(4), top - dp(4), sideMenuWidth / 2f + left + width + dp(4), top + dp(32));
        if (withCenter) {
            if (emojiImage != null) {
                emojiImage.setBounds(
                    (int) (sideMenuWidth / 2f + left + dp(2.66f)),
                    (int) (top + dp(6.5f)),
                    (int) (sideMenuWidth / 2f + left + dp(2.66f + 20)),
                    (int) (top + dp(6.5f + 20))
                );
                emojiImage.setAlpha((int) (0xFF * alpha));
                emojiImage.draw(canvas);
            } else {
                image.setImageCoords(sideMenuWidth / 2f + left + dp(2.66f), top + dp(6.5f), dp(20), dp(20));
                image.setAlpha(alpha);
                image.draw(canvas);
            }
            final int textColor = Theme.getColor(Theme.key_chat_serviceText, resourcesProvider);
            text.draw(canvas, sideMenuWidth / 2f + left + dp(27.66f), top + dp(16.5f), textColor, alpha);

            canvas.save();
            canvas.translate(sideMenuWidth / 2f + left + width - dp(11.25f), top + dp(16.5f));
            arrowPaint.setColor(Theme.multAlpha(textColor, .75f * alpha));
            arrowPaint.setStrokeWidth(dp(1.66f));
            canvas.drawPath(arrowPath, arrowPaint);
            canvas.restore();
        }
    }

    public boolean onTouchEvent(MotionEvent event, boolean removePadding) {
        final boolean hit = text != null && clickBounds.contains(event.getX(), event.getY() - (removePadding ? cell.getPaddingTop() : 0));
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(hit);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (bounce.isPressed() && !hit) {
                bounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed() && onClickListener != null) {
                onClickListener.run();
            }
            bounce.setPressed(false);
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
        }
        return bounce.isPressed();
    }

    public static class Cell extends View {

        public final TopicSeparator separator;
        private Utilities.Callback<Long> onClickListener;

        public Cell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            separator = new TopicSeparator(currentAccount, this, resourcesProvider, false);
            separator.setOnClickListener(() -> {
                if (onClickListener != null) {
                    onClickListener.run(separator.topicId);
                }
            });
        }

        public void setOnTopicClickListener(Utilities.Callback<Long> listener) {
            onClickListener = listener;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return separator.onTouchEvent(event, false) || super.onTouchEvent(event);
        }

        public void set(MessageObject object) {
            separator.update(object);
            if (isAttachedToWindow()) {
                separator.attach();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            separator.attach();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            separator.detach();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(33), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            separator.draw(canvas, getWidth(), 0, 0, 0.75f, 1.0f, true);
        }
    }
}
