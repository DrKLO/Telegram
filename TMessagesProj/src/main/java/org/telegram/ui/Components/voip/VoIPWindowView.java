package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.VoIPFragment;

public class VoIPWindowView extends FrameLayout {

    Activity activity;
    protected boolean lockOnScreen;

    private int orientationBefore;
    private int animationIndex = -1;

    VelocityTracker velocityTracker;

    public VoIPWindowView(Activity activity, boolean enterAnimation) {
        super(activity);
        this.activity = activity;
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setFitsSystemWindows(true);

        orientationBefore = activity.getRequestedOrientation();
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (!enterAnimation) {
            runEnterTransition = true;
        }
    }

    boolean runEnterTransition;
    boolean finished;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!runEnterTransition) {
            runEnterTransition = true;
            startEnterTransition();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouchEvent(ev);
    }

    float startX;
    float startY;
    boolean startDragging;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (lockOnScreen) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startX = event.getX();
            startY = event.getY();

            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.clear();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - startX;
            float dy = event.getY() - startY;
            if (!startDragging && Math.abs(dx) > AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                startX = event.getX();
                dx = 0;
                startDragging = true;
            }
            if (startDragging) {
                if (dx < 0) {
                    dx = 0;
                }
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);
                setTranslationX(dx);
            }
            return startDragging;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            float x = getTranslationX();
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }

            velocityTracker.computeCurrentVelocity(1000);
            float velX = velocityTracker.getXVelocity();
            float velY = velocityTracker.getYVelocity();

            final boolean backAnimation = x < getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
            if (!backAnimation) {
                float distToMove = getMeasuredWidth() - getTranslationX();
                finish(Math.max((int) (200.0f / getMeasuredWidth() * distToMove), 50));
            } else {
                animate().translationX(0).start();
            }
            startDragging = false;
        }
        return false;
    }

    public void finish() {
        finish(150);
    }

    public void finish(long animDuration) {
        if (!finished) {
            finished = true;
            VoIPFragment.clearInstance();

            if (lockOnScreen) {
                try {
                    WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(VoIPWindowView.this);
                } catch (Exception ignore) {

                }
            } else {
                int account = UserConfig.selectedAccount;
                animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null);
                animate().translationX(getMeasuredWidth()).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getInstance(account).onAnimationFinish(animationIndex);
                        if (getParent() != null) {
                            activity.setRequestedOrientation(orientationBefore);

                            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                            setVisibility(View.GONE);
                            try {
                                wm.removeView(VoIPWindowView.this);
                            } catch (Exception ignore) {

                            }
                        }
                    }
                }).setDuration(animDuration).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }
    }

    public void startEnterTransition() {
        if (!lockOnScreen) {
            setTranslationX(getMeasuredWidth());
            animate().translationX(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
    }

    public void setLockOnScreen(boolean lock) {
        lockOnScreen = lock;
    }

    public WindowManager.LayoutParams createWindowLayoutParams() {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSPARENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        return windowLayoutParams;
    }

    public boolean isLockOnScreen() {
        return lockOnScreen;
    }

    public void requestFullscreen(boolean request) {
        if (request) {
            setSystemUiVisibility(getSystemUiVisibility() | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            int flags = getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
            setSystemUiVisibility(flags);
        }

    }

    public void finishImmediate() {
        if (getParent() != null) {
            activity.setRequestedOrientation(orientationBefore);
            WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            setVisibility(View.GONE);
            wm.removeView(VoIPWindowView.this);
        }
    }
}

