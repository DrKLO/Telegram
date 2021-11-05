/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.view.HapticFeedbackConstants;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EmptyStubSpan;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Adapters.DialogsAdapter;
import org.telegram.ui.Components.PullForegroundDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.StatusDrawable;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Components.SwipeGestureSettingsView;

import java.util.ArrayList;

public class DialogCell extends BaseCell {

    boolean moving;
    private RLottieDrawable lastDrawTranslationDrawable;
    private int lastDrawSwipeMessageStringId;
    public boolean swipeCanceled;

    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    public boolean isMoving() {
        return moving;
    }

    public static class FixedWidthSpan extends ReplacementSpan {

        private int width;

        public FixedWidthSpan(int w) {
            width = w;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) {
                fm = paint.getFontMetricsInt();
            }
            if (fm != null) {
                int h = fm.descent - fm.ascent;
                fm.bottom = fm.descent = 1 - h;
                fm.top = fm.ascent = -1;
            }
            return width;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {

        }
    }

    public static class CustomDialog {
        public String name;
        public String message;
        public int id;
        public int unread_count;
        public boolean pinned;
        public boolean muted;
        public int type;
        public int date;
        public boolean verified;
        public boolean isMedia;
        public boolean sent;
    }

    private int paintIndex;

    private int currentAccount;
    private CustomDialog customDialog;
    private long currentDialogId;
    private int currentDialogFolderId;
    private int currentDialogFolderDialogsCount;
    private int currentEditDate;
    private boolean isDialogCell;
    private int lastMessageDate;
    private int unreadCount;
    private boolean markUnread;
    private int mentionCount;
    private boolean lastUnreadState;
    private int lastSendState;
    private boolean dialogMuted;
    private float dialogMutedProgress;
    private MessageObject message;
    private boolean clearingDialog;
    private CharSequence lastMessageString;
    private int index;
    private int dialogsType;
    private int folderId;
    private int messageId;
    private boolean archiveHidden;

    private float cornerProgress;
    private long lastUpdateTime;
    private float onlineProgress;
    private float chatCallProgress;
    private float innerProgress;
    private int progressStage;

    private float clipProgress;
    private int topClip;
    private int bottomClip;
    private float translationX;
    private boolean isSliding;
    private RLottieDrawable translationDrawable;
    private boolean translationAnimationStarted;
    private boolean drawRevealBackground;
    private float currentRevealProgress;
    private float currentRevealBounceProgress;
    private float archiveBackgroundProgress;

    private boolean hasMessageThumb;
    private ImageReceiver thumbImage = new ImageReceiver(this);
    private boolean drawPlay;

    private ImageReceiver avatarImage = new ImageReceiver(this);
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private boolean animatingArchiveAvatar;
    private float animatingArchiveAvatarProgress;
    private BounceInterpolator interpolator = new BounceInterpolator();
    private PullForegroundDrawable archivedChatsDrawable;

    private TLRPC.User user;
    private TLRPC.Chat chat;
    private TLRPC.EncryptedChat encryptedChat;
    private CharSequence lastPrintString;
    private int printingStringType;
    private TLRPC.DraftMessage draftMessage;

    private CheckBox2 checkBox;

    public boolean useForceThreeLines;
    public boolean useSeparator;
    public boolean fullSeparator;
    public boolean fullSeparator2;

    private boolean useMeForMyMessages;

    private boolean hasCall;

    private int nameLeft;
    private StaticLayout nameLayout;
    private boolean drawNameLock;
    private boolean drawNameGroup;
    private boolean drawNameBroadcast;
    private boolean drawNameBot;
    private int nameMuteLeft;
    private int nameLockLeft;
    private int nameLockTop;

    private int timeLeft;
    private int timeTop;
    private StaticLayout timeLayout;

    private boolean promoDialog;

    private boolean drawCheck1;
    private boolean drawCheck2;
    private boolean drawClock;
    private int checkDrawLeft;
    private int checkDrawLeft1;
    private int clockDrawLeft;
    private int checkDrawTop;
    private int halfCheckDrawLeft;

    private int messageTop;
    private int messageLeft;
    private StaticLayout messageLayout;

    private int messageNameTop;
    private int messageNameLeft;
    private StaticLayout messageNameLayout;

    private boolean drawError;
    private int errorTop;
    private int errorLeft;

    private boolean attachedToWindow;

    private float reorderIconProgress;
    private boolean drawReorder;
    private boolean drawPinBackground;
    private boolean drawPin;
    private int pinTop;
    private int pinLeft;

    private boolean drawCount;
    private int countTop;
    private int countLeft;
    private int countWidth;
    private int countWidthOld;
    private int countLeftOld;
    private boolean countAnimationIncrement;
    private ValueAnimator countAnimator;
    private float countChangeProgress = 1f;
    private StaticLayout countLayout;
    private StaticLayout countOldLayout;
    private StaticLayout countAnimationStableLayout;
    private StaticLayout countAnimationInLayout;

    private boolean drawMention;
    private int mentionLeft;
    private int mentionWidth;
    private StaticLayout mentionLayout;

    private boolean drawVerified;

    private int drawScam;

    private boolean isSelected;

    private RectF rect = new RectF();
    private DialogsAdapter.DialogsPreloader preloader;

    private int animateToStatusDrawableParams;
    private int animateFromStatusDrawableParams;
    private int lastStatusDrawableParams = -1;
    private float statusDrawableProgress;
    private boolean statusDrawableAnimationInProgress;
    private ValueAnimator statusDrawableAnimator;
    long lastDialogChangedTime;
    private int statusDrawableLeft;

    private DialogsActivity parentFragment;

    private StaticLayout swipeMessageTextLayout;
    private int swipeMessageTextId;
    private int swipeMessageWidth;

    public static class BounceInterpolator implements Interpolator {

        public float getInterpolation(float t) {
            if (t < 0.33f) {
                return 0.1f * (t / 0.33f);
            } else {
                t -= 0.33f;
                if (t < 0.33f) {
                    return 0.1f - 0.15f * (t / 0.34f);
                } else {
                    t -= 0.34f;
                    return -0.05f + 0.05f * (t / 0.33f);
                }
            }
        }
    }

    public DialogCell(DialogsActivity fragment, Context context, boolean needCheck, boolean forceThreeLines) {
        this(fragment, context, needCheck, forceThreeLines, UserConfig.selectedAccount, null);
    }

    private final Theme.ResourcesProvider resourcesProvider;

