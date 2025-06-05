package org.telegram.ui.web;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.net.URLEncoder;
import java.util.ArrayList;

/*
 * You can add your own search engines of preference with extending custom langpack with such keys:
 * - SearchEngine6Name — name of your search engine
 * - SearchEngine6SearchURL — url of search, query would be put at the end of the url
 * - SearchEngine6AutocompleteURL — url of autocomplete, query would be put at the end of the url
 * - SearchEngine6PrivacyPolicyURL — privacy policy url to show in a dialog
 * */
public class SearchEngine {

    @NonNull
    public final String name;
    @Nullable
    public final String search_url;
    @Nullable
    public final String autocomplete_url;
    @Nullable
    public final String privacy_policy_url;

    public SearchEngine(
        @NonNull  String name,
        @Nullable String search_url,
        @Nullable String autocomplete_url,
        @Nullable String privacy_policy_url
    ) {
        this.name = name;
        this.search_url = search_url;
        this.autocomplete_url = autocomplete_url;
        this.privacy_policy_url = privacy_policy_url;
    }

    public String getSearchURL(String query) {
        if (search_url == null) return null;
        return search_url + URLEncoder.encode(query);
    }

    public String getAutocompleteURL(String query) {
        if (autocomplete_url == null) return null;
        return autocomplete_url + URLEncoder.encode(query);
    }

    public String getPrivacyPolicyURL() {
        return privacy_policy_url;
    }

    public ArrayList<String> extractSuggestions(String json) {
        final ArrayList<String> array = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            JSONArray suggs = arr.getJSONArray(1);
            for (int i = 0; i < suggs.length(); ++i) {
                array.add(suggs.getString(i));
            }
        } catch (Exception e) {
            FileLog.e(e, false);
            try {
                JSONObject obj = new JSONObject(json);
                JSONArray arr = obj.getJSONObject("gossip").getJSONArray("results");
                for (int i = 0; i < arr.length(); ++i) {
                    array.add(arr.getJSONObject(i).getString("key"));
                }
            } catch (Exception e2) {
                FileLog.e(e2, false);
                try {
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); ++i) {
                        String phrase = arr.getJSONObject(i).getString("phrase");
                        if (!TextUtils.isEmpty(phrase)) {
                            array.add(phrase);
                        }
                    }
                } catch (Exception e3) {
                    FileLog.e(e3, false);
                }
            }
        }
        return array;
    }

    private static ArrayList<SearchEngine> searchEngines;
    public static ArrayList<SearchEngine> getSearchEngines() {
        if (searchEngines == null) {
            searchEngines = new ArrayList<>();
            for (int i = 1; true; ++i) {
                final String name =               nullable(getString("SearchEngine" + i + "Name"));
                if (name == null) break;
                final String search_url =         nullable(getString("SearchEngine" + i + "SearchURL"));
                final String autocomplete_url =   nullable(getString("SearchEngine" + i + "AutocompleteURL"));
                final String privacy_policy_url = nullable(getString("SearchEngine" + i + "PrivacyPolicyURL"));
                searchEngines.add(new SearchEngine(name, search_url, autocomplete_url, privacy_policy_url));
            }
        }
        return searchEngines;
    }

    private static String nullable(String s) {
        if (s == null || s.startsWith("LOC_ERR") || "reserved".equals(s)) return null;
        return s;
    }

    public static SearchEngine getCurrent() {
        final ArrayList<SearchEngine> searchEngines = getSearchEngines();
        final int index = Utilities.clamp(SharedConfig.searchEngineType, searchEngines.size() - 1, 0);
        return searchEngines.get(index);
    }

}
