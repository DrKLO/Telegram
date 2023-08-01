package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageSeenCheckDrawable;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Stories.StoriesUtilities;

public class ReactedUserHolderView extends FrameLayout {
    int currentAccount;

    public static int STYLE_DEFAULT = 0;
    public static int STYLE_STORY = 1;

    public BackupImageView avatarView;
    SimpleTextView titleView;
    SimpleTextView subtitleView;
    BackupImageView reactView;
    AvatarDrawable avatarDrawable = new AvatarDrawable();
    View overlaySelectorView;
    AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable rightDrawable;
    public final static int ITEM_HEIGHT_DP = 50;
    public final static int STORY_ITEM_HEIGHT_DP = 58;
    Theme.ResourcesProvider resourcesProvider;
    int style;
    public long dialogId;
    public StoriesUtilities.AvatarStoryParams params = new StoriesUtilities.AvatarStoryParams(false) {
        @Override
        public void openStory(long dialogId, Runnable onDone) {
            ReactedUserHolderView.this.openStory(dialogId, onDone);
        }
    };

    public void openStory(long dialogId, Runnable onDone) {

    }

    public static final MessageSeenCheckDrawable seenDrawable = new MessageSeenCheckDrawable(R.drawable.msg_mini_checks, Theme.key_windowBackgroundWhiteGrayText);
    public static final MessageSeenCheckDrawable reactDrawable = new MessageSeenCheckDrawable(R.drawable.msg_reactions, Theme.key_windowBackgroundWhiteGrayText, 16, 16, 5.66f);

