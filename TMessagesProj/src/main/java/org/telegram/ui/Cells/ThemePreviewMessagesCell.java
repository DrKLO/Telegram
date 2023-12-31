package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stories.recorder.StoryEntry;

public class ThemePreviewMessagesCell extends LinearLayout {

    public final static int TYPE_REACTIONS_DOUBLE_TAP = 2;
    public final static int TYPE_PEER_COLOR = 3;

    private final Runnable invalidateRunnable = this::invalidate;

    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private BackgroundGradientDrawable.Disposable oldBackgroundGradientDisposable;

    private Drawable backgroundDrawable;
    private Drawable oldBackgroundDrawable;
    private ChatMessageCell[] cells = new ChatMessageCell[2];
    private Drawable shadowDrawable;
    private INavigationLayout parentLayout;
    private final int type;

    public BaseFragment fragment;

    private int progress = -1;
    private final Runnable cancelProgress = () -> {
        progress = -1;
        for (int i = 0; i < cells.length; ++i) {
            if (cells[i] != null) {
                cells[i].invalidate();
            }
        }
    };

    public ThemePreviewMessagesCell(Context context, INavigationLayout layout, int type) {
        this(context, layout, type, 0);
    }

    public ThemePreviewMessagesCell(Context context, INavigationLayout layout, int type, long dialogId) {
        this(context, layout, type, dialogId, null);
    }

