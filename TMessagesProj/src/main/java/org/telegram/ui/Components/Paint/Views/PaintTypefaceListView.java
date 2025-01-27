package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Components.RecyclerListView;

public class PaintTypefaceListView extends RecyclerListView implements NotificationCenter.NotificationCenterDelegate {
    private Path mask = new Path();
    private Consumer<Path> maskProvider;

    public PaintTypefaceListView(Context context) {
        super(context);

        setWillNotDraw(false);
        setLayoutManager(new LinearLayoutManager(context));
        setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = new PaintTextOptionsView.TypefaceCell(parent.getContext());
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new Holder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                PaintTextOptionsView.TypefaceCell cell = (PaintTextOptionsView.TypefaceCell) holder.itemView;
                cell.bind(PaintTypeface.get().get(position));
            }

            @Override
            public int getItemCount() {
                return PaintTypeface.get().size();
            }
        });

        setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        setClipToPadding(false);
    }

    @Override
    public Integer getSelectorColor(int position) {
        return 0x10ffffff;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.customTypefacesLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.customTypefacesLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.customTypefacesLoaded) {
            getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.min(PaintTypeface.get().size(), 6) * AndroidUtilities.dp(48) + AndroidUtilities.dp(16), MeasureSpec.EXACTLY));
    }

    @Override
    public void draw(Canvas c) {
        if (maskProvider != null) {
            maskProvider.accept(mask);

            c.save();
            c.clipPath(mask);
        }
        super.draw(c);
        if (maskProvider != null) {
            c.restore();
        }
    }

    public void setMaskProvider(Consumer<Path> maskProvider) {
        this.maskProvider = maskProvider;
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return super.onTouchEvent(e);
    }
}
