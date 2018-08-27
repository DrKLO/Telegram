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
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.LinearSmoothScrollerMiddle;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.BotHelpCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatLoadingCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatUnreadCell;
import org.telegram.ui.Components.AdminLogFilterAlert;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class ChannelAdminLogActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    protected TLRPC.Chat currentChat;

    private ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();

    private FrameLayout progressView;
    private View progressView2;
    private RadialProgressView progressBar;
    private RecyclerListView chatListView;
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
    private SparseArray<TLRPC.User> selectedAdmins;

    private MessageObject scrollToMessage;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

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
                    object.thumb = imageReceiver.getBitmapSafe();
                    object.radius = imageReceiver.getRoundRadius();
                    object.isEvent = true;
                    return object;
                }
            }
            return null;
        }
    };

    public ChannelAdminLogActivity(TLRPC.Chat chat) {
        currentChat = chat;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStarted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        loadMessages(true);
        loadAdmins();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStarted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
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
                progressView.setVisibility(View.VISIBLE);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    final TLRPC.TL_channels_adminLogResults res = (TLRPC.TL_channels_adminLogResults) response;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            boolean added = false;
                            int oldRowsCount = messages.size();
                            for (int a = 0; a < res.events.size(); a++) {
                                TLRPC.TL_channelAdminLogEvent event = res.events.get(a);
                                if (messagesDict.indexOfKey(event.id) >= 0) {
                                    continue;
                                }
                                minEventId = Math.min(minEventId, event.id);
                                added = true;
                                MessageObject messageObject = new MessageObject(currentAccount, event, messages, messagesByDays, currentChat, mid);
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
                            progressView.setVisibility(View.INVISIBLE);
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
                        }
                    });
                }
            }
        });
        if (reset && chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.messagePlayingDidStarted) {
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
                                cell.updateButtonState(false, false);
                            } else if (messageObject1.isRoundVideo()) {
                                cell.checkRoundVideoPlayback(false);
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
                                cell.updateButtonState(false, false);
                            } else if (messageObject.isRoundVideo()) {
                                if (!MediaController.getInstance().isPlayingMessage(messageObject)) {
                                    cell.checkRoundVideoPlayback(true);
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
                ((SizeNotifierFrameLayout) fragmentView).setBackgroundImage(Theme.getCachedWallpaper());
                progressView2.getBackground().setColorFilter(Theme.colorFilter);
                if (emptyView != null) {
                    emptyView.getBackground().setColorFilter(Theme.colorFilter);
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
        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

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

                int keyboardSize = getKeyboardHeight();

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
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                updateMessagesVisisblePart();
                notifyHeightChanged();
            }
        };

        contentView = (SizeNotifierFrameLayout) fragmentView;

        contentView.setOccupyStatusBar(!AndroidUtilities.isTablet());
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


        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.getServiceMessageColor()));
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
                        int top = child.getTop();
                        if (chatMessageCell.isPinnedBottom()) {
                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            if (holder != null) {
                                holder = chatListView.findViewHolderForAdapterPosition(holder.getAdapterPosition() + 1);
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
                                    holder = chatListView.findViewHolderForAdapterPosition(holder.getAdapterPosition() - 1);
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
                        int maxY = chatListView.getHeight() - chatListView.getPaddingBottom();
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
        chatListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                createMenu(view);
            }
        });
        chatListView.setTag(1);
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

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerMiddle linearSmoothScroller = new LinearSmoothScrollerMiddle(recyclerView.getContext());
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
        bottomOverlayChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getParentActivity() == null) {
                    return;
                }
                AdminLogFilterAlert adminLogFilterAlert = new AdminLogFilterAlert(getParentActivity(), currentFilter, selectedAdmins, currentChat.megagroup);
                adminLogFilterAlert.setCurrentAdmins(admins);
                adminLogFilterAlert.setAdminLogFilterAlertDelegate(new AdminLogFilterAlert.AdminLogFilterAlertDelegate() {
                    @Override
                    public void didSelectRights(TLRPC.TL_channelAdminLogEventsFilter filter, SparseArray<TLRPC.User> admins) {
                        currentFilter = filter;
                        selectedAdmins = admins;
                        if (currentFilter != null || selectedAdmins != null) {
                            avatarContainer.setSubtitle(LocaleController.getString("EventLogSelectedEvents", R.string.EventLogSelectedEvents));
                        } else {
                            avatarContainer.setSubtitle(LocaleController.getString("EventLogAllEvents", R.string.EventLogAllEvents));
                        }
                        loadMessages(true);
                    }
                });
                showDialog(adminLogFilterAlert);
            }
        });

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS).toUpperCase());
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        bottomOverlayImage = new ImageView(context);
        bottomOverlayImage.setImageResource(R.drawable.log_info);
        bottomOverlayImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_fieldOverlayText), PorterDuff.Mode.MULTIPLY));
        bottomOverlayImage.setScaleType(ImageView.ScaleType.CENTER);
        bottomOverlayChat.addView(bottomOverlayImage, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 3, 0, 0, 0));
        bottomOverlayImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (currentChat.megagroup) {
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString("EventLogInfoDetail", R.string.EventLogInfoDetail)));
                } else {
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString("EventLogInfoDetailChannel", R.string.EventLogInfoDetailChannel)));
                }
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.setTitle(LocaleController.getString("EventLogInfoTitle", R.string.EventLogInfoTitle));
                showDialog(builder.create());
            }
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
        searchUpButton.setImageResource(R.drawable.search_up);
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
        searchDownButton.setImageResource(R.drawable.search_down);
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
        searchCalendarButton.setImageResource(R.drawable.search_calendar);
        searchCalendarButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons), PorterDuff.Mode.MULTIPLY));
        searchContainer.addView(searchCalendarButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
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
                            loadMessages(true);
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
        searchContainer.addView(searchCountText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 108, 0, 0, 0));

        chatAdapter.updateRows();
        if (loading && messages.isEmpty()) {
            progressView.setVisibility(View.VISIBLE);
            chatListView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.INVISIBLE);
            chatListView.setEmptyView(emptyViewContainer);
        }

        updateEmptyPlaceholder();

        return fragmentView;
    }

    private void createMenu(View v) {
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
        selectedObject = message;
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

        ArrayList<CharSequence> items = new ArrayList<>();
        final ArrayList<Integer> options = new ArrayList<>();

        if (selectedObject.type == 0 || selectedObject.caption != null) {
            items.add(LocaleController.getString("Copy", R.string.Copy));
            options.add(3);
        }
        if (type == 1) {
            if (selectedObject.currentEvent != null && selectedObject.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionChangeStickerSet) {
                TLRPC.InputStickerSet stickerSet = selectedObject.currentEvent.action.new_stickerset;
                if (stickerSet == null || stickerSet instanceof TLRPC.TL_inputStickerSetEmpty) {
                    stickerSet = selectedObject.currentEvent.action.prev_stickerset;
                }
                if (stickerSet != null) {
                    showDialog(new StickersAlert(getParentActivity(), ChannelAdminLogActivity.this, stickerSet, null, null));
                    return;
                }
            }
        } else if (type == 3) {
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
            } else {
                items.add(LocaleController.getString("AddToStickers", R.string.AddToStickers));
            }
            options.add(9);
        } else if (type == 8) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(selectedObject.messageOwner.media.user_id);
            if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && ContactsController.getInstance(currentAccount).contactsDict.get(user.id) == null) {
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

    private String getMessageContent(MessageObject messageObject, int previousUid, boolean name) {
        String str = "";
        if (name) {
            if (previousUid != messageObject.messageOwner.from_id) {
                if (messageObject.messageOwner.from_id > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.from_id);
                    if (user != null) {
                        str = ContactsController.formatName(user.first_name, user.last_name) + ":\n";
                    }
                } else if (messageObject.messageOwner.from_id < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-messageObject.messageOwner.from_id);
                    if (chat != null) {
                        str = chat.title + ":\n";
                    }
                }
            }
        }
        if (messageObject.type == 0 && messageObject.messageOwner.message != null) {
            str += messageObject.messageOwner.message;
        } else if (messageObject.messageOwner.media != null && messageObject.messageOwner.message != null) {
            str += messageObject.messageOwner.message;
        } else {
            str += messageObject.messageText;
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
        if (selectedObject == null) {
            return;
        }
        switch (option) {
            case 3: {
                AndroidUtilities.addToClipboard(getMessageContent(selectedObject, 0, true));
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
                        Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, selectedObject.getDocumentName(), true);
                        if (themeInfo != null) {
                            presentFragment(new ThemePreviewActivity(locFile, themeInfo));
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
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", new File(path)));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
                    }
                } else {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
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
                    return;
                }
                MediaController.saveFile(path, getParentActivity(), 0, null, null);
                break;
            }
            case 9: {
                showDialog(new StickersAlert(getParentActivity(), this, selectedObject.getInputStickerSet(), null, null));
                break;
            }
            case 10: {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
                    path = FileLoader.getPathToMessage(selectedObject.messageOwner).toString();
                }
                MediaController.saveFile(path, getParentActivity(), selectedObject.isMusic() ? 3 : 2, fileName, selectedObject.getDocument() != null ? selectedObject.getDocument().mime_type : "");
                break;
            }
            case 11: {
                TLRPC.Document document = selectedObject.getDocument();
                MessagesController.getInstance(currentAccount).saveGif(document);
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
        }
        selectedObject = null;
    }

    private int getMessageType(MessageObject messageObject) {
        if (messageObject == null) {
            return -1;
        }
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
                    if (!DataQuery.getInstance(currentAccount).isStickerPackInstalled(inputStickerSet.id)) {
                        return 7;
                    }
                } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                    if (!DataQuery.getInstance(currentAccount).isStickerPackInstalled(inputStickerSet.short_name)) {
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

    private void loadAdmins() {
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInputChannel(currentChat);
        req.filter = new TLRPC.TL_channelParticipantsAdmins();
        req.offset = 0;
        req.limit = 200;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            admins = res.participants;
                            if (visibleDialog instanceof AdminLogFilterAlert) {
                                ((AdminLogFilterAlert) visibleDialog).setCurrentAdmins(admins);
                            }
                        }
                    }
                });
            }
        });
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    @Override
    protected void onRemoveFromParent() {
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
                        MediaController.getInstance().setCurrentRoundVisible(false);
                    }
                }
            } else {
                MediaController.getInstance().setCurrentRoundVisible(true);
            }
        }
    }

    private void updateMessagesVisisblePart() {
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
                messageCell.setVisiblePart(viewTop, viewBottom - viewTop);

                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + top + imageReceiver.getImageY());
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
                if (messageObject != null && messageObject.isRoundVideo() && checkTextureViewPosition) {
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

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        NotificationCenter.getInstance(currentAccount).setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.chatInfoDidLoaded, NotificationCenter.dialogsNeedReload,
                NotificationCenter.closeChats, NotificationCenter.messagesDidLoaded, NotificationCenter.botKeyboardDidLoaded/*, NotificationCenter.botInfoDidLoaded*/});
        NotificationCenter.getInstance(currentAccount).setAnimationInProgress(true);
        if (isOpen) {
            openAnimationEnded = false;
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        NotificationCenter.getInstance(currentAccount).setAnimationInProgress(false);
        if (isOpen) {
            openAnimationEnded = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        paused = false;
        checkScrollForLoad(false);
        if (wasPaused) {
            wasPaused = false;
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }

        fixLayout();
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        wasPaused = true;
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
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void fixLayout() {
        if (avatarContainer != null) {
            avatarContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (avatarContainer != null) {
                        avatarContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return true;
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

    public TLRPC.Chat getCurrentChat() {
        return currentChat;
    }

    private void addCanBanUser(Bundle bundle, int uid) {
        if (!currentChat.megagroup || admins == null || !ChatObject.canBlockUsers(currentChat)) {
            return;
        }
        for (int a = 0; a < admins.size(); a++) {
            TLRPC.ChannelParticipant channelParticipant = admins.get(a);
            if (channelParticipant.user_id == uid) {
                if (!channelParticipant.can_edit) {
                    return;
                }
                break;
            }
        }
        bundle.putInt("ban_chat_id", currentChat.id);
    }

    public void showOpenUrlAlert(final String url, boolean ask) {
        if (Browser.isInternalUrl(url, null) || !ask) {
            Browser.openUrl(getParentActivity(), url, true);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.formatString("OpenUrlAlert", R.string.OpenUrlAlert, url));
            builder.setPositiveButton(LocaleController.getString("Open", R.string.Open), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Browser.openUrl(getParentActivity(), url, true);
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
                        showDialog(ShareAlert.createShareAlert(mContext, cell.getMessageObject(), null, ChatObject.isChannel(currentChat) && !currentChat.megagroup && currentChat.username != null && currentChat.username.length() > 0, null, false));
                    }

                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject);
                            MediaController.getInstance().setVoiceMessagesPlaylist(null, false);
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(messages, messageObject);
                        }
                        return false;
                    }

                    @Override
                    public void didPressedChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId) {
                        if (chat != null && chat != currentChat) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", chat.id);
                            if (postId != 0) {
                                args.putInt("message_id", postId);
                            }
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ChannelAdminLogActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    }

                    @Override
                    public void didPressedOther(ChatMessageCell cell) {
                        createMenu(cell);
                    }

                    @Override
                    public void didPressedUserAvatar(ChatMessageCell cell, TLRPC.User user) {
                        if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            addCanBanUser(args, user.id);
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(false);
                            presentFragment(fragment);
                        }
                    }

                    @Override
                    public void didPressedBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {

                    }

                    @Override
                    public void didPressedCancelSendButton(ChatMessageCell cell) {

                    }

                    @Override
                    public void didLongPressed(ChatMessageCell cell) {
                        createMenu(cell);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return true;
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
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(Utilities.parseInt(((URLSpanUserMention) url).getURL()));
                            if (user != null) {
                                MessagesController.openChatOrProfileWith(user, null, ChannelAdminLogActivity.this, 0, false);
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
                                builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, final int which) {
                                        if (which == 0) {
                                            Browser.openUrl(getParentActivity(), urlFinal, true);
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
                                            ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChannelAdminLogActivity.this);
                                            ArticleViewer.getInstance().open(messageObject);
                                            return;
                                        }
                                    }
                                    Browser.openUrl(getParentActivity(), urlFinal, true);
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

                    }

                    @Override
                    public void didPressedViaBot(ChatMessageCell cell, String username) {

                    }

                    @Override
                    public void didPressedImage(ChatMessageCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.type == 13) {
                            showDialog(new StickersAlert(getParentActivity(), ChannelAdminLogActivity.this, message.getInputStickerSet(), null, null));
                        } else if (message.isVideo() || message.type == 1 || message.type == 0 && !message.isWebpageDocument() || message.isGif()) {
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            PhotoViewer.getInstance().openPhoto(message, 0, 0, provider);
                        } else if (message.type == 3) {
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
                                alertUserOpenError(message);
                            }
                        } else if (message.type == 4) {
                            if (!AndroidUtilities.isGoogleMapsInstalled(ChannelAdminLogActivity.this)) {
                                return;
                            }
                            LocationActivity fragment = new LocationActivity(0);
                            fragment.setMessageObject(message);
                            presentFragment(fragment);
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
                                alertUserOpenError(message);
                            }
                        }
                    }

                    @Override
                    public void didPressedInstantButton(ChatMessageCell cell, int type) {
                        MessageObject messageObject = cell.getMessageObject();
                        if (type == 0) {
                            if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChannelAdminLogActivity.this);
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
                    public boolean isChatAdminCell(int uid) {
                        return false;
                    }
                });
                chatMessageCell.setAllowAssistant(true);
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickedImage(ChatActionCell cell) {
                        MessageObject message = cell.getMessageObject();
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 640);
                        if (photoSize != null) {
                            PhotoViewer.getInstance().openPhoto(photoSize.location, provider);
                        } else {
                            PhotoViewer.getInstance().openPhoto(message, 0, 0, provider);
                        }
                    }

                    @Override
                    public void didLongPressed(ChatActionCell cell) {
                        createMenu(cell);
                    }

                    @Override
                    public void needOpenUserProfile(int uid) {
                        if (uid < 0) {
                            Bundle args = new Bundle();
                            args.putInt("chat_id", -uid);
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ChannelAdminLogActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        } else if (uid != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", uid);
                            addCanBanUser(args, uid);
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(false);
                            presentFragment(fragment);
                        }
                    }

                    @Override
                    public void didPressedReplyMessage(ChatActionCell cell, int id) {

                    }

                    @Override
                    public void didPressedBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {

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
                            MessagesController.getInstance(currentAccount).openByUserName(url.substring(1), ChannelAdminLogActivity.this, 0);
                        } else if (url.startsWith("#")) {
                            DialogsActivity fragment = new DialogsActivity(null);
                            fragment.setSearchString(url);
                            presentFragment(fragment);
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
                        pinnedBotton = nextMessage.isOutOwner() == message.isOutOwner() && (nextMessage.messageOwner.from_id == message.messageOwner.from_id) && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedBotton = false;
                    }
                    if (prevType == holder.getItemViewType()) {
                        MessageObject prevMessage = messages.get(messages.size() - (position - messagesStartRow));
                        pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && (prevMessage.messageOwner.from_id == message.messageOwner.from_id) && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedTop = false;
                    }
                    messageCell.setMessageObject(message, null, pinnedBotton, pinnedTop);
                    if (view instanceof ChatMessageCell && DownloadController.getInstance(currentAccount).canDownloadMedia(message)) {
                        ((ChatMessageCell) view).downloadAudioIfNeed();
                    }
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
            if (holder.itemView instanceof ChatMessageCell) {
                final ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
                MessageObject message = messageCell.getMessageObject();

                boolean selected = false;
                boolean disableSelection = false;
                messageCell.setBackgroundDrawable(null);
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

    @Override
    public ThemeDescription[] getThemeDescriptions() {
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

                new ThemeDescription(bottomOverlayChat, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground),
                new ThemeDescription(bottomOverlayChat, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow),

                new ThemeDescription(bottomOverlayChatText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_serviceText),

                new ThemeDescription(progressBar, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_chat_serviceText),

                new ThemeDescription(chatListView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{ChatUnreadCell.class}, new String[]{"backgroundLayout"}, null, null, null, Theme.key_chat_unreadMessagesStartBackground),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ChatUnreadCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chat_unreadMessagesStartArrowIcon),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatUnreadCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_unreadMessagesStartText),

                new ThemeDescription(progressView2, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_SERVICEBACKGROUND, null, null, null, null, Theme.key_chat_serviceBackground),

                new ThemeDescription(chatListView, ThemeDescription.FLAG_SERVICEBACKGROUND, new Class[]{ChatLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_serviceBackground),
                new ThemeDescription(chatListView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{ChatLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_serviceText),

                new ThemeDescription(avatarContainer != null ? avatarContainer.getTimeItem() : null, 0, null, null, null, null, Theme.key_chat_secretTimerBackground),
                new ThemeDescription(avatarContainer != null ? avatarContainer.getTimeItem() : null, 0, null, null, null, null, Theme.key_chat_secretTimerText),
        };
    }
}
