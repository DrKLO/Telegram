package org.spacegram.translator;

import org.telegram.messenger.Utilities;

public interface BaseTranslator {
    void translate(String text, String fromLang, String toLang, Utilities.Callback2<String, Boolean> done);
}
