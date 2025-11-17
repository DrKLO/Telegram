package org.telegram.ui.Stories;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.pip.source.IPipSourceDelegate;
import org.telegram.messenger.pip.utils.PipPermissions;
import org.telegram.messenger.pip.PipSource;
import org.telegram.messenger.pip.utils.PipUtils;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SimpleFloatPropertyCompat;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.LivePlayerView;
import org.webrtc.RendererCommon;

import java.util.ArrayList;
import java.util.List;

public class LiveStoryPipOverlay implements NotificationCenter.NotificationCenterDelegate, IPipSourceDelegate {
    private final static float ROUNDED_CORNERS_DP = 10;
    private final static float SIDE_PADDING_DP = 16;
    private final static FloatPropertyCompat<LiveStoryPipOverlay> PIP_X_PROPERTY = new SimpleFloatPropertyCompat<>("pipX", obj -> obj.pipX, (obj, value) -> {
        obj.windowLayoutParams.x = (int) (obj.pipX = value);
        AndroidUtilities.updateViewLayout(obj.windowManager, obj.contentView, obj.windowLayoutParams);
    }), PIP_Y_PROPERTY = new SimpleFloatPropertyCompat<>("pipY", obj -> obj.pipY, (obj, value) -> {
        obj.windowLayoutParams.y = (int) (obj.pipY = value);
        AndroidUtilities.updateViewLayout(obj.windowManager, obj.contentView, obj.windowLayoutParams);
    });

    @SuppressLint("StaticFieldLeak")
    private static LiveStoryPipOverlay instance = new LiveStoryPipOverlay();

    private float minScaleFactor = 0.6f, maxScaleFactor = 1.4f;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;
    private ViewGroup contentView;
    private FrameLayout contentFrameLayout;
    private LivePlayerView textureView;
    private FrameLayout controlsView;

//    private CellFlickerDrawable cellFlickerDrawable = new CellFlickerDrawable();
    private BackupImageView avatarImageView;
    private View flickerView;
    private boolean placeholderShown = true;
    private boolean firstFrameRendered;
    private boolean boundPresentation;

    private LivePlayer livePlayer;
    private int currentAccount;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetectorCompat gestureDetector;
    private boolean isScrolling;
    private boolean isScrollDisallowed;
    private View consumingChild;
    private boolean isShowingControls;
    private ValueAnimator scaleAnimator;

    private int pipWidth, pipHeight;
    private PipSource pipSource;
    private float scaleFactor = 1f;
    private float pipX, pipY;
    private SpringAnimation pipXSpring, pipYSpring;
    private Float aspectRatio;

    private boolean isVisible;

    private boolean postedDismissControls;
    private Runnable dismissControlsCallback = () -> {
        toggleControls(isShowingControls = false);
        postedDismissControls = false;
    };

    public static boolean isVisible() {
        return instance.isVisible;
    }

    public static boolean isVisible(LivePlayer livePlayer) {
        return instance.isVisible && instance.livePlayer == livePlayer;
    }

    public static LivePlayer getLivePlayer() {
        return instance.livePlayer;
    }

    public static LivePlayer takeLivePlayer() {
        final LivePlayer livePlayer = instance.livePlayer;
        instance.livePlayer = null;
        return livePlayer;
    }

    private int getSuggestedWidth() {
        if (getRatio() >= 1) {
            return (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.35f);
        }
        return (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.6f);
    }

    private int getSuggestedHeight() {
        return (int) (getSuggestedWidth() * getRatio());
    }

