/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.query.DraftQuery;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.GroupCreateCheckBox;

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

    private CustomDialog customDialog;
    private long currentDialogId;
    private int currentEditDate;
    private boolean isDialogCell;
    private int lastMessageDate;
    private int unreadCount;
    private int mentionCount;
    private boolean lastUnreadState;
    private int lastSendState;
    private boolean dialogMuted;
    private MessageObject message;
    private int index;
    private int dialogsType;

    private ImageReceiver avatarImage = new ImageReceiver(this);
    private AvatarDrawable avatarDrawable = new AvatarDrawable();

    private TLRPC.User user = null;
    private TLRPC.Chat chat = null;
    private TLRPC.EncryptedChat encryptedChat = null;
    private CharSequence lastPrintString = null;
    private TLRPC.DraftMessage draftMessage;

    private GroupCreateCheckBox checkBox;

    public boolean useSeparator = false;

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
    private int timeTop = AndroidUtilities.dp(17);
    private StaticLayout timeLayout;

    private boolean drawCheck1;
    private boolean drawCheck2;
    private boolean drawClock;
    private int checkDrawLeft;
    private int checkDrawTop = AndroidUtilities.dp(18);
    private int halfCheckDrawLeft;

    private int messageTop = AndroidUtilities.dp(40);
    private int messageLeft;
    private StaticLayout messageLayout;

    private boolean drawError;
    private int errorTop = AndroidUtilities.dp(39);
    private int errorLeft;

    private boolean drawPinBackground;
    private boolean drawPin;
    private int pinTop = AndroidUtilities.dp(39);
    private int pinLeft;

    private boolean drawCount;
    private int countTop = AndroidUtilities.dp(39);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean drawMention;
    private int mentionLeft;
    private int mentionWidth;

    private boolean drawVerified;

    private int avatarTop = AndroidUtilities.dp(10);

    private boolean isSelected;

    private RectF rect = new RectF();

    public DialogCell(Context context, boolean needCheck) {
        super(context);

        Theme.createDialogsResources(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(26));

        if (needCheck) {
            checkBox = new GroupCreateCheckBox(context);
            checkBox.setVisibility(VISIBLE);
            addView(checkBox);
        }
    }

    public void setDialog(TLRPC.TL_dialog dialog, int i, int type) {
        currentDialogId = dialog.id;
        isDialogCell = true;
        index = i;
        dialogsType = type;
        update(0);
    }

    public void setDialog(CustomDialog dialog) {
        customDialog = dialog;
        update(0);
    }

    public void setDialog(long dialog_id, MessageObject messageObject, int date) {
        currentDialogId = dialog_id;
        message = messageObject;
        isDialogCell = false;
        lastMessageDate = date;
        currentEditDate = messageObject != null ? messageObject.messageOwner.edit_date : 0;
        unreadCount = 0;
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(72) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentDialogId == 0 && customDialog == null) {
            return;
        }
        if (checkBox != null) {
            int x = LocaleController.isRTL ? (right - left) - AndroidUtilities.dp(42) : AndroidUtilities.dp(42);
            int y = AndroidUtilities.dp(43);
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        if (changed) {
            buildLayout();
        }
    }

    public void buildLayout() {
        String nameString = "";
        String timeString = "";
        String countString = null;
        String mentionString = null;
        CharSequence messageString = "";
        CharSequence printingString = null;
        if (isDialogCell) {
            printingString = MessagesController.getInstance().printingStrings.get(currentDialogId);
        }
        TextPaint currentNamePaint = Theme.dialogs_namePaint;
        TextPaint currentMessagePaint = Theme.dialogs_messagePaint;
        boolean checkMessage = true;

        drawNameGroup = false;
        drawNameBroadcast = false;
        drawNameLock = false;
        drawNameBot = false;
        drawVerified = false;
        drawPinBackground = false;
        boolean showChecks = !UserObject.isUserSelf(user);
        boolean drawTime = true;

        String messageFormat;
        if (Build.VERSION.SDK_INT >= 18) {
            messageFormat = "%s: \u2068%s\u2069";
        } else {
            messageFormat = "%s: %s";
        }

        if (customDialog != null) {
            if (customDialog.type == 2) {
                drawNameLock = true;
                nameLockTop = AndroidUtilities.dp(16.5f);
                if (!LocaleController.isRTL) {
                    nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                    nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                } else {
                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                    nameLeft = AndroidUtilities.dp(14);
                }
            } else {
                drawVerified = customDialog.verified;
                if (customDialog.type == 1) {
                    drawNameGroup = true;
                    nameLockTop = AndroidUtilities.dp(17.5f);
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                        nameLeft = AndroidUtilities.dp(14);
                    }
                } else {
                    if (!LocaleController.isRTL) {
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                    } else {
                        nameLeft = AndroidUtilities.dp(14);
                    }
                }
            }

            if (customDialog.type == 1) {
                String name = LocaleController.getString("FromYou", R.string.FromYou);
                checkMessage = false;
                SpannableStringBuilder stringBuilder;
                if (customDialog.isMedia) {
                    currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, name, message.messageText));
                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_attachMessage)), name.length() + 2, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    String mess = customDialog.message;
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, name, mess.replace('\n', ' ')));
                }
                if (stringBuilder.length() > 0) {
                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_nameMessage)), 0, name.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            if (customDialog.type == 2) {
                currentNamePaint = Theme.dialogs_nameEncryptedPaint;
            }
        } else {
            if (encryptedChat != null) {
                drawNameLock = true;
                nameLockTop = AndroidUtilities.dp(16.5f);
                if (!LocaleController.isRTL) {
                    nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                    nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                } else {
                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                    nameLeft = AndroidUtilities.dp(14);
                }
            } else {
                if (chat != null) {
                    if (chat.id < 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
                        drawNameBroadcast = true;
                        nameLockTop = AndroidUtilities.dp(16.5f);
                    } else {
                        drawNameGroup = true;
                        nameLockTop = AndroidUtilities.dp(17.5f);
                    }
                    drawVerified = chat.verified;

                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                        nameLeft = AndroidUtilities.dp(14);
                    }
                } else {
                    if (!LocaleController.isRTL) {
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                    } else {
                        nameLeft = AndroidUtilities.dp(14);
                    }
                    if (user != null) {
                        if (user.bot) {
                            drawNameBot = true;
                            nameLockTop = AndroidUtilities.dp(16.5f);
                            if (!LocaleController.isRTL) {
                                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_botDrawable.getIntrinsicWidth();
                            } else {
                                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - Theme.dialogs_botDrawable.getIntrinsicWidth();
                                nameLeft = AndroidUtilities.dp(14);
                            }
                        }
                        drawVerified = user.verified;
                    }
                }
            }

            int lastDate = lastMessageDate;
            if (lastMessageDate == 0 && message != null) {
                lastDate = message.messageOwner.date;
            }

            if (isDialogCell) {
                draftMessage = DraftQuery.getDraft(currentDialogId);
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
                    if (TextUtils.isEmpty(draftMessage.message)) {
                        String draftString = LocaleController.getString("Draft", R.string.Draft);
                        SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(draftString);
                        stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_draft)), 0, draftString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        messageString = stringBuilder;
                    } else {
                        String mess = draftMessage.message;
                        if (mess.length() > 150) {
                            mess = mess.substring(0, 150);
                        }
                        String draftString = LocaleController.getString("Draft", R.string.Draft);
                        SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, draftString, mess.replace('\n', ' ')));
                        stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_draft)), 0, draftString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    }
                } else {
                    if (message == null) {
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
                                if (encryptedChat.admin_id == UserConfig.getClientUserId()) {
                                    if (user != null && user.first_name != null) {
                                        messageString = LocaleController.formatString("EncryptedChatStartedOutgoing", R.string.EncryptedChatStartedOutgoing, user.first_name);
                                    } else {
                                        messageString = LocaleController.formatString("EncryptedChatStartedOutgoing", R.string.EncryptedChatStartedOutgoing, "");
                                    }
                                } else {
                                    messageString = LocaleController.getString("EncryptedChatStartedIncoming", R.string.EncryptedChatStartedIncoming);
                                }
                            }
                        }
                    } else {
                        TLRPC.User fromUser = null;
                        TLRPC.Chat fromChat = null;
                        if (message.isFromUser()) {
                            fromUser = MessagesController.getInstance().getUser(message.messageOwner.from_id);
                        } else {
                            fromChat = MessagesController.getInstance().getChat(message.messageOwner.to_id.channel_id);
                        }
                        if (dialogsType == 3 && UserObject.isUserSelf(user)) {
                            messageString = LocaleController.getString("SavedMessagesInfo", R.string.SavedMessagesInfo);
                            showChecks = false;
                            drawTime = false;
                        } else if (message.messageOwner instanceof TLRPC.TL_messageService) {
                            if (ChatObject.isChannel(chat) && message.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear) {
                                messageString = "";
                                showChecks = false;
                            } else {
                                messageString = message.messageText;
                            }
                            currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                        } else {
                            if (chat != null && chat.id > 0 && fromChat == null) {
                                String name;
                                if (message.isOutOwner()) {
                                    name = LocaleController.getString("FromYou", R.string.FromYou);
                                } else if (fromUser != null) {
                                    name = UserObject.getFirstName(fromUser).replace("\n", "");
                                } else if (fromChat != null) {
                                    name = fromChat.title.replace("\n", "");
                                } else {
                                    name = "DELETED";
                                }
                                checkMessage = false;
                                SpannableStringBuilder stringBuilder;
                                if (message.caption != null) {
                                    String mess = message.caption.toString();
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, name, mess.replace('\n', ' ')));
                                } else if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                    currentMessagePaint = Theme.dialogs_messagePrintingPaint;
                                    if (message.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            stringBuilder = SpannableStringBuilder.valueOf(String.format("%s: \uD83C\uDFAE \u2068%s\u2069", name, message.messageOwner.media.game.title));
                                        } else {
                                            stringBuilder = SpannableStringBuilder.valueOf(String.format("%s: \uD83C\uDFAE %s", name, message.messageOwner.media.game.title));
                                        }
                                    } else if (message.type == 14) {
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            stringBuilder = SpannableStringBuilder.valueOf(String.format("%s: \uD83C\uDFA7 \u2068%s - %s\u2069", name, message.getMusicAuthor(), message.getMusicTitle()));
                                        } else {
                                            stringBuilder = SpannableStringBuilder.valueOf(String.format("%s: \uD83C\uDFA7 %s - %s", name, message.getMusicAuthor(), message.getMusicTitle()));
                                        }
                                    } else {
                                        stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, name, message.messageText));
                                    }
                                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_attachMessage)), name.length() + 2, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (message.messageOwner.message != null) {
                                    String mess = message.messageOwner.message;
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, name, mess.replace('\n', ' ')));
                                } else {
                                    stringBuilder = SpannableStringBuilder.valueOf("");
                                }
                                if (stringBuilder.length() > 0) {
                                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_nameMessage)), 0, name.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                            } else {
                                if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && message.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString("AttachPhotoExpired", R.string.AttachPhotoExpired);
                                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && message.messageOwner.media.document instanceof TLRPC.TL_documentEmpty && message.messageOwner.media.ttl_seconds != 0) {
                                    messageString = LocaleController.getString("AttachVideoExpired", R.string.AttachVideoExpired);
                                } else if (message.caption != null) {
                                    messageString = message.caption;
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
                if (unreadCount != 0 && (unreadCount != 1 || unreadCount != mentionCount || message == null || !message.messageOwner.mentioned)) {
                    drawCount = true;
                    countString = String.format("%d", unreadCount);
                } else {
                    drawCount = false;
                }
                if (mentionCount != 0) {
                    drawMention = true;
                    mentionString = "@";
                } else {
                    drawMention = false;
                }

                if (message.isOut() && draftMessage == null && showChecks) {
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

            if (chat != null) {
                nameString = chat.title;
            } else if (user != null) {
                if (UserObject.isUserSelf(user)) {
                    if (dialogsType == 3) {
                        drawPinBackground = true;
                    }
                    nameString = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                } else if (user.id / 1000 != 777 && user.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(user.id) == null) {
                    if (ContactsController.getInstance().contactsDict.size() == 0 && (!ContactsController.getInstance().contactsLoaded || ContactsController.getInstance().isLoadingContacts())) {
                        nameString = UserObject.getUserName(user);
                    } else {
                        if (user.phone != null && user.phone.length() != 0) {
                            nameString = PhoneFormat.getInstance().format("+" + user.phone);
                        } else {
                            nameString = UserObject.getUserName(user);
                        }
                    }
                } else {
                    nameString = UserObject.getUserName(user);
                }
                if (encryptedChat != null) {
                    currentNamePaint = Theme.dialogs_nameEncryptedPaint;
                }
            }
            if (nameString.length() == 0) {
                nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
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
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - timeWidth;
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

        if (dialogMuted && !drawVerified) {
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
        }

        nameWidth = Math.max(AndroidUtilities.dp(12), nameWidth);
        try {
            CharSequence nameStringFinal = TextUtils.ellipsize(nameString.replace('\n', ' '), currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        int messageWidth = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 16);
        int avatarLeft;
        if (!LocaleController.isRTL) {
            messageLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            avatarLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 13 : 9);
        } else {
            messageLeft = AndroidUtilities.dp(16);
            avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 65 : 61);
        }
        avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(52), AndroidUtilities.dp(52));
        if (drawError) {
            int w = AndroidUtilities.dp(23 + 8);
            messageWidth -= w;
            if (!LocaleController.isRTL) {
                errorLeft = getMeasuredWidth() - AndroidUtilities.dp(23 + 11);
            } else {
                errorLeft = AndroidUtilities.dp(11);
                messageLeft += w;
            }
        } else if (countString != null || mentionString != null) {
            if (countString != null) {
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + AndroidUtilities.dp(18);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(19);
                } else {
                    countLeft = AndroidUtilities.dp(19);
                    messageLeft += w;
                }
                drawCount = true;
            } else {
                countWidth = 0;
            }
            if (mentionString != null) {
                mentionWidth = AndroidUtilities.dp(12);
                int w = mentionWidth + AndroidUtilities.dp(18);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    mentionLeft = getMeasuredWidth() - mentionWidth - AndroidUtilities.dp(19) - (countWidth != 0 ? countWidth + AndroidUtilities.dp(18) : 0);
                } else {
                    mentionLeft = AndroidUtilities.dp(19) + (countWidth != 0 ? countWidth + AndroidUtilities.dp(18) : 0);
                    messageLeft += w;
                }
                drawMention = true;
            }
        } else {
            if (drawPin) {
                int w = Theme.dialogs_pinnedDrawable.getIntrinsicWidth() + AndroidUtilities.dp(8);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    pinLeft = getMeasuredWidth() - Theme.dialogs_pinnedDrawable.getIntrinsicWidth() - AndroidUtilities.dp(14);
                } else {
                    pinLeft = AndroidUtilities.dp(14);
                    messageLeft += w;
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
            mess = mess.replace('\n', ' ');
            messageString = Emoji.replaceEmoji(mess, Theme.dialogs_messagePaint.getFontMetricsInt(), AndroidUtilities.dp(17), false);
        }
        messageWidth = Math.max(AndroidUtilities.dp(12), messageWidth);
        CharSequence messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        try {
            messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                widthpx = Math.ceil(nameLayout.getLineWidth(0));
                if (dialogMuted && !drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                } else if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                }
                if (left == 0) {
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (messageLayout != null && messageLayout.getLineCount() > 0) {
                left = messageLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(messageLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageLeft += (messageWidth - widthpx);
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
                if (dialogMuted || drawVerified) {
                    nameMuteLeft = (int) (nameLeft + left + AndroidUtilities.dp(6));
                }
            }
            if (messageLayout != null && messageLayout.getLineCount() > 0) {
                left = messageLayout.getLineRight(0);
                if (left == messageWidth) {
                    widthpx = Math.ceil(messageLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageLeft -= (messageWidth - widthpx);
                    }
                }
            }
        }
    }

    public void setDialogSelected(boolean value) {
        if (isSelected != value) {
            invalidate();
        }
        isSelected = value;
    }

    private ArrayList<TLRPC.TL_dialog> getDialogsArray() {
        if (dialogsType == 0) {
            return MessagesController.getInstance().dialogs;
        } else if (dialogsType == 1) {
            return MessagesController.getInstance().dialogsServerOnly;
        } else if (dialogsType == 2) {
            return MessagesController.getInstance().dialogsGroupsOnly;
        } else if (dialogsType == 3) {
            return MessagesController.getInstance().dialogsForward;
        }
        return null;
    }

    public void checkCurrentDialogIndex() {
        if (index < getDialogsArray().size()) {
            TLRPC.TL_dialog dialog = getDialogsArray().get(index);
            TLRPC.DraftMessage newDraftMessage = DraftQuery.getDraft(currentDialogId);
            MessageObject newMessageObject = MessagesController.getInstance().dialogMessage.get(dialog.id);
            if (currentDialogId != dialog.id ||
                    message != null && message.getId() != dialog.top_message ||
                    newMessageObject != null && newMessageObject.messageOwner.edit_date != currentEditDate ||
                    unreadCount != dialog.unread_count ||
                    mentionCount != dialog.unread_mentions_count ||
                    message != newMessageObject ||
                    message == null && newMessageObject != null || newDraftMessage != draftMessage || drawPin != dialog.pinned) {
                currentDialogId = dialog.id;
                update(0);
            }
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null) {
            return;
        }
        checkBox.setChecked(checked, animated);
    }

    public void update(int mask) {
        if (customDialog != null) {
            lastMessageDate = customDialog.date;
            lastUnreadState = customDialog.unread_count != 0;
            unreadCount = customDialog.unread_count;
            drawPin = customDialog.pinned;
            dialogMuted = customDialog.muted;
            avatarDrawable.setInfo(customDialog.id, customDialog.name, null, false);
            avatarImage.setImage((TLObject) null, "50_50", avatarDrawable, null, 0);
        } else {
            if (isDialogCell) {
                TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(currentDialogId);
                if (dialog != null && mask == 0) {
                    message = MessagesController.getInstance().dialogMessage.get(dialog.id);
                    lastUnreadState = message != null && message.isUnread();
                    unreadCount = dialog.unread_count;
                    mentionCount = dialog.unread_mentions_count;
                    currentEditDate = message != null ? message.messageOwner.edit_date : 0;
                    lastMessageDate = dialog.last_message_date;
                    drawPin = dialog.pinned;
                    if (message != null) {
                        lastSendState = message.messageOwner.send_state;
                    }
                }
            } else {
                drawPin = false;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if (isDialogCell) {
                    if ((mask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                        CharSequence printString = MessagesController.getInstance().printingStrings.get(currentDialogId);
                        if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                            continueUpdate = true;
                        }
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
                        TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(currentDialogId);
                        if (dialog != null && (unreadCount != dialog.unread_count || mentionCount != dialog.unread_mentions_count)) {
                            unreadCount = dialog.unread_count;
                            mentionCount = dialog.unread_mentions_count;
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
                    return;
                }
            }

            dialogMuted = isDialogCell && MessagesController.getInstance().isDialogMuted(currentDialogId);
            user = null;
            chat = null;
            encryptedChat = null;

            int lower_id = (int) currentDialogId;
            int high_id = (int) (currentDialogId >> 32);
            if (lower_id != 0) {
                if (high_id == 1) {
                    chat = MessagesController.getInstance().getChat(lower_id);
                } else {
                    if (lower_id < 0) {
                        chat = MessagesController.getInstance().getChat(-lower_id);
                        if (!isDialogCell && chat != null && chat.migrated_to != null) {
                            TLRPC.Chat chat2 = MessagesController.getInstance().getChat(chat.migrated_to.channel_id);
                            if (chat2 != null) {
                                chat = chat2;
                            }
                        }
                    } else {
                        user = MessagesController.getInstance().getUser(lower_id);
                    }
                }
            } else {
                encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
                if (encryptedChat != null) {
                    user = MessagesController.getInstance().getUser(encryptedChat.user_id);
                }
            }

            TLRPC.FileLocation photo = null;
            if (user != null) {
                avatarDrawable.setInfo(user);
                if (UserObject.isUserSelf(user)) {
                    avatarDrawable.setSavedMessages(1);
                } else if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
            } else if (chat != null) {
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                }
                avatarDrawable.setInfo(chat);
            }
            avatarImage.setImage(photo, "50_50", avatarDrawable, null, 0);
        }
        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentDialogId == 0 && customDialog == null) {
            return;
        }

        if (isSelected) {
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_tabletSeletedPaint);
        }
        if (drawPin || drawPinBackground) {
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
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
            canvas.save();
            canvas.translate(nameLeft, AndroidUtilities.dp(13));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (timeLayout != null) {
            canvas.save();
            canvas.translate(timeLeft, timeTop);
            timeLayout.draw(canvas);
            canvas.restore();
        }

        if (messageLayout != null) {
            canvas.save();
            canvas.translate(messageLeft, messageTop);
            try {
                messageLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();
        }

        if (drawClock) {
            setDrawableBounds(Theme.dialogs_clockDrawable, checkDrawLeft, checkDrawTop);
            Theme.dialogs_clockDrawable.draw(canvas);
        } else if (drawCheck2) {
            if (drawCheck1) {
                setDrawableBounds(Theme.dialogs_halfCheckDrawable, halfCheckDrawLeft, checkDrawTop);
                Theme.dialogs_halfCheckDrawable.draw(canvas);
                setDrawableBounds(Theme.dialogs_checkDrawable, checkDrawLeft, checkDrawTop);
                Theme.dialogs_checkDrawable.draw(canvas);
            } else {
                setDrawableBounds(Theme.dialogs_checkDrawable, checkDrawLeft, checkDrawTop);
                Theme.dialogs_checkDrawable.draw(canvas);
            }
        }

        if (dialogMuted && !drawVerified) {
            setDrawableBounds(Theme.dialogs_muteDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            Theme.dialogs_muteDrawable.draw(canvas);
        } else if (drawVerified) {
            setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            Theme.dialogs_verifiedDrawable.draw(canvas);
            Theme.dialogs_verifiedCheckDrawable.draw(canvas);
        }

        if (drawError) {
            rect.set(errorLeft, errorTop, errorLeft + AndroidUtilities.dp(23), errorTop + AndroidUtilities.dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_errorPaint);
            setDrawableBounds(Theme.dialogs_errorDrawable, errorLeft + AndroidUtilities.dp(5.5f), errorTop + AndroidUtilities.dp(5));
            Theme.dialogs_errorDrawable.draw(canvas);
        } else if (drawCount || drawMention) {
            if (drawCount) {
                int x = countLeft - AndroidUtilities.dp(5.5f);
                rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, dialogMuted ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint);
                canvas.save();
                canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
                if (countLayout != null) {
                    countLayout.draw(canvas);
                }
                canvas.restore();
            }
            if (drawMention) {
                int x = mentionLeft - AndroidUtilities.dp(5.5f);
                rect.set(x, countTop, x + mentionWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_countPaint);
                setDrawableBounds(Theme.dialogs_mentionDrawable, mentionLeft - AndroidUtilities.dp(2), countTop + AndroidUtilities.dp(3.2f), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                Theme.dialogs_mentionDrawable.draw(canvas);
            }
        } else if (drawPin) {
            setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop);
            Theme.dialogs_pinnedDrawable.draw(canvas);
        }

        if (useSeparator) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        avatarImage.draw(canvas);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
