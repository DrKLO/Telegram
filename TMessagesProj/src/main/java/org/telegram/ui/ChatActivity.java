/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationsController;
import org.telegram.android.SecretChatHelper;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessageObject;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatAudioCell;
import org.telegram.ui.Cells.ChatBaseCell;
import org.telegram.ui.Cells.ChatContactCell;
import org.telegram.ui.Cells.ChatMediaCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.android.ImageReceiver;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.LayoutListView;
import org.telegram.ui.Components.SizeNotifierRelativeLayout;
import org.telegram.ui.Components.TimerDrawable;
import org.telegram.ui.Components.TypingDotsDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class ChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate,
        PhotoViewer.PhotoViewerProvider {

    private TLRPC.Chat currentChat;
    private TLRPC.User currentUser;
    private TLRPC.EncryptedChat currentEncryptedChat;
    private boolean userBlocked = false;

    private View progressView;
    private View bottomOverlay;
    private ChatAdapter chatAdapter;
    private ChatActivityEnterView chatActivityEnterView;
    private ImageView timeItem;
    private View timeItem2;
    private TimerDrawable timerDrawable;
    private ActionBarMenuItem menuItem;
    private ActionBarMenuItem attachItem;
    private ActionBarMenuItem headerItem;
    private TextView addContactItem;
    private LayoutListView chatListView;
    private BackupImageView avatarImageView;
    private TextView bottomOverlayChatText;
    private View bottomOverlayChat;
    private TypingDotsDrawable typingDotsDrawable;
    private View emptyViewContainer;
    private ArrayList<View> actionModeViews = new ArrayList<View>();
    private TextView nameTextView;
    private TextView onlineTextView;
    private FrameLayout avatarContainer;
    private TextView bottomOverlayText;
    private TextView secretViewStatusTextView;
    private TextView selectedMessagesCountTextView;

    private MessageObject selectedObject;
    private MessageObject forwaringMessage;
    private boolean paused = true;
    private boolean readWhenResume = false;

    private boolean openAnimationEnded = false;

    private int readWithDate = 0;
    private int readWithMid = 0;
    private boolean scrollToTopOnResume = false;
    private boolean scrollToTopUnReadOnResume = false;
    private boolean isCustomTheme = false;
    private View pagedownButton;
    private long dialog_id;
    private boolean isBroadcast = false;
    private HashMap<Integer, MessageObject> selectedMessagesIds = new HashMap<Integer, MessageObject>();
    private HashMap<Integer, MessageObject> selectedMessagesCanCopyIds = new HashMap<Integer, MessageObject>();

    private HashMap<Integer, MessageObject> messagesDict = new HashMap<Integer, MessageObject>();
    private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<String, ArrayList<MessageObject>>();
    private ArrayList<MessageObject> messages = new ArrayList<MessageObject>();
    private int maxMessageId = Integer.MAX_VALUE;
    private int minMessageId = Integer.MIN_VALUE;
    private int maxDate = Integer.MIN_VALUE;
    private boolean endReached = false;
    private boolean loading = false;
    private boolean cacheEndReaced = false;
    private boolean firstLoading = true;
    private int loadsCount = 0;

    private int startLoadFromMessageId = 0;

    private int minDate = 0;
    private boolean first = true;
    private int unread_to_load = 0;
    private int first_unread_id = 0;
    private int last_message_id = 0;
    private int first_message_id = 0;
    private boolean forward_end_reached = true;
    private boolean loadingForward = false;
    private MessageObject unreadMessageObject = null;
    private MessageObject scrollToMessage = null;
    private int highlightMessageId = Integer.MAX_VALUE;
    private boolean scrollToMessageMiddleScreen = false;

    private String currentPicturePath;

    private TLRPC.ChatParticipants info = null;
    private int onlineCount = -1;

    private CharSequence lastPrintString;
    private String lastStatus;

    private long chatEnterTime = 0;
    private long chatLeaveTime = 0;

    private String startVideoEdit = null;

    private Runnable openSecretPhotoRunnable = null;
    private float startX = 0;
    private float startY = 0;

    private final static int copy = 1;
    private final static int forward = 2;
    private final static int delete = 3;
    private final static int chat_enc_timer = 4;
    private final static int chat_menu_attach = 5;
    private final static int attach_photo = 6;
    private final static int attach_gallery = 7;
    private final static int attach_video = 8;
    private final static int attach_document = 9;
    private final static int attach_location = 10;
    private final static int clear_history = 11;
    private final static int delete_chat = 12;
    private final static int share_contact = 13;

    AdapterView.OnItemLongClickListener onItemLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long id) {
            if (!actionBar.isActionModeShowed()) {
                createMenu(view, false);
            }
            return true;
        }
    };

    AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            if (actionBar.isActionModeShowed()) {
                processRowSelect(view);
                return;
            }
            createMenu(view, true);
        }
    };

    public ChatActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        final int chatId = arguments.getInt("chat_id", 0);
        final int userId = arguments.getInt("user_id", 0);
        final int encId = arguments.getInt("enc_id", 0);
        startLoadFromMessageId = arguments.getInt("message_id", 0);
        scrollToTopOnResume = arguments.getBoolean("scrollToTopOnResume", false);

        if (chatId != 0) {
            currentChat = MessagesController.getInstance().getChat(chatId);
            if (currentChat == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentChat = MessagesStorage.getInstance().getChat(chatId);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentChat != null) {
                    MessagesController.getInstance().putChat(currentChat, true);
                } else {
                    return false;
                }
            }
            if (chatId > 0) {
                dialog_id = -chatId;
            } else {
                isBroadcast = true;
                dialog_id = AndroidUtilities.makeBroadcastId(chatId);
            }
            Semaphore semaphore = null;
            if (isBroadcast) {
                semaphore = new Semaphore(0);
            }
            MessagesController.getInstance().loadChatInfo(currentChat.id, semaphore);
            if (isBroadcast) {
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        } else if (userId != 0) {
            currentUser = MessagesController.getInstance().getUser(userId);
            if (currentUser == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentUser = MessagesStorage.getInstance().getUser(userId);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentUser != null) {
                    MessagesController.getInstance().putUser(currentUser, true);
                } else {
                    return false;
                }
            }
            dialog_id = userId;
        } else if (encId != 0) {
            currentEncryptedChat = MessagesController.getInstance().getEncryptedChat(encId);
            if (currentEncryptedChat == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentEncryptedChat = MessagesStorage.getInstance().getEncryptedChat(encId);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentEncryptedChat != null) {
                    MessagesController.getInstance().putEncryptedChat(currentEncryptedChat, true);
                } else {
                    return false;
                }
            }
            currentUser = MessagesController.getInstance().getUser(currentEncryptedChat.user_id);
            if (currentUser == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentUser = MessagesStorage.getInstance().getUser(currentEncryptedChat.user_id);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentUser != null) {
                    MessagesController.getInstance().putUser(currentUser, true);
                } else {
                    return false;
                }
            }
            dialog_id = ((long)encId) << 32;
            maxMessageId = Integer.MIN_VALUE;
            minMessageId = Integer.MAX_VALUE;
            MediaController.getInstance().startMediaObserver();
        } else {
            return false;
        }
        chatActivityEnterView = new ChatActivityEnterView();
        chatActivityEnterView.setDialogId(dialog_id);
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend() {
                chatListView.post(new Runnable() {
                    @Override
                    public void run() {
                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                    }
                });
            }

            @Override
            public void needSendTyping() {
                MessagesController.getInstance().sendTyping(dialog_id, classGuid);
            }

            @Override
            public void onAttachButtonHidden() {
                if (attachItem != null) {
                    attachItem.setVisibility(View.VISIBLE);
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onAttachButtonShow() {
                if (attachItem != null) {
                    attachItem.setVisibility(View.INVISIBLE);
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.VISIBLE);
                }
            }
        });
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByAck);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageSendError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesReadedEncrypted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.screenshotTook);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidStarted);

        super.onFragmentCreate();

        loading = true;

        if (startLoadFromMessageId != 0) {
            MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, true, 0, classGuid, 3, 0, 0, false);
        } else {
            MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, true, 0, classGuid, 2, 0, 0, true);
        }

        if (currentUser != null) {
            userBlocked = MessagesController.getInstance().blockedUsers.contains(currentUser.id);
        }

        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, false);
        }

        typingDotsDrawable = new TypingDotsDrawable();
        typingDotsDrawable.setIsChat(currentChat != null);

        if (currentEncryptedChat != null && AndroidUtilities.getMyLayerVersion(currentEncryptedChat.layer) != SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
            SecretChatHelper.getInstance().sendNotifyLayerMessage(currentEncryptedChat, null);
        }

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageReceivedByAck);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageSendError);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesReadedEncrypted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.screenshotTook);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidStarted);
        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, true);
        }
        if (currentEncryptedChat != null) {
            MediaController.getInstance().stopMediaObserver();
        }
        if (currentUser != null) {
            MessagesController.getInstance().cancelLoadFullUser(currentUser.id);
        }
        if (!AndroidUtilities.isTablet() && getParentActivity() != null) {
            getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        AndroidUtilities.unlockOrientation(getParentActivity());
        MediaController.getInstance().stopAudio();
    }

    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            lastPrintString = null;
            lastStatus = null;

            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(final int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == -2) {
                        selectedMessagesIds.clear();
                        selectedMessagesCanCopyIds.clear();
                        actionBar.hideActionMode();
                        updateVisibleRows();
                    } else if (id == attach_photo) {
                        try {
                            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            File image = Utilities.generatePicturePath();
                            if (image != null) {
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                                currentPicturePath = image.getAbsolutePath();
                            }
                            startActivityForResult(takePictureIntent, 0);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (id == attach_gallery) {
                        PhotoPickerActivity fragment = new PhotoPickerActivity();
                        fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                            @Override
                            public void didSelectPhotos(ArrayList<String> photos) {
                                SendMessagesHelper.prepareSendingPhotos(photos, null, dialog_id);
                            }

                            @Override
                            public void startPhotoSelectActivity() {
                                try {
                                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                                    photoPickerIntent.setType("image/*");
                                    startActivityForResult(photoPickerIntent, 1);
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                        presentFragment(fragment);
                    } else if (id == attach_video) {
                        try {
                            Intent pickIntent = new Intent();
                            pickIntent.setType("video/*");
                            pickIntent.setAction(Intent.ACTION_GET_CONTENT);
                            pickIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1000));
                            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                            File video = Utilities.generateVideoPath();
                            if (video != null) {
                                if (Build.VERSION.SDK_INT >= 18) {
                                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                                }
                                takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1000));
                                currentPicturePath = video.getAbsolutePath();
                            }
                            Intent chooserIntent = Intent.createChooser(pickIntent, "");
                            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takeVideoIntent});

                            startActivityForResult(chooserIntent, 2);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (id == attach_location) {
                        if (!isGoogleMapsInstalled()) {
                            return;
                        }
                        LocationActivity fragment = new LocationActivity();
                        fragment.setDelegate(new LocationActivity.LocationActivityDelegate() {
                            @Override
                            public void didSelectLocation(double latitude, double longitude) {
                                SendMessagesHelper.getInstance().sendMessage(latitude, longitude, dialog_id);
                                if (chatListView != null) {
                                    chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                }
                                if (paused) {
                                    scrollToTopOnResume = true;
                                }
                            }
                        });
                        presentFragment(fragment);
                    } else if (id == attach_document) {
                        DocumentSelectActivity fragment = new DocumentSelectActivity();
                        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
                            @Override
                            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files) {
                                activity.finishFragment();
                                SendMessagesHelper.prepareSendingDocuments(files, files, null, null, dialog_id);
                            }

                            @Override
                            public void startDocumentSelectActivity() {
                                try {
                                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                                    photoPickerIntent.setType("*/*");
                                    startActivityForResult(photoPickerIntent, 21);
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                        presentFragment(fragment);
                    } else if (id == copy) {
                        String str = "";
                        ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesCanCopyIds.keySet());
                        if (currentEncryptedChat == null) {
                            Collections.sort(ids);
                        } else {
                            Collections.sort(ids, Collections.reverseOrder());
                        }
                        for (Integer messageId : ids) {
                            MessageObject messageObject = selectedMessagesCanCopyIds.get(messageId);
                            if (str.length() != 0) {
                                str += "\n";
                            }
                            if (messageObject.messageOwner.message != null) {
                                str += messageObject.messageOwner.message;
                            } else {
                                str += messageObject.messageText;
                            }
                        }
                        if (str.length() != 0) {
                            if (Build.VERSION.SDK_INT < 11) {
                                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setText(str);
                            } else {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", str);
                                clipboard.setPrimaryClip(clip);
                            }
                        }
                        selectedMessagesIds.clear();
                        selectedMessagesCanCopyIds.clear();
                        actionBar.hideActionMode();
                        updateVisibleRows();
                    } else if (id == delete) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.formatString("AreYouSureDeleteMessages", R.string.AreYouSureDeleteMessages, LocaleController.formatPluralString("messages", selectedMessagesIds.size())));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
                                ArrayList<Long> random_ids = null;
                                if (currentEncryptedChat != null) {
                                    random_ids = new ArrayList<Long>();
                                    for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesIds.entrySet()) {
                                        MessageObject msg = entry.getValue();
                                        if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                            random_ids.add(msg.messageOwner.random_id);
                                        }
                                    }
                                }
                                MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat);
                                actionBar.hideActionMode();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == forward) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putBoolean("serverOnly", true);
                        args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
                        args.putString("selectAlertStringGroup", LocaleController.getString("ForwardMessagesToGroup", R.string.ForwardMessagesToGroup));
                        MessagesActivity fragment = new MessagesActivity(args);
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    } else if (id == chat_enc_timer) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        showAlertDialog(AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat));
                    } else if (id == clear_history || id == delete_chat) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        final boolean isChat = (int)dialog_id < 0 && (int)(dialog_id >> 32) != 1;
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        if (id == clear_history) {
                            builder.setMessage(LocaleController.getString("AreYouSureClearHistory", R.string.AreYouSureClearHistory));
                        } else {
                            if (isChat) {
                                builder.setMessage(LocaleController.getString("AreYouSureDeleteAndExit", R.string.AreYouSureDeleteAndExit));
                            } else {
                                builder.setMessage(LocaleController.getString("AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
                            }
                        }
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.getInstance().deleteDialog(dialog_id, 0, id == clear_history);
                                if (id != clear_history) {
                                    if (isChat) {
                                        MessagesController.getInstance().deleteUserFromChat((int) -dialog_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
                                    }
                                    finishFragment();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == share_contact) {
                        if (currentUser == null || getParentActivity() == null) {
                            return;
                        }
                        if (currentUser.phone != null && currentUser.phone.length() != 0) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", currentUser.id);
                            args.putBoolean("addContact", true);
                            presentFragment(new ContactAddActivity(args));
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfo", R.string.AreYouSureShareMyContactInfo));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    SendMessagesHelper.getInstance().sendMessage(UserConfig.getCurrentUser(), dialog_id);
                                    chatListView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                        }
                                    });
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showAlertDialog(builder);
                        }
                    }
                }
            });

            avatarContainer = new FrameLayoutFixed(getParentActivity());
            avatarContainer.setBackgroundResource(R.drawable.bar_selector);
            avatarContainer.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            actionBar.addView(avatarContainer);
            FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
            layoutParams2.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams2.rightMargin = AndroidUtilities.dp(40);
            layoutParams2.leftMargin = AndroidUtilities.dp(56);
            layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
            avatarContainer.setLayoutParams(layoutParams2);
            avatarContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentUser != null) {
                        Bundle args = new Bundle();
                        args.putInt("user_id", currentUser.id);
                        if (currentEncryptedChat != null) {
                            args.putLong("dialog_id", dialog_id);
                        }
                        presentFragment(new ProfileActivity(args));
                    } else if (currentChat != null) {
                        Bundle args = new Bundle();
                        args.putInt("chat_id", currentChat.id);
                        ProfileActivity fragment = new ProfileActivity(args);
                        fragment.setChatInfo(info);
                        presentFragment(fragment);
                    }
                }
            });

            if (currentChat != null) {
                int count = currentChat.participants_count;
                if (info != null) {
                    count = info.participants.size();
                }
                if (count == 0 || currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden || info != null && info instanceof TLRPC.TL_chatParticipantsForbidden) {
                    avatarContainer.setEnabled(false);
                }
            }

            avatarImageView = new BackupImageView(getParentActivity());
            avatarImageView.imageReceiver.setRoundRadius(AndroidUtilities.dp(21));
            avatarImageView.processDetach = false;
            avatarContainer.addView(avatarImageView);
            layoutParams2 = (FrameLayout.LayoutParams) avatarImageView.getLayoutParams();
            layoutParams2.width = AndroidUtilities.dp(42);
            layoutParams2.height = AndroidUtilities.dp(42);
            layoutParams2.topMargin = AndroidUtilities.dp(3);
            layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
            avatarImageView.setLayoutParams(layoutParams2);

            if (currentEncryptedChat != null) {
                timeItem = new ImageView(getParentActivity());
                timeItem.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
                timeItem.setScaleType(ImageView.ScaleType.CENTER);
                avatarContainer.addView(timeItem);
                timerDrawable = new TimerDrawable(getParentActivity());

                layoutParams2 = (FrameLayout.LayoutParams) timeItem.getLayoutParams();
                layoutParams2.width = AndroidUtilities.dp(34);
                layoutParams2.height = AndroidUtilities.dp(34);
                layoutParams2.topMargin = AndroidUtilities.dp(18);
                layoutParams2.leftMargin = AndroidUtilities.dp(16);
                layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
                timeItem.setLayoutParams(layoutParams2);
                timeItem.setImageDrawable(timerDrawable);

                timeItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        showAlertDialog(AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat));
                    }
                });
            }

            nameTextView = new TextView(getParentActivity());
            nameTextView.setTextColor(0xffffffff);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            nameTextView.setLines(1);
            nameTextView.setMaxLines(1);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setGravity(Gravity.LEFT);
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            avatarContainer.addView(nameTextView);
            layoutParams2 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
            layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams2.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams2.leftMargin = AndroidUtilities.dp(54);
            layoutParams2.bottomMargin = AndroidUtilities.dp(22);
            layoutParams2.gravity = Gravity.BOTTOM;
            nameTextView.setLayoutParams(layoutParams2);

            onlineTextView = new TextView(getParentActivity());
            onlineTextView.setTextColor(0xffd7e8f7);
            onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            onlineTextView.setLines(1);
            onlineTextView.setMaxLines(1);
            onlineTextView.setSingleLine(true);
            onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
            onlineTextView.setGravity(Gravity.LEFT);
            avatarContainer.addView(onlineTextView);
            layoutParams2 = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
            layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams2.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams2.leftMargin = AndroidUtilities.dp(54);
            layoutParams2.bottomMargin = AndroidUtilities.dp(4);
            layoutParams2.gravity = Gravity.BOTTOM;
            onlineTextView.setLayoutParams(layoutParams2);

            updateTitle();
            updateSubtitle();

            if (currentEncryptedChat != null) {
                nameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_header, 0, 0, 0);
                nameTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
            }

            ActionBarMenu menu = actionBar.createMenu();

            headerItem = menu.addItem(0, R.drawable.ic_ab_other);
            if (currentUser != null) {
                addContactItem = headerItem.addSubItem(share_contact, "", 0);
            }
            if (currentEncryptedChat != null) {
                timeItem2 = headerItem.addSubItem(chat_enc_timer, LocaleController.getString("SetTimer", R.string.SetTimer), 0);
            }
            headerItem.addSubItem(clear_history, LocaleController.getString("ClearHistory", R.string.ClearHistory), 0);
            if (currentChat != null && !isBroadcast) {
                headerItem.addSubItem(delete_chat, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit), 0);
            } else {
                headerItem.addSubItem(delete_chat, LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser), 0);
            }

            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) headerItem.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(-48);
            headerItem.setLayoutParams(layoutParams);

            attachItem = menu.addItem(chat_menu_attach, R.drawable.ic_ab_other);
            attachItem.addSubItem(attach_photo, LocaleController.getString("ChatTakePhoto", R.string.ChatTakePhoto), R.drawable.ic_attach_photo);
            attachItem.addSubItem(attach_gallery, LocaleController.getString("ChatGallery", R.string.ChatGallery), R.drawable.ic_attach_gallery);
            attachItem.addSubItem(attach_video, LocaleController.getString("ChatVideo", R.string.ChatVideo), R.drawable.ic_attach_video);
            attachItem.addSubItem(attach_document, LocaleController.getString("ChatDocument", R.string.ChatDocument), R.drawable.ic_ab_doc);
            attachItem.addSubItem(attach_location, LocaleController.getString("ChatLocation", R.string.ChatLocation), R.drawable.ic_attach_location);
            attachItem.setVisibility(View.INVISIBLE);

            menuItem = menu.addItem(chat_menu_attach, R.drawable.ic_ab_attach);
            menuItem.addSubItem(attach_photo, LocaleController.getString("ChatTakePhoto", R.string.ChatTakePhoto), R.drawable.ic_attach_photo);
            menuItem.addSubItem(attach_gallery, LocaleController.getString("ChatGallery", R.string.ChatGallery), R.drawable.ic_attach_gallery);
            menuItem.addSubItem(attach_video, LocaleController.getString("ChatVideo", R.string.ChatVideo), R.drawable.ic_attach_video);
            menuItem.addSubItem(attach_document, LocaleController.getString("ChatDocument", R.string.ChatDocument), R.drawable.ic_ab_doc);
            menuItem.addSubItem(attach_location, LocaleController.getString("ChatLocation", R.string.ChatLocation), R.drawable.ic_attach_location);
            menuItem.setShowFromBottom(true);
            menuItem.setBackgroundDrawable(null);

            actionModeViews.clear();

            final ActionBarMenu actionMode = actionBar.createActionMode();
            actionModeViews.add(actionMode.addItem(-2, R.drawable.ic_ab_back_grey, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));

            selectedMessagesCountTextView = new TextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            selectedMessagesCountTextView.setTextColor(0xff737373);
            selectedMessagesCountTextView.setSingleLine(true);
            selectedMessagesCountTextView.setLines(1);
            selectedMessagesCountTextView.setEllipsize(TextUtils.TruncateAt.END);
            selectedMessagesCountTextView.setPadding(AndroidUtilities.dp(11), 0, 0, AndroidUtilities.dp(2));
            selectedMessagesCountTextView.setGravity(Gravity.CENTER_VERTICAL);
            selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            actionMode.addView(selectedMessagesCountTextView);
            layoutParams = (LinearLayout.LayoutParams)selectedMessagesCountTextView.getLayoutParams();
            layoutParams.weight = 1;
            layoutParams.width = 0;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            selectedMessagesCountTextView.setLayoutParams(layoutParams);

            if (currentEncryptedChat == null) {
                actionModeViews.add(actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
                actionModeViews.add(actionMode.addItem(forward, R.drawable.ic_ab_fwd_forward, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
                actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
            } else {
                actionModeViews.add(actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
                actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
            }
            actionMode.getItem(copy).setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);
            checkActionBarMenu();

            fragmentView = inflater.inflate(R.layout.chat_layout, container, false);

            View contentView = fragmentView.findViewById(R.id.chat_layout);
            TextView emptyView = (TextView) fragmentView.findViewById(R.id.searchEmptyView);
            emptyViewContainer = fragmentView.findViewById(R.id.empty_view);
            emptyViewContainer.setVisibility(View.INVISIBLE);
            emptyViewContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            emptyView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
            chatListView = (LayoutListView)fragmentView.findViewById(R.id.chat_list_view);
            chatListView.setAdapter(chatAdapter = new ChatAdapter(getParentActivity()));
            bottomOverlay = fragmentView.findViewById(R.id.bottom_overlay);
            bottomOverlayText = (TextView)fragmentView.findViewById(R.id.bottom_overlay_text);
            bottomOverlayChat = fragmentView.findViewById(R.id.bottom_overlay_chat);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            pagedownButton = fragmentView.findViewById(R.id.pagedown_button);
            pagedownButton.setVisibility(View.GONE);

            View progressViewInner = progressView.findViewById(R.id.progressLayoutInner);

            updateContactStatus();

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            int selectedBackground = preferences.getInt("selectedBackground", 1000001);
            int selectedColor = preferences.getInt("selectedColor", 0);
            if (selectedColor != 0) {
                contentView.setBackgroundColor(selectedColor);
                chatListView.setCacheColorHint(selectedColor);
            } else {
                chatListView.setCacheColorHint(0);
                try {
                    if (ApplicationLoader.cachedWallpaper != null) {
                        isCustomTheme = selectedBackground != 1000001;
                        ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(ApplicationLoader.cachedWallpaper);
                    } else {
                        if (selectedBackground == 1000001) {
                            ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(R.drawable.background_hd);
                            ApplicationLoader.cachedWallpaper = ((SizeNotifierRelativeLayout) contentView).getBackgroundImage();
                        } else {
                            File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                            if (toFile.exists()) {
                                Drawable drawable = Drawable.createFromPath(toFile.getAbsolutePath());
                                if (drawable != null) {
                                    ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(drawable);
                                    ApplicationLoader.cachedWallpaper = drawable;
                                } else {
                                    contentView.setBackgroundColor(-2693905);
                                    chatListView.setCacheColorHint(-2693905);
                                }
                                isCustomTheme = true;
                            } else {
                                ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(R.drawable.background_hd);
                                ApplicationLoader.cachedWallpaper = ((SizeNotifierRelativeLayout) contentView).getBackgroundImage();
                                isCustomTheme = false;
                            }
                        }
                    }
                } catch (Throwable e) {
                    contentView.setBackgroundColor(-2693905);
                    chatListView.setCacheColorHint(-2693905);
                    FileLog.e("tmessages", e);
                }
            }

            if (isCustomTheme) {
                progressViewInner.setBackgroundResource(R.drawable.system_loader2);
                emptyView.setBackgroundResource(R.drawable.system_black);
            } else {
                progressViewInner.setBackgroundResource(R.drawable.system_loader1);
                emptyView.setBackgroundResource(R.drawable.system_blue);
            }
            emptyView.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(1), AndroidUtilities.dp(7), AndroidUtilities.dp(1));

            if (currentUser != null && (currentUser.id / 1000 == 333 || currentUser.id % 1000 == 0)) {
                emptyView.setText(LocaleController.getString("GotAQuestion", R.string.GotAQuestion));
            }

            chatListView.setOnItemLongClickListener(onItemLongClickListener);
            chatListView.setOnItemClickListener(onItemClickListener);

            final Rect scrollRect = new Rect();

            chatListView.setOnInterceptTouchEventListener(new LayoutListView.OnInterceptTouchEventListener() {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    if (actionBar.isActionModeShowed()) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        int x = (int)event.getX();
                        int y = (int)event.getY();
                        int count = chatListView.getChildCount();
                        Rect rect = new Rect();
                        for (int a = 0; a < count; a++) {
                            View view = chatListView.getChildAt(a);
                            int top = view.getTop();
                            int bottom = view.getBottom();
                            view.getLocalVisibleRect(rect);
                            if (top > y || bottom < y) {
                                continue;
                            }
                            if (!(view instanceof ChatMediaCell)) {
                                break;
                            }
                            final ChatMediaCell cell = (ChatMediaCell)view;
                            final MessageObject messageObject = cell.getMessageObject();
                            if (messageObject == null || !messageObject.isSecretPhoto() || !cell.getPhotoImage().isInsideImage(x, y - top)) {
                                break;
                            }
                            File file = FileLoader.getPathToMessage(messageObject.messageOwner);
                            if (!file.exists()) {
                                break;
                            }
                            startX = x;
                            startY = y;
                            chatListView.setOnItemClickListener(null);
                            openSecretPhotoRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (openSecretPhotoRunnable == null) {
                                        return;
                                    }
                                    chatListView.requestDisallowInterceptTouchEvent(true);
                                    chatListView.setOnItemLongClickListener(null);
                                    chatListView.setLongClickable(false);
                                    openSecretPhotoRunnable = null;
                                    if (sendSecretMessageRead(messageObject)) {
                                        cell.invalidate();
                                    }
                                    SecretPhotoViewer.getInstance().setParentActivity(getParentActivity());
                                    SecretPhotoViewer.getInstance().openPhoto(messageObject);
                                }
                            };
                            AndroidUtilities.runOnUIThread(openSecretPhotoRunnable, 100);
                            return true;
                        }
                    }
                    return false;
                }
            });

            chatListView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (openSecretPhotoRunnable != null || SecretPhotoViewer.getInstance().isVisible()) {
                        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_POINTER_UP) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    chatListView.setOnItemClickListener(onItemClickListener);
                                }
                            }, 150);
                            if (openSecretPhotoRunnable != null) {
                                AndroidUtilities.cancelRunOnUIThread(openSecretPhotoRunnable);
                                openSecretPhotoRunnable = null;
                                try {
                                    Toast.makeText(v.getContext(), LocaleController.getString("PhotoTip", R.string.PhotoTip), Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            } else {
                                if (SecretPhotoViewer.getInstance().isVisible()) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            chatListView.setOnItemLongClickListener(onItemLongClickListener);
                                            chatListView.setLongClickable(true);
                                        }
                                    });
                                    SecretPhotoViewer.getInstance().closePhoto();
                                }
                            }
                        } else if (event.getAction() != MotionEvent.ACTION_DOWN) {
                            if (SecretPhotoViewer.getInstance().isVisible()) {
                                return true;
                            } else if (openSecretPhotoRunnable != null) {
                                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                                    if (Math.hypot(startX - event.getX(), startY - event.getY()) > AndroidUtilities.dp(5)) {
                                        AndroidUtilities.cancelRunOnUIThread(openSecretPhotoRunnable);
                                        openSecretPhotoRunnable = null;
                                    }
                                } else {
                                    AndroidUtilities.cancelRunOnUIThread(openSecretPhotoRunnable);
                                    openSecretPhotoRunnable = null;
                                }
                            }
                        }
                    }
                    return false;
                }
            });

            chatListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL || i == AbsListView.OnScrollListener.SCROLL_STATE_FLING && highlightMessageId != Integer.MAX_VALUE) {
                        highlightMessageId = Integer.MAX_VALUE;
                        updateVisibleRows();
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (visibleItemCount > 0) {
                        if (firstVisibleItem <= 10) {
                            if (!endReached && !loading) {
                                if (messagesByDays.size() != 0) {
                                    MessagesController.getInstance().loadMessages(dialog_id, 20, maxMessageId, !cacheEndReaced && startLoadFromMessageId == 0, minDate, classGuid, 0, 0, 0, startLoadFromMessageId == 0);
                                } else {
                                    MessagesController.getInstance().loadMessages(dialog_id, 20, 0, !cacheEndReaced && startLoadFromMessageId == 0, minDate, classGuid, 0, 0, 0, startLoadFromMessageId == 0);
                                }
                                loading = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount >= totalItemCount - 6) {
                            if (!forward_end_reached && !loadingForward) {
                                MessagesController.getInstance().loadMessages(dialog_id, 20, minMessageId, startLoadFromMessageId == 0, maxDate, classGuid, 1, 0, 0, startLoadFromMessageId == 0);
                                loadingForward = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount == totalItemCount && forward_end_reached) {
                            showPagedownButton(false, true);
                        }
                    }
                    for (int a = 0; a < visibleItemCount; a++) {
                        View view = absListView.getChildAt(a);
                        if (view instanceof ChatMessageCell) {
                            ChatMessageCell messageCell = (ChatMessageCell)view;
                            messageCell.getLocalVisibleRect(scrollRect);
                            messageCell.setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
                        }
                    }
                }
            });

            bottomOverlayChatText = (TextView)fragmentView.findViewById(R.id.bottom_overlay_chat_text);
            TextView textView = (TextView)fragmentView.findViewById(R.id.secret_title);
            textView.setText(LocaleController.getString("EncryptedDescriptionTitle", R.string.EncryptedDescriptionTitle));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description1);
            textView.setText(LocaleController.getString("EncryptedDescription1", R.string.EncryptedDescription1));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description2);
            textView.setText(LocaleController.getString("EncryptedDescription2", R.string.EncryptedDescription2));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description3);
            textView.setText(LocaleController.getString("EncryptedDescription3", R.string.EncryptedDescription3));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description4);
            textView.setText(LocaleController.getString("EncryptedDescription4", R.string.EncryptedDescription4));

            if (loading && messages.isEmpty()) {
                progressView.setVisibility(View.VISIBLE);
                chatListView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.INVISIBLE);
                chatListView.setEmptyView(emptyViewContainer);
            }

            pagedownButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    scrollToLastMessage();
                }
            });

            bottomOverlayChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    if (currentUser != null && userBlocked) {
                        builder.setMessage(LocaleController.getString("AreYouSureUnblockContact", R.string.AreYouSureUnblockContact));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.getInstance().unblockUser(currentUser.id);
                            }
                        });
                    } else {
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.getInstance().deleteDialog(dialog_id, 0, false);
                                finishFragment();
                            }
                        });
                    }
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }
            });

            updateBottomOverlay();

            chatActivityEnterView.setContainerView(getParentActivity(), fragmentView);
            chatActivityEnterView.addToAttachLayout(menuItem);

            if (currentEncryptedChat != null) {
                emptyView.setVisibility(View.INVISIBLE);
                View secretChatPlaceholder = contentView.findViewById(R.id.secret_placeholder);
                secretChatPlaceholder.setVisibility(View.VISIBLE);
                if (isCustomTheme) {
                    secretChatPlaceholder.setBackgroundResource(R.drawable.system_black);
                } else {
                    secretChatPlaceholder.setBackgroundResource(R.drawable.system_blue);
                }
                secretViewStatusTextView = (TextView) contentView.findViewById(R.id.invite_text);
                secretChatPlaceholder.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

                View v = contentView.findViewById(R.id.secret_placeholder);
                v.setVisibility(View.VISIBLE);

                if (currentEncryptedChat.admin_id == UserConfig.getClientUserId()) {
                    if (currentUser.first_name.length() > 0) {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, currentUser.first_name));
                    } else {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, currentUser.last_name));
                    }
                } else {
                    if (currentUser.first_name.length() > 0) {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, currentUser.first_name));
                    } else {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, currentUser.last_name));
                    }
                }

                updateSecretStatus();
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private boolean sendSecretMessageRead(MessageObject messageObject) {
        if (messageObject == null || messageObject.isOut() || !messageObject.isSecretMedia() || messageObject.messageOwner.destroyTime != 0 || messageObject.messageOwner.ttl <= 0) {
            return false;
        }
        MessagesController.getInstance().markMessageAsRead(dialog_id, messageObject.messageOwner.random_id, messageObject.messageOwner.ttl);
        messageObject.messageOwner.destroyTime = messageObject.messageOwner.ttl + ConnectionsManager.getInstance().getCurrentTime();
        return true;
    }

    private void scrollToLastMessage() {
        if (forward_end_reached && first_unread_id == 0 && startLoadFromMessageId == 0) {
            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
        } else {
            messages.clear();
            messagesByDays.clear();
            messagesDict.clear();
            progressView.setVisibility(View.VISIBLE);
            chatListView.setEmptyView(null);
            if (currentEncryptedChat == null) {
                maxMessageId = Integer.MAX_VALUE;
                minMessageId = Integer.MIN_VALUE;
            } else {
                maxMessageId = Integer.MIN_VALUE;
                minMessageId = Integer.MAX_VALUE;
            }
            maxDate = Integer.MIN_VALUE;
            minDate = 0;
            forward_end_reached = true;
            loading = true;
            startLoadFromMessageId = 0;
            chatAdapter.notifyDataSetChanged();
            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, true, 0, classGuid, 0, 0, 0, true);
        }
    }

    private void showPagedownButton(boolean show, boolean animated) {
        if (pagedownButton == null) {
            return;
        }
        if (show) {
            if (pagedownButton.getVisibility() == View.GONE) {
                if (animated) {
                    pagedownButton.setVisibility(View.VISIBLE);
                    ViewProxy.setAlpha(pagedownButton, 0);
                    ObjectAnimatorProxy.ofFloatProxy(pagedownButton, "alpha", 1.0f).setDuration(200).start();
                } else {
                    pagedownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (pagedownButton.getVisibility() == View.VISIBLE) {
                if (animated) {
                    ObjectAnimatorProxy.ofFloatProxy(pagedownButton, "alpha", 0.0f).setDuration(200).addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            pagedownButton.setVisibility(View.GONE);
                        }
                    }).start();
                } else {
                    pagedownButton.setVisibility(View.GONE);
                }
            }
        }
    }

    private void updateSecretStatus() {
        if (bottomOverlay == null) {
            return;
        }
        if (currentEncryptedChat == null || secretViewStatusTextView == null) {
            bottomOverlay.setVisibility(View.GONE);
            return;
        }
        boolean hideKeyboard = false;
        if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
            bottomOverlayText.setText(LocaleController.getString("EncryptionProcessing", R.string.EncryptionProcessing));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
            bottomOverlayText.setText(Html.fromHtml(LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, "<b>" + currentUser.first_name + "</b>")));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
            bottomOverlayText.setText(LocaleController.getString("EncryptionRejected", R.string.EncryptionRejected));
            bottomOverlay.setVisibility(View.VISIBLE);
            chatActivityEnterView.setFieldText("");
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            preferences.edit().remove("dialog_" + dialog_id).commit();
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
            bottomOverlay.setVisibility(View.GONE);
        }
        if (hideKeyboard) {
            chatActivityEnterView.hideEmojiPopup();
            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        }
        checkActionBarMenu();
    }

    private void checkActionBarMenu() {
        if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat) ||
                currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left) ||
                currentUser != null && (currentUser instanceof TLRPC.TL_userDeleted || currentUser instanceof TLRPC.TL_userEmpty)) {

            if (menuItem != null) {
                menuItem.setVisibility(View.GONE);
            }
            if (timeItem != null) {
                timeItem.setVisibility(View.GONE);
            }
            if (timeItem2 != null) {
                timeItem2.setVisibility(View.GONE);
            }
        } else {
            if (menuItem != null) {
                menuItem.setVisibility(View.VISIBLE);
            }
            if (timeItem != null) {
                timeItem.setVisibility(View.VISIBLE);
            }
            if (timeItem2 != null) {
                timeItem2.setVisibility(View.VISIBLE);
            }
        }

        if (timerDrawable != null) {
            timerDrawable.setTime(currentEncryptedChat.ttl);
        }

        checkAndUpdateAvatar();
    }

    private int updateOnlineCount() {
        if (info == null) {
            return 0;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId()) && user.status.expires > 10000) {
                onlineCount++;
            }
        }
        return onlineCount;
    }

    private int getMessageType(MessageObject messageObject) {
        if (messageObject == null) {
            return -1;
        }
        if (currentEncryptedChat == null) {
            boolean isBroadcastError = isBroadcast && messageObject.messageOwner.id <= 0 && messageObject.isSendError();
            if (!isBroadcast && messageObject.messageOwner.id <= 0 && messageObject.isOut() || isBroadcastError) {
                if (messageObject.isSendError()) {
                    if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                        return 0;
                    } else {
                        return 6;
                    }
                } else {
                    return -1;
                }
            } else {
                if (messageObject.type == 6) {
                    return -1;
                } else if (messageObject.type == 10 || messageObject.type == 11) {
                    if (messageObject.messageOwner.id == 0) {
                        return -1;
                    }
                    return 1;
                } else {
                    if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo ||
                                messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto ||
                                messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            boolean canSave = false;
                            if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                                File f = new File(messageObject.messageOwner.attachPath);
                                if (f.exists()) {
                                    canSave = true;
                                }
                            }
                            if (!canSave) {
                                File f = FileLoader.getPathToMessage(messageObject.messageOwner);
                                if (f.exists()) {
                                    canSave = true;
                                }
                            }
                            if (canSave) {
                                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    String mime = messageObject.messageOwner.media.document.mime_type;
                                    if (mime != null && mime.endsWith("/xml")) {
                                        return 5;
                                    }
                                }
                                return 4;
                            }
                        }
                        return 2;
                    } else {
                        return 3;
                    }
                }
            }
        } else {
            if (messageObject.type == 6) {
                return -1;
            } else if (messageObject.isSendError()) {
                if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                    return 0;
                } else {
                    return 6;
                }
            } else if (messageObject.type == 10 || messageObject.type == 11) {
                if (messageObject.isSending()) {
                    return -1;
                } else {
                    return 1;
                }
            } else {
                if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo ||
                            messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto ||
                            messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        boolean canSave = false;
                        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                            File f = new File(messageObject.messageOwner.attachPath);
                            if (f.exists()) {
                                canSave = true;
                            }
                        }
                        if (!canSave) {
                            File f = FileLoader.getPathToMessage(messageObject.messageOwner);
                            if (f.exists()) {
                                canSave = true;
                            }
                        }
                        if (canSave) {
                            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                String mime = messageObject.messageOwner.media.document.mime_type;
                                if (mime != null && mime.endsWith("text/xml")) {
                                    return 5;
                                }
                            }
                            if (messageObject.messageOwner.ttl <= 0) {
                                return 4;
                            }
                        }
                    }
                    return 2;
                } else {
                    return 3;
                }
            }
        }
    }

    private void addToSelectedMessages(MessageObject messageObject) {
        if (selectedMessagesIds.containsKey(messageObject.messageOwner.id)) {
            selectedMessagesIds.remove(messageObject.messageOwner.id);
            if (messageObject.type == 0) {
                selectedMessagesCanCopyIds.remove(messageObject.messageOwner.id);
            }
        } else {
            selectedMessagesIds.put(messageObject.messageOwner.id, messageObject);
            if (messageObject.type == 0) {
                selectedMessagesCanCopyIds.put(messageObject.messageOwner.id, messageObject);
            }
        }
        if (actionBar.isActionModeShowed()) {
            if (selectedMessagesIds.isEmpty()) {
                actionBar.hideActionMode();
            }
            actionBar.createActionMode().getItem(copy).setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void processRowSelect(View view) {
        MessageObject message = null;
        if (view instanceof ChatBaseCell) {
            message = ((ChatBaseCell)view).getMessageObject();
        } else if (view instanceof ChatActionCell) {
            message = ((ChatActionCell)view).getMessageObject();
        }

        int type = getMessageType(message);

        if (type < 2 || type == 6) {
            return;
        }
        addToSelectedMessages(message);
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void updateActionModeTitle() {
        if (!actionBar.isActionModeShowed()) {
            return;
        }
        if (!selectedMessagesIds.isEmpty()) {
            selectedMessagesCountTextView.setText(String.format("%d", selectedMessagesIds.size()));
        }
    }

    private void updateTitle() {
        if (nameTextView == null) {
            return;
        }
        if (currentChat != null) {
            nameTextView.setText(currentChat.title);
        } else if (currentUser != null) {
            if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    nameTextView.setText(PhoneFormat.getInstance().format("+" + currentUser.phone));
                } else {
                    nameTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
                }
            } else {
                nameTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            }
        }
    }

    private void updateSubtitle() {
        if (onlineTextView == null) {
            return;
        }
        CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
        if (printString != null) {
            printString = TextUtils.replace(printString, new String[]{"..."}, new String[]{""});
        }
        if (printString == null || printString.length() == 0) {
            setTypingAnimation(false);
            if (currentChat != null) {
                if (currentChat instanceof TLRPC.TL_chatForbidden) {
                    onlineTextView.setText(LocaleController.getString("YouWereKicked", R.string.YouWereKicked));
                } else if (currentChat.left) {
                    onlineTextView.setText(LocaleController.getString("YouLeft", R.string.YouLeft));
                } else {
                    int count = currentChat.participants_count;
                    if (info != null) {
                        count = info.participants.size();
                    }
                    if (onlineCount > 1 && count != 0) {
                        onlineTextView.setText(String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("Online", onlineCount)));
                    } else {
                        onlineTextView.setText(LocaleController.formatPluralString("Members", count));
                    }
                }
            } else if (currentUser != null) {
                TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
                if (user != null) {
                    currentUser = user;
                }
                String newStatus = LocaleController.formatUserStatus(currentUser);
                if (lastStatus == null || lastPrintString != null || lastStatus != null && !lastStatus.equals(newStatus)) {
                    lastStatus = newStatus;
                    onlineTextView.setText(newStatus);
                }
            }
            lastPrintString = null;
        } else {
            lastPrintString = printString;
            onlineTextView.setText(printString);
            setTypingAnimation(true);
        }
    }

    private void setTypingAnimation(boolean start) {
        if (actionBar == null) {
            return;
        }
        if (start) {
            try {
                if (onlineTextView != null) {
                    onlineTextView.setCompoundDrawablesWithIntrinsicBounds(typingDotsDrawable, null, null, null);
                    onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                }
                if (typingDotsDrawable != null) {
                    typingDotsDrawable.start();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            if (onlineTextView != null) {
                onlineTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                onlineTextView.setCompoundDrawablePadding(0);
            }
            if (typingDotsDrawable != null) {
                typingDotsDrawable.stop();
            }
        }
    }

    private void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        AvatarDrawable avatarDrawable = null;
        if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            }
            avatarDrawable = new AvatarDrawable(currentUser);
        } else if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
            avatarDrawable = new AvatarDrawable(currentChat);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
        }
    }

    public boolean openVideoEditor(String videoPath, boolean removeLast) {
        Bundle args = new Bundle();
        args.putString("videoPath", videoPath);
        VideoEditorActivity fragment = new VideoEditorActivity(args);
        fragment.setDelegate(new VideoEditorActivity.VideoEditorActivityDelegate() {
            @Override
            public void didFinishEditVideo(String videoPath, long startTime, long endTime, int resultWidth, int resultHeight, int rotationValue, int originalWidth, int originalHeight, int bitrate, long estimatedSize, long estimatedDuration) {
                TLRPC.VideoEditedInfo videoEditedInfo = new TLRPC.VideoEditedInfo();
                videoEditedInfo.startTime = startTime;
                videoEditedInfo.endTime = endTime;
                videoEditedInfo.rotationValue = rotationValue;
                videoEditedInfo.originalWidth = originalWidth;
                videoEditedInfo.originalHeight = originalHeight;
                videoEditedInfo.bitrate = bitrate;
                videoEditedInfo.resultWidth = resultWidth;
                videoEditedInfo.resultHeight = resultHeight;
                videoEditedInfo.originalPath = videoPath;
                SendMessagesHelper.prepareSendingVideo(videoPath, estimatedSize, estimatedDuration, resultWidth, resultHeight, videoEditedInfo, dialog_id);
            }
        });

        if (parentLayout == null || !fragment.onFragmentCreate()) {
            SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id);
            return false;
        }
        parentLayout.presentFragment(fragment, removeLast, true, true);
        return true;
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {
                Utilities.addMediaToGallery(currentPicturePath);
                SendMessagesHelper.prepareSendingPhoto(currentPicturePath, null, dialog_id);
                currentPicturePath = null;
            } else if (requestCode == 1) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                SendMessagesHelper.prepareSendingPhoto(null, data.getData(), dialog_id);
            } else if (requestCode == 2) {
                String videoPath = null;
                if (data != null) {
                    Uri uri = data.getData();
                    boolean fromCamera = false;
                    if (uri != null && uri.getScheme() != null) {
                        fromCamera = uri.getScheme().contains("file");
                    } else if (uri == null) {
                        fromCamera = true;
                    }
                    if (fromCamera) {
                        if (uri != null) {
                            videoPath = uri.getPath();
                        } else {
                            videoPath = currentPicturePath;
                        }
                        Utilities.addMediaToGallery(currentPicturePath);
                        currentPicturePath = null;
                    } else {
                        try {
                            videoPath = Utilities.getPath(uri);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
                if (videoPath == null && currentPicturePath != null) {
                    File f = new File(currentPicturePath);
                    if (f.exists()) {
                        videoPath = currentPicturePath;
                    }
                    currentPicturePath = null;
                }
                if(Build.VERSION.SDK_INT >= 16) {
                    if (paused) {
                        startVideoEdit = videoPath;
                    } else {
                        openVideoEditor(videoPath, false);
                    }
                } else {
                    SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id);
                }
            } else if (requestCode == 21) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                String tempPath = Utilities.getPath(data.getData());
                String originalPath = tempPath;
                if (tempPath == null) {
                    originalPath = data.toString();
                    tempPath = MediaController.copyDocumentToCache(data.getData(), "file");
                }
                if (tempPath == null) {
                    showAttachmentError();
                    return;
                }
                SendMessagesHelper.prepareSendingDocument(tempPath, originalPath, null, null, dialog_id);
            }
        }
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (currentPicturePath != null) {
            args.putString("path", currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        currentPicturePath = args.getString("path");
    }

    private void removeUnreadPlane(boolean reload) {
        if (unreadMessageObject != null) {
            messages.remove(unreadMessageObject);
            forward_end_reached = true;
            first_unread_id = 0;
            last_message_id = 0;
            unread_to_load = 0;
            unreadMessageObject = null;
            if (reload) {
                chatAdapter.notifyDataSetChanged();
            }
        }
    }

    public boolean processSendingText(String text) {
        return chatActivityEnterView.processSendingText(text);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.messagesDidLoaded) {

            long did = (Long)args[0];
            if (did == dialog_id) {
                loadsCount++;
                int count = (Integer)args[1];
                boolean isCache = (Boolean)args[3];
                int fnid = (Integer)args[4];
                int last_unread_date = (Integer)args[8];
                int load_type = (Integer)args[9];
                boolean wasUnread = false;
                if (fnid != 0) {
                    first_unread_id = fnid;
                    last_message_id = (Integer)args[5];
                    unread_to_load = (Integer)args[7];
                } else if (startLoadFromMessageId != 0 && load_type == 3) {
                    last_message_id = (Integer)args[5];
                    first_message_id = (Integer)args[6];
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>)args[2];

                int newRowsCount = 0;

                forward_end_reached = startLoadFromMessageId == 0 && last_message_id == 0;

                if (loadsCount == 1 && messArr.size() > 20) {
                    loadsCount++;
                }

                if (firstLoading) {
                    if (!forward_end_reached) {
                        messages.clear();
                        messagesByDays.clear();
                        messagesDict.clear();
                        if (currentEncryptedChat == null) {
                            maxMessageId = Integer.MAX_VALUE;
                            minMessageId = Integer.MIN_VALUE;
                        } else {
                            maxMessageId = Integer.MIN_VALUE;
                            minMessageId = Integer.MAX_VALUE;
                        }
                        maxDate = Integer.MIN_VALUE;
                        minDate = 0;
                    }
                    firstLoading = false;
                }

                if (load_type == 1) {
                    Collections.reverse(messArr);
                }

                for (int a = 0; a < messArr.size(); a++) {
                    MessageObject obj = messArr.get(a);
                    if (messagesDict.containsKey(obj.messageOwner.id)) {
                        continue;
                    }

                    if (obj.messageOwner.id > 0) {
                        maxMessageId = Math.min(obj.messageOwner.id, maxMessageId);
                        minMessageId = Math.max(obj.messageOwner.id, minMessageId);
                    } else if (currentEncryptedChat != null) {
                        maxMessageId = Math.max(obj.messageOwner.id, maxMessageId);
                        minMessageId = Math.min(obj.messageOwner.id, minMessageId);
                    }
                    if (obj.messageOwner.date != 0) {
                        maxDate = Math.max(maxDate, obj.messageOwner.date);
                        if (minDate == 0 || obj.messageOwner.date < minDate) {
                            minDate = obj.messageOwner.date;
                        }
                    }

                    if (obj.type < 0) {
                        continue;
                    }

                    if (!obj.isOut() && obj.isUnread()) {
                        wasUnread = true;
                    }
                    messagesDict.put(obj.messageOwner.id, obj);
                    ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);

                    if (dayArray == null) {
                        dayArray = new ArrayList<MessageObject>();
                        messagesByDays.put(obj.dateKey, dayArray);

                        TLRPC.Message dateMsg = new TLRPC.Message();
                        dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(dateMsg, null);
                        dateObj.type = 10;
                        dateObj.contentType = 4;
                        if (load_type == 1) {
                            messages.add(0, dateObj);
                        } else {
                            messages.add(dateObj);
                        }
                        newRowsCount++;
                    }

                    newRowsCount++;
                    dayArray.add(obj);
                    if (load_type == 1) {
                        messages.add(0, obj);
                    } else {
                        messages.add(messages.size() - 1, obj);
                    }

                    if (load_type == 2 && obj.messageOwner.id == first_unread_id) {
                        TLRPC.Message dateMsg = new TLRPC.Message();
                        dateMsg.message = "";
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(dateMsg, null);
                        dateObj.contentType = dateObj.type = 6;
                        boolean dateAdded = true;
                        if (a != messArr.size() - 1) {
                            MessageObject next = messArr.get(a + 1);
                            dateAdded = !next.dateKey.equals(obj.dateKey);
                        }
                        messages.add(messages.size() - (dateAdded ? 0 : 1), dateObj);
                        unreadMessageObject = dateObj;
                        scrollToMessage = unreadMessageObject;
                        scrollToMessageMiddleScreen = false;
                        newRowsCount++;
                    } else if (load_type == 3 && obj.messageOwner.id == startLoadFromMessageId) {
                        highlightMessageId = obj.messageOwner.id;
                        scrollToMessage = obj;
                        if (isCache) {
                            startLoadFromMessageId = 0;
                        }
                        scrollToMessageMiddleScreen = true;
                    } else if (load_type == 1 && startLoadFromMessageId != 0 && first_message_id != 0 && obj.messageOwner.id >= first_message_id) {
                        startLoadFromMessageId = 0;
                    }

                    if (obj.messageOwner.id == last_message_id) {
                        forward_end_reached = true;
                    }
                }

                if (forward_end_reached) {
                    first_unread_id = 0;
                    first_message_id = 0;
                    last_message_id = 0;
                }

                if (load_type == 1) {
                    if (messArr.size() != count) {
                        forward_end_reached = true;
                        first_unread_id = 0;
                        last_message_id = 0;
                        first_message_id = 0;
                        startLoadFromMessageId = 0;
                    }

                    chatAdapter.notifyDataSetChanged();
                    loadingForward = false;
                } else {
                    if (messArr.size() != count) {
                        if (isCache) {
                            cacheEndReaced = true;
                            if (currentEncryptedChat != null || isBroadcast) {
                                endReached = true;
                            }
                        } else {
                            cacheEndReaced = true;
                            endReached = true;
                        }
                    }
                    loading = false;

                    if (chatListView != null) {
                        if (first || scrollToTopOnResume) {
                            chatAdapter.notifyDataSetChanged();
                            if (scrollToMessage != null) {
                                final int yOffset = scrollToMessageMiddleScreen ? Math.max(0, (AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - scrollToMessage.textHeight - AndroidUtilities.getCurrentActionBarHeight() - AndroidUtilities.dp(48)) / 2) : 0;
                                if (messages.get(messages.size() - 1) == scrollToMessage) {
                                    chatListView.setSelectionFromTop(0, AndroidUtilities.dp(-11) + yOffset);
                                } else {
                                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(scrollToMessage), AndroidUtilities.dp(-11) + yOffset);
                                }
                                ViewTreeObserver obs = chatListView.getViewTreeObserver();
                                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (!messages.isEmpty()) {
                                            if (messages.get(messages.size() - 1) == scrollToMessage) {
                                                chatListView.setSelectionFromTop(0, AndroidUtilities.dp(-11) + yOffset);
                                            } else {
                                                chatListView.setSelectionFromTop(messages.size() - messages.indexOf(scrollToMessage), AndroidUtilities.dp(-11) + yOffset);
                                            }
                                        }
                                        chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                                        return true;
                                    }
                                });
                                chatListView.invalidate();
                                showPagedownButton(true, true);
                            } else {
                                chatListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                    }
                                });
                            }
                        } else {
                            int firstVisPos = chatListView.getLastVisiblePosition();
                            View firstVisView = chatListView.getChildAt(chatListView.getChildCount() - 1);
                            int top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                            chatAdapter.notifyDataSetChanged();
                            chatListView.setSelectionFromTop(firstVisPos + newRowsCount - (endReached ? 1 : 0), top);
                        }

                        if (paused) {
                            scrollToTopOnResume = true;
                            if (scrollToMessage != null) {
                                scrollToTopUnReadOnResume = true;
                            }
                        }

                        if (first) {
                            if (chatListView.getEmptyView() == null) {
                                chatListView.setEmptyView(emptyViewContainer);
                            }
                        }
                    } else {
                        scrollToTopOnResume = true;
                        if (scrollToMessage != null) {
                            scrollToTopUnReadOnResume = true;
                        }
                    }
                }

                if (first && messages.size() > 0) {
                    final boolean wasUnreadFinal = wasUnread;
                    final int last_unread_date_final = last_unread_date;
                    final int lastid = messages.get(0).messageOwner.id;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (last_message_id != 0) {
                                MessagesController.getInstance().markDialogAsRead(dialog_id, lastid, last_message_id, 0, last_unread_date_final, wasUnreadFinal, false);
                            } else {
                                MessagesController.getInstance().markDialogAsRead(dialog_id, lastid, minMessageId, 0, maxDate, wasUnreadFinal, false);
                            }
                        }
                    }, 700);
                    first = false;
                }

                if (progressView != null) {
                    progressView.setVisibility(View.INVISIBLE);
                }
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int updateMask = (Integer)args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                updateTitle();
            }
            boolean updateSubtitle = false;
            if ((updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                if (currentChat != null) {
                    int lastCount = onlineCount;
                    if (lastCount != updateOnlineCount()) {
                        updateSubtitle = true;
                    }
                } else {
                    updateSubtitle = true;
                }
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_NAME) != 0) {
                checkAndUpdateAvatar();
                updateVisibleRows();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle = true;
                }
            }
            if (updateSubtitle) {
                updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PHONE) != 0) {
                updateContactStatus();
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long did = (Long)args[0];
            if (did == dialog_id) {

                boolean updateChat = false;
                boolean hasFromMe = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[1];

                if (currentEncryptedChat != null && arr.size() == 1) {
                    MessageObject obj = arr.get(0);

                    if (currentEncryptedChat != null && obj.isOut() && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction &&
                            obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL && getParentActivity() != null) {
                        TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL)obj.messageOwner.action.encryptedAction;
                        if (AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 17 && currentEncryptedChat.ttl > 0 && currentEncryptedChat.ttl <= 60) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(R.string.OK, null);
                            builder.setMessage(LocaleController.formatString("CompatibilityChat", R.string.CompatibilityChat, currentUser.first_name, currentUser.first_name));
                            showAlertDialog(builder);
                        }
                    }
                }

                if (!forward_end_reached) {
                    int currentMaxDate = Integer.MIN_VALUE;
                    int currentMinMsgId = Integer.MIN_VALUE;
                    if (currentEncryptedChat != null) {
                        currentMinMsgId = Integer.MAX_VALUE;
                    }
                    boolean currentMarkAsRead = false;

                    for (MessageObject obj : arr) {
                        if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction &&
                                obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL && timerDrawable != null) {
                            TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL)obj.messageOwner.action.encryptedAction;
                            timerDrawable.setTime(action.ttl_seconds);
                        }
                        if (obj.isOut() && obj.isSending()) {
                            scrollToLastMessage();
                            return;
                        }
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        currentMaxDate = Math.max(currentMaxDate, obj.messageOwner.date);
                        if (obj.messageOwner.id > 0) {
                            currentMinMsgId = Math.max(obj.messageOwner.id, currentMinMsgId);
                            last_message_id = Math.max(last_message_id, obj.messageOwner.id);
                        } else if (currentEncryptedChat != null) {
                            currentMinMsgId = Math.min(obj.messageOwner.id, currentMinMsgId);
                            last_message_id = Math.min(last_message_id, obj.messageOwner.id);
                        }

                        if (!obj.isOut() && obj.isUnread()) {
                            unread_to_load++;
                            currentMarkAsRead = true;
                        }
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }

                    if (currentMarkAsRead) {
                        if (paused) {
                            readWhenResume = true;
                            readWithDate = currentMaxDate;
                            readWithMid = currentMinMsgId;
                        } else {
                            if (messages.size() > 0) {
                                MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, currentMinMsgId, 0, currentMaxDate, true, false);
                            }
                        }
                    }
                    updateVisibleRows();
                } else {
                    boolean markAsRead = false;
                    int oldCount = messages.size();
                    for (MessageObject obj : arr) {
                        if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction &&
                                obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL && timerDrawable != null) {
                            TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL)obj.messageOwner.action.encryptedAction;
                            timerDrawable.setTime(action.ttl_seconds);
                        }
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        if (minDate == 0 || obj.messageOwner.date < minDate) {
                            minDate = obj.messageOwner.date;
                        }

                        if (obj.isOut()) {
                            removeUnreadPlane(false);
                            hasFromMe = true;
                        }

                        if (!obj.isOut() && unreadMessageObject != null) {
                            unread_to_load++;
                        }

                        if (obj.messageOwner.id > 0) {
                            maxMessageId = Math.min(obj.messageOwner.id, maxMessageId);
                            minMessageId = Math.max(obj.messageOwner.id, minMessageId);
                        } else if (currentEncryptedChat != null) {
                            maxMessageId = Math.max(obj.messageOwner.id, maxMessageId);
                            minMessageId = Math.min(obj.messageOwner.id, minMessageId);
                        }
                        maxDate = Math.max(maxDate, obj.messageOwner.date);
                        messagesDict.put(obj.messageOwner.id, obj);
                        ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);
                        if (dayArray == null) {
                            dayArray = new ArrayList<MessageObject>();
                            messagesByDays.put(obj.dateKey, dayArray);

                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(dateMsg, null);
                            dateObj.type = 10;
                            dateObj.contentType = 4;
                            messages.add(0, dateObj);
                        }
                        if (!obj.isOut() && obj.isUnread()) {
                            if (!paused) {
                                obj.setIsRead();
                            }
                            markAsRead = true;
                        }
                        dayArray.add(0, obj);
                        messages.add(0, obj);
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }
                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                    }
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    } else {
                        scrollToTopOnResume = true;
                    }

                    if (chatListView != null && chatAdapter != null) {
                        int lastVisible = chatListView.getLastVisiblePosition();
                        if (endReached) {
                            lastVisible++;
                        }
                        if (lastVisible == oldCount || hasFromMe) {
                            if (!firstLoading) {
                                if (paused) {
                                    scrollToTopOnResume = true;
                                } else {
                                    chatListView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                        }
                                    });
                                }
                            }
                        } else {
                            showPagedownButton(true, true);
                        }
                    } else {
                        scrollToTopOnResume = true;
                    }

                    if (markAsRead) {
                        if (paused) {
                            readWhenResume = true;
                            readWithDate = maxDate;
                            readWithMid = minMessageId;
                        } else {
                            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, true, false);
                        }
                    }
                }
                if (updateChat) {
                    updateTitle();
                    checkAndUpdateAvatar();
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            if (args != null && args.length > 0) {
                long did = (Long)args[0];
                if (did == dialog_id) {
                    finishFragment();
                }
            } else {
                removeSelfFromStack();
            }
        } else if (id == NotificationCenter.messagesRead) {
            ArrayList<Integer> markAsReadMessages = (ArrayList<Integer>)args[0];
            boolean updated = false;
            for (Integer ids : markAsReadMessages) {
                MessageObject obj = messagesDict.get(ids);
                if (obj != null) {
                    obj.setIsRead();
                    updated = true;
                }
            }
            if (updated) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>)args[0];
            boolean updated = false;
            for (Integer ids : markAsDeletedMessages) {
                MessageObject obj = messagesDict.get(ids);
                if (obj != null) {
                    int index = messages.indexOf(obj);
                    if (index != -1) {
                        messages.remove(index);
                        messagesDict.remove(ids);
                        ArrayList<MessageObject> dayArr = messagesByDays.get(obj.dateKey);
                        dayArr.remove(obj);
                        if (dayArr.isEmpty()) {
                            messagesByDays.remove(obj.dateKey);
                            messages.remove(index);
                        }
                        updated = true;
                    }
                }
            }
            if (messages.isEmpty()) {
                if (!endReached && !loading) {
                    progressView.setVisibility(View.INVISIBLE);
                    chatListView.setEmptyView(null);
                    if (currentEncryptedChat == null) {
                        maxMessageId = Integer.MAX_VALUE;
                        minMessageId = Integer.MIN_VALUE;
                    } else {
                        maxMessageId = Integer.MIN_VALUE;
                        minMessageId = Integer.MAX_VALUE;
                    }
                    maxDate = Integer.MIN_VALUE;
                    minDate = 0;
                    MessagesController.getInstance().loadMessages(dialog_id, 30, 0, !cacheEndReaced, minDate, classGuid, 0, 0, 0, true);
                    loading = true;
                }
            }
            if (updated && chatAdapter != null) {
                removeUnreadPlane(false);
                chatAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                TLRPC.Message newMsgObj = (TLRPC.Message)args[2];
                if (newMsgObj != null) {
                    obj.messageOwner.media = newMsgObj.media;
                    obj.generateThumbs(true, 1);
                }
                messagesDict.remove(msgId);
                messagesDict.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.messageReceivedByAck) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.messageSendError) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.chatInfoDidLoaded) {
            int chatId = (Integer)args[0];
            if (currentChat != null && chatId == currentChat.id) {
                info = (TLRPC.ChatParticipants)args[1];
                updateOnlineCount();
                updateSubtitle();
                if (isBroadcast) {
                    SendMessagesHelper.getInstance().setCurrentChatInfo(info);
                }
            }
        } else if (id == NotificationCenter.contactsDidLoaded) {
            updateContactStatus();
            updateSubtitle();
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateContactStatus();
                updateSecretStatus();
            }
        } else if (id == NotificationCenter.messagesReadedEncrypted) {
            int encId = (Integer)args[0];
            if (currentEncryptedChat != null && currentEncryptedChat.id == encId) {
                int date = (Integer)args[1];
                boolean started = false;
                for (MessageObject obj : messages) {
                    if (!obj.isOut()) {
                        continue;
                    } else if (obj.isOut() && !obj.isUnread()) {
                        break;
                    }
                    if (obj.messageOwner.date - 1 <= date) {
                        obj.setIsRead();
                    }
                }
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.audioDidReset) {
            Integer mid = (Integer)args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatAudioCell) {
                        ChatAudioCell cell = (ChatAudioCell)view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == mid) {
                            cell.updateButtonState();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.audioProgressDidChanged) {
            Integer mid = (Integer)args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatAudioCell) {
                        ChatAudioCell cell = (ChatAudioCell)view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == mid) {
                            cell.updateProgress();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.removeAllMessagesFromDialog) {
            long did = (Long)args[0];
            if (dialog_id == did) {
                messages.clear();
                messagesByDays.clear();
                messagesDict.clear();
                progressView.setVisibility(View.INVISIBLE);
                chatListView.setEmptyView(emptyViewContainer);
                if (currentEncryptedChat == null) {
                    maxMessageId = Integer.MAX_VALUE;
                    minMessageId = Integer.MIN_VALUE;
                } else {
                    maxMessageId = Integer.MIN_VALUE;
                    minMessageId = Integer.MAX_VALUE;
                }
                maxDate = Integer.MIN_VALUE;
                minDate = 0;
                selectedMessagesIds.clear();
                selectedMessagesCanCopyIds.clear();
                actionBar.hideActionMode();
                chatAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.screenshotTook) {
            updateInformationForScreenshotDetector();
        } else if (id == NotificationCenter.blockedUsersDidLoaded) {
            if (currentUser != null) {
                boolean oldValue = userBlocked;
                userBlocked = MessagesController.getInstance().blockedUsers.contains(currentUser.id);
                if (oldValue != userBlocked) {
                    updateBottomOverlay();
                }
            }
        } else if (id == NotificationCenter.FileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject)args[0];
            long finalSize = (Long)args[2];
            if (finalSize != 0 && dialog_id == messageObject.getDialogId()) {
                MessageObject currentObject = messagesDict.get(messageObject.messageOwner.id);
                if (currentObject != null) {
                    currentObject.messageOwner.media.video.size = (int)finalSize;
                    updateVisibleRows();
                }
            }
        } else if (id == NotificationCenter.didCreatedNewDeleteTask) {
            SparseArray<ArrayList<Integer>> mids = (SparseArray<ArrayList<Integer>>)args[0];
            boolean changed = false;
            for(int i = 0; i < mids.size(); i++) {
                int key = mids.keyAt(i);
                ArrayList<Integer> arr = mids.get(key);
                for (Integer mid : arr) {
                    MessageObject messageObject = messagesDict.get(mid);
                    if (messageObject != null) {
                        messageObject.messageOwner.destroyTime = key;
                        changed = true;
                    }
                }
            }
            if (changed) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.audioDidStarted) {
            MessageObject messageObject = (MessageObject)args[0];
            sendSecretMessageRead(messageObject);
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        openAnimationEnded = true;
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMediaCell) {
                ChatMediaCell cell = (ChatMediaCell)view;
                cell.setAllowedToSetPhoto(true);
            }
        }

        if (currentUser != null) {
            MessagesController.getInstance().loadFullUser(MessagesController.getInstance().getUser(currentUser.id), classGuid);
        }
    }

    private void updateBottomOverlay() {
        if (currentUser == null) {
            bottomOverlayChatText.setText(LocaleController.getString("DeleteThisGroup", R.string.DeleteThisGroup));
        } else {
            if (userBlocked) {
                bottomOverlayChatText.setText(LocaleController.getString("Unblock", R.string.Unblock));
            } else {
                bottomOverlayChatText.setText(LocaleController.getString("DeleteThisChat", R.string.DeleteThisChat));
            }
        }
        if (currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left) ||
                currentUser != null && (currentUser instanceof TLRPC.TL_userDeleted || currentUser instanceof TLRPC.TL_userEmpty || userBlocked)) {
            bottomOverlayChat.setVisibility(View.VISIBLE);
            chatActivityEnterView.setFieldFocused(false);
        } else {
            bottomOverlayChat.setVisibility(View.GONE);
        }
    }

    private void updateContactStatus() {
        if (addContactItem == null) {
            return;
        }
        if (currentUser == null) {
            addContactItem.setVisibility(View.GONE);
        } else {
            TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
            if (user != null) {
                currentUser = user;
            }
            if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat)
                    || currentUser.id / 1000 == 333 || currentUser.id / 1000 == 777
                    || currentUser instanceof TLRPC.TL_userEmpty || currentUser instanceof TLRPC.TL_userDeleted
                    || ContactsController.getInstance().isLoadingContacts()
                    || (currentUser.phone != null && currentUser.phone.length() != 0 && ContactsController.getInstance().contactsDict.get(currentUser.id) != null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts()))) {
                addContactItem.setVisibility(View.GONE);
            } else {
                addContactItem.setVisibility(View.VISIBLE);
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    addContactItem.setText(LocaleController.getString("AddToContacts", R.string.AddToContacts));
                } else {
                    addContactItem.setText(LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!AndroidUtilities.isTablet()) {
            getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }

        checkActionBarMenu();

        NotificationsController.getInstance().setOpennedDialogId(dialog_id);
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && scrollToMessage != null) {
                if (chatListView != null) {
                    final int yOffset = scrollToMessageMiddleScreen ? Math.max(0, (AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - scrollToMessage.textHeight - AndroidUtilities.getCurrentActionBarHeight() - AndroidUtilities.dp(48)) / 2) : 0;
                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(scrollToMessage), -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
                }
            } else {
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                }
            }
            scrollToTopUnReadOnResume = false;
            scrollToTopOnResume = false;
            scrollToMessage = null;
        }
        paused = false;
        if (readWhenResume && !messages.isEmpty()) {
            for (MessageObject messageObject : messages) {
                if (!messageObject.isUnread() && !messageObject.isFromMe()) {
                    break;
                }
                if (!messageObject.isOut()) {
                    messageObject.setIsRead();
                }
            }
            readWhenResume = false;
            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, readWithMid, 0, readWithDate, true, false);
        }

        fixLayout(true);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String lastMessageText = preferences.getString("dialog_" + dialog_id, null);
        if (lastMessageText != null) {
            preferences.edit().remove("dialog_" + dialog_id).commit();
            chatActivityEnterView.setFieldText(lastMessageText);
        }
        if (bottomOverlayChat.getVisibility() != View.VISIBLE) {
            chatActivityEnterView.setFieldFocused(true);
        }
        if (currentEncryptedChat != null) {
            chatEnterTime = System.currentTimeMillis();
            chatLeaveTime = 0;
        }

        if (startVideoEdit != null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    openVideoEditor(startVideoEdit, false);
                    startVideoEdit = null;
                }
            });
        }

        chatListView.setOnItemLongClickListener(onItemLongClickListener);
        chatListView.setOnItemClickListener(onItemClickListener);
        chatListView.setLongClickable(true);
    }

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        chatActivityEnterView.hideEmojiPopup();
    }

    @Override
    public void onPause() {
        super.onPause();
        chatActivityEnterView.hideEmojiPopup();
        paused = true;
        NotificationsController.getInstance().setOpennedDialogId(0);

        String text = chatActivityEnterView.getFieldText();
        if (text != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("dialog_" + dialog_id, text);
            editor.commit();
        }

        chatActivityEnterView.setFieldFocused(false);
        MessagesController.getInstance().cancelTyping(dialog_id);

        if (currentEncryptedChat != null) {
            chatLeaveTime = System.currentTimeMillis();
            updateInformationForScreenshotDetector();
        }
    }

    private void updateInformationForScreenshotDetector() {
        if (currentEncryptedChat == null) {
            return;
        }
        ArrayList<Long> visibleMessages = new ArrayList<Long>();
        if (chatListView != null) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                MessageObject object = null;
                if (view instanceof ChatBaseCell) {
                    ChatBaseCell cell = (ChatBaseCell) view;
                    object = cell.getMessageObject();
                }
                if (object != null && object.messageOwner.id < 0 && object.messageOwner.random_id != 0) {
                    visibleMessages.add(object.messageOwner.random_id);
                }
            }
        }
        MediaController.getInstance().setLastEncryptedChatParams(chatEnterTime, chatLeaveTime, currentEncryptedChat, visibleMessages);
    }

    private void fixLayout(final boolean resume) {
        if (avatarContainer != null) {
            avatarContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (avatarContainer != null) {
                        avatarContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (!AndroidUtilities.isTablet() && getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        selectedMessagesCountTextView.setTextSize(18);
                    } else {
                        selectedMessagesCountTextView.setTextSize(20);
                    }
                    int padding = (AndroidUtilities.getCurrentActionBarHeight() - AndroidUtilities.dp(48)) / 2;
                    avatarContainer.setPadding(avatarContainer.getPaddingLeft(), padding, avatarContainer.getPaddingRight(), padding);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)avatarContainer.getLayoutParams();
                    layoutParams.topMargin = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                    avatarContainer.setLayoutParams(layoutParams);
                    return false;
                }
            });
        }
        if (!resume && chatListView != null) {
            final int lastPos = chatListView.getLastVisiblePosition();
            chatListView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (chatListView == null) {
                        return false;
                    }
                    chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (lastPos >= messages.size() - 1) {
                        chatListView.post(new Runnable() {
                            @Override
                            public void run() {
                                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                            }
                        });
                    }
                    return false;
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        fixLayout(false);
    }

    public void createMenu(View v, boolean single) {
        if (actionBar.isActionModeShowed()) {
            return;
        }

        MessageObject message = null;
        if (v instanceof ChatBaseCell) {
            message = ((ChatBaseCell)v).getMessageObject();
        } else if (v instanceof ChatActionCell) {
            message = ((ChatActionCell)v).getMessageObject();
        }
        if (message == null) {
            return;
        }
        final int type = getMessageType(message);

        selectedObject = null;
        forwaringMessage = null;
        selectedMessagesCanCopyIds.clear();
        selectedMessagesIds.clear();
        actionBar.hideActionMode();

        if (single || type < 2 || type == 6) {
            if (type >= 0) {
                selectedObject = message;
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                CharSequence[] items = null;

                if (type == 0) {
                    items = new CharSequence[] {LocaleController.getString("Retry", R.string.Retry), LocaleController.getString("Delete", R.string.Delete)};
                } else if (type == 1) {
                    items = new CharSequence[] {LocaleController.getString("Delete", R.string.Delete)};
                } else if (type == 6) {
                    items = new CharSequence[] {LocaleController.getString("Retry", R.string.Retry), LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Delete", R.string.Delete)};
                } else {
                    if (currentEncryptedChat == null) {
                        if (type == 2) {
                            items = new CharSequence[]{LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 3) {
                            items = new CharSequence[]{LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 4) {
                            items = new CharSequence[]{LocaleController.getString(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? "SaveToDownloads" : "SaveToGallery",
                                    selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? R.string.SaveToDownloads : R.string.SaveToGallery), LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 5) {
                            items = new CharSequence[]{LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile), LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads), LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Delete", R.string.Delete)};
                        }
                    } else {
                        if (type == 2) {
                            items = new CharSequence[]{LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 3) {
                            items = new CharSequence[]{LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 4) {
                            items = new CharSequence[]{LocaleController.getString(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? "SaveToDownloads" : "SaveToGallery",
                                    selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? R.string.SaveToDownloads : R.string.SaveToGallery), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 5) {
                            items = new CharSequence[]{LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile), LocaleController.getString("Delete", R.string.Delete)};
                        }
                    }
                }

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (selectedObject == null) {
                            return;
                        }
                        if (type == 0) {
                            if (i == 0) {
                                processSelectedOption(0);
                            } else if (i == 1) {
                                processSelectedOption(1);
                            }
                        } else if (type == 1) {
                            processSelectedOption(1);
                        } else if (type == 2) {
                            if (currentEncryptedChat == null) {
                                if (i == 0) {
                                    processSelectedOption(2);
                                } else if (i == 1) {
                                    processSelectedOption(1);
                                }
                            } else {
                                processSelectedOption(1);
                            }
                        } else if (type == 3) {
                            if (currentEncryptedChat == null) {
                                if (i == 0) {
                                    processSelectedOption(2);
                                } else if (i == 1) {
                                    processSelectedOption(3);
                                } else if (i == 2) {
                                    processSelectedOption(1);
                                }
                            } else {
                                if (i == 0) {
                                    processSelectedOption(3);
                                } else if (i == 1) {
                                    processSelectedOption(1);
                                }
                            }
                        } else if (type == 4) {
                            if (currentEncryptedChat == null) {
                                if (i == 0) {
                                    processSelectedOption(4);
                                } else if (i == 1) {
                                    processSelectedOption(2);
                                } else if (i == 2) {
                                    processSelectedOption(1);
                                }
                            } else {
                                if (i == 0) {
                                    processSelectedOption(4);
                                } else if (i == 1) {
                                    processSelectedOption(1);
                                }
                            }
                        } else if (type == 5) {
                            if (i == 0) {
                                processSelectedOption(5);
                            } else {
                                if (currentEncryptedChat == null) {
                                    if (i == 1) {
                                        processSelectedOption(4);
                                    } else if (i == 2) {
                                        processSelectedOption(2);
                                    } else if (i == 3) {
                                        processSelectedOption(1);
                                    }
                                } else {
                                    if (i == 1) {
                                        processSelectedOption(1);
                                    }
                                }
                            }
                        } else if (type == 6) {
                            if (i == 0) {
                                processSelectedOption(0);
                            } else if (i == 1) {
                                processSelectedOption(3);
                            } else if (i == 2) {
                                processSelectedOption(1);
                            }
                        }
                    }
                });

                builder.setTitle(LocaleController.getString("Message", R.string.Message));
                showAlertDialog(builder);
            }
            return;
        }
        actionBar.showActionMode();

        if (Build.VERSION.SDK_INT >= 11) {
            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            ArrayList<Object> animators = new ArrayList<Object>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View view = actionModeViews.get(a);
                AndroidUtilities.clearDrawableAnimation(view);
                if (a < 1) {
                    animators.add(ObjectAnimatorProxy.ofFloat(view, "translationX", -AndroidUtilities.dp(56), 0));
                } else {
                    animators.add(ObjectAnimatorProxy.ofFloat(view, "scaleY", 0.1f, 1.0f));
                }
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(250);
            animatorSet.start();
        }

        addToSelectedMessages(message);
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void processSelectedOption(int option) {
        if (selectedObject == null) {
            return;
        }
        if (option == 0) {
            if (SendMessagesHelper.getInstance().retrySendMessage(selectedObject, false)) {
                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
            }
        } else if (option == 1) {
            ArrayList<Integer> ids = new ArrayList<Integer>();
            ids.add(selectedObject.messageOwner.id);
            removeUnreadPlane(true);
            ArrayList<Long> random_ids = null;
            if (currentEncryptedChat != null && selectedObject.messageOwner.random_id != 0 && selectedObject.type != 10) {
                random_ids = new ArrayList<Long>();
                random_ids.add(selectedObject.messageOwner.random_id);
            }
            MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat);
        } else if (option == 2) {
            forwaringMessage = selectedObject;
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("serverOnly", true);
            args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
            args.putString("selectAlertStringGroup", LocaleController.getString("ForwardMessagesToGroup", R.string.ForwardMessagesToGroup));
            MessagesActivity fragment = new MessagesActivity(args);
            fragment.setDelegate(this);
            presentFragment(fragment);
        } else if (option == 3) {
            if(Build.VERSION.SDK_INT < 11) {
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(selectedObject.messageText);
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", selectedObject.messageText);
                clipboard.setPrimaryClip(clip);
            }
        } else if (option == 4) {
            String fileName = selectedObject.getFileName();
            String path = selectedObject.messageOwner.attachPath;
            if (path != null && path.length() > 0) {
                File temp = new File(path);
                if (!temp.exists()) {
                    path = null;
                }
            }
            if (path == null || path.length() == 0) {
                path = FileLoader.getPathToMessage(selectedObject.messageOwner).toString();
            }
            if (selectedObject.type == 3) {
                MediaController.saveFile(path, getParentActivity(), 1, null);
            } else if (selectedObject.type == 1) {
                MediaController.saveFile(path, getParentActivity(), 0, null);
            } else if (selectedObject.type == 8 || selectedObject.type == 9) {
                MediaController.saveFile(path, getParentActivity(), 2, selectedObject.messageOwner.media.document.file_name);
            }
        } else if (option == 5) {
            File locFile = null;
            if (selectedObject.messageOwner.attachPath != null && selectedObject.messageOwner.attachPath.length() != 0) {
                File f = new File(selectedObject.messageOwner.attachPath);
                if (f.exists()) {
                    locFile = f;
                }
            }
            if (locFile == null) {
                File f = FileLoader.getPathToMessage(selectedObject.messageOwner);
                if (f.exists()) {
                    locFile = f;
                }
            }
            if (locFile != null) {
                if (LocaleController.getInstance().applyLanguageFile(locFile)) {
                    presentFragment(new LanguageSelectActivity());
                } else {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("IncorrectLocalization", R.string.IncorrectLocalization));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    showAlertDialog(builder);
                }
            }
        }
        selectedObject = null;
    }

    private void forwardSelectedMessages(long did, boolean fromMyName) {
        if (forwaringMessage != null) {
            if (!fromMyName) {
                if (forwaringMessage.messageOwner.id > 0) {
                    SendMessagesHelper.getInstance().sendMessage(forwaringMessage, did);
                }
            } else {
                SendMessagesHelper.getInstance().processForwardFromMyName(forwaringMessage, did);
            }
            forwaringMessage = null;
        } else {
            ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
            Collections.sort(ids);
            for (Integer id : ids) {
                if (!fromMyName) {
                    if (id > 0) {
                        SendMessagesHelper.getInstance().sendMessage(selectedMessagesIds.get(id), did);
                    }
                } else {
                    SendMessagesHelper.getInstance().processForwardFromMyName(selectedMessagesIds.get(id), did);
                }
            }
            selectedMessagesCanCopyIds.clear();
            selectedMessagesIds.clear();
            actionBar.hideActionMode();
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity activity, long did, boolean param) {
        if (dialog_id != 0 && (forwaringMessage != null || !selectedMessagesIds.isEmpty())) {
            if (isBroadcast) {
                param = true;
            }
            if (did != dialog_id) {
                int lower_part = (int)did;
                if (lower_part != 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("scrollToTopOnResume", scrollToTopOnResume);
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args.putInt("chat_id", -lower_part);
                    }
                    forwardSelectedMessages(did, param);
                    ChatActivity chatActivity = new ChatActivity(args);
                    presentFragment(chatActivity, true);
                    if (!AndroidUtilities.isTablet()) {
                        removeSelfFromStack();
                        Activity parentActivity = getParentActivity();
                        if (parentActivity == null) {
                            parentActivity = chatActivity.getParentActivity();
                        }
                        if (parentActivity != null) {
                            parentActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                        }
                    }
                } else {
                    activity.finishFragment();
                }
            } else {
                activity.finishFragment();
                forwardSelectedMessages(did, param);
                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                scrollToTopOnResume = true;
                if (AndroidUtilities.isTablet()) {
                    actionBar.hideActionMode();
                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (actionBar.isActionModeShowed()) {
            selectedMessagesIds.clear();
            selectedMessagesCanCopyIds.clear();
            actionBar.hideActionMode();
            updateVisibleRows();
            return false;
        } else if (chatActivityEnterView.isEmojiPopupShowing()) {
            chatActivityEnterView.hideEmojiPopup();
            return false;
        }
        return true;
    }

    public boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0);
            return true;
        } catch(PackageManager.NameNotFoundException e) {
            if (getParentActivity() == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage("Install Google Maps?");
            builder.setCancelable(true);
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps"));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            showAlertDialog(builder);
            return false;
        }
    }

    private void updateVisibleRows() {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatBaseCell) {
                ChatBaseCell cell = (ChatBaseCell)view;

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBar.isActionModeShowed()) {
                    if (selectedMessagesIds.containsKey(cell.getMessageObject().messageOwner.id)) {
                        view.setBackgroundColor(0x6633b5e5);
                        selected = true;
                    } else {
                        view.setBackgroundColor(0);
                    }
                    disableSelection = true;
                } else {
                    view.setBackgroundColor(0);
                }

                cell.setMessageObject(cell.getMessageObject());

                cell.setCheckPressed(!disableSelection, disableSelection && selected);

                if (highlightMessageId != Integer.MAX_VALUE && cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == highlightMessageId) {
                    cell.setCheckPressed(false, true);
                }
            }
        }
    }

    private void alertUserOpenError(MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        if (message.type == 3) {
            builder.setMessage(LocaleController.getString("NoPlayerInstalled", R.string.NoPlayerInstalled));
        } else {
            builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.messageOwner.media.document.mime_type));
        }
        showAlertDialog(builder);
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (messageObject == null) {
            return null;
        }
        int count = chatListView.getChildCount();

        for (int a = 0; a < count; a++) {
            MessageObject messageToOpen = null;
            ImageReceiver imageReceiver = null;
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMediaCell) {
                ChatMediaCell cell = (ChatMediaCell)view;
                MessageObject message = cell.getMessageObject();
                if (message != null && message.messageOwner.id == messageObject.messageOwner.id) {
                    messageToOpen = message;
                    imageReceiver = cell.getPhotoImage();
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell)view;
                MessageObject message = cell.getMessageObject();
                if (message != null && message.messageOwner.id == messageObject.messageOwner.id) {
                    messageToOpen = message;
                    imageReceiver = cell.getPhotoImage();
                }
            }

            if (messageToOpen != null) {
                int coords[] = new int[2];
                view.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                object.parentView = chatListView;
                object.imageReceiver = imageReceiver;
                object.thumb = imageReceiver.getBitmap();
                object.radius = imageReceiver.getRoundRadius();
                return object;
            }
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() { }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private class ChatAdapter extends BaseFragmentAdapter {

        private Context mContext;

        public ChatAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            int count = messages.size();
            if (count != 0) {
                if (!endReached) {
                    count++;
                }
                if (!forward_end_reached) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int offset = 1;
            if ((!endReached || !forward_end_reached) && messages.size() != 0) {
                if (!endReached) {
                    offset = 0;
                }
                if (i == 0 && !endReached || !forward_end_reached && i == (messages.size() + 1 - offset)) {
                    View progressBar = null;
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.chat_loading_layout, viewGroup, false);
                        progressBar = view.findViewById(R.id.progressLayout);
                        if (isCustomTheme) {
                            progressBar.setBackgroundResource(R.drawable.system_loader2);
                        } else {
                            progressBar.setBackgroundResource(R.drawable.system_loader1);
                        }
                    } else {
                        progressBar = view.findViewById(R.id.progressLayout);
                    }
                    progressBar.setVisibility(loadsCount > 1 ? View.VISIBLE : View.INVISIBLE);

                    return view;
                }
            }
            final MessageObject message = messages.get(messages.size() - i - offset);
            int type = message.contentType;
            if (view == null) {
                if (type == 0) {
                    view = new ChatMessageCell(mContext);
                } if (type == 1) {
                    view = new ChatMediaCell(mContext);
                } else if (type == 2) {
                    view = new ChatAudioCell(mContext);
                } else if (type == 3) {
                    view = new ChatContactCell(mContext);
                } else if (type == 6) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.chat_unread_layout, viewGroup, false);
                } else if (type == 4) {
                    view = new ChatActionCell(mContext);
                }

                if (view instanceof ChatBaseCell) {
                    ((ChatBaseCell)view).setDelegate(new ChatBaseCell.ChatBaseCellDelegate() {
                        @Override
                        public void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user) {
                            if (actionBar.isActionModeShowed()) {
                                processRowSelect(cell);
                                return;
                            }
                            if (user != null && user.id != UserConfig.getClientUserId()) {
                                Bundle args = new Bundle();
                                args.putInt("user_id", user.id);
                                presentFragment(new ProfileActivity(args));
                            }
                        }

                        @Override
                        public void didPressedCancelSendButton(ChatBaseCell cell) {
                            MessageObject message = cell.getMessageObject();
                            if (message.messageOwner.send_state != 0) {
                                SendMessagesHelper.getInstance().cancelSendingMessage(message);
                            }
                        }

                        @Override
                        public void didLongPressed(ChatBaseCell cell) {
                            createMenu(cell, false);
                        }

                        @Override
                        public boolean canPerformActions() {
                            return actionBar != null && !actionBar.isActionModeShowed();
                        }
                    });
                    if (view instanceof ChatMediaCell) {
                        ((ChatMediaCell) view).setAllowedToSetPhoto(openAnimationEnded);
                        ((ChatMediaCell) view).setMediaDelegate(new ChatMediaCell.ChatMediaCellDelegate() {
                            @Override
                            public void didClickedImage(ChatMediaCell cell) {
                                MessageObject message = cell.getMessageObject();
                                if (message.isSendError()) {
                                    createMenu(cell, false);
                                    return;
                                } else if (message.isSending()) {
                                    return;
                                }
                                if (message.type == 1) {
                                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                                    PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                                } else if (message.type == 3) {
                                    sendSecretMessageRead(message);
                                    try {
                                        File f = null;
                                        if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                            f = new File(message.messageOwner.attachPath);
                                        }
                                        if (f == null || f != null && !f.exists()) {
                                            f = FileLoader.getPathToMessage(message.messageOwner);
                                        }
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                        getParentActivity().startActivity(intent);
                                    } catch (Exception e) {
                                        alertUserOpenError(message);
                                    }
                                } else if (message.type == 4) {
                                    if (!isGoogleMapsInstalled()) {
                                        return;
                                    }
                                    LocationActivity fragment = new LocationActivity();
                                    fragment.setMessageObject(message);
                                    presentFragment(fragment);
                                } else if (message.type == 9) {
                                    File f = null;
                                    String fileName = message.getFileName();
                                    if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                        f = new File(message.messageOwner.attachPath);
                                    }
                                    if (f == null || f != null && !f.exists()) {
                                        f = FileLoader.getPathToMessage(message.messageOwner);
                                    }
                                    if (f != null && f.exists()) {
                                        String realMimeType = null;
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW);
                                            if (message.type == 8 || message.type == 9) {
                                                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                                                int idx = fileName.lastIndexOf(".");
                                                if (idx != -1) {
                                                    String ext = fileName.substring(idx + 1);
                                                    realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                                    if (realMimeType == null) {
                                                        realMimeType = message.messageOwner.media.document.mime_type;
                                                        if (realMimeType == null || realMimeType.length() == 0) {
                                                            realMimeType = null;
                                                        }
                                                    }
                                                    if (realMimeType != null) {
                                                        intent.setDataAndType(Uri.fromFile(f), realMimeType);
                                                    } else {
                                                        intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                                    }
                                                } else {
                                                    intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                                }
                                            }
                                            if (realMimeType != null) {
                                                try {
                                                    getParentActivity().startActivity(intent);
                                                } catch (Exception e) {
                                                    intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                                    getParentActivity().startActivity(intent);
                                                }
                                            } else {
                                                getParentActivity().startActivity(intent);
                                            }
                                        } catch (Exception e) {
                                            alertUserOpenError(message);
                                        }
                                    }
                                }
                            }

                            @Override
                            public void didPressedOther(ChatMediaCell cell) {
                                createMenu(cell, true);
                            }
                        });
                    } else if (view instanceof ChatContactCell) {
                        ((ChatContactCell)view).setContactDelegate(new ChatContactCell.ChatContactCellDelegate() {
                            @Override
                            public void didClickAddButton(ChatContactCell cell, TLRPC.User user) {
                                if (actionBar.isActionModeShowed()) {
                                    processRowSelect(cell);
                                    return;
                                }
                                MessageObject messageObject = cell.getMessageObject();
                                Bundle args = new Bundle();
                                args.putInt("user_id", messageObject.messageOwner.media.user_id);
                                args.putString("phone", messageObject.messageOwner.media.phone_number);
                                args.putBoolean("addContact", true);
                                presentFragment(new ContactAddActivity(args));
                            }

                            @Override
                            public void didClickPhone(ChatContactCell cell) {
                                if (actionBar.isActionModeShowed()) {
                                    processRowSelect(cell);
                                    return;
                                }
                                final MessageObject messageObject = cell.getMessageObject();
                                if (getParentActivity() == null || messageObject.messageOwner.media.phone_number == null || messageObject.messageOwner.media.phone_number.length() == 0) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Call", R.string.Call)}, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                if (i == 1) {
                                                    try {
                                                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + messageObject.messageOwner.media.phone_number));
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                        getParentActivity().startActivity(intent);
                                                    } catch (Exception e) {
                                                        FileLog.e("tmessages", e);
                                                    }
                                                } else if (i == 0) {
                                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                                                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                        clipboard.setText(messageObject.messageOwner.media.phone_number);
                                                    } else {
                                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", messageObject.messageOwner.media.phone_number);
                                                        clipboard.setPrimaryClip(clip);
                                                    }
                                                }
                                            }
                                        }
                                );
                                showAlertDialog(builder);
                            }
                        });
                    }
                } else if (view instanceof ChatActionCell) {
                    ((ChatActionCell)view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                        @Override
                        public void didClickedImage(ChatActionCell cell) {
                            MessageObject message = cell.getMessageObject();
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                        }

                        @Override
                        public void didLongPressed(ChatActionCell cell) {
                            createMenu(cell, false);
                        }

                        @Override
                        public void needOpenUserProfile(int uid) {
                            if (uid != UserConfig.getClientUserId()) {
                                Bundle args = new Bundle();
                                args.putInt("user_id", uid);
                                presentFragment(new ProfileActivity(args));
                            }
                        }
                    });
                }
            }

            boolean selected = false;
            boolean disableSelection = false;
            if (actionBar.isActionModeShowed()) {
                if (selectedMessagesIds.containsKey(message.messageOwner.id)) {
                    view.setBackgroundColor(0x6633b5e5);
                    selected = true;
                } else {
                    view.setBackgroundColor(0);
                }
                disableSelection = true;
            } else {
                view.setBackgroundColor(0);
            }

            if (view instanceof ChatBaseCell) {
                ChatBaseCell baseCell = (ChatBaseCell)view;
                baseCell.isChat = currentChat != null;
                baseCell.setMessageObject(message);
                baseCell.setCheckPressed(!disableSelection, disableSelection && selected);
                if (view instanceof ChatAudioCell && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_AUDIO)) {
                    ((ChatAudioCell)view).downloadAudioIfNeed();
                }
                if (highlightMessageId != Integer.MAX_VALUE && message.messageOwner.id == highlightMessageId) {
                    baseCell.setCheckPressed(false, true);
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell actionCell = (ChatActionCell)view;
                actionCell.setMessageObject(message);
                actionCell.setUseBlackBackground(isCustomTheme);
            }
            if (type == 6) {
                TextView messageTextView = (TextView)view.findViewById(R.id.chat_message_text);
                messageTextView.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            int offset = 1;
            if (!endReached && messages.size() != 0) {
                offset = 0;
                if (i == 0) {
                    return 5;
                }
            }
            if (!forward_end_reached && i == (messages.size() + 1 - offset)) {
                return 5;
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            return message.contentType;
        }

        @Override
        public int getViewTypeCount() {
            return 7;
        }

        @Override
        public boolean isEmpty() {
            int count = messages.size();
            if (count != 0) {
                if (!endReached) {
                    count++;
                }
                if (!forward_end_reached) {
                    count++;
                }
            }
            return count == 0;
        }
    }
}
