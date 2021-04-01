package org.telegram.ui.Animations.pages;

import androidx.annotation.Nullable;

import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.AnimationsSettingsAdapter;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.*;
import org.telegram.ui.Animations.MsgAnimationSettings;

import java.util.ArrayList;
import java.util.List;

public class MessageAnimationSettingsPage extends AnimationsSettingsPage {

    private final int[] animPropsPosition;

    public MessageAnimationSettingsPage(int type, String title) {
        super(type, title);
        adapter.setCallback(this);

        int pos = 0;

        AnimationsSettingsAdapter.SectionItem sectionItem = new AnimationsSettingsAdapter.SectionItem();
        MsgAnimationSettings settings = AnimationsController.getInstance().getMsgAnimSettings(type);
        animPropsPosition = new int[settings.settings.length];

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
}
