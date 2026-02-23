package org.spacegram.translator;

import android.util.LruCache;
import org.spacegram.SpaceGramConfig;
import org.telegram.messenger.Utilities;
import java.util.HashMap;
import java.util.Map;

public class SpaceGramTranslator {

    // Provider constants
    public static final int PROVIDER_GOOGLE = 1;
    public static final int PROVIDER_DEEPL = 2;
    public static final int PROVIDER_YANDEX = 3;
    public static final int PROVIDER_MICROSOFT = 4;
    public static final int PROVIDER_LIBRETRANSLATE = 5;
    public static final int PROVIDER_MYMEMORY = 6;

    private static final SpaceGramTranslator INSTANCE = new SpaceGramTranslator();
    private final LruCache<String, String> cache = new LruCache<>(200);
    private final Map<Integer, BaseTranslator> providers = new HashMap<>();

    private SpaceGramTranslator() {
        providers.put(PROVIDER_GOOGLE, new GoogleTranslator());
        providers.put(PROVIDER_DEEPL, new DeepLTranslator());
        providers.put(PROVIDER_YANDEX, new YandexTranslator());
        providers.put(PROVIDER_MICROSOFT, new MicrosoftTranslator());
        providers.put(PROVIDER_LIBRETRANSLATE, new LibreTranslateTranslator());
        providers.put(PROVIDER_MYMEMORY, new MyMemoryTranslator());
    }

    public static SpaceGramTranslator getInstance() {
        return INSTANCE;
    }

    public static String getProviderName(int provider) {
        switch (provider) {
            case PROVIDER_GOOGLE:
                return "Google Translate";
            case PROVIDER_DEEPL:
                return "DeepL";
            case PROVIDER_YANDEX:
                return "Yandex";
            case PROVIDER_MICROSOFT:
                return "Microsoft Translator";
            case PROVIDER_LIBRETRANSLATE:
                return "LibreTranslate";
            case PROVIDER_MYMEMORY:
                return "MyMemory";
            default:
                return "Google Translate";
        }
    }

    public static String[] getAllProviderNames() {
        return new String[]{
            "Google Translate",
            "DeepL",
            "Yandex",
            "Microsoft Translator",
            "LibreTranslate",
            "MyMemory"
        };
    }

    public static int[] getAllProviderIds() {
        return new int[]{
            PROVIDER_GOOGLE,
            PROVIDER_DEEPL,
            PROVIDER_YANDEX,
            PROVIDER_MICROSOFT,
            PROVIDER_LIBRETRANSLATE,
            PROVIDER_MYMEMORY
        };
    }

    public void translate(String text, String fromLang, String toLang, Utilities.Callback2<String, Boolean> done) {
        if (text == null || text.isEmpty()) {
            if (done != null) done.run(null, false);
            return;
        }

        String cacheKey = (fromLang != null ? fromLang : "auto") + "_" + toLang + "_" + text.hashCode();
        String cached = cache.get(cacheKey);
        if (cached != null) {
            if (done != null) done.run(cached, false);
            return;
        }

        BaseTranslator provider = providers.get(SpaceGramConfig.translateProvider);
        if (provider == null) {
            provider = providers.get(PROVIDER_GOOGLE); // Default to Google
        }

        if (provider != null) {
            provider.translate(text, fromLang, toLang, (result, rateLimit) -> {
                if (result != null) {
                    cache.put(cacheKey, result);
                }
                if (done != null) {
                    done.run(result, rateLimit);
                }
            });
        } else {
            if (done != null) done.run(null, false);
        }
    }
}
