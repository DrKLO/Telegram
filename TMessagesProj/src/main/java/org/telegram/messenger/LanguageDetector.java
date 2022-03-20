package org.telegram.messenger;

public class LanguageDetector {
    public interface StringCallback {
        void run(String str);
    }
    public interface ExceptionCallback {
        void run(Exception e);
    }

    public static boolean hasSupport() {
        return true;
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
        try {
            com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
                    .identifyLanguage(text)
                    .addOnSuccessListener(str -> {
                        onSuccess.run(str);
                    })
                    .addOnFailureListener(e -> {
                        onFail.run(e);
                    });
        } catch (Exception e) {
            FileLog.e(e);
            onFail.run(e);
        }
    }
}
