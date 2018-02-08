package org.cloudveil.messenger.api.model.response;

import java.util.ArrayList;

/**
 * Created by Dmitriy on 05.02.2018.
 */

public class SettingsResponse {
    public ArrayList<Long> channels;
    public ArrayList<Long> bots;
    public ArrayList<Long> groups;

    public boolean secretChat;
    public int secretChatMinimumLength;

}
