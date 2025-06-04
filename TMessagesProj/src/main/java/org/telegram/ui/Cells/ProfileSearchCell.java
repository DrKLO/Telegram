/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CanvasButton;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.PhotoBubbleClip;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.FilterCreateActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.Stories.StoriesUtilities;

import java.util.Locale;

public class ProfileSearchCell extends BaseCell implements NotificationCenter.NotificationCenterDelegate, Theme.Colorable {

    public boolean dontDrawAvatar;
    private PhotoBubbleClip bubbleClip;
    private CharSequence currentName;
    public ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private CharSequence subLabel;
    private Theme.ResourcesProvider resourcesProvider;
    private TLRPC.TL_sponsoredPeer ad;

    private TLRPC.User user;
    private TLRPC.Chat chat;
    private TLRPC.EncryptedChat encryptedChat;
    private ContactsController.Contact contact;
    private long dialog_id;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;

    private boolean savedMessages;

    public boolean useSeparator;

    private int currentAccount = UserConfig.selectedAccount;

    private int nameLeft;
    private int nameTop;
    private StaticLayout nameLayout;
    private boolean drawNameLock;
    private int nameLockLeft;
    private int nameLockTop;
    private int nameWidth;
    CanvasButton actionButton;
    private StaticLayout actionLayout;
    private int actionLeft;

    private int sublabelOffsetX;
    private int sublabelOffsetY;

    private boolean drawCount;
    private int lastUnreadCount;
    private int countTop = dp(19);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean[] isOnline;

    private boolean drawCheck;
    private boolean drawPremium;

    private boolean showPremiumBlocked;
    private final AnimatedFloat premiumBlockedT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean premiumBlocked;
    private final AnimatedFloat starsBlockedT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private long starsPriceBlocked;
    private boolean openBot;

    private int statusLeft;
    private StaticLayout statusLayout;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botVerificationDrawable;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    public StoriesUtilities.AvatarStoryParams avatarStoryParams = new StoriesUtilities.AvatarStoryParams(false);

    private final RectF adBounds = new RectF();
    private Text adText;
    private Paint adBackgroundPaint;
    private final ButtonBounce adBounce = new ButtonBounce(this);

    private RectF rect = new RectF();

    CheckBox2 checkBox;

    public ProfileSearchCell(Context context) {
        this(context, null);
    }

