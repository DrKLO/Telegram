package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BitmapShaderTools;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.LaunchActivity;
import org.webrtc.RendererCommon;

import java.io.File;
import java.io.FileOutputStream;

@TargetApi(21)
public abstract class PrivateVideoPreviewDialogNew extends FrameLayout implements VoIPService.StateListener {

    private boolean isDismissed;

    private FrameLayout viewPager;
    private TextView positiveButton;
    private LinearLayout titlesLayout;
    private VoIpBitmapTextView[] titles;
    private VoIPTextureView textureView;
    private int visibleCameraPage = 1;
    private boolean cameraReady;
    private ActionBar actionBar;

    private float pageOffset;
    private int strangeCurrentPage;
    private int realCurrentPage;
    private int previousPage = -1;

    private float openProgress1 = 0f;
    private float openProgress2 = 0f;
    private float closeProgress = 0f;
    private float openTranslationX;
    private float openTranslationY;
    private final float startLocationX;
    private final float startLocationY;
    private final Path clipPath = new Path();
    private final Camera camera = new Camera();
    private final Matrix matrixRight = new Matrix();
    private final Matrix matrixLeft = new Matrix();
    private boolean positiveButtonDrawText;

    private final BitmapShaderTools bgGreenShaderTools = new BitmapShaderTools(80, 80);
    private final BitmapShaderTools bgBlueVioletShaderTools = new BitmapShaderTools(80, 80);
    private final MotionBackgroundDrawable bgGreen = new MotionBackgroundDrawable(0xFF5FD051, 0xFF00B48E, 0xFFA9CC66, 0xFF5AB147, 0, false, true);
    private final MotionBackgroundDrawable bgBlueViolet = new MotionBackgroundDrawable(0xFF00A3E6, 0xFF296EF7, 0xFF18CEE2, 0xFF3FB2FF, 0, false, true);
    private final GestureDetector scrollGestureDetector;

    public PrivateVideoPreviewDialogNew(Context context, float startLocationX, float startLocationY) {
        super(context);

        this.startLocationX = startLocationX;
        this.startLocationY = startLocationY;
        titles = new VoIpBitmapTextView[3];
        scrollGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private boolean startDragging;
            private boolean lockDragging;

            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                startDragging = true;
                return super.onDown(e);
            }

