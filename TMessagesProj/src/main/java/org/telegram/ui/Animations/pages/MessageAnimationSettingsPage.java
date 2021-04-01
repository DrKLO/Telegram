package org.telegram.ui.Animations.pages;

import androidx.annotation.Nullable;

import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.*;
import org.telegram.ui.Animations.MsgAnimationSettings;
import org.telegram.ui.Components.AnimationProperties;

import java.util.ArrayList;
import java.util.List;

public class MessageAnimationSettingsPage extends AnimationsSettingsPage {

    private final int[] animPropsPosition;
    private final MsgAnimationSettings settings;

    public MessageAnimationSettingsPage(int type, String title) {
        super(type, title);
        settings = AnimationsController.getInstance().getMsgAnimSettings(type);
        adapter.setCallback(this);

        AnimationsSettingsAdapter.SectionItem sectionItem = new AnimationsSettingsAdapter.SectionItem();
        MsgAnimationSettings settings = AnimationsController.getInstance().getMsgAnimSettings(type);
        animPropsPosition = new int[settings.settings.length];

        int pos = 0;
        List<Item> items = new ArrayList<>();
        items.add(pos++, new DurationItem(type, settings.getDuration()));
        items.add(pos++, sectionItem);

        int animPropsIdx = 0;
        for (AnimationSettings s : settings.settings) {
            items.add(pos++, new HeaderItem(s.title));
            items.add(animPropsPosition[animPropsIdx++] = pos++, new AnimationPropertiesItem(s));
            items.add(pos++, sectionItem);
        }

        adapter.setItems(items);
    }

    @Override
    protected void onPropertiesItemChanged(AnimationPropertiesItem item) {
        super.onPropertiesItemChanged(item);
        AnimationsController.getInstance().updateMsgAnimSettings(settings);
    }

    @Override
    protected void onDurationItemChanged(DurationItem item) {
        super.onDurationItemChanged(item);
        settings.setDuration(item.duration);
        for (int i = 0; i < animPropsPosition.length; ++i) {
            adapter.notifyItemChanged(animPropsPosition[i]);
        }
        AnimationsController.getInstance().updateMsgAnimSettings(settings);
    }
}
