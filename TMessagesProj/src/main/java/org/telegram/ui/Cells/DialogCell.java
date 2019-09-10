/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.HapticFeedbackConstants;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
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
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

public class DialogCell extends BaseCell {

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

    private int currentAccount = UserConfig.selectedAccount;
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

    private ImageReceiver avatarImage = new ImageReceiver(this);
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private boolean animatingArchiveAvatar;
    private float animatingArchiveAvatarProgress;
    private BounceInterpolator interpolator = new BounceInterpolator();

    private TLRPC.User user;
    private TLRPC.Chat chat;
    private TLRPC.EncryptedChat encryptedChat;
    private CharSequence lastPrintString;
    private TLRPC.DraftMessage draftMessage;

    private CheckBox2 checkBox;

    public boolean useForceThreeLines;
    public boolean useSeparator;
    public boolean fullSeparator;
    public boolean fullSeparator2;

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

    private boolean drawCheck1;
    private boolean drawCheck2;
    private boolean drawClock;
    private int checkDrawLeft;
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
    private StaticLayout countLayout;

    private boolean drawMention;
    private int mentionLeft;
    private int mentionWidth;
    private StaticLayout mentionLayout;

    private boolean drawVerified;

    private boolean drawScam;

    private boolean isSelected;

    private RectF rect = new RectF();

    public class BounceInterpolator implements Interpolator {

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

