package ua.itaysonlab.redesign;

import android.app.Activity;
import android.app.Application;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringSystem;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public abstract class BottomSlideFragment {
    private final static float RADIUS = AndroidUtilities.dp(16);
    private final static int TOP_STATIC_PADDING = AndroidUtilities.dp(32);

    public static Handler uiHandler = new Handler(Looper.getMainLooper());
    public SpringSystem springSystem;
    /**
     * Background view to animate
     */
    private View mBackgroundView;
    private boolean disableLight;
    private BottomSheetSwipeComponent sheetComponent;
    private float mProgress;
    private int topPadding;
    private View mView;
    private boolean dismissed;
    private BottomSlideController mSlideController;
    private final Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            mSlideController.mActivity = activity;
            onCreate();
            attachViews();
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            onResume();
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            onPause();
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            onDestroy();
            if (dismissed) activity.getApplication().unregisterActivityLifecycleCallbacks(this);
        }
    };

    public static void postOnUiThread(Runnable r) {
        uiHandler.post(r);
    }

    public Activity getActivity() {
        return mSlideController.mActivity;
    }

    @CallSuper
    protected void onCreate() {
        springSystem = SpringSystem.create();
        if (mSlideController != null && mSlideController.mActivity instanceof BottomSlideActivityInterface) {
            ((BottomSlideActivityInterface) mSlideController.mActivity).addBackPressedListener(this);
        }
    }

    @CallSuper
    protected void onDestroy() {
        destroyViews();
        for (Spring s : new ArrayList<>(springSystem.getAllSprings()))
            s.destroy();
        if (mSlideController != null && mSlideController.mActivity instanceof BottomSlideActivityInterface) {
            ((BottomSlideActivityInterface) mSlideController.mActivity).removeBackPressedListener(this);
        }
    }

    /**
     * Creates view for the slide fragment
     *
     * @param parent Parent to use
     * @return Created view
     */
    @NonNull
    protected abstract View onCreateView(@NonNull ViewGroup parent);

    /**
     * Called when the view is created
     *
     * @param v Created view
     */
    protected void onViewCreated(View v) {
    }

    @CallSuper
    protected void onResume() {
    }

    @CallSuper
    protected void onPause() {
    }

    /**
     * Dismisses slide fragment
     */
    public void dismiss() {
        if (dismissed) return;
        dismissed = true;

        if (mSlideController != null) {
            springSystem.createSpring()
                    .setSpringConfig(BottomSheetSwipeComponent.SPRING_CONFIG)
                    .setCurrentValue(mProgress)
                    .setOvershootClampingEnabled(true)
                    .addListener(new SimpleSpringListener() {
                        @Override
                        public void onSpringUpdate(Spring spring) {
                            sheetComponent.setCurrentProgress((float) spring.getCurrentValue());
                            if (spring.getCurrentValue() == 1) {
                                spring.destroy();

                                onDestroy();
                                mSlideController.mActivity.getApplication().unregisterActivityLifecycleCallbacks(callbacks);
                            }
                        }
                    }).setEndValue(1);
        }
    }

    /**
     * @return Currently created view
     */
    public final View getView() {
        return mView;
    }

    /**
     * Shows this fragment into activity
     *
     * @param parent Parent activity
     */
    public void show(Activity parent) {
        mSlideController = new BottomSlideController(parent);
        onCreate();

        parent.getApplication().registerActivityLifecycleCallbacks(callbacks);
        attachViews();
    }

    /**
     * Attaches views to the activity
     */
    private void attachViews() {
        if (mSlideController != null) {
            Activity act = mSlideController.mActivity;
            ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
            decor.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));

            mBackgroundView = decor.getChildAt(0);

            mView = onCreateView(decor);
            onViewCreated(mView);

            ViewOutlineProvider o = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float mRadius = RADIUS * (1f - mProgress);
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), (int) (view.getMeasuredHeight() + RADIUS), mRadius);
                }
            };
            mBackgroundView.setOutlineProvider(o);
            mBackgroundView.setClipToOutline(true);

            mView.setOutlineProvider(o);
            mView.setClipToOutline(true);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM;
            mView.setLayoutParams(params);

            sheetComponent = new BottomSheetSwipeComponent(act);
            sheetComponent.setSheetHeight(decor.getMeasuredHeight());
            decor.setOnApplyWindowInsetsListener((v, insets) -> {
                topPadding = insets.getStableInsetTop();
                decor.setOnApplyWindowInsetsListener(null);
                consumeBottomPadding(insets.getStableInsetBottom(), topPadding + TOP_STATIC_PADDING);
                return insets.consumeStableInsets();
            });
            Spring spring = springSystem.createSpring()
                    .setSpringConfig(BottomSheetSwipeComponent.SPRING_CONFIG)
                    .setCurrentValue(1)
                    .setOvershootClampingEnabled(true)
                    .addListener(new SimpleSpringListener() {
                        @Override
                        public void onSpringUpdate(Spring spring) {
                            sheetComponent.setCurrentProgress((float) spring.getCurrentValue());
                            if (spring.getCurrentValue() == 0) {
                                spring.destroy();
                            }
                        }
                    });

            View overlay = new View(act);
            overlay.setBackgroundColor(0x80000000);
            overlay.setAlpha(0);
            overlay.setOnClickListener(v -> dismiss());
            sheetComponent.addView(overlay);
            sheetComponent.addView(mView);

            sheetComponent.initWith(this);
            sheetComponent.setCurrentProgress(1f);
            sheetComponent.setAlpha(0);
            sheetComponent.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                    spring.setEndValue(0);
                    postOnUiThread(() -> v.setAlpha(1));
                }
            });
            decor.addView(sheetComponent);
            sheetComponent.requestApplyInsets();
        }
    }

    /**
     * Callback for swipe component
     *
     * @param progress New progress
     */
    public void onSlide(float progress) {
        mProgress = progress;

        boolean d = progress <= 0.5f;
        if (disableLight != d) {
            disableLight = d;
            if (mSlideController.mActivity instanceof BottomSlideActivityInterface) {
                ((BottomSlideActivityInterface) mSlideController.mActivity).setDisableLightStatusBar(disableLight);
            }
        }

        if (mBackgroundView != null && mView != null) {
            int h = sheetComponent.getMeasuredHeight();
            int top = TOP_STATIC_PADDING + topPadding;
            mView.setTranslationY(h - (h - top) * (1 - progress));

            mBackgroundView.setTranslationY((top / 50f) * (1 - progress));
            float sc = (float) (0.9 + 0.1 * progress);
            mBackgroundView.setScaleX(sc);
            mBackgroundView.setScaleY(sc);

            mBackgroundView.invalidateOutline();
            mView.invalidateOutline();
        }
    }

    /**
     * Destroys views
     */
    private void destroyViews() {
        if (mSlideController != null) {
            ViewGroup decor = (ViewGroup) mSlideController.mActivity.getWindow().getDecorView();
            decor.removeView(sheetComponent);

            if (mBackgroundView != null) {
                mBackgroundView.setClipToOutline(false);
                mBackgroundView.setOutlineProvider(null);
                mBackgroundView = null;
            }
            sheetComponent = null;
        }
    }

    /**
     * Callback for the insets
     *
     * @param bottomPadding  Bottom padding to set
     * @param virtualPadding Padding that should be set in order to remove the space from the top
     */
    protected void consumeBottomPadding(int bottomPadding, int virtualPadding) {
        mView.setPadding(mView.getPaddingLeft(), mView.getPaddingTop(), mView.getPaddingRight(), bottomPadding + virtualPadding);
    }

    /**
     * Called when back button is pressed
     */
    public void onBackPressed() {
        dismiss();
    }

    public interface BottomSlideActivityInterface {
        /**
         * Adds new back pressed listener
         *
         * @param fragment New listener
         */
        void addBackPressedListener(BottomSlideFragment fragment);

        /**
         * Removes back pressed listener
         *
         * @param fragment Listener to remove
         */
        void removeBackPressedListener(BottomSlideFragment fragment);

        /**
         * Sets light status bar disabled
         *
         * @param isLight New disabled value
         */
        void setDisableLightStatusBar(boolean isLight);
    }

    private final static class BottomSlideController {
        private Activity mActivity;

        private BottomSlideController(Activity act) {
            mActivity = act;
        }
    }
}