package org.telegram.ui.web;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class RestrictedDomainsList {

    private static RestrictedDomainsList instance;
    public static RestrictedDomainsList getInstance() {
        if (instance == null) {
            instance = new RestrictedDomainsList();
        }
        return instance;
    }

    public final HashMap<String, Integer> openedDomains = new HashMap<>();
    public final HashSet<String> restrictedDomainsSet = new HashSet<>();
    public final ArrayList<ArrayList<String>> restrictedDomains = new ArrayList<>();

    private boolean loaded;
    public void load() {
        if (loaded) {
            return;
        }
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        try {
            JSONObject o = new JSONObject(prefs.getString("web_opened_domains", "{}"));
            Iterator<String> i = o.keys();
            while (i.hasNext()) {
                String key = i.next();
                openedDomains.put(key, o.getInt(key));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            JSONArray o = new JSONArray(prefs.getString("web_restricted_domains2", "[]"));
            for (int i = 0; i < o.length(); ++i) {
                final JSONArray array = o.getJSONArray(i);
                final ArrayList<String> domains = new ArrayList<>();
                for (int j = 0; j < array.length(); ++j) {
                    final String domain = array.getString(j);
                    restrictedDomainsSet.add(domain);
                    domains.add(domain);
                }
                restrictedDomains.add(domains);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        loaded = true;
    }

    public int incrementOpen(String domain) {
        load();
        Integer count = openedDomains.get(domain);
        if (count == null) count = 0;
        count++;
        openedDomains.put(domain, count);
        scheduleSave();
        return count;
    }

    public boolean isRestricted(String ...domains) {
        load();
        for (String domain : domains) {
            if (restrictedDomainsSet.contains(domain))
                return true;
        }
        return false;
    }

    public boolean isRestricted(String domain) {
        load();
        return restrictedDomainsSet.contains(domain);
    }

    public void setRestricted(boolean restricted, String ...domains) {
        load();
        int index = -1;
        for (int i = 0; i < restrictedDomains.size(); ++i) {
            for (int j = 0; j < domains.length; ++j) {
                if (domains[j] != null && restrictedDomains.get(i).contains(domains[j])) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) break;
        }
        final boolean wasRestricted = isRestricted(domains);
        if (restricted != wasRestricted) {
            if (restricted) {
                final ArrayList<String> newList = new ArrayList<>();
                for (int j = 0; j < domains.length; ++j) {
                    if (domains[j] != null) {
                        newList.add(domains[j]);
                    }
                }
                restrictedDomainsSet.addAll(newList);
                restrictedDomains.add(newList);
            } else {
                restrictedDomainsSet.removeAll(
                    restrictedDomains.remove(index)
                );
            }
            scheduleSave();
        }
    }

    public void scheduleSave() {
        AndroidUtilities.cancelRunOnUIThread(this::save);
        AndroidUtilities.runOnUIThread(this::save, 1_000);
    }

    public void save() {
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor edit = prefs.edit();
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Integer> e : openedDomains.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
            edit.putString("web_opened_domains", o.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            JSONArray o = new JSONArray();
            for (ArrayList<String> domains : restrictedDomains) {
                JSONArray array = new JSONArray();
                for (String domain : domains) {
                    array.put(domain);
                }
                o.put(array);
            }
            edit.putString("web_restricted_domains2", o.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
        edit.apply();
    }

}
