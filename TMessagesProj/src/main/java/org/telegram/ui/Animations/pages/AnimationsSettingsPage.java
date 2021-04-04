package org.telegram.ui.Animations.pages;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.AnimationsSettingsAdapter;
import org.telegram.ui.Cells.AnimationPropertiesCell;
import org.telegram.ui.Cells.DurationCell;
import org.telegram.ui.Components.RecyclerListView;

public abstract class AnimationsSettingsPage implements AnimationsSettingsAdapter.Callback,
        RecyclerListView.OnItemClickListener {

    private static final int[] durations = new int[] { 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000 };

    public final int type;
    public final String title;

    protected final AnimationsSettingsAdapter adapter = new AnimationsSettingsAdapter();
    private RecyclerListView.OnItemClickListener clickListener;

    public AnimationsSettingsPage(int type, String title) {
        this.type = type;
        this.title = title;
    }

    public View createView(Context context) {
        RecyclerListView recyclerView = new RecyclerListView(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setItemAnimator(null);
        recyclerView.setDisallowInterceptTouchEvents(true);
        recyclerView.setOnItemClickListener(this);
        return recyclerView;
    }

    public void refresh() {}

    @Override
    public void onItemClick(View view, int position) {
        if (view instanceof DurationCell) {
            showDurationPopup((DurationCell) view);
        } else {
            if (clickListener != null) {
                clickListener.onItemClick(view, position);
            }
        }
    }

    @Override
    public void onPropertiesChanged(AnimationPropertiesCell cell, @Nullable Object tag) {
        if (tag instanceof AnimationsSettingsAdapter.AnimationPropertiesItem) {
            AnimationsSettingsAdapter.AnimationPropertiesItem item = (AnimationsSettingsAdapter.AnimationPropertiesItem) tag;
            AnimationSettings settings = item.settings;
            settings.setLeftDuration((int) (cell.getLeftProgress() * cell.getMaxValue()));
            settings.setRightDuration((int) (cell.getRightProgress() * cell.getMaxValue()));
            settings.setTopProgress(cell.getTopProgress());
            settings.setBotProgress(cell.getBottomProgress());
            onPropertiesItemChanged(item);
        }
    }

    @Override
    public void onDurationSelected(@Nullable Object tag, int duration) {
        if (tag instanceof AnimationsSettingsAdapter.DurationItem) {
            AnimationsSettingsAdapter.DurationItem item = (AnimationsSettingsAdapter.DurationItem) tag;
            item.duration = duration;
            onDurationItemChanged(item);
        }
    }

    public void setOnItemClickListener(RecyclerListView.OnItemClickListener clickListener) {
        this.clickListener = clickListener;
    }

    protected void onPropertiesItemChanged(AnimationsSettingsAdapter.AnimationPropertiesItem item) {}

    protected void onDurationItemChanged(AnimationsSettingsAdapter.DurationItem item) {}

    private void showDurationPopup(DurationCell cell) {
        DurationItemAdapter adapter = new DurationItemAdapter(durations);

        ListPopupWindow window = new ListPopupWindow(cell.getContext());
        window.setAdapter(adapter);
        window.setAnchorView(cell.getAnchorView());
        window.setModal(true);

        Drawable backgroundDrawable = ContextCompat.getDrawable(cell.getContext(), R.drawable.smiles_popup);
        backgroundDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.MULTIPLY));
        window.setBackgroundDrawable(backgroundDrawable);

        int color = Theme.getColor(Theme.key_listSelector);
        Drawable drawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(5), 0, color, 0xff000000);
        window.setListSelector(drawable);

        DurationItemAdapter.ViewHolder holder = new DurationItemAdapter.ViewHolder(cell.getContext());
        holder.bind(durations[durations.length - 1]);
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        holder.textView.measure(spec, spec);
        int width = holder.textView.getMeasuredWidth() + AndroidUtilities.dp(8);
        window.setWidth(width);

        window.setOnItemClickListener((parent, view, position, id) -> {
            window.dismiss();
            int selectedDuration = durations[position];
            cell.setDuration(selectedDuration);
            onDurationSelected(cell.getTag(), selectedDuration);
        });
        window.show();
    }

    private static class DurationItemAdapter extends BaseAdapter {

        private final int[] items;

        public DurationItemAdapter(int[] items) {
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                vh = new ViewHolder(parent.getContext());
                convertView = vh.textView;
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            vh.bind(items[position]);
            return vh.textView;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public Object getItem(int position) {
            return items[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        private static class ViewHolder {

            final TextView textView;

            public ViewHolder(Context context) {
                textView = new TextView(context);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                int leftRightPadding = AndroidUtilities.dp(18);
                int topBottomPadding = AndroidUtilities.dp(10);
                textView.setPadding(leftRightPadding, topBottomPadding, leftRightPadding, topBottomPadding);
            }

            public void bind(int item) {
                String text = LocaleController.formatString("", R.string.AnimationSettingsDurationMs, item);
                textView.setText(text);
            }
        }
    }
}