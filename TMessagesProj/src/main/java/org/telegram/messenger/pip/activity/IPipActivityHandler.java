package org.telegram.messenger.pip.activity;

import android.app.PictureInPictureParams;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

public interface IPipActivityHandler {
    void onPictureInPictureRequested();
    void onUserLeaveHint();
    void onStart();
    void onResume();
    void onPause();
    void onStop();
    void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration ignoredNewConfig);
    void onDestroy();
    void onConfigurationChanged(Configuration ignoredNewConfig);
    void setPictureInPictureParams(PictureInPictureParams params);
}