    private float getRatio() {
        if (aspectRatio == null) {
            aspectRatio = 16f / 9f;
            maxScaleFactor = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(SIDE_PADDING_DP * 2)) / (float) getSuggestedWidth();
        }
        return aspectRatio;
    }

    private void toggleControls(boolean show) {
        scaleAnimator = ValueAnimator.ofFloat(show ? 0 : 1, show ? 1 : 0f).setDuration(200);
        scaleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        scaleAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            controlsView.setAlpha(value);
        });
        scaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scaleAnimator = null;
            }
        });
        scaleAnimator.start();
    }

    public static void dismiss() {
        instance.dismissInternal(true);
    }

    public static void dismiss(boolean destroyPlayer) {
        instance.dismissInternal(destroyPlayer);
    }

    private void dismissInternal(boolean destroyPlayer) {
        if (!isVisible) {
            return;
        }
        isVisible = false;
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged), 100);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.liveStoryUpdated);

        if (scaleAnimator != null) {
            scaleAnimator.cancel();
        }

        if (postedDismissControls) {
            AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
            postedDismissControls = false;
        }

        AnimatorSet set = new AnimatorSet();
        set.setDuration(250);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.playTogether(
                ObjectAnimator.ofFloat(contentView, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(contentView, View.SCALE_X, 0.1f),
                ObjectAnimator.ofFloat(contentView, View.SCALE_Y, 0.1f)
        );
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                windowManager.removeViewImmediate(contentView);

                textureView.release();
                if (destroyPlayer && livePlayer != null && livePlayer != LivePlayer.recording) {
                    livePlayer.destroy();
                }
                livePlayer = null;

                placeholderShown = true;
                firstFrameRendered = false;
                consumingChild = null;
                isScrolling = false;
            }
        });
        set.start();
        if (pipSource != null) {
            pipSource.destroy();
            pipSource = null;
        }
    }

    public static void show(Activity activity, LivePlayer livePlayer) {
        instance.showInternal(activity, livePlayer);
    }

    private void showInternal(Activity activity, LivePlayer livePlayer) {
        if (livePlayer == null || isVisible) {
            return;
        }
        isVisible = true;

        this.livePlayer = livePlayer;
        currentAccount = livePlayer.currentAccount;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.liveStoryUpdated);

        pipWidth = getSuggestedWidth();
        pipHeight = getSuggestedHeight();
        scaleFactor = 1f;
        isShowingControls = false;

        float stiffness = 650f;
        pipXSpring = new SpringAnimation(this, PIP_X_PROPERTY)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(stiffness));
        pipYSpring = new SpringAnimation(this, PIP_Y_PROPERTY)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(stiffness));

        Context context = activity != null ? activity : ApplicationLoader.applicationContext;
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor = MathUtils.clamp(scaleFactor * detector.getScaleFactor(), minScaleFactor, maxScaleFactor);
                pipWidth = (int) (getSuggestedWidth() * scaleFactor);
                pipHeight = (int) (getSuggestedHeight() * scaleFactor);
                AndroidUtilities.runOnUIThread(()->{
                    contentFrameLayout.invalidate();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && contentFrameLayout.isInLayout()) {
                        return;
                    }
                    contentFrameLayout.requestLayout();
                    contentView.requestLayout();
                    textureView.requestLayout();
                });

                pipXSpring.setStartValue(pipX)
                        .getSpring()
                        .setFinalPosition(detector.getFocusX() >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                if (!pipXSpring.isRunning()) {
                    pipXSpring.start();
                }

                pipYSpring.setStartValue(pipY)
                        .getSpring()
                        .setFinalPosition(MathUtils.clamp(detector.getFocusY() - pipHeight / 2f, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP)));
                if (!pipYSpring.isRunning()) {
                    pipYSpring.start();
                }

                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (isScrolling) {
                    isScrolling = false;
                }
                isScrollDisallowed = true;
                windowLayoutParams.width = (int) (getSuggestedWidth() * maxScaleFactor);
                windowLayoutParams.height = (int) (getSuggestedHeight() * maxScaleFactor);
                AndroidUtilities.updateViewLayout(windowManager, contentView, windowLayoutParams);

                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (pipXSpring.isRunning() || pipYSpring.isRunning()) {
                    List<SpringAnimation> springs = new ArrayList<>();
                    DynamicAnimation.OnAnimationEndListener endListener = new DynamicAnimation.OnAnimationEndListener() {
                        @Override
                        public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                            animation.removeEndListener(this);

                            springs.add((SpringAnimation) animation);
                            if (springs.size() == 2) {
                                updateLayout();
                            }
                        }
                    };
                    if (!pipXSpring.isRunning()) {
                        springs.add(pipXSpring);
                    } else {
                        pipXSpring.addEndListener(endListener);
                    }
                    if (!pipYSpring.isRunning()) {
                        springs.add(pipYSpring);
                    } else {
                        pipYSpring.addEndListener(endListener);
                    }
                    return;
                }
                updateLayout();
            }

            private void updateLayout() {
                pipWidth = windowLayoutParams.width = (int) (getSuggestedWidth() * scaleFactor);
                pipHeight = windowLayoutParams.height = (int) (getSuggestedHeight() * scaleFactor);
                AndroidUtilities.updateViewLayout(windowManager, contentView, windowLayoutParams);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleGestureDetector.setQuickScaleEnabled(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scaleGestureDetector.setStylusScaleEnabled(false);
        }
        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            private float startPipX, startPipY;

            @Override
            public boolean onDown(MotionEvent e) {
                if (isShowingControls) {
                    for (int i = 1; i < contentFrameLayout.getChildCount(); i++) {
                        View child = contentFrameLayout.getChildAt(i);
                        boolean consumed = child.dispatchTouchEvent(e);
                        if (consumed) {
                            consumingChild = child;
                            return true;
                        }
                    }
                }
                startPipX = pipX;
                startPipY = pipY;
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (scaleAnimator != null) {
                    return true;
                }

                if (postedDismissControls) {
                    AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
                    postedDismissControls = false;
                }

                isShowingControls = !isShowingControls;
                toggleControls(isShowingControls);

                if (isShowingControls && !postedDismissControls) {
                    AndroidUtilities.runOnUIThread(dismissControlsCallback, 2500);
                    postedDismissControls = true;
                }

                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isScrolling && !isScrollDisallowed) {
                    pipXSpring.setStartVelocity(velocityX)
                            .setStartValue(pipX)
                            .getSpring()
                            .setFinalPosition(pipX + pipWidth / 2f + velocityX / 7f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                    pipXSpring.start();

                    pipYSpring.setStartVelocity(velocityX)
                            .setStartValue(pipY)
                            .getSpring()
                            .setFinalPosition(MathUtils.clamp(pipY + velocityY / 10f, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP)));
                    pipYSpring.start();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!isScrolling && scaleAnimator == null && !isScrollDisallowed) {
                    if (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) {
                        isScrolling = true;

                        pipXSpring.cancel();
                        pipYSpring.cancel();
                    }
                }
                if (isScrolling) {
                    windowLayoutParams.x = (int) (pipX = startPipX + e2.getRawX() - e1.getRawX());
                    windowLayoutParams.y = (int) (pipY = startPipY + e2.getRawY() - e1.getRawY());
                    AndroidUtilities.updateViewLayout(windowManager, contentView, windowLayoutParams);
                }
                return true;
            }
        });
        contentFrameLayout = new FrameLayout(context) {
            private Path path = new Path();

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                int action = ev.getAction();
                if (consumingChild != null) {
                    MotionEvent newEvent = MotionEvent.obtain(ev);
                    newEvent.offsetLocation(consumingChild.getX(), consumingChild.getY());
                    boolean consumed = consumingChild.dispatchTouchEvent(ev);
                    newEvent.recycle();

                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        consumingChild = null;
                    }

                    if (consumed) {
                        return true;
                    }
                }
                MotionEvent temp = MotionEvent.obtain(ev);
                temp.offsetLocation(ev.getRawX() - ev.getX(), ev.getRawY() - ev.getY());
                boolean scaleDetector = scaleGestureDetector.onTouchEvent(temp);
                temp.recycle();
                boolean detector = !scaleGestureDetector.isInProgress() && gestureDetector.onTouchEvent(ev);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isScrolling = false;
                    isScrollDisallowed = false;

                    if (!pipXSpring.isRunning()) {
                        pipXSpring.setStartValue(pipX)
                                .getSpring()
                                .setFinalPosition(pipX + pipWidth / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                        pipXSpring.start();
                    }

                    if (!pipYSpring.isRunning()) {
                        pipYSpring.setStartValue(pipY)
                                .getSpring()
                                .setFinalPosition(MathUtils.clamp(pipY, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP)));
                        pipYSpring.start();
                    }
                }
                return scaleDetector || detector;
            }

            @Override
            protected void onConfigurationChanged(Configuration newConfig) {
                AndroidUtilities.checkDisplaySize(getContext(), newConfig);
                AndroidUtilities.setPreferredMaxRefreshRate(windowManager, contentView, windowLayoutParams);
                bindTextureView();
            }

            @Override
            public void draw(Canvas canvas) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    super.draw(canvas);
                } else {
                    canvas.save();
                    canvas.clipPath(path);
                    super.draw(canvas);
                    canvas.restore();
                }
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                path.rewind();
                AndroidUtilities.rectTmp.set(0, 0, w, h);
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(ROUNDED_CORNERS_DP), AndroidUtilities.dp(ROUNDED_CORNERS_DP), Path.Direction.CW);
            }
        };
        contentView = new ViewGroup(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (contentFrameLayout.getParent() == this) {
                    contentFrameLayout.layout(0, 0, pipWidth, pipHeight);
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                if (contentFrameLayout.getParent() == this) {
                    contentFrameLayout.measure(MeasureSpec.makeMeasureSpec(pipWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(pipHeight, MeasureSpec.EXACTLY));
                }
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                if (windowViewSkipRender) {
                    return;
                }

                super.draw(canvas);
            }
        };
        contentView.addView(contentFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            contentFrameLayout.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(ROUNDED_CORNERS_DP));
                }
            });
            contentFrameLayout.setClipToOutline(true);
        }
        contentFrameLayout.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_actionBar));

        avatarImageView = new BackupImageView(context);
        contentFrameLayout.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        textureView = new LivePlayerView(context, currentAccount, false);
        textureView.setAlpha(0f);
