package ua.itaysonlab.tgkit.preference.types;

import ua.itaysonlab.tgkit.preference.TGKitPreference;

public class TGKitHeaderRow extends TGKitPreference {
    public TGKitHeaderRow(String title) {
        this.title = title;
    }

    @Override
    public TGPType getType() {
        return TGPType.HEADER;
    }
}
