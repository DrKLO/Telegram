package org.cloudveil.messenger.api.service.holder;

import org.cloudveil.messenger.api.service.MessengerHttpInterface;

public class ServiceClientHolders {
    private static final String BASE_URL = "https://manage.cloudveil.org/api/v1/";

    private static final ServiceClientHolder<MessengerHttpInterface> messengerServiceHolder = new ServiceClientHolder<>(BASE_URL + "messenger/", MessengerHttpInterface.class);

    public static MessengerHttpInterface getSettingsService() {
        return messengerServiceHolder.getInterface();
    }
}