    public DialogCell(Context context, boolean needCheck, boolean forceThreeLines) {
        super(context);

        Theme.createDialogsResources(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(28));
        useForceThreeLines = forceThreeLines;

        if (needCheck) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox);
        }
    }

    public void setDialog(TLRPC.Dialog dialog, int type, int folder) {
        currentDialogId = dialog.id;
        isDialogCell = true;
        if (dialog instanceof TLRPC.TL_dialogFolder) {
            TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
            currentDialogFolderId = dialogFolder.folder.id;
        } else {
            currentDialogFolderId = 0;
        }
        dialogsType = type;
        folderId = folder;
        messageId = 0;
        update(0);
        checkOnline();
    }

    public void setDialogIndex(int i) {
        index = i;
    }

    public void setDialog(CustomDialog dialog) {
        customDialog = dialog;
        messageId = 0;
        update(0);
        checkOnline();
    }

    private void checkOnline() {
        boolean isOnline = user != null && !user.self && (user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id));
        onlineProgress = isOnline ? 1.0f : 0.0f;
    }

    public void setDialog(long dialog_id, MessageObject messageObject, int date) {
        currentDialogId = dialog_id;
        message = messageObject;
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isSliding = false;
        drawRevealBackground = false;
        currentRevealProgress = 0.0f;
        attachedToWindow = false;
        reorderIconProgress = drawPin && drawReorder ? 1.0f : 0.0f;
        avatarImage.onDetachedFromWindow();
        if (translationDrawable != null) {
            translationDrawable.stop();
            translationDrawable.setProgress(0.0f);
            translationDrawable.setCallback(null);
            translationDrawable = null;
            translationAnimationStarted = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
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
            int x = LocaleController.isRTL ? (right - left) - AndroidUtilities.dp(45) : AndroidUtilities.dp(45);
            int y = AndroidUtilities.dp(46);
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
            if (DialogObject.isSecretDialogId(dialog.id)) {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog.id >> 32));
                if (encryptedChat != null) {
                    currentUser = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                }
            } else {
                int lowerId = (int) dialog.id;
                if (lowerId > 0) {
                    currentUser = MessagesController.getInstance(currentAccount).getUser(lowerId);
                } else {
                    currentChat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
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
                builder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, Theme.getColor(Theme.key_chats_nameArchived)), boldStart, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (builder.length() > 150) {
                break;
            }
        }
        return Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(17), false);
    }

    public void buildLayout() {
        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
            Theme.dialogs_namePaint.setTextSize(AndroidUtilities.dp(16));
            Theme.dialogs_nameEncryptedPaint.setTextSize(AndroidUtilities.dp(16));
            Theme.dialogs_messagePaint.setTextSize(AndroidUtilities.dp(15));
            Theme.dialogs_messagePrintingPaint.setTextSize(AndroidUtilities.dp(15));

            Theme.dialogs_messagePaint.setColor(Theme.dialogs_messagePaint.linkColor = Theme.getColor(Theme.key_chats_message_threeLines));
        } else {
            Theme.dialogs_namePaint.setTextSize(AndroidUtilities.dp(17));
            Theme.dialogs_nameEncryptedPaint.setTextSize(AndroidUtilities.dp(17));
            Theme.dialogs_messagePaint.setTextSize(AndroidUtilities.dp(16));
            Theme.dialogs_messagePrintingPaint.setTextSize(AndroidUtilities.dp(16));

            Theme.dialogs_messagePaint.setColor(Theme.dialogs_messagePaint.linkColor = Theme.getColor(Theme.key_chats_message));
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
            printingString = MessagesController.getInstance(currentAccount).printingStrings.get(currentDialogId);
        }
        TextPaint currentMessagePaint = Theme.dialogs_messagePaint;
        boolean checkMessage = true;

        drawNameGroup = false;
        drawNameBroadcast = false;
        drawNameLock = false;
        drawNameBot = false;
        drawVerified = false;
        drawScam = false;
        drawPinBackground = false;
        boolean showChecks = !UserObject.isUserSelf(user);
        boolean drawTime = true;

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
                    currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, message.messageText));
                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_attachMessage)), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            } else {
                messageString = customDialog.message;
                if (customDialog.isMedia) {
                    currentMessagePaint = Theme.dialogs_messagePrintingPaint;
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
                drawClock = false;
                drawError = false;
            } else {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = false;
                drawError = false;
            }
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
                            drawScam = true;
                            Theme.dialogs_scamDrawable.checkText();
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
                            drawScam = true;
                            Theme.dialogs_scamDrawable.checkText();
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
                draftMessage = MediaDataController.getInstance(currentAccount).getDraft(currentDialogId);
                if (draftMessage != null && (TextUtils.isEmpty(draftMessage.message) && draftMessage.reply_to_msg_id == 0 || lastDate > draftMessage.date && unreadCount != 0) ||
                        ChatObject.isChannel(chat) && !chat.megagroup && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) ||
                        chat != null && (chat.left || chat.kicked)) {
                    draftMessage = null;
                }
            } else {
                draftMessage = null;
            }

            if (printingString != null) {
                lastPrintString = messageString = printingString;
                currentMessagePaint = Theme.dialogs_messagePrintingPaint;
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
                            stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_draft)), 0, messageNameString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            messageString = stringBuilder;
                        }
                    } else {
                        String mess = draftMessage.message;
                        if (mess.length() > 150) {
                            mess = mess.substring(0, 150);
                        }
                        SpannableStringBuilder stringBuilder;
                        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                            stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess.replace('\n', ' '), messageNameString));
                        } else {
                            stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess.replace('\n', ' '), messageNameString));
                            stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_draft)), 0, messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    }
                } else {
                    if (clearingDialog) {
                        currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                        messageString = LocaleController.getString("HistoryCleared", R.string.HistoryCleared);
                    } else if (message == null) {
                        if (encryptedChat != null) {
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                            if (encryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
                                messageString = LocaleController.getString("EncryptionProcessing", R.string.EncryptionProcessing);
                            } else if (encryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
                                if (user != null && user.first_name != null) {
                                    messageString = LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, user.first_name);
                                } else {
                                    messageString = LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, "");
                                }
                            } else if (encryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
                                messageString = LocaleController.getString("EncryptionRejected", R.string.EncryptionRejected);
                            } else if (encryptedChat instanceof TLRPC.TL_encryptedChat) {
                                if (encryptedChat.admin_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                    if (user != null && user.first_name != null) {
                                        messageString = LocaleController.formatString("EncryptedChatStartedOutgoing", R.string.EncryptedChatStartedOutgoing, user.first_name);
                                    } else {
                                        messageString = LocaleController.formatString("EncryptedChatStartedOutgoing", R.string.EncryptedChatStartedOutgoing, "");
                                    }
                                } else {
                                    messageString = LocaleController.getString("EncryptedChatStartedIncoming", R.string.EncryptedChatStartedIncoming);
                                }
                            }
                        } else {
                            messageString = "";
                        }
                    } else {
                        TLRPC.User fromUser = null;
                        TLRPC.Chat fromChat = null;
                        if (message.isFromUser()) {
                            fromUser = MessagesController.getInstance(currentAccount).getUser(message.messageOwner.from_id);
                        } else {
                            fromChat = MessagesController.getInstance(currentAccount).getChat(message.messageOwner.to_id.channel_id);
                        }
                        if (dialogsType == 3 && UserObject.isUserSelf(user)) {
                            messageString = LocaleController.getString("SavedMessagesInfo", R.string.SavedMessagesInfo);
                            showChecks = false;
                            drawTime = false;
                        } else if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout && currentDialogFolderId != 0) {
                            checkMessage = false;
                            messageString = formatArchivedDialogNames();
                        } else if (message.messageOwner instanceof TLRPC.TL_messageService) {
                            if (ChatObject.isChannel(chat) && (message.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear ||
                                    message.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom)) {
                                messageString = "";
                                showChecks = false;
                            } else {
                                messageString = message.messageText;
                            }
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                        } else {
                            if (chat != null && chat.id > 0 && fromChat == null) {
                                if (message.isOutOwner()) {
                                    messageNameString = LocaleController.getString("FromYou", R.string.FromYou);
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
                                } else if (fromChat != null) {
                                    messageNameString = fromChat.title.replace("\n", "");
                                } else {
                                    messageNameString = "DELETED";
                                }
                                checkMessage = false;
                                SpannableStringBuilder stringBuilder;
                                if (message.caption != null) {
                                    String mess = message.caption.toString();
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    String emoji;
                                    if (message.isVideo()) {
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
                                    currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                                    CharSequence innerMessage;
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83C\uDFAE \u2068%s\u2069", message.messageOwner.media.game.title);
                                        } else {
                                            innerMessage = String.format("\uD83C\uDFAE %s", message.messageOwner.media.game.title);
                                        }
                                    } else if (message.type == 14) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            innerMessage = String.format("\uD83C\uDFA7 \u2068%s - %s\u2069", message.getMusicAuthor(), message.getMusicTitle());
                                        } else {
                                            innerMessage = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                                        }
                                    } else {
                                        innerMessage = message.messageText;
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, innerMessage, messageNameString));
                                    try {
                                        stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_attachMessage)), hasNameInMessage ? messageNameString.length() + 2 : 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                } else if (message.messageOwner.message != null) {
                                    String mess = message.messageOwner.message;
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, mess.replace('\n', ' '), messageNameString));
                                } else {
                                    stringBuilder = SpannableStringBuilder.valueOf("");
                                }
                                if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || currentDialogFolderId != 0 && stringBuilder.length() > 0) {
                                    try {
                                        stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_nameMessage)), 0, messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                                messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                            } else {
                                if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString("AttachPhotoExpired", R.string.AttachPhotoExpired);
                                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && message.messageOwner.media.document instanceof TLRPC.TL_documentEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString("AttachVideoExpired", R.string.AttachVideoExpired);
                                } else if (message.caption != null) {
                                    String emoji;
                                    if (message.isVideo()) {
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
                                    messageString = emoji + message.caption;
                                } else {
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        messageString = "\uD83C\uDFAE " + message.messageOwner.media.game.title;
                                    } else if (message.type == 14) {
                                        messageString = String.format("\uD83C\uDFA7 %s - %s", message.getMusicAuthor(), message.getMusicTitle());
                                    } else {
                                        messageString = message.messageText;
                                    }
                                    if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                        currentMessagePaint = Theme.dialogs_messagePrintingPaint;
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

            if (dialogsType == 0 && MessagesController.getInstance(currentAccount).isProxyDialog(currentDialogId, true)) {
                drawPinBackground = true;
                timeString = LocaleController.getString("UseProxySponsor", R.string.UseProxySponsor);
            }

            if (currentDialogFolderId != 0) {
                nameString = LocaleController.getString("ArchivedChats", R.string.ArchivedChats);
            } else {
                if (chat != null) {
                    nameString = chat.title;
                } else if (user != null) {
                    if (UserObject.isUserSelf(user)) {
                        if (dialogsType == 3) {
                            drawPinBackground = true;
                        }
                        nameString = LocaleController.getString("SavedMessages", R.string.SavedMessages);
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
                checkDrawLeft = timeLeft - w;
            } else {
                checkDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5);
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
                    checkDrawLeft = timeLeft - w;
                } else {
                    checkDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5);
                    nameLeft += w;
                }
            }
        }

        if (dialogMuted && !drawVerified && !drawScam) {
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
        } else if (drawScam) {
            int w = AndroidUtilities.dp(6) + Theme.dialogs_scamDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        }

        nameWidth = Math.max(AndroidUtilities.dp(12), nameWidth);
        try {
            CharSequence nameStringFinal = TextUtils.ellipsize(nameString.replace('\n', ' '), Theme.dialogs_namePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, Theme.dialogs_namePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        int messageWidth;
        int avatarLeft;
        int avatarTop;
        if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
            avatarTop = AndroidUtilities.dp(11);
            messageNameTop = AndroidUtilities.dp(32);
            timeTop = AndroidUtilities.dp(13);
            errorTop = AndroidUtilities.dp(43);
            pinTop = AndroidUtilities.dp(43);
            countTop = AndroidUtilities.dp(43);
            checkDrawTop = AndroidUtilities.dp(13);
            messageWidth = getMeasuredWidth() - AndroidUtilities.dp(72 + 21);

            if (!LocaleController.isRTL) {
                messageLeft = messageNameLeft = AndroidUtilities.dp(72 + 6);
                avatarLeft = AndroidUtilities.dp(10);
            } else {
                messageLeft = messageNameLeft = AndroidUtilities.dp(16);
                avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(66);
            }
            avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
        } else {
            avatarTop = AndroidUtilities.dp(9);
            messageNameTop = AndroidUtilities.dp(31);
            timeTop = AndroidUtilities.dp(16);
            errorTop = AndroidUtilities.dp(39);
            pinTop = AndroidUtilities.dp(39);
            countTop = AndroidUtilities.dp(39);
            checkDrawTop = AndroidUtilities.dp(17);
            messageWidth = getMeasuredWidth() - AndroidUtilities.dp(72 + 23);

            if (!LocaleController.isRTL) {
                messageLeft = messageNameLeft = AndroidUtilities.dp(72 + 4);
                avatarLeft = AndroidUtilities.dp(10);
            } else {
                messageLeft = messageNameLeft = AndroidUtilities.dp(22);
                avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(64);
            }
            avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(54), AndroidUtilities.dp(54));
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
            }
            messageString = Emoji.replaceEmoji(mess, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(17), false);
        }
        messageWidth = Math.max(AndroidUtilities.dp(12), messageWidth);
        if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && messageNameString != null && (currentDialogFolderId == 0 || currentDialogFolderDialogsCount == 1)) {
            try {
                messageNameLayout = StaticLayoutEx.createStaticLayout(messageNameString, Theme.dialogs_messageNamePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false, TextUtils.TruncateAt.END, messageWidth, 1);
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageTop = AndroidUtilities.dp(32 + 19);
        } else {
            messageNameLayout = null;
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                messageTop = AndroidUtilities.dp(32);
            } else {
                messageTop = AndroidUtilities.dp(39);
            }
        }

        try {
            CharSequence messageStringFinal;
            if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && currentDialogFolderId != 0 && currentDialogFolderDialogsCount > 1) {
                messageStringFinal = messageNameString;
                messageNameString = null;
                currentMessagePaint = Theme.dialogs_messagePaint;
            } else if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || messageNameString != null) {
                messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            } else {
                messageStringFinal = messageString;
            }
            if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
                messageLayout = StaticLayoutEx.createStaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, messageWidth, messageNameString != null ? 1 : 2);
            } else {
                messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                widthpx = Math.ceil(nameLayout.getLineWidth(0));
                if (dialogMuted && !drawVerified && !drawScam) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                } else if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                } else if (drawScam) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_scamDrawable.getIntrinsicWidth());
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
                if (dialogMuted || drawVerified || drawScam) {
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
        ArrayList<TLRPC.Dialog> dialogsArray = DialogsActivity.getDialogsArray(currentAccount, dialogsType, folderId, frozen);
        if (index < dialogsArray.size()) {
            TLRPC.Dialog dialog = dialogsArray.get(index);
            TLRPC.Dialog nextDialog = index + 1 < dialogsArray.size() ? dialogsArray.get(index + 1) : null;
            TLRPC.DraftMessage newDraftMessage = MediaDataController.getInstance(currentAccount).getDraft(currentDialogId);
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
                    message == null && newMessageObject != null || newDraftMessage != draftMessage || drawPin != dialog.pinned) {
                boolean dialogChanged = currentDialogId != dialog.id;
                currentDialogId = dialog.id;
                if (dialog instanceof TLRPC.TL_dialogFolder) {
                    TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
                    currentDialogFolderId = dialogFolder.folder.id;
                } else {
                    currentDialogFolderId = 0;
                }
                fullSeparator = dialog instanceof TLRPC.TL_dialog && dialog.pinned && nextDialog != null && !nextDialog.pinned;
                fullSeparator2 = dialog instanceof TLRPC.TL_dialogFolder && nextDialog != null && !nextDialog.pinned;
                update(0);
                if (dialogChanged) {
                    reorderIconProgress = drawPin && drawReorder ? 1.0f : 0.0f;
                }
                checkOnline();
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
        ArrayList<TLRPC.Dialog> dialogs = DialogsActivity.getDialogsArray(currentAccount, dialogsType, currentDialogFolderId, false);
        MessageObject maxMessage = null;
        if (!dialogs.isEmpty()) {
            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                MessageObject object = MessagesController.getInstance(currentAccount).dialogMessage.get(dialog.id);
                if (object != null && (maxMessage == null || object.messageOwner.date > maxMessage.messageOwner.date)) {
                    maxMessage = object;
                }
                if (dialog.pinnedNum == 0) {
                    break;
                }
            }
        }
        return maxMessage;
    }

    public void update(int mask) {
        if (customDialog != null) {
            lastMessageDate = customDialog.date;
            lastUnreadState = customDialog.unread_count != 0;
            unreadCount = customDialog.unread_count;
            drawPin = customDialog.pinned;
            dialogMuted = customDialog.muted;
            avatarDrawable.setInfo(customDialog.id, customDialog.name, null);
            avatarImage.setImage(null, "50_50", avatarDrawable, null, 0);
        } else {
            if (isDialogCell) {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(currentDialogId);
                if (dialog != null) {
                    if (mask == 0) {
                        clearingDialog = MessagesController.getInstance(currentAccount).isClearingDialog(dialog.id);
                        message = MessagesController.getInstance(currentAccount).dialogMessage.get(dialog.id);
                        lastUnreadState = message != null && message.isUnread();
                        unreadCount = dialog.unread_count;
                        markUnread = dialog.unread_mark;
                        mentionCount = dialog.unread_mentions_count;
                        currentEditDate = message != null ? message.messageOwner.edit_date : 0;
                        lastMessageDate = dialog.last_message_date;
                        drawPin = currentDialogFolderId == 0 && dialog.pinned;
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
                        CharSequence printString = MessagesController.getInstance(currentAccount).printingStrings.get(currentDialogId);
                        if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                            continueUpdate = true;
                        }
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_MESSAGE_TEXT) != 0) {
                    if (message != null && message.messageText != lastMessageString) {
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
                    } else if (isDialogCell) {
                        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(currentDialogId);
                        if (dialog != null && (unreadCount != dialog.unread_count || markUnread != dialog.unread_mark || mentionCount != dialog.unread_mentions_count)) {
                            unreadCount = dialog.unread_count;
                            mentionCount = dialog.unread_mentions_count;
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
                int lower_id = (int) dialogId;
                int high_id = (int) (dialogId >> 32);
                if (lower_id != 0) {
                    if (lower_id < 0) {
                        chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                        if (!isDialogCell && chat != null && chat.migrated_to != null) {
                            TLRPC.Chat chat2 = MessagesController.getInstance(currentAccount).getChat(chat.migrated_to.channel_id);
                            if (chat2 != null) {
                                chat = chat2;
                            }
                        }
                    } else {
                        user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                    }
                } else {
                    encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                    if (encryptedChat != null) {
                        user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                    }
                }
            }

            if (currentDialogFolderId != 0) {
                Theme.dialogs_archiveAvatarDrawable.setCallback(this);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ARCHIVED);
                avatarImage.setImage(null, null, avatarDrawable, null, user, 0);
            } else {
                if (user != null) {
                    avatarDrawable.setInfo(user);
                    if (UserObject.isUserSelf(user)) {
                        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                        avatarImage.setImage(null, null, avatarDrawable, null, user, 0);
                    } else {
                        avatarImage.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, null, user, 0);
                    }
                } else if (chat != null) {
                    avatarDrawable.setInfo(chat);
                    avatarImage.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable, null, chat, 0);
                }
            }
        }
        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
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
        }
        if (isSliding) {
            boolean prevValue = drawRevealBackground;
            drawRevealBackground = Math.abs(translationX) >= getMeasuredWidth() * 0.3f;
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

        long newTime = SystemClock.uptimeMillis();
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
            String archive;
            int backgroundColor;
            int revealBackgroundColor;
            if (currentDialogFolderId != 0) {
                if (archiveHidden) {
                    backgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archiveBackground);
                    archive = LocaleController.getString("UnhideFromTop", R.string.UnhideFromTop);
                    translationDrawable = Theme.dialogs_unpinArchiveDrawable;
                } else {
                    backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground);
                    archive = LocaleController.getString("HideOnTop", R.string.HideOnTop);
                    translationDrawable = Theme.dialogs_pinArchiveDrawable;
                }
            } else {
                if (folderId == 0) {
                    backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground);
                    archive = LocaleController.getString("Archive", R.string.Archive);
                    translationDrawable = Theme.dialogs_archiveDrawable;
                } else {
                    backgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archiveBackground);
                    archive = LocaleController.getString("Unarchive", R.string.Unarchive);
                    translationDrawable = Theme.dialogs_unarchiveDrawable;
                }
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
                if (currentRevealProgress == 0 && Theme.dialogs_archiveDrawableRecolored) {
                    Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", Theme.getColor(Theme.key_chats_archiveBackground));
                    Theme.dialogs_archiveDrawableRecolored = false;
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
                    Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", Theme.getColor(Theme.key_chats_archivePinBackground));
                    Theme.dialogs_archiveDrawableRecolored = true;
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

            int width = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(archive));
            canvas.drawText(archive, getMeasuredWidth() - AndroidUtilities.dp(43) - width / 2, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 62 : 59), Theme.dialogs_archiveTextPaint);

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
            Theme.dialogs_pinnedPaint.setColor(AndroidUtilities.getOffsetColor(0, Theme.getColor(Theme.key_chats_pinnedOverlay), archiveBackgroundProgress, 1.0f));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
        } else if (drawPin || drawPinBackground) {
            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
        }

        if (translationX != 0 || cornerProgress != 0.0f) {
            canvas.save();

            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(64), 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8) * cornerProgress, AndroidUtilities.dp(8) * cornerProgress, Theme.dialogs_pinnedPaint);

            if (currentDialogFolderId != 0 && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0)) {
                Theme.dialogs_pinnedPaint.setColor(AndroidUtilities.getOffsetColor(0, Theme.getColor(Theme.key_chats_pinnedOverlay), archiveBackgroundProgress, 1.0f));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8) * cornerProgress, AndroidUtilities.dp(8) * cornerProgress, Theme.dialogs_pinnedPaint);
            } else if (drawPin || drawPinBackground) {
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay));
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
                Theme.dialogs_namePaint.setColor(Theme.dialogs_namePaint.linkColor = Theme.getColor(Theme.key_chats_nameArchived));
            } else if (encryptedChat != null || customDialog != null && customDialog.type == 2) {
                Theme.dialogs_namePaint.setColor(Theme.dialogs_namePaint.linkColor = Theme.getColor(Theme.key_chats_secretName));
            } else {
                Theme.dialogs_namePaint.setColor(Theme.dialogs_namePaint.linkColor = Theme.getColor(Theme.key_chats_name));
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
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessageArchived_threeLines));
            } else if (draftMessage != null) {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_draft));
            } else {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessage_threeLines));
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
                    Theme.dialogs_messagePaint.setColor(Theme.dialogs_messagePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessageArchived));
                } else {
                    Theme.dialogs_messagePaint.setColor(Theme.dialogs_messagePaint.linkColor = Theme.getColor(Theme.key_chats_messageArchived));
                }
            } else {
                Theme.dialogs_messagePaint.setColor(Theme.dialogs_messagePaint.linkColor = Theme.getColor(Theme.key_chats_message));
            }
            canvas.save();
            canvas.translate(messageLeft, messageTop);
            try {
                messageLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();
        }

        if (currentDialogFolderId == 0) {
            if (drawClock) {
                setDrawableBounds(Theme.dialogs_clockDrawable, checkDrawLeft, checkDrawTop);
                Theme.dialogs_clockDrawable.draw(canvas);
            } else if (drawCheck2) {
                if (drawCheck1) {
                    setDrawableBounds(Theme.dialogs_halfCheckDrawable, halfCheckDrawLeft, checkDrawTop);
                    Theme.dialogs_halfCheckDrawable.draw(canvas);
                    setDrawableBounds(Theme.dialogs_checkReadDrawable, checkDrawLeft, checkDrawTop);
                    Theme.dialogs_checkReadDrawable.draw(canvas);
                } else {
                    setDrawableBounds(Theme.dialogs_checkDrawable, checkDrawLeft, checkDrawTop);
                    Theme.dialogs_checkDrawable.draw(canvas);
                }
            }
        }

        if (dialogMuted && !drawVerified && !drawScam) {
            setDrawableBounds(Theme.dialogs_muteDrawable, nameMuteLeft - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 0 : 1), AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 13.5f : 17.5f));
            Theme.dialogs_muteDrawable.draw(canvas);
        } else if (drawVerified) {
            setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 16.5f));
            setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12.5f : 16.5f));
            Theme.dialogs_verifiedDrawable.draw(canvas);
            Theme.dialogs_verifiedCheckDrawable.draw(canvas);
        } else if (drawScam) {
            setDrawableBounds(Theme.dialogs_scamDrawable, nameMuteLeft, AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 12 : 15));
            Theme.dialogs_scamDrawable.draw(canvas);
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
        } else if (drawCount || drawMention) {
            if (drawCount) {
                Paint paint = dialogMuted || currentDialogFolderId != 0 ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint;
                paint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
                Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                int x = countLeft - AndroidUtilities.dp(5.5f);
                rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, paint);
                if (countLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
                    countLayout.draw(canvas);
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
        avatarImage.draw(canvas);
        if (animatingArchiveAvatar) {
            canvas.restore();
        }

        if (user != null && isDialogCell && currentDialogFolderId == 0 && !MessagesController.isSupportUser(user) && !user.bot) {
            boolean isOnline = !user.self && (user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id));
            if (isOnline || onlineProgress != 0) {
                int top = avatarImage.getImageY2() - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 6 : 8);
                int left;
                if (LocaleController.isRTL) {
                    left = avatarImage.getImageX() + AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 6);
                } else {
                    left = avatarImage.getImageX2() - AndroidUtilities.dp(useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 6);
                }

                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawCircle(left, top, AndroidUtilities.dp(7) * onlineProgress, Theme.dialogs_onlineCirclePaint);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
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
        }

        if (translationX != 0) {
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
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                archiveBackgroundProgress -= dt / 170.0f;
                if (currentRevealBounceProgress < 0.0f) {
                    currentRevealBounceProgress = 0.0f;
                }
                if (avatarDrawable.getAvatarType() == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
                    avatarDrawable.setArchivedAvatarHiddenProgress(archiveBackgroundProgress);
                }
                needInvalidate = true;
            }
        } else {
            if (archiveBackgroundProgress < 1.0f) {
                archiveBackgroundProgress += dt / 170.0f;
                if (currentRevealBounceProgress > 1.0f) {
                    currentRevealBounceProgress = 1.0f;
                }
                if (avatarDrawable.getAvatarType() == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
                    avatarDrawable.setArchivedAvatarHiddenProgress(archiveBackgroundProgress);
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
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
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
                if (user.bot) {
                    sb.append(LocaleController.getString("Bot", R.string.Bot));
                    sb.append(". ");
                }
                if (user.self) {
                    sb.append(LocaleController.getString("SavedMessages", R.string.SavedMessages));
                } else {
                    sb.append(ContactsController.formatName(user.first_name, user.last_name));
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
        if (lastMessageDate == 0 && message != null) {
            lastDate = message.messageOwner.date;
        }
        String date = LocaleController.formatDateAudio(lastDate);
        if (message.isOut()) {
            sb.append(LocaleController.formatString("AccDescrSentDate", R.string.AccDescrSentDate, date));
        } else {
            sb.append(LocaleController.formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, date));
        }
        sb.append(". ");
        if (chat != null && !message.isOut() && message.isFromUser() && message.messageOwner.action == null) {
            TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(message.messageOwner.from_id);
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
}