            @Override
            public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                float dx = e1.getX() - e2.getX();
                float dy = e1.getY() - e2.getY();
                if (Math.abs(dx) > AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy && startDragging && !lockDragging) {
                    startDragging = false;
                    Runnable action = () -> {
                        if (dx > 0) {
                            if (realCurrentPage < 2) {
                                setCurrentPage(realCurrentPage + 1, true);
                            }
                        } else {
                            if (realCurrentPage > 0) {
                                setCurrentPage(realCurrentPage - 1, true);
                            }
                        }
                        lockDragging = false;
                    };
                    if (scrollAnimator != null) {
                        lockDragging = true;
                        AndroidUtilities.runOnUIThread(action, scrollAnimator.getDuration() - scrollAnimator.getCurrentPlayTime() + 50);
                    } else {
                        action.run();
                    }
                }
                return super.onScroll(e1, e2, distanceX, distanceY);
            }
        });
        viewPager = new FrameLayout(context) {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                scrollGestureDetector.onTouchEvent(event);
                return super.onTouchEvent(event);
            }
        };
        viewPager.setClickable(true);

        addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        textureView = new VoIPTextureView(context, false, false);
        textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        textureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
        textureView.clipToTexture = true;
        textureView.renderer.setAlpha(0);
        textureView.renderer.setRotateTextureWithScreen(true);
        textureView.renderer.setUseCameraRotation(true);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar = new ActionBar(context);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss(false, false);
                }
            }
        });
        addView(actionBar);

        positiveButton = new TextView(getContext()) {
            private final Paint whitePaint = new Paint();
            private final Paint[] gradientPaint = new Paint[titles.length];

            {
                bgGreen.setBounds(0, 0, 80, 80);
                bgBlueViolet.setBounds(0, 0, 80, 80);
                bgGreenShaderTools.setBounds(0, 0, 80, 80);
                bgBlueVioletShaderTools.setBounds(0, 0, 80, 80);
                bgGreen.setAlpha(255);
                bgBlueViolet.setAlpha(255);
                bgGreenShaderTools.getCanvas().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                bgBlueVioletShaderTools.getCanvas().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                bgGreen.draw(bgGreenShaderTools.getCanvas());
                bgBlueViolet.draw(bgBlueVioletShaderTools.getCanvas());
                whitePaint.setColor(Color.WHITE);
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                for (int a = 0; a < gradientPaint.length; a++) {
                    if (a == 0) {
                        gradientPaint[a] = bgGreenShaderTools.paint;
                    } else if (a == 1) {
                        gradientPaint[a] = bgBlueVioletShaderTools.paint;
                    } else {
                        gradientPaint[a] = bgGreenShaderTools.paint;
                    }
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                bgGreenShaderTools.setBounds(-getX(), -getY(), PrivateVideoPreviewDialogNew.this.getWidth() - getX(), PrivateVideoPreviewDialogNew.this.getHeight() - getY());
                bgBlueVioletShaderTools.setBounds(-getX(), -getY(), PrivateVideoPreviewDialogNew.this.getWidth() - getX(), PrivateVideoPreviewDialogNew.this.getHeight() - getY());

                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                gradientPaint[strangeCurrentPage].setAlpha(255);
                int round = AndroidUtilities.dp(8) + (int) ((AndroidUtilities.dp(26) - AndroidUtilities.dp(8)) * (1f - openProgress1));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, round, round, gradientPaint[strangeCurrentPage]);
                if (pageOffset > 0 && strangeCurrentPage + 1 < gradientPaint.length) {
                    gradientPaint[strangeCurrentPage + 1].setAlpha((int) (255 * pageOffset));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, round, round, gradientPaint[strangeCurrentPage + 1]);
                }
                if (openProgress1 < 1f) {
                    whitePaint.setAlpha((int) (255 * (1f - openProgress1)));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, round, round, whitePaint);
                }
                super.onDraw(canvas);

                if (positiveButtonDrawText) {
                    int xPos = (getWidth() / 2);
                    int yPos = (int) ((getHeight() / 2) - ((positiveButton.getPaint().descent() + positiveButton.getPaint().ascent()) / 2));
                    canvas.drawText(LocaleController.getString("VoipShareVideo", R.string.VoipShareVideo), xPos, yPos, positiveButton.getPaint());
                }
            }
        };
        positiveButton.setMaxLines(1);
        positiveButton.setEllipsize(null);
        positiveButton.setMinWidth(AndroidUtilities.dp(64));
        positiveButton.setTag(Dialog.BUTTON_POSITIVE);
        positiveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        positiveButton.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        positiveButton.setGravity(Gravity.CENTER);
        positiveButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        positiveButton.getPaint().setTextAlign(Paint.Align.CENTER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            positiveButton.setForeground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_nameText), (int) (255 * 0.3f))));
        }
        positiveButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        positiveButton.setOnClickListener(view -> {
            if (isDismissed) {
                return;
            }
            if (realCurrentPage == 0) {
                MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                ((Activity) getContext()).startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), LaunchActivity.SCREEN_CAPTURE_REQUEST_CODE);
            } else {
                dismiss(false, true);
            }
        });

        addView(positiveButton, LayoutHelper.createFrame(52, 52, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 80));

        titlesLayout = new LinearLayout(context) {

            @Override
            protected void dispatchDraw(Canvas canvas) {
                int halfWidth = getWidth() / 2;
                int halfHeight = getHeight() / 2;

                camera.save();
                camera.rotateY(7);
                camera.getMatrix(matrixRight);
                camera.restore();
                matrixRight.preTranslate(-halfWidth, -halfHeight);
                matrixRight.postTranslate(halfWidth, halfHeight);
                canvas.save();
                canvas.clipRect(halfWidth, 0, getWidth(), getHeight());
                canvas.concat(matrixRight);
                super.dispatchDraw(canvas);
                canvas.restore();

                camera.save();
                camera.rotateY(-7);
                camera.getMatrix(matrixLeft);
                camera.restore();
                matrixLeft.preTranslate(-halfWidth, -halfHeight);
                matrixLeft.postTranslate(halfWidth, halfHeight);
                canvas.save();
                canvas.clipRect(0, 0, halfWidth, getHeight());
                canvas.concat(matrixLeft);
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        titlesLayout.setClipChildren(false);
        addView(titlesLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM));

        for (int i = 0; i < titles.length; i++) {
            String text;
            if (i == 0) {
                text = LocaleController.getString("VoipPhoneScreen", R.string.VoipPhoneScreen);
            } else if (i == 1) {
                text = LocaleController.getString("VoipFrontCamera", R.string.VoipFrontCamera);
            } else {
                text = LocaleController.getString("VoipBackCamera", R.string.VoipBackCamera);
            }
            titles[i] = new VoIpBitmapTextView(context, text);
            titles[i].setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(10), 0);
            titlesLayout.addView(titles[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            final int num = i;
            titles[i].setOnClickListener(view -> {
                if (scrollAnimator != null || view.getAlpha() == 0f) return;
                setCurrentPage(num, true);
            });
        }

        setWillNotDraw(false);

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            textureView.renderer.setMirror(service.isFrontFaceCamera());
            textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
                @Override
                public void onFirstFrameRendered() {

                }

                @Override
                public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

                }
            });
            service.setLocalSink(textureView.renderer, false);
        }
        createPages(viewPager);

        ValueAnimator openAnimator1 = ValueAnimator.ofFloat(0f, 1f);
        openAnimator1.addUpdateListener(animation -> {
            openProgress1 = (float) animation.getAnimatedValue();
            float startLocationXWithOffset = startLocationX + AndroidUtilities.dp(28);
            float startLocationYWithOffset = startLocationY + AndroidUtilities.dp(52);
            openTranslationX = startLocationXWithOffset - (startLocationXWithOffset * openProgress1);
            openTranslationY = startLocationYWithOffset - (startLocationYWithOffset * openProgress1);
            invalidate();
        });
        openAnimator1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isDismissed) {
                    afterOpened();
                }
            }
        });
        ValueAnimator openAnimator2 = ValueAnimator.ofFloat(0f, 1f);
        openAnimator2.addUpdateListener(animation -> {
            openProgress2 = (float) animation.getAnimatedValue();
            int w = AndroidUtilities.displaySize.x - AndroidUtilities.dp(36) - AndroidUtilities.dp(52);
            positiveButton.getLayoutParams().width = AndroidUtilities.dp(52) + (int) (w * openProgress2);
            positiveButton.requestLayout();
        });
        int openAnimationTime = 320;
        openAnimator1.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openAnimator1.setDuration(openAnimationTime);
        openAnimator1.start();
        openAnimator2.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openAnimator2.setDuration(openAnimationTime);
        openAnimator2.setStartDelay(openAnimationTime / 10);
        openAnimator2.start();
        titlesLayout.setAlpha(0f);
        titlesLayout.setScaleY(0.8f);
        titlesLayout.setScaleX(0.8f);
        titlesLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(250).start();
        positiveButton.setTranslationY(AndroidUtilities.dp(53));
        positiveButton.setTranslationX(startLocationX - (AndroidUtilities.displaySize.x / 2f) + AndroidUtilities.dp(8) + AndroidUtilities.dp(26));
        positiveButton.animate().translationY(0).translationX(0).setDuration(openAnimationTime).start();
        positiveButtonDrawText = true;
        setCurrentPage(1, false);
    }

    private void showStub(boolean show, boolean animate) {
        ImageView imageView = viewPager.findViewWithTag("image_stab");
        if (!show) {
            imageView.setVisibility(GONE);
            return;
        }
        Bitmap bitmap = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb" + visibleCameraPage + ".jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {

        }

        if (bitmap != null && bitmap.getPixel(0, 0) != 0) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.icplaceholder);
        }
        if (animate) {
            imageView.setVisibility(VISIBLE);
            imageView.setAlpha(0f);
            imageView.animate().alpha(1f).setDuration(250).start();
        } else {
            imageView.setAlpha(1f);
            imageView.setVisibility(VISIBLE);
        }
    }

    private ValueAnimator scrollAnimator;

    private void setCurrentPage(int position, boolean animate) {
        if (strangeCurrentPage == position || realCurrentPage == position) return;

        if (animate) {
            if (realCurrentPage == 0) {
                //switch from screencast to any camera
                if (visibleCameraPage != position) {
                    visibleCameraPage = position;
                    cameraReady = false;
                    showStub(true, true);
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().switchCamera();
                    }
                } else {
                    showStub(false, false);
                    textureView.animate().alpha(1f).setDuration(250).start();
                }
            } else {
                if (position == 0) {
                    //switch to screencast from any camera
                    viewPager.findViewWithTag("screencast_stub").setVisibility(VISIBLE);
                    saveLastCameraBitmap();
                    showStub(false, false);
                    textureView.animate().alpha(0f).setDuration(250).start();
                } else {
                    //switch between cameras
                    saveLastCameraBitmap();
                    visibleCameraPage = position;
                    cameraReady = false;
                    showStub(true, false);
                    textureView.animate().alpha(0f).setDuration(250).start();
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().switchCamera();
                    }
                }
            }

            if (position > realCurrentPage) {
                //to the right
                previousPage = realCurrentPage;
                realCurrentPage = realCurrentPage + 1;
                scrollAnimator = ValueAnimator.ofFloat(0.1f, 1f);
            } else {
                //to the left
                previousPage = realCurrentPage;
                realCurrentPage = realCurrentPage - 1;
                strangeCurrentPage = position;
                scrollAnimator = ValueAnimator.ofFloat(1f, 0f);
            }

            scrollAnimator.addUpdateListener(animation -> {
                pageOffset = (float) animation.getAnimatedValue();
                updateTitlesLayout();
            });
            scrollAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    previousPage = -1;
                    strangeCurrentPage = position;
                    pageOffset = 0;
                    scrollAnimator = null;
                    updateTitlesLayout();
                }
            });
            scrollAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            scrollAnimator.setDuration(350);
            scrollAnimator.start();
        } else {
            realCurrentPage = position;
            strangeCurrentPage = position;
            pageOffset = 0;
            updateTitlesLayout();
            textureView.setVisibility(VISIBLE);
            cameraReady = false;
            visibleCameraPage = 1;
            showStub(true, false);
        }
    }

    private void createPages(FrameLayout container) {
        {
            FrameLayout frameLayout = new FrameLayout(getContext());
            frameLayout.setBackground(new MotionBackgroundDrawable(0xff212E3A, 0xff2B5B4D, 0xff245863, 0xff274558, true));

            ImageView imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.screencast_big);
            frameLayout.addView(imageView, LayoutHelper.createFrame(82, 82, Gravity.CENTER, 0, 0, 0, 60));

            TextView textView = new TextView(getContext());
            textView.setText(LocaleController.getString("VoipVideoPrivateScreenSharing", R.string.VoipVideoPrivateScreenSharing));
            textView.setGravity(Gravity.CENTER);
            textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            textView.setTextColor(0xffffffff);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 21, 28, 21, 0));
            frameLayout.setTag("screencast_stub");
            frameLayout.setVisibility(GONE);
            container.addView(frameLayout);
        }
        {
            ImageView imageView = new ImageView(getContext());
            imageView.setTag("image_stab");
            imageView.setImageResource(R.drawable.icplaceholder);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            container.addView(imageView);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (openProgress1 < 1f) {
            int maxWidth = AndroidUtilities.displaySize.x;
            int maxHeight = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight + AndroidUtilities.navigationBarHeight;
            float rounded = AndroidUtilities.dp(28) - (AndroidUtilities.dp(28) * openProgress1);
            clipPath.reset();
            clipPath.addCircle(startLocationX + AndroidUtilities.dp(33.5f), startLocationY + AndroidUtilities.dp(26.6f), AndroidUtilities.dp(26), Path.Direction.CW);
            int minWidth = AndroidUtilities.dp(52);
            int minHeight = AndroidUtilities.dp(52);
            int width = AndroidUtilities.lerp(minWidth, maxWidth, openProgress1);
            int height = AndroidUtilities.lerp(minHeight, maxHeight, openProgress1);
            float x = openTranslationX - ((1f - openProgress1) * AndroidUtilities.dp(20f));
            float y = openTranslationY - ((1f - openProgress1) * AndroidUtilities.dp(51));
            clipPath.addRoundRect(x, y, x + width, y + height, rounded, rounded, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }

        if (closeProgress > 0f) {
            int[] loc = getFloatingViewLocation();
            int x = (int) (closeProgress * loc[0]);
            int y = (int) (closeProgress * loc[1]);
            int destWidth = loc[2];
            int w = AndroidUtilities.displaySize.x;
            float currentWidth = destWidth + ((w - destWidth) * (1f - closeProgress));
            float scale = currentWidth / w;
            clipPath.reset();
            clipPath.addRoundRect(0f, 0f, getWidth() * scale, getHeight() * scale, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Path.Direction.CW);
            canvas.translate(x, y);
            canvas.clipPath(clipPath);
            canvas.scale(scale, scale);
        }

        super.dispatchDraw(canvas);
    }

    public void dismiss(boolean screencast, boolean apply) {
        if (isDismissed || openProgress1 != 1f) {
            return;
        }
        beforeClosed();
        isDismissed = true;
        saveLastCameraBitmap();
        onDismiss(screencast, apply);
        if (isHasVideoOnMainScreen() && apply) {
            ValueAnimator closeAnimator = ValueAnimator.ofFloat(0f, 1f);
            closeAnimator.addUpdateListener(animation -> {
                closeProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            closeAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            closeAnimator.setStartDelay(60);
            closeAnimator.setDuration(350);
            closeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (getParent() != null) {
                        ((ViewGroup) getParent()).removeView(PrivateVideoPreviewDialogNew.this);
                    }
                }
            });
            closeAnimator.start();
            positiveButton.animate().setStartDelay(60).alpha(0f).setDuration(100).start();
            actionBar.animate().setStartDelay(60).alpha(0f).setDuration(100).start();
            titlesLayout.animate().setStartDelay(60).alpha(0f).setDuration(100).start();
        } else {
            if (apply) {
                animate().setStartDelay(60).alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (getParent() != null) {
                            ((ViewGroup) getParent()).removeView(PrivateVideoPreviewDialogNew.this);
                        }
                    }
                });
            } else {
                ValueAnimator openAnimator1 = ValueAnimator.ofFloat(1f, 0f);
                openAnimator1.addUpdateListener(animation -> {
                    openProgress1 = (float) animation.getAnimatedValue();
                    float startLocationXWithOffset = startLocationX + AndroidUtilities.dp(28);
                    float startLocationYWithOffset = startLocationY + AndroidUtilities.dp(52);
                    openTranslationX = startLocationXWithOffset - (startLocationXWithOffset * openProgress1);
                    openTranslationY = startLocationYWithOffset - (startLocationYWithOffset * openProgress1);
                    invalidate();
                });
                openAnimator1.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) {
                            ((ViewGroup) getParent()).removeView(PrivateVideoPreviewDialogNew.this);
                        }
                    }
                });
                ValueAnimator openAnimator2 = ValueAnimator.ofFloat(1f, 0f);
                openAnimator2.addUpdateListener(animation -> {
                    openProgress2 = (float) animation.getAnimatedValue();
                    int w = AndroidUtilities.displaySize.x - AndroidUtilities.dp(36) - AndroidUtilities.dp(52);
                    positiveButton.getLayoutParams().width = AndroidUtilities.dp(52) + (int) (w * openProgress2);
                    positiveButton.requestLayout();
                });
                int closeAnimationTime = 320;
                openAnimator1.setInterpolator(CubicBezierInterpolator.DEFAULT);
                openAnimator1.setDuration(closeAnimationTime);
                openAnimator1.start();
                openAnimator2.setInterpolator(CubicBezierInterpolator.DEFAULT);
                openAnimator2.setDuration(closeAnimationTime);
                openAnimator2.start();
                titlesLayout.setAlpha(1f);
                titlesLayout.setScaleY(1f);
                titlesLayout.setScaleX(1f);
                titlesLayout.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(250).start();
                positiveButton.animate().translationY(AndroidUtilities.dp(53)).translationX(startLocationX - (AndroidUtilities.displaySize.x / 2f) + AndroidUtilities.dp(8) + AndroidUtilities.dp(26)).setDuration((long) (closeAnimationTime * 0.6f)).start();
                animate().alpha(0f).setDuration((long) (closeAnimationTime * (2f / 8f))).setStartDelay((long) (closeAnimationTime * (6f / 8f))).start();
            }
        }

        invalidate();
    }

    public void setBottomPadding(int padding) {
        LayoutParams layoutParams = (LayoutParams) positiveButton.getLayoutParams();
        layoutParams.bottomMargin = AndroidUtilities.dp(80) + padding;

        layoutParams = (LayoutParams) titlesLayout.getLayoutParams();
        layoutParams.bottomMargin = padding;
    }

    private void updateTitlesLayout() {
        View current = titles[strangeCurrentPage];
        View next = strangeCurrentPage < titles.length - 1 ? titles[strangeCurrentPage + 1] : null;

        float currentCx = current.getLeft() + current.getMeasuredWidth() / 2;
        float tx = getMeasuredWidth() / 2 - currentCx;
        if (next != null) {
            float nextCx = next.getLeft() + next.getMeasuredWidth() / 2;
            tx -= (nextCx - currentCx) * pageOffset;
        }
        for (int i = 0; i < titles.length; i++) {
            float alpha;
            float scale;
            if (i < strangeCurrentPage || i > strangeCurrentPage + 1) {
                alpha = 0.7f;
                scale = 0.9f;
            } else if (i == strangeCurrentPage) {
                //movement to the right or selected
                alpha = 1.0f - 0.3f * pageOffset;
                scale = 1.0f - 0.1f * pageOffset;
            } else {
                alpha = 0.7f + 0.3f * pageOffset;
                scale = 0.9f + 0.1f * pageOffset;
            }
            titles[i].setAlpha(alpha);
            titles[i].setScaleX(scale);
            titles[i].setScaleY(scale);
            titles[i].setTranslationX(tx);
        }
        positiveButton.invalidate();
        if (realCurrentPage == 0) {
            titles[2].setAlpha(0.7f * pageOffset);
        }
        if (realCurrentPage == 2) {
            if (pageOffset > 0f) {
                titles[0].setAlpha(0.7f * (1f - pageOffset));
            } else {
                titles[0].setAlpha(0f);
            }
        }
        if (realCurrentPage == 1) {
            if (previousPage == 0) {
                titles[2].setAlpha(0.7f * pageOffset);
            }
            if (previousPage == 2) {
                titles[0].setAlpha(0.7f * (1f - pageOffset));
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) service.registerStateListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) service.unregisterStateListener(this);
    }

    private void saveLastCameraBitmap() {
        if (!cameraReady) {
            return;
        }
        try {
            Bitmap bitmap = textureView.renderer.getBitmap();
            if (bitmap != null) {
                Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), textureView.renderer.getMatrix(), true);
                bitmap.recycle();
                bitmap = newBitmap;
                Bitmap lastBitmap = Bitmap.createScaledBitmap(bitmap, 80, (int) (bitmap.getHeight() / (bitmap.getWidth() / 80.0f)), true);
                if (lastBitmap != null) {
                    if (lastBitmap != bitmap) {
                        bitmap.recycle();
                    }
                    Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.getWidth(), lastBitmap.getHeight(), lastBitmap.getRowBytes());
                    File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb" + visibleCameraPage + ".jpg");
                    FileOutputStream stream = new FileOutputStream(file);
                    lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.close();
                    View view = viewPager.findViewWithTag("image_stab");
                    if (view instanceof ImageView) {
                        ((ImageView) view).setImageBitmap(lastBitmap);
                    }
                }
            }
        } catch (Throwable ignore) {

        }
    }

    @Override
    public void onCameraFirstFrameAvailable() {
        if (!cameraReady) {
            cameraReady = true;
            if (realCurrentPage != 0) textureView.animate().alpha(1f).setDuration(250).start();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateTitlesLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    protected void onDismiss(boolean screencast, boolean apply) {

    }

    protected int[] getFloatingViewLocation() {
        return null;
    }

    protected boolean isHasVideoOnMainScreen() {
        return false;
    }

    protected void afterOpened() {

    }

    protected void beforeClosed() {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChildWithMargins(titlesLayout, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY), 0);
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        update();
    }

    public void update() {
        if (VoIPService.getSharedInstance() != null) {
            textureView.renderer.setMirror(VoIPService.getSharedInstance().isFrontFaceCamera());
        }
    }
}