    public DialogCell(DialogsActivity fragment, Context context, boolean needCheck, boolean forceThreeLines, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        parentFragment = fragment;
        Theme.createDialogsResources(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(28));
        thumbImage.setRoundRadius(AndroidUtilities.dp(2));
        useForceThreeLines = forceThreeLines;
        currentAccount = account;

        if (needCheck) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox);
        }
    }

    public void setDialog(TLRPC.Dialog dialog, int type, int folder) {
        if (currentDialogId != dialog.id) {
            if (statusDrawableAnimator != null) {
                statusDrawableAnimator.removeAllListeners();
                statusDrawableAnimator.cancel();
            }
            statusDrawableAnimationInProgress = false;
            lastStatusDrawableParams = -1;
        }
        currentDialogId = dialog.id;
        lastDialogChangedTime = System.currentTimeMillis();
        isDialogCell = true;
        if (dialog instanceof TLRPC.TL_dialogFolder) {
            TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
            currentDialogFolderId = dialogFolder.folder.id;
            if (archivedChatsDrawable != null) {
                archivedChatsDrawable.setCell(this);
            }
        } else {
            currentDialogFolderId = 0;
        }
        dialogsType = type;
        folderId = folder;
        messageId = 0;
        update(0, false);
        checkOnline();
        checkGroupCall();
        checkChatTheme();
    }

    public void setDialogIndex(int i) {
        index = i;
    }

    public void setDialog(CustomDialog dialog) {
        customDialog = dialog;
        messageId = 0;
        update(0);
        checkOnline();
        checkGroupCall();
        checkChatTheme();
    }

    private void checkOnline() {
        if (user != null) {
            TLRPC.User newUser = MessagesController.getInstance(currentAccount).getUser(user.id);
            if (newUser != null) {
                user = newUser;
            }
        }
        boolean isOnline = isOnline();
        onlineProgress = isOnline ? 1.0f : 0.0f;
    }

    private boolean isOnline() {
        if (user == null || user.self) {
            return false;
        }
        if (user.status != null && user.status.expires <= 0) {
            if (MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id)) {
                return true;
            }
        }
        if (user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
            return true;
        }
        return false;
    }

    private void checkGroupCall() {
        hasCall = chat != null && chat.call_active && chat.call_not_empty;
        chatCallProgress = hasCall ? 1.0f : 0.0f;
    }

    private void checkChatTheme() {
        if (message != null && message.messageOwner != null && message.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme && lastUnreadState) {
            TLRPC.TL_messageActionSetChatTheme setThemeAction = (TLRPC.TL_messageActionSetChatTheme) message.messageOwner.action;
            ChatThemeController.getInstance(currentAccount).setDialogTheme(currentDialogId, setThemeAction.emoticon, false);
        }
    }

    public void setDialog(long dialog_id, MessageObject messageObject, int date, boolean useMe) {
        if (currentDialogId != dialog_id) {
            lastStatusDrawableParams = -1;
        }
        currentDialogId = dialog_id;
        lastDialogChangedTime = System.currentTimeMillis();
        message = messageObject;
        useMeForMyMessages = useMe;
        isDialogCell = false;
        lastMessageDate = date;
        currentEditDate = messageObject != null ? messageObject.messageOwner.edit_date : 0;
        unreadCount = 0;
        markUnread = false;
        messageId = messageObject != null ? messageObject.getId() : 0;
        mentionCount = 0;
        lastUnreadState = messageObject != null && messageObject.isUnread();
        if (message != null) {
            lastSendState = message.messageOwner.send_state;
        }
        update(0);
    }

    public long getDialogId() {
        return currentDialogId;
    }

    public int getDialogIndex() {
        return index;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setPreloader(DialogsAdapter.DialogsPreloader preloader) {
        this.preloader = preloader;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isSliding = false;
        drawRevealBackground = false;
        currentRevealProgress = 0.0f;
        attachedToWindow = false;
        reorderIconProgress = drawPin && drawReorder ? 1.0f : 0.0f;
        avatarImage.onDetachedFromWindow();
        thumbImage.onDetachedFromWindow();
        if (translationDrawable != null) {
            translationDrawable.stop();
            translationDrawable.setProgress(0.0f);
            translationDrawable.setCallback(null);
            translationDrawable = null;
            translationAnimationStarted = false;
        }
        if (preloader != null) {
            preloader.remove(currentDialogId);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        thumbImage.onAttachedToWindow();
        resetPinnedArchiveState();
    }

    public void resetPinnedArchiveState() {
        archiveHidden = SharedConfig.archiveHidden;
        archiveBackgroundProgress = archiveHidden ? 0.0f : 1.0f;
        avatarDrawable.setArchivedAvatarHiddenProgress(archiveBackgroundProgress);
        clipProgress = 0.0f;
        isSliding = false;
        reorderIconProgress = drawPin && drawReorder ? 1.0f : 0.0f;
        attachedToWindow = true;
        cornerProgress = 0.0f;
        setTranslationX(0);
        setTranslationY(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 78 : 72) + (useSeparator ? 1 : 0));
        topClip = 0;
        bottomClip = getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentDialogId == 0 && customDialog == null) {
            return;
        }
        if (checkBox != null) {
            int x = LocaleController.isRTL ? (right - left) - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 43 : 45) : AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 43 : 45);
            int y = AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 48 : 42);
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        if (changed) {
            try {
                buildLayout();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public boolean isUnread() {
        return (unreadCount != 0 || markUnread) && !dialogMuted;
    }

    private CharSequence formatArchivedDialogNames() {
        ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getDialogs(currentDialogFolderId);
        currentDialogFolderDialogsCount = dialogs.size();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int a = 0, N = dialogs.size(); a < N; a++) {
            TLRPC.Dialog dialog = dialogs.get(a);
            TLRPC.User currentUser = null;
            TLRPC.Chat currentChat = null;
            if (DialogObject.isEncryptedDialog(dialog.id)) {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialog.id));
                if (encryptedChat != null) {
                    currentUser = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                }
            } else {
                if (DialogObject.isUserDialog(dialog.id)) {
                    currentUser = MessagesController.getInstance(currentAccount).getUser(dialog.id);
                } else {
                    currentChat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                }
            }
            String title;
            if (currentChat != null) {
                title = currentChat.title.replace('\n', ' ');
            } else if (currentUser != null) {
                if (UserObject.isDeleted(currentUser)) {
                    title = LocaleController.getString("HiddenName", R.string.HiddenName);
                } else {
                    title = ContactsController.formatName(currentUser.first_name, currentUser.last_name).replace('\n', ' ');
                }
            } else {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            int boldStart = builder.length();
            int boldEnd = boldStart + title.length();
            builder.append(title);
            if (dialog.unread_count > 0) {
                builder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, Theme.getColor(Theme.key_chats_nameArchived, resourcesProvider)), boldStart, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (builder.length() > 150) {
                break;
            }
        }
        return Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), AndroidUtilities.dp(17), false);
    }


    public void buildLayout() {
        int thumbSize;
        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
            Theme.dialogs_namePaint[1].setTextSize(AndroidUtilities.dp(16));
            Theme.dialogs_nameEncryptedPaint[1].setTextSize(AndroidUtilities.dp(16));
            Theme.dialogs_messagePaint[1].setTextSize(AndroidUtilities.dp(15));
            Theme.dialogs_messagePrintingPaint[1].setTextSize(AndroidUtilities.dp(15));

            Theme.dialogs_messagePaint[1].setColor(Theme.dialogs_messagePaint[1].linkColor = Theme.getColor(Theme.key_chats_message_threeLines, resourcesProvider));
            paintIndex = 1;
            thumbSize = 18;
        } else {
            Theme.dialogs_namePaint[0].setTextSize(AndroidUtilities.dp(17));
            Theme.dialogs_nameEncryptedPaint[0].setTextSize(AndroidUtilities.dp(17));
            Theme.dialogs_messagePaint[0].setTextSize(AndroidUtilities.dp(16));
            Theme.dialogs_messagePrintingPaint[0].setTextSize(AndroidUtilities.dp(16));

            Theme.dialogs_messagePaint[0].setColor(Theme.dialogs_messagePaint[0].linkColor = Theme.getColor(Theme.key_chats_message, resourcesProvider));
            paintIndex = 0;
            thumbSize = 19;
        }

        currentDialogFolderDialogsCount = 0;
        String nameString = "";
        String timeString = "";
        String countString = null;
        String mentionString = null;
        CharSequence messageString = "";
        CharSequence messageNameString = null;
        CharSequence printingString = null;
        if (isDialogCell) {
            printingString = MessagesController.getInstance(currentAccount).getPrintingString(currentDialogId, 0, true);
        }
        TextPaint currentMessagePaint = Theme.dialogs_messagePaint[paintIndex];
        boolean checkMessage = true;

        drawNameGroup = false;
        drawNameBroadcast = false;
        drawNameLock = false;
        drawNameBot = false;
        drawVerified = false;
        drawScam = 0;
        drawPinBackground = false;
        hasMessageThumb = false;
        int offsetName = 0;
        boolean showChecks = !UserObject.isUserSelf(user) && !useMeForMyMessages;
        boolean drawTime = true;
        printingStringType = -1;
        int printigStingReplaceIndex = -1;

        String messageFormat;
        boolean hasNameInMessage;
        if (Build.VERSION.SDK_INT >= 18) {
            if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || currentDialogFolderId != 0) {
                messageFormat = "%2$s: \u2068%1$s\u2069";
                hasNameInMessage = true;
            } else {
                messageFormat = "\u2068%s\u2069";
                hasNameInMessage = false;
            }
        } else {
            if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || currentDialogFolderId != 0) {
                messageFormat = "%2$s: %1$s";
                hasNameInMessage = true;
            } else {
                messageFormat = "%1$s";
                hasNameInMessage = false;
            }
        }

        lastMessageString = message != null ? message.messageText : null;

        if (customDialog != null) {
            if (customDialog.type == 2) {
                drawNameLock = true;
                if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                    nameLockTop = AndroidUtilities.dp(12.5f);
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(72 + 6);
                        nameLeft = AndroidUtilities.dp(72 + 10) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 6) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                        nameLeft = AndroidUtilities.dp(22);
                    }
                } else {
                    nameLockTop = AndroidUtilities.dp(16.5f);
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(72 + 4);
                        nameLeft = AndroidUtilities.dp(72 + 8) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 4) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                        nameLeft = AndroidUtilities.dp(18);
                    }
                }
            } else {
                drawVerified = customDialog.verified;
                if (SharedConfig.drawDialogIcons && customDialog.type == 1) {
                    drawNameGroup = true;
                    if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                        nameLockTop = AndroidUtilities.dp(13.5f);
                        if (!LocaleController.isRTL) {
                            nameLockLeft = AndroidUtilities.dp(72 + 6);
                            nameLeft = AndroidUtilities.dp(72 + 10) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                        } else {
                            nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 6) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                            nameLeft = AndroidUtilities.dp(22);
                        }
                    } else {
                        if (!LocaleController.isRTL) {
                            nameLockTop = AndroidUtilities.dp(17.5f);
                            nameLockLeft = AndroidUtilities.dp(72 + 4);
                            nameLeft = AndroidUtilities.dp(72 + 8) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                        } else {
                            nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 4) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                            nameLeft = AndroidUtilities.dp(18);
                        }
                    }
                } else {
                    if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                        if (!LocaleController.isRTL) {
                            nameLeft = AndroidUtilities.dp(72 + 6);
                        } else {
                            nameLeft = AndroidUtilities.dp(22);
                        }
                    } else {
                        if (!LocaleController.isRTL) {
                            nameLeft = AndroidUtilities.dp(72 + 4);
                        } else {
                            nameLeft = AndroidUtilities.dp(18);
                        }
                    }
                }
            }

            if (customDialog.type == 1) {
                messageNameString = LocaleController.getString("FromYou", R.string.FromYou);
                checkMessage = false;
                SpannableStringBuilder stringBuilder;
                if (customDialog.isMedia) {
                    currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, message.messageText));
                    stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_attachMessage, resourcesProvider), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    String mess = customDialog.message;
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                        stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess, messageNameString));
                    } else {
                        stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess.replace('\n', ' '), messageNameString));
                    }
                }
                messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), AndroidUtilities.dp(20), false);
            } else {
                messageString = customDialog.message;
                if (customDialog.isMedia) {
                    currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                }
            }

            timeString = LocaleController.stringForMessageListDate(customDialog.date);

            if (customDialog.unread_count != 0) {
                drawCount = true;
                countString = String.format("%d", customDialog.unread_count);
            } else {
                drawCount = false;
            }

            if (customDialog.sent) {
                drawCheck1 = true;
                drawCheck2 = true;
            } else {
                drawCheck1 = false;
                drawCheck2 = false;
            }
            drawClock = false;
            drawError = false;
            nameString = customDialog.name;
        } else {
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                if (!LocaleController.isRTL) {
                    nameLeft = AndroidUtilities.dp(72 + 6);
                } else {
                    nameLeft = AndroidUtilities.dp(22);
                }
            } else {
                if (!LocaleController.isRTL) {
                    nameLeft = AndroidUtilities.dp(72 + 4);
                } else {
                    nameLeft = AndroidUtilities.dp(18);
                }
            }

            if (encryptedChat != null) {
                if (currentDialogFolderId == 0) {
                    drawNameLock = true;
                    if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                        nameLockTop = AndroidUtilities.dp(12.5f);
                        if (!LocaleController.isRTL) {
                            nameLockLeft = AndroidUtilities.dp(72 + 6);
                            nameLeft = AndroidUtilities.dp(72 + 10) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                        } else {
                            nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 6) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                            nameLeft = AndroidUtilities.dp(22);
                        }
                    } else {
                        nameLockTop = AndroidUtilities.dp(16.5f);
                        if (!LocaleController.isRTL) {
                            nameLockLeft = AndroidUtilities.dp(72 + 4);
                            nameLeft = AndroidUtilities.dp(72 + 8) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                        } else {
                            nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 4) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                            nameLeft = AndroidUtilities.dp(18);
                        }
                    }
                }
            } else {
                if (currentDialogFolderId == 0) {
                    if (chat != null) {
                        if (chat.scam) {
                            drawScam = 1;
                            Theme.dialogs_scamDrawable.checkText();
                        } else if (chat.fake) {
                            drawScam = 2;
                            Theme.dialogs_fakeDrawable.checkText();
                        } else {
                            drawVerified = chat.verified;
                        }
                        if (SharedConfig.drawDialogIcons) {
                            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                                if (chat.id < 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
                                    drawNameBroadcast = true;
                                    nameLockTop = AndroidUtilities.dp(12.5f);
                                } else {
                                    drawNameGroup = true;
                                    nameLockTop = AndroidUtilities.dp(13.5f);
                                }

                                if (!LocaleController.isRTL) {
                                    nameLockLeft = AndroidUtilities.dp(72 + 6);
                                    nameLeft = AndroidUtilities.dp(72 + 10) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                                } else {
                                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 6) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                                    nameLeft = AndroidUtilities.dp(22);
                                }
                            } else {
                                if (chat.id < 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
                                    drawNameBroadcast = true;
                                    nameLockTop = AndroidUtilities.dp(16.5f);
                                } else {
                                    drawNameGroup = true;
                                    nameLockTop = AndroidUtilities.dp(17.5f);
                                }

                                if (!LocaleController.isRTL) {
                                    nameLockLeft = AndroidUtilities.dp(72 + 4);
                                    nameLeft = AndroidUtilities.dp(72 + 8) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                                } else {
                                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 4) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                                    nameLeft = AndroidUtilities.dp(18);
                                }
                            }
                        }
                    } else if (user != null) {
                        if (user.scam) {
                            drawScam = 1;
                            Theme.dialogs_scamDrawable.checkText();
                        } else if (user.fake) {
                            drawScam = 2;
                            Theme.dialogs_fakeDrawable.checkText();
                        } else {
                            drawVerified = user.verified;
                        }
                        if (SharedConfig.drawDialogIcons && user.bot) {
                            drawNameBot = true;
                            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                                nameLockTop = AndroidUtilities.dp(12.5f);
                                if (!LocaleController.isRTL) {
                                    nameLockLeft = AndroidUtilities.dp(72 + 6);
                                    nameLeft = AndroidUtilities.dp(72 + 10) + Theme.dialogs_botDrawable.getIntrinsicWidth();
                                } else {
                                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 6) - Theme.dialogs_botDrawable.getIntrinsicWidth();
                                    nameLeft = AndroidUtilities.dp(22);
                                }
                            } else {
                                nameLockTop = AndroidUtilities.dp(16.5f);
                                if (!LocaleController.isRTL) {
                                    nameLockLeft = AndroidUtilities.dp(72 + 4);
                                    nameLeft = AndroidUtilities.dp(72 + 8) + Theme.dialogs_botDrawable.getIntrinsicWidth();
                                } else {
                                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(72 + 4) - Theme.dialogs_botDrawable.getIntrinsicWidth();
                                    nameLeft = AndroidUtilities.dp(18);
                                }
                            }
                        }
                    }
                }
            }

            int lastDate = lastMessageDate;
            if (lastMessageDate == 0 && message != null) {
                lastDate = message.messageOwner.date;
            }

            if (isDialogCell) {
                draftMessage = MediaDataController.getInstance(currentAccount).getDraft(currentDialogId, 0);
                if (draftMessage != null && (TextUtils.isEmpty(draftMessage.message) && draftMessage.reply_to_msg_id == 0 || lastDate > draftMessage.date && unreadCount != 0) ||
                        ChatObject.isChannel(chat) && !chat.megagroup && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) ||
                        chat != null && (chat.left || chat.kicked)) {
                    draftMessage = null;
                }
            } else {
                draftMessage = null;
            }

            if (printingString != null) {
                lastPrintString = printingString;
                printingStringType = MessagesController.getInstance(currentAccount).getPrintingStringType(currentDialogId, 0);
                StatusDrawable statusDrawable = Theme.getChatStatusDrawable(printingStringType);
                int startPadding = 0;
                if (statusDrawable != null) {
                    startPadding = statusDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3);
                }
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

                printingString = TextUtils.replace(printingString, new String[]{"..."}, new String[]{""});
                if (printingStringType == 5) {
                    printigStingReplaceIndex = printingString.toString().indexOf("**oo**");
                }
                if (printigStingReplaceIndex >= 0) {
                    spannableStringBuilder.append(printingString).setSpan(new FixedWidthSpan(Theme.getChatStatusDrawable(printingStringType).getIntrinsicWidth()), printigStingReplaceIndex, printigStingReplaceIndex + 6, 0);
                } else {
                    spannableStringBuilder.append(" ").append(printingString).setSpan(new FixedWidthSpan(startPadding), 0, 1, 0);
                }

                messageString = spannableStringBuilder;
                currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                checkMessage = false;
            } else {
                lastPrintString = null;
                if (draftMessage != null) {
                    checkMessage = false;
                    messageNameString = LocaleController.getString("Draft", R.string.Draft);
                    if (TextUtils.isEmpty(draftMessage.message)) {
                        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                            messageString = "";
                        } else {
                            SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(messageNameString);
                            stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_draft, resourcesProvider), 0, messageNameString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            messageString = stringBuilder;
                        }
                    } else {
                        String mess = draftMessage.message;
                        if (mess.length() > 150) {
                            mess = mess.substring(0, 150);
                        }
                        SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess.replace('\n', ' '), messageNameString));
                        if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout) {
                            stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_draft, resourcesProvider), 0, messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    }
                } else {
                    if (clearingDialog) {
                        currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                        messageString = LocaleController.getString("HistoryCleared", R.string.HistoryCleared);
                    } else if (message == null) {
                        if (encryptedChat != null) {
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                            if (encryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
                                messageString = LocaleController.getString("EncryptionProcessing", R.string.EncryptionProcessing);
                            } else if (encryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
                                messageString = LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, UserObject.getFirstName(user));
                            } else if (encryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
                                messageString = LocaleController.getString("EncryptionRejected", R.string.EncryptionRejected);
                            } else if (encryptedChat instanceof TLRPC.TL_encryptedChat) {
                                if (encryptedChat.admin_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                    messageString = LocaleController.formatString("EncryptedChatStartedOutgoing", R.string.EncryptedChatStartedOutgoing, UserObject.getFirstName(user));
                                } else {
                                    messageString = LocaleController.getString("EncryptedChatStartedIncoming", R.string.EncryptedChatStartedIncoming);
                                }
                            }
                        } else {
                            if (dialogsType == 3 && UserObject.isUserSelf(user)) {
                                messageString = LocaleController.getString("SavedMessagesInfo", R.string.SavedMessagesInfo);
                                showChecks = false;
                                drawTime = false;
                            } else {
                                messageString = "";
                            }
                        }
                    } else {
                        String restrictionReason = MessagesController.getRestrictionReason(message.messageOwner.restriction_reason);
                        TLRPC.User fromUser = null;
                        TLRPC.Chat fromChat = null;
                        long fromId = message.getFromChatId();
                        if (DialogObject.isUserDialog(fromId)) {
                            fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
                        } else {
                            fromChat = MessagesController.getInstance(currentAccount).getChat(-fromId);
                        }
                        if (dialogsType == 3 && UserObject.isUserSelf(user)) {
                            messageString = LocaleController.getString("SavedMessagesInfo", R.string.SavedMessagesInfo);
                            showChecks = false;
                            drawTime = false;
                        } else if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout && currentDialogFolderId != 0) {
                            checkMessage = false;
                            messageString = formatArchivedDialogNames();
                        } else if (message.messageOwner instanceof TLRPC.TL_messageService) {
                            if (ChatObject.isChannelAndNotMegaGroup(chat) && (message.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear ||
                                    message.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom)) {
                                messageString = "";
                                showChecks = false;
                            } else {
                                messageString = message.messageText;
                            }
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                        } else {
                            boolean needEmoji = true;
                            if (TextUtils.isEmpty(restrictionReason) && currentDialogFolderId == 0 && encryptedChat == null && !message.needDrawBluredPreview() && (message.isPhoto() || message.isNewGif() || message.isVideo())) {
                                String type = message.isWebpage() ? message.messageOwner.media.webpage.type : null;
                                if (!("app".equals(type) || "profile".equals(type) || "article".equals(type) || type != null && type.startsWith("telegram_"))) {
                                    TLRPC.PhotoSize smallThumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 40);
                                    TLRPC.PhotoSize bigThumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
                                    if (smallThumb == bigThumb) {
                                        bigThumb = null;
                                    }
                                    if (smallThumb != null) {
                                        hasMessageThumb = true;
                                        drawPlay = message.isVideo();
                                        String fileName = FileLoader.getAttachFileName(bigThumb);
                                        if (message.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(message) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                                            int size;
                                            if (message.type == MessageObject.TYPE_PHOTO) {
                                                size = bigThumb != null ? bigThumb.size : 0;
                                            } else {
                                                size = 0;
                                            }
                                            thumbImage.setImage(ImageLocation.getForObject(bigThumb, message.photoThumbsObject), "20_20", ImageLocation.getForObject(smallThumb, message.photoThumbsObject), "20_20", size, null, message, 0);
                                        } else {
                                            thumbImage.setImage(null, null, ImageLocation.getForObject(smallThumb, message.photoThumbsObject), "20_20", (Drawable) null, message, 0);
                                        }
                                        needEmoji = false;
                                    }
                                }
                            }
                            if (chat != null && chat.id > 0 && fromChat == null && (!ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat))) {
                                if (message.isOutOwner()) {
                                    messageNameString = LocaleController.getString("FromYou", R.string.FromYou);
                                } else if (message != null && message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.from_name != null) {
                                    messageNameString = message.messageOwner.fwd_from.from_name;
                                } else if (fromUser != null) {
                                    if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                                        if (UserObject.isDeleted(fromUser)) {
                                            messageNameString = LocaleController.getString("HiddenName", R.string.HiddenName);
                                        } else {
                                            messageNameString = ContactsController.formatName(fromUser.first_name, fromUser.last_name).replace("\n", "");
                                        }
                                    } else {
                                        messageNameString = UserObject.getFirstName(fromUser).replace("\n", "");
                                    }
                                } else {
                                    messageNameString = "DELETED";
                                }
                                checkMessage = false;
                                SpannableStringBuilder stringBuilder;
                                if (!TextUtils.isEmpty(restrictionReason)) {
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, restrictionReason, messageNameString));
                                } else if (message.caption != null) {
                                    String mess = message.caption.toString();
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    String emoji;
                                    if (!needEmoji) {
                                        emoji = "";
                                    } else if (message.isVideo()) {
                                        emoji = "\uD83D\uDCF9 ";
                                    } else if (message.isVoice()) {
                                        emoji = "\uD83C\uDFA4 ";
                                    } else if (message.isMusic()) {
                                        emoji = "\uD83C\uDFA7 ";
                                    } else if (message.isPhoto()) {
                                        emoji = "\uD83D\uDDBC ";
                                    } else {
                                        emoji = "\uD83D\uDCCE ";
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, emoji + mess.replace('\n', ' '), messageNameString));
                                } else if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                    currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                                    String innerMessage;
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.messageOwner.media;
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83D\uDCCA \u2068%s\u2069", mediaPoll.poll.question);
                                        } else {
                                            innerMessage = String.format("\uD83D\uDCCA %s", mediaPoll.poll.question);
                                        }
                                    } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83C\uDFAE \u2068%s\u2069", message.messageOwner.media.game.title);
                                        } else {
                                            innerMessage = String.format("\uD83C\uDFAE %s", message.messageOwner.media.game.title);
                                        }
                                    } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                                        innerMessage = message.messageOwner.media.title;
                                    } else if (message.type == 14) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83C\uDFA7 \u2068%s - %s\u2069", message.getMusicAuthor(), message.getMusicTitle());
                                        } else {
                                            innerMessage = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                                        }
                                    } else {
                                        innerMessage = message.messageText.toString();
                                    }
                                    innerMessage = innerMessage.replace('\n', ' ');
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, innerMessage, messageNameString));
                                    try {
                                        stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_attachMessage, resourcesProvider), hasNameInMessage ? messageNameString.length() + 2 : 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                } else if (message.messageOwner.message != null) {
                                    String mess = message.messageOwner.message;
                                    if (message.hasHighlightedWords()) {
                                        if (message.messageTrimmedToHighlight != null) {
                                            mess = message.messageTrimmedToHighlight;
                                        }
                                        int w = getMeasuredWidth() - AndroidUtilities.dp(72 + 23 + 10);
                                        if (hasNameInMessage) {
                                            if (!TextUtils.isEmpty(messageNameString)) {
                                                w -= currentMessagePaint.measureText(messageNameString.toString());
                                            }
                                            w -= currentMessagePaint.measureText(": ");
                                        }
                                        if (w > 0) {
                                            mess = AndroidUtilities.ellipsizeCenterEnd(mess, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                                        }
                                    } else {
                                        if (mess.length() > 150) {
                                            mess = mess.substring(0, 150);
                                        }
                                        mess = mess.replace('\n', ' ').trim();
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess, messageNameString));
                                } else {
                                    stringBuilder = SpannableStringBuilder.valueOf("");
                                }
                                int thumbInsertIndex = 0;
                                if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || currentDialogFolderId != 0 && stringBuilder.length() > 0) {
                                    try {
                                        stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_nameMessage, resourcesProvider), 0, thumbInsertIndex = messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        offsetName = thumbInsertIndex;
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                                messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), AndroidUtilities.dp(20), false);
                                if (message.hasHighlightedWords()) {
                                    CharSequence messageH = AndroidUtilities.highlightText(messageString, message.highlightedWords, resourcesProvider);
                                    if (messageH != null) {
                                        messageString = messageH;
                                    }
                                }
                                if (hasMessageThumb) {
                                    if (!(messageString instanceof SpannableStringBuilder)) {
                                        messageString = new SpannableStringBuilder(messageString);
                                    }
                                    checkMessage = false;
                                    SpannableStringBuilder builder = (SpannableStringBuilder) messageString;
                                    builder.insert(thumbInsertIndex, " ");
                                    builder.setSpan(new FixedWidthSpan(AndroidUtilities.dp(thumbSize + 6)), thumbInsertIndex, thumbInsertIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            } else {
                                if (!TextUtils.isEmpty(restrictionReason)) {
                                    messageString = restrictionReason;
                                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString("AttachPhotoExpired", R.string.AttachPhotoExpired);
                                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && message.messageOwner.media.document instanceof TLRPC.TL_documentEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString("AttachVideoExpired", R.string.AttachVideoExpired);
                                } else if (message.caption != null) {
                                    String emoji;
                                    if (!needEmoji) {
                                        emoji = "";
                                    } else if (message.isVideo()) {
                                        emoji = "\uD83D\uDCF9 ";
                                    } else if (message.isVoice()) {
                                        emoji = "\uD83C\uDFA4 ";
                                    } else if (message.isMusic()) {
                                        emoji = "\uD83C\uDFA7 ";
                                    } else if (message.isPhoto()) {
                                        emoji = "\uD83D\uDDBC ";
                                    } else {
                                        emoji = "\uD83D\uDCCE ";
                                    }
                                    if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)) {
                                        String str = message.messageTrimmedToHighlight;
                                        if (message.messageTrimmedToHighlight != null) {
                                            str = message.messageTrimmedToHighlight;
                                        }
                                        int w = getMeasuredWidth() - AndroidUtilities.dp(72 + 23 + 24);
                                        if (hasNameInMessage) {
                                            if (!TextUtils.isEmpty(messageNameString)) {
                                                w -= currentMessagePaint.measureText(messageNameString.toString());
                                            }
                                            w -= currentMessagePaint.measureText(": ");
                                        }
                                        if (w > 0) {
                                            str = AndroidUtilities.ellipsizeCenterEnd(str, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                                        }
                                        messageString = emoji + str;
                                    } else {
                                        messageString = emoji + message.caption;
                                    }
                                } else {
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.messageOwner.media;
                                        messageString = "\uD83D\uDCCA " + mediaPoll.poll.question;
                                    } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        messageString = "\uD83C\uDFAE " + message.messageOwner.media.game.title;
                                    } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                                        messageString = message.messageOwner.media.title;
                                    } else if (message.type == 14) {
                                        messageString = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                                    } else {
                                        if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)){
                                            messageString = message.messageTrimmedToHighlight;
                                            if (message.messageTrimmedToHighlight != null) {
                                                messageString = message.messageTrimmedToHighlight;
                                            }
                                            int w = getMeasuredWidth() - AndroidUtilities.dp(72 + 23 );
                                            messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                                        } else {
                                            messageString = message.messageText;
                                        }
                                        AndroidUtilities.highlightText(messageString, message.highlightedWords, resourcesProvider);
                                    }
                                    if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                        currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex];
                                    }
                                }
                                if (hasMessageThumb) {
                                    if (message.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)) {
                                        messageString = message.messageTrimmedToHighlight;
                                        if (message.messageTrimmedToHighlight != null) {
                                            messageString = message.messageTrimmedToHighlight;
                                        }
                                        int w = getMeasuredWidth() - AndroidUtilities.dp(72 + 23 + thumbSize + 6);
                                        messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message.highlightedWords.get(0), w, currentMessagePaint, 130).toString();
                                    } else {
                                        if (messageString.length() > 150) {
                                            messageString = messageString.subSequence(0, 150);
                                        }
                                        messageString = AndroidUtilities.replaceNewLines(messageString);
                                    }
                                    if (!(messageString instanceof SpannableStringBuilder)) {
                                        messageString = new SpannableStringBuilder(messageString);
                                    }
                                    checkMessage = false;
                                    SpannableStringBuilder builder = (SpannableStringBuilder) messageString;
                                    builder.insert(0, " ");
                                    builder.setSpan(new FixedWidthSpan(AndroidUtilities.dp(thumbSize + 6)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), AndroidUtilities.dp(17), false);
                                    if (message.hasHighlightedWords()) {
                                        CharSequence s = AndroidUtilities.highlightText(builder, message.highlightedWords, resourcesProvider);
                                        if (s != null) {
                                            messageString = s;
                                        }
                                    }
                                }
                            }
                        }
                        if (currentDialogFolderId != 0) {
                            messageNameString = formatArchivedDialogNames();
                        }
                    }
                }
            }

            if (draftMessage != null) {
                timeString = LocaleController.stringForMessageListDate(draftMessage.date);
            } else if (lastMessageDate != 0) {
                timeString = LocaleController.stringForMessageListDate(lastMessageDate);
            } else if (message != null) {
                timeString = LocaleController.stringForMessageListDate(message.messageOwner.date);
            }

            if (message == null) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = false;
                drawCount = false;
                drawMention = false;
                drawError = false;
            } else {
                if (currentDialogFolderId != 0) {
                    if (unreadCount + mentionCount > 0) {
                        if (unreadCount > mentionCount) {
                            drawCount = true;
                            drawMention = false;
                            countString = String.format("%d", unreadCount + mentionCount);
                        } else {
                            drawCount = false;
                            drawMention = true;
                            mentionString = String.format("%d", unreadCount + mentionCount);
                        }
                    } else {
                        drawCount = false;
                        drawMention = false;
                    }
                } else {
                    if (clearingDialog) {
                        drawCount = false;
                        showChecks = false;
                    } else if (unreadCount != 0 && (unreadCount != 1 || unreadCount != mentionCount || message == null || !message.messageOwner.mentioned)) {
                        drawCount = true;
                        countString = String.format("%d", unreadCount);
                    } else if (markUnread) {
                        drawCount = true;
                        countString = "";
                    } else {
                        drawCount = false;
                    }
                    if (mentionCount != 0) {
                        drawMention = true;
                        mentionString = "@";
                    } else {
                        drawMention = false;
                    }
                }

                if (message.isOut() && draftMessage == null && showChecks && !(message.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear)) {
                    if (message.isSending()) {
                        drawCheck1 = false;
                        drawCheck2 = false;
                        drawClock = true;
                        drawError = false;
                    } else if (message.isSendError()) {
                        drawCheck1 = false;
                        drawCheck2 = false;
                        drawClock = false;
                        drawError = true;
                        drawCount = false;
                        drawMention = false;
                    } else if (message.isSent()) {
                        drawCheck1 = !message.isUnread() || ChatObject.isChannel(chat) && !chat.megagroup;
                        drawCheck2 = true;
                        drawClock = false;
                        drawError = false;
                    }
                } else {
                    drawCheck1 = false;
                    drawCheck2 = false;
                    drawClock = false;
                    drawError = false;
                }
            }

            promoDialog = false;
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            if (dialogsType == 0 && messagesController.isPromoDialog(currentDialogId, true)) {
                drawPinBackground = true;
                promoDialog = true;
                if (messagesController.promoDialogType == MessagesController.PROMO_TYPE_PROXY) {
                    timeString = LocaleController.getString("UseProxySponsor", R.string.UseProxySponsor);
                } else if (messagesController.promoDialogType == MessagesController.PROMO_TYPE_PSA) {
                    timeString = LocaleController.getString("PsaType_" + messagesController.promoPsaType);
                    if (TextUtils.isEmpty(timeString)) {
                        timeString = LocaleController.getString("PsaTypeDefault", R.string.PsaTypeDefault);
                    }
                    if (!TextUtils.isEmpty(messagesController.promoPsaMessage)) {
                        messageString = messagesController.promoPsaMessage;
                        hasMessageThumb = false;
                    }
                }
            }

            if (currentDialogFolderId != 0) {
                nameString = LocaleController.getString("ArchivedChats", R.string.ArchivedChats);
            } else {
                if (chat != null) {
                    nameString = chat.title;
                } else if (user != null) {
                    if (UserObject.isReplyUser(user)) {
                        nameString = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                    } else if (UserObject.isUserSelf(user)) {
                        if (useMeForMyMessages) {
                            nameString = LocaleController.getString("FromYou", R.string.FromYou);
                        } else {
                            if (dialogsType == 3) {
                                drawPinBackground = true;
                            }
                            nameString = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                        }
                    } else {
                        nameString = UserObject.getUserName(user);
                    }
                }
                if (nameString.length() == 0) {
                    nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
                }
            }
        }

        int timeWidth;
        if (drawTime) {
            timeWidth = (int) Math.ceil(Theme.dialogs_timePaint.measureText(timeString));
            timeLayout = new StaticLayout(timeString, Theme.dialogs_timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!LocaleController.isRTL) {
                timeLeft = getMeasuredWidth() - AndroidUtilities.dp(15) - timeWidth;
            } else {
                timeLeft = AndroidUtilities.dp(15);
            }
        } else {
            timeWidth = 0;
            timeLayout = null;
            timeLeft = 0;
        }

        int nameWidth;

        if (!LocaleController.isRTL) {
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14) - timeWidth;
        } else {
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(77) - timeWidth;
            nameLeft += timeWidth;
        }
        if (drawNameLock) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        } else if (drawNameGroup) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_groupDrawable.getIntrinsicWidth();
        } else if (drawNameBroadcast) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_broadcastDrawable.getIntrinsicWidth();
        } else if (drawNameBot) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_botDrawable.getIntrinsicWidth();
        }
        if (drawClock) {
            int w = Theme.dialogs_clockDrawable.getIntrinsicWidth() + AndroidUtilities.dp(5);
            nameWidth -= w;
            if (!LocaleController.isRTL) {
                clockDrawLeft = timeLeft - w;
            } else {
                clockDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5);
                nameLeft += w;
            }
        } else if (drawCheck2) {
            int w = Theme.dialogs_checkDrawable.getIntrinsicWidth() + AndroidUtilities.dp(5);
            nameWidth -= w;
            if (drawCheck1) {
                nameWidth -= Theme.dialogs_halfCheckDrawable.getIntrinsicWidth() - AndroidUtilities.dp(8);
                if (!LocaleController.isRTL) {
                    halfCheckDrawLeft = timeLeft - w;
                    checkDrawLeft = halfCheckDrawLeft - AndroidUtilities.dp(5.5f);
                } else {
                    checkDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5);
                    halfCheckDrawLeft = checkDrawLeft + AndroidUtilities.dp(5.5f);
                    nameLeft += w + Theme.dialogs_halfCheckDrawable.getIntrinsicWidth() - AndroidUtilities.dp(8);
                }
            } else {
                if (!LocaleController.isRTL) {
                    checkDrawLeft1 = timeLeft - w;
                } else {
                    checkDrawLeft1 = timeLeft + timeWidth + AndroidUtilities.dp(5);
                    nameLeft += w;
                }
            }
        }

        if (dialogMuted && !drawVerified && drawScam == 0) {
            int w = AndroidUtilities.dp(6) + Theme.dialogs_muteDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawVerified) {
            int w = AndroidUtilities.dp(6) + Theme.dialogs_verifiedDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawScam != 0) {
            int w = AndroidUtilities.dp(6) + (drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable).getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        }

        nameWidth = Math.max(AndroidUtilities.dp(12), nameWidth);
        try {
            CharSequence nameStringFinal = TextUtils.ellipsize(nameString.replace('\n', ' '), Theme.dialogs_namePaint[paintIndex], nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            if (message != null && message.hasHighlightedWords()) {
                CharSequence s = AndroidUtilities.highlightText(nameStringFinal, message.highlightedWords, resourcesProvider);
                if (s != null) {
                    nameStringFinal = s;
                }
            }
            nameLayout = new StaticLayout(nameStringFinal, Theme.dialogs_namePaint[paintIndex], nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        int messageWidth;
        int avatarLeft;
        int avatarTop;
        int thumbLeft;
        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
            avatarTop = AndroidUtilities.dp(11);
            messageNameTop = AndroidUtilities.dp(32);
            timeTop = AndroidUtilities.dp(13);
            errorTop = AndroidUtilities.dp(43);
            pinTop = AndroidUtilities.dp(43);
            countTop = AndroidUtilities.dp(43);
            checkDrawTop = AndroidUtilities.dp(13);
            messageWidth = getMeasuredWidth() - AndroidUtilities.dp(72 + 21);

            if (LocaleController.isRTL) {
                messageLeft = messageNameLeft = AndroidUtilities.dp(16);
                avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(66);
                thumbLeft = avatarLeft - AndroidUtilities.dp(13 + 18);
            } else {
                messageLeft = messageNameLeft = AndroidUtilities.dp(72 + 6);
                avatarLeft = AndroidUtilities.dp(10);
                thumbLeft = avatarLeft + AndroidUtilities.dp(56 + 13);
            }
            avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            thumbImage.setImageCoords(thumbLeft, avatarTop + AndroidUtilities.dp(31), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
        } else {
            avatarTop = AndroidUtilities.dp(9);
            messageNameTop = AndroidUtilities.dp(31);
            timeTop = AndroidUtilities.dp(16);
            errorTop = AndroidUtilities.dp(39);
            pinTop = AndroidUtilities.dp(39);
            countTop = AndroidUtilities.dp(39);
            checkDrawTop = AndroidUtilities.dp(17);
            messageWidth = getMeasuredWidth() - AndroidUtilities.dp(72 + 23);

            if (LocaleController.isRTL) {
                messageLeft = messageNameLeft = AndroidUtilities.dp(22);
                avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(64);
                thumbLeft = avatarLeft - AndroidUtilities.dp(11 + thumbSize);
            } else {
                messageLeft = messageNameLeft = AndroidUtilities.dp(72 + 4);
                avatarLeft = AndroidUtilities.dp(10);
                thumbLeft = avatarLeft + AndroidUtilities.dp(56 + 11);
            }
            avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(54), AndroidUtilities.dp(54));
            thumbImage.setImageCoords(thumbLeft, avatarTop + AndroidUtilities.dp(30), AndroidUtilities.dp(thumbSize), AndroidUtilities.dp(thumbSize));
        }
        if (drawPin) {
            if (!LocaleController.isRTL) {
                pinLeft = getMeasuredWidth() - Theme.dialogs_pinnedDrawable.getIntrinsicWidth() - AndroidUtilities.dp(14);
            } else {
                pinLeft = AndroidUtilities.dp(14);
            }
        }
        if (drawError) {
            int w = AndroidUtilities.dp(23 + 8);
            messageWidth -= w;
            if (!LocaleController.isRTL) {
                errorLeft = getMeasuredWidth() - AndroidUtilities.dp(23 + 11);
            } else {
                errorLeft = AndroidUtilities.dp(11);
                messageLeft += w;
                messageNameLeft += w;
            }
        } else if (countString != null || mentionString != null) {
            if (countString != null) {
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + AndroidUtilities.dp(18);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(20);
                } else {
                    countLeft = AndroidUtilities.dp(20);
                    messageLeft += w;
                    messageNameLeft += w;
                }
                drawCount = true;
            } else {
                countWidth = 0;
            }
            if (mentionString != null) {
                if (currentDialogFolderId != 0) {
                    mentionWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(mentionString)));
                    mentionLayout = new StaticLayout(mentionString, Theme.dialogs_countTextPaint, mentionWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                } else {
                    mentionWidth = AndroidUtilities.dp(12);
                }
                int w = mentionWidth + AndroidUtilities.dp(18);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    mentionLeft = getMeasuredWidth() - mentionWidth - AndroidUtilities.dp(20) - (countWidth != 0 ? countWidth + AndroidUtilities.dp(18) : 0);
                } else {
                    mentionLeft = AndroidUtilities.dp(20) + (countWidth != 0 ? countWidth + AndroidUtilities.dp(18) : 0);
                    messageLeft += w;
                    messageNameLeft += w;
                }
                drawMention = true;
            }
        } else {
            if (drawPin) {
                int w = Theme.dialogs_pinnedDrawable.getIntrinsicWidth() + AndroidUtilities.dp(8);
                messageWidth -= w;
                if (LocaleController.isRTL) {
                    messageLeft += w;
                    messageNameLeft += w;
                }
            }
            drawCount = false;
            drawMention = false;
        }

        if (checkMessage) {
            if (messageString == null) {
                messageString = "";
            }
            String mess = messageString.toString();
            if (mess.length() > 150) {
                mess = mess.substring(0, 150);
            }
            if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || messageNameString != null) {
                mess = mess.replace('\n', ' ');
            } else {
                mess = mess.replace("\n\n", "\n");
            }
            messageString = Emoji.replaceEmoji(mess, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), AndroidUtilities.dp(17), false);
            if (message != null) {
                CharSequence s = AndroidUtilities.highlightText(messageString, message.highlightedWords, resourcesProvider);
                if (s != null) {
                    messageString = s;
                }
            }
        }
        messageWidth = Math.max(AndroidUtilities.dp(12), messageWidth);
        if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && messageNameString != null && (currentDialogFolderId == 0 || currentDialogFolderDialogsCount == 1)) {
            try {
                if (message != null && message.hasHighlightedWords()) {
                    CharSequence s = AndroidUtilities.highlightText(messageNameString, message.highlightedWords, resourcesProvider);
                    if (s != null) {
                        messageNameString = s;
                    }
                }
                messageNameLayout = StaticLayoutEx.createStaticLayout(messageNameString, Theme.dialogs_messageNamePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false, TextUtils.TruncateAt.END, messageWidth, 1);
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageTop = AndroidUtilities.dp(32 + 19);
            thumbImage.setImageY(avatarTop + AndroidUtilities.dp(40));
        } else {
            messageNameLayout = null;
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                messageTop = AndroidUtilities.dp(32);
                thumbImage.setImageY(avatarTop + AndroidUtilities.dp(21));
            } else {
                messageTop = AndroidUtilities.dp(39);
            }
        }

        try {
            CharSequence messageStringFinal;
            if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && currentDialogFolderId != 0 && currentDialogFolderDialogsCount > 1) {
                messageStringFinal = messageNameString;
                messageNameString = null;
                currentMessagePaint = Theme.dialogs_messagePaint[paintIndex];
            } else if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || messageNameString != null) {
                messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            } else {
                messageStringFinal = messageString;
            }
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                if (hasMessageThumb && messageNameString != null) {
                    messageWidth += AndroidUtilities.dp(6);
                }
                messageLayout = StaticLayoutEx.createStaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, messageWidth, messageNameString != null ? 1 : 2);
            } else {
                if (hasMessageThumb) {
                    messageWidth += thumbSize + AndroidUtilities.dp(6);
                    if (LocaleController.isRTL) {
                        messageLeft -= thumbSize + AndroidUtilities.dp(6);
                    }
                }
                messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        } catch (Exception e) {
            messageLayout = null;
            FileLog.e(e);
        }

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                widthpx = Math.ceil(nameLayout.getLineWidth(0));
                if (dialogMuted && !drawVerified && drawScam == 0) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                } else if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                } else if (drawScam != 0) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - (drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable).getIntrinsicWidth());
                }
                if (left == 0) {
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (messageLayout != null) {
                int lineCount = messageLayout.getLineCount();
                if (lineCount > 0) {
                    int w = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = messageLayout.getLineLeft(a);
                        if (left == 0) {
                            widthpx = Math.ceil(messageLayout.getLineWidth(a));
                            w = Math.min(w, (int) (messageWidth - widthpx));
                        } else {
                            w = 0;
                            break;
                        }
                    }
                    if (w != Integer.MAX_VALUE) {
                        messageLeft += w;
                    }
                }
            }
            if (messageNameLayout != null && messageNameLayout.getLineCount() > 0) {
                left = messageNameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(messageNameLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageNameLeft += (messageWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
                if (dialogMuted || drawVerified || drawScam != 0) {
                    nameMuteLeft = (int) (nameLeft + left + AndroidUtilities.dp(6));
                }
            }
            if (messageLayout != null) {
                int lineCount = messageLayout.getLineCount();
                if (lineCount > 0) {
                    left = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = Math.min(left, messageLayout.getLineLeft(a));
                    }
                    messageLeft -= left;
                }
            }
            if (messageNameLayout != null && messageNameLayout.getLineCount() > 0) {
                messageNameLeft -= messageNameLayout.getLineLeft(0);
            }
        }
        if (messageLayout != null && hasMessageThumb) {
            try {
                int textLen = messageLayout.getText().length();
                if (offsetName >= textLen) {
                    offsetName = textLen - 1;
                }
                float x1 = messageLayout.getPrimaryHorizontal(offsetName);
                float x2 = messageLayout.getPrimaryHorizontal(offsetName + 1);
                int offset = (int) Math.ceil(Math.min(x1, x2));
                if (offset != 0) {
                    offset += AndroidUtilities.dp(3);
                }
                thumbImage.setImageX(messageLeft + offset);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (messageLayout != null && printingStringType >= 0) {
            float x1, x2;
            if (printigStingReplaceIndex >= 0 && printigStingReplaceIndex + 1 < messageLayout.getText().length() ){
                x1 = messageLayout.getPrimaryHorizontal(printigStingReplaceIndex);
                x2 = messageLayout.getPrimaryHorizontal(printigStingReplaceIndex + 1);
            } else {
                x1 = messageLayout.getPrimaryHorizontal(0);
                x2 = messageLayout.getPrimaryHorizontal(1);
            }
            if (x1 < x2) {
                statusDrawableLeft = (int) (messageLeft + x1);
            } else {
                statusDrawableLeft = (int) (messageLeft + x2 + AndroidUtilities.dp(3));
            }
        }
    }

    private void drawCheckStatus(Canvas canvas, boolean drawClock, boolean drawCheck1, boolean drawCheck2, boolean moveCheck,  float alpha) {
        if (alpha == 0 && !moveCheck) {
            return;
        }
        float scale = 0.5f + 0.5f * alpha;
        if (drawClock) {
            setDrawableBounds(Theme.dialogs_clockDrawable, clockDrawLeft, checkDrawTop);
            if (alpha != 1f) {
                canvas.save();
                canvas.scale(scale, scale, Theme.dialogs_clockDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                Theme.dialogs_clockDrawable.setAlpha((int) (255 * alpha));
            }
            Theme.dialogs_clockDrawable.draw(canvas);
            if (alpha != 1f) {
                canvas.restore();
                Theme.dialogs_clockDrawable.setAlpha(255);
            }
            invalidate();
        } else if (drawCheck2) {
            if (drawCheck1) {
                setDrawableBounds(Theme.dialogs_halfCheckDrawable, halfCheckDrawLeft, checkDrawTop);
                if (moveCheck) {
                    canvas.save();
                    canvas.scale(scale, scale, Theme.dialogs_halfCheckDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                    Theme.dialogs_halfCheckDrawable.setAlpha((int) (255 * alpha));
                }
                if (!moveCheck && alpha != 0) {
                    canvas.save();
                    canvas.scale(scale, scale, Theme.dialogs_halfCheckDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                    Theme.dialogs_halfCheckDrawable.setAlpha((int) (255 * alpha));
                    Theme.dialogs_checkReadDrawable.setAlpha((int) (255 * alpha));
                }

                Theme.dialogs_halfCheckDrawable.draw(canvas);

                if (moveCheck) {
                    canvas.restore();
                    canvas.save();
                    canvas.translate(AndroidUtilities.dp(4) * (1f - alpha), 0);
                }
                setDrawableBounds(Theme.dialogs_checkReadDrawable, checkDrawLeft, checkDrawTop);
                Theme.dialogs_checkReadDrawable.draw(canvas);
                if (moveCheck) {
                    canvas.restore();
                    Theme.dialogs_halfCheckDrawable.setAlpha(255);
                }

                if (!moveCheck && alpha != 0) {
                    canvas.restore();
                    Theme.dialogs_halfCheckDrawable.setAlpha(255);
                    Theme.dialogs_checkReadDrawable.setAlpha(255);
                }
            } else {
                setDrawableBounds(Theme.dialogs_checkDrawable, checkDrawLeft1, checkDrawTop);
                if (alpha != 1f) {
                    canvas.save();
                    canvas.scale(scale, scale, Theme.dialogs_checkDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                    Theme.dialogs_checkDrawable.setAlpha((int) (255 * alpha));
                }
                Theme.dialogs_checkDrawable.draw(canvas);
                if (alpha != 1f) {
                    canvas.restore();
                    Theme.dialogs_checkDrawable.setAlpha(255);
                }
            }
        }
    }

    public boolean isPointInsideAvatar(float x, float y) {
        if (!LocaleController.isRTL) {
            return x >= 0 && x < AndroidUtilities.dp(60);
        } else {
            return x >= getMeasuredWidth() - AndroidUtilities.dp(60) && x < getMeasuredWidth();
        }
    }

    public void setDialogSelected(boolean value) {
        if (isSelected != value) {
            invalidate();
        }
        isSelected = value;
    }

    public void checkCurrentDialogIndex(boolean frozen) {
        if (parentFragment == null) {
            return;
        }
        ArrayList<TLRPC.Dialog> dialogsArray = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, frozen);
        if (index < dialogsArray.size()) {
            TLRPC.Dialog dialog = dialogsArray.get(index);
            TLRPC.Dialog nextDialog = index + 1 < dialogsArray.size() ? dialogsArray.get(index + 1) : null;
            TLRPC.DraftMessage newDraftMessage = MediaDataController.getInstance(currentAccount).getDraft(currentDialogId, 0);
            MessageObject newMessageObject;
            if (currentDialogFolderId != 0) {
                newMessageObject = findFolderTopMessage();
            } else {
                newMessageObject = MessagesController.getInstance(currentAccount).dialogMessage.get(dialog.id);
            }
            if (currentDialogId != dialog.id ||
                    message != null && message.getId() != dialog.top_message ||
                    newMessageObject != null && newMessageObject.messageOwner.edit_date != currentEditDate ||
                    unreadCount != dialog.unread_count ||
                    mentionCount != dialog.unread_mentions_count ||
                    markUnread != dialog.unread_mark ||
                    message != newMessageObject ||
                    newDraftMessage != draftMessage || drawPin != dialog.pinned) {
                boolean dialogChanged = currentDialogId != dialog.id;

                currentDialogId = dialog.id;
                if (dialogChanged) {
                    lastDialogChangedTime = System.currentTimeMillis();
                    if (statusDrawableAnimator != null) {
                        statusDrawableAnimator.removeAllListeners();
                        statusDrawableAnimator.cancel();
                    }
                    statusDrawableAnimationInProgress = false;
                    lastStatusDrawableParams = -1;
                }
                if (dialog instanceof TLRPC.TL_dialogFolder) {
                    TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
                    currentDialogFolderId = dialogFolder.folder.id;
                } else {
                    currentDialogFolderId = 0;
                }
                if (dialogsType == 7 || dialogsType == 8) {
                    MessagesController.DialogFilter filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[dialogsType == 8 ? 1 : 0];
                    fullSeparator = dialog instanceof TLRPC.TL_dialog && nextDialog != null && filter != null && filter.pinnedDialogs.indexOfKey(dialog.id) >= 0 && filter.pinnedDialogs.indexOfKey(nextDialog.id) < 0;
                    fullSeparator2 = false;
                } else {
                    fullSeparator = dialog instanceof TLRPC.TL_dialog && dialog.pinned && nextDialog != null && !nextDialog.pinned;
                    fullSeparator2 = dialog instanceof TLRPC.TL_dialogFolder && nextDialog != null && !nextDialog.pinned;
                }
                update(0, !dialogChanged);
                if (dialogChanged) {
                    reorderIconProgress = drawPin && drawReorder ? 1.0f : 0.0f;
                }
                checkOnline();
                checkGroupCall();
                checkChatTheme();
            }
        }
    }

    public void animateArchiveAvatar() {
        if (avatarDrawable.getAvatarType() != AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
            return;
        }
        animatingArchiveAvatar = true;
        animatingArchiveAvatarProgress = 0.0f;
        Theme.dialogs_archiveAvatarDrawable.setProgress(0.0f);
        Theme.dialogs_archiveAvatarDrawable.start();
        invalidate();
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null) {
            return;
        }
        checkBox.setChecked(checked, animated);
    }

    private MessageObject findFolderTopMessage() {
        if (parentFragment == null) {
            return null;
        }
        ArrayList<TLRPC.Dialog> dialogs = parentFragment.getDialogsArray(currentAccount, dialogsType, currentDialogFolderId, false);
        MessageObject maxMessage = null;
        if (!dialogs.isEmpty()) {
            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                MessageObject object = MessagesController.getInstance(currentAccount).dialogMessage.get(dialog.id);
                if (object != null && (maxMessage == null || object.messageOwner.date > maxMessage.messageOwner.date)) {
                    maxMessage = object;
                }
                if (dialog.pinnedNum == 0 && maxMessage != null) {
                    break;
                }
            }
        }
        return maxMessage;
    }

    public boolean isFolderCell() {
        return currentDialogFolderId != 0;
    }

    public void update(int mask) {
        update(mask, true);
    }

    public void update(int mask, boolean animated) {
        if (customDialog != null) {
            lastMessageDate = customDialog.date;
            lastUnreadState = customDialog.unread_count != 0;
            unreadCount = customDialog.unread_count;
            drawPin = customDialog.pinned;
            dialogMuted = customDialog.muted;
            avatarDrawable.setInfo(customDialog.id, customDialog.name, null);
            avatarImage.setImage(null, "50_50", avatarDrawable, null, 0);
            thumbImage.setImageBitmap((BitmapDrawable) null);
        } else {
            int oldUnreadCount = unreadCount;
            boolean oldMarkUnread = markUnread;
            if (isDialogCell) {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(currentDialogId);
                if (dialog != null) {
                    if (mask == 0) {
                        clearingDialog = MessagesController.getInstance(currentAccount).isClearingDialog(dialog.id);
                        message = MessagesController.getInstance(currentAccount).dialogMessage.get(dialog.id);
                        lastUnreadState = message != null && message.isUnread();
                        if (dialog instanceof TLRPC.TL_dialogFolder) {
                            unreadCount = MessagesStorage.getInstance(currentAccount).getArchiveUnreadCount();
                            mentionCount = 0;
                        } else {
                            unreadCount = dialog.unread_count;
                            mentionCount = dialog.unread_mentions_count;
                        }
                        markUnread = dialog.unread_mark;
                        currentEditDate = message != null ? message.messageOwner.edit_date : 0;
                        lastMessageDate = dialog.last_message_date;
                        if (dialogsType == 7 || dialogsType == 8) {
                            MessagesController.DialogFilter filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[dialogsType == 8 ? 1 : 0];
                            drawPin = filter != null && filter.pinnedDialogs.indexOfKey(dialog.id) >= 0;
                        } else {
                            drawPin = currentDialogFolderId == 0 && dialog.pinned;
                        }
                        if (message != null) {
                            lastSendState = message.messageOwner.send_state;
                        }
                    }
                } else {
                    unreadCount = 0;
                    mentionCount = 0;
                    currentEditDate = 0;
                    lastMessageDate = 0;
                    clearingDialog = false;
                }
            } else {
                drawPin = false;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if (user != null && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    user = MessagesController.getInstance(currentAccount).getUser(user.id);
                    invalidate();
                }
                if (isDialogCell) {
                    if ((mask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                        CharSequence printString = MessagesController.getInstance(currentAccount).getPrintingString(currentDialogId, 0, true);
                        if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && !lastPrintString.equals(printString)) {
                            continueUpdate = true;
                        }
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_MESSAGE_TEXT) != 0) {
                    if (message != null && message.messageText != lastMessageString) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_CHAT) != 0 && chat != null) {
                    TLRPC.Chat newChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                    if ((newChat.call_active && newChat.call_not_empty) != hasCall) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (chat == null) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    if (chat == null) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0) {
                    if (user == null) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                    if (user == null) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0) {
                    if (message != null && lastUnreadState != message.isUnread()) {
                        lastUnreadState = message.isUnread();
                        continueUpdate = true;
                    }
                    if (isDialogCell) {
                        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(currentDialogId);
                        int newCount;
                        int newMentionCount;
                        if (dialog instanceof TLRPC.TL_dialogFolder) {
                            newCount = MessagesStorage.getInstance(currentAccount).getArchiveUnreadCount();
                            newMentionCount = 0;
                        } else if (dialog != null) {
                            newCount = dialog.unread_count;
                            newMentionCount = dialog.unread_mentions_count;
                        } else {
                            newCount = 0;
                            newMentionCount = 0;
                        }
                        if (dialog != null && (unreadCount != newCount || markUnread != dialog.unread_mark || mentionCount != newMentionCount)) {
                            unreadCount = newCount;
                            mentionCount = newMentionCount;
                            markUnread = dialog.unread_mark;
                            continueUpdate = true;
                        }
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_SEND_STATE) != 0) {
                    if (message != null && lastSendState != message.messageOwner.send_state) {
                        lastSendState = message.messageOwner.send_state;
                        continueUpdate = true;
                    }
                }

                if (!continueUpdate) {
                    invalidate();
                    return;
                }
            }

            user = null;
            chat = null;
            encryptedChat = null;

            long dialogId;
            if (currentDialogFolderId != 0) {
                dialogMuted = false;
                message = findFolderTopMessage();
                if (message != null) {
                    dialogId = message.getDialogId();
                } else {
                    dialogId = 0;
                }
            } else {
                dialogMuted = isDialogCell && MessagesController.getInstance(currentAccount).isDialogMuted(currentDialogId);
                dialogId = currentDialogId;
            }

            if (dialogId != 0) {
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                    if (encryptedChat != null) {
                        user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                    }
                } else if (DialogObject.isUserDialog(dialogId)) {
                    user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                } else {
                    chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (!isDialogCell && chat != null && chat.migrated_to != null) {
                        TLRPC.Chat chat2 = MessagesController.getInstance(currentAccount).getChat(chat.migrated_to.channel_id);
                        if (chat2 != null) {
                            chat = chat2;
                        }
                    }
                }
                if (useMeForMyMessages && user != null && message.isOutOwner()) {
                    user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).clientUserId);
                }
            }

            if (currentDialogFolderId != 0) {
                Theme.dialogs_archiveAvatarDrawable.setCallback(this);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ARCHIVED);
                avatarImage.setImage(null, null, avatarDrawable, null, user, 0);
            } else {
                if (user != null) {
                    avatarDrawable.setInfo(user);
                    if (UserObject.isReplyUser(user)) {
                        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                        avatarImage.setImage(null, null, avatarDrawable, null, user, 0);
                    } else if (UserObject.isUserSelf(user) && !useMeForMyMessages) {
                        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                        avatarImage.setImage(null, null, avatarDrawable, null, user, 0);
                    } else {
                        avatarImage.setForUserOrChat(user, avatarDrawable);
                    }
                } else if (chat != null) {
                    avatarDrawable.setInfo(chat);
                    avatarImage.setForUserOrChat(chat, avatarDrawable);
                }
            }

            if (animated && (oldUnreadCount != unreadCount || oldMarkUnread != markUnread) && (System.currentTimeMillis() - lastDialogChangedTime) > 100) {
                if (countAnimator != null) {
                    countAnimator.cancel();
                }
                countAnimator = ValueAnimator.ofFloat(0, 1f);
                countAnimator.addUpdateListener(valueAnimator -> {
                    countChangeProgress = (float) valueAnimator.getAnimatedValue();
                    invalidate();
                });
                countAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        countChangeProgress = 1f;
                        countOldLayout = null;
                        countAnimationStableLayout = null;
                        countAnimationInLayout = null;
                        invalidate();
                    }
                });
                if ((oldUnreadCount == 0 || markUnread) && !(!markUnread && oldMarkUnread)) {
                    countAnimator.setDuration(220);
                    countAnimator.setInterpolator(new OvershootInterpolator());
                } else if (unreadCount == 0) {
                    countAnimator.setDuration(150);
                    countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                } else {
                    countAnimator.setDuration(430);
                    countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                }
                if (drawCount && countLayout != null) {
                    String oldStr = String.valueOf(oldUnreadCount);
                    String newStr = String.valueOf(unreadCount);

                    if (oldStr.length() == newStr.length()) {
                        SpannableStringBuilder oldSpannableStr = new SpannableStringBuilder(oldStr);
                        SpannableStringBuilder newSpannableStr = new SpannableStringBuilder(newStr);
                        SpannableStringBuilder stableStr = new SpannableStringBuilder(newStr);
                        for (int i = 0; i < oldStr.length(); i++) {
                            if (oldStr.charAt(i) == newStr.charAt(i)) {
                                oldSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                                newSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                            } else {
                                stableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                            }
                        }

                        int countOldWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(oldStr)));
                        countOldLayout = new StaticLayout(oldSpannableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        countAnimationStableLayout = new StaticLayout(stableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        countAnimationInLayout = new StaticLayout(newSpannableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    } else {
                        countOldLayout = countLayout;
                    }
                }
                countWidthOld = countWidth;
                countLeftOld = countLeft;
                countAnimationIncrement = unreadCount > oldUnreadCount;
                countAnimator.start();
            }
        }
        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }

        if (!animated) {
            dialogMutedProgress = dialogMuted ? 1f : 0f;
        }

        invalidate();
    }

    @Override
    public float getTranslationX() {
        return translationX;
    }

    @Override
    public void setTranslationX(float value) {
        translationX = (int) value;
        if (translationDrawable != null && translationX == 0) {
            translationDrawable.setProgress(0.0f);
            translationAnimationStarted = false;
            archiveHidden = SharedConfig.archiveHidden;
            currentRevealProgress = 0;
            isSliding = false;
        }
        if (translationX != 0) {
            isSliding = true;
        } else {
            currentRevealBounceProgress = 0f;
            currentRevealProgress = 0f;
            drawRevealBackground = false;
        }
        if (isSliding && !swipeCanceled) {
            boolean prevValue = drawRevealBackground;
            drawRevealBackground = Math.abs(translationX) >= getMeasuredWidth() * 0.45f;
            if (prevValue != drawRevealBackground && archiveHidden == SharedConfig.archiveHidden) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignore) {

                }
            }
        }
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        if (currentDialogId == 0 && customDialog == null) {
            return;
        }

        boolean needInvalidate = false;

        if (currentDialogFolderId != 0 && archivedChatsDrawable != null && archivedChatsDrawable.outProgress == 0.0f && translationX == 0.0f) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            archivedChatsDrawable.draw(canvas);
            canvas.restore();
            return;
        }

        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;

        if (clipProgress != 0.0f && Build.VERSION.SDK_INT != 24) {
            canvas.save();
            canvas.clipRect(0, topClip * clipProgress, getMeasuredWidth(), getMeasuredHeight() - (int) (bottomClip * clipProgress));
        }

        if (translationX != 0 || cornerProgress != 0.0f) {
            canvas.save();
            String swipeMessage;
            int backgroundColor;
            int revealBackgroundColor;
            int swipeMessageStringId;
            if (currentDialogFolderId != 0) {
                if (archiveHidden) {
                    backgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    swipeMessage = LocaleController.getString("UnhideFromTop", swipeMessageStringId = R.string.UnhideFromTop);
                    translationDrawable = Theme.dialogs_unpinArchiveDrawable;
                } else {
                    backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    swipeMessage = LocaleController.getString("HideOnTop", swipeMessageStringId = R.string.HideOnTop);
                    translationDrawable = Theme.dialogs_pinArchiveDrawable;
                }
            } else {
                if (promoDialog) {
                    backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    swipeMessage = LocaleController.getString("PsaHide", swipeMessageStringId = R.string.PsaHide);
                    translationDrawable = Theme.dialogs_hidePsaDrawable;
                } else if (folderId == 0) {
                    backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_MUTE) {
                        if (dialogMuted) {
                            swipeMessage = LocaleController.getString("SwipeUnmute", swipeMessageStringId = R.string.SwipeUnmute);
                            translationDrawable = Theme.dialogs_swipeUnmuteDrawable;
                        } else {
                            swipeMessage = LocaleController.getString("SwipeMute", swipeMessageStringId = R.string.SwipeMute);
                            translationDrawable = Theme.dialogs_swipeMuteDrawable;
                        }
                    } else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_DELETE) {
                        swipeMessage = LocaleController.getString("SwipeDeleteChat", swipeMessageStringId = R.string.SwipeDeleteChat);
                        backgroundColor = Theme.getColor(Theme.key_dialogSwipeRemove, resourcesProvider);
                        translationDrawable = Theme.dialogs_swipeDeleteDrawable;
                    } else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_READ) {
                        if (unreadCount > 0 || markUnread) {
                            swipeMessage = LocaleController.getString("SwipeMarkAsRead", swipeMessageStringId = R.string.SwipeMarkAsRead);
                            translationDrawable = Theme.dialogs_swipeReadDrawable;
                        } else {
                            swipeMessage = LocaleController.getString("SwipeMarkAsUnread", swipeMessageStringId = R.string.SwipeMarkAsUnread);
                            translationDrawable = Theme.dialogs_swipeUnreadDrawable;
                        }
                    } else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_PIN) {
                        if (drawPin) {
                            swipeMessage = LocaleController.getString("SwipeUnpin", swipeMessageStringId = R.string.SwipeUnpin);
                            translationDrawable = Theme.dialogs_swipeUnpinDrawable;
                        } else {
                            swipeMessage = LocaleController.getString("SwipePin", swipeMessageStringId = R.string.SwipePin);
                            translationDrawable = Theme.dialogs_swipePinDrawable;
                        }
                    } else {
                        swipeMessage = LocaleController.getString("Archive", swipeMessageStringId = R.string.Archive);
                        translationDrawable = Theme.dialogs_archiveDrawable;
                    }
                } else {
                    backgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    swipeMessage = LocaleController.getString("Unarchive", swipeMessageStringId = R.string.Unarchive);
                    translationDrawable = Theme.dialogs_unarchiveDrawable;
                }
            }

            if (swipeCanceled && lastDrawTranslationDrawable != null) {
                translationDrawable = lastDrawTranslationDrawable;
                swipeMessageStringId = lastDrawSwipeMessageStringId;
            } else {
                lastDrawTranslationDrawable = translationDrawable;
                lastDrawSwipeMessageStringId = swipeMessageStringId;
            }

            if (!translationAnimationStarted && Math.abs(translationX) > AndroidUtilities.dp(43)) {
                translationAnimationStarted = true;
                translationDrawable.setProgress(0.0f);
                translationDrawable.setCallback(this);
                translationDrawable.start();
            }

            float tx = getMeasuredWidth() + translationX;
            if (currentRevealProgress < 1.0f) {
                Theme.dialogs_pinnedPaint.setColor(backgroundColor);
                canvas.drawRect(tx - AndroidUtilities.dp(8), 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
                if (currentRevealProgress == 0) {
                    if (Theme.dialogs_archiveDrawableRecolored) {
                        Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", Theme.getNonAnimatedColor(Theme.key_chats_archiveBackground));
                        Theme.dialogs_archiveDrawableRecolored = false;
                    }
                    if (Theme.dialogs_hidePsaDrawableRecolored) {
                        Theme.dialogs_hidePsaDrawable.beginApplyLayerColors();
                        Theme.dialogs_hidePsaDrawable.setLayerColor("Line 1.**", Theme.getNonAnimatedColor(Theme.key_chats_archiveBackground));
                        Theme.dialogs_hidePsaDrawable.setLayerColor("Line 2.**", Theme.getNonAnimatedColor(Theme.key_chats_archiveBackground));
                        Theme.dialogs_hidePsaDrawable.setLayerColor("Line 3.**", Theme.getNonAnimatedColor(Theme.key_chats_archiveBackground));
                        Theme.dialogs_hidePsaDrawable.commitApplyLayerColors();
                        Theme.dialogs_hidePsaDrawableRecolored = false;
                    }
                }
            }
            int drawableX = getMeasuredWidth() - AndroidUtilities.dp(43) - translationDrawable.getIntrinsicWidth() / 2;
            int drawableY = AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12 : 9);
            int drawableCx = drawableX + translationDrawable.getIntrinsicWidth() / 2;
            int drawableCy = drawableY + translationDrawable.getIntrinsicHeight() / 2;

            if (currentRevealProgress > 0.0f) {
                canvas.save();
                canvas.clipRect(tx - AndroidUtilities.dp(8), 0, getMeasuredWidth(), getMeasuredHeight());
                Theme.dialogs_pinnedPaint.setColor(revealBackgroundColor);

                float rad = (float) Math.sqrt(drawableCx * drawableCx + (drawableCy - getMeasuredHeight()) * (drawableCy - getMeasuredHeight()));
                canvas.drawCircle(drawableCx, drawableCy, rad * AndroidUtilities.accelerateInterpolator.getInterpolation(currentRevealProgress), Theme.dialogs_pinnedPaint);
                canvas.restore();

                if (!Theme.dialogs_archiveDrawableRecolored) {
                    Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", Theme.getNonAnimatedColor(Theme.key_chats_archivePinBackground));
                    Theme.dialogs_archiveDrawableRecolored = true;
                }
                if (!Theme.dialogs_hidePsaDrawableRecolored) {
                    Theme.dialogs_hidePsaDrawable.beginApplyLayerColors();
                    Theme.dialogs_hidePsaDrawable.setLayerColor("Line 1.**", Theme.getNonAnimatedColor(Theme.key_chats_archivePinBackground));
                    Theme.dialogs_hidePsaDrawable.setLayerColor("Line 2.**", Theme.getNonAnimatedColor(Theme.key_chats_archivePinBackground));
                    Theme.dialogs_hidePsaDrawable.setLayerColor("Line 3.**", Theme.getNonAnimatedColor(Theme.key_chats_archivePinBackground));
                    Theme.dialogs_hidePsaDrawable.commitApplyLayerColors();
                    Theme.dialogs_hidePsaDrawableRecolored = true;
                }
            }

            canvas.save();
            canvas.translate(drawableX, drawableY);
            if (currentRevealBounceProgress != 0.0f && currentRevealBounceProgress != 1.0f) {
                float scale = 1.0f + interpolator.getInterpolation(currentRevealBounceProgress);
                canvas.scale(scale, scale, translationDrawable.getIntrinsicWidth() / 2, translationDrawable.getIntrinsicHeight() / 2);
            }
            setDrawableBounds(translationDrawable, 0, 0);
            translationDrawable.draw(canvas);
            canvas.restore();

            canvas.clipRect(tx, 0, getMeasuredWidth(), getMeasuredHeight());

            int width = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(swipeMessage));

            if (swipeMessageTextId != swipeMessageStringId || swipeMessageWidth != getMeasuredWidth()) {
                swipeMessageTextId = swipeMessageStringId;
                swipeMessageWidth = getMeasuredWidth();
                swipeMessageTextLayout = new StaticLayout(swipeMessage, Theme.dialogs_archiveTextPaint, Math.min(AndroidUtilities.dp(80), width), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

                if (swipeMessageTextLayout.getLineCount() > 1) {
                    swipeMessageTextLayout = new StaticLayout(swipeMessage, Theme.dialogs_archiveTextPaintSmall, Math.min(AndroidUtilities.dp(82), width), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                }
            }

            if (swipeMessageTextLayout != null) {
                canvas.save();
                float yOffset = swipeMessageTextLayout.getLineCount() > 1 ? -AndroidUtilities.dp(4) : 0;
                canvas.translate(getMeasuredWidth() - AndroidUtilities.dp(43) - swipeMessageTextLayout.getWidth() / 2f, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 50 : 47) + yOffset);
                swipeMessageTextLayout.draw(canvas);
                canvas.restore();
            }

//            if (width / 2 < AndroidUtilities.dp(40)) {
         //       canvas.drawText(swipeMessage, getMeasuredWidth() - AndroidUtilities.dp(43) - width / 2, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 62 : 59), Theme.dialogs_archiveTextPaint);
//            }

            canvas.restore();
        } else if (translationDrawable != null) {
            translationDrawable.stop();
            translationDrawable.setProgress(0.0f);
            translationDrawable.setCallback(null);
            translationDrawable = null;
            translationAnimationStarted = false;
        }

        if (translationX != 0) {
            canvas.save();
            canvas.translate(translationX, 0);
        }

        if (isSelected) {
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_tabletSeletedPaint);
        }
        if (currentDialogFolderId != 0 && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0)) {
            Theme.dialogs_pinnedPaint.setColor(AndroidUtilities.getOffsetColor(0, Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider), archiveBackgroundProgress, 1.0f));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
        } else if (drawPin || drawPinBackground) {
            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
        }

        if (translationX != 0 || cornerProgress != 0.0f) {
            canvas.save();

            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(64), 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8) * cornerProgress, AndroidUtilities.dp(8) * cornerProgress, Theme.dialogs_pinnedPaint);

            if (currentDialogFolderId != 0 && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0)) {
                Theme.dialogs_pinnedPaint.setColor(AndroidUtilities.getOffsetColor(0, Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider), archiveBackgroundProgress, 1.0f));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8) * cornerProgress, AndroidUtilities.dp(8) * cornerProgress, Theme.dialogs_pinnedPaint);
            } else if (drawPin || drawPinBackground) {
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8) * cornerProgress, AndroidUtilities.dp(8) * cornerProgress, Theme.dialogs_pinnedPaint);
            }
            canvas.restore();
        }

        if (translationX != 0) {
            if (cornerProgress < 1.0f) {
                cornerProgress += dt / 150.0f;
                if (cornerProgress > 1.0f) {
                    cornerProgress = 1.0f;
                }
                needInvalidate = true;
            }
        } else if (cornerProgress > 0.0f) {
            cornerProgress -= dt / 150.0f;
            if (cornerProgress < 0.0f) {
                cornerProgress = 0.0f;
            }
            needInvalidate = true;
        }

        if (drawNameLock) {
            setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_lockDrawable.draw(canvas);
        } else if (drawNameGroup) {
            setDrawableBounds(Theme.dialogs_groupDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_groupDrawable.draw(canvas);
        } else if (drawNameBroadcast) {
            setDrawableBounds(Theme.dialogs_broadcastDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_broadcastDrawable.draw(canvas);
        } else if (drawNameBot) {
            setDrawableBounds(Theme.dialogs_botDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_botDrawable.draw(canvas);
        }

        if (nameLayout != null) {
            if (currentDialogFolderId != 0) {
                Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_nameArchived, resourcesProvider));
            } else if (encryptedChat != null || customDialog != null && customDialog.type == 2) {
                Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_secretName, resourcesProvider));
            } else {
                Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_name, resourcesProvider));
            }
            canvas.save();
            canvas.translate(nameLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 13));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (timeLayout != null && currentDialogFolderId == 0) {
            canvas.save();
            canvas.translate(timeLeft, timeTop);
            timeLayout.draw(canvas);
            canvas.restore();
        }

        if (messageNameLayout != null) {
            if (currentDialogFolderId != 0) {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessageArchived_threeLines, resourcesProvider));
            } else if (draftMessage != null) {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_draft, resourcesProvider));
            } else {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessage_threeLines, resourcesProvider));
            }
            canvas.save();
            canvas.translate(messageNameLeft, messageNameTop);
            try {
                messageNameLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();
        }

        if (messageLayout != null) {
            if (currentDialogFolderId != 0) {
                if (chat != null) {
                    Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_nameMessageArchived, resourcesProvider));
                } else {
                    Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_messageArchived, resourcesProvider));
                }
            } else {
                Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_message, resourcesProvider));
            }
            canvas.save();
            canvas.translate(messageLeft, messageTop);
            try {
                messageLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();

            if (printingStringType >= 0) {
                StatusDrawable statusDrawable = Theme.getChatStatusDrawable(printingStringType);
                if (statusDrawable != null) {
                    canvas.save();
                    if (printingStringType == 1 || printingStringType == 4) {
                        canvas.translate(statusDrawableLeft, messageTop + (printingStringType == 1 ? AndroidUtilities.dp(1) : 0));
                    } else {
                        canvas.translate(statusDrawableLeft, messageTop + (AndroidUtilities.dp(18) - statusDrawable.getIntrinsicHeight()) / 2f);
                    }
                    statusDrawable.draw(canvas);
                    invalidate(statusDrawableLeft, messageTop, statusDrawableLeft + statusDrawable.getIntrinsicWidth(), messageTop + statusDrawable.getIntrinsicHeight());
                    canvas.restore();
                }
            }
        }


        if (currentDialogFolderId == 0) {
            int currentStatus = (drawClock ? 1 : 0) +  (drawCheck1 ? 2 : 0) + (drawCheck2 ? 4 : 0);
            if (lastStatusDrawableParams >= 0 && lastStatusDrawableParams != currentStatus && !statusDrawableAnimationInProgress) {
                createStatusDrawableAnimator(lastStatusDrawableParams, currentStatus);
            }
            if (statusDrawableAnimationInProgress) {
                currentStatus = animateToStatusDrawableParams;
            }

            boolean drawClock = (currentStatus & 1) != 0;
            boolean drawCheck1 = (currentStatus & 2) != 0;
            boolean drawCheck2 = (currentStatus & 4) != 0;

            if (statusDrawableAnimationInProgress) {
                boolean outDrawClock = (animateFromStatusDrawableParams & 1) != 0;
                boolean outDrawCheck1 = (animateFromStatusDrawableParams & 2) != 0;
                boolean outDrawCheck2 = (animateFromStatusDrawableParams & 4) != 0;
                if (!drawClock && !outDrawClock && outDrawCheck2 && !outDrawCheck1 && drawCheck1 && drawCheck2) {
                    drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, true, statusDrawableProgress);
                } else {
                    drawCheckStatus(canvas, outDrawClock, outDrawCheck1, outDrawCheck2, false, 1f - statusDrawableProgress);
                    drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, false, statusDrawableProgress);
                }
            } else {
                drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, false,1f);
            }
            lastStatusDrawableParams = (this.drawClock ? 1 : 0) +  (this.drawCheck1 ? 2 : 0) + (this.drawCheck2 ? 4 : 0);
        }

        if ((dialogMuted || dialogMutedProgress > 0) && !drawVerified && drawScam == 0) {
            if (dialogMuted && dialogMutedProgress != 1f) {
                dialogMutedProgress += 16 / 150f;
                if (dialogMutedProgress > 1f) {
                    dialogMutedProgress = 1f;
                } else {
                    invalidate();
                }
            } else if (!dialogMuted && dialogMutedProgress != 0f) {
                dialogMutedProgress -= 16 / 150f;
                if (dialogMutedProgress < 0f) {
                    dialogMutedProgress = 0f;
                } else {
                    invalidate();
                }
            }
            setDrawableBounds(Theme.dialogs_muteDrawable, nameMuteLeft - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 0 : 1), AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 13.5f : 17.5f));
            if (dialogMutedProgress != 1f) {
                canvas.save();
                canvas.scale(dialogMutedProgress, dialogMutedProgress, Theme.dialogs_muteDrawable.getBounds().centerX(), Theme.dialogs_muteDrawable.getBounds().centerY());
                Theme.dialogs_muteDrawable.setAlpha((int) (255 * dialogMutedProgress));
                Theme.dialogs_muteDrawable.draw(canvas);
                Theme.dialogs_muteDrawable.setAlpha(255);
                canvas.restore();
            } else {
                Theme.dialogs_muteDrawable.draw(canvas);
            }

        } else if (drawVerified) {
            setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 16.5f));
            setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 16.5f));
            Theme.dialogs_verifiedDrawable.draw(canvas);
            Theme.dialogs_verifiedCheckDrawable.draw(canvas);
        } else if (drawScam != 0) {
            setDrawableBounds((drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable), nameMuteLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12 : 15));
            (drawScam == 1 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable).draw(canvas);
        }

        if (drawReorder || reorderIconProgress != 0) {
            Theme.dialogs_reorderDrawable.setAlpha((int) (reorderIconProgress * 255));
            setDrawableBounds(Theme.dialogs_reorderDrawable, pinLeft, pinTop);
            Theme.dialogs_reorderDrawable.draw(canvas);
        }
        if (drawError) {
            Theme.dialogs_errorDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
            rect.set(errorLeft, errorTop, errorLeft + AndroidUtilities.dp(23), errorTop + AndroidUtilities.dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_errorPaint);
            setDrawableBounds(Theme.dialogs_errorDrawable, errorLeft + AndroidUtilities.dp(5.5f), errorTop + AndroidUtilities.dp(5));
            Theme.dialogs_errorDrawable.draw(canvas);
        } else if (drawCount || drawMention || countChangeProgress != 1f) {
            if (drawCount || countChangeProgress != 1f) {
                final float progressFinal = (unreadCount == 0 && !markUnread) ? 1f - countChangeProgress : countChangeProgress;
                if (countOldLayout == null || unreadCount == 0) {
                    StaticLayout drawLayout = unreadCount == 0 ? countOldLayout : countLayout;
                    Paint paint = dialogMuted || currentDialogFolderId != 0 ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint;
                    paint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
                    Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                    int x = countLeft - AndroidUtilities.dp(5.5f);
                    rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));

                    if (progressFinal != 1f) {
                        if (drawPin) {
                            Theme.dialogs_pinnedDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
                            setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop);
                            canvas.save();
                            canvas.scale(1f - progressFinal, 1f - progressFinal, Theme.dialogs_pinnedDrawable.getBounds().centerX(), Theme.dialogs_pinnedDrawable.getBounds().centerY());
                            Theme.dialogs_pinnedDrawable.draw(canvas);
                            canvas.restore();
                        }
                        canvas.save();
                        canvas.scale(progressFinal, progressFinal, rect.centerX(), rect.centerY());
                    }

                    canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, paint);
                    if (drawLayout != null) {
                        canvas.save();
                        canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
                        drawLayout.draw(canvas);
                        canvas.restore();
                    }

                    if (progressFinal != 1f) {
                        canvas.restore();
                    }
                } else {
                    Paint paint = dialogMuted || currentDialogFolderId != 0 ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint;
                    paint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
                    Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                    float progressHalf = progressFinal * 2;
                    if (progressHalf > 1f) {
                        progressHalf = 1f;
                    }

                    float countLeft = this.countLeft * progressHalf + countLeftOld * (1f - progressHalf);
                    float x = countLeft - AndroidUtilities.dp(5.5f);
                    rect.set(x, countTop, x + (countWidth * progressHalf) + (countWidthOld * (1f - progressHalf)) + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));

                    float scale = 1f;
                    if (progressFinal <= 0.5f) {
                        scale += 0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(progressFinal * 2);
                    } else {
                        scale += 0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation((1f - (progressFinal - 0.5f) * 2));
                    }


                    canvas.save();
                    canvas.scale(scale, scale, rect.centerX(), rect.centerY());
                    canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, paint);

                    if (countAnimationStableLayout != null) {
                        canvas.save();
                        canvas.translate(countLeft,  countTop + AndroidUtilities.dp(4));
                        countAnimationStableLayout.draw(canvas);
                        canvas.restore();
                    }

                    int textAlpha = Theme.dialogs_countTextPaint.getAlpha();
                    Theme.dialogs_countTextPaint.setAlpha((int) (textAlpha * progressHalf));
                    if (countAnimationInLayout != null) {
                        canvas.save();
                        canvas.translate(countLeft,  (countAnimationIncrement ? AndroidUtilities.dp(13) : -AndroidUtilities.dp(13)) * (1f - progressHalf) + countTop + AndroidUtilities.dp(4));
                        countAnimationInLayout.draw(canvas);
                        canvas.restore();
                    } else if (countLayout != null) {
                        canvas.save();
                        canvas.translate(countLeft,  (countAnimationIncrement ? AndroidUtilities.dp(13) : -AndroidUtilities.dp(13)) * (1f - progressHalf) + countTop + AndroidUtilities.dp(4));
                        countLayout.draw(canvas);
                        canvas.restore();
                    }

                    if (countOldLayout != null) {
                        Theme.dialogs_countTextPaint.setAlpha((int) (textAlpha * (1f - progressHalf)));
                        canvas.save();
                        canvas.translate(countLeft, (countAnimationIncrement ? -AndroidUtilities.dp(13) : AndroidUtilities.dp(13)) * progressHalf + countTop + AndroidUtilities.dp(4));
                        countOldLayout.draw(canvas);
                        canvas.restore();
                    }
                    Theme.dialogs_countTextPaint.setAlpha(textAlpha);
                    canvas.restore();

                }
            }
            if (drawMention) {
                Theme.dialogs_countPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                int x = mentionLeft - AndroidUtilities.dp(5.5f);
                rect.set(x, countTop, x + mentionWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                Paint paint = dialogMuted && folderId != 0 ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint;
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, paint);
                if (mentionLayout != null) {
                    Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                    canvas.save();
                    canvas.translate(mentionLeft, countTop + AndroidUtilities.dp(4));
                    mentionLayout.draw(canvas);
                    canvas.restore();
                } else {
                    Theme.dialogs_mentionDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                    setDrawableBounds(Theme.dialogs_mentionDrawable, mentionLeft - AndroidUtilities.dp(2), countTop + AndroidUtilities.dp(3.2f), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                    Theme.dialogs_mentionDrawable.draw(canvas);
                }
            }
        } else if (drawPin) {
            Theme.dialogs_pinnedDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
            setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop);
            Theme.dialogs_pinnedDrawable.draw(canvas);
        }

        if (animatingArchiveAvatar) {
            canvas.save();
            float scale = 1.0f + interpolator.getInterpolation(animatingArchiveAvatarProgress / 170.0f);
            canvas.scale(scale, scale, avatarImage.getCenterX(), avatarImage.getCenterY());
        }

        if (currentDialogFolderId == 0 || archivedChatsDrawable == null || !archivedChatsDrawable.isDraw()) {
            avatarImage.draw(canvas);
        }

        if (hasMessageThumb) {
            thumbImage.draw(canvas);
            if (drawPlay) {
                int x = (int) (thumbImage.getCenterX() - Theme.dialogs_playDrawable.getIntrinsicWidth() / 2);
                int y = (int) (thumbImage.getCenterY() - Theme.dialogs_playDrawable.getIntrinsicHeight() / 2);
                setDrawableBounds(Theme.dialogs_playDrawable, x, y);
                Theme.dialogs_playDrawable.draw(canvas);
            }
        }

        if (animatingArchiveAvatar) {
            canvas.restore();
        }

        if (isDialogCell && currentDialogFolderId == 0) {
            if (user != null && !MessagesController.isSupportUser(user) && !user.bot) {
                boolean isOnline = isOnline();
                if (isOnline || onlineProgress != 0) {
                    int top = (int) (avatarImage.getImageY2() - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 6 : 8));
                    int left;
                    if (LocaleController.isRTL) {
                        left = (int) (avatarImage.getImageX() + AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 6));
                    } else {
                        left = (int) (avatarImage.getImageX2() - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 6));
                    }

                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(7) * onlineProgress, Theme.dialogs_onlineCirclePaint);
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(5) * onlineProgress, Theme.dialogs_onlineCirclePaint);
                    if (isOnline) {
                        if (onlineProgress < 1.0f) {
                            onlineProgress += dt / 150.0f;
                            if (onlineProgress > 1.0f) {
                                onlineProgress = 1.0f;
                            }
                            needInvalidate = true;
                        }
                    } else {
                        if (onlineProgress > 0.0f) {
                            onlineProgress -= dt / 150.0f;
                            if (onlineProgress < 0.0f) {
                                onlineProgress = 0.0f;
                            }
                            needInvalidate = true;
                        }
                    }
                }
            } else if (chat != null) {
                hasCall = chat.call_active && chat.call_not_empty;
                if (hasCall || chatCallProgress != 0) {
                    float checkProgress = checkBox != null && checkBox.isChecked() ? 1.0f - checkBox.getProgress() : 1.0f;
                    int top = (int) (avatarImage.getImageY2() - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 6 : 8));
                    int left;
                    if (LocaleController.isRTL) {
                        left = (int) (avatarImage.getImageX() + AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 6));
                    } else {
                        left = (int) (avatarImage.getImageX2() - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 6));
                    }

                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(11) * chatCallProgress * checkProgress, Theme.dialogs_onlineCirclePaint);
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(9) * chatCallProgress * checkProgress, Theme.dialogs_onlineCirclePaint);
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

                    float size1;
                    float size2;
                    if (progressStage == 0) {
                        size1 = AndroidUtilities.dp(1) + AndroidUtilities.dp(4) * innerProgress;
                        size2 = AndroidUtilities.dp(3) - AndroidUtilities.dp(2) * innerProgress;
                    } else if (progressStage == 1) {
                        size1 = AndroidUtilities.dp(5) - AndroidUtilities.dp(4) * innerProgress;
                        size2 = AndroidUtilities.dp(1) + AndroidUtilities.dp(4) * innerProgress;
                    } else if (progressStage == 2) {
                        size1 = AndroidUtilities.dp(1) + AndroidUtilities.dp(2) * innerProgress;
                        size2 = AndroidUtilities.dp(5) - AndroidUtilities.dp(4) * innerProgress;
                    } else if (progressStage == 3) {
                        size1 = AndroidUtilities.dp(3) - AndroidUtilities.dp(2) * innerProgress;
                        size2 = AndroidUtilities.dp(1) + AndroidUtilities.dp(2) * innerProgress;
                    } else if (progressStage == 4) {
                        size1 = AndroidUtilities.dp(1) + AndroidUtilities.dp(4) * innerProgress;
                        size2 = AndroidUtilities.dp(3) - AndroidUtilities.dp(2) * innerProgress;
                    } else if (progressStage == 5) {
                        size1 = AndroidUtilities.dp(5) - AndroidUtilities.dp(4) * innerProgress;
                        size2 = AndroidUtilities.dp(1) + AndroidUtilities.dp(4) * innerProgress;
                    } else if (progressStage == 6) {
                        size1 = AndroidUtilities.dp(1) + AndroidUtilities.dp(4) * innerProgress;
                        size2 = AndroidUtilities.dp(5) - AndroidUtilities.dp(4) * innerProgress;
                    } else {
                        size1 = AndroidUtilities.dp(5) - AndroidUtilities.dp(4) * innerProgress;
                        size2 = AndroidUtilities.dp(1) + AndroidUtilities.dp(2) * innerProgress;
                    }

                    if (chatCallProgress < 1.0f || checkProgress < 1.0f) {
                        canvas.save();
                        canvas.scale(chatCallProgress * checkProgress, chatCallProgress * checkProgress, left, top);
                    }
                    rect.set(left - AndroidUtilities.dp(1), top - size1, left + AndroidUtilities.dp(1), top + size1);
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.dialogs_onlineCirclePaint);

                    rect.set(left - AndroidUtilities.dp(5), top - size2, left - AndroidUtilities.dp(3), top + size2);
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.dialogs_onlineCirclePaint);

                    rect.set(left + AndroidUtilities.dp(3), top - size2, left + AndroidUtilities.dp(5), top + size2);
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), Theme.dialogs_onlineCirclePaint);
                    if (chatCallProgress < 1.0f || checkProgress < 1.0f) {
                        canvas.restore();
                    }

                    innerProgress += dt / 400.0f;
                    if (innerProgress >= 1.0f) {
                        innerProgress = 0.0f;
                        progressStage++;
                        if (progressStage >= 8) {
                            progressStage = 0;
                        }
                    }
                    needInvalidate = true;

                    if (hasCall) {
                        if (chatCallProgress < 1.0f) {
                            chatCallProgress += dt / 150.0f;
                            if (chatCallProgress > 1.0f) {
                                chatCallProgress = 1.0f;
                            }
                        }
                    } else {
                        if (chatCallProgress > 0.0f) {
                            chatCallProgress -= dt / 150.0f;
                            if (chatCallProgress < 0.0f) {
                                chatCallProgress = 0.0f;
                            }
                        }
                    }
                }
            }
        }

        if (translationX != 0) {
            canvas.restore();
        }
        if (currentDialogFolderId != 0 && translationX == 0 && archivedChatsDrawable != null) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            archivedChatsDrawable.draw(canvas);
            canvas.restore();
        }

        if (useSeparator) {
            int left;
            if (fullSeparator || currentDialogFolderId != 0 && archiveHidden && !fullSeparator2 || fullSeparator2 && !archiveHidden) {
                left = 0;
            } else {
                left = AndroidUtilities.dp(72);
            }
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - left, getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(left, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        if (clipProgress != 0.0f) {
            if (Build.VERSION.SDK_INT != 24) {
                canvas.restore();
            } else {
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                canvas.drawRect(0, 0, getMeasuredWidth(), topClip * clipProgress, Theme.dialogs_pinnedPaint);
                canvas.drawRect(0, getMeasuredHeight() - (int) (bottomClip * clipProgress), getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
            }
        }

        if (drawReorder || reorderIconProgress != 0.0f) {
            if (drawReorder) {
                if (reorderIconProgress < 1.0f) {
                    reorderIconProgress += dt / 170.0f;
                    if (reorderIconProgress > 1.0f) {
                        reorderIconProgress = 1.0f;
                    }
                    needInvalidate = true;
                }
            } else {
                if (reorderIconProgress > 0.0f) {
                    reorderIconProgress -= dt / 170.0f;
                    if (reorderIconProgress < 0.0f) {
                        reorderIconProgress = 0.0f;
                    }
                    needInvalidate = true;
                }
            }
        }

        if (archiveHidden) {
            if (archiveBackgroundProgress > 0.0f) {
                archiveBackgroundProgress -= dt / 230.0f;
                if (archiveBackgroundProgress < 0.0f) {
                    archiveBackgroundProgress = 0.0f;
                }
                if (avatarDrawable.getAvatarType() == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
                    avatarDrawable.setArchivedAvatarHiddenProgress(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(archiveBackgroundProgress));
                }
                needInvalidate = true;
            }
        } else {
            if (archiveBackgroundProgress < 1.0f) {
                archiveBackgroundProgress += dt / 230.0f;
                if (archiveBackgroundProgress > 1.0f) {
                    archiveBackgroundProgress = 1.0f;
                }
                if (avatarDrawable.getAvatarType() == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
                    avatarDrawable.setArchivedAvatarHiddenProgress(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(archiveBackgroundProgress));
                }
                needInvalidate = true;
            }
        }

        if (animatingArchiveAvatar) {
            animatingArchiveAvatarProgress += dt;
            if (animatingArchiveAvatarProgress >= 170.0f) {
                animatingArchiveAvatarProgress = 170.0f;
                animatingArchiveAvatar = false;
            }
            needInvalidate = true;
        }
        if (drawRevealBackground) {
            if (currentRevealBounceProgress < 1.0f) {
                currentRevealBounceProgress += dt / 170.0f;
                if (currentRevealBounceProgress > 1.0f) {
                    currentRevealBounceProgress = 1.0f;
                    needInvalidate = true;
                }
            }
            if (currentRevealProgress < 1.0f) {
                currentRevealProgress += dt / 300.0f;
                if (currentRevealProgress > 1.0f) {
                    currentRevealProgress = 1.0f;
                }
                needInvalidate = true;
            }
        } else {
            if (currentRevealBounceProgress == 1.0f) {
                currentRevealBounceProgress = 0.0f;
                needInvalidate = true;
            }
            if (currentRevealProgress > 0.0f) {
                currentRevealProgress -= dt / 300.0f;
                if (currentRevealProgress < 0.0f) {
                    currentRevealProgress = 0.0f;
                }
                needInvalidate = true;
            }
        }
        if (needInvalidate) {
            invalidate();
        }
    }

    private void createStatusDrawableAnimator(int lastStatusDrawableParams, int currentStatus) {
        statusDrawableProgress = 0f;
        statusDrawableAnimator = ValueAnimator.ofFloat(0,1f);
        statusDrawableAnimator.setDuration(220);

        statusDrawableAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animateFromStatusDrawableParams = lastStatusDrawableParams;
        animateToStatusDrawableParams = currentStatus;
        statusDrawableAnimator.addUpdateListener(valueAnimator -> {
            statusDrawableProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        statusDrawableAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                int currentStatus = (DialogCell.this.drawClock ? 1 : 0) +  (DialogCell.this.drawCheck1 ? 2 : 0) + (DialogCell.this.drawCheck2 ? 4 : 0);
                if (animateToStatusDrawableParams != currentStatus) {
                    createStatusDrawableAnimator(animateToStatusDrawableParams, currentStatus);
                } else {
                    statusDrawableAnimationInProgress = false;
                    DialogCell.this.lastStatusDrawableParams = animateToStatusDrawableParams;
                }
                invalidate();
            }
        });
        statusDrawableAnimationInProgress = true;
        statusDrawableAnimator.start();
    }

    public void startOutAnimation() {
        if (archivedChatsDrawable != null) {
            archivedChatsDrawable.outCy = avatarImage.getCenterY();
            archivedChatsDrawable.outCx = avatarImage.getCenterX();
            archivedChatsDrawable.outRadius = avatarImage.getImageWidth() / 2.0f;
            archivedChatsDrawable.outImageSize = avatarImage.getBitmapWidth();
            archivedChatsDrawable.startOutAnimation();
        }
    }

    public void onReorderStateChanged(boolean reordering, boolean animated) {
        if (!drawPin && reordering || drawReorder == reordering) {
            if (!drawPin) {
                drawReorder = false;
            }
            return;
        }
        drawReorder = reordering;
        if (animated) {
            reorderIconProgress = drawReorder ? 0.0f : 1.0f;
        } else {
            reorderIconProgress = drawReorder ? 1.0f : 0.0f;
        }
        invalidate();
    }

    public void setSliding(boolean value) {
        isSliding = value;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (who == translationDrawable || who == Theme.dialogs_archiveAvatarDrawable) {
            invalidate(who.getBounds());
        } else {
            super.invalidateDrawable(who);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (isFolderCell() && archivedChatsDrawable != null && SharedConfig.archiveHidden && archivedChatsDrawable.pullProgress == 0.0f) {
            info.setVisibleToUser(false);
        } else {
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        }
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        StringBuilder sb = new StringBuilder();
        if (currentDialogFolderId == 1) {
            sb.append(LocaleController.getString("ArchivedChats", R.string.ArchivedChats));
            sb.append(". ");
        } else {
            if (encryptedChat != null) {
                sb.append(LocaleController.getString("AccDescrSecretChat", R.string.AccDescrSecretChat));
                sb.append(". ");
            }
            if (user != null) {
                if (UserObject.isReplyUser(user)) {
                    sb.append(LocaleController.getString("RepliesTitle", R.string.RepliesTitle));
                } else {
                    if (user.bot) {
                        sb.append(LocaleController.getString("Bot", R.string.Bot));
                        sb.append(". ");
                    }
                    if (user.self) {
                        sb.append(LocaleController.getString("SavedMessages", R.string.SavedMessages));
                    } else {
                        sb.append(ContactsController.formatName(user.first_name, user.last_name));
                    }
                }
                sb.append(". ");
            } else if (chat != null) {
                if (chat.broadcast) {
                    sb.append(LocaleController.getString("AccDescrChannel", R.string.AccDescrChannel));
                } else {
                    sb.append(LocaleController.getString("AccDescrGroup", R.string.AccDescrGroup));
                }
                sb.append(". ");
                sb.append(chat.title);
                sb.append(". ");
            }
        }
        if (unreadCount > 0) {
            sb.append(LocaleController.formatPluralString("NewMessages", unreadCount));
            sb.append(". ");
        }
        if (message == null || currentDialogFolderId != 0) {
            event.setContentDescription(sb.toString());
            return;
        }
        int lastDate = lastMessageDate;
        if (lastMessageDate == 0) {
            lastDate = message.messageOwner.date;
        }
        String date = LocaleController.formatDateAudio(lastDate, true);
        if (message.isOut()) {
            sb.append(LocaleController.formatString("AccDescrSentDate", R.string.AccDescrSentDate, date));
        } else {
            sb.append(LocaleController.formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, date));
        }
        sb.append(". ");
        if (chat != null && !message.isOut() && message.isFromUser() && message.messageOwner.action == null) {
            TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(message.messageOwner.from_id.user_id);
            if (fromUser != null) {
                sb.append(ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                sb.append(". ");
            }
        }
        if (encryptedChat == null) {
            sb.append(message.messageText);
            if (!message.isMediaEmpty()) {
                if (!TextUtils.isEmpty(message.caption)) {
                    sb.append(". ");
                    sb.append(message.caption);
                }
            }
        }
        event.setContentDescription(sb.toString());
    }

    public void setClipProgress(float value) {
        clipProgress = value;
        invalidate();
    }

    public float getClipProgress() {
        return clipProgress;
    }

    public void setTopClip(int value) {
        topClip = value;
    }

    public void setBottomClip(int value) {
        bottomClip = value;
    }

    public void setArchivedPullAnimation(PullForegroundDrawable drawable) {
        archivedChatsDrawable = drawable;
    }

    public int getCurrentDialogFolderId() {
        return currentDialogFolderId;
    }

    public MessageObject getMessage() {
        return message;
    }
}
