package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ChatListItemAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyStubSpan;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

public class TextMessageEnterTransition implements MessageEnterTransitionContainer.Transition {
    float fromRadius;
    float progress;

    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Bitmap textLayoutBitmap;
    Bitmap textLayoutBitmapRtl;
    Bitmap crossfadeTextBitmap;

    boolean hasReply;
    private ValueAnimator animator;
    boolean initBitmaps = false;

    float replyFromStartX;
    float replyFromStartWidth;
    float replyFromStartY;
    float replyFromObjectStartY;
    int replayFromColor;
    int replayObjectFromColor;
    float crossfadeTextOffset;

    float drawableFromTop;

    MessageObject currentMessageObject;

    boolean drawBitmaps = false;
    float toXOffset;
    float toXOffsetRtl;

    boolean crossfade;
    boolean changeColor;

    StaticLayout layout;
    StaticLayout rtlLayout;

    ChatMessageCell messageView;
    RecyclerListView listView;
    MessageEnterTransitionContainer container;
    private Matrix gradientMatrix;
    private Paint gradientPaint;
    private int messageId;
    private float drawableFromBottom;
    private float scaleY;
    private float fromStartX;
    private float fromStartY;
    private ChatActivity chatActivity;
    private LinearGradient gradientShader;
    private float scaleFrom;

    private final int currentAccount;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    MessageObject.TextLayoutBlock textLayoutBlock;
    Drawable fromMessageDrawable;
    ChatActivityEnterView enterView;
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiStack;

    float textX;
    float textY;

    float replyNameDx;

    int fromColor;
    int toColor;
    private final Theme.ResourcesProvider resourcesProvider;

    @SuppressLint("WrongConstant")
    public TextMessageEnterTransition(ChatMessageCell messageView, ChatActivity chatActivity, RecyclerListView listView, MessageEnterTransitionContainer container, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        currentAccount = UserConfig.selectedAccount;
        if (messageView.getMessageObject().textLayoutBlocks == null || messageView.getMessageObject().textLayoutBlocks.size() > 1 || messageView.getMessageObject().textLayoutBlocks.isEmpty() || messageView.getMessageObject().textLayoutBlocks.get(0).textLayout.getLineCount() > 10) {
            return;
        }
        this.messageView = messageView;
        this.listView = listView;
        this.container = container;
        this.chatActivity = chatActivity;
        enterView = chatActivity.getChatActivityEnterView();

        ChatActivityEnterView chatActivityEnterView = chatActivity.getChatActivityEnterView();
        if (chatActivityEnterView == null || chatActivityEnterView.getEditField() == null || chatActivityEnterView.getEditField().getLayout() == null) {
            return;
        }

        ChatActivityEnterView.RecordCircle recordCircle = chatActivityEnterView.getRecordCircle();
        fromRadius = recordCircle == null ? 0 : recordCircle.drawingCircleRadius;
        bitmapPaint.setFilterBitmap(true);
        currentMessageObject = messageView.getMessageObject();

        if (!messageView.getTransitionParams().wasDraw) {
            messageView.draw(new Canvas());
        }

        messageView.setEnterTransitionInProgress(true);

        CharSequence editText = chatActivityEnterView.getEditText();
        CharSequence text = messageView.getMessageObject().messageText;

        crossfade = false;
        int linesOffset = 0;
        int layoutH = chatActivityEnterView.getEditField().getLayout().getHeight();
        TextPaint textPaint = Theme.chat_msgTextPaint;
        int emojiSize = AndroidUtilities.dp(20);
        if (messageView.getMessageObject().getEmojiOnlyCount() != 0) {
            boolean large = messageView.getMessageObject().emojiOnlyCount == messageView.getMessageObject().animatedEmojiCount;
            switch (Math.max(messageView.getMessageObject().emojiOnlyCount, messageView.getMessageObject().animatedEmojiCount)) {
                case 0:
                case 1:
                case 2:
                    textPaint = large ? Theme.chat_msgTextPaintEmoji[0] : Theme.chat_msgTextPaintEmoji[2];
                    break;
                case 3:
                    textPaint = large ? Theme.chat_msgTextPaintEmoji[1] : Theme.chat_msgTextPaintEmoji[3];
                    break;
                case 4:
                    textPaint = large ? Theme.chat_msgTextPaintEmoji[2] : Theme.chat_msgTextPaintEmoji[4];
                    break;
                case 5:
                    textPaint = large ? Theme.chat_msgTextPaintEmoji[3] : Theme.chat_msgTextPaintEmoji[5];
                    break;
                case 6:
                    textPaint = large ? Theme.chat_msgTextPaintEmoji[4] : Theme.chat_msgTextPaintEmoji[5];
                    break;
                case 7:
                case 8:
                case 9:
                default:
                    textPaint = Theme.chat_msgTextPaintEmoji[5];
                    break;
            }
            if (textPaint != null) {
                emojiSize = (int) (textPaint.getTextSize() + AndroidUtilities.dp(4));
            }
        }
        boolean containsSpans = false;
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            Object[] objects = spannable.getSpans(0, text.length(), Object.class);
            containsSpans = objects != null && objects.length > 0;
        }
        if (editText.length() != text.length() || containsSpans) {
            crossfade = true;
            int[] newStart = new int[1];
            CharSequence trimmedStr = AndroidUtilities.trim(editText, newStart);
            if (newStart[0] > 0) {
                linesOffset = chatActivityEnterView.getEditField().getLayout().getLineTop(chatActivityEnterView.getEditField().getLayout().getLineForOffset(newStart[0]));
                layoutH = chatActivityEnterView.getEditField().getLayout().getLineBottom(chatActivityEnterView.getEditField().getLayout().getLineForOffset(newStart[0] + trimmedStr.length())) - linesOffset;
            }
            text = AnimatedEmojiSpan.cloneSpans(text);
            text = Emoji.replaceEmoji(editText, textPaint.getFontMetricsInt(), emojiSize, false);
        }

