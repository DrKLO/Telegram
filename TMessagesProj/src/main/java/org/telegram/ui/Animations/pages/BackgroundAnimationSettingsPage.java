package org.telegram.ui.Animations.pages;

import android.content.Context;

import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.BackgroundAnimationController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.*;
import org.telegram.ui.Cells.AnimationPropertiesCell;

import java.util.ArrayList;
import java.util.List;

public class BackgroundAnimationSettingsPage extends AnimationsSettingsPage implements Callback {

    public final int fullScreenPosition;

    private final int[] animPropsPosition = new int[BackgroundAnimationController.getAllSettings().length];
    private final int[] colorPosition = new int[BackgroundAnimationController.pointsCount];
    private final int backgroundPreviewPosition;

    public BackgroundAnimationSettingsPage(Context context) {
        super(context, -1, LocaleController.getString("", R.string.AnimationSettingsBackground));
        adapter.setCallback(this);

        int pos = 0;

        SectionItem sectionItem = new SectionItem();
        DividerItem dividerItem = new DividerItem();

        List<Item> items = new ArrayList<>();
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview)));
        items.add(backgroundPreviewPosition = pos++, new PreviewItem(BackgroundAnimationController.getColorsCopy()));
        items.add(fullScreenPosition = pos++, new TextItem(LocaleController.getString("", R.string.AnimationSettingsOpenFullScreen)));
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsColors)));
        for (int i = 0; i != BackgroundAnimationController.pointsCount; ++i) {
            items.add(colorPosition[i] = pos++, new SelectColorItem(LocaleController.formatString("", R.string.AnimationSettingsColorN, i + 1), i, BackgroundAnimationController.getCurrentColor(i)));
            if (i < BackgroundAnimationController.pointsCount - 1) {
                items.add(pos++, dividerItem);
            }
        }

        items.add(pos++, sectionItem);

        int animPropsIdx = 0;
        AnimationSettings[] settings = BackgroundAnimationController.getAllSettings();
        for (AnimationSettings s : settings) {
            items.add(pos++, new HeaderItem(s.title));
            items.add(pos++, new DurationItem(s.id, s.maxDuration));
            items.add(pos++, dividerItem);
            items.add(animPropsPosition[animPropsIdx++] = pos++, new AnimationPropertiesItem(s));
            items.add(pos++, sectionItem);
        }

        adapter.setItems(items);
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
            setColor(BackgroundAnimationController.getCurrentColor(item.id), tag, true);
        }
    }

    @Override
    public void onPropertiesChanged(AnimationPropertiesCell cell, @Nullable Object tag) {
        if (tag instanceof AnimationPropertiesItem) {
            AnimationSettings settings = ((AnimationPropertiesItem) tag).settings;
            settings.leftDuration = (int)(cell.getLeftProgress() * cell.getMaxValue());
            settings.rightDuration = (int)(cell.getRightProgress() * cell.getMaxValue());
            settings.topProgress = cell.getTopProgress();
            settings.botProgress = cell.getBottomProgress();
            BackgroundAnimationController.updateSettings(settings);
        }
    }

    @Override
    public void onDurationSelected(@Nullable Object tag, int duration) {
        if (tag instanceof DurationItem) {
            DurationItem item = (DurationItem) tag;
            item.duration = duration;

            int position = animPropsPosition[item.id];
            Item adapterItem = adapter.getItemAt(position);
            if (adapterItem instanceof AnimationPropertiesItem) {
                AnimationSettings settings = ((AnimationPropertiesItem) adapterItem).settings;
                settings.setMaxDuration(duration);
                adapter.updateItem(position, adapterItem);
                BackgroundAnimationController.updateSettings(settings);
            }
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

                BackgroundAnimationController.setCurrentColor(colorIdx, color);
            }
        }
    }
}
