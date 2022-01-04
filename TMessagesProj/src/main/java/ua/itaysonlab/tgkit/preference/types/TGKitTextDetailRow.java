package ua.itaysonlab.tgkit.preference.types;

import ua.itaysonlab.tgkit.preference.TGKitPreference;

public class TGKitTextDetailRow extends TGKitPreference {
    public String detail;
    public boolean divider;

    @Override
    public TGPType getType() {
        return TGPType.TEXT_DETAIL;
    }
}