    @SuppressLint("ClickableViewAccessibility")
    public ThemePreviewMessagesCell(Context context, INavigationLayout layout, int type, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.type = type;
        int currentAccount = UserConfig.selectedAccount;
        parentLayout = layout;

        setWillNotDraw(false);
        setOrientation(LinearLayout.VERTICAL);
        setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));

        shadowDrawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow, resourcesProvider);

        int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

        MessageObject message1 = null;
        MessageObject message2 = null;
        if (type == TYPE_PEER_COLOR) {
            final boolean isChannel = dialogId < 0;

            TLRPC.Message message = new TLRPC.TL_message();
            message.message = LocaleController.getString(isChannel ? R.string.ChannelColorPreview : R.string.UserColorPreview);
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.flags |= 1;
            if (dialogId == 0) {
                message.reply_to.reply_to_peer_id = new TLRPC.TL_peerUser();
                message.reply_to.reply_to_peer_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            } else {
                message.reply_to.reply_to_peer_id = new TLRPC.TL_peerChannel();
                message.reply_to.reply_to_peer_id.channel_id = -dialogId;
            }
            message.replyMessage = new TLRPC.Message();
            message.replyMessage.media = new TLRPC.TL_messageMediaEmpty();
            if (dialogId == 0) {
                message.replyMessage.from_id = new TLRPC.TL_peerUser();
                message.replyMessage.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                message.replyMessage.peer_id = new TLRPC.TL_peerUser();
                message.replyMessage.peer_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            } else {
                message.replyMessage.from_id = new TLRPC.TL_peerChannel();
                message.replyMessage.from_id.channel_id = -dialogId;
                message.replyMessage.peer_id = new TLRPC.TL_peerChannel();
                message.replyMessage.peer_id.channel_id = -dialogId;
            }
            message.replyMessage.message = LocaleController.getString(isChannel ? R.string.ChannelColorPreviewReply : R.string.UserColorPreviewReply);
            message.media = new TLRPC.TL_messageMediaWebPage();
            message.media.webpage = new TLRPC.TL_webPage();
            message.media.webpage.embed_url = "https://telegram.org/";
            message.media.webpage.flags |= 2;
            message.media.webpage.site_name = LocaleController.getString(R.string.AppName);
            message.media.webpage.flags |= 4;
            message.media.webpage.title = LocaleController.getString(isChannel ? R.string.ChannelColorPreviewLinkTitle : R.string.UserColorPreviewLinkTitle);
            message.media.webpage.flags |= 8;
            message.media.webpage.description = LocaleController.getString(isChannel ? R.string.ChannelColorPreviewLinkDescription : R.string.UserColorPreviewLinkDescription);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            if (dialogId == 0) {
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            } else {
                message.from_id = new TLRPC.TL_peerChannel();
                message.from_id.channel_id = -dialogId;
            }
            message.id = 1;
            message.out = false;
            if (dialogId == 0) {
                message.peer_id = new TLRPC.TL_peerUser();
                message.peer_id.user_id = 0;
            } else {
                message.peer_id = new TLRPC.TL_peerChannel();
                message.peer_id.channel_id = -dialogId;
            }

            message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
            message1.forceAvatar = true;
            message1.resetLayout();
            message1.eventId = 1;
        } else if (type == TYPE_REACTIONS_DOUBLE_TAP)  {
            TLRPC.Message message = new TLRPC.TL_message();
            message.message = LocaleController.getString("DoubleTapPreviewMessage", R.string.DoubleTapPreviewMessage);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;

            message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
            message1.resetLayout();
            message1.eventId = 1;
            message1.customName = LocaleController.getString("DoubleTapPreviewSenderName", R.string.DoubleTapPreviewSenderName);
            message1.customAvatarDrawable = ContextCompat.getDrawable(context, R.drawable.dino_pic);
            message1.overrideLinkColor = 5;
            message1.overrideLinkEmoji = 0;
        } else {
            TLRPC.Message message = new TLRPC.TL_message();
            if (type == 0) {
                message.message = LocaleController.getString("FontSizePreviewReply", R.string.FontSizePreviewReply);
            } else {
                message.message = LocaleController.getString("NewThemePreviewReply", R.string.NewThemePreviewReply);
            }
            String greeting = "\uD83D\uDC4B";
            int index = message.message.indexOf(greeting);
            if (index >= 0) {
                TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                entity.offset = index;
                entity.length = greeting.length();
                entity.document_id = 5386654653003864312L;
                message.entities.add(entity);
            }
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;
            MessageObject replyMessageObject = new MessageObject(UserConfig.selectedAccount, message, true, false);

            message = new TLRPC.TL_message();
            if (type == 0) {
                message.message = LocaleController.getString("FontSizePreviewLine2", R.string.FontSizePreviewLine2);
            } else {
                String text = LocaleController.getString("NewThemePreviewLine3", R.string.NewThemePreviewLine3);
                StringBuilder builder = new StringBuilder(text);
                int index1 = text.indexOf('*');
                int index2 = text.lastIndexOf('*');
                if (index1 != -1 && index2 != -1) {
                    builder.replace(index2, index2 + 1, "");
                    builder.replace(index1, index1 + 1, "");
                    TLRPC.TL_messageEntityTextUrl entityUrl = new TLRPC.TL_messageEntityTextUrl();
                    entityUrl.offset = index1;
                    entityUrl.length = index2 - index1 - 1;
                    entityUrl.url = "https://telegram.org";
                    message.entities.add(entityUrl);
                }
                message.message = builder.toString();
            }
            String cool = "\uD83D\uDE0E";
            int index1 = message.message.indexOf(cool);
            if (index1 >= 0) {
                TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                entity.offset = index1;
                entity.length = cool.length();
                entity.document_id = 5373141891321699086L;
                message.entities.add(entity);
            }
            message.date = date + 960;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;
            message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
            message1.resetLayout();
            message1.overrideLinkColor = 5;
            message1.overrideLinkEmoji = 0;
            message1.eventId = 1;

            message = new TLRPC.TL_message();
            if (type == 0) {
                message.message = LocaleController.getString("FontSizePreviewLine1", R.string.FontSizePreviewLine1);
            } else {
                message.message = LocaleController.getString("NewThemePreviewLine1", R.string.NewThemePreviewLine1);
            }
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 257 + 8;
            message.from_id = new TLRPC.TL_peerUser();
            message.id = 1;
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.flags |= 16;
            message.reply_to.reply_to_msg_id = 5;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            message2 = new MessageObject(UserConfig.selectedAccount, message, true, false);
            if (type == 0) {
//                message2.customReplyName = LocaleController.getString("FontSizePreviewName", R.string.FontSizePreviewName);
            } else {
                message2.customReplyName = LocaleController.getString("NewThemePreviewName", R.string.NewThemePreviewName);
            }
            message2.eventId = 1;
            message2.resetLayout();
            message2.replyMessageObject = replyMessageObject;
        }

        for (int a = 0; a < cells.length; a++) {
            cells[a] = new ChatMessageCell(context, false, null, resourcesProvider) {
                private GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (type != TYPE_REACTIONS_DOUBLE_TAP || MediaDataController.getInstance(currentAccount).getDoubleTapReaction() == null) {
                            return false;
                        }
                        boolean added = getMessageObject().selectReaction(ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(MediaDataController.getInstance(currentAccount).getDoubleTapReaction()), false, false);
                        setMessageObject(getMessageObject(), null, false, false);
                        requestLayout();
                        ReactionsEffectOverlay.removeCurrent(false);
                        if (added) {
                            ReactionsEffectOverlay.show(fragment, null, cells[1], null, e.getX(), e.getY(), ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(MediaDataController.getInstance(currentAccount).getDoubleTapReaction()), currentAccount, ReactionsEffectOverlay.LONG_ANIMATION);
                            ReactionsEffectOverlay.startAnimation();
                        }
                        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                getViewTreeObserver().removeOnPreDrawListener(this);
                                getTransitionParams().resetAnimation();
                                getTransitionParams().animateChange();
                                getTransitionParams().animateChange = true;
                                getTransitionParams().animateChangeProgress = 0f;
                                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                                valueAnimator.addUpdateListener(valueAnimator1 -> {
                                    getTransitionParams().animateChangeProgress = (float) valueAnimator1.getAnimatedValue();
                                    invalidate();
                                });
                                valueAnimator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        getTransitionParams().resetAnimation();
                                        getTransitionParams().animateChange = false;
                                        getTransitionParams().animateChangeProgress = 1f;
                                    }
                                });
                                valueAnimator.start();
                                return false;
                            }
                        });

                        return true;
                    }
                });

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (allowLoadingOnTouch()) {
                        return super.onTouchEvent(event);
                    }
                    gestureDetector.onTouchEvent(event);
                    return true;
                }

                private final AnimatedColor color1 = new AnimatedColor(this, 0, 180, CubicBezierInterpolator.EASE_OUT);
                private final AnimatedColor color2 = new AnimatedColor(this, 0, 180, CubicBezierInterpolator.EASE_OUT);

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (getMessageObject() != null && getMessageObject().overrideLinkColor >= 0) {
                        final int colorId = getMessageObject().overrideLinkColor;
                        final int color1, color2;
                        if (colorId >= 14) {
                            MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
                            MessagesController.PeerColors peerColors = messagesController != null ? messagesController.peerColors : null;
                            MessagesController.PeerColor peerColor = peerColors != null ? peerColors.getColor(colorId) : null;
                            if (peerColor != null) {
                                final int peerColorValue = peerColor.getColor1();
                                color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getPeerColorIndex(peerColorValue)]);
                                color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getPeerColorIndex(peerColorValue)]);
                            } else {
                                color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(colorId)]);
                                color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(colorId)]);
                            }
                        } else {
                            color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(colorId)]);
                            color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(colorId)]);
                        }
                        avatarDrawable.setColor(this.color1.set(color1), this.color2.set(color2));
                    } else {
                        color1.set(avatarDrawable.getColor());
                        color2.set(avatarDrawable.getColor2());
                    }
                    if (getAvatarImage() != null && getAvatarImage().getImageHeight() != 0) {
                        getAvatarImage().setImageCoords(getAvatarImage().getImageX(), getMeasuredHeight() - getAvatarImage().getImageHeight() - AndroidUtilities.dp(4), getAvatarImage().getImageWidth(), getAvatarImage().getImageHeight());
                        getAvatarImage().setRoundRadius((int) (getAvatarImage().getImageHeight() / 2f));
                        getAvatarImage().draw(canvas);
                    } else if (type == TYPE_REACTIONS_DOUBLE_TAP) {
                        invalidate();
                    }
                    super.dispatchDraw(canvas);
                }
            };
            cells[a].setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                @Override
                public boolean canPerformActions() {
                    return allowLoadingOnTouch();
                }

                @Override
                public void didPressReplyMessage(ChatMessageCell cell, int id) {
                    if (allowLoadingOnTouch()) {
                        progress = ChatActivity.PROGRESS_REPLY;
                        cell.invalidate();

                        AndroidUtilities.cancelRunOnUIThread(cancelProgress);
                        AndroidUtilities.runOnUIThread(cancelProgress, 5000);
                    }
                }

                @Override
                public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                    if (allowLoadingOnTouch()) {
                        progress = ChatActivity.PROGRESS_INSTANT;
                        AndroidUtilities.cancelRunOnUIThread(cancelProgress);
                        AndroidUtilities.runOnUIThread(cancelProgress, 5000);
                    }
                }

                @Override
                public void didPressInstantButton(ChatMessageCell cell, int type) {
                    if (allowLoadingOnTouch()) {
                        progress = ChatActivity.PROGRESS_INSTANT;
                        cell.invalidate();

                        AndroidUtilities.cancelRunOnUIThread(cancelProgress);
                        AndroidUtilities.runOnUIThread(cancelProgress, 5000);
                    }
                }

                @Override
                public boolean isProgressLoading(ChatMessageCell cell, int type) {
                    return type == progress;
                }
            });
            cells[a].isChat = type == TYPE_REACTIONS_DOUBLE_TAP;
            cells[a].setFullyDraw(true);
            MessageObject messageObject = a == 0 ? message2 : message1;
            if (messageObject == null) {
                continue;
            }
            cells[a].setMessageObject(messageObject, null, false, false);
            addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    public ChatMessageCell[] getCells() {
        return cells;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < cells.length; a++) {
            cells[a].invalidate();
        }
    }

    private Drawable overrideDrawable;
    public void setOverrideBackground(Drawable drawable) {
        overrideDrawable = drawable;
        if (overrideDrawable != null) {
            overrideDrawable.setCallback(this);
        }
        if (overrideDrawable instanceof ChatBackgroundDrawable) {
            if (isAttachedToWindow()) {
                ((ChatBackgroundDrawable) overrideDrawable).onAttachedToWindow(this);
            }
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (overrideDrawable instanceof ChatBackgroundDrawable) {
            ((ChatBackgroundDrawable) overrideDrawable).onAttachedToWindow(this);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == overrideDrawable || who == oldBackgroundDrawable || super.verifyDrawable(who);
    }

    public boolean customAnimation;
    private final AnimatedFloat overrideDrawableUpdate = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable newDrawable = overrideDrawable != null ? overrideDrawable : Theme.getCachedWallpaperNonBlocking();
        if (Theme.wallpaperLoadTask != null) {
            invalidate();
        }
        if (newDrawable != backgroundDrawable && newDrawable != null) {
            if (Theme.isAnimatingColor() || customAnimation) {
                oldBackgroundDrawable = backgroundDrawable;
                oldBackgroundGradientDisposable = backgroundGradientDisposable;
            } else if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            backgroundDrawable = newDrawable;
            overrideDrawableUpdate.set(0, true);
        }
        float themeAnimationValue = customAnimation ? overrideDrawableUpdate.set(1) : parentLayout.getThemeAnimationValue();
        for (int a = 0; a < 2; a++) {
            Drawable drawable = a == 0 ? oldBackgroundDrawable : backgroundDrawable;
            if (drawable == null) {
                continue;
            }
            int alpha;
            if (a == 1 && oldBackgroundDrawable != null && (parentLayout != null || customAnimation)) {
                alpha = (int) (255 * themeAnimationValue);
            } else {
                alpha = 255;
            }
            if (alpha <= 0) {
                continue;
            }
            drawable.setAlpha(alpha);
            if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (drawable instanceof BackgroundGradientDrawable) {
                    final BackgroundGradientDrawable backgroundGradientDrawable = (BackgroundGradientDrawable) drawable;
                    backgroundGradientDisposable = backgroundGradientDrawable.drawExactBoundsSize(canvas, this);
                } else {
                    drawable.draw(canvas);
                }
            } else if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                bitmapDrawable.setFilterBitmap(true);
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                } else {
                    int viewHeight = getMeasuredHeight();
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) drawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (viewHeight - height) / 2;
                    canvas.save();
                    canvas.clipRect(0, 0, width, getMeasuredHeight());
                    drawable.setBounds(x, y, x + width, y + height);
                }
                drawable.draw(canvas);
                canvas.restore();
            } else {
                StoryEntry.drawBackgroundDrawable(canvas, drawable, getWidth(), getHeight());
            }
            if (a == 0 && oldBackgroundDrawable != null && themeAnimationValue >= 1.0f) {
                if (oldBackgroundGradientDisposable != null) {
                    oldBackgroundGradientDisposable.dispose();
                    oldBackgroundGradientDisposable = null;
                }
                oldBackgroundDrawable = null;
                invalidate();
            }
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    private boolean allowLoadingOnTouch() {
        return type == TYPE_PEER_COLOR || type == 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
        if (oldBackgroundGradientDisposable != null) {
            oldBackgroundGradientDisposable.dispose();
            oldBackgroundGradientDisposable = null;
        }
        if (overrideDrawable instanceof ChatBackgroundDrawable) {
            ((ChatBackgroundDrawable) overrideDrawable).onDetachedFromWindow(this);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (type == TYPE_REACTIONS_DOUBLE_TAP || allowLoadingOnTouch()) {
            return super.onInterceptTouchEvent(ev);
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (type == TYPE_REACTIONS_DOUBLE_TAP || allowLoadingOnTouch()) {
            return super.dispatchTouchEvent(ev);
        }
        return false;
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (type == TYPE_REACTIONS_DOUBLE_TAP || allowLoadingOnTouch()) {
            return super.onTouchEvent(event);
        }
        return false;
    }
}
