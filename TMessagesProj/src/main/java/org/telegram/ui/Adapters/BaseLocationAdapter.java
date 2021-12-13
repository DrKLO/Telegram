/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.location.Location;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public abstract class BaseLocationAdapter extends RecyclerListView.SelectionAdapter {

    public interface BaseLocationAdapterDelegate {
        void didLoadSearchResult(ArrayList<TLRPC.TL_messageMediaVenue> places);
    }

    protected boolean searching;
    protected ArrayList<TLRPC.TL_messageMediaVenue> places = new ArrayList<>();
    protected ArrayList<String> iconUrls = new ArrayList<>();
    private Location lastSearchLocation;
    private String lastSearchQuery;
    private String lastFoundQuery;
    private BaseLocationAdapterDelegate delegate;
    private Runnable searchRunnable;
    private int currentRequestNum;
    private int currentAccount = UserConfig.selectedAccount;
    private long dialogId;
    private boolean searchingUser;
    private boolean searchInProgress;

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
            searchInProgress = false;
            notifyDataSetChanged();
        } else {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            searchInProgress = true;
            Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;
                lastSearchLocation = null;
                searchPlacesWithQuery(query, coordinate, true);
            }), 400);
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

    public boolean isSearching() {
        return searchInProgress;
    }

    public String getLastSearchString() {
        return lastFoundQuery;
    }

    public void searchPlacesWithQuery(final String query, final Location coordinate, boolean searchUser) {
        searchPlacesWithQuery(query, coordinate, searchUser, false);
    }

    public void searchPlacesWithQuery(final String query, final Location coordinate, boolean searchUser, boolean animated) {
        if (coordinate == null || lastSearchLocation != null && coordinate.distanceTo(lastSearchLocation) < 200) {
            return;
        }
        lastSearchLocation = new Location(coordinate);
        lastSearchQuery = query;
        if (searching) {
            searching = false;
            if (currentRequestNum != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(currentRequestNum, true);
                currentRequestNum = 0;
            }
        }
        int oldItemCount = getItemCount();
        boolean wasSearching = searching;
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

        if (DialogObject.isEncryptedDialog(dialogId)) {
            req.peer = new TLRPC.TL_inputPeerEmpty();
        } else {
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        }

        currentRequestNum = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            currentRequestNum = 0;
            searching = false;
            places.clear();
            iconUrls.clear();
            searchInProgress = false;
            lastFoundQuery = query;

            if (error == null) {
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
            if (delegate != null) {
                delegate.didLoadSearchResult(places);
            }
            notifyDataSetChanged();
        }));
        if (animated && Build.VERSION.SDK_INT >= 19) {
            if (places.isEmpty() || wasSearching) {
                if (!wasSearching) {
                    notifyItemChanged(getItemCount() - 1);
                }
            } else {
                int placesCount = places.size() + 1;
                int offset = oldItemCount - placesCount;
                notifyItemInserted(offset);
                notifyItemRangeRemoved(offset, placesCount);
            }
        } else {
            notifyDataSetChanged();
        }
    }
}
