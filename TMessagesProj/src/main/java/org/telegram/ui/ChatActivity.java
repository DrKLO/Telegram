/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.ImageLoader;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationsController;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessageObject;
import org.telegram.android.PhotoObject;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.ChatAudioCell;
import org.telegram.ui.Cells.ChatBaseCell;
import org.telegram.ui.Cells.ChatMediaCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.ChatActivityEnterView;
import org.telegram.android.ImageReceiver;
import org.telegram.ui.Views.LayoutListView;
import org.telegram.ui.Views.MessageActionLayout;
import org.telegram.ui.Views.SizeNotifierRelativeLayout;
import org.telegram.ui.Views.TimerButton;
import org.telegram.ui.Views.TypingDotsDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class ChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate,
        DocumentSelectActivity.DocumentSelectActivityDelegate, PhotoViewer.PhotoViewerProvider, PhotoPickerActivity.PhotoPickerActivityDelegate,
        VideoEditorActivity.VideoEditorActivityDelegate, LocationActivity.LocationActivityDelegate {

    private TLRPC.Chat currentChat;
    private TLRPC.User currentUser;
    private TLRPC.EncryptedChat currentEncryptedChat;
    private boolean userBlocked = false;

    private View topPanel;
    private View progressView;
    private View bottomOverlay;
    private ChatAdapter chatAdapter;
    private ChatActivityEnterView chatActivityEnterView;
    private View timeItem;
    private View menuItem;
    private LayoutListView chatListView;
    private BackupImageView avatarImageView;
    private TextView bottomOverlayChatText;
    private View bottomOverlayChat;
    private TypingDotsDrawable typingDotsDrawable;
    private View emptyViewContainer;
    private ArrayList<View> actionModeViews = new ArrayList<View>();

    private TextView bottomOverlayText;

    private MessageObject selectedObject;
    private MessageObject forwaringMessage;
    private TextView secretViewStatusTextView;
    private TimerButton timerButton;
    private TextView selectedMessagesCountTextView;
    private boolean paused = true;
    private boolean readWhenResume = false;

    private int readWithDate = 0;
    private int readWithMid = 0;
    private boolean scrollToTopOnResume = false;
    private boolean scrollToTopUnReadOnResume = false;
    private boolean isCustomTheme = false;
    private ImageView topPlaneClose;
    private View pagedownButton;
    private TextView topPanelText;
    private long dialog_id;
    private boolean isBraodcast = false;
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

    private int minDate = 0;
    private boolean first = true;
    private int unread_to_load = 0;
    private int first_unread_id = 0;
    private int last_unread_id = 0;
    private boolean unread_end_reached = true;
    private boolean loadingForward = false;
    private MessageObject unreadMessageObject = null;

    private String currentPicturePath;

    private TLRPC.ChatParticipants info = null;
    private int onlineCount = -1;

    private CharSequence lastPrintString;

    private long chatEnterTime = 0;
    private long chatLeaveTime = 0;

    private String startVideoEdit = null;

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
    private final static int chat_menu_avatar = 11;

    public ChatActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        final int chatId = arguments.getInt("chat_id", 0);
        final int userId = arguments.getInt("user_id", 0);
        final int encId = arguments.getInt("enc_id", 0);
        scrollToTopOnResume = arguments.getBoolean("scrollToTopOnResume", false);

        if (chatId != 0) {
            currentChat = MessagesController.getInstance().getChat(chatId);
            if (currentChat == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
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
                isBraodcast = true;
                dialog_id = AndroidUtilities.makeBroadcastId(chatId);
            }
            Semaphore semaphore = null;
            if (isBraodcast) {
                semaphore = new Semaphore(0);
            }
            MessagesController.getInstance().loadChatInfo(currentChat.id, semaphore);
            if (isBraodcast) {
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
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
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
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
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
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
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

        super.onFragmentCreate();

        loading = true;
        MessagesController.getInstance().loadMessages(dialog_id, 30, 0, true, 0, classGuid, true, false);
        if (currentUser != null) {
            userBlocked = MessagesController.getInstance().blockedUsers.contains(currentUser.id);
        }

        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, false);
        }

        typingDotsDrawable = new TypingDotsDrawable();
        typingDotsDrawable.setIsChat(currentChat != null);

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
        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, true);
        }
        if (currentEncryptedChat != null) {
            MediaController.getInstance().stopMediaObserver();
        }

        AndroidUtilities.unlockOrientation(getParentActivity());
        MediaController.getInstance().stopAudio();
    }

    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            if (AndroidUtilities.isTablet()) {
                actionBarLayer.setExtraLeftMargin(4);
            }
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == -2) {
                        selectedMessagesIds.clear();
                        selectedMessagesCanCopyIds.clear();
                        actionBarLayer.hideActionMode();
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
                        fragment.setDelegate(ChatActivity.this);
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
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    } else if (id == attach_document) {
                        DocumentSelectActivity fragment = new DocumentSelectActivity();
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    } else if (id == chat_menu_avatar) {
                        if (currentUser != null) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", currentUser.id);
                            if (currentEncryptedChat != null) {
                                args.putLong("dialog_id", dialog_id);
                            }
                            presentFragment(new UserProfileActivity(args));
                        } else if (currentChat != null) {
                            if (info != null && info instanceof TLRPC.TL_chatParticipantsForbidden) {
                                return;
                            }
                            int count = currentChat.participants_count;
                            if (info != null) {
                                count = info.participants.size();
                            }
                            if (count == 0 || currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden) {
                                return;
                            }
                            Bundle args = new Bundle();
                            args.putInt("chat_id", currentChat.id);
                            ChatProfileActivity fragment = new ChatProfileActivity(args);
                            fragment.setChatInfo(info);
                            presentFragment(fragment);
                        }
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
                        actionBarLayer.hideActionMode();
                        updateVisibleRows();
                    } else if (id == delete) {
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
                        actionBarLayer.hideActionMode();
                    } else if (id == forward) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putBoolean("serverOnly", true);
                        args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
                        args.putString("selectAlertStringGroup", LocaleController.getString("ForwardMessagesToGroup", R.string.ForwardMessagesToGroup));
                        MessagesActivity fragment = new MessagesActivity(args);
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    }
                }
            });

            updateSubtitle();

            if (currentEncryptedChat != null) {
                actionBarLayer.setTitleIcon(R.drawable.ic_lock_white, AndroidUtilities.dp(4));
            } else if (currentChat != null && currentChat.id < 0) {
                actionBarLayer.setTitleIcon(R.drawable.broadcast2, AndroidUtilities.dp(4));
            }

            ActionBarMenu menu = actionBarLayer.createMenu();

            if (currentEncryptedChat != null) {
                timeItem = menu.addItemResource(chat_enc_timer, R.layout.chat_header_enc_layout);
            }

            ActionBarMenuItem item = menu.addItem(chat_menu_attach, R.drawable.ic_ab_attach);
            item.addSubItem(attach_photo, LocaleController.getString("ChatTakePhoto", R.string.ChatTakePhoto), R.drawable.ic_attach_photo);
            item.addSubItem(attach_gallery, LocaleController.getString("ChatGallery", R.string.ChatGallery), R.drawable.ic_attach_gallery);
            item.addSubItem(attach_video, LocaleController.getString("ChatVideo", R.string.ChatVideo), R.drawable.ic_attach_video);
            item.addSubItem(attach_document, LocaleController.getString("ChatDocument", R.string.ChatDocument), R.drawable.ic_ab_doc);
            item.addSubItem(attach_location, LocaleController.getString("ChatLocation", R.string.ChatLocation), R.drawable.ic_attach_location);
            menuItem = item;

            actionModeViews.clear();

            ActionBarMenu actionMode = actionBarLayer.createActionMode();
            actionModeViews.add(actionMode.addItem(-2, R.drawable.ic_ab_done_gray, R.drawable.bar_selector_mode));

            FrameLayout layout = new FrameLayout(actionMode.getContext());
            layout.setBackgroundColor(0xffe5e5e5);
            actionMode.addView(layout);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)layout.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(1);
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.topMargin = AndroidUtilities.dp(12);
            layoutParams.bottomMargin = AndroidUtilities.dp(12);
            layoutParams.gravity = Gravity.CENTER_VERTICAL;
            layout.setLayoutParams(layoutParams);
            actionModeViews.add(layout);

            selectedMessagesCountTextView = new TextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTextColor(0xff000000);
            selectedMessagesCountTextView.setSingleLine(true);
            selectedMessagesCountTextView.setLines(1);
            selectedMessagesCountTextView.setEllipsize(TextUtils.TruncateAt.END);
            selectedMessagesCountTextView.setPadding(AndroidUtilities.dp(11), 0, 0, 0);
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
                actionModeViews.add(actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy, R.drawable.bar_selector_mode));
                actionModeViews.add(actionMode.addItem(forward, R.drawable.ic_ab_fwd_forward, R.drawable.bar_selector_mode));
                actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, R.drawable.bar_selector_mode));
            } else {
                actionModeViews.add(actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy, R.drawable.bar_selector_mode));
                actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, R.drawable.bar_selector_mode));
            }
            actionMode.getItem(copy).setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);

            View avatarLayout = menu.addItemResource(chat_menu_avatar, R.layout.chat_header_layout);
            avatarImageView = (BackupImageView)avatarLayout.findViewById(R.id.chat_avatar_image);
            avatarImageView.processDetach = false;
            checkActionBarMenu();

            fragmentView = inflater.inflate(R.layout.chat_layout, container, false);

            View contentView = fragmentView.findViewById(R.id.chat_layout);
            TextView emptyView = (TextView) fragmentView.findViewById(R.id.searchEmptyView);
            emptyViewContainer = fragmentView.findViewById(R.id.empty_view);
            emptyViewContainer.setVisibility(View.GONE);
            emptyViewContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            emptyView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
            chatListView = (LayoutListView)fragmentView.findViewById(R.id.chat_list_view);
            chatListView.setAdapter(chatAdapter = new ChatAdapter(getParentActivity()));
            topPanel = fragmentView.findViewById(R.id.top_panel);
            topPlaneClose = (ImageView)fragmentView.findViewById(R.id.top_plane_close);
            topPanelText = (TextView)fragmentView.findViewById(R.id.top_panel_text);
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
                    if (selectedBackground == 1000001) {
                        ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(R.drawable.background_hd);
                    } else {
                        File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                        if (toFile.exists()) {
                            if (ApplicationLoader.cachedWallpaper != null) {
                                ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(ApplicationLoader.cachedWallpaper);
                            } else {
                                Drawable drawable = Drawable.createFromPath(toFile.getAbsolutePath());
                                if (drawable != null) {
                                    ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(drawable);
                                    ApplicationLoader.cachedWallpaper = drawable;
                                } else {
                                    contentView.setBackgroundColor(-2693905);
                                    chatListView.setCacheColorHint(-2693905);
                                }
                            }
                            isCustomTheme = true;
                        } else {
                            ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(R.drawable.background_hd);
                        }
                    }
                } catch (Exception e) {
                    contentView.setBackgroundColor(-2693905);
                    chatListView.setCacheColorHint(-2693905);
                    FileLog.e("tmessages", e);
                }
            }

            if (currentEncryptedChat != null) {
                emptyView.setVisibility(View.GONE);
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

            chatListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long id) {
                    if (!actionBarLayer.isActionModeShowed()) {
                        createMenu(view, false);
                    }
                    return true;
                }
            });

            final Rect scrollRect = new Rect();

            chatListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {

                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (visibleItemCount > 0) {
                        if (firstVisibleItem <= 4) {
                            if (!endReached && !loading) {
                                if (messagesByDays.size() != 0) {
                                    MessagesController.getInstance().loadMessages(dialog_id, 20, maxMessageId, !cacheEndReaced, minDate, classGuid, false, false);
                                } else {
                                    MessagesController.getInstance().loadMessages(dialog_id, 20, 0, !cacheEndReaced, minDate, classGuid, false, false);
                                }
                                loading = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount >= totalItemCount - 6) {
                            if (!unread_end_reached && !loadingForward) {
                                MessagesController.getInstance().loadMessages(dialog_id, 20, minMessageId, true, maxDate, classGuid, false, true);
                                loadingForward = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount == totalItemCount && unread_end_reached) {
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
                progressView.setVisibility(View.GONE);
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

            chatListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (actionBarLayer.isActionModeShowed()) {
                        processRowSelect(view);
                        return;
                    }
                    createMenu(view, true);
                }
            });

            updateBottomOverlay();

            chatActivityEnterView.setContainerView(getParentActivity(), fragmentView);
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void scrollToLastMessage() {
        if (unread_end_reached || first_unread_id == 0) {
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
            unread_end_reached = true;
            loading = true;
            chatAdapter.notifyDataSetChanged();
            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, true, 0, classGuid, true, false);
        }
    }

    private void showPagedownButton(boolean show, boolean animated) {
        if (pagedownButton == null) {
            return;
        }
        if (show) {
            if (pagedownButton.getVisibility() == View.GONE) {
                if (Build.VERSION.SDK_INT > 13 && animated) {
                    pagedownButton.setVisibility(View.VISIBLE);
                    pagedownButton.setAlpha(0);
                    pagedownButton.animate().alpha(1).setDuration(200).setListener(null).start();
                } else {
                    pagedownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (pagedownButton.getVisibility() == View.VISIBLE) {
                if (Build.VERSION.SDK_INT > 13 && animated) {
                    pagedownButton.animate().alpha(0).setDuration(200).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            pagedownButton.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

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
        } else {
            if (menuItem != null) {
                menuItem.setVisibility(View.VISIBLE);
            }

            if (timeItem != null) {
                timeItem.setVisibility(View.VISIBLE);
            }
        }

        if (timeItem != null) {
            timerButton = (TimerButton)timeItem.findViewById(R.id.chat_timer);
            timerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
                    builder.setItems(new CharSequence[]{
                            LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever),
                            LocaleController.getString("ShortMessageLifetime2s", R.string.ShortMessageLifetime2s),
                            LocaleController.getString("ShortMessageLifetime5s", R.string.ShortMessageLifetime5s),
                            LocaleController.getString("ShortMessageLifetime1m", R.string.ShortMessageLifetime1m),
                            LocaleController.getString("ShortMessageLifetime1h", R.string.ShortMessageLifetime1h),
                            LocaleController.getString("ShortMessageLifetime1d", R.string.ShortMessageLifetime1d),
                            LocaleController.getString("ShortMessageLifetime1w", R.string.ShortMessageLifetime1w)

                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int oldValue = currentEncryptedChat.ttl;
                            if (which == 0) {
                                currentEncryptedChat.ttl = 0;
                            } else if (which == 1) {
                                currentEncryptedChat.ttl = 2;
                            } else if (which == 2) {
                                currentEncryptedChat.ttl = 5;
                            } else if (which == 3) {
                                currentEncryptedChat.ttl = 60;
                            } else if (which == 4) {
                                currentEncryptedChat.ttl = 60 * 60;
                            } else if (which == 5) {
                                currentEncryptedChat.ttl = 60 * 60 * 24;
                            } else if (which == 6) {
                                currentEncryptedChat.ttl = 60 * 60 * 24 * 7;
                            }
                            if (oldValue != currentEncryptedChat.ttl) {
                                SendMessagesHelper.getInstance().sendTTLMessage(currentEncryptedChat);
                                MessagesStorage.getInstance().updateEncryptedChat(currentEncryptedChat);
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }
            });
            timerButton.setTime(currentEncryptedChat.ttl);
        }

        if (avatarImageView != null) {
            TLRPC.FileLocation photo = null;
            int placeHolderId = 0;
            if (currentUser != null) {
                if (currentUser.photo != null) {
                    photo = currentUser.photo.photo_small;
                }
                placeHolderId = AndroidUtilities.getUserAvatarForId(currentUser.id);
            } else if (currentChat != null) {
                if (currentChat.photo != null) {
                    photo = currentChat.photo.photo_small;
                }
                if (isBraodcast) {
                    placeHolderId = AndroidUtilities.getBroadcastAvatarForId(currentChat.id);
                } else {
                    placeHolderId = AndroidUtilities.getGroupAvatarForId(currentChat.id);
                }
            }
            avatarImageView.setImage(photo, "50_50", placeHolderId);
        }
    }

    private void updateOnlineCount() {
        if (info == null) {
            return;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId()) && user.status.expires > 10000) {
                onlineCount++;
            }
        }

        updateSubtitle();
    }

    private int getMessageType(MessageObject messageObject) {
        if (currentEncryptedChat == null) {
            boolean isBroadcastError = isBraodcast && messageObject.messageOwner.id <= 0 && messageObject.isSendError();
            if (!isBraodcast && messageObject.messageOwner.id <= 0 && messageObject.isOut() || isBroadcastError) {
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
            } else if (messageObject.type == 10 || messageObject.type == 11 || messageObject.isSending()) {
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
                                if (mime != null && mime.endsWith("text/xml")) {
                                    return 5;
                                }
                            }
                            //return 4;
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
        if (actionBarLayer.isActionModeShowed()) {
            if (selectedMessagesIds.isEmpty()) {
                actionBarLayer.hideActionMode();
            }
            actionBarLayer.createActionMode().getItem(copy).setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void processRowSelect(View view) {
        View parentView = getRowParentView(view);
        if (parentView == null) {
            return;
        }
        MessageObject message = null;
        if (view instanceof ChatBaseCell) {
            message = ((ChatBaseCell)view).getMessageObject();
        } else {
            ChatListRowHolderEx holder = (ChatListRowHolderEx)parentView.getTag();
            message = holder.message;
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
        if (!actionBarLayer.isActionModeShowed()) {
            return;
        }
        if (!selectedMessagesIds.isEmpty()) {
            selectedMessagesCountTextView.setText(LocaleController.formatString("Selected", R.string.Selected, selectedMessagesIds.size()));
        }
    }

    private void updateSubtitle() {
        if (currentChat != null) {
            actionBarLayer.setTitle(currentChat.title);
        } else if (currentUser != null) {
            if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    actionBarLayer.setTitle(PhoneFormat.getInstance().format("+" + currentUser.phone));
                } else {
                    actionBarLayer.setTitle(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
                }
            } else {
                actionBarLayer.setTitle(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            }
        }

        CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
        if (printString != null) {
            printString = TextUtils.replace(printString, new String[]{"..."}, new String[]{""});
        }
        if (printString == null || printString.length() == 0) {
            lastPrintString = null;
            setTypingAnimation(false);
            if (currentChat != null) {
                if (currentChat instanceof TLRPC.TL_chatForbidden) {
                    actionBarLayer.setSubtitle(LocaleController.getString("YouWereKicked", R.string.YouWereKicked));
                } else if (currentChat.left) {
                    actionBarLayer.setSubtitle(LocaleController.getString("YouLeft", R.string.YouLeft));
                } else {
                    int count = currentChat.participants_count;
                    if (info != null) {
                        count = info.participants.size();
                    }
                    if (onlineCount > 1 && count != 0) {
                        actionBarLayer.setSubtitle(String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("Online", onlineCount)));
                    } else {
                        actionBarLayer.setSubtitle(LocaleController.formatPluralString("Members", count));
                    }
                }
            } else if (currentUser != null) {
                TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
                if (user != null) {
                    currentUser = user;
                }
                actionBarLayer.setSubtitle(LocaleController.formatUserStatus(currentUser));
            }
        } else {
            lastPrintString = printString;
            actionBarLayer.setSubtitle(printString);
            setTypingAnimation(true);
        }
    }

    private void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        int placeHolderId = 0;
        if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            }
            placeHolderId = AndroidUtilities.getUserAvatarForId(currentUser.id);
        } else if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
            if (isBraodcast) {
                placeHolderId = AndroidUtilities.getBroadcastAvatarForId(currentChat.id);
            } else {
                placeHolderId = AndroidUtilities.getGroupAvatarForId(currentChat.id);
            }
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", placeHolderId);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {
                Utilities.addMediaToGallery(currentPicturePath);
                processSendingPhoto(currentPicturePath, null);
                currentPicturePath = null;
            } else if (requestCode == 1) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                processSendingPhoto(null, data.getData());
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
                        Bundle args = new Bundle();
                        args.putString("videoPath", videoPath);
                        VideoEditorActivity fragment = new VideoEditorActivity(args);
                        fragment.setDelegate(this);
                        if (!presentFragment(fragment, false, true)) {
                            processSendingVideo(videoPath, 0, 0, 0, 0, null);
                        }
                    }
                } else {
                    processSendingVideo(videoPath, 0, 0, 0, 0, null);
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
                processSendingDocument(tempPath, originalPath);
            }
        }
    }

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
        processSendingVideo(videoPath, estimatedSize, estimatedDuration, resultWidth, resultHeight, videoEditedInfo);
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
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

    public boolean processSendingText(String text) {
        return chatActivityEnterView.processSendingText(text);
    }

    public void processSendingPhoto(String imageFilePath, Uri imageUri) {
        ArrayList<String> paths = null;
        ArrayList<Uri> uris = null;
        if (imageFilePath != null && imageFilePath.length() != 0) {
            paths = new ArrayList<String>();
            paths.add(imageFilePath);
        }
        if (imageUri != null) {
            uris = new ArrayList<Uri>();
            uris.add(imageUri);
        }
        processSendingPhotos(paths, uris);
    }

    public void processSendingPhotos(ArrayList<String> paths, ArrayList<Uri> uris) {
        if (paths == null && uris == null || paths != null && paths.isEmpty() || uris != null && uris.isEmpty()) {
            return;
        }
        final ArrayList<String> pathsCopy = new ArrayList<String>();
        final ArrayList<Uri> urisCopy = new ArrayList<Uri>();
        if (paths != null) {
            pathsCopy.addAll(paths);
        }
        if (uris != null) {
            urisCopy.addAll(uris);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> sendAsDocuments = null;
                ArrayList<String> sendAsDocumentsOriginal = null;
                int count = !pathsCopy.isEmpty() ? pathsCopy.size() : urisCopy.size();
                String path = null;
                Uri uri = null;
                for (int a = 0; a < count; a++) {
                    if (!pathsCopy.isEmpty()) {
                        path = pathsCopy.get(a);
                    } else if (!urisCopy.isEmpty()) {
                        uri = urisCopy.get(a);
                    }

                    String originalPath = path;
                    String tempPath = path;
                    if (tempPath == null && uri != null) {
                        tempPath = Utilities.getPath(uri);
                        originalPath = uri.toString();
                    }

                    boolean isGif = false;
                    if (tempPath != null && tempPath.endsWith(".gif")) {
                        isGif = true;
                    } else if (tempPath == null && uri != null) {
                        isGif = MediaController.isGif(uri);
                        if (isGif) {
                            originalPath = uri.toString();
                            tempPath = MediaController.copyDocumentToCache(uri, "gif");
                        }
                    }

                    if (isGif) {
                        if (sendAsDocuments == null) {
                            sendAsDocuments = new ArrayList<String>();
                            sendAsDocumentsOriginal = new ArrayList<String>();
                        }
                        sendAsDocuments.add(tempPath);
                        sendAsDocumentsOriginal.add(originalPath);
                    } else {
                        if (tempPath != null) {
                            File temp = new File(tempPath);
                            originalPath += temp.length() + "_" + temp.lastModified();
                        } else {
                            originalPath = null;
                        }
                        TLRPC.TL_photo photo = (TLRPC.TL_photo)MessagesStorage.getInstance().getSentFile(originalPath, currentEncryptedChat == null ? 0 : 3);
                        if (photo == null && uri != null) {
                            photo = (TLRPC.TL_photo)MessagesStorage.getInstance().getSentFile(Utilities.getPath(uri), currentEncryptedChat == null ? 0 : 3);
                        }
                        if (photo == null) {
                            photo = SendMessagesHelper.getInstance().generatePhotoSizes(path, uri);
                        }
                        if (photo != null) {
                            final String originalPathFinal = originalPath;
                            final TLRPC.TL_photo photoFinal = photo;
                            AndroidUtilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    SendMessagesHelper.getInstance().sendMessage(photoFinal, originalPathFinal, dialog_id);
                                    if (chatListView != null) {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                    }
                                    if (paused) {
                                        scrollToTopOnResume = true;
                                    }
                                }
                            });
                        }
                    }
                }
                if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                    for (int a = 0; a < sendAsDocuments.size(); a++) {
                        processSendingDocumentInternal(sendAsDocuments.get(a), sendAsDocumentsOriginal.get(a));
                    }
                }
            }
        }).start();
    }

    private void processSendingDocumentInternal(final String path, String originalPath) {
        if (path == null || path.length() == 0) {
            return;
        }
        final File f = new File(path);
        if (!f.exists() || f.length() == 0) {
            return;
        }

        String name = f.getName();
        if (name == null) {
            name = "noname";
        }
        String ext = "";
        int idx = path.lastIndexOf(".");
        if (idx != -1) {
            ext = path.substring(idx + 1);
        }
        if (originalPath != null) {
            originalPath += "" + f.length();
        }

        TLRPC.TL_document document = (TLRPC.TL_document)MessagesStorage.getInstance().getSentFile(originalPath, currentEncryptedChat == null ? 1 : 4);
        if (document == null && !path.equals(originalPath)) {
            document = (TLRPC.TL_document)MessagesStorage.getInstance().getSentFile(path + f.length(), currentEncryptedChat == null ? 1 : 4);
        }
        if (document == null) {
            document = new TLRPC.TL_document();
            document.id = 0;
            document.user_id = UserConfig.getClientUserId();
            document.date = ConnectionsManager.getInstance().getCurrentTime();
            document.file_name = name;
            document.size = (int)f.length();
            document.dc_id = 0;
            if (ext.length() != 0) {
                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                String mimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                if (mimeType != null) {
                    document.mime_type = mimeType;
                } else {
                    document.mime_type = "application/octet-stream";
                }
            } else {
                document.mime_type = "application/octet-stream";
            }
            if (document.mime_type.equals("image/gif")) {
                try {
                    Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90);
                    if (bitmap != null) {
                        document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, currentEncryptedChat != null);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            if (document.thumb == null) {
                document.thumb = new TLRPC.TL_photoSizeEmpty();
                document.thumb.type = "s";
            }
        }

        final TLRPC.TL_document documentFinal = document;
        final String originalPathFinal = originalPath;
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                SendMessagesHelper.getInstance().sendMessage(documentFinal, originalPathFinal, path, dialog_id);
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                }
                if (paused) {
                    scrollToTopOnResume = true;
                }
            }
        });
    }

    public void processSendingDocument(String path, String originalPath) {
        if (path == null || originalPath == null) {
            return;
        }
        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<String> originalPaths = new ArrayList<String>();
        paths.add(path);
        originalPaths.add(originalPath);
        processSendingDocuments(paths, originalPaths);
    }

    public void processSendingDocuments(final ArrayList<String> paths, final ArrayList<String> originalPaths) {
        if (paths == null && originalPaths == null || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int a = 0; a < paths.size(); a++) {
                    processSendingDocumentInternal(paths.get(a), originalPaths.get(a));
                }
            }
        }).start();
    }

    public void processSendingVideo(final String videoPath, final long estimatedSize, final long duration, final int width, final int height, final TLRPC.VideoEditedInfo videoEditedInfo) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String path = videoPath;
                String originalPath = videoPath;
                File temp = new File(originalPath);
                originalPath += temp.length() + "_" + temp.lastModified();
                if (videoEditedInfo != null) {
                    originalPath += duration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime;
                }
                TLRPC.TL_video video = (TLRPC.TL_video)MessagesStorage.getInstance().getSentFile(originalPath, currentEncryptedChat == null ? 2 : 5);
                if (video == null) {
                    Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(thumb, 90, 90, 55, currentEncryptedChat != null);
                    video = new TLRPC.TL_video();
                    video.thumb = size;
                    if (video.thumb == null) {
                        video.thumb = new TLRPC.TL_photoSizeEmpty();
                        video.thumb.type = "s";
                    } else {
                        video.thumb.type = "s";
                    }
                    video.caption = "";
                    video.mime_type = "video/mp4";
                    video.id = 0;
                    UserConfig.saveConfig(false);

                    if (videoEditedInfo != null) {
                        video.duration = (int)(duration / 1000);
                        if (videoEditedInfo.rotationValue == 90 || videoEditedInfo.rotationValue == 270) {
                            video.w = height;
                            video.h = width;
                        } else {
                            video.w = width;
                            video.h = height;
                        }
                        video.size = (int)estimatedSize;
                        video.videoEditedInfo = videoEditedInfo;
                        String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
                        UserConfig.lastLocalId--;
                        File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                        UserConfig.saveConfig(false);
                        path = cacheFile.getAbsolutePath();
                    } else {
                        if (temp != null && temp.exists()) {
                            video.size = (int) temp.length();
                        }
                        boolean infoObtained = false;
                        if (Build.VERSION.SDK_INT >= 14) {
                            MediaMetadataRetriever mediaMetadataRetriever = null;
                            try {
                                mediaMetadataRetriever = new MediaMetadataRetriever();
                                mediaMetadataRetriever.setDataSource(videoPath);
                                String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                                if (width != null) {
                                    video.w = Integer.parseInt(width);
                                }
                                String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                                if (height != null) {
                                    video.h = Integer.parseInt(height);
                                }
                                String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                if (duration != null) {
                                    video.duration = (int) Math.ceil(Long.parseLong(duration) / 1000.0f);
                                }
                                infoObtained = true;
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            } finally {
                                try {
                                    if (mediaMetadataRetriever != null) {
                                        mediaMetadataRetriever.release();
                                        mediaMetadataRetriever = null;
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                        if (!infoObtained) {
                            try {
                                MediaPlayer mp = MediaPlayer.create(ApplicationLoader.applicationContext, Uri.fromFile(new File(videoPath)));
                                if (mp != null) {
                                    video.duration = (int) Math.ceil(mp.getDuration() / 1000.0f);
                                    video.w = mp.getVideoWidth();
                                    video.h = mp.getVideoHeight();
                                    mp.release();
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }

                final TLRPC.TL_video videoFinal = video;
                final String originalPathFinal = originalPath;
                final String finalPath = path;
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        SendMessagesHelper.getInstance().sendMessage(videoFinal, originalPathFinal, finalPath, dialog_id);
                        if (chatListView != null) {
                            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                        }
                        if (paused) {
                            scrollToTopOnResume = true;
                        }
                    }
                });
            }
        }).start();
    }

    private void removeUnreadPlane(boolean reload) {
        if (unreadMessageObject != null) {
            messages.remove(unreadMessageObject);
            unread_end_reached = true;
            first_unread_id = 0;
            last_unread_id = 0;
            unread_to_load = 0;
            unreadMessageObject = null;
            if (reload) {
                chatAdapter.notifyDataSetChanged();
            }
        }
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
                int last_unread_date = (Integer)args[7];
                boolean forwardLoad = (Boolean)args[8];
                boolean wasUnread = false;
                boolean positionToUnread = false;
                if (fnid != 0) {
                    first_unread_id = fnid;
                    last_unread_id = (Integer)args[5];
                    unread_to_load = (Integer)args[6];
                    positionToUnread = true;
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>)args[2];

                int newRowsCount = 0;
                unread_end_reached = last_unread_id == 0;

                if (loadsCount == 1 && messArr.size() > 20) {
                    loadsCount++;
                }

                if (firstLoading) {
                    if (!unread_end_reached) {
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
                    maxDate = Math.max(maxDate, obj.messageOwner.date);
                    if (minDate == 0 || obj.messageOwner.date < minDate) {
                        minDate = obj.messageOwner.date;
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
                        dateObj.contentType = 7;
                        if (forwardLoad) {
                            messages.add(0, dateObj);
                        } else {
                            messages.add(dateObj);
                        }
                        newRowsCount++;
                    }

                    newRowsCount++;
                    dayArray.add(obj);
                    if (forwardLoad) {
                        messages.add(0, obj);
                    } else {
                        messages.add(messages.size() - 1, obj);
                    }

                    if (!forwardLoad) {
                        if (obj.messageOwner.id == first_unread_id) {
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
                            newRowsCount++;
                        }
                        if (obj.messageOwner.id == last_unread_id) {
                            unread_end_reached = true;
                        }
                    }

                }

                if (unread_end_reached) {
                    first_unread_id = 0;
                    last_unread_id = 0;
                }

                if (forwardLoad) {
                    if (messArr.size() != count) {
                        unread_end_reached = true;
                        first_unread_id = 0;
                        last_unread_id = 0;
                    }

                    chatAdapter.notifyDataSetChanged();
                    loadingForward = false;
                } else {
                    if (messArr.size() != count) {
                        if (isCache) {
                            cacheEndReaced = true;
                            if (currentEncryptedChat != null || isBraodcast) {
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
                            if (positionToUnread && unreadMessageObject != null) {
                                if (messages.get(messages.size() - 1) == unreadMessageObject) {
                                    chatListView.setSelectionFromTop(0, AndroidUtilities.dp(-11));
                                } else {
                                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), AndroidUtilities.dp(-11));
                                }
                                ViewTreeObserver obs = chatListView.getViewTreeObserver();
                                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (!messages.isEmpty()) {
                                            if (messages.get(messages.size() - 1) == unreadMessageObject) {
                                                chatListView.setSelectionFromTop(0, AndroidUtilities.dp(-11));
                                            } else {
                                                chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), AndroidUtilities.dp(-11));
                                            }
                                        }
                                        chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                                        return false;
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
                            if (positionToUnread && unreadMessageObject != null) {
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
                        if (positionToUnread && unreadMessageObject != null) {
                            scrollToTopUnReadOnResume = true;
                        }
                    }
                }

                if (first && messages.size() > 0) {
                    if (last_unread_id != 0) {
                        MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, last_unread_id, 0, last_unread_date, wasUnread, false);
                    } else {
                        MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, wasUnread, false);
                    }
                    first = false;
                }

                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int updateMask = (Integer)args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0) {
                updateSubtitle();
                updateOnlineCount();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_NAME) != 0) {
                checkAndUpdateAvatar();
                updateVisibleRows();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle();
                }
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PHONE) != 0) {
                updateContactStatus();
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long did = (Long)args[0];
            if (did == dialog_id) {

                boolean updateChat = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[1];

                if (!unread_end_reached) {
                    int currentMaxDate = Integer.MIN_VALUE;
                    int currentMinMsgId = Integer.MIN_VALUE;
                    if (currentEncryptedChat != null) {
                        currentMinMsgId = Integer.MAX_VALUE;
                    }
                    boolean currentMarkAsRead = false;

                    for (MessageObject obj : arr) {
                        if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageActionTTLChange && timerButton != null) {
                            timerButton.setTime(obj.messageOwner.action.ttl);
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
                        } else if (currentEncryptedChat != null) {
                            currentMinMsgId = Math.min(obj.messageOwner.id, currentMinMsgId);
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
                        if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageActionTTLChange && timerButton != null) {
                            timerButton.setTime(obj.messageOwner.action.ttl);
                        }
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        if (minDate == 0 || obj.messageOwner.date < minDate) {
                            minDate = obj.messageOwner.date;
                        }

                        if (obj.isOut()) {
                            removeUnreadPlane(false);
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
                            dateObj.contentType = 7;
                            messages.add(0, dateObj);
                        }
                        if (!obj.isOut() && obj.isUnread()) {
                            if (!paused) {
                                obj.messageOwner.unread = false;
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
                        progressView.setVisibility(View.GONE);
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
                        if (lastVisible == oldCount) {
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
                    updateSubtitle();
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
                    obj.messageOwner.unread = false;
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
                    progressView.setVisibility(View.GONE);
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
                    MessagesController.getInstance().loadMessages(dialog_id, 30, 0, !cacheEndReaced, minDate, classGuid, false, false);
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
                MessageObject newMsgObj = (MessageObject)args[2];
                if (newMsgObj != null) {
                    obj.messageOwner.media = newMsgObj.messageOwner.media;
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
                if (isBraodcast) {
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
                    } else if (obj.isOut() && !obj.messageOwner.unread) {
                        break;
                    }
                    if (obj.messageOwner.date <= date) {
                        obj.messageOwner.unread = false;
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
            messages.clear();
            messagesByDays.clear();
            messagesDict.clear();
            progressView.setVisibility(View.GONE);
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
            actionBarLayer.hideActionMode();
            chatAdapter.notifyDataSetChanged();
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
        if (topPanel == null) {
            return;
        }
        if (currentUser == null) {
            topPanel.setVisibility(View.GONE);
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
                topPanel.setVisibility(View.GONE);
            } else {
                topPanel.setVisibility(View.VISIBLE);
                topPanelText.setShadowLayer(1, 0, AndroidUtilities.dp(1), 0xff8797a3);
                if (isCustomTheme) {
                    topPlaneClose.setImageResource(R.drawable.ic_msg_btn_cross_custom);
                    topPanel.setBackgroundResource(R.drawable.top_pane_custom);
                } else {
                    topPlaneClose.setImageResource(R.drawable.ic_msg_btn_cross_custom);
                    topPanel.setBackgroundResource(R.drawable.top_pane);
                }
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    if (MessagesController.getInstance().hidenAddToContacts.get(currentUser.id) != null) {
                        topPanel.setVisibility(View.INVISIBLE);
                    } else {
                        topPanelText.setText(LocaleController.getString("AddToContacts", R.string.AddToContacts));
                        topPlaneClose.setVisibility(View.VISIBLE);
                        topPlaneClose.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MessagesController.getInstance().hidenAddToContacts.put(currentUser.id, currentUser);
                                topPanel.setVisibility(View.GONE);
                            }
                        });
                        topPanel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Bundle args = new Bundle();
                                args.putInt("user_id", currentUser.id);
                                presentFragment(new ContactAddActivity(args));
                            }
                        });
                    }
                } else {
                    if (MessagesController.getInstance().hidenAddToContacts.get(currentUser.id) != null) {
                        topPanel.setVisibility(View.INVISIBLE);
                    } else {
                        topPanelText.setText(LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
                        topPlaneClose.setVisibility(View.GONE);
                        topPanel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfo", R.string.AreYouSureShareMyContactInfo));
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        MessagesController.getInstance().hidenAddToContacts.put(currentUser.id, currentUser);
                                        topPanel.setVisibility(View.GONE);
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
                        });
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        checkActionBarMenu();
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        NotificationsController.getInstance().setOpennedDialogId(dialog_id);
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && unreadMessageObject != null) {
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), -chatListView.getPaddingTop() - AndroidUtilities.dp(7));
                }
            } else {
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                }
            }
            scrollToTopUnReadOnResume = false;
            scrollToTopOnResume = false;
        }
        paused = false;
        if (readWhenResume && !messages.isEmpty()) {
            for (MessageObject messageObject : messages) {
                if (!messageObject.isUnread() && !messageObject.isFromMe()) {
                    break;
                }
                if (!messageObject.isOut()) {
                    messageObject.messageOwner.unread = false;
                }
            }
            readWhenResume = false;
            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, readWithMid, 0, readWithDate, true, false);
        }

        fixLayout(true);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String lastMessageText = preferences.getString("dialog_" + dialog_id, null);
        if (lastMessageText != null) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove("dialog_" + dialog_id);
            editor.commit();
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
            AndroidUtilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    Bundle args = new Bundle();
                    args.putString("videoPath", startVideoEdit);
                    VideoEditorActivity fragment = new VideoEditorActivity(args);
                    fragment.setDelegate(ChatActivity.this);
                    if (!presentFragment(fragment, false, true)) {
                        processSendingVideo(startVideoEdit, 0, 0, 0, 0, null);
                    }
                    startVideoEdit = null;
                }
            });
        }
    }

    @Override
    public void didSelectPhotos(ArrayList<String> photos) {
        processSendingPhotos(photos, null);
    }

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

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        chatActivityEnterView.hideEmojiPopup();
    }

    private void setTypingAnimation(boolean start) {
        if (actionBarLayer == null) {
            return;
        }
        if (start) {
            try {
                actionBarLayer.setSubTitleIcon(0, typingDotsDrawable, AndroidUtilities.dp(4));
                typingDotsDrawable.start();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            actionBarLayer.setSubTitleIcon(0, null, 0);
            if (typingDotsDrawable != null) {
                typingDotsDrawable.stop();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        actionBarLayer.hideActionMode();
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

        /*if (currentEncryptedChat != null) { disabled
            chatLeaveTime = System.currentTimeMillis();
            updateInformationForScreenshotDetector();
        }*/
    }

    private void updateInformationForScreenshotDetector() {
        ArrayList<Long> visibleMessages = new ArrayList<Long>();
        if (chatListView != null) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                MessageObject object = null;
                if (view instanceof ChatBaseCell) {
                    ChatBaseCell cell = (ChatBaseCell) view;
                    object = cell.getMessageObject();
                } else {
                    Object tag = view.getTag();
                    if (tag instanceof ChatListRowHolderEx) {
                        ChatListRowHolderEx holder = (ChatListRowHolderEx) tag;
                        object = holder.message;
                    }
                }
                if (object != null && object.messageOwner.id < 0 && object.messageOwner.random_id != 0) {
                    visibleMessages.add(object.messageOwner.random_id);
                }
            }
        }
        MediaController.getInstance().setLastEncryptedChatParams(chatEnterTime, chatLeaveTime, currentEncryptedChat, visibleMessages);
    }

    private void fixLayout(final boolean resume) {
        final int lastPos = chatListView.getLastVisiblePosition();
        ViewTreeObserver obs = chatListView.getViewTreeObserver();
        obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (chatListView != null) {
                    chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                if (getParentActivity() == null) {
                    return true;
                }
                int height = AndroidUtilities.dp(48);
                if (!AndroidUtilities.isTablet() && getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    height = AndroidUtilities.dp(40);
                    selectedMessagesCountTextView.setTextSize(16);
                } else {
                    selectedMessagesCountTextView.setTextSize(18);
                }
                if (avatarImageView != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) avatarImageView.getLayoutParams();
                    params.width = height;
                    params.height = height;
                    avatarImageView.setLayoutParams(params);
                }
                if (!resume && lastPos >= messages.size() - 1) {
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

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        fixLayout(false);
    }

    private View getRowParentView(View v) {
        if (v instanceof ChatBaseCell) {
            return v;
        } else {
            while (!(v.getTag() instanceof ChatListRowHolderEx)) {
                ViewParent parent = v.getParent();
                if (!(parent instanceof View)) {
                    return null;
                }
                v = (View)v.getParent();
                if (v == null) {
                    return null;
                }
            }
            return v;
        }
    }

    public void createMenu(View v, boolean single) {
        if (actionBarLayer.isActionModeShowed()) {
            return;
        }

        View parentView = getRowParentView(v);
        if (parentView == null) {
            return;
        }
        MessageObject message = null;
        if (v instanceof ChatBaseCell) {
            message = ((ChatBaseCell)v).getMessageObject();
        } else {
            ChatListRowHolderEx holder = (ChatListRowHolderEx)parentView.getTag();
            message = holder.message;
        }
        final int type = getMessageType(message);

        selectedObject = null;
        forwaringMessage = null;
        selectedMessagesCanCopyIds.clear();
        selectedMessagesIds.clear();

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
        actionBarLayer.showActionMode();
        if (Build.VERSION.SDK_INT >= 11) {
            AnimatorSet animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<Animator>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View view = actionModeViews.get(a);
                if (a < 2) {
                    animators.add(ObjectAnimator.ofFloat(view, "translationX", -AndroidUtilities.dp(56), 0));
                } else {
                    animators.add(ObjectAnimator.ofFloat(view, "scaleY", 0.1f, 1.0f));
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

    @Override
    public void didSelectFile(DocumentSelectActivity activity, String path) {
        activity.finishFragment();
        processSendingDocument(path, path);
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
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity activity, long did, boolean param) {
        if (dialog_id != 0 && (forwaringMessage != null || !selectedMessagesIds.isEmpty())) {
            if (isBraodcast) {
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
                    presentFragment(new ChatActivity(args), true);
                    forwardSelectedMessages(did, param);
                    if (!AndroidUtilities.isTablet()) {
                        removeSelfFromStack();
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
                    actionBarLayer.hideActionMode();
                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (actionBarLayer.isActionModeShowed()) {
            selectedMessagesIds.clear();
            selectedMessagesCanCopyIds.clear();
            actionBarLayer.hideActionMode();
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
            Object tag = view.getTag();
            if (tag instanceof ChatListRowHolderEx) {
                ChatListRowHolderEx holder = (ChatListRowHolderEx)tag;
                holder.update();

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBarLayer.isActionModeShowed()) {
                    if (selectedMessagesIds.containsKey(holder.message.messageOwner.id)) {
                        view.setBackgroundColor(0x6633b5e5);
                        selected = true;
                    } else {
                        view.setBackgroundColor(0);
                    }
                    disableSelection = true;
                } else {
                    view.setBackgroundColor(0);
                }
                updateRowBackground(holder, disableSelection, selected);
            } else if (view instanceof ChatBaseCell) {
                ChatBaseCell cell = (ChatBaseCell)view;

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBarLayer.isActionModeShowed()) {
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
            }
        }
    }

    private void updateRowBackground(ChatListRowHolderEx holder, boolean disableSelection, boolean selected) {
        int messageType = holder.message.type;
        if (!disableSelection) {
            if (messageType == 12) {
                holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(18), 0);
            } else if (messageType == 13) {
                holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_text_states);
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(6), AndroidUtilities.dp(9), 0);
            }
        } else {
            if (messageType == 12) {
                if (selected) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_selected);
                } else {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out);
                }
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(18), 0);
            } else if (messageType == 13) {
                if (selected) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_selected);
                } else {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in);
                }
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(6), AndroidUtilities.dp(9), 0);
            }
        }
    }

    private void alertUserOpenError(MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(R.string.OK, null);
        if (message.type == 3) {
            builder.setMessage(R.string.NoPlayerInstalled);
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
            } else if (view.getTag() != null) {
                Object tag = view.getTag();
                if (tag instanceof ChatListRowHolderEx) {
                    ChatListRowHolderEx holder = (ChatListRowHolderEx)tag;
                    if (holder.message != null && holder.message.messageOwner.id == messageObject.messageOwner.id) {
                        messageToOpen = holder.message;
                        imageReceiver = holder.photoImage.imageReceiver;
                        view = holder.photoImage;
                    }
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
                object.thumb = object.imageReceiver.getBitmap();
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
                if (!unread_end_reached) {
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
            if ((!endReached || !unread_end_reached) && messages.size() != 0) {
                if (!endReached) {
                    offset = 0;
                }
                if (i == 0 && !endReached || !unread_end_reached && i == (messages.size() + 1 - offset)) {
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.chat_loading_layout, viewGroup, false);
                        View progressBar = view.findViewById(R.id.progressLayout);
                        if (isCustomTheme) {
                            progressBar.setBackgroundResource(R.drawable.system_loader2);
                        } else {
                            progressBar.setBackgroundResource(R.drawable.system_loader1);
                        }
                        progressBar.setVisibility(loadsCount > 1 ? View.VISIBLE : View.INVISIBLE);
                    }
                    return view;
                }
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            int type = message.contentType;
            if (view == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (type == 0) {
                    view = new ChatMessageCell(mContext);
                } if (type == 1) {
                    view = new ChatMediaCell(mContext);
                } else if (type == 7) {
                    view = li.inflate(R.layout.chat_action_message_layout, viewGroup, false);
                } else if (type == 8) {
                    view = li.inflate(R.layout.chat_action_change_photo_layout, viewGroup, false);
                } else if (type == 3) {
                    view = li.inflate(R.layout.chat_outgoing_contact_layout, viewGroup, false);
                } else if (type == 4) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_contact_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_contact_layout, viewGroup, false);
                    }
                } else if (type == 6) {
                    view = li.inflate(R.layout.chat_unread_layout, viewGroup, false);
                } else if (type == 2) {
                    view = new ChatAudioCell(mContext);
                }
            }

            boolean selected = false;
            boolean disableSelection = false;
            if (actionBarLayer.isActionModeShowed()) {
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
                ((ChatBaseCell)view).delegate = new ChatBaseCell.ChatBaseCellDelegate() {
                    @Override
                    public void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user) {
                        if (user != null && user.id != UserConfig.getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            presentFragment(new UserProfileActivity(args));
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
                        return actionBarLayer != null && !actionBarLayer.isActionModeShowed();
                    }
                };
                if (view instanceof ChatMediaCell) {
                    ((ChatMediaCell)view).mediaDelegate = new ChatMediaCell.ChatMediaCellDelegate() {
                        @Override
                        public void didPressedImage(ChatMediaCell cell) {
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
                    };
                }

                ((ChatBaseCell)view).isChat = currentChat != null;
                ((ChatBaseCell)view).setMessageObject(message);
                ((ChatBaseCell)view).setCheckPressed(!disableSelection, disableSelection && selected);
                if (view instanceof ChatAudioCell && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_AUDIO)) {
                    ((ChatAudioCell)view).downloadAudioIfNeed();
                }
            } else {
                ChatListRowHolderEx holder = (ChatListRowHolderEx)view.getTag();
                if (holder == null) {
                    holder = new ChatListRowHolderEx(view, message.type);
                    view.setTag(holder);
                }
                holder.message = message;
                updateRowBackground(holder, disableSelection, selected);
                holder.update();
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
            if (!unread_end_reached && i == (messages.size() + 1 - offset)) {
                return 5;
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            return message.contentType;
        }

        @Override
        public int getViewTypeCount() {
            return 9;
        }

        @Override
        public boolean isEmpty() {
            int count = messages.size();
            if (count != 0) {
                if (!endReached) {
                    count++;
                }
                if (!unread_end_reached) {
                    count++;
                }
            }
            return count == 0;
        }
    }

    public class ChatListRowHolderEx {
        public BackupImageView avatarImageView;
        public TextView nameTextView;
        public TextView messageTextView;
        public MessageActionLayout messageLayoutAction;
        public TextView timeTextView;
        public BackupImageView photoImage;
        public ImageView halfCheckImage;
        public ImageView checkImage;
        public MessageObject message;
        public TextView phoneTextView;
        public BackupImageView contactAvatar;
        public View contactView;
        public ImageView addContactButton;
        public View addContactView;
        public View chatBubbleView;

        public void update() {
            TLRPC.User fromUser = MessagesController.getInstance().getUser(message.messageOwner.from_id);

            int type = message.type;

            if (timeTextView != null) {
                timeTextView.setText(LocaleController.formatterDay.format((long) (message.messageOwner.date) * 1000));
            }

            if (avatarImageView != null && fromUser != null) {
                TLRPC.FileLocation photo = null;
                if (fromUser.photo != null) {
                    photo = fromUser.photo.photo_small;
                }
                int placeHolderId = AndroidUtilities.getUserAvatarForId(fromUser.id);
                avatarImageView.setImage(photo, "50_50", placeHolderId);
            }

            if (type != 12 && type != 13 && nameTextView != null && fromUser != null && type != 8 && type != 9) {
                nameTextView.setText(ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                nameTextView.setTextColor(AndroidUtilities.getColorForId(message.messageOwner.from_id));
            }

            if (type == 11 || type == 10) {
                int width = 0;
                if (AndroidUtilities.isTablet()) {
                    width = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(30);
                } else {
                    width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(30);
                }
                messageTextView.setText(message.messageText);
                messageTextView.setMaxWidth(width);

                if (type == 11) {
                    if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                        photoImage.setImage(message.messageOwner.action.newUserPhoto.photo_small, "50_50", AndroidUtilities.getUserAvatarForId(currentUser.id));
                    } else {
                        PhotoObject photo = PhotoObject.getClosestImageWithSize(message.photoThumbs, AndroidUtilities.dp(64));
                        if (photo != null) {
                            if (photo.image != null) {
                                photoImage.setImageBitmap(photo.image);
                            } else {
                                photoImage.setImage(photo.photoOwner.location, "50_50", AndroidUtilities.getGroupAvatarForId(currentChat.id));
                            }
                        } else {
                            photoImage.setImageResource(AndroidUtilities.getGroupAvatarForId(currentChat.id));
                        }
                    }
                    photoImage.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(message), false);
                }
            } else if (type == 12 || type == 13) {
                TLRPC.User contactUser = MessagesController.getInstance().getUser(message.messageOwner.media.user_id);
                if (contactUser != null) {
                    nameTextView.setText(ContactsController.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(AndroidUtilities.getColorForId(contactUser.id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (!phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.getInstance().format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    TLRPC.FileLocation photo = null;
                    if (contactUser.photo != null) {
                        photo = contactUser.photo.photo_small;
                    }
                    int placeHolderId = AndroidUtilities.getUserAvatarForId(contactUser.id);
                    contactAvatar.setImage(photo, "50_50", placeHolderId);
                    if (contactUser.id != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(contactUser.id) == null) {
                        addContactView.setVisibility(View.VISIBLE);
                    } else {
                        addContactView.setVisibility(View.GONE);
                    }
                } else {
                    nameTextView.setText(ContactsController.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(AndroidUtilities.getColorForId(message.messageOwner.media.user_id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (message.messageOwner.media.user_id != 0 && !phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.getInstance().format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    contactAvatar.setImageResource(AndroidUtilities.getUserAvatarForId(message.messageOwner.media.user_id));
                    addContactView.setVisibility(View.GONE);
                }
            } else if (type == 6) {
                messageTextView.setTextSize(16);
                messageTextView.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
            }

            if (message.isFromMe()) {
                if (halfCheckImage != null) {
                    if (message.isSending()) {
                        checkImage.setVisibility(View.INVISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_clock);
                        halfCheckImage.setVisibility(View.VISIBLE);
                    } else if (message.isSendError()) {
                        halfCheckImage.setVisibility(View.VISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_warning);
                        if (checkImage != null) {
                            checkImage.setVisibility(View.INVISIBLE);
                        }
                    } else if (message.isSent()) {
                        if (!message.messageOwner.unread) {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.VISIBLE);
                            halfCheckImage.setImageResource(R.drawable.msg_halfcheck);
                        } else {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.INVISIBLE);
                            halfCheckImage.setImageResource(R.drawable.msg_check);
                        }
                    }
                }
            }
        }

        public ChatListRowHolderEx(View view, int type) {
            avatarImageView = (BackupImageView)view.findViewById(R.id.chat_group_avatar_image);
            nameTextView = (TextView)view.findViewById(R.id.chat_user_group_name);
            messageLayoutAction = (MessageActionLayout)view.findViewById(R.id.message_action_layout);
            timeTextView = (TextView)view.findViewById(R.id.chat_time_text);
            photoImage = (BackupImageView)view.findViewById(R.id.chat_photo_image);
            halfCheckImage = (ImageView)view.findViewById(R.id.chat_row_halfcheck);
            checkImage = (ImageView)view.findViewById(R.id.chat_row_check);
            messageTextView = (TextView)view.findViewById(R.id.chat_message_text);
            phoneTextView = (TextView)view.findViewById(R.id.phone_text_view);
            contactAvatar = (BackupImageView)view.findViewById(R.id.contact_avatar);
            contactView = view.findViewById(R.id.shared_layout);
            addContactButton = (ImageView)view.findViewById(R.id.add_contact_button);
            addContactView = view.findViewById(R.id.add_contact_view);
            chatBubbleView = view.findViewById(R.id.chat_bubble_layout);
            if (messageTextView != null) {
                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MessagesController.getInstance().fontSize);
            }

            if (messageLayoutAction != null) {
                if (isCustomTheme) {
                    messageLayoutAction.setBackgroundResource(R.drawable.system_black);
                } else {
                    messageLayoutAction.setBackgroundResource(R.drawable.system_blue);
                }
            }

            if (addContactButton != null) {
                addContactButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (actionBarLayer.isActionModeShowed()) {
                            processRowSelect(view);
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putInt("user_id", message.messageOwner.media.user_id);
                        args.putString("phone", message.messageOwner.media.phone_number);
                        presentFragment(new ContactAddActivity(args));
                    }
                });

                addContactButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }

            if (contactView != null) {
                contactView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (message.type == 12 || message.type == 13) {
                            if (actionBarLayer.isActionModeShowed()) {
                                processRowSelect(view);
                                return;
                            }
                            if (message.messageOwner.media.user_id != UserConfig.getClientUserId()) {
                                TLRPC.User user = null;
                                if (message.messageOwner.media.user_id != 0) {
                                    user = MessagesController.getInstance().getUser(message.messageOwner.media.user_id);
                                }
                                if (user != null) {
                                    Bundle args = new Bundle();
                                    args.putInt("user_id", message.messageOwner.media.user_id);
                                    presentFragment(new UserProfileActivity(args));
                                } else {
                                    if (message.messageOwner.media.phone_number == null || message.messageOwner.media.phone_number.length() == 0) {
                                        return;
                                    }
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setItems(new CharSequence[] {LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Call", R.string.Call)}, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    if (i == 1) {
                                                        try {
                                                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + message.messageOwner.media.phone_number));
                                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                            getParentActivity().startActivity(intent);
                                                        } catch (Exception e) {
                                                            FileLog.e("tmessages", e);
                                                        }
                                                    } else if (i == 0) {
                                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                                                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                            clipboard.setText(message.messageOwner.media.phone_number);
                                                        } else {
                                                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", message.messageOwner.media.phone_number);
                                                            clipboard.setPrimaryClip(clip);
                                                        }
                                                    }
                                                }
                                            }
                                    );
                                    showAlertDialog(builder);
                                }
                            }
                        }
                    }
                });

                contactView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }

            if (contactAvatar != null) {
                contactAvatar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
            }

            if (avatarImageView != null) {
                avatarImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (actionBarLayer.isActionModeShowed()) {
                            processRowSelect(view);
                            return;
                        }
                        if (message != null) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", message.messageOwner.from_id);
                            presentFragment(new UserProfileActivity(args));
                        }
                    }
                });
            }

            if (photoImage != null) {
                photoImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        processOnClick(view);
                    }
                });

                photoImage.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }
        }

        private void processOnClick(View view) {
            if (actionBarLayer.isActionModeShowed()) {
                processRowSelect(view);
                return;
            }
            if (message != null) {
                if (message.type == 11) {
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                }
            }
        }
    }
}
