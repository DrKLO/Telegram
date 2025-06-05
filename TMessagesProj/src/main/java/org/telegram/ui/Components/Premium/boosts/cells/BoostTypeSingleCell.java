package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;

@SuppressLint("ViewConstructor")
public class BoostTypeSingleCell extends BoostTypeCell {

    public BoostTypeSingleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    public void setGiveaway(TL_stories.PrepaidGiveaway prepaidGiveaway) {
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
        if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
            TL_stories.TL_prepaidStarsGiveaway prepaid = (TL_stories.TL_prepaidStarsGiveaway) prepaidGiveaway;
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_STARS);
            titleTextView.setText(LocaleController.formatPluralStringComma("BoostingStarsPreparedGiveawaySubscriptionsPlural", (int) prepaid.stars));
            setSubtitle(LocaleController.formatPluralString("AmongWinners", prepaid.quantity));
        } else if (prepaidGiveaway instanceof TL_stories.TL_prepaidGiveaway) {
            titleTextView.setText(LocaleController.getString(R.string.BoostingPreparedGiveawayOne));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_GIFT);
            TL_stories.TL_prepaidGiveaway prepaid = (TL_stories.TL_prepaidGiveaway) prepaidGiveaway;
            if (prepaid.months == 12) {
                avatarDrawable.setColor(0xFFff8560, 0xFFd55246);
            } else if (prepaid.months == 6) {
                avatarDrawable.setColor(0xFF5caefa, 0xFF418bd0);
            } else {
                avatarDrawable.setColor(0xFF9ad164, 0xFF49ba44);
            }
            String subtitle = LocaleController.formatPluralString("BoostingPreparedGiveawaySubscriptionsPlural", prepaidGiveaway.quantity, LocaleController.formatPluralString("Months", prepaid.months));
            setSubtitle(subtitle);
        }
        imageView.setImageDrawable(avatarDrawable);
        imageView.setRoundRadius(dp(20));
    }

    @Override
    protected void updateLayouts() {
        imageView.setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 16, 0, 16, 0));
        titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (69), 0, LocaleController.isRTL ? (69) : 20, 0));
        subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (69), 0, LocaleController.isRTL ? (69) : 20, 0));
    }

    @Override
    protected boolean needCheck() {
        return false;
    }
}
