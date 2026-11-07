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
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public abstract class BaseLocationAdapter extends AdapterWithDiffUtils {

    public final boolean stories;
    public final boolean biz;

    public BaseLocationAdapter(boolean stories, boolean biz) {
        this.stories = stories;
        this.biz = biz;
    }

    public interface BaseLocationAdapterDelegate {
        void didLoadSearchResult(ArrayList<TLRPC.TL_messageMediaVenue> locations);
    }

    protected ArrayList<TLRPC.TL_messageMediaVenue> locations = new ArrayList<>();
    private String lastFoundQuery;
    private BaseLocationAdapterDelegate delegate;
    private Runnable searchRunnable;
    private int searchId;
    protected boolean searchInProgress;

    public void destroy() {
        cancelSearch();
    }

    public void setDelegate(BaseLocationAdapterDelegate delegate) {
        this.delegate = delegate;
    }

    private void cancelSearch() {
        if (searchRunnable != null) {
            Utilities.searchQueue.cancelRunnable(searchRunnable);
            searchRunnable = null;
        }
        searchId++;
    }

    public void searchDelayed(final String query) {
        cancelSearch();
        if (TextUtils.isEmpty(query)) {
            locations.clear();
            searchInProgress = false;
            update(true);
        } else {
            searchInProgress = true;
            Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;
                searchLocations(query);
            }), 400);
        }
    }

    public boolean isSearching() {
        return searchInProgress;
    }

    public String getLastSearchString() {
        return lastFoundQuery;
    }

    // Addresses come from the Geocoder: the venue bot (Foursquare) is gone
    private void searchLocations(final String query) {
        final int id = ++searchId;
        final Locale locale = LocaleController.getInstance().getCurrentLocale();
        final Locale englishLocale = stories ?  locale.getLanguage().contains("en") ? locale : Locale.US : null;
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<TLRPC.TL_messageMediaVenue> locations = new ArrayList<>();
            try {
                final int maxCount = biz ? 10 : 5;
                Geocoder geocoder = new Geocoder(ApplicationLoader.applicationContext, locale);
                List<Address> addresses = geocoder.getFromLocationName(query, 5);
                List<Address> engAddresses = null;
                if (stories) {
                    geocoder = new Geocoder(ApplicationLoader.applicationContext, englishLocale);
                    engAddresses = geocoder.getFromLocationName(query, 5);
                }
                HashSet<String> countries = new HashSet<>();
                HashSet<String> cities = new HashSet<>();
                String arg, lc;
                for (int i = 0; i < addresses.size(); ++i) {
                    Address address = addresses.get(i);
                    Address engAddress = engAddresses != null && i < engAddresses.size() ? engAddresses.get(i) : null;
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
                    String engLocality = null;
                    if (engAddress != null) {
                        engLocality = engAddress.getLocality();
                        if (TextUtils.isEmpty(engLocality)) {
                            engLocality = engAddress.getAdminArea();
                        }
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
                            streetLocation.address = onlyCity ? LocaleController.getString(R.string.PassportCity) : LocaleController.getString(R.string.PassportStreet1);
                            boolean isUnnamed = false;
                            if (engAddress != null) {
                                streetLocation.geoAddress = new TL_stories.TL_geoPointAddress();
                                streetLocation.geoAddress.country_iso2 = engAddress.getCountryCode();

                                String engCity = null, engState = null;
                                if (TextUtils.isEmpty(engCity)) {
                                    engCity = engAddress.getLocality();
                                }
                                if (TextUtils.isEmpty(engCity)) {
                                    engCity = engAddress.getAdminArea();
                                }
                                if (TextUtils.isEmpty(engCity)) {
                                    engCity = engAddress.getSubAdminArea();
                                }
                                engState = engAddress.getAdminArea();
                                StringBuilder engStreet = new StringBuilder();

                                if (!TextUtils.isEmpty(engState)) {
                                    streetLocation.geoAddress.state = engState;
                                    streetLocation.geoAddress.flags |= 1;
                                }
                                if (!TextUtils.isEmpty(engCity)) {
                                    streetLocation.geoAddress.city = engCity;
                                    streetLocation.geoAddress.flags |= 2;
                                }

                                if (!onlyCity) {
                                    String engFeature = null;
                                    if (TextUtils.isEmpty(engFeature) && !TextUtils.equals(engAddress.getThoroughfare(), locality) && !TextUtils.equals(engAddress.getThoroughfare(), engAddress.getCountryName())) {
                                        engFeature = engAddress.getThoroughfare();
                                    }
                                    if (TextUtils.isEmpty(engFeature) && !TextUtils.equals(engAddress.getSubLocality(), locality) && !TextUtils.equals(engAddress.getSubLocality(), engAddress.getCountryName())) {
                                        engFeature = engAddress.getSubLocality();
                                    }
                                    if (TextUtils.isEmpty(engFeature) && !TextUtils.equals(engAddress.getLocality(), locality) && !TextUtils.equals(engAddress.getLocality(), engAddress.getCountryName())) {
                                        engFeature = engAddress.getLocality();
                                    }
                                    if (!TextUtils.isEmpty(engFeature) && !TextUtils.equals(engFeature, engState) && !TextUtils.equals(engFeature, engAddress.getCountryName())) {
                                        if (engStreet.length() > 0) {
                                            engStreet.append(", ");
                                        }
                                        engStreet.append(engFeature);
                                    } else {
                                        engStreet = null;
                                    }

                                    if (!TextUtils.isEmpty(engStreet)) {
                                        for (int j = 0; j < LocationController.unnamedRoads.length; ++j) {
                                            if (LocationController.unnamedRoads[j].equalsIgnoreCase(engStreet.toString())) {
                                                isUnnamed = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (!TextUtils.isEmpty(engStreet)) {
                                        streetLocation.geoAddress.flags |= 4;
                                        streetLocation.geoAddress.street = engStreet.toString();
                                    }
                                }
                            }
                            if (!isUnnamed) {
                                locations.add(streetLocation);
                                if (locations.size() >= maxCount) {
                                    break;
                                }
                            }
                        }

                        if (!onlyCountry && !cities.contains(cityBuilder.toString())) {
                            TLRPC.TL_messageMediaVenue cityLocation = new TLRPC.TL_messageMediaVenue();
                            cityLocation.geo = new TLRPC.TL_geoPoint();
                            cityLocation.geo.lat = lat;
                            cityLocation.geo._long = _long;
                            cityLocation.query_id = -1;
                            cityLocation.title = cityBuilder.toString();
                            cityLocation.icon = "city";
                            cityLocation.emoji = LocationController.countryCodeToEmoji(address.getCountryCode());
                            cities.add(cityLocation.title);
                            cityLocation.address = LocaleController.getString(R.string.PassportCity);
                            if (engAddress != null) {
                                cityLocation.geoAddress = new TL_stories.TL_geoPointAddress();
                                cityLocation.geoAddress.country_iso2 = engAddress.getCountryCode();

                                String engCity = null, engState = null;
                                if (TextUtils.isEmpty(engCity)) {
                                    engCity = engAddress.getLocality();
                                }
                                if (TextUtils.isEmpty(engCity)) {
                                    engCity = engAddress.getAdminArea();
                                }
                                if (TextUtils.isEmpty(engCity)) {
                                    engCity = engAddress.getSubAdminArea();
                                }
                                engState = engAddress.getAdminArea();

                                if (!TextUtils.isEmpty(engState)) {
                                    cityLocation.geoAddress.state = engState;
                                    cityLocation.geoAddress.flags |= 1;
                                }
                                if (!TextUtils.isEmpty(engCity)) {
                                    cityLocation.geoAddress.city = engCity;
                                    cityLocation.geoAddress.flags |= 2;
                                }
                            }
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
                            countryLocation.icon = "country";
                            countryLocation.emoji = LocationController.countryCodeToEmoji(address.getCountryCode());
                            countries.add(countryLocation.title);
                            countryLocation.address = LocaleController.getString(R.string.Country);
                            if (engAddress != null) {
                                countryLocation.geoAddress = new TL_stories.TL_geoPointAddress();
                                countryLocation.geoAddress.country_iso2 = engAddress.getCountryCode();
                            }
                            locations.add(countryLocation);
                            if (locations.size() >= maxCount) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
            AndroidUtilities.runOnUIThread(() -> {
                if (id != searchId) {
                    return;
                }
                searchInProgress = false;
                lastFoundQuery = query;
                BaseLocationAdapter.this.locations.clear();
                BaseLocationAdapter.this.locations.addAll(locations);
                if (delegate != null) {
                    delegate.didLoadSearchResult(BaseLocationAdapter.this.locations);
                }
                update(true);
            });
        });
    }

    protected void update(boolean animated) {
        notifyDataSetChanged();
    }
}
