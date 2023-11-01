/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Xml;

import androidx.annotation.StringRes;

import org.telegram.messenger.time.FastDateFormat;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.RestrictedLanguagesSelectActivity;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

public class LocaleController {

    static final int QUANTITY_OTHER = 0x0000;
    static final int QUANTITY_ZERO = 0x0001;
    static final int QUANTITY_ONE = 0x0002;
    static final int QUANTITY_TWO = 0x0004;
    static final int QUANTITY_FEW = 0x0008;
    static final int QUANTITY_MANY = 0x0010;

    public static boolean isRTL = false;
    public static int nameDisplayOrder = 1;
    public static boolean is24HourFormat = false;
    public FastDateFormat formatterDay;
    public FastDateFormat formatterWeek;
    public FastDateFormat formatterWeekLong;
    public FastDateFormat formatterDayMonth;
    public FastDateFormat formatterYear;
    public FastDateFormat formatterYearMax;
    public FastDateFormat formatterStats;
    public FastDateFormat formatterBannedUntil;
    public FastDateFormat formatterBannedUntilThisYear;
    public FastDateFormat chatDate;
    public FastDateFormat chatFullDate;
    public FastDateFormat formatterScheduleDay;
    public FastDateFormat formatterScheduleYear;
    public FastDateFormat formatterMonthYear;
    public FastDateFormat formatterGiveawayCard;
    public FastDateFormat formatterBoostExpired;
    public FastDateFormat formatterGiveawayMonthDay;
    public FastDateFormat formatterGiveawayMonthDayYear;
    public FastDateFormat[] formatterScheduleSend = new FastDateFormat[15];

    private static HashMap<Integer, String> resourcesCacheMap = new HashMap<>();

    private HashMap<String, PluralRules> allRules = new HashMap<>();

    private Locale currentLocale;
    private Locale systemDefaultLocale;
    private PluralRules currentPluralRules;
    private LocaleInfo currentLocaleInfo;
    private HashMap<String, String> localeValues = new HashMap<>();
    private String languageOverride;
    private boolean changingConfiguration = false;
    private boolean reloadLastFile;

    private String currentSystemLocale;

    private HashMap<String, String> currencyValues;
    private HashMap<String, String> translitChars;
    private HashMap<String, String> ruTranslitChars;

