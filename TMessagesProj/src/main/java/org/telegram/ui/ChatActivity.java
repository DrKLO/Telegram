/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerMiddle;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.LongSparseArray;
import android.util.Property;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
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
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimationProperties;
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
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FragmentContextView;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;

@SuppressWarnings("unchecked")
public class ChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, DialogsActivity.DialogsActivityDelegate, LocationActivity.LocationActivityDelegate {

    protected TLRPC.Chat currentChat;
    protected TLRPC.User currentUser;
    protected TLRPC.EncryptedChat currentEncryptedChat;
    private boolean userBlocked;

    private ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();

    private HashMap<MessageObject, Boolean> alredyPlayedStickers = new HashMap<>();

    private Dialog closeChatDialog;
    private FrameLayout progressView;
    private View progressView2;
    private FrameLayout bottomOverlay;
    protected ChatActivityEnterView chatActivityEnterView;
    private View timeItem2;
    private ActionBarMenuItem attachItem;
    private ActionBarMenuItem headerItem;
    private ActionBarMenuItem editTextItem;
    private ActionBarMenuItem searchItem;
    private RadialProgressView progressBar;
    private ActionBarMenuSubItem addContactItem;
    private RecyclerListView chatListView;
    private int chatListViewClipTop;
    private GridLayoutManagerFixed chatLayoutManager;
    private ChatActivityAdapter chatAdapter;
    private TextView bottomOverlayChatText;
    private UnreadCounterTextView bottomOverlayChatText2;
    private RadialProgressView bottomOverlayProgress;
    private AnimatorSet bottomOverlayAnimation;
    private FrameLayout bottomOverlayChat;
    private FrameLayout bottomMessagesActionContainer;
    private TextView forwardButton;
    private TextView replyButton;
    private FrameLayout emptyViewContainer;
    private SizeNotifierFrameLayout contentView;
    private ChatBigEmptyView bigEmptyView;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ChatAvatarContainer avatarContainer;
    private TextView bottomOverlayText;
    private NumberTextView selectedMessagesCountTextView;
    private RecyclerListView stickersListView;
    private ImageView stickersPanelArrow;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private RecyclerListView.OnItemClickListener mentionsOnItemClickListener;
    private StickersAdapter stickersAdapter;
    private FrameLayout stickersPanel;
    private ActionBarMenuSubItem muteItem;
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
    private FrameLayout topChatPanelView;
    private AnimatorSet reportSpamViewAnimator;
    private TextView addToContactsButton;
    private TextView reportSpamButton;
    private ImageView closeReportSpam;
    private FragmentContextView fragmentContextView;
    private View replyLineView;
    private TextView emptyView;
    private TextView gifHintTextView;
    private TextView mediaBanTooltip;
    private HintView slowModeHint;
    private TextView voiceHintTextView;
    private HintView noSoundHintView;
    private HintView forwardHintView;
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
    private int hideDateDelay = 500;
    private InstantCameraView instantCameraView;
    private View overlayView;
    private boolean currentFloatingDateOnScreen;
    private boolean currentFloatingTopIsNotMessage;
    private AnimatorSet floatingDateAnimation;
    private boolean scrollingFloatingDate;
    private boolean scrollingChatListView;
    private boolean checkTextureViewPosition;
    private boolean searchingForUser;
    private TLRPC.User searchingUserMessages;
    private UndoView undoView;
    private boolean openKeyboardOnAttachMenuClose;

    private boolean inScheduleMode;
    private int scheduledMessagesCount = -1;

    private ArrayList<MessageObject> animatingMessageObjects = new ArrayList<>();
    private MessageObject needAnimateToMessage;

    private int scrollToPositionOnRecreate = -1;
    private int scrollToOffsetOnRecreate = 0;

    ArrayList<MessageObject> pollsToCheck = new ArrayList<>(10);

    private int editTextStart;
    private int editTextEnd;

    private boolean wasManualScroll;
    private boolean fixPaddingsInLayout;
    private boolean globalIgnoreLayout;

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
    private AnimatorSet editButtonAnimation;
    private AnimatorSet forwardButtonAnimation;

    private boolean openSearchKeyboard;

    private boolean waitingForReplyMessageLoad;

    private boolean ignoreAttachOnPause;

    private boolean allowStickersPanel;
    private boolean allowContextBotPanel;
    private boolean allowContextBotPanelSecond = true;
    private AnimatorSet runningAnimation;

    private MessageObject selectedObjectToEditCaption;
    private MessageObject selectedObject;
    private MessageObject.GroupedMessages selectedObjectGroup;
    private ArrayList<MessageObject> forwardingMessages;
    private MessageObject forwardingMessage;
    private MessageObject.GroupedMessages forwardingMessageGroup;
    private MessageObject replyingMessageObject;
    private int editingMessageObjectReqId;
    private MessageObject editingMessageObject;
    private boolean paused = true;
    private boolean pausedOnLastMessage;
    private boolean wasPaused;
    boolean firstOpen = true;
    private int replyImageSize;
    private int replyImageCacheType;
    private TLRPC.PhotoSize replyImageLocation;
    private TLRPC.PhotoSize replyImageThumbLocation;
    private TLObject replyImageLocationObject;
    private int pinnedImageSize;
    private int pinnedImageCacheType;
    private TLRPC.PhotoSize pinnedImageLocation;
    private TLRPC.PhotoSize pinnedImageThumbLocation;
    private TLObject pinnedImageLocationObject;
    private int linkSearchRequestId;
    private TLRPC.WebPage foundWebPage;
    private ArrayList<CharSequence> foundUrls;
    private String pendingLinkSearchString;
    private Runnable pendingWebPageTimeoutRunnable;
    private Runnable waitingForCharaterEnterRunnable;

    private boolean clearingHistory;

    private boolean openAnimationEnded;

    private boolean scrollToTopOnResume;
    private boolean forceScrollToTop;
    private boolean scrollToTopUnReadOnResume;
    private long dialog_id;
    private int lastLoadIndex;
    private SparseArray<MessageObject>[] selectedMessagesIds = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private SparseArray<MessageObject>[] selectedMessagesCanCopyIds = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private SparseArray<MessageObject>[] selectedMessagesCanStarIds = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private boolean hasUnfavedSelected;
    private int cantDeleteMessagesCount;
    private int cantForwardMessagesCount;
    private int canForwardMessagesCount;
    private int canEditMessagesCount;
    private ArrayList<Integer> waitingForLoad = new ArrayList<>();

    private int newUnreadMessageCount;
    private int prevSetUnreadCount = Integer.MIN_VALUE;
    private int newMentionsCount;
    private boolean hasAllMentionsLocal;

    private SparseArray<MessageObject>[] messagesDict = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<>();
    protected ArrayList<MessageObject> messages = new ArrayList<>();
    private LongSparseArray<ArrayList<MessageObject>> polls = new LongSparseArray<>();
    private LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
    private int[] maxMessageId = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE};
    private int[] minMessageId = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
    private int[] maxDate = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
    private int[] minDate = new int[2];
    private boolean[] endReached = new boolean[2];
    private boolean[] cacheEndReached = new boolean[2];
    private boolean[] forwardEndReached = new boolean[]{true, true};
    private boolean loading;
    private boolean firstLoading = true;
    private boolean firstUnreadSent = false;
    private int loadsCount;
    private int last_message_id = 0;
    private long mergeDialogId;

    private boolean showScrollToMessageError;
    private int startLoadFromMessageId;
    private int startLoadFromMessageIdSaved;
    private int startLoadFromMessageOffset = Integer.MAX_VALUE;
    private boolean needSelectFromMessageId;
    private int returnToMessageId;
    private int returnToLoadIndex;
    private int createUnreadMessageAfterId;
    private boolean createUnreadMessageAfterIdLoading;
    private boolean loadingFromOldPosition;

    private boolean first = true;
    private int first_unread_id;
    private boolean loadingForward;
    private MessageObject unreadMessageObject;
    private MessageObject scrollToMessage;
    private int highlightMessageId = Integer.MAX_VALUE;
    private int scrollToMessagePosition = -10000;
    private Runnable unselectRunnable;

    private String currentPicturePath;

    protected TLRPC.ChatFull chatInfo;
    protected TLRPC.UserFull userInfo;

    private SparseArray<TLRPC.BotInfo> botInfo = new SparseArray<>();
    private String botUser;
    private long inlineReturn;
    private MessageObject botButtons;
    private MessageObject botReplyButtons;
    private int botsCount;
    private boolean hasBotsCommands;
    private long chatEnterTime;
    private long chatLeaveTime;

    private boolean locationAlertShown;

    private String startVideoEdit;

    private FrameLayout videoPlayerContainer;
    private ChatMessageCell drawLaterRoundProgressCell;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;
    private boolean scrollToVideo;
    private Path aspectPath;
    private Paint aspectPaint;

    private Paint scrimPaint;
    private View scrimView;
    private AnimatorSet scrimAnimatorSet;
    private ActionBarPopupWindow scrimPopupWindow;

    private ChatActivityDelegate chatActivityDelegate;

    private interface ChatActivityDelegate {
        void openReplyMessage(int mid);
    }

    private class UnreadCounterTextView extends TextView {

        private int currentCounter;
        private String currentCounterString;
        private int textWidth;
        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();
        private int circleWidth;

        public UnreadCounterTextView(Context context) {
            super(context);
            textPaint.setTextSize(AndroidUtilities.dp(13));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(color);
            textPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
            paint.setColor(Theme.getColor(Theme.key_chat_goDownButtonCounterBackground));
        }

        public void updateCounter() {
            int newCount;
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup && chatInfo != null && chatInfo.linked_chat_id != 0) {
                TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(-chatInfo.linked_chat_id);
                if (dialog != null) {
                    newCount = dialog.unread_count;
                } else {
                    newCount = 0;
                }
            } else {
                newCount = 0;
            }
            if (currentCounter != newCount) {
                currentCounter = newCount;
                if (currentCounter == 0) {
                    currentCounterString = null;
                    circleWidth = 0;
                    setPadding(0, 0, 0, 0);
                } else {
                    currentCounterString = String.format("%d", currentCounter);
                    textWidth = (int) Math.ceil(textPaint.measureText(currentCounterString));
                    int newWidth = Math.max(AndroidUtilities.dp(20), AndroidUtilities.dp(12) + textWidth);
                    if (circleWidth != newWidth) {
                        circleWidth = newWidth;
                        setPadding(0, 0, circleWidth / 2 + AndroidUtilities.dp(7), 0);
                    }
                }
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (currentCounterString != null) {
                Layout layout = getLayout();
                if (layout != null && getLineCount() > 0) {
                    int lineWidth = (int) Math.ceil(layout.getLineWidth(0));
                    int x = (getMeasuredWidth() + (lineWidth - circleWidth)) / 2 + AndroidUtilities.dp(8);
                    rect.set(x, getMeasuredHeight() / 2 - AndroidUtilities.dp(10), x + circleWidth, getMeasuredHeight() / 2 + AndroidUtilities.dp(10));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
                    canvas.drawText(currentCounterString, rect.centerX() - textWidth / 2.0f, rect.top + AndroidUtilities.dp(14.5f), textPaint);
                }
            }
        }
    }

    private PhotoViewer.PhotoViewerProvider photoViewerProvider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
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
                    int[] coords = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = chatListView;
                    object.imageReceiver = imageReceiver;
                    if (needPreview) {
                        object.thumb = imageReceiver.getBitmapSafe();
                    }
                    object.radius = imageReceiver.getRoundRadius();
                    if (view instanceof ChatActionCell && currentChat != null) {
                        object.dialogId = -currentChat.id;
                    }
                    if (pinnedMessageView != null && pinnedMessageView.getTag() == null || topChatPanelView != null && topChatPanelView.getTag() == null) {
                        object.clipTopAddition = AndroidUtilities.dp(48);
                    }
                    object.clipTopAddition += chatListViewClipTop;
                    return object;
                }
            }
            return null;
        }
    };

    private ArrayList<Object> botContextResults;
    private PhotoViewer.PhotoViewerProvider botContextProvider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
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
                    int[] coords = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = mentionListView;
                    object.imageReceiver = imageReceiver;
                    object.thumb = imageReceiver.getBitmapSafe();
                    object.radius = imageReceiver.getRoundRadius();
                    return object;
                }
            }
            return null;
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate) {
            if (index < 0 || index >= botContextResults.size()) {
                return;
            }
            sendBotInlineResult((TLRPC.BotInlineResult) botContextResults.get(index), notify, scheduleDate);
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
    private final static int attach_poll = 9;

    private final static int text_bold = 50;
    private final static int text_italic = 51;
    private final static int text_mono = 52;
    private final static int text_link = 53;
    private final static int text_regular = 54;
    private final static int text_strike = 55;
    private final static int text_underline = 56;

    private final static int search = 40;

    private final static int id_chat_compose_panel = 1000;

    RecyclerListView.OnItemLongClickListenerExtended onItemLongClickListener = new RecyclerListView.OnItemLongClickListenerExtended() {
        @Override
        public boolean onItemClick(View view, int position, float x, float y) {
            wasManualScroll = true;
            if (!actionBar.isActionModeShowed()) {
                createMenu(view, false, true, x, y);
            } else {
                boolean outside = false;
                if (view instanceof ChatMessageCell) {
                    outside = !((ChatMessageCell) view).isInsideBackground(x, y);
                }
                processRowSelect(view, outside, x, y);
            }
            return true;
        }

        @Override
        public void onLongClickRelease() {

        }

        @Override
        public void onMove(float dx, float dy) {

        }
    };

    RecyclerListView.OnItemClickListenerExtended onItemClickListener = new RecyclerListView.OnItemClickListenerExtended() {
        @Override
        public void onItemClick(View view, int position, float x, float y) {
            wasManualScroll = true;
            if (actionBar.isActionModeShowed()) {
                boolean outside = false;
                if (view instanceof ChatMessageCell) {
                    outside = !((ChatMessageCell) view).isInsideBackground(x, y);
                }
                processRowSelect(view, outside, x, y);
                return;
            }
            createMenu(view, true, false, x, y);
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
        inScheduleMode = arguments.getBoolean("scheduled", false);
        inlineReturn = arguments.getLong("inline_return", 0);
        String inlineQuery = arguments.getString("inline_query");
        startLoadFromMessageId = arguments.getInt("message_id", 0);
        int migrated_to = arguments.getInt("migrated_to", 0);
        scrollToTopOnResume = arguments.getBoolean("scrollToTopOnResume", false);

        if (chatId != 0) {
            currentChat = getMessagesController().getChat(chatId);
            if (currentChat == null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                final MessagesStorage messagesStorage = getMessagesStorage();
                messagesStorage.getStorageQueue().postRunnable(() -> {
                    currentChat = messagesStorage.getChat(chatId);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (currentChat != null) {
                    getMessagesController().putChat(currentChat, true);
                } else {
                    return false;
                }
            }
            dialog_id = -chatId;
            if (ChatObject.isChannel(currentChat)) {
                getMessagesController().startShortPoll(currentChat, false);
            }
        } else if (userId != 0) {
            currentUser = getMessagesController().getUser(userId);
            if (currentUser == null) {
                final MessagesStorage messagesStorage = getMessagesStorage();
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                messagesStorage.getStorageQueue().postRunnable(() -> {
                    currentUser = messagesStorage.getUser(userId);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (currentUser != null) {
                    getMessagesController().putUser(currentUser, true);
                } else {
                    return false;
                }
            }
            dialog_id = userId;
            botUser = arguments.getString("botUser");
            if (inlineQuery != null) {
                getMessagesController().sendBotStart(currentUser, inlineQuery);
            }
        } else if (encId != 0) {
            currentEncryptedChat = getMessagesController().getEncryptedChat(encId);
            final MessagesStorage messagesStorage = getMessagesStorage();
            if (currentEncryptedChat == null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                messagesStorage.getStorageQueue().postRunnable(() -> {
                    currentEncryptedChat = messagesStorage.getEncryptedChat(encId);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (currentEncryptedChat != null) {
                    getMessagesController().putEncryptedChat(currentEncryptedChat, true);
                } else {
                    return false;
                }
            }
            currentUser = getMessagesController().getUser(currentEncryptedChat.user_id);
            if (currentUser == null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                messagesStorage.getStorageQueue().postRunnable(() -> {
                    currentUser = messagesStorage.getUser(currentEncryptedChat.user_id);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (currentUser != null) {
                    getMessagesController().putUser(currentUser, true);
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

        if (!inScheduleMode) {
            getNotificationCenter().addObserver(this, NotificationCenter.messagesRead);
            getNotificationCenter().addObserver(this, NotificationCenter.screenshotTook);
            getNotificationCenter().addObserver(this, NotificationCenter.encryptedChatUpdated);
            getNotificationCenter().addObserver(this, NotificationCenter.messagesReadEncrypted);
            getNotificationCenter().addObserver(this, NotificationCenter.removeAllMessagesFromDialog);
            getNotificationCenter().addObserver(this, NotificationCenter.messagesReadContent);
            getNotificationCenter().addObserver(this, NotificationCenter.botKeyboardDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.chatSearchResultsAvailable);
            getNotificationCenter().addObserver(this, NotificationCenter.chatSearchResultsLoading);
            getNotificationCenter().addObserver(this, NotificationCenter.didUpdatedMessagesViews);
            getNotificationCenter().addObserver(this, NotificationCenter.pinnedMessageDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.peerSettingsDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.newDraftReceived);
            getNotificationCenter().addObserver(this, NotificationCenter.updateMentionsCount);
            getNotificationCenter().addObserver(this, NotificationCenter.didUpdatePollResults);
            getNotificationCenter().addObserver(this, NotificationCenter.chatOnlineCountDidLoad);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        getNotificationCenter().addObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
        getNotificationCenter().addObserver(this, NotificationCenter.historyCleared);
        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByAck);
        getNotificationCenter().addObserver(this, NotificationCenter.messageSendError);
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingGoingToStop);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.blockedUsersDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.fileNewChunkAvailable);
        getNotificationCenter().addObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().addObserver(this, NotificationCenter.updateMessageMedia);
        getNotificationCenter().addObserver(this, NotificationCenter.replaceMessagesObjects);
        getNotificationCenter().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.replyMessagesDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.didReceivedWebpages);
        getNotificationCenter().addObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        getNotificationCenter().addObserver(this, NotificationCenter.botInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoCantLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.goingToPreviewTheme);
        getNotificationCenter().addObserver(this, NotificationCenter.channelRightsUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.audioRecordTooShort);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateReactions);
        getNotificationCenter().addObserver(this, NotificationCenter.videoLoadingStateChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.scheduledMessagesUpdated);

        super.onFragmentCreate();

        loading = true;
        if (!inScheduleMode) {
            if (currentEncryptedChat == null) {
                getMediaDataController().loadBotKeyboard(dialog_id);
            }
            getMessagesController().loadPeerSettings(currentUser, currentChat);
            getMessagesController().setLastCreatedDialogId(dialog_id, inScheduleMode, true);

            if (startLoadFromMessageId == 0) {
                SharedPreferences sharedPreferences = MessagesController.getNotificationsSettings(currentAccount);
                int messageId = sharedPreferences.getInt("diditem" + dialog_id, 0);
                if (messageId != 0) {
                    wasManualScroll = true;
                    loadingFromOldPosition = true;
                    startLoadFromMessageOffset = sharedPreferences.getInt("diditemo" + dialog_id, 0);
                    startLoadFromMessageId = messageId;
                }
            } else {
                showScrollToMessageError = true;
                needSelectFromMessageId = true;
            }
        }

        waitingForLoad.add(lastLoadIndex);
        if (startLoadFromMessageId != 0) {
            startLoadFromMessageIdSaved = startLoadFromMessageId;
            if (migrated_to != 0) {
                mergeDialogId = migrated_to;
                getMessagesController().loadMessages(mergeDialogId, loadingFromOldPosition ? 50 : (AndroidUtilities.isTablet() ? 30 : 20), startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
            } else {
                getMessagesController().loadMessages(dialog_id, loadingFromOldPosition ? 50 : (AndroidUtilities.isTablet() ? 30 : 20), startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
            }
        } else {
            getMessagesController().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, 0, true, 0, classGuid, 2, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
        }
        if (!inScheduleMode) {
            waitingForLoad.add(lastLoadIndex);
            getMessagesController().loadMessages(dialog_id, 1, 0, 0, true, 0, classGuid, 2, 0, ChatObject.isChannel(currentChat), true, lastLoadIndex++);
        }

        if (currentChat != null) {
            chatInfo = getMessagesController().getChatFull(currentChat.id);
            if (currentChat.megagroup && !getMessagesController().isChannelAdminsLoaded(currentChat.id)) {
                getMessagesController().loadChannelAdmins(currentChat.id, true);
            }
            TLRPC.ChatFull info = getMessagesStorage().loadChatInfo(currentChat.id, null, true, false);
            if (chatInfo == null) {
                chatInfo = info;
            }
            if (!inScheduleMode && chatInfo != null && ChatObject.isChannel(currentChat) && chatInfo.migrated_from_chat_id != 0) {
                mergeDialogId = -chatInfo.migrated_from_chat_id;
                maxMessageId[1] = chatInfo.migrated_from_max_id;
            }
        } else if (currentUser != null) {
            getMessagesController().loadUserInfo(currentUser, true, classGuid);
        }

        if (!inScheduleMode) {
            if (userId != 0 && currentUser.bot) {
                getMediaDataController().loadBotInfo(userId, true, classGuid);
            } else if (chatInfo instanceof TLRPC.TL_chatFull) {
                for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                    TLRPC.ChatParticipant participant = chatInfo.participants.participants.get(a);
                    TLRPC.User user = getMessagesController().getUser(participant.user_id);
                    if (user != null && user.bot) {
                        getMediaDataController().loadBotInfo(user.id, true, classGuid);
                    }
                }
            }
            if (AndroidUtilities.isTablet()) {
                getNotificationCenter().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, false);
            }

            if (currentUser != null) {
                userBlocked = getMessagesController().blockedUsers.indexOfKey(currentUser.id) >= 0;
            }

            if (currentEncryptedChat != null && AndroidUtilities.getMyLayerVersion(currentEncryptedChat.layer) != SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
                getSecretChatHelper().sendNotifyLayerMessage(currentEncryptedChat, null);
            }
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
        if (undoView != null) {
            undoView.hide(true, 0);
        }
        getMessagesController().setLastCreatedDialogId(dialog_id, inScheduleMode, false);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
        getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesRead);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDeleted);
        getNotificationCenter().removeObserver(this, NotificationCenter.historyCleared);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByServer);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByAck);
        getNotificationCenter().removeObserver(this, NotificationCenter.messageSendError);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.encryptedChatUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesReadEncrypted);
        getNotificationCenter().removeObserver(this, NotificationCenter.removeAllMessagesFromDialog);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        getNotificationCenter().removeObserver(this, NotificationCenter.screenshotTook);
        getNotificationCenter().removeObserver(this, NotificationCenter.blockedUsersDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.fileNewChunkAvailable);
        getNotificationCenter().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidStart);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingGoingToStop);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateMessageMedia);
        getNotificationCenter().removeObserver(this, NotificationCenter.replaceMessagesObjects);
        getNotificationCenter().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.replyMessagesDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.didReceivedWebpages);
        getNotificationCenter().removeObserver(this, NotificationCenter.didReceivedWebpagesInUpdates);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesReadContent);
        getNotificationCenter().removeObserver(this, NotificationCenter.botInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.botKeyboardDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatSearchResultsAvailable);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatSearchResultsLoading);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdatedMessagesViews);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoCantLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.pinnedMessageDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.peerSettingsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.newDraftReceived);
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.goingToPreviewTheme);
        getNotificationCenter().removeObserver(this, NotificationCenter.channelRightsUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateMentionsCount);
        getNotificationCenter().removeObserver(this, NotificationCenter.audioRecordTooShort);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdatePollResults);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateReactions);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatOnlineCountDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.videoLoadingStateChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.scheduledMessagesUpdated);

        if (!inScheduleMode && AndroidUtilities.isTablet()) {
            getNotificationCenter().postNotificationName(NotificationCenter.openedChatChanged, dialog_id, true);
        }
        if (currentUser != null) {
            MediaController.getInstance().stopMediaObserver();
        }
        if (currentEncryptedChat != null) {
            try {
                if (Build.VERSION.SDK_INT >= 23 && (SharedConfig.passcodeHash.length() == 0 || SharedConfig.allowScreenCapture)) {
                    MediaController.getInstance().setFlagSecure(this, false);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (currentUser != null) {
            getMessagesController().cancelLoadFullUser(currentUser.id);
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
            getMessagesController().startShortPoll(currentChat, true);
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
        cantForwardMessagesCount = 0;
        canForwardMessagesCount = 0;
        videoPlayerContainer = null;
        voiceHintTextView = null;

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

        if (stickersAdapter != null) {
            stickersAdapter.onDestroy();
            stickersAdapter = null;
        }

        Theme.createChatResources(context, false);

        actionBar.setAddToContainer(false);
        if (inPreviewMode) {
            actionBar.setBackButtonDrawable(null);
        } else {
            actionBar.setBackButtonDrawable(new BackDrawable(false));
        }
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
                        hideActionMode();
                        updatePinnedMessageView(true);
                        updateVisibleRows();
                    } else {
                        finishFragment();
                    }
                } else if (id == copy) {
                    String str = "";
                    int previousUid = 0;
                    for (int a = 1; a >= 0; a--) {
                        ArrayList<Integer> ids = new ArrayList<>();
                        for (int b = 0; b < selectedMessagesCanCopyIds[a].size(); b++) {
                            ids.add(selectedMessagesCanCopyIds[a].keyAt(b));
                        }
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
                            str += getMessageContent(messageObject, previousUid, ids.size() != 1 && (currentUser == null || !currentUser.self));
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
                    hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == delete) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    createDeleteMessagesAlert(null, null);
                } else if (id == forward) {
                    openForward();
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

                    AlertsCreator.createClearOrDeleteDialogAlert(ChatActivity.this, id == clear_history, currentChat, currentUser, currentEncryptedChat != null, (param) -> {
                        if (id == clear_history && ChatObject.isChannel(currentChat) && (!currentChat.megagroup || !TextUtils.isEmpty(currentChat.username))) {
                            getMessagesController().deleteDialog(dialog_id, 2, param);
                        } else {
                            if (id != clear_history) {
                                getNotificationCenter().removeObserver(ChatActivity.this, NotificationCenter.closeChats);
                                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                                finishFragment();
                                getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, dialog_id, currentUser, currentChat, param);
                            } else {
                                clearingHistory = true;
                                undoView.setAdditionalTranslationY(0);
                                undoView.showWithAction(dialog_id, id == clear_history ? UndoView.ACTION_CLEAR : UndoView.ACTION_DELETE, () -> {
                                    if (id == clear_history) {
                                        if (chatInfo != null && chatInfo.pinned_msg_id != 0) {
                                            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                                            preferences.edit().putInt("pin_" + dialog_id, chatInfo.pinned_msg_id).commit();
                                            updatePinnedMessageView(true);
                                        } else if (userInfo != null && userInfo.pinned_msg_id != 0) {
                                            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                                            preferences.edit().putInt("pin_" + dialog_id, userInfo.pinned_msg_id).commit();
                                            updatePinnedMessageView(true);
                                        }
                                        getMessagesController().deleteDialog(dialog_id, 1, param);
                                        clearingHistory = false;
                                        clearHistory(false);
                                        chatAdapter.notifyDataSetChanged();
                                    } else {
                                        if (isChat) {
                                            if (ChatObject.isNotInChat(currentChat)) {
                                                getMessagesController().deleteDialog(dialog_id, 0, param);
                                            } else {
                                                getMessagesController().deleteUserFromChat((int) -dialog_id, getMessagesController().getUser(getUserConfig().getClientUserId()), null);
                                            }
                                        } else {
                                            getMessagesController().deleteDialog(dialog_id, 0, param);
                                        }
                                        finishFragment();
                                    }
                                }, () -> {
                                    clearingHistory = false;
                                    chatAdapter.notifyDataSetChanged();
                                });
                                chatAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                } else if (id == share_contact) {
                    if (currentUser == null || getParentActivity() == null) {
                        return;
                    }
                    if (addToContactsButton.getTag() != null) {
                        shareMyContact((Integer) addToContactsButton.getTag(), null);
                    } else {
                        Bundle args = new Bundle();
                        args.putInt("user_id", currentUser.id);
                        args.putBoolean("addContact", true);
                        presentFragment(new ContactAddActivity(args));
                    }
                } else if (id == mute) {
                    toggleMute(false);
                } else if (id == add_shortcut) {
                    try {
                        getMediaDataController().installShortcut(currentUser.id);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (id == report) {
                    AlertsCreator.createReportAlert(getParentActivity(), dialog_id, 0, ChatActivity.this);
                } else if (id == star) {
                    for (int a = 0; a < 2; a++) {
                        for (int b = 0; b < selectedMessagesCanStarIds[a].size(); b++) {
                            MessageObject msg = selectedMessagesCanStarIds[a].valueAt(b);
                            getMediaDataController().addRecentSticker(MediaDataController.TYPE_FAVE, msg, msg.getDocument(), (int) (System.currentTimeMillis() / 1000), !hasUnfavedSelected);
                        }
                    }
                    for (int a = 1; a >= 0; a--) {
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                        selectedMessagesCanStarIds[a].clear();
                    }
                    hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == edit) {
                    MessageObject messageObject = null;
                    for (int a = 1; a >= 0; a--) {
                        if (messageObject == null && selectedMessagesIds[a].size() == 1) {
                            ArrayList<Integer> ids = new ArrayList<>();
                            for (int b = 0; b < selectedMessagesIds[a].size(); b++) {
                                ids.add(selectedMessagesIds[a].keyAt(b));
                            }
                            messageObject = messagesDict[a].get(ids.get(0));
                        }
                        selectedMessagesIds[a].clear();
                        selectedMessagesCanCopyIds[a].clear();
                        selectedMessagesCanStarIds[a].clear();
                    }
                    startEditingMessageObject(messageObject);
                    hideActionMode();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                } else if (id == chat_menu_attach) {
                    if (chatAttachAlert != null) {
                        chatAttachAlert.setEditingMessageObject(null);
                    }
                    openAttachMenu();
                } else if (id == bot_help) {
                    getSendMessagesHelper().sendMessage("/help", dialog_id, null, null, false, null, null, null, true, 0);
                } else if (id == bot_settings) {
                    getSendMessagesHelper().sendMessage("/settings", dialog_id, null, null, false, null, null, null, true, 0);
                } else if (id == search) {
                    openSearchWithText(null);
                } else if(id == call) {
                    if (currentUser != null && getParentActivity() != null) {
                        VoIPHelper.startCall(currentUser, getParentActivity(), getMessagesController().getUserFull(currentUser.id));
                    }
                } else if (id == text_bold) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedBold();
                    }
                } else if (id == text_italic) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedItalic();
                    }
                } else if (id == text_mono) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedMono();
                    }
                } else if (id == text_strike) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedStrike();
                    }
                } else if (id == text_underline) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedUnderline();
                    }
                } else if (id == text_link) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedUrl();
                    }
                } else if (id == text_regular) {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setSelectionOverride(editTextStart, editTextEnd);
                        chatActivityEnterView.getEditField().makeSelectedRegular();
                    }
                }
            }
        });

        avatarContainer = new ChatAvatarContainer(context, this, currentEncryptedChat != null);
        if (inPreviewMode) {
            avatarContainer.setOccupyStatusBar(false);
        }
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 56 : 0, 0, 40, 0));

        if (currentChat != null) {
            if (!ChatObject.isChannel(currentChat)) {
                int count = currentChat.participants_count;
                if (chatInfo != null) {
                    count = chatInfo.participants.participants.size();
                }
                if (count == 0 || currentChat.deactivated || currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden || chatInfo != null && chatInfo.participants instanceof TLRPC.TL_chatParticipantsForbidden) {
                    avatarContainer.setEnabled(false);
                }
            }
        }

        ActionBarMenu menu = actionBar.createMenu();

        if (currentEncryptedChat == null && !inScheduleMode) {
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
                    searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
                    searchItem.setSearchFieldCaption(null);
                    avatarContainer.setVisibility(View.VISIBLE);
                    if (editTextItem.getTag() != null) {
                        if (headerItem != null) {
                            headerItem.setVisibility(View.GONE);
                        }
                        if (editTextItem != null) {
                            editTextItem.setVisibility(View.VISIBLE);
                        }
                        if (attachItem != null) {
                            attachItem.setVisibility(View.GONE);
                        }
                    } else if (chatActivityEnterView.hasText()) {
                        if (headerItem != null) {
                            headerItem.setVisibility(View.GONE);
                        }
                        if (editTextItem != null) {
                            editTextItem.setVisibility(View.GONE);
                        }
                        if (attachItem != null) {
                            attachItem.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (headerItem != null) {
                            headerItem.setVisibility(View.VISIBLE);
                        }
                        if (editTextItem != null) {
                            editTextItem.setVisibility(View.GONE);
                        }
                        if (attachItem != null) {
                            attachItem.setVisibility(View.GONE);
                        }
                    }
                    searchItem.setVisibility(View.GONE);
                    removeSelectedMessageHighlight();
                    updateBottomOverlay();
                    updatePinnedMessageView(true);
                    updateVisibleRows();
                }

                @Override
                public void onSearchExpand() {
                    if (!openSearchKeyboard) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        searchWas = false;
                        searchItem.getSearchField().requestFocus();
                        AndroidUtilities.showKeyboard(searchItem.getSearchField());
                    }, 300);
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    searchWas = true;
                    updateSearchButtons(0, 0, -1);
                    getMediaDataController().searchMessagesInChat(editText.getText().toString(), dialog_id, mergeDialogId, classGuid, 0, searchingUserMessages);
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
                            searchItem.setSearchFieldText("", true);
                        }
                        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
                        searchCalendarButton.setVisibility(View.VISIBLE);
                        searchUserButton.setVisibility(View.VISIBLE);
                        searchingUserMessages = null;
                    }
                }

                @Override
                public boolean forceShowClear() {
                    return searchingForUser;
                }
            });
            searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
            searchItem.setVisibility(View.GONE);
        }

        if (!inScheduleMode) {
            headerItem = menu.addItem(0, R.drawable.ic_ab_other);
            headerItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            if (currentUser != null) {
                headerItem.addSubItem(call, R.drawable.msg_callback, LocaleController.getString("Call", R.string.Call));
                TLRPC.UserFull userFull = getMessagesController().getUserFull(currentUser.id);
                if (userFull != null && userFull.phone_calls_available) {
                    headerItem.showSubItem(call);
                } else {
                    headerItem.hideSubItem(call);
                }
            }

            editTextItem = menu.addItem(0, R.drawable.ic_ab_other);
            editTextItem.setTag(null);
            editTextItem.setVisibility(View.GONE);

            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(LocaleController.getString("Bold", R.string.Bold));
            stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            editTextItem.addSubItem(text_bold, stringBuilder);
            stringBuilder = new SpannableStringBuilder(LocaleController.getString("Italic", R.string.Italic));
            stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            editTextItem.addSubItem(text_italic, stringBuilder);
            stringBuilder = new SpannableStringBuilder(LocaleController.getString("Mono", R.string.Mono));
            stringBuilder.setSpan(new TypefaceSpan(Typeface.MONOSPACE), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            editTextItem.addSubItem(text_mono, stringBuilder);
            if (currentEncryptedChat == null || currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 101) {
                stringBuilder = new SpannableStringBuilder(LocaleController.getString("Strike", R.string.Strike));
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
                stringBuilder.setSpan(new TextStyleSpan(run), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                editTextItem.addSubItem(text_strike, stringBuilder);
                stringBuilder = new SpannableStringBuilder(LocaleController.getString("Underline", R.string.Underline));
                run = new TextStyleSpan.TextStyleRun();
                run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
                stringBuilder.setSpan(new TextStyleSpan(run), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                editTextItem.addSubItem(text_underline, stringBuilder);
            }
            editTextItem.addSubItem(text_link, LocaleController.getString("CreateLink", R.string.CreateLink));
            editTextItem.addSubItem(text_regular, LocaleController.getString("Regular", R.string.Regular));

            if (searchItem != null) {
                headerItem.addSubItem(search, R.drawable.msg_search, LocaleController.getString("Search", R.string.Search));
            }
            if (currentChat != null && !currentChat.creator) {
                headerItem.addSubItem(report, R.drawable.msg_report, LocaleController.getString("ReportChat", R.string.ReportChat));
            }
            if (currentUser != null) {
                addContactItem = headerItem.addSubItem(share_contact, R.drawable.msg_addcontact, "");
            }
            if (currentEncryptedChat != null) {
                timeItem2 = headerItem.addSubItem(chat_enc_timer, R.drawable.msg_timer, LocaleController.getString("SetTimer", R.string.SetTimer));
            }
            if (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup && TextUtils.isEmpty(currentChat.username)) {
                headerItem.addSubItem(clear_history, R.drawable.msg_clear, LocaleController.getString("ClearHistory", R.string.ClearHistory));
            }
            if (currentUser == null || !currentUser.self) {
                muteItem = headerItem.addSubItem(mute, R.drawable.msg_mute, null);
            }
            if (ChatObject.isChannel(currentChat) && !currentChat.creator) {
                if (!ChatObject.isNotInChat(currentChat)) {
                    if (currentChat.megagroup) {
                        headerItem.addSubItem(delete_chat, R.drawable.msg_leave, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
                    } else {
                        headerItem.addSubItem(delete_chat, R.drawable.msg_leave, LocaleController.getString("LeaveChannelMenu", R.string.LeaveChannelMenu));
                    }
                }
            } else if (!ChatObject.isChannel(currentChat)) {
                if (currentChat != null) {
                    headerItem.addSubItem(delete_chat, R.drawable.msg_leave, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit));
                } else {
                    headerItem.addSubItem(delete_chat, R.drawable.msg_delete, LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser));
                }
            }
            if (currentUser != null && currentUser.self) {
                headerItem.addSubItem(add_shortcut, R.drawable.msg_home, LocaleController.getString("AddShortcut", R.string.AddShortcut));
            }
            if (currentUser != null && currentEncryptedChat == null && currentUser.bot) {
                headerItem.addSubItem(bot_settings, R.drawable.menu_settings, LocaleController.getString("BotSettings", R.string.BotSettings));
                headerItem.addSubItem(bot_help, R.drawable.menu_help, LocaleController.getString("BotHelp", R.string.BotHelp));
                updateBotButtons();
            }
        }

        updateTitle();
        avatarContainer.updateOnlineCount();
        avatarContainer.updateSubtitle();
        updateTitleIcons();

        if (!inScheduleMode) {
            attachItem = menu.addItem(chat_menu_attach, R.drawable.ic_ab_other).setOverrideMenuClick(true).setAllowCloseAnimation(false);
            attachItem.setVisibility(View.GONE);
        }

        actionModeViews.clear();

        if (inPreviewMode) {
            headerItem.setAlpha(0.0f);
            attachItem.setAlpha(0.0f);
        }

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));
        selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);

        if (currentEncryptedChat == null) {
            actionModeViews.add(actionMode.addItemWithWidth(edit, R.drawable.msg_edit, AndroidUtilities.dp(54), LocaleController.getString("Edit", R.string.Edit)));
            actionModeViews.add(actionMode.addItemWithWidth(star, R.drawable.msg_fave, AndroidUtilities.dp(54), LocaleController.getString("AddToFavorites", R.string.AddToFavorites)));
            actionModeViews.add(actionMode.addItemWithWidth(copy, R.drawable.msg_copy, AndroidUtilities.dp(54), LocaleController.getString("Copy", R.string.Copy)));
            actionModeViews.add(actionMode.addItemWithWidth(forward, R.drawable.msg_forward, AndroidUtilities.dp(54), LocaleController.getString("Forward", R.string.Forward)));
            actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete)));
        } else {
            actionModeViews.add(actionMode.addItemWithWidth(edit, R.drawable.msg_edit, AndroidUtilities.dp(54), LocaleController.getString("Edit", R.string.Edit)));
            actionModeViews.add(actionMode.addItemWithWidth(star, R.drawable.msg_fave, AndroidUtilities.dp(54), LocaleController.getString("AddToFavorites", R.string.AddToFavorites)));
            actionModeViews.add(actionMode.addItemWithWidth(copy, R.drawable.msg_copy, AndroidUtilities.dp(54), LocaleController.getString("Copy", R.string.Copy)));
            actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete)));
        }
        actionMode.getItem(edit).setVisibility(canEditMessagesCount == 1 && selectedMessagesIds[0].size() + selectedMessagesIds[1].size() == 1 ? View.VISIBLE : View.GONE);
        actionMode.getItem(copy).setVisibility(selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);
        actionMode.getItem(star).setVisibility(selectedMessagesCanStarIds[0].size() + selectedMessagesCanStarIds[1].size() != 0 ? View.VISIBLE : View.GONE);
        actionMode.getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
        checkActionBarMenu();

        scrimPaint = new Paint() {
            @Override
            public void setAlpha(int a) {
                super.setAlpha(a);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };

        fragmentView = new SizeNotifierFrameLayout(context) {

            int inputFieldHeight = 0;

            ArrayList<ChatMessageCell> drawTimeAfter = new ArrayList<>();
            ArrayList<ChatMessageCell> drawNamesAfter = new ArrayList<>();
            ArrayList<ChatMessageCell> drawCaptionAfter = new ArrayList<>();

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && (messageObject.isRoundVideo() || messageObject.isVideo()) && messageObject.eventId == 0 && messageObject.getDialogId() == dialog_id) {
                    MediaController.getInstance().setTextureView(createTextureView(false), aspectRatioFrameLayout, videoPlayerContainer, true);
                }
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (scrimView != null || chatActivityEnterView != null && chatActivityEnterView.isStickersExpanded() && ev.getY() < chatActivityEnterView.getY()) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (scrimView != null && (child == pagedownButton || child == mentiondownButton || child == floatingDateView)) {
                    return false;
                }
                boolean result;
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                boolean isRoundVideo = false;
                boolean isVideo = messageObject != null && messageObject.eventId == 0 && ((isRoundVideo = messageObject.isRoundVideo()) || messageObject.isVideo());
                if (child == videoPlayerContainer) {
                    if (messageObject != null && messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
                        if (Theme.chat_roundVideoShadow != null && aspectRatioFrameLayout.isDrawingReady()) {
                            int x = (int) child.getX() - AndroidUtilities.dp(3);
                            int y = (int) child.getY() - AndroidUtilities.dp(2);
                            Theme.chat_roundVideoShadow.setAlpha(255);
                            Theme.chat_roundVideoShadow.setBounds(x, y, x + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6), y + AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6));
                            Theme.chat_roundVideoShadow.draw(canvas);
                        }
                        result = super.drawChild(canvas, child, drawingTime);
                    } else {
                        if (child.getTag() == null) {
                            float oldTranslation = child.getTranslationY();
                            child.setTranslationY(-AndroidUtilities.dp(1000));
                            result = super.drawChild(canvas, child, drawingTime);
                            child.setTranslationY(oldTranslation);
                        } else {
                            result = false;
                        }
                    }
                } else {
                    result = super.drawChild(canvas, child, drawingTime);
                    if (isVideo && child == chatListView && messageObject.type != 5 && videoPlayerContainer != null && videoPlayerContainer.getTag() != null) {
                        super.drawChild(canvas, videoPlayerContainer, drawingTime);
                        if (drawLaterRoundProgressCell != null) {
                            canvas.save();
                            canvas.translate(drawLaterRoundProgressCell.getX(), drawLaterRoundProgressCell.getTop() + chatListView.getY());
                            if (isRoundVideo) {
                                drawLaterRoundProgressCell.drawRoundProgress(canvas);
                                drawLaterRoundProgressCell.drawOverlays(canvas);
                            } else {
                                drawLaterRoundProgressCell.drawOverlays(canvas);
                                if (drawLaterRoundProgressCell.needDrawTime()) {
                                    drawLaterRoundProgressCell.drawTime(canvas);
                                }
                            }
                            canvas.restore();
                        }
                    }
                }
                if (child == actionBar && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() + (inPreviewMode && Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) : 0);
                }
                return result;
            }

            @Override
            protected boolean isActionBarVisible() {
                return actionBar.getVisibility() == VISIBLE;
            }

            private void drawChildElement(Canvas canvas, float listTop, ChatMessageCell cell, int type) {
                canvas.save();
                canvas.clipRect(chatListView.getLeft(), listTop, chatListView.getRight(), chatListView.getY() + chatListView.getMeasuredHeight());
                canvas.translate(chatListView.getLeft() + cell.getLeft(), chatListView.getY() + cell.getTop());
                if (type == 0) {
                    cell.drawTime(canvas);
                } else if (type == 1) {
                    cell.drawNamesLayout(canvas);
                } else {
                    cell.drawCaptionLayout(canvas, (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) == 0);
                }
                canvas.restore();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (scrimView != null) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
                    int chatListViewTop = (int) chatListView.getY();
                    int chatListViewBottom = chatListViewTop + chatListView.getMeasuredHeight();
                    int listTop = chatListView.getTop() + chatListView.getPaddingTop() - AndroidUtilities.dp(4) + (chatActivityEnterView.getMeasuredHeight() - AndroidUtilities.dp(51));
                    MessageObject.GroupedMessages scrimGroup;
                    if (scrimView instanceof ChatMessageCell) {
                        scrimGroup = ((ChatMessageCell) scrimView).getCurrentMessagesGroup();
                    } else {
                        scrimGroup = null;
                    }

                    int count = chatListView.getChildCount();
                    for (int num = 0; num < count; num++) {
                        View child = chatListView.getChildAt(num);
                        MessageObject.GroupedMessages group;
                        MessageObject.GroupedMessagePosition position;
                        ChatMessageCell cell;
                        if (child instanceof ChatMessageCell) {
                            cell = (ChatMessageCell) child;
                            group = cell.getCurrentMessagesGroup();
                            position = cell.getCurrentPosition();
                        } else {
                            position = null;
                            group = null;
                            cell = null;
                        }
                        if (child != scrimView && (scrimGroup == null || scrimGroup != group)) {
                            continue;
                        }

                        int clipLeft = 0;
                        int clipBottom = 0;
                        if (position != null) {
                            if (position.pw != position.spanSize && position.spanSize == 1000 && position.siblingHeights == null && group.hasSibling) {
                                clipLeft = cell.getBackgroundDrawableLeft();
                            } else if (position.siblingHeights != null) {
                                clipBottom = child.getBottom() - AndroidUtilities.dp(1 + (cell.isPinnedBottom() ? 1 : 0));
                            }
                        }
                        float viewClipLeft;
                        float viewClipRight;
                        float viewClipTop;
                        float viewClipBottom;
                        if (clipLeft != 0) {
                            float x = chatListView.getLeft() + clipLeft + child.getTranslationX();
                            float y = chatListView.getTop() + child.getTop();

                            viewClipLeft = Math.max(chatListView.getLeft(), x);
                            viewClipTop = Math.max(listTop, y);
                            viewClipRight = Math.min(chatListView.getRight(), x + child.getMeasuredWidth());
                            viewClipBottom = Math.min(chatListView.getY() + chatListView.getMeasuredHeight(), chatListView.getY() + child.getTop() + child.getMeasuredHeight());
                        } else if (clipBottom != 0) {
                            float x = chatListView.getLeft() + child.getTranslationX();
                            float y = chatListView.getTop() + child.getTop();

                            viewClipLeft = Math.max(chatListView.getLeft(), x);
                            viewClipTop = Math.max(listTop, y);
                            viewClipRight = Math.min(chatListView.getRight(), x + child.getMeasuredWidth());
                            viewClipBottom = Math.min(chatListView.getY() + chatListView.getMeasuredHeight(), chatListView.getY() + clipBottom);
                        } else {
                            viewClipLeft = Math.max(chatListView.getLeft(), chatListView.getLeft() + child.getX());
                            viewClipTop = Math.max(listTop, chatListView.getTop() + child.getY());
                            viewClipRight = Math.min(chatListView.getRight(), chatListView.getLeft() + child.getX() + child.getMeasuredWidth());
                            viewClipBottom = Math.min(chatListView.getY() + chatListView.getMeasuredHeight(), chatListView.getY() + child.getY() + child.getMeasuredHeight());
                        }
                        if (viewClipTop < viewClipBottom) {
                            canvas.save();
                            canvas.clipRect(viewClipLeft, viewClipTop, viewClipRight, viewClipBottom);
                            canvas.translate(chatListView.getLeft() + child.getLeft(), chatListView.getY() + child.getTop());
                            child.draw(canvas);
                            canvas.restore();
                        }

                        if (position != null) {
                            if (position.last || position.minX == 0 && position.minY == 0) {
                                if (position.last) {
                                    drawTimeAfter.add(cell);
                                }
                                if (position.minX == 0 && position.minY == 0 && cell.hasNameLayout()) {
                                    drawNamesAfter.add(cell);
                                }
                            }
                            if (cell.hasCaptionLayout() && (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                drawCaptionAfter.add(cell);
                            }
                        }
                    }
                    int size = drawTimeAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            drawChildElement(canvas, listTop, drawTimeAfter.get(a), 0);
                        }
                        drawTimeAfter.clear();
                    }
                    size = drawNamesAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            drawChildElement(canvas, listTop, drawNamesAfter.get(a), 1);
                        }
                        drawNamesAfter.clear();
                    }
                    size = drawCaptionAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            ChatMessageCell cell = drawCaptionAfter.get(a);
                            if (cell.getCurrentPosition() == null) {
                                continue;
                            }
                            drawChildElement(canvas, listTop, cell, 2);
                        }
                        drawCaptionAfter.clear();
                    }

                    if (pagedownButton != null && pagedownButton.getTag() != null) {
                        super.drawChild(canvas, pagedownButton, SystemClock.uptimeMillis());
                    }
                    if (mentiondownButton != null && mentiondownButton.getTag() != null) {
                        super.drawChild(canvas, mentiondownButton, SystemClock.uptimeMillis());
                    }
                    if (floatingDateView != null && floatingDateView.getTag() != null) {
                        super.drawChild(canvas, floatingDateView, SystemClock.uptimeMillis());
                    }
                }
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

                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        heightSize -= chatActivityEnterView.getEmojiPadding();
                        allHeight -= chatActivityEnterView.getEmojiPadding();
                    }
                } else {
                    globalIgnoreLayout = true;
                    chatActivityEnterView.hideEmojiView();
                    globalIgnoreLayout = false;
                }

                int childCount = getChildCount();

                measureChildWithMargins(chatActivityEnterView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int listViewTopHeight;
                if (inPreviewMode) {
                    inputFieldHeight = 0;
                    listViewTopHeight = 0;
                } else {
                    inputFieldHeight = chatActivityEnterView.getMeasuredHeight();
                    listViewTopHeight = AndroidUtilities.dp(49);
                }

                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == chatActivityEnterView || child == actionBar) {
                        continue;
                    }
                    if (child == chatListView) {
                        /*globalIgnoreLayout = true;
                        int additionalPadding = inputFieldHeight - AndroidUtilities.dp(51);
                        if (pinnedMessageView != null && pinnedMessageView.getTag() == null || topChatPanelView != null && topChatPanelView.getTag() == null) {
                            chatListView.setPadding(0, AndroidUtilities.dp(52) + additionalPadding, 0, AndroidUtilities.dp(3));
                        } else {
                            chatListView.setPadding(0, AndroidUtilities.dp(4) + additionalPadding, 0, AndroidUtilities.dp(3));
                        }
                        globalIgnoreLayout = false;*/
                        if (chatActivityEnterView.getAlpha() != 1.0f) {
                            chatListView.setTranslationY(inputFieldHeight - AndroidUtilities.dp(51));
                        }
                        chatListViewClipTop = inPreviewMode ? 0 : (inputFieldHeight - AndroidUtilities.dp(51));
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize - listViewTopHeight - (inPreviewMode && Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)), MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else if (child == progressView) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize - inputFieldHeight - (inPreviewMode && Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(2 + (chatActivityEnterView.isTopViewVisible() ? 48 : 0))), MeasureSpec.EXACTLY);
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
                            int height = child.getLayoutParams().height;
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
                if (fixPaddingsInLayout) {
                    globalIgnoreLayout = true;
                    checkListViewPaddingsInternal();
                    fixPaddingsInLayout = false;
                    chatListView.measure(MeasureSpec.makeMeasureSpec(chatListView.getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(chatListView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                    globalIgnoreLayout = false;
                }
                if (scrollToPositionOnRecreate != -1) {
                    final int scrollTo = scrollToPositionOnRecreate;
                    AndroidUtilities.runOnUIThread(() -> chatLayoutManager.scrollToPositionWithOffset(scrollTo, scrollToOffsetOnRecreate));
                    scrollToPositionOnRecreate = -1;
                }
            }

            @Override
            public void requestLayout() {
                if (globalIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? chatActivityEnterView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
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
                                if (inPreviewMode && Build.VERSION.SDK_INT >= 21) {
                                    childTop += AndroidUtilities.statusBarHeight;
                                }
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
                        if (!inPreviewMode) {
                            childTop -= chatActivityEnterView.getMeasuredHeight();
                        }
                    } else if (child == mentiondownButton) {
                        if (!inPreviewMode) {
                            childTop -= chatActivityEnterView.getMeasuredHeight();
                        }
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
                    } else if (child == chatListView) {
                        if (!inPreviewMode) {
                            childTop -= (inputFieldHeight - AndroidUtilities.dp(51));
                        }
                    } else if (child == progressView) {
                        if (chatActivityEnterView.isTopViewVisible()) {
                            childTop -= AndroidUtilities.dp(48);
                        }
                    } else if (child == actionBar) {
                        if (inPreviewMode && Build.VERSION.SDK_INT >= 21) {
                            childTop += AndroidUtilities.statusBarHeight;
                        }
                        childTop -= getPaddingTop();
                    } else if (child == videoPlayerContainer) {
                        childTop = actionBar.getMeasuredHeight();
                    } else if (child == instantCameraView || child == overlayView) {
                        childTop = 0;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                updateMessagesVisiblePart(true);
                updateTextureViewPosition(false);
                if (!scrollingChatListView) {
                    checkAutoDownloadMessages(false);
                }
                notifyHeightChanged();
            }
        };

        contentView = (SizeNotifierFrameLayout) fragmentView;

        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        emptyViewContainer = new FrameLayout(context);
        emptyViewContainer.setVisibility(View.INVISIBLE);
        contentView.addView(emptyViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        emptyViewContainer.setOnTouchListener((v, event) -> true);

        if (currentEncryptedChat == null) {
            if (!inScheduleMode && (currentUser != null && currentUser.self || currentChat != null && currentChat.creator)) {
                bigEmptyView = new ChatBigEmptyView(context, currentChat != null ? ChatBigEmptyView.EMPTY_VIEW_TYPE_GROUP : ChatBigEmptyView.EMPTY_VIEW_TYPE_SAVED);
                emptyViewContainer.addView(bigEmptyView, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                if (currentChat != null) {
                    bigEmptyView.setStatusText(AndroidUtilities.replaceTags(LocaleController.getString("GroupEmptyTitle1", R.string.GroupEmptyTitle1)));
                }
            } else {
                emptyView = new TextView(context);
                if (inScheduleMode) {
                    emptyView.setText(LocaleController.getString("NoScheduledMessages", R.string.NoScheduledMessages));
                } else if (currentUser != null && currentUser.id != 777000 && currentUser.id != 429000 && currentUser.id != 4244000 && MessagesController.isSupportUser(currentUser)) {
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
            bigEmptyView = new ChatBigEmptyView(context, ChatBigEmptyView.EMPTY_VIEW_TYPE_SECRET);
            if (currentEncryptedChat.admin_id == getUserConfig().getClientUserId()) {
                bigEmptyView.setStatusText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, UserObject.getFirstName(currentUser)));
            } else {
                bigEmptyView.setStatusText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, UserObject.getFirstName(currentUser)));
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

            private int lastWidth;

            ArrayList<ChatMessageCell> drawTimeAfter = new ArrayList<>();
            ArrayList<ChatMessageCell> drawNamesAfter = new ArrayList<>();
            ArrayList<ChatMessageCell> drawCaptionAfter = new ArrayList<>();

            private boolean maybeStartTracking;
            private boolean startedTracking;
            private boolean slideAnimationInProgress;
            private int startedTrackingX;
            private int startedTrackingY;
            private int startedTrackingPointerId;
            private long lastTrackingAnimationTime;
            private float trackAnimationProgress;
            private float endedTrackingX;
            private boolean wasTrackingVibrate;
            private ChatMessageCell slidingView;
            private float replyButtonProgress;
            private long lastReplyButtonAnimationTime;

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (lastWidth != r - l) {
                    lastWidth = r - l;
                    if (noSoundHintView != null) {
                        noSoundHintView.hide();
                    }
                    if (forwardHintView != null) {
                        forwardHintView.hide();
                    }
                    if (slowModeHint != null) {
                        slowModeHint.hide();
                    }
                }
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

            private void setGroupTranslationX(ChatMessageCell view, float dx) {
                MessageObject.GroupedMessages group = view.getCurrentMessagesGroup();
                if (group == null) {
                    return;
                }
                int count = getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    if (child == this || !(child instanceof ChatMessageCell)) {
                        continue;
                    }
                    ChatMessageCell cell = (ChatMessageCell) child;
                    if (cell.getCurrentMessagesGroup() == group) {
                        cell.setTranslationX(dx);
                        cell.invalidate();
                    }
                }
                invalidate();
            }

            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
                if (scrimPopupWindow != null) {
                    return false;
                }
                return super.requestChildRectangleOnScreen(child, rect, immediate);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                boolean result = super.onInterceptTouchEvent(e);
                if (actionBar.isActionModeShowed()) {
                    return result;
                }
                processTouchEvent(e);
                return result;
            }

            private void drawReplyButton(Canvas canvas) {
                if (slidingView == null) {
                    return;
                }
                float translationX = slidingView.getTranslationX();
                long newTime = System.currentTimeMillis();
                long dt = Math.min(17, newTime - lastReplyButtonAnimationTime);
                lastReplyButtonAnimationTime = newTime;
                boolean showing;
                if (showing = (translationX <= -AndroidUtilities.dp(50))) {
                    if (replyButtonProgress < 1.0f) {
                        replyButtonProgress += dt / 180.0f;
                        if (replyButtonProgress > 1.0f) {
                            replyButtonProgress = 1.0f;
                        } else {
                            invalidate();
                        }
                    }
                } else {
                    if (replyButtonProgress > 0.0f) {
                        replyButtonProgress -= dt / 180.0f;
                        if (replyButtonProgress < 0.0f) {
                            replyButtonProgress = 0;
                        } else {
                            invalidate();
                        }
                    }
                }
                int alpha;
                float scale;
                if (showing) {
                    if (replyButtonProgress <= 0.8f) {
                        scale = 1.2f * (replyButtonProgress / 0.8f);
                    } else {
                        scale = 1.2f - 0.2f * ((replyButtonProgress - 0.8f) / 0.2f);
                    }
                    alpha = (int) Math.min(255, 255 * (replyButtonProgress / 0.8f));
                } else {
                    scale = replyButtonProgress;
                    alpha = (int) Math.min(255, 255 * replyButtonProgress);
                }
                Theme.chat_shareDrawable.setAlpha(alpha);
                Theme.chat_replyIconDrawable.setAlpha(alpha);
                float x = getMeasuredWidth() + slidingView.getTranslationX() / 2;
                float y = slidingView.getTop() + slidingView.getMeasuredHeight() / 2;
                if (!Theme.isCustomTheme() || Theme.hasThemeKey(Theme.key_chat_shareBackground)) {
                    Theme.chat_shareDrawable.setColorFilter(Theme.getShareColorFilter(Theme.getColor(Theme.key_chat_shareBackground), false));
                } else {
                    Theme.chat_shareDrawable.setColorFilter(Theme.colorFilter2);
                }
                Theme.chat_shareDrawable.setBounds((int) (x - AndroidUtilities.dp(14) * scale), (int) (y - AndroidUtilities.dp(14) * scale), (int) (x + AndroidUtilities.dp(14) * scale), (int) (y + AndroidUtilities.dp(14) * scale));
                Theme.chat_shareDrawable.draw(canvas);
                Theme.chat_replyIconDrawable.setBounds((int) (x - AndroidUtilities.dp(7) * scale), (int) (y - AndroidUtilities.dp(6) * scale), (int) (x + AndroidUtilities.dp(7) * scale), (int) (y + AndroidUtilities.dp(5) * scale));
                Theme.chat_replyIconDrawable.draw(canvas);
                Theme.chat_shareDrawable.setAlpha(255);
                Theme.chat_replyIconDrawable.setAlpha(255);
            }

            private void processTouchEvent(MotionEvent e) {
                wasManualScroll = true;
                if (e.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                    View view = getPressedChildView();
                    if (view instanceof ChatMessageCell) {
                        slidingView = (ChatMessageCell) view;
                        MessageObject message = slidingView.getMessageObject();
                        if (inScheduleMode ||
                                currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 ||
                                getMessageType(message) == 1 && (message.getDialogId() == mergeDialogId || message.needDrawBluredPreview()) ||
                                currentEncryptedChat == null && message.getId() < 0 ||
                                bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE ||
                                currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !ChatObject.canPost(currentChat) && !currentChat.megagroup || !ChatObject.canSendMessages(currentChat))) {
                            slidingView = null;
                            return;
                        }
                        startedTrackingPointerId = e.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) e.getX();
                        startedTrackingY = (int) e.getY();
                    }
                } else if (slidingView != null && e.getAction() == MotionEvent.ACTION_MOVE && e.getPointerId(0) == startedTrackingPointerId) {
                    int dx = Math.max(AndroidUtilities.dp(-80), Math.min(0, (int) (e.getX() - startedTrackingX)));
                    int dy = Math.abs((int) e.getY() - startedTrackingY);
                    if (getScrollState() == SCROLL_STATE_IDLE && maybeStartTracking && !startedTracking && dx <= -AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        slidingView.onTouchEvent(event);
                        super.onInterceptTouchEvent(event);
                        event.recycle();
                        chatLayoutManager.setCanScrollVertically(false);
                        maybeStartTracking = false;
                        startedTracking = true;
                        startedTrackingX = (int) e.getX();
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (startedTracking) {
                        if (Math.abs(dx) >= AndroidUtilities.dp(50)) {
                            if (!wasTrackingVibrate) {
                                try {
                                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                } catch (Exception ignore) {

                                }
                                wasTrackingVibrate = true;
                            }
                        } else {
                            wasTrackingVibrate = false;
                        }
                        slidingView.setTranslationX(dx);
                        MessageObject messageObject = slidingView.getMessageObject();
                        if (messageObject.isRoundVideo() || messageObject.isVideo()) {
                            updateTextureViewPosition(false);
                        }
                        setGroupTranslationX(slidingView, dx);
                        invalidate();
                    }
                } else if (slidingView != null && e.getPointerId(0) == startedTrackingPointerId && (e.getAction() == MotionEvent.ACTION_CANCEL || e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (Math.abs(slidingView.getTranslationX()) >= AndroidUtilities.dp(50)) {
                        showFieldPanelForReply(slidingView.getMessageObject());
                    }
                    endedTrackingX = slidingView.getTranslationX();
                    lastTrackingAnimationTime = System.currentTimeMillis();
                    trackAnimationProgress = 0.0f;
                    invalidate();
                    maybeStartTracking = false;
                    startedTracking = false;
                    chatLayoutManager.setCanScrollVertically(true);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                boolean result = super.onTouchEvent(e);
                if (actionBar.isActionModeShowed()) {
                    return result;
                }
                processTouchEvent(e);
                return startedTracking || result;
            }

            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                super.requestDisallowInterceptTouchEvent(disallowIntercept);
                if (slidingView != null) {
                    endedTrackingX = slidingView.getTranslationX();
                    lastTrackingAnimationTime = System.currentTimeMillis();
                    trackAnimationProgress = 0.0f;
                    invalidate();
                    maybeStartTracking = false;
                    startedTracking = false;
                    chatLayoutManager.setCanScrollVertically(true);
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
            public void onDraw(Canvas c) {
                super.onDraw(c);
                if (slidingView != null) {
                    float translationX = slidingView.getTranslationX();
                    if (!maybeStartTracking && !startedTracking && endedTrackingX != 0 && translationX != 0) {
                        long newTime = System.currentTimeMillis();
                        long dt = newTime - lastTrackingAnimationTime;
                        trackAnimationProgress += dt / 180.0f;
                        if (trackAnimationProgress > 1.0f) {
                            trackAnimationProgress = 1.0f;
                        }
                        lastTrackingAnimationTime = newTime;
                        translationX = endedTrackingX * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(trackAnimationProgress));
                        if (translationX == 0) {
                            endedTrackingX = 0;
                        }
                        setGroupTranslationX(slidingView, translationX);
                        slidingView.setTranslationX(translationX);
                        MessageObject messageObject = slidingView.getMessageObject();
                        if (messageObject.isRoundVideo() || messageObject.isVideo()) {
                            updateTextureViewPosition(false);
                        }
                        invalidate();
                    }
                    drawReplyButton(c);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                drawLaterRoundProgressCell = null;
                int count = getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                        if (cell.isDrawingSelectionBackground() && (position == null || (position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0)) {
                            int color = Theme.getColor(Theme.key_chat_selectedBackground);
                            int alpha = Color.alpha(color);
                            Theme.chat_replyLinePaint.setColor(Theme.getColor(Theme.key_chat_selectedBackground));
                            Theme.chat_replyLinePaint.setAlpha((int) (alpha * cell.getHightlightAlpha()));
                            canvas.drawRect(0, cell.getTop(), getMeasuredWidth(), cell.getBottom(), Theme.chat_replyLinePaint);
                        }
                    }
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                int clipLeft = 0;
                int clipBottom = 0;
                boolean skipDraw = child == scrimView;
                ChatMessageCell cell;
                if (child instanceof ChatMessageCell) {
                    cell = (ChatMessageCell) child;
                    MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                    if (position != null) {
                        if (position.pw != position.spanSize && position.spanSize == 1000 && position.siblingHeights == null && group.hasSibling) {
                            clipLeft = cell.getBackgroundDrawableLeft();
                        } else if (position.siblingHeights != null) {
                            clipBottom = child.getBottom() - AndroidUtilities.dp(1 + (cell.isPinnedBottom() ? 1 : 0));
                        }
                    }
                    if (cell.needDelayRoundProgressDraw()) {
                        drawLaterRoundProgressCell = cell;
                    }
                    if (!skipDraw && scrimView instanceof ChatMessageCell) {
                        ChatMessageCell cell2 = (ChatMessageCell) scrimView;
                        if (cell2.getCurrentMessagesGroup() != null && cell2.getCurrentMessagesGroup() == group) {
                            skipDraw = true;
                        }
                    }
                } else {
                    cell = null;
                }
                if (clipLeft != 0) {
                    canvas.save();
                    //canvas.clipRect(clipLeft + child.getTranslationX(), child.getTop(), child.getRight() + child.getTranslationX(), child.getBottom());
                } else if (clipBottom != 0) {
                    canvas.save();
                    //canvas.clipRect(child.getLeft() + child.getTranslationX(), child.getTop(), child.getRight() + child.getTranslationX(), clipBottom);
                }
                boolean result;
                if (!skipDraw) {
                    result = super.drawChild(canvas, child, drawingTime);
                } else {
                    result = false;
                }
                if (clipLeft != 0 || clipBottom != 0) {
                    canvas.restore();
                }
                if (cell != null) {
                    cell.drawCheckBox(canvas);
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
                            cell = drawTimeAfter.get(a);
                            canvas.save();
                            canvas.translate(cell.getLeft() + cell.getTranslationX(), cell.getTop());
                            cell.drawTime(canvas);
                            canvas.restore();
                        }
                        drawTimeAfter.clear();
                    }
                    size = drawNamesAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            cell = drawNamesAfter.get(a);
                            canvas.save();
                            canvas.translate(cell.getLeft() + cell.getTranslationX(), cell.getTop());
                            cell.drawNamesLayout(canvas);
                            canvas.restore();
                        }
                        drawNamesAfter.clear();
                    }
                    size = drawCaptionAfter.size();
                    if (size > 0) {
                        for (int a = 0; a < size; a++) {
                            cell = drawCaptionAfter.get(a);
                            if (cell.getCurrentPosition() == null) {
                                continue;
                            }
                            canvas.save();
                            canvas.translate(cell.getLeft() + cell.getTranslationX(), cell.getTop());
                            cell.drawCaptionLayout(canvas, (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) == 0);
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
                                canvas.translate(chatMessageCell.getLeft() + chatMessageCell.getTranslationX(), chatMessageCell.getTop());
                                if (position.last) {
                                    chatMessageCell.drawTime(canvas);
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
                            canvas.translate(chatMessageCell.getLeft() + chatMessageCell.getTranslationX(), chatMessageCell.getTop());
                            if (chatMessageCell.hasCaptionLayout() && (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                chatMessageCell.drawCaptionLayout(canvas, (position.flags & MessageObject.POSITION_FLAG_LEFT) == 0);
                            }
                            canvas.restore();
                        } else {
                            if (chatMessageCell.hasCaptionLayout() && (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                drawCaptionAfter.add(chatMessageCell);
                            }
                        }
                    }
                    MessageObject message = chatMessageCell.getMessageObject();
                    if (videoPlayerContainer != null && (message.isRoundVideo() || message.isVideo()) && MediaController.getInstance().isPlayingMessage(message)) {
                        ImageReceiver imageReceiver = chatMessageCell.getPhotoImage();
                        float newX = imageReceiver.getImageX() + chatMessageCell.getX();
                        float newY = fragmentView.getPaddingTop() + chatMessageCell.getTop() + imageReceiver.getImageY() - chatListViewClipTop + chatListView.getTranslationY() + (inPreviewMode ? AndroidUtilities.statusBarHeight : 0);
                        if (videoPlayerContainer.getTranslationX() != newX || videoPlayerContainer.getTranslationY() != newY) {
                            videoPlayerContainer.setTranslationX(newX);
                            videoPlayerContainer.setTranslationY(newY);
                            fragmentView.invalidate();
                            videoPlayerContainer.invalidate();
                        }
                    }
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
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
                                        for (int a = idx + 1; a < size; a++) {
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
                        float tx = chatMessageCell.getTranslationX();
                        int y = child.getTop() + chatMessageCell.getLayoutHeight();
                        int maxY = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }
                        if (chatMessageCell.isPinnedTop()) {
                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            if (holder != null) {
                                int tries = 0;
                                while (true) {
                                    if (tries >= 20) {
                                        break;
                                    }
                                    tries++;
                                    int p = holder.getAdapterPosition();
                                    int prevPosition;
                                    if (groupedMessages != null && position != null) {
                                        int idx = groupedMessages.posArray.indexOf(position);
                                        if (idx < 0) {
                                            break;
                                        }
                                        int size = groupedMessages.posArray.size();
                                        if ((position.flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                                            prevPosition = p + idx + 1;
                                        } else {
                                            prevPosition = p + 1;
                                            for (int a = idx - 1; a >= 0; a--) {
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
                                        if (y - AndroidUtilities.dp(48) < holder.itemView.getBottom()) {
                                            tx = Math.min(holder.itemView.getTranslationX(), tx);
                                        }
                                        if (holder.itemView instanceof ChatMessageCell) {
                                            cell = (ChatMessageCell) holder.itemView;
                                            if (!cell.isPinnedTop()) {
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                        if (y - AndroidUtilities.dp(48) < top) {
                            y = top + AndroidUtilities.dp(48);
                        }
                        if (tx != 0) {
                            canvas.save();
                            canvas.translate(tx, 0);
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(44));
                        imageReceiver.draw(canvas);
                        if (tx != 0) {
                            canvas.restore();
                        }
                    }
                }
                return result;
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                if (currentEncryptedChat != null) {
                    return;
                }
                super.onInitializeAccessibilityNodeInfo(info);
                if (Build.VERSION.SDK_INT >= 19) {
                    AccessibilityNodeInfo.CollectionInfo collection = info.getCollectionInfo();
                    if (collection != null) {
                        info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(collection.getRowCount(), 1, false));
                    }
                }
            }

            @Override
            public AccessibilityNodeInfo createAccessibilityNodeInfo() {
                if (currentEncryptedChat != null) {
                    return null;
                }
                return super.createAccessibilityNodeInfo();
            }
        };
        if (currentEncryptedChat != null && Build.VERSION.SDK_INT >= 19) {
            chatListView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        chatListView.setInstantClick(true);
        chatListView.setDisableHighlightState(true);
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
                            int h = cell.getCaptionHeight();
                            for (int a = 0; a < position.siblingHeights.length; a++) {
                                h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                            }
                            h += (position.maxY - position.minY) * Math.round(7 * AndroidUtilities.density);
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
            private boolean scrollUp;
            private final int scrollValue = AndroidUtilities.dp(100);

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollingFloatingDate = false;
                    scrollingChatListView = false;
                    checkTextureViewPosition = false;
                    hideFloatingDateView(true);
                    checkAutoDownloadMessages(scrollUp);
                    if (SharedConfig.getDevicePerfomanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }
                } else {
                    if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        wasManualScroll = true;
                        scrollingChatListView = true;
                    } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        wasManualScroll = true;
                        scrollingFloatingDate = true;
                        checkTextureViewPosition = true;
                        scrollingChatListView = true;
                    }
                    if (SharedConfig.getDevicePerfomanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
                scrollUp = dy < 0;
                if (!wasManualScroll && dy != 0) {
                    wasManualScroll = true;
                }
                if (dy != 0) {
                    if (noSoundHintView != null) {
                        noSoundHintView.hide();
                    }
                    if (forwardHintView != null) {
                        forwardHintView.hide();
                    }
                }
                if (dy != 0 && scrollingFloatingDate && !currentFloatingTopIsNotMessage) {
                    if (highlightMessageId != Integer.MAX_VALUE) {
                        removeSelectedMessageHighlight();
                        updateVisibleRows();
                    }
                    showFloatingDateView(true);
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
                updateMessagesVisiblePart(true);
            }
        });

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

        floatingDateView = new ChatActionCell(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getAlpha() == 0 || actionBar.isActionModeShowed()) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (getAlpha() == 0 || actionBar.isActionModeShowed()) {
                    return false;
                }
                return super.onTouchEvent(event);
            }
        };
        floatingDateView.setAlpha(0.0f);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        floatingDateView.setOnClickListener(view -> {
            if (floatingDateView.getAlpha() == 0 || actionBar.isActionModeShowed()) {
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
        });

        if (currentEncryptedChat == null) {
            pinnedMessageView = new FrameLayout(context);
            pinnedMessageView.setTag(1);
            pinnedMessageView.setTranslationY(-AndroidUtilities.dp(50));
            pinnedMessageView.setVisibility(View.GONE);
            pinnedMessageView.setBackgroundResource(R.drawable.blockpanel);
            pinnedMessageView.getBackground().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelBackground), PorterDuff.Mode.MULTIPLY));
            contentView.addView(pinnedMessageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));
            pinnedMessageView.setOnClickListener(v -> {
                wasManualScroll = true;
                if (chatInfo != null) {
                    scrollToMessageId(chatInfo.pinned_msg_id, 0, true, 0, false);
                } else if (userInfo != null) {
                    scrollToMessageId(userInfo.pinned_msg_id, 0, true, 0, false);
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
            pinnedMessageView.addView(pinnedMessageNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(18), Gravity.TOP | Gravity.LEFT, 18, 7.3f, 40, 0));

            pinnedMessageTextView = new SimpleTextView(context);
            pinnedMessageTextView.setTextSize(14);
            pinnedMessageTextView.setTextColor(Theme.getColor(Theme.key_chat_topPanelMessage));
            pinnedMessageView.addView(pinnedMessageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(18), Gravity.TOP | Gravity.LEFT, 18, 25.3f, 40, 0));

            closePinned = new ImageView(context);
            closePinned.setImageResource(R.drawable.miniplayer_close);
            closePinned.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose), PorterDuff.Mode.MULTIPLY));
            closePinned.setScaleType(ImageView.ScaleType.CENTER);
            closePinned.setContentDescription(LocaleController.getString("Close", R.string.Close));
            pinnedMessageView.addView(closePinned, LayoutHelper.createFrame(36, 48, Gravity.RIGHT | Gravity.TOP));
            closePinned.setOnClickListener(v -> {
                if (getParentActivity() == null) {
                    return;
                }
                boolean allowPin;
                if (currentChat != null) {
                    allowPin = ChatObject.canPinMessages(currentChat);
                } else if (currentEncryptedChat == null) {
                    if (userInfo != null) {
                        allowPin = userInfo.can_pin_message;
                    } else {
                        allowPin = false;
                    }
                } else {
                    allowPin = false;
                }
                if (allowPin) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("UnpinMessageAlertTitle", R.string.UnpinMessageAlertTitle));
                    builder.setMessage(LocaleController.getString("UnpinMessageAlert", R.string.UnpinMessageAlert));
                    builder.setPositiveButton(LocaleController.getString("UnpinMessage", R.string.UnpinMessage), (dialogInterface, i) -> getMessagesController().pinMessage(currentChat, currentUser, 0, false));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (chatInfo != null) {
                        preferences.edit().putInt("pin_" + dialog_id, chatInfo.pinned_msg_id).commit();
                    } else if (userInfo != null) {
                        preferences.edit().putInt("pin_" + dialog_id, userInfo.pinned_msg_id).commit();
                    }
                    updatePinnedMessageView(true);
                }
            });
        }

        topChatPanelView = new FrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                if (addToContactsButton != null && addToContactsButton.getVisibility() == VISIBLE && reportSpamButton != null && reportSpamButton.getVisibility() == VISIBLE) {
                    width = (width - AndroidUtilities.dp(31)) / 2;
                }
                ignoreLayout = true;
                if (reportSpamButton != null && reportSpamButton.getVisibility() == VISIBLE) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) reportSpamButton.getLayoutParams();
                    layoutParams.width = width;
                    if (addToContactsButton != null && addToContactsButton.getVisibility() == VISIBLE) {
                        reportSpamButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(19), 0);
                        layoutParams.leftMargin = width;
                    } else {
                        reportSpamButton.setPadding(AndroidUtilities.dp(48), 0, AndroidUtilities.dp(48), 0);
                        layoutParams.leftMargin = 0;
                    }
                }
                if (addToContactsButton != null && addToContactsButton.getVisibility() == VISIBLE) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) addToContactsButton.getLayoutParams();
                    layoutParams.width = width;
                    if (reportSpamButton != null && reportSpamButton.getVisibility() == VISIBLE) {
                        addToContactsButton.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(4), 0);
                    } else {
                        addToContactsButton.setPadding(AndroidUtilities.dp(48), 0, AndroidUtilities.dp(48), 0);
                        layoutParams.leftMargin = 0;
                    }
                }
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        topChatPanelView.setTag(1);
        topChatPanelView.setTranslationY(-AndroidUtilities.dp(50));
        topChatPanelView.setVisibility(View.GONE);
        topChatPanelView.setBackgroundResource(R.drawable.blockpanel);
        topChatPanelView.getBackground().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelBackground), PorterDuff.Mode.MULTIPLY));
        contentView.addView(topChatPanelView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP | Gravity.LEFT));

        reportSpamButton = new TextView(context);
        reportSpamButton.setTextColor(Theme.getColor(Theme.key_chat_reportSpam));
        reportSpamButton.setTag(Theme.key_chat_reportSpam);
        reportSpamButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        reportSpamButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reportSpamButton.setSingleLine(true);
        reportSpamButton.setMaxLines(1);
        reportSpamButton.setGravity(Gravity.CENTER);
        topChatPanelView.addView(reportSpamButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, AndroidUtilities.dp(1)));
        reportSpamButton.setOnClickListener(v2 -> AlertsCreator.showBlockReportSpamAlert(ChatActivity.this, dialog_id, currentUser, currentChat, currentEncryptedChat, reportSpamButton.getTag(R.id.object_tag) != null, chatInfo, param -> {
            if (param == 0) {
                updateTopPanel(true);
            } else {
                finishFragment();
            }
        }));

        addToContactsButton = new TextView(context);
        addToContactsButton.setTextColor(Theme.getColor(Theme.key_chat_addContact));
        addToContactsButton.setVisibility(View.GONE);
        addToContactsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addToContactsButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addToContactsButton.setSingleLine(true);
        addToContactsButton.setMaxLines(1);
        addToContactsButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        addToContactsButton.setGravity(Gravity.CENTER);
        topChatPanelView.addView(addToContactsButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, AndroidUtilities.dp(1)));
        addToContactsButton.setOnClickListener(v -> {
            if (addToContactsButton.getTag() != null) {
                shareMyContact(1, null);
            } else {
                Bundle args = new Bundle();
                args.putInt("user_id", currentUser.id);
                args.putBoolean("addContact", true);
                ContactAddActivity activity = new ContactAddActivity(args);
                activity.setDelegate(() -> {
                    undoView.setAdditionalTranslationY(AndroidUtilities.dp(51));
                    undoView.showWithAction(dialog_id, UndoView.ACTION_CONTACT_ADDED, currentUser);
                });
                presentFragment(activity);
            }
        });

        closeReportSpam = new ImageView(context);
        closeReportSpam.setImageResource(R.drawable.miniplayer_close);
        closeReportSpam.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_topPanelClose), PorterDuff.Mode.MULTIPLY));
        closeReportSpam.setScaleType(ImageView.ScaleType.CENTER);
        topChatPanelView.addView(closeReportSpam, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
        closeReportSpam.setOnClickListener(v -> {
            getMessagesController().hidePeerSettingsBar(dialog_id, currentUser, currentChat);
            updateTopPanel(true);
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
        contentView.addView(pagedownButton, LayoutHelper.createFrame(66, 59, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -3, 5));
        pagedownButton.setOnClickListener(view -> {
            wasManualScroll = true;
            checkTextureViewPosition = true;
            if (createUnreadMessageAfterId != 0) {
                scrollToMessageId(createUnreadMessageAfterId, 0, false, returnToLoadIndex, false);
            } else if (returnToMessageId > 0) {
                scrollToMessageId(returnToMessageId, 0, true, returnToLoadIndex, false);
            } else {
                scrollToLastMessage(true);
            }
        });

        mentiondownButton = new FrameLayout(context);
        mentiondownButton.setVisibility(View.INVISIBLE);
        contentView.addView(mentiondownButton, LayoutHelper.createFrame(46, 59, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 7, 5));
        mentiondownButton.setOnClickListener(new View.OnClickListener() {

            private void loadLastUnreadMention() {
                wasManualScroll = true;
                if (hasAllMentionsLocal) {
                    getMessagesStorage().getUnreadMention(dialog_id, param -> {
                        if (param == 0) {
                            hasAllMentionsLocal = false;
                            loadLastUnreadMention();
                        } else {
                            scrollToMessageId(param, 0, false, 0, false);
                        }
                    });
                } else {
                    final MessagesStorage messagesStorage = getMessagesStorage();
                    TLRPC.TL_messages_getUnreadMentions req = new TLRPC.TL_messages_getUnreadMentions();
                    req.peer = getMessagesController().getInputPeer((int) dialog_id);
                    req.limit = 1;
                    req.add_offset = newMentionsCount - 1;
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        if (error != null || res.messages.isEmpty()) {
                            if (res != null) {
                                newMentionsCount = res.count;
                            } else {
                                newMentionsCount = 0;
                            }
                            messagesStorage.resetMentionsCount(dialog_id, newMentionsCount);
                            if (newMentionsCount == 0) {
                                hasAllMentionsLocal = true;
                                showMentionDownButton(false, true);
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
                            messagesStorage.markMessageAsMention(mid);
                            if (object != null) {
                                object.messageOwner.media_unread = true;
                                object.messageOwner.mentioned = true;
                            }
                            scrollToMessageId(id, 0, false, 0, false);
                        }
                    }));
                }
            }

            @Override
            public void onClick(View view) {
                loadLastUnreadMention();
            }
        });

        mentiondownButton.setOnLongClickListener(view -> {
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
                if (messageObject.messageOwner.mentioned && !messageObject.isContentUnread()) {
                    messageObject.setContentIsRead();
                }
            }
            newMentionsCount = 0;
            getMessagesController().markMentionsAsRead(dialog_id);
            hasAllMentionsLocal = true;
            showMentionDownButton(false, true);
            return true;
        });

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
        updateMessageListAccessibilityVisibility();
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
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, mentionListView, 0, null);
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
        mentionListView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, mentionListView, 0, mentionsOnItemClickListener, null));
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
                size.width = 0;
                size.height = 0;
                Object object = mentionsAdapter.getItem(i);
                if (object instanceof TLRPC.BotInlineResult) {
                    TLRPC.BotInlineResult inlineResult = (TLRPC.BotInlineResult) object;
                    if (inlineResult.document != null) {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(inlineResult.document.thumbs, 90);
                        size.width = thumb != null ? thumb.w : 100;
                        size.height = thumb != null ? thumb.h : 100;
                        for (int b = 0; b < inlineResult.document.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = inlineResult.document.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    } else if (inlineResult.content != null) {
                        for (int b = 0; b < inlineResult.content.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = inlineResult.content.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    } else if (inlineResult.thumb != null) {
                        for (int b = 0; b < inlineResult.thumb.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = inlineResult.thumb.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    } else if (inlineResult.photo != null) {
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(inlineResult.photo.sizes, AndroidUtilities.photoSize);
                        if (photoSize != null) {
                            size.width = photoSize.w;
                            size.height = photoSize.h;
                        }
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
                    } else if (!mentionLayoutManager.getReverseLayout()) {
                        mentionLayoutManager.scrollToPositionWithOffset(0, mentionLayoutManager.getReverseLayout() ? -10000 : 10000);
                    }
                    if (allowStickersPanel && (!mentionsAdapter.isBotContext() || (allowContextBotPanel || allowContextBotPanelSecond))) {
                        if (currentEncryptedChat != null && mentionsAdapter.isBotContext()) {
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
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
                        updateMessageListAccessibilityVisibility();
                        mentionContainer.setTag(null);
                        mentionListAnimation = new AnimatorSet();
                        mentionListAnimation.playTogether(
                                ObjectAnimator.ofFloat(mentionContainer, View.ALPHA, 0.0f, 1.0f)
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
                        updateMessageListAccessibilityVisibility();
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
                                ObjectAnimator.ofFloat(mentionContainer, View.ALPHA, 0.0f)
                        );
                        mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                    mentionContainer.setVisibility(View.GONE);
                                    mentionContainer.setTag(null);
                                    updateMessageListAccessibilityVisibility();
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
                        updateMessageListAccessibilityVisibility();
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
                if (getParentActivity() == null || result.content == null) {
                    return;
                }
                if (result.type.equals("video") || result.type.equals("web_player_video")) {
                    int[] size = MessageObject.getInlineResultWidthAndHeight(result);
                    EmbedBottomSheet.show(getParentActivity(), result.title != null ? result.title : "", result.description, result.content.url, result.content.url, size[0], size[1]);
                } else {
                    Browser.openUrl(getParentActivity(), result.content.url);
                }
            }
        }));
        if (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup) {
            mentionsAdapter.setBotInfo(botInfo);
        }
        mentionsAdapter.setParentFragment(this);
        mentionsAdapter.setChatInfo(chatInfo);
        mentionsAdapter.setNeedUsernames(currentChat != null);
        mentionsAdapter.setNeedBotContext(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
        mentionsAdapter.setBotsCount(currentChat != null ? botsCount : 1);
        mentionListView.setOnItemClickListener(mentionsOnItemClickListener = (view, position) -> {
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
                    searchItem.setSearchFieldHint(null);
                    searchItem.clearSearchText();
                    getMediaDataController().searchMessagesInChat("", dialog_id, mergeDialogId, classGuid, 0, searchingUserMessages);
                } else {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user != null) {
                        if (user.username != null) {
                            chatActivityEnterView.replaceWithText(start, len, "@" + user.username + " ", false);
                        } else {
                            String name = UserObject.getFirstName(user, false);
                            Spannable spannable = new SpannableString(name + " ");
                            spannable.setSpan(new URLSpanUserMention("" + user.id, 1), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            chatActivityEnterView.replaceWithText(start, len, spannable, false);
                        }
                    }
                }
            } else if (object instanceof String) {
                if (mentionsAdapter.isBotCommands()) {
                    if (inScheduleMode) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(currentUser), (notify, scheduleDate) -> {
                            getSendMessagesHelper().sendMessage((String) object, dialog_id, replyingMessageObject, null, false, null, null, null, notify, scheduleDate);
                            chatActivityEnterView.setFieldText("");
                            hideFieldPanel(false);
                        });
                    } else {
                        if (checkSlowMode(view)) {
                            return;
                        }
                        getSendMessagesHelper().sendMessage((String) object, dialog_id, replyingMessageObject, null, false, null, null, null, true, 0);
                        chatActivityEnterView.setFieldText("");
                        hideFieldPanel(false);
                    }
                } else {
                    chatActivityEnterView.replaceWithText(start, len, object + " ", false);
                }
            } else if (object instanceof TLRPC.BotInlineResult) {
                if (chatActivityEnterView.getFieldText() == null || !inScheduleMode && checkSlowMode(view)) {
                    return;
                }
                TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) object;
                if ((result.type.equals("photo") && (result.photo != null || result.content != null) ||
                        result.type.equals("gif") && (result.document != null || result.content != null) ||
                        result.type.equals("video") && (result.document != null/* || result.content_url != null*/))) {
                    ArrayList<Object> arrayList = botContextResults = new ArrayList<>(mentionsAdapter.getSearchResultBotContext());
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, mentionsAdapter.getItemPosition(position), 3, botContextProvider, ChatActivity.this);
                } else {
                    if (inScheduleMode) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(currentUser), (notify, scheduleDate) -> sendBotInlineResult(result, notify, scheduleDate));
                    } else {
                        sendBotInlineResult(result, true, 0);
                    }
                }
            } else if (object instanceof TLRPC.TL_inlineBotSwitchPM) {
                processInlineBotContextPM((TLRPC.TL_inlineBotSwitchPM) object);
            } else if (object instanceof MediaDataController.KeywordResult) {
                String code = ((MediaDataController.KeywordResult) object).emoji;
                chatActivityEnterView.addEmojiToRecent(code);
                chatActivityEnterView.replaceWithText(start, len, code, true);
            }
        });

        mentionListView.setOnItemLongClickListener((view, position) -> {
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
                    builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> mentionsAdapter.clearRecentHashtags());
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                    return true;
                }
            }
            return false;
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

        pagedownButton.addView(pagedownButtonImage, LayoutHelper.createFrame(46, 46, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        pagedownButton.setContentDescription(LocaleController.getString("AccDescrPageDown", R.string.AccDescrPageDown));

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
        mentiondownButton.setContentDescription(LocaleController.getString("AccDescrMentionDown", R.string.AccDescrMentionDown));

        if (!AndroidUtilities.isTablet() || AndroidUtilities.isSmallTablet()) {
            FragmentContextView fragmentLocationContextView = new FragmentContextView(context, this, true);
            contentView.addView(fragmentLocationContextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
            contentView.addView(fragmentContextView = new FragmentContextView(context, this, false), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
            fragmentContextView.setAdditionalContextView(fragmentLocationContextView);
            fragmentLocationContextView.setAdditionalContextView(fragmentContextView);
        }

        contentView.addView(actionBar);

        overlayView = new View(context);
        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                checkRecordLocked();
            }
            overlayView.getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        });
        contentView.addView(overlayView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        overlayView.setVisibility(View.GONE);

        instantCameraView = new InstantCameraView(context, this);
        contentView.addView(instantCameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        bottomMessagesActionContainer = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        bottomMessagesActionContainer.setVisibility(View.INVISIBLE);
        bottomMessagesActionContainer.setWillNotDraw(false);
        bottomMessagesActionContainer.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        contentView.addView(bottomMessagesActionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomMessagesActionContainer.setOnTouchListener((v, event) -> true);

        chatActivityEnterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getAlpha() != 1.0f) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (getAlpha() != 1.0f) {
                    return false;
                }
                return super.onTouchEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (getAlpha() != 1.0f) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message, boolean notify, int scheduleDate) {
                if (!inScheduleMode) {
                    moveScrollToLastMessage();
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.addHashtagsFromMessage(message);
                }
                if (scheduleDate != 0) {
                    if (scheduledMessagesCount == -1) {
                        scheduledMessagesCount = 0;
                    }
                    if (message != null) {
                        scheduledMessagesCount++;
                    }
                    if (forwardingMessages != null && !forwardingMessages.isEmpty()) {
                        scheduledMessagesCount += forwardingMessages.size();
                    }
                    updateScheduledInterface(false);
                }
                hideFieldPanel(notify, scheduleDate, false);
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
            public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {
                showSlowModeHint(button, show, time);
            }

            @Override
            public void onTextSelectionChanged(int start, int end) {
                if (editTextItem == null) {
                    return;
                }
                if (end - start > 0) {
                    if (editTextItem.getTag() == null) {
                        editTextItem.setTag(1);
                        editTextItem.setVisibility(View.VISIBLE);
                        headerItem.setVisibility(View.GONE);
                        attachItem.setVisibility(View.GONE);
                    }
                    editTextStart = start;
                    editTextEnd = end;
                } else {
                    if (editTextItem.getTag() != null) {
                        editTextItem.setTag(null);
                        editTextItem.setVisibility(View.GONE);
                        if (chatActivityEnterView.hasText()) {
                            headerItem.setVisibility(View.GONE);
                            attachItem.setVisibility(View.VISIBLE);
                        } else {
                            headerItem.setVisibility(View.VISIBLE);
                            attachItem.setVisibility(View.GONE);
                        }
                    }
                }
            }

            @Override
            public void onTextChanged(final CharSequence text, boolean bigChange) {
                MediaController.getInstance().setInputFieldHasText(!TextUtils.isEmpty(text) || chatActivityEnterView.isEditingMessage());
                if (stickersAdapter != null && chatActivityEnterView != null && chatActivityEnterView.getVisibility() == View.VISIBLE && (bottomOverlay == null || bottomOverlay.getVisibility() != View.VISIBLE)) {
                    stickersAdapter.loadStikersForEmoji(text, currentChat != null && !ChatObject.canSendStickers(currentChat) || chatActivityEnterView.isEditingMessage());
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.searchUsernameOrHashtag(text.toString(), chatActivityEnterView.getCursorPosition(), messages, false);
                }
                if (waitingForCharaterEnterRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(waitingForCharaterEnterRunnable);
                    waitingForCharaterEnterRunnable = null;
                }
                if ((currentChat == null || ChatObject.canSendEmbed(currentChat)) && chatActivityEnterView.isMessageWebPageSearchEnabled() && (!chatActivityEnterView.isEditingMessage() || !chatActivityEnterView.isEditingCaption())) {
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
            public void onTextSpansChanged(CharSequence text) {
                searchLinks(text, true);
            }

            @Override
            public void needSendTyping() {
                getMessagesController().sendTyping(dialog_id, 0, classGuid);
            }

            @Override
            public void onAttachButtonHidden() {
                if (actionBar.isSearchFieldVisible()) {
                    return;
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.GONE);
                }
                if (editTextItem != null) {
                    editTextItem.setVisibility(View.GONE);
                }
                if (attachItem != null) {
                    attachItem.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onAttachButtonShow() {
                if (actionBar.isSearchFieldVisible()) {
                    return;
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.VISIBLE);
                }
                if (editTextItem != null) {
                    editTextItem.setVisibility(View.GONE);
                }
                if (attachItem != null) {
                    attachItem.setVisibility(View.GONE);
                }
            }

            @Override
            public void onMessageEditEnd(boolean loading) {
                if (!loading) {
                    mentionsAdapter.setNeedBotContext(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
                    if (editingMessageObject != null) {
                        hideFieldPanel(false);
                    }
                    chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
                    if (editingMessageObjectReqId != 0) {
                        getConnectionsManager().cancelRequest(editingMessageObjectReqId, true);
                        editingMessageObjectReqId = 0;
                    }
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
                        updateMessageListAccessibilityVisibility();
                    }
                } else {
                    allowStickersPanel = true;
                    if (stickersPanel.getVisibility() == View.INVISIBLE) {
                        stickersPanel.setVisibility(View.VISIBLE);
                    }
                    if (mentionContainer != null && mentionContainer.getVisibility() == View.INVISIBLE && (!mentionsAdapter.isBotContext() || (allowContextBotPanel || allowContextBotPanelSecond))) {
                        mentionContainer.setVisibility(View.VISIBLE);
                        mentionContainer.setTag(null);
                        updateMessageListAccessibilityVisibility();
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
                if (chatAttachAlert != null) {
                    chatAttachAlert.setEditingMessageObject(null);
                }
                openAttachMenu();
            }

            @Override
            public void needStartRecordVideo(int state, boolean notify, int scheduleDate) {
                if (instantCameraView != null) {
                    if (state == 0) {
                        instantCameraView.showCamera();
                    } else if (state == 1 || state == 3 || state == 4) {
                        instantCameraView.send(state, notify, scheduleDate);
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
                int visibility = state == 0 ? View.GONE : View.VISIBLE;
                if (overlayView.getVisibility() != visibility) {
                    overlayView.setVisibility(visibility);
                }
            }

            @Override
            public void needShowMediaBanHint() {
                showMediaBannedHint();
            }

            @Override
            public void onStickersExpandedChange() {
                checkRaiseSensors();
            }

            @Override
            public void scrollToSendingMessage() {
                int id = getSendMessagesHelper().getSendingMessageId(dialog_id);
                if (id != 0) {
                    scrollToMessageId(id, 0, true, 0, false);
                }
            }

            @Override
            public boolean hasScheduledMessages() {
                return scheduledMessagesCount > 0 && !inScheduleMode;
            }

            @Override
            public void openScheduledMessages() {
                ChatActivity.this.openScheduledMessages();
            }
        });
        chatActivityEnterView.setDialogId(dialog_id, currentAccount);
        if (chatInfo != null) {
            chatActivityEnterView.setChatInfo(chatInfo);
        }
        chatActivityEnterView.setId(id_chat_compose_panel);
        chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
        chatActivityEnterView.setMinimumHeight(AndroidUtilities.dp(51));
        chatActivityEnterView.setAllowStickersAndGifs(currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23, currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46);
        if (inPreviewMode) {
            chatActivityEnterView.setVisibility(View.INVISIBLE);
        }
        contentView.addView(chatActivityEnterView, contentView.getChildCount() - 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

        FrameLayout replyLayout = new FrameLayout(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.invalidate();
                }
                if (getVisibility() != GONE) {
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
                    updateMessagesVisiblePart(false);
                    if (fragmentView != null) {
                        fragmentView.invalidate();
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

        replyLineView = new View(context);
        replyLineView.setBackgroundColor(Theme.getColor(Theme.key_chat_replyPanelLine));
        chatActivityEnterView.addTopView(replyLayout, replyLineView, 48);

        replyLayout.setOnClickListener(v -> {
            if (forwardingMessages != null && !forwardingMessages.isEmpty()) {
                for (int a = 0, N = forwardingMessages.size(); a < N; a++) {
                    MessageObject messageObject = forwardingMessages.get(a);
                    selectedMessagesIds[0].put(messageObject.getId(), messageObject);
                }
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 3);
                args.putInt("messagesCount", forwardingMessages.size());
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate(this);
                presentFragment(fragment);
            } else if (replyingMessageObject != null) {
                scrollToMessageId(replyingMessageObject.getId(), 0, true, 0, false);
            } else if (editingMessageObject != null && editingMessageObject.canEditMedia() && editingMessageObjectReqId == 0) {
                if (chatAttachAlert == null) {
                    createChatAttachView();
                }
                chatAttachAlert.setEditingMessageObject(editingMessageObject);
                openAttachMenu();
            }
        });

        replyIconImageView = new ImageView(context);
        replyIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons), PorterDuff.Mode.MULTIPLY));
        replyIconImageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(replyIconImageView, LayoutHelper.createFrame(52, 46, Gravity.TOP | Gravity.LEFT));

        replyCloseImageView = new ImageView(context);
        replyCloseImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelClose), PorterDuff.Mode.MULTIPLY));
        replyCloseImageView.setImageResource(R.drawable.input_clear);
        replyCloseImageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(replyCloseImageView, LayoutHelper.createFrame(52, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 0, 0));
        replyCloseImageView.setOnClickListener(v -> {
            if (forwardingMessages != null) {
                forwardingMessages.clear();
            }
            showFieldPanel(false, null, null, null, foundWebPage, true, 0, true, true);
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

        final ContentPreviewViewer.ContentPreviewViewerDelegate contentPreviewViewerDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
            @Override
            public void sendSticker(TLRPC.Document sticker, Object parent, boolean notify, int scheduleDate) {
                chatActivityEnterView.onStickerSelected(sticker, parent, true, notify, scheduleDate);
            }

            @Override
            public boolean needSend() {
                return false;
            }

            @Override
            public boolean canSchedule() {
                return ChatActivity.this.canScheduleMessage();
            }

            @Override
            public boolean isInScheduleMode() {
                return inScheduleMode;
            }

            @Override
            public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {
                if (set == null || getParentActivity() == null) {
                    return;
                }
                TLRPC.TL_inputStickerSetID inputStickerSet = new TLRPC.TL_inputStickerSetID();
                inputStickerSet.access_hash = set.access_hash;
                inputStickerSet.id = set.id;
                StickersAlert alert = new StickersAlert(getParentActivity(), ChatActivity.this, inputStickerSet, null, chatActivityEnterView);
                alert.setClearsInputField(clearsInputField);
                showDialog(alert);
            }
        };
        stickersListView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersListView, 0, contentPreviewViewerDelegate);
                return super.onInterceptTouchEvent(event) || result;
            }
        };
        stickersListView.setTag(3);
        stickersListView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, stickersListView, 0, stickersOnItemClickListener, contentPreviewViewerDelegate));
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
        stickersPanel.addView(stickersPanelArrow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 53, 0, 53, 0));

        searchContainer = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        searchContainer.setOnTouchListener((v, event) -> true);
        searchContainer.setWillNotDraw(false);
        searchContainer.setVisibility(View.INVISIBLE);
        searchContainer.setFocusable(true);
        searchContainer.setFocusableInTouchMode(true);
        searchContainer.setClickable(true);
        searchContainer.setPadding(0, AndroidUtilities.dp(3), 0, 0);

        searchUpButton = new ImageView(context);
        searchUpButton.setScaleType(ImageView.ScaleType.CENTER);
        searchUpButton.setImageResource(R.drawable.msg_go_up);
        searchUpButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchUpButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchContainer.addView(searchUpButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 48, 0));
        searchUpButton.setOnClickListener(view -> getMediaDataController().searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 1, searchingUserMessages));
        searchUpButton.setContentDescription(LocaleController.getString("AccDescrSearchNext", R.string.AccDescrSearchNext));

        searchDownButton = new ImageView(context);
        searchDownButton.setScaleType(ImageView.ScaleType.CENTER);
        searchDownButton.setImageResource(R.drawable.msg_go_down);
        searchDownButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchDownButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchContainer.addView(searchDownButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 0, 0));
        searchDownButton.setOnClickListener(view -> getMediaDataController().searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 2, searchingUserMessages));
        searchDownButton.setContentDescription(LocaleController.getString("AccDescrSearchPrev", R.string.AccDescrSearchPrev));

        if (currentChat != null && (!ChatObject.isChannel(currentChat) || currentChat.megagroup)) {
            searchUserButton = new ImageView(context);
            searchUserButton.setScaleType(ImageView.ScaleType.CENTER);
            searchUserButton.setImageResource(R.drawable.msg_usersearch);
            searchUserButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
            searchUserButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
            searchContainer.addView(searchUserButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 48, 0, 0, 0));
            searchUserButton.setOnClickListener(view -> {
                mentionLayoutManager.setReverseLayout(true);
                mentionsAdapter.setSearchingMentions(true);
                searchCalendarButton.setVisibility(View.GONE);
                searchUserButton.setVisibility(View.GONE);
                searchingForUser = true;
                searchingUserMessages = null;
                searchItem.setSearchFieldHint(LocaleController.getString("SearchMembers", R.string.SearchMembers));
                searchItem.setSearchFieldCaption(LocaleController.getString("SearchFrom", R.string.SearchFrom));
                AndroidUtilities.showKeyboard(searchItem.getSearchField());
                searchItem.clearSearchText();
            });
            searchUserButton.setContentDescription(LocaleController.getString("AccDescrSearchByUser", R.string.AccDescrSearchByUser));
        }

        searchCalendarButton = new ImageView(context);
        searchCalendarButton.setScaleType(ImageView.ScaleType.CENTER);
        searchCalendarButton.setImageResource(R.drawable.msg_calendar);
        searchCalendarButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchCalendarButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchContainer.addView(searchCalendarButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        searchCalendarButton.setOnClickListener(view -> {
            if (getParentActivity() == null) {
                return;
            }
            AndroidUtilities.hideKeyboard(searchItem.getSearchField());
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int monthOfYear = calendar.get(Calendar.MONTH);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            try {
                DatePickerDialog dialog = new DatePickerDialog(getParentActivity(), (view1, year1, month, dayOfMonth1) -> {
                    Calendar calendar1 = Calendar.getInstance();
                    calendar1.clear();
                    calendar1.set(year1, month, dayOfMonth1);
                    int date = (int) (calendar1.getTime().getTime() / 1000);
                    clearChatData();
                    waitingForLoad.add(lastLoadIndex);
                    getMessagesController().loadMessages(dialog_id, 30, 0, date, true, 0, classGuid, 4, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
                }, year, monthOfYear, dayOfMonth);
                final DatePicker datePicker = dialog.getDatePicker();
                datePicker.setMinDate(1375315200000L);
                datePicker.setMaxDate(System.currentTimeMillis());
                dialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("JumpToDate", R.string.JumpToDate), dialog);
                dialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog1, which) -> {

                });
                if (Build.VERSION.SDK_INT >= 21) {
                    dialog.setOnShowListener(dialog12 -> {
                        int count = datePicker.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = datePicker.getChildAt(a);
                            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                            layoutParams.width = LayoutHelper.MATCH_PARENT;
                            child.setLayoutParams(layoutParams);
                        }
                    });
                }
                showDialog(dialog);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        searchCalendarButton.setContentDescription(LocaleController.getString("JumpToDate", R.string.JumpToDate));

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
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int allWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (bottomOverlayChatText.getVisibility() == VISIBLE && bottomOverlayChatText2.getVisibility() == VISIBLE) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bottomOverlayChatText.getLayoutParams();
                    layoutParams.width = allWidth / 2;
                    layoutParams = (FrameLayout.LayoutParams) bottomOverlayChatText2.getLayoutParams();
                    layoutParams.width = allWidth / 2;
                    layoutParams.leftMargin = allWidth / 2;
                } else {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bottomOverlayChatText.getLayoutParams();
                    layoutParams.width = allWidth;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

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

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText.setGravity(Gravity.CENTER);
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        bottomOverlayChatText.setOnClickListener(view -> {
            if (getParentActivity() == null) {
                return;
            }
            if (currentUser != null && userBlocked) {
                if (currentUser.bot) {
                    String botUserLast = botUser;
                    botUser = null;
                    getMessagesController().unblockUser(currentUser.id);
                    if (botUserLast != null && botUserLast.length() != 0) {
                        getMessagesController().sendBotStart(currentUser, botUserLast);
                    } else {
                        getSendMessagesHelper().sendMessage("/start", dialog_id, null, null, false, null, null, null, true, 0);
                    }
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("AreYouSureUnblockContact", R.string.AreYouSureUnblockContact));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> getMessagesController().unblockUser(currentUser.id));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            } else if (currentUser != null && currentUser.bot && botUser != null) {
                if (botUser.length() != 0) {
                    getMessagesController().sendBotStart(currentUser, botUser);
                } else {
                    getSendMessagesHelper().sendMessage("/start", dialog_id, null, null, false, null, null, null, true, 0);
                }
                botUser = null;
                updateBottomOverlay();
            } else {
                if (ChatObject.isChannel(currentChat) && !(currentChat instanceof TLRPC.TL_channelForbidden)) {
                    if (ChatObject.isNotInChat(currentChat)) {
                        showBottomOverlayProgress(true, true);
                        getMessagesController().addUserToChat(currentChat.id, getUserConfig().getCurrentUser(), null, 0, null, ChatActivity.this, null);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeSearchByActiveAction);

                        if (hasReportSpam() && reportSpamButton.getTag(R.id.object_tag) != null) {
                            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                            preferences.edit().putInt("dialog_bar_vis3" + dialog_id, 3).commit();
                            getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, dialog_id);
                        }
                    } else {
                        toggleMute(true);
                    }
                } else {
                    AlertsCreator.createClearOrDeleteDialogAlert(ChatActivity.this, false, currentChat, currentUser, currentEncryptedChat != null, (param) -> {
                        getNotificationCenter().removeObserver(ChatActivity.this, NotificationCenter.closeChats);
                        getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                        finishFragment();
                        getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, dialog_id, currentUser, currentChat, param);
                    });
                }
            }
        });

        bottomOverlayChatText2 = new UnreadCounterTextView(context);
        bottomOverlayChatText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText2.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText2.setGravity(Gravity.CENTER);
        bottomOverlayChatText2.setVisibility(View.GONE);
        bottomOverlayChat.addView(bottomOverlayChatText2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        bottomOverlayChatText2.setOnClickListener(v -> {
            if (chatInfo == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putInt("chat_id", chatInfo.linked_chat_id);
            if (!getMessagesController().checkCanOpenChat(args, ChatActivity.this)) {
                return;
            }
            presentFragment(new ChatActivity(args));
        });

        bottomOverlayProgress = new RadialProgressView(context);
        bottomOverlayProgress.setSize(AndroidUtilities.dp(22));
        bottomOverlayProgress.setProgressColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayProgress.setVisibility(View.INVISIBLE);
        bottomOverlayProgress.setScaleX(0.1f);
        bottomOverlayProgress.setScaleY(0.1f);
        bottomOverlayProgress.setAlpha(1.0f);
        bottomOverlayChat.addView(bottomOverlayProgress, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

        replyButton = new TextView(context);
        replyButton.setText(LocaleController.getString("Reply", R.string.Reply));
        replyButton.setGravity(Gravity.CENTER_VERTICAL);
        replyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        replyButton.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(21), 0);
        replyButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 3));
        replyButton.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        replyButton.setCompoundDrawablePadding(AndroidUtilities.dp(7));
        replyButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        Drawable image = context.getResources().getDrawable(R.drawable.input_reply).mutate();
        image.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
        replyButton.setCompoundDrawablesWithIntrinsicBounds(image, null, null, null);
        replyButton.setOnClickListener(v -> {
            MessageObject messageObject = null;
            for (int a = 1; a >= 0; a--) {
                if (messageObject == null && selectedMessagesIds[a].size() != 0) {
                    messageObject = messagesDict[a].get(selectedMessagesIds[a].keyAt(0));
                }
                selectedMessagesIds[a].clear();
                selectedMessagesCanCopyIds[a].clear();
                selectedMessagesCanStarIds[a].clear();
            }
            hideActionMode();
            if (messageObject != null && (messageObject.messageOwner.id > 0 || messageObject.messageOwner.id < 0 && currentEncryptedChat != null)) {
                showFieldPanelForReply(messageObject);
            }
            updatePinnedMessageView(true);
            updateVisibleRows();
        });
        bottomMessagesActionContainer.addView(replyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        forwardButton = new TextView(context);
        forwardButton.setText(LocaleController.getString("Forward", R.string.Forward));
        forwardButton.setGravity(Gravity.CENTER_VERTICAL);
        forwardButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        forwardButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        forwardButton.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        forwardButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 3));
        forwardButton.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        forwardButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        image = context.getResources().getDrawable(R.drawable.input_forward).mutate();
        image.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
        forwardButton.setCompoundDrawablesWithIntrinsicBounds(image, null, null, null);
        forwardButton.setOnClickListener(v -> openForward());
        bottomMessagesActionContainer.addView(forwardButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));

        contentView.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        undoView = new UndoView(context);
        contentView.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        if (currentChat != null) {
            slowModeHint = new HintView(getParentActivity(), 2);
            slowModeHint.setAlpha(0.0f);
            slowModeHint.setVisibility(View.INVISIBLE);
            contentView.addView(slowModeHint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
        }

        chatAdapter.updateRows();
        if (loading && messages.isEmpty()) {
            progressView.setVisibility(chatAdapter.botInfoRow == -1 ? View.VISIBLE : View.INVISIBLE);
            chatListView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.INVISIBLE);
            chatListView.setEmptyView(emptyViewContainer);
        }

        checkBotKeyboard();
        updateBottomOverlay();
        updateSecretStatus();
        updateTopPanel(false);
        updatePinnedMessageView(true);

        try {
            if (currentEncryptedChat != null && Build.VERSION.SDK_INT >= 23 && (SharedConfig.passcodeHash.length() == 0 || SharedConfig.allowScreenCapture)) {
                MediaController.getInstance().setFlagSecure(this, true);
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
        if (videoPlayerContainer == null) {
            if (Build.VERSION.SDK_INT >= 21) {
                videoPlayerContainer = new FrameLayout(getParentActivity()) {
                    @Override
                    public void setTranslationY(float translationY) {
                        super.setTranslationY(translationY);
                        contentView.invalidate();
                    }
                };
                videoPlayerContainer.setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        if (view.getTag(R.id.parent_tag) != null) {
                            outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(4));
                        } else {
                            outline.setOval(0, 0, AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
                        }
                    }
                });
                videoPlayerContainer.setClipToOutline(true);
            } else {
                videoPlayerContainer = new FrameLayout(getParentActivity()) {

                    RectF rect = new RectF();

                    @Override
                    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                        super.onSizeChanged(w, h, oldw, oldh);
                        aspectPath.reset();
                        if (getTag(R.id.parent_tag) != null) {
                            rect.set(0, 0, w, h);
                            aspectPath.addRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Path.Direction.CW);
                        } else {
                            aspectPath.addCircle(w / 2, h / 2, w / 2, Path.Direction.CW);
                        }
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
                        if (getTag() == null) {
                            canvas.drawPath(aspectPath, aspectPaint);
                        }
                    }
                };
                aspectPath = new Path();
                aspectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                aspectPaint.setColor(0xff000000);
                aspectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            }
            videoPlayerContainer.setWillNotDraw(false);

            aspectRatioFrameLayout = new AspectRatioFrameLayout(getParentActivity());
            aspectRatioFrameLayout.setBackgroundColor(0);
            if (add) {
                videoPlayerContainer.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            }

            videoTextureView = new TextureView(getParentActivity());
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        ViewGroup parent = (ViewGroup) videoPlayerContainer.getParent();
        if (parent != null && parent != contentView) {
            parent.removeView(videoPlayerContainer);
            parent = null;
        }
        if (parent == null) {
            contentView.addView(videoPlayerContainer, 1, new FrameLayout.LayoutParams(AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize));
        }
        videoPlayerContainer.setTag(null);
        aspectRatioFrameLayout.setDrawingReady(false);
        return videoTextureView;
    }

    private void destroyTextureView() {
        if (videoPlayerContainer == null || videoPlayerContainer.getParent() == null) {
            return;
        }
        contentView.removeView(videoPlayerContainer);
        aspectRatioFrameLayout.setDrawingReady(false);
        videoPlayerContainer.setTag(null);
        if (Build.VERSION.SDK_INT < 21) {
            videoPlayerContainer.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void openForward() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", 3);
        args.putInt("messagesCount", canForwardMessagesCount);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate(ChatActivity.this);
        presentFragment(fragment);
    }

    private void showBottomOverlayProgress(boolean show, boolean animated) {
        if (show && bottomOverlayProgress.getTag() != null || !show && bottomOverlayProgress.getTag() == null) {
            return;
        }
        if (bottomOverlayAnimation != null) {
            bottomOverlayAnimation.cancel();
            bottomOverlayAnimation = null;
        }
        bottomOverlayProgress.setTag(show ? 1 : null);
        if (animated) {
            bottomOverlayAnimation = new AnimatorSet();
            if (show) {
                bottomOverlayProgress.setVisibility(View.VISIBLE);
                bottomOverlayAnimation.playTogether(
                        ObjectAnimator.ofFloat(bottomOverlayChatText, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText2, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText2, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText2, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomOverlayProgress, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayProgress, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayProgress, View.ALPHA, 1.0f));
            } else {
                bottomOverlayChatText.setVisibility(View.VISIBLE);
                bottomOverlayAnimation.playTogether(
                        ObjectAnimator.ofFloat(bottomOverlayProgress, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(bottomOverlayProgress, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(bottomOverlayProgress, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText2, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText2, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(bottomOverlayChatText2, View.ALPHA, 1.0f));

            }
            bottomOverlayAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (bottomOverlayAnimation != null && bottomOverlayAnimation.equals(animation)) {
                        if (!show) {
                            bottomOverlayProgress.setVisibility(View.INVISIBLE);
                        } else {
                            bottomOverlayChatText.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (bottomOverlayAnimation != null && bottomOverlayAnimation.equals(animation)) {
                        bottomOverlayAnimation = null;
                    }
                }
            });
            bottomOverlayAnimation.setDuration(150);
            bottomOverlayAnimation.start();
        } else {
            bottomOverlayProgress.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            bottomOverlayProgress.setScaleX(show ? 1.0f : 0.1f);
            bottomOverlayProgress.setScaleY(show ? 1.0f : 0.1f);
            bottomOverlayProgress.setAlpha(show ? 1.0f : 1.0f);
            bottomOverlayChatText.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
            bottomOverlayChatText.setScaleX(show ? 0.1f : 1.0f);
            bottomOverlayChatText.setScaleY(show ? 0.1f : 1.0f);
            bottomOverlayChatText.setAlpha(show ? 0.0f : 1.0f);
            bottomOverlayChatText2.setScaleX(show ? 0.1f : 1.0f);
            bottomOverlayChatText2.setScaleY(show ? 0.1f : 1.0f);
            bottomOverlayChatText2.setAlpha(show ? 0.0f : 1.0f);
        }
    }

    private void sendBotInlineResult(TLRPC.BotInlineResult result, boolean notify, int scheduleDate) {
        int uid = mentionsAdapter.getContextBotId();
        HashMap<String, String> params = new HashMap<>();
        params.put("id", result.id);
        params.put("query_id", "" + result.query_id);
        params.put("bot", "" + uid);
        params.put("bot_name", mentionsAdapter.getContextBotName());
        SendMessagesHelper.prepareSendingBotContextResult(getAccountInstance(), result, params, dialog_id, replyingMessageObject, notify, scheduleDate);
        chatActivityEnterView.setFieldText("");
        hideFieldPanel(false);
        getMediaDataController().increaseInlineRaiting(uid);
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
        } else if (chatInfo instanceof TLRPC.TL_chatFull) {
            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = chatInfo.participants.participants.get(a);
                TLRPC.User user = getMessagesController().getUser(participant.user_id);
                if (user != null && user.bot) {
                    URLSpanBotCommand.enabled = true;
                    break;
                }
            }
        } else if (chatInfo instanceof TLRPC.TL_channelFull) {
            URLSpanBotCommand.enabled = !chatInfo.bot_info.isEmpty() && currentChat != null && currentChat.megagroup;
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
            getMessagesController().loadMessages(dialog_id, 30, 0, date, true, 0, classGuid, 4, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
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
            getMessagesController().sendBotStart(currentUser, object.start_param);
        } else {
            Bundle args = new Bundle();
            args.putInt("user_id", user.id);
            args.putString("inline_query", object.start_param);
            args.putLong("inline_return", dialog_id);
            if (!getMessagesController().checkCanOpenChat(args, ChatActivity.this)) {
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
            chatAttachAlert = new ChatAttachAlert(getParentActivity(), this) {
                @Override
                public void dismissInternal() {
                    if (chatAttachAlert.isShowing()) {
                        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                        if (chatActivityEnterView.getVisibility() == View.VISIBLE && fragmentView != null) {
                            fragmentView.requestLayout();
                        }
                    }
                    super.dismissInternal();
                    if (openKeyboardOnAttachMenuClose) {
                        AndroidUtilities.runOnUIThread(() -> chatActivityEnterView.openKeyboard(), 50);
                        openKeyboardOnAttachMenuClose = false;
                    }
                }
            };
            chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
                @Override
                public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate) {
                    if (getParentActivity() == null || chatAttachAlert == null) {
                        return;
                    }
                    if (chatAttachAlert != null) {
                        editingMessageObject = chatAttachAlert.getEditingMessageObject();
                    } else {
                        editingMessageObject = null;
                    }
                    if (button == 8 || button == 7 || button == 4 && !chatAttachAlert.getSelectedPhotos().isEmpty()) {
                        if (button != 8) {
                            chatAttachAlert.dismiss();
                        }
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
                                info.entities = photoEntry.entities;
                                info.masks = !photoEntry.stickers.isEmpty() ? new ArrayList<>(photoEntry.stickers) : null;
                                info.ttl = photoEntry.ttl;
                                info.videoEditedInfo = photoEntry.editedInfo;
                                info.canDeleteAfter = photoEntry.canDeleteAfter;
                                photos.add(info);
                                photoEntry.reset();
                            }
                            fillEditingMediaWithCaption(photos.get(0).caption, photos.get(0).entities);
                            SendMessagesHelper.prepareSendingMedia(getAccountInstance(), photos, dialog_id, replyingMessageObject, null, button == 4, arg, editingMessageObject, notify, scheduleDate);
                            afterMessageSend();
                        }
                        if (scheduleDate != 0) {
                            if (scheduledMessagesCount == -1) {
                                scheduledMessagesCount = 0;
                            }
                            scheduledMessagesCount += selectedPhotos.size();
                            updateScheduledInterface(true);
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

                @Override
                public void needEnterComment() {
                    if (chatActivityEnterView.isKeyboardVisible()) {
                        chatActivityEnterView.showEmojiView();
                        openKeyboardOnAttachMenuClose = true;
                    }
                    AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                    fragmentView.requestLayout();
                }
            });
        }
    }

    public long getDialogId() {
        return dialog_id;
    }

    public boolean hasReportSpam() {
        return topChatPanelView != null && topChatPanelView.getTag() == null && reportSpamButton.getVisibility() != View.GONE;
    }

    public void setBotUser(String value) {
        if (inlineReturn != 0) {
            getMessagesController().sendBotStart(currentUser, value);
        } else {
            botUser = value;
            updateBottomOverlay();
        }
    }

    private void afterMessageSend() {
        hideFieldPanel(false);
        if (!inScheduleMode) {
            getMediaDataController().cleanDraft(dialog_id, true);
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

    private void openScheduledMessages() {
        if (parentLayout == null || parentLayout.getLastFragment() != this) {
            return;
        }
        Bundle bundle = new Bundle();
        if (currentEncryptedChat != null) {
            bundle.putInt("enc_id", currentEncryptedChat.id);
        } else if (currentChat != null) {
            bundle.putInt("chat_id", currentChat.id);
        } else {
            bundle.putInt("user_id", currentUser.id);
        }
        bundle.putBoolean("scheduled", true);
        ChatActivity fragment = new ChatActivity(bundle);
        fragment.chatActivityDelegate = mid -> scrollToMessageId(mid, 0, true, 0, false);
        presentFragment(fragment, false);
    }

    private void initStickers() {
        if (chatActivityEnterView == null || getParentActivity() == null || stickersAdapter != null || currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 23) {
            return;
        }
        stickersListView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        stickersListView.setAdapter(stickersAdapter = new StickersAdapter(getParentActivity(), show -> {
            if (show) {
                int newPadding = stickersAdapter.isShowingKeywords() ? AndroidUtilities.dp(24) : 0;
                if (newPadding != stickersListView.getPaddingTop() || stickersPanel.getTag() == null) {
                    stickersListView.setPadding(AndroidUtilities.dp(18), newPadding, AndroidUtilities.dp(18), 0);
                    stickersListView.scrollToPosition(0);

                    boolean isRtl = chatActivityEnterView.isRtlText();
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) stickersPanelArrow.getLayoutParams();
                    layoutParams.gravity = Gravity.BOTTOM | (isRtl ? Gravity.RIGHT : Gravity.LEFT);
                    stickersPanelArrow.requestLayout();
                }
            }
            if (show && stickersPanel.getTag() != null || !show && stickersPanel.getTag() == null) {
                return;
            }
            if (show) {
                stickersPanel.setVisibility(allowStickersPanel ? View.VISIBLE : View.INVISIBLE);
                stickersPanel.setTag(1);
            } else {
                stickersPanel.setTag(null);
            }
            if (runningAnimation != null) {
                runningAnimation.cancel();
                runningAnimation = null;
            }
            if (stickersPanel.getVisibility() != View.INVISIBLE) {
                runningAnimation = new AnimatorSet();
                runningAnimation.playTogether(
                        ObjectAnimator.ofFloat(stickersPanel, View.ALPHA, show ? 0.0f : 1.0f, show ? 1.0f : 0.0f)
                );
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            if (!show) {
                                stickersAdapter.clearStickers();
                                stickersPanel.setVisibility(View.GONE);
                                if (ContentPreviewViewer.getInstance().isVisible()) {
                                    ContentPreviewViewer.getInstance().close();
                                }
                                ContentPreviewViewer.getInstance().reset();
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
        }));
        stickersListView.setOnItemClickListener(stickersOnItemClickListener = (view, position) -> {
            Object item = stickersAdapter.getItem(position);
            Object parent = stickersAdapter.getItemParent(position);
            if (item instanceof TLRPC.TL_document) {
                if (!inScheduleMode && checkSlowMode(view)) {
                    return;
                }
                TLRPC.TL_document document = (TLRPC.TL_document) item;
                if (inScheduleMode) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(currentUser), (notify, scheduleDate) -> SendMessagesHelper.getInstance(currentAccount).sendSticker(document, dialog_id, replyingMessageObject, parent, notify, scheduleDate));
                } else {
                    getSendMessagesHelper().sendSticker(document, dialog_id, replyingMessageObject, parent, true, 0);
                }
                hideFieldPanel(false);
                chatActivityEnterView.addStickerToRecent(document);
                chatActivityEnterView.setFieldText("");
            } else if (item instanceof String) {
                String emoji = (String) item;
                SpannableString string = new SpannableString(emoji);
                Emoji.replaceEmoji(string, chatActivityEnterView.getEditField().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                //stickersAdapter.loadStikersForEmoji("", false);
                chatActivityEnterView.setFieldText(string, false);
            }
        });
    }

    public void shareMyContact(int type, MessageObject messageObject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("ShareYouPhoneNumberTitle", R.string.ShareYouPhoneNumberTitle));
        if (currentUser != null) {
            if (currentUser.bot) {
                builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfoBot", R.string.AreYouSureShareMyContactInfoBot));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureShareMyContactInfoUser", R.string.AreYouSureShareMyContactInfoUser, PhoneFormat.getInstance().format("+" + getUserConfig().getCurrentUser().phone), ContactsController.formatName(currentUser.first_name, currentUser.last_name))));
            }
        } else {
            builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfo", R.string.AreYouSureShareMyContactInfo));
        }
        builder.setPositiveButton(LocaleController.getString("ShareContact", R.string.ShareContact), (dialogInterface, i) -> {
            if (type == 1) {
                TLRPC.TL_contacts_acceptContact req = new TLRPC.TL_contacts_acceptContact();
                req.id = getMessagesController().getInputUser(currentUser);
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error != null) {
                        return;
                    }
                    getMessagesController().processUpdates((TLRPC.Updates) response, false);
                });
            } else {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(getUserConfig().getCurrentUser(), dialog_id, messageObject, null, null, true, 0);
                if (!inScheduleMode) {
                    moveScrollToLastMessage();
                }
                hideFieldPanel(false);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void hideVoiceHint() {
        if (voiceHintTextView == null) {
            return;
        }
        voiceHintAnimation = new AnimatorSet();
        voiceHintAnimation.playTogether(
                ObjectAnimator.ofFloat(voiceHintTextView, View.ALPHA, 0.0f)
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
                    voiceHintAnimation = null;
                    voiceHintHideRunnable = null;
                }
            }
        });
        voiceHintAnimation.setDuration(300);
        voiceHintAnimation.start();
    }

    private void showVoiceHint(boolean hide, boolean video) {
        if (getParentActivity() == null || fragmentView == null || hide && voiceHintTextView == null || inScheduleMode) {
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
            if (voiceHintTextView.getVisibility() == View.VISIBLE) {
                hideVoiceHint();
            }
            return;
        }

        voiceHintTextView.setText(video ? LocaleController.getString("HoldToVideo", R.string.HoldToVideo) : LocaleController.getString("HoldToAudio", R.string.HoldToAudio));

        if (voiceHintHideRunnable != null) {
            if (voiceHintAnimation != null) {
                voiceHintAnimation.cancel();
                voiceHintAnimation = null;
            } else {
                AndroidUtilities.cancelRunOnUIThread(voiceHintHideRunnable);
                AndroidUtilities.runOnUIThread(voiceHintHideRunnable = this::hideVoiceHint, 2000);
                return;
            }
        } else if (voiceHintAnimation != null) {
            return;
        }

        voiceHintTextView.setVisibility(View.VISIBLE);
        voiceHintAnimation = new AnimatorSet();
        voiceHintAnimation.playTogether(
                ObjectAnimator.ofFloat(voiceHintTextView, View.ALPHA, 1.0f)
        );
        voiceHintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(voiceHintAnimation)) {
                    voiceHintAnimation = null;
                    AndroidUtilities.runOnUIThread(voiceHintHideRunnable = () -> hideVoiceHint(), 2000);
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

    private boolean checkSlowMode(View view) {
        CharSequence time = chatActivityEnterView.getSlowModeTimer();
        if (time != null) {
            showSlowModeHint(view, true, time);
            return true;
        }
        return false;
    }

    private void showSlowModeHint(View view, boolean show, CharSequence time) {
        if (getParentActivity() == null || fragmentView == null || !show && (slowModeHint == null || slowModeHint.getVisibility() != View.VISIBLE)) {
            return;
        }
        slowModeHint.setText(AndroidUtilities.replaceTags(LocaleController.formatString("SlowModeHint", R.string.SlowModeHint, time)));
        if (show) {
            slowModeHint.showForView(view, true);
        }
    }

    private void showMediaBannedHint() {
        if (getParentActivity() == null || currentChat == null || fragmentView == null || mediaBanTooltip != null && mediaBanTooltip.getVisibility() == View.VISIBLE) {
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
            mediaBanTooltip.setVisibility(View.GONE);
            frameLayout.addView(mediaBanTooltip, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 30, 0, 5, 3));
        }

        if (ChatObject.isActionBannedByDefault(currentChat, ChatObject.ACTION_SEND_MEDIA)) {
            mediaBanTooltip.setText(LocaleController.getString("GlobalAttachMediaRestricted", R.string.GlobalAttachMediaRestricted));
        } else {
            if (currentChat.banned_rights == null) {
                return;
            }
            if (AndroidUtilities.isBannedForever(currentChat.banned_rights)) {
                mediaBanTooltip.setText(LocaleController.getString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
            } else {
                mediaBanTooltip.setText(LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(currentChat.banned_rights.until_date)));
            }
        }

        mediaBanTooltip.setVisibility(View.VISIBLE);
        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mediaBanTooltip, View.ALPHA, 0.0f, 1.0f)
        );
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (mediaBanTooltip == null) {
                        return;
                    }
                    AnimatorSet AnimatorSet = new AnimatorSet();
                    AnimatorSet.playTogether(
                            ObjectAnimator.ofFloat(mediaBanTooltip, View.ALPHA, 0.0f)
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
                }, 5000);
            }
        });
        AnimatorSet.setDuration(300);
        AnimatorSet.start();
    }

    private void showNoSoundHint() {
        if (scrollingChatListView || SharedConfig.noSoundHintShowed || chatListView == null || getParentActivity() == null || fragmentView == null || noSoundHintView != null && noSoundHintView.getTag() != null) {
            return;
        }

        if (noSoundHintView == null) {
            SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
            int index = frameLayout.indexOfChild(chatActivityEnterView);
            if (index == -1) {
                return;
            }
            noSoundHintView = new HintView(getParentActivity(), 0);
            frameLayout.addView(noSoundHintView, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
            noSoundHintView.setAlpha(0.0f);
            noSoundHintView.setVisibility(View.INVISIBLE);
        }

        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = chatListView.getChildAt(a);
            if (!(child instanceof ChatMessageCell)) {
                continue;
            }
            ChatMessageCell messageCell = (ChatMessageCell) child;
            MessageObject messageObject = messageCell.getMessageObject();
            if (messageObject == null || !messageObject.isVideo()) {
                continue;
            }
            ImageReceiver imageReceiver = messageCell.getPhotoImage();
            AnimatedFileDrawable animation = imageReceiver.getAnimation();
            if (animation == null || animation.getCurrentProgressMs() < 3000) {
                continue;
            }
            if (noSoundHintView.showForMessageCell(messageCell, true)) {
                SharedConfig.setNoSoundHintShowed(true);
                break;
            }
        }
    }

    private void showForwardHint(ChatMessageCell cell) {
        if (scrollingChatListView || chatListView == null || getParentActivity() == null || fragmentView == null) {
            return;
        }

        if (forwardHintView == null) {
            SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
            int index = frameLayout.indexOfChild(chatActivityEnterView);
            if (index == -1) {
                return;
            }
            forwardHintView = new HintView(getParentActivity(), 1);
            frameLayout.addView(forwardHintView, index + 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
            forwardHintView.setAlpha(0.0f);
            forwardHintView.setVisibility(View.INVISIBLE);
        }
        forwardHintView.showForMessageCell(cell, true);
    }

    private void showGifHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
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
                ObjectAnimator.ofFloat(gifHintTextView, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(emojiButtonRed, View.ALPHA, 0.0f, 1.0f)
        );
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (gifHintTextView == null) {
                        return;
                    }
                    AnimatorSet AnimatorSet = new AnimatorSet();
                    AnimatorSet.playTogether(
                            ObjectAnimator.ofFloat(gifHintTextView, View.ALPHA, 0.0f)
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
        if (currentChat != null && !ChatObject.hasAdminRights(currentChat) && currentChat.slowmode_enabled) {
            chatAttachAlert.setMaxSelectedPhotos(10, true);
        } else {
            chatAttachAlert.setMaxSelectedPhotos(-1, true);
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
                            ObjectAnimator.ofFloat(mentionContainer, View.ALPHA, 0.0f)
                    );
                    mentionListAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (mentionListAnimation != null && mentionListAnimation.equals(animation)) {
                                mentionContainer.setVisibility(View.INVISIBLE);
                                mentionListAnimation = null;
                                updateMessageListAccessibilityVisibility();
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
                    updateMessageListAccessibilityVisibility();
                    mentionListAnimation = new AnimatorSet();
                    mentionListAnimation.playTogether(
                            ObjectAnimator.ofFloat(mentionContainer, View.ALPHA, 0.0f, 1.0f)
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

    private void checkAutoDownloadMessages(boolean scrollUp) {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        int firstMessagePosition = -1;
        int lastMessagePosition = -1;
        for (int a = 0; a < count; a++) {
            View child = chatListView.getChildAt(a);
            if (!(child instanceof ChatMessageCell)) {
                continue;
            }
            RecyclerListView.ViewHolder holder = chatListView.findContainingViewHolder(child);
            if (holder != null) {
                int p = holder.getAdapterPosition();
                if (firstMessagePosition == -1) {
                    firstMessagePosition = p;
                }
                lastMessagePosition = p;
            }

            ChatMessageCell cell = (ChatMessageCell) child;
            MessageObject object = cell.getMessageObject();
            if (object == null || object.mediaExists || !object.isSent()) {
                continue;
            }
            TLRPC.Document document = object.getDocument();
            if (document == null) {
                continue;
            }
            int canDownload;
            if (!MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document) && !MessageObject.isGifDocument(document) && !MessageObject.isRoundVideoDocument(document)
                    && (canDownload = getDownloadController().canDownloadMedia(object.messageOwner)) != 0) {
                if (canDownload == 2) {
                    if (currentEncryptedChat == null && !object.shouldEncryptPhotoOrVideo() && object.canStreamVideo()) {
                        getFileLoader().loadFile(document, object, 0, 10);
                    }
                } else {
                    getFileLoader().loadFile(document, object, 0, MessageObject.isVideoDocument(document) && object.shouldEncryptPhotoOrVideo() ? 2 : 0);
                    cell.updateButtonState(false, true, false);
                }
            }
        }
        if (firstMessagePosition != -1) {
            int lastPosition;
            if (scrollUp) {
                firstMessagePosition = lastPosition = lastMessagePosition;
                if (firstMessagePosition + 10 >= chatAdapter.messagesEndRow) {
                    firstMessagePosition = chatAdapter.messagesEndRow;
                } else {
                    firstMessagePosition = firstMessagePosition + 10;
                }
                for (int a = lastPosition, N = messages.size(); a < firstMessagePosition; a++) {
                    int n = a - chatAdapter.messagesStartRow;
                    if (n < 0 || n >= N) {
                        continue;
                    }
                    checkAutoDownloadMessage(messages.get(n));
                }
            } else {
                if (firstMessagePosition - 20 <= chatAdapter.messagesStartRow) {
                    lastPosition = chatAdapter.messagesStartRow;
                } else {
                    lastPosition = firstMessagePosition - 20;
                }
                for (int a = firstMessagePosition - 1, N = messages.size(); a >= lastPosition; a--) {
                    int n = a - chatAdapter.messagesStartRow;
                    if (n < 0 || n >= N) {
                        continue;
                    }
                    checkAutoDownloadMessage(messages.get(n));
                }
            }
        }
        showNoSoundHint();
    }

    private void checkAutoDownloadMessage(MessageObject object) {
        if (object.mediaExists) {
            return;
        }
        TLRPC.Message message = object.messageOwner;
        int canDownload = getDownloadController().canDownloadMedia(message);
        if (canDownload == 0) {
            return;
        }
        TLRPC.Document document = object.getDocument();
        TLRPC.PhotoSize photo = document == null ? FileLoader.getClosestPhotoSizeWithSize(object.photoThumbs, AndroidUtilities.getPhotoSize()) : null;
        if (document == null && photo == null) {
            return;
        }
        if (canDownload == 2 || canDownload == 1 && object.isVideo()) {
            if (document != null && currentEncryptedChat == null && !object.shouldEncryptPhotoOrVideo() && object.canStreamVideo()) {
                getFileLoader().loadFile(document, object, 0, 10);
            }
        } else {
            if (document != null) {
                getFileLoader().loadFile(document, object, 0, MessageObject.isVideoDocument(document) && object.shouldEncryptPhotoOrVideo() ? 2 : 0);
            } else {
                getFileLoader().loadFile(ImageLocation.getForObject(photo, object.photoThumbsObject), object, null, 0, object.shouldEncryptPhotoOrVideo() ? 2 : 0);
            }
        }
    }

    private void showFloatingDateView(boolean scroll) {
        if (floatingDateView.getTag() == null) {
            if (floatingDateAnimation != null) {
                floatingDateAnimation.cancel();
            }
            floatingDateView.setTag(1);
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(150);
            floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 1.0f));
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
        if (!scroll) {
            updateMessagesVisiblePart(false);
            hideDateDelay = 1000;
        }
    }

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView.getTag() != null && !currentFloatingDateOnScreen && (!scrollingFloatingDate || currentFloatingTopIsNotMessage)) {
            floatingDateView.setTag(null);
            if (animated) {
                floatingDateAnimation = new AnimatorSet();
                floatingDateAnimation.setDuration(150);
                floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 0.0f));
                floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(floatingDateAnimation)) {
                            floatingDateAnimation = null;
                        }
                    }
                });
                floatingDateAnimation.setStartDelay(hideDateDelay);
                floatingDateAnimation.start();
            } else {
                if (floatingDateAnimation != null) {
                    floatingDateAnimation.cancel();
                    floatingDateAnimation = null;
                }
                floatingDateView.setAlpha(0.0f);
            }
            hideDateDelay = 500;
        }
    }

    @Override
    protected void onRemoveFromParent() {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null && messageObject.isVideo()) {
            MediaController.getInstance().cleanupPlayer(true, true);
        } else {
            MediaController.getInstance().setTextureView(videoTextureView, null, null, false);
        }
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
                    getMessagesController().loadMessages(dialog_id, 50, maxMessageId[0], 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
                } else {
                    getMessagesController().loadMessages(dialog_id, 50, 0, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
                }
            } else if (mergeDialogId != 0 && !endReached[1]) {
                loading = true;
                waitingForLoad.add(lastLoadIndex);
                getMessagesController().loadMessages(mergeDialogId, 50, maxMessageId[1], 0, !cacheEndReached[1], minDate[1], classGuid, 0, 0, false, inScheduleMode, lastLoadIndex++);
            }
        }
        if (visibleItemCount > 0 && !loadingForward && firstVisibleItem <= 10) {
            if (mergeDialogId != 0 && !forwardEndReached[1]) {
                waitingForLoad.add(lastLoadIndex);
                getMessagesController().loadMessages(mergeDialogId, 50, minMessageId[1], 0, true, maxDate[1], classGuid, 1, 0, false, inScheduleMode, lastLoadIndex++);
                loadingForward = true;
            } else if (!forwardEndReached[0]) {
                waitingForLoad.add(lastLoadIndex);
                getMessagesController().loadMessages(dialog_id, 50, minMessageId[0], 0, true, maxDate[0], classGuid, 1, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
                loadingForward = true;
            }
        }
    }

    private void processSelectedAttach(int which) {
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
                try {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                } catch (Throwable ignore) {

                }
                return;
            }
            boolean allowGifs;
            if (ChatObject.isChannel(currentChat) && currentChat.banned_rights != null && currentChat.banned_rights.send_gifs) {
                allowGifs = false;
            } else {
                allowGifs = currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46;
            }
            PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(0, allowGifs, true, ChatActivity.this);
            if (currentChat != null && !ChatObject.hasAdminRights(currentChat) && currentChat.slowmode_enabled) {
                fragment.setMaxSelectedPhotos(10, true);
            } else {
                fragment.setMaxSelectedPhotos(editingMessageObject != null ? 1 : 0, editingMessageObject == null);
            }
            fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
                @Override
                public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                    if (photos.isEmpty()) {
                        return;
                    }
                    boolean hasNoGifs = false;
                    for (int a = 0; a < photos.size(); a++) {
                        SendMessagesHelper.SendingMediaInfo info = photos.get(a);
                        if (info.inlineResult == null) {
                            hasNoGifs = true;
                            break;
                        }
                    }
                    if (!hasNoGifs && !TextUtils.isEmpty(photos.get(0).caption)) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(photos.get(0).caption, dialog_id, replyingMessageObject, null, false, photos.get(0).entities, null, null, notify, scheduleDate);
                    }
                    for (int a = 0; a < photos.size(); a++) {
                        SendMessagesHelper.SendingMediaInfo info = photos.get(a);
                        if (info.inlineResult != null) {
                            SendMessagesHelper.prepareSendingBotContextResult(getAccountInstance(), info.inlineResult, info.params, dialog_id, replyingMessageObject, notify, scheduleDate);
                            photos.remove(a);
                            a--;
                        }
                    }
                    if (photos.isEmpty()) {
                        return;
                    }
                    fillEditingMediaWithCaption(photos.get(0).caption, photos.get(0).entities);
                    SendMessagesHelper.prepareSendingMedia(getAccountInstance(), photos, dialog_id, replyingMessageObject, null, false, true, editingMessageObject, notify, scheduleDate);
                    afterMessageSend();
                    if (scheduleDate != 0) {
                        if (scheduledMessagesCount == -1) {
                            scheduledMessagesCount = 0;
                        }
                        scheduledMessagesCount += photos.size();
                        updateScheduledInterface(true);
                    }
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
                try {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 20);
                } catch (Throwable ignore) {

                }
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
            LocationActivity fragment = new LocationActivity(currentEncryptedChat == null && !inScheduleMode ? 1 : 0);
            fragment.setChatActivity(ChatActivity.this);
            fragment.setDialogId(dialog_id);
            fragment.setDelegate(this);
            presentFragment(fragment);
        } else if (which == attach_document) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                try {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                } catch (Throwable ignore) {

                }
                return;
            }
            DocumentSelectActivity fragment = new DocumentSelectActivity(true);
            fragment.setChatActivity(ChatActivity.this);
            fragment.setMaxSelectedFiles(currentChat != null && !ChatObject.hasAdminRights(currentChat) && currentChat.slowmode_enabled || editingMessageObject != null ? 1 : -1);
            fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {

                @Override
                public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, boolean notify, int scheduleDate) {
                    activity.finishFragment();
                    fillEditingMediaWithCaption(null, null);
                    SendMessagesHelper.prepareSendingDocuments(getAccountInstance(), files, files, null, null, null, dialog_id, replyingMessageObject, null, editingMessageObject, notify, scheduleDate);
                    afterMessageSend();
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

                @Override
                public void startMusicSelectActivity(BaseFragment parentFragment) {
                    AudioSelectActivity fragment = new AudioSelectActivity(ChatActivity.this);
                    fragment.setDelegate((audios, notify, scheduleDate) -> {
                        parentFragment.removeSelfFromStack();
                        fillEditingMediaWithCaption(null, null);
                        SendMessagesHelper.prepareSendingAudioDocuments(getAccountInstance(), audios, dialog_id, replyingMessageObject, editingMessageObject, notify, scheduleDate);
                        afterMessageSend();
                    });
                    presentFragment(fragment);
                }
            });
            presentFragment(fragment);
        } else if (which == attach_audio) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
            AudioSelectActivity fragment = new AudioSelectActivity(ChatActivity.this);
            fragment.setDelegate((audios, notify, scheduleDate) -> {
                fillEditingMediaWithCaption(null, null);
                SendMessagesHelper.prepareSendingAudioDocuments(getAccountInstance(), audios, dialog_id, replyingMessageObject, editingMessageObject, notify, scheduleDate);
                afterMessageSend();
            });
            presentFragment(fragment);
        } else if (which == attach_contact) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (getParentActivity().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 5);
                    return;
                }
            }
            PhonebookSelectActivity activity = new PhonebookSelectActivity(ChatActivity.this);
            activity.setDelegate((user, notify, scheduleDate) -> {
                getSendMessagesHelper().sendMessage(user, dialog_id, replyingMessageObject, null, null, notify, scheduleDate);
                afterMessageSend();
            });
            presentFragment(activity);
        } else if (which == attach_poll) {
            if (currentChat == null || !ChatObject.canSendPolls(currentChat)) {
                return;
            }
            PollCreateActivity pollCreateActivity = new PollCreateActivity(ChatActivity.this);
            pollCreateActivity.setDelegate((poll, notify, scheduleDate) -> {
                getSendMessagesHelper().sendMessage(poll, dialog_id, replyingMessageObject, null, null, notify, scheduleDate);
                afterMessageSend();
            });
            presentFragment(pollCreateActivity);
        }
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return dialog != chatAttachAlert && super.dismissDialogOnPause(dialog);
    }

    private void searchLinks(final CharSequence charSequence, final boolean force) {
        if (currentEncryptedChat != null && (getMessagesController().secretWebpagePreview == 0 || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46)) {
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
            foundUrls = null;
            showFieldPanelForWebPage(false, foundWebPage, false);
        }
        final MessagesController messagesController = getMessagesController();
        Utilities.searchQueue.postRunnable(() -> {
            if (linkSearchRequestId != 0) {
                getConnectionsManager().cancelRequest(linkSearchRequestId, true);
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
                if (charSequence instanceof Spannable) {
                    URLSpanReplacement[] spans = ((Spannable) charSequence).getSpans(0, charSequence.length(), URLSpanReplacement.class);
                    if (spans != null && spans.length > 0) {
                        if (urls == null) {
                            urls = new ArrayList<>();
                        }
                        for (int a = 0; a < spans.length; a++) {
                            urls.add(spans[a].getURL());
                        }
                    }
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
                    AndroidUtilities.runOnUIThread(() -> {
                        if (foundWebPage != null) {
                            showFieldPanelForWebPage(false, foundWebPage, false);
                            foundWebPage = null;
                        }
                    });
                    return;
                }
                textToCheck = TextUtils.join(" ", urls);
            } catch (Exception e) {
                FileLog.e(e);
                String text = charSequence.toString().toLowerCase();
                if (charSequence.length() < 13 || !text.contains("http://") && !text.contains("https://")) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (foundWebPage != null) {
                            showFieldPanelForWebPage(false, foundWebPage, false);
                            foundWebPage = null;
                        }
                    });
                    return;
                }
                textToCheck = charSequence;
            }

            if (currentEncryptedChat != null && messagesController.secretWebpagePreview == 2) {
                AndroidUtilities.runOnUIThread(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                        messagesController.secretWebpagePreview = 1;
                        MessagesController.getGlobalMainSettings().edit().putInt("secretWebpage2", getMessagesController().secretWebpagePreview).commit();
                        foundUrls = null;
                        searchLinks(charSequence, force);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setMessage(LocaleController.getString("SecretLinkPreviewAlert", R.string.SecretLinkPreviewAlert));
                    showDialog(builder.create());

                    messagesController.secretWebpagePreview = 0;
                    MessagesController.getGlobalMainSettings().edit().putInt("secretWebpage2", messagesController.secretWebpagePreview).commit();
                });
                return;
            }

            final TLRPC.TL_messages_getWebPagePreview req = new TLRPC.TL_messages_getWebPagePreview();
            if (textToCheck instanceof String) {
                req.message = (String) textToCheck;
            } else {
                req.message = textToCheck.toString();
            }
            linkSearchRequestId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                            showFieldPanelForWebPage(true, foundWebPage, false);
                        } else {
                            if (foundWebPage != null) {
                                showFieldPanelForWebPage(false, foundWebPage, false);
                                foundWebPage = null;
                            }
                        }
                    } else {
                        if (foundWebPage != null) {
                            showFieldPanelForWebPage(false, foundWebPage, false);
                            foundWebPage = null;
                        }
                    }
                }
            }));

            getConnectionsManager().bindRequestToGuid(linkSearchRequestId, classGuid);
        });
    }

    private void forwardMessages(ArrayList<MessageObject> arrayList, boolean fromMyName, boolean notify, int scheduleDate) {
        if (arrayList == null || arrayList.isEmpty()) {
            return;
        }
        if (!fromMyName) {
            AlertsCreator.showSendMediaAlert(getSendMessagesHelper().sendMessage(arrayList, dialog_id, notify, scheduleDate), this);
        } else {
            for (MessageObject object : arrayList) {
                getSendMessagesHelper().processForwardFromMyName(object, dialog_id);
            }
        }
    }

    private void checkBotKeyboard() {
        if (chatActivityEnterView == null || botButtons == null || userBlocked) {
            return;
        }
        if (botButtons.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardForceReply) {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            if (preferences.getInt("answered_" + dialog_id, 0) != botButtons.getId() && (replyingMessageObject == null || chatActivityEnterView.getFieldText() == null)) {
                botReplyButtons = botButtons;
                chatActivityEnterView.setButtons(botButtons);
                showFieldPanelForReply(botButtons);
            }
        } else {
            if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                botReplyButtons = null;
                hideFieldPanel(true);
            }
            chatActivityEnterView.setButtons(botButtons);
        }
    }

    public void hideFieldPanel(boolean animated) {
        showFieldPanel(false, null, null, null, null, true, 0, false, animated);
    }

    public void hideFieldPanel(boolean notify, int scheduleDate, boolean animated) {
        showFieldPanel(false, null, null, null, null, notify, scheduleDate, false, animated);
    }

    public void showFieldPanelForWebPage(boolean show, TLRPC.WebPage webPage, boolean cancel) {
        showFieldPanel(show, null, null, null, webPage, true, 0, cancel, true);
    }

    public void showFieldPanelForForward(boolean show, ArrayList<MessageObject> messageObjectsToForward) {
        showFieldPanel(show, null, null, messageObjectsToForward, null, true, 0, false, true);
    }

    public void showFieldPanelForReply(MessageObject messageObjectToReply) {
        showFieldPanel(true, messageObjectToReply, null, null, null, true, 0, false, true);
    }

    public void showFieldPanelForEdit(boolean show, MessageObject messageObjectToEdit) {
        showFieldPanel(show, null, messageObjectToEdit, null, null, true, 0, false, true);
    }

    public void showFieldPanel(boolean show, MessageObject messageObjectToReply, MessageObject messageObjectToEdit, ArrayList<MessageObject> messageObjectsToForward, TLRPC.WebPage webPage, boolean notify, int scheduleDate, boolean cancel, boolean animated) {
        if (chatActivityEnterView == null) {
            return;
        }
        if (show) {
            if (messageObjectToReply == null && messageObjectsToForward == null && messageObjectToEdit == null && webPage == null) {
                return;
            }
            if (noSoundHintView != null) {
                noSoundHintView.hide();
            }
            if (forwardHintView != null) {
                forwardHintView.hide();
            }
            if (slowModeHint != null) {
                slowModeHint.hide();
            }
            if (searchItem != null && actionBar.isSearchFieldVisible()) {
                actionBar.closeSearchField(false);
                chatActivityEnterView.setFieldFocused();
                AndroidUtilities.runOnUIThread(() -> {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.openKeyboard();
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
            if (messageObjectToEdit != null) {
                forwardingMessages = null;
                replyingMessageObject = null;
                editingMessageObject = messageObjectToEdit;
                chatActivityEnterView.setReplyingMessageObject(null);
                chatActivityEnterView.setEditingMessageObject(messageObjectToEdit, !messageObjectToEdit.isMediaEmpty());
                if (foundWebPage != null) {
                    return;
                }
                chatActivityEnterView.setForceShowSendButton(false, false);
                replyIconImageView.setImageResource(R.drawable.group_edit);
                replyIconImageView.setContentDescription(LocaleController.getString("AccDescrEditing", R.string.AccDescrEditing));
                replyCloseImageView.setContentDescription(LocaleController.getString("AccDescrCancelEdit", R.string.AccDescrCancelEdit));
                if (messageObjectToEdit.isMediaEmpty()) {
                    replyNameTextView.setText(LocaleController.getString("EditMessage", R.string.EditMessage));
                } else {
                    replyNameTextView.setText(LocaleController.getString("EditCaption", R.string.EditCaption));
                }
                if (messageObjectToEdit.canEditMedia()) {
                    replyObjectTextView.setText(LocaleController.getString("EditMessageMedia", R.string.EditMessageMedia));
                } else if (messageObjectToEdit.messageText != null) {
                    String mess = messageObjectToEdit.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    replyObjectTextView.setText(Emoji.replaceEmoji(mess, replyObjectTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                }
            } else if (messageObjectToReply != null) {
                forwardingMessages = null;
                editingMessageObject = null;
                replyingMessageObject = messageObjectToReply;
                chatActivityEnterView.setReplyingMessageObject(messageObjectToReply);
                chatActivityEnterView.setEditingMessageObject(null, false);
                if (foundWebPage != null) {
                    return;
                }
                chatActivityEnterView.setForceShowSendButton(false, false);
                String name;
                if (messageObjectToReply.isFromUser()) {
                    TLRPC.User user = getMessagesController().getUser(messageObjectToReply.messageOwner.from_id);
                    if (user == null) {
                        return;
                    }
                    name = UserObject.getUserName(user);
                } else {
                    TLRPC.Chat chat;
                    if (ChatObject.isChannel(currentChat) && currentChat.megagroup && messageObjectToReply.isForwardedChannelPost()) {
                        chat = getMessagesController().getChat(messageObjectToReply.messageOwner.fwd_from.channel_id);
                    } else {
                        chat = getMessagesController().getChat(messageObjectToReply.messageOwner.to_id.channel_id);
                    }
                    if (chat == null) {
                        return;
                    }
                    name = chat.title;
                }
                replyIconImageView.setImageResource(R.drawable.msg_panel_reply);
                replyNameTextView.setText(name);
                replyIconImageView.setContentDescription(LocaleController.getString("AccDescrReplying", R.string.AccDescrReplying));
                replyCloseImageView.setContentDescription(LocaleController.getString("AccDescrCancelReply", R.string.AccDescrCancelReply));

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
                editingMessageObject = null;
                chatActivityEnterView.setReplyingMessageObject(null);
                chatActivityEnterView.setEditingMessageObject(null, false);
                forwardingMessages = messageObjectsToForward;
                if (foundWebPage != null) {
                    return;
                }
                chatActivityEnterView.setForceShowSendButton(true, false);
                ArrayList<Integer> uids = new ArrayList<>();
                replyIconImageView.setImageResource(R.drawable.msg_panel_forward);
                replyIconImageView.setContentDescription(LocaleController.getString("AccDescrForwarding", R.string.AccDescrForwarding));
                replyCloseImageView.setContentDescription(LocaleController.getString("AccDescrCancelForward", R.string.AccDescrCancelForward));
                MessageObject object = messageObjectsToForward.get(0);
                if (object.isFromUser()) {
                    uids.add(object.messageOwner.from_id);
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(object.messageOwner.to_id.channel_id);
                    if (ChatObject.isChannel(chat) && chat.megagroup && object.isForwardedChannelPost()) {
                        uids.add(-object.messageOwner.fwd_from.channel_id);
                    } else {
                        uids.add(-object.messageOwner.to_id.channel_id);
                    }
                }

                int type = object.isAnimatedEmoji() ? 0 : object.type;
                for (int a = 1; a < messageObjectsToForward.size(); a++) {
                    object = messageObjectsToForward.get(a);
                    int uid;
                    if (object.isFromUser()) {
                        uid = object.messageOwner.from_id;
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(object.messageOwner.to_id.channel_id);
                        if (ChatObject.isChannel(chat) && chat.megagroup && object.isForwardedChannelPost()) {
                            uid = -object.messageOwner.fwd_from.channel_id;
                        } else {
                            uid = -object.messageOwner.to_id.channel_id;
                        }
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
                        user = getMessagesController().getUser(uid);
                    } else {
                        chat = getMessagesController().getChat(-uid);
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
                    } else if (type == MessageObject.TYPE_ROUND_VIDEO) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedRound", messageObjectsToForward.size()));
                    } else if (type == 14) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedMusic", messageObjectsToForward.size()));
                    } else if (type == MessageObject.TYPE_STICKER || type == MessageObject.TYPE_ANIMATED_STICKER) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedSticker", messageObjectsToForward.size()));
                    } else if (type == 17) {
                        replyObjectTextView.setText(LocaleController.formatPluralString("ForwardedPoll", messageObjectsToForward.size()));
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
                replyIconImageView.setImageResource(R.drawable.msg_link);
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
            MessageObject thumbMediaMessageObject;
            if (messageObjectToReply != null) {
                thumbMediaMessageObject = messageObjectToReply;
            } else if (messageObjectToEdit != null) {
                thumbMediaMessageObject = messageObjectToEdit;
            } else {
                thumbMediaMessageObject = null;
            }

            FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) replyNameTextView.getLayoutParams();
            FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) replyObjectTextView.getLayoutParams();

            int cacheType = 1;
            int size = 0;
            TLRPC.PhotoSize photoSize = null;
            TLRPC.PhotoSize thumbPhotoSize = null;
            TLObject photoSizeObject = null;
            if (thumbMediaMessageObject != null) {
                photoSize = FileLoader.getClosestPhotoSizeWithSize(thumbMediaMessageObject.photoThumbs2, 320);
                thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(thumbMediaMessageObject.photoThumbs2, AndroidUtilities.dp(40));
                photoSizeObject = thumbMediaMessageObject.photoThumbsObject2;
                if (photoSize == null) {
                    if (thumbMediaMessageObject.mediaExists) {
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(thumbMediaMessageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                        if (photoSize != null) {
                            size = photoSize.size;
                        }
                        cacheType = 0;
                    } else {
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(thumbMediaMessageObject.photoThumbs, 320);
                    }
                    thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(thumbMediaMessageObject.photoThumbs, AndroidUtilities.dp(40));
                    photoSizeObject = thumbMediaMessageObject.photoThumbsObject;
                }
            }
            if (photoSize == thumbPhotoSize) {
                thumbPhotoSize = null;
            }
            if (photoSize == null || photoSize instanceof TLRPC.TL_photoSizeEmpty || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable || thumbMediaMessageObject.isAnyKindOfSticker() || thumbMediaMessageObject != null && thumbMediaMessageObject.isSecretMedia()) {
                replyImageView.setImageBitmap(null);
                replyImageLocation = null;
                replyImageLocationObject = null;
                replyImageView.setVisibility(View.INVISIBLE);
                layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(52);
            } else {
                if (thumbMediaMessageObject != null && thumbMediaMessageObject.isRoundVideo()) {
                    replyImageView.setRoundRadius(AndroidUtilities.dp(17));
                } else {
                    replyImageView.setRoundRadius(0);
                }
                replyImageSize = size;
                replyImageCacheType = cacheType;
                replyImageLocation = photoSize;
                replyImageThumbLocation = thumbPhotoSize;
                replyImageLocationObject = photoSizeObject;
                replyImageView.setImage(ImageLocation.getForObject(replyImageLocation, photoSizeObject), "50_50", ImageLocation.getForObject(thumbPhotoSize, photoSizeObject), "50_50_b", null, size, cacheType, thumbMediaMessageObject);
                replyImageView.setVisibility(View.VISIBLE);
                layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(96);
            }
            replyNameTextView.setLayoutParams(layoutParams1);
            replyObjectTextView.setLayoutParams(layoutParams2);
            chatActivityEnterView.showTopView(true, openKeyboard);
        } else {
            if (replyingMessageObject == null && forwardingMessages == null && foundWebPage == null && editingMessageObject == null) {
                return;
            }
            if (replyingMessageObject != null && replyingMessageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardForceReply) {
                SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                preferences.edit().putInt("answered_" + dialog_id, replyingMessageObject.getId()).commit();
            }
            if (foundWebPage != null) {
                foundWebPage = null;
                chatActivityEnterView.setWebPage(null, !cancel);
                if (webPage != null && (replyingMessageObject != null || forwardingMessages != null || editingMessageObject != null)) {
                    showFieldPanel(true, replyingMessageObject, editingMessageObject, forwardingMessages, null, notify, scheduleDate, false, true);
                    return;
                }
            }
            if (forwardingMessages != null) {
                ArrayList<MessageObject> messagesToForward = forwardingMessages;
                forwardingMessages = null;
                forwardMessages(messagesToForward, false, notify, scheduleDate != 0 ? scheduleDate + 1 : 0);
            }
            chatActivityEnterView.setForceShowSendButton(false, false);
            chatActivityEnterView.hideTopView(animated);
            chatActivityEnterView.setReplyingMessageObject(null);
            chatActivityEnterView.setEditingMessageObject(null, false);
            topViewWasVisible = 0;
            replyingMessageObject = null;
            editingMessageObject = null;
            replyImageLocation = null;
            replyImageLocationObject = null;
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
            getMessagesController().markMessageAsRead(dialog_id, messageObject.messageOwner.random_id, messageObject.messageOwner.ttl);
        } else {
            getMessagesController().markMessageAsRead(messageObject.getId(), ChatObject.isChannel(currentChat) ? currentChat.id : 0, null, messageObject.messageOwner.ttl, 0);
        }
        messageObject.messageOwner.destroyTime = messageObject.messageOwner.ttl + getConnectionsManager().getCurrentTime();
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
        showScrollToMessageError = false;
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
                removeSelectedMessageHighlight();
                updateVisibleRows();
            } else {
                chatLayoutManager.scrollToPositionWithOffset(0, 0);
            }
        } else {
            clearChatData();
            waitingForLoad.add(lastLoadIndex);
            getMessagesController().loadMessages(dialog_id, 30, 0, 0, true, 0, classGuid, 0, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
        }
    }

    public void updateTextureViewPosition(boolean needScroll) {
        if (fragmentView == null || paused) {
            return;
        }
        boolean foundTextureViewMessage = false;
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                MessageObject messageObject = messageCell.getMessageObject();
                if (videoPlayerContainer != null && (messageObject.isRoundVideo() || messageObject.isVideo()) && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    videoPlayerContainer.setTranslationX(imageReceiver.getImageX() + messageCell.getX());
                    videoPlayerContainer.setTranslationY(fragmentView.getPaddingTop() + messageCell.getTop() + imageReceiver.getImageY() - chatListViewClipTop + chatListView.getTranslationY() + (inPreviewMode ? AndroidUtilities.statusBarHeight : 0));
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoPlayerContainer.getLayoutParams();
                    if (messageObject.isRoundVideo()) {
                        videoPlayerContainer.setTag(R.id.parent_tag, null);
                        if (layoutParams.width != AndroidUtilities.roundMessageSize || layoutParams.height != AndroidUtilities.roundMessageSize) {
                            layoutParams.width = layoutParams.height = AndroidUtilities.roundMessageSize;
                            aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                            videoPlayerContainer.setLayoutParams(layoutParams);
                        }
                    } else {
                        videoPlayerContainer.setTag(R.id.parent_tag, 1);
                        if (layoutParams.width != imageReceiver.getImageWidth() || layoutParams.height != imageReceiver.getImageHeight()) {
                            aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                            layoutParams.width = imageReceiver.getImageWidth();
                            layoutParams.height = imageReceiver.getImageHeight();
                            videoPlayerContainer.setLayoutParams(layoutParams);
                        }
                    }
                    fragmentView.invalidate();
                    videoPlayerContainer.invalidate();
                    foundTextureViewMessage = true;
                    break;
                }
            }
        }
        if (needScroll && videoPlayerContainer != null) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && messageObject.eventId == 0) {
                if (!foundTextureViewMessage) {
                    if (checkTextureViewPosition && messageObject.isVideo()) {
                        MediaController.getInstance().cleanupPlayer(true, true);
                    } else {
                        videoPlayerContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                        fragmentView.invalidate();
                        if (messageObject != null && (messageObject.isRoundVideo() || messageObject.isVideo())) {
                            if (checkTextureViewPosition || PipRoundVideoView.getInstance() != null) {
                                MediaController.getInstance().setCurrentVideoVisible(false);
                            } else {
                                scrollToMessageId(messageObject.getId(), 0, false, 0, true);
                            }
                        }
                    }
                } else {
                    MediaController.getInstance().setCurrentVideoVisible(true);
                    if (messageObject.isRoundVideo() || scrollToVideo) {
                        scrollToMessageId(messageObject.getId(), 0, false, 0, true);
                    } else {
                        chatListView.invalidate();
                    }
                }
            }
        }
    }

    private void updateMessagesVisiblePart(boolean inLayout) {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        int height = chatListView.getMeasuredHeight();
        int minPositionHolder = Integer.MAX_VALUE;
        int minPositionDateHolder = Integer.MAX_VALUE;
        View minDateChild = null;
        View minChild = null;
        View minMessageChild = null;
        boolean foundTextureViewMessage = false;

        Integer currentReadMaxId = getMessagesController().dialogs_read_inbox_max.get(dialog_id);
        if (currentReadMaxId == null) {
            currentReadMaxId = 0;
        }
        int maxPositiveUnreadId = Integer.MIN_VALUE;
        int maxNegativeUnreadId = Integer.MAX_VALUE;
        int maxUnreadDate = Integer.MIN_VALUE;
        int lastVisibleId = currentEncryptedChat != null ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        pollsToCheck.clear();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            MessageObject messageObject = null;
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

                messageObject = messageCell.getMessageObject();
                boolean isVideo;
                if (videoPlayerContainer != null && (isVideo = messageObject.isVideo() || messageObject.isRoundVideo()) && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    if (isVideo && top + imageReceiver.getImageY2() < 0) {
                        foundTextureViewMessage = false;
                    } else {
                        videoPlayerContainer.setTranslationX(imageReceiver.getImageX() + messageCell.getX());
                        videoPlayerContainer.setTranslationY(fragmentView.getPaddingTop() + top + imageReceiver.getImageY() - chatListViewClipTop + chatListView.getTranslationY() + (inPreviewMode ? AndroidUtilities.statusBarHeight : 0));
                        fragmentView.invalidate();
                        videoPlayerContainer.invalidate();
                        foundTextureViewMessage = true;
                    }
                }
            } else if (view instanceof ChatActionCell) {
                messageObject = ((ChatActionCell) view).getMessageObject();
            }
            if (!inScheduleMode && messageObject != null) {
                if (!messageObject.isOut() && messageObject.isUnread() || messageObject.messageOwner.from_scheduled && messageObject.getId() > currentReadMaxId) {
                    int id = messageObject.getId();
                    if (id > 0) {
                        maxPositiveUnreadId = Math.max(maxPositiveUnreadId, messageObject.getId());
                    }
                    if (id < 0) {
                        maxNegativeUnreadId = Math.min(maxNegativeUnreadId, messageObject.getId());
                    }
                    maxUnreadDate = Math.max(maxUnreadDate, messageObject.messageOwner.date);
                }
                if (messageObject.type == MessageObject.TYPE_POLL) {
                    pollsToCheck.add(messageObject);
                }
            }
            if (view.getBottom() <= chatListView.getPaddingTop() + AndroidUtilities.dp(1) + chatListViewClipTop) {
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
            if (view instanceof ChatActionCell && messageObject.isDateObject) {
                if (view.getAlpha() != 1.0f) {
                    view.setAlpha(1.0f);
                }
                if (position < minPositionDateHolder) {
                    minPositionDateHolder = position;
                    minDateChild = view;
                }
            }
        }
        getMessagesController().addToPollsQueue(dialog_id, pollsToCheck);
        if (videoPlayerContainer != null) {
            if (!foundTextureViewMessage) {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null) {
                    if (checkTextureViewPosition && messageObject.isVideo()) {
                        MediaController.getInstance().cleanupPlayer(true, true);
                    } else {
                        videoPlayerContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                        fragmentView.invalidate();
                        if ((messageObject.isRoundVideo() || messageObject.isVideo()) && messageObject.eventId == 0 && checkTextureViewPosition) {
                            MediaController.getInstance().setCurrentVideoVisible(false);
                        }
                    }
                }
            } else {
                MediaController.getInstance().setCurrentVideoVisible(true);
            }
        }
        if (minMessageChild != null) {
            MessageObject messageObject;
            if (minMessageChild instanceof ChatMessageCell) {
                messageObject = ((ChatMessageCell) minMessageChild).getMessageObject();
            } else {
                messageObject = ((ChatActionCell) minMessageChild).getMessageObject();
            }
            floatingDateView.setCustomDate(messageObject.messageOwner.date, inScheduleMode);
        }
        currentFloatingDateOnScreen = false;
        currentFloatingTopIsNotMessage = !(minChild instanceof ChatMessageCell || minChild instanceof ChatActionCell);
        if (minDateChild != null) {
            if (minDateChild.getTop() - chatListViewClipTop > chatListView.getPaddingTop() || currentFloatingTopIsNotMessage) {
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
            float offset = minDateChild.getBottom() - chatListView.getPaddingTop() - chatListViewClipTop;
            if (offset > floatingDateView.getMeasuredHeight() && offset < floatingDateView.getMeasuredHeight() * 2) {
                floatingDateView.setTranslationY(-floatingDateView.getMeasuredHeight() * 2 + offset);
            } else {
                floatingDateView.setTranslationY(0);
            }
        } else {
            hideFloatingDateView(true);
            floatingDateView.setTranslationY(0);
        }

        if (!firstLoading && !paused && !inPreviewMode && !inScheduleMode) {
            int scheduledRead = 0;
            if ((maxPositiveUnreadId != Integer.MIN_VALUE || maxNegativeUnreadId != Integer.MAX_VALUE)) {
                int counterDecrement = 0;
                for (int a = 0; a < messages.size(); a++) {
                    MessageObject messageObject = messages.get(a);
                    int id = messageObject.getId();
                    if (maxPositiveUnreadId != Integer.MIN_VALUE) {
                        if (id > 0 && id <= maxPositiveUnreadId && (messageObject.messageOwner.from_scheduled && id > currentReadMaxId || messageObject.isUnread())) {
                            if (messageObject.messageOwner.from_scheduled) {
                                scheduledRead++;
                            } else {
                                messageObject.setIsRead();
                            }
                            counterDecrement++;
                        }
                    }
                    if (maxNegativeUnreadId != Integer.MAX_VALUE) {
                        if (id < 0 && id >= maxNegativeUnreadId && messageObject.isUnread()) {
                            messageObject.setIsRead();
                            counterDecrement++;
                        }
                    }
                }
                if (forwardEndReached[0] && maxPositiveUnreadId == minMessageId[0] || maxNegativeUnreadId == minMessageId[0]) {
                    newUnreadMessageCount = 0;
                } else {
                    newUnreadMessageCount -= counterDecrement;
                    if (newUnreadMessageCount < 0) {
                        newUnreadMessageCount = 0;
                    }
                }
                if (inLayout) {
                    AndroidUtilities.runOnUIThread(this::inlineUpdate1);
                } else {
                    inlineUpdate1();
                }
                getMessagesController().markDialogAsRead(dialog_id, maxPositiveUnreadId, maxNegativeUnreadId, maxUnreadDate, false, counterDecrement, maxPositiveUnreadId == minMessageId[0] || maxNegativeUnreadId == minMessageId[0], scheduledRead);
                firstUnreadSent = true;
            } else if (!firstUnreadSent) {
                if (chatLayoutManager.findFirstVisibleItemPosition() == 0) {
                    newUnreadMessageCount = 0;
                    if (inLayout) {
                        AndroidUtilities.runOnUIThread(this::inlineUpdate2);
                    } else {
                        inlineUpdate2();
                    }
                    getMessagesController().markDialogAsRead(dialog_id, minMessageId[0], minMessageId[0], maxDate[0], false, 0, true, scheduledRead);
                    firstUnreadSent = true;
                }
            }
        }
    }

    private void inlineUpdate1() {
        if (prevSetUnreadCount != newUnreadMessageCount) {
            prevSetUnreadCount = newUnreadMessageCount;
            pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
        }
        if (newUnreadMessageCount <= 0) {
            if (pagedownButtonCounter.getVisibility() != View.INVISIBLE) {
                pagedownButtonCounter.setVisibility(View.INVISIBLE);
            }
        } else {
            if (pagedownButtonCounter.getVisibility() != View.VISIBLE) {
                pagedownButtonCounter.setVisibility(View.VISIBLE);
            }
        }
    }

    private void inlineUpdate2() {
        if (prevSetUnreadCount != newUnreadMessageCount) {
            prevSetUnreadCount = newUnreadMessageCount;
            pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
        }
        if (pagedownButtonCounter.getVisibility() != View.INVISIBLE) {
            pagedownButtonCounter.setVisibility(View.INVISIBLE);
        }
    }

    private void toggleMute(boolean instant) {
        boolean muted = getMessagesController().isDialogMuted(dialog_id);
        if (!muted) {
            if (instant) {
                long flags;
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("notify2_" + dialog_id, 2);
                flags = 1;
                getMessagesStorage().setDialogFlags(dialog_id, flags);
                editor.commit();
                TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(dialog_id);
                if (dialog != null) {
                    dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                    dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                }
                getNotificationsController().updateServerNotificationsSettings(dialog_id);
                getNotificationsController().removeNotificationsForDialog(dialog_id);
            } else {
                showDialog(AlertsCreator.createMuteAlert(getParentActivity(), dialog_id));
            }
        } else {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("notify2_" + dialog_id, 0);
            getMessagesStorage().setDialogFlags(dialog_id, 0);
            editor.commit();
            TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(dialog_id);
            if (dialog != null) {
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            }
            getNotificationsController().updateServerNotificationsSettings(dialog_id);
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

    private void startMessageUnselect() {
        if (unselectRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(unselectRunnable);
        }
        unselectRunnable = () -> {
            highlightMessageId = Integer.MAX_VALUE;
            updateVisibleRows();
            unselectRunnable = null;
        };
        AndroidUtilities.runOnUIThread(unselectRunnable, 1000);
    }

    private void removeSelectedMessageHighlight() {
        if (unselectRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(unselectRunnable);
            unselectRunnable = null;
        }
        highlightMessageId = Integer.MAX_VALUE;
    }

    public void scrollToMessageId(int id, int fromMessageId, boolean select, int loadIndex, boolean smooth) {
        wasManualScroll = true;
        MessageObject object = messagesDict[loadIndex].get(id);
        boolean query = false;
        if (object != null) {
            int index = messages.indexOf(object);
            if (index != -1) {
                removeSelectedMessageHighlight();
                if (select) {
                    highlightMessageId = id;
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
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                            break;
                        }
                    } else if (view instanceof ChatActionCell) {
                        ChatActionCell cell = (ChatActionCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.getId() == object.getId()) {
                            found = true;
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
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
            if (currentEncryptedChat != null && !getMessagesStorage().checkMessageId(dialog_id, startLoadFromMessageId)) {
                return;
            }
            /*clearChatData();
            loadsCount = 0;
            first_unread_id = 0;
            loadingForward = false;
            unreadMessageObject = null;
            scrollToMessage = null;*/

            waitingForLoad.clear();
            waitingForReplyMessageLoad = true;
            removeSelectedMessageHighlight();
            scrollToMessagePosition = -10000;
            startLoadFromMessageId = id;
            showScrollToMessageError = true;
            if (id == createUnreadMessageAfterId) {
                createUnreadMessageAfterIdLoading = true;
            }
            waitingForLoad.add(lastLoadIndex);
            getMessagesController().loadMessages(loadIndex == 0 ? dialog_id : mergeDialogId, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
            //emptyViewContainer.setVisibility(View.INVISIBLE);
        } else {
            View child = chatListView.getChildAt(0);
            if (child != null && child.getTop() <= 0) {
                showFloatingDateView(false);
            }
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
                                ObjectAnimator.ofFloat(pagedownButton, View.TRANSLATION_Y, 0),
                                ObjectAnimator.ofFloat(mentiondownButton, View.TRANSLATION_Y, -AndroidUtilities.dp(72)));
                    } else {
                        pagedownButtonAnimation.playTogether(ObjectAnimator.ofFloat(pagedownButton, View.TRANSLATION_Y, 0));
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
                                ObjectAnimator.ofFloat(pagedownButton, View.TRANSLATION_Y, AndroidUtilities.dp(100)),
                                ObjectAnimator.ofFloat(mentiondownButton, View.TRANSLATION_Y, 0));
                    } else {
                        pagedownButtonAnimation.playTogether(ObjectAnimator.ofFloat(pagedownButton, View.TRANSLATION_Y, AndroidUtilities.dp(100)));
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

    private void showMentionDownButton(boolean show, boolean animated) {
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
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 0.0f, 1.0f).setDuration(200);
                    } else {
                        if (mentiondownButton.getTranslationY() == 0) {
                            mentiondownButton.setTranslationY(AndroidUtilities.dp(100));
                        }
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, View.TRANSLATION_Y, 0).setDuration(200);
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
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 1.0f, 0.0f).setDuration(200);
                    } else {
                        mentiondownButtonAnimation = ObjectAnimator.ofFloat(mentiondownButton, View.TRANSLATION_Y, AndroidUtilities.dp(100)).setDuration(200);
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
        if (currentChat != null && !ChatObject.canSendMessages(currentChat) && (!ChatObject.isChannel(currentChat) || currentChat.megagroup)) {
            if (currentChat.default_banned_rights != null && currentChat.default_banned_rights.send_messages) {
                bottomOverlayText.setText(LocaleController.getString("GlobalSendMessageRestricted", R.string.GlobalSendMessageRestricted));
            } else if (AndroidUtilities.isBannedForever(currentChat.banned_rights)) {
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
            updateMessageListAccessibilityVisibility();
            hideKeyboard = true;
            if (stickersAdapter != null) {
                stickersAdapter.hide();
            }
        } else {
            if (currentEncryptedChat == null || bigEmptyView == null) {
                bottomOverlay.setVisibility(View.INVISIBLE);
                if (stickersAdapter != null && chatActivityEnterView != null && chatActivityEnterView.hasText()) {
                    stickersAdapter.loadStikersForEmoji(chatActivityEnterView.getFieldText(), false);
                }
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
                getMediaDataController().cleanDraft(dialog_id, false);
                hideKeyboard = true;
            } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
                bottomOverlay.setVisibility(View.INVISIBLE);
            }
            checkRaiseSensors();
            checkActionBarMenu();
        }
        if (inPreviewMode) {
            bottomOverlay.setVisibility(View.INVISIBLE);
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
        if (requestCode == 4 && chatAttachAlert != null) {
            chatAttachAlert.checkStorage();
        } else if ((requestCode == 17 || requestCode == 18) && chatAttachAlert != null) {
            chatAttachAlert.checkCamera(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        } else if (requestCode == 21) {
            if (getParentActivity() == null) {
                return;
            }
            if (grantResults != null && grantResults.length != 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("PermissionNoAudioVideo", R.string.PermissionNoAudioVideo));
                builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
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
                VoIPHelper.startCall(currentUser, getParentActivity(), getMessagesController().getUserFull(currentUser.id));
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
            if (messageObject.isEditing()) {
                return -1;
            } else if (messageObject.getId() <= 0 && messageObject.isOut()) {
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
                if (messageObject.isAnimatedEmoji()) {
                    return 2;
                } else if (messageObject.type == 6) {
                    return -1;
                } else if (messageObject.type == 10 || messageObject.type == 11) {
                    if (messageObject.getId() == 0) {
                        return -1;
                    }
                    return 1;
                } else {
                    if (messageObject.isVoice()) {
                        return 2;
                    } else if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                        TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                        if (inputStickerSet instanceof TLRPC.TL_inputStickerSetID) {
                            if (!getMediaDataController().isStickerPackInstalled(inputStickerSet.id)) {
                                return 7;
                            }
                        } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                            if (!getMediaDataController().isStickerPackInstalled(inputStickerSet.short_name)) {
                                return 7;
                            }
                        }
                        return 9;
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
                                if (mime != null) {
                                    if (messageObject.getDocumentName().toLowerCase().endsWith("attheme")) {
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
            if (messageObject.isAnimatedEmoji()) {
                return 2;
            } else if (messageObject.type == 6) {
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
                } else if (!messageObject.isAnimatedEmoji() && (messageObject.isSticker() || messageObject.isAnimatedSticker())) {
                    TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                    if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                        if (!getMediaDataController().isStickerPackInstalled(inputStickerSet.short_name)) {
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
        int prevCantForwardCount = cantForwardMessagesCount;
        if (messageObject != null) {
            int index = messageObject.getDialogId() == dialog_id ? 0 : 1;
            if (outside && messageObject.getGroupId() != 0) {
                boolean hasUnselected = false;
                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
                if (groupedMessages != null) {
                    int lastNum = 0;
                    for (int a = 0; a < groupedMessages.messages.size(); a++) {
                        MessageObject message = groupedMessages.messages.get(a);
                        if (selectedMessagesIds[index].indexOfKey(message.getId()) < 0) {
                            hasUnselected = true;
                            lastNum = a;
                        }
                    }

                    for (int a = 0; a < groupedMessages.messages.size(); a++) {
                        MessageObject message = groupedMessages.messages.get(a);
                        if (hasUnselected) {
                            if (selectedMessagesIds[index].indexOfKey(message.getId()) < 0) {
                                addToSelectedMessages(message, false, a == lastNum);
                            }
                        } else {
                            addToSelectedMessages(message, false, a == groupedMessages.messages.size() - 1);
                        }
                    }
                }
                return;
            }
            if (selectedMessagesIds[index].indexOfKey(messageObject.getId()) >= 0) {
                selectedMessagesIds[index].remove(messageObject.getId());
                if (messageObject.type == 0 || messageObject.isAnimatedEmoji() || messageObject.caption != null) {
                    selectedMessagesCanCopyIds[index].remove(messageObject.getId());
                }
                if (!messageObject.isAnimatedEmoji() && (messageObject.isSticker() || messageObject.isAnimatedSticker())) {
                    selectedMessagesCanStarIds[index].remove(messageObject.getId());
                }
                if (messageObject.canEditMessage(currentChat)) {
                    canEditMessagesCount--;
                }
                if (!messageObject.canDeleteMessage(inScheduleMode, currentChat)) {
                    cantDeleteMessagesCount--;
                }
                if (inScheduleMode || !messageObject.canForwardMessage()) {
                    cantForwardMessagesCount--;
                } else {
                    canForwardMessagesCount--;
                }
            } else {
                if (selectedMessagesIds[0].size() + selectedMessagesIds[1].size() >= 100) {
                    return;
                }
                selectedMessagesIds[index].put(messageObject.getId(), messageObject);
                if (messageObject.type == 0 || messageObject.isAnimatedEmoji() || messageObject.caption != null) {
                    selectedMessagesCanCopyIds[index].put(messageObject.getId(), messageObject);
                }
                if (!messageObject.isAnimatedEmoji() && (messageObject.isSticker() || messageObject.isAnimatedSticker())) {
                    selectedMessagesCanStarIds[index].put(messageObject.getId(), messageObject);
                }
                if (messageObject.canEditMessage(currentChat)) {
                    canEditMessagesCount++;
                }
                if (!messageObject.canDeleteMessage(inScheduleMode, currentChat)) {
                    cantDeleteMessagesCount++;
                }
                if (inScheduleMode || !messageObject.canForwardMessage()) {
                    cantForwardMessagesCount++;
                } else {
                    canForwardMessagesCount++;
                }
            }
        }
        if (forwardButtonAnimation != null) {
            forwardButtonAnimation.cancel();
            forwardButtonAnimation = null;
        }
        if (last && actionBar.isActionModeShowed()) {
            int selectedCount = selectedMessagesIds[0].size() + selectedMessagesIds[1].size();
            if (selectedCount == 0) {
                hideActionMode();
                updatePinnedMessageView(true);
            } else {
                ActionBarMenuItem copyItem = actionBar.createActionMode().getItem(copy);
                ActionBarMenuItem starItem = actionBar.createActionMode().getItem(star);
                ActionBarMenuItem editItem = actionBar.createActionMode().getItem(edit);
                ActionBarMenuItem forwardItem = actionBar.createActionMode().getItem(forward);

                if (prevCantForwardCount == 0 && cantForwardMessagesCount != 0 || prevCantForwardCount != 0 && cantForwardMessagesCount == 0) {
                    forwardButtonAnimation = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    if (forwardItem != null) {
                        forwardItem.setEnabled(cantForwardMessagesCount == 0);
                        animators.add(ObjectAnimator.ofFloat(forwardItem, View.ALPHA, cantForwardMessagesCount == 0 ? 1.0f : 0.5f));
                    }
                    if (forwardButton != null) {
                        forwardButton.setEnabled(cantForwardMessagesCount == 0);
                        animators.add(ObjectAnimator.ofFloat(forwardButton, View.ALPHA, cantForwardMessagesCount == 0 ? 1.0f : 0.5f));
                    }
                    forwardButtonAnimation.playTogether(animators);
                    forwardButtonAnimation.setDuration(100);
                    forwardButtonAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            forwardButtonAnimation = null;
                        }
                    });
                    forwardButtonAnimation.start();
                } else {
                    if (forwardItem != null) {
                        forwardItem.setEnabled(cantForwardMessagesCount == 0);
                        forwardItem.setAlpha(cantForwardMessagesCount == 0 ? 1.0f : 0.5f);
                    }
                    if (forwardButton != null) {
                        forwardButton.setEnabled(cantForwardMessagesCount == 0);
                        forwardButton.setAlpha(cantForwardMessagesCount == 0 ? 1.0f : 0.5f);
                    }
                }

                int copyVisible = copyItem.getVisibility();
                int starVisible = starItem.getVisibility();
                copyItem.setVisibility(selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);
                starItem.setVisibility(getMediaDataController().canAddStickerToFavorites() && (selectedMessagesCanStarIds[0].size() + selectedMessagesCanStarIds[1].size()) == selectedCount ? View.VISIBLE : View.GONE);
                int newCopyVisible = copyItem.getVisibility();
                int newStarVisible = starItem.getVisibility();
                actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
                hasUnfavedSelected = false;
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < selectedMessagesCanStarIds[a].size(); b++) {
                        MessageObject msg = selectedMessagesCanStarIds[a].valueAt(b);
                        if (!getMediaDataController().isStickerInFavorites(msg.getDocument())) {
                            hasUnfavedSelected = true;
                            break;
                        }
                    }
                    if (hasUnfavedSelected) {
                        break;
                    }
                }
                starItem.setIcon(hasUnfavedSelected ? R.drawable.msg_fave : R.drawable.msg_unfave);
                final int newEditVisibility = canEditMessagesCount == 1 && selectedCount == 1 ? View.VISIBLE : View.GONE;
                if (replyButton != null) {
                    boolean allowChatActions = true;
                    if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 ||
                            bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE ||
                            currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !ChatObject.canPost(currentChat) && !currentChat.megagroup || !ChatObject.canSendMessages(currentChat))) {
                        allowChatActions = false;
                    }

                    int newVisibility;

                    if (inScheduleMode || !allowChatActions || selectedCount == 0 || selectedMessagesIds[0].size() != 0 && selectedMessagesIds[1].size() != 0) {
                        newVisibility = View.GONE;
                    } else if (selectedCount == 1) {
                        newVisibility = View.VISIBLE;
                    } else {
                        newVisibility = View.VISIBLE;
                        long lastGroupId = 0;
                        for (int a = 0; a < 2; a++) {
                            for (int b = 0, N = selectedMessagesIds[a].size(); b < N; b++) {
                                MessageObject message = selectedMessagesIds[a].valueAt(b);
                                long groupId = message.getGroupId();
                                if (groupId == 0 || lastGroupId != 0 && lastGroupId != groupId) {
                                    newVisibility = View.GONE;
                                    break;
                                }
                                lastGroupId = groupId;
                            }
                            if (newVisibility == View.GONE) {
                                break;
                            }
                        }
                    }

                    if (replyButton.getVisibility() != newVisibility) {
                        if (replyButtonAnimation != null) {
                            replyButtonAnimation.cancel();
                        }
                        replyButtonAnimation = new AnimatorSet();
                        if (newVisibility == View.VISIBLE) {
                            replyButton.setVisibility(newVisibility);
                            replyButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(replyButton, View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(replyButton, View.SCALE_Y, 1.0f)
                            );
                        } else {
                            replyButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(replyButton, View.ALPHA, 0.0f),
                                    ObjectAnimator.ofFloat(replyButton, View.SCALE_Y, 0.0f)
                            );
                        }
                        replyButtonAnimation.setDuration(100);
                        int newVisibilityFinal = newVisibility;
                        replyButtonAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (replyButtonAnimation != null && replyButtonAnimation.equals(animation)) {
                                    if (newVisibilityFinal == View.GONE) {
                                        replyButton.setVisibility(View.GONE);
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

                if (editItem != null) {
                    if (copyVisible != newCopyVisible || starVisible != newStarVisible) {
                        if (newEditVisibility == View.VISIBLE) {
                            editItem.setAlpha(1.0f);
                            editItem.setScaleX(1.0f);
                        } else {
                            editItem.setAlpha(0.0f);
                            editItem.setScaleX(0.0f);
                        }
                        editItem.setVisibility(newEditVisibility);
                    } else if (editItem.getVisibility() != newEditVisibility) {
                        if (editButtonAnimation != null) {
                            editButtonAnimation.cancel();
                        }
                        editButtonAnimation = new AnimatorSet();
                        editItem.setPivotX(AndroidUtilities.dp(54));
                        editItem.setPivotX(AndroidUtilities.dp(54));
                        if (newEditVisibility == View.VISIBLE) {
                            editItem.setVisibility(newEditVisibility);
                            editButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(editItem, View.ALPHA, 1.0f),
                                    ObjectAnimator.ofFloat(editItem, View.SCALE_X, 1.0f)
                            );
                        } else {
                            editButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(editItem, View.ALPHA, 0.0f),
                                    ObjectAnimator.ofFloat(editItem, View.SCALE_X, 0.0f)
                            );
                        }
                        editButtonAnimation.setDuration(100);
                        editButtonAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (editButtonAnimation != null && editButtonAnimation.equals(animation)) {
                                    if (newEditVisibility == View.GONE) {
                                        editItem.setVisibility(View.GONE);
                                    }
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                if (editButtonAnimation != null && editButtonAnimation.equals(animation)) {
                                    editButtonAnimation = null;
                                }
                            }
                        });
                        editButtonAnimation.start();
                    }
                }
            }
        }
    }

    private void processRowSelect(View view, boolean outside, float touchX, float touchY) {
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
        if (selectedMessagesIds[0].size() != 0 || selectedMessagesIds[1].size() != 0) {
            selectedMessagesCountTextView.setNumber(selectedMessagesIds[0].size() + selectedMessagesIds[1].size(), true);
        }
    }

    private void updateTitle() {
        if (avatarContainer == null) {
            return;
        }
        if (inScheduleMode) {
            if (UserObject.isUserSelf(currentUser)) {
                avatarContainer.setTitle(LocaleController.getString("Reminders", R.string.Reminders));
            } else {
                avatarContainer.setTitle(LocaleController.getString("ScheduledMessages", R.string.ScheduledMessages));
            }
        } else if (currentChat != null) {
            avatarContainer.setTitle(currentChat.title, currentChat.scam);
        } else if (currentUser != null) {
            if (currentUser.self) {
                avatarContainer.setTitle(LocaleController.getString("SavedMessages", R.string.SavedMessages));
            } else if (!MessagesController.isSupportUser(currentUser) && getContactsController().contactsDict.get(currentUser.id) == null && (getContactsController().contactsDict.size() != 0 || !getContactsController().isLoadingContacts())) {
                if (!TextUtils.isEmpty(currentUser.phone)) {
                    avatarContainer.setTitle(PhoneFormat.getInstance().format("+" + currentUser.phone));
                } else {
                    avatarContainer.setTitle(UserObject.getUserName(currentUser), currentUser.scam);
                }
            } else {
                avatarContainer.setTitle(UserObject.getUserName(currentUser), currentUser.scam);
            }
        }
        setParentActivityTitle(avatarContainer.getTitleTextView().getText());
    }

    private void updateBotButtons() {
        if (headerItem == null || currentUser == null || currentEncryptedChat != null || !currentUser.bot) {
            return;
        }
        boolean hasHelp = false;
        boolean hasSettings = false;
        if (botInfo.size() != 0) {
            for (int b = 0; b < botInfo.size(); b++) {
                TLRPC.BotInfo info = botInfo.valueAt(b);
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
        if (avatarContainer == null || inScheduleMode) {
            return;
        }
        Drawable rightIcon = getMessagesController().isDialogMuted(dialog_id) ? Theme.chat_muteIconDrawable : null;
        avatarContainer.setTitleIcons(currentEncryptedChat != null ? Theme.chat_lockIconDrawable : null, rightIcon);
        if (muteItem != null) {
            if (rightIcon != null) {
                muteItem.setTextAndIcon(LocaleController.getString("UnmuteNotifications", R.string.UnmuteNotifications), R.drawable.msg_unmute);
            } else {
                muteItem.setTextAndIcon(LocaleController.getString("MuteNotifications", R.string.MuteNotifications), R.drawable.msg_mute);
            }
        }
    }

    private void checkAndUpdateAvatar() {
        if (currentUser != null) {
            TLRPC.User user = getMessagesController().getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
        } else if (currentChat != null) {
            TLRPC.Chat chat = getMessagesController().getChat(currentChat.id);
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
                public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
                    return new ImageReceiver.BitmapHolder(thumb, null);
                }

                @Override
                public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate) {
                    sendMedia((MediaController.PhotoEntry) cameraPhoto.get(0), videoEditedInfo, notify, scheduleDate);
                }

                @Override
                public boolean canScrollAway() {
                    return false;
                }
            }, this);
        } else {
            fillEditingMediaWithCaption(caption, null);
            SendMessagesHelper.prepareSendingVideo(getAccountInstance(), videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, null, null, 0, editingMessageObject, true, 0);
            afterMessageSend();
        }
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
    }

    private void fillEditingMediaWithCaption(CharSequence caption, ArrayList<TLRPC.MessageEntity> entities) {
        if (editingMessageObject == null) {
            return;
        }
        if (!TextUtils.isEmpty(caption)) {
            editingMessageObject.editingMessage = caption;
            editingMessageObject.editingMessageEntities = entities;
        } else if (chatActivityEnterView != null) {
            editingMessageObject.editingMessage = chatActivityEnterView.getFieldText();
            if (editingMessageObject.editingMessage == null && !TextUtils.isEmpty(editingMessageObject.messageOwner.message)) {
                editingMessageObject.editingMessage = "";
            }
        }
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
        fillEditingMediaWithCaption(null, null);
        SendMessagesHelper.prepareSendingDocument(getAccountInstance(), tempPath, originalPath, null, null, null, dialog_id, replyingMessageObject, null, editingMessageObject, true, 0);
        hideFieldPanel(false);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0 || requestCode == 2) {
                createChatAttachView();
                if (chatAttachAlert != null) {
                    chatAttachAlert.onActivityResultFragment(requestCode, data, currentPicturePath);
                }
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
                    if (editingMessageObject == null && inScheduleMode) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(currentUser), (notify, scheduleDate) -> {
                            fillEditingMediaWithCaption(null, null);
                            SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), null, uri, dialog_id, replyingMessageObject, null, null, null, null, 0, editingMessageObject, notify, scheduleDate);
                        });
                    } else {
                        fillEditingMediaWithCaption(null, null);
                        SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), null, uri, dialog_id, replyingMessageObject, null, null, null, null, 0, editingMessageObject, true, 0);
                    }
                }
                afterMessageSend();
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
                afterMessageSend();
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

    private void removeUnreadPlane(boolean scrollToEnd) {
        if (unreadMessageObject != null) {
            if (scrollToEnd) {
                forwardEndReached[0] = forwardEndReached[1] = true;
                first_unread_id = 0;
                last_message_id = 0;
            }
            createUnreadMessageAfterId = 0;
            createUnreadMessageAfterIdLoading = false;
            removeMessageObject(unreadMessageObject);
            unreadMessageObject = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            int guid = (Integer) args[10];
            if (guid == classGuid) {
            	setItemAnimationsEnabled(false);
                if (!openAnimationEnded) {
                    getNotificationCenter().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoad, NotificationCenter.dialogsNeedReload, NotificationCenter.scheduledMessagesUpdated,
                            NotificationCenter.closeChats, NotificationCenter.botKeyboardDidLoad, NotificationCenter.userInfoDidLoad, NotificationCenter.needDeleteDialog/*, NotificationCenter.botInfoDidLoad*/});
                }
                int queryLoadIndex = (Integer) args[11];
                int index = waitingForLoad.indexOf(queryLoadIndex);
                int currentUserId = getUserConfig().getClientUserId();
                boolean schedule = (Boolean) args[14];
                boolean isCache = (Boolean) args[3];
                if (index == -1) {
                    if (inScheduleMode && schedule && !isCache) {
                        waitingForReplyMessageLoad = true;
                        waitingForLoad.add(lastLoadIndex);
                        getMessagesController().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, 0, true, 0, classGuid, 2, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
                    }
                    return;
                } else {
                    waitingForLoad.remove(index);
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                if (inScheduleMode != schedule) {
                    if (!inScheduleMode) {
                        scheduledMessagesCount = messArr.size();
                        updateScheduledInterface(true);
                    }
                    return;
                }
                boolean createUnreadLoading = false;
                boolean showDateAfter = waitingForReplyMessageLoad;
                if (waitingForReplyMessageLoad) {
                    if (!inScheduleMode && !createUnreadMessageAfterIdLoading) {
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
                    if (!inScheduleMode) {
                        createUnreadMessageAfterId = unreadAfterId;
                        startLoadFromMessageId = startLoadFrom;
                        needSelectFromMessageId = needSelect;
                    }
                }

                loadsCount++;
                long did = (Long) args[0];
                int loadIndex = did == dialog_id ? 0 : 1;
                int count = (Integer) args[1];
                int fnid = (Integer) args[4];
                int last_unread_date = (Integer) args[7];
                int load_type = (Integer) args[8];
                boolean isEnd = (Boolean) args[9];
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
                int unread_to_load = 0;
                if (fnid != 0) {
                    last_message_id = (Integer) args[5];
                    if (load_type == 3) {
                        if (loadingFromOldPosition) {
                            unread_to_load = (Integer) args[6];
                            if (unread_to_load != 0) {
                                createUnreadMessageAfterId = fnid;
                            }
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

                if (load_type != 0 && (startLoadFromMessageId != 0 || last_message_id != 0)) {
                    forwardEndReached[loadIndex] = false;
                }
                if ((load_type == 1 || load_type == 3) && loadIndex == 1) {
                    endReached[0] = cacheEndReached[0] = true;
                    forwardEndReached[0] = false;
                    minMessageId[0] = 0;
                }
                if (inScheduleMode) {
                    endReached[0] = cacheEndReached[0] = true;
                    forwardEndReached[0] = forwardEndReached[0] = true;
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
                    AndroidUtilities.runOnUIThread(() -> {
                        if (parentLayout != null) {
                            parentLayout.resumeDelayedFragmentAnimation();
                        }
                    });
                }

                if (load_type == 1) {
                    Collections.reverse(messArr);
                }
                if (currentEncryptedChat == null) {
                    getMediaDataController().loadReplyMessagesForMessages(messArr, dialog_id, inScheduleMode);
                }
                int approximateHeightSum = 0;
                if ((load_type == 2 || load_type == 1) && messArr.isEmpty() && !isCache) {
                    forwardEndReached[0] = true;
                }
                LongSparseArray<MessageObject.GroupedMessages> newGroups = null;
                LongSparseArray<MessageObject.GroupedMessages> changedGroups = null;
                MediaController mediaController = MediaController.getInstance();
                TLRPC.MessageAction dropPhotoAction = null;
                boolean createdWas = false;
                for (int a = 0, N = messArr.size(); a < N; a++) {
                    MessageObject obj = messArr.get(N - a - 1);
                    TLRPC.MessageAction action = obj.messageOwner.action;
                    if (a == 0 && action instanceof TLRPC.TL_messageActionChatCreate) {
                        createdWas = true;
                    } else if (!createdWas) {
                        break;
                    } else if (a < 2 && action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                        dropPhotoAction = action;
                    }
                }
                for (int a = 0; a < messArr.size(); a++) {
                    MessageObject obj = messArr.get(a);
                    int messageId = obj.getId();

                    approximateHeightSum += obj.getApproximateHeight();
                    if (currentUser != null) {
                        if (currentUser.self) {
                            obj.messageOwner.out = true;
                        }
                        if (!inScheduleMode && (currentUser.bot && obj.isOut() || currentUser.id == currentUserId)) {
                            obj.setIsRead();
                        }
                    }
                    if (messagesDict[loadIndex].indexOfKey(messageId) >= 0) {
                        continue;
                    }
                    addToPolls(obj, null);
                    if (isSecretChat()) {
                        checkSecretMessageForLocation(obj);
                    }
                    if (mediaController.isPlayingMessage(obj)) {
                        MessageObject player = mediaController.getPlayingMessageObject();
                        obj.audioProgress = player.audioProgress;
                        obj.audioProgressSec = player.audioProgressSec;
                        obj.audioPlayerDuration = player.audioPlayerDuration;
                    }
                    if (loadIndex == 0 && ChatObject.isChannel(currentChat) && messageId == 1) {
                        endReached[loadIndex] = true;
                        cacheEndReached[loadIndex] = true;
                    }
                    if (messageId > 0) {
                        maxMessageId[loadIndex] = Math.min(messageId, maxMessageId[loadIndex]);
                        minMessageId[loadIndex] = Math.max(messageId, minMessageId[loadIndex]);
                    } else if (currentEncryptedChat != null) {
                        maxMessageId[loadIndex] = Math.max(messageId, maxMessageId[loadIndex]);
                        minMessageId[loadIndex] = Math.min(messageId, minMessageId[loadIndex]);
                    }
                    if (obj.messageOwner.date != 0) {
                        maxDate[loadIndex] = Math.max(maxDate[loadIndex], obj.messageOwner.date);
                        if (minDate[loadIndex] == 0 || obj.messageOwner.date < minDate[loadIndex]) {
                            minDate[loadIndex] = obj.messageOwner.date;
                        }
                    }

                    if (messageId == last_message_id) {
                        forwardEndReached[loadIndex] = true;
                    }

                    TLRPC.MessageAction action = obj.messageOwner.action;
                    if (obj.type < 0 || loadIndex == 1 && action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                        continue;
                    }

                    if (currentChat != null && currentChat.creator && (action instanceof TLRPC.TL_messageActionChatCreate || dropPhotoAction != null && action == dropPhotoAction)) {
                        continue;
                    }
                    if (obj.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                        continue;
                    }

                    if (needAnimateToMessage != null && needAnimateToMessage.getId() == messageId && messageId < 0 && obj.type == MessageObject.TYPE_ROUND_VIDEO && !inScheduleMode) {
                        obj = needAnimateToMessage;
                        animatingMessageObjects.add(obj);
                        needAnimateToMessage = null;
                    }

                    messagesDict[loadIndex].put(messageId, obj);
                    ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);

                    if (dayArray == null) {
                        dayArray = new ArrayList<>();
                        messagesByDays.put(obj.dateKey, dayArray);
                        TLRPC.Message dateMsg = new TLRPC.TL_message();
                        if (inScheduleMode) {
                            dateMsg.message = LocaleController.formatString("MessageScheduledOn", R.string.MessageScheduledOn, LocaleController.formatDateChat(obj.messageOwner.date, true));
                        } else {
                            dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                        }
                        dateMsg.id = 0;
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTimeInMillis(((long) obj.messageOwner.date) * 1000);
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        dateMsg.date = (int) (calendar.getTimeInMillis() / 1000);
                        MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false);
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
                        MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(obj.getGroupIdForUse());
                        if (groupedMessages != null) {
                            if (messages.size() > 1) {
                                MessageObject previous;
                                if (load_type == 1) {
                                    previous = messages.get(0);
                                } else {
                                    previous = messages.get(messages.size() - 2);
                                }
                                if (previous.getGroupIdForUse() == obj.getGroupIdForUse()) {
                                    if (previous.localGroupId != 0) {
                                        obj.localGroupId = previous.localGroupId;
                                        groupedMessages = groupedMessagesMap.get(previous.localGroupId);
                                    }
                                } else if (previous.getGroupIdForUse() != obj.getGroupIdForUse()) {
                                    obj.localGroupId = Utilities.random.nextLong();
                                    groupedMessages = null;
                                }
                            }
                        }
                        if (groupedMessages == null) {
                            groupedMessages = new MessageObject.GroupedMessages();
                            groupedMessages.groupId = obj.getGroupId();
                            groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                        } else if (newGroups == null || newGroups.indexOfKey(obj.getGroupId()) < 0) {
                            if (changedGroups == null) {
                                changedGroups = new LongSparseArray<>();
                            }
                            changedGroups.put(obj.getGroupId(), groupedMessages);
                        }
                        if (newGroups == null) {
                            newGroups = new LongSparseArray<>();
                        }
                        newGroups.put(groupedMessages.groupId, groupedMessages);
                        if (load_type == 1) {
                            groupedMessages.messages.add(obj);
                        } else {
                            groupedMessages.messages.add(0, obj);
                        }
                    } else if (obj.getGroupIdForUse() != 0) {
                        obj.messageOwner.grouped_id = 0;
                        obj.localSentGroupId = 0;
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
                            if (obj.isOut() && !obj.messageOwner.from_scheduled || prevObj.getId() >= createUnreadMessageAfterId) {
                                prevObj = null;
                            }
                        } else {
                            prevObj = null;
                        }
                    } else {
                        if (createUnreadMessageAfterId != 0 && load_type != 1 && a - 1 >= 0) {
                            prevObj = messArr.get(a - 1);
                            if (obj.isOut() && !obj.messageOwner.from_scheduled || prevObj.getId() >= createUnreadMessageAfterId) {
                                prevObj = null;
                            }
                        } else {
                            prevObj = null;
                        }
                    }
                    if (load_type == 2 && messageId == first_unread_id) {
                        if (approximateHeightSum > AndroidUtilities.displaySize.y / 2 || !forwardEndReached[0]) {
                            TLRPC.Message dateMsg = new TLRPC.TL_message();
                            dateMsg.message = "";
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false);
                            dateObj.type = 6;
                            dateObj.contentType = 2;
                            messages.add(messages.size() - 1, dateObj);
                            unreadMessageObject = dateObj;
                            scrollToMessage = unreadMessageObject;
                            scrollToMessagePosition = -10000;
                            newRowsCount++;
                        }
                    } else if ((load_type == 3 || load_type == 4) && (startLoadFromMessageId < 0 && messageId == startLoadFromMessageId || startLoadFromMessageId > 0 && messageId > 0 && messageId <= startLoadFromMessageId)) {
                        removeSelectedMessageHighlight();
                        if (needSelectFromMessageId && messageId == startLoadFromMessageId) {
                            highlightMessageId = messageId;
                        }
                        if (showScrollToMessageError && messageId != startLoadFromMessageId) {
                            AlertsCreator.showSimpleToast(ChatActivity.this, LocaleController.getString("MessageNotFound", R.string.MessageNotFound));
                        }
                        scrollToMessage = obj;
                        startLoadFromMessageId = 0;
                        if (scrollToMessagePosition == -10000) {
                            scrollToMessagePosition = -9000;
                        }
                    }
                    if (load_type != 2 && unreadMessageObject == null && createUnreadMessageAfterId != 0 &&
                            (currentEncryptedChat == null && (!obj.isOut() || obj.messageOwner.from_scheduled) && messageId >= createUnreadMessageAfterId || currentEncryptedChat != null && (!obj.isOut() || obj.messageOwner.from_scheduled) && messageId <= createUnreadMessageAfterId) &&
                            (load_type == 1 || prevObj != null || prevObj == null && createUnreadLoading && a == messArr.size() - 1)) {
                        TLRPC.Message dateMsg = new TLRPC.TL_message();
                        dateMsg.message = "";
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false);
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
                        if (firstVisPos == 0) {
                            firstVisPos++;
                        }
                        View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);

                        int testingPosition = firstVisPos;
                        View testingView = firstVisView;
                        View goodView = null;
                        int goodPosition = -1;
                        if (testingView != null) {
                            while (goodView == null) {
                                if (testingView instanceof ChatMessageCell) {
                                    MessageObject messageObject = ((ChatMessageCell) testingView).getMessageObject();
                                    if (messageObject.hasValidGroupId()) {
                                        testingPosition++;
                                        testingView = chatLayoutManager.findViewByPosition(testingPosition);
                                        if (testingView == null) {
                                            goodPosition = firstVisPos;
                                            goodView = firstVisView;
                                            break;
                                        }
                                    } else {
                                        goodPosition = testingPosition;
                                        goodView = testingView;
                                        break;
                                    }
                                } else {
                                    goodPosition = testingPosition;
                                    goodView = testingView;
                                    break;
                                }
                            }
                        }

                        int top = ((goodView == null) ? 0 : chatListView.getMeasuredHeight() - goodView.getBottom() - chatListView.getPaddingBottom());
                        chatAdapter.notifyItemRangeInserted(1, newRowsCount);
                        if (goodPosition != RecyclerView.NO_POSITION) {
                            chatLayoutManager.scrollToPositionWithOffset(goodPosition + newRowsCount - rowsRemoved, top);
                        }
                    }
                    loadingForward = false;
                } else {
                    if (messArr.size() < count && load_type != 3 && load_type != 4) {
                        if (isCache) {
                            if (currentEncryptedChat != null || loadIndex == 1 && mergeDialogId != 0 && isEnd) {
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
                                    yOffset = -AndroidUtilities.dp(11);
                                    bottom = false;
                                } else {
                                    yOffset = scrollToMessagePosition;
                                }
                                if (!messages.isEmpty()) {
                                    if (chatAdapter.loadingUpRow != -1 && !messages.isEmpty() && (messages.get(messages.size() - 1) == scrollToMessage || messages.get(messages.size() - 2) == scrollToMessage)) {
                                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.loadingUpRow, yOffset, bottom);
                                    } else {
                                        chatLayoutManager.scrollToPositionWithOffset(chatAdapter.messagesStartRow + messages.indexOf(scrollToMessage), yOffset, bottom);
                                    }
                                }
                                chatListView.invalidate();
                                if (scrollToMessagePosition == -10000 || scrollToMessagePosition == -9000) {
                                    showPagedownButton(true, true);
                                    if (unread_to_load != 0) {
                                        if (pagedownButtonCounter != null) {
                                            pagedownButtonCounter.setVisibility(View.VISIBLE);
                                            if (prevSetUnreadCount != newUnreadMessageCount) {
                                                pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount = unread_to_load));
                                                prevSetUnreadCount = newUnreadMessageCount;
                                            }
                                        }
                                    }
                                }
                                scrollToMessagePosition = -10000;
                                scrollToMessage = null;
                            } else {
                                moveScrollToLastMessage();
                            }
                            if (loaded_mentions_count != 0) {
                                showMentionDownButton(true, true);
                                if (mentiondownButtonCounter != null) {
                                    mentiondownButtonCounter.setVisibility(View.VISIBLE);
                                    mentiondownButtonCounter.setText(String.format("%d", newMentionsCount = loaded_mentions_count));
                                }
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
                                int testingPosition = firstVisPos;
                                View testingView = firstVisView;
                                View goodView = null;
                                int goodPosition = -1;
                                if (testingView != null) {
                                    while (goodView == null) {
                                        if (testingView instanceof ChatMessageCell) {
                                            MessageObject messageObject = ((ChatMessageCell) testingView).getMessageObject();
                                            if (messageObject.hasValidGroupId()) {
                                                testingPosition++;
                                                testingView = chatLayoutManager.findViewByPosition(testingPosition);
                                                if (testingView == null) {
                                                    goodPosition = firstVisPos;
                                                    goodView = firstVisView;
                                                    break;
                                                }
                                            } else {
                                                goodPosition = testingPosition;
                                                goodView = testingView;
                                                break;
                                            }
                                        } else {
                                            goodPosition = testingPosition;
                                            goodView = testingView;
                                            break;
                                        }
                                    }
                                }
                                int top = ((goodView == null) ? 0 : chatListView.getMeasuredHeight() - goodView.getBottom() - chatListView.getPaddingBottom());
                                if (newRowsCount - (end ? 1 : 0) > 0) {
                                    int insertStart = chatAdapter.messagesEndRow;/* (chatAdapter.isBot ? 2 : 1) + (end ? 0 : 1); TODO check with bot*/
                                    chatAdapter.notifyItemChanged(chatAdapter.loadingUpRow);
                                    chatAdapter.notifyItemRangeInserted(insertStart, newRowsCount - (end ? 1 : 0));
                                }
                                if (goodPosition != -1) {
                                    chatLayoutManager.scrollToPositionWithOffset(goodPosition, top);
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
                if (newGroups != null) {
                    for (int a = 0; a < newGroups.size(); a++) {
                        MessageObject.GroupedMessages groupedMessages = newGroups.valueAt(a);
                        groupedMessages.calculate();
                        if (chatAdapter != null && changedGroups != null && changedGroups.indexOfKey(newGroups.keyAt(a)) >= 0) {
                            MessageObject messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                            int idx = messages.indexOf(messageObject);
                            if (idx >= 0) {
                                chatAdapter.notifyItemRangeChanged(idx + chatAdapter.messagesStartRow, groupedMessages.messages.size());
                            }
                        }
                    }
                }

                if (first && messages.size() > 0) {
                    first = false;
                }
                if (messages.isEmpty() && currentEncryptedChat == null && currentUser != null && currentUser.bot && botUser == null) {
                    botUser = "";
                    updateBottomOverlay();
                }

                if (newRowsCount == 0 && (mergeDialogId != 0 && loadIndex == 0 || currentEncryptedChat != null && !endReached[0])) {
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
                if (newRowsCount == 0 && mergeDialogId != 0 && loadIndex == 0) {
                    getNotificationCenter().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoad, NotificationCenter.dialogsNeedReload, NotificationCenter.scheduledMessagesUpdated,
                            NotificationCenter.closeChats, NotificationCenter.messagesDidLoad, NotificationCenter.botKeyboardDidLoad, NotificationCenter.userInfoDidLoad, NotificationCenter.needDeleteDialog/*, NotificationCenter.botInfoDidLoad*/});
                }
                if (showDateAfter) {
                    showFloatingDateView(false);
                }
                checkScrollForLoad(false);
                setItemAnimationsEnabled(true);
            }
        } else if (id == NotificationCenter.emojiDidLoad) {
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
            if (stickersListView != null) {
                stickersListView.invalidateViews();
            }
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateConnected) {
                checkAutoDownloadMessages(false);
            }
        } else if (id == NotificationCenter.chatOnlineCountDidLoad) {
            Integer chatId = (Integer) args[0];
            if (chatInfo == null || currentChat == null || currentChat.id != chatId) {
                return;
            }
            chatInfo.online_count = (Integer) args[1];
            if (avatarContainer != null) {
                avatarContainer.updateOnlineCount();
                avatarContainer.updateSubtitle();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int updateMask = (Integer) args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                if (currentChat != null) {
                    TLRPC.Chat chat = getMessagesController().getChat(currentChat.id);
                    if (chat != null) {
                        currentChat = chat;
                    }
                } else if (currentUser != null) {
                    TLRPC.User user = getMessagesController().getUser(currentUser.id);
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
            if ((updateMask & MessagesController.UPDATE_MASK_CHAT) != 0 && currentChat != null) {
                TLRPC.Chat chat = getMessagesController().getChat(currentChat.id);
                if (chat == null) {
                    return;
                }
                currentChat = chat;
                updateSubtitle = true;
                updateBottomOverlay();
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setDialogId(dialog_id, currentAccount);
                }
            }
            if ((updateMask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0) {
                if (bottomOverlayChatText2 != null && chatInfo != null && ChatObject.isChannel(currentChat) && !currentChat.megagroup && -chatInfo.linked_chat_id != 0) {
                    bottomOverlayChatText2.updateCounter();
                }
            }
            if (avatarContainer != null && updateSubtitle) {
                avatarContainer.updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PHONE) != 0) {
                updateTopPanel(true);
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                boolean scheduled = (Boolean) args[2];
                if (scheduled != inScheduleMode) {
                    if (!inScheduleMode && !isPaused && forwardingMessages == null) {
                        ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                        if (!arr.isEmpty() && arr.get(0).getId() < 0) {
                            openScheduledMessages();
                        }
                    }
                    return;
                }
                int currentUserId = getUserConfig().getClientUserId();
                boolean updateChat = false;
                boolean hasFromMe = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                if (currentEncryptedChat != null && arr.size() == 1) {
                    MessageObject obj = arr.get(0);

                    if (currentEncryptedChat != null && obj.isOut() && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction &&
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

                boolean notifiedSearch = false;
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject messageObject = arr.get(a);
                    if (messageObject.isOut()) {
                        if (!notifiedSearch) {
                            notifiedSearch = true;
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeSearchByActiveAction);
                        }
                        if (currentChat != null && currentChat.slowmode_enabled && messageObject.isSent() && !inScheduleMode) {
                            if (chatInfo != null) {
                                int date = messageObject.messageOwner.date + chatInfo.slowmode_seconds;
                                int currentTime = getConnectionsManager().getCurrentTime();
                                if (date > getConnectionsManager().getCurrentTime()) {
                                    chatInfo.slowmode_next_send_date = Math.max(chatInfo.slowmode_next_send_date, Math.min(currentTime + chatInfo.slowmode_seconds, date));
                                    if (chatActivityEnterView != null) {
                                        chatActivityEnterView.setSlowModeTimer(chatInfo.slowmode_next_send_date);
                                    }
                                }
                            }
                            getMessagesController().loadFullChat(currentChat.id, 0, true);
                        }
                    }
                    if (currentChat != null) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser && messageObject.messageOwner.action.user_id == currentUserId ||
                                messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser && messageObject.messageOwner.action.users.contains(currentUserId)) {
                            TLRPC.Chat newChat = getMessagesController().getChat(currentChat.id);
                            if (newChat != null) {
                                currentChat = newChat;
                                checkActionBarMenu();
                                updateBottomOverlay();
                                if (avatarContainer != null) {
                                    avatarContainer.updateSubtitle();
                                }
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
                    if (messageObject.messageOwner.reply_to_msg_id != 0 && messageObject.replyMessageObject == null) {
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
                }

                if (inScheduleMode && !arr.isEmpty()) {
                    replaceMessageObjects(arr, 0, true);
                }

                boolean reloadMegagroup = false;
                if (!forwardEndReached[0]) {
                    int currentMaxDate = Integer.MIN_VALUE;
                    int currentMinMsgId = Integer.MIN_VALUE;
                    if (currentEncryptedChat != null) {
                        currentMinMsgId = Integer.MAX_VALUE;
                    }

                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject obj = arr.get(a);
                        int messageId = obj.getId();
                        if (!inScheduleMode && currentUser != null && (currentUser.bot && obj.isOut() || currentUser.id == currentUserId)) {
                            obj.setIsRead();
                        }
                        TLRPC.MessageAction action = obj.messageOwner.action;
                        if (avatarContainer != null && currentEncryptedChat != null && action instanceof TLRPC.TL_messageEncryptedAction && action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                            avatarContainer.setTime(((TLRPC.TL_decryptedMessageActionSetMessageTTL) action.encryptedAction).ttl_seconds);
                        }
                        if (action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                            migrateToNewChat(obj);
                            return;
                        } else if (currentChat != null && currentChat.megagroup && (action instanceof TLRPC.TL_messageActionChatAddUser || action instanceof TLRPC.TL_messageActionChatDeleteUser)) {
                            reloadMegagroup = true;
                        }
                        if (a == 0 && obj.messageOwner.id < 0 && obj.type == MessageObject.TYPE_ROUND_VIDEO && !inScheduleMode) {
                            needAnimateToMessage = obj;
                        }
                        if (obj.isOut() && obj.isSending()) {
                            scrollToLastMessage(false);
                            return;
                        }
                        if (obj.type < 0 || messagesDict[0].indexOfKey(messageId) >= 0) {
                            continue;
                        }
                        if (currentChat != null && currentChat.creator && (action instanceof TLRPC.TL_messageActionChatCreate || action instanceof TLRPC.TL_messageActionChatEditPhoto && messages.size() < 4)) {
                            continue;
                        }
                        if (action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                            continue;
                        }
                        addToPolls(obj, null);
                        obj.checkLayout();
                        currentMaxDate = Math.max(currentMaxDate, obj.messageOwner.date);
                        if (messageId > 0) {
                            currentMinMsgId = Math.max(messageId, currentMinMsgId);
                            last_message_id = Math.max(last_message_id, messageId);
                        } else if (currentEncryptedChat != null) {
                            currentMinMsgId = Math.min(messageId, currentMinMsgId);
                            last_message_id = Math.min(last_message_id, messageId);
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
                        if (prevSetUnreadCount != newUnreadMessageCount) {
                            prevSetUnreadCount = newUnreadMessageCount;
                            pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
                        }
                    }
                    if (newMentionsCount != 0 && mentiondownButtonCounter != null) {
                        mentiondownButtonCounter.setVisibility(View.VISIBLE);
                        mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                        showMentionDownButton(true, true);
                    }

                    updateVisibleRows();
                } else {
                    LongSparseArray<MessageObject.GroupedMessages> newGroups = null;
                    HashMap<String, ArrayList<MessageObject>> webpagesToReload = null;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("received new messages " + arr.size() + " in dialog " + dialog_id);
                    }
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject obj = arr.get(a);
                        if (obj.scheduled != inScheduleMode) {
                            continue;
                        }
                        int placeToPaste = -1;
                        int messageId = obj.getId();
                        if (inScheduleMode && messagesDict[0].indexOfKey(messageId) >= 0) {
                            MessageObject removed = messagesDict[0].get(messageId);
                            messagesDict[0].remove(messageId);
                            if (removed != null) {
                                int index = messages.indexOf(removed);
                                messages.remove(index);
                                ArrayList<MessageObject> dayArr = messagesByDays.get(removed.dateKey);
                                dayArr.remove(removed);
                                if (dayArr.isEmpty()) {
                                    messagesByDays.remove(removed.dateKey);
                                    if (index >= 0 && index < messages.size()) {
                                        messages.remove(index);
                                    }
                                }
                                if (chatAdapter != null) {
                                    chatAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                        if (isSecretChat()) {
                            checkSecretMessageForLocation(obj);
                        }
                        if (!inScheduleMode && currentUser != null && (currentUser.bot && obj.isOut() || currentUser.id == currentUserId)) {
                            obj.setIsRead();
                        }
                        TLRPC.MessageAction action = obj.messageOwner.action;
                        if (avatarContainer != null && currentEncryptedChat != null && action instanceof TLRPC.TL_messageEncryptedAction && action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                            avatarContainer.setTime(((TLRPC.TL_decryptedMessageActionSetMessageTTL) action.encryptedAction).ttl_seconds);
                        }
                        if (obj.type < 0 || messagesDict[0].indexOfKey(messageId) >= 0) {
                            continue;
                        }
                        if (currentChat != null && currentChat.creator && (action instanceof TLRPC.TL_messageActionChatCreate || action instanceof TLRPC.TL_messageActionChatEditPhoto && messages.size() < 4)) {
                            continue;
                        }
                        if (action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                            continue;
                        }
                        addToPolls(obj, null);
                        if (a == 0 && obj.messageOwner.id < 0 && obj.type == MessageObject.TYPE_ROUND_VIDEO && !inScheduleMode) {
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
                                newGroups = new LongSparseArray<>();
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
                            if (!obj.scheduled && obj.messageOwner.id < 0 || messages.isEmpty()) {
                                placeToPaste = 0;
                            } else {
                                int size = messages.size();
                                for (int b = 0; b < size; b++) {
                                    MessageObject lastMessage = messages.get(b);
                                    if (lastMessage.type >= 0 && lastMessage.messageOwner.date > 0) {
                                        if (!inScheduleMode && lastMessage.messageOwner.id > 0 && obj.messageOwner.id > 0 && lastMessage.messageOwner.id < obj.messageOwner.id || lastMessage.messageOwner.date <= obj.messageOwner.date) {
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
                        if (action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                            migrateToNewChat(obj);
                            if (newGroups != null) {
                                for (int b = 0; b < newGroups.size(); b++) {
                                    newGroups.valueAt(b).calculate();
                                }
                            }
                            return;
                        } else if (currentChat != null && currentChat.megagroup && (action instanceof TLRPC.TL_messageActionChatAddUser || action instanceof TLRPC.TL_messageActionChatDeleteUser)) {
                            reloadMegagroup = true;
                        }
                        if (minDate[0] == 0 || obj.messageOwner.date < minDate[0]) {
                            minDate[0] = obj.messageOwner.date;
                        }

                        if (obj.isOut() && !obj.messageOwner.from_scheduled) {
                            removeUnreadPlane(true);
                            hasFromMe = true;
                        }

                        if (messageId > 0) {
                            maxMessageId[0] = Math.min(messageId, maxMessageId[0]);
                            minMessageId[0] = Math.max(messageId, minMessageId[0]);
                        } else if (currentEncryptedChat != null) {
                            maxMessageId[0] = Math.max(messageId, maxMessageId[0]);
                            minMessageId[0] = Math.min(messageId, minMessageId[0]);
                        }
                        maxDate[0] = Math.max(maxDate[0], obj.messageOwner.date);
                        messagesDict[0].put(messageId, obj);
                        ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);
                        if (placeToPaste > messages.size()) {
                            placeToPaste = messages.size();
                        }
                        if (dayArray == null) {
                            dayArray = new ArrayList<>();
                            messagesByDays.put(obj.dateKey, dayArray);
                            TLRPC.Message dateMsg = new TLRPC.TL_message();
                            if (inScheduleMode) {
                                dateMsg.message = LocaleController.formatString("MessageScheduledOn", R.string.MessageScheduledOn, LocaleController.formatDateChat(obj.messageOwner.date, true));
                            } else {
                                dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                            }
                            dateMsg.id = 0;
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(((long) obj.messageOwner.date) * 1000);
                            calendar.set(Calendar.HOUR_OF_DAY, 0);
                            calendar.set(Calendar.MINUTE, 0);
                            dateMsg.date = (int) (calendar.getTimeInMillis() / 1000);
                            MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false);
                            dateObj.type = 10;
                            dateObj.contentType = 1;
                            dateObj.isDateObject = true;
                            messages.add(placeToPaste, dateObj);
                            if (chatAdapter != null) {
                                chatAdapter.notifyItemInserted(placeToPaste);
                            }
                        }
                        if (!obj.isOut() || obj.messageOwner.from_scheduled) {
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
                                    MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false);
                                    dateObj.type = 6;
                                    dateObj.contentType = 2;
                                    messages.add(0, dateObj);
                                    if (chatAdapter != null) {
                                        chatAdapter.notifyItemInserted(0);
                                    }
                                    unreadMessageObject = dateObj;
                                    scrollToMessage = unreadMessageObject;
                                    scrollToMessagePosition = -10000;
                                    scrollToTopUnReadOnResume = true;
                                }
                            }
                        }

                        dayArray.add(0, obj);

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
                        getMessagesController().reloadWebPages(dialog_id, webpagesToReload, inScheduleMode);
                    }
                    if (newGroups != null) {
                        for (int a = 0; a < newGroups.size(); a++) {
                            MessageObject.GroupedMessages groupedMessages = newGroups.valueAt(a);
                            int oldCount = groupedMessages.posArray.size();
                            groupedMessages.calculate();
                            int newCount = groupedMessages.posArray.size();
                            if (newCount - oldCount > 0 && chatAdapter != null) {
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
                    if (chatAdapter == null) {
                        scrollToTopOnResume = true;
                    }

                    if (chatListView != null && chatAdapter != null) {
                        int lastVisible = chatLayoutManager.findFirstVisibleItemPosition();
                        if (lastVisible == RecyclerView.NO_POSITION) {
                            lastVisible = 0;
                        }
                        View child = chatLayoutManager.findViewByPosition(lastVisible);
                        int diff;
                        if (child != null) {
                            diff = child.getBottom() - chatListView.getMeasuredHeight();
                        } else {
                            diff = 0;
                        }
                        if (lastVisible == 0 && diff <= AndroidUtilities.dp(5) || hasFromMe) {
                            newUnreadMessageCount = 0;
                            if (!firstLoading && !inScheduleMode) {
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
                                if (prevSetUnreadCount != newUnreadMessageCount) {
                                    prevSetUnreadCount = newUnreadMessageCount;
                                    pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
                                }
                            }
                            showPagedownButton(true, true);
                        }
                        if (newMentionsCount != 0 && mentiondownButtonCounter != null) {
                            mentiondownButtonCounter.setVisibility(View.VISIBLE);
                            mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                            showMentionDownButton(true, true);
                        }
                    } else {
                        scrollToTopOnResume = true;
                    }
                }
                if (inScheduleMode && !arr.isEmpty()) {
                    int mid = arr.get(0).getId();
                    if (mid < 0) {
                        scrollToMessageId(mid, 0, false, 0, true);
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
                    getMessagesController().loadFullChat(currentChat.id, 0, true);
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
            if (inScheduleMode) {
                return;
            }
            SparseLongArray inbox = (SparseLongArray) args[0];
            SparseLongArray outbox = (SparseLongArray) args[1];
            boolean updated = false;
            if (inbox != null) {
                if (bottomOverlayChatText2 != null && chatInfo != null && ChatObject.isChannel(currentChat) && !currentChat.megagroup && -chatInfo.linked_chat_id != 0) {
                    bottomOverlayChatText2.updateCounter();
                }
                for (int b = 0, size = inbox.size(); b < size; b++) {
                    int key = inbox.keyAt(b);
                    long messageId = inbox.get(key);
                    if (key != dialog_id) {
                        continue;
                    }
                    for (int a = 0, size2 = messages.size(); a < size2; a++) {
                        MessageObject obj = messages.get(a);
                        if (!obj.isOut() && obj.getId() > 0 && obj.getId() <= (int) messageId) {
                            if (!obj.isUnread()) {
                                break;
                            }
                            obj.setIsRead();
                            updated = true;
                            newUnreadMessageCount--;
                        }
                    }
                    removeUnreadPlane(false);
                    break;
                }
            }
            if (updated) {
                if (newUnreadMessageCount < 0) {
                    newUnreadMessageCount = 0;
                }
                if (pagedownButtonCounter != null) {
                    if (prevSetUnreadCount != newUnreadMessageCount) {
                        prevSetUnreadCount = newUnreadMessageCount;
                        pagedownButtonCounter.setText(String.format("%d", newUnreadMessageCount));
                    }
                    if (newUnreadMessageCount <= 0) {
                        if (pagedownButtonCounter.getVisibility() != View.INVISIBLE) {
                            pagedownButtonCounter.setVisibility(View.INVISIBLE);
                        }
                    } else {
                        if (pagedownButtonCounter.getVisibility() != View.VISIBLE) {
                            pagedownButtonCounter.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
            if (outbox != null) {
                for (int b = 0, size = outbox.size(); b < size; b++) {
                    int key = outbox.keyAt(b);
                    int messageId = (int) ((long) outbox.get(key));
                    if (key != dialog_id) {
                        continue;
                    }
                    for (int a = 0, size2 = messages.size(); a < size2; a++) {
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
                if (chatInfo != null && chatInfo.pinned_msg_id == mid) {
                    pinnedMessageObject = null;
                    chatInfo.pinned_msg_id = 0;
                    getMessagesStorage().updateChatPinnedMessage(chatInfo.id, 0);
                    updatePinnedMessageView(true);
                } else if (userInfo != null && userInfo.pinned_msg_id == mid) {
                    pinnedMessageObject = null;
                    userInfo.pinned_msg_id = 0;
                    getMessagesStorage().updateUserPinnedMessage(chatInfo.id, 0);
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
                    getMessagesController().loadMessages(dialog_id, 30, 0, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
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
            showPagedownButton(false, true);
            showMentionDownButton(false, true);
            if (updated && chatAdapter != null) {
                removeUnreadPlane(true);
                chatAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled != inScheduleMode) {
                return;
            }
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
            LongSparseArray<MessageObject.GroupedMessages> newGroups = null;
            int size = markAsDeletedMessages.size();
            boolean updatedSelected = false;
            boolean updatedSelectedLast = false;
            boolean updateScheduled = false;
            for (int a = 0; a < size; a++) {
                Integer ids = markAsDeletedMessages.get(a);
                MessageObject obj = messagesDict[loadIndex].get(ids);
                if (loadIndex == 0 && (chatInfo != null && chatInfo.pinned_msg_id == ids || userInfo != null && userInfo.pinned_msg_id == ids)) {
                    pinnedMessageObject = null;
                    if (chatInfo != null) {
                        chatInfo.pinned_msg_id = 0;
                    } else if (userInfo != null) {
                        userInfo.pinned_msg_id = 0;
                    }
                    getMessagesStorage().updateChatPinnedMessage(channelId, 0);
                    updatePinnedMessageView(true);
                }
                if (obj != null) {
                    if (editingMessageObject == obj) {
                        hideFieldPanel(true);
                    }
                    int index = messages.indexOf(obj);
                    if (index != -1) {
                        if (obj.scheduled) {
                            scheduledMessagesCount--;
                            updateScheduled = true;
                        }
                        removeUnreadPlane(false);
                        if (selectedMessagesIds[loadIndex].indexOfKey(ids) >= 0) {
                            updatedSelected = true;
                            addToSelectedMessages(obj, false, updatedSelectedLast = (a == size - 1));
                        }
                        MessageObject removed = messages.remove(index);
                        if (removed.getGroupId() != 0) {
                            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(removed.getGroupId());
                            if (groupedMessages != null) {
                                if (newGroups == null) {
                                    newGroups = new LongSparseArray<>();
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
            if (updatedSelected) {
                if (!updatedSelectedLast) {
                    addToSelectedMessages(null, false, true);
                }
                updateActionModeTitle();
            }
            if (newGroups != null) {
                for (int a = 0; a < newGroups.size(); a++) {
                    MessageObject.GroupedMessages groupedMessages = newGroups.valueAt(a);
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
                    getMessagesController().loadMessages(dialog_id, 30, 0, 0, !cacheEndReached[0], minDate[0], classGuid, 0, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
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
                showPagedownButton(false, true);
                showMentionDownButton(false, true);
            }
            if (chatAdapter != null) {
                if (updated) {
                    int count = chatListView.getChildCount();
                    int position = -1;
                    int bottom = 0;
                    for (int a = 0; a < count; a++) {
                        View child = chatListView.getChildAt(a);
                        MessageObject messageObject = null;
                        if (child instanceof ChatMessageCell) {
                            messageObject = ((ChatMessageCell) child).getMessageObject();
                        } else if (child instanceof ChatActionCell) {
                            messageObject = ((ChatActionCell) child).getMessageObject();
                        }
                        if (messageObject != null) {
                            int idx = messages.indexOf(messageObject);
                            if (idx < 0) {
                                continue;
                            }
                            position = chatAdapter.messagesStartRow + idx;
                            bottom = child.getBottom();
                            break;
                        }
                    }
                    chatAdapter.notifyDataSetChanged();
                    if (position != -1) {
                        chatLayoutManager.scrollToPositionWithOffset(position, chatListView.getMeasuredHeight() - bottom - chatListView.getPaddingBottom());
                    }
                } else {
                    first_unread_id = 0;
                    last_message_id = 0;
                    createUnreadMessageAfterId = 0;
                    removeMessageObject(unreadMessageObject);
                    unreadMessageObject = null;
                    if (pagedownButtonCounter != null) {
                        pagedownButtonCounter.setVisibility(View.INVISIBLE);
                    }
                }
            }
            if (updateScheduled) {
                updateScheduledInterface(true);
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Boolean scheduled = (Boolean) args[6];
            if (scheduled != inScheduleMode) {
                return;
            }
            Integer msgId = (Integer) args[0];
            MessageObject obj = messagesDict[0].get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer) args[1];
                if (!newMsgId.equals(msgId) && messagesDict[0].indexOfKey(newMsgId) >= 0) {
                    MessageObject removed = messagesDict[0].get(msgId);
                    messagesDict[0].remove(msgId);
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
                Long grouped_id;
                if (args.length >= 4) {
                    grouped_id = (Long) args[4];
                } else {
                    grouped_id = 0L;
                }
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
                        obj.localSentGroupId = obj.messageOwner.grouped_id;
                        obj.messageOwner.grouped_id = grouped_id;
                    }
                    TLRPC.MessageFwdHeader fwdHeader = obj.messageOwner.fwd_from;
                    obj.messageOwner = newMsgObj;
                    if (fwdHeader != null && newMsgObj.fwd_from != null && !TextUtils.isEmpty(newMsgObj.fwd_from.from_name)) {
                        obj.messageOwner.fwd_from = fwdHeader;
                    }
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
                if (args.length >= 6) {
                    obj.applyMediaExistanceFlags((Integer) args[5]);
                }
                addToPolls(obj, null);
                ArrayList<MessageObject> messArr = new ArrayList<>();
                messArr.add(obj);
                if (currentEncryptedChat == null) {
                    getMediaDataController().loadReplyMessagesForMessages(messArr, dialog_id, inScheduleMode);
                }
                if (chatAdapter != null) {
                    chatAdapter.updateRowWithMessageObject(obj, true);
                }
                if (chatLayoutManager != null) {
                    if (mediaUpdated && chatLayoutManager.findFirstVisibleItemPosition() == 0) {
                        moveScrollToLastMessage();
                    }
                }
                getNotificationsController().playOutChatSound();
            }
        } else if (id == NotificationCenter.messageReceivedByAck) {
            Integer msgId = (Integer) args[0];
            MessageObject obj = messagesDict[0].get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                if (chatAdapter != null) {
                    chatAdapter.updateRowWithMessageObject(obj, true);
                }
            }
        } else if (id == NotificationCenter.messageSendError) {
            Integer msgId = (Integer) args[0];
            MessageObject obj = messagesDict[0].get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
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
                            getMessagesController().loadChannelParticipants(currentChat.id);
                        }
                    }
                    if (chatFull.participants == null && chatInfo != null) {
                        chatFull.participants = chatInfo.participants;
                    }
                }
                chatInfo = chatFull;
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setChatInfo(chatInfo);
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.setChatInfo(chatInfo);
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
                if (chatInfo instanceof TLRPC.TL_chatFull) {
                    hasBotsCommands = false;
                    botInfo.clear();
                    botsCount = 0;
                    URLSpanBotCommand.enabled = false;
                    for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                        TLRPC.ChatParticipant participant = chatInfo.participants.participants.get(a);
                        TLRPC.User user = getMessagesController().getUser(participant.user_id);
                        if (user != null && user.bot) {
                            URLSpanBotCommand.enabled = true;
                            botsCount++;
                            getMediaDataController().loadBotInfo(user.id, true, classGuid);
                        }
                    }
                    if (chatListView != null) {
                        chatListView.invalidateViews();
                    }
                } else if (chatInfo instanceof TLRPC.TL_channelFull) {
                    hasBotsCommands = false;
                    botInfo.clear();
                    botsCount = 0;
                    URLSpanBotCommand.enabled = !chatInfo.bot_info.isEmpty() && currentChat != null && currentChat.megagroup;
                    botsCount = chatInfo.bot_info.size();
                    for (int a = 0; a < chatInfo.bot_info.size(); a++) {
                        TLRPC.BotInfo bot = chatInfo.bot_info.get(a);
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
                    if (bottomOverlayChatText2 != null && ChatObject.isChannel(currentChat) && !currentChat.megagroup && -chatInfo.linked_chat_id != 0) {
                        bottomOverlayChatText2.updateCounter();
                    }
                }
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
                }
                if (mentionsAdapter != null) {
                    mentionsAdapter.setBotsCount(botsCount);
                }
                if (!inScheduleMode && ChatObject.isChannel(currentChat) && mergeDialogId == 0 && chatInfo.migrated_from_chat_id != 0) {
                    mergeDialogId = -chatInfo.migrated_from_chat_id;
                    maxMessageId[1] = chatInfo.migrated_from_max_id;
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    }
                    if (mergeDialogId != 0 && endReached[0]) {
                        checkScrollForLoad(false);
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
        } else if (id == NotificationCenter.contactsDidLoad) {
            updateTopPanel(true);
            if (avatarContainer != null) {
                avatarContainer.updateSubtitle();
            }
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateTopPanel(true);
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
                clearHistory((Boolean) args[1]);
            }
        } else if (id == NotificationCenter.screenshotTook) {
            updateInformationForScreenshotDetector();
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            if (currentUser != null) {
                boolean oldValue = userBlocked;
                userBlocked = getMessagesController().blockedUsers.indexOfKey(currentUser.id) >= 0;
                if (oldValue != userBlocked) {
                    updateBottomOverlay();
                }
            }
        } else if (id == NotificationCenter.fileNewChunkAvailable) {
            MessageObject messageObject = (MessageObject) args[0];
            long finalSize = (Long) args[3];
            if (finalSize != 0 && dialog_id == messageObject.getDialogId()) {
                MessageObject currentObject = messagesDict[0].get(messageObject.getId());
                if (currentObject != null && currentObject.messageOwner.media.document != null) {
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
        } else if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.eventId != 0) {
                return;
            }
            sendSecretMessageRead(messageObject);

            if ((messageObject.isRoundVideo() || messageObject.isVideo()) && fragmentView != null && fragmentView.getParent() != null) {
                MediaController.getInstance().setTextureView(createTextureView(true), aspectRatioFrameLayout, videoPlayerContainer, true);
                updateTextureViewPosition(true);
            }

            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null) {
                            boolean isVideo = messageObject1.isVideo();
                            if (messageObject1.isRoundVideo() || isVideo) {
                                cell.checkVideoPlayback(false);
                                if (!MediaController.getInstance().isPlayingMessage(messageObject1)) {
                                    if (isVideo && !MediaController.getInstance().isGoingToShowMessageObject(messageObject1)) {
                                        AnimatedFileDrawable animation = cell.getPhotoImage().getAnimation();
                                        if (animation != null) {
                                            animation.start();
                                        }
                                    }
                                    if (messageObject1.audioProgress != 0) {
                                        messageObject1.resetPlayingProgress();
                                        cell.invalidate();
                                    }
                                } else if (isVideo) {
                                    cell.updateButtonState(false, true, false);
                                }
                            } else if (messageObject1.isVoice() || messageObject1.isMusic()) {
                                cell.updateButtonState(false, true, false);
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
                            cell.updateButtonState(false, true);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingGoingToStop) {
            boolean injecting = (Boolean) args[1];
            if (injecting) {
                contentView.removeView(videoPlayerContainer);
                videoPlayerContainer = null;
                videoTextureView = null;
                aspectRatioFrameLayout = null;
            } else {
                if (chatListView != null && videoPlayerContainer != null && videoPlayerContainer.getTag() != null) {
                    MessageObject messageObject = (MessageObject) args[0];
                    int count = chatListView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = chatListView.getChildAt(a);
                        if (view instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) view;
                            MessageObject messageObject1 = cell.getMessageObject();
                            if (messageObject == messageObject1) {
                                AnimatedFileDrawable animation = cell.getPhotoImage().getAnimation();
                                if (animation != null) {
                                    Bitmap bitmap = animation.getAnimatedBitmap();
                                    if (bitmap != null) {
                                        try {
                                            Bitmap src = videoTextureView.getBitmap(bitmap.getWidth(), bitmap.getHeight());
                                            Canvas canvas = new Canvas(bitmap);
                                            canvas.drawBitmap(src, 0, 0, null);
                                            src.recycle();
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                    }
                                    animation.seekTo(messageObject.audioProgressMs, !getFileLoader().isLoadingVideo(messageObject.getDocument(), true));
                                }
                                break;
                            }
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
                                cell.updateButtonState(false, true, false);
                            } else if (messageObject.isVideo()) {
                                cell.updateButtonState(false, true, false);
                                if (!MediaController.getInstance().isPlayingMessage(messageObject) && !MediaController.getInstance().isGoingToShowMessageObject(messageObject)) {
                                    AnimatedFileDrawable animation = cell.getPhotoImage().getAnimation();
                                    if (animation != null) {
                                        animation.start();
                                    }
                                }
                            } else if (messageObject.isRoundVideo()) {
                                if (!MediaController.getInstance().isPlayingMessage(messageObject)) {
                                    cell.checkVideoPlayback(true);
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
                            cell.updateButtonState(false, true);
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
                                playing.audioPlayerDuration = player.audioPlayerDuration;
                                cell.updatePlayingMessageProgress();
                                if (drawLaterRoundProgressCell == cell) {
                                    fragmentView.invalidate();
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.didUpdatePollResults) {
            long pollId = (Long) args[0];
            ArrayList<MessageObject> arrayList = polls.get(pollId);
            if (arrayList != null) {
                TLRPC.TL_poll poll = (TLRPC.TL_poll) args[1];
                TLRPC.TL_pollResults results = (TLRPC.TL_pollResults) args[2];
                for (int a = 0, N = arrayList.size(); a < N; a++) {
                    MessageObject object = arrayList.get(a);
                    TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                    if (poll != null) {
                        media.poll = poll;
                    }
                    MessageObject.updatePollResults(media, results);
                    if (chatAdapter != null) {
                        chatAdapter.updateRowWithMessageObject(object, true);
                    }
                }
            }
        } else if (id == NotificationCenter.didUpdateReactions) {
            long did = (Long) args[0];
            if (did == dialog_id || did == mergeDialogId) {
                int msgId = (Integer) args[1];
                MessageObject messageObject = messagesDict[did == dialog_id ? 0 : 1].get(msgId);
                if (messageObject != null) {
                    MessageObject.updateReactions(messageObject.messageOwner, (TLRPC.TL_messageReactions) args[2]);
                    messageObject.measureInlineBotButtons();
                    chatAdapter.updateRowWithMessageObject(messageObject, true);
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
                            if (groupedMessages.messages.isEmpty()) {
                                groupedMessagesMap.remove(groupedMessages.groupId);
                            } else {
                                if (messageObject == null) {
                                    messageObject = groupedMessages.messages.get(groupedMessages.messages.size() - 1);
                                }
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
                    if (chatAdapter != null) {
                        chatAdapter.updateRowWithMessageObject(existMessageObject, false);
                    }
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
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            replaceMessageObjects(messageObjects, loadIndex, false);
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateTitleIcons();
            if (ChatObject.isChannel(currentChat)) {
                updateBottomOverlay();
            }
        } else if (id == NotificationCenter.replyMessagesDidLoad) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.pinnedMessageDidLoad) {
            MessageObject message = (MessageObject) args[0];
            if (message.getDialogId() == dialog_id && (chatInfo != null && chatInfo.pinned_msg_id == message.getId() || userInfo != null && userInfo.pinned_msg_id == message.getId())) {
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
                /*if (chatLayoutManager != null && chatLayoutManager.findFirstVisibleItemPosition() == 0) {
                    moveScrollToLastMessage();
                }*/
            }
        } else if (id == NotificationCenter.didReceivedWebpagesInUpdates) {
            if (foundWebPage != null) {
                LongSparseArray<TLRPC.WebPage> hashMap = (LongSparseArray<TLRPC.WebPage>) args[0];
                for (int a = 0; a < hashMap.size(); a++) {
                    TLRPC.WebPage webPage = hashMap.valueAt(a);
                    if (webPage.id == foundWebPage.id) {
                        showFieldPanelForWebPage(!(webPage instanceof TLRPC.TL_webPageEmpty), webPage, false);
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
                            showMentionDownButton(false, true);
                        } else {
                            if (mentiondownButtonCounter != null) {
                                mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                            }
                        }
                    }
                }
            }
            if (updated) {
                updateVisibleRows();
            }
        } else if (id == NotificationCenter.botInfoDidLoad) {
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
        } else if (id == NotificationCenter.botKeyboardDidLoad) {
            if (dialog_id == (Long) args[1]) {
                TLRPC.Message message = (TLRPC.Message) args[0];
                if (message != null && !userBlocked) {
                    botButtons = new MessageObject(currentAccount, message, false);
                    checkBotKeyboard();
                } else {
                    botButtons = null;
                    if (chatActivityEnterView != null) {
                        if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                            botReplyButtons = null;
                            hideFieldPanel(true);
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
                } else {
                    updateVisibleRows();
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
        } else if (id == NotificationCenter.peerSettingsDidLoad) {
            long did = (Long) args[0];
            if (did == dialog_id || currentUser != null && currentUser.id == did) {
                updateTopPanel(!paused);
            }
        } else if (id == NotificationCenter.newDraftReceived) {
            long did = (Long) args[0];
            if (did == dialog_id) {
                applyDraftMaybe(true);
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            Integer uid = (Integer) args[0];
            if (currentUser != null && currentUser.id == uid) {
                userInfo = (TLRPC.UserFull) args[1];
                if (headerItem != null) {
                    if (userInfo.phone_calls_available) {
                        headerItem.showSubItem(call);
                    } else {
                        headerItem.hideSubItem(call);
                    }
                }
                if (args[2] instanceof MessageObject) {
                    pinnedMessageObject = (MessageObject) args[2];
                    updatePinnedMessageView(false);
                } else {
                    updatePinnedMessageView(true);
                }
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (fragmentView != null) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
                progressView2.getBackground().setColorFilter(Theme.colorFilter);
                if (emptyView != null) {
                    emptyView.getBackground().setColorFilter(Theme.colorFilter);
                }
                if (bigEmptyView != null) {
                    bigEmptyView.getBackground().setColorFilter(Theme.colorFilter);
                }
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.goingToPreviewTheme) {
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
                        showMentionDownButton(false, true);
                    } else {
                        mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                    }
                }
            }
        } else if (id == NotificationCenter.audioRecordTooShort) {
            int guid = (Integer) args[0];
            if (guid != classGuid) {
                return;
            }
            showVoiceHint(false, (Boolean) args[1]);
        } else if (id == NotificationCenter.videoLoadingStateChanged) {
            if (chatListView != null) {
                String fileName = (String) args[0];
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = chatListView.getChildAt(a);
                    if (!(child instanceof ChatMessageCell)) {
                        continue;
                    }
                    ChatMessageCell cell = (ChatMessageCell) child;
                    TLRPC.Document document = cell.getStreamingMedia();
                    if (document == null) {
                        continue;
                    }
                    if (FileLoader.getAttachFileName(document).equals(fileName)) {
                        cell.updateButtonState(false, true, false);
                    }
                }
            }
        } else if (id == NotificationCenter.scheduledMessagesUpdated) {
            long did = (Long) args[0];
            if (dialog_id == did) {
                scheduledMessagesCount = (Integer) args[1];
                updateScheduledInterface(openAnimationEnded);
            }
        }
    }

    private void checkSecretMessageForLocation(MessageObject messageObject) {
        if (messageObject.type != 4 || locationAlertShown || SharedConfig.isSecretMapPreviewSet()) {
            return;
        }
        locationAlertShown = true;
        AlertsCreator.showSecretLocationAlert(getParentActivity(), currentAccount, () -> {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    MessageObject message = cell.getMessageObject();
                    if (message.type == 4) {
                        cell.forceResetMessageObject();
                    }
                }
            }
        }, true);
    }

    private void clearHistory(boolean overwrite) {
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
        hideActionMode();
        updatePinnedMessageView(true);

        if (botButtons != null) {
            botButtons = null;
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setButtons(null, false);
            }
        }
        if (overwrite) {
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
            if (startLoadFromMessageIdSaved != 0) {
                startLoadFromMessageId = startLoadFromMessageIdSaved;
                startLoadFromMessageIdSaved = 0;
                getMessagesController().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, startLoadFromMessageId, 0, true, 0, classGuid, 3, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
            } else {
                getMessagesController().loadMessages(dialog_id, AndroidUtilities.isTablet() ? 30 : 20, 0, 0, true, 0, classGuid, 2, 0, ChatObject.isChannel(currentChat), inScheduleMode, lastLoadIndex++);
            }
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

    public boolean processSwitchButton(TLRPC.TL_keyboardButtonSwitchInline button) {
        if (inlineReturn == 0 || button.same_peer || parentLayout == null) {
            return false;
        }
        String query = "@" + currentUser.username + " " + button.query;
        if (inlineReturn == dialog_id) {
            inlineReturn = 0;
            chatActivityEnterView.setFieldText(query);
        } else {
            getMediaDataController().saveDraft(inlineReturn, query, null, null, false);
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
                        getNotificationCenter().removeObserver(lastFragment, NotificationCenter.closeChats);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats);*/
                    presentFragment(new ChatActivity(bundle), true);
                }
            }
        }
        return true;
    }

    private void replaceMessageObjects(ArrayList<MessageObject> messageObjects, int loadIndex, boolean remove) {
        LongSparseArray<MessageObject.GroupedMessages> newGroups = null;
        for (int a = 0; a < messageObjects.size(); a++) {
            MessageObject messageObject = messageObjects.get(a);
            MessageObject old = messagesDict[loadIndex].get(messageObject.getId());
            if (pinnedMessageObject != null && pinnedMessageObject.getId() == messageObject.getId()) {
                pinnedMessageObject = messageObject;
                updatePinnedMessageView(true);
            }
            if (old == null || remove && old.messageOwner.date != messageObject.messageOwner.date) {
                continue;
            }
            if (remove) {
                messageObjects.remove(a);
                a--;
            }

            addToPolls(messageObject, old);
            if (messageObject.type >= 0) {
                if (old.replyMessageObject != null) {
                    messageObject.replyMessageObject = old.replyMessageObject;
                    if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                        messageObject.generateGameMessageText(null);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                        messageObject.generatePaymentSentMessageText(null);
                    }
                }
                if (!old.isEditing()) {
                    if (old.getFileName().equals(messageObject.getFileName())) {
                        messageObject.messageOwner.attachPath = old.messageOwner.attachPath;
                        messageObject.attachPathExists = old.attachPathExists;
                        messageObject.mediaExists = old.mediaExists;
                    } else {
                        messageObject.checkMediaExistance();
                    }
                }
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
                                    newGroups = new LongSparseArray<>();
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
                                if (newGroups == null) {
                                    newGroups = new LongSparseArray<>();
                                }
                                newGroups.put(groupedMessages.groupId, groupedMessages);
                            }
                        }
                    }
                }
                if (messageObject.type >= 0) {
                    messages.set(index, messageObject);
                    if (chatAdapter != null) {
                        chatAdapter.updateRowAtPosition(chatAdapter.messagesStartRow + index);
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
            }
        }
        if (newGroups != null) {
            for (int b = 0; b < newGroups.size(); b++) {
                MessageObject.GroupedMessages groupedMessages = newGroups.valueAt(b);
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
    }

    private void migrateToNewChat(MessageObject obj) {
        if (parentLayout == null) {
            return;
        }
        final int channelId = obj.messageOwner.action.channel_id;
        final BaseFragment lastFragment = parentLayout.fragmentsStack.size() > 0 ? parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) : null;
        int index = parentLayout.fragmentsStack.indexOf(ChatActivity.this);

        final ActionBarLayout actionBarLayout = parentLayout;

        if (index > 0 && !(lastFragment instanceof ChatActivity) && !(lastFragment instanceof ProfileActivity) && currentChat.creator) {
            for (int a = index, N = actionBarLayout.fragmentsStack.size() - 1; a < N; a++) {
                BaseFragment fragment = actionBarLayout.fragmentsStack.get(a);
                if (fragment instanceof ChatActivity) {
                    final Bundle bundle = new Bundle();
                    bundle.putInt("chat_id", channelId);
                    actionBarLayout.addFragmentToStack(new ChatActivity(bundle), a);
                    fragment.removeSelfFromStack();
                } else if (fragment instanceof ProfileActivity) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", channelId);
                    actionBarLayout.addFragmentToStack(new ProfileActivity(args), a);
                    fragment.removeSelfFromStack();
                } else if (fragment instanceof ChatEditActivity) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", channelId);
                    actionBarLayout.addFragmentToStack(new ChatEditActivity(args), a);
                    fragment.removeSelfFromStack();
                } else if (fragment instanceof ChatUsersActivity) {
                    ChatUsersActivity usersActivity = (ChatUsersActivity) fragment;
                    if (!usersActivity.hasSelectType()) {
                        Bundle args = fragment.getArguments();
                        args.putInt("chat_id", channelId);
                        actionBarLayout.addFragmentToStack(new ChatUsersActivity(args), a);
                    }
                    fragment.removeSelfFromStack();
                }
            }
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                if (lastFragment != null) {
                    getNotificationCenter().removeObserver(lastFragment, NotificationCenter.closeChats);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                final Bundle bundle = new Bundle();
                bundle.putInt("chat_id", obj.messageOwner.action.channel_id);
                actionBarLayout.presentFragment(new ChatActivity(bundle), true);
            });
        }
        AndroidUtilities.runOnUIThread(() -> getMessagesController().loadFullChat(channelId, 0, true), 1000);
    }

    private void addToPolls(MessageObject obj, MessageObject old) {
        long pollId = obj.getPollId();
        if (pollId != 0) {
            ArrayList<MessageObject> arrayList = polls.get(pollId);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                polls.put(pollId, arrayList);
            }
            arrayList.add(obj);
            if (old != null) {
                arrayList.remove(old);
            }
        }
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
    protected void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (isOpen) {
            getNotificationCenter().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoad, NotificationCenter.dialogsNeedReload, NotificationCenter.scheduledMessagesUpdated,
                    NotificationCenter.closeChats, NotificationCenter.messagesDidLoad, NotificationCenter.botKeyboardDidLoad, NotificationCenter.userInfoDidLoad, NotificationCenter.needDeleteDialog/*, NotificationCenter.botInfoDidLoad*/});
            openAnimationEnded = false;
        } else {
            if (chatActivityEnterView != null) {
                chatActivityEnterView.onBeginHide();
            }
        }
        getNotificationCenter().setAnimationInProgress(true);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        getNotificationCenter().setAnimationInProgress(false);
        if (isOpen) {
            openAnimationEnded = true;
            if (Build.VERSION.SDK_INT >= 21) {
                createChatAttachView();
            }

            if (chatActivityEnterView.hasRecordVideo() && !chatActivityEnterView.isSendButtonVisible()) {
                boolean isChannel = false;
                if (currentChat != null) {
                    isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
                }
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                String key = isChannel ? "needShowRoundHintChannel" : "needShowRoundHint";
                if (preferences.getBoolean(key, true)) {
                    if (Utilities.random.nextFloat() < 0.2f) {
                        showVoiceHint(false, chatActivityEnterView.isInVideoMode());
                        preferences.edit().putBoolean(key, false).commit();
                    }
                }
            }

            if (!backward && parentLayout != null) {
                for (int a = 0, N = parentLayout.fragmentsStack.size() - 1; a < N; a++) {
                    BaseFragment fragment = parentLayout.fragmentsStack.get(a);
                    if (fragment != this && fragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) fragment;
                        if (chatActivity.dialog_id == dialog_id && chatActivity.inScheduleMode == inScheduleMode) {
                            fragment.removeSelfFromStack();
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (closeChatDialog != null && dialog == closeChatDialog) {
            getMessagesController().deleteDialog(dialog_id, 0);
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
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            if (PhotoViewer.getInstance().getSelectiongLength() == 0 || menu.findItem(android.R.id.copy) == null) {
                return true;
            }
        } else {
            if (chatActivityEnterView.getSelectionLength() == 0 || menu.findItem(android.R.id.copy) == null) {
                return true;
            }
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
        stringBuilder = new SpannableStringBuilder(LocaleController.getString("Mono", R.string.Mono));
        stringBuilder.setSpan(new TypefaceSpan(Typeface.MONOSPACE), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_mono, 8, stringBuilder);
        if (currentEncryptedChat == null || currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 101) {
            stringBuilder = new SpannableStringBuilder(LocaleController.getString("Strike", R.string.Strike));
            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
            stringBuilder.setSpan(new TextStyleSpan(run), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            menu.add(R.id.menu_groupbolditalic, R.id.menu_strike, 9, stringBuilder);
            stringBuilder = new SpannableStringBuilder(LocaleController.getString("Underline", R.string.Underline));
            run = new TextStyleSpan.TextStyleRun();
            run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
            stringBuilder.setSpan(new TextStyleSpan(run), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            menu.add(R.id.menu_groupbolditalic, R.id.menu_underline, 10, stringBuilder);
        }
        menu.add(R.id.menu_groupbolditalic, R.id.menu_link, 11, LocaleController.getString("CreateLink", R.string.CreateLink));
        menu.add(R.id.menu_groupbolditalic, R.id.menu_regular, 12, LocaleController.getString("Regular", R.string.Regular));
        return true;
    }

    private void updateScheduledInterface(boolean animated) {
        if (chatActivityEnterView != null) {
            chatActivityEnterView.updateScheduleButton(animated);
        }
    }

    private void updateBottomOverlay() {
        if (bottomOverlayChatText == null || inScheduleMode) {
            return;
        }
        if (currentChat != null) {
            if (ChatObject.isChannel(currentChat) && !(currentChat instanceof TLRPC.TL_channelForbidden)) {
                if (ChatObject.isNotInChat(currentChat)) {
                    if (getMessagesController().isJoiningChannel(currentChat.id)) {
                        showBottomOverlayProgress(true, false);
                    } else {
                        bottomOverlayChatText.setText(LocaleController.getString("ChannelJoin", R.string.ChannelJoin));
                        showBottomOverlayProgress(false, false);
                    }
                } else {
                    if (!getMessagesController().isDialogMuted(dialog_id)) {
                        bottomOverlayChatText.setText(LocaleController.getString("ChannelMute", R.string.ChannelMute));
                    } else {
                        bottomOverlayChatText.setText(LocaleController.getString("ChannelUnmute", R.string.ChannelUnmute));
                    }
                    showBottomOverlayProgress(false, bottomOverlayProgress.getTag() != null);
                }
                if (!ChatObject.isNotInChat(currentChat) && !currentChat.megagroup && (currentChat.has_link || chatInfo != null && chatInfo.linked_chat_id != 0)) {
                    bottomOverlayChatText2.setText(LocaleController.getString("ChannelDiscuss", R.string.ChannelDiscuss));
                    bottomOverlayChatText2.setVisibility(View.VISIBLE);
                    bottomOverlayChatText2.updateCounter();
                } else {
                    bottomOverlayChatText2.setVisibility(View.GONE);
                }
            } else {
                bottomOverlayChatText.setText(LocaleController.getString("DeleteThisGroup", R.string.DeleteThisGroup));
            }
        } else {
            showBottomOverlayProgress(false, false);
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
                            hideFieldPanel(false);
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

        if (inPreviewMode) {
            searchContainer.setVisibility(View.INVISIBLE);
            bottomOverlayChat.setVisibility(View.INVISIBLE);
            chatActivityEnterView.setFieldFocused(false);
            chatActivityEnterView.setVisibility(View.INVISIBLE);
        } else if (searchItem != null && searchItem.getVisibility() == View.VISIBLE) {
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
            if (muteItem != null) {
                if (currentChat != null && ChatObject.isNotInChat(currentChat)) {
                    muteItem.setVisibility(View.GONE);
                } else {
                    muteItem.setVisibility(View.VISIBLE);
                }
            }
            if (currentChat != null && (ChatObject.isNotInChat(currentChat) || !ChatObject.canWriteToChat(currentChat)) ||
                    currentUser != null && (UserObject.isDeleted(currentUser) || userBlocked)) {
                if (chatActivityEnterView.isEditingMessage()) {
                    chatActivityEnterView.setVisibility(View.VISIBLE);
                    bottomOverlayChat.setVisibility(View.INVISIBLE);
                    chatActivityEnterView.setFieldFocused();
                    AndroidUtilities.runOnUIThread(() -> chatActivityEnterView.openKeyboard(), 100);
                } else {
                    bottomOverlayChat.setVisibility(View.VISIBLE);
                    chatActivityEnterView.setFieldFocused(false);
                    chatActivityEnterView.setVisibility(View.INVISIBLE);
                    chatActivityEnterView.closeKeyboard();
                    if (stickersAdapter != null) {
                        stickersAdapter.hide();
                    }
                }
                if (attachItem != null) {
                    attachItem.setVisibility(View.GONE);
                }
                if (editTextItem != null) {
                    editTextItem.setVisibility(View.GONE);
                }
                if (headerItem != null) {
                    headerItem.setVisibility(View.VISIBLE);
                }
            } else {
                if (botUser != null && currentUser.bot) {
                    bottomOverlayChat.setVisibility(View.VISIBLE);
                    chatActivityEnterView.setVisibility(View.INVISIBLE);
                } else {
                    chatActivityEnterView.setVisibility(View.VISIBLE);
                    bottomOverlayChat.setVisibility(View.INVISIBLE);
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
            alertViewAnimator.playTogether(ObjectAnimator.ofFloat(alertView, View.TRANSLATION_Y, 0));
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
                    alertViewAnimator.playTogether(ObjectAnimator.ofFloat(alertView, View.TRANSLATION_Y, -AndroidUtilities.dp(50)));
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
                pinnedMessageViewAnimator.playTogether(ObjectAnimator.ofFloat(pinnedMessageView, View.TRANSLATION_Y, -AndroidUtilities.dp(50)));
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
        if (pinnedMessageView == null || inScheduleMode) {
            return;
        }
        int pinned_msg_id;
        if (chatInfo != null) {
            if (pinnedMessageObject != null && chatInfo.pinned_msg_id != pinnedMessageObject.getId()) {
                pinnedMessageObject = null;
            }
            if (chatInfo.pinned_msg_id != 0 && pinnedMessageObject == null) {
                pinnedMessageObject = messagesDict[0].get(chatInfo.pinned_msg_id);
            }
            pinned_msg_id = chatInfo.pinned_msg_id;
        } else if (userInfo != null) {
            if (pinnedMessageObject != null && userInfo.pinned_msg_id != pinnedMessageObject.getId()) {
                pinnedMessageObject = null;
            }
            if (userInfo.pinned_msg_id != 0 && pinnedMessageObject == null) {
                pinnedMessageObject = messagesDict[0].get(userInfo.pinned_msg_id);
            }
            pinned_msg_id = userInfo.pinned_msg_id;
        } else {
            pinned_msg_id = 0;
        }
        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        if (chatInfo == null && userInfo == null || pinned_msg_id == 0 || pinned_msg_id == preferences.getInt("pin_" + dialog_id, 0) || actionBar != null && (actionBar.isActionModeShowed() || actionBar.isSearchFieldVisible())) {
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
                        pinnedMessageViewAnimator.playTogether(ObjectAnimator.ofFloat(pinnedMessageView, View.TRANSLATION_Y, 0));
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

                int cacheType = 1;
                int size = 0;
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs2, AndroidUtilities.dp(320));
                TLRPC.PhotoSize thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs2, AndroidUtilities.dp(40));
                TLObject photoSizeObject = pinnedMessageObject.photoThumbsObject2;
                if (photoSize == null) {
                    if (pinnedMessageObject.mediaExists) {
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                        if (photoSize != null) {
                            size = photoSize.size;
                        }
                        cacheType = 0;
                    } else {
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs, AndroidUtilities.dp(320));
                    }
                    thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(pinnedMessageObject.photoThumbs, AndroidUtilities.dp(40));
                    photoSizeObject = pinnedMessageObject.photoThumbsObject;
                }
                if (photoSize == thumbPhotoSize) {
                    thumbPhotoSize = null;
                }
                if (photoSize == null || photoSize instanceof TLRPC.TL_photoSizeEmpty || photoSize.location instanceof TLRPC.TL_fileLocationUnavailable || pinnedMessageObject.isAnyKindOfSticker()) {
                    pinnedMessageImageView.setImageBitmap(null);
                    pinnedImageLocation = null;
                    pinnedImageLocationObject = null;
                    pinnedMessageImageView.setVisibility(View.INVISIBLE);
                    layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(18);
                } else {
                    if (pinnedMessageObject.isRoundVideo()) {
                        pinnedMessageImageView.setRoundRadius(AndroidUtilities.dp(16));
                    } else {
                        pinnedMessageImageView.setRoundRadius(0);
                    }
                    pinnedImageSize = size;
                    pinnedImageCacheType = cacheType;
                    pinnedImageLocation = photoSize;
                    pinnedImageThumbLocation = thumbPhotoSize;
                    pinnedImageLocationObject = photoSizeObject;
                    pinnedMessageImageView.setImage(ImageLocation.getForObject(pinnedImageLocation, photoSizeObject), "50_50", ImageLocation.getForObject(thumbPhotoSize, photoSizeObject), "50_50_b", null, size, cacheType, pinnedMessageObject);
                    pinnedMessageImageView.setVisibility(View.VISIBLE);
                    layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(55);
                }
                pinnedMessageNameTextView.setLayoutParams(layoutParams1);
                pinnedMessageTextView.setLayoutParams(layoutParams2);

                if (pinnedMessageObject.type == MessageObject.TYPE_POLL) {
                    pinnedMessageNameTextView.setText(LocaleController.getString("PinnedPoll", R.string.PinnedPoll));
                } else {
                    pinnedMessageNameTextView.setText(LocaleController.getString("PinnedMessage", R.string.PinnedMessage));
                }
                if (pinnedMessageObject.type == 14) {
                    pinnedMessageTextView.setText(String.format("%s - %s", pinnedMessageObject.getMusicAuthor(), pinnedMessageObject.getMusicTitle()));
                } else if (pinnedMessageObject.type == MessageObject.TYPE_POLL) {
                    TLRPC.TL_messageMediaPoll poll = (TLRPC.TL_messageMediaPoll) pinnedMessageObject.messageOwner.media;
                    String mess = poll.poll.question;
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    pinnedMessageTextView.setText(mess);
                } else if (pinnedMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    pinnedMessageTextView.setText(Emoji.replaceEmoji(pinnedMessageObject.messageOwner.media.game.title, pinnedMessageTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                } else if (!TextUtils.isEmpty(pinnedMessageObject.caption)) {
                    String mess = pinnedMessageObject.caption.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    pinnedMessageTextView.setText(Emoji.replaceEmoji(mess, pinnedMessageTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
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
                pinnedImageLocationObject = null;
                hidePinnedMessageView(animated);
                if (loadingPinnedMessage != pinned_msg_id) {
                    loadingPinnedMessage = pinned_msg_id;
                    getMediaDataController().loadPinnedMessage(dialog_id, ChatObject.isChannel(currentChat) ? currentChat.id : 0, pinned_msg_id, true);
                }
            }
        }
        checkListViewPaddings();
    }

    private void updateTopPanel(boolean animated) {
        if (topChatPanelView == null || inScheduleMode) {
            return;
        }

        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        boolean show;
        long did = dialog_id;
        if (currentEncryptedChat != null) {
            show = !(currentEncryptedChat.admin_id == getUserConfig().getClientUserId() || getContactsController().isLoadingContacts()) && getContactsController().contactsDict.get(currentUser.id) == null;
            did = currentUser.id;
            int vis = preferences.getInt("dialog_bar_vis3" + did, 0);
            if (show && (vis == 1 || vis == 3)) {
                show = false;
            }
        } else {
            show = preferences.getInt("dialog_bar_vis3" + did, 0) == 2;
        }
        boolean showShare = preferences.getBoolean("dialog_bar_share" + did, false);
        boolean showReport = preferences.getBoolean("dialog_bar_report" + did, false);
        boolean showBlock = preferences.getBoolean("dialog_bar_block" + did, false);
        boolean showAdd = preferences.getBoolean("dialog_bar_add" + did, false);
        boolean showGeo = preferences.getBoolean("dialog_bar_location" + did, false);

        if (showReport || showBlock || showGeo) {
            reportSpamButton.setVisibility(View.VISIBLE);
        } else {
            reportSpamButton.setVisibility(View.GONE);
        }

        TLRPC.User user = currentUser != null ? getMessagesController().getUser(currentUser.id) : null;
        if (user != null) {
            if (!user.contact && showAdd) {
                addContactItem.setVisibility(View.VISIBLE);
                addToContactsButton.setVisibility(View.VISIBLE);
                addContactItem.setText(LocaleController.getString("AddToContacts", R.string.AddToContacts));
                if (reportSpamButton.getVisibility() == View.VISIBLE) {
                    addToContactsButton.setText(LocaleController.getString("AddContactChat", R.string.AddContactChat));
                } else {
                    addToContactsButton.setText(LocaleController.formatString("AddContactFullChat", R.string.AddContactFullChat, UserObject.getFirstName(user)).toUpperCase());
                }
                addToContactsButton.setTag(null);
                addToContactsButton.setVisibility(View.VISIBLE);
            } else if (showShare) {
                addContactItem.setVisibility(View.VISIBLE);
                addToContactsButton.setVisibility(View.VISIBLE);
                addContactItem.setText(LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
                addToContactsButton.setText(LocaleController.getString("ShareMyPhone", R.string.ShareMyPhone).toUpperCase());
                addToContactsButton.setTag(1);
                addToContactsButton.setVisibility(View.VISIBLE);
            } else {
                if (!user.contact && !show) {
                    addContactItem.setVisibility(View.VISIBLE);
                    addContactItem.setText(LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
                    addToContactsButton.setTag(2);
                } else {
                    addContactItem.setVisibility(View.GONE);
                }
                addToContactsButton.setVisibility(View.GONE);
            }
            reportSpamButton.setText(LocaleController.getString("ReportSpamUser", R.string.ReportSpamUser));
        } else {
            if (showGeo) {
                reportSpamButton.setText(LocaleController.getString("ReportSpamLocation", R.string.ReportSpamLocation));
                reportSpamButton.setTag(R.id.object_tag, 1);
                reportSpamButton.setTextColor(Theme.getColor(Theme.key_chat_addContact));
                reportSpamButton.setTag(Theme.key_chat_addContact);
            } else {
                reportSpamButton.setText(LocaleController.getString("ReportSpamAndLeave", R.string.ReportSpamAndLeave));
                reportSpamButton.setTag(R.id.object_tag, null);
                reportSpamButton.setTextColor(Theme.getColor(Theme.key_chat_reportSpam));
                reportSpamButton.setTag(Theme.key_chat_reportSpam);
            }
            if (addContactItem != null) {
                addContactItem.setVisibility(View.GONE);
            }
            addToContactsButton.setVisibility(View.GONE);
        }
        if (userBlocked || addToContactsButton.getVisibility() == View.GONE && reportSpamButton.getVisibility() == View.GONE) {
            show = false;
        }

        if (show) {
            if (topChatPanelView.getTag() != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("show spam button");
                }
                topChatPanelView.setTag(null);
                topChatPanelView.setVisibility(View.VISIBLE);
                if (reportSpamViewAnimator != null) {
                    reportSpamViewAnimator.cancel();
                    reportSpamViewAnimator = null;
                }
                if (animated) {
                    reportSpamViewAnimator = new AnimatorSet();
                    reportSpamViewAnimator.playTogether(ObjectAnimator.ofFloat(topChatPanelView, View.TRANSLATION_Y, 0));
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
                } else {
                    topChatPanelView.setTranslationY(0);
                }
            }
        } else {
            if (topChatPanelView.getTag() == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("hide spam button");
                }
                topChatPanelView.setTag(1);

                if (reportSpamViewAnimator != null) {
                    reportSpamViewAnimator.cancel();
                    reportSpamViewAnimator = null;
                }
                if (animated) {
                    reportSpamViewAnimator = new AnimatorSet();
                    reportSpamViewAnimator.playTogether(ObjectAnimator.ofFloat(topChatPanelView, View.TRANSLATION_Y, -AndroidUtilities.dp(50)));
                    reportSpamViewAnimator.setDuration(200);
                    reportSpamViewAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (reportSpamViewAnimator != null && reportSpamViewAnimator.equals(animation)) {
                                topChatPanelView.setVisibility(View.GONE);
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
                } else {
                    topChatPanelView.setTranslationY(-AndroidUtilities.dp(50));
                }
            }
        }
        checkListViewPaddings();
    }

    private void checkListViewPaddingsInternal() {
        if (chatLayoutManager == null) {
            return;
        }
        try {
            int firstVisPos = chatLayoutManager.findFirstVisibleItemPosition();
            int lastVisPos = RecyclerView.NO_POSITION;
            if (!wasManualScroll && unreadMessageObject != null) {
                int pos = messages.indexOf(unreadMessageObject);
                if (pos >= 0) {
                    lastVisPos = pos + chatAdapter.messagesStartRow;
                    firstVisPos = RecyclerView.NO_POSITION;
                }
            }
            int top = 0;
            if (firstVisPos != RecyclerView.NO_POSITION) {
                View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                top = ((firstVisView == null) ? 0 : chatListView.getMeasuredHeight() - firstVisView.getBottom() - chatListView.getPaddingBottom());
            }
            if (chatListView.getPaddingTop() != AndroidUtilities.dp(52) && (pinnedMessageView != null && pinnedMessageView.getTag() == null || topChatPanelView != null && topChatPanelView.getTag() == null)) {
                chatListView.setPadding(0, AndroidUtilities.dp(52), 0, AndroidUtilities.dp(3));
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) floatingDateView.getLayoutParams();
                layoutParams.topMargin = AndroidUtilities.dp(52);
                floatingDateView.setLayoutParams(layoutParams);
                chatListView.setTopGlowOffset(AndroidUtilities.dp(48));
            } else if (chatListView.getPaddingTop() != AndroidUtilities.dp(4) && (pinnedMessageView == null || pinnedMessageView.getTag() != null) && (topChatPanelView == null || topChatPanelView.getTag() != null)) {
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
            } else if (lastVisPos != RecyclerView.NO_POSITION) {
                top = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom() - chatListView.getPaddingTop() - AndroidUtilities.dp(29);
                chatLayoutManager.scrollToPositionWithOffset(lastVisPos, top);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void checkListViewPaddings() {
        if (!wasManualScroll && unreadMessageObject != null) {
            int pos = messages.indexOf(unreadMessageObject);
            if (pos >= 0) {
                fixPaddingsInLayout = true;
                if (fragmentView != null) {
                    fragmentView.requestLayout();
                }
            }
        } else {
            AndroidUtilities.runOnUIThread(this::checkListViewPaddingsInternal);
        }
    }

    private void checkRaiseSensors() {
        if (chatActivityEnterView != null && chatActivityEnterView.isStickersExpanded()) {
            MediaController.getInstance().setAllowStartRecord(false);
        } else if (currentChat != null && !ChatObject.canSendMedia(currentChat)) {
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
    protected void setInPreviewMode(boolean value) {
        super.setInPreviewMode(value);
        if (avatarContainer != null) {
            avatarContainer.setOccupyStatusBar(!value);
            avatarContainer.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !value ? 56 : 0, 0, 40, 0));
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setVisibility(!value ? View.VISIBLE : View.INVISIBLE);
        }
        if (actionBar != null) {
            actionBar.setBackButtonDrawable(!value ? new BackDrawable(false) : null);
            headerItem.setAlpha(!value ? 1.0f : 0.0f);
            attachItem.setAlpha(!value ? 1.0f : 0.0f);
        }

        if (chatListView != null) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                MessageObject message = null;
                if (view instanceof ChatMessageCell) {
                    message = ((ChatMessageCell) view).getMessageObject();
                } else if (view instanceof ChatActionCell) {
                    message = ((ChatActionCell) view).getMessageObject();
                }
                if (message != null && message.messageOwner != null && message.messageOwner.media_unread && message.messageOwner.mentioned) {
                    if (!message.isVoice() && !message.isRoundVideo()) {
                        newMentionsCount--;
                        if (newMentionsCount <= 0) {
                            newMentionsCount = 0;
                            hasAllMentionsLocal = true;
                            showMentionDownButton(false, true);
                        } else {
                            mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                        }
                        getMessagesController().markMentionMessageAsRead(message.getId(), ChatObject.isChannel(currentChat) ? currentChat.id : 0, dialog_id);
                        message.setContentIsRead();
                    }
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).setHighlighted(false);
                        ((ChatMessageCell) view).setHighlightedAnimated();
                    }
                }
            }
        }
        updateBottomOverlay();
        updateSecretStatus();
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
        if (contentView != null) {
            contentView.onResume();
        }

        if (firstOpen) {
            if (getMessagesController().isProxyDialog(dialog_id, true)) {
                SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
                if (preferences.getLong("proxychannel", 0) != dialog_id) {
                    preferences.edit().putLong("proxychannel", dialog_id).commit();
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("UseProxySponsorInfo", R.string.UseProxySponsorInfo));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    showDialog(builder.create());
                }
            }
        }

        checkActionBarMenu();
        if (replyImageLocation != null && replyImageView != null) {
            replyImageView.setImage(ImageLocation.getForObject(replyImageLocation, replyImageLocationObject), "50_50", ImageLocation.getForObject(replyImageThumbLocation, replyImageLocationObject), "50_50_b", null, replyImageSize, replyImageCacheType, replyingMessageObject);
        }
        if (pinnedImageLocation != null && pinnedMessageImageView != null) {
            pinnedMessageImageView.setImage(ImageLocation.getForObject(pinnedImageLocation, pinnedImageLocationObject), "50_50", ImageLocation.getForObject(pinnedImageThumbLocation, pinnedImageLocationObject), "50_50_b", null, pinnedImageSize, pinnedImageCacheType, pinnedMessageObject);
        }

        if (!inScheduleMode) {
            getNotificationsController().setOpenedDialogId(dialog_id);
        }
        getMessagesController().setLastVisibleDialogId(dialog_id, inScheduleMode, true);
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && scrollToMessage != null) {
                if (chatListView != null) {
                    int yOffset;
                    boolean bottom = true;
                    if (scrollToMessagePosition == -9000) {
                        yOffset = getScrollOffsetForMessage(scrollToMessage);
                        bottom = false;
                    } else if (scrollToMessagePosition == -10000) {
                        yOffset = -AndroidUtilities.dp(11);
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
        checkScrollForLoad(false);
        if (wasPaused) {
            wasPaused = false;
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }

        fixLayout();
        applyDraftMaybe(false);
        if (bottomOverlayChat != null && bottomOverlayChat.getVisibility() != View.VISIBLE && !actionBar.isSearchFieldVisible()) {
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
            AndroidUtilities.runOnUIThread(() -> {
                openVideoEditor(startVideoEdit, null);
                startVideoEdit = null;
            });
        }

        if (chatListView != null && (chatActivityEnterView == null || !chatActivityEnterView.isEditingMessage())) {
            chatListView.setOnItemLongClickListener(onItemLongClickListener);
            chatListView.setOnItemClickListener(onItemClickListener);
            chatListView.setLongClickable(true);
        }
        checkBotCommands();
        updateTitle();
    }

    @Override
    public void finishFragment() {
        super.finishFragment();
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }
        getMessagesController().markDialogAsReadNow(dialog_id);
        MediaController.getInstance().stopRaiseToEarSensors(this, true);
        paused = true;
        wasPaused = true;
        if (!inScheduleMode) {
            getNotificationsController().setOpenedDialogId(0);
        }
        getMessagesController().setLastVisibleDialogId(dialog_id, inScheduleMode, false);
        CharSequence draftMessage = null;
        MessageObject replyMessage = null;
        boolean searchWebpage = true;
        if (!ignoreAttachOnPause && chatActivityEnterView != null && bottomOverlayChat.getVisibility() != View.VISIBLE) {
            chatActivityEnterView.onPause();
            replyMessage = replyingMessageObject;
            if (!chatActivityEnterView.isEditingMessage()) {
                draftMessage = AndroidUtilities.getTrimmedString(chatActivityEnterView.getFieldText());
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
        if (contentView != null) {
            contentView.onPause();
        }
        if (!inScheduleMode) {
            CharSequence[] message = new CharSequence[]{draftMessage};
            ArrayList<TLRPC.MessageEntity> entities = getMediaDataController().getEntities(message);
            getMediaDataController().saveDraft(dialog_id, message[0], entities, replyMessage != null ? replyMessage.messageOwner : null, !searchWebpage);
            getMessagesController().cancelTyping(0, dialog_id);

            if (!pausedOnLastMessage) {
                SharedPreferences.Editor editor = MessagesController.getNotificationsSettings(currentAccount).edit();
                int messageId = 0;
                int offset = 0;
                if (chatLayoutManager != null) {
                    int position = chatLayoutManager.findFirstVisibleItemPosition();
                    if (position != 0) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(position);
                        if (holder != null) {
                            int mid = 0;
                            if (holder.itemView instanceof ChatMessageCell) {
                                mid = ((ChatMessageCell) holder.itemView).getMessageObject().getId();
                            } else if (holder.itemView instanceof ChatActionCell) {
                                mid = ((ChatActionCell) holder.itemView).getMessageObject().getId();
                            }
                            if (mid == 0) {
                                holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(position + 1);
                            }
                            boolean ignore = false;
                            for (int a = position - 1; a >= chatAdapter.messagesStartRow; a--) {
                                int num = a - chatAdapter.messagesStartRow;
                                if (num < 0 || num >= messages.size()) {
                                    continue;
                                }
                                MessageObject messageObject = messages.get(num);
                                if (messageObject.getId() == 0) {
                                    continue;
                                }
                                if ((!messageObject.isOut() || messageObject.messageOwner.from_scheduled) && messageObject.isUnread()) {
                                    ignore = true;
                                    messageId = 0;
                                }
                                break;
                            }
                            if (holder != null && !ignore) {
                                if (holder.itemView instanceof ChatMessageCell) {
                                    messageId = ((ChatMessageCell) holder.itemView).getMessageObject().getId();
                                } else if (holder.itemView instanceof ChatActionCell) {
                                    messageId = ((ChatActionCell) holder.itemView).getMessageObject().getId();
                                }
                                if (messageId > 0 && currentEncryptedChat == null || messageId < 0 && currentEncryptedChat != null) {
                                    offset = holder.itemView.getBottom() - chatListView.getMeasuredHeight();
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("save offset = " + offset + " for mid " + messageId);
                                    }
                                } else {
                                    messageId = 0;
                                }
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

            if (undoView != null) {
                undoView.hide(true, 0);
            }
        }
    }

    private void applyDraftMaybe(boolean canClear) {
        if (chatActivityEnterView == null || inScheduleMode) {
            return;
        }
        TLRPC.DraftMessage draftMessage = getMediaDataController().getDraft(dialog_id);
        TLRPC.Message draftReplyMessage = draftMessage != null && draftMessage.reply_to_msg_id != 0 ? getMediaDataController().getDraftMessage(dialog_id) : null;
        if (chatActivityEnterView.getFieldText() == null) {
            if (draftMessage != null) {
                chatActivityEnterView.setWebPage(null, !draftMessage.no_webpage);
                CharSequence message;
                if (!draftMessage.entities.isEmpty()) {
                    SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(draftMessage.message);
                    MediaDataController.sortEntities(draftMessage.entities);
                    for (int a = 0; a < draftMessage.entities.size(); a++) {
                        TLRPC.MessageEntity entity = draftMessage.entities.get(a);
                        if (entity instanceof TLRPC.TL_inputMessageEntityMentionName || entity instanceof TLRPC.TL_messageEntityMentionName) {
                            int user_id;
                            if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                                user_id = ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id;
                            } else {
                                user_id = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
                            }
                            if (entity.offset + entity.length < stringBuilder.length() && stringBuilder.charAt(entity.offset + entity.length) == ' ') {
                                entity.length++;
                            }
                            stringBuilder.setSpan(new URLSpanUserMention("" + user_id, 1), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                            run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
                            MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                        } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                            run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
                            MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                        } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                            run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
                            MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                        } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
                            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                            run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
                            MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                        } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                            run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
                            MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                        } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                            stringBuilder.setSpan(new URLSpanReplacement(entity.url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    message = stringBuilder;
                } else {
                    message = draftMessage.message;
                }
                chatActivityEnterView.setFieldText(message);
                if (getArguments().getBoolean("hasUrl", false)) {
                    chatActivityEnterView.setSelection(draftMessage.message.indexOf('\n') + 1);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (chatActivityEnterView != null) {
                            chatActivityEnterView.setFieldFocused(true);
                            chatActivityEnterView.openKeyboard();
                        }
                    }, 700);
                }
            }
        } else if (canClear && draftMessage == null) {
            chatActivityEnterView.setFieldText("");
            hideFieldPanel(true);
        }
        if (replyingMessageObject == null && draftReplyMessage != null) {
            replyingMessageObject = new MessageObject(currentAccount, draftReplyMessage, getMessagesController().getUsers(), false);
            showFieldPanelForReply(replyingMessageObject);
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
            MediaController.getInstance().setLastVisibleMessageIds(currentAccount, chatEnterTime, chatLeaveTime, currentUser, currentEncryptedChat, visibleMessages, messageId);
        } else {
            SecretMediaViewer viewer = SecretMediaViewer.getInstance();
            MessageObject messageObject = viewer.getCurrentMessageObject();
            if (messageObject != null && !messageObject.isOut()) {
                MediaController.getInstance().setLastVisibleMessageIds(currentAccount, viewer.getOpenTime(), viewer.getCloseTime(), currentUser, null, null, messageObject.getId());
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

    public boolean maybePlayVisibleVideo() {
        if (chatListView == null) {
            return false;
        }
        MessageObject playingMessage = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessage != null && !playingMessage.isVideo()) {
            return false;
        }
        MessageObject visibleMessage = null;
        AnimatedFileDrawable visibleAnimation = null;
        if (noSoundHintView != null && noSoundHintView.getTag() != null) {
            ChatMessageCell cell = noSoundHintView.getMessageCell();
            ImageReceiver imageReceiver = cell.getPhotoImage();
            visibleAnimation = imageReceiver.getAnimation();
            if (visibleAnimation != null) {
                visibleMessage = cell.getMessageObject();
                scrollToVideo = cell.getTop() + imageReceiver.getImageY2() > chatListView.getMeasuredHeight();
            }
        }
        if (visibleMessage == null) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = chatListView.getChildAt(a);
                if (!(child instanceof ChatMessageCell)) {
                    continue;
                }
                ChatMessageCell messageCell = (ChatMessageCell) child;
                MessageObject messageObject = messageCell.getMessageObject();
                boolean isRoundVideo = messageObject.isRoundVideo();
                if (!messageObject.isVideo() && !isRoundVideo) {
                    continue;
                }
                ImageReceiver imageReceiver = messageCell.getPhotoImage();
                AnimatedFileDrawable animation = imageReceiver.getAnimation();
                if (animation == null) {
                    continue;
                }
                int top = child.getTop() + imageReceiver.getImageY();
                int bottom = top + imageReceiver.getImageHeight();
                if (bottom < 0 || top > chatListView.getMeasuredHeight()) {
                    continue;
                }
                if (visibleMessage != null && top < 0) {
                    break;
                }
                visibleMessage = messageObject;
                visibleAnimation = animation;
                scrollToVideo = top < 0 || bottom > chatListView.getMeasuredHeight();
                if (top >= 0 && bottom <= chatListView.getMeasuredHeight()) {
                    break;
                }
            }
        }
        if (visibleMessage != null) {
            if (MediaController.getInstance().isPlayingMessage(visibleMessage)) {
                return false;
            }
            if (noSoundHintView != null) {
                noSoundHintView.hide();
            }
            if (forwardHintView != null) {
                forwardHintView.hide();
            }
            if (visibleMessage.isRoundVideo()) {
                boolean result = MediaController.getInstance().playMessage(visibleMessage);
                MediaController.getInstance().setVoiceMessagesPlaylist(result ? createVoiceMessagesPlaylist(visibleMessage, false) : null, false);
                return result;
            } else {
                SharedConfig.setNoSoundHintShowed(true);
                visibleMessage.audioProgress = visibleAnimation.getCurrentProgress();
                visibleMessage.audioProgressMs = visibleAnimation.getCurrentProgressMs();
                visibleAnimation.stop();
                if (PhotoViewer.isPlayingMessageInPip(visibleMessage)) {
                    PhotoViewer.getPipInstance().destroyPhotoViewer();
                }
                return MediaController.getInstance().playMessage(visibleMessage);
            }
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        fixLayout();
        if (visibleDialog instanceof DatePickerDialog) {
            visibleDialog.dismiss();
        }
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }

        if (!AndroidUtilities.isTablet()) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
                    return;
                }
                MessageObject message = MediaController.getInstance().getPlayingMessageObject();
                if (message != null && message.isVideo()) {
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    getFileLoader().setLoadingVideoForPlayer(message.getDocument(), false);
                    MediaController.getInstance().cleanupPlayer(true, true, false, true);

                    if (PhotoViewer.getInstance().openPhoto(message, message.type != 0 ? dialog_id : 0, message.type != 0 ? mergeDialogId : 0, photoViewerProvider, false)) {
                        PhotoViewer.getInstance().setParentChatActivity(ChatActivity.this);
                    }
                    if (noSoundHintView != null) {
                        noSoundHintView.hide();
                    }
                    if (forwardHintView != null) {
                        forwardHintView.hide();
                    }
                    if (slowModeHint != null) {
                        slowModeHint.hide();
                    }
                    MediaController.getInstance().resetGoingToShowMessageObject();
                }
            } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isOpenedFullScreenVideo()) {
                PhotoViewer.getInstance().injectVideoPlayerToMediaController();
                PhotoViewer.getInstance().closePhoto(false, true);
            }
        }
    }

    private void createDeleteMessagesAlert(final MessageObject finalSelectedObject, final MessageObject.GroupedMessages selectedGroup) {
        createDeleteMessagesAlert(finalSelectedObject, selectedGroup, 1);
    }

    private void createDeleteMessagesAlert(final MessageObject finalSelectedObject, final MessageObject.GroupedMessages finalSelectedGroup, int loadParticipant) {
        AlertsCreator.createDeleteMessagesAlert(this, currentUser, currentChat, currentEncryptedChat, chatInfo, mergeDialogId, finalSelectedObject, selectedMessagesIds, finalSelectedGroup, inScheduleMode, loadParticipant, () -> {
            hideActionMode();
            updatePinnedMessageView(true);
        });
    }

    private void hideActionMode() {
        if (!actionBar.isActionModeShowed()) {
            return;
        }
        if (actionBar != null) {
            actionBar.hideActionMode();
        }
        cantDeleteMessagesCount = 0;
        canEditMessagesCount = 0;
        cantForwardMessagesCount = 0;
        if (chatActivityEnterView != null) {
            EditTextCaption editTextCaption = chatActivityEnterView.getEditField();
            editTextCaption.requestFocus();
            editTextCaption.setAllowDrawCursor(true);
        }
    }

    private void createMenu(View v, boolean single, boolean listView, float x, float y) {
        createMenu(v, single, listView, x, y, true);
    }

    private CharSequence getMessageCaption(MessageObject messageObject, MessageObject.GroupedMessages group) {
        if (messageObject.caption != null) {
            return messageObject.caption;
        }
        if (group == null) {
            return null;
        }
        CharSequence caption = null;
        for (int a = 0, N = group.messages.size(); a < N; a++) {
            MessageObject message = group.messages.get(a);
            if (message.caption != null) {
                if (caption != null) {
                    return null;
                }
                caption = message.caption;
            }
        }
        return caption;
    }

    private void createMenu(View v, boolean single, boolean listView, float x, float y, boolean searchGroup) {
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
        selectedObjectToEditCaption = null;
        for (int a = 1; a >= 0; a--) {
            selectedMessagesCanCopyIds[a].clear();
            selectedMessagesCanStarIds[a].clear();
            selectedMessagesIds[a].clear();
        }
        hideActionMode();
        updatePinnedMessageView(true);

        MessageObject.GroupedMessages groupedMessages;
        if (searchGroup) {
            groupedMessages = getValidGroupedMessage(message);
        } else {
            groupedMessages = null;
        }

        boolean allowChatActions = true;
        boolean allowPin;
        if (inScheduleMode) {
            allowPin = false;
        } else if (currentChat != null) {
            allowPin = message.getDialogId() != mergeDialogId && ChatObject.canPinMessages(currentChat);
        } else if (currentEncryptedChat == null) {
            if (userInfo != null) {
                allowPin = userInfo.can_pin_message;
            } else {
                allowPin = false;
            }
        } else {
            allowPin = false;
        }
        allowPin = allowPin && message.getId() > 0 && (message.messageOwner.action == null || message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
        boolean allowUnpin = message.getDialogId() != mergeDialogId && allowPin && (chatInfo != null && chatInfo.pinned_msg_id == message.getId() || userInfo != null && userInfo.pinned_msg_id == message.getId());
        boolean allowEdit = message.canEditMessage(currentChat) && !chatActivityEnterView.hasAudioToSend() && message.getDialogId() != mergeDialogId;
        if (allowEdit && groupedMessages != null) {
            int captionsCount = 0;
            for (int a = 0, N = groupedMessages.messages.size(); a < N; a++) {
                MessageObject messageObject = groupedMessages.messages.get(a);
                if (a == 0 || !TextUtils.isEmpty(messageObject.caption)) {
                    selectedObjectToEditCaption = messageObject;
                    if (!TextUtils.isEmpty(messageObject.caption)) {
                        captionsCount++;
                    }
                }
            }
            allowEdit = captionsCount < 2;
        }
        if (inScheduleMode || currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 46 ||
                type == 1 && (message.getDialogId() == mergeDialogId || message.needDrawBluredPreview()) ||
                message.messageOwner.action instanceof TLRPC.TL_messageActionSecureValuesSent ||
                currentEncryptedChat == null && message.getId() < 0 ||
                bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE ||
                currentChat != null && (ChatObject.isNotInChat(currentChat) || ChatObject.isChannel(currentChat) && !ChatObject.canPost(currentChat) && !currentChat.megagroup || !ChatObject.canSendMessages(currentChat))) {
            allowChatActions = false;
        }

        if (single || type < 2 || type == 20) {
            if (getParentActivity() == null) {
                return;
            }
            ArrayList<Integer> icons = new ArrayList<>();
            ArrayList<CharSequence> items = new ArrayList<>();
            final ArrayList<Integer> options = new ArrayList<>();

            if (type >= 0 || type == -1 && single && (message.isSending() || message.isEditing()) && currentEncryptedChat == null) {
                selectedObject = message;
                selectedObjectGroup = groupedMessages;

                if (type == -1) {
                    if (selectedObject.type == 0 || selectedObject.isAnimatedEmoji() || getMessageCaption(selectedObject, selectedObjectGroup) != null) {
                        items.add(LocaleController.getString("Copy", R.string.Copy));
                        options.add(3);
                        icons.add(R.drawable.msg_copy);
                    }
                    items.add(LocaleController.getString("CancelSending", R.string.CancelSending));
                    options.add(24);
                    icons.add(R.drawable.msg_delete);
                } else if (type == 0) {
                    items.add(LocaleController.getString("Retry", R.string.Retry));
                    options.add(0);
                    icons.add(R.drawable.msg_retry);
                    items.add(LocaleController.getString("Delete", R.string.Delete));
                    options.add(1);
                    icons.add(R.drawable.msg_delete);
                } else if (type == 1) {
                    if (currentChat != null) {
                        if (allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                            icons.add(R.drawable.msg_reply);
                        }
                        if (allowUnpin) {
                            items.add(LocaleController.getString("UnpinMessage", R.string.UnpinMessage));
                            options.add(14);
                            icons.add(R.drawable.msg_unpin);
                        } else if (allowPin) {
                            items.add(LocaleController.getString("PinMessage", R.string.PinMessage));
                            options.add(13);
                            icons.add(R.drawable.msg_pin);
                        }
                        if (message.canEditMessage(currentChat)) {
                            items.add(LocaleController.getString("Edit", R.string.Edit));
                            options.add(12);
                            icons.add(R.drawable.msg_edit);
                        }
                        if (selectedObject.contentType == 0 && !selectedObject.isMediaEmptyWebpage() && selectedObject.getId() > 0 && !selectedObject.isOut() && (currentChat != null || currentUser != null && currentUser.bot)) {
                            items.add(LocaleController.getString("ReportChat", R.string.ReportChat));
                            options.add(23);
                            icons.add(R.drawable.msg_report);
                        }
                        if (message.canDeleteMessage(inScheduleMode, currentChat)) {
                            items.add(LocaleController.getString("Delete", R.string.Delete));
                            options.add(1);
                            icons.add(R.drawable.msg_delete);
                        }
                    } else {
                        if (selectedObject.getId() > 0 && allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                            icons.add(R.drawable.msg_reply);
                        }
                        if (message.canDeleteMessage(inScheduleMode, currentChat)) {
                            items.add(LocaleController.getString("Delete", R.string.Delete));
                            options.add(1);
                            icons.add(R.drawable.msg_delete);
                        }
                    }
                } else if (type == 20) {
                    items.add(LocaleController.getString("Retry", R.string.Retry));
                    options.add(0);
                    icons.add(R.drawable.msg_retry);
                    items.add(LocaleController.getString("Copy", R.string.Copy));
                    options.add(3);
                    icons.add(R.drawable.msg_copy);
                    items.add(LocaleController.getString("Delete", R.string.Delete));
                    options.add(1);
                    icons.add(R.drawable.msg_delete);
                } else {
                    if (currentEncryptedChat == null) {
                        if (inScheduleMode) {
                            items.add(LocaleController.getString("MessageScheduleSend", R.string.MessageScheduleSend));
                            options.add(100);
                            icons.add(R.drawable.outline_send);
                        }
                        if (selectedObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                            TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) message.messageOwner.action;
                            items.add((call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) && !message.isOutOwner() ? LocaleController.getString("CallBack", R.string.CallBack) : LocaleController.getString("CallAgain", R.string.CallAgain));
                            options.add(18);
                            icons.add(R.drawable.msg_callback);
                            if (VoIPHelper.canRateCall(call)) {
                                items.add(LocaleController.getString("CallMessageReportProblem", R.string.CallMessageReportProblem));
                                options.add(19);
                                icons.add(R.drawable.msg_fave);
                            }
                        }
                        if (allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                            icons.add(R.drawable.msg_reply);
                        }
                        if (selectedObject.type == 0 || selectedObject.isAnimatedEmoji() || getMessageCaption(selectedObject, selectedObjectGroup) != null) {
                            items.add(LocaleController.getString("Copy", R.string.Copy));
                            options.add(3);
                            icons.add(R.drawable.msg_copy);
                        }
                        if (!inScheduleMode && ChatObject.isChannel(currentChat) && currentChat.megagroup) {
                            items.add(LocaleController.getString("CopyLink", R.string.CopyLink));
                            options.add(22);
                            icons.add(R.drawable.msg_link);
                        }
                        if (type == 2) {
                            if (!inScheduleMode) {
                                if (selectedObject.type == MessageObject.TYPE_POLL && !message.isPollClosed()) {
                                    if (message.isVoted()) {
                                        items.add(LocaleController.getString("Unvote", R.string.Unvote));
                                        options.add(25);
                                        icons.add(R.drawable.msg_unvote);
                                    }
                                    if (!message.isForwarded() && (
                                            message.isOut() && (!ChatObject.isChannel(currentChat) || currentChat.megagroup) ||
                                                    ChatObject.isChannel(currentChat) && !currentChat.megagroup && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.edit_messages))) {
                                        items.add(LocaleController.getString("StopPoll", R.string.StopPoll));
                                        options.add(26);
                                        icons.add(R.drawable.msg_pollstop);
                                    }
                                }
                            }
                        } else if (type == 3) {
                            if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && MessageObject.isNewGifDocument(selectedObject.messageOwner.media.webpage.document)) {
                                items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                                options.add(11);
                                icons.add(R.drawable.msg_gif);
                            }
                        } else if (type == 4) {
                            if (selectedObject.isVideo()) {
                                if (!selectedObject.needDrawBluredPreview()) {
                                    items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                    options.add(4);
                                    icons.add(R.drawable.msg_gallery);
                                    items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                    options.add(6);
                                    icons.add(R.drawable.msg_shareout);
                                }
                            } else if (selectedObject.isMusic()) {
                                items.add(LocaleController.getString("SaveToMusic", R.string.SaveToMusic));
                                options.add(10);
                                icons.add(R.drawable.msg_download);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                                icons.add(R.drawable.msg_shareout);
                            } else if (selectedObject.getDocument() != null) {
                                if (MessageObject.isNewGifDocument(selectedObject.getDocument())) {
                                    items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                                    options.add(11);
                                    icons.add(R.drawable.msg_gif);
                                }
                                items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                                options.add(10);
                                icons.add(R.drawable.msg_download);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                                icons.add(R.drawable.msg_shareout);
                            } else {
                                if (!selectedObject.needDrawBluredPreview()) {
                                    items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                    options.add(4);
                                    icons.add(R.drawable.msg_gallery);
                                }
                            }
                        } else if (type == 5) {
                            items.add(LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile));
                            options.add(5);
                            icons.add(R.drawable.msg_language);
                            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                            options.add(10);
                            icons.add(R.drawable.msg_download);
                            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                            options.add(6);
                            icons.add(R.drawable.msg_shareout);
                        } else if (type == 10) {
                            items.add(LocaleController.getString("ApplyThemeFile", R.string.ApplyThemeFile));
                            options.add(5);
                            icons.add(R.drawable.msg_theme);
                            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                            options.add(10);
                            icons.add(R.drawable.msg_download);
                            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                            options.add(6);
                            icons.add(R.drawable.msg_shareout);
                        } else if (type == 6) {
                            items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                            options.add(7);
                            icons.add(R.drawable.msg_gallery);
                            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                            options.add(10);
                            icons.add(R.drawable.msg_download);
                            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                            options.add(6);
                            icons.add(R.drawable.msg_shareout);
                        } else if (type == 7) {
                            if (selectedObject.isMask()) {
                                items.add(LocaleController.getString("AddToMasks", R.string.AddToMasks));
                                options.add(9);
                                icons.add(R.drawable.msg_sticker);
                            } else {
                                items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
                                options.add(9);
                                icons.add(R.drawable.msg_sticker);
                                if (!getMediaDataController().isStickerInFavorites(selectedObject.getDocument())) {
                                    if (getMediaDataController().canAddStickerToFavorites()) {
                                        items.add(LocaleController.getString("AddToFavorites", R.string.AddToFavorites));
                                        options.add(20);
                                        icons.add(R.drawable.msg_fave);
                                    }
                                } else {
                                    items.add(LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites));
                                    options.add(21);
                                    icons.add(R.drawable.msg_unfave);
                                }
                            }
                        } else if (type == 8) {
                            TLRPC.User user = getMessagesController().getUser(selectedObject.messageOwner.media.user_id);
                            if (user != null && user.id != getUserConfig().getClientUserId() && getContactsController().contactsDict.get(user.id) == null) {
                                items.add(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
                                options.add(15);
                                icons.add(R.drawable.msg_addcontact);
                            }
                            if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
                                items.add(LocaleController.getString("Copy", R.string.Copy));
                                options.add(16);
                                icons.add(R.drawable.msg_copy);
                                items.add(LocaleController.getString("Call", R.string.Call));
                                options.add(17);
                                icons.add(R.drawable.msg_callback);
                            }
                        } else if (type == 9) {
                            if (!getMediaDataController().isStickerInFavorites(selectedObject.getDocument())) {
                                items.add(LocaleController.getString("AddToFavorites", R.string.AddToFavorites));
                                options.add(20);
                                icons.add(R.drawable.msg_fave);
                            } else {
                                items.add(LocaleController.getString("DeleteFromFavorites", R.string.DeleteFromFavorites));
                                options.add(21);
                                icons.add(R.drawable.msg_unfave);
                            }
                        }
                        if (!inScheduleMode && !selectedObject.needDrawBluredPreview() && !selectedObject.isLiveLocation() && selectedObject.type != 16) {
                            items.add(LocaleController.getString("Forward", R.string.Forward));
                            options.add(2);
                            icons.add(R.drawable.msg_forward);
                        }
                        if (allowUnpin) {
                            items.add(LocaleController.getString("UnpinMessage", R.string.UnpinMessage));
                            options.add(14);
                            icons.add(R.drawable.msg_unpin);
                        } else if (allowPin) {
                            items.add(LocaleController.getString("PinMessage", R.string.PinMessage));
                            options.add(13);
                            icons.add(R.drawable.msg_pin);
                        }
                        if (allowEdit) {
                            items.add(LocaleController.getString("Edit", R.string.Edit));
                            options.add(12);
                            icons.add(R.drawable.msg_edit);
                        }
                        if (inScheduleMode && selectedObject.canEditMessageScheduleTime(currentChat)) {
                            items.add(LocaleController.getString("MessageScheduleEditTime", R.string.MessageScheduleEditTime));
                            options.add(102);
                            icons.add(R.drawable.msg_schedule);
                        }
                        if (!inScheduleMode && selectedObject.contentType == 0 && selectedObject.getId() > 0 && !selectedObject.isOut() && (currentChat != null || currentUser != null && currentUser.bot)) {
                            items.add(LocaleController.getString("ReportChat", R.string.ReportChat));
                            options.add(23);
                            icons.add(R.drawable.msg_report);
                        }
                        if (message.canDeleteMessage(inScheduleMode, currentChat)) {
                            items.add(LocaleController.getString("Delete", R.string.Delete));
                            options.add(1);
                            icons.add(R.drawable.msg_delete);
                        }
                    } else {
                        if (allowChatActions) {
                            items.add(LocaleController.getString("Reply", R.string.Reply));
                            options.add(8);
                            icons.add(R.drawable.msg_reply);
                        }
                        if (selectedObject.type == 0 || selectedObject.isAnimatedEmoji() || getMessageCaption(selectedObject, selectedObjectGroup) != null) {
                            items.add(LocaleController.getString("Copy", R.string.Copy));
                            options.add(3);
                            icons.add(R.drawable.msg_copy);
                        }
                        if (type == 4) {
                            if (selectedObject.isVideo()) {
                                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                options.add(4);
                                icons.add(R.drawable.msg_gallery);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                                icons.add(R.drawable.msg_shareout);
                            } else if (selectedObject.isMusic()) {
                                items.add(LocaleController.getString("SaveToMusic", R.string.SaveToMusic));
                                options.add(10);
                                icons.add(R.drawable.msg_download);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                                icons.add(R.drawable.msg_shareout);
                            } else if (!selectedObject.isVideo() && selectedObject.getDocument() != null) {
                                items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                                options.add(10);
                                icons.add(R.drawable.msg_download);
                                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                                options.add(6);
                                icons.add(R.drawable.msg_shareout);
                            } else {
                                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                                options.add(4);
                                icons.add(R.drawable.msg_gallery);
                            }
                        } else if (type == 5) {
                            items.add(LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile));
                            options.add(5);
                            icons.add(R.drawable.msg_language);
                        } else if (type == 10) {
                            items.add(LocaleController.getString("ApplyThemeFile", R.string.ApplyThemeFile));
                            options.add(5);
                            icons.add(R.drawable.msg_theme);
                        } else if (type == 7) {
                            items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
                            options.add(9);
                            icons.add(R.drawable.msg_sticker);
                        } else if (type == 8) {
                            TLRPC.User user = getMessagesController().getUser(selectedObject.messageOwner.media.user_id);
                            if (user != null && user.id != getUserConfig().getClientUserId() && getContactsController().contactsDict.get(user.id) == null) {
                                items.add(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
                                options.add(15);
                                icons.add(R.drawable.msg_addcontact);
                            }
                            if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
                                items.add(LocaleController.getString("Copy", R.string.Copy));
                                options.add(16);
                                icons.add(R.drawable.msg_copy);
                                items.add(LocaleController.getString("Call", R.string.Call));
                                options.add(17);
                                icons.add(R.drawable.msg_callback);
                            }
                        }
                        items.add(LocaleController.getString("Delete", R.string.Delete));
                        options.add(1);
                        icons.add(R.drawable.msg_delete);
                    }
                }
            }
            if (options.isEmpty()) {
                return;
            }

            if (scrimPopupWindow != null) {
                scrimPopupWindow.dismiss();
                scrimPopupWindow = null;
                return;
            }

            Rect rect = new Rect();

            ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity());
            popupLayout.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        v.getHitRect(rect);
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            scrimPopupWindow.dismiss();
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        scrimPopupWindow.dismiss();
                    }
                }
                return false;
            });
            popupLayout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                    scrimPopupWindow.dismiss();
                }
            });
            Rect backgroundPaddings = new Rect();
            Drawable shadowDrawable = getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            shadowDrawable.getPadding(backgroundPaddings);
            popupLayout.setBackgroundDrawable(shadowDrawable);

            LinearLayout linearLayout = new LinearLayout(getParentActivity());
            ScrollView scrollView;
            if (Build.VERSION.SDK_INT >= 21) {
                scrollView = new ScrollView(getParentActivity(), null, 0, R.style.scrollbarShapeStyle) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        setMeasuredDimension(linearLayout.getMeasuredWidth(), getMeasuredHeight());
                    }
                };
            } else {
                scrollView = new ScrollView(getParentActivity());
            }
            scrollView.setClipToPadding(false);
            popupLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            linearLayout.setMinimumWidth(AndroidUtilities.dp(200));
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            for (int a = 0, N = items.size(); a < N; a++) {
                ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity());
                cell.setTextAndIcon(items.get(a), icons.get(a));
                linearLayout.addView(cell);
                final int i = a;
                cell.setOnClickListener(v1 -> {
                    if (selectedObject == null || i < 0 || i >= options.size()) {
                        return;
                    }
                    processSelectedOption(options.get(i));
                    if (scrimPopupWindow != null) {
                        scrimPopupWindow.dismiss();
                    }
                });
            }
            scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
            scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    if (scrimPopupWindow != this) {
                        return;
                    }
                    scrimPopupWindow = null;
                    if (scrimAnimatorSet != null) {
                        scrimAnimatorSet.cancel();
                        scrimAnimatorSet = null;
                    }
                    if (scrimView instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) scrimView;
                        cell.setInvalidatesParent(false);
                    }
                    chatLayoutManager.setCanScrollVertically(true);
                    scrimAnimatorSet = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0));
                    if (pagedownButton.getTag() != null) {
                        animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 1.0f));
                    }
                    if (mentiondownButton.getTag() != null) {
                        animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 1.0f));
                    }
                    scrimAnimatorSet.playTogether(animators);
                    scrimAnimatorSet.setDuration(220);
                    scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            scrimView = null;
                            contentView.invalidate();
                            chatListView.invalidate();
                        }
                    });
                    scrimAnimatorSet.start();
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().setAllowDrawCursor(true);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        getParentActivity().getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    }
                }
            };
            scrimPopupWindow.setDismissAnimationDuration(220);
            scrimPopupWindow.setOutsideTouchable(true);
            scrimPopupWindow.setClippingEnabled(true);
            scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            scrimPopupWindow.setFocusable(true);
            popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
            scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
            int popupX = v.getLeft() + (int) x - popupLayout.getMeasuredWidth() + backgroundPaddings.left - AndroidUtilities.dp(28);
            if (popupX < AndroidUtilities.dp(6)) {
                popupX = AndroidUtilities.dp(6);
            } else if (popupX > chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - popupLayout.getMeasuredWidth()) {
                popupX = chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - popupLayout.getMeasuredWidth();
            }
            if (AndroidUtilities.isTablet()) {
                int[] location = new int[2];
                fragmentView.getLocationInWindow(location);
                popupX += location[0];
            }
            int totalHeight = contentView.getHeight();
            int height = popupLayout.getMeasuredHeight();
            int keyboardHeight = contentView.getKeyboardHeight();
            if (keyboardHeight > AndroidUtilities.dp(20)) {
                totalHeight += keyboardHeight;
            }
            int popupY;
            if (height < totalHeight) {
                popupY = (int) (chatListView.getY() + v.getTop() + y);
                if (height - backgroundPaddings.top - backgroundPaddings.bottom > AndroidUtilities.dp(240)) {
                    popupY += AndroidUtilities.dp(240) - height;
                }
                if (popupY < chatListView.getY() + AndroidUtilities.dp(24)) {
                    popupY = (int) (chatListView.getY() + AndroidUtilities.dp(24));
                } else if (popupY > totalHeight - height - AndroidUtilities.dp(8)) {
                    popupY = totalHeight - height - AndroidUtilities.dp(8);
                }
            } else {
                popupY = AndroidUtilities.statusBarHeight;
            }
            scrimPopupWindow.showAtLocation(chatListView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
            chatListView.stopScroll();
            chatLayoutManager.setCanScrollVertically(false);
            scrimView = v;
            if (scrimView instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) scrimView;
                cell.setInvalidatesParent(true);
                restartSticker(cell);
            }
            contentView.invalidate();
            chatListView.invalidate();
            if (scrimAnimatorSet != null) {
                scrimAnimatorSet.cancel();
            }
            scrimAnimatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0, 50));
            if (pagedownButton.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(pagedownButton, View.ALPHA, 0));
            }
            if (mentiondownButton.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(mentiondownButton, View.ALPHA, 0));
            }
            scrimAnimatorSet.playTogether(animators);
            scrimAnimatorSet.setDuration(150);
            scrimAnimatorSet.start();
            if (forwardHintView != null) {
                forwardHintView.hide();
            }
            if (noSoundHintView != null) {
                noSoundHintView.hide();
            }
            if (slowModeHint != null) {
                slowModeHint.hide();
            }
            if (chatActivityEnterView != null) {
                chatActivityEnterView.getEditField().setAllowDrawCursor(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                getParentActivity().getWindow().getDecorView().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            return;
        }

        if (chatActivityEnterView != null && (chatActivityEnterView.isRecordingAudioVideo() || chatActivityEnterView.isRecordLocked())) {
            return;
        }

        final ActionBarMenu actionMode = actionBar.createActionMode();
        View item = actionMode.getItem(delete);
        if (item != null) {
            item.setVisibility(View.VISIBLE);
        }
        bottomMessagesActionContainer.setVisibility(View.VISIBLE);

        int translationY = chatActivityEnterView.getMeasuredHeight() - AndroidUtilities.dp(51);
        if (chatActivityEnterView.getVisibility() == View.VISIBLE) {
            ArrayList<View> views = new ArrayList<>();
            views.add(chatActivityEnterView);
            if (mentionContainer != null && mentionContainer.getVisibility() == View.VISIBLE) {
                views.add(mentionContainer);
            }
            if (stickersPanel != null && stickersPanel.getVisibility() == View.VISIBLE) {
                views.add(stickersPanel);
            }
            actionBar.showActionMode(bottomMessagesActionContainer, null, views.toArray(new View[0]), new boolean[]{false, true, true}, chatListView, translationY);
            if (getParentActivity() != null) {
                ((LaunchActivity) getParentActivity()).hideVisibleActionMode();
            }
            chatActivityEnterView.getEditField().setAllowDrawCursor(false);
        } else if (bottomOverlayChat.getVisibility() == View.VISIBLE) {
            actionBar.showActionMode(bottomMessagesActionContainer, null, new View[]{bottomOverlayChat}, new boolean[]{true}, chatListView, translationY);
        } else if (searchContainer.getVisibility() == View.VISIBLE) {
            actionBar.showActionMode(bottomMessagesActionContainer, null, new View[]{searchContainer}, new boolean[]{true}, chatListView, translationY);
        } else {
            actionBar.showActionMode(bottomMessagesActionContainer, null, null, null, chatListView, translationY);
        }
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }
        chatLayoutManager.setCanScrollVertically(true);
        updatePinnedMessageView(true);

        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int a = 0; a < actionModeViews.size(); a++) {
            View view = actionModeViews.get(a);
            view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
            AndroidUtilities.clearDrawableAnimation(view);
            animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
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
        chatActivityEnterView.setVisibility(View.VISIBLE);
        showFieldPanelForEdit(true, messageObject);
        updateBottomOverlay();
        checkEditTimer();

        chatActivityEnterView.setAllowStickersAndGifs(false, false);

        updatePinnedMessageView(true);
        updateVisibleRows();

        if (!messageObject.scheduled) {
            TLRPC.TL_messages_getMessageEditData req = new TLRPC.TL_messages_getMessageEditData();
            req.peer = getMessagesController().getInputPeer((int) dialog_id);
            req.id = messageObject.getId();
            editingMessageObjectReqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                editingMessageObjectReqId = 0;
                if (response == null) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("EditMessageError", R.string.EditMessageError));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    showDialog(builder.create());

                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.setEditingMessageObject(null, false);
                        hideFieldPanel(true);
                    }
                } else {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.showEditDoneProgress(false, true);
                    }
                }
            }));
        } else {
            chatActivityEnterView.showEditDoneProgress(false, true);
        }
    }

    private void restartSticker(ChatMessageCell cell) {
        MessageObject message = cell.getMessageObject();
        TLRPC.Document document = message.getDocument();
        if (message.isAnimatedEmoji() || MessageObject.isAnimatedStickerDocument(document) && !SharedConfig.loopStickers) {
            ImageReceiver imageReceiver = cell.getPhotoImage();
            RLottieDrawable drawable = imageReceiver.getLottieAnimation();
            if (drawable != null) {
                drawable.restart();
                if (message.isAnimatedEmoji()) {
                    String emoji = message.getStickerEmoji();
                    if ("❤".equals(emoji)) {
                        HashMap<Integer, Integer> pattern = new HashMap<>();
                        pattern.put(1, 1);
                        pattern.put(13, 0);
                        pattern.put(59, 1);
                        pattern.put(71, 0);
                        pattern.put(128, 1);
                        pattern.put(140, 0);
                        drawable.setVibrationPattern(pattern);
                    }
                }
            }
        }
    }

    private String getMessageContent(MessageObject messageObject, int previousUid, boolean name) {
        String str = "";
        if (name) {
            if (previousUid != messageObject.messageOwner.from_id) {
                if (messageObject.messageOwner.from_id > 0) {
                    TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.from_id);
                    if (user != null) {
                        str = ContactsController.formatName(user.first_name, user.last_name) + ":\n";
                    }
                } else if (messageObject.messageOwner.from_id < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-messageObject.messageOwner.from_id);
                    if (chat != null) {
                        str = chat.title + ":\n";
                    }
                }
            }
        }
        if ((messageObject.type == 0 || messageObject.isAnimatedEmoji()) && messageObject.messageOwner.message != null) {
            str += messageObject.messageOwner.message;
        } else if (messageObject.messageOwner.media != null && messageObject.messageOwner.message != null) {
            str += messageObject.messageOwner.message;
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
        MediaController.saveFile(path, getParentActivity(), messageObject.isVideo() ? 1 : 0, null, null);
    }

    private void processSelectedOption(int option) {
        if (selectedObject == null || getParentActivity() == null) {
            return;
        }
        switch (option) {
            case 0: {
                if (selectedObjectGroup != null) {
                    boolean success = true;
                    for (int a = 0; a < selectedObjectGroup.messages.size(); a++) {
                        if (!getSendMessagesHelper().retrySendMessage(selectedObjectGroup.messages.get(a), false)) {
                            success = false;
                        }
                    }
                    if (success && !inScheduleMode) {
                        moveScrollToLastMessage();
                    }
                } else {
                    if (getSendMessagesHelper().retrySendMessage(selectedObject, false)) {
                        updateVisibleRows();
                        if (!inScheduleMode) {
                            moveScrollToLastMessage();
                        }
                    }
                }
                break;
            }
            case 1: {
                if (getParentActivity() == null) {
                    selectedObject = null;
                    selectedObjectToEditCaption = null;
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
                args.putInt("messagesCount", forwardingMessageGroup == null ? 1 : forwardingMessageGroup.messages.size());
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate(this);
                presentFragment(fragment);
                break;
            }
            case 3: {
                CharSequence caption = getMessageCaption(selectedObject, selectedObjectGroup);
                if (caption != null) {
                    AndroidUtilities.addToClipboard(caption);
                } else {
                    AndroidUtilities.addToClipboard(getMessageContent(selectedObject, 0, false));
                }
                break;
            }
            case 4: {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    selectedObjectGroup = null;
                    selectedObjectToEditCaption = null;
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
                    if (locFile.getName().toLowerCase().endsWith("attheme")) {
                        Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, selectedObject.getDocumentName(), null, true);
                        if (themeInfo != null) {
                            presentFragment(new ThemePreviewActivity(themeInfo));
                        } else {
                            scrollToPositionOnRecreate = -1;
                            if (getParentActivity() == null) {
                                selectedObject = null;
                                selectedObjectGroup = null;
                                selectedObjectToEditCaption = null;
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setMessage(LocaleController.getString("IncorrectTheme", R.string.IncorrectTheme));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            showDialog(builder.create());
                        }
                    } else {
                        if (LocaleController.getInstance().applyLanguageFile(locFile, currentAccount)) {
                            presentFragment(new LanguageSelectActivity());
                        } else {
                            if (getParentActivity() == null) {
                                selectedObject = null;
                                selectedObjectGroup = null;
                                selectedObjectToEditCaption = null;
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
                try {
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                } catch (Throwable ignore) {

                }
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
                    selectedObjectToEditCaption = null;
                    return;
                }
                MediaController.saveFile(path, getParentActivity(), 0, null, null);
                break;
            }
            case 8: {
                showFieldPanelForReply(selectedObject);
                break;
            }
            case 9: {
                showDialog(new StickersAlert(getParentActivity(), this, selectedObject.getInputStickerSet(), null, bottomOverlayChat.getVisibility() != View.VISIBLE && (currentChat == null || ChatObject.canSendStickers(currentChat)) ? chatActivityEnterView : null));
                break;
            }
            case 10: {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    selectedObjectGroup = null;
                    selectedObjectToEditCaption = null;
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
                getMessagesController().saveGif(selectedObject, document);
                showGifHint();
                chatActivityEnterView.addRecentGif(document);
                break;
            }
            case 12: {
                if (selectedObjectToEditCaption != null) {
                    startEditingMessageObject(selectedObjectToEditCaption);
                } else {
                    startEditingMessageObject(selectedObject);
                }
                selectedObject = null;
                selectedObjectGroup = null;
                selectedObjectToEditCaption = null;
                break;
            }
            case 13: {
                final int mid = selectedObject.getId();
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("PinMessageAlertTitle", R.string.PinMessageAlertTitle));

                final boolean[] checks;
                if (currentUser != null) {
                    builder.setMessage(LocaleController.getString("PinMessageAlertChat", R.string.PinMessageAlertChat));
                    checks = new boolean[]{false};
                } else if (ChatObject.isChannel(currentChat) && currentChat.megagroup || currentChat != null && !ChatObject.isChannel(currentChat)) {
                    builder.setMessage(LocaleController.getString("PinMessageAlert", R.string.PinMessageAlert));
                    checks = new boolean[]{true};
                    FrameLayout frameLayout = new FrameLayout(getParentActivity());
                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setText(LocaleController.formatString("PinNotify", R.string.PinNotify), "", true, false);
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(8) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(8), 0);
                    frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 8, 0, 8, 0));
                    cell.setOnClickListener(v -> {
                        CheckBoxCell cell1 = (CheckBoxCell) v;
                        checks[0] = !checks[0];
                        cell1.setChecked(checks[0], true);
                    });
                    builder.setView(frameLayout);
                } else {
                    builder.setMessage(LocaleController.getString("PinMessageAlertChannel", R.string.PinMessageAlertChannel));
                    checks = new boolean[]{false};
                }
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> getMessagesController().pinMessage(currentChat, currentUser, mid, checks[0]));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                break;
            }
            case 14: {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("UnpinMessageAlertTitle", R.string.UnpinMessageAlertTitle));
                builder.setMessage(LocaleController.getString("UnpinMessageAlert", R.string.UnpinMessageAlert));
                builder.setPositiveButton(LocaleController.getString("UnpinMessage", R.string.UnpinMessage), (dialogInterface, i) -> getMessagesController().pinMessage(currentChat, currentUser, 0, false));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                break;
            }
            case 15: {
                /*if (!TextUtils.isEmpty(selectedObject.messageOwner.media.vcard)) {
                    openVCard(selectedObject.messageOwner.media.vcard, selectedObject.messageOwner.media.first_name, selectedObject.messageOwner.media.last_name);
                } else {*/
                    Bundle args = new Bundle();
                    args.putInt("user_id", selectedObject.messageOwner.media.user_id);
                    args.putString("phone", selectedObject.messageOwner.media.phone_number);
                    args.putBoolean("addContact", true);
                    presentFragment(new ContactAddActivity(args));
                    break;
                //}
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
                    VoIPHelper.startCall(currentUser, getParentActivity(), getMessagesController().getUserFull(currentUser.id));
                }
                break;
            }
            case 19: {
                VoIPHelper.showRateAlert(getParentActivity(), (TLRPC.TL_messageActionPhoneCall) selectedObject.messageOwner.action);
                break;
            }
            case 20: {
                getMediaDataController().addRecentSticker(MediaDataController.TYPE_FAVE, selectedObject, selectedObject.getDocument(), (int) (System.currentTimeMillis() / 1000), false);
                break;
            }
            case 21: {
                getMediaDataController().addRecentSticker(MediaDataController.TYPE_FAVE, selectedObject, selectedObject.getDocument(), (int) (System.currentTimeMillis() / 1000), true);
                break;
            }
            case 22: {
                TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
                req.id = selectedObject.getId();
                req.channel = MessagesController.getInputChannel(currentChat);
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response != null) {
                        TLRPC.TL_exportedMessageLink exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                        try {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("label", exportedMessageLink.link);
                            clipboard.setPrimaryClip(clip);
                            if (exportedMessageLink.link.contains("/c/")) {
                                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("LinkCopiedPrivate", R.string.LinkCopiedPrivate), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                }));
                break;
            }
            case 23: {
                AlertsCreator.createReportAlert(getParentActivity(), dialog_id, selectedObject.getId(), ChatActivity.this);
                break;
            }
            case 24: {
                if (selectedObject.isEditing() || selectedObject.isSending() && selectedObjectGroup == null) {
                    getSendMessagesHelper().cancelSendingMessage(selectedObject);
                } else if (selectedObject.isSending() && selectedObjectGroup != null) {
                    for (int a = 0; a < selectedObjectGroup.messages.size(); a++) {
                        getSendMessagesHelper().cancelSendingMessage(new ArrayList<>(selectedObjectGroup.messages));
                    }
                }
                break;
            }
            case 25: {
                final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), 3)};
                int requestId = getSendMessagesHelper().sendVote(selectedObject, null, () -> {
                    try {
                        progressDialog[0].dismiss();
                    } catch (Throwable ignore) {

                    }
                    progressDialog[0] = null;
                });
                if (requestId != 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressDialog[0] == null) {
                            return;
                        }
                        progressDialog[0].setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(requestId, true));
                        showDialog(progressDialog[0]);
                    }, 500);
                }
                break;
            }
            case 26: {
                MessageObject object = selectedObject;
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("StopPollAlertTitle", R.string.StopPollAlertTitle));
                builder.setMessage(LocaleController.getString("StopPollAlertText", R.string.StopPollAlertText));
                builder.setPositiveButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), 3)};
                    TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                    TLRPC.TL_inputMediaPoll poll = new TLRPC.TL_inputMediaPoll();
                    poll.poll = new TLRPC.TL_poll();
                    poll.poll.id = mediaPoll.poll.id;
                    poll.poll.question = mediaPoll.poll.question;
                    poll.poll.answers = mediaPoll.poll.answers;
                    poll.poll.closed = true;
                    req.media = poll;
                    req.peer = getMessagesController().getInputPeer((int) dialog_id);
                    req.id = object.getId();
                    req.flags |= 16384;
                    int requestId = getConnectionsManager().sendRequest(req, (response, error) -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog[0].dismiss();
                            } catch (Throwable ignore) {

                            }
                            progressDialog[0] = null;
                        });
                        if (error == null) {
                            getMessagesController().processUpdates((TLRPC.Updates) response, false);
                        } else {
                            AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, ChatActivity.this, req));
                        }
                    });
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressDialog[0] == null) {
                            return;
                        }
                        progressDialog[0].setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(requestId, true));
                        showDialog(progressDialog[0]);
                    }, 500);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                break;
            }
            case 100: {
                if (!checkSlowMode(chatActivityEnterView.getSendButton())) {
                    if (getMediaController().isPlayingMessage(selectedObject)) {
                        getMediaController().cleanupPlayer(true, true);
                    }
                    TLRPC.TL_messages_sendScheduledMessages req = new TLRPC.TL_messages_sendScheduledMessages();
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer((int) dialog_id);
                    if (selectedObjectGroup != null) {
                        for (int a = 0; a < selectedObjectGroup.messages.size(); a++) {
                            req.id.add(selectedObjectGroup.messages.get(a).getId());
                        }
                    } else {
                        req.id.add(selectedObject.getId());
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                        if (error == null) {
                            TLRPC.Updates updates = (TLRPC.Updates) response;
                            MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDeleted, req.id, (int) -dialog_id, true));
                        } else if (error.text != null) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (error.text.startsWith("SLOWMODE_WAIT_")) {
                                    AlertsCreator.showSimpleToast(ChatActivity.this, LocaleController.getString("SlowmodeSendError", R.string.SlowmodeSendError));
                                } else if (error.text.equals("CHAT_SEND_MEDIA_FORBIDDEN")) {
                                    AlertsCreator.showSimpleToast(ChatActivity.this, LocaleController.getString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever));
                                } else {
                                    AlertsCreator.showSimpleToast(ChatActivity.this, error.text);
                                }
                            });
                        }
                    });
                    break;
                }
            }
            case 102: {
                MessageObject message = selectedObject;
                MessageObject.GroupedMessages group = selectedObjectGroup;
                AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(currentUser), message.messageOwner.date, (notify, scheduleDate) -> {
                    if (group != null) {
                        SendMessagesHelper.getInstance(currentAccount).editMessage(group.messages.get(0), null, false, ChatActivity.this, null, scheduleDate, null);
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).editMessage(message, null, false, ChatActivity.this, null, scheduleDate, null);
                    }
                }, null);
                break;
            }
        }
        selectedObject = null;
        selectedObjectGroup = null;
        selectedObjectToEditCaption = null;
    }

    @Override
    public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
        if (forwardingMessage == null && selectedMessagesIds[0].size() == 0 && selectedMessagesIds[1].size() == 0) {
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
                ArrayList<Integer> ids = new ArrayList<>();
                for (int b = 0; b < selectedMessagesIds[a].size(); b++) {
                    ids.add(selectedMessagesIds[a].keyAt(b));
                }
                Collections.sort(ids);
                for (int b = 0; b < ids.size(); b++) {
                    Integer id = ids.get(b);
                    MessageObject messageObject = selectedMessagesIds[a].get(id);
                    if (messageObject != null) {
                        fmessages.add(messageObject);
                    }
                }
                selectedMessagesCanCopyIds[a].clear();
                selectedMessagesCanStarIds[a].clear();
                selectedMessagesIds[a].clear();
            }
            hideActionMode();
            updatePinnedMessageView(true);
        }

        if (dids.size() > 1 || dids.get(0) == getUserConfig().getClientUserId() || message != null) {
            for (int a = 0; a < dids.size(); a++) {
                long did = dids.get(a);
                if (message != null) {
                    getSendMessagesHelper().sendMessage(message.toString(), did, null, null, true, null, null, null, true, 0);
                }
                getSendMessagesHelper().sendMessage(fmessages, did, true, 0);
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
                    if (!getMessagesController().checkCanOpenChat(args, fragment)) {
                        return;
                    }
                }
                ChatActivity chatActivity = new ChatActivity(args);
                if (presentFragment(chatActivity, true)) {
                    chatActivity.showFieldPanelForForward(true, fmessages);
                    if (!AndroidUtilities.isTablet()) {
                        removeSelfFromStack();
                    }
                } else {
                    fragment.finishFragment();
                }
            } else {
                fragment.finishFragment();
                moveScrollToLastMessage();
                showFieldPanelForForward(true, fmessages);
                if (AndroidUtilities.isTablet()) {
                    hideActionMode();
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
            builder.setPositiveButton(LocaleController.getString("DiscardVoiceMessageAction", R.string.DiscardVoiceMessageAction), (dialog, which) -> {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.cancelRecordingAudioVideo();
                }
            });
            builder.setNegativeButton(LocaleController.getString("Continue", R.string.Continue), null);
            showDialog(builder.create());
            return true;
        }
        return false;
    }

    @Override
    public boolean onBackPressed() {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            return false;
        } else if (checkRecordLocked()) {
            return false;
        } else if (actionBar != null && actionBar.isActionModeShowed()) {
            for (int a = 1; a >= 0; a--) {
                selectedMessagesIds[a].clear();
                selectedMessagesCanCopyIds[a].clear();
                selectedMessagesCanStarIds[a].clear();
            }
            hideActionMode();
            updatePinnedMessageView(true);
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
        int lastVisibleItem = RecyclerView.NO_POSITION;
        if (!wasManualScroll && unreadMessageObject != null && chatListView.getMeasuredHeight() != 0) {
            int pos = messages.indexOf(unreadMessageObject);
            if (pos >= 0) {
                lastVisibleItem = chatAdapter.messagesStartRow + pos;
            }
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
                    cell.setCheckBoxVisible(true, true);
                    int idx = messageObject.getDialogId() == dialog_id ? 0 : 1;
                    if (messageObject == editingMessageObject || selectedMessagesIds[idx].indexOfKey(messageObject.getId()) >= 0) {
                        setCellSelectionBackground(messageObject, cell, idx, true);
                        selected = true;
                    } else {
                        cell.setDrawSelectionBackground(false);
                        cell.setChecked(false, false, true);
                    }
                    disableSelection = true;
                } else {
                    cell.setDrawSelectionBackground(false);
                    cell.setCheckBoxVisible(false, true);
                    cell.setChecked(false, false, true);
                }

                cell.setMessageObject(cell.getMessageObject(), cell.getCurrentMessagesGroup(), cell.isPinnedBottom(), cell.isPinnedTop());
                if (cell != scrimView) {
                    cell.setCheckPressed(!disableSelection, disableSelection && selected);
                }
                cell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && messageObject != null && messageObject.getId() == highlightMessageId);
                if (highlightMessageId != Integer.MAX_VALUE) {
                    startMessageUnselect();
                }
                if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE && getMediaDataController().isMessageFound(messageObject.getId(), messageObject.getDialogId() == mergeDialogId) && getMediaDataController().getLastSearchQuery() != null) {
                    cell.setHighlightedText(getMediaDataController().getLastSearchQuery());
                } else {
                    cell.setHighlightedText(null);
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                cell.setMessageObject(cell.getMessageObject());
            }
        }
        chatListView.invalidate();
        if (lastVisibleItem != RecyclerView.NO_POSITION) {
            int top = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom() - chatListView.getPaddingTop() - AndroidUtilities.dp(29);
            chatLayoutManager.scrollToPositionWithOffset(lastVisibleItem, top);
        }
    }

    private void checkEditTimer() {
        if (chatActivityEnterView == null) {
            return;
        }
        MessageObject messageObject = chatActivityEnterView.getEditingMessageObject();
        if (messageObject == null || messageObject.scheduled) {
            return;
        }
        if (currentUser != null && currentUser.self) {
            return;
        }
        int dt = messageObject.canEditMessageAnytime(currentChat) ? 6 * 60 : getMessagesController().maxEditTime + 5 * 60 - Math.abs(getConnectionsManager().getCurrentTime() - messageObject.messageOwner.date);
        if (dt > 0) {
            if (dt <= 5 * 60) {
                replyObjectTextView.setText(LocaleController.formatString("TimeToEdit", R.string.TimeToEdit, String.format("%d:%02d", dt / 60, dt % 60)));
            }
            AndroidUtilities.runOnUIThread(this::checkEditTimer, 1000);
        } else {
            chatActivityEnterView.onEditTimeExpired();
            replyObjectTextView.setText(LocaleController.formatString("TimeToEditExpired", R.string.TimeToEditExpired));
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
        if (!actionBar.isSearchFieldVisible()) {
            avatarContainer.setVisibility(View.GONE);
            headerItem.setVisibility(View.GONE);
            attachItem.setVisibility(View.GONE);
            editTextItem.setVisibility(View.GONE);
            searchItem.setVisibility(View.VISIBLE);
            updateSearchButtons(0, 0, -1);
            updateBottomOverlay();
        }
        openSearchKeyboard = text == null;
        searchItem.openSearch(openSearchKeyboard);
        if (text != null) {
            searchItem.setSearchFieldText(text, false);
            getMediaDataController().searchMessagesInChat(text, dialog_id, mergeDialogId, classGuid, 0, searchingUserMessages);
        }
        updatePinnedMessageView(true);
    }

    @Override
    public void didSelectLocation(TLRPC.MessageMedia location, int live, boolean notify, int scheduleDate) {
        getSendMessagesHelper().sendMessage(location, dialog_id, replyingMessageObject, null, null, notify, scheduleDate);
        if (!inScheduleMode) {
            moveScrollToLastMessage();
        }
        if (live == 1) {
            afterMessageSend();
        }
        if (paused) {
            scrollToTopOnResume = true;
        }
    }

    public boolean isEditingMessageMedia() {
        return chatAttachAlert != null && chatAttachAlert.getEditingMessageObject() != null;
    }

    public boolean isSecretChat() {
        return currentEncryptedChat != null;
    }

    public boolean canScheduleMessage() {
        return currentEncryptedChat == null && (bottomOverlayChat == null || bottomOverlayChat.getVisibility() != View.VISIBLE);
    }

    public boolean isInScheduleMode() {
        return inScheduleMode;
    }

    public TLRPC.User getCurrentUser() {
        return currentUser;
    }

    public TLRPC.Chat getCurrentChat() {
        return currentChat;
    }

    public boolean allowGroupPhotos() {
        return !isEditingMessageMedia() && (currentEncryptedChat == null || AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 73);
    }

    public TLRPC.EncryptedChat getCurrentEncryptedChat() {
        return currentEncryptedChat;
    }

    public TLRPC.ChatFull getCurrentChatInfo() {
        return chatInfo;
    }

    public TLRPC.UserFull getCurrentUserInfo() {
        return userInfo;
    }

    public void sendMedia(MediaController.PhotoEntry photoEntry, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate) {
        if (photoEntry == null) {
            return;
        }
        fillEditingMediaWithCaption(photoEntry.caption, photoEntry.entities);
        if (photoEntry.isVideo) {
            if (videoEditedInfo != null) {
                SendMessagesHelper.prepareSendingVideo(getAccountInstance(), photoEntry.path, videoEditedInfo.estimatedSize, videoEditedInfo.estimatedDuration, videoEditedInfo.resultWidth, videoEditedInfo.resultHeight, videoEditedInfo, dialog_id, replyingMessageObject, photoEntry.caption, photoEntry.entities, photoEntry.ttl, editingMessageObject, notify, scheduleDate);
            } else {
                SendMessagesHelper.prepareSendingVideo(getAccountInstance(), photoEntry.path, 0, 0, 0, 0, null, dialog_id, replyingMessageObject, photoEntry.caption, photoEntry.entities, photoEntry.ttl, editingMessageObject, notify, scheduleDate);
            }
            afterMessageSend();
        } else {
            if (photoEntry.imagePath != null) {
                SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), photoEntry.imagePath, null, dialog_id, replyingMessageObject, photoEntry.caption, photoEntry.entities, photoEntry.stickers, null, photoEntry.ttl, editingMessageObject, notify, scheduleDate);
                afterMessageSend();
            } else if (photoEntry.path != null) {
                SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), photoEntry.path, null, dialog_id, replyingMessageObject, photoEntry.caption, photoEntry.entities, photoEntry.stickers, null, photoEntry.ttl, editingMessageObject, notify, scheduleDate);
                afterMessageSend();
            }
        }
    }

    public void showOpenGameAlert(final TLRPC.TL_game game, final MessageObject messageObject, final String urlStr, boolean ask, final int uid) {
        TLRPC.User user = getMessagesController().getUser(uid);
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
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                showOpenGameAlert(game, messageObject, urlStr, false, uid);
                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("askgame_" + uid, false).commit();
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
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("OpenUrlTitle", R.string.OpenUrlTitle));
            String format = LocaleController.getString("OpenUrlAlert2", R.string.OpenUrlAlert2);
            int index = format.indexOf("%");
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format(format, url));
            if (index >= 0) {
                stringBuilder.setSpan(new URLSpan(url), index, index + url.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            builder.setMessage(stringBuilder);
            builder.setMessageTextViewClickable(false);
            builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), (dialogInterface, i) -> Browser.openUrl(getParentActivity(), url, inlineReturn == 0));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    public void showRequestUrlAlert(final TLRPC.TL_urlAuthResultRequest request, TLRPC.TL_messages_requestUrlAuth buttonReq, String url) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("OpenUrlTitle", R.string.OpenUrlTitle));
        String format = LocaleController.getString("OpenUrlAlert2", R.string.OpenUrlAlert2);
        int index = format.indexOf("%");
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format(format, url));
        if (index >= 0) {
            stringBuilder.setSpan(new URLSpan(url), index, index + url.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        builder.setMessage(stringBuilder);
        builder.setMessageTextViewClickable(false);
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);

        CheckBoxCell[] cells = new CheckBoxCell[2];
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TLRPC.User selfUser = getUserConfig().getCurrentUser();
        for (int a = 0; a < (request.request_write_access ? 2 : 1); a++) {
            cells[a] = new CheckBoxCell(getParentActivity(), 1);
            cells[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
            cells[a].setMultiline(true);
            cells[a].setTag(a);
            if (a == 0) {
                stringBuilder = AndroidUtilities.replaceTags(LocaleController.formatString("OpenUrlOption1", R.string.OpenUrlOption1, request.domain, ContactsController.formatName(selfUser.first_name, selfUser.last_name)));
                index = TextUtils.indexOf(stringBuilder, request.domain);
                if (index >= 0) {
                    stringBuilder.setSpan(new URLSpan(""), index, index + request.domain.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                cells[a].setText(stringBuilder, "", true, false);
            } else if (a == 1) {
                cells[a].setText(AndroidUtilities.replaceTags(LocaleController.formatString("OpenUrlOption2", R.string.OpenUrlOption2, UserObject.getFirstName(request.bot))), "", true, false);
            }
            cells[a].setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            linearLayout.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            cells[a].setOnClickListener(v -> {
                if (!v.isEnabled()) {
                    return;
                }
                Integer num = (Integer) v.getTag();
                cells[num].setChecked(!cells[num].isChecked(), true);
                if (num == 0 && cells[1] != null) {
                    if (cells[num].isChecked()) {
                        cells[1].setEnabled(true);
                    } else {
                        cells[1].setChecked(false, true);
                        cells[1].setEnabled(false);
                    }
                }
            });
        }
        builder.setCustomViewOffset(12);
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), (dialogInterface, i) -> {
            if (!cells[0].isChecked()) {
                Browser.openUrl(getParentActivity(), url, false);
            } else {
                final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), 3)};
                TLRPC.TL_messages_acceptUrlAuth req = new TLRPC.TL_messages_acceptUrlAuth();
                req.button_id = buttonReq.button_id;
                req.msg_id = buttonReq.msg_id;
                req.peer = buttonReq.peer;
                if (request.request_write_access) {
                    req.write_allowed = cells[1].isChecked();
                }
                try {
                    progressDialog[0].dismiss();
                } catch (Throwable ignore) {

                }
                progressDialog[0] = null;
                int requestId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_urlAuthResultAccepted) {
                        TLRPC.TL_urlAuthResultAccepted res = (TLRPC.TL_urlAuthResultAccepted) response;
                        Browser.openUrl(getParentActivity(), res.url, false);
                    } else if (response instanceof TLRPC.TL_urlAuthResultDefault) {
                        Browser.openUrl(getParentActivity(), url, false);
                    }
                }));
                AndroidUtilities.runOnUIThread(() -> {
                    if (progressDialog[0] == null) {
                        return;
                    }
                    progressDialog[0].setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(requestId, true));
                    showDialog(progressDialog[0]);
                }, 500);
            }
        });

        showDialog(builder.create());
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

    public void openVCard(String vcard, String first_name, String last_name) {
        try {
            File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "sharing/");
            f.mkdirs();
            f = new File(f, "vcard.vcf");
            BufferedWriter writer = new BufferedWriter(new FileWriter(f));
            writer.write(vcard);
            writer.close();
            presentFragment(new PhonebookShareActivity(null, null, f, ContactsController.formatName(first_name, last_name)));

            /*
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= 24) {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", f), "text/x-vcard");
            } else {
                intent.setDataAndType(Uri.fromFile(f), "text/x-vcard");
            }
            getParentActivity().startActivity(intent);
            */
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void setCellSelectionBackground(MessageObject message, ChatMessageCell messageCell, int idx, boolean animated) {
        MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);
        if (groupedMessages != null) {
            boolean hasUnselected = false;
            for (int a = 0; a < groupedMessages.messages.size(); a++) {
                if (selectedMessagesIds[idx].indexOfKey(groupedMessages.messages.get(a).getId()) < 0) {
                    hasUnselected = true;
                    break;
                }
            }
            if (!hasUnselected) {
                groupedMessages = null;
            }
        }
        messageCell.setDrawSelectionBackground(groupedMessages == null);
        messageCell.setChecked(true, groupedMessages == null, animated);
    }

    private void setItemAnimationsEnabled(boolean enabled) {
        /*if (chatListView == null) {
            return;
        }
        chatListView.setItemAnimator(enabled ? itemAnimator : null);*/
    }

    private void updateMessageListAccessibilityVisibility() {
        if (currentEncryptedChat != null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            chatListView.setImportantForAccessibility(mentionContainer.getVisibility() == View.VISIBLE || (scrimPopupWindow != null && scrimPopupWindow.isShowing()) ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
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

                if (currentUser != null && currentUser.bot && !inScheduleMode) {
                    botInfoRow = rowCount++;
                } else {
                    botInfoRow = -1;
                }

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

                if (currentUser != null && currentUser.bot && !MessagesController.isSupportUser(currentUser) && !inScheduleMode) {
                    botInfoRow = rowCount++;
                } else {
                    botInfoRow = -1;
                }
            }
        }

        @Override
        public int getItemCount() {
            return clearingHistory ? 0 : rowCount;
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
                    public void didPressShare(ChatMessageCell cell) {
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
                            if (getMessagesController().checkCanOpenChat(args, ChatActivity.this)) {
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
                            showDialog(new ShareAlert(mContext, arrayList, null, ChatObject.isChannel(currentChat), null, false) {
                                @Override
                                public void dismissInternal() {
                                    super.dismissInternal();
                                    AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
                                    if (chatActivityEnterView.getVisibility() == View.VISIBLE) {
                                        fragmentView.requestLayout();
                                    }
                                }
                            });
                            AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                            fragmentView.requestLayout();
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
                    public void videoTimerReached() {
                        showNoSoundHint();
                    }

                    @Override
                    public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell, true, touchX, touchY);
                            return;
                        }
                        if (chat != null && chat != currentChat) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", chat.id);
                            if (postId != 0) {
                                args.putInt("message_id", postId);
                            }
                            if (getMessagesController().checkCanOpenChat(args, ChatActivity.this, cell.getMessageObject())) {
                                presentFragment(new ChatActivity(args));
                            }
                        }
                    }

                    @Override
                    public void didPressHiddenForward(ChatMessageCell cell) {
                        showForwardHint(cell);
                    }

                    @Override
                    public void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
                        if (cell.getMessageObject().type == 16) {
                            if (currentUser != null) {
                                VoIPHelper.startCall(currentUser, getParentActivity(), getMessagesController().getUserFull(currentUser.id));
                            }
                        } else {
                            createMenu(cell, true, false, otherX, otherY, false);
                        }
                    }

                    @Override
                    public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
                        if (actionBar.isActionModeShowed()) {
                            processRowSelect(cell, true, touchX, touchY);
                            return;
                        }
                        if (user != null && user.id != getUserConfig().getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(currentUser != null && currentUser.id == user.id);
                            presentFragment(fragment);
                        }
                    }

                    @Override
                    public void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                        if (getParentActivity() == null || bottomOverlayChat.getVisibility() == View.VISIBLE &&
                                !(button instanceof TLRPC.TL_keyboardButtonSwitchInline) && !(button instanceof TLRPC.TL_keyboardButtonCallback) &&
                                !(button instanceof TLRPC.TL_keyboardButtonGame) && !(button instanceof TLRPC.TL_keyboardButtonUrl) &&
                                !(button instanceof TLRPC.TL_keyboardButtonBuy) && !(button instanceof TLRPC.TL_keyboardButtonUrlAuth)) {
                            return;
                        }
                        chatActivityEnterView.didPressedBotButton(button, cell.getMessageObject(), cell.getMessageObject());
                    }

                    @Override
                    public void didPressReaction(ChatMessageCell cell, TLRPC.TL_reactionCount reaction) {
                        getSendMessagesHelper().sendReaction(cell.getMessageObject(), reaction.reaction, ChatActivity.this);
                    }

                    @Override
                    public void didPressVoteButton(ChatMessageCell cell, TLRPC.TL_pollAnswer button) {
                        getSendMessagesHelper().sendVote(cell.getMessageObject(), button, null);
                    }

                    @Override
                    public void didPressCancelSendButton(ChatMessageCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.messageOwner.send_state != 0) {
                            getSendMessagesHelper().cancelSendingMessage(message);
                        }
                    }

                    @Override
                    public void didLongPress(ChatMessageCell cell, float x, float y) {
                        createMenu(cell, false, false, x, y);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return actionBar != null && !actionBar.isActionModeShowed();
                    }

                    @Override
                    public void didPressUrl(ChatMessageCell cell, final CharacterStyle url, boolean longPress) {
                        if (url == null || getParentActivity() == null) {
                            return;
                        }
                        MessageObject messageObject = cell.getMessageObject();
                        if (url instanceof URLSpanMono) {
                            ((URLSpanMono) url).copyToClipboard();
                            Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                        } else if (url instanceof URLSpanUserMention) {
                            TLRPC.User user = getMessagesController().getUser(Utilities.parseInt(((URLSpanUserMention) url).getURL()));
                            if (user != null) {
                                MessagesController.openChatOrProfileWith(user, null, ChatActivity.this, 0, false);
                            }
                        } else if (url instanceof URLSpanNoUnderline) {
                            String str = ((URLSpanNoUnderline) url).getURL();
                            if (str.startsWith("@")) {
                                String username = str.substring(1).toLowerCase();
                                if (currentChat != null && !TextUtils.isEmpty(currentChat.username) && username.equals(currentChat.username.toLowerCase()) ||
                                        currentUser != null && !TextUtils.isEmpty(currentUser.username) && username.equals(currentUser.username.toLowerCase())) {
                                    Bundle args = new Bundle();
                                    if (currentChat != null) {
                                        args.putInt("chat_id", currentChat.id);
                                    } else if (currentUser != null) {
                                        args.putInt("user_id", currentUser.id);
                                        if (currentEncryptedChat != null) {
                                            args.putLong("dialog_id", dialog_id);
                                        }
                                    }
                                    ProfileActivity fragment = new ProfileActivity(args);
                                    fragment.setPlayProfileAnimation(true);
                                    fragment.setChatInfo(chatInfo);
                                    fragment.setUserInfo(userInfo);
                                    presentFragment(fragment);
                                } else {
                                    getMessagesController().openByUserName(username, ChatActivity.this, 0);
                                }
                            } else if (str.startsWith("#") || str.startsWith("$")) {
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
                                        hideFieldPanel(false);
                                    }
                                }
                            } else if (str.startsWith("video")) {
                                int seekTime = Utilities.parseInt(str);
                                TLRPC.WebPage webPage;
                                if (messageObject.isYouTubeVideo()) {
                                    webPage = messageObject.messageOwner.media.webpage;
                                } else if (messageObject.replyMessageObject != null && messageObject.replyMessageObject.isYouTubeVideo()) {
                                    webPage = messageObject.replyMessageObject.messageOwner.media.webpage;
                                } else {
                                    webPage = null;
                                }
                                if (webPage != null) {
                                    EmbedBottomSheet.show(mContext, webPage.site_name, webPage.title, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height, seekTime);
                                } else {
                                    if (!messageObject.isVideo() && messageObject.replyMessageObject != null) {
                                        messageObject = messagesDict[messageObject.replyMessageObject.getDialogId() == dialog_id ? 0 : 1].get(messageObject.replyMessageObject.getId());
                                        cell = null;
                                    }
                                    messageObject.forceSeekTo = seekTime / (float) messageObject.getDuration();
                                    openPhotoViewerForMessage(cell, messageObject);
                                }
                            }
                        } else {
                            final String urlFinal = ((URLSpan) url).getURL();
                            if (longPress) {
                                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                                builder.setTitle(urlFinal);
                                builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
                                    if (which == 0) {
                                        Browser.openUrl(getParentActivity(), urlFinal, inlineReturn == 0, false);
                                    } else if (which == 1) {
                                        String url1 = urlFinal;
                                        if (url1.startsWith("mailto:")) {
                                            url1 = url1.substring(7);
                                        } else if (url1.startsWith("tel:")) {
                                            url1 = url1.substring(4);
                                        }
                                        AndroidUtilities.addToClipboard(url1);
                                    }
                                });
                                showDialog(builder.create());
                            } else {
                                if (url instanceof URLSpanReplacement && (urlFinal == null || !urlFinal.startsWith("mailto:"))) {
                                    showOpenUrlAlert(urlFinal, true);
                                } else if (url instanceof URLSpan) {
                                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                        String lowerUrl = urlFinal.toLowerCase();
                                        String lowerUrl2 = messageObject.messageOwner.media.webpage.url.toLowerCase();
                                        if ((lowerUrl.contains("telegram.org/blog") || lowerUrl.contains("telegra.ph") || lowerUrl.contains("t.me/iv")) && (lowerUrl.contains(lowerUrl2) || lowerUrl2.contains(lowerUrl))) {
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
                        try {
                            EmbedBottomSheet.show(mContext, title, description, originalUrl, url, w, h);
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }

                    @Override
                    public void didPressReplyMessage(ChatMessageCell cell, int id) {
                        MessageObject messageObject = cell.getMessageObject();
                        if (inScheduleMode) {
                            chatActivityDelegate.openReplyMessage(id);
                            finishFragment();
                        } else {
                            scrollToMessageId(id, messageObject.getId(), true, messageObject.getDialogId() == mergeDialogId ? 1 : 0, false);
                        }
                    }

                    @Override
                    public void didPressViaBot(ChatMessageCell cell, String username) {
                        if (bottomOverlayChat != null && bottomOverlayChat.getVisibility() == View.VISIBLE || bottomOverlay != null && bottomOverlay.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        if (chatActivityEnterView != null && username != null && username.length() > 0) {
                            chatActivityEnterView.setFieldText("@" + username + " ");
                            chatActivityEnterView.openKeyboard();
                        }
                    }

                    @Override
                    public void didStartVideoStream(MessageObject message) {
                        if (message.isVideo()) {
                            sendSecretMessageRead(message);
                        }
                    }

                    void openPhotoViewerForMessage(ChatMessageCell cell, MessageObject message) {
                        if (cell == null) {
                            int count = chatListView.getChildCount();
                            for (int a = 0; a < count; a++) {
                                View child = chatListView.getChildAt(a);
                                if (child instanceof ChatMessageCell) {
                                    ChatMessageCell messageCell = (ChatMessageCell) child;
                                    if (messageCell.getMessageObject().equals(message)) {
                                        cell = messageCell;
                                        break;
                                    }
                                }
                            }
                        }
                        if (message.isVideo()) {
                            sendSecretMessageRead(message);
                        }
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        MessageObject playingObject = MediaController.getInstance().getPlayingMessageObject();
                        if (cell != null && playingObject != null && playingObject.isVideo()) {
                            getFileLoader().setLoadingVideoForPlayer(playingObject.getDocument(), false);
                            if (playingObject.equals(message)) {
                                AnimatedFileDrawable animation = cell.getPhotoImage().getAnimation();
                                if (animation != null && videoTextureView != null && videoPlayerContainer.getTag() != null) {
                                    Bitmap bitmap = animation.getAnimatedBitmap();
                                    if (bitmap != null) {
                                        try {
                                            Bitmap src = videoTextureView.getBitmap(bitmap.getWidth(), bitmap.getHeight());
                                            Canvas canvas = new Canvas(bitmap);
                                            canvas.drawBitmap(src, 0, 0, null);
                                            src.recycle();
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                    }
                                }
                            }
                            MediaController.getInstance().cleanupPlayer(true, true, false, playingObject.equals(message));
                        }
                        if (inScheduleMode && (message.isVideo() || message.type == 1)) {
                            PhotoViewer.getInstance().setParentChatActivity(ChatActivity.this);
                            ArrayList<MessageObject> arrayList = new ArrayList<>();
                            for (int a = 0, N = messages.size(); a < N; a++) {
                                MessageObject m = messages.get(a);
                                if (m.isVideo() || m.type == 1) {
                                    arrayList.add(0, m);
                                }
                            }
                            PhotoViewer.getInstance().openPhoto(arrayList, arrayList.indexOf(message), dialog_id, 0, photoViewerProvider);
                        } else {
                            if (PhotoViewer.getInstance().openPhoto(message, message.type != 0 ? dialog_id : 0, message.type != 0 ? mergeDialogId : 0, photoViewerProvider)) {
                                PhotoViewer.getInstance().setParentChatActivity(ChatActivity.this);
                            }
                        }
                        if (noSoundHintView != null) {
                            noSoundHintView.hide();
                        }
                        if (forwardHintView != null) {
                            forwardHintView.hide();
                        }
                        if (slowModeHint != null) {
                            slowModeHint.hide();
                        }
                        MediaController.getInstance().resetGoingToShowMessageObject();
                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell, float x, float y) {
                        MessageObject message = cell.getMessageObject();
                        if (message.isSendError()) {
                            createMenu(cell, false, false, x, y);
                            return;
                        } else if (message.isSending()) {
                            return;
                        }
                        if (message.isAnimatedEmoji()) {
                            restartSticker(cell);
                        } else if (message.needDrawBluredPreview()) {
                            if (sendSecretMessageRead(message)) {
                                cell.invalidate();
                            }
                            SecretMediaViewer.getInstance().setParentActivity(getParentActivity());
                            SecretMediaViewer.getInstance().openMedia(message, photoViewerProvider);
                        } else if (message.type == MessageObject.TYPE_STICKER || message.type == MessageObject.TYPE_ANIMATED_STICKER) {
                            showDialog(new StickersAlert(getParentActivity(), ChatActivity.this, message.getInputStickerSet(), null, bottomOverlayChat.getVisibility() != View.VISIBLE && (currentChat == null || ChatObject.canSendStickers(currentChat)) ? chatActivityEnterView : null));
                        } else if (message.isVideo() || message.type == 1 || message.type == 0 && !message.isWebpageDocument() || message.isGif()) {
                            openPhotoViewerForMessage(cell, message);
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
                            if (message.getDocumentName().toLowerCase().endsWith("attheme")) {
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
                                Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, message.getDocumentName(), null, true);
                                if (themeInfo != null) {
                                    presentFragment(new ThemePreviewActivity(themeInfo));
                                    return;
                                } else {
                                    scrollToPositionOnRecreate = -1;
                                }
                            }
                            boolean handled = false;
                            if (message.canPreviewDocument()) {
                                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                                PhotoViewer.getInstance().openPhoto(message, message.type != 0 ? dialog_id : 0, message.type != 0 ? mergeDialogId : 0, photoViewerProvider);
                                handled = true;
                            }
                            if (!handled) {
                                try {
                                    AndroidUtilities.openForView(message, getParentActivity());
                                } catch (Exception e) {
                                    FileLog.e(e);
                                    alertUserOpenError(message);
                                }
                            }
                        }
                    }

                    @Override
                    public void didPressInstantButton(ChatMessageCell cell, int type) {
                        MessageObject messageObject = cell.getMessageObject();
                        if (type == 0) {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChatActivity.this);
                                ArticleViewer.getInstance().open(messageObject);
                            }
                        } else if (type == 5) {
                            openVCard(messageObject.messageOwner.media.vcard, messageObject.messageOwner.media.first_name, messageObject.messageOwner.media.last_name);
                        } else {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null) {
                                Browser.openUrl(getParentActivity(), messageObject.messageOwner.media.webpage.url);
                            }
                        }
                    }

                    @Override
                    public String getAdminRank(int uid) {
                        if (ChatObject.isChannel(currentChat) && currentChat.megagroup) {
                            return getMessagesController().getAdminRank(currentChat.id, uid);
                        }
                        return null;
                    }

                    @Override
                    public boolean shouldRepeatSticker(MessageObject message) {
                        return !alredyPlayedStickers.containsKey(message);
                    }

                    @Override
                    public void setShouldNotRepeatSticker(MessageObject message) {
                        alredyPlayedStickers.put(message, true);
                    }
                });
                if (currentEncryptedChat == null) {
                    chatMessageCell.setAllowAssistant(true);
                }
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickImage(ChatActionCell cell) {
                        MessageObject message = cell.getMessageObject();
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 640);
                        if (photoSize != null) {
                            ImageLocation imageLocation = ImageLocation.getForPhoto(photoSize, message.messageOwner.action.photo);
                            PhotoViewer.getInstance().openPhoto(photoSize.location, imageLocation, photoViewerProvider);
                        } else {
                            PhotoViewer.getInstance().openPhoto(message, 0, 0, photoViewerProvider);
                        }
                    }

                    @Override
                    public void didLongPress(ChatActionCell cell, float x, float y) {
                        createMenu(cell, false, false, x, y);
                    }

                    @Override
                    public void needOpenUserProfile(int uid) {
                        if (uid < 0) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", -uid);
                            if (getMessagesController().checkCanOpenChat(args, ChatActivity.this)) {
                                presentFragment(new ChatActivity(args));
                            }
                        } else if (uid != getUserConfig().getClientUserId()) {
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
                    public void didPressReplyMessage(ChatActionCell cell, int id) {
                        MessageObject messageObject = cell.getMessageObject();
                        scrollToMessageId(id, messageObject.getId(), true, messageObject.getDialogId() == mergeDialogId ? 1 : 0, false);
                    }

                    @Override
                    public void didPressBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {
                        if (getParentActivity() == null || bottomOverlayChat.getVisibility() == View.VISIBLE &&
                                !(button instanceof TLRPC.TL_keyboardButtonSwitchInline) && !(button instanceof TLRPC.TL_keyboardButtonCallback) &&
                                !(button instanceof TLRPC.TL_keyboardButtonGame) && !(button instanceof TLRPC.TL_keyboardButtonUrl) &&
                                !(button instanceof TLRPC.TL_keyboardButtonBuy) && !(button instanceof TLRPC.TL_keyboardButtonUrlAuth)) {
                            return;
                        }
                        chatActivityEnterView.didPressedBotButton(button, messageObject, messageObject);
                    }
                });
            } else if (viewType == 2) {
                view = new ChatUnreadCell(mContext);
            } else if (viewType == 3) {
                view = new BotHelpCell(mContext);
                ((BotHelpCell) view).setDelegate(url -> {
                    if (url.startsWith("@")) {
                        getMessagesController().openByUserName(url.substring(1), ChatActivity.this, 0);
                    } else if (url.startsWith("#") || url.startsWith("$")) {
                        DialogsActivity fragment = new DialogsActivity(null);
                        fragment.setSearchString(url);
                        presentFragment(fragment);
                    } else if (url.startsWith("/")) {
                        chatActivityEnterView.setCommand(null, url, false, false);
                        if (chatActivityEnterView.getFieldText() == null) {
                            hideFieldPanel(false);
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
                helpView.setText(botInfo.size() != 0 ? botInfo.get(currentUser.id).description : null);
            } else if (position == loadingDownRow || position == loadingUpRow) {
                ChatLoadingCell loadingCell = (ChatLoadingCell) holder.itemView;
                loadingCell.setProgressVisible(loadsCount > 1);
            } else if (position >= messagesStartRow && position < messagesEndRow) {
                MessageObject message = messages.get(position - messagesStartRow);
                View view = holder.itemView;

                if (view instanceof ChatMessageCell) {
                    final ChatMessageCell messageCell = (ChatMessageCell) view;
                    messageCell.isChat = currentChat != null || UserObject.isUserSelf(currentUser);
                    messageCell.isMegagroup = ChatObject.isChannel(currentChat) && currentChat.megagroup;
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
                    if (!message.hasReactions() && !(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
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
                        pinnedTop = !prevMessage.hasReactions() && !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                        if (pinnedTop) {
                            if (currentChat != null) {
                                pinnedTop = prevMessage.messageOwner.from_id == message.messageOwner.from_id;
                            } else if (UserObject.isUserSelf(currentUser)) {
                                pinnedTop = prevMessage.getFromId() == message.getFromId();
                            }
                        }
                    }
                    if (ChatObject.isChannel(currentChat) && currentChat.megagroup && message.messageOwner.from_id <= 0 && message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.channel_post != 0) {
                        pinnedTop = false;
                        pinnedBottom = false;
                    }

                    messageCell.setMessageObject(message, groupedMessages, pinnedBottom, pinnedTop);
                    messageCell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && message.getId() == highlightMessageId);
                    if (highlightMessageId != Integer.MAX_VALUE) {
                        startMessageUnselect();
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
                                int[] position = new int[2];
                                messageCell.setAlpha(0.0f);
                                messageCell.setTimeAlpha(0.0f);
                                messageCell.getLocationOnScreen(position);
                                position[0] += imageReceiver.getImageX();
                                position[1] += imageReceiver.getImageY();
                                final View cameraContainer = instantCameraView.getCameraContainer();
                                cameraContainer.setPivotX(0.0f);
                                cameraContainer.setPivotY(0.0f);
                                AnimatorSet animatorSet = new AnimatorSet();
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(instantCameraView, View.ALPHA, 0.0f),
                                        ObjectAnimator.ofFloat(cameraContainer, View.SCALE_X, scale),
                                        ObjectAnimator.ofFloat(cameraContainer, View.SCALE_Y, scale),
                                        ObjectAnimator.ofFloat(cameraContainer, View.TRANSLATION_X, position[0] - rect.x),
                                        ObjectAnimator.ofFloat(cameraContainer, View.TRANSLATION_Y, position[1] - rect.y),
                                        ObjectAnimator.ofFloat(instantCameraView.getSwitchButtonView(), View.ALPHA, 0.0f),
                                        ObjectAnimator.ofInt(instantCameraView.getPaint(), AnimationProperties.PAINT_ALPHA, 0),
                                        ObjectAnimator.ofFloat(instantCameraView.getMuteImageView(), View.ALPHA, 0.0f));
                                animatorSet.setDuration(180);
                                animatorSet.setInterpolator(new DecelerateInterpolator());
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        messageCell.setAlpha(1.0f);

                                        Property<ChatMessageCell, Float> ALPHA = new AnimationProperties.FloatProperty<ChatMessageCell>("alpha") {
                                            @Override
                                            public void setValue(ChatMessageCell object, float value) {
                                                object.setTimeAlpha(value);
                                            }

                                            @Override
                                            public Float get(ChatMessageCell object) {
                                                return object.getTimeAlpha();
                                            }
                                        };

                                        AnimatorSet animatorSet = new AnimatorSet();
                                        animatorSet.playTogether(
                                                ObjectAnimator.ofFloat(cameraContainer, View.ALPHA, 0.0f),
                                                ObjectAnimator.ofFloat(messageCell, ALPHA, 1.0f)
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
                    unreadCell.setText(LocaleController.getString("UnreadMessages", R.string.UnreadMessages));
                    if (createUnreadMessageAfterId != 0) {
                        createUnreadMessageAfterId = 0;
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
                    messageCell.setCheckBoxVisible(true, false);
                    MessageObject messageObject = chatActivityEnterView != null ? chatActivityEnterView.getEditingMessageObject() : null;
                    int idx = message.getDialogId() == dialog_id ? 0 : 1;
                    if (messageObject == message || selectedMessagesIds[idx].indexOfKey(message.getId()) >= 0) {
                        setCellSelectionBackground(message, messageCell, idx, false);
                        selected = true;
                    } else {
                        messageCell.setDrawSelectionBackground(false);
                        messageCell.setChecked(false, false, false);
                    }
                    disableSelection = true;
                } else {
                    messageCell.setDrawSelectionBackground(false);
                    messageCell.setChecked(false, false, false);
                    messageCell.setCheckBoxVisible(false, false);
                }
                messageCell.setCheckPressed(!disableSelection, disableSelection && selected);

                if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE && getMediaDataController().isMessageFound(message.getId(), message.getDialogId() == mergeDialogId) && getMediaDataController().getLastSearchQuery() != null) {
                    messageCell.setHighlightedText(getMediaDataController().getLastSearchQuery());
                } else {
                    messageCell.setHighlightedText(null);
                }

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
                if (!inPreviewMode || !messageCell.isHighlighted()) {
                    messageCell.setHighlighted(highlightMessageId != Integer.MAX_VALUE && messageCell.getMessageObject().getId() == highlightMessageId);
                    if (highlightMessageId != Integer.MAX_VALUE) {
                        startMessageUnselect();
                    }
                }
            }

            int position = holder.getAdapterPosition();
            if (position >= messagesStartRow && position < messagesEndRow) {
                MessageObject message = messages.get(position - messagesStartRow);
                View view = holder.itemView;
                if (message != null && message.messageOwner != null && message.messageOwner.media_unread && message.messageOwner.mentioned) {
                    if (!inPreviewMode && !inScheduleMode) {
                        if (!message.isVoice() && !message.isRoundVideo()) {
                            newMentionsCount--;
                            if (newMentionsCount <= 0) {
                                newMentionsCount = 0;
                                hasAllMentionsLocal = true;
                                showMentionDownButton(false, true);
                            } else {
                                mentiondownButtonCounter.setText(String.format("%d", newMentionsCount));
                            }
                            getMessagesController().markMentionMessageAsRead(message.getId(), ChatObject.isChannel(currentChat) ? currentChat.id : 0, dialog_id);
                            message.setContentIsRead();
                        }
                    }
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell messageCell = (ChatMessageCell) view;
                        if (inPreviewMode) {
                            messageCell.setHighlighted(true);
                        } else {
                            messageCell.setHighlightedAnimated();
                        }
                    }
                }
            }
        }

        public void updateRowAtPosition(int index) {
            if (chatLayoutManager == null) {
                return;
            }
            int lastVisibleItem = RecyclerView.NO_POSITION;
            if (!wasManualScroll && unreadMessageObject != null) {
                int pos = messages.indexOf(unreadMessageObject);
                if (pos >= 0) {
                    lastVisibleItem = messagesStartRow + pos;
                }
            }
            notifyItemChanged(index);
            if (lastVisibleItem != RecyclerView.NO_POSITION) {
                int top = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom() - chatListView.getPaddingTop() - AndroidUtilities.dp(29);
                chatLayoutManager.scrollToPositionWithOffset(lastVisibleItem, top);
            }
        }

        public void updateRowWithMessageObject(MessageObject messageObject, boolean allowInPlace) {
            if (allowInPlace) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = chatListView.getChildAt(a);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        if (cell.getMessageObject() == messageObject) {
                            cell.setMessageObject(messageObject, cell.getCurrentMessagesGroup(), cell.isPinnedBottom(), cell.isPinnedTop());
                            return;
                        }
                    }
                }
            }
            int index = messages.indexOf(messageObject);
            if (index == -1) {
                return;
            }
            updateRowAtPosition(index + messagesStartRow);
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
            updateRows();
            try {
                super.notifyItemChanged(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            updateRows();
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
        ThemeDescription.ThemeDescriptionDelegate selectedBackgroundDelegate = () -> {
            updateVisibleRows();
            if (chatActivityEnterView != null && chatActivityEnterView.getEmojiView() != null) {
                chatActivityEnterView.getEmojiView().updateColors();
            }
            if (chatAttachAlert != null) {
                chatAttachAlert.checkColors();
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper),
                new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper_gradient_to),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getSubtitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, new Paint[]{Theme.chat_statusPaint, Theme.chat_statusRecordPaint}, null, null, Theme.key_chat_status, null),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getSubtitleTextView() : null, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_actionBarDefaultSubtitle, null),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector),
                new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),

                new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, 0, null, null, new Drawable[]{Theme.chat_muteIconDrawable}, null, Theme.key_chat_muteIcon),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getTitleTextView() : null, 0, null, null, new Drawable[]{Theme.chat_lockIconDrawable}, null, Theme.key_chat_lockIcon),

                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, BotHelpCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, BotHelpCell.class}, null, new Drawable[]{Theme.chat_msgInShadowDrawable, Theme.chat_msgInMediaShadowDrawable}, null, Theme.key_chat_inBubbleShadow),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutShadowDrawable, Theme.chat_msgOutMediaShadowDrawable}, null, Theme.key_chat_outBubbleShadow),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActionCell.class}, Theme.chat_actionTextPaint, null, null, Theme.key_chat_serviceText),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatActionCell.class}, Theme.chat_actionTextPaint, null, null, Theme.key_chat_serviceLink),

                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_shareIconDrawable, Theme.chat_replyIconDrawable, Theme.chat_botInlineDrawable, Theme.chat_botLinkDrawalbe, Theme.chat_goIconDrawable}, null, Theme.key_chat_serviceIcon),

                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, ChatActionCell.class}, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, ChatActionCell.class}, null, null, null, Theme.key_chat_serviceBackgroundSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, BotHelpCell.class}, null, null, null, Theme.key_chat_messageTextIn),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextOut),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatMessageCell.class, BotHelpCell.class}, null, null, null, Theme.key_chat_messageLinkIn, null),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageLinkOut, null),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgNoSoundDrawable}, null, Theme.key_chat_mediaTimeText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallUpGreenDrawable}, null, Theme.key_chat_outGreenCall),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallDownRedDrawable}, null, Theme.key_chat_inRedCall),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallDownGreenDrawable}, null, Theme.key_chat_inGreenCall),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_timeBackgroundPaint, null, null, Theme.key_chat_mediaTimeBackground),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inContactPhoneSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outContactPhoneText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outContactPhoneSelectedText),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioPerformerText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioPerformerSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioPerformerText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioPerformerSelectedText),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioCacheSeekbar),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbarFill),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioCacheSeekbar),
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
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueInfoSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueInfoSelectedText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaInfoText),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_urlPaint, null, null, Theme.key_chat_linkSelectBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_textSearchSelectionPaint, null, null, Theme.key_chat_textSelectBackground),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outLoader),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outMediaIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outLoaderSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outMediaIconSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inLoader),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inMediaIcon),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inLoaderSelected),
                new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inMediaIconSelected),
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
                new ThemeDescription(bottomMessagesActionContainer, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(bottomMessagesActionContainer, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),

                new ThemeDescription(chatActivityEnterView, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUND, new Class[]{ChatActivityEnterView.class}, new String[]{"audioVideoButtonContainer"}, null, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"messageEditText"}, null, null, null, Theme.key_chat_messagePanelText),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_CURSORCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"messageEditText"}, null, null, null, Theme.key_chat_messagePanelCursor),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"recordSendText"}, null, null, null, Theme.key_chat_fieldOverlayText),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"messageEditText"}, null, null, null, Theme.key_chat_messagePanelHint),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"sendButton"}, null, null, null, Theme.key_chat_messagePanelSend),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"sendButton"}, null, null, null, Theme.key_chat_messagePanelSendPressed),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatActivityEnterView.class}, new String[]{"sendButton"}, null, null, 24, null, Theme.key_chat_messagePanelSend),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"emojiButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"botButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"notifyButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatActivityEnterView.class}, new String[]{"scheduledButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"scheduledButton"}, null, null, null, Theme.key_chat_recordedVoiceDot),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"attachButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"audioSendButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"videoSendButton"}, null, null, null, Theme.key_chat_messagePanelIcons),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"notifyButton"}, null, null, null, Theme.key_chat_messagePanelVideoFrame),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"videoTimelineView"}, null, null, null, Theme.key_chat_messagePanelSend),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatActivityEnterView.class}, new String[]{"doneButtonImage"}, null, null, null, Theme.key_chat_messagePanelBackground),
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
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{ChatActivityEnterView.class}, new String[]{"playDrawable"}, null, null, null, Theme.key_chat_recordedVoicePlayPausePressed),
                new ThemeDescription(chatActivityEnterView, ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{ChatActivityEnterView.class}, new String[]{"pauseDrawable"}, null, null, null, Theme.key_chat_recordedVoicePlayPausePressed),
                new ThemeDescription(chatActivityEnterView, 0, new Class[]{ChatActivityEnterView.class}, new String[]{"dotPaint"}, null, null, null, Theme.key_chat_emojiPanelNewTrending),

                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelBackground),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelShadowLine),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelEmptyText),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelIcon),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelIconSelected),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelStickerPackSelector),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelBackspace),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelTrendingTitle),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelTrendingDescription),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelBadgeText),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelBadgeBackground),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiBottomPanelIcon),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiSearchIcon),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelStickerSetNameHighlight),
                new ThemeDescription(chatActivityEnterView != null ? chatActivityEnterView.getEmojiView() : chatActivityEnterView, 0, new Class[]{EmojiView.class}, null, null, null, selectedBackgroundDelegate, Theme.key_chat_emojiPanelStickerPackSelectorLine),

                new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_undo_background),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor),
                new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor),

                new ThemeDescription(null, 0, null, null, null, null, Theme.key_chat_botKeyboardButtonText),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_chat_botKeyboardButtonBackground),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_chat_botKeyboardButtonBackgroundPressed),

                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_FASTSCROLL, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerPerformer),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose),

                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText),

                new ThemeDescription(pinnedLineView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_chat_topPanelLine),
                new ThemeDescription(pinnedMessageNameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelTitle),
                new ThemeDescription(pinnedMessageTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelMessage),
                new ThemeDescription(alertNameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelTitle),
                new ThemeDescription(alertTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_topPanelMessage),
                new ThemeDescription(closePinned, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_topPanelClose),
                new ThemeDescription(closeReportSpam, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_topPanelClose),
                new ThemeDescription(topChatPanelView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground),
                new ThemeDescription(alertView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground),
                new ThemeDescription(pinnedMessageView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_topPanelBackground),
                new ThemeDescription(addToContactsButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_addContact),
                new ThemeDescription(reportSpamButton, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_chat_reportSpam),
                new ThemeDescription(reportSpamButton, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_chat_addContact),

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
                new ThemeDescription(bottomOverlayChatText2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText),
                new ThemeDescription(bottomOverlayProgress, 0, null, null, null, null, Theme.key_chat_fieldOverlayText),

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

                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_chat_attachMediaBanBackground),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_chat_attachMediaBanText),

                new ThemeDescription(noSoundHintView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HintView.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_gifSaveHintText),
                new ThemeDescription(noSoundHintView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{HintView.class}, new String[]{"imageView"}, null, null, null, Theme.key_chat_gifSaveHintText),
                new ThemeDescription(noSoundHintView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HintView.class}, new String[]{"arrowImageView"}, null, null, null, Theme.key_chat_gifSaveHintBackground),

                new ThemeDescription(forwardHintView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HintView.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_gifSaveHintText),
                new ThemeDescription(forwardHintView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HintView.class}, new String[]{"arrowImageView"}, null, null, null, Theme.key_chat_gifSaveHintBackground),

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

                new ThemeDescription(avatarContainer != null ? avatarContainer.getTimeItem() : null, 0, null, null, null, null, Theme.key_chat_secretTimerBackground),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getTimeItem() : null, 0, null, null, null, null, Theme.key_chat_secretTimerText),

                new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[0]}, null, Theme.key_chat_attachGalleryBackground),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[0]}, null, Theme.key_chat_attachGalleryIcon),
                new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[1]}, null, Theme.key_chat_attachAudioBackground),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[1]}, null, Theme.key_chat_attachAudioIcon),
                new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[2]}, null, Theme.key_chat_attachFileBackground),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[2]}, null, Theme.key_chat_attachFileIcon),
                new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[3]}, null, Theme.key_chat_attachContactBackground),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[3]}, null, Theme.key_chat_attachContactIcon),
                new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[4]}, null, Theme.key_chat_attachLocationBackground),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[4]}, null, Theme.key_chat_attachLocationIcon),
                new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[5]}, null, Theme.key_chat_attachPollBackground),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachButtonDrawables[5]}, null, Theme.key_chat_attachPollIcon),
                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.chat_attachEmptyDrawable}, null, Theme.key_chat_attachEmptyImage),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_chat_attachPhotoBackground),

                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_dialogBackground),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_dialogBackgroundGray),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_dialogTextGray2),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_dialogScrollGlow),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_dialogGrayLine),
                new ThemeDescription(null, 0, null, null, null, selectedBackgroundDelegate, Theme.key_dialogCameraIcon),
        };
    }
}
