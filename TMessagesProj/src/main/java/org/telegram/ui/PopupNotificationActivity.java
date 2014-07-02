/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.ui.Views.ActionBar.ActionBar;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.ChatActivityEnterView;

public class PopupNotificationActivity extends Activity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarLayer actionBarLayer;
    private ChatActivityEnterView chatActivityEnterView;
    private BackupImageView avatarImageView;
    private TextView messageText;
    private TextView countText;
    private View textScroll;

    private int classGuid;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private boolean finished = false;
    private CharSequence lastPrintString;
    private MessageObject currentMessageObject = null;
    private int currentMessageNum = 0;
    private PowerManager.WakeLock wakeLock = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
        NotificationCenter.getInstance().addObserver(this, 1234);
        NotificationCenter.getInstance().addObserver(this, MessagesController.pushMessagesUpdated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);

        chatActivityEnterView = new ChatActivityEnterView();
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend() {
                MessagesController.getInstance().pushMessages.remove(0);
                currentMessageObject = null;
                getNewMessage();
                //MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, wasUnread);
            }

            @Override
            public void needSendTyping() {
                if (currentMessageObject != null) {
                    MessagesController.getInstance().sendTyping(currentMessageObject.getDialogId(), classGuid);
                }
            }
        });

        setContentView(R.layout.popup_notification_layout);
        RelativeLayout popupContainer = (RelativeLayout) findViewById(R.id.popup_container);
        messageText = (TextView)findViewById(R.id.message_text);
        View messageContainer = findViewById(R.id.text_container);
        messageContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCurrentMessage();
            }
        });

        ActionBar actionBar = new ActionBar(this);
        popupContainer.addView(actionBar);
        ViewGroup.LayoutParams layoutParams = actionBar.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);

        actionBarLayer = actionBar.createLayer();
        actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
        actionBarLayer.setBackgroundResource(R.color.header);
        actionBarLayer.setItemsBackground(R.drawable.bar_selector);
        actionBar.setCurrentActionBarLayer(actionBarLayer);

        ActionBarMenu menu = actionBarLayer.createMenu();
        View view = menu.addItemResource(2, R.layout.popup_count_layout);
        countText = (TextView) view.findViewById(R.id.count_text);

        view = menu.addItemResource(1, R.layout.chat_header_layout);
        avatarImageView = (BackupImageView)view.findViewById(R.id.chat_avatar_image);
        avatarImageView.processDetach = false;

        actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finish();
                } else if (id == 1) {
                    openCurrentMessage();
                } else if (id == 2) {
                    if (MessagesController.getInstance().pushMessages.size() > 1) {
                        if (currentMessageNum < MessagesController.getInstance().pushMessages.size() - 1) {
                            currentMessageNum++;
                        } else {
                            currentMessageNum = 0;
                        }
                        currentMessageObject = MessagesController.getInstance().pushMessages.get(currentMessageNum);
                        updateInterfaceForCurrentMessage();
                        countText.setText(String.format("%d/%d", currentMessageNum + 1, MessagesController.getInstance().pushMessages.size()));
                    }
                }
            }
        });

        chatActivityEnterView.setContainerView(this, findViewById(R.id.chat_layout));

        PowerManager pm = (PowerManager)ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "screen");

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    public void handleIntent(Intent intent) {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode() || !ApplicationLoader.isScreenOn) {
            getWindow().addFlags(
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else {
            getWindow().addFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        if (currentMessageObject == null) {
            currentMessageNum = 0;
            getNewMessage();
        }
        wakeLock.acquire(7000);
    }

    private void getNewMessage() {
        if (MessagesController.getInstance().pushMessages.isEmpty()) {
            finish();
            return;
        }

        boolean found = false;
        if ((currentMessageNum != 0 || chatActivityEnterView.hasText()) && currentMessageObject != null) {
            for (int a = 0; a < MessagesController.getInstance().pushMessages.size(); a++) {
                if (MessagesController.getInstance().pushMessages.get(a).messageOwner.id == currentMessageObject.messageOwner.id) {
                    currentMessageNum = a;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            currentMessageObject = MessagesController.getInstance().pushMessages.get(0);
            updateInterfaceForCurrentMessage();
        }
        countText.setText(String.format("%d/%d", currentMessageNum + 1, MessagesController.getInstance().pushMessages.size()));
    }

    private void openCurrentMessage() {
        if (currentMessageObject == null) {
            return;
        }
        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        long dialog_id = currentMessageObject.getDialogId();
        if ((int)dialog_id != 0) {
            int lower_id = (int)dialog_id;
            if (lower_id < 0) {
                intent.putExtra("chatId", -lower_id);
            } else {
                intent.putExtra("userId", lower_id);
            }
        } else {
            intent.putExtra("encId", (int)(dialog_id >> 32));
        }
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(0x00008000);
        startActivity(intent);
    }

    private void updateInterfaceForCurrentMessage() {
        if (actionBarLayer == null) {
            return;
        }
        currentChat = null;
        currentUser = null;
        long dialog_id = currentMessageObject.getDialogId();
        chatActivityEnterView.setDialogId(dialog_id);
        if ((int)dialog_id != 0) {
            int lower_id = (int)dialog_id;
            if (lower_id > 0) {
                currentUser = MessagesController.getInstance().users.get(lower_id);
            } else {
                currentChat = MessagesController.getInstance().chats.get(-lower_id);
                currentUser = MessagesController.getInstance().users.get(currentMessageObject.messageOwner.from_id);
            }
        } else {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().encryptedChats.get((int)(dialog_id >> 32));
            currentUser = MessagesController.getInstance().users.get(encryptedChat.user_id);
        }

        if (currentChat != null && currentUser != null) {
            actionBarLayer.setTitle(currentChat.title);
            actionBarLayer.setSubtitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
        } else if (currentUser != null) {
            actionBarLayer.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
            if ((int)dialog_id == 0) {
                actionBarLayer.setTitleIcon(R.drawable.ic_lock_white, AndroidUtilities.dp(4));
            } else {
                actionBarLayer.setTitleIcon(0, 0);
            }
        }
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, MessagesController.getInstance().fontSize);
        messageText.setText(currentMessageObject.messageText);
        updateSubtitle();
        checkAndUpdateAvatar();
    }

    private void updateSubtitle() {
        if (actionBarLayer == null) {
            return;
        }
        if (currentChat != null || currentUser == null) {
            return;
        }
        if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
            if (currentUser.phone != null && currentUser.phone.length() != 0) {
                actionBarLayer.setTitle(PhoneFormat.getInstance().format("+" + currentUser.phone));
            } else {
                actionBarLayer.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
            }
        } else {
            actionBarLayer.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
        }
        CharSequence printString = MessagesController.getInstance().printingStrings.get(currentMessageObject.getDialogId());
        if (printString == null || printString.length() == 0) {
            lastPrintString = null;
            setTypingAnimation(false);
            TLRPC.User user = MessagesController.getInstance().users.get(currentUser.id);
            if (user != null) {
                currentUser = user;
            }
            actionBarLayer.setSubtitle(LocaleController.formatUserStatus(currentUser));
        } else {
            lastPrintString = printString;
            actionBarLayer.setSubtitle(printString);
            setTypingAnimation(true);
        }
    }

    private void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        int placeHolderId = 0;
        if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance().chats.get(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
            placeHolderId = Utilities.getGroupAvatarForId(currentChat.id);
        } else if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance().users.get(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            }
            placeHolderId = Utilities.getUserAvatarForId(currentUser.id);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", placeHolderId);
        }
    }

    private void setTypingAnimation(boolean start) {
        if (actionBarLayer == null) {
            return;
        }
        if (start) {
            try {
                actionBarLayer.setSubTitleIcon(R.drawable.typing_dots, AndroidUtilities.dp(4));
                AnimationDrawable mAnim = (AnimationDrawable)actionBarLayer.getSubTitleIcon();
                mAnim.setAlpha(200);
                mAnim.start();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            actionBarLayer.setSubTitleIcon(0, 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (chatActivityEnterView.isEmojiPopupShowing()) {
            chatActivityEnterView.hideEmojiPopup();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setFieldFocused(true);
        }
        ConnectionsManager.getInstance().setAppPaused(false, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.hideEmojiPopup();
            chatActivityEnterView.setFieldFocused(false);
        }
        ConnectionsManager.getInstance().setAppPaused(true, false);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == 1234) {
            onFinish();
            finish();
        } else if (id == MessagesController.pushMessagesUpdated) {
            getNewMessage();
        } else if (id == MessagesController.updateInterfaces) {
            if (currentMessageObject == null) {
                return;
            }
            int updateMask = (Integer)args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0) {
                updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0) {
                checkAndUpdateAvatar();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance().printingStrings.get(currentMessageObject.getDialogId());
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onFinish();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    protected void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        NotificationCenter.getInstance().removeObserver(this, 1234);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.pushMessagesUpdated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
