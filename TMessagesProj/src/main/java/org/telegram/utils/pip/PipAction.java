package org.telegram.utils.pip;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

/** A button of the system PiP window (RemoteAction), described by the content. */
public final class PipAction {
    public final int id;
    public final @DrawableRes int iconRes;
    public final @NonNull CharSequence title;
    public final boolean enabled;

    public PipAction(int id, @DrawableRes int iconRes, @NonNull CharSequence title, boolean enabled) {
        this.id = id;
        this.iconRes = iconRes;
        this.title = title;
        this.enabled = enabled;
    }
}
