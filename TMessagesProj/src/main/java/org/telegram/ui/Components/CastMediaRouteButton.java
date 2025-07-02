package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.app.MediaRouteControllerDialog;
import androidx.mediarouter.app.MediaRouteControllerDialogFragment;
import androidx.mediarouter.app.MediaRouteDialogFactory;
import androidx.mediarouter.app.MediaRouteDynamicChooserDialog;
import androidx.mediarouter.media.MediaRouteSelector;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class CastMediaRouteButton extends MediaRouteButton {

    public CastMediaRouteButton(@NonNull Context context) {
        super(context);
//        setDialogFactory(new MediaRouteDialogFactory() {
//            @NonNull
//            @Override
//            public MediaRouteChooserDialogFragment onCreateChooserDialogFragment() {
//                return new MyMediaRouteChooserDialogFragment();
//            }
//
//            @NonNull
//            @Override
//            public MediaRouteControllerDialogFragment onCreateControllerDialogFragment() {
//                return new MyMediaRouteControllerDialogFragment();
//            }
//        });
    }

    public static class MyMediaRouteChooserDialogFragment extends MediaRouteChooserDialogFragment {
        @NonNull
        @Override
        public MediaRouteChooserDialog onCreateChooserDialog(@NonNull Context context, @Nullable Bundle savedInstanceState) {
            ContextThemeWrapper themedContext = new ContextThemeWrapper(context, R.style.Theme_CastDialog);
            return new MediaRouteChooserDialog(themedContext);
        }

        @Override
        public void onStart() {
            super.onStart();
            if (getDialog() != null && getDialog().getWindow() != null) {
                Drawable drawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert3).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(0xF9222222, PorterDuff.Mode.SRC_IN));
                getDialog().getWindow().setBackgroundDrawable(drawable);
            }
        }
    }

    public static class MyMediaRouteControllerDialogFragment extends MediaRouteControllerDialogFragment {
        @NonNull
        @Override
        public MediaRouteControllerDialog onCreateControllerDialog(@NonNull Context context, @Nullable Bundle savedInstanceState) {
            ContextThemeWrapper themedContext = new ContextThemeWrapper(context, R.style.Theme_CastDialog);
            return new MediaRouteControllerDialog(themedContext);
        }
    }

    private boolean lastConnected;
    public boolean isConnected() {
        try {
            java.lang.reflect.Field field = MediaRouteButton.class.getDeclaredField("mConnectionState");
            field.setAccessible(true);
            return ((int) field.get(this)) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setBackground(Drawable background) {}

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        checkConnected();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        checkConnected();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        checkConnected();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkConnected();
    }

    private void checkConnected() {
        final boolean connected = isConnected();
        if (lastConnected != connected) {
            stateUpdated(lastConnected = connected);
        }
    }

    public void stateUpdated(boolean connected) {

    }
}
