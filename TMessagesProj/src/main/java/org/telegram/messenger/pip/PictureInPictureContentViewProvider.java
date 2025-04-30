package org.telegram.messenger.pip;

import android.view.View;

public interface PictureInPictureContentViewProvider {
    View detachContentFromWindow();
    void onAttachContentToPip();
    void prepareDetachContentFromPip();
    void attachContentToWindow();
}
