package org.telegram.messenger.auto;

enum AutoPrimarySection {
    UNREAD("tab_unread", "Unread", AutoDialogsRepository.getUnreadListKey(), AutoListRenderMode.UNREAD_COMPACT),
    PINNED("tab_pinned", "Pinned", AutoDialogsRepository.getPinnedListKey(), AutoListRenderMode.PINNED_COMPACT),
    BOTS("tab_bots", "Bots", AutoDialogsRepository.getBotsListKey(), AutoListRenderMode.BOTS_COMPACT),
    CHANNELS("tab_channels", "Channels", AutoDialogsRepository.getChannelsListKey(), AutoListRenderMode.CHANNELS_COMPACT);

    final String tabId;
    final String title;
    final String listKey;
    final AutoListRenderMode renderMode;

    AutoPrimarySection(String tabId, String title, String listKey, AutoListRenderMode renderMode) {
        this.tabId = tabId;
        this.title = title;
        this.listKey = listKey;
        this.renderMode = renderMode;
    }

    static AutoPrimarySection fromTabId(String tabId) {
        for (AutoPrimarySection section : values()) {
            if (section.tabId.equals(tabId)) {
                return section;
            }
        }
        return UNREAD;
    }

    static AutoPrimarySection fromListKey(String listKey) {
        for (AutoPrimarySection section : values()) {
            if (section.listKey.equals(listKey)) {
                return section;
            }
        }
        return UNREAD;
    }
}
