package org.telegram.ui.Components;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;

public class HintsController {
    private HintsController() {

    }

    public enum Hint {
        RoundHint2("needShowRoundHint2", 3, 0.2f),
        RoundHintChannel2("needShowRoundHintChannel2", 3, 0.2f),

        ChannelSuggestHint("channelsuggesthint", 3, 0.2f),
        ChannelGiftHint("channelgifthint", 3, 0.2f),
        GroupEmojiPackHintShown("groupEmojiPackShownHint", 1, 1),
        AccountSwitchHint("accountswitchhint", 3, 1f);

        private final String name;
        private final int showsLimit;
        private final float probability;

        Hint(String name, int showsLimit, float probability) {
            this.name = name;
            this.showsLimit = showsLimit;
            this.probability = probability;
        }

        public boolean show() {
            final int showsCount = MessagesController.getGlobalMainSettings().getInt(name, 0);
            if (showsCount < showsLimit) {
                if (probability >= 1) {
                    return true;
                }
                if (probability <= 0) {
                    return false;
                }
                return Utilities.fastRandom.nextFloat() < probability;
            }
            return false;
        }

        public void increment() {
            final int showsCount = MessagesController.getGlobalMainSettings().getInt(name, 0) + 1;
            MessagesController.getGlobalMainSettings().edit().putInt(name, showsCount).apply();
        }

        public void doNotShowAgain() {
            MessagesController.getGlobalMainSettings().edit().putInt(name, showsLimit).apply();
        }

        public void reset() {
            MessagesController.getGlobalMainSettings().edit().remove(name).apply();
        }
    }

    public static void resetAll() {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        Hint[] hints = Hint.values();
        for (Hint hint : hints) {
            editor.remove(hint.name);
        }
        editor.apply();
    }
}
