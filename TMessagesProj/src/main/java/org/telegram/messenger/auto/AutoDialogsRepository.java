package org.telegram.messenger.auto;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class AutoDialogsRepository implements NotificationCenter.NotificationCenterDelegate {

    private static final int PREFETCH_DIALOG_COUNT = 30;
    private static final int DEFAULT_SECTION_PAGE_SIZE = 6;
    private static final int DEFAULT_CHANNELS_PAGE_SIZE = 30;
    private static final int DEFAULT_FILTER_PAGE_SIZE = 30;
    private static final int SECTION_PAGE_INCREMENT = 6;
    private static final int FILTER_PAGE_INCREMENT = 30;

    private static final String LIST_KEY_MAIN = "main";
    private static final String LIST_KEY_UNREAD = "section:unread";
    private static final String LIST_KEY_PINNED = "section:pinned";
    private static final String LIST_KEY_CHANNELS = "section:channels";
    private static final String LIST_KEY_BOTS = "section:bots";
    private static final String LIST_KEY_FILTER_PREFIX = "filter:";

    enum EventType {
        VISIBLE_MODEL_CHANGED,
        TABS_CHANGED,
        LOADING_STATE_CHANGED
    }

    interface Listener {
        void onDialogsChanged(@NonNull String listKey, @NonNull EventType eventType, long viewModelVersion);
    }

    static final class AutoListSnapshot {
        final boolean loading;
        final List<TLRPC.Dialog> dialogs;
        final boolean hasMore;
        final int remainingCount;
        final long viewModelVersion;

        AutoListSnapshot(boolean loading,
                         @NonNull List<TLRPC.Dialog> dialogs,
                         boolean hasMore,
                         int remainingCount,
                         long viewModelVersion) {
            this.loading = loading;
            this.dialogs = dialogs;
            this.hasMore = hasMore;
            this.remainingCount = Math.max(0, remainingCount);
            this.viewModelVersion = viewModelVersion;
        }
    }

    static final class FilterTabSnapshot {
        final int filterIndex;
        final String title;

        FilterTabSnapshot(int filterIndex, @NonNull String title) {
            this.filterIndex = filterIndex;
            this.title = title;
        }
    }

    private static final class SectionState {
        final String key;
        boolean loading = true;
        int pageSize;
        int visibleStart = -1;
        int visibleEnd = -1;
        ArrayList<Long> visibleDialogIds = new ArrayList<>();
        ArrayList<TLRPC.Dialog> canonicalDialogs = new ArrayList<>();
        long viewModelVersion = 1L;
        long visibleModelSignature = Long.MIN_VALUE;
        AutoListSnapshot snapshot = new AutoListSnapshot(true, Collections.emptyList(), false, 0, viewModelVersion);

        SectionState(@NonNull String key, int defaultPageSize) {
            this.key = key;
            this.pageSize = defaultPageSize;
        }
    }

    private final int currentAccount;
    private final AccountInstance accountInstance;
    private final AutoAvatarProvider avatarProvider;
    private final AutoGeoRepository geoRepository;
    private final AutoMessagePreviewRepository messagePreviewRepository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final HashMap<String, SectionState> sectionStates = new HashMap<>();
    private final Runnable rebuildRunnable = this::rebuildSnapshots;

    private boolean started;
    private int metadataVersion;
    private int geoVersion;
    private long tabsVersion;
    private ArrayList<FilterTabSnapshot> filterTabs = new ArrayList<>();

    private AutoListSnapshot mainSnapshot = new AutoListSnapshot(true, Collections.emptyList(), false, 0, 1L);
    private AutoListSnapshot unreadSnapshot = new AutoListSnapshot(true, Collections.emptyList(), false, 0, 1L);
    private AutoListSnapshot pinnedSnapshot = new AutoListSnapshot(true, Collections.emptyList(), false, 0, 1L);
    private AutoListSnapshot channelsSnapshot = new AutoListSnapshot(true, Collections.emptyList(), false, 0, 1L);
    private AutoListSnapshot botsSnapshot = new AutoListSnapshot(true, Collections.emptyList(), false, 0, 1L);

    AutoDialogsRepository(int currentAccount,
                          @NonNull AccountInstance accountInstance,
                          @NonNull AutoAvatarProvider avatarProvider,
                          @NonNull AutoGeoRepository geoRepository,
                          @NonNull AutoMessagePreviewRepository messagePreviewRepository) {
        this.currentAccount = currentAccount;
        this.accountInstance = accountInstance;
        this.avatarProvider = avatarProvider;
        this.geoRepository = geoRepository;
        this.messagePreviewRepository = messagePreviewRepository;
    }

    void start() {
        if (started) {
            return;
        }
        started = true;
        NotificationCenter notificationCenter = NotificationCenter.getInstance(currentAccount);
        notificationCenter.addObserver(this, NotificationCenter.dialogsNeedReload);
        notificationCenter.addObserver(this, NotificationCenter.updateInterfaces);
        notificationCenter.addObserver(this, NotificationCenter.messagesDidLoad);
        notificationCenter.addObserver(this, NotificationCenter.didUpdateConnectionState);

        MessagesController messagesController = accountInstance.getMessagesController();
        messagesController.loadGlobalNotificationsSettings();
        messagesController.loadDialogs(0, 0, 300, true);
        messagesController.loadDialogs(1, 0, 300, true);
        messagesController.sortDialogs(null);
        rebuildSnapshots();
    }

    void destroy() {
        if (!started) {
            return;
        }
        started = false;
        handler.removeCallbacksAndMessages(null);
        NotificationCenter notificationCenter = NotificationCenter.getInstance(currentAccount);
        notificationCenter.removeObserver(this, NotificationCenter.dialogsNeedReload);
        notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces);
        notificationCenter.removeObserver(this, NotificationCenter.messagesDidLoad);
        notificationCenter.removeObserver(this, NotificationCenter.didUpdateConnectionState);
        listeners.clear();
        sectionStates.clear();
    }

    void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    AccountInstance getAccountInstance() {
        return accountInstance;
    }

    AutoListSnapshot getMainSnapshot() {
        return mainSnapshot;
    }

    AutoListSnapshot getUnreadSnapshot() {
        return unreadSnapshot;
    }

    AutoListSnapshot getPinnedSnapshot() {
        return pinnedSnapshot;
    }

    AutoListSnapshot getChannelsSnapshot() {
        return channelsSnapshot;
    }

    AutoListSnapshot getBotsSnapshot() {
        return botsSnapshot;
    }

    AutoListSnapshot getFilterSnapshot(int filterIndex) {
        return getOrCreateSectionState(getFilterListKey(filterIndex)).snapshot;
    }

    AutoListSnapshot getPageSnapshot(@NonNull String listKey, int offset, int pageSize) {
        SectionState state = getOrCreateSectionState(listKey);
        int safeOffset = Math.max(0, offset);
        int safePageSize = Math.max(1, pageSize);
        int total = state.canonicalDialogs.size();
        if (safeOffset >= total) {
            return new AutoListSnapshot(state.loading, Collections.emptyList(), false, 0,
                    state.snapshot.viewModelVersion * 31 + safeOffset);
        }
        int end = Math.min(total, safeOffset + safePageSize);
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(state.canonicalDialogs.subList(safeOffset, end));
        int remainingCount = Math.max(0, total - end);
        requestDialogData(extractDialogIds(dialogs, PREFETCH_DIALOG_COUNT));
        long version = state.snapshot.viewModelVersion * 31 + safeOffset;
        version = version * 31 + safePageSize;
        return new AutoListSnapshot(state.loading, dialogs, remainingCount > 0, remainingCount, version);
    }

    List<FilterTabSnapshot> getFilterTabs() {
        return new ArrayList<>(filterTabs);
    }

    long getTabsVersion() {
        return tabsVersion;
    }

    void onVisibleRangeChanged(@NonNull String listKey, int startIndex, int endIndex) {
        SectionState state = getOrCreateSectionState(listKey);
        state.visibleStart = startIndex;
        state.visibleEnd = endIndex;
        updateVisibleDialogIds(state, state.snapshot.dialogs);
        requestDialogData(state.visibleDialogIds);
    }

    void onListHidden(@NonNull String listKey) {
        SectionState state = getOrCreateSectionState(listKey);
        state.visibleStart = -1;
        state.visibleEnd = -1;
        state.visibleDialogIds.clear();
    }

    void loadMore(@NonNull String listKey) {
        SectionState state = getOrCreateSectionState(listKey);
        int previousPageSize = state.pageSize;
        long previousVersion = state.snapshot.viewModelVersion;
        state.pageSize += getPageIncrement(listKey);
        rebuildVisibleSnapshot(state);
        if (state.pageSize != previousPageSize && state.snapshot.viewModelVersion != previousVersion) {
            notifyListeners(listKey, EventType.VISIBLE_MODEL_CHANGED, state.snapshot.viewModelVersion);
        }
    }

    static String getMainListKey() {
        return LIST_KEY_MAIN;
    }

    static String getUnreadListKey() {
        return LIST_KEY_UNREAD;
    }

    static String getPinnedListKey() {
        return LIST_KEY_PINNED;
    }

    static String getChannelsListKey() {
        return LIST_KEY_CHANNELS;
    }

    static String getBotsListKey() {
        return LIST_KEY_BOTS;
    }

    static String getFilterListKey(int filterIndex) {
        return LIST_KEY_FILTER_PREFIX + filterIndex;
    }

    @NonNull
    ArrayList<TLRPC.Dialog> getComposeTargets(int limit) {
        MessagesController messagesController = accountInstance.getMessagesController();
        ArrayList<TLRPC.Dialog> dialogs = getDialogSource(messagesController);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        for (int i = 0; i < dialogs.size() && result.size() < limit; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!isSupportedDriverDialog(dialog) || !isWritableDialog(messagesController, dialog)) {
                continue;
            }
            result.add(dialog);
        }
        return result;
    }

    @NonNull
    ArrayList<TLRPC.Dialog> searchTargets(@NonNull String query, int limit) {
        String normalizedQuery = query.trim().toLowerCase();
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        if (normalizedQuery.isEmpty()) {
            return result;
        }
        MessagesController messagesController = accountInstance.getMessagesController();
        ArrayList<TLRPC.Dialog> dialogs = getDialogSource(messagesController);
        for (int i = 0; i < dialogs.size() && result.size() < limit; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!isSupportedDriverDialog(dialog)) {
                continue;
            }
            String title = avatarProvider.resolveDialogName(dialog.id);
            if (title == null) {
                continue;
            }
            if (!title.toLowerCase().contains(normalizedQuery)) {
                continue;
            }
            result.add(dialog);
        }
        return result;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdateConnectionState) {
            avatarProvider.clearAll();
            metadataVersion++;
            MessagesController messagesController = accountInstance.getMessagesController();
            messagesController.loadDialogs(0, 0, 300, true);
            messagesController.loadDialogs(1, 0, 300, true);
            requestDialogData(getAllVisibleDialogIds());
            rebuildSnapshots();
            return;
        }

        if (id == NotificationCenter.updateInterfaces) {
            int mask = args.length > 0 ? (int) args[0] : 0;
            if ((mask & (MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME)) != 0) {
                avatarProvider.clearNames();
                metadataVersion++;
            }
            if ((mask & (MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_CHAT_AVATAR)) != 0) {
                avatarProvider.clearAvatars();
                metadataVersion++;
            }
            if ((mask & (MessagesController.UPDATE_MASK_NEW_MESSAGE
                    | MessagesController.UPDATE_MASK_SEND_STATE
                    | MessagesController.UPDATE_MASK_MESSAGE_TEXT)) != 0) {
                ArrayList<Long> visibleDialogIds = getAllVisibleDialogIds();
                geoRepository.invalidateDialogs(visibleDialogIds);
                messagePreviewRepository.invalidateDialogs(visibleDialogIds);
                geoVersion++;
                requestDialogData(visibleDialogIds);
            } else if ((mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0) {
                ArrayList<Long> visibleDialogIds = getAllVisibleDialogIds();
                messagePreviewRepository.invalidateDialogs(visibleDialogIds);
                requestPreviewData(visibleDialogIds);
            }
            scheduleRebuild(250L);
            return;
        }

        if (id == NotificationCenter.messagesDidLoad) {
            long dialogId = args.length > 0 && args[0] instanceof Long ? (Long) args[0] : 0L;
            if (dialogId != 0L) {
                geoRepository.invalidateDialog(dialogId);
                messagePreviewRepository.invalidateDialog(dialogId);
                requestDialogData(Collections.singletonList(dialogId));
                geoVersion++;
            } else {
                ArrayList<Long> visibleDialogIds = getAllVisibleDialogIds();
                geoRepository.invalidateDialogs(visibleDialogIds);
                messagePreviewRepository.invalidateDialogs(visibleDialogIds);
                requestDialogData(visibleDialogIds);
                geoVersion++;
            }
            scheduleRebuild(0L);
            return;
        }

        if (id == NotificationCenter.dialogsNeedReload) {
            scheduleRebuild(250L);
        }
    }

    void scheduleRebuild(long delayMs) {
        handler.removeCallbacks(rebuildRunnable);
        handler.postDelayed(rebuildRunnable, delayMs);
    }

    private void rebuildSnapshots() {
        try {
            MessagesController messagesController = accountInstance.getMessagesController();
            messagesController.sortDialogs(null);
            ArrayList<TLRPC.Dialog> mainDialogs = messagesController.getDialogs(0);
            ArrayList<TLRPC.Dialog> dialogSource = getDialogSource(messagesController);
            boolean loading = mainDialogs == null || mainDialogs.isEmpty();
            if (loading) {
                messagesController.loadDialogs(0, 0, 300, true);
                messagesController.loadDialogs(1, 0, 300, true);
            }

            AutoListSnapshot previousMainSnapshot = mainSnapshot;
            AutoListSnapshot previousUnreadSnapshot = unreadSnapshot;
            AutoListSnapshot previousPinnedSnapshot = pinnedSnapshot;
            AutoListSnapshot previousChannelsSnapshot = channelsSnapshot;
            AutoListSnapshot previousBotsSnapshot = botsSnapshot;
            ArrayList<MessagesController.DialogFilter> allFilters = messagesController.getDialogFilters();
            ArrayList<FilterTabSnapshot> newFilterTabs = new ArrayList<>();
            long newTabsVersion = 17L;
            HashMap<String, AutoListSnapshot> previousFilterSnapshots = new HashMap<>();

            mainSnapshot = updateSectionState(getOrCreateSectionState(LIST_KEY_MAIN), mainDialogs, loading);
            unreadSnapshot = updateSectionState(
                    getOrCreateSectionState(LIST_KEY_UNREAD),
                    getUnreadDialogs(messagesController, dialogSource),
                    loading);
            pinnedSnapshot = updateSectionState(
                    getOrCreateSectionState(LIST_KEY_PINNED),
                    getPinnedDialogs(messagesController, dialogSource),
                    loading);
            channelsSnapshot = updateSectionState(
                    getOrCreateSectionState(LIST_KEY_CHANNELS),
                    getChannelsDialogs(messagesController, dialogSource),
                    loading);
            botsSnapshot = updateSectionState(
                    getOrCreateSectionState(LIST_KEY_BOTS),
                    getBotDialogs(messagesController, dialogSource),
                    loading);

            ArrayList<String> activeFilterKeys = new ArrayList<>();
            if (allFilters != null) {
                for (int i = 0; i < allFilters.size(); i++) {
                    MessagesController.DialogFilter filter = allFilters.get(i);
                    if (filter == null || filter.isDefault() || "ALL_CHATS".equals(filter.name)) {
                        continue;
                    }
                    String listKey = getFilterListKey(i);
                    activeFilterKeys.add(listKey);
                    previousFilterSnapshots.put(listKey, getOrCreateSectionState(listKey).snapshot);
                    updateSectionState(getOrCreateSectionState(listKey), getFilteredDialogs(messagesController, filter), false);
                    String title = TextUtils.isEmpty(filter.name) ? "Folder" : filter.name;
                    newFilterTabs.add(new FilterTabSnapshot(i, title));
                    newTabsVersion = newTabsVersion * 31 + i;
                    newTabsVersion = newTabsVersion * 31 + title.hashCode();
                }
            }
            trimObsoleteFilterStates(activeFilterKeys);

            notifyIfChanged(LIST_KEY_MAIN, previousMainSnapshot, mainSnapshot);
            notifyIfChanged(LIST_KEY_UNREAD, previousUnreadSnapshot, unreadSnapshot);
            notifyIfChanged(LIST_KEY_PINNED, previousPinnedSnapshot, pinnedSnapshot);
            notifyIfChanged(LIST_KEY_CHANNELS, previousChannelsSnapshot, channelsSnapshot);
            notifyIfChanged(LIST_KEY_BOTS, previousBotsSnapshot, botsSnapshot);
            for (int i = 0; i < newFilterTabs.size(); i++) {
                int filterIndex = newFilterTabs.get(i).filterIndex;
                String listKey = getFilterListKey(filterIndex);
                AutoListSnapshot previousFilterSnapshot = previousFilterSnapshots.get(listKey);
                if (previousFilterSnapshot != null) {
                    notifyIfChanged(listKey, previousFilterSnapshot, getFilterSnapshot(filterIndex));
                }
            }

            boolean tabsChanged = haveTabsChanged(newFilterTabs, newTabsVersion);
            filterTabs = newFilterTabs;
            tabsVersion = newTabsVersion;
            if (tabsChanged) {
                notifyListeners(LIST_KEY_MAIN, EventType.TABS_CHANGED, mainSnapshot.viewModelVersion);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private AutoListSnapshot updateSectionState(@NonNull SectionState state,
                                                ArrayList<TLRPC.Dialog> dialogs,
                                                boolean loading) {
        state.loading = loading;
        state.canonicalDialogs = limitDialogs(dialogs);
        rebuildVisibleSnapshot(state);
        return state.snapshot;
    }

    private void rebuildVisibleSnapshot(@NonNull SectionState state) {
        ArrayList<TLRPC.Dialog> visibleDialogs = resolveVisibleDialogs(state);
        boolean hasMore = state.canonicalDialogs.size() > visibleDialogs.size();
        int remainingCount = Math.max(0, state.canonicalDialogs.size() - visibleDialogs.size());
        updateVisibleDialogIds(state, visibleDialogs);
        requestDialogData(extractDialogIds(visibleDialogs, PREFETCH_DIALOG_COUNT));

        long signature = computeVisibleModelSignature(visibleDialogs, state.loading, hasMore, remainingCount);
        if (signature != state.visibleModelSignature) {
            state.visibleModelSignature = signature;
            state.viewModelVersion++;
        }
        state.snapshot = new AutoListSnapshot(
                state.loading,
                new ArrayList<>(visibleDialogs),
                hasMore,
                remainingCount,
                state.viewModelVersion);
    }

    private void notifyIfChanged(@NonNull String listKey,
                                 @NonNull AutoListSnapshot previousSnapshot,
                                 @NonNull AutoListSnapshot currentSnapshot) {
        if (previousSnapshot.viewModelVersion == currentSnapshot.viewModelVersion
                && previousSnapshot.loading == currentSnapshot.loading) {
            return;
        }
        EventType eventType = previousSnapshot.loading != currentSnapshot.loading
                ? EventType.LOADING_STATE_CHANGED
                : EventType.VISIBLE_MODEL_CHANGED;
        notifyListeners(listKey, eventType, currentSnapshot.viewModelVersion);
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> resolveVisibleDialogs(@NonNull SectionState state) {
        ArrayList<TLRPC.Dialog> visibleDialogs = new ArrayList<>();
        if (state.canonicalDialogs.isEmpty()) {
            return visibleDialogs;
        }
        int visibleCount = Math.min(Math.max(1, state.pageSize), state.canonicalDialogs.size());
        for (int i = 0; i < visibleCount; i++) {
            TLRPC.Dialog dialog = state.canonicalDialogs.get(i);
            if (dialog != null && dialog.id != 0) {
                visibleDialogs.add(dialog);
            }
        }
        return visibleDialogs;
    }

    private boolean haveTabsChanged(@NonNull ArrayList<FilterTabSnapshot> newTabs, long newTabsVersion) {
        if (newTabsVersion != tabsVersion || newTabs.size() != filterTabs.size()) {
            return true;
        }
        for (int i = 0; i < newTabs.size(); i++) {
            FilterTabSnapshot newTab = newTabs.get(i);
            FilterTabSnapshot oldTab = filterTabs.get(i);
            if (newTab.filterIndex != oldTab.filterIndex || !newTab.title.equals(oldTab.title)) {
                return true;
            }
        }
        return false;
    }

    private void trimObsoleteFilterStates(@NonNull ArrayList<String> activeFilterKeys) {
        ArrayList<String> removeKeys = new ArrayList<>();
        for (String key : sectionStates.keySet()) {
            if (!key.startsWith(LIST_KEY_FILTER_PREFIX)) {
                continue;
            }
            if (!activeFilterKeys.contains(key)) {
                removeKeys.add(key);
            }
        }
        for (int i = 0; i < removeKeys.size(); i++) {
            sectionStates.remove(removeKeys.get(i));
        }
    }

    @NonNull
    private SectionState getOrCreateSectionState(@NonNull String key) {
        SectionState state = sectionStates.get(key);
        if (state != null) {
            return state;
        }
        state = new SectionState(key, getDefaultPageSize(key));
        sectionStates.put(key, state);
        return state;
    }

    private int getDefaultPageSize(@NonNull String key) {
        if (LIST_KEY_CHANNELS.equals(key) || key.startsWith(LIST_KEY_FILTER_PREFIX) || LIST_KEY_MAIN.equals(key)) {
            return DEFAULT_CHANNELS_PAGE_SIZE;
        }
        return DEFAULT_SECTION_PAGE_SIZE;
    }

    private int getPageIncrement(@NonNull String key) {
        if (LIST_KEY_CHANNELS.equals(key) || key.startsWith(LIST_KEY_FILTER_PREFIX) || LIST_KEY_MAIN.equals(key)) {
            return FILTER_PAGE_INCREMENT;
        }
        return SECTION_PAGE_INCREMENT;
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> limitDialogs(ArrayList<TLRPC.Dialog> dialogs) {
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        if (dialogs == null || dialogs.isEmpty()) {
            return result;
        }
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null || dialog.id == 0) {
                continue;
            }
            result.add(dialog);
        }
        return result;
    }

    private long computeVisibleModelSignature(@NonNull List<TLRPC.Dialog> dialogs,
                                              boolean loading,
                                              boolean hasMore,
                                              int remainingCount) {
        long signature = 17L;
        signature = signature * 31 + (loading ? 1 : 0);
        signature = signature * 31 + metadataVersion;
        signature = signature * 31 + geoVersion;
        signature = signature * 31 + (hasMore ? 1 : 0);
        signature = signature * 31 + remainingCount;
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null) {
                continue;
            }
            signature = signature * 31 + dialog.id;
            signature = signature * 31 + dialog.top_message;
            signature = signature * 31 + dialog.unread_count;
            signature = signature * 31 + dialog.last_message_date;
            String title = avatarProvider.resolveDialogName(dialog.id);
            signature = signature * 31 + (title != null ? title.hashCode() : 0);
            AutoGeoRepository.State geoState = geoRepository.getState(dialog.id);
            signature = signature * 31 + (geoState != null ? geoState.status.ordinal() : -1);
            signature = signature * 31 + messagePreviewRepository.getDialogSignature(dialog.id);
        }
        return signature;
    }

    private void notifyListeners(@NonNull String listKey, @NonNull EventType eventType, long viewModelVersion) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onDialogsChanged(listKey, eventType, viewModelVersion);
        }
    }

    private void updateVisibleDialogIds(@NonNull SectionState state, @NonNull List<TLRPC.Dialog> dialogs) {
        state.visibleDialogIds.clear();
        if (dialogs.isEmpty()) {
            return;
        }
        int start = state.visibleStart >= 0 ? Math.min(state.visibleStart, dialogs.size() - 1) : 0;
        int end = state.visibleEnd >= 0 ? Math.min(state.visibleEnd, dialogs.size() - 1) : Math.min(dialogs.size() - 1, 4);
        if (start > end) {
            return;
        }
        for (int i = start; i <= end; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog != null && dialog.id != 0) {
                state.visibleDialogIds.add(dialog.id);
            }
        }
    }

    @NonNull
    private ArrayList<Long> extractDialogIds(@NonNull List<TLRPC.Dialog> dialogs, int limit) {
        ArrayList<Long> dialogIds = new ArrayList<>();
        for (int i = 0; i < dialogs.size() && dialogIds.size() < limit; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog != null && dialog.id != 0) {
                dialogIds.add(dialog.id);
            }
        }
        return dialogIds;
    }

    @NonNull
    private ArrayList<Long> getAllVisibleDialogIds() {
        ArrayList<Long> dialogIds = new ArrayList<>();
        for (SectionState state : sectionStates.values()) {
            for (int i = 0; i < state.visibleDialogIds.size(); i++) {
                Long dialogId = state.visibleDialogIds.get(i);
                if (dialogId != null && !dialogIds.contains(dialogId)) {
                    dialogIds.add(dialogId);
                }
            }
        }
        return dialogIds;
    }

    private void requestDialogData(@NonNull List<Long> dialogIds) {
        requestPreviewData(dialogIds);
        geoRepository.requestIfVisible(dialogIds);
    }

    private void requestPreviewData(@NonNull List<Long> dialogIds) {
        messagePreviewRepository.requestIfVisible(dialogIds);
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> getDialogSource(@NonNull MessagesController messagesController) {
        ArrayList<TLRPC.Dialog> dialogs = messagesController.getAllDialogs();
        if (dialogs == null || dialogs.isEmpty()) {
            dialogs = messagesController.getDialogs(0);
        }
        return dialogs != null ? dialogs : new ArrayList<>();
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> getFilteredDialogs(MessagesController messagesController, MessagesController.DialogFilter filter) {
        ArrayList<TLRPC.Dialog> allDialogs = messagesController.getAllDialogs();
        ArrayList<TLRPC.Dialog> filteredDialogs = new ArrayList<>();
        if (allDialogs == null || allDialogs.isEmpty()) {
            return filteredDialogs;
        }
        for (int i = 0; i < allDialogs.size(); i++) {
            TLRPC.Dialog dialog = allDialogs.get(i);
            if (!(dialog instanceof TLRPC.TL_dialog)) {
                continue;
            }
            if (!filter.includesDialog(accountInstance, dialog.id, dialog)) {
                continue;
            }
            if (DialogObject.isUserDialog(dialog.id)) {
                TLRPC.User user = messagesController.getUser(dialog.id);
                if (UserObject.isDeleted(user)) {
                    continue;
                }
            }
            filteredDialogs.add(dialog);
        }
        return filteredDialogs;
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> getUnreadDialogs(@NonNull MessagesController messagesController,
                                                     @NonNull ArrayList<TLRPC.Dialog> dialogs) {
        ArrayList<TLRPC.Dialog> userDialogs = new ArrayList<>();
        ArrayList<TLRPC.Dialog> groupDialogs = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!isSupportedDriverDialog(dialog) || !isWritableDialog(messagesController, dialog) || !isUnreadDialog(messagesController, dialog)) {
                continue;
            }
            if (isBotDialog(messagesController, dialog)) {
                continue;
            }
            if (DialogObject.isUserDialog(dialog.id)) {
                userDialogs.add(dialog);
            } else {
                groupDialogs.add(dialog);
            }
        }
        sortUserDialogs(userDialogs);
        sortGroupDialogs(messagesController, groupDialogs);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>(userDialogs.size() + groupDialogs.size());
        result.addAll(userDialogs);
        result.addAll(groupDialogs);
        return result;
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> getPinnedDialogs(@NonNull MessagesController messagesController,
                                                     @NonNull ArrayList<TLRPC.Dialog> dialogs) {
        ArrayList<TLRPC.Dialog> userDialogs = new ArrayList<>();
        ArrayList<TLRPC.Dialog> groupDialogs = new ArrayList<>();
        ArrayList<MessagesController.DialogFilter> filters = messagesController.getDialogFilters();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!isSupportedDriverDialog(dialog) || !isWritableDialog(messagesController, dialog)) {
                continue;
            }
            if (!dialog.pinned && !isPinnedInFilters(dialog.id, filters)) {
                continue;
            }
            if (isBotDialog(messagesController, dialog)) {
                continue;
            }
            if (DialogObject.isUserDialog(dialog.id)) {
                userDialogs.add(dialog);
            } else {
                groupDialogs.add(dialog);
            }
        }
        sortUserDialogs(userDialogs);
        sortGroupDialogs(messagesController, groupDialogs);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>(userDialogs.size() + groupDialogs.size());
        result.addAll(userDialogs);
        result.addAll(groupDialogs);
        return result;
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> getChannelsDialogs(@NonNull MessagesController messagesController,
                                                       @NonNull ArrayList<TLRPC.Dialog> dialogs) {
        ArrayList<TLRPC.Dialog> unreadChannels = new ArrayList<>();
        ArrayList<TLRPC.Dialog> readChannels = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!isSupportedDriverDialog(dialog) || !isChannelDialog(messagesController, dialog)) {
                continue;
            }
            if (isUnreadDialog(messagesController, dialog)) {
                unreadChannels.add(dialog);
            } else {
                readChannels.add(dialog);
            }
        }
        sortUserDialogs(unreadChannels);
        sortUserDialogs(readChannels);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>(unreadChannels.size() + readChannels.size());
        result.addAll(unreadChannels);
        result.addAll(readChannels);
        return result;
    }

    @NonNull
    private ArrayList<TLRPC.Dialog> getBotDialogs(@NonNull MessagesController messagesController,
                                                  @NonNull ArrayList<TLRPC.Dialog> dialogs) {
        ArrayList<TLRPC.Dialog> unreadBots = new ArrayList<>();
        ArrayList<TLRPC.Dialog> readBots = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (!isSupportedDriverDialog(dialog) || !isBotDialog(messagesController, dialog)) {
                continue;
            }
            if (isUnreadDialog(messagesController, dialog)) {
                unreadBots.add(dialog);
            } else {
                readBots.add(dialog);
            }
        }
        sortUserDialogs(unreadBots);
        sortUserDialogs(readBots);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>(unreadBots.size() + readBots.size());
        result.addAll(unreadBots);
        result.addAll(readBots);
        return result;
    }

    private boolean isSupportedDriverDialog(TLRPC.Dialog dialog) {
        return dialog != null
                && dialog.id != 0
                && !DialogObject.isFolderDialogId(dialog.id)
                && !DialogObject.isEncryptedDialog(dialog.id);
    }

    private boolean isUnreadDialog(@NonNull MessagesController messagesController, @NonNull TLRPC.Dialog dialog) {
        return messagesController.getDialogUnreadCount(dialog) > 0;
    }

    private boolean isWritableDialog(@NonNull MessagesController messagesController, @NonNull TLRPC.Dialog dialog) {
        if (DialogObject.isUserDialog(dialog.id)) {
            TLRPC.User user = messagesController.getUser(dialog.id);
            return user != null && !UserObject.isDeleted(user);
        }
        if (DialogObject.isChatDialog(dialog.id)) {
            TLRPC.Chat chat = messagesController.getChat(-dialog.id);
            if (chat == null) {
                return false;
            }
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                return false;
            }
            return ChatObject.canSendMessages(chat) || ChatObject.canPost(chat) || chat.megagroup;
        }
        return false;
    }

    private boolean isChannelDialog(@NonNull MessagesController messagesController, @NonNull TLRPC.Dialog dialog) {
        if (!DialogObject.isChatDialog(dialog.id)) {
            return false;
        }
        TLRPC.Chat chat = messagesController.getChat(-dialog.id);
        return chat != null && ChatObject.isChannel(chat) && !chat.megagroup;
    }

    private boolean isBotDialog(@NonNull MessagesController messagesController, @NonNull TLRPC.Dialog dialog) {
        if (!DialogObject.isUserDialog(dialog.id)) {
            return false;
        }
        TLRPC.User user = messagesController.getUser(dialog.id);
        return user != null && user.bot;
    }

    private boolean isPinnedInFilters(long dialogId, ArrayList<MessagesController.DialogFilter> filters) {
        if (filters == null) {
            return false;
        }
        for (int i = 0; i < filters.size(); i++) {
            MessagesController.DialogFilter filter = filters.get(i);
            if (filter != null && filter.pinnedDialogs.indexOfKey(dialogId) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void sortUserDialogs(@NonNull ArrayList<TLRPC.Dialog> dialogs) {
        dialogs.sort((first, second) -> {
            int firstDate = first != null ? first.last_message_date : 0;
            int secondDate = second != null ? second.last_message_date : 0;
            return Integer.compare(secondDate, firstDate);
        });
    }

    private void sortGroupDialogs(@NonNull MessagesController messagesController,
                                  @NonNull ArrayList<TLRPC.Dialog> dialogs) {
        dialogs.sort((first, second) -> {
            int firstMembers = getParticipantsCount(messagesController, first);
            int secondMembers = getParticipantsCount(messagesController, second);
            if (firstMembers != secondMembers) {
                return Integer.compare(firstMembers, secondMembers);
            }
            int firstDate = first != null ? first.last_message_date : 0;
            int secondDate = second != null ? second.last_message_date : 0;
            return Integer.compare(secondDate, firstDate);
        });
    }

    private int getParticipantsCount(@NonNull MessagesController messagesController, TLRPC.Dialog dialog) {
        if (dialog == null || !DialogObject.isChatDialog(dialog.id)) {
            return Integer.MAX_VALUE;
        }
        TLRPC.Chat chat = messagesController.getChat(-dialog.id);
        if (chat == null || chat.participants_count <= 0) {
            return Integer.MAX_VALUE;
        }
        return chat.participants_count;
    }
}
