package org.telegram.ui.QrShare;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The server side of the QR screen ({@link QrShareView}). Anyone's theme is shown; the own one is set, and so is that of a
 * public chat whose info one can change (an admin right). The rest is only for the own QR:
 * <ul>
 * <li>the link of the code: the username, a public phone number, or else a contact token, replaced when it expires;</li>
 * <li>"Remove message fee": the chip shows while messages from non-contacts cost stars; the free code is a no-paid-messages
 * token, permanent (cached, shown at once) or, with the timer, a temporary one replaced every 30 seconds. Its link
 * comes from the server; with a contact token (no username, no import by phone) it is t.me/contact/…?free=…, and only
 * temporary codes exist then;</li>
 * <li>the theme: it comes with the full user / chat (share_theme); the one in memory is shown at once, the full is
 * asked for as a chat does (from the database, from the server when the last load is more than a minute old) and
 * replaces it if it differs; saved after every choice, written into the full at once; a chat's gift is one it owns
 * itself.</li>
 * </ul>
 */
final class QrShareServer implements NotificationCenter.NotificationCenterDelegate {

    private static final long TEMPORARY_CODE_ROTATION = 30_000;     // the server changes the code every 30 seconds
    private static final int TEMPORARY_CODE_MARGIN = 5;              // s, a code is replaced this long before it expires
    private static final long THEME_SAVE_DELAY = 700;                // emoji are often picked in a row
    private static final long CONTACT_TOKEN_RETRY = 5_000;           // a failed contact token is asked for again
    // shared with the previous QR screen, so the state carries over
    private static final String PREF_NO_PAID_PERMANENT = "qrNoPaidPermanent";
    private static final String PREF_NO_PAID_TOKEN = "qrNoPaidToken";
    private static final String PREF_NO_PAID_URL = "qrNoPaidUrl";

    /** The link of the code with the fee and the caption under the code. */
    static final class Link {
        /** Null for a contact token still on its way. */
        final @Nullable String url;
        final String caption;
        final boolean captionIsUsername;
        final boolean contactToken;
        /** The own QR: the fee, the contact token and saving the theme. */
        final boolean self;
        /** The owner of the code: a user id, or a chat as a negative id. */
        final long peerId;
        /** The theme can be set: the own QR, or a public chat whose info one can change. */
        final boolean themeEditable;

        Link(@Nullable String url, String caption, boolean captionIsUsername, boolean contactToken, boolean self, long peerId, boolean themeEditable) {
            this.url = url;
            this.caption = caption;
            this.captionIsUsername = captionIsUsername;
            this.contactToken = contactToken;
            this.self = self;
            this.peerId = peerId;
            this.themeEditable = themeEditable;
        }
    }

    private final int account;
    private final QrShareView view;
    private final Link link;

    private int mode = QrShareView.FEE_PAID;
    /** The free mode of the last time is restored once, when the fee is first known (the settings may come later). */
    private boolean freeModeRestored;
    private @Nullable TLRPC.TL_exportedNoPaidMessagesToken token;
    private long tokenTime;
    private int tokenRequestId;
    private @Nullable TLRPC.TL_exportedContactToken contactToken;
    private long contactTokenRequestTime;

    private @Nullable TLRPC.ShareTheme pendingTheme;
    private int themeRequestId;
    private boolean themeChangedByUser;
    /** A colour chosen before the palette with the server ids has come: saved once it does. */
    private boolean backdropWaitsForIds;
    private boolean released;

    private final Runnable tick = this::tick;
    private final Runnable saveTheme = this::saveTheme;

    /**
     * Resolves the link of the QR: the public username of a user or a chat. The own QR without a username
     * leads to a public phone number, else to a contact token.
     */
    static Link resolveLink(int account, long userId, long chatId) {
        final MessagesController controller = MessagesController.getInstance(account);
        final String prefix = "https://" + controller.linkPrefix + "/";
        if (userId == 0 && chatId != 0) {
            final TLRPC.Chat chat = controller.getChat(chatId);
            final String username = ChatObject.getPublicUsername(chat);
            if (username != null) {
                // the server sets the theme of a public chat only (PEER_PUBLIC_REQUIRED)
                return new Link(prefix + username, username, true, false, false, -chatId, ChatObject.canChangeChatInfo(chat));
            }
            return new Link(prefix, chat != null ? chat.title : "", false, false, false, -chatId, false);
        }
        final boolean self = userId == UserConfig.getInstance(account).getClientUserId();
        final TLRPC.User user = controller.getUser(userId);
        final String username = user != null ? UserObject.getPublicUsername(user) : null;
        if (username != null) {
            return new Link(prefix + username, username, true, false, self, userId, self);
        }
        if (!self) {
            return new Link(prefix, user != null ? UserObject.getUserName(user) : "", false, false, false, userId, false);
        }
        if (user != null && user.phone != null && isPhonePublic(account)) {
            final String phone = user.phone.startsWith("+") ? user.phone : "+" + user.phone;
            return new Link(prefix + phone, phone, false, false, true, userId, true);
        }
        final TLRPC.TL_exportedContactToken cached = controller.getCachedContactToken();
        // until a token comes, there is no code
        return new Link(cached != null ? cached.url : null, user != null ? UserObject.getUserName(user) : "", false, true, true, userId, true);
    }

