package org.telegram.ui.Components;

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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

@SuppressWarnings("unchecked")
public class SharedMediaLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static class MediaPage extends FrameLayout {
        private RecyclerListView listView;
        private FlickerLoadingView progressView;
        private StickerEmptyView emptyView;
        private ExtendedGridLayoutManager layoutManager;
        private ClippingImageView animatingImageView;
        private RecyclerAnimationScrollHelper scrollHelper;
        private int selectedType;

        public MediaPage(Context context) {
            super(context);
        }
    }

    private ActionBar actionBar;

    private SharedPhotoVideoAdapter photoVideoAdapter;
    private SharedLinksAdapter linksAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private SharedDocumentsAdapter voiceAdapter;
    private SharedDocumentsAdapter audioAdapter;
    private GifAdapter gifAdapter;
    private CommonGroupsAdapter commonGroupsAdapter;
    private ChatUsersAdapter chatUsersAdapter;
    private MediaSearchAdapter documentsSearchAdapter;
    private MediaSearchAdapter audioSearchAdapter;
    private MediaSearchAdapter linksSearchAdapter;
    private GroupUsersSearchAdapter groupUsersSearchAdapter;
    private MediaPage[] mediaPages = new MediaPage[2];
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem forwardItem;
    private ActionBarMenuItem gotoItem;
    private int searchItemState;
    private Drawable pinnedHeaderShadowDrawable;
    private boolean ignoreSearchCollapse;
    private NumberTextView selectedMessagesCountTextView;
    private LinearLayout actionModeLayout;
    private ImageView closeButton;
    private BackDrawable backDrawable;
    private ArrayList<SharedPhotoVideoCell> cellCache = new ArrayList<>(10);
    private ArrayList<SharedPhotoVideoCell> cache = new ArrayList<>(10);
    private ArrayList<SharedAudioCell> audioCellCache = new ArrayList<>(10);
    private ArrayList<SharedAudioCell> audioCache = new ArrayList<>(10);
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private View shadowLine;
    private ChatActionCell floatingDateView;
    private AnimatorSet floatingDateAnimation;
    private Runnable hideFloatingDateRunnable = () -> hideFloatingDateView(true);
    private ArrayList<View> actionModeViews = new ArrayList<>();

    private float additionalFloatingTranslation;

    private FragmentContextView fragmentContextView;

    private int maximumVelocity;

    private Paint backgroundPaint = new Paint();

    private boolean searchWas;
    private boolean searching;

    private int[] hasMedia;
    private int initialTab;

    private SparseArray<MessageObject>[] selectedFiles = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private int cantDeleteMessagesCount;
    private boolean scrolling;
    private long mergeDialogId;
    private TLRPC.ChatFull info;

    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;

    private long dialog_id;
    private int columnsCount = 3;

    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    public interface SharedMediaPreloaderDelegate {
        void mediaCountUpdated();
    }

    public static class SharedMediaPreloader implements NotificationCenter.NotificationCenterDelegate {

        private int[] mediaCount = new int[]{-1, -1, -1, -1, -1, -1};
        private int[] mediaMergeCount = new int[]{-1, -1, -1, -1, -1, -1};
        private int[] lastMediaCount = new int[]{-1, -1, -1, -1, -1, -1};
        private int[] lastLoadMediaCount = new int[]{-1, -1, -1, -1, -1, -1};
        private SharedMediaData[] sharedMediaData;
        private long dialogId;
        private long mergeDialogId;
        private BaseFragment parentFragment;
        private ArrayList<SharedMediaPreloaderDelegate> delegates = new ArrayList<>();
        private boolean mediaWasLoaded;

        public SharedMediaPreloader(BaseFragment fragment) {
            parentFragment = fragment;
            if (fragment instanceof ChatActivity) {
                ChatActivity chatActivity = (ChatActivity) fragment;
                dialogId = chatActivity.getDialogId();
                mergeDialogId = chatActivity.getMergeDialogId();
            } else if (fragment instanceof ProfileActivity) {
                ProfileActivity profileActivity = (ProfileActivity) fragment;
                dialogId = profileActivity.getDialogId();
            }

            sharedMediaData = new SharedMediaData[6];
            for (int a = 0; a < sharedMediaData.length; a++) {
                sharedMediaData[a] = new SharedMediaData();
                sharedMediaData[a].setMaxId(0, DialogObject.isEncryptedDialog(dialogId) ? Integer.MIN_VALUE : Integer.MAX_VALUE);
            }
            loadMediaCounts();

            NotificationCenter notificationCenter = parentFragment.getNotificationCenter();
            notificationCenter.addObserver(this, NotificationCenter.mediaCountsDidLoad);
            notificationCenter.addObserver(this, NotificationCenter.mediaCountDidLoad);
            notificationCenter.addObserver(this, NotificationCenter.didReceiveNewMessages);
            notificationCenter.addObserver(this, NotificationCenter.messageReceivedByServer);
            notificationCenter.addObserver(this, NotificationCenter.mediaDidLoad);
            notificationCenter.addObserver(this, NotificationCenter.messagesDeleted);
            notificationCenter.addObserver(this, NotificationCenter.replaceMessagesObjects);
            notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad);
        }

        public void addDelegate(SharedMediaPreloaderDelegate delegate) {
            delegates.add(delegate);
        }

        public void removeDelegate(SharedMediaPreloaderDelegate delegate) {
            delegates.remove(delegate);
        }

        public void onDestroy(BaseFragment fragment) {
            if (fragment != parentFragment) {
                return;
            }
            delegates.clear();
            NotificationCenter notificationCenter = parentFragment.getNotificationCenter();
            notificationCenter.removeObserver(this, NotificationCenter.mediaCountsDidLoad);
            notificationCenter.removeObserver(this, NotificationCenter.mediaCountDidLoad);
            notificationCenter.removeObserver(this, NotificationCenter.didReceiveNewMessages);
            notificationCenter.removeObserver(this, NotificationCenter.messageReceivedByServer);
            notificationCenter.removeObserver(this, NotificationCenter.mediaDidLoad);
            notificationCenter.removeObserver(this, NotificationCenter.messagesDeleted);
            notificationCenter.removeObserver(this, NotificationCenter.replaceMessagesObjects);
            notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad);
        }

        public int[] getLastMediaCount() {
            return lastMediaCount;
        }

        public SharedMediaData[] getSharedMediaData() {
            return sharedMediaData;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.mediaCountsDidLoad) {
                long did = (Long) args[0];
                if (did == dialogId || did == mergeDialogId) {
                    int[] counts = (int[]) args[1];
                    if (did == dialogId) {
                        mediaCount = counts;
                    } else {
                        mediaMergeCount = counts;
                    }
                    for (int a = 0; a < counts.length; a++) {
                        if (mediaCount[a] >= 0 && mediaMergeCount[a] >= 0) {
                            lastMediaCount[a] = mediaCount[a] + mediaMergeCount[a];
                        } else if (mediaCount[a] >= 0) {
                            lastMediaCount[a] = mediaCount[a];
                        } else {
                            lastMediaCount[a] = Math.max(mediaMergeCount[a], 0);
                        }
                        if (did == dialogId && lastMediaCount[a] != 0 && lastLoadMediaCount[a] != mediaCount[a]) {
                            parentFragment.getMediaDataController().loadMedia(did, lastLoadMediaCount[a] == -1 ? 30 : 20, 0, a, 2, parentFragment.getClassGuid());
                            lastLoadMediaCount[a] = mediaCount[a];
                        }
                    }
                    mediaWasLoaded = true;
                    for (int a = 0, N = delegates.size(); a < N; a++) {
                        delegates.get(a).mediaCountUpdated();
                    }
                }
            } else if (id == NotificationCenter.mediaCountDidLoad) {
                long did = (Long) args[0];
                if (did == dialogId || did == mergeDialogId) {
                    int type = (Integer) args[3];
                    int mCount = (Integer) args[1];
                    if (did == dialogId) {
                        mediaCount[type] = mCount;
                    } else {
                        mediaMergeCount[type] = mCount;
                    }
                    if (mediaCount[type] >= 0 && mediaMergeCount[type] >= 0) {
                        lastMediaCount[type] = mediaCount[type] + mediaMergeCount[type];
                    } else if (mediaCount[type] >= 0) {
                        lastMediaCount[type] = mediaCount[type];
                    } else {
                        lastMediaCount[type] = Math.max(mediaMergeCount[type], 0);
                    }
                    for (int a = 0, N = delegates.size(); a < N; a++) {
                        delegates.get(a).mediaCountUpdated();
                    }
                }
            } else if (id == NotificationCenter.didReceiveNewMessages) {
                boolean scheduled = (Boolean) args[2];
                if (scheduled) {
                    return;
                }
                if (dialogId == (Long) args[0]) {
                    boolean enc = DialogObject.isEncryptedDialog(dialogId);
                    ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject obj = arr.get(a);
                        if (obj.messageOwner.media == null || obj.needDrawBluredPreview()) {
                            continue;
                        }
                        int type = MediaDataController.getMediaType(obj.messageOwner);
                        if (type == -1) {
                            continue;
                        }
                        sharedMediaData[type].addMessage(obj, 0, true, enc);
                    }
                    loadMediaCounts();
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
            } else if (id == NotificationCenter.mediaDidLoad) {
                long did = (Long) args[0];
                int guid = (Integer) args[3];
                if (guid == parentFragment.getClassGuid()) {
                    int type = (Integer) args[4];
                    sharedMediaData[type].setTotalCount((Integer) args[1]);
                    ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                    boolean enc = DialogObject.isEncryptedDialog(did);
                    int loadIndex = did == dialogId ? 0 : 1;
                    if (!arr.isEmpty()) {
                        sharedMediaData[type].setEndReached(loadIndex, (Boolean) args[5]);
                    }
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject message = arr.get(a);
                        sharedMediaData[type].addMessage(message, loadIndex, false, enc);
                    }
                }
            } else if (id == NotificationCenter.messagesDeleted) {
                boolean scheduled = (Boolean) args[2];
                if (scheduled) {
                    return;
                }
                long channelId = (Long) args[1];
                TLRPC.Chat currentChat;
                if (DialogObject.isChatDialog(dialogId)) {
                    currentChat = parentFragment.getMessagesController().getChat(-dialogId);
                } else {
                    currentChat = null;
                }
                if (ChatObject.isChannel(currentChat)) {
                    if (!(channelId == 0 && mergeDialogId != 0 || channelId == currentChat.id)) {
                        return;
                    }
                } else if (channelId != 0) {
                    return;
                }

                boolean changed = false;
                ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
                for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                    for (int b = 0; b < sharedMediaData.length; b++) {
                        MessageObject messageObject = sharedMediaData[b].deleteMessage(markAsDeletedMessages.get(a), 0);
                        if (messageObject != null) {
                            if (messageObject.getDialogId() == dialogId) {
                                if (mediaCount[b] > 0) {
                                    mediaCount[b]--;
                                }
                            } else {
                                if (mediaMergeCount[b] > 0) {
                                    mediaMergeCount[b]--;
                                }
                            }
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    for (int a = 0; a < mediaCount.length; a++) {
                        if (mediaCount[a] >= 0 && mediaMergeCount[a] >= 0) {
                            lastMediaCount[a] = mediaCount[a] + mediaMergeCount[a];
                        } else if (mediaCount[a] >= 0) {
                            lastMediaCount[a] = mediaCount[a];
                        } else {
                            lastMediaCount[a] = Math.max(mediaMergeCount[a], 0);
                        }
                    }
                    for (int a = 0, N = delegates.size(); a < N; a++) {
                        delegates.get(a).mediaCountUpdated();
                    }
                }
                loadMediaCounts();
            } else if (id == NotificationCenter.replaceMessagesObjects) {
                long did = (long) args[0];
                if (did != dialogId && did != mergeDialogId) {
                    return;
                }
                int loadIndex = did == dialogId ? 0 : 1;
                ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
                for (int b = 0, N = messageObjects.size(); b < N; b++) {
                    MessageObject messageObject = messageObjects.get(b);
                    int mid = messageObject.getId();
                    int type = MediaDataController.getMediaType(messageObject.messageOwner);
                    for (int a = 0; a < sharedMediaData.length; a++) {
                        MessageObject old = sharedMediaData[a].messagesDict[loadIndex].get(mid);
                        if (old != null) {
                            int oldType = MediaDataController.getMediaType(messageObject.messageOwner);
                            if (type == -1 || oldType != type) {
                                sharedMediaData[a].deleteMessage(mid, loadIndex);
                                if (loadIndex == 0) {
                                    if (mediaCount[a] > 0) {
                                        mediaCount[a]--;
                                    }
                                } else {
                                    if (mediaMergeCount[a] > 0) {
                                        mediaMergeCount[a]--;
                                    }
                                }
                            } else {
                                int idx = sharedMediaData[a].messages.indexOf(old);
                                if (idx >= 0) {
                                    sharedMediaData[a].messagesDict[loadIndex].put(mid, messageObject);
                                    sharedMediaData[a].messages.set(idx, messageObject);
                                }
                            }
                            break;
                        }
                    }
                }
            } else if (id == NotificationCenter.chatInfoDidLoad) {
                TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
                if (dialogId < 0 && chatFull.id == -dialogId) {
                    setChatInfo(chatFull);
                }
            }
        }

        private void loadMediaCounts() {
            parentFragment.getMediaDataController().getMediaCounts(dialogId, parentFragment.getClassGuid());
            if (mergeDialogId != 0) {
                parentFragment.getMediaDataController().getMediaCounts(mergeDialogId, parentFragment.getClassGuid());
            }
        }

        private void setChatInfo(TLRPC.ChatFull chatInfo) {
            if (chatInfo != null && chatInfo.migrated_from_chat_id != 0 && mergeDialogId == 0) {
                mergeDialogId = -chatInfo.migrated_from_chat_id;
                parentFragment.getMediaDataController().getMediaCounts(mergeDialogId, parentFragment.getClassGuid());
            }
        }

        public boolean isMediaWasLoaded() {
            return mediaWasLoaded;
        }
    }

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (messageObject == null || mediaPages[0].selectedType != 0 && mediaPages[0].selectedType != 1 && mediaPages[0].selectedType != 3 && mediaPages[0].selectedType != 5) {
                return null;
            }
            final RecyclerListView listView = mediaPages[0].listView;
            int firstVisiblePosition = -1;
            int lastVisiblePosition = -1;
            for (int a = 0, count = listView.getChildCount(); a < count; a++) {
                View view = listView.getChildAt(a);
                int visibleHeight = mediaPages[0].listView.getMeasuredHeight();
                View parent = (View) getParent();
                if (parent != null) {
                    if (getY() + getMeasuredHeight() > parent.getMeasuredHeight()) {
                        visibleHeight -= getBottom() - parent.getMeasuredHeight();
                    }
                }

                if (view.getTop() >= visibleHeight) {
                    continue;
                }
                int adapterPosition = listView.getChildAdapterPosition(view);
                if (adapterPosition < firstVisiblePosition || firstVisiblePosition == -1) {
                    firstVisiblePosition = adapterPosition;
                }
                if (adapterPosition > lastVisiblePosition || lastVisiblePosition == -1) {
                    lastVisiblePosition = adapterPosition;
                }
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
                } else if (view instanceof SharedLinkCell) {
                    SharedLinkCell cell = (SharedLinkCell) view;
                    MessageObject message = cell.getMessage();
                    if (message != null && message.getId() == messageObject.getId()) {
                        imageReceiver = cell.getLinkImageView();
                        cell.getLocationInWindow(coords);
                    }
                }
                if (imageReceiver != null) {
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = listView;
                    object.animatingImageView = mediaPages[0].animatingImageView;
                    mediaPages[0].listView.getLocationInWindow(coords);
                    object.animatingImageViewYOffset = -coords[1];
                    object.imageReceiver = imageReceiver;
                    object.allowTakeAnimation = false;
                    object.radius = object.imageReceiver.getRoundRadius();
                    object.thumb = object.imageReceiver.getBitmapSafe();
                    object.parentView.getLocationInWindow(coords);
                    object.clipTopAddition = 0;
                    if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                        object.clipTopAddition += AndroidUtilities.dp(36);
                    }

                    if (PhotoViewer.isShowingImage(messageObject)) {
                        final View pinnedHeader = listView.getPinnedHeader();
                        if (pinnedHeader != null) {
                            int top = 0;
                            if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                                top += fragmentContextView.getHeight() - AndroidUtilities.dp(2.5f);
                            }
                            if (view instanceof SharedDocumentCell) {
                                top += AndroidUtilities.dp(8f);
                            }
                            final int topOffset = top - object.viewY;
                            if (topOffset > view.getHeight()) {
                                listView.scrollBy(0, -(topOffset + pinnedHeader.getHeight()));
                            } else {
                                int bottomOffset = object.viewY - listView.getHeight();
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
            if (mediaPages[0].selectedType == 0 && firstVisiblePosition >= 0 && lastVisiblePosition >= 0) {
                int position = photoVideoAdapter.getPositionForIndex(index);

                if (position <= firstVisiblePosition) {
                    mediaPages[0].layoutManager.scrollToPositionWithOffset(position, 0);
                    profileActivity.scrollToSharedMedia();
                } else if (position >= lastVisiblePosition && lastVisiblePosition >= 0) {
                    mediaPages[0].layoutManager.scrollToPositionWithOffset(position, 0, true);
                    profileActivity.scrollToSharedMedia();
                }
            }

            return null;
        }
    };

    public static class SharedMediaData {
        public ArrayList<MessageObject> messages = new ArrayList<>();
        public SparseArray<MessageObject>[] messagesDict = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
        public ArrayList<String> sections = new ArrayList<>();
        public HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();
        public int totalCount;
        public boolean loading;
        public boolean[] endReached = new boolean[]{false, true};
        public int[] max_id = new int[]{0, 0};

        public void setTotalCount(int count) {
            totalCount = count;
        }

        public void setMaxId(int num, int value) {
            max_id[num] = value;
        }

        public void setEndReached(int num, boolean value) {
            endReached[num] = value;
        }

        public boolean addMessage(MessageObject messageObject, int loadIndex, boolean isNew, boolean enc) {
            if (messagesDict[loadIndex].indexOfKey(messageObject.getId()) >= 0) {
                return false;
            }
            ArrayList<MessageObject> messageObjects = sectionArrays.get(messageObject.monthKey);
            if (messageObjects == null) {
                messageObjects = new ArrayList<>();
                sectionArrays.put(messageObject.monthKey, messageObjects);
                if (isNew) {
                    sections.add(0, messageObject.monthKey);
                } else {
                    sections.add(messageObject.monthKey);
                }
            }
            if (isNew) {
                messageObjects.add(0, messageObject);
                messages.add(0, messageObject);
            } else {
                messageObjects.add(messageObject);
                messages.add(messageObject);
            }
            messagesDict[loadIndex].put(messageObject.getId(), messageObject);
            if (!enc) {
                if (messageObject.getId() > 0) {
                    max_id[loadIndex] = Math.min(messageObject.getId(), max_id[loadIndex]);
                }
            } else {
                max_id[loadIndex] = Math.max(messageObject.getId(), max_id[loadIndex]);
            }
            return true;
        }

        public MessageObject deleteMessage(int mid, int loadIndex) {
            MessageObject messageObject = messagesDict[loadIndex].get(mid);
            if (messageObject == null) {
                return null;
            }
            ArrayList<MessageObject> messageObjects = sectionArrays.get(messageObject.monthKey);
            if (messageObjects == null) {
                return null;
            }
            messageObjects.remove(messageObject);
            messages.remove(messageObject);
            messagesDict[loadIndex].remove(messageObject.getId());
            if (messageObjects.isEmpty()) {
                sectionArrays.remove(messageObject.monthKey);
                sections.remove(messageObject.monthKey);
            }
            totalCount--;
            return messageObject;
        }

        public void replaceMid(int oldMid, int newMid) {
            MessageObject obj = messagesDict[0].get(oldMid);
            if (obj != null) {
                messagesDict[0].remove(oldMid);
                messagesDict[0].put(newMid, obj);
                obj.messageOwner.id = newMid;
                max_id[0] = Math.min(newMid, max_id[0]);
            }
        }
    }

    private SharedMediaData[] sharedMediaData = new SharedMediaData[6];
    private SharedMediaPreloader sharedMediaPreloader;

    private final static int forward = 100;
    private final static int delete = 101;
    private final static int gotochat = 102;

    private ProfileActivity profileActivity;

    private int startedTrackingPointerId;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;

    private boolean isActionModeShowed;

    public SharedMediaLayout(Context context, long did, SharedMediaPreloader preloader, int commonGroupsCount, ArrayList<Integer> sortedUsers, TLRPC.ChatFull chatInfo, boolean membersFirst, ProfileActivity parent) {
        super(context);

        sharedMediaPreloader = preloader;
        int[] mediaCount = preloader.getLastMediaCount();
        hasMedia = new int[]{mediaCount[0], mediaCount[1], mediaCount[2], mediaCount[3], mediaCount[4], mediaCount[5], commonGroupsCount};
        if (membersFirst) {
            initialTab = 7;
        } else {
            for (int a = 0; a < hasMedia.length; a++) {
                if (hasMedia[a] == -1 || hasMedia[a] > 0) {
                    initialTab = a;
                    break;
                }
            }
        }
        info = chatInfo;
        if (info != null) {
            mergeDialogId = -info.migrated_from_chat_id;
        }
        dialog_id = did;
        for (int a = 0; a < sharedMediaData.length; a++) {
            sharedMediaData[a] = new SharedMediaData();
            sharedMediaData[a].max_id[0] = DialogObject.isEncryptedDialog(dialog_id) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            fillMediaData(a);
            if (mergeDialogId != 0 && info != null) {
                sharedMediaData[a].max_id[1] = info.migrated_from_max_id;
                sharedMediaData[a].endReached[1] = false;
            }
        }

        profileActivity = parent;
        actionBar = profileActivity.getActionBar();

        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.mediaDidLoad);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);

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

        pinnedHeaderShadowDrawable = context.getResources().getDrawable(R.drawable.photos_header_shadow);
        pinnedHeaderShadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundGrayShadow), PorterDuff.Mode.MULTIPLY));

        if (scrollSlidingTextTabStrip != null) {
            initialTab = scrollSlidingTextTabStrip.getCurrentTabId();
        }
        scrollSlidingTextTabStrip = createScrollingTextTabStrip(context);

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
                onSearchStateChanged(true);
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                documentsSearchAdapter.search(null, true);
                linksSearchAdapter.search(null, true);
                audioSearchAdapter.search(null, true);
                groupUsersSearchAdapter.search(null, true);
                onSearchStateChanged(false);
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
                } else {
                    searchWas = false;
                }
                switchToCurrentSelectedMode(false);
                if (mediaPages[0].selectedType == 1) {
                    if (documentsSearchAdapter == null) {
                        return;
                    }
                    documentsSearchAdapter.search(text, true);
                } else if (mediaPages[0].selectedType == 3) {
                    if (linksSearchAdapter == null) {
                        return;
                    }
                    linksSearchAdapter.search(text, true);
                } else if (mediaPages[0].selectedType == 4) {
                    if (audioSearchAdapter == null) {
                        return;
                    }
                    audioSearchAdapter.search(text, true);
                } else if (mediaPages[0].selectedType == 7) {
                    if (groupUsersSearchAdapter == null) {
                        return;
                    }
                    groupUsersSearchAdapter.search(text, true);
                }
            }

            @Override
            public void onLayout(int l, int t, int r, int b) {
                View parent = (View) searchItem.getParent();
                searchItem.setTranslationX(parent.getMeasuredWidth() - r);
            }
        });
        searchItem.setTranslationY(AndroidUtilities.dp(10));
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        searchItem.setVisibility(View.INVISIBLE);
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_player_time));
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchItemState = 0;

        actionModeLayout = new LinearLayout(context);
        actionModeLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionModeLayout.setAlpha(0.0f);
        actionModeLayout.setClickable(true);
        actionModeLayout.setVisibility(INVISIBLE);

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(backDrawable = new BackDrawable(true));
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        closeButton.setContentDescription(LocaleController.getString("Close", R.string.Close));
        actionModeLayout.addView(closeButton, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(closeButton);
        closeButton.setOnClickListener(v -> closeActionMode());

        selectedMessagesCountTextView = new NumberTextView(context);
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        actionModeLayout.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 18, 0, 0, 0));
        actionModeViews.add(selectedMessagesCountTextView);

        if (!DialogObject.isEncryptedDialog(dialog_id)) {
            gotoItem = new ActionBarMenuItem(context, null, Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
            gotoItem.setIcon(R.drawable.msg_message);
            gotoItem.setContentDescription(LocaleController.getString("AccDescrGoToMessage", R.string.AccDescrGoToMessage));
            gotoItem.setDuplicateParentStateEnabled(false);
            actionModeLayout.addView(gotoItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
            actionModeViews.add(gotoItem);
            gotoItem.setOnClickListener(v -> onActionBarItemClick(gotochat));

            forwardItem = new ActionBarMenuItem(context, null, Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
            forwardItem.setIcon(R.drawable.msg_forward);
            forwardItem.setContentDescription(LocaleController.getString("Forward", R.string.Forward));
            forwardItem.setDuplicateParentStateEnabled(false);
            actionModeLayout.addView(forwardItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
            actionModeViews.add(forwardItem);
            forwardItem.setOnClickListener(v -> onActionBarItemClick(forward));
        }
        deleteItem = new ActionBarMenuItem(context, null, Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        deleteItem.setIcon(R.drawable.msg_delete);
        deleteItem.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
        deleteItem.setDuplicateParentStateEnabled(false);
        actionModeLayout.addView(deleteItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(deleteItem);
        deleteItem.setOnClickListener(v -> onActionBarItemClick(delete));

        photoVideoAdapter = new SharedPhotoVideoAdapter(context);
        documentsAdapter = new SharedDocumentsAdapter(context, 1);
        voiceAdapter = new SharedDocumentsAdapter(context, 2);
        audioAdapter = new SharedDocumentsAdapter(context, 4);
        gifAdapter = new GifAdapter(context);
        documentsSearchAdapter = new MediaSearchAdapter(context, 1);
        audioSearchAdapter = new MediaSearchAdapter(context, 4);
        linksSearchAdapter = new MediaSearchAdapter(context, 3);
        groupUsersSearchAdapter = new GroupUsersSearchAdapter(context);
        commonGroupsAdapter = new CommonGroupsAdapter(context);
        chatUsersAdapter = new ChatUsersAdapter(context);
        chatUsersAdapter.sortedUsers = sortedUsers;
        chatUsersAdapter.chatInfo = membersFirst ? chatInfo : null;
        linksAdapter = new SharedLinksAdapter(context);

        setWillNotDraw(false);

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
                            if (canShowSearchItem()) {
                                if (searchItemState == 2) {
                                    searchItem.setAlpha(1.0f - scrollProgress);
                                } else if (searchItemState == 1) {
                                    searchItem.setAlpha(scrollProgress);
                                }
                            } else {
                                searchItem.setAlpha(0.0f);
                            }
                        }
                    }
                }
            };
            addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 48, 0, 0));
            mediaPages[a] = mediaPage;

            final ExtendedGridLayoutManager layoutManager = mediaPages[a].layoutManager = new ExtendedGridLayoutManager(context, 100) {

                private Size size = new Size();

                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }

                @Override
                protected void calculateExtraLayoutSpace(RecyclerView.State state, int[] extraLayoutSpace) {
                    super.calculateExtraLayoutSpace(state, extraLayoutSpace);
                    if (mediaPage.selectedType == 0) {
                        extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], SharedPhotoVideoCell.getItemSize(columnsCount) * 2);
                    } else if (mediaPage.selectedType == 1) {
                        extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], AndroidUtilities.dp(56f) * 2);
                    }
                }

                @Override
                protected Size getSizeForItem(int i) {
                    TLRPC.Document document;

                    if (mediaPage.listView.getAdapter() == gifAdapter && !sharedMediaData[5].messages.isEmpty()) {
                        document = sharedMediaData[5].messages.get(i).getDocument();
                    } else {
                        document = null;
                    }
                    size.width = size.height = 100;
                    if (document != null) {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                        if (thumb != null && thumb.w != 0 && thumb.h != 0) {
                            size.width = thumb.w;
                            size.height = thumb.h;
                        }
                        ArrayList<TLRPC.DocumentAttribute> attributes = document.attributes;
                        for (int b = 0; b < attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                    }
                    return size;
                }

                @Override
                protected int getFlowItemCount() {
                    if (mediaPage.listView.getAdapter() != gifAdapter) {
                        return 0;
                    }
                    return getItemCount();
                }

                @Override
                public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler, RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info);
                    final AccessibilityNodeInfoCompat.CollectionItemInfoCompat itemInfo = info.getCollectionItemInfo();
                    if (itemInfo != null && itemInfo.isHeading()) {
                        info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(itemInfo.getRowIndex(), itemInfo.getRowSpan(), itemInfo.getColumnIndex(), itemInfo.getColumnSpan(), false));
                    }
                }
            };
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (mediaPage.listView.getAdapter() != gifAdapter) {
                        return mediaPage.layoutManager.getSpanCount();
                    }
                    if (mediaPage.listView.getAdapter() == gifAdapter && sharedMediaData[5].messages.isEmpty()) {
                        return mediaPage.layoutManager.getSpanCount();
                    }
                    return mediaPage.layoutManager.getSpanSizeForItem(position);
                }
            });
            mediaPages[a].listView = new RecyclerListView(context) {

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    checkLoadMoreScroll(mediaPage, mediaPage.listView, layoutManager);
                    if (mediaPage.selectedType == 0) {
                        PhotoViewer.getInstance().checkCurrentImageVisibility();
                    }
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (getAdapter() == photoVideoAdapter) {
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
                    if (getAdapter() == photoVideoAdapter) {
                        if (getChildViewHolder(child).getItemViewType() == 1) {
                            return true;
                        }
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }

            };
            mediaPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            mediaPages[a].listView.setPinnedSectionOffsetY(-AndroidUtilities.dp(2));
            mediaPages[a].listView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
            mediaPages[a].listView.setItemAnimator(null);
            mediaPages[a].listView.setClipToPadding(false);
            mediaPages[a].listView.setSectionsType(2);
            mediaPages[a].listView.setLayoutManager(layoutManager);
            mediaPages[a].addView(mediaPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].listView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    if (mediaPage.listView.getAdapter() == gifAdapter) {
                        int position = parent.getChildAdapterPosition(view);
                        outRect.left = 0;
                        outRect.bottom = 0;
                        if (!mediaPage.layoutManager.isFirstRow(position)) {
                            outRect.top = AndroidUtilities.dp(2);
                        } else {
                            outRect.top = 0;
                        }
                        outRect.right = mediaPage.layoutManager.isLastInRow(position) ? 0 : AndroidUtilities.dp(2);
                    } else {
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    }
                }
            });
            mediaPages[a].listView.setOnItemClickListener((view, position) -> {
                if (mediaPage.selectedType == 7) {
                    if (view instanceof UserCell) {
                        TLRPC.ChatParticipant participant;
                        if (!chatUsersAdapter.sortedUsers.isEmpty()) {
                            participant = chatUsersAdapter.chatInfo.participants.participants.get(chatUsersAdapter.sortedUsers.get(position));
                        } else {
                            participant = chatUsersAdapter.chatInfo.participants.participants.get(position);
                        }
                        onMemberClick(participant, false);
                    } else if (mediaPage.listView.getAdapter() == groupUsersSearchAdapter) {
                        long user_id;
                        TLObject object = groupUsersSearchAdapter.getItem(position);
                        if (object instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) object;
                            user_id = MessageObject.getPeerId(channelParticipant.peer);
                        } else if (object instanceof TLRPC.ChatParticipant) {
                            TLRPC.ChatParticipant chatParticipant = (TLRPC.ChatParticipant) object;
                            user_id = chatParticipant.user_id;
                        } else {
                            return;
                        }

                        if (user_id == 0 || user_id == profileActivity.getUserConfig().getClientUserId()) {
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putLong("user_id", user_id);
                        profileActivity.presentFragment(new ProfileActivity(args));
                    }
                } else if (mediaPage.selectedType == 6 && view instanceof ProfileSearchCell) {
                    TLRPC.Chat chat = ((ProfileSearchCell) view).getChat();
                    Bundle args = new Bundle();
                    args.putLong("chat_id", chat.id);
                    if (!profileActivity.getMessagesController().checkCanOpenChat(args, profileActivity)) {
                        return;
                    }
                    profileActivity.presentFragment(new ChatActivity(args));
                } else if (mediaPage.selectedType == 1 && view instanceof SharedDocumentCell) {
                    onItemClick(position, view, ((SharedDocumentCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if (mediaPage.selectedType == 3 && view instanceof SharedLinkCell) {
                    onItemClick(position, view, ((SharedLinkCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view instanceof SharedAudioCell) {
                    onItemClick(position, view, ((SharedAudioCell) view).getMessage(), 0, mediaPage.selectedType);
                }  else if (mediaPage.selectedType == 5 && view instanceof ContextLinkCell) {
                    onItemClick(position, view, (MessageObject) ((ContextLinkCell) view).getParentObject(), 0, mediaPage.selectedType);
                }
            });
            mediaPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    checkLoadMoreScroll(mediaPage, recyclerView, layoutManager);
                    if (dy != 0 && (mediaPages[0].selectedType == 0 || mediaPages[0].selectedType == 5) && !sharedMediaData[0].messages.isEmpty()) {
                        showFloatingDateView();
                    }
                }
            });
            mediaPages[a].listView.setOnItemLongClickListener((view, position) -> {
                if (isActionModeShowed) {
                    mediaPage.listView.getOnItemClickListener().onItemClick(view, position);
                    return true;
                }
                if (mediaPage.selectedType == 7 && view instanceof UserCell) {
                    final TLRPC.ChatParticipant participant;
                    if (!chatUsersAdapter.sortedUsers.isEmpty()) {
                        participant = chatUsersAdapter.chatInfo.participants.participants.get(chatUsersAdapter.sortedUsers.get(position));
                    } else {
                        participant = chatUsersAdapter.chatInfo.participants.participants.get(position);
                    }
                    return onMemberClick(participant, true);
                } else if (mediaPage.selectedType == 1 && view instanceof SharedDocumentCell) {
                    return onItemLongClick(((SharedDocumentCell) view).getMessage(), view, 0);
                } else if (mediaPage.selectedType == 3 && view instanceof SharedLinkCell) {
                    return onItemLongClick(((SharedLinkCell) view).getMessage(), view, 0);
                } else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view instanceof SharedAudioCell) {
                    return onItemLongClick(((SharedAudioCell) view).getMessage(), view, 0);
                } else if (mediaPage.selectedType == 5 && view instanceof ContextLinkCell) {
                    return onItemLongClick((MessageObject) ((ContextLinkCell) view).getParentObject(), view, 0);
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
                    setIsSingleCell(false);
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
                    } else if (mediaPage.selectedType == 6) {
                        if (scrollSlidingTextTabStrip.getTabsCount() == 1) {
                            setIsSingleCell(true);
                        }
                        return 1;
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

            mediaPages[a].scrollHelper = new RecyclerAnimationScrollHelper(mediaPages[a].listView, mediaPages[a].layoutManager);
        }

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setOverrideColor(Theme.key_chat_mediaTimeBackground, Theme.key_chat_mediaTimeText);
        floatingDateView.setTranslationY(-AndroidUtilities.dp(48));
        addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48 + 4, 0, 0));

        addView(fragmentContextView = new FragmentContextView(context, parent, this, false, null), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));
        fragmentContextView.setDelegate((start, show) -> {
            if (!start) {
                requestLayout();
            }
        });

        addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
        addView(actionModeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(Theme.getColor(Theme.key_divider));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        layoutParams.topMargin = AndroidUtilities.dp(48) - 1;
        addView(shadowLine, layoutParams);

        updateTabs(false);
        switchToCurrentSelectedMode(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (fragmentContextView != null && fragmentContextView.isCallStyle()) {
            canvas.save();
            canvas.translate(fragmentContextView.getX(), fragmentContextView.getY());
            fragmentContextView.setDrawOverlay(true);
            fragmentContextView.draw(canvas);
            fragmentContextView.setDrawOverlay(false);
            canvas.restore();
        }
    }

    private ScrollSlidingTextTabStrip createScrollingTextTabStrip(Context context) {
        ScrollSlidingTextTabStrip scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        if (initialTab != -1) {
            scrollSlidingTextTabStrip.setInitialTabId(initialTab);
            initialTab = -1;
        }
        scrollSlidingTextTabStrip.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        scrollSlidingTextTabStrip.setColors(Theme.key_profile_tabSelectedLine, Theme.key_profile_tabSelectedText, Theme.key_profile_tabText, Theme.key_profile_tabSelector);
        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (mediaPages[0].selectedType == id) {
                    return;
                }
                mediaPages[1].selectedType = id;
                mediaPages[1].setVisibility(View.VISIBLE);
                hideFloatingDateView(true);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
                onSelectedTabChanged();
            }

            @Override
            public void onSamePageSelected() {
                scrollToTop();
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
                if (canShowSearchItem()) {
                    if (searchItemState == 1) {
                        searchItem.setAlpha(progress);
                    } else if (searchItemState == 2) {
                        searchItem.setAlpha(1.0f - progress);
                    }
                } else {
                    searchItem.setVisibility(INVISIBLE);
                    searchItem.setAlpha(0.0f);
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
                    startStopVisibleGifs();
                }
            }
        });
        return scrollSlidingTextTabStrip;
    }

    private boolean fillMediaData(int type) {
        SharedMediaData[] mediaData = sharedMediaPreloader.getSharedMediaData();
        if (mediaData == null) {
            return false;
        }
        sharedMediaData[type].totalCount = mediaData[type].totalCount;
        sharedMediaData[type].messages.addAll(mediaData[type].messages);
        sharedMediaData[type].sections.addAll(mediaData[type].sections);
        for (HashMap.Entry<String, ArrayList<MessageObject>> entry : mediaData[type].sectionArrays.entrySet()) {
            sharedMediaData[type].sectionArrays.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (int i = 0; i < 2; i++) {
            sharedMediaData[type].messagesDict[i] = mediaData[type].messagesDict[i].clone();
            sharedMediaData[type].max_id[i] = mediaData[type].max_id[i];
            sharedMediaData[type].endReached[i] = mediaData[type].endReached[i];
        }
        return !mediaData[type].messages.isEmpty();
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
                ObjectAnimator.ofFloat(floatingDateView, View.TRANSLATION_Y, additionalFloatingTranslation));
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
                    ObjectAnimator.ofFloat(floatingDateView, View.TRANSLATION_Y, -AndroidUtilities.dp(48) + additionalFloatingTranslation));
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

    private void scrollToTop() {
        int height;
        switch (mediaPages[0].selectedType) {
            case 0:
                height = SharedPhotoVideoCell.getItemSize(columnsCount);
                break;
            case 1:
            case 2:
            case 4:
                height = AndroidUtilities.dp(56);
                break;
            case 3:
                height = AndroidUtilities.dp(100);
                break;
            case 5:
                height = AndroidUtilities.dp(60);
                break;
            case 6:
            default:
                height = AndroidUtilities.dp(58);
                break;
        }
        int scrollDistance = mediaPages[0].layoutManager.findFirstVisibleItemPosition() * height;
        if (scrollDistance >= mediaPages[0].listView.getMeasuredHeight() * 1.2f) {
            mediaPages[0].scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            mediaPages[0].scrollHelper.scrollToPosition(0, 0, false, true);
        } else {
            mediaPages[0].listView.smoothScrollToPosition(0);
        }
    }

    private void checkLoadMoreScroll(MediaPage mediaPage, RecyclerView recyclerView, LinearLayoutManager layoutManager) {
        if (searching && searchWas || mediaPage.selectedType == 7) {
            return;
        }
        int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
        int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        int totalItemCount = recyclerView.getAdapter().getItemCount();

        if (mediaPage.selectedType == 7) {

        } else if (mediaPage.selectedType == 6) {
            if (visibleItemCount > 0) {
                if (!commonGroupsAdapter.endReached && !commonGroupsAdapter.loading && !commonGroupsAdapter.chats.isEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
                    commonGroupsAdapter.getChats(commonGroupsAdapter.chats.get(commonGroupsAdapter.chats.size() - 1).id, 100);
                }
            }
        } else {
            final int threshold;
            if (mediaPage.selectedType == 0) {
                threshold = 3;
            } else if (mediaPage.selectedType == 5) {
                threshold = 10;
            } else {
                threshold = 6;
            }
            if (firstVisibleItem + visibleItemCount > totalItemCount - threshold && !sharedMediaData[mediaPage.selectedType].loading) {
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
                    profileActivity.getMediaDataController().loadMedia(dialog_id, 50, sharedMediaData[mediaPage.selectedType].max_id[0], type, 1, profileActivity.getClassGuid());
                } else if (mergeDialogId != 0 && !sharedMediaData[mediaPage.selectedType].endReached[1]) {
                    sharedMediaData[mediaPage.selectedType].loading = true;
                    profileActivity.getMediaDataController().loadMedia(mergeDialogId, 50, sharedMediaData[mediaPage.selectedType].max_id[1], type, 1, profileActivity.getClassGuid());
                }
            }
            if (mediaPages[0].listView == recyclerView && (mediaPages[0].selectedType == 0 || mediaPages[0].selectedType == 5) && firstVisibleItem != RecyclerView.NO_POSITION) {
                RecyclerListView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem);
                if (holder != null && holder.getItemViewType() == 0) {
                    if (holder.itemView instanceof SharedPhotoVideoCell) {
                        SharedPhotoVideoCell cell = (SharedPhotoVideoCell) holder.itemView;
                        MessageObject messageObject = cell.getMessageObject(0);
                        if (messageObject != null) {
                            floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
                        }
                    } else if (holder.itemView instanceof ContextLinkCell) {
                        ContextLinkCell cell = (ContextLinkCell) holder.itemView;
                        floatingDateView.setCustomDate(cell.getDate(), false, true);
                    }
                }
            }
        }
    }

    public ActionBarMenuItem getSearchItem() {
        return searchItem;
    }

    public boolean isSearchItemVisible() {
        if (mediaPages[0].selectedType == 7) {
            return profileActivity.canSearchMembers();
        }
        return mediaPages[0].selectedType != 0 && mediaPages[0].selectedType != 2 && mediaPages[0].selectedType != 5 && mediaPages[0].selectedType != 6;
    }

    public int getSelectedTab() {
        return scrollSlidingTextTabStrip.getCurrentTabId();
    }

    public int getClosestTab() {
        if (mediaPages[1] != null && mediaPages[1].getVisibility() == View.VISIBLE) {
            if (tabsAnimationInProgress && !backAnimation) {
                return mediaPages[1].selectedType;
            } else if (Math.abs(mediaPages[1].getTranslationX()) < mediaPages[1].getMeasuredWidth() / 2f) {
                return mediaPages[1].selectedType;
            }
        }
        return scrollSlidingTextTabStrip.getCurrentTabId();
    }

    protected void onSelectedTabChanged() {

    }

    protected boolean canShowSearchItem() {
        return true;
    }

    protected void onSearchStateChanged(boolean expanded) {

    }

    protected boolean onMemberClick(TLRPC.ChatParticipant participant, boolean isLong) {
        return false;
    }

    public void onDestroy() {
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.mediaDidLoad);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.messagesDeleted);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByServer);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.messagePlayingDidStart);
    }

    private void checkCurrentTabValid() {
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (!scrollSlidingTextTabStrip.hasTab(id)) {
            id = scrollSlidingTextTabStrip.getFirstTabId();
            scrollSlidingTextTabStrip.setInitialTabId(id);
            mediaPages[0].selectedType = id;
            switchToCurrentSelectedMode(false);
        }
    }

    public void setNewMediaCounts(int[] mediaCounts) {
        boolean hadMedia = false;
        for (int a = 0; a < 6; a++) {
            if (hasMedia[a] >= 0) {
                hadMedia = true;
                break;
            }
        }
        System.arraycopy(mediaCounts, 0, hasMedia, 0, 6);
        updateTabs(true);
        if (!hadMedia && scrollSlidingTextTabStrip.getCurrentTabId() == 6) {
            scrollSlidingTextTabStrip.resetTab();
        }
        checkCurrentTabValid();
    }

    public void setCommonGroupsCount(int count) {
        hasMedia[6] = count;
        updateTabs(true);
        checkCurrentTabValid();
    }

    public void onActionBarItemClick(int id) {
        if (id == delete) {
            TLRPC.Chat currentChat = null;
            TLRPC.User currentUser = null;
            TLRPC.EncryptedChat currentEncryptedChat = null;
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                currentEncryptedChat = profileActivity.getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialog_id));
            } else if (DialogObject.isUserDialog(dialog_id)) {
                currentUser = profileActivity.getMessagesController().getUser(dialog_id);
            } else {
                currentChat = profileActivity.getMessagesController().getChat(-dialog_id);
            }
            AlertsCreator.createDeleteMessagesAlert(profileActivity, currentUser, currentChat, currentEncryptedChat, null, mergeDialogId, null, selectedFiles, null, false, 1, () -> {
                showActionMode(false);
                actionBar.closeSearchField();
                cantDeleteMessagesCount = 0;
            }, null);
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
                showActionMode(false);

                if (dids.size() > 1 || dids.get(0) == profileActivity.getUserConfig().getClientUserId() || message != null) {
                    updateRowsSelection();
                    for (int a = 0; a < dids.size(); a++) {
                        long did = dids.get(a);
                        if (message != null) {
                            profileActivity.getSendMessagesHelper().sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null);
                        }
                        profileActivity.getSendMessagesHelper().sendMessage(fmessages, did, false,false, true, 0);
                    }
                    fragment1.finishFragment();
                } else {
                    long did = dids.get(0);
                    Bundle args1 = new Bundle();
                    args1.putBoolean("scrollToTopOnResume", true);
                    if (DialogObject.isEncryptedDialog(did)) {
                        args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                    } else {
                        if (DialogObject.isUserDialog(did)) {
                            args1.putLong("user_id", did);
                        } else {
                            args1.putLong("chat_id", -did);
                        }
                        if (!profileActivity.getMessagesController().checkCanOpenChat(args1, fragment1)) {
                            return;
                        }
                    }

                    profileActivity.getNotificationCenter().postNotificationName(NotificationCenter.closeChats);

                    ChatActivity chatActivity = new ChatActivity(args1);
                    fragment1.presentFragment(chatActivity, true);
                    chatActivity.showFieldPanelForForward(true, fmessages);
                }
            });
            profileActivity.presentFragment(fragment);
        } else if (id == gotochat) {
            if (selectedFiles[0].size() + selectedFiles[1].size() != 1) {
                return;
            }
            MessageObject messageObject = selectedFiles[selectedFiles[0].size() == 1 ? 0 : 1].valueAt(0);
            Bundle args = new Bundle();
            long dialogId = messageObject.getDialogId();
            if (DialogObject.isEncryptedDialog(dialogId)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
            } else if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else {
                TLRPC.Chat chat = profileActivity.getMessagesController().getChat(-dialogId);
                if (chat != null && chat.migrated_to != null) {
                    args.putLong("migrated_to", dialogId);
                    dialogId = -chat.migrated_to.channel_id;
                }
                args.putLong("chat_id", -dialogId);
            }
            args.putInt("message_id", messageObject.getId());
            args.putBoolean("need_remove_previous_same_chat_activity", false);
            profileActivity.presentFragment(new ChatActivity(args), false);
        }
    }

    private boolean prepareForMoving(MotionEvent ev, boolean forward) {
        int id = scrollSlidingTextTabStrip.getNextPageId(forward);
        if (id < 0) {
            return false;
        }
        if (canShowSearchItem()) {
            if (searchItemState != 0) {
                if (searchItemState == 2) {
                    searchItem.setAlpha(1.0f);
                } else if (searchItemState == 1) {
                    searchItem.setAlpha(0.0f);
                    searchItem.setVisibility(View.INVISIBLE);
                }
                searchItemState = 0;
            }
        } else {
            searchItem.setVisibility(INVISIBLE);
            searchItem.setAlpha(0.0f);
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        hideFloatingDateView(true);
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

    int topPadding;
    int lastMeasuredTopPadding;

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        topPadding = top;
        for (int a = 0; a < mediaPages.length; a++) {
            mediaPages[a].setTranslationY(topPadding - lastMeasuredTopPadding);
        }
        fragmentContextView.setTranslationY(AndroidUtilities.dp(48) + top);
        additionalFloatingTranslation = top;
        floatingDateView.setTranslationY((floatingDateView.getTag() == null ? -AndroidUtilities.dp(48) : 0) + additionalFloatingTranslation);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = profileActivity.getListView().getHeight();
        if (heightSize == 0) {
            heightSize = MeasureSpec.getSize(heightMeasureSpec);
        }

        setMeasuredDimension(widthSize, heightSize);

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                continue;
            }
            if (child instanceof MediaPage) {
                measureChildWithMargins(child, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY), 0);
                ((MediaPage) child).listView.setPadding(0, 0 ,0, topPadding);
            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
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

    public boolean isCurrentTabFirst() {
        return scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip.getFirstTabId();
    }

    public RecyclerListView getCurrentListView() {
        return mediaPages[0].listView;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (profileActivity.getParentLayout() != null && !profileActivity.getParentLayout().checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
            if (ev != null) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(ev);
            }
            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking && ev.getY() >= AndroidUtilities.dp(48)) {
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
                    if (canShowSearchItem()) {
                        if (searchItemState == 2) {
                            searchItem.setAlpha(1.0f - scrollProgress);
                        } else if (searchItemState == 1) {
                            searchItem.setAlpha(scrollProgress);
                        }
                    } else {
                        searchItem.setAlpha(0.0f);
                    }
                    scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress);
                    onSelectedTabChanged();
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
                                if (canShowSearchItem()) {
                                    if (searchItemState == 2) {
                                        searchItem.setAlpha(1.0f);
                                    } else if (searchItemState == 1) {
                                        searchItem.setAlpha(0.0f);
                                        searchItem.setVisibility(View.INVISIBLE);
                                    }
                                } else {
                                    searchItem.setVisibility(INVISIBLE);
                                    searchItem.setAlpha(0.0f);
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
                                scrollSlidingTextTabStrip.selectTabWithId(mediaPages[0].selectedType, 1.0f);
                                onSelectedTabChanged();
                                startStopVisibleGifs();
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
                    onSelectedTabChanged();
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

    public boolean closeActionMode() {
        if (isActionModeShowed) {
            for (int a = 1; a >= 0; a--) {
                selectedFiles[a].clear();
            }
            cantDeleteMessagesCount = 0;
            showActionMode(false);
            updateRowsSelection();
            return true;
        } else {
            return false;
        }
    }

    public void setVisibleHeight(int height) {
        height = Math.max(height, AndroidUtilities.dp(120));
        for (int a = 0; a < mediaPages.length; a++) {
            float t = -(getMeasuredHeight() - height) / 2f;
            mediaPages[a].emptyView.setTranslationY(t);
            mediaPages[a].progressView.setTranslationY(-t);
        }
    }

    private AnimatorSet actionModeAnimation;
    private void showActionMode(boolean show) {
        if (isActionModeShowed == show) {
            return;
        }
        isActionModeShowed = show;
        if (actionModeAnimation != null) {
            actionModeAnimation.cancel();
        }
        if (show) {
            actionModeLayout.setVisibility(VISIBLE);
        }
        actionModeAnimation = new AnimatorSet();
        actionModeAnimation.playTogether(ObjectAnimator.ofFloat(actionModeLayout, View.ALPHA, show ? 1.0f : 0.0f));
        actionModeAnimation.setDuration(180);
        actionModeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                actionModeAnimation = null;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (actionModeAnimation == null) {
                    return;
                }
                actionModeAnimation = null;
                if (!show) {
                    actionModeLayout.setVisibility(INVISIBLE);
                }
            }
        });
        actionModeAnimation.start();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mediaDidLoad) {
            long uid = (Long) args[0];
            int guid = (Integer) args[3];
            int type = (Integer) args[4];
            if (guid == profileActivity.getClassGuid()) {
                sharedMediaData[type].totalCount = (Integer) args[1];
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                boolean enc = DialogObject.isEncryptedDialog(dialog_id);
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
                } else if (type == 5) {
                    adapter = gifAdapter;
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
                sharedMediaData[type].loading = false;
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject message = arr.get(a);
                    sharedMediaData[type].addMessage(message, loadIndex, false, enc);
                }
                sharedMediaData[type].endReached[loadIndex] = (Boolean) args[5];
                if (loadIndex == 0 && sharedMediaData[type].endReached[loadIndex] && mergeDialogId != 0) {
                    sharedMediaData[type].loading = true;
                    profileActivity.getMediaDataController().loadMedia(mergeDialogId, 50, sharedMediaData[type].max_id[1], type, 1, profileActivity.getClassGuid());
                }
                if (adapter != null) {
                    RecyclerListView listView = null;
                    for (int a = 0; a < mediaPages.length; a++) {
                        if (mediaPages[a].listView.getAdapter() == adapter) {
                            listView = mediaPages[a].listView;
                            mediaPages[a].listView.stopScroll();
                        }
                    }
                    int newItemCount = adapter.getItemCount();
                    if (sharedMediaData[type].messages.isEmpty() && !sharedMediaData[type].loading) {
                        adapter.notifyDataSetChanged();
                        if (listView != null) {
                            animateItemsEnter(listView, oldItemCount);
                        }
                    } else {
                        adapter.notifyDataSetChanged();
                        if (listView != null && newItemCount >= oldItemCount) {
                            animateItemsEnter(listView, oldItemCount);
                        }
                    }
                }
                scrolling = true;
            } else if (sharedMediaPreloader != null && sharedMediaData[type].messages.isEmpty()) {
                if (fillMediaData(type)) {
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
                    } else if (type == 5) {
                        adapter = gifAdapter;
                    }
                    if (adapter != null) {
                        for (int a = 0; a < mediaPages.length; a++) {
                            if (mediaPages[a].listView.getAdapter() == adapter) {
                                mediaPages[a].listView.stopScroll();
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                    scrolling = true;
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            TLRPC.Chat currentChat = null;
            if (DialogObject.isChatDialog(dialog_id)) {
                currentChat = profileActivity.getMessagesController().getChat(-dialog_id);
            }
            long channelId = (Long) args[1];
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
                if (gifAdapter != null) {
                    gifAdapter.notifyDataSetChanged();
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
                boolean enc = DialogObject.isEncryptedDialog(dialog_id);
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
                        } else if (mediaPages[a].selectedType == 5) {
                            adapter = gifAdapter;
                        }
                        if (adapter != null) {
                            int count = adapter.getItemCount();
                            photoVideoAdapter.notifyDataSetChanged();
                            documentsAdapter.notifyDataSetChanged();
                            voiceAdapter.notifyDataSetChanged();
                            linksAdapter.notifyDataSetChanged();
                            audioAdapter.notifyDataSetChanged();
                            gifAdapter.notifyDataSetChanged();
                        }
                    }
                    updateTabs(true);
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
        } else if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
                for (int b = 0; b < mediaPages.length; b++) {
                    int count = mediaPages[b].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = mediaPages[b].listView.getChildAt(a);
                        if (view instanceof SharedAudioCell) {
                            SharedAudioCell cell = (SharedAudioCell) view;
                            MessageObject messageObject = cell.getMessage();
                            if (messageObject != null) {
                                cell.updateButtonState(false, true);
                            }
                        }
                    }
                }
            } else {
                MessageObject messageObject = (MessageObject) args[0];
                if (messageObject.eventId != 0) {
                    return;
                }
                for (int b = 0; b < mediaPages.length; b++) {
                    int count = mediaPages[b].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = mediaPages[b].listView.getChildAt(a);
                        if (view instanceof SharedAudioCell) {
                            SharedAudioCell cell = (SharedAudioCell) view;
                            MessageObject messageObject1 = cell.getMessage();
                            if (messageObject1 != null) {
                                cell.updateButtonState(false, true);
                            }
                        }
                    }
                }
            }
        }
    }

    private void animateItemsEnter(final RecyclerListView finalListView, int oldItemCount) {
        int n = finalListView.getChildCount();
        View progressView = null;
        for (int i = 0; i < n; i++) {
            View child = finalListView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                progressView = child;
            }
        }
        final View finalProgressView = progressView;
        if (progressView != null) {
            finalListView.removeView(progressView);
        }
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                int n = finalListView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = finalListView.getChildAt(i);
                    if (child != finalProgressView && finalListView.getChildAdapterPosition(child) >= oldItemCount - 1) {
                        child.setAlpha(0);
                        int s = Math.min(finalListView.getMeasuredHeight(), Math.max(0, child.getTop()));
                        int delay = (int) ((s / (float) finalListView.getMeasuredHeight()) * 100);
                        ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                        a.setStartDelay(delay);
                        a.setDuration(200);
                        animatorSet.playTogether(a);
                    }
                    if (finalProgressView != null && finalProgressView.getParent() == null) {
                        finalListView.addView(finalProgressView);
                        RecyclerView.LayoutManager layoutManager = finalListView.getLayoutManager();
                        if (layoutManager != null) {
                            layoutManager.ignoreView(finalProgressView);
                            Animator animator = ObjectAnimator.ofFloat(finalProgressView, ALPHA, finalProgressView.getAlpha(), 0);
                            animator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    finalProgressView.setAlpha(1f);
                                    layoutManager.stopIgnoringView(finalProgressView);
                                    finalListView.removeView(finalProgressView);
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

    public void onResume() {
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

    public void setChatUsers(ArrayList<Integer> sortedUsers, TLRPC.ChatFull chatInfo) {
        chatUsersAdapter.chatInfo = chatInfo;
        chatUsersAdapter.sortedUsers = sortedUsers;
        updateTabs(true);
        for (int a = 0; a < mediaPages.length; a++) {
            if (mediaPages[a].selectedType == 7) {
                mediaPages[a].listView.getAdapter().notifyDataSetChanged();
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
        if (gifAdapter != null) {
            gifAdapter.notifyDataSetChanged();
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
                } else if (child instanceof ContextLinkCell) {
                    ((ContextLinkCell) child).setChecked(false, true);
                }
            }
        }
    }

    public void setMergeDialogId(long did) {
        mergeDialogId = did;
    }

    private void updateTabs(boolean animated) {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        if (!profileActivity.isFragmentOpened) {
            animated = false;
        }
        int changed = 0;
        if ((chatUsersAdapter.chatInfo == null) == scrollSlidingTextTabStrip.hasTab(7)) {
            changed++;
        }
        if ((hasMedia[0] <= 0) == scrollSlidingTextTabStrip.hasTab(0)) {
            changed++;
        }
        if ((hasMedia[1] <= 0) == scrollSlidingTextTabStrip.hasTab(1)) {
            changed++;
        }
        if (!DialogObject.isEncryptedDialog(dialog_id)) {
            if ((hasMedia[3] <= 0) == scrollSlidingTextTabStrip.hasTab(3)) {
                changed++;
            }
            if ((hasMedia[4] <= 0) == scrollSlidingTextTabStrip.hasTab(4)) {
                changed++;
            }
        } else {
            if ((hasMedia[4] <= 0) == scrollSlidingTextTabStrip.hasTab(4)) {
                changed++;
            }
        }
        if ((hasMedia[2] <= 0) == scrollSlidingTextTabStrip.hasTab(2)) {
            changed++;
        }
        if ((hasMedia[5] <= 0) == scrollSlidingTextTabStrip.hasTab(5)) {
            changed++;
        }
        if ((hasMedia[6] <= 0) == scrollSlidingTextTabStrip.hasTab(6)) {
            changed++;
        }
        if (changed > 0) {
            if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final TransitionSet transitionSet = new TransitionSet();
                transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
                transitionSet.addTransition(new ChangeBounds());
                transitionSet.addTransition(new Visibility() {
                    @Override
                    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(
                                ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
                                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f),
                                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f)
                        );
                        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        return set;
                    }
                    @Override
                    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(
                                ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f),
                                ObjectAnimator.ofFloat(view, View.SCALE_X, view.getScaleX(), 0.5f),
                                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.getScaleX(), 0.5f)
                        );
                        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        return set;
                    }
                });
                transitionSet.setDuration(200);
                TransitionManager.beginDelayedTransition(scrollSlidingTextTabStrip.getTabsContainer(), transitionSet);

                scrollSlidingTextTabStrip.recordIndicatorParams();
            }
            SparseArray<View> idToView = scrollSlidingTextTabStrip.removeTabs();
            if (changed > 3) {
                idToView = null;
            }
            if (chatUsersAdapter.chatInfo != null) {
                if (!scrollSlidingTextTabStrip.hasTab(7)) {
                    scrollSlidingTextTabStrip.addTextTab(7, LocaleController.getString("GroupMembers", R.string.GroupMembers), idToView);
                }
            }
            if (hasMedia[0] > 0) {
                if (!scrollSlidingTextTabStrip.hasTab(0)) {
                    if (hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0 && hasMedia[5] == 0 && hasMedia[6] == 0 && chatUsersAdapter.chatInfo == null) {
                        scrollSlidingTextTabStrip.addTextTab(0, LocaleController.getString("SharedMediaTabFull2", R.string.SharedMediaTabFull2), idToView);
                    } else {
                        scrollSlidingTextTabStrip.addTextTab(0, LocaleController.getString("SharedMediaTab2", R.string.SharedMediaTab2), idToView);
                    }
                }
            }
            if (hasMedia[1] > 0) {
                if (!scrollSlidingTextTabStrip.hasTab(1)) {
                    scrollSlidingTextTabStrip.addTextTab(1, LocaleController.getString("SharedFilesTab2", R.string.SharedFilesTab2), idToView);
                }
            }
            if (!DialogObject.isEncryptedDialog(dialog_id)) {
                if (hasMedia[3] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(3)) {
                        scrollSlidingTextTabStrip.addTextTab(3, LocaleController.getString("SharedLinksTab2", R.string.SharedLinksTab2), idToView);
                    }
                }
                if (hasMedia[4] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(4)) {
                        scrollSlidingTextTabStrip.addTextTab(4, LocaleController.getString("SharedMusicTab2", R.string.SharedMusicTab2), idToView);
                    }
                }
            } else {
                if (hasMedia[4] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(4)) {
                        scrollSlidingTextTabStrip.addTextTab(4, LocaleController.getString("SharedMusicTab2", R.string.SharedMusicTab2), idToView);
                    }
                }
            }
            if (hasMedia[2] > 0) {
                if (!scrollSlidingTextTabStrip.hasTab(2)) {
                    scrollSlidingTextTabStrip.addTextTab(2, LocaleController.getString("SharedVoiceTab2", R.string.SharedVoiceTab2), idToView);
                }
            }
            if (hasMedia[5] > 0) {
                if (!scrollSlidingTextTabStrip.hasTab(5)) {
                    scrollSlidingTextTabStrip.addTextTab(5, LocaleController.getString("SharedGIFsTab2", R.string.SharedGIFsTab2), idToView);
                }
            }
            if (hasMedia[6] > 0) {
                if (!scrollSlidingTextTabStrip.hasTab(6)) {
                    scrollSlidingTextTabStrip.addTextTab(6, LocaleController.getString("SharedGroupsTab2", R.string.SharedGroupsTab2), idToView);
                }
            }
        }
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            mediaPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private void startStopVisibleGifs() {
        for (int b = 0; b < mediaPages.length; b++) {
            int count = mediaPages[b].listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = mediaPages[b].listView.getChildAt(a);
                if (child instanceof ContextLinkCell) {
                    ContextLinkCell cell = (ContextLinkCell) child;
                    ImageReceiver imageReceiver = cell.getPhotoImage();
                    if (b == 0) {
                        imageReceiver.setAllowStartAnimation(true);
                        imageReceiver.startAnimation();
                    } else {
                        imageReceiver.setAllowStartAnimation(false);
                        imageReceiver.stopAnimation();
                    }
                }
            }
        }
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < mediaPages.length; a++) {
            mediaPages[a].listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = mediaPages[a].listView.getAdapter();
        if (searching && searchWas) {
            if (animated) {
                if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2 || mediaPages[a].selectedType == 5 || mediaPages[a].selectedType == 6 || mediaPages[a].selectedType == 7 && !profileActivity.canSearchMembers()) {
                    searching = false;
                    searchWas = false;
                    switchToCurrentSelectedMode(true);
                    return;
                } else {
                    String text = searchItem.getSearchField().getText().toString();
                    if (mediaPages[a].selectedType == 1) {
                        if (documentsSearchAdapter != null) {
                            documentsSearchAdapter.search(text, false);
                            if (currentAdapter != documentsSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 3) {
                        if (linksSearchAdapter != null) {
                            linksSearchAdapter.search(text, false);
                            if (currentAdapter != linksSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(linksSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 4) {
                        if (audioSearchAdapter != null) {
                            audioSearchAdapter.search(text, false);
                            if (currentAdapter != audioSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(audioSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 7) {
                        if (groupUsersSearchAdapter != null) {
                            groupUsersSearchAdapter.search(text, false);
                            if (currentAdapter != groupUsersSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(groupUsersSearchAdapter);
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
                    } else if (mediaPages[a].selectedType == 7) {
                        if (currentAdapter != groupUsersSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(groupUsersSearchAdapter);
                        }
                        groupUsersSearchAdapter.notifyDataSetChanged();
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
            } else if (mediaPages[a].selectedType == 5) {
                if (currentAdapter != gifAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(gifAdapter);
                }
            } else if (mediaPages[a].selectedType == 6) {
                if (currentAdapter != commonGroupsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(commonGroupsAdapter);
                }
            } else if (mediaPages[a].selectedType == 7) {
                if (currentAdapter != chatUsersAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(chatUsersAdapter);
                }
            }
            if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2 || mediaPages[a].selectedType == 5 || mediaPages[a].selectedType == 6 || mediaPages[a].selectedType == 7 && !profileActivity.canSearchMembers()) {
                if (animated) {
                    searchItemState = 2;
                } else {
                    searchItemState = 0;
                    searchItem.setVisibility(View.INVISIBLE);
                }
            } else {
                if (animated) {
                    if (searchItem.getVisibility() == View.INVISIBLE && !actionBar.isSearchFieldVisible()) {
                        if (canShowSearchItem()) {
                            searchItemState = 1;
                            searchItem.setVisibility(View.VISIBLE);
                        } else {
                            searchItem.setVisibility(INVISIBLE);
                        }
                        searchItem.setAlpha(0.0f);
                    } else {
                        searchItemState = 0;
                    }
                } else if (searchItem.getVisibility() == View.INVISIBLE) {
                    if (canShowSearchItem()) {
                        searchItemState = 0;
                        searchItem.setAlpha(1.0f);
                        searchItem.setVisibility(View.VISIBLE);
                    } else {
                        searchItem.setVisibility(INVISIBLE);
                        searchItem.setAlpha(0.0f);
                    }
                }
            }
            if (mediaPages[a].selectedType == 6) {
                if (!commonGroupsAdapter.loading && !commonGroupsAdapter.endReached && commonGroupsAdapter.chats.isEmpty()) {
                    commonGroupsAdapter.getChats(0, 100);
                }
            } else if (mediaPages[a].selectedType == 7) {

            } else {
                if (!sharedMediaData[mediaPages[a].selectedType].loading && !sharedMediaData[mediaPages[a].selectedType].endReached[0] && sharedMediaData[mediaPages[a].selectedType].messages.isEmpty()) {
                    sharedMediaData[mediaPages[a].selectedType].loading = true;
                    documentsAdapter.notifyDataSetChanged();
                    profileActivity.getMediaDataController().loadMedia(dialog_id, 50, 0, mediaPages[a].selectedType, 1, profileActivity.getClassGuid());
                }
            }
            mediaPages[a].listView.setVisibility(View.VISIBLE);
        }
        if (searchItemState == 2 && actionBar.isSearchFieldVisible()) {
            ignoreSearchCollapse = true;
            actionBar.closeSearchField();
            searchItemState = 0;
            searchItem.setAlpha(0.0f);
            searchItem.setVisibility(View.INVISIBLE);
        }
    }

    private boolean onItemLongClick(MessageObject item, View view, int a) {
        if (isActionModeShowed || profileActivity.getParentActivity() == null || item == null) {
            return false;
        }
        AndroidUtilities.hideKeyboard(profileActivity.getParentActivity().getCurrentFocus());
        selectedFiles[item.getDialogId() == dialog_id ? 0 : 1].put(item.getId(), item);
        if (!item.canDeleteMessage(false, null)) {
            cantDeleteMessagesCount++;
        }
        deleteItem.setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
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
        } else if (view instanceof ContextLinkCell) {
            ((ContextLinkCell) view).setChecked(true, true);
        }
        if (!isActionModeShowed) {
            showActionMode(true);
        }
        return true;
    }

    private void onItemClick(int index, View view, MessageObject message, int a, int selectedMode) {
        if (message == null) {
            return;
        }
        if (isActionModeShowed) {
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
                showActionMode(false);
            } else {
                selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true);
                deleteItem.setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
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
            } else if (view instanceof ContextLinkCell) {
                ((ContextLinkCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            }
        } else {
            if (selectedMode == 0) {
                PhotoViewer.getInstance().setParentActivity(profileActivity.getParentActivity());
                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, provider);
            } else if (selectedMode == 2 || selectedMode == 4) {
                if (view instanceof SharedAudioCell) {
                    ((SharedAudioCell) view).didPressedButton();
                }
            } else if (selectedMode == 5) {
                PhotoViewer.getInstance().setParentActivity(profileActivity.getParentActivity());
                index = sharedMediaData[selectedMode].messages.indexOf(message);
                if (index < 0) {
                    ArrayList<MessageObject> documents = new ArrayList<>();
                    documents.add(message);
                    PhotoViewer.getInstance().openPhoto(documents, 0, 0, 0, provider);
                } else {
                    PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, provider);
                }
            } else if (selectedMode == 1) {
                if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    TLRPC.Document document = message.getDocument();
                    if (cell.isLoaded()) {
                        if (message.canPreviewDocument()) {
                            PhotoViewer.getInstance().setParentActivity(profileActivity.getParentActivity());
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
                        AndroidUtilities.openDocument(message, profileActivity.getParentActivity(), profileActivity);
                    } else if (!cell.isLoading()) {
                        MessageObject messageObject = cell.getMessage();
                        profileActivity.getFileLoader().loadFile(document, messageObject, 0, 0);
                        cell.updateFileExistIcon(true);
                    } else {
                        profileActivity.getFileLoader().cancelLoadFile(document);
                        cell.updateFileExistIcon(true);
                    }
                }
            } else if (selectedMode == 3) {
                try {
                    TLRPC.WebPage webPage = message.messageOwner.media != null ? message.messageOwner.media.webpage : null;
                    String link = null;
                    if (webPage != null && !(webPage instanceof TLRPC.TL_webPageEmpty)) {
                        if (webPage.cached_page != null) {
                            ArticleViewer.getInstance().setParentActivity(profileActivity.getParentActivity(), profileActivity);
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
    }

    private void openUrl(String link) {
        if (AndroidUtilities.shouldShowUrlInAlert(link)) {
            AlertsCreator.showOpenUrlAlert(profileActivity, link, true, true);
        } else {
            Browser.openUrl(profileActivity.getParentActivity(), link);
        }
    }

    private void openWebView(TLRPC.WebPage webPage, MessageObject message) {
        EmbedBottomSheet.show(profileActivity.getParentActivity(), message, provider, webPage.site_name, webPage.description, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height, false);
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
        }
    }

    SharedLinkCell.SharedLinkCellDelegate sharedLinkCellDelegate = new SharedLinkCell.SharedLinkCellDelegate() {
        @Override
        public void needOpenWebView(TLRPC.WebPage webPage, MessageObject message) {
            openWebView(webPage, message);
        }

        @Override
        public boolean canPerformActions() {
            return !isActionModeShowed;
        }

        @Override
        public void onLinkPress(String urlFinal, boolean longPress) {
            if (longPress) {
                BottomSheet.Builder builder = new BottomSheet.Builder(profileActivity.getParentActivity());
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
                profileActivity.showDialog(builder.create());
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
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            if (sharedMediaData[3].sections.size() == 0 && !sharedMediaData[3].loading) {
                return false;
            }
            return section == 0 || row != 0;
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
                return sharedMediaData[3].sectionArrays.get(sharedMediaData[3].sections.get(section)).size() + (section != 0 ? 1 : 0);
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
                view.setAlpha(0.0f);
            } else if (section < sharedMediaData[3].sections.size()) {
                view.setAlpha(1.0f);
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
                    View emptyStubView = createEmptyStubView(mContext, 3, dialog_id);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 2:
                default:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.showDate(false);
                    flickerLoadingView.setViewType(FlickerLoadingView.LINKS_TYPE);
                    view = flickerLoadingView;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
                        if (section != 0) {
                            position--;
                        }
                        SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position);
                        sharedLinkCell.setLink(messageObject, position != messageObjects.size() - 1 || section == sharedMediaData[3].sections.size() - 1 && sharedMediaData[3].loading);
                        if (isActionModeShowed) {
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
                if (section != 0 && position == 0) {
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
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            if (sharedMediaData[currentType].sections.size() == 0 && !sharedMediaData[currentType].loading) {
                return false;
            }
            return section == 0 || row != 0;
        }

        @Override
        public int getSectionCount() {
            if (sharedMediaData[currentType].sections.size() == 0 && !sharedMediaData[currentType].loading) {
                return 1;
            }
            return sharedMediaData[currentType].sections.size() + ((!sharedMediaData[currentType].sections.isEmpty() && (!sharedMediaData[currentType].endReached[0] || !sharedMediaData[currentType].endReached[1])) ? 1 : 0);
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
                return sharedMediaData[currentType].sectionArrays.get(sharedMediaData[currentType].sections.get(section)).size() + (section != 0 ? 1 : 0);
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
                view.setAlpha(0.0f);
            } else if (section < sharedMediaData[currentType].sections.size()) {
                view.setAlpha(1.0f);
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
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    view = flickerLoadingView;
                    if (currentType == 2) {
                        flickerLoadingView.setViewType(FlickerLoadingView.AUDIO_TYPE);
                    } else {
                        flickerLoadingView.setViewType(FlickerLoadingView.FILES_TYPE);
                    }
                    flickerLoadingView.showDate(false);
                    flickerLoadingView.setIsSingleCell(true);
                    break;
                case 4:
                    View emptyStubView = createEmptyStubView(mContext, currentType, dialog_id);
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
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
                        if (section != 0) {
                            position--;
                        }
                        SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position);
                        sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() - 1 || section == sharedMediaData[currentType].sections.size() - 1 && sharedMediaData[currentType].loading);
                        if (isActionModeShowed) {
                            sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedDocumentCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                    case 3: {
                        if (section != 0) {
                            position--;
                        }
                        SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position);
                        sharedAudioCell.setMessageObject(messageObject, position != messageObjects.size() - 1 || section == sharedMediaData[currentType].sections.size() - 1 && sharedMediaData[currentType].loading);
                        if (isActionModeShowed) {
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
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }

    public static View createEmptyStubView(Context context, int currentType, long dialog_id) {
        EmptyStubView emptyStubView = new EmptyStubView(context);
        if (currentType == 0) {
            emptyStubView.emptyImageView.setImageResource(R.drawable.tip1);
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoMediaSecret", R.string.NoMediaSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoMedia", R.string.NoMedia));
            }
        } else if (currentType == 1) {
            emptyStubView.emptyImageView.setImageResource(R.drawable.tip2);
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedFilesSecret", R.string.NoSharedFilesSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedFiles", R.string.NoSharedFiles));
            }
        } else if (currentType == 2) {
            emptyStubView.emptyImageView.setImageResource(R.drawable.tip5);
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedVoiceSecret", R.string.NoSharedVoiceSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedVoice", R.string.NoSharedVoice));
            }
        } else if (currentType == 3) {
            emptyStubView.emptyImageView.setImageResource(R.drawable.tip3);
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedLinksSecret", R.string.NoSharedLinksSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedLinks", R.string.NoSharedLinks));
            }
        } else if (currentType == 4) {
            emptyStubView.emptyImageView.setImageResource(R.drawable.tip4);
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedAudioSecret", R.string.NoSharedAudioSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedAudio", R.string.NoSharedAudio));
            }
        } else if (currentType == 5) {
            emptyStubView.emptyImageView.setImageResource(R.drawable.tip1);
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedGifSecret", R.string.NoSharedGifSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoGIFs", R.string.NoGIFs));
            }
        } else if (currentType == 6) {
            emptyStubView.emptyImageView.setImageDrawable(null);
            emptyStubView.emptyTextView.setText(LocaleController.getString("NoGroupsInCommon", R.string.NoGroupsInCommon));
        } else if (currentType == 7) {
            emptyStubView.emptyImageView.setImageDrawable(null);
            emptyStubView.emptyTextView.setText("");
        }
        return emptyStubView;
    }

    private static class EmptyStubView extends LinearLayout {

        final TextView emptyTextView;
        final ImageView emptyImageView;

        boolean ignoreRequestLayout;

        public EmptyStubView(Context context) {
            super(context);
            emptyTextView = new TextView(context);
            emptyImageView = new ImageView(context);

            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER);

            addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();
            ignoreRequestLayout = true;
            if (AndroidUtilities.isTablet()) {
                emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            } else {
                if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                    emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
                } else {
                    emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
                }
            }
            ignoreRequestLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public void requestLayout() {
            if (ignoreRequestLayout) {
                return;
            }
            super.requestLayout();
        }
    }

    private class SharedPhotoVideoAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public SharedPhotoVideoAdapter(Context context) {
            mContext = context;
        }

        public int getPositionForIndex(int i) {
            return i / columnsCount;
        }

        @Override
        public int getItemCount() {
            if (sharedMediaData[0].messages.size() == 0 && !sharedMediaData[0].loading) {
                return 1;
            }
            int count = (int) Math.ceil(sharedMediaData[0].messages.size() / (float) columnsCount);
            if (count != 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
                count++;
            }
            return count;
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
                            if (isActionModeShowed) {
                                didClickItem(cell, index, messageObject, a);
                                return true;
                            }
                            return onItemLongClick(messageObject, cell, a);
                        }
                    });
                    cache.add((SharedPhotoVideoCell) view);
                    break;
                case 1:
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
                default:
                case 2:
                    View emptyStubView = createEmptyStubView(mContext, 0, dialog_id);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].messages;
                SharedPhotoVideoCell cell = (SharedPhotoVideoCell) holder.itemView;
                cell.setItemsCount(columnsCount);
              //  cell.setLoading(!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1]);
                cell.setIsFirst(position == 0);
                for (int a = 0; a < columnsCount; a++) {
                    int index = position * columnsCount + a;
                    if (index < messageObjects.size()) {
                        MessageObject messageObject = messageObjects.get(index);
                        cell.setItem(a, sharedMediaData[0].messages.indexOf(messageObject), messageObject);
                        if (isActionModeShowed) {
                            cell.setChecked(a, selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            cell.setChecked(a, false, !scrolling);
                        }
                    } else {
                        cell.setItem(a, index, null);
                    }
                }
                cell.requestLayout();
            } else if (holder.getItemViewType() == 1) {
                FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                int count = (int) Math.ceil(sharedMediaData[0].messages.size() / (float) columnsCount);
                flickerLoadingView.skipDrawItemsCount(columnsCount - (columnsCount * count - sharedMediaData[0].messages.size()));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (sharedMediaData[0].messages.size() == 0 && !sharedMediaData[0].loading) {
                return 2;
            }
            int count = (int) Math.ceil(sharedMediaData[0].messages.size() / (float) columnsCount);
            if (position < count) {
                return 0;
            }
            return 1;
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
            if (DialogObject.isEncryptedDialog(did)) {
                return;
            }
            if (reqId != 0) {
                profileActivity.getConnectionsManager().cancelRequest(reqId, true);
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
            req.peer = profileActivity.getMessagesController().getInputPeer(did);
            if (req.peer == null) {
                return;
            }
            final int currentReqId = ++lastReqId;
            searchesInProgress++;
            reqId = profileActivity.getConnectionsManager().sendRequest(req, (response, error) -> {
                final ArrayList<MessageObject> messageObjects = new ArrayList<>();
                if (error == null) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    for (int a = 0; a < res.messages.size(); a++) {
                        TLRPC.Message message = res.messages.get(a);
                        if (max_id != 0 && message.id > max_id) {
                            continue;
                        }
                        messageObjects.add(new MessageObject(profileActivity.getCurrentAccount(), message, false, true));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (reqId != 0) {
                        if (currentReqId == lastReqId) {
                            int oldItemCounts = getItemCount();
                            globalSearch = messageObjects;
                            searchesInProgress--;
                            int count = getItemCount();
                            if (searchesInProgress == 0 || count != 0) {
                                switchToCurrentSelectedMode(false);
                            }

                            for (int a = 0; a < mediaPages.length; a++) {
                                if (mediaPages[a].selectedType == currentType) {
                                    if (searchesInProgress == 0 && count == 0) {
                                        mediaPages[a].emptyView.showProgress(false, true);
                                    } else if (oldItemCounts == 0) {
                                        animateItemsEnter(mediaPages[a].listView, 0);
                                    }
                                }
                            }
                            notifyDataSetChanged();

                        }
                        reqId = 0;
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
            profileActivity.getConnectionsManager().bindRequestToGuid(reqId, profileActivity.getClassGuid());
        }

        public void search(final String query, boolean animated) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }

            if (!searchResult.isEmpty() || !globalSearch.isEmpty()) {
                searchResult.clear();
                globalSearch.clear();
                notifyDataSetChanged();
            }

            if (TextUtils.isEmpty(query)) {
                if (!searchResult.isEmpty() || !globalSearch.isEmpty() || searchesInProgress != 0) {
                    searchResult.clear();
                    globalSearch.clear();
                    if (reqId != 0) {
                        profileActivity.getConnectionsManager().cancelRequest(reqId, true);
                        reqId = 0;
                        searchesInProgress--;
                    }
                }
            } else {
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == currentType) {
                        mediaPages[a].emptyView.showProgress(true, animated);
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
                int oldItemCount = getItemCount();
                searchResult = documents;
                int count = getItemCount();
                if (searchesInProgress == 0 || count != 0) {
                    switchToCurrentSelectedMode(false);
                }

                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == currentType) {
                        if (searchesInProgress == 0 && count == 0) {
                            mediaPages[a].emptyView.showProgress(false, true);
                        } else if (oldItemCount == 0) {
                            animateItemsEnter(mediaPages[a].listView, 0);
                        }
                    }
                }

                notifyDataSetChanged();

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
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (currentType == 1) {
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedDocumentCell.setDocument(messageObject, position != getItemCount() - 1);
                if (isActionModeShowed) {
                    sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 3) {
                SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedLinkCell.setLink(messageObject, position != getItemCount() - 1);
                if (isActionModeShowed) {
                    sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedLinkCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 4) {
                SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedAudioCell.setMessageObject(messageObject, position != getItemCount() - 1);
                if (isActionModeShowed) {
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

    private class GifAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public GifAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (sharedMediaData[5].messages.size() == 0 && !sharedMediaData[5].loading) {
                return false;
            }
            return true;
        }

        @Override
        public int getItemCount() {
            if (sharedMediaData[5].messages.size() == 0 && !sharedMediaData[5].loading) {
                return 1;
            }
            return sharedMediaData[5].messages.size();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getItemViewType(int position) {
            if (sharedMediaData[5].messages.size() == 0 && !sharedMediaData[5].loading) {
                return 1;
            }
            return 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == 1) {
                View emptyStubView = createEmptyStubView(mContext, 5, dialog_id);
                emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerListView.Holder(emptyStubView);
            }
            ContextLinkCell cell = new ContextLinkCell(mContext, true);
            cell.setCanPreviewGif(true);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() != 1) {
                MessageObject messageObject = sharedMediaData[5].messages.get(position);
                TLRPC.Document document = messageObject.getDocument();
                if (document != null) {
                    ContextLinkCell cell = (ContextLinkCell) holder.itemView;
                    cell.setGif(document, messageObject, messageObject.messageOwner.date, false);
                    if (isActionModeShowed) {
                        cell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                    } else {
                        cell.setChecked(false, !scrolling);
                    }
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ContextLinkCell) {
                ContextLinkCell cell = (ContextLinkCell) holder.itemView;
                ImageReceiver imageReceiver = cell.getPhotoImage();
                if (mediaPages[0].selectedType == 5) {
                    imageReceiver.setAllowStartAnimation(true);
                    imageReceiver.startAnimation();
                } else {
                    imageReceiver.setAllowStartAnimation(false);
                    imageReceiver.stopAnimation();
                }
            }
        }
    }

    private class CommonGroupsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        private boolean loading;
        private boolean firstLoaded;
        private boolean endReached;

        public CommonGroupsAdapter(Context context) {
            mContext = context;
        }

        private void getChats(long max_id, final int count) {
            if (loading) {
                return;
            }
            TLRPC.TL_messages_getCommonChats req = new TLRPC.TL_messages_getCommonChats();
            long uid;
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                TLRPC.EncryptedChat encryptedChat = profileActivity.getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialog_id));
                uid = encryptedChat.user_id;
            } else {
                uid = dialog_id;
            }
            req.user_id = profileActivity.getMessagesController().getInputUser(uid);
            if (req.user_id instanceof TLRPC.TL_inputUserEmpty) {
                return;
            }
            req.limit = count;
            req.max_id = max_id;
            loading = true;
            notifyDataSetChanged();
            int reqId = profileActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                int oldCount = getItemCount();
                if (error == null) {
                    TLRPC.messages_Chats res = (TLRPC.messages_Chats) response;
                    profileActivity.getMessagesController().putChats(res.chats, false);
                    endReached = res.chats.isEmpty() || res.chats.size() != count;
                    chats.addAll(res.chats);
                } else {
                    endReached = true;
                }

                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == 6) {
                        if (mediaPages[a].listView != null) {
                            final RecyclerListView listView = mediaPages[a].listView;
                            if (firstLoaded || oldCount == 0) {
                                animateItemsEnter(listView, 0);
                            }
                        }
                    }
                }
                loading = false;
                firstLoaded = true;
                notifyDataSetChanged();
            }));
            profileActivity.getConnectionsManager().bindRequestToGuid(reqId, profileActivity.getClassGuid());
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() != chats.size();
        }

        @Override
        public int getItemCount() {
            if (chats.isEmpty() && !loading) {
                return 1;
            }
            int count = chats.size();
            if (!chats.isEmpty()) {
                if (!endReached) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ProfileSearchCell(mContext);
                    break;
                case 2:
                    View emptyStubView = createEmptyStubView(mContext, 6, dialog_id);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 1:
                default:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.showDate(false);
                    flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE);
                    view = flickerLoadingView;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                TLRPC.Chat chat = chats.get(position);
                cell.setData(chat, null, null, null, false, false);
                cell.useSeparator = position != chats.size() - 1 || !endReached;
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (chats.isEmpty() && !loading) {
                return 2;
            }
            if (i < chats.size()) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    private class ChatUsersAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private TLRPC.ChatFull chatInfo;
        private ArrayList<Integer> sortedUsers;

        public ChatUsersAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            if (chatInfo != null && chatInfo.participants.participants.isEmpty()) {
                return 1;
            }
            return chatInfo != null ? chatInfo.participants.participants.size() : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == 1) {
                View emptyStubView = createEmptyStubView(mContext, 7, dialog_id);
                emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerListView.Holder(emptyStubView);
            }
            View view = new UserCell(mContext, 9, 0, true);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            UserCell userCell = (UserCell) holder.itemView;
            TLRPC.ChatParticipant part;
            if (!sortedUsers.isEmpty()) {
                part = chatInfo.participants.participants.get(sortedUsers.get(position));
            } else {
                part = chatInfo.participants.participants.get(position);
            }
            if (part != null) {
                String role;
                if (part instanceof TLRPC.TL_chatChannelParticipant) {
                    TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                    if (!TextUtils.isEmpty(channelParticipant.rank)) {
                        role = channelParticipant.rank;
                    } else {
                        if (channelParticipant instanceof TLRPC.TL_channelParticipantCreator) {
                            role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                        } else if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin) {
                            role = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                        } else {
                            role = null;
                        }
                    }
                } else {
                    if (part instanceof TLRPC.TL_chatParticipantCreator) {
                        role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                    } else if (part instanceof TLRPC.TL_chatParticipantAdmin) {
                        role = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                    } else {
                        role = null;
                    }
                }
                userCell.setAdminRole(role);
                userCell.setData(profileActivity.getMessagesController().getUser(part.user_id), null, null, 0, position != chatInfo.participants.participants.size() - 1);
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (chatInfo != null && chatInfo.participants.participants.isEmpty()) {
                return 1;
            }
            return 0;
        }
    }

    private class GroupUsersSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private int totalCount = 0;
        private TLRPC.Chat currentChat;
        int searchCount = 0;

        public GroupUsersSearchAdapter(Context context) {
            mContext = context;
            searchAdapterHelper = new SearchAdapterHelper(true);
            searchAdapterHelper.setDelegate(searchId -> {
                notifyDataSetChanged();
                if (searchId == 1) {
                    searchCount--;
                    if (searchCount == 0) {
                        for (int a = 0; a < mediaPages.length; a++) {
                            if (mediaPages[a].selectedType == 7) {
                                if (getItemCount() == 0) {
                                    mediaPages[a].emptyView.showProgress(false, true);
                                } else {
                                    animateItemsEnter(mediaPages[a].listView, 0);
                                }
                            }
                        }
                    }
                }
            });
            currentChat = profileActivity.getCurrentChat();
        }

        private boolean createMenuForParticipant(TLObject participant, boolean resultOnly) {
            if (participant instanceof TLRPC.ChannelParticipant) {
                TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                TLRPC.TL_chatChannelParticipant p = new TLRPC.TL_chatChannelParticipant();
                p.channelParticipant = channelParticipant;
                p.user_id = MessageObject.getPeerId(channelParticipant.peer);
                p.inviter_id = channelParticipant.inviter_id;
                p.date = channelParticipant.date;
                participant = p;
            }
            return profileActivity.onMemberClick((TLRPC.ChatParticipant) participant, true, resultOnly);
        }

        public void search(final String query, boolean animated) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            searchResultNames.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, false, true, false, false, ChatObject.isChannel(currentChat) ? currentChat.id : 0, false, 2, 0);
            notifyDataSetChanged();

            for (int a = 0; a < mediaPages.length; a++) {
                if (mediaPages[a].selectedType == 7) {
                    if (!TextUtils.isEmpty(query)) {
                        mediaPages[a].emptyView.showProgress(true, animated);
                    }
                }
            }

            if (!TextUtils.isEmpty(query)) {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> processSearch(query), 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;

                final ArrayList<TLObject> participantsCopy = !ChatObject.isChannel(currentChat) && info != null ? new ArrayList<>(info.participants.participants) : null;

                searchCount = 2;
                if (participantsCopy != null) {
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
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
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
                        ArrayList<TLObject> resultArray2 = new ArrayList<>();

                        for (int a = 0, N = participantsCopy.size(); a < N; a++) {
                            long userId;
                            TLObject o = participantsCopy.get(a);
                            if (o instanceof TLRPC.ChatParticipant) {
                                userId = ((TLRPC.ChatParticipant) o).user_id;
                            } else if (o instanceof TLRPC.ChannelParticipant) {
                                userId = MessageObject.getPeerId(((TLRPC.ChannelParticipant) o).peer);
                            } else {
                                continue;
                            }
                            TLRPC.User user = profileActivity.getMessagesController().getUser(userId);
                            if (user.id == profileActivity.getUserConfig().getClientUserId()) {
                                continue;
                            }

                            String name = UserObject.getUserName(user).toLowerCase();
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (user.username != null && user.username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q));
                                    }
                                    resultArray2.add(o);
                                    break;
                                }
                            }
                        }
                        updateSearchResults(resultArrayNames, resultArray2);
                    });
                } else {
                    searchCount--;
                }
                searchAdapterHelper.queryServerSearch(query, false, false, true, false, false, ChatObject.isChannel(currentChat) ? currentChat.id : 0, false, 2, 1);
            });
        }

        private void updateSearchResults(final ArrayList<CharSequence> names, final ArrayList<TLObject> participants) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchResultNames = names;
                searchCount--;
                if (!ChatObject.isChannel(currentChat)) {
                    ArrayList<TLObject> search = searchAdapterHelper.getGroupSearch();
                    search.clear();
                    search.addAll(participants);
                }

                if (searchCount == 0) {
                    for (int a = 0; a < mediaPages.length; a++) {
                        if (mediaPages[a].selectedType == 7) {
                            if (getItemCount() == 0) {
                                mediaPages[a].emptyView.showProgress(false, true);
                            } else {
                                animateItemsEnter(mediaPages[a].listView, 0);
                            }
                        }
                    }
                }

                notifyDataSetChanged();
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public int getItemCount() {
            return totalCount;
        }

        @Override
        public void notifyDataSetChanged() {
            totalCount = searchAdapterHelper.getGroupSearch().size();
            if (totalCount > 0 && searching && mediaPages[0].selectedType == 7 && mediaPages[0].listView.getAdapter() != this) {
                switchToCurrentSelectedMode(false);
            }
            super.notifyDataSetChanged();
        }

        public void removeUserId(long userId) {
            searchAdapterHelper.removeUserId(userId);
            notifyDataSetChanged();
        }

        public TLObject getItem(int i) {
            int count = searchAdapterHelper.getGroupSearch().size();
            if (i < 0 || i >= count) {
                return null;
            }
            return searchAdapterHelper.getGroupSearch().get(i);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ManageChatUserCell view = new ManageChatUserCell(mContext, 9, 5, true);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            view.setDelegate((cell, click) -> {
                TLObject object = getItem((Integer) cell.getTag());
                if (object instanceof TLRPC.ChannelParticipant) {
                    TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) object;
                    return createMenuForParticipant(participant, !click);
                } else {
                    return false;
                }
            });
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TLObject object = getItem(position);
            TLRPC.User user;
            if (object instanceof TLRPC.ChannelParticipant) {
                user = profileActivity.getMessagesController().getUser(MessageObject.getPeerId(((TLRPC.ChannelParticipant) object).peer));
            } else if (object instanceof TLRPC.ChatParticipant) {
                user = profileActivity.getMessagesController().getUser(((TLRPC.ChatParticipant) object).user_id);
            } else {
                return;
            }

            String un = user.username;
            SpannableStringBuilder name = null;

            int count = searchAdapterHelper.getGroupSearch().size();
            String nameSearch = searchAdapterHelper.getLastFoundChannel();

            if (nameSearch != null) {
                String u = UserObject.getUserName(user);
                name = new SpannableStringBuilder(u);
                int idx = AndroidUtilities.indexOfIgnoreCase(u, nameSearch);
                if (idx != -1) {
                    name.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), idx, idx + nameSearch.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
            userCell.setTag(position);
            userCell.setData(user, name, null, false);
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(shadowLine, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_divider));

        arrayList.add(new ThemeDescription(deleteItem.getIconView(), ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(deleteItem, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        if (gotoItem != null) {
            arrayList.add(new ThemeDescription(gotoItem.getIconView(), ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
            arrayList.add(new ThemeDescription(gotoItem, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        }
        if (forwardItem != null) {
            arrayList.add(new ThemeDescription(forwardItem.getIconView(), ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
            arrayList.add(new ThemeDescription(forwardItem, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        }
        arrayList.add(new ThemeDescription(closeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, new Drawable[]{backDrawable}, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(closeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));

        arrayList.add(new ThemeDescription(actionModeLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(floatingDateView, 0, null, null, null, null, Theme.key_chat_mediaTimeBackground));
        arrayList.add(new ThemeDescription(floatingDateView, 0, null, null, null, null, Theme.key_chat_mediaTimeText));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip, 0, new Class[]{ScrollSlidingTextTabStrip.class}, new String[]{"selectorDrawable"}, null, null, null, Theme.key_profile_tabSelectedLine));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_profile_tabSelectedText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_profile_tabText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_profile_tabSelector));

        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_FASTSCROLL, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerPerformer));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose));

        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText));

        for (int a = 0; a < mediaPages.length; a++) {
            final int num = a;
            ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
                if (mediaPages[num].listView != null) {
                    int count = mediaPages[num].listView.getChildCount();
                    for (int a1 = 0; a1 < count; a1++) {
                        View child = mediaPages[num].listView.getChildAt(a1);
                        if (child instanceof SharedPhotoVideoCell) {
                            ((SharedPhotoVideoCell) child).updateCheckboxColor();
                        } else if (child instanceof ProfileSearchCell) {
                            ((ProfileSearchCell) child).update(0);
                        } else if (child instanceof UserCell) {
                            ((UserCell) child).update(0);
                        }
                    }
                }
            };

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

            arrayList.add(new ThemeDescription(mediaPages[a].progressView, 0, null, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
            arrayList.add(new ThemeDescription(mediaPages[a].emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{UserCell.class}, new String[]{"adminTextView"}, null, null, null, Theme.key_profile_creatorIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{UserCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{UserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{ProfileSearchCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EmptyStubView.class}, new String[]{"emptyTextView"}, null,null, null, Theme.key_windowBackgroundWhiteGrayText2));
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

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{ContextLinkCell.class}, new String[]{"backgroundPaint"}, null, null, null, Theme.key_sharedMedia_photoPlaceholder));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{ContextLinkCell.class}, null, null, cellDelegate, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{ContextLinkCell.class}, null, null, cellDelegate, Theme.key_checkboxCheck));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, null, null, new Drawable[]{pinnedHeaderShadowDrawable}, null, Theme.key_windowBackgroundGrayShadow));

            arrayList.add(new ThemeDescription(mediaPages[a].emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        }

        return arrayList;
    }
}
