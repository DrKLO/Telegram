package org.telegram.ui.Animations.pages;

import android.content.Context;

import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter;

import java.util.ArrayList;
import java.util.List;

public class BackgroundAnimationSettingsPage extends AnimationsSettingsPage implements AnimationsSettingsAdapter.Callback {

    public final int fullScreenPosition;

    private final int[] colorPosition = new int[AnimationsController.pointsCount];
    private final int backgroundPreviewPosition;

    public BackgroundAnimationSettingsPage(Context context) {
        super(context, -1, LocaleController.getString("", R.string.AnimationSettingsBackground));
        adapter.setCallback(this);

        int pos = 0;

        AnimationsSettingsAdapter.SectionItem sectionItem = new AnimationsSettingsAdapter.SectionItem();
        AnimationsSettingsAdapter.DividerItem dividerItem = new AnimationsSettingsAdapter.DividerItem();

        List<AnimationsSettingsAdapter.Item> items = new ArrayList<>();
        items.add(pos++, new AnimationsSettingsAdapter.HeaderItem(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview)));
        items.add(backgroundPreviewPosition = pos++, new AnimationsSettingsAdapter.PreviewItem(AnimationsController.getColorsCopy()));
        items.add(fullScreenPosition = pos++, new AnimationsSettingsAdapter.TextItem(LocaleController.getString("", R.string.AnimationSettingsOpenFullScreen)));
        items.add(pos++, sectionItem);
        items.add(pos++, new AnimationsSettingsAdapter.HeaderItem(LocaleController.getString("", R.string.AnimationSettingsColors)));
        for (int i = 0; i != AnimationsController.pointsCount; ++i) {
            items.add(colorPosition[i] = pos++, new AnimationsSettingsAdapter.SelectColorItem(LocaleController.formatString("", R.string.AnimationSettingsColorN, i + 1), i, AnimationsController.getCurrentColor(i)));
            if (i < AnimationsController.pointsCount - 1) {
                items.add(pos++, dividerItem);
            }
        }
        items.add(pos++, sectionItem);
        items.add(pos++, new AnimationsSettingsAdapter.HeaderItem(LocaleController.getString("", R.string.AnimationSettingsSendMessage)));
        items.add(pos++, sectionItem);
        items.add(pos++, new AnimationsSettingsAdapter.HeaderItem(LocaleController.getString("", R.string.AnimationSettingsOpenChat)));
        items.add(pos++, sectionItem);
        items.add(pos++, new AnimationsSettingsAdapter.HeaderItem(LocaleController.getString("", R.string.AnimationSettingsJumpToMessage)));
        items.add(pos++, sectionItem);

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
        if (tag instanceof AnimationsSettingsAdapter.SelectColorItem) {
            AnimationsSettingsAdapter.SelectColorItem item = (AnimationsSettingsAdapter.SelectColorItem) tag;
            setColor(AnimationsController.getCurrentColor(item.id), tag, true);
        }
    }

    private void setColor(int color, @Nullable Object tag, boolean apply) {
        if (tag instanceof AnimationsSettingsAdapter.SelectColorItem) {
            AnimationsSettingsAdapter.SelectColorItem item = (AnimationsSettingsAdapter.SelectColorItem) tag;
            int colorIdx = item.id;

            AnimationsSettingsAdapter.Item i = adapter.getItemAt(backgroundPreviewPosition);
            if (i instanceof AnimationsSettingsAdapter.PreviewItem) {
                AnimationsSettingsAdapter.PreviewItem previewItem = (AnimationsSettingsAdapter.PreviewItem) i;
                previewItem.colors[colorIdx] = color;
                adapter.updateItem(backgroundPreviewPosition, previewItem);
            }

            if (apply) {
                i = adapter.getItemAt(colorPosition[colorIdx]);
                if (i instanceof AnimationsSettingsAdapter.SelectColorItem) {
                    AnimationsSettingsAdapter.SelectColorItem colorItem = (AnimationsSettingsAdapter.SelectColorItem) i;
                    colorItem.color = color;
                    adapter.updateItem(colorPosition[colorIdx], colorItem);
                }

                AnimationsController.setCurrentColor(colorIdx, color);
            }
        }
    }
}
