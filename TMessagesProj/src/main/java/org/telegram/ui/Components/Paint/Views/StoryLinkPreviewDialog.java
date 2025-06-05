package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessagePreviewView;
import org.telegram.ui.SecretVoicePlayer;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.PreviewView;

public class StoryLinkPreviewDialog extends Dialog {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();

    private final FrameLayout windowView;
    private final LinearLayout containerView;

    private final FrameLayout previewContainer;
    private final FrameLayout previewInnerContainer;
    private final ImageView backgroundView;
    private final FrameLayout actionBarContainer;
    private final TextView titleTextView, subtitleTextView;

    private final LinkPreview linkView;

    private final Rect insets = new Rect();
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;

    private final MessagePreviewView.ToggleButton captionButton;
    private final MessagePreviewView.ToggleButton photoButton;

    private float openProgress;

    public StoryLinkPreviewDialog(Context context, int currentAccount) {
        super(context, R.style.TransparentDialog);
        this.currentAccount = currentAccount;

        windowView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (openProgress > 0 && blurBitmapPaint != null) {
                    blurMatrix.reset();
                    final float s = (float) getWidth() / blurBitmap.getWidth();
                    blurMatrix.postScale(s, s);
                    blurBitmapShader.setLocalMatrix(blurMatrix);

                    blurBitmapPaint.setAlpha((int) (0xFF * openProgress));
                    canvas.drawRect(0, 0, getWidth(), getHeight(), blurBitmapPaint);
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    onBackPressed();
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }
        };
        windowView.setOnClickListener(v -> {
            onBackPressed();
        });

