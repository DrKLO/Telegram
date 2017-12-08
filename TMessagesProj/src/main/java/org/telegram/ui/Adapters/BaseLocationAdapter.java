/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.location.Location;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Locale;
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
    private BaseLocationAdapterDelegate delegate;
    private Timer searchTimer;
    private AsyncTask<Void, Void, JSONObject> currentTask;

    public void destroy() {
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
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
            if (currentTask != null) {
                currentTask.cancel(true);
                currentTask = null;
            }
        }
        try {
            searching = true;
            final String url = String.format(Locale.US, "https://api.foursquare.com/v2/venues/search/?v=%s&locale=en&limit=25&client_id=%s&client_secret=%s&ll=%s%s", BuildVars.FOURSQUARE_API_VERSION, BuildVars.FOURSQUARE_API_ID, BuildVars.FOURSQUARE_API_KEY, String.format(Locale.US, "%f,%f", coordinate.getLatitude(), coordinate.getLongitude()), query != null && query.length() > 0 ? "&query=" + URLEncoder.encode(query, "UTF-8") : "");
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
                    FileLog.e(e);
                }
            }
             */
            currentTask = new AsyncTask<Void, Void, JSONObject>() {

                private boolean canRetry = true;

                private String downloadUrlContent(String url) {
                    boolean canRetry = true;
                    InputStream httpConnectionStream = null;
                    boolean done = false;
                    StringBuilder result = null;
                    URLConnection httpConnection = null;
                    try {
                        URL downloadUrl = new URL(url);
                        httpConnection = downloadUrl.openConnection();
                        httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
                        httpConnection.addRequestProperty("Accept-Language", "en-us,en;q=0.5");
                        httpConnection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        httpConnection.addRequestProperty("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
                        httpConnection.setConnectTimeout(5000);
                        httpConnection.setReadTimeout(5000);
                        if (httpConnection instanceof HttpURLConnection) {
                            HttpURLConnection httpURLConnection = (HttpURLConnection) httpConnection;
                            httpURLConnection.setInstanceFollowRedirects(true);
                            int status = httpURLConnection.getResponseCode();
                            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                                String newUrl = httpURLConnection.getHeaderField("Location");
                                String cookies = httpURLConnection.getHeaderField("Set-Cookie");
                                downloadUrl = new URL(newUrl);
                                httpConnection = downloadUrl.openConnection();
                                httpConnection.setRequestProperty("Cookie", cookies);
                                httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
                                httpConnection.addRequestProperty("Accept-Language", "en-us,en;q=0.5");
                                httpConnection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                                httpConnection.addRequestProperty("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
                            }
                        }
                        httpConnection.connect();
                        httpConnectionStream = httpConnection.getInputStream();
                    } catch (Throwable e) {
                        if (e instanceof SocketTimeoutException) {
                            if (ConnectionsManager.isNetworkOnline()) {
                                canRetry = false;
                            }
                        } else if (e instanceof UnknownHostException) {
                            canRetry = false;
                        } else if (e instanceof SocketException) {
                            if (e.getMessage() != null && e.getMessage().contains("ECONNRESET")) {
                                canRetry = false;
                            }
                        } else if (e instanceof FileNotFoundException) {
                            canRetry = false;
                        }
                        FileLog.e(e);
                    }

                    if (canRetry) {
                        try {
                            if (httpConnection != null && httpConnection instanceof HttpURLConnection) {
                                int code = ((HttpURLConnection) httpConnection).getResponseCode();
                                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                                    //canRetry = false;
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }

                        if (httpConnectionStream != null) {
                            try {
                                byte[] data = new byte[1024 * 32];
                                while (true) {
                                    if (isCancelled()) {
                                        break;
                                    }
                                    try {
                                        int read = httpConnectionStream.read(data);
                                        if (read > 0) {
                                            if (result == null) {
                                                result = new StringBuilder();
                                            }
                                            result.append(new String(data, 0, read, "UTF-8"));
                                        } else if (read == -1) {
                                            done = true;
                                            break;
                                        } else {
                                            break;
                                        }
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                        break;
                                    }
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        }

                        try {
                            if (httpConnectionStream != null) {
                                httpConnectionStream.close();
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }
                    return done ? result.toString() : null;
                }

                protected JSONObject doInBackground(Void... voids) {
                    String code = downloadUrlContent(url);
                    if (isCancelled()) {
                        return null;
                    }
                    try {
                        return new JSONObject(code);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(JSONObject response) {
                    if (response != null) {
                        try {
                            places.clear();
                            iconUrls.clear();

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
                                    venue.venue_type = "";
                                    venue.venue_id = object.getString("id");
                                    venue.provider = "foursquare";
                                    places.add(venue);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        searching = false;
                        notifyDataSetChanged();
                        if (delegate != null) {
                            delegate.didLoadedSearchResult(places);
                        }
                    } else {
                        searching = false;
                        notifyDataSetChanged();
                        if (delegate != null) {
                            delegate.didLoadedSearchResult(places);
                        }
                    }
                }
            };
            currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
        } catch (Exception e) {
            FileLog.e(e);
            searching = false;
            if (delegate != null) {
                delegate.didLoadedSearchResult(places);
            }
        }
        notifyDataSetChanged();
    }
}
