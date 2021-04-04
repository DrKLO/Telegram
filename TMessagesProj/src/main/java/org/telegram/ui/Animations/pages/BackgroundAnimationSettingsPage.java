package org.telegram.ui.Animations.pages;

import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.*;

import java.util.ArrayList;
import java.util.List;

public class BackgroundAnimationSettingsPage extends AnimationsSettingsPage {

    public final int fullScreenPosition;

    private final int[] animPropsPosition = new int[AnimationsController.backAnimCount];
    private final int[] durationsPosition = new int[AnimationsController.backAnimCount];
    private final int[] colorPosition = new int[AnimationsController.backPointsCount];
    private final int backgroundPreviewPosition;

    public BackgroundAnimationSettingsPage() {
        super(-1, LocaleController.getString("", R.string.AnimationSettingsBackground));
        adapter.setCallback(this);

        int pos = 0;

        SectionItem sectionItem = new SectionItem();
        DividerItem dividerItem = new DividerItem();

        List<Item> items = new ArrayList<>();
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview)));
        items.add(backgroundPreviewPosition = pos++, new PreviewItem(AnimationsController.getInstance().getBackgroundColorsCopy()));
        items.add(fullScreenPosition = pos++, new TextItem(LocaleController.getString("", R.string.AnimationSettingsOpenFullScreen)));
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsColors)));
        for (int i = 0; i < AnimationsController.backPointsCount; ++i) {
            String title = LocaleController.formatString("", R.string.AnimationSettingsColorN, i + 1);
            int color = AnimationsController.getInstance().getBackgroundCurrentColor(i);
            items.add(colorPosition[i] = pos++, new SelectColorItem(title, i, color));
            if (i < AnimationsController.backPointsCount - 1) {
                items.add(pos++, dividerItem);
            }
        }

        items.add(pos++, sectionItem);

        int animPropsIdx = 0;
        int durationIdx = 0;
        for (int i = 0; i < AnimationsController.backAnimCount; ++i) {
            AnimationSettings s = AnimationsController.getInstance().getBackgroundAnimationSettings(i);
            items.add(pos++, new HeaderItem(s.title));
            items.add(durationsPosition[durationIdx++] = pos++, new DurationItem(s.id, s.maxDuration));
            items.add(pos++, dividerItem);
            items.add(animPropsPosition[animPropsIdx++] = pos++, new AnimationPropertiesItem(s));
            items.add(pos++, sectionItem);
        }

        adapter.setItems(items);
    }

    @Override
    public void refresh() {
        super.refresh();
        PreviewItem previewItem = (PreviewItem) adapter.getItemAt(backgroundPreviewPosition);
        for (int i = 0; i < AnimationsController.backPointsCount; ++i) {
            int color = AnimationsController.getInstance().getBackgroundCurrentColor(i);
            previewItem.colors[i] = color;
            SelectColorItem colorItem = (SelectColorItem) adapter.getItemAt(colorPosition[i]);
            colorItem.color = color;
            adapter.notifyItemChanged(colorPosition[i]);
        }
        adapter.notifyItemChanged(backgroundPreviewPosition);

        for (int i = 0; i < AnimationsController.backAnimCount; ++i) {
            AnimationSettings settings = AnimationsController.getInstance().getBackgroundAnimationSettings(i);
            DurationItem durationItem = (DurationItem) adapter.getItemAt(durationsPosition[i]);
            durationItem.duration = settings.maxDuration;
            adapter.notifyItemChanged(durationsPosition[i]);
            AnimationPropertiesItem propertiesItem = (AnimationPropertiesItem) adapter.getItemAt(animPropsPosition[i]);
            propertiesItem.settings = settings;
            adapter.notifyItemChanged(animPropsPosition[i]);
        }
    }

    @Override
    public void onColorChanged(int color, @Nullable Object tag) {
        setColor(color, tag, false);
    }

    @Override
    public void onColorApplied(int color, @Nullable Object tag) {
        setColor(color, tag, true);
    }

    @Override
    public void onColorCancelled(@Nullable Object tag) {
        if (tag instanceof SelectColorItem) {
            SelectColorItem item = (SelectColorItem) tag;
            // TODO agolokoz: maybe animate?
            setColor(AnimationsController.getInstance().getBackgroundCurrentColor(item.id), tag, true);
        }
    }

    @Override
    protected void onPropertiesItemChanged(AnimationPropertiesItem item) {
        super.onPropertiesItemChanged(item);
        AnimationsController.getInstance().updateBackgroundSettings(item.settings);
    }

    @Override
    protected void onDurationItemChanged(DurationItem item) {
        super.onDurationItemChanged(item);
        int position = animPropsPosition[item.id];
        Item adapterItem = adapter.getItemAt(position);
        if (adapterItem instanceof AnimationPropertiesItem) {
            AnimationSettings settings = ((AnimationPropertiesItem) adapterItem).settings;
            settings.setMaxDuration(item.duration);
            adapter.updateItem(position, adapterItem);
            AnimationsController.getInstance().updateBackgroundSettings(settings);
        }
    }

    private void setColor(int color, @Nullable Object tag, boolean apply) {
        if (tag instanceof SelectColorItem) {
            SelectColorItem item = (SelectColorItem) tag;
            int colorIdx = item.id;

            Item i = adapter.getItemAt(backgroundPreviewPosition);
            if (i instanceof PreviewItem) {
                PreviewItem previewItem = (PreviewItem) i;
                previewItem.colors[colorIdx] = color;
                adapter.updateItem(backgroundPreviewPosition, previewItem);
            }

            if (apply) {
                i = adapter.getItemAt(colorPosition[colorIdx]);
                if (i instanceof SelectColorItem) {
                    SelectColorItem colorItem = (SelectColorItem) i;
                    colorItem.color = color;
                    adapter.updateItem(colorPosition[colorIdx], colorItem);
                }

                AnimationsController.getInstance().setBackgroundCurrentColor(colorIdx, color);
            }
        }
    }
}
