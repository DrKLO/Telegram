package org.telegram.ui.Animations;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class ViewAnimationsController {

    public static final AnimationType[] animationTypes = new AnimationType[] {
            new AnimationType(0, LocaleController.getString("", R.string.AnimationSettingsShortText)),
            new AnimationType(1, LocaleController.getString("", R.string.AnimationSettingsLongText)),
            new AnimationType(2, LocaleController.getString("", R.string.AnimationSettingsLink)),
            new AnimationType(3, LocaleController.getString("", R.string.AnimationSettingsEmoji)),
            new AnimationType(4, LocaleController.getString("", R.string.AnimationSettingsPhoto)),
            new AnimationType(5, LocaleController.getString("", R.string.AnimationSettingsSticker)),
            new AnimationType(6, LocaleController.getString("", R.string.AnimationSettingsVoice)),
            new AnimationType(7, LocaleController.getString("", R.string.AnimationSettingsVideoMessage)),
            new AnimationType(8, LocaleController.getString("", R.string.AnimationSettingsMultiple)),
    };

    public static class AnimationType {

        public final int id;
        public final String title;

        public AnimationType(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
