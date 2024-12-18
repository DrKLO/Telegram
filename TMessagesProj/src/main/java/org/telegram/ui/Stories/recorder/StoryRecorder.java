package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.EDIT_MODE_NONE;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.PAGE_CAMERA;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.PAGE_COVER;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.PAGE_PREVIEW;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.STORY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.DialogStoriesCell;
import org.telegram.ui.Stories.PeerStoriesView;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoryViewer;
import org.telegram.ui.Stories.StoryWaveEffectView;
import org.telegram.ui.WrappedResourceProvider;

import java.util.ArrayList;

public class StoryRecorder implements NotificationCenter.NotificationCenterDelegate {

    public interface ClosingViewProvider {
        void preLayout(long dialogId, Runnable runnable);

        SourceView getView(long dialogId);
    }

    // region Static StoryRecorder interaction
    private static StoryRecorder instance;

    public static StoryRecorder getInstance(Activity activity, int currentAccount) {
        if (instance != null && (instance.activity != activity || instance.currentAccount != currentAccount)) {
            instance.close(false);
            instance = null;
        }
        if (instance == null) {
            instance = new StoryRecorder(activity, currentAccount);
        }
        return instance;
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.close(false);
        }
        instance = null;
    }

    public static boolean isVisible() {
        return instance != null && instance.isShown;
    }

    public static void onResume() {
        if (instance != null) {
            instance.onResumeInternal();
        }
    }

    public static void onPause() {
        if (instance != null) {
            instance.onPauseInternal();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }
    // endregion

    // Account info
    private final int currentAccount;
    private long selectedDialogId;
    private boolean canChangePeer;

    // System components
    private final Activity activity;
    private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();
    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams;

    // Views
    private MediaRecorderView mediaRecorderView;
    private StoryWaveEffectView waveEffect;
    private StoryPrivacyBottomSheet privacySheet;

    // Result fields
    private boolean wasSend;
    private long wasSendPeer = 0;

    // Source params
    private final RectF fromRect = new RectF();
    private ClosingViewProvider closingSourceProvider;
    private SourceView fromSourceView;
    private float fromRounding;

    // Opening fields
    private float openProgress;
    private int openType;
    private ValueAnimator openCloseAnimator;

    // State
    private boolean isShown;
    private boolean shownLimitReached;

    // Listeners
    private Runnable closeListener;
    private Runnable onCloseListener;
    private Runnable onFullyOpenListener;
    private Utilities.Callback4<Long, Runnable, Boolean, Long> onClosePrepareListener;

    public void setOnCloseListener(Runnable listener) {
        onCloseListener = listener;
    }

    public void setOnFullyOpenListener(Runnable listener) {
        onFullyOpenListener = listener;
    }

    public void setOnPrepareCloseListener(Utilities.Callback4<Long, Runnable, Boolean, Long> listener) {
        onClosePrepareListener = listener;
    }

    public StoryRecorder whenSent(Runnable listener) {
        closeListener = listener;
        return this;
    }

    public StoryRecorder closeToWhenSent(ClosingViewProvider closingSourceProvider) {
        this.closingSourceProvider = closingSourceProvider;
        return this;
    }

    private StoryRecorder(Activity activity, int currentAccount) {
        this.activity = activity;
        this.currentAccount = currentAccount;

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowLayoutParams.flags = (
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        initViews();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesLimitUpdate && mediaRecorderView.getCurrentPage() == PAGE_CAMERA) {
            StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
            if (storyLimit != null && storyLimit.active(currentAccount) && (mediaRecorderView.getOutputEntry() == null
                    || mediaRecorderView.getOutputEntry().botId == 0)) {
                showLimitReachedSheet(storyLimit, true);
            }
        }
    }

    private void addNotificationObservers() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesLimitUpdate);
    }

    private void removeNotificationObservers() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesLimitUpdate);
    }

    public StoryRecorder selectedPeerId(long dialogId) {
        this.selectedDialogId = dialogId;
        mediaRecorderView.setDialogId(dialogId);
        return this;
    }

    public StoryRecorder canChangePeer(boolean b) {
        this.canChangePeer = b;
        return this;
    }

    // Pending bot params
    private long botId = -1;
    private String botLang = null;

    public static class SourceView {
        int type = 0;
        float rounding;
        RectF screenRect = new RectF();
        Drawable backgroundDrawable;
        ImageReceiver backgroundImageReceiver;
        boolean hasShadow;
        Paint backgroundPaint;
        Drawable iconDrawable;
        int iconSize;
        View view;
        boolean isVisible;

        protected void show(boolean sent) {
        }

        protected void hide() {
        }

        protected void drawAbove(Canvas canvas, float alpha) {
        }

        public static SourceView fromAvatarImage(ProfileActivity.AvatarImageView avatarImage, boolean isForum) {
            if (avatarImage == null || avatarImage.getRootView() == null) {
                return null;
            }
            float scale = ((View) avatarImage.getParent()).getScaleX();
            final float size = avatarImage.getImageReceiver().getImageWidth() * scale;
            final float rounding = isForum ? size * 0.32f : size;
            SourceView src = new SourceView() {
                @Override
                protected void show(boolean sent) {
                    avatarImage.drawAvatar = true;
                    avatarImage.invalidate();
                    avatarImage.post(() -> isVisible = true);
                }

                @Override
                protected void hide() {
                    avatarImage.drawAvatar = false;
                    avatarImage.post(() -> isVisible = false);
                    avatarImage.invalidate();
                }
            };
            final int[] loc = new int[2];
            final float[] locPositon = new float[2];
            avatarImage.getRootView().getLocationOnScreen(loc);
            AndroidUtilities.getViewPositionInParent(avatarImage, (ViewGroup) avatarImage.getRootView(), locPositon);
            final float x = loc[0] + locPositon[0] + avatarImage.getImageReceiver().getImageX() * scale;
            final float y = loc[1] + locPositon[1] + avatarImage.getImageReceiver().getImageY() * scale;

            src.screenRect.set(x, y, x + size, y + size);
            src.backgroundImageReceiver = avatarImage.getImageReceiver();
            src.rounding = rounding;
            return src;
        }

        public static SourceView fromStoryViewer(StoryViewer storyViewer) {
            if (storyViewer == null) {
                return null;
            }
            SourceView src = new SourceView() {
                @Override
                protected void show(boolean sent) {
                    final PeerStoriesView peerView = storyViewer.getCurrentPeerView();
                    if (peerView != null) {
                        peerView.animateOut(false);
                    }
                    if (view != null) {
                        view.setTranslationX(0);
                        view.setTranslationY(0);
                    }
                    isVisible = true;
                }

                @Override
                protected void hide() {
                    final PeerStoriesView peerView = storyViewer.getCurrentPeerView();
                    if (peerView != null) {
                        peerView.animateOut(true);
                    }
                    isVisible = false;
                }
            };
            if (!storyViewer.getStoryRect(src.screenRect)) {
                return null;
            }
            src.type = 1;
            src.rounding = dp(8);
            final PeerStoriesView peerView = storyViewer.getCurrentPeerView();
            if (peerView != null) {
                src.view = peerView.storyContainer;
            }
            return src;
        }

        public static SourceView fromFloatingButton(FrameLayout floatingButton) {
            if (floatingButton == null) {
                return null;
            }
            SourceView src = new SourceView() {
                @Override
                protected void show(boolean sent) {
                    floatingButton.setVisibility(View.VISIBLE);
                    floatingButton.post(() -> isVisible = true);
                }

                @Override
                protected void hide() {
                    floatingButton.post(() -> {
                        floatingButton.setVisibility(View.GONE);
                        isVisible = false;
                    });
                }
            };
            int[] loc = new int[2];
            final View imageView = floatingButton.getChildAt(0);
            imageView.getLocationOnScreen(loc);
            src.screenRect.set(loc[0], loc[1], loc[0] + imageView.getWidth(), loc[1] + imageView.getHeight());
            src.hasShadow = true;
            src.backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            src.backgroundPaint.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            src.iconDrawable = floatingButton.getContext().getResources().getDrawable(R.drawable.story_camera).mutate();
            src.iconSize = AndroidUtilities.dp(56);
            src.rounding = Math.max(src.screenRect.width(), src.screenRect.height()) / 2f;
            return src;
        }

        public static SourceView fromShareCell(ShareDialogCell shareDialogCell) {
            if (shareDialogCell == null) {
                return null;
            }
            BackupImageView imageView = shareDialogCell.getImageView();
            SourceView src = new SourceView() {
                @Override
                protected void show(boolean sent) {
                    imageView.setVisibility(View.VISIBLE);
                    isVisible = false;
                }

                @Override
                protected void hide() {
                    imageView.post(() -> {
                        imageView.setVisibility(View.GONE);
                        isVisible = true;
                    });
                }
            };
            int[] loc = new int[2];
            imageView.getLocationOnScreen(loc);
            src.screenRect.set(loc[0], loc[1], loc[0] + imageView.getWidth(), loc[1] + imageView.getHeight());
            src.backgroundDrawable = new ShareDialogCell.RepostStoryDrawable(imageView.getContext(), null, false, shareDialogCell.resourcesProvider);
//            src.hasShadow = false;
//            src.backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//            src.backgroundPaint.setColor(Theme.getColor(Theme.key_chats_actionBackground));
//            src.iconDrawable = shareDialogCell.getContext().getResources().getDrawable(R.drawable.large_repost_story).mutate();
//            src.iconSize = AndroidUtilities.dp(30);
            src.rounding = Math.max(src.screenRect.width(), src.screenRect.height()) / 2f;
            return src;
        }

        public static SourceView fromStoryCell(DialogStoriesCell.StoryCell storyCell) {
            if (storyCell == null || storyCell.getRootView() == null) {
                return null;
            }
            final float size = storyCell.avatarImage.getImageWidth();
            final float radius = size / 2f;
            SourceView src = new SourceView() {
                @Override
                protected void show(boolean sent) {
                    storyCell.drawAvatar = true;
                    storyCell.invalidate();
                    if (sent) {
                        final int[] loc = new int[2];
                        storyCell.getLocationInWindow(loc);
                        LaunchActivity.makeRipple(loc[0] + storyCell.getWidth() / 2f, loc[1] + storyCell.getHeight() / 2f, 1f);
                    }
                    storyCell.post(() -> isVisible = true);
                }

                @Override
                protected void hide() {
                    storyCell.post(() -> {
                        storyCell.drawAvatar = false;
                        storyCell.invalidate();
                        storyCell.post(() -> isVisible = false);
                    });
                }

                @Override
                protected void drawAbove(Canvas canvas, float alpha) {
                    storyCell.drawPlus(canvas, radius, radius, (float) Math.pow(alpha, 16));
                }
            };
            final int[] loc = new int[2];
            final float[] locPositon = new float[2];
            storyCell.getRootView().getLocationOnScreen(loc);
            AndroidUtilities.getViewPositionInParent(storyCell, (ViewGroup) storyCell.getRootView(), locPositon);
            final float x = loc[0] + locPositon[0] + storyCell.avatarImage.getImageX();
            final float y = loc[1] + locPositon[1] + storyCell.avatarImage.getImageY();

            src.screenRect.set(x, y, x + size, y + size);
            src.backgroundImageReceiver = storyCell.avatarImage;
            src.rounding = Math.max(src.screenRect.width(), src.screenRect.height()) / 2f;
            return src;
        }
    }

    public void replaceSourceView(SourceView sourceView) {
        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
        } else {
            fromSourceView = null;
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }

        mediaRecorderView.setSourceViewRect(fromRounding, fromRect);
    }

    // region Open StoryRecorder from the SourceView
    public void openBot(long botId, String lang_code, SourceView sourceView) {
        this.botId = botId;
        this.botLang = lang_code;
        open(sourceView, true, true);
    }

    public void openBotEntry(long botId, String lang_code, StoryEntry entry, SourceView sourceView) {
        if (isShown || entry == null || mediaRecorderView == null) {
            return;
        }

        this.botId = botId;
        this.botLang = lang_code;

        if (windowManager != null && mediaRecorderView.getParent() == null) {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, mediaRecorderView, windowLayoutParams);
            windowManager.addView(mediaRecorderView, windowLayoutParams);
        }

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }

        mediaRecorderView.setSourceViewRect(fromRounding, fromRect);
        mediaRecorderView.setupState(entry, false, new MediaRecorderView.Params(false, openType));

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mediaRecorderView.navigateTo(PAGE_PREVIEW, false);
        mediaRecorderView.switchToEditMode(EDIT_MODE_NONE, false);

        animateOpenTo(1, true, this::onOpenDone);
        addNotificationObservers();
    }

    public void open(SourceView sourceView) {
        open(sourceView, true, false);
    }

    public void open(SourceView sourceView, boolean animated, boolean isBot) {
        if (isShown || mediaRecorderView == null) {
            return;
        }

        if (windowManager != null && mediaRecorderView.getParent() == null) {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, mediaRecorderView, windowLayoutParams);
            windowManager.addView(mediaRecorderView, windowLayoutParams);
        }

        if (botId == 0) {
            StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
            if (storyLimit != null && storyLimit.active(currentAccount)) {
                showLimitReachedSheet(storyLimit, true);
            }
        }

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }

        mediaRecorderView.setSourceViewRect(fromRounding, fromRect);
        mediaRecorderView.setupState(null, false, new MediaRecorderView.Params(!isBot, openType));

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mediaRecorderView.navigateTo(PAGE_CAMERA, false);
        mediaRecorderView.switchToEditMode(EDIT_MODE_NONE, false);

        animateOpenTo(1, animated, this::onOpenDone);

        addNotificationObservers();
    }

    public void openEdit(SourceView sourceView, StoryEntry entry, long time, boolean animated) {
        if (isShown || mediaRecorderView == null) {
            return;
        }

        if (windowManager != null && mediaRecorderView.getParent() == null) {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, mediaRecorderView, windowLayoutParams);
            windowManager.addView(mediaRecorderView, windowLayoutParams);
        }

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }

        mediaRecorderView.setSourceViewRect(fromRounding, fromRect);
        mediaRecorderView.setupState(entry, false, new MediaRecorderView.Params(true, openType));

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mediaRecorderView.navigateToPreviewWithPlayerAwait(() -> {
            animateOpenTo(1, animated, this::onOpenDone);
        }, time);
        mediaRecorderView.navigateTo(entry.isEditingCover ? PAGE_COVER : PAGE_PREVIEW, false);
        mediaRecorderView.switchToEditMode(EDIT_MODE_NONE, false);

        addNotificationObservers();
    }

    public void openForward(SourceView sourceView, StoryEntry entry, long time, boolean animated) {
        if (isShown || mediaRecorderView == null) {
            return;
        }

        if (windowManager != null && mediaRecorderView.getParent() == null) {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, mediaRecorderView, windowLayoutParams);
            windowManager.addView(mediaRecorderView, windowLayoutParams);
        }

        StoryPrivacySelector.applySaved(currentAccount, entry);

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }

        mediaRecorderView.setSourceViewRect(fromRounding, fromRect);
        mediaRecorderView.setupState(entry, false, new MediaRecorderView.Params(true, openType));

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mediaRecorderView.navigateToPreviewWithPlayerAwait(() -> {
            animateOpenTo(1, animated, this::onOpenDone);
        }, time);
        mediaRecorderView.navigateTo(PAGE_PREVIEW, false);
        mediaRecorderView.switchToEditMode(EDIT_MODE_NONE, false);

        addNotificationObservers();
    }

    public void openRepost(SourceView sourceView, StoryEntry entry) {
        if (isShown || mediaRecorderView == null) {
            return;
        }

        if (windowManager != null && mediaRecorderView.getParent() == null) {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, mediaRecorderView, windowLayoutParams);
            windowManager.addView(mediaRecorderView, windowLayoutParams);
        }

        StoryPrivacySelector.applySaved(currentAccount, entry);

        if (botId == 0) {
            StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
            if (storyLimit != null && storyLimit.active(currentAccount)) {
                showLimitReachedSheet(storyLimit, true);
            }
        }

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }

        mediaRecorderView.setSourceViewRect(fromRounding, fromRect);
        boolean isVideo = entry != null && entry.isRepostMessage && entry.isVideo;
        mediaRecorderView.setupState(entry, isVideo, new MediaRecorderView.Params(true, openType));

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mediaRecorderView.navigateTo(PAGE_PREVIEW, false);
        mediaRecorderView.switchToEditMode(EDIT_MODE_NONE, false);
        animateOpenTo(1, true, this::onOpenDone);

        addNotificationObservers();
    }
    // endregion

    // region Open-Close interaction
    private boolean prepareClosing = false;

    public void close(boolean animated) {
        if (!isShown) {
            return;
        }

        if (privacySheet != null) {
            privacySheet.dismiss();
            privacySheet = null;
        }

        if (onClosePrepareListener != null && mediaRecorderView != null) {
            if (prepareClosing) {
                return;
            }
            prepareClosing = true;
            long pos = mediaRecorderView.close(animated);
            onClosePrepareListener.run(pos, () -> {
                onClosePrepareListener = null;
                prepareClosing = false;
                close(animated);
            }, wasSend, wasSendPeer);
            return;
        } else if (mediaRecorderView != null) {
            mediaRecorderView.close(animated);
        }

        animateOpenTo(0, animated, this::onCloseDone);
        if (openType == 1 || openType == 0) {
            mediaRecorderView.setBackgroundColor(0x00000000);
        }

        removeNotificationObservers();
    }

    private void animateOpenTo(final float value, boolean animated, Runnable onDone) {
        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        if (animated) {
            notificationsLocker.lock();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            mediaRecorderView.saveDismissProgress();
            openCloseAnimator = ValueAnimator.ofFloat(openProgress, value);
            openCloseAnimator.addUpdateListener(anm -> {
                openProgress = (float) anm.getAnimatedValue();
                mediaRecorderView.setOpenProgress(openProgress);
                mediaRecorderView.checkBackgroundVisibility();
                mediaRecorderView.invalidate();
                if (openProgress < .3f && waveEffect != null) {
                    waveEffect.start();
                    waveEffect = null;
                }
            });
            openCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    openProgress = value;
                    mediaRecorderView.resetDismissProgress();
                    mediaRecorderView.setOpenProgress(openProgress);
                    applyOpenProgress();
                    if (onDone != null) {
                        onDone.run();
                    }
                    if (fromSourceView != null && waveEffect != null) {
                        waveEffect.start();
                        waveEffect = null;
                    }
                    notificationsLocker.unlock();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    NotificationCenter.getGlobalInstance().runDelayedNotifications();
                    mediaRecorderView.checkBackgroundVisibility();

                    if (onFullyOpenListener != null) {
                        onFullyOpenListener.run();
                        onFullyOpenListener = null;
                    }

                    mediaRecorderView.invalidate();
                }
            });
            if (value < 1 && wasSend) {
                openCloseAnimator.setDuration(250);
                openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                if (value > 0 || mediaRecorderView.getTranslationY() < AndroidUtilities.dp(20)) {
                    openCloseAnimator.setDuration(300L);
                    openCloseAnimator.setInterpolator(new FastOutSlowInInterpolator());
                } else {
                    openCloseAnimator.setDuration(400L);
                    openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                }
            }
            openCloseAnimator.start();
        } else {
            openProgress = value;
            mediaRecorderView.resetDismissProgress();
            mediaRecorderView.setOpenProgress(openProgress);
            applyOpenProgress();
            mediaRecorderView.invalidate();
            if (onDone != null) {
                onDone.run();
            }
            mediaRecorderView.checkBackgroundVisibility();
        }
    }

    private void onOpenDone() {
        isShown = true;
        mediaRecorderView.onOpened();
    }

    private void onCloseDone() {
        isShown = false;
        AndroidUtilities.unlockOrientation(activity);

        AndroidUtilities.runOnUIThread(() -> {
            if (windowManager != null && mediaRecorderView != null && mediaRecorderView.getParent() != null) {
                windowManager.removeView(mediaRecorderView);
            }
        }, 16);
        if (fromSourceView != null) {
            fromSourceView.show(false);
        }

        mediaRecorderView.onClosed(true);

        if (instance != null) {
            instance.close(false);
        }
        instance = null;

        if (onCloseListener != null) {
            onCloseListener.run();
            onCloseListener = null;
        }
        if (mediaRecorderView != null) {
            Bulletin.removeDelegate(mediaRecorderView);
        }
    }

    private void initViews() {
        mediaRecorderView = new MediaRecorderView(activity, windowManager, windowLayoutParams, resourcesProvider, currentAccount, STORY) {

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (openCloseAnimator != null && openCloseAnimator.isRunning()) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            private final Rect rect = new Rect();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                if (fromSourceView != null && !fromSourceView.isVisible && openType == 0) {
                    final float r = AndroidUtilities.lerp(fromRounding, 0, openProgress);

                    final float alpha = Utilities.clamp(1f - openProgress * 1.5f, 1, 0);
                    final float bcx = rectF.centerX(),
                            bcy = rectF.centerY(),
                            br = Math.min(rectF.width(), rectF.height()) / 2f;
                    if (fromSourceView.backgroundImageReceiver != null) {
                        fromSourceView.backgroundImageReceiver.setImageCoords(rectF);
                        int prevRoundRadius = fromSourceView.backgroundImageReceiver.getRoundRadius()[0];
                        fromSourceView.backgroundImageReceiver.setRoundRadius((int) r);
                        fromSourceView.backgroundImageReceiver.setAlpha(alpha);
                        fromSourceView.backgroundImageReceiver.draw(canvas);
                        fromSourceView.backgroundImageReceiver.setRoundRadius(prevRoundRadius);
                    } else if (fromSourceView.backgroundDrawable != null) {
                        fromSourceView.backgroundDrawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                        fromSourceView.backgroundDrawable.setAlpha((int) (0xFF * alpha * alpha * alpha));
                        fromSourceView.backgroundDrawable.draw(canvas);
                    } else if (fromSourceView.backgroundPaint != null) {
                        if (fromSourceView.hasShadow) {
                            fromSourceView.backgroundPaint.setShadowLayer(dp(2), 0, dp(3), Theme.multAlpha(0x33000000, alpha));
                        }
                        fromSourceView.backgroundPaint.setAlpha((int) (0xFF * alpha));
                        canvas.drawRoundRect(rectF, r, r, fromSourceView.backgroundPaint);
                    }
                    if (fromSourceView.iconDrawable != null) {
                        rect.set(fromSourceView.iconDrawable.getBounds());
                        fromSourceView.iconDrawable.setBounds(
                                (int) (bcx - fromSourceView.iconSize / 2F),
                                (int) (bcy - fromSourceView.iconSize / 2F),
                                (int) (bcx + fromSourceView.iconSize / 2F),
                                (int) (bcy + fromSourceView.iconSize / 2F)
                        );
                        int wasAlpha = fromSourceView.iconDrawable.getAlpha();
                        fromSourceView.iconDrawable.setAlpha((int) (wasAlpha * alpha));
                        fromSourceView.iconDrawable.draw(canvas);
                        fromSourceView.iconDrawable.setBounds(rect);
                        fromSourceView.iconDrawable.setAlpha(wasAlpha);
                    }

                    canvas.save();
                    canvas.translate(fromRect.left, fromRect.top);
                    fromSourceView.drawAbove(canvas, alpha);
                    canvas.restore();
                }
            }
        };

        mediaRecorderView.setMediaRecorderDelegate(new MediaRecorderDelegateImpl());
    }

    private void applyOpenProgress() {
        mediaRecorderView.applyOpenProgress();
        RectF fullRect = mediaRecorderView.fullRectF;

        if (fromSourceView != null && fromSourceView.view != null) {
            fromSourceView.view.setTranslationX((fullRect.left - fromRect.left) * openProgress);
            fromSourceView.view.setTranslationY((fullRect.top - fromRect.top) * openProgress);
        }
    }
    // endregion

    // region Story related interaction
    private void showLimitReachedSheet(StoriesController.StoryLimit storyLimit, boolean closeRecorder) {
        if (shownLimitReached) {
            return;
        }
        final LimitReachedBottomSheet sheet = new LimitReachedBottomSheet(new BaseFragment() {
            @Override
            public boolean isLightStatusBar() {
                return false;
            }

            @Override
            public Activity getParentActivity() {
                return activity;
            }

            @Override
            public Theme.ResourcesProvider getResourceProvider() {
                return new WrappedResourceProvider(resourceProvider) {
                    @Override
                    public void appendColors() {
                        sparseIntArray.append(Theme.key_dialogBackground, 0xFF1F1F1F);
                        sparseIntArray.append(Theme.key_windowBackgroundGray, 0xFF333333);
                    }
                };
            }

            @Override
            public boolean presentFragment(BaseFragment fragment) {
                openPremium();
                return false;
            }
        }, activity, storyLimit.getLimitReachedType(), currentAccount, null);
        sheet.setOnDismissListener(e -> {
            shownLimitReached = false;
            mediaRecorderView.updatePauseReason(7, true);
            if (closeRecorder) {
                close(true);
            }
        });
        mediaRecorderView.updatePauseReason(7, true);
        shownLimitReached = true;
        sheet.show();
    }

    private void openPremium() {
        PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(new BaseFragment() {
            {
                currentAccount = StoryRecorder.this.currentAccount;
            }

            @Override
            public Dialog showDialog(Dialog dialog) {
                dialog.show();
                return dialog;
            }

            @Override
            public Activity getParentActivity() {
                return StoryRecorder.this.activity;
            }

            @Override
            public Theme.ResourcesProvider getResourceProvider() {
                return new WrappedResourceProvider(resourceProvider) {
                    @Override
                    public void appendColors() {
                        sparseIntArray.append(Theme.key_dialogBackground, 0xFF1E1E1E);
                        sparseIntArray.append(Theme.key_windowBackgroundGray, 0xFF000000);
                    }
                };
            }

            @Override
            public boolean isLightStatusBar() {
                return false;
            }
        }, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false);
        sheet.setOnDismissListener(d -> {
            if (mediaRecorderView != null) {
                mediaRecorderView.updatePauseReason(4, false);
            }
        });
        sheet.show();
    }
    // endregion

    private void onResumeInternal() {
        mediaRecorderView.onResume(openCloseAnimator != null && openCloseAnimator.isRunning());
    }

    private void onPauseInternal() {
        mediaRecorderView.onPause();
    }

    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        mediaRecorderView.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
    }

    private class MediaRecorderDelegateImpl implements MediaRecorderView.MediaRecorderDelegate {

        @Override
        public void close(boolean animated) {
            StoryRecorder.this.close(animated);
        }

        @Override
        public void openPremium() {
            StoryRecorder.this.openPremium();
        }

        @Override
        public void setTitle(StoryEntry outputEntry, int page, SimpleTextView view) {
            if (page == PAGE_COVER) {
                view.setText(getString(R.string.RecorderEditCover));
            } else if (page == PAGE_PREVIEW){
                if (outputEntry != null && botId != 0) {
                    view.setText("");
                } else if (outputEntry != null && outputEntry.isEdit) {
                    view.setText(getString(R.string.RecorderEditStory));
                } else if (outputEntry != null && outputEntry.isRepostMessage) {
                    view.setText(getString(R.string.RecorderRepost));
                } else if (outputEntry != null && outputEntry.isRepost) {
                    SpannableStringBuilder title = new SpannableStringBuilder();
                    AvatarSpan span = new AvatarSpan(view, currentAccount, 32);
                    view.setTranslationX(-dp(6));
                    SpannableString avatar = new SpannableString("a");
                    avatar.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (outputEntry.repostPeer instanceof TLRPC.TL_peerUser) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(outputEntry.repostPeer.user_id);
                        span.setUser(user);
                        title.append(avatar).append("  ");
                        title.append(UserObject.getUserName(user));
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-DialogObject.getPeerDialogId(outputEntry.repostPeer));
                        span.setChat(chat);
                        title.append(avatar).append("  ");
                        title.append(chat != null ? chat.title : "");
                    }
                    view.setText(title);
                } else {
                    view.setText(getString(R.string.RecorderNewStory));
                }
            } else {
                view.setText(getString(R.string.RecorderNewStory));
            }
        }

        @Override
        public void onEntryCreated(StoryEntry entry) {
            StoryPrivacySelector.applySaved(currentAccount, entry);
        }

        @Override
        public void processDone(StoryEntry outputEntry) {
            if (outputEntry == null) {
                return;
            }

            if (botId != -1 && botLang != null) {
                outputEntry.botId = botId;
                outputEntry.botLang = botLang;
            }

            if (!outputEntry.isEdit && outputEntry.botId == 0) {
                StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).storiesController.checkStoryLimit();
                if (storyLimit != null && storyLimit.active(currentAccount)) {
                    showLimitReachedSheet(storyLimit, false);
                    return;
                }
            }

            if (outputEntry.isEdit || outputEntry.botId != 0) {
                outputEntry.editedPrivacy = false;
                mediaRecorderView.applyFilter(null);
                upload(true, outputEntry);
            } else {
                if (selectedDialogId != 0) {
                    outputEntry.peer = MessagesController.getInstance(currentAccount).getInputPeer(selectedDialogId);
                }
                mediaRecorderView.updatePauseReason(3, true);
                privacySheet = new StoryPrivacyBottomSheet(activity, outputEntry.period, resourcesProvider)
                        .setValue(outputEntry.privacy)
                        .setPeer(outputEntry.peer)
                        .setCanChangePeer(canChangePeer)
                        .whenDismiss(privacy -> {
                            outputEntry.privacy = privacy;
                        })
                        .allowCover(!mediaRecorderView.hasCollages())
                        .isEdit(false)
                        .setWarnUsers(getUsersFrom(outputEntry.updatedCaption, currentAccount))
                        .whenSelectedPeer(peer -> {
                            outputEntry.peer = peer == null ? new TLRPC.TL_inputPeerSelf() : peer;
                        })
                        .whenSelectedRules((privacy, allowScreenshots, keepInProfile, sendAs, whenDone) -> {
                            mediaRecorderView.updatePauseReason(5, true);
                            outputEntry.privacy = privacy;
                            StoryPrivacySelector.save(currentAccount, outputEntry.privacy);
                            outputEntry.pinned = keepInProfile;
                            outputEntry.allowScreenshots = allowScreenshots;
                            outputEntry.privacyRules.clear();
                            outputEntry.privacyRules.addAll(privacy.rules);
                            outputEntry.editedPrivacy = true;
                            outputEntry.peer = sendAs;
                            mediaRecorderView.applyFilter(() -> {
                                whenDone.run();
                                upload(true, outputEntry);
                            });
                        }, false);

                if (outputEntry.isVideo) {
                    mediaRecorderView.inflateEntryWithCover(() -> {
                        if (privacySheet == null) {
                            return;
                        }
                        privacySheet.setCover(outputEntry.coverBitmap);
                    });
                    privacySheet.setCover(outputEntry.coverBitmap, () -> {
                        if (privacySheet != null) {
                            privacySheet.dismiss();
                        }
                        mediaRecorderView.navigateTo(PAGE_COVER, true);
                    });
                }
                privacySheet.setOnDismissListener(di -> {
                    mediaRecorderView.updatePauseReason(3, false);
                    privacySheet = null;
                });
                privacySheet.show();
            }
        }

        @Override
        public void onCoverEdited(StoryEntry outputEntry) {
            privacySheet.setCover(outputEntry.coverBitmap);
        }

        private boolean preparingUpload = false;

        private void upload(boolean asStory, StoryEntry outputEntry) {
            if (preparingUpload) {
                return;
            }
            preparingUpload = true;
            mediaRecorderView.applyPaintInBackground(() -> {
                preparingUpload = false;
                uploadInternal(asStory, outputEntry);
            });
        }

        private void uploadInternal(boolean asStory, StoryEntry outputEntry) {
            if (outputEntry == null) {
                close(true);
                return;
            }
            mediaRecorderView.destroyPhotoFilterView();
            mediaRecorderView.prepareThumb(outputEntry, false);
            CharSequence[] caption = new CharSequence[]{outputEntry.updatedCaption};
            ArrayList<TLRPC.MessageEntity> captionEntities = MessagesController.getInstance(currentAccount).storyEntitiesAllowed()
                    ? MediaDataController.getInstance(currentAccount).getEntities(caption, true) : new ArrayList<>();
            CharSequence[] pastCaption = new CharSequence[]{outputEntry.caption};
            ArrayList<TLRPC.MessageEntity> pastEntities = MessagesController.getInstance(currentAccount).storyEntitiesAllowed()
                    ? MediaDataController.getInstance(currentAccount).getEntities(pastCaption, true) : new ArrayList<>();
            outputEntry.editedCaption = !TextUtils.equals(outputEntry.caption, caption[0]) || !MediaDataController.entitiesEqual(captionEntities,
                    pastEntities);
            outputEntry.caption = new SpannableString(outputEntry.updatedCaption);
            MessagesController.getInstance(currentAccount).getStoriesController().uploadStory(outputEntry, asStory);
            if (outputEntry.isDraft && !outputEntry.isEdit) {
                MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(outputEntry);
            }
            outputEntry.cancelCheckStickers();

            long sendAsDialogId = UserConfig.getInstance(currentAccount).clientUserId;
            if (outputEntry.peer != null && !(outputEntry.peer instanceof TLRPC.TL_inputPeerSelf)) {
                sendAsDialogId = DialogObject.getPeerDialogId(outputEntry.peer);
            }
            mediaRecorderView.clearOutputEntry();

            wasSend = true;
            wasSendPeer = sendAsDialogId;
            mediaRecorderView.checkBackgroundVisibility(true);

            long finalSendAsDialogId = sendAsDialogId;
            Runnable runnable = () -> {
                if (asStory) {
                    if (fromSourceView != null) {
                        fromSourceView.show(true);
                        fromSourceView = null;
                    }
                    if (closeListener != null) {
                        closeListener.run();
                        closeListener = null;
                    }
                    fromSourceView = closingSourceProvider != null ? closingSourceProvider.getView(finalSendAsDialogId) : null;
                    if (fromSourceView != null) {
                        openType = fromSourceView.type;
                        mediaRecorderView.updateBackground();
                        fromRect.set(fromSourceView.screenRect);
                        fromRounding = fromSourceView.rounding;
                        fromSourceView.hide();

                        if (waveEffect == null && SharedConfig.getDevicePerformanceClass() > SharedConfig.PERFORMANCE_CLASS_AVERAGE
                                && LiteMode.isEnabled(LiteMode.FLAGS_CHAT)) {
                            waveEffect = new StoryWaveEffectView(activity, fromSourceView.screenRect.centerX(),
                                    fromSourceView.screenRect.centerY(), fromSourceView.screenRect.width() / 2f);
                        }
                    }
                    closingSourceProvider = null;

                    if (activity instanceof LaunchActivity) {
                        ((LaunchActivity) activity).drawerLayoutContainer.post(() -> {
                            if (waveEffect != null) {
                                waveEffect.prepare();
                            }
                            close(true);
                        });
                    } else {
                        close(true);
                    }
                } else {
                    close(true);
                }
            };
            if (closingSourceProvider != null) {
                closingSourceProvider.preLayout(sendAsDialogId, runnable);
            } else {
                runnable.run();
            }
            MessagesController.getGlobalMainSettings().edit().putInt("storyhint2", 2).apply();
        }
    }

    // region Util methods
    private static ArrayList<String> getUsersFrom(CharSequence caption, int currentAccount) {
        ArrayList<String> users = new ArrayList<>();
        if (caption instanceof Spanned) {
            URLSpanUserMention[] spans = ((Spanned) caption).getSpans(0, caption.length(), URLSpanUserMention.class);
            for (URLSpanUserMention span : spans) {
                if (span != null) {
                    try {
                        Long userId = Long.parseLong(span.getURL());
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                        if (user != null && !UserObject.isUserSelf(user) && UserObject.getPublicUsername(user) != null && !users.contains(user)) {
                            users.add(UserObject.getPublicUsername(user));
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        }
        if (caption != null) {
            int u = -1;
            for (int i = 0; i < caption.length(); ++i) {
                char c = caption.charAt(i);
                if (c == '@') {
                    u = i + 1;
                } else if (c == ' ') {
                    if (u != -1) {
                        String username = caption.subSequence(u, i).toString();
                        TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(username);
                        if (obj instanceof TLRPC.User && !((TLRPC.User) obj).bot && !UserObject.isUserSelf((TLRPC.User) obj)
                                && ((TLRPC.User) obj).id != 777000 && !UserObject.isReplyUser((TLRPC.User) obj) && !users.contains(username)) {
                            users.add(username);
                        }
                    }
                    u = -1;
                }
            }
            if (u != -1) {
                String username = caption.subSequence(u, caption.length()).toString();
                TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(username);
                if (obj instanceof TLRPC.User && !((TLRPC.User) obj).bot && !UserObject.isUserSelf((TLRPC.User) obj)
                        && ((TLRPC.User) obj).id != 777000 && !UserObject.isReplyUser((TLRPC.User) obj) && !users.contains(username)) {
                    users.add(username);
                }
            }
        }
        return users;
    }

    public static CharSequence cameraBtnSpan(Context context) {
        SpannableString cameraStr = new SpannableString("c");
        Drawable cameraDrawable = context.getResources().getDrawable(R.drawable.story_camera).mutate();
        final int sz = AndroidUtilities.dp(35);
        cameraDrawable.setBounds(-sz / 4, -sz, sz / 4 * 3, 0);
        cameraStr.setSpan(new ImageSpan(cameraDrawable) {
            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                return super.getSize(paint, text, start, end, fm) / 3 * 2;
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                             @NonNull Paint paint) {
                canvas.save();
                canvas.translate(0, (bottom - top) / 2F + dp(1));
                cameraDrawable.setAlpha(paint.getAlpha());
                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
                canvas.restore();
            }
        }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return cameraStr;
    }

    // endregion

    private static void log(String message) {
        Log.i("kirillNay", message);
    }
}
