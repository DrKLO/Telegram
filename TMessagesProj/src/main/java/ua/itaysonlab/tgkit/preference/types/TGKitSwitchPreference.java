package ua.itaysonlab.tgkit.preference.types;

import androidx.annotation.Nullable;

import ua.itaysonlab.tgkit.preference.TGKitPreference;

public class TGKitSwitchPreference extends TGKitPreference {
    public TGSPContract contract;
    public boolean divider = false;

    @Nullable
    public String summary;

    @Override
    public TGPType getType() {
        return TGPType.SWITCH;
    }

    public interface TGSPContract {
        boolean getPreferenceValue();

        void toggleValue();
    }
}