    public ProfileSearchCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(dp(23));
        avatarDrawable = new AvatarDrawable();

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox);

        botVerificationDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(20));
        botVerificationDrawable.setCallback(this);

        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(20));
        statusDrawable.setCallback(this);
    }

    private boolean allowBotOpenButton;
    private Utilities.Callback<TLRPC.User> onOpenButtonClick;
    public ProfileSearchCell allowBotOpenButton(boolean allow, Utilities.Callback<TLRPC.User> onOpenClick) {
        allowBotOpenButton = allow;
        onOpenButtonClick = onOpenClick;
        return this;
    }

    private Utilities.Callback2<ProfileSearchCell, TLRPC.TL_sponsoredPeer> onSponsoredOptionsClick;
    public void setOnSponsoredOptionsClick(Utilities.Callback2<ProfileSearchCell, TLRPC.TL_sponsoredPeer> onOptionsClick) {
        this.onSponsoredOptionsClick = onOptionsClick;
    }

    public ProfileSearchCell showPremiumBlock(boolean show) {
        showPremiumBlocked = show;
        return this;
    }

    private boolean customPaints;
    private TextPaint namePaint, statusPaint;
    public ProfileSearchCell useCustomPaints() {
        customPaints = true;
        return this;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return statusDrawable == who || botVerificationDrawable == who || super.verifyDrawable(who);
    }

    public void setAd(TLRPC.TL_sponsoredPeer sponsoredPeer) {
        ad = sponsoredPeer;
    }

    private boolean allowEmojiStatus = true;
    public void setAllowEmojiStatus(boolean allowEmojiStatus) {
        this.allowEmojiStatus = allowEmojiStatus;
    }
    public void setData(Object object, TLRPC.EncryptedChat ec, CharSequence n, CharSequence s, boolean needCount, boolean saved) {
        currentName = n;
        if (object instanceof TLRPC.User) {
            user = (TLRPC.User) object;
            chat = null;
            contact = null;
            final TL_account.RequirementToContact r = showPremiumBlocked && user != null ? MessagesController.getInstance(currentAccount).isUserContactBlocked(user.id) : null;
            premiumBlocked = DialogObject.isPremiumBlocked(r);
            starsPriceBlocked = DialogObject.getMessagesStarsPrice(r);
            setOpenBotButton(allowBotOpenButton && user.bot_has_main_app);
        } else if (object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) object;
            user = null;
            contact = null;
            final TL_account.RequirementToContact r = ChatObject.getRequirementToContact(chat);
            premiumBlocked = DialogObject.isPremiumBlocked(r);
            starsPriceBlocked = DialogObject.getMessagesStarsPrice(r);
            setOpenBotButton(false);
        } else if (object instanceof ContactsController.Contact) {
            contact = (ContactsController.Contact) object;
            chat = null;
            user = null;
            final TL_account.RequirementToContact r = showPremiumBlocked && contact != null && contact.user != null ? MessagesController.getInstance(currentAccount).isUserContactBlocked(contact.user.id) : null;
            premiumBlocked = DialogObject.isPremiumBlocked(r);
            starsPriceBlocked = DialogObject.getMessagesStarsPrice(r);
            setOpenBotButton(false);
        } else {
            setOpenBotButton(false);
        }
        encryptedChat = ec;
        subLabel = s;
        drawCount = needCount;
        savedMessages = saved;
        update(0);
    }

    private final ButtonBounce openButtonBounce = new ButtonBounce(this);
    private final Paint openButtonBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF openButtonRect = new RectF();
    private Text openButtonText;
    public void setOpenBotButton(boolean show) {
        if (openBot == show) return;
        if (openButtonText == null) {
            openButtonText = new Text(getString(R.string.BotOpen), 14, AndroidUtilities.bold());
        }
        final int buttonWidth = show ? (int) openButtonText.getCurrentWidth() + dp(15 + 15) : 0;
        setPadding(LocaleController.isRTL ? buttonWidth : 0, 0, LocaleController.isRTL ? 0 : buttonWidth, 0);
        openBot = show;
        openButtonBounce.setPressed(false);
    }

    public void setException(NotificationsSettingsActivity.NotificationException exception, CharSequence name) {
        String text;
        boolean enabled;
        boolean custom = exception.hasCustom;
        int value = exception.notify;
        int delta = exception.muteUntil;
        if (value == 3 && delta != Integer.MAX_VALUE) {
            delta -= ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (delta <= 0) {
                if (custom) {
                    text = getString(R.string.NotificationsCustom);
                } else {
                    text = getString(R.string.NotificationsUnmuted);
                }
            } else if (delta < 60 * 60) {
                text = LocaleController.formatString(R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60));
            } else if (delta < 60 * 60 * 24) {
                text = LocaleController.formatString(R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", (int) Math.ceil(delta / 60.0f / 60)));
            } else if (delta < 60 * 60 * 24 * 365) {
                text = LocaleController.formatString(R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", (int) Math.ceil(delta / 60.0f / 60 / 24)));
            } else {
                text = null;
            }
        } else {
            if (value == 0) {
                enabled = true;
            } else if (value == 1) {
                enabled = true;
            } else if (value == 2) {
                enabled = false;
            } else {
                enabled = false;
            }
            if (enabled && custom) {
                text = getString(R.string.NotificationsCustom);
            } else {
                text = enabled ? getString(R.string.NotificationsUnmuted) : getString(R.string.NotificationsMuted);
            }
        }
        if (text == null) {
            text = getString(R.string.NotificationsOff);
        }

        if (DialogObject.isEncryptedDialog(exception.did)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(exception.did));
            if (encryptedChat != null) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                if (user != null) {
                    setData(user, encryptedChat, name, text, false, false);
                }
            }
        } else if (DialogObject.isUserDialog(exception.did)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(exception.did);
            if (user != null) {
                setData(user, null, name, text, false, false);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-exception.did);
            if (chat != null) {
                setData(chat, null, name, text, false, false);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (showPremiumBlocked) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.userIsPremiumBlockedUpadted);
        }
        statusDrawable.detach();
        botVerificationDrawable.detach();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        if (showPremiumBlocked) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.userIsPremiumBlockedUpadted);
        }
        statusDrawable.attach();
        botVerificationDrawable.attach();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        } else if (id == NotificationCenter.userIsPremiumBlockedUpadted) {
            final TL_account.RequirementToContact r;
            if (user != null) {
                r = showPremiumBlocked ? MessagesController.getInstance(currentAccount).isUserContactBlocked(user.id) : null;
            } else if (chat != null) {
                r = ChatObject.getRequirementToContact(chat);
            } else if (contact != null) {
                r = showPremiumBlocked && contact.user != null ? MessagesController.getInstance(currentAccount).isUserContactBlocked(contact.user.id) : null;
            } else return;
            if (premiumBlocked != DialogObject.isPremiumBlocked(r) || starsPriceBlocked != DialogObject.getMessagesStarsPrice(r)) {
                premiumBlocked = DialogObject.isPremiumBlocked(r);
                starsPriceBlocked = DialogObject.getMessagesStarsPrice(r);
                invalidate();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(60) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (user == null && chat == null && encryptedChat == null && contact == null) {
            return;
        }
        if (checkBox != null) {
            int x = LocaleController.isRTL ? (right - left) - dp(42) : dp(42);
            int y = dp(36);
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        if (changed) {
            buildLayout();
        }
    }

    public TLRPC.User getUser() {
        return user;
    }

    public TLRPC.Chat getChat() {
        return chat;
    }

    public void setSublabelOffset(int x, int y) {
        sublabelOffsetX = x;
        sublabelOffsetY = y;
    }

    public void buildLayout() {
        CharSequence nameString;
        TextPaint currentNamePaint;

        drawNameLock = false;
        drawCheck = false;
        drawPremium = false;

        if (encryptedChat != null) {
            drawNameLock = true;
            dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
            if (!LocaleController.isRTL) {
                nameLockLeft = dp(AndroidUtilities.leftBaseline);
                nameLeft = dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
            } else {
                nameLockLeft = getMeasuredWidth() - dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                nameLeft = dp(11);
            }
            nameLockTop = dp(22.0f);
            updateStatus(false, null, null, false);
        } else if (chat != null) {
            dialog_id = -chat.id;
            drawCheck = chat.verified;
            if (!LocaleController.isRTL) {
                nameLeft = dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = dp(11);
            }
            updateStatus(drawCheck, null, chat, false);
        } else if (user != null) {
            dialog_id = user.id;
            if (!LocaleController.isRTL) {
                nameLeft = dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = dp(11);
            }
            nameLockTop = dp(21);
            drawCheck = user.verified;
            drawPremium = !savedMessages && MessagesController.getInstance(currentAccount).isPremiumUser(user);
            updateStatus(drawCheck, user, null, false);
        } else if (contact != null) {
            dialog_id = 0;
            if (!LocaleController.isRTL) {
                nameLeft = dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = dp(11);
            }
            if (actionButton == null) {
                actionButton = new CanvasButton(this);
                actionButton.setDelegate(() -> {
                    if (getParent() instanceof RecyclerListView) {
                        RecyclerListView parent = (RecyclerListView) getParent();
                        parent.getOnItemClickListener().onItemClick(this, parent.getChildAdapterPosition(this));
                    } else {
                        callOnClick();
                    }
                });
            }
        }
        if (!LocaleController.isRTL) {
            statusLeft = dp(AndroidUtilities.leftBaseline);
        } else {
            statusLeft = dp(11);
        }

        if (ad != null) {
            if (adText == null) {
                final SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.SearchAd)).append(" i");
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.ic_ab_other);
                span.setScale(.55f, .55f);
                span.spaceScaleX = .7f;
                span.translate(-dp(2), 0);
                sb.setSpan(span, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                adText = new Text(sb, 12);
            }
            if (adBackgroundPaint == null) {
                adBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
        }

        if (currentName != null) {
            nameString = currentName;
        } else {
            CharSequence nameString2 = "";
            if (chat != null) {
                if (chat.monoforum) {
                    TLRPC.Chat mfChat = MessagesController.getInstance(currentAccount).getChat(chat.linked_monoforum_id);
                    if (mfChat != null) {
                        final SpannableStringBuilder sb = new SpannableStringBuilder(AndroidUtilities.escape(mfChat.title));
                        sb.append(" ");
                        final int index = sb.length();
                        sb.append(getString(R.string.MonoforumSpan));
                        sb.setSpan(new FilterCreateActivity.TextSpan(getString(R.string.MonoforumSpan), 9.33f, Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), index, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        nameString2 = sb;
                    } else {
                        nameString2 = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(chat.title));
                    }
                } else {
                    nameString2 = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(chat.title));
                }
            } else if (user != null) {
                nameString2 = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(UserObject.getUserName(user)));
            }
            nameString = AndroidUtilities.replaceNewLines(nameString2);
        }
        if (nameString.length() == 0) {
            if (user != null && user.phone != null && user.phone.length() != 0) {
                nameString = PhoneFormat.getInstance().format("+" + user.phone);
            } else {
                nameString = getString(R.string.HiddenName);
            }
        }
        if (customPaints) {
            if (namePaint == null) {
                namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                namePaint.setTypeface(AndroidUtilities.bold());
            }
            namePaint.setTextSize(dp(16));
            if (encryptedChat != null) {
                namePaint.setColor(Theme.getColor(Theme.key_chats_secretName, resourcesProvider));
            } else {
                namePaint.setColor(Theme.getColor(Theme.key_chats_name, resourcesProvider));
            }
            currentNamePaint = namePaint;
        } else if (encryptedChat != null) {
            currentNamePaint = Theme.dialogs_searchNameEncryptedPaint;
        } else {
            currentNamePaint = Theme.dialogs_searchNamePaint;
        }

        int statusWidth;
        if (!LocaleController.isRTL) {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - dp(14);
        } else {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - dp(AndroidUtilities.leftBaseline);
        }
        if (drawNameLock) {
            nameWidth -= dp(6) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        }
        if (ad != null) {
            final int adWidth = (int) adText.getCurrentWidth() + dp(12.66f + 8);
            nameWidth -= adWidth;
            if (LocaleController.isRTL) {
                nameLeft += adWidth;
            }
        }
        if (contact != null) {
            int w = (int) (Theme.dialogs_countTextPaint.measureText(getString(R.string.Invite)) + 1);

            actionLayout = new StaticLayout(getString(R.string.Invite), Theme.dialogs_countTextPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!LocaleController.isRTL) {
                actionLeft = getMeasuredWidth() - w - dp(19) - dp(16);
            } else {
                actionLeft = dp(19) + dp(16);
                nameLeft += w;
                statusLeft += w;
            }
            nameWidth -= dp(32) + w;
        }

        nameWidth -= getPaddingLeft() + getPaddingRight();
        statusWidth -= getPaddingLeft() + getPaddingRight();

        if (drawCount) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
            int unreadCount = MessagesController.getInstance(currentAccount).getDialogUnreadCount(dialog);
            if (unreadCount != 0) {
                lastUnreadCount = unreadCount;
                String countString = String.format(Locale.US, "%d", unreadCount);
                countWidth = Math.max(dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + dp(18);
                nameWidth -= w;
                statusWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - dp(19);
                } else {
                    countLeft = dp(19);
                    nameLeft += w;
                    statusLeft += w;
                }
            } else {
                lastUnreadCount = 0;
                countLayout = null;
            }
        } else {
            lastUnreadCount = 0;
            countLayout = null;
        }

        if (!botVerificationDrawable.isEmpty()) {
            if (LocaleController.isRTL) {
                nameWidth -= botVerificationDrawable.getIntrinsicWidth();
            } else {
                nameLeft += botVerificationDrawable.getIntrinsicWidth();
            }
        }
        if (!statusDrawable.isEmpty()) {
            if (LocaleController.isRTL) {
                nameLeft += statusDrawable.getIntrinsicWidth();
            } else {
                nameWidth -= statusDrawable.getIntrinsicWidth();
            }
        }

        if (nameWidth < 0) {
            nameWidth = 0;
        }
        CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - dp(12), TextUtils.TruncateAt.END);
        if (nameStringFinal != null) {
            nameStringFinal = Emoji.replaceEmoji(nameStringFinal, currentNamePaint.getFontMetricsInt(), false);
        }
        nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        CharSequence statusString = null;
        TextPaint currentStatusPaint = Theme.dialogs_offlinePaint;
        if (chat == null || subLabel != null) {
            if (subLabel != null) {
                statusString = subLabel;
            } else if (user != null) {
                if (MessagesController.isSupportUser(user)) {
                    statusString = getString(R.string.SupportStatus);
                } else if (user.bot && user.bot_active_users != 0) {
                    statusString = LocaleController.formatPluralStringSpaced("BotUsersShort", user.bot_active_users);
                } else if (user.bot) {
                    statusString = getString(R.string.Bot);
                } else if (user.id == UserObject.VERIFY) {
                    statusString = getString(R.string.VerifyCodesNotifications);
                } else if (UserObject.isService(user.id)) {
                    statusString = getString(R.string.ServiceNotifications);
                } else {
                    if (isOnline == null) {
                        isOnline = new boolean[1];
                    }
                    isOnline[0] = false;
                    statusString = LocaleController.formatUserStatus(currentAccount, user, isOnline);
                    if (isOnline[0]) {
                        currentStatusPaint = Theme.dialogs_onlinePaint;
                    }
                    if (user != null && (user.id == UserConfig.getInstance(currentAccount).getClientUserId() || user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime())) {
                        currentStatusPaint = Theme.dialogs_onlinePaint;
                        statusString = getString(R.string.Online);
                    }
                }
            }
            if (savedMessages || UserObject.isReplyUser(user)) {
                statusString = null;
                nameTop = dp(20);
            }
        } else {
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (chat.participants_count != 0) {
                    statusString = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                } else {
                    if (!ChatObject.isPublic(chat)) {
                        statusString = getString(R.string.ChannelPrivate).toLowerCase();
                    } else {
                        statusString = getString(R.string.ChannelPublic).toLowerCase();
                    }
                }
            } else {
                if (chat.participants_count != 0) {
                    statusString = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                } else {
                    if (chat.has_geo) {
                        statusString = getString(R.string.MegaLocation);
                    } else if (ChatObject.isMonoForum(chat)) {
                        statusString = getString(R.string.MonoforumMessages);
                    } else if (!ChatObject.isPublic(chat)) {
                        statusString = getString(R.string.MegaPrivate).toLowerCase();
                    } else {
                        statusString = getString(R.string.MegaPublic).toLowerCase();
                    }
                }
            }
            nameTop = dp(19);
        }
        if (customPaints) {
            if (statusPaint == null) {
                statusPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            }
            statusPaint.setTextSize(dp(15));
            if (currentStatusPaint == Theme.dialogs_offlinePaint) {
                statusPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
            } else if (currentStatusPaint == Theme.dialogs_onlinePaint) {
                statusPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText3, resourcesProvider));
            }
            currentStatusPaint = statusPaint;
        }

        if (!TextUtils.isEmpty(statusString)) {
            CharSequence statusStringFinal = TextUtils.ellipsize(statusString, currentStatusPaint, statusWidth - dp(12), TextUtils.TruncateAt.END);
            statusLayout = new StaticLayout(statusStringFinal, currentStatusPaint, statusWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            nameTop = dp(9);
            nameLockTop -= dp(10);
        } else {
            nameTop = dp(20);
            statusLayout = null;
        }

        int avatarLeft;
        if (LocaleController.isRTL) {
            avatarLeft = getMeasuredWidth() - dp(57) - getPaddingRight();
        } else {
            avatarLeft = dp(rectangularAvatar ? 15 : 11) + getPaddingLeft();
        }
        avatarStoryParams.originalAvatarRect.set(avatarLeft, dp(7), avatarLeft + dp(rectangularAvatar ? 42 : 46), dp(7) + dp(46));

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (statusLayout != null && statusLayout.getLineCount() > 0) {
                left = statusLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(statusLayout.getLineWidth(0));
                    if (widthpx < statusWidth) {
                        statusLeft += (statusWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
            }
            if (statusLayout != null && statusLayout.getLineCount() > 0) {
                left = statusLayout.getLineRight(0);
                if (left == statusWidth) {
                    widthpx = Math.ceil(statusLayout.getLineWidth(0));
                    if (widthpx < statusWidth) {
                        statusLeft -= (statusWidth - widthpx);
                    }
                }
            }
        }

        nameLeft += getPaddingLeft();
        statusLeft += getPaddingLeft();
        nameLockLeft += getPaddingLeft();
    }

    public void updateStatus(boolean verified, TLRPC.User user, TLRPC.Chat chat, boolean animated) {
        statusDrawable.center = LocaleController.isRTL;
        if (allowEmojiStatus && verified) {
            statusDrawable.set(new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable, 0, 0), animated);
            statusDrawable.setColor(null);
        } else if (allowEmojiStatus && user != null && !savedMessages && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(user.emoji_status), animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        } else if (allowEmojiStatus && chat != null && !savedMessages && DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(chat.emoji_status), animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        } else if (allowEmojiStatus && user != null && !savedMessages && MessagesController.getInstance(currentAccount).isPremiumUser(user)) {
            statusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        } else {
            statusDrawable.set((Drawable) null, animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        }
        long botVerificationIcon = 0;
        if (user != null) {
            botVerificationIcon = DialogObject.getBotVerificationIcon(user);
        } else if (chat != null) {
            botVerificationIcon = DialogObject.getBotVerificationIcon(chat);
        }
        if (botVerificationIcon == 0 || savedMessages) {
            botVerificationDrawable.set((Drawable) null, animated);
        } else {
            botVerificationDrawable.set(botVerificationIcon, animated);
        }
        botVerificationDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
    }

    private boolean rectangularAvatar;
    public void setRectangularAvatar(boolean value) {
        rectangularAvatar = value;
    }

    public void update(int mask) {
        TLRPC.FileLocation photo = null;
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
            } else if (savedMessages) {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
            } else {
                Drawable thumb = avatarDrawable;
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                    if (user.photo.strippedBitmap != null) {
                        thumb = user.photo.strippedBitmap;
                    }
                }
                avatarImage.setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_STRIPPED), "50_50", thumb, user, 0);
            }
        } else if (chat != null) {
            Drawable thumb = avatarDrawable;
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
                if (chat.photo.strippedBitmap != null) {
                    thumb = chat.photo.strippedBitmap;
                }
            }
            if (chat.monoforum) {
                ForumUtilities.setMonoForumAvatar(currentAccount, chat, avatarDrawable, avatarImage);
            } else {
                avatarDrawable.setInfo(currentAccount, chat);
                avatarImage.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_STRIPPED), "50_50", thumb, chat, 0);
            }
        } else if (contact != null) {
            avatarDrawable.setInfo(0, contact.first_name, contact.last_name);
            avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
        } else {
            avatarDrawable.setInfo(0, null, null);
            avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
        }

        avatarImage.setRoundRadius(chat != null && chat.monoforum ? 0 : rectangularAvatar ? dp(10) : chat != null && chat.forum ? dp(16) : dp(23));
        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 && user != null || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 && chat != null) {
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0 && user != null) {
                int newStatus = 0;
                if (user.status != null) {
                    newStatus = user.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0 && (user != null || chat != null)) {
                updateStatus(user != null ? user.verified : chat.verified, user, chat, true);
            }
            if (!continueUpdate && ((mask & MessagesController.UPDATE_MASK_NAME) != 0 && user != null) || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 && chat != null) {
                String newName;
                if (user != null) {
                    newName = user.first_name + user.last_name;
                } else if (chat.monoforum) {
                    newName = ForumUtilities.getMonoForumTitle(currentAccount, chat);
                } else {
                    newName = chat.title;
                }
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && drawCount && (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0) {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
                if (dialog != null && MessagesController.getInstance(currentAccount).getDialogUnreadCount(dialog) != lastUnreadCount) {
                    continueUpdate = true;
                }
            }

            if (!continueUpdate) {
                return;
            }
        }

        if (user != null) {
            if (user.status != null) {
                lastStatus = user.status.expires;
            } else {
                lastStatus = 0;
            }
            lastName = user.first_name + user.last_name;
        } else if (chat != null) {
            lastName = chat.monoforum ?
                ForumUtilities.getMonoForumTitle(currentAccount, chat) :
                chat.title;
        }

        lastAvatar = photo;

        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }
        postInvalidate();
    }

    private PremiumGradient.PremiumGradientTools premiumGradient;
    private Drawable lockDrawable;

    @Override
    protected void onDraw(Canvas canvas) {
        if (user == null && chat == null && encryptedChat == null && contact == null) {
            return;
        }

        if (useSeparator) {
            Paint dividerPaint = null;
            if (customPaints && resourcesProvider != null) {
                dividerPaint = resourcesProvider.getPaint(Theme.key_paint_divider);
            }
            if (dividerPaint == null) {
                dividerPaint = Theme.dividerPaint;
            }
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, dividerPaint);
            } else {
                canvas.drawLine(dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerPaint);
            }
        }

        if (drawNameLock) {
            setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_lockDrawable.draw(canvas);
        }

        if (nameLayout != null) {
            int x;
            if (LocaleController.isRTL) {
                x = (int) (nameLeft + nameLayout.getLineRight(0) + dp(6));
            } else {
                if (nameLayout.getLineLeft(0) == 0) {
                    x = nameLeft - dp(3) - botVerificationDrawable.getIntrinsicWidth();
                } else {
                    float w = nameLayout.getLineWidth(0);
                    x = (int) (nameLeft + nameWidth - Math.ceil(w) - dp(3) - botVerificationDrawable.getIntrinsicWidth());
                }
            }
            setDrawableBounds(botVerificationDrawable, x, nameTop + (nameLayout.getHeight() - botVerificationDrawable.getIntrinsicHeight()) / 2f);
            botVerificationDrawable.draw(canvas);

            canvas.save();
            canvas.translate(nameLeft, nameTop);
            nameLayout.draw(canvas);
            canvas.restore();

            if (LocaleController.isRTL) {
                if (nameLayout.getLineLeft(0) == 0) {
                    x = nameLeft - dp(3) - statusDrawable.getIntrinsicWidth();
                } else {
                    float w = nameLayout.getLineWidth(0);
                    x = (int) (nameLeft + nameWidth - Math.ceil(w) - dp(3) - statusDrawable.getIntrinsicWidth());
                }
            } else {
                x = (int) (nameLeft + nameLayout.getLineRight(0) + dp(6));
            }
            setDrawableBounds(statusDrawable, x, nameTop + (nameLayout.getHeight() - statusDrawable.getIntrinsicHeight()) / 2f);
            statusDrawable.draw(canvas);
        }

        if (ad != null && adText != null && adBackgroundPaint != null) {
            final int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            adBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
            final int w = (int) adText.getWidth() + dp(12.66f);
            final int h = dp(17.33f);
            final int l;
            if (LocaleController.isRTL) {
                l = dp(12);
            } else {
                l = getWidth() - dp(12) - w;
            }

            adBounds.set(l, nameTop, l + w, nameTop + h);
            adBounds.inset(-dp(6), -dp(6));

            canvas.save();
            final float s = adBounce.getScale(0.1f);
            canvas.scale(s, s, adBounds.centerX(), adBounds.centerY());
            canvas.translate(l, nameTop);
            AndroidUtilities.rectTmp.set(0, 0, w, h);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, adBackgroundPaint);
            adText.draw(canvas, dp(6.33f), h / 2.f, color, 1.0f);
            canvas.restore();
        }

        if (statusLayout != null) {
            canvas.save();
            canvas.translate(statusLeft + sublabelOffsetX, dp(33) + sublabelOffsetY);
            statusLayout.draw(canvas);
            canvas.restore();
        }

        if (countLayout != null) {
            final int x = countLeft - dp(5.5f);
            rect.set(x, countTop, x + countWidth + dp(11), countTop + dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, MessagesController.getInstance(currentAccount).isDialogMuted(dialog_id, 0) ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint);
            canvas.save();
            canvas.translate(countLeft, countTop + dp(4));
            countLayout.draw(canvas);
            canvas.restore();
        }

        if (actionLayout != null) {
            actionButton.setColor(Theme.getColor(Theme.key_chats_unreadCounter), Theme.getColor(Theme.key_chats_unreadCounterText));
            AndroidUtilities.rectTmp.set(actionLeft, countTop, actionLeft + actionLayout.getWidth(), countTop + dp(23));
            AndroidUtilities.rectTmp.inset(-dp(16), -dp(4));
            actionButton.setRect(AndroidUtilities.rectTmp);
            actionButton.setRounded(true);
            actionButton.draw(canvas);

            canvas.save();
            canvas.translate(actionLeft, countTop + dp(4));
            actionLayout.draw(canvas);
            canvas.restore();
        }

        if (dontDrawAvatar) {

        } else if (chat != null && chat.monoforum) {
            if (bubbleClip == null) {
                bubbleClip = new PhotoBubbleClip();
            }
            bubbleClip.setBounds((int) avatarStoryParams.originalAvatarRect.centerX(), (int) avatarStoryParams.originalAvatarRect.centerY(), (int) (avatarStoryParams.originalAvatarRect.width() / 2));
            canvas.save();
            canvas.clipPath(bubbleClip);
            avatarImage.setImageCoords(avatarStoryParams.originalAvatarRect);
            avatarImage.draw(canvas);
            canvas.restore();
        } else if (user != null) {
            StoriesUtilities.drawAvatarWithStory(user.id, canvas, avatarImage, avatarStoryParams);
        } else if (chat != null) {
            StoriesUtilities.drawAvatarWithStory(-chat.id, canvas, avatarImage, avatarStoryParams);
        } else {
            avatarImage.setImageCoords(avatarStoryParams.originalAvatarRect);
            avatarImage.draw(canvas);
        }

        final float lockT = premiumBlockedT.set(premiumBlocked);
        if (lockT > 0) {
            final float top = avatarImage.getCenterY() + dp(14);
            final float left = avatarImage.getCenterX() + dp(16);

            canvas.save();
            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            canvas.drawCircle(left, top, dp(10 + 1.33f) * lockT, Theme.dialogs_onlineCirclePaint);
            if (premiumGradient == null) {
                premiumGradient = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1, -1, resourcesProvider);
            }
            premiumGradient.gradientMatrix((int) (left - dp(10)), (int) (top - dp(10)), (int) (left + dp(10)), (int) (top + dp(10)), 0, 0);
            canvas.drawCircle(left, top, dp(10) * lockT, premiumGradient.paint);
            if (lockDrawable == null) {
                lockDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_lock2).mutate();
                lockDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            }
            lockDrawable.setBounds(
                    (int) (left - lockDrawable.getIntrinsicWidth() / 2f * .875f * lockT),
                    (int) (top  - lockDrawable.getIntrinsicHeight() / 2f * .875f * lockT),
                    (int) (left + lockDrawable.getIntrinsicWidth() / 2f * .875f * lockT),
                    (int) (top  + lockDrawable.getIntrinsicHeight() / 2f * .875f * lockT)
            );
            lockDrawable.setAlpha((int) (0xFF * lockT));
            lockDrawable.draw(canvas);
            canvas.restore();
        }

        if (openBot && openButtonText != null) {
            final float buttonWidth = dp(14 + 14) + openButtonText.getCurrentWidth();
            final float x = LocaleController.isRTL ? dp(15) : getWidth() - buttonWidth - dp(15);
            final float h = dp(28);

            openButtonBackgroundPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            openButtonRect.set(x, (getHeight() - h) / 2.0f, x + buttonWidth, (getHeight() + h) / 2.0f);
            canvas.save();
            final float s = openButtonBounce.getScale(.06f);
            canvas.scale(s, s, openButtonRect.centerX(), openButtonRect.centerY());
            canvas.drawRoundRect(openButtonRect, openButtonRect.height() / 2.0f, openButtonRect.height() / 2.0f, openButtonBackgroundPaint);
            openButtonText.draw(canvas, x + dp(14), getHeight() / 2.0f, 0xFFFFFFFF, 1.0f);
            canvas.restore();
        }
    }

    public boolean isBlocked() {
        return premiumBlocked;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder builder = new StringBuilder();
        if (nameLayout != null) {
            builder.append(nameLayout.getText());
        }
        if (drawCheck) {
            builder.append(", ").append(getString(R.string.AccDescrVerified)).append("\n");
        }
        if (statusLayout != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(statusLayout.getText());
        }
        info.setText(builder.toString());
        if (checkBox.isChecked()) {
            info.setCheckable(true);
            info.setChecked(checkBox.isChecked());
            info.setClassName("android.widget.CheckBox");
        }
    }

    public long getDialogId() {
        return dialog_id;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null) {
            return;
        }
        checkBox.setChecked(checked, animated);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (openBot && onOpenButtonClick != null && user != null) {
            final boolean hit = openButtonRect.contains(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                openButtonBounce.setPressed(hit);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (openButtonBounce.isPressed()) {
                    onOpenButtonClick.run(user);
                }
                openButtonBounce.setPressed(false);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                openButtonBounce.setPressed(false);
                return true;
            }
            if (hit || openButtonBounce.isPressed())
                return true;
        } else if (ad != null && onSponsoredOptionsClick != null) {
            final boolean hit = adBounds.contains(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                adBounce.setPressed(hit);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (adBounce.isPressed()) {
                    onSponsoredOptionsClick.run(this, ad);
                }
                adBounce.setPressed(false);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                adBounce.setPressed(false);
                return true;
            }
            if (hit || adBounce.isPressed())
                return true;
        }
        if ((user != null || chat != null) && avatarStoryParams.checkOnTouchEvent(event, this)) {
            return true;
        }
        if (actionButton != null && actionButton.checkTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void updateColors() {
        if (nameLayout != null && getMeasuredWidth() > 0) {
            buildLayout();
        }
    }
}