        containerView = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(600)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(heightMeasureSpec), dp(800)), MeasureSpec.EXACTLY)
                );
            }
        };
        containerView.setOrientation(LinearLayout.VERTICAL);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 8, 8, 8, 8));

        previewContainer = new FrameLayout(context) {
            private final Path path = new Path();
            private final RectF rect = new RectF();
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                path.rewind();
                rect.set(0,0,getMeasuredWidth(),getMeasuredHeight());
                path.addRoundRect(rect, dp(10), dp(10), Path.Direction.CW);
                if (linkView != null) {
                    linkView.setMaxWidth(getMeasuredWidth() - dp(32));
                }
            }

            @Override
            public void draw(Canvas canvas) {
                canvas.save();
                canvas.clipPath(path);
                super.draw(canvas);
                canvas.restore();
            }
        };
        previewContainer.setWillNotDraw(false);
        containerView.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        actionBarContainer = new FrameLayout(context);
        actionBarContainer.setBackgroundColor(0xFF1F1F1F);
        previewContainer.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        titleTextView = new TextView(context);
        titleTextView.setText(LocaleController.getString(R.string.StoryLinkPreviewTitle));
        titleTextView.setTextColor(0xFFFFFFFF);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setTypeface(AndroidUtilities.bold());
        actionBarContainer.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 18, 8.33f, 18, 0));

        subtitleTextView = new TextView(context);
        subtitleTextView.setText(LocaleController.getString(R.string.StoryLinkPreviewSubtitle));
        subtitleTextView.setTextColor(0xFF7F7F7F);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        actionBarContainer.addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 18, 31, 18, 0));

        previewInnerContainer = new FrameLayout(context) {
            private final AnimatedFloat x = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat y = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == linkView) {
                    canvas.save();
                    canvas.translate(x.set(child.getX()), y.set(child.getY()));
                    linkView.drawInternal(canvas);
                    canvas.restore();
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        previewContainer.addView(previewInnerContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 56, 0, 0));

        backgroundView = new ImageView(context);
        backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewInnerContainer.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        linkView = new LinkPreview(context, AndroidUtilities.density) {
            @Override
            public void invalidate() {
                previewInnerContainer.invalidate();
                super.invalidate();
            }
        };
        previewInnerContainer.addView(linkView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        ItemOptions options = ItemOptions.makeOptions(windowView, resourcesProvider, windowView);

        captionButton = new MessagePreviewView.ToggleButton(
            getContext(),
            R.raw.position_below, getString(R.string.StoryLinkCaptionAbove),
            R.raw.position_above, getString(R.string.StoryLinkCaptionBelow),
            resourcesProvider
        );
        captionButton.setOnClickListener(v -> {
            link.captionAbove = !link.captionAbove;
            captionButton.setState(!link.captionAbove, true);
            linkView.set(currentAccount, link, true);
        });
        options.addView(captionButton);

        photoButton = new MessagePreviewView.ToggleButton(
            context,
            R.raw.media_shrink, LocaleController.getString(R.string.LinkMediaLarger),
            R.raw.media_enlarge, LocaleController.getString(R.string.LinkMediaSmaller),
            resourcesProvider
        );
        photoButton.setOnClickListener(v -> {
            link.largePhoto = !link.largePhoto;
            photoButton.setState(!link.largePhoto, true);
            linkView.set(currentAccount, link, true);
        });
        options.addView(photoButton);
        options.addGap();
        options.add(R.drawable.msg_select, getString(R.string.ApplyChanges), this::dismiss);
        options.add(R.drawable.msg_delete, LocaleController.getString(R.string.DoNotLinkPreview), true, () -> {
            if (whenDone != null) {
                whenDone.run(null);
                whenDone = null;
            }
            dismiss();
        });
        containerView.addView(options.getLayout(), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.RIGHT | Gravity.BOTTOM));

        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets r = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                        StoryLinkPreviewDialog.this.insets.set(r.left, r.top, r.right, r.bottom);
                    } else {
                        StoryLinkPreviewDialog.this.insets.set(insets.getStableInsetLeft(), insets.getStableInsetTop(), insets.getStableInsetRight(), insets.getStableInsetBottom());
                    }
                    windowView.setPadding(StoryLinkPreviewDialog.this.insets.left, StoryLinkPreviewDialog.this.insets.top, StoryLinkPreviewDialog.this.insets.right, StoryLinkPreviewDialog.this.insets.bottom);
                    windowView.requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE);
        AndroidUtilities.setLightNavigationBar(windowView, !Theme.isCurrentThemeDark());
    }

    private ValueAnimator openAnimator;
    private void animateOpenTo(boolean open, Runnable after) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }

        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1 : 0);
        openAnimator.addUpdateListener(anm -> {
            openProgress = (float) anm.getAnimatedValue();
            containerView.setAlpha(openProgress);
            containerView.setScaleX(AndroidUtilities.lerp(.9f, 1f, openProgress));
            containerView.setScaleY(AndroidUtilities.lerp(.9f, 1f, openProgress));
            windowView.invalidate();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = open ? 1 : 0;
                containerView.setAlpha(openProgress);
                containerView.setScaleX(AndroidUtilities.lerp(.9f, 1f, openProgress));
                containerView.setScaleY(AndroidUtilities.lerp(.9f, 1f, openProgress));
                windowView.invalidate();
                if (after != null) {
                    AndroidUtilities.runOnUIThread(after);
                }
            }
        });
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(open ? 420 : 320);
        openAnimator.start();
    }

    private void prepareBlur(View withoutView) {
        if (withoutView != null) {
            withoutView.setVisibility(View.INVISIBLE);
        }
        AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
            if (withoutView != null) {
                withoutView.setVisibility(View.VISIBLE);
            }
            blurBitmap = bitmap;

            blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            ColorMatrix colorMatrix = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .08f : +.25f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? -.02f : -.07f);
            blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            blurMatrix = new Matrix();
        }, 14);
    }

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        super.show();
        prepareBlur(null);
        animateOpenTo(true, null);
    }

    public boolean isShowing() {
        return !dismissing;
    }

    private boolean dismissing = false;

    @Override
    public void dismiss() {
        if (dismissing) return;
        if (whenDone != null) {
            whenDone.run(link);
            whenDone = null;
        }
        dismissing = true;
        animateOpenTo(false, () -> {
            AndroidUtilities.runOnUIThread(super::dismiss);
        });
        windowView.invalidate();
    }

    private LinkPreview.WebPagePreview link;
    private Utilities.Callback<LinkPreview.WebPagePreview> whenDone;

    public void set(LinkPreview.WebPagePreview link, Utilities.Callback<LinkPreview.WebPagePreview> whenDone) {
        this.link = link;
        final boolean hasPhoto = link != null && link.webpage != null && (link.webpage.photo != null || MessageObject.isVideoDocument(link.webpage.document));
        photoButton.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        linkView.set(currentAccount, link, false);
        captionButton.setState(!link.captionAbove, false);
        photoButton.setState(!link.largePhoto, false);
        this.whenDone = whenDone;
    }

    public void setStoryPreviewView(PreviewView previewView) {
        backgroundView.setImageDrawable(new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.translate(getBounds().left, getBounds().top);
                previewView.draw(canvas);
                canvas.restore();
            }
            @Override
            public void setAlpha(int alpha) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
            @Override
            public int getIntrinsicWidth() {
                return previewView.getWidth();
            }
            @Override
            public int getIntrinsicHeight() {
                return previewView.getHeight();
            }
        });
    }


}
