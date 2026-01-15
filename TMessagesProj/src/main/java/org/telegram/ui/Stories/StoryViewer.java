package org.telegram.ui.Stories;

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileStreamLoadOperation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.pip.PipSource;
import org.telegram.messenger.pip.source.IPipSourceDelegate;
import org.telegram.messenger.pip.utils.PipPermissions;
import org.telegram.messenger.pip.utils.PipUtils;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.messenger.video.VideoPlayerHolderBase;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Stories.LiveStoryPipOverlay;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.LivePlayerView;

import java.util.ArrayList;

public class StoryViewer implements NotificationCenter.NotificationCenterDelegate, BaseFragment.AttachedSheet, IPipSourceDelegate {

    public static boolean animationInProgress;

    public boolean USE_SURFACE_VIEW = SharedConfig.useSurfaceInStories;
    public boolean ATTACH_TO_FRAGMENT = true;
    public boolean ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE = false;
    public boolean foundViewToClose = false;

    public int allowScreenshotsCounter;
    public boolean allowScreenshots = true;
    public static ArrayList<StoryViewer> globalInstances = new ArrayList<>();

    BaseFragment fragment;
    public int currentAccount;
    WindowManager windowManager;
    WindowManager.LayoutParams windowLayoutParams;
    public SizeNotifierFrameLayout windowView;
    HwFrameLayout containerView;
    SelfStoryViewsView selfStoryViewsView;

    Paint inputBackgroundPaint;
    boolean keyboardVisible;
    private static TL_stories.StoryItem lastStoryItem;

    Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();
    private boolean opening;
    ValueAnimator openCloseAnimator;
    ValueAnimator swipeToDissmissBackAnimator;
    ValueAnimator swipeToReplyBackAnimator;

    long lastDialogId;
    int lastPosition;

    float fromXCell;
    float fromYCell;
    StoriesListPlaceProvider.AvatarOverlaysView animateFromCell;
    float fromX;
    float fromY;

    float clipTop;
    float clipBottom;

    float fromWidth;
    float fromHeight;

    RectF avatarRectTmp = new RectF();
    float progressToOpen;
    float progressToDismiss;
    float swipeToDismissOffset;
    float swipeToDismissHorizontalOffset;
    float swipeToDismissHorizontalDirection;
    float swipeToReplyOffset;
    boolean swipeToReplyWaitingKeyboard;

    float fromDismissOffset;

    boolean allowSelfStoriesView;
    float swipeToReplyProgress;
    float progressToSelfStoryViewsViews;
    float selfStoriesViewsOffset;


    boolean allowIntercept;
    boolean verticalScrollDetected;
    boolean allowSwipeToDissmiss;
    GestureDetector gestureDetector;
    boolean inSwipeToDissmissMode;
    boolean inSeekingMode;
    boolean allowSwipeToReply;
    boolean isShowing;
    public StoriesViewPager storiesViewPager;
    float pointPosition[] = new float[2];

    private int realKeyboardHeight;
    private boolean isInTouchMode;
    private float hideEnterViewProgress;
    public final TransitionViewHolder transitionViewHolder = new TransitionViewHolder();
    public PlaceProvider placeProvider;
    Dialog currentDialog;
    BaseFragment.AttachedSheet currentSheet;
    private boolean allowTouchesByViewpager = false;
    boolean openedFromLightNavigationBar;
    ArrayList<Runnable> doOnAnimationReadyRunnables = new ArrayList<>();

    // to prevent attach/detach textureView in view pager and
    // ensure that player is singleton
    // create and attach texture view in contentView
    // draw it in page
    AspectRatioFrameLayout aspectRatioFrameLayout;
    VideoPlayerHolder playerHolder;
    LivePlayer livePlayer;
    private PipSource pipSource;
    private TextureView textureView;
    private SurfaceView surfaceView;
    public LivePlayerView liveView;
    public LivePlayerView pipLiveView;
    Uri lastUri;
    PeerStoriesView.VideoPlayerSharedScope currentPlayerScope;
    private boolean isClosed = true;
    private boolean isRecording;
    AnimationNotificationsLocker locker = new AnimationNotificationsLocker();
    private boolean isWaiting;
    private boolean fullyVisible;
    private boolean isCaption;
    LaunchActivity parentActivity;
    ArrayList<VideoPlayerHolder> preparedPlayers = new ArrayList<>();

    boolean isSingleStory;
    StoriesController.StoriesList storiesList;
    public int dayStoryId;
    TL_stories.PeerStories overrideUserStories;
    boolean reversed;

    boolean singleStoryDeleted;
    TL_stories.StoryItem singleStory;
    private int messageId;
    private boolean animateAvatar;
    private int fromRadius;
    private static boolean runOpenAnimationAfterLayout;
    private boolean isPopupVisible;
    private boolean isBulletinVisible;
    public boolean isTranslating = false;

    public static float currentSpeed = 1f;

    public boolean isLongpressed;

    Runnable longPressRunnable = () -> setLongPressed(true);

    public boolean unreadStateChanged;
    private StoriesVolumeControl volumeControl;
    private static boolean checkSilentMode = true;
    private static boolean isInSilentMode;

    public LongSparseIntArray savedPositions = new LongSparseIntArray();
    private boolean isInPinchToZoom;
    private boolean flingCalled;
    private boolean invalidateOutRect;
    private boolean isHintVisible;
    private boolean isInTextSelectionMode;
    private boolean isOverlayVisible;
    Bitmap playerStubBitmap;
    public Paint playerStubPaint;
    private boolean isSwiping;
    private boolean isCaptionPartVisible;
    private Runnable delayedTapRunnable;
    private Runnable onCloseListener;
    private boolean isLikesReactions;
    private float lastStoryContainerHeight;

    private static final LongSparseArray<CharSequence> replyDrafts = new LongSparseArray<>();
    public boolean fromBottomSheet;
    private boolean paused;
    private long playerSavedPosition;
    private StoriesIntro storiesIntro;

    public static boolean isShowingImage(MessageObject messageObject) {
        if (lastStoryItem == null || messageObject.type != MessageObject.TYPE_STORY && !messageObject.isWebpage() || runOpenAnimationAfterLayout) {
            return false;
        }
        return lastStoryItem.messageId == messageObject.getId() && lastStoryItem.messageType != 3;
    }

    public static void closeGlobalInstances() {
        for (int i = 0; i < globalInstances.size(); i++) {
            globalInstances.get(i).close(false);
        }
        globalInstances.clear();
    }

    private void setLongPressed(boolean b) {
        if (isLongpressed != b) {
            isLongpressed = b;
            if (b && !isInPinchToZoom) {
                PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
                if (peerView != null && peerView.currentStory != null && !peerView.currentStory.isLive() && peerView.currentStory.uploadingStory == null) {
                    if (!inSeekingMode && !inSwipeToDissmissMode && currentPlayerScope != null && currentPlayerScope.player != null) {
                        peerView.storyContainer.invalidate();
                        BotWebViewVibrationEffect.IMPACT_LIGHT.vibrate();
                    }
                    if (currentPlayerScope != null && currentPlayerScope.player != null && !inSeekingMode) {
                        currentPlayerScope.player.setSeeking(true);
                    }
                    inSeekingMode = true;
                }
            }
            updatePlayingMode();
            if (storiesViewPager != null) {
                PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
                if (peerStoriesView != null) {
                    peerStoriesView.setLongpressed(isLongpressed);
                }
            }
        }
    }

    public StoryViewer(BaseFragment fragment) {
        inputBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.fragment = fragment;
    }

    public void setSpeed(float speed) {
        currentSpeed = speed;
        if (playerHolder != null) {
            playerHolder.setSpeed(speed);
            // TODO: storyViewerStack
//            StoryViewer otherStoryViewer = null;
//            if (fragment != null) {
//                if (fragment.overlayStoryViewer != this) {
//                    otherStoryViewer = fragment.overlayStoryViewer;
//                } else if (fragment.storyViewer != this) {
//                    otherStoryViewer = fragment.storyViewer;
//                }
//            }
//            if (otherStoryViewer != null && otherStoryViewer.playerHolder != null) {
//                otherStoryViewer.playerHolder.setSpeed(speed);
//            }
        }
    }

    public void open(Context context, TL_stories.StoryItem storyItem, PlaceProvider placeProvider) {
        open(UserConfig.selectedAccount, context, storyItem, placeProvider);
    }
    public void open(int account, Context context, TL_stories.StoryItem storyItem, PlaceProvider placeProvider) {
        if (storyItem == null) {
            return;
        }
        currentAccount = account;
        if (storyItem.dialogId > 0 && MessagesController.getInstance(currentAccount).getUser(storyItem.dialogId) == null) {
            return;
        }
        if (storyItem.dialogId < 0 && MessagesController.getInstance(currentAccount).getChat(-storyItem.dialogId) == null) {
            return;
        }
        ArrayList<Long> peerIds = new ArrayList<>();
        peerIds.add(storyItem.dialogId);
        open(account, context, storyItem, peerIds, 0, null, null, placeProvider, false);
    }

    public void open(Context context, long dialogId, PlaceProvider placeProvider) {
        currentAccount = UserConfig.selectedAccount;
        int position = 0;
        ArrayList<Long> peerIds = new ArrayList<>();
        peerIds.add(dialogId);
        MessagesController.getInstance(currentAccount).getStoriesController().checkExpiredStories(dialogId);
        open(context, null, peerIds, position, null, null, placeProvider, false);
    }

    public void open(Context context, int startStoryId, StoriesController.StoriesList storiesList, PlaceProvider placeProvider) {
        currentAccount = UserConfig.selectedAccount;
        ArrayList<Long> peerIds = new ArrayList<>();
        peerIds.add(storiesList.dialogId);
        dayStoryId = startStoryId;
        open(context, null, peerIds, 0, storiesList, null, placeProvider, false);
    }

    public void open(Context context, TL_stories.PeerStories userStories, PlaceProvider placeProvider) {
        if (userStories == null || userStories.stories == null || userStories.stories.isEmpty()) {
            doOnAnimationReadyRunnables.clear();
            return;
        }
        currentAccount = UserConfig.selectedAccount;
        ArrayList<Long> peerIds = new ArrayList<>();
        peerIds.add(DialogObject.getPeerDialogId(userStories.peer));
        open(context, userStories.stories.get(0), peerIds, 0, null, userStories, placeProvider, false);
    }

    public void open(Context context, TL_stories.StoryItem storyItem, int startStoryId, StoriesController.StoriesList storiesList, boolean reversed, PlaceProvider placeProvider) {
        currentAccount = UserConfig.selectedAccount;
        ArrayList<Long> peerIds = new ArrayList<>();
        peerIds.add(storiesList.dialogId);
        dayStoryId = startStoryId;
        open(context, storyItem, peerIds, 0, storiesList, null, placeProvider, reversed);
    }

