/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CanvasButton;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.Stories.StoriesUtilities;

import java.util.Locale;

public class ProfileSearchCell extends BaseCell implements NotificationCenter.NotificationCenterDelegate, Theme.Colorable {

    private CharSequence currentName;
    public ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private CharSequence subLabel;
    private Theme.ResourcesProvider resourcesProvider;

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
    private int countTop = AndroidUtilities.dp(19);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean[] isOnline;

    private boolean drawCheck;
    private boolean drawPremium;

    private final AnimatedFloat premiumBlockedT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean showPremiumBlocked;
    private boolean premiumBlocked;
    private boolean openBot;

    private int statusLeft;
    private StaticLayout statusLayout;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    public StoriesUtilities.AvatarStoryParams avatarStoryParams = new StoriesUtilities.AvatarStoryParams(false);

    private RectF rect = new RectF();

    CheckBox2 checkBox;

    public ProfileSearchCell(Context context) {
        this(context, null);
    }

    public ProfileSearchCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(23));
        avatarDrawable = new AvatarDrawable();

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox);

        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(20));
        statusDrawable.setCallback(this);
    }

    private boolean allowBotOpenButton;
    private Utilities.Callback<TLRPC.User> onOpenButtonClick;
    public ProfileSearchCell allowBotOpenButton(boolean allow, Utilities.Callback<TLRPC.User> onOpenClick) {
        allowBotOpenButton = allow;
        onOpenButtonClick = onOpenClick;
        return this;
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
        return statusDrawable == who || super.verifyDrawable(who);
    }

    public void setData(Object object, TLRPC.EncryptedChat ec, CharSequence n, CharSequence s, boolean needCount, boolean saved) {
        currentName = n;
        if (object instanceof TLRPC.User) {
            user = (TLRPC.User) object;
            chat = null;
            contact = null;
            premiumBlocked = showPremiumBlocked && user != null && MessagesController.getInstance(currentAccount).isUserPremiumBlocked(user.id);
            setOpenBotButton(allowBotOpenButton && user.bot_has_main_app);
        } else if (object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) object;
            user = null;
            contact = null;
            premiumBlocked = false;
            setOpenBotButton(false);
        } else if (object instanceof ContactsController.Contact) {
            contact = (ContactsController.Contact) object;
            chat = null;
            user = null;
            premiumBlocked = showPremiumBlocked && contact != null && contact.user != null && MessagesController.getInstance(currentAccount).isUserPremiumBlocked(contact.user.id);
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
            openButtonText = new Text(LocaleController.getString(R.string.BotOpen), 14, AndroidUtilities.bold());
        }
        int buttonWidth = show ? dp(14 + 14) + (int) openButtonText.getCurrentWidth() + dp(15 + 15) : 0;
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
                    text = LocaleController.getString(R.string.NotificationsCustom);
                } else {
                    text = LocaleController.getString(R.string.NotificationsUnmuted);
                }
            } else if (delta < 60 * 60) {
                text = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60));
            } else if (delta < 60 * 60 * 24) {
                text = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", (int) Math.ceil(delta / 60.0f / 60)));
            } else if (delta < 60 * 60 * 24 * 365) {
                text = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", (int) Math.ceil(delta / 60.0f / 60 / 24)));
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
                text = LocaleController.getString(R.string.NotificationsCustom);
            } else {
                text = enabled ? LocaleController.getString(R.string.NotificationsUnmuted) : LocaleController.getString(R.string.NotificationsMuted);
            }
        }
        if (text == null) {
            text = LocaleController.getString(R.string.NotificationsOff);
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
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        } else if (id == NotificationCenter.userIsPremiumBlockedUpadted) {
            final boolean wasPremiumBlocked = premiumBlocked;
            premiumBlocked = showPremiumBlocked && (user != null && MessagesController.getInstance(currentAccount).isUserPremiumBlocked(user.id) || contact != null && contact.user != null && MessagesController.getInstance(currentAccount).isUserPremiumBlocked(contact.user.id));
            if (premiumBlocked != wasPremiumBlocked) {
                invalidate();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(60) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (user == null && chat == null && encryptedChat == null && contact == null) {
            return;
        }
        if (checkBox != null) {
            int x = LocaleController.isRTL ? (right - left) - AndroidUtilities.dp(42) : AndroidUtilities.dp(42);
            int y = AndroidUtilities.dp(36);
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
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                nameLeft = AndroidUtilities.dp(11);
            }
            nameLockTop = AndroidUtilities.dp(22.0f);
            updateStatus(false, null, null, false);
        } else if (chat != null) {
            dialog_id = -chat.id;
            drawCheck = chat.verified;
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(11);
            }
            updateStatus(drawCheck, null, chat, false);
        } else if (user != null) {
            dialog_id = user.id;
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(11);
            }
            nameLockTop = AndroidUtilities.dp(21);
            drawCheck = user.verified;
            drawPremium = !savedMessages && MessagesController.getInstance(currentAccount).isPremiumUser(user);
            updateStatus(drawCheck, user, null, false);
        } else if (contact != null) {
            dialog_id = 0;
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(11);
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
            statusLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        } else {
            statusLeft = AndroidUtilities.dp(11);
        }

        if (currentName != null) {
            nameString = currentName;
        } else {
            String nameString2 = "";
            if (chat != null) {
                nameString2 = AndroidUtilities.removeDiacritics(chat.title);
            } else if (user != null) {
                nameString2 = AndroidUtilities.removeDiacritics(UserObject.getUserName(user));
            }
            nameString = nameString2.replace('\n', ' ');
        }
        if (nameString.length() == 0) {
            if (user != null && user.phone != null && user.phone.length() != 0) {
                nameString = PhoneFormat.getInstance().format("+" + user.phone);
            } else {
                nameString = LocaleController.getString(R.string.HiddenName);
            }
        }
        if (customPaints) {
            if (namePaint == null) {
                namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                namePaint.setTypeface(AndroidUtilities.bold());
            }
            namePaint.setTextSize(AndroidUtilities.dp(16));
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
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14);
        } else {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        }
        if (drawNameLock) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        }
        if (contact != null) {
            int w = (int) (Theme.dialogs_countTextPaint.measureText(LocaleController.getString(R.string.Invite)) + 1);

            actionLayout = new StaticLayout(LocaleController.getString(R.string.Invite), Theme.dialogs_countTextPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!LocaleController.isRTL) {
                actionLeft = getMeasuredWidth() - w - AndroidUtilities.dp(19) - AndroidUtilities.dp(16);
            } else {
                actionLeft = AndroidUtilities.dp(19) + AndroidUtilities.dp(16);
                nameLeft += w;
                statusLeft += w;
            }
            nameWidth -= AndroidUtilities.dp(32) + w;
        }

        nameWidth -= getPaddingLeft() + getPaddingRight();
        statusWidth -= getPaddingLeft() + getPaddingRight();

        if (drawCount) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
            int unreadCount = MessagesController.getInstance(currentAccount).getDialogUnreadCount(dialog);
            if (unreadCount != 0) {
                lastUnreadCount = unreadCount;
                String countString = String.format(Locale.US, "%d", unreadCount);
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + AndroidUtilities.dp(18);
                nameWidth -= w;
                statusWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(19);
                } else {
                    countLeft = AndroidUtilities.dp(19);
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

        if (nameWidth < 0) {
            nameWidth = 0;
        }
        CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        if (nameStringFinal != null) {
            nameStringFinal = Emoji.replaceEmoji(nameStringFinal, currentNamePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        }
        nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        CharSequence statusString = null;
        TextPaint currentStatusPaint = Theme.dialogs_offlinePaint;
        if (chat == null || subLabel != null) {
            if (subLabel != null) {
                statusString = subLabel;
            } else if (user != null) {
                if (MessagesController.isSupportUser(user)) {
                    statusString = LocaleController.getString(R.string.SupportStatus);
                } else if (user.bot && user.bot_active_users != 0) {
                    statusString = LocaleController.formatPluralStringSpaced("BotUsersShort", user.bot_active_users);
                } else if (user.bot) {
                    statusString = LocaleController.getString(R.string.Bot);
                } else if (user.id == UserObject.VERIFY) {
                    statusString = LocaleController.getString(R.string.VerifyCodesNotifications);
                } else if (UserObject.isService(user.id)) {
                    statusString = LocaleController.getString(R.string.ServiceNotifications);
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
                        statusString = LocaleController.getString(R.string.Online);
                    }
                }
            }
            if (savedMessages || UserObject.isReplyUser(user)) {
                statusString = null;
                nameTop = AndroidUtilities.dp(20);
            }
        } else {
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (chat.participants_count != 0) {
                    statusString = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                } else {
                    if (!ChatObject.isPublic(chat)) {
                        statusString = LocaleController.getString(R.string.ChannelPrivate).toLowerCase();
                    } else {
                        statusString = LocaleController.getString(R.string.ChannelPublic).toLowerCase();
                    }
                }
            } else {
                if (chat.participants_count != 0) {
                    statusString = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                } else {
                    if (chat.has_geo) {
                        statusString = LocaleController.getString(R.string.MegaLocation);
                    } else if (!ChatObject.isPublic(chat)) {
                        statusString = LocaleController.getString(R.string.MegaPrivate).toLowerCase();
                    } else {
                        statusString = LocaleController.getString(R.string.MegaPublic).toLowerCase();
                    }
                }
            }
            nameTop = AndroidUtilities.dp(19);
        }
        if (customPaints) {
            if (statusPaint == null) {
                statusPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            }
            statusPaint.setTextSize(AndroidUtilities.dp(15));
            if (currentStatusPaint == Theme.dialogs_offlinePaint) {
                statusPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
            } else if (currentStatusPaint == Theme.dialogs_onlinePaint) {
                statusPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText3, resourcesProvider));
            }
            currentStatusPaint = statusPaint;
        }

        if (!TextUtils.isEmpty(statusString)) {
            CharSequence statusStringFinal = TextUtils.ellipsize(statusString, currentStatusPaint, statusWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            statusLayout = new StaticLayout(statusStringFinal, currentStatusPaint, statusWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            nameTop = AndroidUtilities.dp(9);
            nameLockTop -= AndroidUtilities.dp(10);
        } else {
            nameTop = AndroidUtilities.dp(20);
            statusLayout = null;
        }

        int avatarLeft;
        if (LocaleController.isRTL) {
            avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(57) - getPaddingRight();
        } else {
            avatarLeft = AndroidUtilities.dp(11) + getPaddingLeft();
        }
        avatarStoryParams.originalAvatarRect.set(avatarLeft, AndroidUtilities.dp(7), avatarLeft + AndroidUtilities.dp(46), AndroidUtilities.dp(7) + AndroidUtilities.dp(46));

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
        if (verified) {
            statusDrawable.set(new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable, 0, 0), animated);
            statusDrawable.setColor(null);
        } else if (user != null && !savedMessages && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(user.emoji_status), animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        } else if (chat != null && !savedMessages && DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(chat.emoji_status), animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        } else if (user != null && !savedMessages && MessagesController.getInstance(currentAccount).isPremiumUser(user)) {
            statusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        } else {
            statusDrawable.set((Drawable) null, animated);
            statusDrawable.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
        }
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
            avatarDrawable.setInfo(currentAccount, chat);
            avatarImage.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_STRIPPED), "50_50", thumb, chat, 0);
        } else if (contact != null) {
            avatarDrawable.setInfo(0, contact.first_name, contact.last_name);
            avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
        } else {
            avatarDrawable.setInfo(0, null, null);
            avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
        }

        avatarImage.setRoundRadius(chat != null && chat.forum ? AndroidUtilities.dp(16) : AndroidUtilities.dp(23));
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
            lastName = chat.title;
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
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerPaint);
            }
        }

        if (drawNameLock) {
            setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_lockDrawable.draw(canvas);
        }

        if (nameLayout != null) {
            canvas.save();
            canvas.translate(nameLeft, nameTop);
            nameLayout.draw(canvas);
            canvas.restore();

            int x;
            if (LocaleController.isRTL) {
                if (nameLayout.getLineLeft(0) == 0) {
                    x = nameLeft - AndroidUtilities.dp(3) - statusDrawable.getIntrinsicWidth();
                } else {
                    float w = nameLayout.getLineWidth(0);
                    x = (int) (nameLeft + nameWidth - Math.ceil(w) - AndroidUtilities.dp(3) - statusDrawable.getIntrinsicWidth());
                }
            } else {
                x = (int) (nameLeft + nameLayout.getLineRight(0) + AndroidUtilities.dp(6));
            }
            setDrawableBounds(statusDrawable, x, nameTop + (nameLayout.getHeight() - statusDrawable.getIntrinsicHeight()) / 2f);
            statusDrawable.draw(canvas);
        }

        if (statusLayout != null) {
            canvas.save();
            canvas.translate(statusLeft + sublabelOffsetX, AndroidUtilities.dp(33) + sublabelOffsetY);
            statusLayout.draw(canvas);
            canvas.restore();
        }

        if (countLayout != null) {
            int x = countLeft - AndroidUtilities.dp(5.5f);
            rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, MessagesController.getInstance(currentAccount).isDialogMuted(dialog_id, 0) ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint);
            canvas.save();
            canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
            countLayout.draw(canvas);
            canvas.restore();
        }

        if (actionLayout != null) {
            actionButton.setColor(Theme.getColor(Theme.key_chats_unreadCounter), Theme.getColor(Theme.key_chats_unreadCounterText));
            AndroidUtilities.rectTmp.set(actionLeft, countTop, actionLeft + actionLayout.getWidth(), countTop + AndroidUtilities.dp(23));
            AndroidUtilities.rectTmp.inset(-AndroidUtilities.dp(16), -AndroidUtilities.dp(4));
            actionButton.setRect(AndroidUtilities.rectTmp);
            actionButton.setRounded(true);
            actionButton.draw(canvas);

            canvas.save();
            canvas.translate(actionLeft, countTop + AndroidUtilities.dp(4));
            actionLayout.draw(canvas);
            canvas.restore();
        }

        if (user != null) {
            StoriesUtilities.drawAvatarWithStory(user.id, canvas, avatarImage, avatarStoryParams);
        } else if (chat != null) {
            StoriesUtilities.drawAvatarWithStory(-chat.id, canvas, avatarImage, avatarStoryParams);
        } else {
            avatarImage.setImageCoords(avatarStoryParams.originalAvatarRect);
            avatarImage.draw(canvas);
        }

        final float lockT = premiumBlockedT.set(premiumBlocked);
        if (lockT > 0) {
            float top = avatarImage.getCenterY() + dp(14);
            float left = avatarImage.getCenterX() + dp(16);

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
            builder.append(", ").append(LocaleController.getString(R.string.AccDescrVerified)).append("\n");
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
