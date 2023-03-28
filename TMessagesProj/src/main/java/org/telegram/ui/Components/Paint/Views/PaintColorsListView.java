package org.telegram.ui.Components.Paint.Views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.RecyclerListView;

public class PaintColorsListView extends RecyclerListView {
    private static Paint checkerboardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static Paint checkerboardPaintWhite = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selectedColorIndex = 0;

    private PersistColorPalette colorPalette;
    private Consumer<Integer> colorListener;

    static {
        checkerboardPaint.setColor(0x88000000);
        checkerboardPaintWhite.setColor(0x88ffffff);
    }

    public PaintColorsListView(Context context) {
        super(context);

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(2));

        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        setLayoutManager(new GridLayoutManager(context, 7));
        setAdapter(new Adapter() {
            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new ColorView(context));
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                ColorView colorView = (ColorView) holder.itemView;
                colorView.getLayoutParams().height = (getHeight() - getPaddingTop() - getPaddingBottom()) / 2;

                if (colorPalette != null) {
                    colorView.setColor(colorPalette.getColor(position));
                    colorView.setSelected(selectedColorIndex == position, false);
                }
            }

            @Override
            public int getItemCount() {
                return 14;
            }
        });
        setOverScrollMode(OVER_SCROLL_NEVER);
        setOnItemClickListener((view, position) -> {
            colorListener.accept(colorPalette.getColor(position));
            colorPalette.selectColorIndex(position);
        });
    }

    public static void drawCheckerboard(Canvas canvas, RectF bounds, int checkerboardSize) {
        for (float x = bounds.left; x <= bounds.right; x += checkerboardSize * 2) {
            for (float y = bounds.top; y <= bounds.bottom; y += checkerboardSize * 2) {
                canvas.drawRect(x, y, x + checkerboardSize, y + checkerboardSize, checkerboardPaint);
                canvas.drawRect(x + checkerboardSize, y, x + checkerboardSize * 2, y + checkerboardSize, checkerboardPaintWhite);
                canvas.drawRect(x + checkerboardSize, y + checkerboardSize, x + checkerboardSize * 2, y + checkerboardSize * 2, checkerboardPaint);
                canvas.drawRect(x, y + checkerboardSize, x + checkerboardSize, y + checkerboardSize * 2, checkerboardPaintWhite);
            }
        }
    }

    private static Path colorCirclePath = new Path();
    private static Paint colorCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static void drawColorCircle(Canvas canvas, float cx, float cy, float rad, int color) {
        colorCirclePaint.setColor(color);
        if (colorCirclePaint.getAlpha() != 0xFF) {
            AndroidUtilities.rectTmp.set(cx - rad, cy - rad, cx + rad, cy + rad);
            colorCirclePaint.setAlpha(0xFF);
            canvas.drawArc(AndroidUtilities.rectTmp, -45, -180, true, colorCirclePaint);

            colorCirclePath.rewind();
            colorCirclePath.moveTo(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
            colorCirclePath.lineTo((float) (AndroidUtilities.rectTmp.centerX() + (AndroidUtilities.rectTmp.width() / 2f) * Math.cos(-Math.PI / 2)),
                    (float) (AndroidUtilities.rectTmp.centerY() + (AndroidUtilities.rectTmp.height() / 2f) * Math.sin(-Math.PI / 2)));
            colorCirclePath.moveTo(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
            colorCirclePath.lineTo((float) (AndroidUtilities.rectTmp.centerX() + (AndroidUtilities.rectTmp.width() / 2f) * Math.cos(-Math.PI / 2 + Math.PI * 2)),
                    (float) (AndroidUtilities.rectTmp.centerY() + (AndroidUtilities.rectTmp.height() / 2f) * Math.sin(-Math.PI / 2 + Math.PI * 2)));
            colorCirclePath.addArc(AndroidUtilities.rectTmp, -45, 180);
            canvas.save();
            canvas.clipPath(colorCirclePath);
            drawCheckerboard(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4));
            canvas.restore();

            colorCirclePaint.setColor(color);
            canvas.drawArc(AndroidUtilities.rectTmp, -45, 180, true, colorCirclePaint);
        } else {
            canvas.drawCircle(cx, cy, rad, colorCirclePaint);
        }
    }

    public void setColorListener(Consumer<Integer> colorListener) {
        this.colorListener = colorListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setColorPalette(PersistColorPalette colorPalette) {
        this.colorPalette = colorPalette;
        getAdapter().notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setSelectedColorIndex(int selectedColorIndex) {
        this.selectedColorIndex = selectedColorIndex;
        getAdapter().notifyDataSetChanged();
    }

    public void setProgress(float progress, boolean toShow) {
        if (toShow) {
            progress = CubicBezierInterpolator.EASE_OUT.getInterpolation(progress);
        } else {
            progress = CubicBezierInterpolator.EASE_IN.getInterpolation(progress);
        }

        float step = 1f / (getChildCount() - 1);
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (i == 0) {
                ch.setAlpha(progress == 1f ? 1f : 0f);
            } else {
                float max = step * i;
                float childScale = Math.min(progress, max) / max;
                ch.setScaleX(childScale);
                ch.setScaleY(childScale);
            }
        }

        invalidate();
    }

    private final class ColorView extends View {
        private int mColor;
        private float selectProgress;

        public ColorView(Context context) {
            super(context);

            setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            paint.setColor(mColor);
            float rad = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
            if (selectProgress != 0f) {
                rad -= (AndroidUtilities.dp(3f) + outlinePaint.getStrokeWidth()) * selectProgress;
            }

            float cx = getWidth() / 2f + getPaddingLeft() - getPaddingRight(), cy = getHeight() / 2f + getPaddingTop() - getPaddingBottom();
            drawColorCircle(canvas, cx, cy, rad, mColor);

            if (selectProgress != 0f) {
                rad = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom()) / 2f - AndroidUtilities.dp(2f);
                outlinePaint.setColor(mColor);
                outlinePaint.setAlpha(0xFF);
                canvas.drawCircle(cx, cy, rad, outlinePaint);
            }
        }

        public void setSelected(boolean selected, boolean animate) {
            if (animate) {
                // TODO: Animate
            } else {
                selectProgress = selected ? 1f : 0f;
                invalidate();
            }
        }

        public void setColor(int color) {
            this.mColor = color;
            invalidate();
        }
    }
}