        scaleFrom = chatActivityEnterView.getEditField().getTextSize() / textPaint.getTextSize();

        int n = chatActivityEnterView.getEditField().getLayout().getLineCount();
        int width = (int) (chatActivityEnterView.getEditField().getLayout().getWidth() / scaleFrom);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                    .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build();
        } else {
            layout = new StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, null, animatedEmojiStack, layout);
        float textViewY = chatActivityEnterView.getY() + chatActivityEnterView.getEditField().getY() + ((View) chatActivityEnterView.getEditField().getParent()).getY() + ((View) chatActivityEnterView.getEditField().getParent().getParent()).getY();
        fromStartX = chatActivityEnterView.getX() + chatActivityEnterView.getEditField().getX() + ((View) chatActivityEnterView.getEditField().getParent()).getX() + ((View) chatActivityEnterView.getEditField().getParent().getParent()).getX();
        fromStartY = textViewY + AndroidUtilities.dp(10) - chatActivityEnterView.getEditField().getScrollY() + linesOffset;
        toXOffset = 0;
        float minX = Float.MAX_VALUE;
        for (int i = 0; i < layout.getLineCount(); i++) {
            float begin = layout.getLineLeft(i);
            if (begin < minX) {
                minX = begin;
            }
        }
        if (minX != Float.MAX_VALUE) {
            toXOffset = minX;
        }

        scaleY = (layoutH) / (layout.getHeight() * scaleFrom);

        drawableFromTop = textViewY + AndroidUtilities.dp(4);
        if (enterView.isTopViewVisible()) {
            drawableFromTop -= AndroidUtilities.dp(12);
        }
        drawableFromBottom = textViewY + chatActivityEnterView.getEditField().getMeasuredHeight();
        textLayoutBlock = messageView.getMessageObject().textLayoutBlocks.get(0);
        StaticLayout messageTextLayout = textLayoutBlock.textLayout;
        int normalLinesCount = 0;
        int rtlLinesCount = 0;

        if (Math.abs(ColorUtils.calculateLuminance(getThemedColor(Theme.key_chat_messageTextOut)) - ColorUtils.calculateLuminance(getThemedColor(Theme.key_chat_messagePanelText))) > 0.2f) {
            crossfade = true;
            changeColor = true;
        }

        fromColor = getThemedColor(Theme.key_chat_messagePanelText);
        toColor = getThemedColor(Theme.key_chat_messageTextOut);

        if (messageTextLayout.getLineCount() == layout.getLineCount()) {
            n = messageTextLayout.getLineCount();
            for (int i = 0; i < n; i++) {
                if (isRtlLine(layout, i)) {
                    rtlLinesCount++;
                } else {
                    normalLinesCount++;
                }
                if (messageTextLayout.getLineEnd(i) != layout.getLineEnd(i)) {
                    crossfade = true;
                    break;
                }
            }
        } else {
            crossfade = true;
        }

        minX = Float.MAX_VALUE;
        if (!crossfade && rtlLinesCount > 0 && normalLinesCount > 0) {
            SpannableString normalText = new SpannableString(text);
            SpannableString rtlText = new SpannableString(text);
            for (int i = 0; i < n; i++) {
                if (isRtlLine(layout, i)) {
                    normalText.setSpan(new EmptyStubSpan(), layout.getLineStart(i), layout.getLineEnd(i), 0);
                    float begin = layout.getLineLeft(i);
                    if (begin < minX) {
                        minX = begin;
                    }
                } else {
                    rtlText.setSpan(new EmptyStubSpan(), layout.getLineStart(i), layout.getLineEnd(i), 0);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                layout = StaticLayout.Builder.obtain(normalText, 0, normalText.length(), textPaint, width)
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build();

                rtlLayout = StaticLayout.Builder.obtain(rtlText, 0, rtlText.length(), textPaint, width)
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build();
            } else {
                layout = new StaticLayout(normalText, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                rtlLayout = new StaticLayout(rtlText, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
            }
        }

        toXOffsetRtl = layout.getWidth() - messageView.getMessageObject().textLayoutBlocks.get(0).textLayout.getWidth();

        try {
            if (drawBitmaps) {
                textLayoutBitmap = Bitmap.createBitmap(layout.getWidth(), layout.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(textLayoutBitmap);
                layout.draw(bitmapCanvas);

                if (rtlLayout != null) {
                    textLayoutBitmapRtl = Bitmap.createBitmap(rtlLayout.getWidth(), rtlLayout.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmapCanvas = new Canvas(textLayoutBitmapRtl);
                    rtlLayout.draw(bitmapCanvas);
                }

                if (crossfade) {
                    if (messageView.getMeasuredHeight() < listView.getMeasuredHeight()) {
                        crossfadeTextOffset = 0;
                        crossfadeTextBitmap = Bitmap.createBitmap(messageView.getMeasuredWidth(), messageView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    } else {
                        crossfadeTextOffset = messageView.getTop();
                        crossfadeTextBitmap = Bitmap.createBitmap(messageView.getMeasuredWidth(), listView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    }
                }
            }
        } catch (Exception e) {
            drawBitmaps = false;
        }

        hasReply = messageView.getMessageObject().getReplyMsgId() != 0 && messageView.replyNameLayout != null;

        if (hasReply) {
            SimpleTextView replyNameTextView = chatActivity.getReplyNameTextView();
            replyFromStartX = replyNameTextView.getX() + ((View) replyNameTextView.getParent()).getX();
            replyFromStartWidth = ((View) replyNameTextView.getParent()).getWidth();
            replyFromStartY = replyNameTextView.getY() + ((View) replyNameTextView.getParent().getParent()).getY() + ((View) replyNameTextView.getParent().getParent().getParent()).getY();
            replyNameTextView = chatActivity.getReplyObjectTextView();
            replyFromObjectStartY = replyNameTextView.getY() + ((View) replyNameTextView.getParent().getParent()).getY() + ((View) replyNameTextView.getParent().getParent().getParent()).getY();

            replayFromColor = chatActivity.getReplyNameTextView().getTextColor();
            replayObjectFromColor = chatActivity.getReplyObjectTextView().getTextColor();
            drawableFromTop -= AndroidUtilities.dp(46);
        }

        gradientMatrix = new Matrix();
        gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        gradientShader = new LinearGradient(0, AndroidUtilities.dp(12), 0, 0, 0, 0xFF000000, Shader.TileMode.CLAMP);
        gradientPaint.setShader(gradientShader);

        messageId = messageView.getMessageObject().stableId;

        chatActivityEnterView.getEditField().setAlpha(0f);
        chatActivityEnterView.setTextTransitionIsRunning(true);

        if (messageView.replyNameLayout != null && messageView.replyNameLayout.getText().length() > 1) {
            if (messageView.replyNameLayout.getPrimaryHorizontal(0) != 0) {
                replyNameDx = messageView.replyNameLayout.getWidth() - messageView.replyNameLayout.getLineWidth(0);
            }
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(valueAnimator -> {
            progress = (float) valueAnimator.getAnimatedValue();
            chatActivityEnterView.getEditField().setAlpha(progress);
            container.invalidate();
        });


        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);

        container.addTransition(this);
        notificationsLocker.lock();

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                notificationsLocker.unlock();
                container.removeTransition(TextMessageEnterTransition.this);
                messageView.setEnterTransitionInProgress(false);
                messageView.getTransitionParams().lastDrawingBackgroundRect.set(messageView.getBackgroundDrawableLeft(), messageView.getBackgroundDrawableTop(), messageView.getBackgroundDrawableRight(), messageView.getBackgroundDrawableBottom());
                chatActivityEnterView.setTextTransitionIsRunning(false);
                chatActivityEnterView.getEditField().setAlpha(1f);
                chatActivity.getReplyNameTextView().setAlpha(1f);
                chatActivity.getReplyObjectTextView().setAlpha(1f);
                AnimatedEmojiSpan.release(null, animatedEmojiStack);
            }
        });

        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH) {
            Theme.MessageDrawable drawable = messageView.getCurrentBackgroundDrawable(true);
            if (drawable != null) {
                fromMessageDrawable = drawable.getTransitionDrawable(getThemedColor(Theme.key_chat_messagePanelBackground));
            }
        }
    }

    public void start() {
        if (animator != null) {
            animator.start();
        }
    }

    private boolean isRtlLine(Layout layout, int line) {
        return layout.getLineRight(line) == layout.getWidth() && layout.getLineLeft(line) != 0;
    }

    float lastMessageX;
    float lastMessageY;

    private final RectF replySelectorRect = new RectF();
    private final RectF messageReplySelectorRect = new RectF();
    private Path replyRoundRect;
    private float[] roundRectRadii;

    public void onDraw(Canvas canvas) {
        if (drawBitmaps && !initBitmaps && crossfadeTextBitmap != null && messageView.getTransitionParams().wasDraw) {
            initBitmaps = true;
            Canvas bitmapCanvas = new Canvas(crossfadeTextBitmap);
            bitmapCanvas.translate(0, crossfadeTextOffset);
            if (messageView.animatedEmojiStack != null) {
                messageView.animatedEmojiStack.clearPositions();
            }
            messageView.drawMessageText(bitmapCanvas, messageView.getMessageObject().textLayoutBlocks, true, 1f, true);
            messageView.drawAnimatedEmojis(bitmapCanvas, 1f);
        }
        float listViewBottom = listView.getY() - container.getY() + listView.getMeasuredHeight();

        float fromX = fromStartX - container.getX();
        float fromY = fromStartY - container.getY();

        textX = messageView.getTextX();
        textY = messageView.getTextY();

        float messageViewX;
        float messageViewY;

        if (messageView.getMessageObject().stableId != messageId) {
            return;
        } else {
            messageViewX = messageView.getX() + listView.getX() - container.getX();
            messageViewY = messageView.getTop() + listView.getTop() - container.getY();
            messageViewY += enterView.getTopViewHeight();

            lastMessageX = messageViewX;
            lastMessageY = messageViewY;
        }

        float progress = ChatListItemAnimator.DEFAULT_INTERPOLATOR.getInterpolation(this.progress);
        float alphaProgress = this.progress > 0.4f ? 1f : this.progress / 0.4f;

        float p2 = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(this.progress);
        float progressX = CubicBezierInterpolator.EASE_OUT.getInterpolation(p2);

        float toX = messageViewX + textX;
        float toY = messageViewY + textY;

        int clipBottom = (int) (container.getMeasuredHeight() * (1f - progressX) + listViewBottom * progressX);
        boolean messageViewOverscrolled = messageView.getBottom() - AndroidUtilities.dp(4) > listView.getMeasuredHeight();
        boolean clipBottomWithAlpha = messageViewOverscrolled && (messageViewY + messageView.getMeasuredHeight() - AndroidUtilities.dp(8) > clipBottom) && container.getMeasuredHeight() > 0;

        if (clipBottomWithAlpha) {
            canvas.saveLayerAlpha(0, Math.max(0, messageViewY), container.getMeasuredWidth(), container.getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
        }
        canvas.save();
        canvas.clipRect(0, listView.getY() + chatActivity.getChatListViewPadding() - container.getY() - AndroidUtilities.dp(3), container.getMeasuredWidth(), container.getMeasuredHeight());
        canvas.save();
        float drawableX = messageViewX + messageView.getBackgroundDrawableLeft() + (fromX - (toX - toXOffset)) * (1f - progressX);
        float drawableToTop = messageViewY + messageView.getBackgroundDrawableTop();
        float drawableTop = (drawableFromTop - container.getY()) * (1f - progress) + (drawableToTop) * progress;
        float drawableH = messageView.getBackgroundDrawableBottom() - messageView.getBackgroundDrawableTop();
        float drawableBottom = (drawableFromBottom - container.getY()) * (1f - progress) + (drawableToTop + drawableH) * progress;
        int drawableRight = (int) (messageViewX + messageView.getBackgroundDrawableRight() + AndroidUtilities.dp(4) * (1f - progressX));
        Theme.MessageDrawable drawable = null;
        if (!currentMessageObject.isAnimatedEmojiStickers()) {
            drawable = messageView.getCurrentBackgroundDrawable(true);
        }


        if (drawable != null) {
            messageView.setBackgroundTopY(container.getTop() - listView.getTop());
            Drawable shadowDrawable = drawable.getShadowDrawable();

            if (alphaProgress != 1f && fromMessageDrawable != null) {
                fromMessageDrawable.setBounds((int) drawableX, (int) drawableTop, drawableRight, (int) drawableBottom);
                fromMessageDrawable.draw(canvas);
            }

            if (shadowDrawable != null) {
                shadowDrawable.setAlpha((int) (255 * progressX));
                shadowDrawable.setBounds((int) drawableX, (int) drawableTop, drawableRight, (int) drawableBottom);
                shadowDrawable.draw(canvas);
                shadowDrawable.setAlpha(255);
            }

            drawable.setAlpha((int) (255 * alphaProgress));
            drawable.setBounds((int) drawableX, (int) drawableTop, drawableRight, (int) drawableBottom);
            drawable.setDrawFullBubble(true);
            drawable.draw(canvas);
            drawable.setDrawFullBubble(false);
            drawable.setAlpha(255);
        }
        canvas.restore();

        canvas.save();
        if (drawable != null) {
            if (currentMessageObject.isOutOwner()) {
                canvas.clipRect(
                        drawableX + AndroidUtilities.dp(4), drawableTop + AndroidUtilities.dp(4),
                        drawableRight - AndroidUtilities.dp(10), drawableBottom - AndroidUtilities.dp(4)
                );
            } else {
                canvas.clipRect(
                        drawableX + AndroidUtilities.dp(4), drawableTop + AndroidUtilities.dp(4),
                        drawableRight - AndroidUtilities.dp(4), drawableBottom - AndroidUtilities.dp(4)
                );
            }
        }
        canvas.translate(messageView.getLeft() + listView.getX() - container.getX(), messageViewY + (fromY - toY) * (1f - progress));
        messageView.drawTime(canvas, alphaProgress, false);
        messageView.drawNamesLayout(canvas, alphaProgress);
        messageView.drawCommentButton(canvas, alphaProgress);
        messageView.drawCaptionLayout(canvas, false, alphaProgress);
        messageView.drawReactionsLayout(canvas, alphaProgress, null);
        messageView.drawCommentLayout(canvas, alphaProgress);
        messageView.drawLinkPreview(canvas, alphaProgress);
        canvas.restore();


        if (hasReply) {
            chatActivity.getReplyNameTextView().setAlpha(0f);
            chatActivity.getReplyObjectTextView().setAlpha(0f);

            float replyHeight = AndroidUtilities.lerp(AndroidUtilities.dp(35), messageView.replyHeight, progressX);
            int offset = AndroidUtilities.dp(10);

            float fromReplayX = replyFromStartX - container.getX();
            float fromReplayY = replyFromStartY - container.getY();
            float toReplayX = messageViewX + messageView.replyStartX;
            float toReplayY = messageViewY + messageView.replyStartY;

            if (messageView.replyLine == null) {
                messageView.replyLine = new ReplyMessageLine(messageView);
            }
            messageView.replyLine.check(messageView.getMessageObject(), messageView.getCurrentUser(), messageView.getCurrentChat(), resourcesProvider, ReplyMessageLine.TYPE_REPLY);

            int replyMessageColor;
            int replyOwnerMessageColor;
            int replyLineColor;

            if (currentMessageObject.shouldDrawWithoutBackground()) {
                replyOwnerMessageColor = getThemedColor(Theme.key_chat_stickerReplyNameText);
                replyLineColor = getThemedColor(Theme.key_chat_stickerReplyLine);
            } else if (messageView.replyLine != null) {
                replyOwnerMessageColor = messageView.replyLine.nameColorAnimated.get();
                replyLineColor = messageView.replyLine.getColor();
            } else if (currentMessageObject.isOutOwner()) {
                replyOwnerMessageColor = getThemedColor(Theme.key_chat_outReplyNameText);
                replyLineColor = getThemedColor(Theme.key_chat_outReplyLine);
            } else {
                replyOwnerMessageColor = getThemedColor(Theme.key_chat_inReplyNameText);
                replyLineColor = getThemedColor(Theme.key_chat_inReplyLine);
            }

            if (!currentMessageObject.shouldDrawWithoutBackground()) {
                if (currentMessageObject.isOutOwner()) {
                    if (currentMessageObject.isReplyToStory()) {
                        replyMessageColor = replyOwnerMessageColor;
                    } else {
                        float blendPressed = 0;
                        int color = getThemedColor(Theme.key_chat_outReplyMessageText);
                        if (!currentMessageObject.forceAvatar && !(currentMessageObject.hasValidReplyMessageObject() && (currentMessageObject.replyMessageObject.type == MessageObject.TYPE_TEXT || !TextUtils.isEmpty(currentMessageObject.replyMessageObject.caption)) && !(MessageObject.getMedia(currentMessageObject.replyMessageObject.messageOwner) instanceof TLRPC.TL_messageMediaGame || MessageObject.getMedia(currentMessageObject.replyMessageObject.messageOwner) instanceof TLRPC.TL_messageMediaInvoice) || messageView.isReplyQuote)) {
                            color = getThemedColor(Theme.key_chat_outReplyMediaMessageText);
                            blendPressed = .6f + (blendPressed * .4f);
                        }
                        replyMessageColor = ColorUtils.blendARGB(color, Theme.adaptHue(color, replyOwnerMessageColor), blendPressed);
                    }
                } else {
                    if (currentMessageObject.isReplyToStory()) {
                        replyMessageColor = replyOwnerMessageColor;
                    } else {
                        float blendPressed = 0;
                        int color = getThemedColor(Theme.key_chat_inReplyMessageText);
                        if (!currentMessageObject.forceAvatar && !(currentMessageObject.hasValidReplyMessageObject() && (currentMessageObject.replyMessageObject.type == MessageObject.TYPE_TEXT || !TextUtils.isEmpty(currentMessageObject.replyMessageObject.caption)) && !(MessageObject.getMedia(currentMessageObject.replyMessageObject.messageOwner) instanceof TLRPC.TL_messageMediaGame || MessageObject.getMedia(currentMessageObject.replyMessageObject.messageOwner) instanceof TLRPC.TL_messageMediaInvoice) || messageView.isReplyQuote)) {
                            color = getThemedColor(Theme.key_chat_inReplyMediaMessageText);
                            blendPressed = .6f + (blendPressed * .4f);
                        }
                        replyMessageColor = ColorUtils.blendARGB(color, Theme.adaptHue(color, replyOwnerMessageColor), blendPressed);
                    }
                }
            } else if (messageView.replyLine != null) {
                replyMessageColor = messageView.replyLine.nameColorAnimated.get();
            } else if (currentMessageObject.hasValidReplyMessageObject() && (currentMessageObject.replyMessageObject.type == MessageObject.TYPE_TEXT || !TextUtils.isEmpty(currentMessageObject.replyMessageObject.caption)) && !(currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || currentMessageObject.replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)) {
                replyMessageColor = getThemedColor(Theme.key_chat_outReplyMessageText);
            } else {
                replyMessageColor = getThemedColor(Theme.key_chat_outReplyMediaMessageText);
            }

            Theme.chat_replyTextPaint.setColor(ColorUtils.blendARGB(replayObjectFromColor, replyMessageColor, progress));
            Theme.chat_replyNamePaint.setColor(ColorUtils.blendARGB(replayFromColor, replyOwnerMessageColor, progress));

            if (messageView.needReplyImage) {
                fromReplayX -= AndroidUtilities.dp(44);
            }
            float replyX = AndroidUtilities.lerp(fromReplayX, toReplayX, progressX);
            float replyY = AndroidUtilities.lerp(fromReplayY + AndroidUtilities.dp(12) * progress, toReplayY, progress);

            if (roundRectRadii == null) {
                roundRectRadii = new float[8];
                roundRectRadii[0] = roundRectRadii[1] = roundRectRadii[6] = roundRectRadii[7] = AndroidUtilities.dp(4); // left
                roundRectRadii[2] = roundRectRadii[3] = roundRectRadii[4] = roundRectRadii[5] = 0; // right
            }

            AndroidUtilities.rectTmp.set(
                fromReplayX, fromReplayY,
                fromReplayX + replyFromStartWidth,
                fromReplayY + AndroidUtilities.dp(35)
            );
            AndroidUtilities.rectTmp.offset(0,  + AndroidUtilities.dp(12) * progress);
            messageReplySelectorRect.set(messageView.replySelectorRect);
            messageReplySelectorRect.offset(messageViewX, messageViewY + (fromY - toY) * (1f - progress));
            AndroidUtilities.lerp(
                AndroidUtilities.rectTmp,
                messageReplySelectorRect,
                progressX,
                replySelectorRect
            );

            messageView.replyLine.drawBackground(canvas, replySelectorRect, alphaProgress, messageView.isReplyQuote, messageView.getMessageObject().shouldDrawWithoutBackground());
            messageView.replyLine.drawLine(canvas, replySelectorRect, alphaProgress);

            float replyImageSz = 0;
            if (messageView.needReplyImage) {
                canvas.save();
                final float sz = Math.min(replySelectorRect.height() - AndroidUtilities.dp(10), AndroidUtilities.dp(messageView.isReplyQuote ? 3 : 7) + Theme.chat_replyNamePaint.getTextSize() + Theme.chat_replyTextPaint.getTextSize());
                replyImageSz = AndroidUtilities.lerp(AndroidUtilities.dp(35), sz, progressX);
                messageView.replyImageReceiver.setImageCoords(
                    AndroidUtilities.lerp(replyX, replySelectorRect.left + AndroidUtilities.dp(8), progressX),
                    AndroidUtilities.lerp(replyY, replySelectorRect.top + AndroidUtilities.dp((messageView.isReplyQuote && messageView.replyTextLayout != null && messageView.replyTextLayout.getLineCount() <= 1 ? 2 : 0) + 5), progressX),
                    replyImageSz, replyImageSz
                );
                messageView.replyImageReceiver.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            canvas.translate(offset * progressX, 0);

            final float offsetX = currentMessageObject != null && currentMessageObject.shouldDrawWithoutBackground() ? -AndroidUtilities.dp(6) : -AndroidUtilities.dp(1);
            final float offsetY = currentMessageObject != null && currentMessageObject.shouldDrawWithoutBackground() ? AndroidUtilities.dp(1) : AndroidUtilities.dp(3);

            float replyToMessageX = toReplayX - messageView.replyTextOffset + offsetX;
            float replyToNameX = toReplayX - replyNameDx + offsetX;

            float replyMessageX = AndroidUtilities.lerp(fromReplayX - messageView.replyTextOffset, replyToMessageX, progressX);
            float replyNameX = AndroidUtilities.lerp(fromReplayX, replyToNameX, progressX) + (messageView.needReplyImage ? replyImageSz + AndroidUtilities.dp(3) : 0);

            if (messageView.replyNameLayout != null) {
                canvas.save();
                canvas.translate(replyNameX, replyY + offsetY * progressX);
                int wasAlpha = Theme.chat_replyNamePaint.getAlpha();
                Theme.chat_replyNamePaint.setAlpha((int) (wasAlpha * progressX));
                messageView.replyNameLayout.draw(canvas);
                Theme.chat_replyNamePaint.setAlpha(wasAlpha);
                final SimpleTextView uiText = chatActivity.getReplyNameTextView();
                canvas.saveLayerAlpha(0, 0, uiText.getWidth(), uiText.getHeight(), (int) (0xFF * (1f - progressX)), Canvas.ALL_SAVE_FLAG);
                uiText.setAlpha(1f);
                uiText.draw(canvas);
                uiText.setAlpha(0f);
                canvas.restore();
                canvas.restore();
            }

            if (messageView.isReplyQuote && messageView.replyQuoteDrawable != null) {
                if (messageView.replyLine.getColor() != messageView.replyQuoteDrawableColor) {
                    messageView.replyQuoteDrawable.setColorFilter(new PorterDuffColorFilter(messageView.replyQuoteDrawableColor = messageView.replyLine.getColor(), PorterDuff.Mode.SRC_IN));
                }
                messageView.replyQuoteDrawable.setBounds(
                    (int) (replySelectorRect.right - offset * progressX - AndroidUtilities.dp(2 + (!messageView.drawPinnedTop ? 1 : 0)) - messageView.replyQuoteDrawable.getIntrinsicWidth()),
                    (int) (replySelectorRect.top + AndroidUtilities.dp(2 + (!messageView.drawPinnedTop ? 1 : 0))),
                    (int) (replySelectorRect.right - offset * progressX - AndroidUtilities.dp(2 + (!messageView.drawPinnedTop ? 1 : 0))),
                    (int) (replySelectorRect.top + AndroidUtilities.dp(2 + (!messageView.drawPinnedTop ? 1 : 0)) + messageView.replyQuoteDrawable.getIntrinsicHeight())
                );
                messageView.replyQuoteDrawable.setAlpha((int) (0xFF * progressX));
                messageView.replyQuoteDrawable.draw(canvas);
            }

            if (messageView.replyTextLayout != null) {
                canvas.save();
                float left = replyToMessageX;
                if (messageView.isReplyQuote && messageView.needReplyImage) {
                    left -= AndroidUtilities.dp(2);
                }
                if (messageView.needReplyImage && (!messageView.isReplyQuote || messageView.replyTextRTL)) {
                    left += replyImageSz + AndroidUtilities.dp(3);
                }
                if (messageView.replyTextRTL && messageView.replyTextOffset > 0) {
                    left = replySelectorRect.right - AndroidUtilities.dp(8) - messageView.replyTextLayout.getWidth() - offset * progressX;
                }
                canvas.translate(AndroidUtilities.lerp(fromReplayX - messageView.replyTextOffset, left, progressX), replyY + AndroidUtilities.lerp(AndroidUtilities.dp(19), Theme.chat_replyNamePaint.getTextSize() + AndroidUtilities.dp(4) + offsetY, progressX));

                canvas.save();
                SpoilerEffect.clipOutCanvas(canvas, messageView.replySpoilers);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, messageView.replyTextLayout, messageView.animatedEmojiReplyStack, 0, messageView.replySpoilers, 0, 0, 0, 1f);
                messageView.replyTextLayout.draw(canvas);
                canvas.restore();

                for (SpoilerEffect eff : messageView.replySpoilers) {
                    if (eff.shouldInvalidateColor()) eff.setColor(messageView.replyTextLayout.getPaint().getColor());
                    eff.draw(canvas);
                }

                canvas.restore();
            }

            canvas.restore();
        }

        canvas.save();

        if (messageView.getMessageObject() == null || messageView.getMessageObject().type != MessageObject.TYPE_EMOJIS) {
            canvas.clipRect(drawableX + AndroidUtilities.dp(4), drawableTop + AndroidUtilities.dp(4), drawableRight - AndroidUtilities.dp(4), drawableBottom - AndroidUtilities.dp(4));
        }

        float scale = progressX + scaleFrom * (1f - progressX);
        float scale2;
        if (drawBitmaps) {
            scale2 = progressX + scaleY * (1f - progressX);
        } else {
            scale2 = 1f;
        }

        canvas.save();
        canvas.translate(fromX * (1f - progressX) + (toX - toXOffset) * progressX, fromY * (1f - progress) + (toY + textLayoutBlock.textYOffset(messageView.getMessageObject().textLayoutBlocks, messageView.transitionParams)) * progress);
        canvas.scale(scale, scale * scale2, 0, 0);

        if (drawBitmaps) {
            if (crossfade) {
                bitmapPaint.setAlpha((int) (255 * (1f - alphaProgress)));
            }
            canvas.drawBitmap(textLayoutBitmap, 0, 0, bitmapPaint);
        } else {
            if (crossfade && changeColor) {
                int oldColor = layout.getPaint().getColor();
                layout.getPaint().setColor(ColorUtils.blendARGB(fromColor, toColor, alphaProgress));
                canvas.saveLayerAlpha(0,0,layout.getWidth(),layout.getHeight(),(int) (255 * (1f - alphaProgress)),Canvas.ALL_SAVE_FLAG);
                layout.draw(canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0, null, 0, 0, 0, 1f - alphaProgress);
                layout.getPaint().setColor(oldColor);
                canvas.restore();
            } else if (crossfade) {
                canvas.saveLayerAlpha(0,0,layout.getWidth(),layout.getHeight(),(int) (255 * (1f - alphaProgress)),Canvas.ALL_SAVE_FLAG);
                layout.draw(canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0, null, 0, 0, 0, 1f - alphaProgress);
                canvas.restore();
            } else {
                layout.draw(canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0, null, 0, 0, 0, 1f);
            }
        }
        canvas.restore();

        if (rtlLayout != null) {
            canvas.save();
            canvas.translate(fromX * (1f - progressX) + (toX - toXOffsetRtl) * progressX, fromY * (1f - progress) + (toY + textLayoutBlock.textYOffset(messageView.getMessageObject().textLayoutBlocks, messageView.transitionParams)) * progress);
            canvas.scale(scale, scale * scale2, 0, 0);
            if (drawBitmaps) {
                if (crossfade) {
                    bitmapPaint.setAlpha((int) (255 * (1f - alphaProgress)));
                }
                canvas.drawBitmap(textLayoutBitmapRtl, 0, 0, bitmapPaint);
            } else {
                if (crossfade && changeColor) {
                    int oldColor = rtlLayout.getPaint().getColor();
                    int oldAlpha = Color.alpha(oldColor);
                    rtlLayout.getPaint().setColor(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(fromColor, toColor, alphaProgress), (int) (oldAlpha * (1f - alphaProgress))));
                    rtlLayout.draw(canvas);
                    rtlLayout.getPaint().setColor(oldColor);
                } else if (crossfade) {
                    int oldAlpha = rtlLayout.getPaint().getAlpha();
                    rtlLayout.getPaint().setAlpha((int) (oldAlpha * (1f - alphaProgress)));
                    rtlLayout.draw(canvas);
                    rtlLayout.getPaint().setAlpha(oldAlpha);
                } else {
                    rtlLayout.draw(canvas);
                }
            }
            canvas.restore();
        }

        if (crossfade) {
            canvas.save();
            canvas.translate(messageView.getLeft() + listView.getX() - container.getX() + (fromX - toX) * (1f - progressX), messageViewY + (fromY - toY) * (1f - progress));
            canvas.scale(scale, scale * scale2, messageView.getTextX(), messageView.getTextY());
            canvas.translate(0, -crossfadeTextOffset);

            if (crossfadeTextBitmap != null) {
                bitmapPaint.setAlpha((int) (255 * alphaProgress));
                canvas.drawBitmap(crossfadeTextBitmap, 0, 0, bitmapPaint);
            } else {
                int oldColor = Theme.chat_msgTextPaint.getColor();
                Theme.chat_msgTextPaint.setColor(toColor);
                messageView.drawMessageText(canvas, messageView.getMessageObject().textLayoutBlocks, true, alphaProgress, true);
                messageView.drawAnimatedEmojis(canvas, alphaProgress);
                if (Theme.chat_msgTextPaint.getColor() != oldColor) {
                    Theme.chat_msgTextPaint.setColor(oldColor);
                }
            }
            canvas.restore();
        }

        canvas.restore();

        if (clipBottomWithAlpha) {
            gradientMatrix.setTranslate(0, clipBottom);
            gradientShader.setLocalMatrix(gradientMatrix);
            canvas.drawRect(0, clipBottom, container.getMeasuredWidth(), container.getMeasuredHeight(), gradientPaint);
            canvas.restore();
        }

        float sendProgress = this.progress > 0.4f ? 1f : this.progress / 0.4f;
        if (sendProgress == 1f) {
            enterView.setTextTransitionIsRunning(false);
        }
        if (enterView.getSendButton().getVisibility() == View.VISIBLE && sendProgress < 1f) {
            canvas.save();
            canvas.translate(enterView.getX() + enterView.getSendButton().getX() + ((View) enterView.getSendButton().getParent()).getX() + ((View) enterView.getSendButton().getParent().getParent()).getX() - container.getX() + AndroidUtilities.dp(52) * sendProgress, enterView.getY() + enterView.getSendButton().getY() + ((View) enterView.getSendButton().getParent()).getY() + ((View) enterView.getSendButton().getParent().getParent()).getY() - container.getY());
            enterView.getSendButton().draw(canvas);
            canvas.restore();
            canvas.restore();
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
