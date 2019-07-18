package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;

public class BaseController {

    protected int currentAccount;
    private AccountInstance parentAccountInstance;

    public BaseController(int num) {
        parentAccountInstance = AccountInstance.getInstance(num);
        currentAccount = num;
    }

    protected AccountInstance getAccountInstance() {
        return parentAccountInstance;
    }

    protected MessagesController getMessagesController() {
        return parentAccountInstance.getMessagesController();
    }

    protected ContactsController getContactsController() {
        return parentAccountInstance.getContactsController();
    }

    protected MediaDataController getMediaDataController() {
        return parentAccountInstance.getMediaDataController();
    }

    protected ConnectionsManager getConnectionsManager() {
        return parentAccountInstance.getConnectionsManager();
    }

    protected LocationController getLocationController() {
        return parentAccountInstance.getLocationController();
    }

    protected NotificationsController getNotificationsController() {
        return parentAccountInstance.getNotificationsController();
    }

    protected NotificationCenter getNotificationCenter() {
        return parentAccountInstance.getNotificationCenter();
    }

    protected UserConfig getUserConfig() {
        return parentAccountInstance.getUserConfig();
    }

    protected MessagesStorage getMessagesStorage() {
        return parentAccountInstance.getMessagesStorage();
    }

    protected DownloadController getDownloadController() {
        return parentAccountInstance.getDownloadController();
    }

    protected SendMessagesHelper getSendMessagesHelper() {
        return parentAccountInstance.getSendMessagesHelper();
    }

    protected SecretChatHelper getSecretChatHelper() {
        return parentAccountInstance.getSecretChatHelper();
    }

    protected StatsController getStatsController() {
        return parentAccountInstance.getStatsController();
    }

    protected FileLoader getFileLoader() {
        return parentAccountInstance.getFileLoader();
    }

    protected FileRefController getFileRefController() {
        return parentAccountInstance.getFileRefController();
    }
}
