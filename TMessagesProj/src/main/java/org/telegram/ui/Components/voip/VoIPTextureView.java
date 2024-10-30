package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.GroupCallActivity;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class VoIPTextureView extends FrameLayout {
    final boolean isCamera;
    final boolean applyRotation;

    float roundRadius;

    private boolean screencast;

    public final TextureViewRenderer renderer;
    public TextureView blurRenderer;
    public final ImageView imageView;
    public View backgroundView;
    private FrameLayout screencastView;
    private ImageView screencastImage;
    private TextView screencastText;
    private Bitmap thumb;

    public Bitmap cameraLastBitmap;
    public float stubVisibleProgress = 1f;

    boolean animateOnNextLayout;
    long animateNextDuration;
    ArrayList<Animator> animateOnNextLayoutAnimations = new ArrayList<>();
    int animateFromHeight;
    int animateFromWidth;

    float animateFromY;
    float animateFromX;

    float clipVertical;
    float clipHorizontal;
    float currentClipVertical;
    float currentClipHorizontal;

    float aninateFromScale = 1f;
    float aninateFromScaleBlur = 1f;
    float animateFromThumbScale = 1f;
    float animateFromRendererW;
    float animateFromRendererH;

    public float scaleTextureToFill;
    private float scaleTextureToFillBlur;
    private float scaleThumb;
    float currentThumbScale;

    public static int SCALE_TYPE_NONE = 3;
    public static int SCALE_TYPE_FILL = 0;
    public static int SCALE_TYPE_FIT = 1;
    public static int SCALE_TYPE_ADAPTIVE = 2;

    public int scaleType;

    ValueAnimator currentAnimation;

    boolean applyRoundRadius;
    boolean clipToTexture;
    public float animationProgress;

    public VoIPTextureView(@NonNull Context context, boolean isCamera, boolean applyRotation) {
        this(context, isCamera, applyRotation, true, false);
    }

    public VoIPTextureView(@NonNull Context context, boolean isCamera, boolean applyRotation, boolean applyRoundRadius, boolean blurBackground) {
        super(context);
        this.isCamera = isCamera;
        this.applyRotation = applyRotation;
        imageView = new ImageView(context);

        renderer = new TextureViewRenderer(context) {
            @Override
            public void onFirstFrameRendered() {
                super.onFirstFrameRendered();
                VoIPTextureView.this.onFirstFrameRendered();
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
            }
        };
        renderer.setFpsReduction(30);
        renderer.setOpaque(false);
        renderer.setEnableHardwareScaler(true);
        renderer.setIsCamera(!applyRotation);
        if (!isCamera && applyRotation) {
            backgroundView = new View(context);
            backgroundView.setBackgroundColor(0xff1b1f23);
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            if (blurBackground) {
                blurRenderer = new TextureView(context);
                addView(blurRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            }

            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            addView(renderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        } else if (!isCamera) {
            if (blurBackground) {
                blurRenderer = new TextureView(context);
                addView(blurRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            }
            addView(renderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        } else {
            if (blurBackground) {
                blurRenderer = new TextureView(context);
                addView(blurRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            }
            addView(renderer);
        }

        addView(imageView);

        if (blurRenderer != null) {
            blurRenderer.setOpaque(false);
        }

        screencastView = new FrameLayout(getContext());
        screencastView.setBackground(new MotionBackgroundDrawable(0xff212E3A, 0xff2B5B4D, 0xff245863, 0xff274558, true));
        addView(screencastView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        screencastView.setVisibility(GONE);

        screencastImage = new ImageView(getContext());
        screencastImage.setScaleType(ImageView.ScaleType.CENTER);
        screencastImage.setImageResource(R.drawable.screencast_big);
        screencastView.addView(screencastImage, LayoutHelper.createFrame(82, 82, Gravity.CENTER, 0, 0, 0, 60));

        screencastText = new TextView(getContext());
        screencastText.setText(LocaleController.getString(R.string.VoipVideoScreenSharing));
        screencastText.setGravity(Gravity.CENTER);
        screencastText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        screencastText.setTextColor(0xffffffff);
        screencastText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        screencastText.setTypeface(AndroidUtilities.bold());
        screencastView.addView(screencastText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 21, 28, 21, 0));

        if (applyRoundRadius) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        if (roundRadius < 1) {
                            outline.setRect((int) currentClipHorizontal, (int) currentClipVertical, (int) (view.getMeasuredWidth() - currentClipHorizontal), (int) (view.getMeasuredHeight() - currentClipVertical));
                        } else {
                            outline.setRoundRect((int) currentClipHorizontal, (int) currentClipVertical, (int) (view.getMeasuredWidth() - currentClipHorizontal), (int) (view.getMeasuredHeight() - currentClipVertical), roundRadius);
                        }
                    }
                });
                setClipToOutline(true);
            }
        }

        if (isCamera) {
            if (cameraLastBitmap == null) {
                try {
                    File file = new File(ApplicationLoader.getFilesDirFixed(), "voip_icthumb.jpg");
                    cameraLastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (cameraLastBitmap == null) {
                        file = new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg");
                        cameraLastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    }
                    imageView.setImageBitmap(cameraLastBitmap);
                    imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                } catch (Throwable ignore) {

                }
            }
        }

        if (!applyRotation) {
            Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            renderer.setScreenRotation(display.getRotation());
        }
    }

    public void setScreenshareMiniProgress(float progress, boolean value) {
        if (!screencast) {
            return;
        }
        float scale = ((View) getParent()).getScaleX();
        screencastText.setAlpha(1.0f - progress);
        float sc;
        if (!value) {
            sc = 1.0f / scale - 0.4f / scale * progress;
        } else {
            sc = 1.0f - 0.4f * progress;
        }
        screencastImage.setScaleX(sc);
        screencastImage.setScaleY(sc);
        screencastImage.setTranslationY(AndroidUtilities.dp(60) * progress);
    }

    public void setIsScreencast(boolean value) {
        screencast = value;
        screencastView.setVisibility(screencast ? VISIBLE : GONE);
        if (screencast) {
            renderer.setVisibility(GONE);
            if (blurRenderer != null) {
                blurRenderer.setVisibility(GONE);
            }
            imageView.setVisibility(GONE);
        } else {
            renderer.setVisibility(VISIBLE);
            if (blurRenderer != null) {
                blurRenderer.setVisibility(VISIBLE);
            }
        }
    }

    protected void onFirstFrameRendered() {
        VoIPTextureView.this.invalidate();
        if (renderer.getAlpha() != 1f) {
            renderer.animate().setDuration(300).alpha(1f);
        }

        if (blurRenderer != null && blurRenderer.getAlpha() != 1f) {
            blurRenderer.animate().setDuration(300).alpha(1f);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (imageView.getVisibility() == View.VISIBLE && renderer.isFirstFrameRendered()) {
            stubVisibleProgress -= 16f / 150f;
            if (stubVisibleProgress <= 0) {
                stubVisibleProgress = 0;
                imageView.setVisibility(View.GONE);
            } else {
                invalidate();
                imageView.setAlpha(stubVisibleProgress);
            }
        }
    }

    public void setRoundCorners(float radius) {
        if (roundRadius != radius) {
            roundRadius = radius;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                invalidateOutline();
            } else {
                invalidate();
            }
        }
    }

    public void saveCameraLastBitmap() {
        Bitmap bitmap = renderer.getBitmap(150, 150);
        if (bitmap != null && bitmap.getPixel(0, 0) != 0) {
            Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "voip_icthumb.jpg");
                FileOutputStream stream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                stream.close();
            } catch (Throwable ignore) {

            }
        }
    }

    public void setStub(VoIPTextureView from) {
        if (screencast) {
            return;
        }
        Bitmap bitmap = from.renderer.getBitmap();
        if (bitmap == null || bitmap.getPixel(0, 0) == 0) {
            imageView.setImageDrawable(from.imageView.getDrawable());
        } else {
            imageView.setImageBitmap(bitmap);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        stubVisibleProgress = 1f;
        imageView.setVisibility(View.VISIBLE);
        imageView.setAlpha(1f);
    }

    public void animateToLayout() {
        if (animateOnNextLayout || getMeasuredHeight() == 0 || getMeasuredWidth() == 0) {
            return;
        }
        animateFromHeight = getMeasuredHeight();
        animateFromWidth = getMeasuredWidth();

        if (animateWithParent && getParent() != null) {
            View parent = (View) getParent();
            animateFromY = parent.getY();
            animateFromX = parent.getX();
        } else {
            animateFromY = getY();
            animateFromX = getX();
        }
        aninateFromScale = scaleTextureToFill;
        aninateFromScaleBlur = scaleTextureToFillBlur;
        animateFromThumbScale = scaleThumb;
        animateFromRendererW = renderer.getMeasuredWidth();
        animateFromRendererH = renderer.getMeasuredHeight();

        animateOnNextLayout = true;
        requestLayout();
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!applyRotation) {
            ignoreLayout = true;
            Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            renderer.setScreenRotation(display.getRotation());
            ignoreLayout = false;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateRendererSize();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        renderer.updateRotation();
    }

    protected void updateRendererSize() {
        if (blurRenderer != null) {
            blurRenderer.getLayoutParams().width = renderer.getMeasuredWidth();
            blurRenderer.getLayoutParams().height = renderer.getMeasuredHeight();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (blurRenderer != null) {
            scaleTextureToFillBlur = Math.max(getMeasuredHeight() / (float) blurRenderer.getMeasuredHeight(), getMeasuredWidth() / (float) blurRenderer.getMeasuredWidth());
        }

        if (!applyRotation) {
            renderer.updateRotation();
        }

        if (scaleType == SCALE_TYPE_NONE) {
            if (blurRenderer != null) {
                blurRenderer.setScaleX(scaleTextureToFillBlur);
                blurRenderer.setScaleY(scaleTextureToFillBlur);
            }
            return;
        }

        if (renderer.getMeasuredHeight() == 0 || renderer.getMeasuredWidth() == 0 || getMeasuredHeight() == 0 || getMeasuredWidth() == 0) {
            scaleTextureToFill = 1f;
            if (currentAnimation == null && !animateOnNextLayout) {
                currentClipHorizontal = 0;
                currentClipVertical = 0;
            }
        } else if (scaleType == SCALE_TYPE_FILL) {
            scaleTextureToFill = Math.max(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
        } else if (scaleType == SCALE_TYPE_ADAPTIVE) {
            //sqaud view
            if (Math.abs(getMeasuredHeight() / (float) getMeasuredWidth() - 1f) < 0.02f) {
                scaleTextureToFill = Math.max(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
            } else {
                if (getMeasuredWidth() > getMeasuredHeight() && renderer.getMeasuredHeight() > renderer.getMeasuredWidth()) {
                    scaleTextureToFill = Math.max(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), (getMeasuredWidth() / 2f ) / (float) renderer.getMeasuredWidth());
                } else {
                    scaleTextureToFill = Math.min(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
                }
            }
        } else if (scaleType == SCALE_TYPE_FIT) {
            scaleTextureToFill = Math.min(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
            if (clipToTexture && !animateWithParent && currentAnimation == null && !animateOnNextLayout) {
                currentClipHorizontal = (getMeasuredWidth() - renderer.getMeasuredWidth()) / 2f;
                currentClipVertical = (getMeasuredHeight() - renderer.getMeasuredHeight()) / 2f;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    invalidateOutline();
                }
            }
        }

        if (thumb != null) {
            scaleThumb = Math.max((getMeasuredWidth()) / (float) thumb.getWidth(), (getMeasuredHeight()) / (float) thumb.getHeight());
        }

        if (animateOnNextLayout) {
            aninateFromScale /= renderer.getMeasuredWidth() / animateFromRendererW;
            aninateFromScaleBlur /= renderer.getMeasuredWidth() / animateFromRendererW;
            animateOnNextLayout = false;
            float translationY, translationX;
            if (animateWithParent && getParent() != null) {
                View parent = (View) getParent();
                translationY = animateFromY - parent.getTop();
                translationX = animateFromX - parent.getLeft();
            } else {
                translationY = animateFromY - getTop();
                translationX = animateFromX - getLeft();
            }
            clipVertical = 0;
            clipHorizontal = 0;
            if (animateFromHeight != getMeasuredHeight()) {
                clipVertical = (getMeasuredHeight() - animateFromHeight) / 2f;
                translationY -= clipVertical;
            }
            if (animateFromWidth != getMeasuredWidth()) {
                clipHorizontal = (getMeasuredWidth() - animateFromWidth) / 2f;
                translationX -= clipHorizontal;
            }
            setTranslationY(translationY);
            setTranslationX(translationX);

            if (currentAnimation != null) {
                currentAnimation.removeAllListeners();
                currentAnimation.cancel();
            }
            renderer.setScaleX(aninateFromScale);
            renderer.setScaleY(aninateFromScale);

            if (blurRenderer != null) {
                blurRenderer.setScaleX(aninateFromScaleBlur);
                blurRenderer.setScaleY(aninateFromScaleBlur);
            }

            currentClipVertical = clipVertical;
            currentClipHorizontal = clipHorizontal;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                invalidateOutline();
            }
            invalidate();
            float fromScaleFinal = aninateFromScale;
            float fromScaleBlurFinal = aninateFromScaleBlur;
            float fromThumbScale = animateFromThumbScale;

            currentAnimation = ValueAnimator.ofFloat(1f, 0);
            float finalTranslationX = translationX;
            float finalTranslationY = translationY;
            currentAnimation.addUpdateListener(animator -> {
                float v = (float) animator.getAnimatedValue();
                animationProgress = (1f - v);
                currentClipVertical = v * clipVertical;
                currentClipHorizontal = v * clipHorizontal;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    invalidateOutline();
                }
                invalidate();

                float s = fromScaleFinal * v + scaleTextureToFill * (1f - v);
                renderer.setScaleX(s);
                renderer.setScaleY(s);

                s = fromScaleBlurFinal * v + scaleTextureToFillBlur * (1f - v);
                if (blurRenderer != null) {
                    blurRenderer.setScaleX(s);
                    blurRenderer.setScaleY(s);
                }

                setTranslationX(finalTranslationX * v);
                setTranslationY(finalTranslationY * v);
                currentThumbScale = fromThumbScale * v + scaleThumb * (1f - v);
            });
            if (animateNextDuration != 0) {
                currentAnimation.setDuration(animateNextDuration);
            } else {
                currentAnimation.setDuration(GroupCallActivity.TRANSITION_DURATION);
            }
            currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    currentClipVertical = 0;
                    currentClipHorizontal = 0;

                    renderer.setScaleX(scaleTextureToFill);
                    renderer.setScaleY(scaleTextureToFill);

                    if (blurRenderer != null) {
                        blurRenderer.setScaleX(scaleTextureToFillBlur);
                        blurRenderer.setScaleY(scaleTextureToFillBlur);
                    }

                    setTranslationY(0);
                    setTranslationX(0);

                    currentThumbScale = scaleThumb;
                    currentAnimation = null;
                }
            });
            currentAnimation.start();
            if (!animateOnNextLayoutAnimations.isEmpty()) {
                for (int i = 0; i < animateOnNextLayoutAnimations.size(); i++) {
                    animateOnNextLayoutAnimations.get(i).start();
                }
            }
            animateOnNextLayoutAnimations.clear();
            animateNextDuration = 0;
        } else {
            if (currentAnimation == null) {
                renderer.setScaleX(scaleTextureToFill);
                renderer.setScaleY(scaleTextureToFill);

                if (blurRenderer != null) {
                    blurRenderer.setScaleX(scaleTextureToFillBlur);
                    blurRenderer.setScaleY(scaleTextureToFillBlur);
                }

                currentThumbScale = scaleThumb;
            }
        }
    }

    public void setCliping(float horizontalClip, float verticalClip) {
        if (currentAnimation != null || animateOnNextLayout) {
            return;
        }
        currentClipHorizontal = horizontalClip;
        currentClipVertical = verticalClip;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            invalidateOutline();
        }
        invalidate();

    }

    boolean animateWithParent;

    public void setAnimateWithParent(boolean b) {
        animateWithParent = b;
    }

    public void synchOrRunAnimation(Animator animator) {
        if (animateOnNextLayout) {
            animateOnNextLayoutAnimations.add(animator);
        } else {
            animator.start();
        }
    }

    public void cancelAnimation() {
        animateOnNextLayout = false;
        animateNextDuration = 0;
    }

    public void setAnimateNextDuration(long animateNextDuration) {
        this.animateNextDuration = animateNextDuration;
    }

    public void setThumb(Bitmap thumb) {
        this.thumb = thumb;
    }

    public void detachBackgroundRenderer() {
        if (blurRenderer == null) return;
        ObjectAnimator animator = ObjectAnimator.ofFloat(blurRenderer, View.ALPHA, 0f).setDuration(150);
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean isCanceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                isCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isCanceled) {
                    renderer.setBackgroundRenderer(null);
                }
            }
        });
        animator.start();
    }

    public void reattachBackgroundRenderer() {
        if (blurRenderer != null) {
            renderer.setBackgroundRenderer(blurRenderer);
            ObjectAnimator.ofFloat(blurRenderer, View.ALPHA, 1f).setDuration(150).start();
        }
    }

    public void attachBackgroundRenderer() {
        if (blurRenderer != null) {
            renderer.setBackgroundRenderer(blurRenderer);
            if (!renderer.isFirstFrameRendered()) {
                blurRenderer.setAlpha(0f);
            }
        }
    }

    public boolean isInAnimation() {
        return currentAnimation != null;
    }

    public void updateRotation() {
        if (!applyRotation) {
            Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
//            renderer.setScreenRotation(display.getRotation());
        }
    }
}
