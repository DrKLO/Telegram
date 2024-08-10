/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public abstract class BaseLocationAdapter extends RecyclerListView.SelectionAdapter {

    public final boolean stories;
    public final boolean biz;

    public BaseLocationAdapter(boolean stories, boolean biz) {
        this.stories = stories;
        this.biz = biz;
    }

    public interface BaseLocationAdapterDelegate {
        void didLoadSearchResult(ArrayList<TLRPC.TL_messageMediaVenue> places);
    }

    protected boolean searched = false;
    protected boolean searching;
    protected boolean searchingLocations;
    protected ArrayList<TLRPC.TL_messageMediaVenue> locations = new ArrayList<>();
    protected ArrayList<TLRPC.TL_messageMediaVenue> places = new ArrayList<>();
    private Location lastSearchLocation;
    private String lastSearchQuery;
    private String lastFoundQuery;
    private BaseLocationAdapterDelegate delegate;
    private Runnable searchRunnable;
    private int currentRequestNum;
    private int currentAccount = UserConfig.selectedAccount;
    private long dialogId;
    private boolean searchingUser;
    protected boolean searchInProgress;

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
            locations.clear();
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
        req.username = stories ?
            MessagesController.getInstance(currentAccount).storyVenueSearchBot :
            MessagesController.getInstance(currentAccount).venueSearchBot;
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

    protected void notifyStartSearch(boolean wasSearching, int oldItemCount, boolean animated) {
        if (animated && Build.VERSION.SDK_INT >= 19) {
            if (places.isEmpty() || wasSearching) {
                if (!wasSearching) {
                    int fromIndex = Math.max(0, getItemCount() - 4);
                    notifyItemRangeRemoved(fromIndex, getItemCount() - fromIndex);
                }
            } else {
                int placesCount = 3 + places.size() + locations.size();
                int offset = oldItemCount - placesCount;
                notifyItemInserted(offset);
                notifyItemRangeRemoved(offset, placesCount);
            }
        } else {
            notifyDataSetChanged();
        }
    }

    public void searchPlacesWithQuery(final String query, final Location coordinate, boolean searchUser, boolean animated) {
        if (coordinate == null && !stories || lastSearchLocation != null && coordinate != null && coordinate.distanceTo(lastSearchLocation) < 200) {
            return;
        }
        lastSearchLocation = coordinate == null ? null : new Location(coordinate);
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
        boolean wasSearched = searched;
        searched = true;

        TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(
            stories ?
                MessagesController.getInstance(currentAccount).storyVenueSearchBot :
                MessagesController.getInstance(currentAccount).venueSearchBot
        );
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

        if (coordinate != null) {
            req.geo_point = new TLRPC.TL_inputGeoPoint();
            req.geo_point.lat = AndroidUtilities.fixLocationCoord(coordinate.getLatitude());
            req.geo_point._long = AndroidUtilities.fixLocationCoord(coordinate.getLongitude());
            req.flags |= 1;
        }

        if (DialogObject.isEncryptedDialog(dialogId)) {
            req.peer = new TLRPC.TL_inputPeerEmpty();
        } else {
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        }

        if (!TextUtils.isEmpty(query) && (stories || biz)) {
            searchingLocations = true;
            final Locale locale = LocaleController.getInstance().getCurrentLocale();
            final String finalQuery = query;
            Utilities.globalQueue.postRunnable(() -> {
                final ArrayList<TLRPC.TL_messageMediaVenue> locations = new ArrayList<>();
                try {
                    final int maxCount = biz ? 10 : 5;
                    Geocoder geocoder = new Geocoder(ApplicationLoader.applicationContext, locale);
                    List<Address> addresses = geocoder.getFromLocationName(finalQuery, 5);
                    HashSet<String> countries = new HashSet<>();
                    HashSet<String> cities = new HashSet<>();
                    String arg, lc;
                    for (int i = 0; i < addresses.size(); ++i) {
                        Address address = addresses.get(i);
                        if (!address.hasLatitude() || !address.hasLongitude())
                            continue;
                        double lat = address.getLatitude();
                        double _long = address.getLongitude();

                        StringBuilder countryBuilder = new StringBuilder();
                        StringBuilder cityBuilder = new StringBuilder();
                        StringBuilder streetBuilder = new StringBuilder();
                        boolean onlyCountry = true;
                        boolean onlyCity = true;

                        String locality = address.getLocality();
                        if (TextUtils.isEmpty(locality)) {
                            locality = address.getAdminArea();
                        }
                        arg = address.getThoroughfare();
                        if (!TextUtils.isEmpty(arg) && !TextUtils.equals(arg, address.getAdminArea())) {
                            if (streetBuilder.length() > 0) {
                                streetBuilder.append(", ");
                            }
                            streetBuilder.append(arg);
                            onlyCity = false;
                        } else {
                            arg = address.getSubLocality();
                            if (!TextUtils.isEmpty(arg)) {
                                if (streetBuilder.length() > 0) {
                                    streetBuilder.append(", ");
                                }
                                streetBuilder.append(arg);
                                onlyCity = false;
                            } else {
                                arg = address.getLocality();
                                if (!TextUtils.isEmpty(arg) && !TextUtils.equals(arg, locality)) {
                                    if (streetBuilder.length() > 0) {
                                        streetBuilder.append(", ");
                                    }
                                    streetBuilder.append(arg);
                                    onlyCity = false;
                                } else {
                                    streetBuilder = null;
                                }
                            }
                        }
                        if (!TextUtils.isEmpty(locality)) {
                            if (cityBuilder.length() > 0) {
                                cityBuilder.append(", ");
                            }
                            cityBuilder.append(locality);
                            onlyCountry = false;
                            if (streetBuilder != null) {
                                if (streetBuilder.length() > 0) {
                                    streetBuilder.append(", ");
                                }
                                streetBuilder.append(locality);
                            }
                        }
                        arg = address.getCountryName();
                        if (!TextUtils.isEmpty(arg)) {
                            String shortCountry = arg;
                            if ("US".equals(address.getCountryCode()) || "AE".equals(address.getCountryCode()) || "GB".equals(address.getCountryCode()) && "en".equals(locale.getLanguage())) {
                                shortCountry = "";
                                String[] words = arg.split(" ");
                                for (String word : words) {
                                    if (word.length() > 0)
                                        shortCountry += word.charAt(0);
                                }
                            }
                            if (cityBuilder.length() > 0) {
                                cityBuilder.append(", ");
                            }
                            cityBuilder.append(shortCountry);
                            if (countryBuilder.length() > 0) {
                                countryBuilder.append(", ");
                            }
                            countryBuilder.append(arg);
                        }

                        if (biz) {
                            StringBuilder addressBuilder = new StringBuilder();
                            try {
                                arg = address.getAddressLine(0);
                                if (!TextUtils.isEmpty(arg)) {
                                    addressBuilder.append(arg);
                                }
                            } catch (Exception ignore) {
                            }
                            if (addressBuilder.length() > 0) {
                                TLRPC.TL_messageMediaVenue streetLocation = new TLRPC.TL_messageMediaVenue();
                                streetLocation.geo = new TLRPC.TL_geoPoint();
                                streetLocation.geo.lat = lat;
                                streetLocation.geo._long = _long;
                                streetLocation.query_id = -1;
                                streetLocation.title = addressBuilder.toString();
                                streetLocation.icon = "pin";
                                streetLocation.address = LocaleController.getString(R.string.PassportAddress);
                                locations.add(streetLocation);
                            }
                        } else {
                            if (streetBuilder != null && streetBuilder.length() > 0) {
                                TLRPC.TL_messageMediaVenue streetLocation = new TLRPC.TL_messageMediaVenue();
                                streetLocation.geo = new TLRPC.TL_geoPoint();
                                streetLocation.geo.lat = lat;
                                streetLocation.geo._long = _long;
                                streetLocation.query_id = -1;
                                streetLocation.title = streetBuilder.toString();
                                streetLocation.icon = "pin";
                                streetLocation.address = onlyCity ? LocaleController.getString("PassportCity", R.string.PassportCity) : LocaleController.getString("PassportStreet1", R.string.PassportStreet1);
                                locations.add(streetLocation);
                                if (locations.size() >= maxCount) {
                                    break;
                                }
                            }

                            if (!onlyCountry && !cities.contains(cityBuilder.toString())) {
                                TLRPC.TL_messageMediaVenue cityLocation = new TLRPC.TL_messageMediaVenue();
                                cityLocation.geo = new TLRPC.TL_geoPoint();
                                cityLocation.geo.lat = lat;
                                cityLocation.geo._long = _long;
                                cityLocation.query_id = -1;
                                cityLocation.title = cityBuilder.toString();
                                cityLocation.icon = "https://ss3.4sqi.net/img/categories_v2/travel/hotel_64.png";
                                cityLocation.emoji = LocationController.countryCodeToEmoji(address.getCountryCode());
                                cities.add(cityLocation.title);
                                cityLocation.address = LocaleController.getString("PassportCity", R.string.PassportCity);
                                locations.add(cityLocation);
                                if (locations.size() >= maxCount) {
                                    break;
                                }
                            }

                            if (countryBuilder.length() > 0 && !countries.contains(countryBuilder.toString())) {
                                TLRPC.TL_messageMediaVenue countryLocation = new TLRPC.TL_messageMediaVenue();
                                countryLocation.geo = new TLRPC.TL_geoPoint();
                                countryLocation.geo.lat = lat;
                                countryLocation.geo._long = _long;
                                countryLocation.query_id = -1;
                                countryLocation.title = countryBuilder.toString();
                                countryLocation.icon = "https://ss3.4sqi.net/img/categories_v2/building/government_capitolbuilding_64.png";
                                countryLocation.emoji = LocationController.countryCodeToEmoji(address.getCountryCode());
                                countries.add(countryLocation.title);
                                countryLocation.address = LocaleController.getString("Country", R.string.Country);
                                locations.add(countryLocation);
                                if (locations.size() >= maxCount) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignore) {}
                AndroidUtilities.runOnUIThread(() -> {
                    searchingLocations = false;
                    if (coordinate == null) {
                        currentRequestNum = 0;
                        searching = false;
                        places.clear();
                        searchInProgress = false;
                        lastFoundQuery = query;
                    }
                    BaseLocationAdapter.this.locations.clear();
                    BaseLocationAdapter.this.locations.addAll(locations);
                    notifyDataSetChanged();
                });
            });
        } else {
            searchingLocations = false;
        }

        if (coordinate == null) {
            return;
        }

        currentRequestNum = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                currentRequestNum = 0;
                searching = false;
                places.clear();
                searchInProgress = false;
                lastFoundQuery = query;

                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                for (int a = 0, size = res.results.size(); a < size; a++) {
                    TLRPC.BotInlineResult result = res.results.get(a);
                    if (!"venue".equals(result.type) || !(result.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue)) {
                        continue;
                    }
                    TLRPC.TL_botInlineMessageMediaVenue mediaVenue = (TLRPC.TL_botInlineMessageMediaVenue) result.send_message;
                    TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
                    venue.geo = mediaVenue.geo;
                    venue.address = mediaVenue.address;
                    venue.title = mediaVenue.title;
                    venue.icon = "https://ss3.4sqi.net/img/categories_v2/" + mediaVenue.venue_type + "_64.png";
                    venue.venue_type = mediaVenue.venue_type;
                    venue.venue_id = mediaVenue.venue_id;
                    venue.provider = mediaVenue.provider;
                    venue.query_id = res.query_id;
                    venue.result_id = result.id;
                    places.add(venue);
                }
            }
            if (delegate != null) {
                delegate.didLoadSearchResult(places);
            }
            notifyDataSetChanged();
        }));

        notifyDataSetChanged();
//        notifyStartSearch(wasSearched, wasSearching, oldItemCount, animated);
    }
}
