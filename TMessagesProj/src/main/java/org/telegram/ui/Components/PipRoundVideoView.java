/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import androidx.annotation.Keep;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class PipRoundVideoView implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout windowView;
    private Activity parentActivity;
    private int currentAccount;
    private TextureView textureView;
    private ImageView imageView;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private Bitmap bitmap;
    private int videoWidth;
    private int videoHeight;
    private AnimatorSet hideShowAnimation;
    private Runnable onCloseRunnable;

    private WindowManager.LayoutParams windowLayoutParams;
    private WindowManager windowManager;
    private SharedPreferences preferences;
    private DecelerateInterpolator decelerateInterpolator;

    private RectF rect = new RectF();

    @SuppressLint("StaticFieldLeak")
    private static PipRoundVideoView instance;

    public void show(Activity activity, Runnable closeRunnable) {
        if (activity == null) {
            return;
        }
        instance = this;
        onCloseRunnable = closeRunnable;
        windowView = new FrameLayout(activity) {

            private float startX;
            private float startY;
            private boolean dragging;
            private boolean startDragging;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    startDragging = true;
                }
                return true;
            }

            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                super.requestDisallowInterceptTouchEvent(disallowIntercept);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (!startDragging && !dragging) {
                    return false;
                }
                float x = event.getRawX();
                float y = event.getRawY();
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = (x - startX);
                    float dy = (y - startY);
                    if (startDragging) {
                        if (Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.3f, true) || Math.abs(dy) >= AndroidUtilities.getPixelsInCM(0.3f, false)) {
                            dragging = true;
                            startDragging = false;
                        }
                    } else if (dragging) {
                        windowLayoutParams.x += dx;
                        windowLayoutParams.y += dy;
                        int maxDiff = videoWidth / 2;
                        if (windowLayoutParams.x < -maxDiff) {
                            windowLayoutParams.x = -maxDiff;
                        } else if (windowLayoutParams.x > AndroidUtilities.displaySize.x - windowLayoutParams.width + maxDiff) {
                            windowLayoutParams.x = AndroidUtilities.displaySize.x - windowLayoutParams.width + maxDiff;
                        }
                        float alpha = 1.0f;
                        if (windowLayoutParams.x < 0) {
                            alpha = 1.0f + windowLayoutParams.x / (float) maxDiff * 0.5f;
                        } else if (windowLayoutParams.x > AndroidUtilities.displaySize.x - windowLayoutParams.width) {
                            alpha = 1.0f - (windowLayoutParams.x - AndroidUtilities.displaySize.x + windowLayoutParams.width) / (float) maxDiff * 0.5f;
                        }
                        if (windowView.getAlpha() != alpha) {
                            windowView.setAlpha(alpha);
                        }
                        maxDiff = 0;
                        if (windowLayoutParams.y < -maxDiff) {
                            windowLayoutParams.y = -maxDiff;
                        } else if (windowLayoutParams.y > AndroidUtilities.displaySize.y - windowLayoutParams.height + maxDiff) {
                            windowLayoutParams.y = AndroidUtilities.displaySize.y - windowLayoutParams.height + maxDiff;
                        }
                        windowManager.updateViewLayout(windowView, windowLayoutParams);
                        startX = x;
                        startY = y;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (startDragging && !dragging) {
                        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                        if (messageObject != null) {
                            if (MediaController.getInstance().isMessagePaused()) {
                                MediaController.getInstance().playMessage(messageObject);
                            } else {
                                MediaController.getInstance().pauseMessage(messageObject);
                            }
                        }
                    }
                    dragging = false;
                    startDragging = false;
                    animateToBoundsMaybe();
                }
                return true;
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (Theme.chat_roundVideoShadow != null/* && aspectRatioFrameLayout.isDrawingReady()*/) {
                    Theme.chat_roundVideoShadow.setAlpha((int) (getAlpha() * 255));
                    Theme.chat_roundVideoShadow.setBounds(AndroidUtilities.dp(1), AndroidUtilities.dp(2), AndroidUtilities.dp(125), AndroidUtilities.dp(125));
                    Theme.chat_roundVideoShadow.draw(canvas);

                    Theme.chat_docBackPaint.setColor(Theme.getColor(Theme.key_chat_inBubble));
                    Theme.chat_docBackPaint.setAlpha((int) (getAlpha() * 255));
                    canvas.drawCircle(AndroidUtilities.dp(3 + 60), AndroidUtilities.dp(3 + 60), AndroidUtilities.dp(59.5f), Theme.chat_docBackPaint);
                }
            }
        };
        windowView.setWillNotDraw(false);

        videoWidth = AndroidUtilities.dp(120 + 6);
        videoHeight = AndroidUtilities.dp(120 + 6);

        if (Build.VERSION.SDK_INT >= 21) {
            aspectRatioFrameLayout = new AspectRatioFrameLayout(activity) {
                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    if (child == textureView) {
                        MessageObject currentMessageObject = MediaController.getInstance().getPlayingMessageObject();
                        if (currentMessageObject != null) {
                            rect.set(AndroidUtilities.dpf2(1.5f), AndroidUtilities.dpf2(1.5f), getMeasuredWidth() - AndroidUtilities.dpf2(1.5f), getMeasuredHeight() - AndroidUtilities.dpf2(1.5f));
                            canvas.drawArc(rect, -90, 360 * currentMessageObject.audioProgress, false, Theme.chat_radialProgressPaint);
                        }
                    }
                    return result;
                }
            };
            aspectRatioFrameLayout.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(120), AndroidUtilities.dp(120));
                }
            });
            aspectRatioFrameLayout.setClipToOutline(true);
        } else {
            final Paint aspectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            aspectPaint.setColor(0xff000000);
            aspectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            aspectRatioFrameLayout = new AspectRatioFrameLayout(activity) {

                private Path aspectPath = new Path();

                @Override
                protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                    super.onSizeChanged(w, h, oldw, oldh);
                    aspectPath.reset();
                    aspectPath.addCircle(w / 2, h / 2, w / 2, Path.Direction.CW);
                    aspectPath.toggleInverseFillType();
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    canvas.drawPath(aspectPath, aspectPaint);
                }

                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    boolean result;
                    try {
                        result = super.drawChild(canvas, child, drawingTime);
                    } catch (Throwable ignore) {
                        result = false;
                    }
                    if (child == textureView) {
                        MessageObject currentMessageObject = MediaController.getInstance().getPlayingMessageObject();
                        if (currentMessageObject != null) {
                            rect.set(AndroidUtilities.dpf2(1.5f), AndroidUtilities.dpf2(1.5f), getMeasuredWidth() - AndroidUtilities.dpf2(1.5f), getMeasuredHeight() - AndroidUtilities.dpf2(1.5f));
                            canvas.drawArc(rect, -90, 360 * currentMessageObject.audioProgress, false, Theme.chat_radialProgressPaint);
                        }
                    }
                    return result;
                }
            };
            aspectRatioFrameLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        aspectRatioFrameLayout.setAspectRatio(1.0f, 0);
        windowView.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(120, 120, Gravity.LEFT | Gravity.TOP, 3, 3, 0, 0));
        windowView.setAlpha(1.0f);
        windowView.setScaleX(0.8f);
        windowView.setScaleY(0.8f);

        textureView = new TextureView(activity);
        float scale = (AndroidUtilities.dpf2(120) + AndroidUtilities.dpf2(2)) / AndroidUtilities.dpf2(120);
        textureView.setScaleX(scale);
        textureView.setScaleY(scale);
        aspectRatioFrameLayout.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        imageView = new ImageView(activity);
        aspectRatioFrameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        imageView.setVisibility(View.INVISIBLE);

        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        preferences = ApplicationLoader.applicationContext.getSharedPreferences("pipconfig", Context.MODE_PRIVATE);

        int sidex = preferences.getInt("sidex", 1);
        int sidey = preferences.getInt("sidey", 0);
        float px = preferences.getFloat("px", 0);
        float py = preferences.getFloat("py", 0);

        try {
            windowLayoutParams = new WindowManager.LayoutParams();
            windowLayoutParams.width = videoWidth;
            windowLayoutParams.height = videoHeight;
            windowLayoutParams.x = getSideCoord(true, sidex, px, videoWidth);
            windowLayoutParams.y = getSideCoord(false, sidey, py, videoHeight);
            windowLayoutParams.format = PixelFormat.TRANSLUCENT;
            windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            windowManager.addView(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        parentActivity = activity;
        currentAccount = UserConfig.selectedAccount;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        runShowHideAnimation(true);
    }

    private static int getSideCoord(boolean isX, int side, float p, int sideSize) {
        int total;
        if (isX) {
            total = AndroidUtilities.displaySize.x - sideSize;
        } else {
            total = AndroidUtilities.displaySize.y - sideSize - ActionBar.getCurrentActionBarHeight();
        }
        int result;
        if (side == 0) {
            result = AndroidUtilities.dp(10);
        } else if (side == 1) {
            result = total - AndroidUtilities.dp(10);
        } else {
            result = Math.round((total - AndroidUtilities.dp(20)) * p) + AndroidUtilities.dp(10);
        }
        if (!isX) {
            result += ActionBar.getCurrentActionBarHeight();
        }
        return result;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            if (aspectRatioFrameLayout != null) {
                aspectRatioFrameLayout.invalidate();
            }
        }
    }

    public TextureView getTextureView() {
        return textureView;
    }

    public void close(boolean animated) {
        if (animated) {
            if (textureView != null && textureView.getParent() != null) {
                if (textureView.getWidth() > 0 && textureView.getHeight() > 0) {
                    bitmap = Bitmaps.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
                }
                try {
                    textureView.getBitmap(bitmap);
                } catch (Throwable e) {
                    bitmap = null;
                }
                imageView.setImageBitmap(bitmap);
                try {
                    aspectRatioFrameLayout.removeView(textureView);
                } catch (Exception ignore) {

                }
                imageView.setVisibility(View.VISIBLE);
                runShowHideAnimation(false);
            }
        } else {
            if (bitmap != null) {
                imageView.setImageDrawable(null);
                bitmap.recycle();
                bitmap = null;
            }
            try {
                windowManager.removeView(windowView);
            } catch (Exception e) {
                //don't promt
            }
            if (instance == this) {
                instance = null;
            }
            parentActivity = null;
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }
    }

    public void onConfigurationChanged() {
        int sidex = preferences.getInt("sidex", 1);
        int sidey = preferences.getInt("sidey", 0);
        float px = preferences.getFloat("px", 0);
        float py = preferences.getFloat("py", 0);
        windowLayoutParams.x = getSideCoord(true, sidex, px, videoWidth);
        windowLayoutParams.y = getSideCoord(false, sidey, py, videoHeight);
        windowManager.updateViewLayout(windowView, windowLayoutParams);
    }

    public void showTemporary(boolean show) {
        if (hideShowAnimation != null) {
            hideShowAnimation.cancel();
        }
        hideShowAnimation = new AnimatorSet();
        hideShowAnimation.playTogether(
                ObjectAnimator.ofFloat(windowView, View.ALPHA, show ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(windowView, View.SCALE_X, show ? 1.0f : 0.8f),
                ObjectAnimator.ofFloat(windowView, View.SCALE_Y, show ? 1.0f : 0.8f));
        hideShowAnimation.setDuration(150);
        if (decelerateInterpolator == null) {
            decelerateInterpolator = new DecelerateInterpolator();
        }
        hideShowAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(hideShowAnimation)) {
                    hideShowAnimation = null;
                }
            }
        });
        hideShowAnimation.setInterpolator(decelerateInterpolator);
        hideShowAnimation.start();
    }

    private void runShowHideAnimation(final boolean show) {
        if (hideShowAnimation != null) {
            hideShowAnimation.cancel();
        }
        hideShowAnimation = new AnimatorSet();
        hideShowAnimation.playTogether(
                ObjectAnimator.ofFloat(windowView, View.ALPHA, show ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(windowView, View.SCALE_X, show ? 1.0f : 0.8f),
                ObjectAnimator.ofFloat(windowView, View.SCALE_Y, show ? 1.0f : 0.8f));
        hideShowAnimation.setDuration(150);
        if (decelerateInterpolator == null) {
            decelerateInterpolator = new DecelerateInterpolator();
        }
        hideShowAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(hideShowAnimation)) {
                    if (!show) {
                        close(false);
                    }
                    hideShowAnimation = null;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(hideShowAnimation)) {
                    hideShowAnimation = null;
                }
            }
        });
        hideShowAnimation.setInterpolator(decelerateInterpolator);
        hideShowAnimation.start();
    }

    private void animateToBoundsMaybe() {
        int startX = getSideCoord(true, 0, 0, videoWidth);
        int endX = getSideCoord(true, 1, 0, videoWidth);
        int startY = getSideCoord(false, 0, 0, videoHeight);
        int endY = getSideCoord(false, 1, 0, videoHeight);
        ArrayList<Animator> animators = null;
        SharedPreferences.Editor editor = preferences.edit();
        int maxDiff = AndroidUtilities.dp(20);
        boolean slideOut = false;
        if (Math.abs(startX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x < 0 && windowLayoutParams.x > -videoWidth / 4) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 0);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", startX));
        } else if (Math.abs(endX - windowLayoutParams.x) <= maxDiff || windowLayoutParams.x > AndroidUtilities.displaySize.x - videoWidth && windowLayoutParams.x < AndroidUtilities.displaySize.x - videoWidth / 4 * 3) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            editor.putInt("sidex", 1);
            if (windowView.getAlpha() != 1.0f) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f));
            }
            animators.add(ObjectAnimator.ofInt(this, "x", endX));
        } else if (windowView.getAlpha() != 1.0f) {
            if (animators == null) {
                animators = new ArrayList<>();
            }
            if (windowLayoutParams.x < 0) {
                animators.add(ObjectAnimator.ofInt(this, "x", -videoWidth));
            } else {
                animators.add(ObjectAnimator.ofInt(this, "x", AndroidUtilities.displaySize.x));
            }
            slideOut = true;
        } else {
            editor.putFloat("px", (windowLayoutParams.x - startX) / (float) (endX - startX));
            editor.putInt("sidex", 2);
        }
        if (!slideOut) {
            if (Math.abs(startY - windowLayoutParams.y) <= maxDiff || windowLayoutParams.y <= ActionBar.getCurrentActionBarHeight()) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                editor.putInt("sidey", 0);
                animators.add(ObjectAnimator.ofInt(this, "y", startY));
            } else if (Math.abs(endY - windowLayoutParams.y) <= maxDiff) {
                if (animators == null) {
                    animators = new ArrayList<>();
                }
                editor.putInt("sidey", 1);
                animators.add(ObjectAnimator.ofInt(this, "y", endY));
            } else {
                editor.putFloat("py", (windowLayoutParams.y - startY) / (float) (endY - startY));
                editor.putInt("sidey", 2);
            }
            editor.commit();
        }
        if (animators != null) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator();
            }
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setInterpolator(decelerateInterpolator);
            animatorSet.setDuration(150);
            if (slideOut) {
                animators.add(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f));
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        close(false);
                        if (onCloseRunnable != null) {
                            onCloseRunnable.run();
                        }
                    }
                });
            }
            animatorSet.playTogether(animators);
            animatorSet.start();
        }
    }

    @Keep
    public int getX() {
        return windowLayoutParams.x;
    }

    @Keep
    public int getY() {
        return windowLayoutParams.y;
    }

    @Keep
    public void setX(int value) {
        windowLayoutParams.x = value;
        try {
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception ignore) {

        }
    }

    @Keep
    public void setY(int value) {
        windowLayoutParams.y = value;
        try {
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception ignore) {

        }
    }

    public static PipRoundVideoView getInstance() {
        return instance;
    }
}
