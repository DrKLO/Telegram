/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.location.Location;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public abstract class BaseLocationAdapter extends RecyclerListView.SelectionAdapter {

    public interface BaseLocationAdapterDelegate {
        void didLoadedSearchResult(ArrayList<TLRPC.TL_messageMediaVenue> places);
    }

    protected boolean searching;
    protected ArrayList<TLRPC.TL_messageMediaVenue> places = new ArrayList<>();
    protected ArrayList<String> iconUrls = new ArrayList<>();
    private Location lastSearchLocation;
    private String lastSearchQuery;
    private BaseLocationAdapterDelegate delegate;
    private Timer searchTimer;
    private int currentRequestNum;
    private int currentAccount = UserConfig.selectedAccount;
    private long dialogId;
    private boolean searchingUser;

    public void destroy() {
        if (currentRequestNum != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestNum, true);
            currentRequestNum = 0;
        }
    }

    public void setDelegate(long did, BaseLocationAdapterDelegate delegate) {
        dialogId = did;
        this.delegate = delegate;
    }

    public void searchDelayed(final String query, final Location coordinate) {
        if (query == null || query.length() == 0) {
            places.clear();
            notifyDataSetChanged();
        } else {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        lastSearchLocation = null;
                        searchPlacesWithQuery(query, coordinate, true);
                    });
                }
            }, 200, 500);
        }
    }

    private void searchBotUser() {
        if (searchingUser) {
            return;
        }
        searchingUser = true;
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = MessagesController.getInstance(currentAccount).venueSearchBot;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                    Location coord = lastSearchLocation;
                    lastSearchLocation = null;
                    searchPlacesWithQuery(lastSearchQuery, coord, false);
                });
            }
        });
    }

    public void searchPlacesWithQuery(final String query, final Location coordinate, boolean searchUser) {
        if (coordinate == null || lastSearchLocation != null && coordinate.distanceTo(lastSearchLocation) < 200) {
            return;
        }
        lastSearchLocation = coordinate;
        lastSearchQuery = query;
        if (searching) {
            searching = false;
            if (currentRequestNum != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestNum, true);
                currentRequestNum = 0;
            }
        }
        searching = true;

        TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(MessagesController.getInstance(currentAccount).venueSearchBot);
        if (!(object instanceof TLRPC.User)) {
            if (searchUser) {
                searchBotUser();
            }
            return;
        }
        TLRPC.User user = (TLRPC.User) object;

        TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
        req.query = query == null ? "" : query;
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(user);
        req.offset = "";

        req.geo_point = new TLRPC.TL_inputGeoPoint();
        req.geo_point.lat = AndroidUtilities.fixLocationCoord(coordinate.getLatitude());
        req.geo_point._long = AndroidUtilities.fixLocationCoord(coordinate.getLongitude());
        req.flags |= 1;

        int lower_id = (int) dialogId;
        int high_id = (int) (dialogId >> 32);
        if (lower_id != 0) {
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(lower_id);
        } else {
            req.peer = new TLRPC.TL_inputPeerEmpty();
        }

        currentRequestNum = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            currentRequestNum = 0;
            searching = false;
            places.clear();
            iconUrls.clear();

            if (error != null) {
                if (delegate != null) {
                    delegate.didLoadedSearchResult(places);
                }
            } else {
                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                for (int a = 0, size = res.results.size(); a < size; a++) {
                    TLRPC.BotInlineResult result = res.results.get(a);
                    if (!"venue".equals(result.type) || !(result.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue)) {
                        continue;
                    }
                    TLRPC.TL_botInlineMessageMediaVenue mediaVenue = (TLRPC.TL_botInlineMessageMediaVenue) result.send_message;
                    iconUrls.add("https://ss3.4sqi.net/img/categories_v2/" + mediaVenue.venue_type + "_64.png");
                    TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
                    venue.geo = mediaVenue.geo;
                    venue.address = mediaVenue.address;
                    venue.title = mediaVenue.title;
                    venue.venue_type = mediaVenue.venue_type;
                    venue.venue_id = mediaVenue.venue_id;
                    venue.provider = mediaVenue.provider;
                    places.add(venue);
                }
            }
            notifyDataSetChanged();
        }));
        notifyDataSetChanged();
    }
}
