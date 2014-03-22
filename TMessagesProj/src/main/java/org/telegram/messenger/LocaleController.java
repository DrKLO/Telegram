/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import org.telegram.ui.ApplicationLoader;

import java.util.HashMap;
import java.util.Locale;

public class LocaleController {

    private String currentLanguage;
    private Locale currentLocale;
    private HashMap<String, String> localeValues = new HashMap<String, String>();

    private static volatile LocaleController Instance = null;
    public static LocaleController getInstance() {
        LocaleController localInstance = Instance;
        if (localInstance == null) {
            synchronized (LocaleController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new LocaleController();
                }
            }
        }
        return localInstance;
    }

    public LocaleController() {
        currentLocale = Locale.getDefault();
        currentLanguage = currentLocale.getLanguage();
    }

    public void applyLanguage(String language) {
        if (language != null) {
            currentLanguage = language;
            currentLocale = new Locale(currentLanguage);
        } else {
            currentLocale = Locale.getDefault();
            currentLanguage = currentLocale.getLanguage();
        }
    }

    private void loadCurrentLocale() {
        localeValues.clear();
    }

    public static String getString(String key, int res) {
        String value = getInstance().localeValues.get(key);
        if (value == null) {
            value = ApplicationLoader.applicationContext.getString(res);
        }
        return value;
    }

    public static String formatString(String key, int res, Object... args) {
        String value = getInstance().localeValues.get(key);
        if (value == null) {
            value = ApplicationLoader.applicationContext.getString(res);
        }
        try {
            if (getInstance().currentLocale != null) {
                return String.format(getInstance().currentLocale, value, args);
            } else {
                return String.format(value, args);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return "LOC_ERR: " + key;
        }
    }
}
