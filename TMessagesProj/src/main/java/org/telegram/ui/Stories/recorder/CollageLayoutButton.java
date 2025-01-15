package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.GradientClip;

public class CollageLayoutButton extends ToggleButton2 {

    public CollageLayoutButton(Context context) {
        super(context);
    }

    public static class CollageLayoutListView extends FrameLayout {

        public final RecyclerListView listView;

        private CollageLayout selectedLayout;

        public void setSelected(CollageLayout layout) {
            selectedLayout = layout;
            AndroidUtilities.updateVisibleRows(listView);
        }

        public CollageLayoutListView(Context context, final FlashViews flashViews) {
            super(context);

            listView = new RecyclerListView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent e) {
                    if (e.getX() <= getPaddingLeft() || e.getX() >= getWidth() - getPaddingRight()) {
                        return false;
                    }
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return super.onInterceptTouchEvent(e);
                }
                @Override
                public boolean dispatchTouchEvent(MotionEvent e) {
                    if (e.getX() <= getPaddingLeft() || e.getX() >= getWidth() - getPaddingRight()) {
                        return false;
                    }
                    return super.dispatchTouchEvent(e);
                }

                private final GradientClip clip = new GradientClip();

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * visibleProgress), Canvas.ALL_SAVE_FLAG);
                    canvas.save();
                    final float l = getPaddingLeft(), r = getWidth() - getPaddingRight();
                    canvas.clipRect(l, 0, r, getHeight());
                    canvas.translate(r * (1.0f - visibleProgress), 0);
                    super.dispatchDraw(canvas);
                    canvas.restore();
                    canvas.save();
                    AndroidUtilities.rectTmp.set(l, 0, l + dp(12), getHeight());
                    clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, visibleProgress);
                    AndroidUtilities.rectTmp.set(r - dp(12), 0, r, getHeight());
                    clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, visibleProgress);
                    canvas.restore();
                    canvas.restore();
                }
            };
            listView.setAdapter(new RecyclerView.Adapter() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    final Button imageView = new Button(context);
                    imageView.setLayoutParams(new RecyclerView.LayoutParams(dp(46), dp(56)));
                    imageView.setBackground(Theme.createSelectorDrawable(0x20ffffff));
                    return new RecyclerListView.Holder(imageView);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    final Button imageView = (Button) holder.itemView;
                    final CollageLayout layout = CollageLayout.getLayouts().get(position);
                    final boolean animated = position == imageView.position;
                    imageView.setDrawable(new CollageLayoutDrawable(layout));
                    imageView.setSelected(layout.equals(selectedLayout), animated);
                    imageView.position = position;
                }

                @Override
                public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
                    Button button = (Button) holder.itemView;
                    flashViews.add(button);
                    if (button.position >= 0 && button.position < CollageLayout.getLayouts().size()) {
                        final CollageLayout layout = CollageLayout.getLayouts().get(button.position);
                        button.setDrawable(new CollageLayoutDrawable(layout));
                        button.setSelected(layout.equals(selectedLayout), false);
                    }
                    super.onViewAttachedToWindow(holder);
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
                    flashViews.remove((Button) holder.itemView);
                    super.onViewDetachedFromWindow(holder);
                }

                @Override
                public int getItemCount() {
                    return CollageLayout.getLayouts().size();
                }
            });
            listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            listView.setClipToPadding(false);
            listView.setVisibility(View.GONE);
            listView.setWillNotDraw(false);
            listView.setOnItemClickListener((view, position) -> {
                if (onLayoutClick != null) {
                    onLayoutClick.run(CollageLayout.getLayouts().get(position));
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56));
        }

        private static class Button extends ToggleButton2 {
            public int position;
            public Button(Context context) {
                super(context);
            }
        }

        private Utilities.Callback<CollageLayout> onLayoutClick;
        public void setOnLayoutClick(Utilities.Callback<CollageLayout> onLayoutClick) {
            this.onLayoutClick = onLayoutClick;
        }

        public void setBounds(float left, float right) {
            listView.setPadding((int) left, 0, (int) right, 0);
            listView.invalidate();
        }

        public boolean isVisible() {
            return visible;
        }

        private float visibleProgress;
        private boolean visible;
        private ValueAnimator visibleAnimator;
        public void setVisible(final boolean visible, boolean animated) {
            if (visibleAnimator != null) {
                visibleAnimator.cancel();
            }
            if (this.visible == visible) return;
            this.visible = visible;
            if (animated) {
                listView.setVisibility(View.VISIBLE);
                visibleAnimator = ValueAnimator.ofFloat(this.visibleProgress, visible ? 1.0f : 0.0f);
                visibleAnimator.addUpdateListener(anm -> {
                    visibleProgress = (float) anm.getAnimatedValue();
                    listView.invalidate();
                });
                visibleAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        visibleProgress = visible ? 1.0f : 0.0f;
                        listView.invalidate();
                        listView.setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                });
                visibleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                visibleAnimator.setDuration(340);
                visibleAnimator.start();
            } else {
                this.visibleProgress = visible ? 1.0f : 0.0f;
                listView.invalidate();
                listView.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }

    }

    public static class CollageLayoutDrawable extends Drawable {

        public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Paint crossXferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Path path = new Path();
        private final float[] radii = new float[8];
        private boolean cross;

        public CollageLayoutDrawable(final CollageLayout layout) {
            this(layout, false);
        }

        public CollageLayoutDrawable(final CollageLayout layout, boolean cross) {
            this.cross = cross;

            paint.setColor(0xFFFFFFFF);
            final float ow = dpf2(40 / 3f), oh = dpf2(56 / 3f), or = dpf2(3);
            final float iw = dpf2(30 / 3f), ih = dpf2(46 / 3f), ir = dpf2(1);
            final float p = dpf2(1.33f);
            path.setFillType(Path.FillType.EVEN_ODD);
            AndroidUtilities.rectTmp.set(-ow / 2f, -oh / 2f, ow / 2f, oh / 2f);
            path.addRoundRect(AndroidUtilities.rectTmp, or, or, Path.Direction.CW);

            for (CollageLayout.Part part : layout.parts) {
                final int cols = layout.columns[part.y];
                final float pw = (iw - Math.max(0, cols - 1) * p) / cols;
                final float ph = (ih - Math.max(0, layout.h - 1) * p) / layout.h;
                AndroidUtilities.rectTmp.set(
                    -iw / 2f + pw * part.x + p * part.x,
                    -ih / 2f + ph * part.y + p * part.y,
                    -iw / 2f + pw * (part.x + 1) + p * part.x,
                    -ih / 2f + ph * (part.y + 1) + p * part.y
                );
                radii[0] = radii[1] = part.x == 0        && part.y == 0            ? ir : 0; // top left
                radii[2] = radii[3] = part.x == cols - 1 && part.y == 0            ? ir : 0; // top right
                radii[4] = radii[5] = part.x == cols - 1 && part.y == layout.h - 1 ? ir : 0; // bottom right
                radii[6] = radii[7] = part.x == 0        && part.y == layout.h - 1 ? ir : 0; // bottom left
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            }

            crossXferPaint.setStyle(Paint.Style.STROKE);
            crossXferPaint.setStrokeWidth(dp(3.33f));
            crossXferPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            crossPaint.setStyle(Paint.Style.STROKE);
            crossPaint.setStrokeWidth(dp(1.33f));
            crossPaint.setColor(0xFFFFFFFF);
            crossPaint.setStrokeCap(Paint.Cap.ROUND);
            crossPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (cross) {
                canvas.saveLayerAlpha(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom, 0xFF, Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(getBounds().centerX(), getBounds().centerY());
            canvas.drawPath(path, paint);
            if (cross) {
                canvas.drawLine(-dp(8.66f), -dp(8.66f), dp(8.66f), dp(8.66f), crossXferPaint);
                canvas.drawLine(-dp(8.66f), -dp(8.66f), dp(8.66f), dp(8.66f), crossPaint);
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(32);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(32);
        }
    }

}
