package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Stories.RoundRectOutlineProvider;

import java.util.Collections;
import java.util.Objects;

public class AvatarPreviewer {

    @SuppressLint("StaticFieldLeak")
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
    private WindowManager windowManager;
    private Layout layout;
    private boolean visible;

    public void show(ViewGroup parentContainer, Theme.ResourcesProvider resourcesProvider, Data data, Callback callback) {
        Objects.requireNonNull(parentContainer);
        Objects.requireNonNull(data);
        Objects.requireNonNull(callback);

        final Context context = parentContainer.getContext();

        if (this.view != parentContainer) {
            close();
            this.view = parentContainer;
            this.windowManager = ContextCompat.getSystemService(context, WindowManager.class);
            this.layout = new Layout(context, resourcesProvider, callback) {
                @Override
                protected void onHideFinish() {
                    if (visible) {
                        visible = false;
                        if (layout.getParent() != null) {
                            windowManager.removeView(layout);
                        }
                        layout.recycle();
                        layout = null;
                        view.requestDisallowInterceptTouchEvent(false);
                        view = null;
                        windowManager = null;
                    }
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
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    0, PixelFormat.TRANSLUCENT
            );
            if (Build.VERSION.SDK_INT >= 21) {
                layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, layout, layoutParams);
            windowManager.addView(layout, layoutParams);
            parentContainer.requestDisallowInterceptTouchEvent(true);
            visible = true;
        }
    }

    public void close() {
        if (visible) {
            this.layout.setShowing(false);
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

    public enum MenuItem {
        OPEN_PROFILE("OpenProfile", R.string.OpenProfile, R.drawable.msg_openprofile),
        OPEN_CHANNEL("OpenChannel2", R.string.OpenChannel2, R.drawable.msg_channel),
        OPEN_GROUP("OpenGroup2", R.string.OpenGroup2, R.drawable.msg_discussion),
        SEND_MESSAGE("SendMessage", R.string.SendMessage, R.drawable.msg_discussion),
        MENTION("Mention", R.string.Mention, R.drawable.msg_mention),
        SEARCH_MESSAGES("AvatarPreviewSearchMessages", R.string.AvatarPreviewSearchMessages, R.drawable.msg_search);

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
            final BitmapDrawable thumb = user != null && user.photo != null ? user.photo.strippedBitmap : null;
            return new Data(imageLocation, thumbImageLocation, null, null, thumbFilter, null, null, thumb, user, menuItems, new UserInfoLoadTask(user, classGuid));
        }

        public static Data of(TLRPC.User user, TLRPC.UserFull userFull, MenuItem... menuItems) {
            ImageLocation imageLocation = ImageLocation.getForUserOrChat(userFull.user, ImageLocation.TYPE_BIG);
            if (imageLocation == null && userFull.profile_photo != null) {
                imageLocation = ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(userFull.profile_photo.sizes, 500), userFull.profile_photo);
            }
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(userFull.user, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            final ImageLocation videoLocation;
            final String videoFileName;
            final BitmapDrawable thumb = user != null && user.photo != null ? user.photo.strippedBitmap : null;
            if (userFull.profile_photo != null && !userFull.profile_photo.video_sizes.isEmpty()) {
                final TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(userFull.profile_photo.video_sizes, 1000);
                videoLocation = ImageLocation.getForPhoto(videoSize, userFull.profile_photo);
                videoFileName = FileLoader.getAttachFileName(videoSize);
            } else {
                videoLocation = null;
                videoFileName = null;
            }
            final String videoFilter = videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
            return new Data(imageLocation, thumbImageLocation, videoLocation, null, thumbFilter, videoFilter, videoFileName, thumb, userFull.user, menuItems, null);
        }

        public static Data of(TLRPC.Chat chat, int classGuid, MenuItem... menuItems) {
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            final BitmapDrawable thumb = chat != null && chat.photo != null ? chat.photo.strippedBitmap : null;
            return new Data(imageLocation, thumbImageLocation, null, null, thumbFilter, null, null, thumb, chat, menuItems, new ChatInfoLoadTask(chat, classGuid));
        }

        public static Data of(TLRPC.Chat chat, TLRPC.ChatFull chatFull, MenuItem... menuItems) {
            final ImageLocation imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
            final ImageLocation thumbImageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            final String thumbFilter = thumbImageLocation != null && thumbImageLocation.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
            final ImageLocation videoLocation;
            final String videoFileName;
            final BitmapDrawable thumb = chat != null && chat.photo != null ? chat.photo.strippedBitmap : null;
            if (chatFull.chat_photo != null && !chatFull.chat_photo.video_sizes.isEmpty()) {
                final TLRPC.VideoSize videoSize = FileLoader.getClosestVideoSizeWithSize(chatFull.chat_photo.video_sizes, 1000);
                videoLocation = ImageLocation.getForPhoto(videoSize, chatFull.chat_photo);
                videoFileName = FileLoader.getAttachFileName(videoSize);
            } else {
                videoLocation = null;
                videoFileName = null;
            }
            final String videoFilter = videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
            return new Data(imageLocation, thumbImageLocation, videoLocation, null, thumbFilter, videoFilter, videoFileName, thumb, chat, menuItems, null);
        }

        private final ImageLocation imageLocation;
        private final ImageLocation thumbImageLocation;
        private final ImageLocation videoLocation;
        private final String imageFilter;
        private final String thumbImageFilter;
        private final String videoFilter;
        private final String videoFileName;
        private final BitmapDrawable thumb;
        private final Object parentObject;
        private final MenuItem[] menuItems;
        private final InfoLoadTask<?, ?> infoLoadTask;

        private Data(ImageLocation imageLocation, ImageLocation thumbImageLocation, ImageLocation videoLocation, String imageFilter, String thumbImageFilter, String videoFilter, String videoFileName, BitmapDrawable thumb, Object parentObject, MenuItem[] menuItems, InfoLoadTask<?, ?> infoLoadTask) {
            this.imageLocation = imageLocation;
            this.thumbImageLocation = thumbImageLocation;
            this.videoLocation = videoLocation;
            this.imageFilter = imageFilter;
            this.thumbImageFilter = thumbImageFilter;
            this.videoFilter = videoFilter;
            this.videoFileName = videoFileName;
            this.thumb = thumb;
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

        private final Interpolator openInterpolator = new OvershootInterpolator(1.02f);
        private final FrameLayout container;
        private final AvatarView avatarView;
        private final ActionBarPopupWindow.ActionBarPopupWindowLayout menu;
        private final Callback callback;
        private final Theme.ResourcesProvider resourcesProvider;

        private AnimatorSet openAnimator;
        private boolean showing;
        private MenuItem[] menuItems;
        private View blurView;
        private boolean preparingBlur;

        private String videoFileName;
        private InfoLoadTask<?, ?> infoLoadTask;
        private boolean recycled;

        public Layout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, Callback callback) {
            super(context);
            this.callback = callback;
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            blurView = new View(context);
            blurView.setOnClickListener(v -> setShowing(false));
            addView(blurView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            container = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int width = right - left - getPaddingLeft() - getPaddingRight();
                    int height = bottom - top - getPaddingTop() - getPaddingBottom();

                    int maxAvatarSize = Math.min(width, height) - dp(16);
                    int minAvatarSize = Math.min(dp(60), maxAvatarSize);

                    int maxMenuHeight = height - minAvatarSize - dp(16 + 24);
                    menu.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(maxMenuHeight, MeasureSpec.AT_MOST));

                    int avatarSize = MathUtils.clamp(height - menu.getMeasuredHeight() - dp(16 + 24), minAvatarSize, maxAvatarSize);
                    avatarView.measure(MeasureSpec.makeMeasureSpec(avatarSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(avatarSize, MeasureSpec.EXACTLY));

                    int verticalOffset = (height - avatarSize - menu.getMeasuredHeight() - dp(16 + 24)) / 2;

                    FrameLayout.LayoutParams avatarLayoutParams = (LayoutParams) avatarView.getLayoutParams();
                    FrameLayout.LayoutParams menuLayoutParams = (LayoutParams) menu.getLayoutParams();

                    avatarLayoutParams.topMargin = verticalOffset + dp(8);
                    menuLayoutParams.topMargin = verticalOffset + dp(8) + avatarSize;

                    super.onLayout(changed, left, top, right, bottom);
                }
            };
            container.setFitsSystemWindows(true);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            avatarView = new AvatarView(context, resourcesProvider);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                avatarView.setElevation(dp(4));
                avatarView.setClipToOutline(true);
            }
            container.addView(avatarView, LayoutHelper.createFrame(0, 0, Gravity.CENTER_HORIZONTAL));

            menu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert, resourcesProvider, 0);
            container.addView(menu, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START));
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
            if (!avatarView.getShowProgress() || TextUtils.isEmpty(videoFileName)) {
                return;
            }
            if (id == NotificationCenter.fileLoaded) {
                final String fileName = (String) args[0];
                if (TextUtils.equals(fileName, videoFileName)) {
                    avatarView.setProgress(1f);
                }
            } else if (id == NotificationCenter.fileLoadProgressChanged) {
                String fileName = (String) args[0];
                if (TextUtils.equals(fileName, videoFileName)) {
                        Long loadedSize = (Long) args[1];
                        Long totalSize = (Long) args[2];
                        float progress = Math.min(1f, loadedSize / (float) totalSize);
                    avatarView.setProgress(progress);
                }
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (getKeyDispatcherState() == null) {
                    return super.dispatchKeyEvent(event);
                }

                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    final KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null) {
                        state.startTracking(event, this);
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    final KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null && state.isTracking(event) && !event.isCanceled()) {
                        setShowing(false);
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            } else {
                return super.dispatchKeyEvent(event);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (w != 0 && h != 0 && showing) {
                blurView.setBackground(null);
                AndroidUtilities.runOnUIThread(this::prepareBlurBitmap);
            }
        }

