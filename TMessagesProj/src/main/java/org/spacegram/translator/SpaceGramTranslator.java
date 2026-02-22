package org.spacegram.translator;

import android.util.LruCache;
import org.spacegram.SpaceGramConfig;
import org.telegram.messenger.Utilities;
import java.util.HashMap;
import java.util.Map;

public class SpaceGramTranslator {

    private static final SpaceGramTranslator INSTANCE = new SpaceGramTranslator();
    private final LruCache<String, String> cache = new LruCache<>(200);
    private final Map<Integer, BaseTranslator> providers = new HashMap<>();

    private SpaceGramTranslator() {
        providers.put(1, new GoogleTranslator());
        // Add more providers here
    }

    public static SpaceGramTranslator getInstance() {
        return INSTANCE;
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
            provider = providers.get(1); // Default to Google
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