//        textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
//        textureView.scaleType = VoIPTextureView.SCALE_TYPE_FILL;
//        textureView.renderer.setRotateTextureWithScreen(true);
//        textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
//            @Override
//            public void onFirstFrameRendered() {
//                firstFrameRendered = true;
//                if (firstFrameCallback != null) {
//                    firstFrameCallback.run();
//                    firstFrameCallback = null;
//                }
//                AndroidUtilities.runOnUIThread(()-> bindTextureView());
//            }
//
//            @Override
//            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
//                if ((rotation / 90) % 2 == 0) {
//                    aspectRatio = (float) videoHeight / videoWidth;
//                } else {
//                    aspectRatio = (float) videoWidth / videoHeight;
//                }
//                AndroidUtilities.runOnUIThread(()-> {
//                    if (pipSource != null) {
//                        pipSource.setContentRatio(videoWidth, videoHeight);
//                    }
//                    bindTextureView();
//                });
//            }
//        });
        contentFrameLayout.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        flickerView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (getAlpha() == 0f) return;

                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
//                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(ROUNDED_CORNERS_DP), null);
                invalidate();
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
//                cellFlickerDrawable.setParentWidth(w);
            }
        };
        contentFrameLayout.addView(flickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        controlsView = new FrameLayout(context);
        controlsView.setAlpha(0f);
        View scrim = new View(context);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColors(new int[] {
                0x44000000,
                Color.TRANSPARENT
        });
        gradientDrawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        scrim.setBackground(gradientDrawable);
        controlsView.addView(scrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        int padding = AndroidUtilities.dp(8);
        int margin = 4;
        int buttonSize = 38;

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.pip_video_close);
        closeButton.setColorFilter(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        closeButton.setPadding(padding, padding, padding, padding);
        closeButton.setOnClickListener(v -> dismiss());
        controlsView.addView(closeButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.RIGHT, 0, margin, margin, 0));

        ImageView expandButton = new ImageView(context);
        expandButton.setImageResource(R.drawable.pip_video_expand);
        expandButton.setColorFilter(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        expandButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        expandButton.setPadding(padding, padding, padding, padding);
        expandButton.setOnClickListener(v -> {
            final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment == null || livePlayer == null) return;
            TL_stories.StoryItem storyItem = MessagesController.getInstance(currentAccount).getStoriesController().findStory(livePlayer.dialogId, livePlayer.storyId);
            if (storyItem == null) {
                storyItem = livePlayer.storyItem;
            }
            if (storyItem == null) return;
            fragment.getOrCreateStoryViewer().open(currentAccount, context, storyItem, null);
            AndroidUtilities.runOnUIThread(LiveStoryPipOverlay::dismiss, 200);
        });
        controlsView.addView(expandButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.RIGHT, 0, margin, buttonSize + margin + 6, 0));

        contentFrameLayout.addView(controlsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowLayoutParams = PipUtils.createWindowLayoutParams(context, false);
        windowLayoutParams.width = pipWidth;
        windowLayoutParams.height = pipHeight;
        windowLayoutParams.x = (int) (pipX = AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP));
        windowLayoutParams.y = (int) (pipY = AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP));
        windowLayoutParams.dimAmount = 0f;
        windowLayoutParams.flags = FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        contentView.setAlpha(0f);
        contentView.setScaleX(0.1f);
        contentView.setScaleY(0.1f);
        AndroidUtilities.setPreferredMaxRefreshRate(windowManager, contentView, windowLayoutParams);
        windowManager.addView(contentView, windowLayoutParams);

        AnimatorSet set = new AnimatorSet();
        set.setDuration(250);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.playTogether(
                ObjectAnimator.ofFloat(contentView, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(contentView, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(contentView, View.SCALE_Y, 1f)
        );
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation, boolean isReverse) {
                if (pipSource != null) {
                    pipSource.invalidatePosition();
                }
            }
        });
        set.start();

        bindTextureView();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged);

        if (pipSource != null) {
            pipSource.destroy();
            pipSource = null;
        }
        if (activity != null && PipUtils.checkPermissions(activity) == PipPermissions.PIP_GRANTED_PIP) {
            pipSource = new PipSource.Builder(activity, this)
                .setTagPrefix("pip-live-story")
                .setPriority(1)
                .setCornerRadius(AndroidUtilities.dp(ROUNDED_CORNERS_DP))
                .setContentView(contentView)
                .setPlaceholderView(textureView.getPlaceholderView())
                .build();
        }
    }

    private void bindTextureView() {
        bindTextureView(false);
    }

    private void bindTextureView(boolean forced) {
        if (livePlayer != null) {
            livePlayer.setVolume(1.0f);
            if (pipTextureView != null) {
                livePlayer.setDisplaySink(pipTextureView.getSink());
            } else {
                livePlayer.setDisplaySink(textureView.getSink());
            }
        }
        boolean showPlaceholder = false;/*!firstFrameRendered || boundParticipant == null || boundParticipant.video == null && boundParticipant.presentation == null ||
                (boundParticipant.video != null && boundParticipant.video.paused || boundParticipant.presentation != null && boundParticipant.presentation.paused)*/;
        if (placeholderShown != showPlaceholder) {
            flickerView.animate().cancel();
            flickerView.animate().alpha(showPlaceholder ? 1f : 0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

            avatarImageView.animate().cancel();
            avatarImageView.animate().alpha(showPlaceholder ? 1f : 0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

            textureView.animate().cancel();
            textureView.animate().alpha(showPlaceholder ? 0f : 1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

            placeholderShown = showPlaceholder;
        }
        if (pipWidth != getSuggestedWidth() * scaleFactor || pipHeight != getSuggestedHeight() * scaleFactor) {
            windowLayoutParams.width = pipWidth = (int) (getSuggestedWidth() * scaleFactor);
            windowLayoutParams.height = pipHeight = (int) (getSuggestedHeight() * scaleFactor);
            AndroidUtilities.updateViewLayout(windowManager, contentView, windowLayoutParams);

            pipXSpring.setStartValue(pipX)
                    .getSpring()
                    .setFinalPosition(pipX + (getSuggestedWidth() * scaleFactor) / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - (getSuggestedWidth() * scaleFactor) - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
            pipXSpring.start();

            pipYSpring.setStartValue(pipY)
                    .getSpring()
                    .setFinalPosition(MathUtils.clamp(pipY, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - (getSuggestedHeight() * scaleFactor) - AndroidUtilities.dp(SIDE_PADDING_DP)));
            pipYSpring.start();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didEndCall) {
            dismiss();
        } else if (id == NotificationCenter.groupCallUpdated) {
            bindTextureView();
        }
    }



    private Runnable firstFrameCallback;
    private LivePlayerView pipTextureView;
    private boolean windowViewSkipRender;

    @Override
    public Bitmap pipCreatePrimaryWindowViewBitmap() {
        if (textureView == null || !textureView.isAvailable()) {
            return null;
        }

        return textureView.getBitmap();
    }

    @Override
    public View pipCreatePictureInPictureView() {
        pipTextureView = new LivePlayerView(textureView.getContext(), currentAccount, false);
        return pipTextureView;
    }

    @Override
    public void pipHidePrimaryWindowView(Runnable firstFrameCallback) {
        this.firstFrameCallback = firstFrameCallback;
//        if (textureView != null) {
//            textureView.clearFirstFrame();
//        }

        bindTextureView(true);

        windowViewSkipRender = true;
        windowManager.removeView(contentView);
        contentView.invalidate();
    }

    @Override
    public Bitmap pipCreatePictureInPictureViewBitmap() {
        if (pipTextureView == null || !pipTextureView.isAvailable()) {
            return null;
        }

        return pipTextureView.getBitmap();
    }

    @Override
    public void pipShowPrimaryWindowView(Runnable firstFrameCallback) {
        this.firstFrameCallback = firstFrameCallback;

        if (pipSource != null && pipSource.params.isValid()) {
            windowLayoutParams.width = pipWidth = pipSource.params.getWidth();
            windowLayoutParams.height = pipHeight = pipSource.params.getHeight();
        }

        windowViewSkipRender = false;
        windowManager.addView(contentView, windowLayoutParams);
        contentView.invalidate();

        if (pipTextureView != null) {
            pipTextureView.release();
            pipTextureView = null;
        }
        bindTextureView(true);
    }
}

