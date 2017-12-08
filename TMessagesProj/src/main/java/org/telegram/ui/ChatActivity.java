/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiSuggestion;
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
import org.telegram.messenger.exoplayer2.ui.AspectRatioFrameLayout;
import org.telegram.messenger.query.BotQuery;
import org.telegram.messenger.query.DraftQuery;
import org.telegram.messenger.query.MessagesSearchQuery;
import org.telegram.messenger.query.MessagesQuery;
import org.telegram.messenger.query.SearchQuery;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.support.widget.GridLayoutManager;
import org.telegram.messenger.support.widget.GridLayoutManagerFixed;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.LinearSmoothScrollerMiddle;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Adapters.StickersAdapter;
import org.telegram.ui.Cells.BotSwitchCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatLoadingCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatUnreadCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.MentionCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.BotHelpCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ChatBigEmptyView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CorrectlyMeasuringTextView;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;

@SuppressWarnings("unchecked")
public class ChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate, LocationActivity.LocationActivityDelegate {

    protected TLRPC.Chat currentChat;
    protected TLRPC.User currentUser;
    protected TLRPC.EncryptedChat currentEncryptedChat;
    private boolean userBlocked = false;

    private ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();

    private Dialog closeChatDialog;
    private FrameLayout progressView;
    private View progressView2;
    private FrameLayout bottomOverlay;
    protected ChatActivityEnterView chatActivityEnterView;
    private View timeItem2;
    private ActionBarMenuItem attachItem;
    private ActionBarMenuItem headerItem;
    private ActionBarMenuItem searchItem;
    private RadialProgressView progressBar;
    private TextView addContactItem;
    private RecyclerListView chatListView;
    private GridLayoutManagerFixed chatLayoutManager;
    private ChatActivityAdapter chatAdapter;
    private TextView bottomOverlayChatText;
    private FrameLayout bottomOverlayChat;
    private FrameLayout emptyViewContainer;
    private SizeNotifierFrameLayout contentView;
    private ChatBigEmptyView bigEmptyView;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ChatAvatarContainer avatarContainer;
    private TextView bottomOverlayText;
    private NumberTextView selectedMessagesCountTextView;
    private FrameLayout actionModeTitleContainer;
    private SimpleTextView actionModeTextView;
    private SimpleTextView actionModeSubTextView;
    private RecyclerListView stickersListView;
    private ImageView stickersPanelArrow;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private RecyclerListView.OnItemClickListener mentionsOnItemClickListener;
    private StickersAdapter stickersAdapter;
    private FrameLayout stickersPanel;
    private TextView muteItem;
    private FrameLayout pagedownButton;
    private ImageView pagedownButtonImage;
    private boolean pagedownButtonShowedByScroll;
    private TextView pagedownButtonCounter;
    private FrameLayout mentiondownButton;
    private TextView mentiondownButtonCounter;
    private ImageView mentiondownButtonImage;
    private BackupImageView replyImageView;
    private SimpleTextView replyNameTextView;
    private SimpleTextView replyObjectTextView;
    private ImageView replyIconImageView;
    private ImageView replyCloseImageView;
    private MentionsAdapter mentionsAdapter;
    private FrameLayout mentionContainer;
    private RecyclerListView mentionListView;
    private LinearLayoutManager mentionLayoutManager;
    private ExtendedGridLayoutManager mentionGridLayoutManager;
    private AnimatorSet mentionListAnimation;
    private ChatAttachAlert chatAttachAlert;
    private LinearLayout reportSpamView;
    private AnimatorSet reportSpamViewAnimator;
    private TextView addToContactsButton;
    private TextView reportSpamButton;
    private FrameLayout reportSpamContainer;
    private ImageView closeReportSpam;
    private FragmentContextView fragmentContextView;
    private FragmentContextView fragmentLocationContextView;
    private View replyLineView;
    private TextView emptyView;
    private TextView gifHintTextView;
    private TextView mediaBanTooltip;
    private TextView voiceHintTextView;
    private Runnable voiceHintHideRunnable;
    private AnimatorSet voiceHintAnimation;
    private View emojiButtonRed;
    private FrameLayout pinnedMessageView;
    private View pinnedLineView;
    private AnimatorSet pinnedMessageViewAnimator;
    private BackupImageView pinnedMessageImageView;
    private SimpleTextView pinnedMessageNameTextView;
    private ImageView closePinned;
    private SimpleTextView pinnedMessageTextView;
    private FrameLayout alertView;
    private Runnable hideAlertViewRunnable;
    private TextView alertNameTextView;
    private TextView alertTextView;
    private AnimatorSet alertViewAnimator;
    private FrameLayout searchContainer;
    private ImageView searchCalendarButton;
    private ImageView searchUserButton;
    private ImageView searchUpButton;
    private ImageView searchDownButton;
    private SimpleTextView searchCountText;
    private ChatActionCell floatingDateView;
    private InstantCameraView instantCameraView;
    private View overlayView;
    private boolean currentFloatingDateOnScreen;
    private boolean currentFloatingTopIsNotMessage;
    private AnimatorSet floatingDateAnimation;
    private boolean scrollingFloatingDate;
    private boolean checkTextureViewPosition;
    private boolean searchingForUser;
    private TLRPC.User searchingUserMessages;

    private ArrayList<MessageObject> animatingMessageObjects = new ArrayList<>();

    private int scrollToPositionOnRecreate = -1;
    private int scrollToOffsetOnRecreate = 0;
    private boolean chatListViewIgnoreLayout;

    private int topViewWasVisible;

    private boolean mentionListViewIgnoreLayout;
    private int mentionListViewScrollOffsetY;
    private int mentionListViewLastViewTop;
    private int mentionListViewLastViewPosition;
    private boolean mentionListViewIsScrolling;

    private MessageObject pinnedMessageObject;
    private int loadingPinnedMessage;

    private AnimatorSet pagedownButtonAnimation;
    private ObjectAnimator mentiondownButtonAnimation;
    private AnimatorSet replyButtonAnimation;

    private boolean openSearchKeyboard;

    private boolean waitingForReplyMessageLoad;

    private boolean ignoreAttachOnPause;

    private boolean allowStickersPanel;
    private boolean allowContextBotPanel;
    private boolean allowContextBotPanelSecond = true;
    private AnimatorSet runningAnimation;

    private MessageObject selectedObject;
    private MessageObject.GroupedMessages selectedObjectGroup;
    private ArrayList<MessageObject> forwardingMessages;
    private MessageObject forwardingMessage;
    private MessageObject.GroupedMessages forwardingMessageGroup;
    private MessageObject replyingMessageObject;
    private int editingMessageObjectReqId;
    private boolean paused = true;
    private boolean pausedOnLastMessage;
    private boolean wasPaused;
    private boolean readWhenResume;
    private TLRPC.FileLocation replyImageLocation;
    private TLRPC.FileLocation pinnedImageLocation;
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
    private boolean forceScrollToTop;
    private boolean scrollToTopUnReadOnResume;
    private long dialog_id;
    private int lastLoadIndex;
    private boolean isBroadcast;
    private HashMap<Integer, MessageObject>[] selectedMessagesIds = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private HashMap<Integer, MessageObject>[] selectedMessagesCanCopyIds = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private HashMap<Integer, MessageObject>[] selectedMessagesCanStarIds = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private boolean hasUnfavedSelected;
    private int cantDeleteMessagesCount;
    private int canEditMessagesCount;
    private ArrayList<Integer> waitingForLoad = new ArrayList<>();

    private int newUnreadMessageCount;
    private int newMentionsCount;
    private boolean hasAllMentionsLocal;

    private boolean startReplyOnTextChange;

    private HashMap<Integer, MessageObject>[] messagesDict = new HashMap[]{new HashMap<>(), new HashMap<>()};
    private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<>();
    protected ArrayList<MessageObject> messages = new ArrayList<>();
    private HashMap<Long, MessageObject.GroupedMessages> groupedMessagesMap = new HashMap<>();
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
    private int startLoadFromMessageOffset = Integer.MAX_VALUE;
    private boolean needSelectFromMessageId;
    private int returnToMessageId;
    private int returnToLoadIndex;
    private int createUnreadMessageAfterId;
    private boolean createUnreadMessageAfterIdLoading;
    private boolean loadingFromOldPosition;

    private boolean first = true;
    private int unread_to_load;
    private int first_unread_id;
    private boolean loadingForward;
    private MessageObject unreadMessageObject;
    private MessageObject scrollToMessage;
    private int highlightMessageId = Integer.MAX_VALUE;
    private int scrollToMessagePosition = -10000;

    private String currentPicturePath;

    protected TLRPC.ChatFull info;

    private HashMap<Integer, TLRPC.BotInfo> botInfo = new HashMap<>();
    private String botUser;
    private long inlineReturn;
    private MessageObject botButtons;
    private MessageObject botReplyButtons;
    private int botsCount;
    private boolean hasBotsCommands;
    private long chatEnterTime;
    private long chatLeaveTime;

    private String startVideoEdit;

    private FrameLayout roundVideoContainer;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;
    private Path aspectPath;
    private Paint aspectPaint;

