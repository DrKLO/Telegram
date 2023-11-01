package org.telegram.ui.Components;

import static org.telegram.messenger.MediaDataController.MEDIA_PHOTOVIDEO;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.CalendarActivity;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell2;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.UserListPoller;
import org.telegram.ui.Stories.ViewsForPeerStoriesRequester;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

@SuppressWarnings("unchecked")
public class SharedMediaLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int TAB_PHOTOVIDEO = 0;
    public static final int TAB_FILES = 1;
    public static final int TAB_VOICE = 2;
    public static final int TAB_LINKS = 3;
    public static final int TAB_AUDIO = 4;
    public static final int TAB_GIF = 5;
    public static final int TAB_COMMON_GROUPS = 6;
    public static final int TAB_GROUPUSERS = 7;
    public static final int TAB_STORIES = 8;
    public static final int TAB_ARCHIVED_STORIES = 9;

    public static final int FILTER_PHOTOS_AND_VIDEOS = 0;
    public static final int FILTER_PHOTOS_ONLY = 1;
    public static final int FILTER_VIDEOS_ONLY = 2;

    public static final int VIEW_TYPE_MEDIA_ACTIVITY = 0;
    public static final int VIEW_TYPE_PROFILE_ACTIVITY = 1;

    private static final int[] supportedFastScrollTypes = new int[] {
            MediaDataController.MEDIA_PHOTOVIDEO,
            MediaDataController.MEDIA_FILE,
            MediaDataController.MEDIA_AUDIO,
            MediaDataController.MEDIA_MUSIC
    };

    public boolean isInFastScroll() {
        return mediaPages[0] != null && mediaPages[0].listView.getFastScroll() != null && mediaPages[0].listView.getFastScroll().isPressed();
    }

    public boolean dispatchFastScrollEvent(MotionEvent ev) {
        View view = (View) getParent();
        ev.offsetLocation(-view.getX() - getX() - mediaPages[0].listView.getFastScroll().getX(), -view.getY() - getY() - mediaPages[0].getY() - mediaPages[0].listView.getFastScroll().getY());
        return mediaPages[0].listView.getFastScroll().dispatchTouchEvent(ev);
    }

    boolean isInPinchToZoomTouchMode;
    boolean maybePinchToZoomTouchMode;
    boolean maybePinchToZoomTouchMode2;
    boolean isPinnedToTop;

    private int pointerId1, pointerId2;

    float pinchStartDistance;
    float pinchScale;
    boolean pinchScaleUp;
    int pinchCenterPosition;
    int pinchCenterOffset;
    int pinchCenterX;
    int pinchCenterY;
    Rect rect = new Rect();
    ActionBarPopupWindow optionsWindow;
    FlickerLoadingView globalGradientView;
    private final int viewType;
    private int topicId;

    private UndoView undoView;

    public boolean checkPinchToZoom(MotionEvent ev) {
        final int selectedType = mediaPages[0].selectedType;
        if (selectedType != TAB_PHOTOVIDEO && selectedType != TAB_STORIES && selectedType != TAB_ARCHIVED_STORIES || getParent() == null) {
            return false;
        }
        if (photoVideoChangeColumnsAnimation && !isInPinchToZoomTouchMode) {
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (maybePinchToZoomTouchMode && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2 /*&& finishZoomTransition == null*/) {
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));

                pinchScale = 1f;

                pointerId1 = ev.getPointerId(0);
                pointerId2 = ev.getPointerId(1);

                mediaPages[0].listView.cancelClickRunnables(false);
                mediaPages[0].listView.cancelLongPress();
                mediaPages[0].listView.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));

                View view = (View) getParent();
                pinchCenterX = (int) ((int) ((ev.getX(0) + ev.getX(1)) / 2.0f) - view.getX() - getX() - mediaPages[0].getX());
                pinchCenterY = (int) ((int) ((ev.getY(0) + ev.getY(1)) / 2.0f) - view.getY() - getY() - mediaPages[0].getY());

                selectPinchPosition(pinchCenterX, pinchCenterY);
                maybePinchToZoomTouchMode2 = true;
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                View view = (View) getParent();
               // float x = ev.getX() - view.getX() - getX() - mediaPages[0].getX();
                float y = ev.getY() - view.getY() - getY() - mediaPages[0].getY();
                if (y > 0) {
                    maybePinchToZoomTouchMode = true;
                }
            }

        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && (isInPinchToZoomTouchMode || maybePinchToZoomTouchMode2)) {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (pointerId1 == ev.getPointerId(i)) {
                    index1 = i;
                }
                if (pointerId2 == ev.getPointerId(i)) {
                    index2 = i;
                }
            }
            if (index1 == -1 || index2 == -1) {
                maybePinchToZoomTouchMode = false;
                maybePinchToZoomTouchMode2 = false;
                isInPinchToZoomTouchMode = false;
                finishPinchToMediaColumnsCount();
                return false;
            }
            pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
            if (!isInPinchToZoomTouchMode && (pinchScale > 1.01f || pinchScale < 0.99f)) {
                isInPinchToZoomTouchMode = true;
                pinchScaleUp = pinchScale > 1f;

                startPinchToMediaColumnsCount(pinchScaleUp);
            }
            if (isInPinchToZoomTouchMode) {
                if ((pinchScaleUp && pinchScale < 1f) || (!pinchScaleUp && pinchScale > 1f)) {
                    photoVideoChangeColumnsProgress = 0;
                } else {
                    photoVideoChangeColumnsProgress = Math.max(0, Math.min(1, pinchScaleUp ? (1f - (2f - pinchScale) / 1f) : ((1f - pinchScale) / 0.5f)));
                }
                if (photoVideoChangeColumnsProgress == 1f || photoVideoChangeColumnsProgress == 0f) {

                    final RecyclerView.Adapter adapter;
                    if (changeColumnsTab == TAB_STORIES) {
                        adapter = storiesAdapter;
                    } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                        adapter = archivedStoriesAdapter;
                    } else {
                        adapter = photoVideoAdapter;
                    }
                    if (photoVideoChangeColumnsProgress == 1f) {
                        int newRow = (int) Math.ceil(pinchCenterPosition / (float) animateToColumnsCount);
                        int columnWidth = (int) (mediaPages[0].listView.getMeasuredWidth() / (float) animateToColumnsCount);
                        int newColumn = (int) ((startedTrackingX / (float) (mediaPages[0].listView.getMeasuredWidth() - columnWidth)) * (animateToColumnsCount - 1));
                        int newPosition = newRow * animateToColumnsCount + newColumn;
                        if (newPosition >= adapter.getItemCount()) {
                            newPosition = adapter.getItemCount() - 1;
                        }
                        pinchCenterPosition = newPosition;
                    }

                    finishPinchToMediaColumnsCount();
                    if (photoVideoChangeColumnsProgress == 0) {
                        pinchScaleUp = !pinchScaleUp;
                    }

                    startPinchToMediaColumnsCount(pinchScaleUp);
                    pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                }

                mediaPages[0].listView.invalidate();
                if (mediaPages[0].fastScrollHintView != null) {
                    mediaPages[0].invalidate();
                }
            }
        } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) && isInPinchToZoomTouchMode) {
            maybePinchToZoomTouchMode2 = false;
            maybePinchToZoomTouchMode = false;
            isInPinchToZoomTouchMode = false;
            finishPinchToMediaColumnsCount();
        }

        return isInPinchToZoomTouchMode;
    }

    private void selectPinchPosition(int pinchCenterX, int pinchCenterY) {
        pinchCenterPosition = -1;
        int y = pinchCenterY + mediaPages[0].listView.blurTopPadding;
        if (getY() != 0 && viewType == VIEW_TYPE_PROFILE_ACTIVITY) {
            y = 0;
        }
        for (int i = 0; i < mediaPages[0].listView.getChildCount(); i++) {
            View child = mediaPages[0].listView.getChildAt(i);
            child.getHitRect(rect);
            if (rect.contains(pinchCenterX, y)) {
                pinchCenterPosition = mediaPages[0].listView.getChildLayoutPosition(child);
                pinchCenterOffset = child.getTop();
            }
        }
        if (delegate.canSearchMembers()) {
            if (pinchCenterPosition == -1) {
                float x = Math.min(1, Math.max(pinchCenterX / (float) mediaPages[0].listView.getMeasuredWidth(), 0));
                final int ci = mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
                pinchCenterPosition = (int) (mediaPages[0].layoutManager.findFirstVisibleItemPosition() + (mediaColumnsCount[ci] - 1) * x);
                pinchCenterOffset = 0;
            }
        }
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    public boolean isSwipeBackEnabled() {
        return !photoVideoChangeColumnsAnimation && !tabsAnimationInProgress;
    }

    public int getPhotosVideosTypeFilter() {
        return sharedMediaData[0].filterType;
    }

    public boolean isPinnedToTop() {
        return isPinnedToTop;
    }

    public void setPinnedToTop(boolean pinnedToTop) {
        if (isPinnedToTop != pinnedToTop) {
            isPinnedToTop = pinnedToTop;
            for (int i = 0; i < mediaPages.length; i++) {
                updateFastScrollVisibility(mediaPages[i], true);
            }
        }
    }

    public void drawListForBlur(Canvas blurCanvas) {
        for (int i = 0; i < mediaPages.length; i++) {
            if (mediaPages[i] != null && mediaPages[i].getVisibility() == View.VISIBLE) {
                for (int j = 0; j < mediaPages[i].listView.getChildCount(); j++) {
                    View child = mediaPages[i].listView.getChildAt(j);
                    if (child.getY() < mediaPages[i].listView.blurTopPadding + AndroidUtilities.dp(100)) {
                        int restore = blurCanvas.save();
                        blurCanvas.translate(mediaPages[i].getX() + child.getX(), getY() + mediaPages[i].getY() + mediaPages[i].listView.getY() + child.getY());
                        child.draw(blurCanvas);
                        blurCanvas.restoreToCount(restore);
                    }
                }
            }
        }
    }

    private static class MediaPage extends FrameLayout {
        public long lastCheckScrollTime;
        public boolean fastScrollEnabled;
        public ObjectAnimator fastScrollAnimator;
        private InternalListView listView;
        private InternalListView animationSupportingListView;
        private GridLayoutManager animationSupportingLayoutManager;
        private FlickerLoadingView progressView;
        private StickerEmptyView emptyView;
        private ExtendedGridLayoutManager layoutManager;
        private ClippingImageView animatingImageView;
        private RecyclerAnimationScrollHelper scrollHelper;
        private int selectedType;

        public SharedMediaFastScrollTooltip fastScrollHintView;
        public Runnable fastScrollHideHintRunnable;
        public boolean fastScrollHinWasShown;

        public int highlightMessageId;
        public boolean highlightAnimation;
        public float highlightProgress;


        public MediaPage(Context context) {
            super(context);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == animationSupportingListView) {
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (fastScrollHintView != null && fastScrollHintView.getVisibility() == View.VISIBLE) {
                boolean isVisible = false;
                RecyclerListView.FastScroll fastScroll = listView.getFastScroll();
                if (fastScroll != null) {
                    float y = fastScroll.getScrollBarY() + AndroidUtilities.dp(36);
                    if (selectedType == TAB_ARCHIVED_STORIES) {
                        y += AndroidUtilities.dp(64);
                    }
                    float x = (getMeasuredWidth() - fastScrollHintView.getMeasuredWidth() - AndroidUtilities.dp(16));
                    fastScrollHintView.setPivotX(fastScrollHintView.getMeasuredWidth());
                    fastScrollHintView.setPivotY(0);
                    fastScrollHintView.setTranslationX(x);
                    fastScrollHintView.setTranslationY(y);
                }

                if (fastScroll.getProgress() > 0.85f) {
                    showFastScrollHint(this, null, false);
                }
            }
        }

    }

    public float getPhotoVideoOptionsAlpha(float progress) {
        if (isArchivedOnlyStoriesView()) {
            return 0;
        }
        float alpha = 0;
        if (mediaPages[1] != null && (mediaPages[1].selectedType == TAB_PHOTOVIDEO || mediaPages[1].selectedType == TAB_STORIES || mediaPages[1].selectedType == TAB_ARCHIVED_STORIES))
            alpha += progress;
        if (mediaPages[0] != null && (mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES))
            alpha += 1f - progress;
        return alpha;
    }

    public void updateFastScrollVisibility(MediaPage mediaPage, boolean animated) {
        boolean show = mediaPage.fastScrollEnabled && isPinnedToTop;
        View view = mediaPage.listView.getFastScroll();
        if (mediaPage.fastScrollAnimator != null) {
            mediaPage.fastScrollAnimator.removeAllListeners();
            mediaPage.fastScrollAnimator.cancel();
        }
        if (!animated) {
            view.animate().setListener(null).cancel();
            view.setVisibility(show ? View.VISIBLE : View.GONE);
            view.setTag(show ? 1 : null);
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        } else if (show && view.getTag() == null) {
            view.animate().setListener(null).cancel();
            if (view.getVisibility() != View.VISIBLE) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
            }
            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 1f);
            mediaPage.fastScrollAnimator = objectAnimator;
            objectAnimator.setDuration(150).start();
            view.setTag(1);
        } else if (!show && view.getTag() != null) {

            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f);
            objectAnimator.addListener(new HideViewAfterAnimation(view));
            mediaPage.fastScrollAnimator = objectAnimator;
            objectAnimator.setDuration(150).start();
            view.animate().setListener(null).cancel();

            view.setTag(null);
        }
    }

    private ActionBar actionBar;

    private SharedPhotoVideoAdapter photoVideoAdapter;
    private SharedPhotoVideoAdapter animationSupportingPhotoVideoAdapter;
    private SharedLinksAdapter linksAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private SharedDocumentsAdapter voiceAdapter;
    private SharedDocumentsAdapter audioAdapter;
    private GifAdapter gifAdapter;
    private CommonGroupsAdapter commonGroupsAdapter;
    private ChatUsersAdapter chatUsersAdapter;
    private StoriesAdapter storiesAdapter;
    private StoriesAdapter animationSupportingStoriesAdapter;
    private StoriesAdapter archivedStoriesAdapter;
    private StoriesAdapter animationSupportingArchivedStoriesAdapter;
    private MediaSearchAdapter documentsSearchAdapter;
    private MediaSearchAdapter audioSearchAdapter;
    private MediaSearchAdapter linksSearchAdapter;
    private GroupUsersSearchAdapter groupUsersSearchAdapter;
    private MediaPage[] mediaPages = new MediaPage[2];
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem searchItem;
    public ImageView photoVideoOptionsItem;
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
    private ScrollSlidingTextTabStripInner scrollSlidingTextTabStrip;
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
    private TLRPC.UserFull userInfo;

    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;

    private long dialog_id;
    public boolean scrollingByUser;
    private boolean allowStoriesSingleColumn = false;
    private boolean storiesColumnsCountSet = false;
    private int mediaColumnsCount[] = new int[] { 3, 3 };
    private float photoVideoChangeColumnsProgress;
    private boolean photoVideoChangeColumnsAnimation;
    private int changeColumnsTab;
    private int animationSupportingSortedCellsOffset;
    private ArrayList<SharedPhotoVideoCell2> animationSupportingSortedCells = new ArrayList<>();
    private int animateToColumnsCount;

    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    public interface SharedMediaPreloaderDelegate {
        void mediaCountUpdated();
    }

    public static class SharedMediaPreloader implements NotificationCenter.NotificationCenterDelegate {

        private int[] mediaCount = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        private int[] mediaMergeCount = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        private int[] lastMediaCount = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        private int[] lastLoadMediaCount = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        private SharedMediaData[] sharedMediaData;
        private long dialogId;
        private int topicId;
        private long mergeDialogId;
        private BaseFragment parentFragment;
        private ArrayList<SharedMediaPreloaderDelegate> delegates = new ArrayList<>();
        private boolean mediaWasLoaded;

        public SharedMediaPreloader(BaseFragment fragment) {
            parentFragment = fragment;
            if (fragment instanceof ChatActivityInterface) {
                ChatActivityInterface chatActivity = (ChatActivityInterface) fragment;
                dialogId = chatActivity.getDialogId();
                mergeDialogId = chatActivity.getMergeDialogId();
                topicId = chatActivity.getTopicId();
            } else if (fragment instanceof ProfileActivity) {
                ProfileActivity profileActivity = (ProfileActivity) fragment;
                dialogId = profileActivity.getDialogId();
                topicId = profileActivity.getTopicId();
            } else if (fragment instanceof MediaActivity) {
                MediaActivity mediaActivity = (MediaActivity) fragment;
                dialogId = mediaActivity.getDialogId();
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
            notificationCenter.addObserver(this, NotificationCenter.fileLoaded);
            notificationCenter.addObserver(this, NotificationCenter.storiesListUpdated);
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
            notificationCenter.removeObserver(this, NotificationCenter.fileLoaded);
            notificationCenter.removeObserver(this, NotificationCenter.storiesListUpdated);
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
                int topicId = (int) args[1];
                if (this.topicId == topicId && (did == dialogId || did == mergeDialogId)) {
                    int[] counts = (int[]) args[2];
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
                            int type = a;
                            if (type == 0) {
                                if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
                                    type = MediaDataController.MEDIA_PHOTOS_ONLY;
                                } else if (sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY) {
                                    type = MediaDataController.MEDIA_VIDEOS_ONLY;
                                }
                            }
                            parentFragment.getMediaDataController().loadMedia(did, lastLoadMediaCount[a] == -1 ? 30 : 20, 0, 0, type, topicId,2, parentFragment.getClassGuid(), 0);
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
                long topicId = (Integer) args[1];
                if ((did == dialogId || did == mergeDialogId) && this.topicId == topicId) {
                    int type = (Integer) args[4];
                    int mCount = (Integer) args[2];
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
                        if (topicId != 0 && topicId != MessageObject.getTopicId(obj.messageOwner, true)) {
                            continue;
                        }
                        if (MessageObject.getMedia(obj.messageOwner) == null || obj.needDrawBluredPreview()) {
                            continue;
                        }
                        int type = MediaDataController.getMediaType(obj.messageOwner);
                        if (type == -1) {
                            continue;
                        }
                        if (type == 0 && sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY && !obj.isVideo()) {
                            continue;
                        }
                        if (type == 0 && sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY && obj.isVideo()) {
                            continue;
                        }
                        if (sharedMediaData[type].startReached) {
                            sharedMediaData[type].addMessage(obj, 0, true, enc);
                        }
                        if (topicId == 0) {
                            sharedMediaData[type].totalCount++;
                        }
                        for (int i = 0; i < sharedMediaData[type].fastScrollPeriods.size(); i++) {
                            sharedMediaData[type].fastScrollPeriods.get(i).startOffset++;
                        }
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
                    if (type != 0 && type != 6 && type != 7 && type != 1 && type != 2 && type != 4) {
                        sharedMediaData[type].setTotalCount((Integer) args[1]);
                    }
                    ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                    boolean enc = DialogObject.isEncryptedDialog(did);
                    int loadIndex = did == dialogId ? 0 : 1;
                    if (type == 0 || type == 6 || type == 7) {
                        if (type != sharedMediaData[0].filterType) {
                            return;
                        }
                        type = 0;
                    }
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
                int type;
                ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
                for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                    for (int b = 0; b < sharedMediaData.length; b++) {
                        MessageObject messageObject = sharedMediaData[b].deleteMessage(markAsDeletedMessages.get(a), 0);
                        if (messageObject != null) {
                            if (messageObject.getDialogId() == dialogId && (topicId == 0 || MessageObject.getTopicId(messageObject.messageOwner, true) == topicId)) {
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
                    int topicId = MessageObject.getTopicId(messageObject.messageOwner, true);
                    int type = MediaDataController.getMediaType(messageObject.messageOwner);
                    if (this.topicId != 0 && topicId != this.topicId) {
                        continue;
                    }
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
            } else if (id == NotificationCenter.fileLoaded) {
                ArrayList<MessageObject> allMessages = new ArrayList<>();
                for (int i = 0 ; i < sharedMediaData.length; i++) {
                    allMessages.addAll(sharedMediaData[i].messages);
                }
                String fileName = (String) args[0];
                if (fileName != null) {
                    Utilities.globalQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < allMessages.size(); i++) {
                                if (!fileName.equals(allMessages.get(i).getFileName())) {
                                    allMessages.remove(i);
                                    i--;
                                }
                            }
                            if (allMessages.size() > 0) {
                                FileLoader.getInstance(account).checkMediaExistance(allMessages);
                            }
                        }
                    });
                }
            }
        }

        private void loadMediaCounts() {
            parentFragment.getMediaDataController().getMediaCounts(dialogId, topicId, parentFragment.getClassGuid());
            if (mergeDialogId != 0) {
                parentFragment.getMediaDataController().getMediaCounts(mergeDialogId, topicId, parentFragment.getClassGuid());
            }
        }

        private void setChatInfo(TLRPC.ChatFull chatInfo) {
            if (chatInfo != null && chatInfo.migrated_from_chat_id != 0 && mergeDialogId == 0) {
                mergeDialogId = -chatInfo.migrated_from_chat_id;
                parentFragment.getMediaDataController().getMediaCounts(mergeDialogId, topicId, parentFragment.getClassGuid());
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
                if (view instanceof SharedPhotoVideoCell2) {
                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                    MessageObject message = cell.getMessageObject();
                    if (message == null) {
                        continue;
                    }
                    if (message.getId() == messageObject.getId()) {
                        imageReceiver = cell.imageReceiver;
                        cell.getLocationInWindow(coords);
                        coords[0] += Math.round(cell.imageReceiver.getImageX());
                        coords[1] += Math.round(cell.imageReceiver.getImageY());
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
                    object.allowTakeAnimation = true;
                    object.radius = object.imageReceiver.getRoundRadius();
                    object.thumb = object.imageReceiver.getBitmapSafe();
                    object.parentView.getLocationInWindow(coords);
                    object.clipTopAddition = 0;
                    object.starOffset = sharedMediaData[0].startOffset;
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
                    delegate.scrollToSharedMedia();
                } else if (position >= lastVisiblePosition && lastVisiblePosition >= 0) {
                    mediaPages[0].layoutManager.scrollToPositionWithOffset(position, 0, true);
                    delegate.scrollToSharedMedia();
                }
            }

            return null;
        }
    };

    private float shiftDp = -5;

    public static class SharedMediaData {
        public ArrayList<MessageObject> messages = new ArrayList<>();
        public SparseArray<MessageObject>[] messagesDict = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
        public ArrayList<String> sections = new ArrayList<>();
        public HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();
        public ArrayList<Period> fastScrollPeriods = new ArrayList<>();
        public int totalCount;
        public boolean loading;
        public boolean fastScrollDataLoaded;
        public boolean[] endReached = new boolean[]{false, true};
        public int[] max_id = new int[]{0, 0};
        public int min_id;
        public boolean startReached = true;
        private int startOffset;
        private int endLoadingStubs;
        public boolean loadingAfterFastScroll;
        public int requestIndex;

        public int filterType = FILTER_PHOTOS_AND_VIDEOS;
        public boolean isFrozen;
        public ArrayList<MessageObject> frozenMessages = new ArrayList<>();
        public int frozenStartOffset;
        public int frozenEndLoadingStubs;
        private boolean hasVideos;
        private boolean hasPhotos;

        RecyclerView.RecycledViewPool recycledViewPool = new RecyclerView.RecycledViewPool();

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
                    min_id = Math.max(messageObject.getId(), min_id);
                }
            } else {
                max_id[loadIndex] = Math.max(messageObject.getId(), max_id[loadIndex]);
                min_id = Math.min(messageObject.getId(), min_id);
            }
            if (!hasVideos && messageObject.isVideo()) {
                hasVideos = true;
            }
            if (!hasPhotos && messageObject.isPhoto()) {
                hasPhotos = true;
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
            if (totalCount < 0) {
                totalCount = 0;
            }
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

        public ArrayList<MessageObject> getMessages() {
            return isFrozen ? frozenMessages : messages;
        }

        public int getStartOffset() {
            return isFrozen ? frozenStartOffset : startOffset;
        }

        public void setListFrozen(boolean frozen) {
            if (isFrozen == frozen) {
                return;
            }
            isFrozen = frozen;
            if (frozen) {
                frozenStartOffset = startOffset;
                frozenEndLoadingStubs = endLoadingStubs;
                frozenMessages.clear();
                frozenMessages.addAll(messages);
            }
        }

        public int getEndLoadingStubs() {
            return isFrozen ? frozenEndLoadingStubs : endLoadingStubs;
        }
    }

    public static class Period {
        public String formatedDate;
        public int startOffset;
        int date;
        //int messagesCount;
        int maxId;

        public Period(TLRPC.TL_searchResultPosition calendarPeriod) {
            this.date = calendarPeriod.date;
            this.maxId = calendarPeriod.msg_id;
            this.startOffset = calendarPeriod.offset;
            formatedDate = LocaleController.formatYearMont(this.date, true);
        }
    }

    private SharedMediaData[] sharedMediaData = new SharedMediaData[6];
    private SharedMediaPreloader sharedMediaPreloader;

    private final static int forward = 100;
    private final static int delete = 101;
    private final static int gotochat = 102;

    private BaseFragment profileActivity;

    private int startedTrackingPointerId;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;

    protected boolean isActionModeShowed;

    final Delegate delegate;
    private HintView fwdRestrictedHint;
    private Theme.ResourcesProvider resourcesProvider;

    public boolean hasInternet() {
        return profileActivity.getConnectionsManager().getConnectionState() == ConnectionsManager.ConnectionStateConnected;
    }

    public SharedMediaLayout(Context context, long did, SharedMediaPreloader preloader, int commonGroupsCount, ArrayList<Integer> sortedUsers, TLRPC.ChatFull chatInfo, TLRPC.UserFull userInfo, boolean membersFirst, BaseFragment parent, Delegate delegate, int viewType, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.viewType = viewType;
        this.resourcesProvider = resourcesProvider;

        globalGradientView = new FlickerLoadingView(context);
        globalGradientView.setIsSingleCell(true);

        sharedMediaPreloader = preloader;
        this.delegate = delegate;
        int[] mediaCount = preloader.getLastMediaCount();
        topicId = sharedMediaPreloader.topicId;
        hasMedia = new int[]{mediaCount[0], mediaCount[1], mediaCount[2], mediaCount[3], mediaCount[4], mediaCount[5], topicId == 0 ? commonGroupsCount : 0};
        if (userInfo != null && userInfo.stories_pinned_available || chatInfo != null && chatInfo.stories_pinned_available || isStoriesView()) {
            initialTab = getInitialTab();
        } else if (membersFirst && topicId == 0) {
            initialTab = TAB_GROUPUSERS;
        } else {
            for (int a = 0; a < hasMedia.length; a++) {
                if (hasMedia[a] == -1 || hasMedia[a] > 0) {
                    initialTab = a;
                    break;
                }
            }
        }
        info = chatInfo;
        this.userInfo = userInfo;
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
        mediaColumnsCount[0] = SharedConfig.mediaColumnsCount;
        mediaColumnsCount[1] = SharedConfig.storiesColumnsCount;

        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.mediaDidLoad);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.storiesListUpdated);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.storiesUpdated);

        for (int a = 0; a < 10; a++) {
            //cellCache.add(new SharedPhotoVideoCell(context));
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
        pinnedHeaderShadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundGrayShadow), PorterDuff.Mode.MULTIPLY));

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
        menu.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                if (searchItem == null) {
                    return;
                }
                View parent = (View) searchItem.getParent();
                searchItem.setTranslationX(parent.getMeasuredWidth() - searchItem.getRight());
            }
        });
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
                if (mediaPages[0].selectedType == TAB_FILES) {
                    if (documentsSearchAdapter == null) {
                        return;
                    }
                    documentsSearchAdapter.search(text, true);
                } else if (mediaPages[0].selectedType == TAB_LINKS) {
                    if (linksSearchAdapter == null) {
                        return;
                    }
                    linksSearchAdapter.search(text, true);
                } else if (mediaPages[0].selectedType == TAB_AUDIO) {
                    if (audioSearchAdapter == null) {
                        return;
                    }
                    audioSearchAdapter.search(text, true);
                } else if (mediaPages[0].selectedType == TAB_GROUPUSERS) {
                    if (groupUsersSearchAdapter == null) {
                        return;
                    }
                    groupUsersSearchAdapter.search(text, true);
                }
            }

            @Override
            public void onLayout(int l, int t, int r, int b) {
                View parent = (View) searchItem.getParent();
                searchItem.setTranslationX(parent.getMeasuredWidth() - searchItem.getRight());
            }
        });
        searchItem.setTranslationY(AndroidUtilities.dp(10));
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);

        photoVideoOptionsItem = new ImageView(context);
        photoVideoOptionsItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        photoVideoOptionsItem.setTranslationY(AndroidUtilities.dp(10));
        photoVideoOptionsItem.setVisibility(View.INVISIBLE);

        Drawable calendarDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ab_other).mutate();
        calendarDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
        photoVideoOptionsItem.setImageDrawable(calendarDrawable);
        photoVideoOptionsItem.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (!isArchivedOnlyStoriesView()) {
            actionBar.addView(photoVideoOptionsItem, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM));
        }
        photoVideoOptionsItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                View dividerView = new DividerCell(context);
                ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        if (dividerView.getParent() != null) {
                            dividerView.setVisibility(View.GONE);
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            dividerView.getLayoutParams().width = getMeasuredWidth() - AndroidUtilities.dp(16);
                            dividerView.setVisibility(View.VISIBLE);
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        } else {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        }
                    }
                };

                int tab = getClosestTab();
                boolean isStories = tab == TAB_STORIES || tab == TAB_ARCHIVED_STORIES;

                mediaZoomInItem = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
                mediaZoomOutItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);

                mediaZoomInItem.setTextAndIcon(LocaleController.getString("MediaZoomIn", R.string.MediaZoomIn), R.drawable.msg_zoomin);
                mediaZoomInItem.setOnClickListener(view1 -> zoomIn());
                popupLayout.addView(mediaZoomInItem);

                mediaZoomOutItem.setTextAndIcon(LocaleController.getString("MediaZoomOut", R.string.MediaZoomOut), R.drawable.msg_zoomout);
                mediaZoomOutItem.setOnClickListener(view1 -> zoomOut());
                popupLayout.addView(mediaZoomOutItem);

                if (isStories && allowStoriesSingleColumn) {
                    mediaZoomInItem.setEnabled(false);
                    mediaZoomInItem.setAlpha(0.5f);
                    mediaZoomOutItem.setEnabled(false);
                    mediaZoomOutItem.setAlpha(0.5f);
                } else if (mediaColumnsCount[isStories ? 1 : 0] == 2) {
                    mediaZoomInItem.setEnabled(false);
                    mediaZoomInItem.setAlpha(0.5f);
                } else if (mediaColumnsCount[isStories ? 1 : 0] == 9) {
                    mediaZoomOutItem.setEnabled(false);
                    mediaZoomOutItem.setAlpha(0.5f);
                }

                boolean hasDifferentTypes = isStories || (sharedMediaData[0].hasPhotos && sharedMediaData[0].hasVideos) || !sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1] || !sharedMediaData[0].startReached;
                if (!DialogObject.isEncryptedDialog(dialog_id)) {
                    ActionBarMenuSubItem calendarItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
                    calendarItem.setTextAndIcon(LocaleController.getString("Calendar", R.string.Calendar), R.drawable.msg_calendar2);
                    popupLayout.addView(calendarItem);
                    calendarItem.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            showMediaCalendar(tab, false);
                            if (optionsWindow != null) {
                                optionsWindow.dismiss();
                            }
                        }
                    });

                    if (info != null && !isStoriesView()) {
                        TLRPC.Chat chat = MessagesController.getInstance(profileActivity.getCurrentAccount()).getChat(info.id);
                        if (chat != null && chat.admin_rights != null && chat.admin_rights.edit_stories) {
                            ActionBarMenuSubItem openArchiveItem = new ActionBarMenuSubItem(context, false, true, resourcesProvider);
                            openArchiveItem.setTextAndIcon(LocaleController.getString(R.string.OpenChannelArchiveStories), R.drawable.msg_archive);
                            openArchiveItem.setOnClickListener(e -> {
                                Bundle args = new Bundle();
                                args.putInt("type", MediaActivity.TYPE_ARCHIVED_CHANNEL_STORIES);
                                args.putLong("dialog_id", -info.id);
                                MediaActivity fragment = new MediaActivity(args, null);
                                fragment.setChatInfo(info);
                                profileActivity.presentFragment(fragment);

                                if (optionsWindow != null) {
                                    optionsWindow.dismiss();
                                }
                            });

                            popupLayout.addView(openArchiveItem);
                        }
                    }

                    if (hasDifferentTypes) {
                        popupLayout.addView(dividerView);
                        ActionBarMenuSubItem showPhotosItem = new ActionBarMenuSubItem(context, true, false, false, resourcesProvider);
                        ActionBarMenuSubItem showVideosItem = new ActionBarMenuSubItem(context, true, false, true, resourcesProvider);

                        showPhotosItem.setTextAndIcon(LocaleController.getString("MediaShowPhotos", R.string.MediaShowPhotos), 0);
                        popupLayout.addView(showPhotosItem);

                        showVideosItem.setTextAndIcon(LocaleController.getString("MediaShowVideos", R.string.MediaShowVideos), 0);
                        popupLayout.addView(showVideosItem);

                        if (isStories) {
                            StoriesAdapter adapter = tab == TAB_STORIES ? storiesAdapter : archivedStoriesAdapter;
                            if (adapter.storiesList != null) {
                                showPhotosItem.setChecked(adapter.storiesList.showPhotos());
                                showVideosItem.setChecked(adapter.storiesList.showVideos());
                            }
                            showPhotosItem.setOnClickListener(v -> {
                                if (changeTypeAnimation) {
                                    return;
                                }
                                if (!showVideosItem.getCheckView().isChecked() && showPhotosItem.getCheckView().isChecked()) {
                                    AndroidUtilities.shakeViewSpring(v, shiftDp = -shiftDp);
                                    return;
                                }
                                showPhotosItem.getCheckView().setChecked(!showPhotosItem.getCheckView().isChecked(), true);
                                if (adapter.storiesList == null) {
                                    return;
                                }
                                adapter.storiesList.updateFilters(showPhotosItem.getCheckView().isChecked(), showVideosItem.getCheckView().isChecked());
                            });
                            showVideosItem.setOnClickListener(v -> {
                                if (changeTypeAnimation) {
                                    return;
                                }
                                if (!showPhotosItem.getCheckView().isChecked() && showVideosItem.getCheckView().isChecked()) {
                                    AndroidUtilities.shakeViewSpring(v, shiftDp = -shiftDp);
                                    return;
                                }
                                showVideosItem.getCheckView().setChecked(!showVideosItem.getCheckView().isChecked(), true);
                                if (adapter.storiesList == null) {
                                    return;
                                }
                                adapter.storiesList.updateFilters(showPhotosItem.getCheckView().isChecked(), showVideosItem.getCheckView().isChecked());
                            });
                        } else {
                            showPhotosItem.setChecked(sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS || sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY);
                            showPhotosItem.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (changeTypeAnimation) {
                                        return;
                                    }
                                    if (!showVideosItem.getCheckView().isChecked() && showPhotosItem.getCheckView().isChecked()) {
                                        AndroidUtilities.shakeViewSpring(showPhotosItem, shiftDp = -shiftDp);
                                        return;
                                    }
                                    showPhotosItem.setChecked(!showPhotosItem.getCheckView().isChecked());
                                    if (showPhotosItem.getCheckView().isChecked() && showVideosItem.getCheckView().isChecked()) {
                                        sharedMediaData[0].filterType = FILTER_PHOTOS_AND_VIDEOS;
                                    } else {
                                        sharedMediaData[0].filterType = FILTER_VIDEOS_ONLY;
                                    }
                                    changeMediaFilterType();
                                }
                            });
                            showVideosItem.setChecked(sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS || sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY);
                            showVideosItem.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (changeTypeAnimation) {
                                        return;
                                    }
                                    if (!showPhotosItem.getCheckView().isChecked() && showVideosItem.getCheckView().isChecked()) {
                                        AndroidUtilities.shakeViewSpring(showVideosItem, shiftDp = -shiftDp);
                                        return;
                                    }
                                    showVideosItem.setChecked(!showVideosItem.getCheckView().isChecked());
                                    if (showPhotosItem.getCheckView().isChecked() && showVideosItem.getCheckView().isChecked()) {
                                        sharedMediaData[0].filterType = FILTER_PHOTOS_AND_VIDEOS;
                                    } else {
                                        sharedMediaData[0].filterType = FILTER_PHOTOS_ONLY;
                                    }
                                    changeMediaFilterType();
                                }
                            });
                        }
                    }
                }

                optionsWindow = AlertsCreator.showPopupMenu(popupLayout, photoVideoOptionsItem, 0, -AndroidUtilities.dp(56));
            }
        });

        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(getThemedColor(Theme.key_player_time));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        searchItemState = 0;

        SizeNotifierFrameLayout sizeNotifierFrameLayout = null;
        if (profileActivity != null && profileActivity.getFragmentView() instanceof SizeNotifierFrameLayout) {
            sizeNotifierFrameLayout = (SizeNotifierFrameLayout) profileActivity.getFragmentView();
        }
        actionModeLayout = new BlurredLinearLayout(context, sizeNotifierFrameLayout);
        actionModeLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionModeLayout.setAlpha(0.0f);
        actionModeLayout.setClickable(true);
        actionModeLayout.setVisibility(INVISIBLE);

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(backDrawable = new BackDrawable(true));
        backDrawable.setColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        closeButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        closeButton.setContentDescription(LocaleController.getString("Close", R.string.Close));
        actionModeLayout.addView(closeButton, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(closeButton);
        closeButton.setOnClickListener(v -> closeActionMode());

        selectedMessagesCountTextView = new NumberTextView(context);
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        actionModeLayout.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 18, 0, 0, 0));
        actionModeViews.add(selectedMessagesCountTextView);

        if (!DialogObject.isEncryptedDialog(dialog_id)) {
            gotoItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            gotoItem.setIcon(R.drawable.msg_message);
            gotoItem.setContentDescription(LocaleController.getString("AccDescrGoToMessage", R.string.AccDescrGoToMessage));
            gotoItem.setDuplicateParentStateEnabled(false);
            actionModeLayout.addView(gotoItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
            actionModeViews.add(gotoItem);
            gotoItem.setOnClickListener(v -> onActionBarItemClick(v, gotochat));

            forwardItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            forwardItem.setIcon(R.drawable.msg_forward);
            forwardItem.setContentDescription(LocaleController.getString("Forward", R.string.Forward));
            forwardItem.setDuplicateParentStateEnabled(false);
            actionModeLayout.addView(forwardItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
            actionModeViews.add(forwardItem);
            forwardItem.setOnClickListener(v -> onActionBarItemClick(v, forward));

            updateForwardItem();
        }
        deleteItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
        deleteItem.setIcon(R.drawable.msg_delete);
        deleteItem.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
        deleteItem.setDuplicateParentStateEnabled(false);
        actionModeLayout.addView(deleteItem, new LinearLayout.LayoutParams(AndroidUtilities.dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(deleteItem);
        deleteItem.setOnClickListener(v -> onActionBarItemClick(v, delete));

        photoVideoAdapter = new SharedPhotoVideoAdapter(context) {
            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                MediaPage mediaPage = getMediaPage(0);
                if (mediaPage != null && mediaPage.animationSupportingListView.getVisibility() == View.VISIBLE) {
                    animationSupportingPhotoVideoAdapter.notifyDataSetChanged();
                }
            }
        };
        animationSupportingPhotoVideoAdapter = new SharedPhotoVideoAdapter(context);
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
        if (topicId == 0) {
            chatUsersAdapter.sortedUsers = sortedUsers;
            chatUsersAdapter.chatInfo = membersFirst ? chatInfo : null;
        }
        storiesAdapter = new StoriesAdapter(context, false) {
            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                MediaPage mediaPage = getMediaPage(TAB_STORIES);
                if (mediaPage != null && mediaPage.animationSupportingListView.getVisibility() == View.VISIBLE) {
                    animationSupportingStoriesAdapter.notifyDataSetChanged();
                }
                if (mediaPage != null) {
                    mediaPage.emptyView.showProgress(storiesList != null && (storiesList.isLoading() || hasInternet() && storiesList.getCount() > 0));
                }
            }
        };
        animationSupportingStoriesAdapter = new StoriesAdapter(context, false);
        archivedStoriesAdapter = new StoriesAdapter(context, true) {
            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                MediaPage mediaPage = getMediaPage(TAB_ARCHIVED_STORIES);
                if (mediaPage != null && mediaPage.animationSupportingListView.getVisibility() == View.VISIBLE) {
                    animationSupportingArchivedStoriesAdapter.notifyDataSetChanged();
                }
                if (mediaPage != null) {
                    mediaPage.emptyView.showProgress(storiesList != null && (storiesList.isLoading() || hasInternet() && storiesList.getCount() > 0));
                }
            }
        };
        animationSupportingArchivedStoriesAdapter = new StoriesAdapter(context, true);
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

                                float photoVideoOptionsAlpha = getPhotoVideoOptionsAlpha(scrollProgress);
                                photoVideoOptionsItem.setAlpha(photoVideoOptionsAlpha);
                                photoVideoOptionsItem.setVisibility((photoVideoOptionsAlpha == 0 || !canShowSearchItem() || isArchivedOnlyStoriesView()) ? INVISIBLE : View.VISIBLE);
                            } else {
                                searchItem.setAlpha(0.0f);
                            }

                        }
                    }
                    invalidateBlur();
                }
            };
            addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, isStoriesView() ? 0 : 48, 0, 0));
            if (a == 1) {
                mediaPage.setTranslationX(AndroidUtilities.displaySize.x);
            }
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
                    if (mediaPage.selectedType == TAB_PHOTOVIDEO || mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES) {
                        extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], SharedPhotoVideoCell.getItemSize(1) * 2);
                    } else if (mediaPage.selectedType == TAB_FILES) {
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
                    final int columnsCount = mediaColumnsCount[mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES ? 1 : 0];
                    if (mediaPage.listView.getAdapter() == photoVideoAdapter) {
                        if (photoVideoAdapter.getItemViewType(position) == 2) {
                            return columnsCount;
                        }
                        return 1;
                    } else if (mediaPage.listView.getAdapter() == storiesAdapter) {
                        if (storiesAdapter.getItemViewType(position) == 2) {
                            return columnsCount;
                        }
                        return 1;
                    } else if (mediaPage.listView.getAdapter() == archivedStoriesAdapter) {
                        if (storiesAdapter.getItemViewType(position) == 2) {
                            return columnsCount;
                        }
                        return 1;
                    }
                    if (mediaPage.listView.getAdapter() != gifAdapter) {
                        return mediaPage.layoutManager.getSpanCount();
                    }
                    if (mediaPage.listView.getAdapter() == gifAdapter && sharedMediaData[5].messages.isEmpty()) {
                        return mediaPage.layoutManager.getSpanCount();
                    }
                    return mediaPage.layoutManager.getSpanSizeForItem(position);
                }
            });
            mediaPages[a].listView = new InternalListView(context) {

                final HashSet<SharedPhotoVideoCell2> excludeDrawViews = new HashSet<>();
                final ArrayList<SharedPhotoVideoCell2> drawingViews = new ArrayList<>();
                final ArrayList<SharedPhotoVideoCell2> drawingViews2 = new ArrayList<>();
                final ArrayList<SharedPhotoVideoCell2> drawingViews3 = new ArrayList<>();

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    checkLoadMoreScroll(mediaPage, mediaPage.listView, layoutManager);
                    if (mediaPage.selectedType == 0) {
                        PhotoViewer.getInstance().checkCurrentImageVisibility();
                    }
                }

                private TextPaint archivedHintPaint;
                private StaticLayout archivedHintLayout;
                private float archivedHintLayoutWidth, archivedHintLayoutLeft;

                UserListPoller poller;

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (getAdapter() == archivedStoriesAdapter && getChildCount() > 0) {
                        View topChild = getChildAt(0);
                        if (topChild != null && getChildAdapterPosition(topChild) == 0) {
                            int top = topChild.getTop();
                            if (photoVideoChangeColumnsAnimation && changeColumnsTab == TAB_ARCHIVED_STORIES && mediaPage.animationSupportingListView.getChildCount() > 0) {
                                View supportingTopChild = mediaPage.animationSupportingListView.getChildAt(0);
                                if (supportingTopChild != null && mediaPage.animationSupportingListView.getChildAdapterPosition(supportingTopChild) == 0) {
                                    top = AndroidUtilities.lerp(top, supportingTopChild.getTop(), photoVideoChangeColumnsProgress);
                                }
                            }
                            if (archivedHintPaint == null) {
                                archivedHintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                                archivedHintPaint.setTextSize(AndroidUtilities.dp(14));
                                archivedHintPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                            }
                            int width = getMeasuredWidth() - AndroidUtilities.dp(60);
                            if (archivedHintLayout == null || archivedHintLayout.getWidth() != width) {
                                archivedHintLayout = new StaticLayout(LocaleController.getString(isArchivedOnlyStoriesView() ? R.string.ProfileStoriesArchiveChannelHint : R.string.ProfileStoriesArchiveHint), archivedHintPaint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
                                archivedHintLayoutWidth = 0;
                                archivedHintLayoutLeft = width;
                                for (int i = 0; i < archivedHintLayout.getLineCount(); ++i) {
                                    archivedHintLayoutWidth = Math.max(archivedHintLayoutWidth, archivedHintLayout.getLineWidth(i));
                                    archivedHintLayoutLeft = Math.min(archivedHintLayoutLeft, archivedHintLayout.getLineLeft(i));
                                }
                            }

                            canvas.save();
                            canvas.translate(
                            (getWidth() - archivedHintLayoutWidth) / 2f - archivedHintLayoutLeft,
                                top - (AndroidUtilities.dp(64) + archivedHintLayout.getHeight()) / 2f
                            );
                            archivedHintLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                    SharedPhotoVideoAdapter movingAdapter, supportingMovingAdapter;
                    int ci = 0;
                    if (changeColumnsTab == TAB_STORIES) {
                        movingAdapter = storiesAdapter;
                        supportingMovingAdapter = animationSupportingStoriesAdapter;
                        ci = 1;
                    } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                        movingAdapter = archivedStoriesAdapter;
                        supportingMovingAdapter = animationSupportingArchivedStoriesAdapter;
                        ci = 1;
                    } else {
                        movingAdapter = photoVideoAdapter;
                        supportingMovingAdapter = animationSupportingPhotoVideoAdapter;
                    }
                    if (this == mediaPage.listView && getAdapter() == movingAdapter) {
                        int firstVisibleItemPosition = 0;
                        int firstVisibleItemPosition2 = 0;
                        int lastVisibleItemPosition = 0;
                        int lastVisibleItemPosition2 = 0;

                        int rowsOffset = 0;
                        int columnsOffset = 0;
                        float minY = getMeasuredHeight();
                        if (photoVideoChangeColumnsAnimation) {
                            int max = -1;
                            int min = -1;
                            for (int i = 0; i < mediaPage.listView.getChildCount(); i++) {
                                int p = mediaPage.listView.getChildAdapterPosition(mediaPage.listView.getChildAt(i));
                                if (p >= 0 && (p > max || max == -1)) {
                                    max = p;
                                }
                                if (p >= 0 && (p < min || min == -1)) {
                                    min = p;
                                }
                            }
                            firstVisibleItemPosition = min;
                            lastVisibleItemPosition = max;

                            max = -1;
                            min = -1;
                            for (int i = 0; i < mediaPage.animationSupportingListView.getChildCount(); i++) {
                                int p = mediaPage.animationSupportingListView.getChildAdapterPosition(mediaPage.animationSupportingListView.getChildAt(i));
                                if (p >= 0 && (p > max || max == -1)) {
                                    max = p;
                                }
                                if (p >= 0 && (p < min || min == -1)) {
                                    min = p;
                                }
                            }

                            firstVisibleItemPosition2 = min;
                            lastVisibleItemPosition2 = max;

                            if (firstVisibleItemPosition >= 0 && firstVisibleItemPosition2 >= 0 && pinchCenterPosition >= 0) {
                                int rowsCount1 = (int) Math.ceil((movingAdapter.getItemCount()) / (float) mediaColumnsCount[ci]);
                                int rowsCount2 = (int) Math.ceil((movingAdapter.getItemCount()) / (float) animateToColumnsCount);
                                rowsOffset = ((pinchCenterPosition) / animateToColumnsCount - firstVisibleItemPosition2 / animateToColumnsCount) - ((pinchCenterPosition - movingAdapter.getTopOffset()) / mediaColumnsCount[ci] - firstVisibleItemPosition / mediaColumnsCount[ci]);
                                if ((firstVisibleItemPosition / mediaColumnsCount[ci] - rowsOffset < 0  && animateToColumnsCount < mediaColumnsCount[ci]) || (firstVisibleItemPosition2 / animateToColumnsCount + rowsOffset < 0 && animateToColumnsCount > mediaColumnsCount[ci])) {
                                    rowsOffset = 0;
                                }
                                if ((lastVisibleItemPosition2 / mediaColumnsCount[ci] + rowsOffset >= rowsCount1 && animateToColumnsCount > mediaColumnsCount[ci]) || (lastVisibleItemPosition / animateToColumnsCount - rowsOffset >= rowsCount2 && animateToColumnsCount < mediaColumnsCount[ci])) {
                                    rowsOffset = 0;
                                }

                                float k = (pinchCenterPosition % mediaColumnsCount[ci]) / (float) (mediaColumnsCount[ci] - 1);
                                columnsOffset = (int) ((animateToColumnsCount - mediaColumnsCount[ci]) * k);
                            }
                            animationSupportingSortedCells.clear();
                            excludeDrawViews.clear();
                            drawingViews.clear();
                            drawingViews2.clear();
                            drawingViews3.clear();
                            animationSupportingSortedCellsOffset = 0;
                            for (int i = 0; i < mediaPage.animationSupportingListView.getChildCount(); i++) {
                                View child = mediaPage.animationSupportingListView.getChildAt(i);
                                if (child.getTop() > getMeasuredHeight() || child.getBottom() < 0) {
                                    continue;
                                }
                                if (child instanceof SharedPhotoVideoCell2) {
                                    animationSupportingSortedCells.add((SharedPhotoVideoCell2) child);
                                } else if (child instanceof TextView) {
                                    animationSupportingSortedCellsOffset++;
                                }
                            }
                            drawingViews.addAll(animationSupportingSortedCells);
                            FastScroll fastScroll = getFastScroll();
                            if (fastScroll != null && fastScroll.getTag() != null) {
                                float p1 = movingAdapter.getScrollProgress(mediaPage.listView);
                                float p2 = supportingMovingAdapter.getScrollProgress(mediaPage.animationSupportingListView);
                                float a1 = movingAdapter.fastScrollIsVisible(mediaPage.listView) ? 1f : 0f;
                                float a2 = supportingMovingAdapter.fastScrollIsVisible(mediaPage.animationSupportingListView) ? 1f : 0f;
                                fastScroll.setProgress(p1 * (1f - photoVideoChangeColumnsProgress) + p2 * photoVideoChangeColumnsProgress);
                                fastScroll.setVisibilityAlpha(a1 * (1f - photoVideoChangeColumnsProgress) + a2 * photoVideoChangeColumnsProgress);
                            }
                        }

                        for (int i = 0; i < getChildCount(); i++) {
                            View child = getChildAt(i);
                            if (child.getTop() > getMeasuredHeight() || child.getBottom() < 0) {
                                if (child instanceof SharedPhotoVideoCell2) {
                                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) getChildAt(i);
                                    cell.setCrossfadeView(null, 0, 0);
                                    cell.setTranslationX(0);
                                    cell.setTranslationY(0);
                                    cell.setImageScale(1f, !photoVideoChangeColumnsAnimation);
                                }
                                continue;
                            }
                            if (child instanceof SharedPhotoVideoCell2) {
                                SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) getChildAt(i);

                                if (cell.getMessageId() == mediaPage.highlightMessageId && cell.imageReceiver.hasBitmapImage()) {
                                    if (!mediaPage.highlightAnimation) {
                                        mediaPage.highlightProgress = 0;
                                        mediaPage.highlightAnimation = true;
                                    }
                                    float p = 1f;
                                    if (mediaPage.highlightProgress < 0.3f) {
                                        p = mediaPage.highlightProgress / 0.3f;
                                    } else if (mediaPage.highlightProgress > 0.7f) {
                                        p = (1f - mediaPage.highlightProgress) / 0.3f;
                                    }
                                    cell.setHighlightProgress(p);
                                } else {
                                    cell.setHighlightProgress(0);
                                }

                                MessageObject messageObject = cell.getMessageObject();
                                float alpha = 1f;
                                if (messageObject != null && messageAlphaEnter.get(messageObject.getId(), null) != null) {
                                    alpha = messageAlphaEnter.get(messageObject.getId(), 1f);
                                }
                                cell.setImageAlpha(alpha, !photoVideoChangeColumnsAnimation);

                                boolean inAnimation = false;
                                if (photoVideoChangeColumnsAnimation) {
                                    float fromScale = 1f;

                                    int currentColumn = (((GridLayoutManager.LayoutParams) cell.getLayoutParams()).getViewAdapterPosition()) % mediaColumnsCount[ci] + columnsOffset;
                                    int currentRow = ((((GridLayoutManager.LayoutParams) cell.getLayoutParams()).getViewAdapterPosition()) - firstVisibleItemPosition) / mediaColumnsCount[ci] + rowsOffset;
                                    int toIndex = currentRow * animateToColumnsCount + currentColumn + animationSupportingSortedCellsOffset;
                                    if (currentColumn >= 0 && currentColumn < animateToColumnsCount && toIndex >= 0 && toIndex < animationSupportingSortedCells.size()) {
                                        inAnimation = true;
                                        float toScale = (animationSupportingSortedCells.get(toIndex).getMeasuredWidth() - AndroidUtilities.dpf2(2)) / (float) (cell.getMeasuredWidth() - AndroidUtilities.dpf2(2));
                                        float scale = fromScale * (1f - photoVideoChangeColumnsProgress) + toScale * photoVideoChangeColumnsProgress;
                                        float fromX = cell.getLeft();
                                        float fromY = cell.getTop();
                                        float toX = animationSupportingSortedCells.get(toIndex).getLeft();
                                        float toY = animationSupportingSortedCells.get(toIndex).getTop();

                                        cell.setPivotX(0);
                                        cell.setPivotY(0);
                                        cell.setImageScale(scale, !photoVideoChangeColumnsAnimation);
                                        cell.setTranslationX((toX - fromX) * photoVideoChangeColumnsProgress);
                                        cell.setTranslationY((toY - fromY) * photoVideoChangeColumnsProgress);
                                        cell.setCrossfadeView(animationSupportingSortedCells.get(toIndex), photoVideoChangeColumnsProgress, animateToColumnsCount);
                                        excludeDrawViews.add(animationSupportingSortedCells.get(toIndex));
                                        drawingViews3.add(cell);
                                        canvas.save();
                                        canvas.translate(cell.getX(), cell.getY());
                                        cell.draw(canvas);
                                        canvas.restore();

                                        if (cell.getY() < minY) {
                                            minY = cell.getY();
                                        }
                                    }
                                }

                                if (!inAnimation) {
                                    if (photoVideoChangeColumnsAnimation) {
                                        drawingViews2.add(cell);
                                    }
                                    cell.setCrossfadeView(null, 0, 0);
                                    cell.setTranslationX(0);
                                    cell.setTranslationY(0);
                                    cell.setImageScale(1f, !photoVideoChangeColumnsAnimation);
                                }
                            }
                        }

                        if (photoVideoChangeColumnsAnimation && !drawingViews.isEmpty()) {
                            float toScale = animateToColumnsCount / (float) mediaColumnsCount[ci];
                            float scale = toScale * (1f - photoVideoChangeColumnsProgress) + photoVideoChangeColumnsProgress;

                            float sizeToScale = ((getMeasuredWidth() / (float) mediaColumnsCount[ci]) - AndroidUtilities.dpf2(2)) / ((getMeasuredWidth() / (float) animateToColumnsCount) - AndroidUtilities.dpf2(2));
                            float scaleSize = sizeToScale * (1f - photoVideoChangeColumnsProgress) + photoVideoChangeColumnsProgress;

                            float fromSize = getMeasuredWidth() / (float) mediaColumnsCount[ci];
                            float toSize = (getMeasuredWidth() / (float) animateToColumnsCount);
                            float size1 = (float) ((Math.ceil((getMeasuredWidth() / (float) animateToColumnsCount)) - AndroidUtilities.dpf2(2)) * scaleSize + AndroidUtilities.dpf2(2));
                            if (changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES) {
                                size1 *= 1.25f;
                            }

                            for (int i = 0; i < drawingViews.size(); i++) {
                                SharedPhotoVideoCell2 view = drawingViews.get(i);
                                if (excludeDrawViews.contains(view)) {
                                    continue;
                                }
                                view.setCrossfadeView(null, 0, 0);
                                int fromColumn = (((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) % animateToColumnsCount;
                                int toColumn = fromColumn - columnsOffset;
                                int currentRow = ((((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) - firstVisibleItemPosition2) / animateToColumnsCount;
                                currentRow -= rowsOffset;

                                canvas.save();
                                canvas.translate(toColumn * fromSize * (1f - photoVideoChangeColumnsProgress) + toSize * fromColumn * photoVideoChangeColumnsProgress, minY + size1 * currentRow);
                                view.setImageScale(scaleSize, !photoVideoChangeColumnsAnimation);
                                if (toColumn < mediaColumnsCount[ci]) {
                                    canvas.saveLayerAlpha(0, 0, view.getMeasuredWidth() * scale, view.getMeasuredHeight() * scale, (int) (photoVideoChangeColumnsProgress * 255), Canvas.ALL_SAVE_FLAG);
                                    view.draw(canvas);
                                    canvas.restore();
                                } else {
                                    view.draw(canvas);
                                }
                                canvas.restore();
                            }
                        }

                        super.dispatchDraw(canvas);

                        if (photoVideoChangeColumnsAnimation) {
                            float toScale = mediaColumnsCount[ci] / (float) animateToColumnsCount;
                            float scale = toScale * photoVideoChangeColumnsProgress + (1f - photoVideoChangeColumnsProgress);

                            float sizeToScale = ((getMeasuredWidth() / (float) animateToColumnsCount) - AndroidUtilities.dpf2(2)) / ((getMeasuredWidth() / (float) mediaColumnsCount[ci]) - AndroidUtilities.dpf2(2));
                            float scaleSize = sizeToScale * photoVideoChangeColumnsProgress + (1f - photoVideoChangeColumnsProgress);

                            float size1 = (float) ((Math.ceil((getMeasuredWidth() / (float) mediaColumnsCount[ci])) - AndroidUtilities.dpf2(2)) * scaleSize + AndroidUtilities.dpf2(2));
                            if (changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES) {
                                size1 *= 1.25f;
                            }
                            float fromSize = getMeasuredWidth() / (float) mediaColumnsCount[ci];
                            float toSize = getMeasuredWidth() / (float) animateToColumnsCount;

                            for (int i = 0; i < drawingViews2.size(); i++) {
                                SharedPhotoVideoCell2 view = drawingViews2.get(i);
                                int fromColumn = (((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) % mediaColumnsCount[ci];
                                int currentRow = ((((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) - firstVisibleItemPosition) / mediaColumnsCount[ci];

                                currentRow += rowsOffset;
                                int toColumn = fromColumn + columnsOffset;

                                canvas.save();
                                view.setImageScale(scaleSize, !photoVideoChangeColumnsAnimation);
                                canvas.translate(fromColumn * fromSize * (1f - photoVideoChangeColumnsProgress) + toSize * toColumn * photoVideoChangeColumnsProgress, minY + size1 * currentRow);
                                if (toColumn < animateToColumnsCount) {
                                    canvas.saveLayerAlpha(0, 0, view.getMeasuredWidth() * scale, view.getMeasuredHeight() * scale, (int) ((1f - photoVideoChangeColumnsProgress) * 255), Canvas.ALL_SAVE_FLAG);
                                    view.draw(canvas);
                                    canvas.restore();
                                } else {
                                    view.draw(canvas);
                                }
                                canvas.restore();
                            }

                            if (!drawingViews3.isEmpty()) {
                                canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (255 * photoVideoChangeColumnsProgress), Canvas.ALL_SAVE_FLAG);
                                for (int i = 0; i < drawingViews3.size(); i++) {
                                    drawingViews3.get(i).drawCrossafadeImage(canvas);
                                }
                                canvas.restore();
                            }
                        }
                    } else {
                        for (int i = 0; i < getChildCount(); i++) {
                            View child = getChildAt(i);
                            int messageId = getMessageId(child);
                            float alpha = 1;
                            if (messageId != 0 && messageAlphaEnter.get(messageId, null) != null) {
                                alpha = messageAlphaEnter.get(messageId, 1f);
                            }
                            if (child instanceof SharedDocumentCell) {
                                SharedDocumentCell cell = (SharedDocumentCell) child;
                                cell.setEnterAnimationAlpha(alpha);
                            } else if (child instanceof SharedAudioCell) {
                                SharedAudioCell cell = (SharedAudioCell) child;
                                cell.setEnterAnimationAlpha(alpha);
                            }
                        }
                        super.dispatchDraw(canvas);
                    }


                    if (mediaPage.highlightAnimation) {
                        mediaPage.highlightProgress += 16f / 1500f;
                        if (mediaPage.highlightProgress >= 1) {
                            mediaPage.highlightProgress = 0;
                            mediaPage.highlightAnimation = false;
                            mediaPage.highlightMessageId = 0;
                        }
                        invalidate();
                    }
                    if (poller == null) {
                        poller = UserListPoller.getInstance(profileActivity.getCurrentAccount());
                    }
                    poller.checkList(this);
                }

                @Override
                public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    SharedPhotoVideoAdapter movingAdapter;
                    if (changeColumnsTab == TAB_STORIES) {
                        movingAdapter = storiesAdapter;
                    } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                        movingAdapter = archivedStoriesAdapter;
                    } else {
                        movingAdapter = photoVideoAdapter;
                    }
                    if (mediaPage.listView == this && getAdapter() == movingAdapter) {
                        if (photoVideoChangeColumnsAnimation && child instanceof SharedPhotoVideoCell2) {
                            return true;
                        }
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }

            };

            mediaPages[a].listView.setFastScrollEnabled(RecyclerListView.FastScroll.DATE_TYPE);
            mediaPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            mediaPages[a].listView.setPinnedSectionOffsetY(-AndroidUtilities.dp(2));
            mediaPages[a].listView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
            mediaPages[a].listView.setItemAnimator(null);
            mediaPages[a].listView.setClipToPadding(false);
            mediaPages[a].listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE);
            mediaPages[a].listView.setLayoutManager(layoutManager);
            mediaPages[a].addView(mediaPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].animationSupportingListView = new InternalListView(context);
            mediaPages[a].animationSupportingListView.setLayoutManager(mediaPages[a].animationSupportingLayoutManager = new GridLayoutManager(context, 3) {

                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }

                @Override
                public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                    if (photoVideoChangeColumnsAnimation) {
                        dy = 0;
                    }
                    return super.scrollVerticallyBy(dy, recycler, state);
                }
            });
            mediaPages[a].addView(mediaPages[a].animationSupportingListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].animationSupportingListView.setVisibility(View.GONE);
            mediaPages[a].animationSupportingListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    if (view instanceof SharedPhotoVideoCell2) {
                        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                        final int position = mediaPage.animationSupportingListView.getChildAdapterPosition(cell), spanCount = mediaPage.animationSupportingLayoutManager.getSpanCount();
                        cell.isFirst = position % spanCount == 0;
                        cell.isLast = position % spanCount == spanCount - 1;
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    } else {
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    }
                }
            });


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
                    } else if (view instanceof SharedPhotoVideoCell2) {
                        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                        final int position = mediaPage.listView.getChildAdapterPosition(cell), spanCount = mediaPage.layoutManager.getSpanCount();
                        cell.isFirst = position % spanCount == 0;
                        cell.isLast = position % spanCount == spanCount - 1;
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    } else {
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        outRect.right = 0;
                    }
                }
            });
            mediaPages[a].listView.setOnItemClickListener((view, position, x, y) -> {
                if (mediaPage.selectedType == TAB_GROUPUSERS) {
                    if (view instanceof UserCell) {
                        TLRPC.ChatParticipant participant;
                        final int i;
                        if (!chatUsersAdapter.sortedUsers.isEmpty()) {
                            i = chatUsersAdapter.sortedUsers.get(position);
                        } else {
                            i = position;
                        }
                        participant = chatUsersAdapter.chatInfo.participants.participants.get(i);
                        if (i < 0 || i >= chatUsersAdapter.chatInfo.participants.participants.size()) {
                            return;
                        }
                        onMemberClick(participant, false, view);
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
                } else if (mediaPage.selectedType == TAB_COMMON_GROUPS && view instanceof ProfileSearchCell) {
                    TLRPC.Chat chat = ((ProfileSearchCell) view).getChat();
                    Bundle args = new Bundle();
                    args.putLong("chat_id", chat.id);
                    if (!profileActivity.getMessagesController().checkCanOpenChat(args, profileActivity)) {
                        return;
                    }
                    profileActivity.presentFragment(new ChatActivity(args));
                } else if (mediaPage.selectedType == TAB_FILES && view instanceof SharedDocumentCell) {
                    onItemClick(position, view, ((SharedDocumentCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if (mediaPage.selectedType == TAB_LINKS && view instanceof SharedLinkCell) {
                    onItemClick(position, view, ((SharedLinkCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if ((mediaPage.selectedType == TAB_VOICE || mediaPage.selectedType == TAB_AUDIO) && view instanceof SharedAudioCell) {
                    onItemClick(position, view, ((SharedAudioCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if (mediaPage.selectedType == TAB_GIF && view instanceof ContextLinkCell) {
                    onItemClick(position, view, (MessageObject) ((ContextLinkCell) view).getParentObject(), 0, mediaPage.selectedType);
                } else if (mediaPage.selectedType == TAB_PHOTOVIDEO && view instanceof SharedPhotoVideoCell2) {
                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                    if (cell.canRevealSpoiler()) {
                        cell.startRevealMedia(x, y);
                        return;
                    }
                    MessageObject messageObject = cell.getMessageObject();
                    if (messageObject != null) {
                        onItemClick(position, view, messageObject, 0, mediaPage.selectedType);
                    }
                } else if ((mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES) && view instanceof SharedPhotoVideoCell2) {
                    SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
                    MessageObject messageObject = cell.getMessageObject();
                    if (messageObject != null) {
                        onItemClick(position, view, messageObject, 0, mediaPage.selectedType);
                    }
                }
            });
            mediaPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    checkLoadMoreScroll(mediaPage, (RecyclerListView) recyclerView, layoutManager);
                    if (dy != 0 && (mediaPages[0].selectedType == 0 || mediaPages[0].selectedType == 5) && !sharedMediaData[0].messages.isEmpty()) {
                        showFloatingDateView();
                    }
                    if (dy != 0 && (mediaPage.selectedType == 0 || mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES)) {
                        showFastScrollHint(mediaPage, sharedMediaData, true);
                    }
                    mediaPage.listView.checkSection(true);
                    if (mediaPage.fastScrollHintView != null) {
                        mediaPage.invalidate();
                    }
                    invalidateBlur();
                }
            });
            mediaPages[a].listView.setOnItemLongClickListener((view, position) -> {
                if (photoVideoChangeColumnsAnimation) {
                    return false;
                }
                if (isActionModeShowed) {
                    mediaPage.listView.clickItem(view, position);
                    return true;
                }
                if (mediaPage.selectedType == TAB_GROUPUSERS && view instanceof UserCell) {
                    final TLRPC.ChatParticipant participant;
                    int index = position;
                    if (!chatUsersAdapter.sortedUsers.isEmpty()) {
                        if (position >= chatUsersAdapter.sortedUsers.size()) {
                            return false;
                        }
                        index = chatUsersAdapter.sortedUsers.get(position);
                    }
                    if (index < 0 || index >= chatUsersAdapter.chatInfo.participants.participants.size()) {
                        return false;
                    }
                    participant = chatUsersAdapter.chatInfo.participants.participants.get(index);
                    RecyclerListView listView = (RecyclerListView) view.getParent();
                    for (int i = 0; i < listView.getChildCount(); ++i) {
                        View child = listView.getChildAt(i);
                        if (listView.getChildAdapterPosition(child) == position) {
                            view = child;
                            break;
                        }
                    }
                    return onMemberClick(participant, true, view);
                } else if (mediaPage.selectedType == TAB_FILES && view instanceof SharedDocumentCell) {
                    return onItemLongClick(((SharedDocumentCell) view).getMessage(), view, 0);
                } else if (mediaPage.selectedType == TAB_LINKS && view instanceof SharedLinkCell) {
                    return onItemLongClick(((SharedLinkCell) view).getMessage(), view, 0);
                } else if ((mediaPage.selectedType == TAB_VOICE || mediaPage.selectedType == TAB_AUDIO) && view instanceof SharedAudioCell) {
                    return onItemLongClick(((SharedAudioCell) view).getMessage(), view, 0);
                } else if (mediaPage.selectedType == TAB_GIF && view instanceof ContextLinkCell) {
                    return onItemLongClick((MessageObject) ((ContextLinkCell) view).getParentObject(), view, 0);
                } else if ((mediaPage.selectedType == TAB_PHOTOVIDEO || mediaPage.selectedType == TAB_ARCHIVED_STORIES || mediaPage.selectedType == TAB_STORIES && isStoriesView()) && view instanceof SharedPhotoVideoCell2) {
                    MessageObject messageObject = ((SharedPhotoVideoCell2) view).getMessageObject();
                    if (messageObject != null) {
                        return onItemLongClick(messageObject, view, mediaPage.selectedType);
                    }
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
                    return mediaColumnsCount[mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES ? 1 : 0];
                }

                @Override
                public int getViewType() {
                    setIsSingleCell(false);
                    if (mediaPage.selectedType == TAB_PHOTOVIDEO || mediaPage.selectedType == TAB_GIF) {
                        return FlickerLoadingView.PHOTOS_TYPE;
                    } else if (mediaPage.selectedType == TAB_FILES) {
                        return FlickerLoadingView.FILES_TYPE;
                    } else if (mediaPage.selectedType == TAB_VOICE || mediaPage.selectedType == TAB_AUDIO) {
                        return FlickerLoadingView.USERS_TYPE;
                    } else if (mediaPage.selectedType == TAB_LINKS) {
                        return FlickerLoadingView.LINKS_TYPE;
                    } else if (mediaPage.selectedType == TAB_GROUPUSERS) {
                        return FlickerLoadingView.USERS_TYPE;
                    } else if (mediaPage.selectedType == TAB_COMMON_GROUPS) {
                        if (scrollSlidingTextTabStrip.getTabsCount() == 1) {
                            setIsSingleCell(true);
                        }
                        return FlickerLoadingView.DIALOG_TYPE;
                    } else if (mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES) {
                        return FlickerLoadingView.STORIES_TYPE;
                    }
                    return FlickerLoadingView.DIALOG_TYPE;
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    backgroundPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
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
            mediaPages[a].listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

            mediaPages[a].scrollHelper = new RecyclerAnimationScrollHelper(mediaPages[a].listView, mediaPages[a].layoutManager);
        }

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setOverrideColor(Theme.key_chat_mediaTimeBackground, Theme.key_chat_mediaTimeText);
        floatingDateView.setTranslationY(-AndroidUtilities.dp(48));
        addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48 + 4, 0, 0));

        if (!isStoriesView()) {
            addView(fragmentContextView = new FragmentContextView(context, parent, this, false, resourcesProvider), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));
            fragmentContextView.setDelegate((start, show) -> {
                if (!start) {
                    requestLayout();
                }
            });

            addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            addView(actionModeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
        }

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(getThemedColor(Theme.key_divider));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        layoutParams.topMargin = isStoriesView() ? 0 : AndroidUtilities.dp(48) - 1;
        addView(shadowLine, layoutParams);

        updateTabs(false);
        switchToCurrentSelectedMode(false);
        if (hasMedia[0] >= 0) {
            loadFastScrollData(false);
        }
    }

    protected boolean isStoriesView() {
        return false;
    }

    protected boolean isArchivedOnlyStoriesView() {
        return false;
    }

    protected boolean includeStories() {
        return true;
    }

    protected int getInitialTab() {
        return 0;
    }

    public void setStoriesFilter(boolean photos, boolean videos) {
        if (storiesAdapter != null && storiesAdapter.storiesList != null) {
            storiesAdapter.storiesList.updateFilters(photos, videos);
        }
        if (archivedStoriesAdapter != null && archivedStoriesAdapter.storiesList != null) {
            archivedStoriesAdapter.storiesList.updateFilters(photos, videos);
        }
    }

    protected void invalidateBlur() {

    }

    public void setForwardRestrictedHint(HintView hintView) {
        fwdRestrictedHint = hintView;
    }

    private int getMessageId(View child) {
        if (child instanceof SharedPhotoVideoCell2) {
            return ((SharedPhotoVideoCell2) child).getMessageId();
        }
        if (child instanceof SharedDocumentCell) {
            SharedDocumentCell cell = (SharedDocumentCell) child;
            return cell.getMessage().getId();
        }
        if (child instanceof SharedAudioCell) {
            SharedAudioCell cell = (SharedAudioCell) child;
            return cell.getMessage().getId();
        }
        return 0;
    }

    private void updateForwardItem() {
        if (forwardItem == null) {
            return;
        }
        boolean noforwards = profileActivity.getMessagesController().isChatNoForwards(-dialog_id) || hasNoforwardsMessage();
        forwardItem.setAlpha(noforwards ? 0.5f : 1f);
        if (noforwards && forwardItem.getBackground() != null) {
            forwardItem.setBackground(null);
        } else if (!noforwards && forwardItem.getBackground() == null) {
            forwardItem.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), 5));
        }
    }
    private boolean hasNoforwardsMessage() {
        boolean hasNoforwardsMessage = false;
        for (int a = 1; a >= 0; a--) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (int b = 0; b < selectedFiles[a].size(); b++) {
                ids.add(selectedFiles[a].keyAt(b));
            }
            for (Integer id1 : ids) {
                if (id1 > 0) {
                    MessageObject msg = selectedFiles[a].get(id1);
                    if (msg != null && msg.messageOwner != null && msg.messageOwner.noforwards) {
                        hasNoforwardsMessage = true;
                        break;
                    }
                }
            }
            if (hasNoforwardsMessage)
                break;
        }
        return hasNoforwardsMessage;
    }

    private boolean changeTypeAnimation;

    private void changeMediaFilterType() {
        MediaPage mediaPage = getMediaPage(0);
        if (mediaPage != null && mediaPage.getMeasuredHeight() > 0 && mediaPage.getMeasuredWidth() > 0) {
            Bitmap bitmap = null;
            try {
                bitmap = Bitmap.createBitmap(mediaPage.getMeasuredWidth(), mediaPage.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (bitmap != null) {
                changeTypeAnimation = true;
                Canvas canvas = new Canvas(bitmap);
                mediaPage.listView.draw(canvas);
                View view = new View(mediaPage.getContext());
                view.setBackground(new BitmapDrawable(bitmap));
                mediaPage.addView(view);
                Bitmap finalBitmap = bitmap;
                view.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        changeTypeAnimation = false;
                        if (view.getParent() != null) {
                            mediaPage.removeView(view);
                            finalBitmap.recycle();
                        }
                    }
                }).start();
                mediaPage.listView.setAlpha(0);
                mediaPage.listView.animate().alpha(1f).setDuration(200).start();
            }
        }

        int[] counts = sharedMediaPreloader.getLastMediaCount();
        ArrayList<MessageObject> messages = sharedMediaPreloader.getSharedMediaData()[0].messages;
        if (sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS) {
            sharedMediaData[0].setTotalCount(counts[0]);
        } else if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
            sharedMediaData[0].setTotalCount(counts[6]);
        } else {
            sharedMediaData[0].setTotalCount(counts[7]);
        }
        sharedMediaData[0].fastScrollDataLoaded = false;
        jumpToDate(0, DialogObject.isEncryptedDialog(dialog_id) ? Integer.MIN_VALUE : Integer.MAX_VALUE, 0, true);
        loadFastScrollData(false);
        delegate.updateSelectedMediaTabText();
        boolean enc = DialogObject.isEncryptedDialog(dialog_id);
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (sharedMediaData[0].filterType == FILTER_PHOTOS_AND_VIDEOS) {
                sharedMediaData[0].addMessage(messageObject, 0, false, enc);
            } else if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
                if (messageObject.isPhoto()) {
                    sharedMediaData[0].addMessage(messageObject, 0, false, enc);
                }
            } else {
                if (!messageObject.isPhoto()) {
                    sharedMediaData[0].addMessage(messageObject, 0, false, enc);
                }
            }
        }
    }

    private MediaPage getMediaPage(int type) {
        for (int i = 0; i < mediaPages.length; i++) {
            if (mediaPages[i] != null && mediaPages[i].selectedType == type) {
                return mediaPages[i];
            }
        }
        return null;
    }

    public void showMediaCalendar(int page, boolean fromFastScroll) {
        if (fromFastScroll && SharedMediaLayout.this.getY() != 0 && viewType == VIEW_TYPE_PROFILE_ACTIVITY) {
            return;
        }
        if (fromFastScroll && (page == TAB_STORIES || page == TAB_ARCHIVED_STORIES) && getStoriesCount(page) <= 0) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putLong("dialog_id", dialog_id);
        bundle.putInt("topic_id", topicId);
        int date = 0;
        if (fromFastScroll) {
            MediaPage mediaPage = getMediaPage(0);
            if (mediaPage != null) {
                ArrayList<Period> periods = sharedMediaData[0].fastScrollPeriods;
                Period period = null;
                int position = mediaPage.layoutManager.findFirstVisibleItemPosition();
                if (position >= 0) {
                    if (periods != null) {
                        for (int i = 0; i < periods.size(); i++) {
                            if (position <= periods.get(i).startOffset) {
                                period = periods.get(i);
                                break;
                            }
                        }
                        if (period == null) {
                            period = periods.get(periods.size() - 1);
                        }
                    }
                    if (period != null) {
                        date = period.date;
                    }
                }
            }
        }
        if (page == TAB_ARCHIVED_STORIES) {
            bundle.putInt("type", CalendarActivity.TYPE_ARCHIVED_STORIES);
        } else if (page == TAB_STORIES) {
            bundle.putInt("type", CalendarActivity.TYPE_PROFILE_STORIES);
        } else {
            bundle.putInt("type", CalendarActivity.TYPE_MEDIA_CALENDAR);
        }
        CalendarActivity calendarActivity = new CalendarActivity(bundle, sharedMediaData[0].filterType, date);
        calendarActivity.setCallback(new CalendarActivity.Callback() {
            @Override
            public void onDateSelected(int messageId, int startOffset) {
                int index = -1;
                for (int i = 0; i < sharedMediaData[0].messages.size(); i++) {
                    if (sharedMediaData[0].messages.get(i).getId() == messageId) {
                        index = i;
                    }
                }
                MediaPage mediaPage = getMediaPage(0);
                if (index >= 0 && mediaPage != null) {
                    mediaPage.layoutManager.scrollToPositionWithOffset(index, 0);
                } else {
                    jumpToDate(0, messageId, startOffset, true);
                }
                if (mediaPage != null) {
                    mediaPage.highlightMessageId = messageId;
                    mediaPage.highlightAnimation = false;
                }
            }
        });
        profileActivity.presentFragment(calendarActivity);
    }

    private void startPinchToMediaColumnsCount(boolean pinchScaleUp) {
        if (photoVideoChangeColumnsAnimation) {
            return;
        }
        MediaPage mediaPage = null;
        for (int i = 0; i < mediaPages.length; i++) {
            if (mediaPages[i].selectedType == TAB_PHOTOVIDEO || mediaPages[i].selectedType == TAB_STORIES || mediaPages[i].selectedType == TAB_ARCHIVED_STORIES) {
                mediaPage = mediaPages[i];
                break;
            }
        }
        if (mediaPage != null) {
            changeColumnsTab = mediaPage.selectedType;
            final int ci = changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES ? 1 : 0;

            int newColumnsCount = getNextMediaColumnsCount(ci, mediaColumnsCount[ci], pinchScaleUp);
            animateToColumnsCount = newColumnsCount;
            if (animateToColumnsCount == mediaColumnsCount[ci] || allowStoriesSingleColumn) {
                return;
            }
            mediaPage.animationSupportingListView.setVisibility(View.VISIBLE);
            if (changeColumnsTab == TAB_STORIES) {
                mediaPage.animationSupportingListView.setAdapter(animationSupportingStoriesAdapter);
            } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                mediaPage.animationSupportingListView.setAdapter(animationSupportingArchivedStoriesAdapter);
            } else {
                mediaPage.animationSupportingListView.setAdapter(animationSupportingPhotoVideoAdapter);
            }
            mediaPage.animationSupportingListView.setPadding(
                mediaPage.animationSupportingListView.getPaddingLeft(),
                changeColumnsTab == TAB_ARCHIVED_STORIES ? AndroidUtilities.dp(2 + 64) : AndroidUtilities.dp(2),
                mediaPage.animationSupportingListView.getPaddingRight(),
                mediaPage.animationSupportingListView.getPaddingBottom()
            );

            mediaPage.animationSupportingLayoutManager.setSpanCount(newColumnsCount);
            mediaPage.animationSupportingListView.invalidateItemDecorations();
            final MediaPage finalMediaPage = mediaPage;
            mediaPage.animationSupportingLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (finalMediaPage.animationSupportingListView.getAdapter() == animationSupportingPhotoVideoAdapter) {
                        if (animationSupportingPhotoVideoAdapter.getItemViewType(position) == 2) {
                            return finalMediaPage.animationSupportingLayoutManager.getSpanCount();
                        }
                        return 1;
                    } else if (finalMediaPage.animationSupportingListView.getAdapter() == animationSupportingStoriesAdapter) {
                        if (animationSupportingStoriesAdapter.getItemViewType(position) == 2) {
                            return finalMediaPage.animationSupportingLayoutManager.getSpanCount();
                        }
                        return 1;
                    } else if (finalMediaPage.animationSupportingListView.getAdapter() == animationSupportingArchivedStoriesAdapter) {
                        if (animationSupportingArchivedStoriesAdapter.getItemViewType(position) == 2) {
                            return finalMediaPage.animationSupportingLayoutManager.getSpanCount();
                        }
                        return 1;
                    }
                    return 1;
                }
            });
            AndroidUtilities.updateVisibleRows(mediaPage.listView);

            photoVideoChangeColumnsAnimation = true;
            if (changeColumnsTab == TAB_PHOTOVIDEO) {
                sharedMediaData[0].setListFrozen(true);
            }
            photoVideoChangeColumnsProgress = 0;
            if (pinchCenterPosition >= 0) {
                for (int k = 0; k < mediaPages.length; k++) {
                    if (mediaPages[k].selectedType == changeColumnsTab) {
                        mediaPages[k].animationSupportingLayoutManager.scrollToPositionWithOffset(pinchCenterPosition, pinchCenterOffset - mediaPages[k].animationSupportingListView.getPaddingTop());
                    }
                }
            } else {
                saveScrollPosition();
            }
        }
    }

    private void finishPinchToMediaColumnsCount() {
        if (photoVideoChangeColumnsAnimation) {
            MediaPage mediaPage = null;
            for (int i = 0; i < mediaPages.length; i++) {
                if (mediaPages[i].selectedType == changeColumnsTab) {
                    mediaPage = mediaPages[i];
                    break;
                }
            }
            if (mediaPage != null) {
                final int ci = mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
                if (photoVideoChangeColumnsProgress == 1f) {
                    photoVideoChangeColumnsAnimation = false;
                    mediaColumnsCount[ci] = animateToColumnsCount;
                    if (ci == 0) {
                        SharedConfig.setMediaColumnsCount(animateToColumnsCount);
                    } else if (getStoriesCount(mediaPage.selectedType) >= 5) {
                        SharedConfig.setStoriesColumnsCount(animateToColumnsCount);
                    }
                    for (int i = 0; i < mediaPages.length; ++i) {
                        if (mediaPages[i] != null && mediaPages[i].listView != null && isTabZoomable(mediaPages[i].selectedType)) {
                            RecyclerView.Adapter adapter = mediaPages[i].listView.getAdapter();
                            if (adapter == null) {
                                continue;
                            }
                            int oldItemCount = adapter.getItemCount();
                            if (i == TAB_PHOTOVIDEO) {
                                sharedMediaData[0].setListFrozen(false);
                            }
                            mediaPages[i].animationSupportingListView.setVisibility(View.GONE);
                            mediaPages[i].layoutManager.setSpanCount(mediaColumnsCount[ci]);
                            mediaPages[i].listView.invalidateItemDecorations();
                            mediaPages[i].listView.invalidate();
                            if (adapter.getItemCount() == oldItemCount) {
                                AndroidUtilities.updateVisibleRows(mediaPages[i].listView);
                            } else {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                    if (pinchCenterPosition >= 0) {
                        for (int k = 0; k < mediaPages.length; k++) {
                            if (mediaPages[k].selectedType == changeColumnsTab) {
                                View view = mediaPages[k].animationSupportingLayoutManager.findViewByPosition(pinchCenterPosition);
                                if (view != null) {
                                    pinchCenterOffset = view.getTop();
                                }
                                mediaPages[k].layoutManager.scrollToPositionWithOffset(pinchCenterPosition,  -mediaPages[k].listView.getPaddingTop() + pinchCenterOffset);
                            }
                        }
                    } else {
                        saveScrollPosition();
                    }
                    return;
                }
                if (photoVideoChangeColumnsProgress == 0) {
                    photoVideoChangeColumnsAnimation = false;
                    if (changeColumnsTab == TAB_PHOTOVIDEO) {
                        sharedMediaData[0].setListFrozen(false);
                    }
                    mediaPage.animationSupportingListView.setVisibility(View.GONE);
                    mediaPage.listView.invalidate();
                    return;
                }
                boolean forward = photoVideoChangeColumnsProgress > 0.2f;
                ValueAnimator animator = ValueAnimator.ofFloat(photoVideoChangeColumnsProgress, forward ? 1f : 0);
                MediaPage finalMediaPage = mediaPage;
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        photoVideoChangeColumnsProgress = (float) valueAnimator.getAnimatedValue();
                        finalMediaPage.listView.invalidate();
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        photoVideoChangeColumnsAnimation = false;
                        if (forward) {
                            mediaColumnsCount[ci] = animateToColumnsCount;
                            if (ci == 0) {
                                SharedConfig.setMediaColumnsCount(animateToColumnsCount);
                            } else if (getStoriesCount(finalMediaPage.selectedType) >= 5) {
                                SharedConfig.setStoriesColumnsCount(animateToColumnsCount);
                            }
                        }
                        for (int i = 0; i < mediaPages.length; ++i) {
                            if (mediaPages[i] != null && mediaPages[i].listView != null && isTabZoomable(mediaPages[i].selectedType)) {
                                RecyclerView.Adapter adapter = mediaPages[i].listView.getAdapter();
                                if (adapter == null) {
                                    continue;
                                }
                                int oldItemCount = adapter.getItemCount();
                                if (i == TAB_PHOTOVIDEO) {
                                    sharedMediaData[0].setListFrozen(false);
                                }
                                if (forward) {
                                    mediaPages[i].layoutManager.setSpanCount(mediaColumnsCount[ci]);
                                    mediaPages[i].listView.invalidateItemDecorations();
                                    if (adapter.getItemCount() == oldItemCount) {
                                        AndroidUtilities.updateVisibleRows(mediaPages[i].listView);
                                    } else {
                                        adapter.notifyDataSetChanged();
                                    }
                                }
                                mediaPages[i].animationSupportingListView.setVisibility(View.GONE);
                            }
                        }
                        if (pinchCenterPosition >= 0) {
                            for (int k = 0; k < mediaPages.length; k++) {
                                if (mediaPages[k].selectedType == changeColumnsTab) {
                                    if (forward) {
                                        View view = mediaPages[k].animationSupportingLayoutManager.findViewByPosition(pinchCenterPosition);
                                        if (view != null) {
                                            pinchCenterOffset = view.getTop();
                                        }
                                    }
                                    mediaPages[k].layoutManager.scrollToPositionWithOffset(pinchCenterPosition, -mediaPages[k].listView.getPaddingTop() + pinchCenterOffset);
                                }
                            }
                        } else {
                            saveScrollPosition();
                        }
                        super.onAnimationEnd(animation);
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.setDuration(200);
                animator.start();
            }
        }
    }

    AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    private void animateToMediaColumnsCount(int newColumnsCount) {
        MediaPage mediaPage = getMediaPage(changeColumnsTab);
        pinchCenterPosition = -1;

        if (mediaPage != null) {
            mediaPage.listView.stopScroll();
            animateToColumnsCount = newColumnsCount;
            mediaPage.animationSupportingListView.setVisibility(View.VISIBLE);
            if (changeColumnsTab == TAB_STORIES) {
                mediaPage.animationSupportingListView.setAdapter(animationSupportingStoriesAdapter);
            } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                mediaPage.animationSupportingListView.setAdapter(animationSupportingArchivedStoriesAdapter);
            } else {
                mediaPage.animationSupportingListView.setAdapter(animationSupportingPhotoVideoAdapter);
            }
            mediaPage.animationSupportingListView.setPadding(
                mediaPage.animationSupportingListView.getPaddingLeft(),
                AndroidUtilities.dp(2) + (mediaPage.animationSupportingListView.hintPaddingTop = (changeColumnsTab == TAB_ARCHIVED_STORIES ? AndroidUtilities.dp(64) : 0)),
                mediaPage.animationSupportingListView.getPaddingRight(),
                mediaPage.animationSupportingListView.getPaddingBottom()
            );
            mediaPage.animationSupportingLayoutManager.setSpanCount(newColumnsCount);
            mediaPage.animationSupportingListView.invalidateItemDecorations();
            for (int i = 0; i < mediaPages.length; ++i) {
                if (mediaPages[i] != null && isTabZoomable(mediaPages[i].selectedType)) {
                    AndroidUtilities.updateVisibleRows(mediaPages[i].listView);
                }
            }

            photoVideoChangeColumnsAnimation = true;
            if (changeColumnsTab == TAB_PHOTOVIDEO) {
                sharedMediaData[0].setListFrozen(true);
            }
            photoVideoChangeColumnsProgress = 0;
            saveScrollPosition();
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
            MediaPage finalMediaPage = mediaPage;
            notificationsLocker.lock();
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    photoVideoChangeColumnsProgress = (float) valueAnimator.getAnimatedValue();
                    finalMediaPage.listView.invalidate();
                }
            });
            final int ci = mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    notificationsLocker.unlock();
                    photoVideoChangeColumnsAnimation = false;
                    mediaColumnsCount[ci] = newColumnsCount;
                    for (int i = 0; i < mediaPages.length; ++i) {
                        if (mediaPages[i] != null && mediaPages[i].listView != null && isTabZoomable(mediaPages[i].selectedType)) {
                            RecyclerView.Adapter adapter = mediaPages[i].listView.getAdapter();
                            if (adapter == null) {
                                continue;
                            }
                            int oldItemCount = adapter.getItemCount();
                            if (i == TAB_PHOTOVIDEO) {
                                sharedMediaData[0].setListFrozen(false);
                            }
                            mediaPages[i].layoutManager.setSpanCount(mediaColumnsCount[ci]);
                            mediaPages[i].listView.invalidateItemDecorations();
                            if (adapter.getItemCount() == oldItemCount) {
                                AndroidUtilities.updateVisibleRows(mediaPages[i].listView);
                            } else {
                                adapter.notifyDataSetChanged();
                            }
                            mediaPages[i].animationSupportingListView.setVisibility(View.GONE);
                        }
                    }
                    saveScrollPosition();
                }
            });
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.setStartDelay(100);
            animator.setDuration(350);
            animator.start();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (scrollSlidingTextTabStrip != null) {
            canvas.save();
            canvas.translate(scrollSlidingTextTabStrip.getX(), scrollSlidingTextTabStrip.getY());
            scrollSlidingTextTabStrip.drawBackground(canvas);
            canvas.restore();
        }
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

    private ScrollSlidingTextTabStripInner createScrollingTextTabStrip(Context context) {
        ScrollSlidingTextTabStripInner scrollSlidingTextTabStrip = new ScrollSlidingTextTabStripInner(context, resourcesProvider);
        if (initialTab != -1) {
            scrollSlidingTextTabStrip.setInitialTabId(initialTab);
            initialTab = -1;
        }
        scrollSlidingTextTabStrip.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
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
                onTabProgress(getTabProgress());

                float photoVideoOptionsAlpha = getPhotoVideoOptionsAlpha(progress);
                photoVideoOptionsItem.setAlpha(photoVideoOptionsAlpha);
                photoVideoOptionsItem.setVisibility((photoVideoOptionsAlpha == 0 || !canShowSearchItem() || isArchivedOnlyStoriesView()) ? INVISIBLE : View.VISIBLE);
                if (canShowSearchItem()) {
                    if (searchItemState == 1) {
                        searchItem.setAlpha(progress);
                    } else if (searchItemState == 2) {
                        searchItem.setAlpha(1.0f - progress);
                    }
                } else {
                    searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                    searchItem.setAlpha(0.0f);
                }
                if (progress == 1) {
                    MediaPage tempPage = mediaPages[0];
                    mediaPages[0] = mediaPages[1];
                    mediaPages[1] = tempPage;
                    mediaPages[1].setVisibility(View.GONE);
                    if (searchItemState == 2) {
                        searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                    }
                    searchItemState = 0;
                    startStopVisibleGifs();
                }
            }
        });
        return scrollSlidingTextTabStrip;
    }

    protected void drawBackgroundWithBlur(Canvas canvas, float y, Rect rectTmp2, Paint backgroundPaint) {
        canvas.drawRect(rectTmp2, backgroundPaint);
    }

    private boolean fillMediaData(int type) {
        SharedMediaData[] mediaData = sharedMediaPreloader.getSharedMediaData();
        if (mediaData == null) {
            return false;
        }
        if (type == 0) {
            if (!sharedMediaData[type].fastScrollDataLoaded) {
                sharedMediaData[type].totalCount = mediaData[type].totalCount;
            }
        } else {
            sharedMediaData[type].totalCount = mediaData[type].totalCount;
        }
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
        sharedMediaData[type].fastScrollPeriods.addAll(mediaData[type].fastScrollPeriods);
        return !mediaData[type].messages.isEmpty();
    }

    private void showFloatingDateView() {

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
                height = SharedPhotoVideoCell.getItemSize(1);
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
        int scrollDistance;
        if (mediaPages[0].selectedType == 0) {
            scrollDistance = mediaPages[0].layoutManager.findFirstVisibleItemPosition() / mediaColumnsCount[0] * height;
        } else {
            scrollDistance = mediaPages[0].layoutManager.findFirstVisibleItemPosition() * height;
        }
        if (scrollDistance >= mediaPages[0].listView.getMeasuredHeight() * 1.2f) {
            mediaPages[0].scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            mediaPages[0].scrollHelper.scrollToPosition(0, 0, false, true);
        } else {
            mediaPages[0].listView.smoothScrollToPosition(0);
        }
    }

    Runnable jumpToRunnable;

    private void checkLoadMoreScroll(MediaPage mediaPage, RecyclerListView recyclerView, LinearLayoutManager layoutManager) {
        if (photoVideoChangeColumnsAnimation || jumpToRunnable != null) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if ((recyclerView.getFastScroll() != null && recyclerView.getFastScroll().isPressed()) && (currentTime - mediaPage.lastCheckScrollTime) < 300) {
            return;
        }
        mediaPage.lastCheckScrollTime = currentTime;
        if (searching && searchWas || mediaPage.selectedType == TAB_GROUPUSERS) {
            return;
        }
        int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
        int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        int totalItemCount = recyclerView.getAdapter().getItemCount();
        if (mediaPage.selectedType == 0 || mediaPage.selectedType == 1 || mediaPage.selectedType == 2 || mediaPage.selectedType == 4) {
            int type = mediaPage.selectedType;
            totalItemCount = sharedMediaData[type].getStartOffset() + sharedMediaData[type].messages.size();
            if (sharedMediaData[type].fastScrollDataLoaded && sharedMediaData[type].fastScrollPeriods.size() > 2 && mediaPage.selectedType == 0 && sharedMediaData[type].messages.size() != 0) {
                int columnsCount = 1;
                if (type == 0) {
                    columnsCount = mediaColumnsCount[0];
                }
                int jumpToTreshold = (int) ((recyclerView.getMeasuredHeight() / ((float) (recyclerView.getMeasuredWidth() / (float) columnsCount))) * columnsCount * 1.5f);
                if (jumpToTreshold < 100) {
                    jumpToTreshold = 100;
                }
                if (jumpToTreshold < sharedMediaData[type].fastScrollPeriods.get(1).startOffset) {
                    jumpToTreshold = sharedMediaData[type].fastScrollPeriods.get(1).startOffset;
                }
                if ((firstVisibleItem > totalItemCount && firstVisibleItem - totalItemCount > jumpToTreshold) || ((firstVisibleItem + visibleItemCount) < sharedMediaData[type].startOffset && sharedMediaData[0].startOffset - (firstVisibleItem + visibleItemCount) > jumpToTreshold)) {
                    AndroidUtilities.runOnUIThread(jumpToRunnable = () -> {
                        findPeriodAndJumpToDate(type, recyclerView, false);
                        jumpToRunnable = null;
                    });
                    return;
                }
            }
        }

        if (mediaPage.selectedType == 7) {

        } else if (mediaPage.selectedType == TAB_STORIES) {
            if (storiesAdapter.storiesList != null && firstVisibleItem + visibleItemCount > storiesAdapter.storiesList.getLoadedCount() - mediaColumnsCount[1]) {
                storiesAdapter.load(false);
            }
        } else if (mediaPage.selectedType == TAB_ARCHIVED_STORIES) {
            if (archivedStoriesAdapter.storiesList != null && firstVisibleItem + visibleItemCount > archivedStoriesAdapter.storiesList.getLoadedCount() - mediaColumnsCount[1]) {
                archivedStoriesAdapter.load(false);
            }
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

            if ((firstVisibleItem + visibleItemCount > totalItemCount - threshold || sharedMediaData[mediaPage.selectedType].loadingAfterFastScroll) && !sharedMediaData[mediaPage.selectedType].loading) {
                int type;
                if (mediaPage.selectedType == 0) {
                    type = MEDIA_PHOTOVIDEO;
                    if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
                        type = MediaDataController.MEDIA_PHOTOS_ONLY;
                    } else if (sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY) {
                        type = MediaDataController.MEDIA_VIDEOS_ONLY;
                    }
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
                    profileActivity.getMediaDataController().loadMedia(dialog_id, 50, sharedMediaData[mediaPage.selectedType].max_id[0], 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[mediaPage.selectedType].requestIndex);
                } else if (mergeDialogId != 0 && !sharedMediaData[mediaPage.selectedType].endReached[1]) {
                    sharedMediaData[mediaPage.selectedType].loading = true;
                    profileActivity.getMediaDataController().loadMedia(mergeDialogId, 50, sharedMediaData[mediaPage.selectedType].max_id[1], 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[mediaPage.selectedType].requestIndex);
                }
            }

            int startOffset = sharedMediaData[mediaPage.selectedType].startOffset;
            if (mediaPage.selectedType == 0) {
                startOffset = photoVideoAdapter.getPositionForIndex(0);
            }
            if (firstVisibleItem - startOffset < threshold + 1 && !sharedMediaData[mediaPage.selectedType].loading && !sharedMediaData[mediaPage.selectedType].startReached && !sharedMediaData[mediaPage.selectedType].loadingAfterFastScroll) {
                loadFromStart(mediaPage.selectedType);
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

    private void loadFromStart(int selectedType) {
        int type;
        if (selectedType == 0) {
            type = MEDIA_PHOTOVIDEO;
            if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
                type = MediaDataController.MEDIA_PHOTOS_ONLY;
            } else if (sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY) {
                type = MediaDataController.MEDIA_VIDEOS_ONLY;
            }
        } else if (selectedType == 1) {
            type = MediaDataController.MEDIA_FILE;
        } else if (selectedType == 2) {
            type = MediaDataController.MEDIA_AUDIO;
        } else if (selectedType == 4) {
            type = MediaDataController.MEDIA_MUSIC;
        } else if (selectedType == 5) {
            type = MediaDataController.MEDIA_GIF;
        } else {
            type = MediaDataController.MEDIA_URL;
        }
        sharedMediaData[selectedType].loading = true;
        profileActivity.getMediaDataController().loadMedia(dialog_id, 50, 0, sharedMediaData[selectedType].min_id, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[selectedType].requestIndex);
    }

    public ActionBarMenuItem getSearchItem() {
        return searchItem;
    }

    public boolean isSearchItemVisible() {
        if (mediaPages[0].selectedType == TAB_GROUPUSERS) {
            return delegate.canSearchMembers();
        }
        return (
            mediaPages[0].selectedType != TAB_PHOTOVIDEO &&
            mediaPages[0].selectedType != TAB_STORIES &&
            mediaPages[0].selectedType != TAB_ARCHIVED_STORIES &&
            mediaPages[0].selectedType != TAB_VOICE &&
            mediaPages[0].selectedType != TAB_GIF &&
            mediaPages[0].selectedType != TAB_COMMON_GROUPS
        );
    }

    public boolean isTabZoomable(int type) {
        return type == TAB_PHOTOVIDEO || type == TAB_STORIES || type == TAB_ARCHIVED_STORIES;
    }

    public boolean isCalendarItemVisible() {
        return mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES;
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
        boolean pollerEnabled = isStoriesView() || isArchivedOnlyStoriesView();
        if (archivedStoriesAdapter.poller != null) {
            archivedStoriesAdapter.poller.start(pollerEnabled && getClosestTab() == TAB_ARCHIVED_STORIES);
        }
        if (storiesAdapter.poller != null) {
            storiesAdapter.poller.start(pollerEnabled && getClosestTab() == TAB_STORIES);
        }
    }

    protected boolean canShowSearchItem() {
        return true;
    }

    protected void onSearchStateChanged(boolean expanded) {

    }

    protected boolean onMemberClick(TLRPC.ChatParticipant participant, boolean isLong, View view) {
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
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.storiesListUpdated);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.storiesUpdated);

        if (storiesAdapter != null && storiesAdapter.storiesList != null) {
            storiesAdapter.destroy();
        }
        if (archivedStoriesAdapter != null && archivedStoriesAdapter.storiesList != null) {
            archivedStoriesAdapter.destroy();
        }
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
        if (hasMedia[0] >= 0) {
            loadFastScrollData(false);
        }
    }

    private void loadFastScrollData(boolean force) {
        if (topicId != 0) {
            return;
        }
        for (int k = 0; k < supportedFastScrollTypes.length; k++) {
            int type = supportedFastScrollTypes[k];
            if ((sharedMediaData[type].fastScrollDataLoaded && !force) || DialogObject.isEncryptedDialog(dialog_id)) {
                return;
            }
            sharedMediaData[type].fastScrollDataLoaded = false;
            TLRPC.TL_messages_getSearchResultsPositions req = new TLRPC.TL_messages_getSearchResultsPositions();
            if (type == 0) {
                if (sharedMediaData[type].filterType == FILTER_PHOTOS_ONLY) {
                    req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
                } else if (sharedMediaData[type].filterType == FILTER_VIDEOS_ONLY) {
                    req.filter = new TLRPC.TL_inputMessagesFilterVideo();
                } else {
                    req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
                }
            } else if (type == MediaDataController.MEDIA_FILE) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (type == MediaDataController.MEDIA_AUDIO) {
                req.filter = new TLRPC.TL_inputMessagesFilterRoundVoice();
            } else {
                req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            }
            req.limit = 100;
            req.peer = MessagesController.getInstance(profileActivity.getCurrentAccount()).getInputPeer(dialog_id);
            int reqIndex = sharedMediaData[type].requestIndex;
            int reqId = ConnectionsManager.getInstance(profileActivity.getCurrentAccount()).sendRequest(req, (response, error) ->
                    AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(profileActivity.getCurrentAccount()).doOnIdle(() -> {
                if (error != null) {
                    return;
                }
                if (reqIndex != sharedMediaData[type].requestIndex) {
                    return;
                }
                TLRPC.TL_messages_searchResultsPositions res = (TLRPC.TL_messages_searchResultsPositions) response;
                sharedMediaData[type].fastScrollPeriods.clear();
                for (int i = 0, n = res.positions.size(); i < n; i++) {
                    TLRPC.TL_searchResultPosition serverPeriod = res.positions.get(i);
                    if (serverPeriod.date != 0) {
                        Period period = new Period(serverPeriod);
                        sharedMediaData[type].fastScrollPeriods.add(period);
                    }
                }
                Collections.sort(sharedMediaData[type].fastScrollPeriods, (period, period2) -> period2.date - period.date);
                sharedMediaData[type].setTotalCount(res.count);
                sharedMediaData[type].fastScrollDataLoaded = true;
                if (!sharedMediaData[type].fastScrollPeriods.isEmpty()) {
                    for (int i = 0; i < mediaPages.length; i++) {
                        if (mediaPages[i].selectedType == type) {
                            mediaPages[i].fastScrollEnabled = true;
                            updateFastScrollVisibility(mediaPages[i], true);
                        }
                    }
                }
                photoVideoAdapter.notifyDataSetChanged();
            })));
            ConnectionsManager.getInstance(profileActivity.getCurrentAccount()).bindRequestToGuid(reqId, profileActivity.getClassGuid());
        }
    }


    private static void showFastScrollHint(MediaPage mediaPage, SharedMediaData[] sharedMediaData, boolean show) {
        if (show) {
            if (SharedConfig.fastScrollHintCount <= 0 || mediaPage.fastScrollHintView != null || mediaPage.fastScrollHinWasShown || mediaPage.listView.getFastScroll() == null || !mediaPage.listView.getFastScroll().isVisible || mediaPage.listView.getFastScroll().getVisibility() != View.VISIBLE || sharedMediaData[0].totalCount < 50) {
                return;
            }
            SharedConfig.setFastScrollHintCount(SharedConfig.fastScrollHintCount - 1);
            mediaPage.fastScrollHinWasShown = true;
            SharedMediaFastScrollTooltip tooltip = new SharedMediaFastScrollTooltip(mediaPage.getContext());
            mediaPage.fastScrollHintView = tooltip;
            mediaPage.addView(mediaPage.fastScrollHintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            mediaPage.fastScrollHintView.setAlpha(0);
            mediaPage.fastScrollHintView.setScaleX(0.8f);
            mediaPage.fastScrollHintView.setScaleY(0.8f);
            mediaPage.fastScrollHintView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
            mediaPage.invalidate();

            AndroidUtilities.runOnUIThread(mediaPage.fastScrollHideHintRunnable = () -> {
                mediaPage.fastScrollHintView = null;
                mediaPage.fastScrollHideHintRunnable = null;
                tooltip.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(220).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (tooltip.getParent() != null) {
                            ((ViewGroup) tooltip.getParent()).removeView(tooltip);
                        }
                    }
                }).start();
            }, 4000);
        } else {
            if (mediaPage.fastScrollHintView == null || mediaPage.fastScrollHideHintRunnable == null) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(mediaPage.fastScrollHideHintRunnable);
            mediaPage.fastScrollHideHintRunnable.run();
            mediaPage.fastScrollHideHintRunnable = null;
            mediaPage.fastScrollHintView = null;
        }
    }

    public void setCommonGroupsCount(int count) {
        if (topicId == 0) {
            hasMedia[6] = count;
        }
        updateTabs(true);
        checkCurrentTabValid();
    }

    public void onActionBarItemClick(View v, int id) {
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
            }, null, resourcesProvider);
        } else if (id == forward) {
            if (info != null) {
                TLRPC.Chat chat = profileActivity.getMessagesController().getChat(info.id);
                if (profileActivity.getMessagesController().isChatNoForwards(chat)) {
                    if (fwdRestrictedHint != null) {
                        fwdRestrictedHint.setText(ChatObject.isChannel(chat) && !chat.megagroup ? LocaleController.getString("ForwardsRestrictedInfoChannel", R.string.ForwardsRestrictedInfoChannel) :
                                LocaleController.getString("ForwardsRestrictedInfoGroup", R.string.ForwardsRestrictedInfoGroup));
                        fwdRestrictedHint.showForView(v, true);
                    }
                    return;
                }
            }
            if (hasNoforwardsMessage()) {
                if (fwdRestrictedHint != null) {
                    fwdRestrictedHint.setText(LocaleController.getString("ForwardsRestrictedInfoBot", R.string.ForwardsRestrictedInfoBot));
                    fwdRestrictedHint.showForView(v, true);
                }
                return;
            }

            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("canSelectTopics", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, topicsFragment) -> {
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

                if (dids.size() > 1 || dids.get(0).dialogId == profileActivity.getUserConfig().getClientUserId() || message != null) {
                    updateRowsSelection(true);
                    for (int a = 0; a < dids.size(); a++) {
                        long did = dids.get(a).dialogId;
                        if (message != null) {
                            profileActivity.getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(message.toString(), did, null, null, null, true, null, null, null, true, 0, null, false));
                        }
                        profileActivity.getSendMessagesHelper().sendMessage(fmessages, did, false, false, true, 0);
                    }
                    fragment1.finishFragment();
                    UndoView undoView = null;
                    if (profileActivity instanceof ProfileActivity) {
                        undoView = ((ProfileActivity) profileActivity).getUndoView();
                    }
                    if (undoView != null) {
                        if (dids.size() == 1) {
                            undoView.showWithAction(dids.get(0).dialogId, UndoView.ACTION_FWD_MESSAGES, fmessages.size());
                        } else {
                            undoView.showWithAction(0, UndoView.ACTION_FWD_MESSAGES, fmessages.size(), dids.size(), null, null);
                        }
                    }
                } else {
                    long did = dids.get(0).dialogId;
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
                            return true;
                        }
                    }

                    profileActivity.getNotificationCenter().postNotificationName(NotificationCenter.closeChats);

                    ChatActivity chatActivity = new ChatActivity(args1);
                    ForumUtilities.applyTopic(chatActivity, dids.get(0));
                    fragment1.presentFragment(chatActivity, true);
                    chatActivity.showFieldPanelForForward(true, fmessages);
                }
                return true;
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
            ChatActivity chatActivity = new ChatActivity(args);
            chatActivity.highlightMessageId = messageObject.getId();
            if (topicId != 0) {
                ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(dialogId, topicId));
                args.putInt("message_id", messageObject.getId());
            }
            profileActivity.presentFragment(chatActivity, false);
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
                    searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                }
                searchItemState = 0;
            }
        } else {
            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
            searchItem.setAlpha(0.0f);
        }

        getParent().requestDisallowInterceptTouchEvent(true);
        hideFloatingDateView(true);
        maybeStartTracking = false;
        startedTracking = true;
        onTabScroll(true);
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
        onTabProgress(getTabProgress());
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
        if (fragmentContextView != null) {
            fragmentContextView.setTranslationY(AndroidUtilities.dp(48) + top);
        }
        additionalFloatingTranslation = top;
        floatingDateView.setTranslationY((floatingDateView.getTag() == null ? -AndroidUtilities.dp(48) : 0) + additionalFloatingTranslation);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = delegate.getListView() != null ? delegate.getListView().getHeight() : 0;
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
                ((MediaPage) child).listView.setPadding(0, ((MediaPage) child).listView.topPadding, 0, topPadding);
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
            onTabProgress(getTabProgress());
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

    private boolean disableScrolling;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (disableScrolling) {
            return false;
        }
        if (profileActivity.getParentLayout() != null && !profileActivity.getParentLayout().checkTransitionAnimation() && !checkTabsAnimationInProgress() && !isInPinchToZoomTouchMode) {
            if (ev != null) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(ev);

                if (fwdRestrictedHint != null) {
                    fwdRestrictedHint.hide();
                }
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
                        onTabScroll(false);
                        mediaPages[0].setTranslationX(0);
                        mediaPages[1].setTranslationX(animatingForward ? mediaPages[0].getMeasuredWidth() : -mediaPages[0].getMeasuredWidth());
                        scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, 0);
                        onTabProgress(getTabProgress());
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

                        float photoVideoOptionsAlpha = getPhotoVideoOptionsAlpha(scrollProgress);
                        photoVideoOptionsItem.setAlpha(photoVideoOptionsAlpha);
                        photoVideoOptionsItem.setVisibility((photoVideoOptionsAlpha == 0  || !canShowSearchItem() || isArchivedOnlyStoriesView()) ? INVISIBLE : View.VISIBLE);
                    } else {
                        searchItem.setAlpha(0.0f);
                    }
                    scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress);
                    onTabProgress(getTabProgress());
                    onSelectedTabChanged();
                }
            } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                stopScroll(ev);
            }
            return startedTracking;
        }
        return false;
    }

    public void scrollToPage(int page) {
        if (disableScrolling || scrollSlidingTextTabStrip == null) {
            return;
        }
        scrollSlidingTextTabStrip.scrollTo(page);
    }

    private void stopScroll(MotionEvent ev) {
        if (velocityTracker == null) {
            return;
        }
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
            float dx;
            ValueAnimator invalidate = ValueAnimator.ofFloat(0, 1);
            invalidate.addUpdateListener(anm -> onTabProgress(getTabProgress()));
            if (backAnimation) {
                dx = Math.abs(x);
                if (animatingForward) {
                    tabsAnimation.playTogether(
                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, 0),
                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, mediaPages[1].getMeasuredWidth()),
                            invalidate
                    );
                } else {
                    tabsAnimation.playTogether(
                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, 0),
                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, -mediaPages[1].getMeasuredWidth()),
                            invalidate
                    );
                }
            } else {
                dx = mediaPages[0].getMeasuredWidth() - Math.abs(x);
                if (animatingForward) {
                    tabsAnimation.playTogether(
                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, -mediaPages[0].getMeasuredWidth()),
                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, 0),
                            invalidate
                    );
                } else {
                    tabsAnimation.playTogether(
                            ObjectAnimator.ofFloat(mediaPages[0], View.TRANSLATION_X, mediaPages[0].getMeasuredWidth()),
                            ObjectAnimator.ofFloat(mediaPages[1], View.TRANSLATION_X, 0),
                            invalidate
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
                                searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                            }
                        } else {
                            searchItem.setVisibility(isStoriesView() ? View.GONE : INVISIBLE);
                            searchItem.setAlpha(0.0f);
                        }
                        searchItemState = 0;
                    } else {
                        MediaPage tempPage = mediaPages[0];
                        mediaPages[0] = mediaPages[1];
                        mediaPages[1] = tempPage;
                        mediaPages[1].setVisibility(View.GONE);
                        if (searchItemState == 2) {
                            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                        }
                        searchItemState = 0;
                        scrollSlidingTextTabStrip.selectTabWithId(mediaPages[0].selectedType, 1.0f);
                        onSelectedTabChanged();
                        startStopVisibleGifs();
                    }
                    tabsAnimationInProgress = false;
                    maybeStartTracking = false;
                    startedTracking = false;
                    onTabScroll(false);
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

    public void disableScroll(boolean disable) {
        if (disable) {
            stopScroll(null);
        }
        disableScrolling = disable;
    }

    public boolean closeActionMode() {
        return closeActionMode(true);
    }

    public boolean closeActionMode(boolean uncheckAnimated) {
        if (isActionModeShowed) {
            for (int a = 1; a >= 0; a--) {
                selectedFiles[a].clear();
            }
            cantDeleteMessagesCount = 0;
            showActionMode(false);
            updateRowsSelection(uncheckAnimated);
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

    protected void onActionModeSelectedUpdate(SparseArray<MessageObject> messageObjects) {

    }

    private AnimatorSet actionModeAnimation;

    public boolean isActionModeShown() {
        return isActionModeShowed;
    }

    protected void showActionMode(boolean show) {
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
            int requestIndex = (Integer) args[7];
            int type = (Integer) args[4];
            boolean fromStart = (boolean) args[6];

            if (type == 6 || type == 7) {
                type = 0;
            }

            if (guid == profileActivity.getClassGuid() && requestIndex == sharedMediaData[type].requestIndex) {
                if (type != 0 && type != 1 && type != 2 && type != 4) {
                    sharedMediaData[type].totalCount = (Integer) args[1];
                }
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
                int oldMessagesCount = sharedMediaData[type].messages.size();
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

                SparseBooleanArray addedMesages = new SparseBooleanArray();

                if (fromStart) {
                    for (int a = arr.size() - 1; a >= 0; a--) {
                        MessageObject message = arr.get(a);
                        boolean added = sharedMediaData[type].addMessage(message, loadIndex, true, enc);
                        if (added) {
                            addedMesages.put(message.getId(), true);
                            sharedMediaData[type].startOffset--;
                            if (sharedMediaData[type].startOffset < 0) {
                                sharedMediaData[type].startOffset = 0;
                            }
                        }
                    }
                    sharedMediaData[type].startReached = (Boolean) args[5];
                    if (sharedMediaData[type].startReached) {
                        sharedMediaData[type].startOffset = 0;
                    }
                } else {
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject message = arr.get(a);
                        if (sharedMediaData[type].addMessage(message, loadIndex, false, enc)) {
                            addedMesages.put(message.getId(), true);
                            sharedMediaData[type].endLoadingStubs--;
                            if (sharedMediaData[type].endLoadingStubs < 0) {
                                sharedMediaData[type].endLoadingStubs = 0;
                            }
                        }
                    }
                    if (sharedMediaData[type].loadingAfterFastScroll && sharedMediaData[type].messages.size() > 0) {
                        sharedMediaData[type].min_id = sharedMediaData[type].messages.get(0).getId();
                    }
                    sharedMediaData[type].endReached[loadIndex] = (Boolean) args[5];
                    if (sharedMediaData[type].endReached[loadIndex]) {
                        sharedMediaData[type].totalCount = sharedMediaData[type].messages.size() + sharedMediaData[type].startOffset;
                    }
                }
                if (!fromStart && loadIndex == 0 && sharedMediaData[type].endReached[loadIndex] && mergeDialogId != 0) {
                    sharedMediaData[type].loading = true;
                    profileActivity.getMediaDataController().loadMedia(mergeDialogId, 50, sharedMediaData[type].max_id[1], 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[type].requestIndex);
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
                    if (adapter == photoVideoAdapter) {
                        if (photoVideoAdapter.getItemCount() == oldItemCount) {
                            AndroidUtilities.updateVisibleRows(listView);
                        } else {
                            photoVideoAdapter.notifyDataSetChanged();
                        }
                    } else {
                        try {
                            adapter.notifyDataSetChanged();
                        } catch (Throwable e) {

                        }
                    }
                    if (sharedMediaData[type].messages.isEmpty() && !sharedMediaData[type].loading) {
                        if (listView != null) {
                            animateItemsEnter(listView, oldItemCount, addedMesages);
                        }
                    } else {
                        if (listView != null && (adapter == photoVideoAdapter || newItemCount >= oldItemCount)) {
                            animateItemsEnter(listView, oldItemCount, addedMesages);
                        }
                    }
                    if (listView != null && !sharedMediaData[type].loadingAfterFastScroll) {
                        if (oldMessagesCount == 0) {
                            for (int k = 0; k < 2; k++) {
                                if (mediaPages[k].selectedType == 0) {
                                    int position = photoVideoAdapter.getPositionForIndex(0);
                                    ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(position, 0);
                                }
                            }
                        } else {
                            saveScrollPosition();
                        }
                    }
                }
                if (sharedMediaData[type].loadingAfterFastScroll) {
                    if (sharedMediaData[type].messages.size() == 0) {
                        loadFromStart(type);
                    } else {
                        sharedMediaData[type].loadingAfterFastScroll = false;
                    }
                }
                scrolling = true;
            } else if (sharedMediaPreloader != null && sharedMediaData[type].messages.isEmpty() && !sharedMediaData[type].loadingAfterFastScroll) {
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
            int type = -1;
            for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                for (int b = 0; b < sharedMediaData.length; b++) {
                    if (sharedMediaData[b].deleteMessage(markAsDeletedMessages.get(a), loadIndex) != null) {
                        type = b;
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

                if (type == 0 ||  type == 1 || type == 2 || type == 4) {
                    loadFastScrollData(true);
                }
            }
            MediaPage mediaPage = getMediaPage(type);
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
                    if (MessageObject.getMedia(obj.messageOwner) == null || obj.needDrawBluredPreview()) {
                        continue;
                    }
                    int type = MediaDataController.getMediaType(obj.messageOwner);
                    if (type == -1) {
                        return;
                    }
                    if (sharedMediaData[type].startReached && sharedMediaData[type].addMessage(obj, obj.getDialogId() == dialog_id ? 0 : 1, true, enc)) {
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
        } else if (id == NotificationCenter.storiesListUpdated) {
            StoriesController.StoriesList list = (StoriesController.StoriesList) args[0];
            if (storiesAdapter != null && list == storiesAdapter.storiesList) {
                MediaPage page = getMediaPage(TAB_STORIES);
                if (page != null && page.fastScrollEnabled != (list.getCount() > 0)) {
                    page.fastScrollEnabled = list.getCount() > 0;
                    updateFastScrollVisibility(page, true);
                }
                storiesAdapter.notifyDataSetChanged();
                if (delegate != null) {
                    delegate.updateSelectedMediaTabText();
                }
            }
            if (archivedStoriesAdapter != null && list == archivedStoriesAdapter.storiesList) {
                MediaPage page = getMediaPage(TAB_ARCHIVED_STORIES);
                if (page != null && page.fastScrollEnabled != (list.getCount() > 0)) {
                    page.fastScrollEnabled = list.getCount() > 0;
                    updateFastScrollVisibility(page, true);
                }
                archivedStoriesAdapter.notifyDataSetChanged();
                if (delegate != null) {
                    delegate.updateSelectedMediaTabText();
                }
            }
        } else if (id == NotificationCenter.storiesUpdated) {
            for (int i = 0; i < mediaPages.length; ++i) {
                if (mediaPages[i] != null && mediaPages[i].listView != null && (mediaPages[i].selectedType == TAB_STORIES || mediaPages[i].selectedType == TAB_ARCHIVED_STORIES)) {
                    for (int j = 0; j < mediaPages[i].listView.getChildCount(); ++j) {
                        View child = mediaPages[i].listView.getChildAt(j);
                        if (child instanceof SharedPhotoVideoCell2) {
                            ((SharedPhotoVideoCell2) child).updateViews();
                        }
                    }
                }
            }
        }
    }

    private void saveScrollPosition() {
        for (int k = 0; k < mediaPages.length; k++) {
            RecyclerListView listView = mediaPages[k].listView;
            if (listView != null) {
                int messageId = 0;
                int offset = 0;
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (child instanceof SharedPhotoVideoCell2) {
                        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) child;
                        messageId = cell.getMessageId();
                        offset = cell.getTop();
                    }
                    if (child instanceof SharedDocumentCell) {
                        SharedDocumentCell cell = (SharedDocumentCell) child;
                        messageId = cell.getMessage().getId();
                        offset = cell.getTop();
                    }
                    if (child instanceof SharedAudioCell) {
                        SharedAudioCell cell = (SharedAudioCell) child;
                        messageId = cell.getMessage().getId();
                        offset = cell.getTop();
                    }
                    if (messageId != 0) {
                        break;
                    }
                }
                if (messageId != 0) {
                    int index = -1, position = -1;
                    final int type = mediaPages[k].selectedType;
                    if (type == TAB_STORIES || type == TAB_ARCHIVED_STORIES) {
                        StoriesAdapter adapter = type == TAB_STORIES ? storiesAdapter : archivedStoriesAdapter;
                        if (adapter.storiesList != null) {
                            for (int i = 0; i < adapter.storiesList.messageObjects.size(); ++i) {
                                if (messageId == adapter.storiesList.messageObjects.get(i).getId()) {
                                    index = i;
                                    break;
                                }
                            }
                        }
                        position = index;
                    } else if (type >= 0 && type < sharedMediaData.length) {
                        for (int i = 0; i < sharedMediaData[type].messages.size(); i++) {
                            if (messageId == sharedMediaData[type].messages.get(i).getId()) {
                                index = i;
                                break;
                            }
                        }
                        position = sharedMediaData[type].startOffset + index;
                    } else {
                        continue;
                    }
                    if (index >= 0) {
                        ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(position, -mediaPages[k].listView.getPaddingTop() + offset);
                        if (photoVideoChangeColumnsAnimation) {
                            mediaPages[k].animationSupportingLayoutManager.scrollToPositionWithOffset(position, -mediaPages[k].listView.getPaddingTop() + offset);
                        }
                    }
                }
            }
        }
    }

    SparseArray<Float> messageAlphaEnter = new SparseArray<>();

    private void animateItemsEnter(final RecyclerListView finalListView, int oldItemCount, SparseBooleanArray addedMesages) {
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
                RecyclerView.Adapter adapter = finalListView.getAdapter();
                if (adapter == photoVideoAdapter || adapter == documentsAdapter || adapter == audioAdapter || adapter == voiceAdapter) {
                    if (addedMesages != null) {
                        int n = finalListView.getChildCount();
                        for (int i = 0; i < n; i++) {
                            View child = finalListView.getChildAt(i);
                            int messageId = getMessageId(child);
                            if (messageId != 0 && addedMesages.get(messageId, false)) {
                                messageAlphaEnter.put(messageId, 0f);
                                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
                                valueAnimator.addUpdateListener(valueAnimator1 -> {
                                    messageAlphaEnter.put(messageId, (Float) valueAnimator1.getAnimatedValue());
                                    finalListView.invalidate();
                                });
                                valueAnimator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        messageAlphaEnter.remove(messageId);
                                        finalListView.invalidate();
                                    }
                                });
                                int s = Math.min(finalListView.getMeasuredHeight(), Math.max(0, child.getTop()));
                                int delay = (int) ((s / (float) finalListView.getMeasuredHeight()) * 100);
                                valueAnimator.setStartDelay(delay);
                                valueAnimator.setDuration(250);
                                valueAnimator.start();
                            }
                            finalListView.invalidate();
                        }
                    }
                } else {
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
                }
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
        boolean stories_pinned_available = this.info != null && this.info.stories_pinned_available;
        info = chatInfo;
        if (info != null && info.migrated_from_chat_id != 0 && mergeDialogId == 0) {
            mergeDialogId = -info.migrated_from_chat_id;
            for (int a = 0; a < sharedMediaData.length; a++) {
                sharedMediaData[a].max_id[1] = info.migrated_from_max_id;
                sharedMediaData[a].endReached[1] = false;
            }
        }
        if (info != null && (stories_pinned_available != info.stories_pinned_available)) {
            if (scrollSlidingTextTabStrip != null) {
                scrollSlidingTextTabStrip.setInitialTabId(isArchivedOnlyStoriesView() ? TAB_ARCHIVED_STORIES : TAB_STORIES);
            }
            updateTabs(true);
            switchToCurrentSelectedMode(false);
        }
    }

    public void setUserInfo(TLRPC.UserFull userInfo) {
        boolean stories_pinned_available = this.userInfo != null && this.userInfo.stories_pinned_available;
        this.userInfo = userInfo;
        updateTabs(true);
        if (userInfo != null && (stories_pinned_available != userInfo.stories_pinned_available)) {
            scrollToPage(TAB_STORIES);
        }
    }

    public void setChatUsers(ArrayList<Integer> sortedUsers, TLRPC.ChatFull chatInfo) {
        for (int a = 0; a < mediaPages.length; a++) {
            if (mediaPages[a].selectedType == TAB_GROUPUSERS) {
                if (mediaPages[a].listView.getAdapter().getItemCount() != 0 && profileActivity.getMessagesController().getStoriesController().hasLoadingStories()) {
                    return;
                }
            }
        }
        if (topicId == 0) {
            chatUsersAdapter.chatInfo = chatInfo;
            chatUsersAdapter.sortedUsers = sortedUsers;
        }
        updateTabs(true);
        for (int a = 0; a < mediaPages.length; a++) {
            if (mediaPages[a].selectedType == TAB_GROUPUSERS) {
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
        if (storiesAdapter != null) {
            storiesAdapter.notifyDataSetChanged();
        }
    }

    private void updateRowsSelection(boolean animated) {
        for (int i = 0; i < mediaPages.length; i++) {
            int count = mediaPages[i].listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = mediaPages[i].listView.getChildAt(a);
                if (child instanceof SharedDocumentCell) {
                    ((SharedDocumentCell) child).setChecked(false, animated);
                } else if (child instanceof SharedPhotoVideoCell2) {
                    ((SharedPhotoVideoCell2) child).setChecked(false, animated);
                } else if (child instanceof SharedLinkCell) {
                    ((SharedLinkCell) child).setChecked(false, animated);
                } else if (child instanceof SharedAudioCell) {
                    ((SharedAudioCell) child).setChecked(false, animated);
                } else if (child instanceof ContextLinkCell) {
                    ((ContextLinkCell) child).setChecked(false, animated);
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
        if (!delegate.isFragmentOpened()) {
            animated = false;
        }
        int changed = 0;
        if (((DialogObject.isUserDialog(dialog_id) || DialogObject.isChatDialog(dialog_id)) && !DialogObject.isEncryptedDialog(dialog_id) && (userInfo != null && userInfo.stories_pinned_available || info != null && info.stories_pinned_available || isStoriesView()) && includeStories()) != scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
            changed++;
        }
        if (!isStoriesView()) {
            if ((chatUsersAdapter.chatInfo == null) == scrollSlidingTextTabStrip.hasTab(TAB_GROUPUSERS)) {
                changed++;
            }
            if ((hasMedia[0] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_PHOTOVIDEO)) {
                changed++;
            }
            if ((hasMedia[1] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_FILES)) {
                changed++;
            }
            if (!DialogObject.isEncryptedDialog(dialog_id)) {
                if ((hasMedia[3] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_LINKS)) {
                    changed++;
                }
                if ((hasMedia[4] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_AUDIO)) {
                    changed++;
                }
            } else {
                if ((hasMedia[4] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_AUDIO)) {
                    changed++;
                }
            }
            if ((hasMedia[2] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_VOICE)) {
                changed++;
            }
            if ((hasMedia[5] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_GIF)) {
                changed++;
            }
            if ((hasMedia[6] <= 0) == scrollSlidingTextTabStrip.hasTab(TAB_COMMON_GROUPS)) {
                changed++;
            }
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
            if ((DialogObject.isUserDialog(dialog_id) || DialogObject.isChatDialog(dialog_id)) && !DialogObject.isEncryptedDialog(dialog_id) && (userInfo != null && userInfo.stories_pinned_available || info != null && info.stories_pinned_available || isStoriesView()) && includeStories()) {
                if (isArchivedOnlyStoriesView()) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_ARCHIVED_STORIES)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_ARCHIVED_STORIES, LocaleController.getString("ProfileStories", R.string.ProfileStories), idToView);
                    }
                    scrollSlidingTextTabStrip.animationDuration = 420;
                } else {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_STORIES, LocaleController.getString("ProfileStories", R.string.ProfileStories), idToView);
                    }
                    if (isStoriesView()) {
                        if (!scrollSlidingTextTabStrip.hasTab(TAB_ARCHIVED_STORIES)) {
                            scrollSlidingTextTabStrip.addTextTab(TAB_ARCHIVED_STORIES, LocaleController.getString("ProfileStories", R.string.ProfileStories), idToView);
                        }
                        scrollSlidingTextTabStrip.animationDuration = 420;
                    }
                }
            }
            if (!isStoriesView()) {
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
        }
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            mediaPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
        onSelectedTabChanged();
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
        FrameLayout.LayoutParams layoutParams = (LayoutParams) mediaPages[a].getLayoutParams();
        // layoutParams.leftMargin = layoutParams.rightMargin = 0;
        boolean fastScrollVisible = false;
        int spanCount = 100;
        RecyclerView.Adapter currentAdapter = mediaPages[a].listView.getAdapter();
        RecyclerView.RecycledViewPool viewPool = null;
        if (searching && searchWas) {
            if (animated) {
                if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2 || mediaPages[a].selectedType == 5 || mediaPages[a].selectedType == 6 || mediaPages[a].selectedType == 7 && !delegate.canSearchMembers()) {
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
            mediaPages[a].listView.setPadding(
                mediaPages[a].listView.getPaddingLeft(),
                AndroidUtilities.dp(2) + (mediaPages[a].listView.hintPaddingTop = mediaPages[a].selectedType == TAB_ARCHIVED_STORIES ? AndroidUtilities.dp(64) : 0),
                mediaPages[a].listView.getPaddingRight(),
                mediaPages[a].listView.getPaddingBottom()
            );

            if (mediaPages[a].selectedType == TAB_PHOTOVIDEO) {
                if (currentAdapter != photoVideoAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(photoVideoAdapter);
                }
                layoutParams.leftMargin = layoutParams.rightMargin = -AndroidUtilities.dp(1);
                if (sharedMediaData[0].fastScrollDataLoaded && !sharedMediaData[0].fastScrollPeriods.isEmpty()) {
                    fastScrollVisible = true;
                }
                spanCount = mediaColumnsCount[0];
                mediaPages[a].listView.setPinnedHeaderShadowDrawable(pinnedHeaderShadowDrawable);
                if (sharedMediaData[0].recycledViewPool == null) {
                    sharedMediaData[0].recycledViewPool = new RecyclerView.RecycledViewPool();
                }
                viewPool = sharedMediaData[0].recycledViewPool;
            } else if (mediaPages[a].selectedType == TAB_FILES) {
                if (sharedMediaData[1].fastScrollDataLoaded && !sharedMediaData[1].fastScrollPeriods.isEmpty()) {
                    fastScrollVisible = true;
                }
                if (currentAdapter != documentsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(documentsAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_VOICE) {
                if (sharedMediaData[2].fastScrollDataLoaded && !sharedMediaData[2].fastScrollPeriods.isEmpty()) {
                    fastScrollVisible = true;
                }
                if (currentAdapter != voiceAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(voiceAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_LINKS) {
                if (currentAdapter != linksAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(linksAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_AUDIO) {
                if (sharedMediaData[4].fastScrollDataLoaded && !sharedMediaData[4].fastScrollPeriods.isEmpty()) {
                    fastScrollVisible = true;
                }
                if (currentAdapter != audioAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(audioAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_GIF) {
                if (currentAdapter != gifAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(gifAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_COMMON_GROUPS) {
                if (currentAdapter != commonGroupsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(commonGroupsAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_GROUPUSERS) {
                if (currentAdapter != chatUsersAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(chatUsersAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_STORIES) {
                if (currentAdapter != storiesAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(storiesAdapter);
                }
                spanCount = mediaColumnsCount[1];
            } else if (mediaPages[a].selectedType == TAB_ARCHIVED_STORIES) {
                if (currentAdapter != archivedStoriesAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(archivedStoriesAdapter);
                }
                spanCount = mediaColumnsCount[1];
            }
            if (mediaPages[a].selectedType == TAB_PHOTOVIDEO || mediaPages[a].selectedType == TAB_STORIES || mediaPages[a].selectedType == TAB_ARCHIVED_STORIES || mediaPages[a].selectedType == TAB_VOICE || mediaPages[a].selectedType == TAB_GIF || mediaPages[a].selectedType == TAB_COMMON_GROUPS || mediaPages[a].selectedType == TAB_GROUPUSERS && !delegate.canSearchMembers()) {
                if (animated) {
                    searchItemState = 2;
                } else {
                    searchItemState = 0;
                    searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                }
            } else {
                if (animated) {
                    if (searchItem.getVisibility() == View.INVISIBLE && !actionBar.isSearchFieldVisible()) {
                        if (canShowSearchItem()) {
                            searchItemState = 1;
                            searchItem.setVisibility(View.VISIBLE);
                        } else {
                            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
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
                        searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                        searchItem.setAlpha(0.0f);
                    }
                }
            }
            if (mediaPages[a].selectedType == 6) {
                if (!commonGroupsAdapter.loading && !commonGroupsAdapter.endReached && commonGroupsAdapter.chats.isEmpty()) {
                    commonGroupsAdapter.getChats(0, 100);
                }
            } else if (mediaPages[a].selectedType == 7) {

            } else if (mediaPages[a].selectedType == TAB_STORIES) {
                StoriesController.StoriesList storiesList = storiesAdapter.storiesList;
                storiesAdapter.load(false);
                mediaPages[a].emptyView.showProgress(storiesList != null && (storiesList.isLoading() || hasInternet() && storiesList.getCount() > 0), animated);
                fastScrollVisible = storiesList != null && storiesList.getCount() > 0;
            } else if (mediaPages[a].selectedType == TAB_ARCHIVED_STORIES) {
                StoriesController.StoriesList storiesList = archivedStoriesAdapter.storiesList;
                archivedStoriesAdapter.load(false);
                mediaPages[a].emptyView.showProgress(storiesList != null && (storiesList.isLoading() || hasInternet() && storiesList.getCount() > 0), animated);
                fastScrollVisible = storiesList != null && storiesList.getCount() > 0;
            } else {
                if (!sharedMediaData[mediaPages[a].selectedType].loading && !sharedMediaData[mediaPages[a].selectedType].endReached[0] && sharedMediaData[mediaPages[a].selectedType].messages.isEmpty()) {
                    sharedMediaData[mediaPages[a].selectedType].loading = true;
                    documentsAdapter.notifyDataSetChanged();
                    int type = mediaPages[a].selectedType;
                    if (type == 0) {
                        if (sharedMediaData[0].filterType == FILTER_PHOTOS_ONLY) {
                            type = MediaDataController.MEDIA_PHOTOS_ONLY;
                        } else if (sharedMediaData[0].filterType == FILTER_VIDEOS_ONLY) {
                            type = MediaDataController.MEDIA_VIDEOS_ONLY;
                        }
                    }
                    profileActivity.getMediaDataController().loadMedia(dialog_id, 50, 0, 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[mediaPages[a].selectedType].requestIndex);
                }
            }
            if (mediaPages[a].selectedType == TAB_STORIES) {
                mediaPages[a].emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_ALBUM);
                mediaPages[a].emptyView.title.setText(isStoriesView() ? LocaleController.getString(R.string.NoPublicStoriesTitle) : LocaleController.getString(R.string.NoStoriesTitle));
                mediaPages[a].emptyView.subtitle.setText(isStoriesView() ? LocaleController.getString("NoStoriesSubtitle") : "");
            } else if (mediaPages[a].selectedType == TAB_ARCHIVED_STORIES) {
                mediaPages[a].emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_ALBUM);
                mediaPages[a].emptyView.title.setText(LocaleController.getString("NoArchivedStoriesTitle"));
                mediaPages[a].emptyView.subtitle.setText(isStoriesView() ? LocaleController.getString("NoArchivedStoriesSubtitle") : "");
            } else {
                mediaPages[a].emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_SEARCH);
                mediaPages[a].emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
                mediaPages[a].emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
            }
            mediaPages[a].listView.setVisibility(View.VISIBLE);
        }
        mediaPages[a].fastScrollEnabled = fastScrollVisible;
        updateFastScrollVisibility(mediaPages[a], false);
        mediaPages[a].layoutManager.setSpanCount(spanCount);
        mediaPages[a].listView.invalidateItemDecorations();
        mediaPages[a].listView.setRecycledViewPool(viewPool);
        mediaPages[a].animationSupportingListView.setRecycledViewPool(viewPool);

        if (searchItemState == 2 && actionBar.isSearchFieldVisible()) {
            ignoreSearchCollapse = true;
            actionBar.closeSearchField();
            searchItemState = 0;
            searchItem.setAlpha(0.0f);
            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
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
        } else if (view instanceof SharedPhotoVideoCell2) {
            ((SharedPhotoVideoCell2) view).setChecked(true, true);
        }
        if (!isActionModeShowed) {
            showActionMode(true);
        }
        onActionModeSelectedUpdate(selectedFiles[0]);
        updateForwardItem();
        return true;
    }

    private void onItemClick(int index, View view, MessageObject message, int a, int selectedMode) {
        if (message == null || photoVideoChangeColumnsAnimation) {
            return;
        }
        if (isActionModeShowed) {
            if (selectedMode == TAB_STORIES && !isStoriesView()) {
                return;
            }
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
            onActionModeSelectedUpdate(selectedFiles[0]);
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
            } else if (view instanceof SharedPhotoVideoCell2) {
                ((SharedPhotoVideoCell2) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            }
        } else {
            if (selectedMode == 0) {
                int i = index - sharedMediaData[selectedMode].startOffset;
                if (i >= 0 && i < sharedMediaData[selectedMode].messages.size()) {
                    PhotoViewer.getInstance().setParentActivity(profileActivity);
                    PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, i, dialog_id, mergeDialogId, topicId, provider);
                }
            } else if (selectedMode == 2 || selectedMode == 4) {
                if (view instanceof SharedAudioCell) {
                    ((SharedAudioCell) view).didPressedButton();
                }
            } else if (selectedMode == 5) {
                PhotoViewer.getInstance().setParentActivity(profileActivity);
                index = sharedMediaData[selectedMode].messages.indexOf(message);
                if (index < 0) {
                    ArrayList<MessageObject> documents = new ArrayList<>();
                    documents.add(message);
                    PhotoViewer.getInstance().openPhoto(documents, 0, 0, 0, 0, provider);
                } else {
                    PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, topicId, provider);
                }
            } else if (selectedMode == 1) {
                if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    TLRPC.Document document = message.getDocument();
                    if (cell.isLoaded()) {
                        if (message.canPreviewDocument()) {
                            PhotoViewer.getInstance().setParentActivity(profileActivity);
                            index = sharedMediaData[selectedMode].messages.indexOf(message);
                            if (index < 0) {
                                ArrayList<MessageObject> documents = new ArrayList<>();
                                documents.add(message);
                                PhotoViewer.getInstance().openPhoto(documents, 0, 0, 0, 0, provider);
                            } else {
                                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, topicId, provider);
                            }
                            return;
                        }
                        AndroidUtilities.openDocument(message, profileActivity.getParentActivity(), profileActivity);
                    } else if (!cell.isLoading()) {
                        MessageObject messageObject = cell.getMessage();
                        messageObject.putInDownloadsStore = true;
                        profileActivity.getFileLoader().loadFile(document, messageObject, FileLoader.PRIORITY_LOW, 0);
                        cell.updateFileExistIcon(true);
                    } else {
                        profileActivity.getFileLoader().cancelLoadFile(document);
                        cell.updateFileExistIcon(true);
                    }
                }
            } else if (selectedMode == 3) {
                try {
                    TLRPC.WebPage webPage = MessageObject.getMedia(message.messageOwner) != null ? MessageObject.getMedia(message.messageOwner).webpage : null;
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
            } else if (selectedMode == TAB_STORIES || selectedMode == TAB_ARCHIVED_STORIES) {
                StoriesController.StoriesList storiesList = (selectedMode == TAB_STORIES ? storiesAdapter : archivedStoriesAdapter).storiesList;
                if (storiesList == null) {
                    return;
                }
                profileActivity.getOrCreateStoryViewer().open(getContext(), message.getId(), storiesList, StoriesListPlaceProvider.of(mediaPages[a].listView).with(forward -> {
                    if (forward) {
                        storiesList.load(false, 30);
                    }
                }));
            }
        }
        updateForwardItem();
    }

    private void openUrl(String link) {
        if (AndroidUtilities.shouldShowUrlInAlert(link)) {
            AlertsCreator.showOpenUrlAlert(profileActivity, link, true, true);
        } else {
            Browser.openUrl(profileActivity.getParentActivity(), link);
        }
    }

    private void openWebView(TLRPC.WebPage webPage, MessageObject message) {
        EmbedBottomSheet.show(profileActivity, message, provider, webPage.site_name, webPage.description, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height, false);
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
                view.setBackgroundColor(getThemedColor(Theme.key_graySection) & 0xf2ffffff);
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
                    view = new GraySectionCell(mContext, resourcesProvider);
                    break;
                case 1:
                    view = new SharedLinkCell(mContext, SharedLinkCell.VIEW_TYPE_DEFAULT, resourcesProvider);
                    ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
                    break;
                case 3:
                    View emptyStubView = createEmptyStubView(mContext, 3, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 2:
                default:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext, resourcesProvider);
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
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = 0;
            position[1] = 0;
        }
    }

    private class SharedDocumentsAdapter extends RecyclerListView.FastScrollAdapter {

        private Context mContext;
        private int currentType;
        private boolean inFastScrollMode;

        public SharedDocumentsAdapter(Context context, int type) {
            mContext = context;
            currentType = type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            if (sharedMediaData[currentType].loadingAfterFastScroll) {
                return sharedMediaData[currentType].totalCount;
            }
            if (sharedMediaData[currentType].messages.size() == 0 && !sharedMediaData[currentType].loading) {
                return 1;
            }
            if (sharedMediaData[currentType].messages.size() == 0 && (!sharedMediaData[currentType].endReached[0] || !sharedMediaData[currentType].endReached[1]) && sharedMediaData[currentType].startReached) {
                return 0;
            }
            if (sharedMediaData[currentType].totalCount == 0) {
                int count = sharedMediaData[currentType].getStartOffset() + sharedMediaData[currentType].getMessages().size();
                if (count != 0 && (!sharedMediaData[currentType].endReached[0] || !sharedMediaData[currentType].endReached[1])) {
                    if (sharedMediaData[currentType].getEndLoadingStubs() != 0) {
                        count += sharedMediaData[currentType].getEndLoadingStubs();
                    } else {
                        count++;
                    }
                }
                return count;
            } else {
                return sharedMediaData[currentType].totalCount;
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    SharedDocumentCell cell = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_DEFAULT, resourcesProvider);
                    cell.setGlobalGradientView(globalGradientView);
                    view = cell;
                    break;
                case 2:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext, resourcesProvider);
                    view = flickerLoadingView;
                    if (currentType == 2) {
                        flickerLoadingView.setViewType(FlickerLoadingView.AUDIO_TYPE);
                    } else {
                        flickerLoadingView.setViewType(FlickerLoadingView.FILES_TYPE);
                    }
                    flickerLoadingView.showDate(false);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setGlobalGradientView(globalGradientView);
                    break;
                case 4:
                    View emptyStubView = createEmptyStubView(mContext, currentType, dialog_id, resourcesProvider);
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
                        view = new SharedAudioCell(mContext, SharedAudioCell.VIEW_TYPE_DEFAULT, resourcesProvider) {
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
                    SharedAudioCell audioCell = (SharedAudioCell) view;
                    audioCell.setGlobalGradientView(globalGradientView);
                    if (currentType == MediaDataController.MEDIA_MUSIC) {
                        audioCache.add((SharedAudioCell) view);
                    }
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ArrayList<MessageObject> messageObjects = sharedMediaData[currentType].messages;
            switch (holder.getItemViewType()) {
                case 1: {
                    SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                    MessageObject messageObject = messageObjects.get(position - sharedMediaData[currentType].startOffset);
                    sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() - 1);
                    if (isActionModeShowed) {
                        sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                    } else {
                        sharedDocumentCell.setChecked(false, !scrolling);
                    }
                    break;
                }
                case 3: {
                    SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                    MessageObject messageObject = messageObjects.get(position - sharedMediaData[currentType].startOffset);
                    sharedAudioCell.setMessageObject(messageObject, position != messageObjects.size() - 1);
                    if (isActionModeShowed) {
                        sharedAudioCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                    } else {
                        sharedAudioCell.setChecked(false, !scrolling);
                    }
                    break;
                }
            }
        }


        @Override
        public int getItemViewType(int position) {
            if (sharedMediaData[currentType].sections.size() == 0 && !sharedMediaData[currentType].loading) {
                return 4;
            }
            if (position >= sharedMediaData[currentType].startOffset && position < sharedMediaData[currentType].startOffset + sharedMediaData[currentType].messages.size()) {
                if (currentType == 2 || currentType == 4) {
                    return 3;
                } else {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            if (sharedMediaData[currentType].fastScrollPeriods == null) {
                return "";
            }
            int index = position;
            ArrayList<Period> periods = sharedMediaData[currentType].fastScrollPeriods;
            if (!periods.isEmpty()) {
                for (int i = 0; i < periods.size(); i++) {
                    if (index <= periods.get(i).startOffset) {
                        return periods.get(i).formatedDate;
                    }
                }
                return periods.get(periods.size() - 1).formatedDate;
            }
            return "";
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            int viewHeight = listView.getChildAt(0).getMeasuredHeight();
            int totalHeight = (int) getTotalItemsCount() * viewHeight;
            int listViewHeight = listView.getMeasuredHeight() - listView.getPaddingTop();
            position[0] = (int) ((progress * (totalHeight - listViewHeight)) / viewHeight);
            position[1] = (int) (progress * (totalHeight - listViewHeight)) % viewHeight;
        }

        @Override
        public void onStartFastScroll() {
            inFastScrollMode = true;
            MediaPage mediaPage = getMediaPage(currentType);
            if (mediaPage != null) {
                showFastScrollHint(mediaPage, null, false);
            }
        }

        @Override
        public void onFinishFastScroll(RecyclerListView listView) {
            if (inFastScrollMode) {
                inFastScrollMode = false;
                if (listView != null) {
                    int messageId = 0;
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        View child = listView.getChildAt(i);
                        messageId = getMessageId(child);
                        if (messageId != 0) {
                            break;
                        }
                    }
                    if (messageId == 0) {
                        findPeriodAndJumpToDate(currentType, listView, true);
                    }
                }
            }
        }

        @Override
        public int getTotalItemsCount() {
            return sharedMediaData[currentType].totalCount;
        }
    }

    public static View createEmptyStubView(Context context, int currentType, long dialog_id, Theme.ResourcesProvider resourcesProvider) {
        EmptyStubView emptyStubView = new EmptyStubView(context, resourcesProvider);
        if (currentType == 0) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoMediaSecret", R.string.NoMediaSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoMedia", R.string.NoMedia));
            }
        } else if (currentType == 1) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedFilesSecret", R.string.NoSharedFilesSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedFiles", R.string.NoSharedFiles));
            }
        } else if (currentType == 2) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedVoiceSecret", R.string.NoSharedVoiceSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedVoice", R.string.NoSharedVoice));
            }
        } else if (currentType == 3) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedLinksSecret", R.string.NoSharedLinksSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedLinks", R.string.NoSharedLinks));
            }
        } else if (currentType == 4) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedAudioSecret", R.string.NoSharedAudioSecret));
            } else {
                emptyStubView.emptyTextView.setText(LocaleController.getString("NoSharedAudio", R.string.NoSharedAudio));
            }
        } else if (currentType == 5) {
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

        public EmptyStubView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            emptyTextView = new TextView(context);
            emptyImageView = new ImageView(context);

            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER);

            addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
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

    private class SharedPhotoVideoAdapter extends RecyclerListView.FastScrollAdapter {

        protected Context mContext;
        protected boolean inFastScrollMode;
        SharedPhotoVideoCell2.SharedResources sharedResources;

        public SharedPhotoVideoAdapter(Context context) {
            mContext = context;
        }

        public int getTopOffset() {
            return 0;
        }

        public int getPositionForIndex(int i) {
            return sharedMediaData[0].startOffset + i;
        }

        @Override
        public int getItemCount() {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                if (sharedMediaData[0].messages.size() == 0 && !sharedMediaData[0].loading) {
                    return 1;
                }
                if (sharedMediaData[0].messages.size() == 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
                    return 0;
                }
                int count = sharedMediaData[0].getStartOffset() + sharedMediaData[0].getMessages().size();
                if (count != 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
                    count++;
                }
                return count;
            }
            if (sharedMediaData[0].loadingAfterFastScroll) {
                return sharedMediaData[0].totalCount;
            }
            if (sharedMediaData[0].messages.size() == 0 && !sharedMediaData[0].loading) {
                return 1;
            }
            if (sharedMediaData[0].messages.size() == 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1]) && sharedMediaData[0].startReached) {
                return 0;
            }
            if (sharedMediaData[0].totalCount == 0) {
                int count = sharedMediaData[0].getStartOffset() + sharedMediaData[0].getMessages().size();
                if (count != 0 && (!sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1])) {
                    if (sharedMediaData[0].getEndLoadingStubs() != 0) {
                        count += sharedMediaData[0].getEndLoadingStubs();
                    } else {
                        count++;
                    }
                }
                return count;
            } else {
                return sharedMediaData[0].totalCount;
            }
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
                    if (sharedResources == null) {
                        sharedResources = new SharedPhotoVideoCell2.SharedResources(parent.getContext(), resourcesProvider);
                    }
                    SharedPhotoVideoCell2 cell = new SharedPhotoVideoCell2(mContext, sharedResources, profileActivity.getCurrentAccount());
                    cell.setGradientView(globalGradientView);
                    if (this == storiesAdapter || this == archivedStoriesAdapter) {
                        cell.isStory = true;
                    }
                    view = cell;
                    break;
                default:
                case 2:
                    View emptyStubView = createEmptyStubView(mContext, 0, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].getMessages();
                int index = position - sharedMediaData[0].getStartOffset();
                SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) holder.itemView;
                int oldMessageId = cell.getMessageId();

                int parentCount;
                if (this == photoVideoAdapter) {
                    parentCount = mediaColumnsCount[0];
                } else if (this == storiesAdapter || this == archivedStoriesAdapter) {
                    parentCount = mediaColumnsCount[1];
                } else {
                    parentCount = animateToColumnsCount;
                }
                if (index >= 0 && index < messageObjects.size()) {
                    MessageObject messageObject = messageObjects.get(index);
                    boolean animated = messageObject.getId() == oldMessageId;

                    if (isActionModeShowed) {
                        cell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, animated);
                    } else {
                        cell.setChecked(false, animated);
                    }
                    cell.setMessageObject(messageObject, parentCount);
                } else {
                    cell.setMessageObject(null, parentCount);
                    cell.setChecked(false, false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (!inFastScrollMode && sharedMediaData[0].getMessages().size() == 0 && !sharedMediaData[0].loading && sharedMediaData[0].startReached) {
                return 2;
            }
            int count = sharedMediaData[0].getStartOffset() + sharedMediaData[0].getMessages().size();
            if (position - sharedMediaData[0].getStartOffset() >= 0 && position < count) {
                return 0;
            }
            return 0;
        }

        @Override
        public String getLetter(int position) {
            if (sharedMediaData[0].fastScrollPeriods == null) {
                return "";
            }
            int index = position;
            ArrayList<Period> periods = sharedMediaData[0].fastScrollPeriods;
            if (!periods.isEmpty()) {
                for (int i = 0; i < periods.size(); i++) {
                    if (index <= periods.get(i).startOffset) {
                        return periods.get(i).formatedDate;
                    }
                }
                return periods.get(periods.size() - 1).formatedDate;
            }
            return "";
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            int viewHeight = listView.getChildAt(0).getMeasuredHeight();
            int columnsCount;
            if (this == animationSupportingPhotoVideoAdapter || this == animationSupportingStoriesAdapter || this == animationSupportingArchivedStoriesAdapter) {
                columnsCount = animateToColumnsCount;
            } else if (this == storiesAdapter || this == archivedStoriesAdapter) {
                columnsCount = mediaColumnsCount[1];
            } else {
                columnsCount = mediaColumnsCount[0];
            }
            int totalHeight = (int) (Math.ceil(getTotalItemsCount() / (float) columnsCount) * viewHeight);
            int listHeight =  listView.getMeasuredHeight() - listView.getPaddingTop();
            if (viewHeight == 0) {
                position[0] = position[1] = 0;
                return;
            }
            position[0] = (int) ((progress * (totalHeight - listHeight)) / viewHeight) * columnsCount;
            position[1] = (int) (progress * (totalHeight - listHeight)) % viewHeight;
        }

        @Override
        public void onStartFastScroll() {
            inFastScrollMode = true;
            MediaPage mediaPage = getMediaPage(0);
            if (mediaPage != null) {
                showFastScrollHint(mediaPage, null, false);
            }
        }

        @Override
        public void onFinishFastScroll(RecyclerListView listView) {
            if (inFastScrollMode) {
                inFastScrollMode = false;
                if (listView != null) {
                    int messageId = 0;
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        View child = listView.getChildAt(i);
                        if (child instanceof SharedPhotoVideoCell2) {
                            SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) child;
                            messageId = cell.getMessageId();
                        }
                        if (messageId != 0) {
                            break;
                        }
                    }
                    if (messageId == 0) {
                        findPeriodAndJumpToDate(0, listView, true);
                    }
                }
            }
        }

        @Override
        public int getTotalItemsCount() {
            return sharedMediaData[0].totalCount;
        }

        @Override
        public float getScrollProgress(RecyclerListView listView) {
            int columnsCount;
            if (this == animationSupportingPhotoVideoAdapter || this == animationSupportingStoriesAdapter || this == animationSupportingArchivedStoriesAdapter) {
                columnsCount = animateToColumnsCount;
            } else if (this == storiesAdapter || this == archivedStoriesAdapter) {
                columnsCount = mediaColumnsCount[1];
            } else {
                columnsCount = mediaColumnsCount[0];
            }
            int cellCount = (int) Math.ceil(getTotalItemsCount() / (float) columnsCount);
            if (listView.getChildCount() == 0) {
                return 0;
            }
            int cellHeight = listView.getChildAt(0).getMeasuredHeight();
            View firstChild = listView.getChildAt(0);
            int firstPosition = listView.getChildAdapterPosition(firstChild);
            if (firstPosition < 0) {
                return 0;
            }
            float childTop = firstChild.getTop() - listView.getPaddingTop();
            float listH = listView.getMeasuredHeight() - listView.getPaddingTop();
            float scrollY = (firstPosition / columnsCount) * cellHeight - childTop;
            return scrollY / (((float) cellCount) * cellHeight - listH);
        }

        public boolean fastScrollIsVisible(RecyclerListView listView) {
            int parentCount = this == photoVideoAdapter || this == storiesAdapter || this == archivedStoriesAdapter ? mediaColumnsCount[0] : animateToColumnsCount;
            int cellCount = (int) Math.ceil(getTotalItemsCount() / (float) parentCount);
            if (listView.getChildCount() == 0) {
                return false;
            }
            int cellHeight = listView.getChildAt(0).getMeasuredHeight();
            return cellCount * cellHeight > listView.getMeasuredHeight();
        }

        @Override
        public void onFastScrollSingleTap() {
            showMediaCalendar(0, true);
        }
    }

    private void findPeriodAndJumpToDate(int type, RecyclerListView listView, boolean scrollToPosition) {
        ArrayList<Period> periods = sharedMediaData[type].fastScrollPeriods;
        Period period = null;
        int position = ((LinearLayoutManager) listView.getLayoutManager()).findFirstVisibleItemPosition();
        if (position >= 0) {
            if (periods != null) {
                for (int i = 0; i < periods.size(); i++) {
                    if (position <= periods.get(i).startOffset) {
                        period = periods.get(i);
                        break;
                    }
                }
                if (period == null) {
                    period = periods.get(periods.size() - 1);
                }
            }
            if (period != null) {
                jumpToDate(type, period.maxId, period.startOffset + 1, scrollToPosition);
                return;
            }
        }
    }

    private void jumpToDate(int type, int messageId, int startOffset, boolean scrollToPosition) {
        sharedMediaData[type].messages.clear();
        sharedMediaData[type].messagesDict[0].clear();
        sharedMediaData[type].messagesDict[1].clear();
        sharedMediaData[type].setMaxId(0, messageId);
        sharedMediaData[type].setEndReached(0, false);
        sharedMediaData[type].startReached = false;
        sharedMediaData[type].startOffset = startOffset;
        sharedMediaData[type].endLoadingStubs = sharedMediaData[type].totalCount - startOffset - 1;
        if (sharedMediaData[type].endLoadingStubs < 0) {
            sharedMediaData[type].endLoadingStubs = 0;
        }
        sharedMediaData[type].min_id = messageId;
        sharedMediaData[type].loadingAfterFastScroll = true;
        sharedMediaData[type].loading = false;
        sharedMediaData[type].requestIndex++;
        MediaPage mediaPage = getMediaPage(type);
        if (mediaPage != null && mediaPage.listView.getAdapter() != null) {
            mediaPage.listView.getAdapter().notifyDataSetChanged();
        }
        if (scrollToPosition) {
            for (int i = 0; i < mediaPages.length; i++) {
                if (mediaPages[i].selectedType == type) {
                    mediaPages[i].layoutManager.scrollToPositionWithOffset(Math.min(sharedMediaData[type].totalCount - 1, sharedMediaData[type].startOffset), 0);
                }
            }
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
                                        mediaPages[a].emptyView.title.setText(LocaleController.formatString("NoResultFoundFor", R.string.NoResultFoundFor, query));
                                        mediaPages[a].emptyView.showProgress(false, true);
                                    } else if (oldItemCounts == 0) {
                                        animateItemsEnter(mediaPages[a].listView, 0, null);
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
                                        if (messageObject.type == MessageObject.TYPE_TEXT) {
                                            document = MessageObject.getMedia(messageObject.messageOwner).webpage.document;
                                        } else {
                                            document = MessageObject.getMedia(messageObject.messageOwner).document;
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
                            mediaPages[a].emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
                            mediaPages[a].emptyView.showProgress(false, true);
                        } else if (oldItemCount == 0) {
                            animateItemsEnter(mediaPages[a].listView, 0, null);
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
                view = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_DEFAULT, resourcesProvider);
            } else if (currentType == 4) {
                view = new SharedAudioCell(mContext, SharedAudioCell.VIEW_TYPE_DEFAULT, resourcesProvider) {
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
                view = new SharedLinkCell(mContext, SharedLinkCell.VIEW_TYPE_DEFAULT, resourcesProvider);
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
                View emptyStubView = createEmptyStubView(mContext, 5, dialog_id, resourcesProvider);
                emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerListView.Holder(emptyStubView);
            }
            ContextLinkCell cell = new ContextLinkCell(mContext, true, resourcesProvider);
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
                                animateItemsEnter(listView, 0, null);
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
                    view = new ProfileSearchCell(mContext, resourcesProvider);
                    break;
                case 2:
                    View emptyStubView = createEmptyStubView(mContext, 6, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case 1:
                default:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext, resourcesProvider);
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

    public int getStoriesCount(int tab) {
        StoriesController.StoriesList list;
        if (tab == TAB_STORIES) {
            list = storiesAdapter.storiesList;
        } else if (tab == TAB_ARCHIVED_STORIES) {
            list = archivedStoriesAdapter.storiesList;
        } else {
            return 0;
        }
        if (list != null) {
            return list.getCount();
        }
        return 0;
    }

    private class StoriesAdapter extends SharedPhotoVideoAdapter {

        private final boolean isArchive;
        @Nullable
        public final StoriesController.StoriesList storiesList;
        private StoriesAdapter supportingAdapter;
        private int id;

        private ViewsForPeerStoriesRequester poller;

        public StoriesAdapter(Context context, boolean isArchive) {
            super(context);
            this.isArchive = isArchive;
            if (isArchive && !isStoriesView() || !isArchive && isArchivedOnlyStoriesView()) {
                storiesList = null;
            } else {
                storiesList = profileActivity.getMessagesController().getStoriesController().getStoriesList(dialog_id, isArchive ? StoriesController.StoriesList.TYPE_ARCHIVE : StoriesController.StoriesList.TYPE_PINNED);
            }
            if (storiesList != null) {
                id = storiesList.link();
                poller = new ViewsForPeerStoriesRequester(profileActivity.getMessagesController().getStoriesController(), dialog_id, storiesList.currentAccount) {
                    @Override
                    protected void getStoryIds(ArrayList<Integer> ids) {
                        RecyclerListView listView = null;
                        for (int i = 0; i < mediaPages.length; ++i) {
                            if (mediaPages[i].listView != null && mediaPages[i].listView.getAdapter() == StoriesAdapter.this) {
                                listView = mediaPages[i].listView;
                                break;
                            }
                        }

                        if (listView != null) {
                            for (int i = 0; i < listView.getChildCount(); ++i) {
                                View child = listView.getChildAt(i);
                                if (child instanceof SharedPhotoVideoCell2) {
                                    MessageObject msg = ((SharedPhotoVideoCell2) child).getMessageObject();
                                    if (msg != null && msg.isStory()) {
                                        ids.add(msg.storyItem.id);
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    protected boolean updateStories(ArrayList<Integer> reqIds, TL_stories.TL_stories_storyViews storyViews) {
                        storiesList.updateStoryViews(reqIds, storyViews.views);
                        return true;
                    }
                };
            }
            checkColumns();
        }

        public StoriesAdapter makeSupporting() {
            StoriesAdapter adapter = new StoriesAdapter(getContext(), isArchive, storiesList);
            this.supportingAdapter = adapter;
            return adapter;
        }

        public void destroy() {
            if (storiesList != null) {
                storiesList.unlink(id);
            }
        }

        private StoriesAdapter(Context context, boolean isArchive, StoriesController.StoriesList list) {
            super(context);
            this.isArchive = isArchive;
            this.storiesList = list;
        }

        private void checkColumns() {
            if (storiesList == null) {
                return;
            }
            if (!isArchive && (!storiesColumnsCountSet || allowStoriesSingleColumn && storiesList.getCount() > 1) && storiesList.getCount() > 0 && !isStoriesView()) {
                if (storiesList.getCount() < 5) {
                    mediaColumnsCount[1] = storiesList.getCount();
                    if (mediaPages != null && mediaPages[0] != null && mediaPages[1] != null && mediaPages[0].listView != null && mediaPages[1].listView != null) {
                        switchToCurrentSelectedMode(false);
                    }
                    allowStoriesSingleColumn = mediaColumnsCount[1] == 1;
                } else if (allowStoriesSingleColumn) {
                    allowStoriesSingleColumn = false;
                    mediaColumnsCount[1] = Math.max(2, SharedConfig.storiesColumnsCount);
                    if (mediaPages != null && mediaPages[0] != null && mediaPages[1] != null && mediaPages[0].listView != null && mediaPages[1].listView != null) {
                        switchToCurrentSelectedMode(false);
                    }
                }
                storiesColumnsCountSet = true;
            }
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (supportingAdapter != null) {
                supportingAdapter.notifyDataSetChanged();
            }
            checkColumns();
        }

        public int columnsCount() {
            if (this == photoVideoAdapter) {
                return mediaColumnsCount[0];
            } else if (this == storiesAdapter || this == archivedStoriesAdapter) {
                return mediaColumnsCount[1];
            } else {
                return animateToColumnsCount;
            }
        }

        @Override
        public int getTopOffset() {
            return 0;
        }

        @Override
        public int getItemCount() {
            if (storiesList == null) {
                return 0;
            }
            return storiesList.isOnlyCache() && hasInternet() ? 0 : storiesList.getCount();
        }

        @Override
        public int getTotalItemsCount() {
            return getItemCount();
        }

        @Override
        public int getPositionForIndex(int i) {
            if (isArchive) {
                return getTopOffset() + i;
            }
            return i;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder holder = super.onCreateViewHolder(parent, viewType);
            if (holder.itemView instanceof SharedPhotoVideoCell2) {
                ((SharedPhotoVideoCell2) holder.itemView).isStory = true;
            }
            return holder;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (storiesList == null) {
                return;
            }
            int viewType = holder.getItemViewType();
            if (viewType == 0) {
                SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) holder.itemView;
                cell.isStory = true;
                position -= getTopOffset();
                if (position < 0 || position >= storiesList.messageObjects.size()) {
                    cell.setMessageObject(null, columnsCount());
                    cell.isStory = true;
                    return;
                }
                MessageObject messageObject = storiesList.messageObjects.get(position);
                cell.setMessageObject(messageObject, columnsCount());
                if (isActionModeShowed && messageObject != null) {
                    cell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, false);
                } else {
                    cell.setChecked(false, false);
                }
            }
        }

        public void load(boolean force) {
            if (storiesList == null) {
                return;
            }

            final int columnCount = columnsCount();
            final int count = Math.min(100, Math.max(1, columnCount / 2) * columnCount * columnCount);
            storiesList.load(force, count);
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public String getLetter(int position) {
            if (storiesList == null) {
                return null;
            }
            position -= getTopOffset();
            if (position < 0 || position >= storiesList.messageObjects.size()) {
                return null;
            }
            MessageObject messageObject = storiesList.messageObjects.get(position);
            if (messageObject == null || messageObject.storyItem == null) {
                return null;
            }
            return LocaleController.formatYearMont(messageObject.storyItem.date, true);
        }

        @Override
        public void onFastScrollSingleTap() {
            showMediaCalendar(isArchive ? TAB_ARCHIVED_STORIES : TAB_STORIES, true);
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
                View emptyStubView = createEmptyStubView(mContext, 7, dialog_id, resourcesProvider);
                emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerListView.Holder(emptyStubView);
            }
            View view = new UserCell(mContext, 9, 0, true, false, resourcesProvider);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!(holder.itemView instanceof UserCell)) {
                return;
            }
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
                            if (mediaPages[a].selectedType == TAB_GROUPUSERS) {
                                if (getItemCount() == 0) {
                                    mediaPages[a].emptyView.showProgress(false, true);
                                } else {
                                    animateItemsEnter(mediaPages[a].listView, 0, null);
                                }
                            }
                        }
                    }
                }
            });
            currentChat = delegate.getCurrentChat();
        }

        private boolean createMenuForParticipant(TLObject participant, boolean resultOnly, View view) {
            if (participant instanceof TLRPC.ChannelParticipant) {
                TLRPC.ChannelParticipant channelParticipant = (TLRPC.ChannelParticipant) participant;
                TLRPC.TL_chatChannelParticipant p = new TLRPC.TL_chatChannelParticipant();
                p.channelParticipant = channelParticipant;
                p.user_id = MessageObject.getPeerId(channelParticipant.peer);
                p.inviter_id = channelParticipant.inviter_id;
                p.date = channelParticipant.date;
                participant = p;
            }
            return delegate.onMemberClick((TLRPC.ChatParticipant) participant, true, resultOnly, view);
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
                            String username;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if ((username = UserObject.getPublicUsername(user)) != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q));
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
                                animateItemsEnter(mediaPages[a].listView, 0, null);
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
            ManageChatUserCell view = new ManageChatUserCell(mContext, 9, 5, true, resourcesProvider);
            view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            view.setDelegate((cell, click) -> {
                TLObject object = getItem((Integer) cell.getTag());
                if (object instanceof TLRPC.ChannelParticipant) {
                    TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) object;

//                    int index = searchAdapterHelper.getGroupSearch().indexOf(object);
//                    if (index >= 0) {
//                        for ()
//                    }

                    return createMenuForParticipant(participant, !click, cell);
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

            String un = UserObject.getPublicUsername(user);
            SpannableStringBuilder name = null;

            int count = searchAdapterHelper.getGroupSearch().size();
            String nameSearch = searchAdapterHelper.getLastFoundChannel();

            if (nameSearch != null) {
                String u = UserObject.getUserName(user);
                name = new SpannableStringBuilder(u);
                int idx = AndroidUtilities.indexOfIgnoreCase(u, nameSearch);
                if (idx != -1) {
                    name.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4)), idx, idx + nameSearch.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

        arrayList.add(new ThemeDescription(deleteItem.getIconView(), ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(deleteItem, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        if (gotoItem != null) {
            arrayList.add(new ThemeDescription(gotoItem.getIconView(), ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
            arrayList.add(new ThemeDescription(gotoItem, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        }
        if (forwardItem != null) {
            arrayList.add(new ThemeDescription(forwardItem.getIconView(), ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
            arrayList.add(new ThemeDescription(forwardItem, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        }
        arrayList.add(new ThemeDescription(closeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, new Drawable[]{backDrawable}, null, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(closeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));

        arrayList.add(new ThemeDescription(actionModeLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(floatingDateView, 0, null, null, null, null, Theme.key_chat_mediaTimeBackground));
        arrayList.add(new ThemeDescription(floatingDateView, 0, null, null, null, null, Theme.key_chat_mediaTimeText));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip, 0, new Class[]{ScrollSlidingTextTabStrip.class}, new String[]{"selectorDrawable"}, null, null, null, Theme.key_profile_tabSelectedLine));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_profile_tabSelectedText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_profile_tabText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_profile_tabSelector));

        if (fragmentContextView != null) {
            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground));
            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause));
            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle));
            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_FASTSCROLL, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerPerformer));
            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose));

            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground));
            arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText));
        }

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

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EmptyStubView.class}, new String[]{"emptyTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
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

    public int getNextMediaColumnsCount(int i, int mediaColumnsCount, boolean up) {
        int newColumnsCount = mediaColumnsCount + (!up ? 1 : -1);
        if (newColumnsCount > 6) {
            newColumnsCount = !up ? 9 : 6;
        }
        return Utilities.clamp(newColumnsCount, 9, allowStoriesSingleColumn && i == 1 ? 1 : 2);
    }

    private ActionBarMenuSubItem mediaZoomInItem;
    private ActionBarMenuSubItem mediaZoomOutItem;

    public Boolean zoomIn() {
        if (photoVideoChangeColumnsAnimation || mediaPages[0] == null) {
            return null;
        }
        changeColumnsTab = mediaPages[0].selectedType;
        final int ci = changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES ? 1 : 0;
        int newColumnsCount = getNextMediaColumnsCount(ci, mediaColumnsCount[ci], true);
        if (mediaZoomInItem != null && newColumnsCount == getNextMediaColumnsCount(ci, newColumnsCount, true)) {
            mediaZoomInItem.setEnabled(false);
            mediaZoomInItem.animate().alpha(0.5f).start();
        }
        if (mediaColumnsCount[ci] != newColumnsCount) {
            if (mediaZoomOutItem != null && !mediaZoomOutItem.isEnabled()) {
                mediaZoomOutItem.setEnabled(true);
                mediaZoomOutItem.animate().alpha(1f).start();
            }
            if (ci == 0) {
                SharedConfig.setMediaColumnsCount(newColumnsCount);
            } else if (getStoriesCount(mediaPages[0].selectedType) >= 5) {
                SharedConfig.setStoriesColumnsCount(newColumnsCount);
            }
            animateToMediaColumnsCount(newColumnsCount);
        }
        return newColumnsCount != getNextMediaColumnsCount(ci, newColumnsCount, true);
    }

    public Boolean zoomOut() {
        if (photoVideoChangeColumnsAnimation || mediaPages[0] == null || allowStoriesSingleColumn) {
            return null;
        }
        changeColumnsTab = mediaPages[0].selectedType;
        final int ci = changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES ? 1 : 0;
        int newColumnsCount = getNextMediaColumnsCount(ci, mediaColumnsCount[ci], false);
        if (mediaZoomOutItem != null && newColumnsCount == getNextMediaColumnsCount(ci, newColumnsCount, false)) {
            mediaZoomOutItem.setEnabled(false);
            mediaZoomOutItem.animate().alpha(0.5f).start();
        }
        if (mediaColumnsCount[ci] != newColumnsCount) {
            if (mediaZoomInItem != null && !mediaZoomInItem.isEnabled()) {
                mediaZoomInItem.setEnabled(true);
                mediaZoomInItem.animate().alpha(1f).start();
            }
            if (ci == 0) {
                SharedConfig.setMediaColumnsCount(newColumnsCount);
            } else if (getStoriesCount(mediaPages[0].selectedType) >= 5) {
                SharedConfig.setStoriesColumnsCount(newColumnsCount);
            }
            animateToMediaColumnsCount(newColumnsCount);
        }
        return newColumnsCount != getNextMediaColumnsCount(ci, newColumnsCount, false);
    }

    public boolean canZoomIn() {
        if (mediaPages == null || mediaPages[0] == null) {
            return false;
        }
        final int ci = mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
        return mediaColumnsCount[ci] != getNextMediaColumnsCount(ci, mediaColumnsCount[ci], true);
    }

    public boolean canZoomOut() {
        if (mediaPages == null || mediaPages[0] == null || allowStoriesSingleColumn) {
            return false;
        }
        final int ci = mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
        return mediaColumnsCount[ci] != getNextMediaColumnsCount(ci, mediaColumnsCount[ci], false);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == fragmentContextView) {
            canvas.save();
            canvas.clipRect(0, mediaPages[0].getTop(), child.getMeasuredWidth(),mediaPages[0].getTop() + child.getMeasuredHeight() + AndroidUtilities.dp(12));
            boolean b = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return b;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private class ScrollSlidingTextTabStripInner extends ScrollSlidingTextTabStrip {

        protected Paint backgroundPaint;
        public int backgroundColor = Color.TRANSPARENT;


        public ScrollSlidingTextTabStripInner(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
        }

        protected void drawBackground(Canvas canvas) {
            if (SharedConfig.chatBlurEnabled() && backgroundColor != Color.TRANSPARENT) {
                if (backgroundPaint == null) {
                    backgroundPaint = new Paint();
                }
                backgroundPaint.setColor(backgroundColor);
                AndroidUtilities.rectTmp2.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                drawBackgroundWithBlur(canvas, getY(), AndroidUtilities.rectTmp2, backgroundPaint);
            }
        }

        @Override
        public void setBackgroundColor(int color) {
            backgroundColor = color;
            invalidate();
        }
    }

    private int getThemedColor(int key) {
        if (resourcesProvider != null) {
            return resourcesProvider.getColor(key);
        }
        return Theme.getColor(key);
    }

    public interface Delegate {
        void scrollToSharedMedia();

        boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View view);

        TLRPC.Chat getCurrentChat();

        boolean isFragmentOpened();

        RecyclerListView getListView();

        boolean canSearchMembers();

        void updateSelectedMediaTabText();
    }

    public float getTabProgress() {
        float progress = 0;
        for (int i = 0; i < mediaPages.length; ++i) {
            if (mediaPages[i] != null) {
                progress += mediaPages[i].selectedType * (1f - Math.abs(mediaPages[i].getTranslationX() / getWidth()));
            }
        }
        return progress;
    }

    protected void onTabProgress(float progress) {}
    protected void onTabScroll(boolean scrolling) {}

    public static class InternalListView extends BlurredRecyclerView implements StoriesListPlaceProvider.ClippedView {

        public int hintPaddingTop;

        public InternalListView(Context context) {
            super(context);
        }

        @Override
        public void updateClip(int[] clip) {
            clip[0] = getPaddingTop() - AndroidUtilities.dp(2) - hintPaddingTop;
            clip[1] = getMeasuredHeight() - getPaddingBottom();
        }
    }
}
