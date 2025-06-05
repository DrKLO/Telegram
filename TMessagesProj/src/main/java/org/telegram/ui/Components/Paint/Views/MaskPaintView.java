package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Brush;
import org.telegram.ui.Components.Paint.Painting;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.Paint.RenderView;
import org.telegram.ui.Components.Paint.UndoStore;
import org.telegram.ui.Components.Size;

import java.util.ArrayList;

public class MaskPaintView extends FrameLayout {

    private int currentAccount;

    private UndoStore undoStore;
    private DispatchQueue queue;

    private MediaController.CropState currentCropState;
    private final RenderView renderView;
    private Bitmap bitmapToEdit;
    private int orientation;

    private boolean inBubbleMode;

    public final FrameLayout buttonsLayout;
    public final TextView cancelButton;
    public final TextView doneButton;

    public PaintWeightChooserView weightChooserView;
    private PaintWeightChooserView.ValueOverride weightDefaultValueOverride = new PaintWeightChooserView.ValueOverride() {
        @Override
        public float get() {
            Brush brush = renderView.getCurrentBrush();
            if (brush == null) {
                return PersistColorPalette.getInstance(currentAccount).getCurrentWeight();
            }
            return PersistColorPalette.getInstance(currentAccount).getWeight("-1", brush.getDefaultWeight());
        }

        @Override
        public void set(float val) {
            PersistColorPalette.getInstance(currentAccount).setWeight("-1", val);
            renderView.setBrushSize(val);
        }
    };