    private PhotoViewer.PhotoViewerProvider photoViewerProvider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            int count = chatListView.getChildCount();

            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = null;
                View view = chatListView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    if (messageObject != null) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject message = cell.getMessageObject();
                        if (message != null && message.getId() == messageObject.getId()) {
                            imageReceiver = cell.getPhotoImage();
                        }
                    }
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell cell = (ChatActionCell) view;
                    MessageObject message = cell.getMessageObject();
                    if (message != null) {
                        if (messageObject != null) {
                            if (message.getId() == messageObject.getId()) {
                                imageReceiver = cell.getPhotoImage();
                            }
                        } else if (fileLocation != null && message.photoThumbs != null) {
                            for (int b = 0; b < message.photoThumbs.size(); b++) {
                                TLRPC.PhotoSize photoSize = message.photoThumbs.get(b);
                                if (photoSize.location.volume_id == fileLocation.volume_id && photoSize.location.local_id == fileLocation.local_id) {
                                    imageReceiver = cell.getPhotoImage();
                                    break;
                                }
                            }
                        }
                    }
                }

                if (imageReceiver != null) {
                    int coords[] = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = chatListView;
                    object.imageReceiver = imageReceiver;
                    object.thumb = imageReceiver.getBitmap();
                    object.radius = imageReceiver.getRoundRadius();
                    if (view instanceof ChatActionCell && currentChat != null) {
                        object.dialogId = -currentChat.id;
                    }
                    if (pinnedMessageView != null && pinnedMessageView.getTag() == null || reportSpamView != null && reportSpamView.getTag() == null) {
                        object.clipTopAddition = AndroidUtilities.dp(48);
                    }
                    return object;
                }
            }
            return null;
        }
    };

    private Runnable readRunnable = new Runnable() {
        @Override
        public void run() {
            if (readWhenResume && !messages.isEmpty()) {
                for (int a = 0; a < messages.size(); a++) {
                    MessageObject messageObject = messages.get(a);
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
        }
    };

    private ArrayList<Object> botContextResults;
    private PhotoViewer.PhotoViewerProvider botContextProvider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            if (index < 0 || index >= botContextResults.size()) {
                return null;
            }
            int count = mentionListView.getChildCount();
            Object result = botContextResults.get(index);

            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = null;
                View view = mentionListView.getChildAt(a);
                if (view instanceof ContextLinkCell) {
                    ContextLinkCell cell = (ContextLinkCell) view;
                    if (cell.getResult() == result) {
                        imageReceiver = cell.getPhotoImage();
                    }
                }

                if (imageReceiver != null) {
                    int coords[] = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = mentionListView;
                    object.imageReceiver = imageReceiver;
                    object.thumb = imageReceiver.getBitmap();
                    object.radius = imageReceiver.getRoundRadius();
                    return object;
                }
            }
            return null;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
            if (index < 0 || index >= botContextResults.size()) {
                return;
            }
            sendBotInlineResult((TLRPC.BotInlineResult) botContextResults.get(index));
        }
    };

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
    private final static int report = 21;
    private final static int star = 22;
    private final static int edit = 23;
    private final static int add_shortcut = 24;

    private final static int bot_help = 30;
    private final static int bot_settings = 31;
    private final static int call = 32;

    private final static int attach_photo = 0;
    private final static int attach_gallery = 1;
    private final static int attach_video = 2;
    private final static int attach_audio = 3;
    private final static int attach_document = 4;
    private final static int attach_contact = 5;
    private final static int attach_location = 6;

    private final static int search = 40;

    private final static int id_chat_compose_panel = 1000;

    RecyclerListView.OnItemLongClickListener onItemLongClickListener = new RecyclerListView.OnItemLongClickListener() {
        @Override
        public boolean onItemClick(View view, int position) {
            if (!actionBar.isActionModeShowed()) {
                createMenu(view, false, true);
                return true;
            }
            return false;
        }
    };

    RecyclerListView.OnItemClickListenerExtended onItemClickListener = new RecyclerListView.OnItemClickListenerExtended() {
        @Override
        public void onItemClick(View view, int position, float x, float y) {
            if (actionBar.isActionModeShowed()) {
                boolean outside = false;
                if (view instanceof ChatMessageCell) {
                    outside = !((ChatMessageCell) view).isInsideBackground(x, y);
                }
                processRowSelect(view, outside);
                return;
            }
            createMenu(view, true, false);
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
                    FileLog.e(e);
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
                    FileLog.e(e);
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
                    FileLog.e(e);
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
                    FileLog.e(e);
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
        } else {
            return false;
        }

        if (currentUser != null) {
            MediaController.getInstance().startMediaObserver();
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.historyCleared);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByAck);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageSendError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesReadEncrypted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.screenshotTook);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidStarted);
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
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatSearchResultsLoading);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didUpdatedMessagesViews);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoCantLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didLoadedPinnedMessage);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.peerSettingsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.newDraftReceived);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.userInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.channelRightsUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateMentionsCount);

        super.onFragmentCreate();

        if (currentEncryptedChat == null && !isBroadcast) {
            BotQuery.loadBotKeyboard(dialog_id);
        }

        loading = true;
        MessagesController.getInstance().loadPeerSettings(currentUser, currentChat);
        MessagesController.getInstance().setLastCreatedDialogId(dialog_id, true);

        if (startLoadFromMessageId == 0) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            int messageId = sharedPreferences.getInt("diditem" + dialog_id, 0);
            if (messageId != 0) {
                loadingFromOldPosition = true;
                startLoadFromMessageOffset = sharedPreferences.getInt("diditemo" + dialog_id, 0);
                startLoadFromMessageId = messageId;
            }
        } else {
            needSelectFromMessageId = true;
        }

        if (startLoadFromMessageId != 0) {
            waitingForLoad.add(lastLoadIndex);
            if (migrated_to != 0) {
                mergeDialogId = migrated_to;
                MessagesController.getInstance().loadMessages(mergeDialogId, loadingFromOldPosition ? 50 : (AndroidUtilities.isTablet() ? 30 : 20), startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
            } else {
                MessagesController.getInstance().loadMessages(dialog_id, loadingFromOldPosition ? 50 : (AndroidUtilities.isTablet() ? 30 : 20), startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
            }
        } else {
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, 0, true, 0, classGuid, 2, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
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
                    FileLog.e(e);
                }
            }
        }

        if (userId != 0 && currentUser.bot) {
            BotQuery.loadBotInfo(userId, true, classGuid);
        } else if (info instanceof TLRPC.TL_chatFull) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                if (user != null && user.bot) {
                    BotQuery.loadBotInfo(user.id, true, classGuid);
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
        if (chatAttachAlert != null) {
            chatAttachAlert.dismissInternal();
        }
        MessagesController.getInstance().setLastCreatedDialogId(dialog_id, false);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesRead);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.historyCleared);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageReceivedByAck);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageSendError);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesReadEncrypted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.screenshotTook);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileNewChunkAvailable);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidStarted);
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatSearchResultsLoading);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didUpdatedMessagesViews);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoCantLoad);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didLoadedPinnedMessage);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.peerSettingsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.newDraftReceived);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.userInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.channelRightsUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateMentionsCount);

        if (AndroidUtilities.isTablet()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, true);
        }
        if (currentUser != null) {
            MediaController.getInstance().stopMediaObserver();
        }
        if (currentEncryptedChat != null) {
            try {
                if (Build.VERSION.SDK_INT >= 23 && (UserConfig.passcodeHash.length() == 0 || UserConfig.allowScreenCapture)) {
                    getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (currentUser != null) {
            MessagesController.getInstance().cancelLoadFullUser(currentUser.id);
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (stickersAdapter != null) {
            stickersAdapter.onDestroy();
        }
        if (chatAttachAlert != null) {
            chatAttachAlert.onDestroy();
        }
        AndroidUtilities.unlockOrientation(getParentActivity());
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
            selectedMessagesCanStarIds[a].clear();
        }
        cantDeleteMessagesCount = 0;
        canEditMessagesCount = 0;

        hasOwnBackground = true;
        if (chatAttachAlert != null) {
            try {
                if (chatAttachAlert.isShowing()) {
                    chatAttachAlert.dismiss();
                }
            } catch (Exception ignore) {

            }
            chatAttachAlert.onDestroy();
            chatAttachAlert = null;
        }

        Theme.createChatResources(context, false);

        actionBar.setAddToContainer(false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        for (int a = 1; a >= 0; a--) {
                            selectedMessagesIds[a].clear();
                            selectedMessagesCanCopyIds[a].clear();
                            selectedMessagesCanStarIds[a].clear();
                        }
                        cantDeleteMessagesCount = 0;
                        canEditMessagesCount = 0;
                        if (chatActivityEnterView.isEditingMessage()) {
                            chatActivityEnterView.setEditingMessageObject(null, false);
                        } else {
                            actionBar.hideActionMode();
                            updatePinnedMessageView(true);
                        }
                        updateVisibleRows();
                    } else {
                        finishFragment();
                    }
                } else if (id == copy) {
                    String str = "";
                    int previousUid = 0;
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
                                str += "\n\n";
                            }
                            str += getMessageContent(messageObject, previousUid, true);
                            previousUid = messageObject.messageOwner.from_id;
                        }
                    }
                    if (str.length() != 0) {
                        AndroidUtilities.addToClipboard(str);
                    }
                    for (int a = 1; a >= 0; a--) {
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                        selectedMessagesCanStarIds[a].clear();
                    }
                    cantDeleteMessagesCount = 0;
                    canEditMessagesCount = 0;
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == delete) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    createDeleteMessagesAlert(null, null);
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate(ChatActivity.this);
                    presentFragment(fragment);
                } else if (id == chat_enc_timer) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    showDialog(AlertsCreator.createTTLAlert(getParentActivity(), currentEncryptedChat).create());
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
                                if (ChatObject.isChannel(currentChat) && info != null && info.pinned_msg_id != 0) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    preferences.edit().putInt("pin_" + dialog_id, info.pinned_msg_id).commit();
                                    updatePinnedMessageView(true);
                                }
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
                } else if (id == add_shortcut) {
                    try {
                        AndroidUtilities.installShortcut(currentUser.id);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
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
                        selectedMessagesCanStarIds[a].clear();
                    }
                    if (messageObject != null && (messageObject.messageOwner.id > 0 || messageObject.messageOwner.id < 0 && currentEncryptedChat != null)) {
                        showReplyPanel(true, messageObject, null, null, false);
                    }
                    cantDeleteMessagesCount = 0;
                    canEditMessagesCount = 0;
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == star) {
                    for (int a = 0; a < 2; a++) {
                        for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesCanStarIds[a].entrySet()) {
                            MessageObject msg = entry.getValue();
                            StickersQuery.addRecentSticker(StickersQuery.TYPE_FAVE, msg.getDocument(), (int) (System.currentTimeMillis() / 1000), !hasUnfavedSelected);
                        }
                    }
                    for (int a = 1; a >= 0; a--) {
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                        selectedMessagesCanStarIds[a].clear();
                    }
                    cantDeleteMessagesCount = 0;
                    canEditMessagesCount = 0;
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == edit) {
                    MessageObject messageObject = null;
                    for (int a = 1; a >= 0; a--) {
                        if (messageObject == null && selectedMessagesIds[a].size() == 1) {
                            ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds[a].keySet());
                            messageObject = messagesDict[a].get(ids.get(0));
                        }
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                        selectedMessagesCanStarIds[a].clear();
                    }
                    startReplyOnTextChange = false;
                    startEditingMessageObject(messageObject);
                    cantDeleteMessagesCount = 0;
                    canEditMessagesCount = 0;
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == chat_menu_attach) {
                    openAttachMenu();
                } else if (id == bot_help) {
                    SendMessagesHelper.getInstance().sendMessage("/help", dialog_id, null, null, false, null, null, null);
                } else if (id == bot_settings) {
                    SendMessagesHelper.getInstance().sendMessage("/settings", dialog_id, null, null, false, null, null, null);
                } else if (id == search) {
                    openSearchWithText(null);
                } else if(id == call) {
                    if (currentUser != null && getParentActivity() != null) {
                        VoIPHelper.startCall(currentUser, getParentActivity(), MessagesController.getInstance().getUserFull(currentUser.id));
                    }
                }
            }
        });

        avatarContainer = new ChatAvatarContainer(context, this, currentEncryptedChat != null);
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

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
            searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

                boolean searchWas;

                @Override
                public void onSearchCollapse() {
                    searchCalendarButton.setVisibility(View.VISIBLE);
                    if (searchUserButton != null) {
                        searchUserButton.setVisibility(View.VISIBLE);
                    }
                    if (searchingForUser) {
                        mentionsAdapter.searchUsernameOrHashtag(null, 0, null, false);
                        searchingForUser = false;
                    }
                    mentionLayoutManager.setReverseLayout(false);
                    mentionsAdapter.setSearchingMentions(false);
                    searchingUserMessages = null;
                    searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
                    searchItem.setSearchFieldCaption(null);
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
                    highlightMessageId = Integer.MAX_VALUE;
                    updateVisibleRows();
                    if (searchWas) {
                        scrollToLastMessage(false);
                    }
                    updateBottomOverlay();
                    updatePinnedMessageView(true);
                }

                @Override
                public void onSearchExpand() {
                    if (!openSearchKeyboard) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            searchWas = false;
                            searchItem.getSearchField().requestFocus();
                            AndroidUtilities.showKeyboard(searchItem.getSearchField());
                        }
                    }, 300);
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    searchWas = true;
                    updateSearchButtons(0, 0, -1);
                    MessagesSearchQuery.searchMessagesInChat(editText.getText().toString(), dialog_id, mergeDialogId, classGuid, 0, searchingUserMessages);
                }

                @Override
                public void onTextChanged(EditText editText) {
                    if (searchingForUser) {
                        mentionsAdapter.searchUsernameOrHashtag("@" + editText.getText().toString(), 0, messages, true);
                    } else if (!searchingForUser && searchingUserMessages == null && searchUserButton != null && TextUtils.equals(editText.getText(), LocaleController.getString("SearchFrom", R.string.SearchFrom))) {
                        searchUserButton.callOnClick();
                    }
                }

                @Override
                public void onCaptionCleared() {
                    if (searchingUserMessages != null) {
                        searchUserButton.callOnClick();
                    } else {
                        if (searchingForUser) {
                            mentionsAdapter.searchUsernameOrHashtag(null, 0, null, false);
                            searchingForUser = false;
                        }
                        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
                        searchCalendarButton.setVisibility(View.VISIBLE);
                        searchUserButton.setVisibility(View.VISIBLE);
                        searchingUserMessages = null;
                    }
                }
            });
            searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
            searchItem.setVisibility(View.GONE);
        }

        headerItem = menu.addItem(0, R.drawable.ic_ab_other);
        if (currentUser != null) {
            headerItem.addSubItem(call, LocaleController.getString("Call", R.string.Call));
            TLRPC.TL_userFull userFull = MessagesController.getInstance().getUserFull(currentUser.id);
            if (userFull != null && userFull.phone_calls_available) {
                headerItem.showSubItem(call);
            } else {
                headerItem.hideSubItem(call);
            }
        }
        if (searchItem != null) {
            headerItem.addSubItem(search, LocaleController.getString("Search", R.string.Search));
        }
        if (ChatObject.isChannel(currentChat) && !currentChat.creator && (!currentChat.megagroup || currentChat.username != null && currentChat.username.length() > 0)) {
            headerItem.addSubItem(report, LocaleController.getString("ReportChat", R.string.ReportChat));
        }
        if (currentUser != null) {
            addContactItem = headerItem.addSubItem(share_contact, "");
        }
        if (currentEncryptedChat != null) {
            timeItem2 = headerItem.addSubItem(chat_enc_timer, LocaleController.getString("SetTimer", R.string.SetTimer));
        }
        if (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup && TextUtils.isEmpty(currentChat.username)) {
            headerItem.addSubItem(clear_history, LocaleController.getString("ClearHistory", R.string.ClearHistory));
        }
        if (!ChatObject.isChannel(currentChat)) {
            if (currentChat != null && !isBroadcast) {
                headerItem.addSubItem(delete_chat, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
            } else {
                headerItem.addSubItem(delete_chat, LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser));
            }
        }
        if (currentUser == null || !currentUser.self) {
            muteItem = headerItem.addSubItem(mute, null);
        } else if (currentUser.self) {
            headerItem.addSubItem(add_shortcut, LocaleController.getString("AddShortcut", R.string.AddShortcut));
        }
        if (currentUser != null && currentEncryptedChat == null && currentUser.bot) {
            headerItem.addSubItem(bot_settings, LocaleController.getString("BotSettings", R.string.BotSettings));
            headerItem.addSubItem(bot_help, LocaleController.getString("BotHelp", R.string.BotHelp));
            updateBotButtons();
        }

        updateTitle();
        avatarContainer.updateOnlineCount();
        avatarContainer.updateSubtitle();
        updateTitleIcons();

        attachItem = menu.addItem(chat_menu_attach, R.drawable.ic_ab_other).setOverrideMenuClick(true).setAllowCloseAnimation(false);
        attachItem.setVisibility(View.GONE);

        actionModeViews.clear();

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));
        selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        actionModeTitleContainer = new FrameLayout(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(width, height);

                actionModeTextView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20);
                actionModeTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));

                if (actionModeSubTextView.getVisibility() != GONE) {
                    actionModeSubTextView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 14 : 16);
                    actionModeSubTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int height = bottom - top;

                int textTop;
                if (actionModeSubTextView.getVisibility() != GONE) {
                    textTop = (height / 2 - actionModeTextView.getTextHeight()) / 2 + AndroidUtilities.dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 3);
                } else {
                    textTop = (height - actionModeTextView.getTextHeight()) / 2;
                }
                actionModeTextView.layout(0, textTop, actionModeTextView.getMeasuredWidth(), textTop + actionModeTextView.getTextHeight());

                if (actionModeSubTextView.getVisibility() != GONE) {
                    textTop = height / 2 + (height / 2 - actionModeSubTextView.getTextHeight()) / 2 - AndroidUtilities.dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 1 : 1);
                    actionModeSubTextView.layout(0, textTop, actionModeSubTextView.getMeasuredWidth(), textTop + actionModeSubTextView.getTextHeight());
                }
            }
        };
        actionMode.addView(actionModeTitleContainer, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));
        actionModeTitleContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        actionModeTitleContainer.setVisibility(View.GONE);

        actionModeTextView = new SimpleTextView(context);
        actionModeTextView.setTextSize(18);
        actionModeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        actionModeTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionModeTextView.setText(LocaleController.getString("Edit", R.string.Edit));
        actionModeTitleContainer.addView(actionModeTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionModeSubTextView = new SimpleTextView(context);
        actionModeSubTextView.setGravity(Gravity.LEFT);
        actionModeSubTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionModeTitleContainer.addView(actionModeSubTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (currentEncryptedChat == null) {
            actionModeViews.add(actionMode.addItemWithWidth(edit, R.drawable.group_edit, AndroidUtilities.dp(54)));
            if (!isBroadcast) {
                actionModeViews.add(actionMode.addItemWithWidth(reply, R.drawable.ic_ab_reply, AndroidUtilities.dp(54)));
            }
            actionModeViews.add(actionMode.addItemWithWidth(star, R.drawable.ic_ab_fave, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(copy, R.drawable.ic_ab_copy, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(forward, R.drawable.ic_ab_forward, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.ic_ab_delete, AndroidUtilities.dp(54)));
        } else {
            actionModeViews.add(actionMode.addItemWithWidth(edit, R.drawable.group_edit, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(reply, R.drawable.ic_ab_reply, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(star, R.drawable.ic_ab_fave, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(copy, R.drawable.ic_ab_copy, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.ic_ab_delete, AndroidUtilities.dp(54)));
        }
        actionMode.getItem(edit).setVisibility(canEditMessagesCount == 1 && selectedMessagesIds[0].size() + selectedMessagesIds[1].size() == 1 ? View.VISIBLE : View.GONE);
        actionMode.getItem(copy).setVisibility(selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);
        actionMode.getItem(star).setVisibility(selectedMessagesCanStarIds[0].size() + selectedMessagesCanStarIds[1].size() != 0 ? View.VISIBLE : View.GONE);
        actionMode.getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
        checkActionBarMenu();

        fragmentView = new SizeNotifierFrameLayout(context) {

            int inputFieldHeight = 0;

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isRoundVideo() && messageObject.eventId == 0 && messageObject.getDialogId() == dialog_id) {
                    MediaController.getInstance().setTextureView(createTextureView(false), aspectRatioFrameLayout, roundVideoContainer, true);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result;
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                boolean isRoundVideo = messageObject != null && messageObject.eventId == 0 && messageObject.isRoundVideo();
                if (isRoundVideo && child == roundVideoContainer) {
                    if (messageObject.type == 5) {
                        if (Theme.chat_roundVideoShadow != null && aspectRatioFrameLayout.isDrawingReady()) {
                            int x = (int) child.getX() - AndroidUtilities.dp(3);
                            int y = (int) child.getY() - AndroidUtilities.dp(2);
                            Theme.chat_roundVideoShadow.setAlpha(255);
                            Theme.chat_roundVideoShadow.setBounds(x, y, x + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6), y + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6));
                            Theme.chat_roundVideoShadow.draw(canvas);
                        }
                        result = super.drawChild(canvas, child, drawingTime);
                    } else {
                        result = false;
                    }
                } else {
                    result = super.drawChild(canvas, child, drawingTime);
                    if (isRoundVideo && child == chatListView && messageObject.type != 5 && roundVideoContainer != null) {
                        super.drawChild(canvas, roundVideoContainer, drawingTime);
                    }
                }
                if (child == actionBar && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() : 0);
                }
                return result;
            }

            @Override
            protected boolean isActionBarVisible() {
                return actionBar.getVisibility() == VISIBLE;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int allHeight;
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = allHeight = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                if (actionBar.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }

                int keyboardSize = getKeyboardHeight();

                if (keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow) {
                    heightSize -= chatActivityEnterView.getEmojiPadding();
                    allHeight -= chatActivityEnterView.getEmojiPadding();
                }

                int childCount = getChildCount();

                measureChildWithMargins(chatActivityEnterView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                inputFieldHeight = chatActivityEnterView.getMeasuredHeight();

                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == chatActivityEnterView || child == actionBar) {
                        continue;
                    }
                    if (child == chatListView || child == progressView) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize - inputFieldHeight + AndroidUtilities.dp(2 + (chatActivityEnterView.isTopViewVisible() ? 48 : 0))), MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else if (child == instantCameraView || child == overlayView) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(allHeight - inputFieldHeight + AndroidUtilities.dp(3), MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else if (child == emptyViewContainer) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else if (chatActivityEnterView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(320), heightSize - inputFieldHeight + actionBarHeight - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight + actionBarHeight - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else if (child == mentionContainer) {
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mentionContainer.getLayoutParams();
                        if (mentionsAdapter.isBannedInline()) {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST));
                        } else {
                            int height;
                            mentionListViewIgnoreLayout = true;
                            if (mentionsAdapter.isBotContext() && mentionsAdapter.isMediaLayout()) {
                                int size = mentionGridLayoutManager.getRowsCount(widthSize);
                                int maxHeight = size * 102;
                                if (mentionsAdapter.isBotContext()) {
                                    if (mentionsAdapter.getBotContextSwitch() != null) {
                                        maxHeight += 34;
                                    }
                                }
                                height = heightSize - chatActivityEnterView.getMeasuredHeight() + (maxHeight != 0 ? AndroidUtilities.dp(2) : 0);
                                int padding = Math.max(0, height - AndroidUtilities.dp(Math.min(maxHeight, 68 * 1.8f)));
                                if (mentionLayoutManager.getReverseLayout()) {
                                    mentionListView.setPadding(0, 0, 0, padding);
                                } else {
                                    mentionListView.setPadding(0, padding, 0, 0);
                                }
                            } else {
                                int size = mentionsAdapter.getItemCount();
                                int maxHeight = 0;
                                if (mentionsAdapter.isBotContext()) {
                                    if (mentionsAdapter.getBotContextSwitch() != null) {
                                        maxHeight += 36;
                                        size -= 1;
                                    }
                                    maxHeight += size * 68;
                                } else {
                                    maxHeight += size * 36;
                                }
                                height = heightSize - chatActivityEnterView.getMeasuredHeight() + (maxHeight != 0 ? AndroidUtilities.dp(2) : 0);
                                int padding = Math.max(0, height - AndroidUtilities.dp(Math.min(maxHeight, 68 * 1.8f)));
                                if (mentionLayoutManager.getReverseLayout()) {
                                    mentionListView.setPadding(0, 0, 0, padding);
                                } else {
                                    mentionListView.setPadding(0, padding, 0, 0);
                                }
                            }

                            layoutParams.height = height;
                            layoutParams.topMargin = 0;

                            mentionListViewIgnoreLayout = false;
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? chatActivityEnterView.getEmojiPadding() : 0;
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
                            if (child != actionBar && actionBar.getVisibility() == VISIBLE) {
                                childTop += actionBar.getMeasuredHeight();
                            }
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
                    } else if (child == mentiondownButton) {
                        childTop -= chatActivityEnterView.getMeasuredHeight();
                    } else if (child == emptyViewContainer) {
                        childTop -= inputFieldHeight / 2 - (actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() / 2 : 0);
                    } else if (chatActivityEnterView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow) {
                            childTop = chatActivityEnterView.getTop() - child.getMeasuredHeight() + AndroidUtilities.dp(1);
                        } else {
                            childTop = chatActivityEnterView.getBottom();
                        }
                    } else if (child == gifHintTextView || child == voiceHintTextView || child == mediaBanTooltip) {
                        childTop -= inputFieldHeight;
                    } else if (child == chatListView || child == progressView) {
                        if (chatActivityEnterView.isTopViewVisible()) {
                            childTop -= AndroidUtilities.dp(48);
                        }
                    } else if (child == actionBar) {
                        childTop -= getPaddingTop();
                    } else if (child == roundVideoContainer) {
                        childTop = actionBar.getMeasuredHeight();
                    } else if (child == instantCameraView || child == overlayView) {
                        childTop = 0;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                updateMessagesVisisblePart();
                notifyHeightChanged();
            }
        };

        contentView = (SizeNotifierFrameLayout) fragmentView;

        contentView.setBackgroundImage(Theme.getCachedWallpaper());

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
            if (currentUser != null && currentUser.self) {
                bigEmptyView = new ChatBigEmptyView(context, false);
                emptyViewContainer.addView(bigEmptyView, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            } else {
                emptyView = new TextView(context);
                if (currentUser != null && currentUser.id != 777000 && currentUser.id != 429000 && currentUser.id != 4244000 && MessagesController.isSupportId(currentUser.id)) {
                    emptyView.setText(LocaleController.getString("GotAQuestion", R.string.GotAQuestion));
                } else {
                    emptyView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
                }
                emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                emptyView.setGravity(Gravity.CENTER);
                emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
                emptyView.setBackgroundResource(R.drawable.system);
                emptyView.getBackground().setColorFilter(Theme.colorFilter);
                emptyView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                emptyView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(2), AndroidUtilities.dp(10), AndroidUtilities.dp(3));
                emptyViewContainer.addView(emptyView, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            }
        } else {
            bigEmptyView = new ChatBigEmptyView(context, true);
            if (currentEncryptedChat.admin_id == UserConfig.getClientUserId()) {
                bigEmptyView.setSecretText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, UserObject.getFirstName(currentUser)));
            } else {
                bigEmptyView.setSecretText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, UserObject.getFirstName(currentUser)));
            }
            emptyViewContainer.addView(bigEmptyView, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        CharSequence oldMessage;
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
            if (!chatActivityEnterView.isEditingMessage()) {
                oldMessage = chatActivityEnterView.getFieldText();
            } else {
                oldMessage = null;
            }
        } else {
            oldMessage = null;
        }
        if (mentionsAdapter != null) {
            mentionsAdapter.onDestroy();
        }

        chatListView = new RecyclerListView(context) {

            ArrayList<ChatMessageCell> drawTimeAfter = new ArrayList<>();
            ArrayList<ChatMessageCell> drawNamesAfter = new ArrayList<>();
            ArrayList<ChatMessageCell> drawCaptionAfter = new ArrayList<>();

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                forceScrollToTop = false;
                if (chatAdapter.isBot) {
                    int childCount = getChildCount();
                    for (int a = 0; a < childCount; a++) {
                        View child = getChildAt(a);
                        if (child instanceof BotHelpCell) {
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

            @Override
            protected void onChildPressed(View child, boolean pressed) {
                super.onChildPressed(child, pressed);
                if (child instanceof ChatMessageCell) {
                    MessageObject.GroupedMessages groupedMessages = ((ChatMessageCell) child).getCurrentMessagesGroup();
                    if (groupedMessages != null) {
                        int count = getChildCount();
                        for (int a = 0; a < count; a++) {
                            View item = getChildAt(a);
                            if (item == child || !(item instanceof ChatMessageCell)) {
                                continue;
                            }
                            ChatMessageCell cell = (ChatMessageCell) item;
                            if (((ChatMessageCell) item).getCurrentMessagesGroup() == groupedMessages) {
                                cell.setPressed(pressed);
                            }
                        }
                    }
                }
            }

            @Override
            public void requestLayout() {
                if (chatListViewIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                int clipLeft = 0;
                int clipBottom = 0;
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) child;
                    MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                    if (position != null) {
                        if (position.pw != position.spanSize && position.spanSize == 1000 && position.siblingHeights == null && group.hasSibling) {
                            clipLeft = ((ChatMessageCell) child).getBackgroundDrawableLeft();
                        } else if (position.siblingHeights != null) {
                            clipBottom = child.getBottom() - AndroidUtilities.dp(1 + (cell.isPinnedBottom() ? 1 : 0));
                        }
                    }
                }
                if (clipLeft != 0) {
                    canvas.save();
                    canvas.clipRect(clipLeft, child.getTop(), child.getRight(), child.getBottom());
                } else if (clipBottom != 0) {
                    canvas.save();
                    canvas.clipRect(child.getLeft(), child.getTop(), child.getRight(), clipBottom);
                }
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (clipLeft != 0 || clipBottom != 0) {
                    canvas.restore();
                }
                int num = 0;
                int count = getChildCount();
                for (int a = 0; a < count; a++) {
                    if (getChildAt(a) == child) {
                        num = a;
                        break;
                    }
                }
                if (num == count - 1) {
                    int size = drawTimeAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            ChatMessageCell cell = drawTimeAfter.get(a);
                            canvas.save();
                            canvas.translate(cell.getLeft(), cell.getTop());
                            cell.drawTimeLayout(canvas);
                            canvas.restore();
                        }
                        drawTimeAfter.clear();
                    }
                    size = drawNamesAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            ChatMessageCell cell = drawNamesAfter.get(a);
                            canvas.save();
                            canvas.translate(cell.getLeft(), cell.getTop());
                            cell.drawNamesLayout(canvas);
                            canvas.restore();
                        }
                        drawNamesAfter.clear();
                    }
                    size = drawCaptionAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            ChatMessageCell cell = drawCaptionAfter.get(a);
                            canvas.save();
                            canvas.translate(cell.getLeft(), cell.getTop());
                            cell.drawCaptionLayout(canvas);
                            canvas.restore();
                        }
                        drawCaptionAfter.clear();
                    }
                }
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) child;

                    MessageObject.GroupedMessagePosition position = chatMessageCell.getCurrentPosition();
                    if (position != null) {
                        if (position.last || position.minX == 0 && position.minY == 0) {
                            if (num == count - 1) {
                                canvas.save();
                                canvas.translate(chatMessageCell.getLeft(), chatMessageCell.getTop());
                                if (position.last) {
                                    chatMessageCell.drawTimeLayout(canvas);
                                }
                                if (position.minX == 0 && position.minY == 0) {
                                    chatMessageCell.drawNamesLayout(canvas);
                                }
                                canvas.restore();
                            } else {
                                if (position.last) {
                                    drawTimeAfter.add(chatMessageCell);
                                }
                                if (position.minX == 0 && position.minY == 0 && chatMessageCell.hasNameLayout()) {
                                    drawNamesAfter.add(chatMessageCell);
                                }
                            }
                        }
                        if (num == count - 1) {
                            canvas.save();
                            canvas.translate(chatMessageCell.getLeft(), chatMessageCell.getTop());
                            if (chatMessageCell.hasCaptionLayout() && (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0 && (position.flags & MessageObject.POSITION_FLAG_LEFT) != 0) {
                                chatMessageCell.drawCaptionLayout(canvas);
                            }
                            canvas.restore();
                        } else {
                            if (chatMessageCell.hasCaptionLayout() && (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0 && (position.flags & MessageObject.POSITION_FLAG_LEFT) != 0) {
                                drawCaptionAfter.add(chatMessageCell);
                            }
                        }
                    }
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
                        MessageObject message = chatMessageCell.getMessageObject();

                        MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);

                        int top = child.getTop();
                        if (chatMessageCell.isPinnedBottom()) {
                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            if (holder != null) {
                                int p = holder.getAdapterPosition();
                                int nextPosition;
                                if (groupedMessages != null && position != null) {
                                    int idx = groupedMessages.posArray.indexOf(position);
                                    int size = groupedMessages.posArray.size();
                                    if ((position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                        nextPosition = p - size + idx;
                                    } else {
                                        nextPosition = p - 1;
                                        for (int a = idx + 1; idx < size; a++) {
                                            if (groupedMessages.posArray.get(a).minY > position.maxY) {
                                                break;
                                            } else {
                                                nextPosition--;
                                            }
                                        }
                                    }
                                } else {
                                    nextPosition = p - 1;
                                }
                                holder = chatListView.findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    imageReceiver.setImageY(-AndroidUtilities.dp(1000));
                                    imageReceiver.draw(canvas);
                                    return result;
                                }
                            }
                        }
                        if (chatMessageCell.isPinnedTop()) {
                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            if (holder != null) {
                                while (true) {
                                    int p = holder.getAdapterPosition();
                                    int prevPosition;
                                    if (groupedMessages != null && position != null) {
                                        int idx = groupedMessages.posArray.indexOf(position);
                                        int size = groupedMessages.posArray.size();
                                        if ((position.flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                                            prevPosition = p + idx + 1;
                                        } else {
                                            prevPosition = p + 1;
                                            for (int a = idx - 1; idx >= 0; a--) {
                                                if (groupedMessages.posArray.get(a).maxY < position.minY) {
                                                    break;
                                                } else {
                                                    prevPosition++;
                                                }
                                            }
                                        }
                                    } else {
                                        prevPosition = p + 1;
                                    }
                                    holder = chatListView.findViewHolderForAdapterPosition(prevPosition);
                                    if (holder != null) {
                                        top = holder.itemView.getTop();
                                        if (!(holder.itemView instanceof ChatMessageCell) || !((ChatMessageCell) holder.itemView).isPinnedTop()) {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                        int y = child.getTop() + chatMessageCell.getLayoutHeight();
                        int maxY = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }
                        if (y - AndroidUtilities.dp(48) < top) {
                            y = top + AndroidUtilities.dp(48);
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(44));
                        imageReceiver.draw(canvas);
                    }
                }
                return result;
            }
        };
        chatListView.setTag(1);
        chatListView.setVerticalScrollBarEnabled(true);
        chatListView.setAdapter(chatAdapter = new ChatActivityAdapter(context));
        chatListView.setClipToPadding(false);
        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
        chatListView.setItemAnimator(null);
        chatListView.setLayoutAnimation(null);

        chatLayoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerMiddle linearSmoothScroller = new LinearSmoothScrollerMiddle(recyclerView.getContext());
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }

            @Override
            public boolean shouldLayoutChildFromOpositeSide(View child) {
                if (child instanceof ChatMessageCell) {
                    return !((ChatMessageCell) child).getMessageObject().isOutOwner();
                }
                return false;
            }

            @Override
            protected boolean hasSiblingChild(int position) {
                if (position >= chatAdapter.messagesStartRow && position < chatAdapter.messagesEndRow) {
                    int index = position - chatAdapter.messagesStartRow;
                    if (index >= 0 && index < messages.size()) {
                        MessageObject message = messages.get(index);
                        MessageObject.GroupedMessages group = getValidGroupedMessage(message);
                        if (group != null) {
                            MessageObject.GroupedMessagePosition pos = group.positions.get(message);
                            if (pos.minX == pos.maxX || pos.minY != pos.maxY || pos.minY == 0) {
                                return false;
                            }
                            int count = group.posArray.size();
                            for (int a = 0; a < count; a++) {
                                MessageObject.GroupedMessagePosition p = group.posArray.get(a);
                                if (p == pos) {
                                    continue;
                                }
                                if (p.minY <= pos.minY && p.maxY >= pos.minY) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                return false;
            }
        };
        chatLayoutManager.setSpanSizeLookup(new GridLayoutManagerFixed.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position >= chatAdapter.messagesStartRow && position < chatAdapter.messagesEndRow) {
                    int idx = position - chatAdapter.messagesStartRow;
                    if (idx >= 0 && idx < messages.size()) {
                        MessageObject message = messages.get(idx);
                        MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);
                        if (groupedMessages != null) {
                            return groupedMessages.positions.get(message).spanSize;
                        }
                    }
                }
                return 1000;
            }
        });
        chatListView.setLayoutManager(chatLayoutManager);
        chatListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.bottom = 0;
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                    if (group != null) {
                        MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                        if (position != null && position.siblingHeights != null) {
                            float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                            int h = 0;
                            for (int a = 0; a < position.siblingHeights.length; a++) {
                                h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                            }
                            h += (position.maxY - position.minY) * AndroidUtilities.dp(11);
                            int count = group.posArray.size();
                            for (int a = 0; a < count; a++) {
                                MessageObject.GroupedMessagePosition pos = group.posArray.get(a);
                                if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
                                    continue;
                                }
                                if (pos.minY == position.minY) {
                                    h -= (int) Math.ceil(maxHeight * pos.ph) - AndroidUtilities.dp(4);
                                    break;
                                }
                            }
                            outRect.bottom = -h;
                        }
                    }
                }
            }
        });
        contentView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        chatListView.setOnItemLongClickListener(onItemLongClickListener);
        chatListView.setOnItemClickListener(onItemClickListener);
        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            private float totalDy = 0;
            private final int scrollValue = AndroidUtilities.dp(100);

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollingFloatingDate = true;
                    checkTextureViewPosition = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollingFloatingDate = false;
                    checkTextureViewPosition = false;
                    hideFloatingDateView(true);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
                if (dy != 0 && scrollingFloatingDate && !currentFloatingTopIsNotMessage) {
                    if (highlightMessageId != Integer.MAX_VALUE) {
                        highlightMessageId = Integer.MAX_VALUE;
                        updateVisibleRows();
                    }
                    if (floatingDateView.getTag() == null) {
                        if (floatingDateAnimation != null) {
                            floatingDateAnimation.cancel();
                        }
                        floatingDateView.setTag(1);
                        floatingDateAnimation = new AnimatorSet();
                        floatingDateAnimation.setDuration(150);
                        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 1.0f));
                        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animation.equals(floatingDateAnimation)) {
                                    floatingDateAnimation = null;
                                }
                            }
                        });
                        floatingDateAnimation.start();
                    }
                }
                checkScrollForLoad(true);
                int firstVisibleItem = chatLayoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem != RecyclerView.NO_POSITION) {
                    int totalItemCount = chatAdapter.getItemCount();
                    if (firstVisibleItem == 0 && forwardEndReached[0]) {
                        showPagedownButton(false, true);
                    } else {
                        if (dy > 0) {
                            if (pagedownButton.getTag() == null) {
                                totalDy += dy;
                                if (totalDy > scrollValue) {
                                    totalDy = 0;
                                    showPagedownButton(true, true);
                                    pagedownButtonShowedByScroll = true;
                                }
                            }
                        } else {
                            if (pagedownButtonShowedByScroll && pagedownButton.getTag() != null) {
                                totalDy += dy;
                                if (totalDy < -scrollValue) {
                                    showPagedownButton(false, true);
                                    totalDy = 0;
                                }
                            }
                        }
                    }
                }
                updateMessagesVisisblePart();
            }
        });
        if (scrollToPositionOnRecreate != -1) {
            chatLayoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            scrollToPositionOnRecreate = -1;
        }

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.INVISIBLE);
        contentView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        progressView2 = new View(context);
        progressView2.setBackgroundResource(R.drawable.system_loader);
        progressView2.getBackground().setColorFilter(Theme.colorFilter);
        progressView.addView(progressView2, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(28));
        progressBar.setProgressColor(Theme.getColor(Theme.key_chat_serviceText));
        progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setAlpha(0.0f);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        floatingDateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (floatingDateView.getAlpha() == 0) {
                    return;
                }
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis((long) floatingDateView.getCustomDate() * 1000);
                int year = calendar.get(Calendar.YEAR);
                int monthOfYear = calendar.get(Calendar.MONTH);
                int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

                calendar.clear();
                calendar.set(year, monthOfYear, dayOfMonth);
                jumpToDate((int) (calendar.getTime().getTime() / 1000));
            }
        });

        if (ChatObject.isChannel(currentChat)) {
            pinnedMessageView = new FrameLayout(context);
            pinnedMessageView.setTag(1);
            pinnedMessageView.setTranslationY(-AndroidUtilities.dp(50));
            pinnedMessageView.setVisibility(View.GONE);
            pinnedMessageView.setBackgroundResource(R.drawable.blockpanel);
            pinnedMessageView.getBackground().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelBackground), PorterDuff.Mode.MULTIPLY));
            contentView.addView(pinnedMessageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));
            pinnedMessageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollToMessageId(info.pinned_msg_id, 0, true, 0, false);
                }
            });

            pinnedLineView = new View(context);
            pinnedLineView.setBackgroundColor(Theme.getColor(Theme.key_chat_topPanelLine));
            pinnedMessageView.addView(pinnedLineView, LayoutHelper.createFrame(2, 32, Gravity.LEFT | Gravity.TOP, 8, 8, 0, 0));

            pinnedMessageImageView = new BackupImageView(context);
            pinnedMessageView.addView(pinnedMessageImageView, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.LEFT, 17, 8, 0, 0));

            pinnedMessageNameTextView = new SimpleTextView(context);
            pinnedMessageNameTextView.setTextSize(14);
            pinnedMessageNameTextView.setTextColor(Theme.getColor(Theme.key_chat_topPanelTitle));
            pinnedMessageNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            pinnedMessageView.addView(pinnedMessageNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(18), Gravity.TOP | Gravity.LEFT, 18, 7.3f, 52, 0));

            pinnedMessageTextView = new SimpleTextView(context);
            pinnedMessageTextView.setTextSize(14);
            pinnedMessageTextView.setTextColor(Theme.getColor(Theme.key_chat_topPanelMessage));
            pinnedMessageView.addView(pinnedMessageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(18), Gravity.TOP | Gravity.LEFT, 18, 25.3f, 52, 0));

            closePinned = new ImageView(context);
            closePinned.setImageResource(R.drawable.miniplayer_close);
            closePinned.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose), PorterDuff.Mode.MULTIPLY));
            closePinned.setScaleType(ImageView.ScaleType.CENTER);
            pinnedMessageView.addView(closePinned, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
            closePinned.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (currentChat.creator || currentChat.admin_rights != null && (currentChat.megagroup && currentChat.admin_rights.pin_messages || !currentChat.megagroup && currentChat.admin_rights.edit_messages)) {
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
        reportSpamView.setTranslationY(-AndroidUtilities.dp(50));
        reportSpamView.setVisibility(View.GONE);
        reportSpamView.setBackgroundResource(R.drawable.blockpanel);
        reportSpamView.getBackground().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelBackground), PorterDuff.Mode.MULTIPLY));
        contentView.addView(reportSpamView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));

        addToContactsButton = new TextView(context);
        addToContactsButton.setTextColor(Theme.getColor(Theme.key_chat_addContact));
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
        reportSpamButton.setTextColor(Theme.getColor(Theme.key_chat_reportSpam));
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
                        MessagesController.getInstance().reportSpam(dialog_id, currentUser, currentChat, currentEncryptedChat);
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

        closeReportSpam = new ImageView(context);
        closeReportSpam.setImageResource(R.drawable.miniplayer_close);
        closeReportSpam.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose), PorterDuff.Mode.MULTIPLY));
        closeReportSpam.setScaleType(ImageView.ScaleType.CENTER);
        reportSpamContainer.addView(closeReportSpam, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
        closeReportSpam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MessagesController.getInstance().hideReportSpam(dialog_id, currentUser, currentChat);
                updateSpamView();
            }
        });

        alertView = new FrameLayout(context);
        alertView.setTag(1);
        alertView.setTranslationY(-AndroidUtilities.dp(50));
        alertView.setVisibility(View.GONE);
        alertView.setBackgroundResource(R.drawable.blockpanel);
        alertView.getBackground().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelBackground), PorterDuff.Mode.MULTIPLY));
        contentView.addView(alertView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));

        alertNameTextView = new TextView(context);
        alertNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        alertNameTextView.setTextColor(Theme.getColor(Theme.key_chat_topPanelTitle));
        alertNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        alertNameTextView.setSingleLine(true);
        alertNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        alertNameTextView.setMaxLines(1);
        alertView.addView(alertNameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 8, 5, 8, 0));

        alertTextView = new TextView(context);
        alertTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        alertTextView.setTextColor(Theme.getColor(Theme.key_chat_topPanelMessage));
        alertTextView.setSingleLine(true);
        alertTextView.setEllipsize(TextUtils.TruncateAt.END);
        alertTextView.setMaxLines(1);
        alertView.addView(alertTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 8, 23, 8, 0));

        pagedownButton = new FrameLayout(context);
        pagedownButton.setVisibility(View.INVISIBLE);
        contentView.addView(pagedownButton, LayoutHelper.createFrame(46, 59, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 7, 5));
        pagedownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkTextureViewPosition = true;
                if (createUnreadMessageAfterId != 0) {
                    scrollToMessageId(createUnreadMessageAfterId, 0, false, returnToLoadIndex, false);
                } else if (returnToMessageId > 0) {
                    scrollToMessageId(returnToMessageId, 0, true, returnToLoadIndex, false);
                } else {
                    scrollToLastMessage(true);
                }
            }
        });

        mentiondownButton = new FrameLayout(context);
        mentiondownButton.setVisibility(View.INVISIBLE);
        contentView.addView(mentiondownButton, LayoutHelper.createFrame(46, 59, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 7, 5));
        mentiondownButton.setOnClickListener(new View.OnClickListener() {

            private void loadLastUnreadMention() {
                if (hasAllMentionsLocal) {
                    MessagesStorage.getInstance().getUnreadMention(dialog_id, new MessagesStorage.IntCallback() {
                        @Override
                        public void run(int param) {
                            if (param == 0) {
                                hasAllMentionsLocal = false;
                                loadLastUnreadMention();
                            } else {
                                scrollToMessageId(param, 0, false, 0, false);
                            }
                        }
                    });
                } else {
                    TLRPC.TL_messages_getUnreadMentions req = new TLRPC.TL_messages_getUnreadMentions();
                    req.peer = MessagesController.getInputPeer((int) dialog_id);
                    req.limit = 1;
                    req.add_offset = newMentionsCount - 1;
                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(final TLObject response, final TLRPC.TL_error error) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                    if (error != null || res.messages.isEmpty()) {
                                        if (res != null) {
                                            newMentionsCount = res.count;
                                        } else {
                                            newMentionsCount = 0;
                                        }
                                        MessagesStorage.getInstance().resetMentionsCount(dialog_id, newMentionsCount);
                                        if (newMentionsCount == 0) {
                                            hasAllMentionsLocal = true;
                                            showMentiondownButton(false, true);
                                        } else {
                                            mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                                            loadLastUnreadMention();
                                        }
                                    } else {
                                        int id = res.messages.get(0).id;
                                        long mid = id;
                                        if (ChatObject.isChannel(currentChat)) {
                                            mid = mid | (((long) currentChat.id) << 32);
                                        }
                                        MessageObject object = messagesDict[0].get(id);
                                        MessagesStorage.getInstance().markMessageAsMention(mid);
                                        if (object != null) {
                                            object.messageOwner.media_unread = true;
                                            object.messageOwner.mentioned = true;
                                        }
                                        scrollToMessageId(id, 0, false, 0, false);
                                    }
                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void onClick(View view) {
                loadLastUnreadMention();
            }
        });

        mentiondownButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                for (int a = 0; a < messages.size(); a++) {
                    MessageObject messageObject = messages.get(a);
                    if (messageObject.messageOwner.mentioned && !messageObject.isContentUnread()) {
                        messageObject.setContentIsRead();
                    }
                }
                newMentionsCount = 0;
                MessagesStorage.getInstance().resetMentionsCount(dialog_id, newMentionsCount);
                hasAllMentionsLocal = true;
                showMentiondownButton(false, true);
                TLRPC.TL_messages_readMentions req = new TLRPC.TL_messages_readMentions();
                req.peer = MessagesController.getInputPeer((int) dialog_id);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });
                return true;
            }
        });

        if (!isBroadcast) {
            mentionContainer = new FrameLayout(context) {

                @Override
                public void onDraw(Canvas canvas) {
                    if (mentionListView.getChildCount() <= 0) {
                        return;
                    }
                    if (mentionLayoutManager.getReverseLayout()) {
                        int top = mentionListViewScrollOffsetY + AndroidUtilities.dp(2);
                        int bottom = top + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                        Theme.chat_composeShadowDrawable.setBounds(0, bottom, getMeasuredWidth(), top);
                        Theme.chat_composeShadowDrawable.draw(canvas);
                        canvas.drawRect(0, 0, getMeasuredWidth(), top, Theme.chat_composeBackgroundPaint);
                    } else {
                        int top;
                        if (mentionsAdapter.isBotContext() && mentionsAdapter.isMediaLayout() && mentionsAdapter.getBotContextSwitch() == null) {
                            top = mentionListViewScrollOffsetY - AndroidUtilities.dp(4);
                        } else {
                            top = mentionListViewScrollOffsetY - AndroidUtilities.dp(2);
                        }
                        int bottom = top + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                        Theme.chat_composeShadowDrawable.setBounds(0, top, getMeasuredWidth(), bottom);
                        Theme.chat_composeShadowDrawable.draw(canvas);
                        canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
                    }
                }

                @Override
                public void requestLayout() {
                    if (mentionListViewIgnoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }
            };
            mentionContainer.setVisibility(View.GONE);
            mentionContainer.setWillNotDraw(false);
            contentView.addView(mentionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 110, Gravity.LEFT | Gravity.BOTTOM));

            mentionListView = new RecyclerListView(context) {

                private int lastWidth;
                private int lastHeight;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    if (mentionLayoutManager.getReverseLayout()) {
                        if (!mentionListViewIsScrolling && mentionListViewScrollOffsetY != 0 && event.getY() > mentionListViewScrollOffsetY) {
                            return false;
                        }
                    } else {
                        if (!mentionListViewIsScrolling && mentionListViewScrollOffsetY != 0 && event.getY() < mentionListViewScrollOffsetY) {
                            return false;
                        }
                    }
                    boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, mentionListView, 0, null);
                    return super.onInterceptTouchEvent(event) || result;
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (mentionLayoutManager.getReverseLayout()) {
                        if (!mentionListViewIsScrolling && mentionListViewScrollOffsetY != 0 && event.getY() > mentionListViewScrollOffsetY) {
                            return false;
                        }
                    } else {
                        if (!mentionListViewIsScrolling && mentionListViewScrollOffsetY != 0 && event.getY() < mentionListViewScrollOffsetY) {
                            return false;
                        }
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
                    if (!mentionLayoutManager.getReverseLayout() && mentionListView != null && mentionListViewLastViewPosition >= 0 && width == lastWidth && height - lastHeight != 0) {
                        newPosition = mentionListViewLastViewPosition;
                        newTop = mentionListViewLastViewTop + height - lastHeight - getPaddingTop();
                    }

                    super.onLayout(changed, l, t, r, b);

                    if (newPosition != -1) {
                        mentionListViewIgnoreLayout = true;
                        if (mentionsAdapter.isBotContext() && mentionsAdapter.isMediaLayout()) {
                            mentionGridLayoutManager.scrollToPositionWithOffset(newPosition, newTop);
                        } else {
                            mentionLayoutManager.scrollToPositionWithOffset(newPosition, newTop);
                        }
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
                    return StickerPreviewViewer.getInstance().onTouch(event, mentionListView, 0, mentionsOnItemClickListener, null);
                }
            });
            mentionListView.setTag(2);
            mentionLayoutManager = new LinearLayoutManager(context) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            };
            mentionLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mentionGridLayoutManager = new ExtendedGridLayoutManager(context, 100) {

                private Size size = new Size();

                @Override
                protected Size getSizeForItem(int i) {
                    if (mentionsAdapter.getBotContextSwitch() != null) {
                        i++;
                    }
                    Object object = mentionsAdapter.getItem(i);
                    if (object instanceof TLRPC.BotInlineResult) {
                        TLRPC.BotInlineResult inlineResult = (TLRPC.BotInlineResult) object;
                        if (inlineResult.document != null) {
                            size.width = inlineResult.document.thumb != null ? inlineResult.document.thumb.w : 100;
                            size.height = inlineResult.document.thumb != null ? inlineResult.document.thumb.h : 100;
                            for (int b = 0; b < inlineResult.document.attributes.size(); b++) {
                                TLRPC.DocumentAttribute attribute = inlineResult.document.attributes.get(b);
                                if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                    size.width = attribute.w;
                                    size.height = attribute.h;
                                    break;
                                }
                            }
                        } else {
                            size.width = inlineResult.w;
                            size.height = inlineResult.h;
                        }
                    }
                    return size;
                }

                @Override
                protected int getFlowItemCount() {
                    if (mentionsAdapter.getBotContextSwitch() != null) {
                        return getItemCount() - 1;
                    }
                    return super.getFlowItemCount();
                }
            };
            mentionGridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    Object object = mentionsAdapter.getItem(position);
                    if (object instanceof TLRPC.TL_inlineBotSwitchPM) {
                        return 100;
                    } else {
                        if (mentionsAdapter.getBotContextSwitch() != null) {
                            position--;
                        }
                        return mentionGridLayoutManager.getSpanSizeForItem(position);
                    }
                }
            });
            mentionListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    outRect.left = 0;
                    outRect.right = 0;
                    outRect.top = 0;
                    outRect.bottom = 0;
                    if (parent.getLayoutManager() == mentionGridLayoutManager) {
                        int position = parent.getChildAdapterPosition(view);
                        if (mentionsAdapter.getBotContextSwitch() != null) {
                            if (position == 0) {
                                return;
                            }
                            position--;
                            if (!mentionGridLayoutManager.isFirstRow(position)) {
                                outRect.top = AndroidUtilities.dp(2);
                            }
                        } else {
                            outRect.top = AndroidUtilities.dp(2);
                        }
                        outRect.right = mentionGridLayoutManager.isLastInRow(position) ? 0 : AndroidUtilities.dp(2);
                    }
                }
            });
            mentionListView.setItemAnimator(null);
            mentionListView.setLayoutAnimation(null);
            mentionListView.setClipToPadding(false);
            mentionListView.setLayoutManager(mentionLayoutManager);
            mentionListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
            mentionContainer.addView(mentionListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            mentionListView.setAdapter(mentionsAdapter = new MentionsAdapter(context, false, dialog_id, new MentionsAdapter.MentionsAdapterDelegate() {
                @Override
                public void needChangePanelVisibility(boolean show) {
                    if (mentionsAdapter.isBotContext() && mentionsAdapter.isMediaLayout()) {
                        mentionListView.setLayoutManager(mentionGridLayoutManager);
                    } else {
                        mentionListView.setLayoutManager(mentionLayoutManager);
                    }
                    if (show && bottomOverlay.getVisibility() == View.VISIBLE) {
                        show = false;
                    }
                    if (show) {
                        if (mentionListAnimation != null) {
                            mentionListAnimation.cancel();
                            mentionListAnimation = null;
                        }

                        if (mentionContainer.getVisibility() == View.VISIBLE) {
                            mentionContainer.setAlpha(1.0f);
                            return;
                        }
                        if (mentionsAdapter.isBotContext() && mentionsAdapter.isMediaLayout()) {
                            mentionGridLayoutManager.scrollToPositionWithOffset(0, 10000);
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
                            mentionListAnimation = new AnimatorSet();
                            mentionListAnimation.playTogether(
                                    ObjectAnimator.ofFloat(mentionContainer, "alpha", 0.0f, 1.0f)
                            );
                            mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionListAnimation = null;
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionListAnimation = null;
                                    }
                                }
                            });
                            mentionListAnimation.setDuration(200);
                            mentionListAnimation.start();
                        } else {
                            mentionContainer.setAlpha(1.0f);
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
                            mentionListAnimation = new AnimatorSet();
                            mentionListAnimation.playTogether(
                                    ObjectAnimator.ofFloat(mentionContainer, "alpha", 0.0f)
                            );
                            mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionContainer.setVisibility(View.GONE);
                                        mentionContainer.setTag(null);
                                        mentionListAnimation = null;
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                    if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                        mentionListAnimation = null;
                                    }
                                }
                            });
                            mentionListAnimation.setDuration(200);
                            mentionListAnimation.start();
                        } else {
                            mentionContainer.setTag(null);
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
                        EmbedBottomSheet.show(getParentActivity(), result.title != null ? result.title : "", result.description, result.content_url, result.content_url, result.w, result.h);
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
                    if (mentionsAdapter.isBannedInline()) {
                        return;
                    }
                    Object object = mentionsAdapter.getItem(position);
                    int start = mentionsAdapter.getResultStartPosition();
                    int len = mentionsAdapter.getResultLength();
                    if (object instanceof TLRPC.User) {
                        if (searchingForUser && searchContainer.getVisibility() == View.VISIBLE) {
                            searchingUserMessages = (TLRPC.User) object;
                            if (searchingUserMessages == null) {
                                return;
                            }
                            String name = searchingUserMessages.first_name;
                            if (TextUtils.isEmpty(name)) {
                                name = searchingUserMessages.last_name;
                            }
                            searchingForUser = false;
                            String from = LocaleController.getString("SearchFrom", R.string.SearchFrom);
                            Spannable spannable = new SpannableString(from + " " + name);
                            spannable.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_actionBarDefaultSubtitle)), from.length() + 1, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            searchItem.setSearchFieldCaption(spannable);
                            mentionsAdapter.searchUsernameOrHashtag(null, 0, null, false);
                            searchItem.getSearchField().setHint(null);
                            searchItem.clearSearchText();
                            MessagesSearchQuery.searchMessagesInChat("", dialog_id, mergeDialogId, classGuid, 0, searchingUserMessages);
                        } else {
                            TLRPC.User user = (TLRPC.User) object;
                            if (user != null) {
                                if (user.username != null) {
                                    chatActivityEnterView.replaceWithText(start, len, "@" + user.username + " ", false);
                                } else {
                                    String name = UserObject.getFirstName(user);
                                    Spannable spannable = new SpannableString(name + " ");
                                    spannable.setSpan(new URLSpanUserMention("" + user.id, true), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    chatActivityEnterView.replaceWithText(start, len, spannable, false);
                                }
                            }
                        }
                    } else if (object instanceof String) {
                        if (mentionsAdapter.isBotCommands()) {
                            SendMessagesHelper.getInstance().sendMessage((String) object, dialog_id, replyingMessageObject, null, false, null, null, null);
                            chatActivityEnterView.setFieldText("");
                            showReplyPanel(false, null, null, null, false);
                        } else {
                            chatActivityEnterView.replaceWithText(start, len, object + " ", false);
                        }
                    } else if (object instanceof TLRPC.BotInlineResult) {
                        if (chatActivityEnterView.getFieldText() == null) {
                            return;
                        }
                        TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) object;
                        if ((result.type.equals("photo") && (result.photo != null || result.content_url != null) ||
                                result.type.equals("gif") && (result.document != null || result.content_url != null) ||
                                result.type.equals("video") && (result.document != null/* || result.content_url != null*/))) {
                            ArrayList<Object> arrayList = botContextResults = new ArrayList<Object>(mentionsAdapter.getSearchResultBotContext());
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            PhotoViewer.getInstance().openPhotoForSelect(arrayList, mentionsAdapter.getItemPosition(position), 3, botContextProvider, null);
                        } else {
                            sendBotInlineResult(result);
                        }
                    } else if (object instanceof TLRPC.TL_inlineBotSwitchPM) {
                        processInlineBotContextPM((TLRPC.TL_inlineBotSwitchPM) object);
                    } else if (object instanceof EmojiSuggestion) {
                        String code = ((EmojiSuggestion) object).emoji;
                        chatActivityEnterView.addEmojiToRecent(code);
                        chatActivityEnterView.replaceWithText(start, len, code, true);
                    }
                }
            });

            mentionListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                @Override
                public boolean onItemClick(View view, int position) {
                    if (getParentActivity() == null || !mentionsAdapter.isLongClickEnabled()) {
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
                    int lastVisibleItem;
                    if (mentionsAdapter.isBotContext() && mentionsAdapter.isMediaLayout()) {
                        lastVisibleItem = mentionGridLayoutManager.findLastVisibleItemPosition();
                    } else {
                        lastVisibleItem = mentionLayoutManager.findLastVisibleItemPosition();
                    }
                    int visibleItemCount = lastVisibleItem == RecyclerView.NO_POSITION ? 0 : lastVisibleItem;
                    if (visibleItemCount > 0 && lastVisibleItem > mentionsAdapter.getItemCount() - 5) {
                        mentionsAdapter.searchForContextBotForNextOffset();
                    }
                    mentionListViewUpdateLayout();
                }
            });
        }

        pagedownButtonImage = new ImageView(context);
        pagedownButtonImage.setImageResource(R.drawable.pagedown);
        pagedownButtonImage.setScaleType(ImageView.ScaleType.CENTER);
        pagedownButtonImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_goDownButtonIcon), PorterDuff.Mode.MULTIPLY));
        pagedownButtonImage.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        Drawable drawable = Theme.createCircleDrawable(AndroidUtilities.dp(42), Theme.getColor(Theme.key_chat_goDownButton));
        Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.pagedown_shadow).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_goDownButtonShadow), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
        combinedDrawable.setIconSize(AndroidUtilities.dp(42), AndroidUtilities.dp(42));
        drawable = combinedDrawable;
        pagedownButtonImage.setBackgroundDrawable(drawable);

        pagedownButton.addView(pagedownButtonImage, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.BOTTOM));

        pagedownButtonCounter = new TextView(context);
        pagedownButtonCounter.setVisibility(View.INVISIBLE);
        pagedownButtonCounter.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        pagedownButtonCounter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pagedownButtonCounter.setTextColor(Theme.getColor(Theme.key_chat_goDownButtonCounter));
        pagedownButtonCounter.setGravity(Gravity.CENTER);
        pagedownButtonCounter.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(11.5f), Theme.getColor(Theme.key_chat_goDownButtonCounterBackground)));
        pagedownButtonCounter.setMinWidth(AndroidUtilities.dp(23));
        pagedownButtonCounter.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        pagedownButton.addView(pagedownButtonCounter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        mentiondownButtonImage = new ImageView(context);
        mentiondownButtonImage.setImageResource(R.drawable.mentionbutton);
        mentiondownButtonImage.setScaleType(ImageView.ScaleType.CENTER);
        mentiondownButtonImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_goDownButtonIcon), PorterDuff.Mode.MULTIPLY));
        mentiondownButtonImage.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        drawable = Theme.createCircleDrawable(AndroidUtilities.dp(42), Theme.getColor(Theme.key_chat_goDownButton));
        shadowDrawable = context.getResources().getDrawable(R.drawable.pagedown_shadow).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_goDownButtonShadow), PorterDuff.Mode.MULTIPLY));
        combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
        combinedDrawable.setIconSize(AndroidUtilities.dp(42), AndroidUtilities.dp(42));
        drawable = combinedDrawable;
        mentiondownButtonImage.setBackgroundDrawable(drawable);

        mentiondownButton.addView(mentiondownButtonImage, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.BOTTOM));

        mentiondownButtonCounter = new TextView(context);
        mentiondownButtonCounter.setVisibility(View.INVISIBLE);
        mentiondownButtonCounter.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        mentiondownButtonCounter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        mentiondownButtonCounter.setTextColor(Theme.getColor(Theme.key_chat_goDownButtonCounter));
        mentiondownButtonCounter.setGravity(Gravity.CENTER);
        mentiondownButtonCounter.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(11.5f), Theme.getColor(Theme.key_chat_goDownButtonCounterBackground)));
        mentiondownButtonCounter.setMinWidth(AndroidUtilities.dp(23));
        mentiondownButtonCounter.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        mentiondownButton.addView(mentiondownButtonCounter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        if (!AndroidUtilities.isTablet() || AndroidUtilities.isSmallTablet()) {
            contentView.addView(fragmentLocationContextView = new FragmentContextView(context, this, true), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
            contentView.addView(fragmentContextView = new FragmentContextView(context, this, false), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
            fragmentContextView.setAdditionalContextView(fragmentLocationContextView);
            fragmentLocationContextView.setAdditionalContextView(fragmentContextView);
        }

        contentView.addView(actionBar);

        overlayView = new View(context);
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    checkRecordLocked();
                }
                overlayView.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        });
        contentView.addView(overlayView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        overlayView.setVisibility(View.GONE);

        instantCameraView = new InstantCameraView(context, this);
        contentView.addView(instantCameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        chatActivityEnterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true);
        chatActivityEnterView.setDialogId(dialog_id);
        chatActivityEnterView.setId(id_chat_compose_panel);
        chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
        chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
        contentView.addView(chatActivityEnterView, contentView.getChildCount() - 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message) {
                moveScrollToLastMessage();
                showReplyPanel(false, null, null, null, false);
                if (mentionsAdapter != null) {
                    mentionsAdapter.addHashtagsFromMessage(message);
                }
            }

            @Override
            public void onSwitchRecordMode(boolean video) {
                showVoiceHint(false, video);
            }

            @Override
            public void onPreAudioVideoRecord() {
                showVoiceHint(true, false);
            }

            @Override
            public void onTextChanged(final CharSequence text, boolean bigChange) {
                if (startReplyOnTextChange && text.length() > 0) {
                    actionBar.getActionBarMenuOnItemClick().onItemClick(reply);
                    startReplyOnTextChange = false;
                }
                MediaController.getInstance().setInputFieldHasText(!TextUtils.isEmpty(text) || chatActivityEnterView.isEditingMessage());
                if (stickersAdapter != null && !chatActivityEnterView.isEditingMessage() && ChatObject.canSendStickers(currentChat)) {
                    stickersAdapter.loadStikersForEmoji(text);
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), chatActivityEnterView.getCursorPosition(), messages, false);
                }
                if (waitingForCharaterEnterRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(waitingForCharaterEnterRunnable);
                    waitingForCharaterEnterRunnable = null;
                }
                if (ChatObject.canSendEmbed(currentChat) && chatActivityEnterView.isMessageWebPageSearchEnabled() && (!chatActivityEnterView.isEditingMessage() || !chatActivityEnterView.isEditingCaption())) {
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
            public void onMessageEditEnd(boolean loading) {
                if (!loading) {
                    mentionsAdapter.setNeedBotContext(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
                    chatListView.setOnItemLongClickListener(onItemLongClickListener);
                    chatListView.setOnItemClickListener(onItemClickListener);
                    chatListView.setClickable(true);
                    chatListView.setLongClickable(true);
                    mentionsAdapter.setAllowNewMentions(true);
                    actionModeTitleContainer.setVisibility(View.GONE);
                    selectedMessagesCountTextView.setVisibility(View.VISIBLE);
                    chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
                    if (editingMessageObjectReqId != 0) {
                        ConnectionsManager.getInstance().cancelRequest(editingMessageObjectReqId, true);
                        editingMessageObjectReqId = 0;
                    }
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                    updateBottomOverlay();
                    updateVisibleRows();
                }
            }

            @Override
            public void onWindowSizeChanged(int size) {
                if (size < AndroidUtilities.dp(72) + ActionBar.getCurrentActionBarHeight()) {
                    allowStickersPanel = false;
                    if (stickersPanel.getVisibility() == View.VISIBLE) {
                        stickersPanel.setVisibility(View.INVISIBLE);
                    }
                    if (mentionContainer != null && mentionContainer.getVisibility() == View.VISIBLE) {
                        mentionContainer.setVisibility(View.INVISIBLE);
                    }
                } else {
                    allowStickersPanel = true;
                    if (stickersPanel.getVisibility() == View.INVISIBLE) {
                        stickersPanel.setVisibility(View.VISIBLE);
                    }
                    if (mentionContainer != null && mentionContainer.getVisibility() == View.INVISIBLE && (!mentionsAdapter.isBotContext() || (allowContextBotPanel || allowContextBotPanelSecond))) {
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

            @Override
            public void didPressedAttachButton() {
                openAttachMenu();
            }

            @Override
            public void needStartRecordVideo(int state) {
                if (instantCameraView != null) {
                    if (state == 0) {
                        instantCameraView.showCamera();
                    } else if (state == 1 || state == 3 || state == 4) {
                        instantCameraView.send(state);
                    } else if (state == 2) {
                        instantCameraView.cancel();
                    }
                }
            }

            @Override
            public void needChangeVideoPreviewState(int state, float seekProgress) {
                if (instantCameraView != null) {
                    instantCameraView.changeVideoPreviewState(state, seekProgress);
                }
            }

            @Override
            public void needStartRecordAudio(int state) {
                overlayView.setVisibility(state == 0 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void needShowMediaBanHint() {
                showMediaBannedHint();
            }
        });

        FrameLayout replyLayout = new FrameLayout(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.invalidate();
                }
                if (getVisibility() != GONE) {
                    int height = getLayoutParams().height;
                    if (chatListView != null) {
                        chatListView.setTranslationY(translationY);
                    }
                    if (progressView != null) {
                        progressView.setTranslationY(translationY);
                    }
                    if (mentionContainer != null) {
                        mentionContainer.setTranslationY(translationY);
                    }
                    if (pagedownButton != null) {
                        pagedownButton.setTranslationY(translationY);
                    }
                    if (mentiondownButton != null) {
                        mentiondownButton.setTranslationY(pagedownButton.getVisibility() != VISIBLE ? translationY : translationY - AndroidUtilities.dp(72));
                    }
                }
            }

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility == GONE) {
                    if (chatListView != null) {
                        chatListView.setTranslationY(0);
                    }
                    if (progressView != null) {
                        progressView.setTranslationY(0);
                    }
                    if (mentionContainer != null) {
                        mentionContainer.setTranslationY(0);
                    }
                    if (pagedownButton != null) {
                        pagedownButton.setTranslationY(pagedownButton.getTag() == null ? AndroidUtilities.dp(100) : 0);
                    }
                    if (mentiondownButton != null) {
                        mentiondownButton.setTranslationY(mentiondownButton.getTag() == null ? AndroidUtilities.dp(100) : (pagedownButton.getVisibility() == VISIBLE ? -AndroidUtilities.dp(72) : 0));
                    }
                }
            }
        };
        chatActivityEnterView.addTopView(replyLayout, 48);
        replyLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (replyingMessageObject != null) {
                    scrollToMessageId(replyingMessageObject.getId(), 0, true, 0, false);
                }
            }
        });

        replyLineView = new View(context);
        replyLineView.setBackgroundColor(Theme.getColor(Theme.key_chat_replyPanelLine));
        replyLayout.addView(replyLineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT));

        replyIconImageView = new ImageView(context);
        replyIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons), PorterDuff.Mode.MULTIPLY));
        replyIconImageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(replyIconImageView, LayoutHelper.createFrame(52, 46, Gravity.TOP | Gravity.LEFT));

        replyCloseImageView = new ImageView(context);
        replyCloseImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelClose), PorterDuff.Mode.MULTIPLY));
        replyCloseImageView.setImageResource(R.drawable.msg_panel_clear);
        replyCloseImageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(replyCloseImageView, LayoutHelper.createFrame(52, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 0, 0));
        replyCloseImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (forwardingMessages != null) {
                    forwardingMessages.clear();
                }
                showReplyPanel(false, null, null, foundWebPage, true);
            }
        });

        replyNameTextView = new SimpleTextView(context);
        replyNameTextView.setTextSize(14);
        replyNameTextView.setTextColor(Theme.getColor(Theme.key_chat_replyPanelName));
        replyNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        replyLayout.addView(replyNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 6, 52, 0));

        replyObjectTextView = new SimpleTextView(context);
        replyObjectTextView.setTextSize(14);
        replyObjectTextView.setTextColor(Theme.getColor(Theme.key_chat_replyPanelMessage));
        replyLayout.addView(replyObjectTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 24, 52, 0));

        replyImageView = new BackupImageView(context);
        replyLayout.addView(replyImageView, LayoutHelper.createFrame(34, 34, Gravity.TOP | Gravity.LEFT, 52, 6, 0, 0));

        stickersPanel = new FrameLayout(context);
        stickersPanel.setVisibility(View.GONE);
        contentView.addView(stickersPanel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 81.5f, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 38));

        stickersListView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersListView, 0, null);
                return super.onInterceptTouchEvent(event) || result;
            }
        };
        stickersListView.setTag(3);
        stickersListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return StickerPreviewViewer.getInstance().onTouch(event, stickersListView, 0, stickersOnItemClickListener, null);
            }
        });
        stickersListView.setDisallowInterceptTouchEvents(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        stickersListView.setLayoutManager(layoutManager);
        stickersListView.setClipToPadding(false);
        stickersListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        stickersPanel.addView(stickersListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78));
        initStickers();

        stickersPanelArrow = new ImageView(context);
        stickersPanelArrow.setImageResource(R.drawable.stickers_back_arrow);
        stickersPanelArrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_stickersHintPanel), PorterDuff.Mode.MULTIPLY));
        stickersPanel.addView(stickersPanelArrow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 53, 0, 0, 0));

        searchContainer = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        searchContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        searchContainer.setWillNotDraw(false);
        searchContainer.setVisibility(View.INVISIBLE);
        searchContainer.setFocusable(true);
        searchContainer.setFocusableInTouchMode(true);
        searchContainer.setClickable(true);
        searchContainer.setPadding(0, AndroidUtilities.dp(3), 0, 0);

        searchUpButton = new ImageView(context);
        searchUpButton.setScaleType(ImageView.ScaleType.CENTER);
        searchUpButton.setImageResource(R.drawable.search_up);
        searchUpButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchUpButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 48, 0));
        searchUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MessagesSearchQuery.searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 1, searchingUserMessages);
            }
        });

        searchDownButton = new ImageView(context);
        searchDownButton.setScaleType(ImageView.ScaleType.CENTER);
        searchDownButton.setImageResource(R.drawable.search_down);
        searchDownButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchDownButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 0, 0));
        searchDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MessagesSearchQuery.searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 2, searchingUserMessages);
            }
        });

        if (currentChat != null && (!ChatObject.isChannel(currentChat) || currentChat.megagroup)) {
            searchUserButton = new ImageView(context);
            searchUserButton.setScaleType(ImageView.ScaleType.CENTER);
            searchUserButton.setImageResource(R.drawable.usersearch);
            searchUserButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
            searchContainer.addView(searchUserButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 48, 0, 0, 0));
            searchUserButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mentionLayoutManager.setReverseLayout(true);
                    mentionsAdapter.setSearchingMentions(true);
                    searchCalendarButton.setVisibility(View.GONE);
                    searchUserButton.setVisibility(View.GONE);
                    searchingForUser = true;
                    searchingUserMessages = null;
                    searchItem.getSearchField().setHint(LocaleController.getString("SearchMembers", R.string.SearchMembers));
                    searchItem.setSearchFieldCaption(LocaleController.getString("SearchFrom", R.string.SearchFrom));
                    AndroidUtilities.showKeyboard(searchItem.getSearchField());
                    searchItem.clearSearchText();
                }
            });
        }

        searchCalendarButton = new ImageView(context);
        searchCalendarButton.setScaleType(ImageView.ScaleType.CENTER);
        searchCalendarButton.setImageResource(R.drawable.search_calendar);
        searchCalendarButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchCalendarButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        searchCalendarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getParentActivity() == null) {
                    return;
                }
                AndroidUtilities.hideKeyboard(searchItem.getSearchField());
                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR);
                int monthOfYear = calendar.get(Calendar.MONTH);
                int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
                try {
                    DatePickerDialog dialog = new DatePickerDialog(getParentActivity(), new DatePickerDialog.OnDateSetListener() {
                        @Override
                        public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.clear();
                            calendar.set(year, month, dayOfMonth);
                            int date = (int) (calendar.getTime().getTime() / 1000);
                            clearChatData();
                            waitingForLoad.add(lastLoadIndex);
                            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, date, true, 0, classGuid, 4, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
                        }
                    }, year, monthOfYear, dayOfMonth);
                    final DatePicker datePicker = dialog.getDatePicker();
                    datePicker.setMinDate(1375315200000L);
                    datePicker.setMaxDate(System.currentTimeMillis());
                    dialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("JumpToDate", R.string.JumpToDate), dialog);
                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    if (Build.VERSION.SDK_INT >= 21) {
                        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(DialogInterface dialog) {
                                int count = datePicker.getChildCount();
                                for (int a = 0; a < count; a++) {
                                    View child = datePicker.getChildAt(a);
                                    ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                                    layoutParams.width = LayoutHelper.MATCH_PARENT;
                                    child.setLayoutParams(layoutParams);
                                }
                            }
                        });
                    }
                    showDialog(dialog);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });

        searchCountText = new SimpleTextView(context);
        searchCountText.setTextColor(Theme.getColor(Theme.key_chat_searchPanelText));
        searchCountText.setTextSize(15);
        searchCountText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        searchCountText.setGravity(Gravity.RIGHT);
        searchContainer.addView(searchCountText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 108, 0));

        bottomOverlay = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        bottomOverlay.setWillNotDraw(false);
        bottomOverlay.setVisibility(View.INVISIBLE);
        bottomOverlay.setFocusable(true);
        bottomOverlay.setFocusableInTouchMode(true);
        bottomOverlay.setClickable(true);
        bottomOverlay.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        contentView.addView(bottomOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        bottomOverlayText = new TextView(context);
        bottomOverlayText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomOverlayText.setGravity(Gravity.CENTER);
        bottomOverlayText.setMaxLines(2);
        bottomOverlayText.setEllipsize(TextUtils.TruncateAt.END);
        bottomOverlayText.setLineSpacing(AndroidUtilities.dp(2), 1);
        bottomOverlayText.setTextColor(Theme.getColor(Theme.key_chat_secretChatStatusText));
        bottomOverlay.addView(bottomOverlayText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 14, 0, 14, 0));

        bottomOverlayChat = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        bottomOverlayChat.setWillNotDraw(false);
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
                            SendMessagesHelper.getInstance().sendMessage("/start", dialog_id, null, null, false, null, null, null);
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
                        SendMessagesHelper.getInstance().sendMessage("/start", dialog_id, null, null, false, null, null, null);
                    }
                    botUser = null;
                    updateBottomOverlay();
                } else {
                    if (ChatObject.isChannel(currentChat) && !(currentChat instanceof TLRPC.TL_channelForbidden)) {
                        if (ChatObject.isNotInChat(currentChat)) {
                            MessagesController.getInstance().addUserToChat(currentChat.id, UserConfig.getCurrentUser(), null, 0, null, ChatActivity.this);
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
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        contentView.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        chatAdapter.updateRows();
        if (loading && messages.isEmpty()) {
            progressView.setVisibility(chatAdapter.botInfoRow == -1 ? View.VISIBLE : View.INVISIBLE);
            chatListView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.INVISIBLE);
            chatListView.setEmptyView(emptyViewContainer);
        }

        chatActivityEnterView.setButtons(userBlocked ? null : botButtons);

        updateContactStatus();
        updateBottomOverlay();
        updateSecretStatus();
        updateSpamView();
        updatePinnedMessageView(true);

        try {
            if (currentEncryptedChat != null && Build.VERSION.SDK_INT >= 23 && (UserConfig.passcodeHash.length() == 0 || UserConfig.allowScreenCapture)) {
                getParentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (oldMessage != null) {
            chatActivityEnterView.setFieldText(oldMessage);
        }
        fixLayoutInternal();

        return fragmentView;
    }

    private TextureView createTextureView(boolean add) {
        if (parentLayout == null) {
            return null;
        }
        if (roundVideoContainer == null) {
            if (Build.VERSION.SDK_INT >= 21) {
                roundVideoContainer = new FrameLayout(getParentActivity()) {
                    @Override
                    public void setTranslationY(float translationY) {
                        super.setTranslationY(translationY);
                        contentView.invalidate();
                    }
                };
                roundVideoContainer.setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
                    }
                });
                roundVideoContainer.setClipToOutline(true);
            } else {
                roundVideoContainer = new FrameLayout(getParentActivity()) {
                    @Override
                    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                        super.onSizeChanged(w, h, oldw, oldh);
                        aspectPath.reset();
                        aspectPath.addCircle(w / 2, h / 2, w / 2, Path.Direction.CW);
                        aspectPath.toggleInverseFillType();
                    }

                    @Override
                    public void setTranslationY(float translationY) {
                        super.setTranslationY(translationY);
                        contentView.invalidate();
                    }

                    @Override
                    public void setVisibility(int visibility) {
                        super.setVisibility(visibility);
                        if (visibility == VISIBLE) {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        }
                    }

                    @Override
                    protected void dispatchDraw(Canvas canvas) {
                        super.dispatchDraw(canvas);
                        canvas.drawPath(aspectPath, aspectPaint);
                    }
                };
                aspectPath = new Path();
                aspectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                aspectPaint.setColor(0xff000000);
                aspectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            }
            roundVideoContainer.setWillNotDraw(false);
            roundVideoContainer.setVisibility(View.INVISIBLE);

            aspectRatioFrameLayout = new AspectRatioFrameLayout(getParentActivity());
            aspectRatioFrameLayout.setBackgroundColor(0);
            if (add) {
                roundVideoContainer.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }

            videoTextureView = new TextureView(getParentActivity());
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        if (roundVideoContainer.getParent() == null) {
            contentView.addView(roundVideoContainer, 1, new FrameLayout.LayoutParams(AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize));
        }
        roundVideoContainer.setVisibility(View.INVISIBLE);
        aspectRatioFrameLayout.setDrawingReady(false);
        return videoTextureView;
    }

    private void destroyTextureView() {
        if (roundVideoContainer == null || roundVideoContainer.getParent() == null) {
            return;
        }
        contentView.removeView(roundVideoContainer);
        aspectRatioFrameLayout.setDrawingReady(false);
        roundVideoContainer.setVisibility(View.INVISIBLE);
        if (Build.VERSION.SDK_INT < 21) {
            roundVideoContainer.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void sendBotInlineResult(TLRPC.BotInlineResult result) {
        int uid = mentionsAdapter.getContextBotId();
        HashMap<String, String> params = new HashMap<>();
        params.put("id", result.id);
        params.put("query_id", "" + result.query_id);
        params.put("bot", "" + uid);
        params.put("bot_name", mentionsAdapter.getContextBotName());
        SendMessagesHelper.prepareSendingBotContextResult(result, params, dialog_id, replyingMessageObject);
        chatActivityEnterView.setFieldText("");
        showReplyPanel(false, null, null, null, false);
        SearchQuery.increaseInlineRaiting(uid);
    }

    private void mentionListViewUpdateLayout() {
        if (mentionListView.getChildCount() <= 0) {
            mentionListViewScrollOffsetY = 0;
            mentionListViewLastViewPosition = -1;
            return;
        }
        View child = mentionListView.getChildAt(mentionListView.getChildCount() - 1);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) mentionListView.findContainingViewHolder(child);
        if (mentionLayoutManager.getReverseLayout()) {
            if (holder != null) {
                mentionListViewLastViewPosition = holder.getAdapterPosition();
                mentionListViewLastViewTop = child.getBottom();
            } else {
                mentionListViewLastViewPosition = -1;
            }

            child = mentionListView.getChildAt(0);
            holder = (RecyclerListView.Holder) mentionListView.findContainingViewHolder(child);
            int newOffset = child.getBottom() < mentionListView.getMeasuredHeight() && holder != null && holder.getAdapterPosition() == 0 ? child.getBottom() : mentionListView.getMeasuredHeight();
            if (mentionListViewScrollOffsetY != newOffset) {
                mentionListView.setBottomGlowOffset(mentionListViewScrollOffsetY = newOffset);
                mentionListView.setTopGlowOffset(0);
                mentionListView.invalidate();
                mentionContainer.invalidate();
            }
        } else {
            if (holder != null) {
                mentionListViewLastViewPosition = holder.getAdapterPosition();
                mentionListViewLastViewTop = child.getTop();
            } else {
                mentionListViewLastViewPosition = -1;
            }

            child = mentionListView.getChildAt(0);
            holder = (RecyclerListView.Holder) mentionListView.findContainingViewHolder(child);
            int newOffset = child.getTop() > 0 && holder != null && holder.getAdapterPosition() == 0 ? child.getTop() : 0;
            if (mentionListViewScrollOffsetY != newOffset) {
                mentionListView.setTopGlowOffset(mentionListViewScrollOffsetY = newOffset);
                mentionListView.setBottomGlowOffset(0);
                mentionListView.invalidate();
                mentionContainer.invalidate();
            }
        }
    }

    private void checkBotCommands() {
        URLSpanBotCommand.enabled = false;
        if (currentUser != null && currentUser.bot) {
            URLSpanBotCommand.enabled = true;
        } else if (info instanceof TLRPC.TL_chatFull) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
                if (user != null && user.bot) {
                    URLSpanBotCommand.enabled = true;
                    break;
                }
            }
        } else if (info instanceof TLRPC.TL_channelFull) {
            URLSpanBotCommand.enabled = !info.bot_info.isEmpty() && currentChat != null && currentChat.megagroup;
        }
    }

    private MessageObject.GroupedMessages getValidGroupedMessage(MessageObject message) {
        MessageObject.GroupedMessages groupedMessages = null;
        if (message.getGroupId() != 0) {
            groupedMessages = groupedMessagesMap.get(message.getGroupId());
            if (groupedMessages != null && (groupedMessages.messages.size() <= 1 || groupedMessages.positions.get(message) == null)) {
                groupedMessages = null;
            }
        }
        return groupedMessages;
    }

    private void jumpToDate(int date) {
        if (messages.isEmpty()) {
            return;
        }
        MessageObject firstMessage = messages.get(0);
        MessageObject lastMessage = messages.get(messages.size() - 1);
        if (firstMessage.messageOwner.date >= date && lastMessage.messageOwner.date <= date) {
            for (int a = messages.size() - 1; a >= 0; a--) {
                MessageObject message = messages.get(a);
                if (message.messageOwner.date >= date && message.getId() != 0) {
                    scrollToMessageId(message.getId(), 0, false, message.getDialogId() == mergeDialogId ? 1 : 0, false);
                    break;
                }
            }
        } else if ((int) dialog_id != 0) {
            clearChatData();
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, date, true, 0, classGuid, 4, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
            floatingDateView.setAlpha(0.0f);
            floatingDateView.setTag(null);
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
        if (getParentActivity() == null) {
            return;
        }
        if (chatAttachAlert == null) {
            chatAttachAlert = new ChatAttachAlert(getParentActivity(), this);
            chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
                @Override
                public void didPressedButton(int button) {
                    if (getParentActivity() == null || chatAttachAlert == null) {
                        return;
                    }
                    if (button == 7 || button == 4 && !chatAttachAlert.getSelectedPhotos().isEmpty()) {
                        chatAttachAlert.dismiss();
                        HashMap<Object, Object> selectedPhotos = chatAttachAlert.getSelectedPhotos();
                        ArrayList<Object> selectedPhotosOrder = chatAttachAlert.getSelectedPhotosOrder();
                        if (!selectedPhotos.isEmpty()) {
                            ArrayList<SendMessagesHelper.SendingMediaInfo> photos = new ArrayList<>();
                            for (int a = 0; a < selectedPhotosOrder.size(); a++) {
                                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) selectedPhotos.get(selectedPhotosOrder.get(a));

                                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                if (photoEntry.imagePath != null) {
                                    info.path = photoEntry.imagePath;
                                } else if (photoEntry.path != null) {
                                    info.path = photoEntry.path;
                                }
                                info.isVideo = photoEntry.isVideo;
                                info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                                info.masks = !photoEntry.stickers.isEmpty() ? new ArrayList<>(photoEntry.stickers) : null;
                                info.ttl = photoEntry.ttl;
                                info.videoEditedInfo = photoEntry.editedInfo;
                                photos.add(info);
                                photoEntry.reset();
                            }
                            SendMessagesHelper.prepareSendingMedia(photos, dialog_id, replyingMessageObject, null, button == 4, MediaController.getInstance().isGroupPhotosEnabled());
                            showReplyPanel(false, null, null, null, false);
                            DraftQuery.cleanDraft(dialog_id, true);
                        }
                        return;
                    } else if (chatAttachAlert != null) {
                        chatAttachAlert.dismissWithButtonClick(button);
                    }
                    processSelectedAttach(button);
                }

                @Override
                public View getRevealView() {
                    return chatActivityEnterView.getAttachButton();
                }

                @Override
                public void didSelectBot(TLRPC.User user) {
                    if (chatActivityEnterView == null || TextUtils.isEmpty(user.username)) {
                        return;
                    }
                    chatActivityEnterView.setFieldText("@" + user.username + " ");
                    chatActivityEnterView.openKeyboard();
                }

                @Override
                public void onCameraOpened() {
                    chatActivityEnterView.closeKeyboard();
                }
            });
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
        if (chatActivityEnterView != null && chatActivityEnterView.isRecordingAudioVideo()) {
            return true;
        }
        for (int a = messages.size() - 1; a >= 0; a--) {
            MessageObject messageObject = messages.get(a);
            if ((messageObject.isVoice() || messageObject.isRoundVideo()) && messageObject.isContentUnread() && !messageObject.isOut()) {
                MediaController.getInstance().setVoiceMessagesPlaylist(MediaController.getInstance().playMessage(messageObject) ? createVoiceMessagesPlaylist(messageObject, true) : null, true);
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
                    stickersPanel.setVisibility(allowStickersPanel ? View.VISIBLE : View.INVISIBLE);
                }
                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (stickersPanel.getVisibility() != View.INVISIBLE) {
                    runningAnimation = new AnimatorSet();
                    runningAnimation.playTogether(
                            ObjectAnimator.ofFloat(stickersPanel, "alpha", show ? 0.0f : 1.0f, show ? 1.0f : 0.0f)
                    );
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                if (!show) {
                                    stickersAdapter.clearStickers();
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
                        public void onAnimationCancel(Animator animation) {
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
                    SendMessagesHelper.getInstance().sendSticker(document, dialog_id, replyingMessageObject);
                    showReplyPanel(false, null, null, null, false);
                    chatActivityEnterView.addStickerToRecent(document);
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
                SendMessagesHelper.getInstance().sendMessage(UserConfig.getCurrentUser(), dialog_id, messageObject, null, null);
                moveScrollToLastMessage();
                showReplyPanel(false, null, null, null, false);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void hideVoiceHint() {
        voiceHintAnimation = new AnimatorSet();
        voiceHintAnimation.playTogether(
                ObjectAnimator.ofFloat(voiceHintTextView, "alpha", 0.0f)
        );
        voiceHintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(voiceHintAnimation)) {
                    voiceHintAnimation = null;
                    voiceHintHideRunnable = null;
                    if (voiceHintTextView != null) {
                        voiceHintTextView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(voiceHintAnimation)) {
                    voiceHintHideRunnable = null;
                    voiceHintHideRunnable = null;
                }
            }
        });
        voiceHintAnimation.setDuration(300);
        voiceHintAnimation.start();
    }

    private void showVoiceHint(boolean hide, boolean video) {
        if (getParentActivity() == null || fragmentView == null || hide && voiceHintTextView == null) {
            return;
        }
        if (voiceHintTextView == null) {
            SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
            int index = frameLayout.indexOfChild(chatActivityEnterView);
            if (index == -1) {
                return;
            }
            voiceHintTextView = new TextView(getParentActivity());
            voiceHintTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
            voiceHintTextView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
            voiceHintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            voiceHintTextView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
            voiceHintTextView.setGravity(Gravity.CENTER_VERTICAL);
            voiceHintTextView.setAlpha(0.0f);
            frameLayout.addView(voiceHintTextView, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 5, 0, 5, 3));
        }
        if (hide) {
            if (voiceHintAnimation != null) {
                voiceHintAnimation.cancel();
                voiceHintAnimation = null;
            }
            AndroidUtilities.cancelRunOnUIThread(voiceHintHideRunnable);
            voiceHintHideRunnable = null;
            hideVoiceHint();
            return;
        }

        voiceHintTextView.setText(video ? LocaleController.getString("HoldToVideo", R.string.HoldToVideo) : LocaleController.getString("HoldToAudio", R.string.HoldToAudio));

        if (voiceHintHideRunnable != null) {
            if (voiceHintAnimation != null) {
                voiceHintAnimation.cancel();
                voiceHintAnimation = null;
            } else {
                AndroidUtilities.cancelRunOnUIThread(voiceHintHideRunnable);
                AndroidUtilities.runOnUIThread(voiceHintHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        hideVoiceHint();
                    }
                }, 2000);
                return;
            }
        } else if (voiceHintAnimation != null) {
            return;
        }

        voiceHintTextView.setVisibility(View.VISIBLE);
        voiceHintAnimation = new AnimatorSet();
        voiceHintAnimation.playTogether(
                ObjectAnimator.ofFloat(voiceHintTextView, "alpha", 1.0f)
        );
        voiceHintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(voiceHintAnimation)) {
                    voiceHintAnimation = null;
                    AndroidUtilities.runOnUIThread(voiceHintHideRunnable = new Runnable() {
                        @Override
                        public void run() {
                            hideVoiceHint();
                        }
                    }, 2000);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(voiceHintAnimation)) {
                    voiceHintAnimation = null;
                }
            }
        });
        voiceHintAnimation.setDuration(300);
        voiceHintAnimation.start();
    }

    private void showMediaBannedHint() {
        if (getParentActivity() == null || currentChat == null || currentChat.banned_rights == null || fragmentView == null || mediaBanTooltip != null && mediaBanTooltip.getVisibility() == View.VISIBLE) {
            return;
        }
        SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
        int index = frameLayout.indexOfChild(chatActivityEnterView);
        if (index == -1) {
            return;
        }

        if (mediaBanTooltip == null) {
            mediaBanTooltip = new CorrectlyMeasuringTextView(getParentActivity());
            mediaBanTooltip.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
            mediaBanTooltip.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
            mediaBanTooltip.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
            mediaBanTooltip.setGravity(Gravity.CENTER_VERTICAL);
            mediaBanTooltip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            frameLayout.addView(mediaBanTooltip, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 30, 0, 5, 3));
        }

        if (AndroidUtilities.isBannedForever(currentChat.banned_rights.until_date)) {
            mediaBanTooltip.setText(LocaleController.getString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
        } else {
            mediaBanTooltip.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(currentChat.banned_rights.until_date)));
        }
        mediaBanTooltip.setVisibility(View.VISIBLE);
        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mediaBanTooltip, "alpha", 0.0f, 1.0f)
        );
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaBanTooltip == null) {
                            return;
                        }
                        AnimatorSet AnimatorSet = new AnimatorSet();
                        AnimatorSet.playTogether(
                                ObjectAnimator.ofFloat(mediaBanTooltip, "alpha", 0.0f)
                        );
                        AnimatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (mediaBanTooltip != null) {
                                    mediaBanTooltip.setVisibility(View.GONE);
                                }
                            }
                        });
                        AnimatorSet.setDuration(300);
                        AnimatorSet.start();
                    }
                }, 5000);
            }
        });
        AnimatorSet.setDuration(300);
        AnimatorSet.start();
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
        gifHintTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
        gifHintTextView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        gifHintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        gifHintTextView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
        gifHintTextView.setText(LocaleController.getString("TapHereGifs", R.string.TapHereGifs));
        gifHintTextView.setGravity(Gravity.CENTER_VERTICAL);
        frameLayout.addView(gifHintTextView, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 5, 0, 5, 3));

        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(
                ObjectAnimator.ofFloat(gifHintTextView, "alpha", 0.0f, 1.0f),
                ObjectAnimator.ofFloat(emojiButtonRed, "alpha", 0.0f, 1.0f)
        );
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gifHintTextView == null) {
                            return;
                        }
                        AnimatorSet AnimatorSet = new AnimatorSet();
                        AnimatorSet.playTogether(
                                ObjectAnimator.ofFloat(gifHintTextView, "alpha", 0.0f)
                        );
                        AnimatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (gifHintTextView != null) {
                                    gifHintTextView.setVisibility(View.GONE);
                                }
                            }
                        });
                        AnimatorSet.setDuration(300);
                        AnimatorSet.start();
                    }
                }, 2000);
            }
        });
        AnimatorSet.setDuration(300);
        AnimatorSet.start();
    }

    private void openAttachMenu() {
        if (getParentActivity() == null) {
            return;
        }
        createChatAttachView();
        chatAttachAlert.loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            chatActivityEnterView.closeKeyboard();
        }
        chatAttachAlert.init();
        showDialog(chatAttachAlert);
    }

    private void checkContextBotPanel() {
        if (allowStickersPanel && mentionsAdapter != null && mentionsAdapter.isBotContext()) {
            if (!allowContextBotPanel && !allowContextBotPanelSecond) {
                if (mentionContainer.getVisibility() == View.VISIBLE && mentionContainer.getTag() == null) {
                    if (mentionListAnimation != null) {
                        mentionListAnimation.cancel();
                    }

                    mentionContainer.setTag(1);
                    mentionListAnimation = new AnimatorSet();
                    mentionListAnimation.playTogether(
                            ObjectAnimator.ofFloat(mentionContainer, "alpha", 0.0f)
                    );
                    mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionContainer.setVisibility(View.INVISIBLE);
                                mentionListAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
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
                    mentionListAnimation = new AnimatorSet();
                    mentionListAnimation.playTogether(
                            ObjectAnimator.ofFloat(mentionContainer, "alpha", 0.0f, 1.0f)
                    );
                    mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionListAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
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

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView.getTag() != null && !currentFloatingDateOnScreen && (!scrollingFloatingDate || currentFloatingTopIsNotMessage)) {
            floatingDateView.setTag(null);
            if (animated) {
                floatingDateAnimation = new AnimatorSet();
                floatingDateAnimation.setDuration(150);
                floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 0.0f));
                floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(floatingDateAnimation)) {
                            floatingDateAnimation = null;
                        }
                    }
                });
                floatingDateAnimation.setStartDelay(500);
                floatingDateAnimation.start();
            } else {
                if (floatingDateAnimation != null) {
                    floatingDateAnimation.cancel();
                    floatingDateAnimation = null;
                }
                floatingDateView.setAlpha(0.0f);
            }
        }
    }

    @Override
    protected void onRemoveFromParent() {
        MediaController.getInstance().setTextureView(videoTextureView, null, null, false);
    }

    protected void setIgnoreAttachOnPause(boolean value) {
        ignoreAttachOnPause = value;
    }

    private void checkScrollForLoad(boolean scroll) {
        if (chatLayoutManager == null || paused) {
            return;
        }
        int firstVisibleItem = chatLayoutManager.findFirstVisibleItemPosition();
        int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(chatLayoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        if (visibleItemCount > 0 || currentEncryptedChat != null) {
            int totalItemCount = chatAdapter.getItemCount();
            int checkLoadCount;
            if (scroll) {
                checkLoadCount = 25;
            } else  {
                checkLoadCount = 5;
            }
            if (totalItemCount - firstVisibleItem - visibleItemCount <= checkLoadCount && !loading) {
                if (!endReached[0]) {
                    loading = true;
                    waitingForLoad.add(lastLoadIndex);
                    if (messagesByDays.size() != 0) {
                        MessagesController.getInstance().loadMessages(dialog_id, 50, maxMessageId[0], 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
                    } else {
                        MessagesController.getInstance().loadMessages(dialog_id, 50, 0, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
                    }
                } else if (mergeDialogId != 0 && !endReached[1]) {
                    loading = true;
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(mergeDialogId, 50, maxMessageId[1], 0, !cacheEndReached[1], minDate[1], classGuid, 0, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
                }
            }
            if (visibleItemCount > 0 && !loadingForward && firstVisibleItem <= 10) {
                if (mergeDialogId != 0 && !forwardEndReached[1]) {
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(mergeDialogId, 50, minMessageId[1], 0, true, maxDate[1], classGuid, 1, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
                    loadingForward = true;
                } else if (!forwardEndReached[0]) {
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(dialog_id, 50, minMessageId[0], 0, true, maxDate[0], classGuid, 1, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
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
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 19);
                return;
            }
            try {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File image = AndroidUtilities.generatePicturePath();
                if (image != null) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", image));
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                    }
                    currentPicturePath = image.getAbsolutePath();
                }
                startActivityForResult(takePictureIntent, 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (which == attach_gallery) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
            PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(false, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46, true, ChatActivity.this);
            fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
                @Override
                public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos) {
                    SendMessagesHelper.prepareSendingMedia(photos, dialog_id, replyingMessageObject, null, false, MediaController.getInstance().isGroupPhotosEnabled());
                    showReplyPanel(false, null, null, null, false);
                    DraftQuery.cleanDraft(dialog_id, true);
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
                        FileLog.e(e);
                    }
                }
            });
            presentFragment(fragment);
        } else if (which == attach_video) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 20);
                return;
            }
            try {
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                File video = AndroidUtilities.generateVideoPath();
                if (video != null) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", video));
                        takeVideoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        takeVideoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else if (Build.VERSION.SDK_INT >= 18) {
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                    }
                    takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1536));
                    currentPicturePath = video.getAbsolutePath();
                }
                startActivityForResult(takeVideoIntent, 2);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (which == attach_location) {
            if (!AndroidUtilities.isGoogleMapsInstalled(ChatActivity.this)) {
                return;
            }
            LocationActivity fragment = new LocationActivity(currentEncryptedChat == null ? 1 : 0);
            fragment.setDialogId(dialog_id);
            fragment.setDelegate(this);
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
                    SendMessagesHelper.prepareSendingDocuments(files, files, null, null, dialog_id, replyingMessageObject, null);
                    showReplyPanel(false, null, null, null, false);
                    DraftQuery.cleanDraft(dialog_id, true);
                }

                @Override
                public void startDocumentSelectActivity() {
                    try {
                        Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        if (Build.VERSION.SDK_INT >= 18) {
                            photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        }
                        photoPickerIntent.setType("*/*");
                        startActivityForResult(photoPickerIntent, 21);
                    } catch (Exception e) {
                        FileLog.e(e);
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
                    SendMessagesHelper.prepareSendingAudioDocuments(audios, dialog_id, replyingMessageObject);
                    showReplyPanel(false, null, null, null, false);
                    DraftQuery.cleanDraft(dialog_id, true);
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
                FileLog.e(e);
            }
        }
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return dialog != chatAttachAlert && super.dismissDialogOnPause(dialog);
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
            showReplyPanel(false, null, null, foundWebPage, false);
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
                                    showReplyPanel(false, null, null, foundWebPage, false);
                                    foundWebPage = null;
                                }
                            }
                        });
                        return;
                    }
                    textToCheck = TextUtils.join(" ", urls);
                } catch (Exception e) {
                    FileLog.e(e);
                    String text = charSequence.toString().toLowerCase();
                    if (charSequence.length() < 13 || !text.contains("http://") && !text.contains("https://")) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (foundWebPage != null) {
                                    showReplyPanel(false, null, null, foundWebPage, false);
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
                                            showReplyPanel(true, null, null, foundWebPage, false);
                                        } else {
                                            if (foundWebPage != null) {
                                                showReplyPanel(false, null, null, foundWebPage, false);
                                                foundWebPage = null;
                                            }
                                        }
                                    } else {
                                        if (foundWebPage != null) {
                                            showReplyPanel(false, null, null, foundWebPage, false);
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
            AlertsCreator.showSendMediaAlert(SendMessagesHelper.getInstance().sendMessage(arrayList, dialog_id), this);
        } else {
            for (MessageObject object : arrayList) {
                SendMessagesHelper.getInstance().processForwardFromMyName(object, dialog_id);
            }
        }
    }

    public void showReplyPanel(boolean show, MessageObject messageObjectToReply, ArrayList<MessageObject> messageObjectsToForward, TLRPC.WebPage webPage, boolean cancel) {
        if (chatActivityEnterView == null) {
            return;
        }
        if (show) {
            if (messageObjectToReply == null && messageObjectsToForward == null && webPage == null) {
                return;
            }
            if (searchItem != null && actionBar.isSearchFieldVisible()) {
                actionBar.closeSearchField(false);
                chatActivityEnterView.setFieldFocused();
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (chatActivityEnterView != null) {
                            chatActivityEnterView.openKeyboard();
                        }
                    }
                }, 100);
            }
            boolean openKeyboard = false;
            if (messageObjectToReply != null && messageObjectToReply.getDialogId() != dialog_id) {
                messageObjectsToForward = new ArrayList<>();
                messageObjectsToForward.add(messageObjectToReply);
                messageObjectToReply = null;
                openKeyboard = true;
            }
            if (messageObjectToReply != null) {
                forwardingMessages = null;
                replyingMessageObject = messageObjectToReply;
                chatActivityEnterView.setReplyingMessageObject(messageObjectToReply);
                if (foundWebPage != null) {
                    return;
                }
                String name;
                if (messageObjectToReply.isFromUser()) {
                    TLRPC.User user = MessagesController.getInstance().getUser(messageObjectToReply.messageOwner.from_id);
                    if (user == null) {
                        return;
                    }
                    name = UserObject.getUserName(user);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(messageObjectToReply.messageOwner.to_id.channel_id);
                    if (chat == null) {
                        return;
                    }
                    name = chat.title;
                }
                replyIconImageView.setImageResource(R.drawable.msg_panel_reply);
                replyNameTextView.setText(name);

                if (messageObjectToReply.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    replyObjectTextView.setText(Emoji.replaceEmoji(messageObjectToReply.messageOwner.media.game.title, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                } else if (messageObjectToReply.messageText != null) {
                    String mess = messageObjectToReply.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    replyObjectTextView.setText(Emoji.replaceEmoji(mess, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                }
            } else if (messageObjectsToForward != null) {
                if (messageObjectsToForward.isEmpty()) {
                    return;
                }
                replyingMessageObject = null;
                chatActivityEnterView.setReplyingMessageObject(null);
                forwardingMessages = messageObjectsToForward;
                if (foundWebPage != null) {
                    return;
                }
                chatActivityEnterView.setForceShowSendButton(true, false);
                ArrayList<Integer> uids = new ArrayList<>();
                replyIconImageView.setImageResource(R.drawable.msg_panel_forward);
                MessageObject object = messageObjectsToForward.get(0);
                if (object.isFromUser()) {
                    uids.add(object.messageOwner.from_id);
                } else {
                    uids.add(-object.messageOwner.to_id.channel_id);
                }
                int type = messageObjectsToForward.get(0).type;
                for (int a = 1; a < messageObjectsToForward.size(); a++) {
                    object = messageObjectsToForward.get(a);
                    Integer uid;
                    if (object.isFromUser()) {
                        uid = object.messageOwner.from_id;
                    } else {
                        uid = -object.messageOwner.to_id.channel_id;
                    }
                    if (!uids.contains(uid)) {
                        uids.add(uid);
                    }
                    if (messageObjectsToForward.get(a).type != type) {
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
                            if (!TextUtils.isEmpty(user.first_name)) {
                                userNames.append(user.first_name);
                            } else if (!TextUtils.isEmpty(user.last_name)) {
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
                    if (messageObjectsToForward.size() == 1 && messageObjectsToForward.get(0).messageText != null) {
                        MessageObject messageObject = messageObjectsToForward.get(0);
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                            replyObjectTextView.setText(Emoji.replaceEmoji(messageObject.messageOwner.media.game.title, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                        } else {
                            String mess = messageObject.messageText.toString();
                            if (mess.length() > 150) {
                                mess = mess.substring(0, 150);
                            }
                            mess = mess.replace('\n', ' ');
                            replyObjectTextView.setText(Emoji.replaceEmoji(mess, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                        }
                    } else {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedMessageCount", messageObjectsToForward.size()));
                    }
                } else {
                    if (type == 1) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedPhoto", messageObjectsToForward.size()));
                        if (messageObjectsToForward.size() == 1) {
                            messageObjectToReply = messageObjectsToForward.get(0);
                        }
                    } else if (type == 4) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedLocation", messageObjectsToForward.size()));
                    } else if (type == 3) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedVideo", messageObjectsToForward.size()));
                        if (messageObjectsToForward.size() == 1) {
                            messageObjectToReply = messageObjectsToForward.get(0);
                        }
                    } else if (type == 12) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedContact", messageObjectsToForward.size()));
                    } else if (type == 2) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedAudio", messageObjectsToForward.size()));
                    } else if (type == 5) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedRound", messageObjectsToForward.size()));
                    } else if (type == 14) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedMusic", messageObjectsToForward.size()));
                    } else if (type == 13) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedSticker", messageObjectsToForward.size()));
                    } else if (type == 8 || type == 9) {
                        if (messageObjectsToForward.size() == 1) {
                            if (type == 8) {
                                replyObjectTextView.setText(LocaleController.getString("AttachGif", R.string.AttachGif));
                            } else {
                                String name;
                                if ((name = FileLoader.getDocumentFileName(messageObjectsToForward.get(0).getDocument())).length() != 0) {
                                    replyObjectTextView.setText(name);
                                }
                                messageObjectToReply = messageObjectsToForward.get(0);
                            }
                        } else {
                            replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedFile", messageObjectsToForward.size()));
                        }
                    }
                }
            } else {
                replyIconImageView.setImageResource(R.drawable.msg_panel_link);
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
                    if (webPage.title != null) {
                        replyObjectTextView.setText(webPage.title);
                    } else if (webPage.description != null) {
                        replyObjectTextView.setText(webPage.description);
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
            TLRPC.PhotoSize photoSize = null;
            if (messageObjectToReply != null) {
                photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObjectToReply.photoThumbs2, 80);
                if (photoSize == null) {
                    photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObjectToReply.photoThumbs, 80);
                }
            }
            if (photoSize == null || photoSize instanceof TLRPC.TL_photoSizeEmpty || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable || messageObjectToReply.type == 13 || messageObjectToReply != null && messageObjectToReply.isSecretMedia()) {
                replyImageView.setImageBitmap(null);
                replyImageLocation = null;
                replyImageView.setVisibility(View.INVISIBLE);
                layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(52);
            } else {
                if (messageObjectToReply.isRoundVideo()) {
                    replyImageView.setRoundRadius(AndroidUtilities.dp(17));
                } else {
                    replyImageView.setRoundRadius(0);
                }
                replyImageLocation = photoSize.location;
                replyImageView.setImage(replyImageLocation, "50_50", (Drawable) null);
                replyImageView.setVisibility(View.VISIBLE);
                layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(96);
            }
            replyNameTextView.setLayoutParams(layoutParams1);
            replyObjectTextView.setLayoutParams(layoutParams2);
            chatActivityEnterView.showTopView(false, openKeyboard);
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
                    showReplyPanel(true, replyingMessageObject, forwardingMessages, null, false);
                    return;
                }
            }
            if (forwardingMessages != null) {
                forwardMessages(forwardingMessages, false);
            }
            chatActivityEnterView.setForceShowSendButton(false, false);
            chatActivityEnterView.hideTopView(false);
            chatActivityEnterView.setReplyingMessageObject(null);
            replyingMessageObject = null;
            forwardingMessages = null;
            replyImageLocation = null;
        }
    }

    private void moveScrollToLastMessage() {
        if (chatListView != null && !messages.isEmpty()) {
            chatLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    private boolean sendSecretMessageRead(MessageObject messageObject) {
        if (messageObject == null || messageObject.isOut() || !messageObject.isSecretMedia() || messageObject.messageOwner.destroyTime != 0 || messageObject.messageOwner.ttl <= 0) {
            return false;
        }
        if (currentEncryptedChat != null) {
            MessagesController.getInstance().markMessageAsRead(dialog_id, messageObject.messageOwner.random_id, messageObject.messageOwner.ttl);
        } else {
            MessagesController.getInstance().markMessageAsRead(messageObject.getId(), ChatObject.isChannel(currentChat) ? currentChat.id : 0, messageObject.messageOwner.ttl);
        }
        messageObject.messageOwner.destroyTime = messageObject.messageOwner.ttl + ConnectionsManager.getInstance().getCurrentTime();
        return true;
    }

    private void clearChatData() {
        messages.clear();
        messagesByDays.clear();
        waitingForLoad.clear();
        groupedMessagesMap.clear();

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
        loadingForward = false;
        waitingForReplyMessageLoad = false;
        startLoadFromMessageId = 0;
        last_message_id = 0;
        unreadMessageObject = null;
        createUnreadMessageAfterId = 0;
        createUnreadMessageAfterIdLoading = false;
        needSelectFromMessageId = false;
        chatAdapter.notifyDataSetChanged();
    }

    private void scrollToLastMessage(boolean pagedown) {
        if (forwardEndReached[0] && first_unread_id == 0 && startLoadFromMessageId == 0) {
            if (pagedown && chatLayoutManager.findFirstCompletelyVisibleItemPosition() == 0) {
                showPagedownButton(false, true);
                highlightMessageId = Integer.MAX_VALUE;
                updateVisibleRows();
            } else {
                chatLayoutManager.scrollToPositionWithOffset(0, 0);
            }
        } else {
            clearChatData();
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(dialog_id, 30, 0, 0, true, 0, classGuid, 0, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
        }
    }

    private void updateTextureViewPosition() {
        if (fragmentView == null) {
            return;
        }
        boolean foundTextureViewMessage = false;
        int count = chatListView.getChildCount();
        int additionalTop = chatActivityEnterView.isTopViewVisible() ? AndroidUtilities.dp(48) : 0;
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + messageCell.getTop() + imageReceiver.getImageY() - additionalTop);
                    fragmentView.invalidate();
                    roundVideoContainer.invalidate();
                    foundTextureViewMessage = true;
                    break;
                }
            }
        }
        if (roundVideoContainer != null) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject.eventId == 0) {
                if (!foundTextureViewMessage) {
                    roundVideoContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                    fragmentView.invalidate();
                    if (messageObject != null && messageObject.isRoundVideo()) {
                        if (checkTextureViewPosition || PipRoundVideoView.getInstance() != null) {
                            MediaController.getInstance().setCurrentRoundVisible(false);
                        } else {
                            scrollToMessageId(messageObject.getId(), 0, false, 0, true);
                        }
                    }
                } else {
                    MediaController.getInstance().setCurrentRoundVisible(true);
                    scrollToMessageId(messageObject.getId(), 0, false, 0, true);
                }
            }
        }
    }

    private void updateMessagesVisisblePart() {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        int additionalTop = chatActivityEnterView.isTopViewVisible() ? AndroidUtilities.dp(48) : 0;
        int height = chatListView.getMeasuredHeight();
        int minPositionHolder = Integer.MAX_VALUE;
        int minPositionDateHolder = Integer.MAX_VALUE;
        View minDateChild = null;
        View minChild = null;
        View minMessageChild = null;
        boolean foundTextureViewMessage = false;
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                int top = messageCell.getTop();
                int bottom = messageCell.getBottom();
                int viewTop = top >= 0 ? 0 : -top;
                int viewBottom = messageCell.getMeasuredHeight();
                if (viewBottom > height) {
                    viewBottom = viewTop + height;
                }
                messageCell.setVisiblePart(viewTop, viewBottom - viewTop);

                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + top + imageReceiver.getImageY() - additionalTop);
                    fragmentView.invalidate();
                    roundVideoContainer.invalidate();
                    foundTextureViewMessage = true;
                }
            }
            if (view.getBottom() <= chatListView.getPaddingTop()) {
                continue;
            }
            int position = view.getBottom();
            if (position < minPositionHolder) {
                minPositionHolder = position;
                if (view instanceof ChatMessageCell || view instanceof ChatActionCell) {
                    minMessageChild = view;
                }
                minChild = view;
            }
            if (view instanceof ChatActionCell && ((ChatActionCell) view).getMessageObject().isDateObject) {
                if (view.getAlpha() != 1.0f) {
                    view.setAlpha(1.0f);
                }
                if (position < minPositionDateHolder) {
                    minPositionDateHolder = position;
                    minDateChild = view;
                }
            }
        }
        if (roundVideoContainer != null) {
            if (!foundTextureViewMessage) {
                roundVideoContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                fragmentView.invalidate();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isRoundVideo() && messageObject.eventId == 0 &&checkTextureViewPosition) {
                    MediaController.getInstance().setCurrentRoundVisible(false);
                }
            } else {
                MediaController.getInstance().setCurrentRoundVisible(true);
            }
        }
        if (minMessageChild != null) {
            MessageObject messageObject;
            if (minMessageChild instanceof ChatMessageCell) {
                messageObject = ((ChatMessageCell) minMessageChild).getMessageObject();
            } else {
                messageObject = ((ChatActionCell) minMessageChild).getMessageObject();
            }
            floatingDateView.setCustomDate(messageObject.messageOwner.date);
        }
        currentFloatingDateOnScreen = false;
        currentFloatingTopIsNotMessage = !(minChild instanceof ChatMessageCell || minChild instanceof ChatActionCell);
        if (minDateChild != null) {
            if (minDateChild.getTop() > chatListView.getPaddingTop() || currentFloatingTopIsNotMessage) {
                if (minDateChild.getAlpha() != 1.0f) {
                    minDateChild.setAlpha(1.0f);
                }
                hideFloatingDateView(!currentFloatingTopIsNotMessage);
            } else {
                if (minDateChild.getAlpha() != 0.0f) {
                    minDateChild.setAlpha(0.0f);
                }
                if (floatingDateAnimation != null) {
                    floatingDateAnimation.cancel();
                    floatingDateAnimation = null;
                }
                if (floatingDateView.getTag() == null) {
                    floatingDateView.setTag(1);
                }
                if (floatingDateView.getAlpha() != 1.0f) {
                    floatingDateView.setAlpha(1.0f);
                }
                currentFloatingDateOnScreen = true;
            }
            int offset = minDateChild.getBottom() - chatListView.getPaddingTop();
            if (offset > floatingDateView.getMeasuredHeight() && offset < floatingDateView.getMeasuredHeight() * 2) {
                floatingDateView.setTranslationY(-floatingDateView.getMeasuredHeight() * 2 + offset);
            } else {
                floatingDateView.setTranslationY(0);
            }
        } else {
            hideFloatingDateView(true);
            floatingDateView.setTranslationY(0);
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
                TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
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
            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
            if (dialog != null) {
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            }
            NotificationsController.updateServerNotificationsSettings(dialog_id);
        }
    }

    private int getScrollOffsetForMessage(MessageObject object) {
        int offset = Integer.MAX_VALUE;
        MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(object);
        if (groupedMessages != null) {
            MessageObject.GroupedMessagePosition currentPosition = groupedMessages.positions.get(object);
            float maxH = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;

            float itemHeight;
            if (currentPosition.siblingHeights != null) {
                itemHeight = currentPosition.siblingHeights[0];
            } else {
                itemHeight = currentPosition.ph;
            }
            float totalHeight = 0.0f;
            float moveDiff = 0.0f;
            SparseBooleanArray array = new SparseBooleanArray();
            for (int a = 0; a < groupedMessages.posArray.size(); a++) {
                MessageObject.GroupedMessagePosition pos = groupedMessages.posArray.get(a);
                if (array.indexOfKey(pos.minY) < 0 && pos.siblingHeights == null) {
                    array.put(pos.minY, true);
                    if (pos.minY < currentPosition.minY) {
                        moveDiff -= pos.ph;
                    } else if (pos.minY > currentPosition.minY) {
                        moveDiff += pos.ph;
                    }
                    totalHeight += pos.ph;
                }
            }
            if (Math.abs(totalHeight - itemHeight) < 0.02f) {
                offset = (int) (chatListView.getMeasuredHeight() - totalHeight * maxH) / 2 - chatListView.getPaddingTop() - AndroidUtilities.dp(7);
            } else {
                offset = (int) (chatListView.getMeasuredHeight() - (itemHeight + moveDiff) * maxH) / 2 - chatListView.getPaddingTop() - AndroidUtilities.dp(7);
            }
        }
        return Math.max(0, offset == Integer.MAX_VALUE ? (chatListView.getMeasuredHeight() - object.getApproximateHeight()) / 2 : offset);
    }

    public void scrollToMessageId(int id, int fromMessageId, boolean select, int loadIndex, boolean smooth) {
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
                int yOffset = getScrollOffsetForMessage(object);
                if (smooth) {
                    if (messages.get(messages.size() - 1) == object) {
                        chatListView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    } else {
                        chatListView.smoothScrollToPosition(chatAdapter.messagesStartRow + messages.indexOf(object));
                    }
                } else {
                    if (messages.get(messages.size() - 1) == object) {
                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.getItemCount() - 1, yOffset, false);
                    } else {
                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.messagesStartRow + messages.indexOf(object), yOffset, false);
                    }
                }
                updateVisibleRows();
                boolean found = false;
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.getId() == object.getId()) {
                            found = true;
                            break;
                        }
                    } else if (view instanceof ChatActionCell) {
                        ChatActionCell cell = (ChatActionCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.getId() == object.getId()) {
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

            waitingForLoad.clear();
            waitingForReplyMessageLoad = true;
            highlightMessageId = Integer.MAX_VALUE;
            scrollToMessagePosition = -10000;
            startLoadFromMessageId = id;
            if (id == createUnreadMessageAfterId) {
                createUnreadMessageAfterIdLoading = true;
            }
            waitingForLoad.add(lastLoadIndex);
            MessagesController.getInstance().loadMessages(loadIndex == 0 ? dialog_id : mergeDialogId, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
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
            pagedownButtonShowedByScroll = false;
            if (pagedownButton.getTag() == null) {
                if (pagedownButtonAnimation != null) {
                    pagedownButtonAnimation.cancel();
                    pagedownButtonAnimation = null;
                }
                if (animated) {
                    if (pagedownButton.getTranslationY() == 0) {
                        pagedownButton.setTranslationY(AndroidUtilities.dp(100));
                    }
                    pagedownButton.setVisibility(View.VISIBLE);
                    pagedownButton.setTag(1);
                    pagedownButtonAnimation = new AnimatorSet();
                    if (mentiondownButton.getVisibility() == View.VISIBLE) {
                        pagedownButtonAnimation.playTogether(
                                ObjectAnimator.ofFloat(pagedownButton, "translationY", 0),
                                ObjectAnimator.ofFloat(mentiondownButton, "translationY", -AndroidUtilities.dp(72)));
                    } else {
                        pagedownButtonAnimation.playTogether(ObjectAnimator.ofFloat(pagedownButton, "translationY", 0));
                    }
                    pagedownButtonAnimation.setDuration(200);
                    pagedownButtonAnimation.start();
                } else {
                    pagedownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            returnToMessageId = 0;
            newUnreadMessageCount = 0;
            if (pagedownButton.getTag() != null) {
                pagedownButton.setTag(null);
                if (pagedownButtonAnimation != null) {
                    pagedownButtonAnimation.cancel();
                    pagedownButtonAnimation = null;
                }
                if (animated) {
                    pagedownButtonAnimation = new AnimatorSet();
                    if (mentiondownButton.getVisibility() == View.VISIBLE) {
                        pagedownButtonAnimation.playTogether(
                                ObjectAnimator.ofFloat(pagedownButton, "translationY", AndroidUtilities.dp(100)),
                                ObjectAnimator.ofFloat(mentiondownButton, "translationY", 0));
                    } else {
                        pagedownButtonAnimation.playTogether(ObjectAnimator.ofFloat(pagedownButton, "translationY", AndroidUtilities.dp(100)));
                    }
                    pagedownButtonAnimation.setDuration(200);
                    pagedownButtonAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            pagedownButtonCounter.setVisibility(View.INVISIBLE);
                            pagedownButton.setVisibility(View.INVISIBLE);
                        }
                    });
                    pagedownButtonAnimation.start();
                } else {
                    pagedownButton.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void showMentiondownButton(boolean show, boolean animated) {
        if (mentiondownButton == null) {
            return;
        }
        if (show) {
            if (mentiondownButton.getTag() == null) {
                if (mentiondownButtonAnimation != null) {
                    mentiondownButtonAnimation.cancel();
                    mentiondownButtonAnimation = null;
                }
                if (animated) {
                    mentiondownButton.setVisibility(View.VISIBLE);
                    mentiondownButton.setTag(1);
                    if (pagedownButton.getVisibility() == View.VISIBLE) {
                        mentiondownButton.setTranslationY(-AndroidUtilities.dp(72));
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, "alpha", 0.0f, 1.0f).setDuration(200);
                    } else {
                        if (mentiondownButton.getTranslationY() == 0) {
                            mentiondownButton.setTranslationY(AndroidUtilities.dp(100));
                        }
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, "translationY", 0).setDuration(200);
                    }
                    mentiondownButtonAnimation.start();
                } else {
                    mentiondownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            returnToMessageId = 0;
            if (mentiondownButton.getTag() != null) {
                mentiondownButton.setTag(null);
                if (mentiondownButtonAnimation != null) {
                    mentiondownButtonAnimation.cancel();
                    mentiondownButtonAnimation = null;
                }
                if (animated) {
                    if (pagedownButton.getVisibility() == View.VISIBLE) {
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, "alpha", 1.0f, 0.0f).setDuration(200);
                    } else {
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, "translationY", AndroidUtilities.dp(100)).setDuration(200);
                    }
                    mentiondownButtonAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mentiondownButtonCounter.setVisibility(View.INVISIBLE);
                            mentiondownButton.setVisibility(View.INVISIBLE);
                        }
                    });
                    mentiondownButtonAnimation.start();
                } else {
                    mentiondownButton.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void updateSecretStatus() {
        if (bottomOverlay == null) {
            return;
        }
        boolean hideKeyboard = false;
        if (ChatObject.isChannel(currentChat) && currentChat.banned_rights != null && currentChat.banned_rights.send_messages) {
            if (AndroidUtilities.isBannedForever(currentChat.banned_rights.until_date)) {
                bottomOverlayText.setText(LocaleController.getString("SendMessageRestrictedForever", R.string.SendMessageRestrictedForever));
            } else {
                bottomOverlayText.setText(LocaleController.formatString("SendMessageRestricted", R.string.SendMessageRestricted, LocaleController.formatDateForBan(currentChat.banned_rights.until_date)));
            }
            bottomOverlay.setVisibility(View.VISIBLE);
            if (mentionListAnimation != null) {
                mentionListAnimation.cancel();
                mentionListAnimation = null;
            }
            mentionContainer.setVisibility(View.GONE);
            mentionContainer.setTag(null);
            hideKeyboard = true;
        } else {
            if (currentEncryptedChat == null || bigEmptyView == null) {
                bottomOverlay.setVisibility(View.INVISIBLE);
                return;
            }
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
                DraftQuery.cleanDraft(dialog_id, false);
                hideKeyboard = true;
            } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                bottomOverlay.setVisibility(View.INVISIBLE);
            }
            checkRaiseSensors();
            checkActionBarMenu();
        }
        if (hideKeyboard) {
            chatActivityEnterView.hidePopup(false);
            if (getParentActivity() != null) {
                AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
            }
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
        if (mentionsAdapter != null) {
            mentionsAdapter.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
        if (requestCode == 17 && chatAttachAlert != null) {
            chatAttachAlert.checkCamera(false);
        } else if (requestCode == 21) {
            if (getParentActivity() == null) {
                return;
            }
            if (grantResults != null && grantResults.length != 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("PermissionNoAudioVideo", R.string.PermissionNoAudioVideo));
                builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            getParentActivity().startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
            }
        } else if (requestCode == 19 && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            processSelectedAttach(attach_photo);
        } else if (requestCode == 20 && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            processSelectedAttach(attach_video);
        } else if (requestCode == 101 && currentUser != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPHelper.startCall(currentUser, getParentActivity(), MessagesController.getInstance().getUserFull(currentUser.id));
            } else {
                VoIPHelper.permissionDenied(getParentActivity(), null);
            }
        }
    }

    private void checkActionBarMenu() {
        if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat) ||
                currentChat != null && ChatObject.isNotInChat(currentChat) ||
                currentUser != null && UserObject.isDeleted(currentUser)) {
            if (timeItem2 != null) {
                timeItem2.setVisibility(View.GONE);
            }
            if (avatarContainer != null) {
                avatarContainer.hideTimeItem();
            }
        } else {
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
                } else if (messageObject.type == 10 || messageObject.type == 11 || messageObject.type == 16) {
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
                        return 9;
                    } else if ((!messageObject.isRoundVideo() || messageObject.isRoundVideo() && BuildVars.DEBUG_VERSION) && (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.getDocument() != null || messageObject.isMusic() || messageObject.isVideo())) {
                        boolean canSave = false;
                        if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
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
                                    if (messageObject.getDocumentName().endsWith("attheme")) {
                                        return 10;
                                    } else if (mime.endsWith("/xml")) {
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
                } else if (!messageObject.isRoundVideo() && (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.getDocument() != null || messageObject.isMusic() || messageObject.isVideo())) {
                    boolean canSave = false;
                    if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
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

    private void addToSelectedMessages(MessageObject messageObject, boolean outside) {
        addToSelectedMessages(messageObject, outside, true);
    }

    private void addToSelectedMessages(MessageObject messageObject, boolean outside, boolean last) {
        int index = messageObject.getDialogId() == dialog_id ? 0 : 1;
        if (outside && messageObject.getGroupId() != 0) {
            boolean hasUnselected = false;
            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
            if (groupedMessages != null) {
                int lastNum = 0;
                for (int a = 0; a < groupedMessages.messages.size(); a++) {
                    MessageObject message = groupedMessages.messages.get(a);
                    if (!selectedMessagesIds[index].containsKey(message.getId())) {
                        hasUnselected = true;
                        lastNum = a;
                    }
                }

                for (int a = 0; a < groupedMessages.messages.size(); a++) {
                    MessageObject message = groupedMessages.messages.get(a);
                    if (hasUnselected) {
                        if (!selectedMessagesIds[index].containsKey(message.getId())) {
                            addToSelectedMessages(message, false, a == lastNum);
                        }
                    } else {
                        addToSelectedMessages(message, false, a == groupedMessages.messages.size() - 1);
                    }
                }
            }
            return;
        }
        if (selectedMessagesIds[index].containsKey(messageObject.getId())) {
            selectedMessagesIds[index].remove(messageObject.getId());
            if (messageObject.type == 0 || messageObject.caption != null) {
                selectedMessagesCanCopyIds[index].remove(messageObject.getId());
            }
            if (messageObject.isSticker()) {
                selectedMessagesCanStarIds[index].remove(messageObject.getId());
            }
            if (messageObject.canEditMessage(currentChat) && messageObject.getGroupId() != 0) {
                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
                if (groupedMessages != null && groupedMessages.messages.size() > 1) {
                    canEditMessagesCount--;
                }
            }
            if (!messageObject.canDeleteMessage(currentChat)) {
                cantDeleteMessagesCount--;
            }
        } else {
            if (selectedMessagesIds[0].size() + selectedMessagesIds[1].size() >= 100) {
                return;
            }
            selectedMessagesIds[index].put(messageObject.getId(), messageObject);
            if (messageObject.type == 0 || messageObject.caption != null) {
                selectedMessagesCanCopyIds[index].put(messageObject.getId(), messageObject);
            }
            if (messageObject.isSticker()) {
                selectedMessagesCanStarIds[index].put(messageObject.getId(), messageObject);
            }
            if (messageObject.canEditMessage(currentChat) && messageObject.getGroupId() != 0) {
                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
                if (groupedMessages != null && groupedMessages.messages.size() > 1) {
                    canEditMessagesCount++;
                }
            }
            if (!messageObject.canDeleteMessage(currentChat)) {
                cantDeleteMessagesCount++;
            }
        }
        if (last && actionBar.isActionModeShowed()) {
            int selectedCount = selectedMessagesIds[0].size() + selectedMessagesIds[1].size();
            if (selectedCount == 0) {
                actionBar.hideActionMode();
                updatePinnedMessageView(true);
                startReplyOnTextChange = false;
            } else {
                ActionBarMenuItem copyItem = actionBar.createActionMode().getItem(copy);
                ActionBarMenuItem starItem = actionBar.createActionMode().getItem(star);
                ActionBarMenuItem editItem = actionBar.createActionMode().getItem(edit);
                final ActionBarMenuItem replyItem = actionBar.createActionMode().getItem(reply);
                int copyVisible = copyItem.getVisibility();
                int starVisible = starItem.getVisibility();
                copyItem.setVisibility(selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);
                starItem.setVisibility(StickersQuery.canAddStickerToFavorites() && (selectedMessagesCanStarIds[0].size() + selectedMessagesCanStarIds[1].size()) == selectedCount ? View.VISIBLE : View.GONE);
                int newCopyVisible = copyItem.getVisibility();
                int newStarVisible = starItem.getVisibility();
                actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
                if (editItem != null) {
                    editItem.setVisibility(canEditMessagesCount == 1 && selectedMessagesIds[0].size() + selectedMessagesIds[1].size() == 1 ? View.VISIBLE : View.GONE);
                }
                hasUnfavedSelected = false;
                for (int a = 0; a < 2; a++) {
                    for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesCanStarIds[a].entrySet()) {
                        MessageObject msg = entry.getValue();
                        if (!StickersQuery.isStickerInFavorites(msg.getDocument())) {
                            hasUnfavedSelected = true;
                            break;
                        }
                    }
                    if (hasUnfavedSelected) {
                        break;
                    }
                }
                starItem.setIcon(hasUnfavedSelected ? R.drawable.ic_ab_fave : R.drawable.ic_ab_unfave);
                if (replyItem != null) {
                    boolean allowChatActions = true;
                    if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 ||
                            isBroadcast ||
                            bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE ||
                            currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !ChatObject.canPost(currentChat) && !currentChat.megagroup || !ChatObject.canSendMessages(currentChat))) {
                        allowChatActions = false;
                    }
                    final int newVisibility = allowChatActions && selectedMessagesIds[0].size() + selectedMessagesIds[1].size() == 1 ? View.VISIBLE : View.GONE;
                    startReplyOnTextChange = newVisibility == View.VISIBLE && !chatActivityEnterView.hasText();
                    if (replyItem.getVisibility() != newVisibility) {
                        if (replyButtonAnimation != null) {
                            replyButtonAnimation.cancel();
                        }
                        if (copyVisible != newCopyVisible || starVisible != newStarVisible) {
                            if (newVisibility == View.VISIBLE) {
                                replyItem.setAlpha(1.0f);
                                replyItem.setScaleX(1.0f);
                            } else {
                                replyItem.setAlpha(0.0f);
                                replyItem.setScaleX(0.0f);
                            }
                            replyItem.setVisibility(newVisibility);
                        } else {
                            replyButtonAnimation = new AnimatorSet();
                            replyItem.setPivotX(AndroidUtilities.dp(54));
                            editItem.setPivotX(AndroidUtilities.dp(54));
                            if (newVisibility == View.VISIBLE) {
                                replyItem.setVisibility(newVisibility);
                                replyButtonAnimation.playTogether(
                                        ObjectAnimator.ofFloat(replyItem, "alpha", 1.0f),
                                        ObjectAnimator.ofFloat(replyItem, "scaleX", 1.0f),
                                        ObjectAnimator.ofFloat(editItem, "alpha", 1.0f),
                                        ObjectAnimator.ofFloat(editItem, "scaleX", 1.0f)
                                );
                            } else {
                                replyButtonAnimation.playTogether(
                                        ObjectAnimator.ofFloat(replyItem, "alpha", 0.0f),
                                        ObjectAnimator.ofFloat(replyItem, "scaleX", 0.0f),
                                        ObjectAnimator.ofFloat(editItem, "alpha", 0.0f),
                                        ObjectAnimator.ofFloat(editItem, "scaleX", 0.0f)
                                );
                            }
                            replyButtonAnimation.setDuration(100);
                            replyButtonAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (replyButtonAnimation != null && replyButtonAnimation.equals(animation)) {
                                        if (newVisibility == View.GONE) {
                                            replyItem.setVisibility(View.GONE);
                                        }
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
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

    private void processRowSelect(View view, boolean outside) {
        MessageObject message = null;
        if (view instanceof ChatMessageCell) {
            message = ((ChatMessageCell) view).getMessageObject();
        } else if (view instanceof ChatActionCell) {
            message = ((ChatActionCell) view).getMessageObject();
        }

        int type = getMessageType(message);

        if (type < 2 || type == 20) {
            return;
        }
        addToSelectedMessages(message, outside);
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
            if (currentUser.self) {
                avatarContainer.setTitle(LocaleController.getString("SavedMessages", R.string.SavedMessages));
            } else if (!MessagesController.isSupportId(currentUser.id) && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
                if (!TextUtils.isEmpty(currentUser.phone)) {
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
        Drawable rightIcon = MessagesController.getInstance().isDialogMuted(dialog_id) ? Theme.chat_muteIconDrawable : null;
        avatarContainer.setTitleIcons(currentEncryptedChat != null ? Theme.chat_lockIconDrawable : null, rightIcon);
        if (muteItem != null) {
            if (rightIcon != null) {
                muteItem.setText(LocaleController.getString("UnmuteNotifications", R.string.UnmuteNotifications));
            } else {
                muteItem.setText(LocaleController.getString("MuteNotifications", R.string.MuteNotifications));
            }
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

    public void openVideoEditor(String videoPath, String caption) {
        if (getParentActivity() != null) {
            final Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
            PhotoViewer.getInstance().setParentActivity(getParentActivity());
            final ArrayList<Object> cameraPhoto = new ArrayList<>();
            MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, videoPath, 0, true);
            entry.caption = caption;
            cameraPhoto.add(entry);
            PhotoViewer.getInstance().openPhotoForSelect(cameraPhoto, 0, 2, new PhotoViewer.EmptyPhotoViewerProvider() {
                @Override
                public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                    return thumb;
                }

                @Override
                public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
                    sendMedia((MediaController.PhotoEntry) cameraPhoto.get(0), videoEditedInfo);
                }

                @Override
                public boolean canScrollAway() {
                    return false;
                }
            }, this);
        } else {
            SendMessagesHelper.prepareSendingVideo(videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, null, 0);
            showReplyPanel(false, null, null, null, false);
            DraftQuery.cleanDraft(dialog_id, true);
        }
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
    }

    private void sendUriAsDocument(Uri uri) {
        if (uri == null) {
            return;
        }
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
                FileLog.e(e);
            }
        }
        String tempPath = AndroidUtilities.getPath(uri);
        String originalPath = tempPath;
        if (tempPath == null) {
            originalPath = uri.toString();
            tempPath = MediaController.copyFileToCache(uri, "file");
        }
        if (tempPath == null) {
            showAttachmentError();
            return;
        }
        SendMessagesHelper.prepareSendingDocument(tempPath, originalPath, null, null, dialog_id, replyingMessageObject, null);
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
                    FileLog.e(e);
                }
                arrayList.add(new MediaController.PhotoEntry(0, 0, 0, currentPicturePath, orientation, false));

                PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, 2, new PhotoViewer.EmptyPhotoViewerProvider() {
                    @Override
                    public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
                        sendMedia((MediaController.PhotoEntry) arrayList.get(0), null);
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
                        FileLog.e(e);
                    }
                    if (videoPath == null) {
                        showAttachmentError();
                    }
                    if (paused) {
                        startVideoEdit = videoPath;
                    } else {
                        openVideoEditor(videoPath, null);
                    }
                } else {
                    SendMessagesHelper.prepareSendingPhoto(null, uri, dialog_id, replyingMessageObject, null, null, null, 0);
                }
                showReplyPanel(false, null, null, null, false);
                DraftQuery.cleanDraft(dialog_id, true);
            } else if (requestCode == 2) {
                String videoPath = null;
                FileLog.d("pic path " + currentPicturePath);
                if (data != null && currentPicturePath != null) {
                    if (new File(currentPicturePath).exists()) {
                        data = null;
                    }
                }
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        FileLog.d("video record uri " + uri.toString());
                        videoPath = AndroidUtilities.getPath(uri);
                        FileLog.d("resolved path = " + videoPath);
                        if (videoPath == null || !(new File(videoPath).exists())) {
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
                if (paused) {
                    startVideoEdit = videoPath;
                } else {
                    openVideoEditor(videoPath, null);
                }
            } else if (requestCode == 21) {
                if (data == null) {
                    showAttachmentError();
                    return;
                }
                if (data.getData() != null) {
                    sendUriAsDocument(data.getData());
                } else if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        sendUriAsDocument(clipData.getItemAt(i).getUri());
                    }
                } else {
                    showAttachmentError();
                }
                showReplyPanel(false, null, null, null, false);
                DraftQuery.cleanDraft(dialog_id, true);
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
                            TLRPC.User user = new TLRPC.TL_user();
                            user.first_name = name;
                            user.last_name = "";
                            user.phone = number;
                            SendMessagesHelper.getInstance().sendMessage(user, dialog_id, replyingMessageObject, null, null);
                        }
                        if (sent) {
                            showReplyPanel(false, null, null, null, false);
                            DraftQuery.cleanDraft(dialog_id, true);
                        }
                    }
                } finally {
                    try {
                        if (c != null && !c.isClosed()) {
                            c.close();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
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
            createUnreadMessageAfterId = 0;
            createUnreadMessageAfterIdLoading = false;
            unread_to_load = 0;
            removeMessageObject(unreadMessageObject);
            unreadMessageObject = null;
        }
    }

    public boolean processSendingText(String text) {
        return chatActivityEnterView.processSendingText(text);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.messagesDidLoaded) {
            int guid = (Integer) args[10];
            if (guid == classGuid) {
                if (!openAnimationEnded) {
                    NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoaded, NotificationCenter.dialogsNeedReload,
                            NotificationCenter.closeChats, NotificationCenter.botKeyboardDidLoaded/*, NotificationCenter.botInfoDidLoaded*/});
                }
                int queryLoadIndex = (Integer) args[11];
                int index = waitingForLoad.indexOf(queryLoadIndex);
                int currentUserId = UserConfig.getClientUserId();
                if (index == -1) {
                    return;
                } else {
                    waitingForLoad.remove(index);
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                boolean createUnreadLoading = false;
                if (waitingForReplyMessageLoad) {
                    if (!createUnreadMessageAfterIdLoading) {
                        boolean found = false;
                        for (int a = 0; a < messArr.size(); a++) {
                            MessageObject obj = messArr.get(a);
                            if (obj.getId() == startLoadFromMessageId) {
                                found = true;
                                break;
                            }
                            if (a + 1 < messArr.size()) {
                                MessageObject obj2 = messArr.get(a + 1);
                                if (obj.getId() >= startLoadFromMessageId && obj2.getId() < startLoadFromMessageId) {
                                    startLoadFromMessageId = obj.getId();
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            startLoadFromMessageId = 0;
                            return;
                        }
                    }
                    int startLoadFrom = startLoadFromMessageId;
                    boolean needSelect = needSelectFromMessageId;
                    int unreadAfterId = createUnreadMessageAfterId;
                    createUnreadLoading = createUnreadMessageAfterIdLoading;
                    clearChatData();
                    createUnreadMessageAfterId = unreadAfterId;
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
                int loaded_max_id = (Integer) args[12];
                int loaded_mentions_count = (Integer) args[13];
                if (loaded_mentions_count < 0) {
                    loaded_mentions_count *= -1;
                    hasAllMentionsLocal = false;
                } else if (first) {
                    hasAllMentionsLocal = true;
                }
                if (load_type == 4) {
                    startLoadFromMessageId = loaded_max_id;

                    for (int a = messArr.size() - 1; a > 0; a--) {
                        MessageObject obj = messArr.get(a);
                        if (obj.type < 0 && obj.getId() == startLoadFromMessageId) {
                            startLoadFromMessageId = messArr.get(a - 1).getId();
                            break;
                        }
                    }
                }
                boolean wasUnread = false;
                boolean showUnreadCounter = false;
                if (fnid != 0) {
                    last_message_id = (Integer) args[5];
                    if (load_type == 3) {
                        if (loadingFromOldPosition) {
                            unread_to_load = (Integer) args[6];
                            if (unread_to_load != 0) {
                                createUnreadMessageAfterId = fnid;
                            }
                            showUnreadCounter = true;
                            loadingFromOldPosition = false;
                        }
                        first_unread_id = 0;
                    } else {
                        first_unread_id = fnid;
                        unread_to_load = (Integer) args[6];
                    }
                } else if (startLoadFromMessageId != 0 && (load_type == 3 || load_type == 4)) {
                    last_message_id = (Integer) args[5];
                }
                int newRowsCount = 0;

                if (load_type != 0) {
                    forwardEndReached[loadIndex] = startLoadFromMessageId == 0 && last_message_id == 0;
                }
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
                        groupedMessagesMap.clear();
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (parentLayout != null) {
                                parentLayout.resumeDelayedFragmentAnimation();
                            }
                        }
                    });
                }

                if (load_type == 1) {
                    Collections.reverse(messArr);
                }
                if (currentEncryptedChat == null) {
                    MessagesQuery.loadReplyMessagesForMessages(messArr, dialog_id);
                }
                int approximateHeightSum = 0;
                if (load_type == 2 && messArr.isEmpty() && !isCache) {
                    forwardEndReached[0] = true;
                }
                HashMap<Long, MessageObject.GroupedMessages> newGroups = null;
                HashMap<Long, MessageObject.GroupedMessages> changedGroups = null;
                for (int a = 0; a < messArr.size(); a++) {
                    MessageObject obj = messArr.get(a);
                    approximateHeightSum += obj.getApproximateHeight();
                    if (currentUser != null) {
                        if (currentUser.self) {
                            obj.messageOwner.out = true;
                        }
                        if (currentUser.bot && obj.isOut() || currentUser.id == currentUserId) {
                            obj.setIsRead();
                        }
                    }
                    if (messagesDict[loadIndex].containsKey(obj.getId())) {
                        continue;
                    }
                    if (loadIndex == 1) {
                        obj.setIsRead();
                    }
                    if (loadIndex == 0 && ChatObject.isChannel(currentChat) && obj.getId() == 1) {
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

                    if (obj.getId() == last_message_id) {
                        forwardEndReached[loadIndex] = true;
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
                        TLRPC.Message dateMsg = new TLRPC.TL_message();
                        dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                        dateMsg.id = 0;
                        dateMsg.date = obj.messageOwner.date;
                        MessageObject dateObj = new MessageObject(dateMsg, null, false);
                        dateObj.type = 10;
                        dateObj.contentType = 1;
                        dateObj.isDateObject = true;
                        if (load_type == 1) {
                            messages.add(0, dateObj);
                        } else {
                            messages.add(dateObj);
                        }
                        newRowsCount++;
                    }

                    if (obj.hasValidGroupId()) {
                        MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(obj.messageOwner.grouped_id);
                        if (groupedMessages != null) {
                            if (messages.size() > 1) {
                                MessageObject previous;
                                if (load_type == 1) {
                                    previous = messages.get(0);
                                } else {
                                    previous = messages.get(messages.size() - 2);
                                }
                                if (previous.messageOwner.grouped_id == obj.messageOwner.grouped_id) {
                                    if (previous.localGroupId != 0) {
                                        obj.localGroupId = previous.localGroupId;
                                        groupedMessages = groupedMessagesMap.get(previous.localGroupId);
                                    }
                                } else if (previous.messageOwner.grouped_id != obj.messageOwner.grouped_id) {
                                    obj.localGroupId = Utilities.random.nextLong();
                                    groupedMessages = null;
                                }
                            }
                        }
                        if (groupedMessages == null) {
                            groupedMessages = new MessageObject.GroupedMessages();
                            groupedMessages.groupId = obj.getGroupId();
                            groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                        } else if (newGroups == null || !newGroups.containsKey(obj.getGroupId())) {
                            if (changedGroups == null) {
                                changedGroups = new HashMap<>();
                            }
                            changedGroups.put(obj.getGroupId(), groupedMessages);
                        }
                        if (newGroups == null) {
                            newGroups = new HashMap<>();
                        }
                        newGroups.put(groupedMessages.groupId, groupedMessages);
                        if (load_type == 1) {
                            groupedMessages.messages.add(obj);
                        } else {
                            groupedMessages.messages.add(0, obj);
                        }
                    } else if (obj.messageOwner.grouped_id != 0) {
                        obj.messageOwner.grouped_id = 0;
                    }

                    newRowsCount++;
                    dayArray.add(obj);
                    if (load_type == 1) {
                        messages.add(0, obj);
                    } else {
                        messages.add(messages.size() - 1, obj);
                    }

                    MessageObject prevObj;
                    if (currentEncryptedChat == null) {
                        if (createUnreadMessageAfterId != 0 && load_type != 1 && a + 1 < messArr.size()) {
                            prevObj = messArr.get(a + 1);
                            if (obj.isOut() || prevObj.getId() >= createUnreadMessageAfterId) {
                                prevObj = null;
                            }
                        } else {
                            prevObj = null;
                        }
                    } else {
                        if (createUnreadMessageAfterId != 0 && load_type != 1 && a - 1 >= 0) {
                            prevObj = messArr.get(a - 1);
                            if (obj.isOut() || prevObj.getId() >= createUnreadMessageAfterId) {
                                prevObj = null;
                            }
                        } else {
                            prevObj = null;
                        }
                    }
                    if (load_type == 2 && obj.getId() == first_unread_id) {
                        if (approximateHeightSum > AndroidUtilities.displaySize.y / 2 || !forwardEndReached[0]) {
                            TLRPC.Message dateMsg = new TLRPC.TL_message();
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
                    } else if ((load_type == 3 || load_type == 4) && obj.getId() == startLoadFromMessageId) {
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
                    if (load_type != 2 && unreadMessageObject == null && createUnreadMessageAfterId != 0 &&
                            (currentEncryptedChat == null && !obj.isOut() && obj.getId() >= createUnreadMessageAfterId || currentEncryptedChat != null && !obj.isOut() && obj.getId() <= createUnreadMessageAfterId) &&
                            (load_type == 1 || prevObj != null || prevObj == null && createUnreadLoading && a == messArr.size() - 1)) {
                        TLRPC.Message dateMsg = new TLRPC.TL_message();
                        dateMsg.message = "";
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(dateMsg, null, false);
                        dateObj.type = 6;
                        dateObj.contentType = 2;
                        if (load_type == 1) {
                            messages.add(1, dateObj);
                        } else {
                            messages.add(messages.size() - 1, dateObj);
                        }
                        unreadMessageObject = dateObj;
                        if (load_type == 3) {
                            scrollToMessage = unreadMessageObject;
                            startLoadFromMessageId = 0;
                            scrollToMessagePosition = -9000;
                        }
                        newRowsCount++;
                    }
                }
                if (createUnreadLoading) {
                    createUnreadMessageAfterId = 0;
                }
                if (load_type == 0 && newRowsCount == 0) {
                    loadsCount--;
                }
                if (newGroups != null) {
                    for (HashMap.Entry<Long, MessageObject.GroupedMessages> entry : newGroups.entrySet()) {
                        MessageObject.GroupedMessages groupedMessages = entry.getValue();
                        groupedMessages.calculate();
                        if (chatAdapter != null && changedGroups != null && changedGroups.containsKey(entry.getKey())) {
                            MessageObject messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                            int idx = messages.indexOf(messageObject);
                            if (idx >= 0) {
                                chatAdapter.notifyItemRangeChanged(idx + chatAdapter.messagesStartRow, groupedMessages.messages.size());
                            }
                        }
                    }
                }

                if (forwardEndReached[loadIndex] && loadIndex != 1) {
                    first_unread_id = 0;
                    last_message_id = 0;
                    createUnreadMessageAfterId = 0;
                }

                if (load_type == 1) {
                    int rowsRemoved = 0;
                    if (messArr.size() != count && (!isCache || currentEncryptedChat != null || forwardEndReached[loadIndex])) {
                        forwardEndReached[loadIndex] = true;
                        if (loadIndex != 1) {
                            first_unread_id = 0;
                            last_message_id = 0;
                            createUnreadMessageAfterId = 0;
                            chatAdapter.notifyItemRemoved(chatAdapter.loadingDownRow);
                            rowsRemoved++;
                        }
                        startLoadFromMessageId = 0;
                    }
                    if (newRowsCount > 0) {
                        int firstVisPos = chatLayoutManager.findFirstVisibleItemPosition();
                        int top = 0;
                        if (firstVisPos == 0) {
                            firstVisPos++;
                        }
                        View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                        top = ((firstVisView == null) ? 0 : chatListView.getMeasuredHeight() - firstVisView.getBottom() - chatListView.getPaddingBottom());
                        chatAdapter.notifyItemRangeInserted(1, newRowsCount);
                        if (firstVisPos != RecyclerView.NO_POSITION) {
                            chatLayoutManager.scrollToPositionWithOffset(firstVisPos + newRowsCount - rowsRemoved, top);
                        }
                    }
                    loadingForward = false;
                } else {
                    if (messArr.size() < count && load_type != 3 && load_type != 4) {
                        if (isCache) {
                            if (currentEncryptedChat != null || isBroadcast) {
                                endReached[loadIndex] = true;
                            }
                            if (load_type != 2) {
                                cacheEndReached[loadIndex] = true;
                            }
                        } else if (load_type != 2 || messArr.size() == 0 && messages.isEmpty()) {
                            endReached[loadIndex] = true;
                        }
                    }
                    loading = false;

                    if (chatListView != null) {
                        if (first || scrollToTopOnResume || forceScrollToTop) {
                            forceScrollToTop = false;
                            chatAdapter.notifyDataSetChanged();
                            if (scrollToMessage != null) {
                                int yOffset;
                                boolean bottom = true;
                                if (startLoadFromMessageOffset != Integer.MAX_VALUE) {
                                    yOffset = -startLoadFromMessageOffset - chatListView.getPaddingBottom();
                                    startLoadFromMessageOffset = Integer.MAX_VALUE;
                                } else if (scrollToMessagePosition == -9000) {
                                    yOffset = getScrollOffsetForMessage(scrollToMessage);
                                    bottom = false;
                                } else if (scrollToMessagePosition == -10000) {
                                    yOffset = -chatListView.getPaddingTop() - AndroidUtilities.dp(7);
                                    bottom = false;
                                } else {
                                    yOffset = scrollToMessagePosition;
                                }
                                if (!messages.isEmpty()) {
                                    if (messages.get(messages.size() - 1) == scrollToMessage || messages.get(messages.size() - 2) == scrollToMessage) {
                                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.loadingUpRow, yOffset, bottom);
                                    } else {
                                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.messagesStartRow + messages.indexOf(scrollToMessage), yOffset, bottom);
                                    }
                                }
                                chatListView.invalidate();
                                if (scrollToMessagePosition == -10000 || scrollToMessagePosition == -9000) {
                                    showPagedownButton(true, true);
                                    if (load_type == 3 && unread_to_load != 0 && showUnreadCounter) {
                                        pagedownButtonCounter.setVisibility(View.VISIBLE);
                                        pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount = unread_to_load));
                                    }
                                }
                                scrollToMessagePosition = -10000;
                                scrollToMessage = null;
                            } else {
                                moveScrollToLastMessage();
                            }
                            if (loaded_mentions_count != 0) {
                                showMentiondownButton(true, true);
                                mentiondownButtonCounter.setVisibility(View.VISIBLE);
                                mentiondownButtonCounter.setText(String.format("%d", newMentionsCount = loaded_mentions_count));
                            }
                        } else {
                            if (newRowsCount != 0) {
                                boolean end = false;
                                if (endReached[loadIndex] && (loadIndex == 0 && mergeDialogId == 0 || loadIndex == 1)) {
                                    end = true;
                                    chatAdapter.notifyItemRangeChanged(chatAdapter.loadingUpRow - 1, 2);
                                    chatAdapter.updateRows();
                                }
                                int firstVisPos = chatLayoutManager.findFirstVisibleItemPosition();
                                View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                                int top = ((firstVisView == null) ? 0 : chatListView.getMeasuredHeight() - firstVisView.getBottom() - chatListView.getPaddingBottom());
                                if (newRowsCount - (end ? 1 : 0) > 0) {
                                    int insertStart = chatAdapter.messagesEndRow;/* (chatAdapter.isBot ? 2 : 1) + (end ? 0 : 1); TODO check with bot*/
                                    chatAdapter.notifyItemChanged(chatAdapter.loadingUpRow);
                                    chatAdapter.notifyItemRangeInserted(insertStart, newRowsCount - (end ? 1 : 0));
                                }
                                if (firstVisPos != -1) {
                                    chatLayoutManager.scrollToPositionWithOffset(firstVisPos, top);
                                }
                            } else if (endReached[loadIndex] && (loadIndex == 0 && mergeDialogId == 0 || loadIndex == 1)) {
                                chatAdapter.notifyItemRemoved(chatAdapter.loadingUpRow);
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
                if (currentChat != null) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
                    if (chat != null) {
                        currentChat = chat;
                    }
                } else if (currentUser != null) {
                    TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
                    if (user != null) {
                        currentUser = user;
                    }
                }
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
                int currentUserId = UserConfig.getClientUserId();
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
                            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser && messageObject.messageOwner.action.user_id == currentUserId ||
                                    messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser && messageObject.messageOwner.action.users.contains(currentUserId)) {
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
                                } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                                    messageObject.generateGameMessageText(null);
                                } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                                    messageObject.generatePaymentSentMessageText(null);
                                }
                                if (messageObject.isMegagroup() && messageObject.replyMessageObject != null && messageObject.replyMessageObject.messageOwner != null) {
                                    messageObject.replyMessageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
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
                        if (currentUser != null && (currentUser.bot && obj.isOut() || currentUser.id == currentUserId)) {
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
                        if (obj.type < 0 || messagesDict[0].containsKey(obj.getId())) {
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
                        if (obj.messageOwner.mentioned && obj.isContentUnread()) {
                            newMentionsCount++;
                        }
                        newUnreadMessageCount++;
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }
                    if (newUnreadMessageCount != 0 && pagedownButtonCounter != null) {
                        pagedownButtonCounter.setVisibility(View.VISIBLE);
                        pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
                    }
                    if (newMentionsCount != 0 && mentiondownButtonCounter != null) {
                        mentiondownButtonCounter.setVisibility(View.VISIBLE);
                        mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                        showMentiondownButton(true, true);
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
                    HashMap<Long, MessageObject.GroupedMessages> newGroups = null;
                    boolean markAsRead = false;
                    boolean unreadUpdated = true;
                    HashMap<String, ArrayList<MessageObject>> webpagesToReload = null;
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("received new messages " + arr.size() + " in dialog " + dialog_id);
                    }
                    for (int a = 0; a < arr.size(); a++) {
                        int placeToPaste = -1;
                        MessageObject obj = arr.get(a);

                        if (currentUser != null && (currentUser.bot && obj.isOut() || currentUser.id == currentUserId)) {
                            obj.setIsRead();
                        }
                        if (avatarContainer != null && currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction && obj.messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                            avatarContainer.setTime(((TLRPC.TL_decryptedMessageActionSetMessageTTL) obj.messageOwner.action.encryptedAction).ttl_seconds);
                        }
                        if (obj.type < 0 || messagesDict[0].containsKey(obj.getId())) {
                            continue;
                        }

                        if (a == 0 && obj.messageOwner.id < 0 && obj.type == 5) {
                            animatingMessageObjects.add(obj);
                        }

                        MessageObject.GroupedMessages groupedMessages;
                        if (obj.hasValidGroupId()) {
                            groupedMessages = groupedMessagesMap.get(obj.getGroupId());
                            if (groupedMessages == null) {
                                groupedMessages = new MessageObject.GroupedMessages();
                                groupedMessages.groupId = obj.getGroupId();
                                groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                            }
                            if (newGroups == null) {
                                newGroups = new HashMap<>();
                            }
                            newGroups.put(groupedMessages.groupId, groupedMessages);
                            groupedMessages.messages.add(obj);
                        } else {
                            groupedMessages = null;
                        }

                        if (groupedMessages != null) {
                            int size = groupedMessages.messages.size();
                            MessageObject messageObject = size > 1 ? groupedMessages.messages.get(groupedMessages.messages.size() - 2) : null;
                            if (messageObject != null) {
                                placeToPaste = messages.indexOf(messageObject);
                            }
                        }

                        if (placeToPaste == -1) {
                            if (obj.messageOwner.id < 0 || messages.isEmpty()) {
                                placeToPaste = 0;
                            } else {
                                int size = messages.size();
                                for (int b = 0; b < size; b++) {
                                    MessageObject lastMessage = messages.get(b);
                                    if (lastMessage.type >= 0 && lastMessage.messageOwner.date > 0) {
                                        if (lastMessage.messageOwner.id > 0 && obj.messageOwner.id > 0 && lastMessage.messageOwner.id < obj.messageOwner.id || lastMessage.messageOwner.date < obj.messageOwner.date) {
                                            MessageObject.GroupedMessages lastGroupedMessages;
                                            if (lastMessage.getGroupId() != 0) {
                                                lastGroupedMessages = groupedMessagesMap.get(lastMessage.getGroupId());
                                                if (lastGroupedMessages != null && lastGroupedMessages.messages.size() == 0) {
                                                    lastGroupedMessages = null;
                                                }
                                            } else {
                                                lastGroupedMessages = null;
                                            }
                                            if (lastGroupedMessages == null) {
                                                placeToPaste = b;
                                            } else {
                                                placeToPaste = messages.indexOf(lastGroupedMessages.messages.get(lastGroupedMessages.messages.size() - 1));
                                            }
                                            break;
                                        }
                                    }
                                }
                                if (placeToPaste == -1 || placeToPaste > messages.size()) {
                                    placeToPaste = messages.size();
                                }
                            }
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
                            if (newGroups != null) {
                                for (HashMap.Entry<Long, MessageObject.GroupedMessages> entry : newGroups.entrySet()) {
                                    entry.getValue().calculate();
                                }
                            }
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
                            TLRPC.Message dateMsg = new TLRPC.TL_message();
                            dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                            dateMsg.id = 0;
                            dateMsg.date = obj.messageOwner.date;
                            MessageObject dateObj = new MessageObject(dateMsg, null, false);
                            dateObj.type = 10;
                            dateObj.contentType = 1;
                            dateObj.isDateObject = true;
                            messages.add(placeToPaste, dateObj);
                            if (chatAdapter != null) {
                                chatAdapter.notifyItemInserted(placeToPaste);
                            }
                        }
                        if (!obj.isOut()) {
                            if (paused && placeToPaste == 0) {
                                if (!scrollToTopUnReadOnResume && unreadMessageObject != null) {
                                    removeMessageObject(unreadMessageObject);
                                    if (placeToPaste > 0) {
                                        placeToPaste--;
                                    }
                                    unreadMessageObject = null;
                                }
                                if (unreadMessageObject == null) {
                                    TLRPC.Message dateMsg = new TLRPC.TL_message();
                                    dateMsg.message = "";
                                    dateMsg.id = 0;
                                    MessageObject dateObj = new MessageObject(dateMsg, null, false);
                                    dateObj.type = 6;
                                    dateObj.contentType = 2;
                                    messages.add(0, dateObj);
                                    if (chatAdapter != null) {
                                        chatAdapter.notifyItemInserted(0);
                                    }
                                    unreadMessageObject = dateObj;
                                    scrollToMessage = unreadMessageObject;
                                    scrollToMessagePosition = -10000;
                                    unreadUpdated = false;
                                    unread_to_load = 0;
                                    scrollToTopUnReadOnResume = true;
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
                        if (placeToPaste > messages.size()) {
                            placeToPaste = messages.size();
                        }

                        messages.add(placeToPaste, obj);
                        if (chatAdapter != null) {
                            chatAdapter.notifyItemChanged(placeToPaste);
                            chatAdapter.notifyItemInserted(placeToPaste);
                        }
                        if (!obj.isOut() && obj.messageOwner.mentioned && obj.isContentUnread()) {
                            newMentionsCount++;
                        }
                        newUnreadMessageCount++;
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }
                    if (webpagesToReload != null) {
                        MessagesController.getInstance().reloadWebPages(dialog_id, webpagesToReload);
                    }
                    if (newGroups != null) {
                        for (HashMap.Entry<Long, MessageObject.GroupedMessages> entry : newGroups.entrySet()) {
                            MessageObject.GroupedMessages groupedMessages = entry.getValue();
                            int oldCount = groupedMessages.posArray.size();
                            entry.getValue().calculate();
                            int newCount = groupedMessages.posArray.size();
                            if (newCount - oldCount > 0) {
                                int index = messages.indexOf(groupedMessages.messages.get(groupedMessages.messages.size() - 1));
                                if (index >= 0) {
                                    chatAdapter.notifyItemRangeChanged(index, newCount);
                                }
                            }
                        }
                    }

                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                    }
                    if (chatAdapter != null) {
                        if (unreadUpdated) {
                            chatAdapter.updateRowWithMessageObject(unreadMessageObject);
                        }
                    } else {
                        scrollToTopOnResume = true;
                    }

                    if (chatListView != null && chatAdapter != null) {
                        int lastVisible = chatLayoutManager.findFirstVisibleItemPosition();
                        if (lastVisible == RecyclerView.NO_POSITION) {
                            lastVisible = 0;
                        }
                        if (lastVisible == 0 || hasFromMe) {
                            newUnreadMessageCount = 0;
                            if (!firstLoading) {
                                if (paused) {
                                    scrollToTopOnResume = true;
                                } else {
                                    forceScrollToTop = true;
                                    moveScrollToLastMessage();
                                }
                            }
                        } else {
                            if (newUnreadMessageCount != 0 && pagedownButtonCounter != null) {
                                pagedownButtonCounter.setVisibility(View.VISIBLE);
                                pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
                            }
                            showPagedownButton(true, true);
                        }
                        if (newMentionsCount != 0 && mentiondownButtonCounter != null) {
                            mentiondownButtonCounter.setVisibility(View.VISIBLE);
                            mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                            showMentiondownButton(true, true);
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
            SparseArray<Long> outbox = (SparseArray<Long>) args[1];
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
                int messageId = (int) ((long) outbox.get(key));
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
            if (inbox.size() != 0) {
                removeUnreadPlane();
            }
            if (updated) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.historyCleared) {
            long did = (Long) args[0];
            if (did != dialog_id) {
                return;
            }
            int max_id = (Integer) args[1];
            boolean updated = false;

            for (int b = 0; b < messages.size(); b++) {
                MessageObject obj = messages.get(b);
                int mid = obj.getId();
                if (mid <= 0 || mid > max_id) {
                    continue;
                }
                if (info != null && info.pinned_msg_id == mid) {
                    pinnedMessageObject = null;
                    info.pinned_msg_id = 0;
                    MessagesStorage.getInstance().updateChannelPinnedMessage(info.id, 0);
                    updatePinnedMessageView(true);
                }
                messages.remove(b);
                b--;
                messagesDict[0].remove(mid);
                ArrayList<MessageObject> dayArr = messagesByDays.get(obj.dateKey);
                if (dayArr != null) {
                    dayArr.remove(obj);
                    if (dayArr.isEmpty()) {
                        messagesByDays.remove(obj.dateKey);
                        if (b >= 0 && b < messages.size()) {
                            messages.remove(b);
                            b--;
                        }
                    }
                }
                updated = true;
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
                    MessagesController.getInstance().loadMessages(dialog_id, 30, 0, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
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
            HashMap<Long, MessageObject.GroupedMessages> newGroups = null;
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
                        MessageObject removed = messages.remove(index);
                        if (removed.getGroupId() != 0) {
                            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(removed.getGroupId());
                            if (groupedMessages != null) {
                                if (newGroups == null) {
                                    newGroups = new HashMap<>();
                                }
                                newGroups.put(groupedMessages.groupId, groupedMessages);
                                groupedMessages.messages.remove(obj);
                            }
                        }
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
                        }
                        updated = true;
                    }
                }
            }
            if (newGroups != null) {
                for (HashMap.Entry<Long, MessageObject.GroupedMessages> entry : newGroups.entrySet()) {
                    MessageObject.GroupedMessages groupedMessages = entry.getValue();
                    if (groupedMessages.messages.isEmpty()) {
                        groupedMessagesMap.remove(groupedMessages.groupId);
                    } else {
                        groupedMessages.calculate();
                        MessageObject messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                        int index = messages.indexOf(messageObject);
                        if (index >= 0) {
                            if (chatAdapter != null) {
                                chatAdapter.notifyItemRangeChanged(index + chatAdapter.messagesStartRow, groupedMessages.messages.size());
                            }
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
                    MessagesController.getInstance().loadMessages(dialog_id, 30, 0, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
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
            if (chatAdapter != null) {
                if (updated) {
                    removeUnreadPlane();
                    chatAdapter.notifyDataSetChanged();
                } else {
                    first_unread_id = 0;
                    last_message_id = 0;
                    createUnreadMessageAfterId = 0;
                    unread_to_load = 0;
                    removeMessageObject(unreadMessageObject);
                    unreadMessageObject = null;
                    if (pagedownButtonCounter != null) {
                        pagedownButtonCounter.setVisibility(View.INVISIBLE);
                    }
                }
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
                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                        }
                    }
                    return;
                }
                TLRPC.Message newMsgObj = (TLRPC.Message) args[2];
                boolean mediaUpdated = false;
                boolean updatedForward = false;
                if (newMsgObj != null) {
                    try {
                        updatedForward = obj.isForwarded() && (obj.messageOwner.reply_markup == null && newMsgObj.reply_markup != null || !obj.messageOwner.message.equals(newMsgObj.message));
                        mediaUpdated = updatedForward ||
                                obj.messageOwner.params != null && obj.messageOwner.params.containsKey("query_id") ||
                                newMsgObj.media != null && obj.messageOwner.media != null && !newMsgObj.media.getClass().equals(obj.messageOwner.media.getClass());
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (obj.getGroupId() != 0 && newMsgObj.grouped_id != 0) {
                        MessageObject.GroupedMessages oldGroup = groupedMessagesMap.get(obj.getGroupId());
                        if (oldGroup != null) {
                            groupedMessagesMap.put(newMsgObj.grouped_id, oldGroup);
                        }
                    }
                    obj.messageOwner = newMsgObj;
                    obj.generateThumbs(true);
                    obj.setType();

                    if (newMsgObj.media instanceof TLRPC.TL_messageMediaGame) {
                        obj.applyNewText();
                    }
                }
                if (updatedForward) {
                    obj.measureInlineBotButtons();
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
                if (chatLayoutManager != null) {
                    if (mediaUpdated && chatLayoutManager.findFirstVisibleItemPosition() == 0) {
                        moveScrollToLastMessage();
                    }
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
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setChatInfo(info);
                }
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
                    URLSpanBotCommand.enabled = !info.bot_info.isEmpty() && currentChat != null && currentChat.megagroup;
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
            if (currentEncryptedChat != null) {
                updateSpamView();
            }
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
                    chatActivityEnterView.checkRoundVideo();
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
        } else if (id == NotificationCenter.removeAllMessagesFromDialog) {
            long did = (Long) args[0];
            if (dialog_id == did) {
                messages.clear();
                waitingForLoad.clear();
                messagesByDays.clear();
                groupedMessagesMap.clear();
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
                    selectedMessagesCanStarIds[a].clear();
                }
                cantDeleteMessagesCount = 0;
                canEditMessagesCount = 0;
                actionBar.hideActionMode();
                updatePinnedMessageView(true);

                if (botButtons != null) {
                    botButtons = null;
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.setButtons(null, false);
                    }
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
                    startLoadFromMessageId = 0;
                    needSelectFromMessageId = false;
                    waitingForLoad.add(lastLoadIndex);
                    MessagesController.getInstance().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, 0, true, 0, classGuid, 2, 0, ChatObject.isChannel(currentChat), lastLoadIndex++);
                } else {
                    if (progressView != null) {
                        progressView.setVisibility(View.INVISIBLE);
                        chatListView.setEmptyView(emptyViewContainer);
                    }
                }

                if (chatAdapter != null) {
                    chatAdapter.notifyDataSetChanged();
                }
                if (currentEncryptedChat == null && currentUser != null && currentUser.bot && botUser == null) {
                    botUser = "";
                    updateBottomOverlay();
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
            SparseArray<ArrayList<Long>> mids = (SparseArray<ArrayList<Long>>) args[0];
            boolean changed = false;
            for (int i = 0; i < mids.size(); i++) {
                int key = mids.keyAt(i);
                ArrayList<Long> arr = mids.get(key);
                for (int a = 0; a < arr.size(); a++) {
                    long mid = arr.get(a);
                    if (a == 0) {
                        int channelId = (int) (mid >> 32);
                        if (channelId < 0) {
                            channelId = 0;
                        }
                        if (channelId != (ChatObject.isChannel(currentChat) ? currentChat.id : 0)) {
                            return;
                        }
                    }
                    MessageObject messageObject = messagesDict[0].get((int) mid);
                    if (messageObject != null) {
                        messageObject.messageOwner.destroyTime = key;
                        changed = true;
                    }
                }
            }
            if (changed) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.messagePlayingDidStarted) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.eventId != 0) {
                return;
            }
            sendSecretMessageRead(messageObject);

            if (messageObject.isRoundVideo()) {
                MediaController.getInstance().setTextureView(createTextureView(true), aspectRatioFrameLayout, roundVideoContainer, true);
                updateTextureViewPosition();
            }

            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null) {
                            if (messageObject1.isVoice() || messageObject1.isMusic()) {
                                cell.updateButtonState(false);
                            } else if (messageObject1.isRoundVideo()) {
                                cell.checkRoundVideoPlayback(false);
                            }
                        }
                    }
                }
                count = mentionListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = mentionListView.getChildAt(a);
                    if (view instanceof ContextLinkCell) {
                        ContextLinkCell cell = (ContextLinkCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null && (messageObject1.isVoice() || messageObject1.isMusic())) {
                            cell.updateButtonState(false);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (id == NotificationCenter.messagePlayingDidReset) {
                destroyTextureView();
            }
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null) {
                            if (messageObject.isVoice() || messageObject.isMusic()) {
                                cell.updateButtonState(false);
                            } else if (messageObject.isRoundVideo()) {
                                if (!MediaController.getInstance().isPlayingMessage(messageObject)) {
                                    cell.checkRoundVideoPlayback(true);
                                }
                            }
                        }
                    }
                }
                count = mentionListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = mentionListView.getChildAt(a);
                    if (view instanceof ContextLinkCell) {
                        ContextLinkCell cell = (ContextLinkCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
                            cell.updateButtonState(false);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject playing = cell.getMessageObject();
                        if (playing != null && playing.getId() == mid) {
                            MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                            if (player != null) {
                                playing.audioProgress = player.audioProgress;
                                playing.audioProgressSec = player.audioProgressSec;
                                cell.updatePlayingMessageProgress();
                            }
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.updateMessageMedia) {
            TLRPC.Message message = (TLRPC.Message) args[0];
            MessageObject existMessageObject = messagesDict[0].get(message.id);
            if (existMessageObject != null) {
                existMessageObject.messageOwner.media = message.media;
                existMessageObject.messageOwner.attachPath = message.attachPath;
                existMessageObject.generateThumbs(false);
                if (existMessageObject.getGroupId() != 0 && (existMessageObject.photoThumbs == null || existMessageObject.photoThumbs.isEmpty())) {
                    MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(existMessageObject.getGroupId());
                    if (groupedMessages != null) {
                        int idx = groupedMessages.messages.indexOf(existMessageObject);
                        if (idx >= 0) {
                            int updateCount = groupedMessages.messages.size();
                            MessageObject messageObject = null;
                            if (idx > 0 && idx < groupedMessages.messages.size() - 1) {
                                MessageObject.GroupedMessages slicedGroup = new MessageObject.GroupedMessages();
                                slicedGroup.groupId = Utilities.random.nextLong();
                                slicedGroup.messages.addAll(groupedMessages.messages.subList(idx + 1, groupedMessages.messages.size()));
                                for (int b = 0; b < slicedGroup.messages.size(); b++) {
                                    slicedGroup.messages.get(b).localGroupId = slicedGroup.groupId;
                                    groupedMessages.messages.remove(idx + 1);
                                }
                                groupedMessagesMap.put(slicedGroup.groupId, slicedGroup);
                                messageObject = slicedGroup.messages.get(slicedGroup.messages.size() - 1);
                                slicedGroup.calculate();
                            }
                            groupedMessages.messages.remove(idx);
                            if (messageObject == null) {
                                messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                            }
                            if (groupedMessages.messages.isEmpty()) {
                                groupedMessagesMap.remove(groupedMessages.groupId);
                            } else {
                                groupedMessages.calculate();
                                int index = messages.indexOf(messageObject);
                                if (index >= 0) {
                                    if (chatAdapter != null) {
                                        chatAdapter.notifyItemRangeChanged(index + chatAdapter.messagesStartRow, updateCount);
                                    }
                                }
                            }
                        }
                    }
                }
                if (message.media.ttl_seconds != 0 && (message.media.photo instanceof TLRPC.TL_photoEmpty || message.media.document instanceof TLRPC.TL_documentEmpty)) {
                    existMessageObject.setType();
                    chatAdapter.updateRowWithMessageObject(existMessageObject);
                } else {
                    updateVisibleRows();
                }
            }
        } else if (id == NotificationCenter.replaceMessagesObjects) {
            long did = (long) args[0];
            if (did != dialog_id && did != mergeDialogId) {
                return;
            }
            int loadIndex = did == dialog_id ? 0 : 1;
            boolean changed = false;
            boolean mediaUpdated = false;
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            HashMap<Long, MessageObject.GroupedMessages> newGroups = null;
            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                MessageObject old = messagesDict[loadIndex].get(messageObject.getId());
                if (pinnedMessageObject != null && pinnedMessageObject.getId() == messageObject.getId()) {
                    pinnedMessageObject = messageObject;
                    updatePinnedMessageView(true);
                }
                if (old != null) {
                    if (messageObject.type >= 0) {
                        if (!mediaUpdated && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                            mediaUpdated = true;
                        }
                        if (old.replyMessageObject != null) {
                            messageObject.replyMessageObject = old.replyMessageObject;
                            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                                messageObject.generateGameMessageText(null);
                            } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                                messageObject.generatePaymentSentMessageText(null);
                            }
                        }
                        messageObject.messageOwner.attachPath = old.messageOwner.attachPath;
                        messageObject.attachPathExists = old.attachPathExists;
                        messageObject.mediaExists = old.mediaExists;
                        messagesDict[loadIndex].put(old.getId(), messageObject);
                    } else {
                        messagesDict[loadIndex].remove(old.getId());
                    }
                    int index = messages.indexOf(old);
                    if (index >= 0) {
                        ArrayList<MessageObject> dayArr = messagesByDays.get(old.dateKey);
                        int index2 = -1;
                        if (dayArr != null) {
                            index2 = dayArr.indexOf(old);
                        }
                        if (old.getGroupId() != 0) {
                            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(old.getGroupId());
                            if (groupedMessages != null) {
                                int idx = groupedMessages.messages.indexOf(old);
                                if (idx >= 0) {
                                    if (old.getGroupId() != messageObject.getGroupId()) {
                                        groupedMessagesMap.put(messageObject.getGroupId(), groupedMessages);
                                    }
                                    if (messageObject.photoThumbs == null || messageObject.photoThumbs.isEmpty()) {
                                        if (newGroups == null) {
                                            newGroups = new HashMap<>();
                                        }
                                        newGroups.put(groupedMessages.groupId, groupedMessages);
                                        if (idx > 0 && idx < groupedMessages.messages.size() - 1) {
                                            MessageObject.GroupedMessages slicedGroup = new MessageObject.GroupedMessages();
                                            slicedGroup.groupId = Utilities.random.nextLong();
                                            slicedGroup.messages.addAll(groupedMessages.messages.subList(idx + 1, groupedMessages.messages.size()));
                                            for (int b = 0; b < slicedGroup.messages.size(); b++) {
                                                slicedGroup.messages.get(b).localGroupId = slicedGroup.groupId;
                                                groupedMessages.messages.remove(idx + 1);
                                            }
                                            newGroups.put(slicedGroup.groupId, slicedGroup);
                                            groupedMessagesMap.put(slicedGroup.groupId, slicedGroup);
                                        }
                                        groupedMessages.messages.remove(idx);
                                    } else {
                                        groupedMessages.messages.set(idx, messageObject);
                                        MessageObject.GroupedMessagePosition oldPosition = groupedMessages.positions.remove(old);
                                        if (oldPosition != null) {
                                            groupedMessages.positions.put(messageObject, oldPosition);
                                        }
                                    }
                                }
                            }
                        }
                        if (messageObject.type >= 0) {
                            messages.set(index, messageObject);
                            if (chatAdapter != null) {
                                chatAdapter.notifyItemChanged(chatAdapter.messagesStartRow + index);
                            }
                            if (index2 >= 0) {
                                dayArr.set(index2, messageObject);
                            }
                        } else {
                            messages.remove(index);
                            if (chatAdapter != null) {
                                chatAdapter.notifyItemRemoved(chatAdapter.messagesStartRow + index);
                            }
                            if (index2 >= 0) {
                                dayArr.remove(index2);
                                if (dayArr.isEmpty()) {
                                    messagesByDays.remove(old.dateKey);
                                    messages.remove(index);
                                    chatAdapter.notifyItemRemoved(chatAdapter.messagesStartRow);
                                }
                            }
                        }
                        changed = true;
                    }
                }
            }
            if (newGroups != null) {
                for (HashMap.Entry<Long, MessageObject.GroupedMessages> entry : newGroups.entrySet()) {
                    MessageObject.GroupedMessages groupedMessages = entry.getValue();
                    if (groupedMessages.messages.isEmpty()) {
                        groupedMessagesMap.remove(groupedMessages.groupId);
                    } else {
                        groupedMessages.calculate();
                        MessageObject messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                        int index = messages.indexOf(messageObject);
                        if (index >= 0) {
                            if (chatAdapter != null) {
                                chatAdapter.notifyItemRangeChanged(index + chatAdapter.messagesStartRow, groupedMessages.messages.size());
                            }
                        }
                    }
                }
            }
            if (changed && chatLayoutManager != null) {
                if (mediaUpdated && chatLayoutManager.findFirstVisibleItemPosition() == 0) {
                    //moveScrollToLastMessage();
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
                if (chatLayoutManager != null && chatLayoutManager.findFirstVisibleItemPosition() == 0) {
                    moveScrollToLastMessage();
                }
            }
        } else if (id == NotificationCenter.didReceivedWebpagesInUpdates) {
            if (foundWebPage != null) {
                HashMap<Long, TLRPC.WebPage> hashMap = (HashMap<Long, TLRPC.WebPage>) args[0];
                for (TLRPC.WebPage webPage : hashMap.values()) {
                    if (webPage.id == foundWebPage.id) {
                        showReplyPanel(!(webPage instanceof TLRPC.TL_webPageEmpty), null, null, webPage, false);
                        break;
                    }
                }
            }
        } else if (id == NotificationCenter.messagesReadContent) {
            ArrayList<Long> arrayList = (ArrayList<Long>) args[0];
            boolean updated = false;
            int currentChannelId = ChatObject.isChannel(currentChat) ? currentChat.id : 0;
            for (int a = 0; a < arrayList.size(); a++) {
                long mid = arrayList.get(a);
                int channelId = (int) (mid >> 32);
                if (channelId < 0) {
                    channelId = 0;
                }
                if (channelId != currentChannelId) {
                    continue;
                }
                MessageObject currentMessage = messagesDict[0].get((int) mid);
                if (currentMessage != null) {
                    currentMessage.setContentIsRead();
                    updated = true;
                    if (currentMessage.messageOwner.mentioned) {
                        newMentionsCount--;
                        if (newMentionsCount <= 0) {
                            newMentionsCount = 0;
                            hasAllMentionsLocal = true;
                            showMentiondownButton(false, true);
                        } else {
                            mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                        }
                    }
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
                        chatAdapter.notifyItemChanged(chatAdapter.botInfoRow);
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
                                showReplyPanel(true, botButtons, null, null, false);
                            }
                        } else {
                            if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                                botReplyButtons = null;
                                showReplyPanel(false, null, null, null, false);
                            }
                            chatActivityEnterView.setButtons(botButtons);
                        }
                    }
                } else {
                    botButtons = null;
                    if (chatActivityEnterView != null) {
                        if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                            botReplyButtons = null;
                            showReplyPanel(false, null, null, null, false);
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
                    scrollToMessageId(messageId, 0, true, did == dialog_id ? 0 : 1, false);
                }
                updateSearchButtons((Integer) args[2], (Integer) args[4], (Integer) args[5]);
                if (searchItem != null) {
                    searchItem.setShowSearchProgress(false);
                }
            }
        } else if (id == NotificationCenter.chatSearchResultsLoading) {
            if (classGuid == (Integer) args[0] && searchItem != null) {
                searchItem.setShowSearchProgress(true);
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
        } else if (id == NotificationCenter.newDraftReceived) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                applyDraftMaybe(true);
            }
        } else if (id == NotificationCenter.userInfoDidLoaded) {
            Integer uid = (Integer) args[0];
            if (currentUser != null && currentUser.id == uid) {
                TLRPC.TL_userFull userFull = (TLRPC.TL_userFull) args[1];
                if (headerItem != null) {
                    if (userFull.phone_calls_available) {
                        headerItem.showSubItem(call);
                    } else {
                        headerItem.hideSubItem(call);
                    }
                }
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (fragmentView != null) {
                ((SizeNotifierFrameLayout) fragmentView).setBackgroundImage(Theme.getCachedWallpaper());
                progressView2.getBackground().setColorFilter(Theme.colorFilter);
                if (emptyView != null) {
                    emptyView.getBackground().setColorFilter(Theme.colorFilter);
                }
                if (bigEmptyView != null) {
                    bigEmptyView.getBackground().setColorFilter(Theme.colorFilter);
                }
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.channelRightsUpdated) {
            TLRPC.Chat chat = (TLRPC.Chat) args[0];
            if (currentChat != null && chat.id == currentChat.id && chatActivityEnterView != null) {
                currentChat = chat;
                chatActivityEnterView.checkChannelRights();
                checkRaiseSensors();
                updateSecretStatus();
            }
        } else if (id == NotificationCenter.updateMentionsCount) {
            if (dialog_id == (Long) args[0]) {
                int count = (int) args[1];
                if (newMentionsCount > count) {
                    newMentionsCount = count;
                    if (newMentionsCount <= 0) {
                        newMentionsCount = 0;
                        hasAllMentionsLocal = true;
                        showMentiondownButton(false, true);
                    } else {
                        mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                    }
                }
            }
        }
    }

    public boolean processSwitchButton(TLRPC.TL_keyboardButtonSwitchInline button) {
        if (inlineReturn == 0 || button.same_peer || parentLayout == null) {
            return false;
        }
        String query = "@" + currentUser.username + " " + button.query;
        if (inlineReturn == dialog_id) {
            inlineReturn = 0;
            chatActivityEnterView.setFieldText(query);
        } else {
            DraftQuery.saveDraft(inlineReturn, query, null, null, false);
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

    private void updateSearchButtons(int mask, int num, int count) {
        if (searchUpButton != null) {
            searchUpButton.setEnabled((mask & 1) != 0);
            searchDownButton.setEnabled((mask & 2) != 0);
            searchUpButton.setAlpha(searchUpButton.isEnabled() ? 1.0f : 0.5f);
            searchDownButton.setAlpha(searchDownButton.isEnabled() ? 1.0f : 0.5f);
            if (count < 0) {
                searchCountText.setText("");
            } else if (count == 0) {
                searchCountText.setText(LocaleController.getString("NoResult", R.string.NoResult));
            } else {
                searchCountText.setText(LocaleController.formatString("Of", R.string.Of, num + 1, count));
            }
        }
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return firstLoading;
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
            if (currentUser != null) {
                MessagesController.getInstance().loadFullUser(currentUser, classGuid, false);
            }
            if (Build.VERSION.SDK_INT >= 21) {
                createChatAttachView();
            }

            if (chatActivityEnterView.hasRecordVideo() && !chatActivityEnterView.isSendButtonVisible()) {
                boolean isChannel = false;
                if (currentChat != null) {
                    isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
                }
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                String key = isChannel ? "needShowRoundHintChannel" : "needShowRoundHint";
                if (preferences.getBoolean(key, true)) {
                    if (Utilities.random.nextFloat() < 0.2f) {
                        showVoiceHint(false, chatActivityEnterView.isInVideoMode());
                        preferences.edit().putBoolean(key, false).commit();
                    }
                }
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

    @Override
    public boolean extendActionMode(Menu menu) {
        if (chatActivityEnterView.getSelectionLength() == 0 || menu.findItem(android.R.id.copy) == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            menu.removeItem(android.R.id.shareText);
        }
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(LocaleController.getString("Bold", R.string.Bold));
        stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_bold, 6, stringBuilder);
        stringBuilder = new SpannableStringBuilder(LocaleController.getString("Italic", R.string.Italic));
        stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_italic, 7, stringBuilder);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_regular, 8, LocaleController.getString("Regular", R.string.Regular));
        return true;
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
                            showReplyPanel(false, null, null, null, false);
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

        if (searchItem != null && searchItem.getVisibility() == View.VISIBLE) {
            searchContainer.setVisibility(View.VISIBLE);
            bottomOverlayChat.setVisibility(View.INVISIBLE);
            chatActivityEnterView.setFieldFocused(false);
            chatActivityEnterView.setVisibility(View.INVISIBLE);
            if (chatActivityEnterView.isTopViewVisible()) {
                topViewWasVisible = 1;
                chatActivityEnterView.hideTopView(false);
            } else {
                topViewWasVisible = 2;
            }
        } else {
            searchContainer.setVisibility(View.INVISIBLE);
            if (currentChat != null && (ChatObject.isNotInChat(currentChat) || !ChatObject.canWriteToChat(currentChat)) ||
                    currentUser != null && (UserObject.isDeleted(currentUser) || userBlocked)) {
                if (chatActivityEnterView.isEditingMessage()) {
                    chatActivityEnterView.setVisibility(View.VISIBLE);
                    bottomOverlayChat.setVisibility(View.INVISIBLE);
                    chatActivityEnterView.setFieldFocused();
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            chatActivityEnterView.openKeyboard();
                        }
                    }, 100);
                } else {
                    bottomOverlayChat.setVisibility(View.VISIBLE);
                    chatActivityEnterView.setFieldFocused(false);
                    chatActivityEnterView.setVisibility(View.INVISIBLE);
                    chatActivityEnterView.closeKeyboard();
                }
                if (muteItem != null) {
                    muteItem.setVisibility(View.GONE);
                }
                attachItem.setVisibility(View.GONE);
                headerItem.setVisibility(View.VISIBLE);
            } else {
                if (botUser != null && currentUser.bot) {
                    bottomOverlayChat.setVisibility(View.VISIBLE);
                    chatActivityEnterView.setVisibility(View.INVISIBLE);
                } else {
                    chatActivityEnterView.setVisibility(View.VISIBLE);
                    bottomOverlayChat.setVisibility(View.INVISIBLE);
                }
                if (muteItem != null) {
                    muteItem.setVisibility(View.VISIBLE);
                }
            }
            if (topViewWasVisible == 1) {
                chatActivityEnterView.showTopView(false, false);
                topViewWasVisible = 0;
            }
        }
        checkRaiseSensors();
    }

    public void showAlert(String name, String message) {
        if (alertView == null || name == null || message == null) {
            return;
        }

        if (alertView.getTag() != null) {
            alertView.setTag(null);
            if (alertViewAnimator != null) {
                alertViewAnimator.cancel();
                alertViewAnimator = null;
            }

            alertView.setVisibility(View.VISIBLE);
            alertViewAnimator = new AnimatorSet();
            alertViewAnimator.playTogether(ObjectAnimator.ofFloat(alertView, "translationY", 0));
            alertViewAnimator.setDuration(200);
            alertViewAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                        alertViewAnimator = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                        alertViewAnimator = null;
                    }
                }
            });
            alertViewAnimator.start();
        }
        alertNameTextView.setText(name);
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
                    alertViewAnimator = new AnimatorSet();
                    alertViewAnimator.playTogether(ObjectAnimator.ofFloat(alertView, "translationY", -AndroidUtilities.dp(50)));
                    alertViewAnimator.setDuration(200);
                    alertViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                                alertView.setVisibility(View.GONE);
                                alertViewAnimator = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (alertViewAnimator != null && alertViewAnimator.equals(animation)) {
                                alertViewAnimator = null;
                            }
                        }
                    });
                    alertViewAnimator.start();
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
            if (animated) {
                pinnedMessageViewAnimator = new AnimatorSet();
                pinnedMessageViewAnimator.playTogether(ObjectAnimator.ofFloat(pinnedMessageView, "translationY", -AndroidUtilities.dp(50)));
                pinnedMessageViewAnimator.setDuration(200);
                pinnedMessageViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                            pinnedMessageView.setVisibility(View.GONE);
                            pinnedMessageViewAnimator = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                            pinnedMessageViewAnimator = null;
                        }
                    }
                });
                pinnedMessageViewAnimator.start();
            } else {
                pinnedMessageView.setTranslationY(-AndroidUtilities.dp(50));
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
        if (info == null || info.pinned_msg_id == 0 || info.pinned_msg_id == preferences.getInt("pin_" + dialog_id, 0) || actionBar != null && (actionBar.isActionModeShowed() || actionBar.isSearchFieldVisible())) {
            hidePinnedMessageView(animated);
        } else {
            if (pinnedMessageObject != null) {
                if (pinnedMessageView.getTag() != null) {
                    pinnedMessageView.setTag(null);
                    if (pinnedMessageViewAnimator != null) {
                        pinnedMessageViewAnimator.cancel();
                        pinnedMessageViewAnimator = null;
                    }
                    if (animated) {
                        pinnedMessageView.setVisibility(View.VISIBLE);
                        pinnedMessageViewAnimator = new AnimatorSet();
                        pinnedMessageViewAnimator.playTogether(ObjectAnimator.ofFloat(pinnedMessageView, "translationY", 0));
                        pinnedMessageViewAnimator.setDuration(200);
                        pinnedMessageViewAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                                    pinnedMessageViewAnimator = null;
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                if (pinnedMessageViewAnimator != null && pinnedMessageViewAnimator.equals(animation)) {
                                    pinnedMessageViewAnimator = null;
                                }
                            }
                        });
                        pinnedMessageViewAnimator.start();
                    } else {
                        pinnedMessageView.setTranslationY(0);
                        pinnedMessageView.setVisibility(View.VISIBLE);
                    }
                }
                FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) pinnedMessageNameTextView.getLayoutParams();
                FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) pinnedMessageTextView.getLayoutParams();
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs2, AndroidUtilities.dp(50));
                if (photoSize == null) {
                    photoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs, AndroidUtilities.dp(50));
                }
                if (photoSize == null || photoSize instanceof TLRPC.TL_photoSizeEmpty || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable || pinnedMessageObject.type == 13) {
                    pinnedMessageImageView.setImageBitmap(null);
                    pinnedImageLocation = null;
                    pinnedMessageImageView.setVisibility(View.INVISIBLE);
                    layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(18);
                } else {
                    if (pinnedMessageObject.isRoundVideo()) {
                        pinnedMessageImageView.setRoundRadius(AndroidUtilities.dp(16));
                    } else {
                        pinnedMessageImageView.setRoundRadius(0);
                    }
                    pinnedImageLocation = photoSize.location;
                    pinnedMessageImageView.setImage(pinnedImageLocation, "50_50", (Drawable) null);
                    pinnedMessageImageView.setVisibility(View.VISIBLE);
                    layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(55);
                }
                pinnedMessageNameTextView.setLayoutParams(layoutParams1);
                pinnedMessageTextView.setLayoutParams(layoutParams2);

                pinnedMessageNameTextView.setText(LocaleController.getString("PinnedMessage", R.string.PinnedMessage));
                if (pinnedMessageObject.type == 14) {
                    pinnedMessageTextView.setText(String.format("%s - %s", pinnedMessageObject.getMusicAuthor(), pinnedMessageObject.getMusicTitle()));
                } else if (pinnedMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    pinnedMessageTextView.setText(Emoji.replaceEmoji(pinnedMessageObject.messageOwner.media.game.title, pinnedMessageTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                } else if (pinnedMessageObject.messageText != null) {
                    String mess = pinnedMessageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    pinnedMessageTextView.setText(Emoji.replaceEmoji(mess, pinnedMessageTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                }
            } else {
                pinnedImageLocation = null;
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
            FileLog.d("no spam view found");
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        boolean show;
        if (currentEncryptedChat != null) {
            show = !(currentEncryptedChat.admin_id == UserConfig.getClientUserId() || ContactsController.getInstance().isLoadingContacts()) && ContactsController.getInstance().contactsDict.get(currentUser.id) == null;
            if (show && preferences.getInt("spam3_" + dialog_id, 0) == 1) {
                show = false;
            }
        } else {
            show = preferences.getInt("spam3_" + dialog_id, 0) == 2;
        }
        if (!show) {
            if (reportSpamView.getTag() == null) {
                FileLog.d("hide spam button");
                reportSpamView.setTag(1);

                if (reportSpamViewAnimator != null) {
                    reportSpamViewAnimator.cancel();
                }
                reportSpamViewAnimator = new AnimatorSet();
                reportSpamViewAnimator.playTogether(ObjectAnimator.ofFloat(reportSpamView, "translationY", -AndroidUtilities.dp(50)));
                reportSpamViewAnimator.setDuration(200);
                reportSpamViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                            reportSpamView.setVisibility(View.GONE);
                            reportSpamViewAnimator = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                            reportSpamViewAnimator = null;
                        }
                    }
                });
                reportSpamViewAnimator.start();
            }
        } else {
            if (reportSpamView.getTag() != null) {
                FileLog.d("show spam button");
                reportSpamView.setTag(null);
                reportSpamView.setVisibility(View.VISIBLE);
                if (reportSpamViewAnimator != null) {
                    reportSpamViewAnimator.cancel();
                }
                reportSpamViewAnimator = new AnimatorSet();
                reportSpamViewAnimator.playTogether(ObjectAnimator.ofFloat(reportSpamView, "translationY", 0));
                reportSpamViewAnimator.setDuration(200);
                reportSpamViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                            reportSpamViewAnimator = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                            reportSpamViewAnimator = null;
                        }
                    }
                });
                reportSpamViewAnimator.start();
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
                    || MessagesController.isSupportId(currentUser.id)
                    || UserObject.isDeleted(currentUser)
                    || ContactsController.getInstance().isLoadingContacts()
                    || (!TextUtils.isEmpty(currentUser.phone) && ContactsController.getInstance().contactsDict.get(currentUser.id) != null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts()))) {
                addContactItem.setVisibility(View.GONE);
            } else {
                addContactItem.setVisibility(View.VISIBLE);
                if (!TextUtils.isEmpty(currentUser.phone)) {
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
                    int firstVisPos = chatLayoutManager.findFirstVisibleItemPosition();
                    int top = 0;
                    if (firstVisPos != RecyclerView.NO_POSITION) {
                        View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                        top = ((firstVisView == null) ? 0 : chatListView.getMeasuredHeight() - firstVisView.getBottom() - chatListView.getPaddingBottom());
                    }
                    if (chatListView.getPaddingTop() != AndroidUtilities.dp(52) && (pinnedMessageView != null && pinnedMessageView.getTag() == null || reportSpamView != null && reportSpamView.getTag() == null)) {
                        chatListView.setPadding(0, AndroidUtilities.dp(52), 0, AndroidUtilities.dp(3));
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) floatingDateView.getLayoutParams();
                        layoutParams.topMargin = AndroidUtilities.dp(52);
                        floatingDateView.setLayoutParams(layoutParams);
                        chatListView.setTopGlowOffset(AndroidUtilities.dp(48));
                    } else if (chatListView.getPaddingTop() != AndroidUtilities.dp(4) && (pinnedMessageView == null || pinnedMessageView.getTag() != null) && (reportSpamView == null || reportSpamView.getTag() != null)) {
                        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) floatingDateView.getLayoutParams();
                        layoutParams.topMargin = AndroidUtilities.dp(4);
                        floatingDateView.setLayoutParams(layoutParams);
                        chatListView.setTopGlowOffset(0);
                    } else {
                        firstVisPos = RecyclerView.NO_POSITION;
                    }
                    if (firstVisPos != RecyclerView.NO_POSITION) {
                        chatLayoutManager.scrollToPositionWithOffset(firstVisPos, top);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void checkRaiseSensors() {
        if (ChatObject.isChannel(currentChat) && currentChat.banned_rights != null && currentChat.banned_rights.send_media) {
            MediaController.getInstance().setAllowStartRecord(false);
        } else if (!ApplicationLoader.mainInterfacePaused && (bottomOverlayChat == null || bottomOverlayChat.getVisibility() != View.VISIBLE) && (bottomOverlay == null || bottomOverlay.getVisibility() != View.VISIBLE) && (searchContainer == null || searchContainer.getVisibility() != View.VISIBLE)) {
            MediaController.getInstance().setAllowStartRecord(true);
        } else {
            MediaController.getInstance().setAllowStartRecord(false);
        }
    }

    @Override
    public void dismissCurrentDialig() {
        if (chatAttachAlert != null && visibleDialog == chatAttachAlert) {
            chatAttachAlert.closeCamera(false);
            chatAttachAlert.dismissInternal();
            chatAttachAlert.hideCamera(true);
            return;
        }
        super.dismissCurrentDialig();
    }

    @Override
    public void onResume() {
        super.onResume();

        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        MediaController.getInstance().startRaiseToEarSensors(this);
        checkRaiseSensors();
        if (chatAttachAlert != null) {
            chatAttachAlert.onResume();
        }

        checkActionBarMenu();
        if (replyImageLocation != null && replyImageView != null) {
            replyImageView.setImage(replyImageLocation, "50_50", (Drawable) null);
        }
        if (pinnedImageLocation != null && pinnedMessageImageView != null) {
            pinnedMessageImageView.setImage(pinnedImageLocation, "50_50", (Drawable) null);
        }

        NotificationsController.getInstance().setOpenedDialogId(dialog_id);
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && scrollToMessage != null) {
                if (chatListView != null) {
                    int yOffset;
                    boolean bottom = true;
                    if (scrollToMessagePosition == -9000) {
                        yOffset = getScrollOffsetForMessage(scrollToMessage);
                        bottom = false;
                    } else if (scrollToMessagePosition == -10000) {
                        yOffset = -chatListView.getPaddingTop() - AndroidUtilities.dp(7);
                        bottom = false;
                    } else {
                        yOffset = scrollToMessagePosition;
                    }
                    chatLayoutManager.scrollToPositionWithOffset(chatAdapter.messagesStartRow + messages.indexOf(scrollToMessage), yOffset, bottom);
                }
            } else {
                moveScrollToLastMessage();
            }
            scrollToTopUnReadOnResume = false;
            scrollToTopOnResume = false;
            scrollToMessage = null;
        }
        paused = false;
        pausedOnLastMessage = false;
        AndroidUtilities.runOnUIThread(readRunnable, 500);
        checkScrollForLoad(false);
        if (wasPaused) {
            wasPaused = false;
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }

        fixLayout();
        applyDraftMaybe(false);
        if (bottomOverlayChat != null && bottomOverlayChat.getVisibility() != View.VISIBLE) {
            chatActivityEnterView.setFieldFocused(true);
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onResume();
        }
        if (currentUser != null) {
            chatEnterTime = System.currentTimeMillis();
            chatLeaveTime = 0;
        }

        if (startVideoEdit != null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    openVideoEditor(startVideoEdit, null);
                    startVideoEdit = null;
                }
            });
        }

        if (chatListView != null && (chatActivityEnterView == null || !chatActivityEnterView.isEditingMessage())) {
            chatListView.setOnItemLongClickListener(onItemLongClickListener);
            chatListView.setOnItemClickListener(onItemClickListener);
            chatListView.setLongClickable(true);
        }
        checkBotCommands();
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.cancelRunOnUIThread(readRunnable);
        MediaController.getInstance().stopRaiseToEarSensors(this);
        paused = true;
        wasPaused = true;
        NotificationsController.getInstance().setOpenedDialogId(0);
        CharSequence draftMessage = null;
        MessageObject replyMessage = null;
        boolean searchWebpage = true;
        if (!ignoreAttachOnPause && chatActivityEnterView != null && bottomOverlayChat.getVisibility() != View.VISIBLE) {
            chatActivityEnterView.onPause();
            replyMessage = replyingMessageObject;
            if (!chatActivityEnterView.isEditingMessage()) {
                CharSequence text = AndroidUtilities.getTrimmedString(chatActivityEnterView.getFieldText());
                if (!TextUtils.isEmpty(text) && !TextUtils.equals(text, "@gif")) {
                    draftMessage = text;
                }
            }
            searchWebpage = chatActivityEnterView.isMessageWebPageSearchEnabled();
            chatActivityEnterView.setFieldFocused(false);
        }
        if (chatAttachAlert != null) {
            if (!ignoreAttachOnPause){
                chatAttachAlert.onPause();
            } else {
                ignoreAttachOnPause = false;
            }
        }
        CharSequence[] message = new CharSequence[] {draftMessage};
        ArrayList<TLRPC.MessageEntity> entities = MessagesQuery.getEntities(message);
        DraftQuery.saveDraft(dialog_id, message[0], entities, replyMessage != null ? replyMessage.messageOwner : null, !searchWebpage);

        MessagesController.getInstance().cancelTyping(0, dialog_id);

        if (!pausedOnLastMessage) {
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).edit();
            int messageId = 0;
            int offset = 0;
            if (chatLayoutManager != null) {
                int position = chatLayoutManager.findFirstVisibleItemPosition();
                if (position != 0) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        if (holder.itemView instanceof ChatMessageCell) {
                            messageId = ((ChatMessageCell) holder.itemView).getMessageObject().getId();
                        } else if (holder.itemView instanceof ChatActionCell) {
                            messageId = ((ChatActionCell) holder.itemView).getMessageObject().getId();
                        }
                        if (messageId != 0) {
                            offset = holder.itemView.getBottom() - chatListView.getMeasuredHeight();
                            FileLog.d("save offset = " + offset + " for mid " + messageId);
                        }
                    }
                }
            }
            if (messageId != 0) {
                editor.putInt("diditem" + dialog_id, messageId);
                editor.putInt("diditemo" + dialog_id, offset);
            } else {
                pausedOnLastMessage = true;
                editor.remove("diditem" + dialog_id);
                editor.remove("diditemo" + dialog_id);
            }
            editor.commit();
        }

        if (currentUser != null) {
            chatLeaveTime = System.currentTimeMillis();
            updateInformationForScreenshotDetector();
        }
    }

    private void applyDraftMaybe(boolean canClear) {
        if (chatActivityEnterView == null) {
            return;
        }
        TLRPC.DraftMessage draftMessage = DraftQuery.getDraft(dialog_id);
        TLRPC.Message draftReplyMessage = draftMessage != null && draftMessage.reply_to_msg_id != 0 ? DraftQuery.getDraftMessage(dialog_id) : null;
        if (chatActivityEnterView.getFieldText() == null) {
            if (draftMessage != null) {
                chatActivityEnterView.setWebPage(null, !draftMessage.no_webpage);
                CharSequence message;
                if (!draftMessage.entities.isEmpty()) {
                    SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(draftMessage.message);
                    MessagesQuery.sortEntities(draftMessage.entities);
                    int addToOffset = 0;
                    for (int a = 0; a < draftMessage.entities.size(); a++) {
                        TLRPC.MessageEntity entity = draftMessage.entities.get(a);
                        if (entity instanceof TLRPC.TL_inputMessageEntityMentionName || entity instanceof TLRPC.TL_messageEntityMentionName) {
                            int user_id;
                            if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                                user_id = ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id;
                            } else {
                                user_id = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
                            }
                            if (entity.offset + addToOffset + entity.length < stringBuilder.length() && stringBuilder.charAt(entity.offset + addToOffset + entity.length) == ' ') {
                                entity.length++;
                            }
                            stringBuilder.setSpan(new URLSpanUserMention("" + user_id, true), entity.offset + addToOffset, entity.offset + addToOffset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (entity instanceof TLRPC.TL_messageEntityCode) {
                            stringBuilder.insert(entity.offset + entity.length + addToOffset, "`");
                            stringBuilder.insert(entity.offset + addToOffset, "`");
                            addToOffset += 2;
                        } else if (entity instanceof TLRPC.TL_messageEntityPre) {
                            stringBuilder.insert(entity.offset + entity.length + addToOffset, "```");
                            stringBuilder.insert(entity.offset + addToOffset, "```");
                            addToOffset += 6;
                        } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                            stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                            stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    message = stringBuilder;
                } else {
                    message = draftMessage.message;
                }
                chatActivityEnterView.setFieldText(message);
                if (getArguments().getBoolean("hasUrl", false)) {
                    chatActivityEnterView.setSelection(draftMessage.message.indexOf('\n') + 1);
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
        } else if (canClear && draftMessage == null) {
            chatActivityEnterView.setFieldText("");
            showReplyPanel(false, null, null, null, false);
        }
        if (replyingMessageObject == null && draftReplyMessage != null) {
            replyingMessageObject = new MessageObject(draftReplyMessage, MessagesController.getInstance().getUsers(), false);
            showReplyPanel(true, replyingMessageObject, null, null, false);
        }
    }

    private void updateInformationForScreenshotDetector() {
        if (currentUser == null) {
            return;
        }
        ArrayList<Long> visibleMessages;
        int messageId = 0;
        if (currentEncryptedChat != null) {
            visibleMessages = new ArrayList<>();
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    MessageObject object = null;
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        object = cell.getMessageObject();
                    }
                    if (object != null && object.getId() < 0 && object.messageOwner.random_id != 0) {
                        visibleMessages.add(object.messageOwner.random_id);
                    }
                }
            }
            MediaController.getInstance().setLastVisibleMessageIds(chatEnterTime, chatLeaveTime, currentUser, currentEncryptedChat, visibleMessages, messageId);
        } else {
            SecretMediaViewer viewer = SecretMediaViewer.getInstance();
            MessageObject messageObject = viewer.getCurrentMessageObject();
            if (messageObject != null && !messageObject.isOut()) {
                MediaController.getInstance().setLastVisibleMessageIds(viewer.getOpenTime(), viewer.getCloseTime(), currentUser, null, null, messageObject.getId());
            }
        }
    }

    private boolean fixLayoutInternal() {
        if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            selectedMessagesCountTextView.setTextSize(18);
        } else {
            selectedMessagesCountTextView.setTextSize(20);
        }

        HashMap<Long, MessageObject.GroupedMessages> newGroups = null;
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = chatListView.getChildAt(a);
            if (child instanceof ChatMessageCell) {
                MessageObject.GroupedMessages groupedMessages = ((ChatMessageCell) child).getCurrentMessagesGroup();
                if (groupedMessages != null && groupedMessages.hasSibling) {
                    if (newGroups == null) {
                        newGroups = new HashMap<>();
                    }
                    if (!newGroups.containsKey(groupedMessages.groupId)) {
                        newGroups.put(groupedMessages.groupId, groupedMessages);

                        MessageObject messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                        int idx = messages.indexOf(messageObject);
                        if (idx >= 0) {
                            chatAdapter.notifyItemRangeChanged(idx + chatAdapter.messagesStartRow, groupedMessages.messages.size());
                        }
                    }
                }
            }
        }

        if (AndroidUtilities.isTablet()) {
            if (AndroidUtilities.isSmallTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                actionBar.setBackButtonDrawable(new BackDrawable(false));
                if (fragmentContextView != null && fragmentContextView.getParent() == null) {
                    ((ViewGroup) fragmentView).addView(fragmentContextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
                }
            } else {
                actionBar.setBackButtonDrawable(new BackDrawable(parentLayout == null || parentLayout.fragmentsStack.isEmpty() || parentLayout.fragmentsStack.get(0) == ChatActivity.this || parentLayout.fragmentsStack.size() == 1));
                if (fragmentContextView != null && fragmentContextView.getParent() != null) {
                    fragmentView.setPadding(0, 0, 0, 0);
                    ((ViewGroup) fragmentView).removeView(fragmentContextView);
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
        if (visibleDialog instanceof DatePickerDialog) {
            visibleDialog.dismiss();
        }
    }

    private void createDeleteMessagesAlert(final MessageObject finalSelectedObject, final MessageObject.GroupedMessages selectedGroup) {
        createDeleteMessagesAlert(finalSelectedObject, selectedGroup, 1);
    }

    private void createDeleteMessagesAlert(final MessageObject finalSelectedObject, final MessageObject.GroupedMessages finalSelectedGroup, int loadParticipant) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        int count;
        if (finalSelectedGroup != null) {
            count = finalSelectedGroup.messages.size();
        } else if (finalSelectedObject != null) {
            count = 1;
        } else {
            count = selectedMessagesIds[0].size() + selectedMessagesIds[1].size();
        }
        builder.setMessage(LocaleController.formatString("AreYouSureDeleteMessages", R.string.AreYouSureDeleteMessages, LocaleController.formatPluralString("messages", count)));
        builder.setTitle(LocaleController.getString("Message", R.string.Message));

        final boolean[] checks = new boolean[3];
        final boolean[] deleteForAll = new boolean[1];
        TLRPC.User user = null;
        if (currentChat != null && currentChat.megagroup) {
            boolean hasOutgoing = false;
            boolean canBan = ChatObject.canBlockUsers(currentChat);
            int currentDate = ConnectionsManager.getInstance().getCurrentTime();
            if (finalSelectedObject != null) {
                if (finalSelectedObject.messageOwner.action == null || finalSelectedObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty || finalSelectedObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    user = MessagesController.getInstance().getUser(finalSelectedObject.messageOwner.from_id);
                }
                hasOutgoing = !finalSelectedObject.isSendError() && finalSelectedObject.getDialogId() == mergeDialogId && (finalSelectedObject.messageOwner.action == null || finalSelectedObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) && finalSelectedObject.isOut() && (currentDate - finalSelectedObject.messageOwner.date) <= 2 * 24 * 60 * 60;
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
                boolean exit = false;
                for (int a = 1; a >= 0; a--) {
                    for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesIds[a].entrySet()) {
                        MessageObject msg = entry.getValue();
                        if (a == 1) {
                            if (msg.isOut() && msg.messageOwner.action == null) {
                                if ((currentDate - msg.messageOwner.date) <= 2 * 24 * 60 * 60) {
                                    hasOutgoing = true;
                                }
                            } else {
                                hasOutgoing = false;
                                exit = true;
                                break;
                            }
                        } else if (a == 0) {
                            if (!msg.isOut()) {
                                hasOutgoing = false;
                                exit = true;
                                break;
                            }
                        }
                    }
                    if (exit) {
                        break;
                    }
                }
                if (from_id != -1) {
                    user = MessagesController.getInstance().getUser(from_id);
                }
            }
            if (user != null && user.id != UserConfig.getClientUserId() && loadParticipant != 2) {
                if (loadParticipant == 1 && !currentChat.creator) {
                    final AlertDialog progressDialog[] = new AlertDialog[] {new AlertDialog(getParentActivity(), 1)};

                    TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
                    req.channel = MessagesController.getInputChannel(currentChat);
                    req.user_id = MessagesController.getInputUser(user);
                    int requestId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(final TLObject response, TLRPC.TL_error error) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        progressDialog[0].dismiss();
                                    } catch (Throwable ignore) {

                                    }
                                    progressDialog[0] = null;
                                    int loadType = 2;
                                    if (response != null) {
                                        TLRPC.TL_channels_channelParticipant participant = (TLRPC.TL_channels_channelParticipant) response;
                                        if (!(participant.participant instanceof TLRPC.TL_channelParticipantAdmin || participant.participant instanceof TLRPC.TL_channelParticipantCreator)) {
                                            loadType = 0;
                                        }
                                    }
                                    createDeleteMessagesAlert(finalSelectedObject, finalSelectedGroup, loadType);
                                }
                            });
                        }
                    });
                    if (requestId != 0) {
                        final int reqId = requestId;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (progressDialog[0] == null) {
                                    return;
                                }
                                progressDialog[0].setMessage(LocaleController.getString("Loading", R.string.Loading));
                                progressDialog[0].setCanceledOnTouchOutside(false);
                                progressDialog[0].setCancelable(false);
                                progressDialog[0].setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ConnectionsManager.getInstance().cancelRequest(reqId, true);
                                        try {
                                            dialog.dismiss();
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                });
                                showDialog(progressDialog[0]);
                            }
                        }, 1000);
                    }
                    return;
                }
                FrameLayout frameLayout = new FrameLayout(getParentActivity());
                int num = 0;
                for (int a = 0; a < 3; a++) {
                    if (!canBan && a == 0) {
                        continue;
                    }
                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), true);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setTag(a);
                    if (a == 0) {
                        cell.setText(LocaleController.getString("DeleteBanUser", R.string.DeleteBanUser), "", false, false);
                    } else if (a == 1) {
                        cell.setText(LocaleController.getString("DeleteReportSpam", R.string.DeleteReportSpam), "", false, false);
                    } else if (a == 2) {
                        cell.setText(LocaleController.formatString("DeleteAllFrom", R.string.DeleteAllFrom, ContactsController.formatName(user.first_name, user.last_name)), "", false, false);
                    }
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                    frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 48 * num, 0, 0));
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!v.isEnabled()) {
                                return;
                            }
                            CheckBoxCell cell = (CheckBoxCell) v;
                            Integer num = (Integer) cell.getTag();
                            checks[num] = !checks[num];
                            cell.setChecked(checks[num], true);
                        }
                    });
                    num++;
                }
                builder.setView(frameLayout);
            } else if (hasOutgoing) {
                FrameLayout frameLayout = new FrameLayout(getParentActivity());
                CheckBoxCell cell = new CheckBoxCell(getParentActivity(), true);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                if (currentChat != null) {
                    cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                } else {
                    cell.setText(LocaleController.formatString("DeleteForUser", R.string.DeleteForUser, UserObject.getFirstName(currentUser)), "", false, false);
                }
                cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        deleteForAll[0] = !deleteForAll[0];
                        cell.setChecked(deleteForAll[0], true);
                    }
                });
                builder.setView(frameLayout);
            } else {
                user = null;
            }
        } else if (!ChatObject.isChannel(currentChat) && currentEncryptedChat == null) {
            boolean hasOutgoing = false;
            int currentDate = ConnectionsManager.getInstance().getCurrentTime();
            if (currentUser != null && currentUser.id != UserConfig.getClientUserId() && !currentUser.bot || currentChat != null) {
                if (finalSelectedObject != null) {
                    hasOutgoing = !finalSelectedObject.isSendError() && (finalSelectedObject.messageOwner.action == null || finalSelectedObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) && (finalSelectedObject.isOut() || currentChat != null && (currentChat.creator || currentChat.admin && currentChat.admins_enabled)) && (currentDate - finalSelectedObject.messageOwner.date) <= 2 * 24 * 60 * 60;
                } else {
                    boolean exit = false;
                    for (int a = 1; a >= 0; a--) {
                        int channelId = 0;
                        for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesIds[a].entrySet()) {
                            MessageObject msg = entry.getValue();
                            if (msg.messageOwner.action != null) {
                                continue;
                            }
                            if (msg.isOut() || currentChat != null && (currentChat.creator || currentChat.admin && currentChat.admins_enabled)) {
                                if (!hasOutgoing && (currentDate - msg.messageOwner.date) <= 2 * 24 * 60 * 60) {
                                    hasOutgoing = true;
                                }
                            } else {
                                exit = true;
                                hasOutgoing = false;
                                break;
                            }
                        }
                        if (exit) {
                            break;
                        }
                    }
                }
            }
            if (hasOutgoing) {
                FrameLayout frameLayout = new FrameLayout(getParentActivity());
                CheckBoxCell cell = new CheckBoxCell(getParentActivity(), true);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                if (currentChat != null) {
                    cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                } else {
                    cell.setText(LocaleController.formatString("DeleteForUser", R.string.DeleteForUser, UserObject.getFirstName(currentUser)), "", false, false);
                }
                cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        deleteForAll[0] = !deleteForAll[0];
                        cell.setChecked(deleteForAll[0], true);
                    }
                });
                builder.setView(frameLayout);
            }
        }
        final TLRPC.User userFinal = user;
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ArrayList<Integer> ids = null;
                if (finalSelectedObject != null) {
                    ids = new ArrayList<>();
                    ArrayList<Long> random_ids = null;
                    if (finalSelectedGroup != null) {
                        for (int a = 0; a < finalSelectedGroup.messages.size(); a++) {
                            MessageObject messageObject = finalSelectedGroup.messages.get(a);
                            ids.add(messageObject.getId());
                            if (currentEncryptedChat != null && messageObject.messageOwner.random_id != 0 && messageObject.type != 10) {
                                if (random_ids == null) {
                                    random_ids = new ArrayList<>();
                                }
                                random_ids.add(messageObject.messageOwner.random_id);
                            }
                        }
                    } else {
                        ids.add(finalSelectedObject.getId());
                        if (currentEncryptedChat != null && finalSelectedObject.messageOwner.random_id != 0 && finalSelectedObject.type != 10) {
                            random_ids = new ArrayList<>();
                            random_ids.add(finalSelectedObject.messageOwner.random_id);
                        }
                    }
                    MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat, finalSelectedObject.messageOwner.to_id.channel_id, deleteForAll[0]);
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
                        MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat, channelId, deleteForAll[0]);
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

    private void createMenu(View v, boolean single, boolean listView) {
        createMenu(v, single, listView, true);
    }

    private void createMenu(View v, boolean single, boolean listView, boolean searchGroup) {
        if (actionBar.isActionModeShowed()) {
            return;
        }

        MessageObject message = null;
        if (v instanceof ChatMessageCell) {
            message = ((ChatMessageCell) v).getMessageObject();
        } else if (v instanceof ChatActionCell) {
            message = ((ChatActionCell) v).getMessageObject();
        }
        if (message == null) {
            return;
        }
        final int type = getMessageType(message);
        if (single) {
            if (message.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                scrollToMessageId(message.messageOwner.reply_to_msg_id, message.messageOwner.id, true, 0, false);
                return;
            }
        }

        selectedObject = null;
        selectedObjectGroup = null;
        forwardingMessage = null;
        forwardingMessageGroup = null;
        for (int a = 1; a >= 0; a--) {
            selectedMessagesCanCopyIds[a].clear();
            selectedMessagesCanStarIds[a].clear();
            selectedMessagesIds[a].clear();
        }
        cantDeleteMessagesCount = 0;
        canEditMessagesCount = 0;
        actionBar.hideActionMode();
        updatePinnedMessageView(true);

        MessageObject.GroupedMessages groupedMessages;
        if (searchGroup) {
            groupedMessages = getValidGroupedMessage(message);
        } else {
            groupedMessages = null;
        }

        boolean allowChatActions = true;
        boolean allowPin = message.getDialogId() != mergeDialogId && message.getId() > 0 && ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.admin_rights != null && (currentChat.megagroup && currentChat.admin_rights.pin_messages || !currentChat.megagroup && currentChat.admin_rights.edit_messages)) && (message.messageOwner.action == null || message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
        boolean allowUnpin = message.getDialogId() != mergeDialogId && info != null && info.pinned_msg_id == message.getId() && (currentChat.creator || currentChat.admin_rights != null && (currentChat.megagroup && currentChat.admin_rights.pin_messages || !currentChat.megagroup && currentChat.admin_rights.edit_messages));
        boolean allowEdit = groupedMessages == null && message.canEditMessage(currentChat) && !chatActivityEnterView.hasAudioToSend() && message.getDialogId() != mergeDialogId;
        if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 ||
                type == 1 && (message.getDialogId() == mergeDialogId || message.isSecretPhoto()) ||
                currentEncryptedChat == null && message.getId() < 0 ||
                bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE ||
                isBroadcast ||
                currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !ChatObject.canPost(currentChat) && !currentChat.megagroup || !ChatObject.canSendMessages(currentChat))) {
            allowChatActions = false;
        }

        if (single || type < 2 || type == 20 || message.isSecretPhoto() || message.isLiveLocation()) {
            if (type >= 0) {
                selectedObject = message;
                selectedObjectGroup = groupedMessages;
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
                        if (message.messageOwner.action != null && message.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                            TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) message.messageOwner.action;
                            items.add((call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) && !message.isOutOwner() ? LocaleController.getString("CallBack", R.string.CallBack) : LocaleController.getString("CallAgain", R.string.CallAgain));
                            options.add(18);
                            if(VoIPHelper.canRateCall(call)){
                                items.add(LocaleController.getString("CallMessageReportProblem", R.string.CallMessageReportProblem));
                                options.add(19);
                            }
                        }
                        if (single && selectedObject.getId() > 0 && allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                        }
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
                        if (ChatObject.isChannel(currentChat) && currentChat.megagroup && !TextUtils.isEmpty(currentChat.username) && ChatObject.hasAdminRights(currentChat)) {
                            items.add(LocaleController.getString("CopyLink", R.string.CopyLink));
                            options.add(22);
                        }
                        if (type == 3) {
                            if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && MessageObject.isNewGifDocument(selectedObject.messageOwner.media.webpage.document)) {
                                items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                                options.add(11);
                            }
                        } else if (type == 4) {
                            if (selectedObject.isVideo()) {
                                if (!selectedObject.isSecretPhoto()) {
                                    items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                    options.add(4);
                                    items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                    options.add(6);
                                }
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
                                if (!selectedObject.isSecretPhoto()) {
                                    items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                    options.add(4);
                                }
                            }
                        } else if (type == 5) {
                            items.add(LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile));
                            options.add(5);
                            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                            options.add(10);
                            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                            options.add(6);
                        } else if (type == 10) {
                            items.add(LocaleController.getString("ApplyThemeFile", R.string.ApplyThemeFile));
                            options.add(5);
                            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                            options.add(10);
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
                            if (selectedObject.isMask()) {
                                items.add(LocaleController.getString("AddToMasks", R.string.AddToMasks));
                                options.add(9);
                            } else {
                                items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
                                options.add(9);
                                if (!StickersQuery.isStickerInFavorites(selectedObject.getDocument())) {
                                    if (StickersQuery.canAddStickerToFavorites()) {
                                        items.add(LocaleController.getString("AddToFavorites", R.string.AddToFavorites));
                                        options.add(20);
                                    }
                                } else {
                                    items.add(LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites));
                                    options.add(21);
                                }
                            }
                        } else if (type == 8) {
                            TLRPC.User user = MessagesController.getInstance().getUser(selectedObject.messageOwner.media.user_id);
                            if (user != null && user.id != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(user.id) == null) {
                                items.add(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
                                options.add(15);
                            }
                            if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
                                items.add(LocaleController.getString("Copy", R.string.Copy));
                                options.add(16);
                                items.add(LocaleController.getString("Call", R.string.Call));
                                options.add(17);
                            }
                        } else if (type == 9) {
                            if (!StickersQuery.isStickerInFavorites(selectedObject.getDocument())) {
                                items.add(LocaleController.getString("AddToFavorites", R.string.AddToFavorites));
                                options.add(20);
                            } else {
                                items.add(LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites));
                                options.add(21);
                            }
                        }
                        if (!selectedObject.isSecretPhoto() && !selectedObject.isLiveLocation()) {
                            items.add(LocaleController.getString("Forward", R.string.Forward));
                            options.add(2);
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
                        } else if (type == 10) {
                            items.add(LocaleController.getString("ApplyThemeFile", R.string.ApplyThemeFile));
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

        actionBar.showActionMode();
        updatePinnedMessageView(true);

        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int a = 0; a < actionModeViews.size(); a++) {
            View view = actionModeViews.get(a);
            view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
            AndroidUtilities.clearDrawableAnimation(view);
            animators.add(ObjectAnimator.ofFloat(view, "scaleY", 0.1f, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.setDuration(250);
        animatorSet.start();

        addToSelectedMessages(message, listView);

        selectedMessagesCountTextView.setNumber(selectedMessagesIds[0].size() + selectedMessagesIds[1].size(), false);
        updateVisibleRows();
    }

    private void startEditingMessageObject(MessageObject messageObject) {
        if (messageObject == null || getParentActivity() == null) {
            return;
        }
        if (searchItem != null && actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
            chatActivityEnterView.setFieldFocused();
        }

        mentionsAdapter.setNeedBotContext(false);
        chatListView.setOnItemLongClickListener(null);
        chatListView.setOnItemClickListener((RecyclerListView.OnItemClickListenerExtended) null);
        chatListView.setClickable(false);
        chatListView.setLongClickable(false);
        chatActivityEnterView.setEditingMessageObject(messageObject, !messageObject.isMediaEmpty());
        updateBottomOverlay();
        if (chatActivityEnterView.isEditingCaption()) {
            mentionsAdapter.setAllowNewMentions(false);
        }
        actionModeTitleContainer.setVisibility(View.VISIBLE);
        selectedMessagesCountTextView.setVisibility(View.GONE);
        checkEditTimer();

        chatActivityEnterView.setAllowStickersAndGifs(false, false);
        final ActionBarMenu actionMode = actionBar.createActionMode();
        actionMode.getItem(reply).setVisibility(View.GONE);
        actionMode.getItem(copy).setVisibility(View.GONE);
        actionMode.getItem(forward).setVisibility(View.GONE);
        actionMode.getItem(delete).setVisibility(View.GONE);
        actionMode.getItem(edit).setVisibility(View.GONE);

        actionBar.showActionMode();
        updatePinnedMessageView(true);
        updateVisibleRows();

        TLRPC.TL_messages_getMessageEditData req = new TLRPC.TL_messages_getMessageEditData();
        req.peer = MessagesController.getInputPeer((int) dialog_id);
        req.id = messageObject.getId();
        editingMessageObjectReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        editingMessageObjectReqId = 0;
                        if (response == null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.getString("EditMessageError", R.string.EditMessageError));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            showDialog(builder.create());

                            if (chatActivityEnterView != null) {
                                chatActivityEnterView.setEditingMessageObject(null, false);
                            }
                        } else {
                            if (chatActivityEnterView != null) {
                                chatActivityEnterView.showEditDoneProgress(false, true);
                            }
                        }
                    }
                });
            }
        });
    }

    private String getMessageContent(MessageObject messageObject, int previousUid, boolean name) {
        String str = "";
        if (name) {
            if (previousUid != messageObject.messageOwner.from_id) {
                if (messageObject.messageOwner.from_id > 0) {
                    TLRPC.User user = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
                    if (user != null) {
                        str = ContactsController.formatName(user.first_name, user.last_name) + ":\n";
                    }
                } else if (messageObject.messageOwner.from_id < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(-messageObject.messageOwner.from_id);
                    if (chat != null) {
                        str = chat.title + ":\n";
                    }
                }
            }
        }
        if (messageObject.type == 0 && messageObject.messageOwner.message != null) {
            str += messageObject.messageOwner.message;
        } else if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.caption != null) {
            str += messageObject.messageOwner.media.caption;
        } else {
            str += messageObject.messageText;
        }
        return str;
    }

    private void saveMessageToGallery(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getPathToMessage(messageObject.messageOwner).toString();
        }
        MediaController.saveFile(path, getParentActivity(), messageObject.type == 3 ? 1 : 0, null, null);
    }

    private void processSelectedOption(int option) {
        if (selectedObject == null) {
            return;
        }
        switch (option) {
            case 0: {
                if (selectedObjectGroup != null) {
                    boolean success = true;
                    for (int a = 0; a < selectedObjectGroup.messages.size(); a++) {
                        if (!SendMessagesHelper.getInstance().retrySendMessage(selectedObjectGroup.messages.get(a), false)) {
                            success = false;
                        }
                    }
                    if (success) {
                        moveScrollToLastMessage();
                    }
                } else {
                    if (SendMessagesHelper.getInstance().retrySendMessage(selectedObject, false)) {
                        moveScrollToLastMessage();
                    }
                }
                break;
            }
            case 1: {
                if (getParentActivity() == null) {
                    selectedObject = null;
                    selectedObjectGroup = null;
                    return;
                }
                createDeleteMessagesAlert(selectedObject, selectedObjectGroup);
                break;
            }
            case 2: {
                forwardingMessage = selectedObject;
                forwardingMessageGroup = selectedObjectGroup;
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 3);
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate(this);
                presentFragment(fragment);
                break;
            }
            case 3: {
                AndroidUtilities.addToClipboard(getMessageContent(selectedObject, 0, false));
                break;
            }
            case 4: {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    selectedObjectGroup = null;
                    return;
                }
                if (selectedObjectGroup != null) {
                    for (int a = 0; a < selectedObjectGroup.messages.size(); a++) {
                        saveMessageToGallery(selectedObjectGroup.messages.get(a));
                    }
                } else {
                    saveMessageToGallery(selectedObject);
                }
                break;
            }
            case 5: {
                File locFile = null;
                if (!TextUtils.isEmpty(selectedObject.messageOwner.attachPath)) {
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
                    if (locFile.getName().endsWith("attheme")) {
                        if (chatLayoutManager != null) {
                            int lastPosition = chatLayoutManager.findFirstVisibleItemPosition();
                            if (lastPosition != 0) {
                                scrollToPositionOnRecreate = lastPosition;
                                RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                                if (holder != null) {
                                    scrollToOffsetOnRecreate = chatListView.getMeasuredHeight() - holder.itemView.getBottom() - chatListView.getPaddingBottom();
                                } else {
                                    scrollToPositionOnRecreate = -1;
                                }
                            } else {
                                scrollToPositionOnRecreate = -1;
                            }
                        }

                        Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, selectedObject.getDocumentName(), true);
                        if (themeInfo != null) {
                            presentFragment(new ThemePreviewActivity(locFile, themeInfo));
                        } else {
                            scrollToPositionOnRecreate = -1;
                            if (getParentActivity() == null) {
                                selectedObject = null;
                                selectedObjectGroup = null;
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.getString("IncorrectTheme", R.string.IncorrectTheme));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            showDialog(builder.create());
                        }
                    } else {
                        if (LocaleController.getInstance().applyLanguageFile(locFile)) {
                            presentFragment(new LanguageSelectActivity());
                        } else {
                            if (getParentActivity() == null) {
                                selectedObject = null;
                                selectedObjectGroup = null;
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.getString("IncorrectLocalization", R.string.IncorrectLocalization));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            showDialog(builder.create());
                        }
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
                File f = new File(path);
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", f));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    }
                } else {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                }
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
                    selectedObjectGroup = null;
                    return;
                }
                MediaController.saveFile(path, getParentActivity(), 0, null, null);
                break;
            }
            case 8: {
                showReplyPanel(true, selectedObject, null, null, false);
                break;
            }
            case 9: {
                showDialog(new StickersAlert(getParentActivity(), this, selectedObject.getInputStickerSet(), null, bottomOverlayChat.getVisibility() != View.VISIBLE && ChatObject.canSendStickers(currentChat) ? chatActivityEnterView : null));
                break;
            }
            case 10: {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    selectedObjectGroup = null;
                    return;
                }
                String fileName = FileLoader.getDocumentFileName(selectedObject.getDocument());
                if (TextUtils.isEmpty(fileName)) {
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
                TLRPC.Document document = selectedObject.getDocument();
                MessagesController.getInstance().saveGif(document);
                showGifHint();
                chatActivityEnterView.addRecentGif(document);
                break;
            }
            case 12: {
                startEditingMessageObject(selectedObject);
                selectedObject = null;
                selectedObjectGroup = null;
                break;
            }
            case 13: {
                final int mid = selectedObject.getId();
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                final boolean[] checks;
                if (ChatObject.isChannel(currentChat) && currentChat.megagroup) {
                    builder.setMessage(LocaleController.getString("PinMessageAlert", R.string.PinMessageAlert));
                    checks = new boolean[]{true};
                    FrameLayout frameLayout = new FrameLayout(getParentActivity());
                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), true);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
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
                } else {
                    builder.setMessage(LocaleController.getString("PinMessageAlertChannel", R.string.PinMessageAlertChannel));
                    checks = new boolean[]{false};
                }
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
                AndroidUtilities.addToClipboard(selectedObject.messageOwner.media.phone_number);
                break;
            }
            case 17: {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + selectedObject.messageOwner.media.phone_number));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getParentActivity().startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case 18: {
                if (currentUser != null) {
                    VoIPHelper.startCall(currentUser, getParentActivity(), MessagesController.getInstance().getUserFull(currentUser.id));
                }
                break;
            }
            case 19: {
                VoIPHelper.showRateAlert(getParentActivity(), (TLRPC.TL_messageActionPhoneCall) selectedObject.messageOwner.action);
                break;
            }
            case 20: {
                StickersQuery.addRecentSticker(StickersQuery.TYPE_FAVE, selectedObject.getDocument(), (int) (System.currentTimeMillis() / 1000), false);
                break;
            }
            case 21: {
                StickersQuery.addRecentSticker(StickersQuery.TYPE_FAVE, selectedObject.getDocument(), (int) (System.currentTimeMillis() / 1000), true);
                break;
            }
            case 22: {
                TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
                req.id = selectedObject.getId();
                req.channel = MessagesController.getInputChannel(currentChat);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (response != null) {
                                    TLRPC.TL_exportedMessageLink exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                                    try {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", exportedMessageLink.link);
                                        clipboard.setPrimaryClip(clip);
                                        Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }

                            }
                        });
                    }
                });
                break;
            }
        }
        selectedObject = null;
        selectedObjectGroup = null;
    }

    @Override
    public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
        if (forwardingMessage == null && selectedMessagesIds[0].isEmpty() && selectedMessagesIds[1].isEmpty()) {
            return;
        }
        ArrayList<MessageObject> fmessages = new ArrayList<>();
        if (forwardingMessage != null) {
            if (forwardingMessageGroup != null) {
                fmessages.addAll(forwardingMessageGroup.messages);
            } else {
                fmessages.add(forwardingMessage);
            }
            forwardingMessage = null;
            forwardingMessageGroup = null;
        } else {
            for (int a = 1; a >= 0; a--) {
                ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds[a].keySet());
                Collections.sort(ids);
                for (int b = 0; b < ids.size(); b++) {
                    Integer id = ids.get(b);
                    MessageObject messageObject = selectedMessagesIds[a].get(id);
                    if (messageObject != null && id > 0) {
                        fmessages.add(messageObject);
                    }
                }
                selectedMessagesCanCopyIds[a].clear();
                selectedMessagesCanStarIds[a].clear();
                selectedMessagesIds[a].clear();
            }
            cantDeleteMessagesCount = 0;
            canEditMessagesCount = 0;
            actionBar.hideActionMode();
            updatePinnedMessageView(true);
        }

        if (dids.size() > 1 || dids.get(0) == UserConfig.getClientUserId() || message != null) {
            for (int a = 0; a < dids.size(); a++) {
                long did = dids.get(a);
                if (message != null) {
                    SendMessagesHelper.getInstance().sendMessage(message.toString(), did, null, null, true, null, null, null);
                }
                SendMessagesHelper.getInstance().sendMessage(fmessages, did);
            }
            fragment.finishFragment();
        } else {
            long did = dids.get(0);
            if (did != dialog_id) {
                int lower_part = (int) did;
                int high_part = (int) (did >> 32);
                Bundle args = new Bundle();
                args.putBoolean("scrollToTopOnResume", scrollToTopOnResume);
                if (lower_part != 0) {
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args.putInt("chat_id", -lower_part);
                    }
                } else {
                    args.putInt("enc_id", high_part);
                }
                if (lower_part != 0) {
                    if (!MessagesController.checkCanOpenChat(args, fragment)) {
                        return;
                    }
                }
                ChatActivity chatActivity = new ChatActivity(args);
                if (presentFragment(chatActivity, true)) {
                    chatActivity.showReplyPanel(true, null, fmessages, null, false);
                    if (!AndroidUtilities.isTablet()) {
                        removeSelfFromStack();
                    }
                } else {
                    fragment.finishFragment();
                }
            } else {
                fragment.finishFragment();
                moveScrollToLastMessage();
                showReplyPanel(true, null, fmessages, null, false);
                if (AndroidUtilities.isTablet()) {
                    actionBar.hideActionMode();
                    updatePinnedMessageView(true);
                }
                updateVisibleRows();
            }
        }
    }

    public boolean checkRecordLocked() {
        if (chatActivityEnterView != null && chatActivityEnterView.isRecordLocked()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            if (chatActivityEnterView.isInVideoMode()) {
                builder.setTitle(LocaleController.getString("DiscardVideoMessageTitle", R.string.DiscardVideoMessageTitle));
                builder.setMessage(LocaleController.getString("DiscardVideoMessageDescription", R.string.DiscardVideoMessageDescription));
            } else {
                builder.setTitle(LocaleController.getString("DiscardVoiceMessageTitle", R.string.DiscardVoiceMessageTitle));
                builder.setMessage(LocaleController.getString("DiscardVoiceMessageDescription", R.string.DiscardVoiceMessageDescription));
            }
            builder.setPositiveButton(LocaleController.getString("DiscardVoiceMessageAction", R.string.DiscardVoiceMessageAction), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.cancelRecordingAudioVideo();
                    }
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
            return true;
        }
        return false;
    }

    @Override
    public boolean onBackPressed() {
        if (checkRecordLocked()) {
            return false;
        } else if (actionBar != null && actionBar.isActionModeShowed()) {
            for (int a = 1; a >= 0; a--) {
                selectedMessagesIds[a].clear();
                selectedMessagesCanCopyIds[a].clear();
                selectedMessagesCanStarIds[a].clear();
            }
            chatActivityEnterView.setEditingMessageObject(null, false);
            actionBar.hideActionMode();
            updatePinnedMessageView(true);
            cantDeleteMessagesCount = 0;
            canEditMessagesCount = 0;
            updateVisibleRows();
            return false;
        } else if (chatActivityEnterView != null && chatActivityEnterView.isPopupShowing()) {
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
        MessageObject editingMessageObject = chatActivityEnterView != null ? chatActivityEnterView.getEditingMessageObject() : null;
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) view;
                MessageObject messageObject = cell.getMessageObject();

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBar.isActionModeShowed()) {
                    int idx = messageObject.getDialogId() == dialog_id ? 0 : 1;
                    if (messageObject == editingMessageObject || selectedMessagesIds[idx].containsKey(messageObject.getId())) {
                        setCellSelectionBackground(messageObject, cell, idx);
                        selected = true;
                    } else {
                        view.setBackgroundDrawable(null);
                    }
                    disableSelection = true;
                } else {
                    view.setBackgroundDrawable(null);
                }

                cell.setMessageObject(cell.getMessageObject(), cell.getCurrentMessagesGroup(), cell.isPinnedBottom(), cell.isPinnedTop());
                cell.setCheckPressed(!disableSelection, disableSelection && selected);
                cell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && messageObject != null && messageObject.getId() == highlightMessageId);
                if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE && MessagesSearchQuery.isMessageFound(messageObject.getId(), messageObject.getDialogId() == mergeDialogId) && MessagesSearchQuery.getLastSearchQuery() != null) {
                    cell.setHighlightedText(MessagesSearchQuery.getLastSearchQuery());
                } else {
                    cell.setHighlightedText(null);
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                cell.setMessageObject(cell.getMessageObject());
            }
        }
        chatListView.invalidate();
    }

    private void checkEditTimer() {
        if (chatActivityEnterView == null) {
            return;
        }
        MessageObject messageObject = chatActivityEnterView.getEditingMessageObject();
        if (messageObject == null) {
            return;
        }
        if (currentUser != null && currentUser.self) {
            if (actionModeSubTextView.getVisibility() != View.GONE) {
                actionModeSubTextView.setVisibility(View.GONE);
            }
            return;
        }
        int dt = messageObject.canEditMessageAnytime(currentChat) ? 6 * 60 : MessagesController.getInstance().maxEditTime + 5 * 60 - Math.abs(ConnectionsManager.getInstance().getCurrentTime() - messageObject.messageOwner.date);
        if (dt > 0) {
            if (dt > 5 * 60) {
                if (actionModeSubTextView.getVisibility() != View.GONE) {
                    actionModeSubTextView.setVisibility(View.GONE);
                }
            } else {
                if (actionModeSubTextView.getVisibility() != View.VISIBLE) {
                    actionModeSubTextView.setVisibility(View.VISIBLE);
                }
                actionModeSubTextView.setText(LocaleController.formatString("TimeToEdit", R.string.TimeToEdit, String.format("%d:%02d", dt / 60, dt % 60)));
            }
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    checkEditTimer();
                }
            }, 1000);
        } else {
            chatActivityEnterView.onEditTimeExpired();
            actionModeSubTextView.setText(LocaleController.formatString("TimeToEditExpired", R.string.TimeToEditExpired));
        }
    }

    private ArrayList<MessageObject> createVoiceMessagesPlaylist(MessageObject startMessageObject, boolean playingUnreadMedia) {
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        messageObjects.add(startMessageObject);
        int messageId = startMessageObject.getId();
        long startDialogId = startMessageObject.getDialogId();
        if (messageId != 0) {
            boolean started = false;
            for (int a = messages.size() - 1; a >= 0; a--) {
                MessageObject messageObject = messages.get(a);
                if (messageObject.getDialogId() == mergeDialogId && startMessageObject.getDialogId() != mergeDialogId) {
                    continue;
                }
                /*if (startDialogId == mergeDialogId && messageId != 0 && messageObject.getDialogId() != mergeDialogId) {
                    messageId = 0;
                }*/
                if ((currentEncryptedChat == null && messageObject.getId() > messageId || currentEncryptedChat != null && messageObject.getId() < messageId) && (messageObject.isVoice() || messageObject.isRoundVideo()) && (!playingUnreadMedia || messageObject.isContentUnread() && !messageObject.isOut())) {
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
        searchItem.setVisibility(View.VISIBLE);
        updateSearchButtons(0, 0, -1);
        updateBottomOverlay();
        openSearchKeyboard = text == null;
        searchItem.openSearch(openSearchKeyboard);
        if (text != null) {
            searchItem.getSearchField().setText(text);
            searchItem.getSearchField().setSelection(searchItem.getSearchField().length());
            MessagesSearchQuery.searchMessagesInChat(text, dialog_id, mergeDialogId, classGuid, 0, searchingUserMessages);
        }
        updatePinnedMessageView(true);
    }

    @Override
    public void didSelectLocation(TLRPC.MessageMedia location, int live) {
        SendMessagesHelper.getInstance().sendMessage(location, dialog_id, replyingMessageObject, null, null);
        moveScrollToLastMessage();
        if (live == 1) {
            showReplyPanel(false, null, null, null, false);
            DraftQuery.cleanDraft(dialog_id, true);
        }
        if (paused) {
            scrollToTopOnResume = true;
        }
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

    public boolean allowGroupPhotos() {
        return currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 73;
    }

    public TLRPC.EncryptedChat getCurrentEncryptedChat() {
        return currentEncryptedChat;
    }

    public TLRPC.ChatFull getCurrentChatInfo() {
        return info;
    }

    public void sendMedia(MediaController.PhotoEntry photoEntry, VideoEditedInfo videoEditedInfo) {
        if (photoEntry.isVideo) {
            if (videoEditedInfo != null) {
                SendMessagesHelper.prepareSendingVideo(photoEntry.path, videoEditedInfo.estimatedSize, videoEditedInfo.estimatedDuration, videoEditedInfo.resultWidth, videoEditedInfo.resultHeight, videoEditedInfo, dialog_id, replyingMessageObject, photoEntry.caption != null ? photoEntry.caption.toString() : null, photoEntry.ttl);
            } else {
                SendMessagesHelper.prepareSendingVideo(photoEntry.path, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, photoEntry.caption != null ? photoEntry.caption.toString() : null, photoEntry.ttl);
            }
            showReplyPanel(false, null, null, null, false);
            DraftQuery.cleanDraft(dialog_id, true);
        } else {
            if (photoEntry.imagePath != null) {
                SendMessagesHelper.prepareSendingPhoto(photoEntry.imagePath, null, dialog_id, replyingMessageObject, photoEntry.caption, photoEntry.stickers, null, photoEntry.ttl);
                showReplyPanel(false, null, null, null, false);
                DraftQuery.cleanDraft(dialog_id, true);
            } else if (photoEntry.path != null) {
                SendMessagesHelper.prepareSendingPhoto(photoEntry.path, null, dialog_id, replyingMessageObject, photoEntry.caption, photoEntry.stickers, null, photoEntry.ttl);
                showReplyPanel(false, null, null, null, false);
                DraftQuery.cleanDraft(dialog_id, true);
            }
        }
    }

    public void showOpenGameAlert(final TLRPC.TL_game game, final MessageObject messageObject, final String urlStr, boolean ask, final int uid) {
        TLRPC.User user = MessagesController.getInstance().getUser(uid);
        if (ask) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            String name;
            if (user != null) {
                name = ContactsController.formatName(user.first_name, user.last_name);
            } else {
                name = "";
            }
            builder.setMessage(LocaleController.formatString("BotPermissionGameAlert", R.string.BotPermissionGameAlert, name));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    showOpenGameAlert(game, messageObject, urlStr, false, uid);
                    ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).edit().putBoolean("askgame_" + uid, false).commit();
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        } else {
            if (Build.VERSION.SDK_INT >= 21 && !AndroidUtilities.isTablet() && WebviewActivity.supportWebview()) {
                if (parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) == this) {
                    presentFragment(new WebviewActivity(urlStr, user != null && !TextUtils.isEmpty(user.username) ? user.username : "", game.title, game.short_name, messageObject));
                }
            } else {
                WebviewActivity.openGameInBrowser(urlStr, messageObject, getParentActivity(), game.short_name, user != null && user.username != null ? user.username : "");
            }
        }
    }

    public void showOpenUrlAlert(final String url, boolean ask) {
        if (Browser.isInternalUrl(url, null) || !ask) {
            Browser.openUrl(getParentActivity(), url, inlineReturn == 0);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.formatString("OpenUrlAlert", R.string.OpenUrlAlert, url));
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

    private void removeMessageObject(MessageObject messageObject) {
        int index = messages.indexOf(messageObject);
        if (index == -1) {
            return;
        }
        messages.remove(index);
        if (chatAdapter != null) {
            chatAdapter.notifyItemRemoved(chatAdapter.messagesStartRow + index);
        }
    }

    private void setCellSelectionBackground(MessageObject message, ChatMessageCell messageCell, int idx) {
        MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);
        if (groupedMessages != null) {
            boolean hasUnselected = false;
            for (int a = 0; a < groupedMessages.messages.size(); a++) {
                if (!selectedMessagesIds[idx].containsKey(groupedMessages.messages.get(a).getId())) {
                    hasUnselected = true;
                    break;
                }
            }
            if (!hasUnselected) {
                groupedMessages = null;
            }
        }
        if (groupedMessages == null) {
            messageCell.setBackgroundColor(Theme.getColor(Theme.key_chat_selectedBackground));
        } else {
            messageCell.setBackground(null);
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
            if (!messages.isEmpty()) {
                if (!forwardEndReached[0] || mergeDialogId != 0 && !forwardEndReached[1]) {
                    loadingDownRow = rowCount++;
                } else {
                    loadingDownRow = -1;
                }
                messagesStartRow = rowCount;
                rowCount += messages.size();
                messagesEndRow = rowCount;
                if (!endReached[0] || mergeDialogId != 0 && !endReached[1]) {
                    loadingUpRow = rowCount++;
                } else {
                    loadingUpRow = -1;
                }
            } else {
                loadingUpRow = -1;
                loadingDownRow = -1;
                messagesStartRow = -1;
                messagesEndRow = -1;
            }
            if (currentUser != null && currentUser.bot) {
                botInfoRow = rowCount++;
            } else {
                botInfoRow = -1;
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
                chatMessageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public void didPressedShare(ChatMessageCell cell) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        if (chatActivityEnterView != null) {
                            chatActivityEnterView.closeKeyboard();
                        }
                        MessageObject messageObject = cell.getMessageObject();
                        if (UserObject.isUserSelf(currentUser) && messageObject.messageOwner.fwd_from.saved_from_peer != null) {
                            Bundle args = new Bundle();
                            if (messageObject.messageOwner.fwd_from.saved_from_peer.channel_id != 0) {
                                args.putInt("chat_id", messageObject.messageOwner.fwd_from.saved_from_peer.channel_id);
                            } else if (messageObject.messageOwner.fwd_from.saved_from_peer.chat_id != 0) {
                                args.putInt("chat_id", messageObject.messageOwner.fwd_from.saved_from_peer.chat_id);
                            } else if (messageObject.messageOwner.fwd_from.saved_from_peer.user_id != 0) {
                                args.putInt("user_id", messageObject.messageOwner.fwd_from.saved_from_peer.user_id);
                            }
                            args.putInt("message_id", messageObject.messageOwner.fwd_from.saved_from_msg_id);
                            if (MessagesController.checkCanOpenChat(args, ChatActivity.this)) {
                                presentFragment(new ChatActivity(args));
                            }
                        } else {
                            ArrayList<MessageObject> arrayList = null;
                            if (messageObject.getGroupId() != 0) {
                                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
                                if (groupedMessages != null) {
                                    arrayList = groupedMessages.messages;
                                }
                            }
                            if (arrayList == null) {
                                arrayList = new ArrayList<>();
                                arrayList.add(messageObject);
                            }
                            showDialog(new ShareAlert(mContext, arrayList, null, ChatObject.isChannel(currentChat) && !currentChat.megagroup && currentChat.username != null && currentChat.username.length() > 0, null, false));
                        }
                    }

                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject);
                            MediaController.getInstance().setVoiceMessagesPlaylist(result ? createVoiceMessagesPlaylist(messageObject, false) : null, false);
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(messages, messageObject);
                        }
                        return false;
                    }

                    @Override
                    public void didPressedChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell, true);
                            return;
                        }
                        if (chat != null && chat != currentChat) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", chat.id);
                            if (postId != 0) {
                                args.putInt("message_id", postId);
                            }
                            if (MessagesController.checkCanOpenChat(args, ChatActivity.this, cell.getMessageObject())) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    }

                    @Override
                    public void didPressedOther(ChatMessageCell cell) {
                        if (cell.getMessageObject().type == 16) {
                            if (currentUser != null) {
                                VoIPHelper.startCall(currentUser, getParentActivity(), MessagesController.getInstance().getUserFull(currentUser.id));
                            }
                        } else {
                            createMenu(cell, true, false, false);
                        }
                    }

                    @Override
                    public void didPressedUserAvatar(ChatMessageCell cell, TLRPC.User user) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell, true);
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
                    public void didPressedBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                        if (getParentActivity() == null || bottomOverlayChat.getVisibility() == View.VISIBLE &&
                                !(button instanceof TLRPC.TL_keyboardButtonSwitchInline) && !(button instanceof TLRPC.TL_keyboardButtonCallback) &&
                                !(button instanceof TLRPC.TL_keyboardButtonGame) && !(button instanceof TLRPC.TL_keyboardButtonUrl) &&
                                !(button instanceof TLRPC.TL_keyboardButtonBuy)) {
                            return;
                        }
                        chatActivityEnterView.didPressedBotButton(button, cell.getMessageObject(), cell.getMessageObject());
                    }

                    @Override
                    public void didPressedCancelSendButton(ChatMessageCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.messageOwner.send_state != 0) {
                            SendMessagesHelper.getInstance().cancelSendingMessage(message);
                        }
                    }

                    @Override
                    public void didLongPressed(ChatMessageCell cell) {
                        createMenu(cell, false, false);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return actionBar != null && !actionBar.isActionModeShowed();
                    }

                    @Override
                    public void didPressedUrl(MessageObject messageObject, final CharacterStyle url, boolean longPress) {
                        if (url == null) {
                            return;
                        }
                        if (url instanceof URLSpanMono) {
                            ((URLSpanMono) url).copyToClipboard();
                            Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                        } else if (url instanceof URLSpanUserMention) {
                            TLRPC.User user = MessagesController.getInstance().getUser(Utilities.parseInt(((URLSpanUserMention) url).getURL()));
                            if (user != null) {
                                MessagesController.openChatOrProfileWith(user, null, ChatActivity.this, 0, false);
                            }
                        } else if (url instanceof URLSpanNoUnderline) {
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
                                    if (!longPress && chatActivityEnterView.getFieldText() == null) {
                                        showReplyPanel(false, null, null, null, false);
                                    }
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
                                            String url = urlFinal;
                                            if (url.startsWith("mailto:")) {
                                                url = url.substring(7);
                                            } else if (url.startsWith("tel:")) {
                                                url = url.substring(4);
                                            }
                                            AndroidUtilities.addToClipboard(url);
                                        }
                                    }
                                });
                                showDialog(builder.create());
                            } else {
                                if (url instanceof URLSpanReplacement) {
                                    showOpenUrlAlert(((URLSpanReplacement) url).getURL(), true);
                                } else if (url instanceof URLSpan) {
                                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                        String lowerUrl = urlFinal.toLowerCase();
                                        String lowerUrl2 = messageObject.messageOwner.media.webpage.url.toLowerCase();
                                        if ((lowerUrl.contains("telegra.ph") || lowerUrl.contains("t.me/iv")) && (lowerUrl.contains(lowerUrl2) || lowerUrl2.contains(lowerUrl))) {
                                            ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChatActivity.this);
                                            ArticleViewer.getInstance().open(messageObject);
                                            return;
                                        }
                                    }
                                    Browser.openUrl(getParentActivity(), urlFinal, inlineReturn == 0);
                                } else if (url instanceof ClickableSpan) {
                                    ((ClickableSpan) url).onClick(fragmentView);
                                }
                            }
                        }
                    }

                    @Override
                    public void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h) {
                        EmbedBottomSheet.show(mContext, title, description, originalUrl, url, w, h);
                    }

                    @Override
                    public void didPressedReplyMessage(ChatMessageCell cell, int id) {
                        MessageObject messageObject = cell.getMessageObject();
                        scrollToMessageId(id, messageObject.getId(), true, messageObject.getDialogId() == mergeDialogId ? 1 : 0, false);
                    }

                    @Override
                    public void didPressedViaBot(ChatMessageCell cell, String username) {
                        if (bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE || bottomOverlay != null && bottomOverlay.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        if (chatActivityEnterView != null && username != null && username.length() > 0) {
                            chatActivityEnterView.setFieldText("@" + username + " ");
                            chatActivityEnterView.openKeyboard();
                        }
                    }

                    @Override
                    public void didPressedImage(ChatMessageCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.isSendError()) {
                            createMenu(cell, false, false);
                            return;
                        } else if (message.isSending()) {
                            return;
                        }
                        if (message.isSecretPhoto()) {
                            if (sendSecretMessageRead(message)) {
                                cell.invalidate();
                            }
                            SecretMediaViewer.getInstance().setParentActivity(getParentActivity());
                            SecretMediaViewer.getInstance().openMedia(message, photoViewerProvider);
                        } else if (message.type == 13) {
                            showDialog(new StickersAlert(getParentActivity(), ChatActivity.this, message.getInputStickerSet(), null, bottomOverlayChat.getVisibility() != View.VISIBLE && ChatObject.canSendStickers(currentChat) ? chatActivityEnterView : null));
                        } else if (message.isVideo() || message.type == 1 || message.type == 0 && !message.isWebpageDocument() || message.isGif()) {
                            if (message.isVideo()) {
                                sendSecretMessageRead(message);
                            }
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            if (PhotoViewer.getInstance().openPhoto(message, message.type != 0 ? dialog_id : 0, message.type != 0 ? mergeDialogId : 0, photoViewerProvider)) {
                                PhotoViewer.getInstance().setParentChatActivity(ChatActivity.this);
                            }
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
                                if (Build.VERSION.SDK_INT >= 24) {
                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", f), "video/mp4");
                                } else {
                                    intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                }
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                FileLog.e(e);
                                alertUserOpenError(message);
                            }
                        } else if (message.type == 4) {
                            if (!AndroidUtilities.isGoogleMapsInstalled(ChatActivity.this)) {
                                return;
                            }
                            if (message.isLiveLocation()) {
                                LocationActivity fragment = new LocationActivity(2);
                                fragment.setMessageObject(message);
                                fragment.setDelegate(ChatActivity.this);
                                presentFragment(fragment);
                            } else {
                                LocationActivity fragment = new LocationActivity(currentEncryptedChat == null ? 3 : 0);
                                fragment.setMessageObject(message);
                                fragment.setDelegate(ChatActivity.this);
                                presentFragment(fragment);
                            }
                        } else if (message.type == 9 || message.type == 0) {
                            if (message.getDocumentName().endsWith("attheme")) {
                                File locFile = null;
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    File f = new File(message.messageOwner.attachPath);
                                    if (f.exists()) {
                                        locFile = f;
                                    }
                                }
                                if (locFile == null) {
                                    File f = FileLoader.getPathToMessage(message.messageOwner);
                                    if (f.exists()) {
                                        locFile = f;
                                    }
                                }
                                if (chatLayoutManager != null) {
                                    int lastPosition = chatLayoutManager.findFirstVisibleItemPosition();
                                    if (lastPosition != 0) {
                                        scrollToPositionOnRecreate = lastPosition;
                                        RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                                        if (holder != null) {
                                            scrollToOffsetOnRecreate = chatListView.getMeasuredHeight() - holder.itemView.getBottom() - chatListView.getPaddingBottom();
                                        } else {
                                            scrollToPositionOnRecreate = -1;
                                        }
                                    } else {
                                        scrollToPositionOnRecreate = -1;
                                    }
                                }
                                Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, message.getDocumentName(), true);
                                if (themeInfo != null) {
                                    presentFragment(new ThemePreviewActivity(locFile, themeInfo));
                                    return;
                                } else {
                                    scrollToPositionOnRecreate = -1;
                                }
                            }
                            try {
                                AndroidUtilities.openForView(message, getParentActivity());
                            } catch (Exception e) {
                                FileLog.e(e);
                                alertUserOpenError(message);
                            }
                        }
                    }

                    @Override
                    public void didPressedInstantButton(ChatMessageCell cell, int type) {
                        MessageObject messageObject = cell.getMessageObject();
                        if (type == 0) {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChatActivity.this);
                                ArticleViewer.getInstance().open(messageObject);
                            }
                        } else {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null) {
                                Browser.openUrl(getParentActivity(), messageObject.messageOwner.media.webpage.url);
                            }
                        }
                    }

                    @Override
                    public boolean isChatAdminCell(int uid) {
                        if (ChatObject.isChannel(currentChat) && currentChat.megagroup) {
                            return MessagesController.getInstance().isChannelAdmin(currentChat.id, uid);
                        }
                        return false;
                    }
                });
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
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 640);
                        if (photoSize != null) {
                            PhotoViewer.getInstance().openPhoto(photoSize.location, photoViewerProvider);
                        } else {
                            PhotoViewer.getInstance().openPhoto(message, 0, 0, photoViewerProvider);
                        }
                    }

                    @Override
                    public void didLongPressed(ChatActionCell cell) {
                        createMenu(cell, false, false);
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

                    @Override
                    public void didPressedReplyMessage(ChatActionCell cell, int id) {
                        MessageObject messageObject = cell.getMessageObject();
                        scrollToMessageId(id, messageObject.getId(), true, messageObject.getDialogId() == mergeDialogId ? 1 : 0, false);
                    }

                    @Override
                    public void didPressedBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {
                        if (getParentActivity() == null || bottomOverlayChat.getVisibility() == View.VISIBLE &&
                                !(button instanceof TLRPC.TL_keyboardButtonSwitchInline) && !(button instanceof TLRPC.TL_keyboardButtonCallback) &&
                                !(button instanceof TLRPC.TL_keyboardButtonGame) && !(button instanceof TLRPC.TL_keyboardButtonUrl) &&
                                !(button instanceof TLRPC.TL_keyboardButtonBuy)) {
                            return;
                        }
                        chatActivityEnterView.didPressedBotButton(button, messageObject, messageObject);
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
                            if (chatActivityEnterView.getFieldText() == null) {
                                showReplyPanel(false, null, null, null, false);
                            }
                        }
                    }
                });
            } else if (viewType == 4) {
                view = new ChatLoadingCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
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
                MessageObject message = messages.get(position - messagesStartRow);
                View view = holder.itemView;

                if (view instanceof ChatMessageCell) {
                    final ChatMessageCell messageCell = (ChatMessageCell) view;
                    messageCell.isChat = currentChat != null || UserObject.isUserSelf(currentUser);
                    boolean pinnedBottom = false;
                    boolean pinnedTop = false;
                    MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);

                    int prevPosition;
                    int nextPosition;
                    if (groupedMessages != null) {
                        MessageObject.GroupedMessagePosition pos = groupedMessages.positions.get(message);
                        if (pos != null) {
                            if ((pos.flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                                prevPosition = position + groupedMessages.posArray.indexOf(pos) + 1;
                            } else {
                                pinnedTop = true;
                                prevPosition = -100;
                            }
                            if ((pos.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                nextPosition = position - groupedMessages.posArray.size() + groupedMessages.posArray.indexOf(pos);
                            } else {
                                pinnedBottom = true;
                                nextPosition = -100;
                            }
                        } else {
                            prevPosition = -100;
                            nextPosition = -100;
                        }
                    } else {
                        nextPosition = position - 1;
                        prevPosition = position + 1;
                    }
                    int nextType = getItemViewType(nextPosition);
                    int prevType = getItemViewType(prevPosition);
                    if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                        MessageObject nextMessage = messages.get(nextPosition - messagesStartRow);
                        pinnedBottom = nextMessage.isOutOwner() == message.isOutOwner() && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                        if (pinnedBottom) {
                            if (currentChat != null) {
                                pinnedBottom = nextMessage.messageOwner.from_id == message.messageOwner.from_id;
                            } else if (UserObject.isUserSelf(currentUser)) {
                                pinnedBottom = nextMessage.getFromId() == message.getFromId();
                            }
                        }
                    }
                    if (prevType == holder.getItemViewType()) {
                        MessageObject prevMessage = messages.get(prevPosition - messagesStartRow);
                        pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                        if (pinnedTop) {
                            if (currentChat != null) {
                                pinnedTop = prevMessage.messageOwner.from_id == message.messageOwner.from_id;
                            } else if (UserObject.isUserSelf(currentUser)) {
                                pinnedTop = prevMessage.getFromId() == message.getFromId();
                            }
                        }
                    }

                    messageCell.setMessageObject(message, groupedMessages, pinnedBottom, pinnedTop);
                    if (view instanceof ChatMessageCell && MediaController.getInstance().canDownloadMedia(message)) {
                        ((ChatMessageCell) view).downloadAudioIfNeed();
                    }
                    messageCell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && message.getId() == highlightMessageId);
                    if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE && MessagesSearchQuery.isMessageFound(message.getId(), message.getDialogId() == mergeDialogId) && MessagesSearchQuery.getLastSearchQuery() != null) {
                        messageCell.setHighlightedText(MessagesSearchQuery.getLastSearchQuery());
                    } else {
                        messageCell.setHighlightedText(null);
                    }
                    int index;
                    if ((index = animatingMessageObjects.indexOf(message)) != -1) {
                        animatingMessageObjects.remove(index);
                        messageCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
                                if (pipRoundVideoView != null) {
                                    pipRoundVideoView.showTemporary(true);
                                }

                                messageCell.getViewTreeObserver().removeOnPreDrawListener(this);
                                ImageReceiver imageReceiver = messageCell.getPhotoImage();
                                int w = imageReceiver.getImageWidth();
                                org.telegram.ui.Components.Rect rect = instantCameraView.getCameraRect();
                                float scale = w / rect.width;
                                int position[] = new int[2];
                                messageCell.setAlpha(0.0f);
                                messageCell.getLocationOnScreen(position);
                                position[0] += imageReceiver.getImageX();
                                position[1] += imageReceiver.getImageY();
                                final View cameraContainer = instantCameraView.getCameraContainer();
                                cameraContainer.setPivotX(0.0f);
                                cameraContainer.setPivotY(0.0f);
                                AnimatorSet animatorSet = new AnimatorSet();
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(instantCameraView, "alpha", 0.0f),
                                        ObjectAnimator.ofFloat(cameraContainer, "scaleX", scale),
                                        ObjectAnimator.ofFloat(cameraContainer, "scaleY", scale),
                                        ObjectAnimator.ofFloat(cameraContainer, "translationX", position[0] - rect.x),
                                        ObjectAnimator.ofFloat(cameraContainer, "translationY", position[1] - rect.y),
                                        ObjectAnimator.ofFloat(instantCameraView.getSwitchButtonView(), "alpha", 0.0f),
                                        ObjectAnimator.ofInt(instantCameraView.getPaint(), "alpha", 0),
                                        ObjectAnimator.ofFloat(instantCameraView.getMuteImageView(), "alpha", 0.0f));
                                animatorSet.setDuration(180);
                                animatorSet.setInterpolator(new DecelerateInterpolator());
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        //messageCell.setAlpha(1.0f);
                                        AnimatorSet animatorSet = new AnimatorSet();
                                        animatorSet.playTogether(
                                                ObjectAnimator.ofFloat(cameraContainer, "alpha", 0.0f),
                                                ObjectAnimator.ofFloat(messageCell, "alpha", 1.0f)
                                        );
                                        animatorSet.setDuration(100);
                                        animatorSet.setInterpolator(new DecelerateInterpolator());
                                        animatorSet.addListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                instantCameraView.hideCamera(true);
                                                instantCameraView.setVisibility(View.INVISIBLE);
                                            }
                                        });
                                        animatorSet.start();
                                    }
                                });
                                animatorSet.start();
                                return true;
                            }
                        });
                    }
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell actionCell = (ChatActionCell) view;
                    actionCell.setMessageObject(message);
                    actionCell.setAlpha(1.0f);
                } else if (view instanceof ChatUnreadCell) {
                    ChatUnreadCell unreadCell = (ChatUnreadCell) view;
                    unreadCell.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
                    if (createUnreadMessageAfterId != 0) {
                        createUnreadMessageAfterId = 0;
                    }
                }
                if (message != null && message.messageOwner != null && message.messageOwner.media_unread && message.messageOwner.mentioned) {
                    if (!message.isVoice() && !message.isRoundVideo()) {
                        newMentionsCount--;
                        if (newMentionsCount <= 0) {
                            newMentionsCount = 0;
                            hasAllMentionsLocal = true;
                            showMentiondownButton(false, true);
                        } else {
                            mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                        }
                        MessagesController.getInstance().markMentionMessageAsRead(message.getId(), ChatObject.isChannel(currentChat) ? currentChat.id : 0, dialog_id);
                        message.setContentIsRead();
                    }
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).setHighlightedAnimated();
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= messagesStartRow && position < messagesEndRow) {
                return messages.get(position - messagesStartRow).contentType;
            } else if (position == botInfoRow) {
                return 3;
            }
            return 4;
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ChatMessageCell) {
                final ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
                MessageObject message = messageCell.getMessageObject();

                boolean selected = false;
                boolean disableSelection = false;
                if (actionBar.isActionModeShowed()) {
                    MessageObject messageObject = chatActivityEnterView != null ? chatActivityEnterView.getEditingMessageObject() : null;
                    int idx = message.getDialogId() == dialog_id ? 0 : 1;
                    if (messageObject == message || selectedMessagesIds[idx].containsKey(message.getId())) {
                        setCellSelectionBackground(message, messageCell, idx);
                        selected = true;
                    } else {
                        messageCell.setBackgroundDrawable(null);
                    }
                    disableSelection = true;
                } else {
                    messageCell.setBackgroundDrawable(null);
                }
                messageCell.setCheckPressed(!disableSelection, disableSelection && selected);

                messageCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        messageCell.getViewTreeObserver().removeOnPreDrawListener(this);

                        int height = chatListView.getMeasuredHeight();
                        int top = messageCell.getTop();
                        int bottom = messageCell.getBottom();
                        int viewTop = top >= 0 ? 0 : -top;
                        int viewBottom = messageCell.getMeasuredHeight();
                        if (viewBottom > height) {
                            viewBottom = viewTop + height;
                        }
                        messageCell.setVisiblePart(viewTop, viewBottom - viewTop);

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
            notifyItemChanged(index + messagesStartRow);
        }

        @Override
        public void notifyDataSetChanged() {
            updateRows();
            try {
                super.notifyDataSetChanged();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemChanged(int position) {
            try {
                super.notifyItemChanged(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            try {
                super.notifyItemRangeChanged(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemInserted(int position) {
            updateRows();
            try {
                super.notifyItemInserted(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemMoved(int fromPosition, int toPosition) {
            updateRows();
            try {
                super.notifyItemMoved(fromPosition, toPosition);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeInserted(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRemoved(int position) {
            updateRows();
            try {
                super.notifyItemRemoved(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeRemoved(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate selectedBackgroundDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {
                updateVisibleRows();
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(avatarContainer.getTitleTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(avatarContainer.getSubtitleTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, new Paint[]{Theme.chat_statusPaint, Theme.chat_statusRecordPaint}, null, null, Theme.key_actionBarDefaultSubtitle, null),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector),
                new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),
                new ThemeDescription(actionModeTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),
                new ThemeDescription(actionModeSubTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),

                new ThemeDescription(avatarContainer.getTitleTextView(), 0, null, null, new Drawable[]{Theme.chat_muteIconDrawable}, null, Theme.key_chat_muteIcon),
                new ThemeDescription(avatarContainer.getTitleTextView(), 0, null, null, new Drawable[]{Theme.chat_lockIconDrawable}, null, Theme.key_chat_lockIcon),

                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundRed),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundPink),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageRed),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageOrange),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageViolet),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageGreen),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageCyan),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageBlue),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessagePink),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInShadowDrawable, Theme.chat_msgInMediaShadowDrawable}, null, Theme.key_chat_inBubbleShadow),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutShadowDrawable, Theme.chat_msgOutMediaShadowDrawable}, null, Theme.key_chat_outBubbleShadow),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActionCell.class}, Theme.chat_actionTextPaint, null, null, Theme.key_chat_serviceText),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatActionCell.class}, Theme.chat_actionTextPaint, null, null, Theme.key_chat_serviceLink),

                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_shareIconDrawable, Theme.chat_botInlineDrawable, Theme.chat_botLinkDrawalbe, Theme.chat_goIconDrawable}, null, Theme.key_chat_serviceIcon),

                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, ChatActionCell.class}, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, ChatActionCell.class}, null, null, null, Theme.key_chat_serviceBackgroundSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextIn),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextOut),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageLinkIn, null),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageLinkOut, null),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheck),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutClockDrawable}, null, Theme.key_chat_outSentClock),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedClockDrawable}, null, Theme.key_chat_outSentClockSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInClockDrawable}, null, Theme.key_chat_inSentClock),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedClockDrawable}, null, Theme.key_chat_inSentClockSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgStickerHalfCheckDrawable, Theme.chat_msgStickerCheckDrawable, Theme.chat_msgStickerClockDrawable, Theme.chat_msgStickerViewsDrawable}, null, Theme.key_chat_serviceText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaClockDrawable}, null, Theme.key_chat_mediaSentClock),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutViewsDrawable}, null, Theme.key_chat_outViews),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutViewsSelectedDrawable}, null, Theme.key_chat_outViewsSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInViewsDrawable}, null, Theme.key_chat_inViews),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInViewsSelectedDrawable}, null, Theme.key_chat_inViewsSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaViewsDrawable}, null, Theme.key_chat_mediaViews),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutMenuDrawable}, null, Theme.key_chat_outMenu),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutMenuSelectedDrawable}, null, Theme.key_chat_outMenuSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInMenuDrawable}, null, Theme.key_chat_inMenu),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInMenuSelectedDrawable}, null, Theme.key_chat_inMenuSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaMenuDrawable}, null, Theme.key_chat_mediaMenu),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutInstantDrawable, Theme.chat_msgOutCallDrawable}, null, Theme.key_chat_outInstant),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCallSelectedDrawable}, null, Theme.key_chat_outInstantSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInInstantDrawable, Theme.chat_msgInCallDrawable}, null, Theme.key_chat_inInstant),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInCallSelectedDrawable}, null, Theme.key_chat_inInstantSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallUpRedDrawable, Theme.chat_msgCallDownRedDrawable}, null, Theme.key_calls_callReceivedRedIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallUpGreenDrawable, Theme.chat_msgCallDownGreenDrawable}, null, Theme.key_calls_callReceivedGreenIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_msgErrorPaint, null, null, Theme.key_chat_sentError),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgErrorDrawable}, null, Theme.key_chat_sentErrorIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, selectedBackgroundDelegate, Theme.key_chat_selectedBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_durationPaint, null, null, Theme.key_chat_previewDurationText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_gamePaint, null, null, Theme.key_chat_previewGameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inPreviewInstantText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outPreviewInstantText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inPreviewInstantSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outPreviewInstantSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_deleteProgressPaint, null, null, Theme.key_chat_secretTimeText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_botButtonPaint, null, null, Theme.key_chat_botButtonText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_botProgressPaint, null, null, Theme.key_chat_botProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inForwardedNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outForwardedNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inViaBotNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outViaBotNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerViaBotNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyLine),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyLine),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerReplyLine),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerReplyNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMessageText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMessageText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerReplyMessageText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inPreviewLine),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outPreviewLine),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inSiteNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outSiteNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inContactNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outContactNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inContactPhoneText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outContactPhoneText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSelectedProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSelectedProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaTimeText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_adminText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_adminSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioPerfomerText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioPerfomerText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioTitleText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioTitleText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioDurationText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioDurationText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioDurationSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioDurationSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSeekbar),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbar),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSeekbarSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbarSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSeekbarFill),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbarFill),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVoiceSeekbar),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVoiceSeekbar),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVoiceSeekbarSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVoiceSeekbarSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVoiceSeekbarFill),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVoiceSeekbarFill),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileProgress),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileProgressSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileProgressSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileInfoSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileInfoSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileBackgroundSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileBackgroundSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueNameText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueInfoSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueInfoSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_urlPaint, null, null, Theme.key_chat_linkSelectBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_textSearchSelectionPaint, null, null, Theme.key_chat_textSelectBackground),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[0][0], Theme.chat_fileStatesDrawable[1][0], Theme.chat_fileStatesDrawable[2][0], Theme.chat_fileStatesDrawable[3][0], Theme.chat_fileStatesDrawable[4][0]}, null, Theme.key_chat_outLoader),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[0][0], Theme.chat_fileStatesDrawable[1][0], Theme.chat_fileStatesDrawable[2][0], Theme.chat_fileStatesDrawable[3][0], Theme.chat_fileStatesDrawable[4][0]}, null, Theme.key_chat_outBubble),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[0][1], Theme.chat_fileStatesDrawable[1][1], Theme.chat_fileStatesDrawable[2][1], Theme.chat_fileStatesDrawable[3][1], Theme.chat_fileStatesDrawable[4][1]}, null, Theme.key_chat_outLoaderSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[0][1], Theme.chat_fileStatesDrawable[1][1], Theme.chat_fileStatesDrawable[2][1], Theme.chat_fileStatesDrawable[3][1], Theme.chat_fileStatesDrawable[4][1]}, null, Theme.key_chat_outBubbleSelected),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[5][0], Theme.chat_fileStatesDrawable[6][0], Theme.chat_fileStatesDrawable[7][0], Theme.chat_fileStatesDrawable[8][0], Theme.chat_fileStatesDrawable[9][0]}, null, Theme.key_chat_inLoader),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[5][0], Theme.chat_fileStatesDrawable[6][0], Theme.chat_fileStatesDrawable[7][0], Theme.chat_fileStatesDrawable[8][0], Theme.chat_fileStatesDrawable[9][0]}, null, Theme.key_chat_inBubble),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[5][1], Theme.chat_fileStatesDrawable[6][1], Theme.chat_fileStatesDrawable[7][1], Theme.chat_fileStatesDrawable[8][1], Theme.chat_fileStatesDrawable[9][1]}, null, Theme.key_chat_inLoaderSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_fileStatesDrawable[5][1], Theme.chat_fileStatesDrawable[6][1], Theme.chat_fileStatesDrawable[7][1], Theme.chat_fileStatesDrawable[8][1], Theme.chat_fileStatesDrawable[9][1]}, null, Theme.key_chat_inBubbleSelected),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[0][0], Theme.chat_photoStatesDrawables[1][0], Theme.chat_photoStatesDrawables[2][0], Theme.chat_photoStatesDrawables[3][0]}, null, Theme.key_chat_mediaLoaderPhoto),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[0][0], Theme.chat_photoStatesDrawables[1][0], Theme.chat_photoStatesDrawables[2][0], Theme.chat_photoStatesDrawables[3][0]}, null, Theme.key_chat_mediaLoaderPhotoIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[0][1], Theme.chat_photoStatesDrawables[1][1], Theme.chat_photoStatesDrawables[2][1], Theme.chat_photoStatesDrawables[3][1]}, null, Theme.key_chat_mediaLoaderPhotoSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[0][1], Theme.chat_photoStatesDrawables[1][1], Theme.chat_photoStatesDrawables[2][1], Theme.chat_photoStatesDrawables[3][1]}, null, Theme.key_chat_mediaLoaderPhotoIconSelected),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[7][0], Theme.chat_photoStatesDrawables[8][0]}, null, Theme.key_chat_outLoaderPhoto),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[7][0], Theme.chat_photoStatesDrawables[8][0]}, null, Theme.key_chat_outLoaderPhotoIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[7][1], Theme.chat_photoStatesDrawables[8][1]}, null, Theme.key_chat_outLoaderPhotoSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[7][1], Theme.chat_photoStatesDrawables[8][1]}, null, Theme.key_chat_outLoaderPhotoIconSelected),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[10][0], Theme.chat_photoStatesDrawables[11][0]}, null, Theme.key_chat_inLoaderPhoto),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[10][0], Theme.chat_photoStatesDrawables[11][0]}, null, Theme.key_chat_inLoaderPhotoIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[10][1], Theme.chat_photoStatesDrawables[11][1]}, null, Theme.key_chat_inLoaderPhotoSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[10][1], Theme.chat_photoStatesDrawables[11][1]}, null, Theme.key_chat_inLoaderPhotoIconSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[9][0]}, null, Theme.key_chat_outFileIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[9][1]}, null, Theme.key_chat_outFileSelectedIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[12][0]}, null, Theme.key_chat_inFileIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_photoStatesDrawables[12][1]}, null, Theme.key_chat_inFileSelectedIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[0]}, null, Theme.key_chat_inContactBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[0]}, null, Theme.key_chat_inContactIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[1]}, null, Theme.key_chat_outContactBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[1]}, null, Theme.key_chat_outContactIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_locationDrawable[0]}, null, Theme.key_chat_inLocationBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_locationDrawable[0]}, null, Theme.key_chat_inLocationIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_locationDrawable[1]}, null, Theme.key_chat_outLocationBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_locationDrawable[1]}, null, Theme.key_chat_outLocationIcon),

                new ThemeDescription(mentionContainer, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(mentionContainer, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),
                new ThemeDescription(searchContainer, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(searchContainer, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),
                new ThemeDescription(bottomOverlay, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(bottomOverlay, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),
                new ThemeDescription(bottomOverlayChat, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(bottomOverlayChat, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),

                new ThemeDescription(chatActivityEnterView, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUND, new Class[]{ChatActivityEnterView.class}, new String[]{"audioVideoButtonContainer"}, null, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"messageEditText"}, null, null, null, Theme.key_chat_messagePanelText),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordSendText"}, null, null, null, Theme.key_chat_fieldOverlayText),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"messageEditText"}, null, null, null, Theme.key_chat_messagePanelHint),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"sendButton"}, null, null, null, Theme.key_chat_messagePanelSend),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"emojiButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"botButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"notifyButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"attachButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"audioSendButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"videoSendButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"doneButtonImage"}, null, null, null, Theme.key_chat_editDoneIcon),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUND, new Class[]{ChatActivityEnterView.class}, new String[]{"recordedAudioPanel"}, null, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"micDrawable"}, null, null, null, Theme.key_chat_messagePanelVoicePressed),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"cameraDrawable"}, null, null, null, Theme.key_chat_messagePanelVoicePressed),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"sendDrawable"}, null, null, null, Theme.key_chat_messagePanelVoicePressed),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"lockDrawable"}, null, null, null, Theme.key_chat_messagePanelVoiceLock),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"lockTopDrawable"}, null, null, null, Theme.key_chat_messagePanelVoiceLock),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"lockArrowDrawable"}, null, null, null, Theme.key_chat_messagePanelVoiceLock),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"lockBackgroundDrawable"}, null, null, null, Theme.key_chat_messagePanelVoiceLockBackground),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"lockShadowDrawable"}, null, null, null, Theme.key_chat_messagePanelVoiceLockShadow),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordDeleteImageView"}, null, null, null, Theme.key_chat_messagePanelVoiceDelete),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatActivityEnterView.class}, new String[]{"recordedAudioBackground"}, null, null, null, Theme.key_chat_recordedVoiceBackground),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordTimeText"}, null, null, null, Theme.key_chat_recordTime),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUND, new Class[]{ChatActivityEnterView.class}, new String[]{"recordTimeContainer"}, null, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordCancelText"}, null, null, null, Theme.key_chat_recordVoiceCancel),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUND, new Class[]{ChatActivityEnterView.class}, new String[]{"recordPanel"}, null, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordedAudioTimeTextView"}, null, null, null, Theme.key_chat_messagePanelVoiceDuration),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordCancelImage"}, null, null, null, Theme.key_chat_recordVoiceCancel),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"doneButtonProgress"}, null, null, null, Theme.key_contextProgressInner1),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"doneButtonProgress"}, null, null, null, Theme.key_contextProgressOuter1),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"cancelBotButton"}, null, null, null, Theme.key_chat_messagePanelCancelInlineBot),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"redDotPaint"}, null, null, null, Theme.key_chat_recordedVoiceDot),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"paint"}, null, null, null, Theme.key_chat_messagePanelVoiceBackground),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"paintRecord"}, null, null, null, Theme.key_chat_messagePanelVoiceShadow),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"seekBarWaveform"}, null, null, null, Theme.key_chat_recordedVoiceProgress),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"seekBarWaveform"}, null, null, null, Theme.key_chat_recordedVoiceProgressInner),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"playDrawable"}, null, null, null, Theme.key_chat_recordedVoicePlayPause),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"pauseDrawable"}, null, null, null, Theme.key_chat_recordedVoicePlayPause),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"dotPaint"}, null, null, null, Theme.key_chat_emojiPanelNewTrending),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{ChatActivityEnterView.class}, new String[]{"playDrawable"}, null, null, null, Theme.key_chat_recordedVoicePlayPausePressed),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{ChatActivityEnterView.class}, new String[]{"pauseDrawable"}, null, null, null, Theme.key_chat_recordedVoicePlayPausePressed),

                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelBackground),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelShadowLine),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelEmptyText),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelIcon),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelIconSelected),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelStickerPackSelector),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelIconSelector),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelBackspace),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelTrendingTitle),
                new ThemeDescription(chatActivityEnterView.getEmojiView(), 0, new Class[]{EmojiView.class}, new String[]{""}, null, null, null, Theme.key_chat_emojiPanelTrendingDescription),

                new ThemeDescription(null, 0, null, null, null, null, Theme.key_chat_botKeyboardButtonText),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_chat_botKeyboardButtonBackground),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_chat_botKeyboardButtonBackgroundPressed),

                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground),
                new ThemeDescription(fragmentContextView, 0, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause),
                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle),
                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerPerformer),
                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose),

                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground),
                new ThemeDescription(fragmentContextView, 0, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText),

                new ThemeDescription(pinnedLineView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_chat_topPanelLine),
                new ThemeDescription(pinnedMessageNameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelTitle),
                new ThemeDescription(pinnedMessageTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelMessage),
                new ThemeDescription(alertNameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelTitle),
                new ThemeDescription(alertTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelMessage),
                new ThemeDescription(closePinned, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_topPanelClose),
                new ThemeDescription(closeReportSpam, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_topPanelClose),
                new ThemeDescription(reportSpamView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground),
                new ThemeDescription(alertView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground),
                new ThemeDescription(pinnedMessageView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground),
                new ThemeDescription(addToContactsButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_addContact),
                new ThemeDescription(reportSpamButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_reportSpam),

                new ThemeDescription(replyLineView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_chat_replyPanelLine),
                new ThemeDescription(replyNameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_replyPanelName),
                new ThemeDescription(replyObjectTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_replyPanelMessage),
                new ThemeDescription(replyIconImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_replyPanelIcons),
                new ThemeDescription(replyCloseImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_replyPanelClose),

                new ThemeDescription(searchUpButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_searchPanelIcons),
                new ThemeDescription(searchDownButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_searchPanelIcons),
                new ThemeDescription(searchCalendarButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_searchPanelIcons),
                new ThemeDescription(searchUserButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_searchPanelIcons),
                new ThemeDescription(searchCountText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_searchPanelText),

                new ThemeDescription(bottomOverlayText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_secretChatStatusText),
                new ThemeDescription(bottomOverlayChatText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText),

                new ThemeDescription(bigEmptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_serviceText),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_serviceText),

                new ThemeDescription(progressBar, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_chat_serviceText),

                new ThemeDescription(stickersPanelArrow, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_stickersHintPanel),
                new ThemeDescription(stickersListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{StickerCell.class}, null, null, null, Theme.key_chat_stickersHintPanel),

                new ThemeDescription(chatListView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{ChatUnreadCell.class}, new String[]{"backgroundLayout"}, null, null, null, Theme.key_chat_unreadMessagesStartBackground),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatUnreadCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chat_unreadMessagesStartArrowIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatUnreadCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_unreadMessagesStartText),

                new ThemeDescription(progressView2, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(bigEmptyView, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground),

                new ThemeDescription(chatListView, ThemeDescription.FLAG_SERVICEBACKGROUND, new Class[]{ChatLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{ChatLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_serviceText),

                new ThemeDescription(mentionListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{BotSwitchCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_botSwitchToInlineText),

                new ThemeDescription(mentionListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{MentionCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(mentionListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{MentionCell.class}, new String[]{"usernameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, new Drawable[]{Theme.chat_inlineResultFile, Theme.chat_inlineResultAudio, Theme.chat_inlineResultLocation}, null, Theme.key_chat_inlineResultIcon),
                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteLinkText),
                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, null, null, Theme.key_chat_inAudioProgress),
                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, null, null, Theme.key_chat_inAudioSelectedProgress),
                new ThemeDescription(mentionListView, 0, new Class[]{ContextLinkCell.class}, null, null, null, Theme.key_divider),

                new ThemeDescription(gifHintTextView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_gifSaveHintBackground),
                new ThemeDescription(gifHintTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_gifSaveHintText),

                new ThemeDescription(pagedownButtonCounter, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_goDownButtonCounterBackground),
                new ThemeDescription(pagedownButtonCounter, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_goDownButtonCounter),
                new ThemeDescription(pagedownButtonImage, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_goDownButton),
                new ThemeDescription(pagedownButtonImage, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chat_goDownButtonShadow),
                new ThemeDescription(pagedownButtonImage, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_goDownButtonIcon),

                new ThemeDescription(mentiondownButtonCounter, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_goDownButtonCounterBackground),
                new ThemeDescription(mentiondownButtonCounter, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_goDownButtonCounter),
                new ThemeDescription(mentiondownButtonImage, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_goDownButton),
                new ThemeDescription(mentiondownButtonImage, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chat_goDownButtonShadow),
                new ThemeDescription(mentiondownButtonImage, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_goDownButtonIcon),

                new ThemeDescription(avatarContainer.getTimeItem(), 0, null, null, null, null, Theme.key_chat_secretTimerBackground),
                new ThemeDescription(avatarContainer.getTimeItem(), 0, null, null, null, null, Theme.key_chat_secretTimerText),
        };
    }
}
