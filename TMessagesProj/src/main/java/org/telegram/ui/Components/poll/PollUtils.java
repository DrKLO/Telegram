package org.telegram.ui.Components.poll;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.vkryl.core.BitwiseUtils;

public class PollUtils {
    public static final int VOTE_RESTRICTED_NOT_SUBSCRIBED_FLAG = 1;
    public static final int VOTE_RESTRICTED_NOT_SUBSCRIBED_24H_FLAG = 1 << 1;
    public static final int VOTE_RESTRICTED_BY_COUNTRY_FLAG = 1 << 2;
    public static final int VOTE_RESTRICTED_BY_POLL_CLOSED_FLAG = 1 << 3;

    public static int getVoteRestrictedFlags(MessageObject messageObject) {
        if (messageObject.type != MessageObject.TYPE_POLL) {
            return 0;
        }
        final TLRPC.Message messageOwner = messageObject.messageOwner;
        final int currentAccount = messageObject.currentAccount;
        final TLRPC.TL_messageMediaPoll mediaPoll = MessageObject.getMedia(messageOwner, TLRPC.TL_messageMediaPoll.class);
        if (mediaPoll == null) {
            return 0;
        }

        int flags = 0;

        if (mediaPoll.poll.closed) {
            flags |= VOTE_RESTRICTED_BY_POLL_CLOSED_FLAG;
        }

        if (mediaPoll.poll.subscribers_only) {
            final long dialogId = messageOwner.fwd_from != null ?
                DialogObject.getPeerDialogId(messageOwner.fwd_from.from_id) : messageObject.getDialogId();
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);


            if (chat != null) {
                if (chat.left || chat.kicked) {
                    flags |= VOTE_RESTRICTED_NOT_SUBSCRIBED_FLAG;
                }

                final int messageTime = messageOwner.fwd_from != null ?
                        messageOwner.fwd_from.date : messageOwner.date;
                if ((messageTime - chat.date) < 86400) {
                    flags |= VOTE_RESTRICTED_NOT_SUBSCRIBED_24H_FLAG;
                }
            }
        }

        if (mediaPoll.poll.countries_iso2 != null && !mediaPoll.poll.countries_iso2.isEmpty()) {
            String myCountry = MessagesController.getInstance(currentAccount).config.phoneCountryIso2.get();
            if (!mediaPoll.poll.countries_iso2.contains(myCountry)) {
                flags |= VOTE_RESTRICTED_BY_COUNTRY_FLAG;
            }
        }

        return flags;
    }

    @Nullable
    public static CharSequence getVoteRestrictedToastText(MessageObject messageObject, int restrictedFlags) {
        if (messageObject.type != MessageObject.TYPE_POLL) {
            return null;
        }
        final TLRPC.Message messageOwner = messageObject.messageOwner;
        final int currentAccount = messageObject.currentAccount;
        final TLRPC.TL_messageMediaPoll mediaPoll = MessageObject.getMedia(messageOwner, TLRPC.TL_messageMediaPoll.class);
        if (mediaPoll == null) {
            return null;
        }

        if (BitwiseUtils.hasFlag(restrictedFlags, PollUtils.VOTE_RESTRICTED_BY_COUNTRY_FLAG)) {
            final List<String> countries = new ArrayList<>(mediaPoll.poll.countries_iso2.size());
            for (String country: mediaPoll.poll.countries_iso2) {
                String countryName = LocaleController.getCountryName(country);
                countries.add(TextUtils.isEmpty(countryName) ? country : countryName);
            }

            final boolean subscribersOnly = mediaPoll.poll.subscribers_only;
            if (countries.size() == 1) {
                return AndroidUtilities.replaceTags(formatString(subscribersOnly ?
                    R.string.PollV2ToastOnlySubscribersFromCountriesCanVoteOne :
                    R.string.PollV2ToastOnlyUsersFromCountriesCanVoteOne, countries.get(0)));
            } else {
                StringBuffer b = new StringBuffer();
                for (int a = 0; a < countries.size() - 1; a++) {
                    if (b.length() > 0) {
                        b.append(", ");
                    }
                    b.append(countries.get(a));
                }
                return AndroidUtilities.replaceTags(formatString(subscribersOnly ?
                    R.string.PollV2ToastOnlySubscribersFromCountriesCanVoteOther :
                    R.string.PollV2ToastOnlyUsersFromCountriesCanVoteOther,
                b, countries.get(countries.size() - 1)));
            }
        }

        if (BitwiseUtils.hasFlag(restrictedFlags, PollUtils.VOTE_RESTRICTED_NOT_SUBSCRIBED_FLAG)) {
            final long dialogId = messageOwner.fwd_from != null ?
                DialogObject.getPeerDialogId(messageOwner.fwd_from.from_id) : messageObject.getDialogId();
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);

            return AndroidUtilities.replaceTags(formatString(R.string.PollV2ToastOnlySubscribersCanVote, DialogObject.getShortName(chat)));
        }

        if (BitwiseUtils.hasFlag(restrictedFlags, PollUtils.VOTE_RESTRICTED_NOT_SUBSCRIBED_24H_FLAG)) {
            return AndroidUtilities.replaceTags(getString(R.string.PollV2ToastOnlySubscribersJoined24hCanVote));
        }

        return null;
    }
}