    public MaskPaintView(Context context, int currentAccount, Bitmap bitmapToEdit, Bitmap bitmap, int orientation, MediaController.CropState cropState) {
        super(context);

        this.currentAccount = currentAccount;

        inBubbleMode = context instanceof BubbleActivity;

        undoStore = new UndoStore();
        undoStore.setDelegate(new UndoStore.UndoStoreDelegate() {
            @Override
            public void historyChanged() {

            }
        });
        queue = new DispatchQueue("MaskPaint");

        this.currentCropState = cropState;
        this.bitmapToEdit = bitmapToEdit;
        this.orientation = orientation;
        renderView = new RenderView(context, new Painting(getPaintingSize(), bitmap, orientation, null).asMask(), bitmapToEdit, null, null);
        renderView.setAlpha(0f);
        renderView.setDelegate(new RenderView.RenderViewDelegate() {
            @Override
            public void onBeganDrawing() {
                weightChooserView.setViewHidden(true);
            }

            @Override
            public void onFinishedDrawing(boolean moved) {
                undoStore.getDelegate().historyChanged();
                weightChooserView.setViewHidden(false);
                onDrawn();
            }

            @Override
            public void onFirstDraw() {
                renderView.animate().alpha(1f).setDuration(320).setUpdateListener(MaskPaintView.this::onRenderViewAlphaUpdate).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            }

            @Override
            public boolean shouldDraw() {
                return true;
            }

            @Override
            public void resetBrush() {

            }
        });
        renderView.setUndoStore(undoStore);
        renderView.setQueue(queue);
        renderView.setVisibility(View.INVISIBLE);
        renderView.setBrush(new Brush.Radial());
        renderView.setBrushSize(weightDefaultValueOverride.get());
        renderView.setColor(0xFFFF0000);
        addView(renderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        weightChooserView = new PaintWeightChooserView(context);
        weightChooserView.setMinMax(0.05f, 1f);
        weightChooserView.setBrushWeight(weightDefaultValueOverride.get());
        weightChooserView.setRenderView(renderView);
        weightChooserView.setValueOverride(weightDefaultValueOverride);
        weightChooserView.setTranslationX(-dp(18));
        weightChooserView.setAlpha(0f);
        addView(weightChooserView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buttonsLayout = new FrameLayout(context);
        buttonsLayout.setAlpha(0f);
        buttonsLayout.setVisibility(View.GONE);
        addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM));

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        cancelButton.setTypeface(AndroidUtilities.bold());
        cancelButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Color.WHITE, .15f), Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE));
        cancelButton.setPadding(dp(28), 0, dp(28), 0);
        cancelButton.setText(LocaleController.getString(R.string.Cancel).toUpperCase());
        cancelButton.setTextColor(Color.WHITE);
        cancelButton.setGravity(Gravity.CENTER);
        buttonsLayout.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.LEFT, -8, 0, 0, 0));

        doneButton = new TextView(context);
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        doneButton.setTypeface(AndroidUtilities.bold());
        doneButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_chat_editMediaButton), .15f), Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE));
        doneButton.setPadding(dp(28), 0, dp(28), 0);
        doneButton.setText(LocaleController.getString(R.string.Save).toUpperCase());
        doneButton.setTextColor(Theme.getColor(Theme.key_chat_editMediaButton));
        doneButton.setGravity(Gravity.CENTER);
        buttonsLayout.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.RIGHT, 0, 0, -8, 0));
    }

    protected void onDrawn() {

    }

    protected void onRenderViewAlphaUpdate(ValueAnimator animation) {

    }

    public boolean canUndo() {
        return undoStore.canUndo();
    }
    public boolean undo() {
        if (!undoStore.canUndo()) return false;
        undoStore.undo();
        return true;
    }

    private Size paintingSize;
    private Size getPaintingSize() {
        if (paintingSize != null) {
            return paintingSize;
        }
        float width = bitmapToEdit.getWidth();
        float height = bitmapToEdit.getHeight();

        int maxSide = 1280;
        Size size = new Size(width, height);
        size.width = maxSide;
        size.height = (float) Math.floor(size.width * height / width);
        if (size.height > maxSide) {
            size.height = maxSide;
            size.width = (float) Math.floor(size.height * width / height);
        }
        paintingSize = size;
        return size;
    }

    private boolean eraser;
    public void setEraser(boolean eraser) {
        if (this.eraser == eraser) return;
        this.eraser = eraser;
        renderView.setBrush(eraser ? new Brush.Eraser() : new Brush.Radial());
    }

    private float panTranslationY, scale, inputTransformX, inputTransformY, transformX, transformY, imageWidth, imageHeight;

    public void setTransform(float scale, float trX, float trY, float rotate, float imageWidth, float imageHeight) {
        this.scale = scale;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        inputTransformX = trX;
        inputTransformY = trY;
        transformX = trX;
        trY += panTranslationY;
        transformY = trY;
        for (int a = 0; a < 1; a++) {
            View view;
            float additionlScale = 1.0f;
            if (a == 0) {
                view = renderView;
            } else continue;
            float tx;
            float ty;
            float rotation = rotate;
            if (currentCropState != null) {
                additionlScale *= currentCropState.cropScale;

                int w = view.getMeasuredWidth();
                int h = view.getMeasuredHeight();
                if (w == 0 || h == 0) {
                    return;
                }
                int tr = currentCropState.transformRotation;
                int fw = w, rotatedW = w;
                int fh = h, rotatedH = h;
                if (tr == 90 || tr == 270) {
                    int temp = fw;
                    fw = rotatedW = fh;
                    fh = rotatedH = temp;
                }
                fw *= currentCropState.cropPw;
                fh *= currentCropState.cropPh;

                float sc = Math.max(imageWidth / fw, imageHeight / fh);
                additionlScale *= sc;

                tx = trX + currentCropState.cropPx * rotatedW * scale * sc * currentCropState.cropScale;
                ty = trY + currentCropState.cropPy * rotatedH * scale * sc * currentCropState.cropScale;
                rotation += currentCropState.cropRotate + tr;
            } else {
                if (a == 0) {
                    additionlScale *= baseScale;
                }
                tx = trX;
                ty = trY;
            }
            float finalScale = scale * additionlScale;
            if (Float.isNaN(finalScale)) {
                finalScale = 1f;
            }
            view.setScaleX(finalScale);
            view.setScaleY(finalScale);
            view.setTranslationX(tx);
            view.setTranslationY(ty);
            view.setRotation(rotation);
            view.invalidate();
        }
        invalidate();
    }

    public void init() {
        renderView.setVisibility(View.VISIBLE);
        buttonsLayout.setVisibility(View.VISIBLE);
        buttonsLayout.setTranslationY(dp(18));
        buttonsLayout.animate().alpha(1f).translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
        weightChooserView.animate().alpha(1f).translationX(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
    }

    private boolean ignoreLayout;
    private float baseScale;

    private ArrayList<Rect> exclusionRects = new ArrayList<>();
    private Rect exclusionRect = new Rect();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayout = true;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);

        float bitmapW;
        float bitmapH;
        int fullHeight = AndroidUtilities.displaySize.y;
        int maxHeight = fullHeight;
        if (bitmapToEdit != null) {
            bitmapW = bitmapToEdit.getWidth();
            bitmapH = bitmapToEdit.getHeight();
        } else {
            bitmapW = width;
            bitmapH = height;
        }

        float renderWidth = width;
        float renderHeight = (float) Math.floor(renderWidth * bitmapH / bitmapW);
        if (renderHeight > maxHeight) {
            renderHeight = maxHeight;
            renderWidth = (float) Math.floor(renderHeight * bitmapW / bitmapH);
        }

        renderView.measure(MeasureSpec.makeMeasureSpec((int) renderWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) renderHeight, MeasureSpec.EXACTLY));

        baseScale = 1f;//renderWidth / paintingSize.width;
        measureChild(weightChooserView, widthMeasureSpec, heightMeasureSpec);
        measureChild(buttonsLayout, widthMeasureSpec, heightMeasureSpec);

        ignoreLayout = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exclusionRects.clear();
            exclusionRects.add(exclusionRect);
            int h = (int) (getMeasuredHeight() * .3f);
            exclusionRect.set(0, (getMeasuredHeight() - h) / 2, dp(20), (getMeasuredHeight() + h) / 2);
            setSystemGestureExclusionRects(exclusionRects);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int width = right - left;
        int height = bottom - top;

        int status = (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
        int actionBarHeight = 0;
        int actionBarHeight2 = status;

        int x = (int) Math.ceil((width - renderView.getMeasuredWidth()) / 2f);
        int y = (height - renderView.getMeasuredHeight()) / 2;

        renderView.layout(x, y, x + renderView.getMeasuredWidth(), y + renderView.getMeasuredHeight());
        int b = bottom - top;
        buttonsLayout.layout(0, b - buttonsLayout.getMeasuredHeight(), buttonsLayout.getMeasuredWidth(), b);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean restore = false;
        if ((child == renderView) && currentCropState != null) {
            canvas.save();

            int status = (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            int actionBarHeight = ActionBar.getCurrentActionBarHeight();
            int actionBarHeight2 = actionBarHeight + status;

            int vw = child.getMeasuredWidth();
            int vh = child.getMeasuredHeight();
            int tr = currentCropState.transformRotation;
            if (tr == 90 || tr == 270) {
                int temp = vw;
                vw = vh;
                vh = temp;
            }

            int w = (int) (vw * currentCropState.cropPw * child.getScaleX() / currentCropState.cropScale);
            int h = (int) (vh * currentCropState.cropPh * child.getScaleY() / currentCropState.cropScale);
            float x = (float) Math.ceil((getMeasuredWidth() - w) / 2f) + transformX;
            float y = (getMeasuredHeight() - actionBarHeight2 - dp(48) + getAdditionalBottom() - h) / 2f + dp(8) + status + transformY;

            canvas.clipRect(Math.max(0, x), Math.max(0, y), Math.min(x + w, getMeasuredWidth()), Math.min(getMeasuredHeight(), y + h));
            restore = true;
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (restore) {
            canvas.restore();
        }
        return result;
    }

    public boolean onTouch(MotionEvent ev) {
        float x2 = (ev.getX() - renderView.getTranslationX() - getMeasuredWidth() / 2f) / renderView.getScaleX();
        float y2 = (ev.getY() - renderView.getTranslationY() - getMeasuredHeight() / 2f) / renderView.getScaleY();
        float rotation = (float) Math.toRadians(-renderView.getRotation());
        float x = (float) (x2 * Math.cos(rotation) - y2 * Math.sin(rotation)) + renderView.getMeasuredWidth() / 2f;
        float y = (float) (x2 * Math.sin(rotation) + y2 * Math.cos(rotation)) + renderView.getMeasuredHeight() / 2f;

        MotionEvent event = MotionEvent.obtain(ev);
        event.setLocation(x, y);
        renderView.onTouch(event);
        event.recycle();
        return true;
    }

    public Bitmap getBitmap() {
        Bitmap resultBitmap = renderView.getResultBitmap(false, false);
        if (orientation != 0) {
            int w = resultBitmap.getWidth(), h = resultBitmap.getHeight();
            if (orientation / 90 % 2 != 0) {
                w = resultBitmap.getHeight();
                h = resultBitmap.getWidth();
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            canvas.translate(w / 2f, h / 2f);
            canvas.rotate(-orientation);
            AndroidUtilities.rectTmp.set(
                    -resultBitmap.getWidth() / 2f,
                    -resultBitmap.getHeight() / 2f,
                    resultBitmap.getWidth() / 2f,
                    resultBitmap.getHeight() / 2f
            );
            canvas.drawBitmap(resultBitmap, null, AndroidUtilities.rectTmp, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            resultBitmap.recycle();
            resultBitmap = b;
        }
        return resultBitmap;
    }

    public RenderView getRenderView() {
        return renderView;
    }

    public int getAdditionalTop() {
        return 0; // AndroidUtilities.dp(48);
    }

    public int getAdditionalBottom() {
        return 0; // AndroidUtilities.dp(24);
    }

    public void shutdown() {
        renderView.shutdown();
        queue.postRunnable(() -> {
            Looper looper = Looper.myLooper();
            if (looper != null) {
                looper.quit();
            }
        });
    }

}
