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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.objects.MessageObject;
import org.telegram.objects.PhotoObject;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.EmojiView;
import org.telegram.ui.Views.LayoutListView;
import org.telegram.ui.Views.MessageActionLayout;
import org.telegram.ui.Views.MessageLayout;
import org.telegram.ui.Views.OnSwipeTouchListener;
import org.telegram.ui.Views.SizeNotifierRelativeLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class ChatActivity extends BaseFragment implements SizeNotifierRelativeLayout.SizeNotifierRelativeLayoutDelegate, NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate {
    private LayoutListView chatListView;
    private BackupImageView avatarImageView;
    private TLRPC.Chat currentChat;
    private TLRPC.User currentUser;
    private TLRPC.EncryptedChat currentEncryptedChat;
    private ChatAdapter chatAdapter;
    private EditText messsageEditText;
    private ImageButton sendButton;
    private PopupWindow emojiPopup;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private boolean keyboardVisible;
    private int keyboardHeight = 0;
    private int keyboardHeightLand = 0;
    private View topPanel;
    private View secretChatPlaceholder;
    private View contentView;
    private View progressView;
    private boolean ignoreTextChange = false;
    private TextView emptyView;
    private float displayDensity;
    private View bottomOverlay;
    private TextView bottomOverlayText;
    private MessageObject selectedObject;
    private MessageObject forwaringMessage;
    private TextView secretViewStatusTextView;
    private Point displaySize = new Point();
    private boolean paused = true;
    private boolean readWhenResume = false;
    private int readWithDate = 0;
    private int readWithMid = 0;
    private boolean swipeOpening = false;
    public boolean scrollToTopOnResume = false;
    private boolean scrollToTopUnReadOnResume = false;
    private boolean isCustomTheme = false;
    private ImageView topPlaneClose;
    private View pagedownButton;
    private TextView topPanelText;
    private long dialog_id;
    AlertDialog visibleDialog = null;
    private final Rect mLastTouch = new Rect();
    private SizeNotifierRelativeLayout sizeNotifierRelativeLayout;
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
    private long lastTypingTimeSend = 0;
    private int minDate = 0;
    private int progressTag = 0;
    private int fontSize = 16;
    private boolean invalidateAfterAnimation = false;
    boolean first = true;
    private int unread_to_load = 0;
    private int first_unread_id = 0;
    private int last_unread_id = 0;
    private boolean unread_end_reached = true;
    private boolean loadingForward = false;
    private MessageObject unreadMessageObject = null;
    //private boolean reloadAfterAnimation = false;

    private int videoLocalId;
    private String currentPicturePath;

    private TLRPC.ChatParticipants info = null;
    private int onlineCount = -1;

    private HashMap<String, ProgressBar> progressBarMap = new HashMap<String, ProgressBar>();
    private HashMap<String, ArrayList<ProgressBar>> loadingFile = new HashMap<String, ArrayList<ProgressBar>>();
    private HashMap<Integer, String> progressByTag = new HashMap<Integer, String>();

    ActionMode mActionMode = null;
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            menu.clear();
            MenuInflater inflater = actionMode.getMenuInflater();
            if (currentEncryptedChat == null) {
                inflater.inflate(R.menu.messages_full_menu, menu);
            } else {
                inflater.inflate(R.menu.messages_encrypted_menu, menu);
            }
            menu.findItem(R.id.copy).setVisible(selectedMessagesCanCopyIds.size() == 1);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.copy: {
                    MessageObject messageObject = (MessageObject)selectedMessagesCanCopyIds.values().toArray()[0];
                    if(android.os.Build.VERSION.SDK_INT < 11) {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager)parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(messageObject.messageOwner.message);
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", messageObject.messageOwner.message);
                        clipboard.setPrimaryClip(clip);
                    }
                    break;
                }
                case R.id.delete: {
                    ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
                    MessagesController.Instance.deleteMessages(ids);
                    break;
                }
                case R.id.forward: {
                    MessagesActivity fragment = new MessagesActivity();
                    fragment.selectAlertString = R.string.ForwardMessagesTo;
                    fragment.animationType = 1;
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putBoolean("serverOnly", true);
                    fragment.setArguments(args);
                    fragment.delegate = ChatActivity.this;
                    ((ApplicationActivity)parentActivity).presentFragment(fragment, "select_chat", false);
                    break;
                }
            }
            actionMode.finish();
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mActionMode = null;
            updateVisibleRows();
        }
    };

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        int chatId = getArguments().getInt("chat_id", 0);
        int userId = getArguments().getInt("user_id", 0);
        int encId = getArguments().getInt("enc_id", 0);

        if (chatId != 0) {
            currentChat = MessagesController.Instance.chats.get(chatId);
            if (currentChat == null) {
                return false;
            }
            MessagesController.Instance.loadChatInfo(currentChat.id);
            dialog_id = -chatId;
        } else if (userId != 0) {
            currentUser = MessagesController.Instance.users.get(userId);
            if (currentUser == null) {
                return false;
            }
            dialog_id = userId;
        } else if (encId != 0) {
            currentEncryptedChat = MessagesController.Instance.encryptedChats.get(encId);
            if (currentEncryptedChat == null) {
                return false;
            }
            currentUser = MessagesController.Instance.users.get(currentEncryptedChat.user_id);
            if (currentUser == null) {
                return false;
            }
            dialog_id = ((long)encId) << 32;
            minMessageId = Integer.MAX_VALUE;
        } else {
            return false;
        }
        NotificationCenter.Instance.addObserver(this, MessagesController.messagesDidLoaded);
        NotificationCenter.Instance.addObserver(this, 999);
        NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.addObserver(this, MessagesController.didReceivedNewMessages);
        NotificationCenter.Instance.addObserver(this, MessagesController.closeChats);
        NotificationCenter.Instance.addObserver(this, MessagesController.userPrintUpdate);
        NotificationCenter.Instance.addObserver(this, MessagesController.messagesReaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.messagesDeleted);
        NotificationCenter.Instance.addObserver(this, MessagesController.messageReceivedByServer);
        NotificationCenter.Instance.addObserver(this, MessagesController.messageReceivedByAck);
        NotificationCenter.Instance.addObserver(this, MessagesController.messageSendError);
        NotificationCenter.Instance.addObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.Instance.addObserver(this, MessagesController.messagesReadedEncrypted);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileUploadProgressChanged);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.Instance.addObserver(this, 997);
        loading = true;
        MessagesController.Instance.loadMessages(dialog_id, 0, 30, 0, true, 0, classGuid, true, false);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        fontSize = preferences.getInt("fons_size", 16);

        keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height", Emoji.scale(200.0f));
        keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height_land3", Emoji.scale(200.0f));
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.Instance.removeObserver(this, MessagesController.messagesDidLoaded);
        NotificationCenter.Instance.removeObserver(this, 999);
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.didReceivedNewMessages);
        NotificationCenter.Instance.removeObserver(this, MessagesController.closeChats);
        NotificationCenter.Instance.removeObserver(this, MessagesController.userPrintUpdate);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messagesReaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messagesDeleted);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messageReceivedByServer);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messageReceivedByAck);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messageSendError);
        NotificationCenter.Instance.removeObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.Instance.removeObserver(this, MessagesController.messagesReadedEncrypted);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileUploadProgressChanged);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.Instance.removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.Instance.removeObserver(this, 997);
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.delegate = null;
            sizeNotifierRelativeLayout = null;
        }

        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Display display = parentActivity.getWindowManager().getDefaultDisplay();
        if(android.os.Build.VERSION.SDK_INT < 13) {
            displaySize.set(display.getWidth(), display.getHeight());
        } else {
            display.getSize(displaySize);
        }
        displayDensity = getResources().getDisplayMetrics().density;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.chat_layout, container, false);

            sizeNotifierRelativeLayout = (SizeNotifierRelativeLayout)fragmentView.findViewById(R.id.chat_layout);
            sizeNotifierRelativeLayout.delegate = this;
            contentView = sizeNotifierRelativeLayout;

            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            chatListView = (LayoutListView)fragmentView.findViewById(R.id.chat_list_view);
            chatListView.setAdapter(chatAdapter = new ChatAdapter(parentActivity));
            topPanel = fragmentView.findViewById(R.id.top_panel);
            topPlaneClose = (ImageView)fragmentView.findViewById(R.id.top_plane_close);
            topPanelText = (TextView)fragmentView.findViewById(R.id.top_panel_text);
            bottomOverlay = fragmentView.findViewById(R.id.bottom_overlay);
            bottomOverlayText = (TextView)fragmentView.findViewById(R.id.bottom_overlay_text);
            View bottomOverlayChat = fragmentView.findViewById(R.id.bottom_overlay_chat);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            pagedownButton = fragmentView.findViewById(R.id.pagedown_button);
            View progressViewInner = progressView.findViewById(R.id.progressLayoutInner);

            updateContactStatus();

            ImageView backgroundImage = (ImageView) fragmentView.findViewById(R.id.background_image);

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            int selectedBackground = preferences.getInt("selectedBackground", 1000001);
            int selectedColor = preferences.getInt("selectedColor", 0);
            if (selectedColor != 0) {
                backgroundImage.setBackgroundColor(selectedColor);
                chatListView.setCacheColorHint(selectedColor);
            } else {
                chatListView.setCacheColorHint(0);
                if (selectedBackground == 1000001) {
                    backgroundImage.setImageResource(R.drawable.background_hd);
                } else {
                    File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                    if (toFile.exists()) {
                        if (ApplicationLoader.cachedWallpaper != null) {
                            backgroundImage.setImageBitmap(ApplicationLoader.cachedWallpaper);
                        } else {
                            backgroundImage.setImageURI(Uri.fromFile(toFile));
                            if (backgroundImage.getDrawable() instanceof BitmapDrawable) {
                                ApplicationLoader.cachedWallpaper = ((BitmapDrawable)backgroundImage.getDrawable()).getBitmap();
                            }
                        }
                        isCustomTheme = true;
                    } else {
                        backgroundImage.setImageResource(R.drawable.background_hd);
                    }
                }
            }

            if (currentEncryptedChat != null) {
                secretChatPlaceholder = contentView.findViewById(R.id.secret_placeholder);
                if (isCustomTheme) {
                    secretChatPlaceholder.setBackgroundResource(R.drawable.system_black);
                } else {
                    secretChatPlaceholder.setBackgroundResource(R.drawable.system_blue);
                }
                secretViewStatusTextView = (TextView)contentView.findViewById(R.id.invite_text);
                secretChatPlaceholder.setPadding((int)(16 * displayDensity), (int)(12 * displayDensity), (int)(16 * displayDensity), (int)(12 * displayDensity));

                View v = contentView.findViewById(R.id.secret_placeholder);
                v.setVisibility(View.VISIBLE);

                if (currentEncryptedChat.admin_id == UserConfig.clientUserId) {
                    secretViewStatusTextView.setText(String.format(getStringEntry(R.string.EncryptedPlaceholderTitleOutgoing), currentUser.first_name));
                } else {
                    secretViewStatusTextView.setText(String.format(getStringEntry(R.string.EncryptedPlaceholderTitleIncoming), currentUser.first_name));
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
            emptyView.setPadding((int)(7 * displayDensity), (int)(1 * displayDensity), (int)(7 * displayDensity), (int)(1 * displayDensity));

            if (currentUser != null && currentUser.id == 333000) {
                emptyView.setText(R.string.GotAQuestion);
            }

            chatListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long id) {
                    createMenu(view, false);
                    return true;
                }
            });

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
                                    MessagesController.Instance.loadMessages(dialog_id, 0, 20, maxMessageId, !cacheEndReaced, minDate, classGuid, false, false);
                                } else {
                                    MessagesController.Instance.loadMessages(dialog_id, 0, 20, 0, !cacheEndReaced, minDate, classGuid, false, false);
                                }
                                loading = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount >= totalItemCount - 4) {
                            if (!unread_end_reached && !loadingForward) {
                                MessagesController.Instance.loadMessages(dialog_id, 0, 20, minMessageId, true, minDate, classGuid, false, true);
                                loadingForward = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount == totalItemCount) {
                            showPagedownButton(false, true);
                        }
                    } else {
                        showPagedownButton(false, false);
                    }
                }
            });

            messsageEditText = (EditText)fragmentView.findViewById(R.id.chat_text_edit);

            sendButton = (ImageButton)fragmentView.findViewById(R.id.chat_send_button);
            sendButton.setImageResource(R.drawable.send_button_states);
            sendButton.setEnabled(false);
            emojiButton = (ImageView)fragmentView.findViewById(R.id.chat_smile_button);

            if (loading && messages.isEmpty()) {
                progressView.setVisibility(View.VISIBLE);
                chatListView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                if (currentEncryptedChat == null) {
                    chatListView.setEmptyView(emptyView);
                } else {
                    chatListView.setEmptyView(secretChatPlaceholder);
                }
            }

            emojiButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (emojiPopup == null) {
                        showEmojiPopup(true);
                    } else {
                        showEmojiPopup(!emojiPopup.isShowing());
                    }
                }
            });

            messsageEditText.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View view, int i, KeyEvent keyEvent) {
                    if (i == 4 && !keyboardVisible && emojiPopup != null && emojiPopup.isShowing()) {
                        if (keyEvent.getAction() == 1) {
                            showEmojiPopup(false);
                        }
                        return true;
                    }
                    return false;
                }
            });

            sendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String message = messsageEditText.getText().toString().trim();
                    if (processSendingText(message)) {
                        messsageEditText.setText("");
                        lastTypingTimeSend = 0;
                        chatListView.post(new Runnable() {
                            @Override
                            public void run() {
                                chatListView.setSelectionFromTop(messages.size() - 1, -10000 - chatListView.getPaddingTop());
                            }
                        });
                    }
                }
            });

            pagedownButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (unread_end_reached || first_unread_id == 0) {
                        chatListView.setSelectionFromTop(messages.size() - 1, -10000 - chatListView.getPaddingTop());
                    } else {
                        messages.clear();
                        messagesByDays.clear();
                        messagesDict.clear();
                        progressView.setVisibility(View.VISIBLE);
                        chatListView.setEmptyView(null);
                        maxMessageId = Integer.MAX_VALUE;
                        minMessageId = Integer.MIN_VALUE;
                        maxDate = Integer.MIN_VALUE;
                        minDate = 0;
                        MessagesController.Instance.loadMessages(dialog_id, 0, 30, 0, true, 0, classGuid, true, false);
                        loading = true;
                        chatAdapter.notifyDataSetChanged();
                    }
                }
            });

            messsageEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    String message = charSequence.toString().trim();
                    message = message.replaceAll("\n\n+", "\n\n");
                    message = message.replaceAll(" +", " ");
                    sendButton.setEnabled(message.length() != 0);

                    if (message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                        int currentTime = ConnectionsManager.Instance.getCurrentTime();
                        if (currentUser != null && currentUser.status != null && currentUser.status.expires < currentTime && currentUser.status.was_online < currentTime) {
                            return;
                        }
                        lastTypingTimeSend = System.currentTimeMillis();
                        MessagesController.Instance.sendTyping(dialog_id, classGuid);
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    int i = 0;
                    ImageSpan[] arrayOfImageSpan = editable.getSpans(0, editable.length(), ImageSpan.class);
                    int j = arrayOfImageSpan.length;
                    while (true) {
                        if (i >= j) {
                            Emoji.replaceEmoji(editable);
                            return;
                        }
                        editable.removeSpan(arrayOfImageSpan[i]);
                        i++;
                    }
                }
            });

            bottomOverlayChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (currentChat != null) {
                        MessagesController.Instance.deleteDialog(-currentChat.id, 0, false);
                        finishFragment();
                    }
                }
            });

            chatListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (mActionMode != null) {
                        processRowSelect(view);
                        return;
                    }
                    if (!spanClicked(chatListView, view, R.id.chat_message_text)) {
                        createMenu(view, true);
                    }
                }
            });

            chatListView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                            visibleDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    finishFragment(true);
                }

                public void onSwipeLeft() {
                    if (swipeOpening) {
                        return;
                    }
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                            visibleDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    if (avatarImageView != null) {
                        swipeOpening = true;
                        avatarImageView.performClick();
                    }
                }

                @Override
                public void onTouchUp(MotionEvent event) {
                    mLastTouch.right = (int) event.getX();
                    mLastTouch.bottom = (int) event.getY();
                }
            });

            emptyView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                }

                public void onSwipeLeft() {
                    if (swipeOpening) {
                        return;
                    }
                    if (avatarImageView != null) {
                        swipeOpening = true;
                        avatarImageView.performClick();
                    }
                }
            });
            if (currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left)) {
                bottomOverlayChat.setVisibility(View.VISIBLE);
            } else {
                bottomOverlayChat.setVisibility(View.GONE);
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void showPagedownButton(boolean show, boolean animated) {
        if (pagedownButton == null) {
            return;
        }
        if (show) {
            if (pagedownButton.getVisibility() == View.GONE) {
                if (android.os.Build.VERSION.SDK_INT >= 16 && animated) {
                    pagedownButton.setVisibility(View.VISIBLE);
                    pagedownButton.setAlpha(0);
                    pagedownButton.animate().alpha(1).setDuration(200).start();
                } else {
                    pagedownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (pagedownButton.getVisibility() == View.VISIBLE) {
                if (android.os.Build.VERSION.SDK_INT >= 16 && animated) {
                    pagedownButton.animate().alpha(0).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            pagedownButton.setVisibility(View.GONE);
                        }
                    }).setDuration(200).start();
                } else {
                    pagedownButton.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void onAnimationEnd() {
        super.onAnimationEnd();
        if (invalidateAfterAnimation) {
            if (chatListView != null) {
                updateVisibleRows();
            }
        }
    }

    @Override
    public void willBeHidden() {
        super.willBeHidden();
        paused = true;
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
            bottomOverlayText.setText(getStringEntry(R.string.EncryptionProcessing));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
            bottomOverlayText.setText(Html.fromHtml(String.format(getStringEntry(R.string.AwaitingEncryption), "<b>" + currentUser.first_name + "</b>")));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
            bottomOverlayText.setText(getStringEntry(R.string.EncryptionRejected));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
            bottomOverlay.setVisibility(View.GONE);
        }
        if (hideKeyboard) {
            hideEmojiPopup();
            if (parentActivity != null) {
                Utilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        if (parentActivity != null) {
            parentActivity.supportInvalidateOptionsMenu();
        }
    }

    private void addToLoadingFile(String path, ProgressBar bar) {
        ArrayList<ProgressBar> arr = loadingFile.get(path);
        if (arr == null) {
            arr = new ArrayList<ProgressBar>();
            loadingFile.put(path, arr);
        }
        arr.add(bar);
    }

    private void removeFromloadingFile(String path, ProgressBar bar) {
        ArrayList<ProgressBar> arr = loadingFile.get(path);
        if (arr != null) {
            arr.remove(bar);
        }
    }

    private void updateOnlineCount() {
        if (info == null) {
            return;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.Instance.getCurrentTime();
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.Instance.users.get(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.status.was_online > currentTime || user.id == UserConfig.clientUserId) && (user.status.expires > 10000 || user.status.was_online > 10000)) {
                onlineCount++;
            }
        }

        updateSubtitle();
    }

    private int getMessageType(MessageObject messageObject) {
        if (currentEncryptedChat == null) {
            if (messageObject.messageOwner.id <= 0 && messageObject.messageOwner.out) {
                if (messageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                    return 0;
                } else {
                    return -1;
                }
            } else {
                if (messageObject.type == 15) {
                    return -1;
                } else if (messageObject.type == 10 || messageObject.type == 11) {
                    if (messageObject.messageOwner.id == 0) {
                        return -1;
                    }
                    return 1;
                } else {
                    if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                        return 2;
                    } else {
                        return 3;
                    }
                }
            }
        } else {
            if (messageObject.type == 15) {
                return -1;
            } else if (messageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                return 0;
            } else if (messageObject.type == 10 || messageObject.type == 11 || messageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                if (messageObject.messageOwner.id == 0) {
                    return -1;
                }
                return 1;
            } else {
                if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
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
            if (messageObject.type == 0 || messageObject.type == 1 || messageObject.type == 8 || messageObject.type == 9) {
                selectedMessagesCanCopyIds.remove(messageObject.messageOwner.id);
            }
            if (selectedMessagesIds.size() == 1) {
                if (mActionMode != null && mActionMode.getMenu() != null) {
                    mActionMode.getMenu().findItem(R.id.copy).setVisible(selectedMessagesCanCopyIds.size() == 1);
                }
            }
        } else {
            boolean update = false;
            if (selectedMessagesIds.size() == 1) {
                update = true;
            }
            selectedMessagesIds.put(messageObject.messageOwner.id, messageObject);
            if (messageObject.type == 0 || messageObject.type == 1 || messageObject.type == 8 || messageObject.type == 9) {
                selectedMessagesCanCopyIds.put(messageObject.messageOwner.id, messageObject);
            }
            if (update) {
                if (mActionMode != null && mActionMode.getMenu() != null) {
                    mActionMode.getMenu().findItem(R.id.copy).setVisible(false);
                }
            }
        }
    }

    private void processRowSelect(View view) {
        View parentView = getRowParentView(view);
        if (parentView == null) {
            return;
        }
        ChatListRowHolderEx holder = (ChatListRowHolderEx)parentView.getTag();

        MessageObject message = holder.message;
        if (getMessageType(message) < 2) {
            return;
        }
        addToSelectedMessages(message);
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void updateActionModeTitle() {
        if (mActionMode == null) {
            return;
        }
        if (selectedMessagesIds.isEmpty()) {
            mActionMode.finish();
        } else {
            mActionMode.setTitle(String.format("%s %d", getStringEntry(R.string.Selected), selectedMessagesIds.size()));
        }
    }

    private void updateSubtitle() {
        if (isFinish) {
            return;
        }

        if (paused || getActivity() == null) {
            return;
        }

        ActionBar actionBar = parentActivity.getSupportActionBar();

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }

        if (currentChat != null) {
            actionBar.setTitle(currentChat.title);
            if (title != null) {
                title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                title.setCompoundDrawablePadding(0);
            }
        } else if (currentUser != null) {
            if (currentUser.id != 333000 && MessagesController.Instance.contactsDict.get(currentUser.id) == null && (MessagesController.Instance.contactsDict.size() != 0 || !MessagesController.Instance.loadingContacts)) {
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    actionBar.setTitle(PhoneFormat.Instance.format("+" + currentUser.phone));
                } else {
                    actionBar.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
                }
            } else {
                actionBar.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
            }

            if (title != null) {
                if (currentEncryptedChat != null) {
                    title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_white, 0, 0, 0);
                    title.setCompoundDrawablePadding((int)(4 * displayDensity));
                } else {
                    title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                    title.setCompoundDrawablePadding(0);
                }
            }
        }

        CharSequence printString = MessagesController.Instance.printingStrings.get(dialog_id);
        if (printString == null || printString.length() == 0) {
            setTypingAnimation(false);
            if (currentChat != null) {
                if (currentChat instanceof TLRPC.TL_chatForbidden) {
                    actionBar.setSubtitle(getStringEntry(R.string.YouWereKicked));
                } else if (currentChat.left) {
                    actionBar.setSubtitle(getStringEntry(R.string.YouLeft));
                } else {
                    if (onlineCount > 0 && currentChat.participants_count != 0) {
                        actionBar.setSubtitle(String.format("%d %s, %d %s", currentChat.participants_count, getStringEntry(R.string.Members), onlineCount, getStringEntry(R.string.Online)));
                    } else {
                        actionBar.setSubtitle(String.format("%d %s", currentChat.participants_count, getStringEntry(R.string.Members)));
                    }
                }
            } else if (currentUser != null) {
                if (currentUser.status == null) {
                    actionBar.setSubtitle(getStringEntry(R.string.Offline));
                } else {
                    int currentTime = ConnectionsManager.Instance.getCurrentTime();
                    if (currentUser.status.expires > currentTime || currentUser.status.was_online > currentTime) {
                        actionBar.setSubtitle(getStringEntry(R.string.Online));
                    } else {
                        if (currentUser.status.was_online <= 10000 && currentUser.status.expires <= 10000) {
                            actionBar.setSubtitle(getStringEntry(R.string.Invisible));
                        } else {
                            int value = currentUser.status.was_online;
                            if (value == 0) {
                                value = currentUser.status.expires;
                            }
                            actionBar.setSubtitle(String.format("%s %s", getStringEntry(R.string.LastSeen), Utilities.formatDateOnline(value)));
                        }
                    }
                }
            }
        } else {
            actionBar.setSubtitle(printString);
            setTypingAnimation(true);
        }
    }

    private void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        int placeHolderId = 0;
        if (currentUser != null) {
            currentUser = MessagesController.Instance.users.get(currentUser.id);
            if (currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            }
            placeHolderId = Utilities.getUserAvatarForId(currentUser.id);
        } else if (currentChat != null) {
            currentChat = MessagesController.Instance.chats.get(currentChat.id);
            if (currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
            placeHolderId = Utilities.getGroupAvatarForId(currentChat.id);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", placeHolderId);
        }
    }

    @Override
    public void onSizeChanged(int height) {
        Rect localRect = new Rect();
        parentActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(localRect);

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        if (height > Emoji.scale(50)) {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                keyboardHeightLand = height;
                parentActivity.getSharedPreferences("emoji", 0).edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                parentActivity.getSharedPreferences("emoji", 0).edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (emojiPopup != null && emojiPopup.isShowing()) {
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            final WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams)emojiPopup.getContentView().getLayoutParams();
            layoutParams.width = contentView.getWidth();
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                layoutParams.height = keyboardHeightLand;
            } else {
                layoutParams.height = keyboardHeight;
            }
            wm.updateViewLayout(emojiPopup.getContentView(), layoutParams);
            if (!keyboardVisible) {
                contentView.post(new Runnable() {
                    @Override
                    public void run() {
                        contentView.setPadding(0, 0, 0, layoutParams.height);
                        contentView.forceLayout();
                    }
                });
            }
        }

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        if (keyboardVisible && contentView.getPaddingBottom() > 0) {
            showEmojiPopup(false);
        } else if (!keyboardVisible && keyboardVisible != oldValue && emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {
                Utilities.addMediaToGallery(currentPicturePath);
                processSendingPhoto(currentPicturePath);
                currentPicturePath = null;
            } else if (requestCode == 1) {
                Uri imageUri = data.getData();
                if (imageUri == null) {
                    return;
                }
                String imageFilePath = null;
                if (imageUri.getScheme().contains("file")) {
                    imageFilePath = imageUri.getPath();
                } else {
                    ActionBarActivity inflaterActivity = parentActivity;
                    if (inflaterActivity == null) {
                        inflaterActivity = (ActionBarActivity)getActivity();
                    }
                    if (inflaterActivity == null) {
                        return;
                    }
                    try {
                        Cursor cursor = inflaterActivity.getContentResolver().query(imageUri, new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                        if (cursor == null) {
                            return;
                        }
                        if (cursor.moveToFirst()) {
                            imageFilePath = cursor.getString(0);
                        }
                        cursor.close();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
                processSendingPhoto(imageFilePath);
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
                        ActionBarActivity inflaterActivity = parentActivity;
                        if (inflaterActivity == null) {
                            inflaterActivity = (ActionBarActivity)getActivity();
                        }
                        if (inflaterActivity == null) {
                            return;
                        }
                        try {
                            Cursor cursor = inflaterActivity.getContentResolver().query(uri, new String[]{android.provider.MediaStore.Images.ImageColumns.DATA}, null, null, null);
                            if (cursor == null) {
                                return;
                            }
                            cursor.moveToFirst();
                            videoPath = cursor.getString(0);
                            cursor.close();
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
                processSendingVideo(videoPath);
            }
        }
    }

    public boolean processSendingText(String text) {
        text = text.replaceAll("\n\n+", "\n\n");
        text = text.replaceAll(" +", " ");
        if (text.length() != 0) {
            int count = (int)Math.ceil(text.length() / 2048.0f);
            for (int a = 0; a < count; a++) {
                String mess = text.substring(a * 2048, Math.min((a + 1) * 2048, text.length()));
                MessagesController.Instance.sendMessage(mess, dialog_id);
            }
            return true;
        }
        return false;
    }

    public void processSendingPhoto(String imageFilePath) {
        if (imageFilePath == null) {
            return;
        }
        TLRPC.TL_photo photo = MessagesController.Instance.generatePhotoSizes(imageFilePath);
        if (photo != null) {
            MessagesController.Instance.sendMessage(photo, dialog_id);
            if (chatListView != null) {
                chatListView.setSelection(messages.size() + 1);
            }
            scrollToTopOnResume = true;
        }
    }

    public void processSendingVideo(final String videoPath) {
        if (videoPath == null) {
            return;
        }
        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
        TLRPC.PhotoSize size = FileLoader.scaleAndSaveImage(thumb, 90, 90, 55, currentEncryptedChat != null);
        if (size == null) {
            return;
        }
        size.type = "s";
        TLRPC.TL_video video = new TLRPC.TL_video();
        video.thumb = size;
        video.caption = "";
        video.id = videoLocalId;
        video.path = videoPath;
        File temp = new File(videoPath);
        if (temp != null && temp.exists()) {
            video.size = (int)temp.length();
        }
        UserConfig.lastLocalId--;
        UserConfig.saveConfig(false);

        MediaPlayer mp = MediaPlayer.create(ApplicationLoader.applicationContext, Uri.fromFile(new File(videoPath)));
        if (mp == null) {
            return;
        }
        video.duration = (int)Math.ceil(mp.getDuration() / 1000.0f);
        video.w = mp.getVideoWidth();
        video.h = mp.getVideoHeight();
        mp.release();

        MediaStore.Video.Media media = new MediaStore.Video.Media();
        MessagesController.Instance.sendMessage(video, dialog_id);
        if (chatListView != null) {
            chatListView.setSelection(messages.size() + 1);
        }
        scrollToTopOnResume = true;
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
        if (id == MessagesController.messagesDidLoaded) {
            long did = (Long)args[0];
            if (did == dialog_id) {
                int offset = (Integer)args[1];
                int count = (Integer)args[2];
                boolean isCache = (Boolean)args[4];
                int fnid = (Integer)args[5];
                int last_unread_date = (Integer)args[8];
                boolean forwardLoad = (Boolean)args[9];
                boolean wasUnread = false;
                boolean positionToUnread = false;
                if (fnid != 0) {
                    first_unread_id = (Integer)args[5];
                    last_unread_id = (Integer)args[6];
                    unread_to_load = (Integer)args[7];
                    positionToUnread = true;
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>)args[3];

                int newRowsCount = 0;
                unread_end_reached = last_unread_id == 0;
                for (int a = 0; a < messArr.size(); a++) {
                    MessageObject obj = messArr.get(a);
                    if (messagesDict.containsKey(obj.messageOwner.id)) {
                        continue;
                    }

                    if (obj.messageOwner.id > 0) {
                        maxMessageId = Math.min(obj.messageOwner.id, maxMessageId);
                        minMessageId = Math.max(obj.messageOwner.id, minMessageId);
                    } else if (currentEncryptedChat != null) {
                        minMessageId = Math.min(obj.messageOwner.id, minMessageId);
                    }
                    maxDate = Math.max(maxDate, obj.messageOwner.date);
                    if (minDate == 0 || obj.messageOwner.date < minDate) {
                        minDate = obj.messageOwner.date;
                    }
                    if (!obj.messageOwner.out && obj.messageOwner.unread) {
                        wasUnread = true;
                    }
                    messagesDict.put(obj.messageOwner.id, obj);
                    ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);

                    if (dayArray == null) {
                        dayArray = new ArrayList<MessageObject>();
                        messagesByDays.put(obj.dateKey, dayArray);

                        TLRPC.Message dateMsg = new TLRPC.Message();
                        dateMsg.message = Utilities.formatDateChat(obj.messageOwner.date);
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(dateMsg, null);
                        dateObj.type = 10;
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
                            dateObj.type = 15;
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
                            if (currentEncryptedChat != null) {
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
                                    chatListView.setSelectionFromTop(0, -(int)(11 * displayDensity));
                                } else {
                                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), -(int)(11 * displayDensity));
                                }
                                ViewTreeObserver obs = chatListView.getViewTreeObserver();
                                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (messages.get(messages.size() - 1) == unreadMessageObject) {
                                            chatListView.setSelectionFromTop(0, -(int)(11 * displayDensity));
                                        } else {
                                            chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), -(int)(11 * displayDensity));
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
                                        chatListView.setSelectionFromTop(messages.size() - 1, -10000 - chatListView.getPaddingTop());
                                    }
                                });
                            }
                        } else {
                            int firstVisPos = chatListView.getLastVisiblePosition();
                            View firstVisView = chatListView.getChildAt(chatListView.getChildCount() - 1);
                            int top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                            chatAdapter.notifyDataSetChanged();
                            chatListView.setSelectionFromTop(firstVisPos + newRowsCount, top);
                        }

                        if (paused) {
                            scrollToTopOnResume = true;
                            if (positionToUnread && unreadMessageObject != null) {
                                scrollToTopUnReadOnResume = true;
                            }
                        }

                        if (first) {
                            if (chatListView.getEmptyView() == null) {
                                if (currentEncryptedChat == null) {
                                    chatListView.setEmptyView(emptyView);
                                } else {
                                    chatListView.setEmptyView(secretChatPlaceholder);
                                }
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
                        MessagesController.Instance.markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, last_unread_id, 0, last_unread_date, wasUnread);
                    } else {
                        MessagesController.Instance.markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, wasUnread);
                    }
                    first = false;
                }

                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
            }
        } else if (id == 999) {
            if (animationInProgress) {
                invalidateAfterAnimation = true;
            } else {
                if (chatListView != null) {
                    chatListView.invalidateViews();
                }
            }
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        } else if (id == MessagesController.updateInterfaces) {
            int updateMask = (Integer)args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateSubtitle();
                updateOnlineCount();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                checkAndUpdateAvatar();
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
            }
        } else if (id == MessagesController.didReceivedNewMessages) {
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
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        currentMaxDate = Math.max(currentMaxDate, obj.messageOwner.date);
                        if (obj.messageOwner.id > 0) {
                            currentMinMsgId = Math.max(obj.messageOwner.id, currentMinMsgId);
                        } else if (currentEncryptedChat != null) {
                            currentMinMsgId = Math.min(obj.messageOwner.id, currentMinMsgId);
                        }

                        if (!obj.messageOwner.out && obj.messageOwner.unread) {
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
                            MessagesController.Instance.markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, currentMinMsgId, 0, currentMaxDate, true);
                        }
                    }
                    updateVisibleRows();
                } else {
                    boolean markAsRead = false;
                    int oldCount = messages.size();
                    for (MessageObject obj : arr) {
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        if (minDate == 0 || obj.messageOwner.date < minDate) {
                            minDate = obj.messageOwner.date;
                        }
                        if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                            progressBarMap.put(obj.messageOwner.attachPath, null);
                        }

                        if (obj.messageOwner.out) {
                            removeUnreadPlane(false);
                        }

                        if (!obj.messageOwner.out && unreadMessageObject != null) {
                            unread_to_load++;
                        }

                        if (obj.messageOwner.id > 0) {
                            maxMessageId = Math.min(obj.messageOwner.id, maxMessageId);
                            minMessageId = Math.max(obj.messageOwner.id, minMessageId);
                        } else if (currentEncryptedChat != null) {
                            minMessageId = Math.min(obj.messageOwner.id, minMessageId);
                        }
                        maxDate = Math.max(maxDate, obj.messageOwner.date);
                        messagesDict.put(obj.messageOwner.id, obj);
                        ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);
                        if (dayArray == null) {
                            dayArray = new ArrayList<MessageObject>();
                            messagesByDays.put(obj.dateKey, dayArray);

                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = Utilities.formatDateChat(obj.messageOwner.date);
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(dateMsg, null);
                            dateObj.type = 10;
                            messages.add(0, dateObj);
                        }
                        if (!obj.messageOwner.out && obj.messageOwner.unread) {
                            obj.messageOwner.unread = false;
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
                            if (paused) {
                                scrollToTopOnResume = true;
                            } else {
                                chatListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -10000 - chatListView.getPaddingTop());
                                    }
                                });
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
                            MessagesController.Instance.markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, true);
                        }
                    }
                }
                if (updateChat) {
                    updateSubtitle();
                    checkAndUpdateAvatar();
                }
            }
        } else if (id == MessagesController.closeChats) {
            if (messsageEditText != null && messsageEditText.isFocused()) {
                Utilities.hideKeyboard(messsageEditText);
            }
            removeSelfFromStack();
        } else if (id == MessagesController.userPrintUpdate) {
            long uid = (Long)args[0];
            if (uid == dialog_id) {
                updateSubtitle();
            }
        } else if (id == MessagesController.messagesReaded) {
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
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
            }
        } else if (id == MessagesController.messagesDeleted) {
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
                            if (index != -1) {
                                messages.remove(index);
                            }
                        }
                        updated = true;
                    }
                }
            }
            if (messages.isEmpty()) {
                if (!endReached && !loading) {
                    progressView.setVisibility(View.VISIBLE);
                    chatListView.setEmptyView(null);
                    maxMessageId = Integer.MAX_VALUE;
                    minMessageId = Integer.MIN_VALUE;
                    maxDate = Integer.MIN_VALUE;
                    minDate = 0;
                    MessagesController.Instance.loadMessages(dialog_id, 0, 30, 0, !cacheEndReaced, minDate, classGuid, false, false);
                    loading = true;
                }
            }
            if (updated && chatAdapter != null) {
                removeUnreadPlane(false);
                chatAdapter.notifyDataSetChanged();
            }
        } else if (id == MessagesController.messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                messagesDict.remove(msgId);
                messagesDict.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SENT;
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
                if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                    progressBarMap.remove(obj.messageOwner.attachPath);
                }
            }
        } else if (id == MessagesController.messageReceivedByAck) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                    progressBarMap.remove(obj.messageOwner.attachPath);
                }
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SENT;
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
            }
        } else if (id == MessagesController.messageSendError) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SEND_ERROR;
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
                if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                    progressBarMap.remove(obj.messageOwner.attachPath);
                }
            }
        } else if (id == 997) {
            MessagesController.Instance.sendMessage((Double)args[0], (Double)args[1], dialog_id);
            if (chatListView != null) {
                chatListView.setSelection(messages.size() + 1);
                scrollToTopOnResume = true;
            }
        } else if (id == MessagesController.chatInfoDidLoaded) {
            int chatId = (Integer)args[0];
            if (currentChat != null && chatId == currentChat.id) {
                info = (TLRPC.ChatParticipants)args[1];
                updateOnlineCount();
            }
        } else if (id == FileLoader.FileUploadProgressChanged) {
            String location = (String)args[0];
            ProgressBar bar;
            if ((bar = progressBarMap.get(location)) != null) {
                Float progress = (Float)args[1];
                bar.setProgress((int)(progress * 100));
            }
        } else if (id == FileLoader.FileDidFailedLoad) {
            String location = (String)args[0];
            if (loadingFile.containsKey(location)) {
                loadingFile.remove(location);
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
            }
        } else if (id == FileLoader.FileDidLoaded) {
            String location = (String)args[0];
            if (loadingFile.containsKey(location)) {
                loadingFile.remove(location);
                if (animationInProgress) {
                    invalidateAfterAnimation = true;
                } else {
                    if (chatListView != null) {
                        updateVisibleRows();
                    }
                }
            }
        } else if (id == FileLoader.FileLoadProgressChanged) {
            String location = (String)args[0];
            ArrayList<ProgressBar> arr = loadingFile.get(location);
            if (arr != null) {
                Float progress = (Float)args[1];
                for (ProgressBar bar : arr) {
                    bar.setProgress((int)(progress * 100));
                }
            }
        } else if (id == MessagesController.contactsDidLoaded) {
            updateContactStatus();
            updateSubtitle();
        } else if (id == MessagesController.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateContactStatus();
                updateSecretStatus();
            }
        } else if (id == MessagesController.messagesReadedEncrypted) {
            int encId = (Integer)args[0];
            if (currentEncryptedChat != null && currentEncryptedChat.id == encId) {
                int date = (Integer)args[1];
                boolean started = false;
                for (MessageObject obj : messages) {
                    if (!obj.messageOwner.out) {
                        continue;
                    } else if (obj.messageOwner.out && !obj.messageOwner.unread) {
                        break;
                    }
                    if (obj.messageOwner.date <= date) {
                        obj.messageOwner.unread = false;
                    }
                }
                if (chatListView != null) {
                    updateVisibleRows();
                }
            }
        }
    }

    private void updateContactStatus() {
        if (topPanel == null) {
            return;
        }
        if (currentUser == null) {
            topPanel.setVisibility(View.GONE);
        } else {
            if (!(currentEncryptedChat instanceof TLRPC.TL_encryptedChat) || currentUser.id == 333000 || (currentUser.phone != null && currentUser.phone.length() != 0 &&
                    MessagesController.Instance.contactsDict.get(currentUser.id) != null &&
                    (MessagesController.Instance.contactsDict.size() != 0 || !MessagesController.Instance.loadingContacts))) {
                topPanel.setVisibility(View.GONE);
            } else {
                topPanel.setVisibility(View.VISIBLE);
                topPanelText.setShadowLayer(1, 0, 1 * displayDensity, 0xff8797a3);
                if (isCustomTheme) {
                    topPlaneClose.setImageResource(R.drawable.ic_msg_btn_cross_custom);
                    topPanel.setBackgroundResource(R.drawable.top_pane_custom);
                } else {
                    topPlaneClose.setImageResource(R.drawable.ic_msg_btn_cross_custom);
                    topPanel.setBackgroundResource(R.drawable.top_pane);
                }
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    if (MessagesController.Instance.hidenAddToContacts.get(currentUser.id) != null) {
                        topPanel.setVisibility(View.INVISIBLE);
                    } else {
                        topPanelText.setText(getStringEntry(R.string.AddToContacts));
                        topPlaneClose.setVisibility(View.VISIBLE);
                        topPlaneClose.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MessagesController.Instance.hidenAddToContacts.put(currentUser.id, currentUser);
                                topPanel.setVisibility(View.GONE);
                            }
                        });
                        topPanel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ContactAddActivity fragment = new ContactAddActivity();
                                Bundle args = new Bundle();
                                args.putInt("user_id", currentUser.id);
                                fragment.setArguments(args);
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "add_contact_" + currentUser.id, false);
                            }
                        });
                    }
                } else {
                    if (MessagesController.Instance.hidenAddToContacts.get(currentUser.id) != null) {
                        topPanel.setVisibility(View.INVISIBLE);
                    } else {
                        topPanelText.setText(getStringEntry(R.string.ShareMyContactInfo));
                        topPlaneClose.setVisibility(View.GONE);
                        topPanel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MessagesController.Instance.hidenAddToContacts.put(currentUser.id, currentUser);
                                topPanel.setVisibility(View.GONE);
                                MessagesController.Instance.sendMessage(UserConfig.currentUser, dialog_id);
                                chatListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -10000 - chatListView.getPaddingTop());
                                    }
                                });
                            }
                        });
                    }
                }
            }
        }
    }

    private void createEmojiPopup() {
        emojiView = new EmojiView(parentActivity);
        emojiView.setListener(new EmojiView.Listener() {
            public void onBackspace() {
                messsageEditText.dispatchKeyEvent(new KeyEvent(0, 67));
            }

            public void onEmojiSelected(String paramAnonymousString) {
                int i = messsageEditText.getSelectionEnd();
                CharSequence localCharSequence = Emoji.replaceEmoji(paramAnonymousString);
                messsageEditText.setText(messsageEditText.getText().insert(i, localCharSequence));
                int j = i + localCharSequence.length();
                messsageEditText.setSelection(j, j);
            }
        });
        emojiPopup = new PopupWindow(emojiView);
    }

    private void showEmojiPopup(boolean show) {
        if (parentActivity == null) {
            return;
        }
        InputMethodManager localInputMethodManager = (InputMethodManager)parentActivity.getSystemService("input_method");
        if (show) {
            if (emojiPopup == null) {
                createEmojiPopup();
            }
            int currentHeight;
            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();
            if (keyboardHeight <= 0) {
                keyboardHeight = parentActivity.getSharedPreferences("emoji", 0).getInt("kbd_height", Emoji.scale(200.0f));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = parentActivity.getSharedPreferences("emoji", 0).getInt("kbd_height_land3", Emoji.scale(200.0f));
            }
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                currentHeight = keyboardHeightLand;
            } else {
                currentHeight = keyboardHeight;
            }
            emojiPopup.setHeight(View.MeasureSpec.makeMeasureSpec(currentHeight, View.MeasureSpec.EXACTLY));
            emojiPopup.setWidth(View.MeasureSpec.makeMeasureSpec(contentView.getWidth(), View.MeasureSpec.EXACTLY));

            emojiPopup.showAtLocation(parentActivity.getWindow().getDecorView(), 83, 0, 0);
            if (!keyboardVisible) {
                contentView.setPadding(0, 0, 0, currentHeight);
                emojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
                return;
            }
            emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
            return;
        }
        if (emojiButton != null) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        }
        if (emojiPopup != null) {
            emojiPopup.dismiss();
        }
        if (contentView != null) {
            contentView.post(new Runnable() {
                public void run() {
                    if (contentView != null) {
                        contentView.setPadding(0, 0, 0, 0);
                    }
                }
            });
        }
    }

    public void hideEmojiPopup() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false);
        }
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        updateSubtitle();
        ((ApplicationActivity)parentActivity).fixBackButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (!firstStart && chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        MessagesController.Instance.openned_dialog_id = dialog_id;
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && unreadMessageObject != null) {
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), -chatListView.getPaddingTop() - (int)(7 * displayDensity));
                }
            } else {
                if (chatListView != null) {
                    chatListView.setSelection(messages.size() + 1);
                }
            }
            scrollToTopUnReadOnResume = false;
            scrollToTopOnResume = false;
        }
        firstStart = false;
        swipeOpening = false;
        if (emojiView != null) {
            emojiView.loadRecents();
        }
        paused = false;
        if (readWhenResume && !messages.isEmpty()) {
            readWhenResume = false;
            MessagesController.Instance.markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, readWithMid, 0, readWithDate, true);
        }
        if (getActivity() == null) {
            return;
        }
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
        fixLayout();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        String lastMessageText = preferences.getString("dialog_" + dialog_id, null);
        if (lastMessageText != null) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove("dialog_" + dialog_id);
            editor.commit();
            ignoreTextChange = true;
            messsageEditText.setText(lastMessageText);
            ignoreTextChange = false;
        }
        if (messsageEditText != null) {
            messsageEditText.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (messsageEditText != null) {
                        messsageEditText.requestFocus();
                    }
                }
            }, 400);
        }
    }

    private void setTypingAnimation(boolean start) {
        TextView subtitle = (TextView)parentActivity.findViewById(R.id.action_bar_subtitle);
        if (subtitle == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_subtitle", "id", "android");
            subtitle = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (subtitle != null) {
            if (start) {
                try {
                    if (currentChat != null) {
                        subtitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.typing_dots_chat, 0, 0, 0);
                    } else {
                        subtitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.typing_dots, 0, 0, 0);
                    }
                    subtitle.setCompoundDrawablePadding((int)(4 * displayDensity));
                    AnimationDrawable mAnim = (AnimationDrawable)subtitle.getCompoundDrawables()[0];
                    mAnim.setAlpha(200);
                    mAnim.start();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            } else {
                subtitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
        hideEmojiPopup();
        paused = true;
        MessagesController.Instance.openned_dialog_id = 0;

        if (messsageEditText != null && messsageEditText.length() != 0) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("dialog_" + dialog_id, messsageEditText.getText().toString());
            editor.commit();
        }
        if (messsageEditText != null) {
            messsageEditText.clearFocus();
        }
    }

    private void fixLayout() {
        if (chatListView != null) {
            ViewTreeObserver obs = chatListView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (parentActivity == null) {
                        chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                        return false;
                    }
                    WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                    Display display = manager.getDefaultDisplay();
                    int rotation = display.getRotation();
                    int height;
                    int currentActionBarHeight = parentActivity.getSupportActionBar().getHeight();
                    float density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
                    if (currentActionBarHeight != 48 * density && currentActionBarHeight != 40 * density) {
                        height = currentActionBarHeight;
                    } else {
                        height = (int) (48.0f * density);
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            height = (int) (40.0f * density);
                        }
                    }

                    if (avatarImageView != null) {
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) avatarImageView.getLayoutParams();
                        params.width = height;
                        params.height = height;
                        avatarImageView.setLayoutParams(params);
                    }

                    chatListView.getViewTreeObserver().removeOnPreDrawListener(this);

                    if (currentEncryptedChat != null) {
                        TextView title = (TextView) parentActivity.findViewById(R.id.action_bar_title);
                        if (title == null) {
                            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
                            title = (TextView) parentActivity.findViewById(subtitleId);
                        }
                        if (title != null) {
                            title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_white, 0, 0, 0);
                            title.setCompoundDrawablePadding((int) (4 * displayDensity));
                        }
                    }

                    return false;
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();

        if (parentActivity != null) {
            Display display = parentActivity.getWindowManager().getDefaultDisplay();
            if(android.os.Build.VERSION.SDK_INT < 13) {
                displaySize.set(display.getWidth(), display.getHeight());
            } else {
                display.getSize(displaySize);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.chat_menu, menu);
        if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat) || currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left)) {
            SupportMenuItem item = (SupportMenuItem)menu.findItem(R.id.chat_menu_attach);
            item.setVisible(false);
        }

        SupportMenuItem avatarItem = (SupportMenuItem)menu.findItem(R.id.chat_menu_avatar);
        View avatarLayout = avatarItem.getActionView();
        avatarImageView = (BackupImageView)avatarLayout.findViewById(R.id.chat_avatar_image);

        avatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (parentActivity == null) {
                    return;
                }
                if (currentUser != null) {
                    UserProfileActivity fragment = new UserProfileActivity();
                    Bundle args = new Bundle();
                    args.putInt("user_id", currentUser.id);
                    if (currentEncryptedChat != null) {
                        args.putLong("dialog_id", dialog_id);
                    }
                    fragment.setArguments(args);
                    ((ApplicationActivity) parentActivity).presentFragment(fragment, "user_" + currentUser.id, swipeOpening);
                } else if (currentChat != null) {
                    if (info != null) {
                        if (info instanceof TLRPC.TL_chatParticipantsForbidden) {
                            return;
                        }
                        NotificationCenter.Instance.addToMemCache(5, info);
                    }
                    if (currentChat.participants_count == 0 || currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden) {
                        return;
                    }
                    ChatProfileActivity fragment = new ChatProfileActivity();
                    Bundle args = new Bundle();
                    args.putInt("chat_id", currentChat.id);
                    fragment.setArguments(args);
                    ((ApplicationActivity) parentActivity).presentFragment(fragment, "chat_" + currentChat.id, swipeOpening);
                }
            }
        });

        TLRPC.FileLocation photo = null;
        int placeHolderId = 0;
        if (currentUser != null) {
            if (currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }
            placeHolderId = Utilities.getUserAvatarForId(currentUser.id);
        } else if (currentChat != null) {
            if (currentChat.photo != null) {
                photo = currentChat.photo.photo_small;
            }
            placeHolderId = Utilities.getGroupAvatarForId(currentChat.id);
        }
        avatarImageView.setImage(photo, "50_50", placeHolderId);
    }

    private View getRowParentView(View v) {
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

    public void createMenu(View v, boolean single) {
        if (mActionMode != null || parentActivity == null || getActivity() == null || isFinish || swipeOpening) {
            return;
        }

        selectedMessagesCanCopyIds.clear();
        selectedObject = null;
        forwaringMessage = null;
        selectedMessagesIds.clear();
        View parentView = getRowParentView(v);
        if (parentView == null) {
            return;
        }
        ChatListRowHolderEx holder = (ChatListRowHolderEx)parentView.getTag();

        MessageObject message = holder.message;
        final int type = getMessageType(message);
        if (single || type < 2) {
            if (type >= 0) {
                selectedObject = message;
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                CharSequence[] items = null;

                TLRPC.User user = MessagesController.Instance.users.get(UserConfig.clientUserId);
                if (currentEncryptedChat == null) {
                    if (type == 0) {
                        items = new CharSequence[] {getStringEntry(R.string.Retry), getStringEntry(R.string.Delete)};
                    } else if (type == 1) {
                        items = new CharSequence[] {getStringEntry(R.string.Delete)};
                    } else if (type == 2) {
                        items = new CharSequence[] {getStringEntry(R.string.Forward), getStringEntry(R.string.Delete)};
                    } else if (type == 3) {
                        items = new CharSequence[] {getStringEntry(R.string.Forward), getStringEntry(R.string.Copy), getStringEntry(R.string.Delete)};
                    }
                } else {
                    if (type == 0) {
                        items = new CharSequence[] {getStringEntry(R.string.Retry), getStringEntry(R.string.Delete)};
                    } else if (type == 1) {
                        items = new CharSequence[] {getStringEntry(R.string.Delete)};
                    } else if (type == 2) {
                        items = new CharSequence[] {getStringEntry(R.string.Delete)};
                    } else if (type == 3) {
                        items = new CharSequence[] {getStringEntry(R.string.Copy), getStringEntry(R.string.Delete)};
                    }
                }

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
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
                        }
                    }
                });

                builder.setTitle(R.string.Message);
                visibleDialog = builder.show();
                visibleDialog.setCanceledOnTouchOutside(true);

                visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        visibleDialog = null;
                    }
                });
            }
            return;
        }
        addToSelectedMessages(message);
        mActionMode = parentActivity.startSupportActionMode(mActionModeCallback);
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void processSelectedOption(int option) {
        if (option == 0) {
            if (selectedObject != null && selectedObject.messageOwner.id < 0) {
                if (selectedObject.type == 0 || selectedObject.type == 1) {
                    if (selectedObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                        MessagesController.Instance.sendMessage(selectedObject, dialog_id);
                    } else {
                        MessagesController.Instance.sendMessage(selectedObject.messageOwner.message, dialog_id);
                    }
                } else if (selectedObject.type == 8 || selectedObject.type == 9) {
                    MessagesController.Instance.sendMessage(selectedObject, dialog_id);
                } else if (selectedObject.type == 4 || selectedObject.type == 5) {
                    MessagesController.Instance.sendMessage(selectedObject.messageOwner.media.geo.lat, selectedObject.messageOwner.media.geo._long, dialog_id);
                } else if (selectedObject.type == 2 || selectedObject.type == 3) {
                    if (selectedObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                        MessagesController.Instance.sendMessage(selectedObject, dialog_id);
                    } else {
                        TLRPC.TL_photo photo = (TLRPC.TL_photo)selectedObject.messageOwner.media.photo;
                        MessagesController.Instance.sendMessage(photo, dialog_id);
                    }
                } else if (selectedObject.type == 6 || selectedObject.type == 7) {
                    if (selectedObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                        MessagesController.Instance.sendMessage(selectedObject, dialog_id);
                    } else {
                        TLRPC.TL_video video = (TLRPC.TL_video)selectedObject.messageOwner.media.video;
                        video.path = selectedObject.messageOwner.attachPath;
                        MessagesController.Instance.sendMessage(video, dialog_id);
                    }
                } else if (selectedObject.type == 12 || selectedObject.type == 13) {
                    TLRPC.User user = MessagesController.Instance.users.get(selectedObject.messageOwner.media.user_id);
                    MessagesController.Instance.sendMessage(user, dialog_id);
                }
                ArrayList<Integer> arr = new ArrayList<Integer>();
                arr.add(selectedObject.messageOwner.id);
                MessagesController.Instance.deleteMessages(arr);
                chatListView.setSelection(messages.size() + 1);
            }
        } else if (option == 1) {
            if (selectedObject != null) {
                ArrayList<Integer> ids = new ArrayList<Integer>();
                ids.add(selectedObject.messageOwner.id);
                removeUnreadPlane(true);
                MessagesController.Instance.deleteMessages(ids);
                selectedObject = null;
            }
        } else if (option == 2) {
            if (selectedObject != null) {
                forwaringMessage = selectedObject;
                selectedObject = null;

                MessagesActivity fragment = new MessagesActivity();
                fragment.selectAlertString = R.string.ForwardMessagesTo;
                fragment.animationType = 1;
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putBoolean("serverOnly", true);
                fragment.setArguments(args);
                fragment.delegate = this;
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "select_chat", false);
            }
        } else if (option == 3) {
            if (selectedObject != null) {
                if(android.os.Build.VERSION.SDK_INT < 11) {
                    android.text.ClipboardManager clipboard = (android.text.ClipboardManager)parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setText(selectedObject.messageText);
                } else {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)parentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", selectedObject.messageText);
                    clipboard.setPrimaryClip(clip);
                }
                selectedObject = null;
            }
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity activity, long did) {
        if (dialog_id != 0 && (forwaringMessage != null || !selectedMessagesIds.isEmpty())) {
            if (did != dialog_id) {
                int lower_part = (int)did;
                if (lower_part != 0) {
                    activity.removeSelfFromStack();
                    ChatActivity fragment = new ChatActivity();
                    Bundle bundle = new Bundle();
                    if (lower_part > 0) {
                        bundle.putInt("user_id", lower_part);
                        fragment.setArguments(bundle);
                        fragment.scrollToTopOnResume = true;
                        ActionBarActivity act = (ActionBarActivity)getActivity();
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                    } else if (lower_part < 0) {
                        bundle.putInt("chat_id", -lower_part);
                        fragment.setArguments(bundle);
                        fragment.scrollToTopOnResume = true;
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "chat" + Math.random(), false);
                    }
                    removeSelfFromStack();
                    if (forwaringMessage != null) {
                        MessagesController.Instance.sendMessage(forwaringMessage, did);
                        forwaringMessage = null;
                    } else {
                        ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
                        Collections.sort(ids);
                        for (Integer id : ids) {
                            MessagesController.Instance.sendMessage(selectedMessagesIds.get(id), did);
                        }
                        selectedMessagesIds.clear();
                    }
                } else {
                    activity.finishFragment();
                }
            } else {
                activity.finishFragment();
                if (forwaringMessage != null) {
                    MessagesController.Instance.sendMessage(forwaringMessage, did);
                    forwaringMessage = null;
                } else {
                    ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
                    Collections.sort(ids, new Comparator<Integer>() {
                        @Override
                        public int compare(Integer lhs, Integer rhs) {
                            return lhs.compareTo(rhs);
                        }
                    });
                    for (Integer id : ids) {
                        MessagesController.Instance.sendMessage(selectedMessagesIds.get(id), did);
                    }
                    selectedMessagesIds.clear();
                }
                chatListView.setSelection(messages.size() + 1);
                scrollToTopOnResume = true;
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            hideEmojiPopup();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
            case R.id.attach_photo: {
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
                break;
            }
            case R.id.attach_gallery: {
                try {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    startActivityForResult(photoPickerIntent, 1);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                break;
            }
            case R.id.attach_video: {
                try {
                    Intent pickIntent = new Intent();
                    pickIntent.setType("video/*");
                    pickIntent.setAction(Intent.ACTION_GET_CONTENT);
                    pickIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1024 * 1024 * 1000);
                    Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                    File video = Utilities.generateVideoPath();
                    if (video != null) {
                        if(android.os.Build.VERSION.SDK_INT > 10) {
                            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                        }
                        takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1024 * 1024 * 1000);
                        currentPicturePath = video.getAbsolutePath();
                    }
                    Intent chooserIntent = Intent.createChooser(pickIntent, "");
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takeVideoIntent });

                    startActivityForResult(chooserIntent, 2);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                break;
            }
            case R.id.attach_location: {
                if (!isGoogleMapsInstalled()) {
                    return true;
                }
                LocationActivity fragment = new LocationActivity();
                ((ApplicationActivity)parentActivity).presentFragment(fragment, "location", false);
                break;
            }
        }
        return true;
    }

    public boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );
            return true;
        } catch(PackageManager.NameNotFoundException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setMessage("Install Google Maps?");
            builder.setCancelable(true);
            builder.setPositiveButton(getStringEntry(R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps"));
                        startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            visibleDialog = builder.create();
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.show();
            return false;
        }
    }

    private boolean spanClicked(ListView list, View view, int textViewId) {
        final TextView widget = (TextView)view.findViewById(textViewId);
        if (widget == null) {
            return false;
        }
        try {
            list.offsetRectIntoDescendantCoords(widget, mLastTouch);
            int x = mLastTouch.right;
            int y = mLastTouch.bottom;

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();
            x += widget.getScrollX();
            y += widget.getScrollY();

            final Layout layout = widget.getLayout();
            if (layout == null) {
                return false;
            }
            final int line = layout.getLineForVertical(y);
            final int off = layout.getOffsetForHorizontal(line, x);

            final float left = layout.getLineLeft(line);
            if (left > x || left + layout.getLineWidth(line) < x) {
                return false;
            }

            final Editable buffer = new SpannableStringBuilder(widget.getText());
            if (buffer == null) {
                return false;
            }
            final ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

            if (link.length == 0) {
                return false;
            }

            link[0].onClick(widget);
            return true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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
                if (mActionMode != null) {
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
                int messageType = holder.message.type;
                if (!disableSelection) {
                    if (messageType == 2 || messageType == 4 || messageType == 6) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_photo_states);
                    } else if (messageType == 3 || messageType == 5 || messageType == 7) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_photo_states);
                    } else if (messageType == 0 || messageType == 8) {
                        holder.messageLayout.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                        holder.messageLayout.setPadding((int)(11 * displayDensity), (int)(7 * displayDensity), (int)(18 * displayDensity), 0);
                    } else if (messageType == 1 || messageType == 9) {
                        holder.messageLayout.setBackgroundResource(R.drawable.chat_incoming_text_states);
                        holder.messageLayout.setPadding((int)(19 * displayDensity), (int)(7 * displayDensity), (int)(9 * displayDensity), 0);
                    } else if (messageType == 12) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                        holder.chatBubbleView.setPadding((int)(6 * displayDensity), (int)(6 * displayDensity), (int)(18 * displayDensity), 0);
                    } else if (messageType == 13) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_text_states);
                        holder.chatBubbleView.setPadding((int)(15 * displayDensity), (int)(6 * displayDensity), (int)(9 * displayDensity), 0);
                    }
                } else {
                    if (messageType == 2 || messageType == 4 || messageType == 6) {
                        if (selected) {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_photo_selected);
                        } else {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_photo);
                        }
                    } else if (messageType == 3 || messageType == 5 || messageType == 7) {
                        if (selected) {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_photo_selected);
                        } else {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_photo);
                        }
                    } else if (messageType == 0 || messageType == 8) {
                        if (selected) {
                            holder.messageLayout.setBackgroundResource(R.drawable.msg_out_selected);
                        } else {
                            holder.messageLayout.setBackgroundResource(R.drawable.msg_out);
                        }
                        holder.messageLayout.setPadding((int)(11 * displayDensity), (int)(7 * displayDensity), (int)(18 * displayDensity), 0);
                    } else if (messageType == 1 || messageType == 9) {
                        if (selected) {
                            holder.messageLayout.setBackgroundResource(R.drawable.msg_in_selected);
                        } else {
                            holder.messageLayout.setBackgroundResource(R.drawable.msg_in);
                        }
                        holder.messageLayout.setPadding((int)(19 * displayDensity), (int)(7 * displayDensity), (int)(9 * displayDensity), 0);
                    } else if (messageType == 12) {
                        if (selected) {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_selected);
                        } else {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out);
                        }
                        holder.chatBubbleView.setPadding((int)(6 * displayDensity), (int)(6 * displayDensity), (int)(18 * displayDensity), 0);
                    } else if (messageType == 13) {
                        if (selected) {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_selected);
                        } else {
                            holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in);
                        }
                        holder.chatBubbleView.setPadding((int)(15 * displayDensity), (int)(6 * displayDensity), (int)(9 * displayDensity), 0);
                    }
                }
            }
        }
    }

    private class ChatAdapter extends BaseAdapter {

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
                    }
                    return view;
                }
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            int type = message.type;
            if (view == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (type == 0) {
                    view = li.inflate(R.layout.chat_outgoing_text_layout, viewGroup, false);
                } else if (type == 1) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_text_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_text_layout, viewGroup, false);
                    }
                } else if (type == 8) {
                    view = li.inflate(R.layout.chat_outgoing_forward_layout, viewGroup, false);
                } else if (type == 9) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_forward_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_forward_layout, viewGroup, false);
                    }
                } else if (type == 4) {
                    view = li.inflate(R.layout.chat_outgoing_location_layout, viewGroup, false);
                } else if (type == 5) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_location_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_location_layout, viewGroup, false);
                    }
                } else if (type == 2) {
                    view = li.inflate(R.layout.chat_outgoing_photo_layout, viewGroup, false);
                } else if (type == 3) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_photo_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_photo_layout, viewGroup, false);
                    }
                } else if (type == 6) {
                    view = li.inflate(R.layout.chat_outgoing_video_layout, viewGroup, false);
                } else if (type == 7) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_video_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_video_layout, viewGroup, false);
                    }
                } else if (type == 10) {
                    view = li.inflate(R.layout.chat_action_message_layout, viewGroup, false);
                } else if (type == 11) {
                    view = li.inflate(R.layout.chat_action_change_photo_layout, viewGroup, false);
                } else if (type == 12) {
                    view = li.inflate(R.layout.chat_outgoing_contact_layout, viewGroup, false);
                } else if (type == 13) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_contact_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_contact_layout, viewGroup, false);
                    }
                } else if (type == 15) {
                    view = li.inflate(R.layout.chat_unread_layout, viewGroup, false);
                }
            }

            ChatListRowHolderEx holder = (ChatListRowHolderEx)view.getTag();
            if (holder == null) {
                holder = new ChatListRowHolderEx(view);
                view.setTag(holder);
            }
            holder.message = message;

            boolean selected = false;
            boolean disableSelection = false;
            if (mActionMode != null) {
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
            int messageType = holder.message.type;
            if (!disableSelection) {
                if (messageType == 2 || messageType == 4 || messageType == 6) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_photo_states);
                } else if (messageType == 3 || messageType == 5 || messageType == 7) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_photo_states);
                } else if (messageType == 0 || messageType == 8) {
                    holder.messageLayout.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                    holder.messageLayout.setPadding((int)(11 * displayDensity), (int)(7 * displayDensity), (int)(18 * displayDensity), 0);
                } else if (messageType == 1 || messageType == 9) {
                    holder.messageLayout.setBackgroundResource(R.drawable.chat_incoming_text_states);
                    holder.messageLayout.setPadding((int)(19 * displayDensity), (int)(7 * displayDensity), (int)(9 * displayDensity), 0);
                } else if (messageType == 12) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                    holder.chatBubbleView.setPadding((int)(6 * displayDensity), (int)(6 * displayDensity), (int)(18 * displayDensity), 0);
                } else if (messageType == 13) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_text_states);
                    holder.chatBubbleView.setPadding((int)(15 * displayDensity), (int)(6 * displayDensity), (int)(9 * displayDensity), 0);
                }
            } else {
                if (messageType == 2 || messageType == 4 || messageType == 6) {
                    if (selected) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_photo_selected);
                    } else {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_photo);
                    }
                } else if (messageType == 3 || messageType == 5 || messageType == 7) {
                    if (selected) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_photo_selected);
                    } else {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_photo);
                    }
                } else if (messageType == 0 || messageType == 8) {
                    if (selected) {
                        holder.messageLayout.setBackgroundResource(R.drawable.msg_out_selected);
                    } else {
                        holder.messageLayout.setBackgroundResource(R.drawable.msg_out);
                    }
                    holder.messageLayout.setPadding((int)(11 * displayDensity), (int)(7 * displayDensity), (int)(18 * displayDensity), 0);
                } else if (messageType == 1 || messageType == 9) {
                    if (selected) {
                        holder.messageLayout.setBackgroundResource(R.drawable.msg_in_selected);
                    } else {
                        holder.messageLayout.setBackgroundResource(R.drawable.msg_in);
                    }
                    holder.messageLayout.setPadding((int)(19 * displayDensity), (int)(7 * displayDensity), (int)(9 * displayDensity), 0);
                } else if (messageType == 12) {
                    if (selected) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_selected);
                    } else {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out);
                    }
                    holder.chatBubbleView.setPadding((int)(6 * displayDensity), (int)(6 * displayDensity), (int)(18 * displayDensity), 0);
                } else if (messageType == 13) {
                    if (selected) {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_selected);
                    } else {
                        holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in);
                    }
                    holder.chatBubbleView.setPadding((int)(15 * displayDensity), (int)(6 * displayDensity), (int)(9 * displayDensity), 0);
                }
            }

            holder.update();

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            int offset = 1;
            if (!endReached && messages.size() != 0) {
                offset = 0;
                if (i == 0) {
                    return 14;
                }
            }
            if (!unread_end_reached && i == (messages.size() + 1 - offset)) {
                return 14;
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            return message.type;
        }

        @Override
        public int getViewTypeCount() {
            return 16;
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
        public MessageLayout messageLayout;
        public MessageActionLayout messageLayoutAction;
        public TextView forwardedUserText;
        public TextView foewardedUserName;
        public TextView timeTextView;
        public BackupImageView photoImage;
        public ImageView halfCheckImage;
        public ImageView checkImage;
        public TextView actionAttachButton;
        public TextView videoTimeText;
        public MessageObject message;
        public TextView phoneTextView;
        public BackupImageView contactAvatar;
        public View contactView;
        public ImageView addContactButton;
        public View addContactView;
        public View chatBubbleView;

        public ProgressBar actionProgress;
        public View actionView;
        public ImageView actionCancelButton;

        public void update() {
            TLRPC.User fromUser = MessagesController.Instance.users.get(message.messageOwner.from_id);

            int type = message.type;

            if (type == 0 || type == 1 || type == 8 || type == 9) {
                int width;
                if (currentChat != null && (type == 1 || type == 9)) {
                    width = displaySize.x - (int)(122 * displayDensity);
                } else {
                    width = displaySize.x - (int)(80 * displayDensity);
                }
                messageLayout.density = displayDensity;
                messageLayout.maxWidth = width;
                messageLayout.messageTextView.setText(message.messageText);
                messageLayout.messageTextView.setMaxWidth(width);
            }

            if (timeTextView != null) {
                timeTextView.setText(Utilities.formatterDay.format((long) (message.messageOwner.date) * 1000));
            }

            if (avatarImageView != null && fromUser != null) {
                TLRPC.FileLocation photo = null;
                if (fromUser.photo != null) {
                    photo = fromUser.photo.photo_small;
                }
                int placeHolderId = Utilities.getUserAvatarForId(fromUser.id);
                avatarImageView.setImage(photo, "50_50", placeHolderId);
            }

            if (type != 12 && type != 13 && nameTextView != null && fromUser != null) {
                nameTextView.setText(Utilities.formatName(fromUser.first_name, fromUser.last_name));
                nameTextView.setTextColor(Utilities.getColorForId(message.messageOwner.from_id));
            }

            if (type == 8 || type == 9) {
                TLRPC.User fwdUser = MessagesController.Instance.users.get(message.messageOwner.fwd_from_id);
                if (fwdUser != null) {
                    forwardedUserText.setText(Html.fromHtml(getStringEntry(R.string.From) + " <b>" + Utilities.formatName(fwdUser.first_name, fwdUser.last_name) + "</b>"));
                }
            } else if (type == 2 || type == 3 || type == 6 || type == 7) {
                int width = (int)(Math.min(displaySize.x, displaySize.y) / 2.5f);
                /*PhotoObject photo = PhotoObject.getClosestImageWithSize(message.photoThumbs, width, width + 100);*/
                PhotoObject photo = PhotoObject.getClosestImageWithSize(message.photoThumbs, width, width + 100);

                if (photo != null) {
                    float scale = (float)photo.photoOwner.w / (float)width;

                    if (scale < 1 && photo.photoOwner.w * scale < 100 * displayDensity) {
                        scale = photo.photoOwner.w / (100 * displayDensity);
                    }
                    int w = (int)(photo.photoOwner.w / scale);
                    int h = (int)(photo.photoOwner.h / scale);
                    if (h > 160 * displayDensity) {
                        float scale2 = h;
                        h = (int)(160 * displayDensity);
                        scale2 /= h;
                        w = (int)(w / scale2);
                    } else if (h < 90 * displayDensity) {
                        h = (int)(90 * displayDensity);
                        float hScale = (float)photo.photoOwner.h / h;
                        if (photo.photoOwner.w / hScale < width) {
                            w = (int)(photo.photoOwner.w / hScale);
                        }
                    }

                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)photoImage.getLayoutParams();
                    params.width = w;
                    params.height = h;
                    photoImage.setLayoutParams(params);

                    if (photo.image != null) {
                        photoImage.setImageBitmap(photo.image);
                    } else {
                        if (message.imagePreview != null) {
                            photoImage.setImage(photo.photoOwner.location, String.format(Locale.US, "%d_%d", (int)(w / displayDensity), (int)(h / displayDensity)), message.imagePreview);
                        } else {
                            photoImage.setImage(photo.photoOwner.location, String.format(Locale.US, "%d_%d", (int)(w / displayDensity), (int)(h / displayDensity)), message.messageOwner.out ? R.drawable.photo_placeholder_out : R.drawable.photo_placeholder_in);
                        }
                    }
                }

                if ((type == 6 || type == 7) && videoTimeText != null) {
                    int duration = message.messageOwner.media.video.duration;
                    int minutes = duration / 60;
                    int seconds = duration - minutes * 60;
                    videoTimeText.setText(String.format("%d:%02d", minutes, seconds));
                }
            } else if (type == 4 || type == 5) {
                double lat = message.messageOwner.media.geo.lat;
                double lon = message.messageOwner.media.geo._long;
                String url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=13&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int)Math.ceil(displayDensity)), lat, lon);
                photoImage.setImage(url, null, message.messageOwner.out ? R.drawable.photo_placeholder_out : R.drawable.photo_placeholder_in);
            } else if (type == 11 || type == 10) {
                int width = (int)(displaySize.x - (30 * displayDensity));
                messageLayoutAction.density = displayDensity;
                messageTextView.setText(message.messageText);
                messageTextView.setMaxWidth(width);

                if (type == 11) {
                    if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                        photoImage.setImage(message.messageOwner.action.newUserPhoto.photo_small, "50_50", Utilities.getUserAvatarForId(currentUser.id));
                    } else {
                        PhotoObject photo = PhotoObject.getClosestImageWithSize(message.photoThumbs, (int)(64 * displayDensity), (int)(64 * displayDensity));
                        if (photo.image != null) {
                            photoImage.setImageBitmap(photo.image);
                        } else {
                            photoImage.setImage(photo.photoOwner.location, "50_50", Utilities.getGroupAvatarForId(currentChat.id));
                        }
                    }
                }
            } else if (type == 12 || type == 13) {
                TLRPC.User contactUser = MessagesController.Instance.users.get(message.messageOwner.media.user_id);
                if (contactUser != null) {
                    nameTextView.setText(Utilities.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(Utilities.getColorForId(contactUser.id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (!phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.Instance.format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    TLRPC.FileLocation photo = null;
                    if (contactUser.photo != null) {
                        photo = contactUser.photo.photo_small;
                    }
                    int placeHolderId = Utilities.getUserAvatarForId(contactUser.id);
                    contactAvatar.setImage(photo, "50_50", placeHolderId);
                    if (contactUser.id != UserConfig.clientUserId && MessagesController.Instance.contactsDict.get(contactUser.id) == null) {
                        addContactView.setVisibility(View.VISIBLE);
                    } else {
                        addContactView.setVisibility(View.GONE);
                    }
                } else {
                    nameTextView.setText(Utilities.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(Utilities.getColorForId(message.messageOwner.media.user_id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (!phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.Instance.format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    contactAvatar.setImageResource(Utilities.getUserAvatarForId(message.messageOwner.media.user_id));
                    addContactView.setVisibility(View.GONE);
                }
            } else if (type == 15) {
                if (unread_to_load == 1) {
                    messageTextView.setText(String.format(getStringEntry(R.string.OneNewMessage), unread_to_load));
                } else {
                    messageTextView.setText(String.format(getStringEntry(R.string.FewNewMessages), unread_to_load));
                }
            }

            if (message.messageOwner.id < 0 && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENT) {
                if (MessagesController.Instance.sendingMessages.get(message.messageOwner.id) == null) {
                    message.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SEND_ERROR;
                }
            }

            if (message.messageOwner.from_id == UserConfig.clientUserId) {
                if (halfCheckImage != null) {
                    if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                        checkImage.setVisibility(View.INVISIBLE);
                        if (type == 2 || type == 6 || type == 4) {
                            halfCheckImage.setImageResource(R.drawable.msg_clock_photo);
                        } else {
                            halfCheckImage.setImageResource(R.drawable.msg_clock);
                        }
                        halfCheckImage.setVisibility(View.VISIBLE);
                        if (actionView != null) {
                            actionView.setVisibility(View.VISIBLE);
                            Float progress = FileLoader.Instance.fileProgresses.get(message.messageOwner.attachPath);
                            if (progress != null) {
                                actionProgress.setProgress((int)(progress * 100));
                            } else {
                                actionProgress.setProgress(0);
                            }
                            progressByTag.put((Integer)actionProgress.getTag(), message.messageOwner.attachPath);
                            progressBarMap.put(message.messageOwner.attachPath, actionProgress);
                        }
                        if (actionAttachButton != null) {
                            actionAttachButton.setVisibility(View.GONE);
                        }
                    } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                        halfCheckImage.setVisibility(View.VISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_warning);
                        if (checkImage != null) {
                            checkImage.setVisibility(View.INVISIBLE);
                        }
                        if (actionView != null) {
                            actionView.setVisibility(View.GONE);
                        }
                        if (actionAttachButton != null) {
                            actionAttachButton.setVisibility(View.GONE);
                        }
                    } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENT) {
                        if (!message.messageOwner.unread) {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.VISIBLE);
                            if (type == 2 || type == 6 || type == 4) {
                                halfCheckImage.setImageResource(R.drawable.msg_halfcheck_w);
                            } else {
                                halfCheckImage.setImageResource(R.drawable.msg_halfcheck);
                            }
                        } else {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.INVISIBLE);
                            if (type == 2 || type == 6 || type == 4) {
                                halfCheckImage.setImageResource(R.drawable.msg_check_w);
                            } else {
                                halfCheckImage.setImageResource(R.drawable.msg_check);
                            }
                        }
                        if (actionView != null) {
                            actionView.setVisibility(View.GONE);
                        }
                        if (actionAttachButton != null) {
                            actionAttachButton.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
            if (message.type == 6 || message.type == 7) {
                Integer tag = (Integer)actionProgress.getTag();
                String file = progressByTag.get(tag);
                if (file != null) {
                    removeFromloadingFile(file, actionProgress);
                }
                if (message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENDING && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                    if (file != null) {
                        progressBarMap.remove(file);
                    }
                    String fileName = message.messageOwner.media.video.dc_id + "_" + message.messageOwner.media.video.id + ".mp4";
                    boolean load = false;
                    if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                        File f = new File(message.messageOwner.attachPath);
                        if (f.exists()) {
                            actionAttachButton.setVisibility(View.VISIBLE);
                            actionView.setVisibility(View.GONE);
                            actionAttachButton.setText(getStringEntry(R.string.ViewVideo));
                        } else {
                            load = true;
                        }
                    } else {
                        File cacheFile = new File(Utilities.getCacheDir(), fileName);
                        if (cacheFile.exists()) {
                            actionAttachButton.setVisibility(View.VISIBLE);
                            actionView.setVisibility(View.GONE);
                            actionAttachButton.setText(getStringEntry(R.string.ViewVideo));
                        } else {
                            load = true;
                        }
                    }
                    if (load) {
                        Float progress = FileLoader.Instance.fileProgresses.get(fileName);
                        if (loadingFile.containsKey(fileName) || progress != null) {
                            if (progress != null) {
                                actionProgress.setProgress((int)(progress * 100));
                            } else {
                                actionProgress.setProgress(0);
                            }
                            progressByTag.put((Integer)actionProgress.getTag(), fileName);
                            addToLoadingFile(fileName, actionProgress);
                            actionView.setVisibility(View.VISIBLE);
                            actionAttachButton.setVisibility(View.GONE);
                        } else {
                            actionView.setVisibility(View.GONE);
                            actionAttachButton.setVisibility(View.VISIBLE);
                            actionAttachButton.setText(String.format("%s %.1f MB", getStringEntry(R.string.DOWNLOAD), message.messageOwner.media.video.size / 1024.0f / 1024.0f));
                        }
                    }
                }
            }
        }

        public ChatListRowHolderEx(View view) {
            avatarImageView = (BackupImageView)view.findViewById(R.id.chat_group_avatar_image);
            nameTextView = (TextView)view.findViewById(R.id.chat_user_group_name);
            messageLayout = (MessageLayout)view.findViewById(R.id.message_layout);
            messageLayoutAction = (MessageActionLayout)view.findViewById(R.id.message_action_layout);
            forwardedUserText = (TextView)view.findViewById(R.id.chat_text_forward_name);
            foewardedUserName = (TextView)view.findViewById(R.id.chat_text_forward_text);
            timeTextView = (TextView)view.findViewById(R.id.chat_time_text);
            photoImage = (BackupImageView)view.findViewById(R.id.chat_photo_image);
            halfCheckImage = (ImageView)view.findViewById(R.id.chat_row_halfcheck);
            checkImage = (ImageView)view.findViewById(R.id.chat_row_check);
            actionAttachButton = (TextView)view.findViewById(R.id.chat_view_action_button);
            messageTextView = (TextView)view.findViewById(R.id.chat_message_text);
            videoTimeText = (TextView)view.findViewById(R.id.chat_video_time);
            actionView = view.findViewById(R.id.chat_view_action_layout);
            actionProgress = (ProgressBar)view.findViewById(R.id.chat_view_action_progress);
            actionCancelButton = (ImageView)view.findViewById(R.id.chat_view_action_cancel_button);
            phoneTextView = (TextView)view.findViewById(R.id.phone_text_view);
            contactAvatar = (BackupImageView)view.findViewById(R.id.contact_avatar);
            contactView = view.findViewById(R.id.shared_layout);
            addContactButton = (ImageView)view.findViewById(R.id.add_contact_button);
            addContactView = view.findViewById(R.id.add_contact_view);
            chatBubbleView = view.findViewById(R.id.chat_bubble_layout);
            if (messageTextView != null) {
                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            }

            if (actionProgress != null) {
                actionProgress.setTag(progressTag);
                progressTag++;
            }

            if (actionView != null) {
                if (isCustomTheme) {
                    actionView.setBackgroundResource(R.drawable.system_black);
                } else {
                    actionView.setBackgroundResource(R.drawable.system_blue);
                }
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
                        if (mActionMode != null) {
                            processRowSelect(view);
                            return;
                        }
                        ContactAddActivity fragment = new ContactAddActivity();
                        Bundle args = new Bundle();
                        args.putInt("user_id", message.messageOwner.media.user_id);
                        args.putString("phone", message.messageOwner.media.phone_number);
                        fragment.setArguments(args);
                        ((ApplicationActivity)parentActivity).presentFragment(fragment, "add_contact_" + message.messageOwner.media.user_id, false);
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
                        if (mActionMode != null) {
                            processRowSelect(view);
                            return;
                        }
                        if (message.messageOwner.media.user_id != UserConfig.clientUserId) {
                            UserProfileActivity fragment = new UserProfileActivity();
                            Bundle args = new Bundle();
                            args.putInt("user_id", message.messageOwner.media.user_id);
                            fragment.setArguments(args);
                            ((ApplicationActivity)parentActivity).presentFragment(fragment, "user_" + message.messageOwner.media.user_id, false);
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

            if (actionAttachButton != null) {
                actionAttachButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mActionMode != null) {
                            processRowSelect(view);
                            return;
                        }
                        if (message != null) {
                            if (message.type == 4 || message.type == 5) {
                                if (!isGoogleMapsInstalled()) {
                                    return;
                                }
                                NotificationCenter.Instance.addToMemCache(0, message);
                                LocationActivity fragment = new LocationActivity();
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "location_view", false);
                            } else if (message.type == 2 || message.type == 3) {
                                NotificationCenter.Instance.addToMemCache(51, message);
                                Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                                startActivity(intent);
                            } else if (message.type == 6 || message.type == 7) {
                                boolean loadFile = false;
                                String fileName = message.messageOwner.media.video.dc_id + "_" + message.messageOwner.media.video.id + ".mp4";
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    File f = new File(message.messageOwner.attachPath);
                                    if (f.exists()) {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW);
                                            intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                            startActivity(intent);
                                        } catch (Exception e) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                            builder.setTitle(R.string.AppName);
                                            builder.setMessage(R.string.NoPlayerInstalled);
                                            visibleDialog = builder.show();
                                            visibleDialog.setCanceledOnTouchOutside(true);
                                            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                @Override
                                                public void onDismiss(DialogInterface dialog) {
                                                    visibleDialog = null;
                                                }
                                            });
                                        }
                                    } else {
                                        loadFile = true;
                                    }
                                } else {
                                    File cacheFile = new File(Utilities.getCacheDir(), fileName);
                                    if (cacheFile.exists()) {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW);
                                            intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
                                            startActivity(intent);
                                        } catch (Exception e) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                            builder.setTitle(R.string.AppName);
                                            builder.setMessage(R.string.NoPlayerInstalled);
                                            visibleDialog = builder.show();
                                            visibleDialog.setCanceledOnTouchOutside(true);

                                            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                @Override
                                                public void onDismiss(DialogInterface dialog) {
                                                    visibleDialog = null;
                                                }
                                            });
                                        }
                                    } else {
                                        loadFile = true;
                                    }
                                }
                                if (loadFile) {
                                    if (!loadingFile.containsKey(fileName)) {
                                        progressByTag.put((Integer)actionProgress.getTag(), fileName);
                                        addToLoadingFile(fileName, actionProgress);
                                        FileLoader.Instance.loadFile(message.messageOwner.media.video, null);
                                        updateVisibleRows();
                                    }
                                }
                            }
                        }
                    }
                });
            }

            if (avatarImageView != null) {
                avatarImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mActionMode != null) {
                            processRowSelect(view);
                            return;
                        }
                        if (message != null) {
                            UserProfileActivity fragment = new UserProfileActivity();
                            Bundle args = new Bundle();
                            args.putInt("user_id", message.messageOwner.from_id);
                            fragment.setArguments(args);
                            ((ApplicationActivity)parentActivity).presentFragment(fragment, "user_" + message.messageOwner.from_id, false);
                        }
                    }
                });
            }

            if (actionCancelButton != null) {
                actionCancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (message != null) {
                            Integer tag = (Integer)actionProgress.getTag();
                            if (message.messageOwner.send_state != 0) {
                                MessagesController.Instance.cancelSendingMessage(message);
                                String file = progressByTag.get(tag);
                                if (file != null) {
                                    progressBarMap.remove(file);
                                }
                            } else if (message.type == 6 || message.type == 7) {
                                String file = progressByTag.get(tag);
                                if (file != null) {
                                    loadingFile.remove(file);
                                    FileLoader.Instance.cancelLoadFile(message.messageOwner.media.video, null);
                                    updateVisibleRows();
                                }
                            }
                        }
                    }
                });
            }

            if (photoImage != null) {
                photoImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mActionMode != null) {
                            processRowSelect(view);
                            return;
                        }
                        if (message != null) {
                            if (message.type == 4 || message.type == 5) {
                                if (!isGoogleMapsInstalled()) {
                                    return;
                                }
                                NotificationCenter.Instance.addToMemCache(0, message);
                                LocationActivity fragment = new LocationActivity();
                                ((ApplicationActivity)parentActivity).presentFragment(fragment, "location_view", false);
                            } else if (message.type == 2 || message.type == 3) {
                                NotificationCenter.Instance.addToMemCache(51, message);
                                Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                                startActivity(intent);
                            } else if (message.type == 11) {
                                NotificationCenter.Instance.addToMemCache(51, message);
                                Intent intent = new Intent(parentActivity, GalleryImageViewer.class);
                                startActivity(intent);
                            } else if (message.type == 6 || message.type == 7) {
                                boolean loadFile = false;
                                String fileName = message.messageOwner.media.video.dc_id + "_" + message.messageOwner.media.video.id + ".mp4";
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    File f = new File(message.messageOwner.attachPath);
                                    if (f.exists()) {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW);
                                            intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                            startActivity(intent);
                                        } catch (Exception e) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                            builder.setTitle(R.string.AppName);
                                            builder.setMessage(R.string.NoPlayerInstalled);
                                            visibleDialog = builder.show();
                                            visibleDialog.setCanceledOnTouchOutside(true);

                                            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                @Override
                                                public void onDismiss(DialogInterface dialog) {
                                                    visibleDialog = null;
                                                }
                                            });
                                        }
                                    } else {
                                        loadFile = true;
                                    }
                                } else {
                                    File cacheFile = new File(Utilities.getCacheDir(), fileName);
                                    if (cacheFile.exists()) {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW);
                                            intent.setDataAndType(Uri.fromFile(cacheFile), "video/*");
                                            startActivity(intent);
                                        } catch (Exception e) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                                            builder.setTitle(R.string.AppName);
                                            builder.setMessage(R.string.NoPlayerInstalled);
                                            visibleDialog = builder.show();
                                            visibleDialog.setCanceledOnTouchOutside(true);

                                            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                @Override
                                                public void onDismiss(DialogInterface dialog) {
                                                    visibleDialog = null;
                                                }
                                            });
                                        }
                                    } else {
                                        loadFile = true;
                                    }
                                }
                                if (loadFile) {
                                    if (!loadingFile.containsKey(fileName)) {
                                        progressByTag.put((Integer)actionProgress.getTag(), fileName);
                                        addToLoadingFile(fileName, actionProgress);
                                        FileLoader.Instance.loadFile(message.messageOwner.media.video, null);
                                        updateVisibleRows();
                                    }
                                }
                            }
                        }
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

            if (forwardedUserText != null) {
                forwardedUserText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        TLRPC.User fwdUser = MessagesController.Instance.users.get(message.messageOwner.fwd_from_id);
                        if (fwdUser != null && fwdUser.id != UserConfig.clientUserId) {
                            UserProfileActivity fragment = new UserProfileActivity();
                            Bundle args = new Bundle();
                            args.putInt("user_id", fwdUser.id);
                            fragment.setArguments(args);
                            ((ApplicationActivity)parentActivity).presentFragment(fragment, "user_" + fwdUser.id, false);
                        }
                    }
                });
            }
        }
    }
}
