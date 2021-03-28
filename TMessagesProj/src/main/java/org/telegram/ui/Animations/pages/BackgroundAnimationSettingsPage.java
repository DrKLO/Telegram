package org.telegram.ui.Animations.pages;

import android.content.Context;

import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.*;
import org.telegram.ui.Cells.AnimationPropertiesCell;

import java.util.ArrayList;
import java.util.List;

public class BackgroundAnimationSettingsPage extends AnimationsSettingsPage implements Callback {

    private static int ITEMS = 0;
    private static final int SEND_MESSAGE_ITEM_ID = ITEMS++;
    private static final int OPEN_CHAT_ITEM_ID = ITEMS++;
    private static final int JUMP_MESSAGE_ITEM_ID = ITEMS++;

    public final int fullScreenPosition;

    private final int[] animPropsPosition = new int[ITEMS];
    private final int[] colorPosition = new int[AnimationsController.pointsCount];
    private final int backgroundPreviewPosition;

    public BackgroundAnimationSettingsPage(Context context) {
        super(context, -1, LocaleController.getString("", R.string.AnimationSettingsBackground));
        adapter.setCallback(this);

        int pos = 0;

        SectionItem sectionItem = new SectionItem();
        DividerItem dividerItem = new DividerItem();

        List<Item> items = new ArrayList<>();
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview)));
        items.add(backgroundPreviewPosition = pos++, new PreviewItem(AnimationsController.getColorsCopy()));
        items.add(fullScreenPosition = pos++, new TextItem(LocaleController.getString("", R.string.AnimationSettingsOpenFullScreen)));
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsColors)));
        for (int i = 0; i != AnimationsController.pointsCount; ++i) {
            items.add(colorPosition[i] = pos++, new SelectColorItem(LocaleController.formatString("", R.string.AnimationSettingsColorN, i + 1), i, AnimationsController.getCurrentColor(i)));
            if (i < AnimationsController.pointsCount - 1) {
                items.add(pos++, dividerItem);
            }
        }

        int animationPropertyItemIdx = 0;
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsSendMessage)));
        items.add(pos++, new DurationItem(SEND_MESSAGE_ITEM_ID, 1000));
        items.add(pos++, dividerItem);
        items.add(animPropsPosition[animationPropertyItemIdx++] = pos++, new AnimationPropertiesItem(SEND_MESSAGE_ITEM_ID, 100, 600, 1000, 0.2f, 0.65f));
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsOpenChat)));
        items.add(pos++, new DurationItem(OPEN_CHAT_ITEM_ID, 1000));
        items.add(pos++, dividerItem);
        items.add(animPropsPosition[animationPropertyItemIdx++] = pos++, new AnimationPropertiesItem(OPEN_CHAT_ITEM_ID, 100, 600, 1000, 0.2f, 0.65f));
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsJumpToMessage)));
        items.add(pos++, new DurationItem(JUMP_MESSAGE_ITEM_ID, 1000));
        items.add(pos++, dividerItem);
        items.add(animPropsPosition[animationPropertyItemIdx] = pos++, new AnimationPropertiesItem(JUMP_MESSAGE_ITEM_ID, 100, 600, 1000, 0.2f, 0.65f));
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
        if (tag instanceof SelectColorItem) {
            SelectColorItem item = (SelectColorItem) tag;
            // TODO agolokoz: maybe animate?
            setColor(AnimationsController.getCurrentColor(item.id), tag, true);
        }
    }

    @Override
    public void onPropertiesChanged(AnimationPropertiesCell cell, @Nullable Object tag) {
        if (tag instanceof AnimationPropertiesItem) {
            AnimationPropertiesItem item = (AnimationPropertiesItem) tag;
            item.leftDuration = (int)(cell.getLeftProgress() * cell.getMaxValue());
            item.rightDuration = (int)(cell.getRightProgress() * cell.getMaxValue());
            item.topProgress = cell.getTopProgress();
            item.botProgress = cell.getBottomProgress();
        }
    }

    @Override
    public void onDurationSelected(@Nullable Object tag, int duration) {
        if (tag instanceof DurationItem) {
            DurationItem item = (DurationItem) tag;
            int prevDuration = item.duration;
            item.duration = duration;

            int position = animPropsPosition[item.id];
            Item adapterItem = adapter.getItemAt(position);
            if (adapterItem instanceof AnimationPropertiesItem) {
                AnimationPropertiesItem propertiesItem = (AnimationPropertiesItem) adapterItem;
                float factor = duration * 1f / prevDuration;
                propertiesItem.leftDuration = Math.round(propertiesItem.leftDuration * factor);
                propertiesItem.rightDuration = Math.round(propertiesItem.rightDuration * factor);
                propertiesItem.maxDuration = duration;
                adapter.updateItem(position, adapterItem);
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

                AnimationsController.setCurrentColor(colorIdx, color);
            }
        }
    }
}
