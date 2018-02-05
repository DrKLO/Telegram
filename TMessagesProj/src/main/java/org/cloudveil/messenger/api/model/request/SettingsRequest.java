package org.cloudveil.messenger.api.model.request;

import java.util.ArrayList;

/**
 * Created by Dmitriy on 05.02.2018.
 */

public class SettingsRequest {
    public int userId;
    public String userPhone;
    public ArrayList<Row> groups = new ArrayList<>();
    public ArrayList<Row> channels = new ArrayList<>();
    public ArrayList<Row> bots = new ArrayList<>();

    public boolean isEmpty() {
        return groups.isEmpty() && channels.isEmpty() && bots.isEmpty();
    }

    public static class Row {
        public int id;
        public String title;
    }
}
