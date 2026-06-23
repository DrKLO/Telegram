package org.Tajgram.ui.Components.Premium.boosts.cells;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.ChatObject;
import org.Tajgram.messenger.Emoji;
import org.Tajgram.messenger.LocaleController;
import org.Tajgram.messenger.R;
import org.Tajgram.messenger.UserObject;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Components.AvatarDrawable;

import java.util.List;

@SuppressLint("ViewConstructor")
public class ParticipantsTypeCell extends BaseCell {
    public static int TYPE_ALL = 0;
    public static int TYPE_NEW = 1;

    private int selectedType;

    public ParticipantsTypeCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        imageView.setVisibility(GONE);
    }

    public int getSelectedType() {
        return selectedType;
    }

    public void setType(int type, boolean isSelected, boolean needDivider, List<TLRPC.TL_help_country> countries, TLRPC.Chat chat) {
        selectedType = type;
        boolean isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
        if (type == TYPE_ALL) {
            titleTextView.setText(LocaleController.formatString(isChannel ? R.string.BoostingAllSubscribers : R.string.BoostingAllMembers));
        } else if (type == TYPE_NEW) {
            titleTextView.setText(LocaleController.formatString(isChannel ? R.string.BoostingNewSubscribers : R.string.BoostingNewMembers));
        }
        radioButton.setChecked(isSelected, false);
        setDivider(needDivider);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));

        if (countries.size() == 0) {
            setSubtitle(withArrow(LocaleController.getString(R.string.BoostingFromAllCountries)));
        } else if (countries.size() <= 3) {
            if (countries.size() == 1) {
                setSubtitle(withArrow(LocaleController.formatString("BoostingFromAllCountries1", R.string.BoostingFromAllCountries1, countries.get(0).default_name)));
            } else if (countries.size() == 2) {
                setSubtitle(withArrow(LocaleController.formatString("BoostingFromAllCountries2", R.string.BoostingFromAllCountries2, countries.get(0).default_name, countries.get(1).default_name)));
            } else {
                setSubtitle(withArrow(LocaleController.formatString("BoostingFromAllCountries3", R.string.BoostingFromAllCountries3,
                        countries.get(0).default_name,
                        countries.get(1).default_name,
                        countries.get(2).default_name)));
            }
        } else {
            setSubtitle(withArrow(LocaleController.formatPluralString("BoostingFromCountriesCount", countries.size())));
        }
    }

    @Override
    protected boolean needCheck() {
        return true;
    }
}
