package app.okgram.locale;

import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.Locale;

public class OkgramLocale {

    private boolean isAddBusinessLanguage;

    private OkgramLocale() {

    }

    public static OkgramLocale getInstance() {
        return OkGramLocaleHolder.singleOkgramLocale;
    }

    public static class OkGramLocaleHolder {
        private static final OkgramLocale singleOkgramLocale = new OkgramLocale();
    }

    public ArrayList<LocaleController.LocaleInfo> addBusinessLanguage() {
        if (isAddBusinessLanguage) {
            return null;
        }
        isAddBusinessLanguage = true;

        ArrayList<LocaleController.LocaleInfo> localeInfos = new ArrayList<>();


        LocaleController.LocaleInfo localeInfo;

        localeInfo = new LocaleController.LocaleInfo();
        localeInfo.name = "简体中文";
        localeInfo.nameEnglish = "Chinese (Simplified)";
        localeInfo.shortName = "zh_hans_raw";
        localeInfo.pluralLangCode = "zhcn";
        localeInfo.pathToFile = "unofficial";
        localeInfo.builtIn = false;
        localeInfos.add(localeInfo);

        localeInfo = new LocaleController.LocaleInfo();
        localeInfo.name = "正體中文";
        localeInfo.nameEnglish = "Chinese (zh-Hant-TW)";
        localeInfo.shortName = "zh_hant_raw";
        localeInfo.pluralLangCode = "zh";
        localeInfo.pathToFile = "unofficial";
        localeInfo.builtIn = false;

        localeInfos.add(localeInfo);

        return localeInfos;
    }

    public String getCurrentSystemLanguage(Locale systemDefaultLocale){
        if (systemDefaultLocale == null){
            return null;
        }
        String language = systemDefaultLocale.getLanguage();
        if (language.contains("zh")) {
            String country = systemDefaultLocale.getCountry();
            if (country.contains("CN")) {
                language = "zhcn";
            }
        }
        return language;
    }
}
