package org.telegram.ui.Animations;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AnimationPropertiesCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SelectColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

public class AnimationsSettingsAdapter extends RecyclerView.Adapter<RecyclerListView.Holder> {

    private static final int VIEW_TYPE_SECTION = 0;
    private static final int VIEW_TYPE_DIVIDER = 1;
    private static final int VIEW_TYPE_HEADER = 2;
    private static final int VIEW_TYPE_TEXT = 3;
    private static final int VIEW_TYPE_PREVIEW = 4;
    private static final int VIEW_TYPE_SELECT_COLOR = 5;
    private static final int VIEW_TYPE_ANIMATION_PROPERTIES = 6;

    private final List<Item> items = new ArrayList<>();

    @Nullable
    private Callback callback;

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case VIEW_TYPE_SECTION: {
                view = new ShadowSectionCell(parent.getContext());
                break;
            }
            case VIEW_TYPE_DIVIDER: {
                view = new DividerCell(parent.getContext());
                view.setPadding(AndroidUtilities.dp(21), 0, 0, 0);
                break;
            }
            case VIEW_TYPE_HEADER: {
                HeaderCell headerCell = new HeaderCell(parent.getContext());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) headerCell.getTextView().getLayoutParams();
                lp.bottomMargin = lp.topMargin;
                view = headerCell;
                break;
            }
            case VIEW_TYPE_TEXT: {
                TextSettingsCell textCell = new TextSettingsCell(parent.getContext());
                textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
                view = textCell;
                break;
            }
            case VIEW_TYPE_PREVIEW: {
                view = new GradientBackgroundView(parent.getContext());
                int height = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.37);
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
                break;
            }
            case VIEW_TYPE_SELECT_COLOR: {
                SelectColorCell cell = new SelectColorCell(parent.getContext());
                cell.setColorListener(callback);
                view = cell;
                break;
            }
            case VIEW_TYPE_ANIMATION_PROPERTIES: {
                AnimationPropertiesCell cell = new AnimationPropertiesCell(parent.getContext());
                view = cell;
                break;
            }
        }
        if (view != null && viewType != VIEW_TYPE_SECTION && viewType != VIEW_TYPE_PREVIEW) {
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerListView.Holder holder, int position) {
        Item item = items.get(position);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_SECTION: {
                ShadowSectionCell cell = (ShadowSectionCell) holder.itemView;
                int resId = position == items.size() - 1 ? R.drawable.greydivider_bottom : R.drawable.greydivider;
                cell.setBackground(Theme.getThemedDrawable(cell.getContext(), resId, Theme.key_windowBackgroundGrayShadow));
                break;
            }
            case VIEW_TYPE_HEADER: {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setText(((HeaderItem) item).text);
                break;
            }
            case VIEW_TYPE_TEXT: {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText(((TextItem) item).text, false);
                break;
            }
            case VIEW_TYPE_PREVIEW: {
                GradientBackgroundView view = (GradientBackgroundView) holder.itemView;
                PreviewItem previewItem = (PreviewItem) item;
                view.setColors(previewItem.colors);
                break;
            }
            case VIEW_TYPE_SELECT_COLOR: {
                SelectColorCell view = (SelectColorCell) holder.itemView;
                SelectColorItem colorItem = (SelectColorItem) item;
                view.setTitle(colorItem.text);
                view.setColor(colorItem.color);
                break;
            }
            case VIEW_TYPE_ANIMATION_PROPERTIES: {
                AnimationPropertiesCell view = (AnimationPropertiesCell) holder.itemView;
                AnimationPropertiesItem propertiesItem = (AnimationPropertiesItem) item;
                view.setMaxValue(propertiesItem.maxDuration);
                view.setLeftProgress(propertiesItem.getLeftProgress());
                view.setRightProgress(propertiesItem.getRightProgress());
                view.setTopProgress(propertiesItem.topProgress);
                view.setBottomProgress(propertiesItem.botProgress);
                break;
            }
        }
        holder.itemView.setTag(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        Item item = items.get(position);
        return item.getType();
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void updateItem(int position, Item item) {
        items.set(position, item);
        notifyItemChanged(position);
    }

    public Item getItemAt(int position) {
        return items.get(position);
    }


    public interface Callback extends SelectColorBottomSheet.ColorListener {}


    public abstract static class Item {

        public abstract int getType();
    }

    public static final class SectionItem extends Item {

        @Override
        public int getType() {
            return VIEW_TYPE_SECTION;
        }
    }

    public static final class DividerItem extends Item {

        @Override
        public int getType() {
            return VIEW_TYPE_DIVIDER;
        }
    }

    public static class TextItem extends Item {

        public final String text;

        public TextItem(String text) {
            this.text = text;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_TEXT;
        }
    }

    public static final class HeaderItem extends TextItem {

        public HeaderItem(String text) {
            super(text);
        }

        @Override
        public int getType() {
            return VIEW_TYPE_HEADER;
        }
    }

    public static final class PreviewItem extends Item {

        public final int[] colors;

        public PreviewItem(int[] colors) {
            this.colors = colors;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_PREVIEW;
        }
    }

    public static final class SelectColorItem extends TextItem {

        public final int id;
        @ColorInt
        public int color;

        public SelectColorItem(String text, int id, int color) {
            super(text);
            this.id = id;
            this.color = color;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_SELECT_COLOR;
        }
    }

    public static final class AnimationPropertiesItem extends Item {

        public final int id;
        public final int leftDuration;
        public final int rightDuration;
        public final int maxDuration;
        public final float topProgress;
        public final float botProgress;

        public AnimationPropertiesItem(int id, int leftDuration, int rightDuration, int maxDuration, float topProgress, float botProgress) {
            this.id = id;
            this.leftDuration = leftDuration;
            this.rightDuration = rightDuration;
            this.maxDuration = maxDuration;
            this.topProgress = topProgress;
            this.botProgress = botProgress;
        }

        public float getLeftProgress() {
            return leftDuration * 1f / maxDuration;
        }

        public float getRightProgress() {
            return rightDuration * 1f / maxDuration;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_ANIMATION_PROPERTIES;
        }
    }
}