    @SuppressLint("WrongConstant")
    public void open(Context context, TL_stories.StoryItem storyItem, ArrayList<Long> peerIds, int position, StoriesController.StoriesList storiesList, TL_stories.PeerStories userStories, PlaceProvider placeProvider, boolean reversed) {
        open(UserConfig.selectedAccount, context, storyItem, peerIds, position, storiesList, userStories, placeProvider, reversed);
    }
    public void open(int account, Context context, TL_stories.StoryItem storyItem, ArrayList<Long> peerIds, int position, StoriesController.StoriesList storiesList, TL_stories.PeerStories userStories, PlaceProvider placeProvider, boolean reversed) {
        if (context == null) {
            doOnAnimationReadyRunnables.clear();
            return;
        }
        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }
        if (isShowing) {
            doOnAnimationReadyRunnables.clear();
            return;
        }
        setSpeed(1f);
        ATTACH_TO_FRAGMENT = !AndroidUtilities.isTablet() && !fromBottomSheet;
        USE_SURFACE_VIEW = SharedConfig.useSurfaceInStories && ATTACH_TO_FRAGMENT;
        messageId = storyItem == null ? 0 : storyItem.messageId;
        isSingleStory = storyItem != null && storiesList == null && userStories == null;
        singleStoryDeleted = false;
        if (storyItem != null) {
            singleStory = storyItem;
            lastStoryItem = storyItem;
        }
        this.storiesList = storiesList;
        overrideUserStories = userStories;
        this.placeProvider = placeProvider;
        this.reversed = reversed;
        currentAccount = account;
        swipeToDismissOffset = 0;
        swipeToDismissHorizontalOffset = 0;
        if (storiesViewPager != null) {
            storiesViewPager.setHorizontalProgressToDismiss(0);
            storiesViewPager.currentState = ViewPager.SCROLL_STATE_IDLE;
        }
        swipeToReplyProgress = 0;
        swipeToReplyOffset = 0;
        allowSwipeToReply = false;
        progressToDismiss = 0;
        isShowing = true;
        isLongpressed = false;
        isTranslating = false;
        savedPositions.clear();
        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;

        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        isClosed = false;
        unreadStateChanged = false;

        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (windowView == null) {
            gestureDetector = new GestureDetector(new GestureDetector.OnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) {
                    flingCalled = false;
                    if (findClickableView(windowView, e.getX(), e.getY(), false)) {
                        return false;
                    }
                    return true;
                }

                @Override
                public void onShowPress(@NonNull MotionEvent e) {

                }

                @Override
                public boolean onSingleTapUp(@NonNull MotionEvent e) {
                    if (selfStoriesViewsOffset != 0) {
                        return false;
                    }
                    if (allowIntercept) {
                        if (keyboardVisible || isCaption || isCaptionPartVisible || isHintVisible || isInTextSelectionMode) {
                            closeKeyboardOrEmoji();
                        } else {
                            final PeerStoriesView peerView = getCurrentPeerView();
                            if (peerView != null && peerView.currentStory.isLive()) {
                                return false;
                            }
                            switchByTap(e.getX() > containerView.getMeasuredWidth() * 0.33f);
                        }
                    }
                    return false;
                }

                @Override
                public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                    if (inSwipeToDissmissMode) {
                        if (allowSwipeToReply) {
                            swipeToReplyOffset += distanceY;
                            int maxOffset = dp(200);
                            if (swipeToReplyOffset > maxOffset && !swipeToReplyWaitingKeyboard) {
                                swipeToReplyWaitingKeyboard = true;
                                showKeyboard();
                                try {
                                    windowView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                } catch (Exception ignored) {}
                            }
                            swipeToReplyProgress = Utilities.clamp(swipeToReplyOffset / maxOffset, 1f, 0);
                            if (storiesViewPager.getCurrentPeerView() != null) {
                                storiesViewPager.getCurrentPeerView().invalidate();
                            }
                            if (swipeToReplyOffset < 0) {
                                swipeToReplyOffset = 0;
                                allowSwipeToReply = false;
                            } else {
                                return true;
                            }
                        }
                        if (allowSelfStoriesView) {
                            if (selfStoriesViewsOffset > selfStoryViewsView.maxSelfStoriesViewsOffset && distanceY > 0) {
                                selfStoriesViewsOffset += distanceY * 0.05f;
                            } else {
                                selfStoriesViewsOffset += distanceY;
                            }
                            Bulletin.hideVisible(windowView);
                            if (storiesViewPager.getCurrentPeerView() != null) {
                                storiesViewPager.getCurrentPeerView().invalidate();
                            }
                            containerView.invalidate();
                            if (selfStoriesViewsOffset < 0) {
                                selfStoriesViewsOffset = 0;
                                allowSelfStoriesView = false;
                            } else {
                                return true;
                            }
                        }
                        float k = 0.6f;
                        if (progressToDismiss > 0.8f && ((-distanceY > 0 && swipeToDismissOffset > 0) || (-distanceY < 0 && swipeToDismissOffset < 0))) {
                            k = 0.3f;
                        }
                        swipeToDismissOffset -= distanceY * k;
                        Bulletin.hideVisible(windowView);
                        updateProgressToDismiss();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onLongPress(@NonNull MotionEvent e) {

                }

                @Override
                public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                    if (swipeToReplyOffset != 0 && storiesIntro == null) {
                        if (velocityY < -1000 && !swipeToReplyWaitingKeyboard) {
                            swipeToReplyWaitingKeyboard = true;
                            try {
                                windowView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            } catch (Exception ignored) {}
                            showKeyboard();
                        }
                    }
                    if (selfStoriesViewsOffset != 0) {
                        if (velocityY < -1000) {
                            cancelSwipeToViews(true);
                        } else {
                            if (velocityY > 1000) {
                                cancelSwipeToViews(false);
                            } else {
                                cancelSwipeToViews(selfStoryViewsView.progressToOpen > 0.5f);
                            }
                        }
                    }
                    flingCalled = true;
                    return false;
                }
            });
            windowView = new SizeNotifierFrameLayout(context) {

                float startX, startY;
                float lastTouchX;

                final Path path = new Path();
                final RectF rect1 = new RectF();
                final RectF rect2 = new RectF();
                final RectF rect3 = new RectF();
                final RectF outFromRectAvatar = new RectF();
                final RectF outFromRectContainer = new RectF();

                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    if (child == aspectRatioFrameLayout) {
                        return false;
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }


                @Override
                protected void dispatchDraw(Canvas canvas) {
                    float blackoutAlpha = getBlackoutAlpha();
                    canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * blackoutAlpha)));

                    if (ATTACH_TO_FRAGMENT) {
                        boolean localFullyVisible = progressToOpen * (1f - progressToDismiss) == 1f;
                        if (fullyVisible != localFullyVisible) {
                            fullyVisible = localFullyVisible;
                            if (fragment.getLayoutContainer() != null) {
                                fragment.getLayoutContainer().invalidate();
                            }
                        }
                    }

                    PeerStoriesView currentView = storiesViewPager.getCurrentPeerView();
                    PeerStoriesView.PeerHeaderView headerView = null;
                    if (currentView != null) {
                        headerView = currentView.headerView;
                        if (animateAvatar) {
                            headerView.backupImageView.getImageReceiver().setVisible(progressToOpen == 1f, true);
                        } else {
                            headerView.backupImageView.getImageReceiver().setVisible(true, false);
                        }

                        if (invalidateOutRect) {
                            invalidateOutRect = false;
                            View child = headerView.backupImageView;
                            float toX = 0, toY = 0;
                            while (child != this) {
                                if (child.getParent() == this) {
                                    toX += child.getLeft();
                                    toY += child.getTop();
                                } else if (child.getParent() != storiesViewPager) {
                                    toX += child.getX();
                                    toY += child.getY();
                                }
                                child = (View) child.getParent();
                            }
                            outFromRectAvatar.set(toX, toY, toX + headerView.backupImageView.getMeasuredWidth(), toY + headerView.backupImageView.getMeasuredHeight());
                            outFromRectContainer.set(0, currentView.getTop() + currentView.storyContainer.getTop(), containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                            containerView.getMatrix().mapRect(outFromRectAvatar);
                            containerView.getMatrix().mapRect(outFromRectContainer);
                            //outFromRectContainer.offset(containerView.getX(), containerView.getY());
                          //  outFromRect.offset(-containerView.getTranslationX(), -containerView.getTranslationY());
                        }
                    }

                    volumeControl.setAlpha(1f - progressToDismiss);

                    float dismissScaleProgress = 1f;
                    if (swipeToDismissHorizontalOffset == 0) {
                        dismissScaleProgress = 1f - Utilities.clamp(Math.abs(swipeToDismissOffset / getMeasuredHeight()), 1f, 0);
                    }
                    storiesViewPager.setHorizontalProgressToDismiss((swipeToDismissHorizontalOffset / (float) containerView.getMeasuredWidth()) * progressToOpen);
                    if ((fromX == 0 && fromY == 0) || progressToOpen == 1f) {
                        containerView.setAlpha(progressToOpen);
                        float scale = 0.75f + 0.1f * progressToOpen + 0.15f * dismissScaleProgress;
                        containerView.setScaleX(scale);
                        containerView.setScaleY(scale);
                        containerView.setTranslationY(swipeToDismissOffset);
                        containerView.setTranslationX(swipeToDismissHorizontalOffset);
                        super.dispatchDraw(canvas);
                    } else {
                        float progress2 = progressToOpen;
                        float progressToCircle = progressToOpen;
                        if (isClosed && animateAvatar) {
                          //  progress2 = 1f - Utilities.clamp((1f - progressToOpen) / 0.8f, 1f, 0);
                            progress2 = progressToOpen;
                            progressToCircle = progressToOpen;//(float) Math.pow(progressToOpen, 1.4f);//progressToOpen;//1f - Utilities.clamp((1f - progressToOpen) / 0.85f, 1f, 0);
                            float startAlpha = 0.8f;
                            float endAlpha = startAlpha  + 0.1f;
                            float alpha = 1f - Utilities.clamp(((1f - progressToOpen) - startAlpha) / (endAlpha - startAlpha), 1f, 0f);
                            progressToCircle = Utilities.clamp(progressToCircle - 0.05f * (1f - alpha), 1f, 0);
                            containerView.setAlpha(alpha);
                        } else {
                            containerView.setAlpha(1f);
                        }
                        if (isClosed && transitionViewHolder != null && transitionViewHolder.storyImage != null) {
                            containerView.setAlpha(containerView.getAlpha() * (float) Math.pow(progress2, .2f));
                        } else if (isClosed) {
                           // containerView.setAlpha(Utilities.clamp(progressToOpen / 0.7f, 1f, 0));
                        }
                        containerView.setTranslationX((fromX - containerView.getLeft() - containerView.getMeasuredWidth() / 2f) * (1f - progressToOpen) + swipeToDismissHorizontalOffset * progressToOpen);
                        containerView.setTranslationY((fromY - containerView.getTop() - containerView.getMeasuredHeight() / 2f) * (1f - progressToOpen) + swipeToDismissOffset * progressToOpen);
                        float s1 = 0.85f + 0.15f * dismissScaleProgress;
                        float scale = AndroidUtilities.lerp(fromWidth / (float) containerView.getMeasuredWidth(), s1, progressToCircle);
                        containerView.setScaleX(scale);
                        containerView.setScaleY(scale);

                        path.rewind();
                        rect1.set(
                                fromX - fromWidth / 2f, fromY - fromHeight / 2f,
                                fromX + fromWidth / 2f, fromY + fromHeight / 2f
                        );
                        if (isClosed && animateAvatar) {
                            rect2.set(outFromRectContainer);
                        } else if (currentView != null) {
                            rect2.set(0, currentView.storyContainer.getTop() + fromDismissOffset, getMeasuredWidth(), getMeasuredHeight() + fromDismissOffset);
                        } else {
                            rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        }

                        if (isClosed && animateAvatar) {
                            rect1.inset(dp(12), dp(12));
                        }
                        float cx = AndroidUtilities.lerp(rect1.centerX(), rect2.centerX(), progressToOpen);
                        float cy = AndroidUtilities.lerp(rect1.centerY(), rect2.centerY(), progressToOpen);
                        float rectHeight = AndroidUtilities.lerp(rect1.height(), rect2.height(), progressToCircle);
                        float rectWidth = AndroidUtilities.lerp(rect1.width(), rect2.width(), progressToCircle);
                        if (isClosed && animateAvatar) {
                            rect1.inset(-dp(12), -dp(12));
                        }

                        AndroidUtilities.rectTmp.set(cx - rectWidth / 2f, cy - rectHeight / 2f, cx + rectWidth / 2f, cy + rectHeight / 2f);
                        float rad;
                        if (animateAvatar) {
                            rad = AndroidUtilities.lerp(fromWidth / 2f, 0, progressToCircle);
                        } else {
                            rad = AndroidUtilities.lerp((float) fromRadius, 0, progress2);
                        }
                        path.addRoundRect(
                                AndroidUtilities.rectTmp,
                                rad, rad,
                                Path.Direction.CCW
                        );
                        canvas.save();
                        if (clipTop != 0 && clipBottom != 0) {
                            canvas.clipRect(
                                    0,
                                    AndroidUtilities.lerp(0, clipTop, (float) Math.pow(1f - progressToOpen, .4f)),
                                    getMeasuredWidth(),
                                    AndroidUtilities.lerp(getMeasuredHeight(), clipBottom, (1f - progressToOpen))
                            );
                        }

                        if (transitionViewHolder != null && transitionViewHolder.params != null && transitionViewHolder.params.drawnLive && animateAvatar) {
                            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * progress2), Canvas.ALL_SAVE_FLAG);
                        } else {
                            canvas.save();
                        }
                        canvas.clipPath(path);
                        super.dispatchDraw(canvas);
                        if (transitionViewHolder != null && transitionViewHolder.storyImage != null) {
                            PeerStoriesView page = storiesViewPager.getCurrentPeerView();
                            if (page != null && page.storyContainer != null) {
                                boolean wasVisible = transitionViewHolder.storyImage.getVisible();
                                rect2.set(
                                        swipeToDismissHorizontalOffset + containerView.getLeft() + page.getX() + page.storyContainer.getX(),
                                        swipeToDismissOffset + containerView.getTop() + page.getY() + page.storyContainer.getY(),
                                        swipeToDismissHorizontalOffset + containerView.getRight() - (containerView.getWidth() - page.getRight()) - (page.getWidth() - page.storyContainer.getRight()),
                                        swipeToDismissOffset + containerView.getBottom() - (containerView.getHeight() - page.getBottom()) - (page.getHeight() - page.storyContainer.getBottom())
                                );
                                AndroidUtilities.lerp(rect1, rect2, progress2, rect3);
                                float x = transitionViewHolder.storyImage.getImageX();
                                float y = transitionViewHolder.storyImage.getImageY();
                                float w = transitionViewHolder.storyImage.getImageWidth();
                                float h = transitionViewHolder.storyImage.getImageHeight();
                                transitionViewHolder.storyImage.setImageCoords(rect3);
                                transitionViewHolder.storyImage.setAlpha(1f - progress2);
                                transitionViewHolder.storyImage.setVisible(true, false);
                                int r = canvas.getSaveCount();
                                if (transitionViewHolder.drawClip != null) {
                                    transitionViewHolder.drawClip.clip(canvas, rect3, 1f - progress2, opening);
                                }
                                transitionViewHolder.storyImage.draw(canvas);
                                if (transitionViewHolder.drawAbove != null) {
                                    transitionViewHolder.drawAbove.draw(canvas, rect3, 1f - progress2, opening);
                                }
                                transitionViewHolder.storyImage.setVisible(wasVisible, false);
                                transitionViewHolder.storyImage.setImageCoords(x, y, w, h);
                                canvas.restoreToCount(r);
                            }
                        }
                        canvas.restore();

                        if (headerView != null) {
                            float toX = swipeToDismissHorizontalOffset, toY = swipeToDismissOffset;
                            View child = headerView.backupImageView;
                            if (isClosed && animateAvatar) {
                                rect2.set(outFromRectAvatar);
                            } else {
                                while (child != this && child != null) {
                                    if (child.getParent() == this) {
                                        toX += child.getLeft();
                                        toY += child.getTop();
                                    } else if (child.getParent() != storiesViewPager) {
                                        toX += child.getX();
                                        toY += child.getY();
                                    }
                                    if (!(child.getParent() instanceof View)) {
                                        break;
                                    }
                                    child = (View) child.getParent();
                                }
                                rect2.set(toX, toY, toX + headerView.backupImageView.getMeasuredWidth(), toY + headerView.backupImageView.getMeasuredHeight());
                            }

                            AndroidUtilities.lerp(rect1, rect2, progressToOpen, rect3);

                            int r = canvas.getSaveCount();
                            if (transitionViewHolder != null && transitionViewHolder.drawClip != null) {
                                transitionViewHolder.drawClip.clip(canvas, rect3, 1f - progress2, opening);
                            }
                            if (animateAvatar) {
                                boolean crossfade = transitionViewHolder != null && transitionViewHolder.crossfadeToAvatarImage != null;
                                if (!crossfade || progressToOpen != 0) {
                                    if (transitionViewHolder != null && transitionViewHolder.params != null && transitionViewHolder.params.drawnLive) {
                                        canvas.saveLayerAlpha(rect3.left - dp(4), rect3.top - dp(4), rect3.right + dp(4), rect3.bottom + dp(4), 0xFF, Canvas.ALL_SAVE_FLAG);
                                    }
                                    headerView.backupImageView.getImageReceiver().setImageCoords(rect3);

                                    Integer cellAvatarImageRadius = transitionViewHolder != null ? transitionViewHolder.getAvatarImageRoundRadius() : null;
                                    int newRoundRadius = (int) (AndroidUtilities.lerp(rect3.width() / 2f, cellAvatarImageRadius != null ? cellAvatarImageRadius : rect3.width() / 2f, 1f - progressToOpen));

                                    headerView.backupImageView.getImageReceiver().setRoundRadius(newRoundRadius);
                                    headerView.backupImageView.getImageReceiver().setVisible(true, false);
                                    final float alpha = crossfade ? progressToOpen : 1f;
                                    float thisAlpha = alpha;
                                    if (transitionViewHolder != null && transitionViewHolder.alpha < 1 && transitionViewHolder.bgPaint != null) {
                                        transitionViewHolder.bgPaint.setAlpha((int) (0xFF * (1f - progress2)));
                                        canvas.drawCircle(rect3.centerX(), rect3.centerY(), rect3.width() / 2f, transitionViewHolder.bgPaint);
                                        thisAlpha = AndroidUtilities.lerp(transitionViewHolder.alpha, thisAlpha, progress2);
                                    }
                                    headerView.backupImageView.getImageReceiver().setAlpha(thisAlpha);
                                    headerView.drawUploadingProgress(canvas, rect3, !runOpenAnimationAfterLayout, progressToOpen);
                                    headerView.backupImageView.getImageReceiver().draw(canvas);
                                    headerView.backupImageView.getImageReceiver().setAlpha(alpha);
                                    headerView.backupImageView.getImageReceiver().setVisible(false, false);
                                    if (transitionViewHolder != null && transitionViewHolder.params != null && transitionViewHolder.params.drawnLive) {
                                        AndroidUtilities.rectTmp.set(rect3);
//                                        AndroidUtilities.scaleRect(AndroidUtilities.rectTmp, transitionViewHolder.params.getScale());
                                        StoriesUtilities.drawLive(canvas, AndroidUtilities.rectTmp, 1.0f - progressToOpen, true, 0);
                                        canvas.restore();
                                    }
                                }
                                if (progressToOpen != 1f && crossfade) {
                                    avatarRectTmp.set(
                                            transitionViewHolder.crossfadeToAvatarImage.getImageX(),
                                            transitionViewHolder.crossfadeToAvatarImage.getImageY(),
                                            transitionViewHolder.crossfadeToAvatarImage.getImageX2(),
                                            transitionViewHolder.crossfadeToAvatarImage.getImageY2()
                                    );
                                    int oldRadius = transitionViewHolder.crossfadeToAvatarImage.getRoundRadius()[0];
                                    boolean isVisible = transitionViewHolder.crossfadeToAvatarImage.getVisible();
                                    transitionViewHolder.crossfadeToAvatarImage.setImageCoords(rect3);
                                    transitionViewHolder.crossfadeToAvatarImage.setRoundRadius((int) (rect3.width() / 2f));
                                    transitionViewHolder.crossfadeToAvatarImage.setVisible(true, false);
                                    canvas.saveLayerAlpha(rect3, (int) (255 * (1f - progressToOpen)), Canvas.ALL_SAVE_FLAG);
                                    transitionViewHolder.crossfadeToAvatarImage.draw(canvas);
                                    canvas.restore();
                                    transitionViewHolder.crossfadeToAvatarImage.setVisible(isVisible, false);
                                    transitionViewHolder.crossfadeToAvatarImage.setImageCoords(avatarRectTmp);
                                    transitionViewHolder.crossfadeToAvatarImage.setRoundRadius(oldRadius);
                                   // transitionViewHolder.crossfadeToAvatarImage.setVisible(false, false);
                                }

                                if (transitionViewHolder != null && transitionViewHolder.drawAbove != null) {
                                    transitionViewHolder.drawAbove.draw(canvas, rect3, 1f - progress2, opening);
                                }
                            }
                            canvas.restoreToCount(r);
                        }


                        if (animateFromCell != null) {
                            float progressHalf = Utilities.clamp(progressToOpen / 0.4f, 1f, 0);
                            if (progressHalf != 1) {
                                AndroidUtilities.rectTmp.set(fromX, fromY, fromX + fromWidth, fromY + fromHeight);
                                AndroidUtilities.rectTmp.inset(-dp(16), -dp(16));
                                if (progressHalf != 0) {
                                    canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (255 * (1f - progressHalf)), Canvas.ALL_SAVE_FLAG);
                                } else {
                                    canvas.save();
                                }
                                canvas.translate(fromXCell, fromYCell);
                                animateFromCell.drawAvatarOverlays(canvas);
                                canvas.restore();
                            }
                        }
                        canvas.restore();
                    }

                    if (runOpenAnimationAfterLayout) {
                        startOpenAnimation();
                        runOpenAnimationAfterLayout = false;
                    }
                }

                @Override
                public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                    super.requestDisallowInterceptTouchEvent(disallowIntercept);
                    allowIntercept = false;
                }

                SparseArray<Float> lastX = new SparseArray<>();

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    boolean swipeToReplyCancelled = false;
                    PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
                    if (peerStoriesView != null && peerStoriesView.checkTextSelectionEvent(ev)) {
                        return true;
                    }
                    if (isLikesReactions) {
                        if (peerStoriesView != null && peerStoriesView.checkReactionEvent(ev)) {
                            return true;
                        }
                    }
                    if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        inSwipeToDissmissMode = false;
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                        if (swipeToDismissHorizontalOffset != 0) {
                            swipeToDissmissBackAnimator = ValueAnimator.ofFloat(swipeToDismissHorizontalOffset, 0);
                            swipeToDissmissBackAnimator.addUpdateListener(animation -> {
                                swipeToDismissHorizontalOffset = (float) animation.getAnimatedValue();
                                updateProgressToDismiss();
                            });
                            swipeToDissmissBackAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    swipeToDismissHorizontalOffset = 0;
                                    updateProgressToDismiss();
                                }
                            });
                            swipeToDissmissBackAnimator.setDuration(250);
                            swipeToDissmissBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                            swipeToDissmissBackAnimator.start();
                        }
                        swipeToReplyCancelled = true;
                        if (progressToDismiss >= 0.3f) {
                            close(true);
                        }
                        setInTouchMode(false);
                        setLongPressed(false);
                    }
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        swipeToReplyWaitingKeyboard = false;
                        if (peerStoriesView != null) {
                            peerStoriesView.onActionDown(ev);
                        }
                        storiesViewPager.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
                    }
                    boolean override = false;
                    boolean enableTouch = !keyboardVisible && !isClosed && !isRecording;
                    if (selfStoriesViewsOffset == 0 && !inSwipeToDissmissMode && storiesViewPager.currentState == ViewPager.SCROLL_STATE_DRAGGING && ev.getAction() == MotionEvent.ACTION_MOVE && enableTouch) {
                        float dx = lastX.get(ev.getPointerId(0), 0f) - ev.getX(0);
                        if (dx != 0 && !storiesViewPager.canScroll(dx) || swipeToDismissHorizontalOffset != 0) {
                            if (swipeToDismissHorizontalOffset == 0) {
                                swipeToDismissHorizontalDirection = -dx;
                            }
                            if (dx < 0 && swipeToDismissHorizontalDirection > 0 || dx > 0 && swipeToDismissHorizontalDirection < 0) {
                                dx *= 0.2f;
                            }
                            swipeToDismissHorizontalOffset -= dx;
                            updateProgressToDismiss();
                            if (swipeToDismissHorizontalOffset > 0 && swipeToDismissHorizontalDirection < 0 || swipeToDismissHorizontalOffset < 0 && swipeToDismissHorizontalDirection > 0) {
                                swipeToDismissHorizontalOffset = 0;
                            }
                            override = true;
                        }
                    }
                    if (peerStoriesView != null && selfStoriesViewsOffset == 0 && !inSwipeToDissmissMode && !isCaption && !isRecording && storiesViewPager.currentState != ViewPager.SCROLL_STATE_DRAGGING) {
                        AndroidUtilities.getViewPositionInParent(peerStoriesView.storyContainer, this, pointPosition);
                        ev.offsetLocation(-pointPosition[0], -pointPosition[1]);
                        storiesViewPager.getCurrentPeerView().checkPinchToZoom(ev);
                        ev.offsetLocation(pointPosition[0], pointPosition[1]);
                    }
                    if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        lastX.clear();
                    } else {
                        for (int i = 0; i < ev.getPointerCount(); i++) {
                            lastX.put(ev.getPointerId(i), ev.getX(i));
                        }
                    }
                    if (override) {
                        return true;
                    }
                    boolean rezult = super.dispatchTouchEvent(ev);
                    if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (selfStoriesViewsOffset != 0 && !flingCalled && realKeyboardHeight < dp(20)) {
                            cancelSwipeToViews(selfStoryViewsView.progressToOpen > 0.5f);
                        }
                        PeerStoriesView peerView = getCurrentPeerView();
                        if (peerView != null) {
                            peerView.cancelTouch();
                        }
                    }
                    if (swipeToReplyCancelled && !swipeToReplyWaitingKeyboard) {
                        cancelSwipeToReply();
                    }
                    return rezult || animationInProgress && isInTouchMode;
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN && progressToOpen == 1f) {
                        startX = lastTouchX = ev.getX();
                        startY = ev.getY();
                        verticalScrollDetected = false;
                        allowIntercept = !isRecording && !findClickableView(windowView, ev.getX(), ev.getY(), false);
                        allowSwipeToDissmiss = !isRecording && !findClickableView(windowView, ev.getX(), ev.getY(), true);
                        setInTouchMode(allowIntercept && !isCaptionPartVisible);
                        final PeerStoriesView peerView = getCurrentPeerView();
                        if (allowIntercept && peerView != null && peerView.liveCommentsView != null) {
                            peerView.liveCommentsView.setAllowTouches(false);
                        }
                        if (allowIntercept && !isRecording && isCaptionPartVisible) {
                            delayedTapRunnable = () -> setInTouchMode(true);
                            AndroidUtilities.runOnUIThread(delayedTapRunnable, 150);
                        }
                        if (allowIntercept && !keyboardVisible && !isRecording && !isInTextSelectionMode) {
                            AndroidUtilities.runOnUIThread(longPressRunnable, 400);
                        }
                    } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                        float dy = Math.abs(startY - ev.getY());
                        float dx = Math.abs(startX - ev.getX());
                        if (isLongpressed && inSeekingMode && !isInPinchToZoom && !inSwipeToDissmissMode && currentPlayerScope != null && currentPlayerScope.player != null) {
                            PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
                            if (peerView != null && peerView.currentStory != null && peerView.currentStory.uploadingStory == null && peerView.currentStory.isVideo()) {
                                long videoDuration = peerView.videoDuration;
                                if (videoDuration <= 0 && peerView.currentStory.storyItem != null && peerView.currentStory.storyItem.media != null && peerView.currentStory.storyItem.media.document != null) {
                                    videoDuration = (long) (MessageObject.getDocumentDuration(peerView.currentStory.storyItem.media.document) * 1000L);
                                }
                                if (videoDuration > 0) {
                                    final float x = ev.getX();
                                    final float wasSeek = currentPlayerScope.player.currentSeek;
                                    final float nowSeek = currentPlayerScope.player.seek((x - lastTouchX) / dp(220), videoDuration);
                                    if ((int) (nowSeek * 10) != (int) (wasSeek * 10)) {
                                        try {
                                            peerView.performHapticFeedback(9, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                                        } catch (Exception ignore) {}
                                    }
                                    peerView.storyContainer.invalidate();
                                    lastTouchX = x;
                                }
                            }
                        }
                        if (dy > dx && !inSeekingMode && !verticalScrollDetected && dy > AndroidUtilities.touchSlop * 2) {
                            verticalScrollDetected = true;
                        }
                        if (!inSwipeToDissmissMode && !inSeekingMode && !keyboardVisible && allowSwipeToDissmiss) {
                            if (dy > dx && dy > AndroidUtilities.touchSlop * 2) {
                                inSwipeToDissmissMode = true;
                                PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
                                if (peerView != null) {
                                    peerView.cancelTextSelection();
                                }
                                boolean viewsAllowed = peerView != null && peerView.viewsAllowed();
                                allowSwipeToReply = !viewsAllowed && peerView != null && !peerView.isChannel && !peerView.isPremiumBlocked && storiesIntro == null;
                                allowSelfStoriesView = viewsAllowed && !peerView.unsupported && peerView.currentStory.storyItem != null && storiesIntro == null;
                                if (allowSelfStoriesView && keyboardHeight != 0) {
                                    allowSelfStoriesView = false;
                                }
                                if (allowSelfStoriesView) {
                                    checkSelfStoriesView();
                                }
                                swipeToReplyOffset = 0;
                                if (delayedTapRunnable != null) {
                                    AndroidUtilities.cancelRunOnUIThread(delayedTapRunnable);
                                    delayedTapRunnable.run();
                                    delayedTapRunnable = null;
                                }
                                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                            }
                            layoutAndFindView();
                        }
                    } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                        if (delayedTapRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(delayedTapRunnable);
                            delayedTapRunnable = null;
                        }
                        setInTouchMode(false);
                        verticalScrollDetected = false;
                        inSeekingMode = false;
                        if (currentPlayerScope != null && currentPlayerScope.player != null) {
                            currentPlayerScope.player.setSeeking(false);
                        }
                        final PeerStoriesView peerView = getCurrentPeerView();
                        if (peerView != null && peerView.liveCommentsView != null) {
                            peerView.liveCommentsView.setAllowTouches(true);
                        }
                    }
                    boolean selfViewsViewVisible = selfStoryViewsView != null && selfStoryViewsView.progressToOpen == 1f;
                    if (!inSwipeToDissmissMode && !selfViewsViewVisible) {
                        gestureDetector.onTouchEvent(ev);
                    }
                    return inSwipeToDissmissMode || super.onInterceptTouchEvent(ev);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        inSwipeToDissmissMode = false;
                        setInTouchMode(false);
                        if (progressToDismiss >= 1f) {
                            close(true);
                        } else if (!isClosed) {
                            swipeToDissmissBackAnimator = ValueAnimator.ofFloat(swipeToDismissOffset, 0);
                            swipeToDissmissBackAnimator.addUpdateListener(animation -> {
                                swipeToDismissOffset = (float) animation.getAnimatedValue();
                                updateProgressToDismiss();
                            });
                            swipeToDissmissBackAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    swipeToDismissOffset = 0;
                                    swipeToReplyOffset = 0;
                                    updateProgressToDismiss();
                                }
                            });
                            swipeToDissmissBackAnimator.setDuration(150);
                            swipeToDissmissBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                            swipeToDissmissBackAnimator.start();
                        }
                        final PeerStoriesView peerView = getCurrentPeerView();
                        if (peerView != null && peerView.liveCommentsView != null) {
                            peerView.liveCommentsView.setAllowTouches(true);
                        }
                    }
                    if (inSwipeToDissmissMode || keyboardVisible || swipeToReplyOffset != 0 || (selfStoriesViewsOffset != 0 && (allowIntercept || verticalScrollDetected)) || isInTextSelectionMode) {
                        gestureDetector.onTouchEvent(event);
                        return true;
                    } else {
                        return false;
                    }
                }

                @Override
                public boolean dispatchKeyEventPreIme(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        dispatchVolumeEvent(event);
                        return true;
                    }

                    if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                        onAttachedBackPressed();
                        return true;
                    }
                    return super.dispatchKeyEventPreIme(event);
                }

                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    if (ATTACH_TO_FRAGMENT && !ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE) {
                        AndroidUtilities.requestAdjustResize(fragment.getParentActivity(), fragment.getClassGuid());
                    }
                    Bulletin.addDelegate(this, new Bulletin.Delegate() {

                        float[] position = new float[2];
                        @Override
                        public int getBottomOffset(int tag) {
                            PeerStoriesView child = getCurrentPeerView();
                            if (child == null) {
                                return 0;
                            }
                            AndroidUtilities.getViewPositionInParent(child.storyContainer, windowView, position);
                            return (int) (getMeasuredHeight() - (position[1] + child.storyContainer.getMeasuredHeight()));
                        }
                    });
                    NotificationCenter.getInstance(currentAccount).addObserver(StoryViewer.this, NotificationCenter.storiesListUpdated);
                    NotificationCenter.getInstance(currentAccount).addObserver(StoryViewer.this, NotificationCenter.storiesUpdated);
                    NotificationCenter.getInstance(currentAccount).addObserver(StoryViewer.this, NotificationCenter.articleClosed);
                    NotificationCenter.getInstance(currentAccount).addObserver(StoryViewer.this, NotificationCenter.openArticle);
                    NotificationCenter.getInstance(currentAccount).addObserver(StoryViewer.this, NotificationCenter.storyDeleted);
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    Bulletin.removeDelegate(this);
                    NotificationCenter.getInstance(currentAccount).removeObserver(StoryViewer.this, NotificationCenter.storiesListUpdated);
                    NotificationCenter.getInstance(currentAccount).removeObserver(StoryViewer.this, NotificationCenter.storiesUpdated);
                    NotificationCenter.getInstance(currentAccount).removeObserver(StoryViewer.this, NotificationCenter.articleClosed);
                    NotificationCenter.getInstance(currentAccount).removeObserver(StoryViewer.this, NotificationCenter.openArticle);
                    NotificationCenter.getInstance(currentAccount).removeObserver(StoryViewer.this, NotificationCenter.storyDeleted);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    ((LayoutParams) volumeControl.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight - dp(2);
                    volumeControl.getLayoutParams().height = dp(2);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
        }

        if (containerView == null) {
            containerView = new HwFrameLayout(context) {

                public int measureKeyboardHeight() {
                    View rootView = getRootView();
                    getWindowVisibleDisplayFrame(AndroidUtilities.rectTmp2);
                    if (AndroidUtilities.rectTmp2.bottom == 0 && AndroidUtilities.rectTmp2.top == 0) {
                        return 0;
                    }
                    int usableViewHeight = rootView.getHeight() - (AndroidUtilities.rectTmp2.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);
                    return Math.max(0, usableViewHeight - (AndroidUtilities.rectTmp2.bottom - AndroidUtilities.rectTmp2.top));
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int heightWithKeyboard = MeasureSpec.getSize(heightMeasureSpec);
                    if (!ATTACH_TO_FRAGMENT || ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE) {
                        setKeyboardHeightFromParent(measureKeyboardHeight());
                        heightWithKeyboard += realKeyboardHeight;
                    }
                    int width = MeasureSpec.getSize(widthMeasureSpec);
                    int viewPagerHeight;
                    if (heightWithKeyboard > (int) (16f * width / 9f)) {
                        viewPagerHeight = (int) (16f * width / 9f);
                        storiesViewPager.getLayoutParams().width = LayoutParams.MATCH_PARENT;
                    } else {
                        viewPagerHeight = heightWithKeyboard;
                        width = storiesViewPager.getLayoutParams().width = (int) (heightWithKeyboard / 16f * 9f);
                    }
                    aspectRatioFrameLayout.getLayoutParams().height = viewPagerHeight + 1;
                    aspectRatioFrameLayout.getLayoutParams().width = width;
                    FrameLayout.LayoutParams layoutParams = (LayoutParams) aspectRatioFrameLayout.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.statusBarHeight;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
                    float pivotY = 0;
                    if (selfStoryViewsView != null && peerStoriesView != null) {
                        selfStoryViewsView.setOffset(selfStoriesViewsOffset);
                        if (selfStoryViewsView.progressToOpen == 1f) {
                            storiesViewPager.setVisibility(View.INVISIBLE);
                        } else {
                            storiesViewPager.setVisibility(View.VISIBLE);
                        }
                        storiesViewPager.checkPageVisibility();

                        pivotY = peerStoriesView.getTop() + peerStoriesView.storyContainer.getTop();

                        float progressHalf = selfStoryViewsView.progressToOpen;//Utilities.clamp((selfStoryViewsView.progressToOpen - 0.8f) / 0.2f, 1f, 0);
                        float fromScale = (getMeasuredHeight() - selfStoriesViewsOffset) / getMeasuredHeight();
                        if (peerStoriesView.storyContainer.getMeasuredHeight() > 0) {
                            lastStoryContainerHeight = peerStoriesView.storyContainer.getMeasuredHeight();
                        }
                        float toScale = selfStoryViewsView.toHeight / lastStoryContainerHeight;
                        float s = AndroidUtilities.lerp(1f, toScale, progressHalf);
                        storiesViewPager.setPivotY(pivotY);
                        storiesViewPager.setPivotX(getMeasuredWidth() / 2f);
                        storiesViewPager.setScaleX(s);
                        storiesViewPager.setScaleY(s);
                        peerStoriesView.forceUpdateOffsets = true;

                        if (selfStoriesViewsOffset == 0) {
                            peerStoriesView.setViewsThumbImageReceiver(0, 0, 0, null);
                        } else {
                            peerStoriesView.setViewsThumbImageReceiver(progressHalf, s, pivotY, selfStoryViewsView.getCrossfadeToImage());
                        }
                        peerStoriesView.invalidate();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            peerStoriesView.outlineProvider.radiusInDp = (int) AndroidUtilities.lerp(10f, 6f / toScale, selfStoryViewsView.progressToOpen);
                            peerStoriesView.storyContainer.invalidateOutline();
                        }
                        storiesViewPager.setTranslationY((selfStoryViewsView.toY - pivotY) * progressHalf);

                    }
                    if (peerStoriesView != null) {
                        volumeControl.setTranslationY(peerStoriesView.getY() + peerStoriesView.storyContainer.getY() - volumeControl.getTop() - dp(4));
                    }
                    super.dispatchDraw(canvas);
                  // canvas.drawRect(0, pivotY, getMeasuredWidth(), pivotY + 1, Theme.DEBUG_RED);
                }
            };
            storiesViewPager = new HwStoriesViewPager(currentAccount, context, this, resourcesProvider) {
                @Override
                public void onStateChanged() {
                    if (storiesViewPager.currentState == ViewPager.SCROLL_STATE_DRAGGING) {
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    }
                }
            };
            storiesViewPager.setDelegate(new PeerStoriesView.Delegate() {

                @Override
                public void onPeerSelected(long dialogId, int position) {
                    if (lastPosition != position || lastDialogId != dialogId) {
                        lastDialogId = dialogId;
                        lastPosition = position;
                    }
                }

                @Override
                public void onCloseClick() {
                    close(true);
                }

                @Override
                public void onCloseLongClick() {

                }


                @Override
                public void onEnterViewClick() {

                }

                @Override
                public void shouldSwitchToNext() {
                    PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
                    if (!peerView.switchToNext(true)) {
                        if (!storiesViewPager.switchToNext(true)) {
                            close(true);
                        }
                    }
                }

                @Override
                public void switchToNextAndRemoveCurrentPeer() {
                    if (storiesList != null) {
                        if (storiesViewPager.days == null) {
                            return;
                        }
                        ArrayList<ArrayList<Integer>> newDays = new ArrayList<>(storiesViewPager.days);
                        int index = storiesViewPager.getCurrentPeerView() == null ? -1 : newDays.indexOf(storiesViewPager.getCurrentPeerView().getCurrentDay());
                        if (index >= 0) {
                            newDays.remove(index);
                        } else {
                            close(false);
                            return;
                        }
                        if (!storiesViewPager.switchToNext(true)) {
                            close(false);
                        } else {
                            storiesViewPager.onNextIdle(() -> {
                                storiesViewPager.setDays(storiesList.dialogId, newDays, currentAccount);
                            });
                        }
                    } else {
                        ArrayList<Long> newPeers = new ArrayList<>(peerIds);
                        int index = newPeers.indexOf(storiesViewPager.getCurrentPeerView().getCurrentPeer());
                        if (index >= 0) {
                            newPeers.remove(index);
                        } else {
                            close(false);
                            return;
                        }
                        if (!storiesViewPager.switchToNext(true)) {
                            close(false);
                        } else {
                            storiesViewPager.onNextIdle(() -> {
                                storiesViewPager.setPeerIds(newPeers, currentAccount, index);
                            });
                        }
                    }
                }

                @Override
                public void setHideEnterViewProgress(float v) {
                    if (hideEnterViewProgress != v) {
                        hideEnterViewProgress = v;
                        containerView.invalidate();
                    }
                }

                @Override
                public void showDialog(Dialog dialog) {
                    StoryViewer.this.showDialog(dialog);
                }

                @Override
                public void updatePeers() {
                    //storiesViewPager.setPeerIds();
                }

                @Override
                public boolean releasePlayer(Runnable whenReleased) {
                    if (playerHolder != null) {
                        final boolean r = playerHolder.release(whenReleased);
                        playerHolder = null;
                        return r;
                    } else {
                        return false;
                    }
                }

                @Override
                public void requestAdjust(boolean b) {
                    StoryViewer.this.requestAdjust(b);
                }

                @Override
                public void setKeyboardVisible(boolean keyboardVisible) {
                    if (StoryViewer.this.keyboardVisible != keyboardVisible) {
                        StoryViewer.this.keyboardVisible = keyboardVisible;
                        updatePlayingMode();
                    }
                }

                @Override
                public void setAllowTouchesByViewPager(boolean b) {
                    StoryViewer.this.allowTouchesByViewpager = allowTouchesByViewpager;
                    updatePlayingMode();
                }

                @Override
                public void requestPlayer(TL_stories.StoryItem storyItem, long dialogId, int storyId, boolean rtmp_stream, TLRPC.InputGroupCall call, PeerStoriesView.VideoPlayerSharedScope scope) {
                    switchToLive(true, true);
//                    call = new TLRPC.TL_inputGroupCall();
//                    call.access_hash = -1333869356067995675L;
//                    call.id = -226646545204766746L;
                    if (livePlayer == null || livePlayer.dialogId != dialogId || !livePlayer.equals(call)) {
                        if (liveView != null) {
                            liveView.setScope(dialogId, null);
                            liveView.reset();
                        }
                        if (LiveStoryPipOverlay.isVisible() && LiveStoryPipOverlay.getLivePlayer() != null && LiveStoryPipOverlay.getLivePlayer().equals(call)) {
                            livePlayer = LiveStoryPipOverlay.takeLivePlayer();
                            LiveStoryPipOverlay.dismiss(false);
                        } else if (livePlayer != null) {
                            if (livePlayer.outgoing || LiveStoryPipOverlay.isVisible(livePlayer)) {
                                if (livePlayer.getDisplaySink() == liveView.getSink())
                                    return;
                                livePlayer.setDisplaySink(null);
                            } else {
                                livePlayer.destroy();
                            }
                            livePlayer = null;
                        }
                        if (LiveStoryPipOverlay.isVisible()) {
                            LiveStoryPipOverlay.dismiss();
                        }
                        if (playerHolder != null) {
                            playerHolder.release(null);
                            playerHolder = null;
                        }
                        if (currentPlayerScope != null) {
                            currentPlayerScope.player = null;
                            currentPlayerScope.livePlayer = null;
                            currentPlayerScope.firstFrameRendered = false;
                            currentPlayerScope.renderView = null;
                            currentPlayerScope.textureView = null;
                            currentPlayerScope.surfaceView = null;
                            currentPlayerScope.invalidate();
                            currentPlayerScope = null;
                        }

                        if (livePlayer == null) {
                            if (LivePlayer.recording != null && LivePlayer.recording.equals(call)) {
                                livePlayer = LivePlayer.recording;
                            } else {
                                livePlayer = new LivePlayer(context, currentAccount, storyItem, dialogId, storyId, rtmp_stream, call);
                            }
                        }
                        if (pipLiveView != null) {
                            livePlayer.setDisplaySink(pipLiveView.getSink());
                        } else {
                            livePlayer.setDisplaySink(liveView.getSink());
                        }
                        currentPlayerScope = scope;
                        currentPlayerScope.firstFrameRendered = false;
                        currentPlayerScope.renderView = aspectRatioFrameLayout;
                        currentPlayerScope.textureView = liveView.textureView;
                        currentPlayerScope.surfaceView = liveView.surfaceView;
                        currentPlayerScope.livePlayer = livePlayer;
                        liveView.setScope(dialogId, currentPlayerScope);
                        currentPlayerScope.invalidate();
                    }
                }

                @Override
                public void requestPlayer(TLRPC.Document document, Uri uri, long t, PeerStoriesView.VideoPlayerSharedScope scope) {
                    if (isClosed || progressToOpen < .9f) {
                        if (liveView != null) {
                            liveView.setScope(0, null);
                        }
                        if (livePlayer != null) {
                            if (livePlayer.outgoing) {
                                livePlayer.setDisplaySink(null);
                            } else {
                                livePlayer.destroy();
                            }
                            livePlayer = null;
                        }
                        FileLog.d("StoryViewer requestPlayer ignored, because closed: " + isClosed + ", " + progressToOpen);
                        scope.firstFrameRendered = false;
                        scope.player = null;
                        scope.livePlayer = null;
                        return;
                    }
                    boolean sameUri = TextUtils.equals(lastUri == null ? null : lastUri.toString(), uri == null ? null : uri.toString());
                    if (!sameUri || playerHolder == null) {
                        lastUri = uri;
                        if (liveView != null) {
                            liveView.setScope(0, null);
                        }
                        if (livePlayer != null) {
                            if (livePlayer.outgoing) {
                                livePlayer.setDisplaySink(null);
                            } else {
                                livePlayer.destroy();
                            }
                            livePlayer = null;
                        }
                        if (playerHolder != null) {
                            playerHolder.release(null);
                            playerHolder = null;
                        }
                        if (currentPlayerScope != null) {
                            currentPlayerScope.player = null;
                            currentPlayerScope.livePlayer = null;
                            currentPlayerScope.firstFrameRendered = false;
                            currentPlayerScope.renderView = null;
                            currentPlayerScope.textureView = null;
                            currentPlayerScope.surfaceView = null;
                            currentPlayerScope.invalidate();
                            currentPlayerScope = null;
                        }
                        if (uri != null) {
                            currentPlayerScope = scope;
                            for (int i = 0; i < preparedPlayers.size(); i++) {
                                if (preparedPlayers.get(i).uri.equals(uri)) {
                                    playerHolder = preparedPlayers.remove(i);
                                    break;
                                }
                            }
                            if (playerHolder == null) {
                                playerHolder = new VideoPlayerHolder(surfaceView, textureView);
                                playerHolder.document = document;
                            }
//                            if (surfaceView != null) {
//                                AndroidUtilities.removeFromParent(surfaceView);
//                                aspectRatioFrameLayout.addView(surfaceView = new SurfaceView(context));
//                            }
                            playerHolder.uri = uri;
                            playerHolder.setSpeed(currentSpeed);
                            currentPlayerScope.player = playerHolder;
                            currentPlayerScope.firstFrameRendered = false;
                            currentPlayerScope.renderView = aspectRatioFrameLayout;
                            currentPlayerScope.textureView = textureView;
                            currentPlayerScope.surfaceView = surfaceView;
                            currentPlayerScope.livePlayer = null;
                            FileStreamLoadOperation.setPriorityForDocument(playerHolder.document, FileLoader.PRIORITY_HIGH);
                            FileLoader.getInstance(currentAccount).changePriority(FileLoader.PRIORITY_HIGH, playerHolder.document, null, null, null, null, null);
                            if (t == 0 && playerSavedPosition != 0) {
                                t = playerSavedPosition;
                                currentPlayerScope.firstFrameRendered = true;
                            }
                            FileLog.d("StoryViewer requestPlayer: currentPlayerScope.player start " + uri);
                            currentPlayerScope.player.start(false, isPaused(), uri, t, isInSilentMode, currentSpeed);
                            currentPlayerScope.invalidate();
                        } else {
                            FileLog.d("StoryViewer requestPlayer: url is null (1)");
                        }
                    } else if (sameUri) {
                        currentPlayerScope = scope;
                        currentPlayerScope.player = playerHolder;
                        currentPlayerScope.livePlayer = null;
                        playerHolder.setSpeed(currentSpeed);
                        currentPlayerScope.firstFrameRendered = playerHolder.firstFrameRendered;
                        currentPlayerScope.renderView = aspectRatioFrameLayout;
                        currentPlayerScope.textureView = textureView;
                        currentPlayerScope.surfaceView = surfaceView;
                        FileLog.d("StoryViewer requestPlayer: same url");
                    }
                    switchToLive(false, uri != null);
                    playerSavedPosition = 0;
                    updatePlayingMode();
                }

                private void switchToLive(boolean live, boolean hasVideo) {
                    if (liveView != null) {
                        liveView.setVisibility(live ? View.VISIBLE : View.GONE);
                    }
                    if (surfaceView != null) {
                        surfaceView.setVisibility(live ? View.GONE : (hasVideo ? View.VISIBLE : View.INVISIBLE));
                    }
                    if (textureView != null) {
                        textureView.setVisibility(live ? View.GONE : View.VISIBLE);
                    }
                }

                @Override
                public boolean isClosed() {
                    return isClosed;
                }

                @Override
                public float getProgressToDismiss() {
                    return progressToDismiss;
                }

                @Override
                public void setIsRecording(boolean isRecording) {
                    StoryViewer.this.isRecording = isRecording;
                    updatePlayingMode();
                }

                @Override
                public void setIsWaiting(boolean b) {
                    StoryViewer.this.isWaiting = b;
                    updatePlayingMode();
                }

                @Override
                public void setIsCaption(boolean b) {
                    StoryViewer.this.isCaption = b;
                    updatePlayingMode();
                }

                @Override
                public void setIsCaptionPartVisible(boolean b) {
                    StoryViewer.this.isCaptionPartVisible = b;
                }

                @Override
                public void setPopupIsVisible(boolean b) {
                    StoryViewer.this.isPopupVisible = b;
                    updatePlayingMode();
                }

                @Override
                public void setTranslating(boolean b) {
                    StoryViewer.this.isTranslating = b;
                    updatePlayingMode();
                }

                @Override
                public void setBulletinIsVisible(boolean b) {
                    StoryViewer.this.isBulletinVisible = b;
                    updatePlayingMode();
                }

                @Override
                public void setIsInPinchToZoom(boolean b) {
                    if (!StoryViewer.this.isInPinchToZoom && b && StoryViewer.this.inSeekingMode) {
                        StoryViewer.this.inSeekingMode = false;
                        if (currentPlayerScope != null && currentPlayerScope.player != null) {
                            currentPlayerScope.player.setSeeking(false);
                        }
                        PeerStoriesView peerView = getCurrentPeerView();
                        if (peerView != null) {
                            peerView.invalidate();
                        }
                    }
                    StoryViewer.this.isInPinchToZoom = b;
                    updatePlayingMode();
                }

                @Override
                public void setIsHintVisible(boolean visible) {
                    StoryViewer.this.isHintVisible = visible;
                    updatePlayingMode();
                }

                @Override
                public void setIsSwiping(boolean swiping) {
                    StoryViewer.this.isSwiping = swiping;
                    updatePlayingMode();
                }

                @Override
                public void setIsInSelectionMode(boolean selectionMode) {
                    StoryViewer.this.isInTextSelectionMode = selectionMode;
                    updatePlayingMode();
                }

                @Override
                public void setIsLikesReaction(boolean show) {
                    StoryViewer.this.isLikesReactions = show;
                    updatePlayingMode();
                }

                @Override
                public int getKeyboardHeight() {
                    return realKeyboardHeight;
                }

                @Override
                public void preparePlayer(ArrayList<TLRPC.Document> documents, ArrayList<Uri> uries) {
                    if (!SharedConfig.deviceIsHigh() || !SharedConfig.allowPreparingHevcPlayers()) {
                        return;
                    }
                    if (isClosed) {
                        return;
                    }
                    for (int i = 0; i < preparedPlayers.size(); i++) {
                        boolean found = false;
                        for (int j = 0; j < uries.size(); j++) {
                            if (uries.get(j).equals(preparedPlayers.get(i).uri)) {
                                found = true;
                                uries.remove(j);
                            }
                        }
                    }
                    for (int i = 0; i < uries.size(); i++) {
                        Uri uri = uries.get(i);
                        VideoPlayerHolder playerHolder = new VideoPlayerHolder(surfaceView, textureView);
                        playerHolder.setOnSeekUpdate(() -> {
                            PeerStoriesView currentPeerView = storiesViewPager.getCurrentPeerView();
                            if (currentPeerView != null && currentPeerView.storyContainer != null && currentPlayerScope != null && currentPlayerScope.player == playerHolder) {
                                currentPeerView.storyContainer.invalidate();
                            }
                        });
                        playerHolder.uri = uri;
                        playerHolder.document = documents.get(i);
                        FileStreamLoadOperation.setPriorityForDocument(playerHolder.document, FileLoader.PRIORITY_LOW);
                        playerHolder.preparePlayer(uri, isInSilentMode, currentSpeed);
                        preparedPlayers.add(playerHolder);
                        if (preparedPlayers.size() > 2) {
                            VideoPlayerHolder player = preparedPlayers.remove(0);
                            player.release(null);
                        }
                    }
                }
            });
            containerView.addView(storiesViewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
            aspectRatioFrameLayout = new AspectRatioFrameLayout(context);
            if (USE_SURFACE_VIEW) {
                surfaceView = new SurfaceView(context);
                surfaceView.setZOrderMediaOverlay(false);
                surfaceView.setZOrderOnTop(false);
                //surfaceView.setZOrderMediaOverlay(true);
                aspectRatioFrameLayout.addView(surfaceView);
            } else {
                textureView = new HwTextureView(context) {
                    @Override
                    public void invalidate() {
                        super.invalidate();
                        if (currentPlayerScope != null) {
                            currentPlayerScope.invalidate();
                        }
                    }
                };
                aspectRatioFrameLayout.addView(textureView);
            }

            liveView = new LivePlayerView(context, currentAccount, false);
            liveView.setVisibility(View.GONE);
            aspectRatioFrameLayout.addView(liveView);

            volumeControl = new StoriesVolumeControl(context);
            containerView.addView(volumeControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 4, 0, 4, 0));
        }
        if (liveView != null) {
            liveView.setAccount(currentAccount);
        }
        AndroidUtilities.removeFromParent(aspectRatioFrameLayout);
        windowView.addView(aspectRatioFrameLayout);
        if (surfaceView != null) {
            surfaceView.setVisibility(View.INVISIBLE);
        }

        AndroidUtilities.removeFromParent(containerView);
        windowView.addView(containerView);
        windowView.setClipChildren(false);

        if (isSingleStory) {
            updateTransitionParams();
        }
        if (storiesList != null) {
            storiesViewPager.setDays(storiesList.dialogId, storiesList.getDays(), currentAccount);
        } else {
            storiesViewPager.setPeerIds(peerIds, currentAccount, position);
        }

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (fragment == null || fragment.getLayoutContainer() == null || fragment.isSupportEdgeToEdge()) {
            ATTACH_TO_FRAGMENT = false;
        }
        ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE = ATTACH_TO_FRAGMENT && fragment != null && fragment.isSupportEdgeToEdge();
        ViewCompat.setOnApplyWindowInsetsListener(containerView, (v, insets) -> {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) containerView.getLayoutParams();
            layoutParams.topMargin = ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE ? 0 : insets.getSystemWindowInsetTop();
            layoutParams.bottomMargin = ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE ?
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom :
                insets.getSystemWindowInsetBottom();

            layoutParams.leftMargin = insets.getSystemWindowInsetLeft();
            layoutParams.rightMargin = insets.getSystemWindowInsetRight();

            windowView.requestLayout();
            containerView.requestLayout();

            return WindowInsetsCompat.CONSUMED;
        });

        if (ATTACH_TO_FRAGMENT) {
            AndroidUtilities.removeFromParent(windowView);
            windowView.setTag(0xFF112233, new Object());
            fragment.getLayoutContainer().addView(windowView);
            if (!ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE) {
                AndroidUtilities.requestAdjustResize(fragment.getParentActivity(), fragment.getClassGuid());
            }
        } else {
            windowView.setFocusable(false);
            containerView.setFocusable(false);
            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, windowView, windowLayoutParams);
            windowManager.addView(windowView, windowLayoutParams);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                final OnBackInvokedDispatcher dispatcher = windowView.findOnBackInvokedDispatcher();
                if (dispatcher != null) {
                    dispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        () -> {
                            if (LaunchActivity.instance != null) {
                                LaunchActivity.instance.onBackPressed();
                            } else {
                                onAttachedBackPressed();
                            }
                        }
                    );
                }
            }
        }
        windowView.requestLayout();
        runOpenAnimationAfterLayout = true;

        updateTransitionParams();
        progressToOpen = 0f;
        checkNavBarColor();
        animationInProgress = true;

        checkInSilentMode();
        if (ATTACH_TO_FRAGMENT) {
            lockOrientation(true);
        }

        if (!ATTACH_TO_FRAGMENT) {
            globalInstances.add(this);
        }
        AndroidUtilities.hideKeyboard(fragment.getFragmentView());
    }

    static int J = 0;
    int j = J++;

    private void showKeyboard() {
        PeerStoriesView currentPeerView = storiesViewPager.getCurrentPeerView();
        if (currentPeerView != null) {
            if (currentPeerView.showKeyboard()) {
                AndroidUtilities.runOnUIThread(this::cancelSwipeToReply, 200);
                return;
            }
        }
        cancelSwipeToReply();
    }

    ValueAnimator swipeToViewsAnimator;

    public void cancelSwipeToViews(boolean open) {
        if (swipeToViewsAnimator != null) {
            return;
        }
        if (realKeyboardHeight != 0) {
            AndroidUtilities.hideKeyboard(selfStoryViewsView);
            return;
        }
        if (allowSelfStoriesView || selfStoriesViewsOffset != 0) {
            locker.lock();
            if (!open && selfStoriesViewsOffset == selfStoryViewsView.maxSelfStoriesViewsOffset) {
                selfStoriesViewsOffset = selfStoryViewsView.maxSelfStoriesViewsOffset - 1;
                selfStoryViewsView.setOffset(selfStoryViewsView.maxSelfStoriesViewsOffset - 1);
            }
            swipeToViewsAnimator = ValueAnimator.ofFloat(selfStoriesViewsOffset, open ? selfStoryViewsView.maxSelfStoriesViewsOffset : 0);
            swipeToViewsAnimator.addUpdateListener(animation -> {
                selfStoriesViewsOffset = (float) animation.getAnimatedValue();
//                final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
//                if (peerStoriesView != null) {
//                    peerStoriesView.invalidate();
//                }
                containerView.invalidate();
            });
            swipeToViewsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    locker.unlock();
                    selfStoriesViewsOffset = open ? selfStoryViewsView.maxSelfStoriesViewsOffset : 0;
                    final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
                    if (peerStoriesView != null) {
                        peerStoriesView.invalidate();
                    }
                    containerView.invalidate();
                    swipeToViewsAnimator = null;
                }
            });
            if (open) {
                swipeToViewsAnimator.setDuration(350);
                swipeToViewsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                swipeToViewsAnimator.setDuration(350);
                swipeToViewsAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            }
            swipeToViewsAnimator.start();
        }
    }

    public void checkSelfStoriesView() {
        if (selfStoryViewsView == null) {
            selfStoryViewsView = new SelfStoryViewsView(containerView.getContext(), this);
            containerView.addView(selfStoryViewsView, 0);
        }
        PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
        if (peerStoriesView != null) {
            if (storiesList != null) {
                ArrayList<TL_stories.StoryItem> storyItems = new ArrayList<>();
                for (int i = 0; i < storiesList.messageObjects.size(); i++) {
                    storyItems.add(storiesList.messageObjects.get(i).storyItem);
                }
                selfStoryViewsView.setItems(storiesList.dialogId, storyItems, peerStoriesView.getListPosition());
            } else {
                selfStoryViewsView.setItems(peerStoriesView.getCurrentPeer(), peerStoriesView.getStoryItems(), peerStoriesView.getSelectedPosition());
            }
        }
    }

    @Override
    public boolean showDialog(Dialog dialog) {
        try {
            currentDialog = dialog;
            dialog.setOnDismissListener(dialog1 -> {
                if (dialog1 == currentDialog) {
                    currentDialog = null;
                    updatePlayingMode();
                }
            });
            dialog.show();
            updatePlayingMode();
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            currentDialog = null;
            return false;
        }
    }

    public boolean listenToAttachedSheet(BaseFragment.AttachedSheet sheet) {
        currentSheet = sheet;
        sheet.setOnDismissListener(() -> {
            currentSheet = null;
            updatePlayingMode();
        });
        return true;
    }

    @Override
    public void setOnDismissListener(Runnable onDismiss) {
        // not used
    }

    public void cancelSwipeToReply() {
        if (swipeToReplyBackAnimator == null) {
            inSwipeToDissmissMode = false;
            allowSwipeToReply = false;
            swipeToReplyBackAnimator = ValueAnimator.ofFloat(swipeToReplyOffset, 0);
            swipeToReplyBackAnimator.addUpdateListener(animation -> {
                swipeToReplyOffset = (float) animation.getAnimatedValue();
                int maxOffset = dp(200);
                swipeToReplyProgress = Utilities.clamp(swipeToReplyOffset / maxOffset, 1f, 0);
                PeerStoriesView peerView = storiesViewPager == null ? null : storiesViewPager.getCurrentPeerView();
                if (peerView != null) {
                    peerView.invalidate();
                }
            });
            swipeToReplyBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    swipeToReplyBackAnimator = null;
                    swipeToReplyOffset = 0;
                    swipeToReplyProgress = 0;
                    PeerStoriesView peerView = storiesViewPager == null ? null : storiesViewPager.getCurrentPeerView();
                    if (peerView != null) {
                        peerView.invalidate();
                    }
                }
            });
            swipeToReplyBackAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
            swipeToReplyBackAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
            swipeToReplyBackAnimator.start();
        }
    }

    public boolean getStoryRect(RectF rectF) {
        if (storiesViewPager == null) {
            return false;
        }
        PeerStoriesView page = storiesViewPager.getCurrentPeerView();
        if (page == null || page.storyContainer == null) {
            return false;
        }
        float x = windowView == null ? 0 : windowView.getX();
        float y = windowView == null ? 0 : windowView.getY();
        rectF.set(
            x + swipeToDismissHorizontalOffset + containerView.getLeft() + page.getX() + page.storyContainer.getX(),
            y + swipeToDismissOffset + containerView.getTop() + page.getY() + page.storyContainer.getY(),
            x + swipeToDismissHorizontalOffset + containerView.getRight() - (containerView.getWidth() - page.getRight()) - (page.getWidth() - page.storyContainer.getRight()),
            y + swipeToDismissOffset + containerView.getBottom() - (containerView.getHeight() - page.getBottom()) - (page.getHeight() - page.storyContainer.getBottom())
        );
        return true;
    }

    public void switchByTap(boolean forward) {
        PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
        if (peerView == null) {
            return;
        }
        if (!peerView.switchToNext(forward)) {
            if (!storiesViewPager.switchToNext(forward)) {
                if (forward) {
                    close(true);
                } else {
                    if (playerHolder != null) {
                        playerHolder.loopBack();
                    }
                }
            } else {
                storiesViewPager.lockTouchEvent(150);
            }
        }
    }

    @Nullable
    public PeerStoriesView getCurrentPeerView() {
        if (storiesViewPager == null) {
            return null;
        }
        return storiesViewPager.getCurrentPeerView();
    }

    private void lockOrientation(boolean lock) {
        Activity activity = AndroidUtilities.findActivity(fragment.getContext());
        if (activity != null) {
            try {
                activity.setRequestedOrientation(lock ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } catch (Exception ignore) {}
            if (lock) {
                activity.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    private void dispatchVolumeEvent(KeyEvent event) {
        if (isInSilentMode) {
            toggleSilentMode();
            return;
        }
        PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
        if (peerStoriesView != null && !peerStoriesView.currentStory.hasSound() && peerStoriesView.currentStory.isVideo()) {
            peerStoriesView.showNoSoundHint(true);
            return;
        }
        volumeControl.onKeyDown(event.getKeyCode(), event);
    }

    public void toggleSilentMode() {
        isInSilentMode = !isInSilentMode;
        if (playerHolder != null) {
            playerHolder.setAudioEnabled(!isInSilentMode, false);
        }
        for (int i = 0; i < preparedPlayers.size(); i++) {
            preparedPlayers.get(i).setAudioEnabled(!isInSilentMode, true);
        }
        final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
        if (peerStoriesView != null) {
            peerStoriesView.sharedResources.setIconMuted(!soundEnabled(), true);
        }
        if (!isInSilentMode) {
            volumeControl.unmute();
        }
    }

    private void checkInSilentMode() {
        if (checkSilentMode) {
            checkSilentMode = false;
            AudioManager am = (AudioManager) windowView.getContext().getSystemService(Context.AUDIO_SERVICE);
            isInSilentMode = am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        }
    }

    private void layoutAndFindView() {
        foundViewToClose = true;
        if (transitionViewHolder.avatarImage != null) {
            transitionViewHolder.avatarImage.setVisible(true, true);
        }
        if (transitionViewHolder.storyImage != null) {
            transitionViewHolder.storyImage.setAlpha(1f);
            transitionViewHolder.storyImage.setVisible(true, true);
        }
        if (storiesList != null) {
            final PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
            if (peerView != null) {
                int position = peerView.getSelectedPosition();
                if (position >= 0 && position < storiesList.messageObjects.size()) {
                    messageId = storiesList.messageObjects.get(position).getId();
                }
            }
        }
        if (placeProvider != null) {
            placeProvider.preLayout(storiesViewPager.getCurrentDialogId(), messageId, () -> {
                updateTransitionParams();
                if (transitionViewHolder.avatarImage != null) {
                    transitionViewHolder.avatarImage.setVisible(false, true);
                }
                if (transitionViewHolder.storyImage != null) {
                    transitionViewHolder.storyImage.setVisible(false, true);
                }
            });
        }
    }

    private void updateTransitionParams() {
        if (placeProvider != null) {
            if (transitionViewHolder.avatarImage != null) {
                transitionViewHolder.avatarImage.setVisible(true, true);
            }
            if (transitionViewHolder.storyImage != null) {
                transitionViewHolder.storyImage.setAlpha(1f);
                transitionViewHolder.storyImage.setVisible(true, true);
            }
            final PeerStoriesView peerView = storiesViewPager.getCurrentPeerView();
            int position = peerView == null ? 0 : peerView.getSelectedPosition();
            int storyId = peerView == null || position < 0 || position >= peerView.storyItems.size() ? 0 : peerView.storyItems.get(position).id;
            TL_stories.StoryItem storyItem = peerView == null || position < 0 || position >= peerView.storyItems.size() ? null : peerView.storyItems.get(position);
            if (storyItem == null && isSingleStory) {
                storyItem = singleStory;
            }
            long did = storiesViewPager.getCurrentDialogId();
            if (storiesList instanceof StoriesController.SearchStoriesList && storyItem != null) {
                did = storyItem.dialogId;
                storyId = storyItem.messageId;
            } else if (storiesList != null) {
                storyId = dayStoryId;
            }
            transitionViewHolder.clear();
            if (placeProvider.findView(did, messageId, storyId, storyItem == null ? -1 : storyItem.messageType, transitionViewHolder)) {
                transitionViewHolder.storyId = storyId;
                if (transitionViewHolder.view != null) {
                    int[] loc = new int[2];
                    transitionViewHolder.view.getLocationOnScreen(loc);
                    if (transitionViewHolder.view instanceof ChatMessageCell) {
                        loc[1] += transitionViewHolder.view.getPaddingTop();
                    }
                    fromXCell = loc[0];
                    fromYCell = loc[1];
                    if (transitionViewHolder.view instanceof StoriesListPlaceProvider.AvatarOverlaysView) {
                        animateFromCell = (StoriesListPlaceProvider.AvatarOverlaysView) transitionViewHolder.view;
                    } else {
                        animateFromCell = null;
                    }
                    animateAvatar = false;
                    if (transitionViewHolder.avatarImage != null) {
                        fromX = loc[0] + transitionViewHolder.avatarImage.getCenterX();
                        fromY = loc[1] + transitionViewHolder.avatarImage.getCenterY();
                        fromWidth = transitionViewHolder.avatarImage.getImageWidth();
                        fromHeight = transitionViewHolder.avatarImage.getImageHeight();
                        if (transitionViewHolder.params != null) {
                            fromWidth *= transitionViewHolder.params.getScale();
                            fromHeight *= transitionViewHolder.params.getScale();
                        }
                        if (transitionViewHolder.view.getParent() instanceof View) {
                            View parent = (View) transitionViewHolder.view.getParent();
                            fromX = loc[0] + transitionViewHolder.avatarImage.getCenterX() * parent.getScaleX();
                            fromY = loc[1] + transitionViewHolder.avatarImage.getCenterY() * parent.getScaleY();
                            fromWidth *= parent.getScaleX();
                            fromHeight *= parent.getScaleY();
                        }
                        animateAvatar = true;
                    } else if (transitionViewHolder.storyImage != null) {
                        fromX = loc[0] + transitionViewHolder.storyImage.getCenterX();
                        fromY = loc[1] + transitionViewHolder.storyImage.getCenterY();
                        fromWidth = transitionViewHolder.storyImage.getImageWidth();
                        fromHeight = transitionViewHolder.storyImage.getImageHeight();
                        fromRadius = transitionViewHolder.storyImage.getRoundRadius()[0];
                    }

                    transitionViewHolder.clipParent.getLocationOnScreen(loc);
                    if (transitionViewHolder.clipTop == 0 && transitionViewHolder.clipBottom == 0) {
                        clipBottom = clipTop = 0;
                    } else {
                        clipTop = loc[1] + transitionViewHolder.clipTop;
                        clipBottom = loc[1] + transitionViewHolder.clipBottom;
                    }
                } else {
                    animateAvatar = false;
                    fromX = fromY = 0;
                }
            } else {
                animateAvatar = false;
                fromX = fromY = 0;
            }
        } else {
            animateAvatar = false;
            fromX = fromY = 0;
        }
    }

    private void requestAdjust(boolean nothing) {
        if (ATTACH_TO_FRAGMENT) {
            if (!ATTACHED_FRAGMENT_IS_EDGE_TO_EDGE) {
                if (nothing) {
                    AndroidUtilities.requestAdjustNothing(fragment.getParentActivity(), fragment.getClassGuid());
                } else {
                    AndroidUtilities.requestAdjustResize(fragment.getParentActivity(), fragment.getClassGuid());
                }
            }
        } else {
            windowLayoutParams.softInputMode = (nothing ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            try {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void setInTouchMode(boolean b) {
        isInTouchMode = b;
        if (isInTouchMode) {
            volumeControl.hide();
        }
        updatePlayingMode();
    }

    public void setOverlayVisible(boolean visible) {
        isOverlayVisible = visible;
        updatePlayingMode();
    }

    public void setOnCloseListener(Runnable listener) {
        onCloseListener = listener;
    }

    public boolean isPaused() {
        return (
            isPopupVisible ||
            isTranslating ||
            isBulletinVisible ||
            isCaption ||
            isWaiting ||
            isInTouchMode ||
            keyboardVisible ||
            currentDialog != null ||
            currentSheet != null ||
            allowTouchesByViewpager ||
            isClosed ||
            isRecording ||
            progressToOpen != 1f ||
            selfStoriesViewsOffset != 0 ||
            isHintVisible ||
            (isSwiping && USE_SURFACE_VIEW) ||
            isOverlayVisible ||
            isInTextSelectionMode ||
            isLikesReactions ||
            progressToDismiss != 0 ||
            storiesIntro != null ||
            ATTACH_TO_FRAGMENT && fragment != null && fragment.getLastStoryViewer() != this
        );
    }

    public void updatePlayingMode() {
        updatePipSource();
        if (storiesViewPager == null) {
            return;
        }
        boolean pause = isPaused();
        if (ATTACH_TO_FRAGMENT && (fragment.isPaused() || !fragment.isLastFragment())) {
            pause = true;
        }
        if (ArticleViewer.getInstance().isVisible()) {
            pause = true;
        }

        storiesViewPager.setPaused(pause);
        if (playerHolder != null) {
            if (pause) {
                playerHolder.pause();
            } else {
                playerHolder.play(currentSpeed);
            }
        }
        storiesViewPager.enableTouch(!keyboardVisible && !isClosed && !isRecording && !isLongpressed && !isInPinchToZoom && selfStoriesViewsOffset == 0 && !isInTextSelectionMode);
    }

    private boolean findClickableView(FrameLayout windowView, float x, float y, boolean swipeToDissmiss) {
        if (windowView == null) {
            return false;
        }
        if (isPopupVisible) {
            return true;
        }
        if (selfStoryViewsView != null && selfStoriesViewsOffset != 0) {
            return true;
        }
        final PeerStoriesView currentPeerView = storiesViewPager.getCurrentPeerView();
        if (currentPeerView != null) {
            //fix view pager strange coordinates
            //just skip page.getX()
            float x1 = x - containerView.getX() - storiesViewPager.getX() - currentPeerView.getX();
            float y1 = y - containerView.getY() - storiesViewPager.getY() - currentPeerView.getY();
            if (currentPeerView.findClickableView(currentPeerView, x1, y1, swipeToDissmiss)) {
                return true;
            }
            if (currentPeerView.keyboardVisible) {
                return false;
            }
        }
        if (swipeToDissmiss) {
            return false;
        }
        if (currentPeerView != null && currentPeerView.chatActivityEnterView != null && currentPeerView.chatActivityEnterView.getVisibility() == View.VISIBLE && y > containerView.getY() + storiesViewPager.getY() + currentPeerView.getY() + currentPeerView.chatActivityEnterView.getY()) {
            return true;
        }
        if (currentPeerView != null && currentPeerView.chatActivityEnterView != null && currentPeerView.chatActivityEnterView.isRecordingAudioVideo()) {
            return true;
        }
        if (storiesIntro != null) {
            return true;
        }
        return AndroidUtilities.findClickableView(windowView, x, y, currentPeerView);
    }

    public boolean closeKeyboardOrEmoji() {
        if (storiesViewPager == null) {
            return false;
        }
        final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
        if (peerStoriesView != null) {
            return peerStoriesView.closeKeyboardOrEmoji();
        }
        return false;
    }

    private void updateProgressToDismiss() {
        float newProgress = Utilities.clamp01(Math.abs(Math.max(swipeToDismissHorizontalOffset, swipeToDismissOffset) / dp(80)));
        if (progressToDismiss != newProgress) {
            progressToDismiss = newProgress;
            checkNavBarColor();
            final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
            if (peerStoriesView != null) {
                peerStoriesView.progressToDismissUpdated();
            }
            if (livePlayer != null) {
                livePlayer.setVolume((1.0f - progressToDismiss) * progressToOpen);
            }
        }

        if (windowView != null) {
            windowView.invalidate();
        }
    }

    private boolean showViewsAfterOpening;
    public void showViewsAfterOpening() {
        showViewsAfterOpening = true;
    }

    private void startOpenAnimation() {
        updateTransitionParams();
        progressToOpen = 0f;
        setNavigationButtonsColor(true);
        foundViewToClose = false;
        animationInProgress = true;
        fromDismissOffset = swipeToDismissOffset;
        if (transitionViewHolder.radialProgressUpload != null) {
            final PeerStoriesView peerStoriesView = getCurrentPeerView();
            if (peerStoriesView != null && peerStoriesView.headerView.radialProgress != null) {
                peerStoriesView.headerView.radialProgress.copyParams(transitionViewHolder.radialProgressUpload);
            }
        }
        opening = true;
        openCloseAnimator = ValueAnimator.ofFloat(0, 1f);
        openCloseAnimator.addUpdateListener(animation -> {
            progressToOpen = (float) animation.getAnimatedValue();
            if (containerView != null) {
                containerView.checkHwAcceleration(progressToOpen);
            }
            if (livePlayer != null) {
                livePlayer.setVolume((1.0f - progressToDismiss) * progressToOpen);
            }
            checkNavBarColor();
            if (windowView != null) {
                windowView.invalidate();
            }
        });
        locker.lock();
        if (containerView != null) {
            containerView.enableHwAcceleration();
        }
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                progressToOpen = 1f;
                checkNavBarColor();
                animationInProgress = false;
                if (containerView != null) {
                    containerView.disableHwAcceleration();
                }
                if (windowView != null) {
                    windowView.invalidate();
                }
                if (transitionViewHolder.avatarImage != null && !foundViewToClose) {
                    transitionViewHolder.avatarImage.setVisible(true, true);
                    transitionViewHolder.avatarImage = null;
                }
                if (transitionViewHolder.storyImage != null && !foundViewToClose) {
                    transitionViewHolder.storyImage.setAlpha(1f);
                    transitionViewHolder.storyImage.setVisible(true, true);
                    transitionViewHolder.storyImage = null;
                }
                final PeerStoriesView peerStoriesView = getCurrentPeerView();
                if (peerStoriesView != null) {
                    peerStoriesView.updatePosition();
                }
                if (livePlayer != null) {
                    livePlayer.setVolume((1.0f - progressToDismiss) * progressToOpen);
                }

                if (showViewsAfterOpening) {
                    showViewsAfterOpening = false;
                    openViews();
                } else if (!SharedConfig.storiesIntroShown) {
                    if (storiesIntro == null && containerView != null) {
                        storiesIntro = new StoriesIntro(containerView.getContext(), windowView);
                        storiesIntro.setAlpha(0f);
                        containerView.addView(storiesIntro);
                    }
                    if (storiesIntro != null) {
                        storiesIntro.setOnClickListener(v -> {
                            storiesIntro.animate()
                                    .alpha(0f)
                                    .setDuration(150L)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            if (storiesIntro != null) {
                                                storiesIntro.stopAnimation();
                                                containerView.removeView(storiesIntro);
                                            }
                                            storiesIntro = null;
                                            updatePlayingMode();
                                        }
                                    })
                                    .start();
                        });
                        storiesIntro.animate()
                                .alpha(1f)
                                .setDuration(150L)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        if (storiesIntro != null) {
                                            storiesIntro.startAnimation(true);
                                        }
                                    }
                                }).start();
                    }

                    SharedConfig.setStoriesIntroShown(true);
                }
                updatePlayingMode();
                locker.unlock();
            }
        });

        openCloseAnimator.setStartDelay(40);
        openCloseAnimator.setDuration(250);
        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openCloseAnimator.start();

        if (!doOnAnimationReadyRunnables.isEmpty()) {
            for (int i = 0; i < doOnAnimationReadyRunnables.size(); i++) {
                doOnAnimationReadyRunnables.get(i).run();
            }
            doOnAnimationReadyRunnables.clear();
        }
    }

    public void instantClose() {
        if (!isShowing) {
            return;
        }
        AndroidUtilities.hideKeyboard(windowView);
        isClosed = true;
        fullyVisible = false;
        progressToOpen = 0;
        progressToDismiss = 0;
        updatePlayingMode();
        fromX = fromY = 0;
        if (transitionViewHolder.avatarImage != null) {
            transitionViewHolder.avatarImage.setVisible(true, true);
        }
        if (transitionViewHolder.storyImage != null) {
            transitionViewHolder.storyImage.setVisible(true, true);
        }
        transitionViewHolder.storyImage = null;
        transitionViewHolder.avatarImage = null;
        if (containerView != null) {
            containerView.disableHwAcceleration();
        }
        locker.unlock();
        if (currentPlayerScope != null) {
            currentPlayerScope.invalidate();
        }
        release();
        if (ATTACH_TO_FRAGMENT) {
            AndroidUtilities.removeFromParent(windowView);
        } else {
            windowManager.removeView(windowView);
        }
        windowView = null;
        isShowing = false;
        foundViewToClose = false;
        checkNavBarColor();
        if (onCloseListener != null) {
            onCloseListener.run();
            onCloseListener = null;
        }
    }

    private void startCloseAnimation(boolean backAnimation) {
        setNavigationButtonsColor(false);
        updateTransitionParams();
        locker.lock();
        fromDismissOffset = swipeToDismissOffset;
        opening = false;
        openCloseAnimator = ValueAnimator.ofFloat(progressToOpen, 0);
        openCloseAnimator.addUpdateListener(animation -> {
            progressToOpen = (float) animation.getAnimatedValue();
            checkNavBarColor();
            if (windowView != null) {
                windowView.invalidate();
            }
            if (livePlayer != null) {
                livePlayer.setVolume((1.0f - progressToDismiss) * progressToOpen);
            }
        });
        if (!backAnimation) {
            fromX = fromY = 0;
            if (transitionViewHolder.avatarImage != null) {
                transitionViewHolder.avatarImage.setVisible(true, true);
            }
            if (transitionViewHolder.storyImage != null) {
                transitionViewHolder.storyImage.setVisible(true, true);
            }
            transitionViewHolder.storyImage = null;
            transitionViewHolder.avatarImage = null;
        } else {
            layoutAndFindView();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (openCloseAnimator == null) {
                return;
            }
            if (containerView != null) {
                containerView.enableHwAcceleration();
            }
            openCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (containerView != null) {
                        containerView.disableHwAcceleration();
                    }
                    checkNavBarColor();
                    locker.unlock();
                    if (storiesIntro != null) {
                        storiesIntro.stopAnimation();
                        AndroidUtilities.removeFromParent(storiesIntro);
                        storiesIntro = null;
                    }
                    if (transitionViewHolder.avatarImage != null) {
                        transitionViewHolder.avatarImage.setVisible(true, true);
                        transitionViewHolder.avatarImage = null;
                    }
                    if (transitionViewHolder.storyImage != null) {
                        transitionViewHolder.storyImage.setAlpha(1f);
                        transitionViewHolder.storyImage.setVisible(true, true);
                    }
                    if (transitionViewHolder.radialProgressUpload != null) {
                        final PeerStoriesView peerStoriesView = getCurrentPeerView();
                        if (peerStoriesView != null && peerStoriesView.headerView.radialProgress != null) {
                            transitionViewHolder.radialProgressUpload.copyParams(peerStoriesView.headerView.radialProgress);
                        }
                    }
                    if (currentPlayerScope != null) {
                        currentPlayerScope.invalidate();
                    }
                    release();
                    try {
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                if (windowView == null) {
                                    return;
                                }
                                if (ATTACH_TO_FRAGMENT) {
                                    AndroidUtilities.removeFromParent(windowView);
                                } else {
                                    windowManager.removeView(windowView);
                                }
                                windowView = null;
                            } catch (Exception e2) {

                            }
                        });
                    } catch (Exception e) {

                    }
                    isShowing = false;
                    foundViewToClose = false;
                    if (onCloseListener != null) {
                        onCloseListener.run();
                        onCloseListener = null;
                    }
                }
            });
            openCloseAnimator.setDuration(400);
            openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
