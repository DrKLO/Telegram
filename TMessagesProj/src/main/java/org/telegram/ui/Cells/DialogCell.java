/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ImageListActivity;

import java.util.ArrayList;

public class DialogCell extends BaseCell {

    private static TextPaint namePaint;
    private static TextPaint groupPaint;
    private static TextPaint nameEncryptedPaint;
    private static TextPaint nameUnknownPaint;
    private static TextPaint messagePaint;
    private static TextPaint messagePrintingPaint;
    private static TextPaint messageTypingPaint;
    private static TextPaint timePaint;
    private static TextPaint countPaint;

    private static TextPaint mediaPaint;

    private static Drawable checkDrawable;
    private static Drawable halfCheckDrawable;
    private static Drawable clockDrawable;
    private static Drawable errorDrawable;
    private static Drawable lockDrawable;
    private static Drawable countDrawable;
    private static Drawable countDrawableGrey;
    private static Drawable groupDrawable;
    private static Drawable superGroupDrawable;
    private static Drawable broadcastDrawable;
    private static Drawable botDrawable;
    private static Drawable muteDrawable;
    private static Drawable verifiedDrawable;

    private static Paint linePaint;
    private static Paint backPaint;

    private long currentDialogId;
    private boolean isDialogCell;
    private int lastMessageDate;
    private int unreadCount;
    private boolean lastUnreadState;
    private int lastSendState;
    private boolean dialogMuted;
    private MessageObject message;
    private int index;
    private int dialogsType;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;

    private TLRPC.User user = null;
    private TLRPC.Chat chat = null;
    private TLRPC.EncryptedChat encryptedChat = null;
    private CharSequence lastPrintString = null;

    public boolean useSeparator = false;

    private int nameLeft;
    private int nameTop = AndroidUtilities.dp(13);
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

    private boolean drawCount;
    private int countTop = AndroidUtilities.dp(39);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean drawVerified;

    private int avatarTop = AndroidUtilities.dp(10);

    private boolean isSelected;

    private int avatarSize = AndroidUtilities.dp(52);

    private int avatarLeftMargin;

    private GradientDrawable statusBG;
    private boolean drawStatus;

    private boolean twoLinesMsg = false;

    public DialogCell(Context context) {
        super(context);

        if (namePaint == null) {
            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(AndroidUtilities.dp(17));
            namePaint.setColor(0xff212121);
            namePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            groupPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            groupPaint.setTextSize(AndroidUtilities.dp(17));
            groupPaint.setColor(0xff212121);
            groupPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            nameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            nameEncryptedPaint.setTextSize(AndroidUtilities.dp(17));
            nameEncryptedPaint.setColor(0xff00a60e);
            nameEncryptedPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            nameUnknownPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            nameUnknownPaint.setTextSize(AndroidUtilities.dp(17));
            nameUnknownPaint.setColor(0xff4d83b3);
            nameUnknownPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            messagePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            messagePaint.setTextSize(AndroidUtilities.dp(16));
            messagePaint.setColor(0xff8f8f8f);
            messagePaint.linkColor = 0xff8f8f8f;

            linePaint = new Paint();
            linePaint.setColor(0xffdcdcdc);

            backPaint = new Paint();
            backPaint.setColor(0x0f000000);

            messagePrintingPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            messagePrintingPaint.setTextSize(AndroidUtilities.dp(16));
            messagePrintingPaint.setColor(0xff4d83b3);

            messageTypingPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            messageTypingPaint.setTextSize(AndroidUtilities.dp(16));
            messageTypingPaint.setColor(0xff4d83b3);

            mediaPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            mediaPaint.setTextSize(AndroidUtilities.dp(16));
            mediaPaint.setColor(0xff00ff00);

            timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaint.setTextSize(AndroidUtilities.dp(13));
            timePaint.setColor(0xff999999);

            countPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            countPaint.setTextSize(AndroidUtilities.dp(13));
            countPaint.setColor(0xffffffff);
            countPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            lockDrawable = getResources().getDrawable(R.drawable.list_secret);
            setChecks(context);
            //checkDrawable = getResources().getDrawable(R.drawable.dialogs_check);
            //halfCheckDrawable = getResources().getDrawable(R.drawable.dialogs_halfcheck);
            clockDrawable = getResources().getDrawable(R.drawable.msg_clock);
            errorDrawable = getResources().getDrawable(R.drawable.dialogs_warning);
            countDrawable = getResources().getDrawable(R.drawable.dialogs_badge);
            countDrawableGrey = getResources().getDrawable(R.drawable.dialogs_badge2);
            groupDrawable = getResources().getDrawable(R.drawable.list_group);
            superGroupDrawable = getResources().getDrawable(R.drawable.list_supergroup);
            broadcastDrawable = getResources().getDrawable(R.drawable.list_broadcast);
            muteDrawable = getResources().getDrawable(R.drawable.mute_grey);
            verifiedDrawable = getResources().getDrawable(R.drawable.check_list);
            botDrawable = getResources().getDrawable(R.drawable.bot_list);
        }
        
        setBackgroundResource(R.drawable.list_selector);

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(26));
        avatarDrawable = new AvatarDrawable();

