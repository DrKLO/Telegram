package org.telegram.ui.Components.Forum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.StaticLayoutEx;

public class MessageTopicButton {

    private final static float[] lightHueRanges = { 0,    43,   56,   86,   169,  183,  249,  289,  360 };
    private final static float[] lightSatValues = { .60f, 1f,   .95f, .98f, .80f, .88f, .51f, .55f, .60f };
    private final static float[] lightValValues = { .79f, .77f, .60f, .62f, .60f, .61f, .80f, .70f, .79f };

    private final static float[] darkHueRanges = { 0,    43,   56,   63,   86,   122,  147,  195,  205,  249,   270,  312,  388,  360 };
    private final static float[] darkSatValues = { .64f, .89f, .84f, .87f, .74f, .66f, .81f, .81f, .71f, .51f,  .61f, .55f, .62f, .64f };
    private final static float[] darkValValues = { .92f, .90f, .82f, .82f, .84f, .84f, .82f, .88f, .96f, .100f, .93f, .88f, .96f, .92f };

    private int topicWidth;
    private int topicHeight;
    private boolean topicClosed;
    private Drawable topicClosedDrawable;
    private Paint topicPaint;
    private boolean isGeneralTopic;
    private Path topicPath;
    private boolean topicArrowDrawableVisible;
    private Drawable topicArrowDrawable;
    private Drawable topicSelectorDrawable;
    private Drawable topicIconDrawable;
    private Rect topicIconDrawableBounds;
    private float[] topicHSV;
    private int topicBackgroundColor;
    private int topicNameColor;
    private int topicArrowColor;
    private AnimatedColor topicBackgroundColorAnimated, topicNameColorAnimated;
    private boolean topicIconWaiting;
    private StaticLayout topicNameLayout;
    private float topicNameLeft;
    private RectF topicHitRect;
    private boolean topicPressed;
    private MessageObject lastMessageObject;

    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;
    private int avatarSize;

    private Context context;
    private Theme.ResourcesProvider resourcesProvider;

    private final static int[] idleState = new int[]{};
    private final static int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    public MessageTopicButton(Context context, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
    }

    protected void onClick() {}

    public int set(ChatMessageCell cell, MessageObject messageObject, @NonNull TLObject userOrChat, int maxWidth) {
        if (cell == null || messageObject == null) {
            return 0;
        }
        isGeneralTopic = false;
        topicClosed = false;
        String title;
        if (userOrChat instanceof TLRPC.User) {
            title = UserObject.getForcedFirstName((TLRPC.User) userOrChat);
        } else {
            title = ContactsController.formatName(userOrChat);
        }
        topicIconDrawable = null;

        avatarSize = AndroidUtilities.dp(11) + (int) Theme.chat_topicTextPaint.getTextSize();
        float avatarScale = (float) avatarSize / AndroidUtilities.dp(56);
        avatarDrawable = new AvatarDrawable();
        imageReceiver = new ImageReceiver(cell);
        imageReceiver.setRoundRadius(avatarSize / 2);
        if (userOrChat instanceof TLRPC.User && UserObject.isReplyUser((TLRPC.User) userOrChat)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
            avatarDrawable.setScaleSize(avatarScale);
            imageReceiver.setImage(null, null, avatarDrawable, null, userOrChat, 0);
            title = LocaleController.getString(R.string.RepliesTitle);
        } else if (userOrChat instanceof TLRPC.User && UserObject.isUserSelf((TLRPC.User) userOrChat)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_MY_NOTES);
            avatarDrawable.setScaleSize(avatarScale);
            imageReceiver.setImage(null, null, avatarDrawable, null, userOrChat, 0);
            title = LocaleController.getString(R.string.MyNotes);
        } else {
            avatarDrawable.setInfo(messageObject.currentAccount, userOrChat);
            imageReceiver.setForUserOrChat(userOrChat, avatarDrawable);
        }

        if (messageObject.isOutOwner()) {
            topicNameColor = getThemedColor(Theme.key_chat_outReplyNameText);
        } else {
            int colorId = 0;
            if (userOrChat instanceof TLRPC.User) {
                colorId = UserObject.getColorId((TLRPC.User) userOrChat);
            } else if (userOrChat instanceof TLRPC.Chat) {
                colorId = ChatObject.getColorId((TLRPC.Chat) userOrChat);
            }
            if (colorId < 7) {
                topicNameColor = getThemedColor(Theme.keys_avatar_nameInMessage[colorId]);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(messageObject.currentAccount).peerColors;
                MessagesController.PeerColor peerColor = peerColors != null ? peerColors.getColor(colorId) : null;
                if (peerColor != null) {
                    topicNameColor = peerColor.getColor(0, resourcesProvider);
                } else {
                    topicNameColor = getThemedColor(Theme.key_chat_inReplyNameText);
                }
            }
        }
        boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        topicBackgroundColor = Theme.multAlpha(topicNameColor, dark ? 0.12f : 0.10f);


