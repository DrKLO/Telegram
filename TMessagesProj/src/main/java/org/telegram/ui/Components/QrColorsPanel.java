package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Glass panel with profile colors for the QR screen.
 * It morphs out of a source rect (the bottom buttons group) via {@link #setMorph}.
 */
public class QrColorsPanel extends FrameLayout {

    public interface Delegate {
        void onColorSelected(MessagesController.PeerColor color);
    }

    private static final int COLUMNS = 6;
    private static final int RADIUS_DP = 32;
    private static final int HEADER_HEIGHT_DP = 44;
    private static final int ROW_HEIGHT_DP = 62;
    private static final int SIDE_PADDING_DP = 10;

    private final boolean isDark;
    private final Delegate delegate;
    private final TextView headerView;
    private final RecyclerListView listView;
    private final Adapter adapter = new Adapter();
    private final ArrayList<MessagesController.PeerColor> colors = new ArrayList<>();
    private int selectedId = -1;

    private @Nullable BlurredBackgroundDrawable glass;
    private final RectF fromRect = new RectF();
    private float fromRadius;
    private float progress;
    private final RectF currentRect = new RectF();
    private final Path clipPath = new Path();

    public QrColorsPanel(Context context, boolean isDark, Delegate delegate) {
        super(context);
        this.isDark = isDark;
        this.delegate = delegate;
        setWillNotDraw(false);
        setClipChildren(false);

        headerView = new TextView(context);
        headerView.setText(getString(R.string.QrProfileColors).toUpperCase());
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        headerView.setTypeface(AndroidUtilities.bold());
        headerView.setTextColor(isDark ? 0x99FFFFFF : 0x8C000000);
        headerView.setGravity(Gravity.CENTER);
        addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEADER_HEIGHT_DP, Gravity.TOP));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, COLUMNS));
        listView.setAdapter(adapter);
        listView.setItemAnimator(null);
        listView.setPadding(dp(SIDE_PADDING_DP), 0, dp(SIDE_PADDING_DP), 0);
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= colors.size()) {
                return;
            }
            final MessagesController.PeerColor color = colors.get(position);
            setSelected(color.id, true);
            delegate.onColorSelected(color);
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, HEADER_HEIGHT_DP, 0, 0));
    }

    /* Public API */

    /** The glass has to be created for this view, so that it tracks the view position. */
    public void setGlass(BlurredBackgroundDrawable glass) {
        this.glass = glass;
        invalidate();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setColors(@Nullable MessagesController.PeerColors peerColors, int selectedId) {
        colors.clear();
        if (peerColors != null) {
            for (MessagesController.PeerColor color : peerColors.colors) {
                if (!color.hidden) {
                    colors.add(color);
                }
            }
        }
        this.selectedId = selectedId;
        adapter.notifyDataSetChanged();
    }

    public void setSelected(int id, boolean animated) {
        selectedId = id;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            if (child instanceof ColorCell) {
                final ColorCell cell = (ColorCell) child;
                cell.setSelected(cell.color != null && cell.color.id == id, animated);
            }
        }
    }

    /** Height the panel needs to show all rows, limited by {@code maxHeight}. */
    public int getPreferredHeight(int bottomInset, int maxHeight) {
        final int rows = (colors.size() + COLUMNS - 1) / COLUMNS;
        final int height = dp(HEADER_HEIGHT_DP + 12) + rows * dp(ROW_HEIGHT_DP) + bottomInset;
        return Math.min(height, maxHeight);
    }

    public void setBottomInset(int bottomInset) {
        listView.setPadding(dp(SIDE_PADDING_DP), 0, dp(SIDE_PADDING_DP), bottomInset + dp(12));
    }

    /**
     * @param from rect in this view coordinates the panel grows from
     * @param progress 0 = collapsed into {@code from}, 1 = fully opened
     */
    public void setMorph(RectF from, float fromRadius, float progress) {
        this.fromRect.set(from);
        this.fromRadius = fromRadius;
        this.progress = progress;
        final float contentAlpha = Utilities.clamp((progress - .35f) / .65f, 1f, 0f);
        headerView.setAlpha(contentAlpha);
        listView.setAlpha(contentAlpha);
        setVisibility(progress > 0 ? VISIBLE : GONE);
        invalidate();
    }

    /* Drawing */

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        currentRect.set(
            lerp(fromRect.left, 0, progress),
            lerp(fromRect.top, 0, progress),
            lerp(fromRect.right, getWidth(), progress),
            lerp(fromRect.bottom, getHeight(), progress)
        );
        final float radius = lerp(fromRadius, dp(RADIUS_DP), progress);
        if (glass != null) {
            glass.setBounds((int) currentRect.left, (int) currentRect.top, (int) currentRect.right, (int) currentRect.bottom);
            glass.setRadius(radius);
            glass.draw(canvas);
        }
        clipPath.rewind();
        clipPath.addRoundRect(currentRect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (progress < 1f) {
            return false;
        }
        super.dispatchTouchEvent(ev);
        // touches inside the panel never fall through to the dismiss area
        return true;
    }

    /* List */

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final ColorCell cell = new ColorCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final MessagesController.PeerColor color = colors.get(position);
            final ColorCell cell = (ColorCell) holder.itemView;
            cell.setColor(color);
            cell.setSelected(color.id == selectedId, false);
        }

        @Override
        public int getItemCount() {
            return colors.size();
        }
    }

    private class ColorCell extends View {

        private static final int CIRCLE_SIZE_DP = 48;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat selectedProgress = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        private @Nullable MessagesController.PeerColor color;
        private boolean selected;
        private float shaderSize;

        ColorCell(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dpf2(2.33f));
        }

        void setColor(MessagesController.PeerColor color) {
            this.color = color;
            shaderSize = 0;
            setContentDescription(null);
            invalidate();
        }

        void setSelected(boolean selected, boolean animated) {
            this.selected = selected;
            if (!animated) {
                selectedProgress.set(selected, true);
            }
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (color == null) {
                return;
            }
            final float size = dp(CIRCLE_SIZE_DP);
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (shaderSize != size) {
                shaderSize = size;
                fillPaint.setShader(new LinearGradient(
                    cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f,
                    new int[] { color.getStoryColor1(isDark), color.getBgColor1(isDark), color.getBgColor2(isDark) },
                    new float[] { 0f, .55f, 1f },
                    Shader.TileMode.CLAMP
                ));
            }
            final float selected = selectedProgress.set(this.selected);
            canvas.drawCircle(cx, cy, size / 2f - dp(5) * selected, fillPaint);
            if (selected > 0) {
                ringPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                ringPaint.setAlpha((int) (255 * selected));
                canvas.drawCircle(cx, cy, size / 2f - ringPaint.getStrokeWidth() / 2f, ringPaint);
            }
        }
    }
}
