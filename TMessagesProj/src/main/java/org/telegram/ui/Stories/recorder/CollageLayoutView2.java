package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraView;
import org.telegram.messenger.video.VideoPlayerHolderBase;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;

public class CollageLayoutView2 extends FrameLayout implements ItemOptions.ScrimView {

    private final FrameLayout containerView;
    private final Theme.ResourcesProvider resourcesProvider;

    public CameraView cameraView;
    private Object cameraViewBlurRenderNode;

    @NonNull
    private CollageLayout currentLayout = new CollageLayout(".");
    public final ArrayList<Part> parts = new ArrayList<>();
    public final ArrayList<Part> removingParts = new ArrayList<>();
    public Part currentPart;
    @Nullable
    public Part nextPart;

    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path highlightPath = new Path();
    private final float[] radii = new float[8];
    private final int gradientWidth;
    private final LinearGradient gradient;
    private final Matrix gradientMatrix;

    private final BlurringShader.BlurManager blurManager;

    public CollageLayoutView2(Context context, BlurringShader.BlurManager blurManager, FrameLayout containerView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.blurManager = blurManager;
        this.containerView = containerView;
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(0xFF1F1F1F);

        final Part firstPartView = new Part();
        firstPartView.setPart(currentLayout.parts.get(0), false);
        firstPartView.setCurrent(true);
        if (attached) {
            firstPartView.imageReceiver.onAttachedToWindow();
        }
//        addView(firstPartView);
        parts.add(firstPartView);
        currentPart = firstPartView;
        nextPart = null;

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setColor(0xFFFFFFFF);
        highlightPaint.setStrokeWidth(dp(8));
        gradient = new LinearGradient(0, 0, gradientWidth = dp(300), 0, new int[] { 0, 0xFFFFFFFF, 0xFFFFFFFF, 0 }, new float[] { 0, 0.2f, 0.8f, 1.0f }, Shader.TileMode.CLAMP);
        gradientMatrix = new Matrix();
        highlightPaint.setShader(gradient);

        setWillNotDraw(false);
    }

    @Nullable
    public Part getCurrent() {
        return currentPart;
    }

    @Nullable
    public Part getNext() {
        return nextPart;
    }

    private final Runnable resetReordering = () -> {
        if (this.reordering) {
            this.reordering = false;
            invalidate();
        }
    };
    public void setLayout(CollageLayout layout, boolean animated) {
        layout = layout == null ? new CollageLayout(".") : layout;
        this.currentLayout = layout;
        AndroidUtilities.cancelRunOnUIThread(resetReordering);
        for (int i = 0; i < Math.max(layout.parts.size(), parts.size()); ++i) {
            final CollageLayout.Part part = i < layout.parts.size() ? layout.parts.get(i) : null;
            Part partView = i < parts.size() ? parts.get(i) : null;
            if (partView == null && part != null) {
                partView = new Part();
                if (attached) partView.imageReceiver.onAttachedToWindow();
                partView.setPart(part, animated);
                parts.add(partView);
            } else if (part != null) {
                partView.setPart(part, animated);
            } else if (partView != null) {
                removingParts.add(partView);
                parts.remove(partView);
                partView.setPart(null, animated);
                i--;
            }
        }
        updatePartsState();
        invalidate();
        if (animated) {
            AndroidUtilities.runOnUIThread(resetReordering, 360);
        }
    }

    public void highlight(int i) {
        for (Part part : parts) {
            if (part.index == i) {
                part.highlightAnimated.set(1.0f, true);
                invalidate();
                break;
            }
        }
    }

