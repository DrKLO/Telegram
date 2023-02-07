package org.telegram.ui.Components;

import org.telegram.messenger.ImageReceiver;

public interface AttachableDrawable {
    void onAttachedToWindow(ImageReceiver parent);
    void onDetachedFromWindow(ImageReceiver parent);
}
