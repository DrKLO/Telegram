package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

public class ReplyMessageLine {

    private final RectF rectF = new RectF();
    private final Path clipPath = new Path();
    private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint color2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint color3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final float[] radii = new float[8];
    private final Path lineClipPath = new Path();
    private final Path backgroundPath = new Path();
    private final Paint backgroundPaint = new Paint();
    private LoadingDrawable backgroundLoadingDrawable;

    public boolean hasColor2, hasColor3;
    private boolean lastHasColor3;
    private float lastHeight;
    private Path color2Path = new Path();
    private Path color3Path = new Path();
    private int switchedCount = 0;

    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;

    private long emojiDocumentId;
    private int backgroundColor, nameColor, color1, color2, color3;
    private final View parentView;
    public final AnimatedColor backgroundColorAnimated;
    public final AnimatedColor color1Animated, color2Animated, color3Animated;
    public final AnimatedColor nameColorAnimated;
    public final AnimatedFloat color2Alpha;
    public final AnimatedFloat color3Alpha;
    public final AnimatedFloat emojiLoadedT;
    public final AnimatedFloat loadingStateT;
    public final AnimatedFloat switchStateT;

    public ReplyMessageLine(View view) {
        this.parentView = view;
        parentView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (emoji != null) {
                    emoji.attach();
                }
            }
            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                if (emoji != null) {
                    emoji.detach();
                }
            }
        });

        backgroundColorAnimated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        color1Animated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        color2Animated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        color3Animated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        nameColorAnimated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        color2Alpha = new AnimatedFloat(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        color3Alpha = new AnimatedFloat(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        emojiLoadedT = new AnimatedFloat(view, 0, 440, CubicBezierInterpolator.EASE_OUT_QUINT);
        loadingStateT = new AnimatedFloat(view, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        switchStateT = new AnimatedFloat(view, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public int getColor() {
        return reversedOut ? color2 : color1;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    private int wasMessageId;
    private int wasColorId;
    private void resolveColor(MessageObject messageObject, int colorId, Theme.ResourcesProvider resourcesProvider) {
        if (wasColorId != colorId) {
            final int msgId = messageObject != null ? messageObject.getId() : 0;
            if (msgId == wasMessageId) {
                switchedCount++;
            }
            wasColorId = colorId;
            wasMessageId = msgId;
        }
        if (colorId < 7) {
            color1 = color2 = color3 = Theme.getColor(Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            hasColor2 = hasColor3 = false;
            return;
        }
        final int currentAccount = messageObject != null ? messageObject.currentAccount : UserConfig.selectedAccount;
        final MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
        final MessagesController.PeerColor peerColor = peerColors != null ? peerColors.getColor(colorId) : null;
        if (peerColor == null) {
            color1 = color2 = color3 = Theme.getColor(messageObject != null && messageObject.isOutOwner() ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine, resourcesProvider);
            hasColor2 = hasColor3 = false;
            return;
        }
        color1 = peerColor.getColor1();
        color2 = peerColor.getColor2();
        color3 = peerColor.getColor3();
        hasColor2 = color2 != color1;
        hasColor3 = color3 != color1;
        if (hasColor3) {
            int temp = color3;
            color3 = color2;
            color2 = temp;
        }
    }

    public static final int TYPE_REPLY = 0;
    public static final int TYPE_QUOTE = 1;
    public static final int TYPE_CODE = 2;
    public static final int TYPE_LINK = 3;

    public int check(MessageObject messageObject, TLRPC.User currentUser, TLRPC.Chat currentChat, Theme.ResourcesProvider resourcesProvider, final int type) {
        reversedOut = false;
        emojiDocumentId = 0;
        if (messageObject == null) {
            hasColor2 = hasColor3 = false;
            color1 = color2 = color3 = Theme.getColor(Theme.key_chat_inReplyLine, resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, Theme.isCurrentThemeDark() ? 0.12f : 0.10f);
            return nameColorAnimated.set(nameColor = Theme.getColor(Theme.key_chat_inReplyNameText, resourcesProvider));
        } else if (type != TYPE_REPLY && (
            messageObject.overrideLinkColor >= 0 ||
            messageObject.messageOwner != null && (
                (messageObject.isFromUser() || DialogObject.isEncryptedDialog(messageObject.getDialogId())) && currentUser != null ||
                messageObject.isFromChannel() && currentChat != null ||
                messageObject.isSponsored() && messageObject.sponsoredChatInvite instanceof TLRPC.TL_chatInvite ||
                messageObject.isSponsored() && messageObject.sponsoredChatInvite != null && messageObject.sponsoredChatInvite.chat != null ||
                messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null
            )
        )) {
            int colorId = 5;
            if (messageObject.overrideLinkColor >= 0) {
                colorId = messageObject.overrideLinkColor;
            } else if (messageObject.isSponsored() && messageObject.sponsoredChatInvite instanceof TLRPC.TL_chatInvite) {
                colorId = messageObject.sponsoredChatInvite.color;
            } else if (messageObject.isSponsored() && messageObject.sponsoredChatInvite != null && messageObject.sponsoredChatInvite.chat != null) {
                if ((messageObject.sponsoredChatInvite.chat.flags2 & 64) != 0) {
                    colorId = messageObject.sponsoredChatInvite.chat.color;
                } else {
                    colorId = (int) (messageObject.sponsoredChatInvite.chat.id % 7);
                }
            } else if (messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
                long dialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.fwd_from.from_id);
                if (dialogId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(-dialogId);
                    if (chat != null) {
                        colorId = (chat.flags2 & 64) != 0 ? chat.color : (int) (chat.id % 7);
                    }
                } else {
                    TLRPC.User user = MessagesController.getInstance(messageObject.currentAccount).getUser(dialogId);
                    if (user != null) {
                        colorId = (user.flags2 & 128) != 0 ? user.color : (int) (user.id % 7);
                    }
                }
            } else if (DialogObject.isEncryptedDialog(messageObject.getDialogId()) && currentUser != null) {
                TLRPC.User user = messageObject.isOutOwner() ? UserConfig.getInstance(messageObject.currentAccount).getCurrentUser() : currentUser;
                if (user == null) user = currentUser;
                if ((user.flags2 & 128) != 0) {
                    colorId = user.color;
                } else {
                    colorId = (int) (user.id % 7);
                }
            } else if (messageObject.isFromUser() && currentUser != null) {
                if ((currentUser.flags2 & 128) != 0) {
                    colorId = currentUser.color;
                } else {
                    colorId = (int) (currentUser.id % 7);
                }
            } else if (messageObject.isFromChannel() && currentChat != null) {
                if ((currentChat.flags2 & 64) != 0) {
                    colorId = currentChat.color;
                } else {
                    colorId = (int) (currentChat.id % 7);
                }
            } else {
                colorId = 0;
            }
            resolveColor(messageObject, colorId, resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, 0.10f);
            nameColor = color1;
        } else if (type == TYPE_REPLY && (
            messageObject.overrideLinkColor >= 0 ||
            messageObject.messageOwner != null &&
            messageObject.replyMessageObject != null &&
            messageObject.messageOwner.reply_to != null && (messageObject.messageOwner.reply_to.reply_from == null || TextUtils.isEmpty(messageObject.messageOwner.reply_to.reply_from.from_name)) &&
            messageObject.replyMessageObject.messageOwner != null &&
            messageObject.replyMessageObject.messageOwner.from_id != null && (
                messageObject.replyMessageObject.isFromUser() ||
                DialogObject.isEncryptedDialog(messageObject.getDialogId()) ||
                messageObject.replyMessageObject.isFromChannel()
        ))) {
            int colorId;
            if (messageObject.overrideLinkColor >= 0) {
                colorId = messageObject.overrideLinkColor;
            } else if (DialogObject.isEncryptedDialog(messageObject.replyMessageObject.getDialogId())) {
                TLRPC.User user = messageObject.replyMessageObject.isOutOwner() ? UserConfig.getInstance(messageObject.replyMessageObject.currentAccount).getCurrentUser() : currentUser;
                if (user != null) {
                    colorId = (user.flags2 & 128) != 0 ? user.color : (int) (user.id % 7);
                    if ((user.flags2 & 64) != 0) {
                        emojiDocumentId = user.background_emoji_id;
                    }
                } else {
                    colorId = 0;
                }
            } else if (messageObject.replyMessageObject.isFromUser()) {
                TLRPC.User user = MessagesController.getInstance(messageObject.currentAccount).getUser(messageObject.replyMessageObject.messageOwner.from_id.user_id);
                if (user != null) {
                    colorId = (user.flags2 & 128) != 0 ? user.color : (int) (user.id % 7);
                    if ((user.flags2 & 64) != 0) {
                        emojiDocumentId = user.background_emoji_id;
                    }
                } else {
                    colorId = 0;
                }
            } else if (messageObject.replyMessageObject.isFromChannel()) {
                TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(messageObject.replyMessageObject.messageOwner.from_id.channel_id);
                if (chat != null) {
                    colorId = (chat.flags2 & 64) != 0 ? chat.color : (int) (chat.id % 7);
                    if ((chat.flags2 & 32) != 0) {
                        emojiDocumentId = chat.background_emoji_id;
                    }
                } else {
                    colorId = 0;
                }
            } else {
                colorId = 0;
            }
            resolveColor(messageObject.replyMessageObject, colorId, resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, 0.10f);
            nameColor = color1;
        } else {
            hasColor2 = false;
            hasColor3 = false;
            color1 = color2 = color3 = Theme.getColor(Theme.key_chat_inReplyLine, resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, 0.10f);
            nameColor = Theme.getColor(Theme.key_chat_inReplyNameText, resourcesProvider);
        }
        if (messageObject.shouldDrawWithoutBackground()) {
            hasColor2 = false;
            hasColor3 = false;
            color1 = color2 = color3 = Color.WHITE;
            backgroundColor = Color.TRANSPARENT;
            nameColor = Theme.getColor(Theme.key_chat_stickerReplyNameText, resourcesProvider);
        } else if (messageObject.isOutOwner()) {
            color1 = color2 = color3 = Theme.getColor(hasColor2 || hasColor3 ? Theme.key_chat_outReplyLine2 : Theme.key_chat_outReplyLine, resourcesProvider);
            if (hasColor3) {
                reversedOut = true;
                color1 = Theme.multAlpha(color1, .20f);
                color2 = Theme.multAlpha(color2, .50f); // 50% over 20% = 60%
            } else if (hasColor2) {
                reversedOut = true;
                color1 = Theme.multAlpha(color1, .35f);
            }
            backgroundColor = Theme.multAlpha(color3, Theme.isCurrentThemeDark() ? 0.12f : 0.10f);
            nameColor = Theme.getColor(Theme.key_chat_outReplyNameText, resourcesProvider);
        }
        if (type == TYPE_REPLY && messageObject != null && messageObject.overrideLinkEmoji != -1) {
            emojiDocumentId = messageObject.overrideLinkEmoji;
        }
        if (emojiDocumentId != 0 && emoji == null) {
            emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parentView, false, dp(20), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            if (parentView instanceof ChatMessageCell ? ((ChatMessageCell) parentView).isCellAttachedToWindow() : parentView.isAttachedToWindow()) {
                emoji.attach();
            }
        }
        if (emoji != null) {
            if (emoji.set(emojiDocumentId, true)) {
                emojiLoaded = false;
            }
        }
        return nameColorAnimated.set(nameColor);
    }

    public void resetAnimation() {
        color1Animated.set(color1, true);
        color2Animated.set(color2, true);
        color2Alpha.set(hasColor2, true);
        nameColorAnimated.set(nameColor, true);
        backgroundColorAnimated.set(backgroundColor, true);
        if (emoji != null) {
            emoji.resetAnimation();
        }
    }

    private boolean reversedOut;
    private boolean loading;
    private float loadingT;
    private float loadingTranslationT;

    public void setLoading(boolean loading) {
        if (!loading && this.loading) {
            loadingT = 0;
            if (backgroundLoadingDrawable != null) {
                backgroundLoadingDrawable.disappear();
            }
        } else if (loading && !this.loading) {
            if (backgroundLoadingDrawable != null) {
                backgroundLoadingDrawable.resetDisappear();
                backgroundLoadingDrawable.reset();
            }
        }
        this.loading = loading;
    }

    private long lastLoadingTTime;
    private void incrementLoadingT() {
        final long now = System.currentTimeMillis();
        final float loadingAlpha = loadingStateT.set(loading);
        loadingT += Math.min(30, now - lastLoadingTTime) * loadingAlpha;
        loadingTranslationT += Math.min(30, now - lastLoadingTTime) * loadingAlpha;
        lastLoadingTTime = now;
    }

    public void drawLine(Canvas canvas, RectF rect) {
        drawLine(canvas, rect, 1f);
    }

    public void drawLine(Canvas canvas, RectF rect, float alpha) {
        canvas.save();

        clipPath.rewind();
        final int rad = (int) Math.floor(SharedConfig.bubbleRadius / 3f);
        rectF.set(rect.left, rect.top, rect.left + Math.max(dp(3), dp(2 * rad)), rect.bottom);
        clipPath.addRoundRect(rectF, dp(rad), dp(rad), Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.clipRect(rect.left, rect.top, rect.left + dp(3), rect.bottom);

        color1Paint.setColor(Theme.multAlpha(color1Animated.set(color1), alpha));
        color2Paint.setColor(Theme.multAlpha(color2Animated.set(color2), alpha));
        color3Paint.setColor(Theme.multAlpha(color3Animated.set(color3), alpha));

        boolean restore = false;
        final float loadingAlpha = loadingStateT.set(loading);
        if (loadingAlpha > 0 && !hasColor2) {
            canvas.save();

            // under line
            int wasAlpha = color1Paint.getAlpha();
            color1Paint.setAlpha((int) (wasAlpha * .3f));
            canvas.drawPaint(color1Paint);
            color1Paint.setAlpha(wasAlpha);

            incrementLoadingT();

            float x = (float) Math.pow(loadingT / 240f / 4f, .85f) * 4f;
            final float from = MathUtils.clamp(.5f * ((Math.max(x, .5f) + 1.5f) % 3.5f), 0, 1);
            final float to   = MathUtils.clamp(.5f * (((x + 1.5f) % 3.5f) - 1.5f), 0, 1);

            rectF.set(
                rect.left,
                rect.top + rect.height() * AndroidUtilities.lerp(0, 1f - CubicBezierInterpolator.EASE_IN.getInterpolation(from), loadingAlpha),
                rect.left + dp(6),
                rect.top + rect.height() * AndroidUtilities.lerp(1f, 1f - CubicBezierInterpolator.EASE_OUT.getInterpolation(to), loadingAlpha)
            );
            lineClipPath.rewind();
            lineClipPath.addRoundRect(rectF, dp(4), dp(4), Path.Direction.CW);
            canvas.clipPath(lineClipPath);
            restore = true;

            parentView.invalidate();
        }
        canvas.drawPaint(color1Paint);
        final float color2Alpha = this.color2Alpha.set(hasColor2);
        if (color2Alpha > 0) {
            canvas.save();
            canvas.translate(rect.left, rect.top);
            incrementLoadingT();

            final float color3Alpha = this.color3Alpha.set(hasColor3);
            final float fh;
            if (hasColor3) {
                fh = rect.height() - Math.floorMod((int) rect.height(), dp(6.33f + 6.33f + 3 + 3.33f));
            } else {
                fh = rect.height() - Math.floorMod((int) rect.height(), dp(6.33f + 3 + 3.33f));
            }

            canvas.translate(0, -((loadingTranslationT + switchStateT.set(switchedCount * 425) + (reversedOut ? 100 : 0)) / 1000f * dp(30) % fh));

            checkColorPathes(rect.height() * 2);
            int wasAlpha = color2Paint.getAlpha();
            color2Paint.setAlpha((int) (wasAlpha * color2Alpha));
            canvas.drawPath(color2Path, color2Paint);
            color2Paint.setAlpha(wasAlpha);

            wasAlpha = color3Paint.getAlpha();
            color3Paint.setAlpha((int) (wasAlpha * color3Alpha));
            canvas.drawPath(color3Path, color3Paint);
            color3Paint.setAlpha(wasAlpha);

            canvas.restore();
        }

        if (restore) {
            canvas.restore();
        }

        canvas.restore();
    }

    public void drawBackground(Canvas canvas, RectF rect, float leftRad, float rightRad, float bottomRad, float alpha) {
        drawBackground(canvas, rect, leftRad, rightRad, bottomRad, alpha, false, false);
    }

    public void drawBackground(Canvas canvas, RectF rect, float leftRad, float rightRad, float bottomRad, float alpha, boolean hasQuote, boolean emojiOnly) {
        radii[0] = radii[1] = Math.max(AndroidUtilities.dp((int) Math.floor(SharedConfig.bubbleRadius / 3f)), AndroidUtilities.dp(leftRad));
        radii[2] = radii[3] = AndroidUtilities.dp(rightRad);
        radii[4] = radii[5] = AndroidUtilities.dp(bottomRad);
        radii[6] = radii[7] = Math.max(AndroidUtilities.dp((int) Math.floor(SharedConfig.bubbleRadius / 3f)), AndroidUtilities.dp(bottomRad));
        drawBackground(canvas, rect, alpha, hasQuote, emojiOnly);
    }

    private static class IconCoords {
        public float x, y, s, a;
        public boolean q;
        public IconCoords(float x, float y, float s, float a, boolean q) {
            this(x, y, s, a);
            this.q = q;
        }
        public IconCoords(float x, float y, float s, float a) {
            this.x = x;
            this.y = y;
            this.s = s;
            this.a = a;
        }
    }

    private IconCoords[] iconCoords;

    public void drawBackground(Canvas canvas, RectF rect, float alpha) {
        drawBackground(canvas, rect, alpha, false, false);
    }

    public void drawBackground(Canvas canvas, RectF rect, float alpha, boolean hasQuote, boolean emojiOnly) {
        if (!emojiOnly) {
            backgroundPath.rewind();
            backgroundPath.addRoundRect(rect, radii, Path.Direction.CW);

            backgroundPaint.setColor(backgroundColorAnimated.set(backgroundColor));
            backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * alpha));
            canvas.drawPath(backgroundPath, backgroundPaint);
        }

        if (emoji != null) {
            final float loadedScale = emojiLoadedT.set(isEmojiLoaded());

            if (loadedScale > 0) {
                if (iconCoords == null) {
                    iconCoords = new IconCoords[]{
                        new IconCoords(4, -6.33f, 1f, 1f),
                        new IconCoords(30, 3, .78f, .9f),
                        new IconCoords(46, -17, .6f, .6f),
                        new IconCoords(69.66f, -0.666f, .87f, .7f),
                        new IconCoords(107, -12.6f, 1.03f, .3f),
                        new IconCoords(51, 24, 1f, .5f),
                        new IconCoords(6.33f, 20, .77f, .7f),
                        new IconCoords(-19, 12, .8f, .6f, true),
                        new IconCoords(26, 42, .78f, .9f),
                        new IconCoords(-22, 36, .7f, .5f, true),
                        new IconCoords(-1, 48, 1f, .4f),
                    };
                }

                canvas.save();
                canvas.clipRect(rect);

                float x0 = Math.max(rect.right - dp(15), rect.centerX());
                if (hasQuote) {
                    x0 -= dp(12);
                }
                float y0 = Math.min(rect.centerY(), rect.top + dp(42 / 2));

                emoji.setColor(getColor());
                emoji.setAlpha((int) (0xFF * alpha * (rect.width() < dp(140) ? .3f : .5f )));
                for (int i = 0; i < iconCoords.length; ++i) {
                    IconCoords c = iconCoords[i];
                    if (c.q && !hasQuote) {
                        continue;
                    }
                    emoji.setAlpha((int) (0xFF * .30f * c.a));
                    final float cx = x0 - dp(c.x);
                    final float cy = y0 + dp(c.y);
                    final float sz = dp(10) * c.s * loadedScale;
                    emoji.setBounds((int) (cx - sz), (int) (cy - sz), (int) (cx + sz), (int) (cy + sz));
                    emoji.draw(canvas);
                }

                canvas.restore();
            }
        }
    }

    private boolean emojiLoaded;
    private boolean isEmojiLoaded() {
        if (emojiLoaded) {
            return true;
        }
        if (emoji != null && emoji.getDrawable() instanceof AnimatedEmojiDrawable) {
            AnimatedEmojiDrawable drawable = (AnimatedEmojiDrawable) emoji.getDrawable();
            if (drawable.getImageReceiver() != null && drawable.getImageReceiver().hasImageLoaded()) {
                return emojiLoaded = true;
            }
        }
        return false;
    }

    public void drawLoadingBackground(Canvas canvas, RectF rect, float leftRad, float rightRad, float bottomRad, float alpha) {
        radii[0] = radii[1] = Math.max(AndroidUtilities.dp((int) Math.floor(SharedConfig.bubbleRadius / 3f)), AndroidUtilities.dp(leftRad));
        radii[2] = radii[3] = AndroidUtilities.dp(rightRad);
        radii[4] = radii[5] = AndroidUtilities.dp(bottomRad);
        radii[6] = radii[7] = Math.max(AndroidUtilities.dp((int) Math.floor(SharedConfig.bubbleRadius / 3f)), AndroidUtilities.dp(bottomRad));

        if (loading || backgroundLoadingDrawable != null && backgroundLoadingDrawable.isDisappearing()) {
            if (backgroundLoadingDrawable == null) {
                backgroundLoadingDrawable = new LoadingDrawable();
                backgroundLoadingDrawable.setAppearByGradient(true);
                backgroundLoadingDrawable.setGradientScale(3.5f);
                backgroundLoadingDrawable.setSpeed(.5f);
            }

            backgroundLoadingDrawable.setColors(
                Theme.multAlpha(color1, .1f),
                Theme.multAlpha(color1, .3f),
                Theme.multAlpha(color1, .3f),
                Theme.multAlpha(color1, 1.25f)
            );

            backgroundLoadingDrawable.setBounds(rect);
            backgroundLoadingDrawable.setRadii(radii);
            backgroundLoadingDrawable.strokePaint.setStrokeWidth(AndroidUtilities.dp(1));

            backgroundLoadingDrawable.setAlpha((int) (0xFF * alpha));
            backgroundLoadingDrawable.draw(canvas);

            parentView.invalidate();
        } else if (backgroundLoadingDrawable != null) {
            backgroundLoadingDrawable.reset();
        }
    }

    private void checkColorPathes(float height) {
        if (Math.abs(lastHeight - height) > 3 || lastHasColor3 != hasColor3) {
            final float w = dpf2(3);
            final float h = dpf2(6.33f);
            final float sk = dpf2(3);
            final float margin = dpf2(3.33f);
            float y = margin + sk;

            color2Path.rewind();
            while (y < height) {
                color2Path.moveTo(w + 1, y - 1);
                color2Path.lineTo(w + 1, y + h);
                color2Path.lineTo(0, y + h + sk);
                color2Path.lineTo(0, y + sk);
                color2Path.close();

                y += h + sk + margin;
                if (hasColor3) {
                    y += h;
                }
            }

            if (hasColor3) {
                y = margin + sk + h;
                color3Path.rewind();
                while (y < height) {
                    color3Path.moveTo(w + 1, y - 1);
                    color3Path.lineTo(w + 1, y + h);
                    color3Path.lineTo(0, y + h + sk);
                    color3Path.lineTo(0, y + sk);
                    color3Path.close();

                    y += h + sk + margin + h;
                }
            }

            lastHeight = height;
            lastHasColor3 = hasColor3;
        }
    }
}