    QrShareServer(int account, QrShareView view, Link link) {
        this.account = account;
        this.view = view;
        this.link = link;
    }

    void start() {
        view.setTimerOnly(link.contactToken);
        view.setTemporaryLink(link.contactToken);
        final LocalTheme local = localThemes(account).get(link.peerId);
        final TLRPC.ShareTheme cached = local != null ? local.theme : memoryFullTheme();
        if (cached != null) {
            view.setInstantTheme(true);
            applyTheme(cached);
            view.setInstantTheme(false);
        }
        view.setThemeEditable(link.themeEditable);
        if (link.themeEditable) {
            view.setGiftsOwner(link.peerId);
        }
        if (!link.self) {
            // someone else's code: the link and its owner's theme, set only for a chat one administers
            view.setFeeStars(0);
            loadTheme();
            return;
        }
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.privacyRulesUpdated);
        if (link.contactToken) {
            contactToken = MessagesController.getInstance(account).getCachedContactToken();
            if (contactToken == null) {
                requestContactToken();
            } else {
                view.setLinkExpires(contactToken.expires);
            }
        }
        updateFee();
        loadTheme();
        tick();
    }

    void release() {
        released = true;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.privacyRulesUpdated);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.userInfoDidLoad);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        AndroidUtilities.cancelRunOnUIThread(tick);
        // the last choice is still saved
        AndroidUtilities.cancelRunOnUIThread(saveTheme);
        saveTheme();
        if (tokenRequestId != 0) {
            ConnectionsManager.getInstance(account).cancelRequest(tokenRequestId, true);
            tokenRequestId = 0;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            // the paid messages setting has come or changed
            updateFee();
        } else if (id == NotificationCenter.userInfoDidLoad) {
            if ((Long) args[0] == link.peerId) {
                onFullTheme(((TLRPC.UserFull) args[1]).share_theme);
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            final TLRPC.ChatFull full = (TLRPC.ChatFull) args[0];
            if (full != null && -full.id == link.peerId) {
                onFullTheme(full.share_theme);
            }
        }
    }

    private SharedPreferences getPrefs() {
        return MessagesController.getMainSettings(account);
    }

    /** The chip with the fee; the permanent free code of the last time comes back once there is a fee. */
    private void updateFee() {
        final long stars = getFeeStars();
        view.setFeeStars(stars);
        if (stars > 0 && !freeModeRestored) {
            freeModeRestored = true;
            if (!link.contactToken && mode == QrShareView.FEE_PAID && getPrefs().getBoolean(PREF_NO_PAID_PERMANENT, false)) {
                mode = QrShareView.FEE_PERMANENT;
                view.restoreFreeMode(false);
                token = getCachedPermanentToken();
                if (token != null) {
                    view.setFreeLink(getFreeLink(token));
                }
                requestToken(false, false);
            }
        }
    }

    private long getFeeStars() {
        return getPaidMessagesStars();
    }

    private long getPaidMessagesStars() {
        final TLRPC.GlobalPrivacySettings settings = ContactsController.getInstance(account).getGlobalPrivacySettings();
        if (settings == null) {
            ContactsController.getInstance(account).loadGlobalPrivacySetting();
            return 0;
        }
        return settings.noncontact_peers_paid_stars;
    }

    /* Ticks: the temporary codes and the contact token */

    private void tick() {
        AndroidUtilities.cancelRunOnUIThread(tick);
        if (released) {
            return;
        }
        if (mode == QrShareView.FEE_TIMER && isTemporaryCodeDue()) {
            requestToken(false, true);
        }
        // the same clock as the cache of MessagesController
        if (link.contactToken && (contactToken == null || System.currentTimeMillis() / 1000 >= contactToken.expires)
            && SystemClock.elapsedRealtime() - contactTokenRequestTime >= CONTACT_TOKEN_RETRY) {
            requestContactToken();
        }
        AndroidUtilities.runOnUIThread(tick, 1000);
    }

    private void requestContactToken() {
        contactTokenRequestTime = SystemClock.elapsedRealtime();
        MessagesController.getInstance(account).requestContactToken(token -> {
            if (released || token == null) {
                return;
            }
            contactToken = token;
            // the next one is asked for as soon as this one expires
            contactTokenRequestTime = 0;
            view.setLinks(token.url, link.caption, false);
            view.setLinkExpires(token.expires);
        });
    }

    /* Fee */

    /** The chip has switched the mode; the free modes need a code of their kind. */
    void onFeeMode(int newMode) {
        if (mode == newMode) {
            return;
        }
        if (link.contactToken && newMode == QrShareView.FEE_PERMANENT) {
            // a contact token has temporary free codes only (the chip offers no permanent one)
            newMode = QrShareView.FEE_TIMER;
        }
        mode = newMode;
        getPrefs().edit().putBoolean(PREF_NO_PAID_PERMANENT, mode == QrShareView.FEE_PERMANENT).apply();
        if (mode == QrShareView.FEE_PAID) {
            // nothing is revoked: temporary codes die by themselves, the permanent token stays valid
            if (tokenRequestId != 0) {
                ConnectionsManager.getInstance(account).cancelRequest(tokenRequestId, true);
                tokenRequestId = 0;
            }
            return;
        }
        if (mode == QrShareView.FEE_PERMANENT) {
            final TLRPC.TL_exportedNoPaidMessagesToken cached = getCachedPermanentToken();
            if (cached != null) {
                token = cached;
                view.setFreeLink(getFreeLink(cached));
            }
        }
        requestToken(false, mode == QrShareView.FEE_TIMER);
    }

    /** "Reset Code": all the codes are revoked; with the timer a temporary one from a new secret comes at once. */
    void onResetCode() {
        requestToken(true, mode == QrShareView.FEE_TIMER);
    }

    private boolean isTemporaryCodeDue() {
        if (tokenRequestId != 0) {
            return false;
        }
        return token == null || SystemClock.elapsedRealtime() - tokenTime >= TEMPORARY_CODE_ROTATION
            || isTemporary(token) && ConnectionsManager.getInstance(account).getCurrentTime() >= token.expires - TEMPORARY_CODE_MARGIN;
    }

    /** A temporary code has an expiry (its flag differs between the constructors, the value does not). */
    private static boolean isTemporary(@Nullable TLRPC.TL_exportedNoPaidMessagesToken token) {
        return token != null && token.expires != 0;
    }

    private void requestToken(boolean revoke, boolean temporary) {
        // the latest request wins, e.g. when the timer is switched on and off quickly
        if (tokenRequestId != 0) {
            ConnectionsManager.getInstance(account).cancelRequest(tokenRequestId, true);
        }
        final TLRPC.TL_contacts_exportNoPaidMessagesToken req = new TLRPC.TL_contacts_exportNoPaidMessagesToken();
        req.revoke = revoke;
        req.temporary = temporary;
        final int[] requestId = new int[1];
        requestId[0] = tokenRequestId = ConnectionsManager.getInstance(account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (tokenRequestId != requestId[0] || released) {
                return;
            }
            tokenRequestId = 0;
            if (res == null) {
                final String error = err != null ? err.text : null;
                FileLog.e("QrShare: exportNoPaidMessagesToken " + error);
                if (token == null || isTemporary(token) != temporary) {
                    // the mode has no code to show
                    mode = QrShareView.FEE_PAID;
                    getPrefs().edit().putBoolean(PREF_NO_PAID_PERMANENT, false).apply();
                    view.cancelFree(error);
                } else if (error != null) {
                    view.showError(error);
                }
                return;
            }
            // a code that arrives after the mode has changed is of the wrong kind
            if (mode == QrShareView.FEE_PAID || temporary != (mode == QrShareView.FEE_TIMER)) {
                return;
            }
            if (!isTemporary(res)) {
                getPrefs().edit().putString(PREF_NO_PAID_TOKEN, res.token).putString(PREF_NO_PAID_URL, res.url).apply();
            }
            token = res;
            tokenTime = SystemClock.elapsedRealtime();
            view.setFreeLink(getFreeLink(res));
        });
    }

    @Nullable
    private TLRPC.TL_exportedNoPaidMessagesToken getCachedPermanentToken() {
        final String cachedToken = getPrefs().getString(PREF_NO_PAID_TOKEN, null);
        if (TextUtils.isEmpty(cachedToken)) {
            return null;
        }
        final TLRPC.TL_exportedNoPaidMessagesToken cached = new TLRPC.TL_exportedNoPaidMessagesToken();
        cached.token = cachedToken;
        cached.url = getPrefs().getString(PREF_NO_PAID_URL, null);
        return cached;
    }

    /** The server's link of the code; a permanent token cached by an older version may have none: built then. */
    @Nullable
    private String getFreeLink(TLRPC.TL_exportedNoPaidMessagesToken token) {
        if (!TextUtils.isEmpty(token.url) || link.url == null || link.contactToken) {
            return TextUtils.isEmpty(token.url) ? null : token.url;
        }
        return link.url + "?free=" + token.token;
    }

    /* Theme */

    /** The theme comes with the full user / chat (share_theme): the full peer is asked for on opening. */
    private void loadTheme() {
        final MessagesController controller = MessagesController.getInstance(account);
        if (link.peerId > 0) {
            final TLRPC.User user = controller.getUser(link.peerId);
            if (user == null) {
                return;
            }
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.userInfoDidLoad);
            controller.loadUserInfo(user, true, 0);
        } else {
            final TLRPC.Chat chat = controller.getChat(-link.peerId);
            if (chat == null) {
                return;
            }
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.chatInfoDidLoad);
            MessagesStorage.getInstance(account).loadChatInfo(chat.id, ChatObject.isChannel(chat), null, true, false);
        }
    }

    private void onFullTheme(@Nullable TLRPC.ShareTheme res) {
        if (released) {
            return;
        }
        final TLRPC.ShareTheme theme = themeOf(res);
        final LocalTheme local = localThemes(account).get(link.peerId);
        if (local != null) {
            if (same(theme, local.theme)) {
                localThemes(account).remove(link.peerId); // the server has it
            } else if (local.savedAt == 0 || SystemClock.elapsedRealtime() - local.savedAt < LOCAL_THEME_HOLD) {
                // a full asked for before the choice was saved: the choice stays, in the full too
                putFullTheme(local.theme);
                return;
            } else {
                localThemes(account).remove(link.peerId); // changed since (e.g. on another device)
            }
        }
        if (themeChangedByUser) {
            return;
        }
        if (loadedTheme == null) {
            // nothing on the screen yet (no full in memory): as it is, no swap animation
            view.setInstantTheme(true);
            applyTheme(theme);
            view.setInstantTheme(false);
        } else if (!same(theme, loadedTheme)) {
            applyTheme(theme);
        }
    }

    /** The theme of the full user / chat in memory; null without a full, the default when it has no share_theme. */
    @Nullable
    private TLRPC.ShareTheme memoryFullTheme() {
        final MessagesController controller = MessagesController.getInstance(account);
        if (link.peerId > 0) {
            final TLRPC.UserFull full = controller.getUserFull(link.peerId);
            return full != null ? themeOf(full.share_theme) : null;
        }
        final TLRPC.ChatFull full = controller.getChatFull(-link.peerId);
        return full != null ? themeOf(full.share_theme) : null;
    }

    /** A choice made here: into the full user / chat in memory and in the database, ahead of the server. */
    private void putFullTheme(TLRPC.ShareTheme theme) {
        final MessagesController controller = MessagesController.getInstance(account);
        if (link.peerId > 0) {
            final TLRPC.UserFull full = controller.getUserFull(link.peerId);
            if (full != null) {
                full.share_theme = theme;
                MessagesStorage.getInstance(account).updateUserInfo(full, false);
            }
        } else {
            final TLRPC.ChatFull full = controller.getChatFull(-link.peerId);
            if (full != null) {
                full.share_theme = theme;
                MessagesStorage.getInstance(account).updateChatInfo(full, false);
            }
        }
    }

    private static TLRPC.ShareTheme themeOf(@Nullable TLRPC.ShareTheme theme) {
        return theme != null ? theme : new TLRPC.TL_shareThemeDefault(); // no share_theme: the default one
    }

    /** The same theme, byte for byte: nothing to apply again. */
    private static boolean same(@Nullable TLObject a, @Nullable TLObject b) {
        if (a == null || b == null) {
            return a == b;
        }
        final byte[] x = toBytes(a), y = toBytes(b);
        return x != null && Arrays.equals(x, y);
    }

    @Nullable
    private static byte[] toBytes(TLObject object) {
        try {
            final SerializedData data = new SerializedData(object.getObjectSize());
            object.serializeToStream(data);
            final byte[] bytes = data.toByteArray();
            data.cleanup();
            return bytes;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * A theme chosen on this device and not yet seen in a full from the server (per peer, over the screens of a
     * launch): a full asked for before the save went through still has the previous theme and must not bring it back.
     */
    private static final class LocalTheme {
        TLRPC.ShareTheme theme;
        long savedAt; // elapsedRealtime of the confirmed save, 0 while it is on its way
    }

    /** A full that left the server just before the save may still come with the old theme this long after it. */
    private static final long LOCAL_THEME_HOLD = 10_000;

    @SuppressWarnings("unchecked")
    private static final android.util.LongSparseArray<LocalTheme>[] localThemes = new android.util.LongSparseArray[UserConfig.MAX_ACCOUNT_COUNT];

    private static android.util.LongSparseArray<LocalTheme> localThemes(int account) {
        if (localThemes[account] == null) {
            localThemes[account] = new android.util.LongSparseArray<>();
        }
        return localThemes[account];
    }

    private @Nullable TLRPC.ShareTheme loadedTheme;

    @Nullable
    private TLRPC.InputPeer getInputPeer() {
        if (link.self) {
            return new TLRPC.TL_inputPeerSelf();
        }
        final TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(link.peerId);
        return peer instanceof TLRPC.TL_inputPeerEmpty ? null : peer;
    }

    /**
     * The palette has been replaced: a backdrop theme that came before it (e.g. on the very first opening, with only the
     * built-in table) is applied again, now with its server id known.
     */
    void onPaletteChanged() {
        if (released) {
            return;
        }
        if (backdropWaitsForIds) {
            onThemeChanged();
        } else if (!themeChangedByUser && loadedTheme instanceof TLRPC.TL_shareThemeEmojiBackdrop
            && (((TLRPC.TL_shareThemeEmojiBackdrop) loadedTheme).flags & 1) != 0) {
            applyTheme(loadedTheme);
        }
    }

    private void applyTheme(TLRPC.ShareTheme theme) {
        loadedTheme = theme;
        if (theme instanceof TLRPC.TL_shareThemeGift && ((TLRPC.TL_shareThemeGift) theme).stargift instanceof TL_stars.TL_starGiftUnique) {
            view.applyThemeGift((TL_stars.TL_starGiftUnique) ((TLRPC.TL_shareThemeGift) theme).stargift);
        } else if (theme instanceof TLRPC.TL_shareThemeEmojiBackdrop) {
            // emoji and / or a backdrop, together in one theme
            final TLRPC.TL_shareThemeEmojiBackdrop t = (TLRPC.TL_shareThemeEmojiBackdrop) theme;
            view.applyThemeEmojiBackdrop((t.flags & 1) != 0, t.backdrop_id, (t.flags & 2) != 0 ? t.emojis : null);
        } else {
            // the default, or a profile color of the previous screen (not in the palette)
            view.applyThemeDefault();
        }
    }

    /** The user has changed the theme: it is saved from what the screen shows (a gift, else the emoji with the colour). */
    void onThemeChanged() {
        themeChangedByUser = true;
        backdropWaitsForIds = false;
        if (!link.themeEditable) {
            return;
        }
        final TLRPC.ShareTheme theme;
        // what the server will return, for the cache
        TLRPC.ShareTheme shown = null;
        final TL_stars.TL_starGiftUnique gift = view.getThemeGift();
        final ArrayList<TLRPC.Reaction> emoji = view.getPickedEmoji();
        if (gift != null) {
            final TL_stars.InputSavedStarGift input = getInputGift(gift);
            if (input == null) {
                // cannot be saved; an earlier choice still waiting is outdated
                cancelThemeSave();
                return;
            }
            final TLRPC.TL_inputShareThemeGift giftTheme = new TLRPC.TL_inputShareThemeGift();
            giftTheme.stargift = input;
            theme = giftTheme;
            final TLRPC.TL_shareThemeGift shownGift = new TLRPC.TL_shareThemeGift();
            shownGift.stargift = gift;
            shown = shownGift;
        } else {
            // emoji (custom and standard, up to 6) and the colour go together; the default colour is no backdrop
            final int index = view.getThemeBackdropIndex();
            final boolean backdrop = index >= 0 && !view.isDefaultBackdrop(index);
            if (backdrop && Backdrops.IDS == null) {
                // the built-in table has no server ids: the theme is saved when the palette comes
                cancelThemeSave();
                backdropWaitsForIds = true;
                return;
            }
            if (!backdrop && emoji.isEmpty()) {
                theme = new TLRPC.TL_shareThemeDefault();
            } else {
                final TLRPC.TL_shareThemeEmojiBackdrop t = new TLRPC.TL_shareThemeEmojiBackdrop();
                if (backdrop) {
                    t.flags |= 1;
                    t.backdrop_id = Backdrops.IDS[index];
                }
                if (!emoji.isEmpty()) {
                    t.flags |= 2;
                    t.emojis = emoji;
                }
                theme = t;
            }
        }
        loadedTheme = shown != null ? shown : theme;
        final LocalTheme local = new LocalTheme();
        local.theme = loadedTheme;
        localThemes(account).put(link.peerId, local);
        putFullTheme(loadedTheme);
        pendingTheme = theme;
        AndroidUtilities.cancelRunOnUIThread(saveTheme);
        AndroidUtilities.runOnUIThread(saveTheme, THEME_SAVE_DELAY);
    }

    /** The own gift goes by its slug; a chat's one by the chat and its saved id, it must be the chat's own gift. */
    @Nullable
    private TL_stars.InputSavedStarGift getInputGift(TL_stars.TL_starGiftUnique gift) {
        if (link.self) {
            if (TextUtils.isEmpty(gift.slug)) {
                return null;
            }
            final TL_stars.TL_inputSavedStarGiftSlug input = new TL_stars.TL_inputSavedStarGiftSlug();
            input.slug = gift.slug;
            return input;
        }
        final long savedId = view.getThemeGiftSavedId();
        final TLRPC.InputPeer peer = getInputPeer();
        if (savedId == 0 || peer == null) {
            return null;
        }
        final TL_stars.TL_inputSavedStarGiftChat input = new TL_stars.TL_inputSavedStarGiftChat();
        input.peer = peer;
        input.saved_id = savedId;
        return input;
    }

    private void cancelThemeSave() {
        pendingTheme = null;
        AndroidUtilities.cancelRunOnUIThread(saveTheme);
        final LocalTheme local = localThemes(account).get(link.peerId);
        if (local != null && local.savedAt == 0) {
            localThemes(account).remove(link.peerId); // not to be saved: the server's full is right again
        }
    }

    private void saveTheme() {
        if (pendingTheme == null) {
            return;
        }
        final TLRPC.TL_messages_setShareTheme req = new TLRPC.TL_messages_setShareTheme();
        req.peer = getInputPeer();
        req.theme = pendingTheme;
        pendingTheme = null;
        if (req.peer == null) {
            return;
        }
        // the previous choice is outdated
        if (themeRequestId != 0) {
            ConnectionsManager.getInstance(account).cancelRequest(themeRequestId, true);
        }
        final int[] requestId = new int[1];
        final LocalTheme sent = localThemes(account).get(link.peerId);
        final int account = this.account;
        final long peerId = link.peerId;
        requestId[0] = themeRequestId = ConnectionsManager.getInstance(account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (themeRequestId == requestId[0]) {
                themeRequestId = 0;
            }
            if (sent != null && localThemes(account).get(peerId) == sent) {
                if (err == null) {
                    sent.savedAt = SystemClock.elapsedRealtime();
                } else {
                    localThemes(account).remove(peerId); // not saved: the server's full is right
                }
            }
            if (err != null) {
                FileLog.e("QrShare: setShareTheme " + err.text);
                if (!released) {
                    view.showError(err.text);
                }
            }
        });
    }

    /* Phone */

    private static boolean isPhonePublic(int account) {
        ArrayList<TLRPC.PrivacyRule> rules = ContactsController.getInstance(account).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_PHONE);
        if (rules == null) {
            return false;
        }
        int type = 2;
        for (TLRPC.PrivacyRule rule : rules) {
            if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                type = 0;
                break;
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                type = 2;
                break;
            } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                type = 1;
                break;
            }
        }
        if (type == 2) {
            rules = ContactsController.getInstance(account).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
            if (rules == null || rules.isEmpty()) {
                return true;
            }
            for (TLRPC.PrivacyRule rule : rules) {
                if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                    return true;
                } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll || rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                    return false;
                }
            }
        }
        return type == 0 || type == 1;
    }
}
