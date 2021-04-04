package org.telegram.ui.Animations.pages;

import android.animation.Animator;
import android.animation.ValueAnimator;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.AnimationPropertiesItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.DurationItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.HeaderItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.Item;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.PreviewItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.SectionItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.SelectColorItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.TextItem;

import java.util.ArrayList;
import java.util.List;

public class BackgroundAnimationSettingsPage extends AnimationsSettingsPage {

    public final int fullScreenPosition;

    private final int[] animPropsPosition = new int[AnimationsController.backAnimCount];
    private final int[] durationsPosition = new int[AnimationsController.backAnimCount];
    private final int[] colorPosition = new int[AnimationsController.backPointsCount];
    private final int previewPosition;

    public BackgroundAnimationSettingsPage() {
        super(-1, LocaleController.getString("", R.string.AnimationSettingsBackground));
        adapter.setCallback(this);

        int pos = 0;

        SectionItem sectionItem = new SectionItem();

        List<Item> items = new ArrayList<>();
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview)));
        items.add(previewPosition = pos++, new PreviewItem(AnimationsController.getInstance().getBackgroundColorsCopy()));
        items.add(fullScreenPosition = pos++, new TextItem(LocaleController.getString("", R.string.AnimationSettingsOpenFullScreen)));
        items.add(pos++, sectionItem);
        items.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsColors)));
        for (int i = 0; i < AnimationsController.backPointsCount; ++i) {
            String title = LocaleController.formatString("", R.string.AnimationSettingsColorN, i + 1);
            int color = AnimationsController.getInstance().getBackgroundCurrentColor(i);
            items.add(colorPosition[i] = pos++, new SelectColorItem(title, i, color));
        }

        items.add(pos++, sectionItem);

        int animPropsIdx = 0;
        int durationIdx = 0;
        for (int i = 0; i < AnimationsController.backAnimCount; ++i) {
            AnimationSettings s = AnimationsController.getInstance().getBackgroundAnimationSettings(i);
            items.add(pos++, new HeaderItem(s.title));
            items.add(durationsPosition[durationIdx++] = pos++, new DurationItem(s.id, s.maxDuration));
            items.add(animPropsPosition[animPropsIdx++] = pos++, new AnimationPropertiesItem(s));
            items.add(pos++, sectionItem);
        }

        adapter.setItems(items);
    }

    @Override
    public void refresh() {
        super.refresh();
        PreviewItem previewItem = (PreviewItem) adapter.getItemAt(previewPosition);
        for (int i = 0; i < AnimationsController.backPointsCount; ++i) {
            int color = AnimationsController.getInstance().getBackgroundCurrentColor(i);
            previewItem.colors[i] = color;
            SelectColorItem colorItem = (SelectColorItem) adapter.getItemAt(colorPosition[i]);
            colorItem.color = color;
            adapter.notifyItemChanged(colorPosition[i]);
        }
        adapter.notifyItemChanged(previewPosition);

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
            PreviewItem previewItem = (PreviewItem) adapter.getItemAt(previewPosition);
            int startColor = previewItem.colors[item.id];
            int endColor = AnimationsController.getInstance().getBackgroundCurrentColor(item.id);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(animation -> {
                float fraction = animation.getAnimatedFraction();
                int color = ColorUtils.blendARGB(startColor, endColor, fraction);
                setColor(color, tag, false);
            });
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setColor(AnimationsController.getInstance().getBackgroundCurrentColor(item.id), tag, true);
                }
                @Override public void onAnimationStart(Animator animation) { }
                @Override public void onAnimationCancel(Animator animation) { }
                @Override public void onAnimationRepeat(Animator animation) { }
            });
            animator.setDuration(300);
            animator.start();
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

            PreviewItem previewItem = (PreviewItem) adapter.getItemAt(previewPosition);
            previewItem.colors[colorIdx] = color;
            adapter.updateItem(previewPosition, previewItem);

            SelectColorItem colorItem = (SelectColorItem) adapter.getItemAt(colorPosition[colorIdx]);
            colorItem.color = color;
            adapter.updateItem(colorPosition[colorIdx], colorItem);

            if (apply) {
                AnimationsController.getInstance().setBackgroundCurrentColor(colorIdx, color);
            }
        }
    }
}
