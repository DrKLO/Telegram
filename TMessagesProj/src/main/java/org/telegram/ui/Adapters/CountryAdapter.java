/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.LetterSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class CountryAdapter extends BaseSectionsAdapter {

    public static class Country {
        public String name;
        public String code;
        public String shortname;
    }

    private Context mContext;
    private HashMap<String, ArrayList<Country>> countries = new HashMap<>();
    private ArrayList<String> sortedCountries = new ArrayList<>();

    public CountryAdapter(Context context) {
        mContext = context;

        try {
            InputStream stream = ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                Country c = new Country();
                c.name = args[2];
                c.code = args[0];
                c.shortname = args[1];
                String n = c.name.substring(0, 1).toUpperCase();
                ArrayList<Country> arr = countries.get(n);
                if (arr == null) {
                    arr = new ArrayList<>();
                    countries.put(n, arr);
                    sortedCountries.add(n);
                }
                arr.add(c);
            }
            reader.close();
            stream.close();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        Collections.sort(sortedCountries, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareTo(rhs);
            }
        });

        for (ArrayList<Country> arr : countries.values()) {
            Collections.sort(arr, new Comparator<Country>() {
                @Override
                public int compare(Country country, Country country2) {
                    return country.name.compareTo(country2.name);
                }
            });
        }
    }

    public HashMap<String, ArrayList<Country>> getCountries() {
        return countries;
    }

    @Override
    public Country getItem(int section, int position) {
        if (section < 0 || section >= sortedCountries.size()) {
            return null;
        }
        ArrayList<Country> arr = countries.get(sortedCountries.get(section));
        if (position < 0 || position >= arr.size()) {
            return null;
        }
        return arr.get(position);
    }

    @Override
    public boolean isRowEnabled(int section, int row) {
        ArrayList<Country> arr = countries.get(sortedCountries.get(section));
        return row < arr.size();
    }

    @Override
    public int getSectionCount() {
        return sortedCountries.size();
    }

    @Override
    public int getCountForSection(int section) {
        int count = countries.get(sortedCountries.get(section)).size();
        if (section != sortedCountries.size() - 1) {
            count++;
        }
        return count;
    }

    @Override
    public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = new LetterSectionCell(mContext);
            ((LetterSectionCell) convertView).setCellHeight(AndroidUtilities.dp(48));
        }
        ((LetterSectionCell) convertView).setLetter(sortedCountries.get(section).toUpperCase());
        return convertView;
    }

    @Override
    public View getItemView(int section, int position, View convertView, ViewGroup parent) {
        int type = getItemViewType(section, position);
        if (type == 1) {
            if (convertView == null) {
                convertView = new DividerCell(mContext);
                convertView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 24 : 72), 0, AndroidUtilities.dp(LocaleController.isRTL ? 72 : 24), 0);
            }
        } else if (type == 0) {
            if (convertView == null) {
                convertView = new TextSettingsCell(mContext);
                convertView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 16 : 54), 0, AndroidUtilities.dp(LocaleController.isRTL ? 54 : 16), 0);
            }

            ArrayList<Country> arr = countries.get(sortedCountries.get(section));
            Country c = arr.get(position);
            ((TextSettingsCell) convertView).setTextAndValue(c.name, "+" + c.code, false);
        }
        return convertView;
    }

    @Override
    public int getItemViewType(int section, int position) {
        ArrayList<Country> arr = countries.get(sortedCountries.get(section));
        return position < arr.size() ? 0 : 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }
}
