/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.volley.Request;
import org.telegram.messenger.volley.RequestQueue;
import org.telegram.messenger.volley.Response;
import org.telegram.messenger.volley.VolleyError;
import org.telegram.messenger.volley.toolbox.JsonObjectRequest;
import org.telegram.messenger.volley.toolbox.Volley;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class BaseLocationAdapter extends BaseFragmentAdapter {

    public interface BaseLocationAdapterDelegate {
        void didLoadedSearchResult(ArrayList<TLRPC.TL_messageMediaVenue> places);
    }

    private RequestQueue requestQueue;
    protected boolean searching;
    protected ArrayList<TLRPC.TL_messageMediaVenue> places = new ArrayList<>();
    protected ArrayList<String> iconUrls = new ArrayList<>();
    private Location lastSearchLocation;
    private BaseLocationAdapterDelegate delegate;
    private Timer searchTimer;

    public BaseLocationAdapter() {
        requestQueue = Volley.newRequestQueue(ApplicationLoader.applicationContext);
    }

    public void destroy() {
        if (requestQueue != null) {
            requestQueue.cancelAll("search");
            requestQueue.stop();
        }
    }

    public void setDelegate(BaseLocationAdapterDelegate delegate) {
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
                FileLog.e("tmessages", e);
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            lastSearchLocation = null;
                            searchGooglePlacesWithQuery(query, coordinate);
                        }
                    });
                }
            }, 200, 500);
        }
    }

    public void searchGooglePlacesWithQuery(final String query, final Location coordinate) {
        if (lastSearchLocation != null && coordinate.distanceTo(lastSearchLocation) < 200) {
            return;
        }
        lastSearchLocation = coordinate;
        if (searching) {
            searching = false;
            requestQueue.cancelAll("search");
        }
        try {
            searching = true;
            String url = String.format(Locale.US, "https://api.foursquare.com/v2/venues/search/?v=%s&locale=en&limit=25&client_id=%s&client_secret=%s&ll=%s", BuildVars.FOURSQUARE_API_VERSION, BuildVars.FOURSQUARE_API_ID, BuildVars.FOURSQUARE_API_KEY,  String.format(Locale.US, "%f,%f", coordinate.getLatitude(), coordinate.getLongitude()));
            if (query != null && query.length() > 0) {
                url += "&query=" + URLEncoder.encode(query, "UTF-8");
            }
            JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                places.clear();
                                iconUrls.clear();
                                /*
                                GOOGLE MAPS
                                JSONArray result = response.getJSONArray("results");

                                for (int a = 0; a < result.length(); a++) {
                                    try {
                                        JSONObject object = result.getJSONObject(a);
                                        JSONObject location = object.getJSONObject("geometry").getJSONObject("location");
                                        TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
                                        venue.geo = new TLRPC.TL_geoPoint();
                                        venue.geo.lat = location.getDouble("lat");
                                        venue.geo._long = location.getDouble("lng");
                                        if (object.has("vicinity")) {
                                            venue.address = object.getString("vicinity").trim();
                                        } else {
                                            venue.address = String.format(Locale.US, "%f,%f", venue.geo.lat, venue.geo._long);
                                        }
                                        if (object.has("name")) {
                                            venue.title = object.getString("name").trim();
                                        }
                                        venue.venue_id = object.getString("place_id");
                                        venue.provider = "google";
                                        places.add(venue);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                                 */
                                JSONArray result = response.getJSONObject("response").getJSONArray("venues");

                                for (int a = 0; a < result.length(); a++) {
                                    try {
                                        JSONObject object = result.getJSONObject(a);
                                        String iconUrl = null;
                                        if (object.has("categories")) {
                                            JSONArray categories = object.getJSONArray("categories");
                                            if (categories.length() > 0) {
                                                JSONObject category = categories.getJSONObject(0);
                                                if (category.has("icon")) {
                                                    JSONObject icon = category.getJSONObject("icon");
                                                    iconUrl = String.format(Locale.US, "%s64%s", icon.getString("prefix"), icon.getString("suffix"));
                                                }
                                            }
                                        }
                                        iconUrls.add(iconUrl);

                                        JSONObject location = object.getJSONObject("location");
                                        TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
                                        venue.geo = new TLRPC.TL_geoPoint();
                                        venue.geo.lat = location.getDouble("lat");
                                        venue.geo._long = location.getDouble("lng");
                                        if (location.has("address")) {
                                            venue.address = location.getString("address");
                                        } else if (location.has("city")) {
                                            venue.address = location.getString("city");
                                        } else if (location.has("state")) {
                                            venue.address = location.getString("state");
                                        } else if (location.has("country")) {
                                            venue.address = location.getString("country");
                                        } else {
                                            venue.address = String.format(Locale.US, "%f,%f", venue.geo.lat, venue.geo._long);
                                        }
                                        if (object.has("name")) {
                                            venue.title = object.getString("name");
                                        }
                                        venue.venue_id = object.getString("id");
                                        venue.provider = "foursquare";
                                        places.add(venue);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            searching = false;
                            notifyDataSetChanged();
                            if (delegate != null) {
                                delegate.didLoadedSearchResult(places);
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            FileLog.e("tmessages", "Error: " + error.getMessage());
                            searching = false;
                            notifyDataSetChanged();
                            if (delegate != null) {
                                delegate.didLoadedSearchResult(places);
                            }
                        }
                    });
            jsonObjReq.setShouldCache(false);
            jsonObjReq.setTag("search");
            requestQueue.add(jsonObjReq);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            searching = false;
            if (delegate != null) {
                delegate.didLoadedSearchResult(places);
            }
        }
        notifyDataSetChanged();
        }
}