        return setInternal(cell, messageObject, maxWidth, title, 1);
    }

    public int set(ChatMessageCell cell, MessageObject messageObject, @NonNull TLRPC.TL_forumTopic topic, int maxWidth) {
        if (cell == null || messageObject == null) {
            return 0;
        }
        isGeneralTopic = topic.id == 1;
        topicClosed = topic.closed;
        String title = topic.title == null ? "" : topic.title;
        int iconColor;
        if (isGeneralTopic) {
            iconColor = getThemedColor(messageObject != null && messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonText : Theme.key_chat_inReactionButtonText);
            topicIconDrawable = ForumUtilities.createGeneralTopicDrawable(context, .65f, iconColor, false);
        } else if (topic.icon_emoji_id != 0) {
            if (!(topicIconDrawable instanceof AnimatedEmojiDrawable) || topic.icon_emoji_id != ((AnimatedEmojiDrawable) topicIconDrawable).getDocumentId()) {
                if (topicIconDrawable instanceof AnimatedEmojiDrawable) {
                    ((AnimatedEmojiDrawable) topicIconDrawable).removeView(cell::invalidateOutbounds);
                    topicIconDrawable = null;
                }
                topicIconDrawable = AnimatedEmojiDrawable.make(messageObject.currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, topic.icon_emoji_id);
                ((AnimatedEmojiDrawable) topicIconDrawable).addView(cell::invalidateOutbounds);
            }
            topicIconWaiting = false;
            iconColor = topicIconDrawable instanceof AnimatedEmojiDrawable ? AnimatedEmojiDrawable.getDominantColor((AnimatedEmojiDrawable) topicIconDrawable) : 0;
            if (iconColor == 0) {
                topicIconWaiting = true;
                iconColor = getThemedColor(messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonText : Theme.key_chat_inReactionButtonText);
            }
        } else {
            iconColor = topic.icon_color;
            topicIconDrawable = ForumUtilities.createSmallTopicDrawable(title, topic.icon_color);
        }
        setupColors(iconColor);

        return setInternal(cell, messageObject, maxWidth, title, 2);
    }

    private int setInternal(ChatMessageCell cell, MessageObject messageObject, int maxWidth, String title, int maxLines) {
        lastMessageObject = messageObject;

        int iconsz = AndroidUtilities.dp(7) + (int) Theme.chat_topicTextPaint.getTextSize();
        float padleft = AndroidUtilities.dp(isGeneralTopic ? 6 : 10) + iconsz;
        float padright1 = Theme.chat_topicTextPaint.getTextSize() - AndroidUtilities.dp(8);
        float padright = AndroidUtilities.dp(5) + Theme.chat_topicTextPaint.getTextSize();
        maxWidth -= padleft + padright;
        if (topicClosed) {
            maxWidth -= AndroidUtilities.dp(18);
        }
        topicNameLayout = StaticLayoutEx.createStaticLayout(title, Theme.chat_topicTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false, TextUtils.TruncateAt.END, maxWidth, maxLines, false);
        topicHeight = AndroidUtilities.dp(4 + 4.5f) + Math.min(AndroidUtilities.dp(24), topicNameLayout == null ? 0 : topicNameLayout.getHeight());
        float textWidth = 0;
        int lineCount = topicNameLayout == null ? 0 : topicNameLayout.getLineCount();
        if (topicPath == null) {
            topicPath = new Path();
        } else {
            topicPath.rewind();
        }
        if (topicPaint == null) {
            topicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        if (topicIconWaiting) {
            if (topicNameColorAnimated == null) {
                topicNameColorAnimated = new AnimatedColor(cell);
            }
            if (topicBackgroundColorAnimated == null) {
                topicBackgroundColorAnimated = new AnimatedColor(cell);
            }
        }
        if (topicArrowDrawable == null) {
            topicArrowDrawable = context.getResources().getDrawable(R.drawable.msg_mini_topicarrow).mutate();
        }
        topicArrowDrawable.setColorFilter(new PorterDuffColorFilter(topicArrowColor = ColorUtils.setAlphaComponent(topicNameColor, 140), PorterDuff.Mode.MULTIPLY));
        if (topicClosedDrawable == null) {
            topicClosedDrawable = context.getResources().getDrawable(R.drawable.msg_mini_lock2).mutate();
        }
        topicClosedDrawable.setColorFilter(new PorterDuffColorFilter(topicArrowColor = ColorUtils.setAlphaComponent(topicNameColor, 140), PorterDuff.Mode.MULTIPLY));

        float R = (AndroidUtilities.dp(11) + (int) Theme.chat_topicTextPaint.getTextSize());
        int arrowsz = Math.max(1, (int) Theme.chat_topicTextPaint.getTextSize() + AndroidUtilities.dp(0));
        int locksz = Math.max(1, (int) Theme.chat_topicTextPaint.getTextSize() - AndroidUtilities.dp(4));
        if (lineCount == 2) {
            topicHeight = AndroidUtilities.dp(15) + 2 * ((int) Theme.chat_topicTextPaint.getTextSize());
            float l1w = Math.abs(topicNameLayout.getLineRight(0) - topicNameLayout.getLineLeft(0));
            float l2w = Math.abs(topicNameLayout.getLineRight(1) - topicNameLayout.getLineLeft(1));
            if (topicClosed) {
                l2w += AndroidUtilities.dp(4) + locksz;
            }
            topicNameLeft = Math.min(topicNameLayout.getLineLeft(0), topicNameLayout.getLineLeft(1));
            boolean isRTL = topicNameLeft != 0;
            textWidth = Math.max(l1w, l2w);
            float r = (AndroidUtilities.dp(11) + (int) Theme.chat_topicTextPaint.getTextSize()) / 1.5f;
            boolean same = false;
            AndroidUtilities.rectTmp.set(0, 0, R, R);
            topicPath.arcTo(AndroidUtilities.rectTmp, 180, 90);
            if (Math.abs(l1w - l2w) <= (padright - padright1) || isRTL) {
                l1w = Math.max(l1w, l2w + (padright - padright1));
                l2w = Math.max(l2w, l1w - (padright - padright1));
                same = true;
            }
            AndroidUtilities.rectTmp.set(padleft + padright1 + l1w - r, 0, padleft + padright1 + l1w, r);
            topicPath.arcTo(AndroidUtilities.rectTmp, 270, 90);
            float midly = AndroidUtilities.dp(11) + Theme.chat_topicTextPaint.getTextSize();
            float r2 = Math.min(r, Math.abs(l1w - AndroidUtilities.dp(18 - 5) - l2w));
            if (!same) {
                if (l1w - (padright - padright1) > l2w) {
                    AndroidUtilities.rectTmp.set(padleft + padright1 + l1w - r2, midly - r2, padleft + padright1 + l1w, midly);
                    topicPath.arcTo(AndroidUtilities.rectTmp, 0, 90);
                    AndroidUtilities.rectTmp.set(padleft + padright + l2w, midly, padleft + padright + l2w + r2, midly + r2);
                    topicPath.arcTo(AndroidUtilities.rectTmp, 270, -90);
                } else {
                    midly = topicHeight - midly;
                    AndroidUtilities.rectTmp.set(padleft + padright1 + l1w, midly - r2, padleft + padright1 + l1w + r2, midly);
                    topicPath.arcTo(AndroidUtilities.rectTmp, 180, -90);
                    AndroidUtilities.rectTmp.set(padleft + padright + l2w - r2, midly, padleft + padright + l2w, midly + r2);
                    topicPath.arcTo(AndroidUtilities.rectTmp, 270, 90);
                }
            }
            topicArrowDrawableVisible = !isRTL;
            topicArrowDrawable.setBounds(
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + l2w - arrowsz),
                    (int) ((topicHeight - AndroidUtilities.dp(11) - Theme.chat_topicTextPaint.getTextSize() + topicHeight) / 2f - arrowsz / 2),
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + l2w),
                    (int) ((topicHeight - AndroidUtilities.dp(11) - Theme.chat_topicTextPaint.getTextSize() + topicHeight) / 2f + arrowsz / 2)
            );
            topicClosedDrawable.setBounds(
                    (int) (padleft + padright + AndroidUtilities.dp(-4) - arrowsz + l2w - locksz),
                    (int) ((topicHeight - AndroidUtilities.dp(11) - Theme.chat_topicTextPaint.getTextSize() + topicHeight) / 2f - locksz / 2),
                    (int) (padleft + padright + AndroidUtilities.dp(-4) - arrowsz + l2w),
                    (int) ((topicHeight - AndroidUtilities.dp(11) - Theme.chat_topicTextPaint.getTextSize() + topicHeight) / 2f + locksz / 2)
            );
            AndroidUtilities.rectTmp.set(padleft + padright + l2w - r, topicHeight - r, padleft + padright + l2w, topicHeight);
            topicPath.arcTo(AndroidUtilities.rectTmp, 0, 90);
            AndroidUtilities.rectTmp.set(0, topicHeight - R, R, topicHeight);
            topicPath.arcTo(AndroidUtilities.rectTmp, 90, 90);
            topicPath.close();
        } else if (lineCount == 1) {
            topicHeight = AndroidUtilities.dp(11) + (int) Theme.chat_topicTextPaint.getTextSize();
            textWidth = Math.abs(topicNameLayout.getLineRight(0) - topicNameLayout.getLineLeft(0));
            if (topicClosed) {
                textWidth += AndroidUtilities.dp(4) + locksz;
            }
            topicNameLeft = topicNameLayout.getLineLeft(0);
            AndroidUtilities.rectTmp.set(0, 0, padleft + padright + textWidth, topicHeight);
            topicArrowDrawableVisible = true;
            topicArrowDrawable.setBounds(
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth - arrowsz),
                    (int) (topicHeight / 2f - arrowsz / 2),
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth),
                    (int) (topicHeight / 2f + arrowsz / 2)
            );
            topicClosedDrawable.setBounds(
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth - arrowsz - locksz),
                    (int) (topicHeight / 2f - locksz / 2),
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth - arrowsz),
                    (int) (topicHeight / 2f + locksz / 2)
            );
            topicPath.addRoundRect(AndroidUtilities.rectTmp, R, R, Path.Direction.CW);
        } else if (lineCount == 0) {
            topicHeight = AndroidUtilities.dp(11) + (int) Theme.chat_topicTextPaint.getTextSize();
            textWidth = 0;
            if (topicClosed) {
                textWidth += AndroidUtilities.dp(4) + locksz;
            }
            topicNameLeft = 0;
            topicArrowDrawableVisible = true;
            topicArrowDrawable.setBounds(
                (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth - locksz),
                (int) (topicHeight / 2f - arrowsz / 2),
                (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth),
                (int) (topicHeight / 2f + arrowsz / 2)
            );
            topicClosedDrawable.setBounds(
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth - arrowsz - locksz),
                    (int) (topicHeight / 2f - locksz / 2),
                    (int) (padleft + padright + AndroidUtilities.dp(-4) + textWidth - arrowsz),
                    (int) (topicHeight / 2f + locksz / 2)
            );
            AndroidUtilities.rectTmp.set(0, 0, padleft + padright + textWidth, topicHeight);
            topicPath.addRoundRect(AndroidUtilities.rectTmp, R, R, Path.Direction.CW);
        }
        topicWidth = (int) (padleft + padright - AndroidUtilities.dp(1) + textWidth);
        int occupingHeight = 0;
        if (!messageObject.isAnyKindOfSticker() && messageObject.type != MessageObject.TYPE_ROUND_VIDEO || messageObject.type == MessageObject.TYPE_EMOJIS) {
            occupingHeight += AndroidUtilities.dp(6) + topicHeight;
            if (messageObject.type == MessageObject.TYPE_EMOJIS) {
                occupingHeight += AndroidUtilities.dp(16);
            } else if (messageObject.type != MessageObject.TYPE_TEXT) {
                occupingHeight += AndroidUtilities.dp(9);
            }
        }

        if (topicSelectorDrawable == null) {
            topicSelectorDrawable = Theme.createSelectorDrawable(topicBackgroundColor, Theme.RIPPLE_MASK_ALL);
            topicSelectorDrawable.setCallback(cell);
        } else {
            Theme.setSelectorDrawableColor(topicSelectorDrawable, topicBackgroundColor, true);
        }

        topicPaint.setColor(topicBackgroundColor);

        if (topicIconDrawable != null) {
            if (topicIconDrawableBounds == null) {
                topicIconDrawableBounds = new Rect();
            }
            topicIconDrawableBounds.set(
                    AndroidUtilities.dp(3 + 2f),
                    AndroidUtilities.dp(2 + (lineCount == 2 ? 3 : 0)),
                    AndroidUtilities.dp(3 + 2) + iconsz,
                    AndroidUtilities.dp(2 + (lineCount == 2 ? 3 : 0)) + iconsz
            );
            topicIconDrawable.setBounds(topicIconDrawableBounds);
        }

        return occupingHeight;
    }

    public void onAttached(ChatMessageCell cell) {
        if (topicIconDrawable instanceof AnimatedEmojiDrawable && cell != null) {
            ((AnimatedEmojiDrawable) topicIconDrawable).addView(cell::invalidateOutbounds);
        }
    }

    public void onDetached(ChatMessageCell cell) {
        if (topicIconDrawable instanceof AnimatedEmojiDrawable && cell != null) {
            ((AnimatedEmojiDrawable) topicIconDrawable).removeView(cell::invalidateOutbounds);
        }
    }

    private void setupColors(int iconColor) {
        if (lastMessageObject != null && lastMessageObject.shouldDrawWithoutBackground()) {
            topicNameColor = getThemedColor(Theme.key_chat_stickerReplyNameText);
        } else if (lastMessageObject != null && lastMessageObject.isOutOwner()) {
            topicNameColor = getThemedColor(Theme.key_chat_outReactionButtonText);
            topicBackgroundColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_outReactionButtonBackground), 38);
        } else {
            if (topicHSV == null) {
                topicHSV = new float[3];
            }
            Color.colorToHSV(iconColor, topicHSV);
            float hue = topicHSV[0];
            float sat = topicHSV[1];
            if (sat <= 0.02f) {
                topicNameColor = getThemedColor(Theme.key_chat_inReactionButtonText);
                topicBackgroundColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_inReactionButtonBackground), 38);
            } else {
                Color.colorToHSV(getThemedColor(Theme.key_chat_inReactionButtonText), topicHSV);
                topicHSV[0] = hue;
                float[] hueRanges = Theme.isCurrentThemeDark() ? darkHueRanges : lightHueRanges;
                float[] satValues = Theme.isCurrentThemeDark() ? darkSatValues : lightSatValues;
                float[] valValues = Theme.isCurrentThemeDark() ? darkValValues : lightValValues;
                for (int i = 1; i < hueRanges.length; ++i) {
                    if (hue <= hueRanges[i]) {
                        float t = (hue - hueRanges[i - 1]) / (hueRanges[i] - hueRanges[i - 1]);
                        topicHSV[1] = AndroidUtilities.lerp(satValues[i - 1], satValues[i], t);
                        topicHSV[2] = AndroidUtilities.lerp(valValues[i - 1], valValues[i], t);
                        break;
                    }
                }
                topicNameColor = Color.HSVToColor(Color.alpha(getThemedColor(Theme.key_chat_inReactionButtonText)), topicHSV);
                topicBackgroundColor = Color.HSVToColor(38, topicHSV);
            }
        }
    }

    public boolean checkTouchEvent(MotionEvent event) {
        if (topicHitRect == null) {
            topicPressed = false;
            return false;
        }
        boolean hit = topicHitRect.contains(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (hit) {
                if (topicSelectorDrawable != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        topicSelectorDrawable.setHotspot(event.getX() - topicHitRect.left, event.getY() - topicHitRect.top);
                    }
                    topicSelectorDrawable.setState(pressedState);
                }
                topicPressed = true;
            } else {
                topicPressed = false;
            }
            return topicPressed;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (topicPressed != hit) {
                if (topicPressed && topicSelectorDrawable != null) {
                    topicSelectorDrawable.setState(idleState);
                }
                topicPressed = hit;
            }
            return topicPressed;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (topicPressed) {
                topicPressed = false;
                if (topicSelectorDrawable != null) {
                    topicSelectorDrawable.setState(idleState);
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onClick();
                    return true;
                }
            }
        }
        return false;
    }

    public int width() {
        return topicWidth;
    }

    public int height() {
        return topicHeight;
    }

    public void draw(Canvas canvas, float x, float y, float alpha) {
        if (topicIconWaiting && topicIconDrawable instanceof AnimatedEmojiDrawable) {
            int iconColor = AnimatedEmojiDrawable.getDominantColor((AnimatedEmojiDrawable) topicIconDrawable);
            if (iconColor != 0) {
                topicIconWaiting = false;
                setupColors(iconColor);
            }
        }
        canvas.save();
        if (lastMessageObject != null && lastMessageObject.shouldDrawWithoutBackground()) {
            topicPath.offset(x, y);

            int oldAlpha1 = -1, oldAlpha2 = -1;
            if (alpha < 1) {
                oldAlpha1 = getThemedPaint(Theme.key_paint_chatActionBackground).getAlpha();
                getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha((int) (oldAlpha1 * alpha));
            }
            canvas.drawPath(topicPath, getThemedPaint(Theme.key_paint_chatActionBackground));
            if (hasGradientService()) {
                if (alpha < 1) {
                    oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
                    Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha2 * alpha));
                }
                canvas.drawPath(topicPath, Theme.chat_actionBackgroundGradientDarkenPaint);
            }
            if (oldAlpha1 >= 0) {
                getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha(oldAlpha1);
            }
            if (oldAlpha2 >= 0) {
                Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha2);
            }
            topicPath.offset(-x, -y);
            canvas.translate(x, y);
        } else {
            canvas.translate(x, y);
            if (topicPath != null && topicPaint != null) {
                if (topicBackgroundColorAnimated != null) {
                    topicPaint.setColor(topicBackgroundColorAnimated.set(topicBackgroundColor));
                } else {
                    topicPaint.setColor(topicBackgroundColor);
                }
                int wasAlpha = topicPaint.getAlpha();
                topicPaint.setAlpha((int) (wasAlpha * alpha));
                canvas.drawPath(topicPath, topicPaint);
                topicPaint.setAlpha(wasAlpha);
            }
        }
        if (topicHitRect == null) {
            topicHitRect = new RectF();
        }
        topicHitRect.set(x, y, x + topicWidth, y + topicHeight);
        if (topicSelectorDrawable != null) {
            canvas.save();
            canvas.clipPath(topicPath);
            AndroidUtilities.rectTmp2.set(0, 0, topicWidth, topicHeight);
            topicSelectorDrawable.setBounds(AndroidUtilities.rectTmp2);
            topicSelectorDrawable.draw(canvas);
            canvas.restore();
        }
        int nameColor = topicNameColor;
        if (topicNameLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(isGeneralTopic ? 13 : 17) + Theme.chat_topicTextPaint.getTextSize() - topicNameLeft, AndroidUtilities.dp(4.5f));
            if (topicNameColorAnimated != null) {
                Theme.chat_topicTextPaint.setColor(nameColor = topicNameColorAnimated.set(topicNameColor));
            } else {
                Theme.chat_topicTextPaint.setColor(nameColor = topicNameColor);
            }
            Theme.chat_topicTextPaint.setAlpha((int) (Theme.chat_topicTextPaint.getAlpha() * alpha * (topicClosed ? .7f : 1f)));
            topicNameLayout.draw(canvas);
            canvas.restore();
        }
        if (topicClosedDrawable != null && topicClosed) {
            int arrowColor = ColorUtils.setAlphaComponent(nameColor, 140);
            if (topicArrowColor != arrowColor) {
                topicClosedDrawable.setColorFilter(new PorterDuffColorFilter(topicArrowColor = arrowColor, PorterDuff.Mode.MULTIPLY));
            }
            topicClosedDrawable.draw(canvas);
        }
        if (topicArrowDrawable != null && topicArrowDrawableVisible) {
            int arrowColor = ColorUtils.setAlphaComponent(nameColor, 140);
            if (topicArrowColor != arrowColor) {
                topicArrowDrawable.setColorFilter(new PorterDuffColorFilter(topicArrowColor = arrowColor, PorterDuff.Mode.MULTIPLY));
            }
            topicArrowDrawable.draw(canvas);
        }
        canvas.restore();
    }

    public void drawOutbounds(Canvas canvas, float alpha) {
        if (topicHitRect != null) {
            canvas.save();
            canvas.translate(topicHitRect.left, topicHitRect.top);
            if (topicIconDrawable != null) {
                topicIconDrawable.setAlpha((int) (255 * alpha));
                topicIconDrawable.setBounds(topicIconDrawableBounds);
                topicIconDrawable.draw(canvas);
            } else if (imageReceiver != null) {
                imageReceiver.setImageCoords(0, 0, avatarSize, avatarSize);
                imageReceiver.draw(canvas);
            }
            canvas.restore();
        }
    }

    public void resetClick() {
        if (topicSelectorDrawable != null) {
            topicSelectorDrawable.setState(idleState);
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private Paint getThemedPaint(String key) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(key) : null;
        return paint != null ? paint : Theme.getThemePaint(key);
    }

    private boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

}
