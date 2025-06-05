package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.GradientClip;

public class BotFullscreenButtons extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path backgroundPath = new Path();
    private final Paint downloadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path downloadPath = new Path();

    private final RectF insets = new RectF();

    private final RectF leftMenu = new RectF();
    private final ButtonBounce nullBounce = new ButtonBounce(null);
    private final RectF closeRect = new RectF();
    private final RectF closeRectArea = new RectF();
    private final ButtonBounce closeBounce = new ButtonBounce(this);
    private final RectF rightMenu = new RectF();
    private final RectF collapseRect = new RectF();
    private final RectF collapseClickRect = new RectF();
    private final ButtonBounce collapseBounce = new ButtonBounce(this);
    private final RectF menuRect = new RectF();
    private final RectF menuClickRect = new RectF();
    private final ButtonBounce menuBounce = new ButtonBounce(this);

    private final long start;
    private boolean back;
    private final AnimatedFloat animatedBack = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean preview = true;
    private final AnimatedFloat animatedPreview = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean downloading = false;
    private final AnimatedFloat animatedDownloading = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final Text backText, closeText;
    private final GradientClip previewClip = new GradientClip();
    private Text previewText;
    private Drawable verifiedBackground;
    private Drawable verifiedForeground;

    public BotFullscreenButtons(Context context) {
        super(context);
        this.start = System.currentTimeMillis();
        iconStrokePaint.setStyle(Paint.Style.STROKE);
        iconStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        iconStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        backText = new Text(LocaleController.getString(R.string.BotFullscreenBack), 13, AndroidUtilities.bold());
        closeText = new Text(LocaleController.getString(R.string.BotFullscreenClose), 13, AndroidUtilities.bold());

        downloadPaint.setPathEffect(new CornerPathEffect(dp(1)));
        downloadPath.rewind();
        downloadPath.moveTo(-dpf2(1.33f), dpf2(0.16f));
        downloadPath.lineTo(-dpf2(1.33f), -dpf2(3.5f));
        downloadPath.lineTo(dpf2(1.33f), -dpf2(3.5f));
        downloadPath.lineTo(dpf2(1.33f), dpf2(0.16f));
        downloadPath.lineTo(dpf2(3.5f), dpf2(0.16f));
        downloadPath.lineTo(0, dpf2(3.5f));
        downloadPath.lineTo(-dpf2(3.5f), dpf2(0.16f));
        downloadPath.close();
    }

    public void setInsets(RectF rect) {
        insets.set(rect);
    }

    public void setInsets(Rect rect) {
        insets.set(rect);
    }

    private RenderNode blurNode;

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        iconPaint.setColor(0xFFFFFFFF);
        iconStrokePaint.setColor(0xFFFFFFFF);
        iconStrokePaint.setStrokeWidth(dp(2));

        backgroundPath.rewind();

        rightMenu.set(getWidth() - insets.right - dp(8 + 71.66f), insets.top + dp(8), getWidth() - insets.right - dp(8), insets.top + dp(8 + 30));
        collapseRect.set(rightMenu.left, rightMenu.top, rightMenu.centerX(), rightMenu.bottom);
        collapseClickRect.set(collapseRect.left - dp(8), collapseRect.top - dp(8), collapseRect.right, collapseRect.bottom + dp(8));
        menuRect.set(rightMenu.centerX(), rightMenu.top, rightMenu.right, rightMenu.bottom);
        menuClickRect.set(menuRect.left, menuRect.top - dp(8), menuRect.right + dp(8), menuRect.bottom + dp(8));
        backgroundPath.addRoundRect(rightMenu, dp(15), dp(15), Path.Direction.CW);

        final float back = this.animatedBack.set(this.back);
        final float preview = this.animatedPreview.set(this.preview);
        final float previewWidth = Math.min(rightMenu.left - dp(18) - (insets.left + dp(8 + 30)), previewText == null ? 0 : previewText.getCurrentWidth() + dp(verifiedBackground != null ? 30 : 12));
        final float leftTextWidth = lerp(lerp(closeText.getCurrentWidth(), backText.getCurrentWidth(), back) + dp(12), previewWidth, preview);
        leftMenu.set(insets.left + dp(8), insets.top + dp(8), insets.left + dp(8 + 30) + leftTextWidth, insets.top + dp(8 + 30));
        closeRect.set(leftMenu.left, leftMenu.top, leftMenu.left + dp(30), leftMenu.bottom);
        closeRectArea.set(closeRect);
        closeRectArea.right = lerp(leftMenu.right, closeRect.left + dp(30), preview);
        closeRectArea.inset(-dp(8), -dp(8));
        backgroundPath.addRoundRect(leftMenu, dp(15), dp(15), Path.Direction.CW);

        if (parentRenderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated() && (webView == null || webView.getLayerType() == LAYER_TYPE_HARDWARE)) {
            if (blurNode == null) {
                blurNode = new RenderNode("bot_fullscreen_blur");
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(dp(18), dp(18), Shader.TileMode.CLAMP));
            }
            RenderNode parentNode = (RenderNode) parentRenderNode;
            final int w = Math.max(1, parentNode.getWidth() - dp(16));
            final int h = Math.max(1, (int) Math.min(insets.top + dp(8 + 8 + 30), parentNode.getHeight()));

            blurNode.setPosition(0, 0, w, h);
            final Canvas blurCanvas = blurNode.beginRecording();
            blurCanvas.translate(-dp(8), 0);
            blurCanvas.drawRenderNode(parentNode);
            blurNode.endRecording();
            canvas.save();
            canvas.clipPath(backgroundPath);
            canvas.save();
            canvas.translate(dp(8), 0);
            canvas.drawRenderNode(blurNode);
            canvas.restore();
            backgroundPaint.setColor(Theme.multAlpha(0xFF000000, .22f));
            canvas.drawPaint(backgroundPaint);
            canvas.restore();
        } else {
            backgroundPaint.setColor(Theme.multAlpha(0xFF000000, .35f));
            canvas.drawPath(backgroundPath, backgroundPaint);
        }

        canvas.save();
        canvas.translate(closeRect.centerX(), closeRect.centerY());
        float s = closeBounce.getScale(0.1f);
        canvas.scale(s, s);
        canvas.translate(back * -dp(6.5f), 0);
        final float backR = lerp((float) dp(4.66f), (float) dp(5.5f), back);
        canvas.drawLine(lerp(-backR, 0, back), lerp(-backR, 0, back), +backR, +backR, iconStrokePaint);
        canvas.drawLine(lerp(-backR, 0, back), lerp(+backR, 0, back), +backR, -backR, iconStrokePaint);
        if (back > 0) {
            canvas.drawLine(0, 0, dp(11.6f) * back, 0, iconStrokePaint);
        }
        canvas.restore();

        canvas.saveLayerAlpha(leftMenu.left + dp(30) - dp(10), leftMenu.top, leftMenu.right, leftMenu.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);
        if (preview > 0 && previewText != null) {
            canvas.save();
            canvas.translate(leftMenu.left + dp(30) - previewWidth * (1.0f - preview), leftMenu.centerY());
            previewText.ellipsize(leftMenu.right - dp(verifiedBackground != null ? 30 : 12) - (leftMenu.left + dp(30)) + 2).draw(canvas, 0, 0, 0xFFFFFFFF, preview);
            canvas.translate(previewText.getWidth() + dp(5), 0);
            final int verifiedIconSize = dp(16);
            if (verifiedBackground != null) {
                verifiedBackground.setBounds(0, -verifiedIconSize / 2, verifiedIconSize, verifiedIconSize / 2);
                verifiedBackground.setAlpha((int) (0x4B * preview));
                verifiedBackground.draw(canvas);
            }
            if (verifiedForeground != null) {
                verifiedForeground.setBounds(0, -verifiedIconSize / 2, verifiedIconSize, verifiedIconSize / 2);
                verifiedForeground.setAlpha((int) (0xFF * preview));
                verifiedForeground.draw(canvas);
            }
            AndroidUtilities.rectTmp.set(leftMenu.left + dp(30) - dp(10), leftMenu.top, leftMenu.left + dp(30), leftMenu.bottom);
            previewClip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);
            canvas.restore();
        }
        if (preview < 1) {
            canvas.save();
            s = closeBounce.getScale(0.1f);
            canvas.scale(s, s, closeRect.centerX(), closeRect.centerY());
            if ((1.0f - back) > 0) {
                closeText.draw(canvas, closeRect.left + dp(30) - dp(12) * back + dp(32) * preview, closeRect.centerY(), 0xFFFFFFFF, (1.0f - back) * (1.0f - preview));
            }
            if (back > 0) {
                backText.draw(canvas, closeRect.left + dp(30) + dp(12) * (1.0f - back) + dp(32) * preview, closeRect.centerY(), 0xFFFFFFFF, back * (1.0f - preview));
            }
            canvas.restore();
        }
        canvas.restore();

        canvas.save();
        canvas.translate(collapseRect.centerX() + dp(2), collapseRect.centerY());
        s = collapseBounce.getScale(0.1f);
        canvas.scale(s, s);
        final float collapseW = dp(6), collapseH = dp(3f);
        canvas.drawLine(-collapseW, -collapseH, 0, collapseH, iconStrokePaint);
        canvas.drawLine(0, collapseH, collapseW, -collapseH, iconStrokePaint);
        canvas.restore();

        canvas.save();
        canvas.translate(menuRect.centerX() + dp(1), menuRect.centerY());
        s = menuBounce.getScale(0.1f);
        canvas.scale(s, s);
        canvas.drawCircle(0, -dp(5), dp(1.66f), iconPaint);
        canvas.drawCircle(0, 0, dp(1.66f), iconPaint);
        canvas.drawCircle(0, +dp(5), dp(1.66f), iconPaint);
        final float downloadAlpha = this.animatedDownloading.set(downloading);
        if (downloadAlpha > 0) {
            canvas.translate(-dpf2(8.166f), dpf2(3.5f));
            s = .5f + .5f * downloadAlpha;
            canvas.scale(s, s);
            downloadPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.4f));
            canvas.drawPath(downloadPath, downloadPaint);
            final float t = ((System.currentTimeMillis() - start) % 450 / 450.0f);

            float from = t, to = .5f + t;

            canvas.save();
            canvas.clipRect(-dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), from), dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), to));
            downloadPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 1.0f));
            canvas.drawPath(downloadPath, downloadPaint);
            canvas.restore();

            if (to > 1) {
                from = 0;
                to = to - 1;

                canvas.save();
                canvas.clipRect(-dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), from), dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), to));
                downloadPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 1.0f));
                canvas.drawPath(downloadPath, downloadPaint);
                canvas.restore();
            }

            invalidate();
        }
        canvas.restore();
    }

    public void setDownloading(boolean downloading) {
        if (this.downloading == downloading) return;
        this.downloading = downloading;
        invalidate();
    }

    public void setName(String name, boolean verified) {
        previewText = new Text(name, 13, AndroidUtilities.bold());
        if (!verified) {
            verifiedBackground = null;
            verifiedForeground = null;
        } else {
            verifiedBackground = getContext().getResources().getDrawable(R.drawable.verified_area).mutate();
            verifiedForeground = getContext().getResources().getDrawable(R.drawable.verified_check).mutate();
        }
    }

    private final Runnable hidePreview = () -> setPreview(false, true);

    public void setPreview(boolean preview, boolean animated) {
        AndroidUtilities.cancelRunOnUIThread(hidePreview);
        this.preview = preview;
        if (!animated) {
            this.animatedPreview.set(preview, true);
        }
        invalidate();
        if (preview) {
            AndroidUtilities.runOnUIThread(hidePreview, 2500);
        }
    }

    public Runnable onCloseClickListener;
    public Runnable onCollapseClickListener;
    public Runnable onMenuClickListener;
    public Object parentRenderNode;
    public WebView webView;

    public void setOnCloseClickListener(Runnable listener) {
        onCloseClickListener = listener;
    }
    public void setOnCollapseClickListener(Runnable listener) {
        onCollapseClickListener = listener;
    }
    public void setOnMenuClickListener(Runnable listener) {
        onMenuClickListener = listener;
    }

    public void setParentRenderNode(Object renderNode) {
        parentRenderNode = renderNode;
    }
    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    int pressed;
    private int getButton(MotionEvent e) {
        if (closeRectArea.contains(e.getX(), e.getY())) {
            return 1;
        } else if (collapseClickRect.contains(e.getX(), e.getY())) {
            return 2;
        } else if (menuClickRect.contains(e.getX(), e.getY())) {
            return 3;
        } else {
            return 0;
        }
    }

    private ButtonBounce getBounce(int button) {
        switch (button) {
            case 1: return closeBounce;
            case 2: return collapseBounce;
            case 3: return menuBounce;
            default: return nullBounce;
        }
    }

    public void setBack(boolean enable) {
        setBack(enable, true);
    }
    public void setBack(boolean enable, boolean animated) {
        this.back = enable;
        if (!animated) {
            this.animatedBack.set(enable);
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getBounce(pressed).setPressed(false);
            pressed = getButton(event);
            getBounce(pressed).setPressed(true);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (getButton(event) != pressed) {
                pressed = 0;
                getBounce(pressed).setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressed == 1 && onCloseClickListener != null) {
                onCloseClickListener.run();
            } else if (pressed == 2 && onCollapseClickListener != null) {
                onCollapseClickListener.run();
            } else if (pressed == 3 && onMenuClickListener != null) {
                onMenuClickListener.run();
            }
            getBounce(pressed).setPressed(false);
            pressed = 0;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            getBounce(pressed).setPressed(false);
            pressed = 0;
        }
        return pressed != 0;
    }

    public static class OptionsIcon extends Drawable {

        private final long start;
        private final Drawable drawable;
        private final Paint downloadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path downloadPath = new Path();

        private boolean downloading = false;
        private final AnimatedFloat animatedDownloading = new AnimatedFloat(this::invalidateSelf, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        public OptionsIcon(Context context) {
            start = System.currentTimeMillis();
            drawable = context.getResources().getDrawable(R.drawable.ic_ab_other).mutate();

            downloadPaint.setPathEffect(new CornerPathEffect(dp(1)));
            downloadPath.rewind();
            downloadPath.moveTo(-dpf2(1.33f), dpf2(0.16f));
            downloadPath.lineTo(-dpf2(1.33f), -dpf2(3.5f));
            downloadPath.lineTo(dpf2(1.33f), -dpf2(3.5f));
            downloadPath.lineTo(dpf2(1.33f), dpf2(0.16f));
            downloadPath.lineTo(dpf2(3.5f), dpf2(0.16f));
            downloadPath.lineTo(0, dpf2(3.5f));
            downloadPath.lineTo(-dpf2(3.5f), dpf2(0.16f));
            downloadPath.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            drawable.setBounds(getBounds());
            drawable.draw(canvas);

            final float downloadAlpha = this.animatedDownloading.set(downloading);
            if (downloadAlpha > 0) {
                canvas.save();
                canvas.translate(getBounds().centerX(), getBounds().centerY());

                canvas.translate(-dpf2(8.166f), dpf2(5f));
                float s = .5f + .5f * downloadAlpha;
                canvas.scale(s, s);
                downloadPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.4f));
                canvas.drawPath(downloadPath, downloadPaint);
                final float t = ((System.currentTimeMillis() - start) % 450 / 450.0f);

                float from = t, to = .5f + t;

                canvas.save();
                canvas.clipRect(-dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), from), dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), to));
                downloadPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 1.0f));
                canvas.drawPath(downloadPath, downloadPaint);
                canvas.restore();

                if (to > 1) {
                    from = 0;
                    to = to - 1;

                    canvas.save();
                    canvas.clipRect(-dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), from), dp(5), lerp(-dpf2(3.5f), dpf2(3.5f), to));
                    downloadPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 1.0f));
                    canvas.drawPath(downloadPath, downloadPaint);
                    canvas.restore();
                }
                canvas.restore();

                invalidateSelf();
            }
        }

        public void setDownloading(boolean downloading) {
            if (this.downloading == downloading) return;
            this.downloading = downloading;
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {
            drawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            downloadPaint.setColorFilter(colorFilter);
            drawable.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return drawable.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return drawable.getIntrinsicHeight();
        }
    }
}
