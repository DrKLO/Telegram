package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.graphics.CornerPathEffect;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.MessageSendPreview;

import java.util.ArrayList;

public class ScrimOptions extends Dialog {
    public final Context context;
    public final Theme.ResourcesProvider resourcesProvider;
    public final int currentAccount = UserConfig.selectedAccount;

    private final android.graphics.Rect insets = new Rect();
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;

    private float openProgress;

    private final FrameLayout windowView;
    private final FrameLayout containerView;
    private ItemOptions options;
    private FrameLayout optionsContainer;
    private View optionsView;

    private ChatMessageCell scrimCell;
    private boolean isGroup;
    private Drawable scrimDrawable;
    private float scrimDrawableTx, scrimDrawableTy;

    public ScrimOptions(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);

        this.context = context;
        this.resourcesProvider = resourcesProvider;

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
                if (scrimDrawable != null) {
                    scrimDrawable.setAlpha((int) (0xFF * openProgress));
                    canvas.save();
                    canvas.translate(scrimDrawableTx * openProgress, scrimDrawableTy * openProgress);
                    scrimDrawable.draw(canvas);
                    canvas.restore();
                }
            }

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    onBackPressed();
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                ScrimOptions.this.layout();
            }
        };
        windowView.setOnClickListener(v -> onBackPressed());

        containerView = new SizeNotifierFrameLayout(context);
        containerView.setClipToPadding(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets r = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                        ScrimOptions.this.insets.set(r.left, r.top, r.right, r.bottom);
                    } else {
                        ScrimOptions.this.insets.set(insets.getStableInsetLeft(), insets.getStableInsetTop(), insets.getStableInsetRight(), insets.getStableInsetBottom());
                    }
                    containerView.setPadding(ScrimOptions.this.insets.left, ScrimOptions.this.insets.top, ScrimOptions.this.insets.right, ScrimOptions.this.insets.bottom);
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

    public void setItemOptions(ItemOptions options) {
        this.options = options;
        optionsView = options.getLayout();
        optionsContainer = new FrameLayout(context);
        optionsContainer.addView(optionsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        containerView.addView(optionsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public boolean isShowing() {
        return !dismissing;
    }

    private boolean dismissing = false;

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        super.show();
        prepareBlur(null);
        animateOpenTo(true, null);
    }

    @Override
    public void dismiss() {
        if (dismissing) return;
        dismissing = true;
        animateOpenTo(false, () -> {
            AndroidUtilities.runOnUIThread(super::dismiss);
        });
        windowView.invalidate();
    }

    public void dismissFast() {
        if (dismissing) return;
        dismissing = true;
        animateOpenTo(false, 2f, () -> {
            AndroidUtilities.runOnUIThread(super::dismiss);
        });
        windowView.invalidate();
    }

    private ValueAnimator openAnimator;
    private void animateOpenTo(boolean open, Runnable after) {
        animateOpenTo(open, 1f, after);
    }
    private void animateOpenTo(boolean open, float durationScale, Runnable after) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }

        final boolean animateOptions = false; // open && optionsView != null && optionsView instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout;
//        if (animateOptions) {
//            ActionBarPopupWindow.startAnimation((ActionBarPopupWindow.ActionBarPopupWindowLayout) optionsView);
//        }
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1 : 0);
        openAnimator.addUpdateListener(anm -> {
            openProgress = (float) anm.getAnimatedValue();
            if (!animateOptions) {
                optionsView.setScaleX(AndroidUtilities.lerp(.8f, 1f, openProgress));
                optionsView.setScaleY(AndroidUtilities.lerp(.8f, 1f, openProgress));
                optionsView.setAlpha(openProgress);
            }
//            if (scrimCell != null && !isGroup) {
//                scrimCell.setTranslationX(scrimDrawableTx * openProgress);
//                scrimCell.setTranslationY(scrimDrawableTy * openProgress);
//                scrimCell.invalidate();
//                scrimCell.invalidateOutbounds();
//                if (scrimCell.getParent() instanceof View) {
//                    ((View) scrimCell.getParent()).invalidate();
//                }
//            }
            windowView.invalidate();
            containerView.invalidate();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = open ? 1 : 0;
                if (!animateOptions) {
                    optionsView.setScaleX(AndroidUtilities.lerp(.8f, 1f, openProgress));
                    optionsView.setScaleY(AndroidUtilities.lerp(.8f, 1f, openProgress));
                    optionsView.setAlpha(openProgress);
                }
//                if (scrimCell != null && !isGroup) {
//                    scrimCell.setTranslationX(scrimDrawableTx * openProgress);
//                    scrimCell.setTranslationY(scrimDrawableTy * openProgress);
//                    scrimCell.invalidate();
//                    scrimCell.invalidateOutbounds();
//                    if (scrimCell.getParent() instanceof View) {
//                        ((View) scrimCell.getParent()).invalidate();
//                    }
//                }
                windowView.invalidate();
                containerView.invalidate();
                if (after != null) {
                    AndroidUtilities.runOnUIThread(after);
                }
            }
        });
        final long duration = 350;
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(duration);
        openAnimator.start();
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

    public void layout() {
        Rect bounds;
        if (scrimDrawable != null) {
            bounds = scrimDrawable.getBounds();
        } else {
            return;
        }

        if (optionsContainer != null) {
            boolean right = false;
            boolean bottom = false;
            if (bounds.right - optionsContainer.getMeasuredWidth() < dp(8)) {
                optionsView.setPivotX(dp(6));
                optionsContainer.setX(Math.min(containerView.getWidth() - optionsContainer.getWidth(), bounds.left - dp(10)) - containerView.getX());
            } else {
                right = true;
                optionsView.setPivotX(optionsView.getMeasuredWidth() - dp(6));
                optionsContainer.setX(Math.max(dp(8), bounds.right + dp(4) - optionsContainer.getMeasuredWidth()) - containerView.getX());
            }
            scrimDrawableTx = right ? optionsContainer.getX() + optionsContainer.getWidth() - dp(6) - bounds.right : optionsContainer.getX() + dp(10) - bounds.left;
            scrimDrawableTy = 0f;

            if (bounds.bottom + optionsContainer.getMeasuredHeight() > windowView.getMeasuredHeight() - dp(16)) {
                bottom = true;
                optionsView.setPivotY(optionsView.getMeasuredHeight() - dp(6));
                optionsContainer.setY(bounds.top - dp(4) - optionsContainer.getMeasuredHeight() - containerView.getY());
            } else {
                optionsView.setPivotY(dp(6));
                optionsContainer.setY(Math.min(windowView.getHeight() - optionsContainer.getMeasuredHeight() - dp(16), bounds.bottom) - containerView.getY());
            }
            options.setSwipebackGravity(right, bottom);
        }
    }

    public void setScrim(ChatMessageCell cell) {

    }

    public void setScrim(ChatMessageCell cell, CharacterStyle link) {
        if (cell == null) return;

        scrimCell = cell;
        isGroup = cell.getCurrentMessagesGroup() != null;

        int blockNum = -1;
        int start = 0, end = 0;
        float x = 0, y = 0;
        float rtloffset = 0;
        StaticLayout layout = null;

        MessageObject messageObject = cell.getMessageObject();
        ArrayList<MessageObject.TextLayoutBlock> textblocks = null;
        if (cell.getCaptionLayout() != null) {
            x = cell.getCaptionX();
            y = cell.getCaptionY();
            textblocks = cell.getCaptionLayout().textLayoutBlocks;
            rtloffset = cell.getCaptionLayout().textXOffset;
        }
        if (textblocks == null) {
            x = cell.getTextX();
            y = cell.getTextY();
            textblocks = messageObject.textLayoutBlocks;
            rtloffset = messageObject.textXOffset;
        }
        if (textblocks == null) return;

        for (int i = 0; i < textblocks.size(); ++i) {
            MessageObject.TextLayoutBlock textblock = textblocks.get(i);
            StaticLayout textlayout = textblock.textLayout;
            if (textlayout == null) continue;
            if (!(textlayout.getText() instanceof Spanned)) continue;

            CharacterStyle[] spans = ((Spanned) textlayout.getText()).getSpans(0, textlayout.getText().length(), CharacterStyle.class);
            if (spans == null) continue;
            boolean found = false;
            for (int j = 0; j < spans.length; ++j) {
                if (spans[j] == link) {
                    found = true;
                    break;
                }
            }
            if (!found) continue;

            blockNum = i;
            layout = textlayout;

            start = ((Spanned) textlayout.getText()).getSpanStart(link);
            end = ((Spanned) textlayout.getText()).getSpanEnd(link);

            x += (textblock.isRtl() ? (int) Math.ceil(rtloffset) : 0);
            y += textblock.padTop + textblock.textYOffset(textblocks, cell.transitionParams);
        }

        if (layout == null) return;

        final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outBubble : Theme.key_chat_inBubble, resourcesProvider));
        backgroundPaint.setPathEffect(new CornerPathEffect(dp(5)));

        final LinkPath path = new LinkPath(true);
        path.setCurrentLayout(layout, start, 0);
        layout.getSelectionPath(start, end, path);
        path.closeRects();

        final RectF pathBounds = new RectF();
        path.computeBounds(pathBounds, true);

        Bitmap bitmap = null;
        int w = (int) (pathBounds.width() + path.getRadius());
        if (cell != null && cell.drawBackgroundInParent() && w > 0 && pathBounds.height() > 0) {
            bitmap = Bitmap.createBitmap(w, (int) pathBounds.height(), Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(0, 0, w, pathBounds.height(), paint);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFFFFFFFF);
            paint.setPathEffect(new CornerPathEffect(dp(5)));
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.translate(-pathBounds.left, -pathBounds.top);
            canvas.drawPath(path, paint);
        }
        final Bitmap finalBitmap = bitmap;
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        bitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        cell.setupTextColors();
        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(layout.getPaint().getColor());
        paint.linkColor = layout.getPaint().linkColor;
        paint.setTextSize(layout.getPaint().getTextSize());
        paint.setTextAlign(layout.getPaint().getTextAlign());
        paint.setTypeface(layout.getPaint().getTypeface());
        final StaticLayout finalLayout = new StaticLayout(layout.getText(), paint, layout.getWidth(), layout.getAlignment(), 1f, 0f, false);
        final int finalBlockNum = blockNum;
        final int[] pos = new int[2];
        cell.getLocationOnScreen(pos);
        final int[] pos2 = new int[2];
        pos2[0] = pos[0] + (int) x;
        pos2[1] = pos[1] + (int) y;

        scrimDrawable = new Drawable() {
            private int alpha = 0xFF;
            @Override
            public void draw(@NonNull Canvas canvas) {
                if (alpha <= 0)
                    return;

                AndroidUtilities.rectTmp.set(getBounds());
                AndroidUtilities.rectTmp.left -= path.getRadius() / 2f;
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, alpha, Canvas.ALL_SAVE_FLAG);
                canvas.translate(pos2[0], pos2[1]);

                if (cell != null && cell.drawBackgroundInParent()) {
                    canvas.translate(-pos2[0], -pos2[1]);
                    canvas.translate(pos[0], pos[1]);
                    cell.drawBackgroundInternal(canvas, true);
                    canvas.translate(-pos[0], -pos[1]);
                    canvas.translate(pos2[0], pos2[1]);
                    if (finalBitmap != null) {
                        canvas.save();
                        canvas.drawBitmap(finalBitmap, pathBounds.left, pathBounds.top, bitmapPaint);
                        canvas.restore();
                    }
                } else {
                    canvas.drawPath(path, backgroundPaint);
                }
                canvas.clipPath(path);

                canvas.save();
                canvas.translate(0, dp(1.33f));
                finalLayout.draw(canvas);
//                if (cell != null && cell.linkBlockNum == finalBlockNum) {
//                    cell.links.draw(canvas);
//                }
//                cell.drawProgressLoadingLink(canvas, finalBlockNum);
                canvas.restore();

                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
                this.alpha = alpha;
            }
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        };

        int left = (int) (pos[0] + x + pathBounds.left + path.getRadius() / 2f);
        int top = (int) (pos[1] + y + pathBounds.top);
        scrimDrawable.setBounds(left, top, left + (int) pathBounds.width(), top + (int) pathBounds.height());
    }

}
