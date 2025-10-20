package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class BottomSheetLayouted extends BottomSheetWithRecyclerListView {

    public final LinearLayout layout;

    public FrameLayout buttonContainer;
    public ButtonWithCounterView button;

    public BottomSheetLayouted(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, false, resourcesProvider);
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
    }

    public void createButton() {
        final float pad = (float) backgroundPaddingLeft / AndroidUtilities.density;

        buttonContainer = new FrameLayout(getContext());
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        View buttonShadow = new View(getContext());
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        button = new ButtonWithCounterView(getContext(), resourcesProvider);
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, pad + 16, 16, pad + 16, 16));

        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        recyclerListView.setPadding(
            recyclerListView.getPaddingLeft(),
            recyclerListView.getPaddingTop(),
            recyclerListView.getPaddingRight(),
            recyclerListView.getPaddingBottom() + dp(16 + 48 + 16)
        );
    }

    @Override
    public void setTitle(CharSequence value) {
        actionBar.setTitle(value);
    }

    @Override
    protected CharSequence getTitle() {
        return null;
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return new Adapter();
    }

    public class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(layout);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }


    public static class SpaceView extends View {
        public SpaceView(Context context) {
            super(context);
        }

        private int height = 0;
        public void setHeight(int heightPx, int a) {
            if (this.height != heightPx) {
                this.height = heightPx;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(this.height, MeasureSpec.EXACTLY)
            );
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return false;
        }
    }
}
