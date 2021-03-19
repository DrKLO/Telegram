/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Property;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.StickerEmptyView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

@SuppressWarnings("unchecked")
public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static class MediaPage extends FrameLayout {
        private RecyclerListView listView;
        private FlickerLoadingView progressView;
        private StickerEmptyView emptyView;
        private LinearLayoutManager layoutManager;
        private ClippingImageView animatingImageView;
        private int selectedType;

        public MediaPage(Context context) {
            super(context);
        }
    }

    private SharedPhotoVideoAdapter photoVideoAdapter;
    private SharedLinksAdapter linksAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private SharedDocumentsAdapter voiceAdapter;
    private SharedDocumentsAdapter audioAdapter;
    private MediaSearchAdapter documentsSearchAdapter;
    private MediaSearchAdapter audioSearchAdapter;
    private MediaSearchAdapter linksSearchAdapter;
    private MediaPage[] mediaPages = new MediaPage[2];
    private ActionBarMenuItem searchItem;
    private int searchItemState;
    private Drawable pinnedHeaderShadowDrawable;
    private boolean ignoreSearchCollapse;
    private NumberTextView selectedMessagesCountTextView;
    private ArrayList<SharedPhotoVideoCell> cellCache = new ArrayList<>(10);
    private ArrayList<SharedPhotoVideoCell> cache = new ArrayList<>(10);
    private ArrayList<SharedAudioCell> audioCellCache = new ArrayList<>(10);
    private ArrayList<SharedAudioCell> audioCache = new ArrayList<>(10);
    private FragmentContextView fragmentContextView;
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private View actionModeBackground;

    private int maximumVelocity;

    private Paint backgroundPaint = new Paint();

    private int additionalPadding;

    private boolean searchWas;
    private boolean searching;
    private boolean disableActionBarScrolling;

    private int[] hasMedia;
    private int initialTab;

    private SparseArray<MessageObject>[] selectedFiles = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private int cantDeleteMessagesCount;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ActionBarMenuItem gotoItem;
    private boolean scrolling;
    private long mergeDialogId;
    protected TLRPC.ChatFull info = null;

    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;

    private boolean swipeBackEnabled;

    private long dialog_id;
    private int columnsCount = 3;

    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    public final Property<MediaActivity, Float> SCROLL_Y = new AnimationProperties.FloatProperty<MediaActivity>("animationValue") {
        @Override
        public void setValue(MediaActivity object, float value) {
            object.setScrollY(value);
            for (int a = 0; a < mediaPages.length; a++) {
                mediaPages[a].listView.checkSection();
            }
        }

        @Override
        public Float get(MediaActivity object) {
            return actionBar.getTranslationY();
        }
    };

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (messageObject == null || mediaPages[0].selectedType != 0 && mediaPages[0].selectedType != 1) {
                return null;
            }
            final RecyclerListView listView = mediaPages[0].listView;
            for (int a = 0, count = listView.getChildCount(); a < count; a++) {
                View view = listView.getChildAt(a);
                BackupImageView imageView = null;
                if (view instanceof SharedPhotoVideoCell) {
                    SharedPhotoVideoCell cell = (SharedPhotoVideoCell) view;
                    for (int i = 0; i < 6; i++) {
                        MessageObject message = cell.getMessageObject(i);
                        if (message == null) {
                            break;
                        }
                        if (message.getId() == messageObject.getId()) {
                            imageView = cell.getImageView(i);
                        }
                    }
                } else if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    MessageObject message = cell.getMessage();
                    if (message.getId() == messageObject.getId()) {
                        imageView = cell.getImageView();
                    }
                }
                if (imageView != null) {
                    int[] coords = new int[2];
                    imageView.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = listView;
                    object.animatingImageView = mediaPages[0].animatingImageView;
                    object.imageReceiver = imageView.getImageReceiver();
                    object.radius = object.imageReceiver.getRoundRadius();
                    object.thumb = object.imageReceiver.getBitmapSafe();
                    object.parentView.getLocationInWindow(coords);
                    object.clipTopAddition = (int) (actionBar.getHeight() + actionBar.getTranslationY());
                    if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                        object.clipTopAddition += AndroidUtilities.dp(36);
                    }

                    if (PhotoViewer.isShowingImage(messageObject)) {
                        final View pinnedHeader = listView.getPinnedHeader();
                        if (pinnedHeader != null) {
                            int top = (int) (actionBar.getHeight() + actionBar.getTranslationY());
                            if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                                top += fragmentContextView.getHeight() - AndroidUtilities.dp(2.5f);
                            }
                            if (view instanceof SharedDocumentCell) {
                                top += AndroidUtilities.dp(8f);
                            }
                            final int topOffset = top - object.viewY;
                            if (topOffset > view.getHeight()) {
                                scrollWithoutActionBar(listView, -(topOffset + pinnedHeader.getHeight()));
                            } else {
                                int bottomOffset = object.viewY - listView.getHeight();
                                if (view instanceof SharedDocumentCell) {
                                    bottomOffset -= AndroidUtilities.dp(8f);
                                }
                                if (bottomOffset >= 0) {
                                    scrollWithoutActionBar(listView, bottomOffset + view.getHeight());
                                }
                            }
                        }
                    }

                    return object;
                }
            }
            return null;
        }
    };

    private SharedMediaLayout.SharedMediaData[] sharedMediaData = new SharedMediaLayout.SharedMediaData[6];

    private final static int forward = 3;
    private final static int delete = 4;
    private final static int gotochat = 7;

    public MediaActivity(Bundle args, int[] media) {
        this(args, media, null, MediaDataController.MEDIA_PHOTOVIDEO);
    }

    public MediaActivity(Bundle args, int[] media, SharedMediaLayout.SharedMediaData[] mediaData, int initTab) {
        super(args);
        hasMedia = media;
        initialTab = initTab;
        dialog_id = args.getLong("dialog_id", 0);
        for (int a = 0; a < sharedMediaData.length; a++) {
            sharedMediaData[a] = new SharedMediaLayout.SharedMediaData();
            sharedMediaData[a].max_id[0] = ((int) dialog_id) == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            if (mergeDialogId != 0 && info != null) {
                sharedMediaData[a].max_id[1] = info.migrated_from_max_id;
                sharedMediaData[a].endReached[1] = false;
            }
            if (mediaData != null) {
                sharedMediaData[a].totalCount = mediaData[a].totalCount;
                sharedMediaData[a].messages.addAll(mediaData[a].messages);
                sharedMediaData[a].sections.addAll(mediaData[a].sections);
                for (HashMap.Entry<String, ArrayList<MessageObject>> entry : mediaData[a].sectionArrays.entrySet()) {
                    sharedMediaData[a].sectionArrays.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                for (int i = 0; i < 2; i++) {
                    sharedMediaData[a].endReached[i] = mediaData[a].endReached[i];
                    sharedMediaData[a].messagesDict[i] = mediaData[a].messagesDict[i].clone();
                    sharedMediaData[a].max_id[i] = mediaData[a].max_id[i];
                }
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
    }

    @Override
    public View createView(Context context) {
        for (int a = 0; a < 10; a++) {
            cellCache.add(new SharedPhotoVideoCell(context));
            if (initialTab == MediaDataController.MEDIA_MUSIC) {
                SharedAudioCell cell = new SharedAudioCell(context) {
                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject);
                            MediaController.getInstance().setVoiceMessagesPlaylist(result ? sharedMediaData[MediaDataController.MEDIA_MUSIC].messages : null, false);
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(sharedMediaData[MediaDataController.MEDIA_MUSIC].messages, messageObject, mergeDialogId);
                        }
                        return false;
                    }
                };
                cell.initStreamingIcons();
                audioCellCache.add(cell);
            }
        }

        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        searching = false;
        searchWas = false;
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        int lower_id = (int) dialog_id;
        if (lower_id != 0) {
            if (lower_id > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                if (user != null) {
                    if (user.self) {
                        actionBar.setTitle(LocaleController.getString("SavedMessages", R.string.SavedMessages));
                    } else {
                        actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                    }
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                if (chat != null) {
                    actionBar.setTitle(chat.title);
                }
            }
        } else {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
            if (encryptedChat != null) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                if (user != null) {
                    actionBar.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                }
            }
        }
        if (TextUtils.isEmpty(actionBar.getTitle())) {
            actionBar.setTitle(LocaleController.getString("SharedContentTitle", R.string.SharedContentTitle));
        }
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!closeActionMode()) {
                        finishFragment();
                    }
                } else if (id == delete) {
                    TLRPC.Chat currentChat = null;
                    TLRPC.User currentUser = null;
                    TLRPC.EncryptedChat currentEncryptedChat = null;
                    int lower_id = (int) dialog_id;
                    if (lower_id != 0) {
                        if (lower_id > 0) {
                            currentUser = MessagesController.getInstance(currentAccount).getUser(lower_id);
                        } else {
                            currentChat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                        }
                    } else {
                        currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
                    }
                    AlertsCreator.createDeleteMessagesAlert(MediaActivity.this, currentUser, currentChat, currentEncryptedChat, null, mergeDialogId, null, selectedFiles, null, false, 1, () -> {
                        actionBar.hideActionMode();
                        actionBar.closeSearchField();
                        cantDeleteMessagesCount = 0;
                    });
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate((fragment1, dids, message, param) -> {
                        ArrayList<MessageObject> fmessages = new ArrayList<>();
                        for (int a = 1; a >= 0; a--) {
                            ArrayList<Integer> ids = new ArrayList<>();
                            for (int b = 0; b < selectedFiles[a].size(); b++) {
                                ids.add(selectedFiles[a].keyAt(b));
                            }
                            Collections.sort(ids);
                            for (Integer id1 : ids) {
                                if (id1 > 0) {
                                    fmessages.add(selectedFiles[a].get(id1));
                                }
                            }
                            selectedFiles[a].clear();
                        }
                        cantDeleteMessagesCount = 0;
                        actionBar.hideActionMode();

                        if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
                            updateRowsSelection();
                            for (int a = 0; a < dids.size(); a++) {
                                long did = dids.get(a);
                                if (message != null) {
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0);
                                }
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did, true, 0);
                            }
                            fragment1.finishFragment();
                        } else {
                            long did = dids.get(0);
                            int lower_part = (int) did;
                            int high_part = (int) (did >> 32);
                            Bundle args1 = new Bundle();
                            args1.putBoolean("scrollToTopOnResume", true);
                            if (lower_part != 0) {
                                if (lower_part > 0) {
                                    args1.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args1.putInt("chat_id", -lower_part);
                                }
                            } else {
                                args1.putInt("enc_id", high_part);
                            }
                            if (lower_part != 0) {
                                if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment1)) {
                                    return;
                                }
                            }

                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);

                            ChatActivity chatActivity = new ChatActivity(args1);
                            presentFragment(chatActivity, true);
                            chatActivity.showFieldPanelForForward(true, fmessages);

                            if (!AndroidUtilities.isTablet()) {
                                removeSelfFromStack();
                            }
                        }
                    });
                    presentFragment(fragment);
                } else if (id == gotochat) {
                    if (selectedFiles[0].size() != 1) {
                        return;
                    }
                    Bundle args = new Bundle();
                    int lower_part = (int) dialog_id;
                    int high_id = (int) (dialog_id >> 32);
                    if (lower_part != 0) {
                        if (lower_part > 0) {
                            args.putInt("user_id", lower_part);
                        } else if (lower_part < 0) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_part);
                            if (chat != null && chat.migrated_to != null) {
                                args.putInt("migrated_to", lower_part);
                                lower_part = -chat.migrated_to.channel_id;
                            }
                            args.putInt("chat_id", -lower_part);
                        }
                    } else {
                        args.putInt("enc_id", high_id);
                    }
                    args.putInt("message_id", selectedFiles[0].keyAt(0));
                    args.putBoolean("need_remove_previous_same_chat_activity", false);
                    presentFragment(new ChatActivity(args), false);
                }
            }
        });

        pinnedHeaderShadowDrawable = context.getResources().getDrawable(R.drawable.photos_header_shadow);
        pinnedHeaderShadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundGrayShadow), PorterDuff.Mode.MULTIPLY));

        if (scrollSlidingTextTabStrip != null) {
            initialTab = scrollSlidingTextTabStrip.getCurrentTabId();
        }

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        if (initialTab != -1) {
            scrollSlidingTextTabStrip.setInitialTabId(initialTab);
            initialTab = -1;
        }
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (mediaPages[0].selectedType == id) {
                    return;
                }
                swipeBackEnabled = id == scrollSlidingTextTabStrip.getFirstTabId();
                mediaPages[1].selectedType = id;
                mediaPages[1].setVisibility(View.VISIBLE);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1 && mediaPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    mediaPages[0].setTranslationX(-progress * mediaPages[0].getMeasuredWidth());
                    mediaPages[1].setTranslationX(mediaPages[0].getMeasuredWidth() - progress * mediaPages[0].getMeasuredWidth());
                } else {
                    mediaPages[0].setTranslationX(progress * mediaPages[0].getMeasuredWidth());
                    mediaPages[1].setTranslationX(progress * mediaPages[0].getMeasuredWidth() - mediaPages[0].getMeasuredWidth());
                }
                if (searchItemState == 1) {
                    searchItem.setAlpha(progress);
                } else if (searchItemState == 2) {
                    searchItem.setAlpha(1.0f - progress);
                }
                if (progress == 1) {
                    MediaPage tempPage = mediaPages[0];
                    mediaPages[0] = mediaPages[1];
                    mediaPages[1] = tempPage;
                    mediaPages[1].setVisibility(View.GONE);
                    if (searchItemState == 2) {
                        searchItem.setVisibility(View.INVISIBLE);
                    }
                    searchItemState = 0;
                }
            }
        });

        for (int a = 1; a >= 0; a--) {
            selectedFiles[a].clear();
        }
        cantDeleteMessagesCount = 0;
        actionModeViews.clear();

        final ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                resetScroll();
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                documentsSearchAdapter.search(null);
                linksSearchAdapter.search(null);
                audioSearchAdapter.search(null);
                if (ignoreSearchCollapse) {
                    ignoreSearchCollapse = false;
                    return;
                }

                switchToCurrentSelectedMode(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    switchToCurrentSelectedMode(false);
                } else {
                    searchWas = false;
                    switchToCurrentSelectedMode(false);
                }
                if (mediaPages[0].selectedType == 1) {
                    if (documentsSearchAdapter == null) {
                        return;
                    }
                    documentsSearchAdapter.search(text);
                } else if (mediaPages[0].selectedType == 3) {
                    if (linksSearchAdapter == null) {
                        return;
                    }
                    linksSearchAdapter.search(text);
                } else if (mediaPages[0].selectedType == 4) {
                    if (audioSearchAdapter == null) {
                        return;
                    }
                    audioSearchAdapter.search(text);
                }
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        searchItem.setVisibility(View.INVISIBLE);
        searchItemState = 0;
        hasOwnBackground = true;

        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);
        actionMode.setBackgroundDrawable(null);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), true);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), true);

        actionModeBackground = new View(context);
        actionModeBackground.setBackgroundColor(Theme.getColor(Theme.key_sharedMedia_actionMode));
        actionModeBackground.setAlpha(0.0f);
        actionBar.addView(actionModeBackground, actionBar.indexOfChild(actionMode));

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
        selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));

        if ((int) dialog_id != 0) {
            actionModeViews.add(gotoItem = actionMode.addItemWithWidth(gotochat, R.drawable.msg_message, AndroidUtilities.dp(54), LocaleController.getString("AccDescrGoToMessage", R.string.AccDescrGoToMessage)));
            actionModeViews.add(actionMode.addItemWithWidth(forward, R.drawable.msg_forward, AndroidUtilities.dp(54), LocaleController.getString("Forward", R.string.Forward)));
        }
        actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete)));

        photoVideoAdapter = new SharedPhotoVideoAdapter(context);
        documentsAdapter = new SharedDocumentsAdapter(context, 1);
        voiceAdapter = new SharedDocumentsAdapter(context, 2);
        audioAdapter = new SharedDocumentsAdapter(context, 4);
        documentsSearchAdapter = new MediaSearchAdapter(context, 1);
        audioSearchAdapter = new MediaSearchAdapter(context, 4);
        linksSearchAdapter = new MediaSearchAdapter(context, 3);
        linksAdapter = new SharedLinksAdapter(context);

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context) {

            private int startedTrackingPointerId;
            private boolean startedTracking;
            private boolean maybeStartTracking;
            private int startedTrackingX;
            private int startedTrackingY;
            private VelocityTracker velocityTracker;
            private boolean globalIgnoreLayout;

            private boolean prepareForMoving(MotionEvent ev, boolean forward) {
                int id = scrollSlidingTextTabStrip.getNextPageId(forward);
                if (id < 0) {
                    return false;
                }
                if (searchItemState != 0) {
                    if (searchItemState == 2) {
                        searchItem.setAlpha(1.0f);
                    } else if (searchItemState == 1) {
                        searchItem.setAlpha(0.0f);
                        searchItem.setVisibility(View.INVISIBLE);
                    }
                    searchItemState = 0;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                maybeStartTracking = false;
                startedTracking = true;
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                scrollSlidingTextTabStrip.setEnabled(false);
                mediaPages[1].selectedType = id;
                mediaPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                switchToCurrentSelectedMode(true);
                if (forward) {
                    mediaPages[1].setTranslationX(mediaPages[0].getMeasuredWidth());
                } else {
                    mediaPages[1].setTranslationX(-mediaPages[0].getMeasuredWidth());
                }
                return true;
            }

            @Override
            public void forceHasOverlappingRendering(boolean hasOverlappingRendering) {
                super.forceHasOverlappingRendering(hasOverlappingRendering);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                globalIgnoreLayout = true;
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a] == null) {
                        continue;
                    }
                    if (mediaPages[a].listView != null) {
                        mediaPages[a].listView.setPadding(0, actionBarHeight + additionalPadding, 0, AndroidUtilities.dp(4));
                    }
                    if (mediaPages[a].emptyView != null) {
                        mediaPages[a].emptyView.setPadding(0, actionBarHeight + additionalPadding, 0, 0);
                    }
                    if (mediaPages[a].progressView != null) {
                        mediaPages[a].progressView.setPadding(0, actionBarHeight + additionalPadding, 0, 0);
                    }
                }
                globalIgnoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (fragmentContextView != null) {
                    int y = actionBar.getMeasuredHeight();
                    fragmentContextView.layout(fragmentContextView.getLeft(), fragmentContextView.getTop() + y, fragmentContextView.getRight(), fragmentContextView.getBottom() + y);
                }
            }

            @Override
            public void setPadding(int left, int top, int right, int bottom) {
                additionalPadding = top;
                if (fragmentContextView != null) {
                    fragmentContextView.setTranslationY(top + actionBar.getTranslationY());
                }
                int actionBarHeight = actionBar.getMeasuredHeight();
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a] == null) {
                        continue;
                    }
                    if (mediaPages[a].emptyView != null) {
                        mediaPages[a].emptyView.setPadding(0, actionBarHeight + additionalPadding, 0, 0);
                    }
                    if (mediaPages[a].progressView != null) {
                        mediaPages[a].progressView.setPadding(0, actionBarHeight + additionalPadding, 0, 0);
                    }
                    if (mediaPages[a].listView != null) {
                        mediaPages[a].listView.setPadding(0, actionBarHeight + additionalPadding, 0, AndroidUtilities.dp(4));
                        mediaPages[a].listView.checkSection();
                    }
                }
                fixScrollOffset();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
                }
                if (fragmentContextView != null && fragmentContextView.isCallStyle()) {
                    canvas.save();
                    canvas.translate(fragmentContextView.getX(), fragmentContextView.getY());
                    fragmentContextView.setDrawOverlay(true);
                    fragmentContextView.draw(canvas);
                    fragmentContextView.setDrawOverlay(false);
                    canvas.restore();
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == fragmentContextView && fragmentContextView.isCallStyle()) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public void requestLayout() {
                if (globalIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            public boolean checkTabsAnimationInProgress() {
                if (tabsAnimationInProgress) {
                    boolean cancel = false;
                    if (backAnimation) {
                        if (Math.abs(mediaPages[0].getTranslationX()) < 1) {
                            mediaPages[0].setTranslationX(0);
                            mediaPages[1].setTranslationX(mediaPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                            cancel = true;
                        }
                    } else if (Math.abs(mediaPages[1].getTranslationX()) < 1) {
                        mediaPages[0].setTranslationX(mediaPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                        mediaPages[1].setTranslationX(0);
                        cancel = true;
                    }
                    if (cancel) {
                        if (tabsAnimation != null) {
                            tabsAnimation.cancel();
                            tabsAnimation = null;
                        }
                        tabsAnimationInProgress = false;
                    }
                    return tabsAnimationInProgress;
                }
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip.isAnimatingIndicator() || onTouchEvent(ev);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawRect(0, actionBar.getMeasuredHeight() + actionBar.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!parentLayout.checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
                    if (ev != null) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.addMovement(ev);
                    }
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        velocityTracker.clear();
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                                mediaPages[0].setTranslationX(0);
                                mediaPages[1].setTranslationX(animatingForward ? mediaPages[0].getMeasuredWidth() : -mediaPages[0].getMeasuredWidth());
                                scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, 0);
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) > dy) {
                                prepareForMoving(ev, dx < 0);
                            }
                        } else if (startedTracking) {
                            mediaPages[0].setTranslationX(dx);
                            if (animatingForward) {
                                mediaPages[1].setTranslationX(mediaPages[0].getMeasuredWidth() + dx);
                            } else {
                                mediaPages[1].setTranslationX(dx - mediaPages[0].getMeasuredWidth());
                            }
                            float scrollProgress = Math.abs(dx) / (float) mediaPages[0].getMeasuredWidth();
                            if (searchItemState == 2) {
                                searchItem.setAlpha(1.0f - scrollProgress);
                            } else if (searchItemState == 1) {
                                searchItem.setAlpha(scrollProgress);
                            }
                            scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                        float velX;
                        float velY;
                        if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                            velX = velocityTracker.getXVelocity();
                            velY = velocityTracker.getYVelocity();
                            if (!startedTracking) {
                                if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                    prepareForMoving(ev, velX < 0);
                                }
                            }
                        } else {
                            velX = 0;
                            velY = 0;
                        }
                        if (startedTracking) {
                            float x = mediaPages[0].getX();
                            tabsAnimation = new AnimatorSet();
                            backAnimation = Math.abs(x) < mediaPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float distToMove;
                            float dx;
                            if (backAnimation) {
                                dx = Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, mediaPages[1].getMeasuredWidth())
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, -mediaPages[1].getMeasuredWidth())
                                    );
                                }
                            } else {
                                dx = mediaPages[0].getMeasuredWidth() - Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, -mediaPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, 0)
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, mediaPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, 0)
                                    );
                                }
                            }
                            tabsAnimation.setInterpolator(interpolator);

                            int width = getMeasuredWidth();
                            int halfWidth = width / 2;
                            float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                            float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
                            velX = Math.abs(velX);
                            int duration;
                            if (velX > 0) {
                                duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                            } else {
                                float pageDelta = dx / getMeasuredWidth();
                                duration = (int) ((pageDelta + 1.0f) * 100.0f);
                            }
                            duration = Math.max(150, Math.min(duration, 600));

                            tabsAnimation.setDuration(duration);
                            tabsAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    tabsAnimation = null;
                                    if (backAnimation) {
                                        mediaPages[1].setVisibility(View.GONE);
                                        if (searchItemState == 2) {
                                            searchItem.setAlpha(1.0f);
                                        } else if (searchItemState == 1) {
                                            searchItem.setAlpha(0.0f);
                                            searchItem.setVisibility(View.INVISIBLE);
                                        }
                                        searchItemState = 0;
                                    } else {
                                        MediaPage tempPage = mediaPages[0];
                                        mediaPages[0] = mediaPages[1];
                                        mediaPages[1] = tempPage;
                                        mediaPages[1].setVisibility(View.GONE);
                                        if (searchItemState == 2) {
                                            searchItem.setVisibility(View.INVISIBLE);
                                        }
                                        searchItemState = 0;
                                        swipeBackEnabled = mediaPages[0].selectedType == scrollSlidingTextTabStrip.getFirstTabId();
                                        scrollSlidingTextTabStrip.selectTabWithId(mediaPages[0].selectedType, 1.0f);
                                    }
                                    tabsAnimationInProgress = false;
                                    maybeStartTracking = false;
                                    startedTracking = false;
                                    actionBar.setEnabled(true);
                                    scrollSlidingTextTabStrip.setEnabled(true);
                                }
                            });
                            tabsAnimation.start();
                            tabsAnimationInProgress = true;
                            startedTracking = false;
                        } else {
                            maybeStartTracking = false;
                            actionBar.setEnabled(true);
                            scrollSlidingTextTabStrip.setEnabled(true);
                        }
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                    }
                    return startedTracking;
                }
                return false;
            }
        };
        frameLayout.setWillNotDraw(false);

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;

        for (int a = 0; a < mediaPages.length; a++) {
            if (a == 0) {
                if (mediaPages[a] != null && mediaPages[a].layoutManager != null) {
                    scrollToPositionOnRecreate = mediaPages[a].layoutManager.findFirstVisibleItemPosition();
                    if (scrollToPositionOnRecreate != mediaPages[a].layoutManager.getItemCount() - 1) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) mediaPages[a].listView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                        if (holder != null) {
                            scrollToOffsetOnRecreate = holder.itemView.getTop();
                        } else {
                            scrollToPositionOnRecreate = -1;
                        }
                    } else {
                        scrollToPositionOnRecreate = -1;
                    }
                }
            }
            final MediaPage mediaPage = new MediaPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress) {
                        if (mediaPages[0] == this) {
                            float scrollProgress = Math.abs(mediaPages[0].getTranslationX()) / (float) mediaPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress);
                            if (searchItemState == 2) {
                                searchItem.setAlpha(1.0f - scrollProgress);
                            } else if (searchItemState == 1) {
                                searchItem.setAlpha(scrollProgress);
                            }
                        }
                    }
                }
            };
            frameLayout.addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a] = mediaPage;

            final LinearLayoutManager layoutManager = mediaPages[a].layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }

                @Override
                protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
                    super.calculateExtraLayoutSpace(state, extraLayoutSpace);
                    if (mediaPage.selectedType == 0) {
                        extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], SharedPhotoVideoCell.getItemSize(columnsCount) * 2);
                    } else if (mediaPage.selectedType == 1) {
                        extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], AndroidUtilities.dp(56f) * 2);
                    }
                }
            };
            mediaPages[a].listView = new RecyclerListView(context) {
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    updateSections(this, true);
                }
            };
            mediaPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            mediaPages[a].listView.setItemAnimator(null);
            mediaPages[a].listView.setClipToPadding(false);
            mediaPages[a].listView.setSectionsType(2);
            mediaPages[a].listView.setLayoutManager(layoutManager);
            mediaPages[a].addView(mediaPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].listView.setOnItemClickListener((view, position) -> {
                if (mediaPage.selectedType == 1 && view instanceof SharedDocumentCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedDocumentCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if (mediaPage.selectedType == 3 && view instanceof SharedLinkCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedLinkCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view instanceof SharedAudioCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedAudioCell) view).getMessage(), 0, mediaPage.selectedType);
                }
            });
            mediaPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                    if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
                        int scrollY = (int) -actionBar.getTranslationY();
                        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
                        if (scrollY != 0 && scrollY != actionBarHeight) {
                            if (scrollY < actionBarHeight / 2) {
                                mediaPages[0].listView.smoothScrollBy(0, -scrollY);
                            } else if (mediaPages[0].listView.canScrollVertically(1)) {
                                mediaPages[0].listView.smoothScrollBy(0, actionBarHeight - scrollY);
                            }
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (searching && searchWas) {
                        return;
                    }
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    int totalItemCount = recyclerView.getAdapter().getItemCount();

                    final int threshold = mediaPage.selectedType == 0 ? 3 : 6;
                    if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - threshold && !sharedMediaData[mediaPage.selectedType].loading) {
                        int type;
                        if (mediaPage.selectedType == 0) {
                            type = MediaDataController.MEDIA_PHOTOVIDEO;
                        } else if (mediaPage.selectedType == 1) {
                            type = MediaDataController.MEDIA_FILE;
                        } else if (mediaPage.selectedType == 2) {
                            type = MediaDataController.MEDIA_AUDIO;
                        } else if (mediaPage.selectedType == 4) {
                            type = MediaDataController.MEDIA_MUSIC;
                        } else if (mediaPage.selectedType == 5) {
                            type = MediaDataController.MEDIA_GIF;
                        } else {
                            type = MediaDataController.MEDIA_URL;
                        }
                        if (!sharedMediaData[mediaPage.selectedType].endReached[0]) {
                            sharedMediaData[mediaPage.selectedType].loading = true;
                            MediaDataController.getInstance(currentAccount).loadMedia(dialog_id, 50, sharedMediaData[mediaPage.selectedType].max_id[0], type, 1, classGuid);
                        } else if (mergeDialogId != 0 && !sharedMediaData[mediaPage.selectedType].endReached[1]) {
                            sharedMediaData[mediaPage.selectedType].loading = true;
                            MediaDataController.getInstance(currentAccount).loadMedia(mergeDialogId, 50, sharedMediaData[mediaPage.selectedType].max_id[1], type, 1, classGuid);
                        }
                    }
                    if (recyclerView == mediaPages[0].listView && !searching && !actionBar.isActionModeShowed() && !disableActionBarScrolling) {
                        float currentTranslation = actionBar.getTranslationY();
                        float newTranslation = currentTranslation - dy;
                        if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
                            newTranslation = -ActionBar.getCurrentActionBarHeight();
                        } else if (newTranslation > 0) {
                            newTranslation = 0;
                        }
                        if (newTranslation != currentTranslation) {
                            setScrollY(newTranslation);
                        }
                    }
                    updateSections(recyclerView, false);
                }
            });
            mediaPages[a].listView.setOnItemLongClickListener((view, position) -> {
                if (actionBar.isActionModeShowed()) {
                    mediaPage.listView.getOnItemClickListener().onItemClick(view, position);
                    return true;
                }
                if (mediaPage.selectedType == 1 && view instanceof SharedDocumentCell) {
                    return MediaActivity.this.onItemLongClick(((SharedDocumentCell) view).getMessage(), view, 0);
                } else if (mediaPage.selectedType == 3 && view instanceof SharedLinkCell) {
                    return MediaActivity.this.onItemLongClick(((SharedLinkCell) view).getMessage(), view, 0);
                } else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view instanceof SharedAudioCell) {
                    return MediaActivity.this.onItemLongClick(((SharedAudioCell) view).getMessage(), view, 0);
                }
                return false;
            });
            if (a == 0 && scrollToPositionOnRecreate != -1) {
                layoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            }

            final RecyclerListView listView = mediaPages[a].listView;
            mediaPages[a].animatingImageView = new ClippingImageView(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    listView.invalidate();
                }
            };
            mediaPages[a].animatingImageView.setVisibility(View.GONE);
            mediaPages[a].listView.addOverlayView(mediaPages[a].animatingImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            mediaPages[a].progressView = new FlickerLoadingView(context) {

                @Override
                public int getColumnsCount() {
                    return columnsCount;
                }

                @Override
                public int getViewType() {
                    if (mediaPage.selectedType == 0 || mediaPage.selectedType == 5) {
                        return 2;
                    } else if (mediaPage.selectedType == 1) {
                        return 3;
                    } else if (mediaPage.selectedType == 2 || mediaPage.selectedType == 4) {
                        return 4;
                    } else if (mediaPage.selectedType == 3) {
                        return 5;
                    } else if (mediaPage.selectedType == 7) {
                        return FlickerLoadingView.USERS_TYPE;
                    }
                    return 1;
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
                    super.onDraw(canvas);
                }
            };
            mediaPages[a].progressView.setUseHeaderOffset(true);
            mediaPages[a].progressView.showDate(false);
            if (a != 0) {
                mediaPages[a].setVisibility(View.GONE);
            }

            mediaPages[a].emptyView = new StickerEmptyView(context, mediaPages[a].progressView, StickerEmptyView.STICKER_TYPE_SEARCH);
            mediaPages[a].emptyView.setVisibility(View.GONE);
            mediaPages[a].emptyView.setAnimateLayoutChange(true);
            mediaPages[a].addView(mediaPages[a].emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].emptyView.setOnTouchListener((v, event) -> true);
            mediaPages[a].emptyView.showProgress(true, false);
            mediaPages[a].emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
            mediaPages[a].emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
            mediaPages[a].emptyView.addView(mediaPages[a].progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            mediaPages[a].listView.setEmptyView(mediaPages[a].emptyView);
            mediaPages[a].listView.setAnimateEmptyView(true, 0);
        }

        if (!AndroidUtilities.isTablet()) {
            frameLayout.addView(fragmentContextView = new FragmentContextView(context, this, false), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 0, 8, 0, 0));
        }

        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateTabs();
        switchToCurrentSelectedMode(false);
        swipeBackEnabled = scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip.getFirstTabId();

        return fragmentView;
    }

    private boolean closeActionMode() {
        if (actionBar.isActionModeShowed()) {
            for (int a = 1; a >= 0; a--) {
                selectedFiles[a].clear();
            }
            cantDeleteMessagesCount = 0;
            actionBar.hideActionMode();
            updateRowsSelection();
            return true;
        } else {
            return false;
        }
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        if (fragmentContextView != null) {
            fragmentContextView.setTranslationY(additionalPadding + value);
        }
        for (int a = 0; a < mediaPages.length; a++) {
            mediaPages[a].listView.setPinnedSectionOffsetY((int) value);
        }
        fragmentView.invalidate();
    }

    private void resetScroll() {
        if (actionBar.getTranslationY() == 0) {
            return;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, SCROLL_Y, 0));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mediaDidLoad) {
            long uid = (Long) args[0];
            int guid = (Integer) args[3];
            if (guid == classGuid) {
                int type = (Integer) args[4];
                sharedMediaData[type].loading = false;
                sharedMediaData[type].totalCount = (Integer) args[1];
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                boolean enc = ((int) dialog_id) == 0;
                int loadIndex = uid == dialog_id ? 0 : 1;

                RecyclerListView.Adapter adapter = null;
                if (type == 0) {
                    adapter = photoVideoAdapter;
                } else if (type == 1) {
                    adapter = documentsAdapter;
                } else if (type == 2) {
                    adapter = voiceAdapter;
                } else if (type == 3) {
                    adapter = linksAdapter;
                } else if (type == 4) {
                    adapter = audioAdapter;
                }
                int oldItemCount;
                if (adapter != null) {
                    oldItemCount = adapter.getItemCount();
                    if (adapter instanceof RecyclerListView.SectionsAdapter) {
                        RecyclerListView.SectionsAdapter sectionsAdapter = (RecyclerListView.SectionsAdapter) adapter;
                        sectionsAdapter.notifySectionsChanged();
                    }
                } else {
                    oldItemCount = 0;
                }
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject message = arr.get(a);
                    sharedMediaData[type].addMessage(message, loadIndex, false, enc);
                }
                sharedMediaData[type].endReached[loadIndex] = (Boolean) args[5];
                if (loadIndex == 0 && sharedMediaData[type].endReached[loadIndex] && mergeDialogId != 0) {
                    sharedMediaData[type].loading = true;
                    MediaDataController.getInstance(currentAccount).loadMedia(mergeDialogId, 50, sharedMediaData[type].max_id[1], type, 1, classGuid);
                }
                if (adapter != null) {
                    for (int a = 0; a < mediaPages.length; a++) {
                        if (mediaPages[a].listView.getAdapter() == adapter) {
                            mediaPages[a].listView.stopScroll();
                        }
                    }
                    if (sharedMediaData[type].messages.size() == 0 && !sharedMediaData[type].loading) {
                        adapter.notifyDataSetChanged();
                    } else {
                        int newItemCount = adapter.getItemCount();
                        if (oldItemCount > 1) {
                            adapter.notifyItemChanged(oldItemCount - 2);
                        }
                        if (newItemCount > oldItemCount) {
                            adapter.notifyItemRangeInserted(oldItemCount, newItemCount);
                        } else if (newItemCount < oldItemCount) {
                            adapter.notifyItemRangeRemoved(newItemCount, (oldItemCount - newItemCount));
                        }
                    }
                }
                scrolling = true;
                for (int a = 0; a < mediaPages.length; a++) {
                    if (oldItemCount == 0 && actionBar.getTranslationY() != 0 && mediaPages[a].listView.getAdapter() == adapter) {
                        mediaPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
                    }
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            TLRPC.Chat currentChat = null;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance(currentAccount).getChat(-(int) dialog_id);
            }
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
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            boolean updated = false;
            for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                for (int b = 0; b < sharedMediaData.length; b++) {
                    if (sharedMediaData[b].deleteMessage(markAsDeletedMessages.get(a), loadIndex) != null) {
                        updated = true;
                    }
                }
            }
            if (updated) {
                scrolling = true;
                if (photoVideoAdapter != null) {
                    photoVideoAdapter.notifyDataSetChanged();
                }
                if (documentsAdapter != null) {
                    documentsAdapter.notifyDataSetChanged();
                }
                if (voiceAdapter != null) {
                    voiceAdapter.notifyDataSetChanged();
                }
                if (linksAdapter != null) {
                    linksAdapter.notifyDataSetChanged();
                }
                if (audioAdapter != null) {
                    audioAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            long uid = (Long) args[0];
            if (uid == dialog_id) {
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                boolean enc = ((int) dialog_id) == 0;
                boolean updated = false;
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject obj = arr.get(a);
                    if (obj.messageOwner.media == null || obj.needDrawBluredPreview()) {
                        continue;
                    }
                    int type = MediaDataController.getMediaType(obj.messageOwner);
                    if (type == -1) {
                        return;
                    }
                    if (sharedMediaData[type].addMessage(obj, obj.getDialogId() == dialog_id ? 0 : 1, true, enc)) {
                        updated = true;
                        hasMedia[type] = 1;
                    }
                }
                if (updated) {
                    scrolling = true;
                    for (int a = 0; a < mediaPages.length; a++) {
                        RecyclerListView.Adapter adapter = null;
                        if (mediaPages[a].selectedType == 0) {
                            adapter = photoVideoAdapter;
                        } else if (mediaPages[a].selectedType == 1) {
                            adapter = documentsAdapter;
                        } else if (mediaPages[a].selectedType == 2) {
                            adapter = voiceAdapter;
                        } else if (mediaPages[a].selectedType == 3) {
                            adapter = linksAdapter;
                        } else if (mediaPages[a].selectedType == 4) {
                            adapter = audioAdapter;
                        }
                        if (adapter != null) {
                            int count = adapter.getItemCount();
                            photoVideoAdapter.notifyDataSetChanged();
                            documentsAdapter.notifyDataSetChanged();
                            voiceAdapter.notifyDataSetChanged();
                            linksAdapter.notifyDataSetChanged();
                            audioAdapter.notifyDataSetChanged();
                            if (count == 0 && actionBar.getTranslationY() != 0) {
                                mediaPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
                            }
                        }
                    }
                    updateTabs();
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Boolean scheduled = (Boolean) args[6];
            if (scheduled) {
                return;
            }
            Integer msgId = (Integer) args[0];
            Integer newMsgId = (Integer) args[1];
            for (int a = 0; a < sharedMediaData.length; a++) {
                sharedMediaData[a].replaceMid(msgId, newMsgId);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        scrolling = true;
        if (photoVideoAdapter != null) {
            photoVideoAdapter.notifyDataSetChanged();
        }
        if (documentsAdapter != null) {
            documentsAdapter.notifyDataSetChanged();
        }
        if (linksAdapter != null) {
            linksAdapter.notifyDataSetChanged();
        }
        for (int a = 0; a < mediaPages.length; a++) {
            fixLayoutInternal(a);
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return swipeBackEnabled;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (int a = 0; a < mediaPages.length; a++) {
            if (mediaPages[a].listView != null) {
                final int num = a;
                ViewTreeObserver obs = mediaPages[a].listView.getViewTreeObserver();
                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        mediaPages[num].getViewTreeObserver().removeOnPreDrawListener(this);
                        fixLayoutInternal(num);
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        return actionBar.isEnabled() && !closeActionMode();
    }

    private void updateSections(RecyclerView listView, boolean checkTopBottom) {
        int count = listView.getChildCount();
        int minPositionDateHolder = Integer.MAX_VALUE;
        View minDateChild = null;
        float padding = listView.getPaddingTop() + actionBar.getTranslationY();
        int minTop = Integer.MAX_VALUE;
        int maxBottom = 0;

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            int bottom = view.getBottom();
            minTop = Math.min(minTop, view.getTop());
            maxBottom = Math.max(bottom, maxBottom);
            if (bottom <= padding) {
                continue;
            }
            int position = view.getBottom();
            if (view instanceof SharedMediaSectionCell || view instanceof GraySectionCell) {
                if (view.getAlpha() != 1.0f) {
                    view.setAlpha(1.0f);
                }
                if (position < minPositionDateHolder) {
                    minPositionDateHolder = position;
                    minDateChild = view;
                }
            }
        }
        if (minDateChild != null) {
            if (minDateChild.getTop() > padding) {
                if (minDateChild.getAlpha() != 1.0f) {
                    minDateChild.setAlpha(1.0f);
                }
            } else {
                if (minDateChild.getAlpha() != 0.0f) {
                    minDateChild.setAlpha(0.0f);
                }
            }
        }
        if (checkTopBottom) {
            if (maxBottom != 0 && maxBottom < (listView.getMeasuredHeight() - listView.getPaddingBottom())) {
                resetScroll();
            } else if (minTop != Integer.MAX_VALUE && minTop > listView.getPaddingTop() + actionBar.getTranslationY()) {
                scrollWithoutActionBar(listView, -listView.computeVerticalScrollOffset());
                resetScroll();
            }
        }
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (info != null && info.migrated_from_chat_id != 0 && mergeDialogId == 0) {
            mergeDialogId = -info.migrated_from_chat_id;
            for (int a = 0; a < sharedMediaData.length; a++) {
                sharedMediaData[a].max_id[1] = info.migrated_from_max_id;
                sharedMediaData[a].endReached[1] = false;
            }
        }
    }

    public void updateAdapters() {
        if (photoVideoAdapter != null) {
            photoVideoAdapter.notifyDataSetChanged();
        }
        if (documentsAdapter != null) {
            documentsAdapter.notifyDataSetChanged();
        }
        if (voiceAdapter != null) {
            voiceAdapter.notifyDataSetChanged();
        }
        if (linksAdapter != null) {
            linksAdapter.notifyDataSetChanged();
        }
        if (audioAdapter != null) {
            audioAdapter.notifyDataSetChanged();
        }
    }

    private void updateRowsSelection() {
        for (int i = 0; i < mediaPages.length; i++) {
            int count = mediaPages[i].listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = mediaPages[i].listView.getChildAt(a);
                if (child instanceof SharedDocumentCell) {
                    ((SharedDocumentCell) child).setChecked(false, true);
                } else if (child instanceof SharedPhotoVideoCell) {
                    for (int b = 0; b < 6; b++) {
                        ((SharedPhotoVideoCell) child).setChecked(b, false, true);
                    }
                } else if (child instanceof SharedLinkCell) {
                    ((SharedLinkCell) child).setChecked(false, true);
                } else if (child instanceof SharedAudioCell) {
                    ((SharedAudioCell) child).setChecked(false, true);
                }
            }
        }
    }

    public void setMergeDialogId(long did) {
        mergeDialogId = did;
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        boolean changed = false;
        if ((hasMedia[0] != 0 || hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0) && !scrollSlidingTextTabStrip.hasTab(0)) {
            changed = true;
        }
        if (hasMedia[1] != 0) {
            if (!scrollSlidingTextTabStrip.hasTab(1)) {
                changed = true;
            }
        }
        if ((int) dialog_id != 0) {
            if (hasMedia[3] != 0 && !scrollSlidingTextTabStrip.hasTab(3)) {
                changed = true;
            }
            if (hasMedia[4] != 0 && !scrollSlidingTextTabStrip.hasTab(4)) {
                changed = true;
            }
        } else {
            TLRPC.EncryptedChat currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
            if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46) {
                if (hasMedia[4] != 0 && !scrollSlidingTextTabStrip.hasTab(4)) {
                    changed = true;
                }
            }
        }
        if (hasMedia[2] != 0 && !scrollSlidingTextTabStrip.hasTab(2)) {
            changed = true;
        }
        if (changed) {
            scrollSlidingTextTabStrip.removeTabs();
            if (hasMedia[0] != 0 || hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0) {
                if (!scrollSlidingTextTabStrip.hasTab(0)) {
                    scrollSlidingTextTabStrip.addTextTab(0, LocaleController.getString("SharedMediaTab2", R.string.SharedMediaTab2));
                }
            }
            if (hasMedia[1] != 0) {
                if (!scrollSlidingTextTabStrip.hasTab(1)) {
                    scrollSlidingTextTabStrip.addTextTab(1, LocaleController.getString("SharedFilesTab2", R.string.SharedFilesTab2));
                }
            }
            if ((int) dialog_id != 0) {
                if (hasMedia[3] != 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(3)) {
                        scrollSlidingTextTabStrip.addTextTab(3, LocaleController.getString("SharedLinksTab2", R.string.SharedLinksTab2));
                    }
                }
                if (hasMedia[4] != 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(4)) {
                        scrollSlidingTextTabStrip.addTextTab(4, LocaleController.getString("SharedMusicTab2", R.string.SharedMusicTab2));
                    }
                }
            } else {
                TLRPC.EncryptedChat currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
                if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46) {
                    if (hasMedia[4] != 0) {
                        if (!scrollSlidingTextTabStrip.hasTab(4)) {
                            scrollSlidingTextTabStrip.addTextTab(4, LocaleController.getString("SharedMusicTab2", R.string.SharedMusicTab2));
                        }
                    }
                }
            }
            if (hasMedia[2] != 0) {
                if (!scrollSlidingTextTabStrip.hasTab(2)) {
                    scrollSlidingTextTabStrip.addTextTab(2, LocaleController.getString("SharedVoiceTab2", R.string.SharedVoiceTab2));
                }
            }
        }
        if (scrollSlidingTextTabStrip.getTabsCount() <= 1) {
            scrollSlidingTextTabStrip.setVisibility(View.GONE);
            actionBar.setExtraHeight(0);
        } else {
            scrollSlidingTextTabStrip.setVisibility(View.VISIBLE);
            actionBar.setExtraHeight(AndroidUtilities.dp(44));
        }
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            mediaPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < mediaPages.length; a++) {
            mediaPages[a].listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = mediaPages[a].listView.getAdapter();
        if (searching && searchWas) {
            if (animated) {
                if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2) {
                    searching = false;
                    searchWas = false;
                    switchToCurrentSelectedMode(true);
                    return;
                } else {
                    String text = searchItem.getSearchField().getText().toString();
                    if (mediaPages[a].selectedType == 1) {
                        if (documentsSearchAdapter != null) {
                            documentsSearchAdapter.search(text);
                            if (currentAdapter != documentsSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 3) {
                        if (linksSearchAdapter != null) {
                            linksSearchAdapter.search(text);
                            if (currentAdapter != linksSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(linksSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 4) {
                        if (audioSearchAdapter != null) {
                            audioSearchAdapter.search(text);
                            if (currentAdapter != audioSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(audioSearchAdapter);
                            }
                        }
                    }
                }
            } else {
                if (mediaPages[a].listView != null) {
                    if (mediaPages[a].selectedType == 1) {
                        if (currentAdapter != documentsSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                        }
                        documentsSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == 3) {
                        if (currentAdapter != linksSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(linksSearchAdapter);
                        }
                        linksSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == 4) {
                        if (currentAdapter != audioSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(audioSearchAdapter);
                        }
                        audioSearchAdapter.notifyDataSetChanged();
                    }
                }
            }

        } else {
            mediaPages[a].listView.setPinnedHeaderShadowDrawable(null);

            if (mediaPages[a].selectedType == 0) {
                if (currentAdapter != photoVideoAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(photoVideoAdapter);
                }
                mediaPages[a].listView.setPinnedHeaderShadowDrawable(pinnedHeaderShadowDrawable);
            } else if (mediaPages[a].selectedType == 1) {
                if (currentAdapter != documentsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(documentsAdapter);
                }
            } else if (mediaPages[a].selectedType == 2) {
                if (currentAdapter != voiceAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(voiceAdapter);
                }
            } else if (mediaPages[a].selectedType == 3) {
                if (currentAdapter != linksAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(linksAdapter);
                }
            } else if (mediaPages[a].selectedType == 4) {
                if (currentAdapter != audioAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(audioAdapter);
                }
            }
            if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2) {
                if (animated) {
                    searchItemState = 2;
                } else {
                    searchItemState = 0;
                    searchItem.setVisibility(View.INVISIBLE);
                }
            } else {
                if (animated) {
                    if (searchItem.getVisibility() == View.INVISIBLE && !actionBar.isSearchFieldVisible()) {
                        searchItemState = 1;
                        searchItem.setVisibility(View.VISIBLE);
                        searchItem.setAlpha(0.0f);
                    } else {
                        searchItemState = 0;
                    }
                } else if (searchItem.getVisibility() == View.INVISIBLE) {
                    searchItemState = 0;
                    searchItem.setAlpha(1.0f);
                    searchItem.setVisibility(View.VISIBLE);
                }
            }
            if (!sharedMediaData[mediaPages[a].selectedType].loading && !sharedMediaData[mediaPages[a].selectedType].endReached[0] && sharedMediaData[mediaPages[a].selectedType].messages.isEmpty()) {
                sharedMediaData[mediaPages[a].selectedType].loading = true;
                MediaDataController.getInstance(currentAccount).loadMedia(dialog_id, 50, 0, mediaPages[a].selectedType, 1, classGuid);
            }
            mediaPages[a].listView.setVisibility(View.VISIBLE);
        }
        if (searchItemState == 2 && actionBar.isSearchFieldVisible()) {
            ignoreSearchCollapse = true;
            actionBar.closeSearchField();
        }

        if (actionBar.getTranslationY() != 0) {
            mediaPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        }
    }

    private boolean onItemLongClick(MessageObject item, View view, int a) {
        if (actionBar.isActionModeShowed() || getParentActivity() == null) {
            return false;
        }
        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        selectedFiles[item.getDialogId() == dialog_id ? 0 : 1].put(item.getId(), item);
        if (!item.canDeleteMessage(false, null)) {
            cantDeleteMessagesCount++;
        }
        actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
        if (gotoItem != null) {
            gotoItem.setVisibility(View.VISIBLE);
        }
        selectedMessagesCountTextView.setNumber(1, false);
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int i = 0; i < actionModeViews.size(); i++) {
            View view2 = actionModeViews.get(i);
            AndroidUtilities.clearDrawableAnimation(view2);
            animators.add(ObjectAnimator.ofFloat(view2, View.SCALE_Y, 0.1f, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.setDuration(250);
        animatorSet.start();
        scrolling = false;
        if (view instanceof SharedDocumentCell) {
            ((SharedDocumentCell) view).setChecked(true, true);
        } else if (view instanceof SharedPhotoVideoCell) {
            ((SharedPhotoVideoCell) view).setChecked(a, true, true);
        } else if (view instanceof SharedLinkCell) {
            ((SharedLinkCell) view).setChecked(true, true);
        } else if (view instanceof SharedAudioCell) {
            ((SharedAudioCell) view).setChecked(true, true);
        }
        if (!actionBar.isActionModeShowed()) {
            actionBar.showActionMode(true, null, actionModeBackground, null, null, null, 0);
            resetScroll();
        }
        return true;
    }

    private void onItemClick(int index, View view, MessageObject message, int a, int selectedMode) {
        if (message == null) {
            return;
        }
        if (actionBar.isActionModeShowed()) {
            int loadIndex = message.getDialogId() == dialog_id ? 0 : 1;
            if (selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0) {
                selectedFiles[loadIndex].remove(message.getId());
                if (!message.canDeleteMessage(false, null)) {
                    cantDeleteMessagesCount--;
                }
            } else {
                if (selectedFiles[0].size() + selectedFiles[1].size() >= 100) {
                    return;
                }
                selectedFiles[loadIndex].put(message.getId(), message);
                if (!message.canDeleteMessage(false, null)) {
                    cantDeleteMessagesCount++;
                }
            }
            if (selectedFiles[0].size() == 0 && selectedFiles[1].size() == 0) {
                actionBar.hideActionMode();
            } else {
                selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true);
                actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
                if (gotoItem != null) {
                    gotoItem.setVisibility(selectedFiles[0].size() == 1 ? View.VISIBLE : View.GONE);
                }
            }
            scrolling = false;
            if (view instanceof SharedDocumentCell) {
                ((SharedDocumentCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            } else if (view instanceof SharedPhotoVideoCell) {
                ((SharedPhotoVideoCell) view).setChecked(a, selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            } else if (view instanceof SharedLinkCell) {
                ((SharedLinkCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            } else if (view instanceof SharedAudioCell) {
                ((SharedAudioCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            }
        } else {
            if (selectedMode == 0) {
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, provider);
            } else if (selectedMode == 2 || selectedMode == 4) {
                if (view instanceof SharedAudioCell) {
                    ((SharedAudioCell) view).didPressedButton();
                }
            } else if (selectedMode == 1) {
                if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    TLRPC.Document document = message.getDocument();
                    if (cell.isLoaded()) {
                        if (message.canPreviewDocument()) {
                            PhotoViewer.getInstance().setParentActivity(getParentActivity());
                            index = sharedMediaData[selectedMode].messages.indexOf(message);
                            if (index < 0) {
                                ArrayList<MessageObject> documents = new ArrayList<>();
                                documents.add(message);
                                PhotoViewer.getInstance().openPhoto(documents, 0, 0, 0, provider);
                            } else {
                                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, provider);
                            }
                            return;
                        }
                        AndroidUtilities.openDocument(message, getParentActivity(), this);
                    } else if (!cell.isLoading()) {
                        MessageObject messageObject = cell.getMessage();
                        FileLoader.getInstance(currentAccount).loadFile(document, messageObject, 0, 0);
                        cell.updateFileExistIcon(true);
                    } else {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(document);
                        cell.updateFileExistIcon(true);
                    }
                }
            } else if (selectedMode == 3) {
                try {
                    TLRPC.WebPage webPage = message.messageOwner.media != null ? message.messageOwner.media.webpage : null;
                    String link = null;
                    if (webPage != null && !(webPage instanceof TLRPC.TL_webPageEmpty)) {
                        if (webPage.cached_page != null) {
                            ArticleViewer.getInstance().setParentActivity(getParentActivity(), MediaActivity.this);
                            ArticleViewer.getInstance().open(message);
                            return;
                        } else if (webPage.embed_url != null && webPage.embed_url.length() != 0) {
                            openWebView(webPage);
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
    }

    private void openUrl(String link) {
        if (AndroidUtilities.shouldShowUrlInAlert(link)) {
            AlertsCreator.showOpenUrlAlert(this, link, true, true);
        } else {
            Browser.openUrl(getParentActivity(), link);
        }
    }

    private void openWebView(TLRPC.WebPage webPage) {
        EmbedBottomSheet.show(getParentActivity(), webPage.site_name, webPage.description, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height, false);
    }

    private void recycleAdapter(RecyclerView.Adapter adapter) {
        if (adapter instanceof SharedPhotoVideoAdapter) {
            cellCache.addAll(cache);
            cache.clear();
        } else if (adapter == audioAdapter) {
            audioCellCache.addAll(audioCache);
            audioCache.clear();
        }
    }

    private void fixLayoutInternal(int num) {
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        if (num == 0) {
            if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                selectedMessagesCountTextView.setTextSize(18);
            } else {
                selectedMessagesCountTextView.setTextSize(20);
            }
        }

        if (AndroidUtilities.isTablet()) {
            columnsCount = 3;
        } else {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                columnsCount = 6;
            } else {
                columnsCount = 3;
            }
        }
        if (num == 0) {
            photoVideoAdapter.notifyDataSetChanged();
            fixScrollOffset();
        }
    }

    private void fixScrollOffset() {
        if (actionBar.getTranslationY() != 0f) {
            final RecyclerListView listView = mediaPages[0].listView;
            final View child = listView.getChildAt(0);
            if (child != null) {
                final int offset = (int) (child.getY() - (actionBar.getMeasuredHeight() + actionBar.getTranslationY() + additionalPadding));
                if (offset > 0) {
                    scrollWithoutActionBar(listView, offset);
                }
            }
        }
    }

    private void scrollWithoutActionBar(RecyclerView listView, int dy) {
        disableActionBarScrolling = true;
        listView.scrollBy(0, dy);
        disableActionBarScrolling = false;
    }

    SharedLinkCell.SharedLinkCellDelegate sharedLinkCellDelegate = new SharedLinkCell.SharedLinkCellDelegate() {
        @Override
        public void needOpenWebView(TLRPC.WebPage webPage) {
            MediaActivity.this.openWebView(webPage);
        }

        @Override
        public boolean canPerformActions() {
            return !actionBar.isActionModeShowed();
        }

        @Override
        public void onLinkPress(String urlFinal, boolean longPress) {
            if (longPress) {
                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
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
                showDialog(builder.create());
            } else {
                openUrl(urlFinal);
            }
        }
    };

    private class SharedLinksAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;

        public SharedLinksAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isEnabled(int section, int row) {
            return row != 0;
        }

        @Override
        public int getSectionCount() {
            if (sharedMediaData[3].sections.size() == 0 && !sharedMediaData[3].loading) {
                return 1;
            }
            return sharedMediaData[3].sections.size() + (sharedMediaData[3].sections.isEmpty() || sharedMediaData[3].endReached[0] && sharedMediaData[3].endReached[1] ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (sharedMediaData[3].sections.size() == 0 && !sharedMediaData[3].loading) {
                return 1;
            }
            if (section < sharedMediaData[3].sections.size()) {
                return sharedMediaData[3].sectionArrays.get(sharedMediaData[3].sections.get(section)).size() + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new GraySectionCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_graySection) & 0xf2ffffff);
            }
            if (sharedMediaData[3].sections.size() == 0) {
                view.setAlpha(0);
            } else if (section < sharedMediaData[3].sections.size()) {
                view.setAlpha(1f);
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
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
                    view = new SharedLinkCell(mContext);
                    ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
                    break;
                case 3:
                    View emptyStubView = SharedMediaLayout.createEmptyStubView(mContext, 3, dialog_id);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 2:
                default:
                    view = new LoadingCell(mContext, AndroidUtilities.dp(32), AndroidUtilities.dp(54));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2 && holder.getItemViewType() != 3) {
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((GraySectionCell) holder.itemView).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));
                        break;
                    }
                    case 1: {
                        SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position - 1);
                        sharedLinkCell.setLink(messageObject, position != messageObjects.size() || section == sharedMediaData[3].sections.size() - 1 && sharedMediaData[3].loading);
                        if (actionBar.isActionModeShowed()) {
                            sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedLinkCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (sharedMediaData[3].sections.size() == 0 && !sharedMediaData[3].loading) {
                return 3;
            }
            if (section < sharedMediaData[3].sections.size()) {
                if (position == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return 0;
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
        public boolean isEnabled(int section, int row) {
            return row != 0;
        }

        @Override
        public int getSectionCount() {
            if (sharedMediaData[currentType].sections.size() == 0 && !sharedMediaData[currentType].loading) {
                return 1;
            }
            return sharedMediaData[currentType].sections.size() + (sharedMediaData[currentType].sections.isEmpty() || sharedMediaData[currentType].endReached[0] && sharedMediaData[currentType].endReached[1] ? 0 : 1);
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public int getCountForSection(int section) {
            if (sharedMediaData[currentType].sections.size() == 0 && !sharedMediaData[currentType].loading) {
                return 1;
            }
            if (section < sharedMediaData[currentType].sections.size()) {
                return sharedMediaData[currentType].sectionArrays.get(sharedMediaData[currentType].sections.get(section)).size() + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new GraySectionCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_graySection) & 0xf2ffffff);
            }
            if (sharedMediaData[currentType].sections.size() == 0) {
                view.setAlpha(0);
            } else if (section < sharedMediaData[currentType].sections.size()) {
                view.setAlpha(1f);
                String name = sharedMediaData[currentType].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[currentType].sectionArrays.get(name);
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
                    view = new SharedDocumentCell(mContext);
                    break;
                case 2:
                    view = new LoadingCell(mContext, AndroidUtilities.dp(32), AndroidUtilities.dp(54));
                    break;
                case 4:
                    View emptyStubView = SharedMediaLayout.createEmptyStubView(mContext, currentType, dialog_id);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 3:
                default:
                    if (currentType == MediaDataController.MEDIA_MUSIC && !audioCellCache.isEmpty()) {
                        view = audioCellCache.get(0);
                        audioCellCache.remove(0);
                        ViewGroup p = (ViewGroup) view.getParent();
                        if (p != null) {
                            p.removeView(view);
                        }
                    } else {
                        view = new SharedAudioCell(mContext) {
                            @Override
                            public boolean needPlayMessage(MessageObject messageObject) {
                                if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                                    boolean result = MediaController.getInstance().playMessage(messageObject);
                                    MediaController.getInstance().setVoiceMessagesPlaylist(result ? sharedMediaData[currentType].messages : null, false);
                                    return result;
                                } else if (messageObject.isMusic()) {
                                    return MediaController.getInstance().setPlaylist(sharedMediaData[currentType].messages, messageObject, mergeDialogId);
                                }
                                return false;
                            }
                        };
                    }
                    if (currentType == MediaDataController.MEDIA_MUSIC) {
                        audioCache.add((SharedAudioCell) view);
                    }
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2 && holder.getItemViewType() != 4) {
                String name = sharedMediaData[currentType].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[currentType].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((GraySectionCell) holder.itemView).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));
                        break;
                    }
                    case 1: {
                        SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position - 1);
                        sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() || section == sharedMediaData[currentType].sections.size() - 1 && sharedMediaData[currentType].loading);
                        if (actionBar.isActionModeShowed()) {
                            sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedDocumentCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                    case 3: {
                        SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position - 1);
                        sharedAudioCell.setMessageObject(messageObject, position != messageObjects.size() || section == sharedMediaData[currentType].sections.size() - 1 && sharedMediaData[currentType].loading);
                        if (actionBar.isActionModeShowed()) {
                            sharedAudioCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedAudioCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (sharedMediaData[currentType].sections.size() == 0 && !sharedMediaData[currentType].loading) {
                return 4;
            }
            if (section < sharedMediaData[currentType].sections.size()) {
                if (position == 0) {
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
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }

    private class SharedPhotoVideoAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;

        public SharedPhotoVideoAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isEnabled(int section, int row) {
            return false;
        }

        @Override
        public int getSectionCount() {
            if (sharedMediaData[0].sections.size() == 0 && !sharedMediaData[0].loading) {
                return 1;
            }
            return sharedMediaData[0].sections.size() + (sharedMediaData[0].sections.isEmpty() || sharedMediaData[0].endReached[0] && sharedMediaData[0].endReached[1] ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (sharedMediaData[0].sections.size() == 0 && !sharedMediaData[0].loading) {
                return 1;
            }
            if (section < sharedMediaData[0].sections.size()) {
                return (int) Math.ceil(sharedMediaData[0].sectionArrays.get(sharedMediaData[0].sections.get(section)).size() / (float) columnsCount) + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new SharedMediaSectionCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite) & 0xe5ffffff);
            }
            if (sharedMediaData[0].sections.size() == 0) {
                view.setAlpha(0);
            } else if (section < sharedMediaData[0].sections.size()) {
                view.setAlpha(1f);
                String name = sharedMediaData[0].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((SharedMediaSectionCell) view).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));
            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new SharedMediaSectionCell(mContext);
                    break;
                case 1:
                    if (!cellCache.isEmpty()) {
                        view = cellCache.get(0);
                        cellCache.remove(0);
                        ViewGroup p = (ViewGroup) view.getParent();
                        if (p != null) {
                            p.removeView(view);
                        }
                    } else {
                        view = new SharedPhotoVideoCell(mContext);
                    }
                    SharedPhotoVideoCell cell = (SharedPhotoVideoCell) view;
                    cell.setDelegate(new SharedPhotoVideoCell.SharedPhotoVideoCellDelegate() {
                        @Override
                        public void didClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            onItemClick(index, cell, messageObject, a, 0);
                        }

                        @Override
                        public boolean didLongClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            if (actionBar.isActionModeShowed()) {
                                didClickItem(cell, index, messageObject, a);
                                return true;
                            }
                            return onItemLongClick(messageObject, cell, a);
                        }
                    });
                    cache.add((SharedPhotoVideoCell) view);
                    break;
                case 3:
                    View emptyStubView = SharedMediaLayout.createEmptyStubView(mContext, 0, dialog_id);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 2:
                default:
                    view = new LoadingCell(mContext, AndroidUtilities.dp(32), AndroidUtilities.dp(74));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2 && holder.getItemViewType() != 3) {
                String name = sharedMediaData[0].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((SharedMediaSectionCell) holder.itemView).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));
                        break;
                    }
                    case 1: {
                        SharedPhotoVideoCell cell = (SharedPhotoVideoCell) holder.itemView;
                        cell.setItemsCount(columnsCount);
                        cell.setIsFirst(position == 1);
                        for (int a = 0; a < columnsCount; a++) {
                            int index = (position - 1) * columnsCount + a;
                            if (index < messageObjects.size()) {
                                MessageObject messageObject = messageObjects.get(index);
                                cell.setItem(a, sharedMediaData[0].messages.indexOf(messageObject), messageObject);
                                if (actionBar.isActionModeShowed()) {
                                    cell.setChecked(a, selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                                } else {
                                    cell.setChecked(a, false, !scrolling);
                                }
                            } else {
                                cell.setItem(a, index, null);
                            }
                        }
                        cell.requestLayout();
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (sharedMediaData[0].sections.size() == 0 && !sharedMediaData[0].loading) {
                return 3;
            }
            if (section < sharedMediaData[0].sections.size()) {
                if (position == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }

    public class MediaSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<MessageObject> searchResult = new ArrayList<>();
        private Runnable searchRunnable;
        protected ArrayList<MessageObject> globalSearch = new ArrayList<>();
        private int reqId = 0;
        private int lastReqId;
        private int currentType;
        private int searchesInProgress;

        public MediaSearchAdapter(Context context, int type) {
            mContext = context;
            currentType = type;
        }

        public void queryServerSearch(final String query, final int max_id, long did) {
            int uid = (int) did;
            if (uid == 0) {
                return;
            }
            if (reqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = 0;
                searchesInProgress--;
            }
            if (query == null || query.length() == 0) {
                globalSearch.clear();
                lastReqId = 0;
                notifyDataSetChanged();
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.limit = 50;
            req.offset_id = max_id;
            if (currentType == 1) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (currentType == 3) {
                req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            } else if (currentType == 4) {
                req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            }
            req.q = query;
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(uid);
            if (req.peer == null) {
                return;
            }
            final int currentReqId = ++lastReqId;
            searchesInProgress++;
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                final ArrayList<MessageObject> messageObjects = new ArrayList<>();
                if (error == null) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    for (int a = 0; a < res.messages.size(); a++) {
                        TLRPC.Message message = res.messages.get(a);
                        if (max_id != 0 && message.id > max_id) {
                            continue;
                        }
                        messageObjects.add(new MessageObject(currentAccount, message, false, true));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (reqId != 0) {
                        if (currentReqId == lastReqId) {
                            globalSearch = messageObjects;
                            searchesInProgress--;
                            int count = getItemCount();
                            notifyDataSetChanged();

                            for (int a = 0; a < mediaPages.length; a++) {
                                if (mediaPages[a].listView.getAdapter() == this && count == 0 && actionBar.getTranslationY() != 0) {
                                    mediaPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
                                }
                                if (mediaPages[a].selectedType == currentType) {
                                    if (searchesInProgress == 0 && count == 0) {
                                        mediaPages[a].emptyView.showProgress(false, true);
                                    } else if (count == 0) {
                                        animateItemsEnter(mediaPages[a].listView, 0);
                                    }
                                }
                            }
                        }
                        reqId = 0;
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }

            if (!searchResult.isEmpty() || !globalSearch.isEmpty() || searchesInProgress != 0) {
                searchResult.clear();
                globalSearch.clear();
                if (reqId != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                    reqId = 0;
                    searchesInProgress--;
                }
            }
            notifyDataSetChanged();
            if (!TextUtils.isEmpty(query)) {
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == currentType) {
                        mediaPages[a].emptyView.showProgress(true, true);
                    }
                }


                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    if (!sharedMediaData[currentType].messages.isEmpty() && (currentType == 1 || currentType == 4)) {
                        MessageObject messageObject = sharedMediaData[currentType].messages.get(sharedMediaData[currentType].messages.size() - 1);
                        queryServerSearch(query, messageObject.getId(), messageObject.getDialogId());
                    } else if (currentType == 3) {
                        queryServerSearch(query, 0, dialog_id);
                    }
                    if (currentType == 1 || currentType == 4) {
                        final ArrayList<MessageObject> copy = new ArrayList<>(sharedMediaData[currentType].messages);
                        searchesInProgress++;
                        Utilities.searchQueue.postRunnable(() -> {
                            String search1 = query.trim().toLowerCase();
                            if (search1.length() == 0) {
                                updateSearchResults(new ArrayList<>());
                                return;
                            }
                            String search2 = LocaleController.getInstance().getTranslitString(search1);
                            if (search1.equals(search2) || search2.length() == 0) {
                                search2 = null;
                            }
                            String[] search = new String[1 + (search2 != null ? 1 : 0)];
                            search[0] = search1;
                            if (search2 != null) {
                                search[1] = search2;
                            }

                            ArrayList<MessageObject> resultArray = new ArrayList<>();

                            for (int a = 0; a < copy.size(); a++) {
                                MessageObject messageObject = copy.get(a);
                                for (int b = 0; b < search.length; b++) {
                                    String q = search[b];
                                    String name = messageObject.getDocumentName();
                                    if (name == null || name.length() == 0) {
                                        continue;
                                    }
                                    name = name.toLowerCase();
                                    if (name.contains(q)) {
                                        resultArray.add(messageObject);
                                        break;
                                    }
                                    if (currentType == 4) {
                                        TLRPC.Document document;
                                        if (messageObject.type == 0) {
                                            document = messageObject.messageOwner.media.webpage.document;
                                        } else {
                                            document = messageObject.messageOwner.media.document;
                                        }
                                        boolean ok = false;
                                        for (int c = 0; c < document.attributes.size(); c++) {
                                            TLRPC.DocumentAttribute attribute = document.attributes.get(c);
                                            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                                if (attribute.performer != null) {
                                                    ok = attribute.performer.toLowerCase().contains(q);
                                                }
                                                if (!ok && attribute.title != null) {
                                                    ok = attribute.title.toLowerCase().contains(q);
                                                }
                                                break;
                                            }
                                        }
                                        if (ok) {
                                            resultArray.add(messageObject);
                                            break;
                                        }
                                    }
                                }
                            }

                            updateSearchResults(resultArray);
                        });
                    }
                }, 300);
            }
        }

        private void updateSearchResults(final ArrayList<MessageObject> documents) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchesInProgress--;
                searchResult = documents;
                int count = getItemCount();
                notifyDataSetChanged();
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].listView.getAdapter() == this && count == 0 && actionBar.getTranslationY() != 0) {
                        mediaPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
                        break;
                    }
                }
            });
        }


        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != searchResult.size() + globalSearch.size();
        }

        @Override
        public int getItemCount() {
            int count = searchResult.size();
            int globalCount = globalSearch.size();
            if (globalCount != 0) {
                count += globalCount;
            }
            return count;
        }

        public boolean isGlobalSearch(int i) {
            int localCount = searchResult.size();
            int globalCount = globalSearch.size();
            if (i >= 0 && i < localCount) {
                return false;
            } else if (i > localCount && i <= globalCount + localCount) {
                return true;
            }
            return false;
        }

        public MessageObject getItem(int i) {
            if (i < searchResult.size()) {
                return searchResult.get(i);
            } else {
                return globalSearch.get(i - searchResult.size());
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (currentType == 1) {
                view = new SharedDocumentCell(mContext);
            } else if (currentType == 4) {
                view = new SharedAudioCell(mContext) {
                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject);
                            MediaController.getInstance().setVoiceMessagesPlaylist(result ? searchResult : null, false);
                            if (messageObject.isRoundVideo()) {
                                MediaController.getInstance().setCurrentVideoVisible(false);
                            }
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(searchResult, messageObject, mergeDialogId);
                        }
                        return false;
                    }
                };
            } else {
                view = new SharedLinkCell(mContext);
                ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (currentType == 1) {
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedDocumentCell.setDocument(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 3) {
                SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedLinkCell.setLink(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedLinkCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 4) {
                SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedAudioCell.setMessageObject(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedAudioCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedAudioCell.setChecked(false, !scrolling);
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    private void animateItemsEnter(RecyclerListView listView, int oldItemCount) {
        if (listView == null) {
            return;
        }
        int n = listView.getChildCount();
        View progressView = null;
        for (int i = 0; i < n; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                progressView = child;
            }
        }
        final View finalProgressView = progressView;
        if (progressView != null) {
            listView.removeView(progressView);
        }
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    if (listView.getChildAdapterPosition(child) >= oldItemCount - 1) {
                        child.setAlpha(0);
                        int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                        int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                        ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                        a.setStartDelay(delay);
                        a.setDuration(200);
                        animatorSet.playTogether(a);
                    }
                    if (finalProgressView != null && finalProgressView.getParent() == null) {
                        listView.addView(finalProgressView);
                        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
                        if (layoutManager != null) {
                            layoutManager.ignoreView(finalProgressView);
                            Animator animator = ObjectAnimator.ofFloat(finalProgressView, View.ALPHA, finalProgressView.getAlpha(), 0);
                            animator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    finalProgressView.setAlpha(1f);
                                    layoutManager.stopIgnoringView(finalProgressView);
                                    listView.removeView(finalProgressView);
                                }
                            });
                            animator.start();
                        }
                    }
                }

                animatorSet.start();
                return true;
            }
        });
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionModeBackground, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_sharedMedia_actionMode));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        arrayList.add(new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));

        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_FASTSCROLL, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerPerformer));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose));

        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip, 0, new Class[]{ScrollSlidingTextTabStrip.class}, new String[]{"selectorDrawable"}, null, null, null, Theme.key_actionBarTabLine));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabActiveText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabUnactiveText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabSelector));

        for (int a = 0; a < mediaPages.length; a++) {
            final int num = a;
            ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
                if (mediaPages[num].listView != null) {
                    int count = mediaPages[num].listView.getChildCount();
                    for (int a1 = 0; a1 < count; a1++) {
                        View child = mediaPages[num].listView.getChildAt(a1);
                        if (child instanceof SharedPhotoVideoCell) {
                            ((SharedPhotoVideoCell) child).updateCheckboxColor();
                        }
                    }
                }
            };

            arrayList.add(new ThemeDescription(mediaPages[a].emptyView, 0, null, null, null, null, Theme.key_windowBackgroundGray));
            arrayList.add(new ThemeDescription(mediaPages[a].progressView, 0, null, null, null, null, Theme.key_windowBackgroundGray));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
            arrayList.add(new ThemeDescription(mediaPages[a].emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"dateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{SharedDocumentCell.class}, new String[]{"progressView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"statusImageView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"extTextView"}, null, null, null, Theme.key_files_iconText));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_titleTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_descriptionTextPaint, null, null, Theme.key_windowBackgroundWhiteGrayText2));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, new String[]{"titleTextPaint"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, Theme.linkSelectionPaint, null, null, Theme.key_windowBackgroundWhiteLinkSelection));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholderText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholder));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedPhotoVideoCell.class}, new String[]{"backgroundPaint"}, null, null, null, Theme.key_sharedMedia_photoPlaceholder));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedPhotoVideoCell.class}, null, null, cellDelegate, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedPhotoVideoCell.class}, null, null, cellDelegate, Theme.key_checkboxCheck));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, null, null, new Drawable[]{pinnedHeaderShadowDrawable}, null, Theme.key_windowBackgroundGrayShadow));

            arrayList.add(new ThemeDescription(mediaPages[a].emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        }

        return arrayList;
    }
}