    public ArrayList<Integer> getOrder() {
        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < parts.size(); ++i) {
            order.add(parts.get(i).index);
        }
        return order;
    }

    protected void onLayoutUpdate(CollageLayout layout) {

    }

    public void swap(int from, int to) {
        Collections.swap(parts, from, to);
        setLayout(currentLayout, true);
        reordering = true;
        invalidate();
    }

    private void layoutOut(RectF rect, CollageLayout.Part part) {
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            w = AndroidUtilities.displaySize.x;
            h = AndroidUtilities.displaySize.y;
        }
        layout(rect, part);
        final boolean l = rect.left <= 0,  t = rect.top <= 0;
        final boolean r = rect.right >= w, b = rect.bottom >= h;
        if (l && r && !t && !b) {
            rect.offset(0, h - rect.top);
        } else if (t && b && !l && !r) {
            rect.offset(0, w - rect.left);
        } else {
            if (l && !r) {
//                rect.offset(-rect.width(), 0);
            }
            if (r && !l) {
                rect.offset(+rect.width(), 0);
            }
            if (t && !b) {
//                rect.offset(0, -rect.height());
            }
            if (b && !t) {
                rect.offset(0, +rect.height());
            }
        }
    }

    private void layout(RectF rect, CollageLayout.Part part) {
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            w = AndroidUtilities.displaySize.x;
            h = AndroidUtilities.displaySize.y;
        }
        rect.set(
            ((float) w / part.layout.columns[part.y] * part.x),
            ((float) h / part.layout.h * part.y),
            ((float) w / part.layout.columns[part.y] * (part.x + 1)),
            ((float) h / part.layout.h * (part.y + 1))
        );
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if ((child == cameraView) && AndroidUtilities.makingGlobalBlurBitmap)
            return false;
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child == cameraView) {
                child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            } else {
                Part part = null;
                for (int j = 0; j < parts.size(); ++j) {
                    if (child == parts.get(j).textureView) {
                        part = parts.get(j);
                        break;
                    }
                }
                if (part != null && part.content != null && part.content.width > 0 && part.content.height > 0) {
                    int cw = part.content.width;
                    int ch = part.content.height;
                    if ((part.content.orientation % 90) == 1) {
                        int _cw = cw;
                        cw = ch;
                        ch = _cw;
                    }
                    final float scale = Math.min(1.0f, Math.max((float) cw / w, (float) ch / h));
                    child.measure(MeasureSpec.makeMeasureSpec((int) (cw * scale), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (ch * scale), MeasureSpec.EXACTLY));
                } else {
                    child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                }
            }
        }
    }

    public void set(StoryEntry entry, boolean animated) {
        if (entry == null || entry.collageContent == null) {
            clear(true);
            return;
        }
        setLayout(entry.collage, animated);
        for (int i = 0; i < parts.size(); ++i) {
            parts.get(i).setContent(entry.collageContent.get(i));
        }
    }

    private final AnimatedFloat animatedRows = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat[] animatedColumns = new AnimatedFloat[] {
        new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT),
        new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT),
        new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT),
        new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT),
        new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
    };
    private final AnimatedFloat animatedReordering = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final float[] lefts = new float[5];
    private final float[] rights = new float[5];

    @Override
    public void drawScrim(Canvas canvas, float progress) {
        if (longPressedPart != null) {
            final CollageLayout.Part p = longPressedPart.part;
            final float H = p.layout.h;
            final float cols = animatedColumns[p.y].set(p.layout.columns[p.y]);
            rect.set(
                ((float) getMeasuredWidth() / cols * p.x),
                ((float) getMeasuredHeight() / H * p.y),
                ((float) getMeasuredWidth() / cols * (p.x + 1)),
                ((float) getMeasuredHeight() / H * (p.y + 1))
            );
            drawPart(canvas, rect, longPressedPart);
        }
    }

    @Override
    public void getBounds(RectF bounds) {
        if (longPressedPart != null) {
            final CollageLayout.Part p = longPressedPart.part;
            final float H = p.layout.h;
            final float cols = animatedColumns[p.y].set(p.layout.columns[p.y]);
            bounds.set(
                ((float) getMeasuredWidth() / cols * p.x),
                ((float) getMeasuredHeight() / H * p.y),
                ((float) getMeasuredWidth() / cols * (p.x + 1)),
                ((float) getMeasuredHeight() / H * (p.y + 1))
            );
        } else {
            bounds.set(0, 0, getWidth(), getHeight());
        }
    }

    private final RectF rect = new RectF();
    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!hasLayout() && !reordering && !this.reorderingTouch && animatedRows.get() == currentLayout.h && animatedColumns[0].get() == currentLayout.columns[0]) {
            setCameraNeedsBlur(false);
            return;
        } else if (preview) {
            setCameraNeedsBlur(false);
        }
        canvas.drawColor(0xFF1F1F1F);
        boolean blurNeedsInvalidate = false;
        final float reordering = animatedReordering.set(this.reorderingTouch);
        final float H = animatedRows.set(currentLayout.h);
        float bottom = 0;
        for (int y = 0; y < Math.ceil(H); ++y) {
            lefts[y] = getMeasuredWidth();
            rights[y] = 0;
        }
        for (int y = currentLayout.h; y < animatedColumns.length; ++y) {
            animatedColumns[y].set(1);
        }
        for (int i = 0; i < parts.size(); ++i) {
            final Part part = parts.get(i);
            final CollageLayout.Part p = part.part;
            final float cols = animatedColumns[p.y].set(p.layout.columns[p.y]);
            if (this.reordering || this.reorderingTouch) {
                AndroidUtilities.lerp(part.fromBounds, part.bounds, part.boundsTransition, rect);
            } else {
                rect.set(
                    ((float) getMeasuredWidth() / cols * p.x),
                    ((float) getMeasuredHeight() / H * p.y),
                    ((float) getMeasuredWidth() / cols * (p.x + 1)),
                    ((float) getMeasuredHeight() / H * (p.y + 1))
                );
            }
            lefts[p.y] = Math.min(lefts[p.y], rect.left);
            rights[p.y] = Math.max(rights[p.y], rect.right);
            bottom = Math.max(bottom, rect.bottom);
            if (reordering > 0 && part == reorderingPart) continue;
            if (preview && part.videoPlayer != null) {
                blurNeedsInvalidate = true;
            }
            drawPart(canvas, rect, part);
        }
        for (int i = 0; i < removingParts.size(); ++i) {
            final Part part = removingParts.get(i);
            final CollageLayout.Part p = part.part;
            final float cols = animatedColumns[p.y].set(p.y >= currentLayout.columns.length ? 1 : currentLayout.columns[p.y]);
            rect.set(
                ((float) getMeasuredWidth() / cols * p.x),
                ((float) getMeasuredHeight() / H * p.y),
                ((float) getMeasuredWidth() / cols * (p.x + 1)),
                ((float) getMeasuredHeight() / H * (p.y + 1))
            );
            lefts[p.y] = Math.min(lefts[p.y], rect.left);
            rights[p.y] = Math.max(rights[p.y], rect.right);
            bottom = Math.max(bottom, rect.bottom);
            if (preview && part.videoPlayer != null) {
                blurNeedsInvalidate = true;
            }
            drawPart(canvas, rect, part);
        }
        if (!this.reorderingTouch) {
            for (int y = 0; y < Math.ceil(H); ++y) {
                if (lefts[y] >= 0) {
                    rect.set(
                        0,
                        ((float) getMeasuredHeight() / H * y),
                        lefts[y],
                        ((float) getMeasuredHeight() / H * (y + 1))
                    );
                    drawPart(canvas, rect, null);
                }
                if (rights[y] < getMeasuredWidth()) {
                    rect.set(
                        rights[y],
                        ((float) getMeasuredHeight() / H * y),
                        (float) getMeasuredWidth(),
                        ((float) getMeasuredHeight() / H * (y + 1))
                    );
                    drawPart(canvas, rect, null);
                }
            }
            if (bottom < getMeasuredHeight()) {
                rect.set(0, bottom, getMeasuredWidth(), getMeasuredHeight());
                drawPart(canvas, rect, null);
            }
        }
        if (reordering > 0 && reorderingPart != null) {
            final Part part = reorderingPart;
            final CollageLayout.Part p = part.part;
            final float cols = animatedColumns[p.y].set(currentLayout.columns[p.y]);
            if (this.reorderingTouch) {
                AndroidUtilities.lerp(part.fromBounds, part.bounds, part.boundsTransition, rect);
            } else {
                rect.set(
                        ((float) getMeasuredWidth() / cols * p.x),
                        ((float) getMeasuredHeight() / H * p.y),
                        ((float) getMeasuredWidth() / cols * (p.x + 1)),
                        ((float) getMeasuredHeight() / H * (p.y + 1))
                );
            }
            canvas.save();
            canvas.translate(lerp(ldx, dx, part.boundsTransition) * reordering, lerp(ldy, dy, part.boundsTransition) * reordering);
            drawPart(canvas, rect, part);
            canvas.restore();
        }
        for (int i = 0; i < parts.size(); ++i) {
            final Part part = parts.get(i);
            final CollageLayout.Part p = part.part;
            final float highlight = part.highlightAnimated.set(0);
            if (highlight <= 0) continue;
            final float cols = animatedColumns[p.y].set(p.layout.columns[p.y]);
            if (this.reordering || this.reorderingTouch) {
                AndroidUtilities.lerp(part.fromBounds, part.bounds, part.boundsTransition, rect);
            } else {
                rect.set(
                    ((float) getMeasuredWidth() / cols * p.x),
                    ((float) getMeasuredHeight() / H * p.y),
                    ((float) getMeasuredWidth() / cols * (p.x + 1)),
                    ((float) getMeasuredHeight() / H * (p.y + 1))
                );
            }
            AndroidUtilities.rectTmp.set(rect);
            AndroidUtilities.rectTmp.inset(dp(4), dp(4));
            gradientMatrix.reset();
            gradientMatrix.postTranslate(rect.left + lerp(-1.4f*(float)Math.sqrt(gradientWidth*gradientWidth+gradientWidth*gradientWidth), (float)Math.sqrt(rect.width()*rect.width()+rect.height()*rect.height()), 1.0f - highlight), 0);
            gradientMatrix.postRotate(-25);
            gradient.setLocalMatrix(gradientMatrix);
            final float alpha = 1.0f; // (float) Math.pow(4 * highlight * (1 - highlight), 0.05f);
            highlightPaint.setAlpha((int) (0xFF * alpha));
            highlightPath.rewind();
            radii[0] = radii[1] = part.part.x == 0 && part.part.y == 0 ? dp(8) : 0;
            radii[1] = radii[2] = part.part.x == part.part.layout.w - 1 && part.part.y == 0 ? dp(8) : 0;
            radii[3] = radii[4] = part.part.x == part.part.layout.w - 1 && part.part.y == part.part.layout.h - 1 ? dp(8) : 0;
            radii[5] = radii[6] = part.part.x == 0 && part.part.y == part.part.layout.h - 1 ? dp(8) : 0;
            highlightPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            canvas.drawPath(highlightPath, highlightPaint);
        }
        if (blurNeedsInvalidate && blurManager != null) {
            blurManager.invalidate();
        }
    }

    public float getFilledProgress() {
        int done = 0, total = 0;
        for (int i = 0; i < parts.size(); ++i) {
            if (parts.get(i).hasContent())
                done++;
            total++;
        }
        return (float) done / total;
    }

    private final Path clipPath = new Path();
    private void drawPart(Canvas canvas, RectF rect, Part part) {
        if (AndroidUtilities.makingGlobalBlurBitmap && part == longPressedPart) {
            return;
        }
        boolean restore = false;
        if (part == reorderingPart && animatedReordering.get() > 0) {
            canvas.save();
            clipPath.rewind();
            AndroidUtilities.rectTmp.set(rect);
            AndroidUtilities.rectTmp.inset(dp(10) * animatedReordering.get(), dp(10) * animatedReordering.get());
            final float r = dp(12) * animatedReordering.get();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            restore = true;
        }
        if (part != null && part.content != null) {
            if (part.textureView != null && part.textureViewReady) {
                drawView(canvas, part.textureView, rect, 0);
            } else {
                part.imageReceiver.setImageCoords(rect.left, rect.top, rect.width(), rect.height());
                if (!part.imageReceiver.draw(canvas)) {
                    drawView(canvas, cameraView, rect, 0);
                }
            }
        } else if (part != null && part.current || AndroidUtilities.makingGlobalBlurBitmap) {
            drawView(canvas, cameraView, rect, !(part != null && part.current) ? 0.4f : 0);
        } else {
            setCameraNeedsBlur(!preview);
            if (cameraViewBlurRenderNode != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
                final RenderNode node = (RenderNode) cameraViewBlurRenderNode;
                final float scale = Math.max(rect.width() / node.getWidth(), rect.height() / node.getHeight());
                canvas.save();
                canvas.translate(rect.centerX(), rect.centerY());
                canvas.clipRect(-rect.width() / 2.0f, -rect.height() / 2.0f, rect.width() / 2.0f, rect.height() / 2.0f);
                canvas.scale(scale, scale);
                canvas.translate(-node.getWidth() / 2.0f, -node.getHeight() / 2.0f);
                canvas.drawRenderNode(node);
                canvas.drawColor(0x64000000);
                canvas.restore();
            } else {
                drawView(canvas, cameraView, rect, 0.75f);
            }
            if (cameraView != null && cameraView.blurredStubView != null && cameraView.blurredStubView.getVisibility() == View.VISIBLE && cameraView.blurredStubView.getAlpha() > 0) {
                drawView(canvas, cameraView.blurredStubView, rect, 0.4f);
            }
        }
        if (restore) {
            canvas.restore();
        }
    }

    private void drawView(Canvas canvas, View view, RectF rect, float overlayAlpha) {
        if (view == null) return;
        final float scale = Math.max(rect.width() / view.getWidth(), rect.height() / view.getHeight());
        canvas.save();
        canvas.translate(rect.centerX(), rect.centerY());
        canvas.clipRect(-rect.width() / 2.0f, -rect.height() / 2.0f, rect.width() / 2.0f, rect.height() / 2.0f);
        canvas.scale(scale, scale);
        canvas.translate(-view.getWidth() / 2.0f, -view.getHeight() / 2.0f);
        if (AndroidUtilities.makingGlobalBlurBitmap) {
            TextureView textureView;
            if (view instanceof TextureView) {
                textureView = (TextureView) view;
            } else if (view instanceof CameraView) {
                textureView = ((CameraView) view).getTextureView();
            } else {
                textureView = null;
            }
            if (textureView != null) {
                Bitmap bitmap = textureView.getBitmap();
                if (bitmap != null) {
                    canvas.scale((float) view.getWidth() / bitmap.getWidth(), (float) view.getHeight() / bitmap.getHeight());
                    canvas.drawBitmap(bitmap, 0, 0, null);
                }
            }
        } else {
            view.draw(canvas);
        }
        if (overlayAlpha > 0) {
            canvas.drawColor(Theme.multAlpha(0xFF000000, view.getAlpha() * overlayAlpha));
        }
        canvas.restore();
    }

    public void updatePartsState() {
        currentPart = null;
        nextPart = null;
        for (int i = 0; i < parts.size(); ++i) {
            Part partView = parts.get(i);
            if (!partView.hasContent()) {
                if (currentPart == null) {
                    currentPart = partView;
                } else {
                    nextPart = partView;
                    break;
                }
            }
        }
        for (int i = 0; i < parts.size(); ++i) {
            Part partView = parts.get(i);
            partView.setCurrent(partView == currentPart);
        }
    }

    public boolean swap(Part a, Part b) {
        if (a == null || b == null) return false;
        final CollageLayout.Part partA = a.part;
        a.setPart(b.part, false);
        b.setPart(partA, false);
        Collections.swap(parts, parts.indexOf(a), parts.indexOf(b));
        return true;
    }

    public boolean push(StoryEntry content) {
        if (content != null && content.isVideo) {
            boolean hasUnmuted = false;
            for (Part part : parts) {
                if (part.content != null && part.content.isVideo && part.content.videoVolume > 0) {
                    hasUnmuted = true;
                    break;
                }
            }
            if (hasUnmuted) {
                content.videoVolume = 0.0f;
            }
        }
        if (currentPart != null) {
            currentPart.setContent(content);
        }
        updatePartsState();
        requestLayout();
        return currentPart == null;
    }

    public ArrayList<StoryEntry> getContent() {
        final ArrayList<StoryEntry> array = new ArrayList<>();
        for (Part partView : parts) {
            if (partView.hasContent()) {
                array.add(partView.content);
            }
        }
        return array;
    }

    public void clear(boolean destroy) {
        for (Part part : parts) {
            part.setContent(null);
        }
        updatePartsState();
    }

    @NonNull
    public CollageLayout getLayout() {
        return currentLayout;
    }

    public boolean hasLayout() {
        return currentLayout.parts.size() > 1;
    }

    public boolean hasContent() {
        for (Part part : parts) {
            if (part.hasContent()) return true;
        }
        return false;
    }

    public void setCameraView(CameraView cameraView) {
        if (this.cameraView != cameraView && this.cameraView != null) {
            this.cameraView.unlistenDraw(this::invalidate);
            AndroidUtilities.removeFromParent(this.cameraView);
            this.cameraView = null;
            updateCameraNeedsBlur();
        }
        this.cameraView = cameraView;
        if (cameraView != null) {
            addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        }

        if (this.cameraView != null) {
            this.cameraView.unlistenDraw(this::invalidate);
        }
        this.cameraView = cameraView;
        if (cameraView != null) {
            cameraView.listenDraw(this::invalidate);
        }
        updateCameraNeedsBlur();

        invalidate();
    }

    private boolean needsBlur;
    public void setCameraNeedsBlur(boolean needsBlur) {
        if (this.needsBlur == needsBlur) return;
        this.needsBlur = needsBlur;
        updateCameraNeedsBlur();
    }
    public void updateCameraNeedsBlur() {
        final boolean canDoBlur = cameraView != null && needsBlur;
        final boolean hasBlur = cameraViewBlurRenderNode != null;
        if (canDoBlur == hasBlur) return;
        if (canDoBlur) {
            cameraViewBlurRenderNode = cameraView.getBlurRenderNode();
        } else {
            cameraViewBlurRenderNode = null;
        }
    }

    public Part getPartAt(float x, float y) {
        final float H = animatedRows.get();
        for (int i = 0; i < parts.size(); ++i) {
            final Part part = parts.get(i);
            final CollageLayout.Part p = part.part;
            final float cols = animatedColumns[p.y].get();
            rect.set(
                ((float) getMeasuredWidth() / cols * p.x),
                ((float) getMeasuredHeight() / H * p.y),
                ((float) getMeasuredWidth() / cols * (p.x + 1)),
                ((float) getMeasuredHeight() / H * (p.y + 1))
            );
            if (rect.contains(x, y)) return part;
        }
        return null;
    }

    public int getPartIndexAt(float x, float y) {
        final float H = animatedRows.get();
        for (int i = 0; i < parts.size(); ++i) {
            final Part part = parts.get(i);
            final CollageLayout.Part p = part.part;
            final float cols = animatedColumns[p.y].get();
            rect.set(
                ((float) getMeasuredWidth() / cols * p.x),
                ((float) getMeasuredHeight() / H * p.y),
                ((float) getMeasuredWidth() / cols * (p.x + 1)),
                ((float) getMeasuredHeight() / H * (p.y + 1))
            );
            if (rect.contains(x, y)) return i;
        }
        return -1;
    }

    private void onLongPress() {
        if (reorderingTouch || preview) return;
        if (longPressedPart != null && longPressedPart.videoPlayer != null) {
            longPressedPart.videoPlayer.setVolume(0.0f);
        }
        longPressedPart = pressedPart;
        if (longPressedPart == null || longPressedPart.content == null) {
            return;
        }
        if (cancelGestures != null) {
            cancelGestures.run();
        }
        if (longPressedPart.videoPlayer != null) {
            longPressedPart.videoPlayer.setVolume(longPressedPart.content.videoVolume);
        }

        FrameLayout hintLayout = new FrameLayout(getContext());
        ImageView imageView = new ImageView(getContext());
        imageView.setImageResource(R.drawable.menu_lightbulb);
        imageView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        hintLayout.addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 12, 12, 12));
        TextView textView = new TextView(getContext());
        textView.setText(LocaleController.getString(R.string.StoryCollageMenuHint));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(0xFFFFFFFF);
        hintLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 47, 8, 24, 8));

        ItemOptions i = ItemOptions.makeOptions(containerView, resourcesProvider, this);
        if (longPressedPart.content.isVideo) {
            final SliderView volumeSlider =
                new SliderView(getContext(), SliderView.TYPE_VOLUME)
                    .setMinMax(0, 1.5f)
                    .setValue(longPressedPart.content.videoVolume)
                    .setOnValueChange(volume -> {
                        longPressedPart.content.videoVolume = volume;
                        if (longPressedPart.videoPlayer != null) {
                            longPressedPart.videoPlayer.setVolume(longPressedPart.content.videoVolume);
                        }
                    });
            volumeSlider.fixWidth = dp(220);
            i.addView(volumeSlider).addSpaceGap();
        }

        i
            .setFixedWidth(220)
            .add(R.drawable.menu_camera_retake, LocaleController.getString(R.string.StoreCollageRetake), () -> {
                retake(longPressedPart);
            })
            .add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), true, () -> {
                delete(longPressedPart);
            })
            .addSpaceGap()
            .addView(hintLayout, LayoutHelper.createLinear(220, LayoutHelper.WRAP_CONTENT))
            .setOnDismiss(() -> {

            })
            .setGravity(Gravity.CENTER_HORIZONTAL)
            .allowCenter(true)
            .setBlur(true)
            .setRoundRadius(dp(12), dp(10))
            .setOnDismiss(() -> {
                if (longPressedPart != null && longPressedPart.videoPlayer != null) {
                    longPressedPart.videoPlayer.setVolume(0.0f);
                }
            })
            .show();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    public void retake(Part part) {
        if (part == null) return;
        part.setContent(null);
        updatePartsState();
        invalidate();
        if (onResetState != null) {
            onResetState.run();
        }
    }

    public void delete(Part part) {
        if (part == null) return;
        int index = parts.indexOf(part);
        if (index < 0) return;
        final CollageLayout newLayout = currentLayout.delete(currentLayout.parts.indexOf(part.part));
        if (newLayout.parts.size() <= 1) {
            clear(true);
            invalidate();
        }
        setLayout(newLayout, true);
        reordering = true;
        updatePartsState();
        invalidate();
        if (onResetState != null) {
            onResetState.run();
        }
        onLayoutUpdate(newLayout);
    }

    public float tx, ty;
    public float ldx, ldy;
    public float dx, dy;
    public boolean reorderingTouch, reordering;
    public Part pressedPart;
    public Part reorderingPart;
    public Runnable onLongPressPart;

    public Part longPressedPart;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!hasLayout() || preview) {
            return super.dispatchTouchEvent(event);
        }
        final Part hitPart = getPartAt(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            tx = event.getX();
            ty = event.getY();
            reorderingTouch = false;
            ldx = dx = 0;
            ldy = dy = 0;
            pressedPart = hitPart;
            if (pressedPart != null) {
                AndroidUtilities.runOnUIThread(onLongPressPart = this::onLongPress, ViewConfiguration.getLongPressTimeout());
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (MathUtils.distance(event.getX(), event.getY(), tx, ty) > AndroidUtilities.touchSlop * 1.2f) {
                if (onLongPressPart != null) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPressPart);
                    onLongPressPart = null;
                }
            }
            if (!reorderingTouch && getFilledProgress() >= 1 && pressedPart != null && hitPart != null && MathUtils.distance(event.getX(), event.getY(), tx, ty) > AndroidUtilities.touchSlop * 1.2f) {
                reorderingTouch = true;
                reorderingPart = pressedPart;
                ldx = dx = 0;
                ldy = dy = 0;
                invalidate();
                if (onLongPressPart != null) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPressPart);
                    onLongPressPart = null;
                }
            } else if (reorderingTouch && reorderingPart != null) {
                int newIndex = getPartIndexAt(event.getX(), event.getY());
                int thisIndex = parts.indexOf(reorderingPart);
                if (newIndex >= 0 && thisIndex >= 0 && newIndex != thisIndex) {
                    swap(thisIndex, newIndex);
                    final float H = currentLayout.h;
                    final CollageLayout.Part p = reorderingPart.part;
                    final float cols = animatedColumns[p.y].get();
                    rect.set(
                        ((float) getMeasuredWidth() / cols * p.x),
                        ((float) getMeasuredHeight() / H * p.y),
                        ((float) getMeasuredWidth() / cols * (p.x + 1)),
                        ((float) getMeasuredHeight() / H * (p.y + 1))
                    );
                    ldx = dx;
                    ldy = dy;
                    tx = rect.centerX();
                    ty = rect.centerY();
                }
                dx = event.getX() - tx;
                dy = event.getY() - ty;
                invalidate();
            } else if (pressedPart != hitPart) {
                pressedPart = null;
                if (onLongPressPart != null) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPressPart);
                    onLongPressPart = null;
                }
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedPart != null) {
                pressedPart = null;
                reorderingTouch = false;
                invalidate();
                if (onLongPressPart != null) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPressPart);
                    onLongPressPart = null;
                }
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (cancelTouch()) {
                return true;
            }
        }
        return pressedPart != null || super.dispatchTouchEvent(event);
    }

    public boolean cancelTouch() {
        if (pressedPart != null) {
            pressedPart = null;
            reorderingTouch = false;
            invalidate();
            if (onLongPressPart != null) {
                AndroidUtilities.cancelRunOnUIThread(onLongPressPart);
                onLongPressPart = null;
            }
            return true;
        }
        return false;
    }

    public class Part {

        private int index;
        private final AnimatedFloat highlightAnimated = new AnimatedFloat(CollageLayoutView2.this, 0, 1200, CubicBezierInterpolator.EASE_OUT);

        public final ImageReceiver imageReceiver = new ImageReceiver(CollageLayoutView2.this);
        public VideoPlayerHolderBase videoPlayer;
        public TextureView textureView;
        public boolean textureViewReady;

        private volatile long pendingSeek = -1;

        public CollageLayout.Part part;

        public boolean hasBounds = false;
        public RectF fromBounds = new RectF();
        public RectF bounds = new RectF();
        public float boundsTransition = 1.0f;

        private boolean current;
        private StoryEntry content;

        public Part() {}

        private ValueAnimator animator;
        public void setPart(CollageLayout.Part part, boolean animated) {
            final CollageLayout.Part oldPart = this.part;
            if (part != null) this.part = part;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (animated) {
                if (!hasBounds) {
                    layoutOut(fromBounds, part);
                } else {
                    AndroidUtilities.lerp(fromBounds, bounds, boundsTransition, fromBounds);
                }
                if (part == null) {
                    layoutOut(bounds, oldPart);
                } else {
                    layout(bounds, part);
                }
                boundsTransition = 0.0f;
                animator = ValueAnimator.ofFloat(0, 1.0f);
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                        boundsTransition = (float) animation.getAnimatedValue();
                        invalidate();
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        boundsTransition = 1.0f;
                        if (removingParts.contains(Part.this)) {
                            imageReceiver.onDetachedFromWindow();
                            destroyContent();
                            removingParts.remove(Part.this);
                        }
                        invalidate();
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animator.setDuration(360);
                animator.start();
            } else {
                layout(bounds, part);
                boundsTransition = 1.0f;
                if (part == null) {
                    imageReceiver.onDetachedFromWindow();
                    destroyContent();
                    removingParts.remove(Part.this);
                }
            }
            invalidate();
            hasBounds = true;
        }

        public void setCurrent(boolean current) {
            this.current = current;
        }

        public boolean isEmpty() {
            return !current && !hasContent();
        }

        public void setContent(StoryEntry entry) {
            destroyContent();

            content = entry;
            final String filter = ((int) Math.ceil(AndroidUtilities.displaySize.x / AndroidUtilities.density)) + "_" + ((int) Math.ceil(AndroidUtilities.displaySize.y / AndroidUtilities.density)) + (entry != null && entry.isVideo ? "_" + ImageLoader.AUTOPLAY_FILTER : "") + "_exif";
            if (content == null) {
                imageReceiver.clearImage();
            } else if (content.isVideo) {
                if (content.thumbBitmap != null) {
                    imageReceiver.setImageBitmap(content.thumbBitmap);
                } else if (content.thumbPath != null) {
                    imageReceiver.setImage(content.thumbPath, filter, null, null, 0);
                } else {
                    imageReceiver.clearImage();
                }
                textureView = new TextureView(getContext());
                addView(textureView);

                videoPlayer = new VideoPlayerHolderBase() {
                    @Override
                    public void onRenderedFirstFrame() {
                        textureViewReady = true;
                        invalidate();
                    }

                    @Override
                    protected void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                        AndroidUtilities.runOnUIThread(() -> {
                            StoryEntry e = content;
                            if (e == null) return;
                            if (e.width != width || e.height != height || e.orientation != unappliedRotationDegrees) {
                                e.width = width;
                                e.height = height;
                                e.orientation = unappliedRotationDegrees;
                                if (textureView != null) {
                                    textureView.requestLayout();
                                }
                            }
                        });
                    }

                    @Override
                    public boolean needRepeat() {
                        return !preview;
                    }
                };
                videoPlayer.allowMultipleInstances(true);
                videoPlayer.with(textureView);
                videoPlayer.preparePlayer(Uri.fromFile(content.file), false, 1.0f);
                videoPlayer.setVolume(isMuted || content.muted || !preview ? 0.0f : content.videoVolume);
                if (!preview || playing) {
                    videoPlayer.play();
                } else {
                    videoPlayer.pause();
                }
            } else {
                imageReceiver.setImage(content.file.getAbsolutePath(), filter, null, null, 0);
            }
            invalidate();
        }

        public boolean hasContent() {
            return content != null;
        }

        public void destroyContent() {
            if (videoPlayer != null) {
                videoPlayer.pause();
                videoPlayer.release(null);
                videoPlayer = null;
            }
            if (textureView != null) {
                AndroidUtilities.removeFromParent(textureView);
                textureView = null;
            }
            textureViewReady = false;
        }

    }

    private boolean attached;
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int i = 0; i < parts.size(); ++i) {
            parts.get(i).imageReceiver.onAttachedToWindow();
        }
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i = 0; i < parts.size(); ++i) {
            parts.get(i).imageReceiver.onDetachedFromWindow();
        }
        attached = false;
        AndroidUtilities.cancelRunOnUIThread(syncRunnable);
    }

    private Runnable cancelGestures;
    public void setCancelGestures(Runnable cancelGestures) {
        this.cancelGestures = cancelGestures;
    }

    private Runnable onResetState;
    public void setResetState(Runnable onResetState) {
        this.onResetState = onResetState;
    }

    private boolean preview;
    private long previewStartTime;
    private boolean fastSeek;
    private boolean playing = true;


    public void setPreview(boolean preview) {
        if (this.preview == preview) return;
        this.preview = preview;
        if (preview) {
            if (blurManager != null) {
                blurManager.invalidate();
            }
            for (int i = 0; i < parts.size(); ++i) {
                parts.get(i).index = i;
            }
        }
        fastSeek = false;
        lastPausedPosition = 0;
        for (Part part : parts) {
            if (part.videoPlayer != null) {
                part.videoPlayer.setAudioEnabled(preview, true);
                if (!preview || playing) {
                    part.videoPlayer.play();
                } else {
                    part.videoPlayer.pause();
                }
            }
        }
        AndroidUtilities.cancelRunOnUIThread(syncRunnable);
        if (preview) {
            this.previewStartTime = System.currentTimeMillis();
            AndroidUtilities.runOnUIThread(syncRunnable, (long) (1000L / AndroidUtilities.screenRefreshRate));
        } else {

        }
    }

    public Part getMainPart() {
        if (!this.preview) return null;
        long maxDuration = 0;
        Part maxPart = null;
        for (Part part : parts) {
            if (part.content == null) continue;
            if (!part.content.isVideo) continue;
            long duration = part.content.duration;
            if (part.videoPlayer != null && part.videoPlayer.getDuration() > 0) {
                duration = part.videoPlayer.getDuration();
            }
            if (duration > maxDuration) {
                maxDuration = duration;
                maxPart = part;
            }
        }
        return maxPart;
    }

    private TimelineView timelineView;
    public void setTimelineView(TimelineView timelineView) {
        this.timelineView = timelineView;
    }

    private PreviewView previewView;
    public void setPreviewView(PreviewView previewView) {
        this.previewView = previewView;
    }

    public long getPosition() {
        if (!preview) return 0;
        if (!playing) return lastPausedPosition;
        final long now = System.currentTimeMillis();
        final long d = now - previewStartTime;
        if (d > getDuration()) {
            previewStartTime = now - (d % getDuration());
        }
        return d;
    }

    public long getPositionWithOffset() {
        if (!preview) return 0;
        long position = getPosition();
        final Part mainPart = getMainPart();
        final long generalOffset = mainPart == null ? 0 : mainPart.content.videoOffset + (long) (mainPart.content.videoLeft * mainPart.content.duration);
        return getPosition() + generalOffset;
    }

    private boolean restorePositionOnPlaying = true;
    public void forceNotRestorePosition() {
//        restorePositionOnPlaying = false;
    }

    private long lastPausedPosition;
    public void setPlaying(boolean playing) {
        final boolean restore = restorePositionOnPlaying;
        restorePositionOnPlaying = true;
        if (this.playing == playing) return;
        this.playing = playing;
        if (!playing) {
            lastPausedPosition = getPosition();
        } else if (restore) {
            seekTo(lastPausedPosition, false);
        } else {
            fastSeek = false;
        }
        AndroidUtilities.cancelRunOnUIThread(syncRunnable);
        syncRunnable.run();
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isMuted;
    public void setMuted(boolean muted) {
        if (isMuted == muted) return;
        isMuted = muted;
    }

    public boolean hasVideo() {
        for (Part part : parts) {
            if (part.content != null && part.content.isVideo)
                return true;
        }
        return false;
    }

    public long getDuration() {
        if (!preview) return 1;
        Part mainPart = getMainPart();
        if (mainPart == null || mainPart.content == null) return 1;
        long duration = (long) (mainPart.content.duration * (mainPart.content.videoRight - mainPart.content.videoLeft));
        duration = Math.min(duration, 59_500);
        duration = Math.max(duration, 1);
        return duration;
    }

    public void seekTo(long progress) {
        seekTo(progress, false);
    }

    public void seekTo(long progress, boolean fast) {
        if (!preview) return;
        progress = Utilities.clamp(progress, getDuration(), 0);
        if (!playing) lastPausedPosition = progress;
        final long now = System.currentTimeMillis();
        previewStartTime = now - progress;
        fastSeek = fast;
        AndroidUtilities.cancelRunOnUIThread(syncRunnable);
        syncRunnable.run();
    }

    private final Runnable syncRunnable = () -> {
        final long position = getPosition();
        final Part mainPart = getMainPart();
        final long generalOffset = mainPart == null ? 0 : mainPart.content.videoOffset + (long) (mainPart.content.videoLeft * mainPart.content.duration);
        for (int i = 0; i < parts.size(); ++i) {
            final Part part = parts.get(i);
            if (part.content != null && part.videoPlayer != null) {
                final long duration = part.videoPlayer.getDuration();
                long frame = Utilities.clamp(position + generalOffset - part.content.videoOffset, duration, 0);
                final boolean shouldPlay = (!preview || playing) && frame > part.content.videoLeft * duration && frame < part.content.videoRight * duration;
                frame = Utilities.clamp(frame, (long) (part.content.videoRight * duration), (long) (part.content.videoLeft * duration));
                if (part.videoPlayer.isPlaying() != shouldPlay) {
                    if (shouldPlay) {
                        part.videoPlayer.play();
                    } else {
                        part.videoPlayer.pause();
                    }
                }
                part.videoPlayer.setVolume(isMuted || part.content.muted || !preview ? 0.0f : part.content.videoVolume);
                final long currentPosition = part.pendingSeek >= 0 ? part.pendingSeek : part.videoPlayer.getCurrentPosition();
                if (Math.abs(currentPosition - frame) > 450 && part.pendingSeek < 0) {
                    part.videoPlayer.seekTo(part.pendingSeek = frame, fastSeek, () -> {
                        part.pendingSeek = -1;
                    });
                }
            }
        }
        if (timelineView != null) {
            timelineView.setProgress(position);
        }
        if (previewView != null) {
            previewView.updateAudioPlayer(true);
            previewView.updateRoundPlayer(true);
        }
        if (preview && playing) {
            AndroidUtilities.runOnUIThread(this.syncRunnable, (long) (1000L / AndroidUtilities.screenRefreshRate));
        }
    };

}
