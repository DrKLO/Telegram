package ua.itaysonlab.tgkit.preference.types;

public enum TGPType {
    SECTION(0, false),
    SETTINGS_CELL(2, true),
    HEADER(2, false),
    SWITCH(3, true),
    TEXT_DETAIL(4, true),
    TEXT_ICON(5, true),
    SLIDER(6, true),
    LIST(7, true),
    HINT(8, true);

    public int adapterType;
    public boolean enabled;

    TGPType(int adapterType, boolean enabled) {
        this.adapterType = adapterType;
        this.enabled = enabled;
    }
}