        private void prepareBlurBitmap() {
            if (preparingBlur) {
                return;
            }
            preparingBlur = true;
            AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
                //noinspection deprecation
                blurView.setBackground(new BitmapDrawable(bitmap));
                preparingBlur = false;
            }, 6f, 7, this, Collections.singletonList(this));
        }

        public void setData(Data data) {
            menuItems = data.menuItems;
            avatarView.setShowProgress(data.videoLocation != null);
            videoFileName = data.videoFileName;

            recycleInfoLoadTask();
            if (data.infoLoadTask != null) {
                infoLoadTask = data.infoLoadTask;
                infoLoadTask.load(result -> {
                    if (!recycled) {
                        if (result instanceof TLRPC.UserFull) {
                            setData(Data.of((TLRPC.User) data.infoLoadTask.argument, (TLRPC.UserFull) result, data.menuItems));
                        } else if (result instanceof TLRPC.ChatFull) {
                            setData(Data.of((TLRPC.Chat) data.infoLoadTask.argument, (TLRPC.ChatFull) result, data.menuItems));
                        }
                    }
                });
            }

            avatarView.setImage(UserConfig.selectedAccount, data.videoLocation, data.videoFilter, data.imageLocation, data.imageFilter, data.thumbImageLocation, data.thumbImageFilter, data.thumb, data.parentObject);

            menu.removeInnerViews();
            for (int i = 0; i < menuItems.length; i++) {
                final MenuItem menuItem = menuItems[i];
                CharSequence label = LocaleController.getString(menuItem.labelKey, menuItem.labelResId);
                ActionBarMenuSubItem item = ActionBarMenuItem.addItem(i == 0, i == menuItems.length - 1, menu, menuItem.iconResId, label, false, resourcesProvider);
                item.setTag(i);
                item.setOnClickListener(v -> {
                    setShowing(false);
                    callback.onMenuClick(menuItem);
                });
            }

            setShowing(true);
        }

        private void setShowing(boolean showing) {
            if (this.showing == showing) {
                return;
            }

            this.showing = showing;

            ValueAnimator foregroundAnimator = ValueAnimator.ofFloat(0f, 1f);
            foregroundAnimator.setInterpolator(showing ? openInterpolator : CubicBezierInterpolator.EASE_OUT_QUINT);
            foregroundAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                if (!showing) {
                    value = 1f - value;
                }
                float clampedValue = MathUtils.clamp(value, 0f, 1f);

                container.setScaleX(0.7f + 0.3f * value);
                container.setScaleY(0.7f + 0.3f * value);
                container.setAlpha(clampedValue);
                avatarView.setTranslationY(dp(40) * (1f - value));
                menu.setTranslationY(-dp(40 + 30) * (1f - value));
                menu.setScaleX(0.95f + 0.05f * value);
                menu.setScaleY(0.95f + 0.05f * value);
            });

            ValueAnimator blurAnimator = ValueAnimator.ofFloat(0f, 1f);
            blurAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                if (!showing) {
                    value = 1f - value;
                }
                blurView.setAlpha(value); // Linear value
                invalidate();
            });

            if (openAnimator != null) {
                openAnimator.cancel();
            }
            openAnimator = new AnimatorSet();
            openAnimator.setDuration(showing ? 190 : 150);
            openAnimator.playTogether(foregroundAnimator, blurAnimator);
            openAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!showing) {
                        setVisibility(INVISIBLE);
                        onHideFinish();
                    }
                }
            });
            openAnimator.start();
        }

        public void recycle() {
            recycled = true;
            recycleInfoLoadTask();
        }

        private void recycleInfoLoadTask() {
            if (infoLoadTask != null) {
                infoLoadTask.cancel();
                infoLoadTask = null;
            }
        }

        protected abstract void onHideFinish();
    }

    private static class AvatarView extends FrameLayout {
        private BackupImageView backupImageView;

        private final RadialProgress2 radialProgress;
        private boolean showProgress;
        private ValueAnimator progressHideAnimator;
        private ValueAnimator progressShowAnimator;
        private final int radialProgressSize = dp(64f);

        public AvatarView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setWillNotDraw(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setOutlineProvider(new RoundRectOutlineProvider(6));
            }

            backupImageView = new BackupImageView(context);
            backupImageView.setAspectFit(true);
            backupImageView.setRoundRadius(dp(6));
            addView(backupImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            radialProgress = new RadialProgress2(this, resourcesProvider);
            radialProgress.setOverrideAlpha(0.0f);
            radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, false, false);
            radialProgress.setColors(0x42000000, 0x42000000, Color.WHITE, Color.WHITE);
        }

        public void setImage(int currentAccount, ImageLocation mediaLocation, String mediaFilter, ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, BitmapDrawable thumb, Object parentObject) {
            backupImageView.getImageReceiver().setCurrentAccount(currentAccount);
            backupImageView.getImageReceiver().setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, thumbLocation, thumbFilter, thumb, 0, null, parentObject, 1);
            backupImageView.onNewImageSet();
        }

        public void setProgress(float progress) {
            radialProgress.setProgress(progress, true);
        }

        public boolean getShowProgress() {
            return showProgress;
        }

        public void setShowProgress(boolean showProgress) {
            this.showProgress = showProgress;
            invalidate();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            radialProgress.setProgressRect(cx - radialProgressSize, cy - radialProgressSize, cx + radialProgressSize, cy + radialProgressSize);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            if (showProgress) {
                final Drawable drawable = backupImageView.getImageReceiver().getDrawable();
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
        }
    }
}
