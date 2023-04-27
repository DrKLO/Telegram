package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchViewPager;
import org.telegram.ui.Components.StickerEmptyView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class FilteredSearchView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public RecyclerListView recyclerListView;

    StickerEmptyView emptyView;
    RecyclerListView.Adapter adapter;

    Runnable searchRunnable;

    public ArrayList<MessageObject> messages = new ArrayList<>();
    public SparseArray<MessageObject> messagesById = new SparseArray<>();
    public ArrayList<String> sections = new ArrayList<>();
    public HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();

    private int columnsCount = 3;
    private int nextSearchRate;
    String lastMessagesSearchString;
    String lastSearchFilterQueryString;

    FiltersView.MediaFilterData currentSearchFilter;
    long currentSearchDialogId;
    long currentSearchMaxDate;
    long currentSearchMinDate;
    String currentSearchString;
    boolean currentIncludeFolder;

    Activity parentActivity;
    BaseFragment parentFragment;
    private boolean isLoading;
    private boolean endReached;
    private int totalCount;
    private int requestIndex;

    private String currentDataQuery;

    private static SpannableStringBuilder arrowSpan;

    private int photoViewerClassGuid;

    private final MessageHashId messageHashIdTmp = new MessageHashId(0, 0);
    private OnlyUserFiltersAdapter dialogsAdapter;
    private SharedPhotoVideoAdapter sharedPhotoVideoAdapter;
    private SharedDocumentsAdapter sharedDocumentsAdapter;
    private SharedLinksAdapter sharedLinksAdapter;
    private SharedDocumentsAdapter sharedAudioAdapter;
    private SharedDocumentsAdapter sharedVoiceAdapter;

    private int searchIndex;

    ArrayList<Object> localTipChats = new ArrayList<>();
    ArrayList<FiltersView.DateData> localTipDates = new ArrayList<>();
    boolean localTipArchive;

    Runnable clearCurrentResultsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLoading) {
                messages.clear();
                sections.clear();
                sectionArrays.clear();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    };

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public int getTotalImageCount() {
            return totalCount;
        }

        @Override
        public boolean loadMore() {
            if (!endReached) {
                search(currentSearchDialogId, currentSearchMinDate, currentSearchMaxDate, currentSearchFilter, currentIncludeFolder, lastMessagesSearchString, false);
            }
            return true;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (messageObject == null) {
                return null;
            }
            final RecyclerListView listView = recyclerListView;
            for (int a = 0, count = listView.getChildCount(); a < count; a++) {
                View view = listView.getChildAt(a);
                int[] coords = new int[2];
                ImageReceiver imageReceiver = null;
                if (view instanceof SharedPhotoVideoCell) {
                    SharedPhotoVideoCell cell = (SharedPhotoVideoCell) view;
                    for (int i = 0; i < 6; i++) {
                        MessageObject message = cell.getMessageObject(i);
                        if (message == null) {
                            break;
                        }
                        if (message.getId() == messageObject.getId()) {
                            BackupImageView imageView = cell.getImageView(i);
                            imageReceiver = imageView.getImageReceiver();
                            imageView.getLocationInWindow(coords);
                        }
                    }
                } else if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    MessageObject message = cell.getMessage();
                    if (message.getId() == messageObject.getId()) {
                        BackupImageView imageView = cell.getImageView();
                        imageReceiver = imageView.getImageReceiver();
                        imageView.getLocationInWindow(coords);
                    }
                } else if (view instanceof ContextLinkCell) {
                    ContextLinkCell cell = (ContextLinkCell) view;
                    MessageObject message = (MessageObject) cell.getParentObject();
                    if (message != null && message.getId() == messageObject.getId()) {
                        imageReceiver = cell.getPhotoImage();
                        cell.getLocationInWindow(coords);
                    }
                }
                if (imageReceiver != null) {
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = listView;
                    listView.getLocationInWindow(coords);
                    object.animatingImageViewYOffset = -coords[1];
                    object.imageReceiver = imageReceiver;
                    object.allowTakeAnimation = false;
                    object.radius = object.imageReceiver.getRoundRadius();
                    object.thumb = object.imageReceiver.getBitmapSafe();
                    object.parentView.getLocationInWindow(coords);
                    object.clipTopAddition = 0;

                    if (PhotoViewer.isShowingImage(messageObject)) {
                        final View pinnedHeader = listView.getPinnedHeader();
                        if (pinnedHeader != null) {
                            int top = 0;
                            if (view instanceof SharedDocumentCell) {
                                top += AndroidUtilities.dp(8f);
                            }
                            final int topOffset = (int) (top - object.viewY);
                            if (topOffset > view.getHeight()) {
                                listView.scrollBy(0, -(topOffset + pinnedHeader.getHeight()));
                            } else {
                                int bottomOffset = (int) (object.viewY - listView.getHeight());
                                if (view instanceof SharedDocumentCell) {
                                    bottomOffset -= AndroidUtilities.dp(8f);
                                }
                                if (bottomOffset >= 0) {
                                    listView.scrollBy(0, bottomOffset + view.getHeight());
                                }
                            }
                        }
                    }

                    return object;
                }
            }
            return null;
        }

        @Override
        public CharSequence getTitleFor(int i) {
            return createFromInfoString(messages.get(i));
        }

        @Override
        public CharSequence getSubtitleFor(int i) {
            return LocaleController.formatDateAudio(messages.get(i).messageOwner.date, false);
        }
    };

    private Delegate delegate;
    private SearchViewPager.ChatPreviewDelegate chatPreviewDelegate;
    public final LinearLayoutManager layoutManager;
    private final FlickerLoadingView loadingView;
    private boolean firstLoading = true;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    public int keyboardHeight;
    private final ChatActionCell floatingDateView;

    private AnimatorSet floatingDateAnimation;
    private Runnable hideFloatingDateRunnable = () -> hideFloatingDateView(true);

    private UiCallback uiCallback;

    public FilteredSearchView(@NonNull BaseFragment fragment) {
        super(fragment.getParentActivity());
        parentFragment = fragment;
        Context context = parentActivity = fragment.getParentActivity();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        recyclerListView = new BlurredRecyclerView(context) {

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (getAdapter() == sharedPhotoVideoAdapter) {
                    for (int i = 0; i < getChildCount(); i++) {
                        if (getChildViewHolder(getChildAt(i)).getItemViewType() == 1) {
                            canvas.save();
                            canvas.translate(getChildAt(i).getX(), getChildAt(i).getY() - getChildAt(i).getMeasuredHeight() + AndroidUtilities.dp(2));
                            getChildAt(i).draw(canvas);
                            canvas.restore();
                            invalidate();
                        }
                    }
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (getAdapter() == sharedPhotoVideoAdapter) {
                    if (getChildViewHolder(child).getItemViewType() == 1) {
                        return true;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof SharedDocumentCell) {
                FilteredSearchView.this.onItemClick(position, view, ((SharedDocumentCell) view).getMessage(), 0);
            } else if (view instanceof SharedLinkCell) {
                FilteredSearchView.this.onItemClick(position, view, ((SharedLinkCell) view).getMessage(), 0);
            } else if (view instanceof SharedAudioCell) {
                FilteredSearchView.this.onItemClick(position, view, ((SharedAudioCell) view).getMessage(), 0);
            } else if (view instanceof ContextLinkCell) {
                FilteredSearchView.this.onItemClick(position, view, ((ContextLinkCell) view).getMessageObject(), 0);
            } else if (view instanceof DialogCell) {
                FilteredSearchView.this.onItemClick(position, view, ((DialogCell) view).getMessage(), 0);
            }
        });
        recyclerListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                if (view instanceof SharedDocumentCell) {
                    FilteredSearchView.this.onItemLongClick(((SharedDocumentCell) view).getMessage(), view, 0);
                } else if (view instanceof SharedLinkCell) {
                    FilteredSearchView.this.onItemLongClick(((SharedLinkCell) view).getMessage(), view, 0);
                } else if (view instanceof SharedAudioCell) {
                    FilteredSearchView.this.onItemLongClick(((SharedAudioCell) view).getMessage(), view, 0);
                } else if (view instanceof ContextLinkCell) {
                    FilteredSearchView.this.onItemLongClick(((ContextLinkCell) view).getMessageObject(), view, 0);
                } else if (view instanceof DialogCell) {
                    if (!uiCallback.actionModeShowing()) {
                        if (((DialogCell) view).isPointInsideAvatar(x, y)) {
                            chatPreviewDelegate.startChatPreview(recyclerListView, (DialogCell) view);
                            return true;
                        }
                    }
                    FilteredSearchView.this.onItemLongClick(((DialogCell) view).getMessage(), view, 0);
                }
                return true;
            }

            @Override
            public void onMove(float dx, float dy) {
                chatPreviewDelegate.move(dy);
            }

            @Override
            public void onLongClickRelease() {
                chatPreviewDelegate.finish();
            }
        });
        recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(3));

        layoutManager = new LinearLayoutManager(context);
        recyclerListView.setLayoutManager(layoutManager);
        addView(loadingView = new FlickerLoadingView(context) {
            @Override
            public int getColumnsCount() {
                return columnsCount;
            }
        });
        addView(recyclerListView);

        recyclerListView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE);
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView.getAdapter() == null || adapter == null) {
                    return;
                }
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                int visibleItemCount = Math.abs(lastVisibleItem - firstVisibleItem) + 1;
                int totalItemCount = recyclerView.getAdapter().getItemCount();
                if (!isLoading && visibleItemCount > 0 && lastVisibleItem >= totalItemCount - 10 && !endReached) {
                    AndroidUtilities.runOnUIThread(() -> {
                        search(currentSearchDialogId, currentSearchMinDate, currentSearchMaxDate, currentSearchFilter, currentIncludeFolder, lastMessagesSearchString, false);
                    });
                }

                if (adapter == sharedPhotoVideoAdapter) {
                    if (dy != 0 && !messages.isEmpty() && TextUtils.isEmpty(currentDataQuery)) {
                        showFloatingDateView();
                    }
                    RecyclerListView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem);
                    if (holder != null && holder.getItemViewType() == 0) {
                        if (holder.itemView instanceof SharedPhotoVideoCell) {
                            SharedPhotoVideoCell cell = (SharedPhotoVideoCell) holder.itemView;
                            MessageObject messageObject = cell.getMessageObject(0);
                            if (messageObject != null) {
                                floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
                            }
                        }
                    }
                }
            }
        });

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setOverrideColor(Theme.key_chat_mediaTimeBackground, Theme.key_chat_mediaTimeText);
        floatingDateView.setTranslationY(-AndroidUtilities.dp(48));
        addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        dialogsAdapter = new OnlyUserFiltersAdapter();
        sharedPhotoVideoAdapter = new SharedPhotoVideoAdapter(getContext());
        sharedDocumentsAdapter = new SharedDocumentsAdapter(getContext(), 1);
        sharedLinksAdapter = new SharedLinksAdapter(getContext());
        sharedAudioAdapter = new SharedDocumentsAdapter(getContext(), 4);
        sharedVoiceAdapter = new SharedDocumentsAdapter(getContext(), 2);

        emptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        addView(emptyView);
        recyclerListView.setEmptyView(emptyView);
        emptyView.setVisibility(View.GONE);
    }

    public static CharSequence createFromInfoString(MessageObject messageObject) {
        return createFromInfoString(messageObject, true);
    }

    public static CharSequence createFromInfoString(MessageObject messageObject, boolean includeChat) {
        if (messageObject == null) {
            return "";
        }
        if (arrowSpan == null) {
            arrowSpan = new SpannableStringBuilder(">");
            Drawable arrowDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.attach_arrow_right).mutate();
            ColoredImageSpan span = new ColoredImageSpan(arrowDrawable, ColoredImageSpan.ALIGN_CENTER);
            arrowDrawable.setBounds(0, AndroidUtilities.dp(1), AndroidUtilities.dp(13), AndroidUtilities.dp(1 + 13));
            arrowSpan.setSpan(span, 0, arrowSpan.length(), 0);
        }
        CharSequence fromName = null;
        TLRPC.User user = messageObject.messageOwner.from_id.user_id != 0 ? MessagesController.getInstance(UserConfig.selectedAccount).getUser(messageObject.messageOwner.from_id.user_id) : null;
        TLRPC.Chat chatFrom = null, chatTo = null;
        chatFrom = messageObject.messageOwner.from_id.chat_id != 0 ? MessagesController.getInstance(UserConfig.selectedAccount).getChat(messageObject.messageOwner.peer_id.chat_id) : null;
        if (chatFrom == null) {
            chatFrom = messageObject.messageOwner.from_id.channel_id != 0 ? MessagesController.getInstance(UserConfig.selectedAccount).getChat(messageObject.messageOwner.peer_id.channel_id) : null;
        }
        chatTo = messageObject.messageOwner.peer_id.channel_id != 0 ? MessagesController.getInstance(UserConfig.selectedAccount).getChat(messageObject.messageOwner.peer_id.channel_id) : null;
        if (chatTo == null) {
            chatTo = messageObject.messageOwner.peer_id.chat_id != 0 ? MessagesController.getInstance(UserConfig.selectedAccount).getChat(messageObject.messageOwner.peer_id.chat_id) : null;
        }
        if (!ChatObject.isChannelAndNotMegaGroup(chatTo) && !includeChat) {
            chatTo = null;
        }
        if (user != null && chatTo != null) {
            CharSequence chatTitle = chatTo.title;
            if (ChatObject.isForum(chatTo)) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(UserConfig.selectedAccount).getTopicsController().findTopic(chatTo.id, MessageObject.getTopicId(messageObject.messageOwner, true));
                if (topic != null) {
                    chatTitle = ForumUtilities.getTopicSpannedName(topic, null);
                }
            }
            chatTitle = Emoji.replaceEmoji(chatTitle, null, AndroidUtilities.dp(12), false);
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder
                    .append(Emoji.replaceEmoji(UserObject.getFirstName(user), null, AndroidUtilities.dp(12), false))
                    .append(' ')
                    .append(arrowSpan)
                    .append(' ')
                    .append(chatTitle);
            fromName = spannableStringBuilder;
        } else if (user != null) {
            fromName = Emoji.replaceEmoji(UserObject.getUserName(user), null, AndroidUtilities.dp(12), false);
        } else if (chatFrom != null) {
            CharSequence chatTitle = chatFrom.title;
            if (ChatObject.isForum(chatFrom)) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(UserConfig.selectedAccount).getTopicsController().findTopic(chatFrom.id, MessageObject.getTopicId(messageObject.messageOwner, true));
                if (topic != null) {
                    chatTitle = ForumUtilities.getTopicSpannedName(topic, null);
                }
            }
            chatTitle = Emoji.replaceEmoji(chatTitle, null, AndroidUtilities.dp(12), false);
            fromName = chatTitle;
        }
        return fromName == null ? "" : fromName;
    }

    public void search(long dialogId, long minDate, long maxDate, FiltersView.MediaFilterData currentSearchFilter, boolean includeFolder, String query, boolean clearOldResults) {
        String currentSearchFilterQueryString = String.format(Locale.ENGLISH, "%d%d%d%d%s%s", dialogId, minDate, maxDate, currentSearchFilter == null ? -1 : currentSearchFilter.filterType, query, includeFolder);
        boolean filterAndQueryIsSame = lastSearchFilterQueryString != null && lastSearchFilterQueryString.equals(currentSearchFilterQueryString);
        boolean forceClear = !filterAndQueryIsSame && clearOldResults;
        this.currentSearchFilter = currentSearchFilter;
        this.currentSearchDialogId = dialogId;
        this.currentSearchMinDate = minDate;
        this.currentSearchMaxDate = maxDate;
        this.currentSearchString = query;
        this.currentIncludeFolder = includeFolder;
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        }
        AndroidUtilities.cancelRunOnUIThread(clearCurrentResultsRunnable);
        if (filterAndQueryIsSame && clearOldResults) {
            return;
        }
        if (forceClear || currentSearchFilter == null && dialogId == 0 && minDate == 0 && maxDate == 0) {
            messages.clear();
            sections.clear();
            sectionArrays.clear();
            isLoading = true;
            emptyView.setVisibility(View.VISIBLE);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            requestIndex++;
            firstLoading = true;
            if (recyclerListView.getPinnedHeader() != null) {
                recyclerListView.getPinnedHeader().setAlpha(0);
            }
            localTipChats.clear();
            localTipDates.clear();
            if (!forceClear) {
                return;
            }
        } else if (clearOldResults && !messages.isEmpty()) {
            return;
        }
        isLoading = true;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (!filterAndQueryIsSame) {
            clearCurrentResultsRunnable.run();
            emptyView.showProgress(true, !clearOldResults);
        }

        if (TextUtils.isEmpty(query)) {
            localTipDates.clear();
            localTipChats.clear();
            if (delegate != null) {
                delegate.updateFiltersView(false, null, null, false);
            }
        }
        requestIndex++;
        final int requestId = requestIndex;
        int currentAccount = UserConfig.selectedAccount;

        AndroidUtilities.runOnUIThread(searchRunnable = () -> {
            TLObject request;

            ArrayList<Object> resultArray = null;
            if (dialogId != 0) {
                final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.q = query;
                req.limit = 20;
                req.filter = currentSearchFilter == null ? new TLRPC.TL_inputMessagesFilterEmpty() : currentSearchFilter.filter;
                req.peer = AccountInstance.getInstance(currentAccount).getMessagesController().getInputPeer(dialogId);
                if (minDate > 0) {
                    req.min_date = (int) (minDate / 1000);
                }
                if (maxDate > 0) {
                    req.max_date = (int) (maxDate / 1000);
                }
                if (filterAndQueryIsSame && query.equals(lastMessagesSearchString) && !messages.isEmpty()) {
                    MessageObject lastMessage = messages.get(messages.size() - 1);
                    req.offset_id = lastMessage.getId();
                } else {
                    req.offset_id = 0;
                }
                request = req;
            } else {
                if (!TextUtils.isEmpty(query)) {
                    resultArray = new ArrayList<>();
                    ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
                    ArrayList<TLRPC.User> encUsers = new ArrayList<>();
                    MessagesStorage.getInstance(currentAccount).localSearch(0, query, resultArray, resultArrayNames, encUsers, null, includeFolder ? 1 : 0);
                }

                final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
                req.limit = 20;
                req.q = query;
                req.filter = currentSearchFilter == null ? new TLRPC.TL_inputMessagesFilterEmpty() : currentSearchFilter.filter;
                if (minDate > 0) {
                    req.min_date = (int) (minDate / 1000);
                }
                if (maxDate > 0) {
                    req.max_date = (int) (maxDate / 1000);
                }
                if (filterAndQueryIsSame && query.equals(lastMessagesSearchString) && !messages.isEmpty()) {
                    MessageObject lastMessage = messages.get(messages.size() - 1);
                    req.offset_id = lastMessage.getId();
                    req.offset_rate = nextSearchRate;
                    long id = MessageObject.getPeerId(lastMessage.messageOwner.peer_id);
                    req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id);
                } else {
                    req.offset_rate = 0;
                    req.offset_id = 0;
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                }
                req.flags |= 1;
                req.folder_id = includeFolder ? 1 : 0;
                request = req;
            }

            lastMessagesSearchString = query;
            lastSearchFilterQueryString = currentSearchFilterQueryString;

            ArrayList<Object> finalResultArray = resultArray;
            final ArrayList<FiltersView.DateData> dateData = new ArrayList<>();
            FiltersView.fillTipDates(lastMessagesSearchString, dateData);
            ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                ArrayList<MessageObject> messageObjects = new ArrayList<>();
                if (error == null) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    int n = res.messages.size();
                    for (int i = 0; i < n; i++) {
                        MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, true);
                        messageObject.setQuery(query);
                        messageObjects.add(messageObject);
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    if (requestId != requestIndex) {
                        return;
                    }
                    isLoading = false;
                    if (error != null) {
                        emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle2", R.string.SearchEmptyViewTitle2));
                        emptyView.subtitle.setVisibility(View.VISIBLE);
                        emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
                        emptyView.showProgress(false, true);
                        return;
                    }

                    emptyView.showProgress(false);

                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    nextSearchRate = res.next_rate;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    if (!filterAndQueryIsSame) {
                        messages.clear();
                        messagesById.clear();
                        sections.clear();
                        sectionArrays.clear();
                    }
                    totalCount = res.count;
                    currentDataQuery = query;
                    int n = messageObjects.size();
                    for (int i = 0; i < n; i++) {
                        MessageObject messageObject = messageObjects.get(i);
                        ArrayList<MessageObject> messageObjectsByDate = sectionArrays.get(messageObject.monthKey);
                        if (messageObjectsByDate == null) {
                            messageObjectsByDate = new ArrayList<>();
                            sectionArrays.put(messageObject.monthKey, messageObjectsByDate);
                            sections.add(messageObject.monthKey);
                        }
                        messageObjectsByDate.add(messageObject);
                        messages.add(messageObject);
                        messagesById.put(messageObject.getId(), messageObject);

                        if (PhotoViewer.getInstance().isVisible()) {
                            PhotoViewer.getInstance().addPhoto(messageObject, photoViewerClassGuid);
                        }
                    }
                    if (messages.size() > totalCount) {
                        totalCount = messages.size();
                    }
                    endReached = messages.size() >= totalCount;

                    if (messages.isEmpty()) {
                        if (currentSearchFilter != null) {
                            if (TextUtils.isEmpty(currentDataQuery) && dialogId == 0 && minDate == 0) {
                                emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle", R.string.SearchEmptyViewTitle));
                                String str;
                                if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_FILES) {
                                    str = LocaleController.getString("SearchEmptyViewFilteredSubtitleFiles", R.string.SearchEmptyViewFilteredSubtitleFiles);
                                } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_MEDIA) {
                                    str = LocaleController.getString("SearchEmptyViewFilteredSubtitleMedia", R.string.SearchEmptyViewFilteredSubtitleMedia);
                                } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_LINKS) {
                                    str = LocaleController.getString("SearchEmptyViewFilteredSubtitleLinks", R.string.SearchEmptyViewFilteredSubtitleLinks);
                                } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_MUSIC) {
                                    str = LocaleController.getString("SearchEmptyViewFilteredSubtitleMusic", R.string.SearchEmptyViewFilteredSubtitleMusic);
                                } else {
                                    str = LocaleController.getString("SearchEmptyViewFilteredSubtitleVoice", R.string.SearchEmptyViewFilteredSubtitleVoice);
                                }
                                emptyView.subtitle.setVisibility(View.VISIBLE);
                                emptyView.subtitle.setText(str);
                            } else {
                                emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle2", R.string.SearchEmptyViewTitle2));
                                emptyView.subtitle.setVisibility(View.VISIBLE);
                                emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
                            }
                        } else {
                            emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle2", R.string.SearchEmptyViewTitle2));
                            emptyView.subtitle.setVisibility(View.GONE);
                        }
                    }

                    if (currentSearchFilter != null) {
                        switch (currentSearchFilter.filterType) {
                            case FiltersView.FILTER_TYPE_MEDIA:
                                if (TextUtils.isEmpty(currentDataQuery)) {
                                    adapter = sharedPhotoVideoAdapter;
                                } else {
                                    adapter = dialogsAdapter;
                                }
                                break;
                            case FiltersView.FILTER_TYPE_FILES:
                                adapter = sharedDocumentsAdapter;
                                break;
                            case FiltersView.FILTER_TYPE_LINKS:
                                adapter = sharedLinksAdapter;
                                break;
                            case FiltersView.FILTER_TYPE_MUSIC:
                                adapter = sharedAudioAdapter;
                                break;
                            case FiltersView.FILTER_TYPE_VOICE:
                                adapter = sharedVoiceAdapter;
                                break;
                        }
                    } else {
                        adapter = dialogsAdapter;
                    }
                    if (recyclerListView.getAdapter() != adapter) {
                        recyclerListView.setAdapter(adapter);
                    }

                    if (!filterAndQueryIsSame) {
                        localTipChats.clear();
                        if (finalResultArray != null) {
                            localTipChats.addAll(finalResultArray);
                        }
                        if (query != null && query.length() >= 3 && (LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase().startsWith(query) ||
                                "saved messages".startsWith(query))) {
                            boolean found = false;
                            for (int i = 0; i < localTipChats.size(); i++) {
                                if (localTipChats.get(i) instanceof TLRPC.User)
                                    if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id == ((TLRPC.User) localTipChats.get(i)).id) {
                                        found = true;
                                        break;
                                    }
                            }
                            if (!found) {
                                localTipChats.add(0, UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
                            }
                        }
                        localTipDates.clear();
                        localTipDates.addAll(dateData);
                        localTipArchive = false;
                        if (query != null && query.length() >= 3 && (LocaleController.getString("ArchiveSearchFilter", R.string.ArchiveSearchFilter).toLowerCase().startsWith(query) ||
                                "archive".startsWith(query))) {
                            localTipArchive = true;
                        }
                        if (delegate != null) {
                            delegate.updateFiltersView(TextUtils.isEmpty(currentDataQuery), localTipChats, localTipDates, localTipArchive);
                        }
                    }
                    firstLoading = false;
                    View progressView = null;
                    int progressViewPosition = -1;
                    for (int i = 0; i < n; i++) {
                        View child = recyclerListView.getChildAt(i);
                        if (child instanceof FlickerLoadingView) {
                            progressView = child;
                            progressViewPosition = recyclerListView.getChildAdapterPosition(child);
                        }
                    }
                    final View finalProgressView = progressView;
                    if (progressView != null) {
                        recyclerListView.removeView(progressView);
                    }
                    if ((loadingView.getVisibility() == View.VISIBLE && recyclerListView.getChildCount() == 0) || (recyclerListView.getAdapter() != sharedPhotoVideoAdapter && progressView != null)) {
                        int finalProgressViewPosition = progressViewPosition;
                        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                getViewTreeObserver().removeOnPreDrawListener(this);
                                int n = recyclerListView.getChildCount();
                                AnimatorSet animatorSet = new AnimatorSet();
                                for (int i = 0; i < n; i++) {
                                    View child = recyclerListView.getChildAt(i);
                                    if (finalProgressView != null) {
                                        if (recyclerListView.getChildAdapterPosition(child) < finalProgressViewPosition) {
                                            continue;
                                        }
                                    }
                                    child.setAlpha(0);
                                    int s = Math.min(recyclerListView.getMeasuredHeight(), Math.max(0, child.getTop()));
                                    int delay = (int) ((s / (float) recyclerListView.getMeasuredHeight()) * 100);
                                    ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                                    a.setStartDelay(delay);
                                    a.setDuration(200);
                                    animatorSet.playTogether(a);
                                }
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        notificationsLocker.unlock();
                                    }
                                });
                                notificationsLocker.lock();
                                animatorSet.start();

                                if (finalProgressView != null && finalProgressView.getParent() == null) {
                                    recyclerListView.addView(finalProgressView);
                                    RecyclerView.LayoutManager layoutManager = recyclerListView.getLayoutManager();
                                    if (layoutManager != null) {
                                        layoutManager.ignoreView(finalProgressView);
                                        Animator animator = ObjectAnimator.ofFloat(finalProgressView, ALPHA, finalProgressView.getAlpha(), 0);
                                        animator.addListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                finalProgressView.setAlpha(1f);
                                                layoutManager.stopIgnoringView(finalProgressView);
                                                recyclerListView.removeView(finalProgressView);
                                            }
                                        });
                                        animator.start();
                                    }
                                }
                                return true;
                            }
                        });
                    }
                    adapter.notifyDataSetChanged();
                });
            });
        }, (filterAndQueryIsSame && !messages.isEmpty()) ? 0 : 350);

        if (currentSearchFilter == null) {
            loadingView.setViewType(FlickerLoadingView.DIALOG_TYPE);
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_MEDIA) {
            if (!TextUtils.isEmpty(currentSearchString)) {
                loadingView.setViewType(FlickerLoadingView.DIALOG_TYPE);
            } else {
                loadingView.setViewType(FlickerLoadingView.PHOTOS_TYPE);
            }
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_FILES) {
            loadingView.setViewType(FlickerLoadingView.FILES_TYPE);
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_MUSIC || currentSearchFilter.filterType == FiltersView.FILTER_TYPE_VOICE) {
            loadingView.setViewType(FlickerLoadingView.AUDIO_TYPE);
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_LINKS) {
            loadingView.setViewType(FlickerLoadingView.LINKS_TYPE);
        }
    }

    public void update() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public void setKeyboardHeight(int keyboardSize, boolean animated) {
        emptyView.setKeyboardHeight(keyboardSize, animated);
    }

    public void messagesDeleted(long channelId, ArrayList<Integer> markAsDeletedMessages) {
        boolean changed = false;
        for (int j = 0; j < messages.size(); j++) {
            MessageObject messageObject = messages.get(j);
            long dialogId = messageObject.getDialogId();
            int currentChannelId = dialogId < 0 && ChatObject.isChannel((int) -dialogId, UserConfig.selectedAccount) ? (int) -dialogId : 0;
            if (currentChannelId == channelId) {
                for (int i = 0; i < markAsDeletedMessages.size(); i++) {
                    if (messageObject.getId() == markAsDeletedMessages.get(i)) {
                        changed = true;
                        messages.remove(j);
                        messagesById.remove(messageObject.getId());

                        ArrayList<MessageObject> section = sectionArrays.get(messageObject.monthKey);
                        section.remove(messageObject);
                        if (section.size() == 0) {
                            sections.remove(messageObject.monthKey);
                            sectionArrays.remove(messageObject.monthKey);
                        }
                        j--;
                        totalCount--;
                    }
                }
            }
        }
        if (changed && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private class SharedPhotoVideoAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public SharedPhotoVideoAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            if (messages.isEmpty()) {
                return 0;
            }
            return (int) Math.ceil(messages.size() / (float) columnsCount) +  (endReached ? 0 : 1);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new SharedPhotoVideoCell(mContext, SharedPhotoVideoCell.VIEW_TYPE_GLOBAL_SEARCH);
                    SharedPhotoVideoCell cell = (SharedPhotoVideoCell) view;
                    cell.setDelegate(new SharedPhotoVideoCell.SharedPhotoVideoCellDelegate() {
                        @Override
                        public void didClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            onItemClick(index, cell, messageObject, a);
                        }

                        @Override
                        public boolean didLongClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            if (uiCallback.actionModeShowing()) {
                                didClickItem(cell, index, messageObject, a);
                                return true;
                            }
                            return onItemLongClick(messageObject, cell, a);
                        }
                    });
                    break;
                case 2:
                    view = new GraySectionCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_graySection) & 0xf2ffffff);
                    break;
                case 1:
                default:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext) {
                        @Override
                        public int getColumnsCount() {
                            return columnsCount;
                        }
                    };
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.PHOTOS_TYPE);
                    view = flickerLoadingView;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ArrayList<MessageObject> messageObjects = messages;
                SharedPhotoVideoCell cell = (SharedPhotoVideoCell) holder.itemView;
                cell.setItemsCount(columnsCount);
                cell.setIsFirst(position == 0);
                for (int a = 0; a < columnsCount; a++) {
                    int index = position * columnsCount + a;
                    if (index < messageObjects.size()) {
                        MessageObject messageObject = messageObjects.get(index);
                        cell.setItem(a, messages.indexOf(messageObject), messageObject);
                        if (uiCallback.actionModeShowing()) {
                            messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                            cell.setChecked(a, uiCallback.isSelected(messageHashIdTmp), true);
                        } else {
                            cell.setChecked(a, false, true);
                        }
                    } else {
                        cell.setItem(a, index, null);
                    }
                }
                cell.requestLayout();
            } else if (holder.getItemViewType() == 3) {
                DialogCell cell = (DialogCell) holder.itemView;
                cell.useSeparator = (position != getItemCount() - 1);
                MessageObject messageObject = messages.get(position);
                boolean animated = cell.getMessage() != null && cell.getMessage().getId() == messageObject.getId();
                cell.useFromUserAsAvatar = useFromUserAsAvatar;
                cell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date, false, false);
                if (uiCallback.actionModeShowing()) {
                    messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                    cell.setChecked(uiCallback.isSelected(messageHashIdTmp), animated);
                } else {
                    cell.setChecked(false, animated);
                }
            } else if (holder.getItemViewType() == 1) {
                FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                int count = (int) Math.ceil(messages.size() / (float) columnsCount);
                flickerLoadingView.skipDrawItemsCount(columnsCount - (columnsCount * count - messages.size()));
            }
        }

        @Override
        public int getItemViewType(int position) {
            int count = (int) Math.ceil(messages.size() / (float) columnsCount);
            if (position < count) {
                return 0;
            }
            return 1;
        }
    }

    private boolean useFromUserAsAvatar;
    public void setUseFromUserAsAvatar(boolean value) {
        useFromUserAsAvatar = value;
    }

    private void onItemClick(int index, View view, MessageObject message, int a) {
        if (message == null) {
            return;
        }
        if (uiCallback.actionModeShowing()) {
            uiCallback.toggleItemSelection(message, view, a);
            return;
        }
        if (view instanceof DialogCell) {
            uiCallback.goToMessage(message);
            return;
        }
        if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_MEDIA) {
            PhotoViewer.getInstance().setParentActivity(parentFragment);
            PhotoViewer.getInstance().openPhoto(messages, index, 0, 0, 0, provider);
            photoViewerClassGuid = PhotoViewer.getInstance().getClassGuid();
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_MUSIC || currentSearchFilter.filterType == FiltersView.FILTER_TYPE_VOICE) {
            if (view instanceof SharedAudioCell) {
                ((SharedAudioCell) view).didPressedButton();
            }
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_FILES) {
            if (view instanceof SharedDocumentCell) {
                SharedDocumentCell cell = (SharedDocumentCell) view;
                TLRPC.Document document = message.getDocument();
                if (cell.isLoaded()) {
                    if (message.canPreviewDocument()) {
                        PhotoViewer.getInstance().setParentActivity(parentFragment);
                        index = messages.indexOf(message);
                        if (index < 0) {
                            ArrayList<MessageObject> documents = new ArrayList<>();
                            documents.add(message);
                            PhotoViewer.getInstance().setParentActivity(parentFragment);
                            PhotoViewer.getInstance().openPhoto(documents, 0, 0, 0, 0, provider);
                            photoViewerClassGuid = PhotoViewer.getInstance().getClassGuid();
                        } else {
                            PhotoViewer.getInstance().setParentActivity(parentFragment);
                            PhotoViewer.getInstance().openPhoto(messages, index, 0, 0, 0, provider);
                            photoViewerClassGuid = PhotoViewer.getInstance().getClassGuid();
                        }
                        return;
                    }
                    AndroidUtilities.openDocument(message, parentActivity, parentFragment);
                } else if (!cell.isLoading()) {
                    MessageObject messageObject = cell.getMessage();
                    messageObject.putInDownloadsStore = true;
                    AccountInstance.getInstance(UserConfig.selectedAccount).getFileLoader().loadFile(document, messageObject, FileLoader.PRIORITY_LOW, 0);
                    cell.updateFileExistIcon(true);
                } else {
                    AccountInstance.getInstance(UserConfig.selectedAccount).getFileLoader().cancelLoadFile(document);
                    cell.updateFileExistIcon(true);
                }
            }
        } else if (currentSearchFilter.filterType == FiltersView.FILTER_TYPE_LINKS) {
            try {
                TLRPC.WebPage webPage = message.messageOwner.media != null ? message.messageOwner.media.webpage : null;
                String link = null;
                if (webPage != null && !(webPage instanceof TLRPC.TL_webPageEmpty)) {
                    if (webPage.cached_page != null) {
                        ArticleViewer.getInstance().setParentActivity(parentActivity, parentFragment);
                        ArticleViewer.getInstance().open(message);
                        return;
                    } else if (webPage.embed_url != null && webPage.embed_url.length() != 0) {
                        openWebView(webPage, message);
                        return;
                    } else {
                        link = webPage.url;
                    }
                }
                if (link == null) {
                    link = ((SharedLinkCell) view).getLink(0);
                }
                if (link != null) {
                    openUrl(link);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private class SharedLinksAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;

        private final SharedLinkCell.SharedLinkCellDelegate sharedLinkCellDelegate = new SharedLinkCell.SharedLinkCellDelegate() {

            @Override
            public void needOpenWebView(TLRPC.WebPage webPage, MessageObject message) {
                openWebView(webPage, message);
            }

            @Override
            public boolean canPerformActions() {
                return !uiCallback.actionModeShowing();
            }

            @Override
            public void onLinkPress(String urlFinal, boolean longPress) {
                if (longPress) {
                    BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
                    builder.setTitle(urlFinal);
                    builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
                        if (which == 0) {
                            openUrl(urlFinal);
                        } else if (which == 1) {
                            String url = urlFinal;
                            if (url.startsWith("mailto:")) {
                                url = url.substring(7);
                            } else if (url.startsWith("tel:")) {
                                url = url.substring(4);
                            }
                            AndroidUtilities.addToClipboard(url);
                        }
                    });
                    parentFragment.showDialog(builder.create());
                } else {
                    openUrl(urlFinal);
                }
            }
        };

        public SharedLinksAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            return true;
        }

        @Override
        public int getSectionCount() {
            if (messages.isEmpty()) {
                return 0;
            }
            if (sections.isEmpty() && isLoading) {
                return 0;
            }
            return sections.size() + (sections.isEmpty() || endReached ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sections.size()) {
                return sectionArrays.get(sections.get(section)).size() + (section == 0 ? 0 : 1);
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new GraySectionCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_graySection) & 0xf2ffffff);
            }
            if (section == 0) {
                view.setAlpha(0f);
                return view;
            }
            if (section < sections.size()) {
                view.setAlpha(1.0f);
                String name = sections.get(section);
                ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((GraySectionCell) view).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));

            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(mContext);
                    break;
                case 1:
                    view = new SharedLinkCell(mContext, SharedLinkCell.VIEW_TYPE_GLOBAL_SEARCH);
                    ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
                    break;
                case 2:
                default:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setViewType(FlickerLoadingView.LINKS_TYPE);
                    flickerLoadingView.setIsSingleCell(true);
                    view = flickerLoadingView;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2) {
                String name = sections.get(section);
                ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((GraySectionCell) holder.itemView).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));
                        break;
                    }
                    case 1: {
                        if (section != 0) {
                            position--;
                        }
                        SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position);
                        boolean animated = sharedLinkCell.getMessage() != null && sharedLinkCell.getMessage().getId() == messageObject.getId();
                        sharedLinkCell.setLink(messageObject, position != messageObjects.size() - 1 || section == sections.size() - 1 && isLoading);
                        sharedLinkCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                sharedLinkCell.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (uiCallback.actionModeShowing()) {
                                    messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                                    sharedLinkCell.setChecked(uiCallback.isSelected(messageHashIdTmp), animated);
                                } else {
                                    sharedLinkCell.setChecked(false, animated);
                                }
                                return true;
                            }
                        });
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section < sections.size()) {
                if (section != 0 && position == 0) {
                    return 0;
                }
                return 1;
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = 0;
            position[1] = 0;
        }
    }

    private class SharedDocumentsAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;
        private int currentType;

        public SharedDocumentsAdapter(Context context, int type) {
            mContext = context;
            currentType = type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            return section == 0 || row != 0;
        }

        @Override
        public int getSectionCount() {
            if (sections.isEmpty()) {
                return 0;
            }
            return sections.size() + (sections.isEmpty() || endReached ? 0 : 1);
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sections.size()) {
                return sectionArrays.get(sections.get(section)).size() + (section == 0 ? 0 : 1);
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new GraySectionCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_graySection) & 0xf2ffffff);
            }
            if (section == 0) {
                view.setAlpha(0f);
                return view;
            }
            if (section < sections.size()) {
                view.setAlpha(1.0f);
                String name = sections.get(section);
                ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                String str = LocaleController.formatSectionDate(messageObject.messageOwner.date);
                ((GraySectionCell) view).setText(str);

            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(mContext);
                    break;
                case 1:
                    view = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_GLOBAL_SEARCH);
                    break;
                case 2:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    if (currentType == 2 || currentType == 4) {
                        flickerLoadingView.setViewType(FlickerLoadingView.AUDIO_TYPE);
                    } else {
                        flickerLoadingView.setViewType(FlickerLoadingView.FILES_TYPE);
                    }
                    flickerLoadingView.setIsSingleCell(true);
                    view = flickerLoadingView;
                    break;
                case 3:
                default:
                    view = new SharedAudioCell(mContext, SharedAudioCell.VIEW_TYPE_GLOBAL_SEARCH, null) {
                        @Override
                        public boolean needPlayMessage(MessageObject messageObject) {
                            if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                                boolean result = MediaController.getInstance().playMessage(messageObject);
                                MediaController.getInstance().setVoiceMessagesPlaylist(result ? messages : null, false);
                                return result;
                            } else if (messageObject.isMusic()) {
                                MediaController.PlaylistGlobalSearchParams params = new MediaController.PlaylistGlobalSearchParams(currentDataQuery, currentSearchDialogId, currentSearchMinDate, currentSearchMinDate, currentSearchFilter);
                                params.endReached = endReached;
                                params.nextSearchRate = nextSearchRate;
                                params.totalCount = totalCount;
                                params.folderId = currentIncludeFolder ? 1 : 0;
                                return MediaController.getInstance().setPlaylist(messages, messageObject, 0, params);
                            }
                            return false;
                        }
                    };
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2) {
                String name = sections.get(section);
                ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        String str = LocaleController.formatSectionDate(messageObject.messageOwner.date);
                        ((GraySectionCell) holder.itemView).setText(str);
                        break;
                    }
                    case 1: {
                        if (section != 0) {
                            position--;
                        }
                        SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position);
                        boolean animated = sharedDocumentCell.getMessage() != null && sharedDocumentCell.getMessage().getId() == messageObject.getId();
                        sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() - 1 || section == sections.size() - 1 && isLoading);
                        sharedDocumentCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                sharedDocumentCell.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (uiCallback.actionModeShowing()) {
                                    messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                                    sharedDocumentCell.setChecked(uiCallback.isSelected(messageHashIdTmp), animated);
                                } else {
                                    sharedDocumentCell.setChecked(false, animated);
                                }
                                return true;
                            }
                        });
                        break;
                    }
                    case 3: {
                        if (section != 0) {
                            position--;
                        }
                        SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position);
                        boolean animated = sharedAudioCell.getMessage() != null && sharedAudioCell.getMessage().getId() == messageObject.getId();
                        sharedAudioCell.setMessageObject(messageObject, position != messageObjects.size() - 1 || section == sections.size() - 1 && isLoading);
                        sharedAudioCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                sharedAudioCell.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (uiCallback.actionModeShowing()) {
                                    messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                                    sharedAudioCell.setChecked(uiCallback.isSelected(messageHashIdTmp), animated);
                                } else {
                                    sharedAudioCell.setChecked(false, animated);
                                }
                                return true;
                            }
                        });
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section < sections.size()) {
                if (section != 0 && position == 0) {
                    return 0;
                } else {
                    if (currentType == 2 || currentType == 4) {
                        return 3;
                    } else {
                        return 1;
                    }
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = 0;
            position[1] = 0;
        }
    }

    private void openUrl(String link) {
        if (AndroidUtilities.shouldShowUrlInAlert(link)) {
            AlertsCreator.showOpenUrlAlert(parentFragment, link, true, true);
        } else {
            Browser.openUrl(parentActivity, link);
        }
    }

    private void openWebView(TLRPC.WebPage webPage, MessageObject message) {
        EmbedBottomSheet.show(parentFragment, message, provider, webPage.site_name, webPage.description, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height, false);
    }

    int lastAccount;
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(lastAccount = UserConfig.selectedAccount).addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            int n = recyclerListView.getChildCount();
            for (int i = 0; i < n; i++) {
                if (recyclerListView.getChildAt(i) instanceof DialogCell) {
                    ((DialogCell) recyclerListView.getChildAt(i)).update(0);
                }
                recyclerListView.getChildAt(i).invalidate();
            }
        }
    }

    private boolean onItemLongClick(MessageObject item, View view, int a) {
        if (!uiCallback.actionModeShowing()) {
            uiCallback.showActionMode();
        }
        if (uiCallback.actionModeShowing()) {
            uiCallback.toggleItemSelection(item, view, a);
        }
        return true;
    }

    public static class MessageHashId {
        public long dialogId;
        public int messageId;

        public MessageHashId(int messageId, long dialogId) {
            this.dialogId = dialogId;
            this.messageId = messageId;
        }

        public void set(int messageId, long dialogId) {
            this.dialogId = dialogId;
            this.messageId = messageId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageHashId that = (MessageHashId) o;
            return dialogId == that.dialogId && messageId == that.messageId;
        }

        @Override
        public int hashCode() {
            return messageId;
        }
    }

    class OnlyUserFiltersAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new DialogCell(null, parent.getContext(), true, true) {
                        @Override
                        public boolean isForumCell() {
                            return false;
                        }
                    };
                    break;
                case 3:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(parent.getContext());
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE);
                    view = flickerLoadingView;
                    break;
                default:
                case 2:
                    GraySectionCell cell = new GraySectionCell(parent.getContext());
                    cell.setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
                    view = cell;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                DialogCell cell = ((DialogCell) holder.itemView);
                MessageObject messageObject = messages.get(position);
                cell.useFromUserAsAvatar = useFromUserAsAvatar;
                cell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date, false, false);
                cell.useSeparator = position != getItemCount() - 1;
                boolean animated = cell.getMessage() != null && cell.getMessage().getId() == messageObject.getId();
                cell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        cell.getViewTreeObserver().removeOnPreDrawListener(this);
                        if (uiCallback.actionModeShowing()) {
                            messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                            cell.setChecked(uiCallback.isSelected(messageHashIdTmp), animated);
                        } else {
                            cell.setChecked(false, animated);
                        }
                        return true;
                    }
                });
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= messages.size()) {
                return 3;
            }
            return 0;
        }

        @Override
        public int getItemCount() {
            if (messages.isEmpty()) {
                return 0;
            }
            return messages.size() + (endReached ? 0 : 1);
        }
    }

    boolean ignoreRequestLayout;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int oldColumnsCount = columnsCount;
        if (AndroidUtilities.isTablet()) {
            columnsCount = 3;
        } else {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                columnsCount = 6;
            } else {
                columnsCount = 3;
            }
        }
        if (oldColumnsCount != columnsCount && adapter == sharedPhotoVideoAdapter) {
            ignoreRequestLayout = true;
            adapter.notifyDataSetChanged();
            ignoreRequestLayout = false;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void requestLayout() {
        if (ignoreRequestLayout) {
            return;
        }
        super.requestLayout();
    }

    public void setDelegate(Delegate delegate, boolean update) {
        this.delegate = delegate;
        if (update && delegate != null) {
            if (!localTipChats.isEmpty()) {
                delegate.updateFiltersView(false, localTipChats, localTipDates, localTipArchive);
            }
        }
    }

    public void setUiCallback(UiCallback callback) {
        this.uiCallback = callback;
    }

    public interface Delegate {
        void updateFiltersView(boolean showMediaFilters, ArrayList<Object> users, ArrayList<FiltersView.DateData> dates, boolean archive);
    }

    public interface UiCallback {
        void goToMessage(MessageObject messageObject);

        boolean actionModeShowing();

        void toggleItemSelection(MessageObject item, View view, int a);

        boolean isSelected(MessageHashId messageHashId);

        void showActionMode();

        int getFolderId();
    }

    private void showFloatingDateView() {
        AndroidUtilities.cancelRunOnUIThread(hideFloatingDateRunnable);
        AndroidUtilities.runOnUIThread(hideFloatingDateRunnable, 650);
        if (floatingDateView.getTag() != null) {
            return;
        }
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
        }
        floatingDateView.setTag(1);
        floatingDateAnimation = new AnimatorSet();
        floatingDateAnimation.setDuration(180);
        floatingDateAnimation.playTogether(
                ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(floatingDateView, View.TRANSLATION_Y, 0));
        floatingDateAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                floatingDateAnimation = null;
            }
        });
        floatingDateAnimation.start();
    }

    private void hideFloatingDateView(boolean animated) {
        AndroidUtilities.cancelRunOnUIThread(hideFloatingDateRunnable);
        if (floatingDateView.getTag() == null) {
            return;
        }
        floatingDateView.setTag(null);
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        if (animated) {
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(180);
            floatingDateAnimation.playTogether(
                    ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(floatingDateView, View.TRANSLATION_Y, -AndroidUtilities.dp(48)));
            floatingDateAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    floatingDateAnimation = null;
                }
            });
            floatingDateAnimation.start();
        } else {
            floatingDateView.setAlpha(0.0f);
        }
    }

    public void setChatPreviewDelegate(SearchViewPager.ChatPreviewDelegate chatPreviewDelegate) {
        this.chatPreviewDelegate = chatPreviewDelegate;
    }


    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(this, 0, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(this, 0, null, null, null, null, Theme.key_windowBackgroundGray));

        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"dateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{SharedDocumentCell.class}, new String[]{"progressView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"statusImageView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIcon));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"extTextView"}, null, null, null, Theme.key_files_iconText));

        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_titleTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_descriptionTextPaint, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{SharedLinkCell.class}, new String[]{"titleTextPaint"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{SharedLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{SharedLinkCell.class}, Theme.linkSelectionPaint, null, null, Theme.key_windowBackgroundWhiteLinkSelection));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholderText));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholder));

        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_lockDrawable}, null, Theme.key_chats_secretIcon));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_scamDrawable, Theme.dialogs_fakeDrawable}, null, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_pinnedDrawable, Theme.dialogs_reorderDrawable}, null, Theme.key_chats_pinnedIcon));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint[1], null, null, Theme.key_chats_message_threeLines));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint[0], null, null, Theme.key_chats_message));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messageNamePaint, null, null, Theme.key_chats_nameMessage_threeLines));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_draft));

        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, Theme.dialogs_messagePrintingPaint, null, null, Theme.key_chats_actionMessage));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_timePaint, null, null, Theme.key_chats_date));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_pinnedPaint, null, null, Theme.key_chats_pinnedOverlay));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_tabletSeletedPaint, null, null, Theme.key_chats_tabletSelectedOverlay));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkDrawable}, null, Theme.key_chats_sentCheck));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkReadDrawable, Theme.dialogs_halfCheckDrawable}, null, Theme.key_chats_sentReadCheck));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_clockDrawable}, null, Theme.key_chats_sentClock));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, Theme.dialogs_errorPaint, null, null, Theme.key_chats_sentError));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_errorDrawable}, null, Theme.key_chats_sentErrorIcon));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_muteDrawable}, null, Theme.key_chats_muteIcon));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_mentionDrawable}, null, Theme.key_chats_mentionIcon));

        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archivePinBackground));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archiveBackground));

        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_onlineCircle));
        arrayList.add(new ThemeDescription(recyclerListView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOX, new Class[]{DialogCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{DialogCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        arrayList.add(new ThemeDescription(recyclerListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        arrayList.add(new ThemeDescription(emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));


        return arrayList;
    }
}
