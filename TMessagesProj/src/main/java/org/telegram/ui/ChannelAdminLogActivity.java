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
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatLoadingCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatUnreadCell;
import org.telegram.ui.Components.AdminLogFilterAlert;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.ClearHistoryAlert;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.InviteLinkBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhonebookShareAlert;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.UndoView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class ChannelAdminLogActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    protected TLRPC.Chat currentChat;

    private ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();

    private FrameLayout progressView;
    private View progressView2;
    private RadialProgressView progressBar;
    private RecyclerListView chatListView;
    private UndoView undoView;
    private LinearLayoutManager chatLayoutManager;
    private ChatActivityAdapter chatAdapter;
    private TextView bottomOverlayChatText;
    private ImageView bottomOverlayImage;
    private FrameLayout bottomOverlayChat;
    private FrameLayout emptyViewContainer;
    private ChatAvatarContainer avatarContainer;
    private TextView emptyView;
    private ChatActionCell floatingDateView;
    private ActionBarMenuItem searchItem;
    private long minEventId;
    private boolean currentFloatingDateOnScreen;
    private boolean currentFloatingTopIsNotMessage;
    private AnimatorSet floatingDateAnimation;
    private boolean scrollingFloatingDate;
    private int[] mid = new int[]{2};
    private boolean searchWas;

    private boolean checkTextureViewPosition;
    private SizeNotifierFrameLayout contentView;

    private MessageObject selectedObject;

    private FrameLayout searchContainer;
    private ImageView searchCalendarButton;
    private ImageView searchUpButton;
    private ImageView searchDownButton;
    private SimpleTextView searchCountText;

    private FrameLayout roundVideoContainer;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;
    private Path aspectPath;
    private Paint aspectPaint;

    private int scrollToPositionOnRecreate = -1;
    private int scrollToOffsetOnRecreate = 0;

    private boolean paused = true;
    private boolean wasPaused = false;

    private boolean openAnimationEnded;

    private LongSparseArray<MessageObject> messagesDict = new LongSparseArray<>();
    private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<>();
    protected ArrayList<MessageObject> messages = new ArrayList<>();
    private int minDate;
    private boolean endReached;
    private boolean loading;
    private int loadsCount;

    private ArrayList<TLRPC.ChannelParticipant> admins;
    private TLRPC.TL_channelAdminLogEventsFilter currentFilter = null;
    private String searchQuery = "";
    private LongSparseArray<TLRPC.User> selectedAdmins;

    private MessageObject scrollToMessage;

    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker(new int[]{
            NotificationCenter.chatInfoDidLoad,
            NotificationCenter.dialogsNeedReload,
            NotificationCenter.closeChats,
            NotificationCenter.messagesDidLoad,
            NotificationCenter.botKeyboardDidLoad
            /*, NotificationCenter.botInfoDidLoad*/
    });

    private HashMap<String, Object> invitesCache = new HashMap<>();
    private HashMap<Long, TLRPC.User> usersMap;
    private boolean linviteLoading;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

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
                    object.thumb = imageReceiver.getBitmapSafe();
                    object.radius = imageReceiver.getRoundRadius();
                    object.isEvent = true;
                    return object;
                }
            }
            return null;
        }
    };
    private ChatListItemAnimator chatListItemAnimator;

    public ChannelAdminLogActivity(TLRPC.Chat chat) {
        currentChat = chat;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        loadMessages(true);
        loadAdmins();

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.dp(51);
            }
        });

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        notificationsLocker.unlock();
    }

    private void updateEmptyPlaceholder() {
        if (emptyView == null) {
            return;
        }
        if (!TextUtils.isEmpty(searchQuery)) {
            emptyView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(5), AndroidUtilities.dp(8), AndroidUtilities.dp(5));
            emptyView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EventLogEmptyTextSearch", R.string.EventLogEmptyTextSearch, searchQuery)));
        } else if (selectedAdmins != null || currentFilter != null) {
            emptyView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(5), AndroidUtilities.dp(8), AndroidUtilities.dp(5));
            emptyView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EventLogEmptySearch", R.string.EventLogEmptySearch)));
        } else {
            emptyView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            if (currentChat.megagroup) {
                emptyView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EventLogEmpty", R.string.EventLogEmpty)));
            } else {
                emptyView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EventLogEmptyChannel", R.string.EventLogEmptyChannel)));
            }
        }
    }

    private void loadMessages(boolean reset) {
        if (loading) {
            return;
        }
        if (reset) {
            minEventId = Long.MAX_VALUE;
            if (progressView != null) {
                AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 0.3f, true);
                emptyViewContainer.setVisibility(View.INVISIBLE);
                chatListView.setEmptyView(null);
            }
            messagesDict.clear();
            messages.clear();
            messagesByDays.clear();
        }
        loading = true;
        TLRPC.TL_channels_getAdminLog req = new TLRPC.TL_channels_getAdminLog();
        req.channel = MessagesController.getInputChannel(currentChat);
        req.q = searchQuery;
        req.limit = 50;
        if (!reset && !messages.isEmpty()) {
            req.max_id = minEventId;
        } else {
            req.max_id = 0;
        }
        req.min_id = 0;
        if (currentFilter != null) {
            req.flags |= 1;
            req.events_filter = currentFilter;
        }
        if (selectedAdmins != null) {
            req.flags |= 2;
            for (int a = 0; a < selectedAdmins.size(); a++) {
                req.admins.add(MessagesController.getInstance(currentAccount).getInputUser(selectedAdmins.valueAt(a)));
            }
        }
        updateEmptyPlaceholder();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                final TLRPC.TL_channels_adminLogResults res = (TLRPC.TL_channels_adminLogResults) response;
                AndroidUtilities.runOnUIThread(() -> {
                    chatListItemAnimator.setShouldAnimateEnterFromBottom(false);
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    boolean added = false;
                    int oldRowsCount = messages.size();
                    for (int a = 0; a < res.events.size(); a++) {
                        TLRPC.TL_channelAdminLogEvent event = res.events.get(a);
                        if (messagesDict.indexOfKey(event.id) >= 0) {
                            continue;
                        }
                        if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin) {
                            TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin action = (TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin) event.action;
                            if (action.prev_participant instanceof TLRPC.TL_channelParticipantCreator && !(action.new_participant instanceof TLRPC.TL_channelParticipantCreator)) {
                                continue;
                            }
                        }
                        minEventId = Math.min(minEventId, event.id);
                        added = true;
                        MessageObject messageObject = new MessageObject(currentAccount, event, messages, messagesByDays, currentChat, mid, false);
                        if (messageObject.contentType < 0) {
                            continue;
                        }
                        messagesDict.put(event.id, messageObject);
                    }
                    int newRowsCount = messages.size() - oldRowsCount;
                    loading = false;
                    if (!added) {
                        endReached = true;
                    }
                    AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 0.3f, true);
                    chatListView.setEmptyView(emptyViewContainer);
                    if (newRowsCount != 0) {
                        boolean end = false;
                        if (endReached) {
                            end = true;
                            chatAdapter.notifyItemRangeChanged(0, 2);
                        }
                        int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
                        View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                        int top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                        if (newRowsCount - (end ? 1 : 0) > 0) {
                            int insertStart = 1 + (end ? 0 : 1);
                            chatAdapter.notifyItemChanged(insertStart);
                            chatAdapter.notifyItemRangeInserted(insertStart, newRowsCount - (end ? 1 : 0));
                        }
                        if (firstVisPos != -1) {
                            chatLayoutManager.scrollToPositionWithOffset(firstVisPos + newRowsCount - (end ? 1 : 0), top);
                        }
                    } else if (endReached) {
                        chatAdapter.notifyItemRemoved(0);
                    }
                });
            }
        });
        if (reset && chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject messageObject = (MessageObject) args[0];

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
                                cell.updateButtonState(false, true, false);
                            } else if (messageObject1.isRoundVideo()) {
                                cell.checkVideoPlayback(false, null);
                                if (!MediaController.getInstance().isPlayingMessage(messageObject1)) {
                                    if (messageObject1.audioProgress != 0) {
                                        messageObject1.resetPlayingProgress();
                                        cell.invalidate();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
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
                            } else if (messageObject.isRoundVideo()) {
                                if (!MediaController.getInstance().isPlayingMessage(messageObject)) {
                                    cell.checkVideoPlayback(true, null);
                                }
                            }
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
                            }
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (fragmentView != null) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
                progressView2.invalidate();
                if (emptyView != null) {
                    emptyView.invalidate();
                }
                chatListView.invalidateViews();
            }
        }
    }

    private void updateBottomOverlay() {
        /*if (searchItem != null && searchItem.getVisibility() == View.VISIBLE) {
            searchContainer.setVisibility(View.VISIBLE);
            bottomOverlayChat.setVisibility(View.INVISIBLE);
        } else {
            searchContainer.setVisibility(View.INVISIBLE);
            bottomOverlayChat.setVisibility(View.VISIBLE);
        }*/
    }

    @Override
    public View createView(Context context) {
        if (chatMessageCellsCache.isEmpty()) {
            for (int a = 0; a < 8; a++) {
                chatMessageCellsCache.add(new ChatMessageCell(context));
            }
        }

        searchWas = false;
        hasOwnBackground = true;

        Theme.createChatResources(context, false);

        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21 && !AndroidUtilities.isTablet());
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        avatarContainer = new ChatAvatarContainer(context, null, false);
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

            @Override
            public void onSearchCollapse() {
                searchQuery = "";
                avatarContainer.setVisibility(View.VISIBLE);
                if (searchWas) {
                    searchWas = false;
                    loadMessages(true);
                }
                /*highlightMessageId = Integer.MAX_VALUE;
                updateVisibleRows();
                scrollToLastMessage(false);
                */
                updateBottomOverlay();
            }

            @Override
            public void onSearchExpand() {
                avatarContainer.setVisibility(View.GONE);
                updateBottomOverlay();
            }

            @Override
            public void onSearchPressed(EditText editText) {
                searchWas = true;
                searchQuery = editText.getText().toString();
                loadMessages(true);
                //updateSearchButtons(0, 0, 0);
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));

        avatarContainer.setEnabled(false);

        avatarContainer.setTitle(currentChat.title);
        avatarContainer.setSubtitle(LocaleController.getString("EventLogAllEvents", R.string.EventLogAllEvents));
        avatarContainer.setChatAvatar(currentChat);

        fragmentView = new SizeNotifierFrameLayout(context) {

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isRoundVideo() && messageObject.eventId != 0 && messageObject.getDialogId() == -currentChat.id) {
                    MediaController.getInstance().setTextureView(createTextureView(false), aspectRatioFrameLayout, roundVideoContainer, true);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
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
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                if (actionBar.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }

                int childCount = getChildCount();

                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (child == chatListView || child == progressView) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize - AndroidUtilities.dp(48 + 2)), MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else if (child == emptyViewContainer) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

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
                            childTop = (b - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = (b - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (child == emptyViewContainer) {
                        childTop -= AndroidUtilities.dp(24) - (actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() / 2 : 0);
                    } else if (child == actionBar) {
                        childTop -= getPaddingTop();
                    } else if (child == backgroundView) {
                        childTop = 0;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                updateMessagesVisiblePart();
                notifyHeightChanged();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (AvatarPreviewer.hasVisibleInstance()) {
                    AvatarPreviewer.getInstance().onTouchEvent(ev);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };

        contentView = (SizeNotifierFrameLayout) fragmentView;

        contentView.setOccupyStatusBar(!AndroidUtilities.isTablet());
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        emptyViewContainer = new FrameLayout(context);
        emptyViewContainer.setVisibility(View.INVISIBLE);
        contentView.addView(emptyViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        emptyViewContainer.setOnTouchListener((v, event) -> true);

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(6), emptyView, contentView));
        emptyView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        emptyViewContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));

        chatListView = new RecyclerListView(context) {

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) child;
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
                        if (chatMessageCell.getMessageObject().deleted) {
                            imageReceiver.setVisible(false, false);
                            return result;
                        }

                        int top = (int) child.getY();
                        if (chatMessageCell.drawPinnedBottom()) {
                            int p;

                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            p = holder.getAdapterPosition();


                            if (p >= 0) {
                                int nextPosition;

                                nextPosition = p + 1;

                                holder = chatListView.findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    imageReceiver.setVisible(false, false);
                                    return result;
                                }
                            }
                        }
                        float tx = chatMessageCell.getSlidingOffsetX() + chatMessageCell.getCheckBoxTranslation();


                        int y = (int) child.getY() + chatMessageCell.getLayoutHeight();
                        int maxY = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }

                        if (chatMessageCell.drawPinnedTop()) {
                            int p;

                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            p = holder.getAdapterPosition();

                            if (p >= 0) {
                                int tries = 0;
                                while (true) {
                                    if (tries >= 20) {
                                        break;
                                    }
                                    tries++;

                                    int prevPosition;

                                    prevPosition = p - 1;


                                    holder = chatListView.findViewHolderForAdapterPosition(prevPosition);
                                    if (holder != null) {
                                        top = holder.itemView.getTop();
                                        if (holder.itemView instanceof ChatMessageCell) {
                                            chatMessageCell = (ChatMessageCell) holder.itemView;
                                            if (!chatMessageCell.drawPinnedTop()) {
                                                break;
                                            } else {
                                                p = prevPosition;
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
                        if (!chatMessageCell.drawPinnedBottom()) {
                            int cellBottom = (int) (chatMessageCell.getY() + chatMessageCell.getMeasuredHeight());
                            if (y > cellBottom) {
                                y = cellBottom;
                            }
                        }
                        canvas.save();
                        if (tx != 0) {
                            canvas.translate(tx, 0);
                        }
                        if (chatMessageCell.getCurrentMessagesGroup() != null) {
                            if (chatMessageCell.getCurrentMessagesGroup().transitionParams.backgroundChangeBounds) {
                                y -= chatMessageCell.getTranslationY();
                            }
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(44));
                        if (chatMessageCell.shouldDrawAlphaLayer()) {
                            imageReceiver.setAlpha(chatMessageCell.getAlpha());
                            canvas.scale(
                                    chatMessageCell.getScaleX(), chatMessageCell.getScaleY(),
                                    chatMessageCell.getX() + chatMessageCell.getPivotX(), chatMessageCell.getY() + (chatMessageCell.getHeight() >> 1)
                            );
                        } else {
                            imageReceiver.setAlpha(1f);
                        }
                        imageReceiver.setVisible(true, false);
                        imageReceiver.draw(canvas);
                        canvas.restore();
                    }
                }
                return result;
            }
        };
        chatListView.setOnItemClickListener(new RecyclerListView.OnItemClickListenerExtended() {
            @Override
            public void onItemClick(View view, int position, float x, float y) {
                createMenu(view, x, y);
            }
        });
        chatListView.setTag(1);
        chatListView.setVerticalScrollBarEnabled(true);
        chatListView.setAdapter(chatAdapter = new ChatActivityAdapter(context));
        chatListView.setClipToPadding(false);
        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
        chatListView.setItemAnimator(chatListItemAnimator = new ChatListItemAnimator(null, chatListView, null) {

            int scrollAnimationIndex = -1;
            Runnable finishRunnable;

            public void onAnimationStart() {
                if (scrollAnimationIndex == -1) {
                    scrollAnimationIndex = getNotificationCenter().setAnimationInProgress(scrollAnimationIndex, null, false);
                }
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    finishRunnable = null;
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("admin logs chatItemAnimator disable notifications");
                }
            }

            @Override
            protected void onAllAnimationsDone() {
                super.onAllAnimationsDone();
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                }
                AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                    if (scrollAnimationIndex != -1) {
                        getNotificationCenter().onAnimationFinish(scrollAnimationIndex);
                        scrollAnimationIndex = -1;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("admin logs chatItemAnimator enable notifications");
                    }
                });
            }
        });
        chatListItemAnimator.setReversePositions(true);
        chatListView.setLayoutAnimation(null);
        chatLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE);
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        };
        chatLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        chatLayoutManager.setStackFromEnd(true);
        chatListView.setLayoutManager(chatLayoutManager);
        contentView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
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
                updateMessagesVisiblePart();
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
        progressView2.setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(18), progressView2, contentView));
        progressView.addView(progressView2, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(28));
        progressBar.setProgressColor(Theme.getColor(Theme.key_chat_serviceText));
        progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        contentView.addView(actionBar);

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
        contentView.addView(bottomOverlayChat, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomOverlayChat.setOnClickListener(view -> {
            if (getParentActivity() == null) {
                return;
            }
            AdminLogFilterAlert adminLogFilterAlert = new AdminLogFilterAlert(getParentActivity(), currentFilter, selectedAdmins, currentChat.megagroup);
            adminLogFilterAlert.setCurrentAdmins(admins);
            adminLogFilterAlert.setAdminLogFilterAlertDelegate((filter, admins) -> {
                currentFilter = filter;
                selectedAdmins = admins;
                if (currentFilter != null || selectedAdmins != null) {
                    avatarContainer.setSubtitle(LocaleController.getString("EventLogSelectedEvents", R.string.EventLogSelectedEvents));
                } else {
                    avatarContainer.setSubtitle(LocaleController.getString("EventLogAllEvents", R.string.EventLogAllEvents));
                }
                loadMessages(true);
            });
            showDialog(adminLogFilterAlert);
        });

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS).toUpperCase());
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        bottomOverlayImage = new ImageView(context);
        bottomOverlayImage.setImageResource(R.drawable.msg_help);
        bottomOverlayImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_fieldOverlayText), PorterDuff.Mode.MULTIPLY));
        bottomOverlayImage.setScaleType(ImageView.ScaleType.CENTER);
        bottomOverlayChat.addView(bottomOverlayImage, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 3, 0, 0, 0));
        bottomOverlayImage.setContentDescription(LocaleController.getString("BotHelp", R.string.BotHelp));
        bottomOverlayImage.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            if (currentChat.megagroup) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString("EventLogInfoDetail", R.string.EventLogInfoDetail)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString("EventLogInfoDetailChannel", R.string.EventLogInfoDetailChannel)));
            }
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            builder.setTitle(LocaleController.getString("EventLogInfoTitle", R.string.EventLogInfoTitle));
            showDialog(builder.create());
        });

        searchContainer = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        searchContainer.setWillNotDraw(false);
        searchContainer.setVisibility(View.INVISIBLE);
        searchContainer.setFocusable(true);
        searchContainer.setFocusableInTouchMode(true);
        searchContainer.setClickable(true);
        searchContainer.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        contentView.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        /*searchUpButton = new ImageView(context);
        searchUpButton.setScaleType(ImageView.ScaleType.CENTER);
        searchUpButton.setImageResource(R.drawable.msg_go_up);
        searchUpButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchUpButton, LayoutHelper.createFrame(48, 48));
        searchUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MessagesSearchQuery.searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 1);
            }
        });

        searchDownButton = new ImageView(context);
        searchDownButton.setScaleType(ImageView.ScaleType.CENTER);
        searchDownButton.setImageResource(R.drawable.msg_go_down);
        searchDownButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchDownButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 48, 0, 0, 0));
        searchDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MessagesSearchQuery.searchMessagesInChat(null, dialog_id, mergeDialogId, classGuid, 2);
            }
        });*/

        searchCalendarButton = new ImageView(context);
        searchCalendarButton.setScaleType(ImageView.ScaleType.CENTER);
        searchCalendarButton.setImageResource(R.drawable.msg_calendar);
        searchCalendarButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchCalendarButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
        searchCalendarButton.setOnClickListener(view -> {
            if (getParentActivity() == null) {
                return;
            }
            AndroidUtilities.hideKeyboard(searchItem.getSearchField());
            showDialog(AlertsCreator.createCalendarPickerDialog(getParentActivity(), 1375315200000L, param -> loadMessages(true), null).create());
        });

        searchCountText = new SimpleTextView(context);
        searchCountText.setTextColor(Theme.getColor(Theme.key_chat_searchPanelText));
        searchCountText.setTextSize(15);
        searchCountText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        searchContainer.addView(searchCountText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 108, 0, 0, 0));

        chatAdapter.updateRows();
        if (loading && messages.isEmpty()) {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 0.3f, true);
            chatListView.setEmptyView(null);
        } else {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 0.3f, true);
            chatListView.setEmptyView(emptyViewContainer);
        }
        chatListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA_SCALE);

        undoView = new UndoView(context);
        undoView.setAdditionalTranslationY(AndroidUtilities.dp(51));
        contentView.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        updateEmptyPlaceholder();

        return fragmentView;
    }

    private ActionBarPopupWindow scrimPopupWindow;
    private int scrimPopupX, scrimPopupY;
    private void closeMenu() {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }
    }

    private final static int OPTION_COPY = 3;
    private final static int OPTION_SAVE_TO_GALLERY = 4;
    private final static int OPTION_APPLY_FILE = 5;
    private final static int OPTION_SHARE = 6;
    private final static int OPTION_SAVE_TO_GALLERY2 = 7;
    private final static int OPTION_SAVE_STICKER = 9;
    private final static int OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC = 10;
    private final static int OPTION_SAVE_TO_GIFS = 11;
    private final static int OPTION_ADD_CONTACT = 15;
    private final static int OPTION_COPY_PHONE = 16;
    private final static int OPTION_CALL = 17;
    private final static int OPTION_RESTRICT = 33;
    private final static int OPTION_REPORT_FALSE_POSITIVE = 34;

    private boolean createMenu(View v) {
        return createMenu(v, 0, 0);
    }

    private boolean createMenu(View v, float x, float y) {
        MessageObject message = null;
        if (v instanceof ChatMessageCell) {
            message = ((ChatMessageCell) v).getMessageObject();
        } else if (v instanceof ChatActionCell) {
            message = ((ChatActionCell) v).getMessageObject();
        }
        if (message == null) {
            return false;
        }
        final int type = getMessageType(message);
        selectedObject = message;
        if (getParentActivity() == null) {
            return false;
        }

        ArrayList<CharSequence> items = new ArrayList<>();
        final ArrayList<Integer> options = new ArrayList<>();
        final ArrayList<Integer> icons = new ArrayList<>();

        if (message.currentEvent != null && (message.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionDeleteMessage && message.currentEvent.user_id == getMessagesController().telegramAntispamUserId || message.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionToggleAntiSpam)) {
            if (v instanceof ChatActionCell) {
                SpannableString arrow = new SpannableString(">");
                Drawable arrowDrawable = getContext().getResources().getDrawable(R.drawable.attach_arrow_right).mutate();
                arrowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_undo_cancelColor), PorterDuff.Mode.MULTIPLY));
                arrowDrawable.setBounds(0, 0, AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                arrow.setSpan(new ImageSpan(arrowDrawable, DynamicDrawableSpan.ALIGN_CENTER), 0, arrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                SpannableStringBuilder link = new SpannableStringBuilder();
                link
                    .append(LocaleController.getString("EventLogFilterGroupInfo", R.string.EventLogFilterGroupInfo))
                    .append(" ")
                    .append(arrow)
                    .append(" ")
                    .append(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators));
                link.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        finishFragment();
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                    }
                }, 0, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                CharSequence text = LocaleController.getString("ChannelAntiSpamInfo2", R.string.ChannelAntiSpamInfo2);
                text = AndroidUtilities.replaceCharSequence("%s", text, link);
                Bulletin bulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.msg_antispam, LocaleController.getString("ChannelAntiSpamUser", R.string.ChannelAntiSpamUser), text);
                bulletin.setDuration(Bulletin.DURATION_PROLONG);
                bulletin.show();
                return true;
            }
            items.add(LocaleController.getString("ReportFalsePositive", R.string.ReportFalsePositive));
            icons.add(R.drawable.msg_notspam);
            options.add(OPTION_REPORT_FALSE_POSITIVE);
            items.add(null);
            icons.add(null);
            options.add(null);
        }

        if (selectedObject.type == MessageObject.TYPE_TEXT || selectedObject.caption != null) {
            items.add(LocaleController.getString("Copy", R.string.Copy));
            icons.add(R.drawable.msg_copy);
            options.add(OPTION_COPY);
        }
        if (type == 1) {
            if (selectedObject.currentEvent != null && selectedObject.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionChangeStickerSet) {
                TLRPC.TL_channelAdminLogEventActionChangeStickerSet action = (TLRPC.TL_channelAdminLogEventActionChangeStickerSet) selectedObject.currentEvent.action;
                TLRPC.InputStickerSet stickerSet = action.new_stickerset;
                if (stickerSet == null || stickerSet instanceof TLRPC.TL_inputStickerSetEmpty) {
                    stickerSet = action.prev_stickerset;
                }
                if (stickerSet != null) {
                    showDialog(new StickersAlert(getParentActivity(), ChannelAdminLogActivity.this, stickerSet, null, null));
                    return true;
                }
            } else if (selectedObject.currentEvent != null && selectedObject.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionChangeHistoryTTL) {
                if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_DELETE_MESSAGES)) {
                    ClearHistoryAlert alert = new ClearHistoryAlert(getParentActivity(), null, currentChat, false, null);
                    alert.setDelegate(new ClearHistoryAlert.ClearHistoryAlertDelegate() {
                        @Override
                        public void onAutoDeleteHistory(int ttl, int action) {
                            getMessagesController().setDialogHistoryTTL(-currentChat.id, ttl);
                            TLRPC.ChatFull chatInfo = getMessagesController().getChatFull(currentChat.id);
                            if (chatInfo != null) {
                                undoView.showWithAction(-currentChat.id, action, null, chatInfo.ttl_period, null, null);
                            }
                        }
                    });
                    showDialog(alert);
                }
            }
        } else if (type == 3) {
            if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && MessageObject.isNewGifDocument(selectedObject.messageOwner.media.webpage.document)) {
                items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                icons.add(R.drawable.msg_gif);
                options.add(OPTION_SAVE_TO_GIFS);
            }
        } else if (type == 4) {
            if (selectedObject.isVideo()) {
                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                icons.add(R.drawable.msg_gallery);
                options.add(OPTION_SAVE_TO_GALLERY);
                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                icons.add(R.drawable.msg_share);
                options.add(OPTION_SHARE);
            } else if (selectedObject.isMusic()) {
                items.add(LocaleController.getString("SaveToMusic", R.string.SaveToMusic));
                icons.add(R.drawable.msg_download);
                options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                icons.add(R.drawable.msg_share);
                options.add(OPTION_SHARE);
            } else if (selectedObject.getDocument() != null) {
                if (MessageObject.isNewGifDocument(selectedObject.getDocument())) {
                    items.add(LocaleController.getString("SaveToGIFs", R.string.SaveToGIFs));
                    icons.add(R.drawable.msg_gif);
                    options.add(OPTION_SAVE_TO_GIFS);
                }
                items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
                icons.add(R.drawable.msg_download);
                options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
                items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
                icons.add(R.drawable.msg_share);
                options.add(OPTION_SHARE);
            } else {
                items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
                icons.add(R.drawable.msg_gallery);
                options.add(OPTION_SAVE_TO_GALLERY);
            }
        } else if (type == 5) {
            items.add(LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile));
            icons.add(R.drawable.msg_language);
            options.add(OPTION_APPLY_FILE);
            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
            icons.add(R.drawable.msg_share);
            options.add(OPTION_SHARE);
        } else if (type == 10) {
            items.add(LocaleController.getString("ApplyThemeFile", R.string.ApplyThemeFile));
            icons.add(R.drawable.msg_theme);
            options.add(OPTION_APPLY_FILE);
            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
            icons.add(R.drawable.msg_share);
            options.add(OPTION_SHARE);
        } else if (type == 6) {
            items.add(LocaleController.getString("SaveToGallery", R.string.SaveToGallery));
            icons.add(R.drawable.msg_gallery);
            options.add(OPTION_SAVE_TO_GALLERY2);
            items.add(LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
            items.add(LocaleController.getString("ShareFile", R.string.ShareFile));
            icons.add(R.drawable.msg_share);
            options.add(OPTION_SHARE);
        } else if (type == 7) {
            if (selectedObject.isMask()) {
                items.add(LocaleController.getString("AddToMasks", R.string.AddToMasks));
            } else {
                items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
            }
            icons.add(R.drawable.msg_sticker);
            options.add(OPTION_SAVE_STICKER);
        } else if (type == 8) {
            long uid = selectedObject.messageOwner.media.user_id;
            TLRPC.User user = null;
            if (uid != 0) {
                user = MessagesController.getInstance(currentAccount).getUser(uid);
            }
            if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && ContactsController.getInstance(currentAccount).contactsDict.get(user.id) == null) {
                items.add(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
                icons.add(R.drawable.msg_addcontact);
                options.add(OPTION_ADD_CONTACT);
            }
            if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
                items.add(LocaleController.getString("Copy", R.string.Copy));
                icons.add(R.drawable.msg_copy);
                options.add(OPTION_COPY_PHONE);
                items.add(LocaleController.getString("Call", R.string.Call));
                icons.add(R.drawable.msg_calls);
                options.add(OPTION_CALL);
            }
        }

        boolean callbackSent = false;

        Runnable proceed = () -> {
            if (options.isEmpty()) {
                return;
            }

            ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider(), 0);
            popupLayout.setMinimumWidth(AndroidUtilities.dp(200));
            Rect backgroundPaddings = new Rect();
            Drawable shadowDrawable = getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
            shadowDrawable.getPadding(backgroundPaddings);
            popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

            for (int a = 0, N = items.size(); a < N; ++a) {
                if (options.get(a) == null) {
                    popupLayout.addView(new ActionBarPopupWindow.GapView(getContext(), getResourceProvider()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                } else {
                    ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), a == 0, a == N - 1, getResourceProvider());
                    cell.setMinimumWidth(AndroidUtilities.dp(200));
                    cell.setTextAndIcon(items.get(a), icons.get(a));
                    final Integer option = options.get(a);
                    popupLayout.addView(cell);
                    final int i = a;
                    cell.setOnClickListener(v1 -> {
                        if (selectedObject == null || i >= options.size()) {
                            return;
                        }
                        processSelectedOption(option);
                    });
                }
            }

            ChatScrimPopupContainerLayout scrimPopupContainerLayout = new ChatScrimPopupContainerLayout(contentView.getContext()) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                        closeMenu();
                    }
                    return super.dispatchKeyEvent(event);
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    boolean b = super.dispatchTouchEvent(ev);
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && !b) {
                        closeMenu();
                    }
                    return b;
                }
            };
            scrimPopupContainerLayout.addView(popupLayout, LayoutHelper.createLinearRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 0, 0));
            scrimPopupContainerLayout.setPopupWindowLayout(popupLayout);

            scrimPopupWindow = new ActionBarPopupWindow(scrimPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    if (scrimPopupWindow != this) {
                        return;
                    }
                    Bulletin.hideVisible();
                    scrimPopupWindow = null;
                }
            };
            scrimPopupWindow.setPauseNotifications(true);
            scrimPopupWindow.setDismissAnimationDuration(220);
            scrimPopupWindow.setOutsideTouchable(true);
            scrimPopupWindow.setClippingEnabled(true);
            scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            scrimPopupWindow.setFocusable(true);
            scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
            scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
            popupLayout.setFitItems(true);

            int popupX = v.getLeft() + (int) x - scrimPopupContainerLayout.getMeasuredWidth() + backgroundPaddings.left - AndroidUtilities.dp(28);
            if (popupX < AndroidUtilities.dp(6)) {
                popupX = AndroidUtilities.dp(6);
            } else if (popupX > chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - scrimPopupContainerLayout.getMeasuredWidth()) {
                popupX = chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - scrimPopupContainerLayout.getMeasuredWidth();
            }
            if (AndroidUtilities.isTablet()) {
                int[] location = new int[2];
                fragmentView.getLocationInWindow(location);
                popupX += location[0];
            }
            int totalHeight = contentView.getHeight();
            int height = scrimPopupContainerLayout.getMeasuredHeight() + AndroidUtilities.dp(48);
            int keyboardHeight = contentView.measureKeyboardHeight();
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
                popupY = inBubbleMode ? 0 : AndroidUtilities.statusBarHeight;
            }
            final int finalPopupX = scrimPopupX = popupX;
            final int finalPopupY = scrimPopupY = popupY;
            scrimPopupContainerLayout.setMaxHeight(totalHeight - popupY);
            scrimPopupWindow.showAtLocation(chatListView, Gravity.LEFT | Gravity.TOP, finalPopupX, finalPopupY);
            scrimPopupWindow.dimBehind();
        };

        if (
            ChatObject.canBlockUsers(currentChat) &&
            message.currentEvent != null && message.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionDeleteMessage && message.currentEvent.user_id == getMessagesController().telegramAntispamUserId &&
            message.messageOwner != null && message.messageOwner.from_id != null && !UserObject.isUserSelf(getMessagesController().getUser(message.messageOwner.from_id.user_id))
        ) {
            TLRPC.User user = getMessagesController().getUser(selectedObject.messageOwner.from_id.user_id);
            if (user != null) {
                callbackSent = true;
                getMessagesController().checkIsInChat(true, currentChat, user, (isInChat, rights, rank) -> {
                    if (isInChat) {
                        items.add(LocaleController.getString("BanUser", R.string.BanUser));
                        icons.add(R.drawable.msg_block2);
                        options.add(OPTION_RESTRICT);
                    }
                    AndroidUtilities.runOnUIThread(proceed);
                });
            }
        }
        if (!callbackSent) {
            proceed.run();
        }

        return true;
    }

    private CharSequence getMessageContent(MessageObject messageObject, int previousUid, boolean name) {
        SpannableStringBuilder str = new SpannableStringBuilder();
        if (name) {
            long fromId = messageObject.getFromChatId();
            if (previousUid != fromId) {
                if (fromId > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(fromId);
                    if (user != null) {
                        str.append(ContactsController.formatName(user.first_name, user.last_name)).append(":\n");
                    }
                } else if (fromId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-fromId);
                    if (chat != null) {
                        str.append(chat.title).append(":\n");
                    }
                }
            }
        }
        if (TextUtils.isEmpty(messageObject.messageText)) {
            str.append(messageObject.messageOwner.message);
        } else {
            str.append(messageObject.messageText);
        }
        return str;
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

    private void processSelectedOption(int option) {
        closeMenu();
        if (selectedObject == null) {
            return;
        }
        switch (option) {
            case OPTION_COPY: {
                AndroidUtilities.addToClipboard(getMessageContent(selectedObject, 0, true));
                BulletinFactory.of(ChannelAdminLogActivity.this).createCopyBulletin(LocaleController.getString("MessageCopied", R.string.MessageCopied)).show();
                break;
            }
            case OPTION_SAVE_TO_GALLERY: {
                String path = selectedObject.messageOwner.attachPath;
                if (path != null && path.length() > 0) {
                    File temp = new File(path);
                    if (!temp.exists()) {
                        path = null;
                    }
                }
                if (path == null || path.length() == 0) {
                    path = getFileLoader().getPathToMessage(selectedObject.messageOwner).toString();
                }
                if (selectedObject.type == MessageObject.TYPE_VIDEO || selectedObject.type == MessageObject.TYPE_PHOTO) {
                    if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                        selectedObject = null;
                        return;
                    }
                    MediaController.saveFile(path, getParentActivity(), selectedObject.type == MessageObject.TYPE_VIDEO ? 1 : 0, null, null);
                }
                break;
            }
            case OPTION_APPLY_FILE: {
                File locFile = null;
                if (selectedObject.messageOwner.attachPath != null && selectedObject.messageOwner.attachPath.length() != 0) {
                    File f = new File(selectedObject.messageOwner.attachPath);
                    if (f.exists()) {
                        locFile = f;
                    }
                }
                if (locFile == null) {
                    File f = getFileLoader().getPathToMessage(selectedObject.messageOwner);
                    if (f.exists()) {
                        locFile = f;
                    }
                }
                if (locFile != null) {
                    if (locFile.getName().toLowerCase().endsWith("attheme")) {
                        if (chatLayoutManager != null) {
                            int lastPosition = chatLayoutManager.findLastVisibleItemPosition();
                            if (lastPosition < chatLayoutManager.getItemCount() - 1) {
                                scrollToPositionOnRecreate = chatLayoutManager.findFirstVisibleItemPosition();
                                RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                                if (holder != null) {
                                    scrollToOffsetOnRecreate = holder.itemView.getTop();
                                } else {
                                    scrollToPositionOnRecreate = -1;
                                }
                            } else {
                                scrollToPositionOnRecreate = -1;
                            }
                        }
                        Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, selectedObject.getDocumentName(), null, true);
                        if (themeInfo != null) {
                            presentFragment(new ThemePreviewActivity(themeInfo));
                        } else {
                            scrollToPositionOnRecreate = -1;
                            if (getParentActivity() == null) {
                                selectedObject = null;
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
            case OPTION_SHARE: {
                String path = selectedObject.messageOwner.attachPath;
                if (path != null && path.length() > 0) {
                    File temp = new File(path);
                    if (!temp.exists()) {
                        path = null;
                    }
                }
                if (path == null || path.length() == 0) {
                    path = getFileLoader().getPathToMessage(selectedObject.messageOwner).toString();
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(selectedObject.getDocument().mime_type);
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", new File(path)));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
                    }
                } else {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
                }
                try {
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                } catch (Exception e) {

                }
                break;
            }
            case OPTION_SAVE_TO_GALLERY2: {
                String path = selectedObject.messageOwner.attachPath;
                if (path != null && path.length() > 0) {
                    File temp = new File(path);
                    if (!temp.exists()) {
                        path = null;
                    }
                }
                if (path == null || path.length() == 0) {
                    path = getFileLoader().getPathToMessage(selectedObject.messageOwner).toString();
                }
                if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
                    return;
                }
                MediaController.saveFile(path, getParentActivity(), 0, null, null);
                break;
            }
            case OPTION_SAVE_STICKER: {
                showDialog(new StickersAlert(getParentActivity(), this, selectedObject.getInputStickerSet(), null, null));
                break;
            }
            case OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC: {
                if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                    selectedObject = null;
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
                    path = getFileLoader().getPathToMessage(selectedObject.messageOwner).toString();
                }
                MediaController.saveFile(path, getParentActivity(), selectedObject.isMusic() ? 3 : 2, fileName, selectedObject.getDocument() != null ? selectedObject.getDocument().mime_type : "");
                break;
            }
            case OPTION_SAVE_TO_GIFS: {
                TLRPC.Document document = selectedObject.getDocument();
                MessagesController.getInstance(currentAccount).saveGif(selectedObject, document);
                break;
            }
            case OPTION_ADD_CONTACT: {
                Bundle args = new Bundle();
                args.putLong("user_id", selectedObject.messageOwner.media.user_id);
                args.putString("phone", selectedObject.messageOwner.media.phone_number);
                args.putBoolean("addContact", true);
                presentFragment(new ContactAddActivity(args));
                break;
            }
            case OPTION_COPY_PHONE: {
                AndroidUtilities.addToClipboard(selectedObject.messageOwner.media.phone_number);
                BulletinFactory.of(ChannelAdminLogActivity.this).createCopyBulletin(LocaleController.getString("PhoneCopied", R.string.PhoneCopied)).show();
                break;
            }
            case OPTION_CALL: {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + selectedObject.messageOwner.media.phone_number));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getParentActivity().startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case OPTION_REPORT_FALSE_POSITIVE: {
                TLRPC.TL_channels_reportAntiSpamFalsePositive req = new TLRPC.TL_channels_reportAntiSpamFalsePositive();
                req.channel = getMessagesController().getInputChannel(currentChat.id);
                req.msg_id = selectedObject.getRealId();
                getConnectionsManager().sendRequest(req, (res, err) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TLRPC.TL_boolTrue) {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.msg_antispam, LocaleController.getString("ChannelAntiSpamFalsePositiveReported", R.string.ChannelAntiSpamFalsePositiveReported)).show();
                        } else if (res instanceof TLRPC.TL_boolFalse) {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.getString("UnknownError", R.string.UnknownError)).show();
                        } else {
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.getString("UnknownError", R.string.UnknownError)).show();
                        }
                    });
                });
                break;
            }
            case OPTION_RESTRICT: {
                getMessagesController().deleteParticipantFromChat(currentChat.id, getMessagesController().getInputPeer(selectedObject.messageOwner.from_id));
                if (currentChat != null && selectedObject.messageOwner.from_id instanceof TLRPC.TL_peerUser && BulletinFactory.canShowBulletin(this)) {
                    TLRPC.User user = getMessagesController().getUser(selectedObject.messageOwner.from_id.user_id);
                    if (user != null) {
                        BulletinFactory.createRemoveFromChatBulletin(this, user, currentChat.title).show();
                    }
                }
                break;
            }
        }
        selectedObject = null;
    }

    private int getMessageType(MessageObject messageObject) {
        if (messageObject == null) {
            return -1;
        }
        if (messageObject.type == 6) {
            return -1;
        } else if (messageObject.type == 10 || messageObject.type == MessageObject.TYPE_ACTION_PHOTO || messageObject.type == MessageObject.TYPE_PHONE_CALL) {
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
                    if (!MediaDataController.getInstance(currentAccount).isStickerPackInstalled(inputStickerSet.id)) {
                        return 7;
                    }
                } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                    if (!MediaDataController.getInstance(currentAccount).isStickerPackInstalled(inputStickerSet.short_name)) {
                        return 7;
                    }
                }
            } else if ((!messageObject.isRoundVideo() || messageObject.isRoundVideo() && BuildVars.DEBUG_VERSION) && (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.getDocument() != null || messageObject.isMusic() || messageObject.isVideo())) {
                boolean canSave = false;
                if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                    File f = new File(messageObject.messageOwner.attachPath);
                    if (f.exists()) {
                        canSave = true;
                    }
                }
                if (!canSave) {
                    File f = getFileLoader().getPathToMessage(messageObject.messageOwner);
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
            } else if (messageObject.type == MessageObject.TYPE_CONTACT) {
                return 8;
            } else if (messageObject.isMediaEmpty()) {
                return 3;
            }
            return 2;
        }
    }

    private void loadAdmins() {
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInputChannel(currentChat);
        req.filter = new TLRPC.TL_channelParticipantsAdmins();
        req.offset = 0;
        req.limit = 200;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                admins = res.participants;
                if (currentChat != null) {
                    TLRPC.ChatFull chatFull = getMessagesController().getChatFull(currentChat.id);
                    if (chatFull != null && chatFull.antispam) {
                        TLRPC.ChannelParticipant antispamParticipant = new TLRPC.ChannelParticipant() {};
                        antispamParticipant.user_id = getMessagesController().telegramAntispamUserId;
                        antispamParticipant.peer = getMessagesController().getPeer(antispamParticipant.user_id);
                        loadAntispamUser(getMessagesController().telegramAntispamUserId);
                        admins.add(0, antispamParticipant);
                    }
                }
                if (visibleDialog instanceof AdminLogFilterAlert) {
                    ((AdminLogFilterAlert) visibleDialog).setCurrentAdmins(admins);
                }
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    private void loadAntispamUser(long userId) {
        if (getMessagesController().getUser(userId) != null) {
            return;
        }
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = userId;
        req.id.add(inputUser);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.Vector) {
                ArrayList<Object> objects = ((TLRPC.Vector) res).objects;
                ArrayList<TLRPC.User> users = new ArrayList<>();
                for (int i = 0; i < objects.size(); ++i) {
                    if (objects.get(i) instanceof TLRPC.User) {
                        users.add((TLRPC.User) objects.get(i));
                    }
                }
                getMessagesController().putUsers(users, false);
            }
        });
    }

    @Override
    public void onRemoveFromParent() {
        MediaController.getInstance().setTextureView(videoTextureView, null, null, false);
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
            if (firstVisibleItem <= checkLoadCount && !loading && !endReached) {
                loadMessages(false);
            }
        }
    }

    private void moveScrollToLastMessage() {
        if (chatListView != null && !messages.isEmpty()) {
            chatLayoutManager.scrollToPositionWithOffset(messages.size() - 1, -100000 - chatListView.getPaddingTop());
        }
    }

    private void updateTextureViewPosition() {
        boolean foundTextureViewMessage = false;
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + messageCell.getTop() + imageReceiver.getImageY());
                    fragmentView.invalidate();
                    roundVideoContainer.invalidate();
                    foundTextureViewMessage = true;
                    break;
                }
            }
        }
        if (roundVideoContainer != null) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (!foundTextureViewMessage) {
                roundVideoContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                fragmentView.invalidate();
                if (messageObject != null && messageObject.isRoundVideo()) {
                    if (checkTextureViewPosition || PipRoundVideoView.getInstance() != null) {
                        MediaController.getInstance().setCurrentVideoVisible(false);
                    }
                }
            } else {
                MediaController.getInstance().setCurrentVideoVisible(true);
            }
        }
    }

    private void updateMessagesVisiblePart() {
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
                messageCell.setVisiblePart(viewTop, viewBottom - viewTop, contentView.getHeightWithKeyboard() - AndroidUtilities.dp(48) - chatListView.getTop(), 0, view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0);

                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + top + imageReceiver.getImageY());
                    fragmentView.invalidate();
                    roundVideoContainer.invalidate();
                    foundTextureViewMessage = true;
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                cell.setVisiblePart(view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
                if (cell.hasGradientService()) {
                    cell.invalidate();
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
            if (chatListItemAnimator == null || (!chatListItemAnimator.willRemoved(view) && !chatListItemAnimator.willAddedFromAlpha(view))) {
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
        }
        if (roundVideoContainer != null) {
            if (!foundTextureViewMessage) {
                roundVideoContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                fragmentView.invalidate();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isRoundVideo() && checkTextureViewPosition) {
                    MediaController.getInstance().setCurrentVideoVisible(false);
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
            floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
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

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (isOpen) {
            notificationsLocker.lock();
            openAnimationEnded = false;
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            notificationsLocker.unlock();
            openAnimationEnded = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (contentView != null) {
            contentView.onResume();
        }
        paused = false;
        checkScrollForLoad(false);
        if (wasPaused) {
            wasPaused = false;
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (contentView != null) {
            contentView.onPause();
        }
        if (undoView != null) {
            undoView.hide(true, 0);
        }
        paused = true;
        wasPaused = true;
        if (AvatarPreviewer.hasVisibleInstance()) {
            AvatarPreviewer.getInstance().close();
        }
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    public void openVCard(TLRPC.User user, String vcard, String first_name, String last_name) {
        try {
            File f = AndroidUtilities.getSharingDirectory();
            f.mkdirs();
            f = new File(f, "vcard.vcf");
            BufferedWriter writer = new BufferedWriter(new FileWriter(f));
            writer.write(vcard);
            writer.close();
            showDialog(new PhonebookShareAlert(this, null, user, null, f, first_name, last_name));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        if (visibleDialog instanceof DatePickerDialog) {
            visibleDialog.dismiss();
        }
    }

    private void alertUserOpenError(MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        if (message.type == MessageObject.TYPE_VIDEO) {
            builder.setMessage(LocaleController.getString("NoPlayerInstalled", R.string.NoPlayerInstalled));
        } else {
            builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.getDocument().mime_type));
        }
        showDialog(builder.create());
    }

    public TLRPC.Chat getCurrentChat() {
        return currentChat;
    }

    private void addCanBanUser(Bundle bundle, long uid) {
        if (!currentChat.megagroup || admins == null || !ChatObject.canBlockUsers(currentChat)) {
            return;
        }
        for (int a = 0; a < admins.size(); a++) {
            TLRPC.ChannelParticipant channelParticipant = admins.get(a);
            if (MessageObject.getPeerId(channelParticipant.peer) == uid) {
                if (!channelParticipant.can_edit) {
                    return;
                }
                break;
            }
        }
        bundle.putLong("ban_chat_id", currentChat.id);
    }

    public void showOpenUrlAlert(final String url, boolean ask) {
        if (Browser.isInternalUrl(url, null) || !ask) {
            Browser.openUrl(getParentActivity(), url, true);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("OpenUrlTitle", R.string.OpenUrlTitle));
            builder.setMessage(LocaleController.formatString("OpenUrlAlert2", R.string.OpenUrlAlert2, url));
            builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), (dialogInterface, i) -> Browser.openUrl(getParentActivity(), url, true));
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
            chatAdapter.notifyItemRemoved(chatAdapter.messagesStartRow + messages.size() - index - 1);
        }
    }

    public class ChatActivityAdapter extends RecyclerView.Adapter {

        private Context mContext;
        private int rowCount;
        private int loadingUpRow;
        private int messagesStartRow;
        private int messagesEndRow;

        public ChatActivityAdapter(Context context) {
            mContext = context;
        }

        public void updateRows() {
            rowCount = 0;
            if (!messages.isEmpty()) {
                if (!endReached) {
                    loadingUpRow = rowCount++;
                } else {
                    loadingUpRow = -1;
                }
                messagesStartRow = rowCount;
                rowCount += messages.size();
                messagesEndRow = rowCount;
            } else {
                loadingUpRow = -1;
                messagesStartRow = -1;
                messagesEndRow = -1;
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
            View view;
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
                    public boolean canDrawOutboundsContent() {
                        return true;
                    }

                    @Override
                    public void didPressSideButton(ChatMessageCell cell) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        showDialog(ShareAlert.createShareAlert(mContext, cell.getMessageObject(), null, ChatObject.isChannel(currentChat) && !currentChat.megagroup, null, false));
                    }

                    @Override
                    public boolean needPlayMessage(MessageObject messageObject, boolean muted) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject, muted);
                            MediaController.getInstance().setVoiceMessagesPlaylist(null, false);
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(messages, messageObject, 0);
                        }
                        return false;
                    }

                    @Override
                    public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY) {
                        if (chat != null && chat != currentChat) {
                            Bundle args = new Bundle();
                            args.putLong("chat_id", chat.id);
                            if (postId != 0) {
                                args.putInt("message_id", postId);
                            }
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ChannelAdminLogActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    }

                    @Override
                    public void didPressOther(ChatMessageCell cell, float x, float y) {
                        createMenu(cell);
                    }

                    @Override
                    public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
                        if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            openProfile(user);
                        }
                    }

                    @Override
                    public boolean didLongPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
                        if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            final AvatarPreviewer.MenuItem[] menuItems = {AvatarPreviewer.MenuItem.OPEN_PROFILE, AvatarPreviewer.MenuItem.SEND_MESSAGE};
                            final TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
                            final AvatarPreviewer.Data data;
                            if (userFull != null) {
                                data = AvatarPreviewer.Data.of(userFull, menuItems);
                            } else {
                                data = AvatarPreviewer.Data.of(user, classGuid, menuItems);
                            }
                            if (AvatarPreviewer.canPreview(data)) {
                                AvatarPreviewer.getInstance().show((ViewGroup) fragmentView, data, item -> {
                                    switch (item) {
                                        case SEND_MESSAGE:
                                            openDialog(cell, user);
                                            break;
                                        case OPEN_PROFILE:
                                            openProfile(user);
                                            break;
                                    }
                                });
                                return true;
                            }
                        }
                        return false;
                    }

                    private void openProfile(TLRPC.User user) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", user.id);
                        addCanBanUser(args, user.id);
                        ProfileActivity fragment = new ProfileActivity(args);
                        fragment.setPlayProfileAnimation(0);
                        presentFragment(fragment);
                    }

                    private void openDialog(ChatMessageCell cell, TLRPC.User user) {
                        if (user != null) {
                            Bundle args = new Bundle();
                            args.putLong("user_id", user.id);
                            if (getMessagesController().checkCanOpenChat(args, ChannelAdminLogActivity.this)) {
                                presentFragment(new ChatActivity(args));
                            }
                        }
                    }

                    @Override
                    public void didPressCancelSendButton(ChatMessageCell cell) {

                    }

                    @Override
                    public void didLongPress(ChatMessageCell cell, float x, float y) {
                        createMenu(cell);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return true;
                    }

                    @Override
                    public void didPressUrl(ChatMessageCell cell, final CharacterStyle url, boolean longPress) {
                        if (url == null) {
                            return;
                        }
                        MessageObject messageObject = cell.getMessageObject();
                        if (url instanceof URLSpanMono) {
                            ((URLSpanMono) url).copyToClipboard();
                            if (AndroidUtilities.shouldShowClipboardToast()) {
                                Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                            }
                        } else if (url instanceof URLSpanUserMention) {
                            long peerId = Utilities.parseLong(((URLSpanUserMention) url).getURL());
                            if (peerId > 0) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                                if (user != null) {
                                    MessagesController.openChatOrProfileWith(user, null, ChannelAdminLogActivity.this, 0, false);
                                }
                            } else {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                                if (chat != null) {
                                    MessagesController.openChatOrProfileWith(null, chat, ChannelAdminLogActivity.this, 0, false);
                                }
                            }
                        } else if (url instanceof URLSpanNoUnderline) {
                            String str = ((URLSpanNoUnderline) url).getURL();
                            if (str.startsWith("@")) {
                                MessagesController.getInstance(currentAccount).openByUserName(str.substring(1), ChannelAdminLogActivity.this, 0);
                            } else if (str.startsWith("#")) {
                                DialogsActivity fragment = new DialogsActivity(null);
                                fragment.setSearchString(str);
                                presentFragment(fragment);
                            }
                        } else {
                            final String urlFinal = ((URLSpan) url).getURL();
                            if (longPress) {
                                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                                builder.setTitle(urlFinal);
                                builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
                                    if (which == 0) {
                                        Browser.openUrl(getParentActivity(), urlFinal, true);
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
                                if (url instanceof URLSpanReplacement) {
                                    showOpenUrlAlert(((URLSpanReplacement) url).getURL(), true);
                                } else {
                                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                        String lowerUrl = urlFinal.toLowerCase();
                                        String lowerUrl2 = messageObject.messageOwner.media.webpage.url.toLowerCase();
                                        if ((Browser.isTelegraphUrl(lowerUrl, false) || lowerUrl.contains("t.me/iv")) && (lowerUrl.contains(lowerUrl2) || lowerUrl2.contains(lowerUrl))) {
                                            ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChannelAdminLogActivity.this);
                                            ArticleViewer.getInstance().open(messageObject);
                                            return;
                                        }
                                    }
                                    Browser.openUrl(getParentActivity(), urlFinal, true);
                                }
                            }
                        }
                    }

                    @Override
                    public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                        EmbedBottomSheet.show(ChannelAdminLogActivity.this, message, provider, title, description, originalUrl, url, w, h, false);
                    }

                    @Override
                    public void didPressReplyMessage(ChatMessageCell cell, int id) {

                    }

                    @Override
                    public void didPressViaBot(ChatMessageCell cell, String username) {

                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell, float x, float y) {
                        MessageObject message = cell.getMessageObject();
                        if (message.getInputStickerSet() != null) {
                            showDialog(new StickersAlert(getParentActivity(), ChannelAdminLogActivity.this, message.getInputStickerSet(), null, null));
                        } else if (message.isVideo() || message.type == MessageObject.TYPE_PHOTO || message.type == MessageObject.TYPE_TEXT && !message.isWebpageDocument() || message.isGif()) {
                            PhotoViewer.getInstance().setParentActivity(ChannelAdminLogActivity.this);
                            PhotoViewer.getInstance().openPhoto(message, null, 0, 0, 0, provider);
                        } else if (message.type == MessageObject.TYPE_VIDEO) {
                            try {
                                File f = null;
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    f = new File(message.messageOwner.attachPath);
                                }
                                if (f == null || !f.exists()) {
                                    f = getFileLoader().getPathToMessage(message.messageOwner);
                                }
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                if (Build.VERSION.SDK_INT >= 24) {
                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", f), "video/mp4");
                                } else {
                                    intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                }
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                alertUserOpenError(message);
                            }
                        } else if (message.type == MessageObject.TYPE_GEO) {
                            if (!AndroidUtilities.isMapsInstalled(ChannelAdminLogActivity.this)) {
                                return;
                            }
                            LocationActivity fragment = new LocationActivity(0);
                            fragment.setMessageObject(message);
                            presentFragment(fragment);
                        } else if (message.type == MessageObject.TYPE_FILE || message.type == MessageObject.TYPE_TEXT) {
                            if (message.getDocumentName().toLowerCase().endsWith("attheme")) {
                                File locFile = null;
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    File f = new File(message.messageOwner.attachPath);
                                    if (f.exists()) {
                                        locFile = f;
                                    }
                                }
                                if (locFile == null) {
                                    File f = getFileLoader().getPathToMessage(message.messageOwner);
                                    if (f.exists()) {
                                        locFile = f;
                                    }
                                }
                                if (chatLayoutManager != null) {
                                    int lastPosition = chatLayoutManager.findLastVisibleItemPosition();
                                    if (lastPosition < chatLayoutManager.getItemCount() - 1) {
                                        scrollToPositionOnRecreate = chatLayoutManager.findFirstVisibleItemPosition();
                                        RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                                        if (holder != null) {
                                            scrollToOffsetOnRecreate = holder.itemView.getTop();
                                        } else {
                                            scrollToPositionOnRecreate = -1;
                                        }
                                    } else {
                                        scrollToPositionOnRecreate = -1;
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
                            try {
                                AndroidUtilities.openForView(message, getParentActivity(), null);
                            } catch (Exception e) {
                                alertUserOpenError(message);
                            }
                        }
                    }

                    @Override
                    public void didPressInstantButton(ChatMessageCell cell, int type) {
                        MessageObject messageObject = cell.getMessageObject();
                        if (type == 0) {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChannelAdminLogActivity.this);
                                ArticleViewer.getInstance().open(messageObject);
                            }
                        } else if (type == 5) {
                            openVCard(getMessagesController().getUser(messageObject.messageOwner.media.user_id), messageObject.messageOwner.media.vcard, messageObject.messageOwner.media.first_name, messageObject.messageOwner.media.last_name);
                        } else {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null) {
                                Browser.openUrl(getParentActivity(), messageObject.messageOwner.media.webpage.url);
                            }
                        }
                    }
                });
                chatMessageCell.setAllowAssistant(true);
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext) {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(info);
                        // if alpha == 0, then visibleToUser == false, so we need to override it
                        // to keep accessibility working correctly
                        info.setVisibleToUser(true);
                    }
                };
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickImage(ChatActionCell cell) {
                        MessageObject message = cell.getMessageObject();
                        PhotoViewer.getInstance().setParentActivity(ChannelAdminLogActivity.this);
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 640);
                        if (photoSize != null) {
                            ImageLocation imageLocation = ImageLocation.getForPhoto(photoSize, message.messageOwner.action.photo);
                            PhotoViewer.getInstance().openPhoto(photoSize.location, imageLocation, provider);
                        } else {
                            PhotoViewer.getInstance().openPhoto(message, null, 0, 0, 0, provider);
                        }
                    }

                    @Override
                    public boolean didLongPress(ChatActionCell cell, float x, float y) {
                        return createMenu(cell);
                    }

                    @Override
                    public void needOpenUserProfile(long uid) {
                        if (uid < 0) {
                            Bundle args = new Bundle();
                            args.putLong("chat_id", -uid);
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ChannelAdminLogActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        } else if (uid != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putLong("user_id", uid);
                            addCanBanUser(args, uid);
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(0);
                            presentFragment(fragment);
                        }
                    }

                    public void needOpenInviteLink(final TLRPC.TL_chatInviteExported invite) {
                        if (linviteLoading) {
                            return;
                        }
                        Object cachedInvite = invitesCache.containsKey(invite.link) ? invitesCache.get(invite.link) : null;
                        if (cachedInvite == null) {
                            TLRPC.TL_messages_getExportedChatInvite req = new TLRPC.TL_messages_getExportedChatInvite();
                            req.peer = getMessagesController().getInputPeer(-currentChat.id);
                            req.link = invite.link;

                            linviteLoading = true;

                            boolean[] canceled = new boolean[1];
                            final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                            progressDialog.setOnCancelListener(dialogInterface -> {
                                linviteLoading = false;
                                canceled[0] = true;
                            });
                            progressDialog.showDelayed(300);

                            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                                TLRPC.TL_messages_exportedChatInvite resInvite = null;
                                if (error == null) {
                                    resInvite = (TLRPC.TL_messages_exportedChatInvite) response;
                                    for (int i = 0; i < resInvite.users.size(); i++) {
                                        TLRPC.User user = resInvite.users.get(i);
                                        if (usersMap == null) {
                                            usersMap = new HashMap<>();
                                        }
                                        usersMap.put(user.id, user);
                                    }
                                }

                                TLRPC.TL_messages_exportedChatInvite finalInvite = resInvite;
                                AndroidUtilities.runOnUIThread(() -> {
                                    linviteLoading = false;
                                    invitesCache.put(invite.link, finalInvite == null ? 0 : finalInvite);
                                    if (canceled[0]) {
                                        return;
                                    }
                                    progressDialog.dismiss();
                                    if (finalInvite != null) {
                                        showInviteLinkBottomSheet(finalInvite, usersMap);
                                    } else {
                                        BulletinFactory.of(ChannelAdminLogActivity.this).createSimpleBulletin(R.raw.linkbroken, LocaleController.getString("LinkHashExpired", R.string.LinkHashExpired)).show();
                                    }
                                });
                            });
                            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
                        } else if (cachedInvite instanceof TLRPC.TL_messages_exportedChatInvite) {
                            showInviteLinkBottomSheet((TLRPC.TL_messages_exportedChatInvite) cachedInvite, usersMap);
                        } else {
                            BulletinFactory.of(ChannelAdminLogActivity.this).createSimpleBulletin(R.raw.linkbroken, LocaleController.getString("LinkHashExpired", R.string.LinkHashExpired)).show();
                        }

                    }

                    @Override
                    public BaseFragment getBaseFragment() {
                        return ChannelAdminLogActivity.this;
                    }

                    @Override
                    public long getDialogId() {
                        return -currentChat.id;
                    }

                    @Override
                    public void didPressReplyMessage(ChatActionCell cell, int id) {

                    }

                    @Override
                    public void didPressBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {

                    }
                });
            } else if (viewType == 2) {
                view = new ChatUnreadCell(mContext, null);
            } else {
                view = new ChatLoadingCell(mContext, contentView, null);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position == loadingUpRow) {
                ChatLoadingCell loadingCell = (ChatLoadingCell) holder.itemView;
                loadingCell.setProgressVisible(loadsCount > 1);
            } else if (position >= messagesStartRow && position < messagesEndRow) {
                MessageObject message = messages.get(messages.size() - (position - messagesStartRow) - 1);
                View view = holder.itemView;

                if (view instanceof ChatMessageCell) {
                    final ChatMessageCell messageCell = (ChatMessageCell) view;
                    messageCell.isChat = true;
                    int nextType = getItemViewType(position + 1);
                    int prevType = getItemViewType(position - 1);
                    boolean pinnedBotton;
                    boolean pinnedTop;
                    if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                        MessageObject nextMessage = messages.get(messages.size() - (position + 1 - messagesStartRow) - 1);
                        pinnedBotton = nextMessage.isOutOwner() == message.isOutOwner() && (nextMessage.getFromChatId() == message.getFromChatId()) && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedBotton = false;
                    }
                    if (prevType == holder.getItemViewType()) {
                        MessageObject prevMessage = messages.get(messages.size() - (position - messagesStartRow));
                        pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && (prevMessage.getFromChatId() == message.getFromChatId()) && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedTop = false;
                    }
                    messageCell.setMessageObject(message, null, pinnedBotton, pinnedTop);
                    messageCell.setHighlighted(false);
                    messageCell.setHighlightedText(null);
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell actionCell = (ChatActionCell) view;
                    actionCell.setMessageObject(message);
                    actionCell.setAlpha(1.0f);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= messagesStartRow && position < messagesEndRow) {
                return messages.get(messages.size() - (position - messagesStartRow) - 1).contentType;
            }
            return 4;
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ChatMessageCell || holder.itemView instanceof ChatActionCell) {
                View view = holder.itemView;
                holder.itemView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        view.getViewTreeObserver().removeOnPreDrawListener(this);

                        int height = chatListView.getMeasuredHeight();
                        int top = view.getTop();
                        int bottom = view.getBottom();
                        int viewTop = top >= 0 ? 0 : -top;
                        int viewBottom = view.getMeasuredHeight();
                        if (viewBottom > height) {
                            viewBottom = viewTop + height;
                        }

                        if (holder.itemView instanceof ChatMessageCell) {
                            ((ChatMessageCell) view).setVisiblePart(viewTop, viewBottom - viewTop, contentView.getHeightWithKeyboard() - AndroidUtilities.dp(48) - chatListView.getTop(), 0, view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0);
                        } else if (holder.itemView instanceof ChatActionCell) {
                            if (actionBar != null && contentView != null) {
                                ((ChatActionCell) view).setVisiblePart(view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
                            }
                        }

                        return true;
                    }
                });
            }
            if (holder.itemView instanceof ChatMessageCell) {
                final ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
                MessageObject message = messageCell.getMessageObject();

                messageCell.setBackgroundDrawable(null);
                messageCell.setCheckPressed(true, false);

                messageCell.setHighlighted(false);
            }
        }

        public void updateRowWithMessageObject(MessageObject messageObject) {
            int index = messages.indexOf(messageObject);
            if (index == -1) {
                return;
            }
            notifyItemChanged(messagesStartRow + messages.size() - index - 1);
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

    private void showInviteLinkBottomSheet(TLRPC.TL_messages_exportedChatInvite invite, HashMap<Long, TLRPC.User> usersMap) {
        TLRPC.ChatFull chatInfo = getMessagesController().getChatFull(currentChat.id);
        InviteLinkBottomSheet inviteLinkBottomSheet = new InviteLinkBottomSheet(contentView.getContext(), (TLRPC.TL_chatInviteExported) invite.invite, chatInfo, usersMap, ChannelAdminLogActivity.this, chatInfo.id, false, ChatObject.isChannel(currentChat));
        inviteLinkBottomSheet.setInviteDelegate(new InviteLinkBottomSheet.InviteDelegate() {

            @Override
            public void permanentLinkReplaced(TLRPC.TL_chatInviteExported oldLink, TLRPC.TL_chatInviteExported newLink) {

            }

            @Override
            public void linkRevoked(TLRPC.TL_chatInviteExported invite) {
                TLRPC.TL_channelAdminLogEvent event = new TLRPC.TL_channelAdminLogEvent();
                int size = messages.size();
                invite.revoked = true;
                TLRPC.TL_channelAdminLogEventActionExportedInviteRevoke revokeAction = new TLRPC.TL_channelAdminLogEventActionExportedInviteRevoke();
                revokeAction.invite = invite;
                event.action = revokeAction;
                event.date = (int) (System.currentTimeMillis() / 1000L);
                event.user_id = getAccountInstance().getUserConfig().clientUserId;
                MessageObject messageObject = new MessageObject(currentAccount, event, messages, messagesByDays, currentChat, mid, true);
                if (messageObject.contentType < 0) {
                    return;
                }
                int addCount = messages.size() - size;
                if (addCount > 0) {
                    chatListItemAnimator.setShouldAnimateEnterFromBottom(true);
                    chatAdapter.notifyItemRangeInserted(chatAdapter.messagesEndRow, addCount);
                    moveScrollToLastMessage();
                }
                invitesCache.remove(invite.link);
            }

            @Override
            public void onLinkDeleted(TLRPC.TL_chatInviteExported invite) {
                int size = messages.size();
                int messagesEndRow = chatAdapter.messagesEndRow;
                TLRPC.TL_channelAdminLogEvent event = new TLRPC.TL_channelAdminLogEvent();
                TLRPC.TL_channelAdminLogEventActionExportedInviteDelete deleteAction = new TLRPC.TL_channelAdminLogEventActionExportedInviteDelete();
                deleteAction.invite = invite;
                event.action = deleteAction;
                event.date = (int) (System.currentTimeMillis() / 1000L);
                event.user_id = getAccountInstance().getUserConfig().clientUserId;
                MessageObject messageObject = new MessageObject(currentAccount, event, messages, messagesByDays, currentChat, mid, true);
                if (messageObject.contentType < 0) {
                    return;
                }
                int addCount = messages.size() - size;
                if (addCount > 0) {
                    chatListItemAnimator.setShouldAnimateEnterFromBottom(true);
                    chatAdapter.notifyItemRangeInserted(chatAdapter.messagesEndRow, addCount);
                    moveScrollToLastMessage();
                }

                invitesCache.remove(invite.link);
            }

            @Override
            public void onLinkEdited(TLRPC.TL_chatInviteExported invite) {
                TLRPC.TL_channelAdminLogEvent event = new TLRPC.TL_channelAdminLogEvent();
                TLRPC.TL_channelAdminLogEventActionExportedInviteEdit editAction = new TLRPC.TL_channelAdminLogEventActionExportedInviteEdit();
                editAction.new_invite = invite;
                editAction.prev_invite = invite;
                event.action = editAction;
                event.date = (int) (System.currentTimeMillis() / 1000L);
                event.user_id = getAccountInstance().getUserConfig().clientUserId;
                MessageObject messageObject = new MessageObject(currentAccount, event, messages, messagesByDays, currentChat, mid, true);
                if (messageObject.contentType < 0) {
                    return;
                }
                chatAdapter.notifyDataSetChanged();
                moveScrollToLastMessage();
            }

        });
        inviteLinkBottomSheet.show();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(avatarContainer.getTitleTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(avatarContainer.getSubtitleTextView(), ThemeDescription.FLAG_TEXTCOLOR, null, new Paint[]{Theme.chat_statusPaint, Theme.chat_statusRecordPaint}, null, null, Theme.key_actionBarDefaultSubtitle, null));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_backgroundPink));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageRed));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageOrange));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageViolet));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageGreen));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageCyan));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessageBlue));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_avatar_nameInMessagePink));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgInDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgInMediaDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgOutDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgOutMediaDrawable.getShadowDrawables(), null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient1));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient2));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient3));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatActionCell.class}, Theme.chat_actionTextPaint, null, null, Theme.key_chat_serviceText));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatActionCell.class}, Theme.chat_actionTextPaint, null, null, Theme.key_chat_serviceLink));

        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_botCardDrawable, Theme.chat_shareIconDrawable, Theme.chat_botInlineDrawable, Theme.chat_botLinkDrawable, Theme.chat_goIconDrawable, Theme.chat_commentStickerDrawable}, null, Theme.key_chat_serviceIcon));

        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, ChatActionCell.class}, null, null, null, Theme.key_chat_serviceBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class, ChatActionCell.class}, null, null, null, Theme.key_chat_serviceBackgroundSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextIn));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextOut));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageLinkIn, null));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageLinkOut, null));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutViewsDrawable, Theme.chat_msgOutRepliesDrawable, Theme.chat_msgOutPinnedDrawable}, null, Theme.key_chat_outViews));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutViewsSelectedDrawable, Theme.chat_msgOutRepliesSelectedDrawable, Theme.chat_msgOutPinnedSelectedDrawable}, null, Theme.key_chat_outViewsSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInViewsDrawable, Theme.chat_msgInRepliesDrawable, Theme.chat_msgInPinnedDrawable}, null, Theme.key_chat_inViews));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInViewsSelectedDrawable, Theme.chat_msgInRepliesSelectedDrawable, Theme.chat_msgInPinnedSelectedDrawable}, null, Theme.key_chat_inViewsSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaViewsDrawable, Theme.chat_msgMediaRepliesDrawable, Theme.chat_msgMediaPinnedDrawable}, null, Theme.key_chat_mediaViews));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutMenuDrawable}, null, Theme.key_chat_outMenu));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutMenuSelectedDrawable}, null, Theme.key_chat_outMenuSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInMenuDrawable}, null, Theme.key_chat_inMenu));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInMenuSelectedDrawable}, null, Theme.key_chat_inMenuSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaMenuDrawable}, null, Theme.key_chat_mediaMenu));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutInstantDrawable}, null, Theme.key_chat_outInstant));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInInstantDrawable, Theme.chat_commentDrawable, Theme.chat_commentArrowDrawable}, null, Theme.key_chat_inInstant));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgOutCallDrawable, null, Theme.key_chat_outInstant));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgOutCallSelectedDrawable, null, Theme.key_chat_outInstantSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgInCallDrawable, null, Theme.key_chat_inInstant));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, Theme.chat_msgInCallSelectedDrawable, null, Theme.key_chat_inInstantSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallUpGreenDrawable}, null, Theme.key_chat_outGreenCall));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallDownRedDrawable}, null, Theme.key_fill_RedNormal));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgCallDownGreenDrawable}, null, Theme.key_chat_inGreenCall));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_msgErrorPaint, null, null, Theme.key_chat_sentError));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgErrorDrawable}, null, Theme.key_chat_sentErrorIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_durationPaint, null, null, Theme.key_chat_previewDurationText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_gamePaint, null, null, Theme.key_chat_previewGameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inPreviewInstantText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outPreviewInstantText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_deleteProgressPaint, null, null, Theme.key_chat_secretTimeText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_botButtonPaint, null, null, Theme.key_chat_botButtonText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inForwardedNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outForwardedNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inViaBotNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outViaBotNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerViaBotNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyLine2));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerReplyLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerReplyNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMessageText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMessageText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_stickerReplyMessageText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inPreviewLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outPreviewLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inSiteNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outSiteNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inContactNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outContactNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inContactPhoneText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outContactPhoneText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSelectedProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSelectedProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaTimeText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioPerformerText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioPerformerText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioTitleText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioTitleText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioDurationText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioDurationText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioDurationSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioDurationSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSeekbar));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbar));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSeekbarSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbarSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioSeekbarFill));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inAudioCacheSeekbar));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioSeekbarFill));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outAudioCacheSeekbar));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVoiceSeekbar));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVoiceSeekbar));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVoiceSeekbarSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVoiceSeekbarSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVoiceSeekbarFill));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVoiceSeekbarFill));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileProgress));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileProgressSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileProgressSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileNameText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileInfoText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileInfoText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileInfoSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileInfoSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inFileBackgroundSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outFileBackgroundSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueInfoText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueInfoText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inVenueInfoSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outVenueInfoSelectedText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_mediaInfoText));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_urlPaint, null, null, Theme.key_chat_inReplyLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, Theme.chat_textSearchSelectionPaint, null, null, Theme.key_chat_outReplyLine));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outLoader));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outMediaIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outLoaderSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outMediaIconSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inLoader));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inMediaIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inLoaderSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inMediaIconSelected));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[0]}, null, Theme.key_chat_inContactBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[0]}, null, Theme.key_chat_inContactIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[1]}, null, Theme.key_chat_outContactBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_contactDrawable[1]}, null, Theme.key_chat_outContactIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inLocationBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_locationDrawable[0]}, null, Theme.key_chat_inLocationIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_locationDrawable[1]}, null, Theme.key_chat_outLocationIcon));

        themeDescriptions.add(new ThemeDescription(bottomOverlayChat, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
        themeDescriptions.add(new ThemeDescription(bottomOverlayChat, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));

        themeDescriptions.add(new ThemeDescription(bottomOverlayChatText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_serviceText));

        themeDescriptions.add(new ThemeDescription(progressBar, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_chat_serviceText));

        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{ChatUnreadCell.class}, new String[]{"backgroundLayout"}, null, null, null, Theme.key_chat_unreadMessagesStartBackground));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatUnreadCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chat_unreadMessagesStartArrowIcon));
        themeDescriptions.add(new ThemeDescription(chatListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatUnreadCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_unreadMessagesStartText));

        themeDescriptions.add(new ThemeDescription(progressView2, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground));

        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));

        return themeDescriptions;
    }
}
