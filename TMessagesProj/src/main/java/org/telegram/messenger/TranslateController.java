package org.telegram.messenger;

import android.content.Context;
import android.content.res.Resources;
import android.icu.text.Collator;
import android.text.TextUtils;
import android.util.Pair;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.Nullable;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class TranslateController extends BaseController {

    public static final String UNKNOWN_LANGUAGE = "und";

    private static final int REQUIRED_TOTAL_MESSAGES_CHECKED = 8;
    private static final float REQUIRED_PERCENTAGE_MESSAGES_TRANSLATABLE = .60F;
    private static final float REQUIRED_MIN_PERCENTAGE_MESSAGES_UNKNOWN = .65F;

    private static final int MAX_SYMBOLS_PER_REQUEST = 25000;
    private static final int MAX_MESSAGES_PER_REQUEST = 20;
    private static final int GROUPING_TRANSLATIONS_TIMEOUT = 80;

    private final Set<Long> translatingDialogs = new HashSet<>();
    private final Set<Long> translatableDialogs = new HashSet<>();
    private final HashMap<Long, TranslatableDecision> translatableDialogMessages = new HashMap<>();
    private final HashMap<Long, String> translateDialogLanguage = new HashMap<>();
    private final HashMap<Long, String> detectedDialogLanguage = new HashMap<>();
    private final HashMap<Long, HashMap<Integer, MessageObject>> keptReplyMessageObjects = new HashMap<>();
    private final Set<Long> hideTranslateDialogs = new HashSet<>();

    static class TranslatableDecision {
        Set<Integer> certainlyTranslatable = new HashSet<>();
        Set<Integer> unknown = new HashSet<>();
        Set<Integer> certainlyNotTranslatable = new HashSet<>();
    }

    private MessagesController messagesController;

    public TranslateController(MessagesController messagesController) {
        super(messagesController.currentAccount);
        this.messagesController = messagesController;

        AndroidUtilities.runOnUIThread(this::loadTranslatingDialogsCached, 150);
    }

    public boolean isFeatureAvailable() {
        return isChatTranslateEnabled() && UserConfig.getInstance(currentAccount).isPremium();
    }

    private Boolean chatTranslateEnabled;
    private Boolean contextTranslateEnabled;

    public boolean isChatTranslateEnabled() {
        if (chatTranslateEnabled == null) {
            chatTranslateEnabled = messagesController.getMainSettings().getBoolean("translate_chat_button", true);
        }
        return chatTranslateEnabled;
    }

    public boolean isContextTranslateEnabled() {
        if (contextTranslateEnabled == null) {
            contextTranslateEnabled = messagesController.getMainSettings().getBoolean("translate_button", MessagesController.getGlobalMainSettings().getBoolean("translate_button", false));
        }
        return contextTranslateEnabled;
    }

    public void setContextTranslateEnabled(boolean enable) {
        messagesController.getMainSettings().edit().putBoolean("translate_button", contextTranslateEnabled = enable).apply();
    }

    public void setChatTranslateEnabled(boolean enable) {
        messagesController.getMainSettings().edit().putBoolean("translate_chat_button", chatTranslateEnabled = enable).apply();
    }

    public static boolean isTranslatable(MessageObject messageObject) {
        return (
            messageObject != null && messageObject.messageOwner != null &&
            !messageObject.isOutOwner() &&
            !messageObject.isRestrictedMessage &&
            !messageObject.isSponsored() &&
            (
                messageObject.type == MessageObject.TYPE_TEXT ||
                messageObject.type == MessageObject.TYPE_VIDEO ||
                messageObject.type == MessageObject.TYPE_PHOTO ||
                messageObject.type == MessageObject.TYPE_VOICE ||
                messageObject.type == MessageObject.TYPE_FILE ||
                messageObject.type == MessageObject.TYPE_MUSIC ||
                messageObject.type == MessageObject.TYPE_POLL
            ) && (
                !TextUtils.isEmpty(messageObject.messageOwner.message) ||
                MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaPoll
            )
        );
    }

    public boolean isDialogTranslatable(long dialogId) {
        return (
            isFeatureAvailable() &&
            !DialogObject.isEncryptedDialog(dialogId) &&
            getUserConfig().getClientUserId() != dialogId &&
            /* DialogObject.isChatDialog(dialogId) &&*/
            translatableDialogs.contains(dialogId)
        );
    }

    public boolean isTranslateDialogHidden(long dialogId) {
        if (hideTranslateDialogs.contains(dialogId)) {
            return true;
        }
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
        if (chatFull != null) {
            return chatFull.translations_disabled;
        }
        TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
        if (userFull != null) {
            return userFull.translations_disabled;
        }
        return false;
    }

    public boolean isTranslatingDialog(long dialogId) {
        return isFeatureAvailable() && translatingDialogs.contains(dialogId);
    }

    public void toggleTranslatingDialog(long dialogId) {
        toggleTranslatingDialog(dialogId, !isTranslatingDialog(dialogId));
    }

    public boolean toggleTranslatingDialog(long dialogId, boolean value) {
        boolean currentValue = isTranslatingDialog(dialogId), notified = false;
        if (value && !currentValue) {
            translatingDialogs.add(dialogId);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, true);
            notified = true;
        } else if (!value && currentValue) {
            translatingDialogs.remove((Long) dialogId);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, false);
            cancelTranslations(dialogId);
            notified = true;
        }
        saveTranslatingDialogsCache();
        return notified;
    }

    private int hash(MessageObject messageObject) {
        if (messageObject == null) {
            return 0;
        }
        return Objects.hash(messageObject.getDialogId(), messageObject.getId());
    }

    private String currentLanguage() {
        String lang = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
        if (lang != null) {
            lang = lang.split("_")[0];
        }
        return lang;
    }

    public String getDialogTranslateTo(long dialogId) {
        String lang = translateDialogLanguage.get(dialogId);
        if (lang == null) {
            lang = TranslateAlert2.getToLanguage();
            if (lang == null || lang.equals(getDialogDetectedLanguage(dialogId))) {
                lang = currentLanguage();
            }
        }
        if ("nb".equals(lang)) {
            lang = "no";
        }
        return lang;
    }

    public void setDialogTranslateTo(long dialogId, String language) {
        if (TextUtils.equals(getDialogTranslateTo(dialogId), language)) {
            return;
        }

        boolean wasTranslating = isTranslatingDialog(dialogId);

        if (wasTranslating) {
            AndroidUtilities.runOnUIThread(() -> {
                synchronized (TranslateController.this) {
                    translateDialogLanguage.put(dialogId, language);
                    translatingDialogs.add(dialogId);
                    saveTranslatingDialogsCache();
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, true);
            }, 150);
        } else {
            synchronized (TranslateController.this) {
                translateDialogLanguage.put(dialogId, language);
            }
        }

        cancelTranslations(dialogId);
        synchronized (this) {
            translatingDialogs.remove(dialogId);
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, false);

        TranslateAlert2.setToLanguage(language);
    }

    public void updateDialogFull(long dialogId) {
        if (!isFeatureAvailable() || !isDialogTranslatable(dialogId)) {
            return;
        }

        final boolean wasHidden = hideTranslateDialogs.contains(dialogId);

        boolean hidden = false;
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
        if (chatFull != null) {
            hidden = chatFull.translations_disabled;
        } else {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
            if (userFull != null) {
                hidden = userFull.translations_disabled;
            }
        }

        synchronized (this) {
            if (hidden) {
                hideTranslateDialogs.add(dialogId);
                translatingDialogs.remove(dialogId);
            } else {
                hideTranslateDialogs.remove(dialogId);
            }
        }

        if (wasHidden != hidden) {
            saveTranslatingDialogsCache();
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, isTranslatingDialog(dialogId));
        }
    }

    public void setHideTranslateDialog(long dialogId, boolean hide) {
        setHideTranslateDialog(dialogId, hide, false);
    }

    public void setHideTranslateDialog(long dialogId, boolean hide, boolean doNotNotify) {
        TLRPC.TL_messages_togglePeerTranslations req = new TLRPC.TL_messages_togglePeerTranslations();
        req.peer = getMessagesController().getInputPeer(dialogId);
        req.disabled = hide;
        getConnectionsManager().sendRequest(req, null);

        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
        if (chatFull != null) {
            chatFull.translations_disabled = hide;
            getMessagesStorage().updateChatInfo(chatFull, true);
        }
        TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
        if (userFull != null) {
            userFull.translations_disabled = hide;
            getMessagesStorage().updateUserInfo(userFull, true);
        }

        synchronized (this) {
            if (hide) {
                hideTranslateDialogs.add(dialogId);
                translatingDialogs.remove(dialogId);
            } else {
                hideTranslateDialogs.remove(dialogId);
            }
        }
        saveTranslatingDialogsCache();

        if (!doNotNotify) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, isTranslatingDialog(dialogId));
        }
    }

    private static final List<String> languagesOrder = Arrays.asList(
        "en", "ar", "zh", "fr", "de", "it", "ja", "ko", "pt", "ru", "es", "uk"
    );

    private static final List<String> allLanguages = Arrays.asList(
        "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca", "ceb", "zh-cn", "zh", "zh-tw", "co", "hr", "cs", "da", "nl", "en", "eo", "et", "fi", "fr", "fy", "gl", "ka", "de", "el", "gu", "ht", "ha", "haw", "he", "iw", "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja", "jv", "kn", "kk", "km", "rw", "ko", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn", "my", "ne", "no", "ny", "or", "ps", "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st", "sn", "sd", "si", "sk", "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt", "te", "th", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu"
    );

    public static class Language {
        public String code;
        public String displayName;
        public String ownDisplayName;

        public String q;
    }

    public static ArrayList<Language> getLanguages() {
        ArrayList<Language> result = new ArrayList<>();
        for (int i = 0; i < allLanguages.size(); ++i) {
            Language language = new Language();
            language.code = allLanguages.get(i);
            if ("no".equals(language.code)) {
                language.code = "nb";
            }
            language.displayName = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(language.code));
            language.ownDisplayName = TranslateAlert2.capitalFirst(TranslateAlert2.systemLanguageName(language.code, true));
            if (language.displayName == null) {
                continue;
            }
            language.q = (language.displayName + " " + (language.ownDisplayName == null ? "" : language.ownDisplayName)).toLowerCase();
            result.add(language);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Collator collator = Collator.getInstance(Locale.getDefault());
            Collections.sort(result, (lng1, lng2) -> collator.compare(lng1.displayName, lng2.displayName));
        } else {
            Collections.sort(result, Comparator.comparing(lng -> lng.displayName));
        }
        return result;
    }

    private static LinkedHashSet<String> suggestedLanguageCodes = null;
    public static void invalidateSuggestedLanguageCodes() {
        suggestedLanguageCodes = null;
    }
    public static void analyzeSuggestedLanguageCodes() {
        LinkedHashSet<String> langs = new LinkedHashSet<>();
        try {
            langs.add(LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode);
        } catch (Exception e1) {
            FileLog.e(e1);
        }
        try {
            langs.add(Resources.getSystem().getConfiguration().locale.getLanguage());
        } catch (Exception e2) {
            FileLog.e(e2);
        }
        try {
            langs.addAll(RestrictedLanguagesSelectActivity.getRestrictedLanguages());
        } catch (Exception e3) {
            FileLog.e(e3);
        }
        try {
            InputMethodManager imm = (InputMethodManager) ApplicationLoader.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> ims = imm.getEnabledInputMethodList();
            for (InputMethodInfo method : ims) {
                List<InputMethodSubtype> submethods = imm.getEnabledInputMethodSubtypeList(method, true);
                for (InputMethodSubtype submethod : submethods) {
                    if ("keyboard".equals(submethod.getMode())) {
                        String currentLocale = submethod.getLocale();
                        if (currentLocale != null && currentLocale.contains("_")) {
                            currentLocale = currentLocale.split("_")[0];
                        }
                        if (TranslateAlert2.languageName(currentLocale) != null) {
                            langs.add(currentLocale);
                        }
                    }
                }
            }
        } catch (Exception e4) {
            FileLog.e(e4);
        }
        suggestedLanguageCodes = langs;
    }

    public static ArrayList<Language> getSuggestedLanguages(String except) {
        ArrayList<Language> result = new ArrayList<>();
        if (suggestedLanguageCodes == null) {
            analyzeSuggestedLanguageCodes();
            if (suggestedLanguageCodes == null) {
                return result;
            }
        }
        Iterator<String> i = suggestedLanguageCodes.iterator();
        while (i.hasNext()) {
            final String code = i.next();
            if (TextUtils.equals(code, except) || "no".equals(except) && "nb".equals(code) || "nb".equals(except) && "no".equals(code)) {
                continue;
            }
            Language language = new Language();
            language.code = code;
            if ("no".equals(language.code)) {
                language.code = "nb";
            }
            language.displayName = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(language.code));
            language.ownDisplayName = TranslateAlert2.capitalFirst(TranslateAlert2.systemLanguageName(language.code, true));
            if (language.displayName == null) {
                continue;
            }
            language.q = (language.displayName + " " + language.ownDisplayName).toLowerCase();
            result.add(language);
        }
        return result;
    }

    public static ArrayList<LocaleController.LocaleInfo> getLocales() {
        HashMap<String, LocaleController.LocaleInfo> languages = LocaleController.getInstance().languagesDict;
        ArrayList<LocaleController.LocaleInfo> locales = new ArrayList<>(languages.values());
        for (int i = 0; i < locales.size(); ++i) {
            LocaleController.LocaleInfo locale = locales.get(i);
            if (locale == null || locale.shortName != null && locale.shortName.endsWith("_raw") || !"remote".equals(locale.pathToFile)) {
                locales.remove(i);
                i--;
            }
        }

        final LocaleController.LocaleInfo currentLocale = LocaleController.getInstance().getCurrentLocaleInfo();
        Comparator<LocaleController.LocaleInfo> comparator = (o, o2) -> {
            if (o == currentLocale) {
                return -1;
            } else if (o2 == currentLocale) {
                return 1;
            }
            final int index1 = languagesOrder.indexOf(o.pluralLangCode);
            final int index2 = languagesOrder.indexOf(o2.pluralLangCode);
            if (index1 >= 0 && index2 >= 0) {
                return index1 - index2;
            } else if (index1 >= 0) {
                return -1;
            } else if (index2 >= 0) {
                return 1;
            }
            if (o.serverIndex == o2.serverIndex) {
                return o.name.compareTo(o2.name);
            }
            if (o.serverIndex > o2.serverIndex) {
                return 1;
            } else if (o.serverIndex < o2.serverIndex) {
                return -1;
            }
            return 0;
        };
        Collections.sort(locales, comparator);

        return locales;
    }

    public void checkRestrictedLanguagesUpdate() {
        synchronized (this) {
            translatableDialogMessages.clear();

            ArrayList<Long> toNotify = new ArrayList<>();
            HashSet<String> languages = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
            for (long dialogId : translatableDialogs) {
                String language = detectedDialogLanguage.get(dialogId);
                if (language != null && languages.contains(language)) {
                    cancelTranslations(dialogId);
                    translatingDialogs.remove(dialogId);
                    toNotify.add(dialogId);
                }
            }
            translatableDialogs.clear();
            saveTranslatingDialogsCache();

            for (long dialogId : toNotify) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialogId, false);
            }
        }
    }

    @Nullable
    public String getDialogDetectedLanguage(long dialogId) {
        return detectedDialogLanguage.get(dialogId);
    }

    public void checkTranslation(MessageObject messageObject, boolean onScreen) {
        checkTranslation(messageObject, onScreen, false);
    }

    private void checkTranslation(MessageObject messageObject, boolean onScreen, boolean keepReply) {
        if (!isFeatureAvailable()) {
            return;
        }
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }

        long dialogId = messageObject.getDialogId();

        if (!keepReply && messageObject.replyMessageObject != null) {
            checkTranslation(messageObject.replyMessageObject, onScreen, true);
        }

        if (!isTranslatable(messageObject)) {
            return;
        }

        if (!isTranslatingDialog(dialogId)) {
            checkLanguage(messageObject);
            return;
        }

        if (isTranslateDialogHidden(dialogId)) {
            return;
        }

        final String language = getDialogTranslateTo(dialogId);
        MessageObject potentialReplyMessageObject;
        if (!keepReply && (messageObject.messageOwner.translatedText == null && messageObject.messageOwner.translatedPoll == null || messageObject.messageOwner.translatedPoll != null && !PollText.isFullyTranslated(messageObject, messageObject.messageOwner.translatedPoll) || !language.equals(messageObject.messageOwner.translatedToLanguage)) && (potentialReplyMessageObject = findReplyMessageObject(dialogId, messageObject.getId())) != null) {
            messageObject.messageOwner.translatedToLanguage = potentialReplyMessageObject.messageOwner.translatedToLanguage;
            messageObject.messageOwner.translatedText = potentialReplyMessageObject.messageOwner.translatedText;
            messageObject.messageOwner.translatedPoll = potentialReplyMessageObject.messageOwner.translatedPoll;
            messageObject = potentialReplyMessageObject;
        }

        if (onScreen && isTranslatingDialog(dialogId)) {
            final MessageObject finalMessageObject = messageObject;
            if (finalMessageObject.messageOwner.translatedText == null && finalMessageObject.messageOwner.translatedPoll == null || finalMessageObject.messageOwner.translatedPoll != null && !PollText.isFullyTranslated(finalMessageObject, finalMessageObject.messageOwner.translatedPoll) || !language.equals(finalMessageObject.messageOwner.translatedToLanguage)) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslating, finalMessageObject);
                if (MessageObject.getMedia(finalMessageObject) instanceof TLRPC.TL_messageMediaPoll) {
                    pushPollToTranslate(finalMessageObject, language, (id, poll, lang) -> {
                        if (finalMessageObject.getId() != id) {
                            FileLog.e("wtf, asked to translate " + finalMessageObject.getId() + " poll but got " + id + "!");
                        }
                        finalMessageObject.messageOwner.translatedToLanguage = lang;
                        finalMessageObject.messageOwner.translatedText = null;
                        finalMessageObject.messageOwner.translatedPoll = poll;
                        if (keepReply) {
                            keepReplyMessage(finalMessageObject);
                        }

                        getMessagesStorage().updateMessageCustomParams(dialogId, finalMessageObject.messageOwner);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslated, finalMessageObject);

                        ArrayList<MessageObject> dialogMessages = messagesController.dialogMessage.get(dialogId);
                        if (dialogMessages != null) {
                            for (int i = 0; i < dialogMessages.size(); ++i) {
                                MessageObject dialogMessage = dialogMessages.get(i);
                                if (dialogMessage != null && dialogMessage.getId() == finalMessageObject.getId()) {
                                    dialogMessage.messageOwner.translatedToLanguage = lang;
                                    dialogMessage.messageOwner.translatedText = null;
                                    dialogMessage.messageOwner.translatedPoll = poll;
                                    if (dialogMessage.updateTranslation()) {
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                                    }
                                    break;
                                }
                            }
                        }
                    });
                } else {
                    pushToTranslate(finalMessageObject, language, (id, text, lang) -> {
                        if (finalMessageObject.getId() != id) {
                            FileLog.e("wtf, asked to translate " + finalMessageObject.getId() + " but got " + id + "!");
                        }
                        finalMessageObject.messageOwner.translatedToLanguage = lang;
                        finalMessageObject.messageOwner.translatedText = text;
                        finalMessageObject.messageOwner.translatedPoll = null;
                        if (keepReply) {
                            keepReplyMessage(finalMessageObject);
                        }

                        getMessagesStorage().updateMessageCustomParams(dialogId, finalMessageObject.messageOwner);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslated, finalMessageObject);

                        ArrayList<MessageObject> dialogMessages = messagesController.dialogMessage.get(dialogId);
                        if (dialogMessages != null) {
                            for (int i = 0; i < dialogMessages.size(); ++i) {
                                MessageObject dialogMessage = dialogMessages.get(i);
                                if (dialogMessage != null && dialogMessage.getId() == finalMessageObject.getId()) {
                                    dialogMessage.messageOwner.translatedToLanguage = lang;
                                    dialogMessage.messageOwner.translatedText = text;
                                    dialogMessage.messageOwner.translatedPoll = null;
                                    if (dialogMessage.updateTranslation()) {
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                                    }
                                    break;
                                }
                            }
                        }
                    });
                }
            } else if (keepReply) {
                keepReplyMessage(messageObject);
            }
        }
    }

    public void invalidateTranslation(MessageObject messageObject) {
        if (!isFeatureAvailable()) {
            return;
        }
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        final long dialogId = messageObject.getDialogId();
        messageObject.messageOwner.translatedToLanguage = null;
        messageObject.messageOwner.translatedText = null;
        messageObject.messageOwner.translatedPoll = null;
        getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslated, messageObject, isTranslatingDialog(dialogId));
        });
    }

    public void checkDialogMessage(long dialogId) {
        if (isFeatureAvailable()) {
            checkDialogMessageSure(dialogId);
        }
    }

    public void checkDialogMessageSure(long dialogId) {
        if (!translatingDialogs.contains(dialogId)) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            final ArrayList<MessageObject> dialogMessages = messagesController.dialogMessage.get(dialogId);
            if (dialogMessages == null) {
                return;
            }
            ArrayList<TLRPC.Message> customProps = new ArrayList<>();
            for (int i = 0; i < dialogMessages.size(); ++i) {
                MessageObject dialogMessage = dialogMessages.get(i);
                if (dialogMessage == null || dialogMessage.messageOwner == null) {
                    customProps.add(null);
                    continue;
                }
                customProps.add(getMessagesStorage().getMessageWithCustomParamsOnlyInternal(dialogMessage.getId(), dialogMessage.getDialogId()));
            }
            AndroidUtilities.runOnUIThread(() -> {
                boolean updated = false;
                for (int i = 0; i < Math.min(customProps.size(), dialogMessages.size()); ++i) {
                    MessageObject dialogMessage = dialogMessages.get(i);
                    TLRPC.Message props = customProps.get(i);
                    if (dialogMessage == null || dialogMessage.messageOwner == null || props == null) {
                        continue;
                    }
                    dialogMessage.messageOwner.translatedText = props.translatedText;
                    dialogMessage.messageOwner.translatedPoll = props.translatedPoll;
                    dialogMessage.messageOwner.translatedToLanguage = props.translatedToLanguage;
                    if (dialogMessage.updateTranslation(false)) {
                        updated = true;
                    }
                }
                if (updated) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                }
            });
        });
    }


    public void cleanup() {
        cancelAllTranslations();
        resetTranslatingDialogsCache();

        translatingDialogs.clear();
        translatableDialogs.clear();
        translatableDialogMessages.clear();
        translateDialogLanguage.clear();
        detectedDialogLanguage.clear();
        keptReplyMessageObjects.clear();
        hideTranslateDialogs.clear();
        loadingTranslations.clear();
    }

    private ArrayList<Integer> pendingLanguageChecks = new ArrayList<>();
    private void checkLanguage(MessageObject messageObject) {
        if (!LanguageDetector.hasSupport()) {
            return;
        }
        if (!isTranslatable(messageObject) || messageObject.messageOwner == null || TextUtils.isEmpty(messageObject.messageOwner.message)) {
            return;
        }
        if (messageObject.messageOwner.originalLanguage != null) {
            checkDialogTranslatable(messageObject);
            return;
        }

        final long dialogId = messageObject.getDialogId();
        final int hash = hash(messageObject);
        if (isDialogTranslatable(dialogId)) {
            return;
        }
        if (pendingLanguageChecks.contains(hash)) {
            return;
        }

        pendingLanguageChecks.add(hash);

        Utilities.stageQueue.postRunnable(() -> {
            LanguageDetector.detectLanguage(messageObject.messageOwner.message, lng -> AndroidUtilities.runOnUIThread(() -> {
                String detectedLanguage = lng;
                if (detectedLanguage == null) {
                    detectedLanguage = UNKNOWN_LANGUAGE;
                }
                messageObject.messageOwner.originalLanguage = detectedLanguage;
                getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
                pendingLanguageChecks.remove((Integer) hash);
                checkDialogTranslatable(messageObject);
            }), err -> AndroidUtilities.runOnUIThread(() -> {
                messageObject.messageOwner.originalLanguage = UNKNOWN_LANGUAGE;
                getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
                pendingLanguageChecks.remove((Integer) hash);
            }));
        });
    }

    private void checkDialogTranslatable(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }

        final long dialogId = messageObject.getDialogId();
        TranslatableDecision translatableMessages = translatableDialogMessages.get(dialogId);
        if (translatableMessages == null) {
            translatableDialogMessages.put(dialogId, translatableMessages = new TranslatableDecision());
        }

        final boolean isUnknown = isTranslatable(messageObject) && (
            messageObject.messageOwner.originalLanguage == null ||
            UNKNOWN_LANGUAGE.equals(messageObject.messageOwner.originalLanguage)
        );
        final boolean translatable = (
            isTranslatable(messageObject) &&
            messageObject.messageOwner.originalLanguage != null &&
            !UNKNOWN_LANGUAGE.equals(messageObject.messageOwner.originalLanguage) &&
            !RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(messageObject.messageOwner.originalLanguage)
//            !TextUtils.equals(getDialogTranslateTo(dialogId), messageObject.messageOwner.originalLanguage)
        );

        if (isUnknown) {
            translatableMessages.unknown.add(messageObject.getId());
        } else {
            (translatable ? translatableMessages.certainlyTranslatable : translatableMessages.certainlyNotTranslatable).add(messageObject.getId());
        }

        if (!isUnknown) {
            detectedDialogLanguage.put(dialogId, messageObject.messageOwner.originalLanguage);
        }

        final int translatableCount = translatableMessages.certainlyTranslatable.size();
        final int unknownCount = translatableMessages.unknown.size();
        final int notTranslatableCount = translatableMessages.certainlyNotTranslatable.size();
        final int totalCount = translatableCount + unknownCount + notTranslatableCount;
        if (
            totalCount >= REQUIRED_TOTAL_MESSAGES_CHECKED &&
            (translatableCount / (float) (translatableCount + notTranslatableCount)) >= REQUIRED_PERCENTAGE_MESSAGES_TRANSLATABLE &&
            (unknownCount / (float) totalCount) < REQUIRED_MIN_PERCENTAGE_MESSAGES_UNKNOWN
        ) {
            translatableDialogs.add(dialogId);
            translatableDialogMessages.remove((Long) dialogId);
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogIsTranslatable, dialogId);
            }, 450);
        }
    }

    private final Set<Integer> loadingTranslations = new HashSet<>();
    private final HashMap<Long, ArrayList<PendingTranslation>> pendingTranslations = new HashMap<>();

    private static class PendingTranslation {
        Runnable runnable;
        ArrayList<Integer> messageIds = new ArrayList<>();
        ArrayList<TLRPC.TL_textWithEntities> messageTexts = new ArrayList<>();
        ArrayList<Utilities.Callback3<Integer, TLRPC.TL_textWithEntities, String>> callbacks = new ArrayList<>();
        String language;

        int delay = GROUPING_TRANSLATIONS_TIMEOUT;
        int symbolsCount;

        int reqId = -1;
    }

    private void pushToTranslate(
        MessageObject message,
        String language,
        Utilities.Callback3<Integer, TLRPC.TL_textWithEntities, String> callback
    ) {
        if (message == null || message.getId() < 0 || callback == null) {
            return;
        }

        long dialogId = message.getDialogId();

        PendingTranslation pendingTranslation;
        synchronized (this) {
            ArrayList<PendingTranslation> dialogPendingTranslations = pendingTranslations.get(dialogId);
            if (dialogPendingTranslations == null) {
                pendingTranslations.put(dialogId, dialogPendingTranslations = new ArrayList<>());
            }

            if (dialogPendingTranslations.isEmpty()) {
                dialogPendingTranslations.add(pendingTranslation = new PendingTranslation());
            } else {
                pendingTranslation = dialogPendingTranslations.get(dialogPendingTranslations.size() - 1);
            }

            if (pendingTranslation.messageIds.contains(message.getId())) {
                return;
            }

            int messageSymbolsCount = 0;
            if (message.messageOwner != null && message.messageOwner.message != null) {
                messageSymbolsCount = message.messageOwner.message.length();
            } else if (message.caption != null) {
                messageSymbolsCount = message.caption.length();
            } else if (message.messageText != null) {
                messageSymbolsCount = message.messageText.length();
            }

            if (pendingTranslation.symbolsCount + messageSymbolsCount >= MAX_SYMBOLS_PER_REQUEST ||
                pendingTranslation.messageIds.size() + 1 >= MAX_MESSAGES_PER_REQUEST) {
                AndroidUtilities.cancelRunOnUIThread(pendingTranslation.runnable);
                AndroidUtilities.runOnUIThread(pendingTranslation.runnable); // without timeout
                dialogPendingTranslations.add(pendingTranslation = new PendingTranslation());
            }

            if (pendingTranslation.runnable != null) {
                AndroidUtilities.cancelRunOnUIThread(pendingTranslation.runnable);
            }
            loadingTranslations.add(message.getId());
            pendingTranslation.messageIds.add(message.getId());
            TLRPC.TL_textWithEntities source = null;
            if (message.messageOwner != null) {
                source = new TLRPC.TL_textWithEntities();
                source.text = message.messageOwner.message;
                source.entities = message.messageOwner.entities;
            }
            FileLog.d("pending translation +" + message.getId() + " message");
            pendingTranslation.messageTexts.add(source);
            pendingTranslation.callbacks.add(callback);
            pendingTranslation.language = language;
            pendingTranslation.symbolsCount += messageSymbolsCount;
            final PendingTranslation pendingTranslation1 = pendingTranslation;
            pendingTranslation.runnable = () -> {
                synchronized (TranslateController.this) {
                    ArrayList<PendingTranslation> dialogPendingTranslations1 = pendingTranslations.get(dialogId);
                    if (dialogPendingTranslations1 != null) {
                        dialogPendingTranslations1.remove(pendingTranslation1);
                        if (dialogPendingTranslations1.isEmpty()) {
                            pendingTranslations.remove(dialogId);
                        }
                    }
                }

                TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
                req.flags |= 1;
                req.peer = getMessagesController().getInputPeer(dialogId);
                req.id = pendingTranslation1.messageIds;
                req.to_lang = pendingTranslation1.language;

                final int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    final ArrayList<Integer> ids;
                    final ArrayList<Utilities.Callback3<Integer, TLRPC.TL_textWithEntities, String>> callbacks;
                    final ArrayList<TLRPC.TL_textWithEntities> texts;
                    synchronized (TranslateController.this) {
                        ids = pendingTranslation1.messageIds;
                        callbacks = pendingTranslation1.callbacks;
                        texts = pendingTranslation1.messageTexts;
                    }
                    if (res instanceof TLRPC.TL_messages_translateResult) {
                        ArrayList<TLRPC.TL_textWithEntities> translated = ((TLRPC.TL_messages_translateResult) res).result;
                        final int count = Math.min(callbacks.size(), translated.size());
                        for (int i = 0; i < count; ++i) {
                            callbacks.get(i).run(ids.get(i), TranslateAlert2.preprocess(texts.get(i), translated.get(i)), pendingTranslation1.language);
                        }
                    } else if (err != null && "TO_LANG_INVALID".equals(err.text)) {
                        toggleTranslatingDialog(dialogId, false);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.getString(R.string.TranslationFailedAlert2));
                    } else {
                        if (err != null && "QUOTA_EXCEEDED".equals(err.text)) {
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.getString(R.string.TranslationFailedAlert1));
                        }
                        for (int i = 0; i < callbacks.size(); ++i) {
                            callbacks.get(i).run(ids.get(i), null, pendingTranslation1.language);
                        }
                    }
                    synchronized (TranslateController.this) {
                        for (int i = 0; i < ids.size(); ++i) {
                            loadingTranslations.remove(ids.get(i));
                        }
                    }
                }));
                synchronized (TranslateController.this) {
                    pendingTranslation1.reqId = reqId;
                }
            };
            AndroidUtilities.runOnUIThread(pendingTranslation.runnable, pendingTranslation.delay);
            pendingTranslation.delay /= 2;
        }
    }


    private final HashMap<Long, ArrayList<PendingPollTranslation>> pendingPollTranslations = new HashMap<>();

    private static class PendingPollTranslation {
        Runnable runnable;
        ArrayList<Integer> messageIds = new ArrayList<>();
        ArrayList<Pair<PollText, PollText>> messageTexts = new ArrayList<>();
        ArrayList<Utilities.Callback3<Integer, PollText, String>> callbacks = new ArrayList<>();
        String language;

        int delay = GROUPING_TRANSLATIONS_TIMEOUT;
        int symbolsCount;

        int reqId = -1;
    }

    private void pushPollToTranslate(
        MessageObject message,
        String language,
        Utilities.Callback3<Integer, PollText, String> callback
    ) {
        if (message == null || message.getId() < 0 || callback == null) {
            return;
        }

        long dialogId = message.getDialogId();

        PendingPollTranslation pendingTranslation;
        synchronized (this) {
            ArrayList<PendingPollTranslation> dialogPendingTranslations = pendingPollTranslations.get(dialogId);
            if (dialogPendingTranslations == null) {
                pendingPollTranslations.put(dialogId, dialogPendingTranslations = new ArrayList<>());
            }

            if (dialogPendingTranslations.isEmpty()) {
                dialogPendingTranslations.add(pendingTranslation = new PendingPollTranslation());
            } else {
                pendingTranslation = dialogPendingTranslations.get(dialogPendingTranslations.size() - 1);
            }

            if (pendingTranslation.messageIds.contains(message.getId())) {
                return;
            }

            final TLRPC.MessageMedia media = MessageObject.getMedia(message);
            if (!(media instanceof TLRPC.TL_messageMediaPoll)) {
                return;
            }
            final TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) media;
            final PollText pollText = PollText.fromPoll(mediaPoll);
            final PollText translatedText = message.messageOwner.translatedPoll;
            final int messageSymbolsCount = pollText.length();

            if (pendingTranslation.symbolsCount + messageSymbolsCount >= MAX_SYMBOLS_PER_REQUEST ||
                    pendingTranslation.messageIds.size() + 1 >= MAX_MESSAGES_PER_REQUEST) {
                AndroidUtilities.cancelRunOnUIThread(pendingTranslation.runnable);
                AndroidUtilities.runOnUIThread(pendingTranslation.runnable); // without timeout
                dialogPendingTranslations.add(pendingTranslation = new PendingPollTranslation());
            }

            if (pendingTranslation.runnable != null) {
                AndroidUtilities.cancelRunOnUIThread(pendingTranslation.runnable);
            }
            loadingTranslations.add(message.getId());
            pendingTranslation.messageIds.add(message.getId());
            FileLog.d("pending translation +" + message.getId() + " poll message");
            pendingTranslation.messageTexts.add(new Pair<>(pollText, translatedText));
            pendingTranslation.callbacks.add(callback);
            pendingTranslation.language = language;
            pendingTranslation.symbolsCount += messageSymbolsCount;
            final PendingPollTranslation pendingTranslation1 = pendingTranslation;
            pendingTranslation.runnable = () -> {
                synchronized (TranslateController.this) {
                    ArrayList<PendingTranslation> dialogPendingTranslations1 = pendingTranslations.get(dialogId);
                    if (dialogPendingTranslations1 != null) {
                        dialogPendingTranslations1.remove(pendingTranslation1);
                        if (dialogPendingTranslations1.isEmpty()) {
                            pendingTranslations.remove(dialogId);
                        }
                    }
                }

                final TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
                req.flags |= 2;
                for (Pair<PollText, PollText> pair : pendingTranslation1.messageTexts) {
                    final PollText src = pair.first;
                    final PollText alreadyTranslated = pair.second;
                    if (src.question != null && (alreadyTranslated == null || alreadyTranslated.question == null)) {
                        req.text.add(src.question);
                    }
                    if (src.answers.size() != (alreadyTranslated == null ? 0 : alreadyTranslated.answers.size())) {
                        for (TLRPC.PollAnswer answer : src.answers) {
                            req.text.add(answer.text);
                        }
                    }
                    if (src.solution != null && (alreadyTranslated == null || alreadyTranslated.solution == null)) {
                        req.text.add(src.solution);
                    }
                }
                req.to_lang = pendingTranslation1.language;

                final int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    final ArrayList<Integer> ids;
                    final ArrayList<Utilities.Callback3<Integer, PollText, String>> callbacks;
                    final ArrayList<Pair<PollText, PollText>> texts;
                    synchronized (TranslateController.this) {
                        ids = pendingTranslation1.messageIds;
                        callbacks = pendingTranslation1.callbacks;
                        texts = pendingTranslation1.messageTexts;
                    }
                    if (res instanceof TLRPC.TL_messages_translateResult) {
                        final ArrayList<TLRPC.TL_textWithEntities> translated = ((TLRPC.TL_messages_translateResult) res).result;
                        final ArrayList<PollText> result = new ArrayList<>();
                        int i = 0;
                        for (Pair<PollText, PollText> pair : texts) {
                            final PollText src = pair.first;
                            final PollText alreadyTranslated = pair.second;

                            final PollText dst = new PollText();
                            if (alreadyTranslated != null && alreadyTranslated.question != null) {
                                dst.question = alreadyTranslated.question;
                            } else if (src.question != null) {
                                final TLRPC.TL_textWithEntities text = i >= translated.size() ? new TLRPC.TL_textWithEntities() : translated.get(i++);
                                dst.question = TranslateAlert2.preprocess(src.question, text);
                            }
                            if (src.answers.size() != (alreadyTranslated == null ? 0 : alreadyTranslated.answers.size())) {
                                for (TLRPC.PollAnswer answer : src.answers) {
                                    final TLRPC.TL_textWithEntities text = i >= translated.size() ? new TLRPC.TL_textWithEntities() : translated.get(i++);
                                    TLRPC.PollAnswer dstAnswer = new TLRPC.TL_pollAnswer();
                                    dstAnswer.text = text;
                                    dstAnswer.option = answer.option;
                                    dst.answers.add(dstAnswer);
                                }
                            } else if (alreadyTranslated != null) {
                                dst.answers = alreadyTranslated.answers;
                            }
                            if (alreadyTranslated != null && alreadyTranslated.solution != null) {
                                dst.solution = alreadyTranslated.solution;
                            } else if (src.solution != null) {
                                final TLRPC.TL_textWithEntities text = i >= translated.size() ? new TLRPC.TL_textWithEntities() : translated.get(i++);
                                dst.solution = TranslateAlert2.preprocess(src.solution, text);
                            }
                            result.add(dst);
                        }
                        final int count = Math.min(callbacks.size(), result.size());
                        for (int j = 0; j < count; ++j) {
                            callbacks.get(j).run(ids.get(j), result.get(j), pendingTranslation1.language);
                        }
                    } else if (err != null && "TO_LANG_INVALID".equals(err.text)) {
                        toggleTranslatingDialog(dialogId, false);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.getString(R.string.TranslationFailedAlert2));
                    } else {
                        if (err != null && "QUOTA_EXCEEDED".equals(err.text)) {
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, LocaleController.getString(R.string.TranslationFailedAlert1));
                        }
                        for (int i = 0; i < callbacks.size(); ++i) {
                            callbacks.get(i).run(ids.get(i), null, pendingTranslation1.language);
                        }
                    }
                    synchronized (TranslateController.this) {
                        for (int i = 0; i < ids.size(); ++i) {
                            loadingTranslations.remove(ids.get(i));
                        }
                    }
                }));
                synchronized (TranslateController.this) {
                    pendingTranslation1.reqId = reqId;
                }
            };
            AndroidUtilities.runOnUIThread(pendingTranslation.runnable, pendingTranslation.delay);
            pendingTranslation.delay /= 2;
        }
    }

    public boolean isTranslating(MessageObject messageObject) {
        synchronized (this) {
            return messageObject != null && loadingTranslations.contains(messageObject.getId()) && isTranslatingDialog(messageObject.getDialogId());
        }
    }

    public boolean isTranslating(MessageObject messageObject, MessageObject.GroupedMessages group) {
        if (messageObject == null) {
            return false;
        }
        if (!isTranslatingDialog(messageObject.getDialogId())) {
            return false;
        }
        synchronized (this) {
            if (loadingTranslations.contains(messageObject.getId())) {
                return true;
            }
            if (group != null) {
                for (MessageObject message : group.messages) {
                    if (loadingTranslations.contains(message.getId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void cancelAllTranslations() {
        synchronized (this) {
            for (ArrayList<PendingTranslation> translations : pendingTranslations.values()) {
                if (translations != null) {
                    for (PendingTranslation pendingTranslation : translations) {
                        AndroidUtilities.cancelRunOnUIThread(pendingTranslation.runnable);
                        if (pendingTranslation.reqId != -1) {
                            getConnectionsManager().cancelRequest(pendingTranslation.reqId, true);
                            for (Integer messageId : pendingTranslation.messageIds) {
                                loadingTranslations.remove(messageId);
                            }
                        }
                    }
                }
            }
        }
    }

    public void cancelTranslations(long dialogId) {
        synchronized (this) {
            ArrayList<PendingTranslation> translations = pendingTranslations.get(dialogId);
            if (translations != null) {
                for (PendingTranslation pendingTranslation : translations) {
                    AndroidUtilities.cancelRunOnUIThread(pendingTranslation.runnable);
                    if (pendingTranslation.reqId != -1) {
                        getConnectionsManager().cancelRequest(pendingTranslation.reqId, true);
                        for (Integer messageId : pendingTranslation.messageIds) {
                            loadingTranslations.remove(messageId);
                        }
                    }
                }
                pendingTranslations.remove((Long) dialogId);
            }
        }
    }

    private void keepReplyMessage(MessageObject messageObject) {
        if (messageObject == null) {
            return;
        }
        HashMap<Integer, MessageObject> map = keptReplyMessageObjects.get(messageObject.getDialogId());
        if (map == null) {
            keptReplyMessageObjects.put(messageObject.getDialogId(), map = new HashMap<>());
        }
        map.put(messageObject.getId(), messageObject);
    }

    public MessageObject findReplyMessageObject(long dialogId, int messageId) {
        HashMap<Integer, MessageObject> map = keptReplyMessageObjects.get(dialogId);
        if (map == null) {
            return null;
        }
        return map.get(messageId);
    }

    private void clearAllKeptReplyMessages(long dialogId) {
        keptReplyMessageObjects.remove(dialogId);
    }


    private void loadTranslatingDialogsCached() {
        if (!isFeatureAvailable()) {
            return;
        }

        String translatingDialogsCache = messagesController.getMainSettings().getString("translating_dialog_languages2", null);
        if (translatingDialogsCache == null) {
            return;
        }
        String[] dialogs = translatingDialogsCache.split(";");

        HashSet<String> restricted = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
        for (int i = 0; i < dialogs.length; ++i) {
            String[] keyval = dialogs[i].split("=");
            if (keyval.length < 2) {
                continue;
            }
            long did = Long.parseLong(keyval[0]);
            String[] langs = keyval[1].split(">");
            if (langs.length != 2) {
                continue;
            }
            String from = langs[0], to = langs[1];
            if ("null".equals(from)) from = null;
            if ("null".equals(to)) to = null;
            if (from != null) {
                detectedDialogLanguage.put(did, from);
                if (!restricted.contains(from)) {
                    translatingDialogs.add(did);
                    translatableDialogs.add(did);
                }
                if (to != null) {
                    translateDialogLanguage.put(did, to);
                }
            }
        }

        Set<String> hidden = messagesController.getMainSettings().getStringSet("hidden_translation_at", null);
        if (hidden != null) {
            Iterator<String> i = hidden.iterator();
            while (i.hasNext()) {
                try {
                    hideTranslateDialogs.add(Long.parseLong(i.next()));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private void saveTranslatingDialogsCache() {
        StringBuilder langset = new StringBuilder();
        Iterator<Long> i = translatingDialogs.iterator();
        boolean first = true;
        while (i.hasNext()) {
            try {
                long did = i.next();
                if (!first) {
                    langset.append(";");
                }
                if (first) {
                    first = false;
                }
                String lang = detectedDialogLanguage.get(did);
                if (lang == null) {
                    lang = "null";
                }
                String tolang = getDialogTranslateTo(did);
                if (tolang == null) {
                    tolang = "null";
                }
                langset.append(did).append("=").append(lang).append(">").append(tolang);
            } catch (Exception e) {}
        }

        Set<String> hidden = new HashSet<>();
        i = hideTranslateDialogs.iterator();
        while (i.hasNext()) {
            try {
                hidden.add("" + i.next());
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        MessagesController.getMainSettings(currentAccount).edit().putString("translating_dialog_languages2", langset.toString()).putStringSet("hidden_translation_at", hidden).apply();
    }

    private void resetTranslatingDialogsCache() {
        MessagesController.getMainSettings(currentAccount).edit().remove("translating_dialog_languages2").remove("hidden_translation_at").apply();
    }

    private final HashSet<StoryKey> detectingStories = new HashSet<>();
    private final HashSet<StoryKey> translatingStories = new HashSet<>();

    // ensure dialogId in storyItem is valid
    public void detectStoryLanguage(TL_stories.StoryItem storyItem) {
        if (storyItem == null || storyItem.detectedLng != null || storyItem.caption == null || storyItem.caption.length() == 0 || !LanguageDetector.hasSupport()) {
            return;
        }

        final StoryKey key = new StoryKey(storyItem);
        if (detectingStories.contains(key)) {
            return;
        }
        detectingStories.add(key);

        LanguageDetector.detectLanguage(storyItem.caption, lng -> AndroidUtilities.runOnUIThread(() -> {
            storyItem.detectedLng = lng;
            getMessagesController().getStoriesController().getStoriesStorage().putStoryInternal(storyItem.dialogId, storyItem);
            detectingStories.remove(key);
        }), err -> AndroidUtilities.runOnUIThread(() -> {
            storyItem.detectedLng = UNKNOWN_LANGUAGE;
            getMessagesController().getStoriesController().getStoriesStorage().putStoryInternal(storyItem.dialogId, storyItem);
            detectingStories.remove(key);
        }));
    }

    public boolean canTranslateStory(TL_stories.StoryItem storyItem) {
        return storyItem != null && !TextUtils.isEmpty(storyItem.caption) && !Emoji.fullyConsistsOfEmojis(storyItem.caption) && (
            storyItem.detectedLng == null && storyItem.translatedText != null && TextUtils.equals(storyItem.translatedLng, TranslateAlert2.getToLanguage()) ||
            storyItem.detectedLng != null && !RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(storyItem.detectedLng)
        );
    }

    public void translateStory(TL_stories.StoryItem storyItem, Runnable done) {
        if (storyItem == null) {
            return;
        }

        final StoryKey key = new StoryKey(storyItem);

        String toLang = TranslateAlert2.getToLanguage();

        if (storyItem.translatedText != null && TextUtils.equals(storyItem.translatedLng, toLang)) {
            if (done != null) {
                done.run();
            }
            return;
        }
        if (translatingStories.contains(key)) {
            if (done != null) {
                done.run();
            }
            return;
        }

        translatingStories.add(key);

        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        req.flags |= 2;
        final TLRPC.TL_textWithEntities text = new TLRPC.TL_textWithEntities();
        text.text = storyItem.caption;
        text.entities = storyItem.entities;
        req.text.add(text);
        req.to_lang = toLang;
        getConnectionsManager().sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.TL_messages_translateResult) {
                ArrayList<TLRPC.TL_textWithEntities> result = ((TLRPC.TL_messages_translateResult) res).result;
                if (result.size() <= 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        storyItem.translatedLng = toLang;
                        storyItem.translatedText = null;
                        getMessagesController().getStoriesController().getStoriesStorage().putStoryInternal(storyItem.dialogId, storyItem);
                        translatingStories.remove(key);
                        if (done != null) {
                            done.run();
                        }
                    });
                    return;
                }
                final TLRPC.TL_textWithEntities textWithEntities = result.get(0);
                AndroidUtilities.runOnUIThread(() -> {
                    storyItem.translatedLng = toLang;
                    storyItem.translatedText = TranslateAlert2.preprocess(text, textWithEntities);
                    getMessagesController().getStoriesController().getStoriesStorage().putStoryInternal(storyItem.dialogId, storyItem);
                    translatingStories.remove(key);
                    if (done != null) {
                        done.run();
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    storyItem.translatedLng = toLang;
                    storyItem.translatedText = null;
                    getMessagesController().getStoriesController().getStoriesStorage().putStoryInternal(storyItem.dialogId, storyItem);
                    translatingStories.remove(key);
                    if (done != null) {
                        done.run();
                    }
                });
            }
        });
    }

    public boolean isTranslatingStory(TL_stories.StoryItem storyItem) {
        if (storyItem == null) {
            return false;
        }
        return translatingStories.contains(new StoryKey(storyItem));
    }

    private static class StoryKey {
        public long dialogId;
        public int storyId;

        public StoryKey(TL_stories.StoryItem storyItem) {
            dialogId = storyItem.dialogId;
            storyId = storyItem.id;
        }
    }

    private final HashSet<MessageKey> detectingPhotos = new HashSet<>();
    private final HashSet<MessageKey> translatingPhotos = new HashSet<>();

    public void detectPhotoLanguage(MessageObject messageObject, Utilities.Callback<String> done) {
        if (messageObject == null || messageObject.messageOwner == null || !LanguageDetector.hasSupport() || TextUtils.isEmpty(messageObject.messageOwner.message)) {
            return;
        }
        if (!TextUtils.isEmpty(messageObject.messageOwner.originalLanguage)) {
            if (done != null) {
                done.run(messageObject.messageOwner.originalLanguage);
            }
            return;
        }

        MessageKey key = new MessageKey(messageObject);
        if (detectingPhotos.contains(key)) {
            return;
        }
        detectingPhotos.add(key);

        LanguageDetector.detectLanguage(messageObject.messageOwner.message, lng -> AndroidUtilities.runOnUIThread(() -> {
            messageObject.messageOwner.originalLanguage = lng;
            getMessagesStorage().updateMessageCustomParams(key.dialogId, messageObject.messageOwner);
            detectingPhotos.remove(key);
            if (done != null) {
                done.run(lng);
            }
        }), err -> AndroidUtilities.runOnUIThread(() -> {
            messageObject.messageOwner.originalLanguage = UNKNOWN_LANGUAGE;
            getMessagesStorage().updateMessageCustomParams(key.dialogId, messageObject.messageOwner);
            detectingPhotos.remove(key);
            if (done != null) {
                done.run(UNKNOWN_LANGUAGE);
            }
        }));
    }

    public boolean canTranslatePhoto(MessageObject messageObject, String detectedLanguage) {
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.originalLanguage != null) {
            detectedLanguage = messageObject.messageOwner.originalLanguage;
        }
        return messageObject != null && messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.message) && (
            detectedLanguage == null && messageObject.messageOwner.translatedText != null && TextUtils.equals(messageObject.messageOwner.translatedToLanguage, TranslateAlert2.getToLanguage()) ||
            detectedLanguage != null && !RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(messageObject.messageOwner.originalLanguage)
        ) && !messageObject.translated;
    }

    public void translatePhoto(MessageObject messageObject, Runnable done) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }

        final MessageKey key = new MessageKey(messageObject);

        String toLang = TranslateAlert2.getToLanguage();

        if (messageObject.messageOwner.translatedText != null && TextUtils.equals(messageObject.messageOwner.translatedToLanguage, toLang)) {
            if (done != null) {
                done.run();
            }
            return;
        }
        if (translatingPhotos.contains(key)) {
            if (done != null) {
                done.run();
            }
            return;
        }

        translatingPhotos.add(key);

        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        req.flags |= 2;
        final TLRPC.TL_textWithEntities text = new TLRPC.TL_textWithEntities();
        text.text = messageObject.messageOwner.message;
        text.entities = messageObject.messageOwner.entities;
        if (text.entities == null) {
            text.entities = new ArrayList<>();
        }
        req.text.add(text);
        req.to_lang = toLang;
        final long start = System.currentTimeMillis();
        getConnectionsManager().sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.TL_messages_translateResult) {
                ArrayList<TLRPC.TL_textWithEntities> result = ((TLRPC.TL_messages_translateResult) res).result;
                if (result.size() <= 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        messageObject.messageOwner.translatedToLanguage = toLang;
                        messageObject.messageOwner.translatedText = null;
                        getMessagesStorage().updateMessageCustomParams(key.dialogId, messageObject.messageOwner);
                        translatingPhotos.remove(key);
                        if (done != null) {
                            AndroidUtilities.runOnUIThread(done, Math.max(0, 400L - (System.currentTimeMillis() - start)));
                        }
                    });
                    return;
                }
                final TLRPC.TL_textWithEntities textWithEntities = result.get(0);
                AndroidUtilities.runOnUIThread(() -> {
                    messageObject.messageOwner.translatedToLanguage = toLang;
                    messageObject.messageOwner.translatedText = TranslateAlert2.preprocess(text, textWithEntities);
                    getMessagesStorage().updateMessageCustomParams(key.dialogId, messageObject.messageOwner);
                    translatingPhotos.remove(key);
                    if (done != null) {
                        AndroidUtilities.runOnUIThread(done, Math.max(0, 400L - (System.currentTimeMillis() - start)));
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    messageObject.messageOwner.translatedToLanguage = toLang;
                    messageObject.messageOwner.translatedText = null;
                    getMessagesStorage().updateMessageCustomParams(key.dialogId, messageObject.messageOwner);
                    translatingPhotos.remove(key);
                    if (done != null) {
                        AndroidUtilities.runOnUIThread(done, Math.max(0, 400L - (System.currentTimeMillis() - start)));
                    }
                });
            }
        });
    }

    private static class MessageKey {
        public long dialogId;
        public int id;

        public MessageKey(MessageObject msg) {
            dialogId = msg.getDialogId();
            id = msg.getId();
        }
    }

    public static class PollText extends TLObject {
        public static final int constructor = 0x24953ab8;

        public TLRPC.TL_textWithEntities question;
        public ArrayList<TLRPC.PollAnswer> answers = new ArrayList<>();
        public TLRPC.TL_textWithEntities solution;

        public static PollText TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (PollText.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TranslatedPoll", constructor));
                } else {
                    return null;
                }
            }
            PollText result = new PollText();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            int flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                question = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                answers = Vector.deserialize(stream, TLRPC.PollAnswer::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                solution = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            if (question != null) {
                flags |= 1;
            }
            if (answers != null && !answers.isEmpty()) {
                flags |= 2;
            }
            if (solution != null) {
                flags |= 4;
            }
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                question.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, answers);
            }
            if ((flags & 4) != 0) {
                solution.serializeToStream(stream);
            }
        }

        public int length() {
            int length = 0;
            if (question != null) {
                length += question.text.length();
            }
            for (int i = 0; i < answers.size(); ++i) {
                length += answers.get(i).text.text.length();
            }
            if (solution != null) {
                length += solution.text.length();
            }
            return length;
        }


        public static PollText fromMessage(MessageObject messageObject) {
            final TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
            if (media instanceof TLRPC.TL_messageMediaPoll) {
                return PollText.fromPoll((TLRPC.TL_messageMediaPoll) media);
            }
            return null;
        }

        public static PollText fromPoll(TLRPC.TL_messageMediaPoll mediaPoll) {
            final TLRPC.Poll poll = mediaPoll.poll;
            final PollText pollText = new PollText();
            pollText.question = poll.question;
            for (int i = 0; i < poll.answers.size(); ++i) {
                TLRPC.PollAnswer answer = poll.answers.get(i);
                TLRPC.TL_pollAnswer answerText = new TLRPC.TL_pollAnswer();
                answerText.text = answer.text;
                answerText.option = answer.option;
                pollText.answers.add(answerText);
            }
            if (mediaPoll.results != null && !TextUtils.isEmpty(mediaPoll.results.solution)) {
                pollText.solution = new TLRPC.TL_textWithEntities();
                pollText.solution.text = mediaPoll.results.solution;
                pollText.solution.entities = mediaPoll.results.solution_entities;
            }
            return pollText;
        }

        public static boolean isFullyTranslated(MessageObject messageObject, PollText b) {
            final TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
            final TLRPC.TL_messageMediaPoll poll;
            if (media instanceof TLRPC.TL_messageMediaPoll) {
                poll = (TLRPC.TL_messageMediaPoll) media;
            } else {
                return true;
            }
            if (poll.poll == null) {
                return true;
            }

            if ((poll.poll.question != null) != (b.question != null)) return false;
            if ((poll.results != null && poll.results.solution != null) != (b.solution != null)) return false;

            if (poll.poll.answers.size() != b.answers.size()) return false;

            return true;
        }
    }
}