//            openCloseAnimator.setDuration(2000);
//            openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            openCloseAnimator.start();
        }, 16);
    }

    @Override
    public void release() {
        lastUri = null;
        setInTouchMode(false);
        allowScreenshots(true);
        if (playerHolder != null) {
            playerHolder.release(null);
            playerHolder = null;
        }
        if (liveView != null) {
            liveView.setScope(0, null);
        }
        if (livePlayer != null && !LiveStoryPipOverlay.isVisible(livePlayer)) {
            if (livePlayer.outgoing) {
                livePlayer.setDisplaySink(null);
            } else {
                livePlayer.destroy();
            }
        }
        livePlayer = null;
        for (int i = 0; i < preparedPlayers.size(); i++) {
            preparedPlayers.get(i).release(null);
        }
        preparedPlayers.clear();
        MessagesController.getInstance(currentAccount).getStoriesController().stopAllPollers();
        if (ATTACH_TO_FRAGMENT) {
            lockOrientation(false);
            if (fragment != null) {
                fragment.removeSheet(this);
            }
        }

        globalInstances.remove(this);
        doOnAnimationReadyRunnables.clear();
        selfStoriesViewsOffset = 0;
        lastStoryItem = null;
    }

    public void close(boolean backAnimation) {
        AndroidUtilities.hideKeyboard(windowView);
        isClosed = true;
        invalidateOutRect = true;
        updatePlayingMode();
        startCloseAnimation(backAnimation);
        if (unreadStateChanged) {
            unreadStateChanged = false;
            //MessagesController.getInstance(currentAccount).getStoriesController().scheduleSort();
        }
    }

    @Override
    public View getWindowView() {
        return windowView;
    }

    @Override
    public void dismiss() {
        close(true);
    }

    @Override
    public int getNavigationBarColor(int currentColor) {
        return ColorUtils.blendARGB(currentColor, Color.BLACK, getBlackoutAlpha());
    }

    @Override
    public boolean isAttachedLightStatusBar() {
        return false;
    }

    private float getBlackoutAlpha() {
        return progressToOpen * (0.5f + 0.5f * (1f - progressToDismiss));
    }

    @Override
    public boolean onAttachedBackPressed() {
        if (selfStoriesViewsOffset != 0) {
            if (selfStoryViewsView.onBackPressed()) {
                return true;
            }
            cancelSwipeToViews(false);
            return true;
        } else if (closeKeyboardOrEmoji()) {
            return true;
        } else {
            close(true);
        }
        return true;
    }

    @Override
    public boolean isShown() {
        return !isClosed;
    }

    public void checkNavBarColor() {
        if (ATTACH_TO_FRAGMENT && LaunchActivity.instance != null) {
            LaunchActivity.instance.checkSystemBarColors(true, true, true);
            //LaunchActivity.instance.setNavigationBarColor(fragment.getNavigationBarColor(), false);
        }
    }

    /**
     * Changing the color of buttons in the navigation bar is a difficult task for weak devices.
     * It's better to change the color before the animation starts.
     */
    private void setNavigationButtonsColor(boolean isOpening) {
        LaunchActivity activity = LaunchActivity.instance;
        if (ATTACH_TO_FRAGMENT && activity != null) {
            if (isOpening) {
                openedFromLightNavigationBar = activity.isLightNavigationBar();
            }
            if (openedFromLightNavigationBar) {
                AndroidUtilities.setLightNavigationBar(activity, !isOpening);
            }
        }
    }

    @Override
    public boolean attachedToParent() {
        if (ATTACH_TO_FRAGMENT) {
            return windowView != null;
        }
        return false;
    }

    public void setKeyboardHeightFromParent(int keyboardHeight) {
        if (realKeyboardHeight != keyboardHeight) {
            realKeyboardHeight = keyboardHeight;
            storiesViewPager.setKeyboardHeight(keyboardHeight);
            storiesViewPager.requestLayout();
            if (selfStoryViewsView != null) {
                selfStoryViewsView.setKeyboardHeight(keyboardHeight);
            }
        }
    }

    @Override
    public boolean isFullyVisible() {
        return fullyVisible;
    }

    public void presentFragment(BaseFragment fragment) {
        BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment == null) return;
        if (ATTACH_TO_FRAGMENT) {
            lastFragment.presentFragment(fragment);
        } else {
            lastFragment.presentFragment(fragment);
            close(false);
        }
    }

    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    public FrameLayout getContainerView() {
        return containerView;
    }

    @Nullable
    public FrameLayout getContainerForBulletin() {
        final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
        if (peerStoriesView != null) {
            return peerStoriesView.storyContainer;
        }
        return null;
    }

    public void startActivityForResult(Intent photoPickerIntent, int code) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        fragment.getParentActivity().startActivityForResult(photoPickerIntent, code);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        PeerStoriesView currentPeerView = storiesViewPager.getCurrentPeerView();
        if (currentPeerView != null) {
            currentPeerView.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            dispatchVolumeEvent(event);
        }
    }

    public void dismissVisibleDialogs() {
        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        if (currentSheet != null) {
            currentSheet.dismiss();
        }
        PeerStoriesView peerView = getCurrentPeerView();
        if (peerView != null) {
            if (peerView.reactionsContainerLayout != null && peerView.reactionsContainerLayout.getReactionsWindow() != null) {
                peerView.reactionsContainerLayout.getReactionsWindow().dismiss();
            }
            if (peerView.shareAlert != null) {
                peerView.shareAlert.dismiss();
            }
            peerView.needEnterText();
        }
    }

    public float getProgressToSelfViews() {
        if (selfStoryViewsView == null) {
            return 0;
        }
        return selfStoryViewsView.progressToOpen;
    }

    public void setSelfStoriesViewsOffset(float currentTranslation) {
        selfStoriesViewsOffset = currentTranslation;
        final PeerStoriesView peerStoriesView = storiesViewPager.getCurrentPeerView();
        if (peerStoriesView != null) {
            peerStoriesView.invalidate();
        }
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    public void openViews() {
        checkSelfStoriesView();
        AndroidUtilities.runOnUIThread(() -> {
            allowSelfStoriesView = true;
            cancelSwipeToViews(true);
        }, 30);

    }

    public boolean soundEnabled() {
        return !isInSilentMode;
    }

    public void allowScreenshots(boolean allowScreenshots) {
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            return;
        }
        allowScreenshots = !isShowing || allowScreenshots;
        if (this.allowScreenshots != allowScreenshots) {
            this.allowScreenshots = allowScreenshots;

            if (surfaceView != null) {
                surfaceView.setSecure(!allowScreenshots);
            }
            if (liveView != null) {
                liveView.setSecure(!allowScreenshots);
            }
            if (ATTACH_TO_FRAGMENT) {
                if (fragment.getParentActivity() != null) {
                    if (allowScreenshots) {
                        fragment.getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                        AndroidUtilities.logFlagSecure();
                    } else {
                        fragment.getParentActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                        AndroidUtilities.logFlagSecure();
                    }
                }
            } else {
                if (allowScreenshots) {
                    windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                    AndroidUtilities.logFlagSecure();
                } else {
                    windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;
                    AndroidUtilities.logFlagSecure();
                }
                try {
                    windowManager.updateViewLayout(windowView, windowLayoutParams);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void openFor(BaseFragment fragment, RecyclerListView recyclerListView, ChatActionCell cell) {
        MessageObject messageObject = cell.getMessageObject();
        if (fragment == null || fragment.getContext() == null) {
            return;
        }
        if (messageObject.type == MessageObject.TYPE_STORY_MENTION) {
            TL_stories.StoryItem storyItem =  messageObject.messageOwner.media.storyItem;
            storyItem.dialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.media.peer);
            storyItem.messageId = messageObject.getId();
            open(fragment.getContext(), messageObject.messageOwner.media.storyItem, StoriesListPlaceProvider.of(recyclerListView));
        }
    }

    public void doOnAnimationReady(Runnable runnable) {
        if (runnable != null) {
            doOnAnimationReadyRunnables.add(runnable);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesListUpdated) {
            StoriesController.StoriesList list = (StoriesController.StoriesList) args[0];
            if (storiesList == list) {
                PeerStoriesView peerStoriesView = getCurrentPeerView();
                storiesViewPager.setDays(storiesList.dialogId, storiesList.getDays(), currentAccount);
                if (selfStoryViewsView != null) {
                    TL_stories.StoryItem currentSelectedStory = selfStoryViewsView.getSelectedStory();
                    ArrayList<TL_stories.StoryItem> storyItems = new ArrayList<>();
                    int selectedPosition = 0;
                    for (int i = 0; i < storiesList.messageObjects.size(); i++) {
                        if (currentSelectedStory != null && currentSelectedStory.id == storiesList.messageObjects.get(i).storyItem.id) {
                            selectedPosition = i;
                        }
                        storyItems.add(storiesList.messageObjects.get(i).storyItem);
                    }
                    selfStoryViewsView.setItems(storiesList.dialogId, storyItems, selectedPosition);
                }
            }
        } else if (id == NotificationCenter.storiesUpdated) {
            if (placeProvider instanceof StoriesListPlaceProvider) {
                StoriesListPlaceProvider storiesListPlaceProvider = (StoriesListPlaceProvider) placeProvider;
                if (!storiesListPlaceProvider.hasPaginationParams || storiesListPlaceProvider.onlySelfStories) {
                    return;
                }
                StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
                ArrayList<TL_stories.PeerStories> allStories = storiesListPlaceProvider.hiddedStories ? storiesController.getHiddenList() : storiesController.getDialogListStories();
                boolean changed = false;
                ArrayList<Long> dialogs = storiesViewPager.getDialogIds();
                for (int i = 0; i < allStories.size(); i++) {
                    TL_stories.PeerStories userStories = allStories.get(i);
                    long dialogId = DialogObject.getPeerDialogId(userStories.peer);
                    if (storiesListPlaceProvider.onlyUnreadStories && !storiesController.hasUnreadStories(dialogId)) {
                        continue;
                    }
                    if (!dialogs.contains(dialogId)) {
                        dialogs.add(dialogId);
                        changed = true;
                    }
                }
                if (changed) {
                    storiesViewPager.getAdapter().notifyDataSetChanged();
                }
            }
            if (selfStoryViewsView != null) {
                selfStoryViewsView.selfStoriesPreviewView.update();
            }
        } else if (id == NotificationCenter.openArticle || id == NotificationCenter.articleClosed) {
            updatePlayingMode();
            if (id == NotificationCenter.openArticle) {
                if (playerHolder != null) {
                    playerSavedPosition = playerHolder.currentPosition;
                    playerHolder.release(null);
                    playerHolder = null;
                } else {
                    playerSavedPosition = 0;
                }
            } else if (!paused) {
                PeerStoriesView peerView = getCurrentPeerView();
                if (peerView != null) {
                    getCurrentPeerView().updatePosition();
                }
            }
        } else if (id == NotificationCenter.storyDeleted) {
            final long dialogId = (long) args[0];
            final int storyId = (int) args[1];
            if (singleStory != null && singleStory.dialogId == dialogId && singleStory.id == storyId) {
                singleStoryDeleted = true;
            }
        }
    }

    public void saveDraft(long dialogId, TL_stories.StoryItem storyItem, CharSequence text) {
        if (dialogId == 0 || storyItem == null) {
            return;
        }
        replyDrafts.put(draftHash(dialogId, storyItem), text);
    }

    public CharSequence getDraft(long dialogId, TL_stories.StoryItem storyItem) {
        if (dialogId == 0 || storyItem == null) {
            return "";
        }
        return replyDrafts.get(draftHash(dialogId, storyItem), "");
    }

    public void clearDraft(long dialogId, TL_stories.StoryItem storyItem) {
        if (dialogId == 0 || storyItem == null) {
            return;
        }
        replyDrafts.remove(draftHash(dialogId, storyItem));
    }

    private long draftHash(long dialogId, TL_stories.StoryItem oldStoryItem) {
        return dialogId + (dialogId >> 16) + ((long) oldStoryItem.id << 16);
    }

    public void onResume() {
        paused = false;
        if (!ArticleViewer.getInstance().isVisible()) {
            PeerStoriesView peerView = getCurrentPeerView();
            if (peerView != null) {
                getCurrentPeerView().updatePosition();
            }
        }
        if (storiesIntro != null) {
            storiesIntro.startAnimation(false);
        }
        if (LiveStoryPipOverlay.isVisible()) {
            LiveStoryPipOverlay.dismiss();
        }
    }

    public void onPause() {
        paused = true;
        if (playerHolder != null) {
            playerHolder.release(null);
            playerHolder = null;
        }
        if (pipLiveView == null) {
            if (liveView != null) {
                liveView.setScope(0, null);
            }
            if (livePlayer != null) {
                if (livePlayer.outgoing) {
                    livePlayer.setDisplaySink(null);
                } else {
                    livePlayer.destroy();
                }
                livePlayer = null;
            }
        }
        if (storiesIntro != null) {
            storiesIntro.stopAnimation();
        }
    }

    public interface PlaceProvider {
        boolean findView(long dialogId, int messageId, int storyId, int type, TransitionViewHolder holder);

        default void preLayout(long currentDialogId, int messageId, Runnable o) {
            o.run();
        }

        default void loadNext(boolean forward) {

        }
    }

    public interface HolderDrawAbove {
        void draw(Canvas canvas, RectF bounds, float alpha, boolean opening);
    }

    public interface HolderClip {
        void clip(Canvas canvas, RectF bounds, float alpha, boolean opening);
    }

    public static class TransitionViewHolder {
        public View view;
        public ImageReceiver avatarImage;
        public ImageReceiver storyImage;
        public RadialProgress radialProgressUpload;
        public HolderDrawAbove drawAbove;
        public HolderClip drawClip;
        public View clipParent;
        public float clipTop;
        public float clipBottom;
        public Paint bgPaint;
        public float alpha = 1;
        public boolean isLive;
        public ImageReceiver crossfadeToAvatarImage;
        public StoriesUtilities.AvatarStoryParams params;
        public boolean checkParentScale;
        public int storyId;

        public Integer getAvatarImageRoundRadius() {
            if (avatarImage != null) {
                float scale = 1f;
                if (checkParentScale && view != null && view.getParent() != null) {
                    scale = ((ViewGroup) (view.getParent())).getScaleY();
                }
                return (int) (avatarImage.getRoundRadius()[0] * scale);
            }
            return null;
        }

        public void clear() {
            view = null;
            params = null;
            avatarImage = null;
            storyImage = null;
            drawAbove = null;
            drawClip = null;
            clipParent = null;
            radialProgressUpload = null;
            isLive = false;
            crossfadeToAvatarImage = null;
            clipTop = 0;
            clipBottom = 0;
            storyId = 0;
            bgPaint = null;
            alpha = 1;
        }
    }

    public class VideoPlayerHolder extends VideoPlayerHolderBase {

        boolean logBuffering;

        public VideoPlayerHolder(SurfaceView surfaceView, TextureView textureView) {
            if (USE_SURFACE_VIEW) {
                with(surfaceView);
            } else {
                with(textureView);
            }
        }


        @Override
        public boolean needRepeat() {
            return isCaptionPartVisible;
        }

        @Override
        public void onRenderedFirstFrame() {
            if (currentPlayerScope == null) {
                return;
            }
            firstFrameRendered = currentPlayerScope.firstFrameRendered = true;
            currentPlayerScope.invalidate();
            if (paused && surfaceView != null) {
                prepareStub();
            }
        }

        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == ExoPlayer.STATE_READY || playbackState == ExoPlayer.STATE_BUFFERING) {
                if (firstFrameRendered && playbackState == ExoPlayer.STATE_BUFFERING) {
                    logBuffering = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        final PeerStoriesView storiesView = getCurrentPeerView();
                        if (storiesView != null && storiesView.currentStory.storyItem != null) {
                            FileLog.d("StoryViewer displayed story buffering dialogId=" + storiesView.getCurrentPeer() + " storyId=" + storiesView.currentStory.storyItem.id);
                        }
                    });
                }
                if (logBuffering && playbackState == ExoPlayer.STATE_READY) {
                    logBuffering = false;
                    AndroidUtilities.runOnUIThread(() -> {
                        final PeerStoriesView storiesView = getCurrentPeerView();
                        if (storiesView != null && storiesView.currentStory.storyItem != null) {
                            FileLog.d("StoryViewer displayed story playing dialogId=" + storiesView.getCurrentPeer() + " storyId=" + storiesView.currentStory.storyItem.id);
                        }
                    });
                }
            }
        }
    }

    private void updatePipSource() {
//        if (pipIsAvailable() != (pipSource != null)) {
//            if (pipIsAvailable()) {
//                final PeerStoriesView peerView = getCurrentPeerView();
//                final Activity activity = AndroidUtilities.findActivity(fragment.getContext());
//                if (PipUtils.checkPermissions(activity) == PipPermissions.PIP_GRANTED_PIP && peerView != null && peerView.storyContainer != null) {
//                    pipSource = new PipSource.Builder(activity, this)
//                        .setTagPrefix("live-story-pip")
//                        .setPriority(1)
//                        .setCornerRadius(dp(10))
//                        .setContentView(peerView.storyContainer)
//                        .build();
//                } else if (pipSource != null) {
//                    pipSource.destroy();
//                    pipSource = null;
//                }
//            } else {
//                if (pipSource != null) {
//                    pipSource.destroy();
//                    pipSource = null;
//                }
//            }
//        }
    }

    public void switchToPip() {
        if (livePlayer == null || fragment == null || liveView == null) {
            return;
        }
        final Activity activity = AndroidUtilities.findActivity(fragment.getContext());
        if (PipUtils.checkAnyPipPermissions(activity)) {
            LiveStoryPipOverlay.show(activity, livePlayer);
            dismiss();
        }
    }

    @Override
    public boolean pipIsAvailable() {
        if (fragment == null) return false;
        return fragment != null && getCurrentPeerView() != null && AndroidUtilities.findActivity(fragment.getContext()) != null && livePlayer != null && liveView != null && !isClosed;
    }

    @Override
    public Bitmap pipCreatePrimaryWindowViewBitmap() {
        if (liveView == null || !liveView.isAvailable()) {
            return null;
        }
        return liveView.getBitmap();
    }

    @Override
    public Bitmap pipCreatePictureInPictureViewBitmap() {
        if (pipLiveView == null || !pipLiveView.isAvailable()) {
            return null;
        }
        return pipLiveView.getBitmap();
    }

    @Override
    public View pipCreatePictureInPictureView() {
        pipLiveView = new LivePlayerView(liveView.getContext(), currentAccount, false);
        return pipLiveView;
    }

    @Override
    public void pipHidePrimaryWindowView(Runnable firstFrameCallback) {
        if (pipLiveView != null) {
            pipLiveView.setOnFirstFrameCallback(firstFrameCallback);

            livePlayer.setDisplaySink(pipLiveView.getSink());
        }

        if (ATTACH_TO_FRAGMENT) {
            AndroidUtilities.removeFromParent(windowView);
        } else {
            windowManager.removeView(windowView);
        }
        windowView.invalidate();
    }

    @Override
    public void pipShowPrimaryWindowView(Runnable firstFrameCallback) {
        if (pipLiveView != null) {
            pipLiveView.setOnFirstFrameCallback(firstFrameCallback);
        }
        if (ATTACH_TO_FRAGMENT) {
            AndroidUtilities.removeFromParent(windowView);
            fragment.getLayoutContainer().addView(windowView);
        } else {
            windowManager.addView(windowView, windowLayoutParams);
        }

        if (pipLiveView != null) {
            pipLiveView.release();
            pipLiveView = null;
        }

//        windowViewSkipRender = false;
        windowView.invalidate();

        livePlayer.setDisplaySink(liveView.getSink());
    }
}
