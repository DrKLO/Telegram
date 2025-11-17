package org.telegram.ui.Components.conference.message;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.GroupCallMessage;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;

public class GroupCallMessageCell extends ViewGroup
    implements ClickHelper.Delegate, NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {

    private static final int ANIMATOR_IS_SEND_DELAYED_ID = 0;
    private static final int ANIMATOR_IS_SEND_ERROR_ID = 1;

    private final BoolAnimator isSendDelayedAnimator = new BoolAnimator(ANIMATOR_IS_SEND_DELAYED_ID, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320L);
    private final BoolAnimator isSendErrorAnimator = new BoolAnimator(ANIMATOR_IS_SEND_ERROR_ID, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320L);

    private final ClickHelper clickHelper = new ClickHelper(this);

    private final ImageReceiver avatarReceiver;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint errPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();

    private final ImageReceiver animatedReactionReceiver;
    private AnimatedEmojiDrawable animatedReactionDrawable;

    private ReactionsLayoutInBubble.VisibleReaction messageReaction;
    private final SpoilersTextView messageTextView;

    private Layout layout;
    private boolean layoutInvalidated;

    public GroupCallMessageCell(@NonNull Context context) {
        super(context);

        messageTextView = new SpoilersTextView(context);
        messageTextView.setDisablePaddingsOffset(true);
        messageTextView.setTextSize(14);
        messageTextView.setTextColor(Color.WHITE);
        messageTextView.setLinkTextColor(0xFF4db8ff);
        messageTextView.setHintTextColor(Color.WHITE);
        addView(messageTextView);

        bgPaint.setColor(0xFF2B333B);
        errPaint.setColor(Color.RED);
        errPaint.setAlpha(0);

        avatarReceiver = new ImageReceiver(this);
        avatarReceiver.setRoundRadius(dp(Layout.AVATAR_RADIUS));
        flickerDrawable.setStrokeWidth(dp(1));

        animatedReactionReceiver = new ImageReceiver(this);
        setWillNotDraw(false);
    }

    private final Runnable onMessageStateUpdateListener = () -> onMessageStateUpdate(true);
    private void onMessageStateUpdate(boolean animated) {
        if (groupCallMessage != null) {
            isSendDelayedAnimator.setValue(groupCallMessage.isSendDelayed(), animated);
            isSendErrorAnimator.setValue(groupCallMessage.isSendError(), animated);
        }
    }

    public void setSingleLine() {
        messageTextView.setMaxLines(1);
        messageTextView.setSingleLine(true);
        messageTextView.setEllipsize(TextUtils.TruncateAt.END);
    }

    public void setBackgroundColor(int color) {
        bgPaint.setColor(color);
    }



    private RenderNode renderNode;
    private float renderNodeScale;
    private View blurRoot;
    private GroupCallMessage groupCallMessage;
    private @Nullable Delegate delegate;

    public void setRenderNode(View blurRoot, RenderNode renderNode, float scale) {
        this.blurRoot = blurRoot;
        this.renderNode = renderNode;
        this.renderNodeScale = scale;
    }

    public void set(GroupCallMessage message) {
        if (isAttachedToWindow() && groupCallMessage != null) {
            groupCallMessage.unsubscribeFromStateUpdates(onMessageStateUpdateListener);
        }
        this.groupCallMessage = message;
        if (isAttachedToWindow() && groupCallMessage != null) {
            groupCallMessage.subscribeToStateUpdates(onMessageStateUpdateListener);
        }

        onMessageStateUpdate(false);

        TLObject userOrChat = MessagesController.getInstance(UserConfig.selectedAccount).getUserOrChat(message.fromId);
        String name = DialogObject.getName(userOrChat);

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(message.currentAccount, userOrChat);
        avatarReceiver.setForUserOrChat(userOrChat, avatarDrawable);

        animatedReactionReceiver.setImage(null, null, null, null, null, 0);
        if (animatedReactionDrawable != null && isAttachedToWindow()) {
            animatedReactionDrawable.removeView(this);
        }
        animatedReactionDrawable = null;

        final SpannableStringBuilder ssb = new SpannableStringBuilder(name);
        final CharSequence messageText;
        ssb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(senderNameSpan, 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (message.visibleReaction == null) {
            messageText = concat(ssb, MessageObject.formatTextWithEntities(message.message, false, true, messageTextView.getPaint()));
        } else {
            if (message.visibleReaction.emojicon != null) {
                final TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(message.currentAccount)
                    .getReactionsMap().get(message.visibleReaction.emojicon);

                if (availableReaction != null) {
                    TLRPC.Document document = availableReaction.select_animation;
                    animatedReactionReceiver.setImage(ImageLocation.getForDocument(document),
                            "28_28", null, null, null, 0);
                }
            } else if (message.visibleReaction.documentId != 0){
                animatedReactionDrawable = new AnimatedEmojiDrawable(
                    AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES,
                    message.currentAccount,
                    message.visibleReaction.documentId
                );
                animatedReactionDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                if (isAttachedToWindow()) {
                    animatedReactionDrawable.addView(this);
                }
            }
            messageText = ssb;
        }

        messageReaction = message.visibleReaction;
        layoutInvalidated = true;

        messageTextView.setText(messageText);

        requestLayout();
    }

    private final ClickableSpan senderNameSpan = new ClickableSpan() {
        @Override
        public void onClick(@NonNull View widget) {
            if (delegate != null && groupCallMessage != null) {
                delegate.didClickSenderName(GroupCallMessageCell.this, groupCallMessage);
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {

        }
    };

    public void setDelegate(@Nullable Delegate delegate) {
        this.delegate = delegate;
    }

    public @Nullable GroupCallMessage getMessage() {
        return groupCallMessage;
    }

    private static final Rect tmpRect = new Rect();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (layout == null || layoutInvalidated || layout.viewWidth != width) {
            layout = Layout.build(width, getPaddingLeft(), getPaddingRight(), messageTextView, messageReaction);
            avatarReceiver.setImageCoords(layout.avatar);
            animatedReactionReceiver.setImageCoords(layout.reaction);
            if (animatedReactionDrawable != null) {
                layout.reaction.round(tmpRect);
                animatedReactionDrawable.setBounds(tmpRect);
            }
        }

        final int height = layout.viewHeight;
        setMeasuredDimension(width, height);

        flickerDrawable.setParentWidth(Math.round(layout.bubble.width() + dp(48)));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (layout == null) {
            return;
        }

        final int x = Math.round(layout.text.x);
        final int y = Math.round(layout.text.y);
        messageTextView.layout(x, y,
            x + messageTextView.getMeasuredWidth(),
            y + messageTextView.getMeasuredHeight()
        );
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarReceiver.onAttachedToWindow();
        animatedReactionReceiver.onAttachedToWindow();
        if (animatedReactionDrawable != null) {
            animatedReactionDrawable.addView(this);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        if (groupCallMessage != null) {
            groupCallMessage.subscribeToStateUpdates(onMessageStateUpdateListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarReceiver.onDetachedFromWindow();
        animatedReactionReceiver.onDetachedFromWindow();
        if (animatedReactionDrawable != null) {
            animatedReactionDrawable.removeView(this);
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (groupCallMessage != null) {
            groupCallMessage.unsubscribeFromStateUpdates(onMessageStateUpdateListener);
        }
    }

    public boolean isInsideBubble(float x, float y) {
        if (layout == null) return false;

        return layout.bubble.contains(x, y);
    }

    private final RectF tmpRectF = new RectF();

    private static final int CLICK_NOTHING = -1;
    private static final int CLICK_BUBBLE = 0;
    private static final int CLICK_AVATAR = 1;

    @Override
    public boolean needClickAt(View view, float x, float y) {
        return getClickTarget(x, y) == CLICK_AVATAR;
    }

    @Override
    public void onClickAt(View view, float x, float y) {
        final int target = getClickTarget(x, y);

        if (target == CLICK_AVATAR) {
            if (delegate != null && groupCallMessage != null) {
                delegate.didClickAvatar(this, groupCallMessage, x, y);
            }
        }
    }

    private int getClickTarget(float x, float y) {
        if (layout == null) {
            return CLICK_NOTHING;
        }

        tmpRectF.set(layout.avatar);
        tmpRectF.inset(-dp(5), -dp(5));
        if (tmpRectF.contains(x, y)) {
            return CLICK_AVATAR;
        }

        if (layout.bubble.contains(x, y)) {
            return CLICK_BUBBLE;
        }

        return CLICK_NOTHING;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return clickHelper.onTouchEvent(this, event);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (layout == null) {
            return;
        }

        canvas.drawPath(layout.bubblePath, bgPaint);
        if (Build.VERSION.SDK_INT >= 29 && renderNode != null && canvas.isHardwareAccelerated()) {
            float y = 0;
            View view = this;
            while (view != blurRoot) {
                y += view.getY();
                ViewParent parent = view.getParent();
                if (parent instanceof View) {
                    view = (View) parent;
                } else {
                    return;
                }
            }

            canvas.save();
            canvas.clipPath(layout.bubblePath);
            canvas.translate(0, -y);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        if (errPaint.getAlpha() > 0) {
            canvas.drawPath(layout.bubblePath, errPaint);
        }
        if (isSendDelayedAnimator.getFloatValue() > 0f) {
            tmpRectF.set(layout.bubble);
            tmpRectF.inset(dp(1), dp(1));

            flickerDrawable.draw(canvas, tmpRectF, dp(Layout.BUBBLE_RADIUS), null);
            invalidate();
        }

        super.dispatchDraw(canvas);

        avatarReceiver.draw(canvas);
        animatedReactionReceiver.draw(canvas);
        if (animatedReactionDrawable != null) {
            animatedReactionDrawable.draw(canvas);
        }
    }

    public float getReactionCenterX() {
        return layout != null ? layout.reaction.centerX() : 0;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        }
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        errPaint.setAlpha(Math.round(100 * isSendErrorAnimator.getFloatValue()));
        flickerDrawable.setAlpha(Math.round(220 * isSendDelayedAnimator.getFloatValue()));
        invalidate();
    }

    public interface Delegate {
        default void didClickAvatar(GroupCallMessageCell cell, GroupCallMessage message, float x, float y) {}
        default void didClickSenderName(GroupCallMessageCell cell, GroupCallMessage message) {}
    }

    private static class Layout {
        private static final int TEXT_MARGIN_START = 32;
        private static final int TEXT_MARGIN_END = 12;
        private static final int TEXT_MARGIN_VERTICAL = 4;
        private static final int TEXT_BASELINE = 19;
        private static final int MINIMAL_HEIGHT = 28;
        private static final int BUBBLE_RADIUS = 14;
        private static final int REACTION_MARGIN = 5;
        private static final int REACTION_SIZE = 28;

        private static final int AVATAR_RADIUS = 11;
        private static final int AVATAR_X = 4;
        private static final int AVATAR_Y = 3;

        public int viewWidth;
        public int viewHeight;

        public final RectF bubble = new RectF();
        public final Path bubblePath = new Path();
        public final RectF avatar = new RectF();
        public final RectF reaction = new RectF();
        public final PointF text = new PointF();

        public static Layout build(int width, int paddingLeft, int paddingRight,
           SpoilersTextView textView,
                   ReactionsLayoutInBubble.VisibleReaction reaction
        ) {
            final int maxBubbleWidth = width - paddingLeft - paddingRight;
            final int maxTextWidth = maxBubbleWidth - dp(Layout.TEXT_MARGIN_START + Layout.TEXT_MARGIN_END);

            textView.measure(
                MeasureSpec.makeMeasureSpec(maxTextWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );

            final float messageWidth = textView.getMeasuredWidth();
            final int bubbleWidth;
            if (reaction == null) {
                bubbleWidth = (int) Math.ceil(messageWidth) + dp(Layout.TEXT_MARGIN_START + Layout.TEXT_MARGIN_END);
            } else {
                bubbleWidth = (int) Math.ceil(messageWidth) + dp(Layout.TEXT_MARGIN_START + Layout.REACTION_MARGIN * 2 + Layout.REACTION_SIZE);
            }

            final int bubbleHeight = Math.max(dp(Layout.MINIMAL_HEIGHT),
                textView.getMeasuredHeight() + dp(Layout.TEXT_MARGIN_VERTICAL * 2));


            Layout layout = new Layout();
            layout.viewWidth = width;
            layout.viewHeight = bubbleHeight;

            layout.bubble.set(0, 0, bubbleWidth, bubbleHeight);
            layout.bubble.offset((width - bubbleWidth) / 2f, 0);

            layout.bubblePath.addRoundRect(layout.bubble, dp(BUBBLE_RADIUS), dp(BUBBLE_RADIUS), Path.Direction.CW);

            final boolean messageIsRTL = textView.getLayout().getParagraphDirection(0) == android.text.Layout.DIR_RIGHT_TO_LEFT;

            layout.avatar.set(0, 0, dp(AVATAR_RADIUS * 2), dp(AVATAR_RADIUS * 2));
            if (messageIsRTL) {
                layout.avatar.offset(layout.bubble.right, layout.bubble.top);
                layout.avatar.offset(-dp(AVATAR_X) - layout.avatar.width(), dp(AVATAR_Y));
            } else {
                layout.avatar.offset(layout.bubble.left, layout.bubble.top);
                layout.avatar.offset(dp(AVATAR_X), dp(AVATAR_Y));
            }

            layout.reaction.set(0, 0, dp(REACTION_SIZE), dp(REACTION_SIZE));
            if (messageIsRTL) {
                layout.reaction.offset(layout.bubble.left + dp(REACTION_MARGIN), 0);
            } else {
                layout.reaction.offset(layout.bubble.right - dp(REACTION_SIZE + REACTION_MARGIN), 0);
            }
            layout.reaction.inset(dp(1), dp(1));

            layout.text.set(0, layout.bubble.top + dp(TEXT_BASELINE) - textView.getLayout().getLineBaseline(0));
            if (messageIsRTL) {
                layout.text.offset(layout.bubble.right - dp(TEXT_MARGIN_START) - messageWidth, 0);
            } else {
                layout.text.offset(layout.bubble.left + dp(TEXT_MARGIN_START), 0);
            }

            return layout;
        }
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final GroupCallMessageCell cell;
        public VH(@NonNull GroupCallMessageCell itemView) {
            super(itemView);
            this.cell = itemView;
        }
    }



    private static final char LRI = '\u2066'; // Left-to-Right Isolate
    private static final char RLI = '\u2067'; // Right-to-Left Isolate
    private static final char PDI = '\u2069'; // Pop Directional Isolate

    public static CharSequence concat(CharSequence name, CharSequence message) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        final boolean nameIsRtl = isRtlByFirstStrong(name);
        final boolean messageIsRtl = isRtlByFirstStrong(message);

        if (nameIsRtl != messageIsRtl) {
            builder.append(messageIsRtl ? RLI : LRI);
            builder.append(name);
            builder.append(PDI);
        } else {
            builder.append(name);
        }
        builder.append("  ");
        builder.append(message);
        return builder;
    }

    private static boolean isRtlByFirstStrong(CharSequence text) {
        int len = text.length();
        for (int i = 0; i < len; ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);

            byte dir = Character.getDirectionality(cp);
            switch (dir) {
                case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
                    return false;
                case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
                case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
                    return true;
                default:
            }
        }
        return false;
    }
}
