/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.os.Build;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.ChatActivity;

import java.io.File;

public class InstantCameraView extends FrameLayout {

    private FrameLayout cameraContainer;
    private CameraView cameraView;
    private ChatActivity baseFragment;
    private View actionBar;

    private int[] position = new int[2];

    private AnimatorSet animatorSet;

    private boolean deviceHasGoodCamera;
    private boolean requestingPermissions;
    private File cameraFile;
    private long recordStartTime;
    private boolean recording;

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording) {
                return;
            }
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordProgressChanged, System.currentTimeMillis() - recordStartTime, 0.0);
            AndroidUtilities.runOnUIThread(timerRunnable, 50);
        }
    };

    public InstantCameraView(Context context, ChatActivity parentFragment, View actionBarOverlay) {
        super(context);
        actionBar = actionBarOverlay;
        setBackgroundColor(0x7f000000);
        baseFragment = parentFragment;

        if (Build.VERSION.SDK_INT >= 21) {
            cameraContainer = new FrameLayout(context);
        } else {
            final Path path = new Path();
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xff000000);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            cameraContainer = new FrameLayout(context) {

                @Override
                protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                    super.onSizeChanged(w, h, oldw, oldh);
                    path.reset();
                    path.addCircle(w / 2, h / 2, w / 2, Path.Direction.CW);
                    path.toggleInverseFillType();
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    canvas.drawPath(path, paint);
                }
            };
        }
        final int size;
        if (AndroidUtilities.isTablet()) {
            size = AndroidUtilities.dp(100);
        } else {
            size = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 2;
        }
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        layoutParams.bottomMargin = AndroidUtilities.dp(48);
        addView(cameraContainer, layoutParams);
        if (Build.VERSION.SDK_INT >= 21) {
            cameraContainer.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, size, size);
                }
            });
            cameraContainer.setClipToOutline(true);
        } else {
            cameraContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        setVisibility(GONE);
    }

    public void checkCamera(boolean request) {
        if (baseFragment == null) {
            return;
        }
        boolean old = deviceHasGoodCamera;
        if (Build.VERSION.SDK_INT >= 23) {
            if (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                if (request) {
                    baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 17);
                }
                deviceHasGoodCamera = false;
            } else {
                CameraController.getInstance().initCamera();
                deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
            }
        } else if (Build.VERSION.SDK_INT >= 16) {
            CameraController.getInstance().initCamera();
            deviceHasGoodCamera = CameraController.getInstance().isCameraInitied();
        }
        if (deviceHasGoodCamera && baseFragment != null) {
            showCamera();
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        actionBar.setVisibility(visibility);
        setAlpha(0.0f);
        actionBar.setAlpha(0.0f);
        cameraContainer.setAlpha(0.0f);
        cameraContainer.setScaleX(0.1f);
        cameraContainer.setScaleY(0.1f);
        if (cameraContainer.getMeasuredWidth() != 0) {
            cameraContainer.setPivotX(cameraContainer.getMeasuredWidth() / 2);
            cameraContainer.setPivotY(cameraContainer.getMeasuredHeight() / 2);
        }
    }

    @TargetApi(16)
    public void showCamera() {
        if (cameraView != null) {
            return;
        }
        setVisibility(VISIBLE);
        cameraView = new CameraView(getContext(), true);
        cameraView.setMirror(true);
        cameraContainer.addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        cameraView.setDelegate(new CameraView.CameraViewDelegate() {
            @Override
            public void onCameraCreated(Camera camera) {

            }

            @Override
            public void onCameraInit() {
                if (Build.VERSION.SDK_INT >= 23) {
                    if (baseFragment.getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestingPermissions = true;
                        baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                        return;
                    }
                }
                try {
                    Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(50);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.lockOrientation(baseFragment.getParentActivity());
                cameraFile = AndroidUtilities.generateVideoPath();
                CameraController.getInstance().recordVideo(cameraView.getCameraSession(), cameraFile, new CameraController.VideoTakeCallback() {
                    @Override
                    public void onFinishVideoRecording(final Bitmap thumb) {
                        if (cameraFile == null || baseFragment == null) {
                            return;
                        }
                        AndroidUtilities.addMediaToGallery(cameraFile.getAbsolutePath());
                        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
                        videoEditedInfo.bitrate = -1;
                        videoEditedInfo.originalPath = cameraFile.getAbsolutePath();
                        videoEditedInfo.startTime = videoEditedInfo.endTime = -1;
                        videoEditedInfo.estimatedSize = cameraFile.length();
                        //videoEditedInfo.estimatedDuration = cameraFile.length();

                        baseFragment.sendMedia(new MediaController.PhotoEntry(0, 0, 0, cameraFile.getAbsolutePath(), 0, true), null);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        recording = true;
                        recordStartTime = System.currentTimeMillis();
                        AndroidUtilities.runOnUIThread(timerRunnable);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStarted);
                        startAnimation(true);
                    }
                }, true);
            }
        });
    }

    public FrameLayout getCameraContainer() {
        return cameraContainer;
    }

    public void startAnimation(boolean open) {
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(actionBar, "alpha", open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(this, "alpha", open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(cameraContainer, "alpha", open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(cameraContainer, "scaleX", open ? 1.0f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, "scaleY", open ? 1.0f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, "translationY", open ? getMeasuredHeight() / 2 : 0, open ? 0 : getMeasuredHeight() / 2)
        );
        if (!open) {
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        hideCamera(true);
                        setVisibility(GONE);
                    }
                }
            });
        }
        animatorSet.setDuration(180);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    public Rect getCameraRect() {
        cameraContainer.getLocationOnScreen(position);
        return new Rect(position[0], position[1], cameraContainer.getWidth(), cameraContainer.getHeight());
    }

    public void send() {
        if (cameraView == null || cameraFile == null) {
            return;
        }
        recording = false;
        AndroidUtilities.cancelRunOnUIThread(timerRunnable);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStopped);
        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
        //startAnimation(false);
    }

    public void cancel() {
        if (cameraView == null || cameraFile == null) {
            return;
        }
        recording = false;
        AndroidUtilities.cancelRunOnUIThread(timerRunnable);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStopped);
        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), true);
        cameraFile.delete();
        cameraFile = null;
        startAnimation(false);
    }

    @Override
    public void setAlpha(float alpha) {
        ColorDrawable colorDrawable = (ColorDrawable) getBackground();
        colorDrawable.setAlpha((int) (0x7f * alpha));
    }

    public void hideCamera(boolean async) {
        if (/*!deviceHasGoodCamera || */cameraView == null) {
            return;
        }
        cameraView.destroy(async, null);
        cameraContainer.removeView(cameraView);
        cameraView = null;
    }
}
