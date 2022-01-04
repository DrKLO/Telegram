package ua.itaysonlab.tgkit.preference.types;

import ua.itaysonlab.tgkit.preference.TGKitPreference;

public class TGKitTextHintRow extends TGKitPreference {
    public boolean divider;

    @Override
    public TGPType getType() {
        return TGPType.HINT;
    }
}