    public ReactedUserHolderView(int style, int currentAccount, @NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.style = style;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(ITEM_HEIGHT_DP)));

        int avatarSize = style == STYLE_STORY ? 48 : 34;
        avatarView = new BackupImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (style == STYLE_STORY) {
                    params.originalAvatarRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    StoriesUtilities.drawAvatarWithStory(dialogId, canvas, getImageReceiver(), params);
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return params.checkOnTouchEvent(event, this);
            }
        };
        avatarView.setRoundRadius(AndroidUtilities.dp(avatarSize));
        addView(avatarView, LayoutHelper.createFrameRelatively(avatarSize, avatarSize, Gravity.START | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        if (style == STYLE_STORY) {
            setClipChildren(false);
        }
        titleView = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value) {
                value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false);
                return super.setText(value);
            }
        };
        NotificationCenter.listenEmojiLoading(titleView);
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        titleView.setEllipsizeByGradient(true);
        titleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleView.setRightPadding(AndroidUtilities.dp(30));
        titleView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        titleView.setRightDrawableOutside(true);
        float topMargin = style == STYLE_STORY ? 7.66f : 5.33f;
        float leftMargin = style == STYLE_STORY ? 73 : 55;
        addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, leftMargin, topMargin, 12, 0));

        rightDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(18));
        titleView.setDrawablePadding(AndroidUtilities.dp(3));
        titleView.setRightDrawable(rightDrawable);

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        subtitleView.setEllipsizeByGradient(true);
        subtitleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitleView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        topMargin = style == STYLE_STORY ? 24f : 19f;
        addView(subtitleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, leftMargin, topMargin , 20, 0));

        reactView = new BackupImageView(context);
        addView(reactView, LayoutHelper.createFrameRelatively(24, 24, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        overlaySelectorView = new View(context);
        overlaySelectorView.setBackground(Theme.getSelectorDrawable(false));
        addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setUserReaction(TLRPC.User user, TLRPC.Chat chat, TLRPC.Reaction reaction, long date, boolean dateIsSeen, boolean animated) {
        TLObject u = user;
        if (u == null) {
            u = chat;
        }
        if (u == null) {
            return;
        }

        Long documentId = u instanceof TLRPC.User ? UserObject.getEmojiStatusDocumentId((TLRPC.User) u) : null;
        if (documentId == null) {
            if (user != null && user.premium) {
                rightDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, false);
            } else {
                rightDrawable.set((Drawable) null, false);
            }
        } else {
            rightDrawable.set(documentId, false);
        }
        rightDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));

        avatarDrawable.setInfo(u);
        if (user != null) {
            dialogId = user.id;
            titleView.setText(UserObject.getUserName(user));
        } else {
            dialogId = chat.id;
            titleView.setText(chat.title);
        }

        Drawable thumb = avatarDrawable;
        if (user != null) {
            if (user.photo != null && user.photo.strippedBitmap != null) {
                thumb = user.photo.strippedBitmap;
            }
        } else {
            if (chat.photo != null && chat.photo.strippedBitmap != null) {
                thumb = chat.photo.strippedBitmap;
            }
        }
        avatarView.setImage(ImageLocation.getForUserOrChat(u, ImageLocation.TYPE_SMALL), "50_50", thumb, u);

        String contentDescription;
        boolean hasReactImage = false;
        if (reaction != null) {
            ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(reaction);
            if (visibleReaction.emojicon != null) {
                TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(visibleReaction.emojicon);
                if (r != null) {
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon.thumbs, Theme.key_windowBackgroundGray, 1.0f);
                    reactView.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", "webp", svgThumb, r);
                    hasReactImage = true;
                } else {
                    reactView.setImageDrawable(null);
                }
            } else {
                AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, currentAccount, visibleReaction.documentId);
                drawable.setColorFilter(Theme.getAnimatedEmojiColorFilter(resourcesProvider));
                reactView.setAnimatedEmojiDrawable(drawable);
                hasReactImage = true;
            }
            contentDescription = LocaleController.formatString("AccDescrReactedWith", R.string.AccDescrReactedWith, titleView.getText(), visibleReaction.emojicon != null ? visibleReaction.emojicon : reaction);
        } else {
            reactView.setImageDrawable(null);
            contentDescription = LocaleController.formatString("AccDescrPersonHasSeen", R.string.AccDescrPersonHasSeen, titleView.getText());
        }

        if (date != 0) {
            contentDescription += " " + LocaleController.formatSeenDate(date);
        }
        setContentDescription(contentDescription);

        if (date != 0) {
            subtitleView.setVisibility(View.VISIBLE);
            CharSequence icon = dateIsSeen ? seenDrawable.getSpanned(getContext(), resourcesProvider) : reactDrawable.getSpanned(getContext(), resourcesProvider);
            subtitleView.setText(TextUtils.concat(icon, LocaleController.formatSeenDate(date)));
            subtitleView.setTranslationY(!dateIsSeen ? AndroidUtilities.dp(-1) : 0);
            titleView.setTranslationY(0);
            if (animated) {
                titleView.setTranslationY(AndroidUtilities.dp(9));
                titleView.animate().translationY(0);
                subtitleView.setAlpha(0);
                subtitleView.animate().alpha(1f);
            }
        } else {
            subtitleView.setVisibility(View.GONE);
            titleView.setTranslationY(AndroidUtilities.dp(9));
        }

        titleView.setRightPadding(AndroidUtilities.dp(hasReactImage ? 30 : 0));
        titleView.setTranslationX(hasReactImage && LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        ((MarginLayoutParams) subtitleView.getLayoutParams()).rightMargin = AndroidUtilities.dp(hasReactImage && !LocaleController.isRTL ? 12 + 24 : 12);
        subtitleView.setTranslationX(hasReactImage && LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
    }

    public void setUserReaction(TLRPC.MessagePeerReaction reaction) {
        if (reaction == null) {
            return;
        }
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        long dialogId = MessageObject.getPeerId(reaction.peer_id);
        if (dialogId > 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        }
        setUserReaction(user, chat, reaction.reaction, reaction.date, reaction.dateIsSeen, false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = style == STYLE_DEFAULT ? ITEM_HEIGHT_DP : STORY_ITEM_HEIGHT_DP;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(h), MeasureSpec.EXACTLY));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rightDrawable != null) {
            rightDrawable.attach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rightDrawable != null) {
            rightDrawable.detach();
        }
        params.onDetachFromWindow();
    }

    public void setObject(TLRPC.User user, long date, boolean b) {

    }
}