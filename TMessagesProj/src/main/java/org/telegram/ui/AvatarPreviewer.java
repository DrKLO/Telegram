package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

public class AvatarPreviewer {

    private static AvatarPreviewer INSTANCE;

    public static AvatarPreviewer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AvatarPreviewer();
        }
        return INSTANCE;
    }

    public static boolean hasVisibleInstance() {
        return INSTANCE != null && INSTANCE.visible;
    }

    public static boolean canPreview(Data data) {
        return data != null && (data.imageLocation != null || data.thumbImageLocation != null);
    }

    private ViewGroup view;
    private Context context;
    private WindowManager windowManager;
    private Callback callback;
    private Layout layout;
    private boolean visible;

    public void show(ViewGroup parentContainer, Data data, Callback callback) {
        Preconditions.checkNotNull(parentContainer);
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(callback);

        final Context context = parentContainer.getContext();

        if (this.view != parentContainer) {
            close();
            this.view = parentContainer;
            this.context = context;
            this.windowManager = ContextCompat.getSystemService(context, WindowManager.class);
            this.layout = new Layout(context, callback) {
                @Override
                protected void onHide() {
                    close();
                }
            };
        }

        layout.setData(data);

        if (!visible) {
            if (layout.getParent() != null) {
                windowManager.removeView(layout);
            }
            final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.LAST_APPLICATION_WINDOW,
                    0, PixelFormat.TRANSLUCENT
            );
            if (Build.VERSION.SDK_INT >= 21) {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }
            windowManager.addView(layout, layoutParams);
            parentContainer.requestDisallowInterceptTouchEvent(true);
            visible = true;
        }
    }

    public void close() {
        if (visible) {
            visible = false;
            if (layout.getParent() != null) {
                windowManager.removeView(layout);
            }
            layout.recycle();
            layout = null;
            view.requestDisallowInterceptTouchEvent(false);
            view = null;
            context = null;
            windowManager = null;
            callback = null;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void onTouchEvent(MotionEvent event) {
        if (layout != null) {
            layout.onTouchEvent(event);
        }
    }

    public interface Callback {
        void onMenuClick(MenuItem item);
    }

    public static enum MenuItem {
        OPEN_PROFILE("OpenProfile", R.string.OpenProfile, R.drawable.msg_openprofile),
        OPEN_CHANNEL("OpenChannel2", R.string.OpenChannel2, R.drawable.msg_channel),
        OPEN_GROUP("OpenGroup2", R.string.OpenGroup2, R.drawable.msg_discussion),
        SEND_MESSAGE("SendMessage", R.string.SendMessage, R.drawable.msg_discussion),
        MENTION("Mention", R.string.Mention, R.drawable.msg_mention);

        private final String labelKey;
        private final int labelResId;
        private final int iconResId;

        MenuItem(String labelKey, int labelResId, int iconResId) {
            this.labelKey = labelKey;
            this.labelResId = labelResId;
            this.iconResId = iconResId;
        }
    }

    public static class Data {

        public static Data of(TLRPC.User user, int classGuid, MenuItem... menuItems) {
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG);
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            return new Data(imageLocation, thumbImageLocation, null, null, thumbFilter, null, null, user, menuItems, new UserInfoLoadTask(user, classGuid));
        }

        public static Data of(TLRPC.UserFull userFull, MenuItem... menuItems) {
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(userFull.user, ImageLocation.TYPE_BIG);
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(userFull.user, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            final ImageLocation videoLocation;
            final String videoFileName;
            if (userFull.profile_photo != null && !userFull.profile_photo.video_sizes.isEmpty()) {
                final TLRPC.VideoSize videoSize = userFull.profile_photo.video_sizes.get(0);
                videoLocation = ImageLocation.getForPhoto(videoSize, userFull.profile_photo);
                videoFileName = FileLoader.getAttachFileName(videoSize);
            } else {
                videoLocation = null;
                videoFileName = null;
            }
            final String videoFilter = videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
            return new Data(imageLocation, thumbImageLocation, videoLocation, null, thumbFilter, videoFilter, videoFileName, userFull.user, menuItems, null);
        }

        public static Data of(TLRPC.Chat chat, int classGuid, MenuItem... menuItems) {
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            return new Data(imageLocation, thumbImageLocation, null, null, thumbFilter, null, null, chat, menuItems, new ChatInfoLoadTask(chat, classGuid));
        }

        public static Data of(TLRPC.Chat chat, TLRPC.ChatFull chatFull, MenuItem... menuItems) {
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            final ImageLocation videoLocation;
            final String videoFileName;
            if (chatFull.chat_photo != null && !chatFull.chat_photo.video_sizes.isEmpty()) {
                final TLRPC.VideoSize videoSize = chatFull.chat_photo.video_sizes.get(0);
                videoLocation = ImageLocation.getForPhoto(videoSize, chatFull.chat_photo);
                videoFileName = FileLoader.getAttachFileName(videoSize);
            } else {
                videoLocation = null;
                videoFileName = null;
            }
            final String videoFilter = videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
            return new Data(imageLocation, thumbImageLocation, videoLocation, null, thumbFilter, videoFilter, videoFileName, chat, menuItems, null);
        }

        private final ImageLocation imageLocation;
        private final ImageLocation thumbImageLocation;
        private final ImageLocation videoLocation;
        private final String imageFilter;
        private final String thumbImageFilter;
        private final String videoFilter;
        private final String videoFileName;
        private final Object parentObject;
        private final MenuItem[] menuItems;
        private final InfoLoadTask<?, ?> infoLoadTask;

        private Data(ImageLocation imageLocation, ImageLocation thumbImageLocation, ImageLocation videoLocation, String imageFilter, String thumbImageFilter, String videoFilter, String videoFileName, Object parentObject, MenuItem[] menuItems, InfoLoadTask<?, ?> infoLoadTask) {
            this.imageLocation = imageLocation;
            this.thumbImageLocation = thumbImageLocation;
            this.videoLocation = videoLocation;
            this.imageFilter = imageFilter;
            this.thumbImageFilter = thumbImageFilter;
            this.videoFilter = videoFilter;
            this.videoFileName = videoFileName;
            this.parentObject = parentObject;
            this.menuItems = menuItems;
            this.infoLoadTask = infoLoadTask;
        }
    }

    private static class UserInfoLoadTask extends InfoLoadTask<TLRPC.User, TLRPC.UserFull> {

        public UserInfoLoadTask(TLRPC.User argument, int classGuid) {
            super(argument, classGuid, NotificationCenter.userInfoDidLoad);
        }

        @Override
        protected void load() {
            MessagesController.getInstance(UserConfig.selectedAccount).loadUserInfo(argument, false, classGuid);
        }

        @Override
        protected void onReceiveNotification(Object... args) {
            Long uid = (Long) args[0];
            if (uid == argument.id) {
                onResult((TLRPC.UserFull) args[1]);
            }
        }
    }

    private static class ChatInfoLoadTask extends InfoLoadTask<TLRPC.Chat, TLRPC.ChatFull> {

        public ChatInfoLoadTask(TLRPC.Chat argument, int classGuid) {
            super(argument, classGuid, NotificationCenter.chatInfoDidLoad);
        }

        @Override
        protected void load() {
            MessagesController.getInstance(UserConfig.selectedAccount).loadFullChat(argument.id, classGuid, false);
        }

        @Override
        protected void onReceiveNotification(Object... args) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull != null && chatFull.id == argument.id) {
                onResult(chatFull);
            }
        }
    }

    private static abstract class InfoLoadTask<A, B> {

        private final NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (loading && id == notificationId) {
                    onReceiveNotification(args);
                }
            }
        };

        private final NotificationCenter notificationCenter;

        protected final A argument;
        protected final int classGuid;
        private final int notificationId;

        private Consumer<B> onResult;
        private boolean loading;

        public InfoLoadTask(A argument, int classGuid, int notificationId) {
            this.argument = argument;
            this.classGuid = classGuid;
            this.notificationId = notificationId;
            notificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount);
        }

        public final void load(Consumer<B> onResult) {
            if (!loading) {
                loading = true;
                this.onResult = onResult;
                notificationCenter.addObserver(observer, notificationId);
                load();
            }
        }

        public final void cancel() {
            if (loading) {
                loading = false;
                notificationCenter.removeObserver(observer, notificationId);
            }
        }

        protected final void onResult(B result) {
            if (loading) {
                cancel();
                onResult.accept(result);
            }
        }

        protected abstract void load();

        protected abstract void onReceiveNotification(Object... args);
    }

    private static abstract class Layout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        private static final float ANIM_DURATION = 150f;

        private final int radialProgressSize = AndroidUtilities.dp(64f);
        private final int[] coords = new int[2];
        private final Rect rect = new Rect();

        private final Interpolator interpolator = new AccelerateDecelerateInterpolator();
        private final ColorDrawable backgroundDrawable = new ColorDrawable(0x71000000);
        private final ImageReceiver imageReceiver = new ImageReceiver();
        private final RadialProgress2 radialProgress;
        private final Drawable arrowDrawable;
        private final Callback callback;

        private float progress;
        private boolean showing;
        private long lastUpdateTime;
        private MenuItem[] menuItems;
        private WindowInsets insets;
        private BottomSheet visibleSheet;
        private ValueAnimator moveAnimator;
        private float moveProgress; // [-1; 0]
        private float downY = -1;

        private String videoFileName;
        private InfoLoadTask<?, ?> infoLoadTask;
        private ValueAnimator progressHideAnimator;
        private ValueAnimator progressShowAnimator;
        private boolean showProgress;
        private boolean recycled;

        public Layout(@NonNull Context context, Callback callback) {
            super(context);
            this.callback = callback;
            setWillNotDraw(false);
            setFitsSystemWindows(true);
            imageReceiver.setAspectFit(true);
            imageReceiver.setInvalidateAll(true);
            imageReceiver.setRoundRadius(AndroidUtilities.dp(6));
            imageReceiver.setParentView(this);
            radialProgress = new RadialProgress2(this);
            radialProgress.setOverrideAlpha(0.0f);
            radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, false, false);
            radialProgress.setColors(0x42000000, 0x42000000, Color.WHITE, Color.WHITE);
            arrowDrawable = ContextCompat.getDrawable(context, R.drawable.preview_arrow);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (!showProgress || TextUtils.isEmpty(videoFileName)) {
                return;
            }
            if (id == NotificationCenter.fileLoaded) {
                final String fileName = (String) args[0];
                if (TextUtils.equals(fileName, videoFileName)) {
                    radialProgress.setProgress(1f, true);
                }
            } else if (id == NotificationCenter.fileLoadProgressChanged) {
                String fileName = (String) args[0];
                if (TextUtils.equals(fileName, videoFileName)) {
                    if (radialProgress != null) {
                        Long loadedSize = (Long) args[1];
                        Long totalSize = (Long) args[2];
                        float progress = Math.min(1f, loadedSize / (float) totalSize);
                        radialProgress.setProgress(progress, true);
                    }
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!showing) {
                return false;
            }
            if (moveAnimator == null) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    downY = -1;
                    setShowing(false);
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (downY < 0) {
                        downY = event.getY();
                    } else {
                        moveProgress = Math.max(-1, Math.min(0f, (event.getY() - downY) / AndroidUtilities.dp(56)));
                        if (moveProgress == -1) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            moveAnimator = ValueAnimator.ofFloat(moveProgress, 0);
                            moveAnimator.setDuration(200);
                            moveAnimator.addUpdateListener(a -> {
                                moveProgress = (float) a.getAnimatedValue();
                                invalidate();
                            });
                            moveAnimator.start();
                            showBottomSheet();
                        }
                        invalidate();
                    }
                }
            }
            return true;
        }

        private void showBottomSheet() {
            final CharSequence[] labels = new CharSequence[menuItems.length];
            final int[] icons = new int[menuItems.length];
            for (int i = 0; i < menuItems.length; i++) {
                labels[i] = LocaleController.getString(menuItems[i].labelKey, menuItems[i].labelResId);
                icons[i] = menuItems[i].iconResId;
            }
            visibleSheet = new BottomSheet.Builder(getContext())
                    .setItems(labels, icons, (dialog, which) -> {
                        callback.onMenuClick(menuItems[which]);
                        setShowing(false);
                    })
                    .setDimBehind(false);
            visibleSheet.setOnDismissListener(dialog -> {
                visibleSheet = null;
                setShowing(false);
            });
            visibleSheet.show();
        }

        @Override
        public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            this.insets = insets;
            invalidateSize();
            return insets.consumeStableInsets();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            invalidateSize();
        }

        public void invalidateSize() {
            final int width = getWidth();
            final int height = getHeight();

            if (width == 0 || height == 0) {
                return;
            }

            backgroundDrawable.setBounds(0, 0, width, height);

            final int padding = AndroidUtilities.dp(8);

            int lPadding = padding, rPadding = padding, vPadding = padding;

            if (Build.VERSION.SDK_INT >= 21) {
                lPadding += insets.getStableInsetLeft();
                rPadding += insets.getStableInsetRight();
                vPadding += Math.max(insets.getStableInsetTop(), insets.getStableInsetBottom());
            }

            final int arrowWidth = arrowDrawable.getIntrinsicWidth();
            final int arrowHeight = arrowDrawable.getIntrinsicHeight();
            final int arrowPadding = AndroidUtilities.dp(24);

            final int w = width - (lPadding + rPadding);
            final int h = height - vPadding * 2;

            final int size = Math.min(w, h);
            final int vOffset = arrowPadding + arrowHeight / 2;
            final int x = (w - size) / 2 + lPadding;
            final int y = (h - size) / 2 + vPadding + (w > h ? vOffset : 0);
            imageReceiver.setImageCoords(x, y, size, size - (w > h ? vOffset : 0));

            final int cx = (int) imageReceiver.getCenterX();
            final int cy = (int) imageReceiver.getCenterY();
            radialProgress.setProgressRect(cx - radialProgressSize / 2, cy - radialProgressSize / 2, cx + radialProgressSize / 2, cy + radialProgressSize / 2);

            final int arrowX = x + size / 2;
            final int arrowY = y - arrowPadding;
            arrowDrawable.setBounds(arrowX - arrowWidth / 2, arrowY - arrowHeight / 2, arrowX + arrowWidth / 2, arrowY + arrowHeight / 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long newTime = AnimationUtils.currentAnimationTimeMillis();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;

            if (showing && progress < 1f) {
                progress += dt / ANIM_DURATION;
                if (progress < 1f) {
                    postInvalidateOnAnimation();
                } else {
                    progress = 1f;
                }
            } else if (!showing && progress > 0f) {
                progress -= dt / ANIM_DURATION;
                if (progress > 0f) {
                    postInvalidateOnAnimation();
                } else {
                    progress = 0f;
                    onHide();
                }
            }

            final float interpolatedProgress = interpolator.getInterpolation(progress);
            backgroundDrawable.setAlpha((int) (180 * interpolatedProgress));
            backgroundDrawable.draw(canvas);
            if (interpolatedProgress < 1.0f) {
                canvas.scale(AndroidUtilities.lerp(0.95f, 1.0f, interpolatedProgress), AndroidUtilities.lerp(0.95f, 1.0f, interpolatedProgress), imageReceiver.getCenterX(), imageReceiver.getCenterY());
            }

            final int statusBarHeight = Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0;
            final int navBarHeight = Build.VERSION.SDK_INT >= 21 ? insets.getStableInsetBottom() : 0;
            final int sheetHeight = menuItems.length * AndroidUtilities.dp(48) + AndroidUtilities.dp(16);
            final float maxBottom = getHeight() - (navBarHeight + sheetHeight + AndroidUtilities.dp(16));
            final float translationY = Math.min(0, maxBottom - imageReceiver.getImageY2());
            if (imageReceiver.getImageY() + translationY < statusBarHeight) {
                canvas.translate(0, moveProgress * AndroidUtilities.dp(16));
            } else {
                canvas.translate(0, translationY + moveProgress * AndroidUtilities.dp(16));
            }

            imageReceiver.setAlpha(interpolatedProgress);
            imageReceiver.draw(canvas);

            if (showProgress) {
                final Drawable drawable = imageReceiver.getDrawable();
                if (drawable instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) drawable).getDurationMs() > 0) {
                    if (progressShowAnimator != null) {
                        progressShowAnimator.cancel();
                        if (radialProgress.getProgress() < 1f) {
                            radialProgress.setProgress(1f, true);
                        }
                        progressHideAnimator = ValueAnimator.ofFloat((Float) progressShowAnimator.getAnimatedValue(), 0);
                        progressHideAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                showProgress = false;
                                invalidate();
                            }
                        });
                        progressHideAnimator.addUpdateListener(a -> invalidate());
                        progressHideAnimator.setDuration(250);
                        progressHideAnimator.start();
                    } else {
                        showProgress = false;
                    }
                } else if (progressShowAnimator == null) {
                    progressShowAnimator = ValueAnimator.ofFloat(0f, 1f);
                    progressShowAnimator.addUpdateListener(a -> invalidate());
                    progressShowAnimator.setStartDelay(250);
                    progressShowAnimator.setDuration(250);
                    progressShowAnimator.start();
                }
                if (progressHideAnimator != null) {
                    radialProgress.setOverrideAlpha((Float) progressHideAnimator.getAnimatedValue());
                    radialProgress.draw(canvas);
                } else if (progressShowAnimator != null) {
                    radialProgress.setOverrideAlpha((Float) progressShowAnimator.getAnimatedValue());
                    radialProgress.draw(canvas);
                }
            }
            if (moveAnimator != null) {
                arrowDrawable.setAlpha((int) ((1f - moveAnimator.getAnimatedFraction()) * 255));
            } else {
                arrowDrawable.setAlpha((int) (255 * interpolatedProgress));
            }
            arrowDrawable.draw(canvas);
        }

        public void setData(Data data) {
            menuItems = data.menuItems;
            showProgress = data.videoLocation != null;
            videoFileName = data.videoFileName;

            recycleInfoLoadTask();
            if (data.infoLoadTask != null) {
                infoLoadTask = data.infoLoadTask;
                infoLoadTask.load(result -> {
                    if (!recycled) {
                        if (result instanceof TLRPC.UserFull) {
                            setData(Data.of((TLRPC.UserFull) result, data.menuItems));
                        } else if (result instanceof TLRPC.ChatFull) {
                            setData(Data.of((TLRPC.Chat) data.infoLoadTask.argument, (TLRPC.ChatFull) result, data.menuItems));
                        }
                    }
                });
            }

            imageReceiver.setCurrentAccount(UserConfig.selectedAccount);
            imageReceiver.setImage(data.videoLocation, data.videoFilter, data.imageLocation, data.imageFilter, data.thumbImageLocation, data.thumbImageFilter, null, 0, null, data.parentObject, 1);
            setShowing(true);
        }

        private void setShowing(boolean showing) {
            if (this.showing != showing) {
                this.showing = showing;
                lastUpdateTime = AnimationUtils.currentAnimationTimeMillis();
                invalidate();
            }
        }

        public void recycle() {
            recycled = true;
            if (moveAnimator != null) {
                moveAnimator.cancel();
            }
            if (visibleSheet != null) {
                visibleSheet.cancel();
            }
            recycleInfoLoadTask();
        }

        private void recycleInfoLoadTask() {
            if (infoLoadTask != null) {
                infoLoadTask.cancel();
                infoLoadTask = null;
            }
        }

        protected abstract void onHide();
    }
}