        statusBG = new GradientDrawable();
        statusBG.setColor(Color.GRAY);
        statusBG.setCornerRadius(AndroidUtilities.dp(16));
        statusBG.setStroke(AndroidUtilities.dp(2), Color.WHITE);
    }

    private static void setChecks(Context context) {
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        String check = themePrefs.getString("chatCheckStyle", ImageListActivity.getCheckName(0));
        if (check.equals(ImageListActivity.getCheckName(1))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_2);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_2);
        } else if (check.equals(ImageListActivity.getCheckName(2))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_3);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_3);
        } else if (check.equals(ImageListActivity.getCheckName(3))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_4);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_4);
        } else if (check.equals(ImageListActivity.getCheckName(4))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_5);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_5);
        } else if (check.equals(ImageListActivity.getCheckName(5))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_6);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_6);
        } else if (check.equals(ImageListActivity.getCheckName(6))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_7);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_7);
        } else if (check.equals(ImageListActivity.getCheckName(7))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_8);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_8);
        } else if (check.equals(ImageListActivity.getCheckName(8))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_9);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_9);
        } else if (check.equals(ImageListActivity.getCheckName(9))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_10);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_10);
        } else if (check.equals(ImageListActivity.getCheckName(10))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_11);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_11);
        } else if (check.equals(ImageListActivity.getCheckName(11))) {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check_12);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck_12);
        } else {
            checkDrawable = context.getResources().getDrawable(R.drawable.dialogs_check);
            halfCheckDrawable = context.getResources().getDrawable(R.drawable.dialogs_halfcheck);
        }
    }

    public void setDialog(TLRPC.Dialog dialog, int i, int type) {
        currentDialogId = dialog.id;
        isDialogCell = true;
        index = i;
        dialogsType = type;
        update(0);
    }

    public void setDialog(long dialog_id, MessageObject messageObject, int date) {
        currentDialogId = dialog_id;
        message = messageObject;
        isDialogCell = false;
        lastMessageDate = date;
        unreadCount = 0;
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(72) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentDialogId == 0) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        if (changed) {
            buildLayout();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getBackground().setHotspot(event.getX(), event.getY());
            }
        }
        return super.onTouchEvent(event);
    }

    public void buildLayout() {
        String nameString = "";
        String timeString = "";
        String countString = null;
        CharSequence messageString = "";
        CharSequence printingString = null;
        if (isDialogCell) {
            printingString = MessagesController.getInstance().printingStrings.get(currentDialogId);
        }
        TextPaint currentNamePaint = namePaint;
        TextPaint currentMessagePaint = messagePaint;
        boolean checkMessage = true;

        drawNameGroup = false;
        drawNameBroadcast = false;
        drawNameLock = false;
        drawNameBot = false;
        drawVerified = false;

        drawStatus = false;

        if (encryptedChat != null) {
            drawNameLock = true;
            nameLockTop = AndroidUtilities.dp(16.5f);
            if (!LocaleController.isRTL) {
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + lockDrawable.getIntrinsicWidth();
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - lockDrawable.getIntrinsicWidth();
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
                    //nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? groupDrawable.getIntrinsicWidth() : broadcastDrawable.getIntrinsicWidth());
                    nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? chat.megagroup ? superGroupDrawable.getIntrinsicWidth() : groupDrawable.getIntrinsicWidth() : broadcastDrawable.getIntrinsicWidth());
                } else {
                    //nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - (drawNameGroup ? groupDrawable.getIntrinsicWidth() : broadcastDrawable.getIntrinsicWidth());
                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - (drawNameGroup ?  chat.megagroup ? superGroupDrawable.getIntrinsicWidth() : groupDrawable.getIntrinsicWidth() : broadcastDrawable.getIntrinsicWidth());
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
                            nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + botDrawable.getIntrinsicWidth();
                        } else {
                            nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - botDrawable.getIntrinsicWidth();
                            nameLeft = AndroidUtilities.dp(14);
                        }
                    }
                    drawVerified = user.verified;
                }
            }
        }

        if (message == null) {
            if (printingString != null) {
                lastPrintString = messageString = printingString;
                //currentMessagePaint = messagePrintingPaint;
                currentMessagePaint = messageTypingPaint;
            } else {
                lastPrintString = null;
                if (encryptedChat != null) {
                    currentMessagePaint = messagePrintingPaint;
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
            }
            if (lastMessageDate != 0) {
                timeString = LocaleController.stringForMessageListDate(lastMessageDate);
            }
            drawCheck1 = false;
            drawCheck2 = false;
            drawClock = false;
            drawCount = false;
            drawError = false;
        } else {
            TLRPC.User fromUser = null;
            TLRPC.Chat fromChat = null;
            if (message.isFromUser()) {
                fromUser = MessagesController.getInstance().getUser(message.messageOwner.from_id);
            } else {
                fromChat = MessagesController.getInstance().getChat(message.messageOwner.to_id.channel_id);
            }

            if (lastMessageDate != 0) {
                timeString = LocaleController.stringForMessageListDate(lastMessageDate);
            } else {
                timeString = LocaleController.stringForMessageListDate(message.messageOwner.date);
            }
            if (printingString != null) {
                lastPrintString = messageString = printingString;
                //currentMessagePaint = messagePrintingPaint;
                currentMessagePaint = messageTypingPaint;
            } else {
                lastPrintString = null;
                if (message.messageOwner instanceof TLRPC.TL_messageService) {
                    messageString = message.messageText;
                    currentMessagePaint = messagePrintingPaint;
                } else {
                    if (chat != null && chat.id > 0 && fromChat == null) {
                        String name;
                        if (message.isOutOwner()) {
                            name = LocaleController.getString("FromYou", R.string.FromYou);
                        } else if (fromUser != null) {
                            name = UserObject.getFirstName(fromUser);
                        } else if (fromChat != null) {
                            name = fromChat.title;
                        } else {
                            name = "DELETED";
                        }
                        checkMessage = false;
                        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);

                        String hexMsgColor = String.format("#%08X", themePrefs.getInt("chatsMessageColor", 0xff808080));
                        int darkColor = themePrefs.getInt("chatsMemberColor", AndroidUtilities.getIntDarkerColor("themeColor", 0x15));
                        String hexDarkColor = String.format("#%08X", darkColor);
                        String hexMediaColor = String.format("#%08X", themePrefs.getInt("chatsMediaColor", darkColor));
                        if (message.caption != null) {
                            String mess = message.caption.toString();
                            if (mess.length() > 150) {
                                mess = mess.substring(0, 150);
                            }
                            mess = mess.replace('\n', ' ');
                            //messageString = Emoji.replaceEmoji(AndroidUtilities.replaceTags(String.format("<c#ff4d83b3>%s:</c> <c#ff808080>%s</c>", name.replace("\n", ""), mess), AndroidUtilities.FLAG_TAG_COLOR), messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                            messageString = Emoji.replaceEmoji(AndroidUtilities.replaceTags(String.format("<c" + hexDarkColor + ">%s:</c> <c" + hexMsgColor + ">%s</c>", name.replace("\n", ""), mess), AndroidUtilities.FLAG_TAG_COLOR), messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                        } else {
                            if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                currentMessagePaint = messagePrintingPaint;
                                //messageString = Emoji.replaceEmoji(AndroidUtilities.replaceTags(String.format("<c#ff4d83b3>%s:</c> <c#ff4d83b3>%s</c>", name.replace("\n", ""), message.messageText)), AndroidUtilities.FLAG_TAG_COLOR), messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                                messageString = Emoji.replaceEmoji(AndroidUtilities.replaceTags(String.format("<c" + hexDarkColor + ">%s:</c> <c" + hexMediaColor + ">%s</c>", name.replace("\n", ""), message.messageText), AndroidUtilities.FLAG_TAG_COLOR), messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                            } else {
                                if (message.messageOwner.message != null) {
                                    String mess = message.messageOwner.message;
                                    if (mess.length() > 150) {
                                        mess = mess.substring(0, 150);
                                    }
                                    mess = mess.replace('\n', ' ');
                                    //messageString = Emoji.replaceEmoji(AndroidUtilities.replaceTags(String.format("<c#ff4d83b3>%s:</c> <c#ff808080>%s</c>", name.replace("\n", ""), mess)), AndroidUtilities.FLAG_TAG_COLOR), messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                                    messageString = Emoji.replaceEmoji(AndroidUtilities.replaceTags(String.format("<c" + hexDarkColor + ">%s:</c> <c" + hexMsgColor + ">%s</c>", name.replace("\n", ""), mess), AndroidUtilities.FLAG_TAG_COLOR), messagePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                                }
                            }
                        }
                    } else {
                        if (message.caption != null) {
                            messageString = message.caption;
                        } else {
                            messageString = message.messageText;
                            if (message.messageOwner.media != null && !message.isMediaEmpty()) {
                                //currentMessagePaint = messagePrintingPaint;
                                currentMessagePaint = mediaPaint;
                            }
                        }
                    }
                }
            }

            if (unreadCount != 0) {
                drawCount = true;
                countString = String.format("%d", unreadCount);
            } else {
                drawCount = false;
            }

            if (message.isOut()) {
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
                } else if (message.isSent()) {
                    if (!message.isUnread()) {
                        drawCheck1 = true;
                        drawCheck2 = true;
                    } else {
                        drawCheck1 = false;
                        drawCheck2 = true;
                    }
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

        int timeWidth = (int) Math.ceil(timePaint.measureText(timeString));
        timeLayout = new StaticLayout(timeString, timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        if (!LocaleController.isRTL) {
            timeLeft = getMeasuredWidth() - AndroidUtilities.dp(15) - timeWidth;
        } else {
            timeLeft = AndroidUtilities.dp(15);
        }

        if (chat != null) {
            nameString = chat.title;
            currentNamePaint = groupPaint;
        } else if (user != null) {
            if (user.id / 1000 != 777 && user.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(user.id) == null) {
                if (ContactsController.getInstance().contactsDict.size() == 0 && (!ContactsController.getInstance().contactsLoaded || ContactsController.getInstance().isLoadingContacts())) {
                    nameString = UserObject.getUserName(user);
                } else {
                    if (user.phone != null && user.phone.length() != 0) {
                        nameString = PhoneFormat.getInstance().format("+" + user.phone);
                    } else {
                        currentNamePaint = nameUnknownPaint;
                        nameString = UserObject.getUserName(user);
                    }
                }
            } else {
                nameString = UserObject.getUserName(user);
            }
            if (encryptedChat != null) {
                currentNamePaint = nameEncryptedPaint;
            }
            if(!drawNameBot)drawStatus = true;
        }
        if (nameString.length() == 0) {
            nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
        }

        int nameWidth;

        if (!LocaleController.isRTL) {
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14) - timeWidth;
        } else {
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - timeWidth;
            nameLeft += timeWidth;
        }
        if (drawNameLock) {
            nameWidth -= AndroidUtilities.dp(4) + lockDrawable.getIntrinsicWidth();
        } else if (drawNameGroup) {
            //nameWidth -= AndroidUtilities.dp(4) + groupDrawable.getIntrinsicWidth();
            nameWidth -= AndroidUtilities.dp(4) + (chat.megagroup ? superGroupDrawable.getIntrinsicWidth() : groupDrawable.getIntrinsicWidth());
        } else if (drawNameBroadcast) {
            nameWidth -= AndroidUtilities.dp(4) + broadcastDrawable.getIntrinsicWidth();
        } else if (drawNameBot) {
            nameWidth -= AndroidUtilities.dp(4) + botDrawable.getIntrinsicWidth();
        }
        if (drawClock) {
            int w = clockDrawable.getIntrinsicWidth() + AndroidUtilities.dp(5);
            nameWidth -= w;
            if (!LocaleController.isRTL) {
                checkDrawLeft = timeLeft - w;
            } else {
                checkDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5);
                nameLeft += w;
            }
        } else if (drawCheck2) {
            int w = checkDrawable.getIntrinsicWidth() + AndroidUtilities.dp(5);
            nameWidth -= w;
            if (drawCheck1) {
                nameWidth -= halfCheckDrawable.getIntrinsicWidth() - AndroidUtilities.dp(8);
                if (!LocaleController.isRTL) {
                    halfCheckDrawLeft = timeLeft - w;
                    checkDrawLeft = halfCheckDrawLeft - AndroidUtilities.dp(5.5f);
                } else {
                    checkDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5);
                    halfCheckDrawLeft = checkDrawLeft + AndroidUtilities.dp(5.5f);
                    nameLeft += w + halfCheckDrawable.getIntrinsicWidth() - AndroidUtilities.dp(8);
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
            int w = AndroidUtilities.dp(6) + muteDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawVerified) {
            int w = AndroidUtilities.dp(6) + verifiedDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        }

        nameWidth = Math.max(AndroidUtilities.dp(12), nameWidth);
        CharSequence nameStringFinal = TextUtils.ellipsize(nameString.replace('\n', ' '), currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        try {
            nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        int messageWidth = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 16);
        int avatarLeft;
        if (!LocaleController.isRTL) {
            messageLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            //avatarLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 13 : 9);
            avatarLeft = avatarLeftMargin;
        } else {
            messageLeft = AndroidUtilities.dp(16);
            //avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 65 : 61);
            avatarLeft = getMeasuredWidth() - avatarSize - avatarLeftMargin;
        }
        //avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(52), AndroidUtilities.dp(52));
        avatarTop = (getMeasuredHeight() - (avatarSize)) / 2;
        avatarImage.setImageCoords(avatarLeft, avatarTop, avatarSize, avatarSize);
        if (drawError) {
            int w = errorDrawable.getIntrinsicWidth() + AndroidUtilities.dp(8);
            messageWidth -= w;
            if (!LocaleController.isRTL) {
                errorLeft = getMeasuredWidth() - errorDrawable.getIntrinsicWidth() - AndroidUtilities.dp(11);
            } else {
                errorLeft = AndroidUtilities.dp(11);
                messageLeft += w;
            }
        } else if (countString != null) {
            countWidth = Math.max(AndroidUtilities.dp(12), (int)Math.ceil(countPaint.measureText(countString)));
            countLayout = new StaticLayout(countString, countPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
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
            drawCount = false;
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
            messageString = Emoji.replaceEmoji(mess, messagePaint.getFontMetricsInt(), AndroidUtilities.dp(17), false);
        }
        messageWidth = Math.max(AndroidUtilities.dp(12), messageWidth);
        CharSequence messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        try {
            if(twoLinesMsg){
                messageLayout = new StaticLayout(messageString, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if(messageLayout.getLineCount() > 1){
                    messageTop = AndroidUtilities.dp(32);
                    nameTop = AndroidUtilities.dp(8);
                    timeTop = AndroidUtilities.dp(10);
                    checkDrawTop = AndroidUtilities.dp(12);
                    nameLockTop  = AndroidUtilities.dp(12);
                }else{
                    messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            }else{
                nameLockTop = AndroidUtilities.dp(16.5f);
                messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            //messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                if (dialogMuted && !drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - muteDrawable.getIntrinsicWidth());
                } else if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - verifiedDrawable.getIntrinsicWidth());
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

    private ArrayList<TLRPC.Dialog> getDialogsArray() {
        if (dialogsType == 0) {
            return MessagesController.getInstance().dialogs;
        } else if (dialogsType == 1) {
            return MessagesController.getInstance().dialogsServerOnly;
        } else if (dialogsType == 2) {
            return MessagesController.getInstance().dialogsGroupsOnly;
        }
        //plus
        else if (dialogsType == 3) {
            return MessagesController.getInstance().dialogsUsers;
        } else if (dialogsType == 4) {
            return MessagesController.getInstance().dialogsGroups;
        } else if (dialogsType == 5) {
            return MessagesController.getInstance().dialogsChannels;
        } else if (dialogsType == 6) {
            return MessagesController.getInstance().dialogsBots;
        } else if (dialogsType == 7) {
            return MessagesController.getInstance().dialogsMegaGroups;
        } else if (dialogsType == 8) {
            return MessagesController.getInstance().dialogsFavs;
        } else if (dialogsType == 9) {
            return MessagesController.getInstance().dialogsGroupsAll;
        }
        //
        return null;
    }

    public void checkCurrentDialogIndex() {
        if (index < getDialogsArray().size()) {
            TLRPC.Dialog dialog = getDialogsArray().get(index);
            if (currentDialogId != dialog.id || message != null && message.getId() != dialog.top_message || unreadCount != dialog.unread_count || message == null && MessagesController.getInstance().dialogMessage.get(dialog.id) != null) {
            currentDialogId = dialog.id;
            update(0);
        }
    }
    }

    public void update(int mask) {
        updateTheme();
        if (isDialogCell) {
            TLRPC.Dialog dialog = MessagesController.getInstance().dialogs_dict.get(currentDialogId);
            if (dialog != null && mask == 0) {
                message = MessagesController.getInstance().dialogMessage.get(dialog.id);
                lastUnreadState = message != null && message.isUnread();
                unreadCount = dialog.unread_count;
                lastMessageDate = dialog.last_message_date;
                if (message != null) {
                    lastSendState = message.messageOwner.send_state;
                }
            }
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
                    TLRPC.Dialog dialog = MessagesController.getInstance().dialogs_dict.get(currentDialogId);
                    if (dialog != null && unreadCount != dialog.unread_count) {
                        unreadCount = dialog.unread_count;
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

        int lower_id = (int)currentDialogId;
        int high_id = (int)(currentDialogId >> 32);
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
            if (user.photo != null) {
                photo = user.photo.photo_small;
            }
            avatarDrawable.setInfo(user);
            //Plus
            setStatusColor();
        } else if (chat != null) {
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
            }
            avatarDrawable.setInfo(chat);
        }
        avatarImage.setImage(photo, "50_50", avatarDrawable, null, false);

        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }

        invalidate();
    }

    private void setStatusColor(){
        String s = LocaleController.formatUserStatus(user);
        if (s.equals(LocaleController.getString("ALongTimeAgo", R.string.ALongTimeAgo))){
            statusBG.setColor(Color.BLACK);
        } else if(s.equals(LocaleController.getString("Online", R.string.Online))){
            statusBG.setColor(0xff00e676);
        } else if(s.equals(LocaleController.getString("Lately", R.string.Lately))){
            statusBG.setColor(Color.LTGRAY);
        } else {
            statusBG.setColor(Color.GRAY);
        }
        int l = user.status != null ? ConnectionsManager.getInstance().getCurrentTime() - user.status.expires : -2;
        if(l > 0 && l < 86400){
            statusBG.setColor(Color.LTGRAY);
        }
    }

    private void updateTheme(){
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int tColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
        int dColor = AndroidUtilities.getIntDarkerColor("themeColor", 0x15);

        int nColor = themePrefs.getInt("chatsNameColor", 0xff212121);

        namePaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsNameSize", 17)));
        namePaint.setColor(nColor);

        groupPaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsGroupNameSize", themePrefs.getInt("chatsNameSize", 17))));
        groupPaint.setColor(themePrefs.getInt("chatsGroupNameColor", nColor));

        lockDrawable.setColorFilter(themePrefs.getInt("chatsGroupIconColor", themePrefs.getInt("chatsNameColor", tColor)), PorterDuff.Mode.SRC_IN);

        nameEncryptedPaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsNameSize", 17)));
        nameEncryptedPaint.setColor(themePrefs.getInt("chatsNameColor", dColor));//0xff00a60e

        nameUnknownPaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsNameSize", 17)));
        nameUnknownPaint.setColor(themePrefs.getInt("chatsUnknownNameColor", nColor));//0xff4d83b3

        messagePaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsMessageSize", 16)));
        messagePaint.setColor(themePrefs.getInt("chatsMessageColor", 0xff8f8f8f));

        messagePrintingPaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsMessageSize", 16)));
        messagePrintingPaint.setColor(themePrefs.getInt("chatsMessageColor", tColor));

        mediaPaint.setColor(themePrefs.getInt("chatsMediaColor", dColor));

        messageTypingPaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsMessageSize", 16)));
        messageTypingPaint.setColor(themePrefs.getInt("chatsTypingColor", tColor));

        timePaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsTimeSize", 13)));
        timePaint.setColor(themePrefs.getInt("chatsTimeColor", 0xff999999));

        countPaint.setTextSize(AndroidUtilities.dp(themePrefs.getInt("chatsCountSize", 13)));
        countPaint.setColor(themePrefs.getInt("chatsCountColor", 0xffffffff));

        checkDrawable.setColorFilter(themePrefs.getInt("chatsChecksColor", tColor), PorterDuff.Mode.SRC_IN);
        halfCheckDrawable.setColorFilter(themePrefs.getInt("chatsChecksColor", tColor), PorterDuff.Mode.SRC_IN);
        clockDrawable.setColorFilter(themePrefs.getInt("chatsChecksColor", tColor), PorterDuff.Mode.SRC_IN);

        countDrawable.setColorFilter(themePrefs.getInt("chatsCountBGColor", tColor), PorterDuff.Mode.SRC_IN);
        countDrawableGrey.setColorFilter(themePrefs.getInt("chatsCountSilentBGColor", themePrefs.getInt("chatsCountBGColor", 0xffb9b9b9)), PorterDuff.Mode.SRC_IN);

        nColor = themePrefs.getInt("chatsGroupIconColor", themePrefs.getInt("chatsGroupNameColor", 0xff000000));
        groupDrawable.setColorFilter(nColor, PorterDuff.Mode.SRC_IN);
        superGroupDrawable.setColorFilter(nColor, PorterDuff.Mode.SRC_IN);
        broadcastDrawable.setColorFilter(nColor, PorterDuff.Mode.SRC_IN);
        botDrawable.setColorFilter(nColor, PorterDuff.Mode.SRC_IN);

        int mColor = themePrefs.getInt("chatsMuteColor", 0xffa8a8a8);
        //muteWhiteDrawable.setColorFilter(mColor, PorterDuff.Mode.MULTIPLY);
        muteDrawable.setColorFilter(mColor, PorterDuff.Mode.SRC_IN);

        linePaint.setColor(themePrefs.getInt("chatsDividerColor", 0xffdcdcdc));

        int radius = AndroidUtilities.dp(themePrefs.getInt("chatsAvatarRadius", 32));
        if(avatarImage != null)avatarImage.setRoundRadius(radius);
        if(avatarDrawable != null)avatarDrawable.setRadius(radius);
        avatarSize = AndroidUtilities.dp(themePrefs.getInt("chatsAvatarSize", 52));
        avatarLeftMargin = AndroidUtilities.dp(themePrefs.getInt("chatsAvatarMarginLeft", AndroidUtilities.isTablet() ? 13 : 9));

        statusBG.setStroke(AndroidUtilities.dp(2), themePrefs.getInt("chatsRowColor", 0xffffffff));
        setChecks(this.getContext());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentDialogId == 0) {
            return;
        }

        if (isSelected) {
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backPaint);
        }

        if (drawNameLock) {
            setDrawableBounds(lockDrawable, nameLockLeft, nameLockTop);
            lockDrawable.draw(canvas);
        } else if (drawNameGroup) {
            //setDrawableBounds(groupDrawable, nameLockLeft, nameLockTop);
            //groupDrawable.draw(canvas);
            if(chat.megagroup){
                setDrawableBounds(superGroupDrawable , nameLockLeft, nameLockTop);
                superGroupDrawable.draw(canvas);
            }else{
                setDrawableBounds(groupDrawable, nameLockLeft, nameLockTop);
                groupDrawable.draw(canvas);
            }
        } else if (drawNameBroadcast) {
            setDrawableBounds(broadcastDrawable, nameLockLeft, nameLockTop);
            broadcastDrawable.draw(canvas);
        } else if (drawNameBot) {
            setDrawableBounds(botDrawable, nameLockLeft, nameLockTop);
            botDrawable.draw(canvas);
        }

        if (nameLayout != null) {
            canvas.save();
            //canvas.translate(nameLeft, AndroidUtilities.dp(13));
            canvas.translate(nameLeft, nameTop);
            nameLayout.draw(canvas);
            canvas.restore();
        }

        canvas.save();
        canvas.translate(timeLeft, timeTop);
        timeLayout.draw(canvas);
        canvas.restore();

        if (messageLayout != null) {
            canvas.save();
            canvas.translate(messageLeft, messageTop);
            try {
            messageLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            canvas.restore();
        }

        if (drawClock) {
            setDrawableBounds(clockDrawable, checkDrawLeft, checkDrawTop);
            clockDrawable.draw(canvas);
        } else if (drawCheck2) {
            if (drawCheck1) {
                setDrawableBounds(halfCheckDrawable, halfCheckDrawLeft, checkDrawTop);
                halfCheckDrawable.draw(canvas);
                setDrawableBounds(checkDrawable, checkDrawLeft, checkDrawTop);
                checkDrawable.draw(canvas);
            } else {
                setDrawableBounds(checkDrawable, checkDrawLeft, checkDrawTop);
                checkDrawable.draw(canvas);
            }
        }

        if (dialogMuted && !drawVerified) {
            //setDrawableBounds(muteDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            setDrawableBounds(muteDrawable, nameMuteLeft, nameLockTop);
            muteDrawable.draw(canvas);
        } else if (drawVerified) {
            //setDrawableBounds(verifiedDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            setDrawableBounds(verifiedDrawable, nameMuteLeft, nameLockTop);
            verifiedDrawable.draw(canvas);
        }
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        if (drawError) {
            setDrawableBounds(errorDrawable, errorLeft, errorTop);
            errorDrawable.draw(canvas);
        } else if (drawCount) {
            if (dialogMuted) {
                setDrawableBounds(countDrawableGrey, countLeft - AndroidUtilities.dp(5.5f), countTop, countWidth + AndroidUtilities.dp(11), countDrawable.getIntrinsicHeight());
                countDrawableGrey.draw(canvas);
            } else {
                int size = themePrefs.getInt("chatsCountSize", 13);
                size = size > 13 ? (size - 13) / 2 : 0;
                //setDrawableBounds(countDrawable, countLeft - AndroidUtilities.dp(5.5f), countTop, countWidth + AndroidUtilities.dp(11), countDrawable.getIntrinsicHeight());
                setDrawableBounds(countDrawable, countLeft - AndroidUtilities.dp(5.5f), countTop + AndroidUtilities.dp(size), countWidth + AndroidUtilities.dp(11), countDrawable.getIntrinsicHeight());
                countDrawable.draw(canvas);
            }
            canvas.save();
            canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
            countLayout.draw(canvas);
            canvas.restore();
        }

        if (useSeparator) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, linePaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, linePaint);
            }
        }

        avatarImage.draw(canvas);

        if(drawStatus  && !themePrefs.getBoolean("chatsHideStatusIndicator", false)){
            setDrawableBounds(statusBG, AndroidUtilities.dp(36) + avatarLeftMargin, AndroidUtilities.dp(46), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            statusBG.draw(canvas);
        }

    }
}
