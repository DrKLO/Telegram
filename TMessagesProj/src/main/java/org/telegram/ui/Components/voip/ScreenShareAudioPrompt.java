package org.telegram.ui.Components.voip;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Gravity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Sharing the screen can share what the phone is playing along with it, the way a call on a
 * computer offers to. Whether it does is asked before the screen is shared, and the answer given
 * last time is the one offered again.
 */
public class ScreenShareAudioPrompt {

    private static final String KEY = "screen_share_device_audio";

    public static void show(Context context, Theme.ResourcesProvider resourcesProvider, Runnable start) {
        // what other apps play can only be taken from Android 10 on
        if (Build.VERSION.SDK_INT < 29) {
            VideoCapturerDevice.shareDeviceAudio = false;
            start.run();
            return;
        }
        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();

        final CheckBoxCell cell = new CheckBoxCell(context, 1, resourcesProvider);
        cell.setMultiline(true);
        cell.setText(LocaleController.getString(R.string.VoipShareScreenAudio), "", preferences.getBoolean(KEY, false), false);
        cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.VoipShareScreenTitle));
        builder.setMessage(LocaleController.getString(R.string.VoipShareScreenAudioInfo));
        builder.setView(cell);
        builder.setPositiveButton(LocaleController.getString(R.string.Start), (dialog, which) -> {
            preferences.edit().putBoolean(KEY, cell.isChecked()).apply();
            VideoCapturerDevice.shareDeviceAudio = cell.isChecked();
            start.run();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
