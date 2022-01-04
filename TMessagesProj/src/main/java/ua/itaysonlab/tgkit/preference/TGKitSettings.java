package ua.itaysonlab.tgkit.preference;

import java.util.List;

public class TGKitSettings {
    public String name;
    public List<TGKitCategory> categories;

    public TGKitSettings(String name, List<TGKitCategory> categories) {
        this.name = name;
        this.categories = categories;
    }
}
