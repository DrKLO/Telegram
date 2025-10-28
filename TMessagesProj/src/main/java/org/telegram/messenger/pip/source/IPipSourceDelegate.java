package org.telegram.messenger.pip.source;

import android.app.RemoteAction;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;

public interface IPipSourceDelegate {
    default void pipRenderBackground(Canvas canvas) {}
    default void pipRenderForeground(Canvas canvas) {}

    default boolean pipIsAvailable() {
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    default void pipCreateActionsList(ArrayList<RemoteAction> output, String sourceId, int maxActions) {}

    Bitmap pipCreatePrimaryWindowViewBitmap();

    View pipCreatePictureInPictureView();

    void pipHidePrimaryWindowView(Runnable firstFrameCallback);

    Bitmap pipCreatePictureInPictureViewBitmap();

    void pipShowPrimaryWindowView(Runnable firstFrameCallback);
}
