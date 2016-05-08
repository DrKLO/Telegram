/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.query.BotQuery;
import org.telegram.messenger.query.MessagesSearchQuery;
import org.telegram.messenger.query.MessagesQuery;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Adapters.StickersAdapter;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.ui.Cells.BotSwitchCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatBaseCell;
import org.telegram.ui.Cells.ChatLoadingCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatUnreadCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.BotHelpCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.ChatAttachView;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.PlayerView;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.WebFrameLayout;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;

@SuppressWarnings("unchecked")
public class ChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate,
        PhotoViewer.PhotoViewerProvider {

    protected TLRPC.Chat currentChat;
    protected TLRPC.User currentUser;
    protected TLRPC.EncryptedChat currentEncryptedChat;
    private boolean userBlocked = false;

    private ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();

    private Dialog closeChatDialog;
    private FrameLayout progressView;
    private FrameLayout bottomOverlay;
    protected ChatActivityEnterView chatActivityEnterView;
    private View timeItem2;
    private ActionBarMenuItem menuItem;
    private ActionBarMenuItem attachItem;
    private ActionBarMenuItem headerItem;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem searchUpItem;
    private ActionBarMenuItem searchDownItem;
    private TextView addContactItem;
    private RecyclerListView chatListView;
    private LinearLayoutManager chatLayoutManager;
    private ChatActivityAdapter chatAdapter;
    private TextView bottomOverlayChatText;
    private FrameLayout bottomOverlayChat;
    private FrameLayout emptyViewContainer;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ChatAvatarContainer avatarContainer;
    private TextView bottomOverlayText;
    private TextView secretViewStatusTextView;
    private NumberTextView selectedMessagesCountTextView;
    private TextView actionModeTextView;
    private RecyclerListView stickersListView;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private RecyclerListView.OnItemClickListener mentionsOnItemClickListener;
    private StickersAdapter stickersAdapter;
    private FrameLayout stickersPanel;
    private TextView muteItem;
    private ImageView pagedownButton;
    private BackupImageView replyImageView;
    private TextView replyNameTextView;
    private TextView replyObjectTextView;
    private ImageView replyIconImageView;
    private MentionsAdapter mentionsAdapter;
    private BotSwitchCell botSwitchCell;
    private View botSwitchShadow;
    private FrameLayout mentionContainer;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private AnimatorSetProxy mentionListAnimation;
    private ChatAttachView chatAttachView;
    private BottomSheet chatAttachViewSheet;
    private LinearLayout reportSpamView;
    private AnimatorSetProxy reportSpamViewAnimator;
    private TextView addToContactsButton;
    private TextView reportSpamButton;
    private FrameLayout reportSpamContainer;
    private PlayerView playerView;
    private TextView gifHintTextView;
    private View emojiButtonRed;
    private FrameLayout pinnedMessageView;
    private AnimatorSetProxy pinnedMessageViewAnimator;
    private SimpleTextView pinnedMessageNameTextView;
    private SimpleTextView pinnedMessageTextView;
    private FrameLayout alertView;
    private Runnable hideAlertViewRunnable;
    private TextView alertNameTextView;
    private TextView alertTextView;
    private AnimatorSetProxy alertViewAnimator;

    private boolean mentionListViewIgnoreLayout;
    private int mentionListViewScrollOffsetY;
    private int mentionListViewLastViewTop;
    private int mentionListViewLastViewPosition;
    private boolean mentionListViewIsScrolling;

    private MessageObject pinnedMessageObject;
    private int loadingPinnedMessage;

    private ObjectAnimatorProxy pagedownButtonAnimation;
    private AnimatorSetProxy replyButtonAnimation;

    private boolean openSearchKeyboard;

    private int channelMessagesImportant;
    private boolean waitingForImportantLoad;

    private boolean waitingForReplyMessageLoad;

    private boolean allowStickersPanel;
    private boolean allowContextBotPanel;
    private boolean allowContextBotPanelSecond = true;
    private AnimatorSetProxy runningAnimation;

    private MessageObject selectedObject;
    private ArrayList<MessageObject> forwardingMessages;
    private MessageObject forwaringMessage;
    private MessageObject replyingMessageObject;
    private boolean paused = true;
    private boolean wasPaused = false;
    private boolean readWhenResume = false;
    private TLRPC.FileLocation replyImageLocation;
    private int linkSearchRequestId;
    private TLRPC.WebPage foundWebPage;
    private ArrayList<CharSequence> foundUrls;
    private String pendingLinkSearchString;
    private Runnable pendingWebPageTimeoutRunnable;
    private Runnable waitingForCharaterEnterRunnable;

    private boolean openAnimationEnded;

    private int readWithDate;
    private int readWithMid;
    private boolean scrollToTopOnResume;
    private boolean scrollToTopUnReadOnResume;
    private long dialog_id;
    private int lastLoadIndex;
    private boolean isBroadcast;
    private HashMap<Integer, MessageObject>[] selectedMessagesIds = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private HashMap<Integer, MessageObject>[] selectedMessagesCanCopyIds = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private int cantDeleteMessagesCount;
    private ArrayList<Integer> waitingForLoad = new ArrayList<>();

    private HashMap<Integer, MessageObject>[] messagesDict = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<>();
    protected ArrayList<MessageObject> messages = new ArrayList<>();
    private int maxMessageId[] = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE};
    private int minMessageId[] = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE};
    private int maxDate[] = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE};
    private int minDate[] = new int[2];
    private boolean endReached[] = new boolean[2];
    private boolean cacheEndReached[] = new boolean[2];
    private boolean forwardEndReached[] = new boolean[] {true, true};
    private boolean loading;
    private boolean firstLoading = true;
    private int loadsCount;
    private int last_message_id = 0;
    private long mergeDialogId;

    private int startLoadFromMessageId;
    private boolean needSelectFromMessageId;
    private int returnToMessageId;
    private int returnToLoadIndex;

    private boolean first = true;
    private int unread_to_load;
    private int first_unread_id;
    private boolean loadingForward;
    private MessageObject unreadMessageObject;
    private MessageObject scrollToMessage;
    private int highlightMessageId = Integer.MAX_VALUE;
    private int scrollToMessagePosition = -10000;

    private String currentPicturePath;

    private Rect scrollRect = new Rect();

    protected TLRPC.ChatFull info = null;

    private HashMap<Integer, TLRPC.BotInfo> botInfo = new HashMap<>();
    private String botUser;
    private long inlineReturn;
    private MessageObject botButtons;
    private MessageObject botReplyButtons;
    private int botsCount;
    private boolean hasBotsCommands;
    private long chatEnterTime = 0;
    private long chatLeaveTime = 0;

    private String startVideoEdit = null;

    private Runnable openSecretPhotoRunnable = null;
    private float startX = 0;
    private float startY = 0;

    private final static int copy = 10;
    private final static int forward = 11;
    private final static int delete = 12;
    private final static int chat_enc_timer = 13;
    private final static int chat_menu_attach = 14;
    private final static int clear_history = 15;
    private final static int delete_chat = 16;
    private final static int share_contact = 17;
    private final static int mute = 18;
    private final static int reply = 19;
    private final static int edit_done = 20;
    private final static int report = 21;

    private final static int bot_help = 30;
    private final static int bot_settings = 31;

    private final static int attach_photo = 0;
    private final static int attach_gallery = 1;
    private final static int attach_video = 2;
    private final static int attach_audio = 3;
    private final static int attach_document = 4;
    private final static int attach_contact = 5;
    private final static int attach_location = 6;

    private final static int search = 40;
    private final static int search_up = 41;
    private final static int search_down = 42;

    private final static int open_channel_profile = 50;

    private final static int id_chat_compose_panel = 1000;

    RecyclerListView.OnItemLongClickListener onItemLongClickListener = new RecyclerListView.OnItemLongClickListener() {
        @Override
        public boolean onItemClick(View view, int position) {
            if (!actionBar.isActionModeShowed()) {
                createMenu(view, false);
                return true;
            }
            return false;
        }
    };

    RecyclerListView.OnItemClickListener onItemClickListener = new RecyclerListView.OnItemClickListener() {
        @Override
        public void onItemClick(View view, int position) {
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
        inlineReturn = arguments.getLong("inline_return", 0);
        String inlineQuery = arguments.getString("inline_query");
        startLoadFromMessageId = arguments.getInt("message_id", 0);
        int migrated_to = arguments.getInt("migrated_to", 0);
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
            if (ChatObject.isChannel(currentChat)) {
                if (!currentChat.megagroup) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    channelMessagesImportant = preferences.getInt("important_" + dialog_id, 2);
                } else {
                    channelMessagesImportant = 1;
                }
                MessagesController.getInstance().startShortPoll(chatId, false);
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
            botUser = arguments.getString("botUser");
            if (inlineQuery != null) {
                MessagesController.getInstance().sendBotStart(currentUser, inlineQuery);
            }
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
            dialog_id = ((long) encId) << 32;
            maxMessageId[0] = maxMessageId[1] = Integer.MIN_VALUE;
            minMessageId[0] = minMessageId[1] = Integer.MAX_VALUE;
            MediaController.getInstance().startMediaObserver();
        } else {
            return false;
        }

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
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesReadEncrypted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioPlayStateChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.screenshotTook);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.replaceMessagesObjects);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didLoadedReplyMessages);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedWebpages);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesReadContent);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.botInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.botKeyboardDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatSearchResultsAvailable);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didUpdatedMessagesViews);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoCantLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didLoadedPinnedMessage);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.peerSettingsDidLoaded);

        super.onFragmentCreate();

        if (currentEncryptedChat == null && !isBroadcast) {
            BotQuery.loadBotKeyboard(dialog_id);
        }

        loading = true;
        MessagesController.getInstance().loadPeerSettings(dialog_id, currentUser, currentChat);
        MessagesController.getInstance().setLastCreatedDialogId(dialog_id, true);
        if (startLoadFromMessageId != 0) {
            needSelectFromMessageId = true;
            waitingForLoad.add(lastLoadIndex);
            if (migrated_to != 0) {
                mergeDialogId = migrated_to;
                MessagesController.getInstance().loadMessages(mergeDialogId, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, true, 0, classGuid, 3, 0, 0, lastLoadIndex++);
            } else {
                MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, true, 0, classGuid, 3, 0, channelMessagesImportant, lastLoadIndex++);
            }
        } else {
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, true, 0, classGuid, 2, 0, channelMessagesImportant, lastLoadIndex++);
        }

        if (currentChat != null) {
            Semaphore semaphore = null;
            if (isBroadcast) {
                semaphore = new Semaphore(0);
            }
            MessagesController.getInstance().loadChatInfo(currentChat.id, semaphore, ChatObject.isChannel(currentChat));
            if (isBroadcast && semaphore != null) {
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        URLSpanBotCommand.enabled = false;
        if (userId != 0 && currentUser.bot) {
            BotQuery.loadBotInfo(userId, true, classGuid);
            URLSpanBotCommand.enabled = true;
        } else if (info instanceof TLRPC.TL_chatFull) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                if (user != null && user.bot) {
                    BotQuery.loadBotInfo(user.id, true, classGuid);
                    URLSpanBotCommand.enabled = true;
                }
            }
        }

        if (currentUser != null) {
            userBlocked = MessagesController.getInstance().blockedUsers.contains(currentUser.id);
        }

        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, false);
        }

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
        if (mentionsAdapter != null) {
            mentionsAdapter.onDestroy();
        }
        MessagesController.getInstance().setLastCreatedDialogId(dialog_id, false);
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesReadEncrypted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.screenshotTook);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidStarted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.replaceMessagesObjects);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didLoadedReplyMessages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedWebpages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesReadContent);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.botInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.botKeyboardDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatSearchResultsAvailable);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioPlayStateChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didUpdatedMessagesViews);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoCantLoad);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didLoadedPinnedMessage);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.peerSettingsDidLoaded);

        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, true);
        }
        if (currentEncryptedChat != null) {
            MediaController.getInstance().stopMediaObserver();
            try {
                if (Build.VERSION.SDK_INT >= 14) {
                    getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
        }
        if (currentUser != null) {
            MessagesController.getInstance().cancelLoadFullUser(currentUser.id);
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (stickersAdapter != null) {
            stickersAdapter.onDestroy();
        }
        if (chatAttachView != null) {
            chatAttachView.onDestroy();
        }
        AndroidUtilities.unlockOrientation(getParentActivity());
        /*MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null && !messageObject.isMusic()) {
            MediaController.getInstance().stopAudio();
        }*/
        if (ChatObject.isChannel(currentChat)) {
            MessagesController.getInstance().startShortPoll(currentChat.id, true);
        }
    }

    @Override
    public View createView(Context context) {

        if (chatMessageCellsCache.isEmpty()) {
            for (int a = 0; a < 8; a++) {
                chatMessageCellsCache.add(new ChatMessageCell(context));
            }
        }
        for (int a = 1; a >= 0; a--) {
            selectedMessagesIds[a].clear();
            selectedMessagesCanCopyIds[a].clear();
        }
        cantDeleteMessagesCount = 0;

        hasOwnBackground = true;
        if (chatAttachView != null){
            chatAttachView.onDestroy();
            chatAttachView = null;
        }
        chatAttachViewSheet = null;

        Theme.loadRecources(context);

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        for (int a = 1; a >= 0; a--) {
                            selectedMessagesIds[a].clear();
                            selectedMessagesCanCopyIds[a].clear();
                        }
                        cantDeleteMessagesCount = 0;
                        chatActivityEnterView.setEditinigMessageObject(null, false);
                        actionBar.hideActionMode();
                        updatePinnedMessageView(true);
                        updateVisibleRows();
                    } else {
                        finishFragment();
                    }
                } else if (id == copy) {
                    String str = "";
                    for (int a = 1; a >= 0; a--) {
                        ArrayList<Integer> ids = new ArrayList<>(selectedMessagesCanCopyIds[a].keySet());
                        if (currentEncryptedChat == null) {
                            Collections.sort(ids);
                        } else {
                            Collections.sort(ids, Collections.reverseOrder());
                        }
                        for (int b = 0; b < ids.size(); b++) {
                            Integer messageId = ids.get(b);
                            MessageObject messageObject = selectedMessagesCanCopyIds[a].get(messageId);
                            if (str.length() != 0) {
                                str += "\n";
                            }
                            if (messageObject.type == 0 && messageObject.messageOwner.message != null) {
                                str += messageObject.messageOwner.message;
                            } else if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.caption != null) {
                                str += messageObject.messageOwner.media.caption;
                            } else {
                                str += messageObject.messageText;
                            }
                        }
                    }
                    if (str.length() != 0) {
                        try {
                            if (Build.VERSION.SDK_INT < 11) {
                                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setText(str);
                            } else {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", str);
                                clipboard.setPrimaryClip(clip);
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                    for (int a = 1; a >= 0; a--) {
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                    }
                    cantDeleteMessagesCount = 0;
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == edit_done) {
                    if (chatActivityEnterView != null && (chatActivityEnterView.isEditingCaption() || chatActivityEnterView.hasText())) {
                        chatActivityEnterView.doneEditingMessage();
                        actionBar.hideActionMode();
                        updatePinnedMessageView(true);
                    }
                } else if (id == delete) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    createDeleteMessagesAlert(null);
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 1);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate(ChatActivity.this);
                    presentFragment(fragment);
                } else if (id == chat_enc_timer) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    showDialog(AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat).create());
                } else if (id == clear_history || id == delete_chat) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final boolean isChat = (int) dialog_id < 0 && (int) (dialog_id >> 32) != 1;
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
                            if (id != clear_history) {
                                if (isChat) {
                                    if (ChatObject.isNotInChat(currentChat)) {
                                        MessagesController.getInstance().deleteDialog(dialog_id, 0);
                                    } else {
                                        MessagesController.getInstance().deleteUserFromChat((int) -dialog_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
                                    }
                                } else {
                                    MessagesController.getInstance().deleteDialog(dialog_id, 0);
                                }
                                finishFragment();
                            } else {
                                MessagesController.getInstance().deleteDialog(dialog_id, 1);
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
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
                        shareMyContact(replyingMessageObject);
                    }
                } else if (id == mute) {
                    toggleMute(false);
                } else if (id == report) {
                    showDialog(AlertsCreator.createReportAlert(getParentActivity(), dialog_id, ChatActivity.this));
                } else if (id == reply) {
                    MessageObject messageObject = null;
                    for (int a = 1; a >= 0; a--) {
                        if (messageObject == null && selectedMessagesIds[a].size() == 1) {
                            ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds[a].keySet());
                            messageObject = messagesDict[a].get(ids.get(0));
                        }
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                    }
                    if (messageObject != null && (messageObject.messageOwner.id > 0 || messageObject.messageOwner.id < 0 && currentEncryptedChat != null)) {
                        showReplyPanel(true, messageObject, null, null, false, true);
                    }
                    cantDeleteMessagesCount = 0;
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == chat_menu_attach) {
                    if (getParentActivity() == null) {
                        return;
                    }

                    createChatAttachView();
                    chatAttachView.loadGalleryPhotos();
                    if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
                        chatActivityEnterView.closeKeyboard();
                    }

                    chatAttachView.init(ChatActivity.this);
                    showDialog(chatAttachViewSheet);
                } else if (id == bot_help) {
                    SendMessagesHelper.getInstance().sendMessage("/help", dialog_id, null, null, false, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null, null);
                } else if (id == bot_settings) {
                    SendMessagesHelper.getInstance().sendMessage("/settings", dialog_id, null, null, false, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null, null);
                } else if (id == search) {
                    openSearchWithText(null);
                } else if (id == search_up) {
                    MessagesSearchQuery.searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 1);
                } else if (id == search_down) {
                    MessagesSearchQuery.searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 2);
                } else if (id == open_channel_profile) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", currentChat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    fragment.setChatInfo(info);
                    fragment.setPlayProfileAnimation(true);
                    presentFragment(fragment);
                }
            }
        });

        avatarContainer = new ChatAvatarContainer(context, this, ChatObject.isChannel(currentChat) && !currentChat.megagroup && !(currentChat instanceof TLRPC.TL_channelForbidden), currentEncryptedChat != null);
        avatarContainer.setRadioChecked(channelMessagesImportant == 1, false);
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));
        avatarContainer.setDelegate(new ChatAvatarContainer.ChatAvatarContainerDelegate() {
            @Override
            public void didPressedRadioButton() {
                switchImportantMode(null);
            }
        });

        if (currentChat != null) {
            if (!ChatObject.isChannel(currentChat)) {
                int count = currentChat.participants_count;
                if (info != null) {
                    count = info.participants.participants.size();
                }
                if (count == 0 || currentChat.deactivated || currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden || info != null && info.participants instanceof TLRPC.TL_chatParticipantsForbidden) {
                    avatarContainer.setEnabled(false);
                }
            }
        }

        ActionBarMenu menu = actionBar.createMenu();

        if (currentEncryptedChat == null && !isBroadcast) {
            searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true, false).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

                @Override
                public void onSearchCollapse() {
                    avatarContainer.setVisibility(View.VISIBLE);
                    if (chatActivityEnterView.hasText()) {
                        if (headerItem != null) {
                            headerItem.setVisibility(View.GONE);
                        }
                        if (attachItem != null) {
                            attachItem.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (headerItem != null) {
                            headerItem.setVisibility(View.VISIBLE);
                        }
                        if (attachItem != null) {
                            attachItem.setVisibility(View.GONE);
                        }
                    }
                    searchItem.setVisibility(View.GONE);
                    //chatActivityEnterView.setVisibility(View.VISIBLE);
                    searchUpItem.clearAnimation();
                    searchDownItem.clearAnimation();
                    searchUpItem.setVisibility(View.GONE);
                    searchDownItem.setVisibility(View.GONE);
                    highlightMessageId = Integer.MAX_VALUE;
                    updateVisibleRows();
                    scrollToLastMessage(false);
                }

                @Override
                public void onSearchExpand() {
                    if (!openSearchKeyboard) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            searchItem.getSearchField().requestFocus();
                            AndroidUtilities.showKeyboard(searchItem.getSearchField());
                        }
                    }, 300);
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    updateSearchButtons(0);
                    MessagesSearchQuery.searchMessagesInChat(editText.getText().toString(), dialog_id, mergeDialogId, classGuid, 0);
                }
            });
            searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
            searchItem.setVisibility(View.GONE);

            searchUpItem = menu.addItem(search_up, R.drawable.search_up);
            searchUpItem.setVisibility(View.GONE);
            searchDownItem = menu.addItem(search_down, R.drawable.search_down);
            searchDownItem.setVisibility(View.GONE);
        }

        headerItem = menu.addItem(0, R.drawable.ic_ab_other);
        if (channelMessagesImportant != 0 && !currentChat.megagroup) {
            headerItem.addSubItem(open_channel_profile, LocaleController.getString("OpenChannelProfile", R.string.OpenChannelProfile), 0);
        }
        if (searchItem != null) {
            headerItem.addSubItem(search, LocaleController.getString("Search", R.string.Search), 0);
        }
        if (ChatObject.isChannel(currentChat) && !currentChat.creator && (!currentChat.megagroup || currentChat.username != null && currentChat.username.length() > 0)) {
            headerItem.addSubItem(report, LocaleController.getString("ReportChat", R.string.ReportChat), 0);
        }
        if (currentUser != null) {
            addContactItem = headerItem.addSubItem(share_contact, "", 0);
        }
        if (currentEncryptedChat != null) {
            timeItem2 = headerItem.addSubItem(chat_enc_timer, LocaleController.getString("SetTimer", R.string.SetTimer), 0);
        }
        if (channelMessagesImportant == 0) {
            headerItem.addSubItem(clear_history, LocaleController.getString("ClearHistory", R.string.ClearHistory), 0);
            if (currentChat != null && !isBroadcast) {
                headerItem.addSubItem(delete_chat, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit), 0);
            } else {
                headerItem.addSubItem(delete_chat, LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser), 0);
            }
        }
        muteItem = headerItem.addSubItem(mute, null, 0);
        if (currentUser != null && currentEncryptedChat == null && currentUser.bot) {
            headerItem.addSubItem(bot_settings, LocaleController.getString("BotSettings", R.string.BotSettings), 0);
            headerItem.addSubItem(bot_help, LocaleController.getString("BotHelp", R.string.BotHelp), 0);
            updateBotButtons();
        }

        updateTitle();
        avatarContainer.updateOnlineCount();
        avatarContainer.updateSubtitle();
        updateTitleIcons();

        attachItem = menu.addItem(chat_menu_attach, R.drawable.ic_ab_other).setOverrideMenuClick(true).setAllowCloseAnimation(false);
        attachItem.setVisibility(View.GONE);
        menuItem = menu.addItem(chat_menu_attach, R.drawable.ic_ab_attach).setAllowCloseAnimation(false);
        menuItem.setBackgroundDrawable(null);

        actionModeViews.clear();

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.ACTION_BAR_ACTION_MODE_TEXT_COLOR);
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));
        selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        actionModeTextView = new TextView(actionMode.getContext());
        actionModeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        actionModeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        actionModeTextView.setTextColor(Theme.ACTION_BAR_ACTION_MODE_TEXT_COLOR);
        actionModeTextView.setVisibility(View.GONE);
        actionModeTextView.setGravity(Gravity.CENTER_VERTICAL);
        actionModeTextView.setText(LocaleController.getString("Edit", R.string.Edit));
        actionMode.addView(actionModeTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));
        actionModeTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        if (currentEncryptedChat == null) {
            if (!isBroadcast) {
                actionModeViews.add(actionMode.addItem(reply, R.drawable.ic_ab_reply, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            }
            actionModeViews.add(actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItem(forward, R.drawable.ic_ab_fwd_forward, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItem(edit_done, R.drawable.check_blue, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            actionMode.getItem(edit_done).setVisibility(View.GONE);
        } else {
            actionModeViews.add(actionMode.addItem(reply, R.drawable.ic_ab_reply, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));
        }
        actionMode.getItem(copy).setVisibility(selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);
        actionMode.getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
        checkActionBarMenu();

        fragmentView = new SizeNotifierFrameLayout(context) {

            int inputFieldHeight = 0;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                int keyboardSize = getKeyboardHeight();

                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    heightSize -= chatActivityEnterView.getEmojiPadding();
                }

                int childCount = getChildCount();

                measureChildWithMargins(chatActivityEnterView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                inputFieldHeight = chatActivityEnterView.getMeasuredHeight();

                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == chatActivityEnterView) {
                        continue;
                    }
                    try {
                        if (child == chatListView || child == progressView) {
                            int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                            int contentHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize - inputFieldHeight + AndroidUtilities.dp(2)), MeasureSpec.EXACTLY);
                            child.measure(contentWidthSpec, contentHeightSpec);
                        } else if (child == emptyViewContainer) {
                            int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                            int contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                            child.measure(contentWidthSpec, contentHeightSpec);
                        } else if (chatActivityEnterView.isPopupView(child)) {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        } else if (child == mentionContainer) {
                            int orientation = mentionsAdapter.getOrientation();
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mentionContainer.getLayoutParams();
                            int height;
                            mentionListViewIgnoreLayout = true;
                            if (mentionsAdapter.isBotContext()) {
                                if (orientation == LinearLayoutManager.HORIZONTAL) {
                                    height = AndroidUtilities.dp(90);
                                    if (botSwitchCell.getVisibility() == VISIBLE) {
                                        height += AndroidUtilities.dp(36);
                                        mentionListView.setPadding(0, AndroidUtilities.dp(2 + 36), AndroidUtilities.dp(5), 0);
                                    } else {
                                        mentionListView.setPadding(0, AndroidUtilities.dp(2), AndroidUtilities.dp(5), 0);
                                    }
                                } else {
                                    int size = mentionsAdapter.getItemCount();
                                    int maxHeight = 0;
                                    if (mentionsAdapter.getBotContextSwitch() != null) {
                                        maxHeight += 36;
                                        size -= 1;
                                    }
                                    maxHeight += size * 68;

                                    height = heightSize - chatActivityEnterView.getMeasuredHeight() + (maxHeight != 0 ? AndroidUtilities.dp(2) : 0);
                                    mentionListView.setPadding(0, Math.max(0, height - AndroidUtilities.dp(Math.min(maxHeight, 68 * 1.8f))), 0, 0);
                                }
                                layoutParams.height = height;
                                layoutParams.topMargin = 0;
                            } else {
                                if (orientation == LinearLayoutManager.HORIZONTAL) {
                                    mentionListView.setPadding(0, AndroidUtilities.dp(2), AndroidUtilities.dp(5), 0);
                                    height = 90;
                                } else {
                                    mentionListView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
                                    if (mentionsAdapter.isBotContext()) {
                                        height = 36 * 3 + 18;
                                    } else {
                                        height = 36 * Math.min(3, mentionsAdapter.getItemCount()) + (mentionsAdapter.getItemCount() > 3 ? 18 : 0);
                                    }
                                }
                                layoutParams.height = AndroidUtilities.dp(height + (height != 0 ? 2 : 0));
                                layoutParams.topMargin = -AndroidUtilities.dp(height);
                            }
                            mentionListViewIgnoreLayout = false;
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY));
                        } else {
                            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) ? chatActivityEnterView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (child == mentionContainer) {
                        childTop -= chatActivityEnterView.getMeasuredHeight() - AndroidUtilities.dp(2);
                    } else if (child == pagedownButton) {
                        childTop -= chatActivityEnterView.getMeasuredHeight();
                    } else if (child == emptyViewContainer) {
                        childTop -= inputFieldHeight / 2;
                    } else if (chatActivityEnterView.isPopupView(child)) {
                        childTop = chatActivityEnterView.getBottom();
                    } else if (child == gifHintTextView) {
                        childTop -= inputFieldHeight;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                updateMessagesVisisblePart();
                notifyHeightChanged();
            }
        };

        SizeNotifierFrameLayout contentView = (SizeNotifierFrameLayout) fragmentView;

        contentView.setBackgroundImage(ApplicationLoader.getCachedWallpaper());

        emptyViewContainer = new FrameLayout(context);
        emptyViewContainer.setVisibility(View.INVISIBLE);
        contentView.addView(emptyViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        emptyViewContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        if (currentEncryptedChat == null) {
            TextView emptyView = new TextView(context);
            if (currentUser != null && currentUser.id != 777000 && currentUser.id != 429000 && (currentUser.id / 1000 == 333 || currentUser.id % 1000 == 0)) {
                emptyView.setText(LocaleController.getString("GotAQuestion", R.string.GotAQuestion));
            } else {
                emptyView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
            }
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setTextColor(Theme.CHAT_EMPTY_VIEW_TEXT_COLOR);
            emptyView.setBackgroundResource(R.drawable.system);
            emptyView.getBackground().setColorFilter(Theme.colorFilter);
            emptyView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            emptyView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(2), AndroidUtilities.dp(10), AndroidUtilities.dp(3));
            emptyViewContainer.addView(emptyView, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        } else {
            LinearLayout secretChatPlaceholder = new LinearLayout(context);
            secretChatPlaceholder.setBackgroundResource(R.drawable.system);
            secretChatPlaceholder.getBackground().setColorFilter(Theme.colorFilter);
            secretChatPlaceholder.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            secretChatPlaceholder.setOrientation(LinearLayout.VERTICAL);
            emptyViewContainer.addView(secretChatPlaceholder, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            secretViewStatusTextView = new TextView(context);
            secretViewStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            secretViewStatusTextView.setTextColor(Theme.SECRET_CHAT_INFO_TEXT_COLOR);
            secretViewStatusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            secretViewStatusTextView.setMaxWidth(AndroidUtilities.dp(210));
            if (currentEncryptedChat.admin_id == UserConfig.getClientUserId()) {
                secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, UserObject.getFirstName(currentUser)));
            } else {
                secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, UserObject.getFirstName(currentUser)));
            }
            secretChatPlaceholder.addView(secretViewStatusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

            TextView textView = new TextView(context);
            textView.setText(LocaleController.getString("EncryptedDescriptionTitle", R.string.EncryptedDescriptionTitle));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(Theme.SECRET_CHAT_INFO_TEXT_COLOR);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setMaxWidth(AndroidUtilities.dp(260));
            secretChatPlaceholder.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 8, 0, 0));

            for (int a = 0; a < 4; a++) {
                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                secretChatPlaceholder.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 8, 0, 0));

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.ic_lock_white);

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textView.setTextColor(Theme.SECRET_CHAT_INFO_TEXT_COLOR);
                textView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
                textView.setMaxWidth(AndroidUtilities.dp(260));

                switch (a) {
                    case 0:
                        textView.setText(LocaleController.getString("EncryptedDescription1", R.string.EncryptedDescription1));
                        break;
                    case 1:
                        textView.setText(LocaleController.getString("EncryptedDescription2", R.string.EncryptedDescription2));
                        break;
                    case 2:
                        textView.setText(LocaleController.getString("EncryptedDescription3", R.string.EncryptedDescription3));
                        break;
                    case 3:
                        textView.setText(LocaleController.getString("EncryptedDescription4", R.string.EncryptedDescription4));
                        break;
                }

                if (LocaleController.isRTL) {
                    linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 3, 0, 0));
                } else {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 8, 0));
                    linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                }
            }
        }

        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        if (mentionsAdapter != null) {
            mentionsAdapter.onDestroy();
        }

        chatListView = new RecyclerListView(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (chatAdapter.isBot/* || ChatObject.isChannel(currentChat) && currentChat.megagroup*/) {
                    int childCount = getChildCount();
                    for (int a = 0; a < childCount; a++) {
                        View child = getChildAt(a);
                        if (child instanceof BotHelpCell/* || child instanceof ChatMigrateCell*/) {
                            int height = b - t;
                            int top = height / 2 - child.getMeasuredHeight() / 2;
                            if (child.getTop() > top) {
                                child.layout(0, top, r - l, top + child.getMeasuredHeight());
                            }
                            break;
                        }
                    }
                }
            }
        };
        chatListView.setVerticalScrollBarEnabled(true);
        chatListView.setAdapter(chatAdapter = new ChatActivityAdapter(context));
        chatListView.setClipToPadding(false);
        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
        chatListView.setItemAnimator(null);
        chatListView.setLayoutAnimation(null);
        chatLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        chatLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        chatLayoutManager.setStackFromEnd(true);
        chatListView.setLayoutManager(chatLayoutManager);
        contentView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        chatListView.setOnItemLongClickListener(onItemLongClickListener);
        chatListView.setOnItemClickListener(onItemClickListener);
        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && highlightMessageId != Integer.MAX_VALUE) {
                    highlightMessageId = Integer.MAX_VALUE;
                    updateVisibleRows();
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkScrollForLoad(true);
                int firstVisibleItem = chatLayoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(chatLayoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                if (visibleItemCount > 0) {
                    int totalItemCount = chatAdapter.getItemCount();
                    if (firstVisibleItem + visibleItemCount == totalItemCount && forwardEndReached[0]) {
                        showPagedownButton(false, true);
                    }
                }
                updateMessagesVisisblePart();
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
                        } else if (SecretPhotoViewer.getInstance().isVisible()) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    chatListView.setOnItemLongClickListener(onItemLongClickListener);
                                    chatListView.setLongClickable(true);
                                }
                            });
                            SecretPhotoViewer.getInstance().closePhoto();
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
                            chatListView.setOnItemClickListener(onItemClickListener);
                            chatListView.setOnItemLongClickListener(onItemLongClickListener);
                            chatListView.setLongClickable(true);
                        }
                    }
                }
                return false;
            }
        });
        chatListView.setOnInterceptTouchListener(new RecyclerListView.OnInterceptTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (chatActivityEnterView != null && chatActivityEnterView.isEditingMessage()) {
                    return true;
                }
                if (actionBar.isActionModeShowed()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    int count = chatListView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = chatListView.getChildAt(a);
                        int top = view.getTop();
                        int bottom = view.getBottom();
                        if (top > y || bottom < y) {
                            continue;
                        }
                        if (!(view instanceof ChatMessageCell)) {
                            break;
                        }
                        final ChatMessageCell cell = (ChatMessageCell) view;
                        final MessageObject messageObject = cell.getMessageObject();
                        if (messageObject == null || messageObject.isSending() || !messageObject.isSecretPhoto() || !cell.getPhotoImage().isInsideImage(x, y - top)) {
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

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.INVISIBLE);
        contentView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        View view = new View(context);
        view.setBackgroundResource(R.drawable.system_loader);
        view.getBackground().setColorFilter(Theme.colorFilter);
        progressView.addView(view, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        ProgressBar progressBar = new ProgressBar(context);
        try {
            progressBar.setIndeterminateDrawable(context.getResources().getDrawable(R.drawable.loading_animation));
        } catch (Exception e) {
            //don't promt
        }
        progressBar.setIndeterminate(true);
        AndroidUtilities.setProgressBarAnimationDuration(progressBar, 1500);
        progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        if (ChatObject.isChannel(currentChat)) {
            pinnedMessageView = new FrameLayoutFixed(context);
            pinnedMessageView.setTag(1);
            ViewProxy.setTranslationY(pinnedMessageView, -AndroidUtilities.dp(50));
            pinnedMessageView.clearAnimation();
            pinnedMessageView.setVisibility(View.GONE);
            pinnedMessageView.setBackgroundResource(R.drawable.blockpanel);
            contentView.addView(pinnedMessageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));
            pinnedMessageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollToMessageId(info.pinned_msg_id, 0, true, 0);
                }
            });

            View lineView = new View(context);
            lineView.setBackgroundColor(0xff6c9fd2);
            pinnedMessageView.addView(lineView, LayoutHelper.createFrame(2, 32, Gravity.LEFT | Gravity.TOP, 8, 8, 0, 0));

            pinnedMessageNameTextView = new SimpleTextView(context);
            pinnedMessageNameTextView.setTextSize(14);
            pinnedMessageNameTextView.setTextColor(Theme.PINNED_PANEL_NAME_TEXT_COLOR);
            pinnedMessageNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            pinnedMessageView.addView(pinnedMessageNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(18), Gravity.TOP | Gravity.LEFT, 18, 7.3f, 52, 0));

            pinnedMessageTextView = new SimpleTextView(context);
            pinnedMessageTextView.setTextSize(14);
            pinnedMessageTextView.setTextColor(Theme.PINNED_PANEL_MESSAGE_TEXT_COLOR);
            pinnedMessageView.addView(pinnedMessageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(18), Gravity.TOP | Gravity.LEFT, 18, 25.3f, 52, 0));

            ImageView closePinned = new ImageView(context);
            closePinned.setImageResource(R.drawable.miniplayer_close);
            closePinned.setScaleType(ImageView.ScaleType.CENTER);
            pinnedMessageView.addView(closePinned, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
            closePinned.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (currentChat.creator || currentChat.editor) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("UnpinMessageAlert", R.string.UnpinMessageAlert));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.getInstance().pinChannelMessage(currentChat, 0, false);
                            }
                        });
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    } else {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        preferences.edit().putInt("pin_" + dialog_id, info.pinned_msg_id).commit();
                        updatePinnedMessageView(true);
                    }
                }
            });
        }

        reportSpamView = new LinearLayout(context);
        reportSpamView.setTag(1);
        ViewProxy.setTranslationY(reportSpamView, -AndroidUtilities.dp(50));
        reportSpamView.clearAnimation();
        reportSpamView.setVisibility(View.GONE);
        reportSpamView.setBackgroundResource(R.drawable.blockpanel);
        contentView.addView(reportSpamView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));

        addToContactsButton = new TextView(context);
        addToContactsButton.setTextColor(Theme.CHAT_ADD_CONTACT_TEXT_COLOR);
        addToContactsButton.setVisibility(View.GONE);
        addToContactsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addToContactsButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addToContactsButton.setSingleLine(true);
        addToContactsButton.setMaxLines(1);
        addToContactsButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        addToContactsButton.setGravity(Gravity.CENTER);
        addToContactsButton.setText(LocaleController.getString("AddContactChat", R.string.AddContactChat));
        reportSpamView.addView(addToContactsButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, Gravity.LEFT | Gravity.TOP, 0, 0, 0, AndroidUtilities.dp(1)));
        addToContactsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                args.putInt("user_id", currentUser.id);
                args.putBoolean("addContact", true);
                presentFragment(new ContactAddActivity(args));
            }
        });

        reportSpamContainer = new FrameLayout(context);
        reportSpamView.addView(reportSpamContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.LEFT | Gravity.TOP, 0, 0, 0, AndroidUtilities.dp(1)));

        reportSpamButton = new TextView(context);
        reportSpamButton.setTextColor(Theme.CHAT_REPORT_SPAM_TEXT_COLOR);
        reportSpamButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        reportSpamButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reportSpamButton.setSingleLine(true);
        reportSpamButton.setMaxLines(1);
        if (currentChat != null) {
            reportSpamButton.setText(LocaleController.getString("ReportSpamAndLeave", R.string.ReportSpamAndLeave));
        } else {
            reportSpamButton.setText(LocaleController.getString("ReportSpam", R.string.ReportSpam));
        }
        reportSpamButton.setGravity(Gravity.CENTER);
        reportSpamButton.setPadding(AndroidUtilities.dp(50), 0, AndroidUtilities.dp(50), 0);
        reportSpamContainer.addView(reportSpamButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        reportSpamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    builder.setMessage(LocaleController.getString("ReportSpamAlertChannel", R.string.ReportSpamAlertChannel));
                } else if (currentChat != null) {
                    builder.setMessage(LocaleController.getString("ReportSpamAlertGroup", R.string.ReportSpamAlertGroup));
                } else {
                    builder.setMessage(LocaleController.getString("ReportSpamAlert", R.string.ReportSpamAlert));
                }
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (currentUser != null) {
                            MessagesController.getInstance().blockUser(currentUser.id);
                        }
                        MessagesController.getInstance().reportSpam(dialog_id, currentUser, currentChat);
                        updateSpamView();
                        if (currentChat != null) {
                            if (ChatObject.isNotInChat(currentChat)) {
                                MessagesController.getInstance().deleteDialog(dialog_id, 0);
                            } else {
                                MessagesController.getInstance().deleteUserFromChat((int) -dialog_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
                            }
                        } else {
                            MessagesController.getInstance().deleteDialog(dialog_id, 0);
                        }
                        finishFragment();
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
        });

        ImageView closeReportSpam = new ImageView(context);
        closeReportSpam.setImageResource(R.drawable.miniplayer_close);
        closeReportSpam.setScaleType(ImageView.ScaleType.CENTER);
        reportSpamContainer.addView(closeReportSpam, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
        closeReportSpam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MessagesController.getInstance().hideReportSpam(dialog_id, currentUser, currentChat);
                updateSpamView();
            }
        });

        alertView = new FrameLayoutFixed(context);
        alertView.setTag(1);
        ViewProxy.setTranslationY(alertView, -AndroidUtilities.dp(50));
        alertView.clearAnimation();
        alertView.setVisibility(View.GONE);
        alertView.setBackgroundResource(R.drawable.blockpanel);
        contentView.addView(alertView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));

        alertNameTextView = new TextView(context);
        alertNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        alertNameTextView.setTextColor(Theme.ALERT_PANEL_NAME_TEXT_COLOR);
        alertNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        alertNameTextView.setSingleLine(true);
        alertNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        alertNameTextView.setMaxLines(1);
        alertView.addView(alertNameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 8, 5, 8, 0));

        alertTextView = new TextView(context);
        alertTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        alertTextView.setTextColor(Theme.ALERT_PANEL_MESSAGE_TEXT_COLOR);
        alertTextView.setSingleLine(true);
        alertTextView.setEllipsize(TextUtils.TruncateAt.END);
        alertTextView.setMaxLines(1);
        alertView.addView(alertTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 8, 23, 8, 0));

        if (!isBroadcast) {
            mentionContainer = new FrameLayoutFixed(context) {

                private Drawable background;

                @Override
                public void onDraw(Canvas canvas) {
                    if (mentionsAdapter.isBotContext() && mentionsAdapter.getOrientation() == LinearLayoutManager.VERTICAL) {
                        background.setBounds(0, mentionListViewScrollOffsetY - AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight());
                    } else {
                        background.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    }
                    background.draw(canvas);
                }

                @Override
                public void setBackgroundResource(int resid) {
                    background = getContext().getResources().getDrawable(resid);
                }

                @Override
                public void requestLayout() {
                    if (mentionListViewIgnoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }
            };
            mentionContainer.setBackgroundResource(R.drawable.compose_panel);
            mentionContainer.setVisibility(View.GONE);
            mentionContainer.setWillNotDraw(false);
            contentView.addView(mentionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 110, Gravity.LEFT | Gravity.BOTTOM));

            mentionListView = new RecyclerListView(context) {

                private int lastWidth;
                private int lastHeight;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    if (!mentionListViewIsScrolling && mentionListViewScrollOffsetY != 0 && event.getY() < mentionListViewScrollOffsetY && mentionsAdapter.isBotContext() && mentionsAdapter.getOrientation() == LinearLayoutManager.VERTICAL) {
                        return false;
                    }
                    boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, mentionListView, 0);
                    return super.onInterceptTouchEvent(event) || result;
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!mentionListViewIsScrolling && mentionListViewScrollOffsetY != 0 && event.getY() < mentionListViewScrollOffsetY && mentionsAdapter.isBotContext() && mentionsAdapter.getOrientation() == LinearLayoutManager.VERTICAL) {
                        return false;
                    }
                    //supress warning
                    return super.onTouchEvent(event);
                }

                @Override
                public void requestLayout() {
                    if (mentionListViewIgnoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    int width = r - l;
                    int height = b - t;

                    int newPosition = -1;
                    int newTop = 0;
                    if (mentionListView != null && mentionListViewLastViewPosition >= 0 && width == lastWidth && height - lastHeight != 0) {
                        newPosition = mentionListViewLastViewPosition;
                        newTop = mentionListViewLastViewTop + height - lastHeight - getPaddingTop();
                    }

                    super.onLayout(changed, l, t, r, b);

                    if (newPosition != -1) {
                        mentionListViewIgnoreLayout = true;
                        mentionLayoutManager.scrollToPositionWithOffset(newPosition, newTop);
                        super.onLayout(false, l, t, r, b);
                        mentionListViewIgnoreLayout = false;
                    }

                    lastHeight = height;
                    lastWidth = width;
                    mentionListViewUpdateLayout();
                }
            };
            mentionListView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return StickerPreviewViewer.getInstance().onTouch(event, mentionListView, 0, mentionsOnItemClickListener);
                }
            });
            mentionLayoutManager = new LinearLayoutManager(context) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            };
            mentionListView.setItemAnimator(null);
            mentionListView.setLayoutAnimation(null);
            mentionLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mentionListView.setLayoutManager(mentionLayoutManager);
            mentionListView.setOverScrollMode(ListView.OVER_SCROLL_NEVER);
            mentionContainer.addView(mentionListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            mentionListView.setAdapter(mentionsAdapter = new MentionsAdapter(context, false, dialog_id, new MentionsAdapter.MentionsAdapterDelegate() {
                @Override
                public void needChangePanelVisibility(boolean show) {
                    if (show) {
                        int orientation = mentionsAdapter.getOrientation();
                        if (orientation == LinearLayoutManager.HORIZONTAL) {
                            mentionListView.setClipToPadding(false);
                            mentionListView.setDisallowInterceptTouchEvents(true);
                        } else {
                            mentionListView.setClipToPadding(!mentionsAdapter.isBotContext());
                            mentionListView.setDisallowInterceptTouchEvents(false);
                        }
                        if (mentionsAdapter.isBotContext() && orientation == LinearLayoutManager.HORIZONTAL && mentionsAdapter.getBotContextSwitch() != null) {
                            botSwitchShadow.setVisibility(View.VISIBLE);
                            botSwitchCell.setVisibility(View.VISIBLE);
                            botSwitchCell.setText(mentionsAdapter.getBotContextSwitch().text);
                        } else {
                            botSwitchShadow.setVisibility(View.GONE);
                            botSwitchCell.setVisibility(View.GONE);
                        }
                        if (!mentionsAdapter.isBotContext() || orientation == LinearLayoutManager.HORIZONTAL) {
                            mentionListViewScrollOffsetY = 0;
                            mentionListViewLastViewPosition = -1;
                        }
                        mentionLayoutManager.setOrientation(orientation);

                        if (mentionListAnimation != null) {
                            mentionListAnimation.cancel();
                            mentionListAnimation = null;
                        }

                        if (mentionContainer.getVisibility() == View.VISIBLE) {
                            ViewProxy.setAlpha(mentionContainer, 1.0f);
                            return;
                        } else {
                            mentionLayoutManager.scrollToPositionWithOffset(0, 10000);
                        }
                        if (allowStickersPanel && (!mentionsAdapter.isBotContext() || (allowContextBotPanel || allowContextBotPanelSecond))) {
                            if (currentEncryptedChat != null && mentionsAdapter.isBotContext()) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                if (!preferences.getBoolean("secretbot", false)) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setMessage(LocaleController.getString("SecretChatContextBotAlert", R.string.SecretChatContextBotAlert));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    showDialog(builder.create());
                                    preferences.edit().putBoolean("secretbot", true).commit();
                                }
                            }
                            mentionContainer.setVisibility(View.VISIBLE);
                            mentionContainer.setTag(null);
                            mentionListAnimation = new AnimatorSetProxy();
                            mentionListAnimation.playTogether(
                                    ObjectAnimatorProxy.ofFloat(mentionContainer, "alpha", 0.0f, 1.0f)
                            );
                            mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                                @Override
                                public void onAnimationEnd(Object animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionContainer.clearAnimation();
                                        mentionListAnimation = null;
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Object animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionListAnimation = null;
                                    }
                                }
                            });
                            mentionListAnimation.setDuration(200);
                            mentionListAnimation.start();
                        } else {
                            ViewProxy.setAlpha(mentionContainer, 1.0f);
                            mentionContainer.clearAnimation();
                            mentionContainer.setVisibility(View.INVISIBLE);
                        }
                    } else {
                        if (mentionListAnimation != null) {
                            mentionListAnimation.cancel();
                            mentionListAnimation = null;
                        }

                        if (mentionContainer.getVisibility() == View.GONE) {
                            return;
                        }
                        if (allowStickersPanel) {
                            mentionListAnimation = new AnimatorSetProxy();
                            mentionListAnimation.playTogether(
                                    ObjectAnimatorProxy.ofFloat(mentionContainer, "alpha", 0.0f)
                            );
                            mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                                @Override
                                public void onAnimationEnd(Object animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionContainer.clearAnimation();
                                        mentionContainer.setVisibility(View.GONE);
                                        mentionContainer.setTag(null);
                                        mentionListAnimation = null;
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Object animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionListAnimation = null;
                                    }
                                }
                            });
                            mentionListAnimation.setDuration(200);
                            mentionListAnimation.start();
                        } else {
                            mentionContainer.setTag(null);
                            mentionContainer.clearAnimation();
                            mentionContainer.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onContextSearch(boolean searching) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.setCaption(mentionsAdapter.getBotCaption());
                        chatActivityEnterView.showContextProgress(searching);
                    }
                }

                @Override
                public void onContextClick(TLRPC.BotInlineResult result) {
                    if (getParentActivity() == null || result.content_url == null) {
                        return;
                    }
                    if (result.type.equals("video") || result.type.equals("web_player_video")) {
                        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                        builder.setCustomView(new WebFrameLayout(getParentActivity(), builder.create(), result.title != null ? result.title : "", result.description, result.content_url, result.content_url, result.w, result.h));
                        builder.setUseFullWidth(true);
                        showDialog(builder.create());
                    } else {
                        Browser.openUrl(getParentActivity(), result.content_url);
                    }
                }
            }));
            if (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup) {
                mentionsAdapter.setBotInfo(botInfo);
            }
            mentionsAdapter.setParentFragment(this);
            mentionsAdapter.setChatInfo(info);
            mentionsAdapter.setNeedUsernames(currentChat != null);
            mentionsAdapter.setNeedBotContext(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
            mentionsAdapter.setBotsCount(currentChat != null ? botsCount : 1);
            mentionListView.setOnItemClickListener(mentionsOnItemClickListener = new RecyclerListView.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    Object object = mentionsAdapter.getItem(position);
                    int start = mentionsAdapter.getResultStartPosition();
                    int len = mentionsAdapter.getResultLength();
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        if (user != null) {
                            chatActivityEnterView.replaceWithText(start, len, "@" + user.username + " ");
                        }
                    } else if (object instanceof String) {
                        if (mentionsAdapter.isBotCommands()) {
                            SendMessagesHelper.getInstance().sendMessage((String) object, dialog_id, null, null, false, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null, null);
                            chatActivityEnterView.setFieldText("");
                        } else {
                            chatActivityEnterView.replaceWithText(start, len, object + " ");
                        }
                    } else if (object instanceof TLRPC.BotInlineResult) {
                        String text = chatActivityEnterView.getFieldText();
                        if (text == null) {
                            return;
                        }
                        TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) object;
                        HashMap<String, String> params = new HashMap<>();
                        params.put("id", result.id);
                        params.put("query_id", "" + result.query_id);
                        params.put("bot", "" + mentionsAdapter.getContextBotId());
                        params.put("bot_name", mentionsAdapter.getContextBotName());
                        mentionsAdapter.addRecentBot();
                        SendMessagesHelper.prepareSendingBotContextResult(result, params, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                        chatActivityEnterView.setFieldText("");
                        showReplyPanel(false, null, null, null, false, true);
                    } else if (object instanceof TLRPC.TL_inlineBotSwitchPM) {
                        processInlineBotContextPM((TLRPC.TL_inlineBotSwitchPM) object);
                    }
                }
            });

            mentionListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                @Override
                public boolean onItemClick(View view, int position) {
                    if (!mentionsAdapter.isLongClickEnabled()) {
                        return false;
                    }
                    Object object = mentionsAdapter.getItem(position);
                    if (object instanceof String) {
                        if (mentionsAdapter.isBotCommands()) {
                            if (URLSpanBotCommand.enabled) {
                                chatActivityEnterView.setFieldText("");
                                chatActivityEnterView.setCommand(null, (String) object, true, currentChat != null && currentChat.megagroup);
                                return true;
                            }
                            return false;
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                            builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    mentionsAdapter.clearRecentHashtags();
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder.create());
                            return true;
                        }
                    }
                    return false;
                }
            });

            mentionListView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    mentionListViewIsScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING;
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    int lastVisibleItem = mentionLayoutManager.findLastVisibleItemPosition();
                    int visibleItemCount = lastVisibleItem == RecyclerView.NO_POSITION ? 0 : lastVisibleItem;
                    if (visibleItemCount > 0 && lastVisibleItem > mentionsAdapter.getItemCount() - 5) {
                        mentionsAdapter.searchForContextBotForNextOffset();
                    }

                    mentionListViewUpdateLayout();
                }
            });

            botSwitchCell = new BotSwitchCell(context);
            botSwitchCell.setVisibility(View.GONE);
            botSwitchCell.setBackgroundResource(R.drawable.list_selector);
            mentionContainer.addView(botSwitchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));
            botSwitchCell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    processInlineBotContextPM(mentionsAdapter.getBotContextSwitch());
                }
            });

            botSwitchShadow = new View(context);
            botSwitchShadow.setBackgroundResource(R.drawable.header_shadow);
            botSwitchShadow.setVisibility(View.GONE);
            mentionContainer.addView(botSwitchShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.TOP, 0, 38, 0, 0));
        }

        pagedownButton = new ImageView(context);
        pagedownButton.setVisibility(View.INVISIBLE);
        pagedownButton.setImageResource(R.drawable.pagedown);
        contentView.addView(pagedownButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 4));
        pagedownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (returnToMessageId > 0) {
                    scrollToMessageId(returnToMessageId, 0, true, returnToLoadIndex);
                } else {
                    scrollToLastMessage(true);
                }
            }
        });

        chatActivityEnterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true);
        chatActivityEnterView.setDialogId(dialog_id);
        chatActivityEnterView.addToAttachLayout(menuItem);
        chatActivityEnterView.setId(id_chat_compose_panel);
        chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
        chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
        contentView.addView(chatActivityEnterView, contentView.getChildCount() - 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(String message) {
                moveScrollToLastMessage();
                showReplyPanel(false, null, null, null, false, true);
                if (mentionsAdapter != null) {
                    mentionsAdapter.addHashtagsFromMessage(message);
                }
            }

            @Override
            public void onTextChanged(final CharSequence text, boolean bigChange) {
                MediaController.getInstance().setInputFieldHasText(text != null && text.length() != 0 || chatActivityEnterView.isEditingMessage());
                if (stickersAdapter != null && !chatActivityEnterView.isEditingMessage()) {
                    stickersAdapter.loadStikersForEmoji(text);
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), chatActivityEnterView.getCursorPosition(), messages);
                }
                if (waitingForCharaterEnterRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(waitingForCharaterEnterRunnable);
                    waitingForCharaterEnterRunnable = null;
                }
                if (chatActivityEnterView.isMessageWebPageSearchEnabled() && (!chatActivityEnterView.isEditingMessage() || !chatActivityEnterView.isEditingCaption())) {
                    if (bigChange) {
                        searchLinks(text, true);
                    } else {
                        waitingForCharaterEnterRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (this == waitingForCharaterEnterRunnable) {
                                    searchLinks(text, false);
                                    waitingForCharaterEnterRunnable = null;
                                }
                            }
                        };
                        AndroidUtilities.runOnUIThread(waitingForCharaterEnterRunnable, AndroidUtilities.WEB_URL == null ? 3000 : 1000);
                    }
                }
            }

            @Override
            public void needSendTyping() {
                MessagesController.getInstance().sendTyping(dialog_id, 0, classGuid);
            }

            @Override
            public void onAttachButtonHidden() {
                if (actionBar.isSearchFieldVisible()) {
                    return;
                }
                if (attachItem != null) {
                    attachItem.setVisibility(View.VISIBLE);
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.GONE);
                }
            }

            @Override
            public void onAttachButtonShow() {
                if (actionBar.isSearchFieldVisible()) {
                    return;
                }
                if (attachItem != null) {
                    attachItem.setVisibility(View.GONE);
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onMessageEditEnd() {
                mentionsAdapter.setNeedBotContext(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
                chatListView.setOnItemLongClickListener(onItemLongClickListener);
                chatListView.setOnItemClickListener(onItemClickListener);
                chatListView.setClickable(true);
                chatListView.setLongClickable(true);
                actionModeTextView.setVisibility(View.GONE);
                selectedMessagesCountTextView.setVisibility(View.VISIBLE);
                chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
            }

            @Override
            public void onWindowSizeChanged(int size) {
                if (size < AndroidUtilities.dp(72) + ActionBar.getCurrentActionBarHeight()) {
                    allowStickersPanel = false;
                    if (stickersPanel.getVisibility() == View.VISIBLE) {
                        stickersPanel.clearAnimation();
                        stickersPanel.setVisibility(View.INVISIBLE);
                    }
                    if (mentionContainer != null && mentionContainer.getVisibility() == View.VISIBLE) {
                        mentionContainer.clearAnimation();
                        mentionContainer.setVisibility(View.INVISIBLE);
                    }
                } else {
                    allowStickersPanel = true;
                    if (stickersPanel.getVisibility() == View.INVISIBLE) {
                        stickersPanel.clearAnimation();
                        stickersPanel.setVisibility(View.VISIBLE);
                    }
                    if (mentionContainer != null && mentionContainer.getVisibility() == View.INVISIBLE && (!mentionsAdapter.isBotContext() || (allowContextBotPanel || allowContextBotPanelSecond))) {
                        mentionContainer.clearAnimation();
                        mentionContainer.setVisibility(View.VISIBLE);
                        mentionContainer.setTag(null);
                    }
                }

                allowContextBotPanel = !chatActivityEnterView.isPopupShowing();
                checkContextBotPanel();
            }

            @Override
            public void onStickersTab(boolean opened) {
                if (emojiButtonRed != null) {
                    emojiButtonRed.setVisibility(View.GONE);
                }
                allowContextBotPanelSecond = !opened;
                checkContextBotPanel();
            }
        });

        FrameLayout replyLayout = new FrameLayout(context);
        replyLayout.setClickable(true);
        chatActivityEnterView.addTopView(replyLayout, 48);

        View lineView = new View(context);
        lineView.setBackgroundColor(0xffe8e8e8);
        replyLayout.addView(lineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT));

        replyIconImageView = new ImageView(context);
        replyIconImageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(replyIconImageView, LayoutHelper.createFrame(52, 46, Gravity.TOP | Gravity.LEFT));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.delete_reply);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(imageView, LayoutHelper.createFrame(52, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 0, 0));
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (forwardingMessages != null) {
                    forwardingMessages.clear();
                }
                showReplyPanel(false, null, null, foundWebPage, true, true);
            }
        });

        replyNameTextView = new TextView(context);
        replyNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        replyNameTextView.setTextColor(Theme.REPLY_PANEL_NAME_TEXT_COLOR);
        replyNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        replyNameTextView.setSingleLine(true);
        replyNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        replyNameTextView.setMaxLines(1);
        replyLayout.addView(replyNameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 4, 52, 0));

        replyObjectTextView = new TextView(context);
        replyObjectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        replyObjectTextView.setTextColor(Theme.REPLY_PANEL_MESSAGE_TEXT_COLOR);
        replyObjectTextView.setSingleLine(true);
        replyObjectTextView.setEllipsize(TextUtils.TruncateAt.END);
        replyObjectTextView.setMaxLines(1);
        replyLayout.addView(replyObjectTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 22, 52, 0));

        replyImageView = new BackupImageView(context);
        replyLayout.addView(replyImageView, LayoutHelper.createFrame(34, 34, Gravity.TOP | Gravity.LEFT, 52, 6, 0, 0));

        stickersPanel = new FrameLayout(context);
        stickersPanel.setVisibility(View.GONE);
        contentView.addView(stickersPanel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 81.5f, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 38));

        stickersListView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersListView, 0);
                return super.onInterceptTouchEvent(event) || result;
            }
        };
        stickersListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return StickerPreviewViewer.getInstance().onTouch(event, stickersListView, 0, stickersOnItemClickListener);
            }
        });
        stickersListView.setDisallowInterceptTouchEvents(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        stickersListView.setLayoutManager(layoutManager);
        stickersListView.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 9) {
            stickersListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        }
        stickersPanel.addView(stickersListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78));
        initStickers();

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.stickers_back_arrow);
        stickersPanel.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 53, 0, 0, 0));

        bottomOverlay = new FrameLayout(context);
        bottomOverlay.setVisibility(View.INVISIBLE);
        bottomOverlay.setFocusable(true);
        bottomOverlay.setFocusableInTouchMode(true);
        bottomOverlay.setClickable(true);
        bottomOverlay.setBackgroundResource(R.drawable.compose_panel);
        bottomOverlay.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        contentView.addView(bottomOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        bottomOverlayText = new TextView(context);
        bottomOverlayText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        bottomOverlayText.setTextColor(Theme.CHAT_BOTTOM_OVERLAY_TEXT_COLOR);
        bottomOverlay.addView(bottomOverlayText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        bottomOverlayChat = new FrameLayout(context);
        bottomOverlayChat.setBackgroundResource(R.drawable.compose_panel);
        bottomOverlayChat.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        bottomOverlayChat.setVisibility(View.INVISIBLE);
        contentView.addView(bottomOverlayChat, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomOverlayChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = null;
                if (currentUser != null && userBlocked) {
                    if (currentUser.bot) {
                        String botUserLast = botUser;
                        botUser = null;
                        MessagesController.getInstance().unblockUser(currentUser.id);
                        if (botUserLast != null && botUserLast.length() != 0) {
                            MessagesController.getInstance().sendBotStart(currentUser, botUserLast);
                        } else {
                            SendMessagesHelper.getInstance().sendMessage("/start", dialog_id, null, null, false, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null, null);
                        }
                    } else {
                        builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureUnblockContact", R.string.AreYouSureUnblockContact));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.getInstance().unblockUser(currentUser.id);
                            }
                        });
                    }
                } else if (currentUser != null && currentUser.bot && botUser != null) {
                    if (botUser.length() != 0) {
                        MessagesController.getInstance().sendBotStart(currentUser, botUser);
                    } else {
                        SendMessagesHelper.getInstance().sendMessage("/start", dialog_id, null, null, false, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null, null);
                    }
                    botUser = null;
                    updateBottomOverlay();
                } else {
                    if (ChatObject.isChannel(currentChat) && !(currentChat instanceof TLRPC.TL_channelForbidden)) {
                        if (ChatObject.isNotInChat(currentChat)) {
                            MessagesController.getInstance().addUserToChat(currentChat.id, UserConfig.getCurrentUser(), null, 0, null, null);
                        } else {
                            toggleMute(true);
                        }
                    } else {
                        builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessagesController.getInstance().deleteDialog(dialog_id, 0);
                                finishFragment();
                            }
                        });
                    }
                }
                if (builder != null) {
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            }
        });

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.CHAT_BOTTOM_CHAT_OVERLAY_TEXT_COLOR);
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        chatAdapter.updateRows();
        if (loading && messages.isEmpty()) {
            progressView.setVisibility(chatAdapter.botInfoRow == -1 ? View.VISIBLE : View.INVISIBLE);
            chatListView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.INVISIBLE);
            chatListView.setEmptyView(emptyViewContainer);
        }

        chatActivityEnterView.setButtons(userBlocked ? null : botButtons);

        if (!AndroidUtilities.isTablet() || AndroidUtilities.isSmallTablet()) {
            contentView.addView(playerView = new PlayerView(context, this), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
        }

        updateContactStatus();
        updateBottomOverlay();
        updateSecretStatus();
        updateSpamView();
        updatePinnedMessageView(true);

        try {
            if (currentEncryptedChat != null && Build.VERSION.SDK_INT >= 23) {
                getParentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
        fixLayoutInternal();

        return fragmentView;
    }

    private void mentionListViewUpdateLayout() {
        if (mentionListView.getChildCount() <= 0 || !mentionsAdapter.isBotContext() || mentionsAdapter.getOrientation() != LinearLayoutManager.VERTICAL) {
            mentionListViewScrollOffsetY = 0;
            mentionListViewLastViewPosition = -1;
            return;
        }
        View child = mentionListView.getChildAt(mentionListView.getChildCount() - 1);
        MentionsAdapter.Holder holder = (MentionsAdapter.Holder) mentionListView.findContainingViewHolder(child);
        if (holder != null) {
            mentionListViewLastViewPosition = holder.getAdapterPosition();
            mentionListViewLastViewTop = child.getTop();
        } else {
            mentionListViewLastViewPosition = -1;
        }

        child = mentionListView.getChildAt(0);
        holder = (MentionsAdapter.Holder) mentionListView.findContainingViewHolder(child);
        int newOffset = child.getTop() > 0 && holder != null && holder.getAdapterPosition() == 0 ? child.getTop() : 0;
        if (mentionListViewScrollOffsetY != newOffset) {
            mentionListView.setTopGlowOffset(mentionListViewScrollOffsetY = newOffset);
            mentionListView.invalidate();
            mentionContainer.invalidate();
        }
    }

    public void processInlineBotContextPM(TLRPC.TL_inlineBotSwitchPM object) {
        if (object == null) {
            return;
        }
        TLRPC.User user = mentionsAdapter.getContextBotUser();
        if (user == null) {
            return;
        }
        chatActivityEnterView.setFieldText("");
        if (dialog_id == user.id) {
            inlineReturn = dialog_id;
            MessagesController.getInstance().sendBotStart(currentUser, object.start_param);
        } else {
            Bundle args = new Bundle();
            args.putInt("user_id", user.id);
            args.putString("inline_query", object.start_param);
            args.putLong("inline_return", dialog_id);
            if (!MessagesController.checkCanOpenChat(args, ChatActivity.this)) {
                return;
            }
            presentFragment(new ChatActivity(args));
        }
    }

    private void createChatAttachView() {
        if (chatAttachView == null) {
            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
            chatAttachView = new ChatAttachView(getParentActivity());
            chatAttachView.setDelegate(new ChatAttachView.ChatAttachViewDelegate() {
                @Override
                public void didPressedButton(int button) {
                    if (button == 7) {
                        chatAttachViewSheet.dismiss();
                        HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = chatAttachView.getSelectedPhotos();
                        if (!selectedPhotos.isEmpty()) {
                            ArrayList<String> photos = new ArrayList<>();
                            ArrayList<String> captions = new ArrayList<>();
                            for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry : selectedPhotos.entrySet()) {
                                MediaController.PhotoEntry photoEntry = entry.getValue();
                                if (photoEntry.imagePath != null) {
                                    photos.add(photoEntry.imagePath);
                                    captions.add(photoEntry.caption != null ? photoEntry.caption.toString() : null);
                                } else if (photoEntry.path != null) {
                                    photos.add(photoEntry.path);
                                    captions.add(photoEntry.caption != null ? photoEntry.caption.toString() : null);
                                }
                                photoEntry.imagePath = null;
                                photoEntry.thumbPath = null;
                                photoEntry.caption = null;
                            }
                            SendMessagesHelper.prepareSendingPhotos(photos, null, dialog_id, replyingMessageObject, captions, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                            showReplyPanel(false, null, null, null, false, true);
                        }
                        return;
                    } else {
                        if (chatAttachViewSheet != null) {
                            chatAttachViewSheet.dismissWithButtonClick(button);
                        }
                    }
                    processSelectedAttach(button);
                }
            });
            builder.setDelegate(new BottomSheet.BottomSheetDelegate() {

                @Override
                public void onRevealAnimationStart(boolean open) {
                    if (chatAttachView != null) {
                        chatAttachView.onRevealAnimationStart(open);
                    }
                }

                @Override
                public void onRevealAnimationProgress(boolean open, float radius, int x, int y) {
                    if (chatAttachView != null) {
                        chatAttachView.onRevealAnimationProgress(open, radius, x, y);
                    }
                }

                @Override
                public void onRevealAnimationEnd(boolean open) {
                    if (chatAttachView != null) {
                        chatAttachView.onRevealAnimationEnd(open);
                    }
                }

                @Override
                public void onOpenAnimationEnd() {
                    if (chatAttachView != null) {
                        chatAttachView.onRevealAnimationEnd(true);
                    }
                }

                @Override
                public View getRevealView() {
                    return menuItem;
                }
            });
            builder.setApplyTopPadding(false);
            builder.setApplyBottomPadding(false);
            builder.setUseRevealAnimation();
            builder.setCustomView(chatAttachView);
            chatAttachViewSheet = builder.create();
        }
    }

    public long getDialogId() {
        return dialog_id;
    }

    public void setBotUser(String value) {
        if (inlineReturn != 0) {
            MessagesController.getInstance().sendBotStart(currentUser, value);
        } else {
            botUser = value;
            updateBottomOverlay();
        }
    }

    public boolean playFirstUnreadVoiceMessage() {
        for (int a = messages.size() - 1; a >= 0; a--) {
            MessageObject messageObject = messages.get(a);
            if (messageObject.isVoice() && messageObject.isContentUnread() && !messageObject.isOut() && messageObject.messageOwner.to_id.channel_id == 0) {
                MediaController.getInstance().setVoiceMessagesPlaylist(MediaController.getInstance().playAudio(messageObject) ? createVoiceMessagesPlaylist(messageObject, true) : null, true);
                return true;
            }
        }
        if (Build.VERSION.SDK_INT >= 23 && getParentActivity() != null) {
            if (getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                return true;
            }
        }
        return false;
    }

    private void initStickers() {
        if (chatActivityEnterView == null || getParentActivity() == null || stickersAdapter != null || currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 23) {
            return;
        }
        if (stickersAdapter != null) {
            stickersAdapter.onDestroy();
        }
        stickersListView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        stickersListView.setAdapter(stickersAdapter = new StickersAdapter(getParentActivity(), new StickersAdapter.StickersAdapterDelegate() {
            @Override
            public void needChangePanelVisibility(final boolean show) {
                if (show && stickersPanel.getVisibility() == View.VISIBLE || !show && stickersPanel.getVisibility() == View.GONE) {
                    return;
                }
                if (show) {
                    stickersListView.scrollToPosition(0);
                    stickersPanel.clearAnimation();
                    stickersPanel.setVisibility(allowStickersPanel ? View.VISIBLE : View.INVISIBLE);
                }
                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (stickersPanel.getVisibility() != View.INVISIBLE) {
                    runningAnimation = new AnimatorSetProxy();
                    runningAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(stickersPanel, "alpha", show ? 0.0f : 1.0f, show ? 1.0f : 0.0f)
                    );
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                if (!show) {
                                    stickersAdapter.clearStickers();
                                    stickersPanel.clearAnimation();
                                    stickersPanel.setVisibility(View.GONE);
                                    if (StickerPreviewViewer.getInstance().isVisible()) {
                                        StickerPreviewViewer.getInstance().close();
                                    }
                                    StickerPreviewViewer.getInstance().reset();
                                }
                                runningAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                runningAnimation = null;
                            }
                        }
                    });
                    runningAnimation.start();
                } else if (!show) {
                    stickersPanel.setVisibility(View.GONE);
                }
            }
        }));
        stickersListView.setOnItemClickListener(stickersOnItemClickListener = new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                TLRPC.Document document = stickersAdapter.getItem(position);
                if (document instanceof TLRPC.TL_document) {
                    SendMessagesHelper.getInstance().sendSticker(document, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                    showReplyPanel(false, null, null, null, false, true);
                }
                chatActivityEnterView.setFieldText("");
            }
        });
    }

    public void shareMyContact(final MessageObject messageObject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("ShareYouPhoneNumberTitle", R.string.ShareYouPhoneNumberTitle));
        if (currentUser != null) {
            if (currentUser.bot) {
                builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfoBot", R.string.AreYouSureShareMyContactInfoBot));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureShareMyContactInfoUser", R.string.AreYouSureShareMyContactInfoUser, PhoneFormat.getInstance().format("+" + UserConfig.getCurrentUser().phone), ContactsController.formatName(currentUser.first_name, currentUser.last_name))));
            }
        } else {
            builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfo", R.string.AreYouSureShareMyContactInfo));
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                SendMessagesHelper.getInstance().sendMessage(UserConfig.getCurrentUser(), dialog_id, messageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null);
                moveScrollToLastMessage();
                showReplyPanel(false, null, null, null, false, true);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showGifHint() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (preferences.getBoolean("gifhint", false)) {
            return;
        }
        preferences.edit().putBoolean("gifhint", true).commit();

        if (getParentActivity() == null || fragmentView == null || gifHintTextView != null) {
            return;
        }
        if (!allowContextBotPanelSecond) {
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setOpenGifsTabFirst();
            }
            return;
        }
        SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
        int index = frameLayout.indexOfChild(chatActivityEnterView);
        if (index == -1) {
            return;
        }
        chatActivityEnterView.setOpenGifsTabFirst();
        emojiButtonRed = new View(getParentActivity());
        emojiButtonRed.setBackgroundResource(R.drawable.redcircle);
        frameLayout.addView(emojiButtonRed, index + 1, LayoutHelper.createFrame(10, 10, Gravity.BOTTOM | Gravity.LEFT, 30, 0, 0, 27));

        gifHintTextView = new TextView(getParentActivity());
        gifHintTextView.setBackgroundResource(R.drawable.tooltip);
        gifHintTextView.setTextColor(Theme.CHAT_GIF_HINT_TEXT_COLOR);
        gifHintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        gifHintTextView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        gifHintTextView.setText(LocaleController.getString("TapHereGifs", R.string.TapHereGifs));
        gifHintTextView.setGravity(Gravity.CENTER_VERTICAL);
        frameLayout.addView(gifHintTextView, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.LEFT | Gravity.BOTTOM, 5, 0, 0, 3));

        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
                ObjectAnimatorProxy.ofFloat(gifHintTextView, "alpha", 0.0f, 1.0f),
                ObjectAnimatorProxy.ofFloat(emojiButtonRed, "alpha", 0.0f, 1.0f)
        );
        animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animation) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gifHintTextView == null) {
                            return;
                        }
                        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
                        animatorSetProxy.playTogether(
                                ObjectAnimatorProxy.ofFloat(gifHintTextView, "alpha", 0.0f)
                        );
                        animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (gifHintTextView != null) {
                                    gifHintTextView.clearAnimation();
                                    gifHintTextView.setVisibility(View.GONE);
                                }
                            }
                        });
                        animatorSetProxy.setDuration(300);
                        animatorSetProxy.start();
                    }
                }, 2000);
            }
        });
        animatorSetProxy.setDuration(300);
        animatorSetProxy.start();
    }

    private void checkContextBotPanel() {
        if (allowStickersPanel && mentionsAdapter != null && mentionsAdapter.isBotContext()) {
            if (!allowContextBotPanel && !allowContextBotPanelSecond) {
                if (mentionContainer.getVisibility() == View.VISIBLE && mentionContainer.getTag() == null) {
                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                    }

                    mentionContainer.setTag(1);
                    mentionListAnimation = new AnimatorSetProxy();
                    mentionListAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(mentionContainer, "alpha", 0.0f)
                    );
                    mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionContainer.clearAnimation();
                                mentionContainer.setVisibility(View.INVISIBLE);
                                mentionListAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionListAnimation = null;
                            }
                        }
                    });
                    mentionListAnimation.setDuration(200);
                    mentionListAnimation.start();
                }
            } else {
                if (mentionContainer.getVisibility() == View.INVISIBLE || mentionContainer.getTag() != null) {
                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                    }
                    mentionContainer.setTag(null);
                    mentionContainer.setVisibility(View.VISIBLE);
                    mentionListAnimation = new AnimatorSetProxy();
                    mentionListAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(mentionContainer, "alpha", 0.0f, 1.0f)
                    );
                    mentionListAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionContainer.clearAnimation();
                                mentionListAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionListAnimation = null;
                            }
                        }
                    });
                    mentionListAnimation.setDuration(200);
                    mentionListAnimation.start();
                }
            }
        }
    }

    private void checkScrollForLoad(boolean scroll) {
        if (chatLayoutManager == null || paused) {
            return;
        }
        int firstVisibleItem = chatLayoutManager.findFirstVisibleItemPosition();
        int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(chatLayoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        if (visibleItemCount > 0) {
            int totalItemCount = chatAdapter.getItemCount();
            int checkLoadCount;
            if (scroll) {
                checkLoadCount = 25;
            } else  {
                checkLoadCount = 5;
            }
            if (firstVisibleItem <= checkLoadCount && !loading) {
                if (!endReached[0]) {
                    loading = true;
                    waitingForLoad.add(lastLoadIndex);
                    if (messagesByDays.size() != 0) {
                        MessagesController.getInstance().loadMessages(dialog_id, 50, maxMessageId[0], !cacheEndReached[0], minDate[0], classGuid, 0, 0, channelMessagesImportant, lastLoadIndex++);
                    } else {
                        MessagesController.getInstance().loadMessages(dialog_id, 50, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, channelMessagesImportant, lastLoadIndex++);
                    }
                } else if (mergeDialogId != 0 && !endReached[1]) {
                    loading = true;
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(mergeDialogId, 50, maxMessageId[1], !cacheEndReached[1], minDate[1], classGuid, 0, 0, 0, lastLoadIndex++);
                }
            }
            if (!loadingForward && firstVisibleItem + visibleItemCount >= totalItemCount - 10) {
                if (mergeDialogId != 0 && !forwardEndReached[1]) {
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(mergeDialogId, 50, minMessageId[1], true, maxDate[1], classGuid, 1, 0, 0, lastLoadIndex++);
                    loadingForward = true;
                } else if (!forwardEndReached[0]) {
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(dialog_id, 50, minMessageId[0], true, maxDate[0], classGuid, 1, 0, channelMessagesImportant, lastLoadIndex++);
                    loadingForward = true;
                }
            }
        }
    }

    private void processSelectedAttach(int which) {
        if (which == attach_photo || which == attach_gallery || which == attach_document || which == attach_video) {
            String action;
            if (currentChat != null) {
                if (currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
                    if (which == attach_photo || which == attach_gallery) {
                        action = "bigchat_upload_photo";
                    } else {
                        action = "bigchat_upload_document";
                    }
                } else {
                    if (which == attach_photo || which == attach_gallery) {
                        action = "chat_upload_photo";
                    } else {
                        action = "chat_upload_document";
                    }
                }
            } else {
                if (which == attach_photo || which == attach_gallery) {
                    action = "pm_upload_photo";
                } else {
                    action = "pm_upload_document";
                }
            }
            if (!MessagesController.isFeatureEnabled(action, ChatActivity.this)) {
                return;
            }
        }

        if (which == attach_photo) {
            try {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File image = AndroidUtilities.generatePicturePath();
                if (image != null) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                    currentPicturePath = image.getAbsolutePath();
                }
                startActivityForResult(takePictureIntent, 0);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else if (which == attach_gallery) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
            PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(false, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46, ChatActivity.this);
            fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
                @Override
                public void didSelectPhotos(ArrayList<String> photos, ArrayList<String> captions, ArrayList<MediaController.SearchImage> webPhotos) {
                    SendMessagesHelper.prepareSendingPhotos(photos, null, dialog_id, replyingMessageObject, captions, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                    SendMessagesHelper.prepareSendingPhotosSearch(webPhotos, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                    showReplyPanel(false, null, null, null, false, true);
                }

                @Override
                public void startPhotoSelectActivity() {
                    try {
                        Intent videoPickerIntent = new Intent();
                        videoPickerIntent.setType("video/*");
                        videoPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
                        videoPickerIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1536));

                        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                        photoPickerIntent.setType("image/*");
                        Intent chooserIntent = Intent.createChooser(photoPickerIntent, null);
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{videoPickerIntent});

                        startActivityForResult(chooserIntent, 1);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                @Override
                public boolean didSelectVideo(String path) {
                    if (Build.VERSION.SDK_INT >= 16) {
                        return !openVideoEditor(path, true, true);
                    } else {
                        SendMessagesHelper.prepareSendingVideo(path, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                        showReplyPanel(false, null, null, null, false, true);
                        return true;
                    }
                }
            });
            presentFragment(fragment);
        } else if (which == attach_video) {
            try {
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                File video = AndroidUtilities.generateVideoPath();
                if (video != null) {
                    if (Build.VERSION.SDK_INT >= 18) {
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                    }
                    takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1536));
                    currentPicturePath = video.getAbsolutePath();
                }
                startActivityForResult(takeVideoIntent, 2);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else if (which == attach_location) {
            if (!AndroidUtilities.isGoogleMapsInstalled(ChatActivity.this)) {
                return;
            }
            LocationActivity fragment = new LocationActivity();
            fragment.setDelegate(new LocationActivity.LocationActivityDelegate() {
                @Override
                public void didSelectLocation(TLRPC.MessageMedia location) {
                    SendMessagesHelper.getInstance().sendMessage(location, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null);
                    moveScrollToLastMessage();
                    showReplyPanel(false, null, null, null, false, true);
                    if (paused) {
                        scrollToTopOnResume = true;
                    }
                }
            });
            presentFragment(fragment);
        } else if (which == attach_document) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
            DocumentSelectActivity fragment = new DocumentSelectActivity();
            fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
                @Override
                public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files) {
                    activity.finishFragment();
                    SendMessagesHelper.prepareSendingDocuments(files, files, null, null, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                    showReplyPanel(false, null, null, null, false, true);
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
        } else if (which == attach_audio) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
            AudioSelectActivity fragment = new AudioSelectActivity();
            fragment.setDelegate(new AudioSelectActivity.AudioSelectActivityDelegate() {
                @Override
                public void didSelectAudio(ArrayList<MessageObject> audios) {
                    SendMessagesHelper.prepareSendingAudioDocuments(audios, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                    showReplyPanel(false, null, null, null, false, true);
                }
            });
            presentFragment(fragment);
        } else if (which == attach_contact) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (getParentActivity().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 5);
                    return;
                }
            }
            try {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(intent, 31);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return !(dialog == chatAttachViewSheet && PhotoViewer.getInstance().isVisible()) && super.dismissDialogOnPause(dialog);
    }

    private void searchLinks(final CharSequence charSequence, final boolean force) {
        if (currentEncryptedChat != null && (MessagesController.getInstance().secretWebpagePreview == 0 || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46)) {
            return;
        }
        if (force && foundWebPage != null) {
            if (foundWebPage.url != null) {
                int index = TextUtils.indexOf(charSequence, foundWebPage.url);
                char lastChar = 0;
                boolean lenEqual = false;
                if (index == -1) {
                    if (foundWebPage.display_url != null) {
                        index = TextUtils.indexOf(charSequence, foundWebPage.display_url);
                        lenEqual = index != -1 && index + foundWebPage.display_url.length() == charSequence.length();
                        lastChar = index != -1 && !lenEqual ? charSequence.charAt(index + foundWebPage.display_url.length()) : 0;
                    }
                } else {
                    lenEqual = index + foundWebPage.url.length() == charSequence.length();
                    lastChar = !lenEqual ? charSequence.charAt(index + foundWebPage.url.length()) : 0;
                }
                if (index != -1 && (lenEqual || lastChar == ' ' || lastChar == ',' || lastChar == '.' || lastChar == '!' || lastChar == '/')) {
                    return;
                }
            }
            pendingLinkSearchString = null;
            showReplyPanel(false, null, null, foundWebPage, false, true);
        }
        Utilities.searchQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (linkSearchRequestId != 0) {
                    ConnectionsManager.getInstance().cancelRequest(linkSearchRequestId, true);
                    linkSearchRequestId = 0;
                }
                ArrayList<CharSequence> urls = null;
                CharSequence textToCheck;
                try {
                    Matcher m = AndroidUtilities.WEB_URL.matcher(charSequence);
                    while (m.find()) {
                        if (m.start() > 0) {
                            if (charSequence.charAt(m.start() - 1) == '@') {
                                continue;
                            }
                        }
                        if (urls == null) {
                            urls = new ArrayList<>();
                        }
                        urls.add(charSequence.subSequence(m.start(), m.end()));
                    }
                    if (urls != null && foundUrls != null && urls.size() == foundUrls.size()) {
                        boolean clear = true;
                        for (int a = 0; a < urls.size(); a++) {
                            if (!TextUtils.equals(urls.get(a), foundUrls.get(a))) {
                                clear = false;
                            }
                        }
                        if (clear) {
                            return;
                        }
                    }
                    foundUrls = urls;
                    if (urls == null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (foundWebPage != null) {
                                    showReplyPanel(false, null, null, foundWebPage, false, true);
                                    foundWebPage = null;
                                }
                            }
                        });
                        return;
                    }
                    textToCheck = TextUtils.join(" ", urls);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    String text = charSequence.toString().toLowerCase();
                    if (charSequence.length() < 13 || !text.contains("http://") && !text.contains("https://")) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (foundWebPage != null) {
                                    showReplyPanel(false, null, null, foundWebPage, false, true);
                                    foundWebPage = null;
                                }
                            }
                        });
                        return;
                    }
                    textToCheck = charSequence;
                }

                if (currentEncryptedChat != null && MessagesController.getInstance().secretWebpagePreview == 2) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    MessagesController.getInstance().secretWebpagePreview = 1;
                                    ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("secretWebpage2", MessagesController.getInstance().secretWebpagePreview).commit();
                                    foundUrls = null;
                                    searchLinks(charSequence, force);
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            builder.setMessage(LocaleController.getString("SecretLinkPreviewAlert", R.string.SecretLinkPreviewAlert));
                            showDialog(builder.create());

                            MessagesController.getInstance().secretWebpagePreview = 0;
                            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("secretWebpage2", MessagesController.getInstance().secretWebpagePreview).commit();
                        }
                    });
                    return;
                }

                final TLRPC.TL_messages_getWebPagePreview req = new TLRPC.TL_messages_getWebPagePreview();
                if (textToCheck instanceof String) {
                    req.message = (String) textToCheck;
                } else {
                    req.message = textToCheck.toString();
                }
                linkSearchRequestId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                linkSearchRequestId = 0;
                                if (error == null) {
                                    if (response instanceof TLRPC.TL_messageMediaWebPage) {
                                        foundWebPage = ((TLRPC.TL_messageMediaWebPage) response).webpage;
                                        if (foundWebPage instanceof TLRPC.TL_webPage || foundWebPage instanceof TLRPC.TL_webPagePending) {
                                            if (foundWebPage instanceof TLRPC.TL_webPagePending) {
                                                pendingLinkSearchString = req.message;
                                            }
                                            if (currentEncryptedChat != null && foundWebPage instanceof TLRPC.TL_webPagePending) {
                                                foundWebPage.url = req.message;
                                            }
                                            showReplyPanel(true, null, null, foundWebPage, false, true);
                                        } else {
                                            if (foundWebPage != null) {
                                                showReplyPanel(false, null, null, foundWebPage, false, true);
                                                foundWebPage = null;
                                            }
                                        }
                                    } else {
                                        if (foundWebPage != null) {
                                            showReplyPanel(false, null, null, foundWebPage, false, true);
                                            foundWebPage = null;
                                        }
                                    }
                                }
                            }
                        });
                    }
                });
                ConnectionsManager.getInstance().bindRequestToGuid(linkSearchRequestId, classGuid);
            }
        });
    }

    private void forwardMessages(ArrayList<MessageObject> arrayList, boolean fromMyName) {
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        if (!fromMyName) {
            SendMessagesHelper.getInstance().sendMessage(arrayList, dialog_id, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
        } else {
            for (MessageObject object : arrayList) {
                SendMessagesHelper.getInstance().processForwardFromMyName(object, dialog_id, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
            }
        }
    }

    public void showReplyPanel(boolean show, MessageObject messageObject, ArrayList<MessageObject> messageObjects, TLRPC.WebPage webPage, boolean cancel, boolean animated) {
        if (chatActivityEnterView == null) {
            return;
        }
        if (show) {
            if (messageObject == null && messageObjects == null && webPage == null) {
                return;
            }
            boolean openKeyboard = false;
            if (messageObject != null && messageObject.getDialogId() != dialog_id) {
                messageObjects = new ArrayList<>();
                messageObjects.add(messageObject);
                messageObject = null;
                openKeyboard = true;
            }
            if (messageObject != null) {
                String name;
                if (messageObject.isFromUser()) {
                    TLRPC.User user = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
                    if (user == null) {
                        return;
                    }
                    name = UserObject.getUserName(user);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(messageObject.messageOwner.to_id.channel_id);
                    if (chat == null) {
                        return;
                    }
                    name = chat.title;
                }

                forwardingMessages = null;
                replyingMessageObject = messageObject;
                chatActivityEnterView.setReplyingMessageObject(messageObject);
                if (foundWebPage != null) {
                    return;
                }
                replyIconImageView.setImageResource(R.drawable.reply);
                replyNameTextView.setText(name);
                if (messageObject.messageText != null) {
                    String mess = messageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    replyObjectTextView.setText(Emoji.replaceEmoji(mess, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                }
            } else if (messageObjects != null) {
                if (messageObjects.isEmpty()) {
                    return;
                }
                replyingMessageObject = null;
                chatActivityEnterView.setReplyingMessageObject(null);
                forwardingMessages = messageObjects;

                if (foundWebPage != null) {
                    return;
                }
                chatActivityEnterView.setForceShowSendButton(true, animated);
                ArrayList<Integer> uids = new ArrayList<>();
                replyIconImageView.setImageResource(R.drawable.forward_blue);
                MessageObject object = messageObjects.get(0);
                if (object.isFromUser()) {
                    uids.add(object.messageOwner.from_id);
                } else {
                    uids.add(-object.messageOwner.to_id.channel_id);
                }
                int type = messageObjects.get(0).type;
                for (int a = 1; a < messageObjects.size(); a++) {
                    object = messageObjects.get(a);
                    Integer uid;
                    if (object.isFromUser()) {
                        uid = object.messageOwner.from_id;
                    } else {
                        uid = -object.messageOwner.to_id.channel_id;
                    }
                    if (!uids.contains(uid)) {
                        uids.add(uid);
                    }
                    if (messageObjects.get(a).type != type) {
                        type = -1;
                    }
                }
                StringBuilder userNames = new StringBuilder();
                for (int a = 0; a < uids.size(); a++) {
                    Integer uid = uids.get(a);
                    TLRPC.Chat chat = null;
                    TLRPC.User user = null;
                    if (uid > 0) {
                        user = MessagesController.getInstance().getUser(uid);
                    } else {
                        chat = MessagesController.getInstance().getChat(-uid);
                    }
                    if (user == null && chat == null) {
                        continue;
                    }
                    if (uids.size() == 1) {
                        if (user != null) {
                            userNames.append(UserObject.getUserName(user));
                        } else {
                            userNames.append(chat.title);
                        }
                    } else if (uids.size() == 2 || userNames.length() == 0) {
                        if (userNames.length() > 0) {
                            userNames.append(", ");
                        }
                        if (user != null) {
                            if (user.first_name != null && user.first_name.length() > 0) {
                                userNames.append(user.first_name);
                            } else if (user.last_name != null && user.last_name.length() > 0) {
                                userNames.append(user.last_name);
                            } else {
                                userNames.append(" ");
                            }
                        } else {
                            userNames.append(chat.title);
                        }
                    } else {
                        userNames.append(" ");
                        userNames.append(LocaleController.formatPluralString("AndOther", uids.size() - 1));
                        break;
                    }
                }
                replyNameTextView.setText(userNames);
                if (type == -1 || type == 0 || type == 10 || type == 11) {
                    if (messageObjects.size() == 1 && messageObjects.get(0).messageText != null) {
                        String mess = messageObjects.get(0).messageText.toString();
                        if (mess.length() > 150) {
                            mess = mess.substring(0, 150);
                        }
                        mess = mess.replace('\n', ' ');
                        replyObjectTextView.setText(Emoji.replaceEmoji(mess, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                    } else {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedMessage", messageObjects.size()));
                    }
                } else {
                    if (type == 1) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedPhoto", messageObjects.size()));
                        if (messageObjects.size() == 1) {
                            messageObject = messageObjects.get(0);
                        }
                    } else if (type == 4) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedLocation", messageObjects.size()));
                    } else if (type == 3) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedVideo", messageObjects.size()));
                        if (messageObjects.size() == 1) {
                            messageObject = messageObjects.get(0);
                        }
                    } else if (type == 12) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedContact", messageObjects.size()));
                    } else if (type == 2) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedAudio", messageObjects.size()));
                    } else if (type == 14) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedMusic", messageObjects.size()));
                    } else if (type == 13) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedSticker", messageObjects.size()));
                    } else if (type == 8 || type == 9) {
                        if (messageObjects.size() == 1) {
                            if (type == 8) {
                                replyObjectTextView.setText(LocaleController.getString("AttachGif", R.string.AttachGif));
                            } else {
                                String name;
                                if ((name = FileLoader.getDocumentFileName(messageObjects.get(0).getDocument())).length() != 0) {
                                    replyObjectTextView.setText(name);
                                }
                                messageObject = messageObjects.get(0);
                            }
                        } else {
                            replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedFile", messageObjects.size()));
                        }
                    }
                }
            } else {
                replyIconImageView.setImageResource(R.drawable.link);
                if (webPage instanceof TLRPC.TL_webPagePending) {
                    replyNameTextView.setText(LocaleController.getString("GettingLinkInfo", R.string.GettingLinkInfo));
                    replyObjectTextView.setText(pendingLinkSearchString);
                } else {
                    if (webPage.site_name != null) {
                        replyNameTextView.setText(webPage.site_name);
                    } else if (webPage.title != null) {
                        replyNameTextView.setText(webPage.title);
                    } else {
                        replyNameTextView.setText(LocaleController.getString("LinkPreview", R.string.LinkPreview));
                    }
                    if (webPage.description != null) {
                        replyObjectTextView.setText(webPage.description);
                    } else if (webPage.title != null && webPage.site_name != null) {
                        replyObjectTextView.setText(webPage.title);
                    } else if (webPage.author != null) {
                        replyObjectTextView.setText(webPage.author);
                    } else {
                        replyObjectTextView.setText(webPage.display_url);
                    }
                    chatActivityEnterView.setWebPage(webPage, true);
                }
            }
            FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) replyNameTextView.getLayoutParams();
            FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) replyObjectTextView.getLayoutParams();
            TLRPC.PhotoSize photoSize = messageObject != null ? FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80) : null;
            if (photoSize == null || photoSize instanceof TLRPC.TL_photoSizeEmpty || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable || messageObject.type == 13 || messageObject != null && messageObject.isSecretMedia()) {
                replyImageView.setImageBitmap(null);
                replyImageLocation = null;
                replyImageView.setVisibility(View.INVISIBLE);
                layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(52);
            } else {
                replyImageLocation = photoSize.location;
                replyImageView.setImage(replyImageLocation, "50_50", (Drawable) null);
                replyImageView.setVisibility(View.VISIBLE);
                layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(96);
            }
            replyNameTextView.setLayoutParams(layoutParams1);
            replyObjectTextView.setLayoutParams(layoutParams2);
            chatActivityEnterView.showTopView(animated, openKeyboard);
        } else {
            if (replyingMessageObject == null && forwardingMessages == null && foundWebPage == null) {
                return;
            }
            if (replyingMessageObject != null && replyingMessageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardForceReply) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                preferences.edit().putInt("answered_" + dialog_id, replyingMessageObject.getId()).commit();
            }
            if (foundWebPage != null) {
                foundWebPage = null;
                chatActivityEnterView.setWebPage(null, !cancel);
                if (webPage != null && (replyingMessageObject != null || forwardingMessages != null)) {
                    showReplyPanel(true, replyingMessageObject, forwardingMessages, null, false, true);
                    return;
                }
            }
            if (forwardingMessages != null) {
                forwardMessages(forwardingMessages, false);
            }
            chatActivityEnterView.setForceShowSendButton(false, animated);
            chatActivityEnterView.hideTopView(animated);
            chatActivityEnterView.setReplyingMessageObject(null);
            replyingMessageObject = null;
            forwardingMessages = null;
            replyImageLocation = null;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            preferences.edit().remove("reply_" + dialog_id).commit();
        }
    }

    private void moveScrollToLastMessage() {
        if (chatListView != null) {
            chatLayoutManager.scrollToPositionWithOffset(messages.size() - 1, -100000 - chatListView.getPaddingTop());
        }
    }

    private boolean sendSecretMessageRead(MessageObject messageObject) {
        if (messageObject == null || messageObject.isOut() || !messageObject.isSecretMedia() || messageObject.messageOwner.destroyTime != 0 || messageObject.messageOwner.ttl <= 0) {
            return false;
        }
        MessagesController.getInstance().markMessageAsRead(dialog_id, messageObject.messageOwner.random_id, messageObject.messageOwner.ttl);
        messageObject.messageOwner.destroyTime = messageObject.messageOwner.ttl + ConnectionsManager.getInstance().getCurrentTime();
        return true;
    }

    private void clearChatData() {
        messages.clear();
        messagesByDays.clear();
        waitingForLoad.clear();

        progressView.setVisibility(chatAdapter.botInfoRow == -1 ? View.VISIBLE : View.INVISIBLE);
        chatListView.setEmptyView(null);
        for (int a = 0; a < 2; a++) {
            messagesDict[a].clear();
            if (currentEncryptedChat == null) {
                maxMessageId[a] = Integer.MAX_VALUE;
                minMessageId[a] = Integer.MIN_VALUE;
            } else {
                maxMessageId[a] = Integer.MIN_VALUE;
                minMessageId[a] = Integer.MAX_VALUE;
            }
            maxDate[a] = Integer.MIN_VALUE;
            minDate[a] = 0;
            endReached[a] = false;
            cacheEndReached[a] = false;
            forwardEndReached[a] = true;
        }
        first = true;
        firstLoading = true;
        loading = true;
        waitingForImportantLoad = false;
        waitingForReplyMessageLoad = false;
        startLoadFromMessageId = 0;
        last_message_id = 0;
        needSelectFromMessageId = false;
        chatAdapter.notifyDataSetChanged();
    }

    private void scrollToLastMessage(boolean pagedown) {
        if (forwardEndReached[0] && first_unread_id == 0 && startLoadFromMessageId == 0) {
            if (pagedown && chatLayoutManager.findLastCompletelyVisibleItemPosition() == chatAdapter.getItemCount() - 1) {
                showPagedownButton(false, true);
                highlightMessageId = Integer.MAX_VALUE;
                updateVisibleRows();
            } else {
                chatLayoutManager.scrollToPositionWithOffset(messages.size() - 1, -100000 - chatListView.getPaddingTop());
            }
        } else {
            clearChatData();
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, true, 0, classGuid, 0, 0, channelMessagesImportant, lastLoadIndex++);
        }
    }

    private void updateMessagesVisisblePart() {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                messageCell.getLocalVisibleRect(scrollRect);
                messageCell.setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
            }
        }
    }

    private void toggleMute(boolean instant) {
        boolean muted = MessagesController.getInstance().isDialogMuted(dialog_id);
        if (!muted) {
            if (instant) {
                long flags;
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("notify2_" + dialog_id, 2);
                flags = 1;
                MessagesStorage.getInstance().setDialogFlags(dialog_id, flags);
                editor.commit();
                TLRPC.Dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
                if (dialog != null) {
                    dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                    dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                }
                NotificationsController.updateServerNotificationsSettings(dialog_id);
                NotificationsController.getInstance().removeNotificationsForDialog(dialog_id);
            } else {
                showDialog(AlertsCreator.createMuteAlert(getParentActivity(), dialog_id));
            }
        } else {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("notify2_" + dialog_id, 0);
            MessagesStorage.getInstance().setDialogFlags(dialog_id, 0);
            editor.commit();
            TLRPC.Dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
            if (dialog != null) {
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            }
            NotificationsController.updateServerNotificationsSettings(dialog_id);
        }
    }

    private void scrollToMessageId(int id, int fromMessageId, boolean select, int loadIndex) {
        MessageObject object = messagesDict[loadIndex].get(id);
        boolean query = false;
        if (object != null) {
            int index = messages.indexOf(object);
            if (index != -1) {
                if (select) {
                    highlightMessageId = id;
                } else {
                    highlightMessageId = Integer.MAX_VALUE;
                }
                final int yOffset = Math.max(0, (chatListView.getHeight() - object.getApproximateHeight()) / 2);
                if (messages.get(messages.size() - 1) == object) {
                    chatLayoutManager.scrollToPositionWithOffset(0, -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
                } else {
                    chatLayoutManager.scrollToPositionWithOffset(chatAdapter.messagesStartRow + messages.size() - messages.indexOf(object) - 1, -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
                }
                updateVisibleRows();
                boolean found = false;
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatBaseCell) {
                        ChatBaseCell cell = (ChatBaseCell) view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().getId() == object.getId()) {
                            found = true;
                            break;
                        }
                    } else if (view instanceof ChatActionCell) {
                        ChatActionCell cell = (ChatActionCell) view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().getId() == object.getId()) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    showPagedownButton(true, true);
                }
            } else {
                query = true;
            }
        } else {
            query = true;
        }

        if (query) {
            if (currentEncryptedChat != null && !MessagesStorage.getInstance().checkMessageId(dialog_id, startLoadFromMessageId)) {
                return;
            }
            /*clearChatData();
            loadsCount = 0;
            unread_to_load = 0;
            first_unread_id = 0;
            loadingForward = false;
            unreadMessageObject = null;
            scrollToMessage = null;*/

            waitingForReplyMessageLoad = true;
            highlightMessageId = Integer.MAX_VALUE;
            scrollToMessagePosition = -10000;
            startLoadFromMessageId = id;
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(loadIndex == 0 ? dialog_id : mergeDialogId, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, true, 0, classGuid, 3, 0, loadIndex == 0 ? channelMessagesImportant : 0, lastLoadIndex++);
            //emptyViewContainer.setVisibility(View.INVISIBLE);
        }
        returnToMessageId = fromMessageId;
        returnToLoadIndex = loadIndex;
        needSelectFromMessageId = select;
    }

    private void showPagedownButton(boolean show, boolean animated) {
        if (pagedownButton == null) {
            return;
        }
        if (show) {
            if (pagedownButton.getTag() == null) {
                if (pagedownButtonAnimation != null) {
                    pagedownButtonAnimation.cancel();
                    pagedownButtonAnimation = null;
                }
                if (animated) {
                    if (ViewProxy.getTranslationY(pagedownButton) == 0) {
                        ViewProxy.setTranslationY(pagedownButton, AndroidUtilities.dp(100));
                    }
                    pagedownButton.setVisibility(View.VISIBLE);
                    pagedownButton.setTag(1);
                    pagedownButtonAnimation = ObjectAnimatorProxy.ofFloatProxy(pagedownButton, "translationY", 0).setDuration(200).start();
                } else {
                    pagedownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            returnToMessageId = 0;
            if (pagedownButton.getTag() != null) {
                pagedownButton.setTag(null);
                if (pagedownButtonAnimation != null) {
                    pagedownButtonAnimation.cancel();
                    pagedownButtonAnimation = null;
                }
                if (animated) {
                    pagedownButtonAnimation = ObjectAnimatorProxy.ofFloatProxy(pagedownButton, "translationY", AndroidUtilities.dp(100)).setDuration(200).addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            pagedownButton.clearAnimation();
                            pagedownButton.setVisibility(View.INVISIBLE);
                        }
                    }).start();
                } else {
                    pagedownButton.clearAnimation();
                    pagedownButton.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void updateSecretStatus() {
        if (bottomOverlay == null) {
            return;
        }
        if (currentEncryptedChat == null || secretViewStatusTextView == null) {
            bottomOverlay.setVisibility(View.INVISIBLE);
            return;
        }
        boolean hideKeyboard = false;
        if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
            bottomOverlayText.setText(LocaleController.getString("EncryptionProcessing", R.string.EncryptionProcessing));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
            bottomOverlayText.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, "<b>" + currentUser.first_name + "</b>")));
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
            bottomOverlay.setVisibility(View.INVISIBLE);
        }
        checkRaiseSensors();
        if (hideKeyboard) {
            chatActivityEnterView.hidePopup(false);
            if (getParentActivity() != null) {
                AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
            }
        }
        checkActionBarMenu();
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
        if (mentionsAdapter != null) {
            mentionsAdapter.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
    }

    private void checkActionBarMenu() {
        if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat) ||
                currentChat != null && ChatObject.isNotInChat(currentChat) ||
                currentUser != null && UserObject.isDeleted(currentUser)) {
            if (menuItem != null) {
                menuItem.setVisibility(View.GONE);
            }
            if (timeItem2 != null) {
                timeItem2.setVisibility(View.GONE);
            }
            if (avatarContainer != null) {
                avatarContainer.hideTimeItem();
            }
        } else {
            if (menuItem != null) {
                menuItem.setVisibility(View.VISIBLE);
            }
            if (timeItem2 != null) {
                timeItem2.setVisibility(View.VISIBLE);
            }
            if (avatarContainer != null) {
                avatarContainer.showTimeItem();
            }
        }
        if (avatarContainer != null && currentEncryptedChat != null) {
            avatarContainer.setTime(currentEncryptedChat.ttl);
        }
        checkAndUpdateAvatar();
    }

    private int getMessageType(MessageObject messageObject) {
        if (messageObject == null) {
            return -1;
        }
        if (currentEncryptedChat == null) {
            boolean isBroadcastError = isBroadcast && messageObject.getId() <= 0 && messageObject.isSendError();
            if (!isBroadcast && messageObject.getId() <= 0 && messageObject.isOut() || isBroadcastError) {
                if (messageObject.isSendError()) {
                    if (!messageObject.isMediaEmpty()) {
                        return 0;
                    } else {
                        return 20;
                    }
                } else {
                    return -1;
                }
            } else {
                if (messageObject.type == 6) {
                    return -1;
                } else if (messageObject.type == 10 || messageObject.type == 11) {
                    if (messageObject.getId() == 0) {
                        return -1;
                    }
                    return 1;
                } else {
                    if (messageObject.isVoice()) {
                        return 2;
                    } else if (messageObject.isSticker()) {
                        TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                        if (inputStickerSet instanceof TLRPC.TL_inputStickerSetID) {
                            if (!StickersQuery.isStickerPackInstalled(inputStickerSet.id)) {
                                return 7;
                            }
                        } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                            if (!StickersQuery.isStickerPackInstalled(inputStickerSet.short_name)) {
                                return 7;
                            }
                        }
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.getDocument() != null || messageObject.isMusic() || messageObject.isVideo()) {
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
                            if (messageObject.getDocument() != null) {
                                String mime = messageObject.getDocument().mime_type;
                                if (mime != null) {
                                    if (mime.endsWith("/xml")) {
                                        return 5;
                                    } else if (mime.endsWith("/png") || mime.endsWith("/jpg") || mime.endsWith("/jpeg")) {
                                        return 6;
                                    }
                                }
                            }
                            return 4;
                        }
                    } else if (messageObject.type == 12) {
                        return 8;
                    } else if (messageObject.isMediaEmpty()) {
                        return 3;
                    }
                    return 2;
                }
            }
        } else {
            if (messageObject.isSending()) {
                return -1;
            }
            if (messageObject.type == 6) {
                return -1;
            } else if (messageObject.isSendError()) {
                if (!messageObject.isMediaEmpty()) {
                    return 0;
                } else {
                    return 20;
                }
            } else if (messageObject.type == 10 || messageObject.type == 11) {
                if (messageObject.getId() == 0 || messageObject.isSending()) {
                    return -1;
                } else {
                    return 1;
                }
            } else {
                if (messageObject.isVoice()) {
                    return 2;
                } else if (messageObject.isSticker()) {
                    TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                    if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                        if (!StickersQuery.isStickerPackInstalled(inputStickerSet.short_name)) {
                            return 7;
                        }
                    }
                } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.getDocument() != null || messageObject.isMusic() || messageObject.isVideo()) {
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
                        if (messageObject.getDocument() != null) {
                            String mime = messageObject.getDocument().mime_type;
                            if (mime != null && mime.endsWith("text/xml")) {
                                return 5;
                            }
                        }
                        if (messageObject.messageOwner.ttl <= 0) {
                            return 4;
                        }
                    }
                } else if (messageObject.type == 12) {
                    return 8;
                } else if (messageObject.isMediaEmpty()) {
                    return 3;
                }
                return 2;
            }
        }
    }

    private void addToSelectedMessages(MessageObject messageObject) {
        int index = messageObject.getDialogId() == dialog_id ? 0 : 1;
        if (selectedMessagesIds[index].containsKey(messageObject.getId())) {
            selectedMessagesIds[index].remove(messageObject.getId());
            if (messageObject.type == 0 || messageObject.caption != null) {
                selectedMessagesCanCopyIds[index].remove(messageObject.getId());
            }
            if (!messageObject.canDeleteMessage(currentChat)) {
                cantDeleteMessagesCount--;
            }
        } else {
            selectedMessagesIds[index].put(messageObject.getId(), messageObject);
            if (messageObject.type == 0 || messageObject.caption != null) {
                selectedMessagesCanCopyIds[index].put(messageObject.getId(), messageObject);
            }
            if (!messageObject.canDeleteMessage(currentChat)) {
                cantDeleteMessagesCount++;
            }
        }
        if (actionBar.isActionModeShowed()) {
            if (selectedMessagesIds[0].isEmpty() && selectedMessagesIds[1].isEmpty()) {
                actionBar.hideActionMode();
                updatePinnedMessageView(true);
            } else {
                int copyVisible = actionBar.createActionMode().getItem(copy).getVisibility();
                actionBar.createActionMode().getItem(copy).setVisibility(selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);
                int newCopyVisible = actionBar.createActionMode().getItem(copy).getVisibility();
                actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
                final ActionBarMenuItem replyItem = actionBar.createActionMode().getItem(reply);
                if (replyItem != null) {
                    boolean allowChatActions = true;
                    if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 || isBroadcast || currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !currentChat.creator && !currentChat.editor && !currentChat.megagroup)) {
                        allowChatActions = false;
                    }
                    final int newVisibility = allowChatActions && selectedMessagesIds[0].size() + selectedMessagesIds[1].size() == 1 ? View.VISIBLE : View.GONE;
                    if (replyItem.getVisibility() != newVisibility) {
                        if (replyButtonAnimation != null) {
                            replyButtonAnimation.cancel();
                        }
                        if (copyVisible != newCopyVisible) {
                            if (newVisibility == View.VISIBLE) {
                                ViewProxy.setAlpha(replyItem, 1.0f);
                                ViewProxy.setScaleX(replyItem, 1.0f);
                            } else {
                                ViewProxy.setAlpha(replyItem, 0.0f);
                                ViewProxy.setScaleX(replyItem, 0.0f);
                            }
                            replyItem.setVisibility(newVisibility);
                            replyItem.clearAnimation();
                        } else {
                            replyButtonAnimation = new AnimatorSetProxy();
                            ViewProxy.setPivotX(replyItem, AndroidUtilities.dp(54));
                            if (newVisibility == View.VISIBLE) {
                                replyItem.setVisibility(newVisibility);
                                replyButtonAnimation.playTogether(
                                        ObjectAnimatorProxy.ofFloat(replyItem, "alpha", 1.0f),
                                        ObjectAnimatorProxy.ofFloat(replyItem, "scaleX", 1.0f)
                                );
                            } else {
                                replyButtonAnimation.playTogether(
                                        ObjectAnimatorProxy.ofFloat(replyItem, "alpha", 0.0f),
                                        ObjectAnimatorProxy.ofFloat(replyItem, "scaleX", 0.0f)
                                );
                            }
                            replyButtonAnimation.setDuration(100);
                            replyButtonAnimation.addListener(new AnimatorListenerAdapterProxy() {
                                @Override
                                public void onAnimationEnd(Object animation) {
                                    if (replyButtonAnimation != null && replyButtonAnimation.equals(animation)) {
                                        replyItem.clearAnimation();
                                        if (newVisibility == View.GONE) {
                                            replyItem.setVisibility(View.GONE);
                                        }
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Object animation) {
                                    if (replyButtonAnimation != null && replyButtonAnimation.equals(animation)) {
                                        replyButtonAnimation = null;
                                    }
                                }
                            });
                            replyButtonAnimation.start();
                        }
                    }
                }
            }
        }
    }

    private void processRowSelect(View view) {
        MessageObject message = null;
        if (view instanceof ChatBaseCell) {
            message = ((ChatBaseCell) view).getMessageObject();
        } else if (view instanceof ChatActionCell) {
            message = ((ChatActionCell) view).getMessageObject();
        }

        int type = getMessageType(message);

        if (type < 2 || type == 20) {
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
        if (!selectedMessagesIds[0].isEmpty() || !selectedMessagesIds[1].isEmpty()) {
            selectedMessagesCountTextView.setNumber(selectedMessagesIds[0].size() + selectedMessagesIds[1].size(), true);
        }
    }

    private void updateTitle() {
        if (avatarContainer == null) {
            return;
        }
        if (currentChat != null) {
            avatarContainer.setTitle(currentChat.title);
        } else if (currentUser != null) {
            if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    avatarContainer.setTitle(PhoneFormat.getInstance().format("+" + currentUser.phone));
                } else {
                    avatarContainer.setTitle(UserObject.getUserName(currentUser));
                }
            } else {
                avatarContainer.setTitle(UserObject.getUserName(currentUser));
            }
        }
    }

    private void updateBotButtons() {
        if (headerItem == null || currentUser == null || currentEncryptedChat != null || !currentUser.bot) {
            return;
        }
        boolean hasHelp = false;
        boolean hasSettings = false;
        if (!botInfo.isEmpty()) {
            for (HashMap.Entry<Integer, TLRPC.BotInfo> entry : botInfo.entrySet()) {
                TLRPC.BotInfo info = entry.getValue();
                for (int a = 0; a < info.commands.size(); a++) {
                    TLRPC.TL_botCommand command = info.commands.get(a);
                    if (command.command.toLowerCase().equals("help")) {
                        hasHelp = true;
                    } else if (command.command.toLowerCase().equals("settings")) {
                        hasSettings = true;
                    }
                    if (hasSettings && hasHelp) {
                        break;
                    }
                }
            }
        }
        if (hasHelp) {
            headerItem.showSubItem(bot_help);
        } else {
            headerItem.hideSubItem(bot_help);
        }
        if (hasSettings) {
            headerItem.showSubItem(bot_settings);
        } else {
            headerItem.hideSubItem(bot_settings);
        }
    }

    private void updateTitleIcons() {
        if (avatarContainer == null) {
            return;
        }
        int rightIcon = MessagesController.getInstance().isDialogMuted(dialog_id) ? R.drawable.mute_fixed : 0;
        avatarContainer.setTitleIcons(currentEncryptedChat != null ? R.drawable.ic_lock_header : 0, rightIcon);
        if (rightIcon != 0) {
            muteItem.setText(LocaleController.getString("UnmuteNotifications", R.string.UnmuteNotifications));
        } else {
            muteItem.setText(LocaleController.getString("MuteNotifications", R.string.MuteNotifications));
        }
    }

    private void checkAndUpdateAvatar() {
        if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
        } else if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
        }
        if (avatarContainer != null) {
            avatarContainer.checkAndUpdateAvatar();
        }
    }

    public boolean openVideoEditor(String videoPath, boolean removeLast, boolean animated) {
        Bundle args = new Bundle();
        args.putString("videoPath", videoPath);
        VideoEditorActivity fragment = new VideoEditorActivity(args);
        fragment.setDelegate(new VideoEditorActivity.VideoEditorActivityDelegate() {
            @Override
            public void didFinishEditVideo(String videoPath, long startTime, long endTime, int resultWidth, int resultHeight, int rotationValue, int originalWidth, int originalHeight, int bitrate, long estimatedSize, long estimatedDuration) {
                VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
                videoEditedInfo.startTime = startTime;
                videoEditedInfo.endTime = endTime;
                videoEditedInfo.rotationValue = rotationValue;
                videoEditedInfo.originalWidth = originalWidth;
                videoEditedInfo.originalHeight = originalHeight;
                videoEditedInfo.bitrate = bitrate;
                videoEditedInfo.resultWidth = resultWidth;
                videoEditedInfo.resultHeight = resultHeight;
                videoEditedInfo.originalPath = videoPath;
                SendMessagesHelper.prepareSendingVideo(videoPath, estimatedSize, estimatedDuration, resultWidth, resultHeight, videoEditedInfo, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                showReplyPanel(false, null, null, null, false, true);
            }
        });

        if (parentLayout == null || !fragment.onFragmentCreate()) {
            SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
            showReplyPanel(false, null, null, null, false, true);
            return false;
        }
        parentLayout.presentFragment(fragment, removeLast, !animated, true);
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
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                final ArrayList<Object> arrayList = new ArrayList<>();
                int orientation = 0;
                try {
                    ExifInterface ei = new ExifInterface(currentPicturePath);
                    int exif = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch (exif) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            orientation = 90;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            orientation = 180;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            orientation = 270;
                            break;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                arrayList.add(new MediaController.PhotoEntry(0, 0, 0, currentPicturePath, orientation, false));

                PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, 2, new PhotoViewer.EmptyPhotoViewerProvider() {
                    @Override
                    public void sendButtonPressed(int index) {
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) arrayList.get(0);
                        if (photoEntry.imagePath != null) {
                            SendMessagesHelper.prepareSendingPhoto(photoEntry.imagePath, null, dialog_id, replyingMessageObject, photoEntry.caption, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                            showReplyPanel(false, null, null, null, false, true);
                        } else if (photoEntry.path != null) {
                            SendMessagesHelper.prepareSendingPhoto(photoEntry.path, null, dialog_id, replyingMessageObject, photoEntry.caption, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                            showReplyPanel(false, null, null, null, false, true);
                        }
                    }
                }, this);
                AndroidUtilities.addMediaToGallery(currentPicturePath);
                currentPicturePath = null;
            } else if (requestCode == 1) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                Uri uri = data.getData();
                if (uri.toString().contains("video")) {
                    String videoPath = null;
                    try {
                        videoPath = AndroidUtilities.getPath(uri);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    if (videoPath == null) {
                        showAttachmentError();
                    }
                    if (Build.VERSION.SDK_INT >= 16) {
                        if (paused) {
                            startVideoEdit = videoPath;
                        } else {
                            openVideoEditor(videoPath, false, false);
                        }
                    } else {
                        SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                        showReplyPanel(false, null, null, null, false, true);
                    }
                } else {
                    SendMessagesHelper.prepareSendingPhoto(null, uri, dialog_id, replyingMessageObject, null, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                }
                showReplyPanel(false, null, null, null, false, true);
            } else if (requestCode == 2) {
                String videoPath = null;
                FileLog.d("tmessages", "pic path " + currentPicturePath);
                if (data != null && currentPicturePath != null) {
                    if (new File(currentPicturePath).exists()) {
                        data = null;
                    }
                }
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        FileLog.d("tmessages", "video record uri " + uri.toString());
                        videoPath = AndroidUtilities.getPath(uri);
                        FileLog.d("tmessages", "resolved path = " + videoPath);
                        if (!(new File(videoPath).exists())) {
                            videoPath = currentPicturePath;
                        }
                    } else {
                        videoPath = currentPicturePath;
                    }
                    AndroidUtilities.addMediaToGallery(currentPicturePath);
                    currentPicturePath = null;
                }
                if (videoPath == null && currentPicturePath != null) {
                    File f = new File(currentPicturePath);
                    if (f.exists()) {
                        videoPath = currentPicturePath;
                    }
                    currentPicturePath = null;
                }
                if (Build.VERSION.SDK_INT >= 16) {
                    if (paused) {
                        startVideoEdit = videoPath;
                    } else {
                        openVideoEditor(videoPath, false, false);
                    }
                } else {
                    SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                    showReplyPanel(false, null, null, null, false, true);
                }
            } else if (requestCode == 21) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                Uri uri = data.getData();

                String extractUriFrom = uri.toString();
                if (extractUriFrom.contains("com.google.android.apps.photos.contentprovider")) {
                    try {
                        String firstExtraction = extractUriFrom.split("/1/")[1];
                        int index = firstExtraction.indexOf("/ACTUAL");
                        if (index != -1) {
                            firstExtraction = firstExtraction.substring(0, index);
                            String secondExtraction = URLDecoder.decode(firstExtraction, "UTF-8");
                            uri = Uri.parse(secondExtraction);
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
                String tempPath = AndroidUtilities.getPath(uri);
                String originalPath = tempPath;
                if (tempPath == null) {
                    originalPath = data.toString();
                    tempPath = MediaController.copyFileToCache(data.getData(), "file");
                }
                if (tempPath == null) {
                    showAttachmentError();
                    return;
                }
                SendMessagesHelper.prepareSendingDocument(tempPath, originalPath, null, null, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin());
                showReplyPanel(false, null, null, null, false, true);
            } else if (requestCode == 31) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                Uri uri = data.getData();
                Cursor c = null;
                try {
                    c = getParentActivity().getContentResolver().query(uri, new String[]{ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
                    if (c != null) {
                        boolean sent = false;
                        while (c.moveToNext()) {
                            sent = true;
                            String name = c.getString(0);
                            String number = c.getString(1);
                            TLRPC.User user = new TLRPC.User();
                            user.first_name = name;
                            user.last_name = "";
                            user.phone = number;
                            SendMessagesHelper.getInstance().sendMessage(user, dialog_id, replyingMessageObject, chatActivityEnterView == null || chatActivityEnterView.asAdmin(), null, null);
                        }
                        if (sent) {
                            showReplyPanel(false, null, null, null, false, true);
                        }
                    }
                } finally {
                    try {
                        if (c != null && !c.isClosed()) {
                            c.close();
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
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

    private void removeUnreadPlane() {
        if (unreadMessageObject != null) {
            forwardEndReached[0] = forwardEndReached[1] = true;
            first_unread_id = 0;
            last_message_id = 0;
            unread_to_load = 0;
            if (chatAdapter != null) {
                chatAdapter.removeMessageObject(unreadMessageObject);
            } else {
                messages.remove(unreadMessageObject);
            }
            unreadMessageObject = null;
        }
    }

    public boolean processSendingText(String text) {
        return chatActivityEnterView.processSendingText(text);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.messagesDidLoaded) {
            int guid = (Integer) args[11];
            if (guid == classGuid) {
                if (!openAnimationEnded) {
                    NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoaded, NotificationCenter.dialogsNeedReload,
                            NotificationCenter.closeChats, NotificationCenter.botKeyboardDidLoaded/*, NotificationCenter.botInfoDidLoaded*/});
                }
                int queryLoadIndex = (Integer) args[12];
                int index = waitingForLoad.indexOf(queryLoadIndex);
                if (index == -1) {
                    return;
                } else {
                    waitingForLoad.remove(index);
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                if (waitingForImportantLoad || waitingForReplyMessageLoad) {
                    if (waitingForReplyMessageLoad) {
                        boolean found = false;
                        for (int a = 0; a < messArr.size(); a++) {
                            if (messArr.get(a).getId() == startLoadFromMessageId) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            startLoadFromMessageId = 0;
                            return;
                        }
                    }
                    int startLoadFrom = startLoadFromMessageId;
                    boolean needSelect = needSelectFromMessageId;
                    clearChatData();
                    startLoadFromMessageId = startLoadFrom;
                    needSelectFromMessageId = needSelect;
                }

                loadsCount++;
                long did = (Long) args[0];
                int loadIndex = did == dialog_id ? 0 : 1;
                int count = (Integer) args[1];
                boolean isCache = (Boolean) args[3];
                int fnid = (Integer) args[4];
                int last_unread_date = (Integer) args[7];
                int load_type = (Integer) args[8];
                boolean wasUnread = false;
                if (fnid != 0) {
                    first_unread_id = fnid;
                    last_message_id = (Integer) args[5];
                    unread_to_load = (Integer) args[6];
                } else if (startLoadFromMessageId != 0 && load_type == 3) {
                    last_message_id = (Integer) args[5];
                }
                ArrayList<TLRPC.TL_messageGroup> groups = (ArrayList<TLRPC.TL_messageGroup>) args[9];
                SparseArray<TLRPC.TL_messageGroup> groupsByStart = null;
                if (groups != null && !groups.isEmpty()) {
                    groupsByStart = new SparseArray<>();
                    for (int a = 0; a < groups.size(); a++) {
                        TLRPC.TL_messageGroup group = groups.get(a);
                        groupsByStart.put(group.min_id, group);
                    }
                }
                int newRowsCount = 0;

                forwardEndReached[loadIndex] = startLoadFromMessageId == 0 && last_message_id == 0;
                if ((load_type == 1 || load_type == 3) && loadIndex == 1) {
                    endReached[0] = cacheEndReached[0] = true;
                    forwardEndReached[0] = false;
                    minMessageId[0] = 0;
                }

                if (loadsCount == 1 && messArr.size() > 20) {
                    loadsCount++;
                }

                if (firstLoading) {
                    if (!forwardEndReached[loadIndex]) {
                        messages.clear();
                        messagesByDays.clear();
                        for (int a = 0; a < 2; a++) {
                            messagesDict[a].clear();
                            if (currentEncryptedChat == null) {
                                maxMessageId[a] = Integer.MAX_VALUE;
                                minMessageId[a] = Integer.MIN_VALUE;
                            } else {
                                maxMessageId[a] = Integer.MIN_VALUE;
                                minMessageId[a] = Integer.MAX_VALUE;
                            }
                            maxDate[a] = Integer.MIN_VALUE;
                            minDate[a] = 0;
                        }
                    }
                    firstLoading = false;
                }

                if (load_type == 1) {
                    Collections.reverse(messArr);
                }
                if (currentEncryptedChat == null) {
                    MessagesQuery.loadReplyMessagesForMessages(messArr, dialog_id);
                }
                int approximateHeightSum = 0;
                for (int a = 0; a < messArr.size(); a++) {
                    MessageObject obj = messArr.get(a);
                    approximateHeightSum += obj.getApproximateHeight();
                    if (currentUser != null && currentUser.bot && obj.isOut()) {
                        obj.setIsRead();
                    }
                    if (messagesDict[loadIndex].containsKey(obj.getId())) {
                        continue;
                    }
                    if (loadIndex == 1) {
                        obj.setIsRead();
                    }
                    if (loadIndex == 0 && channelMessagesImportant != 0 && obj.getId() == 1) {
                        endReached[loadIndex] = true;
                        cacheEndReached[loadIndex] = true;
                    }
                    if (obj.getId() > 0) {
                        maxMessageId[loadIndex] = Math.min(obj.getId(), maxMessageId[loadIndex]);
                        minMessageId[loadIndex] = Math.max(obj.getId(), minMessageId[loadIndex]);
                    } else if (currentEncryptedChat != null) {
                        maxMessageId[loadIndex] = Math.max(obj.getId(), maxMessageId[loadIndex]);
                        minMessageId[loadIndex] = Math.min(obj.getId(), minMessageId[loadIndex]);
                    }
                    if (obj.messageOwner.date != 0) {
                        maxDate[loadIndex] = Math.max(maxDate[loadIndex], obj.messageOwner.date);
                        if (minDate[loadIndex] == 0 || obj.messageOwner.date < minDate[loadIndex]) {
                            minDate[loadIndex] = obj.messageOwner.date;
                        }
                    }

                    if (obj.type < 0 || loadIndex == 1 && obj.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                        continue;
                    }

                    if (!obj.isOut() && obj.isUnread()) {
                        wasUnread = true;
                    }
                    messagesDict[loadIndex].put(obj.getId(), obj);
                    ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);

                    if (dayArray == null) {
                        dayArray = new ArrayList<>();
                        messagesByDays.put(obj.dateKey, dayArray);
                        TLRPC.Message dateMsg = new TLRPC.Message();
                        dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(dateMsg, null, false);
                        dateObj.type = 10;
                        dateObj.contentType = 1;
                        if (load_type == 1) {
                            messages.add(0, dateObj);
                        } else {
                            messages.add(dateObj);
                        }
                        newRowsCount++;
                    }

                    newRowsCount++;
                    if (load_type == 1) {
                        dayArray.add(obj);
                        messages.add(0, obj);
                    }

                    if (groupsByStart != null) {
                        TLRPC.TL_messageGroup group = groupsByStart.get(obj.getId());
                        if (group != null) {
                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = "+" + LocaleController.formatPluralString("comments", group.count);
                            dateMsg.id = 0;
                            dateMsg.date = group.min_id;
                            dateMsg.from_id = group.max_id;
                            MessageObject dateObj = new MessageObject(dateMsg, null, false);
                            dateObj.type = 10;
                            dateObj.contentType = 1;
                            dayArray.add(dateObj);
                            if (load_type == 1) {
                                messages.add(0, dateObj);
                            } else {
                                messages.add(messages.size() - 1, dateObj);
                            }
                            newRowsCount++;
                        }
                    }

                    if (load_type != 1) {
                        dayArray.add(obj);
                        messages.add(messages.size() - 1, obj);
                    }

                    if (obj.getId() == last_message_id) {
                        forwardEndReached[loadIndex] = true;
                    }

                    if (load_type == 2 && obj.getId() == first_unread_id) {
                        if (approximateHeightSum > AndroidUtilities.displaySize.y / 2 || !forwardEndReached[0]) {
                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = "";
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(dateMsg, null, false);
                            dateObj.type = 6;
                            dateObj.contentType = 2;
                            messages.add(messages.size() - 1, dateObj);
                            unreadMessageObject = dateObj;
                            scrollToMessage = unreadMessageObject;
                            scrollToMessagePosition = -10000;
                            newRowsCount++;
                        }
                    } else if (load_type == 3 && obj.getId() == startLoadFromMessageId) {
                        if (needSelectFromMessageId) {
                            highlightMessageId = obj.getId();
                        } else {
                            highlightMessageId = Integer.MAX_VALUE;
                        }
                        scrollToMessage = obj;
                        startLoadFromMessageId = 0;
                        if (scrollToMessagePosition == -10000) {
                            scrollToMessagePosition = -9000;
                        }
                    }
                }

                if (forwardEndReached[loadIndex] && loadIndex != 1) {
                    first_unread_id = 0;
                    last_message_id = 0;
                }

                if (loadsCount <= 2) {
                    if (!isCache) {
                        updateSpamView();
                    }
                }

                if (load_type == 1) {
                    if (messArr.size() != count && !isCache) {
                        forwardEndReached[loadIndex] = true;
                        if (loadIndex != 1) {
                            first_unread_id = 0;
                            last_message_id = 0;
                            chatAdapter.notifyItemRemoved(chatAdapter.getItemCount() - 1);
                            newRowsCount--;
                        }
                        startLoadFromMessageId = 0;
                    }
                    if (newRowsCount > 0) {
                        int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
                        int top = 0;
                        if (firstVisPos != chatLayoutManager.getItemCount() - 1) {
                            firstVisPos = RecyclerView.NO_POSITION;
                        } else {
                            View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                            top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                        }
                        chatAdapter.notifyItemRangeInserted(chatAdapter.getItemCount() - 1, newRowsCount);
                        if (firstVisPos != RecyclerView.NO_POSITION) {
                            chatLayoutManager.scrollToPositionWithOffset(firstVisPos, top);
                        }
                    }
                    loadingForward = false;
                } else {
                    if (messArr.size() < count && load_type != 3) {
                        if (isCache) {
                            if (currentEncryptedChat != null || isBroadcast) {
                                endReached[loadIndex] = true;
                            }
                            cacheEndReached[loadIndex] = true;
                        } else if (load_type != 2) {
                            endReached[loadIndex] = true;// TODO if < 7 from unread
                        }
                    }
                    loading = false;

                    if (chatListView != null) {
                        if (first || scrollToTopOnResume) {
                            chatAdapter.notifyDataSetChanged();
                            if (scrollToMessage != null) {
                                int yOffset;
                                if (scrollToMessagePosition == -9000) {
                                    yOffset = Math.max(0, (chatListView.getHeight() - scrollToMessage.getApproximateHeight()) / 2);
                                } else if (scrollToMessagePosition == -10000) {
                                    yOffset = 0;
                                } else {
                                    yOffset = scrollToMessagePosition;
                                }
                                if (!messages.isEmpty()) {
                                    if (messages.get(messages.size() - 1) == scrollToMessage || messages.get(messages.size() - 2) == scrollToMessage) {
                                        chatLayoutManager.scrollToPositionWithOffset((chatAdapter.isBot ? 1 : 0), -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
                                    } else {
                                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.messagesStartRow + messages.size() - messages.indexOf(scrollToMessage) - 1, -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
                                    }
                                }
                                chatListView.invalidate();
                                if (scrollToMessagePosition == -10000 || scrollToMessagePosition == -9000) {
                                    showPagedownButton(true, true);
                                }
                                scrollToMessagePosition = -10000;
                                scrollToMessage = null;
                            } else {
                                moveScrollToLastMessage();
                            }
                        } else {
                            if (newRowsCount != 0) {
                                boolean end = false;
                                if (endReached[loadIndex] && (loadIndex == 0 && mergeDialogId == 0 || loadIndex == 1)) {
                                    end = true;
                                    chatAdapter.notifyItemRangeChanged(chatAdapter.isBot ? 1 : 0, 2);
                                }
                                int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
                                View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                                int top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                                if (newRowsCount - (end ? 1 : 0) > 0) {
                                    chatAdapter.notifyItemRangeInserted((chatAdapter.isBot ? 2 : 1) + (end ? 0 : 1), newRowsCount - (end ? 1 : 0));
                                }
                                if (firstVisPos != -1) {
                                    chatLayoutManager.scrollToPositionWithOffset(firstVisPos + newRowsCount - (end ? 1 : 0), top);
                                }
                            } else if (endReached[loadIndex] && (loadIndex == 0 && mergeDialogId == 0 || loadIndex == 1)) {
                                chatAdapter.notifyItemRemoved(chatAdapter.isBot ? 1 : 0);
                            }
                        }

                        if (paused) {
                            scrollToTopOnResume = true;
                            if (scrollToMessage != null) {
                                scrollToTopUnReadOnResume = true;
                            }
                        }

                        if (first) {
                            if (chatListView != null) {
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
                    if (loadIndex == 0) {
                        final boolean wasUnreadFinal = wasUnread;
                        final int last_unread_date_final = last_unread_date;
                        final int lastid = messages.get(0).getId();
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (last_message_id != 0) {
                                    MessagesController.getInstance().markDialogAsRead(dialog_id, lastid, last_message_id, last_unread_date_final, wasUnreadFinal, false);
                                } else {
                                    MessagesController.getInstance().markDialogAsRead(dialog_id, lastid, minMessageId[0], maxDate[0], wasUnreadFinal, false);
                                }
                            }
                        }, 700);
                    }
                    first = false;
                }
                if (messages.isEmpty() && currentEncryptedChat == null && currentUser != null && currentUser.bot && botUser == null) {
                    botUser = "";
                    updateBottomOverlay();
                }

                if (newRowsCount == 0 && currentEncryptedChat != null && !endReached[0]) {
                    first = true;
                    if (chatListView != null) {
                        chatListView.setEmptyView(null);
                    }
                    if (emptyViewContainer != null) {
                        emptyViewContainer.setVisibility(View.INVISIBLE);
                    }
                } else {
                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                    }
                }
                checkScrollForLoad(false);
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
            if (replyObjectTextView != null) {
                replyObjectTextView.invalidate();
            }
            if (alertTextView != null) {
                alertTextView.invalidate();
            }
            if (pinnedMessageTextView != null) {
                pinnedMessageTextView.invalidate();
            }
            if (mentionListView != null) {
                mentionListView.invalidateViews();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int updateMask = (Integer) args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                updateTitle();
            }
            boolean updateSubtitle = false;
            if ((updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                if (currentChat != null && avatarContainer != null) {
                    avatarContainer.updateOnlineCount();
                }
                updateSubtitle = true;
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_NAME) != 0) {
                checkAndUpdateAvatar();
                updateVisibleRows();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                updateSubtitle = true;
            }
            if ((updateMask & MessagesController.UPDATE_MASK_CHANNEL) != 0 && ChatObject.isChannel(currentChat)) {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
                if (chat == null) {
                    return;
                }
                currentChat = chat;
                updateSubtitle = true;
                updateBottomOverlay();
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setDialogId(dialog_id);
                }
            }
            if (avatarContainer != null && updateSubtitle) {
                avatarContainer.updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PHONE) != 0) {
                updateContactStatus();
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long did = (Long) args[0];
            if (did == dialog_id) {

                boolean updateChat = false;
                boolean hasFromMe = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                if (currentEncryptedChat != null && arr.size() == 1) {
                    MessageObject obj = arr.get(0);

                    if (currentEncryptedChat != null && obj.isOut() && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction &&
                            obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL && getParentActivity() != null) {
                        if (AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 17 && currentEncryptedChat.ttl > 0 && currentEncryptedChat.ttl <= 60) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            builder.setMessage(LocaleController.formatString("CompatibilityChat", R.string.CompatibilityChat, currentUser.first_name, currentUser.first_name));
                            showDialog(builder.create());
                        }
                    }
                }
                if (currentChat != null || inlineReturn != 0) {
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject messageObject = arr.get(a);
                        if (currentChat != null) {
                            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser && messageObject.messageOwner.action.user_id == UserConfig.getClientUserId() ||
                                    messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser && messageObject.messageOwner.action.users.contains(UserConfig.getClientUserId())) {
                                TLRPC.Chat newChat = MessagesController.getInstance().getChat(currentChat.id);
                                if (newChat != null) {
                                    currentChat = newChat;
                                    checkActionBarMenu();
                                    updateBottomOverlay();
                                    if (avatarContainer != null) {
                                        avatarContainer.updateSubtitle();
                                    }
                                }
                            } else if (messageObject.messageOwner.reply_to_msg_id != 0 && messageObject.replyMessageObject == null) {
                                messageObject.replyMessageObject = messagesDict[0].get(messageObject.messageOwner.reply_to_msg_id);
                                if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                                    messageObject.generatePinMessageText(null, null);
                                }
                            }
                        } else if (inlineReturn != 0) {
                            if (messageObject.messageOwner.reply_markup != null) {
                                for (int b = 0; b < messageObject.messageOwner.reply_markup.rows.size(); b++) {
                                    TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(b);
                                    for (int c = 0; c < row.buttons.size(); c++) {
                                        TLRPC.KeyboardButton button = row.buttons.get(c);
                                        if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
                                            processSwitchButton((TLRPC.TL_keyboardButtonSwitchInline) button);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                boolean reloadMegagroup = false;
                if (!forwardEndReached[0]) {
                    int currentMaxDate = Integer.MIN_VALUE;
                    int currentMinMsgId = Integer.MIN_VALUE;
                    if (currentEncryptedChat != null) {
                        currentMinMsgId = Integer.MAX_VALUE;
                    }
                    boolean currentMarkAsRead = false;

                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject obj = arr.get(a);
                        if (currentUser != null && currentUser.bot && obj.isOut()) {
                            obj.setIsRead();
                        }
                        if (avatarContainer != null && currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction && obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                            avatarContainer.setTime(((TLRPC.TL_decryptedMessageActionSetMessageTTL) obj.messageOwner.action.encryptedAction).ttl_seconds);
                        }
                        if (obj.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                            final Bundle bundle = new Bundle();
                            bundle.putInt("chat_id", obj.messageOwner.action.channel_id);
                            final BaseFragment lastFragment = parentLayout.fragmentsStack.size() > 0 ? parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) : null;
                            final int channel_id = obj.messageOwner.action.channel_id;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    ActionBarLayout parentLayout = ChatActivity.this.parentLayout;
                                    if (lastFragment != null) {
                                        NotificationCenter.getInstance().removeObserver(lastFragment, NotificationCenter.closeChats);
                                    }
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                    parentLayout.presentFragment(new ChatActivity(bundle), true);
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            MessagesController.getInstance().loadFullChat(channel_id, 0, true);
                                        }
                                    }, 1000);
                                }
                            });
                            return;
                        } else if (currentChat != null && currentChat.megagroup && (obj.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser || obj.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser)) {
                            reloadMegagroup = true;
                        }
                        if (obj.isOut() && obj.isSending()) {
                            scrollToLastMessage(false);
                            return;
                        }
                        if (messagesDict[0].containsKey(obj.getId())) {
                            continue;
                        }
                        obj.checkLayout();
                        currentMaxDate = Math.max(currentMaxDate, obj.messageOwner.date);
                        if (obj.getId() > 0) {
                            currentMinMsgId = Math.max(obj.getId(), currentMinMsgId);
                            last_message_id = Math.max(last_message_id, obj.getId());
                        } else if (currentEncryptedChat != null) {
                            currentMinMsgId = Math.min(obj.getId(), currentMinMsgId);
                            last_message_id = Math.min(last_message_id, obj.getId());
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
                                MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).getId(), currentMinMsgId, currentMaxDate, true, false);
                            }
                        }
                    }
                    updateVisibleRows();
                } else {
                    boolean markAsRead = false;
                    boolean unreadUpdated = true;
                    int oldCount = messages.size();
                    int addedCount = 0;
                    HashMap<String, ArrayList<MessageObject>> webpagesToReload = null;
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject obj = arr.get(a);
                        if (currentUser != null && currentUser.bot && obj.isOut()) {
                            obj.setIsRead();
                        }
                        if (avatarContainer != null && currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction && obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                            avatarContainer.setTime(((TLRPC.TL_decryptedMessageActionSetMessageTTL) obj.messageOwner.action.encryptedAction).ttl_seconds);
                        }
                        if (messagesDict[0].containsKey(obj.getId())) {
                            continue;
                        }
                        if (currentEncryptedChat != null && obj.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && obj.messageOwner.media.webpage instanceof TLRPC.TL_webPageUrlPending) {
                            if (webpagesToReload == null) {
                                webpagesToReload = new HashMap<>();
                            }
                            ArrayList<MessageObject> arrayList = webpagesToReload.get(obj.messageOwner.media.webpage.url);
                            if (arrayList == null) {
                                arrayList = new ArrayList<>();
                                webpagesToReload.put(obj.messageOwner.media.webpage.url, arrayList);
                            }
                            arrayList.add(obj);
                        }
                        obj.checkLayout();
                        if (obj.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                            final Bundle bundle = new Bundle();
                            bundle.putInt("chat_id", obj.messageOwner.action.channel_id);
                            final BaseFragment lastFragment = parentLayout.fragmentsStack.size() > 0 ? parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) : null;
                            final int channel_id = obj.messageOwner.action.channel_id;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    ActionBarLayout parentLayout = ChatActivity.this.parentLayout;
                                    if (lastFragment != null) {
                                        NotificationCenter.getInstance().removeObserver(lastFragment, NotificationCenter.closeChats);
                                    }
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                    parentLayout.presentFragment(new ChatActivity(bundle), true);
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            MessagesController.getInstance().loadFullChat(channel_id, 0, true);
                                        }
                                    }, 1000);
                                }
                            });
                            return;
                        } else if (currentChat != null && currentChat.megagroup && (obj.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser || obj.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser)) {
                            reloadMegagroup = true;
                        }
                        if (minDate[0] == 0 || obj.messageOwner.date < minDate[0]) {
                            minDate[0] = obj.messageOwner.date;
                        }

                        if (obj.isOut()) {
                            removeUnreadPlane();
                            hasFromMe = true;
                        }

                        if (obj.getId() > 0) {
                            maxMessageId[0] = Math.min(obj.getId(), maxMessageId[0]);
                            minMessageId[0] = Math.max(obj.getId(), minMessageId[0]);
                        } else if (currentEncryptedChat != null) {
                            maxMessageId[0] = Math.max(obj.getId(), maxMessageId[0]);
                            minMessageId[0] = Math.min(obj.getId(), minMessageId[0]);
                        }
                        maxDate[0] = Math.max(maxDate[0], obj.messageOwner.date);
                        messagesDict[0].put(obj.getId(), obj);
                        ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);
                        if (dayArray == null) {
                            dayArray = new ArrayList<>();
                            messagesByDays.put(obj.dateKey, dayArray);
                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(dateMsg, null, false);
                            dateObj.type = 10;
                            dateObj.contentType = 1;
                            messages.add(0, dateObj);
                            addedCount++;
                        }
                        if (!obj.isOut()) {
                            if (paused) {
                                if (!scrollToTopUnReadOnResume && unreadMessageObject != null) {
                                    if (chatAdapter != null) {
                                        chatAdapter.removeMessageObject(unreadMessageObject);
                                    } else {
                                        messages.remove(unreadMessageObject);
                                    }
                                    unreadMessageObject = null;
                                }
                                if (unreadMessageObject == null) {
                                    TLRPC.Message dateMsg = new TLRPC.Message();
                                    dateMsg.message = "";
                                    dateMsg.id = 0;
                                    MessageObject dateObj = new MessageObject(dateMsg, null, false);
                                    dateObj.type = 6;
                                    dateObj.contentType = 2;
                                    messages.add(0, dateObj);
                                    unreadMessageObject = dateObj;
                                    scrollToMessage = unreadMessageObject;
                                    scrollToMessagePosition = -10000;
                                    unreadUpdated = false;
                                    unread_to_load = 0;
                                    scrollToTopUnReadOnResume = true;
                                    addedCount++;
                                }
                            }
                            if (unreadMessageObject != null) {
                                unread_to_load++;
                                unreadUpdated = true;
                            }
                            if (obj.isUnread()) {
                                if (!paused) {
                                    obj.setIsRead();
                                }
                                markAsRead = true;
                            }
                        }

                        dayArray.add(0, obj);
                        messages.add(0, obj);
                        addedCount++;
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }
                    if (webpagesToReload != null) {
                        MessagesController.getInstance().reloadWebPages(dialog_id, webpagesToReload);
                    }

                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                    }
                    if (chatAdapter != null) {
                        if (unreadUpdated) {
                            chatAdapter.updateRowWithMessageObject(unreadMessageObject);
                        }
                        if (addedCount != 0) {
                            chatAdapter.notifyItemRangeInserted(chatAdapter.getItemCount(), addedCount);
                        }
                    } else {
                        scrollToTopOnResume = true;
                    }

                    if (chatListView != null && chatAdapter != null) {
                        int lastVisible = chatLayoutManager.findLastVisibleItemPosition();
                        if (lastVisible == RecyclerView.NO_POSITION) {
                            lastVisible = 0;
                        }
                        if (endReached[0]) {
                            lastVisible++;
                        }
                        if (chatAdapter.isBot) {
                            oldCount++;
                        }
                        if (lastVisible >= oldCount || hasFromMe) {
                            if (!firstLoading) {
                                if (paused) {
                                    scrollToTopOnResume = true;
                                } else {
                                    moveScrollToLastMessage();
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
                            readWithDate = maxDate[0];
                            readWithMid = minMessageId[0];
                        } else {
                            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).getId(), minMessageId[0], maxDate[0], true, false);
                        }
                    }
                }
                if (!messages.isEmpty() && botUser != null && botUser.length() == 0) {
                    botUser = null;
                    updateBottomOverlay();
                }
                if (updateChat) {
                    updateTitle();
                    checkAndUpdateAvatar();
                }
                if (reloadMegagroup) {
                    MessagesController.getInstance().loadFullChat(currentChat.id, 0, true);
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            if (args != null && args.length > 0) {
                long did = (Long) args[0];
                if (did == dialog_id) {
                    finishFragment();
                }
            } else {
                removeSelfFromStack();
            }
        } else if (id == NotificationCenter.messagesRead) {
            SparseArray<Long> inbox = (SparseArray<Long>) args[0];
            SparseIntArray outbox = (SparseIntArray) args[1];
            boolean updated = false;
            for (int b = 0; b < inbox.size(); b++) {
                int key = inbox.keyAt(b);
                long messageId = inbox.get(key);
                if (key != dialog_id) {
                    continue;
                }
                for (int a = 0; a < messages.size(); a++) {
                    MessageObject obj = messages.get(a);
                    if (!obj.isOut() && obj.getId() > 0 && obj.getId() <= (int) messageId) {
                        if (!obj.isUnread()) {
                            break;
                        }
                        obj.setIsRead();
                        updated = true;
                    }
                }
                break;
            }
            for (int b = 0; b < outbox.size(); b++) {
                int key = outbox.keyAt(b);
                int messageId = outbox.get(key);
                if (key != dialog_id) {
                    continue;
                }
                for (int a = 0; a < messages.size(); a++) {
                    MessageObject obj = messages.get(a);
                    if (obj.isOut() && obj.getId() > 0 && obj.getId() <= messageId) {
                        if (!obj.isUnread()) {
                            break;
                        }
                        obj.setIsRead();
                        updated = true;
                    }
                }
                break;
            }
            if (updated) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            int channelId = (Integer) args[1];
            int loadIndex = 0;
            if (ChatObject.isChannel(currentChat)) {
                if (channelId == 0 && mergeDialogId != 0) {
                    loadIndex = 1;
                } else if (channelId == currentChat.id) {
                    loadIndex = 0;
                } else {
                    return;
                }
            } else if (channelId != 0) {
                return;
            }
            boolean updated = false;
            for (int a = 0; a < markAsDeletedMessages.size(); a++) {
                Integer ids = markAsDeletedMessages.get(a);
                MessageObject obj = messagesDict[loadIndex].get(ids);
                if (loadIndex == 0 && info != null && info.pinned_msg_id == ids) {
                    pinnedMessageObject = null;
                    info.pinned_msg_id = 0;
                    MessagesStorage.getInstance().updateChannelPinnedMessage(channelId, 0);
                    updatePinnedMessageView(true);
                }
                if (obj != null) {
                    int index = messages.indexOf(obj);
                    if (index != -1) {
                        messages.remove(index);
                        messagesDict[loadIndex].remove(ids);
                        ArrayList<MessageObject> dayArr = messagesByDays.get(obj.dateKey);
                        if (dayArr != null) {
                            dayArr.remove(obj);
                            if (dayArr.isEmpty()) {
                                messagesByDays.remove(obj.dateKey);
                                if (index >= 0 && index < messages.size()) {
                                    messages.remove(index);
                                }
                            }
                            updated = true;
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                if (!endReached[0] && !loading) {
                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                    }
                    if (chatListView != null) {
                        chatListView.setEmptyView(null);
                    }
                    if (currentEncryptedChat == null) {
                        maxMessageId[0] = maxMessageId[1] = Integer.MAX_VALUE;
                        minMessageId[0] = minMessageId[1] = Integer.MIN_VALUE;
                    } else {
                        maxMessageId[0] = maxMessageId[1] = Integer.MIN_VALUE;
                        minMessageId[0] = minMessageId[1] = Integer.MAX_VALUE;
                    }
                    maxDate[0] = maxDate[1] = Integer.MIN_VALUE;
                    minDate[0] = minDate[1] = 0;
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(dialog_id, 30, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, channelMessagesImportant, lastLoadIndex++);
                    loading = true;
                } else {
                    if (botButtons != null) {
                        botButtons = null;
                        if (chatActivityEnterView != null) {
                            chatActivityEnterView.setButtons(null, false);
                        }
                    }
                    if (currentEncryptedChat == null && currentUser != null && currentUser.bot && botUser == null) {
                        botUser = "";
                        updateBottomOverlay();
                    }
                }
            }
            if (updated && chatAdapter != null) {
                removeUnreadPlane();
                chatAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer) args[0];
            MessageObject obj = messagesDict[0].get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer) args[1];
                if (!newMsgId.equals(msgId) && messagesDict[0].containsKey(newMsgId)) {
                    MessageObject removed = messagesDict[0].remove(msgId);
                    if (removed != null) {
                        int index = messages.indexOf(removed);
                        messages.remove(index);
                        ArrayList<MessageObject> dayArr = messagesByDays.get(removed.dateKey);
                        dayArr.remove(obj);
                        if (dayArr.isEmpty()) {
                            messagesByDays.remove(obj.dateKey);
                            if (index >= 0 && index < messages.size()) {
                                messages.remove(index);
                            }
                        }
                        chatAdapter.notifyDataSetChanged();
                    }
                    return;
                }
                TLRPC.Message newMsgObj = (TLRPC.Message) args[2];
                boolean mediaUpdated = false;
                try {
                    mediaUpdated = obj.messageOwner.params != null && obj.messageOwner.params.containsKey("query_id") || newMsgObj.media != null && obj.messageOwner.media != null && !newMsgObj.media.getClass().equals(obj.messageOwner.media.getClass());
                } catch (Exception e) { //TODO
                    FileLog.e("tmessages", e);
                }
                if (newMsgObj != null) {
                    obj.messageOwner = newMsgObj;
                    obj.generateThumbs(true);
                    obj.setType();
                }
                messagesDict[0].remove(msgId);
                messagesDict[0].put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                obj.forceUpdate = mediaUpdated;
                ArrayList<MessageObject> messArr = new ArrayList<>();
                messArr.add(obj);
                if (currentEncryptedChat == null) {
                    MessagesQuery.loadReplyMessagesForMessages(messArr, dialog_id);
                }
                if (chatAdapter != null) {
                    chatAdapter.updateRowWithMessageObject(obj);
                }
                if (mediaUpdated && chatLayoutManager.findLastVisibleItemPosition() >= messages.size() - 1) {
                    moveScrollToLastMessage();
                }
                NotificationsController.getInstance().playOutChatSound();
            }
        } else if (id == NotificationCenter.messageReceivedByAck) {
            Integer msgId = (Integer) args[0];
            MessageObject obj = messagesDict[0].get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                if (chatAdapter != null) {
                    chatAdapter.updateRowWithMessageObject(obj);
                }
            }
        } else if (id == NotificationCenter.messageSendError) {
            Integer msgId = (Integer) args[0];
            MessageObject obj = messagesDict[0].get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (currentChat != null && chatFull.id == currentChat.id) {
                if (chatFull instanceof TLRPC.TL_channelFull) {
                    if (currentChat.megagroup) {
                        int lastDate = 0;
                        if (chatFull.participants != null) {
                            for (int a = 0; a < chatFull.participants.participants.size(); a++) {
                                lastDate = Math.max(chatFull.participants.participants.get(a).date, lastDate);
                            }
                        }
                        if (lastDate == 0 || Math.abs(System.currentTimeMillis() / 1000 - lastDate) > 60 * 60) {
                            MessagesController.getInstance().loadChannelParticipants(currentChat.id);
                        }
                    }
                    if (chatFull.participants == null && info != null) {
                        chatFull.participants = info.participants;
                    }
                }
                info = chatFull;
                if (mentionsAdapter != null) {
                    mentionsAdapter.setChatInfo(info);
                }
                if (args[3] instanceof MessageObject) {
                    pinnedMessageObject = (MessageObject) args[3];
                    updatePinnedMessageView(false);
                } else {
                    updatePinnedMessageView(true);
                }
                if (avatarContainer != null) {
                    avatarContainer.updateOnlineCount();
                    avatarContainer.updateSubtitle();
                }
                if (isBroadcast) {
                    SendMessagesHelper.getInstance().setCurrentChatInfo(info);
                }
                if (info instanceof TLRPC.TL_chatFull) {
                    hasBotsCommands = false;
                    botInfo.clear();
                    botsCount = 0;
                    URLSpanBotCommand.enabled = false;
                    for (int a = 0; a < info.participants.participants.size(); a++) {
                        TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                        TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                        if (user != null && user.bot) {
                            URLSpanBotCommand.enabled = true;
                            botsCount++;
                            BotQuery.loadBotInfo(user.id, true, classGuid);
                        }
                    }
                    if (chatListView != null) {
                        chatListView.invalidateViews();
                    }
                } else if (info instanceof TLRPC.TL_channelFull) {
                    hasBotsCommands = false;
                    botInfo.clear();
                    botsCount = 0;
                    URLSpanBotCommand.enabled = !info.bot_info.isEmpty();
                    botsCount = info.bot_info.size();
                    for (int a = 0; a < info.bot_info.size(); a++) {
                        TLRPC.BotInfo bot = info.bot_info.get(a);
                        if (!bot.commands.isEmpty() && (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup)) {
                            hasBotsCommands = true;
                        }
                        botInfo.put(bot.user_id, bot);
                    }
                    if (chatListView != null) {
                        chatListView.invalidateViews();
                    }
                    if (mentionsAdapter != null && (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup)) {
                        mentionsAdapter.setBotInfo(botInfo);
                    }
                }
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.setBotsCount(botsCount);
                }
                if (ChatObject.isChannel(currentChat) && mergeDialogId == 0 && info.migrated_from_chat_id != 0) {
                    mergeDialogId = -info.migrated_from_chat_id;
                    maxMessageId[1] = info.migrated_from_max_id;
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    }
                }
            }
        } else if (id == NotificationCenter.chatInfoCantLoad) {
            int chatId = (Integer) args[0];
            if (currentChat != null && currentChat.id == chatId) {
                int reason = (Integer) args[1];
                if (getParentActivity() == null || closeChatDialog != null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                if (reason == 0) {
                    builder.setMessage(LocaleController.getString("ChannelCantOpenPrivate", R.string.ChannelCantOpenPrivate));
                } else if (reason == 1) {
                    builder.setMessage(LocaleController.getString("ChannelCantOpenNa", R.string.ChannelCantOpenNa));
                } else if (reason == 2) {
                    builder.setMessage(LocaleController.getString("ChannelCantOpenBanned", R.string.ChannelCantOpenBanned));
                }
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                showDialog(closeChatDialog = builder.create());

                loading = false;
                if (progressView != null) {
                    progressView.setVisibility(View.INVISIBLE);
                }
                if (chatAdapter != null) {
                    chatAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.contactsDidLoaded) {
            updateContactStatus();
            if (avatarContainer != null) {
                avatarContainer.updateSubtitle();
            }
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateContactStatus();
                updateSecretStatus();
                initStickers();
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.setNeedBotContext(!chatActivityEnterView.isEditingMessage() && (currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46));
                }
            }
        } else if (id == NotificationCenter.messagesReadEncrypted) {
            int encId = (Integer) args[0];
            if (currentEncryptedChat != null && currentEncryptedChat.id == encId) {
                int date = (Integer) args[1];
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
        } else if (id == NotificationCenter.audioDidReset || id == NotificationCenter.audioPlayStateChanged) {
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
                            cell.updateButtonState(false);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.audioProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
                            MessageObject playing = cell.getMessageObject();
                            MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                            if (player != null) {
                                playing.audioProgress = player.audioProgress;
                                playing.audioProgressSec = player.audioProgressSec;
                                cell.updateAudioProgress();
                            }
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.removeAllMessagesFromDialog) {
            long did = (Long) args[0];
            if (dialog_id == did) {
                messages.clear();
                waitingForLoad.clear();
                messagesByDays.clear();
                for (int a = 1; a >= 0; a--) {
                    messagesDict[a].clear();
                    if (currentEncryptedChat == null) {
                        maxMessageId[a] = Integer.MAX_VALUE;
                        minMessageId[a] = Integer.MIN_VALUE;
                    } else {
                        maxMessageId[a] = Integer.MIN_VALUE;
                        minMessageId[a] = Integer.MAX_VALUE;
                    }
                    maxDate[a] = Integer.MIN_VALUE;
                    minDate[a] = 0;
                    selectedMessagesIds[a].clear();
                    selectedMessagesCanCopyIds[a].clear();
                }
                cantDeleteMessagesCount = 0;
                actionBar.hideActionMode();
                updatePinnedMessageView(true);

                if (botButtons != null) {
                    botButtons = null;
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.setButtons(null, false);
                    }
                }
                if (currentEncryptedChat == null && currentUser != null && currentUser.bot && botUser == null) {
                    botUser = "";
                    updateBottomOverlay();
                }
                if ((Boolean) args[1]) {
                    if (chatAdapter != null) {
                        progressView.setVisibility(chatAdapter.botInfoRow == -1 ? View.VISIBLE : View.INVISIBLE);
                        chatListView.setEmptyView(null);
                    }
                    for (int a = 0; a < 2; a++) {
                        endReached[a] = false;
                        cacheEndReached[a] = false;
                        forwardEndReached[a] = true;
                    }
                    first = true;
                    firstLoading = true;
                    loading = true;
                    waitingForImportantLoad = false;
                    startLoadFromMessageId = 0;
                    needSelectFromMessageId = false;
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, true, 0, classGuid, 2, 0, channelMessagesImportant, lastLoadIndex++);
                } else {
                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                        chatListView.setEmptyView(emptyViewContainer);
                    }
                }

                if (chatAdapter != null) {
                    chatAdapter.notifyDataSetChanged();
                }
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
            MessageObject messageObject = (MessageObject) args[0];
            long finalSize = (Long) args[2];
            if (finalSize != 0 && dialog_id == messageObject.getDialogId()) {
                MessageObject currentObject = messagesDict[0].get(messageObject.getId());
                if (currentObject != null) {
                    currentObject.messageOwner.media.document.size = (int) finalSize;
                    updateVisibleRows();
                }
            }
        } else if (id == NotificationCenter.didCreatedNewDeleteTask) {
            SparseArray<ArrayList<Integer>> mids = (SparseArray<ArrayList<Integer>>) args[0];
            boolean changed = false;
            for (int i = 0; i < mids.size(); i++) {
                int key = mids.keyAt(i);
                ArrayList<Integer> arr = mids.get(key);
                for (Integer mid : arr) {
                    MessageObject messageObject = messagesDict[0].get(mid);
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
            MessageObject messageObject = (MessageObject) args[0];
            sendSecretMessageRead(messageObject);
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null && (messageObject1.isVoice() || messageObject1.isMusic())) {
                            cell.updateButtonState(false);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.updateMessageMedia) {
            MessageObject messageObject = (MessageObject) args[0];
            MessageObject existMessageObject = messagesDict[0].get(messageObject.getId());
            if (existMessageObject != null) {
                existMessageObject.messageOwner.media = messageObject.messageOwner.media;
                existMessageObject.messageOwner.attachPath = messageObject.messageOwner.attachPath;
                existMessageObject.generateThumbs(false);
            }
            updateVisibleRows();
        } else if (id == NotificationCenter.replaceMessagesObjects) {
            long did = (long) args[0];
            if (did != dialog_id && did != mergeDialogId) {
                return;
            }
            int loadIndex = did == dialog_id ? 0 : 1;
            boolean changed = false;
            boolean mediaUpdated = false;
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                MessageObject old = messagesDict[loadIndex].get(messageObject.getId());
                if (pinnedMessageObject != null && pinnedMessageObject.getId() == messageObject.getId()) {
                    pinnedMessageObject = messageObject;
                    updatePinnedMessageView(true);
                }
                if (old != null) {
                    if (!mediaUpdated && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                        mediaUpdated = true;
                    }
                    if (old.replyMessageObject != null) {
                        messageObject.replyMessageObject = old.replyMessageObject;
                    }
                    messagesDict[loadIndex].put(old.getId(), messageObject);
                    int index = messages.indexOf(old);
                    if (index >= 0) {
                        messages.set(index, messageObject);
                        if (chatAdapter != null) {
                            chatAdapter.notifyItemChanged(chatAdapter.messagesStartRow + messages.size() - index - 1);
                        }
                        changed = true;
                    }
                }
            }
            if (changed && chatLayoutManager != null) {
                if (mediaUpdated && chatLayoutManager.findLastVisibleItemPosition() >= messages.size() - (chatAdapter.isBot ? 2 : 1)) {
                    moveScrollToLastMessage();
                }
            }
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateTitleIcons();
            if (ChatObject.isChannel(currentChat)) {
                updateBottomOverlay();
            }
        } else if (id == NotificationCenter.didLoadedReplyMessages) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.didLoadedPinnedMessage) {
            MessageObject message = (MessageObject) args[0];
            if (message.getDialogId() == dialog_id && info != null && info.pinned_msg_id == message.getId()) {
                pinnedMessageObject = message;
                loadingPinnedMessage = 0;
                updatePinnedMessageView(true);
            }
        } else if (id == NotificationCenter.didReceivedWebpages) {
            ArrayList<TLRPC.Message> arrayList = (ArrayList<TLRPC.Message>) args[0];
            boolean updated = false;
            for (int a = 0; a < arrayList.size(); a++) {
                TLRPC.Message message = arrayList.get(a);
                long did = MessageObject.getDialogId(message);
                if (did != dialog_id && did != mergeDialogId) {
                    continue;
                }
                MessageObject currentMessage = messagesDict[did == dialog_id ? 0 : 1].get(message.id);
                if (currentMessage != null) {
                    currentMessage.messageOwner.media = new TLRPC.TL_messageMediaWebPage();
                    currentMessage.messageOwner.media.webpage = message.media.webpage;
                    currentMessage.generateThumbs(true);
                    updated = true;
                }
            }
            if (updated) {
                updateVisibleRows();
                if (chatLayoutManager != null && chatLayoutManager.findLastVisibleItemPosition() >= messages.size() - 1) {
                    moveScrollToLastMessage();
                }
            }
        } else if (id == NotificationCenter.didReceivedWebpagesInUpdates) {
            if (foundWebPage != null) {
                HashMap<Long, TLRPC.WebPage> hashMap = (HashMap<Long, TLRPC.WebPage>) args[0];
                for (TLRPC.WebPage webPage : hashMap.values()) {
                    if (webPage.id == foundWebPage.id) {
                        showReplyPanel(!(webPage instanceof TLRPC.TL_webPageEmpty), null, null, webPage, false, true);
                        break;
                    }
                }
            }
        } else if (id == NotificationCenter.messagesReadContent) {
            ArrayList<Long> arrayList = (ArrayList<Long>) args[0];
            boolean updated = false;
            for (int a = 0; a < arrayList.size(); a++) {
                long mid = arrayList.get(a);
                MessageObject currentMessage = messagesDict[mergeDialogId == 0 ? 0 : 1].get((int) mid);
                if (currentMessage != null) {
                    currentMessage.setContentIsRead();
                    updated = true;
                }
            }
            if (updated) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.botInfoDidLoaded) {
            int guid = (Integer) args[1];
            if (classGuid == guid) {
                TLRPC.BotInfo info = (TLRPC.BotInfo) args[0];
                if (currentEncryptedChat == null) {
                    if (!info.commands.isEmpty() && !ChatObject.isChannel(currentChat)) {
                        hasBotsCommands = true;
                    }
                    botInfo.put(info.user_id, info);
                    if (chatAdapter != null) {
                        chatAdapter.notifyItemChanged(0);
                    }
                    if (mentionsAdapter != null && (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup)) {
                        mentionsAdapter.setBotInfo(botInfo);
                    }
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
                    }
                }
                updateBotButtons();
            }
        } else if (id == NotificationCenter.botKeyboardDidLoaded) {
            if (dialog_id == (Long) args[1]) {
                TLRPC.Message message = (TLRPC.Message) args[0];
                if (message != null && !userBlocked) {
                    botButtons = new MessageObject(message, null, false);
                    if (chatActivityEnterView != null) {
                        if (botButtons.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardForceReply) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            if (preferences.getInt("answered_" + dialog_id, 0) != botButtons.getId() && (replyingMessageObject == null || chatActivityEnterView.getFieldText() == null)) {
                                botReplyButtons = botButtons;
                                chatActivityEnterView.setButtons(botButtons);
                                showReplyPanel(true, botButtons, null, null, false, true);
                            }
                        } else {
                            if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                                botReplyButtons = null;
                                showReplyPanel(false, null, null, null, false, true);
                            }
                            chatActivityEnterView.setButtons(botButtons);
                        }
                    }
                } else {
                    botButtons = null;
                    if (chatActivityEnterView != null) {
                        if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                            botReplyButtons = null;
                            showReplyPanel(false, null, null, null, false, true);
                        }
                        chatActivityEnterView.setButtons(botButtons);
                    }
                }
            }
        } else if (id == NotificationCenter.chatSearchResultsAvailable) {
            if (classGuid == (Integer) args[0]) {
                int messageId = (Integer) args[1];
                long did = (Long) args[3];
                if (messageId != 0) {
                    scrollToMessageId(messageId, 0, true, did == dialog_id ? 0 : 1);
                }
                updateSearchButtons((Integer) args[2]);
            }
        } else if (id == NotificationCenter.didUpdatedMessagesViews) {
            SparseArray<SparseIntArray> channelViews = (SparseArray<SparseIntArray>) args[0];
            SparseIntArray array = channelViews.get((int) dialog_id);
            if (array != null) {
                boolean updated = false;
                for (int a = 0; a < array.size(); a++) {
                    int messageId = array.keyAt(a);
                    MessageObject messageObject = messagesDict[0].get(messageId);
                    if (messageObject != null) {
                        int newValue = array.get(messageId);
                        if (newValue > messageObject.messageOwner.views) {
                            messageObject.messageOwner.views = newValue;
                            updated = true;
                        }
                    }
                }
                if (updated) {
                    updateVisibleRows();
                }
            }
        } else if (id == NotificationCenter.peerSettingsDidLoaded) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                updateSpamView();
            }
        }
    }

    public boolean processSwitchButton(TLRPC.TL_keyboardButtonSwitchInline button) {
        if (inlineReturn == 0) {
            return false;
        }
        String query = "@" + currentUser.username + " " + button.query;
        if (inlineReturn == dialog_id) {
            inlineReturn = 0;
            chatActivityEnterView.setFieldText(query);
        } else {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("dialog_" + inlineReturn, query);
            editor.commit();
            if (parentLayout.fragmentsStack.size() > 1) {
                BaseFragment prevFragment = parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2);
                if (prevFragment instanceof ChatActivity && ((ChatActivity) prevFragment).dialog_id == inlineReturn) {
                    finishFragment();
                } else {
                    Bundle bundle = new Bundle();
                    int lower_part = (int) inlineReturn;
                    int high_part = (int) (inlineReturn >> 32);
                    if (lower_part != 0) {
                        if (lower_part > 0) {
                            bundle.putInt("user_id", lower_part);
                        } else if (lower_part < 0) {
                            bundle.putInt("chat_id", -lower_part);
                        }
                    } else {
                        bundle.putInt("enc_id", high_part);
                    }
                    /*ActionBarLayout parentLayout = ChatActivity.this.parentLayout;
                    if (lastFragment != null) {
                        NotificationCenter.getInstance().removeObserver(lastFragment, NotificationCenter.closeChats);
                    }
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);*/
                    presentFragment(new ChatActivity(bundle), true);
                }
            }
        }
        return true;
    }

    private void updateSearchButtons(int mask) {
        if (searchUpItem != null) {
            searchUpItem.setEnabled((mask & 1) != 0);
            searchDownItem.setEnabled((mask & 2) != 0);
            ViewProxy.setAlpha(searchUpItem, searchUpItem.isEnabled() ? 1.0f : 0.6f);
            ViewProxy.setAlpha(searchDownItem, searchDownItem.isEnabled() ? 1.0f : 0.6f);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoaded, NotificationCenter.dialogsNeedReload,
                NotificationCenter.closeChats, NotificationCenter.messagesDidLoaded, NotificationCenter.botKeyboardDidLoaded/*, NotificationCenter.botInfoDidLoaded*/});
        NotificationCenter.getInstance().setAnimationInProgress(true);
        if (isOpen) {
            openAnimationEnded = false;
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        NotificationCenter.getInstance().setAnimationInProgress(false);
        if (isOpen) {
            openAnimationEnded = true;
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    ((ChatMessageCell) view).setAllowedToSetPhoto(true);
                }
            }

            if (currentUser != null) {
                MessagesController.getInstance().loadFullUser(currentUser, classGuid, false);
            }
            if (Build.VERSION.SDK_INT >= 21) {
                createChatAttachView();
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (closeChatDialog != null && dialog == closeChatDialog) {
            MessagesController.getInstance().deleteDialog(dialog_id, 0);
            if (parentLayout != null && !parentLayout.fragmentsStack.isEmpty() && parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) != this) {
                BaseFragment fragment = parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1);
                removeSelfFromStack();
                fragment.finishFragment();
            } else {
                finishFragment();
            }
        }
    }

    private void updateBottomOverlay() {
        if (bottomOverlayChatText == null) {
            return;
        }
        if (currentChat != null) {
            if (ChatObject.isChannel(currentChat) && !(currentChat instanceof TLRPC.TL_channelForbidden)) {
                if (ChatObject.isNotInChat(currentChat)) {
                    bottomOverlayChatText.setText(LocaleController.getString("ChannelJoin", R.string.ChannelJoin));
                } else {
                    if (!MessagesController.getInstance().isDialogMuted(dialog_id)) {
                        bottomOverlayChatText.setText(LocaleController.getString("ChannelMute", R.string.ChannelMute));
                    } else {
                        bottomOverlayChatText.setText(LocaleController.getString("ChannelUnmute", R.string.ChannelUnmute));
                    }
                }
            } else {
                bottomOverlayChatText.setText(LocaleController.getString("DeleteThisGroup", R.string.DeleteThisGroup));
            }
        } else {
            if (userBlocked) {
                if (currentUser.bot) {
                    bottomOverlayChatText.setText(LocaleController.getString("BotUnblock", R.string.BotUnblock));
                } else {
                    bottomOverlayChatText.setText(LocaleController.getString("Unblock", R.string.Unblock));
                }
                if (botButtons != null) {
                    botButtons = null;
                    if (chatActivityEnterView != null) {
                        if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                            botReplyButtons = null;
                            showReplyPanel(false, null, null, null, false, true);
                        }
                        chatActivityEnterView.setButtons(botButtons, false);
                    }
                }
            } else if (botUser != null && currentUser.bot) {
                bottomOverlayChatText.setText(LocaleController.getString("BotStart", R.string.BotStart));
                chatActivityEnterView.hidePopup(false);
                if (getParentActivity() != null) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            } else {
                bottomOverlayChatText.setText(LocaleController.getString("DeleteThisChat", R.string.DeleteThisChat));
            }
        }
        if (currentChat != null && (ChatObject.isNotInChat(currentChat) || !ChatObject.canWriteToChat(currentChat)) ||
                currentUser != null && (UserObject.isDeleted(currentUser) || userBlocked)) {
            bottomOverlayChat.setVisibility(View.VISIBLE);
            muteItem.setVisibility(View.GONE);
            chatActivityEnterView.setFieldFocused(false);
            chatActivityEnterView.setVisibility(View.INVISIBLE);
        } else {
            if (botUser != null && currentUser.bot) {
                bottomOverlayChat.setVisibility(View.VISIBLE);
                chatActivityEnterView.setVisibility(View.INVISIBLE);
            } else {
                chatActivityEnterView.setVisibility(View.VISIBLE);
                bottomOverlayChat.setVisibility(View.INVISIBLE);
            }
            muteItem.setVisibility(View.VISIBLE);
        }
        checkRaiseSensors();
    }

    public void showAlert(TLRPC.User user, String message) {
        if (alertView == null || user == null || message == null) {
            return;
        }

        if (alertView.getTag() != null) {
            alertView.setTag(null);
            if (alertViewAnimator != null) {
                alertViewAnimator.cancel();
                alertViewAnimator = null;
            }
            if (Build.VERSION.SDK_INT >= 11) {
                alertView.setVisibility(View.VISIBLE);
                alertViewAnimator = new AnimatorSetProxy();
                alertViewAnimator.playTogether(ObjectAnimatorProxy.ofFloat(alertView, "translationY", 0));
                alertViewAnimator.setDuration(200);
                alertViewAnimator.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                            alertView.clearAnimation();
                            alertViewAnimator = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Object animation) {
                        if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                            alertViewAnimator = null;
                        }
                    }
                });
                alertViewAnimator.start();
            } else {
                ViewProxy.setTranslationY(alertView, 0);
                alertView.clearAnimation();
                alertView.setVisibility(View.VISIBLE);
            }
        }
        alertNameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
        alertTextView.setText(Emoji.replaceEmoji(message.replace('\n', ' '), alertTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
        if (hideAlertViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideAlertViewRunnable);
        }
        AndroidUtilities.runOnUIThread(hideAlertViewRunnable = new Runnable() {
            @Override
            public void run() {
                if (hideAlertViewRunnable != this) {
                    return;
                }
                if (alertView.getTag() == null) {
                    alertView.setTag(1);
                    if (alertViewAnimator != null) {
                        alertViewAnimator.cancel();
                        alertViewAnimator = null;
                    }
                    if (Build.VERSION.SDK_INT >= 11) {
                        alertViewAnimator = new AnimatorSetProxy();
                        alertViewAnimator.playTogether(ObjectAnimatorProxy.ofFloat(alertView, "translationY", -AndroidUtilities.dp(50)));
                        alertViewAnimator.setDuration(200);
                        alertViewAnimator.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                                    alertView.clearAnimation();
                                    alertView.setVisibility(View.GONE);
                                    alertViewAnimator = null;
                                }
                            }

                            @Override
                            public void onAnimationCancel(Object animation) {
                                if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                                    alertViewAnimator = null;
                                }
                            }
                        });
                        alertViewAnimator.start();
                    } else {
                        ViewProxy.setTranslationY(alertView, -AndroidUtilities.dp(50));
                        alertView.clearAnimation();
                        alertView.setVisibility(View.GONE);
                    }
                }
            }
        }, 3000);
    }

    private void hidePinnedMessageView(boolean animated) {
        if (pinnedMessageView.getTag() == null) {
            pinnedMessageView.setTag(1);
            if (pinnedMessageViewAnimator != null) {
                pinnedMessageViewAnimator.cancel();
                pinnedMessageViewAnimator = null;
            }
            if (Build.VERSION.SDK_INT >= 11 && animated) {
                pinnedMessageViewAnimator = new AnimatorSetProxy();
                pinnedMessageViewAnimator.playTogether(ObjectAnimatorProxy.ofFloat(pinnedMessageView, "translationY", -AndroidUtilities.dp(50)));
                pinnedMessageViewAnimator.setDuration(200);
                pinnedMessageViewAnimator.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                            pinnedMessageView.clearAnimation();
                            pinnedMessageView.setVisibility(View.GONE);
                            pinnedMessageViewAnimator = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Object animation) {
                        if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                            pinnedMessageViewAnimator = null;
                        }
                    }
                });
                pinnedMessageViewAnimator.start();
            } else {
                ViewProxy.setTranslationY(pinnedMessageView, -AndroidUtilities.dp(50));
                pinnedMessageView.clearAnimation();
                pinnedMessageView.setVisibility(View.GONE);
            }
        }
    }

    private void updatePinnedMessageView(boolean animated) {
        if (pinnedMessageView == null) {
            return;
        }
        if (info != null) {
            if (pinnedMessageObject != null && info.pinned_msg_id != pinnedMessageObject.getId()) {
                pinnedMessageObject = null;
            }
            if (info.pinned_msg_id != 0 && pinnedMessageObject == null) {
                pinnedMessageObject = messagesDict[0].get(info.pinned_msg_id);
            }
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        if (info == null || info.pinned_msg_id == 0 || info.pinned_msg_id == preferences.getInt("pin_" + dialog_id, 0) || actionBar != null && actionBar.isActionModeShowed()) {
            hidePinnedMessageView(animated);
        } else {
            if (pinnedMessageObject != null) {
                if (pinnedMessageView.getTag() != null) {
                    pinnedMessageView.setTag(null);
                    if (pinnedMessageViewAnimator != null) {
                        pinnedMessageViewAnimator.cancel();
                        pinnedMessageViewAnimator = null;
                    }
                    if (Build.VERSION.SDK_INT >= 11 && animated) {
                        pinnedMessageView.setVisibility(View.VISIBLE);
                        pinnedMessageViewAnimator = new AnimatorSetProxy();
                        pinnedMessageViewAnimator.playTogether(ObjectAnimatorProxy.ofFloat(pinnedMessageView, "translationY", 0));
                        pinnedMessageViewAnimator.setDuration(200);
                        pinnedMessageViewAnimator.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                                    pinnedMessageView.clearAnimation();
                                    pinnedMessageViewAnimator = null;
                                }
                            }

                            @Override
                            public void onAnimationCancel(Object animation) {
                                if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                                    pinnedMessageViewAnimator = null;
                                }
                            }
                        });
                        pinnedMessageViewAnimator.start();
                    } else {
                        ViewProxy.setTranslationY(pinnedMessageView, 0);
                        pinnedMessageView.clearAnimation();
                        pinnedMessageView.setVisibility(View.VISIBLE);
                    }
                }
                pinnedMessageNameTextView.setText(LocaleController.getString("PinnedMessage", R.string.PinnedMessage));
                if (pinnedMessageObject.messageText != null) {
                    String mess = pinnedMessageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    pinnedMessageTextView.setText(Emoji.replaceEmoji(mess, pinnedMessageTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                }
            } else {
                hidePinnedMessageView(animated);
                if (loadingPinnedMessage != info.pinned_msg_id) {
                    loadingPinnedMessage = info.pinned_msg_id;
                    MessagesQuery.loadPinnedMessage(currentChat.id, info.pinned_msg_id, true);
                }
            }
        }
        checkListViewPaddings();
    }

    private void updateSpamView() {
        if (reportSpamView == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        boolean show = preferences.getInt("spam3_" + dialog_id, 0) == 2;
        if (show) {
            if (messages.isEmpty()) {
                show = false;
            } else {
                int count = messages.size() - 1;
                for (int a = count; a >= Math.max(count - 50, 0); a--) {
                    MessageObject messageObject = messages.get(a);
                    if (messageObject.isOut()) {
                        show = false;
                        break;
                    }
                }
            }
        }
        if (!show) {
            if (reportSpamView.getTag() == null) {
                reportSpamView.setTag(1);
                if (Build.VERSION.SDK_INT >= 11) {
                    if (reportSpamViewAnimator != null) {
                        reportSpamViewAnimator.cancel();
                    }
                    reportSpamViewAnimator = new AnimatorSetProxy();
                    reportSpamViewAnimator.playTogether(ObjectAnimatorProxy.ofFloat(reportSpamView, "translationY", -AndroidUtilities.dp(50)));
                    reportSpamViewAnimator.setDuration(200);
                    reportSpamViewAnimator.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                                reportSpamView.clearAnimation();
                                reportSpamView.setVisibility(View.GONE);
                                reportSpamViewAnimator = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                                reportSpamViewAnimator = null;
                            }
                        }
                    });
                    reportSpamViewAnimator.start();
                } else {
                    reportSpamView.setVisibility(View.GONE);
                }
            }
        } else {
            if (reportSpamView.getTag() != null) {
                reportSpamView.setTag(null);
                if (Build.VERSION.SDK_INT >= 11) {
                    reportSpamView.setVisibility(View.VISIBLE);
                    if (reportSpamViewAnimator != null) {
                        reportSpamViewAnimator.cancel();
                    }
                    reportSpamViewAnimator = new AnimatorSetProxy();
                    reportSpamViewAnimator.playTogether(ObjectAnimatorProxy.ofFloat(reportSpamView, "translationY", 0));
                    reportSpamViewAnimator.setDuration(200);
                    reportSpamViewAnimator.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                                reportSpamView.clearAnimation();
                                reportSpamViewAnimator = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                                reportSpamViewAnimator = null;
                            }
                        }
                    });
                    reportSpamViewAnimator.start();
                } else {
                    reportSpamView.setVisibility(View.VISIBLE);
                }
            }
        }
        checkListViewPaddings();
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
                    || UserObject.isDeleted(currentUser)
                    || ContactsController.getInstance().isLoadingContacts()
                    || (currentUser.phone != null && currentUser.phone.length() != 0 && ContactsController.getInstance().contactsDict.get(currentUser.id) != null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts()))) {
                addContactItem.setVisibility(View.GONE);
            } else {
                addContactItem.setVisibility(View.VISIBLE);
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    addContactItem.setText(LocaleController.getString("AddToContacts", R.string.AddToContacts));
                    reportSpamButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(50), 0);
                    addToContactsButton.setVisibility(View.VISIBLE);
                    reportSpamContainer.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, Gravity.LEFT | Gravity.TOP, 0, 0, 0, AndroidUtilities.dp(1)));
                } else {
                    addContactItem.setText(LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
                    addToContactsButton.setVisibility(View.GONE);
                    reportSpamButton.setPadding(AndroidUtilities.dp(50), 0, AndroidUtilities.dp(50), 0);
                    reportSpamContainer.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.LEFT | Gravity.TOP, 0, 0, 0, AndroidUtilities.dp(1)));
                }
            }
        }
        checkListViewPaddings();
    }

    private void checkListViewPaddings() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
                    int top = 0;
                    if (firstVisPos != RecyclerView.NO_POSITION) {
                        View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                        top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                    }
                    if (chatListView.getPaddingTop() != AndroidUtilities.dp(52) && (pinnedMessageView != null && pinnedMessageView.getTag() == null || reportSpamView != null && reportSpamView.getTag() == null)) {
                        chatListView.setPadding(0, AndroidUtilities.dp(52), 0, AndroidUtilities.dp(3));
                        chatListView.setTopGlowOffset(AndroidUtilities.dp(48));
                        top -= AndroidUtilities.dp(48);
                    } else if (chatListView.getPaddingTop() != AndroidUtilities.dp(4) && (pinnedMessageView == null || pinnedMessageView.getTag() != null) && (reportSpamView == null || reportSpamView.getTag() != null)) {
                        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
                        chatListView.setTopGlowOffset(0);
                        top += AndroidUtilities.dp(48);
                    } else {
                        firstVisPos = RecyclerView.NO_POSITION;
                    }
                    if (firstVisPos != RecyclerView.NO_POSITION) {
                        chatLayoutManager.scrollToPositionWithOffset(firstVisPos, top);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private void checkRaiseSensors() {
        if (!ApplicationLoader.mainInterfacePaused && (bottomOverlayChat == null || bottomOverlayChat.getVisibility() != View.VISIBLE) && (bottomOverlay == null || bottomOverlay.getVisibility() != View.VISIBLE)) {
            MediaController.getInstance().setAllowStartRecord(true);
        } else {
            MediaController.getInstance().setAllowStartRecord(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        MediaController.getInstance().startRaiseToEarSensors(this);
        checkRaiseSensors();

        checkActionBarMenu();
        if (replyImageLocation != null && replyImageView != null) {
            replyImageView.setImage(replyImageLocation, "50_50", (Drawable) null);
        }

        NotificationsController.getInstance().setOpenedDialogId(dialog_id);
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && scrollToMessage != null) {
                if (chatListView != null) {
                    int yOffset;
                    if (scrollToMessagePosition == -9000) {
                        yOffset = Math.max(0, (chatListView.getHeight() - scrollToMessage.getApproximateHeight()) / 2);
                    } else if (scrollToMessagePosition == -10000) {
                        yOffset = 0;
                    } else {
                        yOffset = scrollToMessagePosition;
                    }
                    chatLayoutManager.scrollToPositionWithOffset(messages.size() - messages.indexOf(scrollToMessage), -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
                }
            } else {
                moveScrollToLastMessage();
            }
            scrollToTopUnReadOnResume = false;
            scrollToTopOnResume = false;
            scrollToMessage = null;
        }
        paused = false;
        if (readWhenResume && !messages.isEmpty()) {
            for (MessageObject messageObject : messages) {
                if (!messageObject.isUnread() && !messageObject.isOut()) {
                    break;
                }
                if (!messageObject.isOut()) {
                    messageObject.setIsRead();
                }
            }
            readWhenResume = false;
            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).getId(), readWithMid, readWithDate, true, false);
        }
        checkScrollForLoad(false);
        if (wasPaused) {
            wasPaused = false;
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }

        fixLayout();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (chatActivityEnterView.getFieldText() == null) {
            String lastMessageText = preferences.getString("dialog_" + dialog_id, null);
            if (lastMessageText != null) {
                preferences.edit().remove("dialog_" + dialog_id).commit();
                chatActivityEnterView.setFieldText(lastMessageText);
                if (getArguments().getBoolean("hasUrl", false)) {
                    chatActivityEnterView.setSelection(lastMessageText.indexOf('\n') + 1);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (chatActivityEnterView != null) {
                                chatActivityEnterView.setFieldFocused(true);
                                chatActivityEnterView.openKeyboard();
                            }
                        }
                    }, 700);
                }
            }
        } else {
            preferences.edit().remove("dialog_" + dialog_id).commit();
        }
        if (replyingMessageObject == null) {
            String lastReplyMessage = preferences.getString("reply_" + dialog_id, null);
            if (lastReplyMessage != null && lastReplyMessage.length() != 0) {
                preferences.edit().remove("reply_" + dialog_id).commit();
                try {
                    byte[] bytes = Base64.decode(lastReplyMessage, Base64.DEFAULT);
                    if (bytes != null) {
                        SerializedData data = new SerializedData(bytes);
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message != null) {
                            replyingMessageObject = new MessageObject(message, MessagesController.getInstance().getUsers(), false);
                            showReplyPanel(true, replyingMessageObject, null, null, false, false);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        } else {
            preferences.edit().remove("reply_" + dialog_id).commit();
        }
        if (bottomOverlayChat.getVisibility() != View.VISIBLE) {
            chatActivityEnterView.setFieldFocused(true);
        }
        chatActivityEnterView.onResume();
        if (currentEncryptedChat != null) {
            chatEnterTime = System.currentTimeMillis();
            chatLeaveTime = 0;
        }

        if (startVideoEdit != null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    openVideoEditor(startVideoEdit, false, false);
                    startVideoEdit = null;
                }
            });
        }

        if (chatActivityEnterView == null || !chatActivityEnterView.isEditingMessage()) {
            chatListView.setOnItemLongClickListener(onItemLongClickListener);
            chatListView.setOnItemClickListener(onItemClickListener);
            chatListView.setLongClickable(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MediaController.getInstance().stopRaiseToEarSensors(this);
        if (menuItem != null) {
            menuItem.closeSubMenu();
        }
        paused = true;
        wasPaused = true;
        NotificationsController.getInstance().setOpenedDialogId(0);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onPause();
            if (!chatActivityEnterView.isEditingMessage()) {
                String text = chatActivityEnterView.getFieldText();
                if (text != null && !text.equals("@gif ")) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("dialog_" + dialog_id, text);
                    editor.commit();
                }
            }
            chatActivityEnterView.setFieldFocused(false);
        }
        if (replyingMessageObject != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            try {
                SerializedData data = new SerializedData();
                replyingMessageObject.messageOwner.serializeToStream(data);
                String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                if (string.length() != 0) {
                    editor.putString("reply_" + dialog_id, string);
                }
            } catch (Exception e) {
                editor.remove("reply_" + dialog_id);
                FileLog.e("tmessages", e);
            }
            editor.commit();
        }

        MessagesController.getInstance().cancelTyping(0, dialog_id);

        if (currentEncryptedChat != null) {
            chatLeaveTime = System.currentTimeMillis();
            updateInformationForScreenshotDetector();
        }
    }

    private void updateInformationForScreenshotDetector() {
        if (currentEncryptedChat == null) {
            return;
        }
        ArrayList<Long> visibleMessages = new ArrayList<>();
        if (chatListView != null) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                MessageObject object = null;
                if (view instanceof ChatBaseCell) {
                    ChatBaseCell cell = (ChatBaseCell) view;
                    object = cell.getMessageObject();
                }
                if (object != null && object.getId() < 0 && object.messageOwner.random_id != 0) {
                    visibleMessages.add(object.messageOwner.random_id);
                }
            }
        }
        MediaController.getInstance().setLastEncryptedChatParams(chatEnterTime, chatLeaveTime, currentEncryptedChat, visibleMessages);
    }

    private boolean fixLayoutInternal() {
        if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            selectedMessagesCountTextView.setTextSize(18);
            actionModeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        } else {
            selectedMessagesCountTextView.setTextSize(20);
            actionModeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        }

        if (AndroidUtilities.isTablet()) {
            if (AndroidUtilities.isSmallTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                actionBar.setBackButtonDrawable(new BackDrawable(false));
                if (playerView != null && playerView.getParent() == null) {
                    ((ViewGroup) fragmentView).addView(playerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
                }
            } else {
                actionBar.setBackButtonDrawable(new BackDrawable(parentLayout == null || parentLayout.fragmentsStack.isEmpty() || parentLayout.fragmentsStack.get(0) == ChatActivity.this || parentLayout.fragmentsStack.size() == 1));
                if (playerView != null && playerView.getParent() != null) {
                    fragmentView.setPadding(0, 0, 0, 0);
                    ((ViewGroup) fragmentView).removeView(playerView);
                }
            }
            return false;
        }
        return true;
    }

    private void fixLayout() {
        if (avatarContainer != null) {
            avatarContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (avatarContainer != null) {
                        avatarContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return fixLayoutInternal();
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        fixLayout();
    }

    private void switchImportantMode(MessageObject searchBeforeMessage) {
        int count = chatListView.getChildCount();
        MessageObject messageObject = null;
        if (searchBeforeMessage == null) {
            for (int a = 0; a <= count; a++) {
                View child = chatListView.getChildAt(a);
                MessageObject message = null;
                if (child instanceof ChatBaseCell) {
                    message = ((ChatBaseCell) child).getMessageObject();
                } else if (child instanceof ChatActionCell) {
                    message = ((ChatActionCell) child).getMessageObject();
                }
                if (message != null && message.getId() > 0) {
                    if (message.isImportant()) {
                        messageObject = message;
                        break;
                    } else if (searchBeforeMessage == null) {
                        searchBeforeMessage = message;
                    }
                }
            }
        }

        if (messageObject == null) {
            int index = messages.indexOf(searchBeforeMessage);
            if (index >= 0) {
                for (int a = index + 1; a < messages.size(); a++) {
                    MessageObject message = messages.get(a);
                    if (message.getId() > 0 && message.isImportant()) {
                        messageObject = message;
                        break;
                    }
                }
            }
        }

        if (messageObject != null) {
            scrollToMessagePosition = -10000;
            for (int a = 0; a < count; a++) {
                View child = chatListView.getChildAt(a);
                MessageObject message = null;
                if (child instanceof ChatBaseCell) {
                    message = ((ChatBaseCell) child).getMessageObject();
                } else if (child instanceof ChatActionCell) {
                    message = ((ChatActionCell) child).getMessageObject();
                }
                if (message == messageObject) {
                    scrollToMessagePosition = child.getTop() + AndroidUtilities.dp(7);
                    break;
                }
            }
            if (scrollToMessagePosition == -10000) {
                scrollToMessagePosition = chatListView.getPaddingTop();
            }
        }

        avatarContainer.setRadioChecked(!avatarContainer.isRadioChecked(), true);
        channelMessagesImportant = avatarContainer.isRadioChecked() ? 1 : 2;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("important_" + dialog_id, channelMessagesImportant).commit();
        waitingForImportantLoad = true;
        waitingForLoad.add(lastLoadIndex);
        if (messageObject != null) {
            startLoadFromMessageId = messageObject.getId();
            MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, true, 0, classGuid, 3, 0, channelMessagesImportant, lastLoadIndex++);
        } else {
            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, true, 0, classGuid, 0, 0, channelMessagesImportant, lastLoadIndex++);
        }
    }

    private void createDeleteMessagesAlert(final MessageObject finalSelectedObject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage(LocaleController.formatString("AreYouSureDeleteMessages", R.string.AreYouSureDeleteMessages, LocaleController.formatPluralString("messages", finalSelectedObject != null ? 1 : selectedMessagesIds[0].size() + selectedMessagesIds[1].size())));
        builder.setTitle(LocaleController.getString("Message", R.string.Message));

        final boolean[] checks = new boolean[3];
        TLRPC.User user = null;
        if (currentChat != null && currentChat.megagroup) {
            if (finalSelectedObject != null) {
                if (finalSelectedObject.messageOwner.action == null || finalSelectedObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) {
                    user = MessagesController.getInstance().getUser(finalSelectedObject.messageOwner.from_id);
                }
            } else {
                int from_id = -1;
                for (int a = 1; a >= 0; a--) {
                    int channelId = 0;
                    for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesIds[a].entrySet()) {
                        MessageObject msg = entry.getValue();
                        if (from_id == -1) {
                            from_id = msg.messageOwner.from_id;
                        }
                        if (from_id < 0 || from_id != msg.messageOwner.from_id) {
                            from_id = -2;
                            break;
                        }
                    }
                    if (from_id == -2) {
                        break;
                    }
                }
                if (from_id != -1) {
                    user = MessagesController.getInstance().getUser(from_id);
                }
            }
            if (user != null && user.id != UserConfig.getClientUserId()) {
                FrameLayout frameLayout = new FrameLayout(getParentActivity());
                if (Build.VERSION.SDK_INT >= 21) {
                    frameLayout.setPadding(0, AndroidUtilities.dp(8), 0, 0);
                }
                for (int a = 0; a < 3; a++) {
                    CheckBoxCell cell = new CheckBoxCell(getParentActivity());
                    cell.setBackgroundResource(R.drawable.list_selector);
                    cell.setTag(a);
                    if (Build.VERSION.SDK_INT < 11) {
                        cell.setTextColor(0xffffffff);
                    }
                    if (a == 0) {
                        cell.setText(LocaleController.getString("DeleteBanUser", R.string.DeleteBanUser), "", false, false);
                    } else if (a == 1) {
                        cell.setText(LocaleController.getString("DeleteReportSpam", R.string.DeleteReportSpam), "", false, false);
                    } else if (a == 2) {
                        cell.setText(LocaleController.formatString("DeleteAllFrom", R.string.DeleteAllFrom, ContactsController.formatName(user.first_name, user.last_name)), "", false, false);
                    }
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(8) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(8), 0);
                    frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 8, 48 * a, 8, 0));
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            CheckBoxCell cell = (CheckBoxCell) v;
                            Integer num = (Integer) cell.getTag();
                            checks[num] = !checks[num];
                            cell.setChecked(checks[num], true);
                        }
                    });
                }
                builder.setView(frameLayout);
            } else {
                user = null;
            }
        }
        final TLRPC.User userFinal = user;
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ArrayList<Integer> ids = null;
                if (finalSelectedObject != null) {
                    ids = new ArrayList<>();
                    ids.add(finalSelectedObject.getId());
                    ArrayList<Long> random_ids = null;
                    if (currentEncryptedChat != null && finalSelectedObject.messageOwner.random_id != 0 && finalSelectedObject.type != 10) {
                        random_ids = new ArrayList<>();
                        random_ids.add(finalSelectedObject.messageOwner.random_id);
                    }
                    MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat, finalSelectedObject.messageOwner.to_id.channel_id);
                } else {
                    for (int a = 1; a >= 0; a--) {
                        ids = new ArrayList<>(selectedMessagesIds[a].keySet());
                        ArrayList<Long> random_ids = null;
                        int channelId = 0;
                        if (!ids.isEmpty()) {
                            MessageObject msg = selectedMessagesIds[a].get(ids.get(0));
                            if (channelId == 0 && msg.messageOwner.to_id.channel_id != 0) {
                                channelId = msg.messageOwner.to_id.channel_id;
                            }
                        }
                        if (currentEncryptedChat != null) {
                            random_ids = new ArrayList<>();
                            for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesIds[a].entrySet()) {
                                MessageObject msg = entry.getValue();
                                if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                    random_ids.add(msg.messageOwner.random_id);
                                }
                            }
                        }
                        MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat, channelId);
                    }
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                }
                if (userFinal != null) {
                    if (checks[0]) {
                        MessagesController.getInstance().deleteUserFromChat(currentChat.id, userFinal, info);
                    }
                    if (checks[1]) {
                        TLRPC.TL_channels_reportSpam req = new TLRPC.TL_channels_reportSpam();
                        req.channel = MessagesController.getInputChannel(currentChat);
                        req.user_id = MessagesController.getInputUser(userFinal);
                        req.id = ids;
                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(TLObject response, TLRPC.TL_error error) {

                            }
                        });
                    }
                    if (checks[2]) {
                        MessagesController.getInstance().deleteUserChannelHistory(currentChat, userFinal, 0);
                    }
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void createMenu(View v, boolean single) {
        if (actionBar.isActionModeShowed()) {
            return;
        }

        MessageObject message = null;
        if (v instanceof ChatBaseCell) {
            message = ((ChatBaseCell) v).getMessageObject();
        } else if (v instanceof ChatActionCell) {
            message = ((ChatActionCell) v).getMessageObject();
        }
        if (message == null) {
            return;
        }
        final int type = getMessageType(message);
        if (channelMessagesImportant == 2 && message.getId() == 0 && message.contentType == 1 && message.type == 10 && message.messageOwner.from_id != 0) {
            switchImportantMode(message);
            return;
        }
        if (single && message.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
            scrollToMessageId(message.messageOwner.reply_to_msg_id, 0, true, 0);
            return;
        }

        selectedObject = null;
        forwaringMessage = null;
        for (int a = 1; a >= 0; a--) {
            selectedMessagesCanCopyIds[a].clear();
            selectedMessagesIds[a].clear();
        }
        cantDeleteMessagesCount = 0;
        actionBar.hideActionMode();
        updatePinnedMessageView(true);

        boolean allowChatActions = true;
        boolean allowPin = message.getDialogId() != mergeDialogId && message.getId() > 0 && ChatObject.isChannel(currentChat) && currentChat.megagroup && (currentChat.creator || currentChat.editor) && (message.messageOwner.action == null || message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
        boolean allowUnpin = message.getDialogId() != mergeDialogId && info != null && info.pinned_msg_id == message.getId() && (currentChat.creator || currentChat.editor);
        boolean allowEdit = message.canEditMessage(currentChat) && !chatActivityEnterView.hasAudioToSend();
        if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 || type == 1 && message.getDialogId() == mergeDialogId || currentEncryptedChat == null && message.getId() < 0 || isBroadcast || currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !currentChat.creator && !currentChat.editor && !currentChat.megagroup)) {
            allowChatActions = false;
        }

        if (single || type < 2 || type == 20) {
            if (type >= 0) {
                selectedObject = message;
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                ArrayList<CharSequence> items = new ArrayList<>();
                final ArrayList<Integer> options = new ArrayList<>();

                if (type == 0) {
                    items.add(LocaleController.getString("Retry", R.string.Retry));
                    options.add(0);
                    items.add(LocaleController.getString("Delete", R.string.Delete));
                    options.add(1);
                } else if (type == 1) {
                    if (currentChat != null && !isBroadcast) {
                        if (allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                        }
                        if (allowUnpin) {
                            items.add(LocaleController.getString("UnpinMessage", R.string.UnpinMessage));
                            options.add(14);
                        } else if (allowPin) {
                            items.add(LocaleController.getString("PinMessage", R.string.PinMessage));
                            options.add(13);
                        }
                        if (allowEdit) {
                            items.add(LocaleController.getString("Edit", R.string.Edit));
                            options.add(12);
                        }
                        if (message.canDeleteMessage(currentChat)) {
                            items.add(LocaleController.getString("Delete", R.string.Delete));
                            options.add(1);
                        }
                    } else {
                        if (message.canDeleteMessage(currentChat)) {
                            items.add(LocaleController.getString("Delete", R.string.Delete));
                            options.add(1);
                        }
                    }
                } else if (type == 20) {
                    items.add(LocaleController.getString("Retry", R.string.Retry));
                    options.add(0);
                    items.add(LocaleController.getString("Copy", R.string.Copy));
                    options.add(3);
                    items.add(LocaleController.getString("Delete", R.string.Delete));
                    options.add(1);
                } else {
                    if (currentEncryptedChat == null) {
                        if (allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                        }
                        if (selectedObject.type == 0 || selectedObject.caption != null) {
                            items.add(LocaleController.getString("Copy", R.string.Copy));
                            options.add(3);
                        }
                        if (type == 3) {
                            if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && MessageObject.isNewGifDocument(selectedObject.messageOwner.media.webpage.document)) {
                                items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                                options.add(11);
                            }
                        } else if (type == 4) {
                            if (selectedObject.isVideo()) {
                                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                options.add(4);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                            } else if (selectedObject.isMusic()) {
                                items.add(LocaleController.getString("SaveToMusic", R.string.SaveToMusic));
                                options.add(10);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                            } else if (selectedObject.getDocument() != null) {
                                if (MessageObject.isNewGifDocument(selectedObject.getDocument())) {
                                    items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                                    options.add(11);
                                }
                                items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                                options.add(10);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                            } else {
                                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                options.add(4);
                            }
                        } else if (type == 5) {
                            items.add(LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile));
                            options.add(5);
                            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                            options.add(6);
                        } else if (type == 6) {
                            items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                            options.add(7);
                            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                            options.add(10);
                            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                            options.add(6);
                        } else if (type == 7) {
                            items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
                            options.add(9);
                        } else if (type == 8) {
                            TLRPC.User user = MessagesController.getInstance().getUser(selectedObject.messageOwner.media.user_id);
                            if (user != null && user.id != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(user.id) == null) {
                                items.add(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
                                options.add(15);
                            }
                            if (selectedObject.messageOwner.media.phone_number != null || selectedObject.messageOwner.media.phone_number.length() != 0) {
                                items.add(LocaleController.getString("Copy", R.string.Copy));
                                options.add(16);
                                items.add(LocaleController.getString("Call", R.string.Call));
                                options.add(17);
                            }
                        }
                        items.add(LocaleController.getString("Forward", R.string.Forward));
                        options.add(2);
                        if (allowUnpin) {
                            items.add(LocaleController.getString("UnpinMessage", R.string.UnpinMessage));
                            options.add(14);
                        } else if (allowPin) {
                            items.add(LocaleController.getString("PinMessage", R.string.PinMessage));
                            options.add(13);
                        }
                        if (allowEdit) {
                            items.add(LocaleController.getString("Edit", R.string.Edit));
                            options.add(12);
                        }
                        if (message.canDeleteMessage(currentChat)) {
                            items.add(LocaleController.getString("Delete", R.string.Delete));
                            options.add(1);
                        }
                    } else {
                        if (allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                        }
                        if (selectedObject.type == 0 || selectedObject.caption != null) {
                            items.add(LocaleController.getString("Copy", R.string.Copy));
                            options.add(3);
                        }
                        if (type == 4) {
                            if (selectedObject.isVideo()) {
                                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                options.add(4);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                            } else if (selectedObject.isMusic()) {
                                items.add(LocaleController.getString("SaveToMusic", R.string.SaveToMusic));
                                options.add(10);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                            } else if (!selectedObject.isVideo() && selectedObject.getDocument() != null) {
                                items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                                options.add(10);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                            } else {
                                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                options.add(4);
                            }
                        } else if (type == 5) {
                            items.add(LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile));
                            options.add(5);
                        } else if (type == 7) {
                            items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
                            options.add(9);
                        }
                        items.add(LocaleController.getString("Delete", R.string.Delete));
                        options.add(1);
                    }
                }

                if (options.isEmpty()) {
                    return;
                }
                final CharSequence[] finalItems = items.toArray(new CharSequence[items.size()]);
                builder.setItems(finalItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (selectedObject == null || i < 0 || i >= options.size()) {
                            return;
                        }
                        processSelectedOption(options.get(i));
                    }
                });

                builder.setTitle(LocaleController.getString("Message", R.string.Message));
                showDialog(builder.create());
            }
            return;
        }

        final ActionBarMenu actionMode = actionBar.createActionMode();
        View item = actionMode.getItem(forward);
        if (item != null) {
            item.setVisibility(View.VISIBLE);
        }
        item = actionMode.getItem(delete);
        if (item != null) {
            item.setVisibility(View.VISIBLE);
        }
        item = actionMode.getItem(edit_done);
        if (item != null) {
            item.setVisibility(View.GONE);
        }

        actionBar.showActionMode();
        updatePinnedMessageView(true);

        if (Build.VERSION.SDK_INT >= 11) {
            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            ArrayList<Object> animators = new ArrayList<>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View view = actionModeViews.get(a);
                AndroidUtilities.clearDrawableAnimation(view);
                animators.add(ObjectAnimatorProxy.ofFloat(view, "scaleY", 0.1f, 1.0f));
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(250);
            animatorSet.start();
        }

        addToSelectedMessages(message);
        selectedMessagesCountTextView.setNumber(1, false);
        updateVisibleRows();
    }

    private void processSelectedOption(int option) {
        if (selectedObject == null) {
            return;
        }
        switch (option) {
            case 0: {
                if (SendMessagesHelper.getInstance().retrySendMessage(selectedObject, false)) {
                    moveScrollToLastMessage();
                }
                break;
            }
            case 1: {
                if (getParentActivity() == null) {
                    selectedObject = null;
                    return;
                }
                createDeleteMessagesAlert(selectedObject);
                break;
            }
            case 2: {
                forwaringMessage = selectedObject;
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 1);
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate(this);
                presentFragment(fragment);
                break;
            }
            case 3: {
                try {
                    CharSequence str;
                    if (selectedObject.type == 0 && selectedObject.messageOwner.message != null) {
                        str = selectedObject.messageOwner.message;
                    } else if (selectedObject.messageOwner.media != null && selectedObject.messageOwner.media.caption != null) {
                        str = selectedObject.messageOwner.media.caption;
                    } else {
                        str = selectedObject.messageText;
                    }
                    if (Build.VERSION.SDK_INT < 11) {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(str);
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", str);
                        clipboard.setPrimaryClip(clip);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                break;
            }
            case 4: {
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
                if (selectedObject.type == 3 || selectedObject.type == 1) {
                    if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                        selectedObject = null;
                        return;
                    }
                    MediaController.saveFile(path, getParentActivity(), selectedObject.type == 3 ? 1 : 0, null, null);
                }
                break;
            }
            case 5: {
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
                            selectedObject = null;
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("IncorrectLocalization", R.string.IncorrectLocalization));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(builder.create());
                    }
                }
                break;
            }
            case 6: {
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
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(selectedObject.getDocument().mime_type);
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
                getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                break;
            }
            case 7: {
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
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    return;
                }
                MediaController.saveFile(path, getParentActivity(), 0, null, null);
                break;
            }
            case 8: {
                showReplyPanel(true, selectedObject, null, null, false, true);
                break;
            }
            case 9: {
                showDialog(new StickersAlert(getParentActivity(), selectedObject.getInputStickerSet(), null, bottomOverlayChat.getVisibility() != View.VISIBLE ? chatActivityEnterView : null));
                break;
            }
            case 10: {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    return;
                }
                String fileName = FileLoader.getDocumentFileName(selectedObject.getDocument());
                if (fileName == null || fileName.length() == 0) {
                    fileName = selectedObject.getFileName();
                }
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
                MediaController.saveFile(path, getParentActivity(), selectedObject.isMusic() ? 3 : 2, fileName, selectedObject.getDocument() != null ? selectedObject.getDocument().mime_type : "");
                break;
            }
            case 11: {
                MediaController.SearchImage searchImage = MessagesController.getInstance().saveGif(selectedObject.getDocument());
                showGifHint();
                chatActivityEnterView.addRecentGif(searchImage);
                break;
            }
            case 12: {
                if (getParentActivity() == null) {
                    selectedObject = null;
                    return;
                }
                final MessageObject editingMessageObject = selectedObject;
                final ProgressDialog progressDialog = new ProgressDialog(getParentActivity());
                progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setCancelable(false);

                TLRPC.TL_messages_getMessageEditData req = new TLRPC.TL_messages_getMessageEditData();
                req.peer = MessagesController.getInputPeer((int) dialog_id);
                req.id = selectedObject.getId();
                final int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (!getParentActivity().isFinishing()) {
                                        progressDialog.dismiss();
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                                if (response != null) {
                                    TLRPC.TL_messages_messageEditData res = (TLRPC.TL_messages_messageEditData) response;
                                    if (mentionsAdapter != null) {
                                        mentionsAdapter.setNeedBotContext(false);
                                        chatListView.setOnItemLongClickListener(null);
                                        chatListView.setOnItemClickListener(null);
                                        chatListView.setClickable(false);
                                        chatListView.setLongClickable(false);
                                        chatActivityEnterView.setEditinigMessageObject(editingMessageObject, res.caption);
                                        actionModeTextView.setVisibility(View.VISIBLE);
                                        selectedMessagesCountTextView.setVisibility(View.GONE);

                                        chatActivityEnterView.setAllowStickersAndGifs(false, false);
                                        final ActionBarMenu actionMode = actionBar.createActionMode();
                                        actionMode.getItem(reply).setVisibility(View.GONE);
                                        actionMode.getItem(copy).setVisibility(View.GONE);
                                        actionMode.getItem(forward).setVisibility(View.GONE);
                                        actionMode.getItem(delete).setVisibility(View.GONE);
                                        actionMode.getItem(edit_done).setVisibility(View.VISIBLE);
                                        actionBar.showActionMode();
                                        updatePinnedMessageView(true);
                                    }
                                } else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setMessage(LocaleController.getString("EditMessageError", R.string.EditMessageError));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    showDialog(builder.create());
                                }
                            }
                        });
                    }
                });
                progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ConnectionsManager.getInstance().cancelRequest(reqId, true);
                        try {
                            dialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                });
                try {
                    progressDialog.show();
                } catch (Exception e) {
                    //don't promt
                }
                break;
            }
            case 13: {
                final int mid = selectedObject.getId();
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("PinMessageAlert", R.string.PinMessageAlert));

                final boolean[] checks = new boolean[]{true};
                FrameLayout frameLayout = new FrameLayout(getParentActivity());
                if (Build.VERSION.SDK_INT >= 21) {
                    frameLayout.setPadding(0, AndroidUtilities.dp(8), 0, 0);
                }
                CheckBoxCell cell = new CheckBoxCell(getParentActivity());
                cell.setBackgroundResource(R.drawable.list_selector);
                if (Build.VERSION.SDK_INT < 11) {
                    cell.setTextColor(0xffffffff);
                }
                cell.setText(LocaleController.getString("PinNotify", R.string.PinNotify), "", true, false);
                cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(8) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(8), 0);
                frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 8, 0, 8, 0));
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        checks[0] = !checks[0];
                        cell.setChecked(checks[0], true);
                    }
                });
                builder.setView(frameLayout);
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        MessagesController.getInstance().pinChannelMessage(currentChat, mid, checks[0]);
                    }
                });
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                break;
            }
            case 14: {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("UnpinMessageAlert", R.string.UnpinMessageAlert));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        MessagesController.getInstance().pinChannelMessage(currentChat, 0, false);
                    }
                });
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                break;
            }
            case 15: {
                Bundle args = new Bundle();
                args.putInt("user_id", selectedObject.messageOwner.media.user_id);
                args.putString("phone", selectedObject.messageOwner.media.phone_number);
                args.putBoolean("addContact", true);
                presentFragment(new ContactAddActivity(args));
                break;
            }
            case 16: {
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(selectedObject.messageOwner.media.phone_number);
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", selectedObject.messageOwner.media.phone_number);
                        clipboard.setPrimaryClip(clip);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                break;
            }
            case 17: {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + selectedObject.messageOwner.media.phone_number));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getParentActivity().startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                break;
            }
        }
        selectedObject = null;
    }

    @Override
    public void didSelectDialog(DialogsActivity activity, long did, boolean param) {
        if (dialog_id != 0 && (forwaringMessage != null || !selectedMessagesIds[0].isEmpty() || !selectedMessagesIds[1].isEmpty())) {
            ArrayList<MessageObject> fmessages = new ArrayList<>();
            if (forwaringMessage != null) {
                fmessages.add(forwaringMessage);
                forwaringMessage = null;
            } else {
                for (int a = 1; a >= 0; a--) {
                    ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds[a].keySet());
                    Collections.sort(ids);
                    for (int b = 0; b < ids.size(); b++) {
                        Integer id = ids.get(b);
                        MessageObject message = selectedMessagesIds[a].get(id);
                        if (message != null && id > 0) {
                            fmessages.add(message);
                        }
                    }
                    selectedMessagesCanCopyIds[a].clear();
                    selectedMessagesIds[a].clear();
                }
                cantDeleteMessagesCount = 0;
                actionBar.hideActionMode();
                updatePinnedMessageView(true);
            }

            if (did != dialog_id) {
                int lower_part = (int) did;
                if (lower_part != 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("scrollToTopOnResume", scrollToTopOnResume);
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args.putInt("chat_id", -lower_part);
                    }
                    if (!MessagesController.checkCanOpenChat(args, activity)) {
                        return;
                    }

                    ChatActivity chatActivity = new ChatActivity(args);
                    if (presentFragment(chatActivity, true)) {
                        chatActivity.showReplyPanel(true, null, fmessages, null, false, false);
                        if (!AndroidUtilities.isTablet()) {
                            removeSelfFromStack();
                        }
                    } else {
                        activity.finishFragment();
                    }
                } else {
                    activity.finishFragment();
                }
            } else {
                activity.finishFragment();
                moveScrollToLastMessage();
                showReplyPanel(true, null, fmessages, null, false, AndroidUtilities.isTablet());
                if (AndroidUtilities.isTablet()) {
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                }
                updateVisibleRows();
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (actionBar.isActionModeShowed()) {
            for (int a = 1; a >= 0; a--) {
                selectedMessagesIds[a].clear();
                selectedMessagesCanCopyIds[a].clear();
            }
            chatActivityEnterView.setEditinigMessageObject(null, false);
            actionBar.hideActionMode();
            updatePinnedMessageView(true);
            cantDeleteMessagesCount = 0;
            updateVisibleRows();
            return false;
        } else if (chatActivityEnterView.isPopupShowing()) {
            chatActivityEnterView.hidePopup(true);
            return false;
        }
        return true;
    }

    private void updateVisibleRows() {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatBaseCell) {
                ChatBaseCell cell = (ChatBaseCell) view;

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBar.isActionModeShowed()) {
                    MessageObject messageObject = cell.getMessageObject();
                    if (selectedMessagesIds[messageObject.getDialogId() == dialog_id ? 0 : 1].containsKey(messageObject.getId())) {
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
                cell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && cell.getMessageObject() != null && cell.getMessageObject().getId() == highlightMessageId);
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                cell.setMessageObject(cell.getMessageObject());
            }
        }
    }

    private ArrayList<MessageObject> createVoiceMessagesPlaylist(MessageObject startMessageObject, boolean playingUnreadMedia) {
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        messageObjects.add(startMessageObject);
        int messageId = startMessageObject.getId();
        if (messageId != 0) {
            boolean started = false;
            for (int a = messages.size() - 1; a >= 0; a--) {
                MessageObject messageObject = messages.get(a);
                if ((currentEncryptedChat == null && messageObject.getId() > messageId || currentEncryptedChat != null && messageObject.getId() < messageId) && messageObject.isVoice() && (!playingUnreadMedia || messageObject.isContentUnread() && !messageObject.isOut())) {
                    messageObjects.add(messageObject);
                }
            }
        }
        return messageObjects;
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
            builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.getDocument().mime_type));
        }
        showDialog(builder.create());
    }

    private void openSearchWithText(String text) {
        avatarContainer.setVisibility(View.GONE);
        headerItem.setVisibility(View.GONE);
        attachItem.setVisibility(View.GONE);
        if (searchUpItem.getVisibility() != View.VISIBLE) {
            searchItem.setVisibility(View.VISIBLE);
            searchUpItem.setVisibility(View.VISIBLE);
            searchDownItem.setVisibility(View.VISIBLE);
        }
        updateSearchButtons(0);
        openSearchKeyboard = text == null;
        searchItem.openSearch(openSearchKeyboard);
        if (text != null) {
            searchItem.getSearchField().setText(text);
            searchItem.getSearchField().setSelection(searchItem.getSearchField().length());
            MessagesSearchQuery.searchMessagesInChat(text, dialog_id, mergeDialogId, classGuid, 0);
        }
    }

    @Override
    public void updatePhotoAtIndex(int index) {

    }

    public boolean isSecretChat() {
        return currentEncryptedChat != null;
    }

    public TLRPC.User getCurrentUser() {
        return currentUser;
    }

    public TLRPC.Chat getCurrentChat() {
        return currentChat;
    }

    public TLRPC.EncryptedChat getCurrentEncryptedChat() {
        return currentEncryptedChat;
    }

    public TLRPC.ChatFull getCurrentChatInfo() {
        return info;
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
            if (view instanceof ChatBaseCell) {
                ChatBaseCell cell = (ChatBaseCell) view;
                MessageObject message = cell.getMessageObject();
                if (message != null && message.getId() == messageObject.getId()) {
                    messageToOpen = message;
                    imageReceiver = cell.getPhotoImage();
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                MessageObject message = cell.getMessageObject();
                if (message != null && message.getId() == messageObject.getId()) {
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
                if (pinnedMessageView != null && pinnedMessageView.getTag() == null || reportSpamView != null && reportSpamView.getTag() == null) {
                    object.clipTopAddition = AndroidUtilities.dp(48);
                }
                return object;
            }
        }
        return null;
    }

    @Override
    public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
    }

    @Override
    public void willHidePhotoViewer() {
    }

    @Override
    public boolean isPhotoChecked(int index) {
        return false;
    }

    @Override
    public void setPhotoChecked(int index) {
    }

    @Override
    public boolean cancelButtonPressed() {
        return true;
    }

    @Override
    public void sendButtonPressed(int index) {
    }

    @Override
    public int getSelectedCount() {
        return 0;
    }

    public void showOpenUrlAlert(final String url) {
        if (Browser.isInternalUrl(url)) {
            Browser.openUrl(getParentActivity(), url, inlineReturn == 0);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.formatString("OpenUrlAlert", R.string.OpenUrlAlert, url));
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Browser.openUrl(getParentActivity(), url, inlineReturn == 0);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    public class ChatActivityAdapter extends RecyclerView.Adapter {

        private Context mContext;
        private boolean isBot;
        private int rowCount;
        private int botInfoRow = -1;
        private int loadingUpRow;
        private int loadingDownRow;
        private int messagesStartRow;
        private int messagesEndRow;

        public ChatActivityAdapter(Context context) {
            mContext = context;
            isBot = currentUser != null && currentUser.bot;
        }

        public void updateRows() {
            rowCount = 0;
            if (currentUser != null && currentUser.bot) {
                botInfoRow = rowCount++;
            } else {
                botInfoRow = -1;
            }
            if (!messages.isEmpty()) {
                if (!endReached[0] || mergeDialogId != 0 && !endReached[1]) {
                    loadingUpRow = rowCount++;
                } else {
                    loadingUpRow = -1;
                }
                messagesStartRow = rowCount;
                rowCount += messages.size();
                messagesEndRow = rowCount;
                if (!forwardEndReached[0] || mergeDialogId != 0 && !forwardEndReached[1]) {
                    loadingDownRow = rowCount++;
                } else {
                    loadingDownRow = -1;
                }
            } else {
                loadingUpRow = -1;
                loadingDownRow = -1;
                messagesStartRow = -1;
                messagesEndRow = -1;
            }
        }

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public long getItemId(int i) {
            return RecyclerListView.NO_ID;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            if (viewType == 0) {
                if (!chatMessageCellsCache.isEmpty()) {
                    view = chatMessageCellsCache.get(0);
                    chatMessageCellsCache.remove(0);
                } else {
                    view = new ChatMessageCell(mContext);
                }
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setDelegate(new ChatBaseCell.ChatBaseCellDelegate() {
                    @Override
                    public void didPressedShare(ChatBaseCell cell) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        if (chatActivityEnterView != null) {
                            chatActivityEnterView.closeKeyboard();
                        }
                        showDialog(new ShareAlert(mContext, cell.getMessageObject(), ChatObject.isChannel(currentChat) && !currentChat.megagroup && currentChat.username != null && currentChat.username.length() > 0));
                    }

                    @Override
                    public boolean needPlayAudio(MessageObject messageObject) {
                        if (messageObject.isVoice()) {
                            boolean result = MediaController.getInstance().playAudio(messageObject);
                            MediaController.getInstance().setVoiceMessagesPlaylist(result ? createVoiceMessagesPlaylist(messageObject, false) : null, false);
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(messages, messageObject);
                        }
                        return false;
                    }

                    @Override
                    public void didPressedChannelAvatar(ChatBaseCell cell, TLRPC.Chat chat, int postId) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell);
                            return;
                        }
                        if (chat != null && chat != currentChat) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", chat.id);
                            if (postId != 0) {
                                args.putInt("message_id", postId);
                            }
                            if (MessagesController.checkCanOpenChat(args, ChatActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    }

                    @Override
                    public void didPressedOther(ChatBaseCell cell) {
                        createMenu(cell, true);
                    }

                    @Override
                    public void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell);
                            return;
                        }
                        if (user != null && user.id != UserConfig.getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(currentUser != null && currentUser.id == user.id);
                            presentFragment(fragment);
                        }
                    }

                    @Override
                    public void didPressedBotButton(ChatBaseCell cell, TLRPC.KeyboardButton button) {
                        if (getParentActivity() == null || bottomOverlayChat.getVisibility() == View.VISIBLE && !(button instanceof TLRPC.TL_keyboardButtonCallback) && !(button instanceof TLRPC.TL_keyboardButtonUrl)) {
                            return;
                        }
                        chatActivityEnterView.didPressedBotButton(button, cell.getMessageObject(), cell.getMessageObject());
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

                    @Override
                    public void didPressedUrl(MessageObject messageObject, final ClickableSpan url, boolean longPress) {
                        if (url == null) {
                            return;
                        }
                        if (url instanceof URLSpanNoUnderline) {
                            String str = ((URLSpanNoUnderline) url).getURL();
                            if (str.startsWith("@")) {
                                MessagesController.openByUserName(str.substring(1), ChatActivity.this, 0);
                            } else if (str.startsWith("#")) {
                                if (ChatObject.isChannel(currentChat)) {
                                    openSearchWithText(str);
                                } else {
                                    DialogsActivity fragment = new DialogsActivity(null);
                                    fragment.setSearchString(str);
                                    presentFragment(fragment);
                                }
                            } else if (str.startsWith("/")) {
                                if (URLSpanBotCommand.enabled) {
                                    chatActivityEnterView.setCommand(messageObject, str, longPress, currentChat != null && currentChat.megagroup);
                                }
                            }
                        } else {
                            final String urlFinal = ((URLSpan) url).getURL();
                            if (longPress) {
                                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                                builder.setTitle(urlFinal);
                                builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, final int which) {
                                        if (which == 0) {
                                            Browser.openUrl(getParentActivity(), urlFinal, inlineReturn == 0);
                                        } else if (which == 1) {
                                            try {
                                                if (Build.VERSION.SDK_INT < 11) {
                                                    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                    clipboard.setText(urlFinal);
                                                } else {
                                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", urlFinal);
                                                    clipboard.setPrimaryClip(clip);
                                                }
                                                Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
                                            }
                                        }
                                    }
                                });
                                showDialog(builder.create());
                            } else {
                                if (url instanceof URLSpanReplacement) {
                                    showOpenUrlAlert(((URLSpanReplacement) url).getURL());
                                } else if (url instanceof URLSpan) {
                                    Browser.openUrl(getParentActivity(), urlFinal, inlineReturn == 0);
                                } else {
                                    url.onClick(fragmentView);
                                }
                            }
                        }
                    }

                    @Override
                    public void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h) {
                        BottomSheet.Builder builder = new BottomSheet.Builder(mContext);
                        builder.setCustomView(new WebFrameLayout(mContext, builder.create(), title, description, originalUrl, url, w, h));
                        builder.setUseFullWidth(true);
                        showDialog(builder.create());
                    }

                    @Override
                    public void didPressedReplyMessage(ChatBaseCell cell, int id) {
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject.replyMessageObject != null && !messageObject.replyMessageObject.isImportant() && channelMessagesImportant == 2) {
                            channelMessagesImportant = 1;
                            avatarContainer.setRadioChecked(true, false);
                        }
                        scrollToMessageId(id, messageObject.getId(), true, messageObject.getDialogId() == mergeDialogId ? 1 : 0);
                    }

                    @Override
                    public void didPressedViaBot(ChatBaseCell cell, String username) {
                        if (bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE || bottomOverlay != null && bottomOverlay.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        if (chatActivityEnterView != null && username != null && username.length() > 0) {
                            chatActivityEnterView.setFieldText("@" + username + " ");
                            chatActivityEnterView.openKeyboard();
                        }
                    }

                    @Override
                    public void didPressedImage(ChatBaseCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.isSendError()) {
                            createMenu(cell, false);
                            return;
                        } else if (message.isSending()) {
                            return;
                        }
                        if (message.type == 13) {
                            showDialog(new StickersAlert(getParentActivity(), message.getInputStickerSet(), null, bottomOverlayChat.getVisibility() != View.VISIBLE ? chatActivityEnterView : null));
                        } else if (message.type == 1 || message.type == 0 && !message.isWebpageDocument()) {
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            PhotoViewer.getInstance().openPhoto(message, message.type != 0 ? dialog_id : 0, message.type != 0 ? mergeDialogId : 0, ChatActivity.this);
                        } else if (message.type == 3) {
                            sendSecretMessageRead(message);
                            try {
                                File f = null;
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    f = new File(message.messageOwner.attachPath);
                                }
                                if (f == null || !f.exists()) {
                                    f = FileLoader.getPathToMessage(message.messageOwner);
                                }
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                alertUserOpenError(message);
                            }
                        } else if (message.type == 4) {
                            if (!AndroidUtilities.isGoogleMapsInstalled(ChatActivity.this)) {
                                return;
                            }
                            LocationActivity fragment = new LocationActivity();
                            fragment.setMessageObject(message);
                            presentFragment(fragment);
                        } else if (message.type == 9 || message.type == 0) {
                            File f = null;
                            String fileName = message.getFileName();
                            if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                f = new File(message.messageOwner.attachPath);
                            }
                            if (f == null || !f.exists()) {
                                f = FileLoader.getPathToMessage(message.messageOwner);
                            }
                            if (f != null && f.exists()) {
                                String realMimeType = null;
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    MimeTypeMap myMime = MimeTypeMap.getSingleton();
                                    int idx = fileName.lastIndexOf('.');
                                    if (idx != -1) {
                                        String ext = fileName.substring(idx + 1);
                                        realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                        if (realMimeType == null) {
                                            if (message.type == 9 || message.type == 0) {
                                                realMimeType = message.getDocument().mime_type;
                                            }
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
                                    if (realMimeType != null) {
                                        try {
                                            getParentActivity().startActivityForResult(intent, 500);
                                        } catch (Exception e) {
                                            intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                            getParentActivity().startActivityForResult(intent, 500);
                                        }
                                    } else {
                                        getParentActivity().startActivityForResult(intent, 500);
                                    }
                                } catch (Exception e) {
                                    alertUserOpenError(message);
                                }
                            }
                        }
                    }
                });
                chatMessageCell.setAllowedToSetPhoto(openAnimationEnded);
                if (currentEncryptedChat == null) {
                    chatMessageCell.setAllowAssistant(true);
                }
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickedImage(ChatActionCell cell) {
                        MessageObject message = cell.getMessageObject();
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        PhotoViewer.getInstance().openPhoto(message, 0, 0, ChatActivity.this);
                    }

                    @Override
                    public void didLongPressed(ChatActionCell cell) {
                        createMenu(cell, false);
                    }

                    @Override
                    public void needOpenUserProfile(int uid) {
                        if (uid < 0) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", -uid);
                            if (MessagesController.checkCanOpenChat(args, ChatActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        } else if (uid != UserConfig.getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", uid);
                            if (currentEncryptedChat != null && uid == currentUser.id) {
                                args.putLong("dialog_id", dialog_id);
                            }
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(currentUser != null && currentUser.id == uid);
                            presentFragment(fragment);
                        }
                    }
                });
            } else if (viewType == 2) {
                view = new ChatUnreadCell(mContext);
            } else if (viewType == 3) {
                view = new BotHelpCell(mContext);
                ((BotHelpCell) view).setDelegate(new BotHelpCell.BotHelpCellDelegate() {
                    @Override
                    public void didPressUrl(String url) {
                        if (url.startsWith("@")) {
                            MessagesController.openByUserName(url.substring(1), ChatActivity.this, 0);
                        } else if (url.startsWith("#")) {
                            DialogsActivity fragment = new DialogsActivity(null);
                            fragment.setSearchString(url);
                            presentFragment(fragment);
                        } else if (url.startsWith("/")) {
                            chatActivityEnterView.setCommand(null, url, false, false);
                        }
                    }
                });
            } else if (viewType == 4) {
                view = new ChatLoadingCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position == botInfoRow) {
                BotHelpCell helpView = (BotHelpCell) holder.itemView;
                helpView.setText(!botInfo.isEmpty() ? botInfo.get(currentUser.id).description : null);
            } else if (position == loadingDownRow || position == loadingUpRow) {
                ChatLoadingCell loadingCell = (ChatLoadingCell) holder.itemView;
                loadingCell.setProgressVisible(loadsCount > 1);
            } else if (position >= messagesStartRow && position < messagesEndRow) {
                MessageObject message = messages.get(messages.size() - (position - messagesStartRow) - 1);
                View view = holder.itemView;

                boolean selected = false;
                boolean disableSelection = false;
                if (actionBar.isActionModeShowed()) {
                    if (selectedMessagesIds[message.getDialogId() == dialog_id ? 0 : 1].containsKey(message.getId())) {
                        view.setBackgroundColor(0x6633b5e5);
                        selected = true;
                    } else {
                        view.setBackgroundColor(0);
                    }
                    disableSelection = true;
                } else {
                    view.setBackgroundColor(0);
                }

                if (view instanceof ChatMessageCell) {
                    ChatMessageCell messageCell = (ChatMessageCell) view;
                    messageCell.isChat = currentChat != null;
                    messageCell.setMessageObject(message);
                    messageCell.setCheckPressed(!disableSelection, disableSelection && selected);
                    if (view instanceof ChatMessageCell && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_AUDIO)) {
                        ((ChatMessageCell) view).downloadAudioIfNeed();
                    }
                    messageCell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && message.getId() == highlightMessageId);
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell actionCell = (ChatActionCell) view;
                    actionCell.setMessageObject(message);
                } else if (view instanceof ChatUnreadCell) {
                    ChatUnreadCell unreadCell = (ChatUnreadCell) view;
                    unreadCell.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= messagesStartRow && position < messagesEndRow) {
                return messages.get(messages.size() - (position - messagesStartRow) - 1).contentType;
            } else if (position == botInfoRow) {
                return 3;
            }
            return 4;
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ChatMessageCell) {
                final ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
                messageCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        messageCell.getViewTreeObserver().removeOnPreDrawListener(this);
                        messageCell.getLocalVisibleRect(scrollRect);
                        messageCell.setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
                        return true;
                    }
                });
                messageCell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && messageCell.getMessageObject().getId() == highlightMessageId);
            }
        }

        public void updateRowWithMessageObject(MessageObject messageObject) {
            int index = messages.indexOf(messageObject);
            if (index == -1) {
                return;
            }
            notifyItemChanged(messagesStartRow + messages.size() - index - 1);
        }

        public void removeMessageObject(MessageObject messageObject) {
            int index = messages.indexOf(messageObject);
            if (index == -1) {
                return;
            }
            messages.remove(index);
            notifyItemRemoved(messagesStartRow + messages.size() - index - 1);
        }

        @Override
        public void notifyDataSetChanged() {
            updateRows();
            try {
                super.notifyDataSetChanged();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemChanged(int position) {
            updateRows();
            try {
                super.notifyItemChanged(position);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeChanged(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemInserted(int position) {
            updateRows();
            try {
                super.notifyItemInserted(position);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemMoved(int fromPosition, int toPosition) {
            updateRows();
            try {
                super.notifyItemMoved(fromPosition, toPosition);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeInserted(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemRemoved(int position) {
            updateRows();
            try {
                super.notifyItemRemoved(position);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeRemoved(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }
}
