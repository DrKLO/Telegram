package org.spacegram.translator;

import org.spacegram.SpaceGramConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.TranslateAlert2;

/**
 * Helper class to handle translation based on SpaceGramConfig.translateStyle
 * - Style 0: Translate inline (in message)
 * - Style 1: Translate in popup
 */
public class TranslationHelper {

    /**
     * Translate a message respecting the user's style preference
     * @param messageObject The message to translate
     * @param toLanguage Target language
     * @param currentAccount Current account ID
     * @param onComplete Callback when translation is complete
     */
    public static void translateMessage(
        MessageObject messageObject,
        String toLanguage,
        int currentAccount,
        Runnable onComplete
    ) {
        if (messageObject == null || messageObject.messageOwner == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        String text = messageObject.messageOwner.message;
        if (text == null || text.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Detect source language
        LanguageDetector.detectLanguage(text, detectedLang -> {
            translateWithStyle(messageObject, text, detectedLang, toLanguage, currentAccount, onComplete);
        }, error -> {
            // If detection fails, assume auto
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
        // Check translation style
        if (SpaceGramConfig.translateStyle == 0) {
            // Style 0: Translate inline (in message)
            translateInline(messageObject, text, fromLang, toLang, currentAccount, onComplete);
        } else {
            // Style 1: Translate in popup (show TranslateAlert2)
            translateInPopup(messageObject, text, fromLang, toLang, onComplete);
        }
    }

    /**
     * Translate inline: Store translation in messageObject and notify UI
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
            if (result != null) {
                // Store translation in message object
                messageObject.messageOwner.translatedText = result;
                messageObject.messageOwner.translatedFromLanguage = fromLang;
                messageObject.messageOwner.translatedToLanguage = toLang;
                
                // Mark as translated
                messageObject.translated = true;
                
                // Notify UI to update
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount)
                        .postNotificationName(NotificationCenter.messageTranslated, 
                            messageObject.getDialogId(), 
                            messageObject.getId());
                    
                    if (onComplete != null) onComplete.run();
                });
            } else {
                // Translation failed
                AndroidUtilities.runOnUIThread(() -> {
                    if (onComplete != null) onComplete.run();
                });
            }
        });
    }

    /**
     * Translate in popup: Show TranslateAlert2
     */
    private static void translateInPopup(
        MessageObject messageObject,
        String text,
        String fromLang,
        String toLang,
        Runnable onComplete
    ) {
        // For popup, we just call the standard translation through TranslateAlert2
        // The popup will be shown by the calling code
        // This is a placeholder - the actual popup is shown elsewhere
        if (onComplete != null) {
            AndroidUtilities.runOnUIThread(onComplete);
        }
    }

    /**
     * Check if a message is currently translated
     */
    public static boolean isTranslated(MessageObject messageObject) {
        return messageObject != null && 
               messageObject.messageOwner != null && 
               messageObject.translated &&
               messageObject.messageOwner.translatedText != null &&
               !messageObject.messageOwner.translatedText.isEmpty();
    }

    /**
     * Get translated text from message
     */
    public static String getTranslatedText(MessageObject messageObject) {
        if (isTranslated(messageObject)) {
            return messageObject.messageOwner.translatedText;
        }
        return null;
    }

    /**
     * Clear translation from message (show original)
     */
    public static void clearTranslation(MessageObject messageObject, int currentAccount) {
        if (messageObject != null && messageObject.messageOwner != null) {
            messageObject.messageOwner.translatedText = null;
            messageObject.messageOwner.translatedFromLanguage = null;
            messageObject.messageOwner.translatedToLanguage = null;
            messageObject.translated = false;
            
            // Notify UI to update
            NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.messageTranslated, 
                    messageObject.getDialogId(), 
                    messageObject.getId());
        }
    }
}
