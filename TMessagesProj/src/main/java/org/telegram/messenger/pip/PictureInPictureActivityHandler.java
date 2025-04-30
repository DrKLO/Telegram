package org.telegram.messenger.pip;

import android.view.View;

public interface PictureInPictureActivityHandler {
    boolean isActivityStopped();
    void addActivityPipView(View view);
    void removeActivityPipView(View view);
}
