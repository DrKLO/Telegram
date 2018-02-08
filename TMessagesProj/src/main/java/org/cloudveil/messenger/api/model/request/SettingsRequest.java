package org.cloudveil.messenger.api.model.request;

import java.util.ArrayList;

/**
 * Created by Dmitriy on 05.02.2018.
 */

public class SettingsRequest {
    public int userId;
    public String userPhone;
    public String userName;

    public ArrayList<Row> groups = new ArrayList<>();
    public ArrayList<Row> channels = new ArrayList<>();
    public ArrayList<Row> bots = new ArrayList<>();

    public boolean isEmpty() {
        return groups.isEmpty() && channels.isEmpty() && bots.isEmpty();
    }

    public static class Row {
        public long id;
        public String title;
        public String userName;
    }

    public void addChannel(Row channel) {
        addRow(channels, channel);
    }

    public void addGroup(Row group) {
        addRow(groups, group);
    }

    public void addBot(Row bot) {
        addRow(bots, bot);
    }

    private void addRow(ArrayList<Row> rows, Row data) {
        for(Row row : rows) {
            if(row.id == data.id) {
                return;
            }
        }
        rows.add(data);
    }
}
