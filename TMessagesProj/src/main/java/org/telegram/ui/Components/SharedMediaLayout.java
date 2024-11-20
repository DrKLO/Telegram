package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MediaDataController.MEDIA_PHOTOVIDEO;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import org.telegram.messenger.SavedMessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.CalendarActivity;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.DialogCell;
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
import org.telegram.ui.ChatActivityContainer;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Gifts.ProfileGiftsContainer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.UserListPoller;
import org.telegram.ui.Stories.ViewsForPeerStoriesRequester;
import org.telegram.ui.Stories.bots.BotPreviewsEditContainer;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class SharedMediaLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, DialogCell.DialogCellDelegate {

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
    public static final int TAB_RECOMMENDED_CHANNELS = 10;
    public static final int TAB_SAVED_DIALOGS = 11;
    public static final int TAB_SAVED_MESSAGES = 12;
    public static final int TAB_BOT_PREVIEWS = 13;
    public static final int TAB_GIFTS = 14;

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
    private long topicId;

    private UndoView undoView;

    public boolean checkPinchToZoom(MotionEvent ev) {
        final int selectedType = mediaPages[0].selectedType;
        if (selectedType == TAB_BOT_PREVIEWS && botPreviewsContainer != null) {
            return botPreviewsContainer.checkPinchToZoom(ev);
        }
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
        if (canEditStories() && (getClosestTab() == TAB_STORIES || getClosestTab() == TAB_BOT_PREVIEWS) && isActionModeShown()) {
            return false;
        }
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

    public void drawListForBlur(Canvas blurCanvas, ArrayList<SizeNotifierFrameLayout.IViewWithInvalidateCallback> views) {
        for (int i = 0; i < mediaPages.length; i++) {
            if (mediaPages[i] != null && mediaPages[i].getVisibility() == View.VISIBLE) {
                for (int j = 0; j < mediaPages[i].listView.getChildCount(); j++) {
                    View child = mediaPages[i].listView.getChildAt(j);
                    if (child.getY() < mediaPages[i].listView.blurTopPadding + dp(100)) {
                        int restore = blurCanvas.save();
                        blurCanvas.translate(mediaPages[i].getX() + child.getX(), getY() + mediaPages[i].getY() + mediaPages[i].listView.getY() + child.getY());
                        child.draw(blurCanvas);
                        if (views != null && child instanceof SizeNotifierFrameLayout.IViewWithInvalidateCallback) {
                            views.add((SizeNotifierFrameLayout.IViewWithInvalidateCallback) child);
                        }
                        blurCanvas.restoreToCount(restore);
                    }
                }
            }
        }
    }

    @Override
    public void onButtonClicked(DialogCell dialogCell) {

    }

    @Override
    public void onButtonLongPress(DialogCell dialogCell) {

    }

    @Override
    public boolean canClickButtonInside() {
        return false;
    }

    @Override
    public void openStory(DialogCell dialogCell, Runnable onDone) {
        if (profileActivity == null) return;
        if (profileActivity.getMessagesController().getStoriesController().hasStories(dialogCell.getDialogId())) {
            profileActivity.getOrCreateStoryViewer().doOnAnimationReady(onDone);
            profileActivity.getOrCreateStoryViewer().open(
                profileActivity.getContext(),
                dialogCell.getDialogId(),
                StoriesListPlaceProvider.of((RecyclerListView) dialogCell.getParent())
                    .addBottomClip(profileActivity instanceof ProfileActivity && ((ProfileActivity) profileActivity).myProfile ? dp(68) : 0)
            );
        }
    }

    @Override
    public void showChatPreview(DialogCell dialogCell) {}

    @Override
    public void openHiddenStories() {}

    private static class MediaPage extends FrameLayout {
        public long lastCheckScrollTime;
        public boolean fastScrollEnabled;
        public ObjectAnimator fastScrollAnimator;
        private DefaultItemAnimator itemAnimator;
        private RecyclerView.RecycledViewPool viewPool, searchViewPool;
        private InternalListView listView;
        private InternalListView animationSupportingListView;
        private GridLayoutManager animationSupportingLayoutManager;
        private FlickerLoadingView progressView;
        private StickerEmptyView emptyView;
        private ExtendedGridLayoutManager layoutManager;
        private ClippingImageView animatingImageView;
        private RecyclerAnimationScrollHelper scrollHelper;
        private int selectedType;
        private ButtonWithCounterView buttonView;

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
                    float y = fastScroll.getScrollBarY() + dp(36);
                    if (selectedType == TAB_ARCHIVED_STORIES) {
                        y += dp(64);
                    }
                    float x = (getMeasuredWidth() - fastScrollHintView.getMeasuredWidth() - dp(16));
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
        if (mediaPages[1] != null && (mediaPages[1].selectedType == TAB_PHOTOVIDEO || mediaPages[1].selectedType == TAB_STORIES && TextUtils.isEmpty(getStoriesHashtag()) || mediaPages[1].selectedType == TAB_ARCHIVED_STORIES || mediaPages[1].selectedType == TAB_SAVED_DIALOGS || mediaPages[1].selectedType == TAB_BOT_PREVIEWS))
            alpha += progress;
        if (mediaPages[0] != null && (mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_STORIES && TextUtils.isEmpty(getStoriesHashtag()) || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES || mediaPages[0].selectedType == TAB_SAVED_DIALOGS || mediaPages[0].selectedType == TAB_BOT_PREVIEWS))
            alpha += 1f - progress;
        return alpha;
    }

    public float getSearchAlpha(float progress) {
        if (isArchivedOnlyStoriesView()) {
            return 0;
        }
        float alpha = 0;
        if (mediaPages[1] != null && isSearchItemVisible(mediaPages[1].selectedType) && mediaPages[1].selectedType != TAB_SAVED_DIALOGS)
            alpha += progress;
        if (mediaPages[0] != null && isSearchItemVisible(mediaPages[0].selectedType) && mediaPages[0].selectedType != TAB_SAVED_DIALOGS)
            alpha += 1f - progress;
        return alpha;
    }

    public void updateSearchItemIcon(float progress) {
        if (searchItemIcon == null) {
            return;
        }
        float alpha = 0;
        if (mediaPages[1] != null && mediaPages[1].selectedType == TAB_SAVED_DIALOGS)
            alpha += progress;
        if (mediaPages[0] != null && mediaPages[0].selectedType == TAB_SAVED_DIALOGS)
            alpha += 1f - progress;
        searchItemIcon.setAlpha(alpha);
        searchItemIcon.setScaleX(.85f + .15f * alpha);
        searchItemIcon.setScaleY(.85f + .15f * alpha);
        searchItemIcon.setVisibility(alpha <= 0.01f ? View.GONE : View.VISIBLE);
    }

    public void updateSearchItemIconAnimated() {
        if (searchItemIcon == null) {
            return;
        }
        boolean visible = mediaPages[1] != null && mediaPages[1].selectedType == TAB_SAVED_DIALOGS;
        if (visible) {
            searchItemIcon.setVisibility(View.VISIBLE);
        }
        searchItemIcon.animate().alpha(visible ? 1f : 0f).scaleX(visible ? 1 : .85f).scaleY(visible ? 1 : .85f).withEndAction(() -> {
            if (!visible) {
                searchItemIcon.setVisibility(View.GONE);
            }
        }).setDuration(420).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
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
    private ChannelRecommendationsAdapter channelRecommendationsAdapter;
    private SavedDialogsAdapter savedDialogsAdapter;
    private SavedMessagesSearchAdapter savedMessagesSearchAdapter;
    private ChatActivityContainer savedMessagesContainer;
    private BotPreviewsEditContainer botPreviewsContainer;
    public ProfileGiftsContainer giftsContainer;
    private ChatUsersAdapter chatUsersAdapter;
    private ItemTouchHelper storiesReorder;
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
    @Nullable
    public ActionBarMenuItem searchItemIcon;
    @Nullable
    private ActionBarMenuItem searchItem;
    private float searchAlpha;
    private float optionsAlpha;
    public ImageView photoVideoOptionsItem;
    private RLottieImageView optionsSearchImageView;
    private ActionBarMenuItem forwardItem;
    private ActionBarMenuItem gotoItem;
    private ActionBarMenuItem pinItem;
    private ActionBarMenuItem unpinItem;
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
    public ScrollSlidingTextTabStripInner scrollSlidingTextTabStrip;
    public SearchTagsList searchTagsList;
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
    private ReactionsLayoutInBubble.VisibleReaction searchingReaction;

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
        public boolean hasPreviews;
        public boolean hasSavedMessages;
        private boolean checkedHasSavedMessages;
        private SharedMediaData[] sharedMediaData;
        private long dialogId;
        private long topicId;
        private long mergeDialogId;
        private BaseFragment parentFragment;
        private ArrayList<SharedMediaPreloaderDelegate> delegates = new ArrayList<>();
        private boolean mediaWasLoaded;

        public boolean hasSharedMedia() {
            int[] hasMedia = getLastMediaCount();
            if (hasMedia == null) return false;
            for (int i = 0; i < hasMedia.length; ++i) {
                if (hasMedia[i] > 0)
                    return true;
            }
            if (hasSavedMessages) {
                return true;
            }
            if (parentFragment != null && dialogId == parentFragment.getUserConfig().getClientUserId() && topicId == 0 && parentFragment.getMessagesController().getSavedMessagesController().hasDialogs()) {
                return true;
            }
            return false;
        }

        public SharedMediaPreloader(BaseFragment fragment) {
            parentFragment = fragment;
            if (fragment instanceof ChatActivityInterface) {
                ChatActivityInterface chatActivity = (ChatActivityInterface) fragment;
                dialogId = chatActivity.getDialogId();
                mergeDialogId = chatActivity.getMergeDialogId();
                topicId = chatActivity.getTopicId();
                if (dialogId != fragment.getUserConfig().getClientUserId()) {
                    fragment.getMessagesController().getSavedMessagesController().hasSavedMessages(dialogId, hasMessages -> {
                        this.hasSavedMessages = hasMessages;
                        this.checkedHasSavedMessages = true;
                        if (hasSavedMessages) {
                            for (int a = 0, N = delegates.size(); a < N; a++) {
                                delegates.get(a).mediaCountUpdated();
                            }
                        }
                    });
                }
            } else if (fragment instanceof ProfileActivity) {
                ProfileActivity profileActivity = (ProfileActivity) fragment;
                if (profileActivity.saved) {
                    dialogId = profileActivity.getUserConfig().getClientUserId();
                    topicId = profileActivity.getDialogId();
                } else {
                    dialogId = profileActivity.getDialogId();
                    topicId = profileActivity.getTopicId();

                    if (dialogId != fragment.getUserConfig().getClientUserId()) {
                        fragment.getMessagesController().getSavedMessagesController().hasSavedMessages(dialogId, hasMessages -> {
                            this.hasSavedMessages = hasMessages;
                            this.checkedHasSavedMessages = true;
                            if (hasSavedMessages) {
                                for (int a = 0, N = delegates.size(); a < N; a++) {
                                    delegates.get(a).mediaCountUpdated();
                                }
                            }
                        });
                    }
                }
            } else if (fragment instanceof MediaActivity) {
                MediaActivity mediaActivity = (MediaActivity) fragment;
                dialogId = mediaActivity.getDialogId();
            } else if (fragment instanceof DialogsActivity) {
                dialogId = fragment.getUserConfig().getClientUserId();
            }

            sharedMediaData = new SharedMediaData[6];
            for (int a = 0; a < sharedMediaData.length; a++) {
                sharedMediaData[a] = new SharedMediaData();
                sharedMediaData[a].setMaxId(0, DialogObject.isEncryptedDialog(dialogId) ? Integer.MIN_VALUE : Integer.MAX_VALUE);
            }
            loadMediaCounts();

            if (parentFragment == null) return;
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
            notificationCenter.addObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
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
            if (parentFragment == null) return;
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
            notificationCenter.removeObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
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
                long topicId = (Long) args[1];
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
                            parentFragment.getMediaDataController().loadMedia(did, lastLoadMediaCount[a] == -1 ? 30 : 20, 0, 0, type, topicId,1, parentFragment.getClassGuid(), 0, null, null);
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
                long topicId = (Long) args[1];
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
                    final int currentAccount = parentFragment != null ? parentFragment.getCurrentAccount() : -1;
                    for (int a = 0; a < arr.size(); a++) {
                        MessageObject obj = arr.get(a);
                        if (topicId != 0 && topicId != MessageObject.getTopicId(currentAccount, obj.messageOwner, true)) {
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
                final int currentAccount = parentFragment != null ? parentFragment.getCurrentAccount() : -1;
                for (int a = 0, N = markAsDeletedMessages.size(); a < N; a++) {
                    for (int b = 0; b < sharedMediaData.length; b++) {
                        MessageObject messageObject = sharedMediaData[b].deleteMessage(markAsDeletedMessages.get(a), 0);
                        if (messageObject != null) {
                            if (messageObject.getDialogId() == dialogId && (topicId == 0 || MessageObject.getTopicId(currentAccount, messageObject.messageOwner, true) == topicId)) {
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
                final int currentAccount = parentFragment != null ? parentFragment.getCurrentAccount() : -1;
                for (int b = 0, N = messageObjects.size(); b < N; b++) {
                    MessageObject messageObject = messageObjects.get(b);
                    int mid = messageObject.getId();
                    long topicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, true);
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
            } else if (id == NotificationCenter.savedMessagesDialogsUpdate) {
                final boolean newHasMessages = (parentFragment != null && parentFragment.getMessagesController().getSavedMessagesController().containsDialog(dialogId));
                if (checkedHasSavedMessages && hasSavedMessages != newHasMessages) {
                    hasSavedMessages = newHasMessages;
                    for (int a = 0, N = delegates.size(); a < N; a++) {
                        delegates.get(a).mediaCountUpdated();
                    }
                }
            }
        }

        private void loadMediaCounts() {
            if (parentFragment == null) return;
            parentFragment.getMediaDataController().getMediaCounts(dialogId, topicId, parentFragment.getClassGuid());
            if (mergeDialogId != 0) {
                parentFragment.getMediaDataController().getMediaCounts(mergeDialogId, topicId, parentFragment.getClassGuid());
            }
        }

        private void setChatInfo(TLRPC.ChatFull chatInfo) {
            if (parentFragment == null) return;
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
                    object.radius = object.imageReceiver.getRoundRadius(true);
                    object.thumb = object.imageReceiver.getBitmapSafe();
                    object.parentView.getLocationInWindow(coords);
                    object.clipTopAddition = 0;
                    object.starOffset = sharedMediaData[0].startOffset;
                    if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                        object.clipTopAddition += dp(36);
                    }

                    if (PhotoViewer.isShowingImage(messageObject)) {
                        final View pinnedHeader = listView.getPinnedHeader();
                        if (pinnedHeader != null) {
                            int top = 0;
                            if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                                top += fragmentContextView.getHeight() - dp(2.5f);
                            }
                            if (view instanceof SharedDocumentCell) {
                                top += dp(8f);
                            }
                            final int topOffset = top - object.viewY;
                            if (topOffset > view.getHeight()) {
                                listView.scrollBy(0, -(topOffset + pinnedHeader.getHeight()));
                            } else {
                                int bottomOffset = object.viewY - listView.getHeight();
                                if (view instanceof SharedDocumentCell) {
                                    bottomOffset -= dp(8f);
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
    private final static int pin = 103;
    private final static int unpin = 104;

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

    private Runnable applyBulletin;

    public boolean hasInternet() {
        return profileActivity.getConnectionsManager().getConnectionState() == ConnectionsManager.ConnectionStateConnected;
    }

    public int overrideColumnsCount() {
        return -1;
    }

    public SharedMediaLayout(Context context, long did, SharedMediaPreloader preloader, int commonGroupsCount, ArrayList<Integer> sortedUsers, TLRPC.ChatFull chatInfo, TLRPC.UserFull userInfo, int initialTab, BaseFragment parent, Delegate delegate, int viewType, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.viewType = viewType;
        this.resourcesProvider = resourcesProvider;

        globalGradientView = new FlickerLoadingView(context);
        globalGradientView.setIsSingleCell(true);

        TLRPC.User user = parent.getMessagesController().getUser(did);
        sharedMediaPreloader = preloader;
        this.delegate = delegate;
        int[] mediaCount = preloader.getLastMediaCount();
        topicId = sharedMediaPreloader.topicId;
        hasMedia = new int[]{mediaCount[0], mediaCount[1], mediaCount[2], mediaCount[3], mediaCount[4], mediaCount[5], topicId == 0 ? commonGroupsCount : 0};
        if (initialTab == TAB_GIFTS) {
            this.initialTab = initialTab;
        } else if (initialTab == TAB_RECOMMENDED_CHANNELS) {
            this.initialTab = initialTab;
        } else if (initialTab == TAB_SAVED_DIALOGS) {
            this.initialTab = initialTab;
        } else if (user != null && user.bot && user.bot_has_main_app && user.bot_can_edit) {
            this.initialTab = TAB_BOT_PREVIEWS;
        } else if (userInfo != null && userInfo.bot_info != null && userInfo.bot_info.has_preview_medias) {
            this.initialTab = TAB_STORIES;
        } else if (userInfo != null && userInfo.stories_pinned_available || chatInfo != null && chatInfo.stories_pinned_available || isStoriesView()) {
            this.initialTab = getInitialTab();
        } else if (userInfo != null && userInfo.stargifts_count > 0) {
            this.initialTab = TAB_GIFTS;
        } else if (initialTab != -1 && topicId == 0) {
            this.initialTab = initialTab;
        } else {
            for (int a = 0; a < hasMedia.length; a++) {
                if (hasMedia[a] == -1 || hasMedia[a] > 0) {
                    this.initialTab = a;
                    break;
                }
            }
        }
        onTabProgress(initialTab);
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
        mediaColumnsCount[0] = overrideColumnsCount() <= 0 ? SharedConfig.mediaColumnsCount : overrideColumnsCount();
        mediaColumnsCount[1] = overrideColumnsCount() <= 0 ? SharedConfig.storiesColumnsCount : overrideColumnsCount();

        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.mediaDidLoad);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidReset);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.messagePlayingDidStart);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.storiesListUpdated);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.storiesUpdated);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.channelRecommendationsLoaded);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        profileActivity.getNotificationCenter().addObserver(this, NotificationCenter.starUserGiftsLoaded);

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
        searchingReaction = null;
        if (searchTagsList != null) {
            searchTagsList.show(false);
        }
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
        if (savedDialogsAdapter != null) {
            savedDialogsAdapter.unselectAll();
        }

        if (addActionButtons()) {
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
            if (dialog_id == profileActivity.getUserConfig().getClientUserId() && profileActivity instanceof MediaActivity && canShowSearchItem()) {
                searchItemIcon = menu.addItem(11, R.drawable.ic_ab_search);
            }
            searchItem = menu.addItem(0, 0).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    if (searchTagsList != null) {
                        searchTagsList.show((getSelectedTab() == TAB_SAVED_DIALOGS || getSelectedTab() == TAB_SAVED_MESSAGES) && searchTagsList.hasFilters());
                    }
                    if (photoVideoOptionsItem != null) {
                        photoVideoOptionsItem.setVisibility(View.GONE);
                    }
                    if (searchItemIcon != null) {
                        searchItemIcon.setVisibility(View.GONE);
                    }
                    searchItem.setVisibility(View.GONE);
                    onSearchStateChanged(true);
                    if (optionsSearchImageView != null) {
                        optionsSearchImageView.animate().scaleX(0.6f).scaleY(0.6f).alpha(0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    }
                }

                @Override
                public void onSearchCollapse() {
                    searching = false;
                    searchingReaction = null;
                    if (searchItemIcon != null) {
                        searchItemIcon.setVisibility(View.VISIBLE);
                    }
                    if (photoVideoOptionsItem != null && getPhotoVideoOptionsAlpha(0) > .5f) {
                        photoVideoOptionsItem.setVisibility(View.VISIBLE);
                    }
                    if (searchTagsList != null) {
                        searchTagsList.clear();
                        searchTagsList.show(false);
                    }
                    if (savedMessagesContainer != null) {
                        savedMessagesContainer.chatActivity.clearSearch();
                    }
                    searchWas = false;
                    searchItem.setVisibility(View.VISIBLE);
                    documentsSearchAdapter.search(null, true);
                    linksSearchAdapter.search(null, true);
                    audioSearchAdapter.search(null, true);
                    groupUsersSearchAdapter.search(null, true);
                    if (savedMessagesSearchAdapter != null) {
                        savedMessagesSearchAdapter.search(null, null);
                    }
                    onSearchStateChanged(false);
                    if (optionsSearchImageView != null) {
                        optionsSearchImageView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    }
                    if (ignoreSearchCollapse) {
                        ignoreSearchCollapse = false;
                        return;
                    }
                    switchToCurrentSelectedMode(false);
                }

                @Override
                public void onTextChanged(EditText editText) {
                    String text = editText.getText().toString();
                    if (savedMessagesContainer != null) {
                        savedMessagesContainer.chatActivity.setSearchQuery(text);
                        if (TextUtils.isEmpty(text) && searchingReaction == null) {
                            savedMessagesContainer.chatActivity.clearSearch();
                        }
                    }
                    searchItem.setVisibility(View.GONE);
                    searchWas = text.length() != 0 || searchingReaction != null;
                    post(() -> switchToCurrentSelectedMode(false));
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
                    } else if (mediaPages[0].selectedType == TAB_SAVED_DIALOGS) {
                        if (savedMessagesSearchAdapter == null) {
                            return;
                        }
                        savedMessagesSearchAdapter.search(text, searchingReaction);
                    }
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    super.onSearchPressed(editText);
                    if (savedMessagesContainer != null) {
                        savedMessagesContainer.chatActivity.hitSearch();
                    }
                }

                @Override
                public void onLayout(int l, int t, int r, int b) {
                    View parent = (View) searchItem.getParent();
                    searchItem.setTranslationX(parent.getMeasuredWidth() - searchItem.getRight());
                }
            });
            searchItem.setTranslationY(dp(10));
            searchItem.setSearchFieldHint(getString(searchTagsList != null && searchTagsList.hasFilters() && getSelectedTab() == TAB_SAVED_DIALOGS ? R.string.SavedTagSearchHint : R.string.Search));
            searchItem.setContentDescription(getString("Search", R.string.Search));
            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
        }

        photoVideoOptionsItem = new ImageView(context);
        photoVideoOptionsItem.setContentDescription(getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        photoVideoOptionsItem.setTranslationY(dp(10));
        photoVideoOptionsItem.setVisibility(View.INVISIBLE);

        if (!isArchivedOnlyStoriesView() && !isSearchingStories()) {
            actionBar.addView(photoVideoOptionsItem, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM));

            optionsSearchImageView = new RLottieImageView(context);
            optionsSearchImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            optionsSearchImageView.setAnimation(R.raw.options_to_search, 24, 24);
            optionsSearchImageView.getAnimatedDrawable().multiplySpeed(2f);
            optionsSearchImageView.getAnimatedDrawable().setPlayInDirectionOfCustomEndFrame(true);
            optionsSearchImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.MULTIPLY));
            optionsSearchImageView.setVisibility(GONE);
            actionBar.addView(optionsSearchImageView, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM));
        }
        photoVideoOptionsItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                int tab = getClosestTab();
                boolean isStories = tab == TAB_STORIES || tab == TAB_ARCHIVED_STORIES;

                final int currentAccount = profileActivity.getCurrentAccount();
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog_id);
                if (tab == TAB_BOT_PREVIEWS && user != null && user.bot && user.bot_has_main_app && user.bot_can_edit && botPreviewsContainer != null) {
                    ItemOptions.makeOptions(profileActivity, photoVideoOptionsItem)
                        .addIf(botPreviewsContainer.getItemsCount() < profileActivity.getMessagesController().botPreviewMediasMax, R.drawable.msg_addbot, getString(R.string.ProfileBotAddPreview), () -> {
                            StoryRecorder.getInstance(profileActivity.getParentActivity(), profileActivity.getCurrentAccount()).openBot(dialog_id, botPreviewsContainer.getCurrentLang(), null);
                        })
                        .addIf(botPreviewsContainer.getItemsCount() > 1 && !botPreviewsContainer.isSelectedAll(), R.drawable.tabs_reorder, getString(R.string.ProfileBotReorder), () -> {
                            botPreviewsContainer.selectAll();
                        })
                        .addIf(botPreviewsContainer.getItemsCount() > 0, R.drawable.msg_select, getString(botPreviewsContainer.isSelectedAll() ? R.string.ProfileBotUnSelect : R.string.ProfileBotSelect), () -> {
                            if (botPreviewsContainer.isSelectedAll()) {
                                botPreviewsContainer.unselectAll();
                            } else {
                                botPreviewsContainer.selectAll();
                            }
                        })
                        .addIf(!TextUtils.isEmpty(botPreviewsContainer.getCurrentLang()), R.drawable.msg_delete, LocaleController.formatString(R.string.ProfileBotRemoveLang, TranslateAlert2.languageName(botPreviewsContainer.getCurrentLang())), true, () -> {
                            botPreviewsContainer.deleteLang(botPreviewsContainer.getCurrentLang());
                        })
                        .translate(0, -dp(52))
                        .setDimAlpha(0)
                        .show();
                    return;
                }
                if (getSelectedTab() == TAB_SAVED_DIALOGS) {
                    ItemOptions.makeOptions(profileActivity, photoVideoOptionsItem)
                        .add(R.drawable.msg_discussion, getString(R.string.SavedViewAsMessages), () -> {
                            profileActivity.getMessagesController().setSavedViewAs(false);
                            Bundle args = new Bundle();
                            args.putLong("user_id", profileActivity.getUserConfig().getClientUserId());
                            profileActivity.presentFragment(new ChatActivity(args), true);
                        })
                        .addGap()
                        .add(R.drawable.msg_home, getString(R.string.AddShortcut), () -> {
                            try {
                                profileActivity.getMediaDataController().installShortcut(profileActivity.getUserConfig().getClientUserId(), MediaDataController.SHORTCUT_TYPE_USER_OR_CHAT);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .add(R.drawable.msg_delete, getString(R.string.DeleteAll), () -> {
                            TLRPC.User currentUser = profileActivity.getUserConfig().getCurrentUser();
                            AlertsCreator.createClearOrDeleteDialogAlert(profileActivity, false, null, currentUser, false, true, true, (param) -> {
                                profileActivity.finishFragment();
                                if (profileActivity instanceof NotificationCenter.NotificationCenterDelegate) {
                                    profileActivity.getNotificationCenter().removeObserver((NotificationCenter.NotificationCenterDelegate) profileActivity, NotificationCenter.closeChats);
                                }
                                profileActivity.getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                                profileActivity.getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, dialog_id, currentUser, null, param);
                                profileActivity.getMessagesController().setSavedViewAs(false);
                            }, resourcesProvider);
                        })
                        .translate(0, -dp(52))
                        .setDimAlpha(0)
                        .show();
                    return;
                }
                View dividerView = new DividerCell(context);
                ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        if (dividerView.getParent() != null) {
                            dividerView.setVisibility(View.GONE);
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            dividerView.getLayoutParams().width = getMeasuredWidth() - dp(16);
                            dividerView.setVisibility(View.VISIBLE);
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        } else {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        }
                    }
                };


                mediaZoomInItem = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
                mediaZoomOutItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);

                mediaZoomInItem.setTextAndIcon(getString("MediaZoomIn", R.string.MediaZoomIn), R.drawable.msg_zoomin);
                mediaZoomInItem.setOnClickListener(view1 -> zoomIn());
                popupLayout.addView(mediaZoomInItem);

                mediaZoomOutItem.setTextAndIcon(getString("MediaZoomOut", R.string.MediaZoomOut), R.drawable.msg_zoomout);
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

                final boolean hasDifferentTypes = isStories || (sharedMediaData[0].hasPhotos && sharedMediaData[0].hasVideos) || !sharedMediaData[0].endReached[0] || !sharedMediaData[0].endReached[1] || !sharedMediaData[0].startReached;
                if (!DialogObject.isEncryptedDialog(dialog_id) && !(user != null && user.bot)) {
                    ActionBarMenuSubItem calendarItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
                    calendarItem.setTextAndIcon(getString("Calendar", R.string.Calendar), R.drawable.msg_calendar2);
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
                            openArchiveItem.setTextAndIcon(getString(R.string.OpenChannelArchiveStories), R.drawable.msg_archive);
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

                        showPhotosItem.setTextAndIcon(getString("MediaShowPhotos", R.string.MediaShowPhotos), 0);
                        popupLayout.addView(showPhotosItem);

                        showVideosItem.setTextAndIcon(getString("MediaShowVideos", R.string.MediaShowVideos), 0);
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

                optionsWindow = AlertsCreator.showPopupMenu(popupLayout, photoVideoOptionsItem, 0, -dp(56));
            }
        });

        if (searchItem != null) {
            EditTextBoldCursor editText = searchItem.getSearchField();
            editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setHintTextColor(getThemedColor(Theme.key_player_time));
            editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        }
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
        closeButton.setContentDescription(getString("Close", R.string.Close));
        actionModeLayout.addView(closeButton, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
        actionModeViews.add(closeButton);
        closeButton.setOnClickListener(v -> closeActionMode());

        selectedMessagesCountTextView = new NumberTextView(context);
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.bold());
        selectedMessagesCountTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        actionModeLayout.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 18, 0, 0, 0));
        actionModeViews.add(selectedMessagesCountTextView);

        if (!DialogObject.isEncryptedDialog(dialog_id)) {
            if (!isStoriesView()) {
                gotoItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
                gotoItem.setIcon(R.drawable.msg_message);
                gotoItem.setContentDescription(getString("AccDescrGoToMessage", R.string.AccDescrGoToMessage));
                gotoItem.setDuplicateParentStateEnabled(false);
                actionModeLayout.addView(gotoItem, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
                actionModeViews.add(gotoItem);
                gotoItem.setOnClickListener(v -> onActionBarItemClick(v, gotochat));

                forwardItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
                forwardItem.setIcon(R.drawable.msg_forward);
                forwardItem.setContentDescription(getString("Forward", R.string.Forward));
                forwardItem.setDuplicateParentStateEnabled(false);
                actionModeLayout.addView(forwardItem, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
                actionModeViews.add(forwardItem);
                forwardItem.setOnClickListener(v -> onActionBarItemClick(v, forward));
            }

            pinItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            pinItem.setIcon(R.drawable.msg_pin);
            pinItem.setContentDescription(getString(R.string.PinMessage));
            pinItem.setDuplicateParentStateEnabled(false);
            pinItem.setVisibility(View.GONE);
            actionModeLayout.addView(pinItem, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
            actionModeViews.add(pinItem);
            pinItem.setOnClickListener(v -> onActionBarItemClick(v, pin));

            unpinItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            unpinItem.setIcon(R.drawable.msg_unpin);
            unpinItem.setContentDescription(getString(R.string.UnpinMessage));
            unpinItem.setDuplicateParentStateEnabled(false);
            unpinItem.setVisibility(View.GONE);
            actionModeLayout.addView(unpinItem, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
            actionModeViews.add(unpinItem);
            unpinItem.setOnClickListener(v -> onActionBarItemClick(v, unpin));

            updateForwardItem();
        }
        deleteItem = new ActionBarMenuItem(context, null, getThemedColor(Theme.key_actionBarActionModeDefaultSelector), getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
        deleteItem.setIcon(R.drawable.msg_delete);
        deleteItem.setContentDescription(getString("Delete", R.string.Delete));
        deleteItem.setDuplicateParentStateEnabled(false);
        actionModeLayout.addView(deleteItem, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT));
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
        channelRecommendationsAdapter = new ChannelRecommendationsAdapter(context);
        savedDialogsAdapter = new SavedDialogsAdapter(context);
        savedMessagesSearchAdapter = new SavedMessagesSearchAdapter(context);
        if (!isStoriesView() && !includeSavedDialogs() && topicId == 0) {
            Bundle args = new Bundle();
            args.putLong("user_id", profileActivity.getUserConfig().getClientUserId());
            args.putInt("chatMode", ChatActivity.MODE_SAVED);
            savedMessagesContainer = new ChatActivityContainer(context, profileActivity.getParentLayout(), args) {
                @Override
                protected void onSearchLoadingUpdate(boolean loading) {
                    if (searchItem != null) {
                        searchItem.setShowSearchProgress(loading);
                    }
                }
            };
            savedMessagesContainer.chatActivity.setSavedDialog(dialog_id);
            savedMessagesContainer.chatActivity.reversed = true;
        }
        chatUsersAdapter = new ChatUsersAdapter(context);
        if (topicId == 0) {
            chatUsersAdapter.sortedUsers = sortedUsers;
            chatUsersAdapter.chatInfo = initialTab == TAB_GROUPUSERS ? chatInfo : null;
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
        storiesReorder = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            private RecyclerListView listView;
            @Override
            public boolean isLongPressDragEnabled() {
                return isActionModeShowed;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (isActionModeShowed && storiesAdapter.canReorder(viewHolder.getAdapterPosition())) {
                    listView = mediaPages[0] == null ? null : mediaPages[0].listView;
                    if (listView != null) {
                        listView.setItemAnimator(mediaPages[0].itemAnimator);
                    }
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
                } else {
                    return makeMovementFlags(0, 0);
                }
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (!storiesAdapter.canReorder(viewHolder.getAdapterPosition()) || !storiesAdapter.canReorder(target.getAdapterPosition())) {
                    return false;
                }
                storiesAdapter.swapElements(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (listView != null && viewHolder != null) {
                    listView.hideSelector(false);
                }
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    storiesAdapter.reorderDone();
                    if (listView != null) {
                        listView.setItemAnimator(null);
                    }
                } else {
                    if (listView != null) {
                        listView.cancelClickRunnables(false);
                    }
                    if (viewHolder != null) {
                        viewHolder.itemView.setPressed(true);
                    }
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setPressed(false);
            }
        });
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
        if (isBot()) {
            botPreviewsContainer = new BotPreviewsEditContainer(context, profileActivity, dialog_id) {
                @Override
                public void onSelectedTabChanged() {
                    SharedMediaLayout.this.onSelectedTabChanged();
                }
                @Override
                protected boolean isSelected(MessageObject messageObject) {
                    return selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0;
                }
                @Override
                protected boolean select(MessageObject messageObject) {
                    if (messageObject == null) return false;
                    final int loadIndex = messageObject.getDialogId() == dialog_id ? 0 : 1;
                    if (selectedFiles[loadIndex].indexOfKey(messageObject.getId()) < 0) {
                        if (selectedFiles[0].size() + selectedFiles[1].size() >= 100) {
                            return false;
                        }
                        selectedFiles[loadIndex].put(messageObject.getId(), messageObject);
                        if (!messageObject.canDeleteMessage(false, null)) {
                            cantDeleteMessagesCount++;
                        }
                        if (!isActionModeShowed) {
                            AndroidUtilities.hideKeyboard(profileActivity.getParentActivity().getCurrentFocus());
                            deleteItem.setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
                            if (gotoItem != null) {
                                gotoItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS ? View.VISIBLE : View.GONE);
                            }
                            if (pinItem != null) {
                                pinItem.setVisibility(View.GONE);
                            }
                            if (unpinItem != null) {
                                unpinItem.setVisibility(View.GONE);
                            }
                            if (forwardItem != null) {
                                forwardItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS ? View.VISIBLE : View.GONE);
                            }
                            selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), false);
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
                            showActionMode(true);
                        } else {
                            selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true);
                        }
                        updateSelection(true);
                        return true;
                    }
                    return false;
                }
                @Override
                protected boolean unselect(MessageObject messageObject) {
                    if (messageObject == null) return false;
                    final int loadIndex = messageObject.getDialogId() == dialog_id ? 0 : 1;
                    if (selectedFiles[loadIndex].indexOfKey(messageObject.getId()) >= 0) {
                        selectedFiles[loadIndex].remove(messageObject.getId());
                        if (!messageObject.canDeleteMessage(false, null)) {
                            cantDeleteMessagesCount--;
                        }
                        if (selectedFiles[0].size() == 0 && selectedFiles[1].size() == 0) {
                            AndroidUtilities.hideKeyboard(profileActivity.getParentActivity().getCurrentFocus());
                            selectedFiles[0].clear();
                            selectedFiles[1].clear();
                            deleteItem.setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
                            if (gotoItem != null) {
                                gotoItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS ? View.VISIBLE : View.GONE);
                            }
                            if (pinItem != null) {
                                pinItem.setVisibility(View.GONE);
                            }
                            if (unpinItem != null) {
                                unpinItem.setVisibility(View.GONE);
                            }
                            if (forwardItem != null) {
                                forwardItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS ? View.VISIBLE : View.GONE);
                            }
                            AnimatorSet animatorSet = new AnimatorSet();
                            ArrayList<Animator> animators = new ArrayList<>();
                            for (int i = 0; i < actionModeViews.size(); i++) {
                                View view2 = actionModeViews.get(i);
                                AndroidUtilities.clearDrawableAnimation(view2);
                                animators.add(ObjectAnimator.ofFloat(view2, View.SCALE_Y, 1.0f, 0.1f));
                            }
                            animatorSet.playTogether(animators);
                            animatorSet.setDuration(250);
                            animatorSet.start();
                            scrolling = false;
                            AndroidUtilities.runOnUIThread(() -> {
                                if (isActionModeShowed) {
                                    showActionMode(false);
                                }
                            }, 20);
                        } else {
                            selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true);
                        }
                        updateSelection(true);
                        return true;
                    }
                    return false;
                }
                @Override
                protected boolean isActionModeShowed() {
                    return isActionModeShowed;
                }
                @Override
                public int getStartedTrackingX() {
                    return startedTrackingX;
                }
            };
        } else if (profileActivity instanceof ProfileActivity) {
            giftsContainer = new ProfileGiftsContainer(context, profileActivity.getCurrentAccount(), ((ProfileActivity) profileActivity).getDialogId(), resourcesProvider) {
                @Override
                protected int processColor(int color) {
                    return SharedMediaLayout.this.processColor(color);
                }
            };
        }

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
                                    searchAlpha = 1.0f - scrollProgress;
                                } else if (searchItemState == 1) {
                                    searchAlpha = scrollProgress;
                                }
                                updateSearchItemIcon(scrollProgress);

                                optionsAlpha = getPhotoVideoOptionsAlpha(scrollProgress);
                                photoVideoOptionsItem.setVisibility((optionsAlpha == 0 || !canShowSearchItem() || isArchivedOnlyStoriesView()) ? INVISIBLE : View.VISIBLE);
                            } else {
                                searchAlpha = 0;
                            }
                            updateOptionsSearch();
                        }
                    }
                    invalidateBlur();
                }
            };
            addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, mediaPageTopMargin(), 0, 0));
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
                        extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], dp(56f) * 2);
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
            mediaPages[a].itemAnimator = new DefaultItemAnimator();
            mediaPages[a].itemAnimator.setDurations(280);
            mediaPages[a].itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            mediaPages[a].itemAnimator.setSupportsChangeAnimations(false);
            mediaPages[a].buttonView = new ButtonWithCounterView(context, resourcesProvider);
            mediaPages[a].buttonView.setText(addPostText(), false);
            mediaPages[a].buttonView.setVisibility(View.GONE);
            mediaPages[a].buttonView.setOnClickListener(v -> {
                if (v.getAlpha() < 0.5f) return;
                profileActivity.getMessagesController().getMainSettings().edit().putBoolean("story_keep", true).apply();
                openStoryRecorder();
            });
            mediaPages[a].listView = new SharedMediaListView(context) {

                @Override
                public RecyclerListView.FastScrollAdapter getMovingAdapter() {
                    if (changeColumnsTab == TAB_STORIES) {
                        return storiesAdapter;
                    } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                        return archivedStoriesAdapter;
                    } else {
                        return photoVideoAdapter;
                    }
                }

                @Override
                public RecyclerListView.FastScrollAdapter getSupportingAdapter() {
                    if (changeColumnsTab == TAB_STORIES) {
                        return animationSupportingStoriesAdapter;
                    } else if (changeColumnsTab == TAB_ARCHIVED_STORIES) {
                        return animationSupportingArchivedStoriesAdapter;
                    } else {
                        return animationSupportingPhotoVideoAdapter;
                    }
                }

                @Override
                public int getColumnsCount() {
                    if (changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES) {
                        return mediaColumnsCount[1];
                    } else {
                        return mediaColumnsCount[0];
                    }
                }

                @Override
                public int getAnimateToColumnsCount() {
                    return animateToColumnsCount;
                }

                @Override
                public boolean isChangeColumnsAnimation() {
                    return photoVideoChangeColumnsAnimation;
                }

                @Override
                public float getChangeColumnsProgress() {
                    return photoVideoChangeColumnsProgress;
                }

                @Override
                public boolean isThisListView() {
                    return this == mediaPage.listView;
                }

                @Override
                public SparseArray<Float> getMessageAlphaEnter() {
                    return messageAlphaEnter;
                }

                @Override
                public boolean isStories() {
                    return changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES;
                }

                @Override
                public InternalListView getSupportingListView() {
                    return mediaPage.animationSupportingListView;
                }

                @Override
                public void checkHighlightCell(SharedPhotoVideoCell2 cell) {
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
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    checkLoadMoreScroll(mediaPage, mediaPage.listView, layoutManager);
                    if (mediaPage.selectedType == 0) {
                        PhotoViewer.getInstance().checkCurrentImageVisibility();
                    }
                }

                float lastY, startY;
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    if (profileActivity != null && profileActivity.isInPreviewMode()) {
                        lastY = event.getY();
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            profileActivity.finishPreviewFragment();
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            float dy = startY - lastY;
                            profileActivity.movePreviewFragment(dy);
                            if (dy < 0) {
                                startY = lastY;
                            }
                        }
                        return true;
                    }
                    return super.dispatchTouchEvent(event);
                }

                @Override
                protected void emptyViewUpdated(boolean shown, boolean animated) {
                    if (getAdapter() == storiesAdapter) {
                        if (animated) {
                            mediaPage.buttonView.animate().alpha(shown ? 0f : 1f).start();
                        } else {
                            mediaPage.buttonView.setAlpha(shown ? 0f : 1f);
                        }
                    }
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if ((getAdapter() == archivedStoriesAdapter || getAdapter() == storiesAdapter) && getChildCount() > 0) {
                        View topChild = getChildAt(0);
                        if (topChild != null && getChildAdapterPosition(topChild) == 0) {
                            int top = topChild.getTop();
                            if (photoVideoChangeColumnsAnimation && changeColumnsTab == (getAdapter() == storiesAdapter ? TAB_STORIES : TAB_ARCHIVED_STORIES) && mediaPage.animationSupportingListView.getChildCount() > 0) {
                                View supportingTopChild = mediaPage.animationSupportingListView.getChildAt(0);
                                if (supportingTopChild != null && mediaPage.animationSupportingListView.getChildAdapterPosition(supportingTopChild) == 0) {
                                    top = lerp(top, supportingTopChild.getTop(), photoVideoChangeColumnsProgress);
                                }
                            }
                            if (getAdapter() == storiesAdapter) {
                                mediaPage.buttonView.setVisibility(View.VISIBLE);
                                mediaPage.buttonView.setTranslationY(getY() + top - dp(72));
                            } else {
                                if (archivedHintPaint == null) {
                                    archivedHintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                                    archivedHintPaint.setTextSize(dp(14));
                                    archivedHintPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                                }
                                int width = getMeasuredWidth() - dp(60);
                                if (archivedHintLayout == null || archivedHintLayout.getWidth() != width) {
                                    boolean isChannel = profileActivity != null && ChatObject.isChannelAndNotMegaGroup(profileActivity.getMessagesController().getChat(-dialog_id));
                                    archivedHintLayout = new StaticLayout(getString(isArchivedOnlyStoriesView() ? (isChannel ? R.string.ProfileStoriesArchiveChannelHint : R.string.ProfileStoriesArchiveGroupHint) : R.string.ProfileStoriesArchiveHint), archivedHintPaint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
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
                                        top - (dp(64) + archivedHintLayout.getHeight()) / 2f
                                );
                                archivedHintLayout.draw(canvas);
                                canvas.restore();
                            }
                        } else if (getAdapter() == storiesAdapter) {
                            mediaPage.buttonView.setTranslationY(-dp(72));
                        }
                    }
                    super.dispatchDraw(canvas);
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
                    if (!isChangeColumnsAnimation()) {
                        changeColumnsTab = -1;
                    }
                }

                @Override
                public Integer getSelectorColor(int position) {
                    if (getAdapter() == channelRecommendationsAdapter && channelRecommendationsAdapter.more > 0 && position == channelRecommendationsAdapter.getItemCount() - 1) {
                        return 0;
                    }
                    return super.getSelectorColor(position);
                }

                @Override
                public void onScrolled(int dx, int dy) {
                    super.onScrolled(dx, dy);
                    if (scrollingByUser && getSelectedTab() == TAB_SAVED_DIALOGS && profileActivity != null) {
                        AndroidUtilities.hideKeyboard(profileActivity.getParentActivity().getCurrentFocus());
                    }
                }
            };
            mediaPages[a].listView.setFastScrollEnabled(RecyclerListView.FastScroll.DATE_TYPE);
            mediaPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            mediaPages[a].listView.setPinnedSectionOffsetY(-dp(2));
            mediaPages[a].listView.setPadding(0, 0, 0, 0);
            mediaPages[a].listView.setItemAnimator(null);
            mediaPages[a].listView.setClipToPadding(false);
            mediaPages[a].listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE);
            mediaPages[a].listView.setLayoutManager(layoutManager);
            mediaPages[a].addView(mediaPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].addView(mediaPages[a].buttonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 12, 12, 12));
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
                            outRect.top = dp(2);
                        } else {
                            outRect.top = 0;
                        }
                        outRect.right = mediaPage.layoutManager.isLastInRow(position) ? 0 : dp(2);
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
                    if (chat.forum) {
                        profileActivity.presentFragment(TopicsFragment.getTopicsOrChat(profileActivity, args));
                    } else {
                        profileActivity.presentFragment(new ChatActivity(args));
                    }
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
                } else if (mediaPage.selectedType == TAB_RECOMMENDED_CHANNELS) {
                    if ((view instanceof ProfileSearchCell || y < dp(60)) && position >= 0 && position < channelRecommendationsAdapter.chats.size()) {
                        Bundle args = new Bundle();
                        args.putLong("chat_id", channelRecommendationsAdapter.chats.get(position).id);
                        profileActivity.presentFragment(new ChatActivity(args));
                    }
                } else if (mediaPage.selectedType == TAB_SAVED_DIALOGS) {
                    if (mediaPage.listView.getAdapter() == savedMessagesSearchAdapter) {
                        if (position < 0) {
                            return;
                        }
                        if (position < savedMessagesSearchAdapter.dialogs.size()) {
                            SavedMessagesController.SavedDialog d = savedMessagesSearchAdapter.dialogs.get(position);

                            Bundle args = new Bundle();
                            args.putLong("user_id", profileActivity.getUserConfig().getClientUserId());
                            args.putInt("chatMode", ChatActivity.MODE_SAVED);
                            ChatActivity chatActivity = new ChatActivity(args);
                            chatActivity.setSavedDialog(d.dialogId);
                            profileActivity.presentFragment(chatActivity);
                            return;
                        }

                        position -= savedMessagesSearchAdapter.dialogs.size();
                        if (position < savedMessagesSearchAdapter.messages.size()) {
                            MessageObject msg = savedMessagesSearchAdapter.messages.get(position);
                            final int pos = position;

                            Bundle args = new Bundle();
                            args.putLong("user_id", profileActivity.getUserConfig().getClientUserId());
                            args.putInt("message_id", msg.getId());
                            ChatActivity chatActivity = new ChatActivity(args) {
                                boolean firstCreateView = true;
                                @Override
                                public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
                                    if (firstCreateView) {
                                        if (searchItem != null) {
                                            openSearchWithText("");
                                            searchItem.setSearchFieldText(savedMessagesSearchAdapter.lastQuery, false);
                                        }
                                        if (actionBarSearchTags != null) {
                                            actionBarSearchTags.setChosen(savedMessagesSearchAdapter.lastReaction, false);
                                        }
                                        profileActivity.getMediaDataController().portSavedSearchResults(getClassGuid(), savedMessagesSearchAdapter.lastReaction, savedMessagesSearchAdapter.lastQuery, savedMessagesSearchAdapter.cachedMessages, savedMessagesSearchAdapter.loadedMessages, pos, savedMessagesSearchAdapter.count, savedMessagesSearchAdapter.endReached);
                                        firstCreateView = false;
                                    }
                                    super.onTransitionAnimationStart(isOpen, backward);
                                }
                            };
                            chatActivity.setHighlightMessageId(msg.getId());
                            profileActivity.presentFragment(chatActivity);
                        }
                        return;
                    }
                    if (isActionModeShowed) {
                        if (savedDialogsAdapter.itemTouchHelper.isIdle()) {
                            savedDialogsAdapter.select(view);
                        }
                        return;
                    }

                    Bundle args = new Bundle();
                    if (position < 0 || position >= savedDialogsAdapter.dialogs.size()) {
                        return;
                    }
                    SavedMessagesController.SavedDialog d = savedDialogsAdapter.dialogs.get(position);
                    args.putLong("user_id", profileActivity.getUserConfig().getClientUserId());
                    args.putInt("chatMode", ChatActivity.MODE_SAVED);
                    ChatActivity chatActivity = new ChatActivity(args);
                    chatActivity.setSavedDialog(d.dialogId);
                    profileActivity.presentFragment(chatActivity);
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
                    if (dy != 0 && (mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_GIF) && !sharedMediaData[0].messages.isEmpty()) {
                        showFloatingDateView();
                    }
                    if (dy != 0 && (mediaPage.selectedType == TAB_PHOTOVIDEO || mediaPage.selectedType == TAB_STORIES || mediaPage.selectedType == TAB_ARCHIVED_STORIES)) {
                        showFastScrollHint(mediaPage, sharedMediaData, true);
                    }
                    mediaPage.listView.checkSection(true);
                    if (mediaPage.fastScrollHintView != null) {
                        mediaPage.invalidate();
                    }
                    invalidateBlur();
                }
            });
            mediaPages[a].listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
                @Override
                public boolean onItemClick(View view, int position, float x, float y) {
                    if (photoVideoChangeColumnsAnimation) {
                        return false;
                    }
                    if (mediaPage.listView.getAdapter() == savedMessagesSearchAdapter) {
                        return false;
                    }
                    if (isActionModeShowed && mediaPage.selectedType != TAB_SAVED_DIALOGS) {
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
                    } else if ((mediaPage.selectedType == TAB_PHOTOVIDEO || mediaPage.selectedType == TAB_ARCHIVED_STORIES || mediaPage.selectedType == TAB_STORIES && canEditStories()) && view instanceof SharedPhotoVideoCell2) {
                        MessageObject messageObject = ((SharedPhotoVideoCell2) view).getMessageObject();
                        if (messageObject != null) {
                            return onItemLongClick(messageObject, view, mediaPage.selectedType);
                        }
                    } else if (mediaPage.selectedType == TAB_RECOMMENDED_CHANNELS) {
                        channelRecommendationsAdapter.openPreview(position);
                        return true;
                    } else if (mediaPage.selectedType == TAB_SAVED_DIALOGS) {
                        savedDialogsAdapter.select(view);
                        return true;
                    }
                    return false;
                }

                @Override
                public void onMove(float dx, float dy) {
                    if (profileActivity != null && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                        profileActivity.movePreviewFragment(dy);
                    }
                }

                @Override
                public void onLongClickRelease() {
                    if (profileActivity != null && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                        profileActivity.finishPreviewFragment();
                    }
                }
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
            mediaPages[a].emptyView.title.setText(getString("NoResult", R.string.NoResult));
            mediaPages[a].emptyView.button.setVisibility(View.GONE);
            mediaPages[a].emptyView.subtitle.setText(getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
            mediaPages[a].emptyView.button.setVisibility(View.GONE);
            mediaPages[a].emptyView.addView(mediaPages[a].progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            mediaPages[a].listView.setEmptyView(mediaPages[a].emptyView);
            mediaPages[a].listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

            mediaPages[a].scrollHelper = new RecyclerAnimationScrollHelper(mediaPages[a].listView, mediaPages[a].layoutManager);
        }

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setOverrideColor(Theme.key_chat_mediaTimeBackground, Theme.key_chat_mediaTimeText);
        floatingDateView.setTranslationY(-dp(48));
        addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48 + 4, 0, 0));

        if (!customTabs()) {
            addView(fragmentContextView = new FragmentContextView(context, parent, this, false, resourcesProvider), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));
            fragmentContextView.setDelegate((start, show) -> {
                if (!start) {
                    requestLayout();
                }
                setVisibleHeight(lastVisibleHeight);
            });

            addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            searchTagsList = new SearchTagsList(getContext(), profileActivity, null, profileActivity.getCurrentAccount(), includeSavedDialogs() ? 0 : dialog_id, resourcesProvider, false) {
                @Override
                protected boolean setFilter(ReactionsLayoutInBubble.VisibleReaction reaction) {
                    if (searchItem == null) return false;
                    searchingReaction = reaction;
                    final String text = searchItem.getSearchField().getText().toString();
                    searchWas = text.length() != 0 || searchingReaction != null;
                    switchToCurrentSelectedMode(false);
                    if (mediaPages[0].selectedType == TAB_SAVED_DIALOGS) {
                        if (savedMessagesSearchAdapter != null) {
                            savedMessagesSearchAdapter.search(text, searchingReaction);
                        }
                        AndroidUtilities.hideKeyboard(searchItem.getSearchField());
                    } else if (mediaPages[0].selectedType == TAB_SAVED_MESSAGES) {
                        if (savedMessagesContainer != null) {
                            savedMessagesContainer.chatActivity.setTagFilter(reaction);
                        }
                    }
                    return true;
                }

                @Override
                protected void onShownUpdate(boolean finish) {
                    scrollSlidingTextTabStrip.setAlpha(1f - shownT);
                    scrollSlidingTextTabStrip.setPivotX(scrollSlidingTextTabStrip.getWidth() / 2f);
                    scrollSlidingTextTabStrip.setScaleX(.8f + .2f * (1f - shownT));
                    scrollSlidingTextTabStrip.setPivotY(dp(48));
                    scrollSlidingTextTabStrip.setScaleY(.8f + .2f * (1f - shownT));
                }

                @Override
                public void updateTags(boolean notify) {
                    super.updateTags(notify);
                    show(searching && (getSelectedTab() == TAB_SAVED_DIALOGS || getSelectedTab() == TAB_SAVED_MESSAGES) && searchTagsList.hasFilters());
                    if (searchItemIcon != null) {
                        searchItemIcon.setIcon(hasFilters() && profileActivity.getUserConfig().isPremium() ? R.drawable.navbar_search_tag : R.drawable.ic_ab_search, notify);
                    }
                    if (searchItem != null) {
                        searchItem.setSearchFieldHint(getString(searchTagsList != null && searchTagsList.hasFilters() && getSelectedTab() == TAB_SAVED_DIALOGS ? R.string.SavedTagSearchHint : R.string.Search));
                    }
                }
            };
            searchTagsList.setShown(0f);
            searchTagsList.attach();
            addView(searchTagsList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 0, 4, 0, 0));
            addView(actionModeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
        }

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(getThemedColor(Theme.key_divider));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        layoutParams.topMargin = customTabs() ? 0 : dp(48) - 1;
        addView(shadowLine, layoutParams);

        updateTabs(false);
        switchToCurrentSelectedMode(false);
        if (hasMedia[0] >= 0) {
            loadFastScrollData(false);
        }
    }

    protected boolean customTabs() {
        return false;
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

    protected boolean includeSavedDialogs() {
        return false;
    }

    protected boolean isBot() {
        if (dialog_id > 0) {
            final int currentAccount = profileActivity.getCurrentAccount();
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog_id);
            return user != null && user.bot;
        }
        return false;
    }

    protected boolean isSelf() {
        return false;
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

    public int mediaPageTopMargin() {
        return customTabs() ? 0 : 48;
    }

    protected void invalidateBlur() {

    }

    public void setForwardRestrictedHint(HintView hintView) {
        fwdRestrictedHint = hintView;
    }

    private static int getMessageId(View child) {
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
        bundle.putLong("topic_id", topicId);
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
            if (animateToColumnsCount == mediaColumnsCount[ci] || allowStoriesSingleColumn && (changeColumnsTab == TAB_STORIES || changeColumnsTab == TAB_ARCHIVED_STORIES)) {
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
                changeColumnsTab == TAB_ARCHIVED_STORIES ? dp(64) : 0,
                mediaPage.animationSupportingListView.getPaddingRight(),
                isStoriesView() ? dp(72) : 0
            );
            mediaPage.buttonView.setVisibility(changeColumnsTab == TAB_STORIES && isStoriesView() ? View.VISIBLE : View.GONE);

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
                (mediaPage.animationSupportingListView.hintPaddingTop = (changeColumnsTab == TAB_ARCHIVED_STORIES ? dp(64) : 0)),
                mediaPage.animationSupportingListView.getPaddingRight(),
                (mediaPage.animationSupportingListView.hintPaddingBottom = (isStoriesView() ? dp(72) : 0))
            );
            mediaPage.buttonView.setVisibility(changeColumnsTab == TAB_STORIES ? View.VISIBLE : View.GONE);
            mediaPage.buttonView.setVisibility(changeColumnsTab == TAB_STORIES ? View.VISIBLE : View.GONE);
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
                            if (i == 0) {
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

    protected int processColor(int color) {
        return color;
    }

    private ScrollSlidingTextTabStripInner createScrollingTextTabStrip(Context context) {
        ScrollSlidingTextTabStripInner scrollSlidingTextTabStrip = new ScrollSlidingTextTabStripInner(context, resourcesProvider) {
            @Override
            protected int processColor(int color) {
                return SharedMediaLayout.this.processColor(color);
            }
        };
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
                animateSearchToOptions(!isSearchItemVisible(id), true);
                updateOptionsSearch(true);
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

                optionsAlpha = getPhotoVideoOptionsAlpha(progress);
                photoVideoOptionsItem.setVisibility((optionsAlpha == 0 || !canShowSearchItem() || isArchivedOnlyStoriesView()) ? INVISIBLE : View.VISIBLE);
                if (searchItem != null && !canShowSearchItem()) {
                    searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                    searchAlpha = 0.0f;
                } else {
                    searchAlpha = getSearchAlpha(progress);
                    updateSearchItemIconAnimated();
                }
                updateOptionsSearch();
                if (progress == 1) {
                    MediaPage tempPage = mediaPages[0];
                    mediaPages[0] = mediaPages[1];
                    mediaPages[1] = tempPage;
                    mediaPages[1].setVisibility(View.GONE);
                    if (searchItem != null && searchItemState == 2) {
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
                    ObjectAnimator.ofFloat(floatingDateView, View.TRANSLATION_Y, -dp(48) + additionalFloatingTranslation));
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
                height = dp(56);
                break;
            case 3:
                height = dp(100);
                break;
            case 5:
                height = dp(60);
                break;
            case 6:
            default:
                height = dp(58);
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
        if (searching && searchWas && mediaPage.selectedType != TAB_SAVED_DIALOGS || mediaPage.selectedType == TAB_GROUPUSERS) {
            return;
        }
        int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
        int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        int totalItemCount = recyclerView.getAdapter() == null ? 0 : recyclerView.getAdapter().getItemCount();
        if (mediaPage.selectedType == TAB_PHOTOVIDEO || mediaPage.selectedType == TAB_FILES || mediaPage.selectedType == TAB_VOICE || mediaPage.selectedType == TAB_AUDIO) {
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

        if (mediaPage.selectedType == TAB_GROUPUSERS) {

        } else if (mediaPage.selectedType == TAB_STORIES) {
            if (storiesAdapter.storiesList != null && firstVisibleItem + visibleItemCount > storiesAdapter.storiesList.getLoadedCount() - mediaColumnsCount[1]) {
                storiesAdapter.load(false);
            }
        } else if (mediaPage.selectedType == TAB_ARCHIVED_STORIES) {
            if (archivedStoriesAdapter.storiesList != null && firstVisibleItem + visibleItemCount > archivedStoriesAdapter.storiesList.getLoadedCount() - mediaColumnsCount[1]) {
                archivedStoriesAdapter.load(false);
            }
        } else if (mediaPage.selectedType == TAB_COMMON_GROUPS) {
            if (visibleItemCount > 0) {
                if (!commonGroupsAdapter.endReached && !commonGroupsAdapter.loading && !commonGroupsAdapter.chats.isEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
                    commonGroupsAdapter.getChats(commonGroupsAdapter.chats.get(commonGroupsAdapter.chats.size() - 1).id, 100);
                }
            }
        } else if (mediaPage.selectedType == TAB_SAVED_DIALOGS) {
            int lastVisiblePosition = -1;
            for (int i = 0; i < mediaPage.listView.getChildCount(); ++i) {
                View child = mediaPage.listView.getChildAt(i);
                int position = mediaPage.listView.getChildAdapterPosition(child);
                lastVisiblePosition = Math.max(position, lastVisiblePosition);
            }
            if (mediaPage.listView.getAdapter() == savedMessagesSearchAdapter) {
                if (lastVisiblePosition + 1 >= savedMessagesSearchAdapter.dialogs.size() + savedMessagesSearchAdapter.loadedMessages.size()) {
                    savedMessagesSearchAdapter.loadMore();
                }
                return;
            }
            if (lastVisiblePosition + 1 >= profileActivity.getMessagesController().getSavedMessagesController().getLoadedCount()) {
                profileActivity.getMessagesController().getSavedMessagesController().loadDialogs(false);
            }
        } else if (mediaPage.selectedType != TAB_RECOMMENDED_CHANNELS && mediaPage.selectedType != TAB_SAVED_MESSAGES && mediaPage.selectedType != TAB_BOT_PREVIEWS && mediaPage.selectedType != TAB_GIFTS) {
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
                    profileActivity.getMediaDataController().loadMedia(dialog_id, 50, sharedMediaData[mediaPage.selectedType].max_id[0], 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[mediaPage.selectedType].requestIndex, null, null);
                } else if (mergeDialogId != 0 && !sharedMediaData[mediaPage.selectedType].endReached[1]) {
                    sharedMediaData[mediaPage.selectedType].loading = true;
                    profileActivity.getMediaDataController().loadMedia(mergeDialogId, 50, sharedMediaData[mediaPage.selectedType].max_id[1], 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[mediaPage.selectedType].requestIndex, null, null);
                }
            }

            int startOffset = sharedMediaData[mediaPage.selectedType].startOffset;
            if (mediaPage.selectedType == 0) {
                startOffset = photoVideoAdapter.getPositionForIndex(0);
            }
            if (firstVisibleItem - startOffset < threshold + 1 && !sharedMediaData[mediaPage.selectedType].loading && !sharedMediaData[mediaPage.selectedType].startReached && !sharedMediaData[mediaPage.selectedType].loadingAfterFastScroll) {
                loadFromStart(mediaPage.selectedType);
            }
            if (mediaPages[0].listView == recyclerView && (mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_GIF) && firstVisibleItem != RecyclerView.NO_POSITION) {
                RecyclerListView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem);
                if (holder != null && (holder.getItemViewType() == VIEW_TYPE_PHOTOVIDEO || holder.getItemViewType() == VIEW_TYPE_GIF)) {
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
        profileActivity.getMediaDataController().loadMedia(dialog_id, 50, 0, sharedMediaData[selectedType].min_id, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[selectedType].requestIndex, null, null);
    }

    public ActionBarMenuItem getSearchItem() {
        return searchItem;
    }

    public RLottieImageView getSearchOptionsItem() {
        return optionsSearchImageView;
    }

    public boolean isSearchItemVisible() {
        return isSearchItemVisible(mediaPages[0].selectedType);
    }

    public boolean isSearchItemVisible(int type) {
        if (type == TAB_GROUPUSERS) {
            return delegate.canSearchMembers();
        }
        if (isSearchingStories()) {
            return false;
        }
        return (
            type != TAB_PHOTOVIDEO &&
            type != TAB_STORIES &&
            type != TAB_ARCHIVED_STORIES &&
            type != TAB_VOICE &&
            type != TAB_GIF &&
            type != TAB_COMMON_GROUPS &&
            type != TAB_SAVED_DIALOGS &&
            type != TAB_RECOMMENDED_CHANNELS &&
            type != TAB_BOT_PREVIEWS
        );
    }

    public boolean isTabZoomable(int type) {
        return type == TAB_PHOTOVIDEO || type == TAB_STORIES || type == TAB_ARCHIVED_STORIES;
    }

    public boolean isCalendarItemVisible() {
        return mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES || mediaPages[0].selectedType == TAB_SAVED_DIALOGS;
    }

    public boolean isOptionsItemVisible() {
        return mediaPages[0].selectedType == TAB_PHOTOVIDEO || mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES || mediaPages[0].selectedType == TAB_SAVED_DIALOGS || mediaPages[0].selectedType == TAB_BOT_PREVIEWS;
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
        if (searchItem != null) {
            searchItem.setSearchFieldHint(getString(searchTagsList != null && searchTagsList.hasFilters() && getSelectedTab() == TAB_SAVED_DIALOGS ? R.string.SavedTagSearchHint : R.string.Search));
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
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.channelRecommendationsLoaded);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        profileActivity.getNotificationCenter().removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        if (searchTagsList != null) {
            searchTagsList.detach();
        }

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
        if (topicId != 0 || isSearchingStories()) {
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
            req.peer = profileActivity.getMessagesController().getInputPeer(dialog_id);
            if (topicId != 0) {
                if (profileActivity.getUserConfig().getClientUserId() == dialog_id) {
                    req.flags |= 4;
                    req.saved_peer_id = profileActivity.getMessagesController().getInputPeer(topicId);
                }
            }
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
            if (getSelectedTab() == TAB_STORIES || getSelectedTab() == TAB_ARCHIVED_STORIES || getSelectedTab() == TAB_BOT_PREVIEWS) {
                if (selectedFiles[0] != null) {
                    if (isBot() && botPreviewsContainer != null && botPreviewsContainer.getCurrentList() != null) {
                        final StoriesController.BotPreviewsList list = botPreviewsContainer.getCurrentList();
                        ArrayList<TLRPC.MessageMedia> medias = new ArrayList<>();
                        for (int i = 0; i < selectedFiles[0].size(); ++i) {
                            MessageObject messageObject = selectedFiles[0].valueAt(i);
                            if (messageObject.storyItem != null) {
                                medias.add(messageObject.storyItem.media);
                            }
                        }
                        if (!medias.isEmpty()) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
                            builder.setTitle(medias.size() > 1 ? LocaleController.getString(R.string.DeleteBotPreviewsTitle) : LocaleController.getString(R.string.DeleteBotPreviewTitle));
                            builder.setMessage(LocaleController.formatPluralString("DeleteBotPreviewsSubtitle", medias.size()));
                            builder.setPositiveButton(LocaleController.getString(R.string.Delete), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    list.delete(medias);
                                    BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.ic_delete, LocaleController.formatPluralString("BotPreviewsDeleted", medias.size())).show();
                                    closeActionMode(false);
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (DialogInterface.OnClickListener) (dialog, which) -> {
                                dialog.dismiss();
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            dialog.redPositive();
                        }
                    } else {
                        ArrayList<TL_stories.StoryItem> storyItems = new ArrayList<>();
                        for (int i = 0; i < selectedFiles[0].size(); ++i) {
                            MessageObject messageObject = selectedFiles[0].valueAt(i);
                            if (messageObject.storyItem != null) {
                                storyItems.add(messageObject.storyItem);
                            }
                        }
                        if (!storyItems.isEmpty()) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
                            builder.setTitle(storyItems.size() > 1 ? LocaleController.getString(R.string.DeleteStoriesTitle) : LocaleController.getString(R.string.DeleteStoryTitle));
                            builder.setMessage(LocaleController.formatPluralString("DeleteStoriesSubtitle", storyItems.size()));
                            builder.setPositiveButton(LocaleController.getString(R.string.Delete), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    profileActivity.getMessagesController().getStoriesController().deleteStories(dialog_id, storyItems);
                                    BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.ic_delete, LocaleController.formatPluralString("StoriesDeleted", storyItems.size())).show();
                                    closeActionMode(false);
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (DialogInterface.OnClickListener) (dialog, which) -> {
                                dialog.dismiss();
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            dialog.redPositive();
                        }
                    }
                }
                return;
            } else if (getSelectedTab() == TAB_SAVED_DIALOGS) {
                final SavedMessagesController controller = profileActivity.getMessagesController().getSavedMessagesController();
                final ArrayList<Long> selectedDialogs = new ArrayList<>();
                for (int i = 0; i < controller.allDialogs.size(); ++i) {
                    final long did = controller.allDialogs.get(i).dialogId;
                    if (savedDialogsAdapter.selectedDialogs.contains(did)) {
                        selectedDialogs.add(did);
                    }
                }
                boolean firstDialogSelf = false;
                String firstDialog = "";
                if (!selectedDialogs.isEmpty()) {
                    long did = selectedDialogs.get(0);
                    firstDialogSelf = did == profileActivity.getUserConfig().getClientUserId();
                    if (did < 0) {
                        TLRPC.Chat chat = profileActivity.getMessagesController().getChat(-did);
                        if (chat != null) {
                            firstDialog = chat.title;
                        }
                    } else if (did >= 0) {
                        TLRPC.User user = profileActivity.getMessagesController().getUser(did);
                        if (user != null) {
                            if (UserObject.isAnonymous(user)) {
                                firstDialog = getString(R.string.AnonymousForward);
                            } else {
                                firstDialog = UserObject.getUserName(user);
                            }
                        }
                    }
                }
                AlertDialog dialog = new AlertDialog.Builder(getContext(), resourcesProvider)
                    .setTitle(selectedDialogs.size() == 1 ? LocaleController.formatString(firstDialogSelf ? R.string.ClearHistoryMyNotesTitle : R.string.ClearHistoryTitleSingle2, firstDialog) : LocaleController.formatPluralString("ClearHistoryTitleMultiple", selectedDialogs.size()))
                    .setMessage(selectedDialogs.size() == 1 ? LocaleController.formatString(firstDialogSelf ? R.string.ClearHistoryMyNotesMessage : R.string.ClearHistoryMessageSingle, firstDialog) : LocaleController.formatPluralString("ClearHistoryMessageMultiple", selectedDialogs.size()))
                    .setPositiveButton(getString(R.string.Remove), (di, w) -> {
                        for (int i = 0; i < selectedDialogs.size(); ++i) {
                            final long did = selectedDialogs.get(i);
                            profileActivity.getMessagesController().deleteSavedDialog(did);
                        }
                        closeActionMode();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .create();
                profileActivity.showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
                return;
            }
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
            AlertsCreator.createDeleteMessagesAlert(profileActivity, currentUser, currentChat, currentEncryptedChat, null, mergeDialogId, null, selectedFiles, null, 0, 0, null, () -> {
                showActionMode(false);
                actionBar.closeSearchField();
                cantDeleteMessagesCount = 0;
            }, null, resourcesProvider);
        } else if (id == forward) {
            if (info != null) {
                TLRPC.Chat chat = profileActivity.getMessagesController().getChat(info.id);
                if (profileActivity.getMessagesController().isChatNoForwards(chat)) {
                    if (fwdRestrictedHint != null) {
                        fwdRestrictedHint.setText(ChatObject.isChannel(chat) && !chat.megagroup ? getString("ForwardsRestrictedInfoChannel", R.string.ForwardsRestrictedInfoChannel) :
                                getString("ForwardsRestrictedInfoGroup", R.string.ForwardsRestrictedInfoGroup));
                        fwdRestrictedHint.showForView(v, true);
                    }
                    return;
                }
            }
            if (hasNoforwardsMessage()) {
                if (fwdRestrictedHint != null) {
                    fwdRestrictedHint.setText(getString("ForwardsRestrictedInfoBot", R.string.ForwardsRestrictedInfoBot));
                    fwdRestrictedHint.showForView(v, true);
                }
                return;
            }

            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("canSelectTopics", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, topicsFragment) -> {
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
                if (savedDialogsAdapter != null) {
                    savedDialogsAdapter.unselectAll();
                }

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
        } else if (id == pin || id == unpin) {
            if (getClosestTab() == TAB_STORIES) {
                if (storiesAdapter == null || storiesAdapter.storiesList == null)
                    return;
                ArrayList<Integer> ids = new ArrayList<>();
                for (int i = 0; i < selectedFiles[0].size(); ++i) {
                    ids.add(selectedFiles[0].valueAt(i).getId());
                }
                if (id == pin && ids.size() > profileActivity.getMessagesController().storiesPinnedToTopCountMax) {
                    BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.chats_infotip, AndroidUtilities.replaceTags(formatPluralString("StoriesPinLimit", profileActivity.getMessagesController().storiesPinnedToTopCountMax))).show();
                    return;
                }

                if (storiesAdapter.storiesList.updatePinned(ids, id == pin)) {
                    BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.chats_infotip, AndroidUtilities.replaceTags(formatPluralString("StoriesPinLimit", profileActivity.getMessagesController().storiesPinnedToTopCountMax))).show();
                } else {
                    if (id == pin) {
                        BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.ic_pin, AndroidUtilities.replaceTags(formatPluralString("StoriesPinned", ids.size())), formatPluralString("StoriesPinnedText", ids.size())).show();
                    } else {
                        BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.ic_unpin, AndroidUtilities.replaceTags(formatPluralString("StoriesUnpinned", ids.size()))).show();
                    }

                }

                closeActionMode(false);
//                if (profileActivity == null) return;
//                final long dialogId = profileActivity.getUserConfig().getClientUserId();
//                if (applyBulletin != null) {
//                    applyBulletin.run();
//                    applyBulletin = null;
//                }
//                Bulletin.hideVisible();
//                boolean pin = getClosestTab() == SharedMediaLayout.TAB_ARCHIVED_STORIES;
//                int count = 0;
//                ArrayList<TL_stories.StoryItem> storyItems = new ArrayList<>();
//                SparseArray<MessageObject> actionModeMessageObjects = getActionModeSelected();
//                if (actionModeMessageObjects != null) {
//                    for (int i = 0; i < actionModeMessageObjects.size(); ++i) {
//                        MessageObject messageObject = actionModeMessageObjects.valueAt(i);
//                        if (messageObject.storyItem != null) {
//                            storyItems.add(messageObject.storyItem);
//                            count++;
//                        }
//                    }
//                }
//                closeActionMode(false);
//                if (pin) {
//                    scrollToPage(SharedMediaLayout.TAB_STORIES);
//                    scrollSlidingTextTabStrip.selectTabWithId(SharedMediaLayout.TAB_STORIES, 1f);
//                }
//                if (storyItems.isEmpty()) {
//                    return;
//                }
//                boolean[] pastValues = new boolean[storyItems.size()];
//                for (int i = 0; i < storyItems.size(); ++i) {
//                    TL_stories.StoryItem storyItem = storyItems.get(i);
//                    pastValues[i] = storyItem.pinned;
//                    storyItem.pinned = pin;
//                }
//                profileActivity.getMessagesController().getStoriesController().updateStoriesInLists(dialogId, storyItems);
//                final boolean[] undone = new boolean[] { false };
//                applyBulletin = () -> {
//                    profileActivity.getMessagesController().getStoriesController().updateStoriesPinned(dialogId, storyItems, pin, null);
//                };
//                final Runnable undo = () -> {
//                    undone[0] = true;
//                    AndroidUtilities.cancelRunOnUIThread(applyBulletin);
//                    for (int i = 0; i < storyItems.size(); ++i) {
//                        TL_stories.StoryItem storyItem = storyItems.get(i);
//                        storyItem.pinned = pastValues[i];
//                    }
//                    profileActivity.getMessagesController().getStoriesController().updateStoriesInLists(dialogId, storyItems);
//                };
//                Bulletin bulletin;
//                if (pin) {
//                    bulletin = BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.contact_check, LocaleController.formatPluralString("StorySavedTitle", count), LocaleController.getString(R.string.StorySavedSubtitle), LocaleController.getString(R.string.Undo), undo).show();
//                } else {
//                    bulletin = BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.chats_archived, LocaleController.formatPluralString("StoryArchived", count), LocaleController.getString(R.string.Undo), Bulletin.DURATION_PROLONG, undo).show();
//                }
//                bulletin.setOnHideListener(() -> {
//                    if (!undone[0] && applyBulletin != null) {
//                        applyBulletin.run();
//                    }
//                    applyBulletin = null;
//                });
            } else {
                final SavedMessagesController controller = profileActivity.getMessagesController().getSavedMessagesController();
                final ArrayList<Long> selectedDialogs = new ArrayList<>();
                for (int i = 0; i < controller.allDialogs.size(); ++i) {
                    final long did = controller.allDialogs.get(i).dialogId;
                    if (savedDialogsAdapter.selectedDialogs.contains(did)) {
                        selectedDialogs.add(did);
                    }
                }
                if (!controller.updatePinned(selectedDialogs, id == pin, true)) {
                    LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(profileActivity, getContext(), LimitReachedBottomSheet.TYPE_PIN_SAVED_DIALOGS, profileActivity.getCurrentAccount(), null);
                    profileActivity.showDialog(limitReachedBottomSheet);
                } else {
                    for (int i = 0; i < mediaPages.length; ++i) {
                        if (mediaPages[i].selectedType == TAB_SAVED_DIALOGS) {
                            mediaPages[i].layoutManager.scrollToPositionWithOffset(0, 0);
                            break;
                        }
                    }
                }
                closeActionMode(true);
            }
        }
    }

    private boolean prepareForMoving(MotionEvent ev, boolean forward) {
        int id = scrollSlidingTextTabStrip.getNextPageId(forward);
        if (id < 0) {
            return false;
        }
        if (searchItem != null && !canShowSearchItem()) {
            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
            searchAlpha = 0;
        } else {
            searchAlpha = getSearchAlpha(0);
            updateSearchItemIcon(0);
        }
        if (searching && getSelectedTab() == TAB_SAVED_DIALOGS) {
            return false;
        }
        if (canEditStories() && isActionModeShowed && getClosestTab() == TAB_STORIES) {
            return false;
        }
        if (mediaPages[0] != null && mediaPages[0].selectedType == TAB_BOT_PREVIEWS && botPreviewsContainer != null && !botPreviewsContainer.canScroll(forward)) {
            return false;
        }
        if (isActionModeShowed && mediaPages[0] != null && mediaPages[0].selectedType == TAB_BOT_PREVIEWS) {
            return false;
        }
        updateOptionsSearch();

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
            fragmentContextView.setTranslationY(dp(48) + top);
        }
        additionalFloatingTranslation = top;
        floatingDateView.setTranslationY((floatingDateView.getTag() == null ? -dp(48) : 0) + additionalFloatingTranslation);
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
                ((MediaPage) child).listView.setPadding(0, ((MediaPage) child).listView.topPadding, 0, ((MediaPage) child).listView.bottomPadding);
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
        if (mediaPages[0].selectedType == TAB_BOT_PREVIEWS) {
            return botPreviewsContainer.getCurrentListView();
        }
        if (mediaPages[0].selectedType == TAB_GIFTS) {
            return giftsContainer.getCurrentListView();
        }
        if (mediaPages[0].selectedType == TAB_SAVED_MESSAGES && savedMessagesContainer != null) {
            return savedMessagesContainer.chatActivity.getChatListView();
        }
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
            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking && ev.getY() >= dp(48)) {
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
                    if (!canShowSearchItem()) {
                        searchAlpha = 0;
                    } else {
                        searchAlpha = getSearchAlpha(scrollProgress);
                        updateSearchItemIcon(scrollProgress);
                        optionsAlpha = getPhotoVideoOptionsAlpha(scrollProgress);
                        photoVideoOptionsItem.setVisibility((optionsAlpha == 0  || !canShowSearchItem() || isArchivedOnlyStoriesView()) ? INVISIBLE : View.VISIBLE);
                    }
                    updateOptionsSearch();
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
                        if (searchItem != null && !canShowSearchItem()) {
                            searchItem.setVisibility(isStoriesView() ? View.GONE : INVISIBLE);
                            searchAlpha = 0;
                        } else {
                            searchAlpha = getSearchAlpha(0);
                            updateSearchItemIcon(0);
                        }
                        updateOptionsSearch();
                        searchItemState = 0;
                    } else {
                        MediaPage tempPage = mediaPages[0];
                        mediaPages[0] = mediaPages[1];
                        mediaPages[1] = tempPage;
                        mediaPages[1].setVisibility(View.GONE);
                        if (searchItem != null && searchItemState == 2) {
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
            onActionModeSelectedUpdate(selectedFiles[0]);
            if (botPreviewsContainer != null) {
                botPreviewsContainer.unselectAll();
                botPreviewsContainer.updateSelection(true);
            }
            showActionMode(false);
            updateRowsSelection(uncheckAnimated);
            if (savedDialogsAdapter != null) {
                savedDialogsAdapter.unselectAll();
            }
            return true;
        } else {
            return false;
        }
    }

    private int lastVisibleHeight;
    public void setVisibleHeight(int height) {
        lastVisibleHeight = height;
        for (int a = 0; a < mediaPages.length; a++) {
            float t = -(getMeasuredHeight() - Math.max(height, dp(mediaPages[a].selectedType == TAB_STORIES ? 280 : 120))) / 2f;
            mediaPages[a].emptyView.setTranslationY(t);
            mediaPages[a].progressView.setTranslationY(-t);
        }
        if (botPreviewsContainer != null) {
            botPreviewsContainer.setVisibleHeight(height);
        }
        if (giftsContainer != null) {
            int h = height - dp(48);
            if (fragmentContextView != null && fragmentContextView.getVisibility() == View.VISIBLE) {
                h -= fragmentContextView.getHeight() - dp(2.5f);
            }
            giftsContainer.setVisibleHeight(h);
        }
    }

    protected void onActionModeSelectedUpdate(SparseArray<MessageObject> messageObjects) {

    }

    public SparseArray<MessageObject> getActionModeSelected() {
        return selectedFiles[0];
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
        if (show) {
            updateStoriesPinButton();
        }
    }

    private void updateStoriesPinButton() {
        if (isBot()) {
            if (pinItem != null) {
                pinItem.setVisibility(View.GONE);
            }
            if (unpinItem != null) {
                unpinItem.setVisibility(View.GONE);
            }
        } else if (getClosestTab() == TAB_ARCHIVED_STORIES) {
            if (pinItem != null) {
                pinItem.setVisibility(View.GONE);
            }
            if (unpinItem != null) {
                unpinItem.setVisibility(View.GONE);
            }
        } else if (getClosestTab() == TAB_STORIES) {
            boolean hasUnpinned = false;
            for (int i = 0; i < selectedFiles[0].size(); ++i) {
                MessageObject msg = selectedFiles[0].valueAt(i);
                if (storiesAdapter != null && storiesAdapter.storiesList != null && !storiesAdapter.storiesList.isPinned(msg.getId())) {
                    hasUnpinned = true;
                    break;
                }
            }
            if (pinItem != null) {
                pinItem.setVisibility(hasUnpinned ? View.VISIBLE : View.GONE);
            }
            if (unpinItem != null) {
                unpinItem.setVisibility(!hasUnpinned ? View.VISIBLE : View.GONE);
            }
        }
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
                    profileActivity.getMediaDataController().loadMedia(mergeDialogId, 50, sharedMediaData[type].max_id[1], 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[type].requestIndex, null, null);
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
                if (page != null) {
                    AndroidUtilities.notifyDataSetChanged(page.listView);
                    if (page.listView.getLayoutManager() instanceof LinearLayoutManager) {
                        checkLoadMoreScroll(page, page.listView, (LinearLayoutManager) page.listView.getLayoutManager());
                    }
                }
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
                if (page != null) {
                    AndroidUtilities.notifyDataSetChanged(page.listView);
                    if (page.listView.getLayoutManager() instanceof LinearLayoutManager) {
                        checkLoadMoreScroll(page, page.listView, (LinearLayoutManager) page.listView.getLayoutManager());
                    }
                }
                if (delegate != null) {
                    delegate.updateSelectedMediaTabText();
                }
            }
        } else if (id == NotificationCenter.storiesUpdated) {
            for (int i = 0; i < mediaPages.length; ++i) {
                if (mediaPages[i] != null && mediaPages[i].listView != null && (mediaPages[i].selectedType == TAB_STORIES || mediaPages[i].selectedType == TAB_ARCHIVED_STORIES)) {
                    if (isBot() && mediaPages[i].listView.getAdapter() != null) {
                        AndroidUtilities.notifyDataSetChanged(mediaPages[i].listView);
                    } else {
                        for (int j = 0; j < mediaPages[i].listView.getChildCount(); ++j) {
                            View child = mediaPages[i].listView.getChildAt(j);
                            if (child instanceof SharedPhotoVideoCell2) {
                                ((SharedPhotoVideoCell2) child).updateViews();
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.channelRecommendationsLoaded) {
            long chatId = (long) args[0];
            if (chatId == -dialog_id) {
                channelRecommendationsAdapter.update(true);
                updateTabs(true);
                checkCurrentTabValid();
            }
        } else if (id == NotificationCenter.savedMessagesDialogsUpdate) {
            if (dialog_id == 0 || dialog_id == profileActivity.getUserConfig().getClientUserId()) {
                savedDialogsAdapter.update(true);
                updateTabs(true);
                checkCurrentTabValid();
                onSelectedTabChanged();
            }
        } else if (id == NotificationCenter.dialogsNeedReload) {
            savedDialogsAdapter.update(true);
        } else if (id == NotificationCenter.starUserGiftsLoaded) {
            long dialogId = (long) args[0];
            if (dialogId == dialog_id) {
                updateTabs(true);
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
        if (savedMessagesContainer != null) {
            savedMessagesContainer.onResume();
        }
    }

    public void onPause() {
        if (savedMessagesContainer != null) {
            savedMessagesContainer.onPause();
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
                if (mediaPages[a].listView.getAdapter() != null && mediaPages[a].listView.getAdapter().getItemCount() != 0 && profileActivity.getMessagesController().getStoriesController().hasLoadingStories()) {
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
            if (mediaPages[a].selectedType == TAB_GROUPUSERS && mediaPages[a].listView.getAdapter() != null) {
                AndroidUtilities.notifyDataSetChanged(mediaPages[a].listView);
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
                } else if (child instanceof DialogCell) {
                    ((DialogCell) child).setChecked(false, animated);
                }
            }
        }
    }

    public void setMergeDialogId(long did) {
        mergeDialogId = did;
    }

    private long giftsLastHash;
    private void updateTabs(boolean animated) {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        if (!delegate.isFragmentOpened()) {
            animated = false;
        }
        boolean hasRecommendations = false;
        boolean hasSavedDialogs = false;
        boolean hasSavedMessages = savedMessagesContainer != null && sharedMediaPreloader != null && sharedMediaPreloader.hasSavedMessages;
        final TLRPC.User user = dialog_id <= 0 || profileActivity == null ? null : profileActivity.getMessagesController().getUser(dialog_id);
        boolean hasEditBotPreviews = user != null && user.bot && user.bot_has_main_app && user.bot_can_edit;
        boolean hasBotPreviews = user != null && user.bot && !user.bot_can_edit && (userInfo != null && userInfo.bot_info != null && userInfo.bot_info.has_preview_medias) && !hasEditBotPreviews;
        boolean hasStories = (DialogObject.isUserDialog(dialog_id) || DialogObject.isChatDialog(dialog_id)) && !DialogObject.isEncryptedDialog(dialog_id) && (userInfo != null && userInfo.stories_pinned_available || info != null && info.stories_pinned_available || isStoriesView()) && includeStories();
        boolean hasGifts = giftsContainer != null && userInfo != null && userInfo.stargifts_count > 0;
        int changed = 0;
        if ((hasStories || hasBotPreviews) != scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
            changed++;
        }
        if (hasEditBotPreviews != scrollSlidingTextTabStrip.hasTab(TAB_BOT_PREVIEWS)) {
            changed++;
        }
        if (isSearchingStories() != scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
            changed++;
        }
        if (hasGifts != scrollSlidingTextTabStrip.hasTab(TAB_GIFTS)) {
            changed++;
        } else if (giftsContainer != null && hasGifts && giftsLastHash != giftsContainer.getLastEmojisHash()) {
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
            hasRecommendations = !channelRecommendationsAdapter.chats.isEmpty();
            if (hasRecommendations != scrollSlidingTextTabStrip.hasTab(TAB_RECOMMENDED_CHANNELS)) {
                changed++;
            }
            hasSavedDialogs = includeSavedDialogs() && !profileActivity.getMessagesController().getSavedMessagesController().unsupported && profileActivity.getMessagesController().getSavedMessagesController().hasDialogs();
            if (hasSavedDialogs != scrollSlidingTextTabStrip.hasTab(TAB_SAVED_DIALOGS)) {
                changed++;
            }
            if (hasSavedMessages != scrollSlidingTextTabStrip.hasTab(TAB_SAVED_MESSAGES)) {
                changed++;
            }
        }
        if (changed > 0) {
            if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final TransitionSet transitionSet = new TransitionSet();
                transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
//                transitionSet.addTransition(new ChangeBounds());
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
            if (isSearchingStories()) {
                if (!scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
                    scrollSlidingTextTabStrip.addTextTab(TAB_STORIES, getString(R.string.ProfileStories), idToView);
                }
                scrollSlidingTextTabStrip.animationDuration = 420;
            }
            if (hasEditBotPreviews) {
                if (!scrollSlidingTextTabStrip.hasTab(TAB_BOT_PREVIEWS)) {
                    scrollSlidingTextTabStrip.addTextTab(TAB_BOT_PREVIEWS, getString(R.string.ProfileBotPreviewTab), idToView);
                }
            }
            if (hasBotPreviews) {
                if (!scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
                    scrollSlidingTextTabStrip.addTextTab(TAB_STORIES, getString(R.string.ProfileBotPreviewTab), idToView);
                }
            } else if ((DialogObject.isUserDialog(dialog_id) || DialogObject.isChatDialog(dialog_id)) && !DialogObject.isEncryptedDialog(dialog_id) && (userInfo != null && userInfo.stories_pinned_available || info != null && info.stories_pinned_available || isStoriesView()) && includeStories()) {
                if (isArchivedOnlyStoriesView()) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_ARCHIVED_STORIES)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_ARCHIVED_STORIES, getString(R.string.ProfileArchivedStories), idToView);
                    }
                    scrollSlidingTextTabStrip.animationDuration = 420;
                } else {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_STORIES)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_STORIES, getString(R.string.ProfileStories), idToView);
                    }
                    if (isStoriesView()) {
                        if (!scrollSlidingTextTabStrip.hasTab(TAB_ARCHIVED_STORIES)) {
                            scrollSlidingTextTabStrip.addTextTab(TAB_ARCHIVED_STORIES, getString(R.string.ProfileArchivedStories), idToView);
                        }
                        scrollSlidingTextTabStrip.animationDuration = 420;
                    }
                }
            }
            if (hasGifts) {
                if (!scrollSlidingTextTabStrip.hasTab(TAB_GIFTS)) {
                    scrollSlidingTextTabStrip.addTextTab(TAB_GIFTS, TextUtils.concat(getString(R.string.ProfileGifts), giftsContainer.getLastEmojis(null)), idToView);
                    giftsLastHash = giftsContainer.getLastEmojisHash();
                }
            }
            if (!isStoriesView()) {
                if (hasSavedDialogs) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_SAVED_DIALOGS)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_SAVED_DIALOGS, getString(R.string.SavedDialogsTab), idToView);
                    }
                }
                if (chatUsersAdapter.chatInfo != null) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_GROUPUSERS)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_GROUPUSERS, getString("GroupMembers", R.string.GroupMembers), idToView);
                    }
                }
                if (hasMedia[0] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_PHOTOVIDEO)) {
                        if (hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0 && hasMedia[5] == 0 && hasMedia[6] == 0 && chatUsersAdapter.chatInfo == null) {
                            scrollSlidingTextTabStrip.addTextTab(TAB_PHOTOVIDEO, getString("SharedMediaTabFull2", R.string.SharedMediaTabFull2), idToView);
                        } else {
                            scrollSlidingTextTabStrip.addTextTab(TAB_PHOTOVIDEO, getString("SharedMediaTab2", R.string.SharedMediaTab2), idToView);
                        }
                    }
                }
                if (hasSavedMessages) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_SAVED_MESSAGES)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_SAVED_MESSAGES, getString(R.string.SavedMessagesTab2), idToView);
                    }
                    MessagesController.getGlobalMainSettings().edit().putInt("savedhint", 3).apply();
                }
                if (hasMedia[1] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_FILES)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_FILES, getString("SharedFilesTab2", R.string.SharedFilesTab2), idToView);
                    }
                }
                if (!DialogObject.isEncryptedDialog(dialog_id)) {
                    if (hasMedia[3] > 0) {
                        if (!scrollSlidingTextTabStrip.hasTab(TAB_LINKS)) {
                            scrollSlidingTextTabStrip.addTextTab(TAB_LINKS, getString("SharedLinksTab2", R.string.SharedLinksTab2), idToView);
                        }
                    }
                    if (hasMedia[4] > 0) {
                        if (!scrollSlidingTextTabStrip.hasTab(TAB_AUDIO)) {
                            scrollSlidingTextTabStrip.addTextTab(TAB_AUDIO, getString("SharedMusicTab2", R.string.SharedMusicTab2), idToView);
                        }
                    }
                } else {
                    if (hasMedia[4] > 0) {
                        if (!scrollSlidingTextTabStrip.hasTab(TAB_AUDIO)) {
                            scrollSlidingTextTabStrip.addTextTab(TAB_AUDIO, getString("SharedMusicTab2", R.string.SharedMusicTab2), idToView);
                        }
                    }
                }
                if (hasMedia[2] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_VOICE)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_VOICE, getString("SharedVoiceTab2", R.string.SharedVoiceTab2), idToView);
                    }
                }
                if (hasMedia[5] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_GIF)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_GIF, getString("SharedGIFsTab2", R.string.SharedGIFsTab2), idToView);
                    }
                }
                if (hasMedia[6] > 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_COMMON_GROUPS)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_COMMON_GROUPS, getString("SharedGroupsTab2", R.string.SharedGroupsTab2), idToView);
                    }
                }
                if (hasRecommendations) {
                    if (!scrollSlidingTextTabStrip.hasTab(TAB_RECOMMENDED_CHANNELS)) {
                        scrollSlidingTextTabStrip.addTextTab(TAB_RECOMMENDED_CHANNELS, getString(R.string.SimilarChannelsTab), idToView);
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
        if (currentAdapter == storiesAdapter) {
            storiesReorder.attachToRecyclerView(null);
        }
        RecyclerView.RecycledViewPool viewPool = null;
        if (searching && searchWas) {
            if (mediaPages[a].searchViewPool == null) {
                mediaPages[a].searchViewPool = new RecyclerView.RecycledViewPool();
            }
            viewPool = mediaPages[a].searchViewPool;
            if (animated) {
                if (mediaPages[a].selectedType == TAB_PHOTOVIDEO || mediaPages[a].selectedType == TAB_VOICE || mediaPages[a].selectedType == TAB_GIF || mediaPages[a].selectedType == TAB_COMMON_GROUPS || mediaPages[a].selectedType == TAB_GROUPUSERS && !delegate.canSearchMembers()) {
                    searching = false;
                    if (searchTagsList != null) {
                        searchTagsList.show(false);
                    }
                    searchWas = false;
                    switchToCurrentSelectedMode(true);
                    return;
                } else {
                    String text = searchItem != null ? searchItem.getSearchField().getText().toString() : "";
                    if (mediaPages[a].selectedType == TAB_FILES) {
                        if (documentsSearchAdapter != null) {
                            documentsSearchAdapter.search(text, false);
                            if (currentAdapter != documentsSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == TAB_LINKS) {
                        if (linksSearchAdapter != null) {
                            linksSearchAdapter.search(text, false);
                            if (currentAdapter != linksSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(linksSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == TAB_AUDIO) {
                        if (audioSearchAdapter != null) {
                            audioSearchAdapter.search(text, false);
                            if (currentAdapter != audioSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(audioSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == TAB_GROUPUSERS) {
                        if (groupUsersSearchAdapter != null) {
                            groupUsersSearchAdapter.search(text, false);
                            if (currentAdapter != groupUsersSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(groupUsersSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {
                        if (savedMessagesSearchAdapter != null) {
                            savedMessagesSearchAdapter.search(text, searchingReaction);
                            if (currentAdapter != savedMessagesSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(savedMessagesSearchAdapter);
                            }
                        }
                    }
                }
            } else {
                if (mediaPages[a].listView != null) {
                    if (mediaPages[a].selectedType == TAB_FILES) {
                        if (currentAdapter != documentsSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                        }
                        documentsSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == TAB_LINKS) {
                        if (currentAdapter != linksSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(linksSearchAdapter);
                        }
                        linksSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == TAB_AUDIO) {
                        if (currentAdapter != audioSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(audioSearchAdapter);
                        }
                        audioSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == TAB_GROUPUSERS) {
                        if (currentAdapter != groupUsersSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(groupUsersSearchAdapter);
                        }
                        groupUsersSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {
                        if (currentAdapter != savedMessagesSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(savedMessagesSearchAdapter);
                        }
                        savedMessagesSearchAdapter.notifyDataSetChanged();
                    }
                }
            }
        } else {
            if (mediaPages[a].viewPool == null) {
                mediaPages[a].viewPool = new RecyclerView.RecycledViewPool();
            }
            viewPool = mediaPages[a].viewPool;
            mediaPages[a].listView.setPinnedHeaderShadowDrawable(null);
            mediaPages[a].listView.setPadding(
                mediaPages[a].listView.getPaddingLeft(),
                (mediaPages[a].listView.hintPaddingTop = mediaPages[a].selectedType == TAB_ARCHIVED_STORIES ? dp(64) : 0),
                mediaPages[a].listView.getPaddingRight(),
                (mediaPages[a].listView.hintPaddingTop = isStoriesView() ? dp(72) : 0)
            );
            mediaPages[a].buttonView.setVisibility(mediaPages[a].selectedType == TAB_STORIES && isStoriesView() ? View.VISIBLE : View.GONE);

            if (mediaPages[a].selectedType == TAB_PHOTOVIDEO) {
                if (currentAdapter != photoVideoAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(photoVideoAdapter);
                }
                layoutParams.leftMargin = layoutParams.rightMargin = -dp(1);
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
                storiesReorder.attachToRecyclerView(mediaPages[a].listView);
                spanCount = mediaColumnsCount[1];
            } else if (mediaPages[a].selectedType == TAB_ARCHIVED_STORIES) {
                if (currentAdapter != archivedStoriesAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(archivedStoriesAdapter);
                }
                spanCount = mediaColumnsCount[1];
            } else if (mediaPages[a].selectedType == TAB_RECOMMENDED_CHANNELS) {
                if (currentAdapter != channelRecommendationsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(channelRecommendationsAdapter);
                }
            } else if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {
                if (currentAdapter != savedDialogsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(savedDialogsAdapter);
                    savedDialogsAdapter.itemTouchHelper.attachToRecyclerView(savedDialogsAdapter.attachedToRecyclerView = mediaPages[a].listView);
                }
                viewPool = savedDialogsAdapter.viewPool;
            } else if (mediaPages[a].selectedType == TAB_SAVED_MESSAGES) {
                if (currentAdapter != null) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(null);
                }
                if (savedMessagesContainer.getParent() != mediaPages[a]) {
                    AndroidUtilities.removeFromParent(savedMessagesContainer);
                    mediaPages[a].addView(savedMessagesContainer);
                }
            } else if (mediaPages[a].selectedType == TAB_BOT_PREVIEWS) {
                if (currentAdapter != null) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(null);
                }
                if (botPreviewsContainer != null && botPreviewsContainer.getParent() != mediaPages[a]) {
                    AndroidUtilities.removeFromParent(botPreviewsContainer);
                    mediaPages[a].addView(botPreviewsContainer);
                }
            } else if (mediaPages[a].selectedType == TAB_GIFTS) {
                if (currentAdapter != null) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(null);
                }
                if (giftsContainer != null && giftsContainer.getParent() != mediaPages[a]) {
                    AndroidUtilities.removeFromParent(giftsContainer);
                    mediaPages[a].addView(giftsContainer);
                }
            }
            if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {
                mediaPages[a].listView.setItemAnimator(mediaPages[a].itemAnimator);
            } else {
                mediaPages[a].listView.setItemAnimator(null);
                if (savedDialogsAdapter != null && mediaPages[a].listView == savedDialogsAdapter.attachedToRecyclerView) {
                    savedDialogsAdapter.itemTouchHelper.attachToRecyclerView(savedDialogsAdapter.attachedToRecyclerView = null);
                }
            }
            if (savedMessagesContainer != null && mediaPages[a].selectedType != TAB_SAVED_MESSAGES && savedMessagesContainer.getParent() == mediaPages[a]) {
                savedMessagesContainer.chatActivity.onRemoveFromParent();
                mediaPages[a].removeView(savedMessagesContainer);
            }
            if (botPreviewsContainer != null && mediaPages[a].selectedType != TAB_BOT_PREVIEWS && botPreviewsContainer.getParent() == mediaPages[a]) {
                mediaPages[a].removeView(botPreviewsContainer);
            }
            if (giftsContainer != null && mediaPages[a].selectedType != TAB_GIFTS && giftsContainer.getParent() == mediaPages[a]) {
                mediaPages[a].removeView(giftsContainer);
            }
            if (mediaPages[a].selectedType == TAB_PHOTOVIDEO || mediaPages[a].selectedType == TAB_SAVED_DIALOGS || mediaPages[a].selectedType == TAB_STORIES || mediaPages[a].selectedType == TAB_ARCHIVED_STORIES || mediaPages[a].selectedType == TAB_VOICE || mediaPages[a].selectedType == TAB_GIF || mediaPages[a].selectedType == TAB_COMMON_GROUPS || mediaPages[a].selectedType == TAB_GROUPUSERS && !delegate.canSearchMembers() || mediaPages[a].selectedType == TAB_RECOMMENDED_CHANNELS || mediaPages[a].selectedType == TAB_BOT_PREVIEWS || mediaPages[a].selectedType == TAB_GIFTS) {
                if (animated) {
                    searchItemState = 2;
                } else {
                    searchItemState = 0;
                    if (searchItem != null) {
                        searchItem.setVisibility(isStoriesView() || searching ? View.GONE : View.INVISIBLE);
                    }
                }
            } else {
                if (animated) {
                    if (searchItem != null && searchItem.getVisibility() == View.INVISIBLE && !actionBar.isSearchFieldVisible()) {
                        if (canShowSearchItem()) {
                            searchItemState = 1;
                            searchItem.setVisibility(View.VISIBLE);
                        } else {
                            searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                        }
                        searchAlpha = getSearchAlpha(a);
                        updateSearchItemIcon(1f - a);
                    } else {
                        searchItemState = 0;
                        searchAlpha = 1f;
                    }
                } else if (searchItem != null && searchItem.getVisibility() == View.INVISIBLE) {
                    if (canShowSearchItem()) {
                        searchItemState = 0;
                        searchAlpha = 1;
                        searchItem.setVisibility(View.VISIBLE);
                    } else {
                        searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
                        searchAlpha = 0;
                    }
                }
                updateOptionsSearch();
            }
            if (mediaPages[a].selectedType == TAB_COMMON_GROUPS) {
                if (!commonGroupsAdapter.loading && !commonGroupsAdapter.endReached && commonGroupsAdapter.chats.isEmpty()) {
                    commonGroupsAdapter.getChats(0, 100);
                }
            } else if (mediaPages[a].selectedType == TAB_GROUPUSERS) {

            } else if (mediaPages[a].selectedType == TAB_STORIES) {
                StoriesController.StoriesList storiesList = storiesAdapter.storiesList;
                storiesAdapter.load(false);
                mediaPages[a].emptyView.showProgress(storiesList != null && (storiesList.isLoading() || hasInternet() && storiesList.getCount() > 0), animated);
                fastScrollVisible = storiesList != null && storiesList.getCount() > 0 && !isSearchingStories();
            } else if (mediaPages[a].selectedType == TAB_ARCHIVED_STORIES) {
                StoriesController.StoriesList storiesList = archivedStoriesAdapter.storiesList;
                archivedStoriesAdapter.load(false);
                mediaPages[a].emptyView.showProgress(storiesList != null && (storiesList.isLoading() || hasInternet() && storiesList.getCount() > 0), animated);
                fastScrollVisible = storiesList != null && storiesList.getCount() > 0 && !isSearchingStories();
            } else if (mediaPages[a].selectedType == TAB_RECOMMENDED_CHANNELS) {

            } else if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {

            } else if (mediaPages[a].selectedType == TAB_SAVED_MESSAGES) {

            } else if (mediaPages[a].selectedType == TAB_BOT_PREVIEWS) {

            } else if (mediaPages[a].selectedType == TAB_GIFTS) {

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
                    profileActivity.getMediaDataController().loadMedia(dialog_id, 50, 0, 0, type, topicId, 1, profileActivity.getClassGuid(), sharedMediaData[mediaPages[a].selectedType].requestIndex, null, null);
                }
            }
            if (mediaPages[a].selectedType == TAB_STORIES) {
                mediaPages[a].emptyView.stickerView.setVisibility(!isSelf() && !isBot() ? View.VISIBLE : View.GONE);
                if (isSelf()) {
                    mediaPages[a].emptyView.button.setVisibility(View.GONE);
                } else {
                    mediaPages[a].emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_ALBUM);
                    mediaPages[a].emptyView.button.setVisibility(!isSearchingStories() ? View.VISIBLE : View.GONE);
                    mediaPages[a].emptyView.button.setText(addPostText(), false);
                }
                mediaPages[a].emptyView.title.setText(!isSearchingStories() ? isStoriesView() ? getString(R.string.NoPublicStoriesTitle2) : getString(R.string.NoStoriesTitle) : getString(R.string.NoHashtagStoriesTitle));
                mediaPages[a].emptyView.subtitle.setText(isStoriesView() ? getString(R.string.NoStoriesSubtitle2) : "");
                mediaPages[a].emptyView.button.setOnClickListener(v -> {
                    profileActivity.getMessagesController().getMainSettings().edit().putBoolean("story_keep", true).apply();
                    StoryRecorder.getInstance(profileActivity.getParentActivity(), profileActivity.getCurrentAccount()).open(null);
                });
            } else if (mediaPages[a].selectedType == TAB_ARCHIVED_STORIES) {
                if (isSelf()) {
                    mediaPages[a].emptyView.stickerView.setVisibility(View.GONE);
                    mediaPages[a].emptyView.button.setVisibility(View.GONE);
                } else {
                    mediaPages[a].emptyView.stickerView.setVisibility(View.VISIBLE);
                    mediaPages[a].emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_ALBUM);
                    mediaPages[a].emptyView.button.setVisibility(View.VISIBLE);
                    mediaPages[a].emptyView.button.setText(addPostText(), false);
                }
                mediaPages[a].emptyView.title.setText(getString(R.string.NoArchivedStoriesTitle));
                mediaPages[a].emptyView.subtitle.setText(isStoriesView() ? getString(R.string.NoArchivedStoriesSubtitle) : "");
                mediaPages[a].emptyView.button.setOnClickListener(v -> {
                    profileActivity.getMessagesController().getMainSettings().edit().putBoolean("story_keep", true).apply();
                    StoryRecorder.getInstance(profileActivity.getParentActivity(), profileActivity.getCurrentAccount()).open(null);
                });
            } else {
                mediaPages[a].emptyView.stickerView.setVisibility(View.VISIBLE);
                mediaPages[a].emptyView.setStickerType(StickerEmptyView.STICKER_TYPE_SEARCH);
                mediaPages[a].emptyView.title.setText(getString(R.string.NoResult));
                mediaPages[a].emptyView.subtitle.setText(getString(R.string.SearchEmptyViewFilteredSubtitle2));
                mediaPages[a].emptyView.button.setVisibility(View.GONE);
            }
            mediaPages[a].listView.setVisibility(View.VISIBLE);
        }
        mediaPages[a].fastScrollEnabled = fastScrollVisible;
        updateFastScrollVisibility(mediaPages[a], false);
        mediaPages[a].layoutManager.setSpanCount(spanCount);
        mediaPages[a].listView.invalidateItemDecorations();
        if (viewPool != null) {
            mediaPages[a].listView.setRecycledViewPool(viewPool);
            mediaPages[a].animationSupportingListView.setRecycledViewPool(viewPool);
        }

        if (searchItemState == 2 && actionBar.isSearchFieldVisible()) {
            ignoreSearchCollapse = true;
            actionBar.closeSearchField();
            searchItemState = 0;
            searchAlpha = 0;
            if (searchItem != null) {
                searchItem.setVisibility(isStoriesView() ? View.GONE : View.INVISIBLE);
            }
            updateOptionsSearch();
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
            gotoItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS && getClosestTab() != TAB_GIFTS ? View.VISIBLE : View.GONE);
        }
        if (forwardItem != null) {
            forwardItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS && getClosestTab() != TAB_GIFTS ? View.VISIBLE : View.GONE);
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
            if (selectedMode == TAB_STORIES && !canEditStories()) {
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
                    gotoItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS && getClosestTab() != TAB_GIFTS && selectedFiles[0].size() == 1 ? View.VISIBLE : View.GONE);
                }
                if (forwardItem != null) {
                    forwardItem.setVisibility(getClosestTab() != TAB_STORIES && getClosestTab() != TAB_BOT_PREVIEWS && getClosestTab() != TAB_GIFTS ? View.VISIBLE : View.GONE);
                }
                updateStoriesPinButton();
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
            if (selectedMode == TAB_PHOTOVIDEO) {
                int i = index - sharedMediaData[selectedMode].startOffset;
                if (i >= 0 && i < sharedMediaData[selectedMode].messages.size()) {
                    PhotoViewer.getInstance().setParentActivity(profileActivity);
                    PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, i, dialog_id, mergeDialogId, topicId, provider);
                }
            } else if (selectedMode == TAB_VOICE || selectedMode == TAB_AUDIO) {
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
            } else if (selectedMode == TAB_FILES) {
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
            } else if (selectedMode == TAB_LINKS) {
                try {
                    TLRPC.WebPage webPage = MessageObject.getMedia(message.messageOwner) != null ? MessageObject.getMedia(message.messageOwner).webpage : null;
                    String link = null;
                    if (webPage != null && !(webPage instanceof TLRPC.TL_webPageEmpty)) {
                        if (webPage.cached_page != null) {
                            if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab(message) != null) {
                                return;
                            }
                            profileActivity.createArticleViewer(false).open(message);
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
                }).addBottomClip(profileActivity instanceof ProfileActivity && ((ProfileActivity) profileActivity).myProfile ? dp(68) : 0));
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
                builder.setItems(new CharSequence[]{getString("Open", R.string.Open), getString("Copy", R.string.Copy)}, (dialog, which) -> {
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
                case VIEW_TYPE_LINK_DATE:
                    view = new GraySectionCell(mContext, resourcesProvider);
                    break;
                case VIEW_TYPE_LINK:
                    view = new SharedLinkCell(mContext, SharedLinkCell.VIEW_TYPE_DEFAULT, resourcesProvider);
                    ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
                    break;
                case VIEW_TYPE_LINK_EMPTY:
                    View emptyStubView = createEmptyStubView(mContext, 3, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case VIEW_TYPE_LINK_LOADING:
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
            if (holder.getItemViewType() != VIEW_TYPE_LINK_LOADING && holder.getItemViewType() != VIEW_TYPE_LINK_EMPTY) {
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case VIEW_TYPE_LINK_DATE: {
                        MessageObject messageObject = messageObjects.get(0);
                        if (holder.itemView instanceof GraySectionCell) {
                            ((GraySectionCell) holder.itemView).setText(LocaleController.formatSectionDate(messageObject.messageOwner.date));
                        }
                        break;
                    }
                    case VIEW_TYPE_LINK: {
                        if (section != 0) {
                            position--;
                        }
                        if (!(holder.itemView instanceof SharedLinkCell) || position < 0 || position >= messageObjects.size()) {
                            return;
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
                return VIEW_TYPE_LINK_EMPTY;
            }
            if (section < sharedMediaData[3].sections.size()) {
                if (section != 0 && position == 0) {
                    return VIEW_TYPE_LINK_DATE;
                } else {
                    return VIEW_TYPE_LINK;
                }
            }
            return VIEW_TYPE_LINK_LOADING;
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
                case VIEW_TYPE_DOCUMENT:
                    SharedDocumentCell cell = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_DEFAULT, resourcesProvider);
                    cell.setGlobalGradientView(globalGradientView);
                    view = cell;
                    break;
                case VIEW_TYPE_DOCUMENT_LOADING:
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
                case VIEW_TYPE_DOCUMENT_EMPTY:
                    View emptyStubView = createEmptyStubView(mContext, currentType, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case VIEW_TYPE_AUDIO:
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
                case VIEW_TYPE_DOCUMENT: {
                    if (!(holder.itemView instanceof SharedDocumentCell)) return;
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
                case VIEW_TYPE_AUDIO: {
                    if (!(holder.itemView instanceof SharedAudioCell)) return;
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
                return VIEW_TYPE_DOCUMENT_EMPTY;
            }
            if (position >= sharedMediaData[currentType].startOffset && position < sharedMediaData[currentType].startOffset + sharedMediaData[currentType].messages.size()) {
                if (currentType == 2 || currentType == 4) {
                    return VIEW_TYPE_AUDIO;
                } else {
                    return VIEW_TYPE_DOCUMENT;
                }
            }
            return VIEW_TYPE_DOCUMENT_LOADING;
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
                emptyStubView.emptyTextView.setText(getString(R.string.NoMediaSecret));
            } else {
                emptyStubView.emptyTextView.setText(getString(R.string.NoMedia));
            }
        } else if (currentType == 1) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedFilesSecret));
            } else {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedFiles));
            }
        } else if (currentType == 2) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedVoiceSecret));
            } else {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedVoice));
            }
        } else if (currentType == 3) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedLinksSecret));
            } else {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedLinks));
            }
        } else if (currentType == 4) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedAudioSecret));
            } else {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedAudio));
            }
        } else if (currentType == 5) {
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                emptyStubView.emptyTextView.setText(getString(R.string.NoSharedGifSecret));
            } else {
                emptyStubView.emptyTextView.setText(getString(R.string.NoGIFs));
            }
        } else if (currentType == 6) {
            emptyStubView.emptyImageView.setImageDrawable(null);
            emptyStubView.emptyTextView.setText(getString(R.string.NoGroupsInCommon));
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
            emptyTextView.setPadding(dp(40), 0, dp(40), dp(128));
            addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();
            ignoreRequestLayout = true;
            if (AndroidUtilities.isTablet()) {
                emptyTextView.setPadding(dp(40), 0, dp(40), dp(128));
            } else {
                if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                    emptyTextView.setPadding(dp(40), 0, dp(40), 0);
                } else {
                    emptyTextView.setPadding(dp(40), 0, dp(40), dp(128));
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

    public static final int VIEW_TYPE_PHOTOVIDEO = 0;
    public static final int VIEW_TYPE_PHOTOVIDEO_LOADING = 2;
    public static final int VIEW_TYPE_LINK_DATE = 3;
    public static final int VIEW_TYPE_LINK = 4;
    public static final int VIEW_TYPE_LINK_EMPTY = 5;
    public static final int VIEW_TYPE_LINK_LOADING = 6;
    public static final int VIEW_TYPE_DOCUMENT = 7;
    public static final int VIEW_TYPE_DOCUMENT_LOADING = 8;
    public static final int VIEW_TYPE_DOCUMENT_EMPTY = 9;
    public static final int VIEW_TYPE_AUDIO = 10;
    public static final int VIEW_TYPE_GIF_LOADING = 11;
    public static final int VIEW_TYPE_GIF = 12;
    public static final int VIEW_TYPE_SAVED_DIALOG = 13;
    public static final int VIEW_TYPE_GROUP = 14;
    public static final int VIEW_TYPE_GROUP_EMPTY = 15;
    public static final int VIEW_TYPE_GROUP_LOADING = 16;
    public static final int VIEW_TYPE_SIMILAR_CHANNEL = 17;
    public static final int VIEW_TYPE_SIMILAR_CHANNEL_BLOCK = 18;
    public static final int VIEW_TYPE_STORY = 19;
    public static final int VIEW_TYPE_GROUPUSER_EMPTY = 20;
    public static final int VIEW_TYPE_GROUPUSER = 21;
    public static final int VIEW_TYPE_SEARCH_GROUPUSER = 22;
    public static final int VIEW_TYPE_SEARCH_SAVED_DIALOG = 23;
    public static final int VIEW_TYPE_SEARCH_DOCUMENT = 24;

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
                case VIEW_TYPE_PHOTOVIDEO:
                case VIEW_TYPE_STORY:
                    if (sharedResources == null) {
                        sharedResources = new SharedPhotoVideoCell2.SharedResources(parent.getContext(), resourcesProvider);
                    }
                    SharedPhotoVideoCell2 cell = new SharedPhotoVideoCell2(mContext, sharedResources, profileActivity.getCurrentAccount());
                    if (viewType == VIEW_TYPE_STORY) {
                        cell.setCheck2();
                    }
                    cell.setGradientView(globalGradientView);
                    if (this == storiesAdapter || this == archivedStoriesAdapter) {
                        cell.isStory = true;
                    }
                    view = cell;
                    break;
                default:
                case VIEW_TYPE_PHOTOVIDEO_LOADING:
                    View emptyStubView = createEmptyStubView(mContext, 0, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_TYPE_PHOTOVIDEO) {
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].getMessages();
                int index = position - sharedMediaData[0].getStartOffset();
                if (!(holder.itemView instanceof SharedPhotoVideoCell2)) return;
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
                return VIEW_TYPE_PHOTOVIDEO_LOADING;
            }
            int count = sharedMediaData[0].getStartOffset() + sharedMediaData[0].getMessages().size();
            if (position - sharedMediaData[0].getStartOffset() >= 0 && position < count) {
                return VIEW_TYPE_PHOTOVIDEO;
            }
            return VIEW_TYPE_PHOTOVIDEO;
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
            if (isSearchingStories()) {
                return false;
            }
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

        public void queryServerSearch(final String query, final int max_id, long did, long topicId) {
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
            if (topicId != 0) {
                if (did == profileActivity.getUserConfig().getClientUserId()) {
                    req.flags |= 4;
                    req.saved_peer_id = profileActivity.getMessagesController().getInputPeer(topicId);
                } else {
                    req.flags |= 2;
                    req.top_msg_id = (int) topicId;
                }
            }
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
                                        mediaPages[a].emptyView.button.setVisibility(View.GONE);
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
                        queryServerSearch(query, messageObject.getId(), messageObject.getDialogId(), dialog_id == profileActivity.getUserConfig().getClientUserId() ? messageObject.getSavedDialogId() : 0);
                    } else if (currentType == 3) {
                        queryServerSearch(query, 0, dialog_id, topicId);
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
                            mediaPages[a].emptyView.title.setText(getString("NoResult", R.string.NoResult));
                            mediaPages[a].emptyView.button.setVisibility(View.GONE);
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
            return 0 != searchResult.size() + globalSearch.size();
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
                if (!(holder.itemView instanceof SharedDocumentCell)) return;
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedDocumentCell.setDocument(messageObject, position != getItemCount() - 1);
                if (isActionModeShowed) {
                    sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 3) {
                if (!(holder.itemView instanceof SharedLinkCell)) return;
                SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedLinkCell.setLink(messageObject, position != getItemCount() - 1);
                if (isActionModeShowed) {
                    sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedLinkCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 4) {
                if (!(holder.itemView instanceof SharedAudioCell)) return;
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
            return VIEW_TYPE_SEARCH_DOCUMENT;
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
                return VIEW_TYPE_GIF_LOADING;
            }
            return VIEW_TYPE_GIF;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_GIF_LOADING) {
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
            if (holder.getItemViewType() == VIEW_TYPE_GIF) {
                MessageObject messageObject = sharedMediaData[5].messages.get(position);
                TLRPC.Document document = messageObject.getDocument();
                if (document != null) {
                    if (!(holder.itemView instanceof ContextLinkCell)) return;
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

    private class SavedDialogsAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;
        private final SavedMessagesController controller;

        private final ArrayList<SavedMessagesController.SavedDialog> oldDialogs = new ArrayList<>();
        private final ArrayList<SavedMessagesController.SavedDialog> dialogs = new ArrayList<>();
        private boolean orderChanged;
        private Runnable notifyOrderUpdate = () -> {
            if (!orderChanged) {
                return;
            }
            orderChanged = false;
            ArrayList<Long> pinnedOrder = new ArrayList<>();
            for (int i = 0; i < dialogs.size(); ++i) {
                if (dialogs.get(i).pinned) {
                    pinnedOrder.add(dialogs.get(i).dialogId);
                }
            }
            profileActivity.getMessagesController().getSavedMessagesController().updatePinnedOrder(pinnedOrder);
        };

        public final RecyclerView.RecycledViewPool viewPool = new RecyclerView.RecycledViewPool();
        public RecyclerListView attachedToRecyclerView;
        public final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (!isActionModeShowed || recyclerView.getAdapter() == savedMessagesSearchAdapter) {
                    return makeMovementFlags(0, 0);
                }
                SavedMessagesController.SavedDialog d = getDialog(viewHolder);
                if (d != null && d.pinned) {
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                }
                return makeMovementFlags(0, 0);
            }

            private SavedMessagesController.SavedDialog getDialog(RecyclerView.ViewHolder holder) {
                if (holder == null) {
                    return null;
                }
                int position = holder.getAdapterPosition();
                if (position < 0 || position >= dialogs.size()) {
                    return null;
                }
                return dialogs.get(position);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (viewHolder != null && attachedToRecyclerView != null) {
                    attachedToRecyclerView.hideSelector(false);
                }
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    AndroidUtilities.cancelRunOnUIThread(notifyOrderUpdate);
                    AndroidUtilities.runOnUIThread(notifyOrderUpdate, 300);
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder sourceHolder, @NonNull RecyclerView.ViewHolder targetHolder) {
                if (!isActionModeShowed || recyclerView.getAdapter() == savedMessagesSearchAdapter) {
                    return false;
                }
                SavedMessagesController.SavedDialog source = getDialog(sourceHolder);
                SavedMessagesController.SavedDialog target = getDialog(targetHolder);
                if (source != null && target != null && source.pinned && target.pinned) {
                    int fromPosition = sourceHolder.getAdapterPosition();
                    int toPosition = targetHolder.getAdapterPosition();
                    dialogs.remove(fromPosition);
                    dialogs.add(toPosition, source);
                    notifyItemMoved(fromPosition, toPosition);
                    orderChanged = true;
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setPressed(false);
            }
        });

        public SavedDialogsAdapter(Context context) {
            mContext = context;
            controller = profileActivity.getMessagesController().getSavedMessagesController();
            if (includeSavedDialogs()) {
                controller.loadDialogs(false);
            }
            setHasStableIds(true);
            update(false);
        }

        @Override
        public long getItemId(int position) {
            if (position < 0 || position >= dialogs.size()) return position;
            return dialogs.get(position).dialogId;
        }

        public void update(boolean notify) {
            oldDialogs.clear();
            oldDialogs.addAll(dialogs);
            dialogs.clear();
            dialogs.addAll(controller.allDialogs);
            if (notify) {
                notifyDataSetChanged();
            }
        }

        public final HashSet<Long> selectedDialogs = new HashSet<>();

        public void select(View view) {
            if (!(view instanceof DialogCell)) return;
            DialogCell dialogCell = (DialogCell) view;
            long dialogId = dialogCell.getDialogId();
            SavedMessagesController.SavedDialog dialog = null;
            for (int i = 0; i < dialogs.size(); ++i) {
                if (dialogs.get(i).dialogId == dialogId) {
                    dialog = dialogs.get(i);
                    break;
                }
            }
            if (dialog == null) return;

            if (selectedDialogs.contains(dialog.dialogId)) {
                selectedDialogs.remove(dialog.dialogId);
                if (selectedDialogs.size() <= 0 && isActionModeShowed) {
                    showActionMode(false);
                }
            } else {
                selectedDialogs.add(dialog.dialogId);
                if (selectedDialogs.size() > 0 && !isActionModeShowed) {
                    showActionMode(true);
                    if (gotoItem != null) {
                        gotoItem.setVisibility(View.GONE);
                    }
                    if (forwardItem != null) {
                        forwardItem.setVisibility(View.GONE);
                    }
                }
            }
            selectedMessagesCountTextView.setNumber(selectedDialogs.size(), true);
            boolean allSelectedPinned = selectedDialogs.size() > 0;
            for (long did : selectedDialogs) {
                for (int i = 0; i < dialogs.size(); ++i) {
                    SavedMessagesController.SavedDialog d = dialogs.get(i);
                    if (d.dialogId == did) {
                        if (!d.pinned) {
                            allSelectedPinned = false;
                        }
                        break;
                    }
                }
                if (!allSelectedPinned) break;
            }
            if (pinItem != null) {
                pinItem.setVisibility(allSelectedPinned ? View.GONE : View.VISIBLE);
            }
            if (unpinItem != null) {
                unpinItem.setVisibility(allSelectedPinned ? View.VISIBLE : View.GONE);
            }
            if (view instanceof DialogCell) {
                ((DialogCell) view).setChecked(selectedDialogs.contains(dialog.dialogId), true);
            }
        }

        public void unselectAll() {
            selectedDialogs.clear();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(null, mContext, false, true) {
                @Override
                public boolean isForumCell() {
                    return false;
                }
                @Override
                public boolean getIsPinned() {
                    if (attachedToRecyclerView != null && attachedToRecyclerView.getAdapter() == SavedDialogsAdapter.this) {
                        int position = attachedToRecyclerView.getChildAdapterPosition(this);
                        if (position >= 0 && position < dialogs.size()) {
                            return dialogs.get(position).pinned;
                        }
                    }
                    return false;
                }
            };
            cell.setDialogCellDelegate(SharedMediaLayout.this);
            cell.isSavedDialog = true;
            cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (!(holder.itemView instanceof DialogCell)) return;
            DialogCell cell = (DialogCell) holder.itemView;
            SavedMessagesController.SavedDialog d = dialogs.get(position);
            cell.setDialog(d.dialogId, d.message, d.getDate(), false, false);
            cell.isSavedDialogCell = true;
            cell.setChecked(selectedDialogs.contains(d.dialogId), false);
            cell.useSeparator = position + 1 < getItemCount();
        }

        @Override
        public int getItemViewType(int position) {
            return VIEW_TYPE_SAVED_DIALOG;
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private class SavedMessagesSearchAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;
        private final int currentAccount;
        public final ArrayList<SavedMessagesController.SavedDialog> dialogs = new ArrayList<>();
        public final ArrayList<MessageObject> messages = new ArrayList<>();
        public final ArrayList<MessageObject> loadedMessages = new ArrayList<>();
        public final ArrayList<MessageObject> cachedMessages = new ArrayList<>();
        public SavedMessagesSearchAdapter(Context context) {
            mContext = context;
            currentAccount = profileActivity.getCurrentAccount();
            setHasStableIds(true);
        }

        private boolean loading;
        private boolean endReached = false;
        private int oldItemCounts = 0;
        private int count = 0;

        private String lastQuery;
        private ReactionsLayoutInBubble.VisibleReaction lastReaction;
        private int reqId = -1;

        public void search(String query, ReactionsLayoutInBubble.VisibleReaction reaction) {
            if (TextUtils.equals(query, lastQuery) && (lastReaction == null && reaction == null || lastReaction != null && lastReaction.equals(reaction))) {
                return;
            }
            lastQuery = query;
            lastReaction = reaction;
            if (reqId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = -1;
            }

            cachedMessages.clear();
            loadedMessages.clear();
            messages.clear();
            count = 0;
            endReached = false;
            loading = true;

            dialogs.clear();
            if (lastReaction == null) {
                dialogs.addAll(MessagesController.getInstance(currentAccount).getSavedMessagesController().searchDialogs(query));
            }
            for (int a = 0; a < mediaPages.length; a++) {
                if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {
                    mediaPages[a].emptyView.showProgress(true, true);
                }
            }
            if (lastReaction == null) {
                notifyDataSetChanged();
            }

            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            AndroidUtilities.runOnUIThread(searchRunnable, lastReaction != null ? 60 : 600);
        }

        public void loadMore() {
            if (endReached || loading) return;
            loading = true;
            sendRequest();
        }

        int lastSearchId;
        private Runnable searchRunnable = this::sendRequest;
        private void sendRequest() {
            if (TextUtils.isEmpty(lastQuery) && lastReaction == null) {
                loading = false;
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(UserConfig.getInstance(currentAccount).getClientUserId());
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.q = lastQuery;
            if (lastReaction != null) {
                req.flags |= 8;
                req.saved_reaction.add(lastReaction.toTLReaction());
            }
            if (loadedMessages.size() > 0) {
                MessageObject lastMessage = loadedMessages.get(loadedMessages.size() - 1);
                req.offset_id = lastMessage.getId();
            }
            req.limit = 10;
            endReached = false;
            final int searchId = ++lastSearchId;
            Runnable request = () -> {
                if (searchId != lastSearchId) return;
                reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!(res instanceof TLRPC.messages_Messages) || searchId != lastSearchId) {
                        return;
                    }
                    TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(r.users, r.chats, true, true);
                    for (int i = 0; i < r.messages.size(); ++i) {
                        TLRPC.Message msg = r.messages.get(i);
                        MessageObject messageObject = new MessageObject(currentAccount, msg, false, true);
                        if (messageObject.hasValidGroupId()) {
                            messageObject.isPrimaryGroupMessage = true;
                        }
                        messageObject.setQuery(lastQuery);
                        loadedMessages.add(messageObject);
                    }
                    count = r.count;
                    if (r instanceof TLRPC.TL_messages_messagesSlice) {
                        endReached = loadedMessages.size() >= r.count;
                    } else if (r instanceof TLRPC.TL_messages_messages) {
                        endReached = true;
                    }
                    updateMessages(false);
                    loading = false;
                    reqId = -1;
                }));
            };
            if (lastReaction != null) {
                MessagesStorage.getInstance(currentAccount).searchSavedByTag(lastReaction.toTLReaction(), 0, lastQuery, 100, cachedMessages.size(), (messages, users, chats, emoji) -> {
                    MessagesController.getInstance(currentAccount).putUsers(users, true);
                    MessagesController.getInstance(currentAccount).putChats(chats, true);
                    AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).processDocuments(emoji);
                    for (int i = 0; i < messages.size(); ++i) {
                        MessageObject messageObject = messages.get(i);
                        if (messageObject.hasValidGroupId() && messageObject.messageOwner.reactions != null) {
                            messageObject.isPrimaryGroupMessage = true;
                        }
                        messageObject.setQuery(lastQuery);
                        cachedMessages.add(messageObject);
                    }
                    updateMessages(true);
                    AndroidUtilities.runOnUIThread(request, 540);
                }, false);
            } else {
                request.run();
            }
        }

        private void updateMessages(boolean fromCache) {
            messages.clear();
            HashSet<Integer> msgIds = new HashSet<>();
            for (int i = 0; i < loadedMessages.size(); ++i) {
                MessageObject msg = loadedMessages.get(i);
                if (msg != null && !msgIds.contains(msg.getId())) {
                    msgIds.add(msg.getId());
                    messages.add(msg);
                }
            }
            for (int i = 0; i < cachedMessages.size(); ++i) {
                MessageObject msg = cachedMessages.get(i);
                if (msg != null && !msgIds.contains(msg.getId())) {
                    msgIds.add(msg.getId());
                    messages.add(msg);
                }
            }

            if (!fromCache || !cachedMessages.isEmpty()) {
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == TAB_SAVED_DIALOGS) {
                        if (messages.isEmpty() && dialogs.isEmpty()) {
                            mediaPages[a].emptyView.title.setText(lastReaction != null && TextUtils.isEmpty(lastQuery) ? AndroidUtilities.replaceCharSequence("%s", getString(R.string.NoResultFoundForTag), lastReaction.toCharSequence(mediaPages[a].emptyView.title.getPaint().getFontMetricsInt())) : LocaleController.formatString(R.string.NoResultFoundFor, lastQuery));
                            mediaPages[a].emptyView.button.setVisibility(View.GONE);
                            mediaPages[a].emptyView.showProgress(false, true);
                        }
                    }
                }
            }
            oldItemCounts = count;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            if (position < 0) return position;
            if (position < dialogs.size()) {
                return Objects.hash(1, dialogs.get(position).dialogId);
            }
            position -= dialogs.size();
            if (position < messages.size()) {
                return Objects.hash(2, messages.get(position).getSavedDialogId(), messages.get(position).getId());
            }
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(null, mContext, false, true) {
                @Override
                public boolean isForumCell() {
                    return false;
                }
            };
            cell.setDialogCellDelegate(SharedMediaLayout.this);
            cell.isSavedDialog = true;
            cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0) return;
            if (!(holder.itemView instanceof DialogCell)) return;
            DialogCell cell = (DialogCell) holder.itemView;
            cell.useSeparator = position + 1 < getItemCount();
            if (position < dialogs.size()) {
                final SavedMessagesController.SavedDialog d = dialogs.get(position);
                cell.setDialog(d.dialogId, d.message, d.getDate(), false, false);
            } else {
                position -= dialogs.size();
                if (position < messages.size()) {
                    MessageObject msg = messages.get(position);
                    cell.setDialog(msg.getSavedDialogId(), msg, msg.messageOwner.date, false, false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return VIEW_TYPE_SEARCH_SAVED_DIALOG;
        }

        @Override
        public int getItemCount() {
            return dialogs.size() + messages.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private class ChannelRecommendationsAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;
        private final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        private int more;

        public ChannelRecommendationsAdapter(Context context) {
            mContext = context;
            update(false);
        }

        public void update(boolean notify) {
            if (profileActivity == null || !DialogObject.isChatDialog(dialog_id)) {
                return;
            }
            TLRPC.Chat thisChat = MessagesController.getInstance(profileActivity.getCurrentAccount()).getChat(-dialog_id);
            if (thisChat == null || !ChatObject.isChannelAndNotMegaGroup(thisChat)) {
                return;
            }
            MessagesController.ChannelRecommendations rec = MessagesController.getInstance(profileActivity.getCurrentAccount()).getChannelRecommendations(thisChat.id);
            chats.clear();
            if (rec != null) {
                for (int i = 0; i < rec.chats.size(); ++i) {
                    TLRPC.Chat chat = rec.chats.get(i);
                    if (chat != null && ChatObject.isNotInChat(chat)) {
                        chats.add(chat);
                    }
                }
            }
            more = chats.isEmpty() || UserConfig.getInstance(profileActivity.getCurrentAccount()).isPremium() ? 0 : rec.more;
            if (notify) {
                notifyDataSetChanged();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_SIMILAR_CHANNEL_BLOCK) {
                MoreRecommendationsCell cell = new MoreRecommendationsCell(profileActivity == null ? UserConfig.selectedAccount : profileActivity.getCurrentAccount(), mContext, resourcesProvider, () -> {
                    if (profileActivity != null) {
                        profileActivity.presentFragment(new PremiumPreviewFragment("similar_channels"));
                    }
                });
                view = cell;
            } else {
                view = new ProfileSearchCell(mContext, resourcesProvider);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        public void openPreview(int position) {
            if (position < 0 || position >= chats.size()) return;
            TLRPC.Chat chat = chats.get(position);

            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            final BaseFragment fragment = new ChatActivity(args);
            if (profileActivity instanceof ProfileActivity) {
                ((ProfileActivity) profileActivity).prepareBlurBitmap();
            }

            ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), R.drawable.popup_fixed_alert, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_SHOWN_FROM_BOTTOM);
            previewMenu.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

            ActionBarMenuSubItem openChannel = new ActionBarMenuSubItem(getContext(), false, false);
            openChannel.setTextAndIcon(getString(R.string.OpenChannel2), R.drawable.msg_channel);
            openChannel.setMinimumWidth(160);
            openChannel.setOnClickListener(view -> {
                if (profileActivity != null && profileActivity.getParentLayout() != null) {
                    profileActivity.getParentLayout().expandPreviewFragment();
                }
            });
            previewMenu.addView(openChannel);

            ActionBarMenuSubItem joinChannel = new ActionBarMenuSubItem(getContext(), false, false);
            joinChannel.setTextAndIcon(getString(R.string.ProfileJoinChannel), R.drawable.msg_addbot);
            joinChannel.setMinimumWidth(160);
            joinChannel.setOnClickListener(view -> {
                profileActivity.finishPreviewFragment();
                chat.left = false;
                update(false);
                notifyItemRemoved(position);
                if (chats.isEmpty()) {
                    updateTabs(true);
                    checkCurrentTabValid();
                }
                profileActivity.getNotificationCenter().postNotificationName(NotificationCenter.channelRecommendationsLoaded, -dialog_id);
                profileActivity.getMessagesController().addUserToChat(chat.id, profileActivity.getUserConfig().getCurrentUser(), 0, null, profileActivity, () -> {
                    BulletinFactory.of(profileActivity).createSimpleBulletin(R.raw.contact_check, LocaleController.formatString(R.string.YouJoinedChannel, chat == null ? "" : chat.title)).show(true);
                });
            });
            previewMenu.addView(joinChannel);

            profileActivity.presentFragmentAsPreviewWithMenu(fragment, previewMenu);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ProfileSearchCell cell = null;
            if (holder.getItemViewType() == VIEW_TYPE_SIMILAR_CHANNEL) {
                if (!(holder.itemView instanceof ProfileSearchCell)) return;
                cell = (ProfileSearchCell) holder.itemView;
            } else if (holder.getItemViewType() == VIEW_TYPE_SIMILAR_CHANNEL_BLOCK) {
                if (!(holder.itemView instanceof MoreRecommendationsCell)) return;
                cell = ((MoreRecommendationsCell) holder.itemView).channelCell;
            }
            if (cell != null) {
                TLRPC.Chat chat = chats.get(position);
                cell.setData(chat, null, null, null, false, false);
                cell.useSeparator = position != chats.size() - 1;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (more > 0 && position == getItemCount() - 1) {
                return VIEW_TYPE_SIMILAR_CHANNEL_BLOCK;
            }
            return VIEW_TYPE_SIMILAR_CHANNEL;
        }
    }

    private static class MoreRecommendationsCell extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        public final ProfileSearchCell channelCell;

        private final View gradientView;
        private final ButtonWithCounterView button;
        private final LinkSpanDrawable.LinksTextView textView;

        public MoreRecommendationsCell(int currentAccount, Context context, Theme.ResourcesProvider resourcesProvider, Runnable onPremiumClick) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            channelCell = new ProfileSearchCell(context, resourcesProvider);
            channelCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            gradientView = new View(context);
            gradientView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), .4f),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)
            }));
            addView(gradientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60));

            button = new ButtonWithCounterView(context, resourcesProvider);
            SpannableStringBuilder buttonText = new SpannableStringBuilder();
            buttonText.append(getString(R.string.MoreSimilarButton));
            buttonText.append(" ");
            SpannableString lock = new SpannableString("l");
            lock.setSpan(new ColoredImageSpan(R.drawable.msg_mini_lock2), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            buttonText.append(lock);
            button.setText(buttonText, false);
            addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 14, 38, 14, 0));
            button.setOnClickListener(v -> {
                if (onPremiumClick != null) {
                    onPremiumClick.run();
                }
            });

            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            textView.setLineSpacing(dp(3), 1f);
            SpannableStringBuilder text = AndroidUtilities.premiumText(getString(R.string.MoreSimilarText), () -> {
                if (onPremiumClick != null) {
                    onPremiumClick.run();
                }
            });
            SpannableString count = new SpannableString("" + MessagesController.getInstance(currentAccount).recommendedChannelsLimitPremium);
            count.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, count.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(AndroidUtilities.replaceCharSequence("%s", text, count));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 24, 96, 24, 12));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(145), MeasureSpec.EXACTLY));
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
                case VIEW_TYPE_GROUP:
                    view = new ProfileSearchCell(mContext, resourcesProvider);
                    break;
                case VIEW_TYPE_GROUP_EMPTY:
                    View emptyStubView = createEmptyStubView(mContext, 6, dialog_id, resourcesProvider);
                    emptyStubView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return new RecyclerListView.Holder(emptyStubView);
                case VIEW_TYPE_GROUP_LOADING:
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
            if (holder.getItemViewType() == VIEW_TYPE_GROUP) {
                if (!(holder.itemView instanceof ProfileSearchCell)) return;
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                TLRPC.Chat chat = chats.get(position);
                cell.setData(chat, null, null, null, false, false);
                cell.useSeparator = position != chats.size() - 1 || !endReached;
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (chats.isEmpty() && !loading) {
                return VIEW_TYPE_GROUP_EMPTY;
            }
            if (i < chats.size()) {
                return VIEW_TYPE_GROUP;
            } else {
                return VIEW_TYPE_GROUP_LOADING;
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

    public String getBotPreviewsSubtitle(boolean edit) {
        if (!isBot()) {
            return getString(R.string.BotPreviewEmpty);
        }
        if (edit && botPreviewsContainer != null) {
            return botPreviewsContainer.getBotPreviewsSubtitle();
        }
        int images = 0, videos = 0;
        if (storiesAdapter != null && storiesAdapter.storiesList != null) {
            for (int i = 0; i < storiesAdapter.storiesList.messageObjects.size(); ++i) {
                MessageObject msg = storiesAdapter.storiesList.messageObjects.get(i);
                if (msg.storyItem != null && msg.storyItem.media != null) {
                    if (MessageObject.isVideoDocument(msg.storyItem.media.document)) {
                        videos++;
                    } else if (msg.storyItem.media.photo != null) {
                        images++;
                    }
                }
            }
        }
        if (images == 0 && videos == 0) return getString(R.string.BotPreviewEmpty);
        StringBuilder sb = new StringBuilder();
        if (images > 0) sb.append(formatPluralString("Images", images));
        if (videos > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(formatPluralString("Videos", videos));
        }
        return sb.toString();
    }

    public StoriesController.StoriesList searchStoriesList;
    public void updateStoriesList(StoriesController.StoriesList list) {
        searchStoriesList = list;
        storiesAdapter.storiesList = list;
        storiesAdapter.notifyDataSetChanged();
        animationSupportingStoriesAdapter.storiesList = list;
        animationSupportingStoriesAdapter.notifyDataSetChanged();
    }

    public class StoriesAdapter extends SharedPhotoVideoAdapter {

        private final boolean isArchive;
        private final ArrayList<StoriesController.UploadingStory> uploadingStories = new ArrayList<>();
        @Nullable
        public StoriesController.StoriesList storiesList;
        private StoriesAdapter supportingAdapter;
        private int id;

        private ViewsForPeerStoriesRequester poller;

        public StoriesAdapter(Context context, boolean isArchive) {
            super(context);
            this.isArchive = isArchive;
            final int currentAccount = profileActivity.getCurrentAccount();
            if (!TextUtils.isEmpty(getStoriesHashtag())) {
                if (searchStoriesList == null) {
                    searchStoriesList = new StoriesController.SearchStoriesList(currentAccount, TextUtils.isEmpty(getStoriesHashtagUsername()) ? null : getStoriesHashtagUsername(), getStoriesHashtag());
                }
                storiesList = searchStoriesList;
            } else if (getStoriesArea() != null) {
                if (searchStoriesList == null) {
                    searchStoriesList = new StoriesController.SearchStoriesList(currentAccount, getStoriesArea());
                }
                storiesList = searchStoriesList;
            } else if (isArchive && !isStoriesView() || !isArchive && isArchivedOnlyStoriesView()) {
                storiesList = null;
            } else {
                boolean isBot = false;
                if (dialog_id > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog_id);
                    isBot = user != null && user.bot;
                }
                storiesList = profileActivity.getMessagesController().getStoriesController().getStoriesList(dialog_id, isBot ? StoriesController.StoriesList.TYPE_BOTS : isArchive ? StoriesController.StoriesList.TYPE_ARCHIVE : StoriesController.StoriesList.TYPE_PINNED);
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

        public boolean isSelectedAll() {
            if (storiesList == null) return false;
            for (int i = 0; i < storiesList.messageObjects.size(); ++i) {
                final MessageObject msg = storiesList.messageObjects.get(i);
                boolean found = false;
                final SparseArray<MessageObject> arr = selectedFiles[msg.getDialogId() == dialog_id ? 0 : 1];
                for (int j = 0; j < arr.size(); ++j) {
                    int key = arr.keyAt(j);
                    MessageObject m = arr.get(key);
                    if (msg == m) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void notifyDataSetChanged() {
            if (storiesList != null && isBot()) {
                uploadingStories.clear();
                ArrayList<StoriesController.UploadingStory> list = MessagesController.getInstance(storiesList.currentAccount).getStoriesController().getUploadingStories(dialog_id);
                if (list != null) {
                    uploadingStories.addAll(list);
                }
            }
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
            return uploadingStories.size() + (storiesList.isOnlyCache() && hasInternet() ? 0 : storiesList.getCount());
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
            if (viewType == VIEW_TYPE_STORY) {
                if (!(holder.itemView instanceof SharedPhotoVideoCell2)) return;
                SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) holder.itemView;
                cell.isStory = true;
                if (position >= 0 && position < uploadingStories.size()) {
                    StoriesController.UploadingStory uploadingStory = uploadingStories.get(position);
                    cell.isStoryPinned = false;
                    if (uploadingStory.sharedMessageObject == null) {
                        final TL_stories.TL_storyItem storyItem = new TL_stories.TL_storyItem();
                        storyItem.id = storyItem.messageId = Long.hashCode(uploadingStory.random_id);
                        storyItem.attachPath = uploadingStory.firstFramePath;
                        uploadingStory.sharedMessageObject = new MessageObject(storiesList.currentAccount, storyItem) {
                            @Override
                            public float getProgress() {
                                return uploadingStory.progress;
                            }
                        };
                        uploadingStory.sharedMessageObject.uploadingStory = uploadingStory;
                    }
                    cell.setMessageObject(uploadingStory.sharedMessageObject, columnsCount());
                    cell.isStory = true;
                    cell.setReorder(false);
                    cell.setChecked(false, false);
                    return;
                }
                position -= uploadingStories.size();
                if (position < 0 || position >= storiesList.messageObjects.size()) {
                    cell.isStoryPinned = false;
                    cell.setMessageObject(null, columnsCount());
                    cell.isStory = true;
                    return;
                }
                MessageObject messageObject = storiesList.messageObjects.get(position);
                cell.isStoryPinned = messageObject != null && storiesList.isPinned(messageObject.getId());
                cell.setReorder(isBot() || cell.isStoryPinned);
                cell.isSearchingHashtag = isSearchingStories();
                cell.setMessageObject(messageObject, columnsCount());
                if (isActionModeShowed && messageObject != null) {
                    cell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, true);
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
            return VIEW_TYPE_STORY;
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

        public boolean canReorder(int position) {
            if (isArchive) return false;
            if (storiesList == null) return false;
            if (storiesList instanceof StoriesController.BotPreviewsList) {
                TLRPC.User user = MessagesController.getInstance(profileActivity.getCurrentAccount()).getUser(dialog_id);
                return user != null && user.bot && user.bot_has_main_app && user.bot_can_edit;
            }
            if (position < 0 || position >= storiesList.messageObjects.size()) return false;
            MessageObject messageObject = storiesList.messageObjects.get(position);
            return storiesList.isPinned(messageObject.getId());
        }

        public ArrayList<Integer> lastPinnedIds = new ArrayList<>();
        public boolean applyingReorder;

        public boolean swapElements(int fromPosition, int toPosition) {
            if (isArchive) return false;
            if (storiesList == null) return false;
            if (fromPosition < 0 || fromPosition >= storiesList.messageObjects.size()) return false;
            if (toPosition < 0 || toPosition >= storiesList.messageObjects.size()) return false;

            ArrayList<Integer> pinnedIds;
            if (storiesList instanceof StoriesController.BotPreviewsList) {
                pinnedIds = new ArrayList<>();
                for (int i = 0; i < storiesList.messageObjects.size(); ++i) {
                    pinnedIds.add(storiesList.messageObjects.get(i).getId());
                }
            } else {
                pinnedIds = new ArrayList<>(storiesList.pinnedIds);
            }

            if (!applyingReorder) {
                lastPinnedIds.clear();
                lastPinnedIds.addAll(pinnedIds);
                applyingReorder = true;
            }

            MessageObject from = storiesList.messageObjects.get(fromPosition);
            MessageObject to = storiesList.messageObjects.get(toPosition);

            pinnedIds.remove((Object) from.getId());
            pinnedIds.add(Utilities.clamp(toPosition, pinnedIds.size(), 0), from.getId());

            storiesList.updatePinnedOrder(pinnedIds, false);

            notifyItemMoved(fromPosition, toPosition);

            return true;
        }

        public void reorderDone() {
            if (isArchive) return;
            if (storiesList == null) return;
            if (!applyingReorder) return;

            ArrayList<Integer> ids;
            if (storiesList instanceof StoriesController.BotPreviewsList) {
                ids = new ArrayList<>();
                for (int i = 0; i < storiesList.messageObjects.size(); ++i) {
                    ids.add(storiesList.messageObjects.get(i).getId());
                }
            } else {
                ids = storiesList.pinnedIds;
            }

            boolean changed = lastPinnedIds.size() != ids.size();
            if (!changed) {
                for (int i = 0; i < lastPinnedIds.size(); ++i) {
                    if (lastPinnedIds.get(i) != ids.get(i)) {
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                storiesList.updatePinnedOrder(ids, true);
            }

            applyingReorder = false;
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
            if (viewType == VIEW_TYPE_GROUPUSER_EMPTY) {
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
                            role = getString("ChannelCreator", R.string.ChannelCreator);
                        } else if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin) {
                            role = getString("ChannelAdmin", R.string.ChannelAdmin);
                        } else {
                            role = null;
                        }
                    }
                } else {
                    if (part instanceof TLRPC.TL_chatParticipantCreator) {
                        role = getString("ChannelCreator", R.string.ChannelCreator);
                    } else if (part instanceof TLRPC.TL_chatParticipantAdmin) {
                        role = getString("ChannelAdmin", R.string.ChannelAdmin);
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
                return VIEW_TYPE_GROUPUSER_EMPTY;
            }
            return VIEW_TYPE_GROUPUSER;
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
            return true;
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
            if (!(holder.itemView instanceof ManageChatUserCell)) return;
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
            return VIEW_TYPE_SEARCH_GROUPUSER;
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
        if (photoVideoChangeColumnsAnimation || mediaPages[0] == null || allowStoriesSingleColumn && (mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES)) {
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
        if (mediaPages == null || mediaPages[0] == null || allowStoriesSingleColumn && (mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES)) {
            return false;
        }
        final int ci = mediaPages[0].selectedType == TAB_STORIES || mediaPages[0].selectedType == TAB_ARCHIVED_STORIES ? 1 : 0;
        return mediaColumnsCount[ci] != getNextMediaColumnsCount(ci, mediaColumnsCount[ci], false);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == fragmentContextView) {
            canvas.save();
            canvas.clipRect(0, mediaPages[0].getTop(), child.getMeasuredWidth(),mediaPages[0].getTop() + child.getMeasuredHeight() + dp(12));
            boolean b = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return b;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public class ScrollSlidingTextTabStripInner extends ScrollSlidingTextTabStrip {

        protected Paint backgroundPaint;
        public int backgroundColor = Color.TRANSPARENT;


        public ScrollSlidingTextTabStripInner(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
        }

        private Rect blurBounds = new Rect();
        protected void drawBackground(Canvas canvas) {
            if (SharedConfig.chatBlurEnabled() && backgroundColor != Color.TRANSPARENT) {
                if (backgroundPaint == null) {
                    backgroundPaint = new Paint();
                }
                backgroundPaint.setColor(backgroundColor);
                blurBounds.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                drawBackgroundWithBlur(canvas, getY(), blurBounds, backgroundPaint);
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
        public int hintPaddingBottom;

        public InternalListView(Context context) {
            super(context);
        }

        @Override
        public void updateClip(int[] clip) {
            clip[0] = getPaddingTop() - dp(2) - hintPaddingTop;
            clip[1] = getMeasuredHeight() - getPaddingBottom() - hintPaddingBottom;
        }
    }

    private void updateOptionsSearch() {
        updateOptionsSearch(false);
    }
    private void updateOptionsSearch(boolean finish) {
        if (optionsSearchImageView == null) return;
        optionsSearchImageView.setAlpha(searching ? 0f : Utilities.clamp(searchAlpha + optionsAlpha, 1, 0));
        if (finish) {
            animateSearchToOptions(getPhotoVideoOptionsAlpha(1) > 0.5f, true);
        } else if (searchItemState == 2) {
            animateSearchToOptions(optionsAlpha > 0.1f, true);
        } else {
            animateSearchToOptions(searchAlpha < 0.1f, true);
        }
    }
    private boolean animatingToOptions;
    public void animateSearchToOptions(boolean toOptions, boolean animated) {
        if (optionsSearchImageView == null) return;
        if (animatingToOptions != toOptions) {
            animatingToOptions = toOptions;
            if (!animatingToOptions && optionsSearchImageView.getAnimatedDrawable().getCurrentFrame() < 20) {
                optionsSearchImageView.getAnimatedDrawable().setCustomEndFrame(0);
            } else {
                optionsSearchImageView.getAnimatedDrawable().setCustomEndFrame(animatingToOptions ? 50 : 100);
            }
            if (animated) {
                optionsSearchImageView.getAnimatedDrawable().start();
            } else {
                optionsSearchImageView.getAnimatedDrawable().setCurrentFrame(optionsSearchImageView.getAnimatedDrawable().getCustomEndFrame());
            }
        }
    }

    private SpannableStringBuilder addPostButton;
    private CharSequence addPostText() {
        if (addPostButton == null) {
            addPostButton = new SpannableStringBuilder();
            if (isBot()) {
                addPostButton.append(getString(R.string.ProfileBotPreviewEmptyButton));
            } else {
                addPostButton.append("c");
                addPostButton.setSpan(new ColoredImageSpan(R.drawable.filled_premium_camera), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                addPostButton.append("  ").append(getString(R.string.StoriesAddPost));
            }
        }
        return addPostButton;
    }

    public void showPremiumFloodWaitBulletin(final boolean isUpload) {
        if (profileActivity == null) return;

//        final long now = System.currentTimeMillis();
//        if (now - ConnectionsManager.lastPremiumFloodWaitShown < 1000L * MessagesController.getInstance(currentAccount).uploadPremiumSpeedupNotifyPeriod) {
//            return;
//        }
//        ConnectionsManager.lastPremiumFloodWaitShown = now;
//        if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
//            return;
//        }

        final int currentAccount = profileActivity.getCurrentAccount();

        final float n;
        if (isUpload) {
            n = MessagesController.getInstance(currentAccount).uploadPremiumSpeedupUpload;
        } else {
            n = MessagesController.getInstance(currentAccount).uploadPremiumSpeedupDownload;
        }
        SpannableString boldN = new SpannableString(Double.toString(Math.round(n * 10) / 10.0).replaceAll("\\.0$", ""));
        boldN.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, boldN.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (profileActivity.hasStoryViewer()) return;
        BulletinFactory.of(profileActivity).createSimpleBulletin(
            R.raw.speed_limit,
            LocaleController.getString(isUpload ? R.string.UploadSpeedLimited : R.string.DownloadSpeedLimited),
            AndroidUtilities.replaceCharSequence("%d", AndroidUtilities.premiumText(LocaleController.getString(isUpload ? R.string.UploadSpeedLimitedMessage : R.string.DownloadSpeedLimitedMessage), () -> {
                profileActivity.presentFragment(new PremiumPreviewFragment(isUpload ? "upload_speed" : "download_speed"));
            }), boldN)
        ).setDuration(8000).show(true);
    }

    public boolean canEditStories() {
        if (isBot()) {
            final int currentAccount = profileActivity.getCurrentAccount();
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog_id);
            return user != null && user.bot && user.bot_can_edit;
        }
        return isStoriesView() || profileActivity != null && profileActivity.getMessagesController().getStoriesController().canEditStories(dialog_id);
    }

    public void openStoryRecorder() {
        StoryRecorder.getInstance(profileActivity.getParentActivity(), profileActivity.getCurrentAccount()).open(null);
    }

    public String getStoriesHashtag() {
        return null;
    }

    public String getStoriesHashtagUsername() {
        return null;
    }

    public TL_stories.MediaArea getStoriesArea() {
        return null;
    }

    public boolean isSearchingStories() {
        return !TextUtils.isEmpty(getStoriesHashtag()) || getStoriesArea() != null;
    }

    public static class SharedMediaListView extends InternalListView {

        public SharedMediaListView(Context context) {
            super(context);
        }

        final HashSet<SharedPhotoVideoCell2> excludeDrawViews = new HashSet<>();
        final ArrayList<SharedPhotoVideoCell2> drawingViews = new ArrayList<>();
        final ArrayList<SharedPhotoVideoCell2> drawingViews2 = new ArrayList<>();
        final ArrayList<SharedPhotoVideoCell2> drawingViews3 = new ArrayList<>();

        protected TextPaint archivedHintPaint;
        protected StaticLayout archivedHintLayout;
        protected float archivedHintLayoutWidth, archivedHintLayoutLeft;

        UserListPoller poller;

        public RecyclerListView.FastScrollAdapter getMovingAdapter() {
            return null;
        }

        public RecyclerListView.FastScrollAdapter getSupportingAdapter() {
            return null;
        }

        public int getColumnsCount() {
            return 3;
        }

        public int getAnimateToColumnsCount() {
            return 3;
        }

        public boolean isChangeColumnsAnimation() {
            return false;
        }

        public float getChangeColumnsProgress() {
            return 0;
        }

        public boolean isThisListView() {
            return true;
        }

        public SparseArray<Float> getMessageAlphaEnter() {
            return null;
        }

        public InternalListView getSupportingListView() {
            return null;
        }

        public int getPinchCenterPosition() {
            return 0;
        }

        public boolean isStories() {
            return false;
        }

        public void checkHighlightCell(SharedPhotoVideoCell2 cell) {

        }

        private int animationSupportingSortedCellsOffset;
        private final ArrayList<SharedPhotoVideoCell2> animationSupportingSortedCells = new ArrayList<>();

        @Override
        protected void dispatchDraw(Canvas canvas) {
            final RecyclerListView.FastScrollAdapter movingAdapter = getMovingAdapter();
            final RecyclerListView.FastScrollAdapter supportingMovingAdapter = getSupportingAdapter();
            if (isThisListView() && getAdapter() == movingAdapter) {
                int firstVisibleItemPosition = 0;
                int firstVisibleItemPosition2 = 0;
                int lastVisibleItemPosition = 0;
                int lastVisibleItemPosition2 = 0;

                int rowsOffset = 0;
                int columnsOffset = 0;
                float minY = getMeasuredHeight();
                if (isChangeColumnsAnimation()) {
                    int max = -1;
                    int min = -1;
                    for (int i = 0; i < getChildCount(); i++) {
                        int p = getChildAdapterPosition(getChildAt(i));
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
                    for (int i = 0; i < getSupportingListView().getChildCount(); i++) {
                        int p = getSupportingListView().getChildAdapterPosition(getSupportingListView().getChildAt(i));
                        if (p >= 0 && (p > max || max == -1)) {
                            max = p;
                        }
                        if (p >= 0 && (p < min || min == -1)) {
                            min = p;
                        }
                    }

                    firstVisibleItemPosition2 = min;
                    lastVisibleItemPosition2 = max;

                    if (firstVisibleItemPosition >= 0 && firstVisibleItemPosition2 >= 0 && getPinchCenterPosition() >= 0) {
                        int rowsCount1 = (int) Math.ceil((movingAdapter.getItemCount()) / (float) getColumnsCount());
                        int rowsCount2 = (int) Math.ceil((movingAdapter.getItemCount()) / (float) getAnimateToColumnsCount());
                        rowsOffset = ((getPinchCenterPosition()) / getAnimateToColumnsCount() - firstVisibleItemPosition2 / getAnimateToColumnsCount()) - ((getPinchCenterPosition()) / getColumnsCount() - firstVisibleItemPosition / getColumnsCount());
                        if ((firstVisibleItemPosition / getColumnsCount() - rowsOffset < 0  && getAnimateToColumnsCount() < getColumnsCount()) || (firstVisibleItemPosition2 / getAnimateToColumnsCount() + rowsOffset < 0 && getAnimateToColumnsCount() > getColumnsCount())) {
                            rowsOffset = 0;
                        }
                        if ((lastVisibleItemPosition2 / getColumnsCount() + rowsOffset >= rowsCount1 && getAnimateToColumnsCount() > getColumnsCount()) || (lastVisibleItemPosition / getAnimateToColumnsCount() - rowsOffset >= rowsCount2 && getAnimateToColumnsCount() < getColumnsCount())) {
                            rowsOffset = 0;
                        }

                        float k = (getPinchCenterPosition() % getColumnsCount()) / (float) (getColumnsCount() - 1);
                        columnsOffset = (int) ((getAnimateToColumnsCount() - getColumnsCount()) * k);
                    }
                    animationSupportingSortedCells.clear();
                    excludeDrawViews.clear();
                    drawingViews.clear();
                    drawingViews2.clear();
                    drawingViews3.clear();
                    animationSupportingSortedCellsOffset = 0;
                    for (int i = 0; i < getSupportingListView().getChildCount(); i++) {
                        View child = getSupportingListView().getChildAt(i);
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
                    RecyclerListView.FastScroll fastScroll = getFastScroll();
                    if (fastScroll != null && fastScroll.getTag() != null) {
                        float p1 = movingAdapter.getScrollProgress(this);
                        float p2 = supportingMovingAdapter.getScrollProgress(getSupportingListView());
                        float a1 = movingAdapter.fastScrollIsVisible(this) ? 1f : 0f;
                        float a2 = supportingMovingAdapter.fastScrollIsVisible(getSupportingListView()) ? 1f : 0f;
                        fastScroll.setProgress(p1 * (1f - getChangeColumnsProgress()) + p2 * getChangeColumnsProgress());
                        fastScroll.setVisibilityAlpha(a1 * (1f - getChangeColumnsProgress()) + a2 * getChangeColumnsProgress());
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
                            cell.setImageScale(1f, !isChangeColumnsAnimation());
                        }
                        continue;
                    }
                    if (child instanceof SharedPhotoVideoCell2) {
                        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) getChildAt(i);
                        checkHighlightCell(cell);

                        MessageObject messageObject = cell.getMessageObject();
                        float alpha = 1f;
                        if (messageObject != null && getMessageAlphaEnter() != null && getMessageAlphaEnter().get(messageObject.getId(), null) != null) {
                            alpha = getMessageAlphaEnter().get(messageObject.getId(), 1f);
                        }
                        cell.setImageAlpha(alpha, !isChangeColumnsAnimation());

                        boolean inAnimation = false;
                        if (isChangeColumnsAnimation()) {
                            float fromScale = 1f;

                            int currentColumn = (((GridLayoutManager.LayoutParams) cell.getLayoutParams()).getViewAdapterPosition()) % getColumnsCount() + columnsOffset;
                            int currentRow = ((((GridLayoutManager.LayoutParams) cell.getLayoutParams()).getViewAdapterPosition()) - firstVisibleItemPosition) / getColumnsCount() + rowsOffset;
                            int toIndex = currentRow * getAnimateToColumnsCount() + currentColumn + animationSupportingSortedCellsOffset;
                            if (currentColumn >= 0 && currentColumn < getAnimateToColumnsCount() && toIndex >= 0 && toIndex < animationSupportingSortedCells.size()) {
                                inAnimation = true;
                                float toScale = (animationSupportingSortedCells.get(toIndex).getMeasuredWidth() - AndroidUtilities.dpf2(2)) / (float) (cell.getMeasuredWidth() - AndroidUtilities.dpf2(2));
                                float scale = AndroidUtilities.lerp(fromScale, toScale, getChangeColumnsProgress());
                                float fromX = cell.getLeft();
                                float fromY = cell.getTop();
                                float toX = animationSupportingSortedCells.get(toIndex).getLeft();
                                float toY = animationSupportingSortedCells.get(toIndex).getTop();

                                cell.setPivotX(0);
                                cell.setPivotY(0);
                                cell.setImageScale(scale, !isChangeColumnsAnimation());
                                cell.setTranslationX((toX - fromX) * getChangeColumnsProgress());
                                cell.setTranslationY((toY - fromY) * getChangeColumnsProgress());
                                cell.setCrossfadeView(animationSupportingSortedCells.get(toIndex), getChangeColumnsProgress(), getAnimateToColumnsCount());
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
                            if (isChangeColumnsAnimation()) {
                                drawingViews2.add(cell);
                            }
                            cell.setCrossfadeView(null, 0, 0);
                            cell.setTranslationX(0);
                            cell.setTranslationY(0);
                            cell.setImageScale(1f, !isChangeColumnsAnimation());
                        }
                    }
                }

                if (isChangeColumnsAnimation() && !drawingViews.isEmpty()) {
                    float toScale = getAnimateToColumnsCount() / (float) getColumnsCount();
                    float scale = toScale * (1f - getChangeColumnsProgress()) + getChangeColumnsProgress();

                    float sizeToScale = ((getMeasuredWidth() / (float) getColumnsCount()) - AndroidUtilities.dpf2(2)) / ((getMeasuredWidth() / (float) getAnimateToColumnsCount()) - AndroidUtilities.dpf2(2));
                    float scaleSize = sizeToScale * (1f - getChangeColumnsProgress()) + getChangeColumnsProgress();

                    float fromSize = getMeasuredWidth() / (float) getColumnsCount();
                    float toSize = (getMeasuredWidth() / (float) getAnimateToColumnsCount());
                    float size1 = (float) ((Math.ceil((getMeasuredWidth() / (float) getAnimateToColumnsCount())) - AndroidUtilities.dpf2(2)) * scaleSize + AndroidUtilities.dpf2(2));
                    if (isStories()) {
                        size1 *= 1.25f;
                    }

                    for (int i = 0; i < drawingViews.size(); i++) {
                        SharedPhotoVideoCell2 view = drawingViews.get(i);
                        if (excludeDrawViews.contains(view)) {
                            continue;
                        }
                        view.setCrossfadeView(null, 0, 0);
                        int fromColumn = (((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) % getAnimateToColumnsCount();
                        int toColumn = fromColumn - columnsOffset;
                        int currentRow = ((((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) - firstVisibleItemPosition2) / getAnimateToColumnsCount();
                        currentRow -= rowsOffset;

                        canvas.save();
                        canvas.translate(toColumn * fromSize * (1f - getChangeColumnsProgress()) + toSize * fromColumn * getChangeColumnsProgress(), minY + size1 * currentRow);
                        view.setImageScale(scaleSize, !isChangeColumnsAnimation());
                        if (toColumn < getColumnsCount()) {
                            canvas.saveLayerAlpha(0, 0, view.getMeasuredWidth() * scale, view.getMeasuredHeight() * scale, (int) (getChangeColumnsProgress() * 255), Canvas.ALL_SAVE_FLAG);
                            view.draw(canvas);
                            canvas.restore();
                        } else {
                            view.draw(canvas);
                        }
                        canvas.restore();
                    }
                }

                super.dispatchDraw(canvas);

                if (isChangeColumnsAnimation()) {
                    float toScale = getColumnsCount() / (float) getAnimateToColumnsCount();
                    float scale = toScale * getChangeColumnsProgress() + (1f - getChangeColumnsProgress());

                    float sizeToScale = ((getMeasuredWidth() / (float) getAnimateToColumnsCount()) - AndroidUtilities.dpf2(2)) / ((getMeasuredWidth() / (float) getColumnsCount()) - AndroidUtilities.dpf2(2));
                    float scaleSize = sizeToScale * getChangeColumnsProgress() + (1f - getChangeColumnsProgress());

                    float size1 = (float) ((Math.ceil((getMeasuredWidth() / (float) getColumnsCount())) - AndroidUtilities.dpf2(2)) * scaleSize + AndroidUtilities.dpf2(2));
                    if (isStories()) {
                        size1 *= 1.25f;
                    }
                    float fromSize = getMeasuredWidth() / (float) getColumnsCount();
                    float toSize = getMeasuredWidth() / (float) getAnimateToColumnsCount();

                    for (int i = 0; i < drawingViews2.size(); i++) {
                        SharedPhotoVideoCell2 view = drawingViews2.get(i);
                        int fromColumn = (((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) % getColumnsCount();
                        int currentRow = ((((GridLayoutManager.LayoutParams) view.getLayoutParams()).getViewAdapterPosition()) - firstVisibleItemPosition) / getColumnsCount();

                        currentRow += rowsOffset;
                        int toColumn = fromColumn + columnsOffset;

                        canvas.save();
                        view.setImageScale(scaleSize, !isChangeColumnsAnimation());
                        canvas.translate(fromColumn * fromSize * (1f - getChangeColumnsProgress()) + toSize * toColumn * getChangeColumnsProgress(), minY + size1 * currentRow);
                        if (toColumn < getAnimateToColumnsCount()) {
                            canvas.saveLayerAlpha(0, 0, view.getMeasuredWidth() * scale, view.getMeasuredHeight() * scale, (int) ((1f - getChangeColumnsProgress()) * 255), Canvas.ALL_SAVE_FLAG);
                            view.draw(canvas);
                            canvas.restore();
                        } else {
                            view.draw(canvas);
                        }
                        canvas.restore();
                    }

                    if (!drawingViews3.isEmpty()) {
                        canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (255 * getChangeColumnsProgress()), Canvas.ALL_SAVE_FLAG);
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
                    if (messageId != 0 && getMessageAlphaEnter() != null && getMessageAlphaEnter().get(messageId, null) != null) {
                        alpha = getMessageAlphaEnter().get(messageId, 1f);
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
        }

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            final RecyclerListView.FastScrollAdapter movingAdapter = getMovingAdapter();
            if (isThisListView() && getAdapter() == movingAdapter) {
                if (isChangeColumnsAnimation() && child instanceof SharedPhotoVideoCell2) {
                    return true;
                }
            }
            return super.drawChild(canvas, child, drawingTime);
        }
    };

    public boolean addActionButtons() {
        return true;
    }
}
