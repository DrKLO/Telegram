package org.spacegram.translator;

import org.spacegram.SpaceGramConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

/**
 * Helper class to handle translation based on SpaceGramConfig.translateStyle.
 */
public class TranslationHelper {

    /**
     * Translate a message respecting the user's style preference.
     */
    public static void translateMessage(
        MessageObject messageObject,
        String toLanguage,
        int currentAccount,
        Runnable onComplete
    ) {
        if (messageObject == null || messageObject.messageOwner == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String text = messageObject.messageOwner.message;
        if (text == null || text.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        LanguageDetector.detectLanguage(text, detectedLang -> {
            translateWithStyle(messageObject, text, detectedLang, toLanguage, currentAccount, onComplete);
        }, error -> {
            translateWithStyle(messageObject, text, "auto", toLanguage, currentAccount, onComplete);
        });
    }

    private static void translateWithStyle(
        MessageObject messageObject,
        String text,
        String fromLang,
        String toLang,
        int currentAccount,
        Runnable onComplete
    ) {
        if (SpaceGramConfig.translateStyle == 0) {
            translateInline(messageObject, text, fromLang, toLang, currentAccount, onComplete);
        } else if (onComplete != null) {
            AndroidUtilities.runOnUIThread(onComplete);
        }
    }

    /**
     * Translate inline: Store translation in messageObject and notify UI.
     */
    private static void translateInline(
        MessageObject messageObject,
        String text,
        String fromLang,
        String toLang,
        int currentAccount,
        Runnable onComplete
    ) {
        SpaceGramTranslator.getInstance().translate(text, fromLang, toLang, (result, rateLimit) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null && messageObject.messageOwner != null) {
                    TLRPC.TL_textWithEntities translatedText = new TLRPC.TL_textWithEntities();
                    translatedText.text = result;
                    translatedText.entities = messageObject.messageOwner.entities;

                    messageObject.messageOwner.translatedText = translatedText;
                    messageObject.messageOwner.translatedToLanguage = toLang;
                    messageObject.translated = true;

                    NotificationCenter.getInstance(currentAccount)
                        .postNotificationName(NotificationCenter.messageTranslated,
                            messageObject.getDialogId(),
                            messageObject.getId());
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    /**
     * Check if a message is currently translated.
     */
    public static boolean isTranslated(MessageObject messageObject) {
        return messageObject != null
            && messageObject.messageOwner != null
            && messageObject.translated
            && messageObject.messageOwner.translatedText != null
            && messageObject.messageOwner.translatedText.text != null
            && !messageObject.messageOwner.translatedText.text.isEmpty();
    }

    /**
     * Get translated text from message.
     */
    public static String getTranslatedText(MessageObject messageObject) {
        if (isTranslated(messageObject)) {
            return messageObject.messageOwner.translatedText.text;
        }
        return null;
    }

    /**
     * Clear translation from message (show original).
     */
    public static void clearTranslation(MessageObject messageObject, int currentAccount) {
        if (messageObject != null && messageObject.messageOwner != null) {
            messageObject.messageOwner.translatedText = null;
            messageObject.messageOwner.translatedToLanguage = null;
            messageObject.translated = false;

            NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.messageTranslated,
                    messageObject.getDialogId(),
                    messageObject.getId());
        }
    }
}
