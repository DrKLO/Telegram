package ua.itaysonlab.tgkit.preference;

import java.util.List;

public class TGKitCategory {
    public String name;
    public List<TGKitPreference> preferences;

    public TGKitCategory(String name, List<TGKitPreference> preferences) {
        this.name = name;
        this.preferences = preferences;
    }
}