    private class TimeZoneChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ApplicationLoader.applicationHandler.post(() -> {
                if (!formatterDayMonth.getTimeZone().equals(TimeZone.getDefault())) {
                    LocaleController.getInstance().recreateFormatters();
                }
            });
        }
    }

    public static class LocaleInfo {

        public String name;
        public String nameEnglish;
        public String shortName;
        public String pathToFile;
        public String baseLangCode;
        public String pluralLangCode;
        public boolean isRtl;
        public int version;
        public int baseVersion;
        public boolean builtIn;
        public int serverIndex;

        public String getSaveString() {
            String langCode = baseLangCode == null ? "" : baseLangCode;
            String pluralCode = TextUtils.isEmpty(pluralLangCode) ? shortName : pluralLangCode;
            return name + "|" + nameEnglish + "|" + shortName + "|" + pathToFile + "|" + version + "|" + langCode + "|" + pluralLangCode + "|" + (isRtl ? 1 : 0) + "|" + baseVersion + "|" + serverIndex;
        }

        public static LocaleInfo createWithString(String string) {
            if (string == null || string.length() == 0) {
                return null;
            }
            String[] args = string.split("\\|");
            LocaleInfo localeInfo = null;
            if (args.length >= 4) {
                localeInfo = new LocaleInfo();
                localeInfo.name = args[0];
                localeInfo.nameEnglish = args[1];
                localeInfo.shortName = args[2].toLowerCase();
                localeInfo.pathToFile = args[3];
                if (args.length >= 5) {
                    localeInfo.version = Utilities.parseInt(args[4]);
                }
                localeInfo.baseLangCode = args.length >= 6 ? args[5] : "";
                localeInfo.pluralLangCode = args.length >= 7 ? args[6] : localeInfo.shortName;
                if (args.length >= 8) {
                    localeInfo.isRtl = Utilities.parseInt(args[7]) == 1;
                }
                if (args.length >= 9) {
                    localeInfo.baseVersion = Utilities.parseInt(args[8]);
                }
                if (args.length >= 10) {
                    localeInfo.serverIndex = Utilities.parseInt(args[9]);
                } else {
                    localeInfo.serverIndex = Integer.MAX_VALUE;
                }
                if (!TextUtils.isEmpty(localeInfo.baseLangCode)) {
                    localeInfo.baseLangCode = localeInfo.baseLangCode.replace("-", "_");
                }
            }
            return localeInfo;
        }

        public File getPathToFile() {
            if (isRemote()) {
                return new File(ApplicationLoader.getFilesDirFixed(), "remote_" + shortName + ".xml");
            } else if (isUnofficial()) {
                return new File(ApplicationLoader.getFilesDirFixed(), "unofficial_" + shortName + ".xml");
            }
            return !TextUtils.isEmpty(pathToFile) ? new File(pathToFile) : null;
        }

        public File getPathToBaseFile() {
            if (isUnofficial()) {
                return new File(ApplicationLoader.getFilesDirFixed(), "unofficial_base_" + shortName + ".xml");
            }
            return null;
        }

        public String getKey() {
            if (pathToFile != null && !isRemote() && !isUnofficial()) {
                return "local_" + shortName;
            } else if (isUnofficial()) {
                return "unofficial_" + shortName;
            }
            return shortName;
        }

        public boolean hasBaseLang() {
            return isUnofficial() && !TextUtils.isEmpty(baseLangCode) && !baseLangCode.equals(shortName);
        }

        public boolean isRemote() {
            return "remote".equals(pathToFile);
        }

        public boolean isUnofficial() {
            return "unofficial".equals(pathToFile);
        }

        public boolean isLocal() {
            return !TextUtils.isEmpty(pathToFile) && !isRemote() && !isUnofficial();
        }

        public boolean isBuiltIn() {
            return builtIn;
        }

        public String getLangCode() {
            return shortName.replace("_", "-");
        }

        public String getBaseLangCode() {
            return baseLangCode == null ? "" : baseLangCode.replace("_", "-");
        }
    }

    private boolean loadingRemoteLanguages;

    public ArrayList<LocaleInfo> languages = new ArrayList<>();
    public ArrayList<LocaleInfo> unofficialLanguages = new ArrayList<>();
    public ArrayList<LocaleInfo> remoteLanguages = new ArrayList<>();
    public HashMap<String, LocaleInfo> remoteLanguagesDict = new HashMap<>();
    public HashMap<String, LocaleInfo> languagesDict = new HashMap<>();

    private ArrayList<LocaleInfo> otherLanguages = new ArrayList<>();

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
        addRules(new String[]{"bem", "brx", "da", "de", "el", "en", "eo", "es", "et", "fi", "fo", "gl", "he", "iw", "it", "nb",
                "nl", "nn", "no", "sv", "af", "bg", "bn", "ca", "eu", "fur", "fy", "gu", "ha", "is", "ku",
                "lb", "ml", "mr", "nah", "ne", "om", "or", "pa", "pap", "ps", "so", "sq", "sw", "ta", "te",
                "tk", "ur", "zu", "mn", "gsw", "chr", "rm", "pt", "an", "ast"}, new PluralRules_One());
        addRules(new String[]{"cs", "sk"}, new PluralRules_Czech());
        addRules(new String[]{"ff", "fr", "kab"}, new PluralRules_French());
        addRules(new String[]{"ru", "uk", "be"}, new PluralRules_Balkan());
        addRules(new String[]{"sr", "hr", "bs", "sh"}, new PluralRules_Serbian());
        addRules(new String[]{"lv"}, new PluralRules_Latvian());
        addRules(new String[]{"lt"}, new PluralRules_Lithuanian());
        addRules(new String[]{"pl"}, new PluralRules_Polish());
        addRules(new String[]{"ro", "mo"}, new PluralRules_Romanian());
        addRules(new String[]{"sl"}, new PluralRules_Slovenian());
        addRules(new String[]{"ar"}, new PluralRules_Arabic());
        addRules(new String[]{"mk"}, new PluralRules_Macedonian());
        addRules(new String[]{"cy"}, new PluralRules_Welsh());
        addRules(new String[]{"br"}, new PluralRules_Breton());
        addRules(new String[]{"lag"}, new PluralRules_Langi());
        addRules(new String[]{"shi"}, new PluralRules_Tachelhit());
        addRules(new String[]{"mt"}, new PluralRules_Maltese());
        addRules(new String[]{"ga", "se", "sma", "smi", "smj", "smn", "sms"}, new PluralRules_Two());
        addRules(new String[]{"ak", "am", "bh", "fil", "tl", "guw", "hi", "ln", "mg", "nso", "ti", "wa"}, new PluralRules_Zero());
        addRules(new String[]{"az", "bm", "fa", "ig", "hu", "ja", "kde", "kea", "ko", "my", "ses", "sg", "to",
                "tr", "vi", "wo", "yo", "zh", "bo", "dz", "id", "jv", "jw", "ka", "km", "kn", "ms", "th", "in"}, new PluralRules_None());

        LocaleInfo localeInfo = new LocaleInfo();
        localeInfo.name = "English";
        localeInfo.nameEnglish = "English";
        localeInfo.shortName = localeInfo.pluralLangCode = "en";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Italiano";
        localeInfo.nameEnglish = "Italian";
        localeInfo.shortName = localeInfo.pluralLangCode = "it";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Español";
        localeInfo.nameEnglish = "Spanish";
        localeInfo.shortName = localeInfo.pluralLangCode = "es";
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Deutsch";
        localeInfo.nameEnglish = "German";
        localeInfo.shortName = localeInfo.pluralLangCode = "de";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Nederlands";
        localeInfo.nameEnglish = "Dutch";
        localeInfo.shortName = localeInfo.pluralLangCode = "nl";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "العربية";
        localeInfo.nameEnglish = "Arabic";
        localeInfo.shortName = localeInfo.pluralLangCode = "ar";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        localeInfo.isRtl = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Português (Brasil)";
        localeInfo.nameEnglish = "Portuguese (Brazil)";
        localeInfo.shortName = localeInfo.pluralLangCode = "pt_br";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "한국어";
        localeInfo.nameEnglish = "Korean";
        localeInfo.shortName = localeInfo.pluralLangCode = "ko";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        loadOtherLanguages();
        if (remoteLanguages.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> loadRemoteLanguages(UserConfig.selectedAccount));
        }

        for (int a = 0; a < otherLanguages.size(); a++) {
            LocaleInfo locale = otherLanguages.get(a);
            languages.add(locale);
            languagesDict.put(locale.getKey(), locale);
        }

        for (int a = 0; a < remoteLanguages.size(); a++) {
            LocaleInfo locale = remoteLanguages.get(a);
            LocaleInfo existingLocale = getLanguageFromDict(locale.getKey());
            if (existingLocale != null) {
                existingLocale.pathToFile = locale.pathToFile;
                existingLocale.version = locale.version;
                existingLocale.baseVersion = locale.baseVersion;
                existingLocale.serverIndex = locale.serverIndex;
                remoteLanguages.set(a, existingLocale);
            } else {
                languages.add(locale);
                languagesDict.put(locale.getKey(), locale);
            }
        }

        for (int a = 0; a < unofficialLanguages.size(); a++) {
            LocaleInfo locale = unofficialLanguages.get(a);
            LocaleInfo existingLocale = getLanguageFromDict(locale.getKey());
            if (existingLocale != null) {
                existingLocale.pathToFile = locale.pathToFile;
                existingLocale.version = locale.version;
                existingLocale.baseVersion = locale.baseVersion;
                existingLocale.serverIndex = locale.serverIndex;
                unofficialLanguages.set(a, existingLocale);
            } else {
                languagesDict.put(locale.getKey(), locale);
            }
        }

        systemDefaultLocale = Locale.getDefault();
        is24HourFormat = DateFormat.is24HourFormat(ApplicationLoader.applicationContext);
        LocaleInfo currentInfo = null;
        boolean override = false;

        try {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            String lang = preferences.getString("language", null);
            if (lang != null) {
                currentInfo = getLanguageFromDict(lang);
                if (currentInfo != null) {
                    override = true;
                }
            }

            if (currentInfo == null && systemDefaultLocale.getLanguage() != null) {
                currentInfo = getLanguageFromDict(systemDefaultLocale.getLanguage());
            }
            if (currentInfo == null) {
                currentInfo = getLanguageFromDict(getLocaleString(systemDefaultLocale));
                if (currentInfo == null) {
                    currentInfo = getLanguageFromDict("en");
                }
            }

            applyLanguage(currentInfo, override, true, UserConfig.selectedAccount);
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            IntentFilter timezoneFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ApplicationLoader.applicationContext.registerReceiver(new TimeZoneChangedReceiver(), timezoneFilter);
        } catch (Exception e) {
            FileLog.e(e);
        }

        AndroidUtilities.runOnUIThread(() -> currentSystemLocale = getSystemLocaleStringIso639());
    }

    public static String getLanguageFlag(String countryCode) {
        if (countryCode.length() != 2 || countryCode.equals("YL")) return null;

        if (countryCode.equals("FT")) {
            return "\uD83C\uDFF4\u200D\u2620\uFE0F";
        } else if (countryCode.equals("XG")) {
            return "\uD83D\uDEF0";
        } else if (countryCode.equals("XV")){
            return "\uD83C\uDF0D";
        }

        int base = 0x1F1A5;
        char[] chars = countryCode.toCharArray();
        char[] emoji = {
                CharacterCompat.highSurrogate(base),
                CharacterCompat.lowSurrogate(base + chars[0]),
                CharacterCompat.highSurrogate(base),
                CharacterCompat.lowSurrogate(base + chars[1])
        };
        return new String(emoji);
    }

    public LocaleInfo getLanguageFromDict(String key) {
        if (key == null) {
            return null;
        }
        return languagesDict.get(key.toLowerCase().replace("-", "_"));
    }
    public LocaleInfo getBuiltinLanguageByPlural(String plural) {
        Collection<LocaleInfo> values = languagesDict.values();
        for (LocaleInfo l : values)
            if (l.pathToFile != null && l.pathToFile.equals("remote") && (l.shortName == null || !l.shortName.endsWith("_raw")) && l.pluralLangCode != null && l.pluralLangCode.equals(plural))
                return l;
        return null;
    }

    private void addRules(String[] languages, PluralRules rules) {
        for (String language : languages) {
            allRules.put(language, rules);
        }
    }

    private String stringForQuantity(int quantity) {
        switch (quantity) {
            case QUANTITY_ZERO:
                return "zero";
            case QUANTITY_ONE:
                return "one";
            case QUANTITY_TWO:
                return "two";
            case QUANTITY_FEW:
                return "few";
            case QUANTITY_MANY:
                return "many";
            default:
                return "other";
        }
    }

    public Locale getSystemDefaultLocale() {
        return systemDefaultLocale;
    }

    public boolean isCurrentLocalLocale() {
        return currentLocaleInfo.isLocal();
    }

    public void reloadCurrentRemoteLocale(int currentAccount, String langCode, boolean force, Runnable onDone) {
        if (langCode != null) {
            langCode = langCode.replace("-", "_");
        }
        if (langCode == null || currentLocaleInfo != null && (langCode.equals(currentLocaleInfo.shortName) || langCode.equals(currentLocaleInfo.baseLangCode))) {
            applyRemoteLanguage(currentLocaleInfo, langCode, force, currentAccount, onDone);
        }
    }

    private boolean checkingUpdateForCurrentRemoteLocale;

    public void checkUpdateForCurrentRemoteLocale(int currentAccount, int version, int baseVersion) {
        if (currentLocaleInfo == null || !currentLocaleInfo.isRemote() && !currentLocaleInfo.isUnofficial()) {
            return;
        }
        if (currentLocaleInfo.hasBaseLang()) {
            if (currentLocaleInfo.baseVersion < baseVersion) {
                checkingUpdateForCurrentRemoteLocale = true;
                applyRemoteLanguage(currentLocaleInfo, currentLocaleInfo.baseLangCode, false, currentAccount, () -> {
                    checkingUpdateForCurrentRemoteLocale = false;
                    checkPatchLangpack(currentAccount);
                });
            }
        }
        if (currentLocaleInfo.version < version) {
            checkingUpdateForCurrentRemoteLocale = true;
            applyRemoteLanguage(currentLocaleInfo, currentLocaleInfo.shortName, false, currentAccount, () -> {
                checkingUpdateForCurrentRemoteLocale = false;
                checkPatchLangpack(currentAccount);
            });
        }
    }

    public int calculateTranslatedCount(HashMap<String, String> map) {
        int count = 0;
        HashSet<String> added = new HashSet<>();
        for (String k : map.keySet()) {
            if (k == null) {
                continue;
            }
            String real = null;
            if (k.endsWith("_other")) {
                real = k.substring(0, k.length() - 6);
            } else if (k.endsWith("_zero") || k.endsWith("_many")) {
                real = k.substring(0, k.length() - 5);
            } else if (k.endsWith("_one") || k.endsWith("_two") || k.endsWith("_few")) {
                real = k.substring(0, k.length() - 4);
            }
            if (real == null) {
                count++;
            } else if (!added.contains(real)) {
                added.add(real);
                count++;
            }
        }
        added.clear();
        return count;
    }

    public void checkPatchLangpack(int currentAccount) {
        if (currentLocaleInfo == null || checkingUpdateForCurrentRemoteLocale) {
            return;
        }
        if (shouldReinstallLangpack(currentLocaleInfo.shortName)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("reload locale because locale file is not enough");
            }
            AndroidUtilities.runOnUIThread(() -> reloadCurrentRemoteLocale(currentAccount, null, true, null));
        }
    }

    private boolean patching = false;

    public void checkForcePatchLangpack(int currentAccount, Runnable ifDone) {
        String lng = LocaleController.getCurrentLanguageName();
        boolean shouldPatch = MessagesController.getInstance(currentAccount).checkResetLangpack > 0 && !MessagesController.getGlobalMainSettings().getBoolean("langpack_patched" + lng, false) && !patching;
        if (shouldPatch) {
            patching = true;
            reloadCurrentRemoteLocale(currentAccount, null, true, () -> AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getGlobalMainSettings().edit().putBoolean("langpack_patched" + lng, true).apply();
                if (ifDone != null) {
                    ifDone.run();
                }
                patching = false;
            }));
        }
    }

    private String getLocaleString(Locale locale) {
        if (locale == null) {
            return "en";
        }
        String languageCode = locale.getLanguage();
        String countryCode = locale.getCountry();
        String variantCode = locale.getVariant();
        if (languageCode.length() == 0 && countryCode.length() == 0) {
            return "en";
        }
        StringBuilder result = new StringBuilder(11);
        result.append(languageCode);
        if (countryCode.length() > 0 || variantCode.length() > 0) {
            result.append('_');
        }
        result.append(countryCode);
        if (variantCode.length() > 0) {
            result.append('_');
        }
        result.append(variantCode);
        return result.toString();
    }

    public static String getSystemLocaleStringIso639() {
        Locale locale = getInstance().getSystemDefaultLocale();
        if (locale == null) {
            return "en";
        }
        String languageCode = locale.getLanguage();
        String countryCode = locale.getCountry();
        String variantCode = locale.getVariant();
        if (languageCode.length() == 0 && countryCode.length() == 0) {
            return "en";
        }
        StringBuilder result = new StringBuilder(11);
        result.append(languageCode);
        if (countryCode.length() > 0 || variantCode.length() > 0) {
            result.append('-');
        }
        result.append(countryCode);
        if (variantCode.length() > 0) {
            result.append('_');
        }
        result.append(variantCode);
        return result.toString();
    }

    public static String getLocaleStringIso639() {
        LocaleInfo info = getInstance().currentLocaleInfo;
        if (info != null) {
            return info.getLangCode();
        }
        Locale locale = getInstance().currentLocale;
        if (locale == null) {
            return "en";
        }
        String languageCode = locale.getLanguage();
        String countryCode = locale.getCountry();
        String variantCode = locale.getVariant();
        if (languageCode.length() == 0 && countryCode.length() == 0) {
            return "en";
        }
        StringBuilder result = new StringBuilder(11);
        result.append(languageCode);
        if (countryCode.length() > 0 || variantCode.length() > 0) {
            result.append('-');
        }
        result.append(countryCode);
        if (variantCode.length() > 0) {
            result.append('_');
        }
        result.append(variantCode);
        return result.toString();
    }

    public static String getLocaleAlias(String code) {
        if (code == null) {
            return null;
        }
        switch (code) {
            case "in":
                return "id";
            case "iw":
                return "he";
            case "jw":
                return "jv";
            case "no":
                return "nb";
            case "tl":
                return "fil";
            case "ji":
                return "yi";
            case "id":
                return "in";
            case "he":
                return "iw";
            case "jv":
                return "jw";
            case "nb":
                return "no";
            case "fil":
                return "tl";
            case "yi":
                return "ji";
        }

        return null;
    }

    public boolean applyLanguageFile(File file, int currentAccount) {
        try {
            HashMap<String, String> stringMap = getLocaleFileStrings(file);

            String languageName = stringMap.get("LanguageName");
            String languageNameInEnglish = stringMap.get("LanguageNameInEnglish");
            String languageCode = stringMap.get("LanguageCode");

            if (languageName != null && languageName.length() > 0 &&
                    languageNameInEnglish != null && languageNameInEnglish.length() > 0 &&
                    languageCode != null && languageCode.length() > 0) {

                if (languageName.contains("&") || languageName.contains("|")) {
                    return false;
                }
                if (languageNameInEnglish.contains("&") || languageNameInEnglish.contains("|")) {
                    return false;
                }
                if (languageCode.contains("&") || languageCode.contains("|") || languageCode.contains("/") || languageCode.contains("\\")) {
                    return false;
                }

                File finalFile = new File(ApplicationLoader.getFilesDirFixed(), languageCode + ".xml");
                if (!AndroidUtilities.copyFile(file, finalFile)) {
                    return false;
                }

                String key = "local_" + languageCode.toLowerCase();
                LocaleInfo localeInfo = getLanguageFromDict(key);
                if (localeInfo == null) {
                    localeInfo = new LocaleInfo();
                    localeInfo.name = languageName;
                    localeInfo.nameEnglish = languageNameInEnglish;
                    localeInfo.shortName = languageCode.toLowerCase();
                    localeInfo.pluralLangCode = localeInfo.shortName;

                    localeInfo.pathToFile = finalFile.getAbsolutePath();
                    languages.add(localeInfo);
                    languagesDict.put(localeInfo.getKey(), localeInfo);
                    otherLanguages.add(localeInfo);

                    saveOtherLanguages();
                }
                localeValues = stringMap;
                applyLanguage(localeInfo, true, false, true, false, currentAccount, null);
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private void saveOtherLanguages() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("langconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        StringBuilder stringBuilder = new StringBuilder();
        for (int a = 0; a < otherLanguages.size(); a++) {
            LocaleInfo localeInfo = otherLanguages.get(a);
            String loc = localeInfo.getSaveString();
            if (loc != null) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append("&");
                }
                stringBuilder.append(loc);
            }
        }
        editor.putString("locales", stringBuilder.toString());
        stringBuilder.setLength(0);
        for (int a = 0; a < remoteLanguages.size(); a++) {
            LocaleInfo localeInfo = remoteLanguages.get(a);
            String loc = localeInfo.getSaveString();
            if (loc != null) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append("&");
                }
                stringBuilder.append(loc);
            }
        }
        editor.putString("remote", stringBuilder.toString());
        stringBuilder.setLength(0);
        for (int a = 0; a < unofficialLanguages.size(); a++) {
            LocaleInfo localeInfo = unofficialLanguages.get(a);
            String loc = localeInfo.getSaveString();
            if (loc != null) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append("&");
                }
                stringBuilder.append(loc);
            }
        }
        editor.putString("unofficial", stringBuilder.toString());
        editor.commit();
    }

    public boolean deleteLanguage(LocaleInfo localeInfo, int currentAccount) {
        if (localeInfo.pathToFile == null || localeInfo.isRemote() && localeInfo.serverIndex != Integer.MAX_VALUE) {
            return false;
        }
        if (currentLocaleInfo == localeInfo) {
            LocaleInfo info = null;
            if (systemDefaultLocale.getLanguage() != null) {
                info = getLanguageFromDict(systemDefaultLocale.getLanguage());
            }
            if (info == null) {
                info = getLanguageFromDict(getLocaleString(systemDefaultLocale));
            }
            if (info == null) {
                info = getLanguageFromDict("en");
            }
            applyLanguage(info, true, false, currentAccount);
        }

        unofficialLanguages.remove(localeInfo);
        remoteLanguages.remove(localeInfo);
        remoteLanguagesDict.remove(localeInfo.getKey());
        otherLanguages.remove(localeInfo);
        languages.remove(localeInfo);
        languagesDict.remove(localeInfo.getKey());
        File file = new File(localeInfo.pathToFile);
        file.delete();
        saveOtherLanguages();
        return true;
    }

    private void loadOtherLanguages() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("langconfig", Activity.MODE_PRIVATE);
        String locales = preferences.getString("locales", null);
        if (!TextUtils.isEmpty(locales)) {
            String[] localesArr = locales.split("&");
            for (String locale : localesArr) {
                LocaleInfo localeInfo = LocaleInfo.createWithString(locale);
                if (localeInfo != null) {
                    otherLanguages.add(localeInfo);
                }
            }
        }
        locales = preferences.getString("remote", null);
        if (!TextUtils.isEmpty(locales)) {
            String[] localesArr = locales.split("&");
            for (String locale : localesArr) {
                LocaleInfo localeInfo = LocaleInfo.createWithString(locale);
                localeInfo.shortName = localeInfo.shortName.replace("-", "_");
                if (remoteLanguagesDict.containsKey(localeInfo.getKey())) {
                    continue;
                }
                remoteLanguages.add(localeInfo);
                remoteLanguagesDict.put(localeInfo.getKey(), localeInfo);
            }
        }
        locales = preferences.getString("unofficial", null);
        if (!TextUtils.isEmpty(locales)) {
            String[] localesArr = locales.split("&");
            for (String locale : localesArr) {
                LocaleInfo localeInfo = LocaleInfo.createWithString(locale);
                if (localeInfo == null) {
                    continue;
                }
                localeInfo.shortName = localeInfo.shortName.replace("-", "_");
                unofficialLanguages.add(localeInfo);
            }
        }
    }

    private HashMap<String, String> getLocaleFileStrings(File file) {
        return getLocaleFileStrings(file, false);
    }

    private HashMap<String, String> getLocaleFileStrings(File file, boolean preserveEscapes) {
        FileInputStream stream = null;
        reloadLastFile = false;
        try {
            if (!file.exists()) {
                return new HashMap<>();
            }
            HashMap<String, String> stringMap = new HashMap<>();
            XmlPullParser parser = Xml.newPullParser();
            //AndroidUtilities.copyFile(file, new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "locale10.xml"));
            stream = new FileInputStream(file);
            parser.setInput(stream, "UTF-8");
            int eventType = parser.getEventType();
            String name = null;
            String value = null;
            String attrName = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    name = parser.getName();
                    int c = parser.getAttributeCount();
                    if (c > 0) {
                        attrName = parser.getAttributeValue(0);
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (attrName != null) {
                        value = parser.getText();
                        if (value != null) {
                            value = value.trim();
                            if (preserveEscapes) {
                                value = value.replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace("& ", "&amp; ");
                            } else {
                                value = value.replace("\\n", "\n");
                                value = value.replace("\\", "");
                                String old = value;
                                value = value.replace("&lt;", "<");
                                if (!reloadLastFile && !value.equals(old)) {
                                    reloadLastFile = true;
                                }
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    value = null;
                    attrName = null;
                    name = null;
                }
                if (name != null && name.equals("string") && value != null && attrName != null && value.length() != 0 && attrName.length() != 0) {
                    stringMap.put(attrName, value);
                    name = null;
                    value = null;
                    attrName = null;
                }
                eventType = parser.next();
            }
            return stringMap;
        } catch (Exception e) {
            FileLog.e(e);
            reloadLastFile = true;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return new HashMap<>();
    }

    public int applyLanguage(LocaleInfo localeInfo, boolean override, boolean init, final int currentAccount) {
        return applyLanguage(localeInfo, override, init, false, false, currentAccount, null);
    }

    public int applyLanguage(final LocaleInfo localeInfo, boolean override, boolean init, boolean fromFile, boolean force, final int currentAccount, Runnable onDone) {
        if (localeInfo == null) {
            return 0;
        }
        int requestId = 0;
        boolean hasBase = localeInfo.hasBaseLang();
        File pathToFile = localeInfo.getPathToFile();
        File pathToBaseFile = localeInfo.getPathToBaseFile();
        String shortName = localeInfo.shortName;
        if (!init) {
            ConnectionsManager.setLangCode(localeInfo.getLangCode());
        }
        LocaleInfo existingInfo = getLanguageFromDict(localeInfo.getKey());
        if (existingInfo == null) {
            if (localeInfo.isRemote()) {
                remoteLanguages.add(localeInfo);
                remoteLanguagesDict.put(localeInfo.getKey(), localeInfo);
                languages.add(localeInfo);
                languagesDict.put(localeInfo.getKey(), localeInfo);
                saveOtherLanguages();
            } else if (localeInfo.isUnofficial()) {
                unofficialLanguages.add(localeInfo);
                languagesDict.put(localeInfo.getKey(), localeInfo);
                saveOtherLanguages();
            }
        }
        boolean isLoadingRemote = false;
        if ((localeInfo.isRemote() || localeInfo.isUnofficial()) && (force || !pathToFile.exists() || hasBase && !pathToBaseFile.exists())) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("reload locale because one of file doesn't exist " + pathToFile + " " + pathToBaseFile);
            }
            isLoadingRemote = true;
            if (init) {
                AndroidUtilities.runOnUIThread(() -> applyRemoteLanguage(localeInfo, null, true, currentAccount, onDone));
            } else {
                requestId = applyRemoteLanguage(localeInfo, null, true, currentAccount, onDone);
            }
        }
        try {
            Locale newLocale;
            String[] args;
            if (!TextUtils.isEmpty(localeInfo.pluralLangCode)) {
                args = localeInfo.pluralLangCode.split("_");
            } else if (!TextUtils.isEmpty(localeInfo.baseLangCode)) {
                args = localeInfo.baseLangCode.split("_");
            } else {
                args = localeInfo.shortName.split("_");
            }
            if (args.length == 1) {
                newLocale = new Locale(args[0]);
            } else {
                newLocale = new Locale(args[0], args[1]);
            }
            if (override) {
                languageOverride = localeInfo.shortName;

                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("language", localeInfo.getKey());
                editor.commit();
            }
            if (pathToFile == null) {
                localeValues.clear();
            } else if (!fromFile) {
                localeValues = getLocaleFileStrings(hasBase ? localeInfo.getPathToBaseFile() : localeInfo.getPathToFile());
                if (hasBase) {
                    localeValues.putAll(getLocaleFileStrings(localeInfo.getPathToFile()));
                }
            }
            currentLocale = newLocale;
            currentLocaleInfo = localeInfo;
            FileLog.d("applyLanguage: currentLocaleInfo is set");

            if (!TextUtils.isEmpty(currentLocaleInfo.pluralLangCode)) {
                currentPluralRules = allRules.get(currentLocaleInfo.pluralLangCode);
            }
            if (currentPluralRules == null) {
                currentPluralRules = allRules.get(args[0]);
            }
            if (currentPluralRules == null) {
                currentPluralRules = allRules.get(currentLocale.getLanguage());
            }
            if (currentPluralRules == null) {
                currentPluralRules = new PluralRules_None();
            }
            changingConfiguration = true;
            Locale.setDefault(currentLocale);
            android.content.res.Configuration config = new android.content.res.Configuration();
            config.locale = currentLocale;
            ApplicationLoader.applicationContext.getResources().updateConfiguration(config, ApplicationLoader.applicationContext.getResources().getDisplayMetrics());
            changingConfiguration = false;
            if (reloadLastFile || !isLoadingRemote && !force && shouldReinstallLangpack(localeInfo.shortName)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("reload locale because one of file is corrupted " + pathToFile + " " + pathToBaseFile);
                }
                if (init) {
                    AndroidUtilities.runOnUIThread(() -> reloadCurrentRemoteLocale(currentAccount, null, true, null));
                } else {
                    reloadCurrentRemoteLocale(currentAccount, null, true, null);
                }
                reloadLastFile = false;
            }
            if (!isLoadingRemote) {
                if (init) {
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface));
                } else {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                }
                RestrictedLanguagesSelectActivity.invalidateRestrictedLanguages();
                if (onDone != null) {
                    onDone.run();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            changingConfiguration = false;
        }
        recreateFormatters();
        if (force) {
            MediaDataController.getInstance(currentAccount).loadAttachMenuBots(false, true);
        }
        return requestId;
    }

    public LocaleInfo getCurrentLocaleInfo() {
        return currentLocaleInfo;
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public static String getCurrentLanguageName() {
        LocaleInfo localeInfo = getInstance().currentLocaleInfo;
        return localeInfo == null || TextUtils.isEmpty(localeInfo.name) ? getString("LanguageName", R.string.LanguageName) : localeInfo.name;
    }

    private String getStringInternal(String key, int res) {
        return getStringInternal(key, null, res);
    }

    private String getStringInternal(String key, String fallback, int res) {
        String value = BuildVars.USE_CLOUD_STRINGS ? localeValues.get(key) : null;
        if (value == null) {
            if (BuildVars.USE_CLOUD_STRINGS && fallback != null) {
                value = localeValues.get(fallback);
            }
            if (value == null) {
                try {
                    value = ApplicationLoader.applicationContext.getString(res);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (value == null) {
            value = "LOC_ERR:" + key;
        }
        return value;
    }

    public static String getServerString(String key) {
        String value = getInstance().localeValues.get(key);
        if (value == null) {
            int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(key, "string", ApplicationLoader.applicationContext.getPackageName());
            if (resourceId != 0) {
                value = ApplicationLoader.applicationContext.getString(resourceId);
            }
        }
        return value;
    }

    public static String getString(@StringRes int res) {
        String key = resourcesCacheMap.get(res);
        if (key == null) {
            resourcesCacheMap.put(res, key = ApplicationLoader.applicationContext.getResources().getResourceEntryName(res));
        }
        return getString(key, res);
    }

    public static String getString(String key, int res) {
        return getInstance().getStringInternal(key, res);
    }

    public static String getString(String key, String fallback, int res) {
        return getInstance().getStringInternal(key, fallback, res);
    }

    public static String getString(String key) {
        if (TextUtils.isEmpty(key)) {
            return "LOC_ERR:" + key;
        }
        int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(key, "string", ApplicationLoader.applicationContext.getPackageName());
        if (resourceId != 0) {
            return getString(key, resourceId);
        }
        return getServerString(key);
    }

    public static String getPluralString(String key, int plural) {
        if (key == null || key.length() == 0 || getInstance().currentPluralRules == null) {
            return "LOC_ERR:" + key;
        }
        String param = getInstance().stringForQuantity(getInstance().currentPluralRules.quantityForNumber(plural));
        param = key + "_" + param;
        int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(param, "string", ApplicationLoader.applicationContext.getPackageName());
        return getString(param, key + "_other", resourceId);
    }

    public static String formatPluralString(String key, int plural, Object... args) {
        if (key == null || key.length() == 0 || getInstance().currentPluralRules == null) {
            return "LOC_ERR:" + key;
        }
        String param = getInstance().stringForQuantity(getInstance().currentPluralRules.quantityForNumber(plural));
        param = key + "_" + param;
        int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(param, "string", ApplicationLoader.applicationContext.getPackageName());
        int fallbackResourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(key + "_other", "string", ApplicationLoader.applicationContext.getPackageName());
        Object[] argsWithPlural = new Object[args.length + 1];
        argsWithPlural[0] = plural;
        System.arraycopy(args, 0, argsWithPlural, 1, args.length);
        return formatString(param, key + "_other", resourceId, fallbackResourceId, argsWithPlural);
    }

    public static String formatPluralStringComma(String key, int plural) {
        return formatPluralStringComma(key, plural, ',');
    }

    public static String formatPluralStringComma(String key, int plural, char symbol) {
        try {
            if (key == null || key.length() == 0 || getInstance().currentPluralRules == null) {
                return "LOC_ERR:" + key;
            }
            String param = getInstance().stringForQuantity(getInstance().currentPluralRules.quantityForNumber(plural));
            param = key + "_" + param;
            StringBuilder stringBuilder = new StringBuilder(String.format("%d", plural));
            for (int a = stringBuilder.length() - 3; a > 0; a -= 3) {
                stringBuilder.insert(a, symbol);
            }

            String value = BuildVars.USE_CLOUD_STRINGS ? getInstance().localeValues.get(param) : null;
            if (value == null) {
                value = BuildVars.USE_CLOUD_STRINGS ? getInstance().localeValues.get(key + "_other") : null;
            }
            if (value == null) {
                int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(param, "string", ApplicationLoader.applicationContext.getPackageName());
                value = ApplicationLoader.applicationContext.getString(resourceId);
            }
            value = value.replace("%1$d", "%1$s");

            if (getInstance().currentLocale != null) {
                return String.format(getInstance().currentLocale, value, stringBuilder);
            } else {
                return String.format(value, stringBuilder);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR: " + key;
        }
    }

    public static String formatString(@StringRes int res, Object... args) {
        String key = resourcesCacheMap.get(res);
        if (key == null) {
            resourcesCacheMap.put(res, key = ApplicationLoader.applicationContext.getResources().getResourceEntryName(res));
        }
        return formatString(key, res, args);
    }

    public static String formatString(String key, int res, Object... args) {
        return formatString(key, null, res, 0, args);
    }

    public static String formatString(String key, String fallback, int res, int fallbackRes, Object... args) {
        try {
            String value = BuildVars.USE_CLOUD_STRINGS ? getInstance().localeValues.get(key) : null;
            if (value == null) {
                if (BuildVars.USE_CLOUD_STRINGS && fallback != null) {
                    value = getInstance().localeValues.get(fallback);
                }
                if (value == null) {
                    try {
                        value = ApplicationLoader.applicationContext.getString(res);
                    } catch (Exception e) {
                        if (fallbackRes != 0) {
                            try {
                                value = ApplicationLoader.applicationContext.getString(fallbackRes);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            if (getInstance().currentLocale != null) {
                return String.format(getInstance().currentLocale, value, args);
            } else {
                return String.format(value, args);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR: " + key;
        }
    }

    public static String formatTTLString(int ttl) {
        if (ttl < 60) {
            return LocaleController.formatPluralString("Seconds", ttl);
        } else if (ttl < 60 * 60) {
            return LocaleController.formatPluralString("Minutes", ttl / 60);
        } else if (ttl < 60 * 60 * 24) {
            return LocaleController.formatPluralString("Hours", ttl / 60 / 60);
        } else if (ttl < 60 * 60 * 24 * 7) {
            return LocaleController.formatPluralString("Days", ttl / 60 / 60 / 24);
        } else if (ttl < 60 * 60 * 24 * 31) {
            int days = ttl / 60 / 60 / 24;
            if (ttl % 7 == 0) {
                return LocaleController.formatPluralString("Weeks", days / 7);
            } else {
                return String.format("%s %s", LocaleController.formatPluralString("Weeks", days / 7), LocaleController.formatPluralString("Days", days % 7));
            }
        } else {
            return LocaleController.formatPluralString("Months", ttl / 60 / 60 / 24 / 30);
        }
    }

    private static char[] defaultNumbers = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private static char[][] otherNumbers = new char[][]{
            {'٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'},
            {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'},
            {'०', '१', '२', '३', '४', '५', '६', '७', '८', '९'},
            {'૦', '૧', '૨', '૩', '૪', '૫', '૬', '૭', '૮', '૯'},
            {'੦', '੧', '੨', '੩', '੪', '੫', '੬', '੭', '੮', '੯'},
            {'০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'},
            {'೦', '೧', '೨', '೩', '೪', '೫', '೬', '೭', '೮', '೯'},
            {'୦', '୧', '୨', '୩', '୪', '୫', '୬', '୭', '୮', '୯'},
            {'൦', '൧', '൨', '൩', '൪', '൫', '൬', '൭', '൮', '൯'},
            {'௦', '௧', '௨', '௩', '௪', '௫', '௬', '௭', '௮', '௯'},
            {'౦', '౧', '౨', '౩', '౪', '౫', '౬', '౭', '౮', '౯'},
            {'၀', '၁', '၂', '၃', '၄', '၅', '၆', '၇', '၈', '၉'},
            {'༠', '༡', '༢', '༣', '༤', '༥', '༦', '༧', '༨', '༩'},
            {'᠐', '᠑', '᠒', '᠓', '᠔', '᠕', '᠖', '᠗', '᠘', '᠙'},
            {'០', '១', '២', '៣', '៤', '៥', '៦', '៧', '៨', '៩'},
            {'๐', '๑', '๒', '๓', '๔', '๕', '๖', '๗', '๘', '๙'},
            {'໐', '໑', '໒', '໓', '໔', '໕', '໖', '໗', '໘', '໙'},
            {'꧐', '꧑', '꧒', '꧓', '꧔', '꧕', '꧖', '꧗', '꧘', '꧙'}
    };

    public static String fixNumbers(CharSequence numbers) {
        StringBuilder builder = new StringBuilder(numbers);
        for (int c = 0, N = builder.length(); c < N; c++) {
            char ch = builder.charAt(c);
            if (ch >= '0' && ch <= '9' || ch == '.' || ch == ',') {
                continue;
            }
            for (int a = 0; a < otherNumbers.length; a++) {
                for (int b = 0; b < otherNumbers[a].length; b++) {
                    if (ch == otherNumbers[a][b]) {
                        builder.setCharAt(c, defaultNumbers[b]);
                        a = otherNumbers.length;
                        break;
                    }
                }
            }
        }
        return builder.toString();
    }

    public String formatCurrencyString(long amount, String type) {
        return formatCurrencyString(amount, true, true, false, type);
    }

    public String formatCurrencyString(long amount, boolean fixAnything, boolean withExp, boolean editText, String type) {
        type = type.toUpperCase();
        String customFormat;
        double doubleAmount;
        boolean discount = amount < 0;
        amount = Math.abs(amount);
        Currency currency = Currency.getInstance(type);
        switch (type) {
            case "CLF":
                customFormat = " %.4f";
                doubleAmount = amount / 10000.0;
                break;

            case "IRR":
                doubleAmount = amount / 100.0f;
                if (fixAnything && amount % 100 == 0) {
                    customFormat = " %.0f";
                } else {
                    customFormat = " %.2f";
                }
                break;

            case "BHD":
            case "IQD":
            case "JOD":
            case "KWD":
            case "LYD":
            case "OMR":
            case "TND":
                customFormat = " %.3f";
                doubleAmount = amount / 1000.0;
                break;

            case "BIF":
            case "BYR":
            case "CLP":
            case "CVE":
            case "DJF":
            case "GNF":
            case "ISK":
            case "JPY":
            case "KMF":
            case "KRW":
            case "MGA":
            case "PYG":
            case "RWF":
            case "UGX":
            case "UYI":
            case "VND":
            case "VUV":
            case "XAF":
            case "XOF":
            case "XPF":
                customFormat = " %.0f";
                doubleAmount = amount;
                break;

            case "MRO":
                customFormat = " %.1f";
                doubleAmount = amount / 10.0;
                break;

            default:
                customFormat = " %.2f";
                doubleAmount = amount / 100.0;
                break;
        }
        if (!withExp) {
            customFormat = " %.0f";
        }
        if (currency != null) {
            NumberFormat format = NumberFormat.getCurrencyInstance(currentLocale != null ? currentLocale : systemDefaultLocale);
            format.setCurrency(currency);
            if (editText) {
                format.setGroupingUsed(false);
            }
            if (!withExp || fixAnything && type.equals("IRR")) {
                format.setMaximumFractionDigits(0);
            }
            String result = (discount ? "-" : "") + format.format(doubleAmount);
            int idx = result.indexOf(type);
            if (idx >= 0) {
                idx += type.length();
                if (idx < result.length() && result.charAt(idx) != ' ') {
                    result = result.substring(0, idx) + " " + result.substring(idx);
                }
            }
            return result;
        }
        return (discount ? "-" : "") + String.format(Locale.US, type + customFormat, doubleAmount);
    }

    public static int getCurrencyExpDivider(String type) {
        switch (type) {
            case "CLF":
                return 10000;
            case "BHD":
            case "IQD":
            case "JOD":
            case "KWD":
            case "LYD":
            case "OMR":
            case "TND":
                return 1000;
            case "BIF":
            case "BYR":
            case "CLP":
            case "CVE":
            case "DJF":
            case "GNF":
            case "ISK":
            case "JPY":
            case "KMF":
            case "KRW":
            case "MGA":
            case "PYG":
            case "RWF":
            case "UGX":
            case "UYI":
            case "VND":
            case "VUV":
            case "XAF":
            case "XOF":
            case "XPF":
                return 1;
            case "MRO":
                return 10;
            default:
                return 100;
        }
    }

    public String formatCurrencyDecimalString(long amount, String type, boolean inludeType) {
        type = type.toUpperCase();
        String customFormat;
        double doubleAmount;
        amount = Math.abs(amount);
        switch (type) {
            case "CLF":
                customFormat = " %.4f";
                doubleAmount = amount / 10000.0;
                break;

            case "IRR":
                doubleAmount = amount / 100.0f;
                if (amount % 100 == 0) {
                    customFormat = " %.0f";
                } else {
                    customFormat = " %.2f";
                }
                break;

            case "BHD":
            case "IQD":
            case "JOD":
            case "KWD":
            case "LYD":
            case "OMR":
            case "TND":
                customFormat = " %.3f";
                doubleAmount = amount / 1000.0;
                break;

            case "BIF":
            case "BYR":
            case "CLP":
            case "CVE":
            case "DJF":
            case "GNF":
            case "ISK":
            case "JPY":
            case "KMF":
            case "KRW":
            case "MGA":
            case "PYG":
            case "RWF":
            case "UGX":
            case "UYI":
            case "VND":
            case "VUV":
            case "XAF":
            case "XOF":
            case "XPF":
                customFormat = " %.0f";
                doubleAmount = amount;
                break;

            case "MRO":
                customFormat = " %.1f";
                doubleAmount = amount / 10.0;
                break;

            default:
                customFormat = " %.2f";
                doubleAmount = amount / 100.0;
                break;
        }
        return String.format(Locale.US, inludeType ? type : "" + customFormat, doubleAmount).trim();
    }

    public static String formatStringSimple(String string, Object... args) {
        try {
            if (getInstance().currentLocale != null) {
                return String.format(getInstance().currentLocale, string, args);
            } else {
                return String.format(string, args);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR: " + string;
        }
    }

    public static String formatDuration(int duration) {
        if (duration <= 0) {
            return formatPluralString("Seconds", 0);
        }
        final int hours = duration / 3600;
        final int minutes = duration / 60 % 60;
        final int seconds = duration % 60;
        final StringBuilder stringBuilder = new StringBuilder();
        if (hours > 0) {
            stringBuilder.append(formatPluralString("Hours", hours));
        }
        if (minutes > 0) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(formatPluralString("Minutes", minutes));
        }
        if (seconds > 0) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(formatPluralString("Seconds", seconds));
        }
        return stringBuilder.toString();
    }

    public static String formatCallDuration(int duration) {
        if (duration > 3600) {
            String result = LocaleController.formatPluralString("Hours", duration / 3600);
            int minutes = duration % 3600 / 60;
            if (minutes > 0) {
                result += ", " + LocaleController.formatPluralString("Minutes", minutes);
            }
            return result;
        } else if (duration > 60) {
            return LocaleController.formatPluralString("Minutes", duration / 60);
        } else {
            return LocaleController.formatPluralString("Seconds", duration);
        }
    }

    public void onDeviceConfigurationChange(Configuration newConfig) {
        if (changingConfiguration) {
            return;
        }
        is24HourFormat = DateFormat.is24HourFormat(ApplicationLoader.applicationContext);
        systemDefaultLocale = newConfig.locale;
        if (languageOverride != null) {
            LocaleInfo toSet = currentLocaleInfo;
            currentLocaleInfo = null;
            applyLanguage(toSet, false, false, UserConfig.selectedAccount);
        } else {
            Locale newLocale = newConfig.locale;
            if (newLocale != null) {
                String d1 = newLocale.getDisplayName();
                String d2 = currentLocale.getDisplayName();
                if (d1 != null && d2 != null && !d1.equals(d2)) {
                    recreateFormatters();
                }
                currentLocale = newLocale;
                if (currentLocaleInfo != null && !TextUtils.isEmpty(currentLocaleInfo.pluralLangCode)) {
                    currentPluralRules = allRules.get(currentLocaleInfo.pluralLangCode);
                }
                if (currentPluralRules == null) {
                    currentPluralRules = allRules.get(currentLocale.getLanguage());
                    if (currentPluralRules == null) {
                        currentPluralRules = allRules.get("en");
                    }
                }
            }
        }
        String newSystemLocale = getSystemLocaleStringIso639();
        if (currentSystemLocale != null && !newSystemLocale.equals(currentSystemLocale)) {
            currentSystemLocale = newSystemLocale;
            ConnectionsManager.setSystemLangCode(currentSystemLocale);
        }
    }

    public static String formatDateChat(long date) {
        return formatDateChat(date, false);
    }

    public static String formatDateChat(long date, boolean checkYear) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int currentYear = calendar.get(Calendar.YEAR);
            date *= 1000;

            calendar.setTimeInMillis(date);
            if (checkYear && currentYear == calendar.get(Calendar.YEAR) || !checkYear && Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().chatDate.format(date);
            }
            return getInstance().chatFullDate.format(date);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR: formatDateChat";
    }

    public static String formatDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return getInstance().formatterDay.format(new Date(date));
            } else if (dateDay + 1 == day && year == dateYear) {
                return getString("Yesterday", R.string.Yesterday);
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().formatterDayMonth.format(new Date(date));
            } else {
                return getInstance().formatterYear.format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR: formatDate";
    }

    public static String formatDateAudio(long date, boolean shortFormat) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                if (shortFormat) {
                    return LocaleController.formatString("TodayAtFormatted", R.string.TodayAtFormatted, getInstance().formatterDay.format(new Date(date)));
                } else {
                    return LocaleController.formatString("TodayAtFormattedWithToday", R.string.TodayAtFormattedWithToday, getInstance().formatterDay.format(new Date(date)));
                }
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterDayMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatSeenDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return LocaleController.formatString("TodayAtFormattedWithToday", R.string.TodayAtFormattedWithToday, getInstance().formatterDay.format(new Date(date)));
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterDayMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatStoryDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            long timeInMillis = rightNow.getTimeInMillis();
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (timeInMillis - date < 1000 * 60) {
                return LocaleController.getString("RightNow", R.string.RightNow);
            } else if (timeInMillis - date < 1000 * 60 * 60) {
                int minutesAgo = (int) ((timeInMillis - date) / (1000 * 60));
                return LocaleController.formatPluralString("MinutesAgo", minutesAgo, minutesAgo);
            } else if (dateDay == day && year == dateYear) {
                return LocaleController.formatString("TodayAtFormattedWithToday", R.string.TodayAtFormattedWithToday, getInstance().formatterDay.format(new Date(date)));
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterDayMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatDateCallLog(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return getInstance().formatterDay.format(new Date(date));
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().chatDate.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().chatFullDate.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatStatusExpireDateTime(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return LocaleController.formatString("TodayAtFormatted", R.string.TodayAtFormatted, getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().formatterScheduleDay.format(new Date(date));
            } else {
                return getInstance().chatFullDate.format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatDateTime(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return LocaleController.formatString("TodayAtFormattedWithToday", R.string.TodayAtFormattedWithToday, getInstance().formatterDay.format(new Date(date)));
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().chatDate.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().chatFullDate.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatLocationUpdateDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                int diff = (int) (ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime() - date / 1000) / 60;
                if (diff < 1) {
                    return LocaleController.getString("LocationUpdatedJustNow", R.string.LocationUpdatedJustNow);
                } else if (diff < 60) {
                    return LocaleController.formatPluralString("UpdatedMinutes", diff);
                }
                return LocaleController.formatString("LocationUpdatedFormatted", R.string.LocationUpdatedFormatted, LocaleController.formatString("TodayAtFormatted", R.string.TodayAtFormatted, getInstance().formatterDay.format(new Date(date))));
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("LocationUpdatedFormatted", R.string.LocationUpdatedFormatted, LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date))));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterDayMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return LocaleController.formatString("LocationUpdatedFormatted", R.string.LocationUpdatedFormatted, format);
            } else {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return LocaleController.formatString("LocationUpdatedFormatted", R.string.LocationUpdatedFormatted, format);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatLocationLeftTime(int time) {
        String text;
        int hours = time / 60 / 60;
        time -= hours * 60 * 60;
        int minutes = time / 60;
        time -= minutes * 60;
        if (hours != 0) {
            text = String.format("%dh", hours + (minutes > 30 ? 1 : 0));
        } else if (minutes != 0) {
            text = String.format("%d", minutes + (time > 30 ? 1 : 0));
        } else {
            text = String.format("%d", time);
        }
        return text;
    }

    public static String formatDateOnline(long date, boolean[] madeShorter) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            int hour = rightNow.get(Calendar.HOUR_OF_DAY);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);
            int dateHour = rightNow.get(Calendar.HOUR_OF_DAY);

            if (dateDay == day && year == dateYear) {
                return LocaleController.formatString("LastSeenFormatted", R.string.LastSeenFormatted, LocaleController.formatString("TodayAtFormatted", R.string.TodayAtFormatted, getInstance().formatterDay.format(new Date(date))));
                /*int diff = (int) (ConnectionsManager.getInstance().getCurrentTime() - date) / 60;
                if (diff < 1) {
                    return LocaleController.getString("LastSeenNow", R.string.LastSeenNow);
                } else if (diff < 60) {
                    return LocaleController.formatPluralString("LastSeenMinutes", diff);
                } else {
                    return LocaleController.formatPluralString("LastSeenHours", (int) Math.ceil(diff / 60.0f));
                }*/
            } else if (dateDay + 1 == day && year == dateYear) {
                if (madeShorter != null) {
                    madeShorter[0] = true;
                    if (hour <= 6 && dateHour > 18 && is24HourFormat) {
                        return LocaleController.formatString("LastSeenFormatted", R.string.LastSeenFormatted, getInstance().formatterDay.format(new Date(date)));
                    }
                    return LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date)));
                } else {
                    return LocaleController.formatString("LastSeenFormatted", R.string.LastSeenFormatted, LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted, getInstance().formatterDay.format(new Date(date))));
                }
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterDayMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return LocaleController.formatString("LastSeenDateFormatted", R.string.LastSeenDateFormatted, format);
            } else {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return LocaleController.formatString("LastSeenDateFormatted", R.string.LastSeenDateFormatted, format);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    private FastDateFormat createFormatter(Locale locale, String format, String defaultFormat) {
        if (format == null || format.length() == 0) {
            format = defaultFormat;
        }
        FastDateFormat formatter;
        try {
            formatter = FastDateFormat.getInstance(format, locale);
        } catch (Exception e) {
            format = defaultFormat;
            formatter = FastDateFormat.getInstance(format, locale);
        }
        return formatter;
    }

    public void recreateFormatters() {
        Locale locale = currentLocale;
        if (locale == null) {
            locale = Locale.getDefault();
        }
        String lang = locale.getLanguage();
        if (lang == null) {
            lang = "en";
        }
        lang = lang.toLowerCase();
        isRTL = lang.length() == 2 && (lang.equals("ar") || lang.equals("fa") || lang.equals("he") || lang.equals("iw")) ||
                lang.startsWith("ar_") || lang.startsWith("fa_") || lang.startsWith("he_") || lang.startsWith("iw_")
                || currentLocaleInfo != null && currentLocaleInfo.isRtl;
        nameDisplayOrder = lang.equals("ko") ? 2 : 1;

        formatterBoostExpired = createFormatter(locale, getStringInternal("formatterBoostExpired", R.string.formatterBoostExpired), "MMM dd, yyyy");
        formatterGiveawayCard = createFormatter(locale, getStringInternal("formatterGiveawayCard", R.string.formatterGiveawayCard), "dd MMM yyyy");
        formatterGiveawayMonthDay = createFormatter(locale, getStringInternal("formatterGiveawayMonthDay", R.string.formatterGiveawayMonthDay), "MMMM dd");
        formatterGiveawayMonthDayYear = createFormatter(locale, getStringInternal("formatterGiveawayMonthDayYear", R.string.formatterGiveawayMonthDayYear), "MMMM dd, yyyy");
        formatterMonthYear = createFormatter(locale, getStringInternal("formatterMonthYear", R.string.formatterMonthYear), "MMM yyyy");
        formatterDayMonth = createFormatter(locale, getStringInternal("formatterMonth", R.string.formatterMonth), "dd MMM");
        formatterYear = createFormatter(locale, getStringInternal("formatterYear", R.string.formatterYear), "dd.MM.yy");
        formatterYearMax = createFormatter(locale, getStringInternal("formatterYearMax", R.string.formatterYearMax), "dd.MM.yyyy");
        chatDate = createFormatter(locale, getStringInternal("chatDate", R.string.chatDate), "d MMMM");
        chatFullDate = createFormatter(locale, getStringInternal("chatFullDate", R.string.chatFullDate), "d MMMM yyyy");
        formatterWeek = createFormatter(locale, getStringInternal("formatterWeek", R.string.formatterWeek), "EEE");
        formatterWeekLong = createFormatter(locale, getStringInternal("formatterWeekLong", R.string.formatterWeekLong), "EEEE");
        formatterScheduleDay = createFormatter(locale, getStringInternal("formatDateSchedule", R.string.formatDateSchedule), "MMM d");
        formatterScheduleYear = createFormatter(locale, getStringInternal("formatDateScheduleYear", R.string.formatDateScheduleYear), "MMM d yyyy");
        formatterDay = createFormatter(lang.toLowerCase().equals("ar") || lang.toLowerCase().equals("ko") ? locale : Locale.US, is24HourFormat ? getStringInternal("formatterDay24H", R.string.formatterDay24H) : getStringInternal("formatterDay12H", R.string.formatterDay12H), is24HourFormat ? "HH:mm" : "h:mm a");
        formatterStats = createFormatter(locale, is24HourFormat ? getStringInternal("formatterStats24H", R.string.formatterStats24H) : getStringInternal("formatterStats12H", R.string.formatterStats12H), is24HourFormat ? "MMM dd yyyy, HH:mm" : "MMM dd yyyy, h:mm a");
        formatterBannedUntil = createFormatter(locale, is24HourFormat ? getStringInternal("formatterBannedUntil24H", R.string.formatterBannedUntil24H) : getStringInternal("formatterBannedUntil12H", R.string.formatterBannedUntil12H), is24HourFormat ? "MMM dd yyyy, HH:mm" : "MMM dd yyyy, h:mm a");
        formatterBannedUntilThisYear = createFormatter(locale, is24HourFormat ? getStringInternal("formatterBannedUntilThisYear24H", R.string.formatterBannedUntilThisYear24H) : getStringInternal("formatterBannedUntilThisYear12H", R.string.formatterBannedUntilThisYear12H), is24HourFormat ? "MMM dd, HH:mm" : "MMM dd, h:mm a");
        formatterScheduleSend[0] = createFormatter(locale, getStringInternal("SendTodayAt", R.string.SendTodayAt), "'Send today at' HH:mm");
        formatterScheduleSend[1] = createFormatter(locale, getStringInternal("SendDayAt", R.string.SendDayAt), "'Send on' MMM d 'at' HH:mm");
        formatterScheduleSend[2] = createFormatter(locale, getStringInternal("SendDayYearAt", R.string.SendDayYearAt), "'Send on' MMM d yyyy 'at' HH:mm");
        formatterScheduleSend[3] = createFormatter(locale, getStringInternal("RemindTodayAt", R.string.RemindTodayAt), "'Remind today at' HH:mm");
        formatterScheduleSend[4] = createFormatter(locale, getStringInternal("RemindDayAt", R.string.RemindDayAt), "'Remind on' MMM d 'at' HH:mm");
        formatterScheduleSend[5] = createFormatter(locale, getStringInternal("RemindDayYearAt", R.string.RemindDayYearAt), "'Remind on' MMM d yyyy 'at' HH:mm");
        formatterScheduleSend[6] = createFormatter(locale, getStringInternal("StartTodayAt", R.string.StartTodayAt), "'Start today at' HH:mm");
        formatterScheduleSend[7] = createFormatter(locale, getStringInternal("StartDayAt", R.string.StartDayAt), "'Start on' MMM d 'at' HH:mm");
        formatterScheduleSend[8] = createFormatter(locale, getStringInternal("StartDayYearAt", R.string.StartDayYearAt), "'Start on' MMM d yyyy 'at' HH:mm");
        formatterScheduleSend[9] = createFormatter(locale, getStringInternal("StartShortTodayAt", R.string.StartShortTodayAt), "'Today,' HH:mm");
        formatterScheduleSend[10] = createFormatter(locale, getStringInternal("StartShortDayAt", R.string.StartShortDayAt), "MMM d',' HH:mm");
        formatterScheduleSend[11] = createFormatter(locale, getStringInternal("StartShortDayYearAt", R.string.StartShortDayYearAt), "MMM d yyyy, HH:mm");
        formatterScheduleSend[12] = createFormatter(locale, getStringInternal("StartsTodayAt", R.string.StartsTodayAt), "'Starts today at' HH:mm");
        formatterScheduleSend[13] = createFormatter(locale, getStringInternal("StartsDayAt", R.string.StartsDayAt), "'Starts on' MMM d 'at' HH:mm");
        formatterScheduleSend[14] = createFormatter(locale, getStringInternal("StartsDayYearAt", R.string.StartsDayYearAt), "'Starts on' MMM d yyyy 'at' HH:mm");
    }

    public static boolean isRTLCharacter(char ch) {
        return Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
    }

    public static String formatStartsTime(long date, int type) {
        return formatStartsTime(date, type, true);
    }

    public static String formatStartsTime(long date, int type, boolean needToday) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        int currentYear = calendar.get(Calendar.YEAR);
        int currentDay = calendar.get(Calendar.DAY_OF_YEAR);

        calendar.setTimeInMillis(date * 1000);
        int selectedYear = calendar.get(Calendar.YEAR);
        int selectedDay = calendar.get(Calendar.DAY_OF_YEAR);

        int num;
        if (currentYear == selectedYear) {
            if (needToday && selectedDay == currentDay) {
                num = 0;
            } else {
                num = 1;
            }
        } else {
            num = 2;
        }
        if (type == 1) {
            num += 3;
        } else if (type == 2) {
            num += 6;
        } else if (type == 3) {
            num += 9;
        } else if (type == 4) {
            num += 12;
        }
        return LocaleController.getInstance().formatterScheduleSend[num].format(calendar.getTimeInMillis());
    }

    public static String formatSectionDate(long date) {
        return formatYearMont(date, false);
    }


    public static String formatYearMont(long date, boolean alwaysShowYear) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateYear = rightNow.get(Calendar.YEAR);
            int month = rightNow.get(Calendar.MONTH);

            final String[] months = new String[]{
                    LocaleController.getString("January", R.string.January),
                    LocaleController.getString("February", R.string.February),
                    LocaleController.getString("March", R.string.March),
                    LocaleController.getString("April", R.string.April),
                    LocaleController.getString("May", R.string.May),
                    LocaleController.getString("June", R.string.June),
                    LocaleController.getString("July", R.string.July),
                    LocaleController.getString("August", R.string.August),
                    LocaleController.getString("September", R.string.September),
                    LocaleController.getString("October", R.string.October),
                    LocaleController.getString("November", R.string.November),
                    LocaleController.getString("December", R.string.December)
            };
            if (year == dateYear && !alwaysShowYear) {
                return months[month];
            } else {
                return months[month] + " " + dateYear;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatDateForBan(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (year == dateYear) {
                return getInstance().formatterBannedUntilThisYear.format(new Date(date));
            } else {
                return getInstance().formatterBannedUntil.format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String stringForMessageListDate(long date) {
        try {
            date *= 1000;
            if (Math.abs(System.currentTimeMillis() - date) >= 31536000000L) {
                return getInstance().formatterYear.format(new Date(date));
            } else {
                Calendar rightNow = Calendar.getInstance();
                int day = rightNow.get(Calendar.DAY_OF_YEAR);
                rightNow.setTimeInMillis(date);
                int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);

                int dayDiff = dateDay - day;
                if (dayDiff == 0 || dayDiff == -1 && System.currentTimeMillis() - date < 60 * 60 * 8 * 1000) {
                    return getInstance().formatterDay.format(new Date(date));
                } else if (dayDiff > -7 && dayDiff <= -1) {
                    return getInstance().formatterWeek.format(new Date(date));
                } else {
                    return getInstance().formatterDayMonth.format(new Date(date));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatShortNumber(int number, int[] rounded) {
        StringBuilder K = new StringBuilder();
        int lastDec = 0;
        int KCount = 0;
        while (number / 1000 > 0) {
            K.append("K");
            lastDec = (number % 1000) / 100;
            number /= 1000;
        }
        if (rounded != null) {
            double value = number + lastDec / 10.0;
            for (int a = 0; a < K.length(); a++) {
                value *= 1000;
            }
            rounded[0] = (int) value;
        }
        if (lastDec != 0 && K.length() > 0) {
            if (K.length() == 2) {
                return String.format(Locale.US, "%d.%dM", number, lastDec);
            } else {
                return String.format(Locale.US, "%d.%d%s", number, lastDec, K.toString());
            }
        }
        if (K.length() == 2) {
            return String.format(Locale.US, "%dM", number);
        } else {
            return String.format(Locale.US, "%d%s", number, K.toString());
        }
    }

    public static String formatUserStatus(int currentAccount, TLRPC.User user) {
        return formatUserStatus(currentAccount, user, null);
    }

    public static String formatJoined(long date) {
        try {
            date *= 1000;
            String format;
            if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterDayMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
            return formatString("ChannelOtherSubscriberJoined", R.string.ChannelOtherSubscriberJoined, format);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatImportedDate(long date) {
        try {
            date *= 1000;
            Date dt = new Date(date);
            return String.format("%1$s, %2$s", getInstance().formatterYear.format(dt), getInstance().formatterDay.format(dt));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatUserStatus(int currentAccount, TLRPC.User user, boolean[] isOnline) {
        return formatUserStatus(currentAccount, user, isOnline, null);
    }

    public static String formatUserStatus(int currentAccount, TLRPC.User user, boolean[] isOnline, boolean[] madeShorter) {
        if (user != null && user.status != null && user.status.expires == 0) {
            if (user.status instanceof TLRPC.TL_userStatusRecently) {
                user.status.expires = -100;
            } else if (user.status instanceof TLRPC.TL_userStatusLastWeek) {
                user.status.expires = -101;
            } else if (user.status instanceof TLRPC.TL_userStatusLastMonth) {
                user.status.expires = -102;
            }
        }
        if (user != null && user.status != null && user.status.expires <= 0) {
            if (MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id)) {
                if (isOnline != null) {
                    isOnline[0] = true;
                }
                return getString("Online", R.string.Online);
            }
        }
        if (user == null || user.status == null || user.status.expires == 0 || UserObject.isDeleted(user) || user instanceof TLRPC.TL_userEmpty) {
            return getString("ALongTimeAgo", R.string.ALongTimeAgo);
        } else {
            int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (user.status.expires > currentTime) {
                if (isOnline != null) {
                    isOnline[0] = true;
                }
                return getString("Online", R.string.Online);
            } else {
                if (user.status.expires == -1) {
                    return getString("Invisible", R.string.Invisible);
                } else if (user.status.expires == -100) {
                    return getString("Lately", R.string.Lately);
                } else if (user.status.expires == -101) {
                    return getString("WithinAWeek", R.string.WithinAWeek);
                } else if (user.status.expires == -102) {
                    return getString("WithinAMonth", R.string.WithinAMonth);
                } else {
                    return formatDateOnline(user.status.expires, madeShorter);
                }
            }
        }
    }

    private String escapeString(String str) {
        if (str.contains("[CDATA")) {
            return str;
        }
        return str.replace("<", "&lt;").replace(">", "&gt;").replace("& ", "&amp; ");
    }

    public void saveRemoteLocaleStringsForCurrentLocale(final TLRPC.TL_langPackDifference difference, int currentAccount) {
        if (currentLocaleInfo == null) {
            return;
        }
        final String langCode = difference.lang_code.replace('-', '_').toLowerCase();
        if (!langCode.equals(currentLocaleInfo.shortName) && !langCode.equals(currentLocaleInfo.baseLangCode)) {
            return;
        }
        saveRemoteLocaleStrings(currentLocaleInfo, difference, currentAccount, null);
    }

    public void saveRemoteLocaleStrings(LocaleInfo localeInfo, final TLRPC.TL_langPackDifference difference, int currentAccount, Runnable onDone) {
        if (difference == null || difference.strings.isEmpty() || localeInfo == null || localeInfo.isLocal()) {
            FileLog.d("saveRemoteLocaleStrings: empty difference=" + (difference == null || difference.strings.isEmpty()) + "; locale is local or null=" + (localeInfo == null || localeInfo.isLocal()));
            recreateFormatters();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final String langCode = difference.lang_code.replace('-', '_').toLowerCase();
        int type;
        if (langCode.equals(localeInfo.shortName)) {
            type = 0;
        } else if (langCode.equals(localeInfo.baseLangCode)) {
            type = 1;
        } else {
            type = -1;
        }
        if (type == -1) {
            FileLog.d("saveRemoteLocaleStrings: unknown language " + langCode + " (locale short=" + localeInfo.shortName + ", base=" + localeInfo.baseLangCode + ")");
            return;
        }
        File finalFile;
        if (type == 0) {
            finalFile = localeInfo.getPathToFile();
        } else {
            finalFile = localeInfo.getPathToBaseFile();
        }
        try {
            final HashMap<String, String> values;
            if (difference.from_version == 0) {
                FileLog.d("saveRemoteLocaleStrings: difference is straight from the beginning");
                values = new HashMap<>();
            } else {
                FileLog.d("saveRemoteLocaleStrings: difference is from version " + difference.from_version + " ours " + localeInfo.version + " (base version " + localeInfo.baseLangCode + ")");
                values = getLocaleFileStrings(finalFile, true);
            }
            for (int a = 0; a < difference.strings.size(); a++) {
                TLRPC.LangPackString string = difference.strings.get(a);
                if (string instanceof TLRPC.TL_langPackString) {
                    values.put(string.key, escapeString(string.value));
                } else if (string instanceof TLRPC.TL_langPackStringPluralized) {
                    values.put(string.key + "_zero", string.zero_value != null ? escapeString(string.zero_value) : "");
                    values.put(string.key + "_one", string.one_value != null ? escapeString(string.one_value) : "");
                    values.put(string.key + "_two", string.two_value != null ? escapeString(string.two_value) : "");
                    values.put(string.key + "_few", string.few_value != null ? escapeString(string.few_value) : "");
                    values.put(string.key + "_many", string.many_value != null ? escapeString(string.many_value) : "");
                    values.put(string.key + "_other", string.other_value != null ? escapeString(string.other_value) : "");
                } else if (string instanceof TLRPC.TL_langPackStringDeleted) {
                    values.remove(string.key);
                }
            }
            FileLog.d("save locale file to " + finalFile);
            BufferedWriter writer = new BufferedWriter(new FileWriter(finalFile));
            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            writer.write("<resources>\n");
            for (HashMap.Entry<String, String> entry : values.entrySet()) {
                writer.write(String.format("<string name=\"%1$s\">%2$s</string>\n", entry.getKey(), entry.getValue()));
            }
            writer.write("</resources>");
            writer.close();
            boolean hasBase = localeInfo.hasBaseLang();
            final HashMap<String, String> valuesToSet = getLocaleFileStrings(hasBase ? localeInfo.getPathToBaseFile() : localeInfo.getPathToFile());
            if (hasBase) {
                valuesToSet.putAll(getLocaleFileStrings(localeInfo.getPathToFile()));
            }
            FileLog.d("saved locale file to " + finalFile);
            AndroidUtilities.runOnUIThread(() -> {
                if (type == 0) {
                    localeInfo.version = difference.version;
                } else {
                    localeInfo.baseVersion = difference.version;
                }
                saveOtherLanguages();
                try {
                    if (currentLocaleInfo == localeInfo) {
                        Locale newLocale;
                        String[] args;
                        if (!TextUtils.isEmpty(localeInfo.pluralLangCode)) {
                            args = localeInfo.pluralLangCode.split("_");
                        } else if (!TextUtils.isEmpty(localeInfo.baseLangCode)) {
                            args = localeInfo.baseLangCode.split("_");
                        } else {
                            args = localeInfo.shortName.split("_");
                        }
                        if (args.length == 1) {
                            newLocale = new Locale(args[0]);
                        } else {
                            newLocale = new Locale(args[0], args[1]);
                        }
                        languageOverride = localeInfo.shortName;

                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("language", localeInfo.getKey());
                        editor.commit();

                        localeValues = valuesToSet;
                        currentLocale = newLocale;
                        currentLocaleInfo = localeInfo;
                        if (!TextUtils.isEmpty(currentLocaleInfo.pluralLangCode)) {
                            currentPluralRules = allRules.get(currentLocaleInfo.pluralLangCode);
                        }
                        if (currentPluralRules == null) {
                            currentPluralRules = allRules.get(currentLocale.getLanguage());
                            if (currentPluralRules == null) {
                                currentPluralRules = allRules.get("en");
                            }
                        }
                        changingConfiguration = true;
                        Locale.setDefault(currentLocale);
                        Configuration config = new Configuration();
                        config.locale = currentLocale;
                        ApplicationLoader.applicationContext.getResources().updateConfiguration(config, ApplicationLoader.applicationContext.getResources().getDisplayMetrics());
                        changingConfiguration = false;

                        RestrictedLanguagesSelectActivity.invalidateRestrictedLanguages();
                    } else {
                        FileLog.d("saveRemoteLocaleStrings: currentLocaleInfo != localeInfo, do nothing");
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    changingConfiguration = false;
                }
                recreateFormatters();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                if (onDone != null) {
                    onDone.run();
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void loadRemoteLanguages(final int currentAccount) {
        loadRemoteLanguages(currentAccount, true);
    }

    public void loadRemoteLanguages(final int currentAccount, boolean applyCurrent) {
        if (loadingRemoteLanguages) {
            return;
        }
        loadingRemoteLanguages = true;
        TLRPC.TL_langpack_getLanguages req = new TLRPC.TL_langpack_getLanguages();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    loadingRemoteLanguages = false;
                    TLRPC.Vector res = (TLRPC.Vector) response;
                    for (int a = 0, size = remoteLanguages.size(); a < size; a++) {
                        remoteLanguages.get(a).serverIndex = Integer.MAX_VALUE;
                    }
                    for (int a = 0, size = res.objects.size(); a < size; a++) {
                        TLRPC.TL_langPackLanguage language = (TLRPC.TL_langPackLanguage) res.objects.get(a);
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("loaded lang " + language.name);
                        }
                        LocaleInfo localeInfo = new LocaleInfo();
                        localeInfo.nameEnglish = language.name;
                        localeInfo.name = language.native_name;
                        localeInfo.shortName = language.lang_code.replace('-', '_').toLowerCase();
                        if (language.base_lang_code != null) {
                            localeInfo.baseLangCode = language.base_lang_code.replace('-', '_').toLowerCase();
                        } else {
                            localeInfo.baseLangCode = "";
                        }
                        localeInfo.pluralLangCode = language.plural_code.replace('-', '_').toLowerCase();
                        localeInfo.isRtl = language.rtl;
                        localeInfo.pathToFile = "remote";
                        localeInfo.serverIndex = a;

                        LocaleInfo existing = getLanguageFromDict(localeInfo.getKey());
                        if (existing == null) {
                            languages.add(localeInfo);
                            languagesDict.put(localeInfo.getKey(), localeInfo);
                        } else {
                            existing.nameEnglish = localeInfo.nameEnglish;
                            existing.name = localeInfo.name;
                            existing.baseLangCode = localeInfo.baseLangCode;
                            existing.pluralLangCode = localeInfo.pluralLangCode;
                            existing.pathToFile = localeInfo.pathToFile;
                            existing.serverIndex = localeInfo.serverIndex;
                            localeInfo = existing;
                        }
                        if (!remoteLanguagesDict.containsKey(localeInfo.getKey())) {
                            remoteLanguages.add(localeInfo);
                            remoteLanguagesDict.put(localeInfo.getKey(), localeInfo);
                        }
                    }
                    for (int a = 0; a < remoteLanguages.size(); a++) {
                        LocaleInfo info = remoteLanguages.get(a);
                        if (info.serverIndex != Integer.MAX_VALUE || info == currentLocaleInfo) {
                            continue;
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("remove lang " + info.getKey());
                        }
                        remoteLanguages.remove(a);
                        remoteLanguagesDict.remove(info.getKey());
                        languages.remove(info);
                        languagesDict.remove(info.getKey());
                        a--;
                    }
                    saveOtherLanguages();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.suggestedLangpack);
                    if (applyCurrent) {
                        applyLanguage(currentLocaleInfo, true, false, currentAccount);
                    }
                });
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    private int applyRemoteLanguage(LocaleInfo localeInfo, String langCode, boolean force, final int currentAccount, Runnable onDone) {
        if (localeInfo == null || !localeInfo.isRemote() && !localeInfo.isUnofficial()) {
            return 0;
        }
        FileLog.d("applyRemoteLanguage " + langCode + " force=" + force + " currentAccount=" + currentAccount);
        int[] requested = new int[1], received = new int[1];
        requested[0] = received[0] = 0;
        Runnable onPartlyDone = () -> {
            received[0]++;
            if (received[0] >= requested[0] && onDone != null) {
                onDone.run();
            }
        };
        if (force) {
            patched(localeInfo.shortName);
        }
        if (localeInfo.hasBaseLang() && (langCode == null || langCode.equals(localeInfo.baseLangCode))) {
            if (localeInfo.baseVersion != 0 && !force) {
                if (localeInfo.hasBaseLang()) {
                    FileLog.d("applyRemoteLanguage getDifference of base");
                    TLRPC.TL_langpack_getDifference req = new TLRPC.TL_langpack_getDifference();
                    req.from_version = localeInfo.baseVersion;
                    req.lang_code = localeInfo.getBaseLangCode();
                    req.lang_pack = "";
                    requested[0]++;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                        if (response != null) {
                            AndroidUtilities.runOnUIThread(() -> saveRemoteLocaleStrings(localeInfo, (TLRPC.TL_langPackDifference) response, currentAccount, onPartlyDone));
                        }
                    }, ConnectionsManager.RequestFlagWithoutLogin);
                }
            } else {
                FileLog.d("applyRemoteLanguage getLangPack of base");
                TLRPC.TL_langpack_getLangPack req = new TLRPC.TL_langpack_getLangPack();
                req.lang_code = localeInfo.getBaseLangCode();
                requested[0]++;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            saveRemoteLocaleStrings(localeInfo, (TLRPC.TL_langPackDifference) response, currentAccount, onPartlyDone);
                        });
                    }
                }, ConnectionsManager.RequestFlagWithoutLogin);
            }
        }
        if (langCode == null || langCode.equals(localeInfo.shortName)) {
            if (localeInfo.version != 0 && !force) {
                FileLog.d("applyRemoteLanguage getDifference");
                TLRPC.TL_langpack_getDifference req = new TLRPC.TL_langpack_getDifference();
                req.from_version = localeInfo.version;
                req.lang_code = localeInfo.getLangCode();
                req.lang_pack = "";
                requested[0]++;
                return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            saveRemoteLocaleStrings(localeInfo, (TLRPC.TL_langPackDifference) response, currentAccount, onPartlyDone);
                        });
                    }
                }, ConnectionsManager.RequestFlagWithoutLogin);
            } else {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    ConnectionsManager.setLangCode(localeInfo.getLangCode());
                }
                FileLog.d("applyRemoteLanguage getLangPack");
                TLRPC.TL_langpack_getLangPack req = new TLRPC.TL_langpack_getLangPack();
                req.lang_code = localeInfo.getLangCode();
                requested[0]++;
                return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            saveRemoteLocaleStrings(localeInfo, (TLRPC.TL_langPackDifference) response, currentAccount, onPartlyDone);
                        });
                    }
                }, ConnectionsManager.RequestFlagWithoutLogin);
            }
        }
        return 0;
    }

    public String getTranslitString(String src) {
        return getTranslitString(src, true, false);
    }

    public String getTranslitString(String src, boolean onlyEnglish) {
        return getTranslitString(src, true, onlyEnglish);
    }

    public String getTranslitString(String src, boolean ru, boolean onlyEnglish) {
        if (src == null) {
            return null;
        }

        if (ruTranslitChars == null) {
            ruTranslitChars = new HashMap<>(33);
            ruTranslitChars.put("а", "a");
            ruTranslitChars.put("б", "b");
            ruTranslitChars.put("в", "v");
            ruTranslitChars.put("г", "g");
            ruTranslitChars.put("д", "d");
            ruTranslitChars.put("е", "e");
            ruTranslitChars.put("ё", "yo");
            ruTranslitChars.put("ж", "zh");
            ruTranslitChars.put("з", "z");
            ruTranslitChars.put("и", "i");
            ruTranslitChars.put("й", "i");
            ruTranslitChars.put("к", "k");
            ruTranslitChars.put("л", "l");
            ruTranslitChars.put("м", "m");
            ruTranslitChars.put("н", "n");
            ruTranslitChars.put("о", "o");
            ruTranslitChars.put("п", "p");
            ruTranslitChars.put("р", "r");
            ruTranslitChars.put("с", "s");
            ruTranslitChars.put("т", "t");
            ruTranslitChars.put("у", "u");
            ruTranslitChars.put("ф", "f");
            ruTranslitChars.put("х", "h");
            ruTranslitChars.put("ц", "ts");
            ruTranslitChars.put("ч", "ch");
            ruTranslitChars.put("ш", "sh");
            ruTranslitChars.put("щ", "sch");
            ruTranslitChars.put("ы", "i");
            ruTranslitChars.put("ь", "");
            ruTranslitChars.put("ъ", "");
            ruTranslitChars.put("э", "e");
            ruTranslitChars.put("ю", "yu");
            ruTranslitChars.put("я", "ya");
        }

        if (translitChars == null) {
            translitChars = new HashMap<>(487);
            translitChars.put("ȼ", "c");
            translitChars.put("ᶇ", "n");
            translitChars.put("ɖ", "d");
            translitChars.put("ỿ", "y");
            translitChars.put("ᴓ", "o");
            translitChars.put("ø", "o");
            translitChars.put("ḁ", "a");
            translitChars.put("ʯ", "h");
            translitChars.put("ŷ", "y");
            translitChars.put("ʞ", "k");
            translitChars.put("ừ", "u");
            translitChars.put("ꜳ", "aa");
            translitChars.put("ĳ", "ij");
            translitChars.put("ḽ", "l");
            translitChars.put("ɪ", "i");
            translitChars.put("ḇ", "b");
            translitChars.put("ʀ", "r");
            translitChars.put("ě", "e");
            translitChars.put("ﬃ", "ffi");
            translitChars.put("ơ", "o");
            translitChars.put("ⱹ", "r");
            translitChars.put("ồ", "o");
            translitChars.put("ǐ", "i");
            translitChars.put("ꝕ", "p");
            translitChars.put("ý", "y");
            translitChars.put("ḝ", "e");
            translitChars.put("ₒ", "o");
            translitChars.put("ⱥ", "a");
            translitChars.put("ʙ", "b");
            translitChars.put("ḛ", "e");
            translitChars.put("ƈ", "c");
            translitChars.put("ɦ", "h");
            translitChars.put("ᵬ", "b");
            translitChars.put("ṣ", "s");
            translitChars.put("đ", "d");
            translitChars.put("ỗ", "o");
            translitChars.put("ɟ", "j");
            translitChars.put("ẚ", "a");
            translitChars.put("ɏ", "y");
            translitChars.put("ʌ", "v");
            translitChars.put("ꝓ", "p");
            translitChars.put("ﬁ", "fi");
            translitChars.put("ᶄ", "k");
            translitChars.put("ḏ", "d");
            translitChars.put("ᴌ", "l");
            translitChars.put("ė", "e");
            translitChars.put("ᴋ", "k");
            translitChars.put("ċ", "c");
            translitChars.put("ʁ", "r");
            translitChars.put("ƕ", "hv");
            translitChars.put("ƀ", "b");
            translitChars.put("ṍ", "o");
            translitChars.put("ȣ", "ou");
            translitChars.put("ǰ", "j");
            translitChars.put("ᶃ", "g");
            translitChars.put("ṋ", "n");
            translitChars.put("ɉ", "j");
            translitChars.put("ǧ", "g");
            translitChars.put("ǳ", "dz");
            translitChars.put("ź", "z");
            translitChars.put("ꜷ", "au");
            translitChars.put("ǖ", "u");
            translitChars.put("ᵹ", "g");
            translitChars.put("ȯ", "o");
            translitChars.put("ɐ", "a");
            translitChars.put("ą", "a");
            translitChars.put("õ", "o");
            translitChars.put("ɻ", "r");
            translitChars.put("ꝍ", "o");
            translitChars.put("ǟ", "a");
            translitChars.put("ȴ", "l");
            translitChars.put("ʂ", "s");
            translitChars.put("ﬂ", "fl");
            translitChars.put("ȉ", "i");
            translitChars.put("ⱻ", "e");
            translitChars.put("ṉ", "n");
            translitChars.put("ï", "i");
            translitChars.put("ñ", "n");
            translitChars.put("ᴉ", "i");
            translitChars.put("ʇ", "t");
            translitChars.put("ẓ", "z");
            translitChars.put("ỷ", "y");
            translitChars.put("ȳ", "y");
            translitChars.put("ṩ", "s");
            translitChars.put("ɽ", "r");
            translitChars.put("ĝ", "g");
            translitChars.put("ᴝ", "u");
            translitChars.put("ḳ", "k");
            translitChars.put("ꝫ", "et");
            translitChars.put("ī", "i");
            translitChars.put("ť", "t");
            translitChars.put("ꜿ", "c");
            translitChars.put("ʟ", "l");
            translitChars.put("ꜹ", "av");
            translitChars.put("û", "u");
            translitChars.put("æ", "ae");
            translitChars.put("ă", "a");
            translitChars.put("ǘ", "u");
            translitChars.put("ꞅ", "s");
            translitChars.put("ᵣ", "r");
            translitChars.put("ᴀ", "a");
            translitChars.put("ƃ", "b");
            translitChars.put("ḩ", "h");
            translitChars.put("ṧ", "s");
            translitChars.put("ₑ", "e");
            translitChars.put("ʜ", "h");
            translitChars.put("ẋ", "x");
            translitChars.put("ꝅ", "k");
            translitChars.put("ḋ", "d");
            translitChars.put("ƣ", "oi");
            translitChars.put("ꝑ", "p");
            translitChars.put("ħ", "h");
            translitChars.put("ⱴ", "v");
            translitChars.put("ẇ", "w");
            translitChars.put("ǹ", "n");
            translitChars.put("ɯ", "m");
            translitChars.put("ɡ", "g");
            translitChars.put("ɴ", "n");
            translitChars.put("ᴘ", "p");
            translitChars.put("ᵥ", "v");
            translitChars.put("ū", "u");
            translitChars.put("ḃ", "b");
            translitChars.put("ṗ", "p");
            translitChars.put("å", "a");
            translitChars.put("ɕ", "c");
            translitChars.put("ọ", "o");
            translitChars.put("ắ", "a");
            translitChars.put("ƒ", "f");
            translitChars.put("ǣ", "ae");
            translitChars.put("ꝡ", "vy");
            translitChars.put("ﬀ", "ff");
            translitChars.put("ᶉ", "r");
            translitChars.put("ô", "o");
            translitChars.put("ǿ", "o");
            translitChars.put("ṳ", "u");
            translitChars.put("ȥ", "z");
            translitChars.put("ḟ", "f");
            translitChars.put("ḓ", "d");
            translitChars.put("ȇ", "e");
            translitChars.put("ȕ", "u");
            translitChars.put("ȵ", "n");
            translitChars.put("ʠ", "q");
            translitChars.put("ấ", "a");
            translitChars.put("ǩ", "k");
            translitChars.put("ĩ", "i");
            translitChars.put("ṵ", "u");
            translitChars.put("ŧ", "t");
            translitChars.put("ɾ", "r");
            translitChars.put("ƙ", "k");
            translitChars.put("ṫ", "t");
            translitChars.put("ꝗ", "q");
            translitChars.put("ậ", "a");
            translitChars.put("ʄ", "j");
            translitChars.put("ƚ", "l");
            translitChars.put("ᶂ", "f");
            translitChars.put("ᵴ", "s");
            translitChars.put("ꞃ", "r");
            translitChars.put("ᶌ", "v");
            translitChars.put("ɵ", "o");
            translitChars.put("ḉ", "c");
            translitChars.put("ᵤ", "u");
            translitChars.put("ẑ", "z");
            translitChars.put("ṹ", "u");
            translitChars.put("ň", "n");
            translitChars.put("ʍ", "w");
            translitChars.put("ầ", "a");
            translitChars.put("ǉ", "lj");
            translitChars.put("ɓ", "b");
            translitChars.put("ɼ", "r");
            translitChars.put("ò", "o");
            translitChars.put("ẘ", "w");
            translitChars.put("ɗ", "d");
            translitChars.put("ꜽ", "ay");
            translitChars.put("ư", "u");
            translitChars.put("ᶀ", "b");
            translitChars.put("ǜ", "u");
            translitChars.put("ẹ", "e");
            translitChars.put("ǡ", "a");
            translitChars.put("ɥ", "h");
            translitChars.put("ṏ", "o");
            translitChars.put("ǔ", "u");
            translitChars.put("ʎ", "y");
            translitChars.put("ȱ", "o");
            translitChars.put("ệ", "e");
            translitChars.put("ế", "e");
            translitChars.put("ĭ", "i");
            translitChars.put("ⱸ", "e");
            translitChars.put("ṯ", "t");
            translitChars.put("ᶑ", "d");
            translitChars.put("ḧ", "h");
            translitChars.put("ṥ", "s");
            translitChars.put("ë", "e");
            translitChars.put("ᴍ", "m");
            translitChars.put("ö", "o");
            translitChars.put("é", "e");
            translitChars.put("ı", "i");
            translitChars.put("ď", "d");
            translitChars.put("ᵯ", "m");
            translitChars.put("ỵ", "y");
            translitChars.put("ŵ", "w");
            translitChars.put("ề", "e");
            translitChars.put("ứ", "u");
            translitChars.put("ƶ", "z");
            translitChars.put("ĵ", "j");
            translitChars.put("ḍ", "d");
            translitChars.put("ŭ", "u");
            translitChars.put("ʝ", "j");
            translitChars.put("ê", "e");
            translitChars.put("ǚ", "u");
            translitChars.put("ġ", "g");
            translitChars.put("ṙ", "r");
            translitChars.put("ƞ", "n");
            translitChars.put("ḗ", "e");
            translitChars.put("ẝ", "s");
            translitChars.put("ᶁ", "d");
            translitChars.put("ķ", "k");
            translitChars.put("ᴂ", "ae");
            translitChars.put("ɘ", "e");
            translitChars.put("ợ", "o");
            translitChars.put("ḿ", "m");
            translitChars.put("ꜰ", "f");
            translitChars.put("ẵ", "a");
            translitChars.put("ꝏ", "oo");
            translitChars.put("ᶆ", "m");
            translitChars.put("ᵽ", "p");
            translitChars.put("ữ", "u");
            translitChars.put("ⱪ", "k");
            translitChars.put("ḥ", "h");
            translitChars.put("ţ", "t");
            translitChars.put("ᵱ", "p");
            translitChars.put("ṁ", "m");
            translitChars.put("á", "a");
            translitChars.put("ᴎ", "n");
            translitChars.put("ꝟ", "v");
            translitChars.put("è", "e");
            translitChars.put("ᶎ", "z");
            translitChars.put("ꝺ", "d");
            translitChars.put("ᶈ", "p");
            translitChars.put("ɫ", "l");
            translitChars.put("ᴢ", "z");
            translitChars.put("ɱ", "m");
            translitChars.put("ṝ", "r");
            translitChars.put("ṽ", "v");
            translitChars.put("ũ", "u");
            translitChars.put("ß", "ss");
            translitChars.put("ĥ", "h");
            translitChars.put("ᵵ", "t");
            translitChars.put("ʐ", "z");
            translitChars.put("ṟ", "r");
            translitChars.put("ɲ", "n");
            translitChars.put("à", "a");
            translitChars.put("ẙ", "y");
            translitChars.put("ỳ", "y");
            translitChars.put("ᴔ", "oe");
            translitChars.put("ₓ", "x");
            translitChars.put("ȗ", "u");
            translitChars.put("ⱼ", "j");
            translitChars.put("ẫ", "a");
            translitChars.put("ʑ", "z");
            translitChars.put("ẛ", "s");
            translitChars.put("ḭ", "i");
            translitChars.put("ꜵ", "ao");
            translitChars.put("ɀ", "z");
            translitChars.put("ÿ", "y");
            translitChars.put("ǝ", "e");
            translitChars.put("ǭ", "o");
            translitChars.put("ᴅ", "d");
            translitChars.put("ᶅ", "l");
            translitChars.put("ù", "u");
            translitChars.put("ạ", "a");
            translitChars.put("ḅ", "b");
            translitChars.put("ụ", "u");
            translitChars.put("ằ", "a");
            translitChars.put("ᴛ", "t");
            translitChars.put("ƴ", "y");
            translitChars.put("ⱦ", "t");
            translitChars.put("ⱡ", "l");
            translitChars.put("ȷ", "j");
            translitChars.put("ᵶ", "z");
            translitChars.put("ḫ", "h");
            translitChars.put("ⱳ", "w");
            translitChars.put("ḵ", "k");
            translitChars.put("ờ", "o");
            translitChars.put("î", "i");
            translitChars.put("ģ", "g");
            translitChars.put("ȅ", "e");
            translitChars.put("ȧ", "a");
            translitChars.put("ẳ", "a");
            translitChars.put("ɋ", "q");
            translitChars.put("ṭ", "t");
            translitChars.put("ꝸ", "um");
            translitChars.put("ᴄ", "c");
            translitChars.put("ẍ", "x");
            translitChars.put("ủ", "u");
            translitChars.put("ỉ", "i");
            translitChars.put("ᴚ", "r");
            translitChars.put("ś", "s");
            translitChars.put("ꝋ", "o");
            translitChars.put("ỹ", "y");
            translitChars.put("ṡ", "s");
            translitChars.put("ǌ", "nj");
            translitChars.put("ȁ", "a");
            translitChars.put("ẗ", "t");
            translitChars.put("ĺ", "l");
            translitChars.put("ž", "z");
            translitChars.put("ᵺ", "th");
            translitChars.put("ƌ", "d");
            translitChars.put("ș", "s");
            translitChars.put("š", "s");
            translitChars.put("ᶙ", "u");
            translitChars.put("ẽ", "e");
            translitChars.put("ẜ", "s");
            translitChars.put("ɇ", "e");
            translitChars.put("ṷ", "u");
            translitChars.put("ố", "o");
            translitChars.put("ȿ", "s");
            translitChars.put("ᴠ", "v");
            translitChars.put("ꝭ", "is");
            translitChars.put("ᴏ", "o");
            translitChars.put("ɛ", "e");
            translitChars.put("ǻ", "a");
            translitChars.put("ﬄ", "ffl");
            translitChars.put("ⱺ", "o");
            translitChars.put("ȋ", "i");
            translitChars.put("ᵫ", "ue");
            translitChars.put("ȡ", "d");
            translitChars.put("ⱬ", "z");
            translitChars.put("ẁ", "w");
            translitChars.put("ᶏ", "a");
            translitChars.put("ꞇ", "t");
            translitChars.put("ğ", "g");
            translitChars.put("ɳ", "n");
            translitChars.put("ʛ", "g");
            translitChars.put("ᴜ", "u");
            translitChars.put("ẩ", "a");
            translitChars.put("ṅ", "n");
            translitChars.put("ɨ", "i");
            translitChars.put("ᴙ", "r");
            translitChars.put("ǎ", "a");
            translitChars.put("ſ", "s");
            translitChars.put("ȫ", "o");
            translitChars.put("ɿ", "r");
            translitChars.put("ƭ", "t");
            translitChars.put("ḯ", "i");
            translitChars.put("ǽ", "ae");
            translitChars.put("ⱱ", "v");
            translitChars.put("ɶ", "oe");
            translitChars.put("ṃ", "m");
            translitChars.put("ż", "z");
            translitChars.put("ĕ", "e");
            translitChars.put("ꜻ", "av");
            translitChars.put("ở", "o");
            translitChars.put("ễ", "e");
            translitChars.put("ɬ", "l");
            translitChars.put("ị", "i");
            translitChars.put("ᵭ", "d");
            translitChars.put("ﬆ", "st");
            translitChars.put("ḷ", "l");
            translitChars.put("ŕ", "r");
            translitChars.put("ᴕ", "ou");
            translitChars.put("ʈ", "t");
            translitChars.put("ā", "a");
            translitChars.put("ḙ", "e");
            translitChars.put("ᴑ", "o");
            translitChars.put("ç", "c");
            translitChars.put("ᶊ", "s");
            translitChars.put("ặ", "a");
            translitChars.put("ų", "u");
            translitChars.put("ả", "a");
            translitChars.put("ǥ", "g");
            translitChars.put("ꝁ", "k");
            translitChars.put("ẕ", "z");
            translitChars.put("ŝ", "s");
            translitChars.put("ḕ", "e");
            translitChars.put("ɠ", "g");
            translitChars.put("ꝉ", "l");
            translitChars.put("ꝼ", "f");
            translitChars.put("ᶍ", "x");
            translitChars.put("ǒ", "o");
            translitChars.put("ę", "e");
            translitChars.put("ổ", "o");
            translitChars.put("ƫ", "t");
            translitChars.put("ǫ", "o");
            translitChars.put("i̇", "i");
            translitChars.put("ṇ", "n");
            translitChars.put("ć", "c");
            translitChars.put("ᵷ", "g");
            translitChars.put("ẅ", "w");
            translitChars.put("ḑ", "d");
            translitChars.put("ḹ", "l");
            translitChars.put("œ", "oe");
            translitChars.put("ᵳ", "r");
            translitChars.put("ļ", "l");
            translitChars.put("ȑ", "r");
            translitChars.put("ȭ", "o");
            translitChars.put("ᵰ", "n");
            translitChars.put("ᴁ", "ae");
            translitChars.put("ŀ", "l");
            translitChars.put("ä", "a");
            translitChars.put("ƥ", "p");
            translitChars.put("ỏ", "o");
            translitChars.put("į", "i");
            translitChars.put("ȓ", "r");
            translitChars.put("ǆ", "dz");
            translitChars.put("ḡ", "g");
            translitChars.put("ṻ", "u");
            translitChars.put("ō", "o");
            translitChars.put("ľ", "l");
            translitChars.put("ẃ", "w");
            translitChars.put("ț", "t");
            translitChars.put("ń", "n");
            translitChars.put("ɍ", "r");
            translitChars.put("ȃ", "a");
            translitChars.put("ü", "u");
            translitChars.put("ꞁ", "l");
            translitChars.put("ᴐ", "o");
            translitChars.put("ớ", "o");
            translitChars.put("ᴃ", "b");
            translitChars.put("ɹ", "r");
            translitChars.put("ᵲ", "r");
            translitChars.put("ʏ", "y");
            translitChars.put("ᵮ", "f");
            translitChars.put("ⱨ", "h");
            translitChars.put("ŏ", "o");
            translitChars.put("ú", "u");
            translitChars.put("ṛ", "r");
            translitChars.put("ʮ", "h");
            translitChars.put("ó", "o");
            translitChars.put("ů", "u");
            translitChars.put("ỡ", "o");
            translitChars.put("ṕ", "p");
            translitChars.put("ᶖ", "i");
            translitChars.put("ự", "u");
            translitChars.put("ã", "a");
            translitChars.put("ᵢ", "i");
            translitChars.put("ṱ", "t");
            translitChars.put("ể", "e");
            translitChars.put("ử", "u");
            translitChars.put("í", "i");
            translitChars.put("ɔ", "o");
            translitChars.put("ɺ", "r");
            translitChars.put("ɢ", "g");
            translitChars.put("ř", "r");
            translitChars.put("ẖ", "h");
            translitChars.put("ű", "u");
            translitChars.put("ȍ", "o");
            translitChars.put("ḻ", "l");
            translitChars.put("ḣ", "h");
            translitChars.put("ȶ", "t");
            translitChars.put("ņ", "n");
            translitChars.put("ᶒ", "e");
            translitChars.put("ì", "i");
            translitChars.put("ẉ", "w");
            translitChars.put("ē", "e");
            translitChars.put("ᴇ", "e");
            translitChars.put("ł", "l");
            translitChars.put("ộ", "o");
            translitChars.put("ɭ", "l");
            translitChars.put("ẏ", "y");
            translitChars.put("ᴊ", "j");
            translitChars.put("ḱ", "k");
            translitChars.put("ṿ", "v");
            translitChars.put("ȩ", "e");
            translitChars.put("â", "a");
            translitChars.put("ş", "s");
            translitChars.put("ŗ", "r");
            translitChars.put("ʋ", "v");
            translitChars.put("ₐ", "a");
            translitChars.put("ↄ", "c");
            translitChars.put("ᶓ", "e");
            translitChars.put("ɰ", "m");
            translitChars.put("ᴡ", "w");
            translitChars.put("ȏ", "o");
            translitChars.put("č", "c");
            translitChars.put("ǵ", "g");
            translitChars.put("ĉ", "c");
            translitChars.put("ᶗ", "o");
            translitChars.put("ꝃ", "k");
            translitChars.put("ꝙ", "q");
            translitChars.put("ṑ", "o");
            translitChars.put("ꜱ", "s");
            translitChars.put("ṓ", "o");
            translitChars.put("ȟ", "h");
            translitChars.put("ő", "o");
            translitChars.put("ꜩ", "tz");
            translitChars.put("ẻ", "e");
        }
        StringBuilder dst = new StringBuilder(src.length());
        int len = src.length();
        boolean upperCase = false;
        for (int a = 0; a < len; a++) {
            String ch = src.substring(a, a + 1);
            if (onlyEnglish) {
                String lower = ch.toLowerCase();
                upperCase = !ch.equals(lower);
                ch = lower;
            }
            String tch = translitChars.get(ch);
            if (tch == null && ru) {
                tch = ruTranslitChars.get(ch);
            }
            if (tch != null) {
                if (onlyEnglish && upperCase) {
                    if (tch.length() > 1) {
                        tch = tch.substring(0, 1).toUpperCase() + tch.substring(1);
                    } else {
                        tch = tch.toUpperCase();
                    }
                }
                dst.append(tch);
            } else {
                if (onlyEnglish) {
                    char c = ch.charAt(0);
                    if (((c < 'a' || c > 'z') || (c < '0' || c > '9')) && c != ' ' && c != '\'' && c != ',' && c != '.' && c != '&' && c != '-' && c != '/') {
                        return null;
                    }
                    if (upperCase) {
                        ch = ch.toUpperCase();
                    }
                }
                dst.append(ch);
            }
        }
        return dst.toString();
    }

    abstract public static class PluralRules {
        abstract int quantityForNumber(int n);
    }

    public static class PluralRules_Zero extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0 || count == 1) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Welsh extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else if (count == 3) {
                return QUANTITY_FEW;
            } else if (count == 6) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Two extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Tachelhit extends PluralRules {
        public int quantityForNumber(int count) {
            if (count >= 0 && count <= 1) {
                return QUANTITY_ONE;
            } else if (count >= 2 && count <= 10) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Slovenian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (rem100 == 1) {
                return QUANTITY_ONE;
            } else if (rem100 == 2) {
                return QUANTITY_TWO;
            } else if (rem100 >= 3 && rem100 <= 4) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Romanian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (count == 1) {
                return QUANTITY_ONE;
            } else if ((count == 0 || (rem100 >= 1 && rem100 <= 19))) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Polish extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) {
                return QUANTITY_FEW;
            } else if (rem10 >= 0 && rem10 <= 1 || rem10 >= 5 && rem10 <= 9 || rem100 >= 12 && rem100 <= 14) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_One extends PluralRules {
        public int quantityForNumber(int count) {
            return count == 1 ? QUANTITY_ONE : QUANTITY_OTHER;
        }
    }

    public static class PluralRules_None extends PluralRules {
        public int quantityForNumber(int count) {
            return QUANTITY_OTHER;
        }
    }

    public static class PluralRules_Maltese extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 0 || (rem100 >= 2 && rem100 <= 10)) {
                return QUANTITY_FEW;
            } else if (rem100 >= 11 && rem100 <= 19) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Macedonian extends PluralRules {
        public int quantityForNumber(int count) {
            if (count % 10 == 1 && count != 11) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Lithuanian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (rem10 == 1 && !(rem100 >= 11 && rem100 <= 19)) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 9 && !(rem100 >= 11 && rem100 <= 19)) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Latvian extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count % 10 == 1 && count % 100 != 11) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Langi extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_French extends PluralRules {
        public int quantityForNumber(int count) {
            if (count >= 0 && count < 2) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Czech extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (count >= 2 && count <= 4) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Breton extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else if (count == 3) {
                return QUANTITY_FEW;
            } else if (count == 6) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Balkan extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (rem10 == 1 && rem100 != 11) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) {
                return QUANTITY_FEW;
            } else if ((rem10 == 0 || (rem10 >= 5 && rem10 <= 9) || (rem100 >= 11 && rem100 <= 14))) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Serbian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (rem10 == 1 && rem100 != 11) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Arabic extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else if (rem100 >= 3 && rem100 <= 10) {
                return QUANTITY_FEW;
            } else if (rem100 >= 11 && rem100 <= 99) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static String addNbsp(String src) {
        return src.replace(' ', '\u00A0');
    }

    private static Boolean useImperialSystemType;

    public static void resetImperialSystemType() {
        useImperialSystemType = null;
    }

    public static boolean getUseImperialSystemType() {
        ensureImperialSystemInit();
        return useImperialSystemType;
    }

    public static void ensureImperialSystemInit() {
        if (useImperialSystemType != null) {
            return;
        }
        if (SharedConfig.distanceSystemType == 0) {
            try {
                TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    String country = telephonyManager.getSimCountryIso().toUpperCase();
                    useImperialSystemType = "US".equals(country) || "GB".equals(country) || "MM".equals(country) || "LR".equals(country);
                }
            } catch (Exception e) {
                useImperialSystemType = false;
                FileLog.e(e);
            }
        } else {
            useImperialSystemType = SharedConfig.distanceSystemType == 2;
        }
    }

    public static String formatDistance(float distance, int type) {
        return formatDistance(distance, type, null);
    }

    // patch to force reinstalling of langpack in case some strings are missing after 9.0
    private boolean shouldReinstallLangpack(String lng) {
        int mustBeCount = MessagesController.getInstance(UserConfig.selectedAccount).checkResetLangpack;
        if (mustBeCount <= 0) {
            return false;
        }
        boolean alreadyPatched = MessagesController.getGlobalMainSettings().getBoolean("lngpack_patched_" + lng, false);
        if (alreadyPatched) {
            return false;
        }
        int count = calculateTranslatedCount(localeValues);
        if (count >= mustBeCount) {
            return false;
        }
        FileLog.e("reinstalling " + lng + " langpack because of patch (" + count + " keys, must be at least " + mustBeCount + ")");
        patched(lng);
        return true;
    }

    public static String formatDistance(float distance, int type, Boolean useImperial) {
        ensureImperialSystemInit();
        boolean imperial = useImperial != null && useImperial || useImperial == null && useImperialSystemType;
        if (imperial) {
            distance *= 3.28084f;
            if (distance < 1000) {
                switch (type) {
                    case 0:
                        return formatString("FootsAway", R.string.FootsAway, String.format("%d", (int) Math.max(1, distance)));
                    case 1:
                        return formatString("FootsFromYou", R.string.FootsFromYou, String.format("%d", (int) Math.max(1, distance)));
                    case 2:
                    default:
                        return formatString("FootsShort", R.string.FootsShort, String.format("%d", (int) Math.max(1, distance)));
                }
            } else {
                String arg;
                if (distance % 5280 == 0) {
                    arg = String.format("%d", (int) (distance / 5280));
                } else {
                    arg = String.format("%.2f", distance / 5280.0f);
                }
                switch (type) {
                    case 0:
                        return formatString("MilesAway", R.string.MilesAway, arg);
                    case 1:
                        return formatString("MilesFromYou", R.string.MilesFromYou, arg);
                    default:
                    case 2:
                        return formatString("MilesShort", R.string.MilesShort, arg);
                }

            }
        } else {
            if (distance < 1000) {
                switch (type) {
                    case 0:
                        return formatString("MetersAway2", R.string.MetersAway2, String.format("%d", (int) Math.max(1, distance)));
                    case 1:
                        return formatString("MetersFromYou2", R.string.MetersFromYou2, String.format("%d", (int) Math.max(1, distance)));
                    case 2:
                    default:
                        return formatString("MetersShort", R.string.MetersShort, String.format("%d", (int) Math.max(1, distance)));
                }
            } else {
                String arg;
                if (distance % 1000 == 0) {
                    arg = String.format("%d", (int) (distance / 1000));
                } else {
                    arg = String.format("%.2f", distance / 1000.0f);
                }
                switch (type) {
                    case 0:
                        return formatString("KMetersAway2", R.string.KMetersAway2, arg);
                    case 1:
                        return formatString("KMetersFromYou2", R.string.KMetersFromYou2, arg);
                    case 2:
                    default:
                        return formatString("KMetersShort", R.string.KMetersShort, arg);
                }
            }
        }
    }

    private void patched(String lng) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("set as patched " + lng + " langpack");
        }
        MessagesController.getGlobalMainSettings().edit().putBoolean("lngpack_patched_" + lng, true).apply();
    }
}